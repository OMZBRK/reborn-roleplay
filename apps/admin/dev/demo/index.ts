/**
 * MODE DÉMO : développement uniquement, absent du build de production.
 *
 * Fait tourner le panel sans API ni base, avec les données fictives de
 * scene.ts, pour photographier les vrais écrans (PANEL_V2 §12 : captures en
 * local avant toute publication), comme le mode démo du launcher
 * (apps/launcher/src/dev/demo.ts).
 *
 *   $env:NEXT_PUBLIC_PANEL_DEMO = "1"; pnpm --filter @reborn/admin dev
 *   http://localhost:3002/dashboard                 (Administrateur, par défaut)
 *   http://localhost:3002/dashboard?grade=HELPER    (autre grade, retenu pour l'onglet)
 *   http://localhost:3002/?demo=deconnecte          (page de connexion)
 *
 * Branché dans lib/api.ts (fetchAuthentifie) et lib/auth.ts (getAccessToken)
 * derrière `process.env.NODE_ENV !== 'production' && NEXT_PUBLIC_PANEL_DEMO`,
 * écrit en entier à chaque endroit : `next build` réduit la condition à
 * `false` et n'embarque pas ce dossier. Aucun appel réseau vers l'API.
 *
 * Les actions (prendre, décider, répondre, marquer vu…) modifient l'état en
 * mémoire de l'onglet : la démo reste cohérente jusqu'au rechargement.
 */

import type {
  AppStatus,
  AuditLogItem,
  CurrentUser,
  DashboardStats,
  DouaneCandidature,
  ExaminateurPropose,
  LoginAnomaly,
  OralSlot,
  PhaseCandidature,
  PlayerDetail,
  PlayerListItem,
  ReservationStaff,
  Role,
  SeanceStaff,
  SeanceStaffDetail,
  TicketDetail,
  TicketListItem,
  TicketStatus,
  WhitelistDetail,
  WhitelistListItem,
  WhitelistListe,
  FileScopesResponse,
  DirListing,
  FileContent,
  EtatServeur,
  RecapitulatifDeploiement,
} from '@/lib/types';
import type { TechniqueFull } from '@/lib/abilities';
import {
  ANOMALIES,
  CANDIDATURES,
  COMPTES,
  CRENEAUX,
  DEROULE,
  H,
  IDEES,
  J,
  JOURNAL,
  MESSAGES_DISCORD,
  MIN,
  MOI_PSEUDO,
  SEANCES,
  SEANCE_DU_JOUR,
  T0,
  TAGS,
  TECHNIQUES,
  TICKETS,
  VAGUE_MIN,
  WIKI,
  avant,
  comptePar,
  compteParId,
  messagesDe,
  type CandidatureDemo,
  type CompteDemo,
  type SeanceDemo,
  type TicketDemo,
} from './scene';

// ── Grade simulé ──────────────────────────────────────────────────────

const GRADES: readonly Role[] = [
  'PLAYER',
  'WHITELISTED',
  'HELPER',
  'MODELISATEUR',
  'DEVELOPPEUR',
  'WHITELIST_REVIEWER',
  'MODERATOR',
  'ADMIN',
  'OWNER',
];

/** `?grade=HELPER` (retenu pour l'onglet), Administrateur par défaut. */
function gradeSimule(): Role {
  try {
    const demande = new URLSearchParams(window.location.search).get('grade')?.toUpperCase();
    if (demande && (GRADES as readonly string[]).includes(demande)) {
      window.sessionStorage.setItem('reborn-admin.demo.grade', demande);
      return demande as Role;
    }
    const retenu = window.sessionStorage.getItem('reborn-admin.demo.grade');
    if (retenu && (GRADES as readonly string[]).includes(retenu)) return retenu as Role;
  } catch {
    // Stockage indisponible : grade par défaut.
  }
  return 'ADMIN';
}

// Copie de la table de l'API (apps/api/src/auth/capacites.ts) : la démo ne
// peut pas l'importer. Sert seulement à montrer la coquille de chaque grade.
const STAFF: Role[] = ['HELPER', 'MODELISATEUR', 'DEVELOPPEUR', 'WHITELIST_REVIEWER', 'MODERATOR', 'ADMIN', 'OWNER'];
const MODERATION: Role[] = ['HELPER', 'WHITELIST_REVIEWER', 'MODERATOR', 'ADMIN', 'OWNER'];
const RECRUTEMENT: Role[] = ['WHITELIST_REVIEWER', 'MODERATOR', 'ADMIN', 'OWNER'];
const MODERATEUR_ET_PLUS: Role[] = ['MODERATOR', 'ADMIN', 'OWNER'];
const TECHNIQUE: Role[] = ['DEVELOPPEUR', 'ADMIN', 'OWNER'];
const DIRECTION: Role[] = ['ADMIN', 'OWNER'];

const TABLE: Record<string, Role[]> = {
  'panel.acceder': STAFF,
  'pilotage.voir': STAFF,
  'pilotage.analyses': MODERATEUR_ET_PLUS,
  'pilotage.demographie': DIRECTION,
  'pilotage.revenus': ['OWNER'],
  'joueurs.voir': MODERATION,
  'joueurs.ip': DIRECTION,
  'grades.changer': DIRECTION,
  'candidatures.relire': RECRUTEMENT,
  'candidatures.reinitialiser': DIRECTION,
  'douane.organiser': RECRUTEMENT,
  'douane.examiner': RECRUTEMENT,
  'tickets.traiter': MODERATION,
  'tickets.bug': [...MODERATION, 'DEVELOPPEUR'],
  'prises.forcer': DIRECTION,
  'sanctions.voir': MODERATION,
  'sanctions.poser': MODERATEUR_ET_PLUS,
  'securite.voir': DIRECTION,
  'audit.voir': DIRECTION,
  'wiki.editer': STAFF,
  'wiki.supprimer': MODERATEUR_ET_PLUS,
  'discord.publier': DIRECTION,
  'techniques.editer': TECHNIQUE,
  'techniques.deployer': TECHNIQUE,
  'techniques.supprimer': DIRECTION,
  'reponses-types.gerer': MODERATEUR_ET_PLUS,
};

const ZONES: Partial<Record<Role, string[]>> = {
  MODELISATEUR: ['nexo', 'creator', 'emotes'],
  DEVELOPPEUR: ['nexo', 'creator', 'magicspells', 'mythicmobs', 'modelengine', 'emotes', 'abilities'],
  ADMIN: ['nexo', 'creator', 'magicspells', 'mythicmobs', 'modelengine', 'emotes', 'abilities', 'plugins'],
  OWNER: ['nexo', 'creator', 'magicspells', 'mythicmobs', 'modelengine', 'emotes', 'abilities', 'plugins', 'racine'],
};

