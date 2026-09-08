# État des lieux — Reborn Roleplay

> **Ce document décrit le PRÉSENT.** Ce qui tourne, dans quelle version, à quelle
> adresse. Il est la source de vérité quand une autre doc et le code se contredisent.
>
> Le futur est dans [`ROADMAP_BETA_2026.md`](./ROADMAP_BETA_2026.md).
> L'intention d'origine est dans [`../PLAN_CONCEPTION_LAUNCHER.md`](../PLAN_CONCEPTION_LAUNCHER.md).
>
> **Dernière vérification : 2026-09-08** (API interrogée en direct, dépôt et boards relus).
> À remettre à jour à chaque fin de sprint.

---

## 1. Plateforme cible

| Élément | Version | Où c'est figé |
|---|---|---|
| **Minecraft** | **26.2** | `launcher/src-tauri/src/launcher/game.rs:35` (défaut), env `REBORN_MC_VERSION` |
| **Java** | **25** | toolchains mods + plugins + serveur. JDK portable : `D:\dev-cache\jdk25\jdk-25.0.4+7` |
| **Fabric Loader** | 0.19.3 | `minecraft/*/gradle.properties` |
| **Fabric API** | 0.156.0+26.2 | idem |
| **Mappings** | **aucune** — le client Mojang 26.x est livré déobfusqué | pas de ligne `mappings(...)` dans les `build.gradle` |
| **Serveur** | Paper / Purpur 26.x | Minestrator (prod), VPS dev |

> ⚠️ Depuis 26.x, **Yarn n'existe plus** et `DrawContext`/`GuiGraphics` a été supprimé
> au profit du rendu « extraction / retained » (`GuiGraphicsExtractor`). Toute recette
> de build antérieure à août 2026 est caduque — voir [`MIGRATION_26.2.md`](./MIGRATION_26.2.md).

---

## 2. Artefacts — publié vs. dépôt

| Artefact | **Publié / live** | Sur `origin/main` | Écart |
|---|---|---|---|
| Launcher | **0.3.42** (`/v1/launcher/update` le confirme, release `v0.3.42` du 02/09) | 0.3.40 | ⚠️ 2 commits sur `feature/launcher` seulement |
| `reborn-hud` | **0.4.134** (release `mods-v3.1.90`, 04/09) | 0.4.132 | ⚠️ sur aucune branche distante |
| `reborn-integrity` | 0.3.1 | 0.3.1 | ✅ |
| `reborn-ost` | 0.2.2 | 0.2.2 | ✅ |
| Plugins Shinobi | build manuel Maven | `minecraft/shinobi/` | déploiement SFTP manuel |
| `reborn-guardian`, `reborn-ost-plugin` | build Gradle | `minecraft/plugin-*` | déploiement SFTP manuel |

> 🔴 **Dette à résorber en priorité** : `main` ne reproduit pas la production.
> Merger `feature/launcher` et commiter `reborn-hud 0.4.134`. Cf.
> [`AUDIT_COHERENCE.md` §3](./AUDIT_COHERENCE.md).

---

## 3. Infrastructure

| Service | Adresse | Hébergeur |
|---|---|---|
| API | `https://api.reborn-rp.com/v1` (`/health` → `ok`) | VPS OVH `ubuntu@91.134.136.120` (Gravelines), Docker + Caddy |
| Panel staff | `https://panel.reborn-rp.com` | même VPS |
| Serveur MC **prod** | `play.reborn-rp.com:27106` (`91.197.6.152:27106`) | Minestrator |
| Serveur MC **dev** | `91.197.6.60:25606` | Minestrator |
| Serveur MC **build** | instance dédiée (manifest builder séparé, cf §4) | Minestrator |
| Postgres + Redis | internes au VPS, jamais exposés | Docker |
| Bot Discord | conteneur `bot`, webhooks sur `:3001` | même VPS |
| Repo | `github.com/OMZBRK/reborn-roleplay` (**public**) | GitHub |

Secrets de prod : `/opt/reborn/.env.prod` (VPS) + `secrets/` (machine locale, jamais
commité). Liste des secrets critiques : `MAINTENANCE.md` §12.

**Déploiement des plugins Paper : manuel**, via le panel web Minestrator (pas de SFTP
automatisé, pas de hot-reload — un plugin Paper exige un restart serveur).

---

## 4. Modpack — deux manifests distincts

