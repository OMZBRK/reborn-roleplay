import esbuild from 'esbuild';

/**
 * Blockbench charge un plugin comme un seul fichier JS évalué dans son contexte global
 * (BB, Plugin, Texture, Cube, Mesh, Dialog, Panel, THREE... sont des globals injectés).
 * On bundle donc en IIFE, sans externaliser quoi que ce soit, format classique navigateur.
 */
const options = {
  entryPoints: ['src/index.ts'],
  // Le nom de fichier DOIT correspondre à l'ID du plugin (Plugin.register),
  // sinon Blockbench refuse de le charger ("base file name must match plugin ID").
  outfile: 'dist/reborn_handpainted.js',
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: 'es2020',
  legalComments: 'none',
  logLevel: 'info',
};

const watch = process.argv.includes('--watch');

if (watch) {
  const ctx = await esbuild.context(options);
  await ctx.watch();
  console.log('[reborn-handpainted] watching…');
} else {
  await esbuild.build(options);
}
