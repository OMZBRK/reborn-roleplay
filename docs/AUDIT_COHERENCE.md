# Audit de cohérence — docs / Trello / Miro / repo

> Passe complète du **2026-09-08** sur les quatre sources de vérité du projet :
> le code (`origin/main`), la documentation (`docs/` + `PLAN` + `README` + `CLAUDE.md`),
> le Trello public *[WIP] FR - Naruto* et le Miro *[WIP] FR - Shinobi Reborn*.
>
> Objectif : lister ce qui **se contredit**, dire **quelle source fait foi**, et
> pointer la correction. Chaque ligne est vérifiée sur le dépôt ou sur le board, pas
> déduite.

**Résultat en une phrase** : le *plan* (Trello + Miro, refaits le 4 sept) est
cohérent et à jour ; la *documentation du repo* est restée à l'état de mai-juin 2026
(MC 1.21.1 / Java 21 / périmètre 6 sous-projets), soit **deux migrations de retard** ;
et l'**état publié en production n'est sur aucune branche partagée**.

---

## 0. Hiérarchie des sources de vérité (décidée par cet audit)

| Question | Source qui fait foi |
|---|---|
| Ce qui tourne réellement (versions, URLs, artefacts) | [`docs/ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md) |
| Ce qu'on livre d'ici décembre 2026 | [`docs/ROADMAP_BETA_2026.md`](./ROADMAP_BETA_2026.md) ⇄ Trello `📅 Plan de vol` + `🎯 Beta Fin 2026` |
| Pourquoi une décision technique a été prise | `docs/adr/` puis `PLAN_CONCEPTION_LAUNCHER.md` |
| Comment on opère au quotidien | `docs/MAINTENANCE.md`, `docs/PUBLISH_PREFLIGHT.md`, `docs/RELEASING.md` |
| Design produit d'origine (historique) | `PLAN_CONCEPTION_LAUNCHER.md` (§1-17) |
| Visuel / wireframes | Miro (frames non dépréciés) |

Règle : **le Trello et le Miro décrivent le futur, `ETAT_DES_LIEUX` décrit le présent,
le PLAN décrit l'intention d'origine.** Un conflit entre les trois se tranche dans cet
ordre pour le futur, et par le code pour le présent.

---

## 1. Versions & plateforme — la plus grosse dérive

Le repo cible **Minecraft 26.2 / Java 25 / Fabric API 0.156.0+26.2 / loader 0.19.3**
(`minecraft/*/gradle.properties`, `fabric.mod.json` → `">=26.2 <26.3"`,
`launcher/src-tauri/src/launcher/game.rs:35` → défaut `26.2`).

| # | Où | Ce qui est écrit | Réalité | Correction |
|---|---|---|---|---|
| 1.1 | `CLAUDE.md` — Prerequisites | « Java 21 for the Paper plugin » | Java 25 (toolchain mods + plugins + serveur) | ✅ corrigé |
| 1.2 | `CLAUDE.md` — env vars | `REBORN_MC_VERSION` défaut `1.21.1` | défaut `26.2` | ✅ corrigé |
| 1.3 | `CLAUDE.md` — commandes | `./gradlew runServer` → « Paper 1.21.1 » | Paper/Purpur 26.x | ✅ corrigé |
| 1.4 | `README.md` — prérequis | « Java 21 » | Java 25 | ✅ corrigé |
| 1.5 | `docs/MAINTENANCE.md` §2b | `JAVA_HOME = …\jdk21.0.9_10` | JDK 25 (`D:\dev-cache\jdk25\jdk-25.0.4+7`) | ✅ corrigé |
| 1.6 | `docs/MAINTENANCE.md` §6 | « JDK 21 requis » (plugin Guardian) | JDK 25 | ✅ corrigé |
| 1.7 | `docs/MAINTENANCE.md` §0 | `Mods release tag: mods-v1` | `mods-v3.1.90` | ✅ corrigé |
| 1.8 | `docs/MAINTENANCE.md` §2 | exemple « Sodium 0.6.13 → 0.7.0 », jar `+1.21.1` | versions 26.2 | ✅ corrigé |
| 1.9 | `docs/STAFF_BETA.md` §7-8 | liste de mods et manifeste d'exemple en `1.21.1` | 26.2 | ✅ corrigé + doc marquée « from scratch / historique » |
| 1.10 | `PLAN` §9.5 | « Plugin Paper **Java 21** » | Java 25 | ✅ corrigé |
| 1.11 | `docs/MOD_HUD_REDESIGN.md` §8 | « Power-of-two non requis (MC 1.21) » | MC 26.x | ✅ corrigé |
| 1.12 | `docs/MIGRATION_26.1.md` | tout le document s'arrête à 26.1.2 | on est passé à **26.2** ensuite | ✅ doc marquée archive + [`MIGRATION_26.2.md`](./MIGRATION_26.2.md) créée |

> **Rien ne documentait le saut 26.1 → 26.2.** C'était le trou le plus dangereux :
> quelqu'un qui suit `MIGRATION_26.1.md` à la lettre reconstruit un modpack 26.1
> incompatible avec le serveur.

---

## 2. Périmètre du monorepo — ce qui existe et n'était nulle part

`README.md` et `CLAUDE.md` décrivaient **6** sous-projets Minecraft et **2** packages.
Le dépôt en contient bien plus.

| Chose réellement présente sur `main` | Documentée avant ? |
|---|---|
| `minecraft/shinobi/` — **6 plugins Maven** (`ShinobiCore`, `Abilities`, `Combat`, `Learning`, `Sense`, `Tail`) | ❌ (et `MIGRATION_26.1.md` affirmait encore « repo séparé `ShinobiReborn`, non dans ce repo ») |
| `packages/manifest-uploader` (Rust) | ❌ dans le README (mentionné seulement dans MAINTENANCE §14) |
| `tools/blockbench-reborn-compositor` — plugin Blockbench, compositeur de skins | ❌ |
| `tools/blockbench-handpaint` — plugin Blockbench, AO/Shade hand-paint | ❌ |
| `tools/claude-hooks` — hook Claude Code → notif Discord via le bot Reborn | ❌ |
| `scripts/publish-launcher.ps1`, `scripts/publish-mod-manifest.ps1` | partiellement (MAINTENANCE) |
| `apps/api/src/` : `wiki`, `files`, `game`, `events`, `incidents`, `menu`, `oral-slots`, `security`, `shots`, `social`, `steam`, `upload`, `audit` | ❌ (`PLAN` §10 ne liste que 13 groupes d'endpoints) |
| `apps/admin` : pages `wiki`, `wiki/ideas`, `files`, `anomalies`, `audit`, `inbox`, `oral-slots`, `2fa` | ❌ — et `CLAUDE.md` disait encore « Next.js panel (**placeholder**) » |
| `reborn-design-prep/` (artefacts des sessions design) | ❌ |

**Contradiction interne** en prime : `README.md` décrivait `apps/admin` comme
« Panel staff Next.js 15 » pendant que `CLAUDE.md` le décrivait comme un
« placeholder ». Les deux fichiers du même repo se contredisaient. ✅ corrigé.

### Dossiers morts à supprimer

| Chemin | Contenu | Verdict |
|---|---|---|
| `minecraft/mod-inventory/` | vide (créé le 21/05, 0 fichier, non suivi par git) | à supprimer — l'inventaire vit dans `mod-hud` + `ShinobiCore` (cf `docs/INVENTAIRE_ET_COSMETIQUES.md`) |
| `minecraft/plugin-hud/` | vide, non suivi | à supprimer |
| `minecraft/plugin-inventory/` | vide, non suivi | à supprimer |
| `src/main/resources/assets/reborn-hud/textures/icons/` **à la racine du repo** | vide | à supprimer — chemin d'assets de mod égaré à la racine |
| `docs/security/` | vide | à supprimer ou peupler (le README y renvoyait) |

---

## 3. Git — l'état publié n'est sur aucune branche partagée

C'est le point **le plus critique** de l'audit, parce qu'il n'est pas cosmétique.

| Artefact | Version **publiée / live** | Version sur `origin/main` | Écart |
|---|---|---|---|
| Launcher | **0.3.42** (release GitHub `v0.3.42`, 2026-09-02) | 0.3.40 | 2 commits, sur `feature/launcher` uniquement |
| `reborn-hud` | **0.4.134** (release `mods-v3.1.90`, 2026-09-04) | 0.4.132 | **sur aucune branche distante** |

Conséquences concrètes :
- Un `git clone` + build ne reproduit pas ce que les joueurs ont.
- `docs/PUBLISH_PREFLIGHT.md` §0 *documente* cette pratique (« le code game-side vit
  dans le worktree `emote-bend-test`, souvent avec beaucoup de modifs non commitées »)
  — c'est une explication, pas une excuse : la règle « ne pas publier depuis un tip
  périmé » a été respectée, mais l'inverse (« recommiter ce qui a été publié ») ne
  l'a pas été.
- **Action requise (humaine, pas dans cet audit)** : merger `feature/launcher` dans
  `main`, et commiter/pusher l'état `reborn-hud 0.4.134`.

### État des branches distantes au 2026-09-08

| Branche | En avance sur `main` | En retard | Contenu |
|---|---|---|---|
| `main` | — | — | trunk (launcher 0.3.40, MC 26.2) |
| `feature/launcher` | 2 | 57 | launcher 0.3.41 + 0.3.42 — **déjà en prod** |
| `feature/emote-system` | 2 | 31 | mode « build » serveur + hud 0.4.133 |
| `feature/migrate-26.2` | 22 | 103 | **nom trompeur** : ne contient pas la migration 26.2 (déjà sur `main`) mais `packages/modrinth-sync` (Slices 1→3b) + `apps/api/src/modrinth/` — à renommer `feature/modrinth-sync` |

---

## 4. Branding & monnaies — trois noms pour deux concepts

| Nom | Où | Signification supposée |
|---|---|---|
| **Ryo** | in-game (boutique de tenues, Trello ⚖️ « Drop rates Ryo ») | monnaie **gagnée en jeu** |
| **RBCoins 💎** | Trello, carte *Bug Bounty* | récompense **hors-jeu** (bounty) |
| **ZK Coin** | Trello *v1.1 Boutique launcher*, `PLAN` §11 | monnaie **achetée en euros** (Stripe) |

« ZK » = *Zenkai*, l'ancienne référence visuelle abandonnée en juin 2026 au profit
d'**Akatsuki**. Garder ce nom sur une monnaie premium fige une marque morte dans le
produit payant.

**Décision proposée (à valider par toi)** : deux monnaies, pas trois.
- **Ryo** — gagnée en jeu, dépensée en jeu (tenues, consommables).
- **RBCoins** — achetée en euros (Stripe), dépensée en cosmétiques ; sert aussi de
  récompense Bug Bounty. **« ZK Coin » est retiré.**

Autres résidus Zenkai à statuer :
- `PLAN` §1 « dans la lignée visuelle de **Zenkai Launcher** » → l'identité est
  Akatsuki depuis le 2026-06-21 (`docs/REBORN_ASEPRITE_PALETTE.md`). ✅ corrigé, avec
  la mention historique conservée.
- `docs/CHANGELOG_DESIGN_V2.md` décrit un design system « **Zenkai blue** »
  (`--accent`) alors que la palette vivante est crimson `#A0182B` + or `#D9A95E`.
  C'est un **document historique** (journal de la refonte v2) → ✅ marqué comme tel en
  en-tête, pas réécrit.
- `docs/MIGRATION_26.1.md` : « connect serveur minimaliste **Zenkai** », « 313 pistes
  **Zenkai** » → historique, laissé tel quel dans une archive datée.

---

## 5. Domaines & infra

| # | Source | Écrit | Réel |
|---|---|---|---|
| 5.1 | `PLAN` §10 | `https://api.reborn-rp.**fr**/v1/` | `https://api.reborn-rp.**com**/v1` |
| 5.2 | `docs/DEPLOY.md`, `docs/STAFF_BETA.md` | `api./panel.reborn-rp.**fr**` | `.com` |
| 5.3 | `docs/STAFF_BETA.md` §3 | VPS Hetzner CX22 4 €/mois | OVH VPS-1 Gravelines, `ubuntu@91.134.136.120` |

`STAFF_BETA.md` et `DEPLOY.md` sont des guides « from scratch » : garder des exemples
génériques est légitime, mais ils doivent **dire explicitement** qu'ils ne décrivent
pas l'installation en cours et renvoyer à `MAINTENANCE.md`. ✅ fait.

---

## 6. Roadmaps — deux plans parallèles qui ne se parlaient pas

| Plan | Support | Périmètre | Statut |
|---|---|---|---|
| MVP v1.0 → v1.3 | `PLAN` §11 | **launcher / écosystème web** | largement livré (MVP complet) |
| Beta Fin 2026 (4 sprints) | Trello `📅 Plan de vol` + Miro | **gameplay Minecraft** | en cours |

Aucun des deux ne référençait l'autre. Un lecteur du `PLAN` croit que la prochaine
étape est « Semaine 1 — créer le repo monorepo » (§18), écrit en avril 2026 et
intégralement réalisé depuis. ✅ `PLAN` §11 renvoie désormais à
`ROADMAP_BETA_2026.md`, et §18 est remplacé par un état d'avancement daté.

Les cartes Trello `👑À venir` (v1.0.5 social, Steam, boutique, Twitch, lore interactif,
profil, achievements) sont **la suite du PLAN §11**, c'est-à-dire **post-beta**. Elles
ne sont pas dans les 4 sprints. ✅ la liste est renommée pour le dire.

---

## 7. Incohérences chiffrées entre Trello et Miro

### 7.1 Nombre de techniques de combat — trois chiffres différents

| Source | Chiffre |
|---|---|
| Miro *Système Combat — Matrice*, pied de page | « Beta total : 12 Tai + 12 Ken + 25 Nin = **49** » |
| Trello *MASTER PLAN*, section Risques | « 49 techs trop ambitieux — **cap à ~35** » |
| Somme réelle des sprints 2 + 3 | 12 Tai + 12 Ken + 5 Nin D + 10 Nin C/B = **39** |

La matrice Miro décrit la **cible finale** (tous rangs D→S), pas le périmètre beta.
**Chiffre retenu : 39 techniques en beta** (Nin D + C + B), les rangs A et S étant
explicitement dans la *cut list* du Master Plan. ✅ Miro et Trello alignés sur 39,
avec la cible 49 affichée comme « vision complète ».

### 7.2 Sorts claniques

| Source | Chiffre |
|---|---|
| Miro Matrice | « 2 spells × **11** clans = **22** » |
| Trello Sprint 3 / S4 + Miro Sprint Timeline | « **20** spells claniques restants (**10** clans × 2) » |
| Trello *Spécificités par clan* | **11** clans listés (dont « Neutre » = *pas de bonus clanique* et « Autre » = personnalisable staff) |

Cohérent une fois explicité : **11 entrées de clan, 9 clans à mécaniques + Neutre
(aucune) + Autre (staff)**. Le sprint 3 livre les 20 sorts des clans restants après
Uchiha (sprint 2) et Hyuga (sprint 3 S1). ✅ formulation corrigée des deux côtés.

### 7.3 Calendrier — une semaine manquante

Le Master Plan dit « 17 semaines depuis le 4 sept », mais la grille de sprints
commence au **11 sept** et finit le 31 déc = **16 semaines**. La semaine du 4 au 10
septembre n'est affectée à rien. ✅ Sprint 1 étendu au **4 sept** (semaine S0 =
consolidation Git + docs, ce qui correspond à la réalité de ce qui s'y est passé).

### 7.4 Trimestres de livraison contradictoires

Les 16 cartes `🎯 Beta Fin 2026` portent « 📅 Livraison : **T3 2026** » ou « Q3 2026 »
pour des travaux que le plan de vol place en **octobre → décembre** (= T4). Le T3 se
termine le 30 septembre. ✅ remplacé par le **numéro de sprint** sur chaque carte —
une seule échelle de temps, plus de trimestres.

### 7.5 Placeholder Miro

Le frame *Roadmap Beta Fin 2026*, colonne `📖 SYSTÈMES RP`, contient une carte dont le
texte est littéralement `x`. Le Trello a bien 16 chantiers ; le Miro en affiche 15 + ce
trou. Le chantier manquant est **💀 Système KO / mort RP**. ✅ carte remplie.

### 7.6 Frame déprécié laissé en place

`⚠️ [DÉPRÉCIÉ] HUD Wireframe v1 — voir HUD v2 aligné mod-hud` est toujours sur le
board, à côté du v2. Un board de transparence communautaire ne devrait pas afficher
deux wireframes contradictoires. ✅ contenu vidé et frame réduit à un renvoi.

---

## 8. Trello — hygiène de board

| # | Constat | Correction |
|---|---|---|
| 8.1 | 3 cartes « séparateur » sans titre (`--------------`, `-----`, `-------`) polluent 3 listes | archivées |
| 8.2 | Liste `☄️⚡️In Production` contient 3 cartes **déjà livrées et publiées** (launcher 0.3.42 le 02/09, sprint publish UI, consolidation combat) | déplacées vers `✅ Done` |
| 8.3 | La migration **26.1 → 26.2** n'a aucune carte, alors que la migration 1.21.1 → 26.1.2 en a une dans `Done` | carte ajoutée dans `Done` |
| 8.4 | Chantiers livrés absents du board : panel **Wiki** + **Gestionnaire de fichiers** scopé par grade, **guide Nexo staff**, **outils Blockbench** (compositeur + hand-paint), **sacoche RP / inventaire** | cartes ajoutées |
| 8.5 | Chantier **modrinth-sync** (22 commits non mergés) invisible | carte ajoutée dans `In Production` |
| 8.6 | Date d'échéance du board fixée au **2026-09-04** (dépassée) | à retirer côté UI (non modifiable par l'API board) |
| 8.7 | La liste `🎯 Beta Fin 2026 (Objectif année)` et la liste `📅 Plan de vol` décrivent le même périmètre sous deux angles sans se citer | références croisées ajoutées dans les descriptions |

