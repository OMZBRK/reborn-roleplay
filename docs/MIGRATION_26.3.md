# Migration 26.2 → 26.3 — PRÊTE, EN ATTENTE DE DEPS

> **État au 2026-09-25 :** tout le code game-side est porté et **compile** contre 26.3
> (branche `feature/migrate-26.3`). La publication prod est **volontairement différée** :
> 3 mods du modpack n'ont pas encore de build 26.3, dont **Bendable Cuboids** (requis
> pour le bend des emotes), et **Sodium n'est qu'en alpha**. On bascule dès que ces deps
> se stabilisent. Voir §5.
>
> Suite logique de la check-list §7 de [`MIGRATION_26.2.md`](./MIGRATION_26.2.md).

## En une phrase

**26.2 → 26.3, côté serveur = zéro ligne** (les 6 Shinobi + guardian + ost recompilent
tels quels) ; **côté client = un vrai port d'API** (~8 deltas, du même ordre que 26.2) +
un piège d'outillage (LWJGL/GLFW plus au classpath de compile).

---

## 1. Versions figées

| Élément | 26.2 | **26.3** |
|---|---|---|
| Minecraft | 26.2 | **26.3** |
| Java | 25 | 25 (inchangé) |
| Fabric Loader | 0.19.3 | 0.19.3 (inchangé) |
| Fabric API | 0.156.0+26.2 | **0.161.0+26.3** |
| Fabric Loom (build mods) | 1.15-SNAPSHOT | **1.15-SNAPSHOT inchangé** (⚠️ voir §3) |
| `fabric.mod.json` → `depends.minecraft` | `>=26.2 <26.3` | **`>=26.3 <26.4`** |
| Purpur (Shinobi ×6) | 26.2.build.2620-**stable** | **26.3.build.2641-experimental** (pas de -stable) |
| Paper dev-bundle (guardian/ost) | 26.2.build.112-stable | **26.3.build.41-alpha** (pas de -stable) |
| PlayerAnimationLibMerged | 1.2.6+mc.26.2 | **1.2.7+mc.26.3** |
| EmoteCraft | 3.4.0-b.build.165 | **3.5.0-a.build.169** (alpha) |
| PlasmoVoice | 26.2-2.1.16 | **26.3-2.1.17** |
| Manifest joueur | v3.1.91 (mcVer 26.2) | v3.2.x (mcVer **26.3**) — *pas encore publié* |
| reborn-hud / reborn-ost / reborn-integrity | 0.4.135 / 0.2.2 / 0.3.1 | **0.4.136 / 0.2.3 / 0.3.2** |

Mappings : toujours **aucune** (client Mojang 26.x déobfusqué).

---

## 2. Deltas d'API côté client — la liste complète (mod-hud)

~8 renommages/déplacements. Tous appliqués sur la branche.

| 26.2 | 26.3 | Sites |
|---|---|---|
| `Util.getPlatform().openUri(uri)` | **`com.mojang.blaze3d.Blaze3D.openUri(uri)`** | 7 (RebornBranding, DiscordTab, GameMenuScreenMixin, GalleryScreen ×2, ScreenshotDetailScreen) |
| `InputConstants.isKeyDown(win, k)` | **`InputConstants.isKeyDown(k)`** (plus de handle fenêtre) | PhotoMode, RepositionScreen, HudEditScreen |
| `InputConstants.Type.KEYSYM` | **`InputConstants.Type.KEYBOARD`** | HudKeybinds, OstKeybinds |
| `KeyEvent.scancode()` | **`KeyEvent.keycode()`** (record renommé) | 6 handlers `keyPressed` (local mort → swap simple) |
| `PoseStack.mulPose(Quaternionf)` | **`PoseStack.rotate(Quaternionfc)`** | CosmeticFeatureRenderer ×3 |
| `new OptionsScreen(p, opts, false)` | **`new OptionsScreen(p, opts)`** (3e arg retiré) | MinecraftTab |
| `Window.isFullscreen()` / `toggleFullScreen()` | **`mc.options.fullscreen()` (`OptionInstance<Boolean>`)** en lecture ; **`Window.setFullscreen(bool)`** pour appliquer | VideoTab |
| `LivingEntity.swinging` (champ) | **`LivingEntity.isSwinging()`** (méthode) | CombatInput |

