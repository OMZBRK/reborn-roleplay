/**
 * Génère une galerie HTML des techniques de démonstration : pour chacune, le
 * GRAPHE (rendu SVG à partir des positions/arêtes, comme dans l'éditeur) + le
 * YAML MagicSpells généré + l'entrée abilities.yml. Base visuelle de ce que
 * l'éditeur produit. Régénérable : `pnpm exec tsx scripts/gallery.mts <dir> <out.html>`.
 */
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import yaml from "js-yaml";
import { compile } from "../src/compile.ts";
import { validateGraph } from "../src/dag.ts";
import { TechniqueGraph } from "../src/schema.ts";

const dirs = (process.argv[2] ?? "graphs/showcase,graphs").split(",");
const outPath = process.argv[3] ?? "gallery.html";

type Doc = ReturnType<typeof TechniqueGraph.parse>;
const docs: Doc[] = [];
for (const dir of dirs) {
  let files: string[] = [];
  try { files = readdirSync(dir).filter((f) => f.endsWith(".graph.json")); } catch { continue; }
  for (const f of files) {
    const g = TechniqueGraph.parse(JSON.parse(readFileSync(join(dir, f), "utf8")));
    docs.push(g);
  }
}
docs.sort((a, b) => a.name.localeCompare(b.name));

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const NODE_COLOR: Record<string, string> = { technique: "#3b5bdb", spell: "#dc2626", magicItem: "#0891b2" };

function svgFor(g: Doc): string {
  const W = 210, H = 58;
  const xs = g.nodes.map((n) => n.position?.x ?? 0);
  const ys = g.nodes.map((n) => n.position?.y ?? 0);
  const minX = Math.min(...xs) - 20, minY = Math.min(...ys) - 20;
  const maxX = Math.max(...xs) + W + 20, maxY = Math.max(...ys) + H + 20;
  const vw = maxX - minX, vh = maxY - minY;
  const pos = new Map(g.nodes.map((n) => [n.id, { x: (n.position?.x ?? 0) - minX, y: (n.position?.y ?? 0) - minY }]));
  const parts: string[] = [];
  // edges
  for (const e of g.edges) {
    const a = pos.get(e.from), b = pos.get(e.to);
    if (!a || !b) continue;
    const x1 = a.x + W, y1 = a.y + H / 2, x2 = b.x, y2 = b.y + H / 2;
    const mx = (x1 + x2) / 2;
    const col = e.role === "root" ? "#3b5bdb" : "#7a8290";
    parts.push(`<path d="M${x1},${y1} C${mx},${y1} ${mx},${y2} ${x2},${y2}" fill="none" stroke="${col}" stroke-width="2" ${e.role === "root" ? 'stroke-dasharray="4 3"' : ""}/>`);
    const lbl = e.role === "root" ? "cast" : e.delay ? `DELAY ${e.delay}` : "";
    if (lbl) parts.push(`<text x="${mx}" y="${(y1 + y2) / 2 - 4}" fill="#aab" font-size="11" text-anchor="middle">${esc(lbl)}</text>`);
  }
  // nodes
  for (const n of g.nodes) {
    const p = pos.get(n.id)!;
    const col = NODE_COLOR[n.type] ?? "#666";
    const title = n.type === "technique" ? "▶ TECHNIQUE" : n.type === "magicItem" ? "MAGIC-ITEM" : "SORT";
    const sub =
      n.type === "technique" ? `${(n as { method?: string }).method ?? ""} · ${(n as { chakra?: number }).chakra ?? 0} chakra`
      : n.type === "magicItem" ? String((n as { itemId?: string }).itemId ?? "")
      : String((n as { spellClass?: string }).spellClass ?? "");
    const name = n.type === "spell" ? ((n as { displayName?: string }).displayName?.replace(/&[0-9a-frjl]/gi, "") || n.id) : n.id;
    parts.push(`
      <g transform="translate(${p.x},${p.y})">
        <rect width="${W}" height="${H}" rx="6" fill="#12151b" stroke="${col}" stroke-width="1.5"/>
        <rect width="${W}" height="18" rx="6" fill="${col}"/>
        <rect y="9" width="${W}" height="9" fill="${col}"/>
        <text x="8" y="13" fill="#fff" font-size="10" font-weight="700">${esc(title)}</text>
        <text x="8" y="34" fill="#e5e7eb" font-size="12" font-weight="600">${esc(name.slice(0, 26))}</text>
        <text x="8" y="49" fill="#9aa4b2" font-size="10">${esc(sub.slice(0, 30))}</text>
      </g>`);
  }
  return `<svg viewBox="0 0 ${vw} ${vh}" width="100%" style="max-height:340px" preserveAspectRatio="xMidYMid meet">${parts.join("")}</svg>`;
}

