# Nourriture, satiété, régénération passive — le cuisinier est un joueur

> **v3 validée le 14/10, codée le même jour (non publiée)** — recommandations retenues : plat chaud 10 min (A), ration
> sèche à l'échoppe (A), cuisiniers = membres d'une **entreprise RESTAURANT** créée par le staff
> ([`ENTREPRISES.md`](./ENTREPRISES.md)), panier réservé aux cuisiniers, payer en tendant les ryo, défaut de faim
> corrigé. Ce qui est codé, protocole, réglages et écarts : §13. Captures du code :
> `Maquettes/gameplan/12_nourriture/code/` (banc `REBORN_NOURRITURE_DEBUG`).
> **Décisions du 14/10** : on mange chez un cuisinier, pas n'importe où ; puis, sur la v2 (restaurant à carte, PNJ,
> places) : « beaucoup trop mécanique et pas assez RP. Ce qui serait bien : un mec qui vend et prépare les
> aliments/plats, et les joueurs, quand ils ont faim, viennent manger en commandant ; le mec donne la nourriture et le
> RP se passe ; la consommation du plat prend tout de même du temps. » **Plus aucune écriture chinoise ou japonaise
> décorative** (kanji, sceaux) : « ça fait ringard » ; compositions centrées, épurées.
> Source : RedLine, *Système Nourriture / Satiété / Régénération passive — V1*. Game Plan : [`../GAMEPLAN.md`](../GAMEPLAN.md)
> §4, §12, §27. Maquettes (hors dépôt) : `Maquettes/gameplan/12_nourriture/v3/` — `01_cuisinier_prepare.png`,
> `02_tendre_et_payer.png`, `03_le_client_mange.png`, `04_effet_et_satiete.png`, `05_personne_pour_cuisiner.png`
> (captures brutes dans `captures/`, script `_maquettes_source_v3.py`). v1 et v2 restent dans `12_nourriture/` et
> `12_nourriture/v2/` pour mémoire (abandonnées).

---

## 1. Ce qui existe aujourd'hui (audit du 14/10)

- **La barre or des vitals est déjà la faim vanilla** : `VitalsHud` l'appelle « stamina » mais affiche
  `foodLevel / 20` (`mod-hud/…/runtime/VitalsHud.java` l. 152-156). Un « 85 % » = faim vanilla 17/20.
- **Défaut actuel** : la faim n'est pas tenue. `CharacterManager.applyStats` (l. 541-556) pose un effet SATURATION
  de 90 s au changement de personnage, jamais renouvelé (`display.saturation-refresh-seconds` n'est lu nulle part) ;
  le trot creuse ensuite la faim, sous 6/20 le vanilla coupe le trot, à 0 la famine vanilla peut blesser.
- L'ATA et la douleur **forcent la faim vanilla à 6** pour interdire la course (`AtaManager` l. 395-401,
  `DouleurManager` l. 171-192).
- PV persistants hors terrain d'entraînement (`TrainingZones` l. 283-292) ; repos jusqu'à 30 %
  (`AtaManager.tick`). Aucun horodatage générique du dernier dégât.
- Aucun aliment ; `pilule_soldat`, `ration` dans le registre de la sacoche sans effet ; pilule du soldat (médic) :
  +100 chakra, Vitesse II 3 min, hématomes (`medic/PiluleConsumeListener`). Règle « manger en zone de repos sous
  ATA = +2 min de repos » (`AtaManager` l. 480-491).
- Pas de métiers (spécialisations par permission : médic) ; pas de fenêtre d'échange (décision du 14/10 : on échange
  à la main). Endurance `ShinobiCombat/StaminaManager` ; notifications `reborn:notif` (bas droite).

## 2. Le principe

