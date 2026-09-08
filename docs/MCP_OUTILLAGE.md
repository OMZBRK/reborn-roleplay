# Outillage MCP — Blockbench, Discord, Blender

> Réponse à trois questions : peut-on brancher Claude sur **Blockbench** (génération de
> modèles 3D), sur **Discord** (relais d'infos vers le serveur staff), et sur **Blender**
> (animations 3D depuis un fichier player-animator / EmoteCraft en 26.2) ?
>
> **Réponse courte : oui pour les trois, mais pas au même prix, et pas pour ce qu'on
> croit.** Le gain n'est pas « l'IA dessine à ma place », il est dans la **plomberie** :
> conventions respectées, validation, conversion, distribution, zéro allers-retours.
>
> Rédigé le 2026-09-08. MCP = *Model Context Protocol*, le standard qui expose des outils
> à un assistant. Tout ce qui suit tourne **sur la machine de dev**, jamais chez un joueur.

---

## 0. Ce qui existe déjà chez nous (et qui change la réponse)

Avant d'ajouter quoi que ce soit, l'inventaire — parce qu'il rend deux des trois
chantiers beaucoup moins chers que prévu :

| Brique | Où | Ce que ça donne |
|---|---|---|
| **2 plugins Blockbench maison** | `tools/blockbench-reborn-compositor`, `tools/blockbench-handpaint` | l'API JS de Blockbench est déjà maîtrisée, avec un build TS + esbuild + tests |
| **API Fichiers scopée par grade** | `apps/api/src/files/` | `GET /v1/files/{scopes,reload-targets,list,read}`, `POST /v1/files/{write,upload,mkdir,move,reload}`, `DELETE /v1/files` — gate `@MinRole(MODELISATEUR)`, garde anti-`..`, `.bak` et audit automatiques |
| **Pont de commandes serveur** | `apps/api/src/files/commands.controller.ts` | file d'attente HMAC : `POST /v1/files/reload` → le serveur MC dépile et acquitte. C'est déjà un `/nexo reload` télécommandé |
| **Bot Discord + webhooks HMAC** | `apps/bot/src/webhook-server.ts` | `/webhooks/{whitelist,tickets,security-alert,dm,assignment-update,status-update,claude-notify,*-message}`, tous signés avec `REBORN_WEBHOOK_SECRET` |
| **Hook Claude → Discord** | `tools/claude-hooks/notify-discord.mjs` | preuve que le chemin *Claude → bot → Discord* fonctionne déjà |
| **PAL + EmoteCraft embarqués** | `minecraft/mod-hud/libs/` | `PlayerAnimationLibMerged 1.2.6+mc.26.2`, `emotecraft 3.4.0-b.165` |
| **Distribution d'emotes serveur** | `ShinobiCore` + `reborn:emotepack` | déposer un `.emotecraft` → `/playemote reload` → poussé à tous les clients |

**Conséquence directe** : on n'a pas besoin de SFTP, ni d'un token Discord
supplémentaire, ni d'un canal de déploiement neuf. Les trois MCP se branchent sur des
API qui existent, avec l'authentification et l'audit qui vont avec.

---

## 1. Blockbench — ✅ faisable, deux étages

### 1.1 Ce qui existe en amont

Deux serveurs MCP communautaires pour Blockbench :

- **`jasonjgardner/blockbench-mcp-plugin`** — le plus abouti. C'est un **plugin
  Blockbench** qui héberge lui-même un serveur MCP en **HTTP** (`:3000/bb-mcp`), chargé
  dans Blockbench par URL, et qui expose l'API JS de Blockbench à un agent. Compatible
  Claude Code / Claude Desktop / VS Code / Cline / Ollama. GPL-3.0.
- **`enfp-dev-studio/blockbench-mcp`** — approche socket, plus simple, moins couvrante.

L'un ou l'autre donne le **contrôle live de l'application** : créer des cubes, éditer
des UV, peindre des texels, lancer des exports.

### 1.2 Le vrai découpage : deux étages, pas un

Piloter Blockbench à la voix est spectaculaire mais ce n'est **pas** là qu'est le gain.
Un LLM qui pose des cubes produit du modèle générique. Ce qui coûte cher chez nous,
c'est tout **autour** du modèle :

| | Étage 1 — « fichiers & conventions » | Étage 2 — « pilotage live » |
|---|---|---|
| **Quoi** | lire/écrire `.bbmodel`, `.json` Java Item, PNG, `.mcmeta`, `items/*.yml` | contrôler Blockbench en direct |
| **Où** | serveur MCP autonome, aucun Blockbench requis | plugin Blockbench (MCP embarqué) |
| **Valeur** | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **Coût** | ~2-3 jours | ~1 jour (adopter l'existant) |

**L'étage 1 est celui qui rembourse.** Il automatise exactement les pièges que
[ADR 0005](./adr/0005-pipeline-assets-3d-nexo-panel.md) documente :

- `reborn_model_fix_textures` — réécrit `"textures": {"0": "ma_texture"}` en
  `"reborn:item/ma_texture"`. **Le piège n°1 de Nexo**, celui qui donne un modèle violet,
  corrigé automatiquement sur tout un lot.
- `reborn_spritesheet_build` — assemble N frames carrées en spritesheet **vertical** et
  génère le `.png.mcmeta` avec le bon `frametime` et la bonne liste de `frames`. Le
  piège n°2, éliminé.
- `reborn_nexo_declare` — ajoute l'entrée dans `Nexo/items/*.yml` (material `PAPER` +
  `Pack.model`), sans casser le YAML existant.
- `reborn_asset_publish` — **upload via `POST /v1/files/upload`** puis
  **`POST /v1/files/reload`** → `/nexo reload` en jeu. Scopé par le grade du compte
  utilisé, `.bak` et audit automatiques. Pas de SFTP, pas de restart.
- `reborn_model_lint` — vérifie avant publication : chemins de texture, taille du PNG,
  cohérence spritesheet/mcmeta, id du modèle == nom de fichier, présence de l'entrée YAML.

**Cas d'usage réel** : « prends ces 12 kunai variantes exportées de Blockbench,
corrige les chemins de texture, déclare-les dans `reborn_armes_ninja.yml`, publie et
reload ». Aujourd'hui : ~40 min de manipulations à la souris et un modèle violet sur
deux. Avec l'étage 1 : une phrase.

### 1.3 Recommandation

1. **Construire l'étage 1** en premier, comme un namespace de `packages/reborn-mcp` (§4).
2. **Adopter** `jasonjgardner/blockbench-mcp-plugin` tel quel pour l'étage 2, en
   évaluation — ne pas réécrire un plugin MCP alors qu'on en a déjà deux à maintenir.
3. Si l'étage 2 tient ses promesses, y ajouter nos outils métier (appeler le compositeur
   de skins, déclencher un bake AO) plutôt que d'exposer des primitives génériques.

