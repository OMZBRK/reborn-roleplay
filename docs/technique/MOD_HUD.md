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
| `boutique/` | Boutiques-lieux (15/10) : `BoutiqueScreen` (tailleur et bazar, par-dessus le jeu), `CameraEssayage` (caméra d'essayage dans le monde, trois cadrages, repli sur voile), `BoutiqueClient` (canal `reborn:boutique`), `BoutiqueVideScreen` (la BOUTIQUE vide de l'Échap), `BoutiqueDebug` (banc) — [`../gameplay/BOUTIQUES.md`](../gameplay/BOUTIQUES.md) §13 |
| `menu/` | Menu principal, Échap « Tokonoma » (`esc/`), réglages (`settings/`), fiche shinobi « lanternes célestes » (`stats/`), sacoche « inrō » (`inventory/`), ancienne échoppe (`shop/`, plus ouverte par l'Échap depuis le 15/10), tab list (`tablist/`), test de la Feuille (`tirage/`), connexion et chargement (`connect/` : scène « nuit de pleine lune » du trajet vers le serveur, §8), personnages (`character/` + `character/scene/`) |
| `sol/`, `notif/` | Ramassage volontaire (touche F, invite), lueur de rareté au sol (`AuraSol`, v3 du 15/10 : petit halo pixel art `gui/inventaire/lueur.png` 44×11 — 4 images 11×11, halo seul puis 1-2 pixels qui scintillent — généré par `tools/gen_inventaire_textures.py`, teint à la couleur de la rareté, respiration d'alpha ; objets en vue seulement ; sélection par tick, ≤ 48 objets à 16 blocs, un quad par objet en un seul lot `submitCustomGeometry`), rareté client ; ramassage F actif en survie, aventure et créatif ; notifications en bas à droite et état de charge (`reborn:notif`), indicateur de surcharge sous les vitals (13/10) |
| `ko/` | HUD du KO et de l'ATA, QTE Volonté, menu des corps (`CorpsClient`), scène de mort (`DeathScreen`), micro à terre (`MicroATerreHud`, 31/10 : le micro seul, à sa place, quand le KO masque les vitals — §10) |
| `staff/` | Poste de garde v3 « le volet » (14/10) : `StaffScreen` (volet, onglets, alertes, liste, menus), `GardeFiche` (fiche compacte, sections, sanction), `GardeRecherche` (recherche universelle, hors ligne et actions), `GardeJournal` (jetons, jours), `GardeOutils` + `GardeAnnonces` (outils, chat, commandes, modes), `GardeFormulaire` (formulaire dans le volet), `GardeActions` (catalogue des actions et raccourcis), `GardeArt` (palette, icônes `garde/icones.png`, pinceau en lots) ; `GardeScreen` = l'écran ouvert, avec l'Infirmerie dans Outils ; effets staff en jeu (`EffetsStaff` : annonce, écran noir, séisme via `mixin/CameraSeismeMixin`, indicateur du mode staff / animation ; `PlaquesStaff` : plaques du mode staff ; canaux `reborn:annonce`, `reborn:ecran`, `reborn:plaques` par `CanalJson`), serveur simulé du banc (`StaffDemo`) |
| `faction/` | Faction et sections (16/10, canal `reborn:faction`) : `FactionScreen` + `FactionPages` (volet J, maquettes v3), `FactionArt` (kit du volet, atlas `faction/insignes.png` de `tools/gen_faction_textures.py`, têtes RP, capuche des inconnus, vignette de tenue), `FactionHud` (invitation), `FactionClient`, `Insignes` (atlas pour le Poste de garde), banc `FactionDebug` / `FactionDemo` ; outil staff : `staff/GardeFactions` |
| `parchemin/` | Bibliothèque, lecture, réglages staff, concentration du chakra |
| `chat/` | Chat en panneau (onglets Général / RP / Groupe, recherche, mode /me, icônes Lucide) |
| `radio/` | Radio en escouades (14/10, canal `reborn:radio`) : `RadioClient` (état, invitation, qui parle), `RadioScreen` (écran de l'escouade : appui bref sur B), `RadioHud` (élément HUD « Escouade »), `RadioMenus` (menu R : Radio, Oreillette), `RadioDebug` (banc) — [`../gameplay/RADIO.md`](../gameplay/RADIO.md) §12 |
| `voice/` | Plasmo Voice (client 2.1.17, `compileOnly`) : `RebornVoiceAddon` (réglages Reborn de Plasmo Voice, cercle de portée aux couleurs du micro), `VoiceState` (portée de la voix, micro / casque, qui parle ; relu à chaque tick — §10), `RadioVoix` (son radio, icône masquée, touche B) ; au-dessus des têtes (31/10, §9) : `SpeechBubbles` (la bulle, sans fond : voix = 3 barres, frappe = 3 points) et `MeBulles` (/me au-dessus de la tête, sans fond, canal `reborn:me`, `chat/MePayload`) |
| `combat/` | HUD combat : endurance en ensō autour du viseur, chiffres de dégâts, combo, icônes de recharge |
| `map/` | Carte du monde (touche M), intro en nuages |
| `interaction/` | Mode interaction (touche R) : curseur, menus contextuels (joueur, corps, entité, bloc, soi) |
| `camera/`, `screenshot/` | Caméra 3ᵉ personne (touche Vue = F5 ; réglages dans Réglages → Caméra), mode photo (filtres, cadres), galerie (ouverte depuis le mode photo) |
| `nameplate/` | Noms au-dessus des têtes (4 styles au choix), affichés **au regard** depuis le 31/10 (`Nameplates`) ; `CalqueTete` : la pile commune au-dessus de la tête (nom, bulle, /me, plaque staff, invite) ; banc `PlaquesDebug` — §9 |
| `byakugan/`, `meteo/`, `effets/` | Vision Byakugan, météo client (pluie, vent de sable, brume, **blizzard, tempête de sable** — 15/10), filtres plein écran |
| `eau/` | **Marche sur l'eau** (15/10) : `MarcheEauClient` (permis `reborn:eau`, surface solide pour le joueur local via `mixin/BlockStateEauMixin`, accroupi = s'enfoncer, Saut = remonter), `Ondes` (marcheurs détectés sur chaque client ; à chaque pas un **pli de la texture d'eau** autour du pied, gouttelettes, bruit de pas dans l'eau ; **réglages = constantes en tête de la classe**), `EauRendu` (patch de la texture d'eau aux UV déplacés autour d'un contour irrégulier, après l'eau translucide ; l'eau du jeu n'est pas modifiée), `EauDebug` (banc) |
| `meditation/` | **Halo de méditation** (v2 du 18/10, refait sur la vidéo de référence) : `Aura` (forme et teintes mesurées sur la vidéo : disque sombre r 0,48, couronne jusqu'à r 1,1, une couleur d'aura → teinte + lumière de la couronne, voile du disque, étincelles), `MeditationClient` (canal `reborn:meditation` dans les deux sens : méditants à portée + couleur, couleur du personnage actif, choix envoyé au serveur ; fondus ; relevé du sol et de la lumière du lieu sous chacun à chaque tick), `MeditationRendu` (après le terrain translucide, quatre passes sans écriture de profondeur : **disque** translucide éclairé par la lightmap, **couronne** en deux passes émissives qui respirent (teinte translucide puis lumière additive), **étincelles** additives face caméra halo + cœur ; textures fabriquées au premier usage, 512² / 256² / 32², filtrage linéaire — rond et lisse ; un quad par bloc couvert posé sur la forme de collision, décalage de couche de l'ombre du jeu), `MeditationAnimation` (pose **v4** `animations/meditation.json`, faite dans Blender sur le rig Emotecraft et **calée automatiquement sur 8 images de la vidéo** (`tools/emotes/`, voir son README) — assis bas en tailleur, jambes repliées en masse devant le buste, bras croisés haut sur la poitrine, dos rond, tête baissée ; respiration 4 s + balancement 8 s, boucle de 8 s — sur un layer PlayerAnimationLib dédié, priorité 2050, jouée en boucle sur chaque méditant de la liste du halo — soi et les autres, sans synchro de plus —, fondu d'entrée 8 ticks, `meditation_fin.json` en sortie ; les plis genoux/coudes demandent BendableCuboids, présent dans le pack du launcher), `MeditationDebug` (banc ; `REBORN_MEDITATION_DEBUG=pose` : essais `run/med_essai_<k>.json` aux 4 angles calés de la vidéo, corps tenu au sud, + `REBORN_MEDITATION_FILM=1` : film de 8 s de l'essai 0). Choix de la couleur : `menu/character/perso/PersoAura` (Réglages › Interface › Méditation ; réutilise `PersoCouleur` tel quel, « Par défaut » + 5 auras toutes faites, aperçu herbe / pierre / nuit calculé avec les formules d'`Aura`, monde visible derrière : le halo du joueur qui médite change en direct) |
| `skin/` | Compositeur de skins Reborn (`RebornSkins`, `SkinSpec`) |
| `editor/`, `element/`, `config/` | Éditeur de HUD (déplacer / redimensionner / masquer), éléments, presets. Depuis le 19/10 (GAMEPLAN §4) : élément **`EFFETS`** (zone d'effets, `nourriture/EffetsHud`, ×0,80 sous les vitals, visible) et **`BOUSSOLE`** (`boussole/BoussoleHud`, en haut au centre, **masquée par défaut** : `HudElement` porte une visibilité par défaut, et un preset ancien sans l'élément prend ce défaut) ; aperçus d'exemple dans l'éditeur |
| `boussole/` | Boussole horizontale (19/10) : `BoussoleHud` (filet, graduations 15°, N E S O, chevron du cap, marqueur personnel, repères, lieux de la carte à moins de 250 blocs), `Reperes` (API du marqueur personnel §36 et des repères §49), banc `HudQuatreDebug` |
| `nourriture/` | Nourriture (14/10) et zone d'effets : `EffetsHud` (élément `EFFETS` : état du serveur `reborn:etat` — douleur, ATA, prison, menottes, étourdi, gel, Affamé / Faim —, satiété, modificateurs `reborn:modifs`), `EffetsIcones` (icônes `gui/effets/<nom>.png`, `tools/gen_nourriture_textures.py` et `tools/gen_etats_icones.py`), `FaimCamera` (caméra qui tangue à 0 %, `mixin/CameraFaimMixin`, coupée par « Effets de distorsion ») — [`../gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) |
| `emote/`, `animation/` | Emotes EmoteCraft, menu des animations (touche « . » : émotes et démarches), course de chakra (touche X, `allure/Allure`), synchronisation d'animations. **Créer une animation** (émote, pose, démarche) : chaîne Blender + rig officiel d'Emotecraft dans `tools/emotes/` (spec JSON → export au format PlayerAnimationLib lu par le mod, vignettes, calage sur vidéo) — [`../../minecraft/mod-hud/tools/emotes/README.md`](../../minecraft/mod-hud/tools/emotes/README.md) |
| `ui/` | `MenuScale` (taille des menus), `Da` (kit de dessin DA) |
| `audit/` | Bancs de test : `ResponsiveAudit`, `ConcentrationPreview`, `FeaturePreview` |
| `anchor/` | Ancre de tête (31/10, §9) : `TeteAncre` (centre de la tête réellement rendue, capté au rendu par `mixin/LivingEntityRendererTeteMixin` dans la passe des entités bornée par `LevelRendererTeteMixin`), `ProjectionMonde` (projection identique au monde, balancement et nausée compris, via `GameRendererTeteAccessor`), `OcclusionBlocs` (bloc opaque entre la caméra et la tête), banc `TeteAncreBanc` |
| `mixin/` | Tous les mixins (déclarés dans `reborn-hud.mixins.json`) |

## 2. Touches

**Carte réduite du 15/10** (décision du 15/10, [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)) : 15 touches Reborn
au lieu de 26 (16 depuis la portée de la voix, H, le 31/10), sans conflit avec Minecraft ni le modpack. Image récapitulative (ancien → nouveau) :
`Maquettes/gameplan/touches/00_carte_des_touches.png`. Tout reste réglable dans **Réglages → Contrôles** (`ControlsTab` :
la liste ci-dessous dans cet ordre, avec la sacoche, la vue et les allures de Minecraft, puis le menu OST, Plasmo Voice et
le Poste de garde quand ils sont là) et dans Options → Commandes, catégorie « Reborn ».

| Touche | Action | Touche | Action |
|---|---|---|---|
| **E** | Sacoche (touche Inventaire de Minecraft) | **K** | Fiche shinobi |
| **M** | Carte | **R** | Mode interaction |
| **F** | Contextuel : ramasser / exécuter / prendre ce qu'on me tend (sinon F garde son rôle) | **Z** | Dégainer / rengainer l'arme de l'étui |
| **B** | Radio : **maintenir** = parler à l'escouade (recopiée dans l'activation « radio » de Plasmo Voice) ; **appui bref** (< 0,25 s) = écran de l'escouade | **Entrée / Retour** | Accepter / refuser (radio d'abord, puis duel, puis ce qu'on me tend) |
| **.** | Animations : émotes (Reborn et Emotecraft) et démarches ; la même touche referme | **F5** | Vue : épaule ↔ 1ʳᵉ personne (touche « Changer de vue » de Minecraft, détournée en caméra Reborn) |
| **Alt gauche** | Regard libre (maintenir) | **F1** | Mode cinéma |
| **P** | Mode photo ; la **galerie** s'ouvre dedans (bouton « Galerie », ou G) | **G** | Dash de combat |
| **C** | Parade (maintenir) | **Ctrl gauche** | Allures (touche de sprint de Minecraft) : tenir = trot, 2 appuis = sprint (le triple appui ne lance plus la course depuis le 16/10) ([`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md)) |
| **X** | Course de chakra en bascule (15/10 au soir) : un appui la lance, un autre l'arrête ; seule commande de la course depuis le 16/10 | | |
| **N** | Menu OST (`mod-ost`) | **V** | Réglages de Plasmo Voice (touche de Plasmo Voice) |
| **F7** | Poste de garde (staff) | Espace (à terre) / Entrée, Échap (mort) | Volonté (si apprise) / scène de mort |
| **J** | Volet Faction (16/10) : ta section, ton village, les annonces ; la même touche le referme. Libre dans Minecraft et le modpack, même lettre en AZERTY / QWERTZ (aucune migration) | **Entrée / Retour** | Aussi : accepter / refuser une invitation dans une section (après la radio, avant le duel) |
| **H** | Portée de la voix (31/10, §10) : fait tourner les portées de proximité du serveur Plasmo Voice, **un cran plus bas** à chaque appui (normale → chuchotée → criée → normale : chuchoter tient en un appui, baisser la voix ne passe plus par la plus grande portée), annoncée en bas à droite (« Voix : chuchotée ») ; le micro des vitals en prend la couleur (vert, jaune, rouge). À terre : refusée (chuchoter seulement) ; voix refusée par l'état (mort RP, cinématique…) : refusée avec la raison du serveur. Libre dans Minecraft 26.2 (seulement F3 + H, raccourci de débogage), dans Plasmo Voice (Alt gauche, M, V) et le modpack ; même lettre en AZERTY / QWERTZ | | |

**Sans touche** depuis le 15/10 : éditeur de HUD et viseur → Échap → **INTERFACE** (bouton « Personnaliser le viseur » au
pied de l'éditeur) ; menu personnage → Échap, **carte d'identité** (nom et village, sous le logo) ; caméra (style Reborn /
Minecraft, épaule, préréglage, repositionner) → **Réglages → Caméra** ; galerie → mode photo ; écran de l'escouade → B bref ;
course de chakra → touche **X** (bascule, rajoutée le 15/10 au soir ; le triple appui sur le sprint est retiré le 16/10 à la demande de l'utilisateur).

**Migration** (`keybind/TouchesMigration`, une fois, drapeau `touchesVersion` de `reborn_prefs.json` : 1 = nouvelle carte,
2 = disposition du clavier, ci-dessous, 3 = portée de la voix, plus bas) : une touche dont la
valeur sauvegardée est encore l'**ancien défaut** reçoit le nouveau, une touche personnalisée n'est jamais touchée — menu
OST M → N, dash V → G, roue d'Emotecraft (« fastchoose ») B → aucune, Minecraft « Actions rapides » G → aucune et
« Interactions sociales » P → aucune (conflits avec le dash et le mode photo) ; côté Plasmo Voice (`voice/RadioVoix`,
drapeau `touchesPv`), « couper le micro » M → aucune si la carte est sur M (le micro se coupe dans le menu V). Les
touches Reborn supprimées disparaissent d'`options.txt`. Conflits restants, voulus : F (ramasser / main secondaire, pris
seulement si un objet est visé), F1 (cinéma / masquer l'ATH, le F1 vanilla est neutralisé), C (parade / « sauver la
barre » du créatif, C + chiffre), X (course de chakra / « charger la barre » du créatif, X + chiffre) ; Alt gauche = aussi le push-to-talk de proximité de Plasmo Voice **si** le joueur
passe sa voix de proximité en push-to-talk (par défaut : activation à la voix).

**Claviers AZERTY / QWERTZ** (migration version 2, 15/10) : les défauts de Minecraft sont des *positions* de touche
(noms US) ; en AZERTY, la carte (M) s'affichait « , » et les animations (.) « : ». Au premier lancement, chaque touche
Reborn restée sur son défaut (et le menu OST) passe sur la touche qui **affiche** la même lettre (`glfwGetKeyName`,
`TouchesMigration#cible`, si elle est libre) : **AZERTY** — Carte → la touche qui affiche « M » (US « ; »), Animations →
la touche gravée « ; . » (US « , », affichée « ; » : « . » n'existe qu'avec Maj), Dégainer **reste** à la place du Z
QWERTY, affichée « W » (la touche « Z » est Avancer en ZQSD) ; E, K, R, F, B, G, C, P, N, V, Entrée, Alt, F1… ne bougent
pas. **QWERTZ** — Dégainer → la touche qui affiche « Z » (US Y). **QWERTY** : rien. Les touches de Minecraft (sprint
Ctrl, accroupi Maj) ne dépendent pas de la disposition. Une touche personnalisée n'est jamais touchée ; « Réinitialiser »
dans les Commandes de Minecraft remet le défaut US. Les invites et aides affichent le **nom réel** de la touche
(`HudKeybinds#nom`) : fiche (K), carte (M), galerie (G), sacoche (`/rpinv`), « touche Q bloquée » de la sacoche.

**Portée de la voix sur H** (migration version 3, 31/10) : chez les joueurs déjà migrés, la règle de la disposition
s'applique à cette seule touche (en AZERTY, QWERTZ et QWERTY, H affiche « H » : rien ne bouge). H n'est **jamais prise**
à une autre liaison : si le joueur l'a déjà donnée à autre chose, le conflit reste, Minecraft le montre dans les Commandes
et le journal le note (`reborn-hud/touches`). Aucune autre touche Reborn n'a H par défaut, et la version 2 ne pose une
touche que sur une touche libre (la portée passe avant la parade dans la carte). Seul cas connu : un clavier **BÉPO**
migré avant le 31/10, dont la parade a pu passer sur la touche US H (elle y affiche « c ») ; la portée lui cède alors la
place (aucune touche, à lier dans Réglages → Contrôles) plutôt que de déclencher deux touches Reborn à la fois.

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
| `REBORN_PERSO_DEBUG=1` (`vite` : 1080p seul ; `coupes` : coiffures et sourcils, 18/10, + `REBORN_CREATOR_ASSETS=<copie de creator-assets>` qui rejoue la diffusion live du serveur) | Sélection et création « au bord de l'étang » : roster des maquettes, 8 étapes, 6 villages, échelles 3 et 4, 1366×768, 1706×960 ; `coupes` → `perso_10*` (grille « Tout voir », coupes live, arrivée écran ouvert) et `perso_11*` (sourcils), voir [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) §4 bis (`perso/PersoDebug.java`) |
| `REBORN_SLIM_DEBUG=<étiquette>` (+ `REBORN_CREATOR_ASSETS=<dossier>` : rejoue un asset live, ex. une tenue peinte en 4 px) | Carrure fine pour tous (18/10, `perso/SlimDebug`) : dalle dans le ciel (rendue à la fin), perso Homme composé sans tenue puis avec tenues, mannequin « autre joueur » à côté ; face, profil, dos, bras de ¾, duo ; main à la 1re personne ; Test de la Feuille (confirmation, canalisation) ; création (Sexe, Apparence, Récapitulatif) → `run/screenshots/slim_<étiquette>_*.png`, copies dans `Maquettes/gameplan/homme_slim/`. Validé avec le pack Sodium copié le temps de l'essai |
| `REBORN_SCENE=1` / `journey` | Ancienne sélection / création en scène 3D (repli `-Dreborn.sceneCharacterScreens=true`) |
| `REBORN_KO_DEBUG=1` | Toutes les phases du KO et de l'ATA |
| `REBORN_STAFF_DEBUG=1` (`=vite` : 1920×1080 seul ; `=<cas>,<cas>` : ces cas ; `=vite:<cas>,<cas>` : ces cas en 1920×1080 seul) | Poste de garde v3 (14/10, `staff/StaffDebug`) : 55 cas — alertes (en attente, traitées, vides, valider une mise à mort), joueurs (liste, survol d'une ligne, menu du clic droit), recherche (« uchi », « gel kazu », hors ligne), fiche (menu Plus, écoute radio, notes, sanctions, confirmation de sanction, comptes, écran noir, suppression à N° retapé), journal (filtré et déplié, menu + Filtre, vide), outils (liste, annonces + confirmation, séisme, chat, commandes, modes), Infirmerie (blessés, zones, nouvelle zone, hôpitaux, réglages), variantes **denses**, effets HUD (annonces, écran noir, plaques, indicateur de mode) et, depuis le 15/10, la section **Personnages** de la fiche (`persos`, `persos_detail`, `persos_identite`, `persos_grade`, `persos_mort`, `persos_suppr`, `persos_creer`, `persos_emplacement`, `persos_refus`, `persos_hors_ligne` ; copies `Maquettes/gameplan/04_staff/personnages/`), et depuis le 31/10 la **voix** : `fiche_voix` (menu R › Staff › « Couper la voix… » : le poste s'ouvre sur la fiche puis le formulaire) et `fiche_voix_coupee` (formulaire validé, fiche « Voix coupée — jusqu'à… », réponse du serveur simulé, menu Plus sur « Rétablir la voix ») — chacun en **1920×1080, 1366×768 et échelle GUI 3** → `run/screenshots/garde_<taille>_<nn>_<cas>.png`. Les demandes partent vers `StaffDemo` (serveur simulé conforme à PLUGINS_REFERENCE §4 bis). Copies : `Maquettes/gameplan/04_staff/v3/code/` |
| `REBORN_FACTION_DEBUG=1` (`=<cas>,<cas>` : ces cas) | Faction (16/10, `faction/FactionDebug`, serveur simulé `FactionDemo` au format du canal `reborn:faction`, têtes RP prêtées) : 21 captures, 1920×1080 échelle 2 sauf mention — volet J d'un joueur sans droit (Village, Ma section), chef de section (survol « Gérer › »), Inviter depuis le panneau, Grades de section + fiche d'un membre, Gestion du Hokage, Créer une section, Sections d'un Jōnin (ANBU = « ? ») et du Hokage, Annonces, Publier une annonce, Exclure, Tenue (grille des tenues), Charges, invitation au HUD, menu R « Inviter dans la Police », Poste de garde › Outils › Factions (liste, fiche de Konoha, Créer une faction), échelle GUI 3, 1366×768 → `run/screenshots/faction_<nn>_<cas>.png` ; copies et planche maquettes / code dans `Maquettes/gameplan/37_faction/code/`. Validé avec le pack Sodium copié le temps de l'essai |
| `REBORN_CLOCHE_DEBUG=1` · `REBORN_VOLIERE_DEBUG=1` (`=<cas>,<cas>` : ces cas) | Cloche du village et volières (16/10, `communication/CommDebug`, serveur simulé au format du canal `reborn:communication`, données des maquettes v3c, têtes RP prêtées) : cloche — `rediger` (01), `bande_normale` / `bande_importante` / `bande_danger` (02), `bande_fondu` (annonce en fondu + alerte empilée), `chef_section`, `menu_r`, `echelle3`, `petit` (1366×768), `sans_delai` (10, Commandant Jōnin au seuil « Sonner sans délai » : pied « sans délai pour ton grade ») ; volière — `ecrire` (03), `ajouter` (« + Ajouter » ouvert), `missives` (04, survol Lire / Prendre), `lecture` (05), `alerte_missive`, `alerte_palais` (06), `transfert`, `menu_r`, `vide` → `run/screenshots/cloche_<nn>_<cas>.png`, `voliere_<nn>_<cas>.png` ; copies et planche maquettes / code dans `Maquettes/gameplan/37_faction/code_cloche_voliere/`. Validé avec le pack Sodium copié le temps de l'essai |
| `REBORN_INFIRMERIE_DEBUG=1` | Infirmerie seule (ancien banc) |
| `REBORN_PARCHEMIN_DEBUG=1`, `REBORN_CONC_DEBUG=1` | Bibliothèque / lecture / réglages ; concentration |
| `REBORN_FICHE_DEBUG=1` (`=pid` : pastille Player ID seule), `REBORN_PERSO_DEBUG=1` (`=vite`), `REBORN_YEUX_DEBUG=1`, `REBORN_KO_DEBUG=mort` | Fiche shinobi ; sélection / création ; chirurgie des yeux ; scène de mort + menu d'interaction |
| `REBORN_SACOCHE_DEBUG=detail\|porte\|equip\|vide\|lourd`, `REBORN_ESC_SURVOL=<id>`, `REBORN_CARTE_BANC=1`, `REBORN_REGLAGES_ONGLET=<n>` | Variantes du banc d'audit (`REBORN_AUDIT=1`) pour la sacoche, l'Échap, la carte, les réglages |
| `REBORN_INV_DEBUG=1` (`=arme` : l'arme à la ceinture seule ; `=lueur` : lueur de rareté seule, 15/10 — 5 raretés + un Épique caché derrière un muret provisoire, survie puis créatif, sans / avec objet visé, visée réelle en créatif → `run/screenshots/lueur_*.png`, copies dans `Maquettes/gameplan/02_inventaire/captures_v3/` (v2 = contour, rejeté). Suppose un sol plat et dégagé sur 6 blocs devant le point d'apparition) | Inventaire et ramassage (13/10, `audit/InventaireDebug`) : objets au sol des 5 raretés + lueurs + invite « [F] Ramasser » (et « trop lourd »), indicateur de surcharge, pile de notifications, sacoche onglet Équipement (étui, Tête) à 42 % et à 119 %, lecture seule, arme à l'étui sur 4 mannequins (côté, face, dos) → `run/screenshots/inv_*.png` ; copie dans `Maquettes/gameplan/02_inventaire/captures/` |
| `REBORN_PANORAMA="nom:x,y,z,yaw,pitch[,heure[,nuages]];…"` (+ `REBORN_PANORAMA_RD`, `_FOV`, `_WAIT`, `_NUAGES`) | Vues panoramiques sans HUD (13/10, `audit/PanoramaBench`) : spectateur, téléport, heure et beau temps fixés, attente des chunks, capture `run/screenshots/panorama_<nom>.png` puis quitte. Sert au fond de l'écran de connexion du launcher (monde de dev `run/saves/Konoha`, hors dépôt ; ne jamais noter de coordonnées dans les docs) |
| `REBORN_SACOCHE_DEBUG=…,arme,p119,lecture` | Variantes ajoutées au banc d'audit de la sacoche : étui garni + Tête, jauge à 119 %, lecture seule |
| `REBORN_CHAT_DEBUG=1`, `REBORN_COMBAT_DEBUG=1` | Chat ; HUD combat (force le mode RP en solo) |
| `REBORN_PARADE_DEBUG=1` (`=video` : temps réel, sans film image par image) | Garde et parade (18/10, `combat/ParadeDebug`, lancé par `CombatDebug.init`) : aire dégagée dans le ciel au-dessus du point d'apparition (retirée à la fin), mains nues, deux mannequins « autres joueurs » (de face, de profil) qui reçoivent les anims par le chemin réseau (`CombatAnimations.playByEntityId`) ; la touche Parade est tenue par le banc (vrai chemin `CombatInput.tick`) ; garde tenue (épaule, face, profil par le regard libre), relâche en fondu, parade réussie simulée (`CombatState.onParry(0)` + anim 7 aux mannequins) avec tape courte, reprise de la marche → `run/screenshots/parade_*.png` ; état du layer combat journalisé (`[parade-banc]`, marques horodatées des séquences). En `=1`, monde à 5 ticks/s et films `pfilm_<cas>_<nnn>.png` (une image par tick ; à 20 ticks/s les captures font chuter les FPS et les images se répètent). Pour un MP4 fidèle : `=video` et filmer la fenêtre « 26.2* Reborn » avec `ffmpeg -f gdigrab -i title="26.2* Reborn"`. Copies et MP4 : `Maquettes/gameplan/parade/`. Validé avec le pack Sodium copié le temps de l'essai (BendableCuboids compris : avant-bras pliés) |
| `REBORN_ALLURE_DEBUG=1` | Trois allures (14/10, `allure/AllureDebug`) : piste dans le ciel au-dessus du spawn (effacée à la fin, dégâts de chute coupés le temps du banc), caméra de face le temps du banc, marche / trot / sprint / fin de sprint / essoufflé / récupération → `run/screenshots/allure_0*.png` + vitesses mesurées `allure_vitesses.txt` ; endurance simulée (solo, pas de plugin) ; copie dans `Maquettes/gameplan/05_deplacements/` |
| `REBORN_HUD4_DEBUG=1` (+ `REBORN_HUD4_MP4=<fichier.mp4>` : film ffmpeg de 6 s de la caméra qui tangue, fenêtre `REBORN_HUD4_TITRE`, « 26.2* Reborn » par défaut ; `REBORN_HUD4_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | HUD du Game Plan §4 (19/10, `boussole/HudQuatreDebug`) : 10 captures en 1920×1080 à l'échelle GUI 2 — boussole activée (marqueur personnel sur le côté, lieu proche regardé avec son nom ; marqueur regardé avec sa distance), zone d'effets (cinq effets, noms à l'apparition puis compacte avec minuteurs), prison + menottes + douleur, faim à 15 % (Affamé + alerte), faim à 0 % (FAIM + alerte, caméra qui tangue), éditeur de HUD (Effets puis Boussole sélectionnés) → `run/screenshots/hud4_*.png`, copies et MP4 dans `Maquettes/gameplan/04_hud/`. Réglages des éléments, échelle, heure et mode de jeu rendus à la fin. Validé avec le pack Sodium copié le temps de l'essai |
| `REBORN_NOURRITURE_DEBUG=1` (+ `REBORN_NOURRITURE_POS="x y z lacet tangage"`, point de vue, jamais noté dans le dépôt) | Nourriture (14/10, `nourriture/NourritureDebug`) : 18 cas — préparer au fourneau, plats prêts, invite « [R] Tendre », menu R (Tendre / Donner des ryo), on me tend (F prendre · Retour refuser), je tends, manger (or pâle dans la barre), repas en pause, plat froid, effet Régénération ×2 (nom 3 s), plusieurs effets, régénération en pause, Affamé, Faim, Fatigue (contrecoup), ration achetée, ration assis, onglet Vivres de l'échoppe → `run/screenshots/nourriture_*.png`, copies dans `Maquettes/gameplan/12_nourriture/code/`. Le joueur de dev (pseudo tiré au hasard, parfois un joueur mort d'un ancien banc : il est ressuscité) passe en créatif et vole le temps du banc ; barre d'action, mode de jeu, heure et météo rendus à la fin, mannequin retiré |
| `REBORN_MONNAIE_DEBUG=1` (+ `REBORN_MONNAIE_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Monnaie et banque (15/10, `monnaie/MonnaieDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2, vue à la première personne (rendues à la fin) — bourse dans la sacoche (survol, « Sur toi »), « Combien veux-tu poser ? », bourse au sol (invite [F]), menu R (Tendre Bourse · 990 ryo), guichet (Déposer, Retirer, Virement, Échange garanti : choisir puis en cours, Coffre et paliers avec un parchemin refusé), fouille (filet 3 s, sacoche du corps, « Prendre dans la bourse »), coffre posé (« Combien veux-tu ranger ? »), Poste de garde (section Économie, formulaire Donner), Échap (bourse, RBCoins) → `run/screenshots/monnaie_*.png`, copies dans `Maquettes/gameplan/19_monnaie/code/`. Le pack de dev `run/resourcepacks/reborn_rouleaux_dev` (copie des provisoires du pack Nexo, bourse comprise) est activé le temps du banc ; barre d'action, mode de jeu, heure, météo, packs, caméra et échelle rendus à la fin ; les envois au serveur sont interceptés |
| `REBORN_BOUTIQUE_DEBUG=1` | Boutiques-lieux (15/10, `boutique/BoutiqueDebug`) : 12 captures en 1920×1080 à l'échelle GUI 2 — une petite échoppe de tailleur est construite dans le ciel au-dessus du joueur (blocs relevés puis rendus), le personnage est placé sur l'estrade : comptoir des vêtements sur « Hauts » (vide), essai, de dos, cadrage visage, confirmation d'achat, après l'achat, comptoir des masques (essai du masque 3D), condition grisée + info-bulle, bazar, repli sur voile (mur devant la caméra), carte BOUTIQUE de l'Échap, écran Boutique vide → `run/screenshots/boutique_*.png`, copies dans `Maquettes/gameplan/42_boutiques/code/`. Pack de dev `run/resourcepacks/reborn_boutique_dev` (modèles Nexo du masque Hannya, du chapeau, des rouleaux ; hors dépôt) activé le temps du banc ; envois au serveur interceptés |
| `REBORN_FPS_DEBUG=1` · `=hud` (+ `REBORN_FPS_RD`, `REBORN_FPS_POS="nom:x y z lacet tangage;…"` jamais noté dans le dépôt, `REBORN_FPS_ORDRE=inverse`) | Banc de FPS (24/10, `audit/FpsDebug`) : sans limite ni VSync, limite « AFK » coupée ; `=1` : quatre directions depuis l'apparition (ou les vues données), 8 s avec HUD puis 8 s sans, entités rendues par type ; `=hud` : HUD complet (RP forcé), HUD masqué, vanilla seul, puis chacun de nos 30 éléments seul (`me-bulles` et `micro-a-terre` compris depuis le 31/10) → `run/fps_banc.txt` + `fps_*.png`. **À lancer avec le pack du launcher copié dans `run/mods`** (Sodium, Sodium Extra, Lithium, EntityCulling, ETF, YACL, Kotlin, BendableCuboids), retiré ensuite. **Fenêtre au premier plan et PC non utilisé** : en arrière-plan, Windows / le pilote brident à ~135 i/s et faussent tout. Mesure du 24/10 (monde de dev presque vide, 1080p) : 320-400 i/s, notre HUD ≈ 0,7-0,9 ms par image en tout, aucun élément coûteux à lui seul. Profil CPU : `JAVA_TOOL_OPTIONS=-XX:StartFlightRecording=filename=…,settings=profile` puis `jfr print --stack-depth 80` (fil de rendu = `main`) |
| `REBORN_PLAQUES_DEBUG=1` | Au-dessus des têtes (31/10, `nameplate/PlaquesDebug`, §9) : vue épaule Reborn (RP forcé), 1920×1080, échelle GUI 2, **horloge des fondus imposée** (`Nameplates.horloge`, films image par image, chaque capture attend 2 images rendues), cinq mannequins nommés (`Nameplates.DEBUG_NOMS`) à ~3,5 blocs — debout, debout derrière, à terre (ramper + phase 1), allongé sans lit (phase 2), accroupi ; 56 captures `run/screenshots/plaques_<nn>_<cas>.png` : aucun regard ; nom au regard, fondu entrant à 0 / 80 / 160 / 240 ms, plaque pleine, maintien 1 s puis fondu sortant à 0 / 150 / 300 / 450 ms ; 1ʳᵉ personne ; accroupi (aucun nom) ; à terre et inconscient avec la croix de l'ancre (`TeteAncre.debugCroix` : sur la tête, pas au-dessus des pieds ; écart tête – pieds journalisé, 1,64 bloc allongé) ; émote (`CombatAnimations` sur le mannequin : garde tenue, coup avec la croix) ; voix (3 barres) puis frappe (3 points) **sans nom** (le regard quitte la cible avant que l'horloge n'avance) ; /me court à 0 / 60 / 150 / 300 / 600 ms, /me long seul (3 lignes puis « … ») ; pile complète (nom, barres, deux /me) sur le ciel de jour ; masqué (« ??? ») ; /me à terre ; **fond clair** (1ʳᵉ personne, 3 blocs plus haut, la neige derrière la pile ; frappe) et **nuit** ; auteur d'un /me qui parle, **caché derrière** un mannequin plus proche (posé dans l'axe caméra → mannequin, 3ᵉ puis 1ʳᵉ personne, sans puis avec le nom de celui de devant ; attendu : ni bulle ni /me sur celui de devant — alignement et « tête de derrière cachée par un corps : oui » journalisés) ; **nom en maintien, un autre passe devant** (`21a_nom_maintenu_devant`, 1ʳᵉ personne : le regard part 20° à côté, le mannequin de derrière vient 1,5 bloc devant celui qu'on a regardé, silencieux ; attendu : rien sur son visage — le nom suit `teteDegagee`, corps compris, depuis le 01/11) ; **orateur à 12 blocs** à l'écart (`21b_voix_12_blocs` : la voix de proximité garde sa bulle, atténuée vers 80 % ; `21c_frappe_12_blocs` : la frappe n'en a pas ; posé à droite, loin de l'axe caméra → mannequin, et maintien du nom précédent écoulé : sinon un nom s'affiche sur la tête du joueur) ; **auteur devant soi en vue épaule** (le mannequin qui parle et fait un /me est posé dans l'axe caméra → joueur, noms coupés le temps des deux cas ; `21d_derriere_soi_depasse` : à 3 blocs, le haut de sa tête dépasse, bulle et /me posés au-dessus, pas sur la tête du joueur ; `21e_derriere_soi_cache` : à 1 bloc, centre de sa tête caché par le corps du joueur, ni bulle ni /me — son propre corps compte en 3ᵉ personne) ; soi en 3ᵉ personne ; micro des vitals vert / jaune / rouge / qui parle (`VoiceState.DEBUG_PORTEE`, `DEBUG_SOI_PARLE`) ; micro à terre (phase KO 1 du joueur : vitals masqués, micro seul « chuchoté ») ; **marche** au sol avec balancement de vue (touche Avancer tenue, 4 captures à 2 ticks d'écart : l'écart nom – tête passe de 11-12 à 14 px en approchant de ~1,3 bloc, sans osciller avec le balancement) ; petit écran 1366×768 (`Window.setWindowed`) ; échelle 3 ; mur (tout masqué) ; styles Filet, Village, Ruban. Journal `[plaques-banc]` (origine, chaque photo avec la cible retenue, ancres des corps au sol, alignement des cas cachés avec la valeur attendue, distance de l'orateur lointain, position finale). Dernier passage : 01/11, après les correctifs du 31/10 au soir, 56 captures, aucun mixin en échec au lancement. À la fin : mannequins tués (`kill` : envoyé sous le monde, un mannequin y survit plusieurs secondes), drapeaux, style, échelle, fenêtre, balancement, caméra, mode de jeu, **heure et météo d'origine**, joueur remis à sa **position d'origine en absolu**, remonté au premier 1/16 de bloc libre si ses pieds touchent un bloc (le heightmap `motion_blocking` ignore les couches de neige : dans « New World », les pieds du joueur de dev étaient 4/16 dans `snow[layers=3]` ; la base du banc est relevée de même). Le mur provisoire (béton gris) n'est posé que sur de l'air puis retiré. Voix et frappe simulées par `VoiceState.DEBUG_PARLE` / `DEBUG_SOI_PARLE` et `TypingState.update` ; au départ, les restes d'un banc interrompu sont tués et le husk sans IA que laisse `REBORN_COMBAT_DEBUG` est envoyé sous le monde. À lancer par Git Bash comme ci-dessus : sous Windows PowerShell 5.1, `--args='… "New World" …'` passé à `gradlew.bat` est coupé à l'espace (« Task 'World …' not found ») |
| `REBORN_VOIX_DEBUG=1` | Voix (31/10, banc imbriqué `ko/MicroATerreHud.Banc`, serveur vocal intégré de Plasmo Voice en solo ; le monde n'est pas touché) : réglages Reborn de Plasmo Voice vérifiés (icônes au-dessus des têtes masquées, touche « Action » déliée), micro des vitals vert / jaune / rouge (`VoiceState.DEBUG_PORTEE`), « je parle » (deux phases de la pulsation), coupé, assourdi ; la **vraie** touche « Portée de la voix » (chemin du clavier par réflexion) fait un tour complet des portées du serveur intégré, un cran plus bas à chaque appui (16 → 8 → 32 → 16), notification et cercle de portée ; à terre (phase KO 1 simulée) : micro seul « chuchoté », touche refusée, portée inchangée → « [voix] OK / ECHEC » dans le journal et `run/voix_banc.txt`, captures `run/screenshots/voix_01..13_*.png` ; échelle GUI et portée de départ rendues à la fin. Le `client.toml` de Plasmo Voice du dossier de dev garde les deux réglages Reborn (voulu) |
| `REBORN_TETE_ANCRE_DEBUG=banc` (`=1` ou `-Dreborn.teteAncreDebug=true` : croix seules, en jeu) | Ancre de tête (31/10, `anchor/TeteAncreBanc`, §9) : quatre mannequins (debout, accroupi, à terre « ramper », allongé sans lit), croix au centre de la tête (vert : capturée, or : recalée, rouge : estimée ; pâle si masquée), trait au sommet du chapeau et, en gris, l'ancien ancrage (œil + 0,62) ; vue de face, épaule (soi compris), autre angle, plongée, puis en 1re personne un mur de pierre (croix pâle) et une vitre (croix vive : le verre ne masque pas) → `run/screenshots/ancre_tete_0*.png`. Prouve aussi que les mixins de la capture s'appliquent au lancement. Vol en créatif, murs posés un bloc au-dessus du sol puis retirés : le monde n'est pas modifié |
| `REBORN_INTERACTION_DEBUG=1` | Menu R du Game Plan §14 (24/10, `interaction/InteractionDebug`) : mannequin « autre joueur » décalé à droite (la caméra épaule cache ce qui est droit devant), 10 captures en 1920×1080 — menu d'une personne consciente (survol « Porter (avec son accord) »), « Volume de sa voix » (75 % actuel), sous-menu Staff (staff imposé : « Couper la voix… », puis `03b` « Rétablir la voix » sur une voix coupée, 31/10), demandes « veut te porter » / « veut te fouiller », porté de plein gré (« Espace · descendre »), fouille consentie en lecture seule (personne fouillée à gauche), plaque connue puis masque Hannya → « ??? » (vue à la 1ʳᵉ personne, pack `reborn_boutique_dev`) → `run/screenshots/interaction_*.png`, copies dans `Maquettes/gameplan/14_interaction/code/`. Mode de jeu, heure, météo, caméra et pack rendus à la fin |
| `REBORN_DOUANE_DEBUG=1` | Douane, lot E (25/10, `douane/DouaneDebug` + `staff/GardeDouaneDebug`) : rejoue des messages `reborn:douane` au format du protocole v1 (PLUGINS_REFERENCE §4 decies.4 à .6, demandes C → S interceptées), mannequin « autre candidat » devant la caméra ; 21 captures en 1920×1080 à l'échelle GUI 2 puis en 1366×768 — hall (ligne, rappel des règles, plaque du pseudo), hall fermé, vérification de l'accès, appel et fondu, bureau (plaque « Examinateur »), question courte et longue, Bienvenue, À revoir, Refusé, résultat différé, Poste de garde › Douane (file, survol « Appeler »), dossier en volet élargi, questions et décision, envoi en attente, retrait, indicateur du mode examinateur → `run/screenshots/douane_*.png`, copies dans `Maquettes/gameplan/candidature/jeu_reel/`. Mode de jeu, heure, météo, échelle et fenêtre rendus à la fin |
| `REBORN_RADIO_DEBUG=1` (+ `REBORN_RADIO_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Radio en escouades (14/10, `radio/RadioDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2 (rendue à la fin) — écran de l'escouade (survol « Exclure », dissolution à confirmer, vu d'un membre, renommer, sans escouade), élément Escouade (silence, Sakura parle, j'émets, toute l'escouade, invitation, à terre « haché », écoute staff, oreillette volée), éditeur de HUD (élément sélectionné, case « Afficher toute l'escouade »), menu R (Ajouter à « Kagerō », Créer une escouade, Oreillette sur un joueur à terre) → `run/screenshots/radio_*.png`, copies dans `Maquettes/gameplan/18_radio/code/`. Têtes RP prêtées par `RebornSkins` (rendues à la fin) ; la voix n'est pas simulée |
| `REBORN_METEO_DEBUG=1`, `REBORN_BYAKUGAN_DEBUG=1` | Météo ; Byakugan |
| `REBORN_METEO_DEBUG=tempetes` | Blizzard et tempête de sable (15/10, `meteo/MeteoDebug`) : sol dégagé cherché par le serveur intégré près du point d'apparition, mannequins à 4, 8, 12 et 18 blocs ; clair, blizzard 75 % / 100 % / 3ᵉ personne / nuit / 40 %, tempête de sable 75 % / 100 % / 3ᵉ personne / soir, entrées et sorties en images de GIF → `run/screenshots/meteo_t*.png`, `metgif_*.png` ; copies et GIF dans `Maquettes/gameplan/eau_meteo/`. Remet position, heure et caméra |
| `REBORN_EAU_DEBUG=1` | Marche sur l'eau (15/10, `eau/EauDebug`, permis forcé, allures simulées) : mer ouverte **profonde** cherchée par le serveur intégré (fond à 10 blocs, sinon 3), joueur tenu en l'air tant que l'eau n'est pas arrivée chez le client puis posé dessus ; immobile, marche, trot, sprint (dos), de face, 1ʳᵉ personne (regard vers le bas en avançant, puis à reculons pour voir la traînée de flaques, comme la référence Shinoda), saut et retombée, s'enfoncer (accroupi), remonter (Saut), un mannequin qui traverse (« autre joueur ») ; retour au large avant la 1ʳᵉ personne, le saut et l'autre joueur ; angles tenus à chaque tick et aucun écran (la souris ou une perte de focus ne dérangent pas le banc) ; **films « 60 i/s »** : pendant la marche, le sprint et la vue vers le bas, le monde passe à `/tick rate 1` et chaque image rendue est capturée (fin du rendu du monde, sans HUD, demi-définition) → `eaufilm_*` à remonter en MP4 60 i/s (l'horloge des ondes suit alors le temps du monde) → `run/screenshots/eau_*.png` + images de GIF `eaugif_*` ; coût CPU du rendu dans le log (`[eau-banc]`). Monde **« New World »** (Konoha n'a pas de mer). Flaques : `Maquettes/gameplan/eau_v8/` (v7 : `eau_v7/`, v6 : `eau_v6/`, v5 : `eau_v5/`, v4 : `eau_v4/`, v3 : `eau_v3/`, v1 : `eau_meteo/`) |
| `REBORN_MEDITATION_DEBUG=1` | Halo de méditation (v2 du 18/10, `meditation/MeditationDebug`, halos allumés localement, sans plugin) : trois sols d'essai construits à 600 blocs à l'est (herbe, pierre, pente en escalier + dalle) passés en jungle clairsemée (l'herbe vive de la vidéo), joueur assis sur un support invisible (comme `MeditationManager`) et mannequin assis plus loin (« autre joueur », aura violette) ; aux angles de la vidéo (dessus ¾, dos ¾, face) : herbe, pierre, nuit, auras rouge / bleue / or (pierre, nuit), pente, disparition, puis l'écran de choix → `run/screenshots/med2_*.png` (fin du rendu du monde, sans HUD) ; **film de 8 s** : 240 images `medfilm_*` à 30 i/s, horloge de l'effet imposée, caméra qui tourne → MP4 ; coût CPU dans le log (`[med-banc]`). Monde « New World ». Captures, MP4 et planche vidéo / Reborn : `Maquettes/gameplan/meditation/v2/` |
| `REBORN_MEDITATION_DEBUG=pose` (+ `REBORN_MEDITATION_FILM=1`) | Pose de méditation (v4 du 18/10) : même terrain et même siège ; chaque `run/med_essai_<k>.json` (fichier exporté par `tools/emotes/`) est joué sur soi et sur le mannequin, capturé aux **4 angles calés de la vidéo** (face, dessus ¾, dos ¾, profil gauche : caméra de 3ᵉ personne vanilla à 4 blocs, FOV 70, corps tenu au sud) → `medpose_<k>_{face,dessus,dos,cote}.png`, angles réels journalisés (`[med-pose]`) ; avec `FILM=1`, film de 8 s de l'essai 0 (monde à 3 ticks/s, une capture par tick, horloge du halo imposée → `medposefilm_000..159`, MP4 à 20 i/s = temps réel ; 4 s de dos puis 4 s de face : au-delà de ±45° entre regard et corps, Minecraft tourne le corps avec la tête). Avec BendableCuboids + pack Sodium copiés le temps de l'essai. Planche vidéo / jeu et MP4 : `Maquettes/gameplan/meditation/v4/` |
| `REBORN_TEINTURE_DEBUG=1` | Cosmétiques de tête v3 (18/10, `cosmetic/TeintureDebug`, sacoche simulée ; pack de dev `reborn_boutique_dev` : copies du pack Nexo — bandeau `nexo:bandeau_ninja` teintable, `reborn:masque_hannya`, `reborn:akatsuki_hat`, `reborn:oreillette`) : **aucun placement posé par le banc** (défaut « casque vanilla » vérifié) ; dalle dans le ciel (rendue à la fin), inventaire relevé puis rendu, la séquence attend la fin de tout rechargement des ressources ; sacoche › clic droit › Teinture, écran Teinture (défaut, rouge), puis pour le bandeau sans teinte, le bandeau rouge, le masque, le chapeau et l'oreillette : face, profil, dos, tête tournée, duo avec un mannequin « autre joueur » (diffusion `reborn:cosmetics` sans transform ; sa tête ne tourne pas, le contrôle tête tournée se fait sur soi), ¾ ; contrôle d'un transform diffusé prioritaire → `run/screenshots/tete_*.png` ; validé sous Sodium |
| Avec **Sodium** | Les bancs de l'eau, de la météo et du halo de méditation ont été validés avec le pack du launcher copié le temps des essais dans `run/mods` (sodium, sodium-extra, lithium, entityculling, ETF, YACL, fabric-language-kotlin ; + BendableCuboids pour la pose de méditation, sans lui les genoux et coudes ne plient pas en dev), puis retiré ; `run/config` et le monde de dev remis à l'identique |
| `REBORN_AUDIT=1` (+ `REBORN_AUDIT_SCALES`, `REBORN_AUDIT_ONLY`) | Audit responsive : écrans et états du HUD × échelles. Relève aussi le **nombre d'éléments GUI** de chaque capture (DA §6, règle 7 ; `audit/GuiCompte`) dans `run/audit_elements.txt` |
| `REBORN_AUDIT_ONLY=menu_hud,menu_viseur,menu_camera,menu_animations,menu_photo,menu_galerie,menu_signaler,menu_deconnexion,menu_tirage,menu_cosmetique` | Écrans en jeu du lot du 13/10 (dont `menu_galerie_detail` / `_retouche` / `_partage`, `menu_tirage_confirmer` / `_resultat`, `menu_cosmetique`) |
| `REBORN_AUDIT_ONLY=hud_ko,hud_volonte,hud_cooldowns,hud_combat` | HUD discrets : KO (à terre, carte, inconscient, ATA), Volonté (invitation, épreuve), recharges (`hud_cooldowns` : dash, double saut et saut de chakra en recharge + combat et course de chakra forcés par `CooldownState.debugActifs`) |
| `REBORN_AUDIT_ONLY=menu_tirage` | Test de la Feuille v2 (16/10) : `menu_tirage_signes` (entrée), `_confirmer`, `_concentration` (canalisation, jauge aux deux tiers), `_reaction_<nature>` et `_resultat_<nature>` pour les 5 natures, `_reaction_double`, `_resultat_double` ; écran ouvert à une étape précise par `TirageScreen.banc(nature, autres, étape)` |
| `REBORN_TIRAGE_DEBUG=1` (+ `REBORN_TIRAGE_CAS=katon,double,…` : ces cas ; `=anim` : poses `run/anim_essai_<k>.json` jouées tour à tour sous le plan ; `=bug` : journal de l'ancien bug de caméra) | Test de la Feuille v2 (16/10, `menu/tirage/TirageDebug`, monde « New World », natures imposées, canalisation simulée) : confirmation, puis un **film** par cas — katon, suiton, doton, raiton, futon, double (Suiton éveillé + Katon), renoncer (Échap pendant la jauge), mur (colonne de pierre posée dans l'air là où se poserait le plan, puis retirée : un autre angle est choisi) — de la caméra épaule au retour à l'épaule (objet en main remplacé par du papier, la feuille). L'horloge de l'écran avance de 50 ms par tick client capturé (`TirageHorloge`) : une capture par tick = film régulier à 20 i/s → `run/screenshots/tfilm_<cas>_<nnn>.png` (+ `tirage_01_confirmer`) ; lacet du joueur journalisé (`[tirage-banc]`, il ne bouge plus). Films, GIF et planche : `Maquettes/gameplan/test_feuille_v2/`. Validé avec le pack Sodium copié le temps de l'essai |
| `REBORN_CHARGEMENT_DEBUG=scene` | Scène de chargement (15/10, `menu/connect/ChargementDebug`, horloge imposée) : une capture par état (`chargement_01_connexion` … `_04_monde`) puis 180 images à 20 i/s (`chgif_*`) ; écrit le coût moyen d'extraction par image dans le log. Copies et GIF dans `Maquettes/gameplan/chargement/v2/` |
| `REBORN_CHARGEMENT_DEBUG=serveur` (+ `REBORN_CHARGEMENT_SERVEUR=hôte:port`, défaut `127.0.0.1:25599` ; `REBORN_CHARGEMENT_PACK2="url sha1"`) | Vrai trajet vers un serveur de test **local** (serveur vanilla 26.2 hors dépôt, `online-mode=false`, pack exigé servi en HTTP local) avec un `ServerData` réglé sur « Demander » : Échap annule ; 1ᵉʳ passage (pack en configuration + pack poussé pendant l'écran du monde, comme Nexo) ; 2ᵉ passage (pack servi par le cache) ; pack renvoyé en jeu (comme `/nexo reload`). « OK » / « ECHEC » dans le log, captures `chsrv_*` |
| `REBORN_TOUCHES_DEBUG=1` | Carte des touches (15/10, `keybind/TouchesDebug`) : les touches passent par le **vrai** chemin du clavier (`KeyboardHandler.keyPress` par réflexion : écrans, toutes les liaisons de la touche, mods à l'écoute) ; piste dans le ciel au-dessus du point d'apparition (rendue à la fin) — B bref (écran de l'escouade) puis B dans l'écran (fermé), B maintenu (pas d'écran), F5 (1ʳᵉ personne, épaule), 2 appuis sur le sprint (sprint) puis 3 (course de chakra, arrêt au relâchement ; vue de côté par le regard libre, vraie touche Alt), P puis G (galerie, retour au mode photo), Échap → INTERFACE (éditeur → clic « Personnaliser le viseur ») et carte Personnage (sélection), N (menu OST si `mod-ost` est dans `run/mods`), « . » (émotes, démarches, fermé par « . »), Réglages → Contrôles et Caméra. table de la disposition du clavier sur des claviers simulés (QWERTY : rien ne bouge ; AZERTY : M → US « ; », Z → US W gardée, . → US « , » ; QWERTZ : Z → US Y) et lettre réellement affichée sur le poste. « [touches] OK / ECHEC » dans le log et `run/touches_banc.txt` (carte réelle des touches après migration avec la lettre affichée, raccourcis Plasmo Voice, conflits restants hors F3) ; captures `run/screenshots/touches_*.png`, copies dans `Maquettes/gameplan/touches/`. Pour éprouver la migration : `options.txt` avec les anciens défauts, sans `touchesVersion` dans `reborn_prefs.json`. Validé avec et sans le pack Sodium copié le temps de l'essai |
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
- **Fenêtre du jeu** (16/10, `menu/FenetreReborn`) : titre « 26.2* Reborn » (version lue du jeu) par
  `mixin/menu/FenetreTitreMixin` (retour de `Minecraft#createTitle`, que `updateTitle` rappelle à chaque changement
  d'écran, de monde ou de connexion : le titre ne revient jamais à « Minecraft* 26.2 - Multijoueur ») ; icône = logo du
  launcher (`apps/launcher/src-tauri/icons`) en `assets/reborn/icons/window/icon_{16,32,48,128,256}.png`, posée par
  `mixin/menu/FenetreIconeMixin` sur `IconSet#getStandardIcons` (lu par `Window#setIcon` → `glfwSetWindowIcon`, Windows
  et X11). Un PNG manquant → icône vanilla ; macOS (`getMacIcon`, .icns) et Wayland non touchés. La barre de titre
  native n'apparaît pas dans les captures du jeu : preuve par capture Windows de la fenêtre
  (`Maquettes/gameplan/fenetre/`). Un mixin ne peut pas se référencer lui-même à l'exécution : la logique est dans
  `FenetreReborn`.
- **Écran qui prend la caméra** (16/10, bug du test de la Feuille) : ne jamais réécrire l'orbite de `RebornCamera` à chaque tick en la calculant depuis le lacet du joueur — au repos, `KeyboardInputMixin` aligne le lacet du joueur sur l'orbite : les deux se poursuivent (le joueur tournait de 75° par tick, vu des autres). Passer la caméra Reborn en `Mode.VANILLA` le temps de l'écran et poser un plan dans `CameraRepositionMixin` (modèles : `CameraEssayage`, `menu/tirage/CameraTirage`), sauvegarder une fois, rendre à la fin (y compris déconnexion).
- **Pas d'entités factices hors monde dans un écran** (gel au join) — cf.
  [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) §5.
- Payloads : JSON UTF-8 brut sans préfixe de longueur ; enregistrer le type dans les deux sens si le mod envoie.
- Le jar fait ~29 Mo (0.4.194, 31/10 ; le fond 3D du menu, `assets/reborn-hud/dynamic-player`, en fait ~18). **Un jar
  qui grossit fortement = un asset intrus** (cf. publication).
- Au-dessus des têtes (nom, bulle, /me, plaque staff, invite, dégâts) : ne plus projeter « œil + k » soi-même — passer
  par `TeteAncre` / `CalqueTete` (§9) : tête réellement rendue, interpolée, projection identique au monde, pile sans
  chevauchement, ancre calée au pixel physique puis dessin en coordonnées locales entières (sinon le texte scintille).
- **Plasmo Voice reste facultatif** (31/10) : une classe du mod qui nomme un type de Plasmo dans une signature (retour,
  paramètre) ne se charge plus du tout sans le jar (`NoClassDefFoundError` à la vérification du bytecode, constaté sur
  l'ancien `VoiceState`). Tout le code qui nomme Plasmo va dans une classe imbriquée chargée au premier appel
  (`VoiceState.Pv`), l'appelant rattrape `NoClassDefFoundError` une fois et rend des valeurs neutres.
- **Brouillard et Sodium** (15/10) : Sodium copie le `FogData` pour ses chunks dans son propre `@Inject` RETURN de
  `FogRenderer.setupFog`. Un mixin qui modifie le brouillard doit passer **avant** lui (`priority = 500`, cf.
  `FogRendererMeteoMixin`), sinon seules les entités sont noyées et le terrain garde le brouillard du jeu (c'était le
  cas de la pluie, du sable et de la brume avant le 15/10).
- **Décalque posé sur l'eau ou le sol** (15/10, `eau/EauRendu`) : `LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`
  (par-dessus l'eau de Sodium), maillage construit à la main puis `RenderType.prepare().drawFromBuffer(...)` avec
  `RenderTypes.entityShadow(texture)` = translucide, sans écriture de profondeur, décalé vers la caméra (pas de
  scintillement), brouillard, **pas d'éclairage** (assombrir soi-même la couleur des sommets selon la lumière) ;
  `entityTranslucent` éclaire avec les deux lumières des entités : un décalque clair y sort gris. Quads dans le sens
  qui fait face au ciel.
- **Pas d'allocation par image** dans ce qui se dessine à chaque image (exigence perf du 15/10 : le concurrent tourne
  en 4 Go) : réserves d'objets, listes et tableaux préalloués, vidés puis réutilisés ; tampons GPU réutilisés en
  rotation sur 3 images. Seuls restent les petits objets d'en-tête de l'API du jeu (`BufferBuilder`, `MeshData`).
- **Animations exportées de Blender** (18/10, `tools/emotes/`) : un membre piloté par l'IK ne doit porter **qu'une
  clé** dans l'action (l'exporteur du rig ne remplace la courbe que là où la cuisson de l'IK diffère : une clé « 0 »
  laissée à chaque temps fait claquer bras et jambes au repos à ces instants) ; le pli du buste (`torso` bend)
  n'emporte ni la tête ni les bras (ni dans le rig ni en jeu : un grand pli les laisse décrochés, pencher `waist`) ; le joueur est rendu à 15/16 et un méditant assis sur un support est abaissé
  de 0,6 bloc (attache de véhicule) — le README de `tools/emotes/` détaille tous les pièges.

## 7. Historique des versions

Le détail version par version (0.4.139 → 0.4.169) est dans [`../pilotage/JOURNAL_DES_LIVRAISONS.md`](../pilotage/JOURNAL_DES_LIVRAISONS.md).

## 8. Trajet vers le serveur : scène de chargement et pack Nexo (15/10)

- **Scène** (`menu/connect/ChargementScene`, textures `textures/gui/chargement/*` générées par
  `tools/gen_chargement_textures.py`) : nuit bleue, grande pleine lune au centre, cumulus éclairés par la lune (cuits
  dans le ciel), voiles et brume qui dérivent, collines et village aux fenêtres chaudes, arbres et lisière de cimes au
  premier plan, corbeaux, feuilles, étoiles qui scintillent (le shinobi a été retiré le 15/10, retour utilisateur).
  En bas, centrée : une **barre pixel art** (40 % de la largeur, cadre fin sombre, remplissage bleu en 5 rangées avec
  reflet, tête claire), le **pourcentage à droite** et le **libellé petit au-dessus**, en **ArcadePix** (police des
  menus, capitales, au pixel écran) : « CONNEXION AU SERVEUR », « TELECHARGEMENT DES RESSOURCES », « APPLICATION DES
  RESSOURCES », « CHARGEMENT DU MONDE ». Progression inconnue (connexion) : un éclat bleu glisse dans la barre, sans
  pourcentage. Ni logo, ni bouton : **Échap annule** la connexion.
- **Écrans couverts**, une seule horloge (aucun saut) : `ConnectScreen`, rechargement des ressources du pack serveur
  (`LoadingOverlay` : fond, logo et barre Mojang retirés par `@WrapWithCondition`, choix fait une fois par écran),
  `LevelLoadingScreen` (pendant le trajet et sur tout serveur distant ; vanilla en solo). Toasts masqués tant que la
  scène est à l'écran ; le toast vanilla « Téléchargement du pack » n'est plus jamais affiché. Fondus : depuis la nuit à l'entrée ; à l'arrivée, la scène s'éteint dans la nuit puis la nuit s'efface sur le
  jeu (élément HUD `chargement_fondu`). L'écran du monde est **retenu** tant qu'un pack serveur est en route ou qu'un
  rechargement est à l'écran (garde-fou 120 s). Le splash du démarrage reste `BootLoadingRenderer`.
- **Coût** : 8 textures, ~40 Ko sur disque, ~2,2 Mo de VRAM, **libérées** dès que plus aucun écran ne les dessine ;
  une trentaine de blits et d'aplats par image (la barre : une douzaine d'aplats, pas de texture) ; aucune allocation par image dans le code de la scène (états et textes
  en cache, hachage au lieu de `Random`). Mesuré par le banc : ~0,17 ms d'extraction CPU par image en 1080p (k = 4),
  ~0,13 ms en 1366×768.
- **Pack Nexo sans confirmation** : `ConnectScreenMixin.startConnecting` passe le serveur visé en « packs serveur :
  activés » (bouton JOUER, liste des serveurs, quickPlay du launcher) et `ClientCommonPacketListenerPackMixin` accepte
  tout push de pack (configuration ou jeu) sans écran, même si le `ServerData` est absent ou réglé sur « Demander ».
  Le cache vanilla (`downloads/`, par SHA-1) évite tout retéléchargement d'un pack inchangé (journal : « Returning cached
  file since actual hash matches requested ») — **à condition que le serveur envoie le SHA-1**.
- **Côté serveur (rien d'obligatoire)** : l'écran de confirmation est un écran du client, le serveur ne peut pas le
  retirer ; c'est le mod qui le supprime. Dans `plugins/Nexo/settings.yml`, section `Pack` → dispatch (noms à vérifier
  dans la version installée) : garder le pack **obligatoire** (les clients sans le mod devront toujours l'accepter),
  le texte d'invite ne s'affiche plus pour les joueurs Reborn, garder l'envoi de l'empreinte SHA-1 (sinon chaque
  connexion retélécharge le pack). Envoyer le pack pendant la phase de configuration (avant l'arrivée en jeu), si Nexo
  le propose, raccourcit le trajet ; sinon l'écran du monde attend le pack poussé à l'arrivée.

## 9. Au-dessus des têtes : ancre, pile, nom au regard, bulle, /me (31/10)

Décisions du 31/10 ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)) : une seule bulle (la nôtre), calée sur la
tête comme le nom ; le nom n'est plus permanent (il apparaît au regard puis s'efface) ; /me refait au-dessus de la tête,
blanc et sobre ; le soir même : **aucun fond** derrière le /me ni derrière la bulle (seulement le texte et les icônes,
lisibles par une ombre portée d'1 px ; la plaque de nom garde ses 4 styles). Banc : `REBORN_PLAQUES_DEBUG=1` (§5).
Toutes les constantes sont en tête de leur classe.

- **Ancre** (`anchor/TeteAncre`) : centre de la tête **réellement rendue** (émotes, ramper, à terre, allongé sans lit),
  projetée comme le monde (balancement compris), avec les trois demi-axes de la boîte de tête (chapeau compris) pris dans
  la même pose. `CalqueTete.ancre(e, out)` = `TeteAncre.ecran(e, 0, out)` : **sommet de la tête vue par la caméra** en px
  GUI calés au pixel physique (`ancrePx` en pixels de la fenêtre, pour ce qui se dessine en « unités menu ») — on monte
  le long de l'axe « haut » de la caméra de la distance d'appui de la boîte (`|hx·haut| + |hy·haut| + |hz·haut|`), puis,
  de près, le coin le plus haut à l'écran l'emporte s'il dépasse (perspective) ; neuf projections par entité et par
  image, mises en cache, aucune allocation. Correctifs du 31/10 au soir : on montait de 0,265 vers le haut du monde,
  juste pour une tête droite vue à hauteur d'œil — la pile mordait sur une tête basculée (à terre, demi-hauteur réelle
  0,373) ou vue en plongée (bord arrière du chapeau). Vu de loin à hauteur d'œil, rien ne bouge. Tout ce qui flotte au-dessus d'un joueur s'y ancre : nom, bulle, /me, plaque staff, invite « Tendre »,
  chiffres de dégâts (`CombatHud`, marge 0,413 bloc = l'ancien « œil + 0,7 » d'un joueur debout).
- **Pile** (`nameplate/CalqueTete`), de la tête vers le haut : sommet de tête → 3 px (`ECART_TETE`) → nom → 2 px
  (`ECART`) → bulle → 2 px → /me empilés → 2 px → plaque staff → 2 px → invite. Chaque élément **déclare** sa hauteur
  à chaque image (`occuper`, avec sa demi-largeur), lit le bas de sa couche (`base`, lissage exponentiel de 90 ms,
  `LISSAGE_MS`) et n'est opaque qu'à hauteur de la place déjà libérée au-dessus de lui (`place`) : rien ne se
  recouvre, même pendant une transition. Une déclaration vaut pour l'image et la suivante (l'ordre des éléments du HUD
  n'importe pas). Le /me d'un joueur du fond monte au-dessus du nom et de la bulle de ceux de devant
  (`auDessusDesAutres`, têtes visibles côte à côte) ; un nom en maintien qui recouvrirait celui qu'on regarde s'efface en
  110 ms et le nom regardé attend qu'il soit parti (`recouvre`). La visibilité de la tête (`TeteAncre.visible` : capture
  fraîche, aucun bloc opaque) est filtrée à deux images (`teteVisible`) : une image sans capture fraîche (à-coup de plus
  de 300 ms, capture d'écran) ne fait rien clignoter ; filtre et hystérésis ne relient que des images consécutives (une
  tête évaluée après une pause repart de ce qu'on voit : plus de bulle allumée deux images à travers un mur).
  **`teteDegagee`** (nom, bulle et /me) y ajoute les **corps** plus proches de la caméra
  (`TeteAncre.cacheeParUnCorps` : segment caméra → centre de tête contre la boîte de collision réduite de 0,1 bloc et la
  boîte de la tête rendue de chaque avatar rendu à l'image, soi compris en 3ᵉ personne ; hystérésis de 150 ms) : un
  auteur caché derrière un joueur s'efface comme derrière un mur, au lieu de faire parler celui de devant. Horloge
  commune : `Nameplates.horloge` (injectable).
- **Nom au regard** (`Nameplates`) : rayon depuis la **caméra** (position + direction ; vue épaule par défaut),
  prolongé de la distance caméra – œil ; cible = hitbox gonflée (0,30 pour accrocher, 0,45 pour garder) unie à une
  boîte de ±0,35 × échelle autour du centre de la tête (un joueur allongé se vise à la tête) ; 6 blocs au plus de
  l'œil ; tête dégagée (jamais à travers un bloc opaque ni un corps plus proche) ; le plus proche dans l'axe ; regard tenu 90 ms (un viseur qui
  balaie un groupe n'allume personne). Déroulé : fondu entrant 160 ms (ease-out), visible tant qu'on regarde,
  **maintien 2,5 s**, fondu sortant 450 ms ; au-delà de 9 blocs pendant le maintien, sortie immédiate ; accroupi,
  invisible ou tête cachée (mur, ou quelqu'un qui passe devant pendant le maintien) : sortie en 200 ms. Atténuation avec la distance (pleine à 3 blocs, 60 % au moins). Les
  quatre styles sont inchangés ; l'ancre est calée une fois au pixel physique et la plaque dessinée en coordonnées
  locales entières : le contour du style « Détouré » ne scintille plus. Textes ArcadePix composés une fois par nom
  (`Da.arc` normalisait et compilait une regex à chaque appel, neuf fois par plaque). Banc : `DEBUG_NOMS` (un nom par
  mannequin), `debugAvatars`, `debugInconnu` (« ??? »).
- **Bulle** (`voice/SpeechBubbles`) : **sans fond** depuis le 31/10 au soir (plus de capsule ni de pointe) : le motif
  seul, blanc doux, centré sur l'ancre et posé au-dessus de la tête comme le nom ; lisible sur la neige et le ciel clair
  par une ombre portée d'1 px écran (encre à 85 % × le fondu) et un **contour sombre très discret** sur les 4 côtés
  (1 px écran, 2 en 4K, encre à 40 % × le fondu, invisible sur fond sombre) ; voix = 3 barres d'1 unité qui ondulent
  (égaliseur doux, rythme propre à chaque joueur), frappe = 3 points en vague, nets au repos (85 %) ; la voix l'emporte.
  Tailles en **unités menu** (`DaKit.echelleMenu()`, comme la plaque staff) : la taille validée à l'échelle 2 en 1080p à
  toutes les échelles d'interface. La couche a une **hauteur fixe** (les barres au plus haut, contour et ombre compris ;
  la frappe y est centrée) : passer de l'écriture à la parole ne fait plus glisser le /me ; éteinte, elle reste
  **réservée** (invisible) 1,5 s tant qu'un /me ou une plaque staff est posé au-dessus : le /me ne redescend plus à
  chaque pause de la voix. Portée mesurée depuis le **joueur local** (elle l'était depuis la caméra : en vue épaule, une
  cible devant n'avait de bulle qu'à 1,25 bloc) : **voix de proximité jusqu'à 64 blocs** (la coupure de l'ancienne icône
  de Plasmo : la bulle est le seul signe de qui parle, et une source de proximité activée est forcément audible),
  pleine jusqu'à 8 blocs, 60 % à 16 blocs et au-delà, fondu sur les 4 derniers ; frappe et voix d'une autre ligne
  (radio) : 8 blocs. Fondus 120 / 220 ms, sur soi en 3ᵉ personne seulement, masquée si la tête n'est pas dégagée (mur,
  ou corps d'un joueur plus proche). Sources (Plasmo Voice, frappe) relues toutes les 50 ms dans des tableaux réutilisés ;
  `TypingState` résout les UUID par `Level#getEntity(UUID)` (mannequins compris) dans un ensemble réutilisé. Qui parle
  vient du seul `VoiceState` (relu à chaque tick, §10) ; banc : `VoiceState.DEBUG_PARLE`, `DEBUG_SOI_PARLE`.
- **/me** (`voice/MeBulles`, `chat/MePayload`, canal **`reborn:me`**, S → C, octets UTF-8 bruts) :
  `{"v":1,"t":"me","id":<id réseau>,"u":"<uuid>","nom":…,"inconnu":…,"ton":…,"texte":…,"ms":6500,"r":16,"n":17}`,
  un message par spectateur ; entité par `id` puis par `u` ; `ms` = durée (défaut `min(12000, 2000 + 150 × longueur)`,
  bornée à 1,5 – 20 s), `r` = portée en blocs (défaut 16), `n` = numéro par auteur (un doublon est ignoré) ;
  `{"v":1,"t":"me_fin","id":…}` efface ses bulles. `nom`, `inconnu` et `ton` ne sont pas affichés (le nom est sur la
  plaque) ; codes `§` retirés, 256 caractères au plus. Rendu **sans fond** (31/10 au soir) : texte blanc doux en Inter 8
  posé directement au-dessus de la tête, ombre portée d'1 px écran (encre à 85 %) et contour sombre très discret sur les
  4 côtés (1 px écran, 2 en 4K, même encre à 35 %), alpha = carré du fondu, sous les seules lettres déjà révélées :
  jamais d'ombre ni de contour seuls ; 200 unités de large au plus × facteur distance, en **unités menu** (même taille à
  l'écran à toutes les échelles d'interface : à l'échelle 3 ou en « Auto », le /me grossissait au-dessus d'un nom resté
  petit), 3 lignes puis « … », lignes centrées ; la couche occupe le texte visible (du haut des hampes au bas des
  jambages, ombre et contour compris). Apparition : montée de 6 px et fondu de 220 ms (ease-out), lettres révélées à 16 ms (450 ms
  au plus pour tout le texte) ; disparition : fondu de 400 ms et montée de 4 px. Au plus 2 /me par auteur (le plus
  récent près de la tête, l'ancien monte ; un 3ᵉ fait partir le plus ancien). Échelle = clamp(1 − (d − 4) / 24 ; 0,72 ;
  1), fondu sur les 4 derniers blocs avant `r` (`d` = de l'œil du joueur local au centre de la tête). Deux auteurs qui
  se recouvrent à l'écran sont écartés (poussée verticale gloutonne, lissée ; le plus proche garde sa place et est
  dessiné en dernier). Masqué si la tête n'est pas dégagée (mur, ou **corps d'un joueur plus proche** : le /me d'un
  auteur caché derrière quelqu'un ne se pose pas sur lui ; la ligne de tchat reste) ou l'auteur invisible ; un auteur
  naît invisible (rien ne s'affiche s'il est caché quand le /me arrive) ; soi en 3ᵉ personne seulement ; **visible à
  terre** (pas dans `KoMasqueHud`). Le type est enregistré à l'initialisation du client (sinon le canal n'est pas
  annoncé et le serveur ne le voit pas dans `getListeningPluginChannels`). Mise en page faite à la réception (lignes,
  préfixes de la révélation, largeurs) : rien n'est alloué par image. Élément HUD `reborn-hud:me-bulles`. Banc :
  `MeBulles.debugRecevoir(json)`.
- **Plaques staff, invite « Tendre »** : mêmes ancre et pile (couches `STAFF` et `INVITE`), dessins inchangés. La plaque
  staff reste visible à travers les murs (outil staff).
- **Pseudos vanilla** (`mixin/EntityNameTagHideMixin`) : `AvatarRenderer#shouldShowName` répond faux pour un joueur sur
  un serveur Reborn ou dans la douane — le chemin vanilla et celui d'EntityCulling (noms à travers les murs) passent tous
  deux par là ; mannequins et entités nommées gardent leur nom. L'ancienne version annulait `extractNameTags` en entier et
  sautait aussi la distance à la caméra (ombres des joueurs jamais atténuées).
- **Mouvements de tête** (« bien calibrer les mouvements de tête », 31/10) : la tête des **autres** joueurs reste lissée
  sur les **3 ticks** de vanilla (`mixin/ClientPacketListenerTeteMixin`, point de réglage unique). Le lot l'avait passée
  à 2 (le serveur suit les joueurs tous les 2 ticks) : la tête devançait le lacet du corps et le tangage (3 ticks), le
  cou se tordait au-delà de 50° à chaque virage (jusqu'à 110°), le regard décrivait une courbe et la tête se figeait un
  tick à chaque paquet en retard ; le retard d'une tête distante ne se voit pas, une saccade si (correctifs du 31/10 au
  soir). Pour soi, le corps n'est tiré qu'au-delà de la borne vanilla, 50° (15° en parade au
  bouclier), celle que les autres clients appliquent à notre avatar (`LocalPlayerBodyMixin`, via
  `LivingEntityTeteAccessor` ; elle était à 75°). L'ancre de tête ignore le regard (la plaque ne danse pas quand on lève
  ou baisse la tête) mais garde l'inclinaison imposée par la pose (−45° à terre, en nage, en élytre).

## 10. Voix : portée, micro coloré, micro à terre, outils staff (31/10)

Décisions du 31/10 ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)) : les portées sont **celles de Plasmo Voice**
(configuration du serveur, pas de système Reborn) ; la **couleur du micro** dit la portée ; à terre, chuchoter seulement ;
le staff coupe et rétablit la voix (durée, motif, journalisé, jamais le contenu). Côté serveur : PLUGINS_REFERENCE §4
quater.1. Banc : `REBORN_VOIX_DEBUG=1` (§5).

- **Portée** (`voice/VoiceState`) : `portee()` = rang de la portée choisie parmi celles du serveur — la plus petite = 0
  (chuchotée), la plus grande = 2 (criée), entre les deux = 1 (normale) ; portée unique, inconnue ou sans Plasmo Voice =
  1 ; mode plage `[-1, N]` : par tiers de N. Tout l'état de la voix (portée, je parle, micro coupé, assourdi, qui parle
  autour) est relu **une fois par tick** (`VoiceState.tick`, en tête du tick de `HudKeybinds`) : le HUD ne lit que des
  champs et un ensemble réutilisé.
