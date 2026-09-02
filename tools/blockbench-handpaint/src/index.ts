/**
 * Reborn Handpainted — Blockbench plugin entry point.
 *
 * Suite d'outils hand-painted (AO, Shade, Lighting, Gradient, Edges, Surfaces).
 * L'UI principale est un panneau latéral (src/ui/panel.ts) ; ce fichier ne fait
 * que l'enregistrement + le wiring. La logique vit dans src/core et src/tools.
 */
import { createHandpaintedPanel } from './ui/panel.ts';

const PLUGIN_ID = 'reborn_handpainted';

// Éléments UI créés au chargement, à nettoyer au déchargement.
let disposables: Array<{ delete: () => void }> = [];

BBPlugin.register(PLUGIN_ID, {
  title: 'Reborn Handpainted',
  author: 'Reborn Roleplay',
  icon: 'brush',
  description:
    'Outils hand-painted pour Blockbench : AO, Shade, Lighting, Gradient, Edges, Surfaces. ' +
    'Génère des passes de texture en TextureLayers éditables.',
  tags: ['Texture', 'Paint', 'Minecraft'],
  version: '0.3.0',
  min_version: '5.1.0',
  variant: 'desktop',

  onload() {
    try {
      // Panneau latéral (UI principale, style RuneFist).
      const panel = createHandpaintedPanel();
      disposables.push(panel);

      // Action menu pour (ré)afficher le panneau s'il a été fermé.
      const openAction = new Action(`${PLUGIN_ID}_open_panel`, {
        name: 'Reborn Handpainted (panneau)',
        description: 'Affiche le panneau des outils hand-painted.',
        icon: 'brush',
        category: 'tools',
        click() {
          const p = panel as any;
          if (p.fold) p.fold(false);
          if (p.moveTo) p.moveTo('right_bar');
          Blockbench.showQuickMessage('Panneau Reborn Handpainted → barre de droite', 2000);
        },
      });
      MenuBar.addAction(openAction, 'tools');
      disposables.push(openAction);

      console.log('[reborn-handpainted] loaded (panel: AO + Shade)');
    } catch (err) {
      console.error('[reborn-handpainted] onload crash', err);
      (Blockbench as any).showMessageBox?.({
        title: 'Reborn Handpainted — échec du chargement',
        message: `Le plugin n'a pas pu s'initialiser :\n\n${String(err)}`,
      });
    }
  },

  onunload() {
    disposables.forEach((d) => {
      try {
        d.delete();
      } catch (e) {
        console.warn('[reborn-handpainted] cleanup', e);
      }
    });
    disposables = [];
    console.log('[reborn-handpainted] unloaded');
  },
});
