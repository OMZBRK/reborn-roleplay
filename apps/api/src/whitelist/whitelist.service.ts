import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  HttpException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import {
  AppStatus,
  CharacterSex,
  CharacterType,
  DiscoverySource,
  NarutoKnowledge,
  OralVia,
  Prisma,
  Role,
  RpYears,
  WhitelistApplication,
} from '@prisma/client';
import { estActif, lireRetardMax } from '../douane/douane.regles';
import {
  OPTIONS_TRANSACTION,
  traduireConflitTransaction,
  verrouillerCandidature,
} from '../douane/douane.verrous';
import { PrismaService } from '../prisma/prisma.service';
import { WebhooksService } from '../webhooks/webhooks.service';
import { BrouillonDto, ChampsCandidature } from './dto/whitelist.dto';
import { WhitelistMessagesService } from './whitelist-messages.service';
import { WhitelistVoixService } from './whitelist-voix.service';
import {
  bornesV2,
  calculerSuivi,
  CHAMPS_ENVOI,
  CHAMPS_ENVOI_COMMUNS,
  CHAMPS_ENVOI_HRP,
  CHAMPS_ENVOI_RP,
  CLANS,
  DELAI_NOUVELLE_CANDIDATURE_MS,
  estEnvoiV2,
  formatDateParis,
  LIBELLES,
  MSG_LAUNCHER_A_JOUR,
  PhaseCandidature,
  SANS_CLAN,
  SuiviCandidature,
  verifierBrouillon,
  verifierEnvoi,
  VILLAGES,
} from './whitelist.rules';
import { VOIX_TYPES, voixOctetsMax } from './voix.config';

/** Une décision du staff est arrivée entre la lecture et l'écriture du candidat. */
const MSG_RELUE_ENTRE_TEMPS =
  'Ta candidature vient d’être relue : recharge-la.';

/** Client Prisma : le service, ou une transaction en cours. */
type ClientPrisma = PrismaService | Prisma.TransactionClient;

// Candidature telle que le candidat la voit (launcher). Les notes PRIVÉES du
// staff (writtenStaffNotes, oralNotes), l'identité de l'examinateur et le nom
// du fichier de voix n'y figurent JAMAIS.
//
// Compatibilité : l'ancien launcher (struct Rust WhitelistApplicationDto) exige
// dob, motivation, experience et appearance en texte. Ils restent des chaînes,
// vides pour une candidature v2 ; les champs ajoutés sont ignorés par lui.
export interface WhitelistApplicationDto {
  id: string;
  status: AppStatus;
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  /** 1 = ancien formulaire, 2 = candidature v2. */
  formVersion: number;
  /** Phase calculée (voir `suivi` dans GET /whitelist/me). */
  phase: PhaseCandidature;
  // Partie HRP
  playerAge: number | null;
  /** v1 seulement, AAAA-MM-JJ ; '' en v2. */
  dob: string;
  rpYears: RpYears | null;
  narutoKnowledge: NarutoKnowledge | null;
  discoverySource: DiscoverySource | null;
  availability: string;
  /** v1 seulement ; '' en v2. */
  motivation: string;
  /** v1 seulement ; '' en v2. */
  experience: string;
  // Partie RP
  firstName: string;
  lastName: string;
  village: string;
  characterType: CharacterType | null;
  characterSex: CharacterSex | null;
  characterAge: number | null;
  support: string | null;
  history: string;
  /** v1 seulement ; '' en v2. */
  appearance: string;
  objectives: string;
  /** Enregistrement vocal joint, ou null (v1, ou effacé : voir voiceDeletedAt). */
  voix: {
    mimeType: string;
    durationSec: number | null;
    uploadedAt: string | null;
  } | null;
  voiceDeletedAt: string | null;
  rulesAcceptedAt: string | null;
  voiceConsentAt: string | null;
  // Méta et décisions visibles du joueur
  submittedAt: string;
  reviewedAt: string | null;
  /** Commentaire de l'ÉCRIT, visible du joueur. */
  reviewNotes: string | null;
  /** Pseudo du staff qui a tranché l'écrit en dernier (« Commentaire du staff · Ayame »). */
  reviewerName: string | null;
  writtenApprovedAt: string | null;
  oralStatus: AppStatus;
  oralDecidedAt: string | null;
  oralVia: OralVia | null;
  /** Motif de l'oral, visible du joueur. */
  oralReason: string | null;
  // Assignation staff (flow DM bot) : « Pris en charge par @X depuis Yh »,
  // puis « Demander une reprise » après 4 h (POST /v1/whitelist/me/reclaim).
  assignee: { username: string | null } | null;
  assignedAt: string | null;
}

