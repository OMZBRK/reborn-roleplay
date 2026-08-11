# Créateur de personnage — pipeline skin & assets (doc de reprise)

> État au 2026-08-11. **Refonte « remise en règles » : pipeline data-driven (catalogue),
> convention de nommage à préfixes, gating clan/genre, moteur de masque RGBA.**
> Voir la section « ── PIPELINE V3 » ci-dessous (elle **remplace** les sections
> historiques « rouge = teintable » pour cheveux/tenues ; les yeux gardent le mode red).

## ── PIPELINE V3 — catalogue + convention + masque RGBA (2026-08-11)

### Convention de nommage (préfixe = catégorie)
`Peau_ Tatouage_ Yeux_ Cheveux_ Barbe_ Haut_ Bas_ Complet_` + suffixe `_Mask`.
Ex. `Complet_KimonoChill01.png` (+ `Complet_KimonoChill01_Mask.png`). L'`id` d'un
asset **est** son nom de fichier (sans extension). 64×64, layout skin standard,
**transparent hors zone** (vérifié : les tenues n'ont pas de visage → la teinte de
peau est préservée ; les cheveux couvrent crâne + frange).

### Catalogue data-driven — `assets/reborn/textures/character/catalog.json`
Source de vérité. **Ajouter un cosmétique = déposer le PNG + 1 ligne JSON, zéro code.**
Chargé par `skin/CharacterCatalog.java`. Champs par entrée :
- `id`, `name` (FR affiché), `tint` (`none|all|red`), `split` (mode red), `slot`
  (tenue : `complet|haut|bas`), `gender` (`male|female|null`), `clan` (nom exact|null),
  `zones` (masque RGBA : `[{ch:R|G|B|A, name, default:"RRGGBB"}]`).

### Gating clan / genre
`gender`/`clan` filtrent **le choix à la création** (`CharacterCatalog.available`).
Le **staff est exempté** (`CharacterData.staffExempt()`). La **composition** ne filtre
jamais : un perso garde son apparence même si redevenu non-éligible. Défauts actuels :
tenues `Complet_*Uchiha*/Sasuke/Obito` → clan **Uchiha** ; `Complet_Sakura/Tsunade`
+ cheveux `Cheveux_Tsunade/SakuraAgee` → **femmes**. Modifiable en 1 ligne dans le JSON.

### Ordre des calques (`RebornSkins.compose`)
peau → tatouage → **tenue** → yeux → pilosité → **cheveux (dernier)**. Les cheveux
passent **par-dessus la tenue** (col) et par-dessus les yeux (frange) — conforme à
l'ordre demandé (Yeux avant Cheveux).

### Moteur de teinte (`RebornSkins.overlayAsset`)
Chaque asset est **blitté tel que peint**, puis recoloré selon son mode :
- **`all`** — toute la zone reteintée par la couleur (picker), **luminance de la
  texture préservée** (`tintLuma` : ombrage gardé). → cheveux, pilosité.
- **`red`** — pixels « rouges » (iris) teintés, reste gardé, `split` gauche/droite
  (hétérochromie). → yeux.
- **masque RGBA** — si `<id>_Mask.png` existe **et** que l'asset déclare des `zones` :
  chaque **canal R/G/B/A** est un masque de zone, recoloré en HSL par sa couleur
  (`outfitZone[0..3]`, indexé par canal). Poids = valeur du canal /255 (blend).
  **Sans masque/zones → tenue affichée telle que peinte** (couleurs fixes).

### Sérialisation (`SkinSpec.serialize`, v2 — blob opaque côté ShinobiCore)
Ordre : `useOwnSkin, female, slim, skinStyle, hairId, hairColor, eyeId, eyeColor,
eyeColorRight, facialId, facialColor, outfitId, oz0, oz1, oz2, oz3`. Les facettes
stockent désormais un **id d'asset** (string) au lieu d'un index numérique.

### V3.1 — retours user (2026-08-11) : peau en rampe, sourcils, accessoires, aperçu gating
- **Couleur de peau = curseur sur rampe** (réf `REF - ALL/couleur de peau.png`) au lieu des 10 PNG.
  `SkinSpec.skinColor` + `SkinSpec.skinRamp(t)` (12 stops pâle→foncé). `RebornSkins.composeSkinBase`
  recolore **un template de luminance** (`<genre>/{SKIN_TEMPLATE=4}.png`) par `skinColor` en
  préservant l'ombrage (facteur = luma / **luma médiane** du template — la médiane évite que les
  yeux blancs cuits n'assombrissent tout). Vérifié : au médian on obtient exactement la couleur
  choisie. UI = barre draggable (cat Corps), remplace le cycleur de teinte.
