/**
 * Outil AO — bake d'occlusion ambiante dans un TextureLayer multiply éditable.
 *
 * Ce que ça évite de peindre à la main : l'ombre de contact dans les recoins,
 * sous les chevauchements et aux jonctions de géométrie. On lance des rayons
 * en hémisphère depuis chaque texel ; plus il y a de géométrie autour, plus
 * c'est sombre.
 */
import { collectOccluders, forEachTexel } from '../core/geometry.ts';
import { computeAO, hammersleyPairs } from '../core/raymath.ts';
import { emitLayer, paintMultiplyIntoTexture } from '../core/layers.ts';

export interface AOOptions {
  color: string;      // couleur de l'ombre (#rrggbb)
  intensity: number;  // multiplicateur de la force (0..2)
  radius: number;     // portée des rayons en unités MC
  samples: number;    // rayons par texel
  target: 'layer' | 'texture';
  dither: boolean;    // quantification ordonnée (pixel-art)
  levels: number;     // niveaux de quantification si dither
}

// Presets de teinte d'ombre (RuneFist-like) — utilisés si tone != custom.
export const AO_TONES: Record<string, string> = {
  cool: '#16202e',
  neutral: '#1c1c1c',
  warm: '#2a1e14',
};

// Matrice de Bayer 4×4 (dithering ordonné), valeurs 0..15.
const BAYER4 = [
  [0, 8, 2, 10],
  [12, 4, 14, 6],
  [3, 11, 1, 9],
  [15, 7, 13, 5],
];

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  return [
    parseInt(h.slice(0, 2), 16) || 0,
    parseInt(h.slice(2, 4), 16) || 0,
    parseInt(h.slice(4, 6), 16) || 0,
  ];
}

function activeTexture(): Texture | null {
  const T = Texture as any;
  return T.selected ?? (T.getDefault ? T.getDefault() : null) ?? T.all?.[0] ?? null;
}

/** Exécute le bake. Retourne un petit rapport pour l'affichage. */
export function bakeAO(opts: AOOptions): { ok: boolean; message: string } {
  const texture = activeTexture();
  if (!texture) {
    return { ok: false, message: 'Aucune texture sélectionnée.' };
  }
  if (!texture.width || !texture.height) {
    return { ok: false, message: 'La texture n\'a pas de dimensions valides.' };
  }

  const { soup, triCount } = collectOccluders();
  if (triCount === 0) {
    return { ok: false, message: 'Aucune géométrie à occlure.' };
  }

  const pairs = hammersleyPairs(opts.samples);
  const [cr, cg, cb] = hexToRgb(opts.color);

  const img = new ImageData(texture.width, texture.height);
  const data = img.data;
  let texels = 0;

  forEachTexel(texture, (px, py, wx, wy, wz, nx, ny, nz) => {
    const ao = computeAO(soup, triCount, wx, wy, wz, nx, ny, nz, pairs, opts.samples, opts.radius);
    let shade = ao * opts.intensity;
    if (shade > 1) shade = 1;
    if (shade < 0) shade = 0;

    if (opts.dither) {
      const t = (BAYER4[py & 3][px & 3] + 0.5) / 16 - 0.5;
      shade = Math.round(shade * opts.levels + t) / opts.levels;
      if (shade > 1) shade = 1;
      if (shade < 0) shade = 0;
    }

    // Calque multiply : blanc = neutre, on tire vers la couleur d'ombre.
    const inv = 1 - shade;
    const o = (py * texture.width + px) * 4;
    data[o] = Math.round(255 * inv + cr * shade);
    data[o + 1] = Math.round(255 * inv + cg * shade);
    data[o + 2] = Math.round(255 * inv + cb * shade);
    data[o + 3] = 255;
    texels++;
  });

  if (texels === 0) {
    return {
      ok: false,
      message: 'Aucun texel couvert — la texture sélectionnée est-elle bien mappée sur le modèle ?',
    };
  }

  if (opts.target === 'texture') {
    paintMultiplyIntoTexture(texture, img, 'Bake AO');
  } else {
    emitLayer(texture, 'AO', 'multiply', img, 'Générer le calque AO');
  }

  return {
    ok: true,
    message: `AO bakée : ${texels} texels, ${triCount} triangles, ${opts.samples} rayons.`,
  };
}
