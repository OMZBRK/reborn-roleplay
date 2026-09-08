/**
 * Tests du calcul de chaîne (Blockout Canvas) — node pur, sans Blockbench.
 * On vérifie ce qui casse silencieusement en usage réel : l'accumulation des
 * rotations, l'atténuation, et les inversions de signe du miroir.
 */
import assert from 'node:assert/strict';
import { buildChain, mirrorVector, mirrorRotation, type ChainOptions, type V3 } from '../src/core/chain.ts';

let passed = 0;
function test(name: string, fn: () => void): void {
  fn();
  passed++;
  console.log(`  ✓ ${name}`);
}

const base: ChainOptions = {
  count: 4,
  step: [0, -4, 0],
  stepRotation: [0, 0, 0],
  stepScale: 1,
  mode: 'additive',
  falloff: 0,
};

console.log('chain.ts');

test('produit exactement le nombre de copies demandé', () => {
  assert.equal(buildChain({ ...base, count: 7 }).length, 7);
  assert.equal(buildChain({ ...base, count: 0 }).length, 0);
  assert.equal(buildChain({ ...base, count: -3 }).length, 0);
});

test('mode additif : décalage régulier', () => {
  const steps = buildChain(base);
  assert.deepEqual(steps[0].offset, [0, -4, 0]);
  assert.deepEqual(steps[1].offset, [0, -8, 0]);
  assert.deepEqual(steps[3].offset, [0, -16, 0]);
});

test('les index commencent à 1 (0 = l’original, non produit)', () => {
  const steps = buildChain(base);
  assert.deepEqual(steps.map((s) => s.index), [1, 2, 3, 4]);
});

test('les rotations s’accumulent', () => {
  const steps = buildChain({ ...base, stepRotation: [0, 0, 10] });
  assert.ok(Math.abs(steps[0].rotation[2] - 10) < 1e-9);
  assert.ok(Math.abs(steps[3].rotation[2] - 40) < 1e-9);
});

test('l’échelle est multiplicative, pas additive', () => {
  const steps = buildChain({ ...base, stepScale: 0.5 });
  assert.ok(Math.abs(steps[0].scale - 0.5) < 1e-9);
  assert.ok(Math.abs(steps[1].scale - 0.25) < 1e-9);
  assert.ok(Math.abs(steps[3].scale - 0.0625) < 1e-9);
});

test('l’atténuation raccourcit les pas successifs', () => {
  const steps = buildChain({ ...base, falloff: 0.5, count: 3 });
  const d1 = Math.abs(steps[0].offset[1]);
  const d2 = Math.abs(steps[1].offset[1]) - d1;
  const d3 = Math.abs(steps[2].offset[1]) - Math.abs(steps[1].offset[1]);
  assert.ok(d2 < d1, `pas 2 (${d2}) devrait être < pas 1 (${d1})`);
  assert.ok(d3 < d2, `pas 3 (${d3}) devrait être < pas 2 (${d2})`);
});

test('sans atténuation les pas sont identiques', () => {
  const steps = buildChain({ ...base, falloff: 0, count: 3 });
  const d1 = Math.abs(steps[0].offset[1]);
  const d2 = Math.abs(steps[1].offset[1]) - d1;
  assert.ok(Math.abs(d1 - d2) < 1e-9);
});

test('mode enroulé : la rotation courbe la trajectoire', () => {
  // 90° par pas sur Z : en cumulatif, la chaîne tourne au lieu de filer droit.
  const straight = buildChain({ ...base, mode: 'additive', stepRotation: [0, 0, 90], count: 4 });
  const curled = buildChain({ ...base, mode: 'cumulative', stepRotation: [0, 0, 90], count: 4 });
  // En additif le décalage reste sur Y quoi qu'il arrive.
  straight.forEach((s) => assert.ok(Math.abs(s.offset[0]) < 1e-9));
  // En cumulatif, dès la 2e copie le pas repart dans le repère tourné : il
  // quitte l'axe Y pour l'axe X. C'est ce qui fait la courbe.
  assert.ok(Math.abs(curled[1].offset[0] - 4) < 1e-9, `attendu 4 sur X, obtenu ${curled[1].offset[0]}`);
});

test('mode enroulé : 4 pas à 90° referment le cercle', () => {
  // Propriété géométrique, pas un hasard : quatre quarts de tour ramènent la
  // chaîne à son point de départ. Si ça casse un jour, c'est que l'ordre
  // rotation/décalage a été inversé dans buildChain.
  const curled = buildChain({ ...base, mode: 'cumulative', stepRotation: [0, 0, 90], count: 4 });
  const last = curled[3].offset;
  assert.ok(Math.hypot(last[0], last[1], last[2]) < 1e-9,
    `la boucle devrait se refermer, obtenu [${last.map((v) => v.toFixed(6)).join(', ')}]`);
});

test('mode enroulé sans rotation == mode additif', () => {
  const a = buildChain({ ...base, mode: 'additive' });
  const b = buildChain({ ...base, mode: 'cumulative' });
  a.forEach((s, i) => {
    for (let k = 0; k < 3; k++) {
      assert.ok(Math.abs(s.offset[k] - b[i].offset[k]) < 1e-9);
    }
  });
});

test('mirrorVector n’inverse que l’axe visé', () => {
  const v: V3 = [1, 2, 3];
  assert.deepEqual(mirrorVector(v, 'x'), [-1, 2, 3]);
  assert.deepEqual(mirrorVector(v, 'y'), [1, -2, 3]);
  assert.deepEqual(mirrorVector(v, 'z'), [1, 2, -3]);
});

test('mirrorRotation garde l’axe miroir et inverse les deux autres', () => {
  // C'est l'inverse de mirrorVector : se tromper ici fait partir la copie
  // symétrique de travers, et ça ne se voit qu'en tournant le modèle.
  assert.deepEqual(mirrorRotation([10, 20, 30], 'x'), [10, -20, -30]);
  assert.deepEqual(mirrorRotation([10, 20, 30], 'y'), [-10, 20, -30]);
  assert.deepEqual(mirrorRotation([10, 20, 30], 'z'), [-10, -20, 30]);
});

test('le miroir est involutif', () => {
  const v: V3 = [4, -7, 2];
  assert.deepEqual(mirrorVector(mirrorVector(v, 'x'), 'x'), v);
  assert.deepEqual(mirrorRotation(mirrorRotation(v, 'y'), 'y'), v);
});

console.log(`  → ${passed} tests OK\n`);
