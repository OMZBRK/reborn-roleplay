# Inventaire factuel — serveur Minecraft RP Naruto « Reborn » (au 2026-10-12)

> Base de comparaison avec NydoMod, Ninshu Origins et d'autres serveurs RP. Établi en lecture seule à partir de
> `docs/` (CHANTIERS, ETAT_DES_LIEUX, ROADMAP, fiches gameplay/technique/staff/design, DECISIONS) et vérifié dans
> le code (`minecraft/shinobi/*`, `minecraft/mod-hud`, `apps/*`). Chemins relatifs à la racine du dépôt
> `RBLAUNCHER/`.

## Légende et précautions de lecture

| Statut | Sens |
|---|---|
| ✅ | En prod : publié dans le mod `reborn-hud` 0.4.169 (manifest live), le launcher 0.3.45, ou le web (API / panel / bot) |
| 🧪 | Sur le serveur **dev** (plugins Shinobi déployés par SFTP) |
| 🛠️ | En cours / partiel |
| 💡 | Prévu (roadmap beta ou idée actée) |
| ❌ | Absent |

**À garder en tête pour toute comparaison :**
- **Plateforme** : Minecraft 26.2, Java 25, Fabric 0.19.3 côté client, Purpur 26.2 côté serveur. Architecture
  « plugins serveur Paper + un mod client UI » (pas un mod Forge tout-en-un comme Ninshu Origins).
- **La « prod » est une beta fermée** : `LAUNCHER_BETA_GATE=HELPER` réserve la connexion au staff. Ship public visé
  le **31/12/2026**.
