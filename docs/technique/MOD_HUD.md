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
| `sol/`, `notif/` | Ramassage volontaire (touche F, invite), lueur de rareté au sol (`AuraSol`, v3 du 15/10 : petit halo pixel art `gui/inventaire/lueur.png` 44×11 — 4 images 11×11, halo seul puis 1-2 pixels qui scintillent — généré par `tools/gen_inventaire_textures.py`, teint à la couleur de la rareté, respiration d'alpha ; objets en vue seulement ; sélection par tick, ≤ 48 objets à 16 blocs, un quad par objet en un seul lot `submitCustomGeometry`), rareté client ; ramassage F actif en survie, aventure et créatif ; notifications en bas à droite et état de charge (`reborn:notif`), indicateur de surcharge sous les vitals (13/10). **Colonne bas-droite du lot 2** (01/11, étape 2b, maquettes c1 à c4) : `BasDroite` (une seule échelle kHud pour qui parle, notifications et rappels ; sous-titres vanilla : rappels masqués, qui parle et notifications remontés 15 px au-dessus de leur case, montée immédiate, descente après 1 s — `mixin/SousTitresAccessor` + `HudAccessor#reborn$sousTitres` ; carte d'aperçu de capture, passée en unités kHud : notifications et rappels masqués 6 s) ; `NotifHud` sans voile, texte au moteur `TexteRaster` (Inter capitales 10 px fini à 1860 px, filet à 1895 px, ligne la plus récente à 952 px, plancher kHud = 2), aucune allocation par image |
| `ko/` | HUD du KO et de l'ATA, QTE Volonté, menu des corps (`CorpsClient`), scène de mort (`DeathScreen`), micro à terre (`MicroATerreHud`, 31/10 : le micro seul, à sa place, quand le KO masque les vitals — §10) |
| `staff/` | Poste de garde v3 « le volet » (14/10) : `StaffScreen` (volet, onglets, alertes, liste, menus), `GardeFiche` (fiche compacte, sections, sanction), `GardeRecherche` (recherche universelle, hors ligne et actions), `GardeJournal` (jetons, jours), `GardeOutils` + `GardeAnnonces` (outils, chat, commandes, modes), `GardeNarration` + `GardeEditeur` (outil « Narration », lot 2 : texte sur plusieurs lignes à pastilles de couleur, portée selon le grade, durée, mode, tchat, aperçu à taille réelle sur le jeu par `recit/RecitHud.Apercu` et « Plein écran », liste « En cours » avec Arrêter / Tout arrêter ; requêtes `narration`, `narration_fin`, `narrations`), `GardeFormulaire` (formulaire dans le volet), `GardeActions` (catalogue des actions et raccourcis), `GardeArt` (palette, icônes `garde/icones.png`, pinceau en lots) ; `GardeScreen` = l'écran ouvert, avec l'Infirmerie dans Outils ; effets staff en jeu (`EffetsStaff` : annonce, écran noir, séisme via `mixin/CameraSeismeMixin`, indicateur du mode staff / animation ; `NametagsStaff` : nametags du mode staff ; canaux `reborn:annonce`, `reborn:ecran`, `reborn:plaques` (nom de canal gardé) par `CanalJson`), serveur simulé du banc (`StaffDemo`) |
| `faction/` | Faction et sections (16/10, canal `reborn:faction`) : `FactionScreen` + `FactionPages` (volet J, maquettes v3), `FactionArt` (kit du volet, atlas `faction/insignes.png` de `tools/gen_faction_textures.py`, têtes RP, capuche des inconnus, vignette de tenue), `FactionHud` (invitation), `FactionClient`, `Insignes` (atlas pour le Poste de garde), banc `FactionDebug` / `FactionDemo` ; outil staff : `staff/GardeFactions` |
| `parchemin/` | Bibliothèque, lecture, réglages staff, concentration du chakra |
| `chat/` | Chat en panneau (onglets Général / RP / Groupe, recherche, mode /me, icônes Lucide) |
| `radio/` | Radio en escouades (14/10, canal `reborn:radio`) : `RadioClient` (état, invitation, qui parle), `RadioScreen` (écran de l'escouade : appui bref sur B), `RadioHud` (élément HUD « Escouade »), `RadioMenus` (menu R : Radio, Oreillette), `RadioDebug` (banc) — [`../gameplay/RADIO.md`](../gameplay/RADIO.md) §12 |
| `voice/` | Plasmo Voice (client 2.1.17, `compileOnly`) : `RebornVoiceAddon` (réglages Reborn de Plasmo Voice, cercle de portée aux couleurs du micro), `VoiceState` (portée de la voix, micro / casque, qui parle ; relu à chaque tick — §10), `RadioVoix` (son radio, icône masquée, touche B) ; **qui parle en bas à droite** (lot 2, 01/11) : `QuiParleHud` (élément `QUI_PARLE`, ligne de proximité seule, 3 lignes, soi en bas quand on émet avec la puce de la portée, « INCONNU » #A8A0A0 pour un masqué, noms dans la police du récit, vumètre de 5 barres d'1 px, 60 % au-delà de 16 blocs ou sans entité chargée), `VoixNiveaux` (niveau réel des voix, `AudioSourceWriteEvent` / `AudioCaptureProcessedEvent`), `VoiceState` garde aussi UUID, heure d'arrivée et niveau des orateurs, y compris ceux dont l'entité n'est pas chargée ; au-dessus des têtes (31/10, §9) : `SpeechBubbles` (la bulle, sans fond : voix = 3 barres, frappe = 3 points) et `MeBulles` (/me au-dessus de la tête, sans fond, canal `reborn:me`, `chat/MePayload`) |
| `combat/` | HUD combat : endurance en ensō autour du viseur, chiffres de dégâts, combo, icônes de recharge |
| `map/` | Carte du monde (touche M), intro en nuages |
| `interaction/` | Mode interaction (touche R) : curseur, menus contextuels (joueur, corps, entité, bloc, soi) |
| `camera/`, `screenshot/` | Caméra 3ᵉ personne (touche Vue = F5 ; réglages dans Réglages → Caméra), mode photo (filtres, cadres), galerie (ouverte depuis le mode photo) |
| `nametag/` | Nametags : les noms au-dessus des têtes (4 styles au choix), affichés **au regard** depuis le 31/10 (`Nametags`) ; `CalqueTete` : la pile commune au-dessus de la tête (nametag, bulle, /me, nametag staff, invite) ; banc `NametagsDebug` — §9. Package `nameplate/` et « plaques » dans le code jusqu'au 02/11 |
| `mudra/` | Mudras au clavier (14/11, §1 sexies) : barre des douze signes sur U, rangée des chiffres, canal `reborn:mudra` ; portes scellées et apprentissage côté serveur |
| `byakugan/`, `meteo/`, `effets/` | Byakugan (lot 5 : touche O, canal `reborn:dojutsu`, papier et encre, traits en fin de volume, §1 quinquies), météo client (pluie, vent de sable, brume, **blizzard, tempête de sable** — 15/10), filtres plein écran |
| `eau/` | **Marche sur l'eau** (15/10) : `MarcheEauClient` (permis `reborn:eau`, surface solide pour le joueur local via `mixin/BlockStateEauMixin`, accroupi = s'enfoncer, Saut = remonter), `Ondes` (marcheurs détectés sur chaque client ; à chaque pas un **pli de la texture d'eau** autour du pied, gouttelettes, bruit de pas dans l'eau ; **réglages = constantes en tête de la classe**), `EauRendu` (patch de la texture d'eau aux UV déplacés autour d'un contour irrégulier, après l'eau translucide ; l'eau du jeu n'est pas modifiée), `EauDebug` (banc) |
| `meditation/` | **Halo de méditation** (v2 du 18/10, refait sur la vidéo de référence) : `Aura` (forme et teintes mesurées sur la vidéo : disque sombre r 0,48, couronne jusqu'à r 1,1, une couleur d'aura → teinte + lumière de la couronne, voile du disque, étincelles), `MeditationClient` (canal `reborn:meditation` dans les deux sens : méditants à portée + couleur, couleur du personnage actif, choix envoyé au serveur ; fondus ; relevé du sol et de la lumière du lieu sous chacun à chaque tick), `MeditationRendu` (après le terrain translucide, quatre passes sans écriture de profondeur : **disque** translucide éclairé par la lightmap, **couronne** en deux passes émissives qui respirent (teinte translucide puis lumière additive), **étincelles** additives face caméra halo + cœur ; textures fabriquées au premier usage, 512² / 256² / 32², filtrage linéaire — rond et lisse ; un quad par bloc couvert posé sur la forme de collision, décalage de couche de l'ombre du jeu), `MeditationAnimation` (pose **v4** `animations/meditation.json`, faite dans Blender sur le rig Emotecraft et **calée automatiquement sur 8 images de la vidéo** (`tools/emotes/`, voir son README) — assis bas en tailleur, jambes repliées en masse devant le buste, bras croisés haut sur la poitrine, dos rond, tête baissée ; respiration 4 s + balancement 8 s, boucle de 8 s — sur un layer PlayerAnimationLib dédié, priorité 2050, jouée en boucle sur chaque méditant de la liste du halo — soi et les autres, sans synchro de plus —, fondu d'entrée 8 ticks, `meditation_fin.json` en sortie ; les plis genoux/coudes demandent BendableCuboids, présent dans le pack du launcher), `MeditationDebug` (banc ; `REBORN_MEDITATION_DEBUG=pose` : essais `run/med_essai_<k>.json` aux 4 angles calés de la vidéo, corps tenu au sud, + `REBORN_MEDITATION_FILM=1` : film de 8 s de l'essai 0). Choix de la couleur : `menu/character/perso/PersoAura` (Réglages › Interface › Méditation ; réutilise `PersoCouleur` tel quel, « Par défaut » + 5 auras toutes faites, aperçu herbe / pierre / nuit calculé avec les formules d'`Aura`, monde visible derrière : le halo du joueur qui médite change en direct) |
| `skin/` | Compositeur de skins Reborn (`RebornSkins`, `SkinSpec`) |
| `editor/`, `element/`, `config/` | Éditeur de HUD (déplacer / redimensionner / masquer), éléments, presets. Depuis le 19/10 (GAMEPLAN §4) : élément **`EFFETS`** (zone d'effets, `nourriture/EffetsHud`, ×0,80 sous les vitals, visible) et **`BOUSSOLE`** (`boussole/BoussoleHud`, en haut au centre, **masquée par défaut** : `HudElement` porte une visibilité par défaut, et un preset ancien sans l'élément prend ce défaut) ; aperçus d'exemple dans l'éditeur. Depuis le 01/11 (lot 2) : élément **`OST`** « Musique » (`ost/OstHud`, en haut à droite, boîte en pixels écran × kHud/2 ramenée en GUI), option « Masquer après 10 s » dans l'inspecteur (`RebornPrefs.ostMasquerApres10s`) ; l'état par défaut d'un élément est une instance partagée (`stateOf` n'alloue plus à chaque image). Étape 2b : éléments **`QUI_PARLE`** « Qui parle » et **`RAPPELS`** « Rappels de touches » (ancre en bas à droite, boîtes en pixels écran × kHud/2, aperçus d'exemple dans l'éditeur), aussi masquables dans Réglages › Interface › « Voix et touches » (même réglage que l'œil de l'éditeur) |
| `boussole/` | Boussole horizontale (19/10) : `BoussoleHud` (filet, graduations 15°, N E S O, chevron du cap, marqueur personnel, repères, lieux de la carte à moins de 250 blocs), `Reperes` (API du marqueur personnel §36 et des repères §49), banc `HudQuatreDebug` |
| `nourriture/` | Nourriture (14/10) et zone d'effets : `EffetsHud` (élément `EFFETS` : état du serveur `reborn:etat` — douleur, ATA, prison, menottes, étourdi, gel, Affamé / Faim —, satiété, modificateurs `reborn:modifs`), `EffetsIcones` (icônes `gui/effets/<nom>.png`, `tools/gen_nourriture_textures.py` et `tools/gen_etats_icones.py`), `FaimCamera` (caméra qui tangue à 0 %, `mixin/CameraFaimMixin`, coupée par « Effets de distorsion ») — [`../gameplay/NOURRITURE.md`](../gameplay/NOURRITURE.md) |
| `emote/`, `animation/` | Emotes EmoteCraft, menu des animations (touche « . » : émotes et démarches ; pendant une émote, « . » l'arrête par `/stopemote` — lot 2, le menu demande alors un second appui), course de chakra (touche X, `allure/Allure`), synchronisation d'animations. **Créer une animation** (émote, pose, démarche) : chaîne Blender + rig officiel d'Emotecraft dans `tools/emotes/` (spec JSON → export au format PlayerAnimationLib lu par le mod, vignettes, calage sur vidéo) — [`../../minecraft/mod-hud/tools/emotes/README.md`](../../minecraft/mod-hud/tools/emotes/README.md) |
| `ui/` | `MenuScale` (taille des menus), `Da` (kit de dessin DA) ; **moteur de texte du lot 2** (01/11, §4) : `TexteRaster` (FreeType de LWJGL sur le fil `Reborn-Texte`, typographie française, mise en page, couche RGBA calculée une fois, formes du HUD) et `TexteCache` (poignées `Texte`, textures `reborn:texte/<n>` posées à 1:1, LRU de 96 — 32 au départ, 64 à l'étape 2b, 96 à la revue du 02/11 : la colonne bas-droite au complet et l'OST en dessinent jusqu'à ≈ 55 à la fois —, recalcul 150 ms après un changement d'échelle, y compris pour les formes dont le masque change avec l'échelle (éditeur de HUD) ; une poignée jamais dessinée (page préparée d'avance) relance quand même son recalcul ; **fil à priorité** : lignes et formes du HUD passent avant les blocs (narration, aperçu du Poste de garde) ; durée des rendus (moyenne, maximum, lignes / blocs) dans `TexteCache.bilan()`), banc `audit/TexteBanc` |
| `recit/` (revue du 02/11) | **Pages et durées de la narration** : un paragraphe (`\n\n`) commence toujours une page et sa ligne vide ne compte pas ; dans un paragraphe, pages de 3 lignes au plus au moindre coût (`Recit.paginer` : le minimum de pages ou une de plus, pleines de préférence, coupées après une fin de phrase plutôt qu'en pleine phrase, ni dernière page d'une ligne de un ou deux mots ni page qui commence par » ou une ponctuation) ; le **haut** du bloc reste celui de la plus haute page (la première ligne ne saute pas). Durée automatique = clamp(1,3 s + n / 30 ; 4 ; 60 s) **+ 1,2 s par page au-delà de la première** (deux fondus de page) ; chaque page a ses fondus plus une part du reste au prorata de ses caractères (une page pleine tenue ≈ 7 s à pleine opacité). Le serveur estime les pages (`Narrations.pagesEstimees` : 70 caractères par ligne, 3 lignes, un paragraphe par page) ; le libellé « Auto · N s » du Poste de garde compte les vraies pages. **Stèle** (`Recit.lire`) : aucune minuterie, pages comprises — `Recit.lireSuite(id)` tourne la page (action du joueur, lot 4) ; sous une narration elle s'efface et revient à sa page quand plus aucune narration n'est à l'écran **ni en file** (pas de retour de 400 ms entre deux). **Aperçu du Poste de garde** : 250 ms après la dernière touche ; un bloc qui ne tient pas à gauche du volet avec 24 px de marge est montré **réduit** (« Aperçu réduit · Plein écran pour la taille réelle ») au lieu de passer sous le volet |
| `ost/` | **Lecteur OST en haut à droite** (lot 2, 01/11, variante B) : `OstHud` (élément `OST` « Musique » de l'éditeur : trois barres, titre = catégorie de la piste, filet d'or et losange, au moteur `TexteRaster` en pixels écran × kHud/2 ; 100 % pendant 10 s puis 70 %, pause à 55 % avec « ‖ », option « Masquer après 10 s » ; masqué au KO, en douane côté candidat, sous l'écran noir du staff ; passe sous l'indicateur du mode staff / examinateur et sous la pile des bandes de la cloche) et `OstLien` (état publié par reborn-ost ≥ 0.2.6 dans l'`ObjectShare`, clé `reborn-ost:lecture`, lu par référence ; dépose `reborn-hud:ost-rendu`, qui coupe le bandeau de secours de mod-ost) |
| `recit/` | **Texte au centre** (lot 2, étapes 2c et 2d) : un texte posé au milieu de l'écran, sans fond, au moteur `TexteRaster`, mots de couleur gardés (blanc par défaut). **Deux allures distinctes** (décision du 02/11 : la narration n'est pas le texte des stèles), réglées **chacune en un seul endroit**, `Gabarit` (police, préréglage contour / ombre / gamma, capitales et interligne en 1080p, largeur du bloc en px 1080p et / ou en part de l'écran, centre vertical, rayon du contour, ombre douce, approche, coupes de ligne figées, voile plein écran, fondus, règle d'échelle) : **`Gabarit.NARRATION`**, la narration du staff, plein écran, blanc pur, plus grande = la **variante V2 « Titre droit »** de la planche `Maquettes/gameplan/17_lot2_ketto/d1_narration_plein_ecran.png`, choisie par le propriétaire le 02/11, en **Philosopher Bold** depuis le 02/11 au soir (`assets/reborn/font/narration/philosopher-bold.ttf`, OFL, `ofl.txt` ; Marcellus Regular rejetée : « apparence qui ne corrèle pas avec l'univers », retirée du jar ; Philosopher n'a aucune voyelle à macron précomposée : `TexteRaster` les compose, lettre de base + U+00AF, comme le Python), blanc pur sans mot coloré par défaut, capitales gardées (33,6 px demandés, 33,7 obtenus avec la hauteur d'x calée sur 24 px) et interligne 58 px en 1080p, approche 0 (celle de la planche e1 où Philosopher Bold a été choisie ; +0,5 retiré à la revue du 02/11), bloc ≤ 1 536 px (80 %) et ≤ 90 % de l'écran, **bas du bloc à 47 %** (le bloc grandit vers le haut, réticule et tête libres), contour rond 2 px à 100 % (au 1/16 px, 1 px au moins), ombre douce 2 px vers le bas floutée σ 3 px à 40 %, **vignette** très douce (coins −30 %, centre intact, smoothstep de 30 % à 100 % de la distance au coin ; masque 320 × 180 créé une fois ; **posée sous le HUD** (couche `reborn-hud:recit-vignette` juste après les surcouches vanilla : elle assombrit le monde, jamais les vitals, la colonne bas-droite ni l'OST ; en F1 « BARS », posée sous le texte) et **elle suit la narration entière** : entrée avec le fondu d'entrée de la première page, 1 entre les pages et pendant la pause de 400 ms d'une file, sortie avec le fondu de sortie de la dernière page ou de l'arrêt — seul le texte fait ses fondus de page (revue du 02/11)), coupes de sens (après . ! ? : ; …) figées à la référence 1080p, tout × s = H/1080 ; **narrations courtes : 3 lignes par page** (`lignesMax`, « pas trop long » : 3 lignes chez Ketto), des pages au-delà ; **fondus d'opacité seuls** mesurés sur les vidéos de Ketto (`Maquettes/gameplan/17_lot2_ketto/mesures_ketto_narration_concentration.md`) : entrée 600 ms (ease-out), sortie 700 ms (linéaire), 600 ms de chaque côté entre deux pages, coupe (`remplace`) 150 ms, ni glissement ni changement de taille ; **`Gabarit.NARRATION_V1`** (« Grand récit », Alegreya Sans Bold Italic 38 / 64 px, sans vignette, 4 lignes par page) reste pour comparer au banc ; **`Gabarit.STELE`**, le texte des stèles, plaques, pancartes et objets lu **seulement en concentration, en visant l'objet** (lot 4, pas encore branché) = le rendu validé des maquettes a1 à a8 (Alegreya Sans Bold Italic, capitales 12 px, interligne 21 px, largeur ≤ 340 px, centre à 54 %, préréglage RÉCIT, × f = max(kHud/2 ; H/1080)), fondus mesurés chez Ketto : entrée 250 ms, sortie 350 ms, aucune minuterie (le texte reste tant que la concentration dure), caché sous une narration ; **concentration** (lot 4, pas encore branchée) : `Recit.concentration(true/false)` assombrit légèrement les bords de l'écran (`Gabarit.BORDS_CONCENTRATION` = vignette de la narration, coins −18 %, 250 ms à l'entrée, 350 ms à la sortie depuis l'opacité du moment), avec ou sans texte ; avec une narration, la plus forte des deux vignettes. `Recit` = le service, une seule place au centre : `montrer(id, segments, s, mode, pos, ecoule, gabarit)` (le message `narration` champ pour champ, l'appelant choisit l'allure ; `s` : `AUTO` = clamp(1,3 s + caractères reçus / 30 ; 4 ; 60 s) (règle du 02/11 : 3 lignes pleines ≈ 210 caractères tenues ≈ 7 s, plus les fondus 0,6 + 0,7 s ; même calcul côté serveur, `Narrations.dureeAutoMs`), 2 à 180 s, `TENIR` jusqu'à `cacher` avec un garde-fou de 10 min, toute autre valeur = `AUTO` ; `FILE` : 400 ms entre deux, 8 en attente, une 9e fait sortir la plus ancienne en attente ; `REMPLACE` : coupe en 150 ms et vide la file ; un id déjà à l'écran ou en file est un doublon, ignoré ; `ecoule` = rattrapage : il reste D − ecoule, en partant de la page dont la tranche contient `ecoule`), `cacher(id)` (fondu de sortie du gabarit : narration 700 ms, stèle 350 ms ; id vide = tout), `toutCacher()`, `lire(id, segments)` pour une stèle (allure STELE, tenue, remplacée en 150 ms par une autre ; une narration passe devant, la stèle revient ensuite), `vider()` à la déconnexion ; 1 000 caractères, pages de `Gabarit.lignesMax` lignes (12 pour la stèle, 3 pour la narration ; moins si la fenêtre ne les tient pas) coupées de préférence sur un paragraphe, au gabarit et à l'échelle de la fenêtre (ou à la référence 1080p si les coupes sont figées), durée au prorata ; le minuteur court même masqué. `RecitHud` = l'élément `reborn-hud:recit`, juste après `staff-effets` (au-dessus de l'écran noir) : un blit à l'allure du gabarit de chaque texte, fondus par l'alpha des sommets aux temps du gabarit ; à terre 40 % (et une interligne au-dessus du groupe « À terre », `KoHud.hautATerrePx`, pour un grand texte), inconscient sous le bloc KO (max(60 % ; bas du KO + 2 interlignes + n × P/2)), sous le texte de l'écran noir (+ 2 interlignes), décalé à droite du tchat ouvert qui le touche s'il y tient avec 24 px × f de marge des deux côtés, sinon (narration en petite fenêtre) centré et remonté au-dessus du panneau, ou posé sur le tchat s'il n'y a pas la place ; visible en F1 « BARS » (depuis `mixin/InGameHudCinemaMixin`), masqué en « CLEAN », en mode photo et sous un autre écran ; titre vanilla masqué pendant l'affichage, **son minuteur attend** (« Épuisement », « Bienvenue »… passent après, en entier ; `mixin/HudAccessor#reborn$titreTemps`) ; viseur masqué seulement quand le bloc couvre le centre de l'écran (une stèle ; la narration, bas à 47 %, le laisse : un event `+tenir` se joue viseur compris) ; noms au regard, bulles et /me qui touchent le bloc élargi de 12 px à 25 % (`RecitHud.attenuation`, §9). `NarrationClient` (étape 2d) = le receveur du canal `reborn:narration` (ShinobiCore `staff/outils/Narrations`, protocole v1, PLUGINS_REFERENCE §4 bis.3 bis) : type S2C enregistré (c'est ce qui fait voir le mod au serveur dans `getListeningPluginChannels` ; sans lui, repli en lignes de tchat italiques), message lu sur le fil client (`client.execute`, allocation à la réception seulement) et passé champ pour champ à `Recit.montrer(…, Gabarit.NARRATION)` / `Recit.cacher` (`narration_fin` à id vide = tout effacer) ; JSON illisible, `v` ≠ 1 ou `t` inconnu ignorés, champs inconnus ignorés, clé de couleur inconnue = blanc ; tout est oublié sans fondu à la déconnexion et à la connexion (le serveur rattrape au 40e tick) |
| `audit/` | Bancs de test : `ResponsiveAudit`, `ConcentrationPreview`, `FeaturePreview` |
| `anchor/` | Ancre de tête (31/10, §9) : `TeteAncre` (centre de la tête réellement rendue, capté au rendu par `mixin/LivingEntityRendererTeteMixin` dans la passe des entités bornée par `LevelRendererTeteMixin`), `ProjectionMonde` (projection identique au monde, balancement et nausée compris, via `GameRendererTeteAccessor`), `OcclusionBlocs` (bloc opaque entre la caméra et la tête), banc `TeteAncreBanc` |
| `mixin/` | Tous les mixins (déclarés dans `reborn-hud.mixins.json`) |

## 1 bis. Vitals compacts (0.4.198, 05/11)

Hors combat, `runtime/VitalsHud` dessine un **compact** (tête 32 px dans le cadre réduit, filet de vie au-dessus, filet de chakra en dessous, sans chiffres ; micro à droite ; satiété en filet de 2 px seulement sous 25 %). `runtime/VitalsMode` (états COMPACT / OUVERTURE / COMPLET / REPLI) ouvre le **bloc complet** en combat : coup donné ou parade, coup reçu d'une entité, technique (chakra −3 % ou −300 u en 1 s, ou endurance dépensée hors sprint), arme dégainée, duel, KO ; repli 300 ms après 8 s de calme. Soin, chute, feu, chakra de déplacement : ouverture 2,5 s. Un changement de maximum n'ouvre rien. Vie ≤ 25 % en compact : le filet bat (1,2 s), hystérésis à 30 %. Morph sur la grille GUI (180 ms). L'éditeur de HUD montre toujours le bloc complet. Préférence `RebornPrefs.vitalsToujoursDetailles` (Paramètres › Interface › HUD, étiquette NOUVEAU à retirer à la version suivante). Banc : `REBORN_VITALS_DEBUG=1` → `run/screenshots/vitals_*.png`. Maquettes : `Maquettes/gameplan/18_vitals_compact/`.

## 1 ter. Concentration (lot 4, étape A, 0.4.199, 07/11 ; touche maintenue et effet K, 0.4.201, 08/11)

Client seul ; plaques et traces de pas viendront avec ShinobiCore (étapes B et C du cadrage
`Maquettes/gameplan/17_lot4_concentration/CADRAGE.md`).

- **Une seule classe** tient le mode : `concentration/ModeConcentration` (`entrer`, `sortir(Raison)`, `actif`, `depuis`,
  `reinitialiser`). **Touche Concentration (Y) maintenue** (décision du 08/11, plus aucune bascule) : on est concentré tant
  qu'on la tient ; l'ancienne touche « Interaction » (R), si elle est restée liée, se maintient de même (l'une ou l'autre tenue
  suffit). `ModeConcentration.maintien`, à chaque tick, relit l'**état physique à GLFW** (`Clavier` : clavier ou bouton de souris,
  non liée = jamais), l'état de `KeyMapping` étant vidé sous un écran ; fenêtre quittée = relâchée, y revenir touche enfoncée n'est
  pas un appui.
  - **Un nouvel appui est demandé** (`attendRelache`) après une entrée refusée, une sortie imposée par le jeu (coup, garde, dash,
    sprint, course de chakra, KO, duel, Échap, cinématique, écran, refus du serveur…) ou un appui sous un écran (la lettre tapée
    dans le tchat ou un champ, relevée à l'appui lui-même par `evenementClavier` et `boutonPresse`, pas au tick : « y » puis Entrée en
    roulement n'ouvre rien) : rien ne se relance tout seul, rien ne clignote en combat.
  - **Répétition du système** (`evenementClavier`, `caractereRepete`, `KeyboardInteractionMixin`) : un écran ouvert doigt sur une
    touche pressée hors de tout écran ne reçoit ni sa répétition ni ses caractères, tant qu'elle n'est pas relâchée ou qu'une autre
    touche ne lui a pas pris la répétition. Sans ça, Y tenue (ou Z en marchant) et « Action RP (/me)… » du menu donnent `/me yyyyyy`.
  - **Menu ouvert au clic** : il reste après le relâchement, avec le mode et l'effet ; à sa fermeture (clic dehors, action
    choisie), touche relâchée le mode finit, touche tenue il continue. **Échap** sort de tout sans ouvrir la pause
    (`KeyboardInteractionMixin`). **Tchat** ouvert touche tenue : le mode reste ; relâcher finit le mode, le tchat reste ouvert.
  - **Appuis rapides** : `ANTI_REBOND_MS` (300 ms) entre deux entrées, pour rester sous la limite du serveur (4 `on` par seconde
    glissante, `etat on:false` au-delà ; `off` jamais limité) ; un appui trop tôt attend et n'entre que s'il est encore tenu. Le fondu
    repart d'où il était. Souffles espacés de 600 ms au moins ; pas de souffle de sortie pour un appui plus court que l'entrée (250 ms).
- **Moteur = l'ancien menu R** (`InteractionMode.ouvrirCurseur()`, `menuOuvert()`) : curseur au centre, caméra figée,
  **clic droit maintenu = la caméra tourne** (curseur caché, `MouseInteractionMixin`), clic gauche sur une cible = menu
  d'interaction (notre kit). Porté ou entravé (`int` refusé) : le mode reste, le clic sur une cible ne fait rien.
  L'étiquette « Clic interagir » ne suit le curseur que sur une cible interactive (entité, cloche, volière).
- **Effet d'écran, recette K+** (décisions des 08 et 09/11 : « les coins et cet effet plus sombre », avec « ce petit effet de noir et blanc » et
  l'effet pâle, « pas aussi prononcé » ; remplace le « presque noir et blanc » du 07/11 ; base tirée des mesures de Ketto,
  `Maquettes/gameplan/17_lot4_concentration/_etude_ketto.md` §2.1, un cran plus sombre au choix du propriétaire) : post-effet `post_effect/concentration_ecran.json` +
  `shaders/post/concentration_ecran.fsh`, une passe : désaturation de 40 %, nuance froide très légère (rouge −2,5 %, vert −0,5 %,
  bleu +2 %), assombrissement elliptique (centre −12 %, bords −35 %, coins −60 %) ; relevé sur les captures de jour : −13 %, −34 %,
  −57 %. Il prend l'unique
  emplacement de `effets/FiltresEcran` : **KO > Byakugan > concentration > météo** (filtre de la météo suspendu le temps
  du mode). Entrée 250 ms (courbe douce), sortie 150 ms ; l'avancement passe au shader par une texture 4×1
  (`concentration_params`), réécrite seulement quand l'octet change (`GameRendererKoMixin`). Sous le Byakugan, repli sur
  la vignette de `RecitHud` (`Gabarit.BORDS_CONCENTRATION`, sortie passée à 150 ms).
- **Contour au survol** : un rayon `CursorRaycast` par tick (pas par image) ; l'entité vivante sous le curseur à 16 blocs
  prend le **contour lumineux vanilla** (« glowing », blanc) par `state.outlineColor` dans
  `LivingEntityRendererByakuganMixin`, chez soi seulement, aucun effet serveur ; jamais sous le Byakugan (il garde la
  priorité), coupé menu ouvert.
- **Rappels** : `RappelsTouches.concentrationBranchee = true` ; temps normal « Concentration [Y] » seule ; en
  concentration la liste de six lignes, Concentration en bas (actions en cours dessous) ; `masquee()` ne cache plus la
  liste en concentration, menu ouvert compris. Le réticule est masqué (le curseur le remplace) ; la couche
  `reborn-hud:interaction` (curseur, menu) est posée juste après `reborn-hud:recit`, au-dessus du texte d'une plaque.
- **Blocages relus à chaque tick** (`refusEntree`, `blocage`) : à terre / mort / voile, duel, **Malaxage du chakra**
  (`ConcentrationHud.actif()`, l'épreuve s'affiche sous ce nom), mode photo, écran noir du staff, cinématique /
  sélection / chargement, douane, garde, sprint, course de chakra, demande d'un autre joueur (porter, fouiller) : refus ou
  sortie ; combat : entrée refusée 5 s après un coup donné ou reçu, sortie au coup, à la garde, au dash ; écran ouvert =
  sortie, **sauf le tchat** ; narration, méditation, cécité : le mode reste ; déconnexion / changement de joueur : remise
  à zéro sans fondu.
- **Touches, migration v5 — retrait de R** (code après la validation en jeu du 07/11, sort avec la version suivante ;
  `TouchesMigration.VERSION = 5`, drapeau `touchesVersion` de `reborn_prefs.json`) : l'ancienne touche « Interaction »
  déliée **seulement** si elle est encore sur son défaut **et** si Concentration a une touche (règle pure
  `TouchesMigration.retraitR`) ; « défaut » = R ou la touche qui affiche « r » où la version 2 l'a posée (BÉPO US L,
  Dvorak US O, Colemak US S : `TouchesMigration.surDefaut`) ; Concentration sans touche (Y déjà prise) = R gardée, elle ouvre la Concentration
  (`HudKeybinds.concentrationEffective`) ; touche Interaction personnalisée = jamais touchée, alias de la Concentration.
  Idempotente (rejouée seulement sous la version 5). Le défaut de la liaison reste R (pour reconnaître la valeur d'origine) ;
  « Réinitialiser la section » rejoue la règle avant celle du clavier (R ne revient pas, ni sur la touche « r » en BÉPO). Libellé de la touche : « Interaction (ancienne
  touche) ». Paramètres › Commandes › Monde : aide de Concentration réécrite, ligne « Interaction » montrée seulement si
  elle a encore une touche (aide selon le cas), refaite dès qu'on change Interaction ou Concentration dans l'écran
  (`KeybindButton.assign` vide le cache de `ControlsTab` et remet la page en page). Plus aucun « R » écrit en dur à l'écran : lettre, faction, viseur,
  invite « Tendre » et rappel sur la touche réelle ; rappel du duel = `/duel abandonner` (la Concentration, donc le
  curseur, est refusée en duel). Banc `REBORN_LOT4_DEBUG=v5` (§5).
- Coût hors mode : la lecture de la touche à chaque tick, rien d'autre (aucun rayon, aucun filtre, texture jamais réécrite). En mode : un rayon par tick, une passe
  plein écran, la passe de contour vanilla seulement pendant un survol.

### Étape B : plaques (0.4.199 aussi, 07/11 ; serveur ShinobiCore `concentration/`, PLUGINS_REFERENCE §4 undecies)

- **Canal** `concentration/ConcentrationCanal` (`reborn:concentration`, enregistré par `Plaques.init` depuis
  `ModeConcentration.init`) : `on` à l'entrée, `off` à toute sortie ; `etat on:false` du serveur (refus, fin imposée)
  ferme le mode (`ModeConcentration.refusServeur`).
- **`concentration/Plaques`** : ancres reçues sans le texte ; **choix automatique** à chaque tick de la plaque en face —
  à sa portée (yeux → boîte), en vue (rayon `ClipContext.COLLIDER`, barrières d'un meuble traversées, même règle que le
  serveur), à 25° au plus de la direction de la caméra ; la plus centrée, la plus proche à 3° près, hystérésis 5°, grâce
  150 ms ; cache du texte par `(id, rev)`, refus = pas de nouvelle demande pendant 1,5 s ; cécité = rien.
  `Plaques.clicPage()` (clic gauche hors cible, `InteractionMode`) tourne la page.
- **Rendu ancré** (`RecitHud.ancres` ← `Plaques.pointTexte`) : point = centre de la face verticale tournée vers la
  caméra (bloc, boîte) ou centre de la boîte (entité), avancé de 0,12 bloc ; projeté chaque image
  (`ProjectionMonde`) ; capitales = 84 / profondeur, de 10 à 16 px, **par paliers de 1 px** (re-rastérisé seulement au
  changement de palier ; décalages entiers, aucune autre re-rastérisation) ; hors champ : fondu 150 ms. Repère
  « 1 / 3 › » sous le bloc. Une narration passe devant, la plaque revient (`Recit`).
- **Halo** `concentration/HaloPlaque` (planche c4b, décision H1) : un quad tourné vers la caméra (texture radiale 64×64
  prémultipliée, calculée une fois, filtrée en linéaire, **ARGB** de `NativeImage#setPixel`) + 6 étincelles (texture 16×16),
  mélange additif `GUI_TEXTURED_PREMULTIPLIED_ALPHA`, dessiné dans le HUD (donc après l'effet d'écran) juste avant le
  texte, à son opacité : il naît et meurt avec lui. Ivoire-or (cœur 255/249/232, lueur 255/204/122) ; violet
  160/108/210 en constante de rechange (`HaloPlaque.VIOLET`). Aucune allocation par image.
- **Couleur du halo par plaque** (0.4.200, planche c6, décision du 07/11) : le texte reçu porte `halo` (absent =
  ivoire-or exact `IVOIRE_OR`, `"aucun"`, ou la lueur `"RRGGBB"`) → `HaloPlaque.code` (`DEFAUT` -1, `AUCUN` -2, sinon
  RVB), gardé avec le texte en cache (`Plaques.Texte.halo`) et par place (`haloA` / `haloB` : la sortante garde sa
  couleur pendant son fondu) ; `RecitHud.halos` ← `Plaques.haloDe`. `HaloPlaque.Palette.depuis(lueur)` : luminosité HSV
  relevée à 0,55 au moins, cœur = 85 % vers le blanc, étincelle = 70 %, voile = teinte −5°, saturation × 1,25 (bornée),
  luminosité^1,6 (mêmes valeurs que la planche c6-11). Textures par couleur dans **6 emplacements fixes** (le moins récent
  est repris et libéré) : recherche linéaire, aucune allocation par image. Toujours dessiné dans le HUD, après l'effet.
- **Outil staff** `staff/GardePlaques` (F7 › Outils › Plaques) : liste, fiche, éditeur (texte `GardeEditeur` 700,
  portée, expiration, **rangée « Halo »** `staff/GardeHalo` — huit pastilles « lumière » (`HaloPlaque.PRESETS`), nom et
  code dessous, « Personnalisée » qui ouvre un sélecteur en ligne : carré saturation × luminosité 100 → 55 % (texture
  128×64 refaite au changement de teinte), barre de teinte, aperçu de la lueur, champ hex (relevé à 55 %), 4 récentes
  (`RebornPrefs.haloRecentes`), défilement automatique à l'ouverture ; ligne « Halo » dans la fiche ; aperçu ancré en
  direct `RecitHud.Apercu.dessinerAncre(…, code)`), sélecteur façon Axiom
  `staff/SelecteurPlaque` (volet replié en bandeau, survol par `CursorRaycast` à chaque tick, clic, Maj + clic = boîte,
  clic droit = caméra, ZQSD passés au personnage, Entrée / Échap) ; repères dans le monde `staff/MondePlaques`
  (`RenderTypes.lines()`, un lot : coins dorés, fil de fer crème, or épais, gris hors de portée), seulement outil ouvert.
- **Traces de pas** (étape C, planche c7, 08/11, `concentration/Traces`, protocole PLUGINS_REFERENCE §4 undecies.5) : sur
  le patron d'`AuraSol`, un quad par pas, un lot, plus un second la nuit pour le contour (`RenderTypes.entityTranslucentEmissive`, géométrie sans capture :
  aucune allocation par image), 256 pas au plus, les plus proches, sélectionnés une fois par tick sur des tableaux à plat ;
  dans le monde, donc sous l'effet d'écran. Semelle 6 × 10 (1 texel = 1/28 de bloc × échelle), pied droit en miroir par les
  UV, sprint = les 6 rangées de l'avant, accroupi × 0,55, chargé × 1,15 et 8 % plus large, opacité 0,92 × âge (« une trace reste »,
  08/11 : pleine jusqu'à 60 % de la vie, puis jusqu'à 25 %) × distance (16 → 24 blocs) × niveau de l'effet. **Effritement** : texture
  `assets/reborn/textures/concentration/traces.png` (`tools/gen_traces_texture.py`, 48 × 48, 6 colonnes d'effacement, 3 ordres
  de grains, 1 rangée sang), colonne choisie selon la fraction de vie (67, 74, 81, 87, 93 %, ± 4 % selon le pas : rien avant 63 %). Le test
  alpha seul n'effritait qu'à la toute fin de la vie sans trouer une semelle fraîche : d'où des colonnes, même coût.
  **Couleur** (Paramètres › Interface › Concentration, `menu/settings/CouleurTracesReglage`) : **« Automatique » par défaut**
  (`RebornPrefs.tracesAuto`, 08/11) : ivoire, sauf sur un sol clair où la semelle est sombre (`#141210`, même texture, même
  effritement). Sol clair = luminance de la couleur de carte du bloc sous le pas ≥ `Traces.SEUIL_SOL_CLAIR` (0,70 : sable, neige,
  béton blanc, quartz, calcite, bouleau, grès ; herbe 0,62, terre, pierre, briques, chemin, bois, sable rouge 0,55 restent sombres).
  Le bloc est lu une fois à la réception du pas, dans le tableau `SOL` ; inconnu (chunk pas là) : blanc, relu chaque seconde ; aucune
  lecture du monde par image. C'est le bloc qui décide, jamais l'heure. La nuit, un sable n'est plus qu'à 10 % de luminance et une
  semelle sombre n'y a plus de marge : un **contour** pâle d'un texel (`traces_contour.png`, opacité 0,38, dessiné dans ses propres
  texels, jamais sur la semelle : ni ordre de dessin ni forme changée ; rangées dédiées au sprint, fermées au talon) la fait lire. De jour il
  se verrait et élargirait le pied : il n'existe que **la nuit** (`Traces.contourNuit`, `Level#getSkyDarken` lu une fois par tick, rien
  jusqu'à 4, plein à 9) ; une grotte de jour n'en a donc pas. Choix manuels : sept
  préréglages + « Personnalisée » (`tracesCouleur`, même sélecteur que le halo : `ui/CouleurTextures`, 4 récentes `tracesRecentes`) ;
  ouvrir « Personnalisée » ne change rien tant qu'on n'a pas touché au sélecteur (depuis « Automatique », curseurs, champ et aperçu partent de la dernière personnalisée) ; « Réinitialiser » revient à « Automatique ». La couleur est posée telle quelle, sans compensation : l'effet en garde 60 % (recette K) et l'assombrit
  comme le reste de l'image ; sur la pierre, l'écart entre canaux reste de 75 (rouge sang) à 95 (ambre) sur 255. Une compensation n'aurait presque pas de marge
  sur un canal déjà au maximum (bleu chakra, ambre), et devrait baisser sa luminance. Sous le Byakugan, ni traces ni étiquette (comme le contour
  de `ModeConcentration.halo`). **Petit panneau au survol** (retour du 10/11, comme chez Ketto) : curseur sur un pas à 8 blocs du joueur
  au plus (projection une fois par tick, un rayon vers le pas retenu : un mur le cache), couche HUD `traces-temps` (avant `RecitHud`).
  Au-dessus du pas, un cartouche de nuit (`#110B0D` à 78 %), filet ivoire d'un pixel à 26 %, un clou d'or 2 × 2 à chaque coin ;
  « Trace de pas » en or `#E8C27A` (police du récit, capitales 9 px), puis l'**âge estimé** en ivoire (Inter Medium, capitales 8 px),
  centrés, sans contour ni ombre (préréglage `PANNEAU` de `TexteRaster`) ; 40 px de haut, largeur = texte + 2 × 11 px (de 116 à
  198 px en 1080p ; Ketto ≈ 168 × 69), le tout × kHud / 2 comme le bas-droite. Un **fil** vertical bleu chakra pâle (`#B4D2F2`, 70 %) descend jusqu'à
  un **point** 3 × 3 cerné de nuit posé sur le pas : le bleu ne se confond ni avec la semelle ivoire ni avec l'or du panneau, et
  c'est le fil du sens du chakra ; sur un sol clair de jour (le contraste automatique), point et fil sont sombres, le point cerné
  d'ivoire. Fil d'au moins 30 px, et 10 px au-delà de la moitié du pas vu à l'écran ; panneau gardé à 8 px des bords (dessous
  s'il n'a pas la place au-dessus), jamais sur le réticule (± 10 px) ; d'un pas à l'autre il glisse en 90 ms (le point du pas
  interpolé dans le monde, la caméra peut tourner) ; fondu 120 ms à l'entrée, 150 ms à la sortie, repris là où il en est.
  **Âge estimé** (`concentration/TracesAge`, règle pure, test `TracesAgeTest`) : un pisteur estime, plus flou à mesure que la trace
  vieillit ; le texte ne change qu'aux bornes d'un palier (minutes entières) : 0 « Laissée à l'instant », 1-4 « … il y a quelques
  minutes », 5-7 / 8-12 / 13-17 / 18-24 « … environ 5 / 10 / 15 / 20 min », 25-34 « … une demi-heure », 35-44 / 45-54 « … environ
  40 / 50 min », 55-74 « … environ une heure », 75-119 « … environ 1 h 30 », 120 et plus « … plus de deux heures ». Textes rendus par
  `TexteCache` (deux poignées), largeur recalculée seulement au changement de palier : aucune allocation par image. Jamais de nom.
  Cécité : rien. Hors concentration : rien.
