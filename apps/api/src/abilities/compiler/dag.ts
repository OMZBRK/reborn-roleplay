/**
 * ⚠️ MIROIR de packages/ability-compiler/src/dag.ts (vendorisé, imports sans `.js`).
 *
 * Contrôles d'intégrité du graphe : cycle, arête morte, ou référence à une
 * technique inexistante = erreur de compilation, pas un fallback silencieux.
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

/** Validate a single graph's internal structure (ids, edges, acyclicity). */
export function validateGraph(g: TechniqueGraph): void {
  const ids = new Set<string>();
  for (const n of g.nodes) {
    if (ids.has(n.id)) throw new CompileError(`nœud dupliqué : ${n.id}`, g.id);
    ids.add(n.id);
  }
  for (const e of g.edges) {
    if (!ids.has(e.from))
      throw new CompileError(`arête depuis un nœud inconnu : ${e.from}`, g.id);
    if (!ids.has(e.to))
      throw new CompileError(`arête vers un nœud inconnu : ${e.to}`, g.id);
  }
  assertAcyclic(g);
}

function assertAcyclic(g: TechniqueGraph): void {
  const adj = new Map<string, string[]>();
  for (const n of g.nodes) adj.set(n.id, []);
  for (const e of g.edges) adj.get(e.from)!.push(e.to);

  const WHITE = 0,
    GRAY = 1,
    BLACK = 2;
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
 * Cross-graph reference check: every `ability`/`mastery` requirement must point
 * at a technique id that actually exists in the compiled set.
 */
export function validateReferences(graphs: TechniqueGraph[]): void {
  const known = new Set(graphs.map((g) => g.id));
  for (const g of graphs) {
    for (const n of g.nodes) {
      if (n.type !== "gate") continue;
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
