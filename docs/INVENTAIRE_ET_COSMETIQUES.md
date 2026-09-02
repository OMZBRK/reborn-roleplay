# Sacoche RP — inventaire custom lié au personnage + cosmétiques

Prototype construit le 2026-08-24. Répond à la demande : **inventaire lié au
personnage**, **limité par le sac** (non-vanilla visuellement), **système de
poids**, et faisabilité de **plusieurs cosmétiques 3D**.

Le design suit la ligne Zenkai/Naruto tout en restant dans la DA Minecraft
(cases custom, kanji, palette Akatsuki rouge/or) — cf. [[tirage-feuille]] et
[[character-creation-design]] pour le pattern client↔serveur réutilisé.

---

## TL;DR — réponses à tes 3 questions

| Ta question | Réponse | État |
|---|---|---|
| **9 cases de base**, l'espace se débloque **en équipant un sac** | **Fait (v2).** Sans sac = **9 cases « sur soi »** (la barre de base). Équiper une **Sacoche / Bandoulière / Sac / Sac lourd** ajoute des rangées **et** du poids max. Le sac s'équipe dans un emplacement dédié. Cf. §7. | ✅ |
| Plusieurs **modèles 3D custom** (pas que via la tête) | **Oui — le mythe « que via la tête » est faux sur MC moderne.** Composant `item_model` + resource pack → **n'importe quel item** porte son propre modèle 3D, en nombre illimité. **Nexo** fait exactement ça. Configs de départ créées. Cf. §8. | Config ✅ · Modèles à dessiner ⏳ |
| Plusieurs **cosmétiques** portés en même temps | **Oui.** Donnée en place (bandeau/masque/manteau/dos, équip/déséquip, aperçu). Rendu **sur le corps** = 1 `RenderLayer` client OU items Nexo. Cf. §4 + §8. | Donnée ✅ · Rendu ⏳ |
| **Système de poids** | **Fait.** Chaque objet a un poids ; jauge live qui vire au rouge près de la limite ; la limite dépend du sac. | ✅ |
| Inventaire **différent par personnage** | **Fait.** Le sac est résolu via le **personnage actif** (`plugin.characters().getActive`) et persisté par UUID de perso. | ✅ |

---

## 1. Architecture (data-driven, façon Zenkai)

Choix clé : le sac RP est **data-driven** (des objets = **données**, pas des
`ItemStack` vanilla) et **indépendant** de l'inventaire vanilla du personnage.
On ne touche **ni `ShinobiCharacter` ni le repository** — les sacs sont persistés
à part. C'est ce qui permet un rendu 100% custom, un poids, une limite de cases,
et des cosmétiques, sans se battre contre les 36 slots fixes de Minecraft.

```
  Touche C / commande            reborn:inventory (bidirectionnel)
  ┌──────────────┐   "open"      ┌───────────────────────────────┐
  │  mod-hud     │ ────────────► │  ShinobiCore                  │
  │ (client)     │               │  InventoryManager             │
  │              │ ◄──────────── │  → sac du PERSO ACTIF (JSON)   │
  │ InventoryScreen│  JSON sac    │  → persiste rpbags/<uuid>.yml │
  │              │ ────────────► │  equip / unequip / use / drop │
  └──────────────┘  actions      └───────────────────────────────┘
```

### Client — `minecraft/mod-hud` · package `fr.reborn.hud.menu.inventory`
- **`InventoryScreen`** — l'écran (rendu extractor MC 26.2). Panneau gauche =
  aperçu 3D du perso + colonne cosmétiques + **jauge de poids** ; panneau droit =
  **filtres kanji** (par catégorie) + **grille limitée par le sac**. Interaction
  façon Minecraft : **clic pour prendre / clic pour poser**, clic droit = action
  rapide (équiper/utiliser), clic dehors = jeter, Échap = annuler/fermer.
  Infobulles riches (nom teinté rareté, catégorie, poids, description).
