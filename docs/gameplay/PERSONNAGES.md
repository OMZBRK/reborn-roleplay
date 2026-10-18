# Personnages — sélection, création, apparence

> **Fiche système, à jour au 2026-10-12** (`reborn-hud` 0.4.168 et plus en production). Le pipeline des skins
> (catalogue d'assets, masques de couleur) est détaillé dans
> [`../technique/CREATEUR_PERSONNAGE_ASSETS.md`](../technique/CREATEUR_PERSONNAGE_ASSETS.md).
>
> Code : serveur `ShinobiCore/…/character/` (dont `select/CharacterSelectManager.java`) ; mod
> `menu/character/` (règles, données) et `menu/character/scene/` (écrans en scène), `skin/` (compositeur).

---

## 1. Ce qu'est un personnage

- Un joueur (compte Minecraft) peut avoir **plusieurs personnages** (`character-select`, limite de slots envoyée
  par le serveur). Chaque personnage a : nom, clan, village, sexe, âge, taille, apparence, grade, stats, niveau
  et XP (invisibles), techniques, blessures, sacoche.
- Stockage : un YAML par joueur dans `plugins/ShinobiCore/characters/`.
- Un personnage **mort** (mort RP validée et exécutée) reste dans la liste, marqué « MORT », et n'est plus
  jouable. Voir [`KO_ET_MORT_RP.md`](./KO_ET_MORT_RP.md).
- **Player ID** (13/10, non publié) : chaque personnage a un **N° à 5 chiffres** (`00000`, `00001`…), attribué à la
  création (et par migration au démarrage pour les anciens), **jamais réutilisé** — un personnage supprimé reste
  dans l'index `player-ids.yml`, marqué supprimé ; un mort RP garde son N°. Le N° est dans le YAML du personnage
  (`playerId`). Le joueur le voit sur **sa fiche** (pastille « N° 00042 » à côté du nom, clic = copie pour un
  ticket) ; le staff le voit au Poste de garde, dans `/pid` et les journaux, et peut viser un personnage par
  `#00042` dans ses commandes. **Jamais montré aux autres joueurs** (« rencontrer » garde les noms secrets).
  Détails : [`../GAMEPLAN.md`](../GAMEPLAN.md) §1.4, [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §2.1 bis.
- **Emplacements** : base 2 (premium 4, staff 7) **+ bonus du compte** débloqué ou retiré par le staff
  (`emplacements.yml`, 15/10, non publié), au moins 1.

## 1 bis. Gestion staff (Poste de garde, 15/10, non publiée)

Tout se fait au **Poste de garde (F7) › fiche du joueur › section Personnages** — y compris pour un joueur **hors
ligne** (le trouver par la recherche : nom RP, pseudo ou `#N°`). Code : serveur `staff/panel/PersonnagesPanel.java`,
mod `staff/GardePersonnages.java` ; protocole [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md)
§4 bis.2.

- **Liste** : un personnage par ligne (N°, nom, clan · village · grade · âge ; « joué » / « mort RP »), puis
  « Emplacements 3 / 4 (bonus +2) » avec Ajouter… / Retirer…, et « + Créer un personnage… ».
- **Détail** (clic sur une ligne) : âge, taille, sexe, date de création (états de service ; « — » pour un personnage
  antérieur), art, affinité, natures, nombre de techniques, ryo, blessures, temps de jeu du **compte** (en ligne
  seulement : pas de compteur par personnage) ; **fiche shinobi** : les six stats, PV et chakra max, points à
  répartir, bonus staff, niveau.
- **Actions** (formulaire dans le volet ; grisées avec cadenas si le grade ne suffit pas ; le serveur revérifie) :

| Action | Grade | Motif | Effet |
|---|---|---|---|
| Consulter | Helper | — | liste, détail, fiche shinobi |
| Identité… (nom, âge, taille en cm, sexe) | Modérateur | facultatif | nom unique sur le compte ; affichage (tab, plaque) et skin mis à jour si joué |
| Clan et village… | Modérateur | facultatif | clan libre (un clan connu est corrigé, « aucun » le retire) ; 6 villages, Déserteur, aucun |
| Art et affinité… | Modérateur | facultatif | étiquettes RP, sans effet sur les stats |
| Jouer ce personnage… | Modérateur | **obligatoire** | bascule le joueur (en ligne) sur ce personnage ; refusé s'il est à terre, en combat, gelé, à l'écran de sélection, ou si le personnage est mort |
| Grade et points… | Admin | facultatif | grade (inscrit aux états de service, points recalculés), points de stats bonus |
| Déclarer mort RP… / Ressusciter… | Admin | **obligatoire** | hors scène (une mise à mort passe par les alertes) ; s'il est joué, le joueur revient à la sélection |
| Remettre à zéro… | Admin | **obligatoire** + N° retapé | comme l'ancien `/persoreset` (sauvegarde `sauvegardes/perso/` avant) |
| Supprimer… | Admin | **obligatoire** + N° retapé | comme l'ancien `/persosuppr` (sauvegarde avant, N° réservé, joueur déconnecté s'il le jouait) |
| Emplacement + / − | Admin | **obligatoire** | bonus du compte, de −(base − 1) à +20 |
| Créer un personnage… | Admin | **obligatoire** | nom seulement (le reste se règle ensuite ; apparence par défaut), N° attribué tout de suite |

- **Journal** : chaque action est au journal universel (`STAFF` : `PERSO_IDENTITE`, `PERSO_ORIGINE`, `PERSO_ART`,
  `PERSO_PROGRESSION`, `PERSO_ACTIF_FORCE`, `PERSO_MORT_RP`, `PERSO_RESSUSCITE`, `PERSO_CREE`, `PERSO_RESET`,
  `PERSO_SUPPRIME`, `EMPLACEMENT_AJOUTE`, `EMPLACEMENT_RETIRE` ; détail « avant → après » et motif) et au journal du
  Poste de garde. Le joueur, s'il est en ligne, reçoit une ligne (« Le staff a modifié ton personnage : … »).
- **Hors ligne** : tout est enregistré tout de suite et s'applique à la reconnexion, sauf « Jouer ce personnage »
  (connecté seulement) et la base d'emplacements (qui dépend de ses permissions : seul le bonus s'affiche).
