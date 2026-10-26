import { Injectable, Logger } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import type {
  PersonnageSyncDto,
  SyncPersonnagesJeuDto,
} from './dto/game-personnages.dto';
import {
  AVANCE_PHOTO_MAX_MS,
  donneesDepuisPhoto,
  formatNumero,
  normaliserMcUuid,
  validerPhoto,
} from './game-personnages.regles';
import { GameService } from './game.service';

/**
 * Synchronisation des personnages du jeu vers l'API (CANDIDATURE_ET_DOUANE.md
 * §4.5, lot J). Le serveur de jeu n'a pas de port entrant : ShinobiCore pousse.
 *
 * Contrat, à reproduire tel quel côté plugin :
 *
 *   POST /v1/game/characters/sync
 *   Signature : GameSignatureGuard (en-têtes X-Reborn-*, voir game-signature.ts).
 *   Le serveur est celui de `X-Reborn-Server`, prouvé par son secret ; il n'est
 *   jamais lu dans le corps. Limite : 120 envois par minute et par serveur.
 *
 *   Corps : { "characters": [ <photo>, … ] }   (1 à 200 photos)
 *
 *   Photo complète d'un personnage (un champ facultatif absent vaut sa valeur
 *   par défaut ; un champ inconnu fait refuser la photo) :
 *     characterId    UUID      requis   ShinobiCharacter.id
 *     ownerMcUuid    UUID MC   requis   compte Minecraft propriétaire
 *     playerId       entier ≥ 0 ou null  N° ; absent tant qu'il n'est pas attribué
 *     firstName      texte 1-40 requis   prénom RP
 *     lastName       texte ≤ 40 ou null  nom de famille libre (clan « Autre »)
 *     clan           texte ≤ 40 ou null  clan acquis en session ; « Autre » = sans clan
 *     village        texte ≤ 40 ou null  valeur du mod (« Konohagakure »…)
 *     type           NINJA | CIVIL       défaut NINJA
 *     rank           code Rank ou null   GENIN, CHUNIN… (pas le libellé)
 *     sex            HOMME | FEMME | null
 *     age            entier 0-200 ou null
 *     dead           booléen   requis   mort RP
 *     deleted        booléen             supprimé en jeu ; défaut false
 *     playMinutes    entier ≥ 0 requis   temps de jeu du personnage
 *     createdAt      ISO 8601 ou null    création en jeu
 *     lastSeenAt     ISO 8601 ou null    dernière partie
 *     applicationId  UUID ou null        marque `application` du YAML
 *     snapshotAt     ISO 8601  requis   instant de la photo (ordre des envois)
 *   Dates : avec fuseau (`Instant.toString()`).
 *
 *   Réponse 200 : { ok: true, serveur, resultats: [ { index, characterId,
 *   statut, message?, avertissements? } ] }, un résultat par photo, dans l'ordre :
 *     cree     personnage créé
 *     maj      personnage mis à jour (ou photo identique : idempotent)
 *     perime   une photo plus récente est déjà en base : ignorée, ne pas renvoyer
 *     refuse   photo invalide (message) : ne pas renvoyer telle quelle
 *     conflit  N° déjà porté par un autre personnage de ce serveur : ne pas
 *              renvoyer, à faire vérifier par le staff
 *   `avertissements` (la photo est écrite quand même) : compte Reborn
 *   introuvable, candidature ignorée.
 *
 *   Erreurs de tout l'envoi : 400 (enveloppe invalide : ne pas renvoyer), 401
 *   (signature), 429 (Retry-After), 503 et 5xx : renvoyer plus tard, l'envoi
 *   est idempotent (file de reprise).
 *
 * Ordre : une photo dont `snapshotAt` est plus ancien que celui en base est
 * ignorée (`perime`). L'écriture est conditionnelle en base, deux envois
 * croisés ne s'écrasent donc pas à l'envers.
 *
 * Le compte Reborn est retrouvé par ownerMcUuid. La candidature n'est gardée
 * que si elle appartient à ce compte : un serveur ne peut pas rattacher le
 * dossier d'un autre joueur à un personnage.
 *
 * Pas d'entrée d'audit par envoi (une par minute et par serveur, au moins) :
 * le journal garde les créations et les conflits.
 */