- **Touche H** (`HudKeybinds.VOIX_PORTEE`) : un cran **plus bas** à chaque appui (la plus grande sous l'actuelle,
  sinon la plus grande : normale → chuchotée → criée → normale ; mode plage : tout N → moitié → quart), pour que baisser
  la voix ne passe plus par la plus grande portée, écrite dans la configuration de Plasmo Voice (l'entrée de l'activation de proximité du serveur courant, hors
  API ; Plasmo prévient le serveur, qui revalide), annoncée en bas à droite : petit micro teinté + « Voix : chuchotée /
  normale / criée » (« Voix : N blocs » au-delà de trois portées ; « une seule portée ici » ; « indisponible »), une
  seule ligne remplacée à chaque appui.
- **Micro coloré** (`runtime/VitalsHud#micro`) : vert / jaune / rouge en permanence (la palette des barres), posé sur une
  copie éclaircie de l'icône de l'équipe (texture dynamique 16×16 créée une fois : teinte sur l'originale gris foncé, le
  jaune virait au brun) ; je parle = légère pulsation vers une variante plus lumineuse de la **même** teinte (2,5 Hz,
  monte en 90 ms, retombe en 220 ms ; vers le blanc, l'or devenait beige et se lisait « atténué ») ; **gris** sans
  liaison vocale (`VoiceState.relie()` : Plasmo absent, serveur sans voix, UDP non connecté — personne ne t'entend),
  sans pulsation ; coupé, rendu muet par le staff, assourdi, ou **voix refusée par l'état** (capacité `voc` = 0 : mort
  RP, corps contrôlé ou vide, cinématique — le serveur jette tout) : icônes barrées de l'équipe, grisées, pas de bulle
  sur soi, touche H refusée avec la raison du serveur (« Voix coupée — Ton personnage est mort. »).
- **À terre, inconscient** : chuchoté imposé à l'affichage (`EtatClient.niveau("voc") == 1` ou phase KO 1 / 2) : micro
  vert, touche refusée (« À terre, tu ne peux que chuchoter ») ; la vraie limite est sur le serveur (ShinobiRadio
  `VoixATerre`). Les vitals étant masqués par le KO, **`ko/MicroATerreHud`** (élément `reborn-hud:micro-a-terre`, hors
  de `KoMasqueHud`) remet le micro seul à sa place (élément VITALS de l'éditeur, même échelle), « chuchoté » 2 s à son
  apparition, le refus de la touche à côté (les notifications sont masquées à terre). Seulement si le joueur affiche ses
  vitals, sur un serveur Reborn, avec Plasmo Voice.
- **Réglages Reborn de Plasmo Voice** (`voice/RebornVoiceAddon`, à l'initialisation et à chaque connexion vocale, écrits
  seulement s'ils changent) : icônes au-dessus des têtes masquées (`show_source_icons = 2` : la seule bulle est la nôtre,
  §9), touche « Action » déliée (clic droit + molette = volume de 0 à 200 % : doublon du volume du menu R, borné à
  25-100 %, et le clic droit sert aux armes) ; le cercle de portée de Plasmo est redessiné aux couleurs du micro,
  assombries à 60 % (c'est une sphère translucide qui teinte toute la vue).
- **Outils staff** : Poste de garde › fiche › Plus › **« Couper la voix… »** (V ; motif obligatoire, 5 min, 15 min par
  défaut, 30 min, 1 h ou 1 jour ; joueur en ligne) ou **« Rétablir la voix »** (envoi direct, aussi hors ligne) ; ligne
  « Voix coupée — jusqu'à 21:45 — motif » sous l'état de la fiche (qui l'a coupée au survol), marqueur orange dans la
  liste ; menu R › Staff : « Couper la voix… » ouvre la fiche sur le formulaire, « Rétablir la voix » part tout de suite
  (réponse dans la pile de notifications). Opérations `voix_couper` / `voix_retablir` et champs `voixCoupee`,
  `voixFin`, `voixMotif`, `voixPar` de la fiche : PLUGINS_REFERENCE §4 bis.2.