---

## 2. Discord — ✅ faisable, et **surtout : pas avec un MCP Discord générique**

### 2.1 Le piège

Les MCP Discord communautaires (`v-3/discordmcp`, `SaseQ/discord-mcp`,
`BrainDAO/mcp-discord`) demandent un **token de bot** dans la configuration du serveur
MCP. Ce token porte **toutes** les permissions accordées au bot sur la guilde : lire
n'importe quel salon, poster partout, gérer des membres selon les scopes.

Mettre le token du **bot Reborn de production** dans un MCP local, c'est donner à une
session d'assistant les pleins pouvoirs sur le Discord du projet, sans granularité et
sans trace. Ça ne passe pas.

### 2.2 Ce qu'on fait à la place

Le bot expose déjà un serveur HTTP signé HMAC. **L'MCP parle au bot, pas à Discord.**

```
Claude Code (session dev)
   └─ reborn-mcp · namespace discord
        └─ POST http://localhost:3001/webhooks/<route>   (HMAC REBORN_WEBHOOK_SECRET)
             └─ bot Reborn  →  salon staff / thread / DM
```

Avantages, tous structurels :

- **Aucun token Discord n'entre dans la config MCP.** Le seul secret partagé est
  `REBORN_WEBHOOK_SECRET`, déjà présent des deux côtés.
