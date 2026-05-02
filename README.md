# Reborn Roleplay — Monorepo

Monorepo de l'écosystème **Reborn Roleplay** : launcher desktop, API backend, panel staff, bot Discord, plugin et mod Minecraft.

> Source de vérité produit & technique : [`PLAN_CONCEPTION_LAUNCHER.md`](./PLAN_CONCEPTION_LAUNCHER.md)

## Arborescence

```
reborn-roleplay/
├── apps/
│   ├── launcher/     ← Tauri 2 + React 18 + TS (desktop)
│   ├── api/          ← NestJS 11 + Prisma + Postgres + Redis
│   ├── admin/        ← Panel staff Next.js 15
│   └── bot/          ← Bot Discord (discord.js)
├── packages/
│   ├── shared-types/      ← types TS partagés (DTOs, manifest)
│   └── manifest-signer/   ← CLI Ed25519 pour signer les manifests
├── minecraft/
│   ├── plugin-guardian/   ← plugin Paper (Kotlin) — anti-cheat / integrity
│   ├── mod-integrity/     ← mod Fabric côté client
│   └── server-config/     ← templates server.properties
├── infra/
│   ├── docker-compose.yml ← Postgres + Redis (dev local)
│   └── nginx/
└── docs/
    ├── adr/               ← Architecture Decision Records
    └── security/
```

## Prérequis

| Outil    | Version min | Rôle                              |
|----------|-------------|-----------------------------------|
| Node.js  | 20+         | Frontend, API, panel, bot         |
| pnpm     | 10+         | Workspaces / install              |
| Rust     | 1.77+       | Backend Tauri (launcher)          |
| Docker   | 24+         | Postgres / Redis local            |
| Java     | 21          | Build du plugin / mod (plus tard) |

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

- **Commits** : Conventional Commits (`feat:`, `fix:`, `chore:`…)
- **Branches** : `main` (prod), `develop` (intégration), `feature/*`, `fix/*`
- **TypeScript** : ESLint + Prettier, `strict: true`
- **Rust** : `cargo fmt` + `cargo clippy -- -D warnings`

## Documents clés

- [Plan de conception](./PLAN_CONCEPTION_LAUNCHER.md) — vision produit, architecture, sécurité
- [ADR](./docs/adr/) — décisions techniques

---

> © 2026 Reborn Roleplay