**Un joueur cuisinier prépare et vend ; les autres viennent manger chez lui quand ils ont faim.** Le système ne
fait que le minimum : préparer prend du temps, un plat est un objet, le manger prend du temps et nourrit. Tout le
reste (commander, le prix, l'ardoise, le repas offert, la conversation au comptoir) est du RP.

## 3. Satiété et régénération (inchangées depuis la v1, recommandations appliquées)

- Jauge 0-100 % par personnage, dans la **barre or actuelle des vitals** (repère de 2 px à 90 %), gelée en KO,
  porté, AFK et déconnecté. La faim vanilla est réellement neutralisée ; l'ATA et la douleur coupent le trot par les
  allures.
- Perte **0,0125 %/s** × activité (assis ×0,5, marche ×1, trot ×1,5, sprint ×4, nage ×2, combat ×3, technique
  −0,2 % par 10 chakra) : ≈ 1 h 30 de rassasié à affamé, **environ deux repas par soirée**.
- **Régénération** : satiété ≥ 90 %, aucun dégât depuis 30 s, ni KO ni douleur ; **0,25 % des PV/s**, jusqu'à 100 %
  hors ATA, **30 % pendant l'ATA** (le KO garde son médic ou son repos). Pause après un coup : icône grise et
  minuteur ; vie qui remonte : lueur douce sur la barre rouge.
- Sous ~~20 %~~ **15 %** (19/10) : **affamé** (icône ambre, alerte discrète) ; **0 % : FAIM** — ni sprint, ni course
  ninja, **ni dash** (19/10), récupération d'endurance −50 %, pas de régénération, caméra qui tangue (19/10), **jamais
  de dégâts**. Détail au §3 bis.

## 3 bis. Faim punitive (GAMEPLAN §4, décision du 19/10)

On garde la satiété RP (§3) **et** on ajoute le punitif de RedLine, **sans aucun malus de dégâts** (ni dégâts infligés
réduits, ni dégâts subis, ni perte de vie liée à la faim — correction de l'utilisateur le 19/10).

| Palier | Seuil (`config.yml`) | Effet | Où |
|---|---|---|---|
| Affamé | sous `nourriture.faim.affame-seuil` = **15 %** (remplace `satiete.affame` = 20, ignorée) | alerte discrète « Affamé — va manger » en bas à droite au passage du seuil + icône ambre dans la zone d'effets ; **rien d'autre** | `SatieteService.palier` = 1, modificateur dérivé `affame` de `reborn:etat` |
| FAIM | **0 %** | **ni sprint ni dash** (ligne `mod.faim` d'`etats.yml` : `SPRINTER`, `MOBILITE` refusés, « Tu as trop faim. ») ; course ninja refusée (contributeur) ; récupération d'endurance ×`faim.endurance-recuperation` = **0,5** ; pas de régénération ; **caméra qui tangue** (≈ 0,9° de lacet, 0,55° de tangage, 5 à 7 s, entrée et sortie en 2 s) ; alerte « Faim — ni sprint ni dash » + icône vermillon | `SatieteService.palier` = 2, dérivé `faim`, mod `FaimCamera` + `CameraFaimMixin` |

- **La satiété baisse bien avec le temps** (vérifié le 19/10) : `SatieteService.tick` chaque seconde, 0,0125 %/s ×
  activité (§3), figée en KO, porté, AFK 5 min, mode staff, créatif — de 80 % (départ) à 15 % ≈ 1 h 25 de marche, à
  0 % ≈ 20 min de plus. `display.auto-saturation` ne touche que la faim **vanilla** (gardée pleine), pas la satiété RP.
- Le **dash** est refusé par le serveur (ShinobiCombat lit `MOBILITE`) et bloqué en avance par le mod (`CombatInput.dash`
  lit `mob` de `reborn:etat` : ni recharge fantôme ni traînée) ; le sprint par l'allure (`SPRINTER`) et le mod (`spr`).
- La caméra qui tangue suit le réglage d'accessibilité vanilla **« Effets de distorsion »** (0 = coupée, proportionnel
  sinon), comme le ressenti du KO ; rien sous un voile imposé (KO, mort).
- Captures et MP4 : `Maquettes/gameplan/04_hud/` (`hud4_06` à `hud4_08`, `hud4_faim_0_camera.mp4`).

## 4. Le cuisinier

- **Un métier tenu par un joueur**, donné par le staff (ou la Faction), **2 ou 3 pour Konoha à la beta** (proposé).
- **Son poste** : un fourneau (meuble Nexo) dans son échoppe ; il peut y en avoir plusieurs dans le village.
- **Préparer (minimum viable)** : un **panier d'ingrédients** en main (par plat : « Ingrédients : ramen »,
  « … : dango »…), clic droit sur un fourneau ; **15 s**, il parle et bouge dans sa cuisine ; s'éloigner de plus de
  4 blocs ou être frappé interrompt (le panier n'est pas perdu). **Un panier = deux plats.** Une ligne fine et un
  filet au-dessus de la barre d'action pendant la préparation, une ligne en bas à droite quand c'est prêt.
  Maquette 01.
- **Ingrédients** : vendus à l'échoppe des fournisseurs, **au cuisinier seulement** (30 ryo le panier, indicatif) ;
  plus tard, ingrédients rares placés à la main (§27).
- **Vendre** : il fixe ses prix librement (conseil : 100 à 150 ryo un ramen) ; il garde la recette.
- Pas de PNJ, pas de carte, pas de caisse.

## 5. Donner et payer de la main à la main (maquette 02)

- Objet en main, regarder quelqu'un tout près : **« [R] Tendre Ramen shōyu à Ren »** (le geste passe par le mode
  interaction R ; ce n'est pas une fenêtre d'échange).
- L'autre voit deux lignes au-dessus de sa barre (comme l'invitation de duel, sans sceau) : « Teuchi te tend un Ramen
  shōyu — chaud », « F prendre · Retour refuser » ; 10 s. Pris : l'objet passe de main en main.
