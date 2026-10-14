# Monnaie et banque — la bourse, le compte, le guichet

> **Statut : décidé le 15/10 (réponses aux 12 questions, §14), codé le 15/10, non publié.** Game Plan n° 19 (Monnaie)
> et n° 20 (Banque). Ce qui est codé, le protocole, les réglages, les objets à modéliser et ce qui reste : §15 à §20.
> Proposition d'origine (14/10) gardée ci-dessous comme spécification, corrigée là où les décisions l'ont changée.
>
> **Maquettes** (hors dépôt, 1920 × 1080, sur de vraies captures du client de dev dans le monde Konoha, kit de style
> `minecraft/mod-hud/tools/style/`) : `Maquettes/gameplan/19_monnaie/` à côté de `RBLAUNCHER/` —
> `01_bourse_sacoche.png`, `02_poser_et_tendre.png`, `03_guichet.png`, `04_coffre_et_paliers.png`,
> `05_vol_sur_corps.png`, `06_staff.png` (`_maquettes_monnaie.py`, captures dans `captures/`).
>
> **Sources** : [`../GAMEPLAN.md`](../GAMEPLAN.md) §16 (décision du 14/10 : pas de fenêtre d'échange, « un échange
> garanti n'existera, à la limite, qu'à la banque »), §19, §20 ; la référence RedLine (*Berry Currency System*, *Bank
> System*, *Storage*, *Trash Collector*, *Robbery*) ; [ADR 0006](../adr/0006-monnaies-ryo-et-rbcoins.md) ;
> [`FACTION_PROPOSITION.md`](./FACTION_PROPOSITION.md) §6 ; [`INVENTAIRE_ET_COSMETIQUES.md`](./INVENTAIRE_ET_COSMETIQUES.md)
> §2, §5 bis ; [`NOURRITURE.md`](./NOURRITURE.md) ; [`ENTREPRISES.md`](./ENTREPRISES.md) ;
> [`KO_ET_MORT_RP.md`](./KO_ET_MORT_RP.md) §6, §9 ; [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md)
> §3, §6 (règle 9).

---

## 0. En dix lignes

1. Le ryo **porté** devient un **objet : la bourse**. Une case de la sacoche, **sans poids**, le montant affiché
   dessus. On la pose au sol (« Combien veux-tu poser ? »), on la range dans un coffre, on la tend, on la vole.
2. Le ryo **à l'abri** est sur un **compte**, un par personnage, au **guichet de la banque** de son village. Même
   compte à tous les guichets du village ; guichet d'un village allié avec 5 % de frais ; ennemi ou déserteur : refusé.
