/**
 * Graph → artifacts. The compiler emits only things the running plugins already
 * read, so no graph interpreter ships to Paper:
 *   - an abilities.yml v2 entry (ShinobiCore authority: requires/cost/cooldown)
 *   - MagicSpells spell definitions (spells-reborn-generated.yml)
 *   - MythicMobs skill definitions (Reborn_generated.yml)
 *
 * Both engines are equal citizens, chosen per Effect·External node and wired
 * through the same console-command pipe (JutsuExecutionManager.dispatchCommands).
 */
import { CompileError } from "./dag.js";
import type { Node, Provider, TechniqueGraph } from "./schema.js";

/** Provider → console-command template. The one place the engine choice lives;
 *  swapping a technique's engine never touches the graph, only the node's provider. */
export const PROVIDER_TEMPLATES: Record<Provider, string> = {
  magicspells: "cast forcecast %player% {ref}",
  mythicmobs: "mm skills cast {ref} %player%",
  raw: "{ref}",
};

export interface CompiledTechnique {
  /** One entry for abilities.yml, keyed by id at emit time. */
  ability: Record<string, unknown>;
  /** MagicSpells spell definitions this graph materialised (keyed by ref). */
  msSpells: Record<string, unknown>;
  /** MythicMobs skill definitions this graph materialised (keyed by ref). */
  mythicSkills: Record<string, unknown>;
  /** Non-fatal notes (unsupported nodes, etc.) — surfaced, never silently dropped. */
  warnings: string[];
}

function single<K extends Node["type"]>(
  g: TechniqueGraph,
  type: K,
): Extract<Node, { type: K }> | undefined {
  const found = g.nodes.filter((n) => n.type === type) as Extract<
    Node,
    { type: K }
  >[];
  if (found.length > 1)
    throw new CompileError(`plusieurs nœuds '${type}' (un seul attendu)`, g.id);
  return found[0];
}

/** Nodes reachable by one edge from `nodeId` (its downstream render leaves). */
function childrenOf(g: TechniqueGraph, nodeId: string): Node[] {
  const childIds = new Set(
    g.edges.filter((e) => e.from === nodeId).map((e) => e.to),
  );
  return g.nodes.filter((n) => childIds.has(n.id));
}

export function compile(g: TechniqueGraph): CompiledTechnique {
  const warnings: string[] = [];
  const msSpells: Record<string, unknown> = {};
  const mythicSkills: Record<string, unknown> = {};

  const trigger = single(g, "trigger");
  const cost = single(g, "cost");
  const cooldown = single(g, "cooldown");
  const mastery = single(g, "mastery");

  // Gate(s) → merged requires:, emitted verbatim for the Java Requirement parser.
  const requires = g.nodes
    .filter((n) => n.type === "gate")
    .flatMap((n) => n.requires)
    .map((r) => ({ type: r.type, ...(r.id ? { id: r.id } : {}), ...(r.min ? { min: r.min } : {}) }));

  // Effect·External leaves → console commands + (when they own render nodes)
  // materialised spell/skill definitions.
  const commands: string[] = [];
  let runAsPlayer = true;
  for (const n of g.nodes) {
    if (n.type !== "effectExternal") continue;
    runAsPlayer = n.runAsPlayer;
    commands.push(PROVIDER_TEMPLATES[n.provider].replace("{ref}", n.ref));

    const render = childrenOf(g, n.id);
    if (render.length === 0) continue; // references a spell authored elsewhere
    if (n.provider === "magicspells") {
      msSpells[n.ref] = materialiseMagicSpell(g, n.ref, render, warnings);
    } else if (n.provider === "mythicmobs") {
      mythicSkills[n.ref] = materialiseMythicSkill(g, render, warnings);
    } else {
      warnings.push(
        `${g.id}: nœuds de rendu attachés à un Effect·External 'raw' — ignorés (pas de cible à générer).`,
      );
    }
  }

  const jutsu: Record<string, unknown> = {
    method: trigger?.method ?? "LEFT_CLICK",
    "chakra-cost": cost?.chakra ?? 50,
    "cooldown-millis": cooldown?.ms ?? 5000,
    "run-as-player": runAsPlayer,
  };
  if (trigger?.itemType) jutsu["item-type"] = trigger.itemType;
  if (commands.length) jutsu["commands"] = commands;
  if (mastery) jutsu["mastery-per-cast"] = mastery.perCast;

  const ability: Record<string, unknown> = {
    name: g.name,
    category: g.category,
    rank: g.rank,
    execution: g.execution,
  };
  if (g.description) ability["description"] = g.description;
  if (requires.length) ability["requires"] = requires;
  ability["jutsu"] = jutsu;

  return { ability, msSpells, mythicSkills, warnings };
}

