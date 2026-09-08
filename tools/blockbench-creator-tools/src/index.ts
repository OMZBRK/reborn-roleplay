/**
 * Reborn Creator Tools — point d'entree du plugin Blockbench.
 *
 * Quatre workspaces dans un seul panneau lateral :
 *   Blockout Canvas (modeling)   — block, chain, pose, reuse
 *   Handpainted Workflow (tex.)  — AO, lighting, edges, gradient, surfaces, shade
 *   Motion Lab (animation)       — pose, shape, keep the keys
 *   Render Studio (scenes)       — planifie
 *
 * Ce fichier ne fait que l'enregistrement et le wiring : l'UI vit dans
 * src/ui/panel.ts, la logique dans src/core (pur, testable) et src/tools
 * (acces a l'API Blockbench).
 */
import { createHandpaintedPanel } from './ui/panel.ts';

const PLUGIN_ID = 'reborn_creator_tools';

// Éléments UI créés au chargement, à nettoyer au déchargement.
let disposables: Array<{ delete: () => void }> = [];

BBPlugin.register(PLUGIN_ID, {
  title: 'Reborn Creator Tools',
  author: 'Reborn Roleplay',
  icon: 'construction',
  description:
    'Suite creator pour Blockbench : Blockout Canvas (modeling), Handpainted Workflow ' +
    '(AO, lighting, edges, gradient, surfaces, shade), Motion Lab (animation). ' +
    'Les passes generees restent des TextureLayers editables.',
  // Blockbench plafonne a 3 tags (le type du paquet blockbench-types le dit).
  tags: ['Texture', 'Animation', 'Minecraft'],
  version: '0.4.0',
  min_version: '5.1.0',
  variant: 'desktop',

  onload() {
    try {
      // Panneau latéral (UI principale, style RuneFist).
      const panel = createHandpaintedPanel();
      disposables.push(panel);

      // Action menu pour (ré)afficher le panneau s'il a été fermé.
      const openAction = new Action(`${PLUGIN_ID}_open_panel`, {
        name: 'Reborn Creator Tools (panneau)',
        description: 'Affiche le panneau des Creator Tools.',
        icon: 'construction',
        category: 'tools',
        click() {
          const p = panel as any;
          if (p.fold) p.fold(false);
          if (p.moveTo) p.moveTo('right_bar');
          Blockbench.showQuickMessage('Panneau Reborn Creator Tools -> barre de droite', 2000);
        },
      });
      MenuBar.addAction(openAction, 'tools');
      disposables.push(openAction);

      console.log('[reborn-creator-tools] loaded (Blockout / Handpaint / Motion)');
    } catch (err) {
      console.error('[reborn-creator-tools] onload crash', err);
      (Blockbench as any).showMessageBox?.({
        title: 'Reborn Creator Tools — echec du chargement',
        message: `Le plugin n'a pas pu s'initialiser :\n\n${String(err)}`,
      });
    }
  },

  onunload() {
    disposables.forEach((d) => {
      try {
        d.delete();
      } catch (e) {
        console.warn('[reborn-creator-tools] cleanup', e);
      }
    });
    disposables = [];
    console.log('[reborn-creator-tools] unloaded');
  },
});