- **Payer = tendre des ryo** de la bourse (montant à la molette). Rien n'oblige à payer : c'est la scène.
- Tout est journalisé (journal universel, catégorie DON : objet ou montant, de qui à qui, où).

## 6. Manger (maquette 03)

- Clic droit avec le plat en main : le repas commence ; **30 s à 1 min** selon le plat. La satiété monte peu à peu
  (or pâle dans la barre = ce qui arrive). Une ligne et un filet au-dessus de la barre d'action.
- On parle, on tourne la tête, on marche lentement ; pas de course ni de technique. **Assis (sur n'importe quel
  siège), le repas va deux fois plus vite.**
- Un **coup** fige le repas 30 s, rien n'est perdu ; ranger le bol le laisse entamé.
- **Effets à la dernière bouchée.** Le bol vide reste dans la main (à rendre au cuisinier : RP, sans effet de jeu).
- ✅ **Garder l'esprit « on vient manger sur place »** — **(A) retenu le 14/10** :
  - **(A, proposé) plat chaud** : il sort chaud du fourneau et le reste **10 min** (petite icône de vapeur et
    minuteur) ; froid, ses **effets sont réduits de moitié** (la satiété reste). On peut l'emporter, mais il vaut
    mieux le manger au comptoir.
  - (B) **manger assis obligatoire** (plus contraignant, et il faut des sièges partout).
  - (C) rien de plus.

## 7. Effets (maquette 04)

- Modèle générique (type, puissance, durée), par plat dans la config. **Petites icônes d'objet, sans écriture** :
  cœur (régénération), bol (satiété prolongée), souffle (endurance), pilule ; **or** = bonus, **vermillon** = malus
  (fatigue, poison), **gris** = pause ; filet sous l'icône = temps restant ; le nom s'affiche 3 s à l'apparition.
- Types différents cumulés ; même type : le plus fort remplace. Gelés à la déconnexion ; **au KO seuls les bonus
  partent**.

| Plat (exemples) | Satiété | Repas | Effet |
|---|---|---|---|
| Ramen shōyu | +60 % | 45 s | Régénération ×2 · 5 min |
| Ramen miso | +65 % | 45 s | Satiété prolongée −25 % · 15 min |
| Dango | +20 % | 30 s | Endurance +20 % · 5 min |
| Thé vert | +5 % | 20 s | Méditation ×1,25 · 10 min |
| Bento (à emporter en mission) | +70 % | 60 s | Satiété prolongée −50 % · 20 min |

## 8. Pilules — le seul consommable de mission

- **Pilule du soldat** (objet de sacoche, partout) : avalée en 1 s, +20 % de satiété, +100 chakra, endurance +40 %
  3 min → **Fatigue 5 min** (récupération −50 %, pas de sprint) ; une toutes les 10 min ; pas dans les 30 s après
  un coup ; remplace la Vitesse II actuelle. Fournie par les médics. **Pilule de sang, pilules Akimichi : après la
  beta.**

## 9. Quand personne ne cuisine (maquette 05)

- La faim ne tue jamais (FAIM = plus de sprint, endurance plus lente).
- ✅ **Repli — (A) retenu le 14/10** : l'échoppe vend une **ration sèche** : +25 %, 20 s, **aucun effet**,
  jamais au-dessus de 60 % — on survit, on ne se régénère pas ; (B) rien : on attend un cuisinier.

## 10. Outils staff (le minimum)

- ~~`/cuisinier ajouter|retirer|liste`~~ → **entreprises** (14/10) : `/entreprise creer restaurant "…"`,
  `/entreprise membre ajouter|retirer|role`, `/entreprise poste ajouter` (en visant le fourneau) — fiche
  [`ENTREPRISES.md`](./ENTREPRISES.md) ; une ligne « Entreprise : … (employé) · Satiété … % » dans la fiche du joueur
  au Poste de garde. Pas d'écran dédié.
