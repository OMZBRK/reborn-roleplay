# Reborn HUD (mod client Fabric)

Mod **client-side** qui regroupe **toute la couche UI custom Reborn**
côté jeu : menu principal, ESC menu, sub-screens (Options / Lore / Règles),
ConnectScreen, et HUD en jeu (chat, scoreboard, bossbar, action bar,
hotbar, etc.) avec éditeur visuel drag/resize/hide.

> **État actuel — refactor en cours.** Une partie de l'UI (menu vanilla
> + ESC + ConnectScreen + sub-screens) vit encore dans `mod-integrity/`
> historiquement. Cible : tout ce qui touche au **rendu UI** vit ici,
> `mod-integrity/` se limite à l'attestation play-token. Le move
> physique sera fait en session dédiée pour pas mélanger avec les WIPs
> en cours.

## Portée

### En jeu (HUD)
- **Éditeur visuel** ouvert par touche dédiée (par défaut **H**) :
  drag, resize, hide, snapping/alignment guides, historique undo/redo,
  presets sauvegardés.
- **Repositionnement libre** des éléments vanilla : chat, scoreboard,
  bossbar, action bar, hotbar, health/hunger/armor/air bars, experience
  bar.
- **Chat custom Reborn** : onglets (`ChatTab`), classifier de messages,
  détecteur de mentions, timestamps, dropdown quick-commands, écran de
  settings dédié (`ChatSettingsScreen`).
- Persistance dans `config/reborn-hud.json` (positions, presets,
  préférences chat).

### Hors jeu (menu et écrans Reborn — cible post-migration)
- **TitleScreen** : custom logo REBORN, masque du logo vanilla,
  background procédural (gradient + chakra particles + petals) ou
  `DynamicPlayerBackground` (scène 3D du joueur), boutons Reborn
  empilés, `PressSpacePrompt`, server info card coin haut-gauche,
  lecteur OST coin haut-droite, credits corner, masque des entrées
  vanilla non pertinentes (Skin / RP / Realms).
- **ConnectScreen** custom (`ConnectingRenderer`).
- **GameMenuScreen** (ESC) refondu en 4 panels + community bar.
- **OptionsScreen** redirigé vers `RebornOptionsScreen` (5 onglets :
  Audio / Video / Controls / Account / Discord) avec persistance dans
  `RebornPrefs`.
- **RulesLoreScreen** (règlement et lore in-game).

### Lecteur OST côté menu
Le mod HUD héberge l'**UI** du lecteur OST in-menu (`OSTPlayerV2`,
`OSTVolumePopup`, `OSTPlaylistOverlay`). La **logique audio** et le
**décodage Ogg Vorbis** restent dans `mod-ost/` (séparation de
responsabilité).

## Build

JDK 21 requis (Fabric Loom). Si tu as plusieurs JDK installés,
exporte `JAVA_HOME` avant :

```pwsh
$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk21.0.9_10"
./gradlew build       # → build/libs/reborn-hud-<ver>.jar
./gradlew runClient   # client MC 1.21.1 avec le mod
./gradlew test
```

## Architecture

```
fr.reborn.hud
├── RebornHudClient            ← entrée mod
├── config/                    ← persistance JSON (HudConfig, snapshot,
│                                 history, presets)
├── element/                   ← registre des éléments HUD vanilla
│                                 (HudElement, HudElementBounds, ancre)
├── runtime/                   ← transforms + renderer hooks
├── editor/                    ← édition visuelle (drag/resize guides)
├── chat/                      ← chat custom (renderer, tabs,
│                                 classifier, mentions, timestamps,
│                                 quick-commands, settings)
├── keybind/                   ← keybindings (HudKeybinds)
├── mixin/                     ← injection vanilla (ChatHud, BossBar,
│                                 InGameHud, ChatScreen)
└── ui/
    ├── HudEditScreen          ← UI principale éditeur
    ├── HudEditChrome / SidePanel
    ├── HudHelpScreen
    └── style/                 ← tokens (couleurs, glow, rounded rect,
                                 icônes, grid bg)
```

## Dépendances Fabric

- `fabric-api` — réseau, lifecycle events, rendering hooks
- `mcef` *(post-migration uniquement)* — moteur Chromium pour
  `DynamicPlayerBackground` du menu principal. Optionnel si tu n'as
  besoin que de la partie HUD in-game.

## Packaging par le launcher

Le jar produit est embarqué dans le manifest signé
(cf `packages/manifest-signer`) sous `mods/reborn-hud-<ver>.jar` avec
son hash SHA-256. Téléchargé par le launcher dans `<gameDir>/mods/`
au prochain check.

## Versions

Alignées sur `REBORN_MC_VERSION=1.21.1`. À bumper en lockstep avec
le serveur et les autres mods Reborn.
