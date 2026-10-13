# NydoMod 5.5 — progression et monde (étude pour Reborn)

> Étude du code décompilé de NydoMod 5.5 (Forge 1.20.1, « All Rights Reserved »). **Concepts seulement** : on ne
> reprend ni code ni assets. Chemins relatifs à `net/nydomod/` et aux ressources du jar (code décompilé hors dépôt). Date : 2026-10-12.
> Comparaison avec Reborn d'après `docs/CHANTIERS.md`, `docs/gameplay/{STATS_ET_PROGRESSION,APPRENTISSAGE,PERSONNAGES,MISSIONS}.md`.

## Résumé

1. Nydo est un mod **solo / petit coop** : toute la progression est automatique (rang = f(niveau), niveau = √XP de combat), à l'opposé du choix Reborn (grades RP, XP invisible).
2. Le **moteur de missions** est la pièce la plus réutilisable : 6 types d'objectifs (KILL, FETCH, LOCATION, ESCORT, DIALOGUE, TRIAL), rangs D→S, prérequis, chaînage, répétition avec délai, récompenses XP + ryo + objets, suivi GPS et HUD. ~105 missions, dont ~80 à Konoha.
3. Les **missions et dialogues sont éditables en jeu par un admin** (OP 2) : PNJ, dialogues à conditions, missions, boutiques et factions sont stockés dans un `SavedData` du monde. C'est un équivalent maison de CustomNPCs, et c'est ce qui intéresse le plus un serveur animé par un staff.
4. **Réputation de village** (−1000 à +1000) : elle débloque les missions (C ≥ 100, S ≥ 650), donne le statut « recherché » si l'on agresse un villageois, et l'Akatsuki ne recrute que les joueurs mal vus (≤ 10).
5. **Apprendre un jutsu** se fait de trois façons : un parchemin (mini-jeu « mémoriser puis reproduire les mudras », 3 vies, parchemin perdu en cas d'échec), un maître PNJ (mudras en plus et blocage définitif si l'on n'a pas l'affinité), ou des **points de jutsu** dont le prix augmente à chaque achat.
6. **Max Jutsu** : 6 disciplines × 4 sceaux préparés, 3 préréglages. C'est un choix de build avant le combat.
7. **Tutoriel** : 12 étapes, validées uniquement par des actions acceptées par le serveur. Il s'accompagne d'un **Codex** dont les entrées se débloquent avec l'XP.
8. **Konoha** est générée à un endroit fixe (environ 1 280 × 1 280 blocs), protégée, peuplée de PNJ qui ont un rôle (garde, service, patrouille, habitant, mannequin d'entraînement). Une carte de route trouvée dans les coffres lance la première mission.
9. **Kabuto** est un marchand d'ADN qui apparaît au hasard dans les grottes et les forêts. Il vend 4 à 6 échantillons de kekkei genkai payés en ryo physique ; l'injection est un pari (90 % de chances au maximum) et un échec rend malade.
10. Pour Reborn, les priorités sont le moteur de missions + PNJ/dialogues pilotés par le staff, la réputation de village et la carte de route vers son village. Il vaut mieux écarter les rangs automatiques, l'XP de combat et les points de jutsu, qui contredisent les décisions déjà prises.

---

## 1. « Becoming a Ninja » et progression RPG

**Ce que voit le joueur.** Au début, il est « Civil ». Il devient ninja dès qu'il a **un kunai et un shuriken dans
l'inventaire** : succès « Shinobi », « You may now begin your ninja training! ». Il reçoit alors 100 XP ninja, ses
pouvoirs se débloquent, ses natures de base sont tirées et le tutoriel démarre. La Ninja Card affiche son rang, son niveau, ses PV,
son chakra, un radar de stats et ses points à dépenser.

**Mécanique.**
- `PlayerTracker` : `battle_experience` dans les `getPersistentData()` du joueur ; `isNinja` ⇔ XP > 0 ;
  **niveau = √XP** ; PV bonus = XP × 0,005 ; chakra max = XP × 0,5 (`ModConfig.getPlayerMaxChakraFromBattleXp`, plafonné).
- Le déclencheur « ninja » est un **advancement vanilla** (`data/nydomod/advancements/ninjaachievement.json`,
  `inventory_changed` kunai + shuriken). La config serveur `start_as_ninja` (vraie par défaut) commence directement à **300 XP**
  (niveau ≈ 17).
- **Gain d'XP** (`progression/NinjaXpProgression`) : il vient **des dégâts infligés et reçus** (menace de la cible × part des
  PV retirés), avec un plafond de **60 XP par fenêtre de 1 s**. Les missions donnent aussi de l'XP (§4).
- **Rang affiché = fonction du niveau** (`ninjacard/NinjaCardStatsProvider.rankKey`) : < 10 Académie, < 25 Genin,
  < 45 Chūnin, < 70 Jōnin, < 100 ANBU, 100 « Kage-level ». Ce sont les mêmes paliers que `NinjaMissionRank.minLevel` (D 0, C 10, B 25,
  A 45, S 70).
- **Stats RPG** (`progression/stats/NinjaStats`, `NinjaStatFormula`) : 8 stats (Ninjutsu, Taijutsu, Genjutsu, Contrôle,
  Vitalité, Défense, Agilité, Médical), plafond de 120 chacune. Points = 2 × (niveau + bonus de promotion) ; les
  bonus de promotion valent +2 à 10, +3 à 25, +4 à 45, +5 à 70 et +6 à 100. Certaines techniques **exigent une stat** selon leur palier :
  0 / 12 / 24 / 48 / 72 / 96 (`NinjaTechniqueRequirements`).

