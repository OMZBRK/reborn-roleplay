import { twMerge } from "tailwind-merge";

type ClassValue =
  | string
  | number
  | null
  | false
  | undefined
  | Record<string, unknown>
  | ClassValue[];

function flatten(value: ClassValue): string {
  if (!value) return "";
  if (typeof value === "string" || typeof value === "number") return String(value);
  if (Array.isArray(value)) return value.map(flatten).filter(Boolean).join(" ");
  if (typeof value === "object") {
    return Object.entries(value)
      .filter(([, v]) => Boolean(v))
      .map(([k]) => k)
      .join(" ");
  }
  return "";
}

export function cn(...inputs: ClassValue[]): string {
  return twMerge(inputs.map(flatten).filter(Boolean).join(" "));
}
