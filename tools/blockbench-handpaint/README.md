# Reborn Handpainted — Blockbench plugin

Suite d'outils Blockbench (5.1+) pour **radicalement simplifier la texturisation hand-painted** des modèles low-poly Reborn. Objectif directeur : chaque outil doit **supprimer du travail manuel** (peindre les ombres, l'AO, les dégradés, les arêtes) tout en laissant le contrôle artistique final. Toutes les passes générées restent des **TextureLayers Blockbench éditables** (réordonnables, mélangeables, masquables, régénérables) — jamais des résultats verrouillés.

Clone maison de la référence *RuneFist Handpainted Workflow*, taillé pour le pipeline Reborn (palettes shinobi, export direct vers les textures de mod).

## Les 6 outils (roadmap)

| # | Outil | Ce que ça évite de peindre à la main | Statut |
|---|-------|--------------------------------------|--------|
| 1 | **AO** | L'occlusion ambiante dans les recoins / chevauchements de géométrie (baking cube + mesh, tons Cool/Neutral/Warm, dithering pixel-art) | 🔜 |
| 2 | **Shade** | La sélection de gamme d'ombres/lumières depuis la palette active | 🔜 |
| 3 | **Lighting** | Highlights + ombres d'une couleur de base, dirigés par un « soleil » 3D placé dans le viewport | 🔜 |
| 4 | **Gradient** | Les dégradés de forme (bandes de valeur guidées par 2 points dans la vue) | 🔜 |
| 5 | **Edges** | Highlights / éclats / coutures sombres suivant les arêtes de la géométrie | 🔜 |
| 6 | **Surfaces** | Les matières récurrentes (cloth / fur / wood / stone) appliquées sans repeindre la base | 🔜 |

Ordre de construction choisi : **fondations → AO → Shade → Lighting → Gradient → Edges → Surfaces** (du plus haut rapport valeur/effort vers le plus spécialisé).

## Architecture

- `src/index.ts` — entrée : `Plugin.register`, wiring des outils, panneau latéral.
- `src/core/` — socle partagé : accès géométrie (cube + mesh), mapping UV↔texel, layer engine (création/écriture de TextureLayers + undo), système de palette/ramps.
- `src/tools/` — un module par outil (ao, shade, lighting, gradient, edges, surfaces).
- `src/ui/` — panneaux, dialogs, gizmos 3D.

## Dev

```pwsh
pnpm install
pnpm build        # bundle → dist/reborn-handpainted.js
pnpm watch        # rebuild à chaque save
```

Charger dans Blockbench : **File → Plugins → Load Plugin from File** → `dist/reborn-handpainted.js`.
En dev, Blockbench recharge le fichier à chaque rebuild (garder le dialog ouvert ou re-loader).
