# Étude technique — Ninshu Origins 2.1.7 (Minecraft 1.20.1 / Forge)

> Rétro-ingénierie à usage privé et légitime : l'utilisateur possède le jar et gère un serveur RP Naruto.
> Objectif : comprendre les mécaniques et récupérer des références de modèles/animations 3D.
> **Aucune redistribution.** Analyse + référence d'assets uniquement.
>
> Sources : décompilation CFR de `net.saberart.ninshuorigins.*` (2 239 classes → 1 523 `.java`),
> `META-INF/mods.toml`, `assets/ninshuorigins/lang/en_us.json` (913 clés), datapack `data/ninshuorigins/`.
> Chaque affirmation cite une classe/fichier réellement observé.

---

## 1. Architecture générale

**Entrypoint** — `net.saberart.ninshuorigins.NinshuOrigins` (`@Mod("ninshuorigins")`). Le constructeur enregistre, sur le MOD event bus : `NinshuRegistry.register`, `CreativeTabs`, `ModStructurePieces`, `ModStructures`, la config Forge (`Config.SPEC`), et appelle `GeckoLib.initialize()`. `commonSetup` déclenche `PacketRegistry.register`, `QuestRegistry.init`, l'enregistrement des gamerules (`ModGameRules.register`) et le spawn placement de l'entité `NINJA`.

**Dépendances** (`mods.toml`) :
- `forge [47,)`, `minecraft [1.20.1,1.21)`
- `geckolib [4.4,)` — modèles/animations 3D (entités, armures, armes, susanoo, bijû).
- `photon [1.0.7,)` — moteur de particules/FX (auteur LDLib, style GTCEu).
- `ldlib [1.0.40,)` — lib UI/registry/data.
- **PlayerAnimator (kosmx)** — présent en code (`PlayerAnimationFactory`, `ModifierLayer`, `IAnimation` dans `client/entity/player/Animator.java`) pour les animations *sur le joueur vanilla*, non listé dans mods.toml (embarqué/transitif).

**Registres** — `common/registry/NinshuRegistry` agrège les `DeferredRegister` (items, blocs, entités, sons, particules, menus, enchantements). Registre custom notable : `JutsuRegistry` crée un `IForgeRegistry<NinshuJutsu>` propre (clé `ninshuorigins:jutsus`, `setHasWrapper(true)`).