export type StatutSyncPersonnage =
  | 'cree'
  | 'maj'
  | 'perime'
  | 'refuse'
  | 'conflit';

export interface ResultatSyncPersonnage {
  /** Position de la photo dans `characters`. */
  index: number;
  /** characterId en minuscules ; null si la photo n'en porte pas de lisible. */
  characterId: string | null;
  statut: StatutSyncPersonnage;
  message?: string;
  avertissements?: string[];
}

export interface SyncPersonnagesVue {
  ok: true;
  serveur: string;
  resultats: ResultatSyncPersonnage[];
}

const MSG_COMPTE_INCONNU =
  "ownerMcUuid : aucun compte Reborn lié à ce compte Minecraft ; le personnage n'apparaît dans aucun launcher tant que le compte n'existe pas.";
const MSG_CANDIDATURE_IGNOREE =
  "applicationId ignoré : candidature introuvable ou d'un autre compte.";
const MSG_PHOTO_FUTURE =
  "snapshotAt : photo datée dans le futur (vérifie l'heure du serveur de jeu).";

/** Recherches faites une seule fois par envoi. */
interface Caches {
  comptes: Map<string, Promise<string | null>>;
  candidatures: Map<string, Promise<string | null>>;
}

@Injectable()
export class GamePersonnagesService {
  private readonly logger = new Logger(GamePersonnagesService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly game: GameService,
  ) {}

  /** POST /v1/game/characters/sync — une photo après l'autre, un résultat par photo. */
  async synchroniser(
    dto: SyncPersonnagesJeuDto,
    serveur: string,
    maintenant: Date = new Date(),
  ): Promise<SyncPersonnagesVue> {
    const caches: Caches = { comptes: new Map(), candidatures: new Map() };
    const resultats: ResultatSyncPersonnage[] = [];
    // En série : deux photos du même personnage dans un envoi restent ordonnées.
    for (let i = 0; i < dto.characters.length; i++) {
      resultats.push(
        await this.une(i, dto.characters[i], serveur, maintenant, caches),
      );
    }

    const compte = (s: StatutSyncPersonnage) =>
      resultats.filter((r) => r.statut === s).length;
    const crees = compte('cree');
    const refuses = compte('refuse');
    if (crees > 0) {
      this.logger.log(`personnages ${serveur} : ${crees} créé(s)`);
    }
    if (refuses > 0) {
      this.logger.warn(
        `personnages ${serveur} : ${refuses} photo(s) refusée(s), première : ${
          resultats.find((r) => r.statut === 'refuse')?.message ?? '?'
        }`,
      );
    }
    return { ok: true, serveur, resultats };
  }

  private async une(
    index: number,
    brut: unknown,
    serveur: string,
    maintenant: Date,
    caches: Caches,
  ): Promise<ResultatSyncPersonnage> {
    const lu = validerPhoto(brut);
    if (!lu.ok) {
      return {
        index,
        characterId: lu.characterId,
        statut: 'refuse',
        message: lu.message,
      };
    }
    const photo = lu.photo;
    const characterId = photo.characterId.toLowerCase();
    const snapshotAt = new Date(photo.snapshotAt);
    if (snapshotAt.getTime() > maintenant.getTime() + AVANCE_PHOTO_MAX_MS) {
      return {
        index,
        characterId,
        statut: 'refuse',
        message: MSG_PHOTO_FUTURE,
      };
    }

    const avertissements: string[] = [];
    const ownerMcUuid = normaliserMcUuid(photo.ownerMcUuid);
    const userId = await this.compte(ownerMcUuid, caches);
    if (!userId) avertissements.push(MSG_COMPTE_INCONNU);

    let applicationId: string | null = null;
    if (photo.applicationId) {
      const id = photo.applicationId.toLowerCase();
      const proprietaire = await this.proprietaireCandidature(id, caches);
      if (userId && proprietaire === userId) applicationId = id;
      else avertissements.push(MSG_CANDIDATURE_IGNOREE);
    }

    const resultat = await this.ecrire(photo, {
      serveur,
      characterId,
      ownerMcUuid,
      userId,
      applicationId,
      snapshotAt,
    });
    return {
      index,
      characterId,
      ...resultat,
      ...(avertissements.length > 0 ? { avertissements } : {}),
    };
  }

