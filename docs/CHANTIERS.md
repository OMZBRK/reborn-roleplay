# Tableau de bord des chantiers

> **Le document à lire en premier pour savoir où on en est.** Un chantier par section : son état, ce qui est
> fait (avec le lien vers la fiche détaillée), ce qui reste, ce qui attend une décision. **Mis à jour le
> 2026-10-13** après la consolidation de toutes les sessions de travail (8 septembre → 12 octobre).
>
> Légende : ✅ fait et en service · 🟢 fait, à tester en jeu · 🟡 en cours / partiel · ⏳ pas commencé ·
> ❓ décision attendue de l'utilisateur · ⚠️ point de vigilance
>
> Le **présent technique** (versions, adresses) est dans [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md), le **plan**
> dans [`ROADMAP_BETA_2026.md`](./ROADMAP_BETA_2026.md), le **pourquoi** des décisions dans
> [`pilotage/DECISIONS.md`](./pilotage/DECISIONS.md).

---

## 0. Où en est-on — en une minute

- **Date de référence : 17 octobre 2026.** Sprint 1 prolongé (décision : le finir avant le Sprint 2).
- **Game Plan Reborn : [`GAMEPLAN.md`](./GAMEPLAN.md), on avance point par point dans l'ordre RedLine** (§54 du Game
  Plan).
  - **Recette du 17/10** (en jeu) : radio, boutiques, factions, cloche, volière et missives, marche sur l'eau, Test de
    la Feuille **validés** ; monnaie : bourse OK, **banque pas encore testée**
    ([`operations/RECETTE_GAMEPLAY.md`](./operations/RECETTE_GAMEPLAY.md) §0).
  - **Prochains chantiers (validés à coder le 17/10)** : 1. **refonte des états du joueur**, étapes 1 à 9
    ([`technique/ETATS_JOUEUR.md`](./technique/ETATS_JOUEUR.md)) — **en cours** ; 2. ~~**coffres de section** puis
    **portes à clé** : verrou + clé seulement, une porte verrouillée se crochète ou se casse, **aucune zone
    inaccessible**, pas d'autres « zones de faction » ([`gameplay/FACTION.md`](./gameplay/FACTION.md) §11-§13)~~ — **mis de côté le 18/10** ([`gameplay/SERRURES_ET_COFFRES.md`](./gameplay/SERRURES_ET_COFFRES.md), proposition en réserve) ; effet visuel de méditation en cours (18/10).
    **Plus tard** : mécaniques propres à chaque section (FACTION §12). Modèles 3D : l'utilisateur les fait en parallèle.
  - **Publié le 16/10** (`reborn-hud` 0.4.185 → **0.4.188**, manifest **3.1.145**, ShinobiCore du 16/10) : fenêtre du
    jeu « 26.2* Reborn » + logo, cloche sans délai à partir d'un grade (0.4.185) ; course de chakra sur X seule, lecture
    sur place à la volière (0.4.186) ; Test de la Feuille v2 (0.4.187) puis v3, vraie main Minecraft au skin du joueur
    (0.4.188).
  - **Publié le 14/10** (`reborn-hud` 0.4.175, launcher 0.3.46) : Player ID (N° par personnage, `/pid`), accueil
    Microsoft + Discord, inventaire / ramassage F / aura de rareté / surcharge, duel consenti, derniers écrans en jeu.
  - **Publié le 14/10** (`reborn-hud` 0.4.176 → **0.4.178**, plugins Shinobi, pack Nexo nourriture) : journal universel
    (tout est tracé quel que soit le grade) + commandes staff RedLine ; Poste de garde v3 (volet + recherche) ; mode
    staff sans mécanique RP et mode animation ; allures marche / trot / sprint
    ([`gameplay/DEPLACEMENTS.md`](./gameplay/DEPLACEMENTS.md)) ; chaîne KO v2 (douleur, exécution par F, portage,
    agonie) ; correctifs urgents des états (étape 0 de [`technique/ETATS_JOUEUR.md`](./technique/ETATS_JOUEUR.md)) ;
    nourriture v3 et entreprises ([`gameplay/NOURRITURE.md`](./gameplay/NOURRITURE.md),
    [`gameplay/ENTREPRISES.md`](./gameplay/ENTREPRISES.md)) ; natures canoniques et Test de la Feuille refait.
    **En recette** : [`operations/RECETTE_GAMEPLAY.md`](./operations/RECETTE_GAMEPLAY.md).
  - **Publié sur le dev le 16/10** (`reborn-hud` 0.4.184) : **factions et sections** (volet J, socle F1-F4,
    [`gameplay/FACTION.md`](./gameplay/FACTION.md) §15), cloche du village et volières / missives par corbeau (ShinobiCore `communication/`,
    mod `communication/`, objets Nexo provisoires) — [`gameplay/CLOCHE_ET_MISSIVES.md`](./gameplay/CLOCHE_ET_MISSIVES.md) §10.
  - **Publié le 15/10** (`reborn-hud` **0.4.182**, `reborn-ost` 0.2.5, ShinobiCore) : carte des touches réduite (26 → 15,
    sans conflit, migration une fois, défauts selon le clavier AZERTY/QWERTZ) ([`technique/MOD_HUD.md`](./technique/MOD_HUD.md)
    §2) ; marche sur l'eau v8 (pli de la texture d'eau, fluide) ; gestion des personnages au Poste de garde (F7 › fiche ›
    Personnages, ancien `/character edit`, `/persoreset`, `/persosuppr` retirés) ([`gameplay/PERSONNAGES.md`](./gameplay/PERSONNAGES.md) §1 bis).
  - **Publié le 15/10** (`reborn-hud` **0.4.181**, ShinobiCore / ShinobiAbilities) : boutiques en jeu (tailleur à
    comptoirs par catégorie configurés par le staff, bazar ; carte BOUTIQUE de l'Échap vide en attendant les visuels)
    ([`gameplay/BOUTIQUES.md`](./gameplay/BOUTIQUES.md)) ; météo blizzard et tempête de sable (`/meteo`, zones,
    durée) + correctif du brouillard sous Sodium ; marche sur l'eau (capacité `marche_eau`, eau vanilla + ondes de pas,
    gouttes et sons) ([`gameplay/DEPLACEMENTS.md`](./gameplay/DEPLACEMENTS.md) §10) ; scène de chargement « nuit de
    pleine lune » sur tout le trajet vers le serveur, rien de Mojang, pack Nexo accepté sans confirmation
    ([`technique/MOD_HUD.md`](./technique/MOD_HUD.md) §8). Étude concurrente :
    [`etudes/CONCURRENCE_SHINODA.md`](./etudes/CONCURRENCE_SHINODA.md).
  - **Publié le 15/10** (`reborn-hud` 0.4.179 et correctif 0.4.180, nouveau plugin **ShinobiRadio**) : radio en escouades
    ([`gameplay/RADIO.md`](./gameplay/RADIO.md) §12) ; monnaie et banque — bourse-objet, guichet, virement, échange
    garanti, coffre à paliers, solde hebdomadaire, fouille à 30 %, testament
    ([`gameplay/MONNAIE_ET_BANQUE.md`](./gameplay/MONNAIE_ET_BANQUE.md)) ; lâcher / ramasser en créatif ; halo de
    rareté pixel art ; sacoche sans kanji. La voix radio se teste à 3 comptes sur le dev.
  - Objets 3D (bourse, oreillette, plats, cloche, corbeau…) modélisés par l'utilisateur dans Blockbench, en
    parallèle (provisoires dans le pack Nexo).
  - **Discord staff « RBN - STAFF »** : rework appliqué le 14/10 (rôles réseau, salons, permissions, bannières,
    messages en cadre) ; page « Discord » du panel staff et salon `build-updates` **déployés** (train web, 17/10)
    ([`operations/DISCORD_STAFF.md`](./operations/DISCORD_STAFF.md)). Boards Trello / Miro synchronisés le 17/10.
  - **Plus tard** : système de combat (décision du 14/10).
