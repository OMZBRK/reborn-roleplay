/**
 * Motion Lab — « pose, shape, and keep the keys ».
 *
 * Le principe qui gouverne tout ce fichier : **on ne resample jamais**. Une
 * animation est le travail d'un animateur, pas une courbe à échantillonner.
 * Chaque outil ici déplace, copie ou ajuste les clés **existantes** ; aucun ne
 * remplace l'animation par une purée de keyframes générées.
 *
 * Les quatre gestes :
 *  - **Pose**      : capturer la pose courante, la ranger, la reposer ailleurs.
 *  - **Mirror**    : symétriser une pose gauche/droite en une fois.
 *  - **Shape**     : retimer, étirer, décaler, changer l'interpolation.
 *  - **Keep**      : fermer une boucle sans couture, déphaser un membre.
 */
import {
  retime, scaleTimes, offsetTimes, phaseShift, loopClosureTime,
  mirrorBoneName, type EasingName,
} from '../core/easing.ts';

type Result = { ok: boolean; message: string };

const POSE_KEY = 'reborn_motion_poses';
const CHANNELS = ['rotation', 'position', 'scale'] as const;
type Channel = (typeof CHANNELS)[number];

function currentAnimation(): any | null {
  const A = (globalThis as any).Animation;
  return A?.selected ?? null;
}

function currentTime(): number {
  return (globalThis as any).Timeline?.time ?? 0;
}

function selectedKeyframes(): any[] {
  const T = (globalThis as any).Timeline;
  const sel = T?.selected ?? [];
  return Array.isArray(sel) ? sel : [];
}

function requireAnimation(): { anim: any } | { error: string } {
  const anim = currentAnimation();
  if (!anim) {
    return { error: 'Aucune animation sélectionnée — passe en mode Animate et choisis-en une.' };
  }
  return { anim };
}

// --- Pose : capture / application ------------------------------------------

interface StoredPose {
  name: string;
  savedAt: string;
  /** boneName → channel → [x, y, z] */
  bones: Record<string, Partial<Record<Channel, [number, number, number]>>>;
}

function readPoses(): StoredPose[] {
  try {
    const raw = localStorage.getItem(POSE_KEY);
    return raw ? (JSON.parse(raw) as StoredPose[]) : [];
  } catch {
    return [];
  }
}

function writePoses(p: StoredPose[]): void {
  localStorage.setItem(POSE_KEY, JSON.stringify(p));
}

export function listPoses(): StoredPose[] {
  return readPoses();
}

/**
 * Valeur d'un canal pour un animator à l'instant courant.
 * On demande d'abord à Blockbench d'interpoler (c'est lui la référence) ;
 * si l'API n'est pas disponible, on retombe sur la keyframe exacte.
 */
function readChannel(animator: any, channel: Channel, time: number): [number, number, number] | null {
  try {
    if (typeof animator.interpolate === 'function') {
      const v = animator.interpolate(channel, false);
      if (Array.isArray(v) && v.length >= 3) {
        return [Number(v[0]) || 0, Number(v[1]) || 0, Number(v[2]) || 0];
      }
    }
  } catch {
    /* on tente le repli ci-dessous */
  }
  const kfs: any[] = animator[channel] ?? [];
  const kf = kfs.find((k) => Math.abs(k.time - time) < 1e-4);
  if (!kf) return null;
  return [Number(kf.get('x')) || 0, Number(kf.get('y')) || 0, Number(kf.get('z')) || 0];
}

export function capturePose(name: string): Result {
  const r = requireAnimation();
  if ('error' in r) return { ok: false, message: r.error };
  const clean = (name || '').trim();
  if (!clean) return { ok: false, message: 'Donne un nom à la pose.' };

  const time = currentTime();
  const bones: StoredPose['bones'] = {};
  let count = 0;

  for (const uuid in r.anim.animators) {
    const animator = r.anim.animators[uuid];
    const boneName = animator?.name;
    if (!boneName) continue;
    const entry: StoredPose['bones'][string] = {};
    for (const ch of CHANNELS) {
      const v = readChannel(animator, ch, time);
      if (v) entry[ch] = v;
    }
    if (Object.keys(entry).length) {
      bones[boneName] = entry;
      count++;
    }
  }

  if (!count) {
    return { ok: false, message: 'Aucun os animé à cet instant — pose au moins une clé d’abord.' };
  }

  const poses = readPoses().filter((p) => p.name !== clean);
  poses.push({ name: clean, savedAt: new Date().toISOString(), bones });
  writePoses(poses);
  return { ok: true, message: `Pose « ${clean} » capturée : ${count} os à t=${time.toFixed(2)}s.` };
}

/** Crée ou remplace une keyframe sur un canal, à un instant donné. */
function setKeyframe(animator: any, channel: Channel, time: number, v: [number, number, number]): boolean {
  try {
    if (typeof animator.createKeyframe === 'function') {
      animator.createKeyframe({ x: v[0], y: v[1], z: v[2] }, time, channel, false, false);
      return true;
    }
  } catch (e) {
    console.warn('[reborn-motion] createKeyframe', channel, e);
  }
  return false;
}

