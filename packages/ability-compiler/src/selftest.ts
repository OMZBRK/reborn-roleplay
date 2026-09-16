/**
 * Self-test — no framework, run with `pnpm exec tsx src/selftest.ts`.
 * Guards the two properties that justify the compiler over hand-edited YAML:
 * correct artifacts out, and hard errors on cycles / dead references.
 */
import { compile } from "./compile.js";
import { CompileError, validateGraph, validateReferences } from "./dag.js";
import { TechniqueGraph } from "./schema.js";

let failures = 0;
function ok(cond: boolean, msg: string): void {
  if (cond) console.log(`  ✓ ${msg}`);
  else {
    console.error(`  ✗ ${msg}`);
    failures++;
  }
}
function throws(fn: () => void, needle: string, msg: string): void {
  try {
    fn();
    console.error(`  ✗ ${msg} (n'a pas levé d'erreur)`);
    failures++;
  } catch (e) {
    const m = (e as Error).message;
    ok(e instanceof CompileError && m.includes(needle), `${msg} → « ${m} »`);
  }
}

const base = (over: Record<string, unknown>): TechniqueGraph =>
  TechniqueGraph.parse({
    id: "t_test",
    name: "Test",
    category: "ninjutsu/pur",
    nodes: [{ id: "trigger", type: "trigger" }],
    edges: [],
    ...over,
  });

console.log("compile — technique simple :");
{
  const g = base({
    nodes: [
      { id: "trigger", type: "trigger", method: "LEFT_CLICK" },
      { id: "gate", type: "gate", requires: [{ type: "nature", id: "katon" }] },
      { id: "cost", type: "cost", chakra: 42 },
      { id: "fx", type: "effectExternal", provider: "magicspells", ref: "reborn_test" },
      { id: "p", type: "particles", particle: "FLAME", shape: "cone", count: 20, length: 4 },
    ],
    edges: [
      { from: "trigger", to: "gate" },
      { from: "gate", to: "cost" },
      { from: "cost", to: "fx" },
      { from: "fx", to: "p" },
    ],
  });
  validateGraph(g);
  const r = compile(g);
  const jutsu = r.ability.jutsu as Record<string, unknown>;
  ok((r.ability.requires as unknown[]).length === 1, "requires: compilé");
  ok(jutsu["chakra-cost"] === 42, "chakra-cost propagé");
  ok(
    (jutsu.commands as string[])[0] === "cast forcecast %player% reborn_test",
    "commande MagicSpells via template",
  );
  ok("reborn_test" in r.msSpells, "sort MagicSpells matérialisé");
}

console.log("validateGraph — cycle interdit :");
throws(
  () =>
    validateGraph(
      base({
        nodes: [
          { id: "a", type: "sequence" },
          { id: "b", type: "delay", ms: 100 },
        ],
        edges: [
          { from: "a", to: "b" },
          { from: "b", to: "a" },
        ],
      }),
    ),
  "cycle",
  "cycle a→b→a détecté",
);

console.log("validateGraph — arête vers nœud inconnu :");
throws(
  () =>
    validateGraph(
      base({ nodes: [{ id: "a", type: "sequence" }], edges: [{ from: "a", to: "ghost" }] }),
    ),
  "inconnu",
  "arête vers 'ghost' rejetée",
);

console.log("validateReferences — prérequis vers technique inexistante :");
throws(
  () =>
    validateReferences([
      base({
        nodes: [
          { id: "trigger", type: "trigger" },
          { id: "gate", type: "gate", requires: [{ type: "ability", id: "nexiste_pas" }] },
        ],
      }),
    ]),
  "inconnue",
  "prérequis ability vers 'nexiste_pas' rejeté",
);

console.log(failures === 0 ? "\n✓ tous les tests passent" : `\n✗ ${failures} échec(s)`);
process.exit(failures === 0 ? 0 : 1);