- **Sourcils recolorables** : `SkinSpec.browColor`. Les sourcils **cuits** du template (pixels
  sombres du rectangle visage 8..15×8..15, luma < 0.55·médiane) sont recolorés par `browColor`.
  UI = nouvelle sous-cat Visage **« Sourcils »** (couleur seule, pas de style).
- **Accessoires** (bandeaux…) : catégorie catalogue **`accessory`** (vide pour l'instant), calque
  **au-dessus des cheveux**, gating clan/genre comme les autres. UI = sous-cat Visage **« Accessoire »**.
  → réponse au « des bandeaux sont mélangés aux coupes » : en faire des assets `Accessoire_*.png`
  séparés. Sous-cats Visage désormais : Yeux · Sourcils · Cheveux · Pilosité · Accessoire.
- **Aperçu gating (staff)** : le gating est **exempté pour le staff** — l'exemption vient du **rôle
  API** (`staffExempt` via candidature, HELPER+), PAS de l'op Minecraft → se déop en jeu ne
  change rien. Touche **G** dans l'éditeur = bascule « aperçu JOUEUR » (force `exempt=false`) pour
  visualiser/tester les listes gatées. Indicateur affiché quand staff. (Le filtre clan/genre
  lui-même est correct ; c'est l'exemption staff qui masquait le gating au test.)
- **Sérialisation v3** : `useOwnSkin, female, slim, skinColor, browColor, hairId, hairColor, eyeId,
  eyeColor, eyeColorRight, facialId, facialColor, outfitId, oz0..3, accessoryId, accessoryColor`.
  Toujours **opaque côté ShinobiCore** (aucun rebuild serveur). ⚠️ Les persos créés en v2
  (skinStyle int) réafficheront une peau bizarre → recréer.

### V3.2 — sous-vêtements (couche par défaut) + format d'icônes (2026-08-11)
- **Sous-vêtement = couche par défaut** au-dessus de la peau, SOUS les tenues. Assets
  `underwear/Sous_Homme.png` / `Sous_Femme.png` (livrés), choisis auto selon le genre.
  Ordre `RebornSkins.compose` : peau → **sous-vêtement** → tenue (couvre) → yeux →
  pilosité → cheveux → accessoire. Sans tenue/haut/bas → le sous-vêtement reste visible
  (= la base par défaut, cf vision boutique : Complet via caisses ; Haut/Bas/Complet via
  boutique IG ; échangeables ; nu = sous-vêtement). **Corrige le bug « sous-vêtement pris
  comme peau »** (il était peint dans le template et reteinté par skinColor ; la couche
  overlay le recouvre désormais avec sa vraie couleur, indépendante de skinColor).
- **RGBA sur sous-vêtement** : prêt. L'entrée catalogue n'a pas encore de `zones` ; dès
  qu'un `Sous_Homme_Mask.png` + `zones` sont ajoutés, la recolo par canal s'active (le
  compose passe `new int[0]` → les zones prennent leurs couleurs par défaut du catalogue).
- **⚠️ Si le sous-vêtement du template dépasse** l'overlay (peek de peau teintée aux
  bords), il faudra un template de peau NU (sans sous-vêtement peint) — à voir au test.

### Format des ICÔNES / logos (pour l'UI de sélection) — demandé par le user
- **Taille : 32×32 px**, PNG, **fond transparent**, pixel-art centré (rendu 1:1, pas de scale).
- **Logo de catégorie / sous-catégorie** (Corps, Visage, Genre, Tenue, Yeux, Sourcils,
  Cheveux, Pilosité, Accessoire) → `assets/reborn/textures/character/ui/<slug>.png`
  (slug en minuscules sans accent : `corps`, `visage`, `cheveux`, `accessoire`…).
- **Vignette par asset** (une par coupe/tenue/accessoire, optionnel) →
  `assets/reborn/textures/character/<folder>/icons/<id>.png`
  (ex. `hair/icons/Cheveux_Sasuke.png`). Remplacera le nom texte / le glyphe procédural.