---

## 9. Documentation — trous et pipelines contradictoires

| # | Constat | Correction |
|---|---|---|
| 9.1 | L'index de docs du `README` listait **5** documents sur **17** | ✅ index complet |
| 9.2 | `docs/ANIMATIONS_BASE.md` propose de « hand-author les `.json` **GeckoLib** » alors que la chaîne de prod est **PlayerAnimationLib / EmoteCraft** (`docs/EMOTES.md`) | ✅ note d'alignement ajoutée : format d'échange = animation JSON PAL (`player_animation_library`), `.emotecraft` pour la distribution serveur |
| 9.3 | Aucun ADR depuis le 0001 (mai 2026), malgré la consigne du PLAN d'en écrire un par décision structurante | ✅ ADR 0002 → 0006 rédigés |
| 9.4 | Décisions majeures non documentées : abandon de MCEF, fin de Yarn (jar déobfusqué), Shinobi rapatrié dans le monorepo, **deux trains de release distincts**, pipeline Nexo | ✅ couvertes par les nouveaux ADR |
| 9.5 | `docs/PUBLISH_PREFLIGHT.md` référence un « skill `reborn-ops` » qui n'existe pas dans le repo | signalé — soit committer le skill dans `.claude/skills/`, soit retirer la référence |
| 9.6 | `docs/ETUDE_NINSHU_ORIGINS.md` (rétro-ingénierie, 27 Ko) n'est référencé nulle part | ✅ indexé, avec sa mention d'usage privé |

