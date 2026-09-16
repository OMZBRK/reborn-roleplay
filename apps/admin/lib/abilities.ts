import { api } from "./api";

/** Statuts — miroir de l'enum Prisma TechniqueGraphStatus. */
export type GraphStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type NodeType = "technique" | "spell" | "magicItem";

export type RequirementType =
  | "ability" | "mastery" | "rank" | "skill" | "level"
  | "clan" | "village" | "affinity" | "nature" | "exam" | "mentor";

export interface Requirement {
  type: RequirementType;
  id?: string;
  min?: number;
}

export type EffectPosition =
  | "caster" | "target" | "line" | "trail" | "delayed"
  | "buff" | "orbit" | "special" | "projectile" | "start" | "disabled";

export interface EntityKeyframe {
  delay: number;
  interval?: number;
  iterations?: number;
  data: Record<string, unknown>;
}

export interface EntitySpec {
  entity: string;
  item?: string;
  block?: string;
  text?: string;
  duration: number;
  billboard?: "none" | "fixed" | "vertical" | "horizontal" | "center";
  glowing?: boolean;
  glowColorOverride?: string;
  viewRange?: number;
  interpolationDuration?: number;
  interpolationDelay?: number;
  teleportDuration?: number;
  brightnessBlock?: number;
  brightnessSky?: number;
  transformation?: {
    scale?: string;
    translation?: string;
    leftRotation?: string;
    rightRotation?: string;
  };
  keyframes: EntityKeyframe[];
  extra: Record<string, unknown>;
}

export interface EffectSpec {
  position: EffectPosition;
  effect: string;
  delay?: number;
  chance?: number;
  particle?: string;
  count?: number;
  material?: string;
  color?: string;
  toColor?: string;
  sound?: string;
  volume?: number;
  pitch?: number;
  entitySpec?: EntitySpec;
  effectlibClass?: string;
  params: Record<string, unknown>;
}

/** Un nœud du graphe : id + type + données selon le type (+ position React Flow). */
export interface GraphNode {
  id: string;
  type: NodeType;
  position?: { x: number; y: number };
  // technique
  method?: "LEFT_CLICK" | "RIGHT_CLICK" | "HOLD_SNEAK" | "CLICK_SEQUENCE";
  itemType?: string;
  requires?: Requirement[];
  chakra?: number;
  stamina?: number;
  cooldownMs?: number;
  masteryPerCast?: number;
  // spell
  spellClass?: string;
  displayName?: string;
  helperSpell?: boolean;
  options?: Record<string, unknown>;
  effects?: EffectSpec[];
  modifiers?: string[];
  // magicItem
  itemId?: string;
}

export interface GraphEdge {
  from: string;
  to: string;
  role: "root" | "chain";
  delay?: number;
  mode?: "full" | "partial" | "hard" | "direct" | "none";
  order?: number;
}

export interface TechniqueGraphDoc {
  id: string;
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
  spells: number;
  magicItems: number;
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
  slug: string; name: string; category?: string; status?: GraphStatus; graph: TechniqueGraphDoc;
}): Promise<TechniqueFull> {
  return api<TechniqueFull>("/abilities", { method: "POST", body: input });
}
export function updateTechnique(
  id: string,
  input: Partial<{ name: string; category: string; status: GraphStatus; graph: TechniqueGraphDoc }>,
): Promise<TechniqueFull> {
  return api<TechniqueFull>(`/abilities/${id}`, { method: "PATCH", body: input });
}
export function deleteTechnique(id: string): Promise<{ ok: boolean }> {
  return api<{ ok: boolean }>(`/abilities/${id}`, { method: "DELETE" });
}
export function validateTechnique(id: string): Promise<{ ok: boolean; warnings: string[] }> {
  return api<{ ok: boolean; warnings: string[] }>(`/abilities/${id}/validate`, { method: "POST" });
}
export function previewTechnique(id: string): Promise<{ ok: boolean; queued: string }> {
  return api<{ ok: boolean; queued: string }>(`/abilities/${id}/preview`, { method: "POST" });
}
export function deployTechniques(): Promise<DeployResult> {
  return api<DeployResult>("/abilities/deploy", { method: "POST" });
}

export function importParse(input: { yaml?: string; serverPath?: string }): Promise<{
  spellNames: string[];
  guessedRoot: string;
  yaml: string;
}> {
  return api("/abilities/import/parse", { method: "POST", body: input });
}

export function importGraph(input: { yaml: string; rootSpell: string }): Promise<{ graph: TechniqueGraphDoc }> {
  return api("/abilities/import", { method: "POST", body: input });
}
