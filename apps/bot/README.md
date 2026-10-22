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

Optionnelles, pour les messages rediges dans le panel (annonces / dev / build updates du
Discord staff, routes `POST /webhooks/staff-message[/edit|/delete]`, rendu partage
avec `scripts/post-message.ts` dans `src/messages/cadre.ts`) :

```
DISCORD_STAFF_GUILD_ID
DISCORD_STAFF_ANNONCES_CHANNEL_ID
DISCORD_STAFF_DEVUPDATES_CHANNEL_ID
DISCORD_STAFF_BUILDUPDATES_CHANNEL_ID
DISCORD_STAFF_APERCU_CHANNEL_ID   # salon de test des apercus
DISCORD_STAFF_TESTEUR_ROLE_ID     # sinon role nomme « Testeur »
```

Details : `docs/operations/DISCORD_STAFF.md` §11.

Optionnelles, pour le **Discord joueurs** (« façon Ketto », `docs/operations/DISCORD_JOUEURS.md` §5) —
vides = desactive, le Discord staff n'est jamais concerne :

```
DISCORD_JOUEURS_GUILD_ID            # serveur joueurs
DISCORD_BIENVENUE_CHANNEL_ID        # ⛩・bienvenue : mention + banniere + « N membres » a chaque arrivee
DISCORD_BIENVENUE_EMOJI             # optionnel : <:reborn:ID> dans le pied
DISCORD_BIENVENUE_COULEUR           # optionnel : barre du cadre (défaut #6A2177, violet de l avatar du bot)
DISCORD_BIENVENUE_SEUIL_GROUPE      # defaut 8 arrivees/min, au-dela un message groupe par minute sans ping
DISCORD_BIENVENUE_SEUIL_RAID        # defaut 25/min, au-dela accueil suspendu 10 min + alerte
DISCORD_JOUEURS_LOG_CHANNEL_ID      # optionnel : journal staff (arrivees massives, piege)
DISCORD_JOUEURS_STAFF_ROLE_ID       # optionnel : role jamais sanctionne par le piege
DISCORD_PIEGE_CHANNEL_ID            # salon piege ⚠️・ne-pas-ecrire : vide = desactive (defaut)
DISCORD_PIEGE_JOURS                 # defaut 7 (0 = definitif), leve automatiquement
DISCORD_PIEGE_PURGE_SECONDES        # defaut 3600 : messages effaces partout au bannissement
DISCORD_PIEGE_SIMULATION            # 1 = efface + journal, personne n'est banni
```

Code : `src/joueurs/` (`bienvenue.ts`, `piege.ts`, `journal.ts`), banniere `assets/bienvenue.png` (copiee
dans l'image Docker). Intent requis : **Server Members** (deja demande par `index.ts`, a cocher dans le
portail developpeur). Le piege a besoin de la permission **Bannir des membres**.

Scripts de mise en place (hors ligne par defaut, jamais sur le serveur perso) : `post:message` accepte
`"banniere": "img/x.png"` (image au-dessus du cadre, facon Ketto) ; les plans `plan:guild` / `apply:guild`
acceptent `topChannels` (salons hors categorie, en tete).

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