| Manifest | Pour qui | Contenu |
|---|---|---|
| **Manifest joueur** (`/v1/manifest/current`) | tous les joueurs | ~19 mods + les pistes OST (`reborn/ost/<catégorie>/*.ogg`) |
| **Manifest builder** (`secrets/builder-manifest-signed.json`) | staff builders (« Serveur Build ») | Axiom, FAWE-friendly, Distant Horizons, Sodium/Iris/Lithium — **pas** les mods RP |

Modpack joueur (26.2), tel que signé :

- **Requis** : `fabric-api`, `fabric-language-kotlin`, `sodium`, `sodium-extra`,
  `lithium`, `entityculling`, `yet_another_config_lib_v3`, `entity_texture_features`,
  `firstperson`, `plasmovoice`, **`emotecraft` 3.4.0**, **`PlayerAnimationLibMerged`
  1.2.6**, `reborn-hud`, `reborn-integrity`, `reborn-ost`.
- **Optionnels** : `iris`, `zoomify`, `NoChatReports`, `entity_model_features`,
  `DistantHorizons`.
- **OST** : les pistes `.ogg` sont livrées **par le manifest**, pas embarquées dans le
  jar (`reborn-hud` ≈ 20 Mo ; si le jar gonfle, un asset s'est glissé dedans — cf.
  `PUBLISH_PREFLIGHT.md` §1).

