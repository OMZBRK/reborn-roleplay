# Faction — proposition de design (13/10, remplacée)

> ⚠️ **Remplacée le 2026-10-15 par la proposition v2 : [`FACTION.md`](./FACTION.md)** (aucun kanji, sections de
> métier, fonctions créées par le chef, lieux posés par le staff, banque déjà codée, 8 questions). Gardée pour mémoire :
> réputation, diplomatie, Bingo Book et désertion y sont détaillés pour l'après-beta.
>
> **Statut d'origine : proposition (2026-10-13), rien n'est codé.** C'est le dernier design du Sprint 1 (« Design Faction
> Konoha : sections, trésor commun, banques, coffres, références visuelles », [`CHANTIERS.md`](../CHANTIERS.md) §1,
> décisions en attente n° 1 et 11). L'utilisateur doit valider ou corriger ce document. Les questions à trancher sont
> au §16.
>
> **Maquettes** (hors dépôt, 1920 × 1080, au niveau de la fiche shinobi « portrait en scène ») :
> `Maquettes/faction/` à côté de `RBLAUNCHER/`, générées par `maquette.py` :
> `01_accueil.png`, `02_sections.png`, `03_tresor.png`, `04_coffres_banque.png`, `05_diplomatie.png`,
> `06_fiche_membre.png` (plus un zoom ×2 de chacune).
>
> **Sources** : [`STATS_ET_PROGRESSION.md`](./STATS_ET_PROGRESSION.md) (9 grades, Mérite),
> [`BIBLIOTHEQUES_V2_PROPOSITION.md`](./BIBLIOTHEQUES_V2_PROPOSITION.md) et [`APPRENTISSAGE.md`](./APPRENTISSAGE.md) §3,
> [`KO_ET_MORT_RP.md`](./KO_ET_MORT_RP.md), [`MISSIONS.md`](./MISSIONS.md),
> [`../etudes/BENCHMARK_MODS_ET_SERVEURS.md`](../etudes/BENCHMARK_MODS_ET_SERVEURS.md) §2.7, §2.8, §3, lot A3 (Solve,
> Nydo, Ninshu), [`../staff/GUIDE_STAFF_EN_JEU.md`](../staff/GUIDE_STAFF_EN_JEU.md),
> [`../adr/0006-monnaies-ryo-et-rbcoins.md`](../adr/0006-monnaies-ryo-et-rbcoins.md),
> [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md).

---

## 0. En dix lignes

1. Une **faction** = un groupe de personnages avec un chef, des **sections**, un **trésor**, des **coffres** et une
   **diplomatie**. Les six villages sont des factions ; les organisations indépendantes (type Akatsuki) aussi.
2. Le **grade** (Genin → Kage, déjà en place) dit *ce que tu vaux* ; la **fonction** (Kage, conseiller, chef de
   section, capitaine ANBU, intendant…) dit *ce que tu fais*. Les deux se cumulent ; seule la fonction donne des
   droits (signer, payer, ouvrir un coffre, valider).
