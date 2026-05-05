import { useMemo } from "react";
import { z } from "zod";
import type { WhitelistDraft } from "../stores/whitelist-store";

// Calcule l'âge en années pleines à partir d'une date ISO YYYY-MM-DD.
// Retourne null si la date est invalide ou vide.
export function calcAge(iso: string): number | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const today = new Date();
  let age = today.getFullYear() - d.getFullYear();
  const m = today.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < d.getDate())) age--;
  return age;
}

// Format date FR (ex: "10 janvier 2004"). Null-safe.
export function formatDateFr(iso: string): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const months = [
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
  ];
  return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

// ──────────────────────────────────────────────────────────────
// Schemas Zod
// ──────────────────────────────────────────────────────────────

// Étape 1 : HRP (informations hors-personnage).
// La règle "16+" est validée via refine (zod ne sait pas calculer un âge).
export const Step1Schema = z.object({
  dob: z
    .string()
    .min(1, "Date de naissance requise")
    .refine((v) => {
      const age = calcAge(v);
      return age !== null && age >= 16;
    }, "Vous devez avoir au moins 16 ans"),
  motivation: z
    .string()
    .min(150, "Minimum 150 caractères"),
  experience: z
    .string()
    .min(150, "Minimum 150 caractères"),
  availability: z
    .string()
    .min(20, "Minimum 20 caractères"),
});

// Étape 2 : RP (le personnage).
// support reste optionnel (URL YouTube ou Canva, libre).
export const Step2Schema = z.object({
  firstName: z.string().trim().min(1, "Prénom requis"),
  lastName: z.string().trim().min(1, "Nom requis"),
  village: z.string().min(1, "Village requis"),
  support: z.string().optional().or(z.literal("")),
  history: z.string().min(150, "Minimum 150 caractères"),
  appearance: z.string().min(150, "Minimum 150 caractères"),
  objectives: z.string().min(400, "Minimum 400 caractères"),
});

export type StepValidation = {
  isValid: boolean;
  errors: Record<string, string>;
};

// Hook pour driver la validation d'une étape : retourne {isValid, errors}.
// errors est un map field → message d'erreur (premier rencontré). Memoïsé sur
// le draft pour éviter de recalculer à chaque render hors changement.
export function useStepValidation(
  step: 1 | 2,
  draft: WhitelistDraft,
): StepValidation {
  return useMemo(() => {
    const schema = step === 1 ? Step1Schema : Step2Schema;
    const subset =
      step === 1
        ? {
            dob: draft.dob,
            motivation: draft.motivation,
            experience: draft.experience,
            availability: draft.availability,
          }
        : {
            firstName: draft.firstName,
            lastName: draft.lastName,
            village: draft.village,
            support: draft.support,
            history: draft.history,
            appearance: draft.appearance,
            objectives: draft.objectives,
          };
    const result = schema.safeParse(subset);
    if (result.success) return { isValid: true, errors: {} };
    const errors: Record<string, string> = {};
    for (const issue of result.error.issues) {
      const key = String(issue.path[0] ?? "");
      if (!errors[key]) errors[key] = issue.message;
    }
    return { isValid: false, errors };
  }, [step, draft]);
}
