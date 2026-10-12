# Modèles 3D — bbforge, Blockbench, Model Engine

> **État au 2026-10-12.** Le détail de la méthode est dans [`tools/bbforge/METHODE.md`](../../tools/bbforge/METHODE.md)
> et le skill `.claude/skills/bbforge/SKILL.md` (déclenché dès qu'on parle de modèle 3D). Ajouter un modèle au
> serveur via le panel : [`../staff/NEXO_STAFF_GUIDE.md`](../staff/NEXO_STAFF_GUIDE.md).
>
> ⚠️ **`tools/bbforge/` et `tools/blockbench-handpaint/` sont des chantiers d'une autre session** : ne pas les
> modifier ni les commiter sans demande explicite.

---

## 1. bbforge — générer des modèles par code

- **Intention de l'utilisateur** : « l'idée est de t'utiliser pour réaliser des modèles ». Une demande de modèle
  3D passe par ce pipeline, pas à la main.
- `tools/bbforge/` (Python + Pillow + numpy) : un modèle est décrit en Python (`models/<nom>.py` :
  `build_model()` = arbre d'os, `ANIMS` = animations) ; une seule source produit le `.bbmodel`, le triplet
  Bedrock (`.geo.json`, `.animation.json`, `.png`) pour Model Engine, et un **GIF de contrôle** rendu sans
  Blockbench.
- Commandes : `python build.py --model <nom> [--anim <a>] [--still T --focus head] [--export-only]`,
  `python build_kirin.py`, `python validate.py dist/<nom>/<nom>.bbmodel`, `python java_item.py` (convertit
  un `.bbmodel` sans rotation en objet Java pour resource pack — utilisé pour les parchemins).
- **Livraison** : `dist/<nom>/` ; ouvrir le `.bbmodel` dans Blockbench **et** le GIF chez l'utilisateur, puis
  donner 3 points concrets à vérifier. **Il juge dans Blockbench**, pas seulement sur le GIF.
- **Boucle courte** : export → `validate.py` → images fixes → GIF complet en dernier.

### Modèles produits

| Modèle | Où | État |
|---|---|---|
| **Kirin** (dragon de foudre) | `dist/kirin/` | 147 cubes / 53 os, 32x, atlas 2048×1024, animations `apparition` (6 s) et `idle` (4 s). « c'est bien ». À reconfirmer : inversion verticale signalée le 09-21, signe de la mâchoire |
| **Son Gokū** (Quatre-Queues) | `dist/songoku/` | Modèle de l'équipe converti en cubes (Model Engine ne lit pas les meshes), **texture d'équipe conservée** et étendue dans son style, 218 cubes / 29 os, 6 animations (idle, walk, roar, 3 coups). Reste : tête, marques rouges, caméra pendant `roar` |
| Sac à dos, corbeau, boule de feu, mur de terre, lame chakraïque | `dist/<nom>/` | Créés le 09-23, aucun retour |
| **Parchemins D / C / B / A / S** | `dist/parchemin_*` | **En production** dans le mod (`reborn:parchemin_<rang>`) |

### Règles de texture de l'utilisateur

- **Aplats propres, pas de motifs** : écailles, biseaux, striations, grain, craquelures → « catastrophique ».
  « Améliorer la texture » = dégradés plus propres, tons justes, jamais plus de détails.
- **32x partout** (2 texels par unité), décor compris ; exception acceptée pour un colosse (Son Gokū en 1x).
- **Une texture d'équipe se conserve telle quelle** : garder l'atlas et les UV d'origine, ne remplir que les faces
  nues, dans le style existant, sans changer la taille de l'atlas (« La texturisation t'as tout gâché… le style
  était parfait »).
- Style maison : aplats francs, lignes de 1 px dans le grand axe, 3-4 tons ; dégradé en travers pour un cylindre.
- Toute nouvelle passe de texture se valide sur **une image fixe** d'abord, et se propose comme option.

### Pièges

- Blockbench échoue **en silence** : cube absent de l'outliner, animator indexé par nom au lieu de l'uuid, clés
  numériques → toujours `validate.py`.
- Échelle d'un os héritée par ses enfants ; échelle qui tombe à 0 en interpolation linéaire ; cap / assiette /
  roulis sur trois os séparés.
- L'émission (parties lumineuses) n'existe que dans le rendu bbforge : Blockbench et Model Engine l'ignorent.

---

## 2. Plugins Blockbench maison

| Plugin | Dossier | État |
|---|---|---|
| **Reborn Handpainted** (clone de RuneFist *Handpainted Workflow*) | `tools/blockbench-handpaint/` | Réécriture **interrompue** le 2026-09-23 : nouveaux modules sur le disque, build et test dans Blockbench **non confirmés**, README à refaire. Non commité |
| **Reborn Compositor** (skins RP par couches) | `tools/blockbench-reborn-compositor/` | V1 (modèle Classic) ; roadmap : Slim, extraction de pièces et de palette |
| Creator Tools (Blockout, Handpainted, Motion Lab) | branche `feat/blockbench-creator-tools` | 1 commit non mergé (état au 17/09) |

Avis retenu : **le plugin d'abord**, un MCP Blockbench ne viendrait que par-dessus (pont plugin ↔ serveur local).
Voir [`../technique/MCP_OUTILLAGE.md`](../technique/MCP_OUTILLAGE.md).

## 3. Model Engine

- Échelle 1 unité = 1/16 bloc ; os `hitbox` de 1,8 bloc, non animé.
- Exporter le triplet Bedrock ; noms d'os courts, ASCII et stables.
- **Aucune intégration serveur Model Engine n'a encore été faite.**

## 4. Reste à faire

- Commiter `tools/bbforge/` en entier (aujourd'hui seuls 9 fichiers sont sur `main`, l'outil ne tourne pas depuis un
  clone) — **avec l'accord de l'utilisateur**.
- Reprendre `blockbench-handpaint` : charger dans Blockbench, lister ce qui casse, réécrire le README.
- Intégrer un premier modèle animé côté serveur (Model Engine).
