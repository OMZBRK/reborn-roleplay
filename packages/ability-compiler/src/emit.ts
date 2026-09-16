/**
 * Serialise compiled techniques to the artifact files the plugins read.
 * graph.json is the source of truth; these are build outputs.
 *   - abilities.generated.yml : { abilities: [...] }  (ShinobiAbilities)
 *   - spells-reborn-generated.yml : { magic-items: {...}, <spellName>: {...} }
 *     — real MagicSpells layout (spells are TOP-LEVEL keys, like the server files).
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
  spells: number;
  magicItems: number;
  warnings: string[];
}

export function emit(
  compiled: { id: string; result: CompiledTechnique }[],
  outDir: string,
): EmitResult {
  mkdirSync(outDir, { recursive: true });

  const abilitiesList = compiled.map(({ id, result }) => ({ id, ...result.ability }));
  const spells: Record<string, unknown> = {};
  const magicItems: Record<string, unknown> = {};
  const warnings: string[] = [];
  for (const { result } of compiled) {
    Object.assign(spells, result.spells);
    Object.assign(magicItems, result.magicItems);
    warnings.push(...result.warnings);
  }

  const dump = (data: unknown) =>
    HEADER + yaml.dump(data, { lineWidth: 100, noRefs: true, sortKeys: false });

  const files: string[] = [];
  const abilitiesPath = join(outDir, "abilities.generated.yml");
  writeFileSync(abilitiesPath, dump({ abilities: abilitiesList }), "utf8");
  files.push(abilitiesPath);

  if (Object.keys(spells).length || Object.keys(magicItems).length) {
    const msFile: Record<string, unknown> = {};
    if (Object.keys(magicItems).length) msFile["magic-items"] = magicItems;
    Object.assign(msFile, spells);
    const p = join(outDir, "spells-reborn-generated.yml");
    writeFileSync(p, dump(msFile), "utf8");
    files.push(p);
  }

  return {
    files,
    abilities: abilitiesList.length,
    spells: Object.keys(spells).length,
    magicItems: Object.keys(magicItems).length,
    warnings,
  };
}
