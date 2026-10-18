# Direction artistique — les règles

> **Ce document rassemble les règles visuelles données par l'utilisateur**, écran après écran, de juin à
> octobre 2026. Il sert à ne pas reproposer ce qui a déjà été rejeté. Palette et grilles techniques :
> [`REBORN_ASEPRITE_PALETTE.md`](./REBORN_ASEPRITE_PALETTE.md). Modèles 3D et textures :
> [`MODELES_3D.md`](./MODELES_3D.md).
>
> L'utilisateur est **l'auteur du style** du serveur. Il est très exigeant sur la DA : « c'est quelque chose de
> primordial ».

---

## 1. L'identité commune

- **Thème Akatsuki** : laque noire et rouge profond (`#A0182B`), or (`#D9A95E`), crème, bois, washi (papier),
  ciel de nuit sur un village. Palette du code : `menu/Colors.java`.
- **Le kit** : plaque laquée **suspendue** par des cordelettes et bordée d'or, **onglets pendus** sous la plaque,
  parchemin, bois veiné et ferrures, sceaux à picto (plus de kanji, §3). Police **réduite**, contenu **centré**, écrans **aérés**.
- **Chaque écran a sa signature** : un **objet** posé dans le décor commun (lanternes = fiche de stats, inrō =
  sacoche, tokonoma = Échap…). *Le Poste de garde et l'Infirmerie ont quitté le kit (v3 du 14/10, §4.2) : volet
  sobre nuit / or, sans plaquettes fuda ni tanzaku.* **On ne réutilise pas la
  signature d'un autre écran** (« les lampes c'est spécialement pour les stats »).
- **Sons et animations dédiés** à chaque écran (ouverture, survol, validation, ambiance).
- **Vrais logos** (emblèmes de villages, réseaux sociaux), jamais de dessins provisoires.

## 2. Les fonds

- Écrans utilisés **en jeu** (sacoche, éditeur de HUD, viseur, animations, OST…) : **le jeu reste visible
  derrière**, sous un voile.
- **Création et sélection de personnage** : **jamais le monde du jeu**. Aujourd'hui, un vide sombre avec des
  braises de chakra à la couleur du village ; les décors de blocs construits ont été jugés « dégueulasses ».
- Pas uniquement des thèmes de nuit.

## 3. Ce qui a été rejeté (à ne pas refaire)

| Rejeté | Remarque de l'utilisateur | Retenu à la place |
|---|---|---|
| Écrans au rendu « vanilla » | « trop vanilla visuellement » | Habillage DA complet |
| Effets « RPG » : voile rouge sur les bords, « -28 » à côté de la vie, traînée de vie perdue, trait directionnel | « ça fait très RPG » | Vitals d'origine inchangés |
| HUD trop gros | « entache un peu l'écran » | Version discrète (ensō réduit d'un tiers, chiffres à la taille du texte) |
| Refonte des vitals (cadre bois, ruban, version sobre) | « vachement moche », « trop clean » | **Le HUD vitals de base ne change pas** |
| Cercles pixelisés, en pointillés | « très pixelisé… surdosé et sale » | Tracés lissés au pixel réel, ou particules / braises |
| Plaques de la concentration du chakra | « très très très moche », « vraiment moche » | À refaire (ensō ou trait, non tranché) |
| Style HD « moins pixel » pour les écrans en jeu | rejet | **Pixel art détaillé** en jeu |
| Fonds animés lourds (GIF de ciel, constellations) | « ça sera laggy » | Décors fixes avec de petits détails animés |
| Menus aux échelles 3 et 4 | « trop gros » | Taille de l'échelle 2 en 1080p |
| Maquettes 2D sur fond illustré pour la sélection | « pas immersif » | Scène 3D dans un vide sombre |
| Grand logo de village, textes descriptifs en création | — | Retirés |
| Onglet vedette Infirmerie à droite du Poste de garde | « trop gros… pas si intuitif » | Colonne de navigation à gauche |
| Icônes de chat dessinées à la main | « gros problème au niveau des icônes » | Icônes Lucide (licence ISC) |
| Refonte de l'accueil du launcher en tableau de bord | « pas besoin » | Accueil inchangé |
| Écrans des menus principal et de chargement proposés | « j'aime pas » | Non intégrés |
| Kanji et sceaux décoratifs (titres, onglets, lignes de HUD, pièces de section) — 14/10, écran de la radio | « arrête avec les écritures chinoises/japonaises, y'a pas besoin et ça fait ringard » | **Aucun kanji décoratif** ; texte français, écrans **centrés, épurés**, peu d'éléments |

