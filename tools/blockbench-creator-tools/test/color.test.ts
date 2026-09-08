/**
 * Tests du cœur couleur — exécutables sans Blockbench (`pnpm test:color`).
 */
import {
  hexToRgb,
  rgbToHex,
  rgbToHsl,
  hslToRgb,
  luminance,
  generateRamp,
  rampIndexForLuminance,
} from '../src/core/color.ts';

let failures = 0;
function check(name: string, cond: boolean): void {
  if (cond) console.log(`  ok  ${name}`);
  else { failures++; console.error(`FAIL  ${name}`); }
}
function approx(a: number, b: number, eps = 1): boolean { return Math.abs(a - b) < eps; }

// --- hex ↔ rgb -----------------------------------------------------------
check('hexToRgb #ff8040', (() => { const [r, g, b] = hexToRgb('#ff8040'); return r === 255 && g === 128 && b === 64; })());
check('hexToRgb court #f80', (() => { const [r, g, b] = hexToRgb('#f80'); return r === 255 && g === 136 && b === 0; })());
check('rgbToHex roundtrip', rgbToHex(255, 128, 64) === '#ff8040');
check('rgbToHex clamp', rgbToHex(300, -5, 64) === '#ff0040');

// --- hsl roundtrip -------------------------------------------------------
for (const hex of ['#7a5a3c', '#204080', '#e0e0e0', '#123456', '#00ff00']) {
  const [r, g, b] = hexToRgb(hex);
  const [h, s, l] = rgbToHsl(r, g, b);
  const [r2, g2, b2] = hslToRgb(h, s, l);
  check(`hsl roundtrip ${hex}`, approx(r, r2) && approx(g, g2) && approx(b, b2));
}

// --- luminance ordonnée --------------------------------------------------
check('luminance blanc > gris > noir',
  luminance(255, 255, 255) > luminance(128, 128, 128) &&
  luminance(128, 128, 128) > luminance(0, 0, 0));

// --- rampe ---------------------------------------------------------------
{
  const ramp = generateRamp('#7a5a3c', { steps: 5, valueRange: 0.6, hueShift: 12, satBoost: 0.12 });
  check('rampe a 5 tons', ramp.length === 5);
  check('rampe tons hex valides', ramp.every((c) => /^#[0-9a-f]{6}$/.test(c)));
  // Luminance strictement croissante du sombre vers le clair.
  const lums = ramp.map((c) => { const [r, g, b] = hexToRgb(c); return luminance(r, g, b); });
  let mono = true;
  for (let i = 1; i < lums.length; i++) if (lums[i] <= lums[i - 1]) mono = false;
  check('rampe luminance croissante', mono);
  // Ombre plus froide (teinte plus proche du bleu) que la lumière.
  const hShadow = rgbToHsl(...hexToRgb(ramp[0]))[0];
  const hLight = rgbToHsl(...hexToRgb(ramp[ramp.length - 1]))[0];
  check('hue-shift ombre≠lumière', Math.abs(hShadow - hLight) > 1);
  // Ombre plus saturée que la lumière.
  const sShadow = rgbToHsl(...hexToRgb(ramp[0]))[1];
  const sLight = rgbToHsl(...hexToRgb(ramp[ramp.length - 1]))[1];
  check('ombre plus saturée que lumière', sShadow > sLight);
}

check('steps minimum forcé à 2', generateRamp('#808080', { steps: 1, valueRange: 0.5, hueShift: 0, satBoost: 0 }).length === 2);

// --- index rampe ---------------------------------------------------------
check('index luminance 0 → 0', rampIndexForLuminance(0, 5) === 0);
check('index luminance 255 → dernier', rampIndexForLuminance(255, 5) === 4);
check('index luminance médiane', rampIndexForLuminance(128, 5) === 2);

if (failures > 0) { console.error(`\n${failures} test(s) en échec`); process.exit(1); }
console.log('\nTous les tests color passent ✓');
