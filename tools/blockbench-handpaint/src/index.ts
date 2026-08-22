/**
 * Reborn Handpainted — Blockbench plugin entry point.
 *
 * Suite d'outils hand-painted (AO, Shade, Lighting, Gradient, Edges, Surfaces).
 * Ce fichier ne fait que l'enregistrement + le wiring ; la logique vit dans
 * src/core et src/tools.
 */
import { openAODialog } from './tools/ao.ts';

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
  version: '0.2.0',
  min_version: '5.1.0',
  variant: 'desktop',

  onload() {
    const aoAction = new Action(`${PLUGIN_ID}_ao`, {
      name: 'Bake AO (Handpainted)',
      description:
        'Génère l\'occlusion ambiante dans un calque multiply éditable ' +
        '(ombre de contact dans les recoins).',
      icon: 'blur_on',
      category: 'textures',
      condition: () => (typeof Texture !== 'undefined' && !!(Texture as any).all?.length),
      click() {
        openAODialog();
      },
    });
    MenuBar.addAction(aoAction, 'tools');
    disposables.push(aoAction);

    console.log('[reborn-handpainted] loaded (AO)');
  },

  onunload() {
    disposables.forEach((d) => d.delete());
    disposables = [];
    console.log('[reborn-handpainted] unloaded');
  },
});
