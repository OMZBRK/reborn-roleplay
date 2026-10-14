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
| `sol/`, `notif/` | Ramassage volontaire (touche F, invite), lueur de rareté au sol (`AuraSol`, v3 du 15/10 : petit halo pixel art `gui/inventaire/lueur.png` 44×11 — 4 images 11×11, halo seul puis 1-2 pixels qui scintillent — généré par `tools/gen_inventaire_textures.py`, teint à la couleur de la rareté, respiration d'alpha ; objets en vue seulement ; sélection par tick, ≤ 48 objets à 16 blocs, un quad par objet en un seul lot `submitCustomGeometry`), rareté client ; ramassage F actif en survie, aventure et créatif ; notifications en bas à droite et état de charge (`reborn:notif`), indicateur de surcharge sous les vitals (13/10) |
| `ko/` | HUD du KO et de l'ATA, QTE Volonté, menu des corps (`CorpsClient`), scène de mort (`DeathScreen`) |
| `staff/` | Poste de garde v3 « le volet » (14/10) : `StaffScreen` (volet, onglets, alertes, liste, menus), `GardeFiche` (fiche compacte, sections, sanction), `GardeRecherche` (recherche universelle, hors ligne et actions), `GardeJournal` (jetons, jours), `GardeOutils` + `GardeAnnonces` (outils, chat, commandes, modes), `GardeFormulaire` (formulaire dans le volet), `GardeActions` (catalogue des actions et raccourcis), `GardeArt` (palette, icônes `garde/icones.png`, pinceau en lots) ; `GardeScreen` = l'écran ouvert, avec l'Infirmerie dans Outils ; effets staff en jeu (`EffetsStaff` : annonce, écran noir, séisme via `mixin/CameraSeismeMixin`, indicateur du mode staff / animation ; `PlaquesStaff` : plaques du mode staff ; canaux `reborn:annonce`, `reborn:ecran`, `reborn:plaques` par `CanalJson`), serveur simulé du banc (`StaffDemo`) |
| `parchemin/` | Bibliothèque, lecture, réglages staff, concentration du chakra |
| `chat/` | Chat en panneau (onglets Général / RP / Groupe, recherche, mode /me, icônes Lucide) |
| `radio/` | Radio en escouades (14/10, canal `reborn:radio`) : `RadioClient` (état, invitation, qui parle), `RadioScreen` (écran N), `RadioHud` (élément HUD « Escouade »), `RadioMenus` (menu R : Radio, Oreillette), `RadioDebug` (banc) — [`../gameplay/RADIO.md`](../gameplay/RADIO.md) §12 |
| `voice/` | Plasmo Voice (client 2.1.17, `compileOnly`) : `RebornVoiceAddon`, `VoiceState` (micro / casque, bulles), `RadioVoix` (son radio, icône masquée, touche B) |
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
| **F** | Ramasser l'objet au sol visé (sinon F garde son rôle) | **Z** | Dégainer / rengainer l'arme de l'étui |
| **B** (maintenir) | Radio : parler à l'escouade (recopiée dans l'activation « radio » de Plasmo Voice) | **N** | Radio : écran de l'escouade |
| **Entrée / Retour** | Accepter / refuser une invitation (radio d'abord, puis duel, puis ce qu'on me tend) | | |
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
| `REBORN_STAFF_DEBUG=1` (`=vite` : 1920×1080 seul ; `=<cas>,<cas>` : ces cas) | Poste de garde v3 (14/10, `staff/StaffDebug`) : 43 cas — alertes (en attente, traitées, vides, valider une mise à mort), joueurs (liste, survol d'une ligne, menu du clic droit), recherche (« uchi », « gel kazu », hors ligne), fiche (menu Plus, écoute radio, notes, sanctions, confirmation de sanction, comptes, écran noir, suppression à N° retapé), journal (filtré et déplié, menu + Filtre, vide), outils (liste, annonces + confirmation, séisme, chat, commandes, modes), Infirmerie (blessés, zones, nouvelle zone, hôpitaux, réglages), variantes **denses** et effets HUD (annonces, écran noir, plaques, indicateur de mode) — chacun en **1920×1080, 1366×768 et échelle GUI 3** → `run/screenshots/garde_<taille>_<nn>_<cas>.png`. Les demandes partent vers `StaffDemo` (serveur simulé conforme à PLUGINS_REFERENCE §4 bis). Copies : `Maquettes/gameplan/04_staff/v3/code/` |
| `REBORN_INFIRMERIE_DEBUG=1` | Infirmerie seule (ancien banc) |
| `REBORN_PARCHEMIN_DEBUG=1`, `REBORN_CONC_DEBUG=1` | Bibliothèque / lecture / réglages ; concentration |
| `REBORN_FICHE_DEBUG=1` (`=pid` : pastille Player ID seule), `REBORN_PERSO_DEBUG=1` (`=vite`), `REBORN_YEUX_DEBUG=1`, `REBORN_KO_DEBUG=mort` | Fiche shinobi ; sélection / création ; chirurgie des yeux ; scène de mort + menu d'interaction |
| `REBORN_SACOCHE_DEBUG=detail\|porte\|equip\|vide\|lourd`, `REBORN_ESC_SURVOL=<id>`, `REBORN_CARTE_BANC=1`, `REBORN_REGLAGES_ONGLET=<n>` | Variantes du banc d'audit (`REBORN_AUDIT=1`) pour la sacoche, l'Échap, la carte, les réglages |
| `REBORN_INV_DEBUG=1` (`=arme` : l'arme à la ceinture seule ; `=lueur` : lueur de rareté seule, 15/10 — 5 raretés + un Épique caché derrière un muret provisoire, survie puis créatif, sans / avec objet visé, visée réelle en créatif → `run/screenshots/lueur_*.png`, copies dans `Maquettes/gameplan/02_inventaire/captures_v3/` (v2 = contour, rejeté). Suppose un sol plat et dégagé sur 6 blocs devant le point d'apparition) | Inventaire et ramassage (13/10, `audit/InventaireDebug`) : objets au sol des 5 raretés + lueurs + invite « [F] Ramasser » (et « trop lourd »), indicateur de surcharge, pile de notifications, sacoche onglet Équipement (étui, Tête) à 42 % et à 119 %, lecture seule, arme à l'étui sur 4 mannequins (côté, face, dos) → `run/screenshots/inv_*.png` ; copie dans `Maquettes/gameplan/02_inventaire/captures/` |
| `REBORN_PANORAMA="nom:x,y,z,yaw,pitch[,heure[,nuages]];…"` (+ `REBORN_PANORAMA_RD`, `_FOV`, `_WAIT`, `_NUAGES`) | Vues panoramiques sans HUD (13/10, `audit/PanoramaBench`) : spectateur, téléport, heure et beau temps fixés, attente des chunks, capture `run/screenshots/panorama_<nom>.png` puis quitte. Sert au fond de l'écran de connexion du launcher (monde de dev `run/saves/Konoha`, hors dépôt ; ne jamais noter de coordonnées dans les docs) |
| `REBORN_SACOCHE_DEBUG=…,arme,p119,lecture` | Variantes ajoutées au banc d'audit de la sacoche : étui garni + Tête, jauge à 119 %, lecture seule |
| `REBORN_CHAT_DEBUG=1`, `REBORN_COMBAT_DEBUG=1` | Chat ; HUD combat (force le mode RP en solo) |
| `REBORN_ALLURE_DEBUG=1` | Trois allures (14/10, `allure/AllureDebug`) : piste dans le ciel au-dessus du spawn (effacée à la fin, dégâts de chute coupés le temps du banc), caméra de face le temps du banc, marche / trot / sprint / fin de sprint / essoufflé / récupération → `run/screenshots/allure_0*.png` + vitesses mesurées `allure_vitesses.txt` ; endurance simulée (solo, pas de plugin) ; copie dans `Maquettes/gameplan/05_deplacements/` |
| `REBORN_NOURRITURE_DEBUG=1` (+ `REBORN_NOURRITURE_POS="x y z lacet tangage"`, point de vue, jamais noté dans le dépôt) | Nourriture (14/10, `nourriture/NourritureDebug`) : 18 cas — préparer au fourneau, plats prêts, invite « [R] Tendre », menu R (Tendre / Donner des ryo), on me tend (F prendre · Retour refuser), je tends, manger (or pâle dans la barre), repas en pause, plat froid, effet Régénération ×2 (nom 3 s), plusieurs effets, régénération en pause, Affamé, Faim, Fatigue (contrecoup), ration achetée, ration assis, onglet Vivres de l'échoppe → `run/screenshots/nourriture_*.png`, copies dans `Maquettes/gameplan/12_nourriture/code/`. Le joueur de dev (pseudo tiré au hasard, parfois un joueur mort d'un ancien banc : il est ressuscité) passe en créatif et vole le temps du banc ; barre d'action, mode de jeu, heure et météo rendus à la fin, mannequin retiré |
| `REBORN_MONNAIE_DEBUG=1` (+ `REBORN_MONNAIE_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Monnaie et banque (15/10, `monnaie/MonnaieDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2, vue à la première personne (rendues à la fin) — bourse dans la sacoche (survol, « Sur toi »), « Combien veux-tu poser ? », bourse au sol (invite [F]), menu R (Tendre Bourse · 990 ryo), guichet (Déposer, Retirer, Virement, Échange garanti : choisir puis en cours, Coffre et paliers avec un parchemin refusé), fouille (filet 3 s, sacoche du corps, « Prendre dans la bourse »), coffre posé (« Combien veux-tu ranger ? »), Poste de garde (section Économie, formulaire Donner), Échap (bourse, RBCoins) → `run/screenshots/monnaie_*.png`, copies dans `Maquettes/gameplan/19_monnaie/code/`. Le pack de dev `run/resourcepacks/reborn_rouleaux_dev` (copie des provisoires du pack Nexo, bourse comprise) est activé le temps du banc ; barre d'action, mode de jeu, heure, météo, packs, caméra et échelle rendus à la fin ; les envois au serveur sont interceptés |
| `REBORN_RADIO_DEBUG=1` (+ `REBORN_RADIO_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Radio en escouades (14/10, `radio/RadioDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2 (rendue à la fin) — écran N (survol « Exclure », dissolution à confirmer, vu d'un membre, renommer, sans escouade), élément Escouade (silence, Sakura parle, j'émets, toute l'escouade, invitation, à terre « haché », écoute staff, oreillette volée), éditeur de HUD (élément sélectionné, case « Afficher toute l'escouade »), menu R (Ajouter à « Kagerō », Créer une escouade, Oreillette sur un joueur à terre) → `run/screenshots/radio_*.png`, copies dans `Maquettes/gameplan/18_radio/code/`. Têtes RP prêtées par `RebornSkins` (rendues à la fin) ; la voix n'est pas simulée |
| `REBORN_METEO_DEBUG=1`, `REBORN_BYAKUGAN_DEBUG=1` | Météo ; Byakugan |
| `REBORN_AUDIT=1` (+ `REBORN_AUDIT_SCALES`, `REBORN_AUDIT_ONLY`) | Audit responsive : écrans et états du HUD × échelles. Relève aussi le **nombre d'éléments GUI** de chaque capture (DA §6, règle 7 ; `audit/GuiCompte`) dans `run/audit_elements.txt` |
| `REBORN_AUDIT_ONLY=menu_hud,menu_viseur,menu_camera,menu_animations,menu_photo,menu_galerie,menu_signaler,menu_deconnexion,menu_tirage,menu_cosmetique` | Écrans en jeu du lot du 13/10 (dont `menu_galerie_detail` / `_retouche` / `_partage`, `menu_tirage_confirmer` / `_resultat`, `menu_cosmetique`) |
| `REBORN_AUDIT_ONLY=hud_ko,hud_volonte,hud_cooldowns,hud_combat` | HUD discrets : KO (à terre, carte, inconscient, ATA), Volonté (invitation, épreuve), recharges (`hud_cooldowns` : dash, double saut et saut de chakra en recharge + combat et course de chakra forcés par `CooldownState.debugActifs`) |
| `REBORN_AUDIT_ONLY=menu_tirage` | Test de la Feuille refait (14/10) : `menu_tirage_signes`, `_confirmer`, `_concentration`, `_reaction_<nature>` et `_resultat_<nature>` pour les 5 natures, `_reaction_double` (Katon + Fūton), `_resultat_double` (Raiton, Suiton déjà éveillé) ; écran ouvert à une étape précise par `TirageScreen.banc(nature, autres, étape)`. GIF de chaque réaction générés par `tools/gen_tirage_textures.py` → `Maquettes/test_feuille/` |
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