- Les fourneaux sont des blocs ou des meubles Nexo posés par le staff puis marqués comme postes ; dons, ventes,
  préparations et repas dans le journal universel.
- Tests et arbitrage : `/nourriture etat|satiete|donner|effet|retirer` (journalisé).

## 11. Valeurs V1 (`config.yml`, bloc `nourriture`)

| Réglage | Reborn v3 | RedLine |
|---|---|---|
| Qui nourrit | un joueur cuisinier (métier), de la main à la main | partout, ce qu'on porte |
| Préparer | panier en main + fourneau, 15 s, 2 plats | — |
| Perte de base | 0,0125 %/s (~2 repas par soirée) | 0,025 %/s |
| Seuil / régénération | 90 % / 0,25 % PV/s, 30 % max en ATA | 95 % / 1 %/s |
| Blocage après un dégât | 30 s | 30 s |
| Durée d'un repas | 30 s à 1 min (÷2 assis) | 3 s |
| Plat chaud (si A) | 10 min, puis effets ÷2 | — |
| Effets au KO | bonus perdus, malus gardés | tous perdus |
| Affamé | sous 15 % (19/10, avant 20 %) : alerte + icône, aucun malus | 15 % : malus de dégâts (non repris) |
| FAIM (0 %) | ni sprint ni dash, endurance −50 %, caméra qui tangue, pas de dégâts | plus de sprint ni de dash, caméra qui tangue |
| Pilule du soldat | 1 s, une toutes les 10 min, contrecoup | — |

## 12. Questions — tranchées le 14/10

1. Esprit « sur place » : **plat chaud 10 min, puis effets ÷2** (A).
2. Personne au fourneau : **ration sèche** à l'échoppe (+25 %, sans effet, jamais au-dessus de 60 %) (A).
3. Cuisiniers : **membres d'une entreprise RESTAURANT** créée et gérée par le staff (plus tard par le gérant, depuis le
   menu Faction) — [`ENTREPRISES.md`](./ENTREPRISES.md).
4. Panier réservé aux cuisiniers, deux plats en 15 s au fourneau : **oui**.
5. Payer = **tendre les ryo** (R puis F) : **oui**.
6. Défaut de faim : **corrigé** dans le même lot.

## 13. Ce qui est codé (14/10, non publié)

**Serveur — ShinobiCore** (`modificateur/`, `entreprise/`, `nourriture/`, API `api/ModificateurService`) :

