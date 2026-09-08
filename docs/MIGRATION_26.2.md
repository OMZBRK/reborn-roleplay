# Migration 26.1.2 → 26.2 — version courante

> **C'est la version en production.** Toute recette de build antérieure est caduque.
> La migration précédente (1.21.1 → 26.1.2, le gros morceau : Java 25, fin de Yarn,
> refonte du rendu GUI) est archivée dans [`MIGRATION_26.1.md`](./MIGRATION_26.1.md) —
> à lire pour le *pourquoi*, pas pour appliquer ses commandes.
>
> Exécutée le **2026-08-19** (`dc5f52e`), correctif de boot le même jour (`5a78751`).

## En une phrase

**26.1 → 26.2 est une version mineure** : ~8 renommages d'API côté client, **zéro
changement côté serveur**. Les 6 plugins Shinobi ont recompilé sans qu'une seule ligne
soit modifiée.

---

## 1. Versions figées

| Élément | 26.1.2 | **26.2** |
|---|---|---|
| Minecraft | 26.1.2 | **26.2** |
| Java | 25 | 25 (inchangé) |
| Fabric Loader | 0.19.3 | 0.19.3 (inchangé) |
| Fabric API | 0.155.2+26.1.2 | **0.156.0+26.2** |
| `fabric.mod.json` → `depends.minecraft` | `>=26.1 <26.2` | **`>=26.2 <26.3`** |
| Purpur (serveur dev/prod) | 26.1.2 | **26.2.build.2620-stable** |
| Paper dev-bundle (plugins) | 26.1.2 | **26.2.build.112-stable** |
| PlayerAnimationLib | 1.2.5 | **`PlayerAnimationLibMerged` 1.2.6+mc.26.2** |
| EmoteCraft | 3.3.0-b.160 | **3.4.0-b.build.165** |
| PlasmoVoice | 26.1-2.1.13 | **26.2-2.1.14** |
| Manifest joueur | v2.x | **v3.x** |
| Launcher (défaut `REBORN_MC_VERSION`) | 26.1.2 | **26.2** |

Mappings : toujours **aucune** (`mappings(...)` absent des `build.gradle`) — le client
Mojang 26.x est livré déobfusqué.

---

## 2. Deltas d'API côté client — la liste complète

| 26.1 | 26.2 |
|---|---|
| `minecraft.setScreen(s)` | **`setScreenAndShow(s)`** |
| `minecraft.screen` | **`minecraft.gui.screen()`** |
| `options.hideGui` (lecture) | **`hud.isHidden()`** |
| `options.hideGui` (écriture) | via un **`@Accessor` `HudAccessor`** (plus de champ public) |
| `gui.getChat()` / `gui.getGuiTicks()` | **`hud.getChat()` / `hud.getGuiTicks()`** |
| `getMainCamera()` | **`mainCamera`** |
| `getMainRenderTarget()` | **`gameRenderer.mainRenderTarget`** |
| `ExperienceBarRenderer` | **`ExperienceBar`** |

### ⚠️ Le piège qui a coûté un crash au boot

**26.2 a scindé `Gui` en `Gui` + `Hud`** (`net.minecraft.client.gui.Hud`, accessible
via `minecraft.gui.hud`). Tout le rendu HUD in-game a migré :

`extractRenderState`, `extractCrosshair`, `extractItemHotbar`, `extractPlayerHealth`,
`extractArmor`, `extractFood`, `extractAirBubbles`, `extractSelectedItemName`,
`extractScoreboardSidebar`, `extractBossOverlay`, et le champ `chat`.

Des mixins qui ciblent encore `Gui.class` échouent au `@Shadow` → `MixinApplyError` →
crash **« Initializing game »** sans stack utile. Cibler `Hud.class`.

Autre suppression : `ScreenshotRecorder.saveScreenshot(File, Framebuffer, Consumer)`
→ **`grab(File, RenderTarget, Consumer)`**.

> **Contrôle systématique après un bump de version** : vérifier que **les 28 mixins de
> `reborn-hud` résolvent** contre le jar cible avant de publier. Un mixin non résolu ne
> se voit pas au build, seulement au lancement.

