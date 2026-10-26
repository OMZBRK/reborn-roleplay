import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, timingSafeEqual } from 'node:crypto';
import { PrismaService } from '../prisma/prisma.service';
import {
  AVANCE_ENTREE_MIN,
  estActif,
  etatFenetre,
  lireRetardMax,
} from '../douane/douane.regles';
import { calculerSuivi } from '../whitelist/whitelist.rules';
import { formatConvocation } from './douane-fenetre';

/**
 * Play-token compact format (NOT a full JWT, intentionally simple to
 * verify cote plugin Java sans dep externe) :
 *
 *   <base64url(JSON payload)>.<base64url(HMAC-SHA256(clé, payload))>
 *
 * Clé : le secret brut pour la portée `play` ; pour la portée `douane`, une clé
 * dérivée, HMAC-SHA256(secret, "reborn-douane-v1"). Un Guardian d'avant le 25/10
 * (0.1.0), qui ignore `scope`, lit donc tout jeton douane comme une signature
 * invalide au lieu d'un accès normal (refus par défaut, CANDIDATURE_ET_DOUANE.md
 * §3.12). Guardian 0.2.0 fait la même dérivation (`PlayTokenVerifier`).
 *
 * Payload :
 *   { sub: userId, mcUuid: dashed, mcUsername, iat: unix, exp: unix,
 *     scope: 'play' | 'douane', aud?: serverId }
 *
 * Le secret est partage entre l'API (qui emet) et le plugin Guardian
 * (qui verifie). Le launcher n'a pas ce secret — il ne fait que recevoir
 * le token via l'endpoint authentifie ci-dessous, donc un user patche
 * cote client ne peut pas inventer de play-token.
 *
 * Compatibilite (25/10) : Guardian lit un jeton sans `scope` comme `play`,
 * et n'accepte un jeton sans `aud` que pendant la transition
 * (`accept-tokens-without-aud`). Reference : docs/technique/PLUGINS_REFERENCE.md,
 * section Guardian.
 */

/** Portée du jeton : jeu normal, ou hall de la douane seulement (CANDIDATURE_ET_DOUANE.md §3.3). */
export type PlayScope = 'play' | 'douane';

export interface PlayTokenPayload {
  sub: string;
  mcUuid: string;
  mcUsername: string;
  iat: number;
  exp: number;
  /** Absente sur les jetons émis avant le 25/10 : Guardian la lit alors comme `play`. */
  scope?: PlayScope;
  /** Serveur visé (clé `server-id` de Guardian). Absent si l'API ne connaît aucun identifiant de serveur. */
  aud?: string;
}

export interface PlaySession {
  playToken: string;
  expiresAt: string;
  /** Pour le launcher (bouton « Passer la douane ») ; le jeton fait foi côté serveur. */
  scope: PlayScope;
  aud: string | null;
  /**
   * Adresse de jeu du serveur visé (REBORN_SERVER_ADDRESSES), null si l'API ne
   * la connaît pas : le launcher garde alors l'adresse inscrite à sa publication.
   * Permet d'aiguiller tous les launchers (dev pré-prod, bascule en prod) depuis
   * le VPS, sans republier le launcher.
   */
  adresse: AdresseServeur | null;
}

export interface AdresseServeur {
  host: string;
  port: number;
}

/**
 * REBORN_SERVER_ADDRESSES : `dev=hote:port,prod=hote:port`. Entrée invalide
 * ignorée et signalée. Jamais d'adresse dans le dépôt : la valeur vit dans
 * `.env.prod`.
 */
export function lireAdressesServeurs(brut?: string): {
  adresses: Map<string, AdresseServeur>;
  avertissements: string[];
} {
  const adresses = new Map<string, AdresseServeur>();
  const avertissements: string[] = [];
  for (const part of (brut ?? '').split(',')) {
    const entree = part.trim();
    if (!entree) continue;
    const m = /^([a-z0-9][a-z0-9-]{0,31})=([A-Za-z0-9.-]{1,253}):(\d{1,5})$/.exec(entree);
    const port = m ? Number(m[3]) : 0;
    if (!m || port < 1 || port > 65535) {
      avertissements.push(
        `REBORN_SERVER_ADDRESSES : entrée « ${entree.split('=')[0]}=… » ignorée (forme attendue : id=hote:port).`,
      );
      continue;
    }
    adresses.set(m[1], { host: m[2], port });
  }
  return { adresses, avertissements };
}