3. Au guichet : **déposer, retirer, virer** à un autre compte, **échange garanti** (dépôt fiduciaire : c'est
   « l'échange garanti à la banque » de la décision du 14/10) et **coffre du compte**.
4. Le coffre du compte a des **paliers payants** (puits d'argent) et une **liste d'objets refusés** (ce qui doit rester
   volable : parchemins de technique, objets uniques, objets volés, explosifs).
5. **Aucun revenu passif** (pas les 500 / heure de RedLine). L'argent naît à **trois endroits seulement** : missions,
   solde hebdomadaire (payée par le trésor du village, lui-même alimenté par les missions et le staff), staff.
6. La **solde** tombe sur le **compte**, chaque dimanche, selon le grade et la fonction : il faut passer au guichet.
7. Le **guichetier** peut être un **joueur** (entreprise BANQUE créée par le staff) ou, en attendant, un **PNJ / bloc**
   en libre-service. Le guichetier joueur **scelle** les échanges garantis.
8. **Tout mouvement est journalisé** (catégorie Économie du journal universel), avec alertes au Poste de garde.
9. Staff : **une ligne « Économie »** dans la fiche joueur du Poste de garde et quelques commandes `/ryo`, `/banque`.
10. Les **RBCoins** restent à part (ADR 0006) : jamais dans la sacoche ni à la banque ; l'Échap est corrigé (§12).

---

## 1. État des lieux (audit du code, 14/10)

### 1.1 Où vit le ryo aujourd'hui

| Élément | Code | Ce qu'il fait |
|---|---|---|
| **Solde** | `ShinobiCore/…/character/ShinobiCharacter.java` : champ `long ryo` (par **personnage**), `setRyo`, `addRyo`, `trySpendRyo`, `restoreRyo` | Un nombre sur le personnage, persisté dans le YAML des personnages (`YamlCharacterRepository`, clé `ryo`). **Aucun objet physique.** |
| **Crochet de journal** | `ShinobiCharacter.ryoHook` → `journal/RyoJournal` (installé par `JournalDemarrage`) | **Chaque** variation du solde écrit une ligne `ECONOMIE` `GAIN` / `DEPENSE` (« +120 ryo · solde 300 → 420 · motif »), motif et acteur posés par `RyoJournal.contexte(...)` |
| `/ryo` | `shop/ShopManager.onCommand` | Joueur : affiche son solde. Staff (`shinobicore.ryo.admin`) : `/ryo give\|take\|set <joueur> <montant>`, journalisé « staff /ryo … » |
| **Échoppe** | `shop/ShopManager` (canal `reborn:shop`), écran `menu/widget/ShopScreen` | Tenues (prix plat `shop.tenue-price`, **500**), cartes (`places.yml`, Konoha **150**), onglet Vivres. S'ouvre **depuis l'Échap, n'importe où** |
| **Vivres** | `nourriture/NourritureModule.acheterVivre`, `config.yml` `nourriture.vivres` | Ration sèche ×2 **30** (tous) ; paniers **10 à 40** (cuisiniers seulement) |
| **Tendre** | `nourriture/TendreService` (`/tendre <joueur> ryo <n>`), menu R `InteractionMenus.tendre` « Donner des ryo ▸ » (10, 50, 100, 120, 150, 200, 500, « Autre montant… ») | Offre de 10 s, F prendre / Retour refuser ; revérifie distance, ligne de vue, solde ; journal `ECONOMIE` `DON` + les deux lignes `GAIN` / `DEPENSE` |
| **Remise à zéro** | `staff/outils/GestionPerso` (`/personnage reset`) | Solde → 0 (journalisé « remise à zéro du personnage ») |
| **Affichages** | `ShopScreen` (« Bourse »), Échap `EscTokonoma` (carte ÉCHOPPE : « — ryo » tant que l'échoppe n'a pas été ouverte, car lu dans `ShopData`), Poste de garde `GardeFiche` (« 12 450 ryo » dans la ligne ping / temps de jeu, via `StaffPanel` `ryo`), `VoirSac` (« Ryo : N ») | Pas de ryo sur le HUD (GAMEPLAN §4) |
| **Fouille** | `ko/action/CorpsService` « fouiller » → `ko/gui/FouillerScreen` | Coffre vanilla : **barre + inventaire vanilla + armure** de la cible ; **ni le sac** (RpBag), **ni le ryo** ; impossible sur un corps dont le joueur est parti (« La cible n'est plus connectée ») |
| **Mort RP** | `character` : `setDead(true)` | Le solde reste sur le personnage mort : **gelé de fait**, ni hérité ni détruit |

### 1.2 Sources et puits existants

| | Mouvement | Montant | Remarque |
|---|---|---|---|
| **Source (création)** | `/ryo give\|set` (staff) | libre | **La seule source** aujourd'hui (aucune mission, aucune solde) |
| Circulation | Tendre « Donner des ryo » | libre | joueur → joueur, de la main à la main |
| **Puits (destruction)** | Tenue | 500 | prix plat |
| | Carte de région | 150 (Konoha) | `places.yml` |
| | Ration sèche ×2 | 30 | tous |
| | Panier d'ingrédients | 10 à 40 | cuisiniers (2 plats par panier) |
| | `/ryo take\|set` | libre | staff |
| | Remise à zéro du personnage | tout | staff |
| (gel) | Mort RP | tout le solde | reste sur le personnage mort |

### 1.3 RBCoins

- **Base** : `apps/api/prisma/schema.prisma` `User.rbCoinBalance` (entier, par **compte**, défaut 0). Lu par
  `apps/api/src/me/me.service.ts` (`coins`), affiché dans l'en-tête du launcher (`HomeHeader.tsx`, icône Coins).
- **Aucune écriture** : rien ne crédite ni ne débite (ni Stripe, ni Bug Bounty, ni activations) ; aucun endpoint.
- **En jeu** : l'Échap montre le bloc RÉCOMPENSES (« RBCoins par heure de jeu », cinq omamori d'activations,
  **statiques**, « état réel à brancher sur l'API ») ; **aucun solde de RBCoins** n'y est affiché. La carte ÉCHOPPE du
  même écran affiche le **ryo** : c'est correct (l'échoppe vend en ryo) mais le libellé peut laisser croire que la
  boutique est celle des RBCoins.
- **Aucune conversion** dans le code, conforme à l'ADR 0006.

### 1.4 Ce qui manque pour la cible

Bourse-objet ; compte, guichets, coffre de compte, paliers ; virement ; échange garanti ; solde ; sources contrôlées
(missions) ; fouille de la bourse et du sac ; corps fouillable après le départ du joueur ; journal des nouveaux
mouvements ; ligne staff ; solde de RBCoins en jeu.

---

## 2. Ce que RedLine fait, et ce qu'on garde

| RedLine | Reborn |
|---|---|
| Berrys = objet « Berry Bag », empilable sans limite, **sans poids**, disparaît à 0 | **Gardé** : la bourse, sans poids, une case |
| Poser au sol : « Combien souhaitez-vous jeter au sol ? » → une bourse au sol | **Gardé** : « Combien veux-tu poser ? » |
| Coffre : « Combien souhaitez-vous transférer ? », double-clic pour tout prendre | **Gardé** |
| Échange par la **fenêtre d'échange** | **Écarté** (décision du 14/10) : de la main à la main ; l'échange **garanti** n'existe qu'au guichet |
| Montant **toujours affiché** en bas à droite | **Écarté** (GAMEPLAN §4, DA règle 9) : visible dans la sacoche, au survol, et dans les notifications |
| Banquier PNJ ou coffre-fort, **même compte dans toutes les banques** | **Adapté** : même compte à tous les guichets **du village** ; allié avec frais ; ennemi refusé |
| Liste blanche / noire d'objets, **paliers payants** (2 → 24 cases) | **Gardé**, chiffres à l'échelle de Reborn (§6) |
| **Revenu horaire** (500 / h, plus pour les VIP) à retirer à la banque | **Écarté** : inflation et avantage payant. Remplacé par la **solde** hebdomadaire selon le grade, versée sur le compte |
| Petits boulots (Trash Collector) payés en Berrys | Missions de rang D automatiques (GAMEPLAN §22), plafonnées |
| Braquages (Robbery) comme argent illégal | Plus tard (GAMEPLAN §48) ; le vol d'une bourse sur un corps existe dès la V1 (§8) |

---

## 3. La bourse (le ryo porté) — maquettes 01 et 02

| Règle | Proposition |
|---|---|
| **Objet** | `bourse` (objet Nexo, petit kinchaku de tissu à cordon d'or, une icône 16 px) ; le **montant est dans l'objet** (donnée persistante `reborn:ryo`) avec un identifiant unique (`reborn:bourse_id`) contre la duplication |
| **Une seule bourse** sur soi | Ramasser, recevoir ou retirer des ryo **fusionne** dans sa bourse ; si l'on n'en a pas, une bourse apparaît (barre, puis sac) ; à 0 elle disparaît |
| **Poids** | **0** (RedLine). Une case quand même : sacoche pleine = pas de bourse = on ne peut rien recevoir (« Plus de place pour ta bourse ») |
| **Affichage** | Sur la case : montant **abrégé en or** (`750`, `1,2k`, `12k`, `1,2M`) ; au survol : « Bourse · 1 240 ryo » ; sous la barre de la sacoche, une ligne discrète « Sur toi : 1 240 ryo ». **Rien sur le HUD** |
| **Icône selon le montant** (polish) | trois états de la même bourse : plate (< 100), garnie (< 5 000), lourde (≥ 5 000) |
| **Poser au sol** | Glisser hors de la sacoche (ou clic droit « Poser… ») → « **Combien veux-tu poser ?** » : champ, raccourcis 10 · 100 · moitié · tout, « Poser · Entrée ». Au sol : une bourse avec son montant, invite « **[F] Ramasser** Bourse · 250 ryo » (rareté Commun) |
| **Au sol, durée** | 30 min (au lieu de 10 pour un objet), puis perdue (journalisé `PERTE`) |
| **Ranger dans un coffre** (coffre posé, coffre de section) | Glisser la bourse sur le coffre → « Combien veux-tu ranger ? » ; dans le coffre, une bourse avec son montant ; **double-clic = tout prendre** |
| **Tendre** | Le menu R garde « **Donner des ryo ▸** » (mêmes montants) ; c'est désormais **la bourse** qui paie. La bourse **en main** se tend entière (« Tendre Bourse · 1 240 ryo ») |
| **Payer l'échoppe** | depuis la bourse (« Pas assez dans ta bourse — passe au guichet ») |
| **Interdits** | KO, porté, étourdi, scène de mort, mode staff, sacoche en lecture seule : comme les autres objets |

**Pourquoi un objet** : on le voit, on le perd, on le cache, on le vole ; la richesse portée devient un risque RP
(on ne se promène pas avec 20 000 ryo), ce qui donne sa raison d'être au compte.

---

## 4. Le compte et le guichet — maquette 03

### 4.1 Le guichet

| | Proposition |
|---|---|
| **Où** | Un ou deux guichets par village (la banque de Konoha près de la tour) : un **bloc ou meuble Nexo** marqué par le staff (`/banque guichet ajouter konoha`), ou un **PNJ** |
| **Qui** | Les personnages **du village** : tout ; d'un village **allié** : dépôt, retrait et virement avec **5 % de frais** ; **ennemi, déserteur, exilé** : refusé (« Le guichetier ne te connaît pas ») |
| **Ouvrir** | Clic droit sur le guichet (libre-service) ; avec un **guichetier joueur** en service à côté, c'est lui qui t'ouvre ton compte (menu R sur toi : « Ouvrir son compte ») — tu valides toi-même, il ne voit **jamais ton solde**, seulement l'opération |
| **Écran** | Centré, épuré, **aucun kanji** ; signature propre : la **balance du changeur** (tenbin, deux plateaux en filet d'or) au-dessus du titre ; le jeu reste visible derrière un voile |

### 4.2 Le compte

| | Proposition |
|---|---|
| **Un compte par personnage** (N°) | `compte` (ryo), `palier`, `coffre` (objets) ; le même à tous les guichets du village |
| **Affichage** | « Compte 8 600 ryo » (or) et « Bourse 1 240 ryo » côte à côte ; plafond du palier en petit |
| **Déposer** | De la bourse vers le compte : montant + raccourcis (100 · 500 · 1 000 · tout) ; refusé au-delà du **plafond** du palier |
| **Retirer** | Du compte vers la bourse (crée la bourse si besoin) |
| **Virement** (au guichet seulement) | Vers un personnage **que l'on connaît** (système « rencontrer ») ou par son N° ; **motif obligatoire** ; reçu tout de suite sur son compte (+ notification s'il est connecté : « Virement reçu : 500 ryo — de Ren Hyūga ») ; **frais 1 %** (min. 5) dans le village, 5 % vers un autre village ; **20 000 par jour** au plus |
| **Solde** | Versée le **dimanche** sur le compte (§7) ; une ligne « Solde de la semaine : +500 » à la prochaine visite |
| **Historique** | Les 10 derniers mouvements du compte, en mots (« −500 virement à Ren H. · hier ») |

### 4.3 L'échange garanti (dépôt fiduciaire) — « l'échange garanti à la banque »

C'est la seule forme d'échange sécurisé de Reborn (décision du 14/10), et elle se joue **au guichet**, devant
témoin :

1. Les deux personnages sont **au même guichet** (4 blocs). L'un ouvre « Échange garanti » et choisit l'autre.
2. Chacun **dépose** dans sa colonne : des ryo (bourse ou compte) et / ou des objets (sacoche). On voit **seulement**
   ce que l'autre dépose, jamais son inventaire.
3. Chacun **confirme**. Tout changement d'un côté **annule les deux confirmations**.
4. **Avec un guichetier joueur** en service : il **scelle** (« Sceller l'échange »), il peut refuser (RP). **Sans
   guichetier** : le guichet scelle seul quand les deux ont confirmé.
5. La banque exécute d'un bloc : objets dans la sacoche (sinon dans le coffre du compte, sinon l'échange est refusé
   avant d'être scellé) ; ryo dans la bourse ou sur le compte (au choix de celui qui reçoit).
6. Annulation à tout moment avant le sceau : tout revient à sa place. **Frais : 2 %** des ryo échangés (min. 10).
7. Anti-duplication (reprise du §16.3 abandonné) : objets **retirés de la sacoche** dès le dépôt (pas copiés),
   déconnexion ou éloignement = annulation et restitution, un seul échange par personnage, journal à chaque étape.

**Variante asynchrone** (après la beta, à trancher) : la **lettre de change** — A dépose X au guichet pour B, à
retirer par B à n'importe quel guichet contre Y dans les 48 h, sinon remboursé. Utile pour les accords entre villages.

---

## 5. Le guichetier : un joueur, ou un PNJ en attendant

| | **PNJ / bloc** (V1) | **Guichetier joueur** (entreprise BANQUE) |
|---|---|---|
| Mise en place | `/banque guichet ajouter <village>` sur le bloc ou le PNJ visé | Le staff crée l'entreprise : `/entreprise creer banque "Banque de Konoha" <responsable>` ; ses **postes** sont les guichets (type `BANQUE` à ajouter à [`ENTREPRISES.md`](./ENTREPRISES.md)) |
| Déposer, retirer, virer, coffre | libre-service | idem ; le guichetier peut aussi **ouvrir le compte d'un client** |
| Échange garanti | le guichet scelle seul | **le guichetier scelle** (et peut refuser) |
| Ce qu'il voit | — | l'opération en cours (montant, objets déposés), **jamais le solde ni le coffre** du client |
| Ce qu'il gagne | — | une **solde de fonction** payée par le trésor (pas de commission prélevée : les frais restent un puits) ; les pourboires de la main à la main |
| Journal | — | chaque sceau porte son N° |

Les deux coexistent : le PNJ assure le service quand personne n'est au guichet, le guichetier joueur apporte la
scène (accueil, file d'attente, témoin des échanges).

---

## 6. Le coffre du compte et les paliers — maquette 04

### 6.1 Paliers (puits d'argent)

Payés **une fois**, depuis le compte. Le plafond ne limite que les **dépôts** (la solde et les virements reçus passent
au-dessus). Valeurs indicatives, réglables (`banque.paliers`).

| Palier | Coût | Cases du coffre | Plafond du compte | Pour qui (repère) |
|---|---|---|---|---|
| 1 · **Livret** | gratuit | 2 | 5 000 | tout le monde au départ |
| 2 · **Compte** | 2 000 | 6 | 20 000 | Chūnin en 3-4 semaines |
| 3 · **Compte scellé** | 8 000 | 12 | 60 000 | Jōnin, marchands |
| 4 · **Chambre forte** | 25 000 | 18 | sans plafond | grandes fortunes, chefs de clan |

Chaque coût vaut **40 % du plafond précédent** (même rapport que RedLine : 30 000 pour un T1 de 50 000).

### 6.2 Objets refusés au coffre (`banque.refuses`, liste du staff)

| Refusé | Pourquoi |
|---|---|
| **Parchemins de technique** | Décision du 08/10 : le parchemin est un vrai objet, **volable** ; la banque le mettrait à l'abri |
| **Objets uniques / de quête** (`reborn:unique`, `reborn:quete`) | Ils doivent circuler dans la scène |
| **Objets volés** (marqués « volé à … ») | Pas de blanchiment |
| **Explosifs** (parchemins explosifs, fumigènes) | Sécurité RP |
| Armes | **Acceptées** (décision 8 du 15/10), **sauf les armes RP légendaires / mythiques** (gunbai, épées des Sept Épéistes de la Brume…) : liste `banque.refuses.armes-ids`, clé `reborn:legendaire`, ou arme de rareté Légendaire |
| La bourse | Pas un objet du coffre : la glisser sur le coffre = **déposer** sur le compte |

Refus : la case clignote, notification en bas à droite « **Ce parchemin ne va pas au coffre** — il reste volable »,
petit son d'erreur (RedLine).

---

## 7. D'où vient l'argent, où il part — tableau chiffré

L'argent ne se **crée** qu'à trois endroits : **missions**, **trésor du village** (qui paie la solde, alimenté par les
missions et le staff), **staff**. Tout le reste **circule** ou **disparaît**. Chiffres indicatifs pour **Konoha à la
beta, 40 personnages actifs** ; tout est réglable.

### 7.1 Sources (création)

| Source | Règle | Par semaine (Konoha) |
|---|---|---|
| **Missions de rang D automatiques** (GAMEPLAN §22) | 50 ryo au joueur, **3 par jour** au plus | ≈ 10 000 (40 × 5 missions) |
| **Missions C / B** (bureau, récompense validée) | 300 à 1 500 ; 70 % à l'équipe, **30 % au trésor** (Faction §6.3) | ≈ 8 000 (dont 2 400 au trésor) |
| **Subvention du staff au trésor** | de quoi payer la solde tant que les missions ne suffisent pas, décroissante | ≈ 10 000 au départ |
| **Staff, événements** | `/ryo donner` (motif obligatoire) | ≈ 2 000 |
| **Kit de départ** (GAMEPLAN §51) | une bourse de **100 ryo** à la création | selon les arrivées |
| **Total créé** | | **≈ 30 000** |

### 7.2 Circulation (ni créé ni détruit)

| Mouvement | Exemple |
|---|---|
| **Solde hebdomadaire** (trésor → comptes) | Genin 200 · Genin confirmé 300 · Chūnin 500 · Konin 700 · Tokubetsu Jōnin 900 · Jōnin 1 200 · Commandant 1 600 · Kage 2 000 ; fonction +300 / +500 ; **3 h de présence RP** dans la semaine ; au **prorata** si le trésor est à sec (Faction §6.3) — ≈ 23 000 / semaine pour 40 actifs |
| Repas payé au cuisinier | 8 repas / semaine / joueur |
| Tendre, virement, échange garanti | entre joueurs |
| Amendes, dons, tributs | vers le trésor |

### 7.3 Puits (destruction)

| Puits | Montant | Par semaine (Konoha) |
|---|---|---|
| Paniers et rations (échoppe) | 10 à 40 | ≈ 6 000 |
| Tenues, cartes (échoppe) | 500 / 150 | ≈ 5 700 |
| **Paliers du coffre** | 2 000 / 8 000 / 25 000 | ≈ 8 000 (fort au lancement, puis baisse) |
| Frais de virement (1 %), d'échange garanti (2 %), de guichet allié (5 %) | | ≈ 1 000 |
| Volière (GAMEPLAN §30), à l'échelle de Reborn | 20 par destinataire | ≈ 2 000 |
| Bourses perdues (au sol 30 min, corps non fouillés) | | ≈ 500 |
| **Total détruit** | | **≈ 23 000** |

**Solde net ≈ +7 000 / semaine** sur une masse d'environ 150 000 : **+5 %**, la cible. Si la masse monte plus vite
(`/banque masse`), le staff baisse la subvention du trésor ou ajoute un puits (réparation à la forge, soins payants) ;
**jamais de rachat de RBCoins**.

### 7.4 Une semaine type

| Personnage | Entrées | Sorties | Reste |
|---|---|---|---|
| **Genin** actif | solde 200 + 12 missions D (600) = **800** | 8 repas à 60 (480) + rations | ≈ 250 → palier 2 en ~8 semaines |
| **Chūnin** actif | solde 500 + 6 missions D (300) + 1 mission C (300) = **1 100** | 8 repas (480 à 960) | 150 à 600 |
| **Cuisinier** (Chūnin) | solde 500 + ventes (20 plats × 60 = 1 200) | paniers (10 × 30 = 300) | ≈ 1 400 |

⚠️ **Le prix conseillé du ramen (100 à 150, [`NOURRITURE.md`](./NOURRITURE.md) §4) mange toute la solde d'un Genin** :
proposition, conseiller **40 à 80** (le panier coûte 15 par plat). Question 9.

---

## 8. Le vol : la bourse d'un corps — maquette 05

| | Proposition |
|---|---|
| **Fouiller** | Sur un corps à terre, inconscient ou mort (menu R, inchangé) : **3 s immobile** (un filet discret, interrompu si l'on bouge ou si l'on est frappé), la victime voit « On te fouille » |
| **Ce qu'on voit** | La **sacoche du corps** (même écran que la touche E, en mode fouille) : la barre, **le sac** (aujourd'hui invisible à la fouille) et **la bourse** |
| **Prendre des ryo** | Clic sur la bourse → « Prendre combien ? » ; **plafond : 30 % de la bourse** (décision 3 du 15/10, remplace « 5 000 par scène ») — 30 % de ce qu'elle contenait à la première fouille de la scène, tous voleurs confondus |
| **Mort RP** | Sans plafond (réglage `monnaie.vol.sans-plafond-mort-rp`, **à confirmer**, §19) : la bourse reste sur le corps 15 min ; le corps reste **fouillable même si le joueur est parti** (le mannequin de la scène de mort) ; ce qui n'est pas pris est **perdu** |
| **Alerte** | vol ≥ 1 000 ryo → alerte `vol` au Poste de garde ; journal `ECONOMIE` `VOL` (voleur, victime, montant, lieu) |
| **Ce qu'on ne vole jamais** | le **compte** et le **coffre** (la banque est à l'abri) |

---

## 9. Mort RP, désertion, remise à zéro

| Cas | Bourse | Compte et coffre |
|---|---|---|
| **Mort RP** | sur le corps, fouillable 15 min, puis perdue | **gelés 7 jours** (le staff peut annuler la mort), puis **versés au trésor du village** (objets : coffre des saisies) — **sauf testament validé** : versés aux **héritiers** (décision 4 du 15/10, §15.6) |
| **Désertion / exil** | gardée | **gelé de fait** (le guichet le refuse) ; saisie au trésor si le Kage le décide, validée par le staff (Faction Q13) |
| **Remise à zéro** (staff) | vidée | vidés (sauvegarde avant, comme aujourd'hui) |
| **Changement de personnage** | chaque personnage a sa bourse, son compte | aucun transfert entre personnages d'un même joueur, sauf par le staff |

---

## 10. Journal (catégorie Économie du journal universel)

**Aucun mouvement silencieux** (décision du 14/10). Chaque ligne : date, acteur (N° + grade), cible, montant, bourse
et compte après, lieu, identifiant d'opération.

| Événement | Quand |
|---|---|
| `GAIN`, `DEPENSE` | (existant) toute variation, avec motif |
| `DON` | (existant) tendre des ryo ; `DON_BOURSE` : la bourse entière |
| `POSE_SOL`, `RAMASSE`, `PERTE` | bourse posée, ramassée (par qui), disparue |
| `RANGE`, `PRIS` | bourse dans / hors d'un coffre posé (avec le coffre) |
| `DEPOT`, `RETRAIT`, `VIREMENT`, `FRAIS` | au guichet (avec le guichet et le guichetier éventuel) |
| `ECHANGE_OUVERT`, `ECHANGE_DEPOT`, `ECHANGE_SCELLE`, `ECHANGE_ANNULE` | échange garanti, étape par étape, objets compris |
| `COFFRE_DEPOT`, `COFFRE_RETRAIT`, `COFFRE_REFUS`, `PALIER` | coffre du compte |
| `SOLDE` | versement du dimanche (barème, prorata) |
| `VOL` | fouille d'un corps |
| `STAFF` | toute commande staff, **motif obligatoire** ; `CONTREPASSATION` (ligne inverse avec raison) |

**Alertes au Poste de garde** (type `economie`) : virement ou échange ≥ 10 000 ; vol ≥ 1 000 ; trois virements vers le
même compte en 24 h ; mouvement entre **comptes liés** (même IP, déjà connus du Poste de garde) ; bourse ≥ 5 000
ramassée par un autre ; masse monétaire du village en hausse de plus de 10 % sur la semaine.

---

## 11. Outils staff — maquette 06

**Une ligne, pas un écran.** Dans la fiche joueur du Poste de garde (volet v3) :

- le chiffre du ryo quitte la ligne « Ping · temps de jeu » ;
- une section **« Économie »** s'ajoute aux sections repliées (Notes, Sanctions, Journal, Radio, Comptes liés) :
  aperçu « Bourse 1 240 · compte 8 600 · coffre 4/6 » ; dépliée : bourse, compte et palier, coffre (liste), dernier
  mouvement, et des **mots** : *Donner…* · *Retirer…* · *Voir le coffre* · *Geler le compte* · *Journal économie ›*
  (filtre du journal universel). Les formulaires s'ouvrent **dans le volet** (montant, bourse ou compte, motif).

Commandes (cible = pseudo, nom RP ou `#N°` ; motif obligatoire pour tout ce qui change un montant) :

| Commande | Effet |
|---|---|
| `/ryo` | (joueur) « Sur toi : 1 240 ryo » (la bourse ; le compte ne se voit qu'au guichet) |
| `/ryo donner\|retirer\|fixer <cible> <montant> [bourse\|compte] "<motif>"` | remplace `give\|take\|set` (gardés en alias) |
| `/banque voir <cible>` | compte, palier, coffre, 10 derniers mouvements (lecture) |
| `/banque coffre <cible>` | ouvre le coffre du compte (retrait journalisé) |
| `/banque palier <cible> <1-4>` · `/banque geler\|degeler <cible> "<motif>"` | |
| `/banque guichet ajouter <village> ["libellé"]` · `retirer` · `liste` | sur le bloc, le meuble ou le PNJ visé |
| `/banque contrepasser <n° d'opération> "<raison>"` | ligne inverse, l'originale reste |
| `/banque masse [village]` | bourses + comptes + trésor, évolution sur 4 semaines |
| `/banque solde bareme\|payer [village]` | barème ; versement forcé |

Grades proposés : lecture (fiche, `/banque voir`) Modérateur et plus ; donner / retirer / palier / geler Admin ;
contrepasser et masse Admin et plus. Tout est journalisé, y compris la simple consultation d'un compte.

---

## 12. RBCoins : rester séparés, corriger l'Échap

- **Aucun lien** avec le ryo, dans aucun sens (ADR 0006) : jamais dans la sacoche, jamais au guichet, jamais tendus,
  jamais volés. Le casino (GAMEPLAN §52) reste en ryo.
- **Échap** : la carte ÉCHOPPE affiche « **Sur toi · 1 240 ryo** » lu dans la **bourse** (et non plus « — ryo » tant
  que l'échoppe n'a pas été ouverte) ; le bloc RÉCOMPENSES affiche le **solde de RBCoins du compte** (« 320 RBCoins »,
  icône R) au-dessus de « +5 par heure de jeu ». Le solde vient du **launcher** (passé au lancement, comme le
  play-token ; aucune donnée sensible) ; il est relu à chaque lancement.

---

## 13. Ce qu'il faudra coder (pour mémoire, après validation)

- **ShinobiCore `economie/`** : `BourseService` (objet, fusion, débit / crédit atomiques sur le fil principal, identifiant
  unique, détection d'une bourse en double → alerte) ; `CompteService` (compte, palier, coffre, persistance par
  personnage) ; `GuichetRegistry` (village, type bloc / meuble / PNJ, entreprise BANQUE) ; `EchangeGaranti` ;
  `SoldeService` (dimanche, barème, prorata) ; journal et alertes. `ShinobiCharacter.ryo` devient le **compte** ;
  l'échoppe, `TendreService` et `/ryo` passent par la bourse. **Migration** : le solde actuel de chaque personnage va
  sur son **compte** (question 1).
- **Fouille** : la sacoche du corps (barre + sac + bourse) ; corps fouillable après le départ du joueur.
- **Mod** : montant sur la case et ligne « Sur toi » (sacoche), « Combien ? » (petit écran commun : poser, ranger,
  prendre), écran du guichet (onglets Déposer, Retirer, Virement, Échange garanti, Coffre), mode fouille de la
  sacoche, Échap (bourse et RBCoins), section Économie du Poste de garde.
- **Entreprises** : type `BANQUE` (postes = guichets ; « Sceller »).

---

## 14. Questions pour l'utilisateur (avec la recommandation)

> **Réponses de l'utilisateur (15/10)** en gras après chaque flèche ; le reste est la question d'origine.

1. **Migration** : le solde actuel de chacun va sur son **compte** ou dans sa **bourse** ? → **Compte** (personne ne se
   fait voler au lancement, et ça fait passer tout le monde au guichet). **→ OK.**
2. **Bourse sans poids** (RedLine) ou poids symbolique ? → **Sans poids**, mais une case. **→ OK.**
3. **Plafond de fouille** 5 000 ryo par victime et par scène, levé à la mort RP ? → **Oui**. **→ Au maximum 30 % de la
   bourse** (remplace le plafond de 5 000).
4. **Mort RP** : compte et coffre gelés 7 jours puis versés au **trésor du village** ? (ou hérités par un autre
   personnage du joueur) → **Trésor** (pas d'héritage : sinon on « meurt » pour transférer). **→ OK, mais héritage
   possible s'il est vu en RP, par un testament** (document enregistré au guichet, validé par le staff).
5. **Échange garanti** : V1 au guichet, les deux présents, scellé par le guichetier joueur s'il y en a un, frais 2 % ?
   La lettre de change asynchrone après la beta ? → **Oui aux deux**. **→ OK.**
6. **Guichetier** : PNJ / bloc en libre-service dès la V1, entreprise BANQUE quand un joueur veut tenir le guichet ? →
   **Oui, les deux coexistent**. **→ OK.**
7. **Paliers** 2 / 6 / 12 / 18 cases, plafonds 5 000 / 20 000 / 60 000 / sans plafond, coûts 0 / 2 000 / 8 000 /
   25 000 ? → **Oui** (réglables). **→ OK.**
8. **Objets refusés** : parchemins de technique, uniques, volés, explosifs ; et les **armes** ? → **Armes acceptées**.
   **→ Oui, sauf les armes RP légendaires / mythiques** (gunbai, épées des Sept Épéistes de la Brume…) : liste
   configurable.
9. **Prix conseillé du ramen** : passer de 100-150 à **40-80** pour que la solde d'un Genin suffise ? → **Oui**.
   **→ Sans importance** (les prix du ramen ne changent pas).
10. **Virement** : frais 1 % (5 % vers un autre village), 20 000 par jour, motif obligatoire ? → **Oui**. **→ OK.**
11. **Montant au sol** : une bourse posée reste 30 min ? → **Oui**. **→ OK.**
12. **Solde des RBCoins dans l'Échap**, transmis par le launcher au lancement ? → **Oui**. **→ OK**, affichés dans
    l'Échap, **distincts du ryo** (ne jamais mélanger, ADR 0006).

Retour visuel du 15/10 : sur la maquette 01, la ligne « Sur toi : 1 240 ryo » était trop collée à la barre → descendue
d'un cran dans le code.

---

## 15. Ce qui est codé (15/10, non publié)

### 15.1 Serveur — ShinobiCore, paquets `monnaie/` et `banque/`

| Classe | Rôle |
|---|---|
| `monnaie/MonnaieModule` | Démarre tout (hors mode build), commandes `/ryo`, `/banque`, `/testament` |
| `monnaie/MonnaieConfig` | Réglages, **tous les défauts dans le code** (sections `monnaie` et `banque` de `config.yml` facultatives, §17) |
| `monnaie/Bourse` | L'objet : `PAPER` + item_model `reborn:bourse_plate\|garnie\|lourde` selon le montant, PDC `reborn:ryo` (montant), `reborn:bourse_id` (anti-doublon), poids 0, une pile ; nom « Bourse », lignes « 1 240 ryo », gestes ; au sol / au coffre : « Bourse · 250 ryo » |
| `monnaie/BourseService` | Trouver (barre puis sac), créditer / débiter (fil principal), **une seule bourse** (fusion toutes les 2 s ; deux bourses au même identifiant = alerte « bourse en double »), **poser** (« Combien ? », 30 min au sol, journal `PERTE` à l'expiration), **ramasser** (touche F via `Ramassage`, fusion dans sa bourse, alerte ≥ 5 000 ramassée par un autre), **coffres posés** (clic sur sa bourse → « Combien veux-tu ranger ? » ; clic sur la bourse du coffre → tout reprendre ; la bourse ne passe jamais par le curseur), payer l'échoppe |
| `monnaie/FouilleService` | « Fouiller » : 3 s immobile (bouger ou être frappé interrompt ; la victime voit « On te fouille »), puis la **sacoche du corps** : barre, sac, bourse ; prendre un objet (marqué « volé », `reborn:vole_a`) ; « Prendre combien ? » plafonné à **30 %** ; corps laissé au sol (mannequin) fouillable même joueur parti ; alerte `vol` ≥ 1 000 |
| `monnaie/RyoCommand` | `/ryo` (§15.5) |
| `monnaie/MonnaieCanal` | Canaux `reborn:monnaie` et `reborn:banque` (§16) |
| `banque/BanqueService` | Comptes, **migration**, dépôt, retrait, virement, paliers, coffre (objets refusés), gel, contre-passation, trésors, **succession** (gel 7 j → trésor ou héritiers), masse monétaire, section Économie du Poste de garde |
| `banque/BanqueStore` | `plugins/ShinobiCore/banque/comptes.yml` (un compte par personnage) et `banque/banque.yml` (trésors, saisies, guichets, testaments, 500 dernières opérations numérotées, semaine de solde versée) ; écrit au plus toutes les 2 s, et à l'arrêt |
| `banque/Guichets` | Registre des guichets (bloc, meuble Nexo, PNJ) ; clic droit = ouvrir ; guichetier joueur = membre d'une entreprise `BANQUE` à 6 blocs |
| `banque/GuichetCanal` | L'écran du guichet côté serveur |
| `banque/EchangeGaranti` | L'échange garanti (§4.3) : dépôts retirés de la sacoche, confirmations remises à zéro au moindre changement, sceau du guichetier (ou du guichet), frais 2 % retenus sur ce qui est reçu, annulation = tout rendu |
| `banque/SoldeService` | Solde du dimanche (18 h, Europe/Paris) depuis le trésor du village, barème par grade + fonction (guichetier), 3 h de présence comptées minute par minute, prorata si le trésor est à sec, une fois par compte et par semaine |
| `banque/BanqueCommand` | `/banque`, `/testament` |

Branchements dans l'existant (modifications minimales) : `ShopManager` et l'onglet Vivres paient avec la **bourse**
(« Pas assez dans ta bourse — passe au guichet ») ; `TendreService` (« Donner des ryo », bourse en main tendue entière) ;
`InventoryManager` (montant dans le JSON des objets, `drop:` d'une bourse = la poser, accès au sac d'un personnage non
joué) ; `Ramassage` (une bourse rejoint la bourse) ; `CorpsService` (« fouiller » → `FouilleService`, `corps:<uuid>`) ;
`StaffPanel` (alertes `economie`, section `eco` de la fiche ; le ryo quitte la ligne ping) ; `GestionPerso` (remise à
zéro : compte à 0, coffre aux saisies) ; `VoirSac` (bourse et compte) ; `Entreprise.Type.BANQUE`.

### 15.2 Migration (décision 1)

Au démarrage (5 s après), puis à chaque premier accès à un compte : le champ `ryo` de chaque personnage (YAML des
personnages) va sur son **compte**. Idempotente : le compte est marqué `migre: true` et **écrit sur disque avant** que
l'ancien champ soit remis à 0 ; un reliquat trouvé sur un compte déjà migré (arrêt brutal entre les deux) est remis à 0
sans être recompté. Journal `ECONOMIE MIGRATION` par personnage (« ancien solde 1 240 ryo → compte ») et une ligne de
bilan (« N compte(s), X ryo »). Personne n'a de bourse au lancement : tout le monde passe au guichet.

### 15.3 Mod — reborn-hud

| Fichier | Rôle |
|---|---|
| `mixin/ItemCountBourseMixin` | Montant **abrégé en or** à la place du nombre de pile (750 · 1,2k · 12k · 1,2M) : barre, sacoche, coffres, guichet |
| `monnaie/MonnaieClient` | Canaux `reborn:monnaie` / `reborn:banque` |
| `monnaie/CombienScreen` | « Combien ? » commun (poser, ranger, prendre, déposer des ryo dans l'échange) |
| `monnaie/GuichetScreen` | Le guichet (balance du changeur, compte / bourse, 5 onglets en mots, une plaque) |
| `monnaie/FouilleHud` | « Fouille de Kenta · 2 s · ne bouge pas » + filet |
| `menu/inventory/InventoryScreen` | Ligne « **Sur toi : 1 240 ryo** » sous la barre (descendue, retour du 15/10) ; info-bulle de la bourse ; glisser / lâcher la bourse hors de la fenêtre → « Combien veux-tu poser ? » ; clic droit → « Poser… » ; **mode fouille** (titre FOUILLE, affaires du corps, clic = prendre, bourse = « Prendre combien ? ») |
| `menu/esc/EscTokonoma` | Carte ÉCHOPPE : « Sur toi · 1 240 ryo » (la bourse) ; RÉCOMPENSES : « 320 RBCoins · +5 par heure de jeu » (§15.7) |
| `staff/GardeEconomie` | Section « Économie » de la fiche joueur (Donner… · Retirer… · Voir le coffre · Geler le compte · Journal économie ›) |
| `interaction/InteractionMenus` | « Tendre Bourse · 990 ryo » ; corps au sol : « Fouiller » ; guichetier : « Ouvrir son compte » |
| Sacoche, **plus aucun kanji décoratif** | `tools/gen_sacoche_textures.py` : onglets Équipement / Cosmétique, étages Tout / Sac / Divers, emplacements cosmétiques, plaque Échap et cadre « aucun sac » = **icônes simples** au trait, disposition inchangée |

Textures : `tools/gen_monnaie_textures.py` → `textures/gui/monnaie/` (panneau, balance, pièce). Banc de captures :
`REBORN_MONNAIE_DEBUG=1` (`monnaie/MonnaieDebug`, MOD_HUD §5).

### 15.4 Le guichet

`/banque guichet ajouter <village> ["libellé"] [entreprise]` en visant le bloc, le meuble ou le PNJ. Clic droit =
écran du guichet. Village du personnage = celui du guichet : tout ; village allié (`banque.allies`) : dépôt, retrait,
virement avec 5 % de frais (pas de palier ni de coffre) ; déserteur, exilé, sans village, ennemi : « Le guichetier ne te
connaît pas ». Compte gelé : l'écran s'ouvre, toutes les opérations sont refusées. **Guichetier joueur** : `/entreprise
creer banque "Banque de Konoha" <responsable>` puis `/entreprise membre ajouter …` ; en service à 6 blocs d'un guichet
(de son entreprise si le guichet en nomme une), il est affiché (« Au guichet : Hana Kurenai »), il ouvre le compte d'un
client (menu R → « Ouvrir son compte », `/banque client <joueur>`) et il **scelle** les échanges (`/banque sceller` ou
`refuser`, prévenu par une notification) ; il ne voit jamais le solde ; solde de fonction +300.

### 15.5 Commandes

| Commande | Droits | Effet |
|---|---|---|
| `/ryo` | joueur | « Sur toi : 1 240 ryo » |
| `/ryo donner\|retirer\|fixer <cible> <montant> [bourse\|compte] "<motif>"` | Admin ou `shinobicore.ryo.admin` | Compte par défaut (même hors ligne) ; bourse si le personnage est joué en ligne ; **motif obligatoire** ; `give\|take\|set` en alias |
| `/banque voir <cible>` | Modérateur+ | Compte, palier, coffre, 10 derniers mouvements, testament (consultation journalisée) |
| `/banque coffre <cible>` | Admin+ | Ouvre le coffre du compte (coffre vanilla) ; retraits / ajouts relus à la fermeture et journalisés |
| `/banque palier <cible> <1-4> "<motif>"` · `geler\|degeler <cible> "<motif>"` | Admin+ | |
| `/banque guichet ajouter <village> ["libellé"] [entreprise]` · `retirer` · `liste` | Admin+ | Sur le bloc, le meuble ou le PNJ visé (6 blocs) |
| `/banque contrepasser <n° d'opération> "<raison>"` | Admin+ | Virement, staff, solde, héritage : ligne inverse, l'originale reste (500 dernières opérations) |
| `/banque masse [village]` | Admin+ | Bourses (dernier montant connu hors ligne) + comptes + trésor |
| `/banque solde [bareme\|payer [village]]` | Admin+ | Barème ; versement forcé (idempotent par compte et par semaine) |
| `/banque tresor <village> [ajouter\|retirer\|fixer <montant> "<motif>"]` | Admin+ | Le trésor qui paie la solde |
| `/banque sceller\|refuser` · `/banque client <joueur>` | guichetier en service | |
| `/testament deposer <héritier>[:part] … ["texte"]` | joueur, **au guichet** | Héritier = `#N°` ou nom d'un contact ; parts en % (le reste réparti) ; statut « déposé » |
| `/testament enregistrer <cible> <héritier>[:part] … ["texte"]` | Admin+ | Enregistré **et validé** (vu en RP) |
| `/testament valider\|revoquer\|voir <cible>` | Admin+ (voir : Modérateur+) | |

Admin = grade Admin du Poste de garde, ou `shinobicore.banque.admin` (nouvelle permission, op par défaut).

### 15.6 Mort RP, testament (décision 4)

RPK exécuté (événement `CharacterDeathEvent`), ou mort posée par le staff (vue au passage suivant, 5 min) : compte et
coffre **gelés 7 jours** (`GEL_MORT`). Si le staff annule la mort pendant le gel : dégel (`DEGEL_MORT`). Au bout de 7
jours : **testament validé** → le solde est partagé entre les héritiers selon leurs parts (`HERITAGE`, opération
numérotée, contre-passable), les objets du coffre vont au coffre du premier héritier (sinon aux saisies du village) ;
**sinon** → trésor du village (`SUCCESSION`), objets aux saisies. Un testament seulement déposé (non validé) ne compte pas.

### 15.7 RBCoins (décision 12)

Le mod lit le solde dans la propriété système `-Dreborn.rbcoins=<n>` (ou `-Dreborn.rbcoinsPath=<fichier>`), à passer par
le **launcher** au lancement comme le play-token. **Le launcher ne l'envoie pas encore** (train launcher, §19) : l'Échap
affiche « — RBCoins · +5 par heure de jeu ». Jamais mêlé au ryo.

### 15.8 Journal (catégorie Économie)

Actions écrites : `DEPENSE` (échoppe), `DON`, `POSE_SOL`, `RAMASSE`, `PERTE`, `RANGE`, `PRIS`, `DEPOT`, `RETRAIT`,
`VIREMENT`, `FRAIS`, `PALIER`, `COFFRE_DEPOT`, `COFFRE_RETRAIT`, `COFFRE_REFUS`, `ECHANGE_OUVERT`, `ECHANGE_DEPOT`,
`ECHANGE_CONFIRME`, `ECHANGE_SCELLE`, `ECHANGE_ANNULE` (+ `ECHANGE_RENDU`, `ECHANGE_RECU` côté bourse), `SOLDE`,
`VOL` (et `OBJET VOL` pour les objets), `STAFF`, `CONTREPASSATION`, `CONSULTATION`, `TRESOR`, `MIGRATION`,
`DOUBLON`, `GEL_MORT`, `DEGEL_MORT`, `SUCCESSION`, `HERITAGE`, `TESTAMENT` ; staff : `GUICHET_AJOUTER`,
`GUICHET_RETIRER`. Chaque ligne : acteur, personnage visé, montant, bourse ou compte avant → après, motif, lieu, et
le **N° d'opération** (`op 123`) pour ce qui touche un compte. Alertes `economie` au Poste de garde : virement ou
échange ≥ 10 000, vol ≥ 1 000, trois virements vers le même compte en 24 h, virement entre comptes liés (même IP),
bourse ≥ 5 000 ramassée par un autre, bourse en double.

## 16. Protocole

`reborn:monnaie` (JSON UTF-8 brut S → C, texte C → S) et `reborn:banque` : détail dans
[`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) § 4 quinquies.

## 17. Réglages (`config.yml`, facultatifs, défauts dans le code)

`monnaie.etats.garnie` 100, `.lourde` 5 000 · `monnaie.sol-minutes` 30 · `monnaie.alerte-ramasse` 5 000 ·
`monnaie.vol.pourcent` 30, `.sans-plafond-mort-rp` true, `.fouille-secondes` 3, `.alerte` 1 000 ·
`banque.guichet.portee` 5, `.portee-guichetier` 6, `.frais-allie-pourcent` 5 · `banque.allies` {} ·
`banque.villages-refuses` [deserteur, exile, nukenin] · `banque.virement` 1 %, min 5, 5 % autre village, 20 000 / jour,
alerte 10 000 · `banque.echange` 2 %, min 10, distance 4, alerte 10 000 · `banque.paliers` (2 / 6 / 12 / 18 cases,
0 / 2 000 / 8 000 / 25 000, plafonds 5 000 / 20 000 / 60 000 / aucun) · `banque.refuses` (parchemins, uniques, volés,
`ids`, `armes-ids` [gunbai, kubikiribocho, samehada, kiba, nuibari, shibuki, hiramekarei, kabutowari],
`armes-rarete-min` LEGENDAIRE, `tag` legendaire) · `banque.solde` (SUNDAY 18 h Europe/Paris, présence 3 h, fonction
banque +300, barème Académicien 0 · Genin 200 · Genin confirmé 300 · Chūnin 500 · Konin 700 · Tokubetsu Jōnin 900 ·
Jōnin 1 200 · Commandant 1 600 · Kage 2 000) · `banque.tresor-initial` {} · `banque.mort-rp.gel-jours` 7. Section
documentée à la fin du `config.yml` du plugin (un `config.yml` de serveur existant n'a pas besoin d'être modifié).

## 18. Objets à modéliser (Blockbench, par l'utilisateur)

Provisoires simples posés dans le **pack Nexo** (`minecraft/server-config/nexo/pack/assets/reborn/`, sprites 16 × 16
`item/generated`, générés par `minecraft/mod-hud/tools/gen_monnaie_nexo.py`) ; remplacer en **gardant les identifiants**.
Catalogue Nexo : `minecraft/server-config/nexo/items/reborn_monnaie.yml`.

| Id (item_model `reborn:…`) | États / variantes | Usage | Notes |
|---|---|---|---|
| `bourse_plate` | bourse presque vide | **< 100 ryo** | kinchaku de tissu laqué rouge, cordon d'or ; tenu en main, au sol, en case 16 px |
| `bourse_garnie` | bourse remplie | **< 5 000 ryo** | même objet, panse ronde |
| `bourse_lourde` | bourse pleine à craquer | **≥ 5 000 ryo** | même objet, pièces qui dépassent |
| `testament` | (une) | Document RP (catalogue, remise en scène) | rouleau scellé de cire rouge ; le testament valable est l'enregistrement du guichet |
| *(facultatif)* guichet | meuble Nexo | Le guichet de la banque | n'importe quel bloc, meuble ou PNJ peut servir (`/banque guichet ajouter`) ; à créer comme meuble Nexo si l'on veut un comptoir dédié |

## 19. Écarts avec la proposition, à trancher

- **Mort RP et plafond de 30 %** : la décision 3 remplace le plafond de 5 000 par 30 % ; la proposition levait le plafond
  à la mort RP. Gardé **sans plafond à la mort RP** (`monnaie.vol.sans-plafond-mort-rp: true`, le reste est perdu de
  toute façon) — à confirmer.
- **KO puis déconnexion** : seul le corps de la **mort RP** (mannequin) se fouille joueur parti ; un joueur KO qui se
  déconnecte emporte sa bourse (alerte « Déco en combat » déjà existante). Un corps fouillable après déconnexion en KO
  demanderait un mannequin de KO : à décider.
- **Coffres posés** : un clic suffit pour reprendre une bourse rangée (le double-clic marche aussi) ; ranger = clic sur
  sa bourse quand un coffre est ouvert (plutôt que « glisser sur le coffre »), pour qu'une bourse ne passe jamais par le
  curseur.
- **Frais de l'échange garanti** : retenus sur ce que reçoit chacun (2 %, min. 10 par côté qui donne des ryo).
- **Section Économie du Poste de garde** : dépliée dans le volet comme les autres sections (la maquette 06 la montrait
  en encart à gauche).
- **Alertes** « masse du village + 10 % » et « Alertes : aucune » de la fiche : non codées (la masse se lit avec
  `/banque masse`).
- **RBCoins** : le launcher doit passer `-Dreborn.rbcoins` (train launcher) ; en attendant « — ».
- **Lettre de change** asynchrone : après la beta (décision 5).
- **Missions** (sources de ryo) : à venir (GAMEPLAN §22) ; aujourd'hui l'argent naît du staff (`/ryo donner`, trésor).

## 20. À tester (à deux comptes A et B, après publication)

Préparation (A staff, sur le dev) : viser un comptoir, `/banque guichet ajouter konoha "Banque de Konoha"` ;
`/banque tresor konoha ajouter 20000 "test"` ; pour tester sans attendre : `banque.mort-rp.gel-jours: 0` et
`banque.solde.presence-heures: 0` dans le `config.yml` du dev (à remettre ensuite).

1. **Migration** : journal Économie `MIGRATION` au démarrage ; `/banque voir A` → compte = ancien solde ; `/ryo` → « Sur
   toi : 0 ryo ».
2. **Retirer** 1 000 au guichet (clic droit) → une bourse dans la barre, « 1k » en or sur la case ; sacoche (E) : « Sur
   toi : 1 000 ryo » sous la barre, info-bulle « Bourse / 1 000 ryo ».
3. **Déposer** au-delà du plafond du palier 1 (5 000) → refus « Plafond du palier atteint ».
4. **Poser** : glisser la bourse hors de la sacoche → « Combien veux-tu poser ? » 250 → bourse au sol « [F] Ramasser
   Bourse · 250 ryo » ; B la ramasse (F) → +250 dans **sa** bourse (une seule bourse) ; journal `POSE_SOL`, `RAMASSE`.
5. **Tendre** : R sur B → « Donner des ryo » 100 → B prend (F) ; bourse en main → « Tendre Bourse · … ryo » (entière).
6. **Échoppe** : ration à 30 ryo payée par la bourse ; sans assez → « Pas assez dans ta bourse — passe au guichet ».
7. **Coffre posé** : ouvrir un coffre, clic sur sa bourse → « Combien veux-tu ranger ? » 100 → « Bourse · 100 ryo » dans
   le coffre ; clic dessus → reprise entière.
8. **Coffre du compte** (onglet Coffre) : une épée acceptée ; un parchemin refusé (case rouge, « Ce parchemin ne va pas au
   coffre — il reste volable ») ; un gunbai refusé ; « Passer · 2 000 ryo » → palier 2, 6 cases.
9. **Virement** A → B (contact avec ›, ou `#N°`), motif obligatoire, frais 1 % ; B reçoit « Virement reçu : … ».
10. **Échange garanti** : A et B au guichet, A « Proposer » ; l'onglet s'ouvre chez B ; A dépose 500 ryo, B un objet ;
    changer un dépôt remet les confirmations à zéro ; les deux confirment → scellé, frais 2 % ; un second essai où B
    s'éloigne → annulé, tout rendu.
11. **Guichetier** : `/entreprise creer banque "Banque de Konoha" <A>` ; B au guichet voit « Au guichet : … » ; A (menu R
    sur B) « Ouvrir son compte » → l'écran s'ouvre chez B ; un échange entre B et un 3e compte attend le sceau de A
    (`/banque sceller`).
12. **Fouille** : B KO ; A R → « Fouiller » ; bouger pendant les 3 s annule ; sacoche du corps (titre FOUILLE) ; prendre un
    objet (puis : refusé au coffre de A, « objet volé ») ; « Prendre combien ? » plafonné à 30 % ; vol ≥ 1 000 → alerte
    `economie` au Poste de garde.
13. **Mort RP** : RPK sur B, B part → corps au sol ; A R sur le corps → « Fouiller » (bourse entière) ; `/banque voir B` →
    « mort RP (gelé) ».
14. **Testament** : avant la mort, B au guichet `/testament deposer #<N° de A>:100 "à mon frère"` ; staff `/testament
    valider B` ; après le gel → A reçoit le compte de B (`HERITAGE`), sinon trésor (`SUCCESSION`).
15. **Solde** : `/banque solde payer konoha` → « Solde de la semaine : +… » ; le trésor baisse ; une 2e fois → rien.
16. **Staff** : fiche du Poste de garde → section Économie ; « Donner… » 500 compte avec motif ; « Voir le coffre » ;
    « Geler le compte » → le guichet refuse tout ; « Journal économie › ».
17. **Échap** : carte ÉCHOPPE « Sur toi · … ryo » ; RÉCOMPENSES « — RBCoins · +5 par heure de jeu » (tant que le
    launcher n'envoie pas le solde).
18. **Sacoche** : plus aucun kanji (onglets Équipement / Cosmétique, étages Tout / Sac / Divers, plaques, Échap).