export interface BrouillonVue {
  formVersion: number;
  data: Record<string, unknown>;
  updatedAt: string;
  voix: {
    mimeType: string;
    durationSec: number | null;
    uploadedAt: string | null;
  } | null;
}

/** Colonnes de la partie HRP (rouvertes ensemble par « HRP à revoir »). */
const COLONNES_HRP = [
  'playerAge',
  'dob',
  'rpYears',
  'narutoKnowledge',
  'discoverySource',
  'availability',
  'motivation',
  'experience',
] as const;
/** Colonnes de la partie RP (la voix suit la partie RP, Q2). */
const COLONNES_RP = [
  'firstName',
  'lastName',
  'village',
  'characterType',
  'characterSex',
  'characterAge',
  'support',
  'history',
  'appearance',
  'objectives',
] as const;

type Formulaire = Pick<
  Prisma.WhitelistApplicationUncheckedCreateInput,
  (typeof COLONNES_HRP)[number] | (typeof COLONNES_RP)[number]
> & { formVersion: number };

type ApplicationLue = WhitelistApplication & {
  assignedTo?: {
    minecraftUsername: string;
    discordUsername: string | null;
  } | null;
};

interface Contexte {
  user: {
    id: string;
    role: Role;
    oralPassedAt: Date | null;
    whitelistRejectedAt: Date | null;
    minecraftUsername: string;
  };
  app: ApplicationLue | null;
  suivi: SuiviCandidature;
}

@Injectable()
export class WhitelistService {
  private readonly logger = new Logger(WhitelistService.name);
  /** Retard maximal après l'heure de convocation : même réglage que la douane et le jeton de jeu. */
  private readonly retardMaxMin = lireRetardMax(
    process.env.DOUANE_RETARD_MAX_MIN,
  );
  /** REBORN_DOUANE_ENABLED, lu comme le jeton de jeu : la douane se passe en jeu. */
  private readonly douaneEnJeu = estActif(process.env.REBORN_DOUANE_ENABLED);

  constructor(
    private readonly prisma: PrismaService,
    private readonly webhooks: WebhooksService,
    private readonly messages: WhitelistMessagesService,
    private readonly voix: WhitelistVoixService,
  ) {}

  // ── Lecture ────────────────────────────────────────────────

  async getMine(userId: string): Promise<{
    application: WhitelistApplicationDto | null;
    suivi: SuiviCandidature;
  }> {
    const ctx = await this.contexte(userId);
    const reviewerName = ctx.app?.reviewedBy
      ? await this.nomStaff(ctx.app.reviewedBy)
      : null;
    return {
      application: ctx.app
        ? this.toDto(ctx.app, ctx.suivi.phase, reviewerName)
        : null,
      suivi: ctx.suivi,
    };
  }

  /** Listes fermées, bornes et libellés : le launcher ne code rien en dur (§2.3). */
  options() {
    const b = bornesV2();
    const liste = <K extends string>(t: Record<K, string>) =>
      (Object.entries(t) as Array<[K, string]>).map(([valeur, libelle]) => ({
        valeur,
        libelle,
      }));
    return {
      formVersion: 2,
      villages: VILLAGES.map((v) => ({ ...v })),
      clans: CLANS.map((c) => ({ ...c, surSession: true })),
      sansClan: SANS_CLAN,
      bornes: b,
      listes: {
        rpYears: liste(LIBELLES.rpYears),
        narutoKnowledge: liste(LIBELLES.narutoKnowledge),
        discoverySource: liste(LIBELLES.discoverySource),
        characterType: liste(LIBELLES.characterType),
        characterSex: liste(LIBELLES.characterSex),
      },
      voix: {
        types: [...VOIX_TYPES],
        octetsMax: voixOctetsMax(),
        dureeMinSec: b.voixSec.min,
        dureeMaxSec: b.voixSec.max,
      },
      delaiNouvelleCandidatureHeures: DELAI_NOUVELLE_CANDIDATURE_MS / 3600000,
    };
  }

  // ── Brouillon serveur (§2.5) ───────────────────────────────

  async getBrouillon(
    userId: string,
  ): Promise<{ brouillon: BrouillonVue | null }> {
    const b = await this.prisma.candidatureBrouillon.findUnique({
      where: { userId },
    });
    return { brouillon: b ? this.vueBrouillon(b) : null };
  }

  async putBrouillon(
    userId: string,
    dto: BrouillonDto,
  ): Promise<{ brouillon: BrouillonVue }> {
    const ctx = await this.contexte(userId);
    if (ctx.suivi.phase === 'ACCEPTE') {
      throw new ConflictException(
        'Ta candidature est acceptée : plus de brouillon à enregistrer.',
      );
    }
    const refus = verifierBrouillon(dto.data);
    if (refus) throw new HttpException(refus.message, refus.code);
    const data = dto.data as Prisma.InputJsonObject;
    const formVersion = dto.formVersion ?? 2;
    const b = await this.prisma.candidatureBrouillon.upsert({
      where: { userId },
      create: { userId, formVersion, data },
      update: { formVersion, data },
    });
    return { brouillon: this.vueBrouillon(b) };
  }

