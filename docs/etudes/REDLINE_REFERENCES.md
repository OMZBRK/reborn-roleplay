# RedLine : ce qu'on peut en tirer pour Reborn

> **Ce que contient ce document :** la lecture complète du « Game Plan » de **RedLine** (projet RP One Piece sur
> nanos world / UE5, en pré-production), sa comparaison système par système avec Reborn, ce qu'il éclaire dans les
> questions ouvertes du design Faction, ses bonnes pratiques de rédaction, et une liste priorisée de ce qu'on
> reprend. Établi le 2026-10-13.
>
> **Source** : export texte du document « Copie de RedLine - Game Plan » fourni par l'utilisateur (~12 400 lignes),
> **lu en entier**. Le document n'est pas dans le dépôt. On reprend des **concepts et des chiffres**, jamais de texte.
>
> **Contexte Reborn** : [`CHANTIERS.md`](../CHANTIERS.md), [`ETAT_DES_LIEUX.md`](../ETAT_DES_LIEUX.md),
> [`DECISIONS.md`](../pilotage/DECISIONS.md), fiches [`gameplay/`](../gameplay/),
> [`FACTION_PROPOSITION.md`](../gameplay/FACTION_PROPOSITION.md),
> [`GUIDE_STAFF_EN_JEU.md`](../staff/GUIDE_STAFF_EN_JEU.md),
> [`BENCHMARK_MODS_ET_SERVEURS.md`](./BENCHMARK_MODS_ET_SERVEURS.md) (abrégé « benchmark »).
>
> Valeur RP ★ à ★★★ · Effort **F** facile, **M** moyen, **L** lourd.

---

## 1. Le document RedLine en bref

**Vision.** Un serveur RP One Piece **sans PNJ de quête ni scénario** : chaque personnage est un joueur, qui se fixe
ses buts (pirate redouté, sabreur, marchand). Trois valeurs annoncées : **liberté, conséquence, immersion**. Le
document sert de référence centrale à l'équipe de dev (Lua, UE 5.7) et doit évoluer avec le projet.

**Ce que ces valeurs donnent concrètement** : un état de personnage autoritaire côté serveur (KO, porté, prison…),
une économie physique (l'argent est un objet qu'on pose, vole et range), des métiers interdépendants, des
activités illégales qui appellent une réponse de la Marine, et une vraie fabrique d'outils staff (chaque système a sa
commande de configuration en jeu).

**Feuille de route en quatre phases** (chaque ligne : priorité, dépendance, statut fait / en rédaction / pas écrit) :

| Phase | Contenu |
|---|---|
| **1 — Fondations (critique)** | Player ID, inventaire + hotbar, objets (ramasser, poser, poids), équipement (arme, tête), HUD de base, commandes et outils staff, logs — tout marqué fait |
| **2 — Boucle de jeu (important)** | Déplacements avancés, combat bagarreur / sabreur / tireur, KO, états du joueur, interaction E, arbre de compétences, banque, économie, braquage, métiers légaux, activités illégales, lettres, stockage, trésor, faction / équipage, chat de proximité, Den Den Mushi, médecin, journaliste, capture d'îles |
| **3 — Contenu RP (secondaire)** | Création et éditeur de personnage, identité (nom, prime, profil), boutique de vêtements, cosmétiques de compte, voyage en mer et bateaux, Vivre Card, contrôle de territoire, casino, primes, carte, Log Pose, navigateur |
| **4 — Plus tard** | VIP et abonnements, launcher, écran de sélection, journal, système d'événements staff, performances, image de marque |

