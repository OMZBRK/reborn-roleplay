// MAQUETTES : petits formats français partagés par les écrans L10 / L11.

import type { Minutes } from "./donnees";

/** 21*60 → « 21 h » ; 20*60+45 → « 20 h 45 ». */
export function heure(m: Minutes): string {
  const h = Math.floor(m / 60);
  const min = m % 60;
  return min === 0 ? `${h} h` : `${h} h ${String(min).padStart(2, "0")}`;
}

/** Durée lisible : 38 → « 38 min » ; 152 → « 2 h 32 ». */
export function duree(m: number): string {
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const min = m % 60;
  return min === 0 ? `${h} h` : `${h} h ${String(min).padStart(2, "0")}`;
}

/** 1238 → « 1 238 » (espace fine insécable du français). */
export function nombre(n: number): string {
  return n.toLocaleString("fr-FR");
}

export const pluriel = (n: number, un: string, plusieurs: string) => (n > 1 ? plusieurs : un);