**Intérêt RP.** Faible, voire contraire à ce que veut Reborn. Le rang automatique et l'XP de combat sont des choix de jeu solo.
Reborn a décidé l'inverse : **le grade est RP, l'XP est invisible et le combat hors zone ne rapporte pas d'XP**, et **aucune
stat ne ferme une technique**.
**Reborn a déjà :** XP → niveau (invisible), 6 stats, grades RP. **À ne pas reprendre.** Une seule idée vaut le coup : un
déclencheur de rite « tu deviens ninja » marqué par un vrai moment (remise du bandeau par un PNJ ou par le staff) plutôt que par un
simple flag.

## 2. Apprendre un jutsu (« Learning Jutsus from Scrolls »)

**Ce que voit le joueur.** Il fait un clic droit avec un parchemin (`ItemScroll*`, une soixantaine de classes). Un écran de **mudras**
s'ouvre (`gui/overlay/HandSealScreen`) : la séquence s'affiche pendant N secondes, puis il faut la **reproduire**.
Avec 3 vies, une erreur remet la séquence à zéro. Réussite : « appris », parchemin consommé. Échec : **parchemin perdu**.
Le parchemin peut aussi être **posé sur un présentoir** (`BlockJutsuScrollDisplay`) pour s'entraîner sans le tenir.

**Mécanique** (`system/HandSealRegistry`, `HandSealTrainingManager`, `item/ItemScrollHstsBridge`).
- Chaque entrée = clé, nature, rang, séquence de signes parmi 12 (Tora, Inu…), au moins 4 signes de base.
- **Temps de mémorisation** = max(rang : D 9 s, C 12, B 14, A 16, S 18 ; 6 + 2 × nombre de signes).
- **Vies** : 3 avec un parchemin ; avec un maître, D/C 5, B 4, A/S 3.
- **Maîtres élémentaires** (`EntityJutsuMaster`, `EntityElementalMaster`, `system/JutsuMasterSpawner`) : PNJ **uniques** par
  élément, qui apparaissent dans la forêt « Eternal Leaf » (10 % de chances toutes les 2 400 ticks, réapparition 24 000 ticks).
  Ils enseignent la **technique suivante** de la progression, à condition d'avoir assez d'XP pour son rang (D 100,
  C 200, B 450, A 800, S 1 200).
  **Sans affinité** pour la nature : 3 à 5 signes aléatoires en plus, −10 % de chakra, et un **échec bloque la technique
  pour toujours** (`markBlocked`).
- **Origine** (`progression/jutsu/JutsuOrigin`) : NEUTRAL / BLOOD (clan) / GRAFT (ADN, coût ×1,25) / NO_AFFINITY
  (coût ×2, plafonné au palier T3).
- **Échelle de techniques** (`JutsuLearning.hasPreviousRung`) : pour un palier donné, il faut connaître une technique du palier précédent.
- **Points de jutsu** (`JutsuPoints`, `network/JutsuPurchaseMessage`) : on en gagne niveau + min(niveau, 100) × 3/5.
  La technique d'entrée d'une nature coûte 1 point, puis chaque technique avancée coûte **nombre d'achats + 1** (coût
  triangulaire). Un « grand livre » répare les incohérences.
- **Maîtrise** (`JutsuMasteryFormula`) : l'XP propre à chaque technique raccourcit le temps des signes (jusqu'à ×1/3), baisse
  le coût en chakra (jusqu'à −20 %) et augmente la puissance (jusqu'à ×1,18).

**Intérêt RP.** Moyen. Le **parchemin perdu en cas d'échec** et le **blocage définitif sans affinité** créent de vraies
conséquences. Ce n'est pas l'esprit de Reborn, où un échec ne fait perdre qu'une séance.
**Reborn a déjà :** l'objet parchemin unique (volable), les bibliothèques à tirage, les séances (1 à 20 selon le rang) avec un
**enchaînement de mudras** pour le Ninjutsu, le bonus maître (×2), la validation staff pour le rang S et les slots S. **Reborn est déjà
plus riche** que Nydo sur ce point. On peut reprendre deux idées : (a) le **présentoir à parchemin**, déjà prévu à Reborn
(« rouleau posable en meuble Nexo »), (b) une **pénalité légère sans affinité de nature** (signes en plus) si l'équipe veut
valoriser la nature révélée par le test de la Feuille. Effort : facile (b), moyen (a).

## 3. Max Jutsu — « Shinobi Preparation »

**Ce que voit le joueur.** Un onglet « Preparation » dans la Ninja Card. **6 positions** numérotées de 1 à 6 (le Taijutsu reste
automatique) ; chaque position reçoit une discipline (Katon, Raiton…) et **4 sceaux** où l'on choisit parmi les
techniques apprises. Compteur « MAX JUTSU • x/y PREPARED ». En combat : 1-6 pour choisir la discipline, haut/bas
pour parcourir les sceaux.

**Mécanique** (`item/JutsuLoadoutData`) : `PRESET_COUNT = 3`, `MAX_PREPARED_JUTSUS = 4` par discipline, stocké
sous `ShinobiPreparation` dans le NBT. Échange atomique côté serveur (`JutsuLoadoutActionMessage`). Le tutoriel
précise : « moins de techniques préparées = un build plus rapide à faire tourner ».

**Intérêt RP.** Bon en PvP encadré : on **montre son jeu** avant un duel ou un examen, et la limite pousse à la spécialisation.
**Reborn a déjà :** rien de documenté sur une limite de techniques équipées (à vérifier dans ShinobiAbilities).
**Faisabilité** : moyenne. La liste préparée par personnage vit côté plugin, l'écran se fait dans reborn-hud (canal JSON comme
`reborn:parchemin`), et le serveur refuse le lancer d'une technique non préparée.

