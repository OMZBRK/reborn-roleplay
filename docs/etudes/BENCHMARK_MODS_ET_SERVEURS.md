# Benchmark : NydoMod, Ninshu Origins, serveurs RP de référence ⇄ Reborn

> **Ce que contient ce document :** la comparaison, domaine par domaine, entre ce que possèdent deux mods Naruto
> (NydoMod 5.5 et Ninshu Origins 2.1.8), les serveurs RP de référence (Solve, FSC, Ketto, Zenkai) et Reborn ; ce
> qui est **réellement pratique en RP** ; et une liste priorisée de ce qu'on récupère. Établi le 2026-10-12.
>
> **Les détails sont dans les annexes** [`benchmark-2026-10/`](./benchmark-2026-10/) (un rapport par système, avec
> classes, chiffres et tableau d'idées). Ce document-ci est la synthèse : lisez-le d'abord.

---

## 1. Sources et limites

| Source | Ce que c'est | Comment on l'a étudiée | Annexe |
|---|---|---|---|
| **NydoMod 5.5** | Mod Forge 1.20.1 (Yeyo, Daniel), ~4 800 classes, très orienté solo/RPG | Décompilation complète (Vineflower), lecture des données JSON, vidéo de présentation 5.5 | [combat](./benchmark-2026-10/nydo_combat.md) · [progression et monde](./benchmark-2026-10/nydo_progression_monde.md) · [yeux et Susanoo](./benchmark-2026-10/nydo_dojutsu.md) · [bijū](./benchmark-2026-10/nydo_biju.md) · [moteur client et modèles](./benchmark-2026-10/nydo_moteur_assets.md) |
| **Ninshu Origins 2.1.8** | Mod Forge 1.20.1 (SaberArt), ~2 700 classes, multijoueur (escouades, villages) | Décompilation, différentiel avec l'étude 2.1.7 ([archive](../archives/etudes/ETUDE_NINSHU_ORIGINS.md)) | [ninshu_218](./benchmark-2026-10/ninshu_218.md) |
| **Solve, FSC, Ketto, Zenkai** | Serveurs Naruto RP francophones | Recherche web (sites, wiki Solve complet, launchers), sources citées | [serveurs_rp](./benchmark-2026-10/serveurs_rp.md) |
| **Reborn** | Notre état au 12/10 | Docs + code du dépôt | [reborn_inventaire](./benchmark-2026-10/reborn_inventaire.md) |

**À savoir avant de lire :**
- **Aucun des quatre serveurs n'est sur Minecraft.** Solve, FSC et Ketto tournent sur Garry's Mod, Zenkai sur
  nanos world (Unreal Engine 5). Leur combat ne se transpose pas, mais **leurs règles RP oui** (tickets, MJ, porte
  de sortie, quotas). Solve est le seul vraiment documenté (wiki public complet). Aucun avis de joueur exploitable
  n'a été trouvé : « ce qui marche » est déduit de ce que ces serveurs ont rendu obligatoire.
- **Les deux mods sont « All Rights Reserved ».** On reprend des **concepts et des chiffres d'équilibrage**,
  jamais du code ni des assets. Les Susanoo de Nydo viennent d'un prestataire tiers et une partie de ses animations
  de combat est sous licence : rien n'est réutilisable tel quel.
- **Les mods sont faits pour le solo** (Nydo surtout) : progression jamais partagée, IA, rang calculé au niveau.
  Chaque idée est donc passée au filtre « serveur RP multijoueur encadré par un staff » et aux décisions déjà
  prises ([`DECISIONS.md`](../pilotage/DECISIONS.md), résumé dans l'inventaire Reborn, section « Contraintes »).
- **Statuts Reborn** : ✅ en prod · 🧪 sur le serveur de test · 🛠️ partiel · 💡 prévu · ❌ absent.

---

## 2. Le comparatif, domaine par domaine

### 2.1 Combat au corps à corps

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Autorité réseau | Serveur ; entrées numérotées, périmées à 700 ms, plafonds par type (14 coups/s), rembobinage ≤ 8 ticks | Serveur | — (GMod) | 🧪 serveur autoritaire, **sans** anti-rejeu ni plafonds |
| Combo | Routes L/R lues sur les 6 derniers coups **qui ont touché** ; clic droit = garde tant que 2 L n'ont pas touché | Poing / uppercut / pied (2/2/4 dégâts) | — | 🧪 combo M1 linéaire 4 coups, fenêtre 1 200 ms |
| Parade | Active 5 ticks, réarmement 12 ticks, seulement pendant la phase active adverse, sonne 40 ticks | Parade parfaite au sabre 6 ticks | — | 🧪 parade 320 ms, sans réarmement |
| Garde | Frontale seulement ; **pression de garde** : qui martèle est repoussé et perd de l'endurance | **Haute / basse** (accroupi) ; garde brisée = 45-70 ticks + lenteur | — | 🧪 garde + guard break (20 ticks) |
| Clash | Clash de **jutsu** en QTE (3 duels seulement) | Même coup à ≤ 8 ticks → choc, gel 20 ticks | — | ❌ |
| Prises / finishers | Finishers de clan par route (6 s) | Prises selon le clan, finisher par derrière (≤ 35 % PV) | — | ❌ ; exécutions sans animation |
| Esquive | 2 charges, esquive parfaite → contre | Kawarimi | — | 🧪 esquive, dash, **kawarimi** |
| Hitbox | Phases montée / actif / récupération, zones arc/cône/ligne, multiplicateur PvP/PvE par coup | Ticks fixes par coup | — | 🧪 dégâts au clic |
| Lock-on / aérien | 28-30 blocs ; juggle aérien | — | — | ❌ |
| Règles de combat RP | — | — | Interaction obligatoire avant d'attaquer, pas de jutsu de zone/rang X en ouverture (Solve) | Décision : combat libre seulement en zone d'entraînement |

**Lecture.** Le moteur de combat de Reborn existe mais il est **plat**. Les deux mods montrent une série de
**règles faciles** qui donnent de la lisibilité et de la profondeur sans effet « RPG » : parade avec réarmement,
garde frontale, pression de garde, anti-spam, garde haute/basse, clash de coups identiques, multiplicateur PvP.
Les **phases d'action** (montée / actif / récupération) sont le vrai socle structurant, à faire une fois.

### 2.2 Techniques, signes et préparation

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Déclenchement | Signes : 4-8 selon le rang, 2-12 ticks chacun, **plus rapides avec la maîtrise** | Barre scrollable (catégorie ↔ technique) | SWEPs GMod | 🧪 incantation à mudras, cadence fixe 5 ticks |
| Préparation / limite | **MAX JUTSU** : 3 presets, 2→6 disciplines selon le niveau, 4 techniques max par discipline, roue radiale | — | **Slots par village** pour les « subjutsus » (Solve) | 🧪 5 techniques liées ; aucune limite documentée |
| Apprentissage | Parchemin : mémoriser puis reproduire, 3 vies, perdu en cas d'échec ; maîtres uniques | Items « release » par arbre | Suivi par un MJ (Solve, FSC) | 🧪 parchemins-objets, bibliothèques, séances, slots S — **plus riche que les mods** |
| Contenu | Des centaines de techniques | 53 classes de techniques | — | 🛠️ 11 techniques faites à la main sur 215 entrées |
| Statut chakra | **Tenketsu Hyūga** : 7 points, chaque point fermé = régén −1/7 et coût +12 %, immunité 220 ticks | Chakra bloqué (Poing souple) | — | ❌ (Byakugan visuel 🧪) |

**Lecture.** Notre apprentissage est meilleur que celui des mods. Ce qui manque : **la cadence des signes liée à
la maîtrise** (et l'interruption si l'on est frappé), une **limite de techniques préparées** (un loadout, très RP :
on choisit ce qu'on emporte en mission), et les **tenketsu** comme statut serveur pour le Poing souple.

### 2.3 Dōjutsu, yeux et genjutsu

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| L'œil | **Objet unique traçable** (identifiant, côté, intégrité, registre anti-duplication) ; orbites NORMAL / vide | Œil gauche / droit séparés dans les données | **Dōjutsu volables** par ticket ou en zone sans loi (Solve) | ❌ (dōjutsu = mode de clan) |
| Extraction | Sur soi, sur un **cadavre** (yeux gardés 2 min), sur un **vivant entravé** ; duel de timing serveur | Arrachage en QTE | Ticket staff (Solve) | ❌ ; corps au sol 15 min + menu R 🧪 |
| Cécité | **Demi-écran noir, monde en fil de fer** ; usure du Mangekyō > 72 % → cécité 45 s à 10 min | Bandage d'œil | — | ❌ |
| Greffe / chirurgie | Chirurgie de l'Éternel : mini-jeu 3 phases, PNJ chirurgien, 58-88 % de réussite | Implant transactionnel | — | ❌ (rangs médicaux 🧪) |
| Pouvoirs par œil | Chaque technique exige l'œil G, D, l'un ou les deux ; œil volé = chakra ×2-3 | — | — | ❌ |
| Éveil | Automatique (mort d'un proche Uchiha en vue) | Seuils chiffrés (kills, temps actif, stat) | Héréditaire, sur candidature de clan | ❌ |
| Susanoo | 6 phases, 2,4 → 53 blocs, 500 chakra/phase, 41 s max | Cage → complet | — | ❌ (cut list post-beta) |
| Genjutsu | Tsukuyomi = cinématique 10 s, Kotoamatsukami = interdit d'attaquer 30 s | Amnésie (oubli des techniques) | `/roll` à niveau égal ; 2 usages en WinRP → RPK (Solve) | 🧪 effets + **Kai** ; **hors beta** (décision 23/09) |

**Lecture.** Le **vol d'yeux** est le meilleur générateur de RP du lot (Solve l'a d'ailleurs). Il se branche
naturellement sur ce que Reborn a déjà : corps au sol, menu R, RPK validée par le staff, rangs médicaux. Idée
**nouvelle**, absente des deux mods : le **bandage posé par un médic**.

### 2.4 Bijū et jinchūriki

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Lien | **Confiance −1 000 → 1 000, 6 paliers**, qui évolue par le jeu : prêt de chakra (demander / contenir / forcer), pactes, envies du bijū, **mémoire des trahisons** et dette de réparation | XP, maîtrise, lien (`JinBond`), rage | Lore seulement | 🧪 ShinobiTail : 4 jauges **réglées par le MJ**, rage, corruption |
| Dialogue | 207 répliques contextuelles, souvenirs | — | — | 🧪 `/tail speak` (MJ), murmures |
| Monde intérieur | Dimension + épreuve en 3 manches | — | — | 🧪 faux corps, céder / résister, Union |
| Scellement | **Rituel coopératif** : table, 1 réceptacle + 2-4 sceaux, 60 s, bijū ≤ 10 % PV et enchaîné ; extraction sur hôte entravé | — | — | ❌ (bind MJ) |
| Possession | Écran noir, paupières, chute de caméra ; **IA pilote le corps ~2 min** ; chaînes pour l'arrêter | Rage berserk | — | ❌ ; sauvetage au KO 🧪 |
| Manteaux / formes | 1-3 queues (coque, queues procédurales), forme 4 queues (rig 78 os), Bijūdama | Mode Baryon, manteaux | — | 🧪 échelle + aura ; Kurama en **cut list post-beta** |

**Lecture.** ShinobiTail a déjà le versant « MJ ». Le meilleur rapport valeur/effort est le **lien jouable** en
pure logique serveur (prêt de chakra, mémoires, envies, répliques en YAML), avec la main du MJ intacte. La
possession pilotée par une IA est **dangereuse en RP** (elle tuerait des joueurs) : à remplacer par l'écran noir
côté joueur et une issue confiée au MJ.

### 2.5 KO, mort RP et médical

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| À terre | — | 800 ticks, mort par hémorragie **créditée à qui a mis à terre** | **Pain RP** : immobile, porté sur le dos, soigné (Solve) | 🧪 cycle complet à terre → inconscient → hôpital → ATA, blessures, corps |
| Mort RP | — | Reset optionnel par gamerule | **Ticket + vidéo + porte de sortie obligatoire**, effectifs plafonnés, zones RPK (Solve) | 🧪 demande au staff, validation, exécution par le demandeur, scène de mort |
| Soins | Lit médical | Lit qui accélère la guérison ×4 ; **soin interrompu = remis à zéro** | Médecins indispensables | 🧪 `/soigner`, `/paume`, infirmerie |

**Lecture.** Reborn est **en avance** ici. Deux idées à reprendre : **la porte de sortie** (Solve : la cible se voit
proposer une issue ; la refuser, c'est accepter le risque) comme champ de la demande de RPK, et **le soin
interrompu qui repart de zéro** (donne du poids aux escortes).

### 2.6 Missions, PNJ et outils d'animation

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Moteur | ~105 missions D→S, 6 types d'objectifs (tuer, livrer, lieu, escorte, parler, épreuve), prérequis, chaînage | Quêtes, **caution de 100 ryo**, 4 missions actives, **missions d'escouade partagées** | **Bureau des missions tenu par les joueurs** : D→S, déblocage 7D/7C/5B/3A, missions de Kage obligatoires, missions commanditées par des joueurs, pass saisonnier (Solve) | ❌ (idées dans [`MISSIONS.md`](../gameplay/MISSIONS.md)) |
| Création | **PNJ, dialogues, missions, boutiques éditables en jeu par un admin** | PNJ qui apparaissent seuls (Jiraiya, Orochimaru, Zabuza) avec cooldown de région | Animateurs, « feuilles sélectes », personnages d'event | ❌ |
| Épreuves | Clochettes de Kakashi (90 s, approcher de dos à < 2,8 blocs) | Entraînement à rythme (fenêtres 3/7 ticks) | Examen Genin (Zenkai) | 🧪 mini-jeux de séance |
| Tutoriel / guide | Tutoriel 12 étapes validées par le serveur ; Codex ~40 entrées | **Guide en jeu de 78 pages** avec statut par page | Wiki public (Solve) | ❌ (wiki web) |
| Marchand rare | **Kabuto**, marchand d'ADN itinérant (apparition 0,1-1,8 %) | — | — | ❌ |

**Lecture.** C'est **le plus gros manque de Reborn** et ce que les serveurs RP ont tous. Le modèle de Solve (un
bureau tenu par des joueurs, avec des rangs et des missions commanditées) donne du contenu quotidien sans
animateur permanent ; le moteur de Nydo et la caution/escouade de Ninshu en donnent la mécanique.

### 2.7 Réputation, politique et factions

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Réputation | Par village −1 000 → +1 000 : agresser −25 (recherché 60 s), tuer −150, gardes hostiles à −100, services coupés à −40 ; missions de Kage C100 → S650 | Idem + **exil** ; barème attaque / meurtre / œil / désertion | Bingo Book, Nukenin (Solve) | ❌ |
| Diplomatie | Akatsuki recrute les mal vus (≤ 10) | **Matrice de relations entre villages** (guerre, alliance) | Alliances officielles par ticket, zones diplomatiques sans PvP, conseils (Solve) ; politique évolutive (Zenkai) | ❌ ; **design Faction en attente** |
| Rareté | — | Clan principal : technique ultime réservée | **Quotas** : 6 Jōnin par village dont 2 sans clan, 3 Sannin qui ne combattent pas ; délais minimum entre grades (Solve) | 🧪 9 grades donnés en RP, sans quotas |
| Territoire | — | — | **Captures de drapeaux à horaires fixes** (Solve) | ❓ contrôle de zone observé en jeu (plugin tiers non versionné) |

**Lecture.** Matière directe pour le **design Faction** du Sprint 1 : réputation par village, matrice de
diplomatie, quotas de grades, conflit territorial programmé (qui protège le RP le reste du temps).

### 2.8 Économie

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Monnaie | Pièces de 100 à 1 M ; **portefeuille-objet volable**, journal des 12 dernières opérations | Liquide + **banque** (ATM, carte) ; bonus quotidien 5 000-10 000 ryo | Ryos, hôtel des ventes, coffres de faction ; racket plafonné ; prêt dû après la mort (Solve) | 🧪 `/ryo` donné par le staff, **aucune source de gain** |

**Lecture.** Les missions sont la source de ryo qui nous manque. Le portefeuille physique volable est très RP
(racket, vol), à cadrer avec le design Faction (trésor, banque) et l'ADR 0006.

### 2.9 Personnage, tenues et visuels

| Aspect | Nydo | Ninshu | Serveurs RP | Reborn |
|---|---|---|---|---|
| Création | Clan tiré au hasard (pondéré) | ADN de clan cumulable, clan principal | **Personnage de clan sur candidature écrite + orale**, jugé sur « crée-t-il du jeu ? » (Solve) ; candidature dans le launcher avec vocal (Ketto) | ✅ création en scène, 6 villages, 21 clans ; whitelist |
| Tenues | **73 pièces 3D par emplacement** (variante bras fins, robe ouverte, plis aux coudes/genoux) | **111 tenues** à pans de tissu animés ; **table de couture** 5×5 (213 patrons) ; **20 coiffures en calque** | Tenues d'institution | ✅ skins 2D composés, 5 tenues Uchiha ; coiffures wave 2 💡 |
| Mise en scène | **Cinématiques en JSON** (caméra, acteurs, animations, marqueurs, bandes), plan « témoins » dans un rayon de 48 blocs, vignettes portrait manga | Paires d'animations attaquant / victime | — | ✅ scène de mort, sélection en scène ; exécutions **sans animation** |
| Effets | Moteur VFX JSON (426 effets), caméra « trauma », arrêt sur impact | Photon | — | particules serveur |

**Lecture.** Deux gains forts et réalistes : un **directeur de cinématiques** minimal (pour animer les exécutions,
qui sont prévues comme cosmétiques vendables) et des **tenues 3D par emplacement** (volume des grades, ANBU, Kage).

### 2.10 Accès, staff et vitrine

| Aspect | Serveurs RP | Reborn |
|---|---|---|
| Entrée | Whitelist partout ; candidature **dans le launcher** avec vocal (Ketto, Zenkai) ; mode découverte sans whitelist (FSC) | ✅ whitelist dans le launcher + Discord |
| Suivi | MJ / RMJ pour les arcs et les apprentissages (FSC, Solve) | ✅ Poste de garde, panel staff |
| Outils RP | `/me`, `/it`, `/na`, missives en jeu obligatoires (Solve) | ✅ chat RP |
| Lore | Wiki complet mais marqué HRP (« votre personnage ne le sait pas ») | ✅ wiki staff/joueurs |
| Vitrine | Page « Lives » des streamers sur le site (Ketto), TikTok | ❌ |

---

## 3. Ce qui est réellement pratique en RP

Filtré sur ce que les serveurs RP gardent ou rendent obligatoire, et sur nos décisions :

1. **Le spécial se mérite par le RP produit**, pas par la puissance (clans et dōjutsu sur candidature, Solve). ✅
   déjà notre philosophie (grades en RP).
2. **Une mort rare mais réelle, toujours encadrée**, avec **porte de sortie**. 🧪 Reborn a l'encadrement, il manque
   la porte de sortie.
3. **Un état à terre qui donne un rôle aux autres** (porter, soigner, escorter). 🧪 Reborn l'a.
4. **Des institutions tenues par les joueurs** : bureau des missions, conseils, hôtel des ventes. ❌ Reborn n'en a
   aucune.
5. **La rareté par quotas** : ce qui est rare devient un enjeu politique et un objet de vol (yeux, slots S,
   Jōnin). 🧪 partiel (slots S).
6. **Une politique avec de vrais leviers** : réputation, diplomatie, alliances, exil. ❌ (design Faction).
7. **Un conflit territorial programmé** : le PvP se concentre sur des créneaux, le RP est protégé le reste du
   temps. ❓
8. **Des objets RP volables** : parchemins (✅ chez nous), yeux, portefeuille, fioles.
9. **Le lore marqué HRP** pour éviter le métagaming.
10. **La vitrine streamers**, principal canal de recrutement de ces serveurs.

**Problèmes récurrents** à anticiper (vus dans les règles anti-abus) : copinage dans les montées en grade, refus
de jouer la peur et « WinRP » en combat, métagaming par Discord, déconnexion en pleine scène, abus de genjutsu
pour gagner une scène, campement des points de ressources.

---

## 4. Ce qu'on écarte (contraire aux décisions ou peu RP)

| Idée | Pourquoi on l'écarte |
|---|---|
| Rang calculé au niveau, XP de combat, points de jutsu à coût croissant | Grades donnés en RP, XP invisible sans combat hors zone (décisions 08/10 et 10/10) |
| Clan tiré au hasard, village généré par le mod | Clans sur choix et candidature ; map faite à la main |
| Panneau de cible « menace élevée » pour tous | Anti-RP ; à réserver aux porteurs de dōjutsu (idée retenue en 5.B) |
| Possession où une IA tue des joueurs | Morts sans validation staff ; remplacée par l'écran noir + MJ |
| Bijū sauvages et carte pour les trouver | Les bijū sont attribués par le staff |
| Bonus quotidien de 5 000-10 000 ryo | Inflation ; les ryo viennent des missions |
| Combat aérien (juggle) | Lourd, peu RP |
| Éveil automatique du Mangekyō | À remplacer par une **alerte au staff** sur le même déclencheur |

---

## 5. Ce qu'on récupère, par lots

Effort : **F** facile · **M** moyen · **L** lourd. Valeur RP de ★ à ★★★.

### Lot A — Sprint 1, à brancher sur le travail en cours

| # | Idée | Origine | Valeur | Effort | Où |
|---|---|---|---|---|---|
| A1 | **Directeur de cinématiques minimal** (JSON : caméra à images clés, animations, marqueurs, bandes noires) branché sur les **exécutions** | Nydo | ★★★ | M | `reborn-hud` + `executions.yml` |
| A2 | **Paires d'animations attaquant / victime** pour les exécutions et le plan « témoins » à 48 blocs | Ninshu, Nydo | ★★★ | M | après A1 |
| A3 | **Matière du design Faction** : réputation par village (barème, recherché, exil), matrice de diplomatie, quotas de grades, organisation qui recrute les mal vus | Nydo, Ninshu, Solve | ★★★ | F à M (une fois décidé) | ShinobiCore |
| A4 | **Porte de sortie** dans la demande de RPK | Solve | ★★★ | F | `KillRequestManager` |

### Lot B — Combat : règles faciles, gros gain de lisibilité

| # | Idée | Origine | Valeur | Effort |
|---|---|---|---|---|
| B1 | Parade avec réarmement (5 ticks actifs / 12 de réarmement), valable seulement pendant la phase active adverse | Nydo | ★★★ | F |
| B2 | Garde **haute / basse** (accroupi) et garde **frontale** uniquement | Ninshu, Nydo | ★★★ | F |
| B3 | Pression de garde (qui martèle est repoussé) et anti-spam du même coup | Nydo | ★★★ | F |
| B4 | Clash de coups identiques (≤ 8 ticks → choc, gel 20 ticks) | Ninshu | ★★ | F |
| B5 | Multiplicateur de dégâts PvP par coup ; anti-rejeu et plafonds d'entrées réseau | Nydo | ★★★ | F |
| B6 | Signes plus rapides avec la maîtrise, interrompus si l'on est frappé | Nydo | ★★★ | F |
| B7 | **Tenketsu** (7 points, régén −1/7 et coût +12 % par point) pour le Poing souple Hyūga | Nydo | ★★★ | F à M |
| B8 | **Loadout limité** : techniques préparées avant la mission, presets | Nydo | ★★★ | F à M |
| B9 | Phases d'action (montée / actif / récupération) et formes de hitbox | Nydo | ★★★ | M à L |

### Lot C — Missions et outils d'animation (le plus gros manque)

| # | Idée | Origine | Valeur | Effort |
|---|---|---|---|---|
| C1 | **Bureau des missions** tenu par les joueurs : rangs D→S, déblocage par nombre de missions, missions de Kage, missions commanditées par des joueurs | Solve | ★★★ | M |
| C2 | Moteur de missions, lot 1 : parler, aller à un lieu, livrer, épreuve ; journal ; **caution** ; **missions d'escouade partagées** | Nydo, Ninshu | ★★★ | M |
| C3 | **Missions, PNJ et dialogues créés en jeu par le staff** (sans redémarrage) | Nydo | ★★★ | M |
| C4 | Rapport de mission généré (même mise en page que les rapports faits à la main) | Reborn + Nydo | ★★ | F après C2 |
| C5 | Marchand ambulant rare **piloté par le staff** (type Kabuto) | Nydo | ★★★ | F à M |
| C6 | Guide en jeu data-driven (pages en JSON, statut par page) | Ninshu | ★★★ | M |
| C7 | Escouades persistées (aujourd'hui en mémoire) | Ninshu | ★★ | F |

### Lot D — Visuels et RP du quotidien

| # | Idée | Origine | Valeur | Effort |
|---|---|---|---|---|
| D1 | **Tenues 3D par emplacement** (pans de tissu, variante bras fins, robe ouverte) pour grades, ANBU, Kage | Nydo, Ninshu | ★★★ | M |
| D2 | Coiffures en calque séparé, masquable sous un couvre-chef (wave 2) | Ninshu | ★★ | M |
| D3 | Table de couture (patrons, couleurs, aperçu 3D) | Ninshu | ★★★ | M |
| D4 | Portefeuille physique volable (après le design Faction / ADR 0006) | Nydo, Ninshu | ★★★ | F à M |
| D5 | Soin interrompu = remis à zéro ; mort créditée à qui a mis à terre | Ninshu | ★★ | F |

### Lot E — Après la beta (ou à avancer sur décision)

| # | Idée | Origine | Valeur | Effort |
|---|---|---|---|---|
| E1 | **Yeux physiques** : œil-objet traçable, orbites, extraction sur **corps KO ou cadavre avec validation staff** (circuit RPK), cécité demi-écran, **bandage par un médic**, greffe par un médic, pouvoirs par œil G/D | Nydo, Ninshu, Solve | ★★★ | M à L |
| E2 | **Lien de bijū jouable** dans ShinobiTail (prêt de chakra, mémoires, envies, répliques), puis écran Lien, possession « écran noir » arbitrée par le MJ, rituel de scellement en événement validé | Nydo | ★★★ | F → M |
| E3 | Huit Portes à blessures persistantes ; rituel interruptible par d'autres joueurs | Ninshu | ★★★ | M |
| E4 | Susanoo réduit (cage, squelette) puis par phases ; genjutsu en cinématique | Nydo | ★★ | M à L |
| E5 | Moteur VFX data-driven, lock-on, forme 4 queues | Nydo | ★★ | L |

---

## 6. Décisions à prendre

Ces points conditionnent les lots. Ils sont reportés dans [`CHANTIERS.md`](../CHANTIERS.md) (§ décisions en
attente) :

1. **Lot A3 / Faction** : reprend-on réputation par village, diplomatie et quotas de grades (6 Jōnin, Sannin) ?
2. **Lot B** : on ouvre le chantier « règles de combat » (B1-B8) dans le Sprint 1 ou le Sprint 2 ?
3. **Lot C** : le bureau des missions est-il tenu par les joueurs (modèle Solve), par le staff, ou les deux ?
4. **Lot E1** : le vol d'yeux entre-t-il dans la beta ? (Le Sharingan n'existe pas encore en vrai chez nous.)
5. **Greffe d'ADN** (Kabuto) : compatible avec des clans sur candidature, ou on l'écarte ?
6. **Vitrine streamers** sur le site / launcher : oui ou non ?

---

## 7. Comment refaire l'étude

- Décompilation : Vineflower 1.11.1 (Maven Central), lancé sur les `.class` extraits du jar (`-Xmx9G`, 6 threads ;
  extraire avec Python `zipfile`, l'`unzip` de Git Bash rate les motifs). Le code décompilé reste **hors du dépôt**
  (droits) : poste principal, dossier `Reborn - Gestion/Mods-decompiles/` (à côté de `RBLAUNCHER/`), avec
  `nydo/` et `ninshu/` (`code-source/`, `ressources/`, le jar d'origine) et un `LISEZMOI.txt`.
- Les annexes citent les classes du mod : on peut retrouver chaque affirmation en redécompilant le même jar.
