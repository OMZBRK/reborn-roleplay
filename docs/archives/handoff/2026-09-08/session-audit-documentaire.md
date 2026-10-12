# Session — Audit documentaire, branches et Creator Tools

> Session Claude Code « repository documentation audit », 2026-09-08, PC portable.
> Tout ce qui suit est **commité et poussé**. Rien de cette session n'est resté
> dans le working tree.

---

## 1. Sujet

Trois chantiers enchaînés : remettre la documentation en accord avec la réalité
du projet, assainir les branches GitHub pour que le travail circule entre les
machines, et construire la suite Creator Tools dans Blockbench.

---

## 2. Ce qui est fait

### 2.1 Passe de cohérence docs ⇄ Trello ⇄ Miro ⇄ code (PR #8, mergée)

**Le constat** : le plan (Trello + Miro, refaits le 4 septembre) était sain, mais
la documentation du dépôt était restée à mai-juin 2026 — **deux migrations de
retard**. Elle décrivait Minecraft 1.21.1 et Java 21 alors que la production
tourne en **26.2 / Java 25**, et un périmètre de 6 sous-projets Minecraft alors
qu'il y en a 8, plus les 6 plugins Shinobi que **aucun document ne mentionnait**.

Le trou le plus dangereux : **le saut 26.1 → 26.2 n'était documenté nulle part**.
Quelqu'un qui suivait `MIGRATION_26.1.md` à la lettre reconstruisait un modpack
26.1 incompatible avec le serveur.

Documents créés :

| Fichier | Rôle |
|---|---|
| `docs/ETAT_DES_LIEUX.md` | **Le présent** — versions, URLs, artefacts publiés, périmètre réel, deux trains de release |
| `docs/ROADMAP_BETA_2026.md` | **Le futur** — 4 sprints, 16 chantiers, chiffres consolidés, cut list |
| `docs/AUDIT_COHERENCE.md` | Les écarts relevés, avec la source qui fait foi pour chacun |
| `docs/MIGRATION_26.2.md` | La migration courante + check-list pour la 26.3 |
| `docs/MCP_OUTILLAGE.md` | Faisabilité MCP Blockbench / Discord / Blender |
| `docs/adr/0002` → `0006` + index | Plateforme 26.x, Shinobi dans le monorepo, abandon MCEF, pipeline Nexo, monnaies |

