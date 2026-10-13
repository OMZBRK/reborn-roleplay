# Apprentissage des techniques — parchemins, bibliothèques, séances

> **Fiche système, à jour au 2026-10-12.** Lots 1 à 3 en production (`reborn-hud` 0.4.166 et plus), lot 4
> (mort RP) et concentration du chakra sur le serveur dev avec la 0.4.169. Les règles ont été décidées avec
> l'équipe les 8 et 9 octobre ; la réflexion d'origine est dans
> [`archives/conception/PROPOSITION_PARCHEMINS_2026-10-08.md`](../archives/conception/PROPOSITION_PARCHEMINS_2026-10-08.md).
>
> Code : `ShinobiAbilities/…/parchemin/` (catalogue, bibliothèques, slots S, séances, commandes),
> `ShinobiAbilities/…/techniques/` (objet parchemin, mini-jeux, concentration), mod `…/parchemin/` (écrans).

---

## 1. Les règles du jeu

- **Ce qui s'apprend par parchemin** : Taïjutsu (Gōken), Kenjutsu et Ninjutsu. Les techniques **de clan**
  s'apprennent **dans le clan** (système à venir) ; les techniques **ANBU** passent par la fonction ANBU (design
  Faction). Senjutsu et « autres » restent hors des bibliothèques. Réglable : `parchemins.categories`,
  `parchemins.exclues`.
- **Le parchemin est un vrai objet** : il se vole, s'échange, se brûle, se pose dans un coffre. Il est
  **unique** (pas de recopie) et non empilable.
- **Deux sources** : les **événements RP** (le staff place des rouleaux dans des coffres, sur des tables) et les
  **bibliothèques à tirage** (§3).
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

## 3. Les bibliothèques à tirage

- Le staff transforme un bloc ou un meuble en bibliothèque : `/bibliotheque creer <nom>`, puis la règle depuis
  l'écran du mod : `/bibliotheque regler` (visée ou la plus proche).
- **À l'ouverture**, le joueur voit un meuble à casiers avec des rouleaux tirés au sort. Chaque technique a son
  pourcentage d'apparition ; si plus de rouleaux sortent que de cases, on garde les plus rares.
- **Le tirage est partagé** : un rouleau pris disparaît pour tout le monde jusqu'au tirage suivant. Le premier
  arrivé se sert.
- **Renouvelé toutes les 3 h** par défaut (réglable par bibliothèque) ; entre deux tirages, on revoit les mêmes
  rouleaux.
- **1 à 9 rouleaux** par tirage (6 par défaut). **On peut tout emporter** : seule la limite de poids de la
  sacoche compte.
- **Accès libre.** Le contrôle d'accès est RP (autorisation du Kage, zone dangereuse) : les bibliothèques
  privées (Kage, ANBU, palais, clans) ont simplement des pourcentages plus généreux.
- Un rang S ne sort que s'il **reste un slot** (§5).
- Les prises de rangs A et S sont journalisées.

### Modèles de pourcentages (par technique, à chaque tirage)

| Modèle | D | C | B | A | S |
|---|---|---|---|---|---|
| Publique | 35 % | 12 % | 3 % | 0,3 % | 0,02 % |
| Réservée | 30 % | 18 % | 7 % | 1 % | 0,08 % |
| Privée | 20 % | 22 % | 12 % | 3 % | 0,3 % |

Le staff peut aussi fixer un pourcentage **par technique** (recherche, filtres Tai / Ken / Nin / nature) et
lancer une **simulation** (moyenne sur 10 000 tirages). Ordre de grandeur sur 6 rouleaux, modèle Publique :
3 rouleaux D, 0,7 C, 0,12 B ; au moins un A dans 1,2 % des tirages, un S dans 0,03 %.

Commandes staff : `/bibliotheque creer <nom> | regler | tirage | suppr | liste`. Stockage : `bibliotheques.yml`.

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
- **Il faut le rouleau en main** du début à la fin. La progression est liée au **personnage** : on garde ses
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

- Mod : `parchemin/BibliothequeScreen` (meuble à casiers, fiche au survol, prise limitée par le poids),
  `LectureScreen` (lecture + « S'entraîner »), `BibliothequeStaffScreen` (réglages : modèle, rouleaux, délai,
  % par rang, % par technique avec filtres, simulation), `ConcentrationHud`, `Tirage` (algorithme).
- Canal `reborn:parchemin` (JSON) — serveur → mod : `lib`, `lib_cfg`, `toast`, `read`, `conc` ; mod → serveur :
  `take`, `cfg_save`, `reroll`, `cfg_open`, `train`.
- Banc de captures : `REBORN_PARCHEMIN_DEBUG=1` (bibliothèque, lecture, réglages) et `REBORN_CONC_DEBUG=1`
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
| `ShinobiCore` en prod sans les derniers ajouts : la concentration reste inerte tant que ShinobiAbilities n'y est pas | info |
