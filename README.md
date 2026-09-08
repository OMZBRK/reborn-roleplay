# Reborn Roleplay — Monorepo

Monorepo de l'écosystème **Reborn Roleplay** : launcher desktop, API backend, panel
staff, bot Discord, les mods clients Fabric, les plugins serveur Paper, et l'outillage
de production (Blockbench, publication, hooks).

> **Par où commencer :**
> - Ce qui tourne aujourd'hui → [`docs/ETAT_DES_LIEUX.md`](./docs/ETAT_DES_LIEUX.md)
> - Ce qu'on livre d'ici décembre → [`docs/ROADMAP_BETA_2026.md`](./docs/ROADMAP_BETA_2026.md)
> - Pourquoi les choses sont comme ça → [`PLAN_CONCEPTION_LAUNCHER.md`](./PLAN_CONCEPTION_LAUNCHER.md) + [`docs/adr/`](./docs/adr/)
> - Faire une mise à jour en prod → [`docs/MAINTENANCE.md`](./docs/MAINTENANCE.md)

## Arborescence

```
reborn-roleplay/
├── apps/
│   ├── launcher/     Tauri 2 + React 19 + TS — client desktop
│   ├── api/          NestJS 11 + Prisma + Postgres + Redis (préfixe /v1)
│   ├── admin/        Next.js — panel staff (dashboard, whitelist, tickets,
│   │                 joueurs, audit, anomalies, inbox, oral-slots, wiki,
│   │                 gestionnaire de fichiers scopé par grade, 2FA)
│   └── bot/          discord.js v14 (ESM) — slash commands + webhooks :3001
├── packages/
│   ├── manifest-signer/    CLI TS — signature Ed25519 du manifest
│   ├── manifest-uploader/  CLI Rust — POST du manifest signé (JWT keyring)
│   └── shared-types/       DTOs partagés
├── minecraft/
│   ├── mod-hud/            Fabric client — TOUTE la couche UI Reborn
│   ├── mod-integrity/      Fabric client — attestation play-token (uniquement)
│   ├── mod-ost/            Fabric client — décodage/lecture Ogg Vorbis
│   ├── plugin-guardian/    Paper — vérification du play-token au JOIN
│   ├── plugin-ost/         Paper — broadcast OST + zones + late-join
│   ├── shinobi/            6 plugins Paper/Purpur (Maven) : Core, Abilities,
│   │                       Combat, Learning, Sense, Tail
│   └── server-config/      templates server.properties / MagicSpells / Nexo
├── tools/
│   ├── blockbench-reborn-compositor/  plugin Blockbench — compositeur de skins
│   ├── blockbench-creator-tools/      plugin Blockbench — suite Creator Tools
│   │                                 (Blockout, Handpaint, Motion Lab)
│   └── claude-hooks/                  hook Claude Code → notif Discord
├── scripts/          publish-launcher.ps1 · publish-mod-manifest.ps1
├── infra/            docker-compose dev + prod, Caddy (nginx/ = legacy)
└── docs/             cf. index ci-dessous
```

> Spécification détaillée de chaque mod/plugin et séparation des responsabilités :
> [`PLAN_CONCEPTION_LAUNCHER.md §9`](./PLAN_CONCEPTION_LAUNCHER.md).

## Prérequis

| Outil    | Version min | Rôle                              |
|----------|-------------|-----------------------------------|
| Node.js  | 20+         | Launcher (front), API, panel, bot |
| pnpm     | 10+         | Workspaces / install              |
| Rust     | 1.77+       | Backend Tauri (launcher)          |
| Docker   | 24+         | Postgres / Redis local            |
| **Java** | **25**      | Mods Fabric **et** plugins Paper  |
| Maven    | 3.9+        | Plugins `minecraft/shinobi/`      |

