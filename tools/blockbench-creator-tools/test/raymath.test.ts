/**
 * Tests du cœur mathématique AO — exécutables sans Blockbench (`pnpm test`).
 * Vérifie l'intersection rayon-triangle, la base orthonormée, l'échantillonnage
 * hémisphérique (toujours dans le bon hémisphère) et un scénario AO complet.
 */
import {
  packTri,
  rayHitsAny,
  orthonormalBasis,
  cosineSampleDir,
  hammersleyPairs,
  computeAO,
  rasterizeTriangle,
  type TriSoup,
} from '../src/core/raymath.ts';

let failures = 0;
function check(name: string, cond: boolean): void {
  if (cond) {
    console.log(`  ok  ${name}`);
  } else {
    failures++;
    console.error(`FAIL  ${name}`);
  }
}
function approx(a: number, b: number, eps = 1e-6): boolean {
  return Math.abs(a - b) < eps;
}

// --- 1. Ray-triangle : un quad à z=1 couvrant [-1,1]² --------------------
const quad: TriSoup = new Float64Array(2 * 9);
packTri(quad, 0, -1, -1, 1, 1, -1, 1, 1, 1, 1);
packTri(quad, 1, -1, -1, 1, 1, 1, 1, -1, 1, 1);

check('rayon +z touche le quad', rayHitsAny(quad, 2, 0, 0, 0, 0, 0, 1, 10));
check('rayon -z manque (derrière)', !rayHitsAny(quad, 2, 0, 0, 0, 0, 0, -1, 10));
check('rayon +x manque', !rayHitsAny(quad, 2, 0, 0, 0, 1, 0, 0, 10));
check('hit hors maxDist ignoré', !rayHitsAny(quad, 2, 0, 0, 0, 0, 0, 1, 0.5));
check('rayon oblique hors du quad manque', !rayHitsAny(quad, 2, 0, 0, 0, 0.9, 0, 0.1, 10));

// --- 2. Base orthonormée : orthonormale + main droite --------------------
for (const raw of [[0, 1, 0], [1, 0, 0], [0, 0, 1], [0.3, 0.6, 0.7]]) {
  // La fonction suppose une normale unitaire → on normalise l'entrée de test.
  const rl = Math.hypot(raw[0], raw[1], raw[2]);
  const n = [raw[0] / rl, raw[1] / rl, raw[2] / rl];
  const [nx, ny, nz] = n;
  const b = orthonormalBasis(nx, ny, nz);
  const dotTN = b.tx * nx + b.ty * ny + b.tz * nz;
  const dotBN = b.bx * nx + b.by * ny + b.bz * nz;
  const dotTB = b.tx * b.bx + b.ty * b.by + b.tz * b.bz;
  const tLen = Math.hypot(b.tx, b.ty, b.tz);
  const bLen = Math.hypot(b.bx, b.by, b.bz);
  check(`base ⟂ n=[${n}]`, approx(dotTN, 0, 1e-9) && approx(dotBN, 0, 1e-9) && approx(dotTB, 0, 1e-9));
  check(`base unitaire n=[${n}]`, approx(tLen, 1, 1e-9) && approx(bLen, 1, 1e-9));
}

// --- 3. Échantillons cosinus toujours dans l'hémisphère (dot n ≥ 0) ------
{
  const n = [0.2, 0.9, 0.3862];
  const nl = Math.hypot(n[0], n[1], n[2]);
  const nn = [n[0] / nl, n[1] / nl, n[2] / nl];
  const basis = orthonormalBasis(nn[0], nn[1], nn[2]);
  const pairs = hammersleyPairs(64);
  const dir = new Float64Array(3);
  let allAbove = true;
  for (let s = 0; s < 64; s++) {
    cosineSampleDir(basis, pairs[s * 2], pairs[s * 2 + 1], dir);
    const d = dir[0] * nn[0] + dir[1] * nn[1] + dir[2] * nn[2];
    const len = Math.hypot(dir[0], dir[1], dir[2]);
    if (d < -1e-9 || !approx(len, 1, 1e-6)) allAbove = false;
  }
  check('échantillons dans l\'hémisphère & unitaires', allAbove);
}

// --- 4. Scénario AO : plancher ouvert (0) vs coin fermé (élevé) ----------
{
  const pairs = hammersleyPairs(128);
  // Plancher seul à y=0, normale +y : rien au-dessus → AO ≈ 0.
  const floor: TriSoup = new Float64Array(2 * 9);
  packTri(floor, 0, -8, 0, -8, 8, 0, -8, 8, 0, 8);
  packTri(floor, 1, -8, 0, -8, 8, 0, 8, -8, 0, 8);
  const aoOpen = computeAO(floor, 2, 0, 0, 0, 0, 1, 0, pairs, 128, 8);
  check('plancher dégagé → AO ~0', aoOpen < 0.02);

  // Ajoute deux murs formant un coin serré autour de l'origine → AO élevé.
  const corner: TriSoup = new Float64Array(6 * 9);
  corner.set(floor.subarray(0, 18), 0);
  // Mur à x=0.5 (normale -x), couvre y,z
  packTri(corner, 2, 0.5, -8, -8, 0.5, 8, -8, 0.5, 8, 8);
  packTri(corner, 3, 0.5, -8, -8, 0.5, 8, 8, 0.5, -8, 8);
  // Mur à z=0.5 (normale -z)
  packTri(corner, 4, -8, -8, 0.5, 8, -8, 0.5, 8, 8, 0.5);
  packTri(corner, 5, -8, -8, 0.5, 8, 8, 0.5, -8, 8, 0.5);
  const aoCorner = computeAO(corner, 6, 0, 0, 0, 0, 1, 0, pairs, 128, 8);
  check('coin fermé → AO nettement > plancher', aoCorner > aoOpen + 0.2);
}

// --- 5. Rasterisation : couvre le bon nombre de texels + interpole -------
{
  // Triangle UV (0,0)-(4,0)-(0,4) sur une texture 8×8 → ~8 pixels (moitié d'un 4×4).
  let count = 0;
  let sample: number[] | null = null;
  rasterizeTriangle(
    0, 0, 4, 0, 0, 4,
    0, 0, 0, // world @ (0,0)
    4, 0, 0, // world @ (4,0)  → x suit u
    0, 4, 0, // world @ (0,4)  → y suit v
    8, 8,
    (px, py, wx, wy, wz) => {
      count++;
      if (px === 1 && py === 1) sample = [wx, wy, wz];
    },
  );
  check('rasterise ~la moitié d\'un carré 4×4', count >= 6 && count <= 10);
  check('interpolation monde au texel (1,1)', sample !== null && approx(sample![0], 1.5) && approx(sample![1], 1.5) && approx(sample![2], 0));
}

if (failures > 0) {
  console.error(`\n${failures} test(s) en échec`);
  process.exit(1);
}
console.log('\nTous les tests raymath passent ✓');