`OptionsScreen` a aussi **déménagé** de `net.minecraft.client.gui.screens` vers
`net.minecraft.client.gui.screens.options` (l'import était déjà correct dans le code).

### ⚠️ Le piège n°1 de cette version : LWJGL/GLFW hors classpath de COMPILE

En 26.3, le dependency `minecraft` **n'expose plus `org.lwjgl.glfw` au classpath de
compilation** (il reste fourni au runtime par le jeu). Symptôme : ~130 erreurs en cascade
`package org.lwjgl.glfw does not exist` + `cannot find symbol` sur les `GLFW.GLFW_KEY_*`.

**Fix** (mod-hud ET mod-ost) : ajouter dans `build.gradle`
```groovy
compileOnly "org.lwjgl:lwjgl-glfw:3.4.1"   // version alignée sur le version.json 26.3
```
Ne PAS bumper Loom pour ça : Loom 1.18.2 exige Gradle 9.7.0 (le wrapper est en 9.6.1) →
`No matching variant`. Le `compileOnly` règle le problème sans toucher au toolchain.

> **Contrôle systématique** (inchangé depuis 26.2) : vérifier que **les 28 mixins de
> `reborn-hud` résolvent au LANCEMENT** (`runClient`), pas seulement au build. Un mixin non
> résolu ne se voit qu'au démarrage.

---

## 3. Côté serveur — rien à faire (sauf les numéros)

- `minecraft/shinobi/` (×6) : `purpur-api` → **`26.3.build.2641-experimental`**
  (⚠️ `-experimental`, pas `-stable` : la ligne 26.3 n'a pas encore d'API stable).
  `mvn clean package`. **Zéro ligne modifiée.**
- `plugin-guardian` / `plugin-ost` : `paperDevBundle("26.3.build.41-alpha")` +
  `minecraftVersion("26.3")`. Binairement compatibles, **zéro ligne**.
  ⚠️ Le premier setup paperweight 26.3 est lent (~2 min : extractFromBundler → runCodebook
  → setupMacheSources). Ne PAS lancer les deux plugins en parallèle (lock partagé du cache
  paperweight → `mappedServerJar.jar utilisé par un autre processus`). Séquentiel.

---

## 4. mod-ost

Mêmes 2 corrections que mod-hud : `compileOnly lwjgl-glfw:3.4.1` + `KEYSYM → KEYBOARD`
(OstKeybinds). Jar ≈ 73,5 Mo (les 316 pistes OGG, normal).

---

## 5. POURQUOI C'EST DIFFÉRÉ — les deps qui bloquent la prod

Audit Modrinth du modpack (24 mods) le 2026-09-25. **15/18 mods tiers ont un 26.3.**
Bloquants :

| Mod | Rôle | État 26.3 |
|---|---|---|
| **Bendable Cuboids** | **bend des emotes** | ❌ pas de 26.3 (top 26.2) — la feature emote perd la flexion sans lui |
| First-person Model | voir son corps en vue 1 | ❌ pas de 26.3 (top 26.2) |
| NoChatReports | cosmétique anti-report | ❌ pas de 26.3 (top 26.2) |
| **Sodium** | rendu (Iris + Sodium Extra en dépendent) | ⚠️ **alpha uniquement** (`0.9.3-alpha.1+mc26.3`) |

Serveur et client **doivent** être sur la même version MC → la bascule est tout-ou-rien.
Décision (2026-09-25) : **on attend** que Bendable Cuboids sorte en 26.3 (retour du bend)
et idéalement Sodium stable, plutôt que d'imposer à tous les joueurs une pile alpha +
emotes dégradées.

### Table des builds 26.3 déjà repérés (pour flipper vite le jour J)

Mods à jour côté 26.3 (Modrinth CDN) — au moment de publier, revérifier les numéros :

| Mod | Version 26.3 |
|---|---|
| Distant Horizons | `3.3.2-26.3` (jar fabric-neoforge combiné) |
| Entity Model Features | `3.3.8-26.3-fabric` |
| Entity Texture Features | `7.2.4-26.3-fabric` |
| EntityCulling | `1.11.2-mc26.3` |
| Fabric API | `0.161.0+26.3` |
| Fabric Language Kotlin | `1.14.1+kotlin.2.4.20` |
| Iris | `1.11.6+mc26.3` |
| Lithium | `0.26.1+mc26.3` |
| Sodium Extra | `0.9.4+mc26.3` |
| Sodium | `0.9.3-alpha.1+mc26.3` ⚠️ alpha |
| YACL | `3.9.7+26.3-fabric` |
| Zoomify | `2.16.3+26.3` |
| 3D Skin Layers (slug `3dskinlayers`) | `1.11.3-mc26.3` |
| Axiom | `6.1.3-for-MC26.3` |
| FerriteCore (slug `ferrite-core`) | `9.0.0-fabric` |
| EmoteCraft | `3.5.0-a.build.169` (alpha) |
| PlasmoVoice | `26.3-2.1.17` |
| PlayerAnimationLibMerged | `1.2.7+mc.26.3` |

Les 3 libs bundlées (PAL, EmoteCraft, PlasmoVoice) 26.3 sont **déjà téléchargées** dans
`minecraft/mod-hud/libs/` et référencées par `build.gradle`.

---

## 6. Bascule quand les deps sont prêtes — check-list

1. Vérifier Bendable Cuboids 26.3 dispo (+ Sodium stable si possible). Sourcer leurs jars.
2. `git checkout feature/migrate-26.3`, rebase sur `main` si besoin.
3. Rebuild les 3 mods reborn (0.4.136 / 0.2.3 / 0.3.2 — déjà bumpés). `runClient` :
   **valider visuellement** menu, HUD, chat, emotes, fullscreen, keybinds → vérifier les
   28 mixins.
4. Manifest : base = dernier signé (`secrets/manifest-signed-v3.1.91.json`), passer
   `minecraftVersion` → **26.3**, remplacer **toutes** les entrées `mods/*` par les jars
   26.3 (table §5) + les 3 reborn rebuild + les 3 libs. Retirer les mods sans 26.3 (ou les
   remettre s'ils sont sortis). Uploader les jars non-CDN sur une release `mods-vX`. Signer,
   `gh release create`, vérifier sha256 + HTTP 200, POST via `manifest-uploader`.
5. Plugins : SFTP des 6 Shinobi + guardian + ost sur le serveur (backup `.bak`, vérif
   `st_size`). **Restart Mystrator** obligatoire. Le serveur doit tourner sur Purpur 26.3.
6. Launcher : le défaut `REBORN_MC_VERSION` est déjà à `26.3` (game.rs + mod.rs). Bump
   version, build Tauri avec les 9 vars `_BUILD`, signer, release + `manifest-uploader
   release` (canal stable) → auto-update tous les joueurs. **Sans ce release, les joueurs
   lancent encore en 26.2** (le défaut est compilé, pas lu de l'env chez eux).
7. Bump `REBORN_MC_VERSION` du `.env` de dev/serveur si utilisé.
