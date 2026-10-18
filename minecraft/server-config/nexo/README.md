# Nexo — config items Reborn (sacs + cosmétiques)

Configs de départ pour les items custom 3D du serveur RP, gérés par
**Nexo** (plugin Paper, MC 26.x / lignée Oraxen-like). Ces fichiers ne sont
que la **couche d'enregistrement** : les vrais modèles 3D (`.json`) et
textures (`.png`) restent à créer/importer (Blockbench), voir plus bas.

## Fichiers

| Fichier | Contenu |
|---|---|
| `items/reborn_sacs.yml` | 4 sacs : `sac_sacoche`, `sac_bandouliere`, `sac_dos`, `sac_lourd` |
| `items/reborn_cosmetiques.yml` | 2 cosmétiques d'exemple : `masque_anbu`, `bandeau_konoha` (sans modèle) |
| `items/reborn_cosmetiques_ninja.yml` | cosmétiques modélisés par l'utilisateur (copie du serveur dev, 18/10) : `masque_hannya`, rouleaux, `akatsuki_hat`, `bandeau_ninja` (le bandeau, **teintable**) |

## 1. Où poser ces fichiers sur le serveur live

Copier les `.yml` dans le dossier items de Nexo :

```
plugins/Nexo/items/reborn_sacs.yml
plugins/Nexo/items/reborn_cosmetiques.yml
```

Nexo charge automatiquement tous les `.yml` de `plugins/Nexo/items/`.

## 2. Où déposer les modèles 3D + textures (le resource pack)

Nexo héberge son propre resource pack sous `plugins/Nexo/pack/`. Structure
vanilla classique, indexée par namespace (`reborn` pour nous) :

```
plugins/Nexo/pack/assets/reborn/models/item/sac_sacoche.json
plugins/Nexo/pack/assets/reborn/models/item/sac_bandouliere.json
plugins/Nexo/pack/assets/reborn/models/item/sac_dos.json
plugins/Nexo/pack/assets/reborn/models/item/sac_lourd.json
plugins/Nexo/pack/assets/reborn/models/item/masque_anbu.json
plugins/Nexo/pack/assets/reborn/models/item/bandeau_konoha.json

plugins/Nexo/pack/assets/reborn/textures/item/...   (les .png référencés par les .json)
```

Le champ `Pack.model: reborn:item/sac_sacoche` dans le YAML pointe
exactement sur `assets/reborn/models/item/sac_sacoche.json`.

