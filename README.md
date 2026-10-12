# Reborn Roleplay — Monorepo

Monorepo de l'écosystème **Reborn Roleplay** : launcher desktop, API backend, panel
staff, bot Discord, les mods clients Fabric, les plugins serveur Paper, et l'outillage
de production (Blockbench, publication, hooks).

> **Par où commencer :** toute la documentation part de [`docs/README.md`](./docs/README.md).
> - Reprendre le projet (humain ou Claude) → [`docs/REPRISE_DE_SESSION.md`](./docs/REPRISE_DE_SESSION.md)
> - Où en est chaque chantier → [`docs/CHANTIERS.md`](./docs/CHANTIERS.md)
> - Ce qui tourne aujourd'hui → [`docs/ETAT_DES_LIEUX.md`](./docs/ETAT_DES_LIEUX.md)
> - Ce qu'on livre d'ici décembre → [`docs/ROADMAP_BETA_2026.md`](./docs/ROADMAP_BETA_2026.md)
> - Publier le mod / un plugin → [`docs/operations/PUBLICATION.md`](./docs/operations/PUBLICATION.md)

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
│   ├── blockbench-handpaint/          plugin Blockbench — AO / Shade hand-paint
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
> Le JDK 25 est installé en portable dans `C:\Users\omarb\dev-cache\jdk-25.0.4.1+1` (poste principal).
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
$env:JAVA_HOME = "C:\Users\omarb\dev-cache\jdk-25.0.4.1+1"
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
  [`docs/operations/PUBLICATION.md`](./docs/operations/PUBLICATION.md) §0.
- **Deux trains de release** : le *jeu* (mods + plugins, via le manifest signé et
  l'upload Minestrator) et le *web* (`api`/`admin`/`bot`, via Docker depuis `main`).
  Ne jamais les mélanger dans un même commit — cf.
  [`docs/operations/PUBLICATION.md`](./docs/operations/PUBLICATION.md) §0.
- **TypeScript** — `strict: true`, ESLint + Prettier là où c'est configuré.
- **Rust** — `cargo fmt` + `cargo clippy -- -D warnings`.
- **Java** — toolchain 25.

## Documentation

Tout est indexé dans [`docs/README.md`](./docs/README.md) : pilotage (chantiers, état, roadmap, décisions,
livraisons, boards), fiches de gameplay (KO et mort RP, apprentissage, stats et progression, personnages…),
guide staff, références techniques (commandes, canaux, config, mod client), opérations (publication, VPS,
launcher), direction artistique et modèles 3D, archives.

Boards : Trello (**public**) <https://trello.com/b/OqmICLMD/wip-fr-naruto> · Miro (staff)
<https://miro.com/app/board/uXjVGubPsdk=/> — organisation dans [`docs/pilotage/BOARDS.md`](./docs/pilotage/BOARDS.md).

---

> © 2026 Reborn Roleplay