## 4. Missions

### 4.1 Ce que voit le joueur
- Un onglet **Missions** dans la Ninja Card, en trois sections : « NYDO Main Story » (STORY), « Shinobi assignments »
  (SINGLEPLAYER, tirées au sort) et « Missions created in this world » (ADMIN, créées par un admin).
- On accepte et on rend une mission **auprès d'un PNJ** (options de dialogue « Accepter » / « Rendre »).
- **Navigation** : on suit une mission, une boussole et des points de passage s'affichent (`mission/navigation/*`,
  `MissionRoute` jusqu'à 32 points), avec des marqueurs dans le monde (`village/KonohaMissionMarkers`).
- **HUD de mission** (`NYDOMissionHud`, `MissionNoticeQueue`) : bandeaux « accepted / progress / completed / cancelled »,
  conseils contextuels et alerte « urgent » en combat.

### 4.2 Le modèle de données
`NYDOMissionDefinition` (record, sérialisé en NBT `SchemaVersion 2`) : `id`, `source` (STORY / SINGLEPLAYER / ADMIN),
`rank` (D-S), titre, description, **jusqu'à 16 objectifs**, `rewardNinjaXp`, `rewardRyo`, **jusqu'à 16 objets** en
récompense, `giverFaction`, **prérequis** (32 au maximum), `nextMissionId` (chaînage automatique), `repeatable`,
`repeatCooldownTicks`, `enabled`.

`NYDOMissionObjectiveDefinition` — type + cible + quantité (1 000 000 au maximum) :

| Type | Cible | Validation |
|---|---|---|
| KILL | type d'entité **ou identité d'un PNJ précis** (`NYDOMissionNpcTargets`) | événement de mort |
| FETCH | objet + quantité, `consume` | vérifié **et consommé au moment de rendre la mission** |
| LOCATION | COORDINATE (pos + rayon), DIMENSION, BIOME, STRUCTURE | tick de position |
| ESCORT | preset de PNJ + destination + rayon | `NYDOMissionEscortController` fait apparaître le PNJ, échec s'il meurt |
| DIALOGUE | identité du PNJ (+ conversation scénarisée facultative) | parler au PNJ |
| TRIAL | id d'épreuve scriptée + quantité | code dédié (cloche de Kakashi, mannequins, rondes…) |

Les **rangs** (`NinjaMissionRank`) donnent un niveau minimum (D 0, C 10, B 25, A 45, S 70) et un multiplicateur de récompense
(1,0 / 1,3 / 1,75 / 2,3 / 3,2).

**Mise à l'échelle** (`NYDOMissionScaling`, sauf pour les missions ADMIN et Konoha) :
- XP = (base + niveau × 1,8) × multiplicateur du rang ; ryo = (base + niveau × 35) × multiplicateur, arrondi à la centaine
  (100 au minimum).
- Quantités : KILL + min(5, niveau / 20), FETCH + min(6, niveau / 25).
- Le niveau est **figé à l'acceptation** (`ScaleLevel`), ce qui empêche de monter de niveau en cours de mission pour gonfler la récompense.

**État joueur** (`NYDOMissionsService`, NBT `nydomod:missions_v2`) : liste `Active` (Accepted, Failed, Attempt UUID,
progression par objectif), `Completed` (horodaté, pour le délai de répétition), `TrackedMission`, `LastRoll`.
Au plus **3 missions SINGLEPLAYER** actives, tirées parmi les 4 plus proches du niveau du joueur (`ensureSingleplayerMissions`),
et 128 actives au total. L'UUID d'**Attempt** empêche de compter des kills d'une tentative annulée.

**Récompense** (`reward`) : XP ninja + **ryo physique** (pièces de 100, 1 000, 10 000 et 1 M, `givePhysicalRyo`) + objets ;
pour Konoha, **réputation** en plus (`KonohaMissions.completed`).

### 4.3 Contenu
~105 titres de mission dans `en_us.json`. Le catalogue « générique » (`NYDOMissionsCatalog`) va de « academy_zombies »
(D, 4 zombies, 45 XP, 500 ryo) à « wither_proof » (S, 700 XP, 26 000 ryo) et compte des primes sur des bosses (Zabuza A, Itachi /
Deidara / Sasori S). Konoha (`village/KonohaMissions` et sous-catalogues) : arc principal, commissions de l'Hokage
(D→S, **réputation requise** 0 / 100 / 200 / 400 / 650), leçons de Gai (6), campagne de Jiraiya (6 chapitres), missions
d'habitants, de mentors et de « ville ». Une seule commission ou un seul chapitre à la fois.

### 4.4 Exemple de bout en bout — « First Mission — Journey to Konoha »
1. **Accroche** : la **carte de route de Konoha** (`ItemKonohaMapScroll`) se trouve dans les coffres. Elle est garantie dans les coffres
   de cartographe de village, avec 35 % de chances dans les autres coffres de village et 18 % ailleurs (`loot/KonohaMapLootModifier`).
2. **Clic droit sur la carte** (`KonohaArrivalScroll.unseal`) : il faut être ninja. La mission **« Road to Konoha »** (D, STORY) entre
   dans le journal. Le texte indique ce qu'il faut apporter : 32 bûches de chêne et 8 lingots de fer.
3. Avec les ressources, un nouveau clic **descelle la carte** : l'atlas s'ouvre avec Konoha marquée (`AtlasDestinations.KONOHA`,
   porte principale) et un marqueur rouge sur la porte.