- **✅ Rendu des logos de catégorie CÂBLÉ (2026-08-11)** : les 9 icônes 32×32 livrées
  (`character/ui/{corps,visage,genre,tenue,yeux,sourcils,cheveux,pilosite,accessoire}.png`)
  sont blittées (`ctx.blit(GUI_TEXTURED, id, x,y, 0,0, 32,32, 32,32)`) à la place des glyphes
  procéduraux, dans la grille + les sous-tabs Visage + la tuile de l'éditeur. Check d'existence
  caché (`ICON_EXISTS`) → repli procédural si absente. Liseré blanc/accent au survol/sélection.
  Les icônes ont leur **propre fond** (thème crimson Reborn). RETURN/CONFIRM/`A ‹ n/m › D` restent
  des boutons texte à hint touche (conforme aux réfs SCREEN1-4). **Vignettes par asset** (hair/icons/…)
  = pas encore câblées (à faire quand le user les livre).

### V3.4 — fixes visuels + sous-vêtement recolorable (2026-08-11)
- **Bug liseré blanc corrigé** : `roundedOutlinedRect(...,fill=0,...)` remplit TOUT en
  couleur de bordure (il dessine le rect plein en border puis n'overdraw pas l'intérieur
  si fill=0). → remplacé par `outlinedRect(...,0,border)` (4 bords 1px seulement) pour les
  liserés survol/sélection (grille + sous-tabs).
- **Chevauchement des libellés sous-tabs** (5 tabs `PILOSITEACCESSOIRE`) : le libellé n'est
  plus affiché que pour la tuile **sélectionnée/survolée**.
- **Sous-vêtement recolorable** : le PNG n'est PAS codé en canaux R/G/B (23-32 couleurs de
  tissu shadé) → « png comme masque » = **tint `all`** (recolore tout le sous-vêtement par
  une couleur, ombrage du tissu préservé). Couleur par défaut `UNDERWEAR_DEFAULT` (gris-beige
  proche du peint) ; le choix joueur viendra avec la boutique.

### ✅ V3.5 — REFONTE ÉCRAN DE SÉLECTION (hybride GUI, 2026-08-11)
User a choisi **hybride** (rendu GUI maintenant, lobby serveur plus tard). Fait dans
`CharacterSelectScreen` :
- **Skin RP par perso visible** : `applyFocusPreview()` = `applySpec(localUuid,
  deserialize(card.appearance))` à l'init + à chaque changement de perso focalisé →
  on voit le skin composé de CHAQUE perso en parcourant (perso gelé + autres cachés
  → preview local sans effet pour autrui). Tuile créer / sans apparence → skin normal.
- **Rendu GUI + caméra face/plus grande** : `drawAvatar` via
  `InventoryScreen.extractEntityInInventoryFollowsMouse` (mouse param = centre → face
  statique), size = `height*0.34`. Plus de `THIRD_PERSON_FRONT` monde.
- **Fond stylisé opaque** (masque le monde IG) : `fillGradient` plein crimson-sombre
  + halo central + bande basse. Logo REBORN.
- **Loading 5-10 s** : `CharacterLoadingScreen` (~7 s, 140 ticks) ouvert à la sélection
  pendant que ShinobiCore fait `setActive` (téléport IG) ; se ferme seul → retour en jeu.
  Fond stylisé + « Chargement… » + barre de progression + nom du perso.
- ⏭️ Option future : lobby serveur stylé (ShinobiCore) au lieu du rendu GUI, si le user
  veut plus d'immersion (le choix hybride garde la porte ouverte).

### V3.6 — logo serveur + nouveaux assets + halo retiré (2026-08-11)
- **Logo serveur** (`HUD - Texturing Pack/logo.png`, emblème 1536×1024 à FOND NOIR + halo)
  → converti en **PNG transparent** (alpha = luminance, réduit 384×256) bundlé
  `character/ui/logo.png`. Util `CreatorUi.blitLogo()` (repli texte « REBORN » si absent).
  Remplace le texte « REBORN » : panneau gauche du créateur, coin haut-droite créateur +
  sélection, centre de l'écran de loading.
- **Nouveaux cheveux** (5) : `Cheveux_2/3/4` (libres), `Cheveux_Femme`/`Cheveux_Karin` (femmes).
- **Nouvelles tenues** (5) : `Complet_Ceremonie`/`Ceremonie2`/`Taijutsuka` (libres),
  `Complet_Femme1`/`Complet_Karin` (femmes). Yeux `Yeux_Style1` rafraîchis.
- **Halo** de l'écran de sélection (`glowRect`) **retiré** (jugé moche) → juste le fond
  dégradé crimson.

