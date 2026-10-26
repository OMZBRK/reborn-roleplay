import { AppStatus } from '@prisma/client';
import type { WhitelistApplication } from '@prisma/client';
import type { PhaseCandidature } from '../whitelist/whitelist.rules';
import { LIBELLES, ageDepuisNaissance } from '../whitelist/whitelist.rules';
import type { CandidatureView } from './game.service';
import type { PorteeJeu, RaisonAcces } from './game-acces.regles';
import { fenetreIso } from './game-acces.regles';

/**
 * Réponses des routes signées du serveur de jeu (contrat HTTP lu par
 * ShinobiCore). Dates en ISO 8601 UTC. Textes en français, prêts à afficher.
 */

// ──────────────────────────── GET /game/acces/:mcUuid ────────────────────────────

export interface AccesJeuVue {
  /** `play` : sélection ; `douane` : hall ; `aucune` : expulsion avec `message`. */
  portee: PorteeJeu;
  raison: RaisonAcces;
  message: string | null;
  phase: PhaseCandidature | null;
  staff: boolean;
  /** Pseudo Minecraft connu de l'API ; null sans compte. */
  pseudo: string | null;
  /** REBORN_DOUANE_ENABLED. */
  douaneActive: boolean;
  /** Bloc du roster (wizard de création), comme GET /game/candidature/:mcUuid. */
  candidature: CandidatureView;
  /** Portée `douane` seulement : la place en cours, pour la file du hall. */
  douane: AccesDouaneVue | null;
}

export interface AccesDouaneVue {
  reservationId: string;
  seanceId: string;
  convocationAt: string;
  entreeOuvreAt: string;
  entreeFermeAt: string;
  /** Arrivée déjà enregistrée (POST /game/douane/presence). */
  arriveeAt: string | null;
  /** Oral déjà passé une fois et « à revoir » : marque « 2ᵉ passage » (§3.8). */
  deuxiemePassage: boolean;
  /** Village demandé (valeur exacte du mod). */
  village: string;
}

// ──────────────────────────── GET /game/douane/dossier/:mcUuid ────────────────────────────

/** Nombre de lignes et de caractères gardés par extrait (§3.7 : les trois premières lignes). */
export const EXTRAIT_LIGNES = 3;
export const EXTRAIT_CARACTERES_MAX = 400;
/** La note privée de l'écrit est coupée au-delà (le dossier tient en quelques Ko). */
export const NOTE_ECRIT_CARACTERES_MAX = 2000;

export interface ExtraitVue {
  champ: string;
  titre: string;
  texte: string;
  /** true si le texte complet est plus long : il se lit dans le panel. */
  tronque: boolean;
  /** Longueur du texte complet. */
  caracteres: number;
}

export interface DossierDouaneVue {
  applicationId: string;
  formVersion: number;
  candidat: {
    mcUuid: string;
    pseudo: string;
    ageDeclare: number | null;
    anneesRp: string | null;
    connaissanceNaruto: string | null;
  };
  personnage: {
    prenom: string;
    /** v2 : nom de famille libre. v1 : le champ portait souvent un clan. */
    nomFamille: string;
    sexe: string | null;
    age: number | null;
    village: string;
    type: string | null;
  };
  extraits: ExtraitVue[];
  /** Note PRIVÉE du staff sur l'écrit : les points à creuser. Jamais montrée au candidat. */
  noteEcrit: { texte: string; tronquee: boolean } | null;
  /** Jamais le fichier : la voix s'écoute dans le panel. */
  voix: { presente: boolean; dureeSec: number | null; consigne: string };
  passage: {
    reservationId: string;
    seanceId: string;
    convocationAt: string;
    entreeOuvreAt: string;
    entreeFermeAt: string;
    arriveeAt: string | null;
    appelAt: string | null;
    deuxiemePassage: boolean;
    /** 2ᵉ passage : le motif de l'oral « à revoir » (déjà visible du candidat). */
    motifPrecedent: string | null;
  };
  /** Fiche complète dans le panel (« Ouvrir dans le panel ») ; null si ADMIN_BASE_URL manque. */
  lienPanel: string | null;
}

/** Les `lignes` premières lignes non vides, coupées à `max` caractères. */
export function extraire(
  texte: string,
  lignes = EXTRAIT_LIGNES,
  max = EXTRAIT_CARACTERES_MAX,
): { texte: string; tronque: boolean } {
  const toutes = texte
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
  let court = toutes.slice(0, lignes).join('\n');
  let tronque = toutes.length > lignes;
  if (court.length > max) {
    court = `${court.slice(0, max - 1).trimEnd()}…`;
    tronque = true;
  }
  return { texte: court, tronque };
}