function capacitesDe(role: Role): string[] {
  const fixes = Object.entries(TABLE)
    .filter(([, grades]) => grades.includes(role))
    .map(([c]) => c);
  const zones = (ZONES[role] ?? []).map((z) => `fichiers.${z}`);
  return zones.length > 0 ? [...fixes, ...zones, 'fichiers.ecrire'] : fixes;
}

// ── État en mémoire ───────────────────────────────────────────────────

const etat = {
  candidatures: CANDIDATURES.map((c) => ({ ...c, messages: [...c.messages] })),
  tickets: TICKETS.map((t) => ({ ...t, messages: [...t.messages] })),
  anomalies: ANOMALIES.map((a) => ({ ...a })),
  seances: SEANCES.map((s) => ({ ...s })),
  roles: new Map<string, Role>(),
};

function moi(): CompteDemo {
  return comptePar(MOI_PSEUDO);
}

function roleDe(c: CompteDemo): Role {
  if (c.pseudo === MOI_PSEUDO) return gradeSimule();
  return etat.roles.get(c.id) ?? c.role;
}

/** Compte staff comme l'API le renvoie (compteStaff) : nom affiché, pseudo et UUID Minecraft. */
const assignee = (pseudo: string | null) => {
  if (!pseudo) return null;
  const c = comptePar(pseudo);
  return { id: c.id, username: c.discord ?? c.pseudo, minecraftUsername: c.pseudo, minecraftUuid: c.uuid };
};

/** Relu avant l'envoi courant : un renvoi après « À revoir » (l'API remet alors reviewedAt à null). */
const estRenvoi = (c: CandidatureDemo) => c.relueIlYa !== null && c.relueIlYa > c.envoyeeIlYa;
const relueLe = (c: CandidatureDemo) =>
  c.relueIlYa !== null && !estRenvoi(c) ? avant(c.relueIlYa) : null;

const compteDouane = (c: CompteDemo) => ({
  id: c.id,
  minecraftUsername: c.pseudo,
  discordUsername: c.discord,
});

// ── Douane ────────────────────────────────────────────────────────────

function vaguesDe(s: SeanceDemo): string[] {
  const debut = Date.parse(s.startAt);
  const dureeMin = (Date.parse(s.endAt) - debut) / MIN;
  if (dureeMin <= 60) return [s.startAt];
  const v: string[] = [];
  for (let t = 0; t < dureeMin - VAGUE_MIN; t += VAGUE_MIN) v.push(new Date(debut + t * MIN).toISOString());
  return v;
}

/** Réservations détaillées (séance du jour seulement). */
function reservationsDe(s: SeanceDemo): ReservationStaff[] {
  if (s.id !== SEANCE_DU_JOUR) return [];
  const vagues = vaguesDe(s);
  const debut = Date.parse(s.startAt);
  const maintenant = Date.now();
  return etat.candidatures
    .filter((c) => c.reservation?.seance === s.id)
    .map((c) => {
      const d = DEROULE[c.pseudo] ?? { vague: 0 };
      const convocationAt = vagues[Math.min(d.vague, vagues.length - 1)]!;
      const h = Date.parse(convocationAt);
      const ouvre = h - 15 * MIN;
      const ferme = h + 30 * MIN;
      const quand = (m?: number) => (m === undefined ? null : new Date(debut + m * MIN).toISOString());
      const compte = comptePar(c.pseudo);
      const exam = d.examinateur ? comptePar(d.examinateur) : null;
      const statut: ReservationStaff['statut'] = d.resultat ? 'DONE' : d.absent ? 'MISSED' : 'BOOKED';
      return {
        id: `r-${c.id}`,
        seanceId: s.id,
        statut,
        convocationAt,
        fenetre: {
          ouvreAt: new Date(ouvre).toISOString(),
          fermeAt: new Date(ferme).toISOString(),
          etat: maintenant < ouvre ? 'A_VENIR' : maintenant > ferme ? 'FERMEE' : 'OUVERTE',
        },
        presentAt: quand(d.arrive),
        calledAt: quand(d.appele),
        departAt: quand(d.sorti),
        resultat: d.resultat ?? null,
        decidedAt: d.resultat ? quand(d.sorti) : null,
        examinateur: exam ? compteDouane(exam) : null,
        rappelEnvoyeAt: new Date(debut - 60 * MIN).toISOString(),
        annuleeAt: null,
        motifAnnulation: null,
        createdAt: avant(2 * J),
        candidat: { ...compteDouane(compte), userId: compte.id, seancesManquees: d.absent ? 1 : 0 },
        candidature: {
          id: c.id,
          firstName: c.firstName,
          lastName: c.lastName,
          village: c.village,
          oralStatus: d.resultat ?? 'PENDING',
          secondPassage: c.pseudo === 'Jade_',
        },
      };
    });
}

function seanceStaff(s: SeanceDemo): SeanceStaff {
  const reservations = reservationsDe(s);
  const compte = { BOOKED: 0, DONE: 0, MISSED: 0, CANCELLED: 0 };
  for (const r of reservations) compte[r.statut]++;
  if (s.passe) {
    compte.DONE = s.passe.faites;
    compte.MISSED = s.passe.manquees;
    compte.CANCELLED = s.passe.annulees;
  }
  if (s.inscrits !== undefined) compte.BOOKED = s.inscrits;
  const placesPrises = compte.BOOKED + compte.DONE + compte.MISSED;
  const heures = (Date.parse(s.endAt) - Date.parse(s.startAt)) / H;
  const examinateurs = s.examinateurs.map((p) => {
    const c = comptePar(p);
    return { ...compteDouane(c), role: roleDe(c) };
  });
  return {
    id: s.id,
    kind: 'WHITELIST',
    startAt: s.startAt,
    endAt: s.endAt,
    capacite: s.capacite,
    capaciteConseillee: examinateurs.length > 0 ? Math.round(examinateurs.length * heures * 4) : null,
    statut: s.statut,
    placesPrises,
    placesRestantes: Math.max(0, s.capacite - placesPrises),
    compte,
    vagues: vaguesDe(s),
    examinateurs,
    openedBy: compteDouane(comptePar('Renji')),
    notes: s.notes,
    clotureeAt: s.statut === 'TERMINEE' ? s.endAt : null,
    annuleeAt: s.statut === 'ANNULEE' ? new Date(Date.parse(s.startAt) - 2 * J).toISOString() : null,
    motifAnnulation: s.motifAnnulation,
    createdAt: new Date(Date.parse(s.startAt) - 6 * J).toISOString(),
    updatedAt: avant(H),
  };
}

