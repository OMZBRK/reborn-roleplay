/**
 * Outil Gradient — les dégradés de forme, guidés par un axe dans la vue.
 *
 * Ce que ça évite de peindre à la main : la montée de valeur du bas vers le
 * haut d'une pièce (ou de l'arrière vers l'avant, ou le long d'un membre).
 * C'est le geste le plus répétitif du hand-paint : assombrir régulièrement la
 * base d'une forme et éclaircir son sommet.
 *
 * L'axe est défini par deux points dans l'espace du modèle. On les prend soit
 * d'un preset (les axes du monde, calés sur la boîte englobante du modèle),
 * soit de la sélection courante — ce qui permet de tirer un dégradé le long
 * d'un bras ou d'une queue sans calculer la moindre coordonnée.
 */
import { forEachTexel } from '../core/geometry.ts';
import { hexToRgb, generateRamp, type RampOptions } from '../core/color.ts';
import { emitLayer, paintMultiplyIntoTexture } from '../core/layers.ts';
import { axisFactor, applyFalloff, type Falloff, type V3 } from '../core/field.ts';
import { posterize } from '../core/noise.ts';

export type GradientAxis = 'y' | 'x' | 'z' | 'selection';

export interface GradientOptions {
  axis: GradientAxis;
  /** Inverse le sens (le clair passe en bas). */
  flip: boolean;
  /** Couleur du côté sombre. */
  darkColor: string;
  /** Couleur du côté clair. */
  lightColor: string;
  /** Force globale 0..1. */
  strength: number;
  falloff: Falloff;
  /** 0 = dégradé continu ; ≥2 = bandes de valeur (rendu pixel-art). */
  bands: number;
  /** Nouveau calque, ou composité directement dans la texture. */
  target: 'layer' | 'texture';
}

function activeTexture(): Texture | null {
  const T = Texture as any;
  return T.selected ?? (T.getDefault ? T.getDefault() : null) ?? T.all?.[0] ?? null;
}

/** Boîte englobante monde de toute la géométrie (ou de la sélection). */
function modelBounds(selectionOnly: boolean): { min: V3; max: V3 } | null {
  const C = (globalThis as any).Cube;
  const M = (globalThis as any).Mesh;
  let els: any[] = [...(C?.all ?? []), ...(M?.all ?? [])];
  if (selectionOnly) {
    const sel = (globalThis as any).Outliner?.selected ?? [];
    const selSet = new Set(sel);
    const filtered = els.filter((e) => selSet.has(e));
    if (filtered.length) els = filtered;
  }
  if (!els.length) return null;

  const min: V3 = [Infinity, Infinity, Infinity];
  const max: V3 = [-Infinity, -Infinity, -Infinity];
  let seen = false;
  for (const el of els) {
    const pts = el.getGlobalVertexPositions?.();
    if (!pts) continue;
    for (const p of pts) {
      const v: V3 = Array.isArray(p) ? [p[0], p[1], p[2]] : [p.x, p.y, p.z];
      for (let i = 0; i < 3; i++) {
        if (v[i] < min[i]) min[i] = v[i];
        if (v[i] > max[i]) max[i] = v[i];
      }
      seen = true;
    }
  }
  return seen ? { min, max } : null;
}

/**
 * Détermine les deux extrémités de l'axe du dégradé.
 * Pour un axe du monde, on prend la boîte englobante sur cet axe et on garde le
 * centre des deux autres — l'axe traverse donc le modèle bien en son milieu.
 */