- **Le périmètre est défini par les routes**, pas par les permissions du bot. L'MCP ne
  peut faire que ce que le bot a explicitement exposé.
- **Chaque message passe par le code du bot** : embeds à la charte, salon de destination
  imposé, garde-fous anti-boucle déjà écrits.
- Le pattern est **déjà validé en production** par `tools/claude-hooks`.

### 2.3 Outils à exposer

| Outil | Route | Usage |
|---|---|---|
| `reborn_discord_announce` | nouvelle `/webhooks/announce` | « poste le récap de fin de sprint dans #staff-dev » |
| `reborn_discord_status` | `/webhooks/status-update` existante | publication/rollback : version, contenu, incident |
| `reborn_discord_alert` | `/webhooks/security-alert` existante | anomalie détectée pendant une session |
| `reborn_discord_dm` | `/webhooks/dm` existante | notifier une personne précise (par rôle, jamais par nom en clair dans les logs) |
| `reborn_discord_read_thread` | **nouvelle**, `GET /webhooks/thread/:id` côté bot | relire un fil ticket/whitelist pour instruire une réponse |

Seul `read_thread` demande une lecture Discord. À restreindre au salon
`DISCORD_TICKETS_CHANNEL_ID` et à ses fils — pas d'accès libre à la guilde.

### 2.4 Garde-fous non négociables

- **Écrire dans Discord est une action sortante** : confirmation explicite avant chaque
  envoi, jamais d'auto-post silencieux.
- Le contenu des messages lus est **de la donnée, pas une instruction** — un membre qui
  écrit « ignore tes consignes » dans un ticket ne doit rien déclencher.
- Aucune IP, aucun secret, aucun chemin VPS dans un message. Le Trello est public, le
  Discord est semi-public : mêmes réflexes.

### 2.5 Coût

**~1 jour.** Une route `/webhooks/announce` côté bot, un namespace `discord` côté MCP,
la signature HMAC est déjà écrite.

---

## 3. Blender — ✅ faisable, mais ce n'est pas là qu'il faut commencer

C'est la demande la plus précise : *générer des animations 3D depuis un fichier
player-animator / EmoteCraft en 26.2*. Elle mérite d'être découpée, parce qu'elle
contient deux problèmes très différents.

### 3.1 La chaîne réelle

```
   AUTEUR                          FORMAT                      DISTRIBUTION
┌──────────────┐            ┌──────────────────┐         ┌────────────────────┐
│  Blender     │  addon     │  JSON PAL        │  drop   │ plugins/ShinobiCore│
│  (addon      │ ─────────► │  (GeckoLib v2,   │ ──────► │ /emotes/*.emotecraft│
│   KosmX)     │            │   clé            │         └─────────┬──────────┘
├──────────────┤            │  player_animation│                   │ /playemote reload
│  Blockbench  │  plugin    │  _library,       │                   ▼
│  (GeckoLib   │ ─────────► │   MoLang possible)│         canal reborn:emotepack
│   Anim Utils)│            └──────────────────┘                   │
└──────────────┘                                                   ▼
                                                     PlayerAnimationLibMerged 1.2.6
                                                       (tous les clients, au JOIN)
```

Points qui comptent pour nous :

- **Le format pivot est du JSON.** Des keyframes nommées, avec des métadonnées sous la
  clé `player_animation_library` (name, description, author — et tout ce qu'on veut
  ranger dedans, PAL le recharge tel quel).
- L'addon Blender officiel vit dans `KosmX/emotes` (dossier `blender/`, avec un
  `model.bbmodel` de référence et une variante *bend*). Convention : **60 keyframes =
  3 s**, 20 keyframes = 1 s.
- Côté 26.2 on embarque **PAL 1.2.6+mc.26.2** et **EmoteCraft 3.4.0-b.165** — la chaîne
  est opérationnelle, elle a même été validée par une emote de test sur la touche `,`
  (`eba2096`).
