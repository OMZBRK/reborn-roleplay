/**
 * Outil Surfaces — les matières récurrentes, appliquées sans repeindre la base.
 *
 * Ce que ça évite de peindre à la main : le grain. Un manteau en tissu, une
 * fourrure, une planche, un mur de pierre — à chaque fois le même travail de
 * texture répétitive par-dessus un aplat déjà ombré.
 *
 * L'outil ne remplace pas la couleur : il produit une passe de **modulation**
 * (overlay sombre en multiply, crêtes claires en screen) qu'on pose au-dessus.
 * La base reste intacte, le calque reste réglable, et régénérer avec la même
 * graine redonne exactement le même grain.
 *
 * Deux modes de projection :
 *  - `uv`    : le motif suit la texture. Rapide, prévisible, mais le grain
 *              s'étire là où les UV sont étirées.
 *  - `world` : le motif suit l'espace du modèle. Le grain garde la même échelle
 *              partout et traverse les coutures sans se couper — c'est ce qu'on
 *              veut sur un objet dont les UV sont découpées en morceaux.
 */
import { forEachTexel } from '../core/geometry.ts';
import { hexToRgb } from '../core/color.ts';
import { emitLayer } from '../core/layers.ts';
import { surfacePattern, posterize, clamp01, type SurfaceKind } from '../core/noise.ts';

export interface SurfacesOptions {
  kind: SurfaceKind;
  /** Répétitions du motif (échelle). */
  scale: number;
  /** Graine — même graine = même grain, toujours. */
  seed: number;
  /** Force globale 0..1. */
  strength: number;
  /** 0 = continu, ≥2 = paliers (rendu pixel-art). */
  levels: number;
  /** Couleur des creux (calque multiply). */
  shadowColor: string;
  /** Couleur des crêtes (calque screen). Vide = pas de passe claire. */
  highlightColor: string;
  highlightOn: boolean;
  projection: 'uv' | 'world';
}

function activeTexture(): Texture | null {
  const T = Texture as any;
  return T.selected ?? (T.getDefault ? T.getDefault() : null) ?? T.all?.[0] ?? null;
}

export const SURFACE_LABELS: Record<SurfaceKind, string> = {
  cloth: 'Tissu',
  fur: 'Fourrure',
  wood: 'Bois',
  stone: 'Pierre',
  noise: 'Grain',
};

export function bakeSurface(opts: SurfacesOptions): { ok: boolean; message: string } {
  const texture = activeTexture();
  if (!texture) return { ok: false, message: 'Aucune texture sélectionnée.' };
  if (!texture.width || !texture.height) {
    return { ok: false, message: 'Texture sans dimensions valides.' };
  }

  const W = texture.width, H = texture.height;
  const [shR, shG, shB] = hexToRgb(opts.shadowColor);
  const [hlR, hlG, hlB] = hexToRgb(opts.highlightColor);
  const scale = Math.max(0.25, opts.scale);
  const seed = Math.round(opts.seed) | 0;
  const strength = clamp01(opts.strength);

  const shadow = new ImageData(W, H);
  const highlight = new ImageData(W, H);
  const sd = shadow.data, hd = highlight.data;
  let texels = 0;

  forEachTexel(texture, (x, y, wx, wy, wz) => {
    // En projection monde, on divise par 16 (1 bloc Minecraft) pour que
    // `scale` reste lisible : 1 = un motif par bloc.
    const u = opts.projection === 'world' ? wx / 16 : x / W;
    const v = opts.projection === 'world' ? (wy + wz * 0.5) / 16 : y / H;

    let p = surfacePattern(opts.kind, u, v, scale, seed);
    if (opts.levels >= 2) p = posterize(p, Math.round(opts.levels));

    const o = (y * W + x) * 4;

    // Creux : p faible → assombrit. Centré sur 0.5 pour que le motif ne
    // noircisse pas globalement la texture.
    const dark = clamp01((0.5 - p) * 2) * strength;
    const invD = 1 - dark;
    sd[o] = Math.round(255 * invD + shR * dark);
    sd[o + 1] = Math.round(255 * invD + shG * dark);
    sd[o + 2] = Math.round(255 * invD + shB * dark);
    sd[o + 3] = 255;

    if (opts.highlightOn) {
      const bright = clamp01((p - 0.5) * 2) * strength;
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

  const label = SURFACE_LABELS[opts.kind] ?? 'Surface';
  emitLayer(texture, `Surface ${label}`, 'multiply', shadow, `Surface — ${label}`);
  if (opts.highlightOn) {
    emitLayer(texture, `Surface ${label} (crêtes)`, 'screen', highlight, `Surface — ${label} crêtes`);
  }

  return {
    ok: true,
    message: `${label} appliqué (${opts.projection === 'world' ? 'projection monde' : 'projection UV'}, graine ${seed}) : ${texels} texels.`,
  };
}

/** Aperçu du motif en niveaux de gris, pour l'afficher dans le panneau. */
export function surfacePreview(opts: SurfacesOptions, size = 12): string[] {
  const out: string[] = [];
  for (let i = 0; i < size; i++) {
    const t = size === 1 ? 0.5 : i / (size - 1);
    let p = surfacePattern(opts.kind, t, t * 0.6, Math.max(0.25, opts.scale), opts.seed | 0);
    if (opts.levels >= 2) p = posterize(p, Math.round(opts.levels));
    const v = Math.round(60 + p * 175).toString(16).padStart(2, '0');
    out.push(`#${v}${v}${v}`);
  }
  return out;
}