---

## 3. Côté serveur — rien à faire

- `minecraft/shinobi/` (Core, Abilities, Combat, Learning, Sense, Tail) : bump
  `purpur-api` → `26.2.build.2620-stable`, `mvn clean package`. **Zéro ligne modifiée.**
- `plugin-guardian` / `plugin-ost` : dev-bundle Paper `26.2.build.112-stable`.
  Binairement compatibles.
- Canaux de plugin messaging (`reborn:auth`, `reborn:ost`, `reborn:tablist`,
  `reborn:character`, `reborn:anim`, `reborn:emote`, `reborn:emotepack`,
  `reborn:inventory`, `reborn:tirage`, `reborn:naruto`) : API inchangée.

---

## 4. Nettoyage effectué au passage

7 jars morts retirés de `minecraft/mod-hud/libs/` : ancien PlayerAnimationLib
(`player-animation-lib-fabric-2.0.1+1.21.1`), `emotecraft-2.4.12`,
`emotecraft-for-MC26.1.2-3.3.0`, ancien PlasmoVoice, **`mcef-modern-0.3.3`**
(abandonné — cf. [ADR 0004](./adr/0004-abandon-mcef-fond-menu-3d.md)),
`bendy-lib-fabric-5.1`, et divers vestiges 1.21.1.

---

## 5. Recette de build (26.2)

Inchangée depuis 26.1 sauf les numéros :

1. **`build.gradle` en Groovy** (pas `.kts`) : `id 'net.fabricmc.fabric-loom' version '1.15-SNAPSHOT'`,
   **aucune ligne `mappings(...)`**, `implementation "net.fabricmc:fabric-loader:0.19.3"`,
   `implementation "net.fabricmc.fabric-api:fabric-api:0.156.0+26.2"`.
   Plus de `modImplementation`. `release/VERSION_25`.
2. **`gradle.properties`** : `minecraft_version=26.2`, `loader_version=0.19.3`,
   `fabric_version=0.156.0+26.2`.
3. **`fabric.mod.json`** : `"minecraft": ">=26.2 <26.3"`, `"java": ">=25"`.
   **`mixins.json`** : `"compatibilityLevel": "JAVA_25"`.
4. **Build** :
   ```pwsh
   $env:JAVA_HOME = "D:\dev-cache\jdk25\jdk-25.0.4+7"
   ./gradlew build -x test --no-daemon
   ```
   (les tests plantent en environnement à chemin accentué — connu, sans impact)
5. **Boucle de test rapide** : `runClient` local, **pas** un republish à chaque fix.

---

## 6. Avant de publier

Ne pas court-circuiter [`PUBLISH_PREFLIGHT.md`](./PUBLISH_PREFLIGHT.md) :
concordance des versions, **sanity du poids du jar** (`reborn-hud` ≈ 20 Mo — un jar qui
double contient un asset intrus), sha256 + taille, base = dernier manifest signé
(**une seule entrée `files[]` modifiée**), signature, `gh release create`, vérification
HTTP 200 + sha256 identique, puis POST via `manifest-uploader`.

---

## 7. Pour la prochaine version (26.3+) — check-list

1. Bump `gradle.properties` ×3 + `fabric.mod.json` ×3.
2. Compiler et lister les erreurs de renommage — 26.x bouge par petites touches.
3. **Vérifier que tous les mixins résolvent** (le piège n°1, cf §2).
4. Bumper les libs vendorisées de `mod-hud/libs/` : PAL, EmoteCraft, PlasmoVoice —
   elles sortent souvent après le jour J de la version MC.
5. Serveur : bumper `purpur-api` et le dev-bundle Paper, recompiler, vérifier
   qu'aucun plugin ne casse à l'`onEnable`.
6. Regénérer et republier le manifest ; vérifier le `minecraftVersion` signé.
7. Bumper le défaut `REBORN_MC_VERSION` dans `game.rs`.
8. Tester le flow de lancement complet : natives, assets, Fabric meta, quickPlay,
   purge des mods incompatibles.
