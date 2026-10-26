# Statistiques et pilotage du serveur

> **Proposition du 2026-10-25, non codée, à valider.** Ce document dit ce qu'on mesure et pourquoi, la définition
> exacte de chaque indicateur, les événements à collecter, les agrégats, l'outil et le cadre RGPD. Il est écrit pour
> un lecteur data science : les formules sont exactes et les choix de méthode sont justifiés.
>
> Point de départ : la demande du 25/10 (un tableau inspiré du panel vu chez Fuze III) et trois audits en lecture
> seule du panel staff et des données (25/10). Le constat de ces audits est résumé au §2. Relu le 25/10 avec la
> maquette `Maquettes/panel_v2/12_pilotage` : KPI 10 redéfini, entonnoir précisé, `D` défini, palette validée,
> lot A coupé en deux (A1 indépendant de la candidature v2).
>
> **À ne pas confondre** avec les statistiques des personnages en jeu (Taïjutsu, Vigueur… :
> [`SPEC_STATS_SERVICE.md`](./SPEC_STATS_SERVICE.md)). C'est pour cela que la page du panel s'appelle
> **« Pilotage »** et non « Stats » (Q1).
>
> Documents liés : [`../gameplay/CANDIDATURE_ET_DOUANE.md`](../gameplay/CANDIDATURE_ET_DOUANE.md) (phases de la
> candidature, douane), [`../operations/COMMUNICATION.md`](../operations/COMMUNICATION.md) §2 (calendrier et
> indicateurs par phase), [`../gameplay/MONNAIE_ET_BANQUE.md`](../gameplay/MONNAIE_ET_BANQUE.md) §7 (sources et puits
> du ryo, cible de +5 % par semaine), [`../adr/0006-monnaies-ryo-et-rbcoins.md`](../adr/0006-monnaies-ryo-et-rbcoins.md),
> [`../design/DIRECTION_ARTISTIQUE.md`](../design/DIRECTION_ARTISTIQUE.md) §7 (palette).

---

## 0. En dix lignes

1. **Quatre questions** guident tout : le lancement attire-t-il ? les joueurs restent-ils ? le staff tient-il la
   charge ? l'économie RP dérive-t-elle ? Un indicateur qui ne fait prendre aucune décision n'entre pas sur la page.
2. **Aujourd'hui**, la base couvre le hors jeu (comptes, candidature, douane, tickets, audit). Rien de ce qui se
   passe **en jeu** n'y remonte : ni sessions, ni économie, ni sanctions. Le launcher n'envoie aucune télémétrie.
3. **Douze indicateurs** sur la page Pilotage (§3) : pic du soir, actifs 7 jours, nouveaux joueurs, rétention J1 et
   J7, durée de session, candidatures, conversion, présence à la douane, écrits relus sous 48 h, tickets répondus
   sous 24 h, masse de ryo. Chacun a une formule, un grain, une fenêtre et une règle multi-serveurs.
4. **Unité d'analyse = le compte Reborn** (`User.id`), jamais l'UUID Minecraft ni le personnage. Le **jour de jeu**
   va de 5 h à 5 h (heure de Paris), pour ne pas couper une soirée RP à minuit.
5. **Rétention J7 en fenêtre** (actif au moins une fois entre J+7 et J+13), adaptée au rythme hebdomadaire du RP.
   Cohortes par semaine d'entrée en jeu, colonnes S0 à S4 (J0 à J34).
6. **Écarté du panel Fuze III** : revenus, ARPPDAU, taux d'achat, acquisition par nom d'hôte, entonnoir de rachat
   (§6). Remplacés par l'entonnoir d'acquisition Reborn, de l'arrivée sur le Discord au retour à J7.
7. **Une table d'événements en ajout seul** (`StatEvenement`) alimentée par l'API, le jeu (ShinobiCore, route
   signée), le bot et, avec accord, le launcher. Le panel ne lit que des **agrégats quotidiens**.
8. **Outil** : la page Pilotage dans le panel pour les douze indicateurs, et un **Metabase auto-hébergé**, joignable
   par tunnel SSH seulement, pour l'exploration par OWNER et ADMIN (§9).
9. **RGPD** : agrégats seulement à l'écran, seuil de 5 personnes, pseudonymisation dans le schéma d'analyse,
   télémétrie client sur accord préalable, événements bruts 13 mois au plus (§10).
10. **Ce qui n'est pas collecté au moment ne se reconstitue pas.** D'où l'ordre des lots (§11) : ce qui ne se
    rattrape pas (lot A1 : arrivées sur le Discord, téléchargements, joueurs en ligne) avant l'ouverture du Discord
    (14/11), sans attendre la candidature v2 ; page v1 avant les candidatures (28/11) ; sessions de jeu en service le
    jour de la beta (2/01).

---

## 1. Objectifs et questions métier

Chaque question est liée à la décision qu'elle éclaire. Les ordres de grandeur attendus (quelques dizaines à
quelques centaines de joueurs) imposent des méthodes pour petits effectifs (§12).

### 1.1 Lancement de la beta : le recrutement fonctionne-t-il ?

| Question | Décision éclairée |
|---|---|
| D'où viennent les candidats (TikTok, YouTube, ami, autre serveur) ? | Où mettre l'effort de communication, quels créateurs relancer |
| À quelle étape perd-on les gens : compte, candidature commencée, envoyée, écrit accepté, douane, première session ? | Quelle étape simplifier (formulaire trop long, douane trop rare, lancement qui échoue) |
| Combien de temps entre l'envoi et l'acceptation ? | Ouvrir plus de séances de douane, renforcer la relecture |
| Les candidats réservés viennent-ils à la douane ? | Rappels, horaires des séances |
| Le jeu se lance-t-il chez les nouveaux ? | Priorité aux correctifs du launcher |

### 1.2 Rétention et activité : les joueurs restent-ils ?

| Question | Décision éclairée |
|---|---|
| Combien de joueurs en même temps le soir, et à quelle heure ? | Horaires des events, des séances de douane, présence du staff |
| Combien de joueurs actifs sur la semaine, et la tendance ? | Lancer une vague de whitelist, une campagne |
| Les joueurs reviennent-ils après leur première soirée (J1), leur deuxième semaine (J7), leur premier mois (J30) ? | Améliorer l'accueil en jeu, le premier soir, le contenu de mi-parcours |
| Une mise à jour a-t-elle fait fuir ou revenir ? | Revenir sur un changement (les publications sont annotées sur les courbes) |

### 1.3 Charge du staff : l'équipe tient-elle ?

| Question | Décision éclairée |
|---|---|
| Combien d'écrits attendent, et depuis combien de temps le plus ancien ? | Recruter des relecteurs, répartir |
| Les tickets reçoivent-ils une réponse sous 24 h ? | Astreintes, réponses types |
| Combien de sanctions par semaine, quelle récidive ? | Règlement à clarifier, modération à renforcer |
| Les séances de douane sont-elles remplies ? | Capacité, fréquence des séances |

On montre **la charge, jamais un classement** des membres du staff.

### 1.4 Économie RP : la monnaie dérive-t-elle ?

| Question | Décision éclairée |
|---|---|
| La masse de ryo suit-elle la cible de +5 % par semaine ([`MONNAIE_ET_BANQUE.md`](../gameplay/MONNAIE_ET_BANQUE.md) §7.3) ? | Régler les sources (solde, missions) et les puits (échoppe, paliers, frais) |
| D'où vient et où part l'argent, par motif ? | Quel puits ou quelle source corriger |
| La richesse se concentre-t-elle ? | Taxes, coûts, enquête sur une duplication |
| Le staff crée-t-il beaucoup de ryo ? | Contrôle des abus |

### 1.5 En second plan

- **Vie RP** : personnages créés, morts RP, KO, répartition par village, clan et rang.
- **Santé technique** : versions du launcher, lancements aboutis, plantages, FPS sur les petites configurations (cible
  4 Go de RAM).
- **Plus tard** : revenus de la boutique de compte (RBCoins), quand elle existera.

---

## 2. Ce qui existe aujourd'hui (résumé des audits du 25/10)

