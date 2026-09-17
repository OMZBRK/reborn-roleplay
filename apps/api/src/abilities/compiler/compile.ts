// ⚠️ MIROIR de packages/ability-compiler/src/compile.ts (vendorisé, imports sans .js). Garder en phase.
/**
 * Graph → artifacts. Emits only what the running plugins already read:
 *   - one abilities.yml v2 entry (ShinobiCore frame → cast forcecast root spell)
 *   - real MagicSpells definitions (spells-reborn-generated.yml): top-level named
 *     spells + a magic-items block, matching the server's hand-written style.
 *
 * No graph interpreter ships to Paper. "ShinobiCore decides, MagicSpells draws."
 */
import { CompileError } from "./dag";
import type { EffectSpec, Node, TechniqueGraph } from "./schema";

export interface CompiledTechnique {
  /** abilities.yml entry (keyed by technique id at emit time). */
  ability: Record<string, unknown>;
  /** MagicSpells spell definitions, keyed by spell name (top-level in the file). */
  spells: Record<string, unknown>;
  /** magic-items definitions, keyed by item id. */
  magicItems: Record<string, unknown>;
  warnings: string[];
}

type SpellNode = Extract<Node, { type: "spell" }>;
type TechniqueNode = Extract<Node, { type: "technique" }>;
type MagicItemNode = Extract<Node, { type: "magicItem" }>;

/** MultiSpell-family classes chain via a `spells:` list with DELAY entries. */
function isMulti(cls: string): boolean {
  return /\.(Targeted)?MultiSpell$/i.test(cls);
}
/** Classes that take a single sub-spell via `spell:` (fallback for chaining). */
function usesSingleSpell(cls: string): boolean {
  return /(Nova|Pulser|Orbit|Projectile)Spell$/i.test(cls);
}

export function compile(g: TechniqueGraph): CompiledTechnique {
  const warnings: string[] = [];
  const frame = g.nodes.find((n) => n.type === "technique") as TechniqueNode;
  const rootEdge = g.edges.find((e) => e.role === "root")!;
  const rootSpellId = rootEdge.to;

  // ── MagicSpells spells ──
  const spells: Record<string, unknown> = {};
  for (const n of g.nodes) {
    if (n.type !== "spell") continue;
    spells[n.id] = buildSpell(g, n, warnings);
  }

  // ── magic-items ──
  const magicItems: Record<string, unknown> = {};
  for (const n of g.nodes) {
    if (n.type !== "magicItem") continue;
    magicItems[(n as MagicItemNode).itemId] = { ...(n as MagicItemNode).options };
  }

  // ── abilities.yml frame ──
  const requires = frame.requires.map((r) => ({
    type: r.type,
    ...(r.id ? { id: r.id } : {}),
    ...(r.min ? { min: r.min } : {}),
  }));
  const jutsu: Record<string, unknown> = {
    method: frame.method,
    "chakra-cost": frame.chakra,
    "cooldown-millis": frame.cooldownMs,
    "run-as-player": true,
    commands: [`cast forcecast %player% ${rootSpellId}`],
  };
  if (frame.itemType) jutsu["item-type"] = frame.itemType;
  if (frame.masteryPerCast) jutsu["mastery-per-cast"] = frame.masteryPerCast;

  const ability: Record<string, unknown> = {
    name: g.name,
    category: g.category,
    rank: g.rank,
    execution: g.execution,
  };
  if (g.description) ability["description"] = g.description;
  if (requires.length) ability["requires"] = requires;
  ability["jutsu"] = jutsu;

  return { ability, spells, magicItems, warnings };
}

/** Ordered chain children of a spell (role=chain), sorted by edge.order. */
function chainChildren(
  g: TechniqueGraph,
  nodeId: string,
): { id: string; delay?: number; mode?: string }[] {
  return g.edges
    .filter((e) => e.role === "chain" && e.from === nodeId)
    .map((e, i) => ({ e, i }))
    .sort((a, b) => (a.e.order ?? a.i) - (b.e.order ?? b.i))
    .map(({ e }) => ({ id: e.to, delay: e.delay, mode: e.mode }));
}

