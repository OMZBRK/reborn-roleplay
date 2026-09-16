/**
 * Self-test — run with `pnpm test`. Guards the properties that justify the
 * compiler over hand-edited YAML: correct MagicSpells artifacts out, and hard
 * errors on cycles / dead references / crash-prone particles.
 */
import { compile } from "./compile.js";
import { CompileError, validateGraph, validateReferences } from "./dag.js";
import { TechniqueGraph } from "./schema.js";

let failures = 0;
function ok(cond: boolean, msg: string): void {
  if (cond) console.log(`  ✓ ${msg}`);
  else { console.error(`  ✗ ${msg}`); failures++; }
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

const graph = (over: Record<string, unknown>): TechniqueGraph =>
  TechniqueGraph.parse({
    id: "t_test",
    name: "Test",
    category: "ninjutsu/pur",
    nodes: [
      { id: "frame", type: "technique", method: "LEFT_CLICK", chakra: 30, requires: [{ type: "nature", id: "katon" }] },
      { id: "root", type: "spell", spellClass: ".MultiSpell" },
    ],
    edges: [{ from: "frame", to: "root", role: "root" }],
    ...over,
  });

console.log("compile — frame + MultiSpell chaîné :");
{
  const g = graph({
    nodes: [
      { id: "frame", type: "technique", method: "LEFT_CLICK", chakra: 42, cooldownMs: 8000, requires: [{ type: "mastery", id: "root", min: 10 }] },
      { id: "root", type: "spell", spellClass: ".MultiSpell", displayName: "&cX", helperSpell: false },
      { id: "vfx", type: "spell", spellClass: ".instant.DummySpell", effects: [{ position: "caster", effect: "sound", sound: "x", params: {} }] },
    ],
    edges: [
      { from: "frame", to: "root", role: "root" },
      { from: "root", to: "vfx", role: "chain", delay: 4 },
    ],
  });
  validateGraph(g);
  const r = compile(g);
  const jutsu = r.ability.jutsu as Record<string, unknown>;
  ok((jutsu.commands as string[])[0] === "cast forcecast %player% root", "commande cast forcecast → sort racine");
  ok(jutsu["chakra-cost"] === 42, "chakra-cost propagé");
  ok((r.ability.requires as unknown[]).length === 1, "requires: compilé");
  const rootDef = r.spells.root as Record<string, unknown>;
  ok(rootDef["spell-class"] === ".MultiSpell", "spell-class émis");
  ok(JSON.stringify(rootDef["spells"]) === JSON.stringify(["DELAY 4", "vfx"]), "chaîne MultiSpell avec DELAY");
  const vfx = r.spells.vfx as Record<string, unknown>;
  ok(Array.isArray(vfx["effects"]), "effets émis");
}

console.log("validateGraph — cycle interdit :");
throws(
  () => validateGraph(graph({
    nodes: [
      { id: "frame", type: "technique" },
      { id: "a", type: "spell", spellClass: ".MultiSpell" },
      { id: "b", type: "spell", spellClass: ".MultiSpell" },
    ],
    edges: [
      { from: "frame", to: "a", role: "root" },
      { from: "a", to: "b", role: "chain" },
      { from: "b", to: "a", role: "chain" },
    ],
  })),
  "cycle", "cycle a→b→a détecté",
);

console.log("validateGraph — particule block sans material (crash serveur) :");
throws(
  () => validateGraph(graph({
    nodes: [
      { id: "frame", type: "technique" },
      { id: "root", type: "spell", spellClass: ".instant.DummySpell", effects: [{ position: "caster", effect: "particles", particle: "block", params: {} }] },
    ],
    edges: [{ from: "frame", to: "root", role: "root" }],
  })),
  "material", "particule 'block' sans material rejetée",
);

console.log("validateGraph — arête vers nœud inconnu :");
throws(
  () => validateGraph(graph({ edges: [{ from: "frame", to: "root", role: "root" }, { from: "root", to: "ghost", role: "chain" }] })),
  "inconnu", "arête vers 'ghost' rejetée",
);

console.log("validateReferences — prérequis vers technique inexistante :");
throws(
  () => validateReferences([graph({
    nodes: [
      { id: "frame", type: "technique", requires: [{ type: "ability", id: "nexiste_pas" }] },
      { id: "root", type: "spell", spellClass: ".MultiSpell" },
    ],
    edges: [{ from: "frame", to: "root", role: "root" }],
  })]),
  "inconnue", "prérequis ability vers 'nexiste_pas' rejeté",
);

console.log(failures === 0 ? "\n✓ tous les tests passent" : `\n✗ ${failures} échec(s)`);
process.exit(failures === 0 ? 0 : 1);
