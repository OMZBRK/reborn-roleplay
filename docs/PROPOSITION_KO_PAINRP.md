# Proposition — système KO / PainRP / ATA (v2, 2026-10-06)

> **But premier : tuer le combat « d'arène ».** Se battre doit coûter quelque chose hors des terrains d'entraînement, et la conséquence doit *forcer* l'interaction RP (médic, repos) au lieu de l'espérer.
> Les références jeux (§1) sont écrites de mémoire, pas re-vérifiées en ligne : repères de design, pas citations.
> Ancrage code : `ShinobiCore/ko/` (KoManager, PorterManager, KillRequestManager, injury/*), `ShinobiCore/medic/` (`/soigner`, armoires, médicaments, TreatmentApplier), `character/MeditationManager`.

## 1. Ce que font les autres

| Jeu / serveur | Mécanique | Ce qui marche | Ce qui frustre |
|---|---|---|---|
| **GTA RP (FiveM, NoPixel / ESX / QBCore)** | « Downed » à 0 HP, *bleed-out* ~5-10 min, appel EMS, ramper, **règles PainRP / FearRP / NLR** | Le joueur joue sa blessure, l'EMS a un vrai rôle, urgence | Règles **purement déclaratives** : sans mécanique, les trolls les ignorent. Attente passive sur écran noir |
| **GMod RP (DarkRP, Serious RP, Naruto RP)** | Ragdoll, `/me`, respawn, NLR ; côté Naruto : soin par `/me` + sort « paume de soin » qui lève l'ATA | Lisible ; le sort de soin donne un rôle au médic | Soin instantané, aucune scène à tenir ; respawn trop rapide = pas de tension |
| **Arma 3 (ACE Medical)** | Blessures par partie du corps, saignement, douleur, inconscience, portage, RCR | Le plus complet, chaque outil a un rôle | Trop lourd pour de l'action fluide |
| **Project Zomboid / DayZ** | Blessures par partie, infections, pas de régén gratuite | Conséquences durables | Mort brutale, peu d'intervention RP |
| **Rust / Tarkov / Hunt** | « Wounded » : ramper, relevé par un allié, achèvement | Le blessé reste *acteur* | Achèvement abusif |
| **Sekiro** | Résurrection : une seconde chance, limitée | Tension sans frustration, moment « je refuse de tomber » | — |
| **Apex / Battlefield** | Knocked, rampe, relève par cast ~5 s | Revive lisible | Peu de narration |
| **Jeux de survie (The Forest, Green Hell)** | Soins **maintenus** (bander, appliquer), interrompus par le mouvement | Le soin est une action qu'on tient, pas un clic | — |

### Leçons retenues
1. **Une règle RP sans mécanique est ignorée.** Fear / NLR / Pain doivent être *portés par le jeu*.
2. **L'attente passive tue le fun.** À terre et KO, le joueur doit garder des choix.
3. **Le soin doit être tenu dans le temps**, visible et interruptible, pour créer une scène.
4. **La mort définitive se négocie** (consentement + staff), jamais un simple PvP.
5. **Une conséquence durable ne marche que si elle a une sortie claire** (ici : médic ou repos RP), sinon les joueurs quittent.

## 2. Principes Reborn

1. **Terrains d'entraînement** (zones définies par le staff) : combat libre, **aucune conséquence**. C'est le seul endroit où le « sparring » est gratuit.
2. **Partout ailleurs, les PV perdus sont persistants.** Pas de régénération naturelle (déjà vrai pour le chakra depuis la suppression de la régén automatique).
3. **Seul le médic rend les PV et lève l'ATA rapidement.** Le repos RP (méditation, repas, onsen, lit) lève l'ATA lentement et ne rend qu'une **petite part** des PV (§6, plancher).
4. **Tomber n'est pas mourir.** Ramper, se relever par la volonté (limité), KO interactif, rapatriement à l'hôpital si personne ne vient.
5. **Au retour : Pain, Fear, NLR imposés par le jeu** (état **ATA**) tant que le personnage n'a pas été soigné ou ne s'est pas reposé.

> Terminologie : **ATA** désigne ici l'état post-KO « blessé, en attente de soin » (comme sur les Naruto RP GMod), et **non plus** le timer de scène de la carte Trello d'origine. La carte sera corrigée une fois le design validé.

## 3. Le cycle complet

```
 combat hors zone
       │ PV à 0
       ▼
 ① À TERRE ── QTE « Volonté » réussi (max 2) ──► ② SECOND SOUFFLE (debout, fragile)
       │ échec / temps écoulé / tentatives épuisées      │ PV à 0 à nouveau → KO direct
       ▼                                                  ▼
 ③ KO INTERACTIF ── soigné par un médic sur place ──────► ⑤ ATA (allégée)
       │ personne / abandon / timer                              ▲
       ▼                                                          │
 ④ RAPATRIEMENT HÔPITAL ──────────────────────────────────► ⑤ ATA (pleine)
                                                                   │ médic (paume de soin) OU repos RP cumulé
                                                                   ▼
                                                               rétabli
```

### ① À terre (PV = 0, ~45 s)
- Chute (animation), **ramper** à ~15 % de vitesse : pas de saut, pas de technique, pas d'attaque.
- Parole en **chuchotement** (portée 4 blocs), `/me` libre.
- **Appel à l'aide** (touche dédiée) : signal visible aux alentours + notification aux médics connectés du village.
- Saignement léger (cause PV uniquement) : raccourcit la phase, stoppable par un bandage d'un allié.
- **Se relever — QTE « Volonté »** : le moment shōnen. Touche « se relever » → mini-jeu au HUD (voir §4). Difficulté croissante à chaque tentative et selon la gravité des blessures, adoucie par la **Vigueur**.
  - **2 relèves maximum** par cycle de blessure ; le compteur ne se remet à zéro qu'après un **soin médical**. Un joueur qui a déjà usé ses relèves et repart au combat tombe directement en KO.
  - Chaque relève **aggrave une blessure** d'un cran (système `injury` existant) : l'obstination se paie.

### ② Second souffle (debout, fragile)
- Relevé à **5 % PV**, endurance vide, pas de Naruto run ni de dash, techniques rang D maximum, M1 affaibli.
- Shader et souffle sonore toujours présents. Moment pour fuir, protéger un allié, finir une réplique.
- Retomber à 0 PV = **KO immédiat**, sans phase à terre.

### ③ KO interactif (5 min maximum)
- Corps immobile, mais le joueur **voit** (caméra libre limitée), **chuchote**, fait des `/me`, voit le compteur.
- Les autres peuvent : **porter** (existant), **fouiller / ausculter** (existant), **soigner** (§5), lancer une demande de mort RP (existant, + consentement de la victime : §7).
- **Bouton « Se laisser emmener »** disponible après 2 min : coupe court à l'attente, mène au rapatriement.

### ④ Rapatriement à l'hôpital
- À la fin du KO sans soin (ou sur le bouton) : fondu au noir, réveil sur un **lit de l'hôpital** du village du personnage (point configurable par village).
- PV au **plancher** (§6), ATA **pleine**. Les frais d'hospitalisation se règlent en RP (pas de mécanique).
- L'hôpital devient un lieu RP : les médics y sont, les blessés y arrivent.

### ⑤ État ATA — Pain, Fear, NLR portés par le jeu
| Volet | Effet mécanique | Fin |
|---|---|---|
| **PainRP** | Pas de sprint ni Naruto run, pas de dash/saut chakra, techniques rang D max, M1 −50 %, démarche blessée (emote superposée), shader de douleur léger à l'effort, souffle sonore | Levée de l'ATA |
| **FearRP** | Impossible d'**initier** une attaque contre les protagonistes du KO ; hors zone d'entraînement, impossible d'initier un combat tout court | Levée de l'ATA + 20 min minimum |
| **NLR** | Laissé au joueur (décision du 2026-10-06) : simple rappel dans l'icône ATA, aucun blocage | — |
- Icône ATA au HUD avec ce qui la lève : « Soin médical requis » ou barre de **repos** (ex. 20 min cumulées).
- **Lever l'ATA** :
  - **Médic** : paume de soin tenue au moins 30 s (§5) → levée immédiate (sauf blessures graves : il faut d'abord les traiter via `/soigner`).
  - **Repos RP** cumulé, assis dans une zone dédiée : méditation (existante), repas au restaurant, onsen, lit d'auberge/hôpital. Anti-AFK : il faut une activité réelle (assis + entrée clavier périodique, ou chat RP).
- Retomber en KO **pendant** l'ATA : blessures aggravées (existant) et, à la 3ᵉ fois, **coma** : seul un médic de rang 2 peut réveiller.

## 4. Le QTE « Volonté »

- **Forme** : un cercle de sceaux qui se referme ; il faut frapper la touche affichée au bon moment, 4 à 7 fois selon la difficulté. Une erreur fait perdre la tentative.
- **Difficulté** = base + (tentatives déjà faites) + (gravité des blessures) − (Vigueur). Fenêtres de timing de ~250 ms → ~120 ms.
- **Sécurité** : le serveur envoie la graine et les fenêtres (`reborn:ko` en S2C), le client renvoie les horodatages, le serveur valide la plausibilité ; un client modifié ne peut pas forcer une réussite au-delà des fenêtres.
- **Visuel** : sceaux dorés sur la DA Reborn (kit nuit/laque/or), battement de cœur qui accélère, à la réussite flash blanc + cri de l'effort.

## 5. Le médic : paume de soin maintenue

Ce qui existe : `/soigner` traite les **blessures** par partie du corps (médicaments + 3 s d'immobilité, palier par palier). On garde ça pour les blessures et on ajoute le **soin de PV** par le chakra du médic.

- **Paume de soin (Shōsen)** : le médic cible le patient, qui **accepte** (évite le soin forcé). Le soin est **tenu** :
  - **PV / seconde** = 1,5 % des PV max du patient × (1 + 0,10 × eff(Contrôle)) × bonus de rang médical.
  - Coût : chakra du médic **par seconde**, proportionnel ; à 0, le soin s'arrête (pas de KO chakra pendant un soin, le médic s'arrête avant).
  - **Interrompu** si le médic bouge de plus d'1,5 bloc, est frappé, ou si le patient s'éloigne.
  - Ordre de grandeur : médic débutant ~60 s pour un soin complet, médic expert (Contrôle 10) ~30 s. Assez long pour une scène, assez court pour ne pas lasser.
- **Paliers** (alignés sur la carte Trello « médical ») :
  1. **Bandages** (tout le monde) : stoppe le saignement, +5 % PV, ne lève pas l'ATA.
  2. **Médic rang 1** : paume jusqu'à 60 % PV max, lève l'ATA si aucune blessure Importante ou Urgente.
  3. **Médic rang 2** : jusqu'à 100 %, lève toute ATA, réveille d'un coma.
  4. **Iryō avancé** (post-beta) : réanimation, régénération.
- **RP** : le `/me` reste libre ; le système affiche au patient et au médic des **indications** (« la chaleur se répand dans ta poitrine », « le flux se stabilise ») pour nourrir la scène sans l'écrire à leur place.
- **Avis** : bonne idée de faire dépendre la vitesse du chakra et du Contrôle du soignant, et de la rendre tenue. J'ajoute deux choses : le **coût en chakra par seconde** (soigner devient un vrai don, un médic épuisé ne soigne plus tout le village), et le **plafond par rang** (la hiérarchie médicale a un sens). Pas d'opération, pas de minijeu chirurgical : le temps tenu suffit à créer l'interaction.

## 6. Le plancher : éviter le blocage sans médic

Risque réel en beta : peu de médics connectés → des joueurs coincés à 5 % PV pendant des jours, qui quittent le serveur.

- Le **repos RP** rend des PV lentement, **jusqu'à 30 % PV max seulement**. Au-delà, seul un médic soigne.
- Le **rapatriement** réveille au plancher (30 %).
- L'hôpital peut avoir un **PNJ infirmier** qui applique des bandages (palier 1), sans lever l'ATA.

Ainsi, le combat hors zone coûte toujours une visite au médic pour revenir à pleine forme, mais personne ne reste bloqué.

## 7. Terrains d'entraînement et mort RP

- **Registre de zones** dans ShinobiCore (sur le modèle du registre de zones OST), édité par commande staff. Drapeau `entrainement`.
- **Dans une zone** : à l'entrée, PV mémorisés ; combat libre ; 0 PV = « défaite » (chute 10 s, relève à 30 %, aucune blessure, aucune ATA, QTE permis pour le fun) ; à la sortie, PV **restaurés à la valeur d'entrée**. On ne peut donc ni s'y blesser durablement ni s'y soigner.
- **Hors zone** : toute agression est **journalisée** (qui a frappé en premier, où, issue). Le staff voit dans le panel admin les joueurs qui enchaînent les combats hors zone.
- **Mort RP** : exige l'accord des **deux joueurs** puis la **validation staff** (flux `KillRequestManager` existant, auquel on ajoute le consentement de la victime). Sinon : coma.

## 8. Habillage

### Shaders (mod-hud, post-effect sur le modèle de `byakugan.json` / `meteo.json`)
Un seul `ko.fsh`, trois uniformes envoyés par `reborn:vitals` : `pain`, `consciousness`, `chakraTint`.
- **À terre** : désaturation progressive, vignette rouge qui pulse au rythme cardiaque, flou périphérique.
- **QTE** : le monde se fige presque (désaturé), seuls les sceaux sont en couleur.
- **KO** : noir et blanc, vignette serrée, flou, son étouffé.
- **ATA** : pulsation rouge légère seulement à l'effort (sprint tenté, coup reçu).
- **Cause chakra** : teinte bleu glacé au lieu du rouge.
- **Soin** : la vignette se retire au rythme des PV rendus, halo vert doux.

### Animations (EmoteCraft via `reborn:emote`, posture imposée par `PostureManager`)
`ko_fall`, `ko_crawl` (boucle), `ko_rise_struggle` (relève QTE), `ko_unconscious_side`, `ata_limp_walk` (superposée à la marche), `heal_palm_kneel` (médic) + `healed_breath` (patient), `carry_shoulder` / `carried_limp`, `rest_eat`, `rest_onsen`.

### Sons (Paper + `mod-ost`)
Souffle haché et battement lié aux PV ; filtre passe-bas en KO ; cri d'effort à la relève ; boucle de chakra médical ; bruits de bandage ; jingle discret à la levée de l'ATA.

## 9. Découpage

| Lot | Contenu | S'appuie sur |
|---|---|---|
| **KO-1** | PV persistants hors zone + registre de zones d'entraînement + journal des agressions | `KoManager`, stats PV, registre de zones |
| **KO-2** | À terre (ramper, chuchoter, appel à l'aide) + KO interactif + rapatriement hôpital | `KoManager`, `PostureManager` |
| **KO-3** | État ATA (Pain / Fear / NLR) + repos RP + plancher | KO-1, `MeditationManager`, `sit/` |
| **KO-4** | Paume de soin maintenue + paliers médicaux | `medic/`, StatsService (Contrôle) |
| **KO-5** | QTE Volonté (client + validation serveur) | mod-hud, nouveau canal `reborn:ko` |
| **KO-6** | Shader `ko.fsh`, sons, emotes | mod-hud, `reborn:vitals`, `reborn:emote` |
| **KO-7** | Mort RP avec consentement de la victime + vue staff dans le panel | `KillRequestManager`, API audit |

Ordre conseillé : **KO-1 → KO-2 → KO-3 → KO-4** (la boucle anti-arène est jouable), puis **KO-6 → KO-5 → KO-7**.

## 9 bis. État d'implémentation (2026-10-06)

Lots **KO-1 à KO-4** codés dans ShinobiCore (+ garde-fous ATA dans ShinobiAbilities), compilés, **pas encore testés en jeu**. Côté serveur uniquement : aucun changement du mod client, les visuels passent par des effets vanilla (Darkness, pose forcée, titres, barre de boss, particules).

| Élément | Où | Commandes / fichiers |
|---|---|---|
| Zones RP (entraînement, repos) | `ko/zone/` | `/zonerp pos1·pos2·creer <entrainement\|repos> <id>·suppr·liste·ici` → `training-zones.yml` |
| PV persistants hors zone | `TrainingZones#onRegain` | `ko.pv-persistants` |
| Journal des agressions | `ko/AggressionLog` | `aggression-log.txt` |
| À terre / inconscient / hôpital | `ko/KoManager`, `KoListener` | `/aide`, `/hopital`, `/hopital set <village\|defaut>` → `hospitals.yml` |
| ATA (Pain / Fear, repos, plancher) | `ko/ata/AtaManager` | `/ata`, `/ata voir·lever·appliquer <joueur>` → `ata-state.yml` |
| Mobilité / techniques bloquées en ATA | ShinobiAbilities (`KoService#isImpaired`) | — |
| Paume de soin maintenue (KO-4) | `medic/PalmHealing` | `/paume [joueur]`, `/paume accepter`, `/paume stop` ; `medic.paume.*` |
| Bandages (KO-4) | `medic/BandageListener` | clic droit avec une Bande de gaze ; `medic.bandage.pv-pct` |

Rangs médicaux par permission : `shinobicore.medic` (rang 1, 60 %), `shinobicore.medic.expert` (rang 2, 100 %, lève toute ATA). À brancher plus tard sur le système de faction (Corps médical).

Écarts assumés par rapport au §3, à reprendre dans les lots suivants :
- **Saignement** pendant la phase à terre : pas encore ; le bandage relève un corps KO mais ne « stoppe » rien.
- **QTE et second souffle** : KO-5 (côté client). En attendant, à terre → inconscient sans relève possible.
- **Coma** au 3ᵉ KO pendant une ATA : le compteur existe (`/ata voir`), l'effet n'est pas encore branché.
- **Repos** : méditation n'importe où, ou assis / allongé / couché dans une zone `repos` ; manger dans une zone de repos = +2 min.
- **Médics alertés par `/aide`** : permission `shinobicore.medic` (à donner au grade Corps médical).

## 10. Décisions (2026-10-06)
1. **Plancher sans médic : 30 %** — validé.
2. **Frais d'hospitalisation** : gérés **en RP**, aucune mécanique.
3. **Mobs** : pas de faune vanilla, seulement des MythicMobs. Leurs dégâts suivent la même règle (persistants hors zone d'entraînement), réglable par un drapeau de config si un boss ou un donjon doit faire exception.
4. **NLR** : **laissé au joueur**. Aucun blocage ni log : un simple rappel dans l'icône ATA.
5. **Durées** (à terre 45 s, KO 5 min, repos 20 min, Fear 20 min) : valeurs de départ, à régler en test staff.
