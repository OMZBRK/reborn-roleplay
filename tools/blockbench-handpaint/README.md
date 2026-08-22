# Reborn Handpainted — Blockbench plugin

Suite d'outils Blockbench (5.1+) pour **radicalement simplifier la texturisation hand-painted** des modèles low-poly Reborn. Objectif directeur : chaque outil doit **supprimer du travail manuel** (peindre les ombres, l'AO, les dégradés, les arêtes) tout en laissant le contrôle artistique final. Toutes les passes générées restent des **TextureLayers Blockbench éditables** (réordonnables, mélangeables, masquables, régénérables) — jamais des résultats verrouillés.

Clone maison de la référence *RuneFist Handpainted Workflow*, taillé pour le pipeline Reborn (palettes shinobi, export direct vers les textures de mod).

## Les 6 outils (roadmap)

| # | Outil | Ce que ça évite de peindre à la main | Statut |
|---|-------|--------------------------------------|--------|
| 1 | **AO** | L'occlusion ambiante dans les recoins / chevauchements de géométrie (baking cube + mesh, tons Cool/Neutral/Warm, dithering pixel-art) | ✅ v1 |
| 2 | **Shade** | Mélanger à la main ses tons d'ombre/lumière : génère une rampe hand-painted (ombres froides, lumières chaudes) → palette + ombrage texture | ✅ v1 |
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

```pwsh
pnpm test         # tests du cœur math (rayon-triangle, AO, rasterisation) — sans Blockbench
```

Charger dans Blockbench : **File → Plugins → Load Plugin from File** → `dist/reborn-handpainted.js`.
En dev, Blockbench recharge le fichier à chaque rebuild (garder le dialog ouvert ou re-loader).

## Tester l'AO dans Blockbench

1. Ouvrir un modèle avec une texture mappée (mode Paint), sélectionner la texture.
2. **Tools → Bake AO (Handpainted)** → régler teinte / intensité / portée / rayons.
3. Confirmer : un calque `AO` (multiply) apparaît, éditable/masquable. Undo dispo.

**Points à vérifier visuellement (le baking ne peut pas être testé hors Blockbench) :**
- **Orientation UV des cubes** : la correspondance coin↔UV + rotation est en
  best-effort (`geometry.ts::cubeFaceUV` / `CUBE_FACE_KEYS`). Si l'ombre d'une
  face de cube apparaît tournée/miroir, ajuster l'ordre de rotation là.
- **Normales** : réorientées vers l'extérieur via le centre de l'élément
  (robuste pour formes convexes). Si tout ressort tout noir → normales inversées.
- **Hypothèse mono-texture** : on ombre toutes les faces sur la texture
  sélectionnée. Modèles multi-textures = évolution.
- **Perf** : `texels × rayons × triangles`. Sur 64² c'est instantané ; monter
  les rayons (>64) ou la résolution (256²+) peut geler l'UI une seconde.