/** Textes longs résumés, dans l'ordre de la fiche ; les champs vides sont omis. */
const TEXTES_LONGS: ReadonlyArray<{
  champ: keyof Pick<
    WhitelistApplication,
    | 'history'
    | 'objectives'
    | 'availability'
    | 'motivation'
    | 'experience'
    | 'appearance'
  >;
  titre: string;
}> = [
  { champ: 'history', titre: 'Histoire' },
  { champ: 'objectives', titre: 'Objectifs roleplay' },
  { champ: 'availability', titre: 'Disponibilités' },
  // Ancien formulaire seulement (retirés de la v2, complément du 24/10).
  { champ: 'motivation', titre: 'Pourquoi Reborn ?' },
  { champ: 'experience', titre: 'Expérience du roleplay' },
  { champ: 'appearance', titre: 'Caractère et apparence' },
];

export const CONSIGNE_VOIX = 'Voix : à réécouter dans le panel';

export function dossierDouaneVue(
  app: WhitelistApplication,
  candidat: { minecraftUuid: string; minecraftUsername: string },
  place: {
    id: string;
    seanceId: string;
    convocationAt: Date;
    presentAt: Date | null;
    calledAt: Date | null;
  },
  options: {
    maintenant: Date;
    retardMaxMin: number;
    adminBaseUrl: string | null;
  },
): DossierDouaneVue {
  const ageDeclare =
    app.playerAge ??
    (app.dob
      ? ageDepuisNaissance(
          app.dob.toISOString().slice(0, 10),
          options.maintenant,
        )
      : null);

  const extraits: ExtraitVue[] = [];
  for (const { champ, titre } of TEXTES_LONGS) {
    const brut = app[champ];
    if (typeof brut !== 'string' || brut.trim().length === 0) continue;
    const e = extraire(brut);
    extraits.push({ champ, titre, ...e, caracteres: brut.length });
  }

  const note = app.writtenStaffNotes?.trim() || null;
  const deuxiemePassage = app.oralStatus === AppStatus.NEEDS_REVISION;

  return {
    applicationId: app.id,
    formVersion: app.formVersion,
    candidat: {
      mcUuid: candidat.minecraftUuid,
      pseudo: candidat.minecraftUsername,
      ageDeclare,
      anneesRp: app.rpYears ? LIBELLES.rpYears[app.rpYears] : null,
      connaissanceNaruto: app.narutoKnowledge
        ? LIBELLES.narutoKnowledge[app.narutoKnowledge]
        : null,
    },
    personnage: {
      prenom: app.firstName,
      nomFamille: app.lastName,
      sexe: app.characterSex ? LIBELLES.characterSex[app.characterSex] : null,
      age: app.characterAge,
      village: app.village,
      type: app.characterType
        ? LIBELLES.characterType[app.characterType]
        : null,
    },
    extraits,
    noteEcrit: note
      ? note.length > NOTE_ECRIT_CARACTERES_MAX
        ? {
            texte: `${note.slice(0, NOTE_ECRIT_CARACTERES_MAX - 1).trimEnd()}…`,
            tronquee: true,
          }
        : { texte: note, tronquee: false }
      : null,
    voix: {
      presente: !!app.voiceKey && !app.voiceDeletedAt,
      dureeSec: app.voiceDurationSec,
      consigne: CONSIGNE_VOIX,
    },
    passage: {
      reservationId: place.id,
      seanceId: place.seanceId,
      convocationAt: place.convocationAt.toISOString(),
      ...fenetreIso(place.convocationAt, options.retardMaxMin),
      arriveeAt: place.presentAt?.toISOString() ?? null,
      appelAt: place.calledAt?.toISOString() ?? null,
      deuxiemePassage,
      motifPrecedent: deuxiemePassage ? app.oralReason : null,
    },
    lienPanel: options.adminBaseUrl
      ? `${options.adminBaseUrl}/whitelist/${app.id}`
      : null,
  };
}

// ──────────────────────────── POST /game/douane/decision et /presence ────────────────────────────

export interface DecisionDouaneJeuVue {
  ok: true;
  /** true : cette décision était déjà enregistrée (nouvelle tentative après une réponse perdue). */
  dejaTraitee: boolean;
  resultat: AppStatus;
  /** Phase du candidat après la décision (ACCEPTE, DOUANE_A_REVOIR, REFUSE). */
  phase: PhaseCandidature;
  applicationId: string;
  reservationId: string;
  /** À revoir : nouvelle séance réservable à partir de… */
  prochaineSeanceDes: string | null;
  /** Refusé : nouvelle candidature complète possible à partir de… */
  canReapplyAt: string | null;
}

export interface PresenceDouaneJeuVue {
  ok: true;
  reservationId: string;
  presentAt: string | null;
  calledAt: string | null;
  departAt: string | null;
}