> Sur Windows : Rust via [`rustup`](https://rustup.rs/) (toolchain
> `stable-x86_64-pc-windows-msvc`) + **Build Tools Visual Studio 2022** (C++).
> Le JDK 25 est installé en portable dans `D:\dev-cache\jdk25\jdk-25.0.4+7`.
> Le launcher télécharge lui-même la JRE que Minecraft utilise (piston-meta).

## Démarrage rapide

```pwsh
pnpm install                  # deps JS du workspace
pnpm infra:up                 # Postgres + Redis
pnpm --filter @reborn/api prisma migrate dev
pnpm api:dev                  # API sur :3000, préfixe /v1
pnpm launcher:dev             # fenêtre desktop
```

Côté Minecraft (projets isolés du workspace pnpm — ouvrir chaque sous-dossier
standalone dans l'IDE) :

```pwsh
$env:JAVA_HOME = "D:\dev-cache\jdk25\jdk-25.0.4+7"
cd minecraft\mod-hud       ; ./gradlew build      # mods Fabric
cd minecraft\plugin-guardian ; ./gradlew build    # plugins Paper
cd minecraft\shinobi       ; mvn clean package    # les 6 plugins Shinobi
```

## Conventions

- **Commits** — Conventional Commits (`feat(scope):`, `fix(scope):`, `chore(scope):`…)
  avec un scope explicite : `api`, `launcher`, `admin`, `bot`, `hud`, `integrity`,
  `ost`, `guardian`, `shinobi-core`, `shinobi-combat`, `manifest`, `tools`, `docs`.
- **Branches** — `main` (trunk et prod web), `feature/*`, `fix/*`.
  ⚠️ **Tout ce qui est publié doit revenir sur `main`** — cf.
  [`docs/AUDIT_COHERENCE.md §3`](./docs/AUDIT_COHERENCE.md).
- **Deux trains de release** : le *jeu* (mods + plugins, via le manifest signé et
  l'upload Minestrator) et le *web* (`api`/`admin`/`bot`, via Docker depuis `main`).
  Ne jamais les mélanger dans un même commit — cf.
  [`docs/PUBLISH_PREFLIGHT.md §2`](./docs/PUBLISH_PREFLIGHT.md).
- **TypeScript** — `strict: true`, ESLint + Prettier là où c'est configuré.
- **Rust** — `cargo fmt` + `cargo clippy -- -D warnings`.
- **Java** — toolchain 25.

## Index de la documentation

### État & pilotage
| Document | Contenu |
|---|---|
| [`docs/ETAT_DES_LIEUX.md`](./docs/ETAT_DES_LIEUX.md) | **Le présent** : versions, URLs, artefacts publiés, périmètre réel |
| [`docs/ROADMAP_BETA_2026.md`](./docs/ROADMAP_BETA_2026.md) | **Le futur** : 4 sprints, 16 chantiers, chiffres consolidés, cut list |
| [`docs/AUDIT_COHERENCE.md`](./docs/AUDIT_COHERENCE.md) | Passe de cohérence docs ⇄ Trello ⇄ Miro ⇄ code (2026-09-08) |
| [`PLAN_CONCEPTION_LAUNCHER.md`](./PLAN_CONCEPTION_LAUNCHER.md) | Conception d'origine : vision, architecture, sécurité, écrans |
| [`docs/adr/`](./docs/adr/) | Décisions structurantes, une par fichier |

### Opérations
| Document | Contenu |
|---|---|
| [`docs/MAINTENANCE.md`](./docs/MAINTENANCE.md) | **Premier réflexe** : mettre à jour un mod, un plugin, un user, une env var |
| [`docs/PUBLISH_PREFLIGHT.md`](./docs/PUBLISH_PREFLIGHT.md) | Checklist anti-régression avant tout build/publish game-side |
| [`docs/RELEASING.md`](./docs/RELEASING.md) | Workflow auto-update du launcher (Ed25519) |
| [`docs/AUTO_UPDATE_TEST_PROTOCOL.md`](./docs/AUTO_UPDATE_TEST_PROTOCOL.md) | Protocole de test reproductible de l'auto-update |
| [`docs/MULTI_POSTES.md`](./docs/MULTI_POSTES.md) | **Travailler depuis plusieurs machines** (2 PC Windows + Mac) : secrets à transférer, spécificités macOS, hygiène des branches |
| [`docs/DEPLOY.md`](./docs/DEPLOY.md) | Détails infra VPS (Docker + Caddy) — *setup from scratch* |
| [`docs/STAFF_BETA.md`](./docs/STAFF_BETA.md) | Checklist de déploiement complète — *setup from scratch* |

### Migrations
| Document | Contenu |
|---|---|
| [`docs/MIGRATION_26.2.md`](./docs/MIGRATION_26.2.md) | **Version courante** : 26.1 → 26.2, recette de build, pièges |
| [`docs/MIGRATION_26.1.md`](./docs/MIGRATION_26.1.md) | Archive : 1.21.1 → 26.1.2 (Java 25, fin de Yarn, rendu extraction) |

### Jeu & contenu
| Document | Contenu |
|---|---|
| [`docs/MOD_HUD_REDESIGN.md`](./docs/MOD_HUD_REDESIGN.md) | Refonte UI mod-hud : hub de config, éditeur HUD, chat, crosshair |
| [`docs/INVENTAIRE_ET_COSMETIQUES.md`](./docs/INVENTAIRE_ET_COSMETIQUES.md) | Sacoche RP : inventaire lié au perso, poids, cosmétiques 3D |
| [`docs/character-creator-assets.md`](./docs/character-creator-assets.md) | Pipeline skin data-driven (catalogue, masques RGBA, gating clan/genre) |
| [`docs/ANIMATIONS_BASE.md`](./docs/ANIMATIONS_BASE.md) | Catalogue d'animations P0/P1/P2 + stratégie réseau |
| [`docs/EMOTES.md`](./docs/EMOTES.md) | Emotes RP serveur-autoritaires (EmoteCraft) + distribution |
| [`docs/NEXO_STAFF_GUIDE.md`](./docs/NEXO_STAFF_GUIDE.md) | Guide staff : ajouter un modèle 3D / une texture animée depuis le panel |
| [`docs/ETUDE_NINSHU_ORIGINS.md`](./docs/ETUDE_NINSHU_ORIGINS.md) | Étude technique d'un mod tiers (référence mécaniques — usage privé) |

### Design
| Document | Contenu |
|---|---|
| [`docs/REBORN_ASEPRITE_PALETTE.md`](./docs/REBORN_ASEPRITE_PALETTE.md) | Palette Akatsuki, grilles, workflow d'intégration d'assets |
| [`docs/reborn-akatsuki.gpl`](./docs/reborn-akatsuki.gpl) | Palette importable Aseprite / GIMP |
| [`docs/CHANGELOG_DESIGN_V2.md`](./docs/CHANGELOG_DESIGN_V2.md) | Journal de la refonte v2 du launcher — *historique* |

### Outillage
| Document | Contenu |
|---|---|
| [`docs/MCP_OUTILLAGE.md`](./docs/MCP_OUTILLAGE.md) | Faisabilité MCP : Blockbench, Discord, Blender (pipeline emotes) |
| [`tools/blockbench-reborn-compositor/README.md`](./tools/blockbench-reborn-compositor/README.md) | Compositeur de skins RP |
| [`tools/blockbench-creator-tools/README.md`](./tools/blockbench-creator-tools/README.md) | Suite Creator Tools : Blockout Canvas, Handpainted Workflow, Motion Lab |
| [`tools/claude-hooks/README.md`](./tools/claude-hooks/README.md) | Notification Discord de fin de session Claude Code |

### Boards
- Trello (**public**, transparence communauté) : <https://trello.com/b/OqmICLMD/wip-fr-naruto>
- Miro (interne, roadmap visuelle) : <https://miro.com/app/board/uXjVGubPsdk=/>

---

> © 2026 Reborn Roleplay
