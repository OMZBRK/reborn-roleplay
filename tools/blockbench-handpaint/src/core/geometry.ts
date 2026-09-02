/**
 * Extraction de la géométrie Blockbench pour le baking.
 *
 *  - `collectOccluders()` : la soupe de triangles (monde) qui bloque les rayons —
 *    toutes les faces de tous les Cubes + Meshes.
 *  - `forEachTexel()` : pour une texture cible, énumère chaque texel couvert par
 *    une face utilisant cette texture, avec sa position monde + normale sortante.
 *
 * Repère : unités Minecraft (16 u = 1 bloc), monde = coords globales.
 *
 * Notes de fiabilité :
 *  - Chemin **Mesh** : UV par sommet documentées (`face.uv[vkey]`) → robuste.
 *  - Chemin **Cube** : rectangle UV + rotation → correspondance coin↔UV en
 *    best-effort (cf CUBE_UV_ORDER). À revérifier visuellement sur un vrai
 *    modèle ; un décalage se corrige en ajustant l'ordre/rotation ici.
 *  - Hypothèse v1 : le modèle utilise une seule texture (on ombre toutes les
 *    faces de la texture sélectionnée). Multi-texture = évolution.
 */
import { packTri, type TriSoup } from './raymath.ts';

type V3 = [number, number, number];

/** Callback par texel : coord pixel + position monde + normale sortante unitaire. */
export type TexelCb = (
  px: number, py: number,
  wx: number, wy: number, wz: number,
  nx: number, ny: number, nz: number,
) => void;

function toV3(p: unknown): V3 {
  const a = p as { x?: number; y?: number; z?: number } & number[];
  return Array.isArray(p) ? [p[0], p[1], p[2]] : [a.x!, a.y!, a.z!];
}

function sub(a: V3, b: V3): V3 { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }
function cross(a: V3, b: V3): V3 {
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}
function normalize(a: V3): V3 {
  const l = Math.hypot(a[0], a[1], a[2]) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
}
function centroid(pts: V3[]): V3 {
  const c: V3 = [0, 0, 0];
  for (const p of pts) { c[0] += p[0]; c[1] += p[1]; c[2] += p[2]; }
  return [c[0] / pts.length, c[1] / pts.length, c[2] / pts.length];
}

/** Normale d'une face, réorientée vers l'extérieur via le centre de l'élément. */
function outwardNormal(corners: V3[], elementCenter: V3): V3 {
  const n = normalize(cross(sub(corners[1], corners[0]), sub(corners[2], corners[0])));
  const fc = centroid(corners);
  const outward: V3 = sub(fc, elementCenter);
  if (n[0] * outward[0] + n[1] * outward[1] + n[2] * outward[2] < 0) {
    return [-n[0], -n[1], -n[2]];
  }
  return n;
}

// --- Accès géométrie par élément -----------------------------------------

const CUBE_FACE_KEYS = ['north', 'south', 'east', 'west', 'up', 'down'] as const;

interface FaceGeom {
  world: V3[];      // 3 ou 4 coins monde
  uv: V3[];         // mêmes indices, [u,v,0] en unités UV (z ignoré)
  normal: V3;
}

/** Coins monde d'une face de cube via getGlobalVertexPositions + getVertexIndices. */
function cubeFaceWorld(cube: any, face: any): V3[] | null {
  const gv = cube.getGlobalVertexPositions?.();
  const idx = face.getVertexIndices?.();
  if (!gv || !idx) return null;
  return idx.map((i: number) => toV3(gv[i]));
}

/**
 * Coins UV d'une face de cube. `face.uv = [x1,y1,x2,y2]` en unités UV ; on les
 * associe aux 4 coins monde (ordre de getVertexIndices) puis on applique la
 * rotation. Best-effort — voir la note de fiabilité en tête de fichier.
 */
function cubeFaceUV(face: any): V3[] {
  const [x1, y1, x2, y2] = face.uv as [number, number, number, number];
  // Ordre de base supposé aligné sur getVertexIndices.
  let corners: V3[] = [
    [x1, y1, 0],
    [x2, y1, 0],
    [x2, y2, 0],
    [x1, y2, 0],
  ];
  const steps = ((face.rotation || 0) / 90) % 4;
  for (let s = 0; s < steps; s++) corners = [corners[3], corners[0], corners[1], corners[2]];
  return corners;
}

/** Itère les faces d'un élément (cube ou mesh) en fournissant monde + uv + normale.
 *  `targetTexture` : si non-null, ne rend que les faces sur cette texture. */