- **`InvItem` / `ItemCategory` / `Rarity` / `CosmeticSlot`** — modèle client.
- **`InventoryData`** — snapshot volatile poussé par le serveur, **fallback mock**
  ([`MockInventory`]) pour tester **en solo** sans serveur (comme `CharacterData`).
- **`InventoryPayload`** — canal `reborn:inventory` (octets UTF-8, même forme que
  `TiragePayload`).
- Câblage : `RebornHudClient` (register S2C+C2S + receiver + clear au DISCONNECT),
  `HudKeybinds` (**touche C** = Sacoche ; en serveur envoie `open`, en solo ouvre
  le mock), clés lang fr/en.

### Serveur — `minecraft/shinobi/ShinobiCore` · package `com.reborn.shinobicore.inventory`
- **`RpItemType`** — **registre** des objets (source de vérité) : nom, desc,
  catégorie, rareté, **poids**, kanji, emplacement cosmétique, `maxStack`. 14
  objets ninja seedés (kunai, shuriken, tantō, parchemins, pilules, bandeau,
  masque ANBU, manteau Akatsuki…).
- **`RpBag`** — le sac d'un perso : nom + **capacité (cases)** + **limite de
  poids** + contenu `(typeId,count)` + cosmétiques équipés. Logique
  equip/unequip/use/drop + sérialisation JSON pour le client.
- **`InventoryManager`** — `Listener` + `PluginMessageListener` + `CommandExecutor` :
  canal `reborn:inventory`, **persistance 1 fichier/perso**
  (`plugins/ShinobiCore/rpbags/<charUuid>.yml`), sac de départ auto, actions
  autoritaires, commande **`/sacoche`** (`open` / `give <id> [n]` / `reset`).
- Câblage `ShinobiCore.onEnable` (étape 7f) + accessor `rpInventory()` +
  `flush()` à l'extinction + commande dans `plugin.yml`.

Les deux moitiés **compilent** (client : gradle build ✅ ; serveur : mvn package ✅).

---

## 2. Comment tester

### A. En solo, tout de suite (client seul, données mock)
Le plus rapide pour juger le **feel + le visuel** sans serveur :
1. Charger le jar `reborn-hud` buildé (mods du client).
2. En jeu (monde solo), presser **C** → la Sacoche s'ouvre avec un sac de
   démonstration (Sac de Chûnin, 24 cases, bandeau équipé, ~13 objets).
3. Prendre/poser des objets, filtrer par catégorie (chips kanji), glisser un
   cosmétique sur son emplacement, clic droit pour utiliser une pilule, regarder
   la **jauge de poids** bouger.

> ⚠️ Le launcher **purge** les mods hors-manifest avant lancement : pour tester
> le jar local sans publier, lancer le client en direct (java + argfile dev,
> `-Dreborn.devMenu=true`) **ou** publier le mod (cf. §5).

### B. En serveur (sac réel lié au perso)
Après déploiement de ShinobiCore (cf. §5) + restart :
- **`/sacoche`** (ou touche **C**) ouvre le sac du **perso actif**.
- **`/sacoche give kunai 5`** ajoute des objets ; **`/sacoche reset`** réinitialise.
- Changer de perso (menu `;`) puis rouvrir → **un sac différent** (preuve du lien
  perso).

---

## 3. Ce qui satisfait la demande, en détail

- **Non-vanilla visuellement** : zéro `ContainerScreen`. Cases dessinées
  (rounded rects teintés rareté, kanji, points de rareté), panneaux, chips,
  aperçu 3D — tout est peint par `InventoryScreen`.
- **Limité par le sac** : `slots` vient du sac (`RpBag`) ; la grille se
  redimensionne (6 colonnes, N lignes) et refuse d'ajouter quand le sac est plein.
