# Créateur de personnage — pipeline skin & assets (doc de reprise)

> État au 2026-08-10. Éditeur KORVEX publié en composition procédurale (0.4.25).
> **PEAU désormais branchée sur les vrais PNG** (male + female, 10 teintes chacun)
> + **carrure Classique/Alex** + **nouvelle disposition des yeux**. Cheveux / pilosité
> / tenues restent procéduraux en attendant leurs assets. Réfs visuelles :
> `docs/refs/korvex/screen1-4.png` + `docs/refs/korvex/skin-template-guide.png`.

## ✅ Fait (2026-08-10) — peau + carrure + yeux

- **Assets peau bundlés** : `assets/reborn/textures/character/skin/{male,female}/0..9.png`
  (10 teintes/genre, triées **clair→foncé** ; l'index N = même teinte pour les 2 genres).
  Source = `d:/REBORN - PJ/Modélisation/Skin/peau/{male,female}/peau*.png`.
- **Base peau** dans `RebornSkins.compose()` : le PNG de la teinte est décodé à neuf
  (octets cachés dans `skinBytes`, image mutable), puis les overlays procéduraux
  (tenue/cheveux/yeux/pilosité) sont peints **par-dessus sans détruire le visage**.
- **Peau = cycleur de style** (plus de color-picker) : `SkinSpec.skinStyle` (0..9),
  `SKIN_TONES=10`. `facetHasColor(cat 0)=false`, `facetHasStyle(cat 0)=true`.
- **Genre → dossier** : `SkinSpec.female` choisit `male/` ou `female/`.
- **Carrure (Classique/Alex)** = `SkinSpec.slim`. **Indépendante du genre côté Homme**
  (toggle « CARRURE » dans l'éditeur Corps, touche **S**) ; **Femme = Alex imposé**.
  Les PNG slim (`speau*`) livrés par le user sont **byte-identiques** aux classiques →
  **une seule texture/genre suffit** ; « slim » ne change **que le modèle** du
  `PlayerSkin` : le mixin `AbstractClientPlayerSkinMixin` force
  `PlayerModelType.SLIM/WIDE` via `RebornSkins.isSlim(uuid)`.
- **Yeux** : un œil = **2×2** — colonne gauche **blanche** (2×1), colonne droite **iris**
  (`eyeColor`, toujours color-picker). Cf `RebornSkins.drawEye()`.
- Sérialisation `SkinSpec.serialize()` mise à jour → ordre : `useOwnSkin, female, slim,
  skinStyle, hairStyle, hair, eyeStyle, eye, facialStyle, facial, outfitStyle, outfit`.

**Reste (prochaines sessions)** : assets **yeux** (masque gris + teinte), **cheveux**,
**pilosité**, **tenues** (voir plan couleur ci-dessous) ; puis synchro serveur ShinobiCore.

## Où ça vit dans le code
- **`minecraft/mod-hud/.../skin/SkinSpec.java`** — spec d'apparence par facette :
  `useOwnSkin`, `skinColor`, `hairStyle/hairColor`, `eyeStyle/eyeColor`,
  `facialStyle/facialColor`, `outfitStyle/outfitColor` + helpers HSV
  (`hsvToArgb`/`argbToHsv`) + `serialize()` (queue envoyée au serveur).
- **`minecraft/mod-hud/.../skin/RebornSkins.java`** — `compose(SkinSpec)` construit la
  texture **64×64** (aujourd'hui = aplats procéduraux). **C'EST ICI qu'on branche les PNG.**
  `applySpec(uuid, spec)` enregistre la texture ou retire l'override (`useOwnSkin`).
- **`minecraft/mod-hud/.../mixin/AbstractClientPlayerSkinMixin.java`** — applique la
  texture composée au `getSkin()` de n'importe quel joueur (gère déjà la synchro : chaque
  client compose la même image).
- **`minecraft/mod-hud/.../menu/character/CharacterCreateScreen.java`** — éditeur KORVEX
  (étape Apparence). Facettes/cat via `facetHasColor()`, `facetHasStyle()`, `cycleFacet()`,
  `facetStyleName()`, etc.

## Format des assets
- **1 PNG par style**, en **64×64**, layout **skin Minecraft standard**.
- **Transparent partout SAUF la zone** de la facette (cf `skin-template-guide.png`).
- `compose()` empile les calques dans l'ordre : **base peau → tenue → cheveux → pilosité → yeux**
  (chaque calque ne peint que ses pixels non-transparents).

Zones (rappel du guide) :
- **Cheveux** → tête + **calque chapeau** `(32,0)-(64,16)` (par-dessus la tête).
- **Visage / yeux** → face avant `(8,8)-(16,16)`.
- **Tenue** → torse `(16,16)-(40,32)` + veste `(16,32)-(40,48)` + bras + jambes.

## Décision COULEUR (arrêtée par le user 2026-08-09)
- **YEUX = masque niveaux de gris + color picker HSV** (une forme d'œil → teinte libre).
  → dans `compose()`, charger le PNG yeux et **multiplier par `eyeColor`**.
- **CHEVEUX / PILOSITÉ / TENUE / PEAU = variantes de couleur FAITES MAIN** (PNG couleur
  pleine). Le **cycleur de style choisit la variante colorée** ; **pas de picker**.
  → dans `compose()`, charger le PNG du style et le **blitter tel quel** (pas de teinte).

**Implémentation à faire (prochaine session, avec les assets) :**
1. `CharacterCreateScreen.facetHasColor()` → **ne renvoyer `true` que pour les yeux**
   (`cat==1 && subCat==0`). Retirer le picker des autres facettes.
2. `RebornSkins.compose()` → remplacer les aplats par : charger le PNG du style (via
   `ResourceManager`/`NativeImage.read`), l'overlayer sur la zone. **Yeux** : teinter par
   `eyeColor`. **Autres** : blit direct.
3. Ajuster les tableaux de styles (`SkinSpec.HAIR_STYLES` etc.) + noms sur ce que le user
   livre (nb de variantes libre). Le style d'une facette « faite main » = **le nom de la
   variante colorée** (ex. « Noir hérissé », « Blond long »…).

## Arborescence des assets (à créer)
```
minecraft/mod-hud/src/main/resources/assets/reborn/textures/character/
  eyes/0.png 1.png ...     (niveaux de gris, teintés)
  hair/0.png 1.png ...     (couleur pleine, 1 par variante)
  facial/...               (couleur pleine)
  outfit/...               (couleur pleine)
  skin/...                 (variantes de teinte de peau, couleur pleine ; ou set de couleurs)
```
Les PNG sont **bundlés dans le mod** → tous les clients composent la même image → la
**synchro cross-client est automatique** (le mixin applique la texture à tout joueur ayant
une spec).

## Plan de démarrage (petit d'abord)
1. User livre **1 catégorie** (ex. cheveux, 3-4 variantes couleur pleine).
2. Câbler le chargement + overlay dans `compose()` pour cette catégorie.
3. Tester le pipeline réel en jeu (1 cas), valider.
4. Dérouler yeux (masque+teinte) puis pilosité / tenues / peau.

## Synchro serveur (ShinobiCore — pas encore fait)
`submit()` envoie déjà `appearance.serialize()` (ordre : useOwnSkin, skin, hairStyle, hair,
eyeStyle, eye, facialStyle, facial, outfitStyle, outfit ; couleurs en hex RRGGBB) à la fin
de la commande `create` sur le canal `reborn:character`. **RESTE serveur** : ShinobiCore
doit **parser + stocker** cette queue (nouveaux champs sur `ShinobiCharacter`) et la
**rediffuser dans le roster** → chaque client `applySpec` sur les AUTRES joueurs
(aujourd'hui l'override n'est appliqué qu'au joueur local en preview).

## Retours user en attente (éditeur KORVEX)
- **Rotation** : clic-droit maintenu = rotation, mais **bornée ~±90°** (limite de
  `InventoryScreen.extractEntityInInventoryFollowsMouse`). 360° complet = API bas-niveau
  `GuiGraphicsExtractor.entity(EntityRenderState, ...)` — à faire si le user veut voir le dos.
- **Perso centré vs panneau gauche** sur les étapes Village/Identité/Valider : vérifier
  le chevauchement sur petits écrans (< 1280 de large).
- Police = **ArcadePix** (`RebornFont.arcade`), accents retirés (validé par le user).

## Recette publish mod-hud (rappel)
```
# build (⚠️ C: était plein → temp sur D:, JDK 25)
cd minecraft/mod-hud
./gradlew build -x test -x compileTestJava \
  -Dorg.gradle.java.home=D:/dev-cache/jdk25/jdk-25.0.4+7 \
  -Djava.io.tmpdir=D:/dev-cache/tmp
# jar ~93 Mo dans build/libs/reborn-hud-<ver>.jar ; nettoyer les vieux jars entre builds
# upload (long → background) :
GH_TOKEN=<file> gh release upload mods-v2.0.0 <jar> --clobber -R OMZBRK/reborn-roleplay
# manifest : swap l'entrée reborn-hud dans secrets/manifest-unsigned.json + bump version
#   → sign (packages/manifest-signer : pnpm exec tsx src/cli.ts sign ...)
#   → POST prod (consentement "publie") :
./packages/manifest-uploader/target/release/manifest-uploader.exe manifest \
  --file secrets/manifest-signed.json      # HTTP 201 = isCurrent
# commit -f les 2 manifests
```

## ⚠️ Disque
C: (118 Go) était **plein à 100%**. Libéré ~1 Go (temp + corbeille) et **temp des builds
redirigé sur D:** (`-Djava.io.tmpdir=D:/dev-cache/tmp`, obligatoire). Gros conso Reborn sur
C: = `%APPDATA%\RebornRoleplay` (**install jeu 3,45 Go**). Piste : déplacer le dossier de
jeu du launcher sur D: (908 Go libres). À traiter avant que ça re-bloque.
