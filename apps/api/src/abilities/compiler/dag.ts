// ⚠️ MIROIR de packages/ability-compiler/src/dag.ts (vendorisé, imports sans .js). Garder en phase.
/**
 * Graph integrity — the point of the compiler over hand-edited YAML: a cycle,
 * a dangling edge, a missing frame, or a reference to a technique that doesn't
 * exist is a hard COMPILE ERROR, not a silent runtime fallback.
 */
import type { TechniqueGraph } from "./schema";

export class CompileError extends Error {
  constructor(
    message: string,
    readonly graphId: string,
  ) {
    super(`[${graphId}] ${message}`);
    this.name = "CompileError";
  }
}

/** Validate a single graph's structure (frame, ids, edges, acyclicity, particles). */
export function validateGraph(g: TechniqueGraph): void {
  const ids = new Set<string>();
  for (const n of g.nodes) {
    if (ids.has(n.id)) throw new CompileError(`nœud dupliqué : ${n.id}`, g.id);
    ids.add(n.id);
  }

  const frames = g.nodes.filter((n) => n.type === "technique");
  if (frames.length !== 1)
    throw new CompileError(
      `il faut exactement un nœud 'technique' (trouvé ${frames.length})`,
      g.id,
    );

  const spells = g.nodes.filter((n) => n.type === "spell");
  if (spells.length === 0)
    throw new CompileError("au moins un nœud 'spell' est requis", g.id);

  for (const e of g.edges) {
    if (!ids.has(e.from))
      throw new CompileError(`arête depuis un nœud inconnu : ${e.from}`, g.id);
    if (!ids.has(e.to))
      throw new CompileError(`arête vers un nœud inconnu : ${e.to}`, g.id);
  }

  // Exactly one root edge (technique → root spell).
  const roots = g.edges.filter((e) => e.role === "root");
  if (roots.length !== 1)
    throw new CompileError(
      `il faut exactement une arête 'root' (technique → sort racine) — trouvé ${roots.length}`,
      g.id,
    );
  const root = roots[0];
  if (root.from !== frames[0].id)
    throw new CompileError("l'arête 'root' doit partir du nœud technique", g.id);
  const rootTarget = g.nodes.find((n) => n.id === root.to);
  if (!rootTarget || rootTarget.type !== "spell")
    throw new CompileError("l'arête 'root' doit viser un nœud 'spell'", g.id);

  assertAcyclic(g);
  validateParticles(g);
}

/** Particles that carry BlockData/ItemStack MUST declare `material` — otherwise
 *  MagicSpells crashes every tick (the real bug seen in the server error logs). */
function validateParticles(g: TechniqueGraph): void {
  const NEEDS_MATERIAL = new Set([
    "block", "block_marker", "falling_dust", "block_crumble",
    "dust_pillar", "item",
  ]);
  for (const n of g.nodes) {
    if (n.type !== "spell") continue;
    for (const fx of n.effects) {
      if (fx.effect !== "particles" || !fx.particle) continue;
      const p = fx.particle.toLowerCase().replace(/^minecraft:/, "");
      if (NEEDS_MATERIAL.has(p) && !fx.material) {
        throw new CompileError(
          `sort '${n.id}': la particule '${fx.particle}' exige un 'material' ` +
            `(sinon MagicSpells crashe à chaque tick).`,
          g.id,
        );
      }
    }
  }
}

function assertAcyclic(g: TechniqueGraph): void {
  const adj = new Map<string, string[]>();
  for (const n of g.nodes) adj.set(n.id, []);
  for (const e of g.edges) adj.get(e.from)!.push(e.to);

  const WHITE = 0, GRAY = 1, BLACK = 2;
  const color = new Map<string, number>();
  for (const n of g.nodes) color.set(n.id, WHITE);

  const stack: string[] = [];
  const visit = (u: string): void => {
    color.set(u, GRAY);
    stack.push(u);
    for (const v of adj.get(u)!) {
      if (color.get(v) === GRAY) {
        const cycle = [...stack.slice(stack.indexOf(v)), v].join(" → ");
        throw new CompileError(`cycle détecté : ${cycle}`, g.id);
      }
      if (color.get(v) === WHITE) visit(v);
    }
    stack.pop();
    color.set(u, BLACK);
  };
  for (const n of g.nodes) if (color.get(n.id) === WHITE) visit(n.id);
}

/**
 * Cross-graph reference check: every `ability`/`mastery` requirement (on the
 * technique frame) must point at a technique id that exists in the compiled set.
 */
export function validateReferences(graphs: TechniqueGraph[]): void {
  const known = new Set(graphs.map((g) => g.id));
  for (const g of graphs) {
    for (const n of g.nodes) {
      if (n.type !== "technique") continue;
      for (const req of n.requires) {
        if (
          (req.type === "ability" || req.type === "mastery") &&
          req.id &&
          !known.has(req.id)
        ) {
          throw new CompileError(
            `prérequis '${req.type}' vers une technique inconnue : ${req.id}`,
            g.id,
          );
        }
      }
    }
  }
}
