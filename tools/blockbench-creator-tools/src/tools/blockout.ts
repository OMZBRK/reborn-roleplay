/**
 * Blockout Canvas — « block, chain, pose, and reuse ».
 *
 * Les quatre gestes du blocking, ceux qu'on refait sur chaque modèle :
 *
 *  - **Block**  : poser une primitive propre, calée sur la grille, sans aller
 *                 corriger six champs numériques après coup.
 *  - **Chain**  : répéter un élément le long d'un axe avec décalage, rotation et
 *                 échelle progressifs. C'est ce qui fabrique une queue, une
 *                 corde, une crête, une rangée de piquants — vingt duplications
 *                 manuelles remplacées par un réglage.
 *  - **Pose**   : symétriser une sélection, recentrer un pivot. Le pivot mal
 *                 placé est LA cause des rotations qui partent de travers.
 *  - **Reuse**  : mettre une sélection en bibliothèque et la ré-insérer plus
 *                 tard, dans n'importe quel projet.
 *
 * Tout passe par Undo.initEdit/finishEdit : chaque action reste annulable d'un
 * Ctrl+Z, comme n'importe quelle opération native.
 */
import { buildChain, mirrorVector, mirrorRotation, type ChainOptions, type V3 } from '../core/chain.ts';

const LIBRARY_KEY = 'reborn_blockout_library';

type Result = { ok: boolean; message: string };

function selectedElements(): any[] {
  const O = (globalThis as any).Outliner;
  const sel = O?.selected ?? [];
  return Array.isArray(sel) ? sel.filter((e: any) => e && e.type !== 'group') : [];
}

function requireProject(): string | null {
  const P = (globalThis as any).Project;
  if (!P) return "Aucun projet ouvert — crée ou ouvre un modèle d'abord.";
  const F = (globalThis as any).Format;
  if (F && F.id === 'skin') {
    return 'Le format Skin ne permet pas d’ajouter de la géométrie.';
  }
  return null;
}

// --- Block ----------------------------------------------------------------

export interface BlockOptions {
  /** Taille en unités Minecraft (16 = un bloc). */
  size: V3;
  /** Pas de grille auquel caler la position (0 = pas de calage). */
  grid: number;
  /** Position : centre de la vue, ou origine du monde. */
  at: 'origin' | 'selection';
  name: string;
}

function snap(v: number, grid: number): number {
  return grid > 0 ? Math.round(v / grid) * grid : v;
}