| Partie | Fichier | Ce que ça fait |
|---|---|---|
| Registre des modificateurs | `modificateur/ModificateurRegistry`, `TypeModificateur` | Modèle de [`../technique/ETATS_JOUEUR.md`](../technique/ETATS_JOUEUR.md) §4.4 : types en config (`modificateurs.types`), empilement (le plus fort remplace, prolonge, cumule, ignore), persistance (gelé / continu / perdu), **au KO les bonus partent**, visibilité, journal, effets multiplicateurs (`regen`, `satiete_perte`, `endurance_regen`, `meditation`), capacités refusées (`SPRINTER`, `COURSE_NINJA`), contrecoup (`ensuite`). Données : `modificateurs.yml`. Service Bukkit `ModificateurService`, lu par l'allure (sprint), la course ninja, ShinobiCombat (récupération d'endurance) et la méditation. |
| Satiété, régénération, FAIM | `nourriture/SatieteService` | Jauge par personnage (`satiete.yml`, 80 % au départ) ; faim vanilla **neutralisée en continu** (baisse naturelle annulée, remise à 20 chaque seconde sauf quand l'ATA ou la douleur la tiennent à 6 — compatibilité gardée) ; perte modulée (assis, marche, trot, sprint mesuré à la vitesse, nage, combat, chakra dépensé) ; figée en KO, porté, AFK (5 min sans bouger le regard), mode staff, créatif ; régénération dès 90 %, 30 s sans coup, ni KO, ni douleur, ni duel, hors terrain d'entraînement, 0,25 %/s × modificateurs, plafond 30 % en ATA ; FAIM = sprint et course ninja refusés, endurance ×0,5, jamais de dégâts. |
| Cuisine | `nourriture/CuisineService` | Panier en main + clic droit sur un poste de **son** restaurant (bloc ou meuble) : 15 s ; plus de 4 blocs, coup, KO ou panier disparu = interrompu (panier gardé) ; fin : −1 panier, **2 plats chauds** (10 min), « +2 Ramen shōyu — chaud », journal `PREPARATION`. |
| Repas | `nourriture/RepasService` | Clic droit plat / ration : durée du plat, ×2 assis (siège ou posture), pas de progrès en trottant, un coup fige 30 s, ranger le plat (changer d'objet en main) le laisse **entamé** (part gardée sur l'objet) ; satiété ajoutée peu à peu ; effets à la fin, **÷2 si le plat était froid au début du repas** ; bol vide laissé en main (ramens) ; journal `REPAS`. |
| Tendre | `nourriture/TendreService`, `/tendre` | Objet en main ou ryo ; offre de 10 s ; à la prise : distance, ligne de vue, objet toujours en main, **place et poids** (règles du ramassage), solde ; noms montrés selon le système « rencontrer » (un inconnu reste « Quelqu'un ») ; journal `DON` (Objet ou Économie). |
| Échoppe | `shop/ShopManager` + `NourritureModule` | Onglet **Vivres** : ration sèche ×2 (30 ryo) pour tous ; paniers par recette pour les cuisiniers seulement (`buyvivre:<id>`). |
| Pilule du soldat | `medic/PiluleConsumeListener` | +100 chakra (inchangé), **+20 % de satiété, endurance +40 % 3 min puis Fatigue 5 min** (au lieu de Vitesse II), une toutes les 10 min, refusée 30 s après un coup ; hématomes gardés. |
| Entreprises | `entreprise/*` | [`ENTREPRISES.md`](./ENTREPRISES.md). |
| Staff | `nourriture/NourritureCommand` | `/nourriture etat [cible]`, `satiete <cible> <0-100>`, `donner <cible> <plat:id\|panier:id\|ration\|bol> [n] [froid]`, `effet <cible> <type> <intensité> <s>`, `retirer <cible> <type>` (journalisé). |

**Objets** : PAPER + modèle du pack Nexo `reborn:<id>` + PDC (`reborn:nourriture`, `recette`, `chaud`, `entame`) :
`panier_ingredients`, `plat_ramen_shoyu`, `plat_ramen_miso`, `plat_dango`, `plat_the_vert`, `plat_bento`,
`ration_seche`, `bol_vide`. Sprites 16×16 et modèles générés par `minecraft/mod-hud/tools/gen_nourriture_textures.py`
dans `minecraft/server-config/nexo/pack/assets/reborn/` ; `nexo/items/reborn_nourriture.yml` (visuels pour le catalogue
Nexo seulement : les objets jouables viennent de ShinobiCore). **Le fourneau n'a pas de modèle** : un bloc (feu de
camp, fumoir) ou un meuble Nexo existant, marqué comme poste.

**Mod — reborn-hud** (`fr.reborn.hud.nourriture`) : `NourritureClient` (canaux `reborn:nourriture`, `reborn:modifs`),
`NourritureHud` (ligne + filet au-dessus de la barre d'action à la place du filet de sprint ; invite « [R] Tendre … »
au-dessus de la personne visée quand on tient un plat ; deux lignes « X te tend … — F prendre · Retour refuser » ;
ligne grise « Tu tends … »), `EffetsHud` (icônes sous les vitals : or = bonus, vermillon = malus, gris = pause ;
minuteur, filet d'1 px, nom 3 s à l'apparition puis au survol en mode interaction ; plat chaud / froid en main ;
régénération en pause ; Affamé ; Faim), `EffetsIcones` (icônes `textures/gui/effets/<nom>.png`, taille lue dans le
fichier : remplaçables par des icônes peintes de 16 ou 32 px, bande verticale = animation, sans toucher au code).
Barre or des vitals = **satiété** (or pâle = ce qu'apporte le repas, repère de 2 px à 90 %, lueur sur la barre rouge
pendant la régénération) ; menu R : « Tendre <objet> », « Donner des ryo ▸ » (remplace « Échanger ») ; F prend,
Retour refuse ; onglet Vivres de l'échoppe ; ligne Entreprise / Satiété dans la fiche du Poste de garde.

**Écarts avec la fiche** : montant des ryo choisi dans un sous-menu (10 à 500, ou « Autre montant… » tapé) plutôt qu'à
la molette ; la pilule est avalée tout de suite (pas de délai d'1 s) ; « manger dans une zone de repos = +2 min de
repos ATA » ne se déclenche plus avec les plats Reborn (seulement avec un aliment vanilla) ; aucune technique n'est
bloquée pendant un repas (le sprint l'est, et le trot n'avance plus le repas) ; les vivres utilisent l'icône « carte »
de l'échoppe ; pilule de sang et pilules Akimichi : après la beta.
