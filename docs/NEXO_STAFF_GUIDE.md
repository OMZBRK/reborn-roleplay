# Guide staff — Ajouter des modèles 3D & textures animées (Nexo) depuis le panel

Ce guide explique comment un **Modélisateur** ou **Développeur** ajoute un item 3D
custom (arme, cosmétique, effet de spell…) et **anime sa texture**, entièrement
depuis **panel.reborn-rp.com → onglet Fichiers**, sans rebuild ni accès SFTP.

---

## 0. Prérequis

- Grade **Modélisateur** (accès `Nexo`) ou **Développeur** (accès `Nexo` + MagicSpells/MythicMobs/ModelEngine).
- Panel → **Fichiers** → tu vois ta racine `Nexo — modèles & items`.
- Ton modèle exporté depuis **Blockbench** en *Java Item* (`.json`) + sa/ses texture(s) `.png`.

> Toute action (édition, upload, déplacement, suppression) est **sauvegardée
> automatiquement** (`.bak`) et **tracée** (qui a fait quoi). Tu ne peux sortir
> que de ton périmètre `Nexo`.

---

## 1. Où va quoi (structure Nexo)

À l'intérieur de `Nexo/` :

| Élément | Emplacement |
|---|---|
| **Modèle 3D** | `pack/assets/reborn/models/item/<id>.json` |
| **Texture** | `pack/assets/reborn/textures/item/<tex>.png` |
| **Animation** (si texture animée) | `pack/assets/reborn/textures/item/<tex>.png.mcmeta` |
| **Définition d'item** | `items/<fichier>.yml` |

L'**id** d'un modèle = le nom du fichier `.json` sans extension. Le **nom de la
texture** = le nom du `.png` sans extension.

---

## 2. Ajouter un modèle avec une texture FIXE

### Étape 1 — Corriger la référence de texture (⚠️ piège n°1)

Ouvre ton `.json` exporté de Blockbench. Il contient une ligne du genre :

```json
"textures": { "0": "ma_texture", "particle": "ma_texture" }
```

**Blockbench écrit le nom nu → Minecraft ne trouve pas la texture → modèle
invisible/violet.** Remplace par le chemin complet `reborn:item/…` :

```json
"textures": { "0": "reborn:item/ma_texture", "particle": "reborn:item/ma_texture" }
```

### Étape 2 — Importer le modèle et la texture

1. Panel → **Fichiers** → navigue dans `Nexo/pack/assets/reborn/models/item/` → **Importer** ton `.json`.
2. Va dans `Nexo/pack/assets/reborn/textures/item/` → **Importer** ton `.png`.

### Étape 3 — Déclarer l'item

Va dans `Nexo/items/`, ouvre un `.yml` existant (ex. `reborn_armes_ninja.yml`) ou
crée-en un (**Nouveau fichier** → `reborn_mes_items.yml`), puis ajoute :

```yaml
mon_item:
  itemname: "<white>Mon Item"
  material: PAPER
  lore:
    - "<gray>Item ninja"
  Pack:
    model: reborn:item/mon_id      # = le nom de ton .json
```

> Tous les items Nexo sont des `PAPER` habillés par le modèle — c'est normal.

### Étape 4 — Recharger & tester

En jeu :

```
/nexo reload
/nexo give <toi> mon_item 1
```

Ton modèle 3D apparaît en main. (Reconnecte-toi si le pack ne se met pas à jour.)

---

## 3. Ajouter une texture ANIMÉE

Le principe : Minecraft anime une texture via un **spritesheet vertical** + un
petit fichier **`.mcmeta`** posé à côté du `.png`.

### Étape 1 — Préparer le spritesheet (⚠️ piège n°2)

- Toutes les frames doivent être **carrées et de même taille**, **empilées
  verticalement** (la frame 1 en haut, la 2 en dessous, etc.).
- Donc la **hauteur = nombre de frames × largeur**.
  Exemple : 8 frames de 64×64 → un PNG de **64 × 512**.
  (Notre effet `fight1` = 12 frames de 256×256 → PNG 256 × 3072.)

