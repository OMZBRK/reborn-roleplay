/**
 * Tests du cœur bruit — tourne en node pur, sans Blockbench.
 * Ce qui compte ici : le déterminisme. Un artiste doit pouvoir régénérer une
 * passe de surface et retrouver exactement le même grain.
 */
import assert from 'node:assert/strict';
import { hash2, valueNoise, fbm, surfacePattern, posterize, clamp01, type SurfaceKind } from '../src/core/noise.ts';

let passed = 0;
function test(name: string, fn: () => void): void {
  fn();
  passed++;
  console.log(`  ✓ ${name}`);
}

console.log('noise.ts');

test('hash2 est déterministe', () => {
  assert.equal(hash2(3, 7, 42), hash2(3, 7, 42));
  assert.notEqual(hash2(3, 7, 42), hash2(3, 7, 43));
  assert.notEqual(hash2(3, 7, 42), hash2(4, 7, 42));
});

test('hash2 reste dans [0,1)', () => {
  for (let x = -50; x < 50; x += 7) {
    for (let y = -50; y < 50; y += 11) {
      const v = hash2(x, y, 1);
      assert.ok(v >= 0 && v < 1, `hash2(${x},${y}) = ${v}`);
    }
  }
});

test('valueNoise est continu et borné', () => {
  for (let i = 0; i < 200; i++) {
    const v = valueNoise(i * 0.13, i * 0.29, 7);
    assert.ok(v >= 0 && v <= 1, `valueNoise = ${v}`);
  }
  // Deux points très proches donnent des valeurs proches (pas de discontinuité).
  const a = valueNoise(2.5, 3.5, 7);
  const b = valueNoise(2.5001, 3.5001, 7);
  assert.ok(Math.abs(a - b) < 0.01, `saut de ${Math.abs(a - b)}`);
});

test('valueNoise interpole aux nœuds entiers', () => {
  // Sur un nœud entier, l'interpolation doit rendre exactement le hash.
  assert.ok(Math.abs(valueNoise(4, 9, 3) - hash2(4, 9, 3)) < 1e-9);
});

test('fbm reste borné quel que soit le nombre d’octaves', () => {
  for (const oct of [1, 2, 4, 8]) {
    for (let i = 0; i < 60; i++) {
      const v = fbm(i * 0.37, i * 0.11, 5, oct);
      assert.ok(v >= 0 && v <= 1, `fbm(oct=${oct}) = ${v}`);
    }
  }
});

test('chaque matière reste dans [0,1] et est déterministe', () => {
  const kinds: SurfaceKind[] = ['cloth', 'fur', 'wood', 'stone', 'noise'];
  for (const kind of kinds) {
    for (let i = 0; i < 40; i++) {
      const u = i / 40, v = (i * 7 % 40) / 40;
      const a = surfacePattern(kind, u, v, 8, 1234);
      const b = surfacePattern(kind, u, v, 8, 1234);
      assert.equal(a, b, `${kind} non déterministe`);
      assert.ok(a >= 0 && a <= 1, `${kind} hors bornes : ${a}`);
    }
  }
});

test('changer la graine change le motif', () => {
  const a = surfacePattern('stone', 0.3, 0.4, 8, 1);
  const b = surfacePattern('stone', 0.3, 0.4, 8, 2);
  assert.notEqual(a, b);
});

test('posterize produit exactement le nombre de paliers demandé', () => {
  const seen = new Set<number>();
  for (let i = 0; i <= 100; i++) seen.add(posterize(i / 100, 4));
  assert.equal(seen.size, 4, `paliers obtenus : ${[...seen].join(', ')}`);
  // Les extrêmes sont préservés : pas de perte de contraste.
  assert.equal(posterize(0, 4), 0);
  assert.equal(posterize(1, 4), 1);
});

test('posterize est neutre sous 2 paliers', () => {
  assert.equal(posterize(0.37, 1), 0.37);
});

test('clamp01 borne des deux côtés', () => {
  assert.equal(clamp01(-3), 0);
  assert.equal(clamp01(3), 1);
  assert.equal(clamp01(0.42), 0.42);
});

console.log(`  → ${passed} tests OK\n`);
