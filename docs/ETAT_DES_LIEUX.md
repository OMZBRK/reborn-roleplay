# État des lieux — Reborn Roleplay

> **Ce document décrit le PRÉSENT** : ce qui tourne, dans quelle version, où. Il fait foi quand une autre doc et
> la réalité divergent (le code et le manifest live restant l'arbitre final).
>
> L'avancement par chantier est dans [`CHANTIERS.md`](./CHANTIERS.md), le futur dans
> [`ROADMAP_BETA_2026.md`](./ROADMAP_BETA_2026.md), l'historique des versions dans
> [`pilotage/JOURNAL_DES_LIVRAISONS.md`](./pilotage/JOURNAL_DES_LIVRAISONS.md).
>
> **Dernière mise à jour : 2026-10-15** (reborn-hud 0.4.183, reborn-ost 0.2.5, manifest 3.1.140 ; launcher 0.3.46). À mettre à jour à **chaque
> publication** (commit `docs(etat)` séparé).

---

## 1. Plateforme cible

| Élément | Version | Où c'est figé |
|---|---|---|
| **Minecraft** | **26.2** | `apps/launcher/src-tauri/src/launcher/game.rs` (défaut), env `REBORN_MC_VERSION` |
| **Java** | **25** | mods + plugins + serveur. JDK portable de ce poste : `C:\Users\omarb\dev-cache\jdk-25.0.4.1+1` |
| **Fabric Loader** | 0.19.3 | `minecraft/*/gradle.properties` |
| **Fabric API** | 0.156.0+26.2 | idem |
| **Mappings** | **aucune** — le client 26.x est livré déobfusqué | pas de ligne `mappings(...)` |
| **Serveur** | Purpur 26.2 | Minestrator (prod, dev, build) |

> ⚠️ Depuis 26.x, Yarn n'existe plus et le rendu GUI passe par `GuiGraphicsExtractor`. Toute recette antérieure
> à août 2026 est caduque — [`technique/MIGRATION_26.2.md`](./technique/MIGRATION_26.2.md).

---

## 2. Artefacts — ce qui est en ligne

| Artefact | **En ligne** | Sur `origin/main` | Notes |
|---|---|---|---|
| Launcher | **0.3.46** (14/10) | 0.3.46 | ✅ Accueil Microsoft puis Discord obligatoire |
| `reborn-hud` | **0.4.183** — manifest **3.1.140** (15/10) | 0.4.183 | ✅ 0.4.183 : icônes (recharges, effets, natures) à la bonne taille à toutes les échelles de l'éditeur de HUD, touche X pour la course de chakra. 0.4.182 : 15 touches Reborn au lieu de 26, sans conflit, défauts AZERTY / QWERTZ et migration unique ; marche sur l'eau v8 ; personnages gérés au Poste de garde (F7). 0.4.181 : boutiques en jeu (tailleur, bazar, /boutique), météo blizzard / tempête de sable, marche sur l'eau, scène de chargement « nuit de pleine lune » avec pack Nexo accepté sans écran. Inclut 0.4.180 (crash au clic « Reprendre » de l'Échap avec Sodium ; manifest 3.1.136 signé, jamais envoyé). Le 3.1.135 livrait d'un coup 0.4.176 (journal universel, commandes staff, allures, KO v2), 0.4.178 (Poste de garde v3, états du joueur, nourriture et entreprises, natures canoniques) et 0.4.179 (radio en escouades, monnaie et banque, halo pixel art) : les manifests 3.1.132 à 3.1.134 ont été signés mais jamais envoyés (401 du launcher). Le 3.1.135 passe aussi PlasmoVoice client en 2.1.17. ⚠️ Touches radio par défaut B / N en conflit avec Emotecraft / OST. Détail de chaque version : [journal](./pilotage/JOURNAL_DES_LIVRAISONS.md) |
| `reborn-integrity` | 0.3.1 | 0.3.1 | ✅ |
| `reborn-ost` | **0.2.5** — manifest **3.1.139** (15/10) | 0.2.5 | ✅ Touche N par défaut (carte des touches réduite) |
| Plugins **Shinobi** — serveur **dev** | ShinobiCore du **15/10 soir** (`b5bf568` : personnages gérés depuis le Poste de garde, /character edit, /persoreset et /persosuppr retirés) ; ShinobiAbilities du **15/10 soir** (`099ac90` : souffle discret de la course de chakra, clés `mobility.naruto-run.souffle-*` ; avant : `7b4f214` boutiques, /meteo blizzard|tempetesable, MARCHE_EAU) ; ShinobiCombat, ShinobiTail, ShinobiLearning et le nouveau **ShinobiRadio** du **15/10** (`17397e3` : journal universel, commandes staff, allures, KO v2, états du joueur, nourriture, entreprises, monnaie et banque, radio en escouades) ; plugin serveur **PlasmoVoice 2.1.17** (2.1.14 en `.bak`) ; pack Nexo nourriture (14/10) puis monnaie + testament + oreillette (15/10), parchemins + fiole d'œil du 13/10 ; ShinobiSense du 04/10 (**`ByakuganVision.java` non commité**) | `minecraft/shinobi/` | Déploiement SFTP manuel, redémarrage par l'utilisateur |
| Plugins **Shinobi** — serveur **prod** | ⚠️ non tracé précisément ; au 10/10 son ShinobiCore n'envoyait pas âge / taille / sexe | — | Inventaire à faire avant la mise en prod |
| `reborn-guardian`, `reborn-ost-plugin` | build Gradle | `minecraft/plugin-*` | Déploiement SFTP manuel |