function seanceDetail(s: SeanceDemo): SeanceStaffDetail {
  return { ...seanceStaff(s), reservations: reservationsDe(s) };
}

// ── Candidatures ──────────────────────────────────────────────────────

/** Phase et statuts après la séance du jour (oral décidé au Poste de garde). */
function apresDouane(c: CandidatureDemo): CandidatureDemo {
  const d = c.reservation?.seance === SEANCE_DU_JOUR ? DEROULE[c.pseudo] : undefined;
  if (!d) return c;
  if (d.resultat === 'APPROVED') {
    return { ...c, phase: 'ACCEPTE', status: 'APPROVED', oral: 'APPROVED', examinateur: d.examinateur ?? null, oralDecideIlYa: 30 * MIN };
  }
  if (d.resultat === 'NEEDS_REVISION') {
    return { ...c, phase: 'DOUANE_A_REVOIR', oral: 'NEEDS_REVISION', examinateur: d.examinateur ?? null, oralDecideIlYa: 30 * MIN, reservation: c.reservation };
  }
  if (d.absent) return { ...c, phase: 'DOUANE_A_RESERVER' };
  return c;
}

function toutesCandidatures(): CandidatureDemo[] {
  return etat.candidatures.map(apresDouane);
}

function reservationListe(c: CandidatureDemo) {
  if (!c.reservation || c.phase !== 'DOUANE_RESERVEE') return null;
  const r = reservationsDe(etat.seances.find((s) => s.id === c.reservation!.seance)!).find(
    (x) => x.candidature.id === c.id,
  );
  return r ? { id: r.id, seanceId: r.seanceId, convocationAt: r.convocationAt } : null;
}

function ligneCandidature(c: CandidatureDemo): WhitelistListItem {
  const u = comptePar(c.pseudo);
  return {
    id: c.id,
    status: c.status,
    hrpStatus: c.hrp,
    rpStatus: c.rp,
    oralStatus: c.oral,
    phase: c.phase,
    oralRequis: true,
    reservation: reservationListe(c),
    formVersion: 2,
    submittedAt: avant(c.envoyeeIlYa),
    reviewedAt: relueLe(c),
    renvoi: estRenvoi(c),
    writtenApprovedAt: c.ecritValideIlYa !== null ? avant(c.ecritValideIlYa) : null,
    firstName: c.firstName,
    lastName: c.lastName,
    village: c.village,
    user: { id: u.id, minecraftUsername: u.pseudo, minecraftUuid: u.uuid, discordUsername: u.discord },
    assignee: assignee(c.pris),
    assignedAt: c.prisIlYa !== null ? avant(c.prisIlYa) : c.pris ? avant(c.envoyeeIlYa - H) : null,
  };
}

const FILTRES: Record<string, (c: CandidatureDemo) => boolean> = {
  ECRIT: (c) => c.phase === 'ECRIT_EN_ATTENTE',
  DOUANE: (c) => c.phase === 'DOUANE_A_RESERVER' || c.phase === 'DOUANE_RESERVEE' || c.phase === 'DOUANE_A_REVOIR',
  REVISION: (c) => c.phase === 'ECRIT_A_REVOIR',
};

function listeCandidatures(p: URLSearchParams): WhitelistListe {
  const toutes = toutesCandidatures();
  const filtre = p.get('filtre');
  const statut = p.get('status') as AppStatus | null;
  const q = (p.get('q') ?? '').trim().toLowerCase();
  let l = toutes.filter((c) => {
    if (filtre && FILTRES[filtre] && !FILTRES[filtre]!(c)) return false;
    if (statut && c.status !== statut) return false;
    if (q) {
      const u = comptePar(c.pseudo);
      const champs = [c.firstName, c.lastName, u.pseudo, u.discord ?? ''].join(' ').toLowerCase();
      if (!champs.includes(q)) return false;
    }
    return true;
  });
  if (filtre === 'ECRIT') l = l.sort((a, b) => b.envoyeeIlYa - a.envoyeeIlYa);
  else if (filtre === 'DOUANE') l = l.sort((a, b) => (b.ecritValideIlYa ?? 0) - (a.ecritValideIlYa ?? 0));
  else if (filtre === 'REVISION') l = l.sort((a, b) => (a.relueIlYa ?? 0) - (b.relueIlYa ?? 0));
  else {
    const ordre: AppStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'NEEDS_REVISION'];
    l = l.sort((a, b) => ordre.indexOf(a.status) - ordre.indexOf(b.status) || a.envoyeeIlYa - b.envoyeeIlYa);
  }
  const take = Number(p.get('take') ?? 50);
  const skip = Number(p.get('skip') ?? 0);
  return {
    total: l.length,
    items: l.slice(skip, skip + take).map(ligneCandidature),
    compteurs: {
      ECRIT: toutes.filter(FILTRES.ECRIT!).length,
      DOUANE: toutes.filter(FILTRES.DOUANE!).length,
      REVISION: toutes.filter(FILTRES.REVISION!).length,
    },
  };
}

function suiviDe(c: CandidatureDemo): WhitelistDetail['suivi'] {
  const r = reservationListe(c);
  const s = r ? etat.seances.find((x) => x.id === r.seanceId) : null;
  const res = r && s ? reservationsDe(s).find((x) => x.id === r.id) : null;
  return {
    phase: c.phase,
    canReapplyAt: c.phase === 'REFUSE' ? avant(c.envoyeeIlYa - 6 * J) : null,
    refusPar: c.phase === 'REFUSE' ? 'ECRIT' : null,
    partiesRouvertes: c.rp === 'NEEDS_REVISION' ? ['RP'] : c.hrp === 'NEEDS_REVISION' ? ['HRP'] : [],
    oralRequis: true,
    reservation:
      res && r
        ? {
            id: r.id,
            seanceId: r.seanceId,
            convocationAt: r.convocationAt,
            entreeOuvreAt: res.fenetre.ouvreAt,
            entreeFermeAt: res.fenetre.fermeAt,
          }
        : null,
    reservableDes: c.phase === 'DOUANE_A_RESERVER' ? avant(H) : null,
    douaneEnJeu: true,
  };
}

