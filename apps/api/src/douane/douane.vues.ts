import {
  AppStatus,
  DouaneKind,
  DouaneReservationStatut,
  DouaneSeanceStatut,
  OralVia,
  Role,
} from '@prisma/client';
import {
  attribuerConvocation,
  capaciteConseillee,
  etatFenetre,
  fenetreEntree,
  type EtatFenetre,
  type PhaseCandidature,
  vaguesConvocation,
} from './douane.regles';

/**
 * Formes de réponse des routes de la douane (contrat HTTP). Les dates sont
 * en ISO 8601 UTC ; le launcher et le panel les affichent à l'heure locale.
 * Rien de privé (notes du staff, notes de l'examinateur) dans les vues du
 * candidat.
 */

export interface FenetreVue {
  ouvreAt: string;
  fermeAt: string;
  etat: EtatFenetre;
}

export function fenetreVue(
  convocationAt: Date,
  maintenant: Date,
  retardMaxMin: number,
): FenetreVue {
  const f = fenetreEntree(convocationAt, retardMaxMin);
  return {
    ouvreAt: f.ouvreAt.toISOString(),
    fermeAt: f.fermeAt.toISOString(),
    etat: etatFenetre(convocationAt, maintenant, retardMaxMin),
  };
}

// ──────────────────────────── Candidat ────────────────────────────

export interface SeanceCandidatVue {
  id: string;
  startAt: string;
  endAt: string;
  placesTotal: number;
  placesRestantes: number;
  complet: boolean;
  /** Nombre seulement : le candidat ne voit pas qui examine. */
  nbExaminateurs: number;
  /** Heures de convocation de la séance (une seule si elle dure 1 h au plus). */
  vagues: string[];
  /** Heure qu'obtiendrait une réservation maintenant ; l'heure définitive est fixée à la réservation. */
  convocationIndicative: string | null;
}

export interface ReservationCandidatVue {
  id: string;
  seanceId: string;
  statut: DouaneReservationStatut;
  convocationAt: string;
  fenetre: FenetreVue;
  seance: { startAt: string; endAt: string };
  createdAt: string;
}

/**
 * Dernière place perdue depuis la dernière décision (maquette L10, « séance
 * manquée ») : manquée, ou annulée (par le staff avec son motif, par une
 * séance annulée, par le candidat lui-même).
 */
export interface DernierePlaceVue {
  statut: 'MISSED' | 'CANCELLED';
  convocationAt: string;
  /** Fin de la fenêtre d'entrée (« entrée fermée à 22 h »). */
  fermeAt: string;
  motifAnnulation: string | null;
}

export interface EtatDouaneCandidatVue {
  phase: PhaseCandidature;
  /** Peut prendre une place (DOUANE_A_RESERVER, DOUANE_A_REVOIR). */
  peutReserver: boolean;
  /** A une place et peut en changer (DOUANE_RESERVEE) : POST avec remplacer=true. */
  peutChanger: boolean;
  /** Texte à afficher quand aucune des deux actions n'est possible. */
  raison: string | null;
  /** Après un oral « à revoir » : première date de séance permise (lendemain, heure de Paris). */
  prochaineSeanceDes: string | null;
  reservation: ReservationCandidatVue | null;
  /** Sans place en cours : la dernière place manquée ou annulée depuis la dernière décision, sinon null. */
  derniereReservation: DernierePlaceVue | null;
  seances: SeanceCandidatVue[];
  /**
   * `douaneEnJeu` : REBORN_DOUANE_ENABLED. false pendant la transition (§3.11) :
   * l'oral se passe hors jeu, le launcher n'affiche ni « Passer la douane » ni
   * les textes « en jeu ».
   */
  regles: {
    avanceEntreeMin: number;
    retardMaxMin: number;
    douaneEnJeu: boolean;
  };
}

