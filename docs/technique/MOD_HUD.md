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
| `ko/` | HUD du KO et de l'ATA, QTE Volonté, menu des corps (`CorpsClient`), scène de mort (`DeathScreen`) |
| `staff/` | Poste de garde v3 « le volet » (14/10) : `StaffScreen` (volet, onglets, alertes, liste, menus), `GardeFiche` (fiche compacte, sections, sanction), `GardeRecherche` (recherche universelle, hors ligne et actions), `GardeJournal` (jetons, jours), `GardeOutils` + `GardeAnnonces` (outils, chat, commandes, modes), `GardeFormulaire` (formulaire dans le volet), `GardeActions` (catalogue des actions et raccourcis), `GardeArt` (palette, icônes `garde/icones.png`, pinceau en lots) ; `GardeScreen` = l'écran ouvert, avec l'Infirmerie dans Outils ; effets staff en jeu (`EffetsStaff` : annonce, écran noir, séisme via `mixin/CameraSeismeMixin`, indicateur du mode staff / animation ; `PlaquesStaff` : plaques du mode staff ; canaux `reborn:annonce`, `reborn:ecran`, `reborn:plaques` par `CanalJson`), serveur simulé du banc (`StaffDemo`) |
| `faction/` | Faction et sections (16/10, canal `reborn:faction`) : `FactionScreen` + `FactionPages` (volet J, maquettes v3), `FactionArt` (kit du volet, atlas `faction/insignes.png` de `tools/gen_faction_textures.py`, têtes RP, capuche des inconnus, vignette de tenue), `FactionHud` (invitation), `FactionClient`, `Insignes` (atlas pour le Poste de garde), banc `FactionDebug` / `FactionDemo` ; outil staff : `staff/GardeFactions` |
| `parchemin/` | Bibliothèque, lecture, réglages staff, concentration du chakra |
| `chat/` | Chat en panneau (onglets Général / RP / Groupe, recherche, mode /me, icônes Lucide) |
| `radio/` | Radio en escouades (14/10, canal `reborn:radio`) : `RadioClient` (état, invitation, qui parle), `RadioScreen` (écran de l'escouade : appui bref sur B), `RadioHud` (élément HUD « Escouade »), `RadioMenus` (menu R : Radio, Oreillette), `RadioDebug` (banc) — [`../gameplay/RADIO.md`](../gameplay/RADIO.md) §12 |
| `voice/` | Plasmo Voice (client 2.1.17, `compileOnly`) : `RebornVoiceAddon`, `VoiceState` (micro / casque, bulles), `RadioVoix` (son radio, icône masquée, touche B) |
| `combat/` | HUD combat : endurance en ensō autour du viseur, chiffres de dégâts, combo, icônes de recharge |
| `map/` | Carte du monde (touche M), intro en nuages |
| `interaction/` | Mode interaction (touche R) : curseur, menus contextuels (joueur, corps, entité, bloc, soi) |
| `camera/`, `screenshot/` | Caméra 3ᵉ personne (touche Vue = F5 ; réglages dans Réglages → Caméra), mode photo (filtres, cadres), galerie (ouverte depuis le mode photo) |
| `nameplate/` | Noms au-dessus des têtes (4 styles au choix) |
| `byakugan/`, `meteo/`, `effets/` | Vision Byakugan, météo client (pluie, vent de sable, brume, **blizzard, tempête de sable** — 15/10), filtres plein écran |
| `eau/` | **Marche sur l'eau** (15/10) : `MarcheEauClient` (permis `reborn:eau`, surface solide pour le joueur local via `mixin/BlockStateEauMixin`, accroupi = s'enfoncer, Saut = remonter), `Ondes` (marcheurs détectés sur chaque client ; à chaque pas un **pli de la texture d'eau** autour du pied, gouttelettes, bruit de pas dans l'eau ; **réglages = constantes en tête de la classe**), `EauRendu` (patch de la texture d'eau aux UV déplacés autour d'un contour irrégulier, après l'eau translucide ; l'eau du jeu n'est pas modifiée), `EauDebug` (banc) |
| `skin/` | Compositeur de skins Reborn (`RebornSkins`, `SkinSpec`) |
| `editor/`, `element/`, `config/` | Éditeur de HUD (déplacer / redimensionner / masquer), éléments, presets |
| `emote/`, `animation/` | Emotes EmoteCraft, menu des animations (touche « . » : émotes et démarches), course de chakra (touche X ou triple appui sur le sprint, `allure/Allure`), synchronisation d'animations |
| `ui/` | `MenuScale` (taille des menus), `Da` (kit de dessin DA) |
| `audit/` | Bancs de test : `ResponsiveAudit`, `ConcentrationPreview`, `FeaturePreview` |
| `mixin/` | Tous les mixins (déclarés dans `reborn-hud.mixins.json`) |

## 2. Touches

**Carte réduite du 15/10** (décision du 15/10, [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)) : 15 touches Reborn
au lieu de 26, sans conflit avec Minecraft ni le modpack. Image récapitulative (ancien → nouveau) :
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
| **C** | Parade (maintenir) | **Ctrl gauche** | Allures (touche de sprint de Minecraft) : tenir = trot, 2 appuis = sprint, **3 appuis = course de chakra** ([`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md)) |
| **X** | Course de chakra en bascule (15/10 au soir) : un appui la lance, un autre l'arrête ; s'ajoute au triple appui | | |
| **N** | Menu OST (`mod-ost`) | **V** | Réglages de Plasmo Voice (touche de Plasmo Voice) |
| **F7** | Poste de garde (staff) | Espace (à terre) / Entrée, Échap (mort) | Volonté (si apprise) / scène de mort |
| **J** | Volet Faction (16/10) : ta section, ton village, les annonces ; la même touche le referme. Libre dans Minecraft et le modpack, même lettre en AZERTY / QWERTZ (aucune migration) | **Entrée / Retour** | Aussi : accepter / refuser une invitation dans une section (après la radio, avant le duel) |

**Sans touche** depuis le 15/10 : éditeur de HUD et viseur → Échap → **INTERFACE** (bouton « Personnaliser le viseur » au
pied de l'éditeur) ; menu personnage → Échap, **carte d'identité** (nom et village, sous le logo) ; caméra (style Reborn /
Minecraft, épaule, préréglage, repositionner) → **Réglages → Caméra** ; galerie → mode photo ; écran de l'escouade → B bref ;
course de chakra → touche **X** (bascule, rajoutée le 15/10 au soir à la demande de l'utilisateur) ou triple appui sur le sprint.

**Migration** (`keybind/TouchesMigration`, une fois, drapeau `touchesVersion` de `reborn_prefs.json` : 1 = nouvelle carte,
2 = disposition du clavier, ci-dessous) : une touche dont la
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
| `REBORN_STAFF_DEBUG=1` (`=vite` : 1920×1080 seul ; `=<cas>,<cas>` : ces cas ; `=vite:<cas>,<cas>` : ces cas en 1920×1080 seul) | Poste de garde v3 (14/10, `staff/StaffDebug`) : 43 cas — alertes (en attente, traitées, vides, valider une mise à mort), joueurs (liste, survol d'une ligne, menu du clic droit), recherche (« uchi », « gel kazu », hors ligne), fiche (menu Plus, écoute radio, notes, sanctions, confirmation de sanction, comptes, écran noir, suppression à N° retapé), journal (filtré et déplié, menu + Filtre, vide), outils (liste, annonces + confirmation, séisme, chat, commandes, modes), Infirmerie (blessés, zones, nouvelle zone, hôpitaux, réglages), variantes **denses**, effets HUD (annonces, écran noir, plaques, indicateur de mode) et, depuis le 15/10, la section **Personnages** de la fiche (`persos`, `persos_detail`, `persos_identite`, `persos_grade`, `persos_mort`, `persos_suppr`, `persos_creer`, `persos_emplacement`, `persos_refus`, `persos_hors_ligne` ; copies `Maquettes/gameplan/04_staff/personnages/`) — chacun en **1920×1080, 1366×768 et échelle GUI 3** → `run/screenshots/garde_<taille>_<nn>_<cas>.png`. Les demandes partent vers `StaffDemo` (serveur simulé conforme à PLUGINS_REFERENCE §4 bis). Copies : `Maquettes/gameplan/04_staff/v3/code/` |
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
| `REBORN_ALLURE_DEBUG=1` | Trois allures (14/10, `allure/AllureDebug`) : piste dans le ciel au-dessus du spawn (effacée à la fin, dégâts de chute coupés le temps du banc), caméra de face le temps du banc, marche / trot / sprint / fin de sprint / essoufflé / récupération → `run/screenshots/allure_0*.png` + vitesses mesurées `allure_vitesses.txt` ; endurance simulée (solo, pas de plugin) ; copie dans `Maquettes/gameplan/05_deplacements/` |
| `REBORN_NOURRITURE_DEBUG=1` (+ `REBORN_NOURRITURE_POS="x y z lacet tangage"`, point de vue, jamais noté dans le dépôt) | Nourriture (14/10, `nourriture/NourritureDebug`) : 18 cas — préparer au fourneau, plats prêts, invite « [R] Tendre », menu R (Tendre / Donner des ryo), on me tend (F prendre · Retour refuser), je tends, manger (or pâle dans la barre), repas en pause, plat froid, effet Régénération ×2 (nom 3 s), plusieurs effets, régénération en pause, Affamé, Faim, Fatigue (contrecoup), ration achetée, ration assis, onglet Vivres de l'échoppe → `run/screenshots/nourriture_*.png`, copies dans `Maquettes/gameplan/12_nourriture/code/`. Le joueur de dev (pseudo tiré au hasard, parfois un joueur mort d'un ancien banc : il est ressuscité) passe en créatif et vole le temps du banc ; barre d'action, mode de jeu, heure et météo rendus à la fin, mannequin retiré |
| `REBORN_MONNAIE_DEBUG=1` (+ `REBORN_MONNAIE_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Monnaie et banque (15/10, `monnaie/MonnaieDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2, vue à la première personne (rendues à la fin) — bourse dans la sacoche (survol, « Sur toi »), « Combien veux-tu poser ? », bourse au sol (invite [F]), menu R (Tendre Bourse · 990 ryo), guichet (Déposer, Retirer, Virement, Échange garanti : choisir puis en cours, Coffre et paliers avec un parchemin refusé), fouille (filet 3 s, sacoche du corps, « Prendre dans la bourse »), coffre posé (« Combien veux-tu ranger ? »), Poste de garde (section Économie, formulaire Donner), Échap (bourse, RBCoins) → `run/screenshots/monnaie_*.png`, copies dans `Maquettes/gameplan/19_monnaie/code/`. Le pack de dev `run/resourcepacks/reborn_rouleaux_dev` (copie des provisoires du pack Nexo, bourse comprise) est activé le temps du banc ; barre d'action, mode de jeu, heure, météo, packs, caméra et échelle rendus à la fin ; les envois au serveur sont interceptés |
| `REBORN_BOUTIQUE_DEBUG=1` | Boutiques-lieux (15/10, `boutique/BoutiqueDebug`) : 12 captures en 1920×1080 à l'échelle GUI 2 — une petite échoppe de tailleur est construite dans le ciel au-dessus du joueur (blocs relevés puis rendus), le personnage est placé sur l'estrade : comptoir des vêtements sur « Hauts » (vide), essai, de dos, cadrage visage, confirmation d'achat, après l'achat, comptoir des masques (essai du masque 3D), condition grisée + info-bulle, bazar, repli sur voile (mur devant la caméra), carte BOUTIQUE de l'Échap, écran Boutique vide → `run/screenshots/boutique_*.png`, copies dans `Maquettes/gameplan/42_boutiques/code/`. Pack de dev `run/resourcepacks/reborn_boutique_dev` (modèles Nexo du masque Hannya, du chapeau, des rouleaux ; hors dépôt) activé le temps du banc ; envois au serveur interceptés |
| `REBORN_RADIO_DEBUG=1` (+ `REBORN_RADIO_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Radio en escouades (14/10, `radio/RadioDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2 (rendue à la fin) — écran de l'escouade (survol « Exclure », dissolution à confirmer, vu d'un membre, renommer, sans escouade), élément Escouade (silence, Sakura parle, j'émets, toute l'escouade, invitation, à terre « haché », écoute staff, oreillette volée), éditeur de HUD (élément sélectionné, case « Afficher toute l'escouade »), menu R (Ajouter à « Kagerō », Créer une escouade, Oreillette sur un joueur à terre) → `run/screenshots/radio_*.png`, copies dans `Maquettes/gameplan/18_radio/code/`. Têtes RP prêtées par `RebornSkins` (rendues à la fin) ; la voix n'est pas simulée |
| `REBORN_METEO_DEBUG=1`, `REBORN_BYAKUGAN_DEBUG=1` | Météo ; Byakugan |
| `REBORN_METEO_DEBUG=tempetes` | Blizzard et tempête de sable (15/10, `meteo/MeteoDebug`) : sol dégagé cherché par le serveur intégré près du point d'apparition, mannequins à 4, 8, 12 et 18 blocs ; clair, blizzard 75 % / 100 % / 3ᵉ personne / nuit / 40 %, tempête de sable 75 % / 100 % / 3ᵉ personne / soir, entrées et sorties en images de GIF → `run/screenshots/meteo_t*.png`, `metgif_*.png` ; copies et GIF dans `Maquettes/gameplan/eau_meteo/`. Remet position, heure et caméra |
| `REBORN_EAU_DEBUG=1` | Marche sur l'eau (15/10, `eau/EauDebug`, permis forcé, allures simulées) : mer ouverte **profonde** cherchée par le serveur intégré (fond à 10 blocs, sinon 3), joueur tenu en l'air tant que l'eau n'est pas arrivée chez le client puis posé dessus ; immobile, marche, trot, sprint (dos), de face, 1ʳᵉ personne (regard vers le bas en avançant, puis à reculons pour voir la traînée de flaques, comme la référence Shinoda), saut et retombée, s'enfoncer (accroupi), remonter (Saut), un mannequin qui traverse (« autre joueur ») ; retour au large avant la 1ʳᵉ personne, le saut et l'autre joueur ; angles tenus à chaque tick et aucun écran (la souris ou une perte de focus ne dérangent pas le banc) ; **films « 60 i/s »** : pendant la marche, le sprint et la vue vers le bas, le monde passe à `/tick rate 1` et chaque image rendue est capturée (fin du rendu du monde, sans HUD, demi-définition) → `eaufilm_*` à remonter en MP4 60 i/s (l'horloge des ondes suit alors le temps du monde) → `run/screenshots/eau_*.png` + images de GIF `eaugif_*` ; coût CPU du rendu dans le log (`[eau-banc]`). Monde **« New World »** (Konoha n'a pas de mer). Flaques : `Maquettes/gameplan/eau_v8/` (v7 : `eau_v7/`, v6 : `eau_v6/`, v5 : `eau_v5/`, v4 : `eau_v4/`, v3 : `eau_v3/`, v1 : `eau_meteo/`) |
| Avec **Sodium** | Les bancs de l'eau et de la météo ont été validés avec le pack du launcher copié le temps des essais dans `run/mods` (sodium, sodium-extra, lithium, entityculling, ETF, YACL, fabric-language-kotlin), puis retiré ; `run/config` et le monde de dev remis à l'identique |
| `REBORN_AUDIT=1` (+ `REBORN_AUDIT_SCALES`, `REBORN_AUDIT_ONLY`) | Audit responsive : écrans et états du HUD × échelles. Relève aussi le **nombre d'éléments GUI** de chaque capture (DA §6, règle 7 ; `audit/GuiCompte`) dans `run/audit_elements.txt` |
| `REBORN_AUDIT_ONLY=menu_hud,menu_viseur,menu_camera,menu_animations,menu_photo,menu_galerie,menu_signaler,menu_deconnexion,menu_tirage,menu_cosmetique` | Écrans en jeu du lot du 13/10 (dont `menu_galerie_detail` / `_retouche` / `_partage`, `menu_tirage_confirmer` / `_resultat`, `menu_cosmetique`) |
| `REBORN_AUDIT_ONLY=hud_ko,hud_volonte,hud_cooldowns,hud_combat` | HUD discrets : KO (à terre, carte, inconscient, ATA), Volonté (invitation, épreuve), recharges (`hud_cooldowns` : dash, double saut et saut de chakra en recharge + combat et course de chakra forcés par `CooldownState.debugActifs`) |
| `REBORN_AUDIT_ONLY=menu_tirage` | Test de la Feuille refait (14/10) : `menu_tirage_signes`, `_confirmer`, `_concentration`, `_reaction_<nature>` et `_resultat_<nature>` pour les 5 natures, `_reaction_double` (Katon + Fūton), `_resultat_double` (Raiton, Suiton déjà éveillé) ; écran ouvert à une étape précise par `TirageScreen.banc(nature, autres, étape)`. GIF de chaque réaction générés par `tools/gen_tirage_textures.py` → `Maquettes/test_feuille/` |
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
- **Pas d'entités factices hors monde dans un écran** (gel au join) — cf.
  [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) §5.
- Payloads : JSON UTF-8 brut sans préfixe de longueur ; enregistrer le type dans les deux sens si le mod envoie.
- Le jar fait ~23,9 Mo. **Un jar qui grossit fortement = un asset intrus** (cf. publication).
- Plaques de nom : positionner sur l'image interpolée (partialTick), pas sur le dernier tick.
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
