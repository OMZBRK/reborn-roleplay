# Le mod client `reborn-hud` — toute l'interface Reborn

> **Référence technique, à jour au 2026-10-12** (`reborn-hud` 0.4.169). Le mod `minecraft/mod-hud` porte
> **toute la couche UI** du serveur : menus, HUD en jeu, chat, écrans de jeu, effets visuels, et le banc de
> captures. Les règles visuelles (DA) sont dans [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md).
> Plateforme et API 26.2 : [`MIGRATION_26.2.md`](./MIGRATION_26.2.md). Canaux avec les plugins :
> [`PLUGINS_REFERENCE.md`](./PLUGINS_REFERENCE.md) §4.

---

## 1. Ce que contient le mod (par package `fr/reborn/hud/…`)

| Package | Contenu |
|---|---|
| `menu/` | Menu principal, Échap « Tokonoma » (`esc/`), réglages (`settings/`), fiche shinobi « lanternes célestes » (`stats/`), sacoche « inrō » (`inventory/`), échoppe (`shop/`), tab list (`tablist/`), test de la Feuille (`tirage/`), connexion (`connect/`), personnages (`character/` + `character/scene/`) |
| `ko/` | HUD du KO et de l'ATA, QTE Volonté, menu des corps (`CorpsClient`), scène de mort (`DeathScreen`) |
| `staff/` | Poste de garde (`StaffScreen`, `GardeScreen` avec l'Infirmerie) |
| `parchemin/` | Bibliothèque, lecture, réglages staff, concentration du chakra |
| `chat/` | Chat en panneau (onglets Général / RP / Groupe, recherche, mode /me, icônes Lucide) |
| `combat/` | HUD combat : endurance en ensō autour du viseur, chiffres de dégâts, combo, icônes de recharge |
| `map/` | Carte du monde (touche M), intro en nuages |
| `interaction/` | Mode interaction (touche R) : curseur, menus contextuels (joueur, corps, entité, bloc, soi) |
| `camera/`, `screenshot/` | Caméra 3ᵉ personne, mode photo (filtres, cadres), galerie |
| `nameplate/` | Noms au-dessus des têtes (4 styles au choix) |
| `byakugan/`, `meteo/`, `effets/` | Vision Byakugan, météo client (pluie, sable, brume), filtres plein écran |
| `skin/` | Compositeur de skins Reborn (`RebornSkins`, `SkinSpec`) |
| `editor/`, `element/`, `config/` | Éditeur de HUD (déplacer / redimensionner / masquer), éléments, presets |
| `emote/`, `animation/` | Emotes EmoteCraft, Naruto run, synchronisation d'animations |
| `ui/` | `MenuScale` (taille des menus), `Da` (kit de dessin DA) |
| `audit/` | Bancs de test : `ResponsiveAudit`, `ConcentrationPreview`, `FeaturePreview` |
| `mixin/` | Tous les mixins (déclarés dans `reborn-hud.mixins.json`) |

## 2. Touches

| Touche | Action | Touche | Action |
|---|---|---|---|
| **K** | Fiche shinobi (stats) | **R** | Mode interaction |
| **M** | Carte | **F7** | Poste de garde (staff) |
| **E** | Sacoche | **H** | Éditeur de HUD |
| **J** | Viseur | **V** | Dash |
| **C** | Parade | **P** | Mode photo |
| **G** | Galerie | **F1** | Mode cinéma |
| **Y / U / I / O / '** | Caméra : épaule, côté, preset, menu, vanilla | **Alt gauche** | Regard libre |
| **;** | Menu personnage | **.** | Menu de démarches |
| Espace (à terre) | Volonté (si apprise) | Entrée / Échap (mort) | voir scène de mort |

## 3. Taille des menus — `MenuScale`

- **Règle (décision du 2026-10-10)** : les menus Reborn gardent **la taille qu'ils ont à l'échelle 2 en 1080p**
  (écran logique ≈ 960×540), quelle que soit l'échelle choisie par le joueur. `MenuScale.fit()` prend l'échelle
  dont la hauteur logique est la plus proche de 540 (minimum 600×330) pendant qu'un menu Reborn est ouvert.
- Le réglage du joueur (« Taille du HUD en jeu ») ne s'applique plus qu'au **HUD** et aux écrans vanilla.
- Historique : la 0.4.166 adaptait les menus à la fenêtre (« responsive ») → jugé « trop gros » aux échelles 3 et
  4, corrigé en 0.4.167. Un moteur de mise en page façon flexbox a été proposé et **refusé** (« pas besoin »).
- Le HUD est réglable dans l'éditeur ; ses positions sont en pixels absolus (décalage possible selon l'échelle,
  accepté).
- Code : `ui/MenuScale.java`, `mixin/menu/MenuScaleMixin.java`, `MenuScaleResizeMixin.java`.

## 4. Polices