  async deleteBrouillon(userId: string): Promise<void> {
    const b = await this.prisma.candidatureBrouillon.findUnique({
      where: { userId },
    });
    if (!b) return;
    await this.prisma.candidatureBrouillon.delete({ where: { userId } });
    await this.voix.supprimer(b.voiceKey);
  }

  /**
   * Dépôt de l'enregistrement vocal (corps brut) dans le brouillon. Il ne
   * rejoint la candidature qu'à l'envoi, avec le consentement daté. Un nouveau
   * dépôt remplace l'ancien, dont le fichier est effacé.
   */
  async deposerVoix(
    userId: string,
    contentType: string | undefined,
    dureeBrute: unknown,
    corps: Buffer,
  ): Promise<{
    voix: {
      mimeType: string;
      durationSec: number;
      uploadedAt: string;
      octets: number;
    };
  }> {
    const ctx = await this.contexte(userId);
    const permis: PhaseCandidature[] = [
      'BROUILLON',
      'ECRIT_A_REVOIR',
      'REFUSE',
    ];
    if (!permis.includes(ctx.suivi.phase)) {
      throw new ConflictException(
        ctx.suivi.phase === 'ACCEPTE'
          ? 'Ta candidature est acceptée : plus d’enregistrement à envoyer.'
          : 'Ta candidature est en cours d’examen : l’enregistrement ne peut plus changer.',
      );
    }
    const { type, dureeSec } = this.voix.controler(
      contentType,
      dureeBrute,
      corps,
    );
    const cle = await this.voix.ecrire(corps, type);
    const now = new Date();
    let avant: string | null;
    try {
      avant = await this.remplacerVoixDuBrouillon(userId, {
        voiceKey: cle,
        voiceMimeType: type,
        voiceDurationSec: dureeSec,
        voiceUploadedAt: now,
      });
    } catch (err) {
      await this.voix.supprimer(cle);
      throw err;
    }
    if (avant && avant !== cle) await this.voix.supprimer(avant);
    return {
      voix: {
        mimeType: type,
        durationSec: dureeSec,
        uploadedAt: now.toISOString(),
        octets: corps.length,
      },
    };
  }

  // ── Envoi ──────────────────────────────────────────────────

  /**
   * Première candidature, ou nouvelle candidature complète après un refus
   * (une fois le délai de 1 jour passé, HRP compris). Un corps v2 est déjà
   * contrôlé champ par champ par le ValidationPipe (SubmitWhitelistDto) ; un
   * autre format est refusé ici, d'un bloc.
   */
  async submit(
    userId: string,
    dto: ChampsCandidature,
  ): Promise<WhitelistApplicationDto> {
    this.refuserAncienFormat(dto);
    const ctx = await this.contexte(userId);
    this.refuserSiDelai(ctx);
    if (ctx.app && ctx.suivi.phase !== 'REFUSE') {
      throw new ConflictException(
        ctx.suivi.phase === 'ACCEPTE'
          ? 'Ta candidature est déjà acceptée.'
          : 'Tu as déjà une candidature en cours. Pour la modifier après une demande de révision, utilise PATCH /whitelist/me.',
      );
    }
    return this.nouvelleCandidature(ctx, dto);
  }