Application de la règle « aucun kanji décoratif » (précisée le 18/10, création de personnage) : un repère d'étape
porte un **numéro** (ArcadePix) au lieu d'un caractère ; un bandeau de titre n'a que son titre ; un bouton-plaque est
une plaque nue au libellé centré ; un choix déjà nommé (Homme / Femme) n'a pas besoin de signe ; un tampon décoratif
est retiré plutôt que remplacé. Les **blasons canoniques** (villages, clans, natures) restent des symboles ; les
blasons de clan faits d'un simple kanji faute de canon sont à trancher (PERSONNAGES §6).

Étendue au reste du mod le 18/10 (même jour) : **onglet pendu = plaquette à picto pixel art** (comme la sacoche :
fiche, réglages ; pictos communs dans `tools/pictos.py`) ; **plaque de fermeture = croix** ; **sceau à sens = picto**
dans la laque (blessures, résultat de la chirurgie, accès « membres » au cadenas, cible du menu d'interaction, « à toi » /
« porté » de l'échoppe) ; **signe dans une séquence = losange** (Volonté) ou **numéro** (étapes de la chirurgie) ; état
d'un joueur sur sa plaque de nom = **mot** ; signes des mains = **noms français** ; tampon de coin de planche, sceau de
titre, kanji de grade ou de mérite = **retirés**, le texte se recale. Aucun caractère chinois ou japonais n'est plus
posé par le code ni cuit par les générateurs (hors blasons canoniques). Restent, comme symboles : les blasons de clan
(dont les 6 faits d'un kanji, à trancher), les symboles canoniques des natures, les tours des Kage peintes dans les
anciens fonds de création.

## 4. Ce qui a plu (références de style)

- Fiche de stats « lanternes célestes » v2, menu Échap Tokonoma, sacoche inrō, carte avec intro en nuages,
  mode photo façon viseur, Poste de garde v2 (bois et ferrures), chat en panneau DA, sélection en scène.
- Références externes fournies : Ketto RP (sélection et création), Soulbound 2.0, Nanos World (chat), panels
  de modération GTA RP, Zenkai / Shinoda (concurrents).

### 4.1 La référence de rendu : la fiche shinobi « portrait en scène » v2 (2026-10-13)

Jugée « très très belle… vraiment bien travaillée visuellement ». **C'est le niveau visé pour tous les menus.**
Maquettes hors dépôt : `Maquettes/fiche-shinobi/v2/portrait/`. Le style est codé dans la **boîte à outils de
style** `minecraft/mod-hud/tools/style/` (Python + Pillow + numpy) :

- `kit.py` : rampes de couleurs à ombres pourpres, tramage Bayer 4×4 aligné sur la grille, laque urushi avec
  reflet tramé, liseré or biseauté, ferrures kanagu dessinées à la main, panneaux laqués, cordelettes kumihimo,
  nœuds agemaki, glands, plaques suspendues, onglets pendus, washi (fibres, bords déchirés), bois veiné,
  bandes de tissu, sceaux hanko, tampons, boutons-plaques (normal / survol / actif / inactif), lanternes ;
  ombres en paliers de 1 px (jamais de flou baveux) ; texte ArcadePix au pixel, Inter net à la résolution écran.
- `perso.py` : le vrai personnage Reborn composé depuis les couches de skin du mod (face, 3/4, buste).
- `demo.py` : planches de démonstration.

Tout nouveau menu se maquette **avec ce kit**, puis ses parties fixes deviennent des textures générées par
script (cf. §6).

### 4.2 Écrans remis au niveau de la fiche (13/10, hud 0.4.170 → 0.4.173)

Fiche shinobi, sélection et création de personnage, chirurgie des yeux, scène de mort, menu d'interaction,
concentration du chakra (ensō A retenu par défaut), bibliothèque / lecture / réglages staff, Poste de garde,
Infirmerie, sacoche, chat ouvert, Échap, échoppe, réglages, carte, tablist. Chaque écran a son générateur
`minecraft/mod-hud/tools/gen_<ecran>_textures.py` (pièces communes : `tools/ecrans_art.py`, `fiche_art.py`,
`style/kit.py`) et passe par `ui/DaKit.java`. **Signatures ajoutées (à valider)** : paravent **byōbu** = Réglages,
**registre 名** = Tablist, **pierres de gué sur l'étang** = sélection et création, **fiole** = prélèvement d'œil.
Finitions faites le 13/10 : réglages du chat, logo de l'Échap en pixel art (`echap/logo.png`), icônes réduites au lieu d'être rognées (`IconTextures`).

**Lot « écrans en jeu restants » (13/10, après la 0.4.174 — non publié)**, en version épurée (retour du 13/10 :
« faut que ce soit subtil… pas de gros trucs ») : éditeur de HUD et son aide, viseur, caméra, mouvements (emotes /
démarches), mode photo, galerie (vue détail, retouche, partage), signalement et déconnexion (depuis l'Échap), test de
la Feuille (confirmation et résultat), repositionnement des cosmétiques ; HUD rendus plus discrets : KO / ATA (plus de
plaque ni de bandeau épais), Volonté, créneaux de recharge, activation du Byakugan atténuée. Générateurs
`tools/gen_{editeur,viseur,camera,animations,photo,galerie,signalement,deconnexion,tirage,cosmetique,combat}_textures.py`
(pièces communes du lot : `tools/jeu_art.py`). **Signatures ajoutées (à valider)** :

| Écran | Signature |
|---|---|
| Éditeur de HUD (+ aide) | l'**équerre et le cordeau à encre** du charpentier (sashigane, sumitsubo), règle graduée d'or en filet ; l'ancien « byōbu » du panneau est retiré (signature des Réglages) |
| Viseur | le **mato de kyūdō** sur sa butte de sable, flèches fichées ; l'aperçu se dessine au centre de la cible |
| Caméra | le **miroir de bronze** (dōkyō) où se reflète la vue de dessus |
| Mouvements | l'**éventail** (sensu) entrouvert sur la planche |
| Mode photo | le **viseur** (conservé, il avait plu) : équerres d'angle, déclencheur vermillon cerclé d'or, sceau 撮 |
| Galerie | les **tirages pincés sur des fils** (conservés) ; pellicule dans la vue détail |
| Signaler | la **boîte à doléances** (meyasubako, plaquette 訴) |
| Se déconnecter | le **chapeau de voyageur** (sugegasa) : se déconnecter, c'est prendre la route |
| Test de la Feuille | la **feuille de papier à chakra** sur son sanbō ; au résultat, la feuille réagie (brûlée, mouillée, froissée, fendue, effritée) — *refait le 14/10, voir plus bas* |
| Cosmétiques | la **pelote à épingles** du tailleur (harisashi) |


**Poste de garde clarifié (14/10, non publié)** — retour : « brouillon, pas facile de s'y retrouver ; bien espacer sans
que ça dépasse / s'emboîte ». Identité gardée (cadre de bois, washi, plaquettes fuda, sceau 罰, 番 / 医), structure
refaite :

- **Navigation en groupes nommés** : 番 Garde (Alertes, Joueurs, Journal) · 令 Outils (Annonces, Chat staff,
  Commandes) · 医 Infirmerie ; entrée active en laque rouge ; bascule **Mode staff** en bas de la colonne.
- **Même ossature pour chaque vue** : en-tête fixe (titre ArcadePix ou **fil d'Ariane** « JOUEURS ▸ N° 00042 · nom »
  + « < Retour », compteur à droite, filet), zone d'outils fixe (recherche, puces, carte d'identité, en-têtes de
  colonnes), corps qui défile avec **barre de défilement**, pied fixe (saisie du chat, pagination).
- **Grille** : marges 8, gouttières 6, lignes espacées de 4 ; colonnes calculées depuis la largeur (683×384 à
  960×540) ; tout texte trop long est **coupé par « … » avec info-bulle** (bande d'encre bordée d'or).
- **Boutons** : grands boutons d'action 16 GUI (Inter gras 8, texture `garde/boutons16.png`) pour les actions,
  petits 12 GUI (ArcadePix) pour les puces et les lignes denses ; bouton éteint = raison au clic.
- **Fiche joueur** : carte d'identité fixe (N°, grade, nom, compte, pastilles gelé / écran noir / étiquettes, six
  repères) + sous-onglets Actions · Sanctions · Notes · Comptes liés · Journal ›. Actions en **panneaux par famille**
  (Déplacement, Surveillance, Soins, Mise en scène, Personnage en liseré rouge) ; paramètres dans une **fenêtre de
  formulaire** ; destructif = **retaper le N°**.
- **Journal universel** : puces de catégories à pastille de couleur **sobre** (bleu-vert, brun, ambre, laque,
  lie-de-vin, vert, violet, ardoise, encre, gris), période, texte, N° ; lignes alternées à liseré de couleur ; clic =
  fenêtre de détail.
- **États vides explicites** (une phrase + une aide plus petite), grande carte de washi `garde/feuille.png` pour les
  formulaires pleine largeur.
- **HUD en jeu (règle 9)** : annonce = une ligne fine en haut (minuscule sceau 告 global / 伝 perso, titre en petites
  capitales d'or, filet d'or d'1 px qui se résorbe) ; écran noir = fondu, texte centré Inter 9 crème + filet ; séisme =
  légère secousse de caméra (réglage vanilla « Effets de distorsion ») ; plaques staff = une ligne Inter 7 sur voile
  léger « 00042 · nom · état » + jauge de PV d'1 px. Captures : `Maquettes/gameplan/04_staff/avant|apres/`.

**Poste de garde v3 — direction A « le volet » (14/10, non publié)** — remplace le Poste de garde clarifié ci-dessus
(cadre de bois plein écran). Décision : « J'aime beaucoup la fiche menu A, c'est bien comme ça ; garde le A joueurs
mais, lors de la recherche, la fonctionnalité B ; le journal A est bien aussi. » Maquettes validées :
`Maquettes/gameplan/04_staff/v3/A_01` à `A_06` (+ `B_01` pour la recherche) ; captures du code :
`Maquettes/gameplan/04_staff/v3/code/`.

- **Volet** de 202 GUI (404 px en 1080p) à 8 GUI du bord droit, fond nuit à 92 %, filet de 1 px écran, filet d'or en
  haut, ombre en paliers ; **le jeu reste rendu et visible** (aucun voile, aucun flou ; le HUD n'est pas masqué). Hauteur
  pleine pour les listes, ajustée au contenu pour la fiche et les alertes ; glissement d'ouverture de 160 ms.
- **En-tête** : « POSTE DE GARDE » en ArcadePix or, puce « Mode staff » (verte) / « Animation » (violette) cliquable,
  croix. **Onglets** Alertes · Joueurs · Journal · Outils : icône au trait + mot (Inter 13 px), compteur laqué,
  soulignement d'or de l'onglet actif.
- **Icônes au trait** façon Lucide (ISC), redessinées par `tools/gen_garde_icones.py` dans `garde/icones.png`, **à
  chaque taille en pixels écran** (5 à 10 GUI × échelles 1 à 4) et posées au pixel écran : nettes à toutes les
  échelles, teintées au rendu. **Aucun kanji, aucun sceau** (§3).
- **Typographie** : Inter net à la résolution écran (11 à 18 px de maquette), y compris **dans les champs** (la saisie
  vanilla est masquée et redessinée en Inter) ; petites capitales espacées pour les en-têtes de section.
- **Mots plutôt que boutons** : filtres et actions sont des mots (actif = gras crème souligné d'or ; action principale
  en or) ; une seule plaque laquée fine pour valider (« Écouter · Entrée ») ; « Annuler » en mot.
- **Liste** : lignes de 23 GUI, visage, nom RP, « N° · pseudo · grade », état à pastille à droite, marques en
  dessous ; survol = fond plus clair + liseré d'or + 4 icônes d'action carrées et leur bulle (« Geler  G »).
- **Fiche compacte** (A_02) : 8 actions éclair en grille 4 × 2 (icône 10 GUI + mot), menu « Plus » en panneau à gauche
  du volet (raccourcis en touches, actions Admin grisées au cadenas), sections repliées à aperçu d'une ligne ; les
  formulaires s'ouvrent **dans le volet** (A_02b).
- **Journal** (A_03) : jetons de filtre à croix, « + Filtre » en or, entrées groupées par jour, entrée choisie dépliée.
- **Alertes** (A_04) : liseré et petites capitales à la couleur du type, actions en mots, « Ignorer » à droite, traitées
  récemment en gris.
- **Outils** : sections comme la fiche ; l'Infirmerie (zones, hôpitaux, réglages) ouvre un **volet élargi** (640 GUI
  au plus) pour la carte ; la frise des réglages n'a plus de kanji (couleur + nom + valeur).
- **Performance** (§6) : chaque région est peinte par un « pinceau » qui soumet les aplats, les filets d'1 px, les
  icônes et **tous les textes** en lots (une poignée d'éléments GUI par région) ; textes coupés mémorisés.
- HUD : la marque de l'annonce n'est plus un kanji (petit carré vermillon / or) ; indicateur discret du mode staff /
  animation en haut à droite (Inter 7, pastille).

**Natures de chakra et test de la Feuille refait (14/10, non publié)** — retours : « pour les 5 natures… fais comme pour
les clans ou les villages, prends les VRAIES SVG », « refais le truc du Test de la Feuille ».

- **Symboles de nature = les icônes canoniques de Narutopedia** (octogone au dégradé de la nature, contour et caractère
  noirs ; ShounenSuki, CC BY-SA 3.0 — `minecraft/mod-hud/tools/natures_canon/README.md`), convertis comme les blasons
  de clan (`tools/gen_natures_textures.py` : rendu ×8, encre ramenée à 2,8 px à 48, LANCZOS, alpha franchi) mais en
  **couleurs canoniques** ; 16, 32, 48 et 64 px dans `textures/gui/nature/`, posés au pixel écran par `ui/NatureIcones`.
  Comme les blasons, ce sont des **symboles canoniques**, pas des kanji décoratifs : ils remplacent les sceaux kanji de
  nature (fiche : ligne « Natures » et coin du détail d'une technique ; lecture d'un rouleau). Les icônes peintes
  Katon / Suiton / … (`gen_icones_peintes.py`) ne servent pas : chaque technique aura plus tard sa propre icône.
- **Test de la Feuille** (`menu/tirage/TirageScreen`, `tools/gen_tirage_textures.py`) : plus de panneau ; écran
  **centré, épuré**, le jeu reste visible sous un voile de nuit. Confirmation : la feuille de washi au-dessus de son
  sanbō, lueur de chakra, « TEST DE LA FEUILLE » + filet d'or, une phrase, plaque fine « Confirmer » + mot « Refuser ».
  Le joueur fait ses signes de mains en vue de profil (légende discrète en bas), puis la vue se voile et **la feuille
  réagit au centre, image par image** (16 images de 110 ms, une case d'atlas par image, un blit) : Katon elle
  s'enflamme et se consume (coin carbonisé, cendres sur le plateau), Fūton elle se fend en deux, Raiton elle crépite
  et se froisse, Doton elle se craquelle et s'effrite en poussière, Suiton elle se détrempe, s'affaisse et goutte.
  **Double nature** : la feuille se partage en bandes, une réaction par nature. Révélation : la feuille remonte, le
  **symbole canonique** (64 GUI) apparaît, le nom en ArcadePix ×2, l'élément à la couleur de la nature, une phrase,
  « Valider ». GIF et captures : `Maquettes/test_feuille/`.
- **Icônes de recharge** : combat, course de chakra (animée), garde (peintes) ; l'esquive attend. Créneaux de 16 unités
  (icône 32 px à 1:1 à l’échelle 2, élément à 1,0 ; par défaut sous les vitals en haut à gauche — l’ancien placement sortait de l’écran en 1366×768), icônes posées au pixel écran à un multiple de 16 px : plus de
  réduction au plus proche ; le créneau laqué autour est retiré (les icônes portent leur cadre).

## 5. Méthode de travail sur un visuel

1. **Maquettes d'abord**, souvent plusieurs séries (« fais-en d'autres ») avant un choix.
2. Code, puis **captures réelles dans le client de dev** (banc `REBORN_*`, cf.
   [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5) **montrées avant toute publication**.
3. Corriger ce qui est visiblement raté **avant** de montrer (chevauchements, textes qui dépassent, glyphes
   manquants).
4. Garder le **fonctionnement existant** quand il convient ; seul l'habillage change.

## 6. Performance — pixel art léger (décision du 2026-10-12)

L'utilisateur veut rester en pixel art, **sans coût de performance**. Règles d'implémentation pour tout écran ou HUD :

1. **Ce qui ne bouge pas est une texture** pré-dessinée (PNG pixel art, générée par script si besoin, cf.
   `minecraft/mod-hud/tools/gen_yeux_textures.py`) affichée en **un seul `blit`** : cadres, décors, plateaux,
   personnages, ornements. Pas de cercles ou de dégradés reconstruits ligne par ligne à chaque image.
2. **Le code ne dessine que ce qui bouge** : curseurs, jauges, marqueurs, petits effets, texte.
3. **Échelle entière** : une texture s'affiche à un multiple entier de pixels écran (texels nets, aucun
   filtrage), et tous les éléments d'un même écran partagent la même grille.
4. **Pas d'effet plein écran calculé au pixel** (flous, distorsions, particules par centaines) ni de fond
   animé lourd ; une animation = quelques éléments qui se déplacent.
5. **Calculer une fois** : mise en page, textes découpés, listes triées recalculés quand les données changent
   (nouveau paquet, redimensionnement), pas à chaque image.
6. **Atlas** quand un écran utilise beaucoup de petites images (icônes, lettres de rang) : une seule texture.
7. **Peu d'éléments GUI par image** (constat du 2026-10-13) : en 26.x, chaque `fill`, `blit` ou texte est un élément
   que le jeu compare à tous les autres de la strate — coût **quadratique**. Un tracé « au pixel réel » ou une frise
   répétée (des centaines d'aplats d'1 px ou de petits blits) se soumet en **un seul élément** avec
   `ui/LotGui` (aplats) ou `ui/LotBlit` (blits d'une même texture) : rendu identique, coût linéaire.
8. **Écran opaque = monde caché** : un menu dont le fond couvre tout l'écran implémente `ui/EcranOpaque` (le monde
   et le HUD ne sont plus calculés derrière lui) et n'appelle jamais le fond flou vanilla (`extractBackground`).
9. **HUD en jeu : subtil, épuré, petit — jamais de grosses plaques** (retour du 13/10 sur le duel et le KO : « c'est
   gros, faut que ce soit subtil »). Ce qui s'affiche pendant qu'on joue (invitation, indicateur, compte à rebours,
   carte de fin, état) est du **texte petit** (Inter 7-8, ArcadePix ×2 au plus pour un mot fort) sur un **voile nuit
   très léger** ou rien, avec au plus un minuscule sceau et un **filet d'or d'1 px** ; pas de cadre épais, pas de
   bouton-plaque : un choix cliquable est un **mot souligné au survol**. Rien ne couvre plus que nécessaire, rien de
   plus large qu'environ **1/4 de l'écran** (hors texte), fondu d'entrée et de sortie. Les plaques laquées restent
   pour les **menus** (écrans ouverts). Référence : `duel/DuelHud.java` v2.
