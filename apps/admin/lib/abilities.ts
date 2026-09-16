import { api } from "./api";

/** Statuts — miroir de l'enum Prisma TechniqueGraphStatus. */
export type GraphStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

/** Types de nœuds — miroir du schéma Zod de @reborn/ability-compiler. */
export type NodeType =
  | "trigger"
  | "gate"
  | "cost"
  | "cooldown"
  | "mastery"
  | "effectExternal"
  | "particles"
  | "sound"
  | "damage"
  | "status"
  | "block"
  | "sequence"
  | "delay";

export type Provider = "magicspells" | "mythicmobs" | "raw";

export type RequirementType =
  | "ability"
  | "mastery"
  | "rank"
  | "skill"
  | "level"
  | "clan"
  | "village"
  | "affinity"
  | "nature"
  | "exam"
  | "mentor";

export interface Requirement {
  type: RequirementType;
  id?: string;
  min?: number;
}

/** Un nœud du graphe : id + type + données libres (validées côté serveur). */
export interface GraphNode {
  id: string;
  type: NodeType;
  /** Position React Flow (persistée pour retrouver la mise en page). */
  position?: { x: number; y: number };
  [key: string]: unknown;
}

export interface GraphEdge {
  from: string;
  to: string;
}

export interface TechniqueGraphDoc {
  id: string; // = slug (lower_snake_case)
  name: string;
  category: string;
  rank?: "E" | "D" | "C" | "B" | "A" | "HIDEN";
  execution?: string;
  description?: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface TechniqueSummary {
  id: string;
  slug: string;
  name: string;
  category: string;
  status: GraphStatus;
  updatedAt: string;
}

export interface TechniqueFull extends TechniqueSummary {
  graph: TechniqueGraphDoc;
  createdAt: string;
}

export interface DeployResult {
  deployed: number;
  written: string[];
  reloaded: string[];
  msSpells: number;
  mythicSkills: number;
  warnings: string[];
}

export function listTechniques(status?: GraphStatus): Promise<TechniqueSummary[]> {
  const q = status ? `?status=${status}` : "";
  return api<TechniqueSummary[]>(`/abilities${q}`);
}

export function getTechnique(id: string): Promise<TechniqueFull> {
  return api<TechniqueFull>(`/abilities/${id}`);
}

export function createTechnique(input: {
  slug: string;
  name: string;
  category?: string;
  status?: GraphStatus;
  graph: TechniqueGraphDoc;
}): Promise<TechniqueFull> {
  return api<TechniqueFull>("/abilities", { method: "POST", body: input });
}

export function updateTechnique(
  id: string,
  input: Partial<{
    name: string;
    category: string;
    status: GraphStatus;
    graph: TechniqueGraphDoc;
  }>,
): Promise<TechniqueFull> {
  return api<TechniqueFull>(`/abilities/${id}`, { method: "PATCH", body: input });
}

export function deleteTechnique(id: string): Promise<{ ok: boolean }> {
  return api<{ ok: boolean }>(`/abilities/${id}`, { method: "DELETE" });
}

export function validateTechnique(
  id: string,
): Promise<{ ok: boolean; warnings: string[] }> {
  return api<{ ok: boolean; warnings: string[] }>(`/abilities/${id}/validate`, {
    method: "POST",
  });
}

export function deployTechniques(): Promise<DeployResult> {
  return api<DeployResult>("/abilities/deploy", { method: "POST" });
}
