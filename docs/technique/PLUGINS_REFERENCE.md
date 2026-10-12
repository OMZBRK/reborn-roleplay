# Référence des plugins serveur

> **Ce document est un annuaire**, généré à partir du code le 2026-10-12 : `plugin.yml`, `config.yml`,
> canaux réseau et fichiers de données de chaque plugin. Il répond à « où est la commande X », « quel canal
> transporte Y », « quel fichier stocke Z ».
>
> Les règles de jeu de chaque système sont dans [`gameplay/`](../gameplay/). Les commandes du staff, rangées
> par usage, sont dans [`staff/GUIDE_STAFF_EN_JEU.md`](../staff/GUIDE_STAFF_EN_JEU.md).
>
> **À tenir à jour** quand on ajoute une commande, un canal ou une section de config. Les commandes
> utilisées pour régénérer les tableaux sont en annexe (§7).

---

## 1. Les plugins et leurs dépendances

| Plugin | Dossier | Build | Dépendances (`softdepend`) | Rôle |
|---|---|---|---|---|
| **ShinobiCore** | `minecraft/shinobi/ShinobiCore` | Maven | PlaceholderAPI | Moteur : personnages, chakra, stats, XP, grades, KO, médical, mort RP, inventaire RP, emotes, Poste de garde, carte, météo, boutique |
| **ShinobiAbilities** | `minecraft/shinobi/ShinobiAbilities` | Maven | ShinobiCore, MagicSpells, ShinobiTail | Contenu Naruto : catalogue de techniques (`abilities.yml`), lancer, mobilité (Naruto run, dash, sauts), parchemins, bibliothèques, apprentissage |
| **ShinobiCombat** | `minecraft/shinobi/ShinobiCombat` | Maven | ShinobiCore | Combat M1 kenjutsu / taïjutsu, parade, endurance, HUD combat |
| **ShinobiLearning** | `minecraft/shinobi/ShinobiLearning` | Maven | ShinobiCore, ShinobiAbilities | Académie, équipes Genin, transmission de maîtrise par un sensei |
| **ShinobiSense** | `minecraft/shinobi/ShinobiSense` | Maven | ShinobiCore, ShinobiAbilities, ShinobiTail | Perception : radar de chakra, genjutsu, dōjutsu (Byakugan) |
| **ShinobiTail** | `minecraft/shinobi/ShinobiTail` | Maven | ShinobiCore | Démons à queues : rage, corruption, monde intérieur, transformation |
| **RebornGuardian** | `minecraft/plugin-guardian` | Gradle | — | Vérifie le play-token au JOIN, kick sinon |
| **RebornOst** | `minecraft/plugin-ost` | Gradle | — | Diffusion des musiques (OST), zones, late-join |

> ⚠️ **Si ShinobiCore ne se charge pas, tous les autres plugins Shinobi tombent** en
> `NoClassDefFoundError: com/reborn/shinobicore/...`. Première chose à vérifier au démarrage : la ligne
> `Enabling ShinobiCore`. Cause déjà rencontrée : un `plugin.yml` invalide (cf.
> [`operations/PUBLICATION.md`](../operations/PUBLICATION.md) §4).

Les addons ne parlent pas à la classe `ShinobiCore` : ils passent par les **services** publiés dans le
`ServicesManager` de Bukkit (`com.reborn.shinobicore.api.*`, regroupés côté Abilities dans
`CoreServices`). Services publiés : `CharacterService`, `ResourceService`, `KoService`, `SkillService`,
`HudService`, `ItemGiveService`, `MobilityService`, `ProgressionLadder`, `StatsService`, `CastAttribution`,
`TechniqueRegistry`, `XpService`. Annotation `@Stable` = point d'extension sûr, `@Internal` = réservé au
Core.

---

## 2. Commandes