export function applyPose(name: string): Result {
  const r = requireAnimation();
  if ('error' in r) return { ok: false, message: r.error };
  const pose = readPoses().find((p) => p.name === name);
  if (!pose) return { ok: false, message: `Pose « ${name} » introuvable.` };

  const time = currentTime();
  const Undo = (globalThis as any).Undo;
  Undo.initEdit({ animations: [r.anim] });

  let applied = 0, missing: string[] = [];
  for (const [boneName, channels] of Object.entries(pose.bones)) {
    const animator = findAnimatorByName(r.anim, boneName);
    if (!animator) { missing.push(boneName); continue; }
    for (const ch of CHANNELS) {
      const v = channels[ch];
      if (v && setKeyframe(animator, ch, time, v)) applied++;
    }
  }

  Undo.finishEdit('Motion Lab — pose appliquée', { animations: [r.anim] });
  (globalThis as any).Animator?.preview?.();

  const warn = missing.length ? ` (${missing.length} os absent(s) : ${missing.slice(0, 3).join(', ')}…)` : '';
  return {
    ok: applied > 0,
    message: applied
      ? `Pose « ${name} » posée à t=${time.toFixed(2)}s — ${applied} clé(s)${warn}.`
      : `Aucun os correspondant dans ce modèle${warn}.`,
  };
}

function findAnimatorByName(anim: any, name: string): any | null {
  for (const uuid in anim.animators) {
    if (anim.animators[uuid]?.name === name) return anim.animators[uuid];
  }
  return null;
}

export function deletePose(name: string): Result {
  const poses = readPoses();
  const next = poses.filter((p) => p.name !== name);
  if (next.length === poses.length) return { ok: false, message: `Pose « ${name} » introuvable.` };
  writePoses(next);
  return { ok: true, message: `Pose « ${name} » supprimée.` };
}

// --- Mirror ----------------------------------------------------------------

/**
 * Symétrise la pose de l'instant courant : chaque os « left » reçoit la valeur
 * de son homologue « right », et inversement, avec les inversions de signe qui
 * vont bien (Y et Z en rotation, X en position).
 *
 * Fait à la main, c'est le genre de tâche où une erreur de signe passe
 * inaperçue jusqu'à ce que le personnage marche de travers.
 */
export function mirrorPose(): Result {
  const r = requireAnimation();
  if ('error' in r) return { ok: false, message: r.error };

  const time = currentTime();
  const snapshot: Record<string, Partial<Record<Channel, [number, number, number]>>> = {};
  for (const uuid in r.anim.animators) {
    const a = r.anim.animators[uuid];
    if (!a?.name) continue;
    const entry: Partial<Record<Channel, [number, number, number]>> = {};
    for (const ch of CHANNELS) {
      const v = readChannel(a, ch, time);
      if (v) entry[ch] = v;
    }
    if (Object.keys(entry).length) snapshot[a.name] = entry;
  }

  const Undo = (globalThis as any).Undo;
  Undo.initEdit({ animations: [r.anim] });
  let applied = 0;

  for (const [boneName, channels] of Object.entries(snapshot)) {
    const twin = mirrorBoneName(boneName);
    if (!twin || !snapshot[twin]) continue;
    const target = findAnimatorByName(r.anim, boneName);
    if (!target) continue;
    const src = snapshot[twin];

    for (const ch of CHANNELS) {
      const v = src[ch];
      if (!v) continue;
      // Convention Blockbench : le miroir gauche/droite est sur X.
      const m: [number, number, number] =
        ch === 'rotation' ? [v[0], -v[1], -v[2]]
        : ch === 'position' ? [-v[0], v[1], v[2]]
        : [v[0], v[1], v[2]];
      if (setKeyframe(target, ch, time, m)) applied++;
    }
  }

  Undo.finishEdit('Motion Lab — pose symétrisée', { animations: [r.anim] });
  (globalThis as any).Animator?.preview?.();

  return {
    ok: applied > 0,
    message: applied
      ? `Pose symétrisée : ${applied} clé(s) à t=${time.toFixed(2)}s.`
      : 'Aucune paire gauche/droite trouvée (nomme tes os « bras_left » / « bras_right »).',
  };
}

// --- Shape : retiming des clés existantes ----------------------------------

export interface ShapeOptions {
  /** Redistribution des instants selon une courbe. */
  curve: EasingName;
  /** Facteur d'étirement temporel autour de la première clé. */
  timeScale: number;
  /** Décalage en secondes. */
  timeOffset: number;
  /** Interpolation à forcer sur les clés ('' = ne pas toucher). */
  interpolation: '' | 'linear' | 'catmullrom' | 'step';
  /** Portée : les clés sélectionnées, ou toute l'animation. */
  scope: 'selection' | 'all';
}

function collectKeyframes(anim: any, scope: 'selection' | 'all'): any[] {
  if (scope === 'selection') {
    const sel = selectedKeyframes();
    if (sel.length) return sel;
  }
  const out: any[] = [];
  for (const uuid in anim.animators) {
    const a = anim.animators[uuid];
    for (const ch of CHANNELS) {
      const kfs = a?.[ch];
      if (Array.isArray(kfs)) out.push(...kfs);
    }
  }
  return out;
}