- **Banc** `REBORN_LOT4_DEBUG=b` (`audit/Lot4bDebug`, serveur simulé côté client) : §5 ; couleur du halo :
  `REBORN_LOT4_DEBUG=c6` (`audit/Lot4c6Debug`) ; traces : `REBORN_LOT4_DEBUG=c7`, `c8` et `c9` (panneau, `audit/Lot4c7Debug`).

## 1 quater. Saut chargé (lot 5, étape A, 0.4.202, 09/11)

Maj + Espace tenues au sol : la charge monte en 1,2 s ; relâcher fait partir le bond. **Le serveur décide**
(ShinobiCombat `SautCharge`, protocole et réglages : [`PLUGINS_REFERENCE.md`](./PLUGINS_REFERENCE.md) §5.1 bis, loi et
chiffres : [`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md) §11).

- **`combat/ChakraJump`** (réécrit) : phases `REPOS`, `CHARGE`, `ATTENTE`, `VOL`. Début → `reborn:combatin` kind 8 ; relâche →
  kind 9 (lacet, tangage de la visée caméra) ; annulation (quitté le sol, écran, garde, état) → kind 10. Réponse sur
  `reborn:combat` type 7 : `GO` (vitesse, charge, recharge, drapeaux) ou `NON` (raison). **La recharge ne part qu'au `GO`** :
  plus de recharge fantôme. Sans réponse en 1,5 s : « Pas de réponse du serveur ». Un `GO` arrivé hors de sa fenêtre (plus
  de 3 s après la relâche, ou pendant une nouvelle charge) n'est pas joué : aucune recharge, kind 11 au serveur, qui
  rembourse. Réglages reçus à la connexion (type 8) ; **serveur d'avant le lot 5** (aucun réglage reçu après 5 s de jeu) :
  l'ancien saut (kind 5, 35 s), sans ligne de visée ; avant 5 s, « Un instant… » sans recharge. Horloge injectable
  (`ChakraJump.horloge`), touches et serveur simulables (`debugTouches`, `debugServeur`) pour le banc.
- **Gardes du mod** : garde, KO, état du serveur (`mob`, `sau` : la raison du serveur s'affiche), eau et marche sur l'eau,
  recharge. Après un refus, une nouvelle charge attend que les touches soient relâchées. La charge **ferme la concentration**
  (`ModeConcentration.commun`). Pendant la charge, le corps suit la caméra (`KeyboardInputMixin`) : le regard du serveur reste
  dans son cône de 25°.
- **`combat/SautChargeLoi`** : la loi pure (charge, vitesse, coût, recharge, vol, portée à plat, **vraie arrivée sur le
  relief et son bornage** `bornerArrivee`, contrôle du décollage du serveur), **copie exacte** de
  `com.reborn.shinobicombat.saut.SautChargeLoi` ; deux suites de tests jumelles vérifient le même tableau. La suite du mod
  (`src/test/…/combat/SautChargeLoiTest`) ne passe pas par Gradle (les tests du mod ne compilent plus : JUnit absent du
  classpath de test, défaut ancien) ; on la lance avec le lanceur JUnit de `~/.m2` (14 sur 14 au 09/11 au soir, comme le
  plugin). L'aperçu borne l'arrivée sur la carte des hauteurs du client (`SautArc.relief`, `MOTION_BLOCKING`), comme le
  serveur.
- **Vol** : `mixin/SautVolMixin` (sur `LivingEntity.travel`, joueur local, pendant le bond seulement) appelle
  `ChakraJump.volEtape` : déplacement avec la collision du jeu, gravité vanilla, frottement horizontal × 0,98, **aucune poussée
  des touches**. Un mur annule la vitesse sur son axe (on glisse, on tombe à son pied). Réception : vitesse horizontale à zéro
  (on ne glisse pas hors du cercle). Une autre force (coup, technique, double saut du serveur : écart > 0,05 avec la vitesse
  laissée au tick d'avant), l'eau, une échelle ou un coup reçu rendent la main au jeu. `MobilityInput` (double saut) se tait
  pendant le bond.
- **`combat/SautArc`** : l'arc dans le vrai monde, par `Entity.collideBoundingBox` tick par tick (le même déplacement que le
  vol) : arrivée sur le sol, un toit, au pied d'un mur, sur l'eau ; chunk non chargé = arrivée inconnue (aucun aperçu). Recalculé tous les 2 ticks au plus, seulement si la charge, la visée ou la position bougent ; recalculé au `GO`
  avec la vraie vitesse et tenu jusqu'à la réception.
- **`combat/SautApercu`** (10/11, 6e tour : **retour à la poussière violette de 0.4.64–0.4.68**, la seule version aimée ; la
  zone bleue, les quatre aperçus sobres puis le ruban crème façon Ketto avec anneau fin et barre dans le monde sont
  **refusés** et retirés). La trajectoire et l'anneau, en grains de poussière :
  - **Grains dessinés, pas des particules** : le sprite de la poussière de redstone du jeu (`generic_7` à `generic_3` de
    l'atlas des particules, relus à chaque image), teinté du violet chakra **#9B7FE0** (celui de 0.4.64), face à la caméra,
    échantillonnage net. De vraies particules restaient à l'ancienne visée quand on tourne la tête (traînées), n'étaient pas
    bornées sur 90 blocs et écrivent la profondeur. Ici les grains sont toujours sur l'arc du moment.
  - **Vivants** sans hasard par image : chaque grain a sa période (0,55 à 1,35 s) et sa phase ; à chaque cycle il renaît avec
    un petit écart et une luminosité tirés d'un hachage (grain, cycle), puis son sprite décroît du gros grain au petit en
    dérivant à peine ; mêmes écarts de teinte que `DustParticle` (0,6 à 1, puis 0,8 à 1 par canal).
  - **Sur l'arc exact** (`SautArc` garde désormais le chemin des pieds tick par tick, `points()` / `cheminX/Y/Z`), relevé
    de 0,4 bloc (comme 0.4.64) qui redescend sur les 2 derniers blocs jusqu'au centre de l'anneau, où la ligne s'efface
    (1,2 bloc) : le centre reste net.
  - **Un fil, pas une colonne** (2e correction du 10/11 : les grains bornés à 6–20 px, écartés d'une taille, faisaient en
    1re personne une « tornade » de 25 à 40 px) : grain de 0,2 bloc en **perspective vraie**, jamais moins de 5 px ni plus de
    **9 px en 1re personne, 12 en 3e** (1080 p) ; écart à la naissance 0,18 × sa taille (× 1,5 en fin de vie) : il reste sur
    l'arc ; luminosité 0,8 à 1 (plus de carrés lavande délavés sur le ciel). Espacement réglé **à l'écran** : 0,75 × la
    taille vue (pas = taille × 0,75 / part de la tangente en travers du regard, au moins 0,3). Au plus **300 grains** sur
    la ligne (au-delà l'espacement s'élargit d'autant) et **48 sur l'anneau** ; au banc : 44 à 46 grains en 1re personne,
    77 à 120 en 3e, + 48.
  - **1re personne** : un arc vu de dos se projette en ligne verticale, la montée proche par-dessus la descente lointaine.
    La ligne n'y naît donc que sur les **2,5 blocs qui précèdent le sommet** (jamais avant 1,8 bloc d'arc, pleine au plus
    tôt à 6 : saut à plat ou vers le bas) : on voit le fil qui descend du sommet à l'anneau, rien au bas de l'écran. En 3e,
    elle naît entre 0,8 et 2,2 blocs (hors de la tête). Elle **s'efface autour du réticule** (nulle à 22 px, pleine à 50 px).
  - **Anneau** : 48 grains d'au moins 5 px, pleins, posés au sol, un bloc de diamètre de près, **jamais moins de 90 px** au
    loin (4 blocs de rayon au plus), allongé dans l'axe du regard quand on le voit en rasant (au plus × 8) ; il tourne d'un
    tour en 14 s. Seconde passe à travers le décor à 35 %.
  - **Sous le Byakugan** : aucune écriture de profondeur (types `reborn_saut_poussiere` et `_travers` : shader `core/entity`
    du jeu, émissif, atlas des particules, translucide, créés une fois comme ceux de `MeditationRendu`) : le filtre ne cerne
    pas les grains (capture `byakugan_050`). Le filtre ne gardant que la luminance, le violet virait au gris sale et l'anneau
    disparaissait sur la route : sous le filtre posé (`ByakuganClient.filtreAffiche`, suivant son fondu `niveau`), la
    poussière passe en **lavande presque blanche** (`#F0EAFF`) et se lit comme l'encre. Limite : sur un ciel pâle, le haut
    de l'arc reste discret (le dessiner après le post-effet demanderait de toucher au filtre).
  - **Nuit** : émissif, la poussière luit (pleine lumière, c'est un repère d'interface).
  - **Temps** : la ligne apparaît **au seuil** seulement (sous lui, rien ne partirait), en 0,15 s ; elle s'efface en 0,15 s à
    la relâche ou à l'annulation. Fondus en temps réel. Arrivée inconnue (chunk non chargé, vide) ou serveur d'avant le
    lot 5 : rien.
  - Aucune allocation par image (tableaux fixes, émetteurs sans capture) ; deux passes sur l'arc par image (compte puis
    pose), au plus ~2 400 pas de calcul ; 396 quadrilatères au plus (348 + 48 de la passe à travers).
- **`combat/SautHud`** : la **barre de charge**, à l'écran près du réticule, **la même en 1re et en 3e personne**, en pixels
  d'interface entiers (une unité = `echelleGui` px), sans fond ni plaque : plein violet de la poussière (55 % sous le seuil),
  crête claire au bout du plein, piste violette à 22 % (la lavande à 30 % virait au gris de plaque sur l'herbe), liseré
  sombre d'un pixel à 26 %, repère crème au seuil **pris dans l'épaisseur** (rien ne dépasse). Apparaît en
  0,1 s, s'efface en 0,15 s. **Jauge verticale 3 × 24 px à 10 px à droite du centre**, remplie du bas (à 36 px elle
  couvrait l'anneau en 3e personne) : choisie par le propriétaire le 10/11 (« a c'est mieux ») contre un croissant à
  droite du réticule et une règle horizontale dessous, retirés avec leur réglage. Élément HUD `reborn-hud:saut` (masqué au KO, en douane et
  par `FpsDebug`) ; le filet V1 au-dessus de la barre d'action reste **retiré**. Il garde une ligne de texte fine,
  centrée au-dessus de la barre d'action (à la place du filet de sprint, qui la lui cède, `AllureHud`) : raison d'un refus en
  vermillon (« Pas assez de chakra », « Étourdi »…), « Portée bornée : N blocs (distance de vue) », « Charge bornée par le
  chakra » (`VitalsMode.chakraPart`) ; textes mis en cache : aucune chaîne ni aucun `Component` créés par image.
- `runtime/VitalsMode` : le chakra dépensé par un saut compte comme un déplacement (bref), via `ChakraJump.depenseRecente`.
  Le Byakugan ouvert retire sa seule dépense (2 millièmes de la réserve sur la fenêtre d'1 s) de l'écart : une technique
  lancée vision ouverte ouvre bien le mode combat.

## 1 quinquies. Byakugan (lot 5, étape B, 0.4.202, 09/11 ; 8e tour du 14/11)

Refait le 09/11 : « l'encre ne donne rien, c'est pas beau » (l'encre de la maquette) ; `ByakuganVision.java`
(ShinobiSense) ne sera pas commité. Puis, le même jour : « faut tout de même faire comme sur ketto avec les encre c'est
bien » : l'encre revient, blanche et géométrique, tirée de la profondeur (étude Byakugan v2 §3.3 et §5.5).
Un vrai Byakugan à nous : le serveur est ShinobiCore (`dojutsu/ByakuganService`, canal `reborn:dojutsu`,
[`PLUGINS_REFERENCE.md`](./PLUGINS_REFERENCE.md) §4 duodecies), le rendu est ici. Fiche de jeu :
[`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md) §12.

- **Touche O** (`HudKeybinds.BYAKUGAN`, catégorie Monde, « Amis » de 26.2 déliée par la migration 4 ; migration 6 pour les
  autres dispositions), en bascule. Sans droit (règles du serveur : clan, yeux), KO, mort ou capacité `tec` refusée : rien
  n'est envoyé. Sinon `{"a":"byakugan","on":true}` ; le fondu part à la réponse `byakugan on:true`. Fermeture locale tout de
  suite (fondu de sortie), puis `on:false` au serveur. **KO ou mort : coupure nette**, sans fondu (`KoClient.actif()`).
- **`byakugan/ByakuganClient`** (réécrit) : état, fondu `1 − exp(−t / 160 ms)` fini à 600 ms (90 % à 0,35 s), sortie
  linéaire en 270 ms, sans flash ; horloge injectable (`horloge`). Gain adaptatif lu à la position du joueur chaque tick :
  obscurité (lumière ≤ 3 → 1, ≥ 12 → 0) et part « sous terre » (lumière du ciel brute ≤ 8), lissées sur ~0,6 s, posées d'un
  coup à l'ouverture. `frame()` (début de l'image, `GameRendererKoMixin`) ne calcule que le fondu. **Texture de
  paramètres 21 × 1** (`byakugan_params`, détail des pixels dans `shaders/post/byakugan.vsh`) écrite par `vue()` **pendant
  le rendu du monde**, une fois la projection de l'image connue (`GameRendererByakuganDepthMixin`, sur
  `ProjectionMatrixBuffer.getBuffer` dans `renderLevel`), seulement si elle change : fondu, obscurité, banc, distance de
  rendu, sens du fondu, hauteur des nuages au-dessus de l'œil, distance de l'arrière du porteur (1 bloc en première
  personne : la main) et **rectangle de l'écran qui le contient** (coins de sa boîte élargie de 0,3 bloc, projetés), fin et
  **couleur du brouillard** (`FogRendererByakuganMixin`), et le **rayon exact de chaque pixel** : regard, pas horizontal et
  vertical de l'écran, tirés de `(projection pure)⁻¹ × projection du monde × rotation de la vue`
  (balancement de la marche et nausée compris, sans image de retard), neuf flottants (l'œil dans son bloc, qui ne servait
  qu'à la grille, n'est plus envoyé depuis le 8e tour ; phase des boucles d'encre sur 16 bits). **Filtre compilé d'avance**
  (`prechauffer`, dès que les règles du serveur donnent le droit) : la première ouverture ne paie plus la compilation.
  Hors vision : rien (ni rayon, ni filtre, ni texture réécrite, aucune allocation).
- **Filtre** `post_effect/byakugan.json`, **deux passes et la copie**, un sommet à nous (`shaders/post/byakugan.vsh` : lit
  les paramètres trois fois par image et les passe en variables plates ; le rayon, linéaire à l'écran, est interpolé exact).
  **8e tour, 14/11** : le 7e (une grille blanche sur chaque bloc) est rejeté — « le monde fait trop chargé avec des fils de
  partout, fais en sorte de ne les mettre que sur la fin des volumes […] pour les contours de bâtiment, d'arbres », « il
  manque l'effet d'encre » de la référence. **Plus de grille ni de trait de pli** : un seul trait, la fin d'un volume, et le
  rendu papier et encre de l'étude `_etude_byakugan_v3_hd.md` (masses sombres cernées de blanc, sol pâle plus clair que le
  ciel, lavis par la distance, boucles d'encre). Rappel des tours d'avant : **une feuille est une texture découpée**, chaque
  texel transparent est un trou dans la profondeur ; d'où les feuilles pleines pendant la vision.
  · **Feuilles pleines, plantes cachées** (`byakugan/FeuillagePlein`) : tant que le filtre est voulu (fondu de sortie
  compris), le compilateur des chunks rend les feuilles **pleines** comme en graphismes rapides
  (`mixin/ModelBlockRendererByakuganMixin` sur `forceOpaque`, plus le drapeau statique de `LeavesBlock` pour les faces entre
  feuilles) et **ne maille pas les plantes basses** (`mixin/SectionCompilerByakuganMixin` : `VegetationBlock` — herbes,
  fougères, fleurs, cultures, pousses, champignons, baies —, canne à sucre, plantes qui pendent ou montent ; le fluide d'un
  bloc noyé reste). Pourquoi cacher plutôt que filtrer : aucun seuil ne sépare une touffe d'herbe d'une marche dans la
  profondeur, une plante cachée ne fait aucun bruit et ne coûte rien au filtre, et le Byakugan voit au travers.
  `mixin/LeavesBlockByakuganMixin` : pas de feuilles qui tombent (chacune était cernée). **L'option « Feuilles découpées »
  du joueur et `options.txt` ne sont jamais touchés** (un plantage en pleine vision ne laisse rien de changé). Une feuille
  qui tombait déjà à l'ouverture disparaît au tick suivant (`mixin/FallingLeavesParticleByakuganMixin`). Bascule une fois à
  l'ouverture (tout d'un coup : l'encre en a besoin), une fois à la fin du fondu de sortie (coupure nette du KO,
  déconnexion comprises ; un monde chargé pendant la vision est compilé d'emblée dans l'état en cours) : seules les sections
  à portée qui portent une feuille ou une plante (palette de la section, `LevelChunkSection.maybeHas`, sans lire ses blocs)
  sont marquées sales (`LevelExtractor.setSectionDirty`, recompilées en tâche de fond, l'ancien maillage reste affiché
  jusque-là : aucun trou) ; **à la fermeture, par anneaux de chunks du plus proche au plus lointain, deux par tick**
  (~0,35 s : la saccade ne tombe plus sur quelques images).
  · **Passe 1** `byakugan_traits` (pleine résolution, cible `traits`) : sur chaque axe de l'écran, cinq profondeurs
  inversées (± 1 et ± 2 px en 1080p, ± 2 et ± 4 en 2160p). Le plan du pixel est prolongé vers le voisin (pente prise du côté
  opposé) ; **il y a trait si le voisin est derrière ce plan de plus de ~1,35 bloc (+ 2 % de la distance), l'écart compté
  perpendiculairement au plan du pixel** (l'écart le long du regard, ramené par l'angle sous lequel le plan est vu : sa
  variation de distance d'un pixel à l'autre devant l'angle d'un pixel). Un tronc, un mur, une personne devant un fond plus
  loin : trait. Le dessus d'une marche d'un bloc vu en rasant, deux blocs d'un même mur, un coin dont on voit les deux
  faces, un escalier vu de face, un bloc de feuilles qui dépasse d'un bloc : rien. **Bord sur le ciel** : toujours tracé.
  Marqué du côté proche seulement, 2 px de près, ~1,5 px au-delà de 24 à 48 blocs. Sur le porteur (rectangle de l'écran et
  distance), 0,35 bloc suffit : sa silhouette reste cernée près d'un mur, ses pieds posés ne font pas de pointillés.
  **Lavis** : le trait s'éteint de 48 à 130 blocs, puis au bord du monde (95,5 à 100 % de la fin du brouillard du jeu,
  distance cylindrique comme lui). Rien sur le ciel, les nuages (ils écrivent la profondeur en 26.2 : un point dont la
  hauteur tombe dans leur couche est du ciel) ni au-delà de la distance de rendu. Sortie : R = trait pesé de son opacité.
  9 lectures, plus aucune position dans le monde à reconstruire.
  · **Passe 2** `byakugan.fsh` (pleine résolution) : trait lu en **tente 3 × 3** (quatre lectures bilinéaires) puis
  resserré : net, anticrénelé, ~2 px en 1080p ; blanc froid `F5F9FF` à 95 %, bordé d'un liseré sombre très doux (deux
  lectures à 2,5 px en diagonale) ; **sur un fond pâle (au-dessus de 0,6 à 0,7), il passe à l'encre sombre `3D404A`**, sans
  liseré : blanc sur blanc ne se lisait pas (écart voulu avec la référence, à confirmer par le propriétaire). **À l'entrée,
  plein dès 40 % du fondu (~80 ms), avant la couleur ; à la sortie, parti sous 55 % (~120 ms), avant elle.** **Tons de
  jour, « papier et encre »** (`papier`) : sous 0,18 de luminance (feuillages pleins, troncs, ombre profonde) une masse
  sombre presque unie (0,15 à 0,23 : le moucheté des feuilles s'efface) ; au-dessus de 0,5 (sol, mur, toit éclairés) le
  papier (0,82 et plus) ; entre les deux, la pierre à l'ombre en gris de lavis ; 8 % de couleur ; **ciel gris moyen 0,46 à
  0,66, plus sombre que le sol**. **Lavis** : brume claire `CCD1DB` jusqu'à 50 % avec la distance (moitié de l'effet vers
  75 blocs) ; le bord du monde prend le ton que le ciel donne à l'horizon (couleur du brouillard). Nuit et sous terre :
  exposition qui monte près de soi, lueur du proche, il ne reste presque que les traits et le cyan (inchangé).
  **Boucles d'encre** (`boucles`, `ByakuganClient.anneaux`, activées) : quatre grands anneaux au pinceau, plus épais d'un
  côté, centrés près des bords gauche et droit, qui grandissent lentement (~35 px/s en 1080p) puis s'effacent ; −17 % au
  plus sur ce qu'ils couvrent, jamais au centre de l'écran ni sur le ciel ; sans texture ; rayon et présence calculés au
  sommet (phase sur 16 bits, un tour en 64 s). Fonctions communes dans `shaders/include/byakugan.glsl`.
  · **Brouillard** (`mixin/FogRendererByakuganMixin`, priorité 500 comme la météo, avant Sodium) : pendant la vision, le
  voile de la distance de rendu ne commence qu'à 97 % (90 % en vanilla) et le brouillard de l'air part au-delà, avec le
  fondu. Rien dans l'eau, la lave, la neige poudreuse, sous Cécité ou Ténèbres.
  **Mesures du 14/11** (8e tour ; 1920 × 1080, Intel Iris Xe, `byak_fps`, quatre alternances, forêt du banc) : **coût
  ~+1,2 ms** par image (4,98 à 5,02 ms sans → 6,15 à 6,23 ms avec, feuilles pleines comprises ; +1,5 ms tant que les boucles
  étaient calculées à chaque pixel ; ~1,45 ms au 7e tour). **Bascule des feuilles** : à l'ouverture, image la plus longue
  14,0 à 15,7 ms (médiane 6,5 à 6,7) ; à la fermeture, par anneaux, 11,2 à 13,6 ms, 58 à 71 ms d'excès cumulé sur 1,5 s.
  Banc `byak` : 31 vérifications, 0 échec. Bancs : `debugProfondeur` (bandes de 8 blocs), `debugTraits` (traits seuls),
  `debugBrut` (distance brute sur 24 bits, `_prof`).
  Limites connues : un village clair vu de très haut reste pâle (bâtiments et sol au même ton, lavis de la distance) ;
  quelques traits courts à l'intérieur d'un houppier (une touffe devant une autre, plus d'un bloc derrière) ; de près, les
  feuilles gardent un léger moucheté ; pendant la vision, l'herbe haute, les fleurs, les cultures disparaissent ; les
  particules (fumée, coups) sont cernées comme le reste ; avec Sodium, le compilateur des chunks est le sien : feuilles
  pleines et plantes cachées **non vérifiées**.
- **Profondeur et vue** : `mixin/GameRendererByakuganDepthMixin` saute le vidage de la profondeur de `renderLevel` (26.2)
  tant que **notre** filtre est posé (`currentPostEffect()`, pas l'état) : encre, brume, lueur et ciel lisent la vraie
  profondeur. KO, mode photo et autres filtres gardent le vidage vanilla. Main de la 1re personne testée contre le monde.
  Au même endroit, la projection du monde de l'image part au filtre (`ByakuganClient.vue`).
- **Personnes** (`mixin/LivingEntityRendererByakuganMixin`, `ChakraNetworkLayer`) : vivantes à portée (50 blocs), pas
  soi, filtre réellement posé et fondu au-delà de 25 % (piège 8 : jamais de couleurs-clés sans le filtre). Corps en
  silhouette pleine lumière, réseau de chakra animé (4 images) et **aucune autre couche** (vêtements de villageois,
  armure, objets tenus : leurs textures passaient sur la silhouette), dans des **couleurs-clés à vert nul** que le filtre traduit :
  corps `FF00FF` → cyan pâle, réseau `FF0066` → cyan vif, tenketsu `FF00A6` → presque blanc ; de loin, la personne devient
  une tache cyan franche ; **l'encre blanche la cerne comme les blocs** (silhouette et plis de son modèle, nette). Ni la lave, ni le feu, ni un vêtement rouge n'y entrent
  (l'orange d'avant si) ; le corps et le liseré exigent un vert sous la moitié du rouge : un cheveu ou un tissu violet, le
  purpur restent ce qu'ils sont ; seul un béton magenta du décor peut prendre une teinte cyan pâle. **Derrière un mur** seulement (deux rayons, yeux et milieu du
  corps, tous les 4 ticks) : le contour vanilla en `FF00FF` (`mixin/MinecraftByakuganGlowMixin`, côté client, ni drapeau
  « glowing » du serveur ni équipe), liseré cyan pâle et doux vu au travers du mur ; jamais de contour vanilla sur ce qu'on
  voit (l'encre s'en charge, sans doublon). Un liseré posé sur le porteur ou la main (pixel 6) n'en est pas un : le décor
  voisin y revient. Identifiants lus par `mixin/EntityIdByakuganAccessor` (l'entité d'une cage à monstres n'a
  pas d'identifiant en 26.2 : `getId()` lèverait à chaque image).
- **Signe vu par les autres** (`byakugan/ByakuganSigneLayer`, texture `signe.png`) : porteurs à moins de 12 blocs (liste
  `signes` du serveur) : quelques pixels de veines aux tempes, jamais sur les yeux (leur place change d'un skin à l'autre).
- **Rappel** « Vision du Byakugan [O] » (`RappelsTouches.Rappel.BYAKUGAN`), action en cours, sous Concentration,
  **pendant la vision seulement** (décision du 05/11). Aucune annonce, aucun texte au tchat. Sons locaux, à nous
  (`reborn:byakugan.ouverture`, un tintement de cristal tenu puis une longue résonance, 2,25 s ; `byakugan.fermeture`,
  une retombée sourde de 0,55 s ; synthèse `tools/ui-art/gen_byakugan_sfx.py`, contrôlés par mesure, pas à l'oreille).
- **Priorité du filtre** (`effets/FiltresEcran`) : KO > Byakugan (fondu de sortie compris) > concentration > météo ; la
  concentration reste active sous le Byakugan et reprend l'emplacement à sa fermeture. `runtime/VitalsMode` : la dépense
  du Byakugan compte comme un déplacement (jamais le mode combat).
- **Ancien canal** `reborn:byakugan` (ShinobiSense) : toujours écouté (ShinobiSense se tait), suivi seulement tant que
  ShinobiCore n'a pas parlé `reborn:dojutsu`. `ByakuganActivation` (flash et lignes) et l'ancien banc `ByakuganDebug`
  sont supprimés.

## 1 sexies. Mudras au clavier (0.4.203, 14/11 ; barre à la DA des menus Reborn en 0.4.204, 16/11)

Fiche du système : [`../gameplay/MUDRAS.md`](../gameplay/MUDRAS.md). Côté serveur : ShinobiCore `mudra/`
([`PLUGINS_REFERENCE.md`](./PLUGINS_REFERENCE.md)). **Livré éteint** : sans message `regles` actif du serveur, la touche
ne fait rien.

- **Paquet `mudra/`** : `MudraCanal` (canal `reborn:mudra`, JSON `v` = 1, patron de `DojutsuCanal`, crochet
  `debugServeur` pour le banc), `MudraClient` (état de la session, gestes, horloge injectable), `MudraHud` (la barre).
  Enregistrés par `MudraClient.init()` (appelé dans `RebornHudClient`, après le Byakugan) ; couche de HUD
  `reborn-hud:mudras`.
- **Protocole.** C → S : `ouvrir`, `signe` (`n` session, `s` 0 à 11), `fermer` (`n`). S → C : `regles` (`actif`, `max`),
  `ouvre` (`n`, `mode` libre / guide, `temps`, `gain`, `max`, et en guide `total` + `titre` ; `suite` seulement d'un serveur d'avant le 16/11), `fin` (`n`, `res` ok / rate /
  temps / coupe / ferme, `m`), `refus` (`m`), `geste` (`e` entité, `s`). Les rangs 0 à 11 suivent l'enum `Mudra` du
  serveur = l'ordre de la barre (Rat … Sanglier). Le mod ne sait jamais si une porte est proche ni, en mode libre, si un
  signe est juste.
- **Touche** `HudKeybinds.MUDRAS` (**U**, `key.reborn-hud.mudras`, Réglages › Contrôles › Monde) : un appui demande
  l'ouverture (`ouvrir`, oubliée après 2 s sans réponse), un autre referme. **Migration des touches version 7** : même règle
  que la version 6 pour cette seule touche (jamais prise à une autre liaison).
- **Rangée des chiffres** (`mixin/KeyboardMudraMixin`, `KeyboardHandler#keyPress` au HEAD) : barre ouverte et aucun
  écran, les touches `GLFW_KEY_1` à `9`, `0`, `MINUS`, `EQUAL` (positions : sur AZERTY ce sont bien les douze touches de
  la rangée) composent les signes au lieu de changer l'objet tenu ; Échap referme la barre au lieu d'ouvrir le menu. Seul
  un appui pris voit sa répétition et sa relâche prises ; une touche déjà tenue à l'ouverture garde sa relâche.
- **Minuterie locale** (le serveur tranche, le mod suit la même règle pour le filet) : libre, `temps` au départ, chaque
  signe rend `gain` sans dépasser `temps` ; guidé, chaque signe rend tout (le mod ne sait pas s'il est bon : le serveur referme au premier mauvais). Deux signes à moins de 120 ms : le second
  n'est pas envoyé (c'est le `signe-min-ms` du serveur, qu'il compte en moyenne avec 500 ms de marge). Sans `fin` du serveur 1,2 s après l'échéance,
  la barre se referme seule.
- **Barre** (`MudraHud`, pixels écran entiers), **dans la direction artistique des menus Reborn** depuis la 0.4.204
  (16/11). Historique : médaillons ronds beiges en ligne (14/11), agrandis à la taille de la référence puis réduits à
  85 % (15/11) ; jugés « trop ressemblants avec celle de Ketto », le propriétaire demande « le même principe mais plus
  original pour Reborn », précisé « dans le cadre, comme les autres menus Reborn, qu'on garde une DA graphique ».
  Neuf maquettes (`Maquettes/gameplan/19_mudras/maquettes_1611/`) ; retenue : **R2**, « avec les signes comme avant
  (dessins lisses) et pour les touches fais comme avec les trucs concentration ».
  Douze **cases du kit** (laque en creux, liseré d'or biseauté : les cases de la sacoche agrandies), **sans plaque
  derrière**, le vrai dessin du signe dans chaque case ; nom au-dessus en **ArcadePix** ; dessous la **jauge du temps**
  (celle du poids de la sacoche : piste sombre cernée d'or sombre, plein or à crête claire), puis la touche dans la
  **case des rappels de touches** (`RappelsTouches.lotCases` / `caseTouche`, Inter gras de `TexteCache`, 22 px et
  capitales de 10 px en 1080p). Le signe composé passe en **laque rouge** à équerres d'angle, nom en or. En mode guidé, « Technique · i / n »
  au-dessus (Inter gras) et, depuis la 0.4.205 (16/11, « le joueur doit les connaître depuis le parchemin ou via
  professeur »), **aucun signe attendu mis en avant** : le serveur n'envoie plus la suite, seulement `total`, et tout
  signe avance le compte. Si un serveur plus ancien envoie encore `suite`, le signe attendu repasse en rouge et les
  autres pâlissent (`MudraClient.montree`). La fiche d'une technique apprise montre de nouveau sa suite entière, sur
  deux lignes au besoin (la description en rend alors une). Refus
  et message de fin : une ligne 1,8 s (vermillon pour un refus).
  Gabarit au **texel** : un texel = `z` pixels écran, entier (`z` = hauteur / 540 arrondi : 2 en 1080p, 1 en 768p, 3 en
  1440p ; borné à 94 % de la largeur). Case 34 texels, 58 d'axe en axe : 672 texels, soit 70 % de la largeur en 1080p.
  Trois lots (`LotBlit`) : cases, mains, cases de touche. Entrée en cascade (0,4 s), signe = la case se soulève 0,4 s,
  sortie 0,3 s (en descendant si la suite est bonne).
- **Textures** (`tools/gen_mudras_textures.py`, PyMuPDF + Pillow + la boîte à outils de style `tools/ecrans_art.py`) :
  `gui/mudras/cases.png` (76 × 38, deux cellules de 38 : repos, choisie ; case de 34 en (2, 1), ombre portée dans la
  cellule ; pixel art) et `gui/mudras/mains.png` (1152 × 96, douze mains de 96 sur fond transparent, lissée par
  `mains.png.mcmeta` « blur »), plus `mains_petit.png` (576 × 48, les mêmes réduites, prises quand la main fait 48 px ou
  moins : en 768p, réduite depuis 96 elle crénelait). Les mains sont lues un demi-texel en retrait (pas de bavure de la
  voisine en 4K) ; fenêtre plus étroite que la rangée : le pas se resserre. Les mains sont **les vrais dessins des signes** (15/11, « prends les vraies images de
  mudra ») : sources `tools/mudras_src/<signe>.svg`, dessins de **Schlum, Wikimedia Commons, CC BY-SA 4.0** (fournis par
  le propriétaire ; `LICENCE.txt` à côté) ; chaque dessin est rendu en grand, ses traits épaissis, ses ombres ramenées à
  l'ivoire. `mains.png` est une œuvre dérivée, **sous la même licence**, créditée dans `gui/mudras/credits.txt` (dans le
  jar ; en minuscules : un chemin de ressource à majuscules est refusé au chargement). `medaillons.png` est retirée.
- **Corps et sons** : à chaque signe, `TirageAnimations.playSigne` (animation `animations/mudra_signe.json`, bras, tête
  et buste seulement, tirée de la dernière image de `mudra_chakratest.json` par `tools/gen_mudra_animation.py` ; une pose
  pour les douze signes, **provisoire**), relâchée 0,65 s après le dernier signe ; même pose sur un joueur proche à la
  réception de `geste`. Sons `reborn:mudra.signe | barre | reussite | echec` joués par le mod (**adoucis le 16/11**, « un son plus subtil » : le signe n'est plus un claquement sec mais des paumes feutrées, médiums seulement, −9 dB ; ouverture, réussite et échec baissés aussi), `mudra.dalle` (3 s) et
  `mudra.choc` joués dans le monde par le serveur (synthèse `tools/ui-art/gen_mudra_sfx.py`, **jamais écoutés**).
- **Relecture du 15/11** (deux relecteurs, sept défauts corrigés) : la répétition du clavier de la touche des mudras est
  prise par le mixin (tenir U refermait la barre puis la redemandait trente fois par seconde ; même garde pour le
  Byakugan sur O) ; sortie de la pose par sa propre animation (`mudra_signe_fin.json`, 0,3 s, `TirageAnimations.stopSigne`)
  au lieu de celle de la feuille ; écart entre deux signes ramené de 200 à 120 ms ; le son d'échec ne sonne plus qu'en mode
  guidé (une suite libre se dissipe en silence) ; migration 7 : si U est déjà prise, Mudras passe sur « aucune » ; très
  petites fenêtres : unité arrondie, case de touche qui suit son texte ; côté serveur, une demande d'ouverture qui croise
  une barre guidée ne la referme plus, et une barre libre cède à une séance.
- **Outil staff** `staff/GardePortes` (F7 › Outils › Monde › « Portes scellées », 0.4.206, maquettes
  `Maquettes/gameplan/19_mudras/maquettes_f7/` validées le 16/11 : « 3A et on garde les blocages »), sur le gabarit de
  l'outil Plaques ; protocole : PLUGINS_REFERENCE §4 terdecies.7 (actions `porte*` de `reborn:staff`).
  **Liste** : interrupteur du système (Admin, confirmation avant d'éteindre), recherche, « Proches n » (24 blocs) et
  « Toutes n », rangées de 26 avec l'état à pastille et « redescend dans 5 s » / « 1 joueur bloqué » / « levée 2 »,
  ligne rouge si `mudras.yml` est illisible ; redemandée toutes les 2 s outil ouvert.
  **Sélecteur de dalle** : `SelecteurPlaque` en mode `blocsSeuls` (volet replié, clic = premier coin, Maj + clic =
  second coin, clic droit = tourner, Entrée valide, Échap annule) ; limites jugées sur place (9 par côté, 3
  d'épaisseur, 162 blocs) puis `porte_verifier` numéroté (250 ms d'écart, réponse périmée ignorée, redemandé quand le
  staff s'est déplacé d'un demi-bloc : le serveur juge la distance depuis ses yeux).
  **Nouvelle porte** : dalle, nom, suite en cliquant les douze dessins (atlas `gui/mudras/mains_petit.png`, rang(s)
  en pastille d'or, un signe peut revenir, 12 au plus), levée bornée à la hauteur de la dalle, « Sceller la porte »
  (Ctrl + Entrée). **Fiche** : état, dalle, suite (si `peutGerer` **et** si le message la porte ; sinon « 3 signes ·
  réservée aux modérateurs »), levée, posée, modifiée, blocages avec « Lever » ; actions Ouvrir pour essai / Fermer
  (éteint pendant le mouvement) / Remettre la dalle, Changer la suite, Renommer, Changer la levée, Aller devant,
  Copier l'id, Retirer le sceau… (confirmation `GardeFormulaire`). Après un envoi, le volet attend le `dernier` du
  serveur pour ouvrir la fiche (`StaffClient.portesDernier`, gardé à part de la liste : le rafraîchissement ne
  l'efface pas ; l'attente est soldée dès qu'on quitte la vue).
  **Monde** (`MondePlaques`, outil ouvert) : portes à 64 blocs en coins + id (44 au plus), porte de la fiche ou de la
  rangée survolée en or plein avec « D3 · nom » et « fermée · 3 signes », dalle du sélecteur en or (rouge + « refusée »
  si elle l'est), « 1 » et « 2 » sur les faces cliquées, flèche de levée.
  Branché dans `GardeOutils` (LISTE + aiguillages), `StaffScreen` (routage) et `StaffClient` (`portes`, `porte_verif`,
  crochet de banc `debugPortes`). La suite n'est jamais copiée ni écrite dans un toast ou un journal.
- **Limites connues** : la molette change encore l'objet tenu pendant la barre ; pas d'entrée dans l'éditeur de HUD
  (position fixe) ; l'incantation d'une technique en combat n'utilise pas encore la barre. Outil Portes : durées de
  levée et de maintien absentes de la fiche (pas dans le protocole), phrase des blocages figée sur les réglages par
  défaut (5 essais en une minute), pas de déplacement de la dalle d'une porte existante, « Valider la dalle » reste
  éteint face à un ShinobiCore d'avant le 16/11 (pas de réponse à `porte_verifier`).

## 1 septies. Marche sur les murs, essai technique (lot 5, étape C0, 0.4.207, 24/11)

Essai éteint par défaut : sans le permis du serveur (`/murtest on`, staff), rien n'est lu au clavier ni envoyé, et les
mixins ne font qu'une lecture de booléen. Fiche : [`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md) §13 ;
serveur et canal : [`PLUGINS_REFERENCE.md`](./PLUGINS_REFERENCE.md) §5.1 ter.

- **`mur/MurFace`** : N, S, E, W = la normale du mur vers l'air. **`mur/MurCanal`** : canal `reborn:mur` (reçoit `permis`,
  `etat`, `refus` ; envoie `accroche` et `lache` ; `face` n'est pas émis).
- **`mur/MurClient`** : état par identifiant d'entité, permis, machine du joueur local `REPOS → ATTENTE → BASCULE` (6 ticks)
  `→ MARCHE → SORTIE` (3 ticks), compteur des corrections. Drapeaux lus par les mixins : `MurClient.actif` (au moins un
  état connu) et `MurClient.corpsPris` (notre corps est au mur).
  - **Accroche** : permis, 3 ticks de poussée (direction voulue à moins de 60° de la paroi) contre un mur plat et plein,
    mur plein sous tout le corps à l'arrivée → `accroche`, attente d'une seconde. La bascule ne part qu'à la réception de
    notre propre `etat`. Après un abandon, un refus ou une sortie, il faut **relâcher les touches de déplacement** avant
    une nouvelle demande.
  - **Marche** : relative à la caméra (avant = le regard projeté sur le mur, crans de 25° et 15°, base figée tant qu'une
    touche est tenue), 3,2 et 5,6 blocs/s, `P` tenu à 0,9 de la paroi ; arrêt pile au bord (mur plein sous tout le corps)
    et sous tout obstacle ; sortie automatique à moins d'un bloc du sol en descendant.
  - **Sorties** : Espace (nouvel appui) ou la touche de course de chakra → 3 ticks vers une place debout, puis
    `lache {c}`. Lâcher immédiat : `etat` `-`, permis retiré, refus, mort, spectateur, vol, monture, eau, bond parti, à
    terre, mobilité refusée (`EtatClient` « mob »), changement de monde, écart de plus de 0,05 à la paroi.
  - Un `etat` `-` reçu hors du mur n'est que l'écho d'un `lache` : il n'annule pas une demande en attente. Un `etat` de
    soi inattendu est toujours rendu par `lache` `c` = 0 (le serveur nous tient au mur).
  - L'état d'un voisin tombe dès que son entité disparaît (le serveur le renvoie à qui la revoit au mur) ; un état arrivé
    avant son entité est gardé 10 s.
  - Une ligne au tchat local à chaque fin de marche : « Mur : <durée> s, <n> correction(s) de position (<raison>) ».
- **Boîte** : `mixin/EntityMurMixin` (`ModifyReturnValue` sur `Entity.makeBoundingBox(Vec3)`, garde = un booléen statique,
  filtre joueur + `level().isClientSide()` : le serveur intégré partage les classes). De `P − 0,85 n` à `P + 0,9 n`, ± 0,3,
  haute de 0,6, chez soi et chez les autres.
- **Corps** : `mixin/PlayerMurMixin` (`updatePlayerPose` et `travel` à `HEAD`, joueur local : pose de nage tenue,
  déplacement sur le plan du mur, avant le vol créatif et `SautVolMixin`) ; `mixin/LivingEntityMurAccessor` (`swimAmount` à
  zéro) ; `mixin/ClientPacketListenerMurMixin` (`handleMovePlayer` : compte les corrections).
- **Rendu** : `mur/MurRendu` + `MurRenderState`, `mixin/AvatarRenderStateMurMixin`, `mixin/AvatarRendererMurMixin`
  (`extractRenderState` `TAIL`, `setupRotations` `HEAD` et `TAIL`) : corps entier tourné autour de son centre, nage, bras de
  nage et accroupi retirés, tête dans l'axe. `animation/MovementAnimations` : au mur, marche ou course, jamais de saut.
- **Caméra** : `mur/MurCamera` + `mixin/CameraThirdPersonMixin`, `camera/RebornCamera.decaler` : foyer `P + 0,4 n + 0,5 haut`,
  recul 5 (3 à 120° d'écart au mur, 2 au-delà de 135°), épaule côté air, un glissement de 350 ms à l'accroche, notre corps
  effacé chez nous au-delà de 120° (seulement sur une image que la caméra d'épaule vient de cadrer : pas en mode photo).
  1re personne et caméra de Minecraft passent en épaule le temps du mur puis sont rendues ; l'essayage du tailleur et le
  test de la Feuille gardent la main sur la caméra.
- **Entrées** : `mixin/KeyboardInputMixin` (au mur le regard suit la caméra), `combat/MobilityInput` et `CombatInput` (dash,
  double saut et saut chargé coupés), `allure/Allure` (X lâche le mur au lieu de lancer la course).
- **Limites** : murs plats de blocs pleins ; ni coin, ni sommet, ni plafond ; l'attribut `SCALE` n'entre pas dans la boîte ;
  ombre non retirée ; hors champ, l'ancre de tête (nom, bulle) place la tête comme pour un corps debout ; le recul d'un
  coup reçu est annulé tant que le corps est au mur (c'est au serveur de lâcher) ; sans les canaux RP (caméra d'épaule
  éteinte), pas de cadrage de paroi.

## 2. Touches

**Lot 3 (05/11, 0.4.196)** : **Concentration = Y** (`HudKeybinds.CONCENTRATION`, sans effet avant le lot 4 ; `RappelsTouches.concentrationBranchee` à passer à vrai au lot 4), **Couper le micro = I**. Les touches de la voix sont **fusionnées avec Plasmo Voice** : `keybind/TouchesPlasmo` (sans classe Plasmo, chargé partout) et `voice/PlasmoTouches` (chargé par l'addon Plasmo) recopient Couper le micro, Couper le son, Appuyer pour parler et Parler à la radio dans les raccourcis de Plasmo et inversement — une seule liaison par action. **Migration 4** (`TouchesMigration`) : touches vanilla inutiles déliées et masquées (Progrès, Amis, spectateur, barres du créatif), micro Plasmo resté sur US-M en AZERTY corrigé, Y / I posées seulement si libres, échange Dégainer ↔ Concentration en QWERTZ. L'écran des touches (Paramètres › Commandes, `ControlsTab`) est rangé par catégories avec les conflits en vermillon (`TouchesConflits`). Rappels : seule la Concentration en temps normal, le reste en concentration, combat ou action ; en bas à droite, déplaçables (élément RAPPELS de l'éditeur).

**Retours du 05/11 (0.4.197)** — Échap : la carte **Stream** fait défiler **tous les streamers en direct** de `/menu/panel` (un toutes les 4 s, sortie 180 ms vers le haut, entrée 240 ms ; nom d'origine coupé en « … » à la largeur de la carte, « 2 / 5 » discret ; à l'arrêt au survol ; le clic ouvre le streamer montré ; personne en direct : « STREAM · Hors ligne », clic = première chaîne suivie comme avant). **Identité** sous le logo = le vrai personnage : ligne « SOI » du roster (`TablistData.soi()`, « Prénom Clan », grade FR avec macrons, village), la fiche reçue (`StatsData.serveur()`) seulement tant qu'aucun roster n'est arrivé (un roster sans grade sur la ligne SOI = pas de personnage : jamais la fiche d'un personnage quitté) ; la **sacoche** lit la même source (« Prénom Clan · grade · village », sinon le pseudo seul) — la fiche n'arrive qu'à l'ouverture de la fiche ou sur /stats ; sans donnée (hors serveur, douane, avant la sélection) la ligne disparaît et le logo descend (184 au lieu de 150, unités 1080p). Fuite corrigée : `StatsData.get()` retombe sur la fiche de démonstration « Itachi · Chunin · Konoha », que l'Échap (et la sacoche) lisaient ; plus de demande « stats » silencieuse (sans personnage, elle affichait « Aucun personnage actif. »). **Pied** : la version du mod est retirée ; « Serveur x.y.z » = version de la **dernière patch note publiée** (`EscData.versionServeur()` : la plus récente par `publishedAt` dans `patchNotes` de `/menu/panel`, épinglée ou non ; même donnée que la carte Dev blog, saisie par le staff ; un champ `server.version` de `/menu/panel` passerait devant s'il est un jour servi). Pas la version du manifeste (elle suit les mods clients). Aucune note → rien d'affiché. Lien des Paramètres : libellé et largeur calculés une fois (pas d'allocation par image). Paramètres : lien **« Options de Minecraft › »** toujours visible à droite du pied (`ConfigShellScreen.ouvrirOptionsMinecraft`, sans redirection via `RedirectionOptions.ouvrirVanilla` ; « Terminé » revient aux Paramètres ; ailleurs, la redirection reste).

**Carte réduite du 15/10** (décision du 15/10, [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)) : 15 touches Reborn
au lieu de 26 (16 depuis la portée de la voix, H, le 31/10), sans conflit avec Minecraft ni le modpack. Image récapitulative (ancien → nouveau) :
`Maquettes/gameplan/touches/00_carte_des_touches.png`. Tout reste réglable dans **Réglages → Contrôles** (`ControlsTab` :
la liste ci-dessous dans cet ordre, avec la sacoche, la vue et les allures de Minecraft, puis le menu OST, Plasmo Voice et
le Poste de garde quand ils sont là) et dans Options → Commandes, catégorie « Reborn ».

| Touche | Action | Touche | Action |
|---|---|---|---|
| **E** | Sacoche (touche Inventaire de Minecraft) | **K** | Fiche shinobi |
| **M** | Carte | **Y** | Concentration (lot 4) : curseur, plaques, menu d'interaction ; l'ancienne touche R est déliée par la migration 5 |
| **F** | Contextuel : ramasser / exécuter / prendre ce qu'on me tend (sinon F garde son rôle) | **Z** | Dégainer / rengainer l'arme de l'étui |
| **B** | Radio : **maintenir** = parler à l'escouade (recopiée dans l'activation « radio » de Plasmo Voice) ; **appui bref** (< 0,25 s) = écran de l'escouade | **Entrée / Retour** | Accepter / refuser (radio d'abord, puis duel, puis ce qu'on me tend) |
| **.** | Animations : émotes (Reborn et Emotecraft) et démarches ; la même touche referme | **F5** | Vue : épaule ↔ 1ʳᵉ personne (touche « Changer de vue » de Minecraft, détournée en caméra Reborn) |
| **Alt gauche** | Regard libre (maintenir) | **F1** | Mode cinéma |
| **P** | Mode photo ; la **galerie** s'ouvre dedans (bouton « Galerie », ou G) | **G** | Dash de combat |
| **C** | Parade (maintenir) | **Ctrl gauche** | Allures (touche de sprint de Minecraft) : tenir = trot, 2 appuis = sprint (le triple appui ne lance plus la course depuis le 16/10) ([`../gameplay/DEPLACEMENTS.md`](../gameplay/DEPLACEMENTS.md)) |
| **X** | Course de chakra en bascule (15/10 au soir) : un appui la lance, un autre l'arrête ; seule commande de la course depuis le 16/10 | **U** | Mudras (14/11, §1 sexies) : ouvre ou referme la barre des douze signes, composés sur la rangée des chiffres ; sans effet tant que le serveur n'a pas allumé le système |
| **N** | Menu OST (`mod-ost`) | **V** | Réglages de Plasmo Voice (touche de Plasmo Voice) |
| **F7** | Poste de garde (staff) | Espace (à terre) / Entrée, Échap (mort) | Volonté (si apprise) / scène de mort |
| **J** | Volet Faction (16/10) : ta section, ton village, les annonces ; la même touche le referme. Libre dans Minecraft et le modpack, même lettre en AZERTY / QWERTZ (aucune migration) | **Entrée / Retour** | Aussi : accepter / refuser une invitation dans une section (après la radio, avant le duel) |
| **H** | Portée de la voix (31/10, §10) : fait tourner les portées de proximité du serveur Plasmo Voice, **un cran plus bas** à chaque appui (normale → chuchotée → criée → normale : chuchoter tient en un appui, baisser la voix ne passe plus par la plus grande portée), annoncée en bas à droite : depuis le lot 2, sur la ligne de rappel « Portée de la voix [H] », qui devient « Voix : chuchotée » 2,5 s avec une puce à la couleur de la portée (en notification si le rappel n'est pas affiché) ; le micro des vitals en prend la couleur (vert, jaune, rouge). À terre : refusée (chuchoter seulement) ; voix refusée par l'état (mort RP, cinématique…) : refusée avec la raison du serveur. Libre dans Minecraft 26.2 (seulement F3 + H, raccourci de débogage), dans Plasmo Voice (Alt gauche, M, V) et le modpack ; même lettre en AZERTY / QWERTZ | | |

**Rappels de touches en bas à droite** (lot 2, 01/11, `keybind/RappelsTouches`, élément `RAPPELS` ; décision du 01/11 : **permanents**, masquables dans l'éditeur de HUD et dans Réglages › Interface) : en bas « Portée de la voix [H] » (voix reliée, plusieurs portées, portée non imposée) ; au-dessus, selon le moment : « Arrêter l'animation [.] » (une émote joue et le serveur connaît `/stopemote` : la touche Animations l'arrête au lieu d'ouvrir le menu), « Arrêter la course [X] », « Descendre [Espace] » (porté de plein gré : remplace la ligne « Espace · descendre » de `KoHud`, qui ne revient au centre que si le rappel n'est pas affiché), « Se lever [Maj] » (méditation ; l'invite vanilla « Maj pour descendre » s'efface), « Poste de garde [F7] » (mode staff ou animation), « Parler à la radio [B] » (escouade sans refus) ; « Concentration » attend le lot 4 (masquée) ; 4 lignes au plus, 3 avec des notifications. Toute la liste se masque pendant une invitation ou une demande, en mode interaction, au KO, en douane côté candidat, pendant la carte d'aperçu de capture et avec les sous-titres vanilla. Noms de touches : `keybind/NomTouche` (« Clic G / D / M », « Souris 4 », « Maj », « Ctrl », « Alt Gr », « Espace », « Entrée », « Retour », « Échap », « Pav 1 », « F7 », lettre telle que le clavier l'affiche : AZERTY « ; » pour Animations), en cache par `saveString` ; souris et Espace en pictogrammes ; case en 9 tranches `gui/inventaire/touche_rappel.png` (`tools/gen_inventaire_textures.py`, recette de la maquette).

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

- **Lot 3 (05/11)** : réglage **« Taille des menus Reborn »** (Paramètres › Graphismes › Affichage, `RebornPrefs`) : *Automatique* = la règle ci-dessous (hauteur logique ≈ 540) ; *Petite* vise ≈ 720 (échelle 2 au lieu de 3 en 1440p, 3 au lieu de 4 en 4K), sans jamais descendre sous l'échelle 2 (plancher de lisibilité 8,6 px).

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
- **`ui/TexteRaster`** (lot 2 « Reprises Ketto », 01/11) : narration, plaques (lot 4), OST et bas-droite passent par
  ce moteur, pas par les polices TTF de Minecraft (lues au plus proche, sans crénage, italique cisaillé). Chaque texte
  est calculé une fois en couche RGBA sur le fil `Reborn-Texte` puis posé en un blit à 1:1 ; la recette est celle des
  maquettes (`Maquettes/gameplan/17_lot2_ketto/_texte_raster.py`, banc `REBORN_TEXTE_BANC` : écart d'alpha ≤ 2/255).
  Polices : **Alegreya Sans Bold Italic** (récit, `assets/reborn/font/recit/`, `ofl.txt`), **Philosopher Bold** (narration
  du staff, choix du 02/11 au soir à la place de Marcellus, `assets/reborn/font/narration/`, `ofl.txt` ; voyelles à
  macron composées, lettre de base + U+00AF, `_macron` du Python porté le 02/11) et l'Inter Bold / Medium du
  mod (`ofl-inter.txt`), TTF officiels non modifiés (noms de fichier en minuscules : Minecraft refuse les majuscules
  dans un chemin de ressource). Crénage : un `.kern` à côté de chaque TTF, produit par `tools/gen_kern.py` à partir du
  moteur des maquettes (mêmes paires que le Python ; répertoire latin, latin-1, latin étendu A, ponctuation). Couleur par défaut d'une ligne : une clé nommée (`hud`, `doux`, `inconnu`…) ou, depuis l'étape 2b, une couleur écrite `#RRGGBB` (accents et raretés des notifications ; pas de balise `<#…>`). Étape 2c : `TexteRaster.litteral(texte)` protège les « < » d'un texte reçu déjà découpé en segments (caractère U+E000, rendu « < » : un « <rouge> » écrit par un joueur reste du texte), et `Mise.texteBalise(a, b)` rend le texte balisé des lignes [a, b) d'une mise en page (pages du texte au centre). Étape 2d : dans un bloc, un mot plus large que la ligne (cri sans espace, adresse) est coupé entre deux caractères, au plus près de L (contrat de la narration) ; le moteur des maquettes ne coupe pas, mais aucun de leurs textes n'a de mot aussi large, et la mise en page reste identique au Python dès qu'aucun mot ne dépasse. Étape 2d, allure de la narration (02/11) : `TexteRaster.Preset` devient une classe (les trois du lot, plus celui qu'un client pose avec le reste de son allure : `recit/Gabarit`) ; **ombre douce** facultative d'un bloc (`TexteRaster.Ombre` : masque contour ∪ glyphe décalé, quantifié sur 8 bits, flouté par le flou gaussien de Pillow porté à l'identique — trois passes de boîte à rayon fractionnaire, `flouPillow`, contrôlé bit à bit contre Pillow 12.3 —, couche recadrée sur ses pixels non transparents : la recette `rasteriser_narration` de la planche d1) ; `Demande.bloc` à tous réglages (approche, équilibrage, ombre) ; coupes de ligne figées = la page avec ses retours à la ligne, largeur `SANS_LIMITE`, aucun équilibrage. Sans ombre douce, le calcul est inchangé bit pour bit.

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
| `REBORN_FEATURE_PREVIEW=1` | Lot en cours (à réécrire pour chaque lot) : `audit/FeaturePreview.java`. **Lot 2, étape 2a (01/11)** : lecteur OST, état de la lecture simulé dans l'`ObjectShare` (sans mod-ost) ; jour puis nuit : 100 %, barres qui ondulent, 70 %, pause, reprise, titre long, sous l'indicateur « Mode animation » ; sous l'écran noir, bande en 1080p, 1366×768 (bande + mode staff), 1280×720 (sous une bande pleine largeur), option « Masquer après 10 s » à 5 s et 11,5 s, éditeur (option, puis panneau replié) → `run/screenshots/ost_*.png`. Chaque état fixe est pris deux fois, élément masqué puis visible (`<cas>_sans.png`, monde gelé) : `python tools/ost_compare.py` repose la maquette (`_maquettes_lot2.ost`) sur le fond nu et mesure l'écart (01/11 : max 3/255 dans le bloc, 10 cas sur 10). `=compat` : avec un vrai reborn-ost dans `run/mods` (0.2.6 ou 0.2.5) et une piste dans `run/reborn/ost/<catégorie>/`, joue la première piste (volume général coupé), captures `ost_compat_*` et journal `[ost-banc]` : qui dessine le lecteur. **Étape 2b (01/11)** : colonne bas-droite → `run/screenshots/basdroite_*.png` (`=1` : l'OST puis le bas-droite ; `=ost` ou `=basdroite` : l'une seule) — orateurs simulés (`VoiceState.DEBUG_ORATEURS`, `DEBUG_SOI_PARLE`, `DEBUG_PORTEE`), noms des maquettes (`Nametags.DEBUG_NOMS`), vumètres figés (`QuiParleHud.DEBUG_VU`), vraies notifications, rappels imposés (`RappelsTouches.DEBUG`) ou réels (porté de plein gré, mode animation, à terre) : personne, un orateur, « INCONNU », soi dans les trois portées, orateur loin, colonne complète (c1), vumètre qui ondule, contextes des rappels (c3, AZERTY « ; », clic molette, 4 lignes, 3 avec notifications, « Voix : … »), sous-titres vanilla aux échelles 2, 3 et 4, carte d'aperçu de capture, à terre, 1366×768, échelle d'interface 4, éditeur de HUD, Réglages › Interface ; fond nu `_sans` puis colonne, `python tools/basdroite_compare.py` repose `qui_parle` / `notifs` / `rappels` de la maquette sur le fond nu (rappels et noms de touches relus dans le journal `[basdroite-banc]`) et mesure l'écart. **Étape 2c (01/11)** : texte au centre → `run/screenshots/centre_*.png` (`=1` : les trois séries ; `=centre` : celle-ci seule) — horloge du texte figée (`Recit.horloge`), salle sombre de polished deepslate 100 blocs au-dessus du joueur et plateau de neige 60 blocs au-dessus (posés sur de l'air, retirés à la fin, position rendue en absolu), forêt de jour : salle sombre avec OST, qui parle et rappel (a1), jour (a2), texte court sur la neige (a3), fondu à 0 / 60 / 120 / 180 ms, plein, sortie à 150 ms (a4), 1366×768, 1920×1080 et 2560×1440 à l'échelle 3 (a5), écran noir sans puis avec texte, inconscient (long, court), à terre (a6), tchat ouvert aux échelles 4 et 2 (a8), mannequin « Ren Hyūga » dont le nom, la bulle et le /me passent à 25 %, deux pages, F1 « CLEAN » puis « BARS », lecture d'une plaque (lot 4 : narration devant, retour, autre plaque, fin) ; fond nu `_sans` (`RecitHud.debugMasque` : la couche seule n'est pas posée) puis texte, journal `run/centre_banc.txt` (une ligne JSON par cas : demande posée), `python tools/centre_compare.py [--planches <dossier>]` y repose le moteur des maquettes et mesure l'écart, et fait les planches « maquette \| jeu ». **Étape 2d (02/11)** : narration du staff vue par un joueur → `run/screenshots/narration_*.png` (`=1` : les quatre séries ; `=narration` : celle-ci seule) — messages `reborn:narration` simulés (JSON écrit comme le serveur), passés au vrai receveur (`NarrationClient.recevoir`), horloge figée, **allure `Gabarit.NARRATION`** (plein écran, provisoire) : une narration (file, durée auto, texte d'essai de la planche d1) dans la salle sombre avec le HUD, de jour et sur la neige, deux en file (sortie à 150 ms, écart de 400 ms, la 2e), `remplace` qui coupe l'écran (0,50 à 75 ms) et vide la file, tenue deux minutes puis `narration_fin` (150 ms), garde-fou de 10 min, `narration_fin` à id vide, les quatre couleurs et une clé inconnue (salle, neige), 998 caractères (pages au prorata, leur nombre lu sur l'entrée), `pos` 30 puis hors bornes, mot plus large que la ligne, rattrapage (`ecoule` : temps restant, page de départ, fondu d'entrée), refus du protocole (`v`, `t`, id, `segs`, doublons, durées et `pos` hors protocole, file pleine) ; puis les règles de l'étape 2c rejouées en grand : écran noir sans puis avec texte, inconscient, à terre, tchat ouvert, F1 « CLEAN » puis « BARS », une stèle lue (allure STELE) qu'une narration vient couvrir puis qui revient, 1366×768 et 2560×1440 à l'échelle 3 ; puis la **variante V1 de la planche d1** (`NarrationClient.debugGabarit`) de jour, en salle, sur la neige, en 1366×768 et 2560×1440 ; enfin un message envoyé par le serveur intégré sur le vrai réseau (`ServerPlayNetworking.canSend` : le client déclare le canal). Vérifications « OK · » / « ÉCART · » (allure posée = gabarit, place sous le noir, sous le KO, au-dessus du groupe « À terre »…) et demande posée de chaque capture dans `run/narration_banc.txt` ; `python tools/centre_compare.py --serie narration [--planches <dossier>]` y repose le moteur des maquettes à l'allure du journal (ombre douce : la recette `rasteriser_narration` de la planche elle-même). **Étape 2e (02/11)** : allure V2 retenue, la série `narration_*` est rejouée en V2 (vignette, 3 lignes par page), `centre_compare.py` lit la police (`police` du journal) et repose la vignette de la planche ; **02/11 au soir** : Philosopher Bold (`centre_compare.py` l'ajoute au registre des maquettes sous `philosopher_b`), fondus de Ketto image par image (entrée 0 / 150 / 300 / 450 ms, sortie à 350 et 120 ms de la fin, fin de page à 300 ms, `narration_fin` à 350 ms, rattrapage à 300 ms), durée auto 1,3 s + n / 30, pages de 3 lignes, stèle en concentration (entrée à 125 ms et bords à mi-course, coupe par une narration à 75 ms, retour, sortie à 175 ms), voyelles à macron (`narration_16_macrons`) : 101 vérifications, 0 hors cible, 53 cas sur 53 dans la cible de `centre_compare.py` (cœur ≤ 5/255 ; macrons composés comme le Python), copies `Maquettes/gameplan/17_lot2_ketto/code/narration_v2_*.png`, `garde_narration_v2_*.png` et `narration_v2_compare.txt` ; **revue du 02/11 au soir** (vignette sous le HUD et sur la narration entière, pages par phrases et par paragraphes, durée + 1,2 s par page, approche 0, tchat 1366, aperçu réduit, stèle sans minuterie) : 104 vérifications, 0 hors cible aux trois tailles (1920×1080, 1366×768, 2560×1440 demandé = 1924×1175 sur le poste), dont « 998 caractères : 3 lignes au plus par page, chaque page finit une phrase ou un paragraphe » (8 pages) ; `centre_compare.py` 54/54 (narration) et 29/29 (centre) aux trois tailles, `ost_compare.py` 10/10, `basdroite_compare.py` 24/24 (1920, 1366), `texte_banc_compare.py` 32/32 ; `centre_compare.py` lit `vignette_force` du journal (force réellement posée) ; durée des rendus de blocs mesurée (`TexteCache.bilan`) : moyenne ≈ 100 ms, maximum ≈ 0,5 s en 1080p ; copies `Maquettes/gameplan/17_lot2_ketto/code/revue_0211/` et `planche_revue_0211.png` ; **outil « Narration » du Poste de garde** → `run/screenshots/garde_narration_<taille>_<cas>.png` (`=garde` : celle-ci seule ; incluse dans `=1`) — serveur simulé (`staff/StaffDemo` : requêtes `narration`, `narration_fin`, `narrations` comme `StaffPanel`), ouvert par `StaffDebug.narrationOuvrir` : outil vide, texte en couleur avec sélection et aperçu à taille réelle, confirmation de « tout le serveur », liste « En cours » de deux narrations (une tenue), « Plein écran » ; en 1920×1080 puis 1366×768 ; puis un Helper (un joueur seulement) et un Helper en mode animation (un joueur ou ici), en 1080p ; volet fermé et serveur simulé coupé à la fin. Passage précédent (02/11, allure provisoire) : 83 vérifications, 0 hors cible ; 37 cas sur 37 au même pixel et à la même taille que la référence (cœur ≤ 4/255), V1 identique à la planche d3 (1080p : 3 lignes, bloc 1 247 px, bas à 508 px ; 1366 : 878 px ; 1440 : 1 709 px), texture prête 100 à 250 ms après la réception, journal sans erreur ni mixin en échec |
| `REBORN_PERSO_DEBUG=1` (`vite` : 1080p seul ; `coupes` : coiffures et sourcils, 18/10, + `REBORN_CREATOR_ASSETS=<copie de creator-assets>` qui rejoue la diffusion live du serveur) | Sélection et création « au bord de l'étang » : roster des maquettes, 8 étapes, 6 villages, échelles 3 et 4, 1366×768, 1706×960 ; `coupes` → `perso_10*` (grille « Tout voir », coupes live, arrivée écran ouvert) et `perso_11*` (sourcils), voir [`../gameplay/PERSONNAGES.md`](../gameplay/PERSONNAGES.md) §4 bis (`perso/PersoDebug.java`) |
| `REBORN_SLIM_DEBUG=<étiquette>` (+ `REBORN_CREATOR_ASSETS=<dossier>` : rejoue un asset live, ex. une tenue peinte en 4 px) | Carrure fine pour tous (18/10, `perso/SlimDebug`) : dalle dans le ciel (rendue à la fin), perso Homme composé sans tenue puis avec tenues, mannequin « autre joueur » à côté ; face, profil, dos, bras de ¾, duo ; main à la 1re personne ; Test de la Feuille (confirmation, canalisation) ; création (Sexe, Apparence, Récapitulatif) → `run/screenshots/slim_<étiquette>_*.png`, copies dans `Maquettes/gameplan/homme_slim/`. Validé avec le pack Sodium copié le temps de l'essai |
| `REBORN_SCENE=1` / `journey` | Ancienne sélection / création en scène 3D (repli `-Dreborn.sceneCharacterScreens=true`) |
| `REBORN_KO_DEBUG=1` | Toutes les phases du KO et de l'ATA |
| `REBORN_STAFF_DEBUG=1` (`=vite` : 1920×1080 seul ; `=<cas>,<cas>` : ces cas ; `=vite:<cas>,<cas>` : ces cas en 1920×1080 seul) | Poste de garde v3 (14/10, `staff/StaffDebug`) : 62 cas — alertes (en attente, traitées, vides, valider une mise à mort), joueurs (liste, survol d'une ligne, menu du clic droit), recherche (« uchi », « gel kazu », hors ligne), fiche (menu Plus, écoute radio, notes, sanctions, confirmation de sanction, comptes, écran noir, suppression à N° retapé), journal (filtré et déplié, menu + Filtre, vide), outils (liste, annonces + confirmation, séisme, chat, commandes, modes), Infirmerie (blessés, zones, nouvelle zone, hôpitaux, réglages), variantes **denses**, effets HUD (annonces, écran noir, nametags staff, indicateur de mode) et, depuis le 15/10, la section **Personnages** de la fiche (`persos`, `persos_detail`, `persos_identite`, `persos_grade`, `persos_mort`, `persos_suppr`, `persos_creer`, `persos_emplacement`, `persos_refus`, `persos_hors_ligne` ; copies `Maquettes/gameplan/04_staff/personnages/`), et depuis le 31/10 la **voix** : `fiche_voix` (menu R › Staff › « Couper la voix… » : le poste s'ouvre sur la fiche puis le formulaire) et `fiche_voix_coupee` (formulaire validé, fiche « Voix coupée — jusqu'à… », réponse du serveur simulé, menu Plus sur « Rétablir la voix »), et depuis le 02/11 l'outil **Narration** (`narration`, `narration_couleurs`, `narration_tous`, `narration_actives`, `narration_plein_ecran`, `narration_helper`, `narration_anim`) — chacun en **1920×1080, 1366×768 et échelle GUI 3** → `run/screenshots/garde_<taille>_<nn>_<cas>.png`. Les demandes partent vers `StaffDemo` (serveur simulé conforme à PLUGINS_REFERENCE §4 bis). Copies : `Maquettes/gameplan/04_staff/v3/code/` |
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
| `REBORN_TEXTE_BANC=1` (`=couches` : les couches seules) | Moteur de texte du lot 2 (01/11, `audit/TexteBanc`, au menu principal, sans monde) : 24 textes (texte d'essai des maquettes en Alegreya Sans Bold Italic RÉCIT à f = 1 et 1,5, jeux de typographie, lignes du HUD en Inter Bold capitales 12 et 10, Inter Medium, noms de qui parle) et 8 formes → `run/texte_banc/<cas>.png` + `manifest.json` ; les mêmes couches passées par `TexteCache` sur fond uni sombre puis clair → `run/screenshots/texte_banc_{sombre,clair}.png` ; endurance : 1000 rendus, 50 redimensionnements simulés, rafale (un seul recalcul), 1000 changements de texte, LRU, puis compteurs FreeType (glyphes, faces, bibliothèque) et textures → `run/texte_banc/rapport.txt`, « [texte-banc] OK / ECHEC », le jeu se ferme. Puis `python tools/texte_banc_compare.py` : rend les mêmes entrées avec `_texte_raster.py` et imprime l'écart d'alpha moyen / max par cas (cible < 1/255 et < 16/255) et la mise en page ; contrôle aussi les captures (blit 1:1 exact). Dernier passage : 01/11, 32/32 dans la cible (moyen ≤ 0,07/255, max ≤ 2/255), écran exact, 1000 rendus en 10,6 s (≈ 10 ms par rendu, ×4 par glyphe), glyphes FreeType obtenus = libérés (≈ 171 000), faces 3/3, textures créées = libérées (≈ 1 800). Copies : `Maquettes/gameplan/17_lot2_ketto/code/` |
| `REBORN_NAMETAG_DEBUG=1` (ancien nom `REBORN_PLAQUES_DEBUG`, encore accepté) | Au-dessus des têtes (31/10, `nametag/NametagsDebug`, §9) : vue épaule Reborn (RP forcé), 1920×1080, échelle GUI 2, **horloge des fondus imposée** (`Nametags.horloge`, films image par image, chaque capture attend 2 images rendues), cinq mannequins nommés (`Nametags.DEBUG_NOMS`) à ~3,5 blocs — debout, debout derrière, à terre (ramper + phase 1), allongé sans lit (phase 2), accroupi ; 56 captures `run/screenshots/nametags_<nn>_<cas>.png` : aucun regard ; nom au regard, fondu entrant à 0 / 80 / 160 / 240 ms, nametag plein, maintien 1 s puis fondu sortant à 0 / 150 / 300 / 450 ms ; 1ʳᵉ personne ; accroupi (aucun nom) ; à terre et inconscient avec la croix de l'ancre (`TeteAncre.debugCroix` : sur la tête, pas au-dessus des pieds ; écart tête – pieds journalisé, 1,64 bloc allongé) ; émote (`CombatAnimations` sur le mannequin : garde tenue, coup avec la croix) ; voix (3 barres) puis frappe (3 points) **sans nom** (le regard quitte la cible avant que l'horloge n'avance) ; /me court à 0 / 60 / 150 / 300 / 600 ms, /me long seul (3 lignes puis « … ») ; pile complète (nom, barres, deux /me) sur le ciel de jour ; masqué (« ??? ») ; /me à terre ; **fond clair** (1ʳᵉ personne, 3 blocs plus haut, la neige derrière la pile ; frappe) et **nuit** ; auteur d'un /me qui parle, **caché derrière** un mannequin plus proche (posé dans l'axe caméra → mannequin, 3ᵉ puis 1ʳᵉ personne, sans puis avec le nom de celui de devant ; attendu : ni bulle ni /me sur celui de devant — alignement et « tête de derrière cachée par un corps : oui » journalisés) ; **nom en maintien, un autre passe devant** (`21a_nom_maintenu_devant`, 1ʳᵉ personne : le regard part 20° à côté, le mannequin de derrière vient 1,5 bloc devant celui qu'on a regardé, silencieux ; attendu : rien sur son visage — le nom suit `teteDegagee`, corps compris, depuis le 01/11) ; **orateur à 12 blocs** à l'écart (`21b_voix_12_blocs` : la voix de proximité garde sa bulle, atténuée vers 80 % ; `21c_frappe_12_blocs` : la frappe n'en a pas ; posé à droite, loin de l'axe caméra → mannequin, et maintien du nom précédent écoulé : sinon un nom s'affiche sur la tête du joueur) ; **auteur devant soi en vue épaule** (le mannequin qui parle et fait un /me est posé dans l'axe caméra → joueur, noms coupés le temps des deux cas ; `21d_derriere_soi_depasse` : à 3 blocs, le haut de sa tête dépasse, bulle et /me posés au-dessus, pas sur la tête du joueur ; `21e_derriere_soi_cache` : à 1 bloc, centre de sa tête caché par le corps du joueur, ni bulle ni /me — son propre corps compte en 3ᵉ personne) ; soi en 3ᵉ personne ; micro des vitals vert / jaune / rouge / qui parle (`VoiceState.DEBUG_PORTEE`, `DEBUG_SOI_PARLE`) ; micro à terre (phase KO 1 du joueur : vitals masqués, micro seul « chuchoté ») ; **marche** au sol avec balancement de vue (touche Avancer tenue, 4 captures à 2 ticks d'écart : l'écart nom – tête passe de 11-12 à 14 px en approchant de ~1,3 bloc, sans osciller avec le balancement) ; petit écran 1366×768 (`Window.setWindowed`) ; échelle 3 ; mur (tout masqué) ; styles Filet, Village, Ruban. Journal `[nametags-banc]` (origine, chaque photo avec la cible retenue, ancres des corps au sol, alignement des cas cachés avec la valeur attendue, distance de l'orateur lointain, position finale). Dernier passage : 01/11, après les correctifs du 31/10 au soir, 56 captures, aucun mixin en échec au lancement. À la fin : mannequins tués (`kill` : envoyé sous le monde, un mannequin y survit plusieurs secondes), drapeaux, style, échelle, fenêtre, balancement, caméra, mode de jeu, **heure et météo d'origine**, joueur remis à sa **position d'origine en absolu**, remonté au premier 1/16 de bloc libre si ses pieds touchent un bloc (le heightmap `motion_blocking` ignore les couches de neige : dans « New World », les pieds du joueur de dev étaient 4/16 dans `snow[layers=3]` ; la base du banc est relevée de même). Le mur provisoire (béton gris) n'est posé que sur de l'air puis retiré. Voix et frappe simulées par `VoiceState.DEBUG_PARLE` / `DEBUG_SOI_PARLE` et `TypingState.update` ; au départ, les restes d'un banc interrompu sont tués et le husk sans IA que laisse `REBORN_COMBAT_DEBUG` est envoyé sous le monde. À lancer par Git Bash comme ci-dessus : sous Windows PowerShell 5.1, `--args='… "New World" …'` passé à `gradlew.bat` est coupé à l'espace (« Task 'World …' not found ») |
| `REBORN_VOIX_DEBUG=1` | Voix (31/10, banc imbriqué `ko/MicroATerreHud.Banc`, serveur vocal intégré de Plasmo Voice en solo ; le monde n'est pas touché) : réglages Reborn de Plasmo Voice vérifiés (icônes au-dessus des têtes masquées, touche « Action » déliée, liste des orateurs coupée depuis le lot 2), micro des vitals vert / jaune / rouge (`VoiceState.DEBUG_PORTEE`), « je parle » (deux phases de la pulsation), coupé, assourdi ; la **vraie** touche « Portée de la voix » (chemin du clavier par réflexion) fait un tour complet des portées du serveur intégré, un cran plus bas à chaque appui (16 → 8 → 32 → 16), notification et cercle de portée ; à terre (phase KO 1 simulée) : micro seul « chuchoté », touche refusée, portée inchangée → « [voix] OK / ECHEC » dans le journal et `run/voix_banc.txt`, captures `run/screenshots/voix_01..13_*.png` ; échelle GUI et portée de départ rendues à la fin. Le `client.toml` de Plasmo Voice du dossier de dev garde les deux réglages Reborn (voulu) |
| `REBORN_TETE_ANCRE_DEBUG=banc` (`=1` ou `-Dreborn.teteAncreDebug=true` : croix seules, en jeu) | Ancre de tête (31/10, `anchor/TeteAncreBanc`, §9) : quatre mannequins (debout, accroupi, à terre « ramper », allongé sans lit), croix au centre de la tête (vert : capturée, or : recalée, rouge : estimée ; pâle si masquée), trait au sommet du chapeau et, en gris, l'ancien ancrage (œil + 0,62) ; vue de face, épaule (soi compris), autre angle, plongée, puis en 1re personne un mur de pierre (croix pâle) et une vitre (croix vive : le verre ne masque pas) → `run/screenshots/ancre_tete_0*.png`. Prouve aussi que les mixins de la capture s'appliquent au lancement. Vol en créatif, murs posés un bloc au-dessus du sol puis retirés : le monde n'est pas modifié |
| `REBORN_INTERACTION_DEBUG=1` | Menu R du Game Plan §14 (24/10, `interaction/InteractionDebug`) : mannequin « autre joueur » décalé à droite (la caméra épaule cache ce qui est droit devant), 10 captures en 1920×1080 — menu d'une personne consciente (survol « Porter (avec son accord) »), « Volume de sa voix » (75 % actuel), sous-menu Staff (staff imposé : « Couper la voix… », puis `03b` « Rétablir la voix » sur une voix coupée, 31/10), demandes « veut te porter » / « veut te fouiller », porté de plein gré (« Espace · descendre »), fouille consentie en lecture seule (personne fouillée à gauche), nametag connu puis masque Hannya → « ??? » (vue à la 1ʳᵉ personne, pack `reborn_boutique_dev`) → `run/screenshots/interaction_*.png`, copies dans `Maquettes/gameplan/14_interaction/code/`. Mode de jeu, heure, météo, caméra et pack rendus à la fin |
| `REBORN_DOUANE_DEBUG=1` | Douane, lot E (25/10, `douane/DouaneDebug` + `staff/GardeDouaneDebug`) : rejoue des messages `reborn:douane` au format du protocole v1 (PLUGINS_REFERENCE §4 decies.4 à .6, demandes C → S interceptées), mannequin « autre candidat » devant la caméra ; 21 captures en 1920×1080 à l'échelle GUI 2 puis en 1366×768 — hall (ligne, rappel des règles, nametag du pseudo), hall fermé, vérification de l'accès, appel et fondu, bureau (nametag « Examinateur »), question courte et longue, Bienvenue, À revoir, Refusé, résultat différé, Poste de garde › Douane (file, survol « Appeler »), dossier en volet élargi, questions et décision, envoi en attente, retrait, indicateur du mode examinateur → `run/screenshots/douane_*.png`, copies dans `Maquettes/gameplan/candidature/jeu_reel/`. Mode de jeu, heure, météo, échelle et fenêtre rendus à la fin |
| `REBORN_RADIO_DEBUG=1` (+ `REBORN_RADIO_POS="x y z lacet tangage"`, jamais noté dans le dépôt) | Radio en escouades (14/10, `radio/RadioDebug`) : 17 captures en 1920×1080 à l'échelle GUI 2 (rendue à la fin) — écran de l'escouade (survol « Exclure », dissolution à confirmer, vu d'un membre, renommer, sans escouade), élément Escouade (silence, Sakura parle, j'émets, toute l'escouade, invitation, à terre « haché », écoute staff, oreillette volée), éditeur de HUD (élément sélectionné, case « Afficher toute l'escouade »), menu R (Ajouter à « Kagerō », Créer une escouade, Oreillette sur un joueur à terre) → `run/screenshots/radio_*.png`, copies dans `Maquettes/gameplan/18_radio/code/`. Têtes RP prêtées par `RebornSkins` (rendues à la fin) ; la voix n'est pas simulée |
| `REBORN_METEO_DEBUG=1` | Météo (l'ancien banc `REBORN_BYAKUGAN_DEBUG` est remplacé par `REBORN_LOT5_DEBUG=byak`) |
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
| `REBORN_ESC_DEBUG=captures` (+ `REBORN_ESC_STAFF=1` ; autres modes `reprendre`, `echap`, `boutons`, `tout`) | Échap e5 du lot 3 (05/11, `menu/esc/EscDebug`) : données fictives (solde 1 250, Discord 1 234, stream en direct, deux charmes actifs), repos et survol de chaque entrée et de chaque carte, Boutique fermée puis ouverte, fermeture et ouverture animées → `run/screenshots/echap_e5_*.png` ; retours du 05/11 (`echap_e5_r0197_*`) : carte Stream sur les streamers 1, 2, 3, sortie, entrée, survol, hors ligne, défilement réel vérifié (change seul, pas au survol), identité masquée, sans version, identité réelle en solo (vide) ; perso quitté avec sa fiche encore en mémoire (Échap vide, sacoche = pseudo seul), roster avec personnage (Échap et sacoche « Aki Uchiha · Chūnin · Konohagakure » sans fiche ouverte, pied « Serveur 1.2.0 » tiré de la patch note fictive) ; identité « Aki Uchiha » imposée par le banc seulement ; « [EscDebug] OK / ECHEC » dans le log |
| `REBORN_LOT3_DEBUG=1` | Lot 3 « Échap et touches » (05/11, `audit/Lot3Debug`, intégrateur) : rappels en jeu (normal — `RappelsTouches.concentrationBranchee` forcé le temps du banc : avant le lot 4, aucune ligne permanente —, concentration, combat via `RappelsTouches.debugMode`, plafond de 3 lignes où Concentration reste), éditeur de HUD ouvert sur les rappels (panneau replié à gauche), Paramètres (Commandes › Monde et › Voix et escouade, conflit Concentration sur F, Interface › Rappels et HUD, Audio › Voix et Général, Graphismes › Affichage et Rendu, Compte, Compte › Avancé, Graphismes en « Petite »), puis (05/11, `lot3_r0197_*`) le lien « Options de Minecraft › » au repos et survolé, le clic qui ouvre l'écran d'origine sans redirection, « Terminé » qui revient aux Paramètres, Vidéo ouvert depuis l'écran d'origine, redirection maintenue hors du lien → `run/screenshots/lot3_*.png`. Rien n'est sauvegardé. À lancer en 1920 × 1080 puis 1366 × 768, client en `lang:fr_fr` pour les noms de touches |
| `REBORN_LOT4_DEBUG=b` | Lot 4, **étape B** (07/11, `audit/Lot4bDebug`) : scène bâtie sur un sol à nous (six stèles, lanterne, mur), serveur simulé côté client (`ConcentrationCanal.debugServeur`, `GardePlaquesDebug.serveur`), horloges figées ; lecture automatique à 2, 4 et 6 blocs de jour et de nuit, halo à 0, 120, 250 ms, sortie à 175 ms et fin, deux stèles (la plus centrée puis l'autre après un pas), hors de portée, mur entre les deux, narration qui passe devant puis retour, texte long (pages 1 et 2) ; outil « Plaques » : liste et repères, fiche et aperçu, sélecteur (survol, bloc, boîte), éditeur et aperçu ; en 1920×1080 puis 1366×768 → `run/screenshots/lot4b_*.png`, `[lot4b] OK / ECHEC`, puis ferme le jeu |
| `REBORN_LOT4_DEBUG=c6` | Couleur du halo des plaques (08/11, `audit/Lot4c6Debug`, planche c6) : quatre stèles en rang (ivoire-or = halo absent, bleu chakra `6FB4FF`, rouge sang `D9443F`, aucun), serveur simulé qui envoie `halo` avec le texte, horloges figées ; vérifie les palettes dérivées (cœur du bleu chakra = `E9F4FF`), les codes du protocole et `Plaques.haloDe` ; 1920×1080 jour puis nuit, chaque stèle à 4 blocs sous l'effet ; outil Plaques : éditeur au repos, bleu chakra, aucun, « Personnalisée » ouverte (rose cerisier `F29CC2`, 4 récentes), plaque déjà en `123ABC` par le vrai chemin « Modifier le texte » puis « Personnalisée » (curseurs et champ sur sa couleur, rien de changé, clic sur la teinte), éditeur sans ancre (aperçu de repli : rouge sang, aucun), fiche bleu chakra ; 1366×768 : une stèle, éditeur, sélecteur, fiche → `run/screenshots/lot4c6_*.png` (copies dans `Maquettes/gameplan/17_lot4_concentration/code_c/`), « [lot4c6] FIN » |
| `REBORN_LOT4_DEBUG=c7` | Traces de pas (08/11, `audit/Lot4c7Debug`, planche c7) : chemin de pierre, dalle, escalier, terrasse, sable, bâtis sur un sol à nous ; serveur simulé qui envoie le vrai message `traces` (hauteur lue dans la vraie collision), horloges figées ; 1920×1080 puis 1366×768 : pistes de jour et de nuit (marche, sprint qui croise, accroupi, vieille piste sur le sable), sortie à 75 ms puis plus rien (vérifié au journal), vue plongeante du frais au vieux (jour, nuit), gros plan (âges, sprint, accroupi, chargé, sang, marche vers le fond), survol (fondu à 60 ms, « à l'instant », « il y a 7 min », curseur ailleurs), quatre couleurs dans le monde (ivoire, bleu chakra, rouge sang, ambre), sous le Byakugan (ni traces ni étiquette, puis retour), Paramètres › Interface › Concentration (ivoire, bleu chakra, « Personnalisée » ouverte), parcours par les vrais événements de l'écran (ouvrir et refermer « Personnalisée » depuis l'ivoire sans rien changer, clic sur une pastille, sur le lien, dans le carré, saisie `#202020` relevée) → `run/screenshots/lot4c7_*.png` (copies dans `Maquettes/gameplan/17_lot4_concentration/code_c/`), `[lot4c7] OK / ECHEC`, couleur remise, puis ferme le jeu |
| `REBORN_LOT4_DEBUG=c8` | Traces, retour du 08/11 (`audit/Lot4c7Debug`) : contraste automatique et « une trace reste ». Bandes d'herbe, de pierre, de sable, puis neige, béton blanc, quartz, calcite, bouleau ; table `Traces.solClair` sur les couleurs de carte réelles au journal, classement de chaque pas lu dans le monde, sol inconnu relu en moins de 2 s, jour et nuit, rangées à 10, 80 et 95 % de la vie, gros plan d'un sprint et d'une marche sur le sable (jour sans contour, nuit avec contour fermé au talon), « il y a 1 h 20 » sur l'herbe et sur le sable (nuit comprise), réglage « Automatique » par les vrais événements de l'écran (pastille, retour, « Personnalisée » sans le quitter, un geste le quitte, « Réinitialiser ») ; 1920×1080 et 1366×768 → `run/screenshots/lot4c8_*.png` (copies dans `Maquettes/gameplan/17_lot4_concentration/code_c/`), réglage et sol remis, puis ferme le jeu |
| `REBORN_LOT4_DEBUG=c9` | Traces, retour du 10/11 (`audit/Lot4c7Debug`) : petit panneau au survol et âge estimé. Chemin de terre, sable à droite, neige à gauche, herbe ; paliers de `TracesAge` vérifiés au journal ; panneau sur le chemin de jour (fondu d'entrée à 60 ms), sur le sable (point sombre), vers la neige (glissement à 45 ms), à 7,8 blocs, sortie à 75 ms, de nuit (chemin, sable) ; un pas par palier (à l'instant, quelques minutes, 10 min, 40 min, 1 h 30, plus de deux heures) ; grille de pas : celui du bord gauche de l'écran (panneau gardé à l'écran) et celui dont le panneau tomberait sur le réticule ; chaque panneau vérifié au journal (dans l'écran à 8 px × k, hors du réticule) → `run/screenshots/traces_v6_*.png` (copies dans `Maquettes/gameplan/17_lot4_concentration/code_v6/`), sol remis, puis ferme le jeu. Le test `TracesAgeTest` ne passe pas par Gradle (pas de JUnit déclaré) : compilé et lancé à part avec les jars JUnit 5.10 du cache Gradle |
| `REBORN_LOT4_DEBUG=v5` | Retrait de R (migration 5, 07/11, `audit/Lot4v5Debug`) : bilan de la migration réelle du démarrage (lancer avec `options.txt` sur `key_key.reborn-hud.open_interaction:key.keyboard.r` et `touchesVersion` à 4 dans `config/reborn_prefs.json`), règle pure `retraitR` (options neuves, R personnalisée, Concentration sans touche, R déliée, souris, Concentration sur R), migration rejouée sur les vraies liaisons (drapeau 4 → 5 écrit sur le disque, idempotence, R personnalisée gardée, Concentration sans touche = R gardée, « Réinitialiser la section »), claviers simulés (BÉPO, Dvorak, Colemak : Interaction posée par la version 2 sur la touche qui affiche « r » = défaut ; migration et « Réinitialiser la section » en BÉPO), captures Commandes › Monde (sans ligne Interaction, Interaction personnalisée, Concentration sans touche), parcours d'un vrai joueur dans le même écran par les cases (`KeybindButton`) : Concentration liée → aide d'Interaction à jour, Échap sur Interaction → ligne partie sans redémarrage, invite « Tendre » et rappel sur Y puis sur R gardée → `run/screenshots/lot4v5_*.png`, `[lot4v5] OK / ECHEC`, liaisons et drapeau remis, puis ferme le jeu. Client en `lang:fr_fr` |
| `REBORN_LOT4_DEBUG=d` (lancer avec le même `--username` pour garder le même skin) | Touche maintenue et effet K+ (08-09/11, `audit/Lot4dDebug`, lecture physique simulée par `audit/ClavierBanc`, c'est-à-dire la seule lecture GLFW, serveur simulé avec la vraie limite de 4 `on` par seconde) : entrée à l'appui, sortie au relâchement, marche, accroupi, clic droit ; pas de nouvelle entrée touche tenue après un coup (même 5,5 s après), un Malaxage, Échap, un écran, un refus du serveur, ni après un refus d'entrée ; menu qui reste après le relâchement (clic dehors, action, Échap, touche reprise) ; fenêtre quittée ; lettre tapée dans le tchat, tchat ouvert puis relâchement ; répétition dans le « /me » du vrai menu (Y, marche, retour arrière, autre lettre, par `KeyboardHandler.keyPress` et `charTyped`), lettre ou bouton de souris suivis de la fermeture de l'écran sans tick entre les deux ; dix appuis à trois rythmes (aucun refus du serveur, souffles espacés, rien de coincé), fondu sans saut ; bouton de souris, touche non liée, ancienne touche ; captures 1920×1080 et 1366×768 : jour, nuit, traces sur l'herbe, le sable, la pierre et la neige (automatique, bleu chakra, rouge sang, ambre), plaque et son halo, joueur survolé, menu ouvert après le relâchement → `run/screenshots/lot4d_*.png`, puis ferme le jeu |
| `REBORN_LOT5_DEBUG=saut` (`=saut_styles` : trajectoire et barre seulement, ~2 min de jeu ; lancer avec `--quickPlaySingleplayer "New World" --width 1920 --height 1080 --username BancReborn` : sans monde, le banc attend au menu) | **Saut chargé** (lot 5, étape A, 09/11, `audit/Lot5Debug`) : couloir de 11 × 118 blocs sur une herbe à nous (dégagé sur 46 de haut ; le terrain d'origine n'est pas rendu), serveur simulé avec la loi et les octets du plugin, horloge de la charge figée, touches simulées, **vol réel** par le mixin. Charge à 0, 25, 58 et 100 % ; départ, mi-vol, réception ; **justesse** : 11 sauts (135 ms à 1,2 s, visée de 0 à 60°, un mur à 10 blocs, un toit de 4 blocs) : arrivée réelle contre arrivée prédite (au 09/11 : écart 0,00 bloc partout), et à plat contre la loi pure ; **trajectoire en poussière et barre de charge** (10/11, 6e tour ; **vue imposée à chaque capture** et vérifiée — une capture du mur était sortie en 1re personne ; arrivée = loi pure à plat à 1 bloc près, ou 8 à 11 blocs au pied du mur ; ligne pleinement apparue ; grains posés comptés au journal, au 10/11 : 44 à 46 en 1re personne, 77 à 120 en 3e, + 48 sur l'anneau) : 1re personne à mi-charge (`1p_050`, ~39 blocs), épaule pleine charge (`3p_100`, ~60 blocs), 1re personne pleine charge (`1p_100`), épaule à mi-charge (`3p_050`), au pied d'un mur à 10 blocs (`mur_10`, regard à 18° : à 6°, l'arrivée tombe derrière la tête), sous le Byakugan (`byakugan_050`), de nuit (`nuit_100`) → `saut_v6_<scène>.png` (copiées dans `Maquettes/gameplan/17_lot5_mobilite/code_ab/`) ; écran ouvert = charge annulée (kind 10) ; refus « Pas assez de chakra » (sans recharge fantôme) et « Étourdi » (rien n'est envoyé), en 1920 × 1080 puis 1366 × 768 → `run/screenshots/saut_*.png`. Vraie lumière : le plateau du banc Byakugan, au-dessus de la même origine, ombrait le couloir — il est retiré et le banc attend la lumière du ciel à 15 sur huit points témoins ; l'herbe passée en terre sous le mur ou le toit est remise ; contour vanilla du bloc visé masqué (portée de visée nulle le temps du banc) ; chaque capture attend 3 images comptées par une couche HUD du banc (une capture pouvait reprendre l'image d'avant). Bilans `[lot5] OK / ECHEC` et tableau de justesse au journal, puis ferme le jeu |
| `REBORN_LOT5_DEBUG=byak` (`=byak_vite` : les décors seulement ; `=byak_fps` : coût du filtre seul, forêt du banc sans personne, quatre alternances sans / avec, et la saccade des 1,5 s qui suivent chaque bascule (feuilles pleines : image la plus longue, médiane, images lentes, excès cumulé) ; `=byak_entites` : de vraies bêtes sans IA (villageois, zombie du désert, cochon) derrière le mur du champ, vues au travers (`byak_entites_mur`), puis à découvert de près et à ~20 blocs (villageois, zombie du désert, vache, mouton : `byak_entites_proche`), difficulté facile le temps du banc, objets au sol retirés et nuage de mort dissipé avant chaque capture ; `=byak_vues` + `REBORN_BYAK_VUES="nom:x,y,z,lacet,tangage[,heure];…"`, jamais noté dans le dépôt : vues d'un vrai monde, une copie de Konoha par exemple ; hauteur `sol` : posé sur le vrai sol, feuillages exclus ; en vol à chaque vue, 240 images d'attente et sans fondu d'apparition des chunks le temps du banc (option rendue à la fin) ; bêtes à moins de 16 blocs et fin du brouillard notées au journal ; lancer avec `--username BancReborn` pour garder le même skin ; la copie de Konoha est le monde `Konoha`) | **Byakugan** (lot 5, étape B, 09/11, `audit/Lot5ByakDebug`) : plateau à nous dans le ciel près de l'origine (le relief du monde de dev ne fait ni ombre ni trou) — champ, rue façon Konoha (chaussée de pierre, maisons de bois et d'enduit, toits sombres, lanternes, passants, une personne dans une maison), forêt (vrais arbres placés), mur avec deux personnes derrière ; grotte fermée sous terre ; serveur ShinobiCore simulé au format exact de `reborn:dojutsu` (règles, réponses), touche O par le vrai `ByakuganClient.basculer()`. Vérifie : règles reçues, touche O par défaut, ancien canal ignoré, sans droit rien n'est envoyé, deux personnes cachées sur deux, fondu image par image à horloge figée (90 % à 0,35 s, sortie linéaire finie à 0,27 s), concentration puis Byakugan (un seul filtre, la concentration reprend le sien), KO = coupure nette et fin envoyée ; profondeur vue par le filtre (`debugProfondeur`, bandes de 8 blocs) et capture des boucles d'encre ; encre : traits seuls (`debugTraits`, blanc sur noir : les fins de volume seulement depuis le 8e tour, plus de pli ni de grille) au champ, à la rue, en forêt, la nuit, en grotte et sur chaque vue `byak_vues`, puis après deux pas d'un quart de bloc (`_traits_p1`, `_traits_p2` : tenue du trait en marchant, mesurée hors du jeu contre la parallaxe attendue), particules sur le sol (`byak_particules`, sans écho), feuilles pleines pendant la vision seulement et option « Feuilles découpées » du joueur inchangée (forêt, puis coupure au KO) ; images par seconde sans et avec le filtre, même vue, alternées → `run/screenshots/byak_*.png` (sans / avec / `_traits` / `_prof` : distance brute sur 24 bits, pour régler le filtre hors du jeu avec `_sans` ; forêt aussi en 1366 × 768) ; chaque étape attend 3 images rendues (couche HUD du banc : fenêtre en arrière-plan, une capture sur les ticks seuls reprenait l'image d'avant), bilans `[byak] OK / ECHEC`, puis ferme le jeu |
| `REBORN_LOT6_DEBUG=mudra` (lancer avec `--quickPlaySingleplayer "New World" --width 1920 --height 1080 --username BancReborn`) | **Barre de mudras** (14/11, `audit/Lot6MudraDebug`) : serveur ShinobiCore simulé au format de `reborn:mudra` (règles, ouverture libre et guidée, fin, refus), touche par le vrai `MudraClient.basculer()`, signes par le vrai `MudraClient.signe`, horloge du mod figée et avancée à la main. Vérifie : touche U par défaut, douze touches de la rangée des chiffres, rien sans règles, version étrangère ignorée, une seule demande, rangée prise puis rendue, signe envoyé avec la bonne session, écart de 120 ms, fin d'une autre session ignorée, séance de mémoire sans signe mis en avant, compte qui avance, mauvais signe envoyé quand même, suite d'un ancien serveur encore mise en avant, refus, fermeture de secours, second appui, extinction par le serveur → `run/screenshots/mudra_*.png` (entrée, barre, signe, temps, fin, guide, guide_2, rate, refus, pose de face, 768), bilans `[mudra] OK / ECHEC`, puis ferme le jeu |
| `REBORN_LOT6_DEBUG=portes` (mêmes arguments de lancement) | **Outil Portes du Poste de garde** (16/11, `audit/Lot6PortesDebug` + `staff/GardePortesDebug`) : serveur simulé côté client au format de PLUGINS_REFERENCE §4 terdecies.7 (données des maquettes, grades Helper / Modo / Admin, réponses `porte_verif` retenues à la demande), horloge des vérifications figée, mur de 3 × 2 bâti puis retiré, clics sur les vraies zones du volet. 92 vérifications : format de chaque message envoyé, sélecteur (dalle d'un bloc, 250 ms d'écart, réponse périmée ignorée, refus sur place et refus du serveur), « Sceller la porte » éteint sans nom ou sous 2 signes, un seul envoi pour deux Ctrl + Entrée, fiche ouverte sur `dernier`, sous-éditeurs, confirmation du retrait et de l'extinction, Helper (suite jamais affichée même si le serveur l'envoyait, actions éteintes, rien d'envoyé), 1366 × 768 → `run/screenshots/portes_*.png` (liste, selecteur, selecteur_refus, nouvelle, fiche, fiche_helper, retirer, 768, outils, nom, leve, suite), bilans `[portes] OK / ECHEC`, puis ferme le jeu. Le journal ne cite les messages que par action et id (jamais une suite) |
| `REBORN_MUR_SPIKE=1` (lancer avec `--quickPlaySingleplayer "New World" --width 1920 --height 1080 --username BancReborn`) | **Essai de la marche sur les murs** (24/11, `audit/MurSpike`) : serveur simulé côté client au format du canal `reborn:mur`, mur de briques de 9 × 12, mannequin comme autre joueur. Vérifie « [mur] OK / ECHEC » : rien sans permis, formats, abandon après 1 s, réponse tardive rendue, refus, écho d'un lâcher pendant une demande, boîte de 1,75 × 0,6 × 0,6 sur les quatre faces, pose tenue, `P` à 0,9 de la paroi, 3,2 et 5,6 blocs/s, arrêts au bord et sous un obstacle, sorties (Espace, touche de course, avance tenue, pied du mur, permis retiré). « [mur] FIN (n échec(s)) ». Captures `mur_accroche`, `mur_monter`, `mur_descendre`, `mur_cote`, `mur_long`, `mur_dos`, `mur_boites`, `mur_second_boites`, `mur_second`. Le banc rend la souris et repose les touches à chaque tick : ne pas jouer pendant qu'il tourne. Le solo ne prouve pas les refus de déplacement du serveur |
| `REBORN_LOT4_DEBUG=1` | Lot 4 « Concentration », étape A (07/11, `audit/Lot4Debug`) : mannequin « autre joueur » décalé à droite, horloge du fondu figée (`ModeConcentration.horloge`) ; de jour en 1920×1080 puis 1366×768 : avant, entrée à 0 et 125 ms, concentré (six rappels), curseur sur le mannequin (contour), clic (menu de l'entité), menu d'un joueur, sortie à 75 ms, après ; de nuit : avant, concentré, survol ; blocages vérifiés au journal (`[lot4] OK / ECHEC`) : Malaxage du chakra (refus, capture, puis sortie), coup donné (sortie, refus 5 s), tchat (le mode reste), écran (sortie), Échap par le vrai chemin du clavier, touche Concentration tenue puis relâchée → `run/screenshots/lot4_*.png`, puis ferme le jeu |
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
- Au-dessus des têtes (nametag, bulle, /me, nametag staff, invite, dégâts) : ne plus projeter « œil + k » soi-même — passer
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
lisibles par une ombre portée d'1 px ; le nametag garde ses 4 styles). Banc : `REBORN_NAMETAG_DEBUG=1` (§5).
Toutes les constantes sont en tête de leur classe.

- **Ancre** (`anchor/TeteAncre`) : centre de la tête **réellement rendue** (émotes, ramper, à terre, allongé sans lit),
  projetée comme le monde (balancement compris), avec les trois demi-axes de la boîte de tête (chapeau compris) pris dans
  la même pose. `CalqueTete.ancre(e, out)` = `TeteAncre.ecran(e, 0, out)` : **sommet de la tête vue par la caméra** en px
  GUI calés au pixel physique (`ancrePx` en pixels de la fenêtre, pour ce qui se dessine en « unités menu ») — on monte
  le long de l'axe « haut » de la caméra de la distance d'appui de la boîte (`|hx·haut| + |hy·haut| + |hz·haut|`), puis,
  de près, le coin le plus haut à l'écran l'emporte s'il dépasse (perspective) ; neuf projections par entité et par
  image, mises en cache, aucune allocation. Correctifs du 31/10 au soir : on montait de 0,265 vers le haut du monde,
  juste pour une tête droite vue à hauteur d'œil — la pile mordait sur une tête basculée (à terre, demi-hauteur réelle
  0,373) ou vue en plongée (bord arrière du chapeau). Vu de loin à hauteur d'œil, rien ne bouge. Tout ce qui flotte au-dessus d'un joueur s'y ancre : nametag, bulle, /me, nametag staff, invite « Tendre »,
  chiffres de dégâts (`CombatHud`, marge 0,413 bloc = l'ancien « œil + 0,7 » d'un joueur debout).
- **Pile** (`nametag/CalqueTete`), de la tête vers le haut : sommet de tête → 3 px (`ECART_TETE`) → nametag → 2 px
  (`ECART`) → bulle → 2 px → /me empilés → 2 px → nametag staff → 2 px → invite. Chaque élément **déclare** sa hauteur
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
  commune : `Nametags.horloge` (injectable).
- **Nametag au regard** (`Nametags`) : rayon depuis la **caméra** (position + direction ; vue épaule par défaut),
  prolongé de la distance caméra – œil ; cible = hitbox gonflée (0,30 pour accrocher, 0,45 pour garder) unie à une
  boîte de ±0,35 × échelle autour du centre de la tête (un joueur allongé se vise à la tête) ; 6 blocs au plus de
  l'œil ; tête dégagée (jamais à travers un bloc opaque ni un corps plus proche) ; le plus proche dans l'axe ; regard tenu 90 ms (un viseur qui
  balaie un groupe n'allume personne). Déroulé : fondu entrant 160 ms (ease-out), visible tant qu'on regarde,
  **maintien 2,5 s**, fondu sortant 450 ms ; au-delà de 9 blocs pendant le maintien, sortie immédiate ; accroupi,
  invisible ou tête cachée (mur, ou quelqu'un qui passe devant pendant le maintien) : sortie en 200 ms. Atténuation avec la distance (pleine à 3 blocs, 60 % au moins). Les
  quatre styles sont inchangés ; l'ancre est calée une fois au pixel physique et le nametag dessiné en coordonnées
  locales entières : le contour du style « Détouré » ne scintille plus. Textes ArcadePix composés une fois par nom
  (`Da.arc` normalisait et compilait une regex à chaque appel, neuf fois par nametag). Banc : `DEBUG_NOMS` (un nom par
  mannequin), `debugAvatars`, `debugInconnu` (« ??? »).
- **Bulle** (`voice/SpeechBubbles`) : **sans fond** depuis le 31/10 au soir (plus de capsule ni de pointe) : le motif
  seul, blanc doux, centré sur l'ancre et posé au-dessus de la tête comme le nom ; lisible sur la neige et le ciel clair
  par une ombre portée d'1 px écran (encre à 85 % × le fondu) et un **contour sombre très discret** sur les 4 côtés
  (1 px écran, 2 en 4K, encre à 40 % × le fondu, invisible sur fond sombre) ; voix = 3 barres d'1 unité qui ondulent
  (égaliseur doux, rythme propre à chaque joueur), frappe = 3 points en vague, nets au repos (85 %) ; la voix l'emporte.
  Tailles en **unités menu** (`DaKit.echelleMenu()`, comme le nametag staff) : la taille validée à l'échelle 2 en 1080p à
  toutes les échelles d'interface. La couche a une **hauteur fixe** (les barres au plus haut, contour et ombre compris ;
  la frappe y est centrée) : passer de l'écriture à la parole ne fait plus glisser le /me ; éteinte, elle reste
  **réservée** (invisible) 1,5 s tant qu'un /me ou un nametag staff est posé au-dessus : le /me ne redescend plus à
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
  `{"v":1,"t":"me_fin","id":…}` efface ses bulles. `nom`, `inconnu` et `ton` ne sont pas affichés (le nom est sur le
  nametag) ; codes `§` retirés, 256 caractères au plus. Rendu **sans fond** (31/10 au soir) : texte blanc doux en Inter 8
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
- **Nametags staff, invite « Tendre »** : mêmes ancre et pile (couches `STAFF` et `INVITE`), dessins inchangés. Le nametag
  staff reste visible à travers les murs (outil staff).
- **Centre dégagé** (lot 2, étape 2c, LISEZMOI v2) : pendant un texte au centre (`recit/`), le nom au regard, la bulle et
  la pile de /me dont la boîte touche le bloc élargi de 12 px (× f) passent à 25 %, au rythme du fondu du texte, avec un
  retour progressif sur les 6 px suivants (pas de saut quand la caméra tourne) : `RecitHud.attenuation(x0, y0, x1, y1)`
  en px GUI, appelée par `Nametags`, `SpeechBubbles` et `MeBulles` juste avant leur dessin (1 sans texte au centre :
  rien ne change hors narration). Nametag staff et invite ne sont pas touchés (outils).
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
  `LivingEntityTeteAccessor` ; elle était à 75°). L'ancre de tête ignore le regard (le nametag ne danse pas quand on lève
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
  25-100 %, et le clic droit sert aux armes) ; depuis le lot 2 (01/11), liste d'orateurs de Plasmo coupée (`overlay_enabled = false` : elle
  montrait les pseudos Minecraft ; qui parle est l'élément Reborn du bas-droite) **tant que « Qui parle » est affiché** — masquer « Qui parle » (éditeur, Réglages › Interface) la rétablit, le réafficher la recoupe (`RebornVoiceAddon.synchroniserListe`, revue du 02/11) et icône d'activation de proximité masquée
  (`HudActivationRenderEvent`, comme la radio : le micro des vitals pulse et la ligne « soi » de qui parle le disent) ; le cercle de portée de Plasmo est redessiné aux couleurs du micro,
  assombries à 60 % (c'est une sphère translucide qui teinte toute la vue).
- **Outils staff** : Poste de garde › fiche › Plus › **« Couper la voix… »** (V ; motif obligatoire, 5 min, 15 min par
  défaut, 30 min, 1 h ou 1 jour ; joueur en ligne) ou **« Rétablir la voix »** (envoi direct, aussi hors ligne) ; ligne
  « Voix coupée — jusqu'à 21:45 — motif » sous l'état de la fiche (qui l'a coupée au survol), marqueur orange dans la
  liste ; menu R › Staff : « Couper la voix… » ouvre la fiche sur le formulaire, « Rétablir la voix » part tout de suite
  (réponse dans la pile de notifications). Opérations `voix_couper` / `voix_retablir` et champs `voixCoupee`,
  `voixFin`, `voixMotif`, `voixPar` de la fiche : PLUGINS_REFERENCE §4 bis.2.
