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
| Sync des roles Discord avec le role Reborn (`PLAYER`/`WHITELISTED`/`HELPER`/...) | Abandonné (décision du 24/10 : aucun rôle Discord posé, le statut se voit dans le launcher) |
| Commandes staff `/whitelist hrp\|rp` (décision par partie), `/ticket` | Fait (voir ci-dessous) |
| Discord Rich Presence cote launcher (different du bot) | TODO §11 |

## Candidatures whitelist (candidature v2, lot H)

Référence : `docs/gameplay/CANDIDATURE_ET_DOUANE.md` §2.9 et §6.1.

- **Annonce** dans le salon staff : joueur, âge déclaré, personnage (type, sexe, âge), village, durée de
  la voix, bouton « Prendre en charge ». Le dossier complet part en MP (parties HRP et RP, textes longs
  découpés à 4 096 caractères par embed et 1 024 par champ). Les champs retirés de la v2 (date de
  naissance, « Pourquoi Reborn ? », expérience du RP, caractère et apparence) ne s'affichent que
  s'ils sont remplis (ancien dossier). Les réponses HRP v2 s'affichent dès qu'elles sont présentes,
  même dans un ancien dossier qui n'a renvoyé que sa partie HRP.
- **État des parties** : avec `hrpStatus` / `rpStatus` dans le webhook `/webhooks/whitelist`
  (facultatifs), l'annonce indique « À relire » et le MP grise la partie déjà validée. Après chaque
  décision, le bot garde l'état à jour pour la prise en charge suivante. Quand plus aucune partie
  n'attend de décision, « Libérer » disparaît du MP et une libération ne remet plus « Prendre en
  charge » sur l'annonce.
- **Décision par partie** : boutons `HRP · Accepter / À revoir / Refuser` et `RP · …` dans le MP, ou
  `/whitelist hrp|rp decision:<…> [commentaire] [candidature:<id>]`. Plus de décision globale. Le
  commentaire, visible du candidat, est obligatoire pour « À revoir » et « Refuser ». L'oral ne se
  décide pas ici (douane en jeu, ou carte « Oral » du panel).
- **Droits** : `/whitelist` est réservée par défaut aux membres qui peuvent déplacer des membres en
  vocal (réglable dans Paramètres du serveur › Intégrations). Le staff est transmis à l'API
  (`actorDiscordId`), qui contrôle son compte lié et son rôle, et trace la décision à l'audit.
- **Route API** : `PATCH /v1/staff/whitelist/:id/decision` (signée HMAC), corps
  `{part, status, reviewNotes?, actorDiscordId}`. L'API exige un compte Reborn lié à ce Discord, non
  banni, de rôle Relecteur whitelist ou plus (403 sinon), et trace la décision à son nom
  (`source: discord`). Face à une API plus ancienne qui n'a pas la route, le bot le dit au staff et
  renvoie au panel.
- **MP au candidat (douane)** : `POST /webhooks/dm` avec `context.kind: "douane"`, envoyé par l'API
  quand le candidat a un compte Discord lié : rappel 1 h avant la séance, séance annulée ou place
  retirée par le staff au panel, retrait par l'examinateur ou séance reportée en jeu. Titre repris
  du sujet, le texte reçu, ni bouton ni renvoi vers le panel. Le message système du fil (launcher)
  part toujours, MP ou pas.
- **Aucun rôle Discord** n'est posé à l'acceptation : le statut whitelist se voit dans le launcher.
- Après déploiement : `pnpm --filter @reborn/bot register` (nouvelles sous-commandes `hrp` et `rp`).

## Note sur l'architecture

Le bot et l'API NestJS sont deux process **independants**. Ils communiquent
uniquement via :

- des webhooks signes : l'API POST sur le bot quand un evenement Reborn doit
  generer une action Discord (ouverture de thread, post de message)
- l'API Discord : le bot lit les interactions slash, l'API ne parle pas
  directement a Discord pour les actions communes

Tant que l'API n'a pas l'endpoint webhook qui notifie le bot, le bot ne fait
que repondre a `/ping`. La sync se branche dans une etape ulterieure.