export function resolveAxis(
  opts: Pick<GradientOptions, 'axis' | 'flip'>,
): { a: V3; b: V3 } | null {
  const bounds = modelBounds(opts.axis === 'selection');
  if (!bounds) return null;
  const { min, max } = bounds;
  const mid: V3 = [(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2];

  let a: V3, b: V3;
  if (opts.axis === 'selection') {
    // Le long de la plus grande dimension de la sélection : c'est presque
    // toujours l'axe que l'artiste a en tête (la longueur d'un membre).
    const size: V3 = [max[0] - min[0], max[1] - min[1], max[2] - min[2]];
    const longest = size[0] >= size[1] && size[0] >= size[2] ? 0 : size[1] >= size[2] ? 1 : 2;
    a = [mid[0], mid[1], mid[2]];
    b = [mid[0], mid[1], mid[2]];
    a[longest] = min[longest];
    b[longest] = max[longest];
  } else {
    const i = opts.axis === 'x' ? 0 : opts.axis === 'y' ? 1 : 2;
    a = [mid[0], mid[1], mid[2]];
    b = [mid[0], mid[1], mid[2]];
    a[i] = min[i];
    b[i] = max[i];
  }
  return opts.flip ? { a: b, b: a } : { a, b };
}

export function bakeGradient(opts: GradientOptions): { ok: boolean; message: string } {
  const texture = activeTexture();
  if (!texture) return { ok: false, message: 'Aucune texture sélectionnée.' };
  if (!texture.width || !texture.height) {
    return { ok: false, message: 'Texture sans dimensions valides.' };
  }

  const axis = resolveAxis(opts);
  if (!axis) {
    return { ok: false, message: 'Aucune géométrie pour définir un axe de dégradé.' };
  }

  const [dr, dg, db] = hexToRgb(opts.darkColor);
  const [lr, lg, lb] = hexToRgb(opts.lightColor);
  const img = new ImageData(texture.width, texture.height);
  const px = img.data;
  let texels = 0;

  forEachTexel(texture, (x, y, wx, wy, wz) => {
    let t = axisFactor([wx, wy, wz], axis.a, axis.b);
    t = applyFalloff(t, opts.falloff);
    if (opts.bands >= 2) t = posterize(t, Math.round(opts.bands));

    // Interpolation sombre → clair, puis dosage par la force. À force nulle on
    // reste sur du blanc neutre (multiply) : l'outil ne fait rien, plutôt que
    // d'appliquer un gris moyen qui ternirait la texture.
    const r = dr + (lr - dr) * t;
    const g = dg + (lg - dg) * t;
    const b = db + (lb - db) * t;
    const k = Math.max(0, Math.min(1, opts.strength));
    const o = (y * texture.width + x) * 4;
    px[o] = Math.round(255 * (1 - k) + r * k);
    px[o + 1] = Math.round(255 * (1 - k) + g * k);
    px[o + 2] = Math.round(255 * (1 - k) + b * k);
    px[o + 3] = 255;
    texels++;
  });

  if (texels === 0) {
    return { ok: false, message: 'Aucun texel couvert — la texture est-elle bien mappée ?' };
  }

  if (opts.target === 'texture') {
    paintMultiplyIntoTexture(texture, img, 'Dégradé de forme');
  } else {
    emitLayer(texture, 'Gradient', 'multiply', img, 'Dégradé de forme');
  }

  const axisLabel = opts.axis === 'selection' ? 'sélection' : opts.axis.toUpperCase();
  return {
    ok: true,
    message: `Dégradé appliqué sur l'axe ${axisLabel} : ${texels} texels${opts.bands >= 2 ? `, ${opts.bands} bandes` : ''}.`,
  };
}

/** Aperçu de la rampe du dégradé, pour l'afficher dans le panneau. */
export function gradientPreview(opts: GradientOptions, steps = 8): string[] {
  const ramp: RampOptions = { steps, valueRange: 0, hueShift: 0, satBoost: 0 };
  void ramp; // la rampe du dégradé est une simple interpolation deux points
  const [dr, dg, db] = hexToRgb(opts.darkColor);
  const [lr, lg, lb] = hexToRgb(opts.lightColor);
  const out: string[] = [];
  for (let i = 0; i < steps; i++) {
    let t = steps === 1 ? 0.5 : i / (steps - 1);
    t = applyFalloff(t, opts.falloff);
    if (opts.bands >= 2) t = posterize(t, Math.round(opts.bands));
    const c = (a: number, b: number) =>
      Math.round(a + (b - a) * t).toString(16).padStart(2, '0');
    out.push(`#${c(dr, lr)}${c(dg, lg)}${c(db, lb)}`);
  }
  return out;
}

/** Ré-export pour que le panneau n'ait pas à connaître core/color. */
export { generateRamp };