- `RebornFont` : ArcadePix (majuscules ASCII seulement), Inter, Bebas Neue. **Pas de kanji ni de symboles** comme
  ☠ ou les flèches : un ☠ s'affichait « 8 ». Les kanji passent par `Component.literal` (police vanilla).
- La police des titres des menus de base (noms de village, questions) est celle à garder dans les nouveaux écrans.

## 5. Banc de captures (« visuels avant publication »)

**Règle de l'utilisateur (2026-10-10) : avant chaque prompt de publication, capturer les nouveaux visuels et les
lui montrer.** Le mod contient des bancs inactifs en production, activés par une variable d'environnement sur le
client de dev :

```bash
cd minecraft/mod-hud
JAVA_HOME=/c/Users/omarb/dev-cache/jdk-25.0.4.1+1 REBORN_FEATURE_PREVIEW=1 \
  ./gradlew runClient --offline -q --args="--quickPlaySingleplayer \"New World\" --width 1920 --height 1080"
# captures dans run/screenshots/preview_*.png, le jeu se ferme tout seul
```

| Variable | Banc |
|---|---|
| `REBORN_FEATURE_PREVIEW=1` | Lot en cours (à réécrire pour chaque lot) : `audit/FeaturePreview.java` |
| `REBORN_PERSO_DEBUG=1` (`vite` : 1080p seul) | Sélection et création « au bord de l'étang » : roster des maquettes, 8 étapes, 6 villages, échelles 3 et 4, 1366×768, 1706×960 (`perso/PersoDebug.java`) |
| `REBORN_SCENE=1` / `journey` | Ancienne sélection / création en scène 3D (repli `-Dreborn.sceneCharacterScreens=true`) |
| `REBORN_KO_DEBUG=1` | Toutes les phases du KO et de l'ATA |
| `REBORN_STAFF_DEBUG=1`, `REBORN_INFIRMERIE_DEBUG=1` | Poste de garde, Infirmerie |
| `REBORN_PARCHEMIN_DEBUG=1`, `REBORN_CONC_DEBUG=1` | Bibliothèque / lecture / réglages ; concentration |
| `REBORN_FICHE_DEBUG=1`, `REBORN_PERSO_DEBUG=1` (`=vite`), `REBORN_YEUX_DEBUG=1`, `REBORN_KO_DEBUG=mort` | Fiche shinobi ; sélection / création ; chirurgie des yeux ; scène de mort + menu d'interaction |
| `REBORN_SACOCHE_DEBUG=detail\|porte\|equip\|vide\|lourd`, `REBORN_ESC_SURVOL=<id>`, `REBORN_CARTE_BANC=1`, `REBORN_REGLAGES_ONGLET=<n>` | Variantes du banc d'audit (`REBORN_AUDIT=1`) pour la sacoche, l'Échap, la carte, les réglages |
| `REBORN_CHAT_DEBUG=1`, `REBORN_COMBAT_DEBUG=1` | Chat ; HUD combat (force le mode RP en solo) |
| `REBORN_METEO_DEBUG=1`, `REBORN_BYAKUGAN_DEBUG=1` | Météo ; Byakugan |
| `REBORN_AUDIT=1` (+ `REBORN_AUDIT_SCALES`, `REBORN_AUDIT_ONLY`) | Audit responsive : 46 écrans × échelles |
| `REBORN_MOCKUPS` | Maquettes de scènes |

- Un banc injecte de faux messages serveur (via des méthodes `debugReceive`) et prend des captures à des ticks
  fixes. Le client de dev a besoin de **PlasmoVoice** dans ses mods pour démarrer.
- Pour une capture rapide d'un détail : recadrer avec Python/Pillow avant de l'envoyer.

## 6. Pièges propres au mod (26.2)

- **Les mixins 26.2 échouent au lancement, pas au build** : un `compileJava` vert ne prouve rien ; lancer le client.
- API des écrans : `extractRenderState`, `extractBackground`, `mouseClicked(MouseButtonEvent, boolean)`,
  `mouseDragged(MouseButtonEvent, double, double)`, `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`,
  `GuiGraphicsExtractor`. `blit` existe en 10/11 et 12/13 arguments (le 12 met à l'échelle sans tuilage).
- Masquer le HUD : `HudAccessor.reborn$setHidden(true)` (26.2 a retiré `options.hideGui`).
- **Pas d'entités factices hors monde dans un écran** (gel au join) — cf.
  [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) §5.
- Payloads : JSON UTF-8 brut sans préfixe de longueur ; enregistrer le type dans les deux sens si le mod envoie.
- Le jar fait ~23,9 Mo. **Un jar qui grossit fortement = un asset intrus** (cf. publication).
- Plaques de nom : positionner sur l'image interpolée (partialTick), pas sur le dernier tick.

## 7. Historique des versions

Le détail version par version (0.4.139 → 0.4.169) est dans [`../pilotage/JOURNAL_DES_LIVRAISONS.md`](../pilotage/JOURNAL_DES_LIVRAISONS.md).