### (archive) chantier écran de SÉLECTION — cadré 2026-08-11
Réf `d:/REBORN - PJ/REF - ALL/renduselectioncaractere.png`. Problèmes actuels :
1. **On ne voit pas le skin RP du perso survolé/sélectionné** (l'écran montre le corps du
   joueur local, pas le skin composé de CHAQUE perso). Fix client = `RebornSkins.applySpec(
   localUuid, SkinSpec.deserialize(card.appearance))` au changement de perso focalisé
   (le perso est gelé + les autres joueurs sont cachés pendant la sélection → preview local OK).
2. **Perso de dos + trop petit** : la caméra (THIRD_PERSON_FRONT) ne cadre pas bien →
   régler orientation face + zoom.
3. **Fond = monde IG** : le user veut un **fond stylé** + joueur immobile. Options = (a) serveur
   spawn dans un lobby/void stylé, ou (b) client `drawEntity` GUI sur un backdrop custom.
4. **Loading 5-10 s** stylé à la sélection avant d'arriver IG. (voir [[session-roadmap-modhud]] « loading Zenkai ».)
→ Multi-parties (client + ShinobiCore serveur). À cadrer avec le user (surtout fond : lobby serveur vs drawEntity).

### RESTE à faire (retours après test)
- **Masques** : aucun `_Mask.png` livré encore → les tenues s'affichent telles que
  peintes. Peindre les masks + déclarer `zones` pour activer la recolo par zone.
- **UI multi-zone** : le picker tenue édite la **zone R (`outfitZone[0]`)** ; ajouter
  un sélecteur de zone (façon « œil édité ») quand une tenue a >1 zone.
- **Haut/Bas** : tout est `Complet_` pour l'instant ; le slot `haut`/`bas` est prévu
  (catalogue) mais pas encore composé séparément.
- Ajuster les **restrictions** (clan des coiffures Uchiha ? etc.) selon tes retours.

---

> Historique (2026-08-10). Éditeur KORVEX publié en composition procédurale (0.4.25).
> **PEAU branchée sur les vrais PNG** (male + female, 10 teintes chacun) + **carrure
> Classique/Alex** + yeux. Cheveux / pilosité / tenues étaient procéduraux. Réfs :
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

## Pipeline « rouge = teintable » — GÉNÉRALISÉ à toutes les facettes (2026-08-10)

Les **yeux** ont validé le pipeline (texture PNG, rouge teinté par le picker, blanc gardé,
hétérochromie). Il est désormais **le même pour cheveux / pilosité / tenues** :
`RebornSkins.overlayTinted(folder, style, colorL, colorR, splitX)` overlaye n'importe quel
PNG **de façon agnostique au layout** (chaque pixel opaque est peint à ses coordonnées).

**Règle d'un asset** (cf `docs/refs/korvex/asset-template-guide.png`) :
- **64×64**, layout skin Minecraft standard, **transparent** partout sauf la zone.
- **ROUGE** (`r > g*1.5 && r > b*1.5 && r > 50`) = **zone teintable** : colorée par le
  color-picker ; peins **2+ teintes de rouge** (foncé/clair) → 2 teintes de la couleur
  choisie (facteur = `rouge_pixel / rouge_max`).
- **Non-rouge** = **couleur fixe** gardée telle quelle (blanc des yeux, détail uniforme de
  clan…).
- **Yeux** : split gauche/droite à `x=11` → 2 couleurs possibles (hétérochromie).

**Arborescence** (bundlée dans le jar du mod → publish requis à chaque ajout) :
```
minecraft/mod-hud/src/main/resources/assets/reborn/textures/character/
  eyes/0.png      ✅ livré (yeuxstyle1)
  hair/0.png 1.png …     (à livrer)
  facial/0.png …          (à livrer)
  outfit/0.png …          (à livrer)
  skin/{male,female}/0..9.png  ✅ (base peau, pas de tint — PNG couleur pleine)
```
Le style cycler mappe `style N → <folder>/N.png` (repli sur `/0.png`, puis sur le placeholder
procédural si aucun PNG). **Workflow** : user livre 1 PNG → je le bundle → rebuild+publish
(zéro code). ⚠️ La **peau** reste hors tint (variantes couleur pleine, cf ci-dessus).

**Reste** : assets **cheveux / pilosité / tenues** (le pipeline les attend) ; ajuster les
`*_STYLES` (noms/nb) sur ce que tu livres.

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
