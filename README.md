# Reborn Roleplay — Monorepo

Monorepo de l'écosystème **Reborn Roleplay** : launcher desktop, API backend, panel staff, bot Discord, et les mods/plugins Minecraft (client Fabric + serveur Paper).

> Source de vérité produit & technique : [`PLAN_CONCEPTION_LAUNCHER.md`](./PLAN_CONCEPTION_LAUNCHER.md)

## Arborescence

```
reborn-roleplay/
├── apps/
│   ├── launcher/     ← Tauri 2 + React 19 + TS (desktop)
│   ├── api/          ← NestJS 11 + Prisma + Postgres + Redis
│   ├── admin/        ← Panel staff Next.js 15
│   └── bot/          ← Bot Discord (discord.js v14, ESM)
├── packages/
│   ├── shared-types/      ← types TS partagés (DTOs, manifest)
│   └── manifest-signer/   ← CLI Ed25519 pour signer les manifests
├── minecraft/
│   ├── plugin-guardian/   ← plugin Paper (Java 21) — vérifie le
│   │                         play-token au JOIN
│   ├── plugin-ost/        ← plugin Paper (Java 21) — broadcast OST
│   │                         (commandes /ost + zone registry)
│   ├── mod-integrity/     ← mod Fabric client — attestation
│   │                         play-token au JOIN (uniquement)
│   ├── mod-ost/           ← mod Fabric client — décodage et lecture
│   │                         audio Ogg Vorbis
│   ├── mod-hud/           ← mod Fabric client — toute la couche UI
│   │                         (menu principal, ESC, ConnectScreen,
│   │                         sub-screens, HUD in-game, chat custom)
│   └── server-config/     ← templates server.properties
├── infra/
│   ├── docker-compose.yml ← Postgres + Redis (dev local)
│   └── nginx/             ← (legacy — prod tourne sous Caddy)
└── docs/
    ├── adr/               ← Architecture Decision Records
    └── security/
```

> Voir [`PLAN_CONCEPTION_LAUNCHER.md §9`](./PLAN_CONCEPTION_LAUNCHER.md) pour la spec détaillée de chaque mod/plugin et la séparation des responsabilités.

## Prérequis

| Outil    | Version min | Rôle                              |
|----------|-------------|-----------------------------------|
| Node.js  | 20+         | Frontend, API, panel, bot         |
| pnpm     | 10+         | Workspaces / install              |
| Rust     | 1.77+       | Backend Tauri (launcher)          |
| Docker   | 24+         | Postgres / Redis local            |
| Java     | 21          | Build des mods/plugins Minecraft  |

> Sur Windows, installer Rust via [`rustup`](https://rustup.rs/) (toolchain `stable-x86_64-pc-windows-msvc`) et les **Build Tools Visual Studio 2022** (composants C++).

## Démarrage rapide

```pwsh
# 1. Installer toutes les dépendances JS du workspace
pnpm install

# 2. Lancer Postgres + Redis en local
pnpm infra:up

# 3. Migrer la base
pnpm --filter @reborn/api prisma migrate dev

# 4. Lancer l'API
pnpm api:dev

# 5. Dans un autre terminal — lancer le launcher
pnpm launcher:dev
```

## Conventions

- **Commits** : Conventional Commits (`feat(scope):`, `fix(scope):`, `chore(scope):`…) avec scope explicite (`api`, `launcher`, `bot`, `admin`, `integrity-mod`, `ost-mod`, `ost-plugin`, `hud`, `guardian`, etc.)
- **Branches** : `main` (prod), `develop` (intégration), `feature/*`, `fix/*`
- **TypeScript** : ESLint + Prettier, `strict: true`
- **Rust** : `cargo fmt` + `cargo clippy -- -D warnings`
- **Java** : JDK 21 toolchain, projets Minecraft isolés du monorepo pnpm (ouvrir chaque sous-dossier `minecraft/<projet>` standalone dans IntelliJ)

## Documents clés

- [Plan de conception](./PLAN_CONCEPTION_LAUNCHER.md) — vision produit, architecture, sécurité (~2500 lignes, source de vérité)
- [CLAUDE.md](./CLAUDE.md) — guide architectural pour Claude Code (pièges cross-fichiers, conventions)
- **Opérations courantes :**
  - [docs/MAINTENANCE.md](./docs/MAINTENANCE.md) — **comment faire les MAJ** (mods, users, env, redéploiement) — premier réflexe
  - [docs/RELEASING.md](./docs/RELEASING.md) — workflow auto-update du launcher
- **Setup from scratch (rare) :**
  - [docs/STAFF_BETA.md](./docs/STAFF_BETA.md) — checklist déploiement complète
  - [docs/DEPLOY.md](./docs/DEPLOY.md) — détails infra VPS
- **Côté Minecraft :** chaque sous-dossier `minecraft/<projet>/README.md` documente le périmètre du mod/plugin.
- [docs/adr/](./docs/adr/) — décisions techniques

---

> © 2026 Reborn Roleplay