| Donnée | Où | État pour les statistiques |
|---|---|---|
| Comptes : `createdAt`, `discordLinkedAt`, `lastLoginAt`, `oralPassedAt`, `lastKnownCountry` | `User` | Utilisable |
| Connexions au launcher (une ligne par connexion **et par rafraîchissement**, version dans `userAgent`, IP) | `Session` | Substitut de l'activité launcher ; **jamais purgée** (RGPD, §10) |
| Candidature : une seule ligne par compte, **réécrite** à chaque renvoi, **supprimée** au retrait ou à la remise à zéro | `WhitelistApplication` | L'historique de l'entonnoir ne s'en reconstruit pas |
| Brouillon de candidature, effacé à l'envoi | `CandidatureBrouillon` | Début de candidature perdu |
| Séances et réservations de douane (`MISSED`, `DONE`, résultat, examinateur) | `DouaneSeance`, `DouaneReservation` | Utilisable ; la présence en jeu n'est pas encore appelée par le jeu |
| Tickets et messages typés joueur, staff, système | `Ticket`, `TicketMessage` | Délai de première réponse calculable |
| Décisions et actions du staff, en ajout seul, chaînées | `AuditLog` | **Meilleure source historique** ; ne trace pas les actions des joueurs |
| Joueurs en ligne (ping du serveur, cache 10 s en mémoire, un seul hôte) | `ServerStatusService` | Jamais stocké |
| Source de découverte (facultative, v2) | `WhitelistApplication.discoverySource` | Utilisable |
| Boutique, incidents, events RP, social | `Purchase`, `ShopProduct`, `IntegrityIncident`, `ScheduledEvent`… | **Schéma mort** : jamais écrit ; dossiers `shop/`, `events/`, `incidents/` vides |
| Journal universel du jeu (connexions avec durée, chaque mouvement de ryo, combat, KO, staff, tchat) | `plugins/ShinobiCore/logs/<catégorie>-AAAA-MM-JJ.log`, 90 jours | Texte semi-structuré, reste sur le serveur |
| Sanctions | `sanctions.yml` du Poste de garde | Reste sur le serveur |
| Diagnostics du launcher (plantage, mémoire, mods) | PC du joueur | Rien n'est envoyé |

Ce qui facilite la suite :
- l'API sait déjà recevoir des requêtes **signées** du jeu (`GameSignatureGuard` : secret par serveur, horodatage à
  ± 60 s, nonce à usage unique, limite de débit) ;
- ShinobiCore a déjà un client HTTP qui signe ses appels (`CandidatureClient`) ;
- le jeton de jeu porte le compte (`sub`), la portée (`play` ou `douane`) et le serveur visé (`aud`) ;
- `@nestjs/schedule` est déjà installé dans l'API.

---

## 3. La page « Pilotage » : douze indicateurs

### 3.1 Vocabulaire commun (s'applique à tous les indicateurs)