function forEachElementFace(
  element: any,
  targetTexture: Texture | null,
  cb: (fg: FaceGeom) => void,
): void {
  const isMesh = element.faces && !CUBE_FACE_KEYS.some((k) => element.faces[k]);

  if (!isMesh && element.getGlobalVertexPositions) {
    // --- Cube ---
    const gv = element.getGlobalVertexPositions();
    const center = centroid(gv.map(toV3));
    for (const key of CUBE_FACE_KEYS) {
      const face = element.faces[key];
      if (!face) continue;
      if (targetTexture && face.getTexture?.() !== targetTexture) continue;
      const world = cubeFaceWorld(element, face);
      if (!world || world.length < 3) continue;
      cb({ world, uv: cubeFaceUV(face), normal: outwardNormal(world, center) });
    }
    return;
  }

  // --- Mesh ---
  const obj = element.mesh;
  obj?.updateMatrixWorld?.(true);
  // THREE est un global UMD injecté par Blockbench ; on y accède via globalThis
  // pour éviter la dépendance de type (le paquet `three` n'est pas installé).
  const Three = (globalThis as any).THREE;
  const worldOf = (vkey: string): V3 => {
    const v = element.vertices[vkey];
    const p = new Three.Vector3(v[0], v[1], v[2]);
    obj.localToWorld(p);
    return [p.x, p.y, p.z];
  };
  // Centre monde de l'élément (moyenne des sommets).
  const allKeys = Object.keys(element.vertices || {});
  const center = allKeys.length ? centroid(allKeys.map(worldOf)) : [0, 0, 0] as V3;

  for (const fkey in element.faces) {
    const face = element.faces[fkey];
    const vkeys: string[] = face.getSortedVertices ? face.getSortedVertices() : face.vertices;
    if (!vkeys || vkeys.length < 3) continue;
    if (targetTexture && face.getTexture?.() !== targetTexture) continue;
    const world = vkeys.map(worldOf);
    const uv: V3[] = vkeys.map((k) => {
      const t = face.uv?.[k] ?? [0, 0];
      return [t[0], t[1], 0] as V3;
    });
    cb({ world, uv, normal: outwardNormal(world, center) });
  }
}

function allElements(): any[] {
  const cubes = (typeof Cube !== 'undefined' && (Cube as any).all) || [];
  const meshes = (typeof Mesh !== 'undefined' && (Mesh as any).all) || [];
  return [...cubes, ...meshes];
}

// --- API publique ---------------------------------------------------------

/** Soupe de triangles monde de toute la géométrie (occludeurs). */
export function collectOccluders(): { soup: TriSoup; triCount: number } {
  const raw: number[] = []; // 9 coords (a,b,c) par triangle
  for (const el of allElements()) {
    forEachElementFace(el, null, ({ world }) => {
      // Triangulation en éventail (fan) : (0,i,i+1).
      for (let i = 1; i + 1 < world.length; i++) {
        const a = world[0], b = world[i], c = world[i + 1];
        raw.push(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]);
      }
    });
  }
  const triCount = raw.length / 9;
  const soup = new Float64Array(triCount * 9);
  for (let i = 0; i < triCount; i++) {
    const o = i * 9;
    packTri(
      soup, i,
      raw[o], raw[o + 1], raw[o + 2],
      raw[o + 3], raw[o + 4], raw[o + 5],
      raw[o + 6], raw[o + 7], raw[o + 8],
    );
  }
  return { soup, triCount };
}

/**
 * Énumère chaque texel couvert par une face de `texture`, avec position monde
 * (interpolée) et normale sortante de la face. Retourne le nombre de texels.
 */
export function forEachTexel(texture: Texture, cb: TexelCb): number {
  // Densité UV → pixels (gère box-uv comme per-face-uv).
  const dx = texture.width / (texture.uv_width || texture.width);
  const dy = texture.height / (texture.uv_height || texture.height);
  let count = 0;

  // Rasterisation barycentrique inline (partagée avec raymath en tests).
  const rasterize = (
    p0: V3, p1: V3, p2: V3, // uv (unités UV)
    w0: V3, w1: V3, w2: V3, // monde
    n: V3,
  ): void => {
    const x0 = p0[0] * dx, y0 = p0[1] * dy;
    const x1 = p1[0] * dx, y1 = p1[1] * dy;
    const x2 = p2[0] * dx, y2 = p2[1] * dy;
    const area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    if (Math.abs(area) < 1e-9) return;
    const inv = 1 / area;
    const minX = Math.max(0, Math.floor(Math.min(x0, x1, x2)));
    const maxX = Math.min(texture.width - 1, Math.ceil(Math.max(x0, x1, x2)));
    const minY = Math.max(0, Math.floor(Math.min(y0, y1, y2)));
    const maxY = Math.min(texture.height - 1, Math.ceil(Math.max(y0, y1, y2)));
    const E = 1e-6;
    for (let py = minY; py <= maxY; py++) {
      const cy = py + 0.5;
      for (let px = minX; px <= maxX; px++) {
        const cx = px + 0.5;
        const l0 = ((x1 - cx) * (y2 - cy) - (y1 - cy) * (x2 - cx)) * inv;
        const l1 = ((x2 - cx) * (y0 - cy) - (y2 - cy) * (x0 - cx)) * inv;
        const l2 = 1 - l0 - l1;
        if (l0 < -E || l1 < -E || l2 < -E) continue;
        cb(
          px, py,
          l0 * w0[0] + l1 * w1[0] + l2 * w2[0],
          l0 * w0[1] + l1 * w1[1] + l2 * w2[1],
          l0 * w0[2] + l1 * w1[2] + l2 * w2[2],
          n[0], n[1], n[2],
        );
        count++;
      }
    }
  };

  for (const el of allElements()) {
    forEachElementFace(el, texture, ({ world, uv, normal }) => {
      for (let i = 1; i + 1 < world.length; i++) {
        rasterize(uv[0], uv[i], uv[i + 1], world[0], world[i], world[i + 1], normal);
      }
    });
  }
  return count;
}