- **Livré les 12-13/10 (serveur dev)** — `reborn-hud` 0.4.170 → **0.4.174** + ShinobiCore / ShinobiAbilities + pack Nexo :
  - **Tous les menus au niveau de la « fiche shinobi »** (référence de rendu, DA §4.1-4.2) : fiche shinobi « portrait en
    scène » (remplace l'écran de stats), sélection / création de personnage (lac, lucioles, vrais blasons de clan,
    taille au cm, apparence en vignettes, pipette de couleur, 35 coiffures, accessoire bandeau de Konoha, ambiances),
    chirurgie des yeux, scène de mort, menu d'interaction, concentration (ensō A), bibliothèque, Poste de garde,
    Infirmerie, sacoche, chat, Échap (plein écran), échoppe, réglages, carte, tablist.
  - **Performance** : causes trouvées et corrigées (éléments GUI regroupés `LotGui`/`LotBlit`, monde non rendu derrière
    les écrans opaques, flou supprimé) — DA §6 règles 7-8.
  - **Chirurgie des yeux** complète côté serveur (`medic/yeux`, fiole d'œil Nexo, cécité, bandage).
  - **Bibliothèques v2** : 7 types, fonds + dépôt, journal, lecture sur place, **vol**, **secrets** paramétrables.
  - Scène de mort à 10 s, corps du mort portable, correctif du portage (corps en l'air).
  - Parchemins = rouleaux SNR servis par Nexo ; boîte à outils de style `minecraft/mod-hud/tools/style/`.
  - Études : benchmark NydoMod / Ninshu / serveurs RP (`etudes/`).
- **À tester en jeu** (deux comptes) : toute la recette [`operations/RECETTE_GAMEPLAY.md`](./operations/RECETTE_GAMEPLAY.md).
- **Dette ouverte** : `tools/bbforge/`, `tools/blockbench-handpaint/`, `ShinobiSense/ByakuganVision.java` et
  `scripts/publish-mod-manifest.ps1` (chantiers d'autres sessions) toujours non commités.

---

## 1. Sprint 1 — ce qu'il reste pour le clore

| Livrable du Sprint 1 | État | Détail |
|---|---|---|
| Design **Stats** | ✅ | 6 stats, option B (23/09) — [`gameplay/STATS_ET_PROGRESSION.md`](./gameplay/STATS_ET_PROGRESSION.md) |
| Design **Progression** | ✅ | Grades RP (9 grades, 08/10) + XP invisible → points de stats (10/10) |
| Design **Parchemins** | ✅ | Règles validées le 08/10 — [`gameplay/APPRENTISSAGE.md`](./gameplay/APPRENTISSAGE.md) |
| Design **KO / mort RP** | ✅ | Implémenté KO-1 à KO-7 — [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md) |
| Design **Faction Konoha** | ✅ | v3 validée le 16/10, socle publié (hud 0.4.184), **validé en jeu le 17/10** ; coffres de section et portes à clé mis de côté le 18/10 ; mécaniques par section plus tard — [`gameplay/FACTION.md`](./gameplay/FACTION.md) |
| Code `StatsService` | ✅ | En service depuis le 28/09 |
| `ProgressionLadder` (rangs + seuils XP) | ✅ remplacé | Les grades ne montent plus à l'XP ; l'XP donne des points de stats |
| **8 icônes de recharge** | 🟡 3/8 | Existent : dash, double saut, saut de chakra. **Demandées à l'utilisateur** (il les dessine) : `combat.png`, `course_chakra.png` (32×32, frames empilées verticalement, `mod-hud/src/main/resources/assets/reborn/textures/gui/ability/`). Restent du plan : dodge, block, katon-D, suiton-D, doton-D, fūton-D |
| **Assets wave 1** : 3 clans starter complets (Uchiha, Hyuga, Neutre) | 🟡 | 5 tenues Uchiha seulement |
| **Build** : macro-terraforming du Pays du Feu | 🟡 | Terrain quasi fini (World Machine → World Painter), début de Konoha (état au 10/10, rapporté par l'utilisateur) |

---

## 2. Les 16 chantiers de la beta

### 🎨 Visuel

**HUD & icônes** — 🟡
- ✅ HUD combat (endurance en ensō, dégâts, combo), HUD KO / ATA, chat en panneau, éditeur de HUD, viseur, styles de
  noms, mode photo, galerie. Toutes les interfaces ont été refaites dans la DA Reborn (octobre).
- 🟡 Icônes : cf. §1. ⏳ 20 icônes de sorts + statuts (Sprint 4).
- Fiches : [`technique/MOD_HUD.md`](./technique/MOD_HUD.md), [`design/DIRECTION_ARTISTIQUE.md`](./design/DIRECTION_ARTISTIQUE.md).

**Assets character design** — 🟡 wave 1 partielle (cf. §1) ; ❓ **palettes de couleurs par clan** demandées pour la
création de personnage.

**Modélisation 3D** — 🟡 pipeline `bbforge` opérationnel (Kirin, Son Gokū, accessoires, parchemins en prod) ;
aucune intégration Model Engine côté serveur ; plugin Handpainted interrompu. [`design/MODELES_3D.md`](./design/MODELES_3D.md).

**Animations combat & mouvement** — ⏳ pour l'essentiel. Catalogue : [`gameplay/ANIMATIONS_BASE.md`](./gameplay/ANIMATIONS_BASE.md).
Animations des **exécutions** à créer (§2 RP).

### ⚔️ Combat

**Système de combat complet** (Tai / Ken / Nin / parade) — 🟡 M1 kenjutsu / taïjutsu + parade + endurance en
service ; ⏳ les 39 techniques (Sprints 2-3). Outil : Technique Creator (éditeur de graphes, panel `/abilities`,
`/sa preview`) — [`technique/AUDIT_TECHNIQUES_ET_CREATOR.md`](./technique/AUDIT_TECHNIQUES_ET_CREATOR.md).

**Spécificités par clan** — ⏳ (18 sorts claniques, Sprints 2-3). Byakugan « comme dans l'anime » ✅ (ShinobiSense,
0.4.152).

**Déplacement** — ✅ Naruto run, dash, double saut, saut de chakra, voies de mobilité ; ⏳ 3 styles de course
(Sprint 2).

**Statistiques & équilibrage** — ✅ en service ; ⚠️ dégâts pas à l'échelle des PV (playtest) ; ❓ points / plafond
(5/15 ou 6/20 évoqués) à discuter avec le staff.

### 📖 RP

**Progression** — ✅ grades (`/grade`), XP invisible, Mérite. ❓ **Usage des points de Mérite** à définir ; ❓ garder ou
non les **bonus passifs de grade** ; ⚠️ paliers de niveau (tests de la Feuille) pensés pour 1-17.

**Bibliothèque de parchemins & apprentissage** — ✅ lots 1-3 en prod, 🟢 lot 4 (mort RP) sur le dev.
❓ **Rendu de la concentration du chakra** à choisir (ensō A recommandé, ou trait B) — la mécanique tourne déjà sur le
dev avec un rendu jugé moche. ⏳ apprentissage des techniques de clan. [`gameplay/APPRENTISSAGE.md`](./gameplay/APPRENTISSAGE.md)

**KO / mort RP** — 🟢 KO-1 à KO-7 codés, 0.4.169 sur le dev, **à tester à deux comptes**. Reste : technique
`volonte` à créer (aujourd'hui personne n'a la Volonté), animations d'exécution, vente des exécutions, coma,
saignement. [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md)

**Faction** — ✅ socle (volet J, sections, grades de section, invitations) publié le 16/10, validé en jeu le 17/10 ;
⏸️ coffres de section et portes à clé mis de côté le 18/10 ; mécaniques par section plus tard.
[`gameplay/FACTION.md`](./gameplay/FACTION.md)

**Médical** — 🟡 paume de soin, bandages, `/soigner`, armoires, hôpitaux et lits ✅ ; ⏳ antidotes, coma,
`MedicalService` (Sprint 3). Lien avec le KO volontairement léger.

**Communication RP** (volière, annonces) — ✅ cloche et volières publiées le 16/10 (hud 0.4.184).

**Méditation** — en cut list (post-beta), mais la méditation de base existe (régénération du chakra, repos ATA).

### 🏗️ Monde

**Build** — 🟡 cf. §1. Carte du monde (touche M) générée depuis le vrai monde `PaysDuFeu06`, Konoha seulement ;
lieux placés « à l'œil », à corriger en jeu (`/carte set`).

---

## 3. Chantiers transverses

| Chantier | État | Où |
|---|---|---|
| **Poste de garde** (panel staff en jeu) + Infirmerie | ✅ en prod (0.4.157 → 0.4.160) ; ⏳ casier dans l'API, synchro des grades staff panel → jeu | [`staff/GUIDE_STAFF_EN_JEU.md`](./staff/GUIDE_STAFF_EN_JEU.md) |
| **Sélection / création de personnage en scène** | ✅ 0.4.167-0.4.168 ; ❓ palettes par clan ; vignettes de coiffures à agrandir | [`gameplay/PERSONNAGES.md`](./gameplay/PERSONNAGES.md) |
| **Candidature v2, douane en jeu, « Mes personnages »** | ❓ proposition du 24/10, relue (34 corrections), rien n'est codé ni maquetté ; 13 questions en attente (Q1 à Q13) ; vérification n° 0 (Guardian actif, `white-list` vanilla) avant tout code | [`gameplay/CANDIDATURE_ET_DOUANE.md`](./gameplay/CANDIDATURE_ET_DOUANE.md) |
| **Missions / entraînements** | 💡 idées consolidées, pas de système en jeu (hors roadmap beta) | [`gameplay/MISSIONS.md`](./gameplay/MISSIONS.md) |
| **Benchmark mods et serveurs RP** | ✅ étude du 12/10 (Nydo, Ninshu, Solve/FSC/Ketto/Zenkai) ; lots A→E proposés, ❓ décisions 11-16 ci-dessous | [`etudes/BENCHMARK_MODS_ET_SERVEURS.md`](./etudes/BENCHMARK_MODS_ET_SERVEURS.md) |
| **Carte du monde, échoppe, Échap, sacoche** | ✅ ; ⏳ solde RBCoins réel et activations (boost Discord, stream…) ; liens X / YouTube à confirmer | [`technique/MOD_HUD.md`](./technique/MOD_HUD.md) |
| **Météo client** (pluie, sable, brume) | ✅ 0.4.161-0.4.162 | `/meteo` |
| **Launcher** | ✅ 0.3.45 (fenêtre de mise à jour corrigée) ; ❓ bouton « Copier le lien » OAuth et/ou signature de code (Norton bloque le launcher non signé) | [`operations/RELEASING.md`](./operations/RELEASING.md) |
| **Technique Creator** | 🟡 compilateur, éditeur panel, `requires:`, preview faits (Sprint 1 S1) | [`technique/AUDIT_TECHNIQUES_ET_CREATOR.md`](./technique/AUDIT_TECHNIQUES_ET_CREATOR.md) |
| **Outillage 3D** (bbforge, plugins Blockbench) | 🟡 non commité | [`design/MODELES_3D.md`](./design/MODELES_3D.md) |
| **Documentation** | ✅ restructurée le 12/10 | [`README.md`](./README.md) |
| **Boards Trello / Miro** | ✅ synchronisés le 12/10 (cartes Done, descriptions réécrites, frames bilan et mort RP) | [`pilotage/BOARDS.md`](./pilotage/BOARDS.md) |
| **Serveur prod** | ⚠️ les plugins des sprints d'octobre ont été déployés sur le **dev** ; l'état exact de la prod n'est pas tracé (au 10/10, son ShinobiCore n'envoyait pas âge / taille / sexe). Avant la mise en prod : faire l'inventaire des jars, puis `spigot.yml` (plafond de vie) | [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md) |

---

## 4. Décisions qui attendent l'utilisateur

| # | Question | Contexte |
|---|---|---|
| 1 | ~~**Design Faction Konoha**~~ — tranché (v3 du 16/10, portes et coffres le 17/10) | [`gameplay/FACTION.md`](./gameplay/FACTION.md) |
| 2 | **Rendu de la concentration du chakra** : ensō A ou trait B | Mécanique active sur le dev avec un rendu rejeté |
| 3 | **Technique `volonte`** : la créer au catalogue (et comment l'apprendre) ou activer `ko.volonte.pour-tous` | Aujourd'hui personne n'a la Volonté |
| 4 | **Usage des points de Mérite** | Gagnés au grade, ne servent à rien pour l'instant |
| 5 | **Bonus passifs de grade** (PV, chakra, endurance, +1 à +3 aux stats) : garder ou retirer | Restés en place après la séparation XP / Mérite |
| 6 | **Palettes de couleurs par clan** pour la création | Demandé le 10/10, à concevoir |
| 7 | Points de stats / plafond (5/15, 6/20) | À discuter avec le staff |
| 8 | Vente des **exécutions** et des **jetons de réinitialisation** (boutique launcher ou échoppe en ryo) | Aucune boutique ne les vend |
| 9 | Launcher : bouton « Copier le lien » et/ou signature de code | Problème Norton chez des joueurs |
| 10 | Afficher ou non les chiffres sur les tuiles de la fiche shinobi | Infobulles déjà sans chiffres |
| 11 | Faction : reprendre **réputation par village, diplomatie, quotas de grades** (6 Jōnin, Sannin) ? | Benchmark §5 lot A3 |
| 12 | **Règles de combat** faciles (parade avec réarmement, garde haute/basse, pression de garde, tenketsu, loadout) : Sprint 1 ou 2 ? | Benchmark §5 lot B |
| 13 | **Bureau des missions** tenu par les joueurs (modèle Solve), par le staff, ou les deux ? | Benchmark §5 lot C |
| 14 | **Vol d'yeux** (extraction sur corps KO / cadavre validée par le staff, cécité, bandage, greffe) dans la beta ? | Benchmark §5 lot E1 |
| 15 | **Greffe d'ADN** (marchand type Kabuto) : compatible avec les clans sur candidature ? | Benchmark §2.6 |
| 16 | **Porte de sortie** dans la demande de RPK (modèle Solve) | Benchmark §5 lot A4 |

---

## 5. Dette et ménage

| Élément | État | Action |
|---|---|---|
| `tools/bbforge/` (seuls 9 fichiers sur `main`), `tools/blockbench-handpaint/` (réécriture en cours) | non commités | Avec l'accord de l'utilisateur |
| `minecraft/shinobi/ShinobiSense/…/ByakuganVision.java` | tourne sur le dev, non commité | Le commiter (session Byakugan) |
| `scripts/publish-mod-manifest.ps1` (réécrit pour la 26.2) | non commité | `chore(scripts)` séparé ; mettre à jour sa référence à `PUBLISH_PREFLIGHT.md` |
| `ShinobiCore/plugin.yml` : description de `/staffchat` tronquée (un `#` lu comme commentaire) | corrigée le 12/10 dans le dépôt | Part avec le prochain déploiement de ShinobiCore |
| Pack Nexo et datapack `reborn_effets` du dev | seulement dans `Downloads` | Les versionner dans `minecraft/server-config/` |
| `tools/map-render`, `tools/ui-art` | outils de génération utilisés en octobre | Vérifier qu'ils sont commités |
| Mot de passe SFTP du dev | passé en clair dans plusieurs conversations | **Le changer** sur Minestrator, puis mettre à jour le keyring |
| IP des serveurs dans la doc d'un dépôt **public** | retirées d'`ETAT_DES_LIEUX.md` le 12/10 ; elles restent dans `operations/` (nécessaires pour se connecter au VPS), dans `archives/` et dans l'historique Git | ❓ les sortir aussi d'`operations/` (vers `secrets/`) ? |

---

## 6. Tester la 0.4.169 (à faire avec deux comptes, serveur dev)

1. Clic droit sur un corps KO : rien ne s'ouvre, rappel « Menu d'interaction (R) ». R + clic : menu complet.
2. « Attraper à la gorge » : la prise tient plus de 30 s ; s'accroupir lâche le corps.
3. « Demander une RPK » : message au tchat staff. Valider : la victime reste en vie, le demandeur voit « Achever »
   et « Exécution ».
4. Achever : écran noir pour la victime, question au bout de 60 s. **Non** → sélection, corps au sol à l'apparence
   Reborn. **Oui** → vue grisée sans HUD, Échap pour partir.
5. Inconscient : 5 min sans `/hopital`, puis il reste au sol et `/hopital` apparaît.
6. Sous ATA : course, course de chakra, dash, techniques et coups d'arme refusés ; coup à mains nues accepté.
7. Défaite sur un terrain d'entraînement : PV pleins, sans KO.
8. `/xp niveau <joueur> 5` puis `/stats` : 4 points à répartir, aucun niveau affiché.
9. Logs de démarrage : `Enabling ShinobiCore`, `executions.yml` créé, « Stats : … remboursé » pour les persos concernés.