Importe ce `.png` dans `textures/item/` comme au chapitre 2.

### Étape 2 — Créer le fichier d'animation `.mcmeta`

Dans le **même dossier** que le PNG, clique **Nouveau fichier** et nomme-le
exactement `<tex>.png.mcmeta` (ex. `fight1.png.mcmeta`). Puis édite-le :

```json
{
  "animation": {
    "frametime": 2,
    "frames": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
  }
}
```

- `frametime` = **nombre de ticks par frame** (20 ticks = 1 s).
  `2` = ~0,1 s/frame → une boucle de 12 frames dure 1,2 s. Monte pour ralentir.
- `frames` = l'ordre de lecture. Si c'est simplement 0,1,2,… tu peux même
  omettre la liste — MC détecte tout seul le nombre de frames (hauteur ÷ largeur).
- Optionnel : ajoute `"interpolate": true` pour un fondu fluide entre les frames.

### Étape 3 — Recharger

```
/nexo reload
```

**C'est tout.** Dès que la texture est animée par le `.mcmeta`, **n'importe quel
affichage** du modèle (en main, ItemDisplay, armorstand, effet de spell…) joue
l'animation automatiquement.

Pour **changer la vitesse** plus tard : édite juste `frametime` dans le `.mcmeta`
depuis le panel → `/nexo reload pack`.

---

## 4. Utiliser le modèle animé dans un spell MagicSpells

MagicSpells n'a rien à animer : il doit juste **afficher l'item Nexo**. La façon
moderne = un effet `itemdisplay` (ou `armorstand`) qui montre l'item à l'impact.

D'abord, récupère l'identifiant exact à donner à MagicSpells :

```
/nexo iteminfo mon_item     → te montre l'item-model (ex. nexo:mon_item)
```

Puis dans la config du spell :

```yaml
effects:
  impact:
    position: target
    effect: itemdisplay
    item: nexo:mon_item        # ou un magic item paper + itemmodel: nexo:mon_item
    duration: 24               # en ticks
    scale: 2.0
```

L'animation de la texture joue toute seule pendant que l'item est affiché.

---

## 5. Checklist des pièges (à vérifier si ça ne marche pas)

- [ ] **Texture invisible / violette** → la ref dans le `.json` n'est pas
  `reborn:item/<tex>` (Blockbench écrit le nom nu).
- [ ] **Animation ne joue pas** → le PNG n'est pas un spritesheet **vertical**
  de frames carrées, OU le `.mcmeta` est mal nommé (doit être
  `<tex>.png.mcmeta`, pas `<tex>.mcmeta`).
- [ ] **Rien n'apparaît en jeu** → tu as oublié `/nexo reload`, ou tu dois te
  **reconnecter** pour retélécharger le resource pack.
- [ ] **`texture_size` ≠ dimensions du PNG** dans le `.json` → **normal** (les
  textures HD ont un `texture_size` plus petit que le PNG, MC mappe les UV
  proportionnellement). **Ne le change pas.**
- [ ] **L'item ne se donne pas** (`/nexo give` échoue) → l'entrée dans le `.yml`
  est mal indentée (2 espaces, pas de tab) ou le fichier `.yml` n'est pas dans
  `Nexo/items/`.

---

## 6. Résumé express

1. Modèle `.json` (ref texture `reborn:item/<tex>`) → `pack/.../models/item/`.
2. Texture `.png` → `pack/.../textures/item/`.
3. *(animé)* `<tex>.png.mcmeta` (spritesheet vertical + `frametime`) → même dossier.
4. Item dans un `.yml` → `Nexo/items/` (`Pack.model: reborn:item/<id>`).
5. `/nexo reload` → `/nexo give` → tester.

Tout depuis **panel → Fichiers** : *Importer* (modèle/texture), *Nouveau fichier*
(le `.mcmeta`), *éditer* (corriger les refs), *Nouveau dossier* / glisser-déposer
(organiser). Périmètre limité à `Nexo`, backups + audit automatiques.