- **Les plugins des sprints d'octobre sont sur le serveur dev** ; l'état exact des plugins du serveur **prod n'est
  pas tracé** (au 10/10, son ShinobiCore n'envoyait pas âge/taille/sexe). Toute fonction serveur est donc notée 🧪
  sauf preuve contraire. Le mod client, lui, est publié pour tous (✅) mais ses écrans ne vivent que si le plugin
  correspondant tourne.
- **Contenu de combat quasi vide** : le moteur est là, les techniques réelles non (11 façonnées sur 215 entrées).
- Une étude de Ninshu Origins existe déjà : `docs/archives/etudes/ETUDE_NINSHU_ORIGINS.md` (rétro-ingénierie 2.1.7),
  et une veille concurrents : `docs/archives/night/2026-09-17-concurrents-idees.md`.

---

## 1. Personnage

| Élément | Statut | Description | Source |
|---|---|---|---|
| Multi-personnages par compte | ✅/🧪 | Plusieurs persos, limite de slots envoyée par le serveur ; un YAML par joueur | `ShinobiCore/…/character/`, `docs/gameplay/PERSONNAGES.md` §1 |
| Sélection « en scène » | ✅ (0.4.167-168) | Scène 3D dans un vide sombre, mannequins habillés, caméra cinématique, braises de chakra à la couleur du village, sons | `mod-hud/…/menu/character/scene/SceneSelectScreen.java` |
| Création en 8 étapes | ✅ | Village → clan → sexe → nom → âge → taille → apparence → récap, « façon Ketto » | `scene/SceneJourneyScreen.java` |
| Villages | ✅ | **6** : Konoha, Suna, Kiri, Kumo, Iwa, Ame (Déserteur retiré de la création le 10/10, reste dans la table) | `menu/character/CharacterCreateScreen.java` (VILLAGES) |
| Clans | ✅ | **21 clans nommés + « Autre »** : Konoha 11 (Uchiha, Senju, Hyuga, Nara, Sarutobi, Aburame, Akimichi, Yamanaka, Hatake, Uzumaki, Kurama), Suna 2 (Sabaku, Hoki), Kiri 5 (Hozuki, Hoshigaki, Kaguya, Yuki, Terumi), Kumo 1 (Yotsuki), Iwa 2 (Kamizuru, Onoki), Ame 0. ⚠️ « Neutre » cité par la roadmap n'existe pas dans le code | `CharacterCreateScreen.java` (CLANS, VILLAGE_CLANS) ; `ROADMAP_BETA_2026.md` §3.2 |
| Verrouillage par candidature whitelist | ✅/🧪 | Village + clan validés dans la candidature web grisent le choix (staff libre) ; appel HMAC à l'API | `ShinobiCore/config.yml` §`reborn` ; `CharacterRules.villageLocked` |
| Âge et taille | ✅/🧪 | Âge et taille (153-207 cm) changent la stature (11 ans ≈ 80 % d'un adulte, croissance finie à 17 ans). Âge **fixe** (pas de vieillissement), un calendrier lore existe seulement dans la tab list | `PERSONNAGES.md` §2 ; `lore/LoreCalendar.java` |
| Apparence composée | ✅ | Skin 64×64 composé côté client (peau → sous-vêtement → tenue → yeux → pilosité → cheveux), diffusé à tous (`reborn:skins`) ; rampe de peau, vignettes, teinte/saturation/lumière cheveux et yeux | `mod-hud/…/skin/`, `docs/technique/CREATEUR_PERSONNAGE_ASSETS.md` |
| Assets d'apparence | 🛠️ | ~5 tenues (Uchiha seulement), coiffures, yeux ; wave 1 (Uchiha, Hyuga, Neutre complets) non finie | `CHANTIERS.md` §1 |
| Palettes de couleurs par clan | 💡 | Demandées le 10/10 (« sinon trop de fantaisiste ») | `PERSONNAGES.md` §6 |
| Hétérochromie, sourcils, accessoires, zones de couleur des tenues | ❌ | Absents de l'étape Apparence | idem |
| Archétype de corps (Force/Intelligence/Agilité) | ✅ | Simple étiquette RP sans effet mécanique | `character/Affinity.java` |
| Identité RP : `/rencontrer`, `/amitier`, tab list filtrée | 🧪 | Noms révélés à la rencontre, surnoms, tab list n'affiche que les connus/amis | `character/RencontrerManager.java`, `VisibilityManager.java`, `lore/TabListManager.java` |
| Noms au-dessus des têtes | ✅ | 4 styles au choix | `mod-hud/…/nameplate/` |

## 2. Stats et progression

| Élément | Statut | Description | Source |
|---|---|---|---|
| 6 stats | 🧪 (en service depuis le 28/09) | Taïjutsu, Kenjutsu, Ninjutsu, Contrôle, Vigueur, Chakra ; 1 à 10, plafond doux à 6 | `docs/gameplay/STATS_ET_PROGRESSION.md` §1, `ShinobiCore/…/stats/` |
| Formules | 🧪 | PV 300→1 000 (+grade), chakra 16 000→100 000, endurance, régén chakra **uniquement en méditant**, coûts par rang réduits par le Contrôle, dégâts par lettres de scaling S→D, critique | idem §1.1 |
| Roue des natures | 🧪 | Katon › Fūton › Raiton › Doton › Suiton › Katon, ×1,25 / ×0,8 | idem |
| Fiche shinobi (touche K) | ✅ | « Lanternes célestes », infobulles en phrases **sans chiffres**, onglet Techniques | `mod-hud/…/menu/stats/` |
| XP → niveau → points de stats | 🧪 | **Invisible** pour le joueur ; 1 niveau = 1 point ; niveau max 40 ; gains : présence RP (3 XP/min à deux), seul (1), entraînement en zone (+4, plafond 120/j), séances et techniques apprises ; **aucune XP de combat hors zone** | `stats/Progression.java`, `api/XpService.java` |
| Grades RP (9) | 🧪 | Académicien → Genin → Genin confirmé → Chūnin → Konin → Tokubetsu Jōnin → Jōnin → Commandant Jōnin → Kage ; donnés **en RP** par `/grade` selon son propre grade et son village | `character/Rank.java`, `STATS_ET_PROGRESSION.md` §4 |
| Bonus passifs de grade | 🧪 ❓ | PV/chakra/endurance/+1 à +3 stats par grade ; maintien à trancher | idem §4.2 |
| Points de Mérite | 🧪 | 3 par grade, **sans usage** pour l'instant | idem |
| Jetons de réinitialisation | 🧪 | Liés au compte ; aucune boutique ne les vend | `respec-tokens.yml` |
| Test de la Feuille (nature de chakra) | 🧪/✅ (écran) | Tirage pondéré par clan, jusqu'à 3 natures (cadenas niveau 6/12), effet visible de tous | `character/LeafTestManager.java`, `mod-hud/…/menu/tirage/` |
| Maîtrise par technique | 🧪 | Jauge 0-100 (+1 par lancer), réduit coût (−50 %) et recharge (−30 %) au max | `ShinobiAbilities/config.yml` §`jutsu` |
| Académie (ShinobiLearning) | 🧪 | 7 leçons (grimper à l'arbre, marche sur l'eau, Henge, Kawarimi, Bunshin, armes, taïjutsu) → diplôme Genin ; équipes de 4 avec transmission de maîtrise par le sensei (`/squad teach`) | `ShinobiLearning/…/academy/Lesson.java`, `config.yml` |
| Arbre 4 branches / rangs à l'XP (plan de septembre) | ❌ (remplacé) | Remplacé le 08-10/10 par grades RP + XP invisible | `ROADMAP_BETA_2026.md` §3.3 |

## 3. Techniques et apprentissage

| Élément | Statut | Description | Source |
|---|---|---|---|
| Catalogue de techniques | 🛠️ | **215 entrées** dans `abilities.yml` : **11 façonnées** (Gōkakyū, Teppōdama, Kamaitachi, Doryūheki, Shiden, Shōsen, Kasumi [genjutsu], Konoha Senpū, Iai, Shunshin, Kawarimi) + **204 entrées d'essai** (34 catégories × 6 rangs E→Hiden, effet générique) | `ShinobiAbilities/src/main/resources/abilities.yml`, `jutsu/JutsuEffectRegistry.java` |
| Catégories couvertes (même en essai) | 🛠️ | Ninjutsu (5 natures, iryō, kuchiyose, fūinjutsu), taïjutsu (gōken, jūken), bukijutsu (kenjutsu, kunai, shuriken, fūma, tessen, bō), kekkei genkai (mokuton, hyōton, yōton, shōton, shakuton, ranton, jiton, futton, bakuton), dōjutsu (Sharingan, Byakugan, Rinnegan), senjutsu (crapaud, serpent), genjutsu, kugutsu, kinjutsu, juinjutsu | idem |
| Techniques MagicSpells | 🛠️ | Pont complet (`magicspell:`, `commands:`, `%power%`) mais **0 technique du catalogue ne l'utilise** ; les sorts MS du serveur (dont le M1 kenjutsu `MS_KENJTSU_M1`) **ne sont pas versionnés** — seul `spells-emotes.yml` est dans le dépôt | `server-config/magicspells/`, `docs/technique/AUDIT_TECHNIQUES_ET_CREATOR.md` §1 |
| Cible beta | 💡 | **39** techniques (12 Taï, 12 Ken, 15 Nin D/C/B) + **18** sorts claniques (9 clans × 2) ; 49 en cible complète | `ROADMAP_BETA_2026.md` §3 |
| Lancer | 🧪 | Objets « JutsuItem » + liaisons, incantation par mudras, lancer rapide (clic gauche), **dette de chakra autorisée** (overdraw → épuisement/KO) | `jutsu/JutsuExecutionManager.java`, `IncantationManager.java` |
| ⚠️ Apprentissage exigé pour lancer | 🧪 | `jutsu.require-learned: false` dans le config du dépôt (« garder false pendant les tests ») | `ShinobiAbilities/config.yml` l.11 |
| Parchemin objet (D/C/B/A/S) | ✅/🧪 | Vrai objet 3D par rang, volable/échangeable/brûlable, unique, poids 0,2-0,8 kg ; écran de lecture | `techniques/ParcheminItems.java`, `mod-hud/…/parchemin/LectureScreen` |
| Bibliothèques à tirage | ✅/🧪 (lots 1-3) | Meuble à casiers, tirage **partagé** toutes les 3 h, 1-9 rouleaux, % par rang (Publique/Réservée/Privée) ou par technique, simulateur staff | `parchemin/LibraryService.java`, `docs/gameplay/APPRENTISSAGE.md` §3 |
| Séances | ✅/🧪 | D 1 · C 3 · B 6 · A 12 · S 20 séances, une toutes les 20 h ; mini-jeux **dans le monde** (mudras, pompes) ; maître présent = double ; 0,2 % de perdre le rouleau ; S validé par le staff | `parchemin/SeanceService.java`, `techniques/LearningMinigame.java` |
| Concentration du chakra | 🧪 ❓ | Mini-jeu d'équilibre (rotation de la tête, immobilité) avant tout ce qui coûte du chakra sauf taïjutsu ; rendu client rejeté, ensō A ou trait B à trancher | `techniques/ConcentrationGame.java`, `mod-hud/…/parchemin/ConcentrationHud.java` |
| Slots du rang S | 🧪 | 2 détenteurs max par technique S (réglable), libérés à la mort ; héritage si rouleau porté | `parchemin/SlotRegistry.java` |
| Natures de chakra | 🧪 | 5 natures (Katon, Suiton, Fūton, Doton, Raiton), jusqu'à 3 par perso via le test de la Feuille | `character/ChakraAffinity.java` |
| Techniques de clan / apprentissage dans le clan | 💡 | Règle actée, système « vraiment immersif » à concevoir | `APPRENTISSAGE.md` §8 |
| Ancien système : étagères d'apprentissage, encyclopédie | 🧪 (héritage) | `LearningShelfManager`, `EncyclopediaBook` toujours présents | `ShinobiAbilities/…/techniques/` |
| Technique Creator (éditeur de graphes) | 🛠️ | Compilateur, éditeur dans le panel (`/abilities`), `requires:`, preview `/sa preview` | `packages/ability-compiler`, `apps/admin/…/(panel)/abilities/` |

## 4. Combat

| Élément | Statut | Description | Source |
|---|---|---|---|
| M1 taïjutsu | 🧪 | Combo 4 coups + finition « pyramid », serveur autoritaire, dégâts × (1 + 0,08 × Taïjutsu) | `ShinobiCombat/…/combat/CombatListener.java` |
| M1 kenjutsu | 🧪 | Sort MagicSpells sur l'arme Nexo `epee_kenjutsu`, débite l'endurance | `combat/KenjutsuEnduranceGate.java` |
| Garde (M2) | 🧪 | Absorbe 70 % des dégâts, draine l'endurance, **guard break** (stun) à 0 | `CombatListener.java` |
| Parade timée | 🧪 | Fenêtre 320 ms au début de la garde, stun + recul de l'attaquant, rembourse l'endurance (façon Sekiro). Roadmap : 200 ms, counter 1 s | idem ; `ROADMAP` Sprint 2 |
| Esquive | 🧪 | Esquive latérale/arrière, recharge 2,5 s, sans coût | idem (`DODGE_*`) |
| Endurance | 🧪 | Barre partagée M1 / techniques taï-ken, régén après délai | `combat/StaminaManager.java` |
| HUD combat | ✅ | Endurance en ensō autour du viseur, chiffres de dégâts, combo, icônes de recharge (3/8 dessinées) | `mod-hud/…/combat/` |
| Lock-on / strafe | 💡 | Façon Elden Ring, Sprint 2 | `ROADMAP` Sprint 2 S2 |
| Combo M1 → sort (+20 %, effets) | 💡 | Sprint 2 | idem |
| Mannequins d'entraînement | 🧪 | `/dummy` | `ShinobiCore/…/dummy/` |
| Journal d'agressions hors zone | 🧪 | Qui a frappé en premier, alerte staff au-delà de 2 combats/h | `aggression-log.txt`, `KO_ET_MORT_RP.md` §4 |
| Équilibrage dégâts/PV | ⚠️ | M1 3-8 dégâts face à 300-1 000 PV : non calibré | `STATS_ET_PROGRESSION.md` §1.1 |

### Mobilité

| Élément | Statut | Description | Source |
|---|---|---|---|
| Voies de mobilité | 🧪 | Perso neuf = **aucune** mobilité ; une voie au choix, exclusive : « Voie du Chakra » (kit ninja) ou « Voie du Flux » (free-run) ; débloquées par des parcours d'entraînement | `mobility/MobilityPaths.java`, `/trainingground` |
| Course de chakra (Naruto run) | ✅/🧪 | Touche dédiée, anim synchronisée (`reborn:run`) | `mobility/NarutoRun.java` |
| Dash | ✅/🧪 | Double tap accroupi (touche V côté mod), coût chakra, recharge 4 s | `mobility/ShinobiDash.java` |
| Double saut, saut mural | 🧪 | Jetons aériens (1 par contact au sol), 3 sauts muraux par vol | `DoubleJump.java`, `WallJump.java` |
| Escalade / accroche murale | 🧪 | S'accrocher en l'air, grimper un peu, glisser (2 charges) | `mobility/Climb.java` |
| Onde de choc au sol | 🧪 | Plongée chargée, dégâts de zone + stun | `FloorShockwave.java` |
| Pistes, tyrolienne, grappin | 🧪 | Pistes de déplacement tracées par le staff ; tyrolienne/grappin sous permission | `TrailManager.java`, `ShinobiCore` config `mobility` |
| Marche sur l'eau | ❌ | Seulement une **leçon d'académie** (étiquette), aucune mécanique | `academy/Lesson.java` |
| Marche verticale sur les murs | ❌ | Pas de wall-walk continu (seulement accroche + saut mural) | — |
| 3 styles de course (Vif / Shadowstep / Suzaku) | 💡 | Sprint 2 | `ROADMAP` |
| Poids de la sacoche ↔ mobilité | 💡 | Idée notée | `INVENTAIRE_ET_COSMETIQUES.md` §6 |

## 5. KO, mort RP, médical

| Élément | Statut | Description | Source |
|---|---|---|---|
| Terrains d'entraînement | 🧪 | Zones staff : combat libre, défaite = PV pleins, rien de persistant | `ko/`, `/zonerp`, `KO_ET_MORT_RP.md` §4 |
| PV persistants hors zone | 🧪 | Pas de régénération naturelle ; repos plafonné à 30 % | idem §3 |
| Cycle KO | 🧪 | À terre 45 s (ramper, chuchoter, `/aide`) → inconscient 5 min → `/hopital` proposé → réveil au lit libre à 30 % | `ko/KoManager.java` |
| Volonté (QTE de relève) | 🧪 | Suite de 4-7 sceaux, vérifiée serveur ; **facultative** : seulement si le perso connaît `volonte` — **technique inexistante**, donc personne ne l'a | `ko/VolonteQte.java` |
| ATA (blessé en attente de soin) | 🧪 | Plus de course/dash/techniques/armes, M1 mains nues /2 ; sortie par médic ou repos RP (20/10 min) | `ko/ata/AtaManager.java` |
| Blessures par partie du corps | 🧪 | Gravité Légère → Urgente, aggravées à chaque KO, retirées seulement par `/soigner` (silhouette + recette) | `ShinobiCore/…/medic/` |
| Paume de soin, bandages, armoires, encyclopédie | 🧪 | Soin tenu (Contrôle + rang médical 1/2), bandages +5 %, armoires à médicaments, pilules | `medic/PalmHealing.java`, `BandageListener.java`, `MedicArmoirManager.java` |
| Hôpitaux et lits | 🧪 | Zones hôpital, lits = meubles Nexo, illimités | Infirmerie du Poste de garde |
| Menu d'interaction sur un corps (R) | ✅/🧪 | Ausculter, attraper à la gorge (sans limite), porter, fouiller, demander une RPK, achever, exécution | `ko/action/CorpsService.java`, `mod-hud/…/interaction/` |
| Mort RP (RPK) | 🧪 | Demande → **validation par le staff** (raison obligatoire) → c'est l'achèvement par le demandeur qui tue ; la victime ne valide rien | `ko/action/KillRequestManager.java` |
| Scène de mort | ✅/🧪 | Écran noir, « Assister à votre réelle fin ? » après 60 s, vue grisée sans HUD, corps habillé au sol 15 min ; perso marqué MORT, non jouable | `ko/action/DeathScene.java`, `mod-hud/…/ko/DeathScreen.java` |
| Exécutions (mises à mort animées) | 🛠️ | Catalogue + possession par compte en place ; **aucune animation** ; vente non branchée | `executions.yml`, `KO_ET_MORT_RP.md` §8 |
| Habillage KO | ✅ | Filtres plein écran, cartes de titre en kanji, sons de cœur | `mod-hud/…/ko/KoHud.java` |
| Épuisement de chakra | 🧪 | Chakra à 0 → KO bleu ; dette de chakra | `chakra/ExhaustionManager.java` |
| Coma, saignement, antidotes, `MedicalService` | 💡 | Sprint 3 | `KO_ET_MORT_RP.md` §12 |
| Résurrection, Iryō maître | ❌ (cut list) | Post-beta | `ROADMAP` §5 |

## 6. Dōjutsu et perception (ShinobiSense)

| Élément | Statut | Description | Source |
|---|---|---|---|
| Byakugan | 🧪 (+ rendu ✅ 0.4.152) | Vision « comme dans l'anime » : négatif, réseau de chakra / tenketsu des joueurs et entités à 50 blocs à travers les murs ; clans Hyuga, Ōtsutsuki | `ShinobiSense/…/ByakuganVision.java` (**non commité**), `mod-hud/…/byakugan/` |
| Dōjutsu générique | 🧪 | Portée de perception ×2, drain de chakra/s, activation visible des proches ; clans Hyuga/Uchiha/Ōtsutsuki | `DojutsuManager.java`, `ShinobiSense/config.yml` |
| Sharingan | ❌ (mécanique dédiée) | Seulement le dōjutsu générique + entrées d'essai `generic_sharingan` ; pas de copie, pas de genjutsu oculaire, pas de visuel | `JutsuEffectRegistry.java` |
| Rinnegan | ❌ | Entrées d'essai uniquement | idem |
| Radar de chakra (`/sense`) | 🧪 | Impulsion de perception, portée selon la compétence, sondage discret au-delà d'un seuil | `SenseService.java` |
| Genjutsu | 🧪 | Illusion lancée sur un perso (12 s), libération `/sense kai` | `GenjutsuManager.java` |
| Iris des dōjutsu (assets) | 💡 | Assets wave 2 (Sprint 3) | `ROADMAP` |

## 7. Bijū / jinchūriki (ShinobiTail)

| Élément | Statut | Description | Source |
|---|---|---|---|
| Catalogue des 9 bijū | 🛠️ (code présent, déploiement non tracé) | Shukaku → Kurama, éditables par le staff, 5 personnalités (vengeful, proud, protective, wild, calm) | `ShinobiTail/src/main/resources/beasts.yml` |
| Rage, corruption, déclencheurs | 🛠️ | Montée de rage, chuchotements, fenêtre de contrôle | `ShinobiTail/config.yml`, `trigger/TriggerListener.java` |
| Transformation par stades | 🛠️ | Un stade par queue (dégâts, vitesse, résistance, régén, aura, taille) | `transform/TransformationManager.java` |
| Monde intérieur | 🛠️ | Confrontation avec la bête, faux corps laissé dans le monde | `inner/InnerWorldManager.java`, `FakeBody.java` |
| Scellement (fūinjutsu) | 🛠️ | Lien qui draine la rage contre du chakra du scelleur | `ShinobiAbilities/…/fuinjutsu/SealingLink.java` |
| Outils MJ | 🛠️ | `/tail` + interface admin | `command/TailCommand.java`, `gui/AdminGui.java` |
| Modèles 3D de bijū | 🛠️ | Son Gokū (bbforge, 6 anims) ; **aucune intégration Model Engine côté serveur** ; Kurama géant en cut list | `docs/design/MODELES_3D.md` |

## 8. Modes et transformations

| Élément | Statut | Description | Source |
|---|---|---|---|
| Transformation de jinchūriki | 🛠️ | cf. §7 | ShinobiTail |
| Mode Sage (senjutsu) | ❌ | Catégories d'essai crapaud/serpent seulement ; senjutsu exclu des bibliothèques | `abilities.yml`, `APPRENTISSAGE.md` §1 |
| Huit Portes (Hachimon) | ❌ | Seulement une fiche de preview côté client | `mod-hud/…/parchemin/Technique.java` |
| Susanoo, Kurama, Gamabunta (modèles) | ❌ (cut list) | Post-beta | `ROADMAP` §5 |
| Méditation | 🧪 | Seule source de régénération du chakra ; avance le repos ATA ; système complet en cut list | `character/MeditationManager.java` |

## 9. Économie

| Élément | Statut | Description | Source |
|---|---|---|---|
| Ryo (monnaie de jeu) | 🧪 | Solde par perso ; **donné par le staff** (`/ryo give`) faute de gains RP | `ShinobiCore/…/shop/`, ADR 0006 |
| Échoppe | ✅/🧪 | Tenues (prix plat 500 ryo) et cartes (150 ryo Konoha) | `shop/ShopManager`, `mod-hud/…/menu/shop/` |
| RBCoins (premium) | 🛠️ | Colonne `rbCoinBalance` en base ; achat Stripe en v1.1 ; récompenses d'activation (boost Discord, stream…) prévues ; l'Échap affiche encore le solde ryo | `ETAT_DES_LIEUX.md` §9, `docs/adr/0006-…` |
| Boutique launcher / module `shop` de l'API | ❌ | Dossier `apps/api/src/shop` vide | `apps/api/src/shop/` |
| Vente d'exécutions, de jetons de respec | ❌ ❓ | Aucune boutique ne les vend | `CHANTIERS.md` §4 #8 |
| Trésor commun / banques / coffres de faction | 💡 | Design Faction pas commencé | `CHANTIERS.md` §1 |
| Récolte, métiers, commerce entre joueurs | ❌ | Aucune mécanique (hors objets échangeables) | — |

## 10. Inventaire RP et cosmétiques

| Élément | Statut | Description | Source |
|---|---|---|---|
| Sacoche « inrō » (touche E) | ✅/🧪 | Inventaire data-driven **par personnage**, hors inventaire vanilla, filtres par catégorie, rareté | `ShinobiCore/…/inventory/`, `mod-hud/…/menu/inventory/` |
| Sacs par tiers | 🧪 | 9 cases « sur soi » ; Sacoche 18, Bandoulière 27, Sac ninja 36, Sac lourd 45 | `inventory/BagTier`, `INVENTAIRE_ET_COSMETIQUES.md` §7 |
| Poids | 🧪 | Limite de poids selon le sac (12 à 57 kg), jauge | idem |
| Objets RP | 🧪 | 14 objets seedés : kunai, shuriken, tantō, parchemin explosif, pilule du soldat, ration, onguent, fil, corde, masque ANBU, manteau Akatsuki, bandeau de Konoha, rouleau d'invocation… | `inventory/RpItemType.java` |
| Cosmétiques portés sur le corps | ✅/🛠️ | Emplacements bandeau/masque/manteau/dos, rendu par couche client (`CosmeticFeatureRenderer`) ; peu de modèles produits | `mod-hud/…/cosmetic/`, `server-config/nexo/` |
| Fouille d'un corps | 🧪 | Via le menu d'interaction | `CorpsService` |
| Modèles 3D d'objets | 🛠️ | Parchemins D→S en prod ; sacs/cosmétiques Nexo configurés, modèles à dessiner | `MODELES_3D.md` |

## 11. Monde

| Élément | Statut | Description | Source |
|---|---|---|---|
| Carte du Pays du Feu | 🛠️ | Terrain quasi fini (World Machine → World Painter), monde `PaysDuFeu06` | `CHANTIERS.md` §1 |
| Konoha | 🛠️ | Début de construction ; centre (tour de l'Hokage, académie, marché…) au Sprint 2 | `ROADMAP` Sprint 2 S4 |
| Carte du monde (touche M) | ✅ | Konoha seulement, 8 lieux placés « à l'œil », carte = objet à tenir en main | `ShinobiCore/…/resources/places.yml`, `mod-hud/…/map/` |
| Autres villages jouables (Suna, Kiri, Kumo, Iwa) | ❌ (cut list) | Sélectionnables à la création mais non construits | `ROADMAP` §5 |
| Quartiers claniques, capitale, villages secondaires, donjons | 💡 | Sprints 2-4 | `ROADMAP` |
| Zones RP (entraînement, repos, hôpital) | 🧪 | Posées par le staff | `/zonerp`, Infirmerie |
| Bibliothèques posées dans le monde | 🧪 | Meubles transformés par `/bibliotheque creer` | §3 |
| Zones de ressources / contrôle de zone | ❓ | Observé en jeu (« le plugin bloque » la récolte d'une zone tenue par un autre village) mais **aucun code dans le dépôt** : plugin tiers non versionné | `docs/gameplay/MISSIONS.md` §1 |
| Mobs | ❓ | MythicMobs uniquement (pas de faune vanilla) selon les décisions ; aucune config versionnée | `DECISIONS.md` (06/10) |
| Météo visuelle | ✅ | Pluie, sable, brume en volume 3D, pilotée par le staff (`/meteo`) | `mod-hud/…/meteo/` |
| OST contextuelle | ✅ | Musiques par zones + late-join (`/ost`), pistes livrées par le manifest | `minecraft/plugin-ost`, `minecraft/mod-ost` |
| Calendrier lore | 🧪 | Date in-univers dans la tab list | `lore/LoreCalendar.java` |

## 12. Missions

| Élément | Statut | Description | Source |
|---|---|---|---|
| Système de missions en jeu | ❌ | Aucun (ni tableau, ni récompense automatique) ; hors roadmap beta | `docs/gameplay/MISSIONS.md` |
| Missions/entraînements animés à la main | ✅ (pratique RP) | Idées D/C/B, dilemmes, 7 entraînements originaux ; formats de rapport (PNG parchemin), affiches, vidéos | idem §2-4 |
| Récompense XP par le staff | 🧪 | `/xp donner` après mission/examen/event | `STATS_ET_PROGRESSION.md` §3 |
| Examens de grade | 🧪 (RP) | `/grade <joueur> <grade> examen` ; examens chūnin joués en scène | `STATS_ET_PROGRESSION.md` §4.3 |

## 13. Staff et administration

| Élément | Statut | Description | Source |
|---|---|---|---|
| Poste de garde (F7) | ✅ (0.4.157-160) | Alertes (combats hors zone, `/aide`, signalements, RPK), fiche joueur, chat staff, journal, aide-mémoire des commandes | `mod-hud/…/staff/`, `ShinobiCore/…/staff/panel/` |
| Infirmerie | ✅ | Blessés, zones sur la carte de Konoha, lits, réglages KO à chaud | idem |
| Sanctions | ✅/🧪 | 9 infractions RP, escalade sur 90 jours, preuves jointes, avertissement/mute/ban, `/pardon` ; stockage local (pas dans la base web) | `sanctions.yml` |
| Grades staff en jeu | 🧪 | Helper < Modo < Admin < Owner par permissions, **non synchronisés** avec le panel | `GUIDE_STAFF_EN_JEU.md` §1 |
| Outils d'animation | 🧪 | `/vanish`, `/cinematic` (éditeur de cinématiques), `/meteo`, `/ost`, mannequins | `ShinobiCore/…/cinematic/` |
| Panel web staff | ✅ | Dashboard, whitelist, tickets, joueurs, audit, anomalies, inbox, créneaux d'oral, wiki + idées, fichiers par grade, éditeur de techniques, 2FA | `apps/admin/…/(panel)/` |
| Pont panel → serveur | ✅ | Le serveur interroge l'API et exécute des reloads whitelistés (Nexo, MagicSpells, MythicMobs, ModelEngine) sans RCON | `ShinobiCore/config.yml` §`panel-bridge` |
| Whitelist | ✅ | Candidature dans le launcher, fil Discord, décision staff, chat bidirectionnel ; village/clan validés reportés en jeu | `apps/api/src/whitelist`, `apps/bot/src/commands/whitelist.ts` |
| Tickets | ✅ | Même pont launcher ↔ Discord | `apps/api/src/tickets`, `apps/bot/src/commands/ticket.ts` |
| Casier dans la base web, module `incidents` | ❌ | Dossier vide | `apps/api/src/incidents/` |

## 14. Interface (mod `reborn-hud`)

| Élément | Statut | Description | Source |
|---|---|---|---|
| Menu principal, Échap « Tokonoma », réglages | ✅ | DA Reborn (nuit/laque/or, signature par écran) | `mod-hud/…/menu/` |
| HUD vitals + éditeur de HUD | ✅ | Déplacer/redimensionner/masquer, presets ; vitals inchangés par décision | `editor/`, `element/` |
| Chat RP en panneau | ✅ | Onglets Général / RP / Groupe, recherche, mode `/me`, indicateur de frappe | `mod-hud/…/chat/` |
| `/me`, postures | 🧪 | `/me`, `/assoir`, `/allonger`, `/ramper` | `ShinobiCore` |
| Voix de proximité | ✅ | PlasmoVoice + addon Reborn (bulles de parole) ; chuchotement à terre | `mod-hud/…/voice/` |
| Emotes | ✅/🧪 | EmoteCraft piloté serveur (`/playemote`, menu `.`, menu R), fichiers poussés au JOIN depuis le panel | `docs/gameplay/EMOTES.md`, `emote/` |
| Animations de corps | 🛠️ | Démarches (6 styles), Naruto run ; catalogue P0-P2 largement à produire | `docs/gameplay/ANIMATIONS_BASE.md` |
| Mode interaction (R) | ✅ | Curseur et menus contextuels (joueur, corps, entité, bloc, soi) | `interaction/` |
| Caméra 3ᵉ personne, regard libre | ✅ | Épaule, côté, presets | `camera/` |
| Mode photo, galerie, cadres, mode cinéma | ✅ | Filtres, cadres, bandes de cinéma | `screenshot/`, `immersion/` |
| Cinématiques serveur | 🧪 | Éditeur et lecture staff | `ShinobiCore/…/cinematic/` |
| Tab list RP | ✅ | Grades, connaissances, calendrier lore | `menu/tablist/` |
| Taille des menus fixe | ✅ | Taille « échelle 2 en 1080p » quelle que soit l'échelle | `ui/MenuScale.java` |

## 15. Launcher et anti-triche

| Élément | Statut | Description | Source |
|---|---|---|---|
| Launcher Tauri 0.3.45 | ✅ | Auth Microsoft (App ID approuvé), liaison Discord, auto-update signé Ed25519, Rich Presence | `apps/launcher/` |
| Manifest signé + purge stricte des mods | ✅ | ~19 mods requis/optionnels ; tout mod hors manifest est supprimé | `src-tauri/src/manifest/`, `ETAT_DES_LIEUX.md` §4 |
| Diagnostics en direct | ✅ | Analyse des logs JVM, toasts d'erreur typés | `launcher/diagnostics.rs` |
| Pages launcher | ✅ | Accueil, feed, lore, règles, patchnotes, mods, captures, whitelist, tickets, réglages | `apps/launcher/src/routes/` |
| Onglet « Mon personnage » | 🛠️ | Stub : persos non reliés à l'API | `routes/Character.tsx` |
| Carte dans le launcher | 🛠️ | Stub visuel | `routes/Map.tsx` |
| Attestation play-token | ✅ | Jeton HMAC 5 min émis par l'API, envoyé par `reborn-integrity`, vérifié par `RebornGuardian` (kick à 8 s, UUID vérifié) | `apps/api/src/play`, `minecraft/mod-integrity`, `minecraft/plugin-guardian` |
| Surveillance de fichiers | 🛠️ | Watcher branché, réaction anti-triche non connectée (faux positifs) | `integrity/watcher.rs` |
| Anti-triche gameplay | 🧪 | Serveur autoritaire (combat, QTE Volonté vérifiée, actions sur corps revérifiées) | plugins |
| Signature de code du launcher | ❓ | Norton bloque le launcher non signé | `CHANTIERS.md` §4 #9 |
| Détection des mises à jour de mods | ✅ | `modrinth-sync` | `packages/modrinth-sync` |

## 16. Social et Discord

| Élément | Statut | Description | Source |
|---|---|---|---|
| Bot Discord | ✅ | `/whitelist accept|reject|revise`, `/ticket progress|resolve|close`, `/role`, `/ping` ; fils publics par dossier ; relais des messages dans les deux sens (HMAC) | `apps/bot/src/commands/` |
| Liaison compte Discord | ✅ | OAuth depuis le launcher | `apps/api/src/discord/` |
| Rich Presence | ✅ | En jeu uniquement | `discord_rpc.rs` |
| Module social / Steam / events dans l'API | ❌ | Dossiers vides (v1.1) | `apps/api/src/{social,steam,events}` |
| Communication RP (volière, annonces) | 💡 | Sprint 3 | `ROADMAP` |
| Factions (Kage, ANBU, Corps médical, Académie, Patrouille) | 💡 ❓ | Design non commencé ; fonctions RP ≠ grades | `CHANTIERS.md` §1 |
| Boards publics | ✅ | Trello public + Miro staff | `docs/pilotage/BOARDS.md` |

---

## Contraintes de design (décisions de l'utilisateur à respecter)

Source : `docs/pilotage/DECISIONS.md` sauf mention. Toute idée importée d'un mod ou d'un autre serveur doit passer
ces filtres.

### Philosophie RP et combat
1. **Tuer le combat d'arène** : combat libre seulement sur les terrains d'entraînement ; ailleurs, PV persistants et
   conséquences qui obligent à jouer une scène (06/10). Pas d'XP pour le combat hors zone.
2. **Plancher à 30 %** sans médic ; frais d'hôpital **en RP** ; **NLR laissé au joueur** ; FearRP retirée (10/10).
3. **ATA** : aucune technique, seulement le M1 à mains nues (10/10).
4. **Volonté facultative** : c'est une compétence/technique, pas un droit (10/10).
5. **Mort RP validée par le staff**, sans consentement de la victime ; valider ne tue pas, c'est l'action du
   demandeur (achever/exécuter) qui tue ; le mort reste sur place, écran noir (10-11/10).
6. **Exécutions = cosmétiques vendables** (animations stylées).
7. Mobs = **MythicMobs uniquement**.
8. Lien KO ↔ médical **volontairement léger**.

### Progression
9. **Aucune stat ne ferme une porte RP**, **apprentissage libre** (pas de prérequis de stat) (23/09).
10. **Grande échelle de chakra** (~100 000 max, un rang S se lance 2-3 fois).
11. **Grades donnés en RP**, jamais à l'XP ; ANBU, conseil, bras droit = **fonctions RP**, pas des grades (08/10).
12. **Niveau et XP invisibles** pour le joueur ; 1 niveau = 1 point de stats ; `/xp` réservé au staff (10/10).
13. **Mérite séparé** des stats (usage à définir).
14. **Infobulles sans chiffres** ; respec par **jetons liés au compte** (29/09).
15. 6 stats (option B), **genjutsu hors beta** (23/09).

### Apprentissage
16. Parchemins pour Taï/Ken/Nin ; techniques de clan **apprises dans le clan** ; ANBU via la fonction ANBU (08/10).
17. Parchemin = **vrai objet, unique**, volable/brûlable ; bibliothèques à tirage partagé 3 h, **accès libre**
    (restrictions RP).
18. **Rang S à slots limités** ; fin d'un S validée par le staff ; maître = maîtrise ≥ 80 + 14 jours.
19. Concentration du chakra **dans le monde**, sauf Taïjutsu ; bouger ou sauter fausse le test (09/10).

### Périmètre et planning
20. Beta : **39 techniques** (rangs A/S en cut list) + **18 sorts claniques** (ni Neutre ni Autre) — question close.
21. Cut list post-beta : Susanoo/Kurama/Gamabunta, Nin A/S, méditation complète, médical avancé (résurrection),
    autres villages, guerre inter-villages, achievements/leaderboards, radio moderne.
22. **Finir le Sprint 1 avant le Sprint 2** (Faction encore à designer).

### Visuel et UI
23. DA **Akatsuki** (crimson + or), kit nuit/laque/or, **une signature par écran** ; pixel art détaillé, pas de
    fonds animés lourds ; création de perso jamais dans le monde du jeu (03/10).
24. **HUD vitals inchangé**, HUD combat discret, **pas d'effets « RPG »** (08/10).
25. Menus à la taille fixe « échelle 2 en 1080p » ; police des menus de base à garder (10/10).
26. Textures 3D : **aplats propres, pas de motifs** ; une texture d'équipe se conserve telle quelle (mémoire bbforge).
27. Maquettes avant le code ; visuels montrés avant toute publication.

### Économie
28. **Ryo** (gagné en jeu) et **RBCoins** (achetés / Bug Bounty) **sans conversion** ; les récompenses
    d'activation rapportent des RBCoins (ADR 0006, 03/10).

### Points ouverts qui conditionnent les idées
- Design **Faction** (sections, trésor, banques, coffres) ; usage du **Mérite** ; bonus passifs de grade ;
  rendu de la concentration ; technique `volonte` ; palettes par clan ; plafond des points de stats ; vente des
  exécutions et jetons (`CHANTIERS.md` §4).
