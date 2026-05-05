# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

`reborn-roleplay` is a pnpm monorepo that hosts the entire ecosystem of a Minecraft RP server — desktop launcher, web API, Discord bot, future staff panel, plus a Paper plugin and Fabric mod for the game side. The single source of truth for product + technical decisions is **`PLAN_CONCEPTION_LAUNCHER.md`** at the repo root (~2500 lines, French). When in doubt about a feature's intent, scope, or sequencing, read the relevant section of the plan before guessing.

ADRs live in `docs/adr/` and capture the hard, non-obvious calls — start with `0001-microsoft-app-approval-required.md` because the Microsoft auth path is gated on it.

## Workspace layout

- `apps/launcher` — Tauri 2 + React 19 + TS desktop client
- `apps/api` — NestJS 11 + Prisma + Postgres
- `apps/admin` — Next.js panel (placeholder)
- `apps/bot` — discord.js v14 bot (ESM strict). Serves slash commands AND an HTTP server on `:3001` for inbound webhooks from the API
- `packages/manifest-signer` — TypeScript CLI that signs the launch manifest with Ed25519
- `packages/shared-types` — placeholder for shared DTOs
- `minecraft/plugin-guardian` — Paper plugin (Java 21 + Gradle Kotlin DSL). Real scaffold; isolated from the pnpm monorepo (open it standalone in IntelliJ)
- `minecraft/{mod-integrity,server-config}` — placeholders
- `infra/docker-compose.yml` — Postgres 16 + Redis 7 for local dev

## Prerequisites

- Node.js 20+, pnpm 10+ (the root `package.json` pins `pnpm@10.33.2` via `packageManager`)
- Rust stable + **Visual Studio 2022 Build Tools (C++)** on Windows for the Tauri backend
- Docker for Postgres/Redis
- Java 21 for the Paper plugin (the launcher downloads the JRE itself for Minecraft via piston-meta)

## Common commands

Workspace shortcuts (run from the repo root):

```pwsh
pnpm install                  # install all workspace deps
pnpm infra:up                 # docker compose up -d (postgres + redis)
pnpm infra:down               # docker compose down
pnpm infra:logs               # tail postgres/redis logs
pnpm api:dev                  # nest start --watch on :3000 (prefix /v1)
pnpm api:build
pnpm bot:dev                  # tsx watch — bot online + webhook server on :3001
pnpm bot:register             # deploy slash commands to the guild (instant)
pnpm launcher:dev             # tauri dev — opens the desktop window
pnpm launcher:build           # tauri build — produces the NSIS installer
```

API-specific (run inside `apps/api`):

```pwsh
pnpm exec prisma migrate dev --name <slug>     # create + apply migration
pnpm exec prisma generate                       # after schema edits
pnpm exec ts-node prisma/seed.ts                # idempotent seed (patch notes, rules, lore, dev manifest)
pnpm exec nest build                            # type-check + emit dist/
pnpm test -- path/to/file.spec.ts               # run a single test
```

Manifest signer (run inside `packages/manifest-signer`):

```pwsh
pnpm exec tsx src/cli.ts gen-keys --out-dir ../../secrets
pnpm exec tsx src/cli.ts sign <input.json> --key <private.pem> --out <signed.json>
pnpm exec tsx src/cli.ts verify <signed.json> --pub <public.pem>
```

Launcher Rust (run inside `apps/launcher/src-tauri`):

```pwsh
cargo check --message-format=short      # fast type-check
cargo test --lib                         # unit tests in jvm.rs, manifest/verify.rs, mods.rs, diagnostics.rs, etc.
```

Paper plugin (open `minecraft/plugin-guardian/` standalone in IntelliJ — **not** the monorepo root):

```pwsh
./gradlew build       # → build/libs/reborn-guardian-<ver>.jar
./gradlew runServer   # spins up Paper 1.21.1 with the plugin loaded
```

## Architecture — the parts that need cross-file context

