import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import {
  GARDE_NONCE_MS,
  LimiteurDebit,
  SEAUX_MAX,
  TOLERANCE_HORODATAGE_S,
  chaineASigner,
  lireEnTetes,
  lireSecretsJeu,
  secretsDuServeur,
  signatureValide,
  type RequeteJeu,
  type SecretsJeu,
} from './game-signature';

/** Limite d'une route : nom (clé du seau) et requêtes par minute, par serveur. */
export interface LimiteJeu {
  nom: string;
  parMinute: number;
}

export type VerificationJeu =
  | { ok: true; serveur: string }
  | {
      ok: false;
      /** 401 signature, 429 débit, 503 routes coupées ou base indisponible. */
      statut: 401 | 429 | 503;
      message: string;
      /** 429 : secondes avant de réessayer. */
      reessayerDansS?: number;
    };

/** Une alerte de refus au plus toutes les 10 s par motif : une rafale ne noie pas le journal. */
const INTERVALLE_ALERTE_MS = 10_000;

/**
 * Vérifie les requêtes signées du serveur de jeu (contrat : game-signature.ts).
 * Ordre : en-têtes → serveur connu → horodatage → signature → débit → nonce.
 * Le nonce n'est inscrit qu'après une signature valide : une requête anonyme
 * n'écrit jamais en base.
 */
