# Apprentissage des techniques — parchemins, bibliothèques, séances

> **Fiche système, à jour au 2026-10-13** (bibliothèques v2, puis vols et secrets, codés le 13/10, §3, non déployés). Lots 1 à 3 en production (`reborn-hud` 0.4.166 et plus), lot 4
> (mort RP) et concentration du chakra sur le serveur dev avec la 0.4.169. Les règles ont été décidées avec
> l'équipe les 8 et 9 octobre ; la réflexion d'origine est dans
> [`archives/conception/PROPOSITION_PARCHEMINS_2026-10-08.md`](../archives/conception/PROPOSITION_PARCHEMINS_2026-10-08.md).
>
> Code : `ShinobiAbilities/…/parchemin/` (catalogue, bibliothèques, slots S, séances, commandes),
> `ShinobiAbilities/…/techniques/` (objet parchemin, mini-jeux, concentration), mod `…/parchemin/` (écrans).

---

## 1. Les règles du jeu

- **Ce qui s'apprend par parchemin** : Taïjutsu (Gōken), Kenjutsu et Ninjutsu, au **tirage commun**. Les techniques
  **secrètes** (rang S / Hiden, kekkei, jūken, kinjutsu, juinjutsu, techniques de clan et ANBU) ne sortent que des
  bibliothèques dont la **réserve** les contient (§3.8) : archives d'un clan, quartier ANBU, réserve du palais. Le
  reste de l'apprentissage de clan (système « vraiment immersif ») est à concevoir. Senjutsu et « autres » restent
  hors des bibliothèques. Réglable : `parchemins.categories`, `parchemins.exclues`, `parchemins.secrets`.
- **Le parchemin est un vrai objet** : il se vole, s'échange, se brûle, se pose dans un coffre. Il est
  **unique** (pas de recopie) et non empilable.
- **Deux sources** : les **événements RP** (le staff place des rouleaux dans des coffres, sur des tables) et les
  **bibliothèques** (§3 : rouleaux déposés par les joueurs, et arrivages tirés au sort).
- **Deux façons d'apprendre** : seul avec le parchemin, ou avec un **maître** présent (la séance compte
  double). Le rang S passe surtout par la transmission RP et finit toujours par une validation du staff.
- **Plus le rang est haut, plus c'est long et dur.**
- **L'apprentissage est libre** : aucune stat ne bloque l'accès à une technique (décision du 2026-09-23).

---

## 2. L'objet parchemin

| Rang | Modèle 3D (pack Nexo) | Rouleau SNR d'origine | Poids dans la sacoche |
|---|---|---|---|
| D (et E) | `reborn:parchemin_d` | SNRRouleauVert | 0,2 kg |
| C | `reborn:parchemin_c` | SNRRouleauJaune | 0,2 kg |
| B | `reborn:parchemin_b` | SNRRouleauRouge | 0,2 kg |
| A | `reborn:parchemin_a` | SNRRouleauViolet | 0,5 kg |
| S | `reborn:parchemin_s` | SNRRouleauTechInterdites | 0,8 kg |