Corrections : MC et Java partout ; `api.reborn-rp.fr` → `.com` ; tag `mods-v1` →
`mods-v3.1.x` ; arborescences complétées. `README` et `CLAUDE.md` **se
contredisaient** sur `apps/admin` (« placeholder » d'un côté, panel Next.js de
l'autre) — le panel est live. `MIGRATION_26.1` et `CHANGELOG_DESIGN_V2` marqués
archives, `STAFF_BETA` et `DEPLOY` marqués « from scratch » avec la table des
écarts vs la prod. Le `PLAN` reçoit un bandeau d'historicité et son §18
(« Semaine 1 à 6 », réalisé depuis avril) est remplacé par un état d'avancement
daté. 5 dossiers vides morts supprimés.

**Trello et Miro réalignés en parallèle** sur les mêmes chiffres : 3 cartes
séparateur archivées, `In Production` vidée vers `Done` (elles étaient livrées),
5 chantiers livrés ajoutés, les 16 cartes de chantier réécrites (« T3/T4 2026 »
remplacé par le numéro de sprint), placeholder `x` du Miro rempli, bandeau
« frame obsolète » sur le HUD v1, nouveau frame **📌 État réel**.

### 2.2 Assainissement des branches (PR #9, #10, mergées)

**Le problème de fond** : ce qui était publié ne revenait jamais sur une branche
partagée. Le launcher **0.3.42** tournait en production depuis le 2 septembre
(confirmé en interrogeant `/v1/launcher/update`) alors que `main` était à 0.3.40.
Un clone du dépôt ne reproduisait pas ce que les staffs exécutent.

- **PR #9** — launcher 0.3.41 + 0.3.42 remis sur `main`.
- **PR #10** — mode « build » serveur + `reborn-hud` 0.4.133, publié en
  `mods-v3.1.85`, également absent de `main`.

**`feature/migrate-26.2` était un piège**, pas seulement une branche en retard :

- nom trompeur — la migration 26.2 était sur `main` depuis le 19 août ;
- **10 de ses 22 commits étaient déjà sur `main`** sous un autre hash ;
- son merge aurait **ressuscité les 43 `.ogg` d'OST** dans `mod-hud/src/main/resources/`
  (exactement ce que `PUBLISH_PREFLIGHT.md` §1 interdit — le jar était passé de
  21 à 94 Mo) et le module `backpack/` retiré par le sprint de consolidation ;
- 7 conflits, dont plusieurs sur du code en production.

Traitement : archivée intacte en **`archive/migrate-26.2-wip`**, puis ses 6
commits réellement uniques (modrinth-sync) rebâtis sur `main` → **PR #11**, un
seul conflit dans `apps/bot/src/webhook-server.ts` (`main` y avait ajouté
`postClaudeNotification`, la branche `postModsUpdate`, au même endroit — les deux
conservés).

### 2.3 Portabilité Windows ⇄ macOS (PR #12, mergée)

- **Les 5 `gradlew` étaient en mode `100644`** → `Permission denied` sur Mac
  après chaque clone. Passés en `100755`.
- **`.gitattributes`** ajouté : sans lui, chaque poste applique son propre
  `core.autocrlf` et un fichier apparaît entièrement modifié alors que seule la
  fin de ligne a changé. LF partout, CRLF pour `.bat`/`.cmd`/`.ps1`. La
  renormalisation est un no-op — l'index était déjà en LF.
- **Un jar décompressé était commité** dans `minecraft/mod-hud/` :
  `reborn-hud-0.4.115.jar` dézippé — 289 `.class`, 253 assets dupliqués à
  l'identique de `src/main/resources/`, un `META-INF/MANIFEST.MF` de build, et un
  `fabric.mod.json` portant `"version": "0.4.115"` en dur là où la source a
  `${version}`. Soit **deux `fabric.mod.json` concurrents dans le même projet**.
  Retiré après vérification qu'aucun `sourceSet` ne référence ces chemins ;
  garde-fou ajouté au `.gitignore` du mod.
- **`docs/MULTI_POSTES.md`** : ce qui ne voyage pas dans git, le bootstrap Mac,
  et le fait que **les worktrees sont machine-locaux**.

### 2.4 Creator Tools Blockbench (PR #13, ouverte)

`tools/blockbench-handpaint` → **`tools/blockbench-creator-tools`**. Le plugin ne
portait que AO et Shade plus un Lighting non câblé ; il couvre désormais les
trois workspaces de la suite RuneFist.

- **🧱 Blockout Canvas** (nouveau) — Block, Chain, Pose, Reuse. Le Chain a un
  mode « enroulé » où chaque copie repart du repère de la précédente : les
  rotations s'accumulent et la chaîne se courbe (queue, vrille). Quatre pas à 90°
  referment exactement le cercle, propriété testée.
- **🎨 Handpainted Workflow** (complété) — ajout de **Edges** (travaille en espace
  UV, donc les coutures d'îlots reçoivent aussi leur trait), **Gradient**,
  **Surfaces** (déterministe : même graine = même grain).
- **🎬 Motion Lab** (nouveau) — Pose, Mirror, Shape, Keep. Principe : **on ne
  resample jamais**, on déplace les clés existantes.

**`tools/*` rejoint le workspace pnpm** — le plugin avait son propre lockfile et
n'était pas déclaré, donc `pnpm install` à la racine n'installait pas ses
dépendances et `pnpm build` échouait sur un clone frais.

Trois bugs trouvés par les tests pendant l'écriture :

1. `anticipate()` utilisait la même constante aux deux termes et **retombait à 0
   en fin de course** — l'animation serait revenue à son point de départ.
2. `mirrorBoneName()` ratait `leftArm`/`rightArm`, **la convention des modèles
   Minecraft vanilla** : il exigeait un séparateur.
3. `blockbench-types` plafonne les plugins à 3 tags ; j'en avais mis 4.

---

## 3. État de compilation / test

| Élément | État |
|---|---|
| Creator Tools — tests | ✅ **55 tests passent** (`pnpm --filter reborn-creator-tools test`) |
| Creator Tools — types | ✅ `tsc --noEmit` propre |
| Creator Tools — build | ✅ `dist/reborn_creator_tools.js`, 109 Ko |
| Creator Tools — **en jeu** | ❌ **jamais chargé dans Blockbench** |
| `apps/bot` (conflit résolu) | ✅ `tsc --noEmit` à 0 erreur |
| `apps/api` (PR #11) | ⚠️ 56 erreurs `tsc` contre 54 sur `main` — les 2 en plus sont `@nestjs/schedule` non installé. Les 54 autres pré-existent (client Prisma non régénéré localement) |
| Docs | ✅ 0 lien interne cassé (vérifié) |
| Mods / plugins Java | ❌ **non compilés** — pas de JDK 25 ni de Maven sur ce poste |

**Le point franc** : le baking Blockbench ne peut pas être testé hors de l'app.
La logique de décision est testée (55 tests sur `src/core/`), mais l'orientation
UV des cubes, les normales et l'accès aux animators de Motion Lab demandent une
validation à l'œil. Les points à contrôler sont listés dans le README du plugin.

---

## 4. Ce qui n'est PAS fini

- **PR #11 et #13 sont ouvertes**, pas mergées.
- **PR #11 exige un `pnpm install`** avant déploiement (ajoute `@nestjs/schedule`
  à l'API) et un `docker compose … up -d --build api bot`.
- **Render Studio** (4ᵉ workspace des Creator Tools) : non commencé, planifié
  comme chez la référence.
- **Motion Lab** : les accès aux animators passent par des replis défensifs
  (`interpolate`, `createKeyframe`). Ça n'a jamais tourné sur une vraie
  animation.

---

## 5. Décisions ouvertes

1. 🔴 **`reborn-hud` 0.4.134 est en production et sur aucune branche distante.**
   Son source n'est pas sur ce PC — pas de worktree, pas de disque `D:`. Il est
   sur la machine qui a publié, et c'est de là qu'il faut le commiter. Tant que
   ce n'est pas fait, `main` ne reproduit pas la prod.
2. **Monnaies** (ADR 0006, statut *proposé*) — proposition : **Ryo** (gagné en
   jeu) + **RBCoins** (acheté / bug bounty), sans conversion, et retrait de
   « ZK Coin », vestige de l'identité Zenkai sur le produit payant. Renommé
   partout, à valider ou corriger.
3. **Sorts claniques** — tranché à **18** (9 clans à mécaniques × 2), Neutre et
   Autre n'en ayant pas par définition. Si « Autre » doit recevoir un kit
   générique staff, on repasse à 20 et il faut réaligner les boards.
4. **`archive/migrate-26.2-wip`** — 4 commits `panel-files` n'ont pas été repris
   (ils semblaient re-landés sur `main` sous un autre titre). À vérifier avant de
   supprimer l'archive.
5. **Outillage MCP** (`docs/MCP_OUTILLAGE.md`) — ~6 jours pour les trois
   namespaces les plus rentables. Hors des 16 chantiers de la beta, à caler
   entre le Sprint 1 et le Sprint 2.

---

## 6. Fichiers touchés

**Tout est commité.** Rien de cette session ne traîne dans le working tree.

### Créés

```
.gitattributes
docs/AUDIT_COHERENCE.md
docs/ETAT_DES_LIEUX.md
docs/ROADMAP_BETA_2026.md
docs/MIGRATION_26.2.md
docs/MCP_OUTILLAGE.md
docs/MULTI_POSTES.md
docs/adr/README.md
docs/adr/0002-cible-minecraft-26x-java-25.md
docs/adr/0003-shinobi-dans-le-monorepo-deux-trains-de-release.md
docs/adr/0004-abandon-mcef-fond-menu-3d.md
docs/adr/0005-pipeline-assets-3d-nexo-panel.md
docs/adr/0006-monnaies-ryo-et-rbcoins.md
tools/blockbench-creator-tools/src/core/{chain,easing,field,noise}.ts
tools/blockbench-creator-tools/src/tools/{blockout,edges,gradient,motion,surfaces}.ts
tools/blockbench-creator-tools/test/{chain,easing,field,noise}.test.ts
```

### Modifiés

```
README.md · CLAUDE.md · PLAN_CONCEPTION_LAUNCHER.md
docs/{MAINTENANCE,STAFF_BETA,DEPLOY,MIGRATION_26.1,MOD_HUD_REDESIGN,ANIMATIONS_BASE,CHANGELOG_DESIGN_V2}.md
pnpm-workspace.yaml · pnpm-lock.yaml
tools/blockbench-creator-tools/{package.json,esbuild.mjs,README.md,.gitignore}
tools/blockbench-creator-tools/src/{index.ts,ui/panel.ts,core/geometry.ts}
```

### Renommés

`tools/blockbench-handpaint/**` → `tools/blockbench-creator-tools/**` (13 fichiers)

### Supprimés

`minecraft/mod-hud/{fr,META-INF,assets}/**`, `minecraft/mod-hud/fabric.mod.json`,
`minecraft/mod-hud/reborn-hud.mixins.json` (le jar décompressé, 544 fichiers) ·
5 dossiers vides morts.

### Hors dépôt

Trello : ~30 cartes créées ou réécrites, 2 listes renommées, 3 cartes archivées.
Miro : 6 widgets corrigés, 15 créés (frame **📌 État réel** + bandeau du frame
obsolète).