- **Retiré le 15/10** : `/character create|edit` et leurs écrans-coffres (éditeur, sélecteur de clan), `/persoreset`,
  `/persosuppr`. Restent : `/character select` (repli de la sélection sans le mod), `/character list [joueur]` (texte,
  console comprise), `/character cinematic`. Les points de stats « +1 / −1 » de l'ancien éditeur deviennent un total
  (Grade et points…). Les écrans joueurs « rencontrer » (donner son nom, surnom, demande de nom) ne changent pas.
- **Banc** : `REBORN_STAFF_DEBUG=vite:persos,persos_detail,…` (10 cas, captures
  `Maquettes/gameplan/04_staff/personnages/`).

---

## 2. La sélection « en scène »

Inspirée de Ketto RP (« comme sur ketto c'est stylé de fou ») et de Soulbound.

- Pas de menu plat : une **scène 3D dans un vide sombre**, les personnages en pied (mannequins côté client)
  habillés de leur **vraie apparence** (skins composés), avec leur **stature** : l'âge et la taille changent la
  taille du modèle (11 ans ≈ 80 % d'un adulte, croissance finie à 17 ans ; taille 153 à 207 cm).
- **Caméra cinématique** qui glisse d'un personnage à l'autre ; animations d'attente.
- Sous chaque personnage, des **braises de chakra à la couleur de son village** (Konoha orangé, Suna sable, Kiri
  bleu brume, Kumo jaune, Iwa terre, Ame gris-bleu ; personnage mort = gris).
- **Sons** : souffle du vide en boucle, souffle et tintement au changement de personnage.
- Personnage mort : « MORT » / « Indisponible ». Dernière case : « + Nouveau shinobi ». Échap est bloqué (il faut
  choisir).
- Choisir envoie `select:<id>` puis affiche l'écran de chargement du personnage.
- Le serveur envoie, pour chaque personnage : nom, clan, couleur du clan, village, grade, âge, taille, sexe,
  apparence, mort ou non (canal `reborn:character`). Le mod tolère un serveur plus ancien (défauts : 18 ans,
  taille 1,0, Homme).


## 2 bis. Refonte « au bord de l'étang » (2026-10-13, non publiée)

Maquettes validées : `Maquettes/personnage/01_selection … 09_creation_recapitulatif` (hors dépôt). Code :
`mod-hud/…/menu/character/perso/` ; textures `textures/gui/personnage/` générées par
`tools/gen_personnage_textures.py` (primitives versionnées dans `tools/perso_art.py`, copiées des maquettes).
Écrans par défaut via `CharacterScreens` ; replis `-Dreborn.sceneCharacterScreens=true` (scène 3D) et
`-Dreborn.classicCharacterScreens=true`.

- **Décor** : textures fixes par village (Konoha, Suna, Kiri, Kumo, Iwa, Ame) — ciel étoilé pleine largeur, lune,
  montagnes lointaines, brume, étang (reflets, colonne de lune, rides), roseaux, pierres ; **plus de silhouette de
  village** (retirée le 2026-10-13) : le village ne change que les teintes (ciel, eau, brume, braises, roseaux) ;
  fondu quand le village change. Au-delà de 1 280 × 540 GUI : copies en miroir (aucune couture).
- **Le monde n'est pas rendu** derrière ces écrans (opaques, `EcranOpaque`) : c'était l'essentiel du coût.
- **Animé** (un blit par effet, atlas `PersoSprites` construit une fois) : ~40 lucioles sur trois profondeurs
  (dérive, scintillement, reflets, traînées), braises de chakra à la couleur du village.
- **Personnage principal** : le joueur LOCAL à qui l'on prête la texture composée (jamais d'entité factice),
  8 GUI/texel en sélection (≈ 47 % de la hauteur), 7,5 en création. **Personnages secondaires** (voisins, vignettes
  de tenues et de coiffures, vues du dossier, reflets) : « poupée » (`PersoPoupee`) **rendue une fois** en logiciel à
  la résolution de l'écran (z-buffer, seconde couche, mêmes lumières que le rendu d'entité en interface) dans une
  texture dynamique, puis affichée en un blit ; recalculée seulement si le skin, l'angle ou l'échelle change
  (cache `PersoSkins.poupee`). Vignettes : une composition de skin par apparence, pas par image.