@Injectable()
export class GameSignatureService {
  private readonly logger = new Logger(GameSignatureService.name);
  private readonly secrets: SecretsJeu;
  private readonly limiteur: LimiteurDebit;
  private readonly alertes = new Map<string, number>();

  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    this.limiteur = new LimiteurDebit();
    this.secrets = lireSecretsJeu({
      REBORN_GAME_SECRET: config.get<string>('REBORN_GAME_SECRET'),
      REBORN_GAME_SECRETS: config.get<string>('REBORN_GAME_SECRETS'),
      REBORN_SERVER_ID: config.get<string>('REBORN_SERVER_ID'),
      REBORN_SERVER_IDS: config.get<string>('REBORN_SERVER_IDS'),
      REBORN_WEBHOOK_SECRET: config.get<string>('REBORN_WEBHOOK_SECRET'),
      REBORN_PLAY_TOKEN_SECRET: config.get<string>('REBORN_PLAY_TOKEN_SECRET'),
    });
    for (const a of this.secrets.avertissements) this.logger.error(a);
    if (this.secrets.mode === 'par-serveur') {
      this.logger.log(
        `Routes signées du jeu : un secret par serveur (${[...this.secrets.parServeur.keys()].join(', ')}).`,
      );
    } else if (this.secrets.mode === 'unique') {
      // §3.9 et §3.12 : un secret différent par serveur. Ce mode reste admis, signalé.
      const admis = [...this.secrets.serveursUnique];
      this.logger.warn(
        admis.length > 0
          ? `Routes signées du jeu : un seul secret (REBORN_GAME_SECRET) pour ${admis.join(', ')} (REBORN_SERVER_IDS / REBORN_SERVER_ID). Reco : un secret par serveur (REBORN_GAME_SECRETS).`
          : "Routes signées du jeu : un seul secret (REBORN_GAME_SECRET) et aucune liste de serveurs (REBORN_SERVER_IDS / REBORN_SERVER_ID) : l'identifiant du serveur est celui que déclare l'appelant. Reco : un secret par serveur (REBORN_GAME_SECRETS).",
      );
    } else {
      this.logger.warn(
        'Routes signées du jeu COUPÉES (aucun secret valide dans REBORN_GAME_SECRET ou REBORN_GAME_SECRETS) : /v1/game/acces, /v1/game/douane/* et /v1/game/characters/sync répondent 503.',
      );
    }
  }

  /** true si au moins un secret est configuré. */
  get actif(): boolean {
    return this.secrets.mode !== 'aucun';
  }

  async verifier(req: RequeteJeu, limite: LimiteJeu): Promise<VerificationJeu> {
    if (!this.actif) {
      return {
        ok: false,
        statut: 503,
        message: 'Routes du jeu coupées : aucun secret configuré côté API.',
      };
    }

    const lus = lireEnTetes(req.enTetes);
    if (typeof lus === 'string') return this.refus('en-tetes', lus);

    const secrets = secretsDuServeur(this.secrets, lus.serveur);
    if (secrets.length === 0) {
      return this.refus(
        'serveur',
        `Serveur « ${lus.serveur} » inconnu de l'API (${
          this.secrets.mode === 'unique'
            ? 'REBORN_SERVER_IDS / REBORN_SERVER_ID'
            : 'REBORN_GAME_SECRETS'
        }).`,
      );
    }

    const ecartS = Math.abs(Date.now() / 1000 - lus.horodatage);
    if (ecartS > TOLERANCE_HORODATAGE_S) {
      return this.refus(
        'horodatage',
        `Horodatage hors fenêtre (± ${TOLERANCE_HORODATAGE_S} s) : vérifie l'heure du serveur de jeu.`,
      );
    }

    const chaine = chaineASigner(
      req.methode,
      req.chemin,
      lus.serveur,
      lus.horodatageBrut,
      lus.nonce,
      req.corps,
    );
    if (!signatureValide(secrets, chaine, lus.signature)) {
      return this.refus('signature', 'Signature invalide.');
    }

    const attente = this.limiteur.prendre(
      `${lus.serveur}:${limite.nom}`,
      limite.parMinute,
    );
    if (attente > 0) {
      this.alerter(
        `debit:${lus.serveur}:${limite.nom}`,
        `limite de débit atteinte pour ${lus.serveur} sur ${limite.nom} (${limite.parMinute}/min)`,
      );
      return {
        ok: false,
        statut: 429,
        message: `Trop de requêtes : ${limite.parMinute} par minute au plus sur cette route.`,
        reessayerDansS: attente,
      };
    }

    try {
      await this.prisma.gameRequestNonce.create({
        data: { nonce: lus.nonce, serverId: lus.serveur },
      });
    } catch (err) {
      if (
        err instanceof Prisma.PrismaClientKnownRequestError &&
        err.code === 'P2002'
      ) {
        return this.refus('nonce', 'Nonce déjà utilisé : requête rejouée.');
      }
      this.logger.error(
        `nonce non enregistré (${(err as Error).message}) : requête refusée`,
      );
      return {
        ok: false,
        statut: 503,
        message: 'Base indisponible : réessaie dans un instant.',
      };
    }
    return { ok: true, serveur: lus.serveur };
  }

  /** Supprime les nonces plus vieux que leur durée de garde. Retourne le nombre effacé. */
  async purgerNonces(maintenant = new Date()): Promise<number> {
    const { count } = await this.prisma.gameRequestNonce.deleteMany({
      where: {
        createdAt: { lt: new Date(maintenant.getTime() - GARDE_NONCE_MS) },
      },
    });
    return count;
  }

  private refus(motif: string, message: string): VerificationJeu {
    this.alerter(motif, `requête du jeu refusée : ${message}`);
    return { ok: false, statut: 401, message };
  }

  private alerter(cle: string, texte: string): void {
    const t = Date.now();
    const derniere = this.alertes.get(cle) ?? 0;
    if (t - derniere < INTERVALLE_ALERTE_MS) return;
    // Clés `debit:<serveur>:<route>` : bornées comme les seaux du limiteur. Une
    // alerte plus vieille que l'intervalle vaut une alerte absente.
    if (this.alertes.size >= SEAUX_MAX) {
      for (const [k, quand] of this.alertes) {
        if (t - quand >= INTERVALLE_ALERTE_MS) this.alertes.delete(k);
      }
    }
    this.alertes.set(cle, t);
    this.logger.warn(texte);
  }
}