**À savoir avant de lire la suite :**
- Plusieurs systèmes de la feuille de route **n'ont pas de section** : Vivre Card, casino, contrôle de territoire,
  système de primes et identité (seulement esquissés à travers les avis de recherche, l'interaction « se présenter »
  et la réputation de faction), journal (une ligne dans l'écran de sélection), événements staff (dispersés : séisme,
  annonces, capture d'îles). Ils sont signalés comme tels au §2.
- Le document contient des **doublons et des contradictions** (deux versions du KO, la boutique répétée trois fois,
  les cosmétiques « liés au compte » mais « perdus à la mort du personnage ») : leçon retenue au §4.
- Le moteur (UE5, vocal intégré, physique) n'est pas Minecraft : on garde **les règles**, pas les mécaniques
  techniques.

---

## 2. Les systèmes, un par un

Format : **RedLine** (ce que ça fait, chiffres marquants) · **Reborn** (ce qui existe, avec la fiche) · **À reprendre**
(adapté à Naruto et aux décisions) · valeur · effort.

### 2.1 Player ID, données du personnage, entity binding

- **RedLine** : deux niveaux — un **compte** (lié à l'e-mail et au Discord, un ban oblige à recréer compte et Discord)
  et un **identifiant numérique par personnage** (ex. 82545) qui porte tout : position, PV, endurance, faim,
  inventaire, monnaie, XP, arbre, faction, état. Un seul acteur en jeu par identifiant, sauvegardé à la déconnexion.
  Aucune action ne doit se baser sur le pseudo ou le nom RP.
- **Reborn** : déjà le même modèle — compte Minecraft / Reborn + **UUID de personnage** (`ShinobiCharacter`, un YAML
  par joueur dans `characters/`, sacoche dans `rpbags/<uuid perso>.yml`) ; mais les commandes staff ciblent par nom
  de joueur ou de personnage (`/stats give <personnage>`).
- **À reprendre** : un **identifiant court de personnage** affiché dans la fiche joueur du Poste de garde et accepté
  par les commandes staff (plus fiable qu'un nom RP qui change, utile pour les notes staff §2.5 et les journaux §2.6).
- ★★ · F

### 2.2 États du personnage et mode staff

- **RedLine** : un **état courant unique** par personnage, stocké et modifié **par le serveur seulement** (repos,
  combat, KO, sonné, garde, porté, prison, mode staff), chaque état listant ce qu'il autorise ; transitions écrites
  (KO → porté, n'importe quel état → prison…). Porté : on peut tenter de se libérer (Espace). **Mode staff** :
  caméra libre, le corps reste figé sur place, invisible des joueurs mais visible des autres staffs en mode staff,
  identifiant + nom RP + infos de debug au-dessus des têtes, accès temps réel à l'inventaire, l'état, les logs.
- **Reborn** : les états existent mais sont **répartis** (KO à terre / inconscient, ATA, porté, prise à la gorge,
  scène de mort, méditation…) avec un point de lecture commun partiel (`KoService.isImpaired`, cf.
  [`KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md) §2) ; `/vanish` et « Observer » au Poste de garde.
- **À reprendre** : (1) un **tableau d'états** dans la fiche KO (état → interdit / autorisé), et à terme un service
  d'état unique que tous les plugins interrogent (prépare « prison » pour la police de la Faction) ; (2) en
  `/vanish`, un **calque staff** au-dessus des têtes : personnage, compte, état (KO, ATA, porté), PV.
- ★★ · M (service) / F (calque)

### 2.3 Inventaire, hotbar, équipement

- **RedLine** : 12 cases + 6 de hotbar, **20 kg**, extensibles par la progression ou le VIP ; emplacements **Tête**
  (chapeau, capuche, masque : objets échangeables, à la différence des cosmétiques de compte) et **Arme** ; **l'arme
  équipée est visible sur le corps** (pistolet ou katana à la ceinture) ; une compétence n'est utilisable qu'avec
  l'arme de sa classe ; KO, porté et sonné bloquent l'usage sans déséquiper ; notification en bas à droite
  (manque de place, objet trop lourd).
- **Reborn** : sacoche « inrō » plus riche : 9 cases sur soi, sacs à paliers (jusqu'à 45 cases, poids), objets
  data-driven, cosmétiques en données ([`INVENTAIRE_ET_COSMETIQUES.md`](../gameplay/INVENTAIRE_ET_COSMETIQUES.md)) ;
  rendu sur le corps non fait.
- **À reprendre** : (1) **l'arme et le sac portés visibles** (katana dans le dos, sacoche à la hanche) via la même
  couche de rendu que les cosmétiques ; (2) **techniques de Kenjutsu exigeant une arme tenue** (aujourd'hui rien ne
  l'impose) ; (3) masques et capuches comme **objets de personnage** (volables), distincts des cosmétiques de compte.
- ★★ · M

### 2.4 Objets au sol

- **RedLine** : ramasser avec E (refus et message si sac plein ou trop lourd), jeter en glissant hors de la fenêtre,
  disparition après X minutes (non chiffré).
- **Reborn** : jeter depuis la sacoche existe (clic dehors) ; corps fouillables.
- **À reprendre** : rien de neuf, sauf le **délai de disparition réglable** des objets RP jetés (évite les dépôts
  sauvages, mais laisse le temps de revenir chercher un objet tombé en combat).
- ★ · F

### 2.5 HUD

- **RedLine** : en haut à gauche PV, endurance, faim près du niveau ; endurance qui repart après **5 s sans usage** ;
  faim à 15 % = plus de repas possible + malus de dégâts, à 0 % = ni sprint ni dash, caméra qui tangue ; **boussole
  horizontale** en haut ; argent en bas à droite ; zone de buffs sous la vie.
- **Reborn** : HUD vitals de base **inchangé** (décision 08/10), HUD combat discret, éditeur de HUD, pas d'effets
  « RPG » ; carte en M ; pas de boussole.
- **À reprendre** : une **boussole horizontale facultative** (élément de l'éditeur de HUD) qui porte le marqueur posé
  sur la carte (§2.34) ; une **zone d'effets** sous les vitals (repas, ATA, pilules) avec icône + minuteur.
- ★★ · F à M

### 2.6 Commandes staff

- **RedLine** : toutes ciblent l'**identifiant** (`tp`, `bring`, `freeze`, `mute`, `kill`, `revive`, `heal`, `damage`,
  `invisible`, `invsee`) ; **annonce globale** (bandeau haut d'écran, temporaire) et **annonce à un seul joueur**
  pour une scène (réveil dans un lieu inconnu, convocation en vocal staff) ; **écran noir forcé** (`/blind`) pour enquêtes et
  scènes ; **tenue forcée** (prison, uniforme, event) ; emote forcée ; suppression / remise à zéro d'un personnage
  (les cosmétiques de compte restent) ; **séisme** (annonce + écran qui tremble, branché plus tard sur
  l'ouverture/fermeture de mines et passages nommés) ; **notes staff** sur un personnage (texte, auteur, date,
  visibles du staff, suppression selon droits, gardées si le nom RP change). Chaque commande est journalisée (staff,
  cible, action, heure).
- **Reborn** : Poste de garde (téléporter, ramener, geler, observer, réveiller, ATA), sanctions avec escalade,
  `/vanish`, `/cinematic`, `/meteo`, `/ost`, journal staff ; « casier » local
  ([`GUIDE_STAFF_EN_JEU.md`](../staff/GUIDE_STAFF_EN_JEU.md)). Pas d'annonce staff, pas d'écran noir forcé, pas de
  notes hors sanctions, pas d'`invsee` de la sacoche.
- **À reprendre** : (1) **annonce staff globale et personnelle** (bandeau dans la DA, son) — outil d'animation
  immédiat ; (2) **écran noir forcé** (réutiliser le noir de la scène de mort) ; (3) **notes staff** sur la fiche
  joueur ; (4) **voir la sacoche** d'un joueur en lecture ; (5) **tenue forcée** (tenue de prisonnier, de l'ANBU
  pour une scène) ; (6) un **événement « séisme / glissement de terrain »** qui ouvre ou ferme des passages nommés.
- ★★★ · F (1 à 4), M (5, 6)

### 2.7 Logs

- **RedLine** : tout est journalisé **côté serveur**, horodaté, rattaché à l'identifiant du personnage et
  **requêtable par identifiant** ; catégories : connexion, changements d'état, inventaire (ramassé, posé, équipé,
  utilisé), objets (obtenus, donnés ou supprimés par le staff), combat (dégâts, KO, relève, garde brisée, contre),
  économie (gagné, dépensé), interactions (porter, relever, trésor), staff, système (reset, achats). Règle : **aucune
  action silencieuse**. Les conversations vocales ne sont **jamais** journalisées en texte.
- **Reborn** : journaux par système — staff (`staff-journal.yml`), agressions, RPK (`kill-log.txt`), bibliothèques,
  états de service ; **aucun journal économique ni d'objets** alors que la Faction prévoit trésor, comptes et
  coffres.
- **À reprendre** : un **journal économie + objets** par personnage (ryo gagnés / dépensés et par qui, objets
  donnés ou retirés par le staff, ouvertures de coffres), consultable au Poste de garde. **Prérequis du lot F4** de
  la Faction (trésor, journal en ajout seul) et de toute enquête sur une triche ou une duplication.
- ★★★ · M

### 2.8 Déplacements

- **RedLine** : marche, petite course (Shift, gratuite), **sprint** (double Shift, ~5 s, barre dédiée qui n'apparaît
  qu'en sprint, traînée de fumée), saut, décélération douce, tête qui suit la caméra ; chute ≤ 5 m = roulade sans
  dégâts ; dash au sol et en l'air (sans grande inertie), double saut à débloquer ; tout bloqué en KO / porté /
  sonné / faim nulle.
- **Reborn** : Naruto run, dash, double saut, saut de chakra, voies de mobilité, endurance en ensō ; trois styles de
  course prévus au Sprint 2.
- **À reprendre** : la **jauge qui n'apparaît que pendant l'effort** est déjà notre choix ; la **roulade de
  réception** sous un seuil de chute est une petite touche d'animation à noter pour le catalogue
  ([`ANIMATIONS_BASE.md`](../gameplay/ANIMATIONS_BASE.md)).
- ★ · F

### 2.9 Combat — mode combat, bagarreur et sabreur

- **RedLine** : touche G = mode combat (la barre d'objets devient la barre de sorts, posture et arme en main). M1 :
  **jauge de coups** à gauche du perso (chaque coup la vide, recharge après 5 s sans attaquer, à 0 plus de M1 jusqu'à
  recharge complète), combo gauche / droite / haut / estoc / boucle, déplacement libre pendant le combo. **Clash** :
  deux M1 simultanés = dégâts et recul des deux. **Avantage au premier coup** : la cible ne peut plus enchaîner son
  combo (jauge rose) mais peut fuir. **M1 maintenu 3 s** = estoc brise-garde, sonne 1,5 s. **Garde = réserve de
  points** (ex. 500) qui absorbe les dégâts au lieu d'un pourcentage ; à 0, garde brisée (sonné 1,5 s, jauge rouge),
  recharge après 5 s sans coup ; M2 avec 3 s de recharge. **Parade parfaite** : l'attaquant prend ses propres
  dégâts de M1. M1 en course = **taille en avançant** ; petit combo aérien volontairement court.
- **Reborn** : M1 Tai / Ken, parade 320 ms, guard break 20 ticks, endurance ; le benchmark (lot B) propose déjà
  réarmement, garde frontale / haute-basse, pression de garde, clash, anti-spam.
- **À reprendre** (à verser au lot B) : **garde en réserve de points** (lisible, montre qu'une technique lourde perce
  plus vite), **parade parfaite qui renvoie les dégâts**, **avantage au premier coup** (anti-échange de coups),
  **coup chargé brise-garde**, **taille en course** pour engager. Le passage en « mode combat » par une touche n'est
  pas utile chez nous (barre de techniques déjà séparée).
- ★★★ · F à M

### 2.10 Combat — tireur

- **RedLine** : pistolet à chargeur (capacité selon l'arme), rechargement R, munitions affichées près du perso ; tir
  chargé 3 s = plus de dégâts + saignement + interdiction de dash 3 s ; même garde ; **parade parfaite qui renvoie la
  balle**.
- **Reborn** : projectiles (kunai, shuriken) sans parade spécifique.
- **À reprendre** : la **parade parfaite qui renvoie un kunai** (canon, très lisible) ; un **nombre de projectiles
  portés** limité par la sacoche plutôt qu'un chargeur.
- ★★ · F

### 2.11 KO, Pain RP, agonie

- **RedLine** : à **5 % des PV**, état **Pain RP** (marche lente, ni sprint ni dash, vignette noire, léger tremblement)
  ; un autre joueur peut alors **exécuter en maintenant E** (animations vendues en boutique) ou le **porter sans son
  accord**. À 0 PV : KO **15 min** (interface en français avec minuteur) ; après **10 min**, se relever seul à 10 % des
  PV (−30 % de vitesse et vision limitée à 15 m pendant 1 min) ; après **15 min**, réapparition à l'hôpital le plus
  proche (points posés par le staff) à 50 % des PV, ou **rester KO indéfiniment**. **Déconnexion en KO : état gardé,
  minuteur remis à 15 min.** Un 0 PV direct passe par **20 s d'agonie** où un objet de soin relève à 5 % (Pain RP). Une
  ancienne version (fin du document, « WIP ») disait 7 min, 5 % des PV et une réanimation de 20 s.
- **Reborn** : **en avance** — à terre 45 s (Volonté facultative), inconscient 5 min puis `/hopital` proposé sans
  rapatriement automatique, hôpital à 30 % + ATA, blessures, menu d'interaction, RPK validée par le staff puis jouée,
  scène de mort ([`KO_ET_MORT_RP.md`](../gameplay/KO_ET_MORT_RP.md)). Le KO est réappliqué à la reconnexion
  (`KoListener.onJoin`).
- **À reprendre** : **remettre le compte à rebours à zéro quand un KO se reconnecte** (on ne passe pas son
  inconscience déconnecté ; c'est le problème « déconnexion en pleine scène » du benchmark). Rien d'autre :
  l'exécution libre et la réapparition à 50 % sont contraires à nos décisions (§5.2).
- ★★★ · F

### 2.12 Nourriture, satiété, régénération passive

- **RedLine** (spec V1 très détaillée) : satiété 0-100 %, **−0,025 %/s** (≈ 1 h 07 de 100 à 0), gelée en KO, 80 % au
  réveil ; consommation plus forte en sprint, combat, nage. **Régénération 1 % des PV max/s** seulement entre 95 et
  100 % de satiété, **bloquée 30 s après n'importe quel dégât** (chaque coup relance les 30 s), indicateur seulement si
  la régénération aurait eu lieu. **Manger** prend 3 s, la satiété monte progressivement, on peut bouger ; impossible
  de **commencer** un repas pendant les 30 s ; un coup reçu **met le repas en pause** (rien n'est perdu, reprise
  automatique). Plats = **effets génériques** (buff ou debuff : régénération ×2 / ×3, satiété prolongée −50 %,
  poison…) ; deux effets de types différents se cumulent, **le même type est remplacé par le plus fort** ; effets
  **gelés à la déconnexion**, **perdus au KO**. **Faim** à 0 % : pas de sprint, régénération d'endurance −50 %.
  Toutes les valeurs dans une config ; « regarder l'existant avant de coder ».
- **Reborn** : PV **persistants** hors terrain d'entraînement (décision 06/10) ; repos jusqu'à 30 % ; manger dans une
  zone de repos ajoute 2 min de repos ATA ; pas de faim gérée.
- **À reprendre** : pas la régénération passive (contraire aux PV persistants). Mais **le modèle « effet
  générique »** pour les consommables (pilules de soldat, hyōrōgan, plats d'Ichiraku, poisons) : type, puissance,
  durée, remplacement du même type, gel à la déconnexion, perte au KO ; et la règle **« pas de consommable dans les
  30 s qui suivent un coup »** (anti-potion en combat).
- ★★ · M

### 2.13 États spéciaux : fruits du démon, Logia, Haki, granite marin

- **RedLine** : Logia intangible en permanence sauf face au Haki, au granite marin ou dans l'eau ; tout mangeur de
  fruit coule ; vérification systématique avant chaque dégât (cible intangible ? attaque qui contourne ?).
- **Reborn** : rien d'équivalent (Kamui, Susanoo en cut list).
- **À reprendre** : seulement le principe technique d'une **étiquette « contourne X »** sur les attaques, vérifiée
  avant les dégâts, si un jour une défense absolue arrive (Susanoo, Kamui, sceaux).
- ★ · M (après la beta)

### 2.14 Interaction (touche E)

- **RedLine** : une seule touche, la cible valide la plus proche (joueur > objet > décor). Sur un joueur : **porter**
  (demande à accepter, on se libère avec Espace), **fouiller** (demande à accepter, **lecture seule**), **se
  présenter** (tout le monde est « Inconnu » ; le prénom et le nom ne sont révélés qu'entre les deux joueurs),
  inviter dans sa faction, proposer un duel. **Capuche ou masque équipé = nom masqué** (« Inconnu ») pour les
  braquages et scènes masquées. Menu souris (Alt + clic) : copier l'identifiant, **baisser le volume vocal d'un
  joueur sans pouvoir le couper** (avec un avertissement), signaler.
- **Reborn** : menu d'interaction R (sur les corps), **`/rencontrer`** et noms « Inconnu » tant qu'on ne s'est pas
  présenté (`TabListManager`, `CharacterDisplay`), `/amitier`, `/signaler`.
- **À reprendre** : (1) **le masque qui cache l'identité** (masque ANBU, capuche de déserteur) — s'appuie sur le
  système « connu / inconnu » existant ; (2) **fouille consentie en lecture seule** d'un joueur conscient (contrôle de
  la police, contrôle aux portes en période de tension) ; (3) **porter un joueur conscient sur demande** (blessé qui
  ne peut plus marcher) ; (4) **baisser le volume** d'un joueur (pas de mute) dans les réglages vocaux.
- ★★★ · F à M

### 2.15 Duel

- **RedLine** : sur demande acceptée, 5 s d'invincibilité, PV de départ mémorisés, soin complet ; seuls les deux
  duellistes peuvent se toucher ; fin à **5 % des PV ou 5 min**, 5 s d'invincibilité, puis **retour aux PV d'avant**
  (pas de soin gratuit).
- **Reborn** : la même logique existe **par zone** (terrain d'entraînement : PV mémorisés à l'entrée, rendus à la
  sortie) ; décision : combat libre seulement sur les terrains.
- **À reprendre** : un **duel consenti n'importe où**, qui serait un « terrain d'entraînement portatif » à deux
  (même code, isolation des tiers). ❓ À décider : ça assouplit la règle « combat libre seulement en zone ».
- ★★ · F à M

### 2.16 Échange

- **RedLine** : demande par E, notification, fenêtre commune ; on **ne voit pas l'inventaire de l'autre**, seulement
  ce qu'il pose ; objets et monnaie ; validation des deux puis **compte à rebours de 3 s** annulable.
- **Reborn** : aucun échange sécurisé (on jette au sol, ou le staff donne).
- **À reprendre** tel quel (fenêtre dans la DA, ryo de la bourse inclus) : indispensable dès que la bourse et les
  coffres de Faction existent, et c'est la base des ventes entre joueurs.
- ★★★ · M

### 2.17 Chat de proximité (vocal)

- **RedLine** : trois portées **chuchoter / normal / crier**, une touche qui cycle, petite notification et
  indicateur discret (micro + mode + animation quand on parle), son spatialisé ; KO = parle mais pas de Den Den
  Mushi ; staff : couper localement ou globalement, forcer le mode ; **jamais de journal texte des conversations**.
- **Reborn** : PlasmoVoice dans le modpack (`RebornVoiceAddon`), chat RP en panneau, chuchotement à 4 blocs à terre.
- **À reprendre** : vérifier que PlasmoVoice expose bien les **trois portées** avec un indicateur Reborn discret ;
  ajouter le **volume par joueur** (§2.14). Rappeler dans le guide staff que le vocal n'est pas journalisé.
- ★★ · F

### 2.18 Den Den Mushi (téléphone)

- **RedLine** : un **objet** qui a son propre numéro, ses contacts et ses appels manqués / passés : le prêter ou le
  perdre donne accès à **son** historique ; numéro masquable (streamers) ; appel manuel, répertoire ; **numéros utiles
  créés par le staff et liés à une faction** : appeler la caserne fait sonner **tous les membres**, le premier qui
  décroche prend l'appel (comme un appel de service GTA RP) ; haut-parleur entendu selon le mode vocal ; interdit en
  KO ou menotté.
- **Reborn** : pas de téléphone (et pas dans l'univers). `/aide` alerte déjà les médics et le staff.
- **À reprendre** : **l'appel de service** — « appeler l'hôpital / la police / le bureau des missions » (par un
  oiseau messager, un sceau, ou les **oreillettes radio** que les ninjas utilisent dans l'anime) : tous les membres
  en service de la section reçoivent l'appel, **le premier qui l'accepte le prend** et les autres le voient pris.
  C'est `/aide` généralisé aux sections de la Faction. L'idée d'un **objet de communication qui garde son
  historique** (une radio volée révèle les derniers échanges) est un bon générateur d'enquête.
- ★★★ · F à M

### 2.19 Monnaie

- **RedLine** : la monnaie est un **objet « bourse »** empilable sans limite et sans poids, qui disparaît à 0 ;
  poser au sol demande un montant (le reste reste en main) ; dépôt dans un conteneur avec montant, retrait par
  glisser ou **double-clic pour tout prendre** ; échange par la fenêtre d'échange ; achats PNJ, salaires, artisanat,
  trésors mettent à jour la bourse ; montant en bas à droite du HUD.
- **Reborn** : un solde `ryo` donné par le staff, sans source de gain ; la Faction propose bourse (sur soi, volable)
  et compte (banque) ; benchmark D4 : portefeuille physique.
- **À reprendre** : la **bourse-objet** de RedLine est la forme la plus simple du portefeuille D4 (rangeable dans un
  coffre, posable, volable sur un corps). Voir Q7 au §3.
- ★★ · M

### 2.20 Banque

- **RedLine** : PNJ banquier ou coffre-fort ; **même compte dans toutes les banques** ; monnaie + objets autorisés par
  une **liste blanche / noire** configurable (armes et bombes refusées) avec message et son d'erreur ; **paliers
  payants** — T1 gratuit (2 cases, 50 000), T2 30 000 (6 cases, 100 000), T3 150 000 (8 cases, 1 M), T4 1 M (24 cases,
  illimité) — pour créer des puits d'argent et forcer des choix ; **revenu passif** (ex. 500 / heure de jeu) **qu'il
  faut aller retirer physiquement** à la banque (VIP : plus) ; écran : palier, solde, plafond, cases, coût du palier
  suivant.
- **Reborn** : rien en jeu ; la Faction propose un compte au village (guichet du village, village allié avec 5 %).
- **À reprendre** : (1) **le salaire à retirer au guichet** = notre solde hebdomadaire versée sur le compte, qui
  oblige à passer en ville ; (2) une **liste d'objets refusés** au coffre personnel ; (3) éventuellement un
  **plafond de compte** (puits d'argent) — voir Q7 et Q9 au §3.
- ★★ · M

### 2.21 Métiers et petits boulots

- **RedLine** : **une profession par personnage**, choisie au **niveau 5** (~30 min de jeu) sur un tableau en ville
  (Navigateur, Charpentier, Cuisinier, Médecin, Forgeron), permanente sauf **jeton de changement** acheté en
  boutique ; progression propre, établis dédiés, recettes exclusives. Les **jobs** (mineur, bûcheron, éboueur) sont
  libres, sans progression : ils produisent les ressources que les professions consomment (le mineur vend au
  forgeron, mais rien n'empêche le forgeron de miner).
- **Reborn** : spécialisations par fonction (médic par permission, Corps médical à venir) ; pas de métiers ; les
  missions doivent devenir la source de ryo.
- **À reprendre** : la distinction **spécialisation** (fonction de Faction : médic, armurier, cartographe…) /
  **petits boulots** ouverts à tous. Dans Naruto, les petits boulots **sont les missions de rang D** (§2.22).
  Écarter le choix au niveau (niveau invisible) et le jeton payant.
- ★★ · M

### 2.22 Éboueur (job de départ)

- **RedLine** : tableau d'affichage en ville → mission acceptée → **60 s** pour ramasser 10 déchets **visibles
  seulement par ce joueur** (objets côté client, sans collision, positions tirées dans une liste) → E + barre de 1 s
  par déchet → compteur 1/10… → **retour au tableau** pour toucher la récompense ; **3 par jour** (plus pour les VIP) ;
  échec si temps écoulé ou déconnexion.
- **Reborn** : idées de rang D dans [`MISSIONS.md`](../gameplay/MISSIONS.md) (désherber, promener les chiens Inuzuka,
  retrouver Tora), aucun système.
- **À reprendre** : **c'est le patron exact des missions de rang D automatiques** du bureau des missions : tableau,
  chrono, objectifs visibles du seul joueur (pas de vol d'objectif, pas de bouchon), retour au tableau, plafond
  quotidien, petite récompense en ryo et XP invisible. Contenu quotidien sans animateur, source de ryo contrôlée.
- ★★★ · M

### 2.23 Mineur (et séisme)

- **RedLine** : `/mining` → groupes de filons (nom, minerais), **filons posés à la position du staff**, activables ou
  non ; **rotation hebdomadaire** déclenchée par la commande **séisme** (anciens filons coupés, nouveaux ouverts
  ailleurs) : qui trouve d'abord a l'avantage et **l'information se vend** ; pioche dans la barre, hors combat ; cycle
  de 2 à 5 s qui **se relance seul tant qu'on ne bouge pas** (AFK permis, à ses risques) ; fer, argent, charbon, granite
  marin ; **plafond quotidien** (150 minerais, 200 en VIP) ; **classement réservé au staff** (remis à zéro après une
  mort RP) ; pioche qui s'use.
- **Reborn** : zones de ressources contrôlées par un plugin tiers non versionné (constat terrain,
  [`MISSIONS.md`](../gameplay/MISSIONS.md) §1).
- **À reprendre** (après la beta) : **rotation des gisements par événement**, plafond quotidien, classement staff
  pour surveiller les gros producteurs. Le lien « la zone appartient au village X » est déjà une idée Reborn.
- ★★ · M

### 2.24 Bûcheron

- **RedLine** : zones posées par le staff devant des arbres, butin par arbre (bois commun, dur, d'Adam), hache
  requise (n'importe quelle case), barre de progression, usure.
- **Reborn** : rien.
- **À reprendre** : rien de spécifique au-delà du modèle du mineur.
- ★ · F

### 2.25 Forgeron

- **RedLine** : dépend entièrement du mineur ; **plans** = recette débloquée **pour toujours** (obtenus par trésors,
  events, tournois, captures) ; **schémas** = ingrédient rare **consommé** à chaque fabrication ; armes de rang D à A
  selon le niveau de forgeron (1 à 4) ; **rang S légendaire** (Enma, Yoru…) **ni fabricable ni cassable**, obtenu par
  des systèmes uniques ; durabilité avec icône d'avertissement ; réparations sans plan ni schéma (consomment des
  ressources) ; boucle mineur → forgeron → joueurs → usure → réparation.
- **Reborn** : pas d'artisanat ; parchemins uniques et slots S (une logique de rareté proche).
- **À reprendre** (après la beta) : **plan permanent vs schéma consommable** et des **armes légendaires uniques et
  incassables** — dans Naruto, les **Sept Sabres de la Brume** (Kubikiribōchō, Samehada…) sont l'équivalent parfait,
  à gérer comme les slots S (un exemplaire, transmis par vol ou héritage).
- ★★ · L

### 2.26 Médecin

- **RedLine** : rôle **médecin** donné par commande ; seul un médecin peut **ranimer un KO** avec une trousse (2 usages,
  incantation 10 s visible du seul soigneur, annulée s'il bouge, est frappé ou si la cible n'est plus KO ; 100 % des PV,
  10 s de lenteur) ; **bandages** (pile de 20) : tout le monde, 10 s, +5 %, sur soi ; médecin, 5 s, +20 %, sur soi ou
  la personne visée ; **verrou de combat : 120 s après le dernier coup reçu d'un joueur**, aucun soin possible (les
  dégâts d'environnement ne comptent pas) ; établi médical (recettes coton + lin).
- **Reborn** : paume de soin tenue (interrompue si on bouge ou est frappé), rangs 1 et 2, bandes de gaze (+5 % jusqu'à
  30 %), `/soigner`, armoires, hôpitaux.
- **À reprendre** : (1) **verrou de combat** sur les soins (bandage et paume refusés pendant 2 min après un coup
  d'un joueur — rend le médic de campagne vraiment tactique) ; (2) **bandage plus efficace posé par un médic sur
  autrui**.
- ★★ · F

### 2.27 Cuisinier

- **RedLine** : ingrédients **posés par le staff** dans des lieux précis avec réapparition réglable (le riz dans les
  rizières, une fleur millénaire au sommet d'une montagne : **l'emplacement devient un secret qui se vend**) ;
  recettes communes, rares, légendaires débloquées par des **parchemins de recette** ; caisses de vivres = carburant
  des bateaux ; **repas de maîtrise** : on choisit une technique qui reçoit de l'XP de maîtrise (3 paliers par
  technique, chacun change la technique : dégâts, zone, recharge…).
- **Reborn** : maîtrise 0-100 par l'usage (paliers Novice / Avancé / Maître), séances d'apprentissage.
- **À reprendre** : **les ingrédients rares placés à la main** (herbes médicinales pour les médics, ingrédients des
  pilules du clan Akimichi) dont l'emplacement est une information RP ; et l'idée que **chaque palier de maîtrise
  modifie la technique d'une façon qui lui est propre** (pas seulement +X % de dégâts). Écarter la maîtrise achetée
  par la nourriture.
- ★★ · M

### 2.28 Navigateur et Log Pose

- **RedLine** : le navigateur révèle le brouillard de la carte plus largement, fabrique des **fragments de carte**
  (papier + encre, coût selon la surface) qu'un autre joueur **consomme** pour révéler la zone ; Log Pose réservé
  aux navigateurs, routes définies par le staff ; Log Pose éternel où le navigateur enregistre un lieu (et le vend) ;
  porté au poignet ; direction affichée sur la boussole ; courants plus rapides.
- **Reborn** : carte du monde (M) générée du vrai monde, Konoha seulement, lieux posés par le staff.
- **À reprendre** : la **carte-objet qui révèle un lieu** (rouleau de cartographie : repaire, gisement, bibliothèque
  cachée) et la **boussole qui pointe vers un lieu enregistré** dans un objet (une marque de chakra, un rouleau
  d'invocation inversé) : cadeaux de mission, objets de trafic.
- ★★ · M

### 2.29 Stockage et coffres

- **RedLine** : `/storage` crée un stockage **à la position du staff** ; préréglages 6 / 12 / 18 / 24 cases ; nom et
  description ; **identifiant unique** ; coordonnées figées ; trois accès : **public**, **staff** (invisible des
  joueurs), **privé** (liste de joueurs et de factions ; les non-autorisés ne voient même pas la zone) ; un coffre de
  faction se règle par **rang** ou membre (« capitaine seul », « officiers ») ; **réapprovisionnement automatique**
  (contenu, quantités et cases restaurés à l'identique, minuteur qui **ne démarre qu'une fois le coffre vide**, dépôt
  interdit) ; **éphémère** (se supprime une fois vidé : récompenses d'event, trésor caché) ; onglet Réglages grisé pour
  les joueurs ; popup de quantité et « tout déposer » ; **journal** des dépôts et retraits (perso, identifiant, objet,
  quantité, date à la minute) + créateur et dates ; **`/storagelist`** : recherche, filtres, statistiques par type,
  **téléportation**, ouverture des réglages et du journal depuis n'importe où.
- **Reborn** : la Faction propose des coffres-meubles à trois droits (voir / déposer / retirer), quota de retrait,
  journal, vol (§7) ; les bibliothèques ont déjà un journal et un écran staff.
- **À reprendre** : presque tout, dans le lot F5 — **liste centrale des coffres** au Poste de garde (recherche,
  téléportation, journal), **réapprovisionnement** et **coffre éphémère** pour les events et les caches de mission,
  **coffre invisible** des non-autorisés (cache ANBU).
- ★★★ · M

### 2.30 Boîtes aux lettres et lettres

- **RedLine** : on écrit depuis une boîte aux lettres (destinataire, texte, case « signer ») ; **500 par
  destinataire** (plusieurs destinataires possibles, total affiché avant envoi) ; **anonyme par défaut**, signé
  seulement si coché (prénom et nom RP) ; **transfert** possible, qui garde l'anonymat d'origine ; lettres gardées
  jusqu'à suppression ; **on ne lit qu'à une boîte aux lettres** (pas n'importe où). Usages : menaces anonymes,
  affaires, diplomatie, avis officiels, invitations.
- **Reborn** : chantier « Communication RP (volière, annonces) » au Sprint 3 ; la Faction dit que les missives
  restent du RP.
- **À reprendre** : la **volière** avec ces règles — anonyme par défaut, sceau de signature facultatif, coût par
  destinataire (puits d'argent), lecture seulement à la volière ou au bureau, transfert. Support naturel des
  propositions de trêve (maquette 05), des convocations du Kage et du Bingo Book.
- ★★★ · M

### 2.31 Économie illégale (« MMP », drogue)

- **RedLine** : un **laboratoire attribué par le staff à un équipage** ; à la première prise, **remise en état** (10
  nettoyages + ressources : granite, bois, fer, acide) ; deux machines (10 min puis 30 min, file de 6 lancements
  manuels) ; ingrédient illégal (**feuilles toxiques**) dans des zones qui **empoisonnent** à mesure qu'on y reste ;
  détention = arrestation possible par la Marine ; vente à des **clients PNJ fixes** (on apprend leurs places, la
  Marine aussi) à **capacité partagée** (4 à 10, puis 30 min de recharge) ; **la faction qui tient le territoire est
  prévenue d'une transaction, sans le lieu exact** ; consommation : +50 % de vitesse 15 s puis contrecoup ; réglages
  d'accès par identifiant ou faction.
- **Reborn** : aucune économie illégale.
- **À reprendre** (après la beta, pour les organisations indépendantes) : la **contrebande** sans drogue — herbes
  toxiques ou pilules interdites, **receleurs fixes à capacité partagée**, **alerte au village qui tient la zone sans
  position** (la police enquête), repaire attribué par le staff et à remettre en état. Les **pilules de combat à
  contrecoup** (pilules Akimichi) reprennent le modèle +effet / contrecoup.
- ★★★ · L

### 2.32 Classes, arbre de compétences, XP

- **RedLine** : classe choisie au **niveau 3** (bagarreur, sabreur, tireur) ; points de compétence à chaque niveau,
  **partagés entre les onglets** (classe, Haki, agilité, fruit, spécial) ; sous-classes et Haki obtenus par **tirage
  payant** (Haki royal seulement en boutique) ; onglet **« Spécial » lié à un grade** (attaque réservée à l'amiral) ;
  rareté S à D des compétences ; XP passive, **plafond configurable sur 24 h** (ex. 500), multiplicateurs (VIP,
  events) ; commandes staff (ajouter, retirer, fixer XP et niveau) ; **classement de progression réservé au staff**.
- **Reborn** : 6 stats, apprentissage libre par parchemins, XP et niveau invisibles, techniques réservées à des
  fonctions (ANBU), plafond quotidien de l'XP d'entraînement (120 / jour).
- **À reprendre** : le **classement de progression pour le staff** au Poste de garde (niveaux, points, techniques
  apprises : repérer un farm ou un oubli) ; un **plafond global d'XP par 24 h** en plus du plafond d'entraînement.
  Tout le reste est écarté (§5.2).
- ★★ · F

### 2.33 Deck de techniques

- **RedLine** : bibliothèque des sorts connus par catégorie ; **2 decks** (barres) par défaut, changement par touche
  (7) qui met **1 s de recharge sur toute la barre** ; favoris rangés en catégories personnalisées (« Étourdissements »,
  « Mobilité ») ; **mode rapide ouvrable en marchant ou en combat** : on peut bouger, réorganiser, mais **pas lancer**
  tant qu'il est ouvert ; un sort déposé dans la barre a 1 s de recharge.
- **Reborn** : 5 techniques liées ; benchmark B8 propose un loadout limité avec presets.
- **À reprendre** : la forme concrète de B8 — **presets de techniques**, changement en combat **payé par une
  recharge globale courte**, écran qui n'immobilise pas mais interdit de lancer.
- ★★★ · F à M

### 2.34 Chasse au trésor

- **RedLine** : pelle à **3 usages**, **30 %** de réussite par fouille ; points de fouille **par zone** (nombre, types,
  renouvellement ; le système refuse s'il manque de sol valide) ou **posés à la main** (events) ; coffre de tier 1 / 2
  / 3 (85 / 10 / 5 %) qui **apparaît et doit être porté à la main** (pas dans l'inventaire, **lâché si l'on est frappé**
  ou si l'on change d'objet, traversable pour ne rien bloquer, disparaît après 2 h) jusqu'à un **point d'ouverture**
  (PNJ ou bloc) ; tirage en **trois couches** (type de coffre → rareté → objet d'une réserve gérée par le staff) ;
  **objets uniques** retirés de la réserve une fois obtenus ; roue de tirage purement visuelle ; relances payantes
  (tier 3, 5 max, pas sur un légendaire) ; emplacements trahis par des **indices visuels** au sol.
- **Reborn** : tirage des bibliothèques ; rouleaux placés par le staff en events ; slots S.
- **À reprendre** (après la beta) : **le coffre porté à la main qu'on lâche si on est frappé** (poursuites, escortes,
  embuscades : excellent pour les missions de convoi) ; **objets uniques retirés de la réserve** ; **indices au
  sol**. Écarter les relances payantes.
- ★★ · M

### 2.35 Carte

- **RedLine** : touche M ; **brouillard** levé en explorant ; fragments du navigateur ; **marqueur personnel** qui
  apparaît sur la boussole (pas de minicarte) ; **légendes posées par le staff** (bar, onsen, dojo, forge, port…) ;
  **zones survolables** avec une info (zone de pêche, **territoire de l'équipage X**, ressource rare) ; zones révélées
  par un objet-carte.
- **Reborn** : carte M générée du monde, lieux posés par le staff (`/carte set`), Konoha seulement.
- **À reprendre** : **marqueur personnel + boussole**, **zones survolables** (territoire d'un village ou d'une
  organisation, terrains d'entraînement, zones de repos), **zones révélées par un rouleau**. Le brouillard est à
  discuter (un ninja connaît son pays ; le brouillard aurait du sens pour les pays étrangers).
- ★★ · M

### 2.36 Faction et équipage

- **RedLine** : **créées par le staff** (pas par les joueurs) avec nom, **tag de 3 lettres**, type (pirates,
  restaurant, Marine, gouvernement) ; affichage de la **réputation de faction** (points gagnés en remportant des
  guerres de territoire), du nombre de territoires, de la date et des membres ; **zones de faction** (bâtiments,
  portes, coffres, zones restreintes) avec permissions réglées par le staff ; **chef nommé par le staff** ; le chef
  exclut, crée des **rangs** et donne des permissions par rang (telle porte, tel coffre) mais **ne crée pas de
  zones** ; tir allié activable par chacun ; ouverture par Échap > onglet Faction.
- **Reborn** : la proposition Faction est **nettement plus complète** (sections, fonctions, quotas, trésor, banque,
  coffres, réputation, diplomatie, Bingo Book).
- **À reprendre** : (1) **portes et zones de faction** à accès réglé (quartier ANBU, réserve du palais, poste de
  police) en plus des coffres ; (2) une **renommée de faction** distincte de la réputation personnelle (Q11) ; (3) un
  accès par l'**Échap** en plus de la touche (Q16) ; (4) le **tag court** (KON, SUN…) dans la tablist et les journaux.
- ★★ · M

### 2.37 Caractéristiques

- **RedLine** : bonus **automatiques** par palier de niveau (poids porté, PV, dégâts, cases, vitesse) et **bonus de
  race** (homme-poisson : nage, respiration), dans un arbre visuel **sans choix** (gris tant que verrouillé).
- **Reborn** : bonus passifs de grade (PV, chakra, endurance, +1 à +3 aux stats) ; question n° 5 en attente (les
  garder ou non) ; niveau invisible.
- **À reprendre** : si les bonus de grade restent, les présenter **en mots** dans la fiche shinobi (« le grade de
  Chūnin te rend plus endurant ») ; pas d'arbre chiffré (chiffres cachés, décision 29/09).
- ★ · F

### 2.38 Boutique en argent réel, VIP, tirages, remises à zéro

- **RedLine** : abonnement **VIP** (boost d'XP, file prioritaire, message de connexion, relances incluses) et **VIP+**
  (serveur de combat privé 24/7, moins d'usure à la mort, skins exclusifs, activités hors serveur, récompenses
  mensuelles, boîte de familier) ; **cosmétiques liés au compte**, partagés entre personnages, signalés par une
  **petite icône boutique** ; **tirages** (sous-classes, coffres légendaires) avec points de tirage achetés ;
  **remise à zéro** de classe et de points. Ailleurs : VIP = plus de missions, plus de minerai, plus de personnages.
- **Reborn** : RBCoins pour les cosmétiques, **aucune conversion** avec le ryo (ADR 0006) ; jetons de réinitialisation
  liés au compte ; exécutions vendables.
- **À reprendre** : seulement l'**icône discrète** qui distingue un cosmétique de compte (RBCoins) d'un objet de
  personnage (gagné en jeu). Tout l'avantage de jeu payant est écarté (§5.2).
- ★ · F

### 2.39 Bateaux et charpentier

- **RedLine** (spec la plus longue) : le bateau est une **base mobile** ; voile baissée = navigue, voile levée = ancré
  (plus d'ancre séparée) ; le bateau garde son cap si le barreur part ; joueurs libres sur le pont ; un non-navigateur
  subit des **pertes de contrôle** à rattraper par une touche à marteler ; **provisions = carburant** (vitesse réduite
  sans) ; inventaire persistant lié à l'identifiant du bateau, **conservé après un naufrage** ; coffres additionnels,
  modules, accélérateur ; propriété et permissions ; vol possible voile baissée (sans transfert de propriété) ;
  naufrage visible puis **30 min d'indisponibilité qui survit aux redémarrages** ; réparation par un charpentier
  joueur **nettement moins chère** que par le PNJ ; canons, harpons, pavillon éditable (32 calques) ; liste
  anti-duplication (double paiement, double coffre, double apparition).
- **Reborn** : rien (pas de mer jouable).
- **À reprendre** : pas le système. Deux principes : **le service rendu par un joueur coûte nettement moins que le
  même service PNJ** (valorise les métiers : médic contre hôpital PNJ si un jour il y en a) ; la **liste
  anti-duplication** à écrire pour tout objet persistant (coffres de Faction, trésor).
- ★ · L

### 2.40 Capture d'îles

- **RedLine** : **lancée à la main par le staff** (`/capture start`) avec île, mode, nombre d'équipes (2 à 4), taille
  (5 à 10 selon l'île), **frais d'entrée** et préparation ; annonce à tout le serveur (30 min avant) ; **seul le chef
  inscrit son équipe**, **pas d'alliance**, une seule équipe Marine ; camps assignés et bouton **Prêt** (non prêt =
  disqualifié, sortir du camp = forfait) ; **île verrouillée** (mur invisible) ; modes **Deathmatch** (1 KO = 1 point,
  chef = 10, 100 points ou 30 min), **capture du drapeau** (2 vies, porteur qui ne combat pas, drapeaux visibles à
  travers les murs), **Couronne** (porteur visible de l'ennemi, son KO fait perdre) ; **KO normal inchangé** ;
  déconnexion = sorti ; avertissement aux intrus ; récompenses (XP, argent, classement mensuel, vivres gratuits sur
  l'île, **propriété affichée sur la carte**, drapeau sur l'île, apparition de bateaux) ; pénalités (frais perdus,
  délai avant de se réinscrire) ; menu avec état des équipes.
- **Reborn** : Faction §9.3 — conflit territorial programmé **après la beta** (modèle Solve, captures à horaires).
- **À reprendre** (après la beta) : le **déroulé complet** comme base du conflit territorial — lancement staff,
  annonce, inscription par le Kage ou le chef, camps et « Prêt », zone fermée, modes, intrus avertis, récompenses
  visibles (territoire sur la carte, bannière du village). ❓ À décider : le KO y est-il le vrai KO (blessures
  persistantes) ou celui d'un terrain d'entraînement ?
- ★★★ · L

### 2.41 Boutique en jeu (vêtements)

- **RedLine** : zone boutique, vêtements payés en monnaie de jeu, enregistrés sur l'identifiant (non échangeables),
  perdus à la mort du personnage ; conditions (niveau, VIP). Contradiction avec la boutique en argent réel (§2.38).
- **Reborn** : échoppe (ryo), tenues d'institution débloquées par fonction ou grade (Faction §3.1).
- **À reprendre** : rien de neuf ; garder la règle claire « gagné en jeu = lié au personnage, payé en RBCoins = lié au
  compte ».
- ★ · —

### 2.42 Création de personnage

- **RedLine** : parcours linéaire — race (humain, homme-poisson, mink), apparence, identité (prénom, nom, genre, âge),
  **île de départ** (plusieurs définies par le staff), **cinématique d'introduction** de l'île.
- **Reborn** : création en scène en 8 étapes, plus aboutie ([`PERSONNAGES.md`](../gameplay/PERSONNAGES.md)).
- **À reprendre** : une **courte cinématique d'arrivée** au village choisi après « Oui, c'est moi » (`/cinematic`
  existe), et un **kit de départ** (bandes de gaze, quelques ryo, cf. §2.50).
- ★★ · M

### 2.43 Éditeur de personnage

- **RedLine** : options **par race** ; corps et mâchoire réglés par un **triangle** (mince / large / musclé) ; couleur
  d'yeux libre, **modèle et taille de l'iris** ; sourcils ; curseurs (nez, front, bouche) ; **cheveux en deux parties
  combinables** (avant et arrière), **queue de cheval déplaçable** ; physique des cheveux ; validation « non
  modifiable ensuite ».
- **Reborn** : skins 2D composés, 35 coiffures, couleur libre, palettes de clan.
- **À reprendre** : **frange et arrière séparés** (deux calques combinables dans le compositeur : beaucoup plus de
  coiffures pour le même travail) et **plusieurs formes d'iris** (utile aux dōjutsu).
- ★★ · M

### 2.44 Sélection de personnage et journal

- **RedLine** : un seul personnage par défaut (plus pour le staff et les VIP) ; bouton **Journal** : actualités du
  serveur, notes de version, **primes en cours** ; réseaux, réglages ; personnage posé face à l'horizon.
- **Reborn** : sélection au bord de l'étang ; notes de version dans le launcher.
- **À reprendre** : un **journal RP** consultable depuis le menu (gazette du village, avis de recherche publics du
  Bingo Book, annonces du Kage) — c'est aussi le canal des annonces de Faction.
- ★★ · M

### 2.45 Journaliste, appareil photo, avis de recherche

- **RedLine** : appareil acheté en boutique, **5 photos**, vue subjective avec zoom ; **station de développement**
  (papier + encre) : la photo devient un **objet** (montrable, échangeable, rangeable), copies au coût croissant ;
  appareil plein = jetable. Boucle RP : la Marine commande à un journaliste la photo d'un pirate, la meilleure sert à
  l'**avis de recherche** fait à une **station Marine à accès réglé** (nom ou alias, mort ou vif / vivant seulement,
  montant) ; l'affiche est un objet que le staff peut aussi placarder.
- **Reborn** : mode photo et galerie (client) ; Faction §8.3 : « plus tard, l'avis de recherche en objet ».
- **À reprendre** : **l'avis de recherche-objet** fabriqué au poste de police ou de l'ANBU (portrait, nom, menace,
  « vivant seulement », prime) et **la photo-objet comme preuve** (enquêtes, espionnage). Le portrait peut venir du
  rendu du personnage réel (comme la fiche shinobi) plutôt que d'un appareil.
- ★★★ · M

### 2.46 Launcher et candidatures

- **RedLine** : connexion, liste blanche, candidature écrite (histoire, objectifs) ; le staff accepte ou refuse avec
  un **commentaire visible**, un **délai avant nouvelle candidature** ou un **blocage** ; boutons réseaux, notes,
  réglages, jouer.
- **Reborn** : déjà fait et plus complet (whitelist, tickets, pont Discord, `revise`).
- **À reprendre** : vérifier qu'on a le **délai avant nouvelle candidature** et le **blocage** côté panel.
- ★ · F

### 2.47 Braquages

- **RedLine** : accessibles à partir du **niveau 10** ; `/robbery config` à la position de l'admin : nom, type,
  rayon, **joueurs requis**, **Marines requis**, objets requis, durée, **recharge** (ex. 96 h), table de butin
  (garanti, aléatoire, unique), alerte Marine oui / non. Types : classique (barre + fouille, rester dans la zone),
  **crochetage** (mini-jeu, outil consommé ou non, relié à la porte ou au coffre proche), banque (long, plusieurs
  joueurs, objets), stockage (ouvre un coffre), code. **Alerte à la Marine seulement** (des témoins signalent le
  braquage, avec son nom). **Refus si pas assez de Marines dans le rayon**. Classement `/robbery top`, journal
  complet (début, fin, échec, butin, alerte, recharge).
- **Reborn** : vol de parchemins (fouille immobile, interrompue si l'on bouge ou est frappé, alertes, journal), vol
  prévu sur les coffres de Faction.
- **À reprendre** : des **points de vol configurés** (entrepôt marchand, coffre de saisies, réserve d'un clan) avec
  **effectif minimum de forces de l'ordre connectées et proches** pour pouvoir lancer (pas de braquage sans
  opposition possible), alerte à la police / l'ANBU du village, **outil requis** (crochetage au senbon, sceau à
  briser), recharge longue, butin réglé, journal. Le grade minimum remplace le niveau.
- ★★★ · M

### 2.48 Log Pose (redite)

- Section dupliquée de §2.28 (routes staff, Log Pose éternel, poignet, boussole). Rien de plus.

### 2.49 KO « WIP » (ancienne version)

- Version précédente du KO : pas de perte d'usure à la mort, relève seule après **7 min** à 5 % des PV (−30 % de
  vitesse et vision à 15 blocs pendant 6 min), hôpital à 15 min, réanimation par trousse en **20 s** immobile. Laissée
  dans le document sans être barrée : exemple de ce qu'il ne faut pas faire (§4).

### 2.50 Exploration (fin du document, tronquée)

- **RedLine** : apparition sur l'île choisie à la création, **objets de départ** (bandages, monnaie) ; interaction E
  avec les portes et les établis.
- **À reprendre** : le **kit de départ** (bandes de gaze, quelques ryo, bandeau du village).
- ★ · F

### 2.51 Systèmes annoncés mais non écrits

| Système | Ce qu'on en sait | Équivalent Reborn |
|---|---|---|
| Vivre Card | Titre seulement | — (une **marque de chakra** suivie par un objet serait l'équivalent, cf. §2.28) |
| Contrôle de territoire | Seulement à travers la réputation de faction, la carte et les alertes de contrebande | Faction §9.3, après la beta |
| Casino | Titre seulement | Tanzaku-gai (ville de jeu canon) : en ryo uniquement, jamais en RBCoins ; hors beta |
| Primes / identité | Avis de recherche, « se présenter », masque | Bingo Book (Faction §8.3), `/rencontrer` |
| Journal | Une ligne dans la sélection | §2.44 |
| Événements staff | Séisme, annonces, capture | §2.6, §2.40 |

---

## 3. Pour le design Faction (questions du §16 de `FACTION_PROPOSITION.md`)

RedLine traite la faction plus simplement que nous, mais ses systèmes voisins (monnaie, banque, stockage, lettres,
capture d'îles, braquages, avis de recherche) éclairent la plupart des questions. Question par question :

1. **Signature visuelle (koma, jinmaku)** — Rien de visuel exploitable (maquettes Figma non jointes). Seule idée :
   l'**éditeur de pavillon** par calques, transposable plus tard en **emblème éditable des organisations
   indépendantes** (les villages ont leurs armoiries fixes). Ne change pas la réponse.
2. **Sections, Racine** — RedLine rattache aux factions des **zones** (bâtiments, portes, coffres) et des **numéros
   utiles** (appeler l'hôpital fait sonner tous ses membres). Ça plaide pour des sections qui ont chacune **des lieux
   à accès réglé** et un **appel de service** (§2.18). Les stockages « invisibles des non-autorisés » confirment
   l'idée d'une section scellée : une Racine peut exister **sans être visible** de ceux qui n'en sont pas.
3. **Fonctions, Sannin** — RedLine : chef nommé par le staff, rangs créés par le chef avec des permissions par rang,
   compétence « Spécial » réservée à un grade (l'amiral). Confirme notre « la fonction donne des droits » et les
   techniques réservées à une fonction. Rien sur un titre honorifique.
4. **Quotas et délais** — Rien (seule règle de rareté : une seule équipe Marine par capture). Pas d'éclairage.
5. **Double signature** — Pas de double signature, mais la règle « tout est journalisé, requêtable, aucune action
   silencieuse » (§2.7) : la double signature n'a de sens que si le journal des promotions est complet et visible du
   staff.
6. **Sanctions RP, blâme** — Les **notes staff** de RedLine (texte, auteur, date, visibles d'un public restreint,
   suppression selon les droits, attachées à l'identifiant) sont exactement le format du **blâme** : une ligne datée
   et signée, visible de la seule hiérarchie. Oui au blâme, avec auteur et date affichés.
7. **Bourse et compte, fouille plafonnée** — RedLine sépare nettement les deux : l'argent sur soi est un **objet**
   (posé, volé, rangé dans une planque, partagé après un braquage), la banque est **sûre**, accessible dans **toutes**
   les banques, et refuse certains objets. Ça conforte la séparation bourse / compte. Deux compléments possibles :
   (a) la **bourse-objet** (§2.19), qui rend le vol et la planque naturels ; (b) un **plafond de compte** qui
   s'agrandit contre paiement (puits d'argent). Pour la fouille, RedLine distingue la **fouille d'un joueur conscient
   (consentie, lecture seule)** du pillage d'un corps : le plafond de 5 000 ryo par scène sur un corps à terre reste
   cohérent.
8. **Mort RP : bourse et compte** — Chez RedLine, la remise à zéro d'un personnage efface inventaire et monnaie, seuls
   les cosmétiques de compte survivent ; l'argent sur soi est physique, donc **reste là où il tombe**. Ça éclaire :
   **la bourse reste sur le corps** (fouillable pendant les 15 min où il reste au sol, puis perdue) ; le **compte
   n'est pas transmis automatiquement** (héritage seulement par une décision staff ou un testament RP), sinon la mort
   RP perd son poids.
9. **Trésor, solde** — RedLine verse un **revenu horaire** qu'il faut **aller retirer à la banque** (raison de venir
   en ville) et multiplie les **puits d'argent** (paliers de banque, 500 par lettre, frais d'inscription aux captures,
   réparations). Pour nous : solde **sur le compte, retirée au guichet**, réservée aux actifs (oui) ; et prévoir les
   puits **dès F4** : frais de missive, frais de guichet allié (déjà 5 %), frais d'inscription aux conflits, achats
   d'équipement de section. Les 30 % des missions au trésor restent la bonne source.
10. **Un Genin voit-il le solde en chiffres ?** — RedLine affiche les chiffres partout, mais grise les réglages pour
    ceux qui n'y ont pas droit. Pas d'argument contre les **mots** ; garder les mots (cohérent avec les stats sans
    chiffres).
11. **Réputation** — RedLine n'a pas de réputation personnelle, mais une **réputation de faction** faite de points
    gagnés en **remportant des guerres de territoire**. Suggestion : garder notre réputation personnelle (−1 000 /
    +1 000, en mots) **et** ajouter une **renommée du village** (gagnée aux conflits et aux missions S), affichée sur
    l'Accueil, qui pourra peser plus tard dans les captures.
12. **Diplomatie, guerre** — Dans les captures RedLine, **pas d'alliance** autorisée et une seule équipe par camp
    officiel. Suggestion : pendant un conflit programmé, **chaque village combat seul**, même allié (l'alliance joue
    hors de l'arène). Les **missives** (§2.30) sont le bon support des propositions de trêve (maquette 05).
13. **Désertion : compte gelé ou saisi** — Chez RedLine le compte est **personnel**, pas à la faction. Avec un
    guichet **par village**, un déserteur **ne peut de toute façon plus l'atteindre** (Hostile → refus de banque) :
    le compte est **gelé de fait** ; la **saisie au profit du trésor** devient une décision du Kage validée par le
    staff, pas un automatisme.
14. **Organisation indépendante pour la beta** — Chez RedLine, les équipages pirates sont des factions **créées par le
    staff** au même titre que la Marine, avec un **repaire attribué** (laboratoire à remettre en état) et une
    **économie illégale** propre. Suggestion : la prévoir dans le **modèle de données dès F1** (type « organisation »),
    mais son contenu (repaire, contrebande) après la beta. Nom : éviter « Akatsuki » (identité visuelle).
15. **Mérite** — RedLine ne dépense rien : réputation de faction et caractéristiques sont des **compteurs de
    prestige** qui débloquent automatiquement. Ça soutient l'**option A** (prestige, seuil des fonctions, ordre de la
    file).
16. **Touche J** — RedLine ouvre la faction par **Échap > onglet Faction**. Garder J **et** ajouter une entrée
    « Faction » dans l'Échap Tokonoma (découvrable sans connaître la touche).
17. **Bureau des missions** — RedLine a un **tableau d'affichage automatique** (jobs répétables : chrono, objectifs
    visibles du seul joueur, 3 par jour) **et** des événements lancés par le staff. Suggestion : **les deux** — le
    rang D automatique au tableau (§2.22), les rangs C et plus tenus par les joueurs du bureau (modèle Solve) avec le
    staff pour les rangs A et S.
18. **Ordre de réalisation** — La feuille de route RedLine met **journaux et outils staff en phase 1, avant
    l'économie**. Suggestion : intégrer le **journal économie + objets** (§2.7) au lot **F1** (ou en tout début de F4),
    garder F1 → F4 pour la beta, puis F5 (coffres, avec la liste centrale de §2.29), F6.

---

## 4. Ce que RedLine fait bien dans la rédaction de ses specs

| Pratique | Chez RedLine | Pour Reborn |
|---|---|---|
| **Feuille de route avec dépendances** | Chaque système : priorité, « dépend de », statut (fait / en rédaction / non écrit) | Ajouter une colonne « dépend de » aux tableaux de [`CHANTIERS.md`](../CHANTIERS.md) et aux lots des propositions |
| **Plan type d'une spec** | Vision → règles → **liens avec chaque autre système** (« Link with HUD / Logs / State… ») → règles serveur → réglages staff → écran requis → journaux → objectifs | Notre §10 « liens avec le reste » de la Faction va dans ce sens ; en faire un **gabarit** pour toutes les propositions |
| **« Valeurs V1 pour commencer » + tout configurable** | Le système de satiété finit par la liste des valeurs de départ et la liste de **tout ce qui doit être réglable** | Ajouter un bloc « Valeurs de départ » à chaque proposition (nos fiches ont déjà le tableau `config.yml`, mais après coup) |
| **« Regarde l'existant avant de coder »** | Consigne explicite : réutiliser vie, dégâts, KO, buffs, sauvegarde ; pas de système en double | Déjà notre règle ; l'écrire dans le gabarit |
| **Exemples chiffrés et cas limites** | « Il reste 4 s de recharge, il reçoit un coup → retour à 30 s » ; repas en cours + coup = pause, pas annulation ; déconnexion pendant un état (KO gardé, effets gelés, mission échouée) | Toujours traiter **déconnexion**, **coup reçu pendant une action** et **mort RP** pour chaque nouvelle mécanique |
| **Textes d'interface en français dans la spec** | Phrases exactes à afficher, avec leur sens pour le dev | Écrire les textes des écrans dans la proposition (on le fait en partie dans les maquettes) |
| **Autorité serveur et anti-duplication** | État modifié par le serveur seul ; liste de ce qui ne doit jamais arriver (double paiement, double coffre, double apparition, contournement des droits) | Ajouter une **liste anti-duplication** à toute spec qui touche des objets ou de l'argent (trésor, coffres, échange) |
| **Identifiant stable, jamais le nom** | Toute commande, tout journal, toute permission par identifiant | Identifiant court de personnage (§2.1) |
| **Journaux** | Côté serveur, horodatés, requêtables, **aucune action silencieuse** ; **pas de journal texte du vocal** | Écrire cette règle dans la fiche des journaux du Poste de garde |
| **Outil staff livré avec chaque système** | `/mining`, `/storage`, `/robbery config`, `/capture start` : création **à la position du staff**, liste centrale avec recherche, statistiques, téléportation | Notre Poste de garde et les écrans staff des bibliothèques suivent déjà ce modèle : le garder pour coffres, missions, conflits |
| **Journal de modification de la spec** | « Modifications exactes par rapport à la version précédente » (bateaux) | Nos propositions gagneraient un court « ce qui a changé » quand elles sont révisées |
| **Avertissement sur les images** | Les visuels d'illustration sont signalés comme concepts, le dev reste libre | Nos maquettes sont des cibles validées : préciser quand une image n'est **qu'une idée** |

**Ce qu'il ne faut pas reproduire** : deux versions contradictoires du même système laissées côte à côte (KO 10 min
contre 7 min), une section recopiée trois fois (boutique), des « X minutes à définir » sans responsable, une règle
contraire entre deux sections (cosmétiques de compte perdus à la mort), des systèmes annoncés sans section. Notre
journal des décisions (une décision remplacée est barrée et renvoie à la nouvelle) évite précisément ça : le garder.

---

## 5. À reprendre, à écarter

### 5.1 Tableau priorisé

**P1** = utile à la beta et branché sur un chantier en cours (Faction, Poste de garde, combat) · **P2** = beta si le
temps le permet ou Sprint 3 · **P3** = après la beta.

| Prio | Idée | Origine RedLine | Où chez Reborn | Valeur | Effort |
|---|---|---|---|---|---|
| P1 | **Journal économie + objets** par personnage (ryo, dons staff, coffres), requêtable | Logs §2.7 | ShinobiCore + Poste de garde (Journal) ; prérequis Faction F4 | ★★★ | M |
| P1 | **Annonce staff** globale et à un seul joueur ; **écran noir forcé** | Commandes staff §2.6 | Poste de garde + `reborn-hud` | ★★★ | F |
| P1 | **Notes staff** sur un personnage (auteur, date, droits de suppression) | Player tags §2.6 | Fiche joueur du Poste de garde | ★★★ | F |
| P1 | **KO reconnecté = compte à rebours remis à zéro** | KO §2.11 | `ko/KoListener.onJoin` | ★★★ | F |
| P1 | **Coffres configurables** : liste centrale (recherche, téléportation, journal), invisibles des non-autorisés, réapprovisionnés, éphémères | Stockage §2.29 | Faction §7, lot F5 | ★★★ | M |
| P1 | **Fenêtre d'échange** (inventaire adverse caché, ryo inclus, validation + 3 s) | Échange §2.16 | Sacoche + bourse | ★★★ | M |
| P1 | **Appel de service** : tous les membres en service reçoivent, le premier qui accepte le prend | Den Den Mushi §2.18 | `/aide` généralisé aux sections (hôpital, police, bureau) | ★★★ | F à M |
| P1 | **Missions de rang D au tableau** (chrono, objectifs visibles du seul joueur, retour au tableau, 3 / jour, échec si déconnexion) | Éboueur §2.22 | Bureau des missions (décision n° 13), [`MISSIONS.md`](../gameplay/MISSIONS.md) | ★★★ | M |
| P1 | **Garde en réserve de points**, parade parfaite qui renvoie les dégâts (et les kunai), avantage au premier coup, coup chargé brise-garde, taille en course | Combat §2.9-2.10 | ShinobiCombat, benchmark lot B | ★★★ | F à M |
| P1 | **Presets de techniques**, changement en combat contre une recharge globale de 1 s | Deck §2.33 | Benchmark B8 | ★★★ | F à M |
| P1 | **Masque / capuche qui cache l'identité** tant qu'il est porté | Interaction §2.14 | Système connu / inconnu (`/rencontrer`, `TabListManager`) | ★★★ | F à M |
| P2 | **Volière** : anonyme par défaut, sceau facultatif, coût par destinataire, lecture sur place, transfert | Lettres §2.30 | Communication RP (Sprint 3), diplomatie | ★★★ | M |
| P2 | **Points de vol configurés** + effectif minimum de forces de l'ordre + alerte police + outil requis + recharge | Braquages §2.47 | Section police, coffres, `VolService` | ★★★ | M |
| P2 | **Avis de recherche-objet** (portrait, menace, « vivant seulement », prime) ; photo-objet comme preuve | Journaliste §2.45 | Bingo Book (Faction §8.3) | ★★★ | M |
| P2 | **Fouille consentie en lecture seule** d'un joueur conscient ; porter un blessé sur demande | Interaction §2.14 | Menu d'interaction R | ★★ | F |
| P2 | **Verrou de combat** sur les soins (2 min après un coup d'un joueur) ; bandage plus efficace posé par un médic | Médecin §2.26 | `medic/` | ★★ | F |
| P2 | **Bourse-objet** (ryo en sac, montant à poser, rangeable, volable) | Monnaie §2.19 | Faction §6.2, benchmark D4 | ★★ | M |
| P2 | **Effets de consommables génériques** (type, durée, même type remplacé, gel à la déconnexion, perte au KO) ; pas de consommable 30 s après un coup | Satiété §2.12 | Pilules, plats des zones de repos | ★★ | M |
| P2 | **Arme et sac visibles sur le corps** ; Kenjutsu exige une arme tenue | Inventaire §2.3 | Couche de rendu des cosmétiques | ★★ | M |
| P2 | **Volume vocal par joueur** (sans mute) ; trois portées visibles | Chat de proximité §2.17 | PlasmoVoice, réglages | ★★ | F |
| P2 | **Classement de progression staff** ; plafond global d'XP par 24 h | XP §2.32 | Poste de garde, `XpService` | ★★ | F |
| P2 | **Identifiant court de personnage** au Poste de garde et dans les commandes | Player ID §2.1 | Fiche joueur | ★★ | F |
| P2 | **Calque staff** au-dessus des têtes en `/vanish` | Mode staff §2.2 | `/vanish` | ★★ | F à M |
| P2 | **Boussole facultative + marqueur personnel**, zones survolables sur la carte (territoires) | HUD §2.5, carte §2.35 | Carte M, éditeur de HUD | ★★ | M |
| P2 | **Duel consenti** n'importe où (PV rendus, tiers exclus) ❓ | Duel §2.15 | Logique des terrains d'entraînement | ★★ | F à M |
| P2 | **Journal RP** au menu (gazette, avis de recherche publics, annonces du Kage) | Sélection §2.44 | Menu principal / Échap | ★★ | M |
| P2 | **Frange + arrière** de coiffure combinables, formes d'iris | Éditeur §2.43 | Compositeur de skins | ★★ | M |
| P2 | **Cinématique d'arrivée** et **kit de départ** | Création §2.42, §2.50 | `/cinematic`, création | ★★ | F à M |
| P3 | **Conflit territorial** sur le modèle de la capture d'îles (staff, annonce, Kage inscrit, camps, Prêt, zone fermée, modes, carte) ❓ KO réel ou d'entraînement | Capture §2.40 | Faction §9.3 | ★★★ | L |
| P3 | **Contrebande** : herbes toxiques en zone dangereuse, receleurs fixes à capacité partagée, alerte au village sans position, repaire à remettre en état | MMP §2.31 | Organisations indépendantes | ★★★ | L |
| P3 | **Événement séisme** (passages nommés ouverts / fermés) et **rotation des gisements**, plafond quotidien | Séisme §2.6, mineur §2.23 | Zones de ressources | ★★ | M |
| P3 | **Coffre porté à la main, lâché si frappé** ; objets uniques retirés de la réserve ; indices au sol | Trésor §2.34 | Missions de convoi, reliques | ★★ | M |
| P3 | **Plans permanents / schémas consommables** ; armes légendaires uniques (Sept Sabres) gérées comme les slots S | Forgeron §2.25 | Artisanat (à concevoir) | ★★ | L |
| P3 | **Ingrédients rares placés à la main** ; paliers de maîtrise qui changent la technique | Cuisinier §2.27 | Médical, apprentissage | ★★ | M |
| P3 | **Carte-objet qui révèle un lieu**, objet qui pointe vers un lieu enregistré | Navigateur §2.28 | Carte M | ★★ | M |
| P3 | **Service joueur nettement moins cher que le service PNJ** ; liste anti-duplication pour tout objet persistant | Bateaux §2.39 | Règle de design | ★ | F |

### 5.2 À écarter (contraire aux décisions Reborn ou hors univers)

| Idée RedLine | Pourquoi on l'écarte |
|---|---|
| **Classes choisies au niveau 3**, points de compétence par niveau, arbre à dépenser | Apprentissage libre par parchemins, aucune stat ne ferme une porte (23/09) ; niveau invisible (10/10) |
| **Tirages payants** (sous-classes, Haki, relances de coffre), Haki royal en boutique, points de tirage | RBCoins = cosmétiques seulement, aucune conversion (ADR 0006) ; pas de hasard payant sur la puissance |
| **Avantages VIP de jeu** : boost d'XP, plus de missions ou de minerai, serveur de combat privé, moins d'usure, plus de personnages | Même raison : rien de payant qui donne un avantage en jeu |
| **Exécution libre** d'un joueur blessé (E maintenu) | Toute mort passe par une **RPK validée par le staff** puis jouée par le demandeur (10/10) |
| **Relève seule à 10 %, hôpital à 50 %** | Plancher 30 %, ATA, soin médical tenu (06/10 et 10/10) |
| **Régénération passive de 1 %/s** liée à la satiété | PV persistants hors terrain d'entraînement (06/10) |
| **XP de maîtrise achetée par la nourriture** | La maîtrise vient de l'usage et des séances (08/10) |
| **Drogue consommable** telle quelle | Hors ton du serveur ; on garde la contrebande et les pilules canon à contrecoup |
| **Revenu passif horaire** pour tout le monde | Inflation ; l'argent ne naît que des missions et du staff (Faction §6.3) |
| **Bateaux, Log Pose, fruits du démon, Logia, granite marin, nage** | Hors univers ; pas de mer jouable |
| **Niveau et XP affichés, classement de joueurs public** | Niveau et XP invisibles (10/10) ; seul le staff voit un classement |
| **Barre d'objets remplacée par un « mode combat » sur une touche** | Barre de techniques déjà séparée ; HUD combat discret (08/10) |
| **Morphologie en triangle, physique des cheveux** | Skins 2D Minecraft ; pas de modèle déformable |
| **Brouillard total de la carte** à la création | À discuter seulement pour les pays étrangers (un ninja connaît son pays) |
