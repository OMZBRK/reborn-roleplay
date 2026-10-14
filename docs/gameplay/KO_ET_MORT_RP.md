# KO, blessures, soins et mort RP

> **Fiche système, à jour au 2026-10-14** (serveur dev : ShinobiCore et ShinobiAbilities du 14/10, `reborn-hud`
> 0.4.175 ; **la douleur, l'exécution unifiée et le portage en douleur du 14/10 sont codés, non publiés**, §1 bis,
> §6 bis, §8). Elle décrit **ce qui est codé**, pas une intention. La réflexion de départ (références GTA RP,
> GMod, Arma…) et les variantes écartées sont dans
> [`archives/conception/PROPOSITION_KO_PAINRP_2026-10-06.md`](../archives/conception/PROPOSITION_KO_PAINRP_2026-10-06.md) ;
> l'alignement sur le Game Plan RedLine (*KO / Pain System*) dans [`../GAMEPLAN.md`](../GAMEPLAN.md) §11.
>
> Code : `ShinobiCore/…/ko/` (KO, ATA, zones, corps, mort RP), `ShinobiCore/…/medic/` (soins),
> `mod-hud/…/ko/` (habillage, QTE, scène de mort).

---

## 1. Le principe en une phrase

**Se battre hors d'un terrain d'entraînement coûte quelque chose, et cette conséquence oblige à jouer une
scène** (médic, repos, hôpital) au lieu de repartir au combat. C'est la réponse au « combat d'arène » que
l'équipe veut éviter.

| Où | Ce qui se passe à 0 PV | Conséquence |
|---|---|---|
| **Terrain d'entraînement** (zone staff) | « Défaite » : on se relève aussitôt | **Aucune** : PV remis au maximum, pas de KO, pas de blessure |
| **Duel consenti** (n'importe où, §4.1) | « Défaite » : le duel s'arrête | **Aucune** : PV et chakra d'avant le duel rendus aux deux |
| **Partout ailleurs** | KO (à terre → inconscient) | PV perdus **persistants**, blessures, ATA au réveil |

---

## 2. Le cycle complet

```
combat hors zone ──► PV ≤ 5 % ──► ⓪ DOULEUR (debout, à bout de forces) ──── soin au-dessus de 5 % ──► normal
                                     │ PV à 0                 │ « Exécuter » (touche F maintenue, 3 s, sans RPK)
                                     ▼                        ▼
              ① À TERRE = AGONIE (45 s, on rampe)        ② INCONSCIENT direct (ni agonie ni Volonté)
                 │  ├─[Volonté]──► SECOND SOUFFLE : debout à 5 % = DOULEUR (+ ATA allégée)
                 │  └─[soin : bandage, paume]──► relevé, en DOULEUR s'il reste ≤ 5 % (+ ATA allégée)
                 │ coup de plus, temps écoulé, échec
                 ▼
              ② INCONSCIENT (5 min) ──── soigné sur place ────► ④ ATA allégée
                      │ les 5 min passent
                      ▼
              ③ TOUJOURS INCONSCIENT + « /hopital » proposé ──► réveil à l'hôpital ──► ④ ATA pleine
                                                                                        │ médic ou repos
                                                                                        ▼
                                                                                     rétabli
   Sur une personne en douleur : la porter sans son accord (elle se débat avec Espace), l'exécuter (§6 bis).
   Sur un corps à terre ou inconscient : ausculter, porter, attraper à la gorge, fouiller, demander un RPK,
   puis l'exécuter (mort RP) une fois le RPK validé (§6, §7, §8).
```

### Tableau des états et des transitions (14/10)

| État | On y entre | On en sort | Ce qu'on peut faire | Ce que les autres peuvent faire |
|---|---|---|---|---|
| **⓪ Douleur** 痛 | PV ≤ 5 % (`ko.douleur.seuil-pct`) hors terrain, hors duel ; second souffle ; relevé d'agonie par un soin | PV > 5 % → normal ; 0 PV → agonie ; exécuté → inconscient | Marcher (×0,6), parler, `/me`, se soigner (bandage) | Porter **sans accord**, exécuter (→ K.O.), soigner, ausculter |
| **① Agonie** 倒 (« à terre ») | 0 PV | Volonté ou soin → douleur ; coup, 45 s, échec → inconscient | **Ramper** (pose allongée, lent, sans saut), chuchoter, `/me` | Porter, gorge, fouiller, soigner, demander un RPK |
| **② Inconscient** 気絶 | Fin d'agonie, exécution en douleur, épuisement de chakra | Soin → réveil (ATA allégée) ; `/hopital` après 5 min → ATA pleine | Chuchoter, `/me`, `/hopital` (après 5 min) | Idem, + exécuter (mort RP) si RPK validé |
| **Porté** | Quelqu'un le porte (douleur, KO, mort) | Le porteur s'accroupit ; en douleur : se débattre (Espace) ; reprendre des forces | En douleur : Espace pour se débattre | — |
| **Mort RP** 屍 | Exécution d'un KO avec RPK validé | Sélection de personnage | Rien (scène de mort, §9) | Porter, fouiller, constater |

### ⓪ Douleur — sous 5 % des PV (14/10, Pain RP du Game Plan RedLine)

Code : `ko/DouleurManager.java` ; mod `ko/KoClient` (caméra, sons), `ko.fsh` (mode 4), `nameplate/Nameplates`
(glyphe au nom), `mixin/CameraKoMixin`, `mixin/SoundEngineKoMixin`.

- **Entrée** : les PV tombent à 5 % du max ou moins (`ko.douleur.seuil-pct`), hors terrain d'entraînement, hors duel,
  hors mode construction. État **dérivé des PV** (persistants) : recalculé chaque quart de seconde et à la reconnexion,
  rien à sauvegarder. Le **second souffle** de la Volonté (debout à 5 %) et un **soin pendant l'agonie** (bandage,
  paume) ramènent donc **en douleur** — c'est la règle RedLine « relevé pendant l'agonie → 5 % + Pain RP ».
