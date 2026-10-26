import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import {
  AppStatus,
  CharacterSex,
  CharacterType,
  DiscoverySource,
  MessageAuthor,
  NarutoKnowledge,
  OralVia,
  Prisma,
  Role,
  RpYears,
  TicketStatus,
} from '@prisma/client';
import { AuditService } from '../audit/audit.service';
import { aCapacite, peutTraiterTicket } from '../auth/capacites';
import {
  estActif,
  estExaminateur,
  lireRetardMax,
} from '../douane/douane.regles';
import { PrismaService } from '../prisma/prisma.service';
import {
  calculerSuivi,
  PhaseCandidature,
  SuiviCandidature,
} from '../whitelist/whitelist.rules';
import {
  FiltreWhitelist,
  ListAuditQueryDto,
  ListTicketsQueryDto,
  ListWhitelistQueryDto,
} from './dto/admin.dto';

/**
 * Chiffres de Pilotage. Sans `pilotage.analyses` (Helper, Modélisateur,
 * Développeur, Recruteur), les blocs Comptes et Recrutement ne sortent pas :
 * `users` et le détail des candidatures valent null. Restent les compteurs des
 * blocs Maintenant et Staff, ouverts à `pilotage.voir`.
 */
export interface DashboardStats {
  whitelist: {
    pending: number;
    needsRevision: number | null;
    approved: number | null;
    rejected: number | null;
  };
  tickets: {
    open: number;
    inProgress: number;
    resolved: number;
    closed: number;
  };
  users: {
    total: number;
    /** Comptes whitelistés : tous les grades à partir de WHITELISTED (staff compris). */
    whitelisted: number;
    last24h: number;
  } | null;
}

/**
 * Compte staff montré au panel (prise en charge, relecture, oral) : le nom
 * affiché reste le pseudo Discord s'il existe, mais le visage et le pseudo du
 * jeu viennent du compte Minecraft (mc-heads.net reçoit l'UUID, jamais un
 * pseudo Discord qui donnerait la tête d'un inconnu).
 */
export interface CompteStaff {
  id: string;
  username: string;
  minecraftUsername: string;
  minecraftUuid: string;
}

/** Sélection Prisma qui nourrit `compteStaff`. */
export const SELECT_COMPTE_STAFF = {
  id: true,
  minecraftUsername: true,
  minecraftUuid: true,
  discordUsername: true,
} as const;

export function compteStaff(
  u: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUsername: string | null;
  } | null,
): CompteStaff | null {
  return u
    ? {
        id: u.id,
        username: u.discordUsername ?? u.minecraftUsername,
        minecraftUsername: u.minecraftUsername,
        minecraftUuid: u.minecraftUuid,
      }
    : null;
}

/**
 * Un écrit renvoyé par le candidat après « À revoir » (et non un premier
 * envoi). Deux traces, aucune colonne dédiée :
 *  - le renvoi remet `reviewedAt` à null mais garde `reviewedBy`, alors
 *    qu'une nouvelle candidature après un refus efface les deux ;
 *  - il poste un message système « Candidature renvoyée… » (whitelist.service
 *    › resubmit), postérieur au dernier envoi : il reste vrai quand le staff a
 *    déjà tranché une des deux parties renvoyées.
 */
export const PREFIXE_MESSAGE_RENVOI = '✏️ Candidature renvoyée';

export function estRenvoi(row: {
  reviewedAt: Date | null;
  reviewedBy: string | null;
  submittedAt: Date;
  messages?: Array<{ createdAt: Date }>;
}): boolean {
  if (row.reviewedAt === null && row.reviewedBy !== null) return true;
  const trace = row.messages?.[0];
  return !!trace && trace.createdAt.getTime() >= row.submittedAt.getTime();
}

/**
 * Comptes whitelistés = tout grade sauf PLAYER (STATISTIQUES lot 0). L'ancien
 * filtre listait WHITELISTED, HELPER et MODERATOR : il oubliait Modélisateur,
 * Développeur, Recruteur, Administrateur et Propriétaire. Écrit en exclusion
 * pour qu'un grade ajouté plus tard compte d'office.
 */
export const WHERE_WHITELISTES: Prisma.UserWhereInput = {
  role: { not: Role.PLAYER },
};

/**
 * Filtre de catégorie des listes de tickets : aucun avec `tickets.traiter`,
 * « Bug » seulement sinon (développeur, `tickets.bug`).
 */
export function filtreCategorieTickets(role: Role): Prisma.TicketWhereInput {
  return aCapacite(role, 'tickets.traiter') ? {} : { category: 'BUG' };
}

export interface WhitelistListItem {
  id: string;
  status: AppStatus;
  /** Parties de l'écrit et oral (colonnes Écrit HRP / Écrit RP / Oral du panel). */
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  oralStatus: AppStatus;
  /** Phase calculée (CANDIDATURE_ET_DOUANE.md §1.2). */
  phase: PhaseCandidature;
  /** false : oral déjà réussi sur ce compte (première whitelist seulement), l'écrit validé suffit. */
  oralRequis: boolean;
  /** Place de douane réservée (BOOKED), pour la colonne Oral. */
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: string;
  } | null;
  formVersion: number;
  submittedAt: string;
  reviewedAt: string | null;
  /** Écrit renvoyé après « À revoir » (voir `estRenvoi`), pas un premier envoi. */
  renvoi: boolean;
  writtenApprovedAt: string | null;
  firstName: string;
  lastName: string;
  village: string;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUsername: string | null;
  };
  // Assignation staff (cf C3 — flow DM bot). null si personne ne s'en
  // occupe.
  assignee: CompteStaff | null;
  assignedAt: string | null;
}