  /**
   * Écriture conditionnelle : rien n'est écrit si la base porte une photo plus
   * récente. Une création concurrente du même personnage (deux envois croisés)
   * est relue une fois, puis traitée comme une mise à jour.
   */
  private async ecrire(
    photo: PersonnageSyncDto,
    c: {
      serveur: string;
      characterId: string;
      ownerMcUuid: string;
      userId: string | null;
      applicationId: string | null;
      snapshotAt: Date;
    },
  ): Promise<Pick<ResultatSyncPersonnage, 'statut' | 'message'>> {
    for (let essai = 0; essai < 2; essai++) {
      const existant = await this.prisma.gameCharacter.findUnique({
        where: {
          serverId_characterId: {
            serverId: c.serveur,
            characterId: c.characterId,
          },
        },
        select: { id: true, snapshotAt: true, deletedAt: true },
      });
      if (existant && existant.snapshotAt > c.snapshotAt) {
        return { statut: 'perime' };
      }
      const donnees = donneesDepuisPhoto(photo, {
        ownerMcUuid: c.ownerMcUuid,
        userId: c.userId,
        applicationId: c.applicationId,
        snapshotAt: c.snapshotAt,
        deletedAtExistant: existant?.deletedAt ?? null,
      });
      try {
        if (existant) {
          const { count } = await this.prisma.gameCharacter.updateMany({
            where: { id: existant.id, snapshotAt: { lte: c.snapshotAt } },
            data: donnees,
          });
          return { statut: count > 0 ? 'maj' : 'perime' };
        }
        await this.prisma.gameCharacter.create({
          data: { serverId: c.serveur, characterId: c.characterId, ...donnees },
        });
        return { statut: 'cree' };
      } catch (err) {
        if (
          err instanceof Prisma.PrismaClientKnownRequestError &&
          err.code === 'P2002'
        ) {
          if (JSON.stringify(err.meta?.target ?? '').includes('playerId')) {
            const numero = formatNumero(donnees.playerId) ?? '?';
            this.logger.warn(
              `personnages ${c.serveur} : N° ${numero} déjà porté par un autre personnage (photo de ${c.characterId} non écrite)`,
            );
            return {
              statut: 'conflit',
              message: `playerId : le N° ${numero} est déjà porté par un autre personnage de ce serveur.`,
            };
          }
          if (!existant && essai === 0) continue;
        }
        throw err;
      }
    }
    // Inatteignable : le second essai rend un statut ou lève l'erreur.
    return { statut: 'perime' };
  }

  /** Compte Reborn d'un compte Minecraft (forme canonique), une recherche par envoi. */
  private compte(ownerMcUuid: string, caches: Caches): Promise<string | null> {
    let p = caches.comptes.get(ownerMcUuid);
    if (!p) {
      p = this.game.findUserByMcUuid(ownerMcUuid).then((u) => u?.id ?? null);
      caches.comptes.set(ownerMcUuid, p);
    }
    return p;
  }

  /** Compte propriétaire d'une candidature, ou null si elle n'existe pas. */
  private proprietaireCandidature(
    applicationId: string,
    caches: Caches,
  ): Promise<string | null> {
    let p = caches.candidatures.get(applicationId);
    if (!p) {
      p = this.prisma.whitelistApplication
        .findUnique({ where: { id: applicationId }, select: { userId: true } })
        .then((a) => a?.userId ?? null);
      caches.candidatures.set(applicationId, p);
    }
    return p;
  }
}
