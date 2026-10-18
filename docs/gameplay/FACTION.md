# Faction et sections

> **Statut : v3 validée le 2026-10-16 (questions tranchées, §14) ; socle codé et publié sur le dev le 16/10 (hud 0.4.184, manifest 3.1.141, ShinobiCore du 16/10 ; §15) ; validé en jeu le 17/10 ; coffres de section et portes à clé validés à coder le 17/10 (§11, §13) ; mécaniques par section plus tard (§12).** Game Plan n° 37
> ([`../GAMEPLAN.md`](../GAMEPLAN.md) §37). Intègre les **dix retours de l'utilisateur sur la v2 du 15/10**
> (consignés dans [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md), 16/10). La cloche d'annonces et les
> missives par corbeau ont leur fiche : [`CLOCHE_ET_MISSIVES.md`](./CLOCHE_ET_MISSIVES.md). Questions : §14 (Q1 à Q5 ;
> Q6 à Q8 dans l'autre fiche, 8 en tout).
>
> **Maquettes v3** (hors dépôt, 1920 × 1080, captures réelles du client de dev, kit du volet v3, **aucun kanji**) :
> `Maquettes/gameplan/37_faction/v3/`, script `_maquettes_faction_v3.py` : `01_volet_joueur_lambda.png`,
> `02_volet_chef_de_section.png`, `03_gestion_chef_de_faction.png`, `04_grades_de_section.png`,
> `05_anbu_non_membre.png` (et pour l'autre fiche `06_cloche_annonce.png`, `07_voliere_ecrire.png`,
> `08_voliere_missives.png`). Les maquettes v2 (dossier parent) restent valables pour le **Poste de garde staff**
> (`04_staff_poste_de_garde.png`) et l'**invitation en jeu** (`05_invitation.png`).
>
> Proposition du 13/10 gardée pour l'après-beta : [`FACTION_PROPOSITION.md`](./FACTION_PROPOSITION.md).

---

## 0. En dix lignes

1. Une **faction** est créée par le **staff** : nom, **tag de 3 lettres**, type (**village** ou **organisation**),
   emblème, **chef**. Les six villages existent d'office. Une faction par personnage, une faction par serveur (network).
2. Le panneau est un **volet à droite** ouvert par **J**, comme le Poste de garde : le jeu reste visible, rien en plein
   écran, plus d'entrée dans l'Échap.
3. **Chacun voit selon sa place** : un joueur sans grade ni droit voit **sa section** puis **le village** (membres et
   grades), sans plus ; les gradés voient en plus les sections ; le chef voit tout ; le staff aussi.
4. **On ne voit que les personnes qu'on connaît** (« se présenter ») : les autres sont **comptés, jamais nommés**, ou
   « Inconnu » dans sa propre section. Seul le chef du village est une **figure publique**.
5. Les **sections ne sont pas publiques**. L'**ANBU** (et toute section « cachée ») est un **« ? »** : ni chef, ni
   effectif, ni membres, et rien n'en part vers le client.
6. Les **sections se créent** par le **chef de faction** (modèle ou libre) et le **staff** ; le chef de section est
   nommé à la création.
7. **Deux étages de grades** : le **grade du village** = `/grade` (Genin → Kage), inchangé, le chef dit **à partir de
   quel grade** on a tel droit ; les **grades de section** sont **créés par le chef de section** (droits en cases,
   monter / descendre façon Flashback).
8. Le chef de section est **autonome** : il fixe les **places** (aucune limite du système), choisit la **tenue de
   section** (prêtée aux membres) et tient la **caisse de section**.
9. **Trésorerie** : chaque section a sa caisse ; le village prélève une **taxe unique** sur ses entrées (10 % par
   défaut) et ne puise jamais à la main dedans ; le trésor du village reste celui de la banque.
10. **Invitation** en face à face (menu R) **ou depuis le panneau** (personnes connues à 30 blocs, plus loin selon
    le grade de section). Chaque section aura **ses mécaniques** (menottes, soins, missions secrètes, cours…), **plus
    tard** (décision du 17/10), sur un **socle commun** livré maintenant.

> **17/10 — portes et zones** : une porte de section n'est **qu'un verrou avec clé**. **Aucune zone ne doit être
> inaccessible** : une porte verrouillée peut être **crochetée ou cassée** (si possible). Le reste des « zones de
> faction » (accès par grade, territoires fermés) **n'est pas voulu**. Les **coffres de section** sont validés à
> coder (§11, §13).

---

## 1. Ce qui change depuis la v2 (retours du 16/10)

| Retour de l'utilisateur | v3 |
|---|---|
| 1. Sections invisibles du public, sauf pour les gradés ; ANBU : un « ? », rien de visible | §4 : sections réservées aux gradés (seuil réglable) ; ANBU et sections cachées = « ? » pour tous les non-membres sauf le chef de faction et le staff ; filtrées côté serveur |
| 2. Création de sections | §5 : chef de faction (et charge déléguée) + staff ; modèles fournis |
| 3. Gestion et création de grades | §6 : grades du village = `Rank` + droits par seuil ; grades de section créés par le chef de section ; charges du village |
| 4. Tenue de section | §7 : une tenue choisie par le chef de section, prêtée aux membres (garde-robe) |
| 5. Trésorerie par section, taxe, autonomie | §8 : caisse par section, taxe unique sur les entrées, dotation par le village |
| 6. Mécaniques propres à chaque section | §12 : socle commun maintenant ; mécaniques vues plus tard (17/10) |
| 7. Volet à droite, sur une touche | §10 : volet v3 sur **J** |
| 8. Visibilité selon le joueur, seulement les gens connus | §4 : tableau par profil, règle de connaissance |
| 9. Inviter aussi depuis le panneau | §9 : menu R **et** panneau |
| 10. Pas de limite de places imposée | §5 : le chef de section fixe ses places |

Ce qui ne bouge pas de la v2 : faction créée par le staff, tag, six villages d'office, coffres de section **posés
par le staff** au Poste de garde (portes : verrou à clé, décision du 17/10, §11), journal `FACTION` en ajout seul, tag dans la tablist, quotas et délais simples dans
`/grade` (v2 §6), réputation / diplomatie / Bingo Book après la beta.

---

## 2. Ce qui existe déjà et qu'on réutilise

| Existant (code) | Usage |
|---|---|
| `ShinobiCharacter.village` | Appartenance au village (passer en « Déserteur » au F7 sort de la faction, comme en v2) |
| `Rank` (Académicien → Kage), `/grade`, `GradeRules` | **Grade du village**, inchangé |
| `ShinobiCharacter.contacts` + `RencontrerManager` (« se présenter », surnoms) | **Filtre de visibilité** : le volet n'affiche que les personnages présents dans les contacts du lecteur, **sous le nom qu'il connaît** (vrai nom ou surnom) |
| Banque (15/10) : trésor par village, solde du dimanche, guichet, virements à motif | Trésor du village ; la caisse de section est un **compte de plus** dans `banque.yml` |
| `ownedOutfits` (garde-robe du personnage), sacoche › Cosmétique › Tenue, cosmétiques 3D Nexo | Tenue de section prêtée, accessoires remis par le coffre de section |
| Poste de garde v3 (volet), `GardeArt`, `LotGui` | Gabarit du volet Faction ; outil staff Factions (maquette v2 04) |
| Menu R + règle Entrée / Retour | Invitation en face à face |
| Coffres et bibliothèques (membres, journal, vol) ; journal universel | Lieux de section ; journal `FACTION` |

---

## 3. La faction (inchangé)

Nom, tag (3 lettres, unique sur le serveur : KON, SUN, KIR, KUM, IWA, AME), type, emblème (village : son emblème réel ;
organisation : une liste dessinée), chef, **serveur** (network : une faction appartient à un serveur). Tout est réglé
par le staff au **F7 › Outils › Factions** (maquette v2 04). Une **organisation** n'a pas de grade du village : ses
grades se créent comme ceux d'une section (§6.3) ; pas de guichet ni de caisse à la beta.

---

## 4. Qui voit quoi (le cœur de la v3) — maquettes 01, 02, 05

### 4.1 Règles transverses

- **Connaissance** : un personnage n'apparaît **nommé** que s'il est dans les contacts du lecteur (« se présenter »),
  sous le nom appris. Sinon : **compté** dans la liste du village (« + 9 que tu ne connais pas », par grade), ou
  **« Inconnu »** avec son grade de section dans **sa propre** section (on sait qu'il existe, pas qui il est), ou
  absent partout ailleurs.
- **Figure publique** : le **chef de faction** (le Kage) est toujours nommé (Q1).
- **Section cachée** (l'ANBU par défaut, toute section cochée « cachée ») : pour qui n'en est pas membre, ni chef de
  faction, ni staff, c'est **un « ? »** dans l'onglet Sections des gradés, **rien** ailleurs ; ses membres apparaissent
  dans le village comme des membres ordinaires (leur grade du village), jamais rattachés à elle ; jamais ciblable par la
  cloche.
- **Filtré par le serveur** : le paquet `reborn:faction` ne contient que ce que le lecteur a le droit de voir (aucun
  membre ANBU, aucun nom inconnu, aucune caisse) : fouiller le mod ne révèle rien.
- **Registre** : le **chef de section** voit les noms de **tous** les membres de sa section (c'est son registre), même
  ceux qu'il n'a pas rencontrés.

### 4.2 Par profil

| Profil | Il voit | Il peut |
|---|---|---|
| **Joueur sans grade ni droit** (01) | **Sa section** s'il en a une (insigne, son grade de section, membres connus, « Inconnu » pour les autres, tenue prêtée, ses accès) ; **le village** : chef, membres **connus** groupés par grade du village, en ligne / absent, les autres comptés ; la **dernière annonce de la cloche** reçue | Quitter sa section ; porter la tenue de section |
| **Membre de section** | Pareil ; plus ce que ses **droits de grade de section** ouvrent (caisse en chiffres, journal de section…) | Ce que cochent ses droits (§6.3) |
| **Chef de section** (02, 04) | Le **registre** complet de sa section, sa caisse, ses grades, ses lieux, son journal | Places, grades de section, monter / descendre / exclure, inviter (R et panneau), tenue, caisse (au guichet), cloche pour sa section |
| **Gradé du village** (seuil « voir les sections », Chūnin par défaut) (05) | En plus : l'onglet **Sections** : insigne, nom, chef (s'il le connaît), effectif ; **ANBU = « ? »** ; clic → membres de la section **qu'il connaît** ; ni caisse, ni journal | Selon les autres seuils (cloche, volière du palais…) |
| **Chef de faction** (Kage) et **charges** déléguées (03) | Tout, ANBU comprise ; les caisses de section ; le journal de la faction | Créer / cacher une section, nommer un chef de section, taxe, seuils de droits, charges |
| **Staff** | Tout (Poste de garde) | Tout, journalisé ; ne fait aucune mécanique RP en mode staff (décision du 14/10) |

---

## 5. Sections — maquettes 03, 05

- **Qui crée** : le **chef de faction** (ou une charge qui en a le droit) depuis l'onglet Gestion, et le **staff**
  (Poste de garde) ; le staff peut renommer, cacher, dissoudre, et c'est lui qui **pose les lieux** (Q2).
- **Création** : un **modèle** (ANBU, Police, Corps médical, Académie, Bureau des missions, Garde des portes : nom,
  insigne, grades de départ et case « cachée » préremplis) ou une section **libre** ; nom, **insigne** (liste dessinée de
  la v2, sans écriture), **chef** (un membre du village), **places**, **cachée** oui / non.
- **Places** : nombre fixé par le **chef de section** (proposé à la création, 8 par défaut), modifiable à tout moment ;
  **aucune limite du système** ; l'invitation est refusée quand c'est plein.
- **Une section par personnage** à la beta (Q3). Le Conseil n'est pas une section : ce sont des **charges** (§6.2).
- **Dissoudre** : chef de faction ou staff, motif ; membres libérés, caisse versée au trésor du village, lieux détachés.

---

## 6. Grades — maquettes 03, 04

### 6.1 Grade du village = `Rank` (inchangé)

Académicien, Genin, Genin confirmé, Chūnin, Konin, Tokubetsu Jōnin, Jōnin, Commandant Jōnin, Kage : donnés par
`/grade` (règles, quotas et délais de la v2), **jamais créés ni renommés** par le chef. Le chef règle seulement **à
partir de quel grade** on a chaque droit du village (un seuil par droit, lisible, sans tableau à 9 lignes) :

| Droit du village | Seuil par défaut |
|---|---|
| Voir les sections | Chūnin |
| Sonner la cloche du village (tout le village) | Jōnin |
| Sonner sans délai (aucun anti-spam de la cloche, CLOCHE_ET_MISSIVES.md §2.5 ; le chef de faction l'a toujours) | Commandant Jōnin (config `communication.cloche.sans-delai-des`, tant que le chef ne l'a pas réglé) |
| Écrire à la volière du palais | Chūnin |
| Recevoir les alertes du palais | Jōnin |
| Prendre à une volière | Genin |

### 6.2 Charges du village

Conseiller, Intendant… : **créées par le chef de faction**, chacune avec des droits en cases (créer des sections, régler
la taxe, voir les caisses, journal de la faction, sonner la cloche, recevoir les alertes du palais, inviter dans une
organisation…). Une charge par personnage, un **bonus de solde** possible (réglé par le staff, comme le guichetier).
C'est la « fonction » de la v2, réduite à ce qui ne passe pas par le grade.

### 6.3 Grades de section (façon Flashback, dans notre style)

- Créés, renommés, **ordonnés** et supprimés par le **chef de section** ; **l'ordre est l'autorité** ; le premier est
  celui du chef (verrouillé), le dernier celui des nouveaux. Modèles fournis (Police : Commissaire, Inspecteur, Agent,
  Recrue ; Médical : Médecin-chef, Médecin, Infirmier, Recrue…).
- **Droits en cases** : inviter (face à face et panneau), inviter à distance, exclure, monter / descendre, voir la caisse
  en chiffres, dépenser la caisse (guichet), coffres de la section (voir / déposer / retirer, par coffre), sonner la
  cloche pour la section, lire le journal de la section.
- **Règles** : on ne monte, ne descend, n'exclut que **sous son propre grade** ; on ne donne que des droits qu'on a.
  **Jamais de stat** : un grade de section ne donne que des droits.
- **Fiche d'un membre** (clic) : monter, descendre, tenue prêtée, journal du membre, exclure (motif facultatif).

---

## 7. Tenue de section — maquettes 01, 02

- Le chef de section choisit **une tenue** parmi les tenues existantes (`CharacterCatalog`, calque du skin) : elle est
  **prêtée** à chaque membre tant qu'il l'est — elle apparaît dans sa garde-robe (sacoche › Cosmétique › Tenue, « Tenue
  de section ») sans entrer dans `ownedOutfits` ; elle disparaît quand il quitte la section.
- **Accessoires** (brassard, masque ANBU, gilet) : des **objets** Nexo, remis depuis le **coffre de la section** ; ils
  restent volables, comme tout cosmétique 3D.
- Une **nouvelle** tenue d'institution = demande au staff (tu dessines l'asset) ; jamais vendue en boutique (BOUTIQUES).
  Porter la tenue n'est **pas obligatoire** à la beta (Q5).

---

## 8. Trésoreries — maquettes 02, 03

| Caisse | Reçoit | Paie | Qui la voit / la tient |
|---|---|---|---|
| **Trésor du village** (banque existante) | Subvention staff, successions, missions, **taxe des sections** | Solde du dimanche, **dotations** aux sections | Chef de faction et charges ; en mots pour personne d'autre (retour 8 : le joueur lambda ne le voit pas) |
| **Caisse de section** (nouvelle, un compte dans `banque.yml`) | Dotations du village, recettes propres (amendes, soins payants, missions de section, plus tard), dons | Primes aux membres, achats, ce que décide le chef | Chef de section et grades « voir la caisse » ; chef de faction (chiffres) |

- **Taxe** : **un taux unique** pour toutes les sections, réglé par le chef de faction (10 % par défaut, 0 à 30 %),
  prélevé **automatiquement sur chaque entrée** d'une caisse de section, sauf les dotations du village (Q4).
- **Autonomie** : le village ne retire **jamais** à la main dans une caisse de section ; il dote, il taxe. Le staff
  garde la contre-passation (banque).
- **Où** : on **consulte** dans le volet ; on **dépense** au **guichet** du village, avec motif (comme un virement).
  Journal `TRESOR` de la banque + journal de la section.

---

## 9. Recruter, monter, exclure, quitter

| Action | Où | Qui | Règle |
|---|---|---|---|
| **Inviter (face à face)** | Menu R sur un joueur à 3 blocs : « Inviter dans la Police » (maquette v2 05) | Grade de section « inviter » | Même village, sans section, places libres |
| **Inviter (panneau)** | Volet › Ma section › **+ Inviter** : les personnes **connues à 30 blocs** (maquette 02) | Grade « inviter » | Mêmes règles ; un inconnu n'y figure pas |
| **Inviter à distance** | Même liste, élargie aux connus **en ligne** | Grade « inviter à distance » | Pour les sections dispersées |
| **Répondre** | En bas d'écran, deux lignes et un fil d'or | L'invité | **Entrée** accepte, **Retour** refuse ; 10 s en face à face, 30 s depuis le panneau |
| **Monter / descendre** | Fiche du membre | Grade « monter et descendre » | Sous son propre grade |
| **Exclure** | Fiche du membre | Grade « exclure », chef de faction | Motif facultatif, journalisé |
| **Organisation** | Menu R : « Inviter dans Kurogane » | Chef ou charge | Le joueur n'a pas de faction |
| **Quitter** | Volet › Ma section › Quitter | Soi | Pas son village (c'est le staff, F7) |

Chaque action envoie une ligne sobre à la personne concernée et au chef de section, et va au journal.

---

## 10. Le volet J — maquettes 01 à 05

- **J** ouvre et ferme (touche libre de la carte du 15/10, réglable) ; Échap ferme. Le **volet à droite** reprend le
  gabarit du Poste de garde v3 (202 px GUI, nuit, filet d'or, mots soulignés), **le jeu continue** derrière. En-tête :
  emblème réel du village, nom en capitales pixel, tag.
- **Onglets selon le profil** : **Ma section** (si on en a une) · **Village** · **Sections** (gradés) · **Annonces**
  (cloche reçues, alertes du palais) · **Gestion** (chef de faction, charges). Rien d'autre.
- Aucun kanji, aucun sceau ; insignes de section dessinés au trait (v2) ; visages = vraies têtes de skin, **capuche
  sombre** pour un inconnu.
- Rendu : textures générées par script, listes et textes en lots (`LotGui`) ; paquet `reborn:faction` filtré par profil
  et par contacts (§4.1), rafraîchi à l'ouverture et sur événement (pas de sondage).

---

## 11. Staff et lieux (Poste de garde, F7 › Outils › Factions) — maquette v2 04

Inchangé : créer une faction, nommer le chef, quotas et délais, journal, dissoudre ; **en plus** : sections (créer,
renommer, cacher, dissoudre, changer le chef), **coffres de section** (on vise le bloc), et la pose des
**cloches** et **volières** ([`CLOCHE_ET_MISSIVES.md`](./CLOCHE_ET_MISSIVES.md) §5). `/faction admin …` en secours.

**Portes — décision du 17/10** (remplace ~~portes de section posées par le staff, accès par grade, zones de faction~~) :

- une porte se protège **uniquement par un verrou avec clé** : qui a la clé ouvre (la clé est un objet, elle se
  prête, se perd, se vole) ;
- **aucune zone n'est inaccessible** : une porte verrouillée peut être **crochetée** ou **cassée** (si c'est faisable
  techniquement), ce qui laisse une trace (journal, bruit) mais jamais un mur infranchissable ;
- **pas d'autres zones de faction** : ni zones à accès par grade, ni territoires fermés.

**Coffres de section — validés à coder le 17/10** (après la refonte des états du joueur) : coffres posés par le staff,
rattachés à une section, voir / déposer / retirer selon les droits du grade de section (§6.3), journal `FACTION`.

---

## 12. Mécaniques propres à chaque section — plus tard (17/10)

**Socle commun livré à la beta** : droits de section (§6.3), coffres de section (à coder) et portes à clé (§11),
caisse, tenue prêtée, cloche ciblée sur la section, volière, journal de section. Chaque mécanique ci-dessous
s'y branche, une fiche à la fois. **17/10 : vues plus tard, pas maintenant** (remplace ~~à coder au lot suivant~~,
synchro des boards du 16/10) :

| Section | Mécaniques à venir | S'appuie sur |
|---|---|---|
| **Police** | Menottes, arrestation, **prison** (état du joueur), amendes versées à la caisse, coffre des saisies, avis de recherche | GAMEPLAN §13, états du joueur, banque |
| **Corps médical** | Soins avancés (droit de jeu `shinobicore.medic` donné par la section), hôpital, soins payants, garde de nuit | Infirmerie, KO et mort RP |
| **ANBU** | Missions secrètes, **noms de code** entre membres, masque qui cache l'identité, bibliothèque ANBU (au lieu de `shinobiabilities.biblio.anbu`) | Connu / inconnu, masques (§14) |
| **Académie** | Cours, examens (Genin, Chūnin), inscription des élèves, carnet de l'élève | `/squad`, `/grade` |
| **Bureau des missions** | Registre, attribution, récompenses, part au trésor | GAMEPLAN §22 |
| **Garde des portes** | Contrôle aux portes, registre des entrées, laissez-passer | Portes à clé (§11) |
| Toutes | **Appel de service** (« appeler l'hôpital, la police ») : le premier membre en service qui accepte le prend | GAMEPLAN §18.3 |

---

## 13. Lots d'implémentation (beta)

| Lot | Contenu | Dépend de |
|---|---|---|
| **F1** | Données (`ShinobiCore/faction/` : factions, sections, grades de section, charges, seuils, membres), 6 villages d'office, tag, journal `FACTION`, `/faction admin`, **paquet `reborn:faction` filtré** (profil + contacts + sections cachées) | — |
| **F2** | Poste de garde › Factions (v2 04) : factions, chefs, sections, quotas | F1 |
| **F3** | **Volet J** en lecture : Ma section, Village, Sections, Annonces ; inconnus comptés ; « ? » | F1 |
| **F4** | Gestion : création de section, grades de section et fiche du membre, places, seuils et charges, invitations **R et panneau**, exclure, quitter | F3 |
| **F5** | **Coffres de section** posés par le staff, droits par grade de section ; **portes à clé** (verrou + clé, crochetage / casse, aucune zone inaccessible) — validés à coder le 17/10 | F2, F4, états du joueur |
| **F6** | Caisse de section, taxe, dotation (banque), tenue de section prêtée | F4, banque |
| C1-C3, V1-V4 | Cloche, volières, missives : [`CLOCHE_ET_MISSIVES.md`](./CLOCHE_ET_MISSIVES.md) §8 | F1, F3 |
| plus tard | Mécaniques de section (§12) — vues plus tard (17/10) | F5 |
| après | Réputation, diplomatie, Bingo Book, désertion encadrée (plus de « zones de faction » : décision du 17/10) | F5 |

Chaque lot finit par des captures du client de dev montrées avant toute publication, et le YAML passé au parseur.

---

## 14. Questions — tranchées le 16/10

Réponses de l'utilisateur ([`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md), 16/10) :

1. **Figure publique** : **oui**, seul le chef du village (le Kage) est nommé pour tous. Une charge peut être cochée
   « publique » (case prévue, décochée par défaut).
2. **Création de sections** : **si le chef de faction est le Kage, création libre** ; le staff garde la vision : tout
   est visible et journalisé côté staff, corrigeable au Poste de garde (Outils › Factions), et c'est lui qui pose les
   lieux.
3. **Une section par personnage** à la beta : **oui**.
4. **Taxe** : **définissable par le Kage** (le chef de faction règle le taux dans l'onglet Gestion), un taux unique pour
   toutes les sections, sur les **entrées** (jamais les dotations) ; **10 % par défaut**, **plafond configurable par le
   staff** (`faction.taxe.plafond` de la config de ShinobiCore, 30 % par défaut).
5. **Tenue de section** : **prêtée** tant qu'on est membre, **non obligatoire** : c'est géré en RP, aucune contrainte
   mécanique.

Pour l'autre fiche (consigné ici, codé avec elle) : **Q8** annonce de la cloche visible des visiteurs et seuil de grade
pour sonner : **oui** ; **missives** : le destinataire **est prévenu à l'arrivée** (justification RP : un clan, par
exemple Yamanaka ou un clan caché, transmet l'information par l'esprit), **aucune limite d'envoi**, seulement le coût en
ryo.

<details><summary>Questions telles que posées (v3)</summary>

1. **Figure publique** : le chef du village est-il **toujours nommé** pour tous (même sans s'être présenté) ? Faut-il
   pouvoir rendre publiques d'autres personnes (conseil) ? — *Reco : le Kage seul, d'office ; une case « publique » sur
   une charge si tu veux un conseil connu de tous.*
2. **Création de sections** : le chef de faction crée **librement** (journalisé, le staff peut cacher ou dissoudre),
   ou chaque création attend une **validation du staff** ? — *Reco : libre ; le staff pose de toute façon les lieux,
   c'est son contrôle naturel.*
3. **Une seule section par personnage** à la beta ? — *Reco : oui.*
4. **Taxe** : un taux unique, sur les entrées, 10 % par défaut, plafond 30 % ? — *Reco : oui.*
5. **Tenue de section** : prêtée, non obligatoire ? — *Reco : oui.*

</details>

---

## 15. Ce qui est codé (16/10, publié sur le dev : hud 0.4.184, manifest 3.1.141)

**Serveur — ShinobiCore, package `faction/`** (référence : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §4 ter) :

- **Registre et migration** : au démarrage, chaque personnage connu du registre des N° est inscrit (nom, grade du
  village, village) et **rattaché à la faction de son village** (Konohagakure → KON…), **sans section** ; tenu à jour à
  chaque connexion / changement de personnage et toutes les 30 s pour les joueurs en ligne. Passer « Déserteur » sort de
  la faction (et de la section) ; une mort RP libère la section.
- **Factions** : les six villages d'office (KON, SUN, KIR, KUM, IWA, AME, emblème réel) ; organisations créées par le
  staff (tag de 3 lettres, emblème dessiné, section racine qui porte leurs grades ; membres = personnages sans village).
- **Sections** : créées par le chef de faction (ou une charge « sections ») et le staff, modèle (ANBU, Police, Corps
  médical, Académie, Bureau des missions, Garde des portes) ou libre ; insigne, chef, places (8 par défaut, réglage
  `faction.sections.places-defaut`), cachée oui / non ; renommer, cacher, changer de chef, dissoudre (membres libérés,
  caisse au trésor).
- **Grades de section** : créer (au-dessus du dernier), renommer, ordonner, supprimer (membres → dernier grade), droits
  en cases ; monter / descendre / exclure **sous son propre grade** ; on ne donne que des droits qu'on a.
- **Seuils du village** (voir les sections, cloche, volières : réglés par le chef, utilisés par la cloche et les volières depuis le 16/10) et
  **charges** (droits en cases, une par personnage, case « publique »).
- **Visibilité filtrée par le serveur** (`Regles`, `FactionVue`) : inconnus comptés jamais nommés (ni nom, ni UUID, ni
  N°), « Inconnu(e) » dans sa propre section, registre complet pour le chef de section, Kage figure publique, section
  cachée = `{"q":true}` pour qui n'en est pas (ni nom, ni chef, ni effectif), caisses et journaux selon les droits.
  Vérifié par un banc de 28 cas sur les règles pures (visibilité, taxe, « sous son grade »).
- **Invitations** : face à face (menu R, `/faction inviter`, 3 blocs, 10 s) et depuis le volet (personnes **connues** à
  30 blocs, ou en ligne avec « inviter à distance », 30 s) ; Entrée / Retour chez l'invité ; refus si autre village,
  déjà en section, section pleine.
- **Caisse et taxe** : une caisse par section ; chaque entrée est taxée au taux du Kage et la taxe versée au trésor du
  village (banque) ; au guichet (6 blocs) : `/faction verser`, `/faction prime` (droit « dépenser »), `/faction doter`
  (chef ou charge « doter », jamais taxée) ; correction staff motivée.
- **Tenue de section prêtée** : portable tant qu'on est membre (garde-robe de la boutique de tenues), jamais ajoutée à
  `ownedOutfits`, retirée en quittant la section.
- **Annonces** publiées depuis le volet (village : seuil « cloche du village » ou charge « annonces » ; section : droit
  « annoncer à la section »), une ligne sobre aux membres en ligne ; la cloche du village (16/10) les reprend dans l'onglet Annonces.
- **Journal** : catégorie universelle **`FACTION`** (ajout seul) + lignes courtes par section et par faction pour le
  volet. **Tag** de faction dans la tablist (après le grade).
- Persistance YAML `plugins/ShinobiCore/factions/` (`factions.yml`, `membres.yml`).

**Client — mod-hud, package `faction/`** : volet **J** (maquettes v3 01 à 05 reproduites, captures
`Maquettes/gameplan/37_faction/code/`), invitation au HUD (deux lignes, fil d'or, insigne), « Inviter dans … » en tête
du menu R, outil staff **F7 › Outils › Factions** (`staff/GardeFactions`). Banc `REBORN_FACTION_DEBUG`
([`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5).

**Pas dans ce chantier (lots suivants)** : coffres de section et portes à clé (F5, validés à coder le 17/10),
guichet intégré (dotation et dépense depuis
l'écran du guichet ; d'ici là, les commandes au guichet), quotas et délais de `/grade` (v2 §6), mécaniques propres à
chaque section (§12, plus tard), réputation, diplomatie, Bingo Book, plusieurs sections par personnage.
La cloche et les volières, prévues ici à l'origine, sont livrées dans la même publication (hud 0.4.184) :
[`CLOCHE_ET_MISSIVES.md`](./CLOCHE_ET_MISSIVES.md).
