/**
 * Panneau latéral Reborn Creator Tools — l'UI principale.
 *
 * Quatre workspaces, un onglet chacun, calqués sur la suite de référence :
 *   🧱 Blockout   (modeling)  — block, chain, pose, reuse
 *   🎨 Handpaint  (texturing) — material, light, edges, AO
 *   🎬 Motion     (animation) — pose, shape, keep the keys
 *   🖼️ Render     (scenes)    — planifié
 *
 * Deux pièges Blockbench gérés ici :
 *  - Les inputs couleur sont des <input type="color"> natifs → valeur hex directe
 *    (le champ Dialog `color` de Blockbench renvoie un objet tinycolor).
 *  - Vue 2 IGNORE les balises <style> dans un template → on injecte le CSS dans
 *    document.head (sinon le panneau s'affiche sans aucun style).
 */
import { bakeAO, AO_TONES, type AOOptions } from '../tools/ao.ts';
import { applyShade, type ShadeOptions } from '../tools/shade.ts';
import { bakeLighting, LIGHT_PRESETS, type LightingOptions } from '../tools/lighting.ts';
import { bakeGradient, gradientPreview, type GradientOptions } from '../tools/gradient.ts';
import { bakeEdges, type EdgesOptions } from '../tools/edges.ts';
import { bakeSurface, surfacePreview, SURFACE_LABELS, type SurfacesOptions } from '../tools/surfaces.ts';
import {
  addBlock, chainSelection, mirrorSelection, centerPivots,
  listParts, savePart, insertPart, deletePart,
} from '../tools/blockout.ts';
import {
  capturePose, applyPose, listPoses, deletePose, mirrorPose,
  shapeKeys, closeLoop, shiftPhase, type ShapeOptions,
} from '../tools/motion.ts';
import { generateRamp } from '../core/color.ts';
import type { ChainOptions } from '../core/chain.ts';

const STYLE_ID = 'reborn-ct-styles';

