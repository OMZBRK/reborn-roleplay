#!/usr/bin/env node
/**
 * Garde-fou du panel (PANEL_V2 §5.6), lancé par `pnpm build` avant
 * `next build` (pnpm ne lance pas les scripts `prebuild`), et à la main :
 * `pnpm garde-fou`. L'image Docker appelle `next build` directement : le
 * garde-fou ne bloque jamais un déploiement, il se lance en local.
 *
 * Refuse, dans le code des pages (app/, fichiers .ts et .tsx) :
 *  - les couleurs arbitraires `[var(--color-…)]` : le kit a ses classes
 *    (`text-fg-3`, `border-border`, `bg-surface-2`, `text-gold`…) ;
 *  - les couleurs `#hex` écrites en dur ;
 * et, dans app/ comme dans components/, les fenêtres natives
 * `window.confirm` / `window.prompt` (remplacées par useConfirm).
 *
 * Les pages pas encore passées au kit sont en exception ; la liste rétrécit à
 * chaque lot (PANEL_V2 §12). Aucune dépendance : Node seul.
 */

import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const RACINE = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Pages pas encore migrées (lot L6 : Fichiers, Annonces, Techniques, Wiki). */
const EXCEPTIONS_COULEURS = new Set([
  'app/(panel)/abilities/page.tsx',
  'app/(panel)/discord/page.tsx',
  'app/(panel)/files/page.tsx',
  'app/(panel)/wiki/page.tsx',
  'app/(panel)/wiki/ideas/page.tsx',
]);

const REGLES = [
  {
    nom: 'couleur arbitraire [var(--color-…)] : utiliser une classe du kit',
    motif: /\[var\(--color[\w-]*\)\]/g,
    dossiers: ['app'],
    exceptions: EXCEPTIONS_COULEURS,
  },
  {
    nom: 'couleur #hex écrite en dur : utiliser un token du kit',
    // #rgb, #rrggbb, #rrggbbaa après un guillemet, un espace, « : » ou « ( ».
    motif: /(?<=["'`\s:(])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{3})(?![\w-])/g,
    dossiers: ['app'],
    exceptions: EXCEPTIONS_COULEURS,
  },
  {
    nom: 'fenêtre native du navigateur : utiliser useConfirm (components/ui/Dialog)',
    motif: /\bwindow\.(?:confirm|prompt)\s*\(/g,
    dossiers: ['app', 'components'],
    exceptions: new Set(),
  },
];

/** Fichiers .ts / .tsx d'un dossier, chemins relatifs à la racine du panel (séparateur « / »). */
function fichiers(dossier) {
  const out = [];
  const pile = [join(RACINE, dossier)];
  while (pile.length > 0) {
    const d = pile.pop();
    for (const nom of readdirSync(d)) {
      if (nom === 'node_modules' || nom.startsWith('.')) continue;
      const chemin = join(d, nom);
      if (statSync(chemin).isDirectory()) pile.push(chemin);
      else if (/\.(ts|tsx)$/.test(nom)) out.push(relative(RACINE, chemin).split(sep).join('/'));
    }
  }
  return out.sort();
}

const fautes = [];
for (const regle of REGLES) {
  for (const dossier of regle.dossiers) {
    for (const f of fichiers(dossier)) {
      if (regle.exceptions.has(f)) continue;
      const lignes = readFileSync(join(RACINE, f), 'utf8').split(/\r?\n/);
      lignes.forEach((ligne, i) => {
        for (const m of ligne.matchAll(regle.motif)) {
          fautes.push(`${f}:${i + 1}  ${m[0]}  → ${regle.nom}`);
        }
      });
    }
  }
}

if (fautes.length > 0) {
  console.error(`Garde-fou du panel : ${fautes.length} écart(s) au kit (PANEL_V2 §5.6)\n`);
  for (const f of fautes) console.error(`  ${f}`);
  console.error(
    '\nCorrige-les, ou, pour une page pas encore migrée, ajoute-la aux exceptions de scripts/garde-fou.mjs.',
  );
  process.exit(1);
}
console.log('Garde-fou du panel : aucun écart au kit.');