- **Modèles de l'équipe** (« Pack Cosmétique Ninja », dossier projet `Modélisation/Pack Cosmétique Ninja/Rouleau -
  FULL` et `Rouleau des Techniques Interdites - FULL`), adoptés le 2026-10-12 à la place des modèles bbforge.
  **Livrés par le pack Nexo** (`minecraft/server-config/nexo/pack/assets/reborn/` : `items/`, `models/item/`,
  `textures/item/` `parchemin_*`), comme tous les objets 3D du serveur ; ils ne sont plus dans le mod. Le
  serveur pose `item_model = reborn:parchemin_<rang>` (`ParcheminItems`). Réglages d'affichage revus : taille en main et au sol réduite, rouleau interdit rendu visible en première
  personne (il était prévu porté dans le dos). La couleur de rang des écrans suit ces rouleaux
  (`ScrollArt.rankColor`), et les écrans (bibliothèque, simulation staff) affichent le **même modèle 3D**
  (`ScrollArt.item`).
- L'objet porte la technique dans ses données (`parchemin_ability`, `parchemin_id`). Code :
  `techniques/ParcheminItems.java`.
- **Clic droit rouleau en main** : écran de lecture (le rouleau se déroule entre ses baguettes : technique,
  signes, perles des séances faites, délai avant la prochaine) avec le bouton **« S'entraîner »**.

---

## 3. Les bibliothèques (v2)

> **Bibliothèques v2**, codées le 2026-10-13 avec les choix par défaut de l'entrée « Bibliothèques v2 » de
> [`DECISIONS.md`](../pilotage/DECISIONS.md) (révisables). Spec : [`BIBLIOTHEQUES_V2_PROPOSITION.md`](./BIBLIOTHEQUES_V2_PROPOSITION.md).
> **Vols et secrets** ajoutés le même jour (§3.7, §3.8 ; entrée « Vols possibles, secrets paramétrables » de
> `DECISIONS.md`). **Pas encore déployé** sur le dev ni publié (ShinobiAbilities, ShinobiCore et `reborn-hud` à
> livrer ensemble).

### 3.1 Deux étagères

| Étagère | Ce qu'il y a dessus | Durée |
|---|---|---|
| **Fonds** | Les rouleaux **déposés** par les joueurs ou le staff (9 cases par meuble) | Permanent : le rouleau reste tant que personne ne le prend |
| **Arrivages** | Le **tirage au sort** partagé (1 à 9 rouleaux, 6 par défaut) : **tirage commun** (catalogue hors secrets) + **réserve de secrets** de la bibliothèque (§3.8) | Renouvelé toutes les 3 h par défaut (réglable) |

- Le rouleau déposé garde **son identité complète** : l'objet est conservé tel quel (sérialisé), avec sa technique et
  son `parchemin_id`. Un **rang S déposé garde son slot** (§5) ; le reprendre rend le même objet.
- Arrivages : chaque technique a son pourcentage ; si plus de rouleaux sortent que de cases, on garde les plus rares.
  Un rouleau pris disparaît pour tout le monde jusqu'au tirage suivant ; un S ne sort que s'il reste un slot.
- **On peut tout emporter** : seule la limite de poids de la sacoche compte, sur les deux étagères.

### 3.2 Les sept types

| Type | Propriétaire | Qui prend (membres) | Qui dépose | Accès par défaut | Arrivages par défaut |
|---|---|---|---|---|---|
| **Village** | village (facultatif) | personnages du village | tout le monde | **libre** | oui, modèle Publique |
| **Académie** | village (facultatif) | personnages du village | membres de grade **Chūnin et plus** | membres | oui, modèle Publique |
| **Clan** | nom du clan (obligatoire) | membres du clan (`clan` du personnage) | membres du clan | membres | oui, **réserve du clan seulement** (tirage commun coupé) |
| **ANBU** | village (facultatif) | permission `shinobiabilities.biblio.anbu` | membres | membres | oui, modèle Privée |
| **Palais du Kage** | village (facultatif) | grade **Kage**, ou permission `shinobiabilities.biblio.palais` (conseil, bras droit) | membres | membres | oui, modèle Privée |
| **Hôpital** | village (facultatif) | rang médical (`shinobicore.medic`) | membres | membres | oui, techniques **médicales** seulement (Iryō ; toutes s'il n'y en a pas) |
| **Personnelle** | le personnage | le propriétaire et ceux qu'il **autorise** | le propriétaire | membres | **non** |

- Il n'existe pas de fonction ANBU dans ShinobiCore (l'ANBU n'est plus un grade) : d'où la permission dédiée.
- **Accès `membres`** : un non-membre **voit** les rouleaux (petit cadenas sur chacun, sceau 秘 vermillon dans
  l'écran, survol explicatif) et ne peut pas les prendre… mais il peut tenter de les **voler** (§3.7), sauf si la
  bibliothèque l'interdit. Le staff peut toujours ouvrir l'accès à tous un temps : `/bibliotheque ouvrir <minutes>`
  (0 = refermer). **Accès `libre`** : tout le monde prend, le contrôle est RP.
- Le **staff** est membre de toutes les bibliothèques et peut déposer partout.
- Les **pourcentages** gardent les modèles Publique / Réservée / Privée, devenus un réglage *interne* du type (le
  staff peut toujours les ajuster par rang ou par technique).

### 3.3 Déposer, prendre, journal

- **Déposer** : bouton **« Déposer »** sous le meuble, **sur toutes les bibliothèques** et sur les deux onglets (il
  ouvre le fonds), ou clic sur une case vide du fonds, puis choix d'un rouleau de la sacoche : barre d'action,
  inventaire et **stockage du sac** (API ShinobiCore `bagStorage` / `takeFromBag` ; avec un ShinobiCore plus ancien,
  seuls barre d'action et inventaire comptent). Quand on ne peut pas déposer, le bouton est **grisé** et son survol
  (ou un clic) dit pourquoi (« Seuls les membres du clan Uchiha déposent ici. », « Seul Itachi Uchiha dépose ici. Ta
  propre bibliothèque : /bibliotheque perso. »…). Pour créer la sienne : §3.5.
- **Prendre** : clic sur le rouleau, dans la limite de poids.
- **Journal** : chaque dépôt, chaque prise et chaque **vol** (qui — nom du personnage —, quand, quelle étagère, quel
  rouleau ; 120 dernières lignes) ; bouton 記 de l'écran, visible par le **propriétaire** (bibliothèque personnelle) et le
  **staff**, ou `/bibliotheque journal`. Les prises et dépôts de rang A et S, et les prises par un non-membre pendant
  une ouverture, vont aussi au journal staff (ShinobiCore).

### 3.4 Lecture sur place

- Devant une bibliothèque **Clan, Académie ou Palais** (8 blocs), un **membre** peut faire une séance sur un rouleau
  du **fonds** sans l'emporter : clic droit sur le rouleau → écran de lecture « sur place » → « S'entraîner ».
- Il faut un **maître** de la technique à portée (règle du §4) : la séance compte donc double.
- Le rouleau **reste sur l'étagère** : ni consommé quand la technique est apprise, ni perdu par l'easter egg.
  La séance ne compte pas si le rouleau a quitté l'étagère ou si l'élève s'est éloigné.
- Le **rang S ne s'étudie pas sur place** (slot et validation staff liés à l'objet) : il faut l'emporter.

### 3.5 Bibliothèque personnelle

- `/bibliotheque perso` : le bloc ou le meuble visé devient **sa** bibliothèque (une par personnage).
- `/bibliotheque autoriser <joueur>` / `retirer <joueur>` (nom du compte ou du personnage, joueur connecté) ;
  sans argument : la liste des autorisés.
- `/bibliotheque perso retirer` : la défait ; les rouleaux du fonds reviennent dans la sacoche.

### 3.6 Modèles de pourcentages (par technique, à chaque tirage)

| Modèle | D | C | B | A | S |
|---|---|---|---|---|---|
| Publique (Village, Académie, Hôpital) | 35 % | 12 % | 3 % | 0,3 % | 0,02 % |
| Réservée (anciennes bibliothèques « Réservée ») | 30 % | 18 % | 7 % | 1 % | 0,08 % |
| Privée (ANBU, Palais) | 20 % | 22 % | 12 % | 3 % | 0,3 % |

Le staff peut aussi fixer un pourcentage **par technique** (recherche, filtres Tai / Ken / Nin / nature) et
lancer une **simulation** (moyenne sur 10 000 tirages). Ordre de grandeur sur 6 rouleaux, modèle Publique :
3 rouleaux D, 0,7 C, 0,12 B ; au moins un A dans 1,2 % des tirages, un S dans 0,03 %.

### 3.7 Le vol

- **Qui** : un non-membre (personnage actif) d'une bibliothèque en accès `membres` dont le **vol est permis** (réglage
  staff, permis par défaut). Le staff, lui, prend directement (et garde `/bibliotheque ouvrir`).
- **Comment** : dans l'écran, « Prendre » devient **« Voler »** (fiche : « Clic · voler · 12 s immobile »). Le clic lance
  une **fouille** : le reste de l'étagère s'éteint, une barre vermillon court sous le rouleau, « Ne bouge pas : un pas,
  un coup, et la fouille échoue. » Un bruit de pages tournées sort du meuble toutes les ~1,2 s (on peut se faire
  repérer).
- **Durée** = durée de base de la bibliothèque (6 s par défaut, 2 à 30 s dans les réglages) × facteur du rang
  (`parchemins.vol.facteurs`, D 1 · C 1,5 · B 2 · A 3 · S 4) : 6 / 9 / 12 / 18 / 24 s par défaut.
- **Interrompue** si le voleur bouge (plus de 0,45 bloc, `parchemins.vol.tolerance-mouvement`), s'éloigne, est
  **frappé** (même si un système de combat annule les dégâts vanilla), ferme l'écran ou renonce (Échap), se
  déconnecte ; ou si quelqu'un a pris le rouleau entre-temps. Rien ne part.
- **Réussite** : le rouleau part dans la sacoche (au sol à ses pieds si elle est pleine), **marqué « Volé à
  &lt;bibliothèque&gt; »** (lore + donnée `parchemin_vole` ; même objet : technique, identifiant, slot S) ; la fiche du
  rouleau, s'il est redéposé ailleurs, affiche « Volé à ». Journal : ligne **VOLE** avec le personnage voleur.
- **Alertes** : message discret aux **membres connectés** (règles du type, staff exclu), en gris italique : « Quelqu'un
  fouille les archives de « X »… un rouleau de rang B a disparu. » ; **nominatif** si la bibliothèque le règle
  (« Kazuki Uchiha a fouillé… »). Au staff : **alerte `vol`** du Poste de garde (ShinobiCore, score 30 pour un A,
  60 pour un S, bouton « Y aller ») + ligne du chat staff + journal staff « Vol de parchemin ».
- **Réglages par bibliothèque** (écran staff, page Réglages, bloc « Vol ») : Permis / Interdit, alerte Anonyme /
  Nominative, durée de base. Stockage : `vol`, `vol-secondes`, `vol-nominatif`.

### 3.8 Les secrets (réserve)

- **Technique secrète** (`ScrollCatalog.isSecret`) : marque `secret: true|false` d'abilities.yml d'abord (elle
  l'emporte), puis `parchemins.secrets.techniques`, le **rang S (Hiden)** (`rang-s: true`), les catégories secrètes
  (`kekkei/`, `taijutsu/juken`, `autres/kinjutsu`, `autres/juinjutsu`), les techniques **de clan** (marque `clan:`,
  prérequis `{ type: clan }`, ou `parchemins.secrets.clans` : Uchiha → Sharingan, Hyūga → Byakugan et Jūken, Senju →
  Mokuton) et **ANBU** (marque `anbu: true` ou `parchemins.secrets.anbu`, kinjutsu et juinjutsu par défaut).
- Une technique secrète ne sort **jamais** du tirage commun : Village, Académie, Hôpital (et les autres types tant
  qu'elle n'est pas dans leur réserve) ne la tirent pas.
- **Réserve** : chaque bibliothèque a une liste de techniques exclusives avec **leur propre %** (défaut par rang :
  `parchemins.secrets.pourcentages`, D 30 · C 20 · B 10 · A 3 · S 0,5 %). Elle s'ajoute au tirage commun (et son %
  l'emporte si la technique est dans les deux). Le rang S ne sort toujours que s'il reste un slot (§5).
- **Réserve par défaut** (calculée tant que le staff n'y touche pas) : **Clan** → toutes les techniques de son clan ;
  **ANBU** → techniques ANBU ; **Palais** → rangs A et S (Hiden) des trois branches, hors clan et ANBU ; autres
  types → vide.
- **Tirage commun** oui / non par bibliothèque : coupé par défaut pour le **Clan** (ses arrivages = ses secrets
  seulement ; les arrivages du Clan sont maintenant actifs par défaut pour les nouvelles bibliothèques, les
  anciennes gardent leur réglage).
- **Écran staff, onglet « Secrets »** : recherche au clavier (nom, identifiant, clan…), filtres Toutes / Réserve /
  Secrètes / Clan / ANBU / Rang S, bouton **Ajouter** / **Retirer**, % propre (− +), état « Réserve par défaut du
  type » ou « Réserve réglée » avec retour **Par défaut**, moyenne de rouleaux de la réserve par tirage ; la
  **simulation** (aperçu et 10 000 tirages) tient compte de la réserve et du tirage commun.
- Stockage : `tirage-commun`, `reserve: { technique: % }` (absent = réserve par défaut ; `reserve-vide: true` = réserve
  vidée exprès).

### 3.9 Commandes et stockage

| Commande | Qui | Effet |
|---|---|---|
| `/bibliotheque creer <nom> <type> [propriétaire]` | staff | bloc ou meuble visé ; nom entre guillemets ou avec des `_` ; type `village / academie / clan / anbu / palais / hopital / personnelle` ; propriétaire = village, clan (obligatoire), ou joueur (personnelle) |
| `/bibliotheque regler` | staff | écran de réglages (type, accès, arrivages, rouleaux, délai, % par rang et par technique, simulation) |
| `/bibliotheque ouvrir <minutes>` | staff | accès ouvert à tous un temps (0 = refermer) |
| `/bibliotheque tirage` · `suppr` · `liste` | staff | nouveau tirage · suppression (le fonds est remis dans la sacoche du staff) · liste (type, propriétaire, accès, fonds) |
| `/bibliotheque perso [retirer]` | joueur | sa bibliothèque personnelle |
| `/bibliotheque autoriser` · `retirer <joueur>` | propriétaire | partage de sa bibliothèque personnelle |
| `/bibliotheque journal` | propriétaire, staff | journal de la bibliothèque visée (ou la sienne) |

- La commande `/bibliotheque` passe à la permission `shinobiabilities.use` ; les sous-commandes staff exigent
  `shinobiabilities.staff`. Nouvelles permissions (défaut `false`) : `shinobiabilities.biblio.anbu`,
  `shinobiabilities.biblio.palais`.
- Stockage : `bibliotheques.yml` (type, proprietaire, proprietaire-nom, acces, arrivages, ouvert-jusqu-a,
  autorises, fonds — avec `vole-a` —, journal, vol, vol-secondes, vol-nominatif, tirage-commun, reserve, plus les
  réglages du tirage). `/bibliotheque liste` affiche aussi la taille de la réserve et le réglage du vol. **Migration automatique** au premier chargement des
  bibliothèques v1 : modèle Publique → **Village** (libre), Réservée → **Académie**, Privée → **Palais** (accès
  membres) ; arrivages conservés, pourcentages conservés. ⚠️ Une ancienne bibliothèque « Privée » devient donc
  réservée au Kage (et à la permission palais) : la repasser en `libre` dans les réglages si ce n'est pas voulu.

---

## 4. Apprendre : les séances

| Rang | Séances à réussir | Épreuve |
|---|---|---|
| D | 1 | mini-jeu du rang D |
| C | 3 | … |
| B | 6 | … |
| A | 12 | … |
| S | 20 | … puis validation du staff |

- **« S'entraîner »** lance l'épreuve **dans le monde** (pas dans un menu) :
  - **Ninjutsu** : concentration du chakra (§4.1), puis enchaînement de **mudras** ;
  - **Kenjutsu** : concentration du chakra, puis **pompes** ;
  - **Taïjutsu** : **pompes** seulement (pas de chakra).
- **Une séance par technique toutes les 20 h** (`parchemins.seance-heures`), réussie ou ratée.
- **Échec** = séance perdue, sans avancer. Rien d'autre.
- **Maître à portée** (6 blocs) : une séance réussie compte **double**. Un maître = maîtrise de la technique
  **≥ 80** (jauge 0-100 qui monte à chaque lancer : Novice 0-39, Avancé 40-79, Maître 80-100) **et** la
  connaître depuis **≥ 14 jours**. Réglages `parchemins.maitre.*`.
- **Il faut le rouleau en main** du début à la fin (sauf lecture sur place, §3.4). La progression est liée au **personnage** : on garde ses
  séances même si on perd le rouleau, mais il en faut un pour continuer.
- **Easter egg** : 0,2 % de chance par séance de perdre le rouleau (déchiré, emporté par le vent…), avec un
  message RP (`parchemins.perte-rare`).
- **Technique apprise** : le rouleau est consommé. **Rang S** : à la dernière séance, le rouleau est remis au
  staff et une **demande de validation** s'ouvre (`/parchemin attente | valider | refuser <joueur> <technique>`).
- Chaque séance réussie et chaque technique apprise rapportent de l'XP (invisible), multipliée par le rang (cf.
  [`STATS_ET_PROGRESSION.md`](./STATS_ET_PROGRESSION.md) §3).
- Staff : `/parchemin seance <joueur> <technique> [n]` accorde (ou retire, n négatif) des séances ;
  `/parchemin donner <joueur> <technique>` donne un rouleau.
- Stockage : `parchemins-progression.yml` (séances par personnage et par technique, date d'apprentissage pour
  l'ancienneté du maître, demandes S en attente). Code : `parchemin/SeanceService.java`,
  `techniques/LearningMinigame.java`.

### 4.1 La concentration du chakra — ⚠️ visuel non validé

- **Idée de l'équipe** : concentrer son chakra en gardant un trait **en équilibre dans une zone**, jouable
  **dans le monde** (on peut bouger, comme un mini-jeu de serveur), pour tout ce qui demande du chakra **sauf
  le Taïjutsu**. Le canon sert de référence (étapes d'apprentissage du Rasengan, du Chidori…).
- **Contrôle** : la rotation de la tête déplace le trait ; des dérives le poussent hors de la zone d'or.
- **Le corps doit rester immobile** : marcher vide la stabilité (−0,8 / s), courir la vide presque d'un coup, un
  saut coûte 30 %, un coup reçu 35 %.

| Rang | Demi-largeur de zone | Temps à tenir | Dérive | Tolérance hors zone | Rafales | Zone mobile |
|---|---|---|---|---|---|---|
| D | 0,40 | 8 s | 0,95 | 3,0 s | non | non |
| C | 0,30 | 12 s | 1,10 | 2,5 s | non | non |
| B | 0,22 | 14 s | 1,20 | 2,2 s | oui | non |
| A | 0,16 | 16 s | 1,25 | 2,0 s | oui | oui |
| S | 0,12 | 18 s | 1,35 | 2,0 s | oui | oui |

- Un maître présent élargit la zone de 25 % (`techniques.concentration.bonus-maitre`).
- Essai sans enjeu : `/parchemin concentration [D|C|B|A|S] [maitre]`.
- **État** : la mécanique serveur est faite et **active de fait sur le dev depuis la 0.4.169**. Le rendu client
  (plaque au-dessus de la barre d'action, `parchemin/ConcentrationHud.java`) a été jugé « vraiment moche » ;
  deux pistes sont proposées (ensō A, recommandée ; trait B) **et n'ont pas été tranchées**. Code :
  `techniques/ConcentrationGame.java`, canal `reborn:parchemin` (message `conc`).

---

## 5. Les slots du rang S

- Chaque technique S a un **nombre maximum de détenteurs** : 2 par défaut (`parchemins.slots-s`), réglable par
  technique (`parchemins.slots-par-technique: { rasengan: 1 }`).
- Occupent un slot : **chaque parchemin S qui existe** (`rouleau:<uuid>`) et **chaque personnage qui connaît la
  technique** (`perso:<uuid>`). Stockage : `parchemins-s.yml`.
- Un S ne sort d'une bibliothèque que s'il reste un slot ; il le réserve à sa sortie.
- `/parchemin slots <technique>` : voir qui occupe les slots.

## 6. À la mort RP (lot 4)

Quand un personnage meurt (RPK exécutée, cf. [`KO_ET_MORT_RP.md`](./KO_ET_MORT_RP.md) §7) :

- **ses slots S se libèrent** ;
- **héritage** : si le rouleau S de la technique est **dans son inventaire** au moment de la mort, le slot reste
  réservé au rouleau, qui passe à celui qui le récupère ;
- sa progression de séances est effacée.

Code : `SeanceService.onCharacterDeath` (écoute `CharacterDeathEvent`), `SlotRegistry.releaseEverywhere`.

---

## 7. Écrans et canal réseau

- Mod : `parchemin/BibliothequeScreen` (meuble à casiers, onglets pendus Fonds / Arrivages, sceau 秘 et cadenas
  de l'accès « membres », vol et fouille en cours, bouton Déposer toujours visible (grisé + raison), choix du rouleau
  à déposer, journal 記, fiche au survol, prise limitée par le poids),
  `LectureScreen` (lecture + « S'entraîner »), `BibliothequeStaffScreen` (réglages : type, accès, arrivages, rouleaux, délai,
  % par rang, vol ; % par technique avec filtres ; réserve de secrets ; simulation), `ConcentrationHud`, `Tirage`
  (algorithme). Serveur : `parchemin/VolService` (fouille), `ScrollCatalog` (secrets, réserve par défaut).
- Canal `reborn:parchemin` (JSON) — serveur → mod : `lib`, `lib_cfg`, `lib_log`, `toast`, `read`, `conc`, `steal`
  (`state` = start | fail | done, `slot`, `shelf`, `ms`) ; mod → serveur : `take` (`shelf` = fonds | arrivages),
  `steal` (`shelf`, `slot`), `steal_cancel`, `deposit` (`ref` = H/I/B + case, `cell`), `log`, `read_onsite` (`cell`),
  `cfg_save`, `reroll`, `cfg_open`, `train` (`lib` = lecture sur place). Champs du 13/10, tous **optionnels** : `lib`
  → `canSteal`, `stealSecs` (D..S), `depositWhy`, fonds[].`stolen` ; `lib_cfg` → `theft`, `theftSecs`, `theftNamed`,
  `theftFactors`, `commons`, `reserveCustom`, `reserve`, `reserveDefault`, `secretPct`, `secretTechs` (entrées
  légères : `secret`, `clan`, `anbu`, `slots`) ; `cfg_save` ← `theft`, `theftSecs`, `theftNamed`, `commons`,
  `reserve` (seulement si touchée) ou `reserveReset`. Un ancien mod ne voit pas le vol (cadenas comme avant) et
  n'efface pas la réserve en enregistrant ; un ancien serveur donne l'écran sans vol ni onglet Secrets (message).
- Textures : `tools/gen_bibliotheque_textures.py` (v2 : `onglets.png` avec la perche, `acces.png` sceaux 秘 et
  cadenas, `registre.png`, `reglages.png` 設 + 記).
- Banc de captures : `REBORN_PARCHEMIN_DEBUG=1` (fonds d'un clan, arrivages, non-membre et sceau, dépôt, journal,
  lecture sur place, lecture, réglages avec le vol, techniques, secrets, recherche, simulation, vol : fiche, fouille,
  réussite, dépôt refusé, journal avec un vol : `bib_1` à `bib_20`) et `REBORN_CONC_DEBUG=1`
  (concentration). Voir [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5.

---

## 8. Reste à faire

| Sujet | État |
|---|---|
| Rendu de la concentration (ensō A ou trait B) | **à trancher** par l'utilisateur, puis à coder |
| Étapes d'apprentissage plus « fun » au-delà de la concentration (rotation d'une bulle d'eau, puissance, forme, natures ; Taïjutsu sur mannequin ; Kenjutsu par parades) | idées, non tranchées |
| Lancer l'apprentissage dès le clic sur « Apprendre » | idée de l'utilisateur, non faite |
| Apprentissage des techniques de clan | à concevoir (système « vraiment immersif ») |
| Rouleau posable en meuble Nexo (une variante par rang) | à faire |
| Bibliothèques v2 : déploiement dev (ShinobiCore + ShinobiAbilities + `reborn-hud`) et test en jeu | à faire |
| Bibliothèques v2 : plusieurs meubles reliés (fonds > 9 cases), dépôt par glisser-déposer | idées, non faites |
| Vols et secrets : déploiement dev (ShinobiCore pour l'alerte `vol` + ShinobiAbilities + `reborn-hud`) et test en jeu | à faire |
| **Meuble Nexo « étagère à rouleaux »** : poser le meuble crée sa bibliothèque personnelle (comme `/bibliotheque perso`, une par personnage), le casser la défait et rend le fonds ; à l'achat à l'échoppe. Côté code : écouter `NexoFurniturePlaceEvent` / `NexoFurnitureBreakEvent` (dépendance Nexo facultative) et appeler `LibraryService.create(…, PERSONNELLE…)` / `delete` ; côté pack : modèle 3D + entrée Nexo | **proposé, non fait** (demande un modèle et la dépendance Nexo) |
| `ShinobiCore` en prod sans les derniers ajouts : la concentration reste inerte tant que ShinobiAbilities n'y est pas | info |
