// « Mes personnages » : ce que la carte et la fiche disent d'un personnage
// (nom, clan, village, profil) et ses emblèmes. Les emblèmes sont les vrais
// logos du mod, déjà embarqués pour la candidature (whitelist/villages.ts).

import {
  LIBELLE_SEXE,
  LIBELLE_TYPE,
  libelleGrade,
  type PersonnageCompte,
} from "../../lib/personnages";
import { HABILLAGE_CLANS, HABILLAGE_VILLAGES } from "../whitelist/villages";

/**
 * Emplacements de base d'un compte (le 2ᵉ personnage est libre, Q8). L'API
 * ne transmet pas ceux que le staff débloque : on n'invite à créer que sous
 * ce seuil, jamais au-delà.
 */
export const EMPLACEMENTS_DE_BASE = 2;

/** Clé de comparaison : sans accents ni casse, « gakure » final retiré. */
const cle = (s: string) =>
  s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .trim()
    .toLowerCase()
    .replace(/gakure$/, "");

const VILLAGES = new Map(Object.entries(HABILLAGE_VILLAGES).map(([nom, h]) => [cle(nom), h.emblem]));
const CLANS = new Map(Object.entries(HABILLAGE_CLANS).map(([nom, h]) => [cle(nom), h.emblem]));

/** Emblème du village (« Konohagakure », « Konoha »…), ou null s'il est inconnu. */
export const emblemeVillage = (village: string | null): string | null =>
  village ? (VILLAGES.get(cle(village)) ?? null) : null;

/** Blason du clan (« Hozuki », « Hōzuki »…), ou null s'il est inconnu. */
export const emblemeClan = (clan: string | null): string | null =>
  clan ? (CLANS.get(cle(clan)) ?? null) : null;

/** Nom de famille affiché : le clan acquis en session, sinon le nom libre. */
export const nomFamille = (p: PersonnageCompte): string | null => p.clan ?? p.lastName;

/** Prénom affiché ; repli si le serveur n'en a pas transmis. */
export const prenom = (p: PersonnageCompte): string => p.firstName || "Sans nom";

export const nomComplet = (p: PersonnageCompte): string =>
  [prenom(p), nomFamille(p)].filter(Boolean).join(" ");

/** Mort RP ou supprimé : la carte et le blason s'éteignent. */
export const eteint = (p: PersonnageCompte): boolean => p.state !== "ALIVE";

/** Grade lisible, ou null (civil ou grade inconnu). */
export const grade = (p: PersonnageCompte): string | null =>
  p.type === "CIVIL" ? null : libelleGrade(p.rank);

/** « Clan Hozuki, rejoint en session · Kirigakure » ; « Sans clan · Konohagakure ». */
export function ligneClanVillage(p: PersonnageCompte): string {
  const clan = p.clan ? `Clan ${p.clan}, rejoint en session` : "Sans clan";
  return [clan, p.village].filter(Boolean).join(" · ");
}

/** « Ninja · Genin · 16 ans · Homme » ; « Civil · 22 ans · Femme ». */
export function ligneProfil(p: PersonnageCompte): string {
  return [
    LIBELLE_TYPE[p.type],
    grade(p),
    p.age !== null ? `${p.age} ans` : null,
    p.sex ? LIBELLE_SEXE[p.sex] : null,
  ]
    .filter(Boolean)
    .join(" · ");
}
