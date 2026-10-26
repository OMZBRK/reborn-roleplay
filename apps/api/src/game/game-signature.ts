import { createHmac, timingSafeEqual } from 'node:crypto';
import { SERVER_ID_PATTERN } from '../play/play.service';

/**
 * Signature des routes appelées par le serveur de jeu (CANDIDATURE_ET_DOUANE.md
 * §3.9 et §3.12) : `GET /v1/game/acces/:mcUuid`, `/v1/game/douane/*` et
 * `POST /v1/game/characters/sync` (§4.5).
 *
 * Remplace, pour ces routes, `GameHmacGuard` et `HmacSignatureGuard`, qui ne
 * lient la signature ni à la route ni à l'heure, et qui partagent
 * `REBORN_WEBHOOK_SECRET` avec le bot. Ici :
 *  - un secret PROPRE au jeu, `REBORN_GAME_SECRET`, ou un secret par serveur
 *    (`REBORN_GAME_SECRETS="dev:…,build:…"`) ;
 *  - la signature couvre la méthode, le chemin (requête comprise), le serveur,
 *    l'horodatage, le nonce et le corps brut ;
 *  - horodatage accepté à ± 60 s (§3.9) ; nonce à usage unique (table
 *    `GameRequestNonce`) ; limite de débit par serveur et par route.
 *
 * L'identifiant `X-Reborn-Server` n'est prouvé que par son secret. Avec un
 * secret par serveur, il l'est. Avec un seul secret (`REBORN_GAME_SECRET`),
 * seuls les identifiants de `REBORN_SERVER_IDS` / `REBORN_SERVER_ID` passent
 * quand ces variables sont remplies ; sinon, l'identifiant est celui que
 * déclare l'appelant (mode signalé au démarrage).
 *
 * Contrat, à reproduire tel quel côté plugin :
 *
 *   En-têtes :
 *     X-Reborn-Server     identifiant du serveur (minuscules, chiffres, tirets ; 32 au plus)
 *     X-Reborn-Timestamp  secondes Unix (entier décimal)
 *     X-Reborn-Nonce      16 à 64 caractères [A-Za-z0-9_-], neuf à chaque envoi (un UUID convient)
 *     X-Reborn-Signature  hex minuscule de HMAC-SHA256(secret, chaîne), 64 caractères
 *
 *   Chaîne signée (UTF-8, lignes séparées par « \n », sans « \n » final) :
 *     REBORN-GAME-V1
 *     <MÉTHODE en majuscules>
 *     <chemin tel qu'envoyé, préfixe /v1 et requête « ?… » compris>
 *     <X-Reborn-Server>
 *     <X-Reborn-Timestamp>
 *     <X-Reborn-Nonce>
 *     <corps brut, octet pour octet ; vide pour un GET>
 *
 * Une nouvelle tentative est un nouvel envoi : nouvel horodatage, nouveau nonce,
 * nouvelle signature.
 */

/** Première ligne de la chaîne signée : version du schéma. */
export const SCHEMA_SIGNATURE = 'REBORN-GAME-V1';
/** Écart maximal entre l'horodatage de la requête et l'heure de l'API (§3.9 : 60 s). */
export const TOLERANCE_HORODATAGE_S = 60;
/**
 * Garde d'un nonce en base. Un nonce rejoué doit être encore présent tant que
 * son horodatage peut passer : 2 × la tolérance au plus ; on garde 3 × pour
 * absorber l'écart d'horloge entre l'API et la base (createdAt = now() de Postgres).
 */
export const GARDE_NONCE_MS = 3 * TOLERANCE_HORODATAGE_S * 1000;
/** Longueur minimale d'un secret du jeu. */
export const SECRET_LONGUEUR_MIN = 32;

/** Noms des en-têtes, en minuscules (forme d'Express). */
export const EN_TETES_JEU = {
  serveur: 'x-reborn-server',
  horodatage: 'x-reborn-timestamp',
  nonce: 'x-reborn-nonce',
  signature: 'x-reborn-signature',
} as const;

const MOTIF_HORODATAGE = /^\d{1,12}$/;
const MOTIF_NONCE = /^[A-Za-z0-9_-]{16,64}$/;
const MOTIF_SIGNATURE = /^[0-9a-f]{64}$/;

export interface RequeteJeu {
  methode: string;
  /** `req.originalUrl` : chemin et requête, tels que reçus. */
  chemin: string;
  enTetes: Record<string, string | string[] | undefined>;
  /** Corps brut (`req.rawBody`, capturé dans main.ts) ; vide pour un GET. */
  corps: Buffer;
}

export interface EnTetesLus {
  serveur: string;
  horodatage: number;
  horodatageBrut: string;
  nonce: string;
  signature: string;
}

