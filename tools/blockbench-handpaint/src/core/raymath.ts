/**
 * Cœur mathématique du baking AO — 100% pur (aucune dépendance Blockbench),
 * donc testable en node. Tout est écrit en scalaires sans allocation dans les
 * boucles chaudes (des millions d'itérations rayon×triangle par bake).
 *
 * Repère : coordonnées "unités Minecraft" de Blockbench (16 u = 1 bloc).
 */

/** 9 floats par triangle : [ax,ay,az, e1x,e1y,e1z, e2x,e2y,e2z] où e1=b-a, e2=c-a. */
export type TriSoup = Float64Array;

/** Empile un triangle (a,b,c) dans le buffer plat à l'offset `tri` (index de triangle). */
export function packTri(
  soup: TriSoup,
  tri: number,
  ax: number, ay: number, az: number,
  bx: number, by: number, bz: number,
  cx: number, cy: number, cz: number,
): void {
  const o = tri * 9;
  soup[o] = ax; soup[o + 1] = ay; soup[o + 2] = az;
  soup[o + 3] = bx - ax; soup[o + 4] = by - ay; soup[o + 5] = bz - az;
  soup[o + 6] = cx - ax; soup[o + 7] = cy - ay; soup[o + 8] = cz - az;
}

/**
 * Vrai si le rayon (origin, dir normalisé) touche un triangle de la soupe à une
 * distance dans (EPS, maxDist). Möller–Trumbore, double face (pas de culling).
 */
export function rayHitsAny(
  soup: TriSoup,
  triCount: number,
  ox: number, oy: number, oz: number,
  dx: number, dy: number, dz: number,
  maxDist: number,
): boolean {
  const EPS = 1e-6;
  for (let i = 0; i < triCount; i++) {
    const o = i * 9;
    const ax = soup[o], ay = soup[o + 1], az = soup[o + 2];
    const e1x = soup[o + 3], e1y = soup[o + 4], e1z = soup[o + 5];
    const e2x = soup[o + 6], e2y = soup[o + 7], e2z = soup[o + 8];
    // p = dir × e2
    const px = dy * e2z - dz * e2y;
    const py = dz * e2x - dx * e2z;
    const pz = dx * e2y - dy * e2x;
    const det = e1x * px + e1y * py + e1z * pz;
    if (det > -EPS && det < EPS) continue; // rayon parallèle au triangle
    const inv = 1 / det;
    const tx = ox - ax, ty = oy - ay, tz = oz - az;
    const u = (tx * px + ty * py + tz * pz) * inv;
    if (u < 0 || u > 1) continue;
    const qx = ty * e1z - tz * e1y;
    const qy = tz * e1x - tx * e1z;
    const qz = tx * e1y - ty * e1x;
    const v = (dx * qx + dy * qy + dz * qz) * inv;
    if (v < 0 || u + v > 1) continue;
    const t = (e2x * qx + e2y * qy + e2z * qz) * inv;
    if (t > EPS && t < maxDist) return true;
  }
  return false;
}

/** Inverse binaire radical (base 2) de `i`, dans [0,1). */
function radicalInverse2(i: number): number {
  let bits = i >>> 0;
  bits = (bits << 16) | (bits >>> 16);
  bits = ((bits & 0x55555555) << 1) | ((bits & 0xaaaaaaaa) >>> 1);
  bits = ((bits & 0x33333333) << 2) | ((bits & 0xcccccccc) >>> 2);
  bits = ((bits & 0x0f0f0f0f) << 4) | ((bits & 0xf0f0f0f0) >>> 4);
  bits = ((bits & 0x00ff00ff) << 8) | ((bits & 0xff00ff00) >>> 8);
  return (bits >>> 0) * 2.3283064365386963e-10; // / 2^32
}

/**
 * Suite de Hammersley déterministe (mêmes échantillons à chaque bake → résultat
 * reproductible). Renvoie un Float64Array plat de `n` paires [xi1,xi2].
 */
export function hammersleyPairs(n: number): Float64Array {
  const out = new Float64Array(n * 2);
  for (let i = 0; i < n; i++) {
    out[i * 2] = (i + 0.5) / n;
    out[i * 2 + 1] = radicalInverse2(i);
  }
  return out;
}

/** Base orthonormée (tangent, bitangent) autour d'une normale unitaire. */
export interface Basis {
  tx: number; ty: number; tz: number;
  bx: number; by: number; bz: number;
  nx: number; ny: number; nz: number;
}

