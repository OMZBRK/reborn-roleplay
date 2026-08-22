/**
 * Panneau latéral Reborn Handpainted (style RuneFist) — l'UI principale.
 * Contrôles live pour chaque outil + aperçu de rampe + bouton Apply + statut,
 * plus un bouton Diagnostics qui montre ce que le plugin voit.
 *
 * Les inputs couleur sont des <input type="color"> natifs → valeur hex directe
 * (on évite le piège du champ Dialog `color` qui renvoie un objet tinycolor).
 */
import { bakeAO, AO_TONES, type AOOptions } from '../tools/ao.ts';
import { applyShade, type ShadeOptions } from '../tools/shade.ts';
import { generateRamp } from '../core/color.ts';

/** Compte la géométrie / textures visibles, pour Diagnostics. */
function diagnosticsReport(): string {
  const C = (globalThis as any).Cube;
  const M = (globalThis as any).Mesh;
  const T = (globalThis as any).Texture;
  const CP = (globalThis as any).ColorPanel;
  const cubes = C?.all?.length ?? 0;
  const meshes = M?.all?.length ?? 0;
  const textures: any[] = T?.all ?? [];
  const sel = T?.selected;
  const lines = [
    `Cubes : ${cubes}`,
    `Meshes : ${meshes}`,
    `Textures : ${textures.length}`,
    sel ? `Sélectionnée : ${sel.name} (${sel.width}×${sel.height})` : 'Sélectionnée : aucune',
    `Palette (ColorPanel) : ${CP && Array.isArray(CP.palette) ? CP.palette.length + ' couleurs' : 'indisponible'}`,
    `THREE : ${(globalThis as any).THREE ? 'ok' : 'absent'}`,
  ];
  return lines.join('\n');
}