export function seanceCandidatVue(
  s: { id: string; startAt: Date; endAt: Date; capacite: number },
  nbExaminateurs: number,
  prises: Date[],
  maintenant: Date,
  retardMaxMin: number,
): SeanceCandidatVue {
  const restantes = Math.max(0, s.capacite - prises.length);
  const indicative =
    restantes > 0
      ? attribuerConvocation(s, prises, maintenant, retardMaxMin)
      : null;
  return {
    id: s.id,
    startAt: s.startAt.toISOString(),
    endAt: s.endAt.toISOString(),
    placesTotal: s.capacite,
    placesRestantes: restantes,
    complet: restantes === 0,
    nbExaminateurs,
    vagues: vaguesConvocation(s.startAt, s.endAt).map((v) => v.toISOString()),
    convocationIndicative: indicative?.toISOString() ?? null,
  };
}

export function reservationCandidatVue(
  r: {
    id: string;
    seanceId: string;
    statut: DouaneReservationStatut;
    convocationAt: Date;
    createdAt: Date;
    seance: { startAt: Date; endAt: Date };
  },
  maintenant: Date,
  retardMaxMin: number,
): ReservationCandidatVue {
  return {
    id: r.id,
    seanceId: r.seanceId,
    statut: r.statut,
    convocationAt: r.convocationAt.toISOString(),
    fenetre: fenetreVue(r.convocationAt, maintenant, retardMaxMin),
    seance: {
      startAt: r.seance.startAt.toISOString(),
      endAt: r.seance.endAt.toISOString(),
    },
    createdAt: r.createdAt.toISOString(),
  };
}

// ──────────────────────────── Staff ────────────────────────────

export interface CompteVue {
  id: string;
  minecraftUsername: string;
  discordUsername: string | null;
}

export interface ExaminateurVue extends CompteVue {
  role: Role;
}

