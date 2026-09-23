# Session — design des statistiques

## 1. Sujet

Produire le design **Stats** du Sprint 1 S2. Une divergence bloquante entre le
plan et le code a été trouvée en cours de route, puis une étude comparative a
montré que les catégories elles-mêmes ne tiennent pas.

## 2. Ce qui est fait

Quatre livrables, dans l'ordre où ils ont été produits — l'ordre compte, chacun
a modifié le suivant.

### a. L'audit de l'existant — `072ef26`

[`docs/AUDIT_STATS_ET_PROGRESSION.md`](../../AUDIT_STATS_ET_PROGRESSION.md)

Avant d'écrire quoi que ce soit, mesure de ce qui tourne dans ShinobiCore.
**Le modèle du plan et celui du code sont incompatibles.**

| | Code (`LevelTable`) | Plan (carte `tstHtbat`) |
|---|---|---|
| Modèle | table de **17 niveaux** | 5 stats allouables |
| HP | 100 → **2 000** | 100 → ~200 |
| Chakra | 300 → **100 000** | 50 → ~200 |

Un **facteur 500** sépare les deux échelles de chakra au plafond. Quatre autres
écarts relevés, dont deux qui ne sont pas cosmétiques :

- `Rank` n'a pas d'`ACADEMIE` et place `SPECIAL_JONIN` **avant** `JONIN`, là où le
  plan les sépare de 90 000 XP.
- La roadmap écrivait « résistances via `Affinity` » — c'est **`ChakraAffinity`**.
  `Affinity` est un axe de build (`STRENGTH`/`INTELLIGENCE`/`AGILITY`), sans
  rapport avec le triangle des natures.

**Le chiffre qui a tranché** : les 218 abilities coûtent **6 à 50 chakra**
(médiane 50) face à un pool qui monte à 100 000. Au niveau 17, un personnage
enchaîne **2 000 techniques** — la ressource cesse d'être une contrainte dès le
niveau 7. Ce déséquilibre existait avant, indépendamment du choix de modèle.

### b. Les décisions — `13af9f2`

- ✅ **Voie A** : le plan fait foi, `StatsService` devient la source de vérité,
  `LevelTable` et `AffinityMultipliers` sont retirés. Échéance du 1er oct tenue.
  Argument décisif : la beta est fermée au staff, le coût de la reprise d'échelle
  ne sera **jamais plus bas qu'aujourd'hui**.
- ✅ **18 sorts claniques** (9 clans × 2).

### c. La spec — `6123805`

[`docs/SPEC_STATS_SERVICE.md`](../../SPEC_STATS_SERVICE.md)

Interface `StatsService`, allocation, formules dérivées, persistance, migration,
alignement des rangs, découpage d'implémentation en 7 étapes.

Deux points structurants qui valent d'être retenus :

- **La règle d'or** — aucun appelant ne réimplémente une formule. `ShinobiCombat`,
  `ShinobiAbilities` et le HUD passent tous par les méthodes dérivées du service.
  Sans ça, l'équilibrage d'octobre consiste à chasser des copies de formules dans
  trois plugins.
- **Les frontières** — `StatsService` possède les *maximums* ; `StaminaManager`
  (ShinobiCombat) et `ResourcePool` gardent la consommation. `StaminaManager` doit
  cesser de porter son propre max.

Également : `Affinity` **survit en étiquette RP** plutôt que d'être supprimé — il
est persisté partout et lu par le créateur de personnage, le retirer élargirait
la migration sans bénéfice.

### d. L'étude comparative — `9323e9a`

[`docs/ETUDE_STATS_COMPARATIF.md`](../../ETUDE_STATS_COMPARATIF.md)

Demandée après la spec, parce que les catégories du plan étaient explicitement
provisoires. Grands RPG, concurrents Naruto, littérature de game design.

**Le constat central** : tous les concurrents Naruto mappent leurs stats sur la
trinité de la fiction. Shindo Life nomme littéralement ses stats « Ninjutsu » et
« Taijutsu » ; Nin Online le fait par proxy. Le plan de Reborn est le seul à ne
pas le faire — alors que le jeu a une trinité établie (12 Tai / 12 Ken / 15 Nin)
et un arbre à 4 branches.

Conséquences vérifiables sur le plan d'origine :

- un joueur qui veut être **sabreur** n'a aucune stat où mettre ses points ;
- **Force** ne sert qu'au M1 taïjutsu → dump stat pour tout ninjutsuka ;
- **Précision** ne donne que +5 % de crit au cap → deuxième dump stat ;
- **Contrôle** et **Chakra** font tous deux « meilleur en ninjutsu ».

**Sur la courbe** : le linéaire du plan récompense le all-in, donc *fabrique* des
dump stats. Les soft caps d'Elden Ring (Vigor 40 puis 60, Mind 50, Endurance
15/30/50) donnent l'impact progressif recherché.

### e. L'atlas visuel

**<https://claude.ai/code/artifact/8a8d92af-8699-47d3-a694-6d4db7e336b2>**

Page publiée (artifact privé, même URL à chaque mise à jour). **18 familles de
systèmes** avec une maquette d'écran dessinée pour chacune, une carte
d'orientation à deux axes, les courbes comparées, et **10 propositions**.