4. **Voyage** jusqu'au village (à −6144, 1024). On parle au **capitaine de la porte** (objectif DIALOGUE). On **rend** la mission
   auprès de lui : les ressources sont consommées **à ce moment-là seulement**. Récompense : 35 XP, 400 ryo, +20 de réputation.
5. **Chaînage** (`nextMissionId`) : « **A Place in the Leaf** » est acceptée automatiquement. Il faut parler à Iruka à l'Académie puis
   à l'Hokage (deux **scènes de dialogue écrites**, `KonohaMissionScenes`) et revenir au capitaine. On gagne +25 de réputation
   et l'on **rejoint Konoha** comme village d'origine (`NYDOVillageReputation.join`).
6. Suite : « First Lesson » (parler à Kakashi, puis porter 12 coups aux mannequins), puis « **Kakashi: The Bell Test** » (TRIAL :
   tentative de 90 s, s'approcher dans son dos à moins de 2,8 blocs, main vide, rester caché 1 s, prendre la cloche ;
   un coup reçu, une sortie de l'arène ou une déconnexion font échouer), puis « The Will of Fire » (C, niveau 10).

### 4.5 Intérêt RP et faisabilité
- **Utile en multijoueur encadré** : un staff écrit des missions à étapes et à conditions, les rattache à un PNJ donneur,
  les enchaîne et voit qui en est où. Les objectifs DIALOGUE / LOCATION / FETCH / ESCORT sont parfaits pour du rang D ou C
  joué en RP (le ravitaillement Konoha → Ryukon de `MISSIONS.md` se traduit directement en LOCATION → FETCH → ESCORT → DIALOGUE).
- **Propre au solo** : tirage automatique de 3 missions, mise à l'échelle par niveau, missions de « farm » de mobs vanilla,
  progression **individuelle** (aucune notion d'équipe : les kills et objets ne sont pas partagés, rien dans
  `NYDOMissionsHooks` ni `NYDOMissionsService`).
- **Pour Reborn**, il faut ajouter : des missions **d'équipe** (progression partagée par escouade), une **validation par le staff** pour le
  rang B et au-dessus (comme pour les parchemins S), un **rapport de mission** généré à la fin (le format parchemin de `MISSIONS.md` §4)
  et une récompense via `XpService.reward` + ryo + mérite.
- **Reborn a déjà :** rien en jeu, seulement des idées et des formats de rapport (`MISSIONS.md`). **Effort : lourd** (moteur
  d'objectifs, persistance par personnage, écrans du journal et du suivi dans reborn-hud, éditeur staff). On peut le découper :
  lot 1 DIALOGUE + LOCATION + FETCH + journal (moyen), lot 2 ESCORT / KILL de PNJ / équipes (lourd).

## 5. Tutoriel interactif et Codex

**Tutoriel** (`tutorial/NydoTutorialSystem`, `NydoTutorialStage`) — 12 étapes dans l'ordre : ouvrir la Ninja Card →
Préparation → mode combat → coup léger → coup lourd → esquive → garde → combo aérien (lanceur puis poursuite) →
apprendre un jutsu → le lancer une fois → changer de jutsu → lancer un jutsu. **Chaque étape n'est validée que par une action acceptée
par le serveur** (« Only an attack accepted by the server completes this step »). Un panneau HUD montre l'objectif, le prérequis,
« pourquoi c'est utile » et une astuce. `/nydotutorial explain | skip`. Les étapes déjà acquises sont rattrapées automatiquement
(`reconcileExistingProgress`). Persistance `NydomodTutorial` (Version 4) ; on peut le couper par la config et par une gamerule.

**Codex de l'Hokage** (`guide/CodexCatalog`, `CodexService`, `client/CodexScreen`) : livre en 10 sections (chemin,
chakra, combat, jutsu, yeux, monde, biju…). Une quarantaine d'entrées liées entre elles, **débloquées par l'XP** (0 / 100 / 400 / 1 600)
ou par un contexte (avoir un Sharingan, un biju…). On le trouve physiquement dans le bureau de l'Hokage (`village/HokageCodexDiscovery`).

**Intérêt RP.** Fort pour **l'arrivée des nouveaux joueurs** sans mobiliser un staff : touche K, sacoche, KO / Volonté, parchemins…
Un codex à déverrouiller peut tenir lieu de wiki en jeu.
**Reborn a déjà :** aucun tutoriel documenté, mais il y a le wiki web (`apps/admin` wiki, launcher). **Faisabilité : moyenne.** Les étapes et
leur validation côté plugin (événements déjà émis par ShinobiCore) + un panneau HUD dans reborn-hud. À articuler avec la
création de personnage (« Académicien »).

## 6. Konoha et la génération du monde

**Ce que voit le joueur.** Un **Konoha complet** à un endroit fixe : porte, Académie, bureau de l'Hokage, falaise des Hokage
sculptée, rivière, quartiers, quartier Uchiha (schematics importés), intérieurs meublés, éclairage. Autour, des biomes NYDO
(forêt Eternal Leaf, montagnes, cols enneigés, Myoboku, Vallée de la Fin, repaire Akatsuki).

**Mécanique.**
- `village/KonohaVillage` : centre (−6144, 1024), zone ±640 blocs ; `KonohaBlueprint` (785 lignes, plan **procédural en
  code**), `KonohaLandscape` / `KonohaRiver` (terrain), `AuthoredVoxelTemplate` + `data/nydomod/authored/*.nydo.gz`
  (schematics importés et compressés, avec `SOURCES.txt` qui crédite les auteurs).
- **Habitants** : liste fixe avec un rôle (`Duty` GUARD / SERVICE / PATROL / RESIDENT / DUMMY) et un rang de PNJ ; ceux de SERVICE
  tiennent une **boutique** (armes, nourriture, vêtements, parchemins). Ils réapparaissent après 600 s (`NYDOVillageRules`).
- `KonohaVillageProtection` : on ne peut ni casser ni poser de blocs dans le village.
- Génération : `worldgen/biome/NYDOTectonicBiomeSource`, `density/NationRegionDensityFunction` (régions de « nations »),
  de nombreuses `feature/*`, avec un interrupteur par élément (`NYDOWorldgenConfig`).

**Réputation** (`NYDOVillageReputation`, `NYDOVillageRules`, `VillageStanding`) : score de −1 000 à +1 000. Il démarre à 1 000 pour
un ninja admis. Agresser un villageois coûte −25 (une fois toutes les 5 s) et donne le statut **« recherché » pendant 60 s** ;
un meurtre coûte −150. À ≤ −100, les gardes deviennent hostiles ; en dessous de −40, plus aucun service. Statuts affichés : WANTED / HOSTILE / RESTRICTED / NEUTRAL /
TRUSTED (≥ 100) / HONORED (≥ 300). La mission « restitution » (64 bûches + 16 fers) permet de se racheter. `/nydovillage reputation`.

**Intérêt RP.** Le village en dur fait doublon : Reborn construit sa propre map (`PaysDuFeu06`). La **réputation par
village** est en revanche très RP : elle peut ouvrir l'accès aux missions, aux bibliothèques privées et aux services,
et le statut « recherché » est une conséquence lisible pour les agresseurs. À Reborn, elle devrait **aussi pouvoir être fixée par le staff**
(« mal vu par le conseil ») et pas seulement par des événements automatiques.
**Reborn a déjà :** des villages au choix (6) à la création, un monde construit à la main, une carte (touche M). Pas de réputation.
**Faisabilité** de la réputation : **facile à moyenne** (un champ par personnage et par village, des hooks de dégâts sur les PNJ
protégés, un affichage dans la fiche K). Génération de village : **hors sujet**.

## 7. PNJ, dialogues et IA (customnpc/, dialogue/, ai/, npc/)

**Ce que voit un admin.** Un **éditeur de PNJ en jeu** (OP 2, à 16 blocs au plus) : nom, modèle, texture / skin, échelle
(0,5 à 2), rôle (COMBATANT / TRADER / AMBIENT / GUARDED_TRADER), **rang** (Académie→Kage avec des stats préréglées, par ex. Jōnin 150 PV,
5 000 chakra), faction, boutique, PV et chakra personnalisés, déplacement, **zone de rattachement** (rayon de 1 à 256), réapparition
(de 1 s à 24 h), style de taijutsu, **jusqu'à 32 jutsus**, inventaire et butin. Clonage et presets, dossiers, import / export
(`NYDOCustomNpcPresetTransfer`).
Tout est stocké dans `NYDOCustomNpcWorldData` (`SavedData "nydo_custom_npcs"`) : PNJ, **factions**, **missions**, **boutiques**,
**dialogues**, liens d'interaction. Un contrôle d'intégrité empêche de supprimer une mission encore référencée (`NYDOContentIntegrity`).

**Dialogues** (`NYDODialogueDefinition`) : titre, texte (4 096 caractères), **8 options au maximum**, prérequis, `oncePerPlayer`.
Une option déclenche une action : OPEN_DIALOGUE, **ACCEPT_MISSION**, **CLAIM_MISSION**, OPEN_SHOP, CLOSE, OPEN_OCULAR_SURGERY ou
REQUEST_ORGANIZATION_RECRUITMENT. **Conditions** (`NYDODialogueAvailability`) : niveau min / max, **jour / nuit**,
missions terminées, actives ou interdites, dialogues lus ou non lus, état d'une mission (CAN_START, IN_PROGRESS, READY,
COMPLETED, FAILED…). On obtient ainsi des PNJ qui « savent » où en est le joueur.

