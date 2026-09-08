# ADR 0005 — Pipeline d'assets 3D : Blockbench → Nexo → panel staff

**Statut** : Accepté — 2026-08-28
**Documents liés** : [`NEXO_STAFF_GUIDE.md`](../NEXO_STAFF_GUIDE.md),
[`EMOTES.md`](../EMOTES.md), [`MCP_OUTILLAGE.md`](../MCP_OUTILLAGE.md)

## Contexte

La beta demande beaucoup d'art : VFX par nature, projectiles, armes, cosmétiques,
entités. Si chaque nouvel asset exige un rebuild de mod + une republication de manifest
+ une mise à jour forcée de tous les joueurs, la production d'art devient goulot
d'étranglement et le rythme est plafonné par la disponibilité d'un développeur.

Deux croyances traînaient et bloquaient le raisonnement :

- « en Minecraft moderne, un modèle 3D custom passe forcément par la tête d'un joueur » —
  **faux** : le composant `item_model` permet à n'importe quel item de porter son
  propre modèle, sans limite de nombre ;
- « ajouter une emote demande une mise à jour du mod » — **faux** aussi : EmoteCraft
  charge des emotes poussées par le serveur à la connexion.

## Décision

**Les assets de contenu ne passent pas par le code.** Trois pipelines data-driven,
tous pilotables par un **Modélisateur** ou un **Développeur** depuis le panel staff
(onglet Fichiers, périmètre scopé par grade) :

| Asset | Outil d'auteur | Dépôt | Activation |
|---|---|---|---|
| **Modèle 3D / item** | Blockbench → *Java Item* (`.json` + `.png`) | `Nexo/pack/assets/reborn/models/item/` + `textures/item/` + déclaration dans `Nexo/items/*.yml` | `/nexo reload` (pas de restart) |
| **Texture animée** | spritesheet vertical + `.png.mcmeta` | même dossier | `/nexo reload` |
| **Emote / animation joueur** | Blockbench (plugin GeckoLib) ou Blender (addon KosmX) → `.emotecraft` / JSON PAL | `plugins/ShinobiCore/emotes/` | `/playemote reload` — poussé à tous les clients via `reborn:emotepack` |
| **Assets du créateur de perso** | PNG 64×64 + 1 ligne dans `catalog.json` | périmètre « Character creator » du panel | diffusion live serveur → mod |

Deux **pièges** documentés une fois pour toutes (ils ont coûté du temps deux fois) :

1. Blockbench écrit `"textures": {"0": "ma_texture"}` — nom nu. Minecraft ne trouve pas
   la texture → modèle violet. Il faut `"reborn:item/ma_texture"`.
2. Un spritesheet animé doit avoir des frames **carrées, de même taille, empilées
   verticalement** : hauteur = nb_frames × largeur.

## Conséquences

- ✅ Un modélisateur produit et met en jeu sans développeur, sans build, sans
  republication de manifest.
- ✅ Le jar `reborn-hud` reste léger (~20 Mo) : les assets lourds ne le traversent pas.
- ✅ Chaque action du panel est sauvegardée (`.bak`) et tracée (qui a fait quoi), et
  reste confinée au périmètre du grade.
- ⚠️ La contrepartie : les assets ne sont plus versionnés par git. **Le contenu Nexo et
  les emotes doivent être sauvegardés à part** (le panel garde des `.bak`, ce n'est pas
  une stratégie de backup).
- ➡️ Les outils Blockbench maison (`tools/blockbench-reborn-compositor`,
  `tools/blockbench-handpaint`) s'inscrivent en amont de ce pipeline : ils réduisent le
  temps de fabrication, pas le temps de mise en jeu.