export interface WhitelistDetail {
  id: string;
  status: AppStatus;
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  /** 1 = ancien formulaire, 2 = candidature v2. */
  formVersion: number;
  phase: PhaseCandidature;
  /** Suivi complet, tel que le launcher le reçoit (délai, parties rouvertes, place réservée). */
  suivi: SuiviCandidature;
  // Partie HRP — null quand le champ n'existe pas dans la version du formulaire.
  playerAge: number | null;
  /** v1 seulement, AAAA-MM-JJ. */
  dob: string | null;
  rpYears: RpYears | null;
  narutoKnowledge: NarutoKnowledge | null;
  discoverySource: DiscoverySource | null;
  availability: string;
  /** v1 seulement. */
  motivation: string | null;
  /** v1 seulement. */
  experience: string | null;
  // Partie RP
  firstName: string;
  lastName: string;
  village: string;
  characterType: CharacterType | null;
  characterSex: CharacterSex | null;
  characterAge: number | null;
  support: string | null;
  history: string;
  /** v1 seulement. */
  appearance: string | null;
  objectives: string;
  /** Voix jointe : à écouter par GET /v1/admin/whitelist/:id/voix. null = aucune, ou effacée (voiceDeletedAt). */
  voix: {
    mimeType: string;
    durationSec: number | null;
    uploadedAt: string | null;
    /** Effacement programmé (décision finale + 30 jours). */
    purgeAt: string | null;
  } | null;
  voiceDeletedAt: string | null;
  rulesAcceptedAt: string | null;
  voiceConsentAt: string | null;
  // Écrit
  submittedAt: string;
  reviewedAt: string | null;
  /** Commentaire de l'écrit, VISIBLE du joueur. */
  reviewNotes: string | null;
  reviewedBy: CompteStaff | null;
  writtenApprovedAt: string | null;
  /** Note PRIVÉE sur l'écrit (staff et examinateur seulement). */
  writtenStaffNotes: string | null;
  // Oral
  oral: {
    status: AppStatus;
    decidedAt: string | null;
    via: OralVia | null;
    examiner: CompteStaff | null;
    /** Motif VISIBLE du joueur. */
    reason: string | null;
    /** Notes PRIVÉES de l'examinateur. */
    notes: string | null;
  };
  discordThreadId: string | null;
  assignee: CompteStaff | null;
  assignedAt: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUserId: string | null;
    discordUsername: string | null;
    role: string;
    /** Oral déjà réussi sur ce compte : plus d'oral, même après une nouvelle candidature. */
    oralPassedAt: string | null;
    /** Dernier refus (écrit ou oral) : nouvelle candidature 1 jour plus tard. */
    whitelistRejectedAt: string | null;
  };
  messages: Array<{
    id: string;
    authorType: MessageAuthor;
    authorId: string | null;
    authorName: string | null;
    content: string;
    createdAt: string;
  }>;
}

export interface TicketListItem {
  id: string;
  subject: string;
  category: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  lastMessagePreview: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    discordUsername: string | null;
  };
  assignee: CompteStaff | null;
  assignedAt: string | null;
}

export interface TicketDetail {
  id: string;
  subject: string;
  category: string;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  discordThreadId: string | null;
  assignee: CompteStaff | null;
  assignedAt: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUserId: string | null;
    discordUsername: string | null;
    role: string;
  };
  messages: Array<{
    id: string;
    authorType: MessageAuthor;
    authorId: string | null;
    authorName: string | null;
    content: string;
    createdAt: string;
  }>;
}

export interface AuditLogItem {
  id: string;
  action: string;
  actor: { id: string; username: string };
  targetUser: { id: string; username: string } | null;
  targetEntity: string | null;
  metadata: unknown;
  source: string;
  ipAddress: string | null;
  createdAt: string;
}

export interface PlayerListItem {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: string;
  banned: boolean;
  lastLoginAt: string | null;
}

export interface PlayerDetail {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUserId: string | null;
  discordUsername: string | null;
  steamUsername: string | null;
  role: string;
  banned: boolean;
  banReason: string | null;
  bannedUntil: string | null;
  createdAt: string;
  lastLoginAt: string | null;
  /**
   * Toujours null : l'IP ne sort plus avec la fiche (elle était visible dès
   * Helper). Elle se lit par `GET /admin/players/:id/ip` (capacité
   * `joueurs.ip`, chaque lecture journalisée). Champ gardé pour le panel actuel.
   */
  lastKnownIp: null;
  /** Une IP est connue pour ce compte (le panel propose alors « Afficher »). */
  ipConnue: boolean;
  /**
   * Toujours null, comme `lastKnownIp` : le pays de connexion relève aussi de
   * `joueurs.ip` (capacites.ts) et ne sort que par `GET /admin/players/:id/ip`,
   * lecture journalisée. Il partait avec la fiche, lisible dès Helper.
   */
  lastKnownCountry: null;
  whitelist: {
    id: string;
    status: AppStatus;
    firstName: string;
    lastName: string;
    village: string;
    submittedAt: string;
    reviewedAt: string | null;
  } | null;
  tickets: Array<{
    id: string;
    subject: string;
    category: string;
    status: TicketStatus;
    createdAt: string;
    updatedAt: string;
    lastMessagePreview: string | null;
  }>;
}

