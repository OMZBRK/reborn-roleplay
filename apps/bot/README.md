# @reborn/bot

Bot Discord Reborn Roleplay (cf `PLAN_CONCEPTION_LAUNCHER.md` §11 / §10.12).

## Stack

- **discord.js** v14 (Node 20+, ESM)
- **TypeScript** strict
- **tsx** pour le dev (`watch`) et les scripts ponctuels

## Dev workflow

Variables d'env requises (lues depuis le `.env` racine du monorepo) :

```
DISCORD_BOT_TOKEN
DISCORD_CLIENT_ID
DISCORD_GUILD_ID
DISCORD_TICKETS_CHANNEL_ID
```

Etapes :

```pwsh
# 1. Installe les deps (depuis la racine du monorepo).
pnpm install

# 2. Enregistre les slash commands sur le guild Reborn (instantane).
pnpm --filter @reborn/bot register

# 3. Demarre le bot en mode watch.
pnpm --filter @reborn/bot start:dev
```

Quand tu te connectes a Discord, tape `/ping` dans le serveur Reborn — tu
dois voir le bot repondre avec sa latence.

## Roadmap

| Etape | Etat |
|---|---|
| Scaffold + commande `/ping` | Fait |
| Webhook depuis l'API : poster un thread quand une candidature whitelist est creee | TODO §10.12 |
| Webhook depuis l'API : poster un thread quand un ticket est ouvert | TODO §10.12 |
| Sync des roles Discord avec le role Reborn (`PLAYER`/`WHITELISTED`/`HELPER`/...) | TODO §10.12 |
| Commandes staff `/whitelist accept`, `/whitelist reject`, `/ticket close` | TODO |
| Discord Rich Presence cote launcher (different du bot) | TODO §11 |

## Note sur l'architecture

Le bot et l'API NestJS sont deux process **independants**. Ils communiquent
uniquement via :

- des webhooks signes : l'API POST sur le bot quand un evenement Reborn doit
  generer une action Discord (ouverture de thread, post de message)
- l'API Discord : le bot lit les interactions slash, l'API ne parle pas
  directement a Discord pour les actions communes

Tant que l'API n'a pas l'endpoint webhook qui notifie le bot, le bot ne fait
que repondre a `/ping`. La sync se branche dans une etape ulterieure.
