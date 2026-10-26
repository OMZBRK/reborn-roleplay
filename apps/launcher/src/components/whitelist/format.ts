// Dates et durées en français, à l'heure locale du PC (l'API envoie de
// l'ISO 8601 UTC). Typographie des maquettes : « 21 h », « 21 h 30 ».

const JOUR_LONG = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" });
const JOUR_SEMAINE = new Intl.DateTimeFormat("fr-FR", { weekday: "short" });
const DATE_COURTE = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "2-digit" });

export const MIN_MS = 60_000;
export const HEURE_MS = 60 * MIN_MS;
export const JOUR_MS = 24 * HEURE_MS;

/** Date ISO lisible, ou null. */
export function dateDe(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** « 21 h » / « 21 h 30 ». */
export function heure(d: Date): string {
  const h = d.getHours();
  const m = d.getMinutes();
  return m === 0 ? `${h} h` : `${h} h ${String(m).padStart(2, "0")}`;
}

/** « dimanche 25 octobre ». */
export const jourLong = (d: Date) => JOUR_LONG.format(d);

/** « dim » */
export const jourSemaine = (d: Date) => JOUR_SEMAINE.format(d).replace(".", "");

/** « 25/10 » */
export const dateCourte = (d: Date) => DATE_COURTE.format(d);

/** « dimanche 25 octobre à 21 h 30 ». */
export const quand = (d: Date) => `${jourLong(d)} à ${heure(d)}`;

export const majuscule = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

/** Clé de jour local (regroupement des séances). */
export const cleJour = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;

/** Vrai si `d` tombe aujourd'hui (heure locale). */
export const aujourdhui = (d: Date, now = new Date()) => cleJour(d) === cleJour(now);

/** « ce soir », « aujourd'hui », « demain », sinon le jour (« dimanche »). */
export function jourRelatif(d: Date, now = new Date()): string {
  if (aujourdhui(d, now)) return d.getHours() >= 18 ? "ce soir" : "aujourd'hui";
  const demain = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  if (cleJour(d) === cleJour(demain)) return "demain";
  return JOUR_LONG.format(d).split(" ")[0];
}

/** Durée lisible : « 38 min », « 2 h 32 », « 3 j 4 h ». Arrondi à la minute supérieure. */
export function duree(ms: number): string {
  let min = Math.max(0, Math.ceil(ms / MIN_MS));
  const j = Math.floor(min / 1440);
  min -= j * 1440;
  const h = Math.floor(min / 60);
  min -= h * 60;
  if (j > 0) return h > 0 ? `${j} j ${h} h` : `${j} j`;
  if (h > 0) return min > 0 ? `${h} h ${String(min).padStart(2, "0")}` : `${h} h`;
  return `${min} min`;
}

/** « dans 1 j 2 h », « dans 22 h 35 », « dans 5 min » ; « maintenant » une fois passé. */
export function compteARebours(cible: Date, now = new Date()): string {
  const ms = cible.getTime() - now.getTime();
  return ms <= 0 ? "maintenant" : `dans ${duree(ms)}`;
}

/** « 0:42 ». */
export const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
