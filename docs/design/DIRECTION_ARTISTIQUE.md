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

## 5. Méthode de travail sur un visuel

1. **Maquettes d'abord**, souvent plusieurs séries (« fais-en d'autres ») avant un choix.
2. Code, puis **captures réelles dans le client de dev** (banc `REBORN_*`, cf.
   [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5) **montrées avant toute publication**.
3. Corriger ce qui est visiblement raté **avant** de montrer (chevauchements, textes qui dépassent, glyphes
   manquants).
4. Garder le **fonctionnement existant** quand il convient ; seul l'habillage change.
