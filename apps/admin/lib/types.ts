/**
 * DTOs partages entre l'API et le panel. Mirror cote-pour-cote des
 * interfaces declarees dans apps/api/src/admin/admin.service.ts. Si
 * tu modifies un champ cote API, mets a jour ici aussi (et inversement)
 * jusqu'a ce qu'on extraie un package partage @reborn/shared-types.
 */

export type AppStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'NEEDS_REVISION';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketCategory =
  | 'BUG'
  | 'REPORT_PLAYER'
  | 'WHITELIST_APPEAL'
  | 'PURCHASE_ISSUE'
  | 'OTHER';
export type MessageAuthor = 'USER' | 'STAFF' | 'SYSTEM';
export type Role =
  | 'PLAYER'
  | 'WHITELISTED'
  | 'HELPER'
  | 'MODELISATEUR'
  | 'DEVELOPPEUR'
  | 'WHITELIST_REVIEWER'
  | 'MODERATOR'
  | 'ADMIN'
  | 'OWNER';

/**
 * GET /admin/dashboard. Sans `pilotage.analyses`, l'API ne compte ni les
 * comptes (`users` null) ni le détail des candidatures (null) : seuls restent
 * les chiffres des blocs Maintenant et Staff.
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
    whitelisted: number;
    last24h: number;
  } | null;
}

/**
 * Compte staff (prise en charge, relecture, oral). `username` = nom affiché
 * (Discord s'il existe) ; le visage se tire de `minecraftUuid`, jamais du nom.
 */
export interface Assignee {
  id: string;
  username: string;
  minecraftUsername: string;
  minecraftUuid: string;
}

// ── Candidature v2 (CANDIDATURE_ET_DOUANE.md §1.2, §2.9) ──
// Mirror de apps/api/src/whitelist/whitelist.rules.ts (phase, suivi) et du
// schéma Prisma (listes fermées, codes ASCII ; libellés dans lib/candidature.ts).

export type PhaseCandidature =
  | 'BROUILLON'
  | 'ECRIT_EN_ATTENTE'
  | 'ECRIT_A_REVOIR'
  | 'DOUANE_A_RESERVER'
  | 'DOUANE_RESERVEE'
  | 'DOUANE_A_REVOIR'
  | 'REFUSE'
  | 'ACCEPTE';

export type RpYears =
  | 'JAMAIS'
  | 'MOINS_D_UN_AN'
  | 'UN_A_DEUX_ANS'
  | 'TROIS_A_CINQ_ANS'
  | 'SIX_ANS_ET_PLUS';
export type NarutoKnowledge =
  | 'DEBUTANT'
  | 'QUELQUES_ARCS'
  | 'BONNE_CONNAISSANCE'
  | 'INCOLLABLE';
export type DiscoverySource =
  | 'DISCORD'
  | 'TIKTOK'
  | 'YOUTUBE'
  | 'AMI'
  | 'AUTRE_SERVEUR'
  | 'RECHERCHE'
  | 'AUTRE';
export type CharacterType = 'NINJA' | 'CIVIL';
export type CharacterSex = 'HOMME' | 'FEMME';
export type OralVia = 'DOUANE' | 'HORS_JEU';

/** Onglets « à faire » de la liste (GET /admin/whitelist?filtre=…). */
export type FiltreWhitelist = 'ECRIT' | 'DOUANE' | 'REVISION';

export interface SuiviCandidature {
  phase: PhaseCandidature;
  canReapplyAt: string | null;
  refusPar: 'ECRIT' | 'ORAL' | null;
  partiesRouvertes: Array<'HRP' | 'RP'>;
  /** false : oral déjà réussi sur ce compte, l'écrit validé suffit. */
  oralRequis: boolean;
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: string;
    entreeOuvreAt: string;
    entreeFermeAt: string;
  } | null;
  reservableDes: string | null;
  /** REBORN_DOUANE_ENABLED : false pendant la transition (oral hors jeu). */
  douaneEnJeu: boolean;
}

export interface WhitelistListItem {
  id: string;
  status: AppStatus;
  /** Parties de l'écrit et oral (colonnes Écrit HRP / Écrit RP / Oral). */
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  oralStatus: AppStatus;
  phase: PhaseCandidature;
  oralRequis: boolean;
  reservation: {
    id: string;
    seanceId: string;
    convocationAt: string;
  } | null;
  formVersion: number;
  submittedAt: string;
  reviewedAt: string | null;
  /** Écrit renvoyé par le candidat après « À revoir » (pas un premier envoi). */
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
  assignee: Assignee | null;
  assignedAt: string | null;
}

