# Plan de migration Reborn Roleplay → Minecraft 26.1

> Rédigé le 2026-08-05. Objectif : transition **totale** vers **26.1** — launcher,
> mods client, plugins serveur, et le serveur dev (fork « Rasengan »/Purpur).
> Le **serveur build est déjà migré** (Paper `26.1.2-74`) → il sert de référence.

## ⚡ AVANCEMENT EXÉCUTION (2026-08-05)

**✅ FAIT & buildé (Java 25, Gradle 9.6.1, JDK 25 portable dans `D:\dev-cache\jdk25`) :**
- **plugin-guardian** + **plugin-ost** → Paper dev-bundle `26.1.2.build.74-stable`,
  paperweight `2.0.0-beta.21`, run-paper `3.0.2`, api-version `26.1`. Jars produits.
- **Plugins Shinobi (repo séparé)** : ShinobiCore, ShinobiAbilities, ShinobiLearning,
  ShinobiSense, ShinobiTail → **tous buildés** contre **purpur-api `26.1.2.build.2592-stable`**,
  Java 25. Fixes API 26.1 appliqués : attributs `GENERIC_*`→sans préfixe (MAX_HEALTH,
  MOVEMENT_SPEED, STEP_HEIGHT, JUMP_STRENGTH, GRAVITY, SCALE…), `Material.CHAIN`→`IRON_CHAIN`,
  `Sound.ENTITY_LEASH_KNOT_PLACE`→`ITEM_LEAD_TIED`, shade-plugin `3.6.0`→`3.6.2` + ASM `9.9.1`
  (bytecode Java 25 = class major 69), repo Purpur ajouté aux 4 poms dépendants.
- **Launcher** : `runtime.rs` JRE `java-runtime-delta`→**`java-runtime-epsilon`** (Java 25,
  bug critique sinon le jeu plante), `game.rs` défaut MC `1.21.1`→`26.1.2`, fixtures
  verify.rs/jvm.rs. **`cargo check` OK**.
- **Serveur DEV** : Purpur 26.1.2 (build 2592) + 5 jars Shinobi migrés **stagés** dans
  `_26.1-migration/` (SFTP), configs backupées, `CUTOVER.md` écrit. **Non-destructif**
  (serveur 1.21.1 en cours intact) → bascule = geste manuel admin (Java 25 panel + snapshot monde).

**✅ mod-integrity BUILDÉ (reborn-integrity 0.3.0)** — réseau uniquement, aucun UI. La
recette de build 26.1 est **prouvée** (voir « RECETTE » ci-dessous).

**⛔ mod-ost + mod-hud = chantier de RÉÉCRITURE UI (pas un simple remap) :**
- **26.x a refondu tout le système de rendu GUI** : `DrawContext`/`GuiGraphics` **n'existe plus**
  (remplacé par un nouveau pipeline de rendu). Tout code UI (`OstScreen`/`OstHudOverlay` de mod-ost,
  et **la totalité de mod-hud** : tablist, character screens, HUD panel, ~21 mixins) doit être
  **réécrit** contre la nouvelle API 26.x, + **validé en jeu** (impossible en headless).
- mod-hud en plus : swap **MCEF → `mcef-modern 0.3.3+mc26.1`** (API à adapter), addon PlasmoVoice `2.1.13`.
- ⇒ **Modpack 26.1 + republication launcher/manifest = EN ATTENTE** des 3 mods (ne pas pousser en prod).

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