/** Centre de la sélection courante, ou [0,0,0]. */
function selectionCenter(): V3 {
  const els = selectedElements();
  if (!els.length) return [0, 0, 0];
  const min: V3 = [Infinity, Infinity, Infinity];
  const max: V3 = [-Infinity, -Infinity, -Infinity];
  let seen = false;
  for (const el of els) {
    const f = el.from, t = el.to;
    if (!f || !t) continue;
    for (let i = 0; i < 3; i++) {
      min[i] = Math.min(min[i], f[i], t[i]);
      max[i] = Math.max(max[i], f[i], t[i]);
    }
    seen = true;
  }
  if (!seen) return [0, 0, 0];
  return [(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2];
}

export function addBlock(opts: BlockOptions): Result {
  const err = requireProject();
  if (err) return { ok: false, message: err };

  const C = (globalThis as any).Cube;
  if (!C) return { ok: false, message: 'API Cube indisponible dans ce format.' };

  const c = opts.at === 'selection' ? selectionCenter() : ([0, 0, 0] as V3);
  const half: V3 = [opts.size[0] / 2, opts.size[1] / 2, opts.size[2] / 2];
  const from: V3 = [
    snap(c[0] - half[0], opts.grid),
    snap(c[1] - half[1], opts.grid),
    snap(c[2] - half[2], opts.grid),
  ];
  const to: V3 = [
    from[0] + opts.size[0],
    from[1] + opts.size[1],
    from[2] + opts.size[2],
  ];

  (globalThis as any).Undo.initEdit({ outliner: true, elements: [], selection: true });
  const cube = new C({
    name: opts.name || 'block',
    from,
    to,
    origin: [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2, (from[2] + to[2]) / 2],
    autouv: 0,
  }).init();
  (globalThis as any).Undo.finishEdit('Blockout — nouveau bloc', { outliner: true, elements: [cube], selection: true });

  return { ok: true, message: `Bloc « ${cube.name} » créé (${opts.size.join('×')}).` };
}

// --- Chain ----------------------------------------------------------------

export function chainSelection(opts: ChainOptions): Result {
  const err = requireProject();
  if (err) return { ok: false, message: err };

  const source = selectedElements();
  if (!source.length) {
    return { ok: false, message: 'Sélectionne au moins un élément à répéter.' };
  }
  const steps = buildChain(opts);
  if (!steps.length) return { ok: false, message: 'Nombre de copies nul.' };

  const C = (globalThis as any).Cube;
  const Undo = (globalThis as any).Undo;
  const created: any[] = [];

  Undo.initEdit({ outliner: true, elements: [], selection: true });
  try {
    for (const step of steps) {
      for (const el of source) {
        if (!el.from || !el.to) continue; // meshes non gérés en v1

        // Mise à l'échelle autour du centre de l'élément source, pour que la
        // chaîne se rétrécisse « en place » plutôt que de dériver.
        const cx = (el.from[0] + el.to[0]) / 2;
        const cy = (el.from[1] + el.to[1]) / 2;
        const cz = (el.from[2] + el.to[2]) / 2;
        const scaleAbout = (p: number[], c: number): number[] =>
          p.map((v, i) => c + (v - [cx, cy, cz][i]) * step.scale);

        const from = scaleAbout(el.from, 0).map((v, i) => v + step.offset[i]);
        const to = scaleAbout(el.to, 0).map((v, i) => v + step.offset[i]);

        const copy = new C({
          name: `${el.name}_${step.index}`,
          from,
          to,
          origin: [
            (el.origin?.[0] ?? cx) + step.offset[0],
            (el.origin?.[1] ?? cy) + step.offset[1],
            (el.origin?.[2] ?? cz) + step.offset[2],
          ],
          rotation: [
            (el.rotation?.[0] ?? 0) + step.rotation[0],
            (el.rotation?.[1] ?? 0) + step.rotation[1],
            (el.rotation?.[2] ?? 0) + step.rotation[2],
          ],
          uv_offset: el.uv_offset ? [...el.uv_offset] : undefined,
          autouv: el.autouv ?? 0,
        }).init();

        // On recopie les faces pour garder le mapping UV de la source : sans
        // ça, chaque copie repart en UV automatique et perd sa texture.
        if (el.faces && copy.faces) {
          for (const key in el.faces) {
            const src = el.faces[key];
            const dst = copy.faces[key];
            if (!src || !dst) continue;
            if (src.uv) dst.uv = [...src.uv];
            if (src.texture !== undefined) dst.texture = src.texture;
            if (src.rotation !== undefined) dst.rotation = src.rotation;
          }
        }
        created.push(copy);
      }
    }
  } catch (e) {
    Undo.finishEdit('Blockout — chaîne (échec)', { outliner: true, elements: created, selection: true });
    return { ok: false, message: `Échec pendant la duplication : ${String(e)}` };
  }
  Undo.finishEdit('Blockout — chaîne', { outliner: true, elements: created, selection: true });

  (globalThis as any).Canvas?.updateAll?.();
  return {
    ok: true,
    message: `${created.length} copie(s) créée(s) depuis ${source.length} élément(s), mode ${opts.mode === 'cumulative' ? 'enroulé' : 'droit'}.`,
  };
}

// --- Pose -----------------------------------------------------------------

export function mirrorSelection(axis: 'x' | 'y' | 'z'): Result {
  const err = requireProject();
  if (err) return { ok: false, message: err };

  const source = selectedElements().filter((e) => e.from && e.to);
  if (!source.length) return { ok: false, message: 'Sélectionne au moins un cube.' };

  const C = (globalThis as any).Cube;
  const Undo = (globalThis as any).Undo;
  const i = axis === 'x' ? 0 : axis === 'y' ? 1 : 2;
  const created: any[] = [];

  Undo.initEdit({ outliner: true, elements: [], selection: true });
  for (const el of source) {
    const from = [...el.from];
    const to = [...el.to];
    // Le miroir inverse l'ordre sur l'axe : sans le re-trier, from > to et le
    // cube devient invisible (volume négatif).
    const a = -to[i], b = -from[i];
    from[i] = Math.min(a, b);
    to[i] = Math.max(a, b);

    const copy = new C({
      name: `${el.name}_mirror`,
      from,
      to,
      origin: mirrorVector((el.origin ?? [0, 0, 0]) as V3, axis),
      rotation: mirrorRotation((el.rotation ?? [0, 0, 0]) as V3, axis),
      autouv: el.autouv ?? 0,
    }).init();
    if (el.faces && copy.faces) {
      for (const key in el.faces) {
        const src = el.faces[key], dst = copy.faces[key];
        if (src?.uv && dst) dst.uv = [...src.uv];
        if (src && dst && src.texture !== undefined) dst.texture = src.texture;
      }
    }
    created.push(copy);
  }
  Undo.finishEdit('Blockout — miroir', { outliner: true, elements: created, selection: true });
  (globalThis as any).Canvas?.updateAll?.();

  return { ok: true, message: `${created.length} copie(s) symétrisée(s) sur ${axis.toUpperCase()}.` };
}

export function centerPivots(): Result {
  const source = selectedElements().filter((e) => e.from && e.to);
  if (!source.length) return { ok: false, message: 'Sélectionne au moins un cube.' };

  const Undo = (globalThis as any).Undo;
  Undo.initEdit({ elements: source });
  for (const el of source) {
    el.origin = [
      (el.from[0] + el.to[0]) / 2,
      (el.from[1] + el.to[1]) / 2,
      (el.from[2] + el.to[2]) / 2,
    ];
  }
  Undo.finishEdit('Blockout — pivots recentrés', { elements: source });
  (globalThis as any).Canvas?.updateAll?.();

  return { ok: true, message: `${source.length} pivot(s) recentré(s).` };
}

// --- Reuse ----------------------------------------------------------------

interface StoredPart {
  name: string;
  savedAt: string;
  cubes: Array<{
    name: string;
    from: number[];
    to: number[];
    origin: number[];
    rotation: number[];
    faces?: Record<string, { uv?: number[]; rotation?: number }>;
  }>;
}

function readLibrary(): StoredPart[] {
  try {
    const raw = localStorage.getItem(LIBRARY_KEY);
    return raw ? (JSON.parse(raw) as StoredPart[]) : [];
  } catch {
    return [];
  }
}

function writeLibrary(parts: StoredPart[]): void {
  localStorage.setItem(LIBRARY_KEY, JSON.stringify(parts));
}

export function listParts(): StoredPart[] {
  return readLibrary();
}

export function savePart(name: string): Result {
  const source = selectedElements().filter((e) => e.from && e.to);
  if (!source.length) return { ok: false, message: 'Sélectionne au moins un cube à enregistrer.' };
  const clean = (name || '').trim();
  if (!clean) return { ok: false, message: 'Donne un nom à la pièce.' };

  // On normalise sur le centre de la sélection : la pièce se ré-insère là où
  // l'artiste le demande, pas là où elle a été dessinée.
  const c = selectionCenter();
  const part: StoredPart = {
    name: clean,
    savedAt: new Date().toISOString(),
    cubes: source.map((el) => ({
      name: el.name,
      from: el.from.map((v: number, i: number) => v - c[i]),
      to: el.to.map((v: number, i: number) => v - c[i]),
      origin: (el.origin ?? [0, 0, 0]).map((v: number, i: number) => v - c[i]),
      rotation: [...(el.rotation ?? [0, 0, 0])],
      faces: el.faces
        ? Object.fromEntries(
            Object.entries(el.faces).map(([k, f]: [string, any]) => [
              k,
              { uv: f?.uv ? [...f.uv] : undefined, rotation: f?.rotation },
            ]),
          )
        : undefined,
    })),
  };

  const lib = readLibrary().filter((p) => p.name !== clean);
  lib.push(part);
  writeLibrary(lib);
  return { ok: true, message: `Pièce « ${clean} » enregistrée (${part.cubes.length} cube(s)).` };
}

export function insertPart(name: string): Result {
  const err = requireProject();
  if (err) return { ok: false, message: err };

  const part = readLibrary().find((p) => p.name === name);
  if (!part) return { ok: false, message: `Pièce « ${name} » introuvable.` };

  const C = (globalThis as any).Cube;
  const Undo = (globalThis as any).Undo;
  const c = selectionCenter();
  const created: any[] = [];

  Undo.initEdit({ outliner: true, elements: [], selection: true });
  for (const cube of part.cubes) {
    const copy = new C({
      name: cube.name,
      from: cube.from.map((v, i) => v + c[i]),
      to: cube.to.map((v, i) => v + c[i]),
      origin: cube.origin.map((v, i) => v + c[i]),
      rotation: [...cube.rotation],
      autouv: 0,
    }).init();
    if (cube.faces && copy.faces) {
      for (const [k, f] of Object.entries(cube.faces)) {
        const dst = copy.faces[k];
        if (dst && f?.uv) dst.uv = [...f.uv];
        if (dst && f?.rotation !== undefined) dst.rotation = f.rotation;
      }
    }
    created.push(copy);
  }
  Undo.finishEdit('Blockout — pièce insérée', { outliner: true, elements: created, selection: true });
  (globalThis as any).Canvas?.updateAll?.();

  return { ok: true, message: `Pièce « ${name} » insérée (${created.length} cube(s)).` };
}

export function deletePart(name: string): Result {
  const lib = readLibrary();
  const next = lib.filter((p) => p.name !== name);
  if (next.length === lib.length) return { ok: false, message: `Pièce « ${name} » introuvable.` };
  writeLibrary(next);
  return { ok: true, message: `Pièce « ${name} » supprimée de la bibliothèque.` };
}