/** Réponse de GET /admin/whitelist. */
export interface WhitelistListe extends Paginated<WhitelistListItem> {
  compteurs: Record<FiltreWhitelist, number>;
}

export type OralSlotStatus = 'OPEN' | 'BOOKED' | 'DONE' | 'CANCELLED';

export interface OralSlot {
  id: string;
  startAt: string;
  durationMin: number;
  status: OralSlotStatus;
  bookedBy: {
    id: string;
    minecraftUsername: string;
    displayName: string | null;
  } | null;
  bookedAt: string | null;
  notes: string | null;
}

/** Réponse de GET /admin/whitelist/:id (mirror de WhitelistDetail, admin.service.ts). */
export interface WhitelistDetail {
  id: string;
  status: AppStatus;
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  /** 1 = ancien formulaire, 2 = candidature v2. */
  formVersion: number;
  phase: PhaseCandidature;
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
  /** v2 : nom de famille libre (aucun clan à la candidature). */
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
  /** Voix jointe, à écouter par GET /admin/whitelist/:id/voix (jamais une URL publique). */
  voix: {
    mimeType: string;
    durationSec: number | null;
    uploadedAt: string | null;
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
  reviewedBy: Assignee | null;
  writtenApprovedAt: string | null;
  /** Note PRIVÉE sur l'écrit (staff et examinateur seulement). */
  writtenStaffNotes: string | null;
  // Oral
  oral: {
    status: AppStatus;
    decidedAt: string | null;
    via: OralVia | null;
    examiner: Assignee | null;
    /** Motif VISIBLE du joueur. */
    reason: string | null;
    /** Notes PRIVÉES de l'examinateur. */
    notes: string | null;
  };
  discordThreadId: string | null;
  assignee: Assignee | null;
  assignedAt: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUserId: string | null;
    discordUsername: string | null;
    role: Role;
    oralPassedAt: string | null;
    whitelistRejectedAt: string | null;
  };
  messages: AdminMessage[];
}

// ── Douane : séances, réservations, oral (CANDIDATURE_ET_DOUANE.md §3.3) ──
// Mirror de apps/api/src/douane/douane.vues.ts.

export type DouaneSeanceStatut = 'OUVERTE' | 'FERMEE' | 'TERMINEE' | 'ANNULEE';
export type DouaneReservationStatut = 'BOOKED' | 'DONE' | 'MISSED' | 'CANCELLED';
export type EtatFenetre = 'A_VENIR' | 'OUVERTE' | 'FERMEE';
export type EvenementPresence = 'ARRIVEE' | 'APPEL' | 'DEPART';

export interface CompteDouane {
  id: string;
  minecraftUsername: string;
  discordUsername: string | null;
}

export interface ExaminateurDouane extends CompteDouane {
  role: Role;
}

/** GET /admin/examinateurs */
export interface ExaminateurPropose extends ExaminateurDouane {
  minecraftUuid: string;
}

export interface FenetreEntree {
  ouvreAt: string;
  fermeAt: string;
  etat: EtatFenetre;
}