> ⚠️ **Les tags `mods-v*` ne sont pas des marqueurs fiables** (certains pointent sur une version antérieure à
> celle publiée). Pour connaître la version livrée : le **manifest live** (`manifest-uploader get-manifest` ou
> `/v1/manifest/current`, authentifié).

---

## 3. Infrastructure

| Service | Adresse | Hébergeur |
|---|---|---|
| API | `https://api.reborn-rp.com/v1` (`/health` → `ok`) — `main` du 15/10 (`984e4b2` : familles de sessions, un replay de refresh token ne coupe plus que sa famille) | VPS OVH (Gravelines), Docker + Caddy |
| Panel staff | `https://panel.reborn-rp.com` | même VPS |
| Serveur MC **prod** | `play.reborn-rp.com:27106` | Minestrator |
| Serveur MC **dev** | port de jeu 25606 (hôte dans le `.env` : `REBORN_SERVER_DEV_HOST`) | Minestrator |
| Serveur MC **build** | instance dédiée (manifest builder séparé, §4) | Minestrator |
| Postgres + Redis | internes au VPS, jamais exposés | Docker |
| Bot Discord | conteneur `bot`, webhooks sur `:3001` | même VPS |
| Dépôt | `github.com/OMZBRK/reborn-roleplay` (**public**) | GitHub |

- Secrets de prod : `/opt/reborn/.env.prod` (VPS) + `secrets/` (machine locale, jamais commité). Liste :
  [`operations/MAINTENANCE.md`](./operations/MAINTENANCE.md) §12.
- **Plugins Paper** : déploiement **SFTP** (dev : mot de passe dans le gestionnaire d'identification Windows,
  entrée `reborn-sftp-dev`) ou panel Minestrator ; **redémarrage manuel** par l'utilisateur (pas de rechargement
  à chaud). Recette : [`operations/PUBLICATION.md`](./operations/PUBLICATION.md) §3.
- Le dépôt étant public, l'adresse IP des serveurs ne figure plus ici.

---

## 4. Modpack — deux manifests

| Manifest | Pour qui | Contenu |
|---|---|---|
| **Joueur** (`/v1/manifest/current`) | tous les joueurs | ~19 mods + les pistes OST (`reborn/ost/<catégorie>/*.ogg`) |
| **Builder** (`secrets/builder-manifest-signed.json`) | staff builders (« Serveur Build ») | Axiom, Distant Horizons, Sodium/Iris/Lithium — pas les mods RP |

Modpack joueur (26.2) : **requis** `fabric-api`, `fabric-language-kotlin`, `sodium`, `sodium-extra`, `lithium`,
`entityculling`, `yet_another_config_lib_v3`, `entity_texture_features`, `firstperson`, `plasmovoice`,
`emotecraft` 3.4.0, `PlayerAnimationLibMerged` 1.2.6, `reborn-hud`, `reborn-integrity`, `reborn-ost` ;
**optionnels** `iris`, `zoomify`, `NoChatReports`, `entity_model_features`, `DistantHorizons`.

- Les pistes OST sont livrées **par le manifest**, pas dans un jar. `reborn-hud` pèse ~28 Mo.
- `secrets/manifest-signed.json` peut être périmé : partir du **dernier** `manifest-signed-v*.json` ou du live.
- ❓ À vérifier : l'existence réelle de deux manifests distincts (un modpack unifié a été évoqué en septembre).

---

## 5. Périmètre du monorepo

```
reborn-roleplay/
├── apps/
│   ├── launcher/   Tauri 2 + React 19 + TS — client desktop
│   ├── api/        NestJS 11 + Prisma + Postgres + Redis (préfixe /v1)
│   ├── admin/      Next.js — panel staff (dashboard, whitelist, tickets, joueurs, audit, anomalies,
│   │               inbox, oral-slots, wiki, fichiers scopés par grade, 2FA, abilities)
│   └── bot/        discord.js v14 (ESM) — slash commands + serveur HTTP :3001
├── packages/       manifest-signer (TS), manifest-uploader (Rust), shared-types, ability-compiler,
│                   modrinth-sync
├── minecraft/
│   ├── mod-hud/            Fabric client — TOUTE l'interface Reborn
│   ├── mod-integrity/      Fabric client — attestation play-token au JOIN
│   ├── mod-ost/            Fabric client — lecture Ogg Vorbis + OpenAL
│   ├── plugin-guardian/    Paper — vérifie le play-token
│   ├── plugin-ost/         Paper — diffusion OST + zones + late-join
│   ├── shinobi/            6 plugins Paper (Maven) : Core, Abilities, Combat, Learning, Sense, Tail
│   └── server-config/      modèles server.properties, MagicSpells, Nexo
├── tools/          bbforge (modèles 3D par code), blockbench-handpaint, blockbench-reborn-compositor,
│                   claude-hooks, map-render, ui-art
├── scripts/        publish-launcher.ps1, publish-mod-manifest.ps1
├── infra/          docker-compose (dev + prod), Caddy
└── docs/           cf. docs/README.md
```