**IA** (`ai/`, une soixantaine de classes) : cerveau, blackboard, factions (disposition ami / neutre / hostile), budget d'actions par
niveau, phases de combat, presets de ninjas (`NYDOAINinjaPresets`, 933 lignes), Susanoo / dōjutsu pour les PNJ.

**Intérêt RP.** **Très fort.** C'est l'outil de l'animateur : un staff pose un PNJ « marchand louche », écrit trois répliques
qui dépendent de l'avancement du joueur, lui fait donner une mission et l'enlève après l'événement. C'est l'équivalent maison de
CustomNPCs / Citizens + Quests.
**Reborn a déjà :** aucun système de PNJ ni de dialogue documenté. MythicMobs et ModelEngine servent au combat, et il y a le Poste de garde (F7) pour le staff.
**Faisabilité : lourde** si on le refait entièrement, **moyenne** si on s'appuie sur Citizens (PNJ) + un plugin Reborn pour les dialogues et missions
avec un écran de dialogue dans reborn-hud (canal JSON). L'éditeur de PNJ complet n'est pas nécessaire : les commandes staff +
YAML + rechargement suffisent pour commencer.

## 8. Organisations (Akatsuki)

`organization/NYDOOrganizations`, `OrganizationAdmissionRules.AKATSUKI = (peaceful ≤ 40, recruit ≤ 10)` : les PNJ
« poste Akatsuki » sont **hostiles** aux joueurs dont la réputation dans leur village d'origine dépasse 40 ; ils **recrutent**
ceux qui sont à 10 ou moins (option de dialogue, à 6 blocs au plus et en ligne de vue). L'adhésion est enregistrée avec la date et le parrain.
Il existe aussi un repaire / donjon généré (`AkatsukiHideoutBlueprint`, `AkatsukiDungeonRooms`, Gedo Mazo).