3. **Quotas** par village (6 Jōnin dont 2 sans clan, comme Solve) et **délais minimum** entre deux grades.
4. Le ryo se partage en **bourse** (sur soi, volable) et **compte au village** (à l'abri, au guichet de la banque).
5. Le **trésor commun** reçoit une part des missions, les dons et les amendes ; il paie la **solde** hebdomadaire et
   les budgets de section. Tout passe par un **journal** que l'on ne peut pas effacer.
6. Les **coffres** de faction ou de section sont de vrais meubles, avec des droits voir / déposer / retirer et un journal.
7. **Réputation** de −1 000 à +1 000 par personnage et par village ; **diplomatie** à quatre états (alliance, paix,
   tension, guerre) décidée par les Kage ; **Bingo Book** et **désertion** encadrés par le staff.
8. Tout est **serveur** (ShinobiCore, package `faction/`) ; le mod n'affiche qu'un écran à 5 onglets.
9. Signature visuelle : les **koma de shōgi** (encre noire = grade, encre rouge = fonction), sur un décor de
   **jinmaku** (rideau de camp aux armoiries) éclairé par deux **kagaribi**.

---

## 1. Ce qu'est une faction en jeu

| Type | Exemples | Comment on y entre | Territoire | Ce qu'elle a |
|---|---|---|---|---|
| **Village caché** | Konoha, Suna, Kiri, Kumo, Iwa, Ame (les 6 de la création) | Choix du village à la création (déjà en place, `ShinobiCharacter.village`) | Oui (zones du village) | Tout : sections, fonctions, trésor, banque, coffres, réputation, diplomatie, Bingo Book |
| **Organisation rattachée** | ANBU, police, Corps médical, bureau des missions | Nomination par le chef de la faction | Bâtiments du village | **C'est une section** du village (§2), pas une faction à part |
| **Organisation indépendante** | type Akatsuki (nom à choisir, §16), bande de mercenaires | Recrutement RP **validé par le staff** | Un repaire, pas de village | Sections, fonctions, trésor, coffres, diplomatie ; **pas de banque** (pas de guichet) ; réputation propre auprès des villages |
| **Déserteur (nukenin)** | ancien Jōnin de Kiri | Désertion validée par le staff (§8.4) | — | **Un statut, pas une faction** : hors de tout village, inscrit au Bingo Book de son village d'origine, peut rejoindre une organisation indépendante |

- **Une seule faction par personnage** (village **ou** organisation indépendante). Un espion qui appartient en secret
  à une autre faction se joue en RP, pas dans les données (sinon il serait trahi par l'écran).
- Le **staff** crée et règle les factions ; les joueurs les font vivre. « Déserteur » reste retiré de la création
  (décision du 10/10) : on ne devient nukenin qu'en jeu.

---

## 2. Les sections

Une section = une unité du village avec **un chef**, des **membres**, éventuellement un **quota**, un **coffre**, un
**budget** et des **accès** (bibliothèque, bâtiments). Chaque village part du même modèle et renomme ses sections.

| Section (modèle) | Kanji de la pièce | Qui | Chef (fonction) | Accès typiques | Konoha |
|---|---|---|---|---|---|
| **Palais** | 影 | Kage, conseillers, bras droit | le Kage | Réserve du palais (bibliothèque Palais), coffre du palais, trésor | Palais de l'Hokage |
| **Forces Jōnin** | 上 | Tokubetsu Jōnin, Jōnin, Commandant Jōnin | Commandant Jōnin | Armurerie (retrait), salle des Jōnin | Forces Jōnin |
| **ANBU** | 暗 | Fonction ANBU (Chūnin et plus) | Capitaine ANBU | Bibliothèque ANBU, coffre ANBU, quartier ANBU | ANBU |
| **Corps médical** | 医 | Médecins (`shinobicore.medic`, `.expert`) | Directeur de l'hôpital | Bibliothèque Hôpital, pharmacie, armoires | Hôpital de Konoha |
| **Police / garde** | 警 | Chūnin et plus | Chef de la police | Poste de police, coffre des saisies, droit d'amende | Police militaire |
| **Bureau des missions** | 任 | Chūnin | Chef du bureau | Registre des missions, part du trésor (§6) | Bureau des missions |
| **Académie** | 学 | Académiciens, instructeurs | Directeur de l'Académie | Bibliothèque Académie | Académie |
| **Genin** | 下 | Genin, Genin confirmés, en équipes de 3 avec un sensei | Chef des Genin (souvent un Jōnin) | Coffre des Genin, bibliothèque Académie | Genin |

- Un personnage est **membre de sa section principale** (calculée depuis son grade : un Genin est dans « Genin ») et
  peut **appartenir en plus** à une section spéciale (ANBU, Corps médical, police, bureau des missions).
- Les **équipes de Genin** (3 Genin + 1 sensei) sont des sous-groupes de la section Genin : le sensei voit et
  recommande ses élèves.
- **ANBU scellée** : les non-membres voient la section mais ni ses membres ni son quota (sceau 秘 dans la maquette 02).
  Même principe possible pour une « Racine » créée par le staff.
- Le staff peut **ajouter, renommer ou masquer** une section par village (Ame n'a pas forcément de police).

---

## 3. Grades et fonctions

**Rappel (décisions du 08/10)** : 9 grades RP (Académicien, Genin, Genin confirmé, Chūnin, Konin, Tokubetsu Jōnin,
Jōnin, Commandant Jōnin, Kage), donnés par `/grade` ; ANBU, bras droit, conseil… sont des **fonctions RP**, qui
donnent des **avantages, jamais de stats**.

### 3.1 Les fonctions

| Fonction | Pièce | Places (défaut) | Grade minimum | Ce qu'elle permet |
|---|---|---|---|---|
| **Kage** | 影 rouge | 1 | Kage (grade) | Tout dans sa faction : nommer aux fonctions, diplomatie, dépenser le trésor, décrets, Bingo Book, rétrograder |
| **Conseiller** | 議 | 2 | Jōnin | Valider les dépenses au-dessus du plafond de l'intendant, voter (si le conseil vote), annonces, voir tout le journal |
| **Bras droit** | 補 | 1 | Jōnin | Agit au nom du Kage en son absence (sauf diplomatie et guerre) |
| **Chef de section** | 長 | 1 par section | selon la section | Gérer sa section : membres, équipes, coffre, budget ; proposer des promotions, sanctions RP (§5) |
| **Capitaine ANBU** | 長 sur 暗 | 1 | Jōnin | Chef de l'ANBU ; inscriptions au Bingo Book |
| **ANBU** | 暗 | 6 | Chūnin | Accès ANBU (bibliothèque, coffre, techniques réservées) ; tenue et masque |
| **Intendant (trésorier)** | 金 | 1 | Chūnin | Payer la solde, achats et budgets **sous un plafond** (§6.4) |
| **Sensei** | 師 | 1 par équipe | Chūnin | Recommander ses Genin, les inscrire à l'examen |
| **Sannin** (honorifique) | 仙 | 3 | Tokubetsu Jōnin | Titre narratif nommé par le Kage (et le staff) ; **ne combat pas en guerre** (règle Solve) ; aucun droit de gestion |

- **Le Kage est à la fois un grade et une fonction** (une seule place par village).
- Une fonction se **donne** par le Kage (ou par le chef de section pour les fonctions de sa section : sensei, membre
  de la police…), se **retire** de même, et se **suspend** (sanction, §5.2).
- Chaque fonction peut porter : une **tenue d'institution** (débloquée dans l'échoppe), des **permissions** (calculées
  par ShinobiCore, pas des nœuds LuckPerms à entretenir), un **bonus de solde** (§6.3), un **accès** (bibliothèque,
  coffre, bâtiment).

### 3.2 Visuel : la pièce de shōgi

Chaque membre est une **pièce** : encre **noire** pour le grade, encre **rouge** (comme une pièce promue au shōgi)
quand il occupe une fonction. Le chef d'une section porte une petite pièce rouge 長 accrochée à celle de sa section.
C'est lisible d'un coup d'œil et ça rappelle la leçon du « roi » de Konoha.

---

## 4. Quotas et délais de promotion

### 4.1 Quotas par village (réglables par le staff, par village)

| Grade | Quota proposé | Remarque |
|---|---|---|
| Kage | 1 | — |
| Commandant Jōnin | 1 | — |
| Jōnin | **6, dont au moins 2 sans clan** | Repris de Solve ; « sans clan » = clan Neutre ou Autre |
| Tokubetsu Jōnin | 6 | — |
| Konin | 8 | — |
| Chūnin et moins | sans quota | — |
| Sannin (titre) | 3 | — |
| ANBU (fonction) | 6 | dont 1 capitaine |

- **Quota plein** : la promotion est refusée avec la raison ; le chef peut **inscrire le candidat en attente**. Quand
  une place se libère (rétrogradation, mort RP, désertion, inactivité), le premier de la file est proposé au Kage.
  L'ordre de la file : le **Mérite** (§11), puis l'ancienneté dans le grade.
- **Inactivité** : un Jōnin absent 30 jours (réglable) libère sa place automatiquement mais garde son grade en
  « réserve » ; il la retrouve si une place est libre à son retour.

### 4.2 Délais minimum (temps passé dans le grade avant la promotion suivante)

| Vers | Délai | Condition |
|---|---|---|
| Genin | — | Examen de sortie de l'Académie |
| Genin confirmé | 7 jours | — |
| Chūnin | 14 jours | **Examen chūnin** |
| Konin | 14 jours | — |
| Tokubetsu Jōnin | 14 jours | — |
| Jōnin | 21 jours | Quota |
| Commandant Jōnin | 30 jours | Quota |

Repris et adoucis de Solve (2 semaines Genin → Chūnin). Le délai se compte en **jours réels depuis la dernière
promotion** (déjà dans le journal des grades). Le **staff ignore** délais et quotas (`shinobicore.grade.staff`).

### 4.3 Comment on promeut

Les règles actuelles de `/grade` restent (on donne un grade inférieur au sien, dans son village, au mérite ou sur
examen — `GradeRules`). La faction ajoute :

1. **Proposer** : le chef de section ou le sensei clique « Proposer » (ou « Inscrire à l'examen ») sur la fiche du
   membre (maquette 06). Les conditions s'affichent en clair : délai, quota, recommandation, examen.
2. **Valider** : la personne qui a le droit de donner ce grade (tableau de `/grade`) reçoit la proposition (chat +
   onglet Sections) et valide. Si le proposeur a lui-même le droit, la proposition vaut validation.
3. **Trace** : la promotion entre dans les **états de service** de la fiche shinobi (déjà en place, `ServiceLog`),
   dans le journal de la faction et au Poste de garde.

**Anti-copinage** (problème n° 1 des serveurs RP selon le benchmark) : à partir de Jōnin, il faut **deux
signatures** (le Kage + un conseiller, ou le Kage + le staff) ; chaque promotion est visible du staff ; un staff
peut l'annuler (`/grade` reste l'outil).

---

## 5. Le chef de section et ses membres (maquette 06)

### 5.1 Ce que voit un chef

Fiche du membre (le **personnage réel** au centre, comme la fiche shinobi) : grade et ancienneté, section, équipe,
fonction, clan, missions, palier de Mérite, palier de réputation, états de service (y compris les sanctions, visibles
de la seule hiérarchie). À droite, les **décisions** : conditions de la promotion suivante, « Inscrire à l'examen »,
« Proposer une fonction », sanctions RP.

### 5.2 Sanctions RP (distinctes des sanctions staff du Poste de garde)

| Sanction | Qui | Effet | Durée |
|---|---|---|---|
| **Blâme** | Chef de section, sensei | Ligne dans les états de service (visible de la hiérarchie) | Permanent |
| **Retenue sur solde** | Chef de section | −25 % à −100 % de la prochaine solde | 1 à 4 semaines |
| **Suspension de fonction** | Chef de section, Kage | Fonction gelée (droits coupés, tenue gardée) | 1 à 14 jours |
| **Exclusion de la section** | Chef de section (Kage pour l'ANBU) | Retour dans la section de son grade | — |
| **Rétrogradation** | Kage seul (règle actuelle) | Grade inférieur | — |
| **Inscription au Bingo Book** | Kage, capitaine ANBU, chef de la police | §8.3 | — |

Une sanction RP **n'est pas** une sanction de modération : elle ne mute ni ne bannit. Le staff voit tout et peut
annuler. Une sanction RP abusive se signale au staff (`/signaler`).

---

## 6. Économie : bourse, banque, trésor

### 6.1 Rappel des règles

- **Ryo** gagnés en jeu, **RBCoins** achetés ou gagnés hors jeu, **aucune conversion** (ADR 0006, décision du 08/09).
  Tout ce document ne parle que de **ryo**.
- Aujourd'hui : un seul solde `ryo` par personnage, donné par le staff (`/ryo`), **aucune source de gain**. Les
  missions sont la source qui manque (benchmark §2.8).

### 6.2 Bourse et compte (maquette 04, planche de gauche)

| | **Bourse** (liquide) | **Compte au village** (banque) |
|---|---|---|
| Où | Sur le personnage | À la banque de son village |
| Sert à | Payer l'échoppe, payer un autre joueur de la main à la main, payer une amende | Recevoir la solde, épargner |
| Risque | **Volable** : un joueur peut fouiller un corps à terre (menu d'interaction R) et prendre une partie de la bourse, plafonnée à **5 000 ryo par scène** (règle Solve), avec alerte au Poste de garde | **Aucun vol possible** |
| Accès | Partout | Au **guichet** (meuble Nexo ou PNJ) de la banque de son village ; dans un village **allié** avec 5 % de frais |
| À la mort RP | ❓ §16 (perdue, laissée sur le corps, ou héritée) | ❓ §16 (héritage, ou versée au trésor) |

- Le solde actuel (`ryo`) devient la **bourse** ; on ajoute un champ `compte`.
- Plus tard (benchmark lot D4) : un **portefeuille-objet** physique ; ce n'est pas nécessaire pour la beta.

### 6.3 Le trésor commun (maquette 03)

Un compte de la faction, en ryo.

**Entrées**

| Source | Règle proposée |
|---|---|
| **Missions** | **30 % de la récompense** d'une mission du bureau vont au trésor, 70 % à l'équipe (réglable) |
| **Dons** | N'importe quel membre, depuis sa bourse ou son compte (`/faction don <montant>`) |
| **Amendes** | Infligées en RP par la police (plafond par amende, ex. 2 000 ryo), prises dans la bourse ou le compte du condamné |
| **Butin, rançons, tributs** | Accords entre villages ou fin de guerre, **validés par le staff** |
| **Subventions du staff** | Événements, lancement de la beta (trésor de départ) |
| Taxes | **Pas à la beta** (une taxe sur les ventes de l'échoppe est possible plus tard) |

**Sorties**

| Sortie | Règle proposée |
|---|---|
| **Solde hebdomadaire** | Versée le dimanche sur le **compte** de chaque membre **actif** (au moins 3 h de présence RP dans la semaine, mesure déjà faite par l'XP) ; barème ci-dessous |
| **Budgets de section** | Le Kage alloue un budget à une section (le chef le dépense pour sa section) |
| **Achats** | Équipement pour les coffres (achats à l'échoppe au nom de la faction) |
| **Primes** | Bingo Book (§8.3), missions commanditées par le village |
| **Tributs, accords** | Diplomatie, validés par le staff |

**Barème de solde proposé** (par semaine, réglable par le staff) : Genin 200 · Genin confirmé 300 · Chūnin 500 ·
Konin 700 · Tokubetsu Jōnin 900 · Jōnin 1 200 · Commandant Jōnin 1 600 · Kage 2 000 ; bonus de fonction : chef de
section ou ANBU +300, conseiller, bras droit ou capitaine ANBU +500. **Si le trésor ne suffit pas, la solde est
versée au prorata** et tout le monde le voit : c'est ce qui pousse à faire des missions.

**L'argent ne se crée qu'à deux endroits** (missions et staff) ; tout le reste circule. C'est ce qui évite
l'inflation (benchmark : les 15 000 ryo par jour de Ninshu cassent toute économie RP).

### 6.4 Droits sur le trésor

| Action | Qui |
|---|---|
| Voir le solde | Tous les membres (Genin : le solde en mots — « bien garni », « à sec » — plutôt qu'en chiffres, ❓ §16) |
| Voir le journal détaillé | Jōnin et plus, toutes les fonctions |
| Donner | Tous |
| Payer la solde, acheter | Intendant, jusqu'à **10 000 ryo par opération** et **30 000 par semaine** (hors solde) |
| Allouer un budget, payer une prime, au-delà du plafond | Kage, ou intendant + un conseiller |
| Tributs, rançons | Kage + validation staff |

### 6.5 Le journal

Chaque mouvement (trésor, compte, coffre) est écrit dans un journal **en ajout seul** : date, auteur (sa pièce),
motif, montant, solde après. Personne ne peut l'effacer, pas même le Kage ; le staff peut **contre-passer** une
opération (une ligne inverse avec sa raison). Alertes au Poste de garde : opération au-dessus d'un seuil, plusieurs
sorties rapprochées, don massif entre deux comptes liés par IP (le Poste de garde connaît déjà les comptes liés).

---

## 7. Coffres de faction et de section (maquette 04, planche de droite)

- Un coffre est un **vrai meuble** (Nexo, ou coffre vanilla marqué) enregistré par le staff ou le Kage :
  `/faction coffre creer <nom> <section>` en visant le meuble.
- **Trois droits** par coffre : **voir**, **déposer**, **retirer**. Chaque droit vaut pour : tous les membres, une
  section, un grade minimum, une fonction, ou une liste de personnages. Exemples (Konoha) :

| Coffre | Lieu | Voir | Déposer | Retirer |
|---|---|---|---|---|
| Armurerie | Caserne | membres | membres | Chūnin et plus |
| Pharmacie | Hôpital | Corps médical | membres | Corps médical |
| Coffre des Genin | Académie | section Genin | section Genin | chef des Genin, sensei |
| Saisies | Poste de police | police | police | chef de la police (scellé pour les autres) |
| Coffre ANBU | inconnu | ANBU | ANBU | capitaine ANBU |
| Coffre du palais | Palais | Kage, conseil | Kage, conseil | Kage |

- **Quota de retrait** par personne et par jour (ex. 16 objets) pour empêcher le pillage par un membre.
- **Journal** du coffre (qui a pris quoi, quand) visible du chef de la section et du Kage ; « dernier retrait » affiché.
- **Vol** : même mécanique que les bibliothèques v2 (fouille immobile de quelques secondes, interrompue si l'on bouge
  ou si l'on est frappé, alerte discrète aux membres et alerte `vol` au Poste de garde), réglable par coffre (vol
  permis ou interdit, durée, alerte nominative).
- Les **bibliothèques** (types Village / Académie / Clan / ANBU / Palais / Hôpital, déjà en place) restent des
  bibliothèques ; l'onglet Coffres les liste simplement avec leurs accès.

---

## 8. Réputation, Bingo Book, désertion

### 8.1 Réputation par village (maquette 05, planche de droite)

Un score de **−1 000 à +1 000** par personnage **et** par village (repris de Nydo et Ninshu). Le joueur ne voit
**jamais le chiffre** (comme les stats, décision du 29/09) : un palier en mots et une jauge sans graduation ; le staff
et le Kage voient le chiffre.

| Palier | Score | Effets proposés |
|---|---|---|
| **Honoré** | ≥ 600 | Missions de rang A et S proposées ; accès à la banque et à l'hôpital d'un village **étranger** |
| **Estimé** | 200 à 599 | Missions de rang B ; point de départ dans **son** village |
| **Neutre** | −99 à 199 | Point de départ ailleurs (+100 si alliance, −100 si tension, −300 si guerre) |
| **Méfiant** | −299 à −100 | Missions limitées au rang D dans ce village ; services payants majorés |
| **Hostile** | −599 à −300 | **Recherché** dans ce village : refus de banque, d'hôpital et de bibliothèque « Village » ; sa police peut l'arrêter |
| **Ennemi juré** | ≤ −600 | Dans **son propre** village : le Kage peut prononcer l'**exil** (§8.4) |

**Barème automatique** (réglable, **désactivé sur les terrains d'entraînement et pendant les scènes validées par le
staff**, comme le recommande l'annexe Ninshu) :

| Acte (hors terrain d'entraînement) | Victime : son village | Ses alliés | Ses ennemis |
|---|---|---|---|
| Agresser un membre (au plus une fois par minute) | −20 | −10 | +5 |
| Mettre un membre KO | −60 | −30 | +15 |
| Mort RP validée d'un membre | −300 | −150 | +75 |
| Mission réussie pour le village (D, C, B, A, S) | +10, +20, +40, +80, +150 | — | — |
| Désertion | −1 000 (exil) | −100 | +25 |

**Réglages manuels** : le Kage et le conseil, **±50 par personne et par semaine** dans leur village, avec une raison
(journal) ; le staff sans limite (`/faction reputation`). La réputation apparaît aussi sur la fiche du membre.

### 8.2 Recherché

Le statut « recherché » (palier Hostile) est une **information**, pas un effet visuel au-dessus de la tête (pas
d'effets « RPG », décision du 08/10) : il apparaît pour la police et l'ANBU du village dans l'onglet Accueil (avis
de recherche) et dans la fiche du membre.

### 8.3 Bingo Book

- Une liste par village : personnage, **niveau de menace** (D à S), motif, dernier lieu vu (écrit à la main), **prime**
  en ryo (mise de côté sur le trésor au moment de l'inscription), statut (recherché, capturé, mort).
- Inscrit par le Kage, le capitaine ANBU ou le chef de la police ; les **déserteurs** y entrent automatiquement.
- La prime est versée quand le **staff** valide une capture ou une mort RP (le circuit RPK existe déjà).
- Plus tard : l'avis de recherche en **objet** (affiche) à placarder.

### 8.4 Désertion et exil

1. **Désertion** : le joueur la joue (fuite, trahison) puis la demande au staff, ou le staff la constate.
   `/faction deserter <perso>` (staff) : le personnage quitte son village, perd ses fonctions, garde son grade
   affiché « ex-Jōnin de Konoha » ; réputation −1 000 avec son village ; entrée au Bingo Book.
2. **Exil** : prononcé par le Kage contre un membre « Ennemi juré », **validé par le staff** ; mêmes effets.
3. **Compte au village** d'un déserteur : ❓ §16 (gelé, saisi au profit du trésor).
4. **Rouleaux S** : le slot d'une technique S d'un déserteur reste bloqué **2 semaines** pour son ancien village
   (règle Solve), puis se libère.
5. Un nukenin peut rejoindre une **organisation indépendante** (recrutement RP + validation staff). Nydo lie
   l'Akatsuki à la mauvaise réputation ; chez nous c'est une **décision RP et staff**, la réputation n'est qu'un indice.
6. **Grâce** : le Kage peut réintégrer un déserteur (staff), réputation remise au palier Méfiant.

---

## 9. Diplomatie (maquette 05)

### 9.1 Les états

| État | Fil sur le plateau | Effets proposés |
|---|---|---|
| **Alliance** | Cordon d'or double | Banque (avec frais), hôpital et bibliothèques « Village » ouverts aux alliés ; missions communes ; agresser un allié coûte de la réputation dans les deux villages |
| **Paix** | (pointillé discret) | État par défaut |
| **Tension** | Fil vermillon effiloché | Réputation de départ −100 ; contrôles aux portes (RP) ; prélude à la guerre |
| **Guerre** | Cordon rouge épais | Agresser un ennemi ne coûte plus de réputation chez soi (et en rapporte) ; créneaux de **conflit programmés** possibles (captures de drapeaux, plus tard) |

La guerre **ne change pas** les règles du KO ni de la mort RP : le combat hors terrain d'entraînement laisse les PV
perdus, la mort RP reste validée par le staff (décisions du 06/10 et du 10/10).

### 9.2 Qui décide

| Changement | Procédure |
|---|---|
| Vers **alliance** ou **paix** (fin de tension) | **Proposition** d'un Kage, **acceptation** de l'autre ; la proposition expire en 72 h (washi « Kumogakure propose une trêve », maquette 05) |
| Vers **tension** | Un Kage seul |
| Vers **guerre** | Un Kage seul, **préavis RP de 24 h** et **validation staff** (la guerre change les règles en jeu) |
| **Fin de guerre** | Les deux Kage, ou le staff |

Les **organisations indépendantes** ont aussi une relation avec chaque village (souvent tension). Le staff peut tout
régler (`/faction diplomatie set`).

### 9.3 Conflit territorial (après la beta)

Les captures de drapeaux à horaires fixes de Solve (son pays à 19 h, les pays frontaliers après, les ennemis à 20 h)
concentrent le PvP sur des créneaux et protègent le RP le reste du temps. À prévoir **après** la beta, sur le
contrôle de zone déjà observé en jeu (plugin tiers non versionné).

---

## 10. Les liens avec le reste

| Système | Ce que la faction lui apporte |
|---|---|
| **Grades** (`/grade`, `GradeRules`) | Quotas, délais, file d'attente, double signature au-dessus de Jōnin (§4) |
| **Fiche shinobi** | Fonction affichée, états de service enrichis (promotions, missions, sanctions visibles de la hiérarchie), palier de réputation |
| **Bibliothèques v2** | Les membres d'une bibliothèque Palais / ANBU / Hôpital deviennent ceux d'une **fonction ou section** ; on remplace les permissions `shinobiabilities.biblio.anbu` / `.palais` par une question à la faction (« a-t-il la fonction ANBU dans ce village ? ») |
| **Médical** | Le Corps médical est une section : la permission `shinobicore.medic` est donnée par la fonction médecin (le guide staff l'attendait) |
| **Missions** (décision n° 13 en attente) | Le bureau des missions est une section ; 30 % des récompenses au trésor ; le rang de mission ouvert dépend du grade **et** de la réputation ; les **décrets du Kage** (missions obligatoires de Solve) partent de l'Accueil |
| **KO et mort RP** | Barème de réputation (KO, mort RP), prime du Bingo Book à la validation d'une RPK, fouille de la bourse sur un corps à terre (menu d'interaction R) |
| **Poste de garde** | Nouvel onglet **Factions** (§13.3), alertes `tresor`, `vol`, `guerre`, `desertion` ; toutes les actions de faction dans le journal du staff |
| **Échoppe** | Tenues d'institution débloquées par la fonction ou le grade ; paiement par la bourse ; achats au nom de la faction par l'intendant |
| **Chat RP** | Canal de faction et canal de section (annonces, appels) ; les **missives** restent du RP |

---

## 11. Les points de Mérite (question n° 4 du tableau de bord)

Aujourd'hui : 3 points par passage de grade, transmis au mod, sans usage. Trois options :

| Option | Principe | Pour | Contre |
|---|---|---|---|
| **A. Prestige** (recommandée) | Le Mérite ne se dépense pas. Il se gagne au grade **et** par le service (missions B et plus, décorations du Kage, +1 à +3). Il sert de **seuil** pour les fonctions (ex. capitaine ANBU 12, conseiller 15) et d'**ordre de passage** dans la file d'attente des quotas | Simple, lisible, colle à « le spécial se mérite par le RP produit » ; aucun marché | Un compteur que l'on ne « dépense » pas peut sembler abstrait |
| B. Crédit de réquisition | Le Mérite se dépense à l'**intendance** : tenue d'institution, équipement de l'armurerie hors quota, priorité d'un rouleau du fonds | Donne une boucle de jeu | Crée une seconde monnaie à équilibrer à côté du ryo |
| C. Poids politique | Le Mérite pèse dans les votes du conseil | Très RP | Ne concerne que quelques joueurs |

Le palier de Mérite reste affiché **en mots** (« Reconnu par ton village ») comme sur la fiche.

---

## 12. L'interface (maquettes)

### 12.1 Signature et décor (DA §1)

- **Signature : les koma de shōgi.** Pièces pentagonales en buis, kanji à l'encre ; noire = grade, rouge = fonction.
  On les retrouve partout : le plateau des sections (02), les pièces des villages sur le plateau diplomatique (05),
  l'auteur de chaque annonce (01) et de chaque ligne du journal (03), le cartouche du lecteur, la plaque du Kage et
  celle du membre (01, 06), le grade minimum d'un coffre (04).
- **Décor : le jinmaku**, rideau de camp en lés cousus tendu sur une corde, bandes sombres en haut et en bas,
  armoiries du village peintes au centre (texture du mod `gui/esc/village_<v>.png`, 2 GUI par texel) ; deux
  **kagaribi** (corbeilles à feu sur trépied) l'éclairent ; plancher laqué de la fiche ; nuit étoilée au-dessus.
- Rien de déjà pris : ni lanternes (fiche), ni inrō (sacoche), ni tokonoma, kamon, kawaraban (Échap), ni fuda (Poste de
  garde), ni tanzaku (Infirmerie), ni byōbu (Réglages), ni registre (Tablist), ni fiole (yeux), ni pierres de gué
  (création). ⚠️ Le jinmaku est un tissu tendu, comme le **noren** de l'échoppe : formes et usages différents, à
  confirmer.
- Kit commun inchangé : planches laquées pendues à des perches, onglets pendus (5 : 本 ACCUEIL, 組 SECTIONS,
  金 TRÉSOR, 蔵 COFFRES, 盟 DIPLOMATIE), plaque d'en-tête au nom de la faction avec son emblème, fermeture 閉,
  cartouche du lecteur (sa pièce, son nom, grade · fonction), aides en cabochons.

### 12.2 Les six maquettes

| # | Écran | Centre de la scène | Planche gauche | Planche droite |
|---|---|---|---|---|
| 01 | **Accueil** | Le **Kage réel** (personnage rendu) devant le jinmaku, plaque « HOKAGE » à ses pieds | Annonces (pièce de l'auteur, décret marqué d'un sceau 公) | Le village : membres, trésor, ta place, ta réputation, diplomatie (5 villages, état), Bingo Book, prochain conseil |
| 02 | **Sections** | **Plateau de shōgi** dressé : Kage → conseil, bras droit → 7 sections avec chef, nom et quota ; ANBU scellée ; légende des encres | Grades et quotas (effectif / quota, délai, « dont 2 sans clan ») | Section Genin : chef, effectif, accès, équipes et membres (clic → 06) |
| 03 | **Trésor** | **Senryōbako** (coffre à mille ryo cerclé de fer) et piles de koban ; barre « Verser un don · Allouer · Payer la solde » selon les droits | Solde, entrées et sorties sur 7 jours (barres), prochaine solde | Journal (filtres Tout / Entrées / Sorties, pièce de l'auteur, montants) |
| 04 | **Coffres** | **Nagamochi** laqué (long coffre à perche de portage, emblème doré, cordon de sceau) ; plaque « ARMURERIE · retirer : Chūnin et plus » | Ta banque : bourse, compte, guichet (montant, Déposer / Retirer), dernières opérations, ta solde | Coffres et droits (voir / déposer / retirer, scellés), contenu du coffre choisi, dernier retrait |
| 05 | **Diplomatie** | **Plateau** des 6 villages (pièces à l'emblème), fils d'alliance, tension, guerre ; Konoha à l'encre rouge | Relations (état, motif, depuis), proposition de trêve en attente (Accepter / Refuser grisés : le Kage décide) | Ta réputation par village (palier en mots, jauge sans chiffres) |
| 06 | **Fiche d'un membre** (vue chef de section) | Le **membre réel** (Ren Hyūga), plaque à son nom | Service : grade et ancienneté, section, fonction, clan, missions, Mérite, réputation, états de service | Décisions : conditions vers Chūnin (coches), Inscrire à l'examen, Proposer une fonction, sanctions RP, Rétrograder (Kage seul) |

### 12.3 Ouverture, rendu, performances

- **Touche proposée : J** (à confirmer, §16) et `/faction`. Sans faction (déserteur), l'écran montre son statut et le
  Bingo Book qui le concerne.
- Règles DA §6 : jinmaku, plancher, ciel, planches, plateau, coffres = **textures pré-dessinées** générées par
  script (`minecraft/mod-hud/tools/gen_faction_textures.py`, sur le modèle des autres générateurs) ; les **koma**
  dans un **atlas** (pièce vide par taille + kanji posés à l'exécution, ou atlas des kanji utilisés) ; seuls les
  flammes des kagaribi (3 images), les survols et les textes sont dessinés par le code ; plateau, fils et jauges en
  `LotGui` / `LotBlit` ; écran opaque (`EcranOpaque`).
- La maquette montre un **Jōnin chef de section** ; un Genin voit les mêmes onglets avec moins de boutons (trésor en
  lecture seule, coffres grisés, pas de colonne Décisions).

---

## 13. Commandes et permissions

### 13.1 Joueurs

| Commande | Qui | Effet |
|---|---|---|
| `/faction` (touche J) | tous | Ouvre l'écran |
| `/faction info [village]` | tous | Résumé dans le chat (sans mod) |
| `/faction don <montant>` | membres | Don au trésor |
| `/banque deposer\|retirer <montant>` | au guichet | Bourse ↔ compte |
| `/faction annonce <texte>` | Kage, conseil, chefs de section | Annonce (Accueil + chat de faction) |
| `/faction proposer <perso> <grade>` | chef de section, sensei | Proposition de promotion |
| `/faction fonction donner\|retirer <perso> <fonction>` | Kage ; chef de section pour sa section | Fonctions |
| `/faction sanction <perso> blame\|retenue\|suspendre\|exclure [durée] <raison>` | chef de section, Kage | Sanctions RP |
| `/faction tresor payer\|allouer\|acheter …` | intendant, Kage | Dépenses (plafonds §6.4) |
| `/faction coffre creer\|regler\|journal` | Kage, chef de section (ses coffres) | Coffres |
| `/faction reputation <perso> <+n\|-n> <raison>` | Kage, conseil (±50 / semaine) | Réputation dans son village |
| `/faction diplomatie proposer\|accepter\|refuser\|tension\|guerre <village>` | Kage | Diplomatie |
| `/bingo [voir\|ajouter\|retirer\|prime] …` | Kage, capitaine ANBU, chef de la police | Bingo Book |

### 13.2 Staff

`/faction admin creer|section|quota|delai|solde|journal|contrepasser|deserter|exiler|grace|diplomatie set|reputation set`.
Permissions : `shinobicore.faction.staff` (voir tout, contre-passer, valider guerre et désertion) et
`shinobicore.faction.admin` (créer et régler les factions). **Les droits des joueurs viennent de leurs fonctions**,
calculés par ShinobiCore : pas de nœuds de permission à entretenir à la main.

### 13.3 Poste de garde

Nouvel onglet **Factions** : liste des factions, effectifs et quotas, journal des trésors avec les alertes, guerres
et propositions en cours, désertions à valider, réglages (quotas, délais, barème de solde, barème de réputation).

---

## 14. Ce qui se fait où

| Partie | Où | Détail |
|---|---|---|
| Données : factions, sections, fonctions, quotas, membres | **ShinobiCore**, package `faction/` | `factions.yml` (modèle et réglages par village), `faction-data.yml` ou SQLite (membres, fonctions, files d'attente) |
| Trésor, comptes, journal | ShinobiCore `faction/tresor` | Journal en **ajout seul** ; la bourse reste le champ `ryo` du personnage, ajout d'un champ `compte` |
| Coffres | ShinobiCore `faction/coffre` | Meubles Nexo / coffres marqués, écoute de l'ouverture d'inventaire, droits, journal, vol (réutilise la fouille des bibliothèques) |
| Réputation, diplomatie, Bingo Book | ShinobiCore `faction/politique` | Barème dans `config.yml`, crochets sur les dégâts, le KO et la mort RP existants |
| Promotions | ShinobiCore `GradeCommand` / `GradeRules` | + quotas, délais, file d'attente, double signature |
| Bibliothèques | ShinobiAbilities `LibraryService` | Membres Palais / ANBU / Hôpital lus depuis la faction (API ShinobiCore) |
| Paquet vers le mod | Canal `reborn:faction` (JSON, comme la fiche) | Envoyé à l'ouverture et à chaque changement ; **filtré par droits** côté serveur (un Genin ne reçoit jamais le journal détaillé ni les membres ANBU) |
| Écran | **reborn-hud**, `fr.reborn.hud.menu.faction` | 5 onglets + fiche du membre ; textures `gui/faction/` générées par script |
| Panel web / API | Plus tard | Miroir en lecture pour le staff (même chemin que le casier, encore local) |

---

## 15. Étapes d'implémentation

| Lot | Contenu | Effort | Dépend de |
|---|---|---|---|
| **F1** | Modèle de données (factions, sections, fonctions), `/faction info`, attribution des fonctions, paquet `reborn:faction` | M | — |
| **F2** | Écran : Accueil + Sections en lecture (plateau, quotas, membres), annonces | M | F1 |
| **F3** | Quotas, délais, file d'attente, propositions de promotion, double signature, fiche du membre et sanctions RP | M | F1, F2 |
| **F4** | Bourse / compte, guichet, trésor, journal, solde hebdomadaire, onglet Trésor | M à L | F1 |
| **F5** | Coffres (droits, journal, quota, vol) + bibliothèques branchées sur les fonctions | M | F1 |
| **F6** | Réputation (barème, paliers), diplomatie, Bingo Book, désertion et exil, onglet Diplomatie | M à L | F1, F4 |
| **F7** | Onglet Factions du Poste de garde, alertes | M | F3 à F6 |
| **F8** | Missions : part du trésor, rangs ouverts par la réputation, décrets du Kage | selon la décision n° 13 | Bureau des missions |

Ordre conseillé pour la beta : **F1 → F2 → F3 → F4**, puis F5 et F6. F7 au fil de l'eau ; F8 avec le bureau des
missions. Chaque lot se termine par des captures du client de dev montrées avant toute publication (règle n° 2).

---

## 16. Questions à trancher par l'utilisateur

1. **Signature visuelle** : les koma de shōgi (encre noire = grade, rouge = fonction) sur décor de jinmaku et
   kagaribi, ça te va ? Ou une autre variante (bannières nobori, éventail de commandement) ?
2. **Sections** : la liste du §2 (Palais, Forces Jōnin, ANBU, Corps médical, police, bureau des missions, Académie,
   Genin) convient-elle ? Faut-il une « Racine » secrète ?
3. **Fonctions** : la liste du §3.1 te va-t-elle ? Le **Sannin** en titre honorifique (3 par village, ne combat pas
   en guerre), oui ou non ?
4. **Quotas** : on reprend **6 Jōnin dont 2 sans clan** ? Les autres quotas (TKJ 6, Konin 8, ANBU 6) et les
   **délais** (7 / 14 / 14 / 14 / 21 / 30 jours) ?
5. **Double signature** au-dessus de Jōnin (Kage + conseiller ou staff) contre le copinage : oui ?
6. **Sanctions RP** du chef de section (blâme, retenue sur solde, suspension, exclusion) : oui, et le blâme visible
   de la seule hiérarchie ?
7. **Bourse et compte** : on sépare le ryo en deux ? Fouille d'un corps à terre plafonnée à 5 000 ryo par scène ?
8. **Mort RP** : que deviennent la bourse (perdue, laissée sur le corps, héritée) et le compte (héritage à un autre
   personnage du même joueur, versé au trésor) ?
9. **Trésor** : 30 % des missions au trésor ? Solde hebdomadaire avec le barème du §6.3, réservée aux joueurs actifs
   (3 h par semaine), au prorata si le trésor est vide ?
10. Un **Genin** voit-il le solde du trésor en chiffres, ou seulement en mots ?
11. **Réputation** : échelle −1 000 / +1 000 avec paliers en mots (chiffre caché au joueur), barème automatique hors
    terrains d'entraînement, ajustement du Kage limité à ±50 par semaine : d'accord ?
12. **Diplomatie** : quatre états (alliance, paix, tension, guerre) ; guerre avec 24 h de préavis **et validation
    staff** ; effets du §9.1 ?
13. **Désertion** : compte du déserteur gelé ou saisi au profit du trésor ? Slot S bloqué 2 semaines ?
14. **Organisation indépendante type Akatsuki** : on la prévoit pour la beta ? Quel nom (« Akatsuki » est aussi le
    nom de notre identité visuelle) ?
15. **Mérite** : option A (prestige, seuil des fonctions et ordre de la file d'attente), B (crédit de réquisition) ou
    C (poids politique) ?
16. **Touche d'ouverture** : J ?
17. **Bureau des missions** (décision n° 13 du tableau de bord) : tenu par les joueurs, le staff ou les deux ? Il
    conditionne la principale entrée du trésor.
18. **Ordre de réalisation** : F1 → F4 pour la beta, le reste après ?