/** Build a MagicSpells spell that just plays the attached VFX/SFX. The heavy
 *  lifting (particle types, shapes, parametrised data) is all native MS config —
 *  MS resolves any 26.2 particle via the Bukkit registry, no plugin patch. */
function materialiseMagicSpell(
  g: TechniqueGraph,
  ref: string,
  render: Node[],
  warnings: string[],
): Record<string, unknown> {
  const effects: Record<string, unknown> = {};
  for (const r of render) {
    if (r.type === "particles") {
      const e: Record<string, unknown> = {
        position: "caster",
        effect: "particles",
        "particle-name": r.particle,
        count: r.count,
      };
      if (r.color) e["color"] = r.color;
      if (r.toColor) e["to-color"] = r.toColor;
      if (r.material) e["material"] = r.material;
      if (r.shape === "cone" || r.shape === "line") {
        e["position"] = "line";
        if (r.length) e["distance"] = r.length;
      } else if (r.shape === "ring" || r.shape === "sphere") {
        if (r.radius) e["radius"] = r.radius;
      }
      effects[r.id] = e;
    } else if (r.type === "sound") {
      effects[r.id] = {
        position: "caster",
        effect: "sound",
        sound: r.sound,
        volume: r.volume,
        pitch: r.pitch,
      };
    } else {
      warnings.push(
        `${g.id}: nœud '${r.type}' pas encore matérialisé côté MagicSpells (scaffold) — à câbler.`,
      );
    }
  }
  // DummySpell simply fires its effects when force-cast; no mana/cooldown here —
  // ShinobiCore owns those (the §7.3 discipline).
  return { "spell-class": ".instant.DummySpell", effects };
}

function materialiseMythicSkill(
  g: TechniqueGraph,
  render: Node[],
  warnings: string[],
): Record<string, unknown> {
  const skills: string[] = [];
  for (const r of render) {
    if (r.type === "particles") {
      const shape =
        r.shape === "ring" || r.shape === "sphere"
          ? "particlesphere"
          : r.shape === "line" || r.shape === "cone"
            ? "particleline"
            : "particles";
      skills.push(
        `effect:${shape}{particle=${r.particle};amount=${r.count}} @Origin`,
      );
    } else if (r.type === "sound") {
      skills.push(`sound{s=${r.sound};v=${r.volume};p=${r.pitch}} @Self`);
    } else if (r.type === "damage") {
      const targeter =
        r.shape === "ring"
          ? `@EntitiesInRadius{r=${r.radius ?? 4}}`
          : r.shape === "cone"
            ? `@ConeTargets{r=${r.length ?? 6};angle=45}`
            : "@Target";
      skills.push(`damage{amount=${r.amount}} ${targeter}`);
    } else if (r.type === "status") {
      skills.push(
        `potion{type=${r.effect};duration=${r.durationTicks};lvl=${r.amplifier}} @Self`,
      );
    } else if (r.type === "delay") {
      skills.push(`delay ${Math.max(1, Math.round(r.ms / 50))}`);
    } else {
      warnings.push(
        `${g.id}: nœud '${r.type}' pas encore matérialisé côté MythicMobs (scaffold) — à câbler.`,
      );
    }
  }
  return { Skills: skills };
}
