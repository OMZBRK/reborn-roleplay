# Parchemins de techniques — proposition

> Brouillon du 2026-10-08, à partir des décisions de l'équipe. Les valeurs chiffrées sont des propositions ; les
> points marqués **Q** attendent une réponse.

## 1. Décidé

- **Périmètre** : techniques Taïjutsu, Kenjutsu et Ninjutsu. Les techniques de clan s'apprennent **dans le clan**
  (système dédié, plus tard) ; les techniques ANBU passent par la fonction ANBU (design Faction).
- **Le parchemin est un objet** : il se vole, s'échange, se brûle, se pose (coffre, bibliothèque, table) comme un vrai
  rouleau.
- **Deux sources** :
  1. **Événements RP** : scènes d'animation, missions. Le staff place les rouleaux dans des coffres, sur des tables,
     dans des bibliothèques de décor.
  2. **Bibliothèques définies** : le staff pose une bibliothèque ; à l'ouverture, un menu tire des rouleaux selon un
     pourcentage d'apparition par technique (beaucoup de rang D, un peu de C, rarement du B, très rarement du A, et le
     S presque jamais).
- **Rareté** : le rang A est très rare. Le rang S est à **slots limités** et se transmet surtout en RP.
- **Deux façons d'apprendre** : en **autodidacte** (avec le parchemin) ou par **transmission RP** d'un professeur.
  Le rang S passe en grande majorité par la transmission.
- **Plus le rang est haut, plus c'est dur et long à apprendre.**

## 2. Ce qui existe déjà dans le code

- `ParcheminItems` (ShinobiAbilities) : un parchemin porte l'identifiant d'une technique.
- `LearningShelfManager` : une étagère d'apprentissage (3 ou 9 emplacements) où l'on pose les rouleaux ; cliquer un
  rouleau lance l'entraînement, et le rouleau n'est consommé qu'en cas de réussite.
- `LearningMinigame` : les épreuves d'apprentissage (enchaîner des mudras, des pompes, ou une validation staff), avec
  trois niveaux de difficulté.
- Technique Creator : chaque technique a un rang, une difficulté et un gate `requires:`.

La proposition réutilise ces briques. Ce qui manque : la bibliothèque à tirage, les slots S, l'apprentissage long et
la transmission par un professeur.

## 3. Règles — validées le 2026-10-08

### 3.1 Bibliothèque à tirage

- **Accès libre.** Le contrôle d'accès est RP (autorisation du Kage, haut gradé, zone dangereuse) : les étages ou
  ailes réservés aux hauts grades et les bibliothèques privées (Kage, ANBU, palais, clans) ont simplement des
  pourcentages plus généreux en hauts rangs, réglés par le staff.
- **Tirage toutes les 3 heures** par joueur et par bibliothèque. Entre deux tirages, le joueur revoit les mêmes
  rouleaux.
- **1 à 9 rouleaux par tirage**, au choix du staff pour chaque bibliothèque.
- **On peut tout emporter** : seule la limite de poids de la sacoche compte.
- **Un rouleau pris disparaît pour les autres** : le tirage est partagé, le premier arrivé se sert.
- **Panel staff** : le plus versatile possible tout en restant intuitif. Réglages par bibliothèque : nom, nombre de
  rouleaux, délai, pourcentage par rang **et** par technique (avec recherche et filtres Tai / Ken / Nin / nature),
  modèles prêts à l'emploi (publique, réservée, privée), aperçu d'un tirage.

Valeurs de départ par rang (modèle « publique ») :

| Rang | Apparition par technique, à chaque tirage |
|---|---|
| D | 35 % |
| C | 12 % |
| B | 3 % |
| A | 0,3 % |
| S | 0,02 %, et seulement s'il reste un slot |

### 3.2 Slots du rang S

- Chaque technique S a un **nombre maximum de détenteurs** (personnages), réglé par le staff.
- Un slot est occupé par chaque personnage qui connaît la technique **et** par chaque parchemin S qui existe en jeu.
- À la **mort RP** d'un détenteur, son slot se libère. Il peut laisser son rouleau en héritage **seulement s'il porte
  l'objet sur lui** au moment de sa mort.

### 3.3 Apprendre

L'apprentissage se fait en **séances**, sur l'étagère d'apprentissage existante :

| Rang | Séances |
|---|---|
| D | 1 |
| C | 3 |
| B | 6 |
| A | 12 |
| S | 20 |

- **Une séance par jour** et par technique. Chaque séance est une épreuve des mini-jeux existants (mudras, pompes…),
  plus dure quand le rang monte.
- **Échec** : la séance est perdue, sans avancer. Rien d'autre.
- **Easter eggs** : des événements très rares (rouleau déchiré, emporté par le vent, rongé…) peuvent faire perdre le
  parchemin. Probabilité de l'ordre de 1 sur 500 séances, avec un message RP à chaque fois.
- **Commande staff** pour accorder des séances : `/parchemin seance <joueur> <technique> [+n]`.
- **Professeur** : il connaît la technique **depuis un moment** et à un **niveau de maîtrise** suffisant. Le niveau
  vient de la jauge de maîtrise existante (0 à 100 par technique, qui monte à chaque lancer) : Novice 0-39, Avancé
  40-79, Maître 80-100. Proposition : il faut être Maître et connaître la technique depuis 14 jours. Avec un
  professeur présent, la séance compte double.
- **Rang S** : la dernière séance n'apprend pas la technique, elle ouvre une **demande de validation staff**. On peut
  faire tout le parcours sans le staff ; seule l'obtention finale passe par lui.
- Le parchemin est **consommé** quand la technique est apprise.

## 4. Tranché le 2026-10-08 (suite)

- **Pas de recopie** : un parchemin est unique, il ne se duplique pas.
- **Professeur** : Maître de la technique (maîtrise 80 et plus) et la connaître depuis au moins 14 jours.
- **Progression liée au personnage** : on garde ses séances même si l'on perd le rouleau, mais il faut un rouleau en
  main pour faire une séance.

## 5. Previews et banc d'essai (2026-10-08)

Client de dev uniquement (`REBORN_PARCHEMIN_DEBUG=1`), données de démo, rien n'est branché au serveur :

- `mod-hud/parchemin/BibliothequeScreen` — meuble à casiers, rouleaux couchés, étiquette de rang, fiche au survol,
  prise d'un rouleau limitée par le poids de la sacoche.
- `LectureScreen` — le rouleau se déroule entre ses baguettes ; technique, signes, perles de séances, délai.
- `BibliothequeStaffScreen` — réglages (modèle, rouleaux, délai, % par rang), techniques (filtres, % par technique),
  simulation (aperçu d'un tirage, moyenne sur 10 000 tirages).
- `Tirage` — l'algorithme de tirage, en code pur, prêt à passer côté serveur.

Simulation sur l'échantillon de démo (25 techniques), 6 rouleaux par tirage :

| Modèle | D | C | B | A | S | Au moins un A | Au moins un S |
|---|---|---|---|---|---|---|---|
| Publique | 3,0 | 0,7 | 0,12 | 0,012 | 0,0003 | 1,2 % | 0,03 % |
| Réservée | 2,6 | 1,1 | 0,27 | 0,04 | 0,0016 | 3,9 % | 0,16 % |
| Privée | 1,7 | 1,3 | 0,48 | 0,12 | 0,0066 | 11,5 % | 0,66 % |

Reste à préparer : le modèle 3D du rouleau posable (meuble Nexo, une variante par rang).