export interface SeanceStaff {
  id: string;
  kind: 'WHITELIST';
  startAt: string;
  endAt: string;
  capacite: number;
  /** Repère : examinateurs × heures × 4 ; null sans examinateur. */
  capaciteConseillee: number | null;
  statut: DouaneSeanceStatut;
  placesPrises: number;
  placesRestantes: number;
  compte: Record<DouaneReservationStatut, number>;
  /** Heures de convocation (vagues de 30 min au-delà d'une heure). */
  vagues: string[];
  examinateurs: ExaminateurDouane[];
  openedBy: CompteDouane | null;
  /** Notes PRIVÉES de clôture. */
  notes: string | null;
  clotureeAt: string | null;
  annuleeAt: string | null;
  motifAnnulation: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReservationStaff {
  id: string;
  seanceId: string;
  statut: DouaneReservationStatut;
  convocationAt: string;
  fenetre: FenetreEntree;
  presentAt: string | null;
  calledAt: string | null;
  departAt: string | null;
  resultat: AppStatus | null;
  decidedAt: string | null;
  examinateur: CompteDouane | null;
  rappelEnvoyeAt: string | null;
  annuleeAt: string | null;
  motifAnnulation: string | null;
  createdAt: string;
  candidat: CompteDouane & { userId: string; seancesManquees: number };
  candidature: {
    id: string;
    firstName: string;
    lastName: string;
    village: string;
    oralStatus: AppStatus;
    /** Oral « à revoir » : marque « 2ᵉ passage ». */
    secondPassage: boolean;
  };
}

export interface SeanceStaffDetail extends SeanceStaff {
  reservations: ReservationStaff[];
}

/** GET /admin/douane/candidatures/:applicationId — bloc Douane de la fiche. */
export interface DouaneCandidature {
  applicationId: string;
  userId: string;
  phase: PhaseCandidature;
  writtenApprovedAt: string | null;
  writtenStaffNotes: string | null;
  oralPassedAt: string | null;
  oral: {
    status: AppStatus;
    via: OralVia | null;
    decidedAt: string | null;
    examinateur: CompteDouane | null;
    reason: string | null;
    notes: string | null;
  };
  reservations: Array<
    ReservationStaff & {
      seance: { startAt: string; endAt: string; statut: DouaneSeanceStatut };
    }
  >;
}

/** Réponse de PATCH /admin/whitelist/:id/oral (carte « Oral hors jeu »). */
export interface OralDecision {
  applicationId: string;
  userId: string;
  resultat: AppStatus;
  status: AppStatus;
  oralStatus: AppStatus;
  oralVia: OralVia | null;
  oralDecidedAt: string | null;
  phase: PhaseCandidature;
  prochaineSeanceDes: string | null;
  canReapplyAt: string | null;
  reservation: {
    id: string;
    seanceId: string;
    statut: DouaneReservationStatut;
    resultat: AppStatus | null;
  } | null;
}

export interface TicketListItem {
  id: string;
  subject: string;
  category: TicketCategory;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  lastMessagePreview: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    discordUsername: string | null;
  };
  assignee: Assignee | null;
  assignedAt: string | null;
}

export interface TicketDetail {
  id: string;
  subject: string;
  category: TicketCategory;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string;
  discordThreadId: string | null;
  assignee: Assignee | null;
  assignedAt: string | null;
  user: {
    id: string;
    minecraftUsername: string;
    minecraftUuid: string;
    discordUserId: string | null;
    discordUsername: string | null;
    role: Role;
  };
  messages: AdminMessage[];
}

export interface AdminMessage {
  id: string;
  authorType: MessageAuthor;
  /** Pour STAFF : id User Reborn de l'auteur si poste depuis le panel,
   *  sinon snowflake Discord si poste via bot. Pour USER : id User. */
  authorId: string | null;
  authorName: string | null;
  content: string;
  createdAt: string;
}

export interface Paginated<T> {
  total: number;
  items: T[];
}

export interface AuditLogItem {
  id: string;
  action: string;
  actor: { id: string; username: string };
  targetUser: { id: string; username: string } | null;
  targetEntity: string | null;
  metadata: unknown;
  source: 'panel' | 'discord' | 'launcher' | 'system' | string;
  ipAddress: string | null;
  createdAt: string;
}

/** Reponse de GET /v1/auth/me — le staff connecte. */
export interface CurrentUser {
  id: string;
  minecraftUuid: string;
  minecraftUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: Role;
  discord: {
    userId: string;
    username: string;
    linkedAt: string;
  } | null;
  /**
   * Capacités du grade au panel (apps/api/src/auth/capacites.ts), relues en
   * base à chaque requête. Le panel ne lit que cette liste (lib/droits.ts).
   * Vide pour un joueur.
   */
  capacites: string[];
}

export interface PlayerListItem {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUsername: string | null;
  role: Role;
  banned: boolean;
  lastLoginAt: string | null;
}

// ── Wiki staff (base de connaissances + banque d'idées) ──
// Mirror des modèles Prisma WikiTag / WikiEntry / WikiRevision / WikiIdea.

export type WikiEntryStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type WikiIdeaStatus =
  | 'PROPOSED'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'DONE'
  | 'REJECTED';