/** Lit et contrôle le format des quatre en-têtes ; message d'erreur sinon. */
export function lireEnTetes(
  enTetes: RequeteJeu['enTetes'],
): EnTetesLus | string {
  const un = (nom: string): string | null => {
    const v = enTetes[nom];
    return typeof v === 'string' ? v.trim() : null;
  };
  const serveur = un(EN_TETES_JEU.serveur);
  const horodatage = un(EN_TETES_JEU.horodatage);
  const nonce = un(EN_TETES_JEU.nonce);
  const signature = un(EN_TETES_JEU.signature)?.toLowerCase() ?? null;
  if (!serveur || !SERVER_ID_PATTERN.test(serveur)) {
    return 'En-tête X-Reborn-Server manquant ou invalide.';
  }
  if (!horodatage || !MOTIF_HORODATAGE.test(horodatage)) {
    return 'En-tête X-Reborn-Timestamp manquant ou invalide (secondes Unix).';
  }
  if (!nonce || !MOTIF_NONCE.test(nonce)) {
    return 'En-tête X-Reborn-Nonce manquant ou invalide (16 à 64 caractères A-Z, a-z, 0-9, _ ou -).';
  }
  if (!signature || !MOTIF_SIGNATURE.test(signature)) {
    return 'En-tête X-Reborn-Signature manquant ou invalide (64 caractères hexadécimaux).';
  }
  return {
    serveur,
    horodatage: Number(horodatage),
    horodatageBrut: horodatage,
    nonce,
    signature,
  };
}

/** Chaîne signée (voir l'en-tête du fichier). */
export function chaineASigner(
  methode: string,
  chemin: string,
  serveur: string,
  horodatage: string,
  nonce: string,
  corps: Buffer,
): Buffer {
  const entete = [
    SCHEMA_SIGNATURE,
    methode.toUpperCase(),
    chemin,
    serveur,
    horodatage,
    nonce,
    '',
  ].join('\n');
  return Buffer.concat([Buffer.from(entete, 'utf8'), corps]);
}

/** hex minuscule de HMAC-SHA256(secret, chaîne). */
export function signer(secret: string, chaine: Buffer): string {
  return createHmac('sha256', secret).update(chaine).digest('hex');
}

/** Vrai si la signature fournie correspond à l'un des secrets (comparaison en temps constant). */
export function signatureValide(
  secrets: readonly string[],
  chaine: Buffer,
  fournie: string,
): boolean {
  const recue = Buffer.from(fournie, 'hex');
  let ok = false;
  for (const secret of secrets) {
    const attendue = createHmac('sha256', secret).update(chaine).digest();
    // Pas de sortie anticipée : chaque secret est essayé.
    if (recue.length === attendue.length && timingSafeEqual(recue, attendue)) {
      ok = true;
    }
  }
  return ok;
}

// ──────────────────────────── Secrets ────────────────────────────

export interface SecretsJeu {
  /** `par-serveur` : REBORN_GAME_SECRETS ; `unique` : REBORN_GAME_SECRET pour tout serveur ; `aucun` : routes coupées. */
  mode: 'aucun' | 'unique' | 'par-serveur';
  /** Secrets acceptés par serveur (plusieurs pendant un changement de secret). */
  parServeur: ReadonlyMap<string, readonly string[]>;
  /** Secret unique (mode `unique`). */
  unique: string | null;
  /**
   * Mode `unique` : serveurs admis (`REBORN_SERVER_IDS` + `REBORN_SERVER_ID`).
   * Vide = tout identifiant, tel que l'appelant le déclare.
   */
  serveursUnique: ReadonlySet<string>;
  /** Réglages ignorés ou risqués, à journaliser (jamais la valeur d'un secret). */
  avertissements: string[];
}

export interface EnvSecretsJeu {
  REBORN_GAME_SECRET?: string;
  REBORN_GAME_SECRETS?: string;
  /** Serveurs connus de l'API : mêmes variables que le jeton de jeu (play.service.ts). */
  REBORN_SERVER_ID?: string;
  REBORN_SERVER_IDS?: string;
  /** Secrets à ne jamais réutiliser pour le jeu. */
  REBORN_WEBHOOK_SECRET?: string;
  REBORN_PLAY_TOKEN_SECRET?: string;
}

/**
 * Lit REBORN_GAME_SECRETS (`id:secret` séparés par des virgules ; un même
 * identifiant peut revenir pour un changement de secret) ou, à défaut,
 * REBORN_GAME_SECRET. Un secret trop court ou égal à un autre secret de l'API
 * (bot, jeton de jeu) est ignoré. Un même secret rattaché à deux serveurs est
 * signalé : la fuite de l'un ouvrirait l'autre. En mode `unique`,
 * REBORN_SERVER_IDS / REBORN_SERVER_ID bornent les serveurs admis (un
 * identifiant invalide y est déjà signalé par le jeton de jeu).
 */
