# Game Plan — Reborn

> **Ce que contient ce document :** le « Game Plan » de Reborn, rédigé sur le **modèle exact** du Game Plan de
> **RedLine** (projet RP One Piece sur nanos world, cahier des charges lu en entier le 2026-10-13) : un en-tête de
> spécification, une **feuille de route en quatre phases** avec dépendances et états, puis **une section par
> système, dans l'ordre de leur document**. Chaque section dit ce que fait Reborn aujourd'hui, ce que fait RedLine,
> l'écart, et la cible proposée pour Reborn.
>
> **Méthode décidée le 2026-10-13** : on avance **point par point, dans l'ordre RedLine** (§54). Une cible marquée
> **« validé 13/10 »** a été tranchée par l'utilisateur ; tout le reste est une **proposition, à valider point par
> point**. Chaque décision prise en route va dans [`pilotage/DECISIONS.md`](./pilotage/DECISIONS.md) et met à jour
> la section concernée.
>
> **Sources** : [`CHANTIERS.md`](./CHANTIERS.md), [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md),
> [`pilotage/DECISIONS.md`](./pilotage/DECISIONS.md), les fiches [`gameplay/`](./gameplay/) et
> [`technique/`](./technique/), le code (`minecraft/shinobi/`, `minecraft/mod-hud/`, `apps/`), et l'étude
> comparative [`etudes/REDLINE_REFERENCES.md`](./etudes/REDLINE_REFERENCES.md) (qui garde l'analyse détaillée,
> les priorités P1-P3 et ce qu'on écarte). On reprend la **structure et des concepts** de RedLine, jamais leur texte.
>
> **États** : ✅ fait · ✍🏻 en cours ou partiel · ❌ pas commencé. Quand un état est incertain, il est noté ✍🏻 avec
> la raison. **Priorités** : 🔴 Critique · 🟠 Important · 🟡 Secondaire · 🟢 Plus tard.
>
> Mis à jour le **2026-10-14**. Aucun secret, aucune adresse de serveur dans ce document (dépôt public).

---

# 📖 Introduction

# **Specifications document — Reborn**

## Reborn Roleplay : Minecraft

**Reborn** est un serveur de roleplay multijoueur **Naruto**, sur Minecraft, avec son launcher, son mod client et
ses plugins serveur.

Le principe est simple : **que des joueurs**. Pas de PNJ de quête, pas de scénario écrit d'avance, pas de quêtes
scriptées. Chaque shinobi du monde est un vrai joueur, qui choisit sa voie : devenir Kage de son village, maître
d'un art interdit, médic respecté, espion de l'ANBU, déserteur traqué par le Bingo Book. Les histoires naissent des
rencontres, des alliances, des trahisons, des missions et des morts. Les seuls éléments « automatiques » (missions
de rang D, tirages des bibliothèques, MythicMobs) servent de **décor** au RP, jamais de fil conducteur.

L'expérience repose sur **trois valeurs** :

| Valeur | Ce qu'elle veut dire chez Reborn (décisions déjà prises) |
|---|---|
| **Liberté** | L'apprentissage est **libre** : aucune stat ne ferme une porte RP (23/09) ; les bibliothèques sont en **accès libre**, les restrictions sont RP (08/10) ; le NLR est laissé au joueur (06/10) ; le duel se joue **n'importe où** si les deux acceptent (13/10) |
| **Conséquence** | Se battre hors d'un terrain d'entraînement **coûte** : PV persistants, KO, ATA, hôpital (06/10) ; la **mort RP** existe (RPK validé par le staff, puis jouée) ; un parchemin est **unique** et peut être **volé** ou perdu (08/10, 13/10) ; les grades se gagnent **en RP**, pas à l'XP (08/10) |
| **Immersion** | Toute l'interface est dans la **DA Reborn** (nuit, laque, or ; une signature par écran) ; les inconnus restent « Inconnu » tant qu'on ne s'est pas présenté ; niveau et XP **invisibles** (10/10) ; infobulles **sans chiffres** (29/09) ; HUD en jeu **subtil** (13/10) |

Ce document est la **référence centrale** du projet Reborn pour l'équipe (Omar, le staff, les sessions Claude). Il
couvre chaque système, de la création de personnage au combat, à l'économie et aux outils du staff. Il évolue avec
le projet : une décision de l'utilisateur met à jour la section concernée et s'inscrit dans le journal des
décisions.

**Tech stack** :

| Couche | Technologie |
|---|---|
| Jeu | **Minecraft 26.2** · Java 25 · Fabric Loader 0.19.3 · Fabric API 0.156.0+26.2 (client déobfusqué, sans mappings) |
| Client | Mod Fabric **`reborn-hud`** (toute l'interface : menus, HUD, chat, sacoche, fiche shinobi, Poste de garde…), `reborn-integrity` (attestation), `reborn-ost` (musiques) |
| Serveur | **Purpur 26.2** + six plugins **Shinobi** (Core, Abilities, Combat, Learning, Sense, Tail) + RebornGuardian, RebornOst ; Nexo (objets et meubles 3D), MagicSpells, MythicMobs, PlasmoVoice |
| Launcher | **Tauri 2** + React 19 (connexion Microsoft, liaison Discord, manifest signé Ed25519, auto-update) |
| Web | **API NestJS 11** (Prisma, Postgres, Redis), **panel staff** Next.js, **bot Discord** (discord.js v14) relié à l'API par HMAC |

**Statut actuel** (au 2026-10-13, détail dans [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md)) : **Sprint 1 prolongé**
(décision : le finir avant le Sprint 2), **beta jouable visée le 31 décembre 2026**. En ligne : launcher 0.3.45,
`reborn-hud` 0.4.174 (manifest 3.1.130), plugins Shinobi du 13/10 sur le serveur **dev** ; l'état exact de la prod
n'est pas tracé. Toute l'interface est passée dans la DA Reborn ; KO / mort RP, yeux, bibliothèques v2 et duel sont
codés et attendent leurs tests à deux comptes.

---

# 🗺️ REBORN — SYSTEM ROADMAP

> Chaque système de la feuille de route RedLine, **dans leurs quatre phases**, avec son **équivalent Reborn**, puis
> les systèmes de leur document absents de leur feuille de route, puis les **systèmes propres à Reborn** (§53).
> Colonne « État » = **vérité du dépôt** (fiches, `CHANTIERS.md`, `ETAT_DES_LIEUX.md`, code) au 13/10.
> « Cible » : **validé 13/10** ou **proposition**.

---

## 🔴 PHASE 1 — Critique (Fondations)

| Système RedLine | Équivalent Reborn | Priorité | Dépend de | État | Cible | § |
|---|---|---|---|---|---|---|
| Player ID System | **Compte Reborn** (Microsoft + Discord) + **N° de personnage** ; états du personnage ; onboarding launcher | 🔴 Critique | — | ✍🏻 compte et UUID de perso ✅ ; N° court ❌ ; état unique ❌ ; onboarding en cours (non commité) | validé 13/10 | §1 |
| Inventory + Hotbar | **Sacoche « inrō »** : barre de 9 + sacs à paliers | 🔴 Critique | Player ID | ✅ | validé 13/10 | §2 |
| Item System (pickup, drop, weight) | Ramassage **manuel**, **aura de rareté**, disparition ; poids et surcharge | 🔴 Critique | Sacoche | ✍🏻 poids, surcharge progressive et jeter ✅ ; ramassage manuel, aura, disparition ❌ | validé 13/10 | §3 |
| Equipment System (weapon, head) | Emplacements **Tête** et **Arme** (arme visible sur le corps) + cosmétiques | 🔴 Critique | Sacoche | ✍🏻 emplacements cosmétiques ✅ ; Tête / Arme ❌ | validé 13/10 | §2 |
| Basic HUD (HP, stamina, hunger, compass) | Vitals PV / chakra / endurance, HUD combat, éditeur de HUD | 🔴 Critique | Player ID | ✅ vitals ; boussole, zone d'effets, notifications ❌ | proposition | §4 |
| Staff commands | **Poste de garde** + commandes staff | 🟠 Important | Player ID, états | ✍🏻 tp, ramener, geler, observer, réveiller, sanctions ✅ ; annonces, écran noir, voir la sacoche, tenue forcée, notes, reset ❌ | proposition | §5 |
| Logs System | Journaux par personnage | 🔴 Critique | Player ID | ✍🏻 staff, agressions, RPK, duels, bibliothèques ✅ ; économie et objets ❌ | proposition | §6 |
| Staff Tools (tp, freeze, logs) | Poste de garde, Infirmerie, `/vanish` | 🔴 Critique | Player ID | ✅ (gel en mémoire seulement) | — | §5 |

---

## 🟠 PHASE 2 — Important (boucle de jeu)

| Système RedLine | Équivalent Reborn | Priorité | Dépend de | État | Cible | § |
|---|---|---|---|---|---|---|
| Player movements | **Marche** par défaut, **trot** (sprint maintenu), **sprint** (double appui, endurance), Naruto run | 🟠 Important | — | ✍🏻 trois allures codées 14/10 (non publiées) | validé 14/10 | §7 |
| Advanced Movement (dash, double jump) | Dash, double saut, saut de chakra, mur, escalade, voies de mobilité | 🟠 Important | Déplacements | ✅ ; 3 styles de course ❌ (Sprint 2) ; dégâts de chute désactivés | proposition | §7 |
| Combat — Brawler / Swordsman | **Taïjutsu / Kenjutsu** : M1, garde, parade, endurance | 🟠 Important | Interaction, équipement | ✍🏻 M1, parade, garde brisée ✅ ; jauge de garde, clash, coup chargé ❌ ; 39 techniques Sprints 2-3 | proposition (lot B) | §9 |
| Combat — Sniper | **Armes de jet** (kunai, shuriken) | 🟠 Important | Interaction, équipement | ❌ objets et effets de techniques seulement, pas de lancer | proposition | §10 |
| KO / Pain System | **KO, ATA, mort RP** | 🟠 Important | Combat | ✅ à tester à deux comptes | nos décisions + 1 ajout | §11 |
| Player State System (KO, combat, Logia, Haki, Granite) | **8 états** + modificateurs ; « contournements » (§34) | 🟠 Important | Déplacements | ✍🏻 états éclatés ; prison ❌ | validé 13/10 (liste) | §13 |
| Interaction System (E key) | **Mode interaction R** | 🟠 Important | Phase 1 | ✍🏻 menus joueur et corps ✅ ; entrées sans commande serveur (échanger, cadenas…) ; masque, volume vocal ❌ | proposition | §14 |
| Skill Tree (skills) | **Progression** : 6 stats, XP invisible, grades, parchemins | 🟠 Important | Combat | ✅ (Mérite, bonus de grade ❓) | nos décisions + 2 ajouts | §32 |
| Bank System | **Compte au guichet du village** | 🟠 Important | Sacoche, Faction | ❌ | proposition maquettée 14/10 | §20 |
| Economy | **Ryo** (+ RBCoins de compte) | 🟠 Important | Sacoche | ✍🏻 solde et échoppe ✅ ; aucune source de gain, bourse-objet ❌ | proposition maquettée 14/10 | §19 |
| Robbery System | **Vols** (parchemins, puis points de vol) | 🟠 Important | Interaction | ✍🏻 vol de parchemins ✅ ; points de vol configurés ❌ | proposition | §48 |
| Legal Jobs (blacksmith, medic, merchant) | **Spécialisations** de section + **missions de rang D** | 🟠 Important | Économie | ✍🏻 médic ✅ ; missions ❌ | proposition | §21 |
| Illegal Activities (smuggling, contraband) | **Contrebande** (pilules interdites, receleurs) | 🟠 Important | Économie | ❌ | après la beta | §31 |
| Letter system | **Volière** | 🟠 Important | Player ID | ❌ (Sprint 3) | proposition | §30 |
| Storage System | **Coffres configurables** (+ bibliothèques) | 🟠 Important | Player ID | ✍🏻 bibliothèques ✅ ; coffres ❌ | proposition (F5) | §29 |
| Treasure System | **Caches, reliques, coffre porté** | 🟠 Important | Sacoche, économie | ❌ | après la beta | §35 |
| Faction / Crew System | **Faction** (villages, organisations, sections, fonctions) | 🟠 Important | Player ID | ✍🏻 proposition maquettée ; escouades Genin ✅ | proposition en cours | §37 |
| Proximity Chat | **PlasmoVoice** + chat RP en panneau | 🟠 Important | Player ID | ✍🏻 vocal + icône ✅ ; portées, volume par joueur ❌ | proposition | §17 |
| DenDen Mushi system | **Appel de service** (système à part) + **radio = escouades** (oreillettes) | 🟠 Important | Chat de proximité | ✍🏻 `/aide` seulement | radio décidée 14/10, maquettes v3 ([`RADIO.md`](./gameplay/RADIO.md)) ; appel de service à concevoir | §18 |
| Medic Job | **Médical** (médics, paume, bandages, hôpitaux) | 🟠 Important | Déplacements | ✍🏻 base ✅ ; verrou de combat, `MedicalService` ❌ | proposition | §26 |
| Journalist & Camera System | **Avis de recherche-objet**, photo-preuve | 🟠 Important | Déplacements | ✍🏻 mode photo client ✅ ; objets ❌ | proposition | §46 |
| Island capture system | **Conflit territorial** | 🟠 Important | Économie, combat, carte | ❌ | après la beta | §41 |

**Sections de leur document hors de leur feuille de route** (même phase) :

| Système RedLine | Équivalent Reborn | Priorité | Dépend de | État | Cible | § |
|---|---|---|---|---|---|---|
| Nourriture / satiété / régénération | **Un cuisinier joueur** prépare et donne de la main à la main ; manger prend du temps ; pilules seules portables | 🟠 Important | États, sacoche, monnaie | ❌ (faim mal neutralisée) | principe validé 14/10, maquettes v3 | §12 |
| Duel System | **Duel consenti n'importe où** | 🟠 Important | Combat, KO | ✍🏻 codé le 13/10, non publié | décidé 13/10 | §15 |
| Trading system | **Fenêtre d'échange** | 🟠 Important | Interaction, monnaie | ❌ | proposition | §16 |
| Spell Deck System | **Presets de techniques** | 🟠 Important | Progression | ✍🏻 5 liaisons par objet ✅ ; presets ❌ | proposition | §33 |
| Miner / Lumberjack / Blacksmith / Cook | Gisements, artisanat, ingrédients rares | 🟡 Secondaire | Économie | ❌ | après la beta | §23-§27 |

---

## 🟡 PHASE 3 — Secondaire (contenu RP)

| Système RedLine | Équivalent Reborn | Priorité | Dépend de | État | Cible | § |
|---|---|---|---|---|---|---|
| Character Creation | **Création en scène** (8 étapes) | 🟡 Secondaire | Player ID | ✅ ; cinématique désignable ✅ ; kit de départ ❌ | 2 ajouts | §43 |
| Character Editor | **Apparence** (skins composés, 35 coiffures, pipette) | 🟡 Secondaire | Création | ✍🏻 fait ; palettes de clan et assets wave 1 à finir | proposition | §44 |
| Identity System (name, bounty, profile) | **Connu / inconnu**, fiche shinobi, Bingo Book | 🟡 Secondaire | Création | ✍🏻 | proposition | §52 |
| Clothing Shop (in-game) | **Échoppe** (ryo) | 🟡 Secondaire | Économie | ✅ | — | §42 |
| Cosmetics System (account-based) | **Cosmétiques RBCoins** (compte) / objets de personnage | 🟡 Secondaire | Player ID | ✍🏻 solde RBCoins en base ; pas de boutique en jeu | proposition | §39 |
| Sea Travel + Ship System | **Voyages entre pays** (routes, convois, postes-frontières) | 🟡 Secondaire | Économie | ❌ (autres villages en cut list) | après la beta | §40 |
| Vivre Card System | **Sceau de pistage** (marque de chakra) | 🟡 Secondaire | Player ID | ❌ | proposition | §52 |
| Territory Control | **Territoires de village** | 🟡 Secondaire | Faction | ✍🏻 zones de ressources par plugin tiers non versionné | après la beta | §52 |
| Casino | **Tanzaku-gai** (ryo seulement) | 🟡 Secondaire | Économie | ❌ | après la beta | §52 |
| Bounty System | **Bingo Book** | 🟡 Secondaire | Combat, identité | ❌ | proposition (Faction §8.3) | §46 |
| Map System | **Carte du monde (M)** | 🟡 Secondaire | Player ID | ✅ Konoha seulement ; marqueur, zones survolables ❌ | proposition | §36 |
| Log Pose | **Objet qui pointe un lieu** + boussole | 🟡 Secondaire | Carte | ❌ | après la beta | §49 |
| Navigator Profession | **Cartographe** (spécialisation) | 🟡 Secondaire | Voyages | ❌ | après la beta | §28 |

---

## 🟢 PHASE 4 — Plus tard (vie du serveur)

| Système RedLine | Équivalent Reborn | Priorité | Dépend de | État | Cible | § |
|---|---|---|---|---|---|---|
| VIP System + Subscriptions | **RBCoins cosmétiques**, pas d'avantage de jeu | 🟢 Plus tard | Cosmétiques, économie | ❌ (`reborn.premium` dormant, Stripe en v1.1) | nos décisions | §39 |
| Launcher | **Launcher Tauri** | 🟢 Plus tard (fait chez nous) | Player ID | ✅ 0.3.45 ; onboarding Ketto en cours | validé 13/10 | §47 |
| Character Selection Screen | **Sélection en scène** (étang) | 🟢 Plus tard (fait chez nous) | Création | ✅ | journal RP proposé | §45 |
| Journal System | **Journal RP** (gazette, avis publics, annonces du Kage) | 🟢 Plus tard | Player ID | ❌ | proposition | §45 |
| Staff Event System | **Événements** (cinématiques, météo, OST, annonces, séisme) | 🟢 Plus tard | Systèmes de base | ✍🏻 | proposition | §52 |
| Performance Optimization | **LotGui / LotBlit**, écrans opaques | 🟢 Plus tard | Tout | ✍🏻 menus optimisés ; FPS à mesurer | — | §52 |
| Branding / Network | Rich Presence, réseaux, bot Discord, Trello, Miro | 🟢 Plus tard | Serveur stable | ✍🏻 | — | §52 |

---

## 🍥 Systèmes propres à Reborn

Détail et liens : §53.

| Système | Priorité | Dépend de | État |
|---|---|---|---|
| Chakra (grande échelle, natures) | 🔴 Critique | Player ID | ✅ |
| Six stats + fiche shinobi | 🔴 Critique | Chakra | ✅ |
| Grades RP + Mérite | 🟠 Important | Stats | ✅ (usage du Mérite ❓) |
| Techniques + Technique Creator | 🔴 Critique | Combat | ✍🏻 39 techniques à produire |
| Parchemins + bibliothèques v2 (vol, secrets) | 🟠 Important | Sacoche, techniques | ✅ sur le dev, à tester |
| Séances + concentration du chakra | 🟠 Important | Parchemins | ✍🏻 rendu à trancher |
| Slots du rang S | 🟡 Secondaire | Parchemins | ✅ |
| Mort RP (RPK, exécutions, scène de mort) | 🟠 Important | KO | ✅ à tester |
| ATA, terrains d'entraînement, zones de repos | 🟠 Important | KO | ✅ |
| Yeux (chirurgie, Byakugan) | 🟡 Secondaire | KO, médical | ✅ |
| Clans + 18 sorts claniques | 🟠 Important | Techniques | ✍🏻 |
| Perception (ShinobiSense) | 🟡 Secondaire | Chakra | ✍🏻 |
| Démons à queues (ShinobiTail) | 🟢 Plus tard | Techniques | ✍🏻 à auditer |
| Académie + équipes Genin (ShinobiLearning) | 🟡 Secondaire | Progression | ✍🏻 à auditer |
| Méditation | 🟡 Secondaire | Chakra | ✅ base |
| Missions + bureau des missions | 🟠 Important | Faction, monnaie | ❌ |
| Médical avancé (antidotes, coma) | 🟡 Secondaire | Médical | ❌ |
| OST contextuelle, météo, emotes, postures | 🟡 Secondaire | — | ✅ |
| Caméra, mode photo, galerie, éditeur de HUD | 🟡 Secondaire | — | ✅ |
| Whitelist, tickets, pont Discord, intégrité | 🔴 Critique | Launcher | ✅ |

---

# 🆔 1. Player ID System (outils staff)

> RedLine : *Player ID System* (avec *Character State System*, *Entity Binding*, *Staff Mode*). **Cible : validé
> 13/10** pour les deux niveaux d'identifiant, l'onboarding du launcher et la liste des états ; le reste est une
> proposition.

L'identifiant est la **fondation technique** du serveur : création de personnage, sacoche, combat, échoppe,
faction et journaux s'en servent pour reconnaître un personnage de façon fiable.

## 1.1 Aujourd'hui chez Reborn

- **Compte** : un compte Reborn dans l'API (`apps/api`), créé à la première connexion du launcher par le compte
  **Microsoft** (chaîne MS → Xbox → Minecraft, App ID approuvé, [ADR 0001](./adr/0001-microsoft-app-approval-required.md)) ;
  le **Discord** se lie ensuite au compte (`/v1/auth/discord/start`). Whitelist et tickets passent par ce compte.
- **Personnage** : un compte Minecraft peut avoir **plusieurs personnages**, chacun identifié par un **UUID**
  (`ShinobiCharacter`, un YAML par joueur dans `characters/`, sacoche dans `rpbags/<uuid du perso>.yml`). Depuis le
  13/10, chacun a aussi un **N° court** (Player ID, §1.4 — **fait**).
- Chaque personnage a un `id` (UUID aléatoire) et un `ownerId` (UUID Minecraft) — `ShinobiCharacter`. Le Poste de garde
  agit sur le **joueur** (UUID Minecraft) et affiche le nom du personnage ; les commandes prennent un `<joueur>`
  (`/grade`, `/ryo`, `/character edit`).
- **Slots de personnage** : 2 par défaut, 4 avec `reborn.premium` (dormant), 7 pour le staff
  (`CharacterSelectManager`).
- Fiche : [`gameplay/PERSONNAGES.md`](./gameplay/PERSONNAGES.md).

## 1.2 Chez RedLine

Deux niveaux : un **Account ID** (créé par le launcher, lié à l'e-mail et au Discord ; un ban oblige à recréer compte
**et** Discord) et un **Player ID** numérique par personnage (ex. 82545) qui porte toutes les données de jeu. Aucune
action ne doit se baser sur le pseudo ou le nom RP.

## 1.3 L'écart

Le modèle est le même (compte + personnage), mais Reborn n'a pas d'**identifiant court** utilisable à l'oral, dans
les commandes, les journaux et les notes staff, et l'onboarding du launcher ne présente pas clairement les deux
comptes obligatoires.

## 1.4 Architecture cible — **validé 13/10**

### Account ID — le compte Reborn

→ Créé **par le launcher**, au premier lancement.
→ Lié à **deux comptes obligatoires** : **Microsoft** (l'identité Minecraft) et **Discord** (whitelist, tickets,
annonces, staff).
→ Le Discord est lié au **compte**, jamais au personnage.
→ *Proposition* : un ban porte sur le **compte Reborn** et bloque le compte Microsoft **et** le Discord qui lui sont
liés (on ne revient pas en changeant seulement l'un des deux).

### Player ID — le numéro de personnage

→ **Un numéro unique par personnage**, attribué à la création. Exemple : **N° 82545**.
→ Un compte, plusieurs personnages, plusieurs numéros :

```
Compte Reborn (Microsoft + Discord)
 ├─ N° 82545  premier personnage   (Konoha, vivant)
 └─ N° 82546  second personnage    (Kiri, mort RP — le numéro n'est jamais réattribué)
```

→ Le numéro porte **toutes les données de jeu** du personnage : position, PV, chakra, endurance, sacoche et
emplacements, ryo, XP et niveau (invisibles), six stats, grade et Mérite, techniques et maîtrises, blessures et ATA,
apparence, village et clan, faction et fonction, état courant (§1.5), journaux.
→ Le numéro est **interne** (UUID conservé) : il s'ajoute à côté de l'UUID existant, il ne le remplace pas.

**Validé 13/10** (« N° ok… on commence par 00000 au cas où ») — **fait** (ShinobiCore + mod, non publié) :
→ Numéros **séquentiels à 5 chiffres affichés avec zéros**, **départ 00000** (remplace « départ 10 000 »), jamais
réutilisés, même après une mort RP ou une suppression (l'index garde le personnage, marqué supprimé).
→ Index `plugins/ShinobiCore/player-ids.yml` (compteur `next` + N° → perso, compte, nom, supprimé ; écriture atomique,
compteur écrit avant d'attribuer) et champ `playerId` dans le YAML du personnage ; migration idempotente au démarrage
pour les personnages existants (ordre : compte puis perso) ; API `CharacterService.getPlayerId` / `findByPlayerId`
(hors ligne).
→ **Visible du staff** : Poste de garde (N° devant chaque joueur, recherche par `#00042`, pseudo ou nom RP, fiche,
alertes), journaux (`aggression-log`, `kill-log`, journal du Poste de garde) ; **visible du joueur** : pastille
discrète « N° 00042 » à côté du nom en bas de la fiche shinobi (clic = copie, pour un ticket) ; **jamais** envoyé aux
autres joueurs.
→ Les commandes staff acceptent `#00042` en plus du nom (`/grade #00042 chunin`, `/tp #00042`…) ; nouvelle commande
`/pid <joueur|#N°>` — détail dans [`technique/PLUGINS_REFERENCE.md`](./technique/PLUGINS_REFERENCE.md) §2.1 bis.
→ Reste : le **calque staff** au-dessus des têtes (§1.7) n'existe pas encore (pas de calque observateur dans le mod) ;
le `duel-log` et le journal des bibliothèques (visible des propriétaires, donc pas de N°) ne portent pas le N°.

## 1.5 États du personnage — **validé 13/10**

Chaque personnage a **toujours un état courant**, tenu **par le serveur seul** (le client ne peut pas le modifier).
Tous les systèmes (combat, déplacement, interaction, techniques, sacoche) **lisent l'état** avant d'agir.

| État | Ce qu'il autorise | Ce qu'il interdit | Aujourd'hui chez Reborn |
|---|---|---|---|
| **Libre** (*Idle*, par défaut) | Tout : se déplacer, interagir, combattre | — | ✅ (état normal) |
| **Combat** | M1, parade, techniques, endurance | Interactions longues (fouille, échange, soin tenu) | ✍🏻 il existe des minuteurs de combat par système, pas d'état commun |
| **KO** | Ramper et chuchoter (à terre), rien (inconscient) | Se déplacer, attaquer, interagir | ✅ à terre / inconscient (`KoService`) |
| **Étourdi** | — | Se déplacer, attaquer, parer | ✍🏻 garde brisée (étourdi 20 ticks, lenteur) dans ShinobiCombat |
| **Garde** | Absorber les coups | Attaquer (pendant la garde) | ✅ parade (touche C) |
| **Porté** | Tenter de se libérer (Espace) | Se déplacer, attaquer, interagir | ✅ porté / tenu à la gorge (corps KO seulement) |
| **Prison** | Se déplacer dans une zone fermée | Techniques, sortie de la zone | ❌ |
| **Mode staff** | Caméra libre, outils staff | Toute action de jeu | ✍🏻 `/modestaff` (14/10, non publié : invisible, vol, aucune action de jeu, plaques côté serveur ; affichage des plaques dans le mod à faire), `/vanish`, « Observer » ; le **gel** staff est **persistant** depuis le 14/10 |

*Propositions, à valider* :
→ L'ATA, le duel, la méditation, la scène de mort et la concentration du chakra ne sont **pas des états** mais des
**modificateurs** qui se cumulent avec l'état (on peut être « Libre + ATA »).
→ Un **service d'état unique** (`EtatService`, publié comme les autres services de ShinobiCore) remplace les lectures
éparses (`KoService.isImpaired`, garde, duel…) ; chaque changement d'état est journalisé (§6).
→ Transitions écrites : Libre ⇄ Combat (premier coup donné ou reçu ; retour à Libre après N s sans coup) ; Combat →
Étourdi (garde brisée) ; Combat → KO (PV à 0) ; KO → Porté (porter, gorge) ; Porté → KO (déposé) ; KO → Libre
(relevé, soigné, hôpital) ; tout état → Prison (arrestation RP, cf. §37) ; tout état → Mode staff.

## 1.6 Liaison personnage ↔ joueur (*Entity Binding*)

→ **Aujourd'hui** : Minecraft gère l'entité ; à la sélection, ShinobiCore charge le **personnage actif** et
l'applique au joueur (apparence, PV, chakra, sacoche) ; sauvegarde automatique et à la déconnexion
(`CharacterAutoSave`). Règle tenue : **un personnage actif par joueur connecté**.
→ *Proposition* : écrire les cas limites comme RedLine — déconnexion en KO, en duel, en séance, pendant une
fouille, un échange ou un vol ; chacun a sa règle (le KO est gardé et son compte à rebours **repart de zéro** à la
reconnexion, cf. §11).

## 1.7 Mode staff

→ **Aujourd'hui** : `/vanish`, « Observer » et téléportations du Poste de garde (F7). **Codé le 14/10 (serveur, non
publié)** : `/modestaff` — invisible aux joueurs et au staff hors mode staff (les staffs en mode staff se voient),
vol, invulnérable, ni coup ni ramassage ni lâcher ; le serveur envoie chaque seconde les **plaques** (N°, nom RP,
pseudo, état, PV) des joueurs à 64 blocs sur `reborn:plaques` (affichage côté mod à faire) ; entrée et sortie au
journal. Protocole : [`technique/PLUGINS_REFERENCE.md`](./technique/PLUGINS_REFERENCE.md) §4 bis.5.
→ *Proposition* : en mode staff, un **calque au-dessus des têtes**, visible des seuls staffs : N° du personnage, nom
RP, compte, état (§1.5), PV ; les staffs en mode staff se voient entre eux ; accès en direct à la sacoche, à l'état et
aux journaux du joueur visé. Le mode staff ne modifie **aucune donnée** sans action explicite.

## 1.8 Onboarding du launcher façon Ketto — **validé 13/10**

> **État : ✍🏻 en cours dans une autre session, non commité au 13/10** — `apps/launcher/src/routes/Login.tsx` affiche
> déjà une carte d'accueil en deux étapes (Microsoft, puis liaison Discord avec attente de `auth_me`, 5 min) et
> `App.tsx` bloque l'entrée tant que les deux ne sont pas faits (contournement en dev seulement). Ne pas refaire :
> reprendre ce travail.

À la première ouverture, le launcher affiche une **carte sombre** :

```
┌────────────────────────────────────────────┐
│            Bienvenue sur Reborn            │
│     Comptes Microsoft et Discord requis    │
│                                            │
│  ① Microsoft   [ Se connecter ]      ✓     │
│  ② Discord     [ Lier mon Discord ]  ·     │
│                                            │
└────────────────────────────────────────────┘
```

→ **Étape 1 : Microsoft** (connexion, identité Minecraft). **Étape 2 : Discord** (liaison au compte).
→ L'étape 2 n'est accessible qu'une fois l'étape 1 faite ; on ne peut pas jouer sans les deux.
→ *Propositions* : chaque étape affiche son état (coche) ; si le launcher est fermé entre les deux, il reprend à
l'étape manquante ; la carte suit la DA du launcher ; une fois les deux faites, on enchaîne sur la whitelist /
candidature (§47). Le bouton « Copier le lien » demandé contre les blocages d'antivirus (décision n° 9 en attente)
trouve sa place sous chaque bouton.

## 1.9 Règles serveur

→ Toute donnée de jeu se lit et s'écrit **par le personnage** (UUID, N°), jamais par le pseudo ou le nom RP.
→ Le nom RP peut changer ; le N° ne change jamais.

---

# 🎒 2. Inventaire + barre d'action

> RedLine : *Inventory + Hotbar*. **Cible : validé 13/10** (on garde la sacoche, emplacements Tête et Arme, surpoids
> = ralentissement progressif, notifications en bas à droite) ; le reste est une proposition.
>
> ✅ **Fait le 13/10 (codé, non publié)** — maquettes validées (`Maquettes/gameplan/02_inventaire/`) puis code :
> fiche [`gameplay/INVENTAIRE_ET_COSMETIQUES.md`](./gameplay/INVENTAIRE_ET_COSMETIQUES.md). Tête = rappel des
> cosmétiques Chapeau / Masque / Bandeau ; Arme = **étui** visible à la ceinture, dégainer / rengainer (Z) ;
> surcharge par paliers (100-115 % Chargé, 115-130 % Surchargé, > 130 % Immobile, réglables) ; notifications en bas
> à droite (canal `reborn:notif`) ; lecture seule en KO / porté / étourdi ; journal des objets ; cases cachées 9-35
> vidées.

## 2.1 Aujourd'hui chez Reborn — la sacoche « inrō »

- **Touche E** : la Sacoche. Les **9 premières cases** sont la **vraie barre d'action** du joueur (« sur soi ») ;
  l'espace supplémentaire vient du **sac porté**, dans un emplacement Sac dédié (code : `ShinobiCore/…/inventory/`,
  mod `menu/inventory/`).
- **Sacs à paliers** (`BagTier`) :

| Sac | Objet | Cases en plus | Poids max en plus | Total |
|---|---|---|---|---|
| Aucun (« à la ceinture ») | — | +0 | +0 (base 12 kg) | 9 cases, 12 kg |
| Sacoche | `sac_sacoche` | +9 | +8 kg | 18 cases |
| Bandoulière | `sac_bandouliere` | +18 | +16 kg | 27 cases |
| Sac ninja | `sac_dos` | +27 | +28 kg | 36 cases |
| Sac lourd | `sac_lourd` | +36 | +45 kg | 45 cases |

- **Poids** (`RpWeights`) : barème par famille de matériau, ou poids propre posé par le plugin qui crée l'objet
  (clé `reborn:rp_weight`, ex. parchemins). Jauge verte → or → rouge dans la Sacoche.
- ~~**Surcharge déjà codée** (`InventoryManager.tickWeights`, chaque seconde) : au-delà du poids max, lenteur
  progressive — jusqu'à 125 % lenteur I, 150 % lenteur II, 200 % lenteur III + fatigue, 300 % lenteur IV, au-delà
  quasi-immobile ; message rouge dans la barre d'action. Seuils **en dur** dans le code.~~ → remplacé le 13/10 par
  les paliers par attributs (§2.4).
- **Cosmétiques** : emplacements en données (`CosmeticSlot` : bandeau, masque, manteau, dos, chapeau, boucle, haut,
  tenue, ceinture, bas), diffusés à tous (`reborn:cosmetics`).
- **Persistance** par personnage (`rpbags/<uuid du perso>.yml`) : chaque personnage a sa sacoche.
- Fiche : [`gameplay/INVENTAIRE_ET_COSMETIQUES.md`](./gameplay/INVENTAIRE_ET_COSMETIQUES.md).

## 2.2 Chez RedLine

12 cases + 6 de barre, **20 kg** ; plein ou trop lourd = **impossible de ramasser** ; emplacements **Tête**
(chapeau, capuche, masque, échangeables) et **Arme** (l'arme équipée conditionne les compétences et se **voit sur
le corps**) ; KO, porté et étourdi bloquent tout usage sans déséquiper ; notification en bas à droite.

## 2.3 L'écart

Notre sacoche est plus riche (sacs à paliers, cosmétiques, poids par objet). Il nous manque les emplacements **Tête**
et **Arme**, l'arme visible sur le corps, des notifications propres, et des seuils réglables.

## 2.4 Cible — **validé 13/10**

### On garde la sacoche

→ La sacoche, la barre de 9 et les **sacs à paliers** restent tels quels (pas de 12 + 6 cases).

### Emplacements Tête et Arme

→ **Tête** : chapeau, capuche, masque (masque ANBU, capuche de déserteur). Ce sont des **objets de personnage**
(échangeables, volables, perdus à la mort RP), distincts des cosmétiques de compte (§39).
→ **Arme** : l'arme équipée (katana, tantō, bō…) **se voit sur le corps** (à la ceinture, dans le dos) ; la
déséquiper la retire du modèle ; en changer met à jour le modèle.

### Le surpoids ralentit, il n'empêche pas de ramasser

→ Dépasser la limite de poids **n'empêche jamais de ramasser** un objet (seul le manque de **cases** l'empêche).
→ Au-delà de la limite, le personnage **ralentit progressivement**, jusqu'à **l'immobilité** au-delà d'un seuil.
→ Le mécanisme existe déjà (§2.1) ; reste à le **rendre réglable** (`config.yml`) et à le vérifier en jeu.
→ ✅ **Codé 13/10** : on ramasse au-delà de la limite **jusqu'à 130 %** (refusé au-delà) ; paliers **Chargé**
100-115 % (vitesse 90 → 70 %, plus de sprint), **Surchargé** 115-130 % (70 → 35 %, ni saut, ni dash, ni course aux
murs), **Immobile** au-delà ; modificateurs d'attributs, plus de potions ; `inventaire.surcharge.*`.

### Notifications

→ Les messages de la sacoche s'affichent **en bas à droite**, **épurés et simples**, comme chez RedLine (une ligne,
une icône, quelques secondes) : « Sacoche pleine », « Trop lourd — tu ralentis », « Objet ramassé ».
→ Plus de message rouge dans la barre d'action. Style : HUD en jeu subtil (DA §6 règle 9).
→ ✅ **Codé 13/10** : 4 lignes au plus, 3 s, fondu, gains identiques cumulés ; indicateur discret sous les vitals.

## 2.5 Propositions, à valider

→ **Liens avec les techniques** : une technique de Kenjutsu exige une **arme équipée** ; le Taïjutsu se fait mains
nues (aujourd'hui rien ne l'impose).
→ **Liens avec l'état** (§1.5) : KO, Porté et Étourdi interdisent d'équiper, de déséquiper, d'utiliser ou de déplacer
un objet ; l'équipement reste en place.
→ **Seuils de surcharge** dans `config.yml`, avec les valeurs actuelles comme valeurs de départ.
→ **Journal** : équiper, déséquiper, jeter, ramasser, utiliser → journal objets (§6).
→ ~~❓ **Arme équipée et arme en main** : l'emplacement Arme sert d'**étui** (l'arme est portée, visible) et on la
**dégaine** dans la main par une touche ? Ou l'emplacement Arme suffit à combattre ? À trancher avec le combat (§9).~~
→ ✅ **Tranché et codé 13/10** : étui, dégainer / rengainer par la touche **Z** ; `weapon(p)` ; le Kenjutsu exige une
arme. Les autres propositions ci-dessus sont codées (KO / porté / étourdi = lecture seule ; seuils dans
`config.yml` ; journal), sauf « utiliser » au journal.

## 2.6 Règles serveur

→ Toute action de sacoche est liée au personnage, **validée côté serveur**, journalisée quand elle compte.

---

# ✋ 3. Ramasser les objets

> RedLine : *Picking up items*. **Cible : validé 13/10** (plus de ramassage automatique, ramassage par une touche,
> aura de couleur selon la rareté, disparition après X min) ; les valeurs exactes sont des propositions.
>
> ✅ **Fait le 13/10 (codé, non publié)** — touche **F** (prise seulement quand un objet est visé), invite
> « [F] Ramasser Kunai ×3 », vérifications serveur (3 blocs, vue, cadence, état, place avec fusion, poids ≤ 130 %),
> aura à 5 raretés (or = Légendaire, braises), disparition 10 / 15 min, rouleaux uniques jamais détruits — fiche
> [`gameplay/INVENTAIRE_ET_COSMETIQUES.md`](./gameplay/INVENTAIRE_ET_COSMETIQUES.md) §5-7.

## 3.1 Aujourd'hui chez Reborn

- Ramassage **automatique** de Minecraft (on marche sur l'objet).
- On **jette** depuis la sacoche (clic en dehors de la fenêtre).
- Pas de halo de rareté au sol, pas de délai de disparition propre à Reborn (celui de Minecraft).
- Les objets RP ont déjà une **rareté** côté serveur (`RpRarity` : Commun, Rare, Épique, Légendaire).

## 3.2 Chez RedLine

Ramasser avec **E** ; sac plein ou trop lourd = l'objet reste au sol + notification « Libérez de la place » ; jeter
en glissant hors de la fenêtre ; **disparition après X minutes** (non chiffré).

## 3.3 Cible — **validé 13/10**

### 1. Plus de ramassage automatique

→ Marcher sur un objet ne le ramasse plus.

### 2. On ramasse l'objet

→ Il faut **viser l'objet et appuyer sur une touche** pour le ramasser : la touche **F** (décision du 13/10).
→ Pas de case libre : l'objet **reste au sol** et une notification apparaît en bas à droite (« Sacoche pleine »).
→ Trop lourd : l'objet est **ramassé quand même**, et le personnage ralentit (§2.4) — **jusqu'à 130 %** de la
limite (décision du 13/10) ; au-delà il reste au sol (« Trop lourd — 2,4 kg de trop ») ; une pile peut être ramassée
en partie.

### 3. Une aura selon la rareté

→ Chaque objet au sol porte une **aura de couleur** selon sa rareté, pour le repérer et l'estimer d'un coup d'œil.

### 4. Disparition

→ Un objet au sol **disparaît après X minutes**.

## 3.4 Propositions, à valider

→ ❓ **Touche** : une touche dédiée, réglable (E est déjà la Sacoche, R le mode interaction). Proposition : **F**
(l'échange main gauche de Minecraft sert peu en RP) ; ou ramasser depuis le mode interaction (R + clic).
→ **Ramassage court** : ~0,5 s, interrompu si l'on est frappé ; impossible en KO, Porté, Étourdi (§1.5).
→ **Couleurs d'aura** (halo fin, discret, DA §6 règle 9) : Commun gris clair · Rare bleu · Épique violet · Légendaire
or. À valider en maquette.
→ **Délai de disparition** : **10 min** par défaut (`config.yml`), assez pour revenir chercher un objet tombé en
combat, assez court contre les dépôts sauvages.
→ ❓ **Objets uniques** (parchemins, objets de rang S, futures reliques) : ils **ne disparaissent jamais** au sol, ou
ils **retournent** à leur source (fonds de la bibliothèque, réserve staff) au lieu d'être détruits — sinon un
parchemin unique serait perdu pour tout le serveur.
→ **Jeter une pile** : demander la quantité (« Combien veux-tu jeter ? »), comme RedLine pour l'argent.
→ **Journal** : ramassé / jeté / disparu (§6), pour retrouver un objet perdu ou une duplication.

---

# 🖥️ 4. HUD

> RedLine : *Basic HUD & Exploration*. **Proposition, à valider point par point.**

## 4.1 Aujourd'hui chez Reborn

- **Vitals de base** (PV, chakra, endurance) : **inchangés** par décision du 08/10.
- **HUD combat discret** : endurance en ensō autour du viseur, chiffres de dégâts, combo, icônes de recharge
  (`mod-hud/…/combat/`) ; HUD du KO, de l'ATA, du duel (subtils, DA §6 règle 9).
- **Éditeur de HUD** (touche H) : déplacer, redimensionner, masquer chaque élément ; presets.
- Viseur, noms au-dessus des têtes (4 styles), chat en panneau, carte en M.
- **Faim** : la jauge vanilla est masquée (mixin du HUD) et le serveur garde la saturation pleine en permanence
  (`display.auto-saturation`) ; le panneau vitals (`runtime/VitalsHud`) affiche tête, vie, chakra, endurance et
  l'icône vocale (muet / parle).
- La **boussole** n'existe que dans la carte du monde (M).
- Pas de boussole sur le HUD, pas de zone d'effets (buffs, malus), pas de ryo sur le HUD (seulement dans l'échoppe et
  l'Échap), pas de système de notifications générique (seulement des messages locaux : Poste de garde, échoppe,
  bibliothèque).
- Fiche : [`technique/MOD_HUD.md`](./technique/MOD_HUD.md).

## 4.2 Chez RedLine

En haut à gauche, PV, endurance et faim près du niveau ; endurance qui repart après **5 s** sans usage ; faim à 15 %
= plus de repas possible et malus de dégâts, à 0 % = ni sprint ni dash, caméra qui tangue ; **boussole horizontale**
en haut ; argent en bas à droite ; **zone d'effets** sous la vie.

## 4.3 L'écart

Pas de boussole, pas de zone d'effets, pas de faim. Le niveau n'est **jamais** affiché chez nous (décision du 10/10).

## 4.4 Cible proposée

→ **Vitals** : on ne touche pas (décision du 08/10).
→ **Boussole horizontale facultative** (élément de l'éditeur de HUD, masquée par défaut ?), qui porte le **marqueur
personnel** posé sur la carte (§36) et la direction d'un objet d'orientation (§49).
→ **Zone d'effets** sous les vitals : une petite icône + un minuteur par effet (ATA, pilule, repas, poison, verrou de
soin §26), au style du HUD subtil.
→ **Ryo** : pas d'affichage permanent ; le montant se voit dans la sacoche (ou au survol de la bourse, §19).
→ **Niveau** : jamais affiché (décision du 10/10).
→ **Faim** : voir §12 (proposition : pas de faim punitive, des consommables à effets).

---

# 🧑‍🏫 5. Commandes staff

> RedLine : *Staff commands*. **Proposition, à valider point par point.**

## 5.1 Aujourd'hui chez Reborn — le Poste de garde (F7, `/garde`)

- **Alertes** (combats hors zone, `/aide`, signalements, RPK) avec boutons d'action ; **fiche joueur** (personnage,
  PV, chakra, ryo, temps de jeu, ping, état KO / ATA, comptes liés, casier) ; actions : **téléporter, ramener, geler,
  observer, réveiller, lever l'ATA** ; **chat staff** ; **journal** ; aide-mémoire des commandes.
- **Sanctions** : 9 infractions RP, escalade par récidive sur 90 jours, preuves jointes, `/pardon`.
- **Infirmerie** : blessés, zones (entraînement, repos, hôpital) sur la carte, lits, réglages à chaud.
- Ambiance et outils : `/vanish`, `/cinematic`, `/meteo`, `/ost`, `/zonerp`, `/grade`, `/xp`, `/stats give`,
  `/parchemin`, `/execution`, `/ryo`, `/sacoche give|reset`, `/carte set`.
- Grades staff : helper < modo < admin < owner (permissions en jeu, **pas encore synchronisées** avec le panel web).
- Fiche : [`staff/GUIDE_STAFF_EN_JEU.md`](./staff/GUIDE_STAFF_EN_JEU.md).

## 5.2 Chez RedLine

Toutes les commandes ciblent l'**identifiant** (`tp`, `bring`, `freeze`, `mute`, `kill`, `revive`, `heal`, `damage`,
`invisible`, `invsee`) ; **annonce globale** (bandeau en haut, temporaire) et **annonce à un seul joueur** (scène,
convocation) ; **écran noir forcé** ; **tenue forcée** ; emote forcée (plus tard) ; suppression / remise à zéro d'un
personnage (les cosmétiques de compte restent) ; **séisme** (annonce + écran qui tremble, branché sur les mines et
passages nommés) ; **notes staff** (texte, auteur, date, suppression selon droits, gardées si le nom RP change). Chaque
commande est journalisée.

## 5.3 L'écart

| RedLine | Reborn |
|---|---|
| tp, bring, freeze, invisible, revive | ✅ Poste de garde, `/vanish`, réveiller |
| heal, damage, kill | ✍🏻 soins et `/ata` ; pas de commande directe de dégâts |
| mute | ✅ sanctions |
| invsee (sacoche) | ✍🏻 `/voirsac` (14/10, non publié) : lecture Modérateur, prise Admin |
| Annonce globale / personnelle | ✍🏻 `/annonce`, `/annoncejoueur` (14/10) ; bandeau du mod à faire (repli : titres vanilla) |
| Écran noir forcé | ✍🏻 `/noir` (14/10) ; écran du mod à faire (repli : Cécité + Obscurité) |
| Tenue forcée, emote forcée | ✍🏻 `/tenue` (emplacement cosmétique `TENUE` ; l'apparence de skin de l'échoppe : non), `/emoteforcee` (14/10) |
| Supprimer / remettre à zéro un personnage | ✍🏻 `/persoreset`, `/persosuppr` (14/10) : confirmation + sauvegarde |
| Gel (*freeze*) | ✍🏻 persistant depuis le 14/10 (`/geler`, `gels.yml`) |
| Séisme | ✍🏻 `/seisme` (14/10) : grondement serveur, secousse du mod à faire ; passages nommés : plus tard |
| Notes staff | ✍🏻 `/note` + Poste de garde (14/10), par N° |
| Cible par identifiant | ✅ `#N°` partout (13/10) |

## 5.4 Cible proposée

→ **Toutes les commandes acceptent le N° de personnage** (`#82545`), §1.
→ **Annonce** : `/annonce <message>` (bandeau en haut au centre, temporaire, son, DA Reborn) et `/annonce joueur <N°>
<message>` (une seule personne : « Vous vous réveillez dans un lieu inconnu… », « Viens en vocal staff »).
→ **Écran noir forcé** : `/noir <N°> [secondes]` (réutilise le noir de la scène de mort ; enquêtes, enlèvements).
→ **Voir la sacoche** d'un joueur, en lecture (`/sacoche voir <N°>`), depuis la fiche joueur.
→ **Tenue forcée** : prison, uniforme d'institution, event (`/tenue <N°> <tenue>`) ; **emote forcée** plus tard.
→ **Personnage** : `/personnage reset <N°>` (stats, techniques, sacoche, ryo ; les cosmétiques de compte restent) et
`/personnage supprimer <N°>` (libère le slot), avec confirmation.
→ **Séisme / glissement de terrain** (après la beta) : annonce + écran qui tremble, ouvre ou ferme des **passages
nommés** (grottes, mines, sentiers) et sert la rotation des gisements (§23).
→ **Notes staff** sur un personnage : texte, auteur, date, visibles des staffs selon leur grade, suppression selon
droits, rattachées au N° (survivent à un changement de nom RP).
→ **Tout est journalisé** (staff, cible, action, heure) — c'est déjà le cas du journal du Poste de garde.

## 5.5 Codé le 14/10 (serveur, non publié)

Demande de l'utilisateur (14/10) : « staff command et les logs : il faut que **tout** soit référencé et traçable,
quel que soit le grade ». Côté **ShinobiCore** (`staff/outils/`), toutes les commandes ciblent un pseudo, un nom RP ou
un `#N°`, et **chaque exécution est journalisée** (la commande tapée et son effet, catégorie `STAFF`) :

| Commande | Grade | Ce qu'elle fait |
|---|---|---|
| `/annonce <message>` · `/annoncejoueur <cible> <message>` | Modérateur · Helper | Annonce globale / personnelle (canal `reborn:annonce`) |
| `/aller`, `/ramener`, `/retour [cible]` | Helper · Modérateur | Aller, ramener, revenir / renvoyer à la position d'avant |
| `/geler <cible> [motif]`, `/degeler` | Modérateur | Gel **persistant** : ni déplacement, ni coup, ni interaction |
| `/voirsac <cible>` | Modérateur (prendre : Admin) | Barre, sac, équipement, en coffre de lecture |
| `/noir <cible\|tous> [s\|off] [message]` | Modérateur (`tous` : Admin) | Écran noir forcé (canal `reborn:ecran`) |
| `/seisme [rayon\|tous] [force] [s] [message]` | Admin | Écran qui tremble + grondement ; `tous` = annonce globale |
| `/tenue <cible> <id\|main\|retirer>` | Modérateur | Emplacement `TENUE` imposé et verrouillé, ancienne pièce rendue à la levée |
| `/emoteforcee <cible> <emote\|stop>` | Modérateur | Emote jouée par la cible |
| `/persoreset`, `/persosuppr <cible\|#N°> [confirmer]` | Admin | Remise à zéro / suppression, sauvegarde avant |
| `/note <cible\|#N°> [ajouter\|suppr]` | Helper (supprimer : auteur ou Admin) | Notes staff rattachées au N° |
| `/modestaff` | Helper | Mode staff (§1.7) |
| `/journal …` | Helper | Journal universel (§6.4) |

Les mêmes actions existent en boutons au Poste de garde (protocole : [`technique/PLUGINS_REFERENCE.md`](./technique/PLUGINS_REFERENCE.md)
§4 bis, écrans du mod en cours). **Reste** : écrans du mod (bandeau d'annonce, écran noir, secousse, plaques, journal,
notes) ; apparence de skin imposée ; séisme branché sur des passages nommés ; synchronisation des grades avec le panel web.

---

# 🪵 6. Journaux (*Logs*)

> RedLine : *Logs System*. **Proposition, à valider point par point.**

## 6.1 Aujourd'hui chez Reborn

- **Journal staff** (`staff-journal.yml`) : sanctions, téléportations, `/ata`, `/zonerp`, `/hopital`, soins, grades,
  RPK — filtrable au Poste de garde.
- **Journal d'agression** (`aggression-log.txt`, qui a frappé en premier, où), **RPK** (`kill-log.txt`), **duels**
  (`duel-log.txt`), **bibliothèques** (dépôts, prises, vols), sanctions (`sanctions.yml`).
- **Aucun journal économique** (ryo gagnés, dépensés, donnés) ni **d'objets** (obtenus, donnés par le staff, jetés,
  ouvertures de coffres).
