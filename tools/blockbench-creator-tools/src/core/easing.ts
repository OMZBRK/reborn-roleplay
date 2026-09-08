/**
 * Courbes et retiming de keyframes — 100% pur, testable en node.
 *
 * Sert à Motion Lab : « pose, shape, and keep the keys ». Le principe est que
 * l'animateur garde SES clés — on ne resample jamais l'animation en la
 * remplaçant par une purée de keyframes générées. On déplace les clés
 * existantes dans le temps et on ajuste leur interpolation.
 *
 * Un resample détruirait le travail : c'est la différence entre un outil qui
 * assiste et un outil qui écrase.
 */

export type EasingName =
  | 'linear'
  | 'ease_in'
  | 'ease_out'
  | 'ease_in_out'
  | 'anticipate'
  | 'overshoot'
  | 'bounce_out';

/** Toutes les courbes prennent et rendent 0..1. */
export function ease(name: EasingName, t: number): number {
  const x = t < 0 ? 0 : t > 1 ? 1 : t;
  switch (name) {
    case 'ease_in': return x * x * x;
    case 'ease_out': return 1 - Math.pow(1 - x, 3);
    case 'ease_in_out':
      return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    case 'anticipate': {
      // Recule un peu avant de partir — le petit contre-mouvement qui rend
      // un départ crédible au lieu d'un démarrage mécanique.
      // c3 = c1 + 1 est ce qui garantit ease(1) === 1 : avec la même constante
      // aux deux termes, la courbe retombe à 0 en fin de course et l'animation
      // revient à son point de départ au lieu d'atteindre la pose visée.
      const c1 = 1.70158, c3 = c1 + 1;
      return c3 * x * x * x - c1 * x * x;
    }
    case 'overshoot': {
      // Dépasse la cible puis revient — l'accent de fin de geste.
      const c1 = 1.70158, c3 = c1 + 1;
      return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }
    case 'bounce_out': {
      const n1 = 7.5625, d1 = 2.75;
      let v = x;
      if (v < 1 / d1) return n1 * v * v;
      if (v < 2 / d1) return n1 * (v -= 1.5 / d1) * v + 0.75;
      if (v < 2.5 / d1) return n1 * (v -= 2.25 / d1) * v + 0.9375;
      return n1 * (v -= 2.625 / d1) * v + 0.984375;
    }
    default: return x;
  }
}

/**
 * Redistribue une liste de temps de keyframes selon une courbe, en gardant
 * le premier et le dernier en place.
 *
 * L'animateur a posé ses clés à des instants qui racontent quelque chose ;
 * on ne change que leur **répartition**, pas leur nombre ni leur ordre.
 */
export function retime(times: number[], curve: EasingName): number[] {
  if (times.length < 3) return [...times];
  const sorted = [...times].sort((a, b) => a - b);
  const t0 = sorted[0];
  const t1 = sorted[sorted.length - 1];
  const span = t1 - t0;
  if (span <= 1e-9) return sorted;
  return sorted.map((t, i) => {
    if (i === 0) return t0;
    if (i === sorted.length - 1) return t1;
    return t0 + ease(curve, (t - t0) / span) * span;
  });
}

/**
 * Met à l'échelle une liste de temps autour d'un pivot.
 * `factor` < 1 resserre (geste plus vif), > 1 étale.
 */
export function scaleTimes(times: number[], factor: number, pivot: number): number[] {
  const f = Math.max(0.01, factor);
  return times.map((t) => pivot + (t - pivot) * f);
}

/** Décale une liste de temps, sans jamais passer sous zéro. */
export function offsetTimes(times: number[], delta: number): number[] {
  return times.map((t) => Math.max(0, t + delta));
}

/**
 * Temps d'une boucle sans couture : renvoie l'instant auquel copier la pose
 * initiale pour que la boucle se referme, ou null si l'animation n'a pas de
 * durée exploitable.
 *
 * Sans ça, une animation en boucle « claque » à chaque tour parce que la
 * dernière image ne raccorde pas la première.
 */
export function loopClosureTime(length: number, epsilon = 1e-4): number | null {
  return length > epsilon ? length : null;
}

/**
 * Décale cycliquement des temps dans une boucle de longueur `length`.
 * Utile pour désynchroniser deux membres (une patte à contretemps de l'autre)
 * sans réanimer quoi que ce soit.
 */
export function phaseShift(times: number[], length: number, shift: number): number[] {
  if (length <= 1e-9) return [...times];
  return times.map((t) => {
    let v = (t + shift) % length;
    if (v < 0) v += length;
    return v;
  });
}

/**
 * Symétrise un nom d'os gauche/droite : `leftArm` → `rightArm`,
 * `bras_left` → `bras_right`, `jambe_l` → `jambe_r`.
 *
 * La difficulté est de repérer le token de côté SANS taper à côté. Les modèles
 * Minecraft vanilla nomment leurs os en camelCase collé (`leftArm`), tandis que
 * les riggeurs francophones séparent (`bras_left`, `jambe_l`). Il faut couvrir
 * les deux sans transformer « clavicule » ou « bright » au passage.
 *
 * Un token compte donc s'il est délimité par : un début/fin de nom, un
 * séparateur (`_`, `-`, `.`, espace), ou une transition de casse (camelCase).
 * Les abréviations `l` / `r`, bien plus ambiguës, exigent un vrai séparateur.
 */
const SEPARATORS = new Set(['_', '-', '.', ' ']);

function isBoundaryBefore(name: string, i: number, strict: boolean): boolean {
  if (i === 0) return true;
  const prev = name[i - 1];
  if (SEPARATORS.has(prev)) return true;
  if (strict) return false;
  // Transition de casse : « ...armLeft » — le token démarre en majuscule.
  return /[a-z0-9]/.test(prev) && /[A-Z]/.test(name[i]);
}

function isBoundaryAfter(name: string, i: number, strict: boolean): boolean {
  if (i >= name.length) return true;
  const next = name[i];
  if (SEPARATORS.has(next)) return true;
  if (strict) return false;
  // « leftArm » — le token est suivi d'une majuscule ou d'un chiffre.
  return /[A-Z0-9]/.test(next);
}

/** Reporte la casse de `sample` sur `word` (Left→Right, LEFT→RIGHT, left→right). */
function matchCase(word: string, sample: string): string {
  if (sample === sample.toUpperCase() && sample !== sample.toLowerCase()) return word.toUpperCase();
  if (sample[0] === sample[0].toUpperCase()) return word[0].toUpperCase() + word.slice(1);
  return word;
}

export function mirrorBoneName(name: string): string | null {
  const pairs: Array<[string, string, boolean]> = [
    // [token, remplacement, séparateur strict requis]
    ['left', 'right', false],
    ['right', 'left', false],
    ['l', 'r', true],
    ['r', 'l', true],
  ];

  const lower = name.toLowerCase();
  for (const [token, replacement, strict] of pairs) {
    let from = 0;
    for (;;) {
      const i = lower.indexOf(token, from);
      if (i < 0) break;
      const end = i + token.length;
      if (isBoundaryBefore(name, i, strict) && isBoundaryAfter(name, end, strict)) {
        const matched = name.slice(i, end);
        return name.slice(0, i) + matchCase(replacement, matched) + name.slice(end);
      }
      from = i + 1;
    }
  }
  return null;
}