const PLAYER_SELECT = {
  id: true,
  minecraftUsername: true,
  minecraftUuid: true,
  discordUsername: true,
  role: true,
  banned: true,
  lastLoginAt: true,
} as const;

function toPlayerListItem(row: {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: string;
  banned: boolean;
  lastLoginAt: Date | null;
}): PlayerListItem {
  return {
    id: row.id,
    minecraftUsername: row.minecraftUsername,
    minecraftUuid: row.minecraftUuid,
    discordUsername: row.discordUsername,
    role: row.role,
    banned: row.banned,
    lastLoginAt: row.lastLoginAt?.toISOString() ?? null,
  };
}

/** Relations lues pour une ligne de la liste whitelist (phase comprise). */
const INCLUDE_LIGNE_WHITELIST = {
  user: {
    select: {
      id: true,
      minecraftUsername: true,
      minecraftUuid: true,
      discordUsername: true,
      role: true,
      oralPassedAt: true,
      whitelistRejectedAt: true,
    },
  },
  assignedTo: { select: SELECT_COMPTE_STAFF },
  douaneReservations: {
    where: { statut: 'BOOKED' },
    orderBy: { convocationAt: 'asc' },
    take: 1,
    select: { id: true, seanceId: true, convocationAt: true },
  },
  // Dernière trace de renvoi (voir `estRenvoi`).
  messages: {
    where: {
      authorType: MessageAuthor.SYSTEM,
      content: { startsWith: PREFIXE_MESSAGE_RENVOI },
    },
    orderBy: { createdAt: 'desc' },
    take: 1,
    select: { createdAt: true },
  },
} satisfies Prisma.WhitelistApplicationInclude;

type LigneWhitelist = Prisma.WhitelistApplicationGetPayload<{
  include: typeof INCLUDE_LIGNE_WHITELIST;
}>;

function ligneWhitelist(row: LigneWhitelist): WhitelistListItem {
  const place = row.douaneReservations[0] ?? null;
  const suivi = calculerSuivi({
    role: row.user.role,
    oralPassedAt: row.user.oralPassedAt,
    whitelistRejectedAt: row.user.whitelistRejectedAt,
    app: row,
    reservation: place,
    now: new Date(),
    retardMaxMin: lireRetardMax(process.env.DOUANE_RETARD_MAX_MIN),
  });
  return {
    id: row.id,
    status: row.status,
    hrpStatus: row.hrpStatus,
    rpStatus: row.rpStatus,
    oralStatus: row.oralStatus,
    phase: suivi.phase,
    oralRequis: suivi.oralRequis,
    reservation: place
      ? {
          id: place.id,
          seanceId: place.seanceId,
          convocationAt: place.convocationAt.toISOString(),
        }
      : null,
    formVersion: row.formVersion,
    submittedAt: row.submittedAt.toISOString(),
    reviewedAt: row.reviewedAt?.toISOString() ?? null,
    renvoi: estRenvoi(row),
    writtenApprovedAt: row.writtenApprovedAt?.toISOString() ?? null,
    firstName: row.firstName,
    lastName: row.lastName,
    village: row.village,
    user: {
      id: row.user.id,
      minecraftUsername: row.user.minecraftUsername,
      minecraftUuid: row.user.minecraftUuid,
      discordUsername: row.user.discordUsername,
    },
    assignee: compteStaff(row.assignedTo),
    assignedAt: row.assignedAt?.toISOString() ?? null,
  };
}

/** Relations lues pour une ligne de ticket (liste, « Mes prises »). */
const INCLUDE_LIGNE_TICKET = {
  user: {
    select: {
      id: true,
      minecraftUsername: true,
      discordUsername: true,
    },
  },
  assignedTo: { select: SELECT_COMPTE_STAFF },
  messages: { orderBy: { createdAt: 'desc' }, take: 1 },
} satisfies Prisma.TicketInclude;

type LigneTicket = Prisma.TicketGetPayload<{
  include: typeof INCLUDE_LIGNE_TICKET;
}>;

function ligneTicket(row: LigneTicket): TicketListItem {
  return {
    id: row.id,
    subject: row.subject,
    category: row.category,
    status: row.status,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
    lastMessagePreview: row.messages[0]?.content.slice(0, 140) ?? null,
    user: row.user,
    assignee: compteStaff(row.assignedTo),
    assignedAt: row.assignedAt?.toISOString() ?? null,
  };
}

/** Filtres de statut de la liste Joueurs (mêmes valeurs que le panel). */
export type StatutJoueur = 'whiteliste' | 'non' | 'banni';

/** Filtres grade et statut de la recherche de joueurs, appliqués en base. */
export function whereFiltresJoueurs(filtres: {
  role?: Role;
  statut?: StatutJoueur;
}): Prisma.UserWhereInput[] {
  const w: Prisma.UserWhereInput[] = [];
  if (filtres.role) w.push({ role: filtres.role });
  if (filtres.statut === 'banni') w.push({ banned: true });
  if (filtres.statut === 'whiteliste') {
    w.push({ banned: false }, WHERE_WHITELISTES);
  }
  if (filtres.statut === 'non') w.push({ banned: false, role: Role.PLAYER });
  return w;
}