  /**
   * Renvoi après « À revoir » (seules les parties rouvertes sont contrôlées et
   * reprises, les parties validées restent verrouillées, dans leur format
   * d'origine) ou après un refus (nouvelle candidature complète, après le
   * délai). Le corps n'est contrôlé qu'ici (RenvoiWhitelistDto) : ce qui est
   * exigé dépend des parties rouvertes. Toujours au format v2 : un dossier de
   * l'ancien format rouvert se renvoie avec le nouveau launcher.
   */
  async resubmit(
    userId: string,
    dto: ChampsCandidature,
  ): Promise<WhitelistApplicationDto> {
    this.refuserAncienFormat(dto);
    const ctx = await this.contexte(userId);
    const app = ctx.app;
    if (!app) {
      throw new NotFoundException(
        'Aucune candidature à modifier. Utilise POST /whitelist pour la créer.',
      );
    }
    const phase = ctx.suivi.phase;
    if (phase === 'REFUSE') {
      this.refuserSiDelai(ctx);
      // Nouvelle candidature complète : tout le formulaire est contrôlé.
      this.validerEnvoi(dto, CHAMPS_ENVOI);
      return this.nouvelleCandidature(ctx, dto);
    }
    if (phase === 'DOUANE_A_REVOIR') {
      throw new ConflictException(
        'Ton écrit est conservé : réserve une nouvelle séance de douane dans le launcher.',
      );
    }
    if (phase !== 'ECRIT_A_REVOIR') {
      throw new ForbiddenException(
        phase === 'ACCEPTE'
          ? 'Ta candidature est acceptée : elle ne se modifie plus.'
          : 'Ta candidature n’est pas à modifier pour l’instant : attends la réponse du staff.',
      );
    }
    const parties = ctx.suivi.partiesRouvertes;
    const hrp = parties.includes('HRP');
    const rp = parties.includes('RP');
    this.validerEnvoi(dto, [
      ...CHAMPS_ENVOI_COMMUNS,
      ...(hrp ? CHAMPS_ENVOI_HRP : []),
      ...(rp ? CHAMPS_ENVOI_RP : []),
    ]);

    const form = this.normaliser(dto);
    const now = new Date();
    const data: Prisma.WhitelistApplicationUncheckedUpdateInput = {
      // formVersion suit le format de la partie RP (nom de famille libre, voix) :
      // un dossier v1 passe en v2 quand sa partie RP est renvoyée ; ses
      // colonnes HRP v1, validées, restent. HRP seul renvoyé : il reste en v1.
      formVersion: app.formVersion === 2 || rp ? 2 : 1,
      status: AppStatus.PENDING,
      submittedAt: now,
      reviewedAt: null,
      reviewNotes: null,
      // Cases de l'onglet Envoi, cochées (contrôlées par verifierEnvoi).
      rulesAcceptedAt: now,
      voiceConsentAt: now,
    };
    if (hrp) {
      for (const c of COLONNES_HRP)
        (data as Record<string, unknown>)[c] = form[c];
      data.hrpStatus = AppStatus.PENDING;
    }
    if (rp) {
      for (const c of COLONNES_RP)
        (data as Record<string, unknown>)[c] = form[c];
      data.rpStatus = AppStatus.PENDING;
    }

    // La voix suit la partie RP : une nouvelle voix du brouillon remplace
    // l'ancienne ; sans nouvelle voix, l'ancienne reste (si elle existe encore).
    const brouillon = await this.prisma.candidatureBrouillon.findUnique({
      where: { userId },
    });
    let ancienneVoixAEffacer: string | null = null;
    if (
      rp &&
      brouillon?.voiceKey &&
      (await this.voix.existe(brouillon.voiceKey))
    ) {
      Object.assign(data, {
        voiceKey: brouillon.voiceKey,
        voiceMimeType: brouillon.voiceMimeType,
        voiceDurationSec: brouillon.voiceDurationSec,
        voiceUploadedAt: brouillon.voiceUploadedAt,
        voicePurgeAt: null,
        voiceDeletedAt: null,
      });
      if (app.voiceKey && app.voiceKey !== brouillon.voiceKey)
        ancienneVoixAEffacer = app.voiceKey;
    } else if (rp && !(await this.voix.existe(app.voiceKey))) {
      throw new BadRequestException(
        'voix: Ajoute ton enregistrement vocal (onglet Voix) avant de renvoyer ta candidature.',
      );
    }

    // Écriture sous le verrou de la candidature (même ordre que la décision de
    // l'écrit et la douane) : une décision du staff arrivée entre-temps n'est
    // jamais écrasée.
    const updated = await this.prisma
      .$transaction(async (tx) => {
        await verrouillerCandidature(tx, app.id);
        const relu = await this.contexte(userId, tx);
        if (
          relu.app?.id !== app.id ||
          relu.suivi.phase !== 'ECRIT_A_REVOIR' ||
          relu.suivi.partiesRouvertes.join() !== parties.join()
        ) {
          throw new ConflictException(MSG_RELUE_ENTRE_TEMPS);
        }
        return tx.whitelistApplication.update({
          where: { id: app.id },
          data,
        });
      }, OPTIONS_TRANSACTION)
      .catch(traduireConflitTransaction);
    await this.effacerBrouillonApresEnvoi(
      userId,
      brouillon,
      data.voiceKey as string | undefined,
    );
    if (ancienneVoixAEffacer) await this.voix.supprimer(ancienneVoixAEffacer);

    await this.notifyStaff(userId, updated);
    void this.messages.postSystemMessage(
      updated.id,
      `✏️ Candidature renvoyée après révision (partie ${parties.join(' et ')}). Elle est en cours de relecture par l'équipe whitelist.`,
    );
    const suivi = this.suiviApres(ctx.user, updated);
    return this.toDto(updated, suivi.phase, null);
  }

