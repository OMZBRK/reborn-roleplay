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
  parchemin, bois veiné et ferrures, sceaux kanji. Police **réduite**, contenu **centré**, écrans **aérés**.
- **Chaque écran a sa signature** : un **objet** posé dans le décor commun (lanternes = fiche de stats, inrō =
  sacoche, tokonoma = Échap, plaquettes fuda = Poste de garde, tanzaku = Infirmerie…). **On ne réutilise pas la
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
Restent hors lot : fenêtre des réglages du chat, logo HD de l'Échap, icônes 32×32 rognées par `IconTextures.drawIcon`.

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
