# Refonte mod-hud — Reborn UI (4 piliers)

> Statut : **en cours**. Document de design + spec d'assets. Source de vérité
> pour la refonte de la couche HUD/chat/menus du `minecraft/mod-hud`.
> Langue UI : FR. Thème : Akatsuki (cf `menu/Colors.java`).

## 1. Contexte & objectif

Le `mod-hud` porte déjà beaucoup : éditeur HUD drag/resize (`ui/`), chat avancé
(`chat/`), menus custom (title / ESC / Paramètres), et une lib de rendu
procédural (`menu/DrawHelpers`, `Colors`, `RebornFont`) + widgets réutilisables
(`SegmentedControl`, `SliderWidget`, `ToggleBig`, `TabButton`).

But : **unifier** tout ça sous un langage UI unique, sobre et cohérent, inspiré
de mods de référence, et **ajouter un module crosshair**. Ce n'est pas une
réécriture from-scratch : on capitalise sur l'existant.

Mods de référence :
- **OneConfig** — la coquille de config (sidebar + recherche + lignes de réglages, dark sobre).
- **StarHUD** — placement/édition des éléments HUD (ancrage, snapping, scale, groupes).
- **AdvancedChatHUD** — chat multi-fenêtres/onglets, filtres, customisation.
- **Custom Crosshair Mod** — fonctionnel : formes, dynamique, adaptive, hit-marker, draw-your-own.
- **Crosshair** — esthétique : presets jolis recolorables.

## 2. Principe d'architecture — un hub unique

Un **hub « Reborn UI »** (écran `ConfigShellScreen`) façon OneConfig contient
toutes les catégories. Chaque pilier est une catégorie (ou un sous-écran lancé
depuis le hub). Modèle data-driven : une catégorie expose des « lignes » de
réglages (`SettingsTab`-like) ; le hub gère le layout, le scroll, la recherche
et le clipping.

```
ConfigShellScreen (coquille OneConfig)
├─ Top bar : titre + recherche + fermer
├─ Sidebar gauche : catégories (Vidéo, Audio, Contrôles, HUD, Chat, Crosshair, Discord, Compte)
└─ Content (scrollable) : la catégorie active
     ├─ catégories "réglages" → lignes (toggle/slider/segmented/dropdown/color/keybind…)
     └─ catégories "éditeur"  → bouton qui ouvre un sous-écran plein (HUD editor, etc.)
```

Règle de fond conservée (cf `CLAUDE.md`) : **chrome procédural** (panneaux,
boutons, sliders) pour rester net à tous les GUI Scale + themable ; **assets PNG**
seulement pour l'art (icônes, crosshairs, logo, déco).

## 3. Pilier 1 — Hub de config (OneConfig)

- **Layout** : sidebar verticale de catégories (~200px), barre de recherche en
  haut, zone de contenu en lignes `label gauche / contrôle droite`, panneaux
  arrondis, hover doux.
- **Recherche** : filtre les catégories (v1) puis les lignes (v2).
- **Contrôles** (widgets) : existants = toggle, slider, segmented. À ajouter :
  **dropdown**, **color picker**, **capture de keybind**, **champ texte/nombre**,
  groupes/sous-sections.
- **Migration** : les onglets actuels (Vidéo/Audio/Contrôles/Discord/Compte)
  deviennent des catégories du hub. `RebornOptionsScreen` (tabs horizontaux) est
  remplacé par `ConfigShellScreen` (sidebar).

## 4. Pilier 2 — Placement HUD (StarHUD)

- Éditeur (touche dédiée, déjà `ui/HudEditScreen`) étendu :
  - **Ancrage** (coins/bords/centre) + **snapping** + grille.
  - Par élément : **scale**, couleur, visibilité, fond optionnel (arrondi),
    direction de croissance, mode **Icône / Info / Les deux**.
  - **Groupes** d'éléments (gap réglable).
- **Registry** d'éléments HUD : coords, direction, FPS, ping, heure, biome,
  effets, jour, vitesse, joueurs en ligne… (extensible).
- Anti-overlap avec le HUD vanilla.

## 5. Pilier 3 — Chat (AdvancedChatHUD)