### Three-layer launcher with the frontend behind an IPC boundary

1. **React frontend** in `apps/launcher/src/` — UI only. **Never speaks HTTP directly** (`PLAN §3.1`). Every external call goes through `lib/tauri.ts::invoke()` to a typed wrapper in `lib/auth.ts`, `lib/launcher.ts`, `lib/content.ts`, or `lib/prefs.ts`.
2. **Rust backend** in `apps/launcher/src-tauri/src/` — exposes `#[tauri::command]` fns that are the only API surface the WebView sees. Holds the JWT and refresh tokens in the OS keyring, downloads files, spawns the JVM, runs the FS watcher.
3. **NestJS API** in `apps/api/src/` — JWT-protected REST endpoints prefixed with `/v1`. Authoritative for users, sessions, manifest, whitelist, tickets, lore, etc.

When adding a feature that needs API data: add the NestJS endpoint, then a method on `ApiClient` in `src-tauri/src/api/mod.rs` (use the generic `get_json`/`post_json`/`patch_json`/`delete_no_content` helpers — they already attach the bearer), then a `#[tauri::command]` in `src-tauri/src/content/mod.rs` (or a new module), then register it in `src-tauri/src/lib.rs::run()`'s `generate_handler!` array, then a typed wrapper in the frontend's `src/lib/`. Don't shortcut the frontend HTTP — it breaks the security model.

### `generate_handler!` requires fully-qualified paths for re-exports

Tauri's macro looks up companion symbols (`__cmd__<name>`, `__tauri_command_name_<name>`) at the exact path you write. **Re-exports break it.** When `launcher::game::launcher_launch_game` is re-exported as `launcher::launcher_launch_game`, the macro can't find the companions through the alias. Always reference commands by their defining-module path in `generate_handler!`.

### API ↔ Bot bidirectional bridge via shared HMAC

The API and the Discord bot run as **two independent processes** that talk over HTTP, both ways. Six bridge directions in total, all signed with HMAC-SHA256 using the same `REBORN_WEBHOOK_SECRET`:

**Lifecycle (creation + decisions):**
- **API → Bot** (notifications): `WebhooksService` (apps/api/src/webhooks/) POSTs to the bot's `:3001/webhooks/{whitelist,tickets}` after `WhitelistService.submit` or `TicketsService.create`. The bot creates a public Discord thread in `DISCORD_TICKETS_CHANNEL_ID` with an embed whose footer carries the entity ID (`application <uuid>` or `ticket <uuid>`), and **returns the threadId in the response body**. The API persists `discordThreadId` on the entity for later message relay.
- **Bot → API** (staff slash commands): `/whitelist accept|reject|revise` and `/ticket progress|resolve|close` extract the entity ID by reading the bot's own original embed footer in the current thread (`apps/bot/src/thread-context.ts::extractIdFromThread` — filter by footer pattern, **not** by message order, because subsequent status embeds without footer would mask the original), then PATCH `/v1/staff/{whitelist,tickets}/:id`.

**Chat (per-message relay):**
- **API → Bot** (user replies in launcher): when a user posts via `POST /v1/{whitelist/me,tickets/:id}/messages`, the API persists the message *and* POSTs `/webhooks/{whitelist,tickets}-message` so the bot replies in the existing thread with a compact embed. The footer `from-launcher <pseudo>` is a marker for the bot's own listener to skip its own posts (loop prevention belt-and-suspenders; the `client.user.id` check is the real guard).
- **Bot → API** (staff replies in Discord thread): `apps/bot/src/thread-listener.ts` listens to `Events.MessageCreate`, filters by `parentId === DISCORD_TICKETS_CHANNEL_ID`, ignores its own posts, identifies whether the thread is whitelist or ticket via the same footer-extraction trick (`extractIdFromMessage`), then POSTs HMAC-signed `/v1/staff/{whitelist,tickets}/:id/messages` with `{discordMessageId, authorDiscordId, authorName, content, attachmentUrls}`.

