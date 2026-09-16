/**
 * Serialise compiled techniques to the three artifact files the plugins read.
 * Nothing here is hand-edited downstream — graph.json is the source of truth,
 * these are build outputs (regenerated every compile).
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import yaml from "js-yaml";
import type { CompiledTechnique } from "./compile.js";

const HEADER =
  "# ⚠️ GÉNÉRÉ par @reborn/ability-compiler — NE PAS ÉDITER À LA MAIN.\n" +
  "# Source de vérité : les graph.json. Régénéré à chaque `pnpm ability build`.\n";

export interface EmitResult {
  files: string[];
  abilities: number;
  msSpells: number;
  mythicSkills: number;
  warnings: string[];
}

export function emit(
  compiled: { id: string; result: CompiledTechnique }[],
  outDir: string,
): EmitResult {
  mkdirSync(outDir, { recursive: true });

  const abilitiesList = compiled.map(({ id, result }) => ({
    id,
    ...result.ability,
  }));
  const msSpells: Record<string, unknown> = {};
  const mythicSkills: Record<string, unknown> = {};
  const warnings: string[] = [];
  for (const { result } of compiled) {
    Object.assign(msSpells, result.msSpells);
    Object.assign(mythicSkills, result.mythicSkills);
    warnings.push(...result.warnings);
  }

  const dump = (data: unknown) =>
    HEADER + yaml.dump(data, { lineWidth: 100, noRefs: true, sortKeys: false });

  const files: string[] = [];

  const abilitiesPath = join(outDir, "abilities.generated.yml");
  writeFileSync(abilitiesPath, dump({ abilities: abilitiesList }), "utf8");
  files.push(abilitiesPath);

  if (Object.keys(msSpells).length) {
    const p = join(outDir, "spells-reborn-generated.yml");
    writeFileSync(p, dump({ spells: msSpells }), "utf8");
    files.push(p);
  }
  if (Object.keys(mythicSkills).length) {
    const p = join(outDir, "Reborn_generated.yml");
    writeFileSync(p, dump(mythicSkills), "utf8");
    files.push(p);
  }

  return {
    files,
    abilities: abilitiesList.length,
    msSpells: Object.keys(msSpells).length,
    mythicSkills: Object.keys(mythicSkills).length,
    warnings,
  };
}
