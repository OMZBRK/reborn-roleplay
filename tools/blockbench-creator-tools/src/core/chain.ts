/**
 * Calcul des transformations d'une chaîne — 100% pur, testable en node.
 *
 * Le « chain » de Blockout Canvas : à partir d'un élément, produire N copies
 * décalées, tournées et redimensionnées progressivement. C'est ce qui fabrique
 * en un geste une queue, une corde, une rangée de piquants, une crête, un
 * escalier, une colonne segmentée — tout ce qu'on ferait sinon en dupliquant
 * vingt fois à la main puis en corrigeant chaque pas.
 *
 * Deux modes, et la différence compte :
 *  - `additive`   : chaque copie s'ajoute au même repère (rangée régulière).
 *  - `cumulative` : chaque copie repart de la précédente, donc les rotations
 *                   s'accumulent et la chaîne s'enroule (queue, tentacule).
 */

export type V3 = [number, number, number];

export interface ChainStep {
  /** Décalage total depuis l'origine, en unités Minecraft. */
  offset: V3;
  /** Rotation totale en degrés (X, Y, Z). */
  rotation: V3;
  /** Facteur d'échelle uniforme appliqué à la copie. */
  scale: number;
  /** Index de la copie, 1..count (0 = l'original, non produit ici). */
  index: number;
}

export interface ChainOptions {
  /** Nombre de copies à produire (hors original). */
  count: number;
  /** Décalage appliqué à chaque pas. */
  step: V3;
  /** Rotation appliquée à chaque pas, en degrés. */
  stepRotation: V3;
  /** Échelle multipliée à chaque pas (1 = constant, 0.9 = rétrécit). */
  stepScale: number;
  /** `cumulative` fait s'enrouler la chaîne ; `additive` la garde droite. */
  mode: 'additive' | 'cumulative';
  /**
   * Atténuation appliquée au pas au fil de la chaîne (0 = aucune).
   * À 0.5, le dernier pas ne fait plus que la moitié du premier — ce qui donne
   * les courbes qui se resserrent, typiques d'une queue ou d'une vrille.
   */
  falloff: number;
}

function rotateAroundOrigin(p: V3, degrees: V3): V3 {
  const [rx, ry, rz] = degrees.map((d) => (d * Math.PI) / 180) as V3;
  let [x, y, z] = p;
  // X
  let c = Math.cos(rx), s = Math.sin(rx);
  [y, z] = [y * c - z * s, y * s + z * c];
  // Y
  c = Math.cos(ry); s = Math.sin(ry);
  [x, z] = [x * c + z * s, -x * s + z * c];
  // Z
  c = Math.cos(rz); s = Math.sin(rz);
  [x, y] = [x * c - y * s, x * s + y * c];
  return [x, y, z];
}

/**
 * Produit la liste des transformations, dans l'ordre.
 * Rien n'est créé ici : c'est le tool Blockbench qui applique le résultat.
 */
export function buildChain(opts: ChainOptions): ChainStep[] {
  const count = Math.max(0, Math.round(opts.count));
  const out: ChainStep[] = [];

  let offset: V3 = [0, 0, 0];
  let rotation: V3 = [0, 0, 0];
  let scale = 1;

  for (let i = 1; i <= count; i++) {
    // Atténuation : le pas i vaut (1 - falloff * (i-1)/count) du pas nominal.
    const k = count > 1 ? (i - 1) / (count - 1) : 0;
    const damp = Math.max(0, 1 - opts.falloff * k);

    const stepOffset: V3 = [
      opts.step[0] * damp,
      opts.step[1] * damp,
      opts.step[2] * damp,
    ];

    if (opts.mode === 'cumulative') {
      // Le décalage repart dans le repère déjà tourné de la copie précédente,
      // c'est ce qui fait s'enrouler la chaîne au lieu de rester droite.
      const rotated = rotateAroundOrigin(stepOffset, rotation);
      offset = [offset[0] + rotated[0], offset[1] + rotated[1], offset[2] + rotated[2]];
    } else {
      offset = [offset[0] + stepOffset[0], offset[1] + stepOffset[1], offset[2] + stepOffset[2]];
    }

    rotation = [
      rotation[0] + opts.stepRotation[0] * damp,
      rotation[1] + opts.stepRotation[1] * damp,
      rotation[2] + opts.stepRotation[2] * damp,
    ];
    scale *= opts.stepScale;

    out.push({
      offset: [offset[0], offset[1], offset[2]],
      rotation: [rotation[0], rotation[1], rotation[2]],
      scale,
      index: i,
    });
  }

  return out;
}

/** Miroir d'un vecteur sur un axe ('x' | 'y' | 'z'). */
export function mirrorVector(v: V3, axis: 'x' | 'y' | 'z'): V3 {
  const i = axis === 'x' ? 0 : axis === 'y' ? 1 : 2;
  const out: V3 = [v[0], v[1], v[2]];
  out[i] = -out[i];
  return out;
}

/**
 * Miroir d'une rotation Euler sur un axe. Sur l'axe miroir on garde l'angle,
 * sur les deux autres on l'inverse — sinon la copie symétrique part de travers.
 */
export function mirrorRotation(r: V3, axis: 'x' | 'y' | 'z'): V3 {
  const i = axis === 'x' ? 0 : axis === 'y' ? 1 : 2;
  return [
    i === 0 ? r[0] : -r[0],
    i === 1 ? r[1] : -r[1],
    i === 2 ? r[2] : -r[2],
  ];
}
