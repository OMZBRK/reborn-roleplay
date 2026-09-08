/**
 * Outil Lighting — crée le VOLUME depuis la géométrie (form shading).
 *
 * Ce que ça évite de peindre à la main : le modelé « 3D » d'une texture plate.
 * Pour chaque texel, on regarde l'orientation de sa face par rapport à une
 * lumière : les faces tournées vers la lumière sont éclairées (highlight chaud,
 * calque screen), celles à l'opposé sont ombrées (shadow froid, calque multiply).
 * Résultat : une texture unie prend immédiatement du relief peint.
 *
 * Contrairement à l'AO, pas besoin de raycast — juste la normale de chaque face.
 */
import { forEachTexel } from '../core/geometry.ts';
import { hexToRgb } from '../core/color.ts';
import { emitLayer } from '../core/layers.ts';

export type V3 = [number, number, number];

/** Directions de lumière prédéfinies (normalisées). */
export const LIGHT_PRESETS: Record<string, V3> = {
  top: norm([0, 1, 0]),
  top_front: norm([0.25, 0.85, 0.45]),
  top_left: norm([-0.5, 0.8, 0.3]),
  top_right: norm([0.5, 0.8, 0.3]),
  front: norm([0, 0.3, 1]),
};

function norm(v: V3): V3 {
  const l = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / l, v[1] / l, v[2] / l];
}

export interface LightingOptions {
  dir: V3;                 // direction de la lumière (unitaire)
  shadowColor: string;     // teinte des ombres (froide)
  shadowStrength: number;  // 0..1
  highlightColor: string;  // teinte des lumières (chaude)
  highlightStrength: number; // 0..1
  shadowOn: boolean;
  highlightOn: boolean;
}

function activeTexture(): Texture | null {
  const T = Texture as any;
  return T.selected ?? (T.getDefault ? T.getDefault() : null) ?? T.all?.[0] ?? null;
}

export function bakeLighting(opts: LightingOptions): { ok: boolean; message: string } {
  const texture = activeTexture();
  if (!texture) return { ok: false, message: 'Aucune texture sélectionnée.' };
  if (!texture.width || !texture.height) return { ok: false, message: 'Texture sans dimensions valides.' };

  const [dx, dy, dz] = opts.dir;
  const [shR, shG, shB] = hexToRgb(opts.shadowColor);
  const [hlR, hlG, hlB] = hexToRgb(opts.highlightColor);

  const shadow = new ImageData(texture.width, texture.height);
  const highlight = new ImageData(texture.width, texture.height);
  const sd = shadow.data, hd = highlight.data;
  let texels = 0;

  forEachTexel(texture, (px, py, _wx, _wy, _wz, nx, ny, nz) => {
    const dot = nx * dx + ny * dy + nz * dz; // -1..1
    const t = (dot + 1) / 2;                  // 0 = dos à la lumière, 1 = face
    const o = (py * texture.width + px) * 4;

    if (opts.shadowOn) {
      // multiply : blanc = neutre, on assombrit les faces à l'opposé.
      const dark = Math.min(1, Math.max(0, (1 - t) * opts.shadowStrength));
      const inv = 1 - dark;
      sd[o] = Math.round(255 * inv + shR * dark);
      sd[o + 1] = Math.round(255 * inv + shG * dark);
      sd[o + 2] = Math.round(255 * inv + shB * dark);
      sd[o + 3] = 255;
    }
    if (opts.highlightOn) {
      // screen : noir = neutre, on éclaircit les faces exposées.
      const bright = Math.min(1, Math.max(0, (t - 0.5) * 2)) * opts.highlightStrength;
      hd[o] = Math.round(hlR * bright);
      hd[o + 1] = Math.round(hlG * bright);
      hd[o + 2] = Math.round(hlB * bright);
      hd[o + 3] = 255;
    }
    texels++;
  });

  if (texels === 0) {
    return { ok: false, message: 'Aucun texel couvert — la texture est-elle bien mappée ?' };
  }

  if (opts.shadowOn) emitLayer(texture, 'Light Shadow', 'multiply', shadow, 'Lumière — ombres');
  if (opts.highlightOn) emitLayer(texture, 'Light Highlight', 'screen', highlight, 'Lumière — hautes lumières');

  return { ok: true, message: `Lumière appliquée : ${texels} texels.` };
}