> ℹ️ Le fichier local `secrets/manifest-signed.json` **peut être périmé** par rapport
> au manifest live (il l'était au moment de cet audit : v3.0.1 / `reborn-hud 0.4.48`).
> Toujours partir du manifest live ou du dernier `manifest-signed-v*.json`, jamais du
> fichier local supposé à jour.

### Le « retrait de l'OST » du sprint de consolidation

Il s'agit d'un retrait des **assets audio embarqués dans `reborn-hud`** et d'une mise
en veille des features backpack/cosmétiques côté client — **pas** d'une suppression de
`mod-ost` / `plugin-ost`, qui restent valides et livrés par le manifest.

---

## 5. Périmètre réel du monorepo

```
reborn-roleplay/
├── apps/
│   ├── launcher/   Tauri 2 + React 19 + TS — client desktop
│   ├── api/        NestJS 11 + Prisma + Postgres + Redis (préfixe /v1)
│   ├── admin/      Next.js — panel staff (dashboard, whitelist, tickets, players,
│   │               audit, anomalies, inbox, oral-slots, wiki, wiki/ideas, files, 2FA)
│   └── bot/        discord.js v14 (ESM) — slash commands + serveur HTTP :3001
├── packages/
│   ├── manifest-signer/    CLI TS — signature Ed25519 du manifest
│   ├── manifest-uploader/  CLI Rust — lit le JWT du Credential Manager, POST le manifest
│   └── shared-types/       DTOs partagés
├── minecraft/
│   ├── mod-hud/            Fabric client — TOUTE la couche UI (menu, ESC, HUD,
│   │                       chat RP, éditeur HUD, créateur de perso, boutique,
│   │                       sacoche, combat client, caméra, emotes)
│   ├── mod-integrity/      Fabric client — attestation play-token au JOIN, rien d'autre
│   ├── mod-ost/            Fabric client — décodage Ogg Vorbis (STBVorbis) + OpenAL
│   ├── plugin-guardian/    Paper — vérifie le play-token, kick si invalide
│   ├── plugin-ost/         Paper — broadcast OST + zone registry + late-join
│   ├── shinobi/            ⭐ 6 plugins Paper/Purpur (Maven, agrégateur en racine)
│   │   ├── ShinobiCore/      personnages, chakra, techniques, KO, progression,
│   │   │                     mobilité, HUD push, inventaire RP, emotes
│   │   ├── ShinobiAbilities/ kit de sorts de base
│   │   ├── ShinobiCombat/    kenjutsu / taïjutsu, parade, endurance
│   │   ├── ShinobiLearning/  apprentissage des techniques
│   │   ├── ShinobiSense/     perception (Sharingan, Byakugan, radar chakra)
│   │   └── ShinobiTail/      transformations jinchūriki / bijū
│   └── server-config/      templates server.properties, MagicSpells, Nexo
├── tools/
│   ├── blockbench-reborn-compositor/  plugin Blockbench — compositeur de skins RP
│   ├── blockbench-creator-tools/      plugin Blockbench — suite Creator Tools
│   │                                 (Blockout, Handpaint, Motion Lab)
│   └── claude-hooks/                  hook Claude Code → notif Discord via le bot
├── scripts/     publish-launcher.ps1, publish-mod-manifest.ps1
├── infra/       docker-compose (dev + prod), Caddy
└── docs/        cf. index du README
```

> Les plugins **Shinobi** ont un dépôt d'origine séparé (`ShinobiReborn`) ; la copie de
> travail **fait autorité ici**. Toute doc qui dit « repo séparé, non présent dans ce
> repo » est périmée (c'était vrai jusqu'en août 2026).

---

## 6. Deux trains de release — à ne jamais mélanger

| Train | Contenu | Chemin de déploiement | Base |
|---|---|---|---|
| **Jeu** | mods clients + plugins Paper | manifest signé (auto-update launcher) + upload manuel Minestrator | worktree game-side |
| **Web** | `apps/api`, `apps/admin`, `apps/bot` | `git pull` + `docker compose up -d --build` sur le VPS | `origin/main` |

Un commit ne doit jamais toucher les deux. Détails et pièges :
[`PUBLISH_PREFLIGHT.md`](./PUBLISH_PREFLIGHT.md) §2.

---

## 7. Ce qui est livré et jouable

**Launcher & web** — auth Microsoft (Client ID approuvé Mojang, cf ADR 0001),
liaison Discord, auto-update Ed25519, manifest signé + purge stricte des mods,
whitelist + tickets avec pont Discord bidirectionnel HMAC, Rich Presence,
panel staff (dashboard, whitelist, tickets, joueurs, audit, anomalies, wiki,
gestionnaire de fichiers scopé par grade), bot Discord.

**En jeu** — menu principal + ESC + ConnectScreen custom, HUD éditable
(drag/resize/hide + presets), chat RP custom (têtes, mentions, timestamps, freecam),
plaques RP de proximité, créateur de personnage (11 clans, gating village/genre,
catalogue data-driven), boutique de tenues 360° (Ryo), sacoche RP (inventaire lié au
perso + poids + cosmétiques), caméra 3ᵉ personne + Naruto run + dash + dodge + saut
chakraïque, combat kenjutsu/taïjutsu M1 + parade gated par l'endurance, HUD de
cooldowns, emotes RP (EmoteCraft) avec distribution serveur, OST contextuelle,
attestation play-token.

**Chantiers en cours** — refonte HUD (hub de config type OneConfig, crosshair),
consolidation combat, `modrinth-sync` (détection auto des mises à jour de mods).

---

## 8. Rôles

`PLAYER` < `WHITELISTED` < `HELPER` < `MODERATOR` / `WHITELIST_REVIEWER` <
`MODELISATEUR` / `DEVELOPPEUR` < `ADMIN` < `OWNER`.

`LAUNCHER_BETA_GATE=HELPER` empêche les non-staff de se connecter (beta fermée).
Les grades **Modélisateur** et **Développeur** ouvrent des périmètres du gestionnaire
de fichiers du panel (Nexo, MagicSpells, Emotes RP, Character creator) — cf.
[`NEXO_STAFF_GUIDE.md`](./NEXO_STAFF_GUIDE.md).

---

## 9. Monnaies

| Monnaie | Obtention | Usage |
|---|---|---|
| **Ryo** | gagnée en jeu (RP, combat, quêtes) | boutique de tenues, consommables |
| **RBCoins 💎** | achetée (Stripe, v1.1) ou gagnée via Bug Bounty | cosmétiques premium |

« ZK Coin » (résidu de l'identité *Zenkai*, abandonnée en juin 2026 au profit
d'**Akatsuki**) est **retiré** — voir [`AUDIT_COHERENCE.md` §4](./AUDIT_COHERENCE.md).

---

## 10. Boards

| Board | URL | Rôle |
|---|---|---|
| Trello *[WIP] FR - Naruto* | <https://trello.com/b/OqmICLMD/wip-fr-naruto> | **public** — transparence communauté + plan de vol beta |
| Miro *[WIP] FR - Shinobi Reborn* | <https://miro.com/app/board/uXjVGubPsdk=/> | interne — roadmap visuelle, HUD, matrice combat, progression, API ShinobiCore, sprints |

Le Trello étant **public**, aucune information sensible (IP, secrets, chemins VPS) ne
doit y figurer.
