import { createReadStream, mkdirSync } from 'node:fs';
import { readdir, stat, unlink, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import type { Readable } from 'node:stream';
import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
  OnApplicationBootstrap,
  OnModuleDestroy,
  UnsupportedMediaTypeException,
} from '@nestjs/common';
import { AuditService } from '../audit/audit.service';
import { PrismaService } from '../prisma/prisma.service';
import {
  bornesV2,
  formatDateParis,
  INACTIVITE_BROUILLON_VOIX_MS,
} from './whitelist.rules';
import {
  estCleVoix,
  genererCleVoix,
  resolveVoixDir,
  typeAnnonce,
  typeDeCle,
  typeReel,
  VOIX_OCTETS_MIN,
  VOIX_TYPES,
  VoixType,
} from './voix.config';

/**
 * Fichiers de voix de la candidature (privés) : écriture, effacement, écoute
 * par le staff et par le candidat lui-même, et purge planifiée (Q2 : effacée
 * 30 jours après la décision finale ; complément du 25/10 : la voix d'un
 * brouillon abandonné, jamais envoyée, est effacée après 30 jours sans activité
 * sur le brouillon ; un fichier que plus rien ne référence est effacé au bout
 * d'une heure).
 *
 * Le chemin d'un fichier n'est jamais bâti sur une donnée du client : la clé
 * est générée ici et revérifiée (VOIX_CLE_RE) avant tout accès disque.
 */
/** Fréquence de la purge des voix arrivées en fin de garde. */
const PURGE_TOUTES_LES_MS = 60 * 60 * 1000;
/**
 * Un fichier sans référence en base est effacé après ce délai : il laisse le
 * temps à un dépôt en cours d'écrire sa référence (fichier écrit avant la ligne).
 */
const ORPHELIN_APRES_MS = 60 * 60 * 1000;
/** Taille des lots lus en base par la purge. */
const LOT_PURGE = 500;

/**
 * Réécoute du candidat (`GET /whitelist/me/voix?source=`) : `brouillon` (onglets
 * Voix et Envoi : le brouillon, sinon la candidature) ou `candidature` (la voix
 * envoyée seulement).
 */
export const SOURCES_VOIX_CANDIDAT = ['brouillon', 'candidature'] as const;
export type SourceVoixCandidat = (typeof SOURCES_VOIX_CANDIDAT)[number];