function detailCandidature(c: CandidatureDemo): WhitelistDetail {
  const u = comptePar(c.pseudo);
  const decide = c.phase === 'ACCEPTE' || c.phase === 'REFUSE';
  return {
    id: c.id,
    status: c.status,
    hrpStatus: c.hrp,
    rpStatus: c.rp,
    formVersion: 2,
    phase: c.phase,
    suivi: suiviDe(c),
    playerAge: c.ageJoueur,
    dob: null,
    rpYears: c.rpYears,
    narutoKnowledge: c.naruto,
    discoverySource: c.source,
    availability: c.dispo,
    motivation: null,
    experience: null,
    firstName: c.firstName,
    lastName: c.lastName,
    village: c.village,
    characterType: c.type,
    characterSex: c.sexe,
    characterAge: c.agePerso,
    support: c.support,
    history: c.histoire,
    appearance: null,
    objectives: c.objectifs,
    voix: {
      mimeType: 'audio/wav',
      durationSec: 47,
      uploadedAt: avant(c.envoyeeIlYa),
      purgeAt: decide ? avant(-(30 * J - (c.relueIlYa ?? 0))) : null,
    },
    voiceDeletedAt: null,
    rulesAcceptedAt: avant(c.envoyeeIlYa + 10 * MIN),
    voiceConsentAt: avant(c.envoyeeIlYa + 5 * MIN),
    submittedAt: avant(c.envoyeeIlYa),
    reviewedAt: relueLe(c),
    reviewNotes: estRenvoi(c) ? null : c.motif,
    reviewedBy: assignee(c.relu),
    writtenApprovedAt: c.ecritValideIlYa !== null ? avant(c.ecritValideIlYa) : null,
    writtenStaffNotes: c.notePrivee,
    oral: {
      status: c.oral,
      decidedAt: c.oralDecideIlYa !== null ? avant(c.oralDecideIlYa) : null,
      via: c.oral !== 'PENDING' ? 'DOUANE' : null,
      examiner: assignee(c.examinateur),
      reason: c.oral === 'NEEDS_REVISION' ? 'Hésitations sur le rôle du personnage au village.' : null,
      notes: null,
    },
    discordThreadId: null,
    assignee: assignee(c.pris),
    assignedAt: c.prisIlYa !== null ? avant(c.prisIlYa) : null,
    user: {
      id: u.id,
      minecraftUsername: u.pseudo,
      minecraftUuid: u.uuid,
      discordUserId: '000000000000000000',
      discordUsername: u.discord,
      role: roleDe(u),
      oralPassedAt: c.oral === 'APPROVED' && c.oralDecideIlYa !== null ? avant(c.oralDecideIlYa) : null,
      whitelistRejectedAt: c.phase === 'REFUSE' && c.relueIlYa !== null ? avant(c.relueIlYa) : null,
    },
    messages: messagesDe(c.id, [
      { auteur: 'SYSTEM', nom: null, texte: '✅ Nouvelle candidature envoyée. Elle est en cours de relecture par l’équipe whitelist.', ilYa: c.envoyeeIlYa },
      ...c.messages,
    ]),
  };
}

function douaneCandidature(c: CandidatureDemo): DouaneCandidature {
  const u = comptePar(c.pseudo);
  const reservations = etat.seances.flatMap((s) =>
    reservationsDe(s)
      .filter((r) => r.candidature.id === c.id)
      .map((r) => ({ ...r, seance: { startAt: s.startAt, endAt: s.endAt, statut: s.statut } })),
  );
  return {
    applicationId: c.id,
    userId: u.id,
    phase: c.phase,
    writtenApprovedAt: c.ecritValideIlYa !== null ? avant(c.ecritValideIlYa) : null,
    writtenStaffNotes: c.notePrivee,
    oralPassedAt: c.oral === 'APPROVED' && c.oralDecideIlYa !== null ? avant(c.oralDecideIlYa) : null,
    oral: {
      status: c.oral,
      via: c.oral !== 'PENDING' ? 'DOUANE' : null,
      decidedAt: c.oralDecideIlYa !== null ? avant(c.oralDecideIlYa) : null,
      examinateur: c.examinateur ? compteDouane(comptePar(c.examinateur)) : null,
      reason: null,
      notes: null,
    },
    reservations,
  };
}

// ── Tickets ───────────────────────────────────────────────────────────

function ligneTicket(t: TicketDemo): TicketListItem {
  const u = comptePar(t.pseudo);
  const dernier = [...t.messages].reverse().find((m) => m.auteur !== 'SYSTEM');
  return {
    id: t.id,
    subject: t.subject,
    category: t.category,
    status: t.status,
    createdAt: avant(t.creeIlYa),
    updatedAt: avant(t.majIlYa),
    lastMessagePreview: dernier?.texte ?? null,
    user: { id: u.id, minecraftUsername: u.pseudo, discordUsername: u.discord },
    assignee: assignee(t.pris),
    assignedAt: t.prisIlYa !== null ? avant(t.prisIlYa) : null,
  };
}

function detailTicket(t: TicketDemo): TicketDetail {
  const u = comptePar(t.pseudo);
  return {
    id: t.id,
    subject: t.subject,
    category: t.category,
    status: t.status,
    createdAt: avant(t.creeIlYa),
    updatedAt: avant(t.majIlYa),
    discordThreadId: '000000000000000001',
    assignee: assignee(t.pris),
    assignedAt: t.prisIlYa !== null ? avant(t.prisIlYa) : null,
    user: {
      id: u.id,
      minecraftUsername: u.pseudo,
      minecraftUuid: u.uuid,
      discordUserId: '000000000000000000',
      discordUsername: u.discord,
      role: roleDe(u),
    },
    messages: messagesDe(t.id, t.messages),
  };
}

function ticketsVisibles(): TicketDemo[] {
  const caps = capacitesDe(gradeSimule());
  if (caps.includes('tickets.traiter')) return etat.tickets;
  if (caps.includes('tickets.bug')) return etat.tickets.filter((t) => t.category === 'BUG');
  return [];
}

function listeTickets(p: URLSearchParams) {
  const statut = p.get('status') as TicketStatus | null;
  const ordre: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
  const l = ticketsVisibles()
    .filter((t) => !statut || t.status === statut)
    .sort((a, b) => ordre.indexOf(a.status) - ordre.indexOf(b.status) || a.majIlYa - b.majIlYa);
  const take = Number(p.get('take') ?? 50);
  const skip = Number(p.get('skip') ?? 0);
  return { total: l.length, items: l.slice(skip, skip + take).map(ligneTicket) };
}

// ── Joueurs ───────────────────────────────────────────────────────────

function ligneJoueur(c: CompteDemo): PlayerListItem {
  return {
    id: c.id,
    minecraftUsername: c.pseudo,
    minecraftUuid: c.uuid,
    discordUsername: c.discord,
    role: roleDe(c),
    banned: !!c.banni,
    lastLoginAt: c.vuIlYa !== null ? avant(c.vuIlYa) : null,
  };
}

