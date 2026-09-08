/**
 * Tests des champs scalaires (Gradient / Edges) — node pur, sans Blockbench.
 * Le baking lui-même ne peut pas être testé hors de l'app ; ce qui l'est ici,
 * c'est la géométrie de décision : où est le bord, saillant ou rentrant, et à
 * quelle distance.
 */
import assert from 'node:assert/strict';
import {
  axisFactor, applyFalloff, classifyEdge, edgeFalloff, distanceTransform,
  normalizeV, type V3,
} from '../src/core/field.ts';

let passed = 0;
function test(name: string, fn: () => void): void {
  fn();
  passed++;
  console.log(`  ✓ ${name}`);
}

console.log('field.ts');

test('axisFactor rend 0 au début, 1 à la fin, 0.5 au milieu', () => {
  const a: V3 = [0, 0, 0], b: V3 = [0, 10, 0];
  assert.equal(axisFactor([0, 0, 0], a, b), 0);
  assert.equal(axisFactor([0, 10, 0], a, b), 1);
  assert.equal(axisFactor([0, 5, 0], a, b), 0.5);
});

test('axisFactor borne au-delà des extrémités', () => {
  const a: V3 = [0, 0, 0], b: V3 = [0, 10, 0];
  assert.equal(axisFactor([0, -50, 0], a, b), 0);
  assert.equal(axisFactor([0, 50, 0], a, b), 1);
});

test('axisFactor ignore la composante perpendiculaire', () => {
  const a: V3 = [0, 0, 0], b: V3 = [0, 10, 0];
  // Un point très écarté sur X reste au même niveau sur l'axe Y.
  assert.equal(axisFactor([100, 5, -60], a, b), 0.5);
});

test('axisFactor ne divise pas par zéro sur un axe dégénéré', () => {
  const p: V3 = [1, 2, 3];
  assert.equal(axisFactor(p, [0, 0, 0], [0, 0, 0]), 0);
});

test('les courbes de falloff préservent les extrémités', () => {
  for (const mode of ['linear', 'smooth', 'ease_in', 'ease_out'] as const) {
    assert.equal(applyFalloff(0, mode), 0, mode);
    assert.equal(applyFalloff(1, mode), 1, mode);
  }
});

test('les courbes de falloff sont monotones croissantes', () => {
  for (const mode of ['linear', 'smooth', 'ease_in', 'ease_out'] as const) {
    let prev = -1;
    for (let i = 0; i <= 20; i++) {
      const v = applyFalloff(i / 20, mode);
      assert.ok(v >= prev - 1e-9, `${mode} non monotone en ${i / 20}`);
      prev = v;
    }
  }
});

test('classifyEdge voit plat deux faces coplanaires', () => {
  const n: V3 = [0, 1, 0];
  assert.equal(classifyEdge(n, n, [1, 0, 0]), 'flat');
});

test('classifyEdge distingue saillant et rentrant', () => {
  // Coin extérieur d'un cube : dessus + côté droit, voisin situé à droite.
  // La normale du voisin (+X) « fuit » le sens du décalage → saillant.
  const up: V3 = [0, 1, 0];
  const right: V3 = [1, 0, 0];
  assert.equal(classifyEdge(up, right, [-1, 0, 0]), 'convex');
  // Même paire, décalage inversé → rentrant.
  assert.equal(classifyEdge(up, right, [1, 0, 0]), 'concave');
});

test('le seuil de planéité est respecté', () => {
  const a: V3 = [0, 1, 0];
  const b = normalizeV([0.05, 1, 0]); // ~3° d'écart
  assert.equal(classifyEdge(a, b, [1, 0, 0], 0.98), 'flat');
  // Avec un seuil très strict, la même paire n'est plus « plate ».
  assert.notEqual(classifyEdge(a, b, [1, 0, 0], 0.9999), 'flat');
});

test('edgeFalloff vaut 1 sur le bord et 0 au-delà de la largeur', () => {
  assert.equal(edgeFalloff(0, 3), 1);
  assert.equal(edgeFalloff(3, 3), 0);
  assert.equal(edgeFalloff(10, 3), 0);
  assert.ok(Math.abs(edgeFalloff(1.5, 3) - 0.5) < 1e-9);
});

test('edgeFalloff gère une largeur nulle', () => {
  assert.equal(edgeFalloff(0, 0), 1);
  assert.equal(edgeFalloff(1, 0), 0);
});

test('distanceTransform : 0 sur les graines, croissant en s’éloignant', () => {
  // Bande verticale de graines en x=0 sur une image 5×1.
  const w = 5, h = 1;
  const mask = new Uint8Array(w * h).fill(1);
  mask[0] = 0;
  const d = distanceTransform(mask, w, h);
  assert.equal(d[0], 0);
  for (let x = 1; x < w; x++) {
    assert.ok(d[x] > d[x - 1], `d[${x}]=${d[x]} pas > d[${x - 1}]=${d[x - 1]}`);
    // Le chanfrein 3-4 approche la distance euclidienne à ~10% près.
    assert.ok(Math.abs(d[x] - x) < 0.35, `d[${x}]=${d[x]}, attendu ~${x}`);
  }
});

test('distanceTransform sans aucune graine reste au plafond', () => {
  const d = distanceTransform(new Uint8Array(9).fill(1), 3, 3, 60);
  for (const v of d) assert.ok(v >= 19, `valeur ${v} trop basse`);
});

console.log(`  → ${passed} tests OK\n`);