Les plugins Shinobi ont eu un dépôt séparé (`ShinobiReborn`) ; **la copie de ce dépôt fait autorité**
([ADR 0003](./adr/0003-shinobi-dans-le-monorepo-deux-trains-de-release.md)).

---

## 6. Deux trains de release

| Train | Contenu | Chemin |
|---|---|---|
| **Jeu** | mods clients + plugins Paper | manifest signé (auto-update du launcher) + SFTP / panel Minestrator |
| **Web** | `apps/api`, `apps/admin`, `apps/bot` | `git pull` + `docker compose up -d --build` sur le VPS, depuis `main` |

Jamais dans le même commit. Détails : [`operations/PUBLICATION.md`](./operations/PUBLICATION.md).

---

## 7. Ce qui est livré et jouable

**Launcher et web** — auth Microsoft (App ID approuvé, [ADR 0001](./adr/0001-microsoft-app-approval-required.md)),
liaison Discord, auto-update Ed25519, manifest signé + purge stricte des mods, whitelist et tickets avec pont
Discord bidirectionnel, Rich Presence, panel staff (dont gestionnaire de fichiers par grade et éditeur de
techniques), bot Discord, détection automatique des mises à jour de mods (modrinth-sync).

**En jeu** (mod + plugins) :
- **Interface** entièrement dans la DA Reborn : menu principal, Échap « Tokonoma », réglages, fiche shinobi
  « lanternes célestes » (K), sacoche « inrō », échoppe, carte du monde (M), chat en panneau, éditeur de HUD,
  viseur, caméra, mode photo et galerie, noms au-dessus des têtes (4 styles), tab list RP.
- **Personnages** : sélection et création en scène, apparence composée, âge et taille.
- **Progression** : 6 stats, XP invisible (1 niveau = 1 point), 9 grades RP (`/grade`), Mérite.
- **Combat et mouvement** : M1 kenjutsu / taïjutsu, parade, endurance, HUD combat, Naruto run, dash, double saut,
  saut de chakra, voies de mobilité ; Byakugan.
- **Apprentissage** : parchemins par rang, bibliothèques à tirage, séances (+ concentration du chakra sur le dev).
- **KO et mort RP** : PV persistants hors terrain d'entraînement, à terre / inconscient / hôpital, ATA, soins
  (paume, bandages, `/soigner`), menu d'interaction sur les corps, RPK validée par le staff puis exécutée, scène de
  mort.
- **Staff** : Poste de garde (F7) avec Infirmerie.
- **Ambiance** : météo visuelle, OST contextuelle, emotes RP (EmoteCraft) distribuées par le serveur.
- **Sécurité** : attestation play-token.

**En cours** : voir [`CHANTIERS.md`](./CHANTIERS.md).

---

## 8. Rôles

**Web (panel / API)** : `PLAYER` < `WHITELISTED` < `HELPER` < `MODERATOR` / `WHITELIST_REVIEWER` <
`MODELISATEUR` / `DEVELOPPEUR` < `ADMIN` < `OWNER`. `LAUNCHER_BETA_GATE=HELPER` réserve la connexion au staff
(beta fermée). Modélisateur et Développeur ouvrent des périmètres du gestionnaire de fichiers
([`staff/NEXO_STAFF_GUIDE.md`](./staff/NEXO_STAFF_GUIDE.md)).

**En jeu (Poste de garde)** : `reborn.staff.helper` < `.modo` < `.admin` < `.owner` (permissions, pas encore
synchronisées avec le web) — [`staff/GUIDE_STAFF_EN_JEU.md`](./staff/GUIDE_STAFF_EN_JEU.md).

---

## 9. Monnaies

| Monnaie | Obtention | Usage |
|---|---|---|
| **Ryo** | gagnée en jeu | échoppe (tenues, cartes), consommables |
| **RBCoins** | achetée (Stripe, v1.1) ou Bug Bounty, récompenses d'activation | cosmétiques premium |

Colonne `rbCoinBalance` en base (ex-`zkCoinBalance`, renommée le 01/10 — migration appliquée au prochain
déploiement web). Le solde RBCoins affiché dans l'Échap est encore celui des ryo. [ADR 0006](./adr/0006-monnaies-ryo-et-rbcoins.md).

---

## 10. Boards

Trello public et Miro staff : [`pilotage/BOARDS.md`](./pilotage/BOARDS.md).