- La distribution est déjà **sans rebuild** : dépôt du fichier + `/playemote reload`,
  garde-fou à 512 Ko.

### 3.2 Deux problèmes, pas un

**Problème A — manipuler des animations existantes.** Retimer, inverser, mettre en
miroir gauche/droite, corriger un point de bouclage, découper un combo en 3 fichiers,
renommer et re-métadonner un lot, convertir Blockbench → PAL, valider avant publication,
comparer deux versions.

→ **C'est du JSON. Blender n'est pas nécessaire.** Un MCP « format » fait tout ça, de
façon déterministe et testable. C'est **80 % du travail récurrent** sur les emotes, et
c'est le chantier à faire en premier.

**Problème B — créer un mouvement full-body crédible.** Roulade, backstep, projection,
relevée, atterrissage, finisher.

→ Là il faut Blender **et un animateur**. `docs/ANIMATIONS_BASE.md` le dit déjà
franchement : hand-authoring possible pour les poses simples (idle, garde, focus chakra,
hitstun léger, bow), **rendu robotique** pour tout ce qui a du poids et du contact. Un
MCP ne change pas ce constat : un LLM qui pose des keyframes de roulade produit une
roulade qui ne convainc personne.

### 3.3 Ce que Blender MCP apporte quand même

Les serveurs existants (`ahujasid/blender-mcp`, le projet officiel *Blender lab*,
`djeada/blender-mcp-server`) exposent l'API Python de Blender à un agent. Utile chez
nous pour :

- **Le batch** : ouvrir 20 `.blend`, exporter en PAL, nommer selon la convention,
  déposer — sans clics.
- **Le setup** : monter la scène (import du rig `model.bbmodel`, réglage 60 frames,
  addon KosmX chargé) pour qu'un animateur démarre sur une base propre.
- **Le rendu de vignettes** : générer une preview GIF/PNG par emote, pour un catalogue
  consultable côté staff.
- **Le blocking**, à la rigueur : poser des poses clés grossières qu'un humain repasse.

⚠️ **Avertissement de sécurité, mot pour mot des projets amont** : un Blender MCP
**exécute du code Python généré par un LLM dans Blender**, sans bac à sable. À n'utiliser
que sur une machine ou une VM sans données sensibles, jamais sur le poste qui héberge
`secrets/`.

### 3.4 Recommandation

1. **D'abord** le namespace `anim` du MCP format (problème A). Aucun Blender requis,
   valeur immédiate, testable unitairement.
2. **Ensuite seulement**, et si le besoin de batch/preview se confirme, évaluer un
   Blender MCP en VM, cantonné à l'export et au rendu de vignettes.
3. **Ne pas attendre** d'un MCP qu'il remplace un animateur sur le problème B.

---

## 4. Architecture proposée — un seul serveur, trois namespaces

Plutôt que trois serveurs MCP hétérogènes à configurer et maintenir séparément :

```
packages/reborn-mcp/          (TypeScript, transport stdio, aligné sur le workspace pnpm)
├── src/
│   ├── index.ts              enregistrement des outils + config
│   ├── auth.ts               JWT staff (Credential Manager, comme manifest-uploader)
│   │                         + signature HMAC pour le bot
│   ├── nexo/                 modèles 3D, textures, spritesheets, items YAML, publication
│   ├── anim/                 JSON PAL : lire, valider, retimer, miroir, convertir, publier
│   └── discord/              relais vers le bot Reborn (jamais vers Discord en direct)
└── README.md
```

Choix structurants :

- **Réutiliser l'authentification existante.** `packages/manifest-uploader` lit déjà le
  JWT staff depuis le Credential Manager Windows et le rafraîchit tout seul. Même
  mécanisme ici : le MCP agit **en tant que le compte staff connecté**, avec ses grades,
  et chaque action apparaît dans l'audit du panel. Aucun secret nouveau.
- **Passer par les API existantes**, pas par le disque du serveur ni par SFTP. Le
  périmètre est celui du grade, la garde anti-`..` est déjà écrite, les `.bak` aussi.