| Terme | Définition |
|---|---|
| **Joueur** | Un compte Reborn (`User.id`). Un joueur qui a trois personnages compte une fois. |
| **Serveur** | Un identifiant de configuration (`dev`, `build`, futurs serveurs du network), jamais une adresse. Le serveur `build` est exclu par défaut (ce n'est pas du jeu). |
| **Network** | L'union des serveurs de jeu. Un compte actif sur deux serveurs compte **une** fois : un total network est un `count(DISTINCT)`, **jamais une somme** des serveurs (sauf pour les joueurs simultanés, voir KPI 1). |
| **Jour de jeu** | `jour(t) = date((t à l'heure de Paris) − 5 h)`. Une soirée de 21 h à 1 h 30 tombe sur un seul jour. Tout est stocké en UTC, tout est affiché à l'heure de Paris. |
| **Session de jeu** | Une connexion au serveur avec la portée `play` (la douane est exclue), de la connexion à la déconnexion. Deux sessions du même compte sur le même serveur séparées de **moins de 5 minutes** sont fusionnées (reconnexions). |
| **Session qualifiante** | Une session de **2 minutes ou plus** après fusion. Les plus courtes sont comptées à part (« sessions courtes » : souvent un plantage à l'arrivée). Une session compte pour le jour de jeu de son **début**. |
| **Actif le jour d** | Au moins une session qualifiante commencée le jour de jeu d. |
| **Première session** | La première session qualifiante du compte sur le network (ou sur le serveur, en vue par serveur). C'est le **J0** des cohortes et de la rétention : pas la création du compte, parce que l'écrit et la douane prennent des jours. |
| **Comptes exclus** | Comptes de développement (`msAccountId` commençant par `dev:`). Les comptes staff **comptent** comme joueurs par défaut, avec un filtre « hors staff » (Q3). Le temps passé en mode staff est mesuré à part. |
| **Fenêtre complète** | Un taux n'est affiché que pour les cohortes dont la fenêtre d'observation est **terminée** (pas de rétention J7 pour une cohorte de cinq jours). |
| **Petit effectif** | Tout taux est affiché avec son **effectif** (n) et un **intervalle de Wilson à 95 %**. Sous n = 10, on affiche l'effectif seul, en gris. |
| **Notation** | `#{u : condition}` = nombre de joueurs qui remplissent la condition ; `#C` = effectif de la cohorte C ; **D = le dernier jour de jeu terminé** (la veille : le calcul tourne à 5 h 15, juste après la fin du jour de jeu) ; d = un jour de jeu. |

### 3.2 Les douze indicateurs

**Activité et rétention**

| # | Indicateur | Formule exacte | Grain, fenêtre | Network | Source, lot |
|---|---|---|---|---|---|
| 1 | **Pic du soir** (joueurs simultanés) | `PCU(d, s) = max enLigne(s, t)` sur les échantillons de la minute du jour de jeu d. Complément robuste : médiane de `enLigne` entre 20 h et 23 h. | Quotidien ; tuile = pic d'hier, comparé au **même jour de la semaine précédente** (saisonnalité hebdomadaire) ; courbe 30 j | `max_t Σ_s enLigne(s, t)` : la somme est juste ici, un joueur n'est que sur un serveur à la fois | `ServeurEchantillon` ; ping dès le lot A1, battement du jeu au lot C |
| 2 | **Joueurs actifs 7 jours** (WAU) | `WAU(d) = #{u : u actif un jour de [d−6, d]}` | Quotidien glissant ; courbe 90 j | `count(DISTINCT)` sur tous les serveurs | `JoueurJour`, lot C. **Avant** : substitut « launcher actif 7 j » (comptes ayant une ligne `Session` créée dans la fenêtre), affiché comme tel |
| 3 | **Nouveaux joueurs** | `N(d) = #{u : jour(première session de u) = d}`, somme sur [d−6, d] | Quotidien glissant 7 j | Première session sur le network | `JoueurPremiere`, lot C |
| 4 | **Rétention J1** | Cohorte `C_d` = joueurs dont la première session est le jour d. `R1 = Σ_d #{u ∈ C_d : u actif le jour d+1} / Σ_d #C_d`, sur les cohortes de [D−29, D−2] (taux poolé, pondéré par l'effectif) | Quotidien ; 4 semaines de cohortes ; Wilson | Network | `JoueurJour`, lot C (lot D pour l'affichage complet) |
| 5 | **Rétention J7** | `R7 = Σ_d #{u ∈ C_d : u actif au moins un jour de [d+7, d+13]} / Σ_d #C_d`, sur les cohortes de [D−41, D−14] | Quotidien ; 4 semaines de cohortes complètes ; Wilson | Network | Idem |
| 6 | **Durée médiane de session** | Médiane de `fin − début` des sessions qualifiantes commencées sur [D−6, D] ; affichée avec l'écart interquartile. Une session sans déconnexion (arrêt brutal du serveur) prend pour fin le dernier battement du serveur et est marquée « fin estimée » ; leur part est affichée. | 7 j glissants | Par serveur ; network = toutes sessions confondues | `SessionJeu`, lot C |

**Entonnoir de recrutement** (détail au §4)

| # | Indicateur | Formule exacte | Grain, fenêtre | Network | Source, lot |
|---|---|---|---|---|---|
| 7 | **Candidatures envoyées** | Nombre de comptes ayant envoyé leur **première** candidature sur [D−6, D] ; les renvois après « à revoir » sont affichés à côté, pas additionnés | 7 j glissants ; barres par semaine | Sans objet (la candidature est au niveau du network) | `candidature.envoyee`, lot A2 ; reprise historique depuis `submittedAt` et l'audit |
| 8 | **Conversion candidature → accepté** | Cohorte = comptes dont la première candidature est envoyée la semaine w. `conv = #{u : candidature.acceptee dans les 28 jours suivant le premier envoi} / #cohorte`, poolé sur les 4 dernières semaines **mûres** (w + 28 j ≤ D). Délai médian envoi → acceptation affiché dessous. | Hebdomadaire ; Wilson | Sans objet | Lot A2 |
| 9 | **Présence à la douane** | `DONE / (DONE + MISSED)` sur les réservations des séances terminées des 14 derniers jours (annulations exclues) | 14 j glissants | Par serveur de douane | `DouaneReservation` : **calculable dès aujourd'hui** ; `douane.presence` quand le jeu l'appellera |

**Charge du staff**

| # | Indicateur | Formule exacte | Grain, fenêtre | Network | Source, lot |
|---|---|---|---|---|---|
| 10 | **Écrits relus sous 48 h** | Envois (premiers envois et renvois) reçus sur [D−15, D−2], donc vieux d'au moins 48 h : `#{e : première décision staff sur l'écrit après l'envoi (candidature.decision, partie hrp ou rp) − envoi ≤ 48 h} / #envois`. Dessous : délai médian et nombre d'envois encore sans décision au-delà de 48 h. Même logique que le KPI 11 : un taux à seuil compte les dossiers oubliés, qu'une médiane ignorerait. Seuil = celui du retard dans le panel (PANEL_V2 Q10) | 14 j glissants ; Wilson | Sans objet | `candidature.envoyee`, `candidature.decision` (lot A2) ; reprise depuis l'audit |
| 11 | **Tickets répondus sous 24 h** | Tickets créés sur [D−14, D−1] (au moins 24 h d'âge) : `#{t : premier message STAFF − création ≤ 24 h} / #tickets`. La médiane du délai est affichée dessous, avec le nombre de tickets **sans réponse**. Le taux à 24 h est l'indicateur principal parce qu'il compte les tickets sans réponse, qu'une médiane ignorerait. | 14 j glissants | Sans objet | `Ticket`, `TicketMessage` : calculable dès aujourd'hui |

Le **stock** d'écrits en attente (phase `ECRIT_EN_ATTENTE` maintenant, âge du plus ancien, médiane des âges) n'est pas
un indicateur : c'est un compteur en direct du bloc Maintenant et de la page À traiter, calculable dès aujourd'hui.
La première version de ce document en faisait le KPI 10 **et** le mettait dans Maintenant : deux fois le même chiffre.

**Économie RP**

| # | Indicateur | Formule exacte | Grain, fenêtre | Network | Source, lot |
|---|---|---|---|---|---|
| 12 | **Masse de ryo et variation** | `M(d)` = bourses + comptes + trésors, par village, à l'instantané du jour d. `Δ7 = (M(d) − M(d−7)) / M(d−7)`, comparé à la bande cible **+5 % ± 3 points** (Q8) | Quotidien ; courbe 90 j empilée par village | **Par serveur seulement** : chaque serveur a sa propre économie, une somme network n'a pas de sens | `InstantaneJeu`, lot D |

### 3.3 Indicateurs secondaires (vue « Détail » et Metabase)

Pas sur la vue Essentiel, mais calculés chaque nuit, pour la vue Détail et Metabase (ce qui est déjà sur la vue
Essentiel n'est pas répété ici) :

| Domaine | Indicateurs |
|---|---|
| Activité | DAU, ratio DAU / WAU (assiduité), temps de jeu hebdomadaire médian par joueur actif, sessions courtes, part des sessions à fin estimée |
| Rétention | Rétention J30 (fenêtre [J+28, J+34]), courbe de survie de Kaplan-Meier, histogramme des durées de session, désengagement (aucune session depuis 14 jours) |
| Entonnoir | Taux par étape et par source de découverte, délais médians et P90 entre étapes, boucles « à revoir », retraits |
| Staff | Passages par séance, sanctions par type et par semaine, récidive à 30 jours |
| Économie | Création et destruction par motif, solde net hebdomadaire, courbe de Lorenz des soldes, part des 10 % les plus riches, vols et montants, alertes de bourse en double |
| Vie RP | Personnages créés, actifs 7 j, morts RP par cause, KO, passages à l'hôpital, duels, répartition par village, clan et rang (seuil de 5) |
| Santé technique | Versions du launcher (depuis `Session.userAgent`), **lancements aboutis** (jeton `play` émis puis connexion au serveur dans les 5 minutes), plantages pour 100 sessions et FPS p5 par tranche de RAM (lot F, sur accord) |
| Démographie (ADMIN et plus) | Langue, croisements (âge × village…) ; tranches d'âge déclaré 15-17 / 18-24 / 25+ et pays sont sur la vue Essentiel ; jamais une case de moins de 5 |

### 3.4 Disposition de la page

Une seule page, cinq blocs, du plus urgent au plus analytique. L'**Accueil** du panel reste opérationnel (« À faire » :
candidatures, douane, tickets, anomalies) ; la page Pilotage est analytique. On n'y met pas deux fois la même chose.

1. **En-tête** : titre, bascule **Essentiel · Détail**. Le serveur est celui de la barre latérale du panel (pas de
   second sélecteur). **Pas de période globale** : chaque tuile porte sa fenêtre dans son titre, chaque courbe sa
   plage fixe ; un sélecteur 7 j / 30 j / 90 j qui ne change aucune tuile prête à confusion.
2. **Maintenant** (rafraîchi toutes les 30 s) : en ligne par serveur de jeu (`build` affiché à part, hors total),
   écrits en attente avec l'âge du plus ancien, douane du jour (réservés / places), tickets ouverts.
3. **Activité** : tuiles 1 à 6 (chiffre, écart avec la période précédente, petite courbe), courbe des joueurs
   simultanés par heure, carte de chaleur jour × heure, tableau de cohortes, courbe WAU.
4. **Recrutement** : tuiles 7 à 9 ; entonnoir en barres horizontales (effectif, taux, intervalle), choix de la
   découpe (source de découverte, village, type de personnage) ; source de découverte ; démographie (ADMIN).
5. **Staff** : tuiles 10 et 11, remplissage des séances ; tickets par catégorie, séances des 14 derniers jours.
6. **Économie** (lot D) : masse empilée par village, variation hebdomadaire face à la bande cible, Gini, ryo créé par
   le staff.
7. **Vue Détail** (mêmes droits que le bloc) : indicateurs secondaires du §3.3, dont la courbe de survie, la durée
   des sessions, les sanctions par semaine, les invitations par canal et la santé technique.

Règles d'affichage :
- **Annotations** : chaque publication (audit `manifest.publish`, `release.publish`) apparaît comme une fine ligne
  verticale sur toutes les courbes, pour lire l'effet d'une mise à jour.
- **Pas de faux chiffres ni de bloc « à brancher »** : un bloc dont la collecte n'est pas en service n'est pas
  affiché (principe 7 de PANEL_V2) ; collecte en service mais fenêtre pas encore complète → « Premier chiffre le
  JJ/MM », jamais un zéro.
- **Palette** : une couleur par serveur ou par série, dans cet ordre : pervenche `#728fe3`, corail `#d77361`,
  turquoise `#16a8a9`, glycine `#a67ed4`, menthe `#23ac81`, or `#b78d19`. Ce sont les teintes des accents de salon
  (DA §7) ramenées à OKLCH L 0,665 / C 0,13. Les pastels d'origine (`#9DB4F0`…) échouent au validateur sur la
  surface `#17090c` : trop clairs, trop peu saturés, or pâle et menthe à ΔE 12 (seuil 15). Ceux-ci passent (ΔE ≥ 16,7
  entre voisines, contraste ≥ 3:1) ; glycine et turquoise restent à 6,7 en deutéranopie, d'où légende et étiquettes
  directes obligatoires. Rampe de la surface vers l'or pour les cohortes. Une série seule garde la pervenche. Les composants `StatTile` et `ChartCard` du futur kit du panel (refonte en
  cours d'étude) sont un prérequis du lot B.
- **Droits** : Maintenant et Staff pour tout le staff ; Activité, Recrutement et Économie à partir de MODERATOR ;
  démographie ADMIN et plus ; revenus OWNER (Q6).

---

## 4. L'entonnoir d'acquisition Reborn

Il remplace l'entonnoir « connexion → achat → rachat » de Fuze III. Les étapes suivent les phases de la candidature
([`CANDIDATURE_ET_DOUANE.md`](../gameplay/CANDIDATURE_ET_DOUANE.md) §1.2).

| Étape | Atteinte quand | Événement | Disponible |
|---|---|---|---|
| E0 Arrivée sur le Discord | Le membre rejoint le serveur Discord, avec le code d'invitation utilisé | `discord.arrivee` (bot) | Lot A1 ; **pas de reprise possible** avant |
| E0 bis Téléchargement du launcher | Passage par le lien de téléchargement suivi | `launcher.telechargement` (API) | Lot A1 ; pas de reprise ; **flux seulement** : aucun identifiant n'est gardé, donc aucun lien avec un compte ni place dans une cohorte |
| E1 Compte créé | Premier login Microsoft | `compte.cree` | Reprise depuis `User.createdAt` |
| E2 Discord lié | Liaison obligatoire depuis le launcher 0.3.46 | `compte.discord_lie` | Reprise depuis `discordLinkedAt` |
| E3 Candidature commencée | Premier brouillon serveur | `candidature.brouillon_cree` | Lot A2 ; reprise partielle (brouillons en cours) |
| E4 Candidature envoyée | Premier envoi | `candidature.envoyee` (`numeroEnvoi = 1`) | Lot A2 ; reprise depuis la ligne courante |
| E5 Écrit accepté | HRP et RP validés | `candidature.decision` | Reprise depuis l'audit |
| E6 Douane réservée | Première réservation | `douane.reservee` | Reprise depuis `DouaneReservation` (lignes encore présentes) |
| E7 Présent à la douane | Arrivée dans le hall | `douane.presence` | Dès que le jeu appelle la route |
| E8 Accepté | Phase `ACCEPTE` | `candidature.acceptee` | Reprise depuis `oralPassedAt` et l'audit |
| E9 Première session en jeu | Première session qualifiante | `jeu.connexion` | Lot C |
| E10 Revenu à J7 | Actif sur [J+7, J+13] | dérivé | Lot C |

**Deux lectures, à ne pas mélanger :**

1. **Entonnoir de cohorte** (pour les taux) : on suit les personnes dont le **premier contact** (E0 ou E1, le
   premier des deux) tombe une semaine donnée. E0 n'est relié à un compte qu'à la liaison Discord (E2) ; un compte
   créé avant d'arriver sur le Discord entre par E1. Étapes E1 à E9 : atteintes **dans les 28 jours** suivant le
   premier contact ; une cohorte s'affiche quand ces 28 jours sont passés. E10 se compte sur J+7 à J+13 après la
   première session, quelle que soit sa date : sa case reste hachurée jusqu'à ce que toute la cohorte ait eu ses
   13 jours (sinon une fenêtre de 28 jours coupe la plupart des retours). Un compte qui repasse par « à revoir »
   compte une fois par étape (« a atteint l'étape au moins une fois »).
2. **Flux hebdomadaire** (pour la charge) : nombre d'événements de chaque étape par semaine, toutes cohortes
   confondues.

Pour chaque passage d'étape : taux, **intervalle de Wilson**, délai médian et P90. Les sorties sont montrées à part :
retrait, refus, « à revoir » (boucle), absence à la douane.

**Acquisition** : l'équivalent du « nom d'hôte » de Fuze, c'est la **source**, mesurée de trois façons
complémentaires :
- `discoverySource` déclarée dans la candidature (déjà là) ;
- un **code d'invitation Discord par canal** (bio TikTok, YouTube, chaque créateur), dont le bot compare les
  utilisations à chaque arrivée ;
- un **lien de téléchargement par canal** (`/v1/telecharger?canal=tiktok`, redirection vers le binaire, aucune IP
  gardée).

Le lien entre E0 et E1 se fait quand le compte lie son Discord : la clé Discord pseudonymisée de `discord.arrivee`
est comparée à celle du compte.

---

## 5. Le tableau de cohortes (J0 à J30)

- **Cohorte** : semaine (lundi) de la première session en jeu. Variante : **vague de whitelist**, c'est-à-dire la
  semaine d'acceptation (Q7).
- **Colonnes** : semaines relatives `S_k = [J+7k, J+7k+6]` pour k = 0 à 4. S0 vaut 100 % par construction : la
  colonne n'est pas affichée (l'effectif de la cohorte la remplace). S1 est la rétention J7 du KPI 5, et le KPI 5 doit
  retrouver la moyenne pondérée de S1 sur ses cohortes (la maquette d'origine affichait 41 % pour environ 60 % dans le
  tableau) ; S4 (J+28 à J+34) est la rétention J30. Le tableau couvre donc J7 à J34.
- **Cellule** : `#{u ∈ C : u actif au moins un jour de S_k} / #C`, avec l'effectif entre parenthèses. Cellule vide
  si la fenêtre n'est pas terminée (triangle). Ligne grisée si la cohorte compte moins de 10 joueurs.
- **Pourquoi des semaines et pas des jours** : un serveur RP se joue le soir, souvent deux ou trois fois par semaine.
  Une rétention « au jour J+7 exact » mesure surtout le jour de la semaine. La matrice **quotidienne** J0 à J30 (actif
  le jour exact J+N) existe quand même dans Metabase pour l'exploration.
- **Courbe de survie** : dans la vue Détail, une courbe de Kaplan-Meier. Événement = désengagement (dernière session
  suivie de 14 jours sans session) ; un joueur dont la dernière session date de moins de 14 jours est **censuré**.
  C'est plus juste qu'un pourcentage sur une cohorte encore jeune.
- **Gel** : une ligne de cohorte est figée quand sa fenêtre S4 est terminée. Elle survit ainsi à la purge des données
  brutes (13 mois).

Exemple de requête, sur le schéma d'analyse (§8) :

```sql
-- Rétention S1 (J+7 à J+13) par cohorte hebdomadaire, network
WITH premiere AS (
  SELECT joueur_cle, jour AS j0
  FROM stats.joueur_premiere
  WHERE serveur = '*'
)
SELECT date_trunc('week', p.j0)::date              AS cohorte,
       count(*)                                    AS taille,
       count(*) FILTER (WHERE EXISTS (
         SELECT 1 FROM stats.joueur_jour a
         WHERE a.joueur_cle = p.joueur_cle
           AND a.jour BETWEEN p.j0 + 7 AND p.j0 + 13)) AS retenus_s1
FROM premiere p
WHERE p.j0 + 13 < current_date
GROUP BY 1
ORDER BY 1;
```

---

## 6. Le panel de Fuze III : ce qu'on garde, ce qu'on adapte, ce qu'on écarte

| Élément du panel Fuze III | Verdict | Pourquoi |
|---|---|---|
| Revenus, revenus mensuels | **Écarté** pendant la beta | Pas de boutique (`Purchase` jamais écrit, aucun prestataire de paiement). Plus tard : registre des RBCoins et revenus mensuels, **OWNER seulement** (§11, lot G) |
| Taux d'achat, ARPPDAU | **Écarté** | Métriques de jeu gratuit à très grand volume. Avec quelques centaines de joueurs, c'est un rapport de deux petits nombres qui saute d'un jour à l'autre. Au plus, plus tard : payeurs du mois et revenu moyen par payeur, au mois. Prudence avec les paiements de joueurs de 15 à 17 ans |
| DAU | **Adapté** | Le WAU (KPI 2) et le pic du soir (KPI 1) parlent davantage pour un RP du soir ; le DAU reste en indicateur secondaire |
| Rétention J1 / J7 | **Gardé** (KPI 4 et 5) | J0 = première session en jeu, J7 en fenêtre ; c'est déjà l'indicateur de la beta dans COMMUNICATION §2 |
| Nouveaux joueurs | **Gardé** (KPI 3) | Défini comme première session en jeu, pas création de compte |
| Durée de session | **Gardé** (KPI 6) | Avec fusion des reconnexions et seuil de 2 minutes |
| Acquisition par nom d'hôte | **Écarté** | Sans objet : chez Fuze, c'est l'adresse tapée dans Minecraft ; le launcher Reborn se connecte seul à une adresse fixe. Remplacé par la source (§4) |
| Tableau de cohortes | **Gardé** (§5) | Par semaine ou par vague de whitelist, avec effectifs et Kaplan-Meier |
| Entonnoir connexion → achat → rachat | **Remplacé** | Par l'entonnoir d'acquisition Reborn (§4) |
| Progression des joueurs | **Adapté** | Version RP : rangs, techniques apprises, en indicateur secondaire |
| Démographie | **Adapté** | Agrégats seulement, tranches d'âge, seuil de 5, ADMIN et plus |

**Ce qu'on ne fait pas non plus** : prédiction du désengagement par apprentissage automatique (trop peu d'individus,
et ce serait du profilage de joueurs dont certains sont mineurs) ; classement des membres du staff ; suivi individuel
dans les statistiques (la fiche joueur existe déjà pour ça) ; analytique des clics dans le launcher ou le panel ;
entrepôt de données ou outil de transformation dédié (le volume reste sous quelques millions de lignes par an,
Postgres suffit).

---

## 7. Le schéma d'événements

### 7.1 La table `StatEvenement`

Nom choisi pour ne pas confondre avec les events RP (`ScheduledEvent`, module `events/` vide). Ajout seul : aucune
mise à jour ; les seules suppressions sont la purge à 13 mois et l'effacement d'un compte.

```prisma
model StatEvenement {
  id           BigInt   @id @default(autoincrement())
  type         String              // « domaine.action », registre du §7.3
  at           DateTime            // moment où ça s'est produit, horloge de la source (UTC)
  recuAt       DateTime @default(now())  // réception par l'API
  source       String              // api | jeu | bot | launcher | reprise
  serverId     String?             // identifiant de config, jamais une adresse ; pris de l'en-tête signé pour le jeu
  userId       String?             // compte concerné (null : événement de serveur, arrivée Discord non liée)
  acteurId     String?             // compte staff qui a agi (décision, sanction), sinon null
  personnageRef String?            // « <serverId>:<N°> » tant que les personnages ne sont pas synchronisés dans l'API
  sessionRef   String?             // corrélation des événements d'une même session de jeu
  cleIdem      String   @unique    // clé d'idempotence fournie par la source (UUID)
  schemaV      Int      @default(1)
  props        Json                // propriétés du type, schéma strict et versionné

  @@index([type, at])
  @@index([userId, at])
  @@index([serverId, at])
}
```

Règles d'écriture :
- **Registre fermé** : chaque type a un schéma strict (class-validator ou zod) ; type inconnu ou clé inconnue =
  refus. C'est ce qui empêche de laisser passer un texte libre, une IP ou un e-mail.
- **Jamais dans `props`** : texte libre (tchat, motif rédigé, histoire), IP, e-mail, identifiant Discord en clair
  (on garde une clé HMAC), coordonnées précises.
- **Idempotence** : `cleIdem` unique ; un lot renvoyé après une coupure ne crée pas de doublon.
- **Retard admis** : `at` peut précéder `recuAt` de 7 jours au plus (file d'attente du jeu pendant une coupure de
  l'API) ; au-delà, refus et compteur d'erreur.
- **Pas de blocage** : côté API, l'écriture se fait dans la même transaction que l'action métier quand elle est
  transactionnelle, sinon juste après ; une erreur d'écriture est journalisée et ne fait jamais échouer l'action du
  joueur.
- **Double écriture assumée avec l'audit** : `AuditLog` sert la sécurité (immuable, chaîné, conservé longtemps) ;
  `StatEvenement` sert l'analyse (purgeable, pseudonymisable). Les décisions du staff vont dans les deux.

Volume estimé : moins de 1 000 événements par jour pendant la beta, quelques millions par an à l'échelle d'un
network. Pas de partitionnement nécessaire ; une partition mensuelle pourra rendre la purge moins chère plus tard.

### 7.2 D'où vient chaque événement

| Source | Canal | Authentification | Remarques |
|---|---|---|---|
| **API** | Appel interne `StatsService.emettre()` depuis les services existants (auth, whitelist, douane, tickets, play, staff) | — | Lot A2 |
| **Jeu** | `POST /v1/game/evenements`, lots de 500 au plus toutes les 30 s ; `POST /v1/game/etat` chaque minute ; `POST /v1/game/instantane` chaque nuit | `GameSignatureGuard` (déjà en place) ; `serverId` lu dans l'en-tête signé, jamais dans le corps | **ShinobiCore** émet : il a déjà le client signé et le journal universel. Un second « puits » du journal transmet les types retenus, avec des propriétés structurées passées par l'appelant (on ne lit pas le texte libre du journal). File sur disque pendant une coupure. Guardian reste minimal |
| **Bot** | `POST /v1/staff/evenements` | HMAC existant du pont API ↔ bot | Arrivées, départs, invitation utilisée |
| **Launcher** | `POST /v1/telemetrie` après la fermeture du jeu | JWT ; **refusé côté API sans accord enregistré** | Le mod écrit un résumé local (`perf-session.json`) ; le launcher l'envoie avec ses propres diagnostics. Lot F |

### 7.3 Le registre des types

Colonne « Lot » : A = lot A2 (émission depuis les services, après la candidature v2), sauf les trois types marqués
A1 (`launcher.telechargement`, `discord.arrivee`, `discord.depart`), qui n'attendent pas.

Convention : `domaine.action`, en français, participe passé. `userId` est le compte concerné ; `acteurId` le staff qui
agit.

**Compte et launcher** (source API)

| Type | Émis par | Propriétés | Lot |
|---|---|---|---|
| `compte.cree` | `auth.service` (premier login) | — | A |
| `compte.discord_lie` | module Discord | `discordCle` | A |
| `launcher.connexion` | `auth.service` (connexion, pas les rafraîchissements) | `versionLauncher` | A |
| `launcher.telechargement` | route de redirection | `canal` | A1 |
| `jeu.jeton_emis` | `play.service` | `portee` (`play`, `douane`), `serveurVise`, `versionLauncher` | A |

**Candidature et douane** (source API ; les deux routes du jeu passent déjà par l'API)

| Type | Émis par | Propriétés | Lot |
|---|---|---|---|
| `candidature.brouillon_cree` | whitelist | `formVersion` | A |
| `candidature.envoyee` | whitelist | `numeroEnvoi`, `formVersion`, `source` (découverte), `village`, `typePerso` | A |
| `candidature.retiree` | whitelist | `phase` au moment du retrait | A |
| `candidature.prise_en_charge` / `.relachee` | staff | `acteurId` | A |
| `candidature.decision` | staff | `partie` (`hrp`, `rp`, `oral`), `decision` (`accepte`, `a_revoir`, `refuse`), `acteurId` | A |
| `candidature.acceptee` | whitelist (passage en `ACCEPTE`) | `via` (`douane`, `oral_hors_jeu`, `oral_deja_passe`) | A |
| `candidature.remise_a_zero` | staff | `acteurId` | A |
| `douane.seance_ouverte` / `.seance_close` / `.seance_annulee` | douane | `seanceId`, `capacite`, `places`, `presents`, `absents` | A |
| `douane.reservee` / `.annulee` | douane | `seanceId`, `par` (`candidat`, `staff`, `seance`) | A |
| `douane.presence` | route du jeu `douane/presence` | `seanceId` | A (dès que le jeu l'appelle) |
| `douane.absent` | tâche planifiée (`MISSED`) | `seanceId` | A |
| `douane.decision` | route du jeu `douane/decision` | `seanceId`, `resultat`, `acteurId` | A |

**Tickets** (source API)

| Type | Propriétés | Lot |
|---|---|---|
| `ticket.cree` | `categorie` | A |
| `ticket.message` | `auteur` (`joueur`, `staff`, `systeme`) ; jamais le contenu | A |
| `ticket.statut` | `de`, `vers`, `acteurId` | A |
| `ticket.pris_en_charge` | `acteurId` | A |

**Jeu** (source ShinobiCore, route signée)

| Type | Propriétés | Lot |
|---|---|---|
| `jeu.serveur_demarre` / `jeu.serveur_arrete` | `versionPlugins`, `raison` | C |
| `jeu.connexion` | `sessionRef`, `premiereSurServeur`, `modeStaff` | C |
| `jeu.deconnexion` | `sessionRef`, `dureeS`, `raison` (`quitte`, `expulse`, `delai`, `arret_serveur`), `enKo`, `enDuel`, `modeStaffS` | C |
| `jeu.personnage_cree` | `personnageRef`, `village`, `clan`, `type`, `sexe` | C |
| `jeu.personnage_choisi` | `personnageRef` | C |
| `jeu.grade` | `personnageRef`, `vers` | D |
| `jeu.technique_apprise` | `personnageRef`, `techniqueId`, `rang`, `via` (`parchemin`, `seance`, `staff`) | D |
| `jeu.mort_rp` | `personnageRef`, `cause` (`rpk`, `execution`, `abandon`, `staff`), `avecVerdict` | D |
| `jeu.sanction` | `sanctionRef`, `type` (`avertissement`, `mute`, `expulsion`, `ban`), `dureeS`, `motifCode`, `acteurId` ; le motif rédigé reste au casier | D (avec la synchronisation des sanctions) |
| `jeu.ryo_staff` | `personnageRef`, `montant`, `sens` (`creation`, `retrait`), `acteurId` | D |

**Discord** (source bot)

| Type | Propriétés | Lot |
|---|---|---|
| `discord.arrivee` | `discordCle`, `canal` (libellé de l'invitation) | A1 |
| `discord.depart` | `discordCle`, `joursPresence` | A1 |

**Client** (source launcher, **sur accord seulement**)

| Type | Propriétés | Lot |
|---|---|---|
| `client.lancement_echec` | `etape` (`runtime`, `bibliotheques`, `assets`, `fabric`, `jvm`), `code` | F |
| `client.plantage` | `code` (codes stables de `diagnostics.rs`), `versionLauncher`, `versionMod`, `versionManifest`, `ramAlloueeMo`, `trancheRam` (`4-8 Go`…), `os` | F |
| `client.perf_session` | `fpsP50`, `fpsP5`, `msImageP95`, `dureeS`, `distanceRendu`, `trancheRam` ; un seul événement par session, calculé sur le PC | F |

**Boutique** (source API, plus tard)

| Type | Propriétés | Lot |
|---|---|---|
| `boutique.achat` | `produit`, `montantCentimes`, `devise`, `prestataire`, `statut` | G |
| `rbcoins.mouvement` | `delta`, `motif`, `soldeApres` ; miroir d'un **registre** `RbCoinMouvement` en ajout seul, qui reste la source de vérité | G |

### 7.4 Hors événements : deux flux à part

- **`ServeurEchantillon`** (`serverId`, `at`, `enLigne`, `max`, `tps`, `msptP95`) : une ligne par minute et par
  serveur. Au lot A1, l'API pingue elle-même chaque serveur de la liste (identifiant + hôte, dans le `.env`, jamais
  dans le dépôt) ; au lot C, le battement de ShinobiCore (`/v1/game/etat`) prend le relais et ajoute TPS et MSPT. Le
  ping reste en secours. Attention : le ping peut ne pas compter un staff invisible ; le battement, si.
- **`InstantaneJeu`** (`serverId`, `jour`, `contenu` JSON validé) : un instantané par serveur et par nuit, des
  **agrégats** calculés par le jeu. Économie : masse par village (bourse, compte, trésor), création et destruction
  par motif, vols, alertes de bourse en double, et la **liste des soldes sans identifiant**, mélangée (elle suffit pour
  un Gini et une courbe de Lorenz exacts). Vie RP : personnages actifs, créés, morts RP, KO, hôpital, duels, verdicts
  de RPK, répartition par rang et par clan. Le détail reste dans le journal du jeu.

---

## 8. Les agrégats

### 8.1 Où ils vivent

Un **schéma Postgres `stats`**, séparé des tables métier. C'est le seul que voient la page Pilotage (pour les
chiffres historiques) et Metabase. Il ne contient **aucun `userId`** : la clé de joueur y est
`joueur_cle = HMAC-SHA256(poivre, userId)`, calculée par l'API au moment de l'agrégation, le poivre restant dans le
`.env` de l'API. (Prisma 6 : `multiSchema`, à vérifier selon la version installée ; à défaut, tables créées par une
migration SQL.)

| Table | Contenu | Grain | Conservation |
|---|---|---|---|
| `SessionJeu` (schéma métier) | Sessions reconstruites : compte, serveur, personnage, début, fin, `finEstimee`, raison | Une ligne par session | 13 mois |
| `stats.joueur_jour` | `jour`, `serveur`, `joueur_cle`, `secondes`, `sessions`, `staff` | Une ligne par joueur actif et par jour | 13 mois |
| `stats.joueur_premiere` | `joueur_cle`, `serveur` (`*` = network), `jour` de la première session | Une ligne par joueur et par serveur | Durée de vie du compte |
| `stats.stat_jour` | `jour`, `serveur` (`*` = network), `metrique`, `dimension`, `valeur_dim`, `valeur`, `n`, `def_version` | Format long : un indicateur × une découpe × un jour | Illimitée (agrégats) |
| `stats.stat_cohorte` | `cohorte`, `type` (`semaine`, `vague`), `serveur`, `age_semaines`, `taille`, `actifs`, `gelee` | Triangle de cohortes | Illimitée |
| `stats.serveur_heure` | Pic, médiane et moyenne des joueurs simultanés, TPS moyen | Une ligne par heure et par serveur | Illimitée ; les échantillons à la minute sont gardés 90 jours |
| `stats.instantane_jeu` | Copie des instantanés du jeu, sans la liste des soldes au-delà de 13 mois | Un par serveur et par nuit | Illimitée |

**Tables plutôt que vues matérialisées** : une vue matérialisée se recalcule entièrement à partir des événements ; le
jour où les événements bruts de plus de 13 mois sont purgés, l'historique disparaîtrait. Des tables alimentées par
upsert gardent les agrégats après la purge, encaissent les événements en retard (on recalcule les derniers jours) et
coûtent moins cher. Seul le bloc « Maintenant » interroge directement les tables métier, en direct.

`def_version` : si la définition d'un indicateur change, on incrémente sa version au lieu de mélanger deux définitions
sur une même courbe. Les définitions vivent dans un seul fichier de code (`apps/api/src/stats/definitions.ts`), que ce
document recopie.

### 8.2 Rafraîchissement

| Quand | Quoi |
|---|---|
| Chaque minute | `ServeurEchantillon` (ping, puis battement du jeu) |
| Toutes les 30 s, à la demande | Bloc « Maintenant » (requêtes directes, cache court) |
| Chaque nuit à 5 h 15, heure de Paris (juste après la fin du jour de jeu) | 1. Reconstruction des sessions (fusion, fins estimées) pour J−1, et recalcul de J−2 à J−7 pour les événements en retard. 2. `joueur_jour` et `joueur_premiere`. 3. `stat_jour` pour tous les indicateurs de J−1, recalcul de J−2 à J−7. 4. `stat_cohorte` pour les cohortes des 35 derniers jours ; gel des cohortes mûres. 5. Agrégation horaire des échantillons de plus de 90 jours. 6. Purges (§10.4) |
| Chaque nuit, côté jeu | `POST /v1/game/instantane` par ShinobiCore |

Le calcul est **déterministe** : pour une fenêtre donnée, suppression puis insertion dans une transaction. On peut le
relancer sans risque (commande `pnpm stats:recalculer --depuis AAAA-MM-JJ`).

### 8.3 Qualité des données

Une ligne discrète « Santé des données » sur la page (ADMIN) : part des sessions à fin estimée, retard médian entre
`at` et `recuAt`, événements refusés par type, trous dans les battements (serveur arrêté ou flux cassé ?), date du
dernier calcul nocturne. Les règles de §3.1 ont des **tests unitaires** sur des cas fabriqués : session à cheval sur
minuit et sur 5 h, arrêt brutal sans déconnexion, même joueur sur deux serveurs, cohorte incomplète, reconnexion en
moins de 5 minutes.

---

## 9. L'outil : page du panel et Metabase

### 9.1 Recommandation : les deux, avec des rôles nets

| | Page Pilotage (panel) | Metabase auto-hébergé |
|---|---|---|
| Pour qui | Tout le staff, selon le grade | OWNER et ADMIN (l'utilisateur, profil data science) |
| Pour quoi | Les douze indicateurs, chaque jour, sans réfléchir | Les questions imprévues : « rétention par source × village », Lorenz, matrice quotidienne |
| Données | API → `stats.*` et tables métier pour « Maintenant » | Rôle Postgres **en lecture seule sur le schéma `stats` seulement** |
| Définitions | Dans le code, relues, versionnées | Questions sauvegardées, libres |
| Accès | Connexion Discord et 2FA du panel, droits par grade, choix du serveur, identité Reborn | **Pas exposé sur internet** : n'écoute que sur le VPS, accès par tunnel SSH |

**Pourquoi pas Metabase seul** : il faudrait une deuxième connexion pour le staff, sans les droits par grade ni la
2FA du panel (absentes de la version libre) ; les définitions deviendraient des requêtes que chacun modifie ; et
l'identité visuelle serait perdue.

**Pourquoi pas le panel seul** : chaque question nouvelle demanderait du code et une publication. Pour un profil data
science, une console SQL avec graphiques sur des agrégats propres est le bon outil d'exploration.

**Quand** : Metabase au lot E seulement, quand il y a des données à explorer (un Metabase sur des tables vides ne sert
à rien). Coût : un conteneur Docker de plus (environ 1 à 1,5 Go de RAM, base interne dans une base Postgres
`metabase` à part, jamais H2) ; vérifier la marge du VPS avant. Le même tunnel et le même rôle en lecture seule
permettent aussi un carnet Jupyter ou DBeaver.

### 9.2 Graphiques de la page Pilotage

- **Recharts**, chargé seulement sur cette page (import dynamique de Next.js), pour les courbes et les barres :
  déclaratif, courant avec React, suffisant pour quelques centaines de points, couleurs passées depuis les variables
  CSS du thème.
- **Sans bibliothèque** : petites courbes des tuiles (un composant SVG d'une trentaine de lignes), tableau de cohortes
  (tableau HTML avec fond coloré par cellule), entonnoir (barres en CSS).
- Pas d'ECharts (lourd), pas de Grafana (fait pour la supervision technique ; le TPS et la charge du serveur restent
  chez l'hébergeur).

---

## 10. RGPD (joueurs à partir de 15 ans)

> Cadrage technique, pas un avis juridique. La politique de confidentialité et les mentions légales sont « à faire »
> avant l'ouverture des candidatures (COMMUNICATION §5) : ce chapitre dit ce qu'elles devront couvrir.

### 10.1 Bases légales

| Traitement | Base proposée | Conséquence |
|---|---|---|
| Comptes, candidature, sessions de jeu pour la sécurité et la modération | Exécution du service, intérêt légitime | Information dans la politique de confidentialité |
| Statistiques agrégées calculées côté serveur (sessions, entonnoir, économie) | Intérêt légitime documenté | Droit d'opposition : un réglage `statsOpposition` exclut le compte des agrégats à venir |
| Arrivées et départs sur le Discord, y compris de membres qui ne créeront jamais de compte | Intérêt légitime documenté | Clé HMAC seulement, purgée à 13 mois comme le reste ; une ligne d'information dans le salon règlement du Discord |
| Télémétrie client (FPS, matériel, plantages, échecs de lancement) : lecture d'informations sur le PC | **Consentement préalable** (règle des traceurs, art. 82 de la loi Informatique et Libertés) | Opt-in, désactivé par défaut |
| Revenus, achats (plus tard) | Exécution du contrat, obligations comptables | OWNER seulement |

15 ans est l'âge du consentement numérique en France : un joueur de 15 ans peut consentir seul à la télémétrie. Les
recommandations de la CNIL sur les mineurs s'appliquent quand même : minimisation, pas de profilage, pas
d'interface trompeuse. Une analyse d'impact courte est conseillée (présence de mineurs), une page suffit.

### 10.2 Minimisation et pseudonymisation

- **À l'écran, des agrégats seulement.** Aucune liste nominative dans la page Pilotage ; la fiche joueur existe pour
  le suivi individuel, avec ses propres droits.
- **Seuil de 5** : toute case d'une découpe (âge, pays, sexe, village, clan) sous 5 personnes s'affiche « < 5 ».
- **Tranches d'âge** 15-17 / 18-24 / 25+, jamais l'âge exact ; démographie réservée à ADMIN et plus.
- **Schéma `stats` pseudonymisé** (clé HMAC, aucun `userId`, aucune IP) ; Metabase ne voit que lui.
- **Discord** : identifiants gardés sous forme de clé HMAC dans les événements.
- **Jamais collecté** : texte libre, tchat, voix, IP dans les événements, géolocalisation fine, carte graphique ou
  numéro de série du matériel.

### 10.3 Consentement à la télémétrie (lot F)

- Proposé **une fois**, après la première partie (pas au premier lancement, pour ne pas ajouter une étape à
  l'accueil), avec « Accepter » et « Refuser » de même poids et un lien vers le détail de ce qui est envoyé.
- Deux cases séparées : **plantages** et **performances**.
- Enregistré dans le compte (`telemetrieAccordAt`, `telemetrieAccordVersion`, par case) et **vérifié par l'API** :
  un envoi sans accord est refusé, même si le launcher se trompe.
- Retirable à tout moment dans les réglages du launcher ; le retrait arrête l'envoi, et les données déjà reçues sont
  effacées sur demande ou au plus tard à 90 jours.
- Le bouton « Envoyer le rapport » de l'écran de plantage reste possible sans accord général : c'est un envoi
  ponctuel demandé par le joueur.

### 10.4 Conservation

| Donnée | Durée proposée |
|---|---|
| `StatEvenement` (brut) | 13 mois, puis agrégats seulement |
| `SessionJeu`, `stats.joueur_jour` | 13 mois |
| `stats.joueur_premiere` | Durée de vie du compte |
| Télémétrie client brute | 90 jours |
| `ServeurEchantillon` à la minute | 90 jours, puis à l'heure sans limite |
| Agrégats (`stat_jour`, `stat_cohorte`, `serveur_heure`) | Sans limite (anonymes avec le seuil de 5) |
| `Session` (authentification, avec IP) | **À purger** : 30 jours après expiration, ou IP tronquée ; aujourd'hui jamais purgée |
| `AuditLog` (avec IP) | Immuable ; archivage annuel par segment de la chaîne à prévoir |
| Journal du jeu (tchat compris), `staff-ips.yml` | 90 jours ; **à écrire** dans la politique de confidentialité |
| Voix de la candidature | 30 jours après la décision (déjà en place) |

**Effacement d'un compte** : suppression de ses `StatEvenement`, `SessionJeu`, de ses lignes `joueur_jour` et
`joueur_premiere` (retrouvées par sa clé HMAC) ; les agrégats restent.

### 10.5 Deux corrections préalables

- **Géolocalisation** : la détection de nouveau pays envoie l'IP des joueurs à ip-api.com, **en HTTP non chiffré**
  (`login-anomaly.service.ts`), prestataire hors UE non déclaré. À remplacer par une base locale (fichier GeoLite2 sur
  le VPS), ou à déclarer.
- **Purge des sessions** d'authentification (ci-dessus).

Le registre des traitements reçoit deux entrées : « Statistiques et pilotage » et « Télémétrie du client ».

---

## 11. Plan en lots

Ordre dicté par les fenêtres qui ne se rattrapent pas : l'ouverture du Discord (14/11), des candidatures (28/11) et de
la beta (2/01, COMMUNICATION §2). **Collecter d'abord, afficher ensuite** : une page peut arriver après coup, une
donnée non collectée est perdue.

Les lots qui touchent `apps/admin` et `apps/api/src/whitelist` passent **après** la candidature v2 en cours (ou se
fusionnent avec elle). Le 25/10, cette tâche modifie aussi `auth.service`, `play.*`, `staff.*`, `game.*` et
`app.module.ts` : toute l'émission côté API en dépend, d'où le découpage du lot A. Les lots qui touchent le jeu et
le web suivent les **deux trains** : la route de l'API est publiée d'abord, le plugin ensuite, dans des commits
séparés.

| Lot | Contenu | Touche | Échéance | Taille |
|---|---|---|---|---|
| **0 · Corrections** | Compteur « joueurs whitelistés » (compter tous les rôles à partir de `WHITELISTED`) ; accueil et liste comptés sur les **phases v2** ; retrait de la carte « Revenus boutique » ; remplacement d'ip-api ; purge des sessions d'authentification, **activée seulement après la reprise historique** du lot A2, qui lit `Session` (sinon l'historique des connexions au launcher est perdu) | API, panel | Après la candidature v2 | S |
| **A1 · Ce qui ne se rattrape pas** | Module `apps/api/src/stats/` réduit : table `StatEvenement` et registre, `ServeurEchantillon` avec une **liste** de serveurs, lien de téléchargement suivi, route signée du bot ; bot : invitations par canal, `discord.arrivee` et `.depart` (nouveau fichier ; tant que la route n'est pas déployée, le bot écrit un journal local rejoué ensuite par la reprise). Nouveaux fichiers seulement ; seul contact avec la candidature v2 : l'import du module dans `app.module.ts`, une ligne | API, bot | **Avant le 14/11**, sans attendre la candidature v2 | S |
| **A2 · Émission depuis les services** | Émission depuis auth, whitelist, douane, tickets, play, staff ; script de **reprise historique** (`User`, `AuditLog`, `DouaneReservation`, `Session`), événements marqués `source = reprise`. Ce qui passe entre-temps se reprend depuis l'audit et les tables, sauf les débuts de candidature (E3) et le détail des renvois | API | Juste après le commit de la candidature v2 | M |
| **B · Page Pilotage v1** | Kit `StatTile` / `ChartCard` (avec la refonte du panel) ; calcul nocturne de `stat_jour` ; blocs Maintenant, Recrutement, Staff ; KPI 1 et 7 à 11 ; substitut « launcher actif » pour le KPI 2 | API, panel | **Avant le 28/11** | M |
| **C · Sessions de jeu** | ShinobiCore : client d'événements (file sur disque, lots signés), `jeu.connexion` / `.deconnexion` / `.personnage_*`, battement `/v1/game/etat` ; API : routes, `SessionJeu`, `joueur_jour`, `joueur_premiere` ; KPI 2 à 6 | Jeu, API | **En service au playtest staff (18-24/12)** pour valider le flux, au plus tard le 2/01 | M |
| **D · Rétention, économie, vie RP** | `stat_cohorte`, tableau de cohortes, Kaplan-Meier ; `/v1/game/instantane` (économie, vie RP) ; KPI 12 ; `jeu.mort_rp`, `.grade`, `.technique_apprise`, `.ryo_staff` ; `jeu.sanction` avec le chantier « casier en base » | Jeu, API, panel | Janvier (le J7 de la première cohorte tombe vers le 9/01, le J30 vers le 1/02) | M |
| **E · Exploration** | Metabase (Docker, tunnel SSH), base `metabase`, rôle en lecture seule sur `stats` ; questions sauvegardées : Wilson, Kaplan-Meier, Lorenz, matrice J0-J30 | Infra | Quand le lot C a deux à trois semaines de données | S |
| **F · Télémétrie sur accord** | Écran d'accord et réglages du launcher ; résumé de performance écrit par le mod ; envoi des plantages et des échecs de lancement ; bloc Santé technique | Launcher, mod, API | Après la beta, avec la politique de confidentialité à jour | M |
| **G · Boutique et RBCoins** | Registre `RbCoinMouvement`, webhooks du prestataire vers `Purchase`, revenus mensuels OWNER | API, panel | Avec la boutique (ADR 0006) | M |

### 11.1 Vérifications prévues, lot par lot

- **A1** : rejoindre le Discord de test par une invitation de canal → `discord.arrivee` avec le bon canal, sans
  identifiant Discord en clair ; route coupée → l'arrivée attend dans le journal local et arrive une fois, au retour ;
  couper le serveur de jeu → les échantillons passent à `enLigne = 0` avec le statut hors ligne, sans trou.
- **A2** : créer un compte de test, lier Discord, commencer et envoyer une candidature → quatre événements en base,
  dans l'ordre, avec `cleIdem` distincts ; relancer la reprise historique deux fois → aucun doublon.
- **B** : comparer chaque tuile avec une requête SQL écrite à la main sur la même fenêtre ; vérifier « < 5 », les
  effectifs seuls sous n = 10 et « Premier chiffre le JJ/MM » ; vérifier que le KPI 5 égale la moyenne pondérée de
  la colonne J7–J13 ; un compte HELPER ne voit que Maintenant et Staff.
- **C** : se connecter 10 minutes, se déconnecter, se reconnecter dans les 2 minutes → une seule session fusionnée ;
  arrêter le serveur brutalement → session close au dernier battement, marquée « fin estimée » ; couper l'API pendant
  une session → les événements arrivent au retour, sans doublon.
- **D** : rejouer une semaine de données fabriquées dont la rétention est connue → le tableau la retrouve ; la masse
  de ryo de l'instantané égale `/banque masse` en jeu le même soir.

---

## 12. Méthodes adaptées aux petits effectifs

- **Taux** : intervalle de Wilson à 95 % sur chaque taux (entonnoir, rétention, présence) ; jamais un pourcentage
  sans son effectif.
- **Rétention** : courbes de Kaplan-Meier, qui traitent correctement les joueurs encore actifs (censure à droite) ;
  comparaison de deux cohortes par un test du log-rank, à titre indicatif seulement.
- **Médianes** (durée de session, délais) : intervalle par bootstrap quand on compare deux périodes.
- **Saisonnalité** : comparer au même jour de la semaine précédente, pas à la veille ; un samedi n'est pas un mardi.
- **Network** : attention au paradoxe de Simpson quand un serveur grossit plus vite qu'un autre ; montrer la découpe
  par serveur à côté du total.
- **Inégalité** : Gini et courbe de Lorenz des soldes ; part des 10 % les plus riches.
- **Anomalies économiques** : d'abord des règles simples et lisibles (création de ryo par le staff au-dessus d'un
  seuil, alerte de bourse en double, solde net hors de la bande cible), puis un score robuste (écart à la médiane en
  MAD) sur la création par motif. Pas de modèle tant qu'il n'y a pas des mois d'historique.
- **Effet d'une mise à jour** : lecture avant / après autour de l'annotation de publication, sur des fenêtres de même
  longueur et mêmes jours de la semaine ; c'est une indication, pas une preuve causale.

---

## 13. Questions pour l'utilisateur (avec la recommandation)

| # | Question | Recommandation |
|---|---|---|
| Q1 | Nom de la page : « Pilotage » ou « Indicateurs » ? | **Pilotage** (« Stats » désigne déjà les statistiques des personnages) |
| Q2 | Rétention J7 : en fenêtre [J+7, J+13] ou au jour exact ? | **En fenêtre**, adaptée au rythme du RP ; le jour exact reste dans Metabase |
| Q3 | Les comptes staff comptent-ils comme joueurs ? | **Oui par défaut**, filtre « hors staff », temps en mode staff mesuré à part |
| Q4 | Metabase auto-hébergé, accessible par tunnel SSH ? | **Oui, au lot E**, pas avant d'avoir des données |
| Q5 | Quand proposer la télémétrie ? | **Après la première partie, une seule fois**, deux cases séparées, après la beta |
| Q6 | Qui voit quoi sur la page ? | Maintenant et Staff : tout le staff ; Activité, Recrutement, Économie : MODERATOR et plus ; démographie : ADMIN et plus ; revenus : OWNER |
| Q7 | Cohortes par semaine d'entrée en jeu ou par vague de whitelist ? | **Les deux**, semaine par défaut ; vague = semaine d'acceptation |
| Q8 | Bande d'alerte de l'économie autour de +5 % par semaine ? | **± 3 points**, à resserrer après un mois de beta |
| Q9 | Le lot A1 avant le 14/11 est-il prioritaire sur le reste du panel ? | **Oui** : les arrivées sur le Discord et les téléchargements non suivis à l'ouverture sont perdus pour toujours. A1 ne dépend pas de la candidature v2 |
| Q10 | KPI 10 : « écrits relus sous 48 h » plutôt qu'un délai médian ? | **Oui**, même seuil que le retard du panel ; le délai médian reste affiché dessous |