const CSS = `
.rct { padding: 10px 12px 16px; font-size: 12px; color: var(--color-text); overflow-y: auto; height: 100%; box-sizing: border-box; }
.rct * { box-sizing: border-box; }
.rct-head { display: flex; align-items: center; gap: 9px; margin-bottom: 10px; }
.rct-head .material-icons { font-size: 22px; color: var(--color-accent); }
.rct-head .t { font-size: 13px; font-weight: 700; letter-spacing: .3px; }
.rct-head .s { font-size: 10px; opacity: .55; margin-top: 1px; }

.rct-tabs { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; margin-bottom: 12px; background: var(--color-back); padding: 4px; border-radius: 9px; border: 1px solid var(--color-border); }
.rct-tab { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 7px 2px; border: none; background: transparent; color: var(--color-text); border-radius: 6px; cursor: pointer; font-size: 9.5px; font-weight: 600; letter-spacing: .2px; opacity: .55; transition: background .1s, opacity .1s; }
.rct-tab .material-icons { font-size: 17px; }
.rct-tab:hover { opacity: .85; background: var(--color-button); }
.rct-tab.on { opacity: 1; background: var(--color-accent); color: var(--color-accent_text, #fff); }
.rct-tab:disabled { opacity: .3; cursor: default; background: transparent; }

.rct-lead { font-size: 10.5px; line-height: 1.45; opacity: .6; margin: 0 2px 12px; }

.rct-auto { display: flex; align-items: center; justify-content: center; gap: 8px; width: 100%; padding: 11px; background: linear-gradient(135deg, var(--color-accent), color-mix(in srgb, var(--color-accent) 60%, #b060ff)); color: #fff; border: none; border-radius: 9px; cursor: pointer; font-weight: 800; font-size: 13px; letter-spacing: .3px; box-shadow: 0 2px 8px rgba(0,0,0,.25); transition: filter .1s, transform .06s; }
.rct-auto:hover { filter: brightness(1.1); }
.rct-auto:active { transform: translateY(1px); }
.rct-auto:disabled { opacity: .5; cursor: default; }
.rct-auto .material-icons { font-size: 19px; }
.rct-auto-desc { font-size: 10.5px; line-height: 1.45; opacity: .6; margin: 6px 2px 14px; }

.rct-card { background: var(--color-ui); border: 1px solid var(--color-border); border-radius: 10px; padding: 11px 12px; margin-bottom: 12px; }
.rct-card-h { display: flex; align-items: center; gap: 7px; margin-bottom: 3px; }
.rct-card-h .material-icons { font-size: 17px; color: var(--color-accent); }
.rct-card-h .n { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; }
.rct-desc { font-size: 10.5px; line-height: 1.45; opacity: .6; margin-bottom: 10px; }
.rct-row { display: flex; align-items: center; gap: 10px; margin: 7px 0; min-height: 20px; }
.rct-row > label { flex: 0 0 80px; opacity: .8; font-size: 11px; }
.rct-row .grow { flex: 1; display: flex; align-items: center; gap: 8px; }
.rct-badge { flex: 0 0 auto; min-width: 30px; text-align: center; font-variant-numeric: tabular-nums; font-size: 10.5px; padding: 2px 6px; border-radius: 5px; background: var(--color-back); border: 1px solid var(--color-border); opacity: .9; }
.rct-hint { font-size: 10px; opacity: .5; margin: 6px 0 0; line-height: 1.4; }

.rct input[type=range] { -webkit-appearance: none; appearance: none; flex: 1; height: 4px; border-radius: 3px; background: var(--color-border); outline: none; }
.rct input[type=range]::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 14px; height: 14px; border-radius: 50%; background: var(--color-accent); cursor: pointer; border: 2px solid var(--color-ui); box-shadow: 0 0 0 1px var(--color-accent); transition: transform .08s; }
.rct input[type=range]::-webkit-slider-thumb:hover { transform: scale(1.15); }
.rct select { flex: 1; background: var(--color-button); color: var(--color-text); border: 1px solid var(--color-border); border-radius: 6px; padding: 3px 6px; font-size: 11px; cursor: pointer; }
.rct input[type=color] { width: 40px; height: 24px; padding: 0; border: 1px solid var(--color-border); border-radius: 6px; background: var(--color-button); cursor: pointer; }
.rct input[type=checkbox] { width: 15px; height: 15px; accent-color: var(--color-accent); cursor: pointer; }
.rct input[type=text], .rct input[type=number] { flex: 1; min-width: 0; background: var(--color-button); color: var(--color-text); border: 1px solid var(--color-border); border-radius: 6px; padding: 3px 7px; font-size: 11px; }
.rct .num3 { display: flex; gap: 5px; flex: 1; }
.rct .num3 input { width: 100%; }

.rct-ramp { display: flex; height: 30px; border-radius: 7px; overflow: hidden; border: 1px solid var(--color-border); margin: 4px 0 8px; }
.rct-ramp > div { flex: 1; }

.rct-apply { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; margin-top: 8px; padding: 8px; background: var(--color-accent); color: var(--color-accent_text, #fff); border: none; border-radius: 7px; cursor: pointer; font-weight: 700; font-size: 12px; transition: filter .1s; }
.rct-apply:hover { filter: brightness(1.12); }
.rct-apply:disabled { opacity: .5; cursor: default; filter: none; }
.rct-apply .material-icons { font-size: 16px; }
.rct-mini { display: flex; gap: 6px; margin-top: 8px; }
.rct-mini button { flex: 1; padding: 6px 4px; background: var(--color-button); color: var(--color-text); border: 1px solid var(--color-border); border-radius: 6px; cursor: pointer; font-size: 11px; font-weight: 600; }
.rct-mini button:hover { background: var(--color-accent); color: var(--color-accent_text, #fff); }
.rct-mini button:disabled { opacity: .4; cursor: default; background: var(--color-button); color: var(--color-text); }

.rct-list { max-height: 128px; overflow-y: auto; border: 1px solid var(--color-border); border-radius: 7px; background: var(--color-back); margin: 6px 0; }
.rct-list .empty { padding: 10px; font-size: 10.5px; opacity: .5; text-align: center; }
.rct-item { display: flex; align-items: center; gap: 6px; padding: 5px 8px; border-bottom: 1px solid var(--color-border); }
.rct-item:last-child { border-bottom: none; }
.rct-item .nm { flex: 1; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rct-item button { padding: 2px 7px; font-size: 10px; background: var(--color-button); color: var(--color-text); border: 1px solid var(--color-border); border-radius: 5px; cursor: pointer; }
.rct-item button:hover { background: var(--color-accent); color: var(--color-accent_text, #fff); }
.rct-item button.del:hover { background: #c0392b; color: #fff; }

.rct-soon { text-align: center; padding: 26px 14px; opacity: .55; }
.rct-soon .material-icons { font-size: 40px; margin-bottom: 8px; display: block; }
.rct-soon .t { font-size: 13px; font-weight: 700; margin-bottom: 6px; }
.rct-soon .d { font-size: 11px; line-height: 1.5; }

.rct-status { margin-top: 2px; padding: 8px 10px; border-radius: 7px; background: var(--color-back); border: 1px solid var(--color-border); font-size: 11px; line-height: 1.4; word-break: break-word; }
.rct-status.ok { border-color: var(--color-accent); }
.rct-status.err { border-color: #e05a5a; color: #ff9a9a; }
.rct-diag { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; margin-top: 8px; padding: 6px; background: transparent; color: var(--color-text); border: 1px solid var(--color-border); border-radius: 7px; cursor: pointer; font-size: 11px; opacity: .8; }
.rct-diag:hover { opacity: 1; background: var(--color-button); }
.rct-diag .material-icons { font-size: 15px; }
`;

function injectStyles(): HTMLStyleElement {
  const old = document.getElementById(STYLE_ID);
  if (old) old.remove();
  const el = document.createElement('style');
  el.id = STYLE_ID;
  el.textContent = CSS;
  document.head.appendChild(el);
  return el;
}

/** Compte la géométrie / textures / animations visibles, pour Diagnostics. */
function diagnosticsReport(): string {
  const g = globalThis as any;
  const textures: any[] = g.Texture?.all ?? [];
  const sel = g.Texture?.selected;
  const anim = g.Animation?.selected;
  return [
    `Cubes : ${g.Cube?.all?.length ?? 0}`,
    `Meshes : ${g.Mesh?.all?.length ?? 0}`,
    `Sélection : ${g.Outliner?.selected?.length ?? 0} élément(s)`,
    `Textures : ${textures.length}`,
    sel ? `Texture active : ${sel.name} (${sel.width}×${sel.height})` : 'Texture active : AUCUNE — sélectionnes-en une',
    `Animations : ${g.Animation?.all?.length ?? 0}`,
    anim ? `Animation active : ${anim.name} (${anim.length}s)` : 'Animation active : aucune',
    `Keyframes sélectionnées : ${g.Timeline?.selected?.length ?? 0}`,
    `Format : ${g.Format?.id ?? 'inconnu'}`,
    `Palette : ${Array.isArray(g.ColorPanel?.palette) ? g.ColorPanel.palette.length + ' couleurs' : 'indisponible'}`,
    `THREE : ${g.THREE ? 'ok' : 'absent'}`,
  ].join('\n');
}