function chercherJoueurs(p: URLSearchParams): PlayerListItem[] {
  const q = (p.get('q') ?? '').trim().toLowerCase();
  const take = Number(p.get('take') ?? 30);
  // Filtres Grade et Statut appliqués avant la limite, comme l'API.
  const role = p.get('role');
  const statut = p.get('statut');
  return COMPTES.filter((c) => {
    if (q && ![c.pseudo, c.discord ?? '', c.uuid].join(' ').toLowerCase().includes(q)) return false;
    const r = roleDe(c);
    if (role && r !== role) return false;
    if (statut === 'banni') return !!c.banni;
    if (statut === 'whiteliste') return !c.banni && r !== 'PLAYER';
    if (statut === 'non') return !c.banni && r === 'PLAYER';
    return true;
  })
    .sort((a, b) => (a.vuIlYa ?? Infinity) - (b.vuIlYa ?? Infinity))
    .slice(0, take)
    .map(ligneJoueur);
}

function detailJoueur(c: CompteDemo): PlayerDetail {
  const app = toutesCandidatures().find((x) => x.pseudo === c.pseudo);
  return {
    id: c.id,
    minecraftUsername: c.pseudo,
    minecraftUuid: c.uuid,
    discordUserId: c.discord ? '000000000000000000' : null,
    discordUsername: c.discord,
    steamUsername: null,
    role: roleDe(c),
    banned: !!c.banni,
    banReason: c.banni?.motif ?? null,
    bannedUntil: c.banni?.jusquA ?? null,
    createdAt: avant(c.creeIlYa),
    lastLoginAt: c.vuIlYa !== null ? avant(c.vuIlYa) : null,
    lastKnownIp: null,
    ipConnue: true,
    lastKnownCountry: null,
    whitelist: app
      ? {
          id: app.id,
          status: app.status,
          firstName: app.firstName,
          lastName: app.lastName,
          village: app.village,
          submittedAt: avant(app.envoyeeIlYa),
          reviewedAt: app.relueIlYa !== null ? avant(app.relueIlYa) : null,
        }
      : null,
    tickets: etat.tickets
      .filter((t) => t.pseudo === c.pseudo)
      .map((t) => {
        const l = ligneTicket(t);
        return {
          id: l.id,
          subject: l.subject,
          category: l.category,
          status: l.status,
          createdAt: l.createdAt,
          updatedAt: l.updatedAt,
          lastMessagePreview: l.lastMessagePreview,
        };
      }),
  };
}

// ── Journal ───────────────────────────────────────────────────────────

function journal(p: URLSearchParams) {
  const action = p.get('action');
  const source = p.get('source');
  const acteur = p.get('actor');
  const cible = p.get('targetUserId');
  const items: AuditLogItem[] = JOURNAL.map((l) => {
    const a = comptePar(l.acteur);
    const c = l.cible ? comptePar(l.cible) : null;
    return {
      id: l.id,
      action: l.action,
      actor: { id: a.id, username: a.pseudo },
      targetUser: c ? { id: c.id, username: c.pseudo } : null,
      targetEntity: l.targetEntity,
      metadata: l.metadata,
      source: l.source,
      ipAddress: null,
      createdAt: l.createdAt,
    };
  }).filter(
    (l) =>
      (!action || l.action.startsWith(action)) &&
      (!source || l.source === source) &&
      (!acteur || l.actor.id === acteur) &&
      (!cible || l.targetUser?.id === cible),
  ).sort((x, y) => y.createdAt.localeCompare(x.createdAt));
  const take = Number(p.get('take') ?? 50);
  const skip = Number(p.get('skip') ?? 0);
  return { total: items.length, items: items.slice(skip, skip + take) };
}

// ── Fichiers ──────────────────────────────────────────────────────────

function scopes(): FileScopesResponse {
  const role = gradeSimule();
  const racines: Partial<Record<Role, FileScopesResponse['roots']>> = {
    MODELISATEUR: [
      { path: 'plugins/Nexo', label: 'Nexo — modèles & items' },
      { path: 'plugins/ShinobiCore/emotes', label: 'Emotes RP' },
      { path: 'plugins/ShinobiCore/creator-assets', label: 'Character creator — tenues/cheveux/yeux (diffusion live)' },
    ],
    DEVELOPPEUR: [
      { path: 'plugins/Nexo', label: 'Nexo — modèles & items' },
      { path: 'plugins/MagicSpells', label: 'MagicSpells' },
      { path: 'plugins/ShinobiAbilities', label: 'Techniques — abilities.yml généré' },
    ],
    ADMIN: [{ path: 'plugins', label: 'plugins/' }],
    OWNER: [{ path: '', label: 'Serveur (racine)' }],
  };
  const roots = racines[role] ?? [];
  return { server: 'Reborn · Développement', canWrite: roots.length > 0, roots };
}

const ARBRE: Record<string, Array<{ name: string; type: 'dir' | 'file'; size: number; ilYa: number }>> = {
  '': [
    { name: 'plugins', type: 'dir', size: 0, ilYa: 2 * H },
    { name: 'server.properties', type: 'file', size: 1420, ilYa: 9 * J },
  ],
  plugins: [
    { name: 'MagicSpells', type: 'dir', size: 0, ilYa: 28 * H },
    { name: 'ModelEngine', type: 'dir', size: 0, ilYa: 4 * J },
    { name: 'MythicMobs', type: 'dir', size: 0, ilYa: 6 * J },
    { name: 'Nexo', type: 'dir', size: 0, ilYa: 26 * H },
    { name: 'ShinobiAbilities', type: 'dir', size: 0, ilYa: 28 * H },
    { name: 'ShinobiCore', type: 'dir', size: 0, ilYa: 2 * H },
  ],
  'plugins/Nexo': [
    { name: 'items', type: 'dir', size: 0, ilYa: 26 * H },
    { name: 'pack', type: 'dir', size: 0, ilYa: 26 * H },
    { name: 'config.yml', type: 'file', size: 8_214, ilYa: 12 * J },
  ],
  'plugins/Nexo/items': [
    { name: 'parchemins.yml', type: 'file', size: 4_120, ilYa: 26 * H },
    { name: 'sacs.yml', type: 'file', size: 2_388, ilYa: 3 * J },
    { name: 'masques.yml', type: 'file', size: 1_902, ilYa: 2 * J },
  ],
};