/** Connexion suspecte telle que le panel la reçoit : l'IP n'y est plus. */
export interface AnomalieListItem {
  id: string;
  userId: string;
  /** Une IP est enregistrée : « Afficher » la lit par une route journalisée. */
  ipConnue: boolean;
  country: string | null;
  reason: string;
  acknowledged: boolean;
  notifiedVia: string[];
  createdAt: string;
}

/** Nombre de candidatures par onglet « à faire » (pastilles de la liste, maquette P1). */
export type CompteursWhitelist = Record<FiltreWhitelist, number>;

export interface WhitelistListe {
  total: number;
  items: WhitelistListItem[];
  compteurs: CompteursWhitelist;
}

/**
 * Filtres « à faire », traduits en requête à partir des règles de
 * `calculerSuivi` (whitelist.rules.ts) : la phase n'est pas stockée, mais
 * chaque onglet ne retient que les dossiers dont la phase calculée est la
 * bonne. Toute évolution de `calculerSuivi` doit se reporter ici.
 */
const PAS_DE_REFUS: Prisma.WhitelistApplicationWhereInput = {
  hrpStatus: { not: AppStatus.REJECTED },
  rpStatus: { not: AppStatus.REJECTED },
  oralStatus: { not: AppStatus.REJECTED },
};
const ECRIT_PAS_VALIDE: Prisma.WhitelistApplicationWhereInput = {
  NOT: { hrpStatus: AppStatus.APPROVED, rpStatus: AppStatus.APPROVED },
};

export const WHERE_FILTRE_WHITELIST: Record<
  FiltreWhitelist,
  Prisma.WhitelistApplicationWhereInput
> = {
  // ECRIT_EN_ATTENTE : rien de refusé ni « à revoir », écrit pas encore validé.
  ECRIT: {
    AND: [
      PAS_DE_REFUS,
      ECRIT_PAS_VALIDE,
      {
        status: AppStatus.PENDING,
        hrpStatus: { not: AppStatus.NEEDS_REVISION },
        rpStatus: { not: AppStatus.NEEDS_REVISION },
        user: { role: Role.PLAYER },
      },
    ],
  },
  // DOUANE_A_RESERVER, DOUANE_RESERVEE, DOUANE_A_REVOIR : écrit validé, oral
  // ni réussi ni refusé, aucun oral déjà passé sur le compte.
  DOUANE: {
    hrpStatus: AppStatus.APPROVED,
    rpStatus: AppStatus.APPROVED,
    oralStatus: { in: [AppStatus.PENDING, AppStatus.NEEDS_REVISION] },
    status: { in: [AppStatus.PENDING, AppStatus.NEEDS_REVISION] },
    user: { role: Role.PLAYER, oralPassedAt: null },
  },
  // ECRIT_A_REVOIR : au moins une partie (ou le statut global) « à revoir », écrit pas validé.
  REVISION: {
    AND: [
      PAS_DE_REFUS,
      ECRIT_PAS_VALIDE,
      {
        status: { in: [AppStatus.PENDING, AppStatus.NEEDS_REVISION] },
        user: { role: Role.PLAYER },
        OR: [
          { status: AppStatus.NEEDS_REVISION },
          { hrpStatus: AppStatus.NEEDS_REVISION },
          { rpStatus: AppStatus.NEEDS_REVISION },
        ],
      },
    ],
  },
};

/** Tri de chaque onglet : les files d'attente partent du plus ancien. */
const TRI_FILTRE_WHITELIST: Record<
  FiltreWhitelist,
  Prisma.WhitelistApplicationOrderByWithRelationInput[]
> = {
  ECRIT: [{ submittedAt: 'asc' }],
  DOUANE: [{ writtenApprovedAt: 'asc' }, { submittedAt: 'asc' }],
  REVISION: [{ reviewedAt: 'desc' }],
};

/** Compte staff proposé comme examinateur d'une séance de douane (rôle WHITELIST_REVIEWER ou plus). */
export interface ExaminateurListItem {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: Role;
}

