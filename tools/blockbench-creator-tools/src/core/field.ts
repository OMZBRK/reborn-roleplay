/**
 * Champs scalaires utilitaires — 100% pur, testable en node.
 *
 * Deux besoins des outils Gradient et Edges :
 *  - projeter une position monde sur un axe pour en tirer un facteur 0..1
 *    (dégradé de forme guidé par deux points) ;
 *  - classer un texel par rapport aux bords de sa face, et savoir si l'arête
 *    voisine est convexe (highlight) ou concave (couture sombre).
 */

export type V3 = [number, number, number];

export function dot(a: V3, b: V3): number {
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

export function subV(a: V3, b: V3): V3 {
  return [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
}

export function lengthV(a: V3): number {
  return Math.hypot(a[0], a[1], a[2]);
}

export function normalizeV(a: V3): V3 {
  const l = lengthV(a) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
}

/**
 * Facteur 0..1 de la projection de `p` sur le segment [a,b].
 * 0 quand p se projette en a (ou avant), 1 en b (ou après).
 *
 * C'est le cœur du dégradé « guidé par deux points » : l'artiste pose un début
 * et une fin dans la vue, chaque texel reçoit sa position le long de cet axe.
 */
export function axisFactor(p: V3, a: V3, b: V3): number {
  const ab = subV(b, a);
  const len2 = dot(ab, ab);
  if (len2 < 1e-12) return 0;
  const t = dot(subV(p, a), ab) / len2;
  return t < 0 ? 0 : t > 1 ? 1 : t;
}

/** Courbe de réponse appliquée au facteur brut d'un dégradé. */
export type Falloff = 'linear' | 'smooth' | 'ease_in' | 'ease_out';

export function applyFalloff(t: number, mode: Falloff): number {
  const c = t < 0 ? 0 : t > 1 ? 1 : t;
  switch (mode) {
    case 'smooth': return c * c * (3 - 2 * c);
    case 'ease_in': return c * c;
    case 'ease_out': return 1 - (1 - c) * (1 - c);
    default: return c;
  }
}

/**
 * Classe une arête entre deux faces à partir de leurs normales et de la
 * direction qui va de la première face vers la seconde.
 *
 *  - `convex`  : les faces s'écartent (arête saillante) → capte la lumière,
 *                c'est là qu'on pose un liseré clair.
 *  - `concave` : les faces se referment (rentrant) → c'est une couture, on y
 *                pose une ligne sombre.
 *  - `flat`    : quasi coplanaires, rien à faire (sinon on souligne des faux
 *                bords sur une surface continue).
 *
 * `flatThreshold` est en cosinus : 0.98 ≈ 11°.
 */
export type EdgeKind = 'convex' | 'concave' | 'flat';

export function classifyEdge(
  nSelf: V3,
  nOther: V3,
  selfToOther: V3,
  flatThreshold = 0.98,
): EdgeKind {
  const d = dot(nSelf, nOther);
  if (d >= flatThreshold) return 'flat';
  // Si la normale voisine pointe « en s'éloignant » dans le sens du décalage,
  // les faces s'ouvrent vers l'extérieur → arête saillante.
  return dot(nOther, normalizeV(selfToOther)) < 0 ? 'convex' : 'concave';
}

/**
 * Atténuation d'un liseré selon la distance au bord, en pixels.
 * Retourne 1 sur le bord et décroît jusqu'à 0 à `width` pixels.
 */
export function edgeFalloff(distancePx: number, width: number): number {
  if (width <= 0) return distancePx <= 0 ? 1 : 0;
  const t = 1 - distancePx / width;
  return t < 0 ? 0 : t > 1 ? 1 : t;
}

/**
 * Transformée de distance approchée (deux passes, chanfrein 3-4) sur un masque.
 * `mask[i] === 0` = pixel de bord (distance 0), sinon distance à calculer.
 * Bien plus rapide qu'un BFS exact et largement assez précis pour un liseré.
 */
export function distanceTransform(
  mask: Uint8Array,
  width: number,
  height: number,
  maxDist = 255,
): Float32Array {
  const d = new Float32Array(width * height);
  const BIG = maxDist;
  for (let i = 0; i < d.length; i++) d[i] = mask[i] === 0 ? 0 : BIG;

  const at = (x: number, y: number) => (x < 0 || y < 0 || x >= width || y >= height ? BIG : d[y * width + x]);

  // Passe avant (haut-gauche → bas-droite).
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = y * width + x;
      let v = d[i];
      v = Math.min(v, at(x - 1, y) + 3, at(x, y - 1) + 3);
      v = Math.min(v, at(x - 1, y - 1) + 4, at(x + 1, y - 1) + 4);
      d[i] = v;
    }
  }
  // Passe arrière (bas-droite → haut-gauche).
  for (let y = height - 1; y >= 0; y--) {
    for (let x = width - 1; x >= 0; x--) {
      const i = y * width + x;
      let v = d[i];
      v = Math.min(v, at(x + 1, y) + 3, at(x, y + 1) + 3);
      v = Math.min(v, at(x + 1, y + 1) + 4, at(x - 1, y + 1) + 4);
      d[i] = v;
    }
  }
  // Le chanfrein travaille en tiers de pixel.
  for (let i = 0; i < d.length; i++) d[i] /= 3;
  return d;
}
