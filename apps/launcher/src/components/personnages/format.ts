// « Mes personnages » : dates, durées et textes en français, à l'heure
// locale du PC (l'API envoie de l'ISO 8601 UTC). Typographie de la maquette
// L11 : « 34 h 12 », « hier, 23 h 14 », « 25/10/2026 ».

import { dateDe, heure } from "../whitelist/format";

export { dateDe };

const DATE_JOUR = new Intl.DateTimeFormat("fr-FR", { day: "2-digit", month: "2-digit", year: "numeric" });

/** « 25/10/2026 ». */
export const dateJour = (d: Date) => DATE_JOUR.format(d);

/** Vrai si les deux dates tombent le même jour (heure locale). */
export const memeJour = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

/** Temps de jeu : 38 → « 38 min » ; 2052 → « 34 h 12 » ; 720 → « 12 h ». */
export function tempsDeJeu(minutes: number): string {
  const total = Math.max(0, Math.floor(minutes));
  if (total < 60) return `${total} min`;
  const h = Math.floor(total / 60);
  const m = total % 60;
  const heures = h.toLocaleString("fr-FR");
  return m === 0 ? `${heures} h` : `${heures} h ${String(m).padStart(2, "0")}`;
}

/** Dernière partie : « aujourd'hui, 14 h 02 », « hier, 23 h 14 », sinon « 21/12/2026 ». */
export function dernierePartie(d: Date, now = new Date()): string {
  if (memeJour(d, now)) return `aujourd'hui, ${heure(d)}`;
  const hier = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
  if (memeJour(d, hier)) return `hier, ${heure(d)}`;
  return dateJour(d);
}

/**
 * Texte du dossier sans la syntaxe Markdown du formulaire, comme dans la
 * maquette L11 : le dossier se lit en texte simple, replié à trois lignes.
 * Les titres de section sont retirés (ils mangeraient les lignes visibles) ;
 * gras, italique, barré et citations perdent leurs marques, les puces
 * deviennent « · ». Rendu par React en texte, jamais en HTML.
 */
export function texteBrut(md: string): string {
  return md
    .replace(/\r\n?/g, "\n")
    .split("\n")
    .filter((ligne) => !/^\s{0,3}#{1,6}\s/.test(ligne))
    .map((ligne) =>
      ligne
        .replace(/^\s{0,3}>\s?/, "")
        .replace(/^\s{0,3}[-*+]\s+/, "· ")
        .replace(/\[([^\]]+)\]\([^)]*\)/g, "$1")
        .replace(/\*\*|__|~~|\*/g, ""),
    )
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
