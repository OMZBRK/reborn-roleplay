# Palette Reborn — Akatsuki direction

Reference colors pour Aseprite / Photoshop / tout outil pixel-art quand tu dessines des assets Reborn.

> **Direction visuelle 2026-06-21+** : Akatsuki (rouge profond + noir + accents or). Cf brief design dans `docs/REBORN_DESIGN_PHASE_B.md` (à venir).

## Palette principale (8 couleurs)

| Nom            | Hex       | Usage                                                       |
|----------------|-----------|-------------------------------------------------------------|
| `bg-deep`      | `#0A0608` | Background main menu / surfaces "deepest"                   |
| `bg-surface`   | `#15090B` | Cards, modals, panels                                       |
| `bg-elevated`  | `#1F0E11` | Surface au-dessus du bg-surface (hover, focused)            |
| `crimson-core` | `#A0182B` | Accent rouge Akatsuki principal (CTA, halos, logo cloud)    |
| `crimson-soft` | `#5C0E18` | Hover state du crimson, glows                               |
| `gold-core`    | `#D9A95E` | Accent or pour le contraste (sub-actions, hover icons)      |
| `text-primary` | `#F5E9D0` | Texte principal sur fond sombre (ivoire chaud, pas blanc)   |
| `text-muted`   | `#7A6E5C` | Texte secondaire / metadata                                 |

## Palette Aseprite (importable)

Sauvegarde le bloc ci-dessous dans `reborn-akatsuki.gpl` (GIMP Palette format, Aseprite l'importe via **Edit → Preferences → Palette → Import**).

```
GIMP Palette
Name: Reborn Akatsuki
Columns: 4
#
 10   6   8	bg-deep
 21   9  11	bg-surface
 31  14  17	bg-elevated
160  24  43	crimson-core
 92  14  24	crimson-soft
217 169  94	gold-core
245 233 208	text-primary
122 110  92	text-muted
255 255 255	pure-white
  0   0   0	pure-black
```

## Grille / résolutions standard

| Asset                         | Taille canvas | Notes                                            |
|-------------------------------|---------------|--------------------------------------------------|
| Icône UI petite (titlebar)    | 16×16         | Close / minimize style window controls           |
| Icône UI moyenne (toolbar)    | 24×24         | Boutons reborn-hud editor, OST player controls   |
| Icône UI grande (CTA)         | 32×32 ou 48×48| Play button du main menu, gros boutons          |
| Logo / sigil                  | 64×64 ou 128×128 | Logo Reborn central, sigils                  |
| Bouton "Press Enter"          | 256×64        | Bouton CTA principal style Stray                 |

> **Tip** : tous power-of-2 (16, 32, 64, 128...) pour les mipmaps Minecraft. Pour les icônes UI sans mipmap (la plupart de notre code) ce n'est pas obligatoire, mais ça scale mieux.

## Templates Aseprite à créer (toi-même, 5 min)

Aseprite n'a pas de format texte que je puisse générer ici. Workflow :

1. **Crée un nouveau fichier** : File → New, **128×128**, mode RGB, background "Transparent".
2. **Importe la palette** : Edit → Preferences → Palette → Browse… → sélectionne `reborn-akatsuki.gpl` (copié depuis ci-dessus).
3. **Active la grid** : View → Show Grid + View → Grid Settings → 8×8 (ou 16×16 selon ce que tu dessines).
4. **Active onion skin** si tu fais des animations : View → Onion Skin.
5. **Save As** → `templates/reborn-icon-template.aseprite`. Réutilise pour chaque nouvel asset (File → Open + File → Save As pour duplicater).

## Workflow d'intégration dans Reborn

```
1. Dessine ton asset en Aseprite avec la palette ci-dessus
2. File → Export Sprite Sheet → PNG, 1x, transparent background
3. Drop dans le bon dossier :
   - assets/reborn/textures/gui/icons/<name>.png   (icônes vanilla-style, scope mod-hud)
   - assets/reborn-hud/textures/icons/<name>.png   (icônes spécifiques editor HUD)
4. Si nouveau name : ajoute la constante Identifier dans IconTextures.java
5. cd minecraft/mod-hud && ./gradlew build
6. Republie le manifest (./scripts/publish-mod-manifest.ps1 -Version X.Y.Z -SkipBuild)
7. Relance le launcher + Jouer -> ton asset apparaît
```

## Conventions visuelles Reborn

- **Pas d'outline noir 1px** systématique style Minecraft vanilla. Préfère une ombre interne légère ou un glow `crimson-core` pour les CTAs.
- **Anti-alias par pixel manuel** plutôt qu'auto (Aseprite Pencil tool, pas Brush).
- **Cohérence Akatsuki** : 80% des assets doivent avoir au moins un pixel `crimson-core` (signature visuelle).
- **Texte / lettre** : utiliser `text-primary` (ivoire), JAMAIS `pure-white` (trop dur sur fond sombre).
- **Glow / halo** : superposer `crimson-soft` à 40% opacity autour du sujet pour l'effet light beam style Stray/Sineru.

## Assets prioritaires à redessiner (par ordre d'impact visuel)

1. `assets/reborn/textures/gui/icons/play.png` (24×24) — utilisé partout OST player + bouton CTA
2. Bouton "PRESS ENTER" main menu (256×64, nouveau) — style Stray, central
3. Logo REBORN central main menu (128×128, nouveau ou revamp existant)
4. Icones OST player : `play.png`, `pause.png`, `next.png`, `prev.png`, `volume.png`
5. Icones titlebar : `close.png`, `menu.png`, `settings.png`
6. Sigil / monogramme Reborn pour les loaders / splash
