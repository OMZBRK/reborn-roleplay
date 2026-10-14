# Inventaire (sacoche), ramassage, cosmétiques et arme

> **Fiche système, à jour au 2026-10-13** (lot « inventaire et ramassage », codé, **non publié** : ShinobiCore,
> ShinobiAbilities, ShinobiCombat, `reborn-hud`). Elle décrit **ce qui est codé**. Décisions : GAMEPLAN §2-3 et
> [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md) (13/10). Maquettes validées (hors dépôt) :
> `Maquettes/gameplan/02_inventaire/` (01 sacoche, 02 surpoids, 03 notifications, 04 ramassage).
>
> Code : `ShinobiCore/…/inventory/` (serveur), `mod-hud/…/menu/inventory/` (sacoche), `mod-hud/…/sol/`
> (ramassage, aura), `mod-hud/…/notif/` (notifications, indicateur de surcharge), `mod-hud/…/cosmetic/` (rendu porté).
> Canaux, messages, config et journaux : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md).

---

## 1. Le principe en une phrase

**Ce que porte le personnage est à lui, se voit, pèse, et se ramasse à la main** : une sacoche liée au personnage
(barre 1-9 + sac à paliers), une arme à l'étui visible à la ceinture, un poids qui ralentit au lieu d'interdire, et
des objets au sol qu'on vise et qu'on prend (plus de ramassage automatique).

---

## 2. La sacoche (touche E)

| Élément | Ce qui est codé |
|---|---|
| **Barre 1-9** | La vraie barre d'action du joueur (« sur soi »), affichée comme une obi |
| **Sac** | Espace en plus débloqué par le sac porté (emplacement Sac, onglet Équipement) |
| **Capacité de base** | 9 cases, **12 kg** (choix Reborn, pas les 12 + 6 cases / 20 kg de RedLine) |
| **Persistance** | Par personnage : `plugins/ShinobiCore/rpbags/<uuid du perso>.yml` |
| **Cases cachées 9-35** | Minecraft a 27 cases que la sacoche n'affiche pas : tout ce qui y tombe (give, plugin) est **rangé** dans la barre, puis le sac, sinon posé au sol, à chaque seconde et à chaque changement de personnage. Les dons des plugins passent par `InventoryService.give` (barre → sac → sol) |

### Sacs à paliers (`BagTier`, inchangés)

| Sac | Objet | Cases en plus | Poids max en plus | Total |
|---|---|---|---|---|
| Aucun (« à la ceinture ») | — | +0 | +0 | 9 cases, 12 kg |
| Sacoche | `sac_sacoche` | +9 | +8 kg | 18 cases, 20 kg |
| Bandoulière | `sac_bandouliere` | +18 | +16 kg | 27 cases, 28 kg |
| Sac ninja | `sac_dos` | +27 | +28 kg | 36 cases, 40 kg |
| Sac lourd | `sac_lourd` | +36 | +45 kg | 45 cases, 57 kg |

### Ce qu'on fait dans la sacoche

| Geste | Effet |
|---|---|
| Clic | prendre / poser (fusion des piles, échange) |
| Clic droit | panneau d'actions : utiliser, équiper, **à l'étui**, jeter, détruire |
| **Glisser hors de la fenêtre** | lâcher au sol (le clic hors des cases avec un objet en main jette aussi) |
| Onglet Équipement | **Sac**, **Tête** (rappel), **Arme** (étui) |
| Onglet Cosmétique | les 10 emplacements de cosmétiques portés |

**Lecture seule** : en **KO**, **porté**, **étourdi** (combat : coup final, garde brisée, parade subie) ou pendant la
**scène de mort**, la sacoche s'ouvre mais rien ne bouge (« Lecture seule — KO » sous le nom) ; le serveur refuse
toute action, la touche « jeter » (Q) aussi. L'équipement reste en place.

---

## 3. Tête et Arme (onglet Équipement)

- **Tête** : **pas de nouvel emplacement**. La plaque « Tête » de l'onglet Équipement rappelle ce que portent les
  emplacements Chapeau / Masque / Bandeau de l'onglet Cosmétique (masque d'abord, puis chapeau, puis bandeau) ; un
  clic ouvre l'onglet Cosmétique.
