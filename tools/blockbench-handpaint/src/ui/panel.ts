/**
 * Panneau latéral Reborn Handpainted (style RuneFist) — l'UI principale.
 * Contrôles live pour chaque outil + aperçu de rampe + Apply + statut + Diagnostics.
 *
 * Deux pièges Blockbench gérés ici :
 *  - Les inputs couleur sont des <input type="color"> natifs → valeur hex directe
 *    (le champ Dialog `color` de Blockbench renvoie un objet tinycolor).
 *  - Vue 2 IGNORE les balises <style> dans un template → on injecte le CSS dans
 *    document.head (sinon le panneau s'affiche sans aucun style).
 */
import { bakeAO, AO_TONES, type AOOptions } from '../tools/ao.ts';
import { applyShade, type ShadeOptions } from '../tools/shade.ts';
import { generateRamp } from '../core/color.ts';

const STYLE_ID = 'reborn-hp-styles';

const CSS = `
.rhp { padding: 12px; font-size: 12px; color: var(--color-text); overflow-y: auto; height: 100%; box-sizing: border-box; }
.rhp * { box-sizing: border-box; }
.rhp-head { display: flex; align-items: center; gap: 9px; margin-bottom: 12px; }
.rhp-head .material-icons { font-size: 22px; color: var(--color-accent); }
.rhp-head .t { font-size: 13px; font-weight: 700; letter-spacing: .3px; }
.rhp-head .s { font-size: 10px; opacity: .55; margin-top: 1px; }
.rhp-card { background: var(--color-ui); border: 1px solid var(--color-border); border-radius: 10px; padding: 11px 12px; margin-bottom: 12px; }
.rhp-card-h { display: flex; align-items: center; gap: 7px; margin-bottom: 3px; }
.rhp-card-h .material-icons { font-size: 17px; color: var(--color-accent); }
.rhp-card-h .n { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; }
.rhp-desc { font-size: 10.5px; line-height: 1.45; opacity: .6; margin-bottom: 10px; }
.rhp-row { display: flex; align-items: center; gap: 10px; margin: 7px 0; min-height: 20px; }
.rhp-row > label { flex: 0 0 80px; opacity: .8; font-size: 11px; }
.rhp-row .grow { flex: 1; display: flex; align-items: center; gap: 8px; }
.rhp-badge { flex: 0 0 auto; min-width: 30px; text-align: center; font-variant-numeric: tabular-nums; font-size: 10.5px; padding: 2px 6px; border-radius: 5px; background: var(--color-back); border: 1px solid var(--color-border); opacity: .9; }

.rhp input[type=range] { -webkit-appearance: none; appearance: none; flex: 1; height: 4px; border-radius: 3px; background: var(--color-border); outline: none; }
.rhp input[type=range]::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 14px; height: 14px; border-radius: 50%; background: var(--color-accent); cursor: pointer; border: 2px solid var(--color-ui); box-shadow: 0 0 0 1px var(--color-accent); transition: transform .08s; }
.rhp input[type=range]::-webkit-slider-thumb:hover { transform: scale(1.15); }

.rhp select { flex: 1; background: var(--color-button); color: var(--color-text); border: 1px solid var(--color-border); border-radius: 6px; padding: 3px 6px; font-size: 11px; cursor: pointer; }
.rhp input[type=color] { width: 40px; height: 24px; padding: 0; border: 1px solid var(--color-border); border-radius: 6px; background: var(--color-button); cursor: pointer; }
.rhp input[type=checkbox] { width: 15px; height: 15px; accent-color: var(--color-accent); cursor: pointer; }

.rhp-ramp { display: flex; height: 30px; border-radius: 7px; overflow: hidden; border: 1px solid var(--color-border); margin: 4px 0 8px; }
.rhp-ramp > div { flex: 1; }

.rhp-apply { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; margin-top: 8px; padding: 8px; background: var(--color-accent); color: var(--color-accent_text, #fff); border: none; border-radius: 7px; cursor: pointer; font-weight: 700; font-size: 12px; transition: filter .1s; }
.rhp-apply:hover { filter: brightness(1.12); }
.rhp-apply:disabled { opacity: .5; cursor: default; filter: none; }
.rhp-apply .material-icons { font-size: 16px; }

.rhp-status { margin-top: 2px; padding: 8px 10px; border-radius: 7px; background: var(--color-back); border: 1px solid var(--color-border); font-size: 11px; line-height: 1.4; word-break: break-word; }
.rhp-status.ok { border-color: var(--color-accent); }
.rhp-status.err { border-color: #e05a5a; color: #ff9a9a; }
.rhp-diag { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; margin-top: 8px; padding: 6px; background: transparent; color: var(--color-text); border: 1px solid var(--color-border); border-radius: 7px; cursor: pointer; font-size: 11px; opacity: .8; }
.rhp-diag:hover { opacity: 1; background: var(--color-button); }
.rhp-diag .material-icons { font-size: 15px; }
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

/** Compte la géométrie / textures visibles, pour Diagnostics. */
function diagnosticsReport(): string {
  const C = (globalThis as any).Cube;
  const M = (globalThis as any).Mesh;
  const T = (globalThis as any).Texture;
  const CP = (globalThis as any).ColorPanel;
  const textures: any[] = T?.all ?? [];
  const sel = T?.selected;
  return [
    `Cubes : ${C?.all?.length ?? 0}`,
    `Meshes : ${M?.all?.length ?? 0}`,
    `Textures : ${textures.length}`,
    sel ? `Sélectionnée : ${sel.name} (${sel.width}×${sel.height})` : 'Sélectionnée : AUCUNE — sélectionne une texture d\'abord',
    `Palette : ${CP && Array.isArray(CP.palette) ? CP.palette.length + ' couleurs' : 'indisponible'}`,
    `THREE : ${(globalThis as any).THREE ? 'ok' : 'absent'}`,
  ].join('\n');
}

export function createHandpaintedPanel(): { delete: () => void } {
  const styleEl = injectStyles();

  const panel = new Panel('reborn_handpainted_panel', {
    name: 'Handpainted',
    id: 'reborn_handpainted_panel',
    icon: 'brush',
    growable: true,
    resizable: true,
    default_side: 'right',
    default_position: {
      slot: 'right_bar',
      float_position: [0, 0],
      float_size: [330, 600],
      height: 600,
      folded: false,
    },
    component: {
      data() {
        return {
          status: 'Sélectionne une texture, puis lance un outil.',
          statusKind: '',
          busy: false,
          ao_tone: 'cool',
          ao_color: '#16202e',
          ao_intensity: 1,
          ao_radius: 4,
          ao_samples: 24,
          ao_target: 'layer',
          ao_dither: false,
          ao_levels: 4,
          sh_base: '#7a5a3c',
          sh_steps: 5,
          sh_range: 0.6,
          sh_hue: 12,
          sh_sat: 0.12,
          sh_action: 'both',
        };
      },
      computed: {
        ramp(this: any): string[] {
          return generateRamp(this.sh_base, {
            steps: this.sh_steps,
            valueRange: this.sh_range,
            hueShift: this.sh_hue,
            satBoost: this.sh_sat,
          });
        },
      },
      methods: {
        _run(this: any, label: string, fn: () => { ok: boolean; message: string }) {
          this.busy = true;
          this.status = `${label} en cours…`;
          this.statusKind = '';
          setTimeout(() => {
            try {
              const r = fn();
              this.status = r.message;
              this.statusKind = r.ok ? 'ok' : 'err';
              Blockbench.showQuickMessage(r.message, r.ok ? 2000 : 3500);
              if (!r.ok) console.warn('[reborn-handpainted]', label, r.message);
            } catch (err) {
              console.error('[reborn-handpainted]', label, err);
              this.status = 'Erreur : ' + String(err);
              this.statusKind = 'err';
              (Blockbench as any).showMessageBox?.({
                title: 'Reborn Handpainted — erreur',
                message: `${label} a échoué :\n\n${String(err)}\n\n(Détails : Ctrl+Shift+I → Console)`,
              });
            } finally {
              this.busy = false;
            }
          }, 30);
        },
        runAO(this: any) {
          const opts: AOOptions = {
            color: this.ao_tone === 'custom' ? this.ao_color : (AO_TONES[this.ao_tone] ?? '#16202e'),
            intensity: Number(this.ao_intensity),
            radius: Number(this.ao_radius),
            samples: Math.round(Number(this.ao_samples)),
            target: this.ao_target,
            dither: !!this.ao_dither,
            levels: Math.max(2, Math.round(Number(this.ao_levels))),
          };
          this._run('Bake AO', () => bakeAO(opts));
        },
        runShade(this: any) {
          const opts: ShadeOptions = {
            base: this.sh_base,
            steps: Math.round(Number(this.sh_steps)),
            valueRange: Number(this.sh_range),
            hueShift: Number(this.sh_hue),
            satBoost: Number(this.sh_sat),
            action: this.sh_action,
          };
          this._run('Shade', () => applyShade(opts));
        },
        showDiagnostics(this: any) {
          const report = diagnosticsReport();
          this.status = 'Diagnostics affichés.';
          this.statusKind = '';
          (Blockbench as any).showMessageBox?.({ title: 'Reborn Handpainted — Diagnostics', message: report });
          console.log('[reborn-handpainted] diagnostics\n' + report);
        },
      },
      template: `
        <div class="rhp">
          <div class="rhp-head">
            <i class="material-icons">brush</i>
            <div><div class="t">Reborn Handpainted</div><div class="s">Texturisation hand-painted assistée</div></div>
          </div>

          <div class="rhp-card">
            <div class="rhp-card-h"><i class="material-icons">blur_on</i><span class="n">AO</span></div>
            <div class="rhp-desc">Ajoute l'ombre de contact dans les creux et sous les pièces qui se chevauchent. Crée un calque « AO » (multiply) qui assombrit les recoins sans toucher au reste.</div>
            <div class="rhp-row"><label>Teinte</label><div class="grow"><select v-model="ao_tone"><option value="cool">Froide</option><option value="neutral">Neutre</option><option value="warm">Chaude</option><option value="custom">Personnalisée</option></select></div></div>
            <div class="rhp-row" v-if="ao_tone==='custom'"><label>Couleur</label><div class="grow"><input type="color" v-model="ao_color"></div></div>
            <div class="rhp-row"><label>Intensité</label><div class="grow"><input type="range" min="0" max="2" step="0.05" v-model.number="ao_intensity"><span class="rhp-badge">{{ ao_intensity.toFixed(2) }}</span></div></div>
            <div class="rhp-row"><label>Portée</label><div class="grow"><input type="range" min="0.5" max="16" step="0.5" v-model.number="ao_radius"><span class="rhp-badge">{{ ao_radius }}</span></div></div>
            <div class="rhp-row"><label>Rayons</label><div class="grow"><input type="range" min="4" max="128" step="1" v-model.number="ao_samples"><span class="rhp-badge">{{ ao_samples }}</span></div></div>
            <div class="rhp-row"><label>Cible</label><div class="grow"><select v-model="ao_target"><option value="layer">Nouveau calque</option><option value="texture">Dans la texture</option></select></div></div>
            <div class="rhp-row"><label>Dithering</label><div class="grow"><input type="checkbox" v-model="ao_dither"><span style="opacity:.55;font-size:10.5px">rendu pixel-art</span></div></div>
            <div class="rhp-row" v-if="ao_dither"><label>Niveaux</label><div class="grow"><input type="range" min="2" max="12" step="1" v-model.number="ao_levels"><span class="rhp-badge">{{ ao_levels }}</span></div></div>
            <button class="rhp-apply" :disabled="busy" @click="runAO"><i class="material-icons">blur_on</i>Bake AO</button>
          </div>

          <div class="rhp-card">
            <div class="rhp-card-h"><i class="material-icons">gradient</i><span class="n">Shade</span></div>
            <div class="rhp-desc">À partir d'une couleur, génère une gamme d'ombres/lumières cohérente (ombres froides, lumières chaudes). Remplit la palette pour peindre + peut coloriser ta texture en bandes.</div>
            <div class="rhp-row"><label>Base</label><div class="grow"><input type="color" v-model="sh_base"><span style="opacity:.55;font-size:10.5px">aperçu ↓</span></div></div>
            <div class="rhp-ramp"><div v-for="(c,i) in ramp" :key="i" :style="{background:c}"></div></div>
            <div class="rhp-row"><label>Tons</label><div class="grow"><input type="range" min="2" max="10" step="1" v-model.number="sh_steps"><span class="rhp-badge">{{ sh_steps }}</span></div></div>
            <div class="rhp-row"><label>Amplitude</label><div class="grow"><input type="range" min="0.1" max="1" step="0.05" v-model.number="sh_range"><span class="rhp-badge">{{ sh_range.toFixed(2) }}</span></div></div>
            <div class="rhp-row"><label>Hue-shift</label><div class="grow"><input type="range" min="0" max="40" step="1" v-model.number="sh_hue"><span class="rhp-badge">{{ sh_hue }}</span></div></div>
            <div class="rhp-row"><label>Satur.</label><div class="grow"><input type="range" min="0" max="0.5" step="0.01" v-model.number="sh_sat"><span class="rhp-badge">{{ sh_sat.toFixed(2) }}</span></div></div>
            <div class="rhp-row"><label>Action</label><div class="grow"><select v-model="sh_action"><option value="both">Palette + ombrage</option><option value="palette">Palette seule</option><option value="remap">Ombrer la texture</option></select></div></div>
            <button class="rhp-apply" :disabled="busy" @click="runShade"><i class="material-icons">gradient</i>Appliquer Shade</button>
          </div>

          <div class="rhp-status" :class="statusKind">{{ status }}</div>
          <button class="rhp-diag" @click="showDiagnostics"><i class="material-icons">bug_report</i>Diagnostics</button>
        </div>
      `,
    },
  } as any);

  return {
    delete() {
      try { (panel as any).delete(); } catch (e) { console.warn('[reborn-handpainted] panel cleanup', e); }
      styleEl.remove();
    },
  };
}
