/**
 * Tests du retiming (Motion Lab) — node pur, sans Blockbench.
 *
 * La propriété qui compte le plus : **on ne perd jamais une clé**. Un outil de
 * retiming qui change le nombre de keyframes détruirait le travail de
 * l'animateur ; chaque test vérifie que le compte est conservé.
 */
import assert from 'node:assert/strict';
import {
  ease, retime, scaleTimes, offsetTimes, phaseShift, loopClosureTime,
  mirrorBoneName, type EasingName,
} from '../src/core/easing.ts';

let passed = 0;
function test(name: string, fn: () => void): void {
  fn();
  passed++;
  console.log(`  ✓ ${name}`);
}

const CURVES: EasingName[] = [
  'linear', 'ease_in', 'ease_out', 'ease_in_out', 'anticipate', 'overshoot', 'bounce_out',
];

console.log('easing.ts');

test('toutes les courbes partent de 0 et arrivent à 1', () => {
  for (const c of CURVES) {
    assert.ok(Math.abs(ease(c, 0)) < 1e-9, `${c} en 0 = ${ease(c, 0)}`);
    assert.ok(Math.abs(ease(c, 1) - 1) < 1e-9, `${c} en 1 = ${ease(c, 1)}`);
  }
});

test('les entrées hors bornes sont clampées', () => {
  for (const c of CURVES) {
    assert.equal(ease(c, -5), ease(c, 0));
    assert.equal(ease(c, 5), ease(c, 1));
  }
});

test('anticipate passe sous zéro (le contre-mouvement)', () => {
  let min = Infinity;
  for (let i = 0; i <= 100; i++) min = Math.min(min, ease('anticipate', i / 100));
  assert.ok(min < -0.01, `minimum ${min}, attendu négatif`);
});

test('overshoot dépasse un (l’accent de fin)', () => {
  let max = -Infinity;
  for (let i = 0; i <= 100; i++) max = Math.max(max, ease('overshoot', i / 100));
  assert.ok(max > 1.01, `maximum ${max}, attendu > 1`);
});

test('retime conserve le nombre de clés', () => {
  const times = [0, 0.2, 0.5, 0.9, 1.4];
  for (const c of CURVES) {
    assert.equal(retime(times, c).length, times.length, c);
  }
});

test('retime garde la première et la dernière clé en place', () => {
  const times = [0.5, 1, 2, 3.5];
  for (const c of CURVES) {
    const r = retime(times, c);
    assert.ok(Math.abs(r[0] - 0.5) < 1e-9, c);
    assert.ok(Math.abs(r[r.length - 1] - 3.5) < 1e-9, c);
  }
});

test('retime laisse intactes les listes de moins de 3 clés', () => {
  assert.deepEqual(retime([], 'ease_in'), []);
  assert.deepEqual(retime([1], 'ease_in'), [1]);
  assert.deepEqual(retime([1, 2], 'ease_in'), [1, 2]);
});

test('retime ne casse pas sur des clés toutes au même instant', () => {
  const r = retime([2, 2, 2], 'ease_in_out');
  assert.equal(r.length, 3);
  r.forEach((t) => assert.equal(t, 2));
});

test('scaleTimes resserre autour du pivot', () => {
  const r = scaleTimes([0, 1, 2], 0.5, 0);
  assert.deepEqual(r, [0, 0.5, 1]);
});

test('scaleTimes refuse un facteur nul ou négatif', () => {
  const r = scaleTimes([0, 2], 0, 0);
  assert.ok(r[1] > 0, 'un facteur 0 écraserait toutes les clés au même instant');
  const neg = scaleTimes([0, 2], -1, 0);
  assert.ok(neg[1] > 0, 'un facteur négatif inverserait l’ordre des clés');
});

test('offsetTimes ne descend jamais sous zéro', () => {
  assert.deepEqual(offsetTimes([0, 1, 2], -5), [0, 0, 0]);
  assert.deepEqual(offsetTimes([0, 1], 0.5), [0.5, 1.5]);
});

test('phaseShift boucle dans [0, length)', () => {
  const r = phaseShift([0, 0.5, 0.9], 1, 0.3);
  r.forEach((t) => assert.ok(t >= 0 && t < 1, `t=${t} hors boucle`));
  assert.ok(Math.abs(r[0] - 0.3) < 1e-9);
  assert.ok(Math.abs(r[2] - 0.2) < 1e-9, `0.9 + 0.3 mod 1 devrait donner 0.2, obtenu ${r[2]}`);
});

test('phaseShift gère un décalage négatif', () => {
  const r = phaseShift([0.1], 1, -0.3);
  assert.ok(Math.abs(r[0] - 0.8) < 1e-9, `obtenu ${r[0]}`);
});

test('loopClosureTime refuse une durée nulle', () => {
  assert.equal(loopClosureTime(0), null);
  assert.equal(loopClosureTime(2.5), 2.5);
});

test('mirrorBoneName trouve les paires séparées par un underscore', () => {
  assert.equal(mirrorBoneName('bras_left'), 'bras_right');
  assert.equal(mirrorBoneName('bras_right'), 'bras_left');
  assert.equal(mirrorBoneName('jambe_l'), 'jambe_r');
  assert.equal(mirrorBoneName('jambe_r'), 'jambe_l');
});

test('mirrorBoneName gère le camelCase des modèles Minecraft vanilla', () => {
  // C'est la convention de loin la plus répandue : leftArm, rightLeg.
  assert.equal(mirrorBoneName('leftArm'), 'rightArm');
  assert.equal(mirrorBoneName('rightLeg'), 'leftLeg');
  assert.equal(mirrorBoneName('armLeft'), 'armRight');
});

test('mirrorBoneName conserve la casse du token', () => {
  assert.equal(mirrorBoneName('LeftArm'), 'RightArm');
  assert.equal(mirrorBoneName('BRAS_LEFT'), 'BRAS_RIGHT');
  assert.equal(mirrorBoneName('bras_left'), 'bras_right');
});

test('mirrorBoneName rend null pour un os central', () => {
  assert.equal(mirrorBoneName('torse'), null);
  assert.equal(mirrorBoneName('head'), null);
});

test('mirrorBoneName ne se déclenche pas sur des faux positifs', () => {
  // Les pièges : un « l » au milieu d'un mot, et « right » enfoui dans
  // « bright ». Renommer un os central casserait la symétrie silencieusement.
  assert.equal(mirrorBoneName('clavicule'), null);
  assert.equal(mirrorBoneName('bright'), null);
  assert.equal(mirrorBoneName('collier'), null);
});

console.log(`  → ${passed} tests OK\n`);