- Le casier et le journal ne sont **pas** dans la base du panel web (module `incidents` vide).

## 6.2 Chez RedLine

Tout est journalisé **côté serveur**, horodaté, rattaché à l'identifiant et **requêtable par identifiant** :
connexions, changements d'état, sacoche (ramassé, jeté, équipé, utilisé), objets (obtenus, donnés ou supprimés par le
staff), combat (dégâts, KO, relève, garde brisée, contre), économie, interactions, staff, système (reset, achats).
Règle : **aucune action silencieuse**. Le vocal n'est **jamais** journalisé en texte.

## 6.3 Cible proposée

→ **Un journal par personnage** (N°), requêtable au Poste de garde : connexions, états (§1.5), **économie** (ryo
gagnés / dépensés / donnés, par qui), **objets** (obtenus, donnés ou retirés par le staff, ramassés, jetés, disparus),
combat (KO, relève, RPK), interactions (porter, fouiller, échanger, voler), staff.
→ Format d'une ligne : N°, type d'action, données, horodatage ; si action staff : staff + cible.
→ **Aucune action silencieuse** sur l'argent et les objets — prérequis de la banque (§20), du trésor de faction
(§37), de l'échange (§16) et de toute enquête sur une duplication.
→ **Le vocal n'est jamais journalisé** (à écrire dans le guide staff).
→ Plus tard : remonter le journal et le casier dans la base du panel web.

