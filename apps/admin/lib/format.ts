/**
 * Formats d'affichage communs (PANEL_V2 §5.5) : durées d'attente écrites
 * d'une seule façon (« 2 j 6 h », jamais « 26 h » à côté de « 1 j 20 h »),
 * dates relatives avec la date exacte en info-bulle, heure de Paris, nombres
 * avec espace fine.
 */

import { FUSEAU, dateHeure, ilYa } from './candidature';

export { dateHeure, ilYa };

const MIN = 60_000;
const HEURE = 60 * MIN;
const JOUR = 24 * HEURE;

/**
 * Durée d'attente : « 40 min », « 9 h », « 1 j 20 h », « 3 j ». Au-delà d'un
 * jour, les heures suivent toujours les jours, pour qu'un tri à l'œil reste juste.
 */
export function duree(ms: number): string {
  const t = Math.max(0, ms);
  if (t < MIN) return 'moins d’1 min';
  if (t < HEURE) return `${Math.floor(t / MIN)} min`;
  if (t < JOUR) return `${Math.floor(t / HEURE)} h`;
  const j = Math.floor(t / JOUR);
  const h = Math.floor((t % JOUR) / HEURE);
  return h > 0 ? `${j} j ${h} h` : `${j} j`;
}

/** Attente depuis un instant. */
export function attenteDepuis(iso: string): string {
  return duree(Date.now() - new Date(iso).getTime());
}

/** Millisecondes écoulées depuis un instant. */
export function ecouleDepuis(iso: string): number {
  return Date.now() - new Date(iso).getTime();
}

const FORMAT_JOUR = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  day: 'numeric',
  month: 'long',
});

const FORMAT_JOUR_AN = new Intl.DateTimeFormat('fr-FR', {
  timeZone: FUSEAU,
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** « 12 janvier » (avec l'année si elle n'est pas l'année en cours). */
export function dateJour(iso: string): string {
  const d = new Date(iso);
  return d.getFullYear() === new Date().getFullYear()
    ? FORMAT_JOUR.format(d)
    : FORMAT_JOUR_AN.format(d);
}

const NF = new Intl.NumberFormat('fr-FR');

/** 1 234 (espace fine insécable). */
export function nombre(n: number): string {
  return NF.format(n);
}

/** « 3 tickets », « 1 ticket ». */
export function pluriel(n: number, singulier: string, plurielForme?: string): string {
  return `${nombre(n)} ${n > 1 ? (plurielForme ?? `${singulier}s`) : singulier}`;
}

/** UUID raccourci pour l'affichage (« 3f2a…9c41 »). */
export function uuidCourt(uuid: string): string {
  const u = uuid.replace(/-/g, '');
  return u.length > 10 ? `${u.slice(0, 4)}…${u.slice(-4)}` : u;
}

/** Temps de jeu : 38 → « 38 min » ; 2052 → « 34 h 12 » ; 720 → « 12 h » (comme le launcher). */
export function tempsDeJeu(minutes: number): string {
  const total = Math.max(0, Math.floor(minutes));
  if (total < 60) return `${total} min`;
  const h = Math.floor(total / 60);
  const m = total % 60;
  const heures = h.toLocaleString('fr-FR');
  return m === 0 ? `${heures} h` : `${heures} h ${String(m).padStart(2, '0')}`;
}
