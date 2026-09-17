/**
 * Zod schema for a Technique graph (`graph.json`) — MagicSpells-native.
 *
 * A technique = a ShinobiCore FRAME (trigger/gate/cost/cooldown, node `technique`)
 * that points at a graph of MagicSpells SPELLS (node `spell`). Spells reference
 * each other through edges (MultiSpell/Area/Nova/Projectile chaining, with an
 * optional DELAY). `magicItem` nodes define Nexo/vanilla items.
 *
 * "ShinobiCore decides, MagicSpells draws" : the frame owns chakra/gating/cooldown
 * (→ abilities.yml), the spell graph owns VFX/hitbox (→ spells-reborn-generated.yml).
 *
 * The model is deliberately FLEXIBLE: each spell carries `spellClass` + a generic
 * `options` record, so ANY MagicSpells key works even if the editor has no
 * dedicated field for it. Effects are structured (esp. `entity` item_display) with
 * a `params` catch-all. This exposes MagicSpells' full power, not a fixed subset.
 */
import { z } from "zod";

/* ── Requirements (ShinobiCore gate) — mirrors Requirement.Type (Java) ── */

export const RequirementType = z.enum([
  "ability", "mastery", "rank", "skill", "level",
  "clan", "village", "affinity", "nature", "exam", "mentor",
]);
export type RequirementType = z.infer<typeof RequirementType>;

export const Requirement = z.object({
  type: RequirementType,
  id: z.string().default(""),
  min: z.number().int().nonnegative().default(0),
});
export type Requirement = z.infer<typeof Requirement>;

/* ── Generic option bag (any MagicSpells key → scalar / list / nested) ── */

export const OptionValue: z.ZodType<unknown> = z.lazy(() =>
  z.union([
    z.string(),
    z.number(),
    z.boolean(),
    z.array(OptionValue),
    z.record(z.string(), OptionValue),
  ]),
);
export const Options = z.record(z.string(), OptionValue).default({});

/* ── Effects (the MagicSpells `effects:` list) ── */

export const EffectPosition = z.enum([
  "caster", "target", "line", "trail", "delayed", "buff",
  "orbit", "special", "projectile", "start", "disabled",
]);

/** A keyframe of an entity display animation (delayed-entity-data). */
export const EntityKeyframe = z.object({
  delay: z.number().int().nonnegative().default(1),
  interval: z.number().int().nonnegative().optional(),
  iterations: z.number().int().positive().optional(),
  /** Partial entity-data applied at this keyframe (transformation, item, …). */
  data: Options,
});

/** Structured display-entity effect (item_display / block_display / text_display). */
export const EntitySpec = z.object({
  entity: z.string().default("item_display"),
  item: z.string().optional(),
  block: z.string().optional(),
  text: z.string().optional(),
  duration: z.number().int().positive().default(20),
  billboard: z.enum(["none", "fixed", "vertical", "horizontal", "center"]).optional(),
  glowing: z.boolean().optional(),
  glowColorOverride: z.string().optional(),
  viewRange: z.number().positive().optional(),
  interpolationDuration: z.number().int().nonnegative().optional(),
  interpolationDelay: z.number().int().nonnegative().optional(),
  teleportDuration: z.number().int().nonnegative().optional(),
  brightnessBlock: z.number().int().min(0).max(15).optional(),
  brightnessSky: z.number().int().min(0).max(15).optional(),
  transformation: z
    .object({
      scale: z.string().optional(),
      translation: z.string().optional(),
      leftRotation: z.string().optional(),
      rightRotation: z.string().optional(),
    })
    .optional(),
  keyframes: z.array(EntityKeyframe).default([]),
  /** Escape hatch for any other entity-data key. */
  extra: Options,
});

export const EffectSpec = z.object({
  position: EffectPosition.default("caster"),
  effect: z.string().min(1), // particles | sound | entity | effectlib | …
  delay: z.number().int().nonnegative().optional(),
  chance: z.number().min(0).max(1).optional(),
  /** particles */
  particle: z.string().optional(),
  count: z.number().int().positive().optional(),
  material: z.string().optional(), // required for block/item/falling_dust particles
  color: z.string().optional(),
  toColor: z.string().optional(),
  /** sound */
  sound: z.string().optional(),
  volume: z.number().positive().optional(),
  pitch: z.number().positive().optional(),
  /** entity (structured) */
  entitySpec: EntitySpec.optional(),
  /** effectlib */
  effectlibClass: z.string().optional(),
  /** any other effect key (particle-name spreads, effectlib params, …). */
  params: Options,
});
export type EffectSpec = z.infer<typeof EffectSpec>;

/* ── Nodes ── */

const NodeBase = {
  id: z.string().min(1),
  position: z.object({ x: z.number(), y: z.number() }).optional(),
};

/** ShinobiCore frame — the "technique" wrapper. Exactly one per graph. */
const TechniqueNode = z.object({
  ...NodeBase,
  type: z.literal("technique"),
  method: z
    .enum(["LEFT_CLICK", "RIGHT_CLICK", "HOLD_SNEAK", "CLICK_SEQUENCE"])
    .default("LEFT_CLICK"),
  itemType: z.string().optional(),
  requires: z.array(Requirement).default([]),
  chakra: z.number().nonnegative().default(50),
  stamina: z.number().nonnegative().optional(),
  cooldownMs: z.number().int().nonnegative().default(5000),
  masteryPerCast: z.number().nonnegative().optional(),
});

/** A MagicSpells spell. The node id is the emitted spell name. */
const SpellNode = z.object({
  ...NodeBase,
  type: z.literal("spell"),
  /** e.g. ".MultiSpell", ".instant.ParticleProjectileSpell", ".targeted.PainSpell". */
  spellClass: z.string().min(1).default(".instant.DummySpell"),
  displayName: z.string().optional(),
  helperSpell: z.boolean().default(true),
  options: Options,
  effects: z.array(EffectSpec).default([]),
  modifiers: z.array(z.string()).default([]),
});

/** A magic-items entry (Nexo / vanilla item). */
const MagicItemNode = z.object({
  ...NodeBase,
  type: z.literal("magicItem"),
  /** internal name referenced by cast-item / item / items. */
  itemId: z.string().min(1),
  options: Options, // type, item-model, custom-model-data, name, lore, …
});

export const Node = z.discriminatedUnion("type", [
  TechniqueNode,
  SpellNode,
  MagicItemNode,
]);
export type Node = z.infer<typeof Node>;

/** An edge. role=root: technique→root spell. role=chain: spell→sub-spell. */
export const Edge = z.object({
  from: z.string().min(1),
  to: z.string().min(1),
  role: z.enum(["root", "chain"]).default("chain"),
  /** DELAY in ticks before the child fires (MultiSpell chains). */
  delay: z.number().int().nonnegative().optional(),
  /** MagicSpells cast mode of the sub-spell (full/partial/hard/direct/none). */
  mode: z.enum(["full", "partial", "hard", "direct", "none"]).optional(),
  /** Ordering within the parent's children (lower = earlier). */
  order: z.number().optional(),
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
