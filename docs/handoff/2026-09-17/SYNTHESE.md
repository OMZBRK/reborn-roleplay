# Synthèse — journée du 2026-09-17

> **Une session Claude Code sur le PC portable**, de la réunification du dépôt
> jusqu'au design des stats.
>
> **À lire en premier depuis une autre machine.** La procédure de reprise est dans
> [`../README.md`](../README.md).

---

## En une page

| Session | Sujet | Code touché | Où c'est |
|---|---|---|---|
| [réunification du dépôt](./session-reunification-depot.md) | Fusion des 3 branches hors tronc, suppression des branches mortes, portabilité du clone | **67 + 33 fichiers**, TS · Rust · Java · Prisma | ✅ `main` |
| [boards et planning](./session-boards-et-planning.md) | Docs, Trello et Miro réalignés ; dates et checklists sur les 4 sprints | docs seuls | ✅ `main` + boards |
| [design des stats](./session-design-stats.md) | Audit de l'existant, 2 décisions actées, spec `StatsService`, étude comparative | docs seuls | ✅ `main` · ⏸️ **décision en attente** |

**Le fait le plus important de la journée** : `main` reproduit de nouveau la
production. Les trois branches qui portaient du code publié
(`feature/migrate-26.2`, `feature/emote-system`, `explore/overnight-2026-09-17`)
sont fusionnées et supprimées. La dette signalée par `AUDIT_COHERENCE.md` §3 est
soldée — `reborn-hud 0.4.135`, la version réellement livrée aux joueurs, est
enfin sur le tronc.

**Le point qui t'attend** : le design des stats est bloqué sur **un choix de
catégories** (§4). Tout le reste du Sprint 1 S3 en dépend.

---

## 1. Ce qui est sur `main` et utilisable

**Le tronc est réunifié.** Deux merges, 17 conflits arbitrés un par un et
documentés dans les messages de commit. Le Technique Creator, qui vivait coupé en
deux (panel d'un côté, jeu de l'autre), est désormais d'un seul tenant :
compilateur de graphes, module API `abilities`, éditeur React Flow, `/sa preview`
et le gate `requires:`. `modrinth-sync` a atterri aussi.

**Le dépôt est réellement clonable ailleurs** — c'est ce qui compte le plus pour
toi maintenant. Les 5 projets Gradle portaient tous une ligne `.gitignore` qui
excluait `gradle-wrapper.jar`, à contre-emploi : `gradlew` était commité, pas le
jar qu'il exécute. Sur un clone neuf, seul `mod-hud` buildait. Corrigé, les 5
wrappers sont sur `main` (Gradle 9.6.1, jars byte-identiques).

**Quatre manifests traînaient dans `secrets/`** malgré le `.gitignore` — commités
avant que la règle n'existe. Désuivis, conservés sur disque. Aucune fuite : ce
sont des manifests signés, et les vraies clés privées du dossier n'ont jamais été
suivies.

**Les documents disent de nouveau la vérité** : `ETAT_DES_LIEUX.md` §2 et §7,
`ROADMAP_BETA_2026.md` (risque 5 levé, Sprint 1 re-séquencé), `AUDIT_COHERENCE.md`
(note de résolution). Trois documents neufs sur les stats (§3).

---

## 2. État des branches

```
origin/main                          ← tout est là
origin/feat/blockbench-creator-tools ← 1 commit non mergé, hors périmètre du jour
```

Supprimées ce jour, toutes vérifiées contenues dans `main` avant suppression :
`feature/migrate-26.2` · `feature/emote-system` · `explore/overnight-2026-09-17` ·
`feature/modrinth-sync` · `archive/migrate-26.2-wip`.

> `feature/modrinth-sync` avait 6 commits hors `main` et 546 fichiers absents du
> tronc. Vérification faite : c'était **le jar décompressé par erreur** à la racine
> de `mod-hud`, que `main` avait déjà nettoyé et que son `.gitignore` documente.
> Rien perdu.

---

## 3. Ce qui n'est PAS compilé

🔴 **Toute la partie Java de la fusion.** Ce poste n'a ni JDK 25 ni Maven
(seulement Corretto 17). Les mods Fabric et les 6 plugins Shinobi sont cohérents
**textuellement** — j'ai contrôlé les 108 références de classes de
`ShinobiCore.java`, aucune manquante — mais **jamais compilés**.

**C'est la première chose à faire sur le PC principal** s'il a la toolchain :

```pwsh
$env:JAVA_HOME = "D:\dev-cache\jdk25\jdk-25.0.4+7"
cd minecraft\shinobi ; mvn clean package
cd ..\mod-hud ; .\gradlew build -x test --no-daemon
```

✅ **Vérifié vert** : `prisma generate`, `nest build`, `tsc --noEmit` sur bot /
admin / launcher-front / modrinth-sync, `cargo check`, et le self-test du
compilateur d'abilities (10 assertions).