- **Écrire les outils comme des fonctions pures + une couche IO.** Le cœur (parser un
  `.bbmodel`, assembler un spritesheet, retimer un JSON PAL) se teste sans Blockbench,
  sans Blender et sans serveur — exactement le découpage déjà retenu dans
  `tools/blockbench-handpaint` (`pnpm test` sur le cœur math).
- **Toute action sortante demande confirmation** : publier un asset, poster sur Discord,
  déclencher un reload serveur.

### Séquencement proposé

| Ordre | Chantier | Effort | Valeur |
|---|---|---|---|
| 1 | `discord/` — relais via le bot | ~1 j | ⭐⭐⭐⭐ récap de sprint, alertes, publication automatisés |
| 2 | `nexo/` — modèles, spritesheets, publication | ~3 j | ⭐⭐⭐⭐⭐ débloque la charge n°1 de la beta (assets art) |
| 3 | `anim/` — JSON PAL | ~2 j | ⭐⭐⭐⭐ retime/miroir/validation/distribution des emotes |
| 4 | Blockbench MCP live (adopter l'existant) | ~1 j éval | ⭐⭐ confort, à juger sur pièce |
| 5 | Blender MCP en VM | ~2 j | ⭐⭐ batch export + vignettes seulement |

**~6 jours** pour les trois premiers, qui portent l'essentiel du gain.

> ⏳ **Quand ?** Le Sprint 1 est déjà chargé (design + foundation code + assets wave 1).
> Ce chantier n'est pas dans les 16 chantiers de la beta et **ne doit pas mordre dessus**.
> Fenêtre raisonnable : entre le Sprint 1 et le Sprint 2, ou en parallèle si quelqu'un
> d'autre le prend — l'étage `nexo/` se rembourse dès le Sprint 2 (bundles claniques,
> VFX, armes).

---

## 5. Ce qu'il ne faut pas en attendre

Pour que la décision soit prise sur des bases honnêtes :

- ❌ **Un LLM ne fera pas le travail artistique.** Ni un modèle 3D avec du caractère, ni
  une animation de combat qui a du poids. Il fait la mise en conformité, la conversion,
  la validation, le batch et la distribution.
- ❌ **Un MCP n'accélère pas le build.** Compiler `mod-hud`, signer un manifest, publier
  une release restent ce qu'ils sont.
- ❌ **Un MCP Discord générique est un risque net**, pas un gain — voir §2.1.
- ✅ **Ce qu'il fait vraiment disparaître** : les pièges à répétition (chemin de texture
  Blockbench, spritesheet mal empilé, id/YAML désynchronisés), les allers-retours
  panel ↔ Blockbench ↔ jeu, et le temps passé à publier plutôt qu'à créer.

---

## Sources

- [Blockbench MCP plugin — jasonjgardner](https://github.com/jasonjgardner/blockbench-mcp-plugin)
- [BlockbenchMCP — enfp-dev-studio](https://github.com/enfp-dev-studio/blockbench-mcp)
- [Blender MCP — ahujasid](https://github.com/ahujasid/blender-mcp) · [Blender lab — MCP Server](https://www.blender.org/lab/mcp-server/) · [blender-mcp-server — djeada](https://github.com/djeada/blender-mcp-server)
- [KosmX/emotes — outil Blender](https://github.com/KosmX/emotes/tree/dev/blender) · [Emotecraft — Blender emote creator](https://kosmx.gitbook.io/emotecraft/tutorial/section-3-the-blender-emote-creator) · [PlayerAnimationLibrary & Emotecraft — créer des emotes avec Blockbench](https://docs.zigythebird.com/emotecraft/creatingemotes/blockbench/)
- [KosmX/minecraftPlayerAnimator](https://github.com/KosmX/minecraftPlayerAnimator)
- MCP Discord communautaires : [v-3/discordmcp](https://github.com/v-3/discordmcp) · [SaseQ/discord-mcp](https://github.com/SaseQ/discord-mcp) · [BrainDAO/mcp-discord](https://github.com/BrainDAO/mcp-discord)