**Intérêt RP.** L'idée est bonne : **la mauvaise réputation ouvre la porte des organisations criminelles**. Mais à Reborn,
entrer dans l'Akatsuki ou devenir déserteur doit rester une **décision staff et RP** (le « Déserteur » a d'ailleurs été retiré de la création).
**Reborn a déjà :** les « fonctions RP » (ANBU, conseil) prévues dans le design Faction, pas encore commencé. **Faisabilité : facile** (une condition
dans le design Faction), à condition d'avoir la réputation (§6).

## 9. Ninja Card

`ninjacard/NinjaCardData` (une soixantaine de champs synchronisés par `NinjaCardSyncPacket`) avec 5 onglets : **Overview** (rang, niveau,
PV, chakra, radar attaque / défense / vitesse / chakra / taijutsu / technique), **Shinobi Build** (stats), **Preparation**
(Max Jutsu), **Progression** (natures, ADN, maîtrise), **Missions**. On y trouve aussi les statistiques de vie (dégâts infligés et reçus, kills,
morts, temps de jeu, distance), le clan, le sous-clan, le village et sa réputation, et les points de jutsu.

**Intérêt RP.** Moyen. Une **fiche d'identité shinobi** que d'autres peuvent consulter (le staff, un supérieur) est utile ; le radar chiffré
contredit la règle Reborn « sans chiffres exacts ». **Reborn a déjà :** la fiche « lanternes célestes » (K). On peut ajouter un onglet
**Missions / état de service** (missions faites, rang de mission le plus haut, réputation) quand les missions existeront. Effort : facile une fois
les données présentes.

## 10. Boutique, économie, ryo

- **Boutiques fixes** (`shop/NinjaShopCatalog`) avec 5 types : WEAPONS (shuriken ×24 300 ryo … Gunbai 42 000), FOOD (riz 300 …
  chakrafruit 125 000), CLOTHES (masques ANBU 3 500 … robe Akatsuki 28 000), SCROLLS (Kawarimi 1 500, Kage Bunshin 4 500,
  Rasengan 9 000, Chidori 8 500…), RARE.
- **Boutiques personnalisées** (`NYDOShopDefinition`, `NYDOShopOffer`) configurées par l'admin : paiement **RYO** (portefeuille), **PHYSICAL_RYO**
  (pièces) ou **ITEMS** (troc avec un ou deux objets).
- **Portefeuille** (`item/ItemShinobiWallet`) : c'est un **objet**. Il porte le solde (999 999 999 999 au maximum), le propriétaire et un **journal des
  12 dernières opérations** ; on y dépose les pièces de ryo. Il peut être perdu ou volé, comme le parchemin Reborn. `/wallet get|add|set|take`
  (OP 2). Les transactions sont atomiques (`NYDOShopTransactions`).
- Sources de ryo : missions (§4) et ventes.

**Intérêt RP.** **Le ryo physique et le portefeuille-objet** sont très RP (on se fait racketter, on paie en main propre, on
transporte une solde). Les missions qui paient en ryo bouclent l'économie que l'ADR 0006 laisse ouverte (« gagnée en jeu : RP, combat,
quêtes »). **Reborn a déjà :** l'échoppe (tenues, cartes, consommables) en ryo, sans source de ryo documentée. **Faisabilité** du
portefeuille-objet : facile à moyenne (objet Nexo / PDC + solde dans le PDC de l'objet + journal). **Ne pas** vendre de parchemins
en boutique : cela contredirait les bibliothèques à tirage.

## 11. Cartographie (Atlas)

`cartography/AtlasService` : rendu **côté serveur** du vrai terrain en tuiles (512 échantillons par tick, cache de 768 tuiles), envoyé au
client et affiché en style **papier** (`AtlasScreen`, `AtlasPaperStyle`). Les **destinations dépendent de l'objet** utilisé
(`AtlasDestinations` : carte de Konoha → porte de Konoha ; carte de Jiraiya → 6 paysages ; **carte des biju** → biju vivants
suivis par `BijuMapTracking`).

**Intérêt RP.** L'idée à garder est la **carte comme objet** : une carte trouvée ou donnée par le staff qui **révèle un lieu** (planque,
zone de mission). **Reborn a déjà :** une carte du monde (M) générée depuis `PaysDuFeu06`, avec des lieux placés à la main (`/carte set`).
Il resterait à ajouter **des marqueurs propres à un objet ou à une mission**, ce qui est facile à moyen (le serveur envoie des points à la carte existante).

## 12. Clans et sous-clans

- **Clans de sang** (`data/nydomod/clanes/*.json`, `ClanRegistry`, `ClanAssignmentHandler`) — **tirés au hasard selon un poids**
  à la première connexion : Commun 36, Sarutobi 8, Hatake 7, Hozuki 6, Uzumaki 6, Yotsuki 5, Yuki 4, Uchiha 3,
  Hyuga 3, Kaguya 3. Chaque clan donne ses natures, son kekkei genkai et ses jutsus de départ ; le **village est déduit du clan**
  (Uchiha → Konoha, Yuki / Hozuki → Kiri, Yotsuki → Kumo, sinon au hasard).