Familles couvertes : Elden Ring, Shindo Life, Nin Online, Naruto Online, Vampire
la Mascarade, Fallout SPECIAL, World of Warcraft, Path of Exile, Ultima Online,
RuneScape, EVE Online, Project Zomboid, Deepwoken, Dark Souls, Naruto Precursors,
GTA RP (gym + réputation).

> ⚠️ L'atlas vit **hors du dépôt**. Il n'est pas versionné et n'apparaîtra pas
> dans un `git log`. Le contenu texte est repris dans
> `ETUDE_STATS_COMPARATIF.md` ; seules les maquettes sont exclusives à la page.

## 3. État de compilation / test

Sans objet — quatre documents, aucun code écrit. `StatsService` n'existe pas
encore : c'est le livrable du Sprint 1 S3 (25 sept → 1er oct).

Les chiffres cités dans l'audit sont **mesurés sur le code**, pas estimés :
`LevelTable.java` lu ligne à ligne, `chakra-cost` comptés par `grep` sur
`abilities.yml` (218 occurrences, min 6, max 50, médiane 50).

## 4. Ce qui n'est PAS fini

- ~~🔴 **Le §1 de `SPEC_STATS_SERVICE.md` est marqué provisoire.**~~ ✅ **Tranché le
  2026-09-23 : option B**, six stats. Le §1 est réécrit, avec trois principes en plus :
  aucun seuil de stat sur le RP, apprentissage libre des techniques (les stats agissent
  indirectement via coût, dégâts et maîtrise), grande échelle de chakra (~100 000 au cap,
  équilibrage par les coûts). Points par rang et plafond restent à discuter avec le
  staff (§1.5).
- ✅ **Le reste de la spec ne dépend pas du choix** et reste valable tel quel :
  API (§3), persistance et migration (§4), alignement des rangs (§5), séparation
  leviers / structurel (§6), découpage d'implémentation (§7).
- Le **design Progression** n'a pas été commencé. Il suit immédiatement, et la
  question §8.1 de la spec (allocation libre, ou contrainte par l'arbre à 4
  branches ?) se tranche avec lui.
- Les designs **Parchemins**, **Faction** et **KO** ne sont pas commencés. Ils ne
  dépendent pas de la décision sur les catégories — parallélisables.

## 5. Décisions ouvertes

### 🔴 Bloquante : les catégories

C'est le seul vrai blocage. Trois options chiffrées dans
`ETUDE_STATS_COMPARATIF.md` §5 :

| | Catégories | Remarque |
|---|---|---|
| **A** | Taïjutsu · Kenjutsu · Ninjutsu · Vigueur · Chakra | la branche Médical n'a pas de stat |
| **B** ⭐ | + Contrôle | correspondance exacte stats ⇄ branches ⇄ disciplines |
| **C** | le plan corrigé (Précision fusionnée, + une stat d'arme) | ne règle pas le fond |

**Recommandation : B**, pour une raison structurelle plus qu'esthétique — elle
fait coïncider trois systèmes qui doivent de toute façon être cohérents. Sinon on
maintient une traduction permanente entre trois vocabulaires.

### Les couches, indépendantes du choix ci-dessus

Sept mécaniques qui se posent par-dessus n'importe quelle option, et ne
s'excluent pas entre elles :

| | Couche | Source | Note |
|---|---|---|---|
| D | Budget total | Ultima Online (cap 700) | exige un `respec` accessible |
| E | Grades de scaling par technique | Dark Souls (S→D) | le Technique Creator peut porter le champ |
| F | Maîtrise par la pratique | Project Zomboid (×3→×16) | **la moins chère** — les parchemins sont déjà au Sprint 3 |
| G | Entretien / décroissance | GTA RP gym | la plus risquée |
| H | Seuils d'accès | Fallout, Path of Exile | marche avec le gate `requires:` déjà livré |
| I | Axe social | Vampire la Mascarade | aucun concurrent Naruto ne le fait |
| J | Entraînement hors ligne | EVE Online | pour les joueurs occasionnels |

**Assemblage proposé pour décembre** : B + soft caps + E + H, avec le
multiplicateur clanique si le temps le permet. Le reste après la beta.

### Autres questions ouvertes

- Points par rang et plafond — la spec propose **3** et **10**, à challenger.
- Le **Médical** : stat dédiée (option B) ou simple branche d'arbre ?
- Le **multiplicateur clanique** : permanent, ou bonus de départ ?
- Les **résistances par nature** (`ChakraAffinity`) : dans les stats, ou à côté ?
- Les **seuils de soft cap** : à calibrer en jouant, pas sur le papier.

## 6. Fichiers touchés

```
A  docs/AUDIT_STATS_ET_PROGRESSION.md   (072ef26, + note de décision en 13af9f2)
A  docs/SPEC_STATS_SERVICE.md           (6123805, §1 marqué provisoire en 9323e9a)
A  docs/ETUDE_STATS_COMPARATIF.md       (9323e9a)
M  docs/ROADMAP_BETA_2026.md            (§3.2 — 18 sorts claniques acté, 13af9f2)
```

Hors dépôt : l'atlas publié (URL en §2.e).