Le descriptif complet (usage, alias) est dans le `plugin.yml` de chaque plugin. Colonne « Perm. » =
permission déclarée ; « — » = aucune (la commande vérifie elle-même ce qu'il faut).

### 2.1 ShinobiCore

| Commande | Alias | Pour | Perm. | Ce qu'elle fait |
|---|---|---|---|---|
| `/shinobi` | `sc`, `shinobicore` | tous / staff | `shinobicore.use` | Commande racine (`/sc help`, `/sc reload`) |
| `/character` | `char`, `characters` | tous | `shinobicore.use` | Gérer ses personnages (liste, création, sélection) |
| `/stats` | `fiche`, `statistiques` | tous / staff | `shinobicore.use` | Fiche shinobi : répartir ses points, réinitialiser, jeton ; staff `give`, `set`, `reset` |
| `/xp` | — | staff | `shinobicore.staff` | `voir`, `donner`, `retirer`, `niveau <joueur> <n>` — l'XP est invisible pour le joueur |
| `/grade` | — | tous / gradés / staff | `shinobicore.use` | Voir son grade ; promouvoir ou rétrograder selon son propre grade |
| `/meditation` | `meditate`, `med` | tous | `shinobicore.use` | S'asseoir, régénérer du chakra, avancer le repos ATA |
| `/testfeuille` | `feuille`, `leaftest` | tous | `shinobicore.use` | Test de la feuille : révèle la nature de chakra |
| `/me` | `action`, `act` | tous | `shinobicore.use` | Action RP avec le nom du personnage |
| `/rencontrer` | `meet`, `introduce` | tous | `shinobicore.use` | Se présenter à la personne regardée |
| `/amitier` | `friend`, `amis` | tous | `shinobicore.use` | Demande d'amitié |
| `/etat` | `état` | tous | `shinobicore.use` | État ressenti de son propre personnage |
| `/osculter` | `ausculter`, `auscult`, `examine` | tous | `shinobicore.osculter` | Ausculter la personne en face |
| `/soigner` | `heal`, `treat` | tous | `shinobicore.use` | Traiter les blessures (silhouette + recette) |
| `/paume` | `shosen`, `soin` | médics / patient | `shinobicore.use` | Paume de soin maintenue ; `accepter`, `stop` |
| `/aide` | `help-rp` | à terre / KO | `shinobicore.use` | Appeler à l'aide (alerte médics + staff) |
| `/hopital` | `hospital` | KO / staff | `shinobicore.use` | KO : se laisser emmener (proposé après 5 min) ; staff : `set`, `suppr`, `liste` |
| `/ata` | — | tous / staff | `shinobicore.use` | Son ATA ; staff `voir`, `lever`, `appliquer <joueur> [pleine\|allegee]` |
| `/corps` | — | tous | — | Actions sur un corps (menu d'interaction du mod) : `porter`, `gorge`, `fouiller`, `rpk`, `achever`, `executer`, et `quitter` (fin de la scène de mort) |
| `/execution` | — | tous / staff | — | Ses exécutions ; staff `donner`, `retirer`, `liste`, `recharger` |
| `/sckillvote` | — | staff | `shinobicore.staff` | Boutons du tchat RPK : `approve`, `refuse`, `goto <id>` |
| `/zonerp` | `entrainement`, `zonesrp` | staff | `shinobicore.zonerp` | Zones d'entraînement et de repos : `pos1`, `pos2`, `creer`, `suppr`, `liste`, `ici` |
| `/garde` | `postedegarde`, `panelstaff` | staff | `shinobicore.use` | Ouvre le Poste de garde (mod requis, touche F7) |
| `/infirmerie` | `ko`, `kogestion` | staff | `shinobicore.use` | Ouvre le Poste de garde sur l'onglet Infirmerie |
| `/staffchat` | `schat` | staff | `shinobicore.use` | Écrire dans le canal staff |
| `/signaler` | `report` | tous | `shinobicore.use` | Signaler un joueur au staff |
| `/staff` | `panel`, `staffpanel`, `adminpanel` | staff | `shinobicore.staff` | Ancien panneau staff en inventaire (`build` = mode construction) |
| `/vanish` | `v`, `invisible` | staff | `shinobicore.vanish` | Invisibilité staff |
| `/cinematic` | `cine`, `cinematique` | staff | `shinobicore.cinematic` | Cinématiques |
| `/dummy` | `trainingdummy`, `sandbag` | staff | `shinobicore.dummy` | Mannequins d'entraînement |
| `/assoir`, `/allonger`, `/ramper` | `sit`, `lay`, `crawl` | tous | `shinobicore.use` | Postures RP |
| `/sacoche` | `sac`, `inv` | tous / staff | `shinobicore.use` | Sacoche RP ; staff `give`, `reset` |
| `/playemote`, `/stopemote` | `emote`, `pe`, `se` | tous | `shinobicore.emote` | Jouer / arrêter une emote |
| `/creator` | — | staff | `shinobicore.creator.admin` | `reload` des assets du créateur de personnage |
| `/ryo` | — | tous / staff | — | Solde de ryo ; staff `give`, `take`, `set` |
| `/carte` | `map`, `worldmap` | tous / staff | `shinobicore.use` | Carte (touche M) ; staff `liste`, `tp`, `set`, `del`, `reload` |
| `/meteo` | `weatherfx` | staff | `shinobicore.meteo` | Météo visuelle : `pluie`, `sable`, `brume`, `clair` + intensité |

### 2.2 ShinobiAbilities

| Commande | Alias | Pour | Perm. | Ce qu'elle fait |
|---|---|---|---|---|
| `/menu` | `hub`, `shinobimenu` | tous | `shinobiabilities.use` | Menu Shinobi (techniques, liaisons, mobilité) |
| `/techniques` | `technique`, `jutsus` | tous | `shinobiabilities.use` | Techniques connues |
| `/shinobiabilities` | `sa`, `sja` | staff | `shinobiabilities.use` | Racine : `reload`, `preview`… |
| `/bibliotheque` | `biblio` | staff | `shinobiabilities.staff` | `creer <nom>`, `regler`, `tirage`, `suppr`, `liste` |
| `/parchemin` | `parchemins` | staff | `shinobiabilities.staff` | `donner`, `slots`, `seance`, `attente`, `valider`, `refuser`, `concentration` |
| `/trainingground` | `parkour`, `entrainement` | tous / staff | `shinobiabilities.use` | Parcours qui débloquent la mobilité |
| `/mobility` | `mob`, `voie` | staff | `shinobiabilities.staff` | `path <perso> <none\|one\|two>` |
| `/trail` | `piste` | staff | `shinobiabilities.staff` | Pistes de déplacement |

### 2.3 Autres plugins

| Plugin | Commande | Alias | Perm. | Ce qu'elle fait |
|---|---|---|---|---|
| ShinobiCombat | `/resetcd [joueur]` | — | `shinobicombat.resetcd` | Remet les recharges à zéro (test) |
| ShinobiCombat | `/scendurance [joueur] <n>` | — | `shinobicombat.endurance` | Débite l'endurance (pont MagicSpells) |
| ShinobiCombat | `/lookdown` | — | `shinobicombat.lookdown` | Force la caméra vers le sol |
| ShinobiLearning | `/academy` | `academie`, `ecole` | `shinobilearning.use` | Cursus de l'académie, outils d'instructeur |
| ShinobiLearning | `/squad` | `equipe`, `team` | `shinobilearning.use` | Équipe Genin, transmission sensei |
| ShinobiSense | `/sense [suppress]` | `sentir`, `perception` | `shinobisense.use` | Percevoir le chakra alentour |
| ShinobiTail | `/tail` | `biju`, `bijuu` | `shinobitail.use` | Démons à queues : statut + outils MJ |
| RebornOst | `/ost play\|playat\|playglobal\|stop` | — | `reborn.ost.broadcast` | Diffusion musicale |

---

## 3. Permissions

### 3.1 Grades du staff (Poste de garde)

`StaffGrades` lit des permissions, pas des groupes :

| Niveau | Permission | Nom affiché |
|---|---|---|
| 4 | OP ou `reborn.staff.owner` | Owner |
| 3 | `reborn.staff.admin` | Admin |
| 2 | `reborn.staff.modo` | Modérateur |
| 1 | `reborn.staff.helper` | Helper |
| 0 | — | (pas staff) |

Chaque action du Poste de garde exige un niveau minimum (ex. téléportation = Helper, valider une RPK =
Modérateur).

### 3.2 Permissions de gameplay

| Permission | Effet |
|---|---|
| `shinobicore.staff` | Statut staff général (commandes staff, messages RPK) |
| `shinobicore.admin`, `shinobiabilities.admin` | Contourne les vérifications (techniques non apprises, prérequis) |
| `shinobicore.medic` | Médic rang 1 : paume jusqu'à 60 % des PV, reçoit les `/aide` |
| `shinobicore.medic.expert` | Médic rang 2 : paume jusqu'à 100 %, lève toute ATA |
| `shinobicore.grade.staff` | Donne n'importe quel grade |
| `shinobicore.execution.<id>` | Possède l'exécution `<id>` (alternative à `/execution donner`) |
| `shinobicore.hopital`, `shinobicore.ata`, `shinobicore.zonerp` | Outils staff du KO |
| `shinobicore.stats.admin`, `shinobicore.character.admin`, `shinobicore.ryo.admin` | Outils staff des persos |
| `shinobicore.map.tp`, `shinobicore.map.admin` | Carte : téléportation, édition |
| `shinobicore.emote.others`, `shinobicore.emote.admin` | Emotes sur autrui, catalogue |
| `shinobicore.mobility.zipline`, `shinobicore.mobility.grapple` | Accès à la tyrolienne et au grappin |
| `shinobiabilities.staff`, `shinobiabilities.fuinjutsu` | Outils staff Abilities, sceaux |
| `shinobilearning.instructor` | Instructeur de l'académie |
| `shinobisense.dojutsu.any` | Tous les dōjutsu, quel que soit le clan |
| `shinobitail.gm`, `shinobitail.admin` | Maître du jeu des démons à queues |

> ⚠️ **Les OP ont toutes les permissions non déclarées** (défaut Bukkit). C'est pratique en test mais
> trompeur : un OP « a » toujours toutes les exécutions et le rang médic expert.

---

## 4. Canaux réseau mod ⇄ plugins

Tous les échanges entre le mod `reborn-hud` et les plugins passent par des **custom payloads** Minecraft.
Convention : charge utile = **JSON UTF-8 brut** (sans préfixe de longueur), sauf mention contraire.

| Canal | Sens | Côté serveur | Côté mod | Transporte |
|---|---|---|---|---|
| `reborn:auth` | C → S | `guardian…AuthChannelListener` | `integrity/AuthPayload` | Play-token au JOIN (octets bruts) |
| `reborn:character` | ⇄ | `character.select.CharacterSelectManager` | `menu/character/CharacterPayload` | Liste des persos (âge, taille, sexe, apparence), sélection, création |
| `reborn:skins` | S → C | `CharacterSelectManager` | `skin/SkinPayload` | Apparence RP de chaque joueur (`<uuid>\n<SkinSpec>`) |
| `reborn:creatorpack` | S → C | `creator.CreatorAssetManager` | `skin/CreatorPackPayload` | Assets du créateur de perso |
| `reborn:vitals` | S → C | `character.VitalsPush` | `runtime/VitalsPayload` | PV, chakra, endurance |
| `reborn:stats` | ⇄ | `stats.StatsChannel` | `menu/stats/StatsPayload` | Fiche shinobi (points, stats, Mérite, techniques) |
| `reborn:tablist` | S → C | `lore.TabListManager` | `menu/tablist/TablistPayload` | Tab list RP |
| `reborn:tirage` | ⇄ | `character.LeafTestManager` | `menu/tirage/TiragePayload` | Test de la feuille |
| `reborn:ko` | S → C | `ko.KoHudSync` | `ko/KoPayload` | État KO / ATA (binaire) |
| `reborn:ko_event` | S → C | `ko.KoHudSync` | `ko/KoEventPayload` | Cartes d'événement (À terre, K.O., Second souffle…) |
| `reborn:volonte` | ⇄ | `ko.VolonteQte` | `ko/VolontePayload` | QTE Volonté : `avail`, `start`, `result`, `ok`, `fail`, `deny` |
| `reborn:corps` | S → C | `ko.action.CorpsService`, `DeathScene` | `ko/CorpsClient` | Corps proches + statut RPK, exécutions possédées, corps au sol, scène de mort (`mort`, `fin`) |
| `reborn:staff` | ⇄ | `staff.panel.StaffPanel` | `staff/StaffPayload` | Poste de garde (alertes, joueurs, chat, journal, Infirmerie) |
| `reborn:inventory` | ⇄ | `inventory.InventoryManager` | `menu/inventory/InventoryPayload` | Sacoche RP |
| `reborn:cosmetics` | S → C | `InventoryManager` | `cosmetic/CosmeticsPayload` | Cosmétiques portés |
| `reborn:shop` | ⇄ | `shop.ShopManager` | `menu/shop/ShopPayload` | Échoppe |
| `reborn:map` | ⇄ | `map.MapChannel` | `map/MapPayload` | Carte du monde |
| `reborn:meteo` | S → C | `meteo.MeteoCommand` | `meteo/MeteoPayload` | Météo visuelle |
| `reborn:typing` | ⇄ | `chat.TypingManager` | `chat/TypingPayload` | Indicateur « en train d'écrire » |
| `reborn:emote` | ⇄ | `emote.EmoteManager` | `emote/EmotePayload` | Jouer une emote (visible par les proches) |
| `reborn:emotepack` | S → C | `EmoteManager` | `emote/EmotePackPayload` | Distribution des fichiers d'emotes au JOIN |
| `reborn:parchemin` | ⇄ | `shinobiabilities.parchemin.LibraryService`, `techniques.ConcentrationGame` | `parchemin/ParcheminPayload` | Bibliothèques, lecture, séances, concentration (`conc`) |
| `reborn:run` | ⇄ | `shinobiabilities.mobility.RunChannelListener` | `animation/NarutoRunPayload` | Course de chakra (1 octet : 1 démarrer, 0 arrêter) |
| `reborn:naruto` | C → S | `RunChannelListener` | (ancien nom) | Alias accepté pour les vieux mods |
| `reborn:anim` | ⇄ | `shinobiabilities.mobility.AnimRelayListener` | `animation/AnimSyncPayload` | Synchronisation d'animations |
| `reborn:combat`, `reborn:combatin` | ⇄ | `shinobicombat.net.CombatChannel` | `combat/CombatPayload`, `CombatInputPayload` | HUD combat, entrées M1 / parade |
| `reborn:byakugan` | S → C | `shinobisense.ByakuganVision` | `byakugan/ByakuganPayload` | Vision Byakugan |
| `reborn:ost`, `reborn:ost_request` | ⇄ | `ost.plugin.network.OstChannel` | `ost/network/*` (mod-ost) | Musiques |
| `reborn:controls` | client | — | `keybind/*` | Interne au mod |

---

## 5. Configuration

### 5.1 `ShinobiCore/config.yml` — sections

| Section | Contient | Doc |
|---|---|---|
| `mode` | `dev` / `prod` | — |
| `chakra` | régénération, affichage en barre d'XP, dépassement | [`gameplay/STATS_ET_PROGRESSION.md`](../gameplay/STATS_ET_PROGRESSION.md) |
| `stats` | `points-per-rank` (= points de **Mérite**), respec, plafond doux, PV, chakra, endurance, régén, coûts, critique, roue des natures | idem + [`SPEC_STATS_SERVICE.md`](./SPEC_STATS_SERVICE.md) |
| `progression` | `niveau-max`, `xp-base`, `xp-par-niveau`, `points-par-niveau`, `gains.*` | [`gameplay/STATS_ET_PROGRESSION.md`](../gameplay/STATS_ET_PROGRESSION.md) |
| `grades` | bonus passifs par grade, règles de promotion / rétrogradation | idem |
| `ko` | PV persistants, défaite à l'entraînement, à terre, inconscient, `volonte`, plancher, lits, ATA | [`gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) |
| `mort-rp` | `scene-min-secondes`, `corps-minutes` | idem |
| `medic` | `paume.*`, `bandage.*` | idem |
| `meditation` | régénération, rayon d'annulation | — |
| `display` | échelle des cœurs, saturation auto, HUD de recharge | — |
| `mobility` | Naruto run, sauts, dash, grappin, tyrolienne, escalade | — |
| `character-select` | point de sélection des persos | [`gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) |
| `tablist`, `lore`, `cinematic`, `optimization`, `no-character-lockdown`, `character`, `shop` | divers | — |
| `reborn`, `panel-bridge` | URL de l'API, secrets HMAC du pont panel | **ne jamais commiter de vraie valeur** |

> **Une config existante sur le serveur n'est jamais réécrite** par une nouvelle version du plugin. Une clé
> ajoutée dans le code prend sa **valeur par défaut du code** tant qu'elle n'est pas dans le fichier du
> serveur. Pour changer une valeur déjà présente, il faut éditer `plugins/ShinobiCore/config.yml` sur le
> serveur, **serveur arrêté** (sinon il est réécrit à l'arrêt).

### 5.2 `ShinobiAbilities/config.yml` — sections

| Section | Contient |
|---|---|
| `jutsu` | apprentissage requis, dépassement, coûts par stats, maîtrise, lancer rapide, incantation, MagicSpells |
| `mobility` | jetons aériens, ressenti, Naruto run, contrôle aérien, double saut, saut mural, onde au sol, dash, escalade, pistes, entraînement |
| `techniques` | répétitions des pompes, `concentration` (épreuve d'équilibre) |
| `fuinjutsu` | sceaux |
| `parchemins` | catégories, exclusions, `slots-s`, `slots-par-technique`, `seance-heures`, `perte-rare`, `maitre` |

### 5.3 Autres

- **ShinobiLearning** : `academy`, `squad`, `storage`.
- **ShinobiSense** : `sense`, `genjutsu`, `dojutsu` (dont `byakugan`).
- **ShinobiTail** : `triggers`, `rage`, `corruption`, `control-window`, `inner-world`, `fake-body`,
  `final-stage`, `aura`, `union`, `stage-power`, `messages`.

---

## 6. Fichiers de données (dossier du plugin sur le serveur)

| Fichier | Plugin | Contenu |
|---|---|---|
| `characters/` | Core | Un YAML par joueur : ses personnages (stats, niveau, XP, apparence, techniques, blessures) |
| `rpbags/` | Core | Sacoches RP |
| `creator-assets/` | Core | Catalogue du créateur de personnage |
| `emotes/` (+ `emotes.yml`) | Core | Fichiers EmoteCraft distribués + catalogue |
| `ko-state.yml`, `ko-reglages.yml` | Core | États KO en cours, réglages de l'Infirmerie |
| `ata-state.yml` | Core | ATA en cours |
| `training-zones.yml` | Core | Zones d'entraînement et de repos |
| `hospitals.yml`, `hospital-beds.yml` | Core | Points de réveil par village, lits |
| `aggression-log.txt` | Core | Qui a ouvert chaque combat hors zone |
| `kill-log.txt` | Core | Demandes, validations, refus et morts RP |
| `executions.yml` | Core | Catalogue des exécutions (créé avec 3 exemples au premier démarrage) |
| `executions-joueurs.yml` | Core | Exécutions possédées, par compte |
| `respec-tokens.yml` | Core | Jetons de réinitialisation des stats (par compte) |
| `staff-journal.yml`, `sanctions.yml`, `staff-ips.yml` | Core | Journal, sanctions, IP du staff |
| `medic-armoirs.yml` | Core | Armoires à médicaments |
| `places.yml`, `cinematics.yml`, `dummies.yml` | Core | Lieux de la carte, cinématiques, mannequins |
| `abilities.yml` | Abilities | Catalogue des techniques (données Naruto) |
| `bibliotheques.yml` | Abilities | Bibliothèques à tirage et tirage en cours |
| `parchemins-s.yml` | Abilities | Slots du rang S (`rouleau:<uuid>`, `perso:<uuid>`) |
| `parchemins-progression.yml` | Abilities | Séances faites, dates d'apprentissage, demandes S en attente |
| `shelves.yml`, `bindings.yml` | Abilities | Étagères d'apprentissage, liaisons de touches |
| `mobility-paths.yml`, `mobility-unlocks.yml`, `parkours.yml`, `trails.yml` | Abilities | Voies et déblocages de mobilité, parcours, pistes |
| `beasts.yml` | Tail | Démons à queues |

---

## 7. Annexe — régénérer ces tableaux

```bash
# Commandes et permissions déclarées
python -c "import yaml,io,sys;d=yaml.safe_load(io.open(sys.argv[1],encoding='utf-8'));[print(k,v) for k,v in d['commands'].items()]" \
  minecraft/shinobi/ShinobiCore/src/main/resources/plugin.yml
# Canaux côté serveur
grep -rhoE "\"reborn:[a-z_]+\"" --include=*.java minecraft/shinobi/*/src minecraft/plugin-*/src | sort -u
# Fichiers de données
grep -rhoE "getDataFolder\(\), \"[a-z0-9_.-]+\"" --include=*.java minecraft/shinobi | sort -u
```