- **Sous-clans** créés par les joueurs (`MiniClan`, `ClanSavedData`) : `/nyclan`, 3 à 24 caractères, **15 membres au maximum**, invitation
  par « Clan Scroll » valable 60 s. Ils donnent un **bonus quand les membres sont proches** (32 blocs, jusqu'à 5 compagnons), différent selon le clan (Uchiha
  +11 % d'attaque par compagnon, plafond 45 %…).

**Intérêt RP.** Le tirage au sort est propre au solo : à Reborn, le clan se **choisit** et passe par une **candidature** pour les clans
verrouillés. Le **sous-clan / famille** créé par les joueurs a de l'intérêt pour le RP (lignées, maisons). Le buff de proximité en a moins (combat).
**Reborn a déjà :** 11 clans au choix, avec verrous par village et par sexe et des candidatures. **Faisabilité** des familles : moyenne, à rattacher au
design Faction.

## 13. ADN et Kabuto, marchand ambulant

**Ce que voit le joueur.** En explorant une grotte ou une forêt, il peut tomber sur **Kabuto**, qui ouvre un « DNA Black Market ». Il vend 4 à 6
échantillons d'ADN et **n'accepte que le ryo en pièces** (« Kabuto only accepts cash »). On prépare l'injection avec une seringue
vide (Maj + clic), puis on se l'injecte.

**Mécanique.**
- `worldgen/npc/NYDOKabutoBlackMarketSpawner` : tirage **toutes les 10 s** autour du joueur ; grotte sous un biome NYDO 1,8 %,
  autre grotte 0,5 %, forêt NYDO 0,3 %, autre forêt 0,1 % ; pas de second Kabuto à moins de 160 blocs.
- `entity/EntityKabutoBlackMarket` : stock persistant de 4 à 6 offres pondérées (Hyōton poids 85 / 24 000 ryo … Mokuton 20 /
  70 000, Jinton 8 / 95 000). Le prix varie avec la qualité de l'échantillon (×0,7 à ×3,3, entre 8 000 et 500 000), et une offre achetée disparaît du stock.
- `progression/dna/DnaProgression.inject` : chance finale = chance de base (progression de la nature) + bonus de l'échantillon
  (25 % au plus), **plafonnée à 90 %**. Un échec provoque nausée, faiblesse, peut-être Wither, des dégâts et une **perte de chakra** proportionnels au
  risque de rejet. Une réussite donne une nature **GRAFT** (coût en chakra ×1,25). On trouve aussi des échantillons en mangeant des cellules de
  Zetsu blanc (Mokuton).

**Intérêt RP.** **Très fort comme ressort d'animation** : un marchand **rare, mobile et louche**, qui ne prend que le cash, propose
des objets **interdits** à un prix délirant, avec un risque physique. Dans un serveur RP, c'est idéal comme **PNJ d'événement piloté par le
staff** (Orochimaru, Kabuto, un marchand du marché noir) plutôt que tiré au hasard : apparition annoncée par une rumeur, stock choisi.
L'acquisition de kekkei genkai par ADN contredit en revanche probablement le lore Reborn (clans sur candidature), donc **à décider par l'utilisateur**.
**Reborn a déjà :** l'échoppe, mais pas de marchand ambulant. **Faisabilité** : marchand ambulant à stock tournant, **facile à moyen**
(Citizens / MythicMobs + boutique du mod). Greffe d'ADN : moyenne, et **à trancher** pour le lore.

## 14. Sauvegarde des données joueur

**Pas de capability Forge** pour ces systèmes : tout est rangé dans les **`getPersistentData()` du joueur** (NBT), sous des clés
préfixées et versionnées. Exemples : `battle_experience`, `nydomod:missions_v2`, `nydomod:villages_v1`, `nydomod:jutsu_points`,
`nydomod:rpg_stats` (schéma 3), `nydomod_hsts`, `ShinobiPreparation`, `nydomod:organizations_v1`, `NydomodTutorial` (sous
`PlayerPersisted`). Chaque module recopie ses données à la mort (`Clone` event, parfois selon une gamerule
`keepChakra`). Les données de monde vont dans des `SavedData` (`nydo_custom_npcs`, `nydomod_clanes`). Des **migrations** sont prévues (missions v1 → v2,
`SchemaVersion`).

**Pour Reborn** : ShinobiCore stocke déjà un YAML par joueur **et par personnage**. Le point à retenir est la **règle « une clé
versionnée par module + migration »**, et le fait que tout état de mission doit être **par personnage** (pas par compte),
sinon il fuit d'un personnage à l'autre.

## 15. Commandes (command/)

Côté admin (OP 2) : `/ninja` (xp / points / chakra / jutsu unlock / affinités…), `/wallet get|add|set|take`, `/nydovillage
get|status|reputation`, `/nyclan` (joueurs), réputation de clan, `/hsts reset_learned|reset_blocked`, `/nydo worldgen locate`,
`/sharinganxp`, `/susanooxp`, `/nydotutorial explain|skip`. Il n'y a **pas de commande de mission** : tout passe par l'interface admin (§7).
**Pour Reborn**, le staff préfère les commandes et le Poste de garde : prévoir `/mission donner|valider|annuler|voir <perso>` en plus de
l'écran.

## 16. Inspect (inspect/)

Un **panneau de cible** au HUD (`InspectProvider` par priorité, `InspectTargets`) : type (joueur / boss / ninja / créature), état
(au repos, en combat, « moule son chakra », genjutsu…), menace (faible / moyenne / élevée), arme, distance ; **avec un dōjutsu, on en voit
plus** (réseau de chakra stable / tendu / scellé avec le Byakugan, tomoe et technique en cours avec le Sharingan, `DojutsuEye`).
Le panneau se règle dans l'éditeur de HUD. Le texte crédite un autre auteur (« used with permission »).

**Intérêt RP.** Moyen. La **perception enrichie par le dōjutsu** est un bon bonus de clan. Un panneau qui affiche « menace : élevée » sur
les joueurs va en revanche à l'encontre du RP. **Reborn a déjà :** le Byakugan « comme dans l'anime » (ShinobiSense). Faisabilité : moyenne
(le serveur envoie l'état de la cible, le panneau se fait dans reborn-hud), **réservé aux porteurs de dōjutsu**.

---

## Tableau de synthèse

| Idée | Ce que fait Nydo | Reborn a déjà ? | Valeur RP | Effort |
|---|---|---|---|---|
| Moteur de missions à objectifs | 6 types (KILL, FETCH, LOCATION, ESCORT, DIALOGUE, TRIAL), 16 objectifs, prérequis, chaînage, répétition avec délai | Non (idées seulement, `MISSIONS.md`) | ★★★ | Lourd (lot 1 moyen) |
| Missions créées par le staff en jeu | Missions ADMIN stockées dans le monde, intégrité des références | Non | ★★★ | Moyen |
| PNJ donneur + dialogues à conditions | 8 options, actions accepter / rendre / boutique, conditions mission / jour-nuit / déjà lu | Non | ★★★ | Moyen (Citizens + plugin) à lourd |
| Rang de mission ↔ grade / réputation | Niveau min D0 → S70, réputation C100 → S650 | Grades RP oui, pas de lien | ★★★ | Facile (une fois le moteur fait) |
| Réputation par village | −1 000 → +1 000, recherché 60 s, services coupés à −40 | Non | ★★★ | Facile à moyen |
| Rachat de réputation | Mission « restitution » | Non | ★★ | Facile |
| Carte de route qui lance l'arc d'arrivée | Carte trouvée en coffre → mission → carte révélée | Carte M sans marqueurs d'objet | ★★ | Moyen |
| Scènes de dialogue scénarisées | Iruka / Hokage, une scène par étape | Non | ★★ | Moyen |
| Épreuves scriptées (cloche de Kakashi) | TRIAL : 90 s, approche de dos, rester caché 1 s | Mini-jeux de séance oui | ★★ | Moyen par épreuve |
| Escorte de PNJ | Apparition, suivi, échec si le PNJ meurt | Non | ★★ | Lourd |
| GPS et HUD de mission | Points de route (32), bandeaux de progression | Non | ★★ | Moyen |
| Rapport / état de service | Onglet Missions dans la Ninja Card | Format de rapport PNG fait à la main | ★★ | Facile après le moteur |
| Tutoriel validé par le serveur | 12 étapes, panneau « pourquoi c'est utile », skip / explain | Non | ★★ | Moyen |
| Codex débloquable | ~40 entrées liées, déblocage par XP / contexte | Wiki web | ★ | Moyen |
| Max Jutsu / Préparation | 6 disciplines × 4 sceaux, 3 préréglages | Non documenté | ★★ | Moyen |
| Mudras mémoriser → reproduire | 3 vies, parchemin perdu en cas d'échec | Oui (séances avec mudras) | ★ | — (déjà là) |
| Pénalité sans affinité | Signes en plus, −10 % de chakra, blocage | Non (apprentissage libre) | ★ | Facile |
| Présentoir à parchemin | S'entraîner sur un parchemin posé | Prévu (meuble Nexo) | ★ | Moyen |
| Points de jutsu à coût croissant | 1 puis achats + 1 | Non, contraire à la règle | ✗ | — |
| Rang automatique par niveau | Académie < 10 … Kage 100 | Grades RP (décision contraire) | ✗ | — |
| XP de combat | Dégâts infligés / reçus, 60 / s | XP de présence, entraînement en zone (décision contraire) | ✗ | — |
| Ryo physique + portefeuille-objet | Pièces 100 → 1 M, journal de 12 opérations, volable | Ryo dans l'échoppe, sans source | ★★★ | Facile à moyen |
| Boutiques configurables (troc) | RYO / pièces / objets ×2 | Échoppe fixe | ★★ | Moyen |
| Marchand ambulant rare (Kabuto) | 10 s, 0,1 à 1,8 %, stock 4 à 6, cash uniquement | Non | ★★★ (si piloté par le staff) | Facile à moyen |
| Greffe d'ADN risquée | Plafond 90 %, effets de rejet, nature GRAFT | Non (clans sur candidature) | ★★ (à trancher) | Moyen |
| Organisation qui recrute les mal vus | Akatsuki : recrute ≤ 10, hostile > 40 | Design Faction pas commencé | ★★ | Facile (avec la réputation) |
| Sous-clans / familles | 15 membres, invitation par parchemin, buff de proximité | Non | ★★ | Moyen |
| Clan tiré au hasard | Pondéré, Commun 36 … Uchiha 3 | Choix + candidature | ✗ | — |
| Carte-objet à destinations | Atlas papier, marqueurs selon la carte, suivi des biju | Carte M | ★★ | Facile à moyen |
| Panneau de cible via dōjutsu | Réseau de chakra / tomoe visibles avec un dōjutsu | Byakugan (ShinobiSense) | ★★ | Moyen |
| Village généré + protection | Konoha procédural, fixe, cassage interdit | Map construite à la main | ✗ | — |
| PNJ habitants avec rôle | Garde / service / patrouille / habitant / mannequin, réapparition 600 s | Non | ★★ | Moyen (Citizens) |
| Données versionnées par module | Clés NBT `_vN` + migrations + recopie à la mort | YAML par personnage | ★ (bonne pratique) | Facile |