function buildSpell(
  g: TechniqueGraph,
  n: SpellNode,
  warnings: string[],
): Record<string, unknown> {
  const def: Record<string, unknown> = { "spell-class": n.spellClass };
  if (n.displayName) def["name"] = n.displayName;
  if (n.helperSpell) def["helper-spell"] = true;

  // user options first (so explicit keys win nothing structural below)
  Object.assign(def, n.options);

  if (n.modifiers.length) def["modifiers"] = [...n.modifiers];

  // chaining
  const children = chainChildren(g, n.id);
  if (children.length) {
    if (isMulti(n.spellClass)) {
      const list: string[] = [];
      for (const c of children) {
        if (c.delay && c.delay > 0) list.push(`DELAY ${c.delay}`);
        list.push(c.mode ? `${c.id}(mode=${c.mode})` : c.id);
      }
      def["spells"] = list;
    } else if (usesSingleSpell(n.spellClass)) {
      def["spell"] = children[0].id;
      if (children.length > 1)
        warnings.push(
          `${g.id}: le sort '${n.id}' (${n.spellClass}) ne prend qu'un sous-sort — ${children.length - 1} ignoré(s).`,
        );
    } else {
      def["spells"] = children.map((c) => c.id);
    }
  }

  // effects
  if (n.effects.length) {
    def["effects"] = n.effects.map((fx) => buildEffect(fx));
  }
  return def;
}

function buildEffect(fx: EffectSpec): Record<string, unknown> {
  const e: Record<string, unknown> = { position: fx.position, effect: fx.effect };
  if (fx.delay !== undefined) e["delay"] = fx.delay;
  if (fx.chance !== undefined) e["chance"] = fx.chance;

  switch (fx.effect) {
    case "particles": {
      if (fx.particle) e["particle-name"] = fx.particle;
      if (fx.count !== undefined) e["count"] = fx.count;
      if (fx.material) e["material"] = fx.material;
      if (fx.color) e["color"] = fx.color;
      if (fx.toColor) e["to-color"] = fx.toColor;
      break;
    }
    case "sound": {
      if (fx.sound) e["sound"] = fx.sound;
      if (fx.volume !== undefined) e["volume"] = fx.volume;
      if (fx.pitch !== undefined) e["pitch"] = fx.pitch;
      break;
    }
    case "entity": {
      if (fx.entitySpec) e["entity"] = buildEntity(fx.entitySpec);
      break;
    }
    case "effectlib": {
      const el: Record<string, unknown> = { ...(fx.params as object) };
      if (fx.effectlibClass) el["class"] = fx.effectlibClass;
      e["effectlib"] = el;
      Object.assign(e, {}); // effectlib params live under `effectlib:`
      return e;
    }
  }
  // passthrough for any extra keys (spreads, effect-interval, offsets, …)
  Object.assign(e, fx.params as object);
  return e;
}

function buildEntity(s: NonNullable<EffectSpec["entitySpec"]>): Record<string, unknown> {
  const ent: Record<string, unknown> = { entity: s.entity, duration: s.duration };
  if (s.item) ent["item"] = s.item;
  if (s.block) ent["block"] = s.block;
  if (s.text) ent["text"] = s.text;
  if (s.billboard) ent["billboard"] = s.billboard;
  if (s.glowing !== undefined) ent["glowing"] = s.glowing;
  if (s.glowColorOverride) ent["glow-color-override"] = s.glowColorOverride;
  if (s.viewRange !== undefined) ent["view-range"] = s.viewRange;
  if (s.interpolationDuration !== undefined)
    ent["interpolation-duration"] = s.interpolationDuration;
  if (s.interpolationDelay !== undefined)
    ent["interpolation-delay"] = s.interpolationDelay;
  if (s.teleportDuration !== undefined) ent["teleport-duration"] = s.teleportDuration;
  if (s.brightnessBlock !== undefined || s.brightnessSky !== undefined) {
    ent["brightness"] = {
      ...(s.brightnessBlock !== undefined ? { block: s.brightnessBlock } : {}),
      ...(s.brightnessSky !== undefined ? { sky: s.brightnessSky } : {}),
    };
  }
  if (s.transformation) {
    const t = s.transformation;
    const tr: Record<string, unknown> = {};
    if (t.leftRotation) tr["left-rotation"] = t.leftRotation;
    if (t.rightRotation) tr["right-rotation"] = t.rightRotation;
    if (t.scale) tr["scale"] = t.scale;
    if (t.translation) tr["translation"] = t.translation;
    if (Object.keys(tr).length) ent["transformation"] = tr;
  }
  if (s.keyframes.length) {
    ent["delayed-entity-data"] = s.keyframes.map((k) => ({
      delay: k.delay,
      ...(k.interval !== undefined ? { interval: k.interval } : {}),
      ...(k.iterations !== undefined ? { iterations: k.iterations } : {}),
      "entity-data": { ...(k.data as object) },
    }));
  }
  Object.assign(ent, s.extra as object);
  return ent;
}