- **Arme = l'étui** :
  - on y range une arme (clic ou glisser sur la plaque, ou « À l'étui » dans le panneau) ; seules les armes entrent
    (ids `inventaire.arme.ids` — katana, ninjato, tantō, kendo, gunbai… — et épées, haches, trident, masse) ;
  - l'arme à l'étui **se voit à la ceinture**, hanche gauche, pour tous les joueurs (diffusée comme l'emplacement
    `ARME` de `reborn:cosmetics`, ancrée au bassin, repositionnable comme un cosmétique) ;
  - **dégainer / rengainer : touche Z** (rebindable) ou le panneau : l'arme passe dans la case tenue (ou la première
    case libre de la barre, qui devient la case tenue) ; Z à nouveau la remet à l'étui (une arme simplement tenue en
    main peut aussi être rengainée). « Mains pleines » si la barre est pleine ;
  - **techniques d'arme** : l'API `InventoryService.weapon(p)` rend l'arme (étui, dégainée, ou tenue en main). Le
    **Kenjutsu** l'exige (`jutsu.kenjutsu-exige-arme` de ShinobiAbilities, vrai par défaut).

---

## 4. Le poids et la surcharge

Poids d'un objet : poids propre posé par le plugin qui le crée (`reborn:rp_weight`, ex. parchemins), sinon barème par
famille de matériau (`RpWeights`). Le poids compte la barre, le sac, les cosmétiques et l'étui.

| Palier | Charge (poids / poids max) | Effet |
|---|---|---|
| Normal | ≤ 100 % | rien |
| **Chargé** | 100 – 115 % | vitesse 90 → 70 %, **plus de sprint** |
| **Surchargé** | 115 – 130 % | vitesse 70 → 35 %, **ni saut, ni dash, ni course aux murs** (mobilité ninja coupée : double saut, saut mural, escalade, onde au sol, course de chakra, Voie du Flux, dash et bonds de combat) |
| **Immobile** | > 130 % | vitesse 0 ; on peut encore lâcher, ranger, parler |

- Appliqué par **modificateurs d'attributs** (`movement_speed`, `jump_strength`), plus de potions (ni icône, ni
  particules, ni fatigue). Le bonus de vitesse du sprint est annulé côté serveur, et le mod empêche de sprinter.
- Recalcul à **chaque changement** (ramassage, don, action dans la sacoche, Q, changement de personnage) et **chaque
  seconde** (filet de sécurité).
- **Une notification au changement de palier** seulement (« Tu es surchargé — tu ralentis », « Tu peux de nouveau
  courir ») ; plus de message rouge en barre d'action.
- **Indicateur discret sous les vitals** dès « Chargé » : kanji, mot, pourcentage, jauge d'un pixel 0 → 130 % avec
  repères à 100 / 115 / 130 %. Les vitals eux-mêmes ne changent pas.
- **Jauge de la sacoche** graduée 0 → 130 % (mêmes repères), or jusqu'à la limite, laque rouge au-delà ; la valeur
  affiche « 45,2 / 38,0 kg · 119 % ».
- Seuils et vitesses : `inventaire.surcharge.*`. Les add-ons lisent le palier par `InventoryService.palier(p)`.

---

## 5. Ramasser

**Plus de ramassage automatique** : marcher sur un objet ne le ramasse plus (survie et aventure ; flèches et tridents
inchangés ; créatif inchangé).

1. On **vise** l'objet (rayon de la caméra, à 3 blocs au plus des yeux, non caché par un bloc).
2. Une **invite** discrète apparaît au-dessus : « [F] Ramasser Kunai ×3 », ou « … · trop lourd » / « … · plus de
   place » quand le mod le prévoit.
3. Touche **Ramasser = F** (rebindable). Elle n'est prise **que** quand un objet est visé : sinon F garde son rôle
   (techniques, tyrolienne, grappin, main gauche).
4. Le serveur vérifie tout : distance, vue, délai de ramassage, objet réservé, état (KO, porté, étourdi, scène de
   mort), cadence (4 par seconde au plus), **place** (fusion des piles comprise : barre puis sac), **poids**.

| Situation | Résultat |
|---|---|
| Place et poids ≤ 130 % | ramassé : « +3 Kunai » |
| Pile trop grosse pour la place ou le poids | **ramassée en partie** : « +2 Kunai — 1 reste au sol » |
| Plus aucune case | reste au sol : « Plus assez de place — libère une case » |
| Au-delà de 130 % même pour un seul | reste au sol : « Trop lourd — 2,4 kg de trop » |
| KO, porté, étourdi | « Impossible en KO » (…) |

On peut donc **dépasser la limite de poids jusqu'à 130 %** en ramassant (on ralentit), pas au-delà.

---

## 6. Objets au sol

- **Rareté** (cinq niveaux, §7) posée sur l'objet quand il apparaît au sol ; le mod l'affiche en **aura** : halo
  doux posé à plat (presque invisible pour un Commun), anneau fin à partir de Peu commun, **trois braises qui
  montent** pour un Légendaire ; l'objet visé a son halo ravivé. Au plus 48 objets à 16 blocs, en un seul lot de
  rendu ; coupable dans **Réglages → Interface → Auras de rareté**.
