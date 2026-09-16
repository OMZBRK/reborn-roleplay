/**
 * Zod schema for a Technique graph (`graph.json`) — the single source of truth
 * the editor produces and the compiler consumes. The graph never runs on the
 * server: it compiles to artifacts the plugins already read (see compile.ts).
 *
 * "ShinobiCore decides, the engine draws": Gate/Cost/Cooldown/Mastery are
 * authority nodes (→ abilities.yml v2), Effect·* are render leaves (→ MagicSpells
 * or MythicMobs YAML via a provider template).
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

/** Both effect engines are equal citizens — chosen per node (user decision). */
export const Provider = z.enum(["magicspells", "mythicmobs", "raw"]);
export type Provider = z.infer<typeof Provider>;

export const ParticleShape = z.enum([
  "cone",
  "ring",
  "line",
  "sphere",
  "point",
]);

const NodeBase = { id: z.string().min(1) };

/** How the technique is triggered + which JutsuItem carries it. */
const TriggerNode = z.object({
  ...NodeBase,
  type: z.literal("trigger"),
  method: z
    .enum(["LEFT_CLICK", "RIGHT_CLICK", "HOLD_SNEAK", "CLICK_SEQUENCE"])
    .default("LEFT_CLICK"),
  itemType: z.string().optional(),
});

/** Access prerequisites — compiles verbatim into abilities.yml `requires:`. */
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

/** A render leaf pointing at an existing MS spell / MythicMob skill, OR carrying
 *  an inline VFX built from connected particle/sound/damage nodes (materialised). */
const EffectExternalNode = z.object({
  ...NodeBase,
  type: z.literal("effectExternal"),
  provider: Provider,
  /** Name of the target spell/skill. Also the generated key when materialised. */
  ref: z.string().min(1),
  runAsPlayer: z.boolean().default(true),
});

const ParticlesNode = z.object({
  ...NodeBase,
  type: z.literal("particles"),
  /** Any Bukkit/26.2 particle name — MagicSpells resolves it via the registry. */
  particle: z.string().min(1),
  shape: ParticleShape.default("point"),
  count: z.number().int().positive().default(20),
  length: z.number().positive().optional(),
  radius: z.number().positive().optional(),
  /** For dust_color_transition / dust. */
  color: z.string().optional(),
  toColor: z.string().optional(),
  /** For block / falling_dust / item particles. */
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

/** Timing — the 80% of a technique's feel the old model couldn't express. */
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
