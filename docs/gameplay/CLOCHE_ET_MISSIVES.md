# Cloche du village, missives et volières

> **Statut : validée et codée le 2026-10-16, publiée sur le dev le 16/10 (hud 0.4.184, manifest 3.1.141, ShinobiCore
> du 16/10, objets Nexo provisoires ; §10) ; Q6 et Q8 tranchées le 16/10, Q7 en partie (§9).**
> Communication RP sans instantané :
> la **cloche** pour annoncer à ceux qui sont là, le **corbeau** pour écrire à ceux qui sont loin. Remplace et précise
> [`../GAMEPLAN.md`](../GAMEPLAN.md) §30 (Volière) ; branché sur la Faction ([`FACTION.md`](./FACTION.md) v3 : droits
> par seuil de grade, sections, sections cachées). Questions Q6 à Q8 au §9 (Q1 à Q5 dans FACTION.md).
>
> **Maquettes** : `Maquettes/gameplan/37_faction/v3c/` (16/10) — **écrans centrés**, pas de volet (« pour la volière
> et pour les annonces, fais comme avant ») ; **zone d'écriture dans le style de l'écran** (nuit, laque, or) :
> `01_cloche_rediger.png` (zone d'annonce, type, sections en liste qui s'adapte, grades, Sonner),
> `02_cloche_chez_un_joueur.png` (la bande nette en haut de l'écran, trois types sur fond clair, sombre et chargé,
> fondu), `03_voliere_ecrire.png` (la lettre à plusieurs destinataires, + Ajouter, volière d'arrivée, sceau, coût
> total, Envoyer par corbeau), `04_voliere_missives.png` (missives présentes, copies d'une missive multiple, prendre /
> lire), `05_missive_lecture.png` (la lettre ouverte, cachet brisé), `06_voliere_alertes.png` (« un corbeau a déposé
> une missive pour toi », alerte du palais). Script `_maquettes_cloche_voliere_v3c.py` (réutilise
> `../v3b/_maquettes_cloche_voliere.py`).
> **Remplacées** : `37_faction/v3b/` (feuille crème qui jurait avec l'écran, 6 sections fixes, un seul destinataire,
> ligne sur voile dégradé « un peu sale ») et les maquettes 06 à 08 de `37_faction/v3/` (formulaires dans le volet ;
> on en garde la cloche provisoire sur la place).
>
> **Références** : Zenkai RP (cloche dorée dans le village ; fenêtre « Annonce village » : 200 caractères, normale /
> importante / danger, tout le village ou sections cochées, grades cochés) — on reprend l'idée, pas leur style web ;
> RedLine *Mailbox & Letter System* (GAMEPLAN §30.2).

---

## 0. En dix lignes

1. Chaque village a **une cloche** : un objet Nexo **posé par le staff** sur la place (modèle provisoire, tu le
   dessines), avec une **zone** (le village).
2. Clic droit ou menu R sur la cloche : **« Sonner la cloche »**, si le grade (Jōnin par défaut) ou une charge le
   permet ; un chef de section peut la sonner **pour sa section seulement**.
3. Un **écran centré** (comme le guichet) avec une **zone d'écriture encastrée** dans le même style : texte de **200
   caractères**, type **normale / importante / danger**, **tout le village** ou **sections cochées** (autant qu'il y en
   a), **grades cochés**.
4. La cloche **sonne pour toute la zone** (un coup, deux coups, le tocsin) ; le texte s'affiche **en haut de l'écran**
   des seuls concernés présents, **signé** du nom du personnage : « ANNONCE DU VILLAGE — … — Signé : Daisuke Senju,
   Hokage ».
5. **Anti-spam** (2 min par cloche, 10 min par personne, 3 « danger » par heure) et **journal** en ajout seul.
6. Une **missive** s'écrit **à une volière** et part **par corbeau** vers **une volière** (petite, grande, tour) —
   jamais directement au destinataire : **délai** selon la distance, **coût** en ryo par destinataire ; **plusieurs
   destinataires** possibles (une copie chacun).
7. Le destinataire **est prévenu** par une ligne fine (« Un corbeau a déposé une missive pour toi à la volière de
   … ») — un clan (Yamanaka ou un clan caché) fait passer l'information par l'esprit — mais il doit **aller à la
   volière** : ni auteur ni contenu à distance, pas de message instantané.
