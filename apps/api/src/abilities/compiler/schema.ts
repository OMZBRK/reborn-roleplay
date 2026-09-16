/**
 * ⚠️ MIROIR de packages/ability-compiler/src/schema.ts — vendorisé dans l'API
 * pour que `nest build` le compile directement (pas d'import ESM cross-package
 * en conteneur Docker). Garder les deux en phase. Le package standalone sert la
 * CLI + l'éditeur ; cette copie sert le runtime API.
 *
 * Zod schema d'un graphe de technique (graph.json). "ShinobiCore décide, le
 * moteur dessine" : Gate/Cost/Cooldown/Mastery = nœuds d'autorité (→ abilities.yml),
 * Effect·* = feuilles de rendu (→ MagicSpells / MythicMobs).
 */
import { z } from "zod";

/** Mirrors com.reborn.shinobicore.technique.Requirement.Type (Java). */
export const RequirementType = z.enum([
  "ability",
  "mastery",
  "rank",
  "skill",
  "level",
  "clan",
  "village",
  "affinity",
  "nature",
  "exam",
  "mentor",
]);
export type RequirementType = z.infer<typeof RequirementType>;

export const Requirement = z.object({
  type: RequirementType,
  id: z.string().default(""),
  min: z.number().int().nonnegative().default(0),
});
export type Requirement = z.infer<typeof Requirement>;

/** Both effect engines are equal citizens — chosen per node. */
export const Provider = z.enum(["magicspells", "mythicmobs", "raw"]);
export type Provider = z.infer<typeof Provider>;

export const ParticleShape = z.enum(["cone", "ring", "line", "sphere", "point"]);

const NodeBase = { id: z.string().min(1) };

const TriggerNode = z.object({
  ...NodeBase,
  type: z.literal("trigger"),
  method: z
    .enum(["LEFT_CLICK", "RIGHT_CLICK", "HOLD_SNEAK", "CLICK_SEQUENCE"])
    .default("LEFT_CLICK"),
  itemType: z.string().optional(),
});

const GateNode = z.object({
  ...NodeBase,
  type: z.literal("gate"),
  requires: z.array(Requirement).default([]),
});

const CostNode = z.object({
  ...NodeBase,
  type: z.literal("cost"),
  chakra: z.number().nonnegative().default(50),
  stamina: z.number().nonnegative().optional(),
});

const CooldownNode = z.object({
  ...NodeBase,
  type: z.literal("cooldown"),
  ms: z.number().int().nonnegative().default(5000),
});

const MasteryNode = z.object({
  ...NodeBase,
  type: z.literal("mastery"),
  perCast: z.number().nonnegative().default(1),
  variantThreshold: z.number().int().min(0).max(100).optional(),
  variantId: z.string().optional(),
});

const EffectExternalNode = z.object({
  ...NodeBase,
  type: z.literal("effectExternal"),
  provider: Provider,
  ref: z.string().min(1),
  runAsPlayer: z.boolean().default(true),
});

const ParticlesNode = z.object({
  ...NodeBase,
  type: z.literal("particles"),
  particle: z.string().min(1),
  shape: ParticleShape.default("point"),
  count: z.number().int().positive().default(20),
  length: z.number().positive().optional(),
  radius: z.number().positive().optional(),
  color: z.string().optional(),
  toColor: z.string().optional(),
  material: z.string().optional(),
});

const SoundNode = z.object({
  ...NodeBase,
  type: z.literal("sound"),
  sound: z.string().min(1),
  volume: z.number().positive().default(1),
  pitch: z.number().positive().default(1),
});

const DamageNode = z.object({
  ...NodeBase,
  type: z.literal("damage"),
  shape: z.enum(["cone", "ring", "target"]).default("cone"),
  amount: z.number().nonnegative().default(4),
  length: z.number().positive().optional(),
  radius: z.number().positive().optional(),
});

const StatusNode = z.object({
  ...NodeBase,
  type: z.literal("status"),
  effect: z.string().min(1),
  durationTicks: z.number().int().positive().default(60),
  amplifier: z.number().int().min(0).default(0),
});

const BlockNode = z.object({
  ...NodeBase,
  type: z.literal("block"),
  width: z.number().int().positive().default(3),
  lifetimeSeconds: z.number().int().positive().default(6),
});

const SequenceNode = z.object({ ...NodeBase, type: z.literal("sequence") });
const DelayNode = z.object({
  ...NodeBase,
  type: z.literal("delay"),
  ms: z.number().int().nonnegative().default(200),
});

export const Node = z.discriminatedUnion("type", [
  TriggerNode,
  GateNode,
  CostNode,
  CooldownNode,
  MasteryNode,
  EffectExternalNode,
  ParticlesNode,
  SoundNode,
  DamageNode,
  StatusNode,
  BlockNode,
  SequenceNode,
  DelayNode,
]);
export type Node = z.infer<typeof Node>;

export const Edge = z.object({
  from: z.string().min(1),
  to: z.string().min(1),
});
export type Edge = z.infer<typeof Edge>;

export const TechniqueGraph = z.object({
  id: z
    .string()
    .regex(/^[a-z0-9_]+$/, "id must be lower_snake_case (a-z, 0-9, _)"),
  name: z.string().min(1),
  category: z.string().min(1),
  rank: z.enum(["E", "D", "C", "B", "A", "HIDEN"]).default("D"),
  execution: z.string().default("JUTSU"),
  description: z.string().default(""),
  nodes: z.array(Node).min(1),
  edges: z.array(Edge).default([]),
});
export type TechniqueGraph = z.infer<typeof TechniqueGraph>;
