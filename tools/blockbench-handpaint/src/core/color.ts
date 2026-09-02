/**
 * Cœur couleur — 100% pur (aucune dépendance Blockbench), donc testable en node.
 * Génération de rampes hand-painted (ombres refroidies, lumières réchauffées)
 * et remap de luminance sur une rampe.
 */

export type RGB = [number, number, number]; // 0..255

export function hexToRgb(hex: string): RGB {
  const h = hex.replace('#', '').trim();
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  return [
    parseInt(full.slice(0, 2), 16) || 0,
    parseInt(full.slice(2, 4), 16) || 0,
    parseInt(full.slice(4, 6), 16) || 0,
  ];
}

export function rgbToHex(r: number, g: number, b: number): string {
  const c = (v: number) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0');
  return `#${c(r)}${c(g)}${c(b)}`;
}

/** RGB (0..255) → HSL (h en degrés 0..360, s/l en 0..1). */
export function rgbToHsl(r: number, g: number, b: number): [number, number, number] {
  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  const l = (max + min) / 2;
  let h = 0, s = 0;
  const d = max - min;
  if (d > 1e-9) {
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break;
      case g: h = (b - r) / d + 2; break;
      default: h = (r - g) / d + 4; break;
    }
    h *= 60;
  }
  return [h, s, l];
}

/** HSL (h degrés, s/l 0..1) → RGB (0..255). */
export function hslToRgb(h: number, s: number, l: number): RGB {
  h = ((h % 360) + 360) % 360;
  s = Math.max(0, Math.min(1, s));
  l = Math.max(0, Math.min(1, l));
  if (s < 1e-9) {
    const v = l * 255;
    return [v, v, v];
  }
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;
  let r = 0, g = 0, b = 0;
  if (h < 60) { r = c; g = x; }
  else if (h < 120) { r = x; g = c; }
  else if (h < 180) { g = c; b = x; }
  else if (h < 240) { g = x; b = c; }
  else if (h < 300) { r = x; b = c; }
  else { r = c; b = x; }
  return [(r + m) * 255, (g + m) * 255, (b + m) * 255];
}

/** Luminance perçue (Rec. 601), 0..255. */
export function luminance(r: number, g: number, b: number): number {
  return 0.299 * r + 0.587 * g + 0.114 * b;
}

export interface RampOptions {
  steps: number;      // nombre de tons (>=2)
  valueRange: number; // amplitude de clair (0..1) autour de la base
  hueShift: number;   // décalage de teinte total (degrés) ombre↔lumière
  satBoost: number;   // sursaturation des ombres (0..~0.5)
}

/**
 * Génère une rampe hand-painted du plus sombre au plus clair à partir d'une
 * couleur de base. Principe : les ombres sont refroidies (teinte vers le bleu)
 * et plus saturées, les lumières réchauffées (teinte vers le jaune) et moins
 * saturées — ce qui donne des dégradés vivants plutôt qu'un simple assombrissement.
 */
export function generateRamp(baseHex: string, opts: RampOptions): string[] {
  const steps = Math.max(2, Math.round(opts.steps));
  const [r, g, b] = hexToRgb(baseHex);
  const [h, s, l] = rgbToHsl(r, g, b);
  const out: string[] = [];
  for (let i = 0; i < steps; i++) {
    // t: 0 = plus sombre, 1 = plus clair ; 0.5 ≈ base.
    const t = steps === 1 ? 0.5 : i / (steps - 1);
    const d = t - 0.5; // -0.5..+0.5
    const li = l + d * opts.valueRange;
    // Teinte : ombres vers le bleu (240), lumières vers le jaune (60).
    // On décale linéairement autour de la base, borné par hueShift.
    const hi = h + d * 2 * opts.hueShift;
    // Saturation : plus forte dans les ombres, plus faible dans les lumières.
    const si = s + (-d) * 2 * opts.satBoost;
    const [rr, gg, bb] = hslToRgb(hi, si, li);
    out.push(rgbToHex(rr, gg, bb));
  }
  return out;
}

/**
 * Choisit l'index de rampe (0..steps-1) pour une luminance donnée (0..255).
 * Posterise la valeur en bandes égales.
 */
export function rampIndexForLuminance(lum: number, steps: number): number {
  const t = Math.max(0, Math.min(0.9999, lum / 255));
  return Math.min(steps - 1, Math.floor(t * steps));
}
