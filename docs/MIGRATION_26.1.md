# Plan de migration Reborn Roleplay → Minecraft 26.1

> Rédigé le 2026-08-05. Objectif : transition **totale** vers **26.1** — launcher,
> mods client, plugins serveur, et le serveur dev (fork « Rasengan »/Purpur).
> Le **serveur build est déjà migré** (Paper `26.1.2-74`) → il sert de référence.

## ⚡ AVANCEMENT EXÉCUTION — ✅ MIGRATION COMPLÈTE + PUBLIÉE EN PROD (2026-08-06)

> **La migration 1.21.1 → 26.1.2 est terminée et déployée.** Tout ci-dessous
> est en prod (launcher + manifest auto-update les joueurs). Il ne reste que
> des **features in-world mod-hud « phase-2 »** à peaufiner (voir plus bas).
> Branche mod-hud : `feature/mc-26.1-modhud-wip` (le reste sur `main`).

**✅ Serveurs / plugins (Java 25, Gradle 9.6.1, JDK 25 dans `D:\dev-cache\jdk25\jdk-25.0.4+7`)**
- plugin-guardian + plugin-ost → Paper `26.1.2.build.74-stable`, paperweight `2.0.0-beta.21`,
  run-paper `3.0.2`, api-version `26.1`.
- Plugins Shinobi (repo séparé `ShinobiReborn`) : Core/Abilities/Learning/Sense/Tail →
  purpur-api `26.1.2.build.2592-stable`, Java 25. Fixes : attributs `GENERIC_*`→sans préfixe,
  `Material.CHAIN`→`IRON_CHAIN`, `Sound.ENTITY_LEASH_KNOT_PLACE`→`ITEM_LEAD_TIED`,
  shade `3.6.2` + ASM `9.9.1` (bytecode Java 25 = class major 69).
- Serveur DEV = `91.197.6.60:25606` (maj récente ; avant `.51:26547`). Purpur 26.1.2.

**✅ Launcher (Tauri) — PUBLIÉ v0.3.24** (prod, isCurrent windows-x86_64/stable)
- `runtime.rs` JRE `java-runtime-epsilon` (Java 25), `game.rs` MC défaut `26.1.2`.
- **Purge STRICTE** (`manifest/download.rs::purge_orphan_mods`) : `mods/` == ensemble actif
  du manifest, rien d'autre (retire les vieux mods 1.21.1). + `mods.rs::constraint_accepts`
  gère le saut 1.21→26.1 (contraintes composées `>=1.21 <1.21.2` en AND).
- **Retry updater** (`use-updater.ts`) débloqué après coupure réseau.
- Env `_BUILD` bakées depuis `.env` (serveur prod `91.197.6.152:27106`, dev `91.197.6.60:25606`).
- Publish = `scripts/publish-launcher.ps1` (build NSIS + sign `secrets/tauri-updater.key` +
  release GitHub `v0.3.24` + POST `/v1/admin/releases`). Détails/pièges : voir mémoire
  `modpack-26.1-publish-state`.

**✅ Mods Reborn — PORTÉS + PUBLIÉS**
- reborn-integrity `0.3.0`, reborn-ost `0.2.0`, **reborn-hud `0.4.0`** (compile 0 err, boote,
  join monde OK). Recette build 26.1 = voir « RECETTE » ci-dessous.