function listerDossier(chemin: string): DirListing {
  const entrees = ARBRE[chemin] ?? [];
  const i = chemin.lastIndexOf('/');
  return {
    path: chemin,
    parent: chemin === '' ? null : i > 0 ? chemin.slice(0, i) : '',
    entries: entrees.map((e) => ({
      name: e.name,
      path: chemin ? `${chemin}/${e.name}` : e.name,
      type: e.type,
      size: e.size,
      modified: avant(e.ilYa),
    })),
  };
}

function lireFichier(chemin: string): FileContent {
  const contenu = `# Fichier simulé (mode démo)\nparchemin_katon:\n  material: PAPER\n  display_name: "<gold>Parchemin : Katon"\n  model: reborn:parchemin_katon\n`;
  return { path: chemin, size: contenu.length, encoding: 'utf8', content: contenu, editable: true, kind: 'text' };
}

// ── Pilotage ──────────────────────────────────────────────────────────

function tableau(): DashboardStats {
  const toutes = toutesCandidatures();
  const n = (s: AppStatus) => toutes.filter((c) => c.status === s).length;
  // Comme l'API : sans pilotage.analyses, ni comptes ni détail du recrutement.
  const analyses = capacitesDe(gradeSimule()).includes('pilotage.analyses');
  return {
    whitelist: {
      pending: n('PENDING'),
      needsRevision: analyses ? n('NEEDS_REVISION') : null,
      approved: analyses ? n('APPROVED') : null,
      rejected: analyses ? n('REJECTED') : null,
    },
    tickets: {
      open: etat.tickets.filter((t) => t.status === 'OPEN').length,
      inProgress: etat.tickets.filter((t) => t.status === 'IN_PROGRESS').length,
      resolved: etat.tickets.filter((t) => t.status === 'RESOLVED').length,
      closed: etat.tickets.filter((t) => t.status === 'CLOSED').length,
    },
    users: analyses
      ? {
          total: COMPTES.length,
          whitelisted: COMPTES.filter((c) => roleDe(c) !== 'PLAYER').length,
          last24h: COMPTES.filter((c) => c.creeIlYa <= J).length,
        }
      : null,
  };
}

// ── Réponses ──────────────────────────────────────────────────────────

class Refus extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