- **Poids** : `RpItemType.weight` × quantité, sommé (objets + cosmétiques) ;
  jauge verte → or → rouge, texte `x.x / y.y kg`. Prêt à **coupler au mouvement**
  plus tard (léger = mobile, lourd = lent — cf. le deck d'inspiration, pilier 08).
- **Par personnage** : persistance par UUID de perso, résolue à l'ouverture.

---

## 4. Cosmétiques 3D — faisabilité (réponse détaillée)

**Oui, plusieurs modèles 3D cosmétiques sont faisables** et c'est même une base
classique en Fabric. Le prototype pose déjà la **couche donnée** (emplacements,
équip, aperçu). Reste le **rendu sur le corps**. Voies possibles, de la plus
simple à la plus riche :

| Approche | Ce que c'est | Effort | Verdict |
|---|---|---|---|
| **`RenderLayer` custom + `ModelPart`** | Une couche ajoutée au rendu du joueur (`LivingEntityFeatureRendererRegistrationCallback`), qui dessine un **box texturé** attaché à une partie du corps (tête = bandeau/masque, corps = manteau). | **Faible–Moyen** | ✅ **Reco pour démarrer.** 100% vanilla-render, aucun dépendance. Un modèle par cosmétique. |
| **GeckoLib (déjà dans le modpack)** | Modèles animés `.geo.json` attachés au joueur via un render layer GeckoLib. Tu l'utilises déjà pour les émotes/anims. | **Moyen** | ✅ pour les cosmétiques **animés/complexes** (manteau qui flotte, familier). |
| **Couche 3dSkinLayers-like** | Rendre des overlays de skin en relief. | Moyen | Complémentaire (relief cheveux/tenue). |
| **Modèle d'armure custom** | Traiter le cosmétique comme une armure à modèle custom. | Moyen | Possible mais couple à l'équipement vanilla — on veut justement s'en découpler. |

### Archi recommandée (multi-cosmétiques, vu par tous les joueurs)
1. **Serveur** : le `RpBag.equipped` (déjà là) est diffusé à tous via un canal
   type `reborn:cosmetics` (S2C broadcast, comme `reborn:skins`/`reborn:tablist`) :
   `{playerUuid → {BANDEAU:id, MASQUE:id, MANTEAU:id, DOS:id}}`.
2. **Client** : un `CosmeticState` (par joueur) reçoit ces données. Un
   **`RenderLayer<AbstractClientPlayer, PlayerModel>`** enregistré une fois, qui
   pour chaque emplacement équipé rend le modèle correspondant (registre
   client `id → (ModelPart|GeoModel, texture, attach point)`), positionné sur la
   partie de corps (tête/torse/dos) en suivant les transforms vanilla.
3. **Plusieurs cosmétiques coexistent** naturellement : la couche itère les
   emplacements ; chaque slot = un modèle indépendant → bandeau **+** masque **+**
   manteau ensemble, sans conflit (contrairement à la compo de skin 64×64 qui,
   elle, aplati tout — cf. [[character-creation-design]] Phase 2).

> **Note DA** : garder des modèles **low-poly + texture 16px** pour rester
> raccord Minecraft. Le bandeau/masque/manteau sont les 3 pièces canon à faire
> en premier (Blockbench → export `.json`/GeckoLib, pipeline déjà en place via
> [[emote-geckolib-import]] et le plugin Blockbench maison).

**Pourquoi ce n'est pas livré dans ce prototype** : le pipeline de rendu de MC
26.2 est en modèle « extractor » (API de render différente de 1.21.1) ; écrire un
`RenderLayer` à l'aveugle risquait de ne pas compiler. Il n'existe aucun
`RenderLayer`/`FeatureRenderer` existant dans mod-hud à copier (le package `skin`
fait de la **compo de texture**, pas des modèles attachés). C'est donc un
incrément propre à faire en vérifiant l'API réelle (décompiler
`LivingEntityRenderer`/`RenderLayer` 26.2), pas une rustine.

---

## 5. Déploiement — ce qui reste (et pourquoi je ne l'ai pas auto-poussé)

Les deux moitiés sont **buildées et prêtes**. Je n'ai **pas** poussé en prod
sans ton aval car les deux actions sont **outward-facing / à blast-radius** :

