import { heureCourteParis } from '../whitelist/whitelist.rules';

/**
 * Affichage de l'heure de convocation dans les refus du jeton de jeu.
 *
 * La fenêtre d'entrée elle-même (H − 15 min à H + retard maximal, variable
 * `DOUANE_RETARD_MAX_MIN`) n'est PAS ici : elle vit dans `douane/douane.regles.ts`
 * (`fenetreEntree`, `etatFenetre`, `lireRetardMax`), seule règle appliquée par le
 * bouton du launcher, le passage en MISSED et le jeton de jeu. L'heure suit le
 * format unique des textes joueurs (whitelist.rules.ts › heureCourteParis).
 */

const FORMAT_JOUR = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/** « samedi 25 octobre à 21 h » ou « … à 21 h 30 », heure de Paris. */
export function formatConvocation(date: Date): string {
  return `${FORMAT_JOUR.format(date)} à ${heureCourteParis(date)}`;
}