function json(data: unknown, status = 200): Response {
  return new Response(data === undefined ? '' : JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Voix simulée : deux secondes de silence (WAV 8 kHz, 8 bits). */
function voixSimulee(): Response {
  const n = 16_000;
  const buf = new ArrayBuffer(44 + n);
  const v = new DataView(buf);
  const ecrire = (o: number, s: string) => [...s].forEach((ch, i) => v.setUint8(o + i, ch.charCodeAt(0)));
  ecrire(0, 'RIFF');
  v.setUint32(4, 36 + n, true);
  ecrire(8, 'WAVE');
  ecrire(12, 'fmt ');
  v.setUint32(16, 16, true);
  v.setUint16(20, 1, true);
  v.setUint16(22, 1, true);
  v.setUint32(24, 8_000, true);
  v.setUint32(28, 8_000, true);
  v.setUint16(32, 1, true);
  v.setUint16(34, 8, true);
  ecrire(36, 'data');
  v.setUint32(40, n, true);
  for (let i = 0; i < n; i++) v.setUint8(44 + i, 128);
  return new Response(new Blob([buf], { type: 'audio/wav' }), { status: 200 });
}

function exiger(capacite: string) {
  if (!capacitesDe(gradeSimule()).includes(capacite)) {
    throw new Refus(403, 'Réservé à un autre grade (mode démo).');
  }
}

function trouver<T>(l: T[], pred: (x: T) => boolean, quoi: string): T {
  const x = l.find(pred);
  if (!x) throw new Refus(404, `${quoi} introuvable (mode démo).`);
  return x;
}

type Corps = Record<string, unknown> | undefined;

function router(methode: string, chemin: string, p: URLSearchParams, corps: Corps): unknown {
  const seg = chemin.split('/').filter(Boolean);
  const [a, b, c, d, e] = seg;

  // Compte connecté
  if (chemin === '/auth/me') {
    const m = moi();
    const role = gradeSimule();
    const me: CurrentUser = {
      id: m.id,
      minecraftUuid: m.uuid,
      minecraftUsername: m.pseudo,
      displayName: m.pseudo,
      avatarUrl: null,
      role,
      discord: { userId: '000000000000000000', username: m.discord ?? m.pseudo, linkedAt: avant(100 * J) },
      capacites: capacitesDe(role),
    };
    return me;
  }
  if (chemin === '/auth/2fa/status') return { enabled: true };
  if (a === 'auth') return { ok: true };

  if (chemin === '/server/status') {
    const s: EtatServeur = {
      online: true,
      players: { online: 23, max: 120 },
      version: '26.2',
      motd: 'Reborn Roleplay',
      latencyMs: 38,
      fetchedAt: new Date().toISOString(),
    };
    return s;
  }

  if (a === 'admin') {
    // Candidatures
    if (b === 'whitelist') {
      if (!c && methode === 'GET') return listeCandidatures(p);
      const cand = trouver(etat.candidatures, (x) => x.id === c, 'Candidature');
      if (!d && methode === 'GET') return detailCandidature(apresDouane(cand));
      if (d === 'voix') return voixSimulee();
      if (d === 'assign') {
        cand.pris = methode === 'DELETE' ? null : MOI_PSEUDO;
        cand.prisIlYa = methode === 'DELETE' ? null : 0;
        return { ok: true };
      }
      if (d === 'decision') {
        const partie = String(corps?.part ?? 'BOTH');
        const decision = (corps?.decision ?? corps?.status ?? 'APPROVED') as AppStatus;
        if (partie === 'HRP' || partie === 'BOTH') cand.hrp = decision;
        if (partie === 'RP' || partie === 'BOTH') cand.rp = decision;
        cand.pris = cand.pris ?? MOI_PSEUDO;
        cand.relu = MOI_PSEUDO;
        cand.relueIlYa = 0;
        if (typeof corps?.reviewNotes === 'string') cand.motif = corps.reviewNotes;
        const phase: PhaseCandidature =
          cand.hrp === 'REJECTED' || cand.rp === 'REJECTED'
            ? 'REFUSE'
            : cand.hrp === 'NEEDS_REVISION' || cand.rp === 'NEEDS_REVISION'
              ? 'ECRIT_A_REVOIR'
              : cand.hrp === 'APPROVED' && cand.rp === 'APPROVED'
                ? 'DOUANE_A_RESERVER'
                : 'ECRIT_EN_ATTENTE';
        cand.phase = phase;
        cand.status = phase === 'REFUSE' ? 'REJECTED' : phase === 'ECRIT_A_REVOIR' ? 'NEEDS_REVISION' : 'PENDING';
        if (phase === 'DOUANE_A_RESERVER') cand.ecritValideIlYa = 0;
        return detailCandidature(cand);
      }
      if (d === 'note-privee') {
        cand.notePrivee = typeof corps?.note === 'string' ? corps.note : (corps?.writtenStaffNotes as string | null) ?? null;
        return { writtenStaffNotes: cand.notePrivee };
      }
      if (d === 'messages') {
        cand.messages.push({ auteur: 'STAFF', nom: MOI_PSEUDO, texte: String(corps?.content ?? ''), ilYa: 0 });
        return { ok: true };
      }
      if (d === 'reset') return { ok: true, roleDemoted: false };
      if (d === 'oral') throw new Refus(409, 'Oral hors jeu : non simulé en mode démo.');
    }

    // Douane
    if (b === 'examinateurs') {
      const l: ExaminateurPropose[] = COMPTES.filter((x) =>
        ['WHITELIST_REVIEWER', 'MODERATOR', 'ADMIN', 'OWNER'].includes(roleDe(x)),
      ).map((x) => ({ ...compteDouane(x), minecraftUuid: x.uuid, role: roleDe(x) }));
      return l;
    }
    if (b === 'douane') {
      if (c === 'seances') {
        if (!d && methode === 'GET') {
          const depuis = p.get('depuis');
          return etat.seances
            .filter((s) => !depuis || s.startAt >= depuis)
            .sort((x, y) => x.startAt.localeCompare(y.startAt))
            .map(seanceStaff);
        }
        if (!d && methode === 'POST') {
          const s: SeanceDemo = {
            id: `s-n${etat.seances.length}`,
            startAt: String(corps?.startAt ?? new Date(Date.now() + 3 * J).toISOString()),
            endAt: String(corps?.endAt ?? new Date(Date.now() + 3 * J + 2 * H).toISOString()),
            capacite: Number(corps?.capacite ?? 16),
            statut: 'OUVERTE',
            examinateurs: [MOI_PSEUDO],
            notes: null,
            motifAnnulation: null,
            inscrits: 0,
          };
          etat.seances.push(s);
          return seanceDetail(s);
        }
        const s = trouver(etat.seances, (x) => x.id === d, 'Séance');
        if (!e && methode === 'GET') return seanceDetail(s);
        if (!e && methode === 'PATCH') {
          if (corps?.statut) s.statut = corps.statut as SeanceDemo['statut'];
          return seanceDetail(s);
        }
        if (e === 'cloture') {
          s.statut = 'TERMINEE';
          s.notes = typeof corps?.notes === 'string' ? corps.notes : s.notes;
          return seanceDetail(s);
        }
        if (e === 'annulation') {
          s.statut = 'ANNULEE';
          s.motifAnnulation = typeof corps?.motif === 'string' ? corps.motif : null;
          return seanceDetail(s);
        }
      }
      if (c === 'candidatures' && d) {
        return douaneCandidature(apresDouane(trouver(etat.candidatures, (x) => x.id === d, 'Candidature')));
      }
      if (c === 'reservations') return { ok: true };
    }
    if (b === 'oral-slots') {
      if (!c && methode === 'GET') {
        const l: OralSlot[] = CRENEAUX.map((o) => {
          const u = o.pseudo ? comptePar(o.pseudo) : null;
          return {
            id: o.id,
            startAt: o.startAt,
            durationMin: o.durationMin,
            status: o.status,
            bookedBy: u ? { id: u.id, minecraftUsername: u.pseudo, displayName: null } : null,
            bookedAt: o.bookedAt,
            notes: o.notes,
          };
        });
        return l;
      }
      return { created: 0, ok: true };
    }

    // Tickets
    if (b === 'tickets') {
      if (!c && methode === 'GET') return listeTickets(p);
      const t = trouver(etat.tickets, (x) => x.id === c, 'Ticket');
      if (!d && methode === 'GET') return detailTicket(t);
      if (!d && methode === 'PATCH') {
        if (corps?.status) t.status = corps.status as TicketStatus;
        t.majIlYa = 0;
        return detailTicket(t);
      }
      if (d === 'assign') {
        t.pris = methode === 'DELETE' ? null : MOI_PSEUDO;
        t.prisIlYa = methode === 'DELETE' ? null : 0;
        return { ok: true };
      }
      if (d === 'messages') {
        t.messages.push({ auteur: 'STAFF', nom: MOI_PSEUDO, texte: String(corps?.content ?? ''), ilYa: 0 });
        t.majIlYa = 0;
        return { ok: true };
      }
    }

    // Joueurs
    if (b === 'players') {
      exiger('joueurs.voir');
      if (c === 'search') return chercherJoueurs(p);
      const j = compteParId(c ?? '');
      if (!j) throw new Refus(404, 'Joueur introuvable (mode démo).');
      if (!d) return detailJoueur(j);
      if (d === 'ip') {
        exiger('joueurs.ip');
        return { ip: null, pays: j.pays };
      }
      if (d === 'role' && methode === 'PATCH') {
        exiger('grades.changer');
        const motif = typeof corps?.motif === 'string' ? corps.motif.trim() : '';
        if (motif.length < 3) throw new Refus(400, 'Motif : 3 caractères au moins.');
        etat.roles.set(j.id, corps?.role as Role);
        return { ok: true };
      }
    }

    // Sécurité, journal, tableau
    if (b === 'anomalies') {
      exiger('securite.voir');
      if (!c) {
        const joueur = p.get('userId');
        const l: LoginAnomaly[] = etat.anomalies
          .filter((x) => p.get('unack') !== 'true' || !x.acknowledged)
          .map(({ pseudo, ...x }) => ({ ...x, userId: comptePar(pseudo).id }))
          .filter((x) => !joueur || x.userId === joueur);
        return l;
      }
      if (d === 'ip') {
        exiger('joueurs.ip');
        const x = trouver(etat.anomalies, (y) => y.id === c, 'Connexion suspecte');
        // Aucune IP dans la démo (même fictive) : un texte en tient lieu.
        return { ip: 'adresse de démonstration', pays: x.country };
      }
      if (d === 'ack') {
        const x = trouver(etat.anomalies, (y) => y.id === c, 'Alerte');
        x.acknowledged = true;
        return { ok: true };
      }
    }
    if (b === 'audit') {
      exiger('audit.voir');
      return journal(p);
    }
    if (b === 'dashboard') return tableau();
    if (b === 'discord' && c === 'messages') {
      if (methode === 'GET') {
        const canal = p.get('canal');
        return MESSAGES_DISCORD.filter((m) => !canal || m.canal === canal);
      }
      return MESSAGES_DISCORD[0];
    }
  }

  // Contenu
  if (a === 'wiki') {
    if (b === 'tags') return TAGS;
    if (b === 'ideas') {
      const statut = p.get('status');
      return IDEES.filter((i) => !statut || i.status === statut);
    }
    if (b === 'entries') {
      if (!c) {
        const q = (p.get('q') ?? p.get('search') ?? '').toLowerCase();
        return WIKI.filter((w) => !q || `${w.title} ${w.summary ?? ''}`.toLowerCase().includes(q)).map(
          ({ _count: _c, ...w }) => w,
        );
      }
      const w = trouver(WIKI, (x) => x.id === c, 'Article');
      if (d === 'revisions') {
        return [
          { id: `${w.id}-r1`, entryId: w.id, title: w.title, summary: w.summary, body: w.body, editedById: w.createdById, createdAt: w.updatedAt },
        ];
      }
      return w;
    }
  }
  if (a === 'files') {
    if (b === 'scopes') return scopes();
    if (b === 'list') return listerDossier(p.get('path') ?? '');
    if (b === 'read') return lireFichier(p.get('path') ?? '');
    if (b === 'reload-targets') {
      return [
        { key: 'nexo', label: 'Nexo' },
        { key: 'creator', label: 'Character creator (assets live)' },
        { key: 'magicspells', label: 'MagicSpells' },
        { key: 'emotes', label: 'Emotes' },
        { key: 'abilities', label: 'Techniques (ShinobiAbilities)' },
      ];
    }
    return { ok: true };
  }
  if (a === 'abilities') {
    if (!b && methode === 'GET') {
      const statut = p.get('status');
      return TECHNIQUES.filter((t) => !statut || t.status === statut);
    }
    if (b === 'deploy' && c === 'recapitulatif') {
      // Comme l'API : seules les techniques publiées partent ; « Doton : mur de
      // terre », déployée la dernière fois puis repassée en brouillon, est retirée.
      const dernier = T0 - 28 * H;
      const publiees = TECHNIQUES.filter((t) => t.status === 'PUBLISHED');
      const techniques = publiees.map((t) => ({
        id: t.id,
        slug: t.slug,
        name: t.name,
        category: t.category,
        updatedAt: t.updatedAt,
        modifiee: Date.parse(t.updatedAt) > dernier,
      }));
      const r: RecapitulatifDeploiement = {
        serveur: 'Reborn · Développement',
        dernierDeploiement: new Date(dernier).toISOString(),
        total: techniques.length,
        modifiees: techniques.filter((t) => t.modifiee).length,
        techniques,
        retirees: TECHNIQUES.filter((t) => t.slug === 'doton_mur_de_terre').map((t) => ({
          slug: t.slug,
          name: t.name,
          status: t.status,
        })),
      };
      return r;
    }
    if (b && methode === 'GET') {
      const t = trouver(TECHNIQUES, (x) => x.id === b, 'Technique');
      const full: TechniqueFull = {
        ...t,
        createdAt: avant(20 * J),
        graph: { id: t.slug, name: t.name, category: t.category, rank: 'C', description: '', nodes: [], edges: [] },
      };
      return full;
    }
    return { ok: true, warnings: [], queued: 'demo' };
  }

  if (methode === 'GET') throw new Refus(404, `Mode démo : ${chemin} n’est pas simulé.`);
  return { ok: true };
}

// ── Gestes scriptés (captures sans fenêtre) ───────────────────────────

/**
 * `?gestes=touche:e` ou `?gestes=touche:ctrl+k|saisie:ita` ou
 * `?gestes=clic:Changer de grade` : rejoue des gestes après le premier rendu,
 * pour photographier un tiroir, une fenêtre ou la palette avec Edge sans
 * fenêtre (--headless --screenshot ne sait pas cliquer). Un geste toutes les
 * 700 ms.
 */
function rejouerGestes() {
  if (typeof window === 'undefined') return;
  const brut = new URLSearchParams(window.location.search).get('gestes');
  if (!brut) return;
  const gestes = brut.split('|').filter(Boolean);
  gestes.forEach((g, i) => {
    window.setTimeout(() => {
      const [genre, ...reste] = g.split(':');
      const valeur = reste.join(':');
      const cible = (document.activeElement as HTMLElement | null) ?? document.body;
      if (genre === 'touche') {
        // « + » arrive en espace dans une adresse (URLSearchParams) : les deux séparent.
        const morceaux = valeur.split(/[+ ]/);
        const key = morceaux[morceaux.length - 1]!;
        cible.dispatchEvent(
          new KeyboardEvent('keydown', {
            key,
            ctrlKey: morceaux.includes('ctrl'),
            shiftKey: morceaux.includes('maj'),
            bubbles: true,
            cancelable: true,
          }),
        );
      } else if (genre === 'saisie' && (cible instanceof HTMLInputElement || cible instanceof HTMLTextAreaElement)) {
        // Valeur posée par le setter natif : React voit un vrai changement.
        const proto = cible instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
        Object.getOwnPropertyDescriptor(proto, 'value')?.set?.call(cible, valeur);
        cible.dispatchEvent(new Event('input', { bubbles: true }));
      } else if (genre === 'clic') {
        const el = [...document.querySelectorAll<HTMLElement>('button, a, [role="tab"], [role="menuitem"]')].find(
          (x) => x.textContent?.trim().startsWith(valeur),
        );
        el?.click();
      }
    }, 1_500 + i * 700);
  });
}

rejouerGestes();

/**
 * Réponse simulée d'une requête du panel, au même format qu'une réponse de
 * l'API (JSON, statut HTTP, message d'erreur en français).
 */
export async function reponseDemo(
  path: string,
  opts: { method: string; body?: unknown },
): Promise<Response> {
  const url = new URL(path, 'http://demo.local');
  const corps =
    opts.body && typeof opts.body === 'object' && !(opts.body instanceof FormData)
      ? (opts.body as Record<string, unknown>)
      : undefined;
  try {
    const r = router(opts.method, url.pathname, url.searchParams, corps);
    return r instanceof Response ? r : json(r);
  } catch (e) {
    if (e instanceof Refus) return json({ statusCode: e.status, message: e.message }, e.status);
    return json({ statusCode: 500, message: e instanceof Error ? e.message : 'Erreur du mode démo.' }, 500);
  }
}