- **Disparition** : **10 min** au sol, **15 min** pour un objet lâché par un joueur KO (ou apparu tout près d'un KO /
  d'une scène de mort), **jamais** pour un rouleau (identifiant unique), un objet marqué unique ou de quête
  (`reborn:unique`, `reborn:quete`, ou id listé) — ces derniers sont aussi protégés du feu, de la lave et des
  explosions. Horloge réelle : un objet d'un tronçon déchargé expire à son rechargement. Les objets de décor posés par
  un plugin (jamais ramassables) ne sont pas touchés.

---

## 7. Rareté

| Rareté | Couleur | Rang de parchemin | Aura |
|---|---|---|---|
| Commun | gris clair `CFC8B8` | D (et E) | presque invisible |
| Peu commun | vert `6FBF8E` | C | halo doux + anneau fin |
| Rare | bleu `5E95E0` | B | halo doux + anneau fin |
| Épique | violet `A97BE0` | A | halo plus présent |
| Légendaire | **or** `E9BC5C` | S | halo + 3 braises |

**Résolution** (serveur, `RareteItems`) : clé persistante `reborn:rarete` posée sur l'objet → table
`rarete.par-id` de la config (id Nexo ou id de modèle) → rang du parchemin (modèle `reborn:parchemin_<rang>`) →
composant vanilla `rarity` → Commun. La rareté colore aussi le liseré des cases de la sacoche et le nom dans les
notifications (à partir de Peu commun).

---

## 8. Notifications (en bas à droite)

Canal générique `reborn:notif`. Une ligne de texte petit, une petite icône (l'objet, ou un sceau kanji), un voile
très léger, un filet d'or d'un pixel ; **4 lignes au plus**, la plus récente en bas, **3 s** chacune avec fondu ;
**gains identiques cumulés** sur 1,5 s (+1, +1, +1 → +3).

| Cas | Exemple |
|---|---|
| Gain | « +3 Kunai » (nombre en or ; nom teinté à partir de Peu commun) |
| Gain partiel | « +2 Kunai — 1 reste au sol » |
| Perte | « −1 Ration · lâchée » |
| Refus de place / de poids | « 袋 Plus assez de place — libère une case », « 重 Trop lourd — 2,4 kg de trop » |
| Palier | « 重 Tu es surchargé — tu ralentis », « Tu peux de nouveau courir » |
| Sac | « 袋 Sac ninja équipé — +27 cases », « Vide ton sac — avant de le retirer » |
| État | « 止 Impossible en KO » |
| Rangement | « 袋 2 objets rangés — dans ta sacoche » (cases cachées) |

Les dons des plugins (bibliothèque, test de la Feuille, soins, carte…) s'annoncent aussi par une ligne « +1 … ».

---

## 9. Journal des objets

- **Fichier tournant** `plugins/ShinobiCore/logs/inventaire-AAAA-MM-JJ.log` (catégorie `INVENTAIRE` du journal universel
  depuis le 14/10, format commun ; objets obtenus → `logs/objet-…`, équipements et objets utilisés aussi tracés) : ramassé, lâché (sacoche ou Q), donné,
  détruit, rangé (case cachée), mis à l'étui, disparu (Rare et plus) — heure, `#N° pseudo (nom RP)`, quantité,
  objet, rareté, unique, lieu.
- **Poste de garde** (type `objet`) pour les objets **Rare et plus** et les **uniques**
  (`inventaire.journal.poste-de-garde-des`).

---

## 10. Cosmétiques portés

- 10 emplacements (`CosmeticSlot`) : chapeau, bandeau, masque, boucle d'oreille, manteau (colonne gauche) ; haut,
  tenue, ceinture, bas, dos (colonne droite) ; plus l'**étui** (`ARME`, onglet Équipement).
- Chaque cosmétique est un objet de personnage (échangeable, volable, perdu à la mort RP) rendu **sur le corps** avec
  son vrai modèle Nexo (`item_model`), ancré sur un os, placement réglable (« Repositionner », pelote à épingles) et
  **diffusé à tous** (`reborn:cosmetics`), placement compris.
- Modèles 3D : n'importe quel objet porte son propre modèle par le composant `item_model` (Nexo génère le pack) ;
  les objets 3D (parchemins, cosmétiques, sacs) vivent dans le pack Nexo (`minecraft/server-config/nexo/`), jamais
  dans les assets du mod.

---

## 11. Ce qui reste (propositions GAMEPLAN, non codées)

- Ramassage avec un court délai (~0,5 s) interrompu par un coup.
- « Combien veux-tu jeter ? » pour une pile.
- Objets uniques qui **retournent** à leur source au lieu de rester au sol.
- Préfixe de joueur dans les journaux : fait (Player ID) ; journal « utiliser » d'un objet : non.