- **Emblèmes** (villages, clans, kamon du dais) : construits au lancement depuis `gui/esc/village_*.png` et
  `gui/clans/clan_*.png` (`PersoEmblemes`) — plus aucun emblème cuit dans les textures générées (d'anciens logos
  s'affichaient après la mise à jour des blasons).
- **Couleur libre** (cheveux, yeux) : pastille « pipette » après la palette du clan → sélecteur laque/or (carré
  saturation × luminosité, barre de teinte, aperçu nouvelle/avant, code hex au clavier), aperçu immédiat sur le
  personnage. Le serveur stocke l'apparence telle quelle (`ShinobiCharacter.appearance`) : aucune validation de
  couleur à adapter.
- **Banc** : `REBORN_PERSO_DEBUG=1|vite` (captures), `REBORN_PERSO_DEBUG=perf` (FPS, temps d'extraction, nombre
  d'éléments GUI par écran → `run/perso_perf.txt`).
- **Création** : fil de 8 sceaux numérotés 1 à 8 (voir « Sans kanji » au §3), registre qui se remplit, emblèmes de clan (`gui/clans/clan_<slug>.png`) dans la
  grille, gravés sur le dais, dans le registre et le dossier ; taille ADULTE au cm (153–207, pas de 1 cm), taille
  actuelle selon l'âge, toise ; apparence en vignettes directes (peau, coiffures en têtes, palette de couleurs et
  d'yeux **par clan**, tenues en corps entiers, « au hasard »), plus aucune flèche ; nom 3 à 20 lettres
  (espaces, apostrophe), « Nom libre » ; plaque de nom au-dessus de la tête.
- **Carrure fine pour tous** (18/10, « mets aussi le skin homme en Alex… supprime le Steve ») : Homme comme Femme sont
  en **modèle Alex (bras de 3 px)** partout — création, sélection, en jeu chez soi et chez les autres, corps au sol,
  poupées et vignettes, main à la première personne, bout de bras du Test de la Feuille. La rangée **Carrure**
  (Large / Fine) a disparu de la création (et du créateur de repli : boutons Classique / Alex, touche S). Les persos
  déjà créés passent en fine **sans rien refaire** (la carrure enregistrée est ignorée). Les gabarits de peau ont été
  redessinés au bras de 3 px ; un calque encore peint en 4 px est converti à la volée — règle pour le staff :
  [`../technique/CREATEUR_PERSONNAGE_ASSETS.md`](../technique/CREATEUR_PERSONNAGE_ASSETS.md) « Bras fins pour tous ».
  Le **skin Minecraft personnel** (« Mon skin ») garde le modèle choisi chez Mojang.
- **Sons** (2026-10-13) : **pas de musique**, des **ambiances** de nuit au bord de l'étang faites de sons vanilla en
  petites touches aléatoires (catégorie Ambiance, `PersoSons`) : clapotis (`block.water.ambient`), insectes
  (`block.firefly_bush.idle`), vent (`block.dry_grass.ambient`), grenouilles (`entity.frog.ambient`) ; sable à Suna,
  pluie à Ame, orage lointain à Kumo. Plus d'indicateur de piste. **Voix off désactivée** (et son indicateur retiré) :
  le mécanisme `reborn:voix.creation_<étape>` reste, réactivable par `-Dreborn.persoVoix=true` pour un vrai
  enregistrement.
- **Taille envoyée** : `size` à 4 décimales (`cm / 180`), le serveur lit un double.
- **Échelle** : ces écrans exigent 840 × 470 GUI (`MenuScale.Plancher`) : en 1366 × 768 ils passent à l'échelle 1
  (mise en page complète, plus petite) au lieu d'un mode compact.

## 3. La création en 8 étapes

1. **Village** (6 : Konoha, Suna, Kiri, Kumo, Iwa, Ame — **Déserteur retiré**). Emblèmes officiels ; Ame a un
   emblème dessiné pour l'occasion (4 traits de pluie).
2. **Clan** (11 entrées au total, dont Neutre et Autre ; verrouillages par village et par sexe, candidature
   requise pour certains clans sauf staff).
3. **Sexe**.
4. **Nom** (déclic sonore à chaque lettre).
5. **Âge**.
6. **Taille**.
7. **Apparence** — « À quoi ressembles-tu », façon Ketto mais dans la DA Reborn : catégories à gauche, **rampe
   de couleur de peau**, **vignettes** de coiffures et de tenues composées sur le personnage, **pastilles +
   barres teinte / saturation / lumière** pour les cheveux et les yeux.
8. **Récapitulatif** → « Oui, c'est moi ».

- **Ouverture** : directement sur la scène, avec les phrases « Avant le nom… / avant le sang… / il n'y avait que le
  chakra. » et « APPUIE SUR UNE TOUCHE » qui clignote en bas. (L'écran titre « REBORN » intermédiaire a été retiré.)
- Même message serveur et mêmes règles que l'ancien créateur.
- **Police** : celle des menus de base (celle des noms de village et des questions), à garder.
- **Sans kanji** (18/10, règle DA du 14/10, non publié) : le fil d'étapes porte le **numéro** de l'étape (ArcadePix,
  mêmes trois états : apposé vermillon, en cours laqué rouge, à venir éteint) ; les bandeaux des planches (Village,
  Clan, Sexe, Âge, Taille, Apparence, Tenue, Dossier, Ton registre) et la plaque « Nom et prénom » n'ont plus que leur
  titre ; le registre du récapitulatif garde son **sceau**, désormais à l'**emblème du village** (voir « Sceau du
  registre » ci-dessous) ; les tuiles Homme /
  Femme n'ont plus que leur libellé ; les boutons-plaques (Retour, Suivant, Entrer en jeu, Créer, Oui c'est moi, Au
  hasard, Large, Fine, et « Valider » du sélecteur de couleur) sont des plaques nues au **libellé centré**, mêmes
  largeurs (aucun écran décalé). Textures : `tools/gen_personnage_textures.py ui` (`sceaux`, `panneau_*`,
  `plaque_nom`, `boutons`). Captures avant / après : `Maquettes/gameplan/creation_sans_kanji/`.
- **Blasons dessinés** (18/10, non publiés) : les 6 clans dont le blason était de l'écriture ont un **emblème dessiné**,
  sans aucun caractère, au format des autres (`gui/clans/clan_<slug>.png`, 48×48 blanc, trait 2,8 px ; dessins dans
  `tools/gen_clan_emblemes.py`, section « dessins ») — vérifié sur Narutopedia, **aucun n'a de blason canon** :

  | Clan | Avant | Après | Source |
  |---|---|---|---|
  | Hōki (Suna) | 医 cerclé (Unités médicales) | deux feuilles en coupe et une goutte (clan de médecins) | création : la « Famille Hōki » n'existe que dans le roman *Gaara Hiden*, sans symbole |
  | Hoshigaki (Kiri) | 鮫 cerclé | aileron de requin sur deux vagues (Kisame) | création : la page du clan n'a que le symbole de Kiri |
  | Terumi (Kiri) | 水 cerclé | goutte à demi pleine, bulles : l'eau qui bout (eau, vapeur, lave de Mei) | création : pas de page de clan |
  | Ōnoki (Iwa) | 土 cerclé | montagne enneigée, petit pic en contour, ligne de sol | création : lignée du Tsuchikage sans symbole |
  | Sabaku (Suna) | 風 cerclé | gourde de sable de Gaara, cordon et sangle | création : « Clan du Kazekage » sans symbole (manga, roman) |
  | Kamizuru (Iwa) | nom 上水流 en sigillaire | abeille vue de dessus (clan des abeilles) | création : le `Kamizuru_Symbol.svg` de Narutopedia est un **placeholder de fan** (ShounenSuki), pas un canon |

  **Kurama** n'a pas changé : son blason est le **canon de l'anime** (`Kurama_Symbol.svg`, Narutopedia, ShounenSuki,
  CC BY-SA 3.0), un dessin sans écriture — c'est Kamizuru que l'inventaire du 18/10 décrivait comme « sceau stylisé ».
  Les blasons ne sont lus que par les écrans de création / sélection (grille, dais, registre, dossier) ; rien côté
  serveur, la fiche affiche un hanko générique. Planche : `Maquettes/gameplan/clans_sceau/planche_blasons_tous.png`.
- **Sceau du registre** (18/10, non publié ; « c'était bien ») : le tampon vermillon du récapitulatif revient au même
  endroit (centre `xD + 188, y0 + 222`), **penché de 5°**, encre inégale, bords un peu mangés, filet réservé — avec
  l'**emblème du village** choisi en réserve couleur papier au lieu de 認. Base `personnage/sceau_registre.png`
  (`tools/gen_personnage_textures.py sceau` : tampon sans marque, variante 認, masque d'usure) ; emblème composé au
  lancement par `PersoEmblemes.sceaux()` depuis les vraies textures (jamais cuit), à la demi-texel GUI (40 × 40 GUI).
  Variantes : `-Dreborn.sceauRegistre=clan` (emblème du clan), `=kanji` (l'ancien 認). Banc :
  `REBORN_PERSO_DEBUG=clans` → `perso_12_clans_*`, `perso_13_sceau_*` ; captures avant / après et planche :
  `Maquettes/gameplan/clans_sceau/`. L'ancien créateur de repli reste sans tampon.
- Repli sur les anciens écrans : `-Dreborn.classicCharacterScreens=true` (non testé).

## 4. L'apparence et les skins

- L'apparence est une chaîne `SkinSpec.serialize()` stockée sur le personnage. Le serveur la diffuse à tous
  (`reborn:skins`) ; chaque client **compose** la texture 64×64 (peau → sous-vêtement → tenue → yeux → pilosité →
  cheveux) et l'applique au joueur.
- Les **corps laissés au sol** après une mort RP portent aussi l'apparence Reborn (mannequin habillé par le mod).
- Catalogue d'assets data-driven (`creator-assets/`, rechargé par `/creator reload`) — voir
  [`../technique/CREATEUR_PERSONNAGE_ASSETS.md`](../technique/CREATEUR_PERSONNAGE_ASSETS.md).
- Assets disponibles : peau (rampe), sous-vêtements, ~5 tenues (Uchiha), coiffures, yeux. Assets wave 1 du Sprint 1
  (3 clans complets) **pas terminée** : seulement les tenues Uchiha.

## 4 bis. Sourcils et coiffures live (18/10, non publiés)

**Sourcils.** Avant : six pixels sombres cuits dans le gabarit de peau (`skin/<genre>/4.png`, ligne 12) repeints d'un
aplat `browColor` qu'aucun écran « au bord de l'étang » ne proposait (toujours brun foncé). Maintenant :

- **Calque à part** `character/brows/Sourcils_Base.png` (niveaux de gris, mêmes pixels ; `tools/gen_sourcils.py`),
  catégorie `brows` du `catalog.json`, teint comme les cheveux (`tint: all`) ; le compositeur repeint les pixels cuits en
  peau et pose le calque après les yeux, avant pilosité et cheveux (une frange peut le couvrir).
- **Choix** : étape Apparence › rangée COULEUR › onglet **SOURCILS** (à côté de CHEVEUX) : pastille « = » **comme les
  cheveux** (défaut), la palette du clan, la pipette (même sélecteur de couleur libre ; Échap rend l'état d'avant).
- **Stockage** : dans l'apparence (`SkinSpec`, blob opaque côté ShinobiCore, aucun changement serveur) ; nouvelle dernière
  ligne `browFollowHair` (« 1 » = comme les cheveux, la couleur suit aussi une teinture ultérieure) et `browColor` porte
  la couleur effective (un client plus ancien affiche la même). Apparence antérieure sans cette ligne : sourcils restés à
  la couleur par défaut → comme les cheveux ; couleur choisie (ancien créateur) → gardée.
- Pas de réglage staff ni chirurgie des sourcils (le Poste de garde ne touche pas à l'apparence ; la chirurgie ne
  concerne que les yeux).

**Coiffures live du serveur.** Le staff alimente `plugins/ShinobiCore/creator-assets/` (catalog.json + `hair/*.png`,
`/creator reload`) : 59 coupes au catalogue le 18/10, dont **58 diffusées** (`coupe15` : PNG absent, ignorée par le
serveur). La diffusion (`reborn:creatorpack`, ~2 s après la connexion) et l'injection dans le catalogue du mod n'avaient
pas changé, mais la création refaite le 13/10 les rendait introuvables : liste = 35 coupes du mod **puis** les coupes du
serveur, quatre vignettes par rangée et deux rangées visibles (une seule en 1366 × 768) → il fallait 9 crans de molette
(23 en petit écran) pour en voir une. Et un skin composé avant l'arrivée du pack (personnages de la sélection, autres
joueurs diffusés au join) restait **sans la coupe** jusqu'à sa prochaine mise à jour. Corrigé :

- coupes du serveur **en tête** de la liste, puis celles du mod ; bouton **« Tout voir (n) »** : grille de toutes les
  coupes (9 × 6, molette), losange sur celles du serveur, nom au survol ;
- choix gardé par **id** (la liste peut grandir écran ouvert) ; vignettes, aperçus et skins des écrans perso refaits à
  chaque asset reçu (génération du catalogue) ; skins des joueurs **recomposés** quand l'asset qu'ils portent arrive ;
- filtres du catalogue respectés : les coupes du serveur n'ont pas de `gender` (proposées à tous) ; côté mod, 12 coupes
  sont réservées aux femmes et 4 aux hommes (tags posés à la génération du 13/10, sans décision : à trancher) → un
  homme voit 81 coupes, une femme 89. Pour réserver une coupe du serveur : `"gender": "female"` dans son entrée.
- Banc : `REBORN_PERSO_DEBUG=coupes` (+ `REBORN_CREATOR_ASSETS=<copie locale de creator-assets>` : rejoue la diffusion
  du serveur octet pour octet) → `run/screenshots/perso_10*` (grille, arrivée écran ouvert, coupe portée, 1366 × 768),
  `perso_11*` (sourcils) ; copies `Maquettes/gameplan/perso_sourcils_coupes/`.

## 5. Pièges connus

- **Pas d'entités factices hors monde dans un écran** : un `RemotePlayer` jamais ajouté au monde fait planter le
  rendu (« Tried to access entity ID before ID assignment »), avalé par Minecraft → **gel au join**. Les scènes
  utilisent des mannequins ajoutés au monde client ; les anciens écrans prêtaient le skin au joueur local.
- Les mannequins ne plient pas le coude : impossible de croiser les bras pour l'instant.
- Écrans du mod en 26.2 : voir [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md).

## 6. Reste à faire

| Sujet | État |
|---|---|
| **Palettes par clan** (couleurs de cheveux / peau / yeux définies par clan, « sinon trop de fantaisiste ») | demandé, à faire |
| Vignettes de coiffures trop petites | à agrandir |
| Hétérochromie, pilosité, zones de couleur des tenues | absents de l'étape Apparence (sourcils et accessoires faits) |
| Assets wave 1 : 3 clans starter complets (Uchiha, Hyuga, Neutre) | 5 tenues Uchiha seulement |
| Décors 3D éventuels (au lieu du vide sombre) | idée, non tranchée |
| Blasons de clan faits d'écriture (Hōki, Hoshigaki, Terumi, Ōnoki, Sabaku, Kamizuru) | **fait 18/10** (non publié) : emblèmes dessinés, §3 « Blasons dessinés » ; à valider en jeu |
| Sceau du registre : marque par défaut (village / clan / 認) | **village** câblé 18/10 ; variantes `-Dreborn.sceauRegistre=clan` ou `=kanji` — choix de l'utilisateur |

## 7. Retours joueurs (2026-10-13) — à traiter dans la refonte au niveau « fiche shinobi »

Retour de **Dadil** (testeur) :

| Écran | Retour | Piste |
|---|---|---|
| Sélection | Fond noir, étoiles à un seul endroit ; « vide » | Habiller la scène : plans de fond (ciel, silhouette du village, brume), lucioles et braises réparties, sol |
| Sélection | Personnage trop petit | Agrandir le perso (cadrage caméra plus serré) |
| Sélection | Pas de musique | Ambiance musicale douce (piste OST existante ou nouvelle) |
| Sélection | Police (avis subjectif) | À revoir avec la typo de la fiche shinobi (ArcadePix titres + Inter) |
| Création | Une voix en plus du texte affiché (« avant le nom… ») | Voix off sur les questions de l'étape (fichiers audio à enregistrer ou générer) |
| Création | Personnage trop petit | Même correctif que la sélection |
| Création | Changer de village ne change pas le fond | Teinte / décor du fond selon le village (priorité tertiaire) |
| Création | Pas de logo de clan | Emblème du clan à l'étape Clan et sur le récapitulatif |
| Création | Taille : pas de 1 cm | Pas de 1 cm sur le réglage de taille |
| Création | « À quoi ressembles-tu » pas intuitif (flèches) | Refaire la personnalisation du skin : choix visuels directs (vignettes cliquables, aperçu immédiat), moins de flèches |

L'utilisateur demande aussi de **faire mieux** sur les lucioles et l'habillage général de ces menus, au niveau
des maquettes de la fiche shinobi (cf. `../design/DIRECTION_ARTISTIQUE.md` §4.1).