> **Placeholders fournis.** Des modèles 3D + textures de test pour les 4 sacs
> sont déjà présents sous `pack/assets/reborn/` (des pochettes en cuir, taille
> croissante par tier). Ils prouvent toute la chaîne dès l'install — à
> **remplacer** par les vrais modèles **Blockbench** (export "Java Block/Item"
> en `.json`) quand ils seront prêts. `masque_anbu` n'a pas encore de modèle
> → cube « manquant », mais l'item Nexo existe et se donne quand même.
> `bandeau_konoha` n'a pas de modèle. Le bandeau porté en jeu est `bandeau_ninja`
> (modèle Blockbench de l'utilisateur `models/item/bandeau.json`), teintable : voir § 7.

### Test rapide (une fois Nexo installé)

```
# 1. copier configs + pack
plugins/Nexo/items/reborn_sacs.yml
plugins/Nexo/items/reborn_cosmetiques.yml
plugins/Nexo/pack/assets/reborn/...     (models + textures de ce dossier)

# 2. recharger + se donner un sac
/nexo reload
/nexo give <toi> sac_sacoche 1
```

→ un petit sac 3D apparaît en main. `sac_bandouliere` / `sac_dos` /
`sac_lourd` sont de plus en plus gros.

## 3. Comment Nexo génère et sert le pack

Au démarrage / reload, Nexo compile `plugins/Nexo/pack/` en un resource
pack unique et le distribue aux joueurs. Le mode d'hébergement se règle
dans `plugins/Nexo/settings.yml` via `Pack.server.type` :

- `POLYMATH` — serveur d'upload de Nexo (défaut le plus simple)
- `SELFHOST` — sert le pack depuis la machine du serveur (port ouvert)
- `LOBFILE` / `S3` — hébergement externe (S3, R2, …)
- `NONE` — pas de distribution automatique

## 4. Recharger après modification

```
/nexo reload items    # recharge uniquement les items (les .yml)
/nexo reload pack      # reconstruit + renvoie le resource pack
/nexo reload           # tout : items, recettes, pack
```

Utilitaires :

```
/nexo give <joueur> sac_sacoche 1
/nexo iteminfo sac_sacoche
/nexo inventory        # aperçu de tous les items Nexo
```

## 5. Tie-in avec ShinobiCore (système RP)

Les ids ci-dessus sont volontairement alignés sur le système d'inventaire /
sacoche RP de **ShinobiCore**. Objectif :

- équiper une sacoche dans le menu RP → le serveur peut donner l'item Nexo
  correspondant (`/nexo give` ou API) pour l'**affichage 3D en main** ;
- lors d'une **fouille INRP**, présenter l'item Nexo du sac que porte la
  cible.

Le mapping id RP → id Nexo est 1:1 (`sac_sacoche` RP ↔ `sac_sacoche` Nexo),
donc côté Java il suffit de réutiliser la même chaîne d'id.

## 6. item_model vs CustomModelData (MC 26.x)

Sur un serveur récent (post-1.21.4), garder Nexo en mode **item_model** :
chaque item reçoit son propre `minecraft:item_model` (`nexo:<id>`), sans
CustomModelData partagé. Résultat : **aucun conflit** et un **nombre
illimité** de modèles distincts coexistants. Réglage dans
`plugins/Nexo/settings.yml` :

```yaml
Pack:
  generation:
    prefer_item_models: true
```

(Le passage depuis un ancien mode CustomModelData se fait via
`/nexo reset_custom_model_data` puis `/nexo reload` — inutile sur une
install neuve.)

## 7. Objets teintables (bandeau, 18/10)

Un objet Nexo dont une partie se teint (ex. le tissu du bandeau, pas sa plaque) :

- YAML : `color: "#RRGGBB"` sur l'objet (couleur par défaut). Nexo pose cette couleur
  sur l'objet (composant `minecraft:dyed_color`) et génère la définition
  `nexo:<id>` avec une source de teinte `minecraft:dye` (défaut = cette couleur).
- Modèle : `"tintindex": 0` sur **chaque face** à teindre, rien sur le reste ; les
  faces teintes sont peintes en **gris clair** (le jeu multiplie ce gris par la
  couleur). Pour que le défaut rende comme l'original, `color` = couleur d'origine
  du tissu ÷ (gris / 255).
- En jeu : sacoche › clic droit sur le bandeau porté › « Teinture » (ShinobiCore
  réécrit `dyed_color` ; ids teintables : `inventaire.teinture.ids` + tout `bandeau_*`).
  Le staff peut aussi tester avec `/nexo dye <couleur>` sur l'objet en main.

**Le bandeau** = `bandeau_ninja` (modèle Blockbench de l'utilisateur, forme inchangée) :
`color: "#302C32"` (le tissu presque noir d'origine), `tintindex 0` sur les 10 éléments
du tissu, rien sur le groupe `metal` (la plaque) ; tissu de `texturebandeau.png` en gris
clair, original gardé dans `texturebandeau_origine.png`. Outil :
`mod-hud/tools/teinture_bandeau_nexo.py` — **à relancer après chaque nouvel export
Blockbench du bandeau** (déposer le nouvel original, supprimer `texturebandeau_origine.png`,
relancer : l'outil remet les `tintindex`, refait le gris et affiche la valeur de `color:`).

Fichiers du bandeau à déployer dans `plugins/Nexo/` (rien d'autre) :
`items/reborn_cosmetiques_ninja.yml`, `pack/assets/reborn/models/item/bandeau.json`,
`pack/assets/reborn/textures/item/texturebandeau.png` ; puis `/nexo reload all`.
Ne pas déployer `texturebandeau_origine.png` (sauvegarde). Les bandeaux déjà distribués
**sans** `color` gardent leur aspect (la teinte par défaut du modèle est la couleur
d'origine) et se teignent quand même.