---

## 10. Ce qui, après vérification, **n'était pas** une incohérence

Pour éviter les faux positifs dans les prochains passages :

- **`REBORN_SERVER_HOST/PORT` absents du `.env` d'exemple mais présents en dur dans le
  build** : c'est voulu — les valeurs sont bakées à la compilation via les variables
  `*_BUILD` (cf `MAINTENANCE.md` §7).
- **`infra/nginx/` conservé alors que la prod tourne sous Caddy** : le README le
  marquait déjà « legacy ».
- **`plugin-ost` / `mod-ost` documentés partout alors que l'OST a été « retiré »** :
  le retrait annoncé dans le sprint de consolidation est un **retrait du modpack
  actif**, pas une suppression du code. Les deux projets restent valides et
  redéployables. ✅ précisé dans `ETAT_DES_LIEUX`.
- **Deux jeux de commandes de build (Gradle pour les mods, Maven pour Shinobi)** :
  normal, ce sont deux écosystèmes.
- **`docs/STAFF_BETA.md` qui double `docs/DEPLOY.md`** : périmètres différents
  (checklist bout-en-bout vs détails infra), les deux se citent.

---

## 11. Actions restantes (hors périmètre de cette passe)

Ces points demandent une décision ou une action humaine :

1. **Merger `feature/launcher` (0.3.42) dans `main`** et **commiter `reborn-hud`
   0.4.134**. Tant que ce n'est pas fait, `main` ne reproduit pas la prod. — 🔴 P0
2. **Renommer `feature/migrate-26.2` → `feature/modrinth-sync`** puis la rebaser sur
   `main` (103 commits de retard) et la merger ou la fermer. — 🟠 P1
3. **Valider la décision monnaies** (§4) : Ryo + RBCoins, retrait de « ZK Coin ». — 🟠 P1
4. **Supprimer les 4 dossiers morts** de §2 et `docs/security/`. — 🟡 P2
5. **Statuer sur le skill `reborn-ops`** (§9.5) : le committer ou retirer la référence. — 🟡 P2
6. **Retirer la date d'échéance du board Trello** (2026-09-04, dépassée) depuis l'UI. — 🟡 P2

---

*Audit produit le 2026-09-08. Prochaine passe recommandée : à la fin du Sprint 2
(début novembre), pour vérifier que le plan de vol et l'état réel n'ont pas
recommencé à diverger.*
