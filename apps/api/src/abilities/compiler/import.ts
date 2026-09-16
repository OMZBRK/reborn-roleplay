// ⚠️ MIROIR de packages/ability-compiler/src/import.ts (vendorisé, imports sans .js). Garder en phase.
/**
 * Reverse compiler : un fichier de sorts MagicSpells (spells-*.yml) → un
 * TechniqueGraph éditable/visualisable. C'est l'inverse de compile.ts — de quoi
 * ouvrir les sorts existants du serveur dans l'éditeur et voir leur structure.
 *
 * On importe UN sort racine + sa fermeture transitive (les sous-sorts qu'il
 * référence), plus les magic-items utilisés. Tout ce qui n'a pas de champ
 * dédié est conservé en `options`/`params` (aucune perte).
 */
import { parse as parseYaml } from "yaml";
import type {
  EffectSpec,
  Node,
  TechniqueGraph,
} from "./schema";

type Raw = Record<string, unknown>;

/** Clés structurelles d'un sort — gérées à part, pas versées dans `options`. */
const STRUCTURAL = new Set([
  "spell-class", "name", "helper-spell", "spells", "spell", "effects", "modifiers",
]);

export function parseSpellFile(text: string): Raw {
  // uniqueKeys:false = tolère les clés dupliquées (dernier gagne), comme SnakeYAML
  // côté serveur — les fichiers écrits à la main en ont souvent.
  const doc = parseYaml(text, { uniqueKeys: false, strict: false });
  if (!doc || typeof doc !== "object")
    throw new Error("YAML MagicSpells invalide (racine non-objet).");
  return doc as Raw;
}

/** Noms des sorts top-level (hors magic-items). */
export function listSpellNames(parsed: Raw): string[] {
  return Object.keys(parsed).filter(
    (k) => k !== "magic-items" && isSpellDef(parsed[k]),
  );
}

function isSpellDef(v: unknown): v is Raw {
  return !!v && typeof v === "object" && "spell-class" in (v as Raw);
}

/** Choisit un sort racine plausible : cast-item d'abord, puis un MultiSpell. */
export function guessRoot(parsed: Raw): string | undefined {
  const names = listSpellNames(parsed);
  const withCastItem = names.find((n) => {
    const d = parsed[n] as Raw;
    return "cast-item" in d || "cast-items" in d;
  });
  if (withCastItem) return withCastItem;
  const multi = names.find((n) =>
    /MultiSpell$/i.test(String((parsed[n] as Raw)["spell-class"] ?? "")),
  );
  return multi ?? names[0];
}

/** Importe le sous-graphe atteignable depuis `rootName`. */
export function importTechnique(
  parsed: Raw,
  rootName: string,
  meta?: { id?: string; name?: string; category?: string },
): TechniqueGraph {
  if (!isSpellDef(parsed[rootName]))
    throw new Error(`Sort racine introuvable : ${rootName}`);

  const magicItemsBlock = (parsed["magic-items"] as Raw) ?? {};
  const nodes: Node[] = [];
  const edges: TechniqueGraph["edges"] = [];
  const usedItems = new Set<string>();

  // BFS depuis la racine sur les références spells:/spell:
  const seen = new Set<string>();
  const queue: string[] = [rootName];
  const depth = new Map<string, number>([[rootName, 0]]);
  while (queue.length) {
    const name = queue.shift()!;
    if (seen.has(name)) continue;
    seen.add(name);
    const def = parsed[name] as Raw;
    if (!isSpellDef(def)) continue;

    const { node, refs, items } = toSpellNode(name, def);
    nodes.push({ ...node, position: { x: 0, y: 0 } });
    for (const it of items) usedItems.add(it);

    refs.forEach((r, i) => {
      edges.push({
        from: name, to: r.id, role: "chain",
        ...(r.delay !== undefined ? { delay: r.delay } : {}),
        ...(r.mode ? { mode: r.mode as never } : {}),
        order: i,
      });
      if (!seen.has(r.id) && isSpellDef(parsed[r.id])) {
        queue.push(r.id);
        if (!depth.has(r.id)) depth.set(r.id, (depth.get(name) ?? 0) + 1);
      }
    });
  }

  // magic-items référencés (item: d'un entity, cast-item, items:)
  for (const itemId of usedItems) {
    if (magicItemsBlock[itemId] !== undefined) {
      nodes.push({
        id: `item__${itemId}`, type: "magicItem", itemId,
        options: (magicItemsBlock[itemId] as Raw) ?? {},
        position: { x: 0, y: 0 },
      } as Node);
    }
  }

  // cadre technique synthétique + arête racine
  const rootDef = parsed[rootName] as Raw;
  nodes.unshift({
    id: "frame", type: "technique",
    method: "LEFT_CLICK",
    itemType: typeof rootDef["cast-item"] === "string" ? String(rootDef["cast-item"]) : undefined,
    requires: [],
    chakra: 40,
    cooldownMs: Math.round(Number(rootDef["cooldown"] ?? 5) * 1000) || 5000,
    position: { x: 0, y: 0 },
  } as Node);
  edges.unshift({ from: "frame", to: rootName, role: "root" });

  layout(nodes, edges, depth);

  const id = (meta?.id ?? rootName).toLowerCase().replace(/[^a-z0-9_]/g, "_");
  return {
    id,
    name: meta?.name ?? String(rootDef["name"] ?? rootName),
    category: meta?.category ?? "importé",
    rank: "D",
    execution: "JUTSU",
    description: `Importé depuis MagicSpells (sort racine : ${rootName}).`,
    nodes,
    edges,
  };
}