## 6.4 Codé le 14/10 (serveur, non publié) — **décision : tout est journalisé, quel que soit le grade**

→ **Journal universel** (`ShinobiCore/journal/`, service `JournalService` publié pour les add-ons) : chaque entrée =
horodatage, **catégorie**, action, **acteur** (N°, pseudo, nom RP, grade staff au moment de l'action), **cible**
éventuelle, détail, lieu. Catégories : Joueur, Inventaire, Objet, Combat, KO / mort, Duel, Économie, Interaction,
Bibliothèque, Staff, Commande, Tchat, Système.
→ **Stockage** : un fichier par catégorie et par jour (`plugins/ShinobiCore/logs/<categorie>-AAAA-MM-JJ.log`, rétention
90 jours réglable), écriture sur un fil dédié (jamais bloquante) ; **index mémoire** des 20 000 dernières entrées
(relu au démarrage) pour le Poste de garde ; une recherche plus ancienne relit les fichiers.
→ **Tous les anciens journaux y passent** : agressions, RPK et mort RP, KO (`KoJournal`), duels, objets
(`inventaire-…` garde son nom), journal du Poste de garde (gardé aussi dans `staff-journal.yml` pour l'onglet
existant), bibliothèques, chat staff, alertes.
→ **Trous bouchés** : connexions / déconnexions (durée, « déconnecté en KO / en duel »), expulsions, choix / création /
suppression de personnage, niveau, mode de jeu, téléportations par commande, **chaque coup entre joueurs**, morts
vanilla, **chaque mouvement de ryo** (crochet unique sur le personnage : échoppe, staff, reset — aucune action
silencieuse), objets obtenus, équipés, utilisés, **coffres** et inventaires d'autrui, prises staff, tchat, et
**toutes les commandes** de tout le monde (vanilla comprise : `/tp`, `/gamemode`, `/give`…), de la console, du RCON
et du panel web.
→ **Consultation** : `/journal [#N°] [catégorie] [j:<joueur>] [h:<heures>|d:<jours>] [p:<page>] [texte]` et le
Poste de garde (filtres catégorie / N° / joueur / période / texte, pagination — écran du mod en cours).
→ Le vocal n'est jamais journalisé. **Reste** : écran du journal au Poste de garde (mod), remontée au panel web.

---

# 🏃 7. Déplacements

> RedLine : *Player movements*. **Proposition, à valider point par point.**

## 7.1 Aujourd'hui chez Reborn

- Marche, course et saut de Minecraft ; **Naruto run** ; **dash** (V), **double saut**, **saut de chakra** (chargé),
  **saut mural**, **escalade**, **glissade et roulade** (voie de mobilité n° 2), grappin et tyrolienne ; **voies de
  mobilité** débloquées par des parcours (`/trainingground`) ; endurance en ensō (`ShinobiAbilities/…/mobility/`).
- **Dégâts de chute désactivés** pour tout le monde (`MobilityListener`).
- Interdits en ATA : courir, course de chakra, dash, double saut, saut de chakra ; en KO : tout sauf ramper.
- Démarches RP (menu « . »), postures (`/assoir`, `/allonger`, `/ramper`).
- ⏳ **Trois styles de course** prévus au Sprint 2.
- ✍🏻 **Codé 14/10 (non publié) — les trois allures** de RedLine : **marche** par défaut (× 0,75, lente, RP), **trot**
  tant que la touche de sprint est maintenue (× 1,15, gratuit), **sprint** sur un **double appui** (× 1,55, 20
  d'endurance par seconde ≈ 5 s, essoufflé jusqu'à 30 %) ; même endurance que le combat (durée qui suit la Vigueur) ;
  barre fine au-dessus de la barre d'action **seulement** en sprint et en récupération, traînée blanche ; vitesse posée
  par le serveur et prédite par le mod ; coupé en Chargé, ATA, étourdi, en portant quelqu'un ; neutre en KO, porté,
  course ninja. Fiche [`gameplay/DEPLACEMENTS.md`](./gameplay/DEPLACEMENTS.md).

## 7.2 Chez RedLine

Marche, petite course (Shift, gratuite), **sprint** (double Shift, ~5 s, barre qui n'apparaît qu'en sprint, traînée
de fumée), saut, décélération douce, tête qui suit la caméra ; chute ≤ 5 m = **roulade** sans dégâts ; dash au sol et
en l'air sans grande inertie ; double saut à débloquer ; tout bloqué en KO, porté, étourdi, faim nulle.

## 7.3 Cible proposée

→ On garde notre mobilité (plus riche) ; la **jauge qui n'apparaît que pendant l'effort** est déjà notre choix.
→ **Dégâts de chute** : les rétablir au-delà d'un seuil (~5 blocs, réglable), avec une **roulade de réception**
sans dégâts en dessous (la roulade de la voie n° 2 existe ; animation au catalogue
[`gameplay/ANIMATIONS_BASE.md`](./gameplay/ANIMATIONS_BASE.md)). ❓ Aujourd'hui personne ne prend de dégâts de chute.
→ Les **trois styles de course** (Sprint 2) suivent la règle RedLine : pas d'inertie forte, pas de saut à longue
distance gratuit.
→ Tout déplacement avancé est refusé selon l'état (§1.5) et la surcharge (§2.4).

---

# ⚔️ 8. Système de combat

> RedLine : *Combat System* (mode combat, logique d'arme). **Proposition, à valider point par point.**

## 8.1 Aujourd'hui chez Reborn

- Pas de « mode combat » sur une touche : un indicateur visuel s'allume en combat (`combat/CombatState`), la **barre
  de techniques** est séparée de la barre d'objets ; le HUD combat est discret (décision du 08/10).
- M1 **Taïjutsu** à mains nues (ShinobiCombat), M1 **Kenjutsu** par un sort MagicSpells avec coût d'endurance ;
  **5 techniques liées par objet** (`JutsuBindingStore`) ; aucune technique n'exige aujourd'hui une arme.

## 8.2 Chez RedLine

Touche G : la barre d'objets devient la barre de sorts, posture et arme en main. Le M1 dépend de l'arme équipée
(sabre, mains nues, pistolet) ; chaque compétence exige l'arme de sa classe.

## 8.3 Cible proposée

→ **Pas de mode combat** sur une touche (barre de techniques déjà séparée) — l'état « Combat » (§1.5) se déclenche au
premier coup.
→ **Logique d'arme** : M1 selon l'arme en main (déjà fait) ; **techniques de Kenjutsu exigeant une arme équipée**
(§2.5) ; techniques de Taïjutsu à mains nues.

---

# 🥋 9. Combat au corps à corps (RedLine : Bagarreur & Sabreur → Taïjutsu & Kenjutsu)

> **Proposition, à valider point par point** (à verser au lot B du benchmark, décision n° 12 en attente).

## 9.1 Aujourd'hui chez Reborn

- **M1** Taïjutsu : combo de 4 coups avec coup final ; Kenjutsu par sort. **Garde** au M2 ; **parade** (fenêtre de
  320 ms, touche C). La garde puise dans l'**endurance** : la vider = **garde brisée** (étourdi 20 ticks, lenteur) —
  ShinobiCombat (`CombatListener`, `StaminaManager`). Pas de clash, pas de coup chargé.
- Le benchmark (lot B) propose : parade avec réarmement, garde haute / basse, pression de garde, tenketsu, loadout.
- ⏳ Les **39 techniques** de la beta (12 Tai, 12 Ken, 15 Nin) : Sprints 2-3.

## 9.2 Chez RedLine

**Jauge de coups** (chaque M1 la vide, recharge après 5 s sans attaquer) ; **clash** quand deux M1 se croisent ;
**avantage au premier coup** (la cible ne peut plus enchaîner mais peut fuir) ; **M1 maintenu 3 s** = estoc
brise-garde (sonne 1,5 s) ; **garde = réserve de points** (ex. 500) qui absorbe les dégâts, garde brisée à 0
(sonné 1,5 s), recharge après 5 s, M2 avec 3 s de recharge ; **parade parfaite** = l'attaquant prend ses propres
dégâts ; M1 en course = **taille en avançant** ; petit combo aérien court.

## 9.3 Cible proposée

→ **Garde en réserve de points** : chez nous la garde puise déjà dans l'endurance ; la cible est une **jauge de garde
propre**, lisible, qui encaisse les dégâts (une technique lourde perce plus vite) et se recharge après 5 s sans coup.
→ **Parade parfaite** qui **renvoie les dégâts** du M1 à l'attaquant (et renvoie un kunai, §10).
→ **Avantage au premier coup** (anti-échange de coups simultanés) et **clash** sur deux M1 croisés.
→ **Coup chargé brise-garde** (M1 maintenu), étourdit (§1.5).
→ **Taille en course** pour engager ; combo aérien court.
→ Toutes les valeurs dans `config.yml` (« valeurs de départ » écrites dans la fiche combat).

---

# 🎯 10. Combat à distance (RedLine : Tireur → armes de jet)

> **Proposition, à valider point par point.**

## 10.1 Aujourd'hui chez Reborn

- Kunai et shuriken existent comme **objets** (`RpItemType`) et comme **effets de techniques**
  (`JutsuEffectRegistry`) ; il n'y a **pas de lancer** d'objet ni de munitions.

## 10.2 Chez RedLine

Pistolet à chargeur, rechargement (R), munitions affichées près du perso ; tir chargé 3 s (plus de dégâts,
saignement, pas de dash pendant 3 s) ; même garde ; **parade parfaite qui renvoie la balle**.

## 10.3 Cible proposée

→ **Parade parfaite qui renvoie un kunai** à son lanceur (canon, très lisible).
→ **Lancer de kunai et shuriken** comme objets (M1 avec l'objet en main, ou technique de lancer).
→ **Munitions = ce qu'on porte** : le nombre de kunai et shuriken est limité par la sacoche (poids, cases), pas par
un chargeur ; on ramasse ses projectiles au sol (§3).
→ Lancer chargé (plus de dégâts, saignement) : à étudier avec les techniques de lancer du catalogue.

---

# 💀 11. KO / mort

> RedLine : *KO / Death System* (Pain RP, exécution, agonie). **Reborn est en avance** : la cible est surtout de
> garder nos décisions. Un seul ajout proposé.

## 11.1 Aujourd'hui chez Reborn

- Hors terrain d'entraînement : **à terre 45 s** (ramper, chuchoter, `/aide` ; **Volonté** facultative) →
  **inconscient 5 min** → `/hopital` proposé (pas de rapatriement automatique) → réveil à 30 % + **ATA** (pas de
  technique, M1 à mains nues seulement) → sortie par médic ou repos RP.
- **PV persistants**, plancher de 30 % sans médic ; blessures ; soins tenus.
- **Menu d'interaction sur les corps** (R) : ausculter, porter, attraper à la gorge, fouiller, demander un RPK.
- **Mort RP** : RPK validé par le staff (la victime ne valide rien), puis jouée par le demandeur (achever,
  exécution) ; écran noir, « Assister à votre réelle fin ? », scène de mort.
- KO réappliqué à la reconnexion.
- Fiche : [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md) — à tester à deux comptes.

## 11.2 Chez RedLine

À 5 % des PV, **Pain RP** (marche lente, vignette noire) : un autre joueur peut **exécuter** (E maintenu) ou **porter
sans accord** ; à 0 PV, KO **15 min** ; relève seule à 10 min (10 % des PV, −30 % de vitesse et vision à 15 m) ;
hôpital à 15 min (50 % des PV) ou rester KO ; **déconnexion en KO : état gardé, minuteur remis à 15 min** ; 0 PV
direct = 20 s d'**agonie** où un soin relève à 5 %.

## 11.3 Cible

→ **On garde nos règles** (décisions du 06/10 et du 10/10) : pas d'exécution libre (toute mort passe par un RPK),
pas de relève à 10 % ni d'hôpital à 50 %.
→ **14/10 — Pain RP, exécution, portage, agonie repris de RedLine** (demande de l'utilisateur), emboîtés dans nos
mécaniques — fiche [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md) §1 bis, §2, §6 bis, §8 :

| RedLine | Reborn (codé le 14/10, non publié) | Écart assumé |
|---|---|---|
| Pain RP à 5 % : pas de sprint / dash / double saut, marche lente | **Douleur** ≤ 5 % (réglable) : marche ×0,6, ni course ni mobilité ninja, **ni technique ni coup** | Pas d'animation de douleur (vitesse seulement) |
| Vignette noire + léger tremblement | Bords noirs qui battent au cœur, vision troublée et dédoublée par à-coups, désaturation, vacillement de caméra, cœur et souffle, sons étouffés ; aucun HUD ajouté | Coupable par « Effets de distorsion » |
| E maintenu → exécution → KO | **F maintenue** (touche Ramasser, invite « [F] Exécuter », anneau autour du viseur ; 3 s, sans RPK) → **inconscient direct** ; animation choisie au menu R (si possédée) | F au lieu de E (E = sacoche) ; état intégré au nom RP (glyphe 痛) |
| Porter un Pain RP sans demande | **Porter (sans accord)** ; le porté **se débat avec Espace** (8 appuis), protégé 6 s après | — |
| Porter un KO sans accord | Inchangé (déjà le cas) ; un porté ne descend plus seul | — |
| Agonie : 0 PV → 20 s au sol, immobile ; un soin relève à 5 % + Pain RP | **À terre = agonie** : 45 s, on **rampe** ; Volonté ou soin → debout à 5 % = **douleur** | 45 s et reptation gardés ; l'agonie vaut aussi quand on tombe depuis la douleur |
| KO 15 min, relève seul à 10 min, hôpital 15 min à 50 % | Inchangé chez nous (5 min, `/hopital`, 30 %, ATA) | Nos règles du 10/10 |
| Exécution = KO | Exécution d'une personne **en douleur** = KO ; d'un **KO avec RPK** = mort RP (F en deux temps : confirmer, puis maintenir) | La mort reste derrière le RPK |

→ *Proposition* : **un KO qui se reconnecte reprend son compte à rebours à zéro** (on ne passe pas son inconscience
déconnecté) — RedLine §KO, benchmark « déconnexion en pleine scène ».
→ Restes connus (fiche KO §12) : technique `volonte`, animations d'exécution, vente des exécutions, coma, saignement.

---

# 🍜 12. Nourriture, satiété, régénération passive

> RedLine : *Système Nourriture / Satiété / Régénération passive — V1*. **Proposition, à valider point par point.**

## 12.1 Aujourd'hui chez Reborn

- Faim vanilla **masquée**, saturation **pleine en permanence** côté serveur : la faim n'existe pas.
- **PV persistants** hors terrain d'entraînement (décision du 06/10) : pas de régénération passive ; le repos rend
  jusqu'à 30 %, au-delà il faut un médic.
- Manger dans une **zone de repos** ajoute 2 min de repos ATA ; pilules et armoires à médicaments existent (`medic/`).

## 12.2 Chez RedLine

Satiété 0-100 % (−0,025 %/s, gelée en KO, 80 % au réveil) ; régénération **1 % des PV/s** seulement entre 95 et 100 %
de satiété, **bloquée 30 s après tout dégât** ; manger prend 3 s, on peut bouger, un coup **met le repas en pause** ;
plats à **effets génériques** (buff ou malus : régénération ×2 / ×3, satiété prolongée, poison) ; deux effets de
types différents se cumulent, **le même type est remplacé par le plus fort** ; effets **gelés à la déconnexion**,
**perdus au KO** ; à 0 % : pas de sprint, endurance −50 %. Tout est réglable ; « regarder l'existant avant de coder ».

## 12.3 Cible — **14/10 : un cuisinier joueur, de la main à la main** (v3 **validée et codée le 14/10**, non publiée)

> ~~Consommables à effets portés partout~~ puis ~~restaurant à carte, PNJ et places (v2)~~ → remplacés le 14/10 :
> « beaucoup trop mécanique et pas assez RP… un mec qui vend et prépare les aliments/plats, et les joueurs, quand ils
> ont faim, viennent manger en commandant ; le mec donne la nourriture et le RP se passe ; la consommation du plat
> prend tout de même du temps. » Fiche : [`gameplay/NOURRITURE.md`](./gameplay/NOURRITURE.md) ; maquettes
> `Maquettes/gameplan/12_nourriture/v3/` (01 à 05). Plus aucune écriture décorative (kanji, sceaux) sur ces visuels.

→ **Satiété** dans la barre or actuelle des vitals (repère à 90 %), ~2 repas par soirée ; **régénération** dès 90 %,
0,25 % des PV/s, bloquée 30 s après un coup, jusqu'à 100 % hors ATA et 30 % en ATA ; **FAIM** à 0 % : plus de sprint,
endurance −50 %, jamais de dégâts.
→ **Cuisinier = un joueur** (métier donné par le staff ou la Faction, 2-3 à la beta) : panier d'ingrédients + fourneau,
15 s pour deux plats ; il **tend** le plat (R, l'autre prend avec F), le client **tend** les ryo ; prix, commande et
conversation = RP. Pas de PNJ, pas de carte, pas de caisse.
→ **Manger prend du temps** (30 s à 1 min, deux fois plus vite assis), un coup fige le repas, effets à la fin, en
petites icônes d'objet sous les vitals. ❓ Plat **chaud 10 min** puis effets ÷2 (proposé) pour garder « on vient
manger sur place ».
→ **Pilule du soldat** seul consommable de mission (contrecoup) ; sang et Akimichi après la beta. ❓ Personne au
fourneau : ration sèche sans effet à l'échoppe (proposé).
→ Staff : `/cuisinier ajouter|retirer|liste` et une ligne dans la fiche joueur ; tout dans le journal universel.
→ Défaut relevé : la faim vanilla n'est pas tenue (l'effet SATURATION de 90 s n'est jamais renouvelé) — **corrigé**.
→ **Décisions du 14/10** : recommandations de la v3 validées — plat chaud 10 min puis effets ÷2 ; ration sèche à
l'échoppe (+25 %, sans effet, jamais au-dessus de 60 %) ; panier réservé aux cuisiniers ; payer en tendant les ryo ;
~~`/cuisinier`~~ → **cuisinier = membre d'une entreprise RESTAURANT** créée par le staff
([`gameplay/ENTREPRISES.md`](./gameplay/ENTREPRISES.md)).
→ **Codé le 14/10 (non publié)** : registre des modificateurs (premier usage de [`technique/ETATS_JOUEUR.md`](./technique/ETATS_JOUEUR.md)
§4.4), satiété dans la barre or, régénération, FAIM, cuisine au poste, repas progressif, tendre (objet ou ryo), onglet
Vivres de l'échoppe, pilule v3, entreprises — détail et écarts : [`gameplay/NOURRITURE.md`](./gameplay/NOURRITURE.md)
§13 ; captures `Maquettes/gameplan/12_nourriture/code/`.

---

# 🧬 13. États du joueur (*Player State System*)

> RedLine : *Player State System* (Idle, Combat, KO, Stunned, Guarding, Carried, Prison, Staff Mode + états spéciaux
> Logia, Haki, Granite marin). La **liste des états est validée le 13/10** (§1.5) ; les états spéciaux sont une
> proposition (§34). **14/10 : principe validé** (« structurer correctement les choses et prendre toutes les
> possibilités des clans et autres ») ; **code après le chantier nourriture**. Étude et conception complètes :
> [`technique/ETATS_JOUEUR.md`](./technique/ETATS_JOUEUR.md) (inventaire du code, inventaire Naruto par clan, modèle,
> matrice, « contourne X », migration, tests, points à trancher).

## 13.1 Aujourd'hui chez Reborn

- États **éclatés** : ≈ 70 états ou drapeaux dans une trentaine de classes de six plugins, sans lecteur commun (seul
  `KoService.isImpaired` est partagé) ; aucun état « combat » ; l'étourdi n'empêche ni coup ni technique ; plusieurs
  évasions et effets qui survivent aux crashs. Ni prison ni menottes. Détail : fiche, §2.

## 13.2 Chez RedLine

Un état courant unique par personnage, stocké et modifié par le serveur, que tous les systèmes lisent. États spéciaux :
un **Logia** est intangible sauf face au Haki, au granite marin ou dans l'eau ; tout mangeur de fruit coule ; chaque
dégât vérifie « cible intangible ? attaque qui contourne ? ».

## 13.3 Cible proposée

→ Les **8 états validés** (§1.5) dans un **service d'état unique** ; ATA, duel, méditation, scène de mort et
concentration en **modificateurs**.
→ **Prison** : nouvel état pour la police de la Faction (§37) — zone fermée, techniques interdites ; **menottes
anti-chakra** (canon Naruto, équivalent du granite marin) : objet qui pose l'état et bloque le chakra.
→ **Étiquette « contourne X »** sur les attaques, vérifiée avant chaque dégât, prête pour les défenses absolues
(§34).
→ **Conception du 14/10** ([`technique/ETATS_JOUEUR.md`](./technique/ETATS_JOUEUR.md)) : l'état principal est **résolu**
à partir de **trois couches** exclusives — **Vital** (valide, douleur, à terre, inconscient, mort), **Contrôle** (libre,
garde, étourdi, entravé, porté, contrôlé, corps vide) et **Cadre** (monde, entraînement, duel, prison, construction,
staff, hors jeu) — plus des **modificateurs** cumulables (ATA, combat, surcharge, poisons, modes, menottes… même
registre que les effets de la nourriture, §12) ; 44 **capacités** calculées par une matrice unique ; un paquet
`reborn:etat` ; une catégorie de journal `ETAT` ; migration en dix étapes sans casse, en commençant par un lot de
correctifs urgents. 17 points à trancher (fiche §11).

---

# 🤝 14. Interaction (RedLine : touche E → Reborn : mode interaction R)

> RedLine : *Interaction System (E Key)*. **Proposition, à valider point par point.**

## 14.1 Aujourd'hui chez Reborn

- **Touche R** : mode interaction (curseur, menus contextuels sur un joueur, un corps, une entité, un bloc, soi) —
  `mod-hud/…/interaction/InteractionMenus`.
- **Sur un joueur** : saluer, **se présenter** (`/rencontrer`), message privé, ami, ausculter, porter, fouiller,
  échanger, **duel**, animations, sous-menu staff.
- **Sur un corps KO** : attraper à la gorge, porter, fouiller, demander un RPK, achever, exécutions, prélever un œil,
  soins des yeux.
- **Connu / inconnu** : un personnage reste « Inconnu » tant qu'on ne s'est pas présenté (`CharacterDisplay`,
  `TabListManager`).
- ⚠️ **Constat du 13/10** : plusieurs entrées du menu envoient des commandes **qu'aucun plugin ne déclare** :
  `trade` (« Échanger »), `porter` / `fouiller` en commande directe sur un joueur conscient (seul `/corps
  porter|fouiller` existe, pour les corps KO), `lock` (« Verrouiller / Cadenas »), `manage`, `freeze`, `sanction`.
  Ces boutons ne font rien aujourd'hui.

## 14.2 Chez RedLine

Une seule touche (E), la cible valide la plus proche ; sur un joueur : **porter** (demande acceptée, on se libère
avec Espace), **fouiller** (demande acceptée, **lecture seule**), **se présenter**, inviter dans sa faction, duel ;
**capuche ou masque = nom masqué** ; menu souris (Alt + clic) : copier l'identifiant, **baisser le volume vocal d'un
joueur sans pouvoir le couper**, signaler.

## 14.3 Cible proposée

→ On garde **R** (E est la sacoche).
→ **Brancher ou retirer** les entrées mortes du menu (échanger §16, porter / fouiller un joueur conscient, cadenas,
gérer, geler, sanction).
→ **Porter un joueur conscient** sur demande acceptée (blessé qui ne peut plus marcher) ; il se libère avec Espace.
→ **Fouille consentie en lecture seule** d'un joueur conscient (contrôle de police, poste-frontière en période de
tension) ; le pillage reste réservé aux corps KO.
→ **Le masque et la capuche cachent l'identité** tant qu'ils sont portés (emplacement Tête, §2) : les autres voient
« Inconnu », même ceux qui nous connaissent.
→ **Volume vocal par joueur** (baisser, jamais couper) dans le menu R et les réglages, avec la phrase d'avertissement.
→ Inviter dans sa faction / section (§37).
→ Conditions : interaction refusée en KO, Porté, Étourdi (§1.5) ; tout est validé et journalisé côté serveur.

---

# 🤺 15. Duel

> RedLine : *Duel System*. **Décision prise le 13/10** (duel consenti n'importe où, règles et visuel) — codé, non
> publié.

## 15.1 Aujourd'hui chez Reborn

- **Duel consenti** : menu R → « Proposer un duel » (ou `/duel`), invitation 30 s (Entrée / Retour), 3-2-1, combat
  **sans séquelles** (PV et chakra d'avant rendus), défaite à 0 PV ou 0 chakra, annulé si un tiers frappe, si l'écart
  dépasse 30 blocs ou après une téléportation de plus de 16 blocs, nul à 5 min ; visuel discret ; journal
  `duel-log.txt`. Code : `ShinobiCore/…/duel/`, mod `duel/`. Fiche : [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md) §4.1.

## 15.2 Chez RedLine

Duel accepté : 5 s d'invincibilité, PV mémorisés, soin complet, seuls les deux duellistes se touchent, fin à 5 % des
PV ou 5 min, retour aux PV d'avant.

## 15.3 Cible

→ **Fait** — reste à publier et à tester à deux comptes. Écart volontaire : chez nous, un tiers qui frappe **annule**
le duel au lieu d'être ignoré.

---

# 🔁 16. Échange

> RedLine : *Trading system*. **Décision du 14/10 : pas de fenêtre d'échange sécurisé entre joueurs** (« pas RP ») ;
> on échange à la main, et un échange garanti n'existera, à la limite, **qu'à la banque** (§20). Le bouton « Échanger »
> du menu R est à retirer. La cible ci-dessous (§16.3) est **abandonnée** ; ses règles anti-duplication resservent pour la
> banque.

## 16.1 Aujourd'hui chez Reborn

- **Aucun échange sécurisé** : on jette au sol, ou le staff donne. Le bouton « Échanger » du menu R n'a pas de
  commande serveur.

## 16.2 Chez RedLine

Demande par l'interaction, notification en bas à droite, fenêtre commune ; on **ne voit pas l'inventaire de l'autre**,
seulement ce qu'il pose ; objets et monnaie ; validation des deux puis **3 s de compte à rebours** annulable.

## 16.3 Cible proposée

→ **Fenêtre d'échange** dans la DA (signature propre à l'écran), ouverte depuis le menu R, objets **et ryo** (§19).
→ On ne voit que ce que l'autre pose ; **validation des deux**, puis **3-2-1 annulable** ; un objet modifié annule les
validations.
→ Interdit en KO, Porté, Étourdi, Combat ; refusé si la sacoche d'arrivée n'a pas les cases.
→ **Liste anti-duplication** (déconnexion pendant l'échange, double clic, objet jeté pendant l'échange) et
journal économie + objets (§6).

---

# 🎙️ 17. Chat de proximité (vocal)

> RedLine : *Proximity Chat System*. **Proposition, à valider point par point.**

## 17.1 Aujourd'hui chez Reborn

- **PlasmoVoice** dans le modpack (client `2.1.16` dans le dernier manifest ; `reborn-hud` compile contre `2.1.14`) ;
  le mod détecte PlasmoVoice et affiche l'icône **muet / parle / assourdi** dans le panneau vitals
  (`voice/RebornVoiceAddon`, `VoiceState`) ; bulles de frappe au chat.
- **Plugin PlasmoVoice installé sur le serveur** (confirmé par l'utilisateur le 14/10 ; version à relever sur le panel,
  rien dans le dépôt).
- **Pas de portées** chuchoter / parler / crier gérées par Reborn ; pas de volume par joueur.
- **Chat écrit** en panneau (Général / RP / Groupe, `/me`) ; à terre, on chuchote (4 blocs).

## 17.2 Chez RedLine

Trois portées **chuchoter / normal / crier**, une touche qui cycle, petite notification « Mode vocal : … »,
indicateur discret (micro + mode + animation quand on parle), son spatialisé ; en KO on parle mais pas de Den Den
Mushi ; staff : couper localement ou globalement, forcer le mode ; **jamais de journal texte des conversations**.

## 17.3 Cible proposée

→ **Trois portées** (vérifier ce que PlasmoVoice expose, sinon un addon) avec une touche qui cycle et un indicateur
Reborn discret dans le panneau vitals.
→ **Volume par joueur** (§14).
→ En KO à terre : chuchotement seulement (déjà la règle pour le chat écrit).
→ Outils staff : couper un joueur localement ou partout (journalisé). **Le vocal n'est jamais journalisé.**
→ **Radio = une escouade** (**décidé le 14/10**, conception v2) : une seconde façon de parler, par-dessus
PlasmoVoice, avec une **oreillette** portée (emplacement Boucle d'oreille) et une **touche à soi** (B, configurable).
Quelqu'un **crée** l'escouade, ajoute des gens **à côté de lui** (menu R), et ceux qu'il a ajoutés peuvent en ajouter ;
**persistante** (déconnecté = toujours membre). En parlant à la radio on est entendu à 4 blocs, en chuchoté ; son
radio filtré par le client ; en jeu, un **élément « Escouade » de l'éditeur de HUD** ([tête] Nom de ceux qui parlent).
**À terre : radio très dégradée** (hachée). L'oreillette peut être **prise** (coupée, ou gardée pour parler à la place
du propriétaire). Le **staff peut écouter** (un bouton de la fiche joueur ou `/radio ecouter`, journalisé). Fiche
[`gameplay/RADIO.md`](./gameplay/RADIO.md), maquettes `Maquettes/gameplan/18_radio/v3/` (et `v2/` pour le menu R et le vol).
→ **Codé le 14/10** (non publié) : plugin `ShinobiRadio` (addon serveur Plasmo Voice) + mod (écran N, élément HUD
« Escouade », menu R, son radio) — [`gameplay/RADIO.md`](./gameplay/RADIO.md) §12.
→ **Portée chuchotée** : l'addon officiel `pv-addon-whisper` (Paper 26.x) donne un mode chuchoté à moitié de portée
sans rien coder ; un cycle à trois portées demande en plus une activation « crier » (même mécanique que la radio).

---

# 🐦 18. Transmissions (RedLine : Den Den Mushi)

> RedLine : *Den Den Mushi System*. **Proposition, à valider point par point.**

## 18.1 Aujourd'hui chez Reborn

- Pas de téléphone (et pas dans l'univers). `/aide` (à terre / KO, alerte les médics à 48 blocs et le Poste de
  garde), `/staffchat`, `/signaler`, `/msg`.
- « Radio moderne » en **cut list** (volière + annonce suffisent pour la beta). **Révisé le 14/10** : l'utilisateur
  demande une radio au sens canon (oreillettes) → **escouades radio**, §17.3 et [`gameplay/RADIO.md`](./gameplay/RADIO.md).

## 18.2 Chez RedLine

Un **objet** avec son propre numéro, ses contacts, ses appels manqués et passés (le perdre ou le prêter donne accès à
**son** historique) ; numéro masquable ; **numéros utiles liés à une faction** : appeler la caserne fait sonner **tous
les membres**, le premier qui décroche prend l'appel ; haut-parleur selon le mode vocal ; interdit en KO ou menotté.

## 18.3 Cible proposée

→ **Appel de service** : « appeler l'hôpital / la police / le bureau des missions » (par un **oiseau messager**, un
sceau, le menu R… — **pas par la radio**) : tous les membres **en service** de la section reçoivent
l'appel, **le premier qui l'accepte le prend**, les autres le voient pris. C'est `/aide` généralisé aux sections de la
Faction (§37).
→ **L'appel de service n'est pas la radio** (décision du 14/10) : il reste un **système à part**, à concevoir
(support RP : oiseau messager, menu R, sceau…).
→ **Radio (§17.3)** : l'oreillette garde ses données comme le Den Den Mushi — **qui la porte a accès à l'escouade**
(vol = écoute et fausse voix ; parade : le créateur exclut l'oreillette). Jamais d'historique vocal.
→ Interdit en **inconscient** ; **à terre**, radio autorisée mais très dégradée ; menotté : à voir avec §13.
→ **Codé le 14/10** (non publié) : oreillette = données sur l'objet (numéro, escouade, propriétaire, coupée), vol par le
menu R (couper / prendre), parade du créateur (exclure l'oreillette) — [`gameplay/RADIO.md`](./gameplay/RADIO.md) §12.

---

# 🪙 19. Monnaie (RedLine : Berry → Reborn : Ryo)

> RedLine : *Berry Currency System*. **Décidé et codé le 15/10 (non publié)** — réponses aux 12 questions dans la fiche
> §14, ce qui est codé §15 ; proposition du 14/10 : fiche
> [`gameplay/MONNAIE_ET_BANQUE.md`](./gameplay/MONNAIE_ET_BANQUE.md) (état des lieux du code, bourse, compte, guichet,
> échange garanti, coffre et paliers, sources / puits chiffrés, vol, journal, staff, RBCoins, 12 questions) ; maquettes
> `Maquettes/gameplan/19_monnaie/` (01 bourse dans la sacoche, 02 poser et tendre, 03 guichet, 04 coffre et paliers,
> 05 vol sur un corps, 06 staff).

## 19.1 Aujourd'hui chez Reborn

- **Ryo** : un champ `ryo` sur le personnage (`ShinobiCharacter`), pas d'objet physique ; `/ryo give|take|set` (staff)
  ; **aucune source de gain** en jeu (missions à venir) ; dépensé à l'**échoppe** (tenues 500, cartes 150, vivres 10 à
  40) ; donné de la main à la main (« Donner des ryo », `TendreService`) ; chaque mouvement journalisé (`RyoJournal`).
  Audit complet des sources et des puits : fiche §1.
- **RBCoins** : monnaie de compte (`User.rbCoinBalance`, affiché dans le launcher ; rien ne la crédite encore),
  **sans conversion** avec le ryo ([ADR 0006](./adr/0006-monnaies-ryo-et-rbcoins.md)) ; en jeu, l'Échap n'affiche
  **aucun solde de RBCoins** et sa carte ÉCHOPPE montre le ryo (« — ryo » tant que l'échoppe n'a pas été ouverte).
- La Faction propose **bourse** (sur soi, volable) et **compte** (à l'abri, au guichet du village) —
  [`gameplay/FACTION_PROPOSITION.md`](./gameplay/FACTION_PROPOSITION.md) §6.

## 19.2 Chez RedLine

La monnaie est un **objet « bourse »** empilable sans limite et sans poids, qui disparaît à 0 ; poser au sol demande un
montant ; déposer dans un conteneur avec montant, **double-clic pour tout prendre** ; échange par la fenêtre
d'échange ; boutiques, salaires, artisanat et trésors mettent la bourse à jour ; montant en bas à droite.

## 19.3 Cible proposée

→ **La bourse-objet** : le ryo porté est un objet « bourse » dans la sacoche (sans poids, une case), posable au sol
(« Combien veux-tu poser ? »), rangeable dans un coffre, **volable sur un corps** (plafond par scène prévu par la
Faction).
→ Le **compte** reste à l'abri (§20) ; la mort RP laisse la bourse sur le corps.
→ Gains et dépenses **journalisés** (§6).
→ Montant visible dans la sacoche, pas sur le HUD (§4).
→ **Détail (14/10)** : une seule bourse sur soi, sans poids, montant abrégé en or sur la case ; au sol 30 min ;
« Donner des ryo » et l'échoppe paient avec la bourse ; fouille d'un corps = sa sacoche en mode fouille, 5 000 ryo au
plus par scène (pas de plafond à la mort RP) ; Échap corrigé (bourse sur la carte ÉCHOPPE, solde de RBCoins dans
RÉCOMPENSES) — fiche §3, §8, §12.
→ **Décisions du 15/10** : solde actuel → **compte** ; bourse sans poids ; vol sur un corps **30 % de la bourse au plus**
(remplace 5 000 par scène) ; bourse au sol 30 min ; RBCoins dans l'Échap, distincts du ryo. **Codé le 15/10** (bourse-
objet, poser / ramasser / coffres, échoppe et « Donner des ryo » par la bourse, fouille 3 s et sacoche du corps, montant
en or sur la case, ligne « Sur toi », Échap) — fiche §15, captures `Maquettes/gameplan/19_monnaie/code/`.

---

# 🏦 20. Banque

> RedLine : *Bank System*. **Décidé et codé le 15/10 (non publié)** : compte, guichets (PNJ / bloc et guichetier
> joueur, entreprise BANQUE), échange garanti, coffre à paliers, solde du dimanche, testament (fiche §15). Proposition du
> 14/10 (lié à la Faction, lot F4) : même fiche,
> [`gameplay/MONNAIE_ET_BANQUE.md`](./gameplay/MONNAIE_ET_BANQUE.md) §4-§7, §9-§11 ; maquettes 03, 04, 06.

## 20.1 Aujourd'hui chez Reborn

- Rien en jeu. La Faction propose un **compte au village** (guichet du village, village allié avec 5 % de frais).

## 20.2 Chez RedLine

Banquier ou coffre-fort ; **même compte dans toutes les banques** ; liste blanche / noire d'objets ; **paliers
payants** (T1 gratuit 2 cases / 50 000 → T4 1 M, 24 cases, illimité) comme puits d'argent ; **revenu horaire à aller
retirer** à la banque ; écran : palier, solde, plafond, cases, coût du palier suivant.

## 20.3 Cible proposée

→ **Compte au guichet du village** (Faction) ; guichet allié avec frais.
→ **Solde hebdomadaire versée sur le compte, à retirer au guichet** (raison de passer en ville) ; **pas de revenu
passif** pour tous (inflation, l'argent naît des missions et du staff).
→ **Liste d'objets refusés** au coffre du compte ; **plafond de compte** qui s'agrandit contre paiement (puits
d'argent) — ❓ à décider avec la Faction (Q7, Q9).
→ Un déserteur ne peut plus atteindre le guichet de son ancien village (compte gelé de fait).
→ **Détail (14/10)** : guichet = bloc / meuble / PNJ en libre-service, ou **guichetier joueur** (entreprise BANQUE) ;
déposer, retirer, **virement au guichet seulement** (1 %, motif obligatoire), **échange garanti** = dépôt fiduciaire
des deux parties au guichet, scellé par le guichetier (c'est « l'échange garanti à la banque » de la décision du 14/10,
§16) ; coffre du compte à **paliers** 2 / 6 / 12 / 18 cases (0 / 2 000 / 8 000 / 25 000 ryo) et objets refusés
(parchemins, uniques, volés, explosifs) ; **solde du dimanche** sur le compte ; tableau sources / puits pour Konoha
(≈ +5 % de masse par semaine) ; une section « Économie » dans la fiche du Poste de garde et `/banque` — fiche §4-§11,
questions §14.
→ **Décisions du 15/10** : mort RP = compte et coffre gelés 7 j puis trésor, **sauf testament validé** (héritage vu en
RP) ; échange garanti au guichet scellé par le guichetier, 2 % ; PNJ / bloc **et** guichetier joueur ; paliers
2 / 6 / 12 / 18 cases (0 / 2 000 / 8 000 / 25 000) ; **armes acceptées au coffre sauf les armes RP légendaires /
mythiques** (liste configurable) ; virement 1 % (5 % hors village), 20 000 / jour, motif obligatoire. **Codé le 15/10**
— fiche §15-§20.

---

# 🛠️ 21. Professions et petits boulots

> RedLine : *Profession System* (professions + jobs). **Proposition, à valider point par point.**

## 21.1 Aujourd'hui chez Reborn

- **Spécialisations par permission** : médic rang 1 et 2 (`shinobicore.medic`, `.expert`) ; les **fonctions RP**
  (ANBU, conseil…) relèvent de la Faction. Pas de métiers ni d'artisanat.
- Missions : seulement des **états de service** (`fiche/ServiceRecords`) et de l'XP donnée par le staff.

## 21.2 Chez RedLine

**Une profession par personnage**, choisie au **niveau 5** à un tableau en ville (Navigateur, Charpentier, Cuisinier,
Médecin, Forgeron), permanente sauf **jeton de changement** payant ; progression propre, établis, recettes
exclusives. **Petits boulots** (mineur, bûcheron, éboueur) libres et sans progression, qui produisent les ressources
des professions.

## 21.3 Cible proposée

→ **Spécialisation** = une **fonction de section** de la Faction (médic, armurier, cartographe, intendant…) donnée en
RP, pas choisie à un niveau (niveau invisible) ; pas de jeton payant.
→ **Petits boulots** ouverts à tous : dans Naruto, ce sont les **missions de rang D** (§22).
→ Règle de design (RedLine §bateaux) : **un service rendu par un joueur coûte nettement moins cher que le même service
PNJ** (médic contre hôpital PNJ, réparation…).

---

# 🧹 22. Missions de rang D au tableau (RedLine : éboueur)

> RedLine : *Trash Collector Job*. **Proposition, à valider point par point** (décision n° 13 en attente : bureau des
> missions tenu par les joueurs, le staff, ou les deux).

## 22.1 Aujourd'hui chez Reborn

- Idées de rang D (désherber, promener les chiens Inuzuka, retrouver Tora) — [`gameplay/MISSIONS.md`](./gameplay/MISSIONS.md) ;
  aucun système.

## 22.2 Chez RedLine

Tableau d'affichage en ville → mission acceptée → **60 s** pour ramasser 10 déchets **visibles seulement par ce
joueur** (objets côté client, sans collision, positions tirées dans une liste) → barre de 1 s par déchet → compteur →
**retour au tableau** pour toucher la récompense ; **3 par jour** ; échec si temps écoulé ou déconnexion.

## 22.3 Cible proposée

→ **Le patron exact des missions de rang D automatiques** du bureau des missions : tableau, chrono, objectifs visibles
du seul joueur (pas de vol d'objectif, pas de bouchon), retour au tableau, **plafond quotidien**, petite récompense
en **ryo** et XP invisible (`XpService.reward`), état de service.
→ Les rangs C et plus restent tenus par les joueurs du bureau (modèle Solve), les rangs A et S par le staff.
→ Première source de ryo **contrôlée** du serveur.

---

# ⛏️ 23. Mineur (et séisme)

> RedLine : *Profession: Miner*. **Proposition — après la beta.**

## 23.1 Aujourd'hui chez Reborn

- Zones de ressources gérées par un **plugin tiers non versionné** ; une zone tenue par un autre village bloque la
  récolte (constat terrain, [`gameplay/MISSIONS.md`](./gameplay/MISSIONS.md) §1).

## 23.2 Chez RedLine

`/mining` : groupes de filons posés à la position du staff, activables ; **rotation hebdomadaire** par la commande
séisme (qui trouve d'abord a l'avantage, **l'information se vend**) ; cycle de 2 à 5 s qui se relance tant qu'on ne
bouge pas ; **plafond quotidien** ; **classement réservé au staff** ; pioche qui s'use.

## 23.3 Cible proposée

→ Gisements **posés par le staff**, rotation **par événement** (séisme, §5), plafond quotidien, classement staff.
→ Lien « la zone appartient au village X » (territoires, §52).

---

# 🪓 24. Bûcheron

> RedLine : *Lumberjack Job*. **Proposition — après la beta.**

→ **Aujourd'hui** : rien. **RedLine** : zones devant des arbres, butin par arbre, hache requise, usure.
→ **Cible** : même modèle que le mineur (§23), rien de spécifique.

---

# 🔨 25. Forgeron

> RedLine : *Blacksmith Profession*. **Proposition — après la beta.**

## 25.1 Aujourd'hui chez Reborn

- Pas d'artisanat ; la rareté passe par les **parchemins uniques** et les **slots du rang S**.

## 25.2 Chez RedLine

Dépend du mineur ; **plans** = recette débloquée pour toujours ; **schémas** = ingrédient rare **consommé** ; armes D à
A selon le niveau de forgeron ; **rang S légendaire ni fabricable ni cassable** ; durabilité ; réparations sans plan.

## 25.3 Cible proposée

→ **Plan permanent / schéma consommable** pour un futur artisanat (armurier de section).
→ **Armes légendaires uniques et incassables** : les **Sept Sabres de la Brume** (Kubikiribōchō, Samehada…) gérés
comme les slots S (un exemplaire, transmis par vol ou héritage).

---

# 🩺 26. Médecin (médical)

> RedLine : *Medic System*. **Proposition, à valider point par point.**

## 26.1 Aujourd'hui chez Reborn

- **Médics** par permission (rang 1 et 2) ; **paume de soin** tenue (interrompue si l'on bouge ou si l'on est frappé) ;
  **bandes de gaze** utilisables par tous (+5 % jusqu'à 30 %) ; `/soigner` (blessures) ; armoires, pilules ;
  **hôpitaux** (zones) et **lits** (meubles Nexo) ; ATA levée par un médic.
- Pas de blocage des soins en combat. ⏳ `MedicalService` (antidotes, coma) au Sprint 3.

## 26.2 Chez RedLine

Rôle **médecin** par commande ; seul un médecin relève un KO (trousse 2 usages, 10 s, annulée si l'on bouge ou est
frappé) ; **bandage** : tout le monde 10 s +5 % sur soi, médecin 5 s +20 % sur soi ou autrui ; **verrou de combat :
120 s après le dernier coup reçu d'un joueur**, aucun soin (l'environnement ne compte pas) ; établi médical.

## 26.3 Cible proposée

→ **Verrou de combat sur les soins** : bandage et paume refusés pendant **2 min** après un coup **d'un joueur** (pas
l'environnement) ; message « Tu es en plein combat… » ; minuteur dans la zone d'effets (§4).
→ **Bandage plus efficace posé par un médic sur autrui** (le médic de campagne devient tactique).
→ Le reste suit nos décisions (lien KO / médical léger, plancher 30 %).

---

# 🍳 27. Cuisinier

> RedLine : *Profession: Cook*. **Proposition — après la beta.**

## 27.1 Aujourd'hui chez Reborn

- Pas de cuisine ; **maîtrise 0-100** des techniques par l'usage et les séances (`LearnedState`).

## 27.2 Chez RedLine

Ingrédients **posés par le staff** en des lieux précis, avec réapparition (l'emplacement d'un ingrédient rare devient
un **secret qui se vend**) ; recettes communes, rares, légendaires débloquées par **parchemins de recette** ; caisses
de vivres = carburant des bateaux ; **repas de maîtrise** : de l'XP de maîtrise sur une technique choisie (3 paliers,
chacun change la technique à sa façon).

## 27.3 Cible proposée

→ **Ingrédients rares placés à la main** (herbes médicinales des médics, ingrédients des pilules Akimichi), dont
l'emplacement est une information RP.
→ **Chaque palier de maîtrise change la technique à sa façon** (zone, recharge, effet), pas seulement +X % de dégâts.
→ Écarté : la maîtrise achetée par la nourriture (la maîtrise vient de l'usage et des séances, 08/10).
→ **14/10 — le cuisinier est le cœur de la nourriture** (§12) : métier tenu par un joueur (staff ou Faction, 2-3 à la
beta) ; panier d'ingrédients (réservé aux cuisiniers) + fourneau Nexo, 15 s pour deux plats ; il donne et se fait payer
de la main à la main, fixe ses prix ; plus tard, recettes spéciales et ingrédients rares placés à la main —
[`gameplay/NOURRITURE.md`](./gameplay/NOURRITURE.md) §4.
→ **14/10 — codé** : le métier passe par les **entreprises** (type RESTAURANT, gérant / employé, fourneaux marqués par
le staff) — [`gameplay/ENTREPRISES.md`](./gameplay/ENTREPRISES.md).

---

# 🗺️ 28. Navigateur → cartographe

> RedLine : *Navigator Profession*. **Proposition — après la beta.**

→ **Aujourd'hui** : carte du monde (M) générée du vrai monde, Konoha seulement, lieux posés par le staff.
→ **RedLine** : révèle le brouillard plus largement, fabrique des **fragments de carte** qu'un autre joueur consomme ;
Log Pose réservé, routes du staff ; Log Pose éternel qui pointe un lieu enregistré (et se vend).
→ **Cible** : une **spécialisation de cartographe** (section du village) qui fabrique des **rouleaux de carte** révélant
un lieu (repaire, gisement, bibliothèque cachée) et des **objets qui pointent un lieu enregistré** (§49).

---

# 📦 29. Stockage et coffres

> RedLine : *Storage / Chest System*. **Proposition, à valider point par point** (Faction, lot F5).

## 29.1 Aujourd'hui chez Reborn

- **Aucun coffre configurable** ni permission de porte ou de zone (le « Cadenas » du menu R n'a pas de commande
  serveur).
- Les **bibliothèques** sont déjà un stockage spécialisé : membres, dépôt / prise, journal, vol, écran staff
  ([`gameplay/APPRENTISSAGE.md`](./gameplay/APPRENTISSAGE.md) §3).
- La Faction propose des **coffres-meubles** à trois droits (voir / déposer / retirer), quota de retrait, journal, vol.

## 29.2 Chez RedLine

`/storage` à la position du staff ; tailles 6 / 12 / 18 / 24 ; nom, description, **identifiant unique**, coordonnées
figées ; accès **public**, **staff** (invisible des joueurs) ou **privé** (joueurs et factions ; les non-autorisés ne
voient même pas la zone) ; droits par **rang** de faction ; **réapprovisionnement** (contenu restauré à l'identique,
minuteur qui démarre **une fois le coffre vide**) ; **éphémère** (se supprime une fois vidé) ; popup de quantité,
« tout déposer » ; **journal** des dépôts et retraits ; **`/storagelist`** : recherche, filtres, statistiques,
**téléportation**, réglages et journal depuis n'importe où.

## 29.3 Cible proposée

→ **Coffres configurables** (meubles Nexo) créés par le staff, avec identifiant, nom, taille, accès public / staff /
privé (personnages, village, section, fonction).
→ **Coffre invisible** pour les non-autorisés (cache ANBU, réserve du palais).
→ **Réapprovisionnement** et **coffre éphémère** pour les events et les caches de mission.
→ **Journal** par coffre ; **liste centrale** au Poste de garde (recherche, filtres, téléportation, réglages,
journal).
→ **Vol** sur le modèle des bibliothèques (fouille immobile, alerte, journal) — §48.
→ **Liste anti-duplication** (double dépôt, déconnexion pendant un transfert).

---

# ✉️ 30. Volière (RedLine : boîtes aux lettres)

> RedLine : *Mailbox & Letter System*. **Proposition, à valider point par point** (chantier « Communication RP »,
> Sprint 3).

## 30.1 Aujourd'hui chez Reborn

- Rien en jeu ; la volière et les annonces sont prévues au Sprint 3 ; la Faction dit que les missives restent du RP.

## 30.2 Chez RedLine

On écrit depuis une boîte aux lettres (destinataire, texte, case « signer ») ; **500 par destinataire** ; **anonyme par
défaut**, signé si coché ; **transfert** qui garde l'anonymat d'origine ; lettres gardées jusqu'à suppression ; **on ne
lit qu'à une boîte aux lettres**.

## 30.3 Cible proposée

→ **Volière** (et bureau du village) : anonyme par défaut, **sceau de signature** facultatif, **coût par destinataire**
en ryo (puits d'argent), plusieurs destinataires (coût total affiché), **lecture seulement à la volière**, transfert.
→ Support naturel des propositions de trêve (Faction, maquette 05), des convocations du Kage et du Bingo Book.

---

# ☠️ 31. Contrebande (RedLine : MMP, drogue)

> RedLine : *MMP — Drug System*. **Proposition — après la beta** (organisations indépendantes).

## 31.1 Aujourd'hui chez Reborn

- Aucune économie illégale.

## 31.2 Chez RedLine

Laboratoire **attribué par le staff à un équipage**, à **remettre en état** (10 nettoyages + ressources) ; deux machines
(10 min puis 30 min, file de 6) ; **feuilles toxiques** dans des zones qui empoisonnent ; détention = arrestation ;
vente à des **clients PNJ fixes** à **capacité partagée** (4 à 10, puis 30 min de recharge) ; **la faction qui tient
le territoire est prévenue d'une transaction, sans le lieu exact** ; consommation : +50 % de vitesse 15 s puis
contrecoup ; réglages d'accès par identifiant ou faction.

## 31.3 Cible proposée

→ **Contrebande sans drogue** : herbes toxiques ou **pilules interdites**, récoltées en zone dangereuse ; **receleurs
fixes à capacité partagée** ; **alerte au village qui tient la zone, sans la position** (la police enquête) ; repaire
attribué par le staff, à remettre en état.
→ Les **pilules de combat à contrecoup** suivent le modèle effet + contrecoup (§12).
→ Écarté : la drogue consommable telle quelle (hors ton du serveur).

---

# 🌳 32. Progression (RedLine : classes et arbre de compétences)

> RedLine : *Class / Skill Tree*. **Nos décisions priment** ; deux ajouts proposés.

## 32.1 Aujourd'hui chez Reborn

- **Pas de classes ni d'arbre** : **6 stats** (Taïjutsu, Kenjutsu, Ninjutsu, Contrôle, Vigueur, Chakra), points
  venus de l'**XP invisible** (1 niveau = 1 point) ; **apprentissage libre** par parchemins ; **9 grades** RP donnés en
  RP (`/grade`), qui rapportent du **Mérite** (usage à définir) et des bonus passifs (❓ à garder ou non) ;
  **maîtrise 0-100** par technique ; techniques réservées à des fonctions (ANBU) ; jetons de réinitialisation liés au
  compte ; plafond quotidien de l'XP **d'entraînement** seulement.
- Fiches : [`gameplay/STATS_ET_PROGRESSION.md`](./gameplay/STATS_ET_PROGRESSION.md),
  [`gameplay/APPRENTISSAGE.md`](./gameplay/APPRENTISSAGE.md).

## 32.2 Chez RedLine

Classe choisie au **niveau 3** (bagarreur, sabreur, tireur) ; points par niveau partagés entre onglets (classe,
Haki, agilité, fruit, spécial) ; sous-classes et Haki par **tirage payant** ; compétence « Spécial » liée à un grade ;
rareté S à D ; maîtrise en 3 paliers par repas ; XP passive avec **plafond sur 24 h** et multiplicateurs ; commandes
staff ; **classement réservé au staff**.

## 32.3 Cible proposée

→ On garde notre modèle (décisions du 23/09, 08/10, 10/10) ; classes, arbre à dépenser et tirages payants écartés.
→ **Classement de progression pour le staff** au Poste de garde (niveaux, points, techniques apprises) : repérer un
farm ou un oubli.
→ **Plafond global d'XP par 24 h** en plus du plafond d'entraînement.
→ Décisions toujours en attente : usage du **Mérite** (n° 4), **bonus passifs de grade** (n° 5), points et plafond
des stats (n° 7).

---

# 🃏 33. Deck de techniques

> RedLine : *Spell Deck System*. **Proposition, à valider point par point** (benchmark B8).

## 33.1 Aujourd'hui chez Reborn

- **5 techniques liées** par objet tenu (`JutsuBindingStore`, `JutsuHotbarManager`) ; menu `/menu` ; pas de presets.

## 33.2 Chez RedLine

Bibliothèque des sorts par catégorie ; **2 decks** par défaut, changement par une touche qui met **1 s de recharge
sur toute la barre** ; favoris en catégories personnalisées ; **mode rapide ouvrable en marchant ou en combat** : on
bouge, on réorganise, **on ne lance pas** ; un sort déposé dans la barre a 1 s de recharge.

## 33.3 Cible proposée

→ **Presets de techniques** (2 par défaut), changement en combat contre une **recharge globale courte** (1 s).
→ Écran rapide qui **n'immobilise pas** mais **interdit de lancer** tant qu'il est ouvert.
→ Catégories de favoris (« Entraves », « Mobilité ») : plus tard.

---

# 👁️ 34. Fruits du démon, Logia, Haki, granite marin → équivalents chakra

> RedLine : *FDD & Logia*. **Proposition — après la beta** pour l'essentiel.

## 34.1 Aujourd'hui chez Reborn

- Rien d'équivalent à une défense absolue (Kamui, Susanoo en cut list). Les **kekkei genkai** passent par les clans
  (18 sorts claniques prévus, Sprints 2-3) ; **dōjutsu** : Byakugan « comme dans l'anime » (ShinobiSense) et
  chirurgie des yeux ; **démons à queues** dans ShinobiTail.

## 34.2 Chez RedLine

Logia intangible en permanence sauf face au **Haki**, au **granite marin** ou dans l'eau ; tout mangeur de fruit coule ;
vérification systématique avant chaque dégât.

## 34.3 Équivalents proposés

| RedLine | Équivalent Reborn proposé |
|---|---|
| Fruit du démon (pouvoir rare, un par personnage) | **Kekkei genkai** de clan, **dōjutsu** greffé, **jinchūriki** |
| Logia (intangibilité) | **Hydrification** (clan Hōzuki), **Kamui** (cut list) : intangible sauf exception |
| Haki (contourne l'intangibilité) | **Chakra de nature opposée** (Raiton contre l'hydrification), **senjutsu** |
| Granite marin (arme anti-pouvoir) | **Menottes anti-chakra** (prison, §13), **Samehada** (absorbe le chakra) |
| L'eau affaiblit | Chaque défense a son **contre canon** écrit dans la technique |

→ Règle technique : une **étiquette « contourne X »** sur les attaques, vérifiée avant chaque dégât (cible
intangible ? attaque qui contourne ?), prête dès qu'une défense absolue arrive.

---

# 💰 35. Chasse au trésor → caches et reliques

> RedLine : *Treasure System*. **Proposition — après la beta.**

## 35.1 Aujourd'hui chez Reborn

- Rien ; le staff place des rouleaux dans des coffres en event ; tirage des bibliothèques ; slots S.

## 35.2 Chez RedLine

Pelle à 3 usages, 30 % de réussite ; points de fouille par zone ou posés à la main ; coffre de tier 1 / 2 / 3
(85 / 10 / 5 %) qui **doit être porté à la main** (pas en inventaire, **lâché si l'on est frappé**, disparaît après
2 h) jusqu'à un point d'ouverture ; tirage en trois couches ; **objets uniques** retirés de la réserve une fois
obtenus ; relances payantes ; emplacements trahis par des **indices visuels** au sol.

## 35.3 Cible proposée

→ **Le coffre porté à la main, lâché si l'on est frappé** : poursuites, escortes, embuscades — parfait pour les
**missions de convoi**.
→ **Objets uniques retirés de la réserve** une fois obtenus (reliques, armes légendaires §25).
→ **Indices au sol** plutôt que fouille aveugle.
→ Écarté : les relances payantes.

---

# 🗺️ 36. Carte

> RedLine : *Map System*. **Proposition, à valider point par point.**

## 36.1 Aujourd'hui chez Reborn

- **Carte du monde (M)** générée depuis le vrai monde, **Konoha seulement** : zoom, libellés de zones, marqueurs
  (lieux, PNJ, portes), flèche du joueur, boussole dans la carte (`map/WorldMapScreen`) ; lieux posés par le staff
  (`/carte set|del|tp|liste`, `places.yml`), « à l'œil », à corriger en jeu.
- Pas de brouillard, pas de marqueur personnel, pas de zones survolables.

## 36.2 Chez RedLine

Touche M ; **brouillard** levé en explorant ; fragments de carte ; **marqueur personnel** qui apparaît sur la
boussole (pas de minicarte) ; **légendes** posées par le staff ; **zones survolables** avec une info (territoire de
l'équipage X, ressource rare) ; zones révélées par un objet-carte.

## 36.3 Cible proposée

→ **Marqueur personnel** posé sur la carte, suivi sur la **boussole** du HUD (§4).
→ **Zones survolables** : territoire d'un village ou d'une organisation, terrains d'entraînement, zones de repos,
hôpitaux, zones de ressources.
→ **Zones révélées par un rouleau de carte** (§28).
→ ❓ **Brouillard** : seulement pour les **pays étrangers** (un ninja connaît son pays) — à discuter.
→ Autres villages : avec leur build (cut list beta).

---

# 🏯 37. Menus — Faction

> RedLine : *Faction System* (ouvert par Échap > Faction). **Proposition maquettée, en cours de validation** :
> [`gameplay/FACTION_PROPOSITION.md`](./gameplay/FACTION_PROPOSITION.md) (18 questions ouvertes).

## 37.1 Aujourd'hui chez Reborn

- **Pas de système de faction** : le village est une chaîne sur le personnage, `Rank` est le grade RP. Les **escouades
  Genin** existent (ShinobiLearning `/squad`) ; les bibliothèques ont des membres.
- Proposition Faction (maquettes) : villages et organisations, **sections**, **fonctions** (koma de shōgi), quotas et
  délais de promotion, trésor commun, bourse et compte, coffres, réputation, diplomatie, Bingo Book, désertion.

## 37.2 Chez RedLine

Factions **créées par le staff** (nom, **tag de 3 lettres**, type) ; réputation de faction (guerres de territoire),
territoires, membres ; **zones de faction** (bâtiments, portes, coffres, zones restreintes) avec permissions du staff ;
**chef nommé par le staff**, qui crée des **rangs** et donne des permissions par rang (telle porte, tel coffre) mais
**ne crée pas de zones** ; tir allié réglable par chacun ; ouverture par **Échap > Faction**.

## 37.3 Cible proposée

→ La proposition Faction, **plus** quatre reprises : (1) **portes et zones de faction** à accès réglé (quartier ANBU,
réserve du palais, poste de police) ; (2) une **renommée du village** distincte de la réputation personnelle ; (3) une
entrée **Faction dans l'Échap** en plus de la touche J ; (4) un **tag court** (KON, SUN, KIR, KUM, IWA, AME) dans la
tablist et les journaux.
→ **Prison** et menottes pour la police (§13).
→ Ordre de réalisation : journal économie + objets (§6) **avant** le trésor.
→ **Entreprises (14/10, codé côté staff)** : restaurant, forge, échoppe — nom, type, responsable, membres (gérant,
employé), postes ; créées et gérées par le staff (`/entreprise`) en attendant un onglet **Entreprise** du menu Faction
où le gérant recrute et gère (conçu, non codé) — [`gameplay/ENTREPRISES.md`](./gameplay/ENTREPRISES.md) §4.

---

# 📈 38. Caractéristiques

> RedLine : *Characteristics System*. **Proposition, liée à la décision n° 5.**

→ **Aujourd'hui** : bonus passifs de grade (PV, chakra, endurance, +1 à +3 aux stats), ❓ à garder ou non ; chiffres
cachés (29/09).
→ **RedLine** : bonus **automatiques** par palier de niveau (poids porté, PV, dégâts, cases, vitesse) et bonus de race,
dans un arbre visuel **sans choix**, gris tant que verrouillé.
→ **Cible** : si les bonus de grade restent, les montrer **en mots** dans la fiche shinobi (« le grade de Chūnin te
rend plus endurant ») ; pas d'arbre chiffré.

---

# 💳 39. Boutique en argent réel (abonnements, cosmétiques, tirages, remises à zéro)

> RedLine : *Shop (Real Money Store)*, *Roll System*, *Subscription Tab*, *Cosmetics Tab*, *Reset System* (sections
> recopiées deux fois chez eux). **Nos décisions priment.**

## 39.1 Aujourd'hui chez Reborn

- **RBCoins** sur le compte (`rbCoinBalance` dans l'API, produits et achats en base) ; **pas de boutique RBCoins en
  jeu**, la page boutique du launcher est un emplacement réservé ; Stripe en v1.1.
- **VIP** : permission `reborn.premium` **dormante** (elle donne 4 slots de personnage au lieu de 2).
- Jetons de réinitialisation des stats **liés au compte** ; exécutions « à la boutique » (non branché).

## 39.2 Chez RedLine

**VIP** (boost d'XP, file prioritaire, message de connexion, relances) et **VIP+** (serveur de combat privé, moins
d'usure, skins, récompenses mensuelles) ; **cosmétiques de compte** partagés entre personnages, signalés par une
**petite icône boutique** ; **tirages** (sous-classes, coffres) ; **remise à zéro** de classe et de points.

## 39.3 Cible proposée

→ **RBCoins = cosmétiques seulement**, aucun avantage de jeu, aucune conversion (ADR 0006) : pas de boost d'XP, pas de
tirage payant sur la puissance, pas de VIP qui donne plus de missions ou de ressources.
→ ❓ Le **slot de personnage supplémentaire** (`reborn.premium`) est-il un avantage acceptable ?
→ **Petite icône** qui distingue un cosmétique de compte (RBCoins) d'un objet de personnage (gagné en jeu).
→ Vente des **exécutions** et des **jetons de réinitialisation** : décision n° 8 en attente.

---

# ⛵ 40. Voyages entre pays (RedLine : bateaux et charpentier)

> RedLine : *Ship & Carpenter system* (la spec la plus longue de leur document). **Proposition — après la beta**
> (autres villages en cut list).

## 40.1 Aujourd'hui chez Reborn

- Pas de mer jouable ; un seul pays construit (Pays du Feu, Konoha en cours).

## 40.2 Chez RedLine

Le bateau est une **base mobile** : voile baissée = navigue, voile levée = ancré ; garde son cap ; joueurs libres sur le
pont ; un non-navigateur subit des **pertes de contrôle** ; **provisions = carburant** ; inventaire persistant lié au
bateau, **conservé après un naufrage** ; permissions ; vol possible voile baissée ; naufrage puis **30 min
d'indisponibilité qui survit aux redémarrages** ; réparation par un charpentier joueur **nettement moins chère** que
par le PNJ ; canons, harpons, pavillon éditable ; **liste anti-duplication**.

## 40.3 Cible proposée

→ Pas de bateaux. L'équivalent Naruto est le **voyage entre pays** : routes marchandes, **convois** (§35),
**postes-frontières** (fouille consentie §14, laissez-passer), auberges d'étape ; quand les autres pays existeront.
→ On garde deux principes : **le service d'un joueur coûte nettement moins cher que le service PNJ** ; **une liste
anti-duplication** pour tout objet persistant (coffres, trésor, échange).

---

# 🏝️ 41. Conflit territorial (RedLine : capture d'îles)

> RedLine : *Island Capture System*. **Proposition — après la beta** (Faction §9.3).

## 41.1 Aujourd'hui chez Reborn

- Rien ; la Faction prévoit un conflit territorial programmé **après la beta** (modèle Solve) ; « guerre
  inter-villages » en cut list.

## 41.2 Chez RedLine

**Lancée par le staff** (`/capture start`) : île, mode, nombre d'équipes (2 à 4), taille, **frais d'entrée**,
préparation ; annonce 30 min avant ; **seul le chef inscrit son équipe**, **pas d'alliance** ; camps et bouton
**Prêt** ; **île verrouillée** ; modes **Deathmatch**, **capture du drapeau**, **Couronne** ; **KO normal inchangé** ;
déconnexion = sorti ; intrus avertis ; récompenses (territoire sur la carte, drapeau) ; pénalités.

## 41.3 Cible proposée

→ Ce **déroulé complet** comme base du conflit territorial : lancement staff, annonce, inscription par le Kage ou le
chef, camps et « Prêt », zone fermée, modes, intrus avertis, récompenses visibles (territoire sur la carte, bannière du
village, renommée).
→ Pendant un conflit programmé, **chaque village combat seul**, même allié.
→ ❓ Le KO y est-il le **vrai KO** (blessures persistantes) ou celui d'un terrain d'entraînement ?

---

# 👘 42. Boutique en jeu (échoppe)

> RedLine : *In-Game Shop Zone*. **Fait** chez Reborn.

→ **Aujourd'hui** : l'**échoppe** vend tenues et cartes en ryo (`ShinobiCore/…/shop/`, écran dans la DA) ; tenues
achetées **liées au personnage** ; tenues d'institution débloquées par fonction ou grade (Faction §3.1).
→ **RedLine** : zone boutique, vêtements en monnaie de jeu, liés à l'identifiant, non échangeables, perdus à la mort du
personnage (contradiction avec leurs cosmétiques de compte).
→ **Cible** : garder la règle claire — **gagné en jeu = lié au personnage**, **payé en RBCoins = lié au compte**.

---

# 🧑‍🎨 43. Création de personnage

> RedLine : *Character Creation*. **Fait** ; deux ajouts proposés.

## 43.1 Aujourd'hui chez Reborn

- Création **en scène** « au bord de l'étang », en **8 étapes** : village (6), clan (11 entrées, verrous par village
  et sexe, candidature pour certains), sexe, nom, âge, taille, apparence, récapitulatif « Oui, c'est moi ».
- Une **cinématique d'introduction** peut être désignée par le staff (`/character cinematic <nom>`, drapeau
  `introSeen`).
- Pas de **kit de départ**.
- Fiche : [`gameplay/PERSONNAGES.md`](./gameplay/PERSONNAGES.md).

## 43.2 Chez RedLine

Parcours linéaire : race, apparence, identité (prénom, nom, genre, âge), **île de départ**, **cinématique
d'introduction** de l'île.

## 43.3 Cible proposée

→ Une **courte cinématique d'arrivée au village choisi** après « Oui, c'est moi ».
→ Un **kit de départ** (bandes de gaze, quelques ryo, bandeau du village) — §51.

---

# 🪞 44. Éditeur de personnage

> RedLine : *Character Editor* et *Character Creator detailed*. **Fait en partie** ; reprises proposées.

## 44.1 Aujourd'hui chez Reborn

- Skins 2D **composés** (peau → sous-vêtement → tenue → yeux → pilosité → cheveux), **35 coiffures**, couleur libre
  (pipette) des cheveux et des yeux, palettes de clan à faire, accessoire bandeau ; tenues : 5 Uchiha seulement
  (assets wave 1 à finir).

## 44.2 Chez RedLine

Options **par race** ; corps et mâchoire par un **triangle** (mince / large / musclé) ; couleur d'yeux libre, **modèle
et taille de l'iris** ; sourcils ; curseurs ; **cheveux en deux parties combinables** (avant et arrière), queue de
cheval déplaçable, physique des cheveux ; validation « non modifiable ensuite ».

## 44.3 Cible proposée

→ **Frange et arrière séparés** (deux calques combinables dans le compositeur : bien plus de coiffures pour le même
travail).
→ **Plusieurs formes d'iris** (utile aux dōjutsu) ; sourcils.
→ Options **par clan** comme RedLine par race (palettes, marques claniques : Inuzuka, Akimichi…).
→ Écarté : triangle de morphologie et physique des cheveux (skins 2D Minecraft).

---

# 🌅 45. Sélection de personnage et journal

> RedLine : *Existing Player — Character Selection*. **Fait** ; journal RP proposé.

→ **Aujourd'hui** : sélection **en scène** au bord de l'étang (lucioles, braises à la couleur du village, personnage
réel au centre) ; 2 slots par défaut, 7 pour le staff ; notes de version dans le launcher.
→ **RedLine** : un personnage par défaut (plus pour le staff et les VIP) ; bouton **Journal** (actualités, notes de
version, **primes en cours**) ; réseaux, réglages ; personnage face à l'horizon.
→ **Cible** : un **journal RP** consultable depuis le menu (gazette du village, avis de recherche publics du Bingo
Book, annonces du Kage) — aussi le canal des annonces de Faction.

---

# 📸 46. Avis de recherche et preuves (RedLine : journaliste et appareil photo)

> RedLine : *Journalist & Camera System*. **Proposition, à valider point par point.**

## 46.1 Aujourd'hui chez Reborn

- **Mode photo** et **galerie** côté client (filtres, cadres, partage vers le fil du launcher) ; pas d'objet photo ;
  Faction §8.3 : « plus tard, l'avis de recherche en objet ».

## 46.2 Chez RedLine

Appareil acheté, **5 photos**, vue subjective avec zoom ; **station de développement** (papier + encre) : la photo
devient un **objet** (montrable, échangeable, rangeable) ; boucle RP : la Marine commande une photo à un journaliste,
la meilleure sert à l'**avis de recherche** fait à une station à accès réglé (nom ou alias, mort ou vif, montant) ;
l'affiche est un objet que le staff peut placarder.

## 46.3 Cible proposée

→ **Avis de recherche-objet** fabriqué au poste de police ou de l'ANBU : portrait, nom ou alias, menace, « vivant
seulement », prime ; placardable.
→ **Photo-objet comme preuve** (enquêtes, espionnage) ; le portrait peut venir du **rendu du personnage réel** (comme
la fiche shinobi) plutôt que d'un appareil.

---

# 🚀 47. Launcher

> RedLine : *Launcher*. **Fait** (plus complet que RedLine) ; onboarding **validé 13/10** (§1.8).

## 47.1 Aujourd'hui chez Reborn

- Launcher Tauri 0.3.45 : connexion **Microsoft**, liaison **Discord**, **whitelist** avec candidature et fil de
  discussion relié à un fil Discord (`revise` côté staff), **tickets**, notes de version, réglages, mods signés et
  purge stricte, **auto-update** Ed25519, diagnostics, Rich Presence.
- Panel staff web (whitelist, tickets, joueurs, audit, anomalies, wiki, fichiers, 2FA).
- ❓ Décision n° 9 : bouton « Copier le lien » OAuth et / ou signature de code (Norton bloque le launcher).

## 47.2 Chez RedLine

Connexion (identifiant + mot de passe), liste blanche, candidature écrite ; le staff accepte ou refuse avec un
**commentaire visible**, un **délai avant nouvelle candidature** ou un **blocage** ; réseaux, notes, réglages, jouer.

## 47.3 Cible proposée

→ **Onboarding façon Ketto** (§1.8, validé, en cours).
→ Vérifier qu'on a le **délai avant nouvelle candidature** et le **blocage** côté panel.

---

# 🗝️ 48. Vols (RedLine : braquages)

> RedLine : *Robbery System*. **Proposition, à valider point par point.**

## 48.1 Aujourd'hui chez Reborn

- **Vol de parchemins** dans les bibliothèques « membres » : fouille immobile de quelques secondes devant le meuble,
  interrompue si l'on bouge ou est frappé, rouleau marqué « volé à … », alerte discrète aux membres, alerte `vol` au
  Poste de garde, journal (`ABIL/parchemin/VolService`). Vol prévu sur les coffres de Faction.

## 48.2 Chez RedLine

Accessibles au **niveau 10** ; `/robbery config` à la position de l'admin : nom, type, rayon, **joueurs requis**,
**Marines requis**, objets requis, durée, **recharge** (ex. 96 h), table de butin (garanti, aléatoire, unique),
alerte. Types : classique, **crochetage** (mini-jeu), banque, stockage, code. **Alerte à la Marine seulement**.
**Refus si pas assez de Marines dans le rayon**. Classement, journal complet.

## 48.3 Cible proposée

→ **Points de vol configurés** par le staff (entrepôt marchand, coffre de saisies, réserve d'un clan) avec **effectif
minimum de forces de l'ordre connectées et proches** pour pouvoir lancer (pas de vol sans opposition possible).
→ **Alerte à la police / l'ANBU du village**, **outil requis** (crochetage au senbon, sceau à briser), **recharge
longue**, butin réglé, journal.
→ Le **grade minimum** remplace le niveau (invisible).

---

# 🧭 49. Orientation (RedLine : Log Pose)

> RedLine : *Log Pose* (section en double chez eux). **Proposition — après la beta.**

→ **Aujourd'hui** : boussole dans la carte seulement.
→ **RedLine** : Log Pose réservé aux navigateurs, routes du staff, recalibrage d'île en île ; Log Pose éternel qui
pointe un lieu enregistré (et se vend) ; porté au poignet ; direction sur la boussole du HUD.
→ **Cible** : un **objet qui pointe vers un lieu ou une personne** — **marque de chakra** (style Hiraishin) ou
**rouleau de pistage** — affiché sur la boussole (§4) ; cadeaux de mission, objets de trafic ; voir aussi la « Vivre
Card » (§52).

---

# 🗂️ 50. KO « WIP » (ancienne version chez RedLine)

> RedLine : section *WIP* — une ancienne version du KO (7 min, réanimation de 20 s) laissée sans être barrée.

→ **Leçon pour nous** : ne jamais garder deux versions contradictoires d'un système côte à côte. Notre journal des
décisions **barre** une règle remplacée et renvoie à la nouvelle ; ce Game Plan suit la même règle.

---

# 🧭 51. Exploration (apparition, kit de départ)

> RedLine : *Exploration* (fin du document, tronquée). **Proposition, à valider.**

→ **Aujourd'hui** : apparition au village choisi ; pas de kit de départ ; interactions avec portes et meubles par
Minecraft, Nexo et le menu R.
→ **RedLine** : apparition sur l'île choisie, **objets de départ** (bandages, monnaie) ; E pour les portes et établis.
→ **Cible** : **kit de départ** (bandes de gaze, quelques ryo, bandeau du village, une sacoche ?) ; à chiffrer.

---

# 📋 52. Systèmes de leur feuille de route sans section chez RedLine

| Système RedLine | Ce qu'on en sait | Équivalent Reborn proposé | État |
|---|---|---|---|
| **Identité** (nom, prime, profil) | Esquissée : « se présenter », masques, avis de recherche | **Connu / inconnu** (`/rencontrer`) ✅, fiche shinobi ✅, masque qui cache l'identité (§14), Bingo Book (§46, Faction §8.3) | ✍🏻 |
| **Vivre Card** | Titre seulement | **Sceau de pistage** (marque de chakra) : un objet lié à un personnage, qui pointe vers lui et **se consume à sa mort** | ❌ |
| **Contrôle de territoire** | Réputation de faction, carte, alertes de contrebande | **Territoires de village** : zones de ressources (plugin tiers non versionné aujourd'hui), zones survolables (§36), conflit (§41) | ✍🏻 |
| **Casino** | Titre seulement | **Tanzaku-gai** (ville de jeu canon) : en ryo uniquement, jamais en RBCoins | ❌ |
| **Primes** (*Bounty*) | Avis de recherche | **Bingo Book** : primes posées par un village, validées par le staff | ❌ |
| **Journal** | Une ligne dans la sélection | Journal RP (§45) | ❌ |
| **Événements staff** | Séisme, annonces, capture | `/cinematic` ✅, `/meteo` ✅, `/ost` ✅ ; annonces (§5) ❌ ; séisme (§5) ❌ ; conflits (§41) ❌ | ✍🏻 |
| **Performances** | Titre seulement | `LotGui` / `LotBlit`, écrans opaques, monde non rendu derrière les menus (DA §6 règles 7-8) ; FPS par menu à mesurer chez l'utilisateur | ✍🏻 |
| **Image de marque / réseau** | Titre seulement | Rich Presence, réseaux dans le launcher et l'Échap, bot Discord, Trello public, Miro staff | ✍🏻 |
| **VIP + abonnements** | §39 | RBCoins cosmétiques ; `reborn.premium` dormant | ❌ |

---

# 🍥 53. Systèmes propres à Reborn (absents chez RedLine)

> Ce que RedLine n'a pas et qui fait l'identité de Reborn. **État = vérité du dépôt au 13/10.**

| Système | Ce que c'est | État | Fiche / code |
|---|---|---|---|
| **Chakra** | Grande jauge (~100 000 au maximum, un rang S se lance 2 à 3 fois), natures de chakra (test de la Feuille) | ✅ | `ShinobiCore/…/chakra/`, `/testfeuille` |
| **Six stats + fiche shinobi** | « Portrait en scène » (K), infobulles sans chiffres, jetons de réinitialisation | ✅ | [`gameplay/STATS_ET_PROGRESSION.md`](./gameplay/STATS_ET_PROGRESSION.md) |
| **Grades RP et Mérite** | 9 grades donnés en RP, Mérite (usage ❓) | ✅ | idem §4 |
| **Techniques et Technique Creator** | Catalogue, éditeur de graphes (panel), `/sa preview` ; 39 techniques beta à produire | ✍🏻 | [`technique/AUDIT_TECHNIQUES_ET_CREATOR.md`](./technique/AUDIT_TECHNIQUES_ET_CREATOR.md) |
| **Parchemins et bibliothèques v2** | Objet unique, 7 types de bibliothèques, fonds et arrivages, journal, lecture sur place, **vol**, **secrets** | ✅ sur le dev, à tester | [`gameplay/APPRENTISSAGE.md`](./gameplay/APPRENTISSAGE.md) §2-3 |
| **Séances et concentration du chakra** | Séances par rang, maître, mini-jeu de concentration | ✍🏻 rendu à trancher (n° 2) | idem §4 |
| **Slots du rang S** | Nombre limité de détenteurs par technique, héritage | ✅ | idem §5 |
| **Mort RP** | RPK validé par le staff, exécutions, scène de mort, corps à l'apparence Reborn | ✅ à tester à deux comptes | [`gameplay/KO_ET_MORT_RP.md`](./gameplay/KO_ET_MORT_RP.md) §7-9 |
| **ATA et terrains d'entraînement** | Blessé en attente de soin ; zones d'entraînement et de repos | ✅ | idem §2-4 |
| **Yeux** | Chirurgie (prélèvement, greffe, cécité, bandage), fiole d'œil Nexo, **Byakugan** | ✅ (Byakugan non commité) | `ShinobiCore/…/medic/yeux/`, ShinobiSense |
| **Clans et sorts claniques** | 11 entrées, verrous, candidature ; 18 sorts claniques à produire | ✍🏻 | [`ROADMAP_BETA_2026.md`](./ROADMAP_BETA_2026.md) §3.2 |
| **Perception** | Radar de chakra, genjutsu, dōjutsu (ShinobiSense) | ✍🏻 | `/sense` |
| **Démons à queues** | Rage, corruption, monde intérieur, transformation (ShinobiTail) | ✍🏻 à auditer | `/tail` |
| **Académie et équipes Genin** | Cursus, escouades, transmission par un sensei (ShinobiLearning) | ✍🏻 à auditer | `/academy`, `/squad` |
| **Mobilité ninja** | Naruto run, dash, double saut, saut de chakra, mur, escalade, voies de mobilité | ✅ | `ShinobiAbilities/…/mobility/` |
| **Méditation** | Régénère le chakra, avance le repos ATA (système avancé en cut list) | ✅ | `/meditation` |
| **Missions et bureau des missions** | Idées et formats de rapports, états de service | ❌ | [`gameplay/MISSIONS.md`](./gameplay/MISSIONS.md) |
| **Médical avancé** | Antidotes, coma, `MedicalService` | ❌ Sprint 3 | — |
| **OST contextuelle** | Musiques par zone, diffusion, late-join | ✅ | `minecraft/plugin-ost`, `mod-ost` |
| **Météo visuelle** | Pluie, sable, brume | ✅ | `/meteo` |
| **Emotes, démarches, postures** | EmoteCraft distribué par le serveur, démarches, `/assoir` | ✅ | [`gameplay/EMOTES.md`](./gameplay/EMOTES.md) |
| **Caméra, mode photo, galerie, éditeur de HUD** | Outils d'immersion côté client | ✅ | [`technique/MOD_HUD.md`](./technique/MOD_HUD.md) |
| **Whitelist, tickets, pont Discord** | Candidatures et tickets reliés à des fils Discord | ✅ | [`../CLAUDE.md`](../CLAUDE.md) |
| **Intégrité** | Play-token signé vérifié au join | ✅ | `minecraft/plugin-guardian`, `mod-integrity` |
| **Poste de garde et Infirmerie** | Panel staff en jeu | ✅ | [`staff/GUIDE_STAFF_EN_JEU.md`](./staff/GUIDE_STAFF_EN_JEU.md) |

---

# ▶️ 54. Ordre de passage, point par point

> La suite qu'on traite avec l'utilisateur, **dans l'ordre du document RedLine**. À chaque point : on relit la
> section, l'utilisateur tranche les ❓, la décision va dans [`pilotage/DECISIONS.md`](./pilotage/DECISIONS.md), la
> section passe en « validé », puis maquette si c'est visuel, puis code. On ne saute un point que sur décision.

| # | Point | § | Statut | Ce qu'il reste à trancher |
|---|---|---|---|---|
| 1 | **Player ID** : compte + N° de personnage, états, onboarding | §1 | **validé 13/10** | Format du N° (5 chiffres séquentiels ?) ; visible du joueur ? ; ban = compte + Microsoft + Discord ? ; ATA / duel en modificateurs ? ; reprendre l'onboarding en cours |
| 2 | **Inventaire** : sacoche, Tête, Arme, surcharge, notifications | §2 | **validé 13/10** | Arme en étui + dégainer, ou emplacement = arme en main ? ; Kenjutsu exige une arme ? ; maquette des notifications en bas à droite |
| 3 | **Ramassage** : manuel, aura, disparition | §3 | **validé 13/10** | Touche (F ? R + clic ?) ; couleurs d'aura ; délai (10 min ?) ; objets uniques jamais détruits ? |
| 4 | HUD | §4 | proposition | Boussole facultative ; zone d'effets ; ryo hors HUD |
| 5 | Commandes staff | §5 | proposition | Annonces, écran noir, voir la sacoche, tenue forcée, reset / suppression, notes staff, cible par N° |
| 6 | Journaux | §6 | proposition | Journal par personnage (économie + objets) ; remontée au panel web |
| 7 | Déplacements | §7 | proposition | Rétablir les dégâts de chute (seuil + roulade) ? |
| 8 | Combat (logique d'arme) | §8 | proposition | Pas de mode combat ; techniques liées à l'arme |
| 9 | Corps à corps (Taï / Ken) | §9 | proposition | Lot B : jauge de garde, parade parfaite qui renvoie, avantage au premier coup, clash, coup chargé, taille en course (décision n° 12) |
| 10 | Armes de jet | §10 | proposition | Lancer de kunai / shuriken, munitions = sacoche, renvoi à la parade |
| 11 | KO / mort | §11 | nos décisions | Compte à rebours remis à zéro à la reconnexion ? ; technique `volonte` (n° 3) |
| 12 | Nourriture → consommables | §12 | proposition | Modèle d'effet générique ; pas de consommable 30 s après un coup |
| 13 | États du joueur | §13 | validé 13/10 (liste) | Service unique ; prison ; menottes anti-chakra |
| 14 | Interaction (R) | §14 | proposition | Entrées mortes du menu ; porter / fouiller un conscient ; masque ; volume vocal |
| 15 | Duel | §15 | décidé 13/10 | Publier puis tester à deux comptes |
| 16 | Échange | §16 | proposition | Fenêtre d'échange (objets + ryo, 3-2-1) |
| 17 | Chat de proximité | §17 | proposition | Trois portées ; indicateur ; outils staff |
| 18 | Transmissions | §18 | radio décidée (14/10), maquettes v3 | Radio = escouades (oreillette, ajout à côté, persistante, vol, écoute staff) ; appel de service à part, à concevoir ; fiche `gameplay/RADIO.md` |
| 19 | Monnaie | §19 | proposition maquettée (14/10) | Bourse-objet : migration du solde, poids, plafond de fouille, mort RP — [`gameplay/MONNAIE_ET_BANQUE.md`](./gameplay/MONNAIE_ET_BANQUE.md) §14 |
| 20 | Banque | §20 | proposition maquettée (14/10) | Guichet PNJ / guichetier joueur, échange garanti, paliers, objets refusés, virement (Faction Q7, Q9) — même fiche §14 |
| 21 | Professions et petits boulots | §21 | proposition | Spécialisations = fonctions de section |
| 22 | Missions de rang D | §22 | proposition | Bureau des missions (décision n° 13) |
| 23-28 | Mineur, bûcheron, forgeron, médecin, cuisinier, cartographe | §23-§28 | proposition (médecin) / après la beta (le reste) | Verrou de combat sur les soins ; bandage du médic |
| 29 | Stockage et coffres | §29 | proposition | Coffres configurables, liste centrale (Faction F5) |
| 30 | Volière | §30 | proposition | Anonyme par défaut, coût par destinataire, lecture sur place |
| 31 | Contrebande | §31 | après la beta | — |
| 32 | Progression | §32 | nos décisions | Classement staff ; plafond d'XP 24 h ; Mérite (n° 4), bonus de grade (n° 5), plafond des stats (n° 7) |
| 33 | Deck de techniques | §33 | proposition | Presets + recharge globale |
| 34 | Équivalents chakra (Logia, Haki, granite) | §34 | après la beta | Étiquette « contourne X » |
| 35 | Caches et reliques | §35 | après la beta | Coffre porté, lâché si frappé |
| 36 | Carte | §36 | proposition | Marqueur + boussole, zones survolables, brouillard des pays étrangers ? |
| 37 | **Faction** | §37 | proposition maquettée | Les 18 questions de la proposition (décision n° 1) |
| 38 | Caractéristiques | §38 | proposition | Lié à la décision n° 5 |
| 39 | Boutique en argent réel | §39 | nos décisions | Slot de personnage payant acceptable ? ; vente des exécutions et jetons (n° 8) |
| 40-41 | Voyages entre pays, conflit territorial | §40-§41 | après la beta | KO réel ou d'entraînement dans un conflit ? |
| 42-45 | Échoppe, création, éditeur, sélection | §42-§45 | fait + ajouts | Cinématique d'arrivée ; kit de départ ; frange / arrière ; formes d'iris ; journal RP |
| 46 | Avis de recherche, preuves | §46 | proposition | Avis-objet, photo-objet |
| 47 | Launcher | §47 | fait + validé 13/10 | Délai et blocage des candidatures ; « Copier le lien » (n° 9) |
| 48 | Vols | §48 | proposition | Points de vol, effectif minimum de forces de l'ordre |
| 49-51 | Orientation, KO « WIP », exploration | §49-§51 | proposition | Kit de départ chiffré |
| 52-53 | Systèmes sans section, systèmes propres à Reborn | §52-§53 | — | Revue d'état (ShinobiTail, ShinobiLearning à auditer) |

**Prochain point à traiter : n° 1 (Player ID)**, puis 2 et 3, déjà validés dans leurs grandes lignes : il reste à
trancher leurs ❓ avant de passer aux maquettes et au code.