export type WikiTagKind = 'SOURCE' | 'CANON' | 'TYPE' | 'AUDIENCE';

export interface WikiTag {
  id: string;
  kind: WikiTagKind;
  label: string;
  slug: string;
  color: string | null;
  createdAt: string;
}

export interface WikiEntry {
  id: string;
  title: string;
  slug: string;
  summary: string | null;
  body: string;
  status: WikiEntryStatus;
  sources: string | null;
  createdById: string | null;
  createdAt: string;
  updatedAt: string;
  tags: WikiTag[];
  /** Présent seulement sur GET /wiki/entries/:id. */
  _count?: { revisions: number };
}

export interface WikiRevision {
  id: string;
  entryId: string;
  title: string;
  summary: string | null;
  body: string;
  editedById: string | null;
  createdAt: string;
}

export interface WikiIdea {
  id: string;
  title: string;
  body: string;
  status: WikiIdeaStatus;
  category: string | null;
  linkedEntryId: string | null;
  linkedEntry: { id: string; title: string; slug: string } | null;
  createdById: string | null;
  createdAt: string;
  updatedAt: string;
}

// ── Gestionnaire de fichiers serveur ────────────────────
// Mirror du contrat REST /v1/files/* (scopes par grade + éditeur texte/image).

export interface FileScope {
  path: string;
  label: string;
}

export interface FileScopesResponse {
  server: string;
  canWrite: boolean;
  roots: FileScope[];
}

export interface FileEntry {
  name: string;
  path: string;
  type: 'dir' | 'file';
  size: number;
  modified: string;
}

export interface DirListing {
  path: string;
  parent: string | null;
  entries: FileEntry[];
}

export interface FileContent {
  path: string;
  size: number;
  encoding: 'utf8' | 'base64';
  content: string;
  editable: boolean;
  kind: 'text' | 'image' | 'binary';
}

export interface PlayerDetail {
  id: string;
  minecraftUsername: string;
  minecraftUuid: string;
  discordUserId: string | null;
  discordUsername: string | null;
  steamUsername: string | null;
  role: Role;
  banned: boolean;
  banReason: string | null;
  bannedUntil: string | null;
  createdAt: string;
  lastLoginAt: string | null;
  /** Toujours null : l'IP se lit à la demande (GET /admin/players/:id/ip, journalisé). */
  lastKnownIp: null;
  /** Une IP de dernière connexion est-elle connue ? */
  ipConnue: boolean;
  /** Toujours null : le pays suit l'IP (GET /admin/players/:id/ip, journalisé). */
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
    category: TicketCategory;
    status: TicketStatus;
    createdAt: string;
    updatedAt: string;
    lastMessagePreview: string | null;
  }>;
}

/** Réponse de GET /admin/players/:id/ip (capacité joueurs.ip, journalisé). */
export interface IpJoueur {
  ip: string | null;
  pays: string | null;
}

/**
 * Connexion suspecte (GET /admin/anomalies, capacité securite.voir). L'IP
 * n'y est pas : GET /admin/anomalies/:id/ip la lit, journalisée.
 */
export interface LoginAnomaly {
  id: string;
  userId: string;
  ipConnue: boolean;
  country: string | null;
  reason: string;
  acknowledged: boolean;
  notifiedVia: string[];
  createdAt: string;
}

/** Réponse de GET /abilities/deploy/recapitulatif (capacité techniques.deployer). */
export interface RecapitulatifDeploiement {
  serveur: string;
  dernierDeploiement: string | null;
  total: number;
  modifiees: number;
  techniques: Array<{
    id: string;
    slug: string;
    name: string;
    category: string;
    updatedAt: string;
    modifiee: boolean;
  }>;
  /**
   * Déployées la dernière fois, plus publiées : le déploiement les retire du
   * serveur. `status` null = technique supprimée.
   */
  retirees: Array<{
    slug: string;
    name: string;
    status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | null;
  }>;
}

/** Réponse de GET /admin/anomalies/:id/ip (capacité joueurs.ip, journalisé). */
export interface IpAnomalie {
  ip: string | null;
  pays: string | null;
}

/** Réponse de GET /server/status (public) : état du serveur Minecraft. */
export interface EtatServeur {
  online: boolean;
  players: { online: number; max: number };
  version: string | null;
  motd: string | null;
  latencyMs: number | null;
  fetchedAt: string;
}
