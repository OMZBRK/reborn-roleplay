/**
 * Outil Shade — rampe hand-painted depuis une couleur de base.
 *
 * Ce que ça évite de peindre à la main : mélanger soi-même des tons d'ombre et
 * de lumière cohérents. À partir d'une couleur, génère une rampe (ombres
 * refroidies + saturées, lumières réchauffées) puis :
 *   - l'installe dans la palette Blockbench pour peindre directement avec, et/ou
 *   - « ombre » la texture d'un coup (remap de luminance sur la rampe → calque).
 */
import { generateRamp, hexToRgb, luminance, rampIndexForLuminance, type RGB } from '../core/color.ts';
import { emitLayer } from '../core/layers.ts';

export interface ShadeOptions {
  base: string;
  steps: number;
  valueRange: number;
  hueShift: number;
  satBoost: number;
  action: 'palette' | 'remap' | 'both';
}

function activeTexture(): Texture | null {
  const T = Texture as any;
  return T.selected ?? (T.getDefault ? T.getDefault() : null) ?? T.all?.[0] ?? null;
}

/** Installe la rampe dans la palette Blockbench (best-effort — API non typée). */
function installRamp(hexes: string[]): boolean {
  const CP = (globalThis as any).ColorPanel;
  if (!CP || !Array.isArray(CP.palette)) return false;
  try {
    for (const hx of hexes) if (!CP.palette.includes(hx)) CP.palette.push(hx);
    // Positionne la couleur active sur le ton médian de la rampe.
    CP.set?.(hexes[Math.floor(hexes.length / 2)]);
    return true;
  } catch {
    return false;
  }
}

/** Lit les pixels composités de la texture dans un ImageData. */
function readTextureImageData(texture: Texture): ImageData | null {
  const w = texture.width, h = texture.height;
  const cnv = document.createElement('canvas');
  cnv.width = w; cnv.height = h;
  const ctx = cnv.getContext('2d');
  if (!ctx) return null;
  const t = texture as any;
  if (t.img && t.img.complete !== false) ctx.drawImage(t.img, 0, 0, w, h);
  else if (t.canvas) ctx.drawImage(t.canvas, 0, 0);
  else return null;
  return ctx.getImageData(0, 0, w, h);
}

/** Remap la luminance de chaque pixel opaque sur la rampe → nouvel ImageData. */
function remapToRamp(src: ImageData, ramp: string[]): ImageData {
  const rgbRamp: RGB[] = ramp.map(hexToRgb);
  const out = new ImageData(src.width, src.height);
  const si = src.data, di = out.data;
  for (let i = 0; i < si.length; i += 4) {
    const a = si[i + 3];
    if (a === 0) continue; // transparent → laissé vide
    const idx = rampIndexForLuminance(luminance(si[i], si[i + 1], si[i + 2]), rgbRamp.length);
    const [r, g, b] = rgbRamp[idx];
    di[i] = r; di[i + 1] = g; di[i + 2] = b; di[i + 3] = a;
  }
  return out;
}

export function applyShade(opts: ShadeOptions): { ok: boolean; message: string } {
  const ramp = generateRamp(opts.base, {
    steps: opts.steps,
    valueRange: opts.valueRange,
    hueShift: opts.hueShift,
    satBoost: opts.satBoost,
  });

  let didPalette = false;
  if (opts.action === 'palette' || opts.action === 'both') {
    didPalette = installRamp(ramp);
  }

  let didRemap = false;
  if (opts.action === 'remap' || opts.action === 'both') {
    const texture = activeTexture();
    if (!texture) return { ok: false, message: 'Aucune texture sélectionnée pour l\'ombrage.' };
    const src = readTextureImageData(texture);
    if (!src) return { ok: false, message: 'Impossible de lire les pixels de la texture.' };
    const remapped = remapToRamp(src, ramp);
    emitLayer(texture, 'Shade', 'default', remapped, 'Ombrer sur la rampe');
    didRemap = true;
  }

  const parts: string[] = [`Rampe ${ramp.length} tons générée`];
  if (opts.action !== 'remap') parts.push(didPalette ? 'palette mise à jour' : 'palette indisponible');
  if (didRemap) parts.push('texture ombrée (calque)');
  return { ok: true, message: parts.join(' · ') };
}