export function createHandpaintedPanel(): { delete: () => void } {
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
      float_size: [320, 560],
      height: 560,
      folded: false,
    },
    component: {
      data() {
        return {
          status: 'Prêt.',
          busy: false,
          // AO
          ao_tone: 'cool',
          ao_color: '#16202e',
          ao_intensity: 1,
          ao_radius: 4,
          ao_samples: 24,
          ao_target: 'layer',
          ao_dither: false,
          ao_levels: 4,
          // Shade
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
          this.status = `${label}…`;
          // Laisse l'UI peindre le "en cours" avant le calcul bloquant.
          setTimeout(() => {
            try {
              const r = fn();
              this.status = r.message;
              Blockbench.showQuickMessage(r.message, r.ok ? 2000 : 3500);
              if (!r.ok) console.warn('[reborn-handpainted]', label, r.message);
            } catch (err) {
              console.error('[reborn-handpainted]', label, err);
              this.status = 'Erreur : ' + String(err);
              (Blockbench as any).showMessageBox?.({
                title: 'Reborn Handpainted — erreur',
                message: `${label} a échoué :\n\n${String(err)}\n\n(Détails dans la console : Ctrl+Shift+I)`,
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
          (Blockbench as any).showMessageBox?.({
            title: 'Reborn Handpainted — Diagnostics',
            message: report,
          });
          console.log('[reborn-handpainted] diagnostics\n' + report);
        },
      },
      template: `
        <div class="reborn-hp" style="padding:10px; font-size:12px; color:var(--color-text); overflow-y:auto;">
          <style>
            .reborn-hp h3 { margin:2px 0 6px; font-size:12px; text-transform:uppercase; letter-spacing:.5px; opacity:.8; }
            .reborn-hp .sec { border:1px solid var(--color-border); border-radius:6px; padding:8px; margin-bottom:10px; background:var(--color-back); }
            .reborn-hp .row { display:flex; align-items:center; justify-content:space-between; gap:8px; margin:4px 0; }
            .reborn-hp .row label { flex:0 0 auto; opacity:.85; }
            .reborn-hp input[type=range] { flex:1; }
            .reborn-hp .val { flex:0 0 34px; text-align:right; font-variant-numeric:tabular-nums; opacity:.9; }
            .reborn-hp select, .reborn-hp input[type=color] { background:var(--color-button); color:var(--color-text); border:1px solid var(--color-border); border-radius:4px; }
            .reborn-hp .apply { width:100%; margin-top:6px; padding:6px; background:var(--color-accent); color:var(--color-light,#fff); border:none; border-radius:5px; cursor:pointer; font-weight:600; }
            .reborn-hp .apply:disabled { opacity:.5; cursor:default; }
            .reborn-hp .ramp { display:flex; height:22px; border-radius:4px; overflow:hidden; border:1px solid var(--color-border); margin:6px 0; }
            .reborn-hp .ramp > div { flex:1; }
            .reborn-hp .status { margin-top:4px; padding:6px; border-radius:4px; background:var(--color-back); border:1px solid var(--color-border); opacity:.85; min-height:16px; word-break:break-word; }
            .reborn-hp .diag { width:100%; margin-top:4px; padding:5px; background:var(--color-button); color:var(--color-text); border:1px solid var(--color-border); border-radius:5px; cursor:pointer; }
          </style>

          <div class="sec">
            <h3>AO — occlusion ambiante</h3>
            <div class="row"><label>Teinte</label>
              <select v-model="ao_tone" style="flex:1;">
                <option value="cool">Froide</option>
                <option value="neutral">Neutre</option>
                <option value="warm">Chaude</option>
                <option value="custom">Personnalisée</option>
              </select>
            </div>
            <div class="row" v-if="ao_tone==='custom'"><label>Couleur</label>
              <input type="color" v-model="ao_color">
            </div>
            <div class="row"><label>Intensité</label><input type="range" min="0" max="2" step="0.05" v-model.number="ao_intensity"><span class="val">{{ ao_intensity.toFixed(2) }}</span></div>
            <div class="row"><label>Portée</label><input type="range" min="0.5" max="16" step="0.5" v-model.number="ao_radius"><span class="val">{{ ao_radius }}</span></div>
            <div class="row"><label>Rayons</label><input type="range" min="4" max="128" step="1" v-model.number="ao_samples"><span class="val">{{ ao_samples }}</span></div>
            <div class="row"><label>Cible</label>
              <select v-model="ao_target" style="flex:1;">
                <option value="layer">Nouveau calque</option>
                <option value="texture">Dans la texture</option>
              </select>
            </div>
            <div class="row"><label>Dithering</label><input type="checkbox" v-model="ao_dither"></div>
            <div class="row" v-if="ao_dither"><label>Niveaux</label><input type="range" min="2" max="12" step="1" v-model.number="ao_levels"><span class="val">{{ ao_levels }}</span></div>
            <button class="apply" :disabled="busy" @click="runAO">Bake AO</button>
          </div>

          <div class="sec">
            <h3>Shade — rampe hand-painted</h3>
            <div class="row"><label>Base</label><input type="color" v-model="sh_base"></div>
            <div class="ramp"><div v-for="(c,i) in ramp" :key="i" :style="{background:c}"></div></div>
            <div class="row"><label>Tons</label><input type="range" min="2" max="10" step="1" v-model.number="sh_steps"><span class="val">{{ sh_steps }}</span></div>
            <div class="row"><label>Amplitude</label><input type="range" min="0.1" max="1" step="0.05" v-model.number="sh_range"><span class="val">{{ sh_range.toFixed(2) }}</span></div>
            <div class="row"><label>Hue-shift</label><input type="range" min="0" max="40" step="1" v-model.number="sh_hue"><span class="val">{{ sh_hue }}</span></div>
            <div class="row"><label>Satur. ombres</label><input type="range" min="0" max="0.5" step="0.01" v-model.number="sh_sat"><span class="val">{{ sh_sat.toFixed(2) }}</span></div>
            <div class="row"><label>Action</label>
              <select v-model="sh_action" style="flex:1;">
                <option value="both">Palette + ombrage</option>
                <option value="palette">Palette seule</option>
                <option value="remap">Ombrer la texture</option>
              </select>
            </div>
            <button class="apply" :disabled="busy" @click="runShade">Appliquer Shade</button>
          </div>

          <div class="status">{{ status }}</div>
          <button class="diag" @click="showDiagnostics">Diagnostics</button>
        </div>
      `,
    },
  } as any);

  return panel as any;
}