8. Exception : la **volière du palais du Kage** (dès Chūnin) déclenche une **alerte sans contenu** chez les gradés du
   palais : « Une missive importante est arrivée à la volière du palais. »
9. **Tout le monde peut se servir** à une volière (dès Genin) : chercher pour un autre, renseignement, espionnage. La
   missive devient un **objet** (parchemin Nexo) qu'on garde, donne, vole.
10. Missive **scellée** à la cire par défaut ; la lire la **descelle pour toujours** : on voit si elle a été ouverte.
    Anonyme par défaut, **signée** si on veut.

---

## 1. Ce qui existe et qu'on réutilise

| Existant | Usage |
|---|---|
| Ligne d'annonce du staff (`/annonce`, canal `reborn:annonce`, `EffetsStaff#ligneAnnonce` : ligne fine en haut au centre, voile nuit, marque, filet qui se résorbe) | **Rendu de la cloche** : même ligne, plus un titre, une teinte par type et une signature |
| Écrans centrés du kit (guichet de la banque, écran Faction v2, escouade) ; `GardeAnnonces` (formulaire d'annonce staff, aperçu) | Gabarit des écrans de la cloche et de la volière (panneau laqué, motif signature, mots, une plaque pour valider) |
| Faction v3 : seuils de grade du village, charges, sections, sections cachées | Qui sonne, qui écrit au palais, qui prend à une volière, ciblage |
| Contacts (« se présenter ») | Liste des destinataires connus |
| Bourse et banque (ryo) | Coût des missives (puits d'argent) |
| Sacoche, objets, vol sur un corps, tendre (menu R) | La missive est un objet comme un autre |
| Journal universel | Catégories `FACTION` (cloche) et `MISSIVE` |
| Nexo (meubles et objets, `item_model`) | Cloche, perchoir de volière, parchemins |

---

## 2. La cloche du village — maquettes v3c 01, 02

### 2.1 L'objet et sa zone

- Meuble Nexo **`reborn:cloche_village`**, posé par le staff (§5) ; une cloche **par village** à la beta (plusieurs
  plus tard : quartiers, organisations).
- **Zone** : un rayon autour de la cloche (250 blocs par défaut, réglé à la pose), en attendant les zones de faction ;
  la cloche ne parle qu'aux joueurs **présents dans la zone**.

### 2.2 Qui sonne

| Qui | Peut viser |
|---|---|
| Grade du village ≥ seuil « Sonner la cloche » (Jōnin par défaut, FACTION §6.1) et charges qui en ont le droit | Tout le village, ou des sections, avec filtre de grades |
| Grade de section avec « Sonner la cloche pour la section » (chef de section…) | **Sa section seulement** |
| Personne d'autre | Le menu R n'affiche pas « Sonner la cloche » |

### 2.3 L'écran (centré, maquette v3c 01)

Écran centré du kit, signature : **la cloche** (filet d'or). À gauche, la **zone d'écriture, encastrée dans l'écran**
(même langage que le reste : nuit plus profonde que la laque, filet d'or doux au focus, filets d'écriture presque
invisibles) : marque et « ANNONCE DU VILLAGE » teintées selon le type, texte crème en grand (200 caractères, retour à
la ligne, interligne large, compteur discret), mise en forme **gras / italique** seulement (G, I en haut de la zone),
et en pied la **signature imposée**, écrite seule : **nom du personnage + son titre** (charge, grade de section ou
grade). Dessous : **grades** en puces cochables (les 9 grades du village). À droite : **type** (normale / importante /
danger, avec le son de chacun) ; **pour qui** : « Tout le village » (visiteurs présents compris) ou « Des sections ».

**Les sections ne sont pas fixes** (le chef de faction en crée, FACTION.md) : « Des sections » ouvre une **recherche**
et une **liste qui défile** (5 lignes visibles, barre fine), **cochées en tête**, insigne de chaque section, mention
« nouvelle » quelques jours après la création, « 3 cochées sur 10 · Tout · Aucune ». Seulement les sections que le
sonneur voit ; **jamais une section cachée** (« L'ANBU n'est jamais proposée »). La maquette montre 10 sections, dont la
Brigade du port créée par le Hokage. En pied : « 38 personnes dans la zone · 13 concernées », « les autres entendent la
cloche, sans le texte », **Sonner · Entrée**, Annuler ; sous l'écran, le délai de la cloche. Un chef de section n'a que
sa section, déjà cochée.

### 2.4 Ce que les autres reçoivent

| Type | Son (tout le monde dans la zone) | Ligne en haut (seulement les concernés présents) |
|---|---|---|
| **Normale** | un coup de cloche | marque et titre **or**, 10 s ; petite cloche sans onde |
| **Importante** | deux coups | **ambre**, 12 s ; une onde de chaque côté de la cloche |
| **Danger** | le **tocsin** (coups rapides) | **vermillon**, 15 s ; deux ondes |

La ligne (maquette v3c 02) : **une bande fine et nette** en haut au centre, jamais une plaque : fond sombre **uni**
semi-opaque (≈ 84 %), **bords francs**, ombre d'un pixel, aucun dégradé ni bruit. **Une seule couleur**, celle du
type, sur la petite cloche et le titre en petites capitales ; un séparateur fin ; texte crème (le gras de la zone
d'écriture est gardé) ; « — Signé : Nom, charge » en gris ; filet du temps restant **neutre** en bas de la bande.
**Fondu** à l'entrée (0,25 s) et à la sortie (0,4 s). Vérifiée sur fond clair (ciel), sombre (forêt de nuit) et chargé
(façade du palais). 200 caractères tiennent sur une ligne, deux au plus.

- Les non-concernés **entendent la cloche sans lire le texte** : ils savent qu'une annonce a eu lieu.
- « Tout le village » s'affiche chez **tous les présents**, visiteurs compris (une annonce publique s'entend : un espion
  en profite) ; une annonce ciblée ne s'affiche que chez les membres visés (Q8, tranché le 16/10).
- La ligne reste ensuite dans l'onglet **Annonces** du volet J (20 dernières, 7 jours), pour ceux qui l'ont reçue.
- Afficher le nom dans la signature **n'ajoute pas** le sonneur aux contacts : on lit un nom, on ne connaît pas un
  visage.

### 2.5 Anti-spam et journal

- **2 min** entre deux annonces d'une même cloche, **10 min** par personne, **3 « danger » par heure** et par village ;
  le staff peut **faire taire** une cloche ou retirer la ligne en cours (`/cloche taire`).
- **Sonner sans délai** (16/10) : à partir d'un grade, plusieurs annonces à la suite **sans aucun des trois délais**
  (ni par cloche, ni par personne, ni limite « danger »). Seuil du village **« Sonner sans délai »** (FACTION.md §6.1),
  réglé par le Kage dans J › Gestion avec les autres seuils, défaut **Commandant Jōnin** (config staff
  `communication.cloche.sans-delai-des`, appliquée tant que le Kage n'a pas réglé le seuil) ; le **chef de faction (le
  Kage) en bénéficie toujours**. Le staff en **mode animation** aussi (events, `communication.cloche.sans-delai-animation`,
  oui par défaut) ; en mode staff on ne sonne pas du tout (aucune mécanique RP). Une cloche **tue par le staff** reste
  muette pour tout le monde. Leurs annonces comptent quand même (la cloche « vient de sonner » pour les autres, les
  « danger » s'ajoutent à l'heure du village). Sous l'écran : « la cloche peut sonner, sans délai pour ton grade »
  (« sans délai en mode animation »).
- Journal `FACTION` / `CLOCHE`, en ajout seul : auteur (`#N°`), cloche, type, cible (sections, grades), texte, nombre de
  destinataires ; une annonce sans délai le dit (« sans délai (grade Commandant Jōnin) », « (chef de faction) »,
  « (mode animation) »). Le chef de faction le lit ; le staff aussi.
- `/annonce` du staff reste à part (HRP, tout le serveur).

---

## 3. Missives et volières — maquettes v3c 03 à 06

### 3.1 Les volières

| Type | Où | Reçoit de | Garde |
|---|---|---|---|
| **Petite volière** | Quartier, hameau, poste avancé | Toute volière du même pays | 12 missives |
| **Grande volière** | Cœur du village | Toute volière du même pays, et les tours | 40 missives |
| **Tour de volière** | Une par village, la porte vers l'extérieur | **Toutes** les volières, autres villages compris | 80 missives |
| **Volière du palais** | Dans le palais du Kage (derrière une porte de faction) | Toute volière, **si l'expéditeur a le seuil** « Écrire à la volière du palais » (Chūnin) | 40 missives |

Le bâtiment est construit par les builders ; le staff pose seulement le **point d'interaction** (meuble Nexo
`reborn:perchoir_voliere`) et lui donne un nom, un type, un village (§5). Pleine, une volière **brûle la plus ancienne**
missive non prise pour faire de la place (journal).

### 3.2 Écrire et envoyer (à une volière : menu R sur le perchoir, écran centré, mot « Écrire »)

L'écran (maquette v3c 03) : signature **le corbeau perché** ; deux mots en tête, « Écrire » et « Missives ici · N ».
À gauche, **la lettre, encastrée dans l'écran** (même style que la cloche) : « À » en tête, une ligne « N
destinataires · une copie chacun » avec G / I, le corps crème en grand, le compteur discret, la signature et le
**cachet de cire** en pied (seul accent de couleur). À droite, la volière d'arrivée et le sceau. En pied : coût total,
heure d'arrivée, **Envoyer par corbeau · Entrée**. Le brouillon est gardé si on ferme.

- **À** : **une ou plusieurs** personnes, en **puces retirables** (×), et **+ Ajouter** : une recherche parmi ceux
  qu'on connaît (contacts), ou **une adresse écrite à la main** (40 caractères : « Au chef de la Police »). Les
  adresses sont **écrites sur la missive, lisibles de tous**.
- **Volière d'arrivée** : liste des volières que celle-ci peut joindre, avec **délai** et **coût**.
- **Texte** : 600 caractères, retour à la ligne, **gras / italique** seulement. **Rien d'autre** : une missive ne
  transporte ni objet, ni ryo.
- **Signature** : **anonyme** par défaut, ou **signée** (nom du personnage). **Toujours scellée** à la cire.
- **Plusieurs destinataires** = **une copie par destinataire**, toutes vers **la même volière d'arrivée**, un coût
  chacune (GAMEPLAN §30) : l'écran affiche le total (« 80 ryo (2 × 40) »). Chaque copie porte la liste complète des
  destinataires, a **son propre cachet**, et **chaque destinataire est prévenu** à l'arrivée de la sienne.
- **Envoyer par corbeau · Entrée** : le corbeau part (battement d'ailes, `reborn:voliere.envoi`) ; le coût est pris dans
  la **bourse**.

### 3.3 Délais et coûts (réglables par le staff)

| Trajet | Délai | Coût |
|---|---|---|
| Même volière ou même village | 1 min | 10 ryo |
| Autre volière du pays | 1 min + 1 min par 250 blocs, au plus 8 min | 10 ryo |
| Autre village (de tour à tour) | 1 min par 250 blocs, au plus 20 min | 40 ryo |
| Volière du palais | 1 min | 10 ryo |

**Aucun plafond d'envois** (tranché le 16/10) : seul le prix freine. Le corbeau en vol ne s'intercepte pas à la beta
(Q7).

### 3.4 Arrivée et récupération

- La missive **attend à la volière d'arrivée** (7 jours). Le destinataire (s'il est une personne connue ; en ligne,
  sinon à sa connexion) reçoit **une ligne fine** en haut de l'écran : « MISSIVE — Un corbeau a déposé une missive
  pour toi à la tour de volière de Suna. » Ni auteur, ni contenu (Q6, tranché le 16/10 ; RP : un clan, Yamanaka ou un
  clan caché, transmet l'information par l'esprit). Une adresse écrite à la main ne prévient personne. À la volière,
  la liste affiche ses missives en tête, en or, « pour toi ». Une missive multiple y apparaît **en une ligne par
  copie** : « Pour Mei Kazane et 2 autres » (sa copie), « Pour Aya Inuzuka et 2 autres » (celle d'Aya), avec « aussi
  pour Aya et Yui » en sous-ligne (maquette v3c 04).
- **Prendre** (écran « Missives ici », maquette v3c 04 ; « Lire » = l'ouvrir **sur place** : le cachet se brise pour toujours mais la missive **reste à la volière** ; seul « Prendre » la met dans la sacoche — corrigé le 16/10, hud 0.4.186) : **toute
  personne** au seuil « Prendre à une volière » (Genin par défaut), **même si la missive ne lui est pas destinée**.
  Rien ne s'affiche pour les autres joueurs ; c'est écrit au **journal staff**.
- Non prise en 7 jours : le gardien la **brûle** (journal).

### 3.5 La missive, objet

- Parchemin Nexo à deux états : **`reborn:missive_scellee`** et **`reborn:missive_descellee`**. Info-bulle : « Pour :
  <adresse> », l'état, jamais l'auteur s'il est anonyme.
- **Lire** (clic droit) : la lettre s'ouvre **seule au centre**, dans le même écran laqué (maquette v3c 05) : « À »,
  volière et heure d'arrivée, texte, signature ou « sans signature », cachet en pied ; la première lecture **brise le cachet pour
  toujours** (son `reborn:missive.descelle`). Si le cachet était déjà brisé, une ligne sous la lettre le dit : « Cachet
  déjà brisé quand tu l'as prise : quelqu'un l'a lue avant toi. »
  Resceller (faussaire) : après la beta.
- On la **garde** dans la sacoche, la **donne** (tendre, menu R), la **pose**, la **perd** ou se la fait **voler**
  (fouille, corps), comme tout objet. **Transférer** : la renvoyer depuis une volière, avec un nouveau coût, en gardant
  son anonymat d'origine (RedLine). **Brûler** : la détruire (RP).
- Mode staff : aucune de ces actions (décision du 14/10).

### 3.6 La volière du palais

- Envoi réservé au seuil « Écrire à la volière du palais » (Chūnin par défaut).
- À l'arrivée : **une seule ligne** en haut de l'écran, chez les personnes **en ligne** qui ont « Recevoir les alertes
  du palais » (Jōnin par défaut, et les charges) : « VOLIÈRE DU PALAIS — Une missive importante est arrivée à la
  volière du palais. » **Ni auteur, ni contenu, ni destinataire.** Même bande nette que la cloche, marque : la
  missive, titre ambre (maquette v3c 06).
- La missive se récupère **au palais** (porte de faction : seuls ceux qui l'ouvrent y entrent).

### 3.7 Écart avec GAMEPLAN §30 (proposition précédente)

| §30 | Ici |
|---|---|
| Lecture seulement à la volière | **Retrait** seulement à la volière ; **lecture** partout une fois l'objet en main |
| « Sceau de signature » facultatif | **Signature** facultative ; le **cachet** (fermeture) est toujours là : deux choses différentes |
| Coût par destinataire, plusieurs destinataires, anonyme par défaut, transfert | Gardés |
| — | Volières à types, délais par distance, palais, prise par n'importe qui, état scellé / descellé |

---

## 4. Objets à modéliser (par toi, Blockbench / Nexo) — identifiants stables

| Identifiant | Type Nexo | Rôle | Provisoire livré |
|---|---|---|---|
| `reborn:cloche_village` | meuble (hitbox : 2 barrières) | La cloche de la place (potence et cloche de bronze, maquette v3 06) | ✅ potence, poutre et cloche en cubes (`models/item/cloche_village.json`) |
| `reborn:perchoir_voliere` | meuble (hitbox : 2 barrières) | Point d'interaction d'une volière (perchoir, pupitre) | ✅ poteau, perchoir, pupitre et corbeau-bloc en cubes |
| `reborn:missive_scellee` | objet | Parchemin roulé, cachet de cire entier | ✅ sprite 16 × 16 (maquettes v3c 04, 05) |
| `reborn:missive_descellee` | objet | Même parchemin, cachet brisé | ✅ sprite 16 × 16 |
| `reborn:corbeau` | modèle animé (après la beta) | Le corbeau qui part de la volière | — |

Sons : `reborn:cloche.normale`, `reborn:cloche.importante`, `reborn:cloche.danger`, `reborn:voliere.envoi`,
`reborn:missive.descelle`.

Livrés le 16/10 dans `minecraft/server-config/nexo/` : `items/reborn_communication.yml` et les modèles, textures et
définitions d'objets de `pack/assets/reborn/` (générés par `minecraft/mod-hud/tools/gen_communication_textures.py`).
**À modéliser par toi** (en gardant les identifiants) : la cloche, le perchoir, les deux missives, puis le corbeau
(après la beta). Les sons `reborn:*` n'ont pas encore d'asset : le serveur joue des sons vanilla adaptés (cloche
`block.bell.use` 1, 2 ou 7 coups + `block.bell.resonate`, battement d'ailes `entity.parrot.fly` + `entity.bat.takeoff`,
cachet `item.axe.wax_off`, page `item.book.page_turn`, brûler `block.fire.extinguish`).

---

## 5. Staff

| Commande / outil | Grade | Effet |
|---|---|---|
| `/cloche poser <village> [rayon] ["lieu"]` (aide-mémoire : F7 › Outils › Commandes) | Modérateur | Vise le meuble, l'attache au village, règle la zone |
| `/cloche liste` · `/cloche retirer <id>` · `/cloche taire <village> [min]` | Helper · Modérateur | Lister, retirer, faire taire, retirer la ligne en cours |
| `/voliere poser "<nom>" <petite\|grande\|tour\|palais> <village>` (idem) | Modérateur | Vise le perchoir, nomme la volière |
| `/voliere liste` · `/voliere vider <id>` · `/voliere retirer <id>` | Helper · Admin | Lister, vider (journal), retirer |
| `/missive lire <id> "<motif>"` | Admin | Lire une missive (harcèlement, enquête) — motif obligatoire, journalisé |
| Réglages `config.yml` | Admin | Délais, coûts, quotas, durées de garde, seuils de la cloche |

Tout est journalisé (catégorie `STAFF`), comme les autres outils.

---

## 6. Anti-abus

- Cloche : délais (§2.5), signature imposée, droits par seuil, journal ; une annonce abusive est une **faute RP**
  (sanctions existantes).
- Missives : coût (aucun plafond d'envois), 600 caractères, texte seul (aucun transfert d'objet ou de ryo entre ses propres
  personnages), capacité des volières, journal complet ; **signaler** une missive passe par `/signaler` avec son
  identifiant.

---

## 7. Après la beta

Intercepter un corbeau (techniques, faucons), resceller une missive (faussaire), corbeau animé, cloche d'une
organisation ou d'un quartier, panneau d'affichage écrit, avis de recherche en parchemin (Bingo Book), appel de service
par oiseau messager (GAMEPLAN §18.3), météo qui retarde les corbeaux.

---

## 8. Lots d'implémentation

| Lot | Contenu | Dépend de |
|---|---|---|
| **C1** | Cloche côté serveur : meuble posé, zone, droits (seuils FACTION), diffusion (son à la zone, ligne aux concernés), anti-spam, journal, `/cloche` | FACTION F1 |
| **C2** | Mod : bande d'annonce nette, signée, une couleur par type, fondu (réutilise la place d'`EffetsStaff`), écran centré de la cloche (zone d'écriture encastrée, G / I, liste de sections avec recherche et défilement), onglet Annonces du volet J | C1, FACTION F3 |
| **V1** | Volières : pose staff, types, capacités, `/voliere` | FACTION F1 |
| **V2** | Écrire et envoyer (écran centré, lettre encastrée, plusieurs destinataires : une copie et un coût chacun), délais, file d'arrivée, bande « un corbeau a déposé une missive pour toi » pour chaque destinataire | V1, banque |
| **V3** | Objet missive (Nexo, deux états), prendre, lire (descelle), tendre, vol, transfert, brûler, journal `MISSIVE` | V2 |
| **V4** | Volière du palais et son alerte | V3, FACTION F5 (porte du palais) |

Chaque lot finit par des captures du client de dev montrées avant toute publication, et le YAML passé au parseur.

---

## 9. Questions (Q6 à Q8)

6. **Arrivée d'une missive** — *tranché le 16/10* : le destinataire **est prévenu** par une ligne fine en haut de
   l'écran (« Un corbeau a déposé une missive pour toi à la volière de … »), sans auteur ni contenu ; il doit aller la
   chercher. RP : un clan (Yamanaka ou un clan caché) transmet l'information par l'esprit.
7. **Délais et coûts** — *tranché en partie le 16/10* : **aucune limite d'envois**, seulement le coût en ryo ; les
   délais d'acheminement du §3.3 sont gardés. Reste ouvert : les montants (10 / 40 ryo) et l'interception du corbeau
   (reco : pas à la beta, on ajuste après deux semaines de jeu).
8. **Cloche « tout le village »** — *tranché le 16/10* : le texte s'affiche aussi chez les **visiteurs** présents dans
   la zone ; seuil pour sonner validé : **Jōnin**, plus les charges, et les chefs de section pour leur section.

---

## 10. Ce qui est codé (16/10, publié sur le dev : hud 0.4.184, manifest 3.1.141)

**Serveur — ShinobiCore, package `communication/`** (référence :
[`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §4 octies, canal `reborn:communication`) :

- **Points posés par le staff** sur le meuble ou le bloc visé (6 blocs) : `/cloche poser <village> [rayon] ["lieu"]`,
  `/voliere poser "<nom>" <petite|grande|tour|palais> <village>` ; `liste`, `retirer`, `taire`, `vider`, `/missive lire`
  (§5). Reconnus au clic droit sur le bloc (barrière du meuble, ±1 bloc, 2 de haut) ou sur l'entité du meuble.
- **Cloche** : droits pris dans la Faction (seuil `cloche_village` Jōnin ou charge « annonces » → tout le village ou
  des sections ; droit de grade « annoncer à la section » → sa section seulement ; jamais une section cachée) ; zone =
  rayon ; « tout le village » = tous les présents, visiteurs compris ; filtre par grades ; compte « zone · concernés »
  à la volée ; anti-spam (2 min par cloche, 10 min par personne, 3 « danger » par heure et par village) ; son entendu
  de toute la zone depuis la direction de la cloche ; bande chez les concernés ; ligne gardée dans l'onglet Annonces du
  volet J (une par section visée) ; journal `FACTION` / `CLOCHE`.
- **Volières** : trajets (pays : toute volière ; hors pays : de tour à tour ; palais : seuil `voliere_palais`), délais
  et coûts du §3.3 (réglables), paiement dans la bourse, **une copie par destinataire** (contacts sous le nom appris, ou
  adresse écrite à la main), signée ou anonyme, toujours scellée ; arrivée au tick (pleine : la plus ancienne brûle),
  **le destinataire est prévenu** (ou à sa connexion), alerte sans contenu du palais ; 7 jours de garde ; prendre (seuil
  `prendre_voliere`, même si elle n'est pas pour soi), lire (clic droit sur l'objet, la première lecture brise le
  cachet pour toujours ; « cachet déjà brisé » si quelqu'un l'a lue avant), brûler, transférer (la missive en main, un
  destinataire, anonymat et état du cachet gardés). Journal `MISSIVE` (nouvelle catégorie) et `STAFF`.
- Persistance YAML : `plugins/ShinobiCore/communication/points.yml`, `missives.yml` ; réglages `communication.*`.

**Client — mod-hud, package `communication/`** : écrans centrés `ClocheScreen` (01), `VoliereScreen` (03, 04, « +
Ajouter »), `LettreScreen` (05) ; zone d'écriture `Editeur` (gras / italique : G, I, Ctrl+G ou Ctrl+B, Ctrl+I ;
**Maj + Entrée** = retour à la ligne, Entrée = Sonner / Envoyer ; brouillons gardés) ; bande `BandeHud` (02, 06 :
fondu, empilée, trois au plus) ; entrées du menu R sur le meuble (« Sonner la cloche », « Écrire une missive »,
« Missives ici », titre du menu = le lieu). Textures `textures/gui/communication/` (`tools/gen_communication_textures.py`).
Banc `REBORN_CLOCHE_DEBUG` / `REBORN_VOLIERE_DEBUG` ([`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5),
captures `Maquettes/gameplan/37_faction/code_cloche_voliere/`.

**Limites connues / pas dans ce chantier** : l'objet missive se lit au clic droit **en main** (pas depuis la case de
la sacoche) ; le transfert prend la missive tenue en main ; la volière du palais n'a pas de porte (lot F5 des lieux de
faction) ; sons `reborn:*` et corbeau animé à venir ; pas d'interception (Q7) ; signaler une missive passe par son
identifiant (`/missive lire` côté Admin).