/** Identifiant de serveur : minuscules, chiffres et tirets, comme la clé `server-id` de Guardian. */
export const SERVER_ID_PATTERN = /^[a-z0-9][a-z0-9-]{0,31}$/;

const TOKEN_TTL_SECONDS = 5 * 60;

/**
 * Étiquette de dérivation de la clé des jetons douane. Doit rester identique à
 * `PlayTokenVerifier.DOUANE_KEY_LABEL` côté Guardian ; la changer invalide tous
 * les jetons douane en circulation (5 min au plus).
 */
export const DOUANE_KEY_LABEL = 'reborn-douane-v1';

/** Message unique pour un compte qui n'a pas (encore) accès au jeu. */
const MSG_CANDIDATURE_REQUISE =
  'Ta candidature doit être acceptée avant de rejoindre le serveur.';

/** Compte PLAYER dont la candidature est pourtant acceptée : incohérence à corriger par le staff. */
const MSG_COMPTE_PAS_A_JOUR =
  "Ton accès est validé mais ton compte n'est pas à jour : ouvre un ticket.";

@Injectable()
export class PlayService {
  private readonly logger = new Logger(PlayService.name);
  private readonly secret: Buffer;
  /** Clé des jetons douane, dérivée du secret (voir l'en-tête du fichier). */
  private readonly douaneKey: Buffer;
  /** `aud` par défaut : le serveur que visent les launchers qui ne précisent rien. */
  private readonly defaultServerId: string | null;
  /** Serveurs qu'un launcher peut demander (`REBORN_SERVER_IDS` + `REBORN_SERVER_ID`). */
  private readonly knownServerIds: ReadonlySet<string>;
  private readonly douaneEnabled: boolean;
  /** Retard maximal après l'heure de convocation : même variable et mêmes bornes que DouaneService. */
  private readonly douaneRetardMaxMin: number;
  /** Adresses de jeu par serveur (REBORN_SERVER_ADDRESSES). */
  private readonly adresses: ReadonlyMap<string, AdresseServeur>;

  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    const raw = config.get<string>('REBORN_PLAY_TOKEN_SECRET');
    if (!raw || raw.length < 32) {
      throw new Error(
        'REBORN_PLAY_TOKEN_SECRET manquant ou trop court (32+ chars requis). ' +
          'Cf .env.example.',
      );
    }
    this.secret = Buffer.from(raw, 'utf8');
    this.douaneKey = createHmac('sha256', this.secret)
      .update(DOUANE_KEY_LABEL)
      .digest();

    this.defaultServerId = this.readServerId(
      config.get<string>('REBORN_SERVER_ID'),
      'REBORN_SERVER_ID',
    );
    const known = new Set<string>();
    for (const part of (config.get<string>('REBORN_SERVER_IDS') ?? '').split(
      ',',
    )) {
      const id = this.readServerId(part, 'REBORN_SERVER_IDS');
      if (id) known.add(id);
    }
    if (this.defaultServerId) known.add(this.defaultServerId);
    this.knownServerIds = known;

    this.douaneEnabled = estActif(config.get<string>('REBORN_DOUANE_ENABLED'));
    // Règle unique de la fenêtre d'entrée (douane.regles.ts) : le bouton du launcher,
    // le passage en MISSED et ce jeton lisent la même variable, avec les mêmes bornes.
    this.douaneRetardMaxMin = lireRetardMax(
      config.get<string>('DOUANE_RETARD_MAX_MIN'),
    );

    const lu = lireAdressesServeurs(
      config.get<string>('REBORN_SERVER_ADDRESSES'),
    );
    for (const a of lu.avertissements) this.logger.error(a);
    this.adresses = lu.adresses;