Une seule vraie casse est apparue — une accolade fermante perdue dans ma
résolution en union sur `webhook-server.ts`, attrapée par `tsc` et corrigée en
`de96341`.

---

## 4. ⏸️ La décision qui t'attend

> ✅ **Levée le 2026-09-23 : option B.** Voir `SPEC_STATS_SERVICE.md` §1. Le
> paragraphe ci-dessous est conservé tel qu'écrit le 17.

**Quelles catégories de stats ?** C'est le seul blocage réel.

L'audit a montré que le modèle du plan (5 stats allouables) et celui du code
(table de 17 niveaux) sont **incompatibles** : un facteur 500 sépare les deux
échelles de chakra au plafond. Tu as tranché la **voie A** — le plan fait foi,
`StatsService` devient la source de vérité, `LevelTable` est retiré.

Mais l'étude comparative qui a suivi montre que **les catégories elles-mêmes ne
tiennent pas** : pas de stat pour le kenjutsu, Force et Précision en position de
dump stats, Contrôle et Chakra redondants. Trois options sont chiffrées dans
[`../../ETUDE_STATS_COMPARATIF.md`](../../ETUDE_STATS_COMPARATIF.md) §5 :

| | Catégories | Remarque |
|---|---|---|
| **A** | Taïjutsu · Kenjutsu · Ninjutsu · Vigueur · Chakra | la branche Médical n'a pas de stat |
| **B** ⭐ | + Contrôle | correspondance exacte stats ⇄ branches ⇄ disciplines — **recommandé** |
| **C** | le plan corrigé | ne règle pas le fond |

Plus sept **couches** indépendantes (budget total, grades de scaling, maîtrise par
la pratique, seuils d'accès, axe social, entraînement hors ligne, décroissance).

**Atlas visuel de l'étude** — 18 familles de systèmes avec maquettes :
<https://claude.ai/code/artifact/8a8d92af-8699-47d3-a694-6d4db7e336b2>

**Tant que ce n'est pas tranché** : la spec `StatsService` a son §1 marqué
provisoire, et le peuplement de `ProgressionLadder` (Sprint 1 S3) ne peut pas
commencer. Le reste de la spec — API, persistance, migration, alignement des
rangs, découpage d'implémentation — ne dépend pas du choix et reste valable.

**Décisions déjà actées ce jour** :
- ✅ **Voie A** pour le modèle de stats, échéance du 1er oct tenue.
- ✅ **18 sorts claniques** (9 clans × 2 ; Neutre et Autre sans kit signature).
- ✅ **Pas de backups `.bak`** sur le gestionnaire de fichiers staff.
- ✅ **Absorber la semaine perdue**, ship maintenu au 31 décembre.

---

## 5. Le calendrier, remis à plat

Sprint 1 S1 (11-17 sept) a dérivé : les 3 designs prévus n'ont pas été faits, la
semaine est partie sur le Technique Creator et la réunification. Ce n'est pas du
temps perdu — c'est l'outil qui rend tenable la cadence des 57 techniques — mais
le plan mentait, il est corrigé.

| Sprint | Échéance | Checklist |
|---|---|---|
| 1 — Foundation & Design | **8 oct** | 17 items, **6 cochés** |
| 2 — Combat & Konoha centre | 5 nov | 10 items |
| 3 — Systèmes RP & Faction | 3 déc | 9 items |
| 4 — Build & SHIP | 31 déc | 8 items |

S2 (18-24 sept) devient une semaine chargée à **5 designs** : les trois reportés
(Stats, Progression, Parchemins) plus Faction et KO.

**Chemin critique** : `StatsService` dû le 1er oct. Sans lui, aucune des 29
techniques d'octobre ne peut être équilibrée.

---

## 6. Points d'attention pour la prochaine session

- 🔴 **Compiler le Java** (§3) avant tout build ou publish.
- ⚠️ **Le MCP Trello s'est déconnecté** en fin de session (`ENOTFOUND
  mcp.trello.com`). Les mises à jour du board ont toutes été passées **avant** la
  coupure. Si tu reprends dessus, vérifier que la connexion revient.
- ⚠️ **`ETAT_DES_LIEUX.md` §4 est peut-être périmé** : il décrit deux manifests
  distincts (joueur + builder) alors que `5ab3976` a unifié le modpack et que
  `main` a retiré le chemin de lancement builder. Non vérifié, à confirmer.
- ⚠️ **Sur Miro, seul le frame « État réel » a été repris.** Les frames *Matrice
  Combat* et *Progression Timeline* n'ont pas été relus ; ils peuvent encore
  porter d'anciens chiffres.
- ℹ️ Les tags `mods-v*` **ne sont pas des marqueurs de version fiables**
  (`mods-v3.1.91` pointe sur 0.4.133 alors que 0.4.135 était publiée). C'est
  documenté dans `ETAT_DES_LIEUX.md` §2 — lire le manifest live, pas le tag.
