/**
 * ⚠️ MIROIR de packages/ability-compiler/src/compile.ts (vendorisé, imports sans `.js`).
 *
 * Graphe → artefacts (abilities.yml v2 + sorts MagicSpells + skills MythicMobs).
 * Les deux moteurs à égalité via un template par provider, sur le même tuyau
 * console (JutsuExecutionManager.dispatchCommands). ShinobiCore décide, le
 * moteur dessine.
 */
import { CompileError } from "./dag";
import type { Node, Provider, TechniqueGraph } from "./schema";

/** Provider → console-command template. */
export const PROVIDER_TEMPLATES: Record<Provider, string> = {
  magicspells: "cast forcecast %player% {ref}",
  mythicmobs: "mm skills cast {ref} %player%",
  raw: "{ref}",
};

export interface CompiledTechnique {
  ability: Record<string, unknown>;
  msSpells: Record<string, unknown>;
  mythicSkills: Record<string, unknown>;
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

  const requires = g.nodes
    .filter((n) => n.type === "gate")
    .flatMap((n) => n.requires)
    .map((r) => ({
      type: r.type,
      ...(r.id ? { id: r.id } : {}),
      ...(r.min ? { min: r.min } : {}),
    }));

  const commands: string[] = [];
  let runAsPlayer = true;
  for (const n of g.nodes) {
    if (n.type !== "effectExternal") continue;
    runAsPlayer = n.runAsPlayer;
    commands.push(PROVIDER_TEMPLATES[n.provider].replace("{ref}", n.ref));

    const render = childrenOf(g, n.id);
    if (render.length === 0) continue;
    if (n.provider === "magicspells") {
      msSpells[n.ref] = materialiseMagicSpell(g, render, warnings);
    } else if (n.provider === "mythicmobs") {
      mythicSkills[n.ref] = materialiseMythicSkill(g, render, warnings);
    } else {
      warnings.push(
        `${g.id}: nœuds de rendu attachés à un Effect·External 'raw' — ignorés.`,
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

function materialiseMagicSpell(
  g: TechniqueGraph,
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
        `${g.id}: nœud '${r.type}' pas encore matérialisé côté MagicSpells — à câbler.`,
      );
    }
  }
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
        `${g.id}: nœud '${r.type}' pas encore matérialisé côté MythicMobs — à câbler.`,
      );
    }
  }
  return { Skills: skills };
}