**✅ Modpack + manifest — PUBLIÉ en prod, manifest courant `v2.2.0`** (mc 26.1.2)
- Release GitHub `mods-v2.0.0` (tous les jars ; ⚠️ upload >2min = release en DRAFT, faire
  `gh release edit --draft=false` sinon URLs 404). 17→18 mods (dropped indium/modernfix/FFP/
  modmenu/replaymod/mcef/continuity). Required : fabric-api 0.155.2+26.1.2, FLK 1.13.13,
  sodium 0.9.1, lithium 0.24.7, sodium-extra 0.9.3, entityculling, yacl 3.9.6, ETF 7.1,
  emotecraft 3.3.0, plasmovoice 2.1.13, **PlayerAnimationLib 1.2.5** (dép d'emotecraft),
  reborn-hud/ost/integrity. Optionnels : iris, nochatreports, zoomify, emf.
- POST manifest = `manifest-uploader manifest --file secrets/manifest-signed.json`
  (⚠️ le classifier gate le POST prod → confirm user requise). Version `@unique` côté API :
  re-POST même version = HTTP 500 (rollback transactionnel, pas cassant).

**🔧 RESTE = features in-world mod-hud « phase-2 »**

**✅ FAIT en 0.4.1 (session 2026-08-07, branche `feature/mc-26.1-modhud-wip`, à publier) :**
- **HUD qui bouge vraiment** : `InGameHudMixin` porté au mode extraction — inject HEAD/RETURN
  (push/pop `HudTransform`) sur les vraies cibles 26.1 : `extractItemHotbar`, `extractPlayerHealth`,
  `extractArmor` (static), `extractFood`, `extractAirBubbles`, `extractSelectedItemName` (action bar),
  `extractScoreboardSidebar`, `extractCrosshair`, `extractBossOverlay` (boss bar), `extractChat` (via
  `ChatHudMixin`). La pose se propage en retained. **Supprimés** : `InGameHudInvoker`, `BossBarHudMixin` (morts).
- **Chat RP custom** : `ChatHudMixin` sur `ChatComponent.extractRenderState` (7-args, descripteur explicite) —
  têtes joueurs, mentions, timestamps, panneau, blocage. Rendu sur la passe **FOREGROUND si chat ouvert,
  BACKGROUND si fermé (HUD)** (sinon invisible hors chat). `currentTick` = param vanilla (pas `getGuiTicks`).
  Scissor = clip largeur. **Animation d'arrivée** (slide+fade) + **Animated Typing** (curseur animé, 3 styles).
  Barre de saisie rétrécie via `ChatScreenMixin` re-ciblé sur `extractRenderState` (`render` n'existe plus).
- **Éditeur HUD refondu** : panneau latéral permanent sobre (carte insérée, coins carrés `FlatRect`,
  police **ArcadePix** échelle 0.5 comme main-menu, thème crimson `menu/Colors`), liste éléments+œil,
  presets nommables, scoreboard draggable (sous panneau), engrenage → réglages chat (restylés pareil).
- **Mouvement** : free-look permanent (caméra orbite), **marche/course suit la souris** (fix =
  `LocalPlayerBodyMixin` re-force yBodyRot en post-tick, sinon vanilla le tourne vers le déplacement),
  **Naruto run** = touche dédiée `L` (`NarutoRun` + payload C2S `reborn:naruto`) = Elden Ring.
- **Cinéma 3 modes** (K) : HUD → CLEAN → BARS. **Perf** : `DrawHelpers.roundedRect` réécrit en spans
  (~2r fills au lieu de 4r²) → fix lag 117→7 fps sur viseur/ESC. **Menus carrés** (rayon plafonné 2px)
  sauf ESC (variantes `*Full`).

**✅ FAIT en 0.4.6 (session 2026-08-08 nuit, `feature/mc-26.1-modhud-wip`, PUBLIÉ prod manifest v2.6.0) :**
- **MCEF → ABANDONNÉ.** Le fond menu 3D Chromium ne marche PAS en 26.1 : sous JDK 25 le binding
  jcef lève une exception sur le thread `AWT-EventQueue-0` juste après `createBrowser`, si bien que
  `onPaint` n'alimente jamais la texture GPU (browser transparent → panorama vanilla traversait).
  Prouvé via un probe HTML minimal (fond rouge) invisible même depuis un chemin ASCII propre. →
  Remplacé par un **fond dégradé sombre→crimson branded** (`DynamicPlayerBackground` réécrit).
  `mcef-modern` **retiré du modpack** + des deps.
- **Démarches — enfin RÉPARÉES + smooth.** Bug racine = registration via `REGISTER_ANIMATION_EVENT`
  + WeakHashMap récupérait un controller PAS celui réellement rendu. Migré au **pattern canonique
  doc PAL** : `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIO, avatar →
  new PlayerAnimationController(avatar, (c,d,s)→PlayState.STOP))` + récup `PlayerAnimationAccess
  .getPlayerAnimationLayer(player, LAYER)`. Détection mouvement par **delta de position** + hystérésis
  (getDeltaMovement oscillait). Loop **sans couture** : `then(anim, anim.loopType())` (respecte le
  point de bouclage authoré) au lieu de `thenLoop()` (reset-à-0) ; fondu `EASE_IN_OUT_SINE`. Visible
  en 3e personne uniquement. Rendu local (sync cross-joueurs `reborn:anim` toujours non câblé).
- **Menu/UI** : splash Elden-Ring (reste jusqu'à touche CLAVIER, souris ignorée) ; connect serveur
  minimaliste Zenkai (fond noir + logo + statut ArcadePix) ; VitalsHUD nom RP + niveau (tablist SOI).
- **OST** : 313 pistes Zenkai catégorisées + release `ost-v1`, ajoutées au manifest `required:true`
  (`reborn/ost/<cat>/<nom>.ogg`) → DL launcher au 1er lancement, scan reborn-ost, zéro code mod.
- **Audit trous migration 1.21.4→26.1 (corrigés)** : (A1) commandes `/rblock /runblock /rblocklist`
  réactivées — `ClientCommandManager`→**`ClientCommands`** en command-api-v2 3.0.5 + module explicite
  au compile ; (A2) `ServerInfoState.PROTOCOL_VERSION` 767→`SharedConstants.getProtocolVersion()` ;
  (A3) **tous les ~21 mixins vérifiés** appliqués (boot `defaultRequire:1` sans crash — durci) ;
  (A4) `RebornVersion` MC 1.21.1→26.1.2 / loader 0.16.5→0.19.3.

**⏳ À FAIRE (prochaines sessions) — features JAMAIS construites (pas des régressions) :**
- **Voix + émotes** : PlasmoVoice (bulle parole/mute) + Emotecraft. **Création perso** in-game (Zenkai).
  **Screenshot social** (gallery/éditeur/feed).
- **Tablist** : client OK ; la data vient de **ShinobiCore** (`TabListManager#pushClientFeed`, serveur).
- **Sync démarches cross-joueurs** : re-câbler le canal C2S/S2C `reborn:anim` + relais ShinobiCore
  (voir vieux `AnimSyncPayload`, absent de l'arbre 26.1).

⚠️ **Boucle test rapide** = `runClient` local (JDK25) + Monitor grep, PAS un republish à chaque fix.
Publish mod-hud = bump `gradle.properties`, build, upload jar → release `mods-v2.0.0`, régénère
manifest (`build-from-folder.ts` + `cli.ts sign`), POST. Recette complète : mémoire.

### 🍳 RECETTE build mod client 26.1 (PROUVÉE sur mod-integrity)
1. **build.gradle Groovy** (pas .kts) : `id 'net.fabricmc.fabric-loom' version '1.15-SNAPSHOT'`,
   **AUCUNE ligne `mappings(...)`** (client déobfusqué → noms officiels par défaut),
   `implementation "net.fabricmc:fabric-loader:0.19.3"`, fabric-api soit complet
   `implementation "net.fabricmc.fabric-api:fabric-api:0.155.2+26.1.2"` soit par module
   `implementation fabricApi.module("fabric-networking-api-v1", …)`. **Plus de `modImplementation`**
   (supprimé par le Loom déobf). `release/VERSION_25`.
2. **settings** : pluginManagement repos = Fabric maven + mavenCentral + gradlePluginPortal.
3. **gradle.properties** : minecraft `26.1.2`, loader `0.19.3`, fabric `0.155.2+26.1.2` (plus de yarn).
4. **fabric.mod.json** : `minecraft: ">=26.1 <26.2"`, `java: ">=25"`. **mixins.json** : `JAVA_25`.
5. **Build** : `JAVA_HOME=D:\dev-cache\jdk25\jdk-25.0.4+7  ./gradlew build -x test --no-daemon`.

### 🗺️ Renames Yarn → noms réels 26.1 (VÉRIFIÉS via javap sur le jar déobfusqué)
⚠️ Les vrais noms Mojang ≠ « mojmap » : **vérifier chaque classe** dans le jar
(`$CLAUDE_JOB_DIR/tmp/client2612.jar`, `javap -classpath …`). Confirmés :
`net.minecraft.util.Identifier`→**`net.minecraft.resources.Identifier`** (nom identique, package change) ·
`PacketByteBuf`→`net.minecraft.network.FriendlyByteBuf` · `PacketCodec`→`net.minecraft.network.codec.StreamCodec` ·
`CustomPayload`→`net.minecraft.network.protocol.common.custom.CustomPacketPayload` (+ nested **`.Id`→`.Type`**, `getId()`→`type()`) ·
`MinecraftClient`→`net.minecraft.client.Minecraft` · `TextRenderer`→`net.minecraft.client.gui.Font` ·
`KeyBinding`→`net.minecraft.client.KeyMapping` · `Screen`→`net.minecraft.client.gui.screens.Screen` ·
`TextFieldWidget`→`net.minecraft.client.gui.components.EditBox` (package `gui.widget`→`gui.components`) ·
`Text`→`net.minecraft.network.chat.Component`.
**❌ `DrawContext`/`GuiGraphics` : SUPPRIMÉ en 26.x** (refonte rendu → réécriture, pas un rename).
fabric-api : **`PayloadTypeRegistry.playC2S()`→`serverboundPlay()`** (`playS2C`→`clientboundPlay`).

### 🎨 Nouveau rendu GUI 26.x (mode « extraction / retained ») — recette de réécriture UI
La 26.x sépare **extraction de l'état de rendu** et rendu. Nouvelle classe =
**`net.minecraft.client.gui.GuiGraphicsExtractor`** (API impérative, proche de l'ancien
DrawContext). Mapping des `Screen`/widgets :
- **Override** : `render(DrawContext, mx, my, delta)` → **`extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta)`** ;
  fond via `extractBackground(GuiGraphicsExtractor, ...)` / `extractMenuBackground(...)`.
- **Primitives** (méthodes sur `g`) :
  `context.fill(x1,y1,x2,y2,argb)`→`g.fill(...)` (identique) ·
  `fillGradient`→`g.fillGradient(...)` (identique) ·
  `drawText(font,txt,x,y,color,shadow)`→**`g.text(font,txt,x,y,color,shadow)`** ·
  `drawCenteredText...`→**`g.centeredText(font,txt,x,y,color)`** ·
  `textWithWordWrap`, `outline`, `horizontalLine/verticalLine`, `enableScissor/disableScissor` (identiques) ·
  **texture** `drawTexture(id, x,y, u,v, w,h, texW,texH)`→**`g.blit(RenderPipelines.GUI_TEXTURED, id, x,y, u,v, w,h, texW,texH)`**
  (import `net.minecraft.client.renderer.RenderPipelines` ; équivalent direct de drawTexture,
  juste le pipeline en 1er arg — ⚠️ la variante `blit(id,x,y,w,h,u0,v0,u1,v1)` sans pipeline ne rend PAS) ·
  item : `g.item(ItemStack,x,y)`.
- **Matrices** : `context.getMatrices()` (3D `MatrixStack`) → **`g.pose()` = `org.joml.Matrix3x2fStack` (2D !)**
  (push/popMatrix, translate/scale 2D ; plus de Z via la pose).
- **Font** : `textRenderer.getWidth`→`font.width` ; `TextRenderer`→`Font`.
- ⚠️ **Rendu d'entité GUI** (`InventoryScreen.drawEntity`, modèle perso des écrans character) =
  refondu (render-state entités) → à revoir spécifiquement. L'astuce caméra 3e-pers-face du
  character-select (`Perspective.THIRD_PERSON_FRONT`) n'utilise PAS drawEntity → devrait survivre.
- **fabric-api HUD** : `HudRenderCallback` (rendering.v1) supprimé → nouveau
  `HudElementRegistry` / `HudLayerRegistrationCallback` (fabric-api 26.1, à câbler pour OstHudOverlay + PlayerPanel).
- **Mixins** ciblant les anciennes méthodes `render(...)` → recibler sur `extractRenderState`/les
  nouvelles méthodes (vérifier chaque descripteur dans le jar déobfusqué).

> **Ampleur** : mod-ost `OstScreen` = ~100 sites d'appel ; mod-hud = **bien plus** (tablist,
> character select/create, HUD panel, ~21 mixins). Faisable mécaniquement mais **exige une
> validation en jeu** (positions/couleurs/layout) → à faire en session avec le client lancé.

---

## 0. État des lieux constaté (SFTP + code, 2026-08-05)

### Serveurs
| Serveur | Jar actuel | MC | Plugins présents |
|---|---|---|---|
| **build** (7002) | `paper-26.1.2-74.jar` | **26.1.2** ✅ | Axiom, Chunky, EssentialsX, FAWE, HeadDatabase, Multiverse, MythicMobs, ShinobiCore/Abilities, ProtocolLib*, spark |
| **dev** (7012) | `Rasengan-1.21.1.jar` | 1.21.1 | ShinobiCore/Abilities/Learning/Sense/Tail, spark, bStats |

`*` ProtocolLib & ProtocolSupport présents en **stub 9 Ko** (Mystrator les réinjecte ; cf note historique).

### Le « fork » Rasengan — VÉRIFIÉ = Purpur stock
`Rasengan-1.21.1.jar` = **paperclip standard** ; le serveur embarqué sous
`META-INF/versions/1.21.1/purpur-1.21.1.jar` a été extrait et inspecté : composition
**100% Purpur 1.21.1 stock** (`net/minecraft`, `org/bukkit/craftbukkit`,
`io/papermc/paper`, `ca/spottedleaf/*`, `gg/pufferfish`, `org/purpurmc/purpur` [51
classes = standard], `alternate/current/wire`). **Zéro occurrence de « rasengan »**,
aucun package custom, `version.json` interne stock (protocol 767). Le build string dev
`1.21.1-DEV-3e77f66` = simple compil locale de la source Purpur.
**⇒ Décision : PAS de fork réel → on prend Purpur 26.1 officiel, rien à rebaser.**

### Modpack client (24 mods, 1.21.1 — via `secrets/manifest-unsigned.json`)
```
continuity 3.0.0+1.21          emotecraft 2.4.12              EMF 3.2.4-1.21
ETF 7.1                        entityculling 1.10.5          fabric-api 0.116.12+1.21.1
FLK 1.13.12                    FFP 1.3.0                     indium 1.0.35+mc1.21
iris 1.8.8+mc1.21.1            lithium 0.15.3+mc1.21.1       mcef 2.1.6-1.21.1  ⚠️fork
modernfix 5.25.1+mc1.21.1      modmenu 11.0.4  ⚠️tri         NoChatReports 2.9.1
plasmovoice 2.1.10            replaymod 2.6.23  ⚠️tri        sodium 0.6.13+mc1.21.1
sodium-extra 0.6.0            YACL 3.8.2                     zoomify 2.15.2
reborn-hud 0.2.44 ★           reborn-integrity 0.2.0 ★      reborn-ost 0.1.4 ★
```
★ = code custom Reborn (le gros morceau).

### Code custom dans ce repo (encore 1.21.1)
- Mods Fabric : `mod-hud`, `mod-ost`, `mod-integrity` — `minecraft_version=1.21.1`,
  `yarn 1.21.1+build.3`, `loader 0.16.5`, `fabric 0.102.1+1.21.1`.
- Plugins Paper : `plugin-guardian`, `plugin-ost` — `paperDevBundle 1.21.1`.
- Launcher : défaut `REBORN_MC_VERSION=1.21.1` (`game.rs:35`) ; manifest API porte
  `minecraftVersion` (signé Ed25519).
- Plugins Shinobi* : **repo séparé** `ShinobiReborn/V1` (Maven), non dans ce repo.

---

## Décisions à valider AVANT d'exécuter

1. ✅ **Moteur du serveur dev = Purpur 26.1 officiel** (drop-in du jar actuel ;
   Purpur ⊃ Paper → aucun plugin cassé ; configs/monde conservés).
2. ✅ **Fork Rasengan = Purpur stock** (vérifié) → rien à rebaser, on remplace le jar.
3. **Version cible exacte** : **26.1.2** (= build). On fige tout le modpack sur
   `26.1.2` (ou la dernière 26.1.x stable au moment de l'exécution). — _à confirmer_

---

## Phase 1 — Versions cibles 26.1 — ✅ FIGÉ (2026-08-05)

### Fondations
| Élément | Valeur 26.1.2 | Note |
|---|---|---|
| Minecraft | **26.1.2** | = build server |
| **Java** | **25** (`java-runtime-epsilon`) | ⚠️ saut depuis 21 : toolchains mods+plugins+serveur en 25 |
| Fabric Loader | **0.19.3** | |
| **Mappings** | **❌ plus de Yarn NI de mappings Mojang** | Depuis 26.x, **le client Mojang est livré DÉOBFUSQUÉ** (noms officiels dans le jar ; `version.json` n'a plus `client_mappings`). Loom 1.17 utilise ces noms par défaut → **aucune ligne `mappings(...)`** dans les build.gradle (cf `fabric-example-mod` branche `26.1.2`). |
| Fabric API | **0.155.2+26.1.2** | |
| Purpur | dernier build **26.1.2** | serveur dev |

> ⚠️ **Le code mod reste à remapper** : les noms Yarn utilisés dans la source
> (`MinecraftClient`, `DrawContext`, `Text`, `Identifier`, `PlayerEntity`, `World`,
> `PacketByteBuf`, `PacketCodec`, `CustomPayload`…) deviennent les **noms officiels
> Mojang** (`Minecraft`, `GuiGraphics`, `Component`, `ResourceLocation`, `Player`,
> `Level`, `FriendlyByteBuf`, `StreamCodec`, `CustomPacketPayload`…) — qui sont
> désormais **directement dans le jar déobfusqué**. Remap de source, pas un simple bump.
> Côté Loom : bump `1.9-SNAPSHOT`→`1.17.17`, **retirer la ligne `mappings(...)`**.

### Outillage build à bumper
- Gradle wrapper 8.12.1 → **9.x** (support Java 25 toolchain).
- fabric-loom → dernière (26.1 + mojmap + Java 25).
- paperweight-userdev 2.0.0-beta.14 → dernière ; run-paper idem.
- JDK 25 local requis (installé portable dans `D:\dev-cache`).

## Phase 2 — Serveurs → 26.1
- [ ] **dev** : remplacer `Rasengan-1.21.1.jar` par **Purpur 26.1.2** (ou Paper).
      Régénérer/adapter les configs (`purpur.yml`, `paper-world-defaults`,
      `bukkit/spigot.yml`) — nouveaux champs 26.1.
- [ ] Backup monde + configs avant bascule (SFTP).
- [ ] `server.properties` : garder `online-mode=true`, port dev `26547`.

## Phase 3 — Plugins tiers (dev en a peu ; réf = build 26.1)
Versions **confirmées 26.1** sur le build : Axiom `5.0.4-MC26.1`, Chunky `1.4.40`,
EssentialsX `2.22.0`, FAWE `2.15.2`, HeadDatabase `4.19.8`, Multiverse `5.5.3`,
ProtocolLib `5.3.0`.
- [ ] Aligner le dev sur ces versions **si** on y ajoute des plugins tiers.
- [ ] **Tri** (cf [[mc-version-migration]]) : ne PAS remettre ProtocolSupport.
- [ ] LuckPerms/PAPI/Nexo/SkinsRestorer/PlasmoVoice(serveur) : récupérer les builds
      26.1 **au moment** où on les redéploie (pas présents sur dev/build actuels).

## Phase 4 — Plugins Reborn (repo `ShinobiReborn` + ce repo)
Constat : `ShinobiAbilities`/`ShinobiCore` **tournent déjà sur le build 26.1** (jars
compilés 1.21.1 qui chargent) → bon signe (peu/pas de NMS cassant).
- [ ] `ShinobiCore/Abilities/Learning/Sense/Tail` : bump API Paper/Purpur `26.1`
      dans le `pom.xml`, `mvn clean package`, fixer déprécié/NMS le cas échéant.
- [ ] Ce repo — `plugin-guardian` & `plugin-ost` : `paperDevBundle("26.1-R0.1-SNAPSHOT")`,
      `runServer.minecraftVersion("26.1.x")`, corriger le commentaire faux (guardian
      dit « 1.21.4 » mais bundle 1.21.1). Rebuild + test kick/attestation.
- [ ] Vérifier canaux plugin-message (`reborn:auth`, `reborn:ost`, `reborn:tablist`,
      `reborn:character`, `reborn:anim`, `reborn:run`) : API messaging inchangée 26.1.

## Phase 5 — Mods client Reborn = LE gros morceau
`mod-hud`, `mod-ost`, `mod-integrity` : passage aux **mappings Yarn 26.1**.
- [ ] `gradle.properties` (×3) : `minecraft_version`, `yarn_mappings`, `loader_version`,
      `fabric_version` → 26.1.
- [ ] Recompiler → **corriger chaque mixin / appel d'API cassé** (renommages Yarn,
      signatures changées). Zones à risque connues :
  - `mod-hud` : `InventoryScreen.drawEntity` (signature перso creation/tablist),
    `HudRenderCallback`, mixins `reborn-hud.mixins.json` (~21), `Screen`/`ctx.*`
    (fillGradient, drawTexture), `RebornVoiceAddon` (réflexion PlasmoVoice → check
    API PV 26.1), keybinds, `TextRenderer`.
  - `mod-ost` : décodage Ogg (STBVorbis) + OpenAL (indépendant de MC, peu de risque),
    `sounds.json`/event ids, `PositionedSoundInstance`.
  - `mod-integrity` : `ClientPlayConnectionEvents.JOIN`, `ClientPlayNetworking`,
    codec payload `reborn:auth`.
- [ ] `fabric.mod.json` (×3) : `depends.minecraft` → `>=26.1`, `fabricloader`,
      `fabric-language-kotlin` si utilisé.
- [ ] Build `./gradlew build -x test` (les tests plantent en env accentué — connu).

## Phase 6 — Modpack tiers (client) → 26.1 + tri — ✅ versions figées
| Mod | 1.21.1 actuel | 26.1.2 cible | Action |
|---|---|---|---|
| fabric-api | 0.116.12 | **0.155.2+26.1.2** | bump |
| fabric-language-kotlin | 1.13.12 | **1.13.13+kotlin.2.4.10** | bump |
| sodium | 0.6.13 | **mc26.1.2-0.9.2-alpha.3** | bump (⚠️ alpha seule dispo) |
| lithium | 0.15.3 | **mc26.1.2-0.24.7** | bump |
| sodium-extra | 0.6.0 | **mc26.1.2-0.9.3** | bump |
| iris | 1.8.8 | **1.11.3+26.1** | bump (shaders OK) |
| entityculling | 1.10.5 | **1.10.5** (multi-ver) | re-DL jar 26.1 |
| continuity | 3.0.0 | **3.0.1-beta.2+26.1** | bump (vérifier FRAPI sans Indium) |
| EMF | 3.2.4 | **3.2.4-fabric-26.1** | bump |
| ETF | 7.1 | **7.1-fabric-26.1** | bump |
| emotecraft | 2.4.12 | **3.3.0-b.build.160** | bump |
| plasmovoice | 2.1.10 | **fabric-26.1-2.1.13** | bump (aligner API mod-hud voice) |
| **mcef** | 2.1.6 officiel | **mcef-modern 0.3.3+mc26.1** | **swap fork** (⚠️ API à vérifier) |
| zoomify | 2.15.2 | **2.16.1+26.1** | bump |
| YACL | 3.8.2 | **3.9.6+26.1** | bump |
| NoChatReports | 2.9.1 | **Fabric-26.1-v2.19.0** | bump |
| ~~indium~~ | 1.0.35 | — | **RETIRER** (pas de 26.1 ; Sodium 0.9 gère FRAPI) |
| ~~modernfix~~ | 5.25.1 | — | **RETIRER** (pas de 26.1) |
| ~~FFP (Fresh First Person)~~ | 1.3.0 | — | **RETIRER** (pas de 26.1 ; gap immersion vue 1re pers) |
| ~~modmenu~~ | 11.0.4 | — | **RETIRER** (tri : expose la liste des mods) |
| ~~replaymod~~ | 2.6.23 | — | **RETIRER** (tri : niche) |

Nouveau modpack = **16 tiers + 3 Reborn = 19 jars** (vs 24). Retraits : indium,
modernfix, FFP, modmenu, replaymod.

## Phase 7 — Launcher + API manifest
- [ ] `REBORN_MC_VERSION=26.1.x` (`.env`) — `game.rs:35` lit déjà l'env.
- [ ] Re-tester le flow launch : natives (chaque native = entrée lib séparée depuis
      1.19+), `fabric.rs` (meta loader 26.1), assets index, quickPlay
      `--quickPlayMultiplayer host:port`, purge mods incompatibles (`mods.rs`).
- [ ] Diagnostics : mettre à jour les tests avec version 26.1 (`diagnostics.rs`).
- [ ] API : régénérer le **manifest** signé avec `minecraftVersion=26.1.x` +
      nouveaux jars du modpack (`packages/manifest-signer`), publier
      `mods-vX` (24→22 jars après tri) + `POST /v1/admin/... manifest`.
- [ ] `verify.rs` fixtures : bump `1.21.4`→26.1 dans les tests.

## Phase 8 — Tests E2E + bascule
- [ ] Serveur dev 26.1 up + plugins Reborn OK (attestation Guardian, tablist,
      character select, OST, anims/voix).
- [ ] Launcher : DL modpack 26.1 → join dev → HUD/menu perso/OST/voix.
- [ ] Compat shaders (Iris 26.1) + MCEF fork (fond menu Chromium).
- [ ] Une fois validé sur dev → répliquer le modpack en prod, republier launcher.

---

## Ordre recommandé d'exécution
1. Décisions ci-dessus (Purpur vs Paper ; source fork ; cible 26.1.2).
2. Phase 1 (figer versions) — **débloque tout le reste**.
3. Serveur dev → Purpur/Paper 26.1 (Phase 2) + plugins Reborn (Phase 4) → un serveur
   qui démarre en 26.1, testable sans client custom.
4. Mods client Reborn (Phase 5) — le vrai chantier code — en parallèle possible.
5. Modpack tiers + MCEF fork + tri (Phase 6).
6. Launcher + manifest (Phase 7).
7. E2E (Phase 8).

Liens mémoire : [[mc-version-migration]] (politique + tri), [[character-creation-design]]
(Phase 1 bouclée), [[wip-launcher-mod-fixes]] (recettes publish mod/launcher).