const cards = docs.map((g) => {
  validateGraph(g);
  const r = compile(g);
  const msFile: Record<string, unknown> = {};
  if (Object.keys(r.magicItems).length) msFile["magic-items"] = r.magicItems;
  Object.assign(msFile, r.spells);
  const spellYaml = yaml.dump(msFile, { lineWidth: 100, noRefs: true, sortKeys: false });
  const abYaml = yaml.dump({ abilities: [{ id: g.id, ...r.ability }] }, { lineWidth: 100, noRefs: true, sortKeys: false });
  const rankColors: Record<string, string> = { E: "#6b7280", D: "#9ca3af", C: "#22c55e", B: "#38bdf8", A: "#f59e0b", HIDEN: "#c084fc" };
  return `
  <article class="card">
    <header>
      <h2>${esc(g.name)}</h2>
      <span class="rank" style="background:${rankColors[g.rank ?? "D"]}">${esc(g.rank ?? "D")}</span>
      <span class="cat">${esc(g.category)}</span>
    </header>
    <p class="desc">${esc(g.description ?? "")}</p>
    <div class="graph">${svgFor(g)}</div>
    <div class="cols">
      <div><h3>Sorts MagicSpells générés</h3><pre>${esc(spellYaml)}</pre></div>
      <div><h3>Entrée abilities.yml</h3><pre>${esc(abYaml)}</pre></div>
    </div>
  </article>`;
}).join("\n");

const html = `<h1>Galerie — Technique Creator</h1>
<p class="lead">${docs.length} technique(s) de démonstration. Chaque carte montre le <b>graphe</b> (comme dans l'éditeur du panel) et le <b>YAML MagicSpells</b> réellement généré à la compilation. Base visuelle de ce que l'éditeur produit.</p>
<style>
  :root { color-scheme: dark; }
  body, .wrap { background:#0b0e13; color:#e5e7eb; font-family: ui-sans-serif, system-ui, sans-serif; }
  h1 { font-size: 1.5rem; margin: 0 0 .3rem; }
  .lead { color:#9aa4b2; margin:0 0 1.2rem; max-width: 70ch; }
  .card { border:1px solid #232833; border-radius:12px; padding:1rem 1.2rem; margin-bottom:1.4rem; background:#0e1116; }
  .card header { display:flex; align-items:center; gap:.6rem; }
  .card h2 { font-size:1.15rem; margin:0; }
  .rank { color:#0b0e13; font-weight:800; font-size:.72rem; padding:.1rem .45rem; border-radius:5px; }
  .cat { color:#7a8290; font-size:.8rem; }
  .desc { color:#aab2bf; margin:.35rem 0 .7rem; }
  .graph { background:#080a0e; border:1px solid #1b2029; border-radius:8px; padding:.5rem; margin-bottom:.8rem; overflow-x:auto; }
  .cols { display:grid; grid-template-columns: 1fr 1fr; gap:1rem; }
  @media (max-width: 800px){ .cols { grid-template-columns: 1fr; } }
  h3 { font-size:.8rem; text-transform:uppercase; letter-spacing:.04em; color:#7a8290; margin:.2rem 0 .3rem; }
  pre { background:#080a0e; border:1px solid #1b2029; border-radius:8px; padding:.7rem; font-size:11.5px; line-height:1.45; overflow-x:auto; margin:0; white-space:pre; color:#cbd5e1; }
</style>
<div class="wrap">${cards}</div>`;

writeFileSync(outPath, html, "utf8");
console.log(`galerie écrite : ${outPath} (${docs.length} techniques)`);