- **Restrictions** : démarche ×0,6 (`ko.douleur.vitesse`), **ni course** (faim maintenue à 6 comme l'ATA), ni course
  de chakra, dash, double saut ou saut de chakra, **aucune technique** (via `KoService.isImpaired`, comme l'ATA :
  ShinobiAbilities n'a pas changé), **aucun coup** (mains nues, arme, projectile). En entrant en douleur, on lâche ce
  qu'on portait ou tenait à la gorge, et une exécution qu'on canalisait s'arrête. Duel refusé.
- **Interactions** : une personne en douleur ne peut ni porter, ni attraper (entrées grisées « à bout de forces »
  au menu R), ni exécuter (pas d'invite F) ; elle peut parler, faire des `/me`, se bander.
- **Sortie** : remonter au-dessus du seuil (bandage, paume, repos jusqu'à 30 %) ; à 0 PV → agonie ; exécutée →
  inconsciente.
- **Ce que voient les autres** (v2 du 14/10) : l'état est **intégré au nom RP** (plaque de nom existante, quand on
  regarde la personne à 5 blocs) — le nom se teinte d'un rose pâle et un petit glyphe 痛 est accolé à droite ; plus
  de ligne séparée au-dessus. À portée (3,5 blocs), en la visant : l'invite **« [F] Exécuter »** sous le viseur, dans
  le style de « [F] Ramasser » (§8). Plus la démarche ralentie.
- **Ce que vit la victime** (v2 du 14/10 : « il n'y a pas de visuel poignant ») : **aucune ligne de HUD** (la
  ligne du haut est supprimée) — seulement, à l'entrée, un filet d'or et « à bout de forces » ≈ 1,5 s. La douleur
  se **ressent** : bords qui s'assombrissent à chaque battement de cœur (noir, jamais rouge), vision qui se trouble
  et se **dédouble par à-coups** (toutes les 4 à 6 s), désaturation partielle, léger **vacillement de la caméra**
  qui tressaille au battement, **cœur** audible et **respiration lourde**, **sons du monde étouffés** (÷2). Tout
  ce qui bouge suit le réglage vanilla **« Effets de distorsion »** (0 % = aucun vacillement ni dédoublement). HUD de
  jeu normal. Client sans mod : sous-titre « Douleur — à bout de forces » et une ligne de tchat.

### ① À terre (agonie) — 45 s (`ko.a-terre-secondes`)

C'est l'**agonie** de RedLine (fenêtre courte avant le KO), à la différence qu'on **rampe** au lieu d'être figé.

- Le joueur **rampe** lentement (pose allongée forcée, lenteur II, saut supprimé) : pas de technique, pas d'attaque.
  La reptation **se sent** : chaque poussée fait tanguer la vue (caméra, coupée par « Effets de distorsion »), le
  cœur bat vite, les sons s'étouffent.
- **Plus de HUD** (v2 du 14/10) : viseur, barre d'action, cœurs, faim, vitals RP, endurance, recharges… sont masqués
  (`ko/KoMasqueHud`) ; restent le tchat, les noms et le bloc « À terre » (titre pixel, filet d'or qui se vide autour
  d'un losange qui bat, secondes, « Tu rampes · tu peux chuchoter », ou « Espace pour te relever » avec la Volonté).
- Un **soin** (bandage, paume) le relève, en **douleur** s'il reste sous 5 % des PV, avec une ATA allégée.
- Il **chuchote** (portée 4 blocs, `ko.chuchotement-blocs`) et peut faire des `/me`.
- `/aide` (appel aux médics) n'est **plus mentionné** dans les textes du jeu (décision du 14/10 : « la commande
  n'existe pas » pour les joueurs) ; le code reste en place.
- Un coup de plus, ou la fin du compte à rebours, le fait passer **inconscient**.
- **Un corps à terre ne blesse personne** (14/10) : ni coup, ni projectile, ni technique, ni grappin — refusé par le
  serveur même avec un client modifié (ETATS_JOUEUR §9 étape 0).
- `ko.a-terre: false` désactive cette phase (KO direct, comme avant).

### La Volonté (QTE de relève) — **facultative**

- **N'apparaît que si le personnage connaît la technique `volonte`** (`ko.volonte.technique`), ou pour tout
  le monde si `ko.volonte.pour-tous: true`. Décision du 2026-10-10 : la Volonté est une compétence, pas un
  droit.
- ⚠️ **La technique `volonte` n'existe pas encore dans le catalogue** (`abilities.yml`) : aujourd'hui,
  personne ne l'a. À ajouter, ou passer `pour-tous` à `true` pour tester.
- Fonctionnement : à terre, **Espace** lance une suite de 4 à 7 sceaux (子丑寅卯辰巳午). Un cercle se referme sur
  chaque sceau ; il faut frapper la touche affichée (Espace, clic gauche ou clic droit) au moment où il se pose.
- **Difficulté** = relèves déjà faites + blessures graves − Vigueur / 4 (de 0 à 6). Fenêtre de 250 ms au plus
  facile, 120 ms au plus dur.
- **Anti-triche** : le serveur tire la séquence et vérifie chaque appui (bonne touche, dans la fenêtre + 90 ms de
  marge réseau, durée réelle plausible).
- **Réussite** : « Second souffle » — debout à 5 % des PV, en ATA allégée, et la pire blessure s'aggrave d'un cran.
- **Échec** : inconscient.
- **2 relèves maximum** par cycle de blessure ; le compteur ne revient à zéro qu'après une paume de soin tenue
  30 s. Relèves épuisées → retomber = KO direct, sans phase à terre.
- Canal `reborn:volonte` (`avail`, `start`, `result`, `ok`, `fail`, `deny`). Code : `ko/VolonteQte.java`, mod
  `ko/VolonteHud.java`.

### ② Inconscient — 5 min (`ko.inconscient-secondes`)

- Corps immobile, vision assombrie ; le joueur voit, chuchote et fait des `/me`. Commandes autorisées :
  `/me`, `/hopital`. **Plus de HUD** de jeu (comme à terre) : seul le bloc « Inconscient » (la base du style KO).
- **Pendant les 5 minutes, `/hopital` n'est PAS proposé** — ni au tchat ni à l'écran (la ligne « /hopital » remplace
  la minuterie seulement quand la commande devient utilisable).
- **Après les 5 minutes, il reste inconscient** et `/hopital` apparaît (« Se laisser emmener à l'hôpital »). Il
  n'y a plus de rapatriement automatique (décision du 2026-10-10).
- Cause chakra (chakra à 0) : épuisement, teinte bleue, réveil automatique à la fin du délai. Un seul chemin y mène
  (chakra à 0 ou effondrement de l'épuisement) : en duel c'est une défaite, sur un terrain d'entraînement aussi, en
  mode staff rien (14/10).
- **Déconnexion pendant le KO** (décision du 14/10) : le minuteur — à terre comme inconscient — est **figé** et
  **repris au retour**, là où il en était (`paused-at` dans `ko-state.yml`) ; il est aussi figé pendant un arrêt du
  serveur et pendant l'écran de sélection. Le joueur doit reprendre **ce** personnage (un autre est refusé tant
  qu'il est KO), et une demande de RPK en attente **reste** en attente.

### ③ L'hôpital

- `/hopital` : réveil sur le **premier lit libre** de l'hôpital du village du personnage, à **30 % des PV**
  (le plancher, `ko.plancher-pct`), en **ATA pleine**.
- Ordre de recherche : lit libre d'une zone hôpital du village → entrée de la zone → point `/hopital set`
  du village → spawn du monde.
- Lits : meubles Nexo dont l'identifiant contient `lit`, `bed` ou `futon` (`ko.lits-nexo`), ou lit vanilla.
  Un lit reste occupé 15 min tant que son occupant est à côté (`ko.lit-occupe-minutes`). Les lits se
  posent depuis l'Infirmerie du Poste de garde.
- Les frais d'hôpital se règlent **en RP** (aucune mécanique, décision du 2026-10-06).

### ④ L'ATA — « blessé, en attente de soin »

Posée sur le **personnage** à chaque réveil d'un KO par les PV : **pleine** après l'hôpital, **allégée** si
quelqu'un l'a soigné sur place.

| Interdit pendant l'ATA | Autorisé |
|---|---|
| Courir, course de chakra, dash, double saut, saut de chakra | Marcher |
| **Toute technique** (ninjutsu, taïjutsu, kenjutsu…) | **Coups à mains nues (M1)**, divisés par deux |
| Frapper avec une arme ou un projectile | — |

- Cette règle s'applique **avant** le calcul du coup mortel (14/10) : un blessé en ATA ne peut plus mettre KO avec une
  arme, et son M1 est divisé par deux avant de savoir s'il fait tomber.

- **La peur (FearRP) a été retirée** le 2026-10-10 (« inutile »). Le NLR reste à la charge du joueur (simple
  rappel).
- **On en sort** : par un médic (paume de soin tenue 30 s), ou par le **repos RP** cumulé — méditer n'importe
  où, ou être assis / allongé dans une zone de repos (restaurant, onsen, auberge, hôpital). 20 min pour une
  ATA pleine, 10 min pour une allégée ; manger dans une zone de repos ajoute 2 min. Le repos n'avance que
  si le joueur est actif (anti-AFK, 120 s).
- Retomber KO pendant une ATA la remet à zéro, en pleine.
- Staff : `/ata voir|lever|appliquer <joueur> [pleine|allegee]`.
- Code : `ko/ata/AtaManager.java` ; blocages côté Abilities via `KoService.isImpaired`
  (`JutsuExecutionManager`, `MobilityListener`, `NarutoRun`).

---

## 3. Les PV persistants et le plancher

- **Hors terrain d'entraînement, les PV ne remontent pas tout seuls** (la saturation permanente du serveur
  régénérait la vie ; c'est coupé). `ko.pv-persistants: false` réactive la régénération.
- Le **repos** rend des PV, mais **jusqu'à 30 % seulement**. Au-delà, il faut un médic. Ainsi, personne ne
  reste bloqué à 5 % s'il n'y a pas de médic connecté, mais revenir à pleine forme demande une scène.
- Les mobs (MythicMobs uniquement, pas de faune vanilla) suivent la même règle.

---

## 4. Terrains d'entraînement et zones RP

- Zones posées par le staff : `/zonerp pos1`, `pos2`, `creer <entrainement|repos> <id>`, `suppr`, `liste`,
  `ici` (stockées dans `training-zones.yml`). Les zones **hôpital** se créent depuis l'Infirmerie.
- **Dans un terrain d'entraînement** : PV et chakra mémorisés à l'entrée, combat libre, **défaite à 0 PV
  = PV remis au maximum** (ou chakra plein si c'est le chakra qui a lâché), sans KO ni blessure
  (`ko.defaite-pct: 1.0`). À la sortie, retour aux valeurs d'entrée : on ne peut ni s'y blesser ni s'y soigner.
- **Hors zone**, chaque combat est journalisé (journal universel, catégorie `COMBAT`, action `AGRESSION` : qui a frappé en premier, où — remplace `aggression-log.txt` depuis le 14/10 ; chaque coup est aussi tracé, action `DEGATS`). Le Poste de
  garde lève une alerte quand un joueur enchaîne les combats hors zone.

### 4.1 Le duel consenti (décision du 2026-10-13)

Un « terrain d'entraînement portatif » à deux : il assouplit la règle « combat libre seulement sur les terrains ».
Code : `ShinobiCore/…/duel/DuelService.java`, `DuelCommand.java` ; mod `duel/DuelClient`, `DuelHud`.

1. **Proposer** — menu d'interaction (R, clic sur un joueur) → « Proposer un duel », ou `/duel proposer <joueur>`.
   À 8 blocs au plus (`duel.distance-proposition`). Refusé si l'un des deux n'a pas de personnage, est à terre /
   KO, en ATA, déjà en duel, en mode construction, ou sur un terrain d'entraînement (le combat y est déjà libre).
   Anti-spam : 10 s entre deux propositions d'un joueur, 60 s avant de reproposer à la même personne (levé dès qu'un
   duel commence, pour la revanche). Deux propositions croisées valent acceptation.
2. **Répondre** — la cible voit deux petites lignes au-dessus de la barre d'action (minuscule sceau 決 « X te propose
   un duel », puis « Entrée accepter · Retour refuser · 30 s » en petit, fil d'or d'1 px qui se vide). Touches
   **Entrée** / **Retour arrière** (rebindables : « Duel : accepter / refuser »), ou **R** puis clic sur le mot
   « accepter » / « refuser » (souligné au survol), ou le menu d'interaction sur le proposant
   (« Duel proposé ▸ Accepter / Refuser »), ou `/duel accepter|refuser [joueur]`. Sans réponse en 30 s
   (`duel.expiration-secondes`), la proposition tombe. Le proposant voit une ligne grise « Duel proposé à X — en
   attente · 24 s ».
   Client sans mod : ligne de tchat avec [Accepter] [Refuser] cliquables.
3. **Compte à rebours** 3-2-1 sobre en fondu puis « COMBAT » bref (`duel.compte-a-rebours`) : PV et chakra des deux sont mémorisés ;
   ils ne peuvent pas se toucher ni lancer de technique pendant le décompte.
4. **Combat** — comme sur un terrain d'entraînement : **pas de KO, pas de blessure, pas de journal d'agression,
   pas de FearRP, aucune XP de combat**. Techniques permises (`duel.techniques: false` = mains nues et armes). Une
   annonce discrète part aux joueurs à 24 blocs (« ⚔ X et Y s'affrontent en duel », noms tels que chacun les connaît,
   `duel.annonce.*`). Indicateur fin en haut au centre : « 決 adversaire · 4:56 · abandon » (R puis clic sur
   « abandon ») ; il passe en rouge pâle au-delà de 80 % de la distance maximale.
5. **Fin** — dans tous les cas, **les deux retrouvent exactement leurs PV et leur chakra d'avant le duel**, puis 3 s
   pendant lesquelles ils ne peuvent plus se toucher (`duel.grace-secondes`). Fin : un mot centré en fondu (VICTOIRE /
   DÉFAITE / MATCH NUL / DUEL ANNULÉ), un filet d'or fin, la raison en petit gris (≈ 3 s).

| Cause | Issue |
|---|---|
| PV à 0 (coup « mortel ») ou chakra à 0 | Défaite de celui qui tombe |
| `/duel abandonner` ou bouton ABANDON | Défaite de celui qui abandonne |
| Déconnexion, changement de personnage | Défaite de celui qui part |
| Plus de 30 blocs entre les deux (`duel.distance-max`), autre monde | Annulé |
| Téléportation de plus de 16 blocs (`duel.teleport-max-blocs` ; un shunshin court ne compte pas) | Annulé |
| **Un tiers frappe un duelliste**, ou un duelliste frappe un tiers | Annulé — **ce coup-là suit les règles normales** (KO possible) |
| KO, mort RP, mode construction en cours de duel | Annulé |
| 5 min de combat (`duel.duree-max-secondes`) | Match nul |
| Arrêt du serveur | Annulé (PV d'avant rendus avant la sauvegarde) |

- **Journal staff** : journal universel, catégorie `DUEL` (`logs/duel-AAAA-MM-JJ.log`, remplace `duel-log.txt` le 14/10 : DEBUT, FIN avec issue, raison, vainqueur, lieu) et journal du Poste de garde
  (type `duel`).
- Un duelliste sauvegardé en plein duel (sauvegarde auto, crash) est écrit avec ses **PV d'avant le duel**.
- Limite connue : un coup mortel venu de l'environnement (chute, lave) ou d'un mob pendant le duel compte comme une
  défaite, pas comme un KO.

---

## 5. Blessures et soins

### 5.1 Les blessures

- Blessures **par partie du corps**, avec une gravité (de Légère à Urgente). Chaque nouveau KO **aggrave d'un
  cran** les blessures existantes.
- **Seul `/soigner` les retire** (le temps ne les guérit pas) : silhouette du patient, recette de médicaments,
  quelques secondes d'immobilité par palier. Armoires à médicaments posables (`medic-armoirs.yml`).
- `/osculter` (ou « Ausculter » dans le menu d'interaction) : voir l'état d'une personne.

### 5.2 La paume de soin (`/paume`)

- Le médic vise le patient ; un patient **conscient** accepte d'un clic, un patient à terre ou KO est soigné
  directement.
- Soin **tenu dans le temps** : PV par seconde = 1,5 % des PV max × (1 + 0,10 × Contrôle du médic) × bonus de
  rang. Coût en chakra du médic par seconde. Interrompu si le médic bouge de plus de 1,5 bloc, est frappé ou
  n'a plus de chakra.

| Rang médical | Permission | Soigne jusqu'à | Lève l'ATA |
|---|---|---|---|
| 1 | `shinobicore.medic` | 60 % des PV | après 30 s, s'il n'y a pas de blessure Importante ou Urgente |
| 2 | `shinobicore.medic.expert` | 100 % | toute ATA |

- Un corps KO soigné se relève sur place, en ATA allégée.
- Bandes de gaze (clic droit, accroupi pour soi) : +5 % des PV, jamais au-delà de 30 %, une fois toutes les
  10 s par blessé ; ne lève pas l'ATA.
- Réglages : `medic.paume.*`, `medic.bandage.pv-pct`.

### 5.3 Lien entre KO et médical — **léger, volontairement**

Aujourd'hui, les seuls points de contact sont : la paume ou le bandage qui réveillent un KO au-dessus du
plancher ; la paume tenue 30 s qui lève l'ATA et remet les relèves de Volonté à zéro ; l'aggravation des
blessures à chaque KO. L'encyclopédie médicale, les armoires et les traitements par blessure vivent à part.

---

## 6. Agir sur un corps — le menu d'interaction

**Touche R, puis clic sur un corps à terre ou inconscient.** L'ancien menu en coffre (clic droit) a été
**retiré** le 2026-10-11 : un clic droit sur un corps rappelle seulement d'utiliser R.

| Action | Effet |
|---|---|
| **Ausculter (état)** | Voir les blessures et l'état |
| **Attraper à la gorge** | Le corps est tenu **debout** devant soi, **sans limite de durée** ; on avance lentement ; s'accroupir le lâche |
| **Porter** | Le corps passe sur les épaules ; s'accroupir le dépose |
| **Fouiller** | Ouvrir les affaires du corps |
| **Demander un RPK** | Envoie la demande de mise à mort RP au staff (§7) ; ensuite « RPK demandé — en attente du staff » (grisé) |
| **RPK validé** | Ligne grise « exécuter : F » : l'exécution se fait **à la touche F** (v2 du 14/10, §8), plus au menu |
| **Animation d'exécution ▸** | *Seulement si l'on possède une exécution animée* : choisir « Sans animation » ou une exécution (la courante est marquée « › ») ; elle servira à la touche F. Sinon l'entrée n'existe pas |

- Une entrée **grisée** s'affiche en gris avec sa raison à droite ; un clic ne fait rien (le menu reste ouvert).
- Le mod sait quels corps sont proches, qui est en douleur et où en est le RPK grâce au canal `reborn:corps` (mis à
  jour toutes les ½ s). Les actions passent par `/corps <action> <joueur>`, toujours revérifiées par le serveur
  (distance 3,5 blocs, état de la cible, RPK accordé, exécution possédée ; l'acteur ne doit être ni KO, ni en
  douleur, ni porté, **ni gelé**). On ne porte, ne tient, ne fouille ni n'exécute un joueur **gelé** par le staff, un
  staff en **mode staff** ou un **invisible** (14/10).
- **Le porteur qui tombe KO dépose** aussitôt ce qu'il porte (et lâche la gorge ou l'exécution en cours) ; même chose
  s'il tombe en douleur, meurt ou est gelé (14/10). Les lenteurs du porteur, de la gorge et de l'exécution ne sont plus
  des potions (rien ne reste après un crash).
- Sur un **joueur conscient** (pas en douleur), « Porter » et « Fouiller » sont **grisés** (« à terre ou en douleur »,
  « à terre ») : ils envoyaient des commandes qui n'existent pas ; la demande d'accord de RedLine reste à faire.
- Code : `ko/action/CorpsService.java`, mod `interaction/InteractionMenus.forBody` / `forPain` / `animation`,
  `InteractionItem.disabled`, `ko/CorpsClient.java`, `ko/ExecuterClient.java` (touche F).

### 6 bis. Une personne en douleur — porter sans accord, exécuter (14/10)

Menu R sur une personne en douleur : **Ausculter**, **Porter (sans accord)**, **Animation d'exécution ▸** (si l'on en
possède), puis le menu d'un joueur conscient (saluer, se présenter, message privé, soins des yeux). L'exécution elle-même
se fait à la touche F (§8).

- **Porter sans accord** (`/corps porter`) : elle passe sur les épaules, le porteur est ralenti (lenteur II), un `/me`
  automatique l'annonce (« soulève X de force »). **Elle peut se débattre avec Espace** : chaque appui remplit une
  jauge (8 appuis, `ko.douleur.lutte-appuis`) qui perd un appui toutes les 1,5 s (`ko.douleur.lutte-oubli-secondes`) ;
  pleine, elle se dégage, posée au sol devant le porteur, et ne peut pas être reprise pendant 6 s
  (`ko.douleur.lutte-protection-secondes`). Le porteur voit « Il se débat… ». Si elle reprend des forces (PV au-dessus
  du seuil), elle est reposée ; si elle tombe KO sur les épaules, elle reste portée (inconsciente, ne se débat plus).
  Le porteur la dépose en s'accroupissant (dépôt au sol devant lui, `PorterManager.dropSpot`).
- **Un corps porté ne descend plus seul** en s'accroupissant (correctif du 14/10, vaut aussi pour les corps KO) : seul
  le porteur dépose. Côté victime (v2, « simple et efficace ») : seulement **« Espace · se débattre »** sous un filet
  d'or d'1 px qui se remplit. **Uniquement en douleur** : porté KO, inconscient ou mort, il n'y a ni invite ni lutte.
- **Exécuter** : voir §8.

---

## 7. La mise à mort RP (RPK)

**La victime n'a rien à valider. Le staff tranche, puis c'est le demandeur qui joue la mort.**

1. **Demande** — « Demander un RPK » sur un corps à terre ou KO. Tout le staff en ligne reçoit au tchat :
   `☠ [Staff] RPK : Kazuki Uchiha (Raiden_FR) veut mettre à mort Ren Hyūga (Mika_) · world 640 70 -120
   [Valider] [Refuser] [Y aller]`, et une alerte « mort » apparaît au Poste de garde (Valider le RPK / Refuser /
   Y aller, Modérateur et plus). Sans staff en ligne, la demande attend au Poste de garde.
2. **Validation** — le staff donne une raison (au tchat, ou depuis le Poste de garde). **Le personnage ne
   meurt pas** : le RPK est **accordé** au demandeur, qui est prévenu. Le staff voit « RPK validé … — X doit
   maintenant l'exécuter ».
3. **La dernière action** — le demandeur joue sa scène, puis vise le corps et l'exécute à la touche F (premier appui :
   confirmer, second appui maintenu : exécuter ; animation choisie au menu R). **C'est
   cette action qui tue.** Le personnage est marqué mort et sauvegardé, `CharacterDeathEvent` est émis (les
   addons libèrent ce qu'il détenait : slots du rang S, cf.
   [`APPRENTISSAGE.md`](./APPRENTISSAGE.md) §6), tout est journalisé, et la scène de mort commence (§9).
- **La demande tombe** si la cible n'est plus KO (relevée, soignée, hôpital) ; le staff et le demandeur sont
  prévenus. **Une déconnexion ne la fait plus tomber** (14/10) : le KO est figé hors ligne et la demande attend le
  retour de la victime. Le refus est annoncé à tout le staff.
- Journaux : journal universel, catégorie `KO` (`RPK_DEMANDE`, `RPK_VALIDE`, `RPK_REFUSE`, `RPK_EXPIRE`, `RPK_MORT` ; remplace `kill-log.txt` le 14/10) et journal du Poste de garde.
- Commandes : `/sckillvote approve|refuse|goto <id>` (boutons du tchat). Code :
  `ko/action/KillRequestManager.java`.

---

## 8. Les exécutions (unifiées le 14/10 ; à la touche F depuis la v2)

**Une seule action « Exécuter », à la touche Ramasser (F, rebindable), maintenue** — « comme pour ramasser ». En
visant à 3,5 blocs au plus une cible exécutable, une invite apparaît sous le viseur, dans le style de « [F] Ramasser »
(voile, touche, Inter 8, filet d'1 px) : **« [F] Exécuter »**, ou **« [F] Exécuter · mort RP »** sur un corps KO.
**Maintenir** F lance l'exécution, un **fin anneau d'or** se remplit autour du viseur (« exécution », « · mort RP ») ;
**relâcher** avant la fin l'annule (`/corps annuler`). Côté victime : un anneau rose pâle et « on te met hors de
combat » / « on t'exécute ». **Priorité de F** : objet au sol visé (ramasser) > personne exécutable > rôle normal de
F (techniques, tyrolienne, grappin, main gauche) — F n'est prise que lorsqu'une personne exécutable est visée.
Code : mod `ko/ExecuterClient.java`.

| Cible | Il faut | Effet | Confirmation |
|---|---|---|---|
| **En douleur** (debout, PV ≤ 5 %) | rien (pas de RPK) | **Mise hors de combat** : elle tombe **inconsciente** sur-le-champ, sans agonie ni Volonté. **Ce n'est pas une mort.** | Non : F maintenue (comme le « E maintenu » de RedLine) |
| **KO** (à terre ou inconsciente) | un **RPK validé** pour l'exécuteur (§7) | **Mort RP** (`KillRequestManager.kill`) | **Oui, en deux temps** : un premier appui arme « [F] Confirmer · mort RP définitive » (4 s), le second, maintenu, exécute |
| Debout, ou KO sans RPK | — | Pas d'invite (au menu R : « Demander un RPK » / « RPK demandé ») | — |

- **Qui** : tout joueur avec un personnage, ni KO, ni en douleur, ni porté, ne portant personne, à 3,5 blocs au plus.
  Une seule exécution à la fois par cible.
- **Canalisation** : la victime est tenue debout face à l'exécuteur, les deux sont figés, l'animation choisie joue
  (ou « Sans animation » : 3 s, `ko.execution.canalisation-secondes`), le coup tombe à l'instant prévu
  (`mort-au-tick`). L'animation se choisit au menu R (« Animation d'exécution », seulement si l'on en possède une).
- **Interruption** : F relâchée, l'exécuteur est frappé, s'accroupit (renonce), tombe en douleur ou KO ; la victime en
  douleur est soignée au-dessus du seuil (« Il s'est repris ») ; l'un des deux se déconnecte ; le staff
  (`/execution annuler`).
- « Achever » (`/corps achever`, anciens clients) = exécuter **sans animation** (désormais 3 s de canalisation au lieu
  d'un coup instantané).
- **Staff** : `/execution forcer <joueur|#N°>` (met K.O. inconscient, comme une exécution en douleur — arbitrage),
  `/execution douleur <joueur>` (PV au seuil, pour tester ou arbitrer), `/execution annuler <joueur>` (arrête une
  exécution en cours). La mort RP, elle, passe toujours par un RPK (§7) : pas de commande pour tuer.
- **Journal** : début, interruption (raison), coup (« met K.O. … ») au journal universel (catégorie KO, `logs/ko-AAAA-MM-JJ.log`) et au Poste de garde, via `ko/KoJournal` ; la mort
  RP au journal universel (`RPK_MORT`, catégorie KO).
- Les animations sont des **cosmétiques vendables** (« animation stylée »), valables pour les deux cas.
- Catalogue `executions.yml` (dossier de ShinobiCore, créé au premier démarrage) : pour chaque exécution, un
  nom, l'animation du tueur et celle de la victime (clés d'emotes EmoteCraft), la durée et l'instant où le coup
  tombe. Rechargé par `/execution recharger`.
- Possession **par compte** (`executions-joueurs.yml`) : `/execution donner|retirer <joueur> <id>`, ou la
  permission `shinobicore.execution.<id>`. Une entrée `gratuite: true` appartient à tout le monde. « Sans animation »
  est toujours disponible.
- ⚠️ **Aucune animation n'existe encore.** Les 3 exemples (Coup de grâce, Lame dans le dos, Étranglement)
  pointent vers des emotes à créer (`execution_coup_de_grace`, `…_victime`…). Sans fichier, la scène se joue
  sans animation. **La vente en boutique n'est pas branchée.**

---

> **Porter un corps (correctif du 13/10)** : au dépôt (accroupi), le corps est posé **au sol devant le porteur**
> (`PorterManager.dropSpot`) ; avant, il restait figé à la hauteur des épaules, en l'air. Même correctif pour la
> prise à la gorge.

## 9. La scène de mort

Le joueur dont le personnage meurt **reste sur place**, allongé, et **ne peut plus rien faire** : ni bouger,
ni parler, ni lancer de commande, ni ouvrir d'inventaire, ni être blessé.

1. Ses yeux battent puis **se ferment** ; écran noir.
2. **10 s après la mort** (`mort-rp.scene-min-secondes`, 60 avant le 13/10) : « **Assister à votre réelle fin ?** » Oui / Non.
3. **Non** → il part : le corps reste au sol et il arrive à la **sélection de personnage**.
4. **Oui** → ses yeux se rouvrent sur une **vue grisée, sans aucun HUD, en bandes de cinéma** ; il regarde la
   suite (glisser la souris tourne la vue autour du corps) et part quand il veut avec **Échap**.
   **Tant qu'il regarde, le corps, c'est lui** : les autres peuvent le **porter, le déposer, le fouiller**
   (menu d'interaction : Constater la mort, Porter, Fouiller). S'il part, le mannequin apparaît là où le corps a
   été laissé.
- En partant (ou en se déconnectant), son **corps reste au sol 15 min** (`mort-rp.corps-minutes`) : un
  mannequin allongé, **habillé de l'apparence Reborn** du personnage (le mod reçoit l'apparence par
  `reborn:corps` et la compose).
- Code : `ko/action/DeathScene.java`, mod `ko/DeathScreen.java`, `ko/CorpsClient.java`,
  `mixin/ClientMannequinSkinMixin.java`.

---

## 10. Habillage visuel et sonore (KO-6)

- **Un seul style pour toute la chaîne** (v2 du 14/10, « visuellement c'est pas ça », sauf « inconscient ») : tout
  dérive de l'écran « Inconscient » — **titre pixel centré, filet d'or d'1 px autour d'un losange, temps en Inter 9,
  sous-texte gris en Inter 7**, rien d'autre (pas de plaque, pas de kanji, pas de voile rouge). Transitions (douleur,
  à terre, inconscient, réveil, hôpital, rétabli, second souffle, défaite) = ce bloc en fondu ≈ 3 s ; à terre = le
  bloc plus bas ; porté = le filet et « Espace · se débattre » ; exécution = un anneau autour du viseur ; ATA = une
  ligne « Blessé », filet des minutes de repos, détail gris. Planche avant / après :
  `Maquettes/gameplan/06_ko/v2/planche_avant_apres.png`.
- **À terre et inconscient : plus de HUD de jeu** (`ko/KoMasqueHud`, viseur, barre d'action, cœurs, faim, armure,
  air, expérience, effets, boss, tableau, message d'action, vitals RP, endurance, recharges, concentration, allure,
  notifications, ramassage, interaction, duel) ; restent le tchat, les titres, la liste, les bulles, les noms.
- Filtre plein écran `ko.fsh` (mod) : **à terre (agonie)** désaturation + **bords qui s'assombrissent** au rythme du
  cœur, vision qui tangue ; **inconscient** noir et blanc, flou, grain, paupières ; **douleur** bords noirs qui battent,
  vision troublée et dédoublée par à-coups, désaturation partielle (§1 bis) ; **ATA** pulsation légère ; **cause
  chakra** bords bleu nuit. Priorité des filtres : KO > Byakugan > Météo. Caméra (`CameraKoMixin`) et sons étouffés
  (`SoundEngineKoMixin`) en douleur et à terre ; tout ce qui bouge suit « Effets de distorsion ».
- **14/10 : plus de voile rouge sur les bords** (effet « RPG » rejeté, [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md)
  §3) : à terre et en ATA, la vignette rouge est remplacée par un léger assombrissement.
- État au **nom RP** (plaque de nom, `nameplate/Nameplates`) : nom teinté et petit glyphe accolé — 痛 douleur, 倒 à
  terre, 気絶 inconscient, 屍 mort ; plus d'étiquette séparée (l'ancienne `EtatTete` est supprimée).
- Le HUD du KO évite le chat et les jauges. Les titres vanilla ne s'affichent que pour un client sans mod.
- Canaux `reborn:ko` (état) et `reborn:ko_event` (cartes). Code : `ko/KoHudSync.java`, mod `ko/KoHud.java`.
- La transition « Inconscient » se place au-dessus du bloc (plus de superposition).

---

## 11. Réglages (`ShinobiCore/config.yml`)

| Clé | Défaut | Rôle |
|---|---|---|
| `ko.pv-persistants` | `true` | Pas de régénération naturelle hors terrain |
| `ko.defaite-pct` | `1.0` | Fraction rendue après une défaite à l'entraînement |
| `ko.journal-agressions.*` | activé, 5 min | Journal des combats hors zone |
| `ko.a-terre`, `ko.a-terre-secondes` | `true`, `45` | Phase à terre |
| `ko.inconscient-secondes` | `300` | Durée avant que `/hopital` soit proposé |
| `ko.volonte.technique`, `ko.volonte.pour-tous` | `volonte`, `false` | Qui a la Volonté |
| `ko.plancher-pct` | `0.30` | PV max rendus par le repos, PV au réveil à l'hôpital |
| `ko.chuchotement-blocs`, `ko.aide-portee` | `4`, `48` | Portées |
| `ko.lits-nexo`, `ko.lit-occupe-minutes` | `[lit, bed, futon]`, `15` | Lits d'hôpital |
| `ko.ata.repos-minutes-pleine` / `-allegee` | `20` / `10` | Repos nécessaire |
| `ko.ata.anti-afk-secondes`, `ko.ata.repos-pv-pct-par-10s` | `120`, `0.01` | Repos |
| `ko.douleur.actif`, `ko.douleur.seuil-pct`, `ko.douleur.vitesse` | `true`, `0.05`, `0.6` | Douleur : seuil de PV, démarche |
| `ko.douleur.lutte-appuis`, `ko.douleur.lutte-oubli-secondes`, `ko.douleur.lutte-protection-secondes` | `8`, `1.5`, `6` | Porté en douleur : se débattre |
| `ko.execution.canalisation-secondes` | `3` | Exécution sans animation |
| `mort-rp.scene-min-secondes`, `mort-rp.corps-minutes` | `10`, `15` | Scène de mort |
| `duel.actif`, `duel.techniques` | `true`, `true` | Duels permis ; techniques permises en duel |
| `duel.distance-proposition`, `duel.distance-max`, `duel.teleport-max-blocs` | `8`, `30`, `16` | Distances (blocs) |
| `duel.expiration-secondes`, `duel.compte-a-rebours`, `duel.duree-max-secondes`, `duel.grace-secondes` | `30`, `3`, `300`, `3` | Délais |
| `duel.cooldown-secondes`, `duel.cooldown-couple-secondes` | `10`, `60` | Anti-spam |
| `duel.annonce.actif`, `duel.annonce.rayon` | `true`, `24` | Annonce RP aux alentours |
| `medic.paume.*`, `medic.bandage.pv-pct` | cf. config | Soins |

> Une clé absente du `config.yml` **du serveur** prend la valeur par défaut du code. Les réglages de
> l'Infirmerie faits en jeu vont dans `ko-reglages.yml` (perdus par `/sc reload` jusqu'au redémarrage).

---

## 12. Reste à faire

| Sujet | État |
|---|---|
| **Douleur, exécution à la touche F, portage en douleur, style KO unique, HUD masqué** (14/10, v2 après retours) : codés (ShinobiCore + `reborn-hud`), **non publiés** ; captures `Maquettes/gameplan/06_ko/v2/` ; banc `REBORN_KO_DEBUG=douleur` (captures `Maquettes/gameplan/06_ko/`) ; à tester à deux comptes | à publier puis tester |
| Animation de douleur (démarche « blessé ») | non faite : la douleur ne change que la vitesse |
| Demande de portage / de fouille d'une personne consciente (accord, Espace pour se dégager — RedLine) | à faire (entrées grisées en attendant) |
| Tester la 0.4.169 à **deux comptes** (RPK, exécution, scène de mort, corps) | à faire |
| **Duel consenti** (§4.1) : codé le 13/10 (ShinobiCore, ShinobiAbilities, `reborn-hud`), **non publié** ; banc de captures `REBORN_DUEL_DEBUG=1` (`run/screenshots/duel_*.png`) ; à tester à deux comptes | à publier puis tester |
| Créer la technique `volonte` (ou décider `pour-tous`) | à faire |
| Animations EmoteCraft des exécutions | à créer (staff animation) |
| Vente des exécutions en boutique | non branchée |
| Coma au 3ᵉ KO pendant une ATA (réveil par médic rang 2) | compteur prêt, effet non branché |
| Saignement pendant la phase à terre | non fait |
| `MedicalService` (antidotes, coma) | non fait — chantier Médical (Sprint 3) |
| Animations de chute / reptation / relève | non faites |
| Carte « K.O. » superposée à « INCONSCIENT » | défaut connu |