export function createHandpaintedPanel(): { delete: () => void } {
  const styleEl = injectStyles();

  const panel = new Panel('reborn_creator_tools_panel', {
    name: 'Reborn Creator Tools',
    id: 'reborn_creator_tools_panel',
    icon: 'construction',
    growable: true,
    resizable: true,
    default_side: 'right',
    default_position: {
      slot: 'right_bar',
      float_position: [0, 0],
      float_size: [340, 700],
      height: 700,
      folded: false,
    },
    component: {
      data() {
        return {
          tab: 'paint',
          status: 'Choisis un workspace, puis un outil.',
          statusKind: '',
          busy: false,

          // --- Handpainted ---
          ao_tone: 'cool', ao_color: '#16202e', ao_intensity: 1, ao_radius: 4,
          ao_samples: 24, ao_target: 'layer', ao_dither: false, ao_levels: 4,
          li_dir: 'top_front', li_shadow: 0.55, li_hl: 0.45,
          li_shadow_color: '#1a2636', li_hl_color: '#fff0cf',
          li_shadow_on: true, li_hl_on: true,
          sh_base: '#7a5a3c', sh_steps: 5, sh_range: 0.6, sh_hue: 12,
          sh_sat: 0.12, sh_action: 'both',
          gr_axis: 'y', gr_flip: false, gr_dark: '#2a3550', gr_light: '#fff4dd',
          gr_strength: 0.55, gr_falloff: 'smooth', gr_bands: 0, gr_target: 'layer',
          ed_hl_on: true, ed_hl_color: '#fff6e0', ed_hl_strength: 0.55,
          ed_seam_on: true, ed_seam_color: '#1a1220', ed_seam_strength: 0.5,
          ed_width: 1.5, ed_flat: 12,
          su_kind: 'cloth', su_scale: 8, su_seed: 1337, su_strength: 0.45,
          su_levels: 0, su_shadow: '#241c2e', su_hl: '#fff2dc',
          su_hl_on: true, su_projection: 'uv',

          // --- Blockout ---
          bo_size_x: 8, bo_size_y: 8, bo_size_z: 8,
          bo_grid: 1, bo_at: 'origin', bo_name: 'block',
          ch_count: 6,
          ch_step_x: 0, ch_step_y: -4, ch_step_z: 0,
          ch_rot_x: 0, ch_rot_y: 0, ch_rot_z: 8,
          ch_scale: 0.92, ch_mode: 'cumulative', ch_falloff: 0.3,
          part_name: '',
          parts: [] as Array<{ name: string; cubes: unknown[] }>,

          // --- Motion ---
          pose_name: '',
          poses: [] as Array<{ name: string }>,
          mo_curve: 'ease_in_out',
          mo_scale: 1,
          mo_offset: 0,
          mo_interp: '',
          mo_scope: 'selection',
          mo_phase: 0.25,
        };
      },
      computed: {
        ramp(this: any): string[] {
          return generateRamp(this.sh_base, {
            steps: this.sh_steps, valueRange: this.sh_range,
            hueShift: this.sh_hue, satBoost: this.sh_sat,
          });
        },
        gradRamp(this: any): string[] {
          return gradientPreview(this.gradientOpts, 10);
        },
        gradientOpts(this: any): GradientOptions {
          return {
            axis: this.gr_axis, flip: !!this.gr_flip,
            darkColor: this.gr_dark, lightColor: this.gr_light,
            strength: Number(this.gr_strength), falloff: this.gr_falloff,
            bands: Math.round(Number(this.gr_bands)), target: this.gr_target,
          };
        },
        surfOpts(this: any): SurfacesOptions {
          return {
            kind: this.su_kind, scale: Number(this.su_scale), seed: Number(this.su_seed),
            strength: Number(this.su_strength), levels: Math.round(Number(this.su_levels)),
            shadowColor: this.su_shadow, highlightColor: this.su_hl,
            highlightOn: !!this.su_hl_on, projection: this.su_projection,
          };
        },
        surfRamp(this: any): string[] { return surfacePreview(this.surfOpts, 14); },
        surfaceLabels() { return SURFACE_LABELS; },
      },
      mounted(this: any) { this.refreshLibraries(); },
      methods: {
        refreshLibraries(this: any) {
          try { this.parts = listParts(); } catch { this.parts = []; }
          try { this.poses = listPoses(); } catch { this.poses = []; }
        },
        _run(this: any, label: string, fn: () => { ok: boolean; message: string }) {
          this.busy = true;
          this.status = `${label} en cours…`;
          this.statusKind = '';
          setTimeout(() => {
            try {
              const r = fn();
              this.status = r.message;
              this.statusKind = r.ok ? 'ok' : 'err';
              Blockbench.showQuickMessage(r.message, r.ok ? 2200 : 3500);
              if (!r.ok) console.warn('[reborn-creator-tools]', label, r.message);
            } catch (err) {
              console.error('[reborn-creator-tools]', label, err);
              this.status = 'Erreur : ' + String(err);
              this.statusKind = 'err';
              (Blockbench as any).showMessageBox?.({
                title: 'Reborn Creator Tools — erreur',
                message: `${label} a échoué :\n\n${String(err)}\n\n(Détails : Ctrl+Shift+I → Console)`,
              });
            } finally {
              this.busy = false;
              this.refreshLibraries();
            }
          }, 30);
        },

        // --- Handpainted ---
        runAO(this: any) {
          const opts: AOOptions = {
            color: this.ao_tone === 'custom' ? this.ao_color : (AO_TONES[this.ao_tone] ?? '#16202e'),
            intensity: Number(this.ao_intensity), radius: Number(this.ao_radius),
            samples: Math.round(Number(this.ao_samples)), target: this.ao_target,
            dither: !!this.ao_dither, levels: Math.max(2, Math.round(Number(this.ao_levels))),
          };
          this._run('Bake AO', () => bakeAO(opts));
        },
        runShade(this: any) {
          const opts: ShadeOptions = {
            base: this.sh_base, steps: Math.round(Number(this.sh_steps)),
            valueRange: Number(this.sh_range), hueShift: Number(this.sh_hue),
            satBoost: Number(this.sh_sat), action: this.sh_action,
          };
          this._run('Shade', () => applyShade(opts));
        },
        runLighting(this: any) {
          const opts: LightingOptions = {
            dir: LIGHT_PRESETS[this.li_dir] ?? LIGHT_PRESETS.top_front,
            shadowColor: this.li_shadow_color, shadowStrength: Number(this.li_shadow),
            highlightColor: this.li_hl_color, highlightStrength: Number(this.li_hl),
            shadowOn: !!this.li_shadow_on, highlightOn: !!this.li_hl_on,
          };
          this._run('Lighting', () => bakeLighting(opts));
        },
        runGradient(this: any) { this._run('Gradient', () => bakeGradient(this.gradientOpts)); },
        runEdges(this: any) {
          const opts: EdgesOptions = {
            highlightOn: !!this.ed_hl_on, highlightColor: this.ed_hl_color,
            highlightStrength: Number(this.ed_hl_strength),
            seamOn: !!this.ed_seam_on, seamColor: this.ed_seam_color,
            seamStrength: Number(this.ed_seam_strength),
            width: Number(this.ed_width), flatAngle: Number(this.ed_flat),
          };
          this._run('Edges', () => bakeEdges(opts));
        },
        runSurface(this: any) { this._run('Surfaces', () => bakeSurface(this.surfOpts)); },
        rerollSeed(this: any) { this.su_seed = Math.floor(Math.random() * 99999); },
        runAuto(this: any) {
          // Un clic : volume (lumière) + profondeur (AO) + liserés d'arêtes.
          // C'est la passe qui transforme un aplat en surface lisible ; on
          // ajuste ensuite outil par outil.
          this._run('Auto hand-paint', () => {
            const li = bakeLighting({
              dir: LIGHT_PRESETS.top_front,
              shadowColor: '#1a2636', shadowStrength: 0.55,
              highlightColor: '#fff0cf', highlightStrength: 0.45,
              shadowOn: true, highlightOn: true,
            });
            const ao = bakeAO({
              color: AO_TONES.cool, intensity: 1, radius: 4, samples: 24,
              target: 'layer', dither: false, levels: 4,
            });
            const ed = bakeEdges({
              highlightOn: true, highlightColor: '#fff6e0', highlightStrength: 0.4,
              seamOn: true, seamColor: '#1a1220', seamStrength: 0.35,
              width: 1.5, flatAngle: 12,
            });
            const ok = li.ok || ao.ok || ed.ok;
            return {
              ok,
              message: ok
                ? 'Auto hand-paint : lumière + AO + arêtes appliqués — regarde les calques de la texture.'
                : `${li.message} / ${ao.message} / ${ed.message}`,
            };
          });
        },

        // --- Blockout ---
        runBlock(this: any) {
          this._run('Nouveau bloc', () => addBlock({
            size: [Number(this.bo_size_x), Number(this.bo_size_y), Number(this.bo_size_z)],
            grid: Number(this.bo_grid), at: this.bo_at, name: this.bo_name,
          }));
        },
        runChain(this: any) {
          const opts: ChainOptions = {
            count: Math.round(Number(this.ch_count)),
            step: [Number(this.ch_step_x), Number(this.ch_step_y), Number(this.ch_step_z)],
            stepRotation: [Number(this.ch_rot_x), Number(this.ch_rot_y), Number(this.ch_rot_z)],
            stepScale: Number(this.ch_scale), mode: this.ch_mode,
            falloff: Number(this.ch_falloff),
          };
          this._run('Chaîne', () => chainSelection(opts));
        },
        runMirror(this: any, axis: 'x' | 'y' | 'z') {
          this._run('Miroir', () => mirrorSelection(axis));
        },
        runCenterPivots(this: any) { this._run('Pivots', () => centerPivots()); },
        runSavePart(this: any) {
          const n = this.part_name;
          this._run('Enregistrer la pièce', () => { const r = savePart(n); if (r.ok) this.part_name = ''; return r; });
        },
        runInsertPart(this: any, name: string) { this._run('Insérer la pièce', () => insertPart(name)); },
        runDeletePart(this: any, name: string) { this._run('Supprimer la pièce', () => deletePart(name)); },

        // --- Motion ---
        runCapturePose(this: any) {
          const n = this.pose_name;
          this._run('Capturer la pose', () => { const r = capturePose(n); if (r.ok) this.pose_name = ''; return r; });
        },
        runApplyPose(this: any, name: string) { this._run('Appliquer la pose', () => applyPose(name)); },
        runDeletePose(this: any, name: string) { this._run('Supprimer la pose', () => deletePose(name)); },
        runMirrorPose(this: any) { this._run('Symétriser la pose', () => mirrorPose()); },
        runShape(this: any) {
          const opts: ShapeOptions = {
            curve: this.mo_curve, timeScale: Number(this.mo_scale),
            timeOffset: Number(this.mo_offset), interpolation: this.mo_interp,
            scope: this.mo_scope,
          };
          this._run('Remodeler les clés', () => shapeKeys(opts));
        },
        runCloseLoop(this: any) { this._run('Fermer la boucle', () => closeLoop()); },
        runPhase(this: any) { this._run('Déphaser', () => shiftPhase(Number(this.mo_phase))); },

        showDiagnostics(this: any) {
          const report = diagnosticsReport();
          this.status = 'Diagnostics affichés.';
          this.statusKind = '';
          (Blockbench as any).showMessageBox?.({ title: 'Reborn Creator Tools — Diagnostics', message: report });
          console.log('[reborn-creator-tools] diagnostics\n' + report);
        },
      },
      template: `
        <div class="rct">
          <div class="rct-head">
            <i class="material-icons">construction</i>
            <div><div class="t">Reborn Creator Tools</div><div class="s">Modeling · Texturing · Animation</div></div>
          </div>

          <div class="rct-tabs">
            <button class="rct-tab" :class="{on: tab==='block'}" @click="tab='block'"><i class="material-icons">view_in_ar</i>Blockout</button>
            <button class="rct-tab" :class="{on: tab==='paint'}" @click="tab='paint'"><i class="material-icons">brush</i>Handpaint</button>
            <button class="rct-tab" :class="{on: tab==='motion'}" @click="tab='motion'"><i class="material-icons">animation</i>Motion</button>
            <button class="rct-tab" disabled title="Planifié"><i class="material-icons">photo_camera</i>Render</button>
          </div>

          <!-- ================= BLOCKOUT ================= -->
          <div v-if="tab==='block'">
            <div class="rct-lead">Bloquer, chaîner, poser, réutiliser. Tout est annulable au Ctrl+Z.</div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">add_box</i><span class="n">Block</span></div>
              <div class="rct-desc">Pose une primitive calée sur la grille, pivot déjà centré — plutôt que de créer un cube puis corriger six champs.</div>
              <div class="rct-row"><label>Taille</label><div class="num3"><input type="number" v-model.number="bo_size_x" min="1"><input type="number" v-model.number="bo_size_y" min="1"><input type="number" v-model.number="bo_size_z" min="1"></div></div>
              <div class="rct-row"><label>Grille</label><div class="grow"><input type="range" min="0" max="8" step="1" v-model.number="bo_grid"><span class="rct-badge">{{ bo_grid || '—' }}</span></div></div>
              <div class="rct-row"><label>Position</label><div class="grow"><select v-model="bo_at"><option value="origin">Origine du monde</option><option value="selection">Centre de la sélection</option></select></div></div>
              <div class="rct-row"><label>Nom</label><div class="grow"><input type="text" v-model="bo_name" placeholder="block"></div></div>
              <button class="rct-apply" :disabled="busy" @click="runBlock"><i class="material-icons">add_box</i>Créer le bloc</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">linear_scale</i><span class="n">Chain</span></div>
              <div class="rct-desc">Répète la sélection avec décalage, rotation et échelle progressifs. En mode <b>enroulé</b>, chaque copie repart du repère de la précédente : c'est ce qui fait une queue ou une vrille plutôt qu'une rangée droite.</div>
              <div class="rct-row"><label>Copies</label><div class="grow"><input type="range" min="1" max="40" step="1" v-model.number="ch_count"><span class="rct-badge">{{ ch_count }}</span></div></div>
              <div class="rct-row"><label>Décalage</label><div class="num3"><input type="number" v-model.number="ch_step_x" step="0.5"><input type="number" v-model.number="ch_step_y" step="0.5"><input type="number" v-model.number="ch_step_z" step="0.5"></div></div>
              <div class="rct-row"><label>Rotation°</label><div class="num3"><input type="number" v-model.number="ch_rot_x" step="1"><input type="number" v-model.number="ch_rot_y" step="1"><input type="number" v-model.number="ch_rot_z" step="1"></div></div>
              <div class="rct-row"><label>Échelle</label><div class="grow"><input type="range" min="0.5" max="1.5" step="0.01" v-model.number="ch_scale"><span class="rct-badge">{{ ch_scale.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Atténuation</label><div class="grow"><input type="range" min="0" max="1" step="0.05" v-model.number="ch_falloff"><span class="rct-badge">{{ ch_falloff.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Mode</label><div class="grow"><select v-model="ch_mode"><option value="cumulative">Enroulé (queue, vrille)</option><option value="additive">Droit (rangée)</option></select></div></div>
              <button class="rct-apply" :disabled="busy" @click="runChain"><i class="material-icons">linear_scale</i>Chaîner la sélection</button>
              <div class="rct-hint">Cubes uniquement en v1 — les meshes ne sont pas dupliqués.</div>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">flip</i><span class="n">Pose</span></div>
              <div class="rct-desc">Symétrise la sélection, ou recentre les pivots. Un pivot mal placé est la cause n°1 des rotations qui partent de travers.</div>
              <div class="rct-mini">
                <button :disabled="busy" @click="runMirror('x')">Miroir X</button>
                <button :disabled="busy" @click="runMirror('y')">Miroir Y</button>
                <button :disabled="busy" @click="runMirror('z')">Miroir Z</button>
              </div>
              <button class="rct-apply" :disabled="busy" @click="runCenterPivots"><i class="material-icons">center_focus_strong</i>Recentrer les pivots</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">bookmarks</i><span class="n">Reuse</span></div>
              <div class="rct-desc">Range une sélection en bibliothèque et ré-insère-la dans n'importe quel projet. Stockée sur ce poste ; les UV et les rotations sont conservées.</div>
              <div class="rct-row"><label>Nom</label><div class="grow"><input type="text" v-model="part_name" placeholder="ex. main_gauche"><button class="rct-item" style="padding:3px 9px" :disabled="busy" @click="runSavePart">Enregistrer</button></div></div>
              <div class="rct-list">
                <div v-if="!parts.length" class="empty">Aucune pièce enregistrée</div>
                <div v-for="p in parts" :key="p.name" class="rct-item">
                  <span class="nm">{{ p.name }}</span>
                  <span style="opacity:.45;font-size:10px">{{ p.cubes.length }}</span>
                  <button :disabled="busy" @click="runInsertPart(p.name)">Insérer</button>
                  <button class="del" :disabled="busy" @click="runDeletePart(p.name)">✕</button>
                </div>
              </div>
            </div>
          </div>

          <!-- ================= HANDPAINT ================= -->
          <div v-if="tab==='paint'">
            <button class="rct-auto" :disabled="busy" @click="runAuto"><i class="material-icons">auto_fix_high</i>Auto hand-paint (1 clic)</button>
            <div class="rct-auto-desc">Lumière + AO + arêtes d'un coup. La passe qui transforme un aplat en surface lisible ; on ajuste ensuite outil par outil.</div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">wb_sunny</i><span class="n">Lighting</span></div>
              <div class="rct-desc">Crée le volume : éclaire les faces tournées vers la lumière (chaud), ombre celles à l'opposé (froid).</div>
              <div class="rct-row"><label>Direction</label><div class="grow"><select v-model="li_dir"><option value="top_front">Haut-avant</option><option value="top">Dessus</option><option value="top_left">Haut-gauche</option><option value="top_right">Haut-droite</option><option value="front">Avant</option></select></div></div>
              <div class="rct-row"><label>Ombres</label><div class="grow"><input type="checkbox" v-model="li_shadow_on"><input type="color" v-model="li_shadow_color"><input type="range" min="0" max="1" step="0.05" v-model.number="li_shadow" :disabled="!li_shadow_on"><span class="rct-badge">{{ li_shadow.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Lumières</label><div class="grow"><input type="checkbox" v-model="li_hl_on"><input type="color" v-model="li_hl_color"><input type="range" min="0" max="1" step="0.05" v-model.number="li_hl" :disabled="!li_hl_on"><span class="rct-badge">{{ li_hl.toFixed(2) }}</span></div></div>
              <button class="rct-apply" :disabled="busy" @click="runLighting"><i class="material-icons">wb_sunny</i>Appliquer Lighting</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">blur_on</i><span class="n">AO</span></div>
              <div class="rct-desc">Ombre de contact dans les creux et sous les pièces qui se chevauchent. Calque multiply qui assombrit les recoins sans toucher au reste.</div>
              <div class="rct-row"><label>Teinte</label><div class="grow"><select v-model="ao_tone"><option value="cool">Froide</option><option value="neutral">Neutre</option><option value="warm">Chaude</option><option value="custom">Personnalisée</option></select></div></div>
              <div class="rct-row" v-if="ao_tone==='custom'"><label>Couleur</label><div class="grow"><input type="color" v-model="ao_color"></div></div>
              <div class="rct-row"><label>Intensité</label><div class="grow"><input type="range" min="0" max="2" step="0.05" v-model.number="ao_intensity"><span class="rct-badge">{{ ao_intensity.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Portée</label><div class="grow"><input type="range" min="0.5" max="16" step="0.5" v-model.number="ao_radius"><span class="rct-badge">{{ ao_radius }}</span></div></div>
              <div class="rct-row"><label>Rayons</label><div class="grow"><input type="range" min="4" max="128" step="1" v-model.number="ao_samples"><span class="rct-badge">{{ ao_samples }}</span></div></div>
              <div class="rct-row"><label>Cible</label><div class="grow"><select v-model="ao_target"><option value="layer">Nouveau calque</option><option value="texture">Dans la texture</option></select></div></div>
              <div class="rct-row"><label>Dithering</label><div class="grow"><input type="checkbox" v-model="ao_dither"><span style="opacity:.55;font-size:10.5px">rendu pixel-art</span></div></div>
              <div class="rct-row" v-if="ao_dither"><label>Niveaux</label><div class="grow"><input type="range" min="2" max="12" step="1" v-model.number="ao_levels"><span class="rct-badge">{{ ao_levels }}</span></div></div>
              <button class="rct-apply" :disabled="busy" @click="runAO"><i class="material-icons">blur_on</i>Bake AO</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">grade</i><span class="n">Edges</span></div>
              <div class="rct-desc">Liseré clair sur les arêtes saillantes, ligne sombre dans les rentrants et sur les coutures UV. Le trait qu'on passe son temps à tracer au pixel près.</div>
              <div class="rct-row"><label>Liseré</label><div class="grow"><input type="checkbox" v-model="ed_hl_on"><input type="color" v-model="ed_hl_color"><input type="range" min="0" max="1" step="0.05" v-model.number="ed_hl_strength" :disabled="!ed_hl_on"><span class="rct-badge">{{ ed_hl_strength.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Couture</label><div class="grow"><input type="checkbox" v-model="ed_seam_on"><input type="color" v-model="ed_seam_color"><input type="range" min="0" max="1" step="0.05" v-model.number="ed_seam_strength" :disabled="!ed_seam_on"><span class="rct-badge">{{ ed_seam_strength.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Épaisseur</label><div class="grow"><input type="range" min="0.5" max="6" step="0.5" v-model.number="ed_width"><span class="rct-badge">{{ ed_width }}</span></div></div>
              <div class="rct-row"><label>Planéité°</label><div class="grow"><input type="range" min="0" max="45" step="1" v-model.number="ed_flat"><span class="rct-badge">{{ ed_flat }}</span></div></div>
              <button class="rct-apply" :disabled="busy" @click="runEdges"><i class="material-icons">grade</i>Appliquer Edges</button>
              <div class="rct-hint">Sous le seuil de planéité, deux faces sont vues comme coplanaires et l'arête est ignorée — sinon on souligne de faux bords.</div>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">gradient</i><span class="n">Gradient</span></div>
              <div class="rct-desc">Dégradé de forme guidé par un axe : la montée de valeur du bas vers le haut d'une pièce. Sur l'axe « sélection », suit la plus grande dimension de ce qui est sélectionné (la longueur d'un membre).</div>
              <div class="rct-ramp"><div v-for="(c,i) in gradRamp" :key="i" :style="{background:c}"></div></div>
              <div class="rct-row"><label>Axe</label><div class="grow"><select v-model="gr_axis"><option value="y">Y (vertical)</option><option value="x">X</option><option value="z">Z</option><option value="selection">Sélection</option></select><input type="checkbox" v-model="gr_flip" title="Inverser"><span style="opacity:.55;font-size:10px">inv.</span></div></div>
              <div class="rct-row"><label>Sombre</label><div class="grow"><input type="color" v-model="gr_dark"><label style="flex:0 0 auto;opacity:.7">Clair</label><input type="color" v-model="gr_light"></div></div>
              <div class="rct-row"><label>Force</label><div class="grow"><input type="range" min="0" max="1" step="0.05" v-model.number="gr_strength"><span class="rct-badge">{{ gr_strength.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Courbe</label><div class="grow"><select v-model="gr_falloff"><option value="smooth">Douce</option><option value="linear">Linéaire</option><option value="ease_in">Accélérée</option><option value="ease_out">Freinée</option></select></div></div>
              <div class="rct-row"><label>Bandes</label><div class="grow"><input type="range" min="0" max="8" step="1" v-model.number="gr_bands"><span class="rct-badge">{{ gr_bands || 'continu' }}</span></div></div>
              <div class="rct-row"><label>Cible</label><div class="grow"><select v-model="gr_target"><option value="layer">Nouveau calque</option><option value="texture">Dans la texture</option></select></div></div>
              <button class="rct-apply" :disabled="busy" @click="runGradient"><i class="material-icons">gradient</i>Appliquer Gradient</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">texture</i><span class="n">Surfaces</span></div>
              <div class="rct-desc">Matières récurrentes posées par-dessus la base, sans la repeindre. Même graine = même grain, toujours : on peut régénérer sans perdre le rendu.</div>
              <div class="rct-ramp"><div v-for="(c,i) in surfRamp" :key="i" :style="{background:c}"></div></div>
              <div class="rct-row"><label>Matière</label><div class="grow"><select v-model="su_kind"><option value="cloth">Tissu</option><option value="fur">Fourrure</option><option value="wood">Bois</option><option value="stone">Pierre</option><option value="noise">Grain</option></select></div></div>
              <div class="rct-row"><label>Échelle</label><div class="grow"><input type="range" min="1" max="40" step="1" v-model.number="su_scale"><span class="rct-badge">{{ su_scale }}</span></div></div>
              <div class="rct-row"><label>Graine</label><div class="grow"><input type="number" v-model.number="su_seed"><button class="rct-item" style="padding:3px 9px" @click="rerollSeed">↻</button></div></div>
              <div class="rct-row"><label>Force</label><div class="grow"><input type="range" min="0" max="1" step="0.05" v-model.number="su_strength"><span class="rct-badge">{{ su_strength.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Paliers</label><div class="grow"><input type="range" min="0" max="8" step="1" v-model.number="su_levels"><span class="rct-badge">{{ su_levels || 'continu' }}</span></div></div>
              <div class="rct-row"><label>Creux</label><div class="grow"><input type="color" v-model="su_shadow"><input type="checkbox" v-model="su_hl_on" title="Crêtes"><input type="color" v-model="su_hl" :disabled="!su_hl_on"></div></div>
              <div class="rct-row"><label>Projection</label><div class="grow"><select v-model="su_projection"><option value="uv">UV (rapide)</option><option value="world">Monde (traverse les coutures)</option></select></div></div>
              <button class="rct-apply" :disabled="busy" @click="runSurface"><i class="material-icons">texture</i>Appliquer Surface</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">palette</i><span class="n">Shade</span></div>
              <div class="rct-desc">Génère la gamme d'ombres/lumières d'une couleur (ombres froides, lumières chaudes) et remplit la palette. « Ombrer la texture » n'a d'effet que si elle a déjà du relief — sur un aplat, passer par Lighting d'abord.</div>
              <div class="rct-row"><label>Base</label><div class="grow"><input type="color" v-model="sh_base"><span style="opacity:.55;font-size:10.5px">aperçu ↓</span></div></div>
              <div class="rct-ramp"><div v-for="(c,i) in ramp" :key="i" :style="{background:c}"></div></div>
              <div class="rct-row"><label>Tons</label><div class="grow"><input type="range" min="2" max="10" step="1" v-model.number="sh_steps"><span class="rct-badge">{{ sh_steps }}</span></div></div>
              <div class="rct-row"><label>Amplitude</label><div class="grow"><input type="range" min="0.1" max="1" step="0.05" v-model.number="sh_range"><span class="rct-badge">{{ sh_range.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Hue-shift</label><div class="grow"><input type="range" min="0" max="40" step="1" v-model.number="sh_hue"><span class="rct-badge">{{ sh_hue }}</span></div></div>
              <div class="rct-row"><label>Satur.</label><div class="grow"><input type="range" min="0" max="0.5" step="0.01" v-model.number="sh_sat"><span class="rct-badge">{{ sh_sat.toFixed(2) }}</span></div></div>
              <div class="rct-row"><label>Action</label><div class="grow"><select v-model="sh_action"><option value="both">Palette + ombrage</option><option value="palette">Palette seule</option><option value="remap">Ombrer la texture</option></select></div></div>
              <button class="rct-apply" :disabled="busy" @click="runShade"><i class="material-icons">palette</i>Appliquer Shade</button>
            </div>
          </div>

          <!-- ================= MOTION ================= -->
          <div v-if="tab==='motion'">
            <div class="rct-lead">Poser, remodeler, garder les clés. Aucun outil ici ne resample l'animation : on déplace tes clés, on ne les remplace pas.</div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">accessibility_new</i><span class="n">Pose</span></div>
              <div class="rct-desc">Capture la pose de l'instant courant et repose-la ailleurs — sur une autre image, une autre animation, un autre projet.</div>
              <div class="rct-row"><label>Nom</label><div class="grow"><input type="text" v-model="pose_name" placeholder="ex. garde_haute"><button class="rct-item" style="padding:3px 9px" :disabled="busy" @click="runCapturePose">Capturer</button></div></div>
              <div class="rct-list">
                <div v-if="!poses.length" class="empty">Aucune pose enregistrée</div>
                <div v-for="p in poses" :key="p.name" class="rct-item">
                  <span class="nm">{{ p.name }}</span>
                  <button :disabled="busy" @click="runApplyPose(p.name)">Poser</button>
                  <button class="del" :disabled="busy" @click="runDeletePose(p.name)">✕</button>
                </div>
              </div>
              <button class="rct-apply" :disabled="busy" @click="runMirrorPose"><i class="material-icons">flip</i>Symétriser la pose (L↔R)</button>
              <div class="rct-hint">La symétrie repère les paires par le nom : <code>bras_left</code> ↔ <code>bras_right</code> (ou <code>_l</code> / <code>_r</code>).</div>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">timeline</i><span class="n">Shape</span></div>
              <div class="rct-desc">Redistribue les instants de tes clés selon une courbe, étire ou décale le tout. Le nombre de clés et leurs valeurs ne bougent pas — seule leur répartition change.</div>
              <div class="rct-row"><label>Portée</label><div class="grow"><select v-model="mo_scope"><option value="selection">Clés sélectionnées</option><option value="all">Toute l'animation</option></select></div></div>
              <div class="rct-row"><label>Courbe</label><div class="grow"><select v-model="mo_curve"><option value="linear">Aucune</option><option value="ease_in">Départ doux</option><option value="ease_out">Arrivée douce</option><option value="ease_in_out">Doux aux deux bouts</option><option value="anticipate">Anticipation</option><option value="overshoot">Dépassement</option><option value="bounce_out">Rebond</option></select></div></div>
              <div class="rct-row"><label>Étirement</label><div class="grow"><input type="range" min="0.25" max="3" step="0.05" v-model.number="mo_scale"><span class="rct-badge">{{ mo_scale.toFixed(2) }}×</span></div></div>
              <div class="rct-row"><label>Décalage</label><div class="grow"><input type="range" min="-2" max="2" step="0.05" v-model.number="mo_offset"><span class="rct-badge">{{ mo_offset.toFixed(2) }}s</span></div></div>
              <div class="rct-row"><label>Interpol.</label><div class="grow"><select v-model="mo_interp"><option value="">Ne pas toucher</option><option value="linear">Linéaire</option><option value="catmullrom">Lissée</option><option value="step">Palier (step)</option></select></div></div>
              <button class="rct-apply" :disabled="busy" @click="runShape"><i class="material-icons">timeline</i>Remodeler les clés</button>
            </div>

            <div class="rct-card">
              <div class="rct-card-h"><i class="material-icons">loop</i><span class="n">Keep</span></div>
              <div class="rct-desc">Ferme la boucle en recopiant la pose de t=0 à la fin — sans ça, une animation en boucle claque à chaque tour. Le déphasage désynchronise un membre sans le réanimer.</div>
              <button class="rct-apply" :disabled="busy" @click="runCloseLoop"><i class="material-icons">loop</i>Fermer la boucle</button>
              <div class="rct-row" style="margin-top:10px"><label>Déphasage</label><div class="grow"><input type="range" min="-2" max="2" step="0.05" v-model.number="mo_phase"><span class="rct-badge">{{ mo_phase.toFixed(2) }}s</span></div></div>
              <button class="rct-apply" :disabled="busy" @click="runPhase"><i class="material-icons">sync_alt</i>Déphaser la sélection</button>
            </div>
          </div>

          <div class="rct-status" :class="statusKind">{{ status }}</div>
          <button class="rct-diag" @click="showDiagnostics"><i class="material-icons">bug_report</i>Diagnostics</button>
        </div>
      `,
    },
  } as any);

  return {
    delete() {
      try { (panel as any).delete(); } catch (e) { console.warn('[reborn-creator-tools] panel cleanup', e); }
      styleEl.remove();
    },
  };
}