1. **Publier le mod client** (manifest → tous les joueurs). Le changement est
   **purement additif** (nouveau package + touche C + canal), donc sûr, mais ça
   touche le launcher de tout le monde → à faire sur ton feu vert. Une fois
   publié : lancer + presser **C** suffit pour voir la Sacoche (données mock tant
   que le serveur n'envoie rien).
2. **Déployer ShinobiCore** (SFTP dev + restart). ⚠️ **Piège** : ce worktree
   (`emote-bend-test`) **n'a pas** les fichiers serveur du *test de la feuille*
   (`LeafTestManager`/`LeafTestItem`/`TestFeuilleCommand` — ils sont en
   *uncommitted* sur la branche `feature/migrate-26.2`). Déployer le jar tel quel
   **régresserait le test de la feuille** côté serveur. Avant de déployer, il faut
   **porter ces 3 fichiers** (+ leur câblage) dans ce ShinobiCore pour avoir un
   **surensemble** (test feuille **+** sacoche). Sinon, garder le serveur actuel
   (le mock client démontre déjà tout l'UX).

Dis-moi « publie » / « déploie » et je fais les deux proprement (avec vérif
sha256 + URL 200 avant POST comme pour le tirage, et le port des fichiers
leaf-test avant le build serveur).

---

## 6. Évolutions naturelles (piste courte)
- **Poids ↔ mobilité** : au-delà d'un seuil, ralentir le shunpo/wall-run
  (le levier « le loadout devient un choix de build »).
- **Vrais effets** : `use` d'une pilule → régen chakra ; `equip` bandeau → tag
  visuel de village ; `drop` → item au sol RP.
- **Icônes PNG** par objet (hook déjà prévu : `textures/inventory/items/<id>.png`)
  quand tu veux remplacer les glyphes kanji par de l'art.
- **Cosmétiques 3D sur le corps** : §4, étape suivante.

---

## 7. Système de sacs — 9 cases de base + déblocage par tier (FAIT)

La demande : **de base 9 slots** (la barre), et l'espace se débloque **après
l'équipement d'un type de sac** — pour la cohérence RP (on ne porte pas 1000
choses sans contenant) et pour amorcer la **fouille INRP** (qui a un sac a de
quoi transporter… et à fouiller).

**Modèle** (`BagTier` serveur / `BagTiers` client — à garder synchronisés) :

| Tier | Objet (id) | Cases (base 9 +) | Poids max (base 12 +) | Total cases |
|---|---|---|---|---|
| Aucun | — | +0 | +0 | **9** |
| Sacoche | `sac_sacoche` | +9 | +8 kg | **18** |
| Bandoulière | `sac_bandouliere` | +18 | +16 kg | **27** |
| Sac ninja | `sac_dos` | +27 | +28 kg | **36** |
| Sac lourd | `sac_lourd` | +36 | +45 kg | **45** |

- **Emplacement Sac** dédié dans la Sacoche (carte en haut du panneau gauche).
  On équipe un sac par **clic droit** dessus (ou glisser sur l'emplacement) ; on
  le retire par **clic droit sur l'emplacement Sac**. Retirer est **bloqué** si le
  contenu ne rentre plus dans les 9 cases de base (anti-perte d'objets).
- **La grille sépare visuellement** la zone **« SUR SOI »** (les 9 premières
  cases, une rangée type barre vanilla) de la zone **« SAC »** (les rangées
  débloquées), avec un trait or et le nom du sac.
- Serveur autoritaire : `RpBag.slots()`/`maxWeight()` sont **dérivés** du tier
  équipé (plus de capacité fixe). Un changement de sac **repousse** un snapshot
  frais (la capacité change). En solo, MAJ optimiste via `BagTiers`.
- Persistance : le fichier `rpbags/<uuid>.yml` stocke maintenant `bagTier`
  (les anciens fichiers « slots fixes » sont migrés en **Sacoche** au chargement).
- **Test serveur** : `/sacoche bag sac_lourd` équipe un sac lourd, `/sacoche bag none`
  le retire. Le sac de départ = **Sacoche équipée** + un **Sac ninja en réserve**
  (équipe-le pour sentir l'agrandissement).
- **Amorce fouille INRP** : le sac étant un **objet physique** (tier ⇄ item Nexo,
  §8), l'étape suivante est de matérialiser le sac équipé sur le joueur et de
  permettre à un tiers d'inspecter/prendre son contenu.

---

## 8. Modèles 3D custom via Nexo — le mythe « que via la tête » est faux

**Réponse courte : sur MC moderne (ta version 26.2, très au-delà de 1.21.4), on
peut donner un modèle 3D distinct à N'IMPORTE QUEL item — pas seulement la
tête — et en nombre illimité.** Le « custom que via player_head » date de l'ère
datapack ; c'est obsolète.

**Le mécanisme vanilla :**
- Composant **`minecraft:item_model`** (arrivé en 1.21.3, refondu en 1.21.4) :
  pointe un item vers une **définition de modèle** par id (`reborn:sac_sacoche` →
  `assets/reborn/items/sac_sacoche.json`).
- `minecraft:custom_model_data` existe encore mais n'est plus qu'un **sélecteur
  conditionnel** (varier le modèle selon un état), plus le levier obligatoire.
- Chaque définition d'item du pack peut pointer vers **n'importe quel modèle** →
  un papier, un bâton, une carotte peuvent tous rendre un modèle 3D différent.

**Ce que Nexo fait par-dessus :** tu déclares les items en YAML
(`plugins/Nexo/items/*.yml`), Nexo génère le resource pack et câble le composant
pour toi. Champs clés : `itemname`, `material` (item vanilla de base), `lore`, et
`Pack.model: reborn:item/<id>`. Sur 1.21.4+, avec
`Pack.generation.prefer_item_models: true` (settings.yml), **chaque item Nexo
reçoit son propre `item_model` = `nexo:<id>`** — plus de collision de numéros
CustomModelData. **Un item Nexo = un modèle distinct. Illimité.**

**Coexistence :** chaque sac, chaque cosmétique = une définition séparée
(`nexo:sac_sacoche`, `nexo:sac_dos`, `nexo:masque_anbu`…). Aucune limite, aucun
conflit — c'est justement l'intérêt de `item_model` vs l'ancien CustomModelData.

**Nexo vs mod client :** Nexo est idéal pour tout ce qui est **item tenu /
d'inventaire** (le sac en main, l'affichage de fouille) — server-authoritative,
aucun install client. Un **mod client** (Fabric + GeckoLib / render layers) est
préférable quand le modèle doit vivre **sur le corps** indépendamment de l'item
tenu (masque/bandeau/manteau portés en permanence, cosmétiques **animés**). Les
deux se complètent : Nexo pour l'item, mod pour le porté-sur-le-corps.

**Premiers configs créés** (dans `minecraft/server-config/nexo/`) :
- `items/reborn_sacs.yml` — les 4 sacs (`sac_sacoche`, `sac_bandouliere`,
  `sac_dos`, `sac_lourd`) avec les **mêmes ids** que `BagTier` côté serveur.
- `items/reborn_cosmetiques.yml` — `masque_anbu`, `bandeau_konoha`.
- `README.md` — où poser les fichiers sur le serveur (`plugins/Nexo/items/`),
  l'arbo du pack (`plugins/Nexo/pack/assets/reborn/models|textures/item/…`),
  génération/reload (`/nexo reload items|pack`), et le lien avec ShinobiCore.

> Il reste à **dessiner** les `.json` (Blockbench) + textures et les déposer dans
> le pack Nexo. Le YAML n'est que la couche d'enregistrement ; le modèle 3D est
> l'asset à produire. Pipeline Blockbench déjà en place (cf. [[emote-geckolib-import]]).

Sources : docs.nexomc.com (items, item-appearance, itemmodels-vs-custommodeldata,
resourcepack, commands) ; minecraft.wiki (Data_component_format).
```