@Injectable()
export class WhitelistVoixService
  implements OnApplicationBootstrap, OnModuleDestroy
{
  private readonly logger = new Logger(WhitelistVoixService.name);
  private readonly dossier = resolveVoixDir();
  private minuterie: NodeJS.Timeout | null = null;

  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {
    mkdirSync(this.dossier, { recursive: true });
  }

  /**
   * Purge au démarrage (une API arrêtée longtemps rattrape son retard), puis
   * toutes les heures. Minuterie simple : `@nestjs/schedule` n'est pas
   * chargeable par jest, et une heure de décalage ne change rien à une garde
   * de 30 jours.
   */
  onApplicationBootstrap(): void {
    void this.purgePeriodique();
    this.minuterie = setInterval(
      () => void this.purgePeriodique(),
      PURGE_TOUTES_LES_MS,
    );
    this.minuterie.unref();
  }

  onModuleDestroy(): void {
    if (this.minuterie) clearInterval(this.minuterie);
    this.minuterie = null;
  }

  async purgePeriodique(): Promise<void> {
    try {
      await this.purger();
    } catch (err) {
      this.logger.warn(`purge des voix : ${(err as Error).message}`);
    }
  }

  /**
   * Contrôle un envoi : type annoncé, signature d'octets, taille, durée
   * déclarée. Lève 415 ou 400 avec un message utile.
   */
  controler(
    contentType: string | undefined,
    dureeBrute: unknown,
    corps: Buffer,
  ): {
    type: VoixType;
    dureeSec: number;
  } {
    const annonce = typeAnnonce(contentType);
    if (!annonce) {
      throw new UnsupportedMediaTypeException(
        `Format d'enregistrement non accepté. Formats acceptés : ${VOIX_TYPES.join(', ')}.`,
      );
    }
    if (corps.length < VOIX_OCTETS_MIN) {
      throw new BadRequestException(
        "L'enregistrement est vide ou incomplet. Refais-le.",
      );
    }
    if (typeReel(corps) !== annonce) {
      throw new BadRequestException(
        "Le fichier reçu ne correspond pas au format annoncé. Refais l'enregistrement.",
      );
    }
    const b = bornesV2().voixSec;
    const duree = typeof dureeBrute === 'string' ? Number(dureeBrute) : NaN;
    if (!Number.isInteger(duree)) {
      throw new BadRequestException(
        'Durée de l’enregistrement manquante (paramètre dureeSec, en secondes).',
      );
    }
    if (duree < b.min)
      throw new BadRequestException(`${b.min} secondes au moins.`);
    if (duree > b.max)
      throw new BadRequestException(`Une minute au plus (${b.max} secondes).`);
    return { type: annonce, dureeSec: duree };
  }

  /** Écrit les octets sous une clé neuve et la renvoie. */
  async ecrire(corps: Buffer, type: VoixType): Promise<string> {
    const cle = genererCleVoix(type);
    await writeFile(join(this.dossier, cle), corps, { flag: 'wx' });
    return cle;
  }

  /** Le fichier existe-t-il encore sur le disque ? */
  async existe(cle: string | null | undefined): Promise<boolean> {
    if (!estCleVoix(cle)) return false;
    try {
      return (await stat(join(this.dossier, cle))).isFile();
    } catch {
      return false;
    }
  }

  /**
   * Efface un fichier. Renvoie true si le fichier n'est plus sur le disque
   * (effacé, ou déjà absent), false si l'effacement a échoué : la référence en
   * base ne doit alors pas être vidée, la purge réessaiera. Ne lève jamais : un
   * échec est journalisé. Une clé invalide n'a jamais désigné un fichier d'ici.
   */
  async supprimer(cle: string | null | undefined): Promise<boolean> {
    if (!cle) return true;
    if (!estCleVoix(cle)) {
      this.logger.warn('clé de voix invalide ignorée à l’effacement');
      return true;
    }
    try {
      await unlink(join(this.dossier, cle));
      return true;
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code === 'ENOENT') return true;
      this.logger.error(
        `effacement d'une voix impossible : ${(err as Error).message}`,
      );
      return false;
    }
  }

  /**
   * Ouvre la voix d'une candidature pour l'écoute staff (WHITELIST_REVIEWER,
   * vérifié par le contrôleur). Chaque écoute est tracée à l'audit.
   */
  async ouvrirPourStaff(
    applicationId: string,
    staffUserId: string,
  ): Promise<{ flux: Readable; type: string; taille: number }> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      select: {
        userId: true,
        voiceKey: true,
        voiceMimeType: true,
        voiceDeletedAt: true,
      },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    if (!app.voiceKey) {
      throw new NotFoundException(
        app.voiceDeletedAt
          ? `Enregistrement effacé le ${formatDateParis(app.voiceDeletedAt)} (fin de la durée de garde).`
          : 'Aucun enregistrement vocal pour cette candidature.',
      );
    }
    if (!estCleVoix(app.voiceKey) || !(await this.existe(app.voiceKey))) {
      throw new NotFoundException('Enregistrement introuvable sur le serveur.');
    }
    const chemin = join(this.dossier, app.voiceKey);
    const taille = (await stat(chemin)).size;
    void this.audit.log({
      actorId: staffUserId,
      action: 'whitelist.voix.ecoute',
      targetUserId: app.userId,
      targetEntity: `whitelist:${applicationId}`,
      source: 'panel',
    });
    return {
      flux: createReadStream(chemin),
      type: app.voiceMimeType ?? typeDeCle(app.voiceKey),
      taille,
    };
  }

  /**
   * Ouvre sa propre voix pour le candidat (réécoute, y compris depuis un autre
   * PC) : celle du brouillon si elle existe encore, sinon celle de la
   * candidature. `source: 'candidature'` (« Mon dossier » du launcher) : la
   * voix ENVOYÉE seulement, même quand un nouvel enregistrement attend dans le
   * brouillon. Rien n'est servi d'un autre compte : tout part de `userId`.
   */
  async ouvrirPourCandidat(
    userId: string,
    source?: SourceVoixCandidat,
  ): Promise<{ flux: Readable; type: string; taille: number }> {
    const [brouillon, app] = await Promise.all([
      this.prisma.candidatureBrouillon.findUnique({
        where: { userId },
        select: { voiceKey: true, voiceMimeType: true },
      }),
      this.prisma.whitelistApplication.findUnique({
        where: { userId },
        select: { voiceKey: true, voiceMimeType: true, voiceDeletedAt: true },
      }),
    ]);
    let fichier: { voiceKey: string; voiceMimeType: string | null } | null =
      null;
    if (
      source !== 'candidature' &&
      brouillon?.voiceKey &&
      (await this.existe(brouillon.voiceKey))
    ) {
      fichier = {
        voiceKey: brouillon.voiceKey,
        voiceMimeType: brouillon.voiceMimeType,
      };
    } else if (app?.voiceKey && (await this.existe(app.voiceKey))) {
      fichier = { voiceKey: app.voiceKey, voiceMimeType: app.voiceMimeType };
    }
    if (!fichier) {
      throw new NotFoundException(
        app?.voiceDeletedAt && !app.voiceKey
          ? `Enregistrement effacé le ${formatDateParis(app.voiceDeletedAt)} (fin de la durée de garde).`
          : 'Aucun enregistrement vocal.',
      );
    }
    const chemin = join(this.dossier, fichier.voiceKey);
    const taille = (await stat(chemin)).size;
    return {
      flux: createReadStream(chemin),
      type: fichier.voiceMimeType ?? typeDeCle(fichier.voiceKey),
      taille,
    };
  }

  /**
   * Efface les voix dont la durée de garde est passée :
   * - candidatures : `voicePurgeAt` atteint (décision finale + 30 jours) ;
   *   la référence n'est vidée que si le fichier a bien disparu ;
   * - brouillons abandonnés : voix jamais envoyée, brouillon sans activité
   *   depuis 30 jours (voir `purgerBrouillonsAbandonnes`) ;
   * - fichiers orphelins : plus aucune candidature ni aucun brouillon ne les
   *   référence (effacement manqué, dépôts croisés), au-delà d'une heure.
   * Renvoie le nombre de fichiers effacés.
   */
  async purger(now: Date = new Date()): Promise<number> {
    let n = 0;
    const apps = await this.prisma.whitelistApplication.findMany({
      where: { voiceKey: { not: null }, voicePurgeAt: { lte: now } },
      select: { id: true, voiceKey: true },
      take: LOT_PURGE,
    });
    for (const a of apps) {
      if (!(await this.supprimer(a.voiceKey))) continue;
      await this.prisma.whitelistApplication.update({
        where: { id: a.id },
        data: { voiceKey: null, voiceDeletedAt: now },
      });
      n++;
    }

    n += await this.purgerBrouillonsAbandonnes(now);
    n += await this.effacerOrphelins(now);
    if (n > 0)
      this.logger.log(
        `${n} enregistrement(s) vocal(aux) effacé(s) (fin de la durée de garde ou fichier orphelin)`,
      );
    return n;
  }

  /**
   * Voix des brouillons abandonnés (complément du 25/10) : jamais envoyée, et
   * brouillon sans activité depuis 30 jours. L'activité, c'est un
   * enregistrement du brouillon (sauvegarde automatique, dépôt de voix), lu
   * dans `updatedAt` ; le brouillon lui-même est gardé, sans voix.
   *
   * La référence est vidée d'abord, sous condition (même clé, toujours
   * inactif) : un joueur revenu entre la lecture et l'écriture garde sa voix,
   * et un nouveau dépôt n'est jamais écrasé. Le fichier est effacé ensuite,
   * sauf si une candidature vient de le reprendre (envoi en cours). S'il
   * résiste, le balayage des orphelins le reprendra.
   */
  private async purgerBrouillonsAbandonnes(now: Date): Promise<number> {
    const limite = new Date(now.getTime() - INACTIVITE_BROUILLON_VOIX_MS);
    const brouillons = await this.prisma.candidatureBrouillon.findMany({
      where: { voiceKey: { not: null }, updatedAt: { lte: limite } },
      select: { id: true, voiceKey: true },
      take: LOT_PURGE,
    });
    let n = 0;
    for (const b of brouillons) {
      const { count } = await this.prisma.candidatureBrouillon.updateMany({
        where: { id: b.id, voiceKey: b.voiceKey, updatedAt: { lte: limite } },
        data: {
          voiceKey: null,
          voiceMimeType: null,
          voiceDurationSec: null,
          voiceUploadedAt: null,
        },
      });
      if (count === 0) continue;
      const reprise = await this.prisma.whitelistApplication.count({
        where: { voiceKey: b.voiceKey },
      });
      if (reprise > 0) continue;
      if (await this.supprimer(b.voiceKey)) n++;
    }
    return n;
  }

  /**
   * Balaye le dossier des voix : un fichier de plus d'une heure qu'aucune
   * candidature ni aucun brouillon ne référence est effacé. Rattrape les
   * effacements qui ont échoué (retrait, renvoi) et les fichiers laissés par
   * des dépôts croisés.
   */
  private async effacerOrphelins(now: Date): Promise<number> {
    let noms: string[];
    try {
      noms = await readdir(this.dossier);
    } catch (err) {
      this.logger.warn(
        `lecture du dossier des voix impossible : ${(err as Error).message}`,
      );
      return 0;
    }
    const limite = now.getTime() - ORPHELIN_APRES_MS;
    const vieux: string[] = [];
    for (const nom of noms) {
      if (!estCleVoix(nom)) continue;
      try {
        const s = await stat(join(this.dossier, nom));
        if (s.isFile() && s.mtimeMs <= limite) vieux.push(nom);
      } catch {
        // Effacé entre-temps.
      }
    }
    let n = 0;
    for (let i = 0; i < vieux.length; i += LOT_PURGE) {
      const lot = vieux.slice(i, i + LOT_PURGE);
      const [apps, brouillons] = await Promise.all([
        this.prisma.whitelistApplication.findMany({
          where: { voiceKey: { in: lot } },
          select: { voiceKey: true },
        }),
        this.prisma.candidatureBrouillon.findMany({
          where: { voiceKey: { in: lot } },
          select: { voiceKey: true },
        }),
      ]);
      const references = new Set(
        [...apps, ...brouillons].map((r) => r.voiceKey),
      );
      for (const cle of lot) {
        if (!references.has(cle) && (await this.supprimer(cle))) n++;
      }
    }
    return n;
  }
}