export interface SeanceStaffVue {
  id: string;
  kind: DouaneKind;
  startAt: string;
  endAt: string;
  capacite: number;
  /** Repère Q5 : examinateurs × heures × 4 ; null sans examinateur. */
  capaciteConseillee: number | null;
  statut: DouaneSeanceStatut;
  /** Places prises = réservations BOOKED + DONE. */
  placesPrises: number;
  placesRestantes: number;
  compte: Record<DouaneReservationStatut, number>;
  vagues: string[];
  examinateurs: ExaminateurVue[];
  openedBy: CompteVue | null;
  notes: string | null;
  clotureeAt: string | null;
  annuleeAt: string | null;
  motifAnnulation: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReservationStaffVue {
  id: string;
  seanceId: string;
  statut: DouaneReservationStatut;
  convocationAt: string;
  fenetre: FenetreVue;
  presentAt: string | null;
  calledAt: string | null;
  departAt: string | null;
  resultat: AppStatus | null;
  decidedAt: string | null;
  examinateur: CompteVue | null;
  rappelEnvoyeAt: string | null;
  annuleeAt: string | null;
  motifAnnulation: string | null;
  createdAt: string;
  candidat: CompteVue & { userId: string; seancesManquees: number };
  candidature: {
    id: string;
    firstName: string;
    lastName: string;
    village: string;
    oralStatus: AppStatus;
    /** Oral déjà passé une fois et « à revoir » : marque « 2ᵉ passage » (§3.8). */
    secondPassage: boolean;
  };
}

export interface SeanceStaffDetailVue extends SeanceStaffVue {
  reservations: ReservationStaffVue[];
}

export interface OralDecisionVue {
  applicationId: string;
  userId: string;
  resultat: AppStatus;
  status: AppStatus;
  oralStatus: AppStatus;
  oralVia: OralVia | null;
  oralDecidedAt: string | null;
  phase: PhaseCandidature;
  /** À revoir : première date de séance permise. */
  prochaineSeanceDes: string | null;
  /** Refusé : nouvelle candidature complète possible à partir de… */
  canReapplyAt: string | null;
  reservation: {
    id: string;
    seanceId: string;
    statut: DouaneReservationStatut;
    resultat: AppStatus | null;
  } | null;
}

type CompteBrut = {
  id: string;
  minecraftUsername: string;
  discordUsername: string | null;
};

export function compteVue(u: CompteBrut | null | undefined): CompteVue | null {
  if (!u) return null;
  return {
    id: u.id,
    minecraftUsername: u.minecraftUsername,
    discordUsername: u.discordUsername,
  };
}

export function compteParStatut(
  statuts: DouaneReservationStatut[],
): Record<DouaneReservationStatut, number> {
  const c: Record<DouaneReservationStatut, number> = {
    BOOKED: 0,
    DONE: 0,
    MISSED: 0,
    CANCELLED: 0,
  };
  for (const s of statuts) c[s] += 1;
  return c;
}

export function seanceStaffVue(
  s: {
    id: string;
    kind: DouaneKind;
    startAt: Date;
    endAt: Date;
    capacite: number;
    statut: DouaneSeanceStatut;
    notes: string | null;
    clotureeAt: Date | null;
    annuleeAt: Date | null;
    motifAnnulation: string | null;
    createdAt: Date;
    updatedAt: Date;
    examinateurs: (CompteBrut & { role: Role })[];
    openedBy?: CompteBrut | null;
  },
  statutsReservations: DouaneReservationStatut[],
): SeanceStaffVue {
  const compte = compteParStatut(statutsReservations);
  const prises = compte.BOOKED + compte.DONE;
  return {
    id: s.id,
    kind: s.kind,
    startAt: s.startAt.toISOString(),
    endAt: s.endAt.toISOString(),
    capacite: s.capacite,
    capaciteConseillee: capaciteConseillee(
      s.startAt,
      s.endAt,
      s.examinateurs.length,
    ),
    statut: s.statut,
    placesPrises: prises,
    placesRestantes: Math.max(0, s.capacite - prises),
    compte,
    vagues: vaguesConvocation(s.startAt, s.endAt).map((v) => v.toISOString()),
    examinateurs: s.examinateurs.map((e) => ({
      id: e.id,
      minecraftUsername: e.minecraftUsername,
      discordUsername: e.discordUsername,
      role: e.role,
    })),
    openedBy: compteVue(s.openedBy),
    notes: s.notes,
    clotureeAt: s.clotureeAt?.toISOString() ?? null,
    annuleeAt: s.annuleeAt?.toISOString() ?? null,
    motifAnnulation: s.motifAnnulation,
    createdAt: s.createdAt.toISOString(),
    updatedAt: s.updatedAt.toISOString(),
  };
}

export function reservationStaffVue(
  r: {
    id: string;
    seanceId: string;
    statut: DouaneReservationStatut;
    convocationAt: Date;
    presentAt: Date | null;
    calledAt: Date | null;
    departAt: Date | null;
    resultat: AppStatus | null;
    decidedAt: Date | null;
    rappelEnvoyeAt: Date | null;
    annuleeAt: Date | null;
    motifAnnulation: string | null;
    createdAt: Date;
    user: CompteBrut;
    application: {
      id: string;
      firstName: string;
      lastName: string;
      village: string;
      oralStatus: AppStatus;
    };
    examinateur: CompteBrut | null;
  },
  seancesManquees: number,
  maintenant: Date,
  retardMaxMin: number,
): ReservationStaffVue {
  return {
    id: r.id,
    seanceId: r.seanceId,
    statut: r.statut,
    convocationAt: r.convocationAt.toISOString(),
    fenetre: fenetreVue(r.convocationAt, maintenant, retardMaxMin),
    presentAt: r.presentAt?.toISOString() ?? null,
    calledAt: r.calledAt?.toISOString() ?? null,
    departAt: r.departAt?.toISOString() ?? null,
    resultat: r.resultat,
    decidedAt: r.decidedAt?.toISOString() ?? null,
    examinateur: compteVue(r.examinateur),
    rappelEnvoyeAt: r.rappelEnvoyeAt?.toISOString() ?? null,
    annuleeAt: r.annuleeAt?.toISOString() ?? null,
    motifAnnulation: r.motifAnnulation,
    createdAt: r.createdAt.toISOString(),
    candidat: {
      userId: r.user.id,
      id: r.user.id,
      minecraftUsername: r.user.minecraftUsername,
      discordUsername: r.user.discordUsername,
      seancesManquees,
    },
    candidature: {
      id: r.application.id,
      firstName: r.application.firstName,
      lastName: r.application.lastName,
      village: r.application.village,
      oralStatus: r.application.oralStatus,
      secondPassage: r.application.oralStatus === AppStatus.NEEDS_REVISION,
    },
  };
}
