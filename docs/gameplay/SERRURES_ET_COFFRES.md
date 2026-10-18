# Serrures, clés et coffres de section

> **Mis de côté le 18/10/2026** (décision de l'utilisateur) : proposition en réserve, questions non tranchées, rien de codé.

> **Statut : proposition du 2026-10-17, maquettée, rien de codé.** Suite des décisions de l'utilisateur du 17/10 :
> **coffres de section validés** (droits voir / déposer / retirer par grade de section, quota, journal, vol) ; pour les
> portes, **uniquement un verrou à clé** ; **aucune zone inaccessible** : une porte verrouillée se **crochète** ou se
> **force**, pas de « zone de faction » interdite. Précise le lot **F5** de [`FACTION.md`](./FACTION.md) (§11, §13) et
> [`../GAMEPLAN.md`](../GAMEPLAN.md) §29 (stockage) et §48 (vols). Questions : §13 (8).
>
> **Maquettes** (hors dépôt, 1920 × 1080, fonds = captures du client de dev, porte et fissures = textures du client
> 26.2, **aucun kanji**) : `Maquettes/gameplan/serrures_coffres/`, script `_maquettes_serrures.py` (réutilise le kit
> de `mod-hud/tools/style/` et les pièces des maquettes Faction v3 et cloche / volière v3c) :
> `01_porte_verrouillee.png` (menu R de la porte, visée sans clé, avec la clé), `02_crochetage.png` (écran centré,
> goupilles ; réussite, crochet cassé), `03_forcer.png` (fissures, filet d'action ; porte enfoncée, alerte de la
> section), `04_coffre_section.png` (écran du coffre ; onglet Droits, coffre crocheté), `05_volet_cles.png` (J › Lieux :
> clés de la section ; info-bulles clé et trousseau, droit de grade), `06_staff_poser.png` (Poste de garde : poser une
> serrure, liste des lieux ; poser un coffre).

---

## 0. En dix lignes

1. Le **staff** pose une **serrure** sur une porte existante (vanilla ou meuble Nexo) ou un **coffre de section**, en
   la visant : nom, **propriétaire** (une section, la faction, ou « HRP »), **niveau** (simple, renforcée, scellée).
2. Une porte verrouillée ne s'ouvre **qu'avec sa clé** : un **objet** numéroté, n'importe où dans la sacoche ou dans
   un **trousseau**. **Aucun grade n'ouvre sans clé**, pas même le Kage.
3. La **section tient ses clés** : le chef (ou le grade qui a « Clés de la section ») les **taille** dans J › Lieux
   (prix pris dans la caisse), les remet **en main propre**, les **révoque** une à une ; « changer la serrure » révoque
   tout.
4. Une clé se **donne, se perd, se vole** (fouille d'un corps). Pas de copie par les joueurs à la beta, **aucun
   passe-partout** en objet ; le staff ouvre en mode staff, journalisé.
5. **Rien n'est fermé pour toujours** : sans clé, menu R sur la porte : **Crocheter**, **Forcer**, **Frapper**.
6. **Crocheter** : un crochet, un **petit écran centré** (caler 3 à 5 goupilles sur une ligne d'or), 5 à 20 s ; un
   pas ou un coup interrompt ; chaque faux pas s'entend ; deux, le crochet casse. Réussi, c'est **silencieux** mais
   laisse des traces.
7. **Forcer** : une arme ou un outil en main, 10 à 50 s, **très bruyant** (48 blocs), fissures visibles, la section en
   ligne est prévenue ; la porte reste **enfoncée** (ouverte) jusqu'à réparation.
8. **Coffre de section** : meuble posé par le staff ; les membres l'ouvrent **selon leur grade de section** (voir,
   déposer, retirer, quota par jour), journal ; pour un étranger, la même serrure : vol plafonné.
9. Crocheter, forcer et réparer sont des **actions longues** de l'`EtatService` (`OCCUPE(...)`) : la matrice des
   états les refuse ou les interrompt ; jamais en mode staff.
10. Tout va au **journal** (`LIEU`), avec des délais anti-abus ; aucun calcul par tick.

---

## 1. Ce qui existe et qu'on réutilise

| Existant | Usage |
|---|---|
| Faction v3 : sections, grades de section à droits en cases (§6.3, dont « coffres : voir / déposer / retirer »), caisse de section (§8), lieux posés par le staff (§11), journal `FACTION` | Propriétaire d'une serrure ; droits des coffres ; prix des clés et réparations ; deux droits de plus (§3.3) |
| Volet J v3 : « Lieux de la section » (ligne repliée de « Ma section ») | Écran des clés du chef (maquette 05) |
| Poste de garde v3, outil Factions (maquette v2 `37_faction/04_staff_poste_de_garde.png` : Porte…, Coffre…) | Pose staff, liste des lieux (maquette 06) ; on remplace « Ouvre : Hokage, Conseiller » par les clés |
| Pose des meubles de la cloche et des volières (`/cloche poser`, visée à 6 blocs, barrières du meuble, entrées du menu R par meuble) | Même mécanique pour les serrures et les coffres |
| `VolService` (bibliothèques) et `FouilleService` (corps) : fouille immobile, objet marqué `reborn:vole_a`, alerte `vol` | Vol dans un coffre ; marquage des objets pris |
| Menu R : entrée morte « Verrouiller / Cadenas » (`lock`, GAMEPLAN §14, « constat du 13/10 ») | **Remplacée** par les entrées de la porte, calculées par le serveur |
| `EtatService` (refonte en trois couches, [`../technique/ETATS_JOUEUR.md`](../technique/ETATS_JOUEUR.md)) : modificateur `OCCUPE(action)`, matrice des capacités | Conditions et interruptions (§7) |
| Bande nette en haut d'écran (cloche, missive : `BandeHud`), filet d'action (préparer, fouiller) | Alerte de la section ; jauge de la casse |
| Paper `Player#sendBlockDamage`, sons vanilla | Fissures visibles sur la porte ; bruits, avant nos sons |
| Nexo provisoire généré par script (`gen_communication_textures.py`) | Clés, crochet, trousseau, plaques de serrure, coffre (§8) |

---

## 2. Les serrures

### 2.1 Ce qu'on verrouille

- Portes en bois vanilla (les **deux battants** d'une porte double = une seule serrure), trappes, portillons ; portes
  en **meuble Nexo** (shōji coulissant, plus tard) ; **coffres de section** (§6).
- Une **plaque de serrure** est affichée sur la porte (objet posé par le serveur, `reborn:serrure_*`) : on voit qu'une
  porte est fermée à clé, et de quel niveau ; elle tombe au sol quand la porte est enfoncée.
- Rien d'autre n'ouvre une porte verrouillée : ni redstone, ni villageois, ni piston.
- Données : identifiant (`S-0042`), nom, position(s), propriétaire, niveau, état (**verrouillée**, **laissée ouverte**,
  **enfoncée**), refermeture (après 3 s ou à la main), clés en service, dégâts en cours, traces de crochetage.

### 2.2 Niveaux (réglables, `lieux.niveaux.*`)

| Niveau | Crocheter | Forcer à la hache | Tailler une clé | Réparer | Pour |
|---|---|---|---|---|---|
| **Simple** | 3 goupilles, ~5-8 s | 10 s | 30 ryo | 100 ryo | Réserves, ateliers |
| **Renforcée** | 4 goupilles, fenêtre plus étroite, ~8-14 s | 25 s | 60 ryo | 200 ryo | Postes, armureries |
| **Scellée** (fer et papier de sceau) | 5 goupilles au rythme irrégulier, ~12-20 s | 50 s | 120 ryo | 300 ryo | Palais, ANBU, cellules |
| **HRP** | impossible | impossible | — | — | Lieux **hors jeu** seulement (spawn, salles staff) : posé par le staff, jamais sur un lieu RP |

Le niveau est choisi par le staff à la pose ; le chef de section le **demande** (ticket) pour le changer.

### 2.3 Ouvrir, laisser ouvert, frapper — maquette 01

- **Avec la clé** (sacoche, trousseau ou main) : **clic droit**, la porte s'ouvre comme une autre et **se referme
  seule** après 3 s (réglé à la pose). **Maj + clic droit** : la **laisser ouverte** (heures de service), puis la
  reverrouiller ; c'est écrit au journal.
- **Sans clé** : clic droit = un cliquetis (`block.chest.locked`) et un petit « Verrouillée » sous le viseur.
- **Viser + R** : le **menu R de la porte** (gabarit réel, titre = le nom du lieu), entrées grisées avec leur raison :
  *Ouvrir avec la clé* · *Crocheter* (niveau, crochets portés) · *Forcer* (« on t'entendra de loin ») · *Frapper à la
  porte* (trois coups, entendus à 16 blocs à l'intérieur ; le moyen poli d'entrer).

---

## 3. Les clés — maquette 05

### 3.1 L'objet

- `reborn:cle` (trois apparences : laiton pour simple, fer pour renforcée, fer et papier de sceau pour scellée), une
  par case, 0,05 kg. Données : serrure (`S-0042`) et **numéro**. Info-bulle : « Clé · *gravure* », « n° 2 · serrure
  renforcée », les gestes. La **gravure** est le nom du lieu ou **« sans marque »** (ANBU, caches : une clé volée ne
  dit pas ce qu'elle ouvre).
- **Trousseau** `reborn:trousseau` : jusqu'à 8 clés dans une case (même principe que le sac vanilla, filtré aux clés).
  Voler un trousseau, c'est voler toutes ses clés.
- Une clé n'ouvre que si **son numéro est en service** pour sa serrure.

### 3.2 Tailler, remettre, révoquer (J › Ma section › Lieux)

- **Tailler** : « + Tailler une clé », prix du niveau **pris dans la caisse de section** (journal `TRESOR`) ; la clé
  arrive dans la sacoche de celui qui la taille ; il la **remet en main propre** (menu R › Tendre). À la pose, le staff
  offre 1 à 3 clés (à tailler sans frais).
- **Registre** : chaque numéro, son **dernier porteur connu** (mis à jour quand la clé entre dans l'inventaire d'un
  joueur en ligne : ramassée, tendue, prise dans un coffre, fouillée), la date. Vu du chef et des grades « Clés », de
  personne d'autre.
- **Révoquer** un numéro : l'objet existe encore mais **n'ouvre plus rien** (« clé hors d'usage »). Un membre qui
  **quitte** la section ou est exclu : sa clé passe en ambre, « a quitté la section », le chef décide (il a pu la
  prêter). **Changer la serrure** : tout est révoqué, on retaille.
- **Faction** (palais, réserve) : mêmes écrans dans J › Gestion, pour le chef de faction et les charges « lieux ».

### 3.3 Deux droits de grade de section en plus (FACTION §6.3)

- **Clés de la section** : tailler, remettre, révoquer, changer la serrure, réparer.
- **Alertes des lieux** : recevoir la ligne « On force la porte… » (§5) et « Le coffre … a été fouillé » (§6).

### 3.4 Ce qu'il n'y a pas à la beta

Pas de copie par les joueurs (après la beta : **empreinte à la cire** chez un forgeron, une entreprise) ; **pas de
passe-partout** en objet (volé, ce serait une catastrophe) ; pas de serrure personnelle (maisons : avec le logement,
après la beta). Le **staff** ouvre tout **en mode staff** (journal `STAFF`) ; en **mode animation**, `/serrure cle`
donne une clé temporaire qui disparaît à la fin de l'animation. Mort RP : les clés restent sur le corps, fouillables.

---

## 4. Crocheter — maquette 02

- **Il faut** un **crochet** (`reborn:crochet`, vendu à l'échoppe d'outils, 40 ryo ; saisissable, volable) et un état
  qui le permet (§7). Une seule personne à la fois sur une serrure.
- **L'écran** : centré, compact, **non opaque** (le jeu reste visible, on reste exposé) ; signature : la **coupe de la
  serrure** en pixel art (corps de fer, rotor de laiton, ligne d'or, crochet et clé de tension). La goupille active
  monte et descend ; **Espace** quand elle touche la ligne d'or : elle reste **calée**, on passe à la suivante.
  Échap abandonne.
- **Faux pas** : la goupille retombe, un **cliquetis s'entend à 12 blocs**. **Deux faux pas** : le **crochet casse**
  (perdu), bruit à 16 blocs, 10 s avant de reprendre cette serrure.
- **Durée** : 5 à 20 s ; une **durée minimale** par niveau (5, 8, 12 s) même pour un joueur parfait. Le **serveur** tient
  la graine du rythme, l'horloge et la position de la goupille ; le client anime et envoie des appuis horodatés
  (tolérance 150 ms). Un appui impossible est refusé (anti-macro).
- **Interrompu** si l'on bouge (> 0,45 bloc), reçoit un coup, tombe, est porté ou entravé, ou ferme l'écran : les
  goupilles retombent, le crochet est gardé.
- **Réussi** : la porte s'ouvre **une fois** (elle se referme, toujours verrouillée). Rien ne prévient la section ;
  la serrure garde **des rayures fraîches** 1 h (vues de qui l'inspecte : « Des rayures fraîches sur la serrure ») et
  le journal de la section note « **crochetée par un inconnu** » (le staff voit le nom).

---

## 5. Forcer — maquette 03

- **Il faut** une **arme ou un outil en main** : menu R › *Forcer*, puis **frapper la porte** (clic gauche maintenu, à
  3 blocs). Hache ou masse : débit plein (10 / 25 / 50 s) ; sabre : ×0,6 ; kunai, tantō : ×0,25 ; mains nues : non.
  **À deux**, les débits s'additionnent (deux au plus). Les techniques de destruction (doton, explosifs) : après la beta.
- **Visible** : les **fissures du jeu** (`destroy_stage` 0 à 9) sur la porte, montrées à tous à 32 blocs ; des éclats
  de bois. **Audible** : chaque coup à **48 blocs** (`entity.zombie.attack_wooden_door`), la casse
  (`entity.zombie.break_wooden_door`).
- **HUD** : rien de plein écran ; un **filet d'action** fin en bas (« Forcer la porte · 62 % · on t'entend de loin »).
- **Alerte** : au premier coup, une **bande nette** chez les membres **en ligne** de la section propriétaire qui ont
  « Alertes des lieux » : « POLICE — On force la porte du poste de police. » (une fois par 5 min et par serrure) ;
  alerte `lieu` au Poste de garde.
- **Les dégâts restent sur la porte** : un coup reçu arrête celui qui force, pas la porte ; sans coup pendant 1 h,
  les fissures s'effacent.
- **Enfoncée** : battants ouverts, fendus, **plaque arrachée** au sol ; plus de serrure, plus de clé utile ; au survol,
  « Porte enfoncée · ouverte jusqu'à réparation ».
- **Réparer** : un membre qui a « Clés de la section » (menu R › *Réparer*, 10 s immobile, prix du niveau pris dans la
  caisse) ou le staff (gratuit). Les clés en service restent bonnes ; on peut changer la serrure dans la foulée.

---

## 6. Coffres de section — maquette 04

- **L'objet** : meuble Nexo `reborn:coffre_section` posé par le **staff** (F7 › Coffre…, `/coffre poser`), attaché à
  **une section** (ou à la faction : coffre du palais) ; taille 9, 18, 27 ou 36 cases ; niveau de serrure ; vol permis
  ou interdit.
- **Les membres n'ont pas de clé** : ils l'ouvrent **selon leur grade de section** : **voir**, **déposer**, **retirer**,
  et un **quota de retraits par jour** (par grade ; le chef : sans limite ; remis à zéro à 6 h). Réglé par le chef dans
  l'onglet **Droits** de l'écran du coffre ; on ne coche que les droits qu'on a. Case « **caché aux autres sections** »
  (ANBU : le meuble est là, son nom et sa section ne s'affichent pas).
- **L'écran** (centré, signature : le **coffre à ferrures**) : grille du coffre, sacoche (barre et sac), tes droits,
  retraits du jour, quatre derniers mouvements, onglet **Journal** (grades « journal »), onglet **Droits** (chef).
  Glisser pour déposer, double-clic pour prendre, Maj + clic : la pile. La bourse s'y range ([`MONNAIE_ET_BANQUE.md`](./MONNAIE_ET_BANQUE.md) §3).
- **Pour un étranger** : menu R sur le coffre › *Crocheter* ou *Forcer* (mêmes règles, mêmes niveaux), **seulement si
  au moins un membre de la section est en ligne** (« Personne ne garde ce coffre. »). Réussi : l'écran s'ouvre **en
  fouille** (prendre seulement), **9 objets au plus** par intrusion, chacun marqué « volé à la Police »
  (`reborn:vole_a`) ; bouger ferme l'écran ; puis **30 min de répit** pour ce coffre. Crocheté, il se reverrouille à la
  fermeture ; forcé, il reste ouvert en fouille (9 objets par personne) jusqu'à réparation.
- **Alerte** : dès qu'un objet est pris, bande « Le coffre des saisies a été fouillé. » aux membres en ligne qui ont
  « Alertes des lieux », alerte `vol` au Poste de garde. Le journal du coffre écrit « crocheté par un inconnu · 3
  objets pris ».
- **Technique** : inventaire tenu par le serveur et partagé entre les lecteurs (pas de copie côté client), écrit à la
  fermeture et toutes les 60 s s'il a changé ; chaque mouvement est une transaction journalisée (liste anti-duplication,
  GAMEPLAN §40.3).

---

## 7. Place dans les états du joueur (refonte en trois couches)

Pour la session qui code l'`EtatService` : **aucune capacité nouvelle**, trois valeurs de `OCCUPE` et une note de
matrice. Tant que le service n'est pas livré, on reprend le gabarit de `VolService` (immobilité + coup reçu).

| Action | Modificateur | Refusée si | Prend fin si |
|---|---|---|---|
| **Crocheter** | `OCCUPE(crocheter)` (famille SOCIAL, perdu à la déconnexion et au KO, visible de soi) | `INTERAGIR` ou `UTILISER_OBJET` refusé ; Vital ≠ `VALIDE` (en douleur, les mains tremblent) ; Contrôle ≠ `LIBRE` ; `EN_COMBAT` ; `MENOTTES` ; cadre `STAFF`, `CONSTRUCTION`, `HORS_JEU` | coup reçu, mouvement > 0,45 bloc, changement de couche Vital ou Contrôle, écran fermé |
| **Forcer** | `OCCUPE(forcer)` | `FRAPPER(arme)` refusé (`ATA`, `MENOTTES`, douleur…) ; Contrôle ≠ `LIBRE` ; mêmes cadres. **Permis en combat** (un assaut) | coup reçu (la porte garde ses dégâts), éloignement > 3 blocs, KO |
| **Réparer** | `OCCUPE(reparer)` | comme crocheter | comme crocheter |
| **Fouiller un coffre** ouvert | `OCCUPE(voler)` (existant) | comme la fouille d'un corps | bouger, coup reçu |

- Matrice §4.6, note ⁹ de `EN_COMBAT` : « ni fouille, ni soin tenu, ni séance, ni vol, **ni crochetage**, ni changer de
  personnage ». Raisons affichées dans le menu R grisé : « en combat », « mains liées », « tu es blessé ».
- **Prison** (cadre `PRISON`) : une cellule est une porte à serrure scellée ; un détenu qui a gardé un crochet et n'est
  pas menotté peut la crocheter. L'évasion reste possible, jamais garantie.
- **Mode staff** : aucune de ces actions (décision du 14/10) ; le staff ouvre et répare par ses outils.

---

## 8. Objets Nexo à modéliser (par toi) — identifiants stables

| Identifiant | Type | Rôle | Provisoire |
|---|---|---|---|
| `reborn:cle` | objet (`item_model` à 3 états : `cle_laiton`, `cle_fer`, `cle_scellee`) | La clé, selon le niveau | sprites 16 × 16 (maquettes 04, 05) |
| `reborn:trousseau` | objet (sac vanilla filtré) | Jusqu'à 8 clés | sprite 16 × 16 |
| `reborn:crochet` | objet | Crochet et clé de tension, manche laqué | sprite 16 × 16 |
| `reborn:serrure_simple`, `_renforcee`, `_scellee`, `_arrachee` | objet affiché (ItemDisplay, échelle 0,5) | Plaque de serrure sur la porte ; arrachée au sol | sprites 6 × 9 |
| `reborn:coffre_section` | meuble (hitbox : 1 barrière) ; état `_force` | Coffre à ferrures de la section | cubes et signature de l'écran |
| `reborn:porte_shoji` | meuble (après la beta) | Porte coulissante verrouillable | — |

Sons (vanilla en attendant) : `reborn:serrure.verrouillee` (`block.chest.locked`), `reborn:serrure.ouvre`
(`block.iron_trapdoor.open`), `reborn:crochet.goupille` (`block.tripwire.click_on`), `reborn:crochet.faux_pas`
(`block.chain.hit`), `reborn:crochet.casse` (`entity.item.break`), `reborn:porte.frapper` (`block.wood.hit` × 3),
`reborn:porte.forcer` (`entity.zombie.attack_wooden_door`), `reborn:porte.enfoncee`
(`entity.zombie.break_wooden_door`).

---

## 9. Staff — maquette 06

| Commande / outil | Grade | Effet |
|---|---|---|
| F7 › Outils › Factions › *faction* › **Porte…** / **Coffre…** | Modérateur | Formulaire dans le volet (on vise) ; **liste des lieux** avec leur état (verrouillé, laissé ouvert, crocheté, enfoncé) et l'auteur ; Aller, Réparer, Ouvrir, Clés, Journal, Retirer |
| `/serrure poser "<nom>" <section\|faction\|hrp> <simple\|renforcee\|scellee\|hrp> [clés]` | Modérateur | Vise la porte (6 blocs), pose la serrure |
| `/serrure info` · `/serrure liste [faction\|section]` | Helper | Lire |
| `/serrure ouvrir` · `/serrure reparer <id>` | Modérateur | En mode staff, journalisé |
| `/serrure revoquer <id> <n\|tout>` · `/serrure retirer <id>` | Modérateur · Admin | Clés, retrait |
| `/serrure cle <id> [joueur] "<motif>"` | Admin | Clé (temporaire en animation), motif obligatoire |
| `/coffre poser "<nom>" <section> <9\|18\|27\|36> <niveau>` · `/coffre retirer <id>` | Modérateur · Admin | Vise le meuble |
| `/coffre voir <id> "<motif>"` · `/coffre journal <id>` | Admin · Modérateur | Ouvrir (retraits relus et journalisés), lire |
| Réglages `config.yml` › `lieux.*` | Admin | Niveaux, prix, durées, rayons sonores, quota par défaut, plafond de vol, répit, zones HRP |

---

## 10. Journal

Catégorie universelle **`LIEU`**, en ajout seul : `POSE`, `RETRAIT`, `CLE_TAILLEE`, `CLE_REMISE`, `CLE_REVOQUEE`,
`SERRURE_CHANGEE`, `OUVERTURE` (avec une clé : une ligne par joueur, porte et 5 min), `LAISSEE_OUVERTE`, `CROCHETAGE`
(début, faux pas, crochet cassé, réussite, interruption), `FORCAGE` (début, enfoncée), `REPAREE`, `COFFRE_DEPOT`,
`COFFRE_RETRAIT`, `COFFRE_VOL`, `QUOTA_ATTEINT`, `STAFF_OUVERTURE`. Le **staff** voit tout, noms compris. La
**section** voit ses mouvements nommés (registre du chef) et les intrusions **« par un inconnu »**.

---

## 11. Anti-abus et performance

- **Anti-abus** : une personne par serrure ; 10 s après un crochet cassé ; **trois crochets cassés** par la même
  personne sur la même serrure : elle est **grippée** pour elle 10 min ; forcer est bruyant, alerté, et coûte une
  réparation au propriétaire ; coffres : 9 objets par intrusion, 30 min de répit, un membre en ligne, quota de retraits
  contre le pillage interne, objets marqués « volés » ; **HRP** réservé aux lieux hors jeu (`lieux.zones-hrp`) ;
  forcer sans raison RP est une **faute RP** (sanctions existantes) ; mode staff sans mécanique RP.
- **Performance** : registre des serrures en mémoire, **indexé par tronçon puis position** (une recherche au clic,
  rien par tick) ; fissures renvoyées toutes les 15 s seulement aux joueurs à 32 blocs d'une porte abîmée ; mini-jeu :
  le serveur ne garde qu'une graine et une horloge, un paquet par appui ; dernier porteur : événements d'inventaire
  filtrés sur la donnée de la clé ; coffres chargés à l'ouverture, écrits hors du fil principal ; côté mod, la coupe
  de serrure et le coffre sont des **textures** et quelques aplats (`LotGui`, DA §6).
- **Paquet** `reborn:lieu` : entrées du menu R d'une porte ou d'un coffre, session de crochetage, contenu de l'écran du
  coffre, J › Lieux ; **filtré** par le serveur (registre des clés seulement au chef et aux grades « Clés »).

---

## 12. Lots d'implémentation

| Lot | Contenu | Dépend de |
|---|---|---|
| **S1** | Serrures côté serveur : registre, pose et retrait (`/serrure`), blocage des ouvertures, clés (donnée, numéros, validation), clic droit / Maj + clic, refermeture, plaque affichée, journal `LIEU`, persistance `plugins/ShinobiCore/lieux/` ; objets Nexo provisoires | FACTION F1, F2 |
| **S2** | Clés dans le volet J (Lieux : tailler, registre, révoquer, changer la serrure, gravure) ; trousseau ; droits « Clés » et « Alertes des lieux » | S1, FACTION F4, F6 (caisse) |
| **S3** | Menu R de la porte (remplace `lock`) ; crochetage : session serveur, écran compact, bruits, crochet cassé, grippage, traces ; `OCCUPE(crocheter)` | S1, ETATS (`OCCUPE`) ou gabarit `VolService` |
| **S4** | Forcer et réparer : dégâts sur la serrure, fissures, sons, alerte de la section (bande), porte enfoncée, réparation payée | S1, S2 |
| **S5** | Coffres de section (= FACTION **F5**) : meuble, écran, droits par grade, quota, journal ; vol par S3 / S4 (fouille, plafond, répit, membre en ligne) | FACTION F4, S3, S4 |
| **S6** | Poste de garde : Porte… / Coffre…, liste des lieux et actions, alertes `lieu` | S1, S5 |

Chaque lot finit par des captures du client de dev montrées avant toute publication, et le YAML passé au parseur.

---

## 13. Questions (8) — avec recommandations

1. **Clé seule** : aucun grade, pas même le Kage, n'ouvre une porte verrouillée sans sa clé ; le Kage reçoit les clés du
   palais comme des objets ? — *Reco : oui, c'est ta règle « uniquement un verrou à clé » ; les grades ne servent qu'à
   gérer les clés.*
2. **Qui taille les clés** : le chef de section et les grades « Clés de la section », depuis J, au prix du niveau pris
   dans la caisse, remises en main propre ; pas de copie par les joueurs à la beta ? — *Reco : oui ; l'empreinte à la
   cire chez un forgeron après la beta.*
3. **Passe-partout du staff** : aucun objet ; le staff ouvre en mode staff (journalisé), clé temporaire en mode
   animation ? — *Reco : oui ; un passe-partout volé ouvrirait tout le serveur.*
4. **Qui pose** : le staff seul à la beta (le chef de section demande par ticket) ; serrures de maison avec le
   logement, après la beta ? — *Reco : oui ; les portes appartiennent aux bâtiments des builders.*
5. **Crochetage réussi silencieux** : aucune alerte à distance, mais faux pas audibles, rayures 1 h et journal « par un
   inconnu » ? — *Reco : oui ; le crochetage est l'outil de l'infiltration, la casse celui de l'assaut.*
6. **Casse** : 10 / 25 / 50 s à la hache, son à 48 blocs, alerte aux membres en ligne, porte enfoncée jusqu'à
   réparation payée par la caisse (100 / 200 / 300 ryo), fissures effacées après 1 h sans coup ? — *Reco : oui ; on
   ajuste les durées après deux semaines de jeu.*
7. **Vol d'un coffre** : 9 objets par intrusion, 30 min de répit, et **au moins un membre de la section en ligne**
   (pas pour les portes) ? — *Reco : oui ; pas de pillage d'un coffre que personne ne peut défendre.*
8. **Niveau HRP inviolable** : réservé aux lieux hors jeu (spawn, salles staff), jamais sur un lieu RP ? — *Reco : oui ;
   c'est la seule exception à « aucune zone inaccessible ».*
