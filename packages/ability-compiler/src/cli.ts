#!/usr/bin/env node
/**
 * reborn-ability — compile Technique graphs to plugin artifacts.
 *
 *   pnpm ability build [graphsDir] [--out <dir>]   # validate + compile + emit
 *   pnpm ability validate [graphsDir]              # validate only (no output)
 *
 * Defaults: graphsDir = ./graphs, out = ./out. The editor (apps/admin) will call
 * the same compile()/validate() functions in-process; this CLI is the
 * testable/versionable path (Track B, Phase 1).
 */
import { readdirSync, readFileSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { compile } from "./compile.js";
import { CompileError, validateGraph, validateReferences } from "./dag.js";
import { emit } from "./emit.js";
import { TechniqueGraph } from "./schema.js";

function loadGraphs(dir: string): TechniqueGraph[] {
  let entries: string[];
  try {
    entries = readdirSync(dir).filter((f) => f.endsWith(".graph.json"));
  } catch {
    fail(`dossier de graphes introuvable : ${dir}`);
  }
  if (entries.length === 0) fail(`aucun *.graph.json dans ${dir}`);
  const graphs: TechniqueGraph[] = [];
  for (const f of entries) {
    const path = join(dir, f);
    let json: unknown;
    try {
      json = JSON.parse(readFileSync(path, "utf8"));
    } catch (e) {
      fail(`${f}: JSON invalide — ${(e as Error).message}`);
    }
    const parsed = TechniqueGraph.safeParse(json);
    if (!parsed.success) {
      const issues = parsed.error.issues
        .map((i) => `    • ${i.path.join(".") || "(racine)"} : ${i.message}`)
        .join("\n");
      fail(`${f}: schéma invalide\n${issues}`);
    }
    graphs.push(parsed.data);
  }
  return graphs;
}

function validateAll(graphs: TechniqueGraph[]): void {
  for (const g of graphs) validateGraph(g);
  validateReferences(graphs);
}

function fail(msg: string): never {
  console.error(`✗ ${msg}`);
  process.exit(1);
}

function main(): void {
  const [, , cmd = "build", ...rest] = process.argv;
  const outIdx = rest.indexOf("--out");
  const out = outIdx >= 0 ? rest[outIdx + 1] : "out";
  const graphsDir = resolve(
    rest.find((a, i) => !a.startsWith("--") && i !== outIdx + 1) ?? "graphs",
  );

  const graphs = loadGraphs(graphsDir);

  try {
    validateAll(graphs);
  } catch (e) {
    if (e instanceof CompileError) fail(e.message);
    throw e;
  }
  console.log(
    `✓ ${graphs.length} graphe(s) valide(s) : ${graphs.map((g) => g.id).join(", ")}`,
  );

  if (cmd === "validate") return;
  if (cmd !== "build") fail(`commande inconnue : ${cmd} (build | validate)`);

  const compiled = graphs.map((g) => ({ id: g.id, result: compile(g) }));
  const res = emit(compiled, resolve(out));

  console.log(
    `✓ compilé → ${res.abilities} technique(s), ${res.spells} sort(s) MagicSpells, ${res.magicItems} magic-item(s)`,
  );
  for (const f of res.files) console.log(`  → ${basename(f)}`);
  if (res.warnings.length) {
    console.log(`\n⚠ ${res.warnings.length} avertissement(s) :`);
    for (const w of res.warnings) console.log(`  • ${w}`);
  }
}

main();
