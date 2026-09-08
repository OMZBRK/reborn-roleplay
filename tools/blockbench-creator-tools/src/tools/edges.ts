/**
 * Outil Edges — liserés de lumière sur les arêtes saillantes, coutures sombres
 * dans les rentrants.
 *
 * Ce que ça évite de peindre à la main : le contour clair qu'on trace au pixel
 * près sur chaque arête pour « détacher » une forme, et la ligne sombre qui
 * marque les jonctions. Sur un modèle low-poly c'est des dizaines de traits,
 * tous à refaire dès qu'on bouge une pièce.
 *
 * Méthode, en espace UV plutôt qu'en 3D :
 *  1. une passe de rasterisation remplit trois buffers — id de face, normale,
 *     couverture ;
 *  2. un texel dont un voisin appartient à une autre face (ou au vide) est un
 *     texel de bord ;
 *  3. la normale du voisin dit si l'arête est saillante ou rentrante ;
 *  4. une transformée de distance donne l'épaisseur du liseré.
 *
 * Travailler en UV a un avantage décisif : le bord d'un îlot UV est traité
 * comme une arête, donc les coutures reçoivent aussi leur trait — ce qu'une
 * approche purement géométrique raterait.
 */
import { forEachTexel } from '../core/geometry.ts';
import { hexToRgb } from '../core/color.ts';
import { emitLayer } from '../core/layers.ts';
import { classifyEdge, edgeFalloff, distanceTransform, type V3 } from '../core/field.ts';

export interface EdgesOptions {
  /** Liseré clair sur les arêtes saillantes. */
  highlightOn: boolean;
  highlightColor: string;
  highlightStrength: number; // 0..1
  /** Ligne sombre dans les rentrants et sur les coutures UV. */
  seamOn: boolean;
  seamColor: string;
  seamStrength: number;      // 0..1
  /** Épaisseur du liseré, en pixels de texture. */
  width: number;
  /**
   * Seuil de planéité, en degrés. En dessous, deux faces sont considérées
   * coplanaires et l'arête est ignorée — sinon on souligne des faux bords au
   * milieu d'une surface continue.
   */
  flatAngle: number;
}

function activeTexture(): Texture | null {
  const T = Texture as any;
  return T.selected ?? (T.getDefault ? T.getDefault() : null) ?? T.all?.[0] ?? null;
}

export function bakeEdges(opts: EdgesOptions): { ok: boolean; message: string } {
  const texture = activeTexture();
  if (!texture) return { ok: false, message: 'Aucune texture sélectionnée.' };
  if (!texture.width || !texture.height) {
    return { ok: false, message: 'Texture sans dimensions valides.' };
  }
  if (!opts.highlightOn && !opts.seamOn) {
    return { ok: false, message: 'Active au moins le liseré clair ou la couture sombre.' };
  }

  const W = texture.width, H = texture.height;
  const N = W * H;

  // --- 1. Buffers de la passe de rasterisation ---------------------------
  const faceId = new Int32Array(N).fill(-1);
  const nx = new Float32Array(N);
  const ny = new Float32Array(N);
  const nz = new Float32Array(N);
  let texels = 0;

  forEachTexel(texture, (x, y, _wx, _wy, _wz, ax, ay, az, fid) => {
    const i = y * W + x;
    faceId[i] = fid;
    nx[i] = ax; ny[i] = ay; nz[i] = az;
    texels++;
  });

  if (texels === 0) {
    return { ok: false, message: 'Aucun texel couvert — la texture est-elle bien mappée ?' };
  }

  // --- 2. Détection des bords + classification ---------------------------
  // 0 = pixel de bord (graine de la transformée de distance), 1 = intérieur.
  const convexMask = new Uint8Array(N).fill(1);
  const concaveMask = new Uint8Array(N).fill(1);
  const flatThreshold = Math.cos((Math.max(0, opts.flatAngle) * Math.PI) / 180);

  const NEIGHBOURS: Array<[number, number]> = [[1, 0], [-1, 0], [0, 1], [0, -1]];

  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const i = y * W + x;
      const self = faceId[i];
      if (self < 0) continue; // texel vide : pas de bord à lui seul

      const nSelf: V3 = [nx[i], ny[i], nz[i]];
      let convex = false, concave = false;

      for (const [dx, dy] of NEIGHBOURS) {
        const ox = x + dx, oy = y + dy;
        if (ox < 0 || oy < 0 || ox >= W || oy >= H) {
          // Bord de la texture : traité comme une couture.
          concave = true;
          continue;
        }
        const j = oy * W + ox;
        const other = faceId[j];
        if (other === self) continue;

        if (other < 0) {
          // Bord d'îlot UV : c'est une couture, on la marque toujours — c'est
          // exactement le trait que l'artiste passe son temps à tracer.
          concave = true;
          continue;
        }

        const kind = classifyEdge(
          nSelf,
          [nx[j], ny[j], nz[j]],
          [dx, dy, 0] as V3,
          flatThreshold,
        );
        if (kind === 'convex') convex = true;
        else if (kind === 'concave') concave = true;
      }

      if (convex) convexMask[i] = 0;
      if (concave) concaveMask[i] = 0;
    }
  }

  // --- 3. Épaisseur par transformée de distance --------------------------
  const width = Math.max(0.5, opts.width);
  const hlDist = opts.highlightOn ? distanceTransform(convexMask, W, H, 64) : null;
  const seamDist = opts.seamOn ? distanceTransform(concaveMask, W, H, 64) : null;

  // --- 4. Composition des passes -----------------------------------------
  const [hr, hg, hb] = hexToRgb(opts.highlightColor);
  const [sr, sg, sb] = hexToRgb(opts.seamColor);

  let hlPixels = 0, seamPixels = 0;
  const highlight = new ImageData(W, H);
  const seam = new ImageData(W, H);
  const hd = highlight.data, sd = seam.data;

  for (let i = 0; i < N; i++) {
    const o = i * 4;
    const covered = faceId[i] >= 0;

    if (hlDist) {
      // screen : noir = neutre.
      const k = covered ? edgeFalloff(hlDist[i], width) * opts.highlightStrength : 0;
      hd[o] = Math.round(hr * k);
      hd[o + 1] = Math.round(hg * k);
      hd[o + 2] = Math.round(hb * k);
      hd[o + 3] = 255;
      if (k > 0.01) hlPixels++;
    }
    if (seamDist) {
      // multiply : blanc = neutre.
      const k = covered ? edgeFalloff(seamDist[i], width) * opts.seamStrength : 0;
      const inv = 1 - k;
      sd[o] = Math.round(255 * inv + sr * k);
      sd[o + 1] = Math.round(255 * inv + sg * k);
      sd[o + 2] = Math.round(255 * inv + sb * k);
      sd[o + 3] = 255;
      if (k > 0.01) seamPixels++;
    }
  }

  if (opts.highlightOn) emitLayer(texture, 'Edge Highlight', 'screen', highlight, 'Arêtes — liseré clair');
  if (opts.seamOn) emitLayer(texture, 'Edge Seam', 'multiply', seam, 'Arêtes — couture sombre');

  const parts: string[] = [];
  if (opts.highlightOn) parts.push(`${hlPixels} px de liseré`);
  if (opts.seamOn) parts.push(`${seamPixels} px de couture`);
  return { ok: true, message: `Arêtes traitées : ${parts.join(', ')}.` };
}
