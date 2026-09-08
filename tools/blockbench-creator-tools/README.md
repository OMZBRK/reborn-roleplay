# Reborn Creator Tools — plugin Blockbench

Suite d'outils Blockbench (5.1+) pour la production d'assets Reborn. **Quatre
workspaces dans un seul panneau latéral**, calqués sur la suite de référence
[RuneFist Creator Tools](https://runefist.com/creator-tools) :

| Workspace | Domaine | Ce qu'il fait | Statut |
|---|---|---|---|
| 🧱 **Blockout Canvas** | Modeling | Block, chain, pose, reuse | ✅ v1 |
| 🎨 **Handpainted Workflow** | Texturing | AO, lighting, edges, gradient, surfaces, shade | ✅ complet |
| 🎬 **Motion Lab** | Animation | Pose, shape, keep the keys | ✅ v1 |
| 🖼️ **Render Studio** | Scenes | Mettre en scène et présenter | 🔜 planifié |

**Le fil rouge** : chaque outil doit **supprimer du travail manuel** sans retirer
le contrôle artistique. Toutes les passes générées restent des **TextureLayers
Blockbench éditables** — réordonnables, mélangeables, masquables, régénérables.
Jamais un résultat verrouillé.

---

## 🧱 Blockout Canvas

Les quatre gestes du blocking, ceux qu'on refait sur chaque modèle.

| Outil | Ce que ça évite de faire à la main |
|---|---|
| **Block** | Créer un cube puis corriger six champs numériques. Taille, calage sur grille et pivot centré en une fois. |
| **Chain** | Dupliquer vingt fois un élément puis reprendre chaque pas. Décalage, rotation et échelle progressifs, avec atténuation. |
| **Pose** | Symétriser à la main (et se tromper de signe). Miroir X/Y/Z + recentrage des pivots. |
| **Reuse** | Redessiner une pièce déjà faite ailleurs. Bibliothèque de pièces réinsérables, UV conservées. |

**Le mode « enroulé » du Chain** mérite un mot : chaque copie repart du repère de
la précédente, donc les rotations s'accumulent et la chaîne se courbe. C'est ce
qui fabrique une queue, un tentacule ou une vrille — là où le mode « droit »
donne une rangée régulière. Quatre pas à 90° referment exactement le cercle
(propriété testée).

> Cubes uniquement en v1 : les meshes ne sont pas dupliqués par le Chain.

## 🎨 Handpainted Workflow

| Outil | Ce que ça évite de peindre | Calque produit |
|---|---|---|
| **Lighting** | Le modelé « 3D » d'une texture plate | `Light Shadow` (multiply) + `Light Highlight` (screen) |
| **AO** | L'occlusion dans les recoins et sous les chevauchements | `AO` (multiply) |
| **Edges** | Le liseré clair sur chaque arête et la ligne sombre des jonctions | `Edge Highlight` (screen) + `Edge Seam` (multiply) |
| **Gradient** | La montée de valeur du bas vers le haut d'une pièce | `Gradient` (multiply) |
| **Surfaces** | Le grain d'une matière (tissu, fourrure, bois, pierre) | `Surface <matière>` (+ crêtes en screen) |
| **Shade** | Mélanger à la main ses tons d'ombre et de lumière | palette + ombrage |

**Auto hand-paint (1 clic)** enchaîne Lighting + AO + Edges avec de bons
réglages : la passe qui transforme un aplat en surface lisible. On ajuste
ensuite outil par outil.

Deux détails qui font la différence à l'usage :

- **Edges travaille en espace UV**, pas en 3D. Conséquence : le bord d'un îlot UV
  est traité comme une arête, donc **les coutures reçoivent aussi leur trait** —
  ce qu'une approche purement géométrique raterait.
- **Surfaces est déterministe.** Même graine = même grain, toujours. On peut
  régénérer un calque sans perdre le rendu. En projection « monde », le motif
  garde la même échelle partout et traverse les coutures sans se couper.

## 🎬 Motion Lab

**Le principe qui gouverne tout : on ne resample jamais.** Une animation est le
travail d'un animateur, pas une courbe à échantillonner. Chaque outil déplace,
copie ou ajuste les clés **existantes** ; aucun ne remplace l'animation par une
purée de keyframes générées. C'est la différence entre un outil qui assiste et
un outil qui écrase.

| Outil | Ce qu'il fait |
|---|---|
| **Pose** | Capture la pose de l'instant courant, la range, la repose ailleurs — autre image, autre animation, autre projet. |
| **Mirror** | Symétrise une pose gauche/droite, avec les inversions de signe correctes. |
| **Shape** | Redistribue les instants selon une courbe (anticipation, dépassement, rebond…), étire, décale, force l'interpolation. |
| **Keep** | Ferme la boucle (recopie la pose de t=0 à la fin) et déphase un membre pour le désynchroniser. |

La symétrie repère les paires par le nom : `leftArm` ↔ `rightArm`,
`bras_left` ↔ `bras_right`, `jambe_l` ↔ `jambe_r`. Le camelCase collé des modèles
Minecraft vanilla est géré, et les faux positifs (`clavicule`, `bright`) sont
écartés.

---

## Architecture

```
src/
├── index.ts        enregistrement du plugin + wiring
├── core/           100% pur, AUCUN accès Blockbench → testable en node
│   ├── color.ts      rampes hand-painted, conversions HSL
│   ├── raymath.ts    intersection rayon/triangle, occlusion
│   ├── geometry.ts   extraction de la géométrie, itérateur de texels
│   ├── layers.ts     émission de TextureLayers
│   ├── field.ts      axes de dégradé, classification d'arêtes, distance
│   ├── noise.ts      bruits déterministes, motifs de surface
│   ├── chain.ts      calcul des transformations d'une chaîne
│   └── easing.ts     courbes et retiming de keyframes
├── tools/          un module par outil (accès API Blockbench)
└── ui/panel.ts     le panneau à onglets
```

**La règle de découpage** : tout ce qui peut être pur l'est, et vit dans `core/`.
C'est ce qui permet de tester la logique de décision — où est le bord, saillant
ou rentrant, quel instant pour quelle clé — sans lancer Blockbench.

## Développement

```pwsh
pnpm install          # depuis la racine du monorepo (tools/* est dans le workspace)
pnpm --filter reborn-creator-tools build      # → dist/reborn_creator_tools.js
pnpm --filter reborn-creator-tools watch      # rebuild à chaque save
pnpm --filter reborn-creator-tools test       # 55 tests du cœur, sans Blockbench
pnpm --filter reborn-creator-tools typecheck
```

Charger dans Blockbench : **File → Plugins → Load Plugin from File** →
`dist/reborn_creator_tools.js`.

> ⚠️ Le nom du fichier **doit** correspondre à l'ID du plugin, sinon Blockbench
> refuse de le charger (« base file name must match plugin ID »). C'est pour ça
> que `esbuild.mjs` et `PLUGIN_ID` sont alignés.

Si le panneau n'apparaît pas : **Tools → Reborn Creator Tools (panneau)**.

## Ce qui demande une validation en jeu

Le baking ne peut pas être testé hors de Blockbench. Les points à vérifier à
l'œil sur un vrai modèle :

- **Orientation UV des cubes** — la correspondance coin↔UV et la rotation sont
  en best-effort (`core/geometry.ts::cubeFaceUV`). Si l'ombre d'une face
  apparaît tournée ou en miroir, c'est l'ordre de rotation à ajuster là.
- **Normales** — réorientées vers l'extérieur via le centre de l'élément, ce qui
  est robuste pour les formes convexes. Si tout ressort noir, elles sont
  inversées.
- **Hypothèse mono-texture** — on ombre toutes les faces sur la texture
  sélectionnée. Le multi-texture est une évolution.
- **Perf** — l'AO coûte `texels × rayons × triangles`. Instantané en 64² ;
  monter les rayons au-delà de 64 ou la résolution à 256²+ peut figer l'UI une
  seconde.
- **Motion Lab** — l'accès aux animators passe par l'API Blockbench avec des
  replis défensifs (`interpolate`, `createKeyframe`). À valider sur une vraie
  animation ; les erreurs remontent dans le panneau et la console.

## Diagnostics

Le bouton **Diagnostics** en bas du panneau affiche ce que le plugin voit :
cubes, meshes, sélection, texture active, animation active, keyframes
sélectionnées, format, palette, présence de THREE. C'est le premier réflexe
quand un outil « ne fait rien ».
