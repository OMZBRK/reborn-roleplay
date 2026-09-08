/**
 * Bruits procéduraux déterministes — 100% pur, testable en node.
 *
 * Sert à l'outil Surfaces : générer des matières récurrentes (tissu, fourrure,
 * bois, pierre) sans les repeindre à la main. Tout est déterministe à partir
 * d'une graine : relancer avec les mêmes réglages redonne exactement la même
 * passe, ce qui permet de régénérer un calque sans perdre le rendu.
 *
 * Aucun Math.random() : un artiste doit pouvoir reproduire son résultat.
 */

/** Hash entier 2D → [0,1). Déterministe, bien distribué, sans table. */
export function hash2(x: number, y: number, seed: number): number {
  let h = (x | 0) * 374761393 + (y | 0) * 668265263 + (seed | 0) * 1442695040888963407;
  h = (h ^ (h >>> 13)) >>> 0;
  h = Math.imul(h, 1274126177) >>> 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

function smooth(t: number): number {
  return t * t * (3 - 2 * t); // smoothstep
}

/** Bruit de valeur 2D interpolé, sortie 0..1. */
export function valueNoise(x: number, y: number, seed: number): number {
  const xi = Math.floor(x), yi = Math.floor(y);
  const xf = x - xi, yf = y - yi;
  const u = smooth(xf), v = smooth(yf);
  const a = hash2(xi, yi, seed);
  const b = hash2(xi + 1, yi, seed);
  const c = hash2(xi, yi + 1, seed);
  const d = hash2(xi + 1, yi + 1, seed);
  return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v;
}

/** Somme d'octaves (fBm). `octaves` ≥ 1, `gain` ~0.5, `lacunarity` ~2. */
export function fbm(
  x: number, y: number, seed: number,
  octaves = 3, gain = 0.5, lacunarity = 2,
): number {
  let sum = 0, amp = 1, norm = 0, fx = x, fy = y;
  for (let i = 0; i < octaves; i++) {
    sum += valueNoise(fx, fy, seed + i * 101) * amp;
    norm += amp;
    amp *= gain;
    fx *= lacunarity;
    fy *= lacunarity;
  }
  return norm > 0 ? sum / norm : 0;
}

export type SurfaceKind = 'cloth' | 'fur' | 'wood' | 'stone' | 'noise';

/**
 * Motif de surface, sortie 0..1 (0 = creux/sombre, 1 = crête/clair).
 * `scale` = nombre de répétitions du motif sur la largeur de la texture.
 *
 * Chaque matière est une recette simple et lisible plutôt qu'un shader opaque :
 *  - cloth : trame — deux ondes croisées + un grain fin qui casse la régularité
 *  - fur   : mèches — bruit étiré verticalement, contrasté pour faire des poils
 *  - wood  : veines — anneaux concentriques déformés par du bruit
 *  - stone : granit — fBm large + piqûres sombres éparses
 *  - noise : fBm brut, pour moduler autre chose
 */
export function surfacePattern(
  kind: SurfaceKind,
  u: number, v: number,      // 0..1 sur la texture
  scale: number,
  seed: number,
): number {
  const x = u * scale, y = v * scale;
  switch (kind) {
    case 'cloth': {
      const warp = 0.5 + 0.5 * Math.sin(x * Math.PI * 2);
      const weft = 0.5 + 0.5 * Math.sin(y * Math.PI * 2);
      const weave = Math.max(warp, weft) * 0.75 + (warp * weft) * 0.25;
      const grain = valueNoise(x * 4, y * 4, seed) * 0.18;
      return clamp01(weave * 0.82 + grain);
    }
    case 'fur': {
      // Bruit étiré sur Y → mèches verticales ; le contraste fait les pointes.
      const n = fbm(x * 3, y * 0.35, seed, 3);
      const strands = clamp01((n - 0.42) * 2.6 + 0.5);
      const tips = valueNoise(x * 9, y * 1.2, seed + 7) * 0.22;
      return clamp01(strands * 0.85 + tips);
    }
    case 'wood': {
      // Anneaux : distance au centre, déformée par du bruit basse fréquence.
      const cx = x - scale * 0.5, cy = (y - scale * 0.5) * 0.35;
      const r = Math.hypot(cx, cy);
      const wobble = fbm(x * 0.6, y * 0.6, seed, 2) * 1.6;
      const rings = 0.5 + 0.5 * Math.sin((r + wobble) * Math.PI * 2.2);
      const fibre = valueNoise(x * 1.5, y * 12, seed + 3) * 0.15;
      return clamp01(rings * 0.85 + fibre);
    }
    case 'stone': {
      const base = fbm(x, y, seed, 4, 0.55);
      // Piqûres : les valeurs très basses du bruit fin deviennent des trous.
      const pit = valueNoise(x * 6, y * 6, seed + 11);
      const pits = pit < 0.18 ? -0.35 : 0;
      return clamp01(base + pits);
    }
    default:
      return clamp01(fbm(x, y, seed, 4));
  }
}

export function clamp01(v: number): number {
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** Quantifie 0..1 en `levels` paliers (rendu pixel-art plutôt que dégradé lisse). */
export function posterize(v: number, levels: number): number {
  if (levels < 2) return v;
  const q = Math.round(clamp01(v) * (levels - 1)) / (levels - 1);
  return q;
}