interface Ref { id: string; delay?: number; mode?: string }

function toSpellNode(
  name: string,
  def: Raw,
): { node: Node; refs: Ref[]; items: string[] } {
  const items: string[] = [];
  const options: Raw = {};
  for (const [k, v] of Object.entries(def)) {
    if (!STRUCTURAL.has(k)) options[k] = v;
  }
  if (typeof def["cast-item"] === "string") items.push(def["cast-item"]);

  // effets
  const effects: EffectSpec[] = [];
  const rawEffects = def["effects"];
  const effectEntries: Raw[] = Array.isArray(rawEffects)
    ? (rawEffects as Raw[])
    : rawEffects && typeof rawEffects === "object"
      ? Object.values(rawEffects as Raw).filter((v): v is Raw => !!v && typeof v === "object")
      : [];
  for (const fx of effectEntries) {
    const { spec, item } = toEffectSpec(fx);
    if (item) items.push(item);
    effects.push(spec);
  }

  // références de sous-sorts
  const refs: Ref[] = [];
  const spellsList = def["spells"];
  if (Array.isArray(spellsList)) {
    let pendingDelay: number | undefined;
    for (const entry of spellsList) {
      const s = String(entry).trim();
      const mDelay = s.match(/^DELAY\s+(\d+)/i);
      if (mDelay) { pendingDelay = Number(mDelay[1]); continue; }
      const m = s.match(/^([^(:\s]+)(?:\(mode=([a-z]+)\))?(?::\d+)?/i);
      if (m) {
        refs.push({ id: m[1], delay: pendingDelay, mode: m[2] });
        pendingDelay = undefined;
      }
    }
  } else if (typeof def["spell"] === "string") {
    refs.push({ id: def["spell"] });
  }

  const modifiers = Array.isArray(def["modifiers"]) ? (def["modifiers"] as unknown[]).map(String) : [];

  const node: Node = {
    id: name, type: "spell",
    spellClass: String(def["spell-class"] ?? ".instant.DummySpell"),
    displayName: typeof def["name"] === "string" ? def["name"] : undefined,
    helperSpell: def["helper-spell"] === true,
    options,
    effects,
    modifiers,
  } as Node;
  return { node, refs, items };
}

function toEffectSpec(fx: Raw): { spec: EffectSpec; item?: string } {
  const position = String(fx["position"] ?? "caster") as EffectSpec["position"];
  const effect = String(fx["effect"] ?? "particles");
  const spec: EffectSpec = { position, effect, params: {} };
  if (fx["delay"] !== undefined) spec.delay = Number(fx["delay"]);
  if (fx["chance"] !== undefined) spec.chance = Number(fx["chance"]);
  let item: string | undefined;

  const consumed = new Set(["position", "effect", "delay", "chance"]);
  if (effect === "particles") {
    if (fx["particle-name"]) spec.particle = String(fx["particle-name"]);
    if (fx["count"] !== undefined) spec.count = Number(fx["count"]);
    if (fx["material"]) spec.material = String(fx["material"]);
    if (fx["color"]) spec.color = String(fx["color"]);
    if (fx["to-color"]) spec.toColor = String(fx["to-color"]);
    ["particle-name", "count", "material", "color", "to-color"].forEach((k) => consumed.add(k));
  } else if (effect === "sound") {
    if (fx["sound"]) spec.sound = String(fx["sound"]);
    if (fx["volume"] !== undefined) spec.volume = Number(fx["volume"]);
    if (fx["pitch"] !== undefined) spec.pitch = Number(fx["pitch"]);
    ["sound", "volume", "pitch"].forEach((k) => consumed.add(k));
  } else if (effect === "entity" && fx["entity"] && typeof fx["entity"] === "object") {
    const { es, item: it } = toEntitySpec(fx["entity"] as Raw);
    spec.entitySpec = es; item = it; consumed.add("entity");
  } else if (effect === "effectlib" && fx["effectlib"] && typeof fx["effectlib"] === "object") {
    const el = fx["effectlib"] as Raw;
    if (el["class"]) spec.effectlibClass = String(el["class"]);
    const p: Raw = {};
    for (const [k, v] of Object.entries(el)) if (k !== "class") p[k] = v;
    spec.params = p; consumed.add("effectlib");
  }
  // reste → params
  for (const [k, v] of Object.entries(fx)) if (!consumed.has(k)) (spec.params as Raw)[k] = v;
  return { spec, item };
}

function toEntitySpec(e: Raw): { es: NonNullable<EffectSpec["entitySpec"]>; item?: string } {
  const es: NonNullable<EffectSpec["entitySpec"]> = {
    entity: String(e["entity"] ?? "item_display"),
    duration: Number(e["duration"] ?? 20),
    keyframes: [],
    extra: {},
  };
  let item: string | undefined;
  const consumed = new Set(["entity", "duration"]);
  const s = (k: string) => (e[k] !== undefined ? String(e[k]) : undefined);
  if (e["item"]) { es.item = String(e["item"]); item = es.item.split("{")[0]; consumed.add("item"); }
  if (e["block"]) { es.block = String(e["block"]); consumed.add("block"); }
  if (e["text"]) { es.text = String(e["text"]); consumed.add("text"); }
  if (e["billboard"]) { es.billboard = String(e["billboard"]).toLowerCase() as never; consumed.add("billboard"); }
  if (e["glowing"] !== undefined) { es.glowing = e["glowing"] === true; consumed.add("glowing"); }
  if (e["glow-color-override"]) { es.glowColorOverride = s("glow-color-override"); consumed.add("glow-color-override"); }
  if (e["view-range"] !== undefined) { es.viewRange = Number(e["view-range"]); consumed.add("view-range"); }
  if (e["visible-range"] !== undefined) { es.viewRange = Number(e["visible-range"]); consumed.add("visible-range"); }
  if (e["interpolation-duration"] !== undefined) { es.interpolationDuration = Number(e["interpolation-duration"]); consumed.add("interpolation-duration"); }
  if (e["interpolation-delay"] !== undefined) { es.interpolationDelay = Number(e["interpolation-delay"]); consumed.add("interpolation-delay"); }
  if (e["teleport-duration"] !== undefined) { es.teleportDuration = Number(e["teleport-duration"]); consumed.add("teleport-duration"); }
  const br = e["brightness"] as Raw | undefined;
  if (br && typeof br === "object") {
    if (br["block"] !== undefined) es.brightnessBlock = Number(br["block"]);
    if (br["sky"] !== undefined) es.brightnessSky = Number(br["sky"]);
    consumed.add("brightness");
  }
  const tr = e["transformation"] as Raw | undefined;
  if (tr && typeof tr === "object") {
    es.transformation = {
      leftRotation: tr["left-rotation"] !== undefined ? String(tr["left-rotation"]) : undefined,
      rightRotation: tr["right-rotation"] !== undefined ? String(tr["right-rotation"]) : undefined,
      scale: tr["scale"] !== undefined ? String(tr["scale"]) : undefined,
      translation: tr["translation"] !== undefined ? String(tr["translation"]) : undefined,
    };
    consumed.add("transformation");
  }
  const ded = e["delayed-entity-data"];
  if (Array.isArray(ded)) {
    es.keyframes = ded.map((k) => {
      const kf = k as Raw;
      return {
        delay: Number(kf["delay"] ?? 1),
        ...(kf["interval"] !== undefined ? { interval: Number(kf["interval"]) } : {}),
        ...(kf["iterations"] !== undefined ? { iterations: Number(kf["iterations"]) } : {}),
        data: (kf["entity-data"] as Raw) ?? {},
      };
    });
    consumed.add("delayed-entity-data");
  }
  for (const [k, v] of Object.entries(e)) if (!consumed.has(k)) (es.extra as Raw)[k] = v;
  return { es, item };
}

/** Layout en couches (racine à gauche, sous-sorts vers la droite). */
function layout(nodes: Node[], edges: TechniqueGraph["edges"], depth: Map<string, number>): void {
  const perDepth = new Map<number, number>();
  for (const n of nodes) {
    let d = depth.get(n.id);
    if (n.type === "technique") d = -1;
    else if (n.type === "magicItem") d = 4;
    else if (d === undefined) d = 1;
    const row = perDepth.get(d) ?? 0;
    perDepth.set(d, row + 1);
    n.position = { x: 60 + (d + 1) * 250, y: 60 + row * 150 };
  }
}