export function shapeKeys(opts: ShapeOptions): Result {
  const r = requireAnimation();
  if ('error' in r) return { ok: false, message: r.error };

  const kfs = collectKeyframes(r.anim, opts.scope);
  if (!kfs.length) {
    return {
      ok: false,
      message: opts.scope === 'selection'
        ? 'Aucune keyframe sélectionnée — sélectionne-les dans la timeline, ou passe en portée « toute l’animation ».'
        : 'Cette animation n’a aucune keyframe.',
    };
  }

  const Undo = (globalThis as any).Undo;
  Undo.initEdit({ animations: [r.anim] });

  // On travaille sur les instants, pas sur les valeurs : la performance de
  // l'animateur est conservée, seule sa répartition dans le temps change.
  const times = kfs.map((k) => k.time);
  let next = times;
  if (opts.curve !== 'linear') next = retime(next, opts.curve);
  if (Math.abs(opts.timeScale - 1) > 1e-6) {
    next = scaleTimes(next, opts.timeScale, Math.min(...times));
  }
  if (Math.abs(opts.timeOffset) > 1e-6) next = offsetTimes(next, opts.timeOffset);

  // L'ordre de `times` suit celui de `kfs`, mais retime() a trié : on remappe
  // par rang pour ne pas réattribuer les instants au hasard.
  const order = times.map((t, i) => [t, i] as const).sort((a, b) => a[0] - b[0]);
  order.forEach(([, originalIndex], rank) => {
    kfs[originalIndex].time = Math.max(0, next[rank]);
  });

  if (opts.interpolation) {
    for (const k of kfs) k.interpolation = opts.interpolation;
  }

  Undo.finishEdit('Motion Lab — clés remodelées', { animations: [r.anim] });
  (globalThis as any).Animator?.preview?.();

  return {
    ok: true,
    message: `${kfs.length} clé(s) remodelée(s)${opts.curve !== 'linear' ? ` (courbe ${opts.curve})` : ''}${opts.interpolation ? `, interpolation ${opts.interpolation}` : ''}.`,
  };
}

// --- Keep : boucle et déphasage --------------------------------------------

/**
 * Ferme la boucle : recopie la pose de t=0 à la fin de l'animation.
 * Sans ça, une animation en boucle « claque » à chaque tour parce que la
 * dernière image ne raccorde pas la première.
 */
export function closeLoop(): Result {
  const r = requireAnimation();
  if ('error' in r) return { ok: false, message: r.error };

  const length = Number(r.anim.length) || 0;
  const end = loopClosureTime(length);
  if (end === null) {
    return { ok: false, message: 'L’animation n’a pas de durée — règle sa longueur d’abord.' };
  }

  const Undo = (globalThis as any).Undo;
  Undo.initEdit({ animations: [r.anim] });
  let applied = 0;

  for (const uuid in r.anim.animators) {
    const a = r.anim.animators[uuid];
    if (!a?.name) continue;
    for (const ch of CHANNELS) {
      const kfs: any[] = a[ch] ?? [];
      if (!kfs.length) continue;
      const first = kfs.slice().sort((x, y) => x.time - y.time)[0];
      if (!first) continue;
      const v: [number, number, number] = [
        Number(first.get('x')) || 0,
        Number(first.get('y')) || 0,
        Number(first.get('z')) || 0,
      ];
      if (setKeyframe(a, ch, end, v)) applied++;
    }
  }

  Undo.finishEdit('Motion Lab — boucle fermée', { animations: [r.anim] });
  (globalThis as any).Animator?.preview?.();

  return {
    ok: applied > 0,
    message: applied
      ? `Boucle fermée : ${applied} clé(s) recopiée(s) à t=${end.toFixed(2)}s.`
      : 'Aucune clé à recopier.',
  };
}

/**
 * Déphase les clés sélectionnées dans la boucle — pour désynchroniser deux
 * membres (une patte à contretemps de l'autre) sans réanimer quoi que ce soit.
 */
export function shiftPhase(shift: number): Result {
  const r = requireAnimation();
  if ('error' in r) return { ok: false, message: r.error };

  const kfs = selectedKeyframes();
  if (!kfs.length) {
    return { ok: false, message: 'Sélectionne les keyframes à déphaser dans la timeline.' };
  }
  const length = Number(r.anim.length) || 0;
  if (length <= 0) return { ok: false, message: 'L’animation n’a pas de durée.' };

  const Undo = (globalThis as any).Undo;
  Undo.initEdit({ animations: [r.anim] });
  const shifted = phaseShift(kfs.map((k) => k.time), length, shift);
  kfs.forEach((k, i) => { k.time = shifted[i]; });
  Undo.finishEdit('Motion Lab — déphasage', { animations: [r.anim] });
  (globalThis as any).Animator?.preview?.();

  return { ok: true, message: `${kfs.length} clé(s) déphasée(s) de ${shift.toFixed(2)}s.` };
}
