/**
 * Règles de la douane utiles à l'aperçu du formulaire « Ouvrir une séance »
 * (maquette P3). Copie des règles pures de l'API
 * (apps/api/src/douane/douane.regles.ts) : l'API reste l'autorité, elle
 * recalcule les vagues et refuse ce qui sort des bornes. Heures saisies et
 * affichées en heure de Paris (lib/candidature.ts › FUSEAU).
 */

import { instantParis, partiesParis } from './candidature';

/** Durée d'une séance : de 15 min à 8 h. */
export const DUREE_SEANCE_BORNES_MIN = { min: 15, max: 480 } as const;
/** Capacité d'une séance. */
export const CAPACITE_BORNES = { min: 1, max: 100 } as const;
/** Jusqu'à cette durée, toute la séance est convoquée à son début. */
export const SEANCE_COURTE_MAX_MIN = 60;
/** Pas des vagues de convocation d'une séance longue. */
export const VAGUE_MIN = 30;
/** Repère de dimensionnement (Q5) : 4 candidats par heure et par examinateur. */
export const CANDIDATS_PAR_HEURE_PAR_EXAMINATEUR = 4;
/** Entrée en jeu : de H − 15 min à H + 30 min (retard maximal réglable côté API). */
export const AVANCE_ENTREE_MIN = 15;

const MIN = 60_000;

/** Heures de convocation d'une séance (même calcul que l'API). */
export function vaguesConvocation(startAt: Date, endAt: Date): Date[] {
  const debut = startAt.getTime();
  const dureeMin = (endAt.getTime() - debut) / MIN;
  if (dureeMin <= SEANCE_COURTE_MAX_MIN) return [new Date(debut)];
  const vagues: Date[] = [];
  for (let t = 0; t < dureeMin - VAGUE_MIN; t += VAGUE_MIN) {
    vagues.push(new Date(debut + t * MIN));
  }
  return vagues;
}

/** Capacité conseillée : examinateurs × heures × 4 ; null sans examinateur. */
export function capaciteConseillee(
  startAt: Date,
  endAt: Date,
  nbExaminateurs: number,
): number | null {
  if (nbExaminateurs <= 0) return null;
  const heures = (endAt.getTime() - startAt.getTime()) / (60 * MIN);
  return Math.max(
    1,
    Math.round(nbExaminateurs * heures * CANDIDATS_PAR_HEURE_PAR_EXAMINATEUR),
  );
}

/** Répartition indicative des places par vague (comme l'API : part égale, arrondie au-dessus). */
export function placesParVague(capacite: number, nbVagues: number): number {
  return Math.max(1, Math.ceil(capacite / Math.max(1, nbVagues)));
}

/** « 2026-10-31 » : date de Paris d'un instant (valeur d'un <input type="date">). */
export function valeurDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  const { an, mois, jour } = partiesParis(d);
  return `${an}-${p(mois)}-${p(jour)}`;
}

/**
 * Instants d'une séance à partir d'un jour (AAAA-MM-JJ) et d'heures (HH:MM)
 * lus en heure de Paris, comme l'API et les messages envoyés aux candidats,
 * quel que soit le fuseau du navigateur. Une heure de fin plus petite que le
 * début passe au lendemain (séance après minuit).
 */
export function horaireSeance(
  jour: string,
  debut: string,
  fin: string,
): { startAt: Date; endAt: Date } | null {
  const j = /^(\d{4})-(\d{2})-(\d{2})$/.exec(jour);
  const d = /^(\d{2}):(\d{2})/.exec(debut);
  const f = /^(\d{2}):(\d{2})/.exec(fin);
  if (!j || !d || !f) return null;
  const [an, mois, quantieme] = [Number(j[1]), Number(j[2]), Number(j[3])];
  const [hd, md, hf, mf] = [Number(d[1]), Number(d[2]), Number(f[1]), Number(f[2])];
  if (mois < 1 || mois > 12 || quantieme < 1 || quantieme > 31) return null;
  if (hd > 23 || md > 59 || hf > 23 || mf > 59) return null;
  const finLendemain = hf * 60 + mf <= hd * 60 + md;
  const startAt = instantParis(an, mois, quantieme, hd, md);
  const endAt = instantParis(an, mois, quantieme + (finLendemain ? 1 : 0), hf, mf);
  if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime())) {
    return null;
  }
  return { startAt, endAt };
}