**Stockage des données par joueur — le cœur du mod** : une **unique Forge Capability** `Player_Capability.PLAYER_VARIABLES` (`common/capability/Player_Capability.java`). Toutes les données RP (chakra, clan, village, dojutsu, XP, modes…) vivent dans **un seul `CompoundTag data`** (~250 clés). Le schéma est déclaré dans `PlayerVariables.getDefaultData()` via `setData(map, "Nom", Type, défaut, sync)` : chaque clé porte un type Java (`Integer/Boolean/String/Double/Long/Float/CompoundTag`), une valeur par défaut, et un flag **`Sync`** (certaines clés serveur-only ne sont pas envoyées au client).
- Attachement : `PlayerVariablesProvider` (`ICapabilitySerializable<Tag>`) attaché sur `AttachCapabilitiesEvent<Entity>` à tout `Player` non-`FakePlayer`.
- Sérialisation : `writeNBT()` (tout) vs `writeNBTPacket()` (uniquement clés `Sync=true`).
- Sync réseau : `PlayerVariables.sync()` envoie `SyncPlayerCapabilityPacket` en `PacketDistributor.TRACKING_ENTITY_AND_SELF` (donc les autres joueurs voient l'état visuel : dojutsu, modes…).
- Cycle de vie : re-sync sur `PlayerLoggedIn`, `Respawn`, `ChangedDimension` ; `PlayerEvent.Clone` recopie la capability à la mort **sauf** si gamerule `resetCapOnDeath` actif (alors reset total + redonne `STARTER_ITEM` + `SCISSORS`). À la mort non-reset, chakra/stamina sont remis au max et les toggles (charge, contrôle) coupés.

**Networking** — `SimpleChannel` Forge `ninshuorigins:ninshuorigins` (protocole "1"), enregistré dans `PacketRegistry`. ~60+ paquets `common/network/CS*`/`SC*`. Le modèle d'interaction passe presque entièrement par des paquets C→S déclenchés par des touches (voir §3).

**GeckoLib + PlayerAnimator** — GeckoLib pour tout ce qui est *entité/objet rendu* (renderers sous `client/entity/jutsu/**`, `client/item/geo/**`, susanoo, bijû). PlayerAnimator pour les *gestes du corps du joueur* (dash, saut double, finishers, mudras) via `Animator.registerPlayerAnimations` (couche `ModifierLayer` + `WalkAnimationSpeedModifier`, crossfade/fadeout modifiers présents).

---

## 2. Système de chakra

Clés capability (`Player_Capability`) : `Chakra` (courant, défaut 0), `MaxChakra` (défaut **100**), `Charge_Chakra` (bool, mode recharge actif), `ChakraControl` (bool, technique de contrôle marche-sur-l'eau/murs), plus un pilier « nature » : `NatureChakra`/`MaxNatureChakra` (défaut **5**) et `Charge_NatureChakra` (recharge du chakra senjutsu pour le mode ermite), et `Stamina`/`MaxStamina` (100) + `StaminaRegenDelay` pour le taïjutsu.

**Consommation** : `NinshuJutsu.executeCost()` appelle `ChakraUtils.consume(entity, cost)` (ou `NinjaEntity.consumeChakra` pour les PNJ). Une technique ne s'exécute que si `executeCost` réussit ET si pas de cooldown (`onCooldown`).

**Recharge** : touche « Charge Chakra » (`key.ninshuorigins.charge_chakra`) → `CSPacketChargeChakraPressed` bascule `Charge_Chakra`. Un handler de tick régénère tant que le flag est actif (le joueur reste immobile, animation + FX `client/fx/ChakraChargeEffect`). Idem « Charge Nature Chakra » (`CSPacketChargeNatureChakraPressed`) pour remplir `NatureChakra` (pré-requis du Sage Mode).

**Blocages** : `Chakra_Blocked` + `Chakra_Block_EndTick` (Gentle Fist Hyûga bloque les points de chakra → empêche l'usage de jutsu un temps). `MemoryBlockEndTick` (genjutsu d'amnésie) empêche aussi d'exécuter une technique (`NinshuJutsu.execute` refuse : *"You can't remember how to use this jutsu…"*).

---

## 3. Système de jutsu / techniques

**Data-driven ? Non — codé.** Chaque technique est une sous-classe de `common/jutsu/NinshuJutsu` (**53 sous-classes** recensées) enregistrée dans le `IForgeRegistry<NinshuJutsu>` via des initialiseurs par nature : `FireRelease.init`, `WaterRelease.init`, `WindRelease.init`, `LightningRelease.init`, `EarthRelease.init`, `Biju.init` (`common/registry/jutsu/*`, appelés par `JutsuRegistry.init`). `NinshuJutsu` expose : `execute()` (garde-fous), `isUnlocked()`, `executeJutsu()` (effet réel), `onCooldown()`, `getCost()`, `getJutsuType()` (`Jutsu.Enum`), `getIconLocation()`.

**Catégorisation** — `common/data/Jutsu.Enum` (NINJUTSU par défaut ; catégories par nature FIRE/WATER/WIND/LIGHTNING/EARTH/YIN/YANG + TAIJUTSU/KENJUTSU/GENJUTSU). `JutsuRegistry.typeForNature(Natures.Enum)` mappe une nature élémentaire → catégorie de jutsu. `getAllForType` / `getRandomForType` permettent aux PNJ de piocher.

**Déclenchement (joueur) — pas de hand-seals en gameplay, c'est une barre scrollable** :
- `key.next_jutsu` / `key.prev_jutsu` → `CSPacketSwitchJutsuPressed` : cycle la technique active.
- `key.next_release` / `key.prev_release` → `CSPacketSwitchReleasePressed` : cycle la *catégorie* (release/nature) active.
- `key.use_jutsu` → `CSPacketJutsuPressed` : exécute la technique sélectionnée.
- `key.subuse_jutsu` → `CSPacketSubJutsuPressed` : jutsu de substitution (kawarimi ; `CanSubstitute`/`LastSubTime`).
- Overlay HUD : `client/gui/overlays/JutsuOverlay` affiche la technique/nature courante.

Les techniques débloquées sont mémorisées par nature dans des **chaînes CSV** de la capability : `Unlocked_Fire_Jutsu`, `Unlocked_Water_Jutsu`, … `Unlocked_Uchiha_Jutsu`, `Unlocked_Rinnegan_Jutsu`, `Unlocked_Akimichi_Jutsu`, etc. (une clé par arbre). On débloque via des items « parchemins » `release_*` (voir §7) et l'UI « Releases » (`CSPacketOpenReleases`).

**Scaling** — coût fixe par technique (`getCost`) ; dégâts modulés par les stats (`Ninjutsu`, `Genjutsu`, `Taijutsu`, `Kenjutsu`, `Medical`, `Senjutsu`… dans la capability) et par `strength` (durée d'appui) passé à `execute(entity, world, strength)`. Beaucoup de techniques spawn une **entité GeckoLib** dédiée (ex. `earthdragon`, `dragonflame`, `waterdragonbullet`, `tengaishinsei`).

**Taïjutsu / Kenjutsu (combat au corps-à-corps, priorité Reborn)** :
- `TaijutsuReleaseItem` / `KenjutsuReleaseItem` : techniques à **hitbox tick-timée synchronisée à l'animation**. Ex. *Leaf Whirlwind* = 3 coups aux ticks 2/12/16 (tags NBT `LeafWhirlwindHit1..3`), *Thousand Years of Death* au tick 26 ; hitbox AABB autour du joueur, filtrage ami/ennemi via `SquadCombatRules.canDamage`.
- Coûts en **stamina** (`tryUseStamina`), pas en chakra.
- Système de **garde** : `isBlocking`, `BlockTicks`, `GuardBreakCooldown` (capability) ; touche `key.taijutsu_block` (`CSBlockPacket`), `key.toggle_taijutsu` (`CSTaijutsuPacket`).
- **Grab/agrippe** : `TaijutsuGrabHandler` + `CSTaijutsuGrabPacket` (finishers `weapon_finisher`/`_victim` en player_animation).
- Progression dédiée : `TaijutsuXp`, `KenjutsuXp` (voir §7). Bloc d'entraînement `taijutsu_mat`.

**Substitution / esquive** : `CanSubstitute` + kawarimi (téléporte + laisse une bûche — modèle `summons/substitution`).

---

## 4. Clans & génétique (ADN / hérédité)

**36 clans** exposés via les items `dna_clan_*` : aburame, akimichi, chinoike, fuma, funato, hagoromo, hatake, hozuki, hyuga, iburi, inuzuka, izuno, jugo, kagetsu, kaguya, kamizuru, kedoin, kohaku, kurama, kuronosu, lee, nara, otsutsuki, rinha, ryu, sarutobi, senju, shiin, shimura, shirogane, tsuchigumo, uchiha, uzumaki, wagarashi, wasabi, yamanaka (`common/data/Clan.Enum`).

**Modèle d'ADN** — La capability stocke `Clan` (id du clan « actif ») **et** `ClanDNA` (String CSV d'ids de clans possédés). Le mod a même une **migration** (`migrateClanDNAStorage`) qui convertit l'ancien format `CompoundTag`/`clan_*` vers la chaîne CSV normalisée (ids séparés par virgule) — preuve d'un système d'hérédité multi-clan accumulable.
- Item `dna_bottle` : flacon d'ADN générique ; les `dna_clan_X` / `dna_release_X` sont des échantillons.
- On acquiert un kekkei genkai/dojutsu en « débloquant » à partir de l'ADN correspondant (les tooltips `gui.dojutsu.*_locked` répètent *"Acquire X Clan DNA and unlock it through the Jutsu Bar"*).
- Certains clans ont une logique dédiée : `common/clans/yuki/YukiClanPassiveEvents` (Hyôton passif), `common/clans/uchiha/sharingan/Izanagi`.

**Kekkei genkai / natures avancées** — enum `Natures.Enum` : 5 bases (EARTH/FIRE/WIND/LIGHTNING/WATER) + YIN/YANG + kekkei genkai composites via `dna_release_*` : blaze, boil, crystal, dark, dust, ice, lava, magnet, mud, scorch, steel, storm, swift, typhoon, wood, explosion (23 « releases » au total). Stockées dans `Nature_Proficiencies` (CSV) + `KG` (kekkei genkai).

---

## 5. Dôjutsu

Enum `common/data/dojutsu/Dojutsu.Enum` (chaque valeur = une instance `Dojutsu_Base` + item œil) : **SHARINGAN, MANGEKYO, ETERNAL_MANGEKYO, JOGAN, SHION, RANMARU, RINNEGAN, BYAKUGAN, KETSURYUGAN, TENSEIGAN, SENRIGAN, YOME, KOKUGAN**. Classes d'implémentation dédiées : `Byakugan`, `Sharingan`(via items), `Mangekyo`, `EternalMangekyo`, `Rinnegan`, `Jogan`, `Ketsuryugan`, `Kokugan`, `Ranmaru`, `Senrigan`, `Yome`, `Tenseigan`, `Shion`, + `DojutsuBuffs`.

**Stockage** : la capability modélise **chaque œil séparément** — `Left_Eye` / `Right_Eye` (CompoundTag avec `TYPE`, `TYPE_ID`, `TOMOES`), `Has_Left_Eye` / `Has_Right_Eye` (permet l'arrachage d'œil, touche `key.pluckeye` → `C2SEyePluckStartPacket`, item `eye_bandage`), plus des flags `*_Unlocked` (`Sharingan_Unlocked`, `Mangekyo_Unlocked`, `Eternal_Unlocked`, `Rinnegan_Unlocked`, `Byakugan_Unlocked`, `Tenseigan_Unlocked`, `Senrigan_Unlocked`, `Ketsuryugan_Unlocked`, `Kokugan_Unlocked`, `Yome_Unlocked`, `Jogan_Unlocked`, `Ranmaru_Unlocked`, `Shion_Unlocked`, `Rinnesharingan_Unlocked`) et des types (`Tomoe_Amount`, `Mangekyo_Type`, `Eternal_Type`, `Rinnegan_Type`, `Byakugan_Type`). `EMS_Eligible`/`Monogon_Unlocked` gèrent l'EMS (transplant d'un 2e Mangekyô).
`DojutsuUtils.isDojutsuUnlocked(vars, id)` teste l'état. Activation : `key.dojutsu` → `CSPacketDojutsuPressed` (`Dojutsu_Active`).

**Conditions de déblocage (lang `gui.dojutsu.*_locked`, preuves textuelles)** :
- **1 tomoe (Sharingan)** : ADN Uchiha + activation via Jutsu Bar (ou Sharingan Implant).
- **2 tomoe** : Uchiha 8 kills / 80 genjutsu / 30 min sharingan actif ; non-Uchiha 10 / 100 / 35 min.
- **3 tomoe** : Uchiha 75 / 300 / 75 min ; non-Uchiha 100 / 450 / 90 min.
- **Mangekyô** : Uchiha 150 kills / 750 genjutsu / 150 min ; non-Uchiha 400 / 1000 / 300 min (ou implant).
- **Eternal Mangekyô** : transplanter un 2e Mangekyô (ou implant).
- **Rinnegan** : évoluer au-delà du Mangekyô sous conditions spéciales (ou implant).
- **Rinne-Sharingan** : posséder Rinnegan **et** Mangekyô + conditions (ou implant).
- **Byakugan** : ADN clan Hyûga + Jutsu Bar (ou implant). **Ketsuryugan** : ADN Ketsuryugan (ou implant).
- **Jôgan/Tenseigan/Senrigan/Kokugan/Shion/Yome/Ranmaru** : uniquement par « Implant » (item œil greffé) à ce stade.

Le tracking du temps Sharingan est un handler de tick (`Player_Capability.SharinganTimeTracker.onPlayerTick` : +1 min toutes les 1200 ticks quand le sharingan est débloqué).

Rendu : `client/entity/player/renderlayers/ByakuganChakraPointsGeoLayer`, `ByakuganPostChakraRenderer` (vision chakra), overlays d'œil.

---

## 6. Modes / transformations

Implémentation dominante : **flags d'état dans la capability + layer d'armure GeckoLib rendu par-dessus le joueur** + attributs temporaires (vitesse/PV/dégâts) + FX Photon.

- **Sage Mode** (`key.sagemode` → `CSPacketSageModePressed`) : `Sage_Mode`, `Sage_Mode_Type` (-1 = aucun), pré-requis `NatureChakra` chargé ; transition animée (`TransitioningIn/Out`, `SageModeTransitionStart`). Cloaks : `narutotoadsagecloak`, `narutosagecloak`, `sagescroll`.
- **Mode chakra jinchûriki** (`key.activate_chakramode` « Chakra Cloak Mode (Jin) ») : bloc de clés `Jin*` très riche — `Jin` (id du bijû, -1 sinon), `JinXP`, `JinMastery`, `JinBond`, `JinChakraMode_Type/_Active`, `JinRage`/`JinRageStage`/`JinRageTimeLeft` (mode berserk), `JinToggleJinStage` (`CSToggleJinStage`). `TailedBeastTracker.isJin/getJinID` détermine si le joueur porte un bijû. Modèles : `fox_barion`, `saiken_barion`, `chomei_baryon`, `baryonshukaku` (**Baryon Mode** Kurama + versions autres bijû).
- **Butterfly Mode (Akimichi)** : `AkimichiButterflyModeActive` + calories (`AkimichiCalories`/`MaxAkimichiCalories` 10000), agrandissements (`AkimichiMultiSizeActive`, `AkimichiSuperMultiSizeActive`, `AkimichiRollActive` = char à billes humaines), item `butterfly_mode`, modèle `armor/butterflymode`.
- **Huit Portes (taïjutsu)** : `Gates` (int 0-8), `fistrock` (Guy).
- **Karma / Cursemark** : `Karma_Type`/`Karma_Active` (Ôtsutsuki, `key.activate_mark` → `CSMarkActivationPressed`) ; `Cursemark_Type/_Stage/_Unlocked/_Active` (marque maudite Orochimaru, `common/data/Cursemarks`).
- **Susanoo** : `susanooribcage`(cage thoracique) → `sasukeskeletal`/`itachi` complet, saut dédié (`key.susanoo_jump` → `CSJumpSusanno`), rendu GeckoLib entité.
- **Transformation Wheel** (`key.transformationwheel`) : roue radiale pour choisir la transfo active.

---

## 7. Progression

**Grille de stats (capability)** : `Ninjutsu`, `Genjutsu`, `Taijutsu`, `Kenjutsu`, `Shurikenjutsu`, `Summoning`, `Medical`, `Senjutsu`, `Kinjutsu`, `Mitejutsu`, `Intelligence`, `Wit`, `SpeedStat`, `StaminaStat`. Points à dépenser : `SP/JP/MP/KP`. `CSPacketStatChange` / `C2SSaveProficiencies` sauvegardent la répartition.

**XP par voie** (`gui.level_page.*`) : **Ninja** (`NinjaXp`/`NinjaLevel`), **Taijutsu** (`TaijutsuXp`), **Kenjutsu** (`KenjutsuXp`), **Summoning** (`SummoningXp`), **Wit** (`WitXp`), + « Speed Training » (`SpeedProgress`, entraînement passif à la course — `WeightedBootsProgress` avec les bottes lestées). Règles d'XP : `common/data/SkillXpRules`.

**Rangs ninja** : `common/entity/ninja/NinjaRank` = **E, D, C, B, A, S, SS, Z** (utilisé pour le tirage de natures des PNJ, cf. §« Natures.rollNatureCount(rank) »). `RankXP`/`MaxRankXP`.

**Économie & activité** : `Ryo` (monnaie ; items `currency/`, ATM `atm.animation`, recettes de change yen↔ryo), `ActivityXP`/`MaxActivityXP` (récompenses quotidiennes, `C2SClaimActivityRewardPacket`, `LastActivityReset`), quêtes (`common/data/quests/QuestRegistry`, `Quests`, escouades `Squad*`).

**Apprentissage de techniques** : items `release_*` (~110, dont `release_taijutsu`, `release_kenjutsu`, `release_medical`, `release_ninjutsu`, `release_genjutsu`, `release_fire`…`release_wood`, + un `release_<clan>` par clan) = « Learner » qui débloque un arbre → alimente les CSV `Unlocked_*_Jutsu`. `Learners` (compteur), `Proficiency_Chosen`/`Proficiency_Mask`.

**Config & gamerules** : `Config.java` est quasi-vide (boilerplate Forge). Le vrai réglage serveur = `ModGameRules` : `resetCapOnDeath` (RESET_CAP_ON_DEATH — wipe RP à la mort) et `squadFriendlyFire` (SQUAD_FRIENDLY_FIRE — tir allié en escouade).

**Mort / down** : `Downed`/`Downed_Timer` (état à terre facon battle-royale), `ReviveProgress`/`SelfReviveProgress`, `key.giveup` (`C2SGiveUpPacket`), `Deaths`/`Kills`, damage type `bled_out`, `medical_bed` (réanimation). Bonus PV via stat `Medical` (modifier d'attribut au respawn).

---

## 8. Contenu monde

- **Dimensions** (`data/.../dimension/`) : `mount_myoboku` (Mont Myôboku, réalm des crapauds/Sage Mode) et `pocket` (dimension de poche — probablement Kamui/scellement). + `dimension_type/` (3).
- **Worldgen** (`worldgen/`, 11 fichiers) : structures `travel_carts` (`brokencart`, `foodcart`, `honeytravelcart`, `travelcart`…) et `ichurakuramen` (le stand de ramen Ichiraku), event `christmas_present`. Structures NBT sous `structures/` (8).
- **Loot tables** : 36. **Recipes** : 40 (crafting standard : `steel*` acier, `weighted_boots`, `syringe`, `storybook` + toute la table de change monétaire yen/ryo).
- **Damage types** : `bled_out` (+ générique).
- **Entités** : entité `NINJA` (PNJ ninja avec `NinjaRank`, IA, spawn), invocations (21 modèles), bijû (9), NPC nommés (13), nombreuses entités-jutsu.
- **Tags** (7), **forge** compat (3).

---

## 9. Inventaire des assets (comptes + catégories)

Détail exhaustif dans `D:\Téléchargement - ALL\ninshu-extracted\INDEX.md`. Synthèse :

| Type | Nombre | Format |
|---|---|---|
| **geo** (modèles 3D GeckoLib) | **316** | bedrock geometry |
| **animations** (keyframe GeckoLib) | **271** | entités/blocs/armes |
| **player_animation** (kosmx) | **141** | gestes joueur |
| **models** (item/bloc vanilla) | **704** | JSON `parent`+`textures` |
| **textures** (restées dans le jar) | 1 879 PNG | — |
| particles | 2 | Photon |
| shaders | 6 | post/FX |

**geo — regroupement thématique** :
- **Armures/tenues (106)** — la catégorie phare pour Reborn. Uniformes de **Kage** (hokage/raikage/mizukage/tsuchikage/kazekage/hoshikage/amekage/yukikage…), **jônin par village** (cloud/mist/rain/sand/snow/star/stone-jonin), **ANBU** (`anbuarmor`, `anbumask`), **manteaux Akatsuki** (`akatsukirobearmor`, `akatsukihat`, `reanimatedcloak`, `uchiharobearmor`), **layers de modes** (`baryonshukaku`, `fox_barion`, `chomei_baryon`, `butterflymode`, `fistrock`), **masques/cosmétiques event** (`tobimask`, `shinigamimask`, `pumpkinmask*`, `michaelmyers`, `screammask`, `jasoncostume`, `scarecrow`), + costumes de dizaines de personnages nommés.
- **Armes (75)** — katanas légendaires (`kusanagi`, `kubikiribocho`, `samehada`, `hashiramasword`), 7 épées ninja de la Brume (`nuibari`, `kabutowarihammer`, `hiramekarei`, `shibuki`, `kokinjo`), armes de zone (`gunbai`, `bashosen`, `sixpathstaff`), outils (`kunaiblade`, `fumashuriken`, `wristsmokelauncher`, `temarifan`).
- **Invocations (21)** — clan des crapauds Myôboku (`gamabunta`, `gamakichi`…), `slug` (Katsuyu), `salamander`, `akamaru`, `threegiantsnakes`, `substitution` (la bûche kawarimi).
- **Bijû (9)** — les 9 démons à queues (`shukaku`→`kurama`).
- **Susanoo (2)**, **entités-jutsu (22)**, **NPC (13)**, **blocs/mobilier (~26)**, **items 3D (23)** (ramen, dango, parchemins, seringue…).

**player_animation (141)** — format **PlayerAnimator kosmx**, identique à la pipeline emote Reborn (PAL) → réutilisables quasi tels quels : `weapon_finisher(_victim)`, dashs, sauts, mudras, `waterhidinginwaterout`, etc.

---

## 10. Idées à emprunter pour Reborn (priorisé)

Mapping concret sur la roadmap Reborn (character-creation, tirage de la feuille, combat taïjutsu, dojutsu, clans/villages, modes, cosmétiques 3D, inventaire). Difficulté = effort d'intégration côté ShinobiCore + mod-hud.

| # | Idée Ninshu | Ce que Ninshu fait | Ce qu'on prend/adapte pour Reborn | Difficulté |
|---|---|---|---|---|
| **1** | **Modèle « une capability = un CompoundTag à schéma déclaratif »** | `getDefaultData()` déclare ~250 clés (nom/type/défaut/**sync**) dans une seule capability, sync sélectif `TRACKING_ENTITY_AND_SELF`, migrations intégrées, reset-on-death par gamerule | Le **squelette exact** de la persistance perso ShinobiCore : table de champs typés + flag de sync + politique mort/respawn. On a déjà le backend multi-perso ; adopter ce pattern déclaratif + sync partiel évite la plomberie paquet-par-paquet | **Faible** (archi, pas de contenu) |
| **2** | **Tirage de nature de chakra pondéré par rang** | `Natures.rollNatureCount()` : 1 nature 64,5 % / 2 → 25 % / 3 → 8 % / 4 → 2 % / 5 → 0,5 % ; variante `rollNatureCount(NinjaRank)` re-pondère par rang E→Z | **Directement branchable sur le « tirage de la feuille » Reborn (touche F) existant** : reprendre la courbe gacha exacte + le boost par rang comme récompense de progression. On tire nature(s) parmi EARTH/FIRE/WIND/LIGHTNING/WATER (+ chances kekkei) | **Faible** (déjà un proto Reborn) |
| **3** | **Combat taïjutsu à hitbox tick-timée sur animation** | Techniques = série de coups à ticks fixes (2/12/16/26), AABB autour du joueur, coût **stamina**, `SquadCombatRules.canDamage` pour l'ami/ennemi, système garde `isBlocking`/`BlockTicks`/`GuardBreakCooldown` + grab/finisher | **Le cœur du PvP taïjutsu M1/M2 fluide** visé par Reborn : timeline de hitbox synchro anim + garde + guard-break + finisher (grab). Modèle propre et copiable ; à câbler sur PlayerAnimator (déjà maîtrisé côté emote) | **Moyenne-élevée** |
| **4** | **Cosmétiques 3D en layer d'armure GeckoLib** (100+ tenues) | Uniformes village/Kage, manteaux, masques rendus en RenderLayer par-dessus le joueur, pilotés par un flag capability | **Système bandeau/tenue de village + cosmétiques** Reborn : porter un manteau/ANBU/masque via RenderLayer. On a déjà Nexo + inventaire cosmétique ; ces **316 geo** sont une bibliothèque de référence de topologie/rigging (textures tirables à la demande) | **Moyenne** |
| **5** | **Dôjutsu par œil individuel + conditions de déblocage chiffrées** | `Left_Eye`/`Right_Eye` (TYPE/TOMOES), flags `*_Unlocked`, tracking temps/kills/genjutsu, arrachage d'œil + implant, layer vision chakra Byakugan | Progression dôjutsu Reborn : **œil G/D séparés** (permet vol/greffe d'œil = RP fort), déblocage par métriques (kills/temps actif) plutôt que par simple achat. Reprendre les seuils comme base d'équilibrage | **Moyenne** |
| **6** | **Barre de jutsu scrollable (release ↔ jutsu ↔ use)** au lieu de hand-seals | 4 touches : cycle release, cycle jutsu, use, substitute + overlay HUD ; déblocage en CSV par arbre | UX d'activation de techniques simple et lisible pour un serveur RP grand public. À intégrer au mod-hud (overlay jutsu). Option : **garder les hand-seals en surcouche cosmétique** (anim de mudras PlayerAnimator) sans les rendre obligatoires | **Faible-moyenne** |
| **7** | **Modes/transformations = flags + layer + attributs temporaires** | Sage/Baryon/Butterfly/Gates/Susanoo : bool capability → RenderLayer + modifiers d'attributs + FX + transitions animées | Patron générique « mode actif » réutilisable pour toute transfo Reborn (voie/style versatile). Séparer proprement état (serveur), visuel (layer), buff (attribut), FX | **Moyenne** |
| **8** | **ADN de clan multi-source (CSV) + hérédité** | `ClanDNA` = liste d'ids cumulables, `Clan` actif distinct, 36 clans, `dna_clan_*`/`dna_bottle`, migration de format | Système **clans/villages** Reborn (lockés depuis la whitelist) : un perso peut porter plusieurs lignées, en activer une ; l'ADN comme objet RP échangeable/transmissible. Reprendre la liste de 36 clans comme catalogue de départ | **Moyenne** |
| 9 | Système de mort « downed / revive / bleed-out » | `Downed`+timer, revive/self-revive, give-up, `bled_out`, medical bed, bonus PV via stat Medical | Complète le système mort/RPK de ShinobiCore : phase « à terre » réanimable → RP médical (déjà une voie « médecin » possible) | Moyenne |
| 10 | Speed/Weight training passif | `SpeedProgress` monte en courant, `weightedBoots` = bottes lestées qui accélèrent l'entraînement | Progression physique passive gratifiante et RP (entraînement) sans grind actif | Faible |
| 11 | Économie Ryo + ATM + change | Monnaie `Ryo`, coins craftés, ATM (bloc), recettes de change | Base d'économie serveur simple si Reborn veut une monnaie IG | Faible |
| 12 | Invocation « substitution » (kawarimi) | Téléport + laisse un modèle bûche | Mécanique d'esquive signature, très lisible, peu coûteuse à porter | Faible |
| 13 | Système d'escouades + friendly-fire réglable | `Squad*` quests/quest-projection, gamerule `squadFriendlyFire`, `SquadCombatRules.canDamage` | Gestion d'équipes/factions pour le PvP RP (villages), FF configurable | Moyenne |
| 14 | Roue de transformation (radial menu) | `key.transformationwheel` | UX radiale pour switcher voie/mode/jutsu — cohérent avec le menu Reborn | Faible (UI) |
| 15 | Réutilisation directe des **player_animations kosmx** | 141 anims PlayerAnimator | Pipeline emote Reborn = **même format** → import quasi direct de dashs/sauts/finishers comme base à re-styliser | Faible |

### Priorité recommandée
1. **#1 (archi capability déclarative)** — fondation, débloque tout le reste.
2. **#2 (tirage nature)** — quick win, s'appuie sur le proto « tirage de la feuille » déjà live.
3. **#3 (combat taïjutsu tick-timé)** — LE morceau prioritaire de la roadmap IG Reborn.
4. **#6 (barre de jutsu)** + **#4 (cosmétiques layer)** — UX + contenu visible immédiat.
5. **#5 / #7 / #8** (dôjutsu, modes, clans) — profondeur RP à moyen terme.

---

## Points ambigus / non vérifiés
- La **version exacte de PlayerAnimator** et son mode d'embarquement (jarjar/transitif) n'apparaissent pas dans `mods.toml` ; confirmé uniquement par les imports code (`PlayerAnimationFactory`).
- Les **coûts/cooldowns précis par technique** ne sont pas listés ici (53 sous-classes `NinshuJutsu`, chacune son `getCost/onCooldown`) — à ouvrir au cas par cas si besoin d'équilibrage.
- Les **conditions de Jôgan/Tenseigan/etc.** au-delà de « implant » ne sont pas détaillées dans le lang (le code `Dojutsu_Base` dédié n'a pas été lu en entier).
- `Config.java` est du boilerplate Forge : le réglage réel passe par les **gamerules** et sans doute des valeurs codées en dur.