    this.logger.log(
      `Jetons de jeu : serveur par défaut ${this.defaultServerId ?? 'aucun (pas de aud)'}` +
        ` ; serveurs connus : ${known.size ? [...known].join(', ') : 'aucun'}` +
        ` ; adresses connues : ${this.adresses.size ? [...this.adresses.keys()].join(', ') : 'aucune (adresse du launcher)'}` +
        ` ; douane ${this.douaneEnabled ? `ACTIVE (H − ${AVANCE_ENTREE_MIN} min à H + ${this.douaneRetardMaxMin} min)` : 'coupée'}`,
    );
  }

  /**
   * Emet un play-token pour `userId`, apres avoir verifie en base que le
   * user existe, n'est pas banni, et que son role ou sa phase autorise
   * l'entree sur le serveur. C'est l'unique source de verite : meme si le
   * launcher gate cote UI, c'est ici qu'on bloque pour de vrai.
   *
   * Portée (CANDIDATURE_ET_DOUANE.md §3.3) :
   * - rôle WHITELISTED ou plus (staff compris) : `play`, jamais la douane ;
   * - compte PLAYER, écrit accepté, réservation de douane dans sa fenêtre
   *   d'entrée, et `REBORN_DOUANE_ENABLED` actif : `douane` ;
   * - sinon : 403 avec un message selon la situation.
   *
   * `serveurDemande` : identifiant envoyé par le launcher (facultatif).
   */
  async issuePlayToken(
    userId: string,
    serveurDemande?: string,
  ): Promise<PlaySession> {
    const aud = this.resolveAudience(serveurDemande);

    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        minecraftUuid: true,
        minecraftUsername: true,
        role: true,
        banned: true,
        oralPassedAt: true,
      },
    });

    if (!user) {
      throw new ForbiddenException(
        'Compte introuvable. Reconnecte-toi au launcher.',
      );
    }
    if (user.banned) {
      throw new ForbiddenException(
        'Ton compte est banni : tu ne peux pas rejoindre le serveur.',
      );
    }

    let scope: PlayScope;
    if (user.role !== 'PLAYER') {
      scope = 'play';
    } else {
      await this.assertDouaneAccess(user.id, user.oralPassedAt, aud);
      scope = 'douane';
    }

    const now = Math.floor(Date.now() / 1000);
    const payload: PlayTokenPayload = {
      sub: user.id,
      mcUuid: user.minecraftUuid,
      mcUsername: user.minecraftUsername,
      iat: now,
      exp: now + TOKEN_TTL_SECONDS,
      scope,
      ...(aud ? { aud } : {}),
    };

    const payloadB64 = base64url(Buffer.from(JSON.stringify(payload), 'utf8'));
    const signature = createHmac('sha256', this.keyFor(scope))
      .update(payloadB64)
      .digest();
    const sigB64 = base64url(signature);

    this.logger.log(
      `play-token émis pour ${user.minecraftUsername} (portée ${scope}, serveur ${aud ?? 'non précisé'}, exp dans ${TOKEN_TTL_SECONDS}s)`,
    );
    return {
      playToken: `${payloadB64}.${sigB64}`,
      expiresAt: new Date(payload.exp * 1000).toISOString(),
      scope,
      aud: aud ?? null,
      adresse: (aud && this.adresses.get(aud)) || null,
    };
  }

  /**
   * Serveur visé : celui que demande le launcher s'il est connu, sinon le serveur par défaut.
   * Une API qui ne connaît aucun serveur n'émet pas de `aud` (ancien comportement).
   */
  private resolveAudience(serveurDemande?: string): string | null {
    const demande = serveurDemande?.trim();
    if (!demande) return this.defaultServerId;
    if (this.knownServerIds.size === 0) return null;
    if (!this.knownServerIds.has(demande)) {
      throw new BadRequestException(
        `Serveur inconnu : « ${demande} ». Mets ton launcher à jour.`,
      );
    }
    return demande;
  }

  /**
   * Compte PLAYER : lève une 403 au message utile, sauf s'il peut entrer à la douane maintenant.
   *
   * Lit le modèle de la candidature v2 (lot A) : `writtenApprovedAt` (écrit accepté),
   * `oralStatus`, `User.oralPassedAt`, et la réservation `DouaneReservation` BOOKED avec son
   * heure de convocation. Quand la phase sera calculée à un seul endroit (`GET /game/acces`,
   * lot B), ce contrôle devra s'y brancher.
   */
  private async assertDouaneAccess(
    userId: string,
    oralPassedAt: Date | null,
    aud: string | null,
  ): Promise<void> {
    if (oralPassedAt) {
      // Oral déjà passé sur ce compte : jamais de nouvelle douane. Aucune
      // promotion n'est différée (les décisions promeuvent dans leur
      // transaction) : le message dépend de la phase.
      await this.refuserOralDejaPasse(userId, oralPassedAt);
    }
    if (!this.douaneEnabled) {
      throw new ForbiddenException(MSG_CANDIDATURE_REQUISE);
    }

    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
      select: {
        id: true,
        status: true,
        writtenApprovedAt: true,
        oralStatus: true,
      },
    });
    const ecritAccepte =
      !!app &&
      !!app.writtenApprovedAt &&
      app.status !== 'REJECTED' &&
      app.oralStatus !== 'REJECTED' &&
      app.oralStatus !== 'APPROVED';
    if (!app || !ecritAccepte) {
      throw new ForbiddenException(MSG_CANDIDATURE_REQUISE);
    }

    const reservation = await this.prisma.douaneReservation.findFirst({
      where: {
        userId,
        applicationId: app.id,
        statut: 'BOOKED',
        seance: { statut: { in: ['OUVERTE', 'FERMEE'] } },
      },
      orderBy: { convocationAt: 'asc' },
      select: { convocationAt: true },
    });
    if (!reservation) {
      throw new ForbiddenException(
        "Ton écrit est accepté : réserve ta séance de douane dans le launcher pour passer l'oral.",
      );
    }

    const etat = etatFenetre(
      reservation.convocationAt,
      new Date(),
      this.douaneRetardMaxMin,
    );
    if (etat === 'A_VENIR') {
      throw new ForbiddenException(
        `Ta séance de douane est prévue ${formatConvocation(reservation.convocationAt)}. ` +
          `L'entrée ouvre ${AVANCE_ENTREE_MIN} min avant.`,
      );
    }
    if (etat === 'FERMEE') {
      throw new ForbiddenException(
        "L'heure d'entrée de ta séance de douane est passée : réserve une nouvelle séance dans le launcher.",
      );
    }

    // Un jeton douane doit désigner son serveur : Guardian refuse une douane sans aud.
    if (!aud) {
      this.logger.warn(
        'Douane active mais aucun identifiant de serveur (REBORN_SERVER_ID) : jeton douane refusé.',
      );
      throw new ForbiddenException(
        "La douane n'est pas encore ouverte. Réessaie un peu plus tard.",
      );
    }
  }

  /**
   * Compte PLAYER dont l'oral est déjà passé : lève toujours une 403.
   * - écrit pas encore accepté (dossier migré dont le RP est à relire, nouvel
   *   écrit après un retrait) : candidature requise ;
   * - phase ACCEPTE avec un rôle encore PLAYER : incohérence, journalisée, le
   *   joueur passe par un ticket (relancer le launcher n'y changerait rien).
   */
  private async refuserOralDejaPasse(
    userId: string,
    oralPassedAt: Date,
  ): Promise<never> {
    const [user, app] = await Promise.all([
      this.prisma.user.findUnique({
        where: { id: userId },
        select: { whitelistRejectedAt: true },
      }),
      this.prisma.whitelistApplication.findUnique({
        where: { userId },
        select: {
          status: true,
          hrpStatus: true,
          rpStatus: true,
          oralStatus: true,
          oralDecidedAt: true,
          reviewedAt: true,
        },
      }),
    ]);
    const { phase } = calculerSuivi({
      role: 'PLAYER',
      oralPassedAt,
      whitelistRejectedAt: user?.whitelistRejectedAt ?? null,
      app: app ?? null,
      reservation: null,
      now: new Date(),
    });
    if (phase === 'ACCEPTE') {
      this.logger.warn(
        `compte ${userId} : candidature acceptée mais rôle encore PLAYER (jeton refusé)`,
      );
      throw new ForbiddenException(MSG_COMPTE_PAS_A_JOUR);
    }
    throw new ForbiddenException(MSG_CANDIDATURE_REQUISE);
  }

  /**
   * Verifie un play-token (utilise par les tests + potentiellement un futur
   * endpoint d'introspection cote plugin). La verif cote plugin Guardian
   * est faite en Java avec la meme logique, plus les controles de portee et
   * de serveur vise.
   */
  verifyPlayToken(token: string): PlayTokenPayload | null {
    const payload = this.verifySignedPayload(token);
    if (!payload) return null;
    if (typeof payload.exp !== 'number' || payload.exp * 1000 < Date.now())
      return null;
    return payload;
  }

  /**
   * Vérifie la signature HMAC d'un play-token SANS exiger la fraîcheur (exp).
   * Utilisé par le report in-game : le menu ÉCHAP peut être ouvert bien après
   * les 5min de validité, mais la signature prouve toujours que l'API a émis
   * ce token pour ce compte — donc l'identité (`sub`) reste digne de confiance.
   */
  verifyPlayTokenSignature(token: string): PlayTokenPayload | null {
    return this.verifySignedPayload(token);
  }

  /** Clé de signature d'une portée : secret brut pour `play`, clé dérivée pour `douane`. */
  private keyFor(scope: PlayScope): Buffer {
    return scope === 'douane' ? this.douaneKey : this.secret;
  }

  /**
   * Signature valide (comparaison en temps constant, avec les deux clés) et payload lisible.
   * La clé qui a signé fixe la portée : un payload `douane` doit être signé par la clé
   * douane, tout autre payload par le secret brut. Sinon : `null`.
   */
  private verifySignedPayload(token: string): PlayTokenPayload | null {
    if (typeof token !== 'string') return null;
    const parts = token.split('.');
    if (parts.length !== 2) return null;
    const [payloadB64, sigB64] = parts;

    let providedSig: Buffer;
    try {
      providedSig = base64urlDecode(sigB64);
    } catch {
      return null;
    }
    const signedPlay = sameSignature(
      providedSig,
      createHmac('sha256', this.secret).update(payloadB64).digest(),
    );
    const signedDouane = sameSignature(
      providedSig,
      createHmac('sha256', this.douaneKey).update(payloadB64).digest(),
    );
    if (!signedPlay && !signedDouane) return null;

    let payload: PlayTokenPayload;
    try {
      payload = JSON.parse(
        base64urlDecode(payloadB64).toString('utf8'),
      ) as PlayTokenPayload;
    } catch {
      return null;
    }
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return null;
    }
    if (signedDouane !== (payload.scope === 'douane')) return null;
    return payload;
  }

  /** Identifiant de serveur lu dans l'environnement ; invalide = ignoré, avec une erreur au démarrage. */
  private readServerId(
    raw: string | undefined,
    variable: string,
  ): string | null {
    const id = raw?.trim();
    if (!id) return null;
    if (!SERVER_ID_PATTERN.test(id)) {
      this.logger.error(
        `${variable} : identifiant de serveur « ${id} » ignoré (lettres minuscules, chiffres et tirets, 32 caractères au plus).`,
      );
      return null;
    }
    return id;
  }
}

/** Comparaison en temps constant ; une longueur différente suffit à refuser. */
function sameSignature(provided: Buffer, expected: Buffer): boolean {
  return (
    provided.length === expected.length && timingSafeEqual(provided, expected)
  );
}

function base64url(buf: Buffer): string {
  return buf
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function base64urlDecode(str: string): Buffer {
  const pad = str.length % 4 === 0 ? '' : '='.repeat(4 - (str.length % 4));
  return Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/') + pad, 'base64');
}
