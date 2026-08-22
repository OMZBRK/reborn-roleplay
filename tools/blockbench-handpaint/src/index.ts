/**
 * Reborn Handpainted — Blockbench plugin entry point.
 *
 * Suite d'outils hand-painted (AO, Shade, Lighting, Gradient, Edges, Surfaces).
 * Ce fichier ne fait que l'enregistrement + le wiring ; la logique vit dans src/core et src/tools.
 *
 * NOTE: squelette initial. Les outils sont branchés au fur et à mesure de leur implémentation.
 */

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
  version: '0.1.0',
  min_version: '5.1.0',
  variant: 'desktop',

  onload() {
    // Point d'ancrage : un menu regroupant les outils, alimenté par les modules à venir.
    const openAction = new Action(`${PLUGIN_ID}_hello`, {
      name: 'Reborn Handpainted',
      description: 'Suite hand-painted (en construction)',
      icon: 'brush',
      click() {
        Blockbench.showQuickMessage('Reborn Handpainted — squelette chargé ✔', 1500);
      },
    });
    MenuBar.addAction(openAction, 'tools');
    disposables.push(openAction);

    console.log('[reborn-handpainted] loaded');
  },

  onunload() {
    disposables.forEach((d) => d.delete());
    disposables = [];
    console.log('[reborn-handpainted] unloaded');
  },
});
