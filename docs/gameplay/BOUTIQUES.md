# Boutiques — le tailleur du village (ryo) et la boutique de compte (RBCoins)

> **Statut : décidé et codé le 15/10, publié sur le dev le 15/10 (hud 0.4.181, manifest 3.1.137)** (ShinobiCore `boutique/`, reborn-hud `boutique/`) — §13. Game Plan
> n° 39 (boutique en argent réel) et n° 42 (boutique en jeu). Demande du 15/10 : « va sur l'In-Game Shop Zone dans
> l'ensemble, au lieu de l'échoppe actuelle […] on ne quitte pas le jeu, juste un menu qui s'affiche, on peut changer
> directement, on peut faire tourner le personnage, avec la bourse notée. »
>
> **Décisions du 15/10** (DECISIONS.md) : (1) « Change le nom ÉCHOPPE par BOUTIQUE tout simplement, et **ne mets RIEN
> dedans** ; je te passerai des screens de ce à quoi je veux que ça ressemble » → la carte de l'Échap s'appelle
> **BOUTIQUE** et ouvre un **écran vide** (« Boutique — bientôt ») ; **pas de RBCoins dans ce chantier** (§6-§7 restent
> une proposition, en attente des visuels de l'utilisateur). (2) « Mettre en possibilité côté staff de faire des
> **catégories** : un comptoir c'est les hauts, un autre les bas, etc. ; boutique de masques, accessoires… » → chaque
> **comptoir** vend une ou plusieurs catégories, et éventuellement une liste précise d'articles (§9, §13.3). Le reste
> du tailleur (§3-§5, §8, §10, questions 1 à 8 et 12 du §12) est pris **avec les recommandations**.
>
> **Maquettes** (hors dépôt, 1920 × 1080, sur de vraies captures du client de dev dans le monde Konoha, personnage et
> figurants rendus par le jeu, kit de style `minecraft/mod-hud/tools/style/`) : `Maquettes/gameplan/42_boutiques/` à
> côté de `RBLAUNCHER/` — `01_entree_boutique.png`, `02_essayage.png`, `03_rotation_zoom.png`, `04_achat.png`,
> `05_rbcoins.png`, `06_vip.png`, `07_staff.png` (`_maquettes_boutiques.py`, captures dans `captures/`).
>
> **Sources** : [`../GAMEPLAN.md`](../GAMEPLAN.md) §39, §42 ; la référence RedLine (*In-Game Shop Zone*, *Shop (Real
> Money Store)*, *Subscription Tab*, *Cosmetics Tab*, *Roll System*) ; [ADR 0006](../adr/0006-monnaies-ryo-et-rbcoins.md) ;
> [`MONNAIE_ET_BANQUE.md`](./MONNAIE_ET_BANQUE.md) (bourse, §12 et §15.7 RBCoins) ;
> [`INVENTAIRE_ET_COSMETIQUES.md`](./INVENTAIRE_ET_COSMETIQUES.md) §10 ; [`PERSONNAGES.md`](./PERSONNAGES.md) §5 ;
> [`ENTREPRISES.md`](./ENTREPRISES.md) ; [`NOURRITURE.md`](./NOURRITURE.md) ; [`FACTION_PROPOSITION.md`](./FACTION_PROPOSITION.md)
> §3 (tenues d'institution) ; [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md) §2, §3, §4.1, §6.

---

## 0. En dix lignes

1. **Deux boutiques, deux monnaies, rien en commun** (ADR 0006) : le **tailleur** du village vend en **ryo**, ce qu'on
   y achète est **lié au personnage** ; la **boutique de compte** vend en **RBCoins**, ce qu'on y achète est **lié au
   compte** et partagé par tous ses personnages.
2. Le tailleur est un **lieu** : une échoppe du village posée par le staff (comptoir + estrade d'essayage), où l'on va.
3. **Clic droit sur le comptoir** : le personnage monte sur l'estrade, la **caméra vient se placer en face de lui, dans
   le monde** ; un menu s'affiche par-dessus le jeu. Le monde continue : les autres clients, le tailleur, la rue.
4. On **essaie directement sur soi** (gratuit, vu de soi seul), on **fait tourner** son personnage (glisser) et on
   **zoome** (molette : en pied, buste, visage).
5. La **bourse** est affichée (ce qu'on a sur soi) ; on paie avec elle ; « Pas assez dans ta bourse — passe au guichet ».
6. **Conditions** : grade, fonction, clan, village, entreprise. Grade et fonction non remplis = **grisé avec la
   raison** ; autre clan, autre village = **caché**.
7. **L'échoppe actuelle disparaît de l'Échap** : ses tenues vont au **tailleur**, ses vivres et ses cartes au **bazar**
   (un second genre de boutique-lieu, sans essayage).
8. *(Proposition en attente des visuels de l'utilisateur — décision du 15/10 : la carte s'appelle **BOUTIQUE** et
   ouvre pour l'instant un écran vide.)* **L'Échap ouvre la boutique de compte** (RBCoins), partout : même principe de caméra en face de soi, solde de RBCoins,
   cosmétiques marqués d'une **petite icône** discrète, onglet **Abonnement**.
9. Les achats en RBCoins passent par le **serveur**, qui appelle l'**API** (signée, comme `/v1/game/candidature`) ; le
   **launcher** transmet le solde au lancement pour l'affichage (déjà lu par le mod).
10. Une **entreprise TAILLEUR** peut tenir la boutique (V1 : présence affichée ; V2 : part des ventes, catalogue,
    retouches). Tout achat est **journalisé**.

---

## 1. État des lieux (audit du code, 15/10)

### 1.1 L'échoppe d'aujourd'hui

| Élément | Code | Ce qu'il fait |
|---|---|---|
| **Écran** | `mod-hud/…/menu/widget/ShopScreen` (+ `menu/shop/EchoppeArt`, `ShopData`, `ShopPayload`) | Écran **opaque** (`EcranOpaque`, le monde n'est pas rendu) : mur de planches, noren indigo au kanji 商, chōchin 店, estrade éclairée avec le **joueur local** rendu en grand (`renderAvatar360` : `bodyRot` imposé, rotation 360° au clic droit maintenu, pas de zoom), catalogue en étiquettes de papier, onglets **TENUES / CARTES / VIVRES**, sons `reborn:shop.*` |
| **Ouverture** | `menu/esc/EscTokonoma` (carte ÉCHOPPE, `H_SHOP`) | **Depuis l'Échap, n'importe où** — pas de lieu. La carte affiche « Sur toi · N ryo » (bourse) |
| **Serveur** | `ShinobiCore/…/shop/ShopManager` (canal `reborn:shop`) | `open` → état `{ryo, price, owned[], appearance, maps[], vivres}` ; `buy:<id>` (tenue), `buymap:<id>` (carte), `buyvivre:<id>` (vivres), `equip:<id>\n<apparence>` |
| **Tenues** | catalogue du mod `CharacterCatalog` (`outfit`, **59 tenues** : 12 « Complet_* » + `tenueh1…47`) | **Prix plat** `shop.tenue-price` (**500**) ; possession = liste `ownedOutfits` **sur le personnage** (YAML) : liée au personnage, non physique, ni échangeable ni volable ; aucune condition (ni grade, ni clan) ; aperçu local sur son corps (`RebornSkins.applySpec`), « Équiper » rediffuse l'apparence |
| **Cartes** | `places.yml` (Konoha **150**) | Donne l'objet « Carte — région » ; rachetable |
| **Vivres** | `nourriture/NourritureModule.acheterVivre` | Ration sèche ×2 (**30**, tous) ; paniers d'ingrédients **10 à 40** (cuisiniers seulement) |
| **Paiement** | `ShopManager.payer` → `monnaie().bourses().payer` | Depuis la **bourse** (15/10) ; repli sur l'ancien solde sans le module monnaie |

### 1.2 Les cosmétiques et leur liaison

| | Où | Liaison |
|---|---|---|
| **Tenues** (calque du skin composé) | `SkinSpec.outfitId`, `ownedOutfits` | **Personnage** (perdue à sa mort RP : reste sur le personnage mort) |
| **Cosmétiques 3D** (10 emplacements : chapeau, bandeau, masque, boucle d'oreille, manteau, haut, tenue, ceinture, bas, dos) | `ShinobiCore/…/inventory/CosmeticSlot`, rendu `mod-hud/…/cosmetic/` (modèle Nexo par `item_model`, « Repositionner ») | **Objets de personnage** : échangeables, volables, perdus à la mort RP — **aucune boutique ne les vend aujourd'hui** |
| Tenue forcée (staff) | `staff/outils/TenuesForcees` | — |
| **Rien n'est lié au compte** en jeu | — | — |

### 1.3 Le rendu du personnage réutilisable

| Où | Méthode | Réutilisable pour |
|---|---|---|
| `ShopScreen.renderAvatar360` | joueur local rendu en interface (`ctx.entity`), `bodyRot` libre | **Repli** quand la caméra ne peut pas se placer (§3.4) |
| `menu/character/perso/PersoFigure` | joueur local avec **skin prêté** le temps de l'extraction (jamais d'entité factice : gel au join, PERSONNAGES §5) | Vignettes d'aperçu, repli |
| `PersoPoupee` | « poupée » rendue **une fois** en logiciel dans une texture, puis un blit | **Vignettes** du catalogue (la tenue sur *son* corps), comme les vignettes de tenues de la création |
| `camera/` (caméra 3ᵉ personne, presets), mode photo | caméra placée librement | **La caméra d'essayage** (§3.3) |

### 1.4 Les RBCoins

- **API** : `User.rbCoinBalance` (entier, par compte), lu par `me.service.ts` (`coins`) et affiché dans l'en-tête du
  launcher. Modèles `ShopProduct` (prix en centimes) et `Purchase` (statuts PENDING → DELIVERED) **présents mais
  inutilisés** ; le dossier `apps/api/src/shop/` est **vide** ; **aucune écriture** du solde.
- **Pont serveur → API** : `apps/api/src/game/` (`GameHmacGuard`, `/v1/game/candidature/:mcUuid`, HMAC
  `REBORN_WEBHOOK_SECRET`) — le bon chemin pour les achats en jeu.
- **Launcher** : page `/shop` = `PlaceholderPage « Boutique »` ; le jeu reçoit le play-token par fichier
  (`launcher/game.rs`), **pas** le solde.
- **Mod** : `monnaie/RbCoins` lit `-Dreborn.rbcoins` ou `-Dreborn.rbcoinsPath` ; tant que le launcher ne l'envoie pas,
  l'Échap affiche « — RBCoins » (MONNAIE §15.7).
- **VIP** : permission `reborn.premium` **dormante** (4 emplacements de personnage au lieu de 2).

---

## 2. Ce que RedLine fait, et ce qu'on garde

| RedLine | Reborn |
|---|---|
| *In-Game Shop Zone* : on **entre dans une zone**, on interagit, l'interface s'ouvre | **Gardé** : le tailleur est un lieu, comptoir posé par le staff, clic droit |
| Vêtements achetés en Berrys, liés au personnage, perdus à sa mort, non échangeables | **Gardé** pour les tenues (c'est déjà le cas : `ownedOutfits`) |
| Certains vêtements demandent un **niveau** ou le **VIP** | **Conditions** de grade, fonction, clan, village, entreprise ; **jamais de VIP** dans la boutique en ryo (séparation stricte) |
| *Real Money Store* séparé : onglet **Abonnement** (VIP, VIP+), onglet **Cosmétiques** liés au compte, partagés, **petite icône** | **Gardé**, sans aucun avantage de jeu (§6, §7) |
| VIP : boost d'XP, file prioritaire, relances, message de connexion ; VIP+ : serveur de combat privé, moins d'usure… | **Écarté** : rien qui touche à la progression, au combat ou à l'économie (EULA, ADR 0006) |
| *Roll* (sous-classes, coffres) et *Reset* (classe, points) payants | **Écarté** des RBCoins (décision n° 8 pour les jetons de réinitialisation, gagnés en jeu ou donnés par le staff) |

---

## 3. (A) Le tailleur — la boutique en jeu, en ryo

### 3.1 Le lieu — maquette 01

- Une **échoppe du village** (Konoha : « Tailleur Hōsen » dans la rue commerçante) : façade, noren, mannequins de
  vitrine, rayonnages de tissus, un **comptoir** et une **estrade d'essayage** devant un miroir. Le décor est construit
  par les bâtisseurs ; le staff ne fait que **marquer** le comptoir et l'estrade (§9).
- Un village peut avoir plusieurs tailleurs (un par quartier, un de luxe), chacun avec son **catalogue** (§4).
- **Invite discrète** quand on vise le comptoir (règle 9 : texte petit, pas de plaque) : « **Clic droit** Tailleur
  Hōsen · tenues et accessoires ». Fermé (heures RP, décision staff) : « Fermé — revenez plus tard ».
- Refusé en **KO**, **porté**, **étourdi**, en **combat** (5 s après le dernier coup), en **mode staff gelé**.

### 3.2 Entrer en essayage — maquette 02

1. Clic droit sur le comptoir → le serveur place le personnage **sur l'estrade** (téléportation courte, ≤ 8 blocs, dans
   la boutique), tourné vers le miroir. Une estrade occupée : « Quelqu'un essaie déjà — patiente » (une boutique peut en
   avoir plusieurs).
2. La **caméra glisse** (300 ms) des yeux vers le **point de vue d'essayage** posé par le staff : en face du
   personnage, à ~2,7 blocs, à hauteur de poitrine, champ de vision resserré (~55°) pour un portrait sans déformation.
3. Le HUD se masque ; le menu s'affiche **par-dessus le monde** (aucun voile plein écran, aucun chargement, aucun
   changement de monde). Les autres joueurs voient simplement le client debout sur l'estrade, dans sa tenue réelle.
4. Échap ou « Quitter » : la caméra revient aux yeux ; le personnage reste sur l'estrade. Un coup reçu ferme le menu.

### 3.3 L'écran — maquettes 02, 03, 04

Centré, épuré, **aucun kanji**, peu d'éléments ; le personnage est le centre de l'écran.

| Zone | Contenu |
|---|---|
| **Haut, au centre** | « TAILLEUR HŌSEN » (ArcadePix, or), filet d'or d'1 px ; dessous, en petit : « Konoha · Au comptoir : Hōsen » (si un tailleur joueur est là) |
| **Gauche** | Les **catégories** en mots, une icône au trait chacune : **Tenues** (le calque du skin, complet), **Hauts**, **Bas**, **Manteaux**, **Coiffes** (chapeau, bandeau), **Masques**, **Accessoires** (boucle d'oreille, dos, ceinture). Actif = gras crème souligné d'or. Pas de « Chaussures » : aucun emplacement aujourd'hui |
| **Centre** | **Le personnage, dans le monde** ; une ligne au-dessus de la tête : « Tu essaies : Yukata indigo » + mot « Retirer » ; en bas, les aides en mots : « Glisser  tourner · Molette  en pied / buste / visage · Échap  quitter » |
| **Droite** | Le **catalogue** en vignettes (la pièce **sur ton corps**, `PersoPoupee`, rendue une fois) : nom, prix en ryo ; possédée = « à toi » ; portée = « portée » ; verrouillée = grisée + cadenas ; en tête, la **bourse** : « Bourse · 1 240 ryo » (icône de la bourse) |
| **Bas du catalogue** | La pièce choisie : nom, prix, liaison (« Liée à ce personnage »), condition éventuelle ; **une** plaque fine « Acheter · 450 ryo » et le mot « Acheter et porter » ; pour une pièce possédée : « Porter » / « Ranger » |

**Rotation et zoom** (maquette 03) : glisser fait **tourner le personnage** (son corps pivote pour de vrai : les autres
le voient se regarder sous tous les angles, naturel en RP) ; la caméra reste fixe, le décor ne bouge pas. Molette :
trois cadrages (**en pied**, **buste**, **visage**) avec un glissement de la caméra ; double-clic = retour face.

**Essayage** : clic sur une vignette = la pièce passe sur le personnage **pour soi seul** (comme aujourd'hui :
`RebornSkins.applySpec` pour une tenue, rendu local du cosmétique 3D pour un accessoire). On peut cumuler (une tenue, une
coiffe, un manteau) ; « Tout retirer » revient à ce qu'on porte. L'essai est **gratuit et illimité** ; il s'efface en
quittant.

### 3.4 Repli quand la caméra ne peut pas se placer

Si le point de vue est obstrué (bloc posé après coup) ou absent, le personnage est **dessiné en interface** sur un voile
léger, au centre (la méthode `renderAvatar360` de l'échoppe actuelle, avec zoom) ; le reste de l'écran est identique.

### 3.5 Acheter — maquette 04

- **Confirmation** (petit cadre centré, une seule fois par achat) : « Acheter Yukata indigo ? » · « 450 ryo · ta bourse :
  1 240 → 790 » · en petit : « Liée à ce personnage : perdue à sa mort RP, ni échangeable ni volable » · plaque
  « Acheter et porter · Entrée », mots « Acheter » et « Annuler ».
- Le serveur revérifie tout (bourse, condition, place pour un accessoire, état), débite la bourse, ajoute la pièce,
  journalise, renvoie l'état ; notification « −450 ryo · Yukata indigo » ; son de pièces puis de tissu.
- **Tenue** → la **garde-robe du personnage** (`ownedOutfits`, non physique). **Accessoire 3D** → un **objet** remis
  dans la sacoche (barre → sac ; refusé si pas de place : « Libère une case »), avec les règles actuelles des
  cosmétiques 3D (échangeable, volable, perdu à la mort RP) — question n° 3.
- **Condition non remplie** : la vignette reste visible, grisée, cadenas ; au survol et dans le bas du catalogue :
  « **Réservé aux Chūnin** — ton grade : Genin » ; on peut **l'essayer**, pas l'acheter (plaque inactive, raison au clic).
- **Bourse insuffisante** : plaque inactive « Il te manque 210 ryo » ; mot « Où est le guichet ? » (marqueur sur la carte
  quand la carte le permettra).

### 3.6 Se changer ailleurs (proposition)

Les tenues possédées se changent aussi **hors boutique**, depuis la sacoche (onglet Cosmétique → plaque **Tenue** :
liste de la garde-robe), en **3 s**, hors combat, comme on enfile un vêtement. Sans cela, il faudrait retourner au
tailleur pour changer de tenue — question n° 4.

---

## 4. Catalogue, prix et conditions

| Gamme | Exemples | Prix proposé |
|---|---|---|
| Tenues de tous les jours | `tenueh1…47` simples | **200 à 400** |
| Belles tenues | yukata, haori, kimono de voyage | **450 à 900** |
| Cérémonie | `Complet_Ceremonie`, `Complet_Ceremonie2` | **1 200 à 2 000** |
| **Tenues d'institution** | gilet de Chūnin, tenue ANBU, tenue de médic | **600 à 1 000**, **condition** de grade ou de fonction (Faction §3) |
| Tenues de clan | `Complet_Uchiha`, `Complet_UchihaTraining` | **400 à 800**, **réservées au clan** (cachées pour les autres) |
| Accessoires 3D | bandeau, chapeau de voyage, masque de fête, ceinture | **100 à 600** |

- Remplace le prix plat de 500 ; ordre de grandeur cohérent avec MONNAIE §7 (une tenue ≈ une à deux missions).
- **Conditions** (une ou plusieurs) : `grade>=chunin`, `fonction=anbu`, `clan=uchiha`, `village=konoha`,
  `entreprise=<id>`, `sexe=femme`, `personnage=#00042` (pièce unique commandée, §8).
- **Affichage** : grade / fonction / entreprise non remplis → **grisé avec la raison** (donne envie, explique) ;
  autre clan / autre village / pièce unique d'un autre → **caché** (ni tentation absurde, ni encombrement).
- Les **prix et conditions** sont dans le catalogue du serveur (`boutiques.yml`), jamais dans le mod.

---

## 5. Ce que devient l'échoppe actuelle

| Aujourd'hui (Échap, partout) | Demain |
|---|---|
| Onglet **Tenues** | **Le tailleur** (§3), avec essayage en caméra et conditions |
| Onglet **Cartes** | **Le bazar** : le marchand général du village (comptoir posé par le staff, PNJ ou bloc), genre `bazar` |
| Onglet **Vivres** (ration sèche pour tous, paniers pour les cuisiniers) | **Le bazar** aussi (mêmes règles : panier réservé aux cuisiniers) |
| Carte ÉCHOPPE de l'Échap (« Sur toi · 1 240 ryo ») | Carte **BOUTIQUE** : « 320 RBCoins » → la boutique de compte (§6). La bourse se lit dans la sacoche |
| `ShopScreen` (écran opaque, noren 商, chōchin 店) | Retiré ; son rendu d'avatar sert de repli (§3.4) ; ses sons servent au tailleur |

- **Le bazar** = un écran simple **par-dessus le jeu** (pas de caméra, pas de personnage) : une liste centrée, la bourse,
  « Acheter · 30 ryo ». Signature propre à trouver (proposition : la **balance romaine** du marchand). Il peut être tenu
  par une entreprise `ECHOPPE` (déjà prévue dans ENTREPRISES).
- **Transition** : aucun accès en ryo depuis l'Échap. Le staff dispose de `/boutique voir <boutique>` pour tester à
  distance. Les tenues déjà achetées restent dans la garde-robe des personnages.

---

## 6. (B) La boutique de compte — RBCoins — maquette 05

> **15/10 : en attente.** « Ne mets RIEN dedans ; je te passerai des screens de ce à quoi je veux que ça ressemble. »
> La carte BOUTIQUE de l'Échap ouvre un écran vide (§13.1) ; ce qui suit reste une proposition.

### 6.1 Ouvrir

- **Depuis l'Échap**, partout : carte **BOUTIQUE** avec le solde (« 320 RBCoins », icône R) → « Entrer ».
- **Même principe que le tailleur** : la caméra vient en face du personnage **là où il se trouve** (calculée : 2,7 blocs
  devant, à hauteur de poitrine ; si c'est obstrué, derrière ; sinon, repli §3.4) ; le personnage est immobile le temps
  du menu, le monde continue. Refusé en KO, porté, en combat.

### 6.2 L'écran

Même ossature que le tailleur (catégories à gauche, personnage au centre, catalogue à droite), avec une identité propre,
**sobre** : pas de « luxe » criard. Signature : le **mizuhiki**, le nœud de cordelette d'or et de rouge qui ferme un
cadeau au Japon, en petit dans l'en-tête.

| Zone | Contenu |
|---|---|
| Haut | « BOUTIQUE » + filet d'or ; onglets en mots : **Cosmétiques** · **Abonnement** · **Mes cosmétiques** |
| Droite, en tête | Le **solde** : « 320 RBCoins » (icône R) ; le mot « Obtenir des RBCoins » → « Depuis le launcher, page Boutique » (jamais de paiement en jeu) |
| Catalogue | Vignettes comme au tailleur, prix en **RBCoins** ; chaque cosmétique de compte porte la **petite icône de compte** (un petit losange d'or de 5 px dans le coin) |
| Bas | « Lié à ton compte : porté par tous tes personnages, jamais perdu à la mort RP, ni échangeable, ni volable » ; plaque « Acheter · 600 RBCoins » ; achat = **confirmation** (somme en RBCoins, solde avant → après) |

### 6.3 Ce qu'on y vend (proposition)

- **Tenues** et **cosmétiques 3D** exclusifs (masques de fête, kimonos de cérémonie, haori brodés, accessoires de dos),
  **animations** (emotes) et **démarches**, styles de plaque de nom.
- **Jamais** : une tenue d'**institution**, de **clan**, de **grade** ou de **village** (elle ferait passer pour ce qu'on
  n'est pas), une **arme**, un **objet**, du **ryo**, des **points**, un **tirage**, une **réinitialisation** (§2).
- Les cosmétiques de compte se **portent** dans les mêmes emplacements que les autres (onglet Cosmétique de la sacoche),
  marqués de la petite icône ; ils sont **virtuels** : la fouille, le vol, la mort RP, la surcharge et l'échange les
  ignorent ; seul celui qui les porte voit l'icône (les autres voient un vêtement).

### 6.4 Le chemin des achats et du solde

```
Launcher ──(au lancement)── fichier .reborn-rbcoins + -Dreborn.rbcoinsPath ──► mod (affichage immédiat, RbCoins)
Mod ── reborn:boutique « rbc_acheter:<sku> » ──► ShinobiCore ── POST /v1/game/rbcoins/achat (HMAC) ──► API
API : débit atomique (solde ≥ prix), Purchase (provider « rbcoins », DELIVERED), cosmétique de compte, mouvement
      ──► { solde, cosmétiques } ──► ShinobiCore ──► mod (solde à jour, pièce portable, notification)
À la connexion : ShinobiCore ── GET /v1/game/compte/:mcUuid (HMAC) ──► { solde, cosmétiques, abonnement }
```

- **Le mod ne parle jamais à l'API** et ne voit jamais le JWT (sécurité du launcher, CLAUDE.md §3.1) ; le serveur fait
  foi, le launcher ne sert qu'à afficher un solde dès le menu.
- **Launcher** (train launcher) : dans `launcher/game.rs`, après le play-token, lire `coins` (`/v1/me`), l'écrire dans
  `<game_dir>/.reborn-rbcoins` et passer `-Dreborn.rbcoinsPath=<chemin>` (le mod le lit déjà).
- **API** (train web) : `ShopProduct` + `priceRbCoins`, `gameRef` (id de tenue ou de modèle Nexo), `kind`
  (COSMETIQUE, ABONNEMENT) ; nouveaux modèles **cosmétique de compte** (`userId`, `productId`, source : achat, Bug
  Bounty, abonnement, staff ; `revokedAt`) et **mouvement de RBCoins** (`delta`, solde après, motif, référence) ;
  idempotence par une clé d'achat générée par le serveur ; panel staff : produits, dons, remboursements, mouvements.
- **Mod** : canal `reborn:boutique` (tailleur, bazar et compte), `RbCoins.maj(n)` à chaque état reçu.

---

## 7. L'abonnement (VIP) — maquette 06, proposition à trancher

Un **seul niveau** pour la beta, nommé **« Soutien »** (ou VIP), mensuel, payé **en euros depuis le launcher** (Stripe,
v1.1). **Cosmétique et confort seulement** :

| Compris (proposition) | Jamais |
|---|---|
| **300 RBCoins** par mois | Boost d'XP, de ryo, de missions, de ressources |
| **Un cosmétique exclusif** par mois (gardé à vie) | Tirage, relance, réinitialisation payante |
| **Badge** « Soutien » dans le launcher et sur Discord, couleur de pseudo Discord | Serveur de combat privé, essais de techniques hors RP |
| **+1 emplacement de personnage** (3 au lieu de 2 ; `reborn.premium`) — question n° 9 | Moins de pertes à la mort, au KO, à l'usure |
| Accès anticipé aux **cosmétiques du mois** | Priorité dans la file d'attente — question n° 9 |

L'écran montre ce qui est compris, ce qui ne le sera jamais (en petit, pour la transparence), et la date de
renouvellement ; « S'abonner » renvoie au launcher.

---

## 8. L'entreprise TAILLEUR

Nouveau type d'entreprise `TAILLEUR` (ENTREPRISES §1) ; le **bazar** relève du type `ECHOPPE` déjà prévu.

| | V1 (avec la boutique) | V2 (avec la caisse d'entreprise, ENTREPRISES §4) |
|---|---|---|
| **Présence** | Un membre à 6 blocs d'un comptoir de **sa** boutique est « au comptoir » : affiché dans l'en-tête (« Au comptoir : Hōsen ») | idem |
| **Revenu** | Solde de fonction **+200 / semaine** (comme le guichetier) | **20 %** de chaque vente versés à la caisse ; le reste est un puits |
| **Catalogue** | Celui du staff | Le gérant **choisit** parmi les pièces autorisées et règle les prix dans une fourchette (−20 % / +30 %) |
| **Service** | — | **Retouches** : teinture d'une tenue (quand les tenues auront des zones de couleur, PERSONNAGES §6), **pièce unique** commandée (le staff crée l'asset, condition `personnage=#N°`) |

Sans tailleur joueur, la boutique fonctionne en **libre-service** (PNJ ou bloc). Un tailleur ne peut pas vendre de la
main à la main une tenue (elle n'est pas un objet) ; il peut vendre des **accessoires 3D** comme tout objet.

---

## 9. Lieux et outils staff — maquette 07

Données : `plugins/ShinobiCore/boutiques.yml` (boutiques, comptoirs, estrades, catalogues). Permission
`shinobicore.boutique` (Modérateur du Poste de garde et au-dessus). Noms à plusieurs mots entre guillemets ; cible =
pseudo, nom RP ou `#N°`.

| Commande | Effet |
|---|---|
| `/boutique creer <tailleur\|bazar> "<nom>" <village> [entreprise]` | Crée (catalogue par défaut du genre) |
| `/boutique comptoir ajouter\|retirer <boutique>` | Marque le bloc, le meuble Nexo ou le PNJ **visé** |
| `/boutique estrade ajouter\|retirer <boutique>` | Place du client = **là où se tient le staff, dans sa direction** ; la caméra est calculée et **montrée 10 s** (cadre fantôme) ; `camera <distance> <hauteur>` pour ajuster |
| `/boutique article ajouter <boutique> <id> <prix> [condition…]` · `retirer` · `prix` | Catalogue propre à une boutique |
| `/boutique ouvrir\|fermer <boutique>` · `info` · `liste [village]` · `supprimer <boutique> confirmer` | Gestion |
| `/boutique voir <boutique>` | Ouvre l'écran à distance (test) |
| `/boutique donner\|reprendre <cible> <id> "<motif>"` | Ajoute ou retire une pièce de la garde-robe |

**Poste de garde** (volet v3, onglet Outils → **Boutiques**) : liste des boutiques par village (genre, entreprise,
comptoirs, estrades, état) ; fiche compacte d'une boutique : « Viser un comptoir… », « Placer l'estrade ici »,
catalogue (prix, condition) avec formulaire dans le volet. Fiche joueur, section Économie : « Garde-robe : 6 tenues ·
2 cosmétiques de compte ». Les produits en RBCoins se gèrent dans le **panel web** (`apps/admin`), jamais en jeu.

---

## 10. Journal

| Catégorie | Type | Ligne |
|---|---|---|
| Économie | `ACHAT` | « #00042 Kenta Arai · Tailleur Hōsen · Yukata indigo · 450 ryo · bourse 1 240 → 790 · entreprise : tailleur_hosen » |
| Économie | `ACHAT_RBCOINS` | « #00042 (compte K…) · Masque de fête · 600 RBCoins · 920 → 320 · réf. achat » (l'API garde le mouvement) |
| Objet | `TENUE` | porter / ranger une tenue (hors boutique), don et reprise staff |
| Staff | `BOUTIQUE_CREER`, `_SUPPRIMER`, `_COMPTOIR_AJOUTER`, `_COMPTOIR_RETIRER`, `_ESTRADE`, `_ARTICLE`, `_OUVRIR`, `_FERMER`, `_DONNER`, `_REPRENDRE` | acteur, boutique, détail |

Les **essais** ne sont pas journalisés (bruit). Le Poste de garde voit les achats dans « Journal économie ».

---

## 11. Ce qu'il faudra coder (pour mémoire, après validation)

- **ShinobiCore `boutique/`** (remplace `shop/ShopManager`) : `BoutiqueService` (boutiques, comptoirs, estrades,
  catalogues, conditions), `EssayageService` (placement sur l'estrade, occupation), `CompteService` côté RBCoins (appels
  HMAC à l'API, cache du solde et des cosmétiques par joueur), canal `reborn:boutique`, commandes `/boutique`, journal.
- **reborn-hud** : `boutique/CameraEssayage` (point de vue, glissement, trois cadrages, repli), `BoutiqueScreen`
  (tailleur et compte, même ossature), `BazarScreen`, vignettes `PersoPoupee`, petite icône de compte, carte BOUTIQUE de
  l'Échap, plaque Tenue de la sacoche, textures générées `tools/gen_boutique_textures.py` ; banc
  `REBORN_BOUTIQUE_DEBUG`.
- **API** (train web) : `/v1/game/compte/:mcUuid`, `/v1/game/rbcoins/achat`, modèles (§6.4), pages du panel.
- **Launcher** (train launcher) : `.reborn-rbcoins` + `-Dreborn.rbcoinsPath` ; page Boutique (achat de RBCoins, Stripe
  v1.1).

---

## 12. Questions pour l'utilisateur (avec la recommandation)

| # | Question | Recommandation |
|---|---|---|
| 1 | Caméra **dans le monde**, en face du personnage (maquettes), plutôt que le personnage dessiné sur un voile ? | **Oui**, repli sur le voile seulement si la caméra est bloquée |
| 2 | Le client est **placé sur l'estrade** d'essayage (posée par le staff), ou essaie là où il se tient ? | **L'estrade** : cadrage maîtrisé, pas de caméra dans un mur, et c'est du RP visible |
| 3 | Accessoires 3D achetés au tailleur = **objets** (échangeables, volables, comme aujourd'hui) ; tenues = garde-robe ? | **Oui** (aucune règle existante ne change) |
| 4 | Se changer **hors boutique** depuis la sacoche (3 s, hors combat) ? | **Oui** |
| 5 | Vivres et cartes → **bazar** (comptoir du marchand général) ? | **Oui** ; ration aussi chez Ichiraku plus tard, si un restaurant le veut |
| 6 | Prix par gamme (§4 : 200 à 2 000) au lieu du prix plat de 500 ? | **Oui** |
| 7 | Grade / fonction non remplis **grisés avec la raison**, autre clan / village **cachés** ? | **Oui** |
| 8 | Ventes = **puits** en V1, 20 % à la caisse de l'entreprise en V2 ? | **Oui** |
| 9 | Abonnement « Soutien » : un seul niveau, contenu §7 ; **+1 emplacement de personnage** acceptable ? **File prioritaire** ? | Un niveau ; **+1 emplacement oui** (confort, pas de puissance) ; **pas de file prioritaire** (à vérifier dans les règles commerciales de Mojang) |
| 10 | RBCoins : jamais de tenue d'institution, de clan, de grade, de village, ni d'arme ou de réinitialisation ? | **Oui** |
| 11 | Pas d'achat de RBCoins en jeu : seulement depuis le launcher ? | **Oui** |
| 12 | L'échoppe quitte l'Échap dès que le tailleur et le bazar existent (pas d'accès transitoire en ryo) ? | **Oui** ; `/boutique voir` pour le staff |

---

## 13. Ce qui est codé (15/10, publié sur le dev : hud 0.4.181, manifest 3.1.137)

### 13.1 L'Échap : la carte BOUTIQUE et son écran vide

- `mod-hud/…/menu/esc/EscTokonoma` : la carte **ÉCHOPPE** devient **BOUTIQUE** (« Bientôt » au lieu de la bourse) ;
  plus de demande `reborn:shop open` à l'ouverture de l'Échap. Textures (`tools/gen_echap_textures.py`) : le blason du
  noren porte un **kimono au trait** au lieu du kanji 商, les chōchin sont **sans kanji** (店 retiré).
- `boutique/BoutiqueVideScreen` : voile sur le jeu et vignette comme les autres écrans de l'Échap, « BOUTIQUE » et un
  filet d'or au centre, « Bientôt. », « Échap revenir » ; HUD masqué ; ni tenue, ni RBCoins, ni kanji.
- L'ancien `ShopScreen` n'est plus ouvert par l'Échap (il reste dans le code, sans entrée ; son rendu
  `renderAvatar360` sert au repli du tailleur). Côté serveur, `ShopManager` refuse désormais les achats `buy*`
  (« L'échoppe a déménagé… ») quand les boutiques-lieux tournent ; `open` et `equip` restent pour les anciens clients.

### 13.2 Le tailleur et le bazar (écran)

`mod-hud/…/boutique/` — `BoutiqueClient` (canal `reborn:boutique`, protocole PLUGINS_REFERENCE §4 sexies),
`BoutiqueScreen`, `CameraEssayage`, `BoutiqueDebug` (banc) ; vignettes par `menu/character/perso/PersoVignettes`
(accès public aux poupées) ; essai des objets 3D par `cosmetic/CosmeticFeatureRenderer.ESSAI` (joueur local seul) ;
textures `tools/gen_boutique_textures.py` → `textures/gui/boutique/` (panneau, mètre ruban, balance romaine, bourse,
cadenas, pièce 8 × 8).

| Élément | Comportement |
|---|---|
| **Caméra** | `CameraEssayage` : posée **dans le monde** en face du personnage (point de vue de l'estrade envoyé par le serveur ; sinon calculé là où il se tient : devant, derrière, puis sur les côtés), champ 55°, glissement de 300 ms des yeux vers le point de vue (position et regard), trois cadrages **En pied · Buste · Visage** (molette, mots cliquables, glissement 300 ms). Branchée dans `CameraRepositionMixin` (position, regard) et `CameraPhotoMixin` (champ) ; la caméra épaule passe en mode vanilla le temps de l'écran (elle ne ramène plus le regard). Aucun nouveau mixin |
| **Repli** | Un bloc entre le personnage et le point de vue → le personnage est **dessiné en interface sur un voile** (rendu de l'ancienne échoppe, trois tailles) ; le reste de l'écran est identique |
| **Rotation** | Glisser au centre : le **corps pivote pour de vrai** (`yRot`, tête et corps ; parti au serveur, les autres le voient) ; double-clic : de face |
| **Gauche** | Libellé du comptoir en petites capitales, ses **catégories** en mots avec leur nombre (actif : gras crème, trait d'or ; vide : grisé), « Tout retirer », « Quitter » ; voile en dégradé sous la colonne |
| **Haut** | Mètre ruban d'or (signature), nom de la boutique (ArcadePix ×1,5, or), filet d'or, « Konoha · Au comptoir : Hōsen » (membre de l'entreprise de la boutique à 6 blocs) ou « Libre-service » ; « Tu essaies : … Retirer » ; messages du serveur |
| **Catalogue** (droite) | Panneau laqué 230 × 382 GUI : catégorie, nombre de pièces, **bourse** (« 1 240 ryo · Bourse · sur toi ») ; grille 3 × 3 de vignettes (défilement à la molette) : la tenue **sur son propre corps** (poupée rendue une fois, de trois quarts), l'objet 3D en icône ; « essai », « portée », « à toi », prix (rouge si la bourse ne suffit pas), cadenas + raison courte pour une pièce verrouillée (info-bulle : « Réservé aux… », « Ton grade : … », « Tu peux l'essayer, pas l'acheter ») |
| **Pièce choisie** | Nom, prix, liaison (« Tenue · liée à ce personnage », « Objet · va dans ta sacoche ») ou condition en ambre ; une plaque fine + un mot : **Acheter · N ryo** / **Acheter et porter** ; « Il te manque N ryo » / « Où est le guichet ? » ; « Essayer reste libre » ; tenue possédée : **Porter** / **Ranger** |
| **Confirmation** | Cadre centré : « Acheter X ? », prix, « bourse 1 240 → 990 », liaison, **Acheter et porter · Entrée**, « Acheter », « Annuler » |
| **Essais** | Clic sur une vignette = essai gratuit, pour soi seul (`RebornSkins.applySpec` pour une tenue, `ESSAI` pour un cosmétique 3D par emplacement) ; cumul tenue + objets ; tout s'efface en sortant |
| **Bazar** | Même écran sans caméra : panneau centré, balance romaine, nom, onglets en mots (Vivres · Cartes), liste (nom, prix), bourse en bas à gauche, « Acheter · N ryo » |
| **Sortie** | Échap (ferme d'abord la confirmation), « Quitter », coup reçu, éloignement (> 8 blocs), boutique fermée : la caméra revient aux yeux, l'apparence réelle est remise |

### 13.3 Côté serveur (ShinobiCore `boutique/`)

| Classe | Rôle |
|---|---|
| `BoutiqueService` | Canal, clic droit sur un comptoir (bloc, meuble Nexo à 1 bloc près, PNJ précis), refus (fermée, KO / porté / étourdi / gelé / tenu, mode staff, **5 s après un coup**), placement sur l'**estrade** libre la plus proche (≤ 12 blocs ; « Quelqu'un essaie déjà »), offres du comptoir (catégories, liste précise, articles retirés, prix propres, conditions), achats, fermeture (coup reçu, > 8 blocs) |
| `Catalogue` | `boutique-catalogue.yml` (copié du jar, `/boutique recharger`) : 66 articles au 15/10 — les **59 tenues** du mod en `tenues` (toutes complètes : couverture des calques vérifiée ; tenues de clan `clan=uchiha`, tenues féminines `sexe=femme`), et 7 cosmétiques 3D du pack Nexo (masques Hannya et ANBU `fonction=anbu`, bandeau de Konoha `village=konoha`, chapeau de voyage, trois rouleaux de dos). Prix par gamme : 200 à 400 au quotidien, 450 à 900 les belles tenues, 1 200 à 1 400 la cérémonie, 450 à 800 les tenues de clan, 120 à 600 les objets |
| `Conditions` | `grade>=chunin` / `grade=…`, `fonction=<x>` (permission `reborn.fonction.<x>`), `entreprise=<id>` → **grisé avec la raison** ; `clan=`, `village=`, `sexe=`, `personnage=#N°` → **caché** |
| `Boutique`, `BoutiqueStore` | `boutiques.yml` : boutiques (genre tailleur / bazar, village, entreprise, ouverte, caméra), comptoirs (catégories, articles), estrades (place + regard), prix propres, articles retirés |
| `BoutiqueCommand` | `/boutique` (staff), §13.4 |

**Achats** : tout est revérifié (comptoir, condition, bourse, place, état). Paiement par la **bourse**
(`BourseService.debiter`, journal `ECONOMIE ACHAT` : « −450 ryo · bourse 1 240 → 790 · Tailleur Hōsen · Yukata indigo
· entreprise : … ») ; ryo **détruits** (V1). **Tenue** → garde-robe du personnage (`ownedOutfits`), « Acheter et
porter » applique l'apparence et la rediffuse. **Objet 3D** → la sacoche (« Libère une case » sinon), ou directement
son emplacement cosmétique s'il est libre (« Acheter et porter ») ; échangeable, volable, perdu à la mort RP comme tout
cosmétique. **Vivres** → `NourritureModule.acheterVivre` (paniers réservés aux cuisiniers). **Cartes** →
`MapItem.give`. Porter / ranger une tenue de sa garde-robe au comptoir : journal `OBJET TENUE`. Les essais ne sont pas
journalisés. **Migration** : rien à faire — les tenues déjà achetées (`ownedOutfits`) restent acquises.

### 13.4 Commandes staff (`shinobicore.boutique`, opérateur, ou Modérateur du Poste de garde et au-dessus)

| Commande | Effet |
|---|---|
| `/boutique creer <tailleur\|bazar> "<nom>" [village] [entreprise]` | Crée la boutique |
| `/boutique comptoir ajouter <b> [catégories…]` | Marque le bloc, le meuble Nexo ou le PNJ **visé** ; sans catégorie : toutes celles du genre |
| `/boutique comptoir categories <cat…>` · `articles <id…\|tous>` · `nom "<libellé>"` · `retirer` | En visant le comptoir : ses catégories, une liste précise, son libellé (« Rayon des masques »), le retirer |
| `/boutique estrade ajouter\|retirer <b>` | Place du client = là où se tient le staff, **regard vers la caméra** ; la ligne de visée s'affiche 10 s (fumée = bloc gênant) |
| `/boutique camera <b> <distance 1,5-5> <hauteur 0,5-1,8>` | Point de vue (défaut 2,7 · 1,0) |
| `/boutique article prix <b> <id> <prix\|catalogue>` · `article retirer\|remettre <b> <id>` | Prix propre à la boutique ; ne plus vendre / revendre un article |
| `/boutique ouvrir\|fermer <b>` · `info <b>` · `liste [village]` · `supprimer <b> confirmer` | Gestion (fermer ferme les écrans ouverts) |
| `/boutique voir <b> [n° de comptoir]` | Ouvre l'écran à distance (test, sans estrade ni contrôle d'état) |
| `/boutique donner\|reprendre <cible> <tenue> "<motif>"` | Garde-robe d'un personnage |
| `/boutique recharger` | Relit `boutique-catalogue.yml` |

Catégories : `tenues`, `hauts`, `bas`, `manteaux`, `coiffes`, `masques`, `accessoires` (tailleur), `vivres`, `cartes`
(bazar). Journal Staff : `BOUTIQUE_CREER`, `_SUPPRIMER`, `_COMPTOIR_AJOUTER`, `_COMPTOIR_RETIRER`,
`_COMPTOIR_CATEGORIES`, `_COMPTOIR_NOM`, `_ESTRADE` (estrade et caméra), `_ARTICLE`, `_OUVRIR`, `_FERMER`, `_DONNER`,
`_REPRENDRE`. Pas de section dans le Poste de garde (commandes seulement).

### 13.5 Banc et captures

`REBORN_BOUTIQUE_DEBUG=1` (client de dev, monde Konoha, jamais de serveur) : construit une petite échoppe de tailleur
dans le ciel au-dessus du joueur (blocs relevés puis rendus), place le personnage sur l'estrade, rejoue les messages du
serveur et capture `run/screenshots/boutique_*.png` (comptoir des vêtements sur « Hauts », essai, de dos, visage,
confirmation, après l'achat, comptoir des masques avec essai du masque, condition grisée et info-bulle, bazar, repli sur
voile, carte BOUTIQUE de l'Échap, écran Boutique vide) ; copies dans `Maquettes/gameplan/42_boutiques/code/`. Le pack
de dev `run/resourcepacks/reborn_boutique_dev` (hors dépôt : modèles Nexo du masque Hannya, du chapeau, des rouleaux)
est activé le temps du banc. Monde, position, mode de jeu, heure, météo, packs et échelle sont rendus à la fin.

### 13.6 Écarts et reste à faire

- **Hauts, bas, manteaux vides** : les 59 tenues du mod sont toutes des tenues complètes et le pack Nexo n'a ni haut,
  ni bas, ni manteau ; un comptoir « hauts » affiche « Rien de cette catégorie au catalogue pour l'instant » jusqu'à
  l'arrivée des assets (une ligne par pièce dans `boutique-catalogue.yml`).
- **Masque ANBU et bandeau de Konoha** n'ont pas encore de modèle dans le pack Nexo (seulement leur entrée d'objet) ;
  un cosmétique 3D sans placement réglé (« Repositionner ») s'essaie à sa taille par défaut, souvent trop grande (le
  masque Hannya couvre le buste) — même rendu que s'il était porté.
- **Se changer hors boutique** depuis la sacoche (3 s, hors combat, décision par défaut) : **non codé** ; on porte et
  on range ses tenues à n'importe quel comptoir de tenues.
- **Fonction** (ANBU…) = permission `reborn.fonction.<x>` en attendant le menu Faction ; **entreprise TAILLEUR** : la
  présence au comptoir s'affiche (membre de l'entreprise de la boutique, quel que soit son type), pas de revenu (V2).
- Pas de section Boutiques au Poste de garde ; pas de marqueur du guichet sur la carte ; pas de chaussures.
- RBCoins, abonnement, boutique de compte : en attente des visuels de l'utilisateur (§6-§7).

