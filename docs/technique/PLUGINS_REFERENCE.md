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
`TechniqueRegistry`, `XpService`, `JournalService` (journal universel, 14/10 : `entree(cat, action).acteur(…).cible(…).detail(…).lieu(…).ecrire()`), `InventoryService` (sacoche : palier de surcharge, `weapon(p)`, `give`, verrou —
13/10) ; ShinobiCombat publie `EnduranceService` et `StunService` (étourdi). Annotation `@Stable` = point d'extension sûr, `@Internal` = réservé au
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
| `/corps` | — | tous | — | Actions du menu d'interaction du mod sur un corps (KO, mort) ou une personne **en douleur** : `porter` (sans accord en douleur), `gorge`, `fouiller`, `rpk`, `executer <joueur> [id\|sobre]` (douleur → K.O. ; KO + RPK → mort RP ; canalisation), `achever` (= exécuter sans animation), `annuler` (touche F relâchée : renoncer à l'exécution en cours), et `quitter` (fin de la scène de mort) — [`KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §6, §8 |
| `/execution` | — | tous / staff | — | Ses exécutions ; staff `donner`, `retirer`, `liste`, `recharger`, `forcer <joueur>` (K.O. inconscient), `douleur <joueur>` (PV au seuil de douleur), `annuler <joueur>` (arrête une exécution en cours) |
| `/sckillvote` | — | staff | `shinobicore.staff` | Boutons du tchat RPK : `approve`, `refuse`, `goto <id>` |
| `/zonerp` | `entrainement`, `zonesrp` | staff | `shinobicore.zonerp` | Zones d'entraînement et de repos : `pos1`, `pos2`, `creer`, `suppr`, `liste`, `ici` |
| `/duel` | `defi` | tous | `shinobicore.use` | Duel consenti n'importe où, sans séquelles : `proposer <joueur>`, `accepter [joueur]`, `refuser [joueur]`, `abandonner` (menu d'interaction et boutons du mod) — [`KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §4.1 |
| `/pid` | `playerid`, `numero` | staff | `reborn.staff.helper` | **Player ID** : `/pid <joueur\|nom RP\|#N°>` affiche le N°, le nom RP, le compte propriétaire, l'état (vivant, mort RP, supprimé ; KO / ATA si en ligne), en ligne ou non et les autres N° du même compte. Hors ligne avec `#N°` (lu dans l'index). Sans argument : son propre personnage |
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
| `/journal` | `logs` | Helper | `reborn.staff.helper` | **Journal universel** (14/10) : `/journal [#N°] [catégorie…] [j:<pseudo\|nom RP>] [h:<heures>\|d:<jours>] [p:<page>] [texte]`, 12 lignes par page, lieu au survol ; au-delà de l'index mémoire, relit les fichiers |
| `/note` | `notes`, `tag` | Helper | `reborn.staff.helper` | Notes staff par N° : `/note <cible> [ajouter [etiquette:<mot>] <texte> \| suppr <id>]` ; sans sous-commande = liste (✕ cliquable si on peut supprimer : auteur ou Admin) |
| `/annonce` | `broadcast`, `bc` | Modérateur | `reborn.staff.helper` | Annonce à tous (bandeau du mod, sinon titre) ; `/annonce joueur <cible> <message>` = une seule personne |
| `/annoncejoueur` | `broadcastplayer`, `bcp` | Helper | `reborn.staff.helper` | Message à un seul joueur (scène, convocation) |
| `/aller` | `goto`, `stp` | Helper | `reborn.staff.helper` | Se téléporter à la cible (position de départ mémorisée) |
| `/ramener` | `bring` | Modérateur | `reborn.staff.helper` | Ramener la cible à soi (sa position est mémorisée) |
| `/retour` | `back` | Helper / Modérateur | `reborn.staff.helper` | Sans argument : revenir où l'on était ; `/retour <cible>` (Modérateur) : la renvoyer où elle était |
| `/geler`, `/degeler` | `freeze`, `unfreeze` | Modérateur | `reborn.staff.helper` | Gel **persistant** (`gels.yml`) : ni déplacement (la chute reste), ni coup, ni interaction, ni lâcher, ni bloc ; tchat et commandes ouverts ; titre rappelé à la reconnexion. `/degeler` marche hors ligne (`#N°`) |
| `/voirsac` | `invsee`, `voirinv` | Modérateur | `reborn.staff.helper` | Coffre en lecture : barre, sac porté, équipement, fiche (ryo, poids). **Admin** : clic = prendre l'objet (il arrive dans sa sacoche, journalisé) |
| `/noir` | `blind`, `ecrannoir` | Modérateur | `reborn.staff.helper` | Écran noir : `/noir <cible> [secondes] [message]`, `/noir <cible> off` (ou retaper sans argument) ; `tous` = Admin (joueurs non staff). Persiste à la reconnexion |
| `/seisme` | `earthquake` | Admin | `reborn.staff.helper` | `/seisme [rayon\|tous] [force 1-10] [secondes] [message]` : secousse + grondement ; `tous` = tout le serveur + annonce « La terre tremble… » |
| `/tenue` | `forceclothes` | Modérateur | `reborn.staff.helper` | `/tenue <cible> <id Nexo\|main\|retirer>` : impose l'emplacement cosmétique `TENUE` (verrouillé), rend l'ancienne pièce à la levée. **TODO** : l'apparence de skin de l'échoppe ne peut pas être imposée |
| `/emoteforcee` | `forceemote` | Modérateur | `reborn.staff.helper` | Fait jouer une emote à la cible (`stop` pour arrêter) ; même catalogue que `/playemote` |
| `/persoreset` | `resetcharacter` | Admin | `reborn.staff.helper` | Remise à zéro : XP, niveau, stats, techniques, grade (Genin), ryo, sacoche, inventaire, blessures, apparence. Gardés : N°, nom, clan, village, âge, taille, sexe, natures. Confirmation (`confirmer` sous 60 s) + sauvegarde `sauvegardes/perso/…` |
| `/persosuppr` | `deletecharacter` | Admin | `reborn.staff.helper` | Supprime le personnage (slot libéré, N° réservé) ; joueur en ligne déconnecté. Confirmation + sauvegarde |
| `/modestaff` | `staffmode`, `sm` | Helper | `reborn.staff.helper` | Mode staff : invisible aux joueurs et au staff hors mode staff, vol, invulnérable, **aucune mécanique RP** et personne ne peut le cibler (§4 bis.7), tchat redirigé vers le chat staff, plaques au-dessus des têtes (`reborn:plaques`) ; tombe à la déconnexion. `/modestaff anim` = `/modeanim` |
| `/entreprise` | `entreprises` | Modérateur (ou `shinobicore.entreprise`) ; joueur : les siennes | `shinobicore.use` | **Entreprises** (14/10) : `creer <restaurant\|forge\|echoppe> "<nom>" [responsable]`, `supprimer <e> confirmer`, `renommer <e> "<nom>"`, `membre ajouter\|retirer\|role <e> <cible> [gerant\|employe]`, `poste ajouter <e> [libellé]` / `poste retirer` (en visant le bloc ou le meuble), `info`, `liste` ; journal Staff `ENTREPRISE_*` — [`../gameplay/ENTREPRISES.md`](../gameplay/ENTREPRISES.md) |
| `/tendre` | `donner` | Joueur | `shinobicore.use` | **Tendre** (14/10) : `/tendre <joueur>` (objet en main), `/tendre <joueur> ryo <montant>`, `/tendre prendre\|refuser` (touches F / Retour du mod) ; journal `DON` — [`../gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) §5 |
| `/nourriture` | `satiete` | Joueur : son état ; Modérateur (ou `shinobicore.nourriture.staff`) | `shinobicore.use` | `etat [cible]`, `satiete <cible> <0-100>`, `donner <cible> <plat:id\|panier:id\|ration\|bol> [n] [froid]`, `effet <cible> <type> <intensité> <secondes>`, `retirer <cible> <type>` ; journal Staff |
| `/modeanim` | `modeanimation` | Animateur / MJ | `reborn.staff.animation` | **Mode animation** (14/10) : incarner un personnage pour un event — visible, mécaniques RP permises, **tout journalisé** (entrée / sortie, commandes, tchat, coups) ; exclusif avec le mode staff ; tombe à la déconnexion (§4 bis.7) |

### 2.1 bis Syntaxe `#N°` (Player ID) dans les commandes staff

Partout où une commande staff attend un joueur, on peut écrire le **N° du personnage** précédé d'un dièse :
`#00042` ou `#42` (zéros facultatifs, dièse obligatoire pour ne pas confondre avec un pseudo numérique).
Résolution commune : `character/PlayerIds` (`online(sender, arg)` = le joueur qui joue **ce** personnage ;
`character(sender, arg)` = le personnage, même hors ligne ; `ownerByRef` = le compte).

- **ShinobiCore** : `/pid`, `/grade` (`voir` et promotion), `/stats` (`give|set|reset|jeton` — hors ligne pour
  le personnage), `/fiche edit|service|json`, `/xp`, `/ata voir|lever|appliquer`, `/execution liste|donner|retirer`,
  `/character create|edit|list <joueur>`, `/ryo give|take|set`, `/carte donner`, `/meteo … <joueur>`,
  `/cinematic … <joueur>`, `/playemote <emote> <joueur>`, `/testfeuille give`, `/vanish <cible>`.
- **ShinobiAbilities** : `/sa` (cibles joueur), `/sa mobility path <#N°> …`, `/parchemin donner|seance`.
- **Commandes vanilla** `/tp`, `/teleport`, `/spectate`, `/kick`, `/gamemode`, `/clear`, `/give`, `/effect`, `/kill` :
  le `#N°` est remplacé par le pseudo avant exécution (`character/PlayerIdCommandAlias`, joueur en ligne seulement).
- **Réservé au staff** : la console, `reborn.staff.*`, `shinobicore.staff` ou op. Pour un joueur, `#N°` ne
  résout rien (sinon il pourrait sonder les N° pour retrouver des noms — le système « rencontrer » garde les
  noms secrets). Le N° n'est montré qu'au joueur lui-même (fiche) et au staff.

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

Chaque action du Poste de garde exige un niveau minimum (ex. téléportation = Helper, valider un RPK =
Modérateur).

### 3.2 Permissions de gameplay

| Permission | Effet |
|---|---|
| `shinobicore.staff` | Statut staff général (commandes staff, messages RPK) |
| `reborn.staff.animation` | Mode animation `/modeanim` (animateur, MJ ; OP par défaut ; pas besoin d'un grade staff) |
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
| `reborn:vitals` | S → C | `character.VitalsPush` | `runtime/VitalsPayload` | 4 entiers bruts (PV, PV max, chakra, chakra max) ; **+ 4 entiers** pour un mod qui déclare `reborn:nourriture` (14/10) : satiété ‰, satiété à venir ‰ (repas en cours), pause de la régénération (s), drapeaux (1 régénère, 2 affamé, 4 FAIM, 8 ATA, 16 satiété figée) — un ancien mod lit 4 entiers exactement, il ne reçoit donc jamais les champs ajoutés |
| `reborn:nourriture` | S → C | `nourriture.NourritureCanal` | `nourriture/NourritureClient` | Préparer, manger, tendre, plat en main (§4 ter) |
| `reborn:modifs` | S → C | `modificateur.ModificateurRegistry` | `nourriture/NourritureClient` | Effets actifs visibles (modificateurs) : `{"mods":[{"id","nom","ic","b","val","reste","duree","pause"}]}` (§4 ter) |
| `reborn:stats` | ⇄ | `stats.StatsChannel` | `menu/stats/StatsPayload` | Fiche shinobi (points, stats, Mérite, techniques ; `pid` = N° du personnage, envoyé au seul joueur) |
| `reborn:tablist` | S → C | `lore.TabListManager` | `menu/tablist/TablistPayload` | Tab list RP |
| `reborn:tirage` | ⇄ | `character.LeafTestManager` | `menu/tirage/TiragePayload` | Test de la feuille : S2C `confirm\|<clan>`, `<NATURE>\|<clan>\|<AUTRES>` (AUTRES = natures déjà éveillées, virgules ; 14/10) ou `deny` ; C2S `go` |
| `reborn:ko` | S → C | `ko.KoHudSync` | `ko/KoPayload` | État KO / ATA (binaire) |
| `reborn:ko_event` | S → C | `ko.KoHudSync` | `ko/KoEventPayload` | Cartes d'événement (À terre, K.O., Second souffle…) |
| `reborn:volonte` | ⇄ | `ko.VolonteQte` | `ko/VolontePayload` | QTE Volonté : `avail`, `start`, `result`, `ok`, `fail`, `deny` |
| `reborn:duel` | S → C | `duel.DuelService` | `duel/DuelClient` | Duel consenti : `invit` / `invit_fin`, `attente` / `attente_fin`, `debut` (`adv`, `advId`, `cr`, `duree`, `tech`), `etat` (`d`, `max`, `reste`, chaque seconde), `fin` (`issue` victoire / defaite / nul / annule, `adv`, `raison`) ; réponses par commandes `/duel` ; sans mod : tchat cliquable et titres |
| `reborn:corps` | S → C | `ko.action.CorpsService`, `DeathScene` | `ko/CorpsClient` | Corps proches + statut RPK (`b`), personnes en douleur (`dl`, 14/10), exécutions possédées (`ex`), corps au sol (`cp`), son propre état (`me` : douleur, porté, jauge de lutte, exécution en cours), scène de mort (`mort`, `fin`) |
| `reborn:staff` | ⇄ | `staff.panel.StaffPanel` | `staff/StaffPayload` | Poste de garde (alertes, joueurs, chat, journal, Infirmerie ; `pid` dans chaque joueur / alerte / fiche) |
| `reborn:inventory` | ⇄ | `inventory.InventoryManager` | `menu/inventory/InventoryPayload` | Sacoche RP. C2S : `open`, `swap:<A>:<B>`, `bag:equip:<ref>` / `bag:unequip`, `cos:equip:<ref>:<SLOT>` / `cos:unequip:<SLOT>`, `arme:equip:<ref>` / `arme:unequip` / `arme:basculer` (dégainer / rengainer), `drop:<ref>`, `del:<ref>`, `use:<ref>`, `pick:<uuid de l'objet au sol>` (ramassage). S2C : JSON (`ouvrir`, capacité, poids, `palier`, `poidsMax`, `lecture`, `armeDegainee`, `hotbar`, `bag`, `equipped` dont `ARME` ; chaque objet porte `rarity`, `arme`, `unique`) — `ouvrir:false` = rafraîchir sans ouvrir |
| `reborn:cosmetics` | S → C | `InventoryManager` | `cosmetic/CosmeticsPayload` | Cosmétiques portés, dont l'arme à l'étui (`ARME`, ancrée au bassin) |
| `reborn:notif` | S → C | `inventory.Notifs` | `notif/NotifClient` | Canal générique (13/10) : `gain` / `perte` (`q`, `nom`, `rar`, `mat`, `model`, `suite`), `msg` (`ton` refus / info / palier / etat, `kj`, `gras`, `texte`, `ic` = picto d'effet au lieu d'un kanji, 14/10) → pile en bas à droite ; `poids` (`cur`, `max`, `palier` 0-3, `libres`, `verrou`) → indicateur de surcharge, sprint coupé, invite de ramassage ; `arme` (`degainee`) |
| `reborn:shop` | ⇄ | `shop.ShopManager` | `menu/shop/ShopPayload` | Échoppe ; état + `vivres` (14/10 : onglet Vivres, filtré : paniers pour les cuisiniers), C2S `buyvivre:<id>` |
| `reborn:map` | ⇄ | `map.MapChannel` | `map/MapPayload` | Carte du monde |
| `reborn:meteo` | S → C | `meteo.MeteoCommand` | `meteo/MeteoPayload` | Météo visuelle |
| `reborn:typing` | ⇄ | `chat.TypingManager` | `chat/TypingPayload` | Indicateur « en train d'écrire » |
| `reborn:emote` | ⇄ | `emote.EmoteManager` | `emote/EmotePayload` | Jouer une emote (visible par les proches) |
| `reborn:emotepack` | S → C | `EmoteManager` | `emote/EmotePackPayload` | Distribution des fichiers d'emotes au JOIN |
| `reborn:parchemin` | ⇄ | `shinobiabilities.parchemin.LibraryService`, `techniques.ConcentrationGame` | `parchemin/ParcheminPayload` | Bibliothèques, lecture, séances, concentration (`conc`) |
| `reborn:run` | ⇄ | `shinobiabilities.mobility.RunChannelListener` | `animation/NarutoRunPayload` | Course de chakra (1 octet : 1 démarrer, 0 arrêter) |
| `reborn:naruto` | C → S | `RunChannelListener` | (ancien nom) | Alias accepté pour les vieux mods |
| `reborn:allure` | ⇄ | `shinobiabilities.mobility.Allure` | `allure/AllurePayload` | Trois allures (14/10, octets bruts). C2S : `2` sprint / `0` arrêt + n° de demande. S2C `0` réglages (actif, marche, trot, sprint) ; S2C `1` état (allure accordée, endurance 0-1, drapeaux neutre / essoufflé, n° de demande) — [`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md) |
| `reborn:anim` | ⇄ | `shinobiabilities.mobility.AnimRelayListener` | `animation/AnimSyncPayload` | Synchronisation d'animations |
| `reborn:combat`, `reborn:combatin` | ⇄ | `shinobicombat.net.CombatChannel` | `combat/CombatPayload`, `CombatInputPayload` | HUD combat, entrées M1 / parade |
| `reborn:byakugan` | S → C | `shinobisense.ByakuganVision` | `byakugan/ByakuganPayload` | Vision Byakugan |
| `reborn:ost`, `reborn:ost_request` | ⇄ | `ost.plugin.network.OstChannel` | `ost/network/*` (mod-ost) | Musiques |
| `reborn:controls` | client | — | `keybind/*` | Interne au mod |
| `reborn:annonce` | S → C | `staff.outils.EffetsStaff` | `staff/EffetsStaff` | Annonce globale ou personnelle (§4 bis.3) |
| `reborn:ecran` | S → C | `staff.outils.EffetsStaff` | `staff/EffetsStaff` (+ `mixin/CameraSeismeMixin`) | Écran noir forcé, séisme (§4 bis.4) |
| `reborn:plaques` | S → C | `staff.outils.ModeStaff` | `staff/PlaquesStaff` | Plaques staff au-dessus des têtes, en mode staff (§4 bis.5) |

---

## 4 ter. Nourriture et modificateurs — protocole (14/10)

> ShinobiCore `nourriture/` et `modificateur/` ⇄ mod `fr.reborn.hud.nourriture`. JSON UTF-8 brut, champs optionnels,
> inconnus ignorés des deux côtés. Fiches : [`../gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) §13,
> [`ETATS_JOUEUR.md`](./ETATS_JOUEUR.md) §4.4.

**`reborn:nourriture`** (S → C) :

| `t` | Champs | Affichage |
|---|---|---|
| `prep` / `prep_fin` | `nom`, `ic`, `dur` (s), `fait` (s) / `ok` | Ligne + filet au-dessus de la barre d'action (« Ramen shōyu — au fourneau · 9 s ») |
| `repas` / `repas_fin` | `nom`, `ic`, `dur`, `fait` (s mangées), `vit` (0 = trot : pas de progrès, 1, 2 = assis), `pause` (s figées), `froid` / `ok` | Idem ; gris pendant une pause (« Repas en pause — reprend dans 28 s ») ; le mod extrapole entre deux paquets |
| `offre` / `offre_fin` | `de` (nom tel que je le connais), `quoi`, `etat` (chaud / froid), `mat`, `model` (icône de l'objet) ou `ic` (`ryo`), `s` | « Teuchi te tend un Ramen shōyu — chaud » / « F prendre · Retour refuser · 10 s » |
| `attente` / `attente_fin` | `a`, `quoi`, `s` | « Tu tends … à … — 8 s » (gris) |
| `main` | `nom`, `ic`, `chaud` (s restantes, 0 = froid) ; sans `nom` = plus de plat en main | « chaud · 7 min » ou « Froid — effet réduit de moitié » sous les vitals |

Déclarer `reborn:nourriture` (le mod enregistre un récepteur) vaut « je lis les vitals étendus ». Réponses : commandes
`/tendre prendre|refuser` ; préparer et manger passent par le clic droit (`PlayerInteractEvent`, `PlayerInteractEntityEvent`
pour les meubles Nexo).

**`reborn:modifs`** (S → C) : à chaque changement et toutes les `modificateurs.sync-secondes` (10) :
`{"mods":[{"id":"regeneration","nom":"Régénération","ic":"regeneration","b":1,"val":"×2","reste":287,"duree":300,"pause":0}]}`
— types `visible: SOI` seulement ; `ic` = fichier `textures/gui/effets/<ic>.png` du mod ; `pause` = régénération
figée par un coup (icône grise). Le mod décompte seul.

**Service** `api/ModificateurService` (ServicesManager, `@Stable`) : `multiplicateur(compte, effet)`,
`raison(compte, capacité)` / `refuse`, `poser(joueur, type, intensité, duréeMs, source)`, `retirer`, `actifs` ;
raccourci statique `ModificateurService.refusDe(joueur, "SPRINTER")`. Effets lus aujourd'hui : `regen` (satiété),
`satiete_perte` (satiété), `endurance_regen` (ShinobiCombat), `meditation` (méditation) ; capacités : `SPRINTER`
(allure), `COURSE_NINJA` (course de chakra). Contributeurs dérivés : FAIM (satiété 0 %), repas en cours (pas de sprint).

**Journal** : `OBJET` `PREPARATION`, `REPAS`, `ACHAT`, `PILULE`, `DON` (objet) ; `ECONOMIE` `DON` (ryo, plus les lignes
`GAIN` / `DEPENSE` du journal des ryo avec le motif « don à … ») ; `STAFF` `ENTREPRISE_*`, `SATIETE`, `DONNER`,
`MODIF_POSE`, `MODIF_RETRAIT` ; `JOUEUR` `MODIF_POSE` / `MODIF_RETRAIT` / `MODIF_FIN` pour les types `journal: true`.

---

## 4 bis. Journal universel, outils staff et effets — protocole (14/10)

> Contrat entre ShinobiCore (`journal/`, `staff/outils/`, `staff/panel/StaffPanel`) et le Poste de garde du mod
> (`staff/*Screen`). Décision du 14/10 : **tout est journalisé, quel que soit le grade** (du joueur à l'Owner).
> Toutes les charges utiles sont du **JSON UTF-8 brut**. Les champs inconnus sont ignorés des deux côtés : on peut
> ajouter un champ sans casser l'autre. **Tout est revérifié côté serveur** (grade, cible) ; le client ne fait
> qu'afficher et demander.

### 4 bis.1 Notions communes

**Personne** (objet `acteur` / `cible` d'une entrée de journal, et des réponses) :

```json
{"uuid":"<UUID du compte>","pseudo":"Omar","pid":"00042","perso":"Itachi Uchiha","grade":3,"gradeName":"Admin"}
```

`pid` = N° du personnage **au moment de l'action** (vide si aucun personnage), `grade` = grade staff 0-4 (0 joueur,
1 Helper, 2 Modérateur, 3 Admin, 4 Owner). La console est `{"pseudo":"CONSOLE","grade":4,"gradeName":"Console"}`.

**Cible** dans les requêtes : `"t":"<UUID du compte>"` (comme avant) **ou** `"pid":"00042"` (avec ou sans `#`, zéros
facultatifs). Si les deux sont présents, `t` gagne.

**Catégories** du journal (`cat`), avec leur libellé (renvoyé dans `cats` à chaque réponse `journal`) :

| `cat` | Libellé | Ce qu'on y trouve |
|---|---|---|
| `JOUEUR` | Joueur | connexion, déconnexion (durée), choix / création / suppression / changement de personnage, changement de nom RP |
| `INVENTAIRE` | Inventaire | ramassé, lâché, rangé, équipé / retiré (sac, cosmétique, arme), utilisé, détruit, disparu au sol |
| `OBJET` | Objet | obtenu (toute source), donné ou retiré par le staff (`/sacoche give`, `/give`, prise via `/voirsac`) |
| `COMBAT` | Combat | dégâts donnés / reçus entre joueurs, agression hors zone, kill (mort vanilla), garde brisée |
| `KO` | KO / mort | à terre, inconscient, réveil, relevé, hôpital, ATA, demande / validation / refus de RPK, exécution, mort RP |
| `DUEL` | Duel | proposition, début, fin (issue, raison) |
| `ECONOMIE` | Économie | ryo gagnés, dépensés, donnés / retirés / fixés par le staff, achats à l'échoppe |
| `INTERACTION` | Interaction | menu d'interaction (porter, gorge, fouiller, achever…), soins, rencontrer, échange, ouverture de l'inventaire d'autrui |
| `BIBLIOTHEQUE` | Bibliothèque | dépôts, prises, vols de parchemins, séances |
| `STAFF` | Staff | toute action du Poste de garde et des commandes staff (tp, gel, noir, séisme, annonce, notes, reset…) |
| `COMMANDE` | Commande | **chaque commande tapée par n'importe qui** (joueur ou staff, vanilla comprise) et par la console |
| `CHAT` | Tchat | messages du tchat (`journal.chat`), chat staff |
| `SYSTEME` | Système | démarrage / arrêt, rechargements, suppression / remise à zéro de personnage, sauvegardes, rotation des journaux |

**Actions émises** (champ `action`, majuscules ; liste indicative, de nouvelles actions peuvent apparaître — l'UI
affiche l'action telle quelle si elle ne la connaît pas) :

- `JOUEUR` : `CONNEXION`, `DECONNEXION` (durée, « déconnecté en KO / en duel »), `EXPULSE`, `CHOIX_PERSO`,
  `CREATION_PERSO`, `SUPPRESSION_PERSO`, `MODE_DE_JEU`, `TELEPORTATION` (par commande), `NIVEAU` (et `XP` si
  `journal.xp`).
- `INVENTAIRE` : `RAMASSE`, `LACHE`, `DETRUIT`, `RANGE`, `ARME_ETUI`, `DISPARU`, `EQUIPEMENT` (détail = message de la
  sacoche, ex. `cos:equip:B3:TENUE`), `UTILISE`. `OBJET` : `OBTENU` (toute source), `PRISE_STAFF` (`/voirsac`).
- `COMBAT` : `DEGATS` (détail : PV, PV restants, arme ou projectile, cause, « duel » / « entraînement »),
  `AGRESSION` (premier coup hors terrain d'entraînement), `MORT` (mort vanilla).
- `KO` : une action par transition de `KoJournal` (`DOULEUR`, `AGONIE_…`, `INCONSCIENT`, `REVEIL`, `SECOND_SOUFFLE`,
  `PORTE`, `DEPOSE`, `FOUILLE`, `PRISE_A_LA_GORGE`, `EXECUTION…` — le libellé du KO en majuscules), `RPK_DEMANDE`,
  `RPK_VALIDE`, `RPK_REFUSE`, `RPK_EXPIRE`, `RPK_MORT`. Les actions staff sur le KO (réveil forcé, ATA levée) vont en
  `STAFF`.
- `DUEL` : `DEBUT`, `FIN` (et les autres étapes de `DuelService`). `ECONOMIE` : `GAIN`, `DEPENSE` (détail : montant,
  solde avant → après, motif : achat tenue / carte, `staff /ryo give|take|set`, remise à zéro, « autre »).
- `BIBLIOTHEQUE` : `PRISE`, `DEPOT`, `VOL` (cible = nom de la bibliothèque), `PARCHEMINS_…` (alertes staff).
- `STAFF` : `COMMANDE` (toute commande d'un staff, de la console `CONSOLE`, du RCON), `PANEL_WEB`, `TP_VERS`,
  `RAMENE`, `RETOUR`, `RENVOYE`, `GEL`, `DEGEL`, `GEL_RECONNEXION`, `VOIR_SAC`, `ECRAN_NOIR`, `ECRAN_NOIR_FIN`,
  `SEISME`, `ANNONCE_GLOBALE`, `ANNONCE_PERSO`, `TENUE_IMPOSEE`, `TENUE_LEVEE`, `EMOTE_FORCEE`, `NOTE_AJOUT`,
  `NOTE_SUPPRESSION`, `PERSO_RESET(_DEMANDE)`, `PERSO_SUPPRIME` / `PERSO_SUPPR_DEMANDE`, `MODE_STAFF`,
  `JOURNAL_CONSULTE`, `INVENTAIRE_AUTRUI`, et les entrées du Poste de garde (`<KIND>_<TYPE>`, ex. `SANCTION_BAN_1J`,
  `KO_REVEIL_FORCE`, `GRADE_PROMOTION`, `MONDE_ZONE_CREEE`).
- `COMMANDE` / `INTERACTION` / `CHAT` : `COMMANDE` (commande d'un joueur ; les commandes RP — `/corps`, `/soigner`,
  `/duel`, `/rencontrer`… — vont en `INTERACTION`, `/me`, `/msg`… en `CHAT`), `MESSAGE` (tchat), `CHAT_STAFF`,
  `COFFRE`, `INVENTAIRE_AUTRUI`. `SYSTEME` : `DEMARRAGE`, `ARRET`, `ALERTE_STAFF`, `PERSO_RESET`, `PERSO_SUPPRIME`.

### 4 bis.2 Canal `reborn:staff` — ajouts (C → S, champ `a`)

| `a` | Grade min | Champs | Réponse |
|---|---|---|---|
| `journal` | Helper | `req` (entier, renvoyé tel quel), `cat` (tableau de catégories, vide = toutes), `pid`, `joueur` (pseudo ou nom RP, sous-chaîne, acteur **ou** cible), `du` / `au` (epoch ms), `q` (texte libre dans action + détail), `page` (0…), `par` (taille de page, défaut 50, max 200) | `journal` |
| `notes` | Helper | `pid` (ou `t`) | `notes` |
| `note_add` | Helper | `pid` (ou `t`), `texte` (≤ 500 car.), `etiquette` (facultatif, ≤ 24 car. : « surveillé », « event », « avertissement »…) | `notes` + `toast` |
| `note_del` | auteur, ou Admin | `pid` (ou `t`), `id` | `notes` + `toast` |
| `act` | selon `op` | `t` **ou** `pid`, `op`, et selon `op` : `m` (message), `s` (secondes), `v` (valeur), `r` (rayon) | `toast` + `profile` |
| `annonce` | Modérateur | `m` (message), `titre` (facultatif), `s` (durée d'affichage, défaut 8) | `toast` |
| `seisme` | Admin | `r` (rayon en blocs autour du staff ; 0 = tout le serveur), `f` (force 1-10, défaut 5), `s` (durée, défaut 4), `m` (message facultatif) | `toast` |
| `modestaff` | Helper | — (bascule) | `toast` + `snap` (`modeStaff`) + `mode` |
| `modeanim` | `reborn.staff.animation` | — (bascule, 14/10) | `toast` + `snap` (`modeAnim`) + `mode` |
| `cherche` | Helper | `q` (≥ 2 caractères : N° avec ou sans `#`, nom RP, pseudo ; mots séparés = tous requis), `req` | `cherche` (recherche universelle du Poste de garde v3, **hors ligne compris**) |

Champ facultatif de `act` (14/10) : `alerte` = id de l'alerte d'où vient l'action (« Y aller », « Observer », « Répondre ») ;
le serveur la range dans les **alertes traitées** (et la retire des alertes en attente). `kill` accepte `note` (raison de la
validation, déjà lue par le serveur).

**`op` de `act`** (les anciens restent : `tp`, `bring`, `freeze`, `watch`, `revive`, `lift_ata`) :

| `op` | Grade min | Effet |
|---|---|---|
| `tp` | Helper | aller au joueur (la position de départ du staff est mémorisée pour `back`) |
| `back` | Helper | le staff revient où il était avant son dernier tp / ramener / observer |
| `bring` | Modérateur | ramener la cible au staff (sa position de départ est mémorisée pour `return`) |
| `return` | Modérateur | renvoyer la cible où elle était avant d'être ramenée |
| `freeze` / `unfreeze` | Modérateur | `freeze` **bascule** (comme avant) : gèle, ou dégèle un joueur déjà gelé ; `unfreeze` dégèle (hors ligne compris). Gel **persistant** (survit à la déconnexion et au redémarrage) ; `m` = motif |
| `invsee` | Modérateur (lecture) | ouvre côté serveur un coffre en lecture : barre, sacoche, sac porté, équipement. Prendre un objet = Admin |
| `blind` | Modérateur | écran noir : bascule ; `s` = durée (0 = jusqu'à `blind` de nouveau), `m` = texte affiché sur le noir |
| `shake` | Admin | séisme sur la cible seule ; `s`, `v` = force 1-10 |
| `announce` | Helper | annonce personnelle à la cible ; `m`, `s` |
| `emote` | Modérateur | forcer une emote ; `v` = id de l'emote, `stop` pour arrêter |
| `outfit` | Modérateur | forcer une tenue (emplacement cosmétique `TENUE`) ; `v` = id Nexo de la tenue, `main` = l'objet que le staff a en main, `retirer` pour rendre la sienne |

**Réponses S → C** (champ `t`) :

`journal` :

```json
{"t":"journal","req":7,"total":312,"page":0,"pages":7,"source":"memoire",
 "cats":[{"id":"COMBAT","label":"Combat"}],
 "entries":[{"id":"lq3k9a-12","at":1760468592000,"date":"14/10 21:03:12","cat":"COMBAT","action":"DEGATS",
   "acteur":{"uuid":"…","pseudo":"Omar","pid":"00042","perso":"Itachi Uchiha","grade":0,"gradeName":"Joueur"},
   "cible":{"uuid":"…","pseudo":"Bob","pid":"00017","perso":"Kakashi","grade":0,"gradeName":"Joueur"},
   "detail":"6,5 PV · kunai","lieu":"world 12 64 -30"}]}
```

Ordre : **du plus récent au plus ancien**. `source` = `memoire` (index des N dernières entrées, réponse
immédiate) ou `disque` (la période demandée remonte avant l'index : le serveur relit les fichiers journaliers en
tâche de fond, puis répond ; dans ce cas `acteur` / `cible` peuvent n'avoir que `pseudo`, `pid` et `perso`).
`cible` peut être `null`. `lieu` peut être vide.

`notes` :

```json
{"t":"notes","pid":"00042","perso":"Itachi Uchiha","pseudo":"Omar",
 "entries":[{"id":"n1a2b","at":1760468592000,"date":"14/10 21:03","auteur":"Patoche","auteurPid":"","auteurGrade":2,
   "etiquette":"avertissement","texte":"Averti pour HRP le 14/10.","suppr":true}]}
```

`suppr` = le spectateur a le droit de supprimer cette note (auteur ou Admin+). Les notes sont rattachées au **N°**
(elles survivent à un changement de nom RP ou de pseudo).

**Champs ajoutés aux réponses existantes** :
- `snap` : `modeStaff` (bool, le spectateur est en mode staff), `journalCats` (liste `{id,label}`) ; 14/10 : `modeAnim`
  (bool), `animPossible` (bool, permission `reborn.staff.animation`), `traitees` (20 dernières alertes traitées :
  `{kind, pid, perso, par, quoi, time, moi}` — `par` = « Toi » pour le spectateur, `quoi` = « y est allé », « l'a
  ignorée », « a validé / refusé la mise à mort », « l'a observé », « a répondu »).
- `person` (chaque joueur de `players`, `alerts`) : `notes` (nombre), `etiquettes` (tableau des étiquettes des notes),
  `gele` (bool), `noir` (bool), `modeStaff` (bool), `modeAnim` (bool, 14/10).

`cherche` (S → C, 14/10) :

```json
{"t":"cherche","req":3,"q":"uchi","joueurs":[{"uuid":"<compte>","pid":"00009","perso":"Sora Uchiha","name":"sorasan",
  "online":false,"vu":"il y a 2 j"}]}
```

Lu dans l'index des N° (`PlayerIdRegistry`, personnages supprimés exclus), 20 résultats au plus ; `online` = ce
personnage est joué en ce moment ; `vu` = dernière connexion du compte (« en ligne (autre perso) » si le compte joue un
autre personnage). Le mod fusionne avec les joueurs en ligne de `snap`.

`mode` (S → C, 14/10) : `{"t":"mode","staff":false,"anim":true}` — envoyé au staff concerné à chaque entrée / sortie du
mode staff ou du mode animation ; le mod affiche un indicateur discret en haut à droite.
- `profile` : idem, plus `geleMotif`, `gelePar`, `retour` (bool : `return` possible), `back` (bool : `back` possible
  pour le spectateur).

### 4 bis.3 Canal `reborn:annonce` (S → C)

```json
{"t":"annonce","portee":"global","titre":"Annonce","texte":"La terre tremble sous vos pieds.","s":8,"son":true}
```

`portee` = `global` (tous les joueurs) ou `perso` (un seul joueur : scène, convocation). Affichage attendu : **bandeau
discret en haut au centre**, DA Reborn, fondu, `s` secondes. L'auteur n'est **jamais** envoyé aux joueurs (il est au
journal). **Sans le mod** : titre + sous-titre vanilla (global) ; sous-titre + ligne de tchat (perso).

### 4 bis.4 Canal `reborn:ecran` (S → C)

```json
{"t":"noir","on":true,"texte":"Vous perdez connaissance…","fondu":20,"s":0}
{"t":"noir","on":false,"fondu":20}
{"t":"seisme","f":5,"s":4,"son":true}
```

- `noir` : écran noir plein (au-dessus du HUD, sous le tchat), `texte` centré facultatif, `fondu` en ticks, `s` =
  durée prévue (0 = jusqu'à `on:false` ; le serveur envoie toujours `on:false` à la fin). Renvoyé à la reconnexion si
  toujours actif. **Sans le mod** : Cécité + Obscurité infinies et un titre.
- `seisme` : secousse de la caméra (amplitude ∝ `f`/10, décroissante sur `s` secondes) ; le **son** est joué par le
  serveur (grondement), le client n'en joue pas. **Sans le mod** : son + message de barre d'action seulement.

### 4 bis.5 Canal `reborn:plaques` (S → C, staff en mode staff seulement)

```json
{"t":"plaques","on":true,"joueurs":[{"uuid":"…","pid":"00042","perso":"Itachi Uchiha","pseudo":"Omar",
  "etat":"À terre · ATA","pv":7,"pvMax":20,"staff":false,"gele":false,"modeStaff":false}]}
{"t":"plaques","on":false}
```

Envoyé **chaque seconde** au staff en mode staff, pour les joueurs du même monde à ≤ 64 blocs. Affichage attendu :
petite plaque au-dessus de la tête (N°, nom RP, pseudo, état, PV), visible du seul staff ; `on:false` = tout
effacer. Les staffs en mode staff se voient entre eux (ils sont invisibles aux joueurs).

### 4 bis.6 Commandes en jeu (équivalents tapés, mêmes droits)

Détail dans §2.1 : `/journal`, `/note`, `/annonce`, `/annoncejoueur`, `/aller`, `/ramener`, `/retour`, `/geler`,
`/degeler`, `/voirsac`, `/noir`, `/seisme`, `/tenue`, `/emoteforcee`, `/persoreset`, `/persosuppr`, `/modestaff`,
`/modeanim`.

### 4 bis.7 Mode staff et mode animation (14/10)

**Mode staff = aucune mécanique RP** (`staff/outils/ModeStaff`, décision du 14/10). Point d'entrée commun pour les
addons : `KoService#isOutOfPlay(UUID)` (méthode par défaut ajoutée à l'API `@Stable`, vraie en mode staff) ;
`KoService#techniqueDenial` renvoie « Mode staff : aucune technique. ». Ce qui est bloqué, et où :

| Mécanique | Garde |
|---|---|
| Frapper / être frappé (mêlée, projectiles, techniques) | `ModeStaff` `EntityDamageByEntityEvent` / `EntityDamageEvent` (LOWEST) ; ShinobiCombat `onMelee` (`isOutOfPlay` de l'attaquant ou de la victime) |
| Techniques | ShinobiAbilities `JutsuExecutionManager.gateOrWarn` via `techniqueDenial` |
| Garde, dash, bond de chakra, esquive | ShinobiCombat `CombatListener` (canal `reborn:combatin`) |
| Menu R et commandes RP : `/corps` (porter, fouiller, exécuter, gorge, RPK), `/yeux`, `/bandage`, `/rencontrer`, `/amitier`, `/osculter`, `/soigner`, `/paume`, `/aide`, `/me`, `/duel`, `/meditation`, `/assoir`, `/allonger`, `/ramper`, `/playemote` (sur soi), `/testfeuille` (hors `give`), `/hopital` (hors `set` / `suppr` / `liste`), `/radio` (hors outils staff), `/trade`, `/lookdown` | `ModeStaff` `PlayerCommandPreprocessEvent` (LOWEST) |
| Ramassage (touche F, `pick:`), lâcher, utiliser (manger, pilule), détruire | `InventoryManager` (canal `reborn:inventory`) ; `EntityPickupItemEvent`, `PlayerDropItemEvent`, `PlayerItemConsumeEvent` |
| Blocs et meubles RP (coffres, portes, bibliothèques, chaises), objet en main | `PlayerInteractEvent` (clic droit refusé, clic gauche libre), `PlayerInteractEntityEvent` ; ShinobiAbilities `LibraryService` |
| Monture | `EntityMountEvent` ; on descend en entrant dans le mode |
| Tchat RP | `AsyncChatEvent` (LOWEST) : le message part au **chat staff** (le `#` du chat staff marche toujours) |
| Radio | `/radio` refusé hors sous-commandes staff (`ecouter`, `stop`, `couper`, `retirer`) — la radio n'existe pas encore |

**Réciproque — personne ne cible un staff en mode staff** : coups (annulés), regard des commandes RP (les rayons de
`/rencontrer`, `/osculter`, `/soigner`, `/amitier`, `/paume` l'ignorent), commandes RP qui le **nomment** (pseudo ou nom
RP : « Personne de ce nom ici. »), clic droit sur lui, monter sur lui, cible des mobs. Restent permis : Poste de garde,
commandes staff, `/voirsac`, `/emoteforcee`, `/playemote <emote> <joueur>`, choix des lits de l'Infirmerie.
*Non couvert* : la perception de ShinobiSense (Byakugan, détection du chakra), hors du périmètre de ce chantier.

**Mode animation** (`staff/outils/ModeAnimation`, `/modeanim`, permission `reborn.staff.animation`) : l'animateur ou le
MJ incarne un personnage pour un event — visible, mécaniques RP permises. **Journal** : `STAFF / MODE_ANIMATION`
(entrée, sortie et durée), et `STAFF / ANIMATION` pour **chaque commande, chaque message et chaque coup porté** pendant
le mode (en plus des catégories habituelles). Indicateurs : `modeAnim` dans `person` et `snap`, « Animation » dans
l'état des plaques (`reborn:plaques`) et du Poste de garde, message `mode` → ligne fine en haut à droite de l'écran de
l'animateur, ligne « X passe en mode animation » au chat staff. Exclusif avec le mode staff ; tombe à la déconnexion.

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
| `ko` | PV persistants, défaite à l'entraînement, à terre, inconscient, `volonte`, plancher, lits, ATA, `douleur` (seuil, vitesse, lutte), `execution.canalisation-secondes` | [`gameplay/KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) |
| `mort-rp` | `scene-min-secondes`, `corps-minutes` | idem |
| `inventaire` | `ramassage.distance` (3), `ramassage.cadence-ms` (250), `ramassage.poids-max-pourcent` (130) ; `surcharge.charge` (`de` 100, `vitesse-de` 0,90, `vitesse-a` 0,70, `saut`, `sprint`), `surcharge.surcharge` (`de` 115, 0,70 → 0,35, `saut` 0), `surcharge.immobile` (`de` 130, `vitesse` 0, `saut` 0) ; `sol.disparition-minutes` (10), `sol.disparition-ko-minutes` (15), `sol.balayage-secondes`, `sol.jamais-detruits.parchemins` / `.ids` ; `journal.fichier`, `journal.poste-de-garde-des` (RARE) ; `arme.ids`, `arme.vanilla` ; `cases-cachees.balayage` | [`gameplay/INVENTAIRE_ET_COSMETIQUES.md`](../gameplay/INVENTAIRE_ET_COSMETIQUES.md) |
| `journal` | `actif`, `dossier` (`logs`), `retention-jours` (90, 0 = jamais), `memoire` (20 000 entrées), `disque-max-jours` (31), `chat`, `coffres`, `xp` (faux), `commandes.tous` (vrai), `commandes.ignorer` (préfixes), `combat.degats`, `combat.seuil-pv` | §4 bis |
| `rarete` | `par-id` : id Nexo / de modèle → COMMUN, PEU_COMMUN, RARE, EPIQUE, LEGENDAIRE | idem §7 |
| `duel` | `actif`, `distance-proposition`, `distance-max`, `duree-max-secondes`, `expiration-secondes`, `cooldown-secondes`, `cooldown-couple-secondes`, `compte-a-rebours`, `techniques`, `teleport-max-blocs`, `grace-secondes`, `annonce.actif`, `annonce.rayon` | idem §4.1 |
| `medic` | `paume.*`, `bandage.*` | idem |
| `meditation` | régénération, rayon d'annulation (× modificateur `meditation`) | — |
| `modificateurs` | `sync-secondes` ; `types.<id>` : `nom`, `famille`, `bonus`, `icone`, `empilement`, `plafond`, `persistance`, `au-ko`, `visible`, `journal`, `duree-secondes`, `valeur`, `effets`, `refuse`, `ensuite` (types livrés : `regeneration`, `satiete_prolongee`, `endurance`, `meditation`, `pilule_soldat`, `fatigue`, `poison`, `pilule_recharge`) | [`ETATS_JOUEUR.md`](./ETATS_JOUEUR.md) §4.4 |
| `nourriture` | `actif` ; `satiete.*` (départ 80, perte 0,0125 %/s, `activite.*`, technique, vitesse du sprint, combat, AFK, affamé 20) ; `regeneration.*` (seuil 90, 0,25 %/s, pause 30 s, plafond ATA 30) ; `faim.endurance-recuperation` ; `cuisine.*` (15 s, 2 plats, 4 blocs, chaud 10 min, poste de son entreprise) ; `repas.*` (assis ×2, pause 30 s, effets froids ×0,5) ; `tendre.*` (3 blocs, 10 s) ; `pilule.*` ; `ration` ; `plats.<id>` (`nom`, `satiete`, `duree-secondes`, `icone`, `reste`, `effets`) ; `vivres.<id>` (`prix`, `quantite`, `recette`, `cuisinier-seulement`) | [`gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) §11, §13 |
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
| `jutsu` | apprentissage requis, `kenjutsu-exige-arme` (vrai : une arme à l'étui, dégainée ou en main), dépassement, coûts par stats, maîtrise, lancer rapide, incantation, MagicSpells |
| `mobility` | jetons aériens, ressenti, Naruto run, **`allure`** (marche, trot, sprint, coût d'endurance du sprint, relance ; 14/10), contrôle aérien, double saut, saut mural, onde au sol, dash, escalade, pistes, entraînement |
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
| `rpbags/` | Core | Sacoches RP (barre exclue : sac, sac porté, cosmétiques, étui `ARME`) |
| `logs/inventaire-AAAA-MM-JJ.log` | Core | (Catégorie `INVENTAIRE` du journal universel, format commun depuis le 14/10 ; les objets obtenus vont dans `logs/objet-…`.) Journal des objets, un fichier par jour : `RAMASSE`, `LACHE`, `DONNE`, `DETRUIT`, `RANGE` (case cachée), `ARME_ETUI`, `DISPARU` (Rare et plus) — heure, `#N° pseudo (nom RP)`, quantité, objet, rareté, lieu ; Rare et plus + uniques aussi au Poste de garde (type `objet`) |
| `creator-assets/` | Core | Catalogue du créateur de personnage |
| `emotes/` (+ `emotes.yml`) | Core | Fichiers EmoteCraft distribués + catalogue |
| `ko-state.yml`, `ko-reglages.yml` | Core | États KO en cours, réglages de l'Infirmerie |
| `ata-state.yml` | Core | ATA en cours |
| `training-zones.yml` | Core | Zones d'entraînement et de repos |
| `hospitals.yml`, `hospital-beds.yml` | Core | Points de réveil par village, lits |
| `player-ids.yml` | Core | **Player ID** : `next` (prochain N°) + `index` `'00042'` → `n`, `character` (UUID perso), `owner` (UUID compte), `name`, `deleted`. Un N° n'est **jamais réutilisé** (supprimé = `deleted: true`). Écriture atomique (`.tmp` + renommage), compteur écrit avant d'attribuer. Chaque personnage porte aussi `playerId: 42` dans `characters/<compte>.yml`. Migration idempotente au démarrage (ordre : UUID du compte puis UUID du perso) |
| `logs/<categorie>-AAAA-MM-JJ.log` | Core | **Journal universel** (14/10) : un fichier par catégorie (`joueur`, `inventaire`, `objet`, `combat`, `ko`, `duel`, `economie`, `interaction`, `bibliotheque`, `staff`, `commande`, `chat`, `systeme`) et par jour, une ligne par entrée : `AAAA-MM-JJ HH:mm:ss \| CAT \| ACTION \| #00042 Pseudo (Nom RP) [Grade] \| cible \| détail \| monde x y z` (`—` = vide). Rétention `journal.retention-jours`. Remplace `aggression-log.txt` (→ `combat`), `kill-log.txt` et `ko-log.txt` (→ `ko`), `duel-log.txt` (→ `duel`) ; les anciens fichiers restent sur le disque, plus alimentés |
| `gels.yml` | Core | Gels staff persistants, par compte : `par`, `motif`, `le`, `nom` |
| `entreprises.yml` | Core | Entreprises (14/10) : `nom`, `type`, `responsable` (UUID perso), `cree-le`, `cree-par`, `membres` (`perso`, `compte`, `nom`, `numero`, `role`), `postes` (`monde`, `x`, `y`, `z`, `libelle`) |
| `satiete.yml` | Core | Satiété par personnage (UUID → %) |
| `modificateurs.yml` | Core | Modificateurs actifs par personnage : `type`, `n` (intensité), `duree`, `reste` (gelé), `fin` (continu, epoch), `source` ; les types `PERDU` ne sont pas écrits |
| `notes-staff.yml` | Core | Notes staff par N° : `id`, `le`, `auteur`, `auteur-uuid`, `auteur-pid`, `auteur-grade`, `etiquette`, `texte` |
| `tenues-forcees.yml` | Core | Tenues imposées, par personnage : id, staff, date, pièce d'origine (sérialisée) |
| `sauvegardes/perso/<date>-<N°>-<reset\|suppr>/` | Core | Copie de `characters/<compte>.yml` et de la sacoche avant `/persoreset` ou `/persosuppr` |
| `logs/ko-AAAA-MM-JJ.log` (catégorie KO, via `ko/KoJournal`) | Core | Transitions du KO (douleur, agonie, inconscient, réveil, hôpital), portages, prises à la gorge, fouilles, exécutions (début, interruption, coup), commandes staff `/execution forcer\|douleur\|annuler` — `#00042 Nom RP (pseudo)` ; aussi au journal du Poste de garde (`ko`) |
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