export function lireSecretsJeu(env: EnvSecretsJeu): SecretsJeu {
  const avertissements: string[] = [];
  const interdits = new Set(
    [env.REBORN_WEBHOOK_SECRET, env.REBORN_PLAY_TOKEN_SECRET]
      .map((s) => s?.trim())
      .filter((s): s is string => !!s),
  );
  const accepte = (secret: string, ou: string): boolean => {
    if (secret.length < SECRET_LONGUEUR_MIN) {
      avertissements.push(
        `${ou} : secret ignoré (${SECRET_LONGUEUR_MIN} caractères minimum).`,
      );
      return false;
    }
    if (interdits.has(secret)) {
      avertissements.push(
        `${ou} : secret ignoré, égal à REBORN_WEBHOOK_SECRET ou REBORN_PLAY_TOKEN_SECRET (le jeu a son propre secret).`,
      );
      return false;
    }
    return true;
  };

  const parServeur = new Map<string, string[]>();
  for (const brut of (env.REBORN_GAME_SECRETS ?? '').split(',')) {
    const entree = brut.trim();
    if (!entree) continue;
    const i = entree.indexOf(':');
    const id = i > 0 ? entree.slice(0, i).trim() : '';
    const secret = i > 0 ? entree.slice(i + 1).trim() : '';
    if (!SERVER_ID_PATTERN.test(id)) {
      avertissements.push(
        'REBORN_GAME_SECRETS : entrée ignorée (forme attendue « serveur:secret », serveur en minuscules, chiffres et tirets).',
      );
      continue;
    }
    if (!accepte(secret, `REBORN_GAME_SECRETS (${id})`)) continue;
    const memeSecret = [...parServeur]
      .filter(([autre, liste]) => autre !== id && liste.includes(secret))
      .map(([autre]) => autre);
    if (memeSecret.length > 0) {
      avertissements.push(
        `REBORN_GAME_SECRETS : ${id} a le même secret que ${memeSecret.join(', ')} ; il faut un secret différent par serveur.`,
      );
    }
    parServeur.set(id, [...(parServeur.get(id) ?? []), secret]);
  }

  const serveursUnique = new Set<string>();
  for (const brut of [
    ...(env.REBORN_SERVER_IDS ?? '').split(','),
    env.REBORN_SERVER_ID ?? '',
  ]) {
    const id = brut.trim();
    if (SERVER_ID_PATTERN.test(id)) serveursUnique.add(id);
  }

  const unique = env.REBORN_GAME_SECRET?.trim() || null;
  if (parServeur.size > 0) {
    if (unique) {
      avertissements.push(
        'REBORN_GAME_SECRET ignoré : REBORN_GAME_SECRETS (un secret par serveur) est rempli.',
      );
    }
    return {
      mode: 'par-serveur',
      parServeur,
      unique: null,
      serveursUnique: new Set(),
      avertissements,
    };
  }
  if (unique && accepte(unique, 'REBORN_GAME_SECRET')) {
    return {
      mode: 'unique',
      parServeur,
      unique,
      serveursUnique,
      avertissements,
    };
  }
  return {
    mode: 'aucun',
    parServeur,
    unique: null,
    serveursUnique: new Set(),
    avertissements,
  };
}

/** Secrets acceptés pour un serveur ; vide = serveur inconnu. */
export function secretsDuServeur(
  secrets: SecretsJeu,
  serveur: string,
): readonly string[] {
  if (secrets.mode === 'unique' && secrets.unique) {
    const borne = secrets.serveursUnique;
    return borne.size === 0 || borne.has(serveur) ? [secrets.unique] : [];
  }
  return secrets.parServeur.get(serveur) ?? [];
}

// ──────────────────────────── Limite de débit ────────────────────────────

/** Au-delà de ce nombre de seaux, ceux restés inactifs une minute sont effacés. */
export const SEAUX_MAX = 1000;

/**
 * Seau à jetons par clé (serveur + route), en mémoire : une seule instance de
 * l'API (comme l'état OAuth Discord). Les clés ne viennent que de requêtes déjà
 * authentifiées : serveurs × routes avec un secret par serveur ou une liste de
 * serveurs ; sinon, l'identifiant est au choix du détenteur du secret. Pour
 * rester borné dans tous les cas, un seau inactif depuis une minute (donc plein,
 * identique à un seau neuf) est effacé dès que la table atteint SEAUX_MAX.
 */
export class LimiteurDebit {
  private readonly seaux = new Map<string, { jetons: number; maj: number }>();

  constructor(private readonly horloge: () => number = Date.now) {}

  /** Nombre de seaux en mémoire. */
  get taille(): number {
    return this.seaux.size;
  }

  /** 0 si la requête passe ; sinon, secondes à attendre (Retry-After). */
  prendre(cle: string, parMinute: number): number {
    const t = this.horloge();
    if (this.seaux.size >= SEAUX_MAX && !this.seaux.has(cle)) {
      for (const [k, s] of this.seaux) {
        if (t - s.maj >= 60_000) this.seaux.delete(k);
      }
    }
    const seau = this.seaux.get(cle) ?? { jetons: parMinute, maj: t };
    seau.jetons = Math.min(
      parMinute,
      seau.jetons + ((t - seau.maj) / 60_000) * parMinute,
    );
    seau.maj = t;
    this.seaux.set(cle, seau);
    if (seau.jetons >= 1) {
      seau.jetons -= 1;
      return 0;
    }
    return Math.max(1, Math.ceil(((1 - seau.jetons) * 60) / parMinute));
  }
}