export function orthonormalBasis(nx: number, ny: number, nz: number): Basis {
  // Axe d'aide non colinéaire à n, puis t = normalize(help × n).
  let tx: number, ty: number, tz: number;
  if (Math.abs(nx) > 0.9) {
    // help = (0,1,0) → help × n = (nz, 0, -nx)
    tx = nz; ty = 0; tz = -nx;
  } else {
    // help = (1,0,0) → help × n = (0, -nz, ny)
    tx = 0; ty = -nz; tz = ny;
  }
  const tl = Math.hypot(tx, ty, tz) || 1;
  tx /= tl; ty /= tl; tz /= tl;
  // b = n × t (déjà unitaire car n et t orthonormés)
  const bx = ny * tz - nz * ty;
  const by = nz * tx - nx * tz;
  const bz = nx * ty - ny * tx;
  return { tx, ty, tz, bx, by, bz, nx, ny, nz };
}

/**
 * Direction échantillon en cosinus-pondéré (hémisphère autour de la normale),
 * à partir d'une paire de Hammersley (xi1,xi2). Écrit dans `out` [dx,dy,dz].
 */
export function cosineSampleDir(
  basis: Basis,
  xi1: number,
  xi2: number,
  out: Float64Array,
): void {
  const r = Math.sqrt(xi1);
  const phi = 2 * Math.PI * xi2;
  const lx = r * Math.cos(phi);
  const ly = r * Math.sin(phi);
  const lz = Math.sqrt(Math.max(0, 1 - xi1));
  out[0] = basis.tx * lx + basis.bx * ly + basis.nx * lz;
  out[1] = basis.ty * lx + basis.by * ly + basis.ny * lz;
  out[2] = basis.tz * lx + basis.bz * ly + basis.nz * lz;
}

/**
 * Occlusion ambiante en un point (0 = totalement dégagé, 1 = totalement occlus).
 * Lance `samples` rayons cosinus-pondérés et compte ceux qui touchent la soupe
 * dans le rayon `maxDist`. L'origine est décalée le long de la normale pour
 * éviter l'auto-intersection sur la face courante.
 */
export function computeAO(
  soup: TriSoup,
  triCount: number,
  wx: number, wy: number, wz: number,
  nx: number, ny: number, nz: number,
  pairs: Float64Array,
  samples: number,
  maxDist: number,
): number {
  const basis = orthonormalBasis(nx, ny, nz);
  const OFFSET = 0.02; // décalage anti auto-hit (unités MC)
  const ox = wx + nx * OFFSET;
  const oy = wy + ny * OFFSET;
  const oz = wz + nz * OFFSET;
  const dir = new Float64Array(3);
  let hits = 0;
  for (let s = 0; s < samples; s++) {
    cosineSampleDir(basis, pairs[s * 2], pairs[s * 2 + 1], dir);
    if (rayHitsAny(soup, triCount, ox, oy, oz, dir[0], dir[1], dir[2], maxDist)) {
      hits++;
    }
  }
  return hits / samples;
}

/**
 * Rasterise un triangle en espace texel (coords px) et appelle `cb` pour chaque
 * pixel dont le centre est dans le triangle, avec la position monde interpolée
 * (barycentrique). Bornée à [0..W-1]×[0..H-1].
 */
export function rasterizeTriangle(
  x0: number, y0: number, x1: number, y1: number, x2: number, y2: number,
  w0x: number, w0y: number, w0z: number,
  w1x: number, w1y: number, w1z: number,
  w2x: number, w2y: number, w2z: number,
  width: number, height: number,
  cb: (px: number, py: number, wx: number, wy: number, wz: number) => void,
): void {
  const area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
  if (Math.abs(area) < 1e-9) return; // UV dégénéré
  const invArea = 1 / area;
  const minX = Math.max(0, Math.floor(Math.min(x0, x1, x2)));
  const maxX = Math.min(width - 1, Math.ceil(Math.max(x0, x1, x2)));
  const minY = Math.max(0, Math.floor(Math.min(y0, y1, y2)));
  const maxY = Math.min(height - 1, Math.ceil(Math.max(y0, y1, y2)));
  const E = 1e-6;
  for (let py = minY; py <= maxY; py++) {
    const cy = py + 0.5;
    for (let px = minX; px <= maxX; px++) {
      const cx = px + 0.5;
      // Coords barycentriques du centre du pixel.
      const l0 = ((x1 - cx) * (y2 - cy) - (y1 - cy) * (x2 - cx)) * invArea;
      const l1 = ((x2 - cx) * (y0 - cy) - (y2 - cy) * (x0 - cx)) * invArea;
      const l2 = 1 - l0 - l1;
      if (l0 < -E || l1 < -E || l2 < -E) continue;
      cb(
        px, py,
        l0 * w0x + l1 * w1x + l2 * w2x,
        l0 * w0y + l1 * w1y + l2 * w2y,
        l0 * w0z + l1 * w1z + l2 * w2z,
      );
    }
  }
}