Sur la base `chat/` actuelle :
- **Multi-fenêtres / onglets** déplaçables & redimensionnables, visibilité par
  fenêtre.
- **Filtres** : router un message vers un onglet selon son contenu.
- Customisation : padding, couleur de fond/bordure, espacement des lignes,
  fade (durée/type), scale, compact vs plein.
- Compteur de **non-lus** par onglet, têtes de joueurs, stripe/stack.

## 6. Pilier 4 — Crosshair (CCM + Crosshair)

- **Fonctionnel (CCM)** : formes (croix / point / cercle / T / custom), taille,
  couleur, **rainbow**, **adaptive color** (selon la cible visée),
  **dynamique** (écart à l'usage arc/épée, cooldown), **hit-marker**,
  **draw-your-own** (éditeur pixel).
- **Esthétique (Crosshair)** : set de **presets** prêts à choisir avec preview,
  recolorables.
- Implémentation : mixin sur le rendu du crosshair vanilla (`InGameHud`), module
  de config dans le hub + preview live.

## 7. Approche assets — hybride

| Type | Qui | Pourquoi |
|---|---|---|
| Chrome / layout (panneaux, boutons, sliders, onglets, fonds) | **Code (procédural)** | Net à tout GUI Scale, recolorable au thème |
| Art pixel (icônes, crosshairs, logo, déco) | **Aseprite (PNG)** → intégré au code | Le pixel-art y brille |

Rien n'est bloquant : tout peut être fait procéduralement ; les PNG sont du
polish qu'on câble quand ils arrivent.

## 8. Spec sheet d'assets (pour Aseprite)

**Conventions**
- PNG **32-bit RGBA**, fond **transparent**.
- Chemin : `src/main/resources/assets/reborn/textures/gui/...`
  → identifiant `reborn:textures/gui/<chemin>.png`.
- Icônes & crosshairs en **blanc pur `#FFFFFF`** sur transparent → teintés en
  code (hover, thème, rainbow, adaptive). Presets multicolores figés = en
  couleur directement.
- Power-of-two non requis (MC 1.21) **sauf** gros logo (256×256).
- Pas de détail 1px fragile (disparaît à GUI Scale bas). Au doute : dessine en
  **2×**, on réduit.

**Assets à produire**

| Asset | Taille | Notes |
|---|---|---|
| Icônes nav / catégories | **16×16** (ou 20×20) | blanc, set cohérent ; calque sur `textures/gui/icons/` existant |
| **Crosshairs presets** | **15×15** → **32×32** | centré, 1px de marge, blanc (recolorable), 1 PNG/preset ; dossier `textures/gui/crosshairs/` |
| Hit-marker | **16×16** | "X" blanc centré avec gap |
| Logo / emblème | **256×256** | comme `logo_sigil.png` |
| Décorations (dividers, pétales, accents) | libre | transparent |
| (Option) skin panneau 9-slice | **48×48**, coins **8px** | seulement si panneaux texturés voulus ; sinon procédural |

**Palette (thème Akatsuki — à respecter dans les assets couleur)**
- fond `#0A0608` · surface `#15090B` · bordure `#2D181C` / `#4A2127`
- accent `#A0182B` (hover `#C01E35`, pressed `#7A1322`)
- or `#D9A95E` · ivoire `#F5E9D0`
- succès `#4ADE80` · warning `#F59E0B` · danger `#EF4444`

**Priorité de production** : 1) crosshairs presets (100% art, gros impact),
2) set d'icônes, 3) logo/emblème.

## 9. Séquencement

1. **Hub OneConfig** (coquille) ← *en cours*
2. **Crosshair** (neuf, self-contained ; presets faisables en parallèle côté Aseprite)
3. **Polish éditeur HUD** (StarHUD)
4. **Polish chat** (AdvancedChatHUD)

## 10. Notes d'implémentation

- Réutiliser le scroll/scissor/scrollbar déjà écrit pour `RebornOptionsScreen`.
- Garder les widgets existants ; en ajouter (dropdown, color picker, keybind).
- Persistance via `RebornPrefs` (étendre au fur et à mesure).
- Les écrans custom doivent rester robustes en petit viewport / GUI Scale élevé
  (cf le fix d'adaptativité déjà fait sur l'ESC menu et les Paramètres).