Idempotence on the chat path is `discordMessageId`-based: both `WhitelistMessagesService.postStaffMessage` and `TicketsService.postStaffMessage` upsert by that key so retries / re-emits don't create dupes. The launcher polls the user-side endpoints every 5s (`StatusChatPage` for whitelist, `Tickets.tsx` for tickets) — no SSE yet.

The API verifies inbound HMAC signatures via `apps/api/src/staff/hmac-signature.guard.ts`; this requires the raw request body, captured in `main.ts` via the `verify` hook on `express.json()` and stashed on `req.rawBody`. Don't re-serialize the parsed JSON — bytes won't match. The whole staff path bypasses JWT and trusts the bot as infrastructure; that simplification will be replaced when real human staff users with role-based permissions land.

### Six-step launch flow plus a pre-step

`apps/launcher/src-tauri/src/launcher/game.rs` orchestrates:

0. **Mods cleanup** (`launcher/mods.rs::purge_incompatible_mods`) — reads `fabric.mod.json` from each jar in `mods/`, parses `depends.minecraft`, deletes any whose constraint doesn't accept the active MC version. Constraint parser handles exact, wildcard (`1.21.x`), `>=`/`<=`, `~`, `^`, `[a,b)` ranges; conservative on unknowns (= keep). Emits `mods:purged` event.
1. `runtime::ensure_runtime` — JRE 21 from `piston-meta.mojang.com` (Mojang rotates the JRE manifest URL hash; refresh from skyrising's gist linked in `runtime.rs` when it 404s).
2. `mojang::fetch_version_json` — Minecraft metadata for the version returned by `minecraft_version()` (env `REBORN_MC_VERSION`, default `1.21.1`).
3. `libraries::ensure_libraries` — client jar + libs filtered by OS rules + native extraction. Modern Mojang JSONs (1.19+) put each native variant as a **separate library entry** with the classifier in the `name` (4 colon-separated parts). `libraries.rs` routes those to `natives_jars` for unzip; only the main artifact lands on the classpath.
4. `assets::ensure_assets` — downloads the asset index + each hash-addressed object (~5000 files, **semaphore=8**, kept low to avoid Windows TLS saturation + AV scanning timeouts).
5. `fabric::ensure_fabric` — picks the latest stable Fabric Loader from `meta.fabricmc.net` and downloads its libs.
6. `jvm::build_command` builds the argv, then we override the vanilla main class with Fabric's. The classpath is **deduplicated by `group:artifact:classifier`** (see `dedupe_classpath`); without this, Fabric refuses to start with "duplicate ASM classes". Auto-connect is passed via `--quickPlayMultiplayer host:port` (the canonical MC 1.20+ form; legacy `--server`/`--port` are deprecated and silently ignored on 1.21+).

`mojang::download_with_sha1` retries transient failures (timeouts, connect errors, body interrupts, 5xx) up to 4 times with exponential backoff (0.5s/1s/2s/4s). Hash mismatches are **not** retried (they signal real corruption). The `auth::AuthState::http` reqwest client uses a 60s timeout to absorb slow batches on residential networks.

### Real-time stdout/stderr diagnostics layer

`launcher/diagnostics.rs::LogAnalyzer` parses each line of the JVM's output and emits typed diagnostics on patterns like `MOD_MC_VERSION_MISMATCH`, `FABRIC_MOD_RESOLUTION_FAILED`, `JVM_OUT_OF_MEMORY`, `MC_INVALID_UUID`, `SERVER_UNREACHABLE`, etc. Each diagnostic has a stable `code`, severity, FR-locale message, action hint, and a raw log excerpt; emitted as Tauri event `game:diagnostic` and rendered by `components/DiagnosticToast.tsx` (mounted globally in `AuthenticatedLayout`).

`pump_stdio` in `game.rs` tees stderr to both `last-stderr.txt` (for forensics) and the analyzer in real time. Both stdout and stderr feed the same shared analyzer instance because Fabric Loader spreads warnings across both streams.

When adding a new pattern: extend `LogAnalyzer::ingest`, add a unit test, and update the `GameDiagnostic.code` discriminated union in `apps/launcher/src/lib/launcher.ts` so the toast renders properly.

### Manifest signing requires byte-identical canonical form on both sides

The Reborn launch manifest is signed with Ed25519 by `packages/manifest-signer` and verified by `apps/launcher/src-tauri/src/manifest/verify.rs`. **Both sides must produce identical canonical bytes** or verification fails silently:

- **Sorted keys at every depth.** `serde_json::Value` uses `BTreeMap` so Rust naturally produces alphabetic order at all levels. The JS side has to match — see `canonicalStringify` in `packages/manifest-signer/src/manifest.ts`.
- **Timestamp round-tripping.** A signed `"2026-05-10T09:00:00Z"` becomes `"2026-05-10T09:00:00.000Z"` after Postgres → JS Date → `.toISOString()`. The signer normalises every timestamp through `new Date(...).toISOString()` so signing matches the form Postgres will round-trip back. The API stores the **exact signed values** in `Manifest.issuedAt` / `Manifest.expiresAt` — never derive them from `publishedAt`.

The dev public key is loaded from the `MANIFEST_PUBLIC_KEY_HEX` env var in debug builds; in release it falls back to the constant in `manifest/mod.rs::PUBLIC_KEY_HEX`. Rotate that constant before cutting a release.

### Auth has two paths, both populating the same keyring

- **Microsoft OAuth** (`auth/microsoft.rs` → `xbox.rs` → `minecraft.rs`) — the real one. **Currently blocked at `login_with_xbox` (403 "Invalid app registration")** until Microsoft approves our App ID via <https://aka.ms/mce-reviewappid>; see ADR 0001.
- **Discord OAuth** (`apps/api/src/discord/`) — links a Discord account to a Reborn user. The launcher calls `GET /v1/auth/discord/start` (returns a state-prefixed URL), opens it via `tauri-plugin-opener`, and polls `auth_me` until `discordUserId` populates server-side. The state-to-userId mapping lives in-RAM with a 5-min TTL — fine for a single API instance, will move to Redis when scaled.
- **`/v1/auth/dev-login` + `auth_dev_login` Tauri command** — debug-only bypass. Gated by `process.env.NODE_ENV !== 'production'` server-side and `cfg!(debug_assertions)` client-side. The fake UUID is computed via **Mojang offline-mode format**: `MD5("OfflinePlayer:" + pseudo)` with bits version=3 / variant=IETF, so MC 1.21+'s `UndashedUuid.fromStringLenient` accepts it (the previous `dev00000-...` form failed with NumberFormatException because non-hex chars). Lookup is by `msAccountId="dev:<pseudo>"` so an existing dev user gets their UUID migrated silently.

`RebornAccessToken` and `RebornRefreshToken` persist to the OS keyring (Windows Credential Manager) via `storage/secrets.rs`. Anything downstream that needs auth (manifest fetch, launch, content commands) reads from the keyring, not from React state.

### Error types crossing the IPC boundary must be struct-shaped

`AuthError`, `LauncherError`, `GameError`, `ContentError` all use `#[serde(tag = "kind", rename_all = "snake_case")]`. Serde **cannot** serialise a tagged newtype variant carrying a single value — `Foo(String)` blows up at runtime with "cannot serialize tagged newtype variant". Always write `Foo { message: String }` for variants that carry data; unit variants like `NotAuthenticated` are fine.

## Dev environment quirks

- The Rust process spawned by `tauri dev` does **not** read the workspace `.env` automatically. `lib.rs` calls `dotenvy::from_path_override` walking up from `CARGO_MANIFEST_DIR` to find it, and prints `[dev] .env loaded from ...` at startup including `MS_CLIENT_ID`, `MANIFEST_PUBLIC_KEY_HEX`, and `REBORN_SERVER`. If the launcher behaves as if env is unset, that line is the first thing to check.
- `WebhooksService` (apps/api) logs `Webhooks ACTIFS — POST <url>/webhooks/* (HMAC OK)` or `Webhooks DESACTIVES — ...` at boot. If the bot doesn't react to whitelist/ticket creation, that log tells you whether the API even tried.
- **`nest start --watch` does not reload on `.env` changes**, only on TS source changes. After editing `.env`, restart `pnpm api:dev` manually.
- The bot's `tsx watch` does reload on `.env` changes because it re-imports the config module on file events.
- The FS watcher (`integrity/watcher.rs`) currently emits false positives on `config/` because Sodium and Fabric write their own config there at startup. The watcher is wired but the kill-on-tamper response is intentionally not connected yet.
- `REBORN_DEV_LINGER_SECS=N` in `.env` makes `launcher_launch_game` spawn `powershell Start-Sleep` instead of the real game, useful for testing the FS watcher without launching Minecraft.
- A failed Java spawn writes `%APPDATA%\RebornRoleplay\logs\last-stderr.txt` (now teed in real time, not just at exit) and the full argv to `last-argv.txt`.
- **Paper plugin's `runDirectory` is set to `~/.reborn-guardian-run/`**, deliberately outside the project tree. Desktop folders are often OneDrive-synced on Windows; if you let `run/` live inside the project, OneDrive locks `world/session.lock` at boot and Paper crashes with *"Le processus ne peut pas accéder au fichier car un autre processus en a verrouillé une partie"*.

## Environment variables

Beyond Postgres/Redis/JWT/MS OAuth (already documented in `.env.example` patterns), several pieces of the system depend on these:

- `REBORN_MC_VERSION` — Minecraft version the launcher targets (default `1.21.1`). Bump in lockstep with the dev server.
- `REBORN_SERVER_HOST` / `REBORN_SERVER_PORT` — auto-connect target. Empty → game launches to the main menu.
- `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` / `DISCORD_BOT_TOKEN` / `DISCORD_GUILD_ID` / `DISCORD_TICKETS_CHANNEL_ID` / `DISCORD_REDIRECT_URI` — all required for OAuth + bot to function.
- `REBORN_WEBHOOK_SECRET` — shared HMAC secret. Same value must be present in the API and bot processes; both read the root `.env`.
- `REBORN_BOT_WEBHOOK_URL` (default `http://localhost:3001`) — where the API POSTs notifications.
- `BOT_HTTP_PORT` (default `3001`) — port the bot exposes for webhooks.
- `REBORN_API_URL` (default `http://localhost:3000/v1`) — used by the bot for outbound calls to `/v1/staff/*`.
- `DISCORD_RICH_PRESENCE_CLIENT_ID` — Discord application ID for Rich Presence shown while the user is in-game. Create the app at <https://discord.com/developers/applications>, upload "logo" + "play" images under Rich Presence → Art Assets, paste the Application ID here. **Optional** — if unset, the launcher silently skips RPC. The launcher publishes the activity from `discord_rpc.rs::start_in_game` after `game:started` and clears it after `game:exited`. The IPC is local (Unix socket / Windows named pipe), no network.

## Conventions

- **Commits** — Conventional Commits (`feat(scope):`, `fix(scope):`, `chore(scope):`...) with multi-paragraph bodies that explain *why* and reference the PLAN section being implemented.
- **Branches** — `main` (prod), `develop` (intégration), `feature/*`, `fix/*`. Push directly to `main` is the current workflow until we set up review.
- **Rust** — `cargo fmt` + `cargo clippy -- -D warnings`. Dead-code warnings on yet-unconsumed fields are accepted during scaffolding; clean them up when the consumer lands.
- **TypeScript** — `strict: true`, ESLint + Prettier where configured (NestJS app has it; the launcher app and bot do not yet).