  /**
   * Retire la candidature courante, tant que l'oral n'est pas validé (après,
   * le joueur passe par un ticket). Efface la voix (candidature et brouillon) et
   * le brouillon. `User.whitelistRejectedAt` survit : le délai après un refus
   * court toujours.
   */
  async withdraw(userId: string): Promise<void> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { userId },
    });
    if (!app) {
      throw new NotFoundException('Aucune candidature à retirer.');
    }
    if (app.status === AppStatus.APPROVED) {
      throw new ForbiddenException(
        'Tu as été accepté. Pour quitter le serveur, contacte le staff par un ticket.',
      );
    }
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { minecraftUsername: true },
    });
    const brouillon = await this.prisma.candidatureBrouillon.findUnique({
      where: { userId },
      select: { voiceKey: true },
    });

    // Fichiers d'abord : une voix ne doit pas survivre au retrait.
    await this.voix.supprimer(app.voiceKey);
    await this.voix.supprimer(brouillon?.voiceKey);
    await this.prisma.$transaction([
      this.prisma.candidatureBrouillon.deleteMany({ where: { userId } }),
      // Les réservations de douane partent avec la candidature (onDelete: Cascade).
      this.prisma.whitelistApplication.delete({ where: { userId } }),
    ]);
    this.logger.log(`whitelist withdrawn by user ${userId} (app ${app.id})`);

    if (app.discordThreadId || app.discordMessageId) {
      void this.webhooks
        .statusUpdate({
          kind: 'whitelist',
          threadId: app.discordThreadId,
          messageId: app.discordMessageId,
          status: 'DELETED',
          actorName: user?.minecraftUsername ?? 'le joueur',
        })
        .catch((err) =>
          this.logger.warn(
            `statusUpdate whitelist withdraw echec : ${(err as Error).message}`,
          ),
        );
    }
  }

  // ── Interne ────────────────────────────────────────────────

  /** Compte, candidature, place réservée et phase. `db` : une transaction en cours, pour relire sous verrou. */
  private async contexte(
    userId: string,
    db: ClientPrisma = this.prisma,
  ): Promise<Contexte> {
    const [user, app, reservation] = await Promise.all([
      db.user.findUnique({
        where: { id: userId },
        select: {
          id: true,
          role: true,
          oralPassedAt: true,
          whitelistRejectedAt: true,
          minecraftUsername: true,
        },
      }),
      db.whitelistApplication.findUnique({
        where: { userId },
        include: {
          assignedTo: {
            select: { minecraftUsername: true, discordUsername: true },
          },
        },
      }),
      db.douaneReservation.findFirst({
        where: { userId, statut: 'BOOKED' },
        orderBy: { convocationAt: 'asc' },
        select: {
          id: true,
          seanceId: true,
          convocationAt: true,
          presentAt: true,
        },
      }),
    ]);
    if (!user) throw new NotFoundException('Compte introuvable.');
    const suivi = calculerSuivi({
      role: user.role,
      oralPassedAt: user.oralPassedAt,
      whitelistRejectedAt: user.whitelistRejectedAt,
      app,
      reservation,
      now: new Date(),
      retardMaxMin: this.retardMaxMin,
      // Vue du candidat : une place dont l'entrée est fermée sans arrivée est manquée.
      placeFermeeManquee: true,
      douaneEnJeu: this.douaneEnJeu,
    });
    return { user, app, suivi };
  }

  /**
   * 400 « Mets ton launcher à jour pour envoyer ta candidature. » pour tout
   * envoi sans `formVersion: 2` (complément du 25/10 : plus de voie « ancien
   * format »), avant tout autre contrôle.
   */
  private refuserAncienFormat(dto: ChampsCandidature): void {
    if (!estEnvoiV2(dto)) throw new BadRequestException(MSG_LAUNCHER_A_JOUR);
  }

  /** 400 avec un message par champ (« champ: message »), comme le ValidationPipe global. */
  private validerEnvoi(
    dto: ChampsCandidature,
    champs: readonly string[],
  ): void {
    const erreurs = verifierEnvoi(
      dto as unknown as Record<string, unknown>,
      champs,
    );
    if (erreurs.length > 0) throw new BadRequestException(erreurs);
  }

  /**
   * Range une nouvelle voix dans le brouillon et renvoie la clé qu'elle
   * remplace. La ligne du brouillon est verrouillée (SELECT … FOR UPDATE) :
   * deux dépôts simultanés se suivent, chacun lit la clé laissée par l'autre,
   * et aucun fichier ne perd sa référence sans être effacé.
   */
  private async remplacerVoixDuBrouillon(
    userId: string,
    voix: {
      voiceKey: string;
      voiceMimeType: string;
      voiceDurationSec: number;
      voiceUploadedAt: Date;
    },
  ): Promise<string | null> {
    for (let essai = 0; ; essai++) {
      try {
        return await this.prisma.$transaction(async (tx) => {
          // La ligne doit exister pour être verrouillée.
          await tx.candidatureBrouillon.upsert({
            where: { userId },
            create: { userId },
            update: {},
          });
          const lignes = await tx.$queryRaw<
            Array<{ voiceKey: string | null }>
          >`SELECT "voiceKey" FROM "CandidatureBrouillon" WHERE "userId" = ${userId} FOR UPDATE`;
          await tx.candidatureBrouillon.update({
            where: { userId },
            data: voix,
          });
          return lignes[0]?.voiceKey ?? null;
        }, OPTIONS_TRANSACTION);
      } catch (err) {
        // Deux premiers dépôts simultanés : les créations de la ligne se sont
        // croisées. Le second essai la trouve.
        if (
          essai === 0 &&
          err instanceof Prisma.PrismaClientKnownRequestError &&
          err.code === 'P2002'
        ) {
          continue;
        }
        return traduireConflitTransaction(err);
      }
    }
  }

  /** Phase après une écriture (pas de réservation BOOKED juste après un envoi). */
  private suiviApres(
    user: Contexte['user'],
    app: WhitelistApplication,
  ): SuiviCandidature {
    return calculerSuivi({
      role: user.role,
      oralPassedAt: user.oralPassedAt,
      whitelistRejectedAt: user.whitelistRejectedAt,
      app,
      reservation: null,
      now: new Date(),
      retardMaxMin: this.retardMaxMin,
      douaneEnJeu: this.douaneEnJeu,
    });
  }

  /** 409 tant que le délai de 1 jour après un refus n'est pas écoulé (§2.7). */
  private refuserSiDelai(ctx: Contexte): void {
    const s = ctx.suivi;
    if (s.phase !== 'REFUSE' || !s.canReapplyAt) return;
    const date = new Date(s.canReapplyAt);
    if (date.getTime() <= Date.now()) return;
    throw new ConflictException({
      statusCode: 409,
      error: 'Conflict',
      code: 'DELAI_REFUS',
      message: `Nouvelle candidature possible le ${formatDateParis(date)}.`,
      canReapplyAt: s.canReapplyAt,
    });
  }

  /** Écrit une candidature complète (création, ou remise à neuf de la ligne après un refus). */
  private async nouvelleCandidature(
    ctx: Contexte,
    dto: ChampsCandidature,
  ): Promise<WhitelistApplicationDto> {
    const userId = ctx.user.id;
    const form = this.normaliser(dto);
    const now = new Date();

    // La voix (obligatoire, Q2) vient du brouillon.
    const brouillon = await this.prisma.candidatureBrouillon.findUnique({
      where: { userId },
    });
    if (!brouillon?.voiceKey || !(await this.voix.existe(brouillon.voiceKey))) {
      throw new BadRequestException(
        'voix: Ajoute ton enregistrement vocal (onglet Voix) avant d’envoyer ta candidature.',
      );
    }
    const champs = {
      ...form,
      voiceKey: brouillon.voiceKey,
      voiceMimeType: brouillon.voiceMimeType,
      voiceDurationSec: brouillon.voiceDurationSec,
      voiceUploadedAt: brouillon.voiceUploadedAt,
      // Cases de l'onglet Envoi, cochées (contrôlées par le DTO ou verifierEnvoi).
      rulesAcceptedAt: now,
      voiceConsentAt: now,
    };

    let saved: WhitelistApplication;
    const ancienne = ctx.app;
    if (ancienne) {
      // Remise à neuf : HRP, RP et oral repartent de zéro (Q7). Le fil est gardé.
      // Sous le verrou de la candidature, phase relue : une décision du staff
      // arrivée entre-temps n'est pas écrasée.
      const maj = await this.prisma
        .$transaction(async (tx) => {
          await verrouillerCandidature(tx, ancienne.id);
          const relu = await this.contexte(userId, tx);
          if (relu.app?.id !== ancienne.id || relu.suivi.phase !== 'REFUSE') {
            throw new ConflictException(MSG_RELUE_ENTRE_TEMPS);
          }
          this.refuserSiDelai(relu);
          await tx.douaneReservation.updateMany({
            where: { applicationId: ancienne.id, statut: 'BOOKED' },
            data: {
              statut: 'CANCELLED',
              annuleeAt: now,
              motifAnnulation: 'Nouvelle candidature',
            },
          });
          return tx.whitelistApplication.update({
            where: { id: ancienne.id },
            data: {
              ...champs,
              status: AppStatus.PENDING,
              hrpStatus: AppStatus.PENDING,
              rpStatus: AppStatus.PENDING,
              submittedAt: now,
              reviewedAt: null,
              reviewedBy: null,
              reviewNotes: null,
              writtenApprovedAt: null,
              writtenStaffNotes: null,
              oralStatus: AppStatus.PENDING,
              oralDecidedAt: null,
              oralVia: null,
              oralExaminerUserId: null,
              oralReason: null,
              oralNotes: null,
              voicePurgeAt: null,
              voiceDeletedAt: null,
              assignedToUserId: null,
              assignedAt: null,
            },
          });
        }, OPTIONS_TRANSACTION)
        .catch(traduireConflitTransaction);
      saved = maj;
      if (ancienne.voiceKey && ancienne.voiceKey !== saved.voiceKey) {
        await this.voix.supprimer(ancienne.voiceKey);
      }
    } else {
      try {
        saved = await this.prisma.whitelistApplication.create({
          data: { ...champs, userId, status: AppStatus.PENDING },
        });
      } catch (err) {
        // Double envoi simultané : la ligne existe déjà (userId unique).
        if (
          err instanceof Prisma.PrismaClientKnownRequestError &&
          err.code === 'P2002'
        ) {
          throw new ConflictException('Ta candidature est déjà envoyée.');
        }
        throw err;
      }
    }
    await this.effacerBrouillonApresEnvoi(
      userId,
      brouillon,
      saved.voiceKey ?? undefined,
    );

    await this.notifyStaff(userId, saved);
    void this.messages.postSystemMessage(
      saved.id,
      ancienne
        ? "✅ Nouvelle candidature envoyée. Elle est en cours de relecture par l'équipe whitelist."
        : "✅ Candidature envoyée. Elle est en cours de relecture par l'équipe whitelist.",
    );
    const suivi = this.suiviApres(ctx.user, saved);
    return this.toDto(saved, suivi.phase, null);
  }

  /** Après un envoi, le brouillon serveur est effacé (§2.5) ; sa voix, si elle n'a pas été reprise, aussi. */
  private async effacerBrouillonApresEnvoi(
    userId: string,
    brouillon: { voiceKey: string | null } | null,
    voixReprise: string | undefined,
  ): Promise<void> {
    if (!brouillon) return;
    await this.prisma.candidatureBrouillon.deleteMany({ where: { userId } });
    if (brouillon.voiceKey && brouillon.voiceKey !== voixReprise) {
      await this.voix.supprimer(brouillon.voiceKey);
    }
  }

  /**
   * Colonnes du formulaire v2 : toutes sont renseignées, celles de l'ancien
   * format à null (un renvoi complet ne garde rien de l'ancien format).
   */
  private normaliser(dto: ChampsCandidature): Formulaire {
    const t = (s: string | null | undefined) => (s ?? '').trim();
    const support = t(dto.support);
    const commun = {
      availability: t(dto.availability),
      firstName: t(dto.firstName),
      lastName: t(dto.lastName),
      village: t(dto.village),
      support: support === '' ? null : support,
      history: t(dto.history),
      objectives: t(dto.objectives),
    };
    return {
      formVersion: 2,
      ...commun,
      playerAge: dto.playerAge ?? null,
      dob: null,
      rpYears: (dto.rpYears as RpYears) ?? null,
      narutoKnowledge: (dto.narutoKnowledge as NarutoKnowledge) ?? null,
      discoverySource:
        (dto.discoverySource as DiscoverySource | null | undefined) || null,
      motivation: null,
      experience: null,
      characterType: (dto.characterType as CharacterType) ?? null,
      characterSex: (dto.characterSex as CharacterSex) ?? null,
      characterAge: dto.characterAge ?? null,
      appearance: null,
    };
  }

  private vueBrouillon(b: {
    formVersion: number;
    data: Prisma.JsonValue;
    updatedAt: Date;
    voiceKey: string | null;
    voiceMimeType: string | null;
    voiceDurationSec: number | null;
    voiceUploadedAt: Date | null;
  }): BrouillonVue {
    const data =
      b.data && typeof b.data === 'object' && !Array.isArray(b.data)
        ? (b.data as Record<string, unknown>)
        : {};
    return {
      formVersion: b.formVersion,
      data,
      updatedAt: b.updatedAt.toISOString(),
      voix: b.voiceKey
        ? {
            mimeType: b.voiceMimeType ?? 'audio/webm',
            durationSec: b.voiceDurationSec,
            uploadedAt: b.voiceUploadedAt?.toISOString() ?? null,
          }
        : null,
    };
  }

  private async nomStaff(userId: string): Promise<string | null> {
    const u = await this.prisma.user.findUnique({
      where: { id: userId },
      select: { discordUsername: true, minecraftUsername: true },
    });
    return u ? (u.discordUsername ?? u.minecraftUsername) : null;
  }

  /**
   * Notifie le bot Discord qu'une candidature vient d'être créée / renvoyée.
   * Échec non bloquant : le joueur a déjà reçu sa réponse.
   *
   * Format compatible avec le bot actuel : les champs historiques restent des
   * chaînes (vides en v2) ; les champs v2 s'ajoutent, ignorés par l'ancien bot.
   */
  private async notifyStaff(userId: string, app: WhitelistApplication) {
    const user = await this.prisma.user.findUnique({ where: { id: userId } });
    if (!user) return;
    try {
      const result = await this.webhooks.whitelistSubmitted({
        applicationId: app.id,
        userPseudo: user.minecraftUsername,
        userId: user.id,
        discordUserId: user.discordUserId,
        firstName: app.firstName,
        lastName: app.lastName,
        village: app.village,
        support: app.support,
        dob: app.dob?.toISOString() ?? '',
        motivation: app.motivation ?? '',
        experience: app.experience ?? '',
        availability: app.availability,
        history: app.history,
        appearance: app.appearance ?? '',
        objectives: app.objectives,
        formVersion: app.formVersion,
        playerAge: app.playerAge,
        rpYears: app.rpYears,
        narutoKnowledge: app.narutoKnowledge,
        discoverySource: app.discoverySource,
        characterType: app.characterType,
        characterSex: app.characterSex,
        characterAge: app.characterAge,
        voiceDurationSec: app.voiceKey ? app.voiceDurationSec : null,
        hrpStatus: app.hrpStatus,
        rpStatus: app.rpStatus,
      });
      // Le bot retourne {messageId} (message public + bouton) ; {threadId}
      // reste accepté pour les anciens déploiements.
      const data: { discordMessageId?: string; discordThreadId?: string } = {};
      if (result?.messageId) data.discordMessageId = result.messageId;
      if (result?.threadId) data.discordThreadId = result.threadId;
      if (Object.keys(data).length > 0) {
        await this.prisma.whitelistApplication.update({
          where: { id: app.id },
          data,
        });
        this.logger.log(
          `whitelist app ${app.id} → ${result?.messageId ? `message ${result.messageId}` : `thread ${result?.threadId}`}`,
        );
      }
    } catch (err) {
      this.logger.warn(
        `Webhook whitelist non transmis : ${(err as Error).message}`,
      );
    }
  }

  private toDto(
    app: ApplicationLue,
    phase: PhaseCandidature,
    reviewerName: string | null,
  ): WhitelistApplicationDto {
    const iso = (d: Date | null | undefined) => d?.toISOString() ?? null;
    return {
      id: app.id,
      status: app.status,
      hrpStatus: app.hrpStatus,
      rpStatus: app.rpStatus,
      formVersion: app.formVersion,
      phase,
      playerAge: app.playerAge,
      // dob est stocké en DateTime ; exposé en AAAA-MM-JJ (ancien champ date du launcher).
      dob: app.dob ? app.dob.toISOString().slice(0, 10) : '',
      rpYears: app.rpYears,
      narutoKnowledge: app.narutoKnowledge,
      discoverySource: app.discoverySource,
      availability: app.availability,
      motivation: app.motivation ?? '',
      experience: app.experience ?? '',
      firstName: app.firstName,
      lastName: app.lastName,
      village: app.village,
      characterType: app.characterType,
      characterSex: app.characterSex,
      characterAge: app.characterAge,
      support: app.support,
      history: app.history,
      appearance: app.appearance ?? '',
      objectives: app.objectives,
      voix: app.voiceKey
        ? {
            mimeType: app.voiceMimeType ?? 'audio/webm',
            durationSec: app.voiceDurationSec,
            uploadedAt: iso(app.voiceUploadedAt),
          }
        : null,
      voiceDeletedAt: iso(app.voiceDeletedAt),
      rulesAcceptedAt: iso(app.rulesAcceptedAt),
      voiceConsentAt: iso(app.voiceConsentAt),
      submittedAt: app.submittedAt.toISOString(),
      reviewedAt: iso(app.reviewedAt),
      reviewNotes: app.reviewNotes,
      reviewerName,
      writtenApprovedAt: iso(app.writtenApprovedAt),
      oralStatus: app.oralStatus,
      oralDecidedAt: iso(app.oralDecidedAt),
      oralVia: app.oralVia,
      oralReason: app.oralReason,
      assignee: app.assignedTo
        ? {
            username:
              app.assignedTo.discordUsername ??
              app.assignedTo.minecraftUsername,
          }
        : null,
      assignedAt: iso(app.assignedAt),
    };
  }
}