@Injectable()
export class AdminService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async listAudit(
    query: ListAuditQueryDto,
  ): Promise<{ total: number; items: AuditLogItem[] }> {
    const where: Record<string, unknown> = {};
    if (query.actor) where.actorId = query.actor;
    if (query.action) where.action = { contains: query.action };
    if (query.source) where.source = query.source;
    if (query.targetUserId) where.targetUserId = query.targetUserId;
    const [total, rows] = await Promise.all([
      this.prisma.auditLog.count({ where }),
      this.prisma.auditLog.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        take: query.take ?? 50,
        skip: query.skip ?? 0,
        include: {
          actor: {
            select: {
              id: true,
              minecraftUsername: true,
              discordUsername: true,
            },
          },
          targetUser: {
            select: {
              id: true,
              minecraftUsername: true,
              discordUsername: true,
            },
          },
        },
      }),
    ]);
    return {
      total,
      items: rows.map((r) => ({
        id: r.id,
        action: r.action,
        actor: {
          id: r.actor.id,
          username: r.actor.discordUsername ?? r.actor.minecraftUsername ?? '—',
        },
        targetUser: r.targetUser
          ? {
              id: r.targetUser.id,
              username:
                r.targetUser.discordUsername ??
                r.targetUser.minecraftUsername ??
                '—',
            }
          : null,
        targetEntity: r.targetEntity,
        metadata: r.metadata,
        source: r.source,
        ipAddress: r.ipAddress,
        createdAt: r.createdAt.toISOString(),
      })),
    };
  }

  /**
   * Chiffres de Pilotage, selon le grade (STATISTIQUES Q6) : les blocs
   * Comptes et Recrutement exigent `pilotage.analyses`. Sans elle, ces
   * chiffres ne sont même pas comptés (null), pas seulement cachés au panel.
   */
  async dashboard(role: Role): Promise<DashboardStats> {
    const analyses = aCapacite(role, 'pilotage.analyses');
    const since24h = new Date(Date.now() - 24 * 3600 * 1000);
    const compter = (where: Prisma.WhitelistApplicationWhereInput) =>
      this.prisma.whitelistApplication.count({ where });
    const [wlPending, tkOpen, tkProgress, tkResolved, tkClosed] =
      await Promise.all([
        compter({ status: 'PENDING' }),
        this.prisma.ticket.count({ where: { status: 'OPEN' } }),
        this.prisma.ticket.count({ where: { status: 'IN_PROGRESS' } }),
        this.prisma.ticket.count({ where: { status: 'RESOLVED' } }),
        this.prisma.ticket.count({ where: { status: 'CLOSED' } }),
      ]);
    const tickets = {
      open: tkOpen,
      inProgress: tkProgress,
      resolved: tkResolved,
      closed: tkClosed,
    };
    if (!analyses) {
      return {
        whitelist: {
          pending: wlPending,
          needsRevision: null,
          approved: null,
          rejected: null,
        },
        tickets,
        users: null,
      };
    }
    const [
      wlRevision,
      wlApproved,
      wlRejected,
      usersTotal,
      usersWhitelisted,
      users24h,
    ] = await Promise.all([
      compter({ status: 'NEEDS_REVISION' }),
      compter({ status: 'APPROVED' }),
      compter({ status: 'REJECTED' }),
      this.prisma.user.count(),
      this.prisma.user.count({ where: WHERE_WHITELISTES }),
      this.prisma.user.count({ where: { createdAt: { gte: since24h } } }),
    ]);
    return {
      whitelist: {
        pending: wlPending,
        needsRevision: wlRevision,
        approved: wlApproved,
        rejected: wlRejected,
      },
      tickets,
      users: {
        total: usersTotal,
        whitelisted: usersWhitelisted,
        last24h: users24h,
      },
    };
  }

  async listWhitelist(query: ListWhitelistQueryDto): Promise<WhitelistListe> {
    const conditions: Prisma.WhitelistApplicationWhereInput[] = [];
    if (query.status) conditions.push({ status: query.status });
    if (query.filtre) conditions.push(WHERE_FILTRE_WHITELIST[query.filtre]);
    const terme = query.q?.trim();
    if (terme) {
      const contient = { contains: terme, mode: 'insensitive' as const };
      conditions.push({
        OR: [
          { firstName: contient },
          { lastName: contient },
          { user: { minecraftUsername: contient } },
          { user: { discordUsername: contient } },
        ],
      });
    }
    const where: Prisma.WhitelistApplicationWhereInput =
      conditions.length > 0 ? { AND: conditions } : {};
    const orderBy: Prisma.WhitelistApplicationOrderByWithRelationInput[] =
      query.filtre
        ? TRI_FILTRE_WHITELIST[query.filtre]
        : [{ status: 'asc' }, { submittedAt: 'desc' }];

    const [total, rows, ecrit, douane, revision] = await Promise.all([
      this.prisma.whitelistApplication.count({ where }),
      this.prisma.whitelistApplication.findMany({
        where,
        orderBy,
        take: query.take ?? 50,
        skip: query.skip ?? 0,
        include: INCLUDE_LIGNE_WHITELIST,
      }),
      this.prisma.whitelistApplication.count({
        where: WHERE_FILTRE_WHITELIST.ECRIT,
      }),
      this.prisma.whitelistApplication.count({
        where: WHERE_FILTRE_WHITELIST.DOUANE,
      }),
      this.prisma.whitelistApplication.count({
        where: WHERE_FILTRE_WHITELIST.REVISION,
      }),
    ]);
    return {
      total,
      items: rows.map((row) => ligneWhitelist(row)),
      compteurs: { ECRIT: ecrit, DOUANE: douane, REVISION: revision },
    };
  }

  /**
   * Note PRIVÉE du staff sur l'écrit (writtenStaffNotes), enregistrée sans
   * trancher : les points à creuser, lus par l'examinateur à l'oral. Le
   * commentaire visible du joueur (reviewNotes) n'est pas touché. Tracée à
   * l'audit sans son contenu.
   */
  async majNotePriveeEcrit(
    applicationId: string,
    note: string,
    staffUserId: string,
  ): Promise<{ id: string; writtenStaffNotes: string | null }> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id: applicationId },
      select: { id: true, userId: true, writtenStaffNotes: true },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    const valeur = note.trim() || null;
    const maj = await this.prisma.whitelistApplication.update({
      where: { id: applicationId },
      data: { writtenStaffNotes: valeur },
      select: { id: true, writtenStaffNotes: true },
    });
    void this.audit.log({
      actorId: staffUserId,
      action: 'whitelist.note_privee',
      targetUserId: app.userId,
      targetEntity: `whitelist:${applicationId}`,
      metadata: {
        avant: app.writtenStaffNotes !== null,
        apres: valeur !== null,
        longueur: valeur?.length ?? 0,
      },
      source: 'panel',
    });
    return maj;
  }

  /**
   * Comptes qui peuvent examiner une séance de douane : rôle
   * WHITELIST_REVIEWER ou plus (même règle que la création de séance et la
   * décision d'oral, douane.regles.ts › estExaminateur), non bannis.
   */
  async listerExaminateurs(): Promise<ExaminateurListItem[]> {
    const staff = await this.prisma.user.findMany({
      where: {
        banned: false,
        role: { notIn: [Role.PLAYER, Role.WHITELISTED] },
      },
      orderBy: { minecraftUsername: 'asc' },
      select: {
        id: true,
        minecraftUsername: true,
        minecraftUuid: true,
        discordUsername: true,
        role: true,
      },
    });
    return staff.filter((u) => estExaminateur(u.role));
  }

  async getWhitelist(id: string): Promise<WhitelistDetail> {
    const app = await this.prisma.whitelistApplication.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            minecraftUsername: true,
            minecraftUuid: true,
            discordUserId: true,
            discordUsername: true,
            role: true,
            oralPassedAt: true,
            whitelistRejectedAt: true,
          },
        },
        assignedTo: { select: SELECT_COMPTE_STAFF },
        oralExaminer: { select: SELECT_COMPTE_STAFF },
        douaneReservations: {
          where: { statut: 'BOOKED' },
          orderBy: { convocationAt: 'asc' },
          take: 1,
          select: { id: true, seanceId: true, convocationAt: true },
        },
        messages: { orderBy: { createdAt: 'asc' } },
      },
    });
    if (!app) throw new NotFoundException('Candidature introuvable.');
    const reviewer = app.reviewedBy
      ? await this.prisma.user.findUnique({
          where: { id: app.reviewedBy },
          select: SELECT_COMPTE_STAFF,
        })
      : null;
    const suivi = calculerSuivi({
      role: app.user.role,
      oralPassedAt: app.user.oralPassedAt,
      whitelistRejectedAt: app.user.whitelistRejectedAt,
      app,
      reservation: app.douaneReservations[0] ?? null,
      now: new Date(),
      retardMaxMin: lireRetardMax(process.env.DOUANE_RETARD_MAX_MIN),
      douaneEnJeu: estActif(process.env.REBORN_DOUANE_ENABLED),
    });
    const iso = (d: Date | null) => d?.toISOString() ?? null;
    const nom = compteStaff;
    return {
      id: app.id,
      status: app.status,
      hrpStatus: app.hrpStatus,
      rpStatus: app.rpStatus,
      formVersion: app.formVersion,
      phase: suivi.phase,
      suivi,
      playerAge: app.playerAge,
      dob: app.dob ? app.dob.toISOString().slice(0, 10) : null,
      rpYears: app.rpYears,
      narutoKnowledge: app.narutoKnowledge,
      discoverySource: app.discoverySource,
      availability: app.availability,
      motivation: app.motivation,
      experience: app.experience,
      firstName: app.firstName,
      lastName: app.lastName,
      village: app.village,
      characterType: app.characterType,
      characterSex: app.characterSex,
      characterAge: app.characterAge,
      support: app.support,
      history: app.history,
      appearance: app.appearance,
      objectives: app.objectives,
      voix: app.voiceKey
        ? {
            mimeType: app.voiceMimeType ?? 'audio/webm',
            durationSec: app.voiceDurationSec,
            uploadedAt: iso(app.voiceUploadedAt),
            purgeAt: iso(app.voicePurgeAt),
          }
        : null,
      voiceDeletedAt: iso(app.voiceDeletedAt),
      rulesAcceptedAt: iso(app.rulesAcceptedAt),
      voiceConsentAt: iso(app.voiceConsentAt),
      submittedAt: app.submittedAt.toISOString(),
      reviewedAt: iso(app.reviewedAt),
      reviewNotes: app.reviewNotes,
      reviewedBy: nom(reviewer),
      writtenApprovedAt: iso(app.writtenApprovedAt),
      writtenStaffNotes: app.writtenStaffNotes,
      oral: {
        status: app.oralStatus,
        decidedAt: iso(app.oralDecidedAt),
        via: app.oralVia,
        examiner: nom(app.oralExaminer),
        reason: app.oralReason,
        notes: app.oralNotes,
      },
      discordThreadId: app.discordThreadId,
      assignee: nom(app.assignedTo),
      assignedAt: iso(app.assignedAt),
      user: {
        id: app.user.id,
        minecraftUsername: app.user.minecraftUsername,
        minecraftUuid: app.user.minecraftUuid,
        discordUserId: app.user.discordUserId,
        discordUsername: app.user.discordUsername,
        role: app.user.role,
        oralPassedAt: iso(app.user.oralPassedAt),
        whitelistRejectedAt: iso(app.user.whitelistRejectedAt),
      },
      messages: app.messages.map((m) => ({
        id: m.id,
        authorType: m.authorType,
        authorId: m.authorId,
        authorName: m.authorName,
        content: m.content,
        createdAt: m.createdAt.toISOString(),
      })),
    };
  }

  async listTickets(
    query: ListTicketsQueryDto,
    role: Role,
  ): Promise<{ total: number; items: TicketListItem[] }> {
    const where: Prisma.TicketWhereInput = {
      ...(query.status ? { status: query.status } : {}),
      ...filtreCategorieTickets(role),
    };
    const [total, rows] = await Promise.all([
      this.prisma.ticket.count({ where }),
      this.prisma.ticket.findMany({
        where,
        orderBy: [{ status: 'asc' }, { updatedAt: 'desc' }],
        take: query.take ?? 50,
        skip: query.skip ?? 0,
        include: INCLUDE_LIGNE_TICKET,
      }),
    ]);
    return { total, items: rows.map(ligneTicket) };
  }

  /**
   * Recherche de joueurs. Les filtres Grade et Statut de la liste s'appliquent
   * en base, avant la limite : « Bannis » compte tous les bannis, pas ceux des
   * 100 derniers connectés.
   */
  async searchPlayers(
    q: string,
    take = 30,
    filtres: { role?: Role; statut?: StatutJoueur } = {},
  ): Promise<PlayerListItem[]> {
    const term = q.trim();
    const conditions = whereFiltresJoueurs(filtres);
    if (term.length === 0) {
      // Sans query : retourne les derniers connectes pour donner au staff
      // une vue de demarrage utile (plutot qu'une liste vide).
      const rows = await this.prisma.user.findMany({
        where: conditions.length > 0 ? { AND: conditions } : undefined,
        orderBy: { lastLoginAt: { sort: 'desc', nulls: 'last' } },
        take,
        select: PLAYER_SELECT,
      });
      return rows.map(toPlayerListItem);
    }
    const rows = await this.prisma.user.findMany({
      where: {
        AND: [
          ...conditions,
          {
            OR: [
              { minecraftUsername: { contains: term, mode: 'insensitive' } },
              { discordUsername: { contains: term, mode: 'insensitive' } },
              { minecraftUuid: { contains: term, mode: 'insensitive' } },
              { id: { equals: term } },
            ],
          },
        ],
      },
      orderBy: { minecraftUsername: 'asc' },
      take,
      select: PLAYER_SELECT,
    });
    return rows.map(toPlayerListItem);
  }

  async getPlayer(id: string): Promise<PlayerDetail> {
    const user = await this.prisma.user.findUnique({
      where: { id },
      include: {
        whitelistApp: true,
        tickets: {
          orderBy: { updatedAt: 'desc' },
          include: { messages: { orderBy: { createdAt: 'desc' }, take: 1 } },
        },
      },
    });
    if (!user) throw new NotFoundException('Joueur introuvable.');
    return {
      id: user.id,
      minecraftUsername: user.minecraftUsername,
      minecraftUuid: user.minecraftUuid,
      discordUserId: user.discordUserId,
      discordUsername: user.discordUsername,
      steamUsername: user.steamUsername,
      role: user.role,
      banned: user.banned,
      banReason: user.banReason,
      bannedUntil: user.bannedUntil?.toISOString() ?? null,
      createdAt: user.createdAt.toISOString(),
      lastLoginAt: user.lastLoginAt?.toISOString() ?? null,
      lastKnownIp: null,
      ipConnue: !!user.lastKnownIp,
      // Le pays suit l'IP : lecture à la demande, journalisée (revelerIp).
      lastKnownCountry: null,
      whitelist: user.whitelistApp
        ? {
            id: user.whitelistApp.id,
            status: user.whitelistApp.status,
            firstName: user.whitelistApp.firstName,
            lastName: user.whitelistApp.lastName,
            village: user.whitelistApp.village,
            submittedAt: user.whitelistApp.submittedAt.toISOString(),
            reviewedAt: user.whitelistApp.reviewedAt?.toISOString() ?? null,
          }
        : null,
      tickets: user.tickets.map((t) => ({
        id: t.id,
        subject: t.subject,
        category: t.category,
        status: t.status,
        createdAt: t.createdAt.toISOString(),
        updatedAt: t.updatedAt.toISOString(),
        lastMessagePreview: t.messages[0]?.content.slice(0, 140) ?? null,
      })),
    };
  }

  /**
   * Renvoie la liste de tout ce qui est assigne au staff donne :
   * candidatures whitelist (non terminees) + tickets ouverts (non
   * CLOSED). Sert a la page "Mes prises en charge" du panel.
   */
  async myInbox(
    staffUserId: string,
    role: Role,
  ): Promise<{
    whitelist: WhitelistListItem[];
    tickets: TicketListItem[];
  }> {
    const [whitelistRows, ticketRows] = await Promise.all([
      this.prisma.whitelistApplication.findMany({
        where: {
          assignedToUserId: staffUserId,
          status: { in: ['PENDING', 'NEEDS_REVISION'] },
        },
        orderBy: { assignedAt: 'desc' },
        include: INCLUDE_LIGNE_WHITELIST,
      }),
      this.prisma.ticket.findMany({
        where: {
          assignedToUserId: staffUserId,
          status: { in: ['OPEN', 'IN_PROGRESS'] },
          ...filtreCategorieTickets(role),
        },
        orderBy: { assignedAt: 'desc' },
        include: INCLUDE_LIGNE_TICKET,
      }),
    ]);
    return {
      whitelist: whitelistRows.map((row) => ligneWhitelist(row)),
      tickets: ticketRows.map(ligneTicket),
    };
  }

  /**
   * Refuse un ticket que le grade ne traite pas : sans `tickets.traiter`, seul
   * un ticket « Bug » passe (développeur). 404 si le ticket n'existe pas.
   */
  async verifierAccesTicket(id: string, role: Role): Promise<void> {
    if (aCapacite(role, 'tickets.traiter')) return;
    const ticket = await this.prisma.ticket.findUnique({
      where: { id },
      select: { category: true },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    if (!peutTraiterTicket(role, ticket.category)) {
      throw new ForbiddenException(
        'Ton grade ne traite que les tickets « Bug ».',
      );
    }
  }

  /**
   * IP et pays de dernière connexion d'un joueur, à la demande. La lecture est
   * journalisée AVANT de répondre (`player.ip.view`, cible = le joueur) : qui a
   * vu l'IP de qui se retrouve dans le Journal.
   */
  async revelerIp(
    id: string,
    actorId: string,
  ): Promise<{ ip: string | null; pays: string | null }> {
    const user = await this.prisma.user.findUnique({
      where: { id },
      select: { id: true, lastKnownIp: true, lastKnownCountry: true },
    });
    if (!user) throw new NotFoundException('Joueur introuvable.');
    await this.audit.log({
      actorId,
      action: 'player.ip.view',
      targetUserId: user.id,
      targetEntity: `user:${user.id}`,
      metadata: { connue: !!user.lastKnownIp },
      source: 'panel',
    });
    return { ip: user.lastKnownIp, pays: user.lastKnownCountry };
  }

  /**
   * Connexions suspectes, sans leur IP (`ipConnue` seulement) : l'adresse ne
   * sort que par `revelerIpAnomalie`, lecture journalisée. `userId` : celles
   * d'un joueur (onglet Sécurité de sa fiche), sans recharger toute la liste.
   */
  async listerAnomalies(
    opts: { nonVues?: boolean; userId?: string } = {},
  ): Promise<AnomalieListItem[]> {
    const rows = await this.prisma.loginAnomaly.findMany({
      where: {
        ...(opts.nonVues ? { acknowledged: false } : {}),
        ...(opts.userId ? { userId: opts.userId } : {}),
      },
      orderBy: { createdAt: 'desc' },
      take: 100,
    });
    return rows.map((a) => ({
      id: a.id,
      userId: a.userId,
      ipConnue: a.ipAddress.length > 0,
      country: a.country,
      reason: a.reason,
      acknowledged: a.acknowledged,
      notifiedVia: a.notifiedVia,
      createdAt: a.createdAt.toISOString(),
    }));
  }

  /**
   * IP d'UNE connexion suspecte, à la demande. Journalisée AVANT de répondre
   * (`anomaly.ip.view`, cible = l'anomalie et son joueur) : le Journal dit
   * quelle adresse a été vue, pas seulement la dernière IP du joueur.
   */
  async revelerIpAnomalie(
    id: string,
    actorId: string,
  ): Promise<{ ip: string | null; pays: string | null }> {
    const a = await this.prisma.loginAnomaly.findUnique({
      where: { id },
      select: { id: true, userId: true, ipAddress: true, country: true },
    });
    if (!a) throw new NotFoundException('Connexion suspecte introuvable.');
    await this.audit.log({
      actorId,
      action: 'anomaly.ip.view',
      targetUserId: a.userId,
      targetEntity: `anomaly:${a.id}`,
      metadata: { connue: a.ipAddress.length > 0 },
      source: 'panel',
    });
    return { ip: a.ipAddress || null, pays: a.country };
  }

  async getTicket(id: string): Promise<TicketDetail> {
    const ticket = await this.prisma.ticket.findUnique({
      where: { id },
      include: {
        user: {
          select: {
            id: true,
            minecraftUsername: true,
            minecraftUuid: true,
            discordUserId: true,
            discordUsername: true,
            role: true,
          },
        },
        assignedTo: { select: SELECT_COMPTE_STAFF },
        messages: { orderBy: { createdAt: 'asc' } },
      },
    });
    if (!ticket) throw new NotFoundException('Ticket introuvable.');
    return {
      id: ticket.id,
      subject: ticket.subject,
      category: ticket.category,
      status: ticket.status,
      createdAt: ticket.createdAt.toISOString(),
      updatedAt: ticket.updatedAt.toISOString(),
      discordThreadId: ticket.discordThreadId,
      assignee: compteStaff(ticket.assignedTo),
      assignedAt: ticket.assignedAt?.toISOString() ?? null,
      user: ticket.user,
      messages: ticket.messages.map((m) => ({
        id: m.id,
        authorType: m.authorType,
        authorId: m.authorId,
        authorName: m.authorName,
        content: m.content,
        createdAt: m.createdAt.toISOString(),
      })),
    };
  }
}
