# Déplacements : marche, trot, sprint

> **Fiche système, à jour au 2026-10-15** (lot « allures », codé, **non publié** : ShinobiAbilities, `reborn-hud` ;
> **marche sur l'eau** ajoutée le 15/10, §10, non publiée).
> **Saut chargé** (lot 5, étape A, 09/11) : §11, codé, **non publié** (`reborn-hud` 0.4.202, ShinobiCombat, ShinobiAbilities).
> **Byakugan** (lot 5, étape B, 09/11) : §12, codé, **non publié** (`reborn-hud` 0.4.202, ShinobiCore). Ce n'est pas un
> déplacement, mais il appartient au même lot « Mobilité ».
> **Marche sur les murs, essai technique** (lot 5) : §13. Étape C0 publiée le 24/11 (`reborn-hud` 0.4.207,
> ShinobiAbilities) et validée en jeu ; étape C1a (caméra de paroi, passage de mur en mur) le 26/11 (`reborn-hud`
> 0.4.208, ShinobiAbilities). Éteint par défaut, réservé au staff (`/murtest`).
> Elle décrit **ce qui est codé**. Décision : GAMEPLAN §7 et [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)
> (14/10). Référence : RedLine *Player movements* (marche par défaut, jogging sur Shift maintenu, sprint sur double
> Shift avec une barre qui n'apparaît qu'en sprint, ~5 s). Captures (hors dépôt) : `Maquettes/gameplan/05_deplacements/`.
>
> Code : `ShinobiAbilities/…/mobility/Allure.java` (serveur, autorité), `mod-hud/…/allure/` (prédiction, barre,
> banc), `mixin/LocalPlayerAllureMixin` (trot relâché), `mixin/AbstractClientPlayerFovMixin` (vue stable),
> `animation/MovementAnimations` (cadence et traînée). Canal et config :
> [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md).

---

## 1. Le principe en une phrase

**On marche par défaut, on trotte en tenant la touche de sprint, on sprint en la tapant deux fois, on lance la course
de chakra en la tapant trois fois** (« 4ᵉ rapport », 15/10) : la marche est l'allure RP (lente, discrète), le trot est
gratuit, le sprint est rapide mais court (endurance), la course de chakra se paie en chakra, et rien ne s'affiche tant
qu'on ne sprinte pas.

---

## 2. Les trois allures

| Allure | Touche | Vitesse (× la marche Minecraft, ~4,3 blocs/s) | Coût | Animation |
|---|---|---|---|---|
| **Marche** | rien (ZQSD) | **0,75** → ~3,2 blocs/s | — | la **démarche** choisie (menu « . »), cadence 0,85 |
| **Trot** | **touche de sprint maintenue** (Ctrl gauche par défaut) | **1,15** → ~5 blocs/s | gratuit (faim vanilla) | course, cadence 1 |
| **Sprint** | **double appui** sur la touche de sprint, le second maintenu | **1,55** → ~6,7 blocs/s | **20 d'endurance par seconde** (~5 s avec 100) | course, cadence 1,3 + **traînée de fumée blanche** |
| **Course de chakra** (15/10) | **touche X** seule (bascule : un appui la lance, un autre l'arrête) ; le triple appui sur le sprint est retiré le 16/10 (hud 0.4.186) | celle de la course ninja (ShinobiAbilities, `mobility.naruto-run`) | chakra (`chakra-cost-per-second`) | course ninja |

- La touche est **celle du sprint de Minecraft** (Options → Commandes → Déplacement → Sprint), donc **rebindable** et
  sans conflit avec les touches Reborn (R interaction, F ramasser, Z dégainer, E sacoche, X lâcher, G dash, C garde…). RedLine utilise Shift, qui est chez nous l'accroupi (et le saut de chakra Shift + Espace).
- **Relâcher** la touche ramène à la marche après 0,2 s (le temps d'un double appui) ; s'arrêter, heurter un mur ou
  reculer coupe trot et sprint (règles vanilla).
- Le **double appui sur Avancer** (sprint vanilla) donne le trot jusqu'à l'arrêt.
- Option « Sprint : basculer » de Minecraft : un appui = trot jusqu'au prochain appui ; double appui = sprint.
- Le passage d'une allure à l'autre est **lissé** (~0,25 s), sans à-coup ; la **vue ne zoome pas** quand l'allure change
  (le modificateur Reborn est retiré du calcul du champ de vision).

---

## 3. Endurance du sprint

- **Même réserve que le combat** (`EnduranceService`, ShinobiCombat) : courir avant un combat se paie. Son maximum suit
  la **Vigueur** : la durée du sprint progresse avec le personnage (RedLine : « augmentable par la progression »).
- Récupération : celle du combat (16/s après 1,2 s sans dépense).
- **Vidé = essoufflé** : retour au trot, plus de sprint avant d'avoir retrouvé **30 %**.
- Pour lancer un sprint : au moins **10** d'endurance.
- Sans ShinobiCombat : une réserve locale de 100 la remplace (mêmes règles).
- La dépense du sprint **n'allume pas** le HUD de combat (ensō, barre de combat) ; un coup porté, si.

## 4. Retour visuel (DA §6, règle 9)

- **Barre de sprint** : un filet de 2 pixels écran au-dessus de la barre d'action, centré, ~1/9 de l'écran, sur un
  voile nuit ; or, vermillon sous 20 % et quand le personnage est essoufflé (avec un repère à 30 %).
- Un **kanji minuscule** à gauche : 疾 en sprint, 息 pendant la récupération.
- Visible **seulement** pendant le sprint et la récupération qui suit, fondu d'entrée et de sortie. Marche et trot
  n'affichent rien.
- **Traînée** : petites bouffées blanches derrière les pieds, vues par tous (état relayé par `reborn:anim`, code 6).

## 5. Ce qui interdit quoi

| Situation | Marche | Trot | Sprint |
|---|---|---|---|
| **Chargé** (sacoche 100-115 %) | ralentie | ❌ (déjà : sprint vanilla coupé) | ❌ |
| **Surchargé**, **Immobile** | ralentie / 0 | ❌ | ❌ |
| **ATA** (blessé) | ✅ | ❌ (faim tenue à 6) | ❌ |
| **Étourdi** (ShinobiCombat) | immobile (14/10) | — | ❌ |
| **Porter quelqu'un** | ralentie (porteur) | selon l'état | ❌ |
| **Garde** (C maintenue) | immobile | — | ❌ |
| Faim ≤ 30 % (vanilla) | ✅ | ❌ | ❌ |
| **KO**, **porté**, véhicule, nage, vol, créatif | allure **neutre** (le modificateur est retiré, une autre mécanique gère la vitesse) |
| **Course ninja** (touche X) / Voie du Flux | allure **neutre** : la course possède la vitesse ; lancer la course coupe le sprint |

> **Depuis la refonte des états (17/10, non publiée)** : ce tableau est lu dans la matrice `etats.yml` (capacités
> `TROTTER`, `SPRINTER`, `BOUGER`, `SAUTER`) — [`../technique/ETATS_JOUEUR.md`](../technique/ETATS_JOUEUR.md). Le sprint
> est aussi refusé aux menottés, entravés, occupés ; le mod reçoit l'état (`reborn:etat`) et coupe trot et sprint en
> avance. Les lenteurs d'état (douleur ×0,6, ATA ×0,85, porteur ×0,7, gorge ×0,55, surcharge, manteau du bijū…) sont
> **un seul** modificateur `reborn:etat_vitesse` (produit, plancher 0,25) qui se compose avec l'allure.

## 6. Autorité et prédiction

- Le **serveur** (ShinobiAbilities) pose le modificateur `reborn:allure` (MULTIPLY_SCALAR_1 sur MOVEMENT_SPEED) d'après
  le drapeau de sprint Minecraft (trot) et le sprint qu'il a **accordé** ; il débite l'endurance et coupe le sprint
  (vide, étourdi, chargé…).
- Le **mod** pose le même modificateur avant chaque déplacement (prédiction) : la touche répond tout de suite et
  l'attribut serveur, qui arrive avec la latence, ne fait pas sauter la vitesse. Chaque demande de sprint porte un
  numéro ; un refus n'est pris en compte que s'il répond à la dernière demande.
- Hors serveur Reborn (canal absent, ou `mobility.allure.enabled: false`), tout redevient vanilla.

## 7. Réglages (`ShinobiAbilities/config.yml`, `mobility.allure`, défauts dans le code)

| Clé | Défaut | Rôle |
|---|---|---|
| `enabled` | `true` | Interrupteur général |
| `marche` / `trot` / `sprint` | `0.75` / `1.15` / `1.55` | Vitesses (× la marche Minecraft), poussées au mod à la connexion |
| `sprint-endurance-par-seconde` | `20.0` | Coût du sprint |
| `sprint-endurance-min` | `10.0` | Endurance minimale pour partir |
| `sprint-relance-pourcent` | `30` | Seuil de fin d'essoufflement |

## 8. Banc de captures

`REBORN_ALLURE_DEBUG=1` (client de dev, solo, endurance simulée) : piste dans le ciel au-dessus du spawn, marche, trot,
sprint, fin de sprint, essoufflement, récupération → `run/screenshots/allure_0*.png` et les vitesses mesurées dans
`allure_vitesses.txt` ; la piste est effacée à la fin. Cf. [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5.
La touche X (course de chakra ; le banc vérifie aussi que le triple appui ne la lance plus) est éprouvée par le banc des touches (`REBORN_TOUCHES_DEBUG=1`, même section). Son de la course (15/10 au soir) : souffle discret (`ENTITY_BREEZE_IDLE_AIR`, volume 0,2, ton varié, toutes les 2 s et seulement en mouvement), clés `mobility.naruto-run.souffle-*` ; l'ancienne élytre relancée chaque seconde (`wind-*`) n'est plus lue.

## 9. En attente

- ✅ **Course de chakra sur la touche X seule** (16/10, hud 0.4.186 : le triple appui sur le sprint est retiré, « c'est chiant ») — historique : la touche L disparaît ; la course de
  chakra est le **4ᵉ rapport**, un **triple appui** sur la touche de sprint (le 3ᵉ maintenu, dans la fenêtre du double
  appui de Minecraft, `sprintWindow`). Le mod ne la demande que si le chakra connu suffit (≥ 5, défaut de
  `min-chakra-to-start` ; sans état de chakra reçu, il laisse le serveur trancher) ; le plugin vérifie le reste (apprise,
  KO, étourdi, surcharge, verrou après un coup) et refuse sinon (message d'action « Course Shinobi non débloquée. » si
  elle n'est pas apprise). Relâcher la touche l'arrête (option « Sprint : basculer » : un nouvel appui). La demande part
  toujours sur `reborn:run` (alias `reborn:naruto`), plugin inchangé. Code : `allure/Allure#courseDeChakra`,
  `animation/NarutoRun#demarrer` / `#arreter`.
- ❓ **Faim** : le vanilla coupe trot et sprint sous 30 % de faim ; RedLine ne coupe le sprint qu'à 0 %.
- ❓ **Dégâts de chute et roulade** (GAMEPLAN §7.3) : non traités ici.

---

## 10. Marche sur l'eau (15/10)

> Codé le 15/10, publié sur le dev le 15/10 (hud 0.4.181, v8 en 0.4.182 ; ShinobiAbilities `mobility/MarcheEau`, mod `eau/`),
> **validé en jeu le 17/10**. Demande : « fais la marche sur
> l'eau avec les mêmes visuels d'eau qui bouge en marchant ; il faut vraiment que ce soit aussi bien, si ce n'est
> mieux » (référence Shinoda, [`../etudes/CONCURRENCE_SHINODA.md`](../etudes/CONCURRENCE_SHINODA.md)). Captures, GIF
> et planche comparative (hors dépôt) : `Maquettes/gameplan/eau_v8/` (version actuelle, pli de la texture, films 60 i/s ; v7 : `eau_v7/`, v6 : `eau_v6/`, v5 : `eau_v5/`,
> v4 : `eau_v4/`, v3 : `eau_v3/`, v1 : `eau_meteo/`).

### 10.1 Pour le joueur

- **Capacité apprise** : `MARCHE_EAU` (« Marche sur l'eau »), une capacité de mobilité comme le dash ou l'escalade :
  débloquée par un **parcours d'entraînement** qui la récompense ou par le staff (`/sa mobility unlock <perso>
  marche_eau`). La leçon d'académie « Marche sur l'eau » (ShinobiLearning) ne la débloque pas encore d'elle-même.
- **Automatique** : on marche sur l'eau comme sur le sol — marche, trot, sprint, saut, avec les allures habituelles.
- **Accroupi maintenu** (~0,35 s) : on **s'enfonce** volontairement. Un appui bref (dash, course ninja) ne fait pas
  couler.
- **Dans l'eau, Saut** près de la surface : on **remonte d'un bond** sur l'eau (si on en a le droit).
- **Chakra** : un peu, en continu, tant qu'on se tient sur l'eau — 0,3/s immobile, 0,5 en marche, 0,8 au trot, 1,5 au
  sprint (le chakra ne remonte pas tout seul : la méditation le rend). **Chakra vide = on tombe à l'eau.**
- **On tombe aussi** : KO (à terre, inconscient), douleur ou ATA, étourdi, gelé, porté, mode staff, surchargé
  (tout ce que refuse `KoService.mobilityDenial`) — le serveur retire le permis, la surface redevient de l'eau.
- Se poser sur l'eau après un saut **n'inflige pas de dégâts de chute**. En créatif : gratuit.

### 10.2 Ce qu'on voit (tout le monde, pas seulement soi)

Refait le 15/10 (retours « c'est hideux, fais subtil », « toujours pas ça », puis « ça fait trop ajout ») sur la
référence Shinoda : **l'eau reste strictement celle du jeu**, aucune déformation, et les flaques doivent sembler en
faire partie ; v6 : chaque pas se lit comme un impact sur l'eau.

- À **chaque pas** (pied gauche puis droit), **la texture de l'eau se plie** autour du point d'impact (v8, 15/10 :
  « chez eux c'est une sorte de distorsion, pas des cercles ») : le long d'un contour irrégulier propre à chaque pas
  (~0,6 à 1 bloc), le motif de l'eau est déplacé comme par une réfraction, avec un liseré clair très léger sur la crête
  du pli. Le pli s'étale à peine (×0,9 → ×1,15), frémit lentement puis se résorbe (~1,6 s). Animation au temps réel
  de chaque image (fluide à toute fréquence d'images).
- **Gouttelettes** : à chaque pas, 3 à 4 gouttes d'eau du jeu jaillissent du pied et retombent (6 à 7 au sprint) ; à
  la **retombée d'un saut**, une onde plus grande et une petite gerbe (12 gouttes). Pas de nuage blanc (la bouffée du
  sprint des allures reste coupée sur l'eau).
- **Sons** : un bruit de pas dans l'eau à chaque pas (éclaboussure légère + clapotis, pitch et volume variés), plus
  marqué au sprint, une éclaboussure à la retombée. Pluie et orage : rien de plus.
- Les autres joueurs voient les ondes et gouttes et entendent les pas **sans réseau** : chaque client détecte lui-même les joueurs debout à la
  surface de l'eau (pieds à la surface, pas dans l'eau) et pose leurs effets.
- Réglages : constantes en tête de `eau/Ondes.java` (rayon, irrégularité, largeur de la bande pliée, déplacement du
  motif, liseré, opacité, étalement, frémissement, durées, retombée, écart des pieds, gouttelettes, volume des sons).

### 10.3 Technique

- **Physique** : Minecraft calcule le mouvement côté client. Quand le serveur le permet (`reborn:eau`), le mod donne au
  bloc d'eau de surface une **forme de collision** (hauteur de l'eau + 2 cm, seulement si les pieds sont au-dessus,
  seulement pour le joueur local — `mixin/BlockStateEauMixin`). Le serveur accepte ces positions (aucun contrôle de
  vol ne se déclenche au-dessus de l'eau) ; il détecte lui-même qui se tient sur l'eau pour débiter le chakra.
- **Rendu** (Sodium) : par-dessus l'eau du jeu (`LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`), un petit patch
  redessiné avec **la même texture d'eau** (sprite animé de l'atlas, pipeline translucide des blocs, teinte du biome,
  lumière du bloc, brouillard) : grille de cellules de 1/16 de bloc alignées sur les texels de l'eau ; à chaque sommet,
  le point de lecture de la texture est décalé radialement dans une bande gaussienne autour du contour (le motif se
  resserre sur la crête : le pli), l'opacité suit la bande (nulle ailleurs), la crête est très légèrement éclaircie.
  L'eau des chunks n'est jamais touchée.
- **Coût** mesuré au banc (portable, iGPU Intel Iris Xe, avec Sodium) : ~0,30 ms CPU par image en moyenne pendant le
  banc (~1 000 petits quads), 0 quand il n'y a pas de pli ; gouttelettes = quelques particules du jeu par pas.
  Mémoire ajoutée : aucune texture, ~80 Ko de tableaux et grilles (128 plis au plus), tampons réutilisés de 128 Ko
  (CPU) et 3 × 128 à 256 Ko (GPU). Aucune allocation par image ; rien au-delà de 56 blocs ni caméra sous l'eau.
- **Shader Iris** : non vérifié (Iris n'est pas dans le modpack) ; le patch passe par le pipeline translucide des
  blocs du jeu.
- **Limites** : un joueur téléporté au-dessus de l'eau avant que ses chunks n'arrivent tombe dedans (Saut pour
  remonter) ; les plantes aquatiques qui affleurent portent aussi (eau dans le bloc) ; une chute d'eau ne porte pas.

### 10.4 Réglages

`ShinobiAbilities/config.yml`, `mobility.marche-eau` : `enabled` (true), `chakra-par-seconde.immobile` 0.3, `marche`
0.5, `trot` 0.8, `sprint` 1.5 (défauts identiques dans le code). Canal : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md)
(`reborn:eau`). Banc : `REBORN_EAU_DEBUG=1` ([`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5).

---

## 11. Saut chargé (lot 5, étape A, 09/11)

> Codé le 09/11, **non publié**. Demande du 08/11 : « faut que ça aille plus loin qu'avant », puis du 09/11 : « faut aller
> loin », sommet gardé à 40, et une charge courte ne doit **jamais** aller moins loin qu'avant au même temps de maintien.
> Cadrage : `Maquettes/gameplan/17_lot5_mobilite/CADRAGE.md` §5.1, `_etude_murs_saut_v2.md` §6 (hors dépôt). Captures :
> `Maquettes/gameplan/17_lot5_mobilite/code_ab/saut_*.png`.

### 11.1 Pour le joueur

- **Maj + Espace tenues au sol** : un filet d'or fin se remplit au-dessus de la barre d'action, **1,2 s** pour la pleine
  charge ; relâcher fait partir le bond. Sous le petit repère crème (135 ms, le seuil d'avant), rien ne part.
- **Un cercle bleu** (le bleu de la course de chakra) se pose **là où l'on arrivera vraiment** : sur le sol, sur un toit, au
  pied d'un mur si l'arc en touche un. Derrière une colline ou un muret, il se voit en pâle à travers le décor. Au-delà de
  30 blocs, une colonne fine monte de l'arrivée (à plat, un cercle lointain ne fait qu'un ou deux pixels de haut).
- **On ne dirige plus le bond en l'air** : Z, Q, S, D ne changent rien pendant le vol, c'est ce qui rend le cercle juste. On
  choisit avant de partir : la visée (caméra) et la charge.
- **Réception nette**, sans glisser hors du cercle, et **sans dégât de chute**, pour tout le monde, quelle que soit la voie.
- Ce qui refuse (texte fin sous le filet, en vermillon) : à terre, étourdi, porté, douleur… (la raison du serveur) ; en
  garde ; sur l'eau ; recharge en cours (« Saut chargé dans N s ») ; pas assez de chakra ; trop chargé. **Rien ne charge,
  aucune recharge ne part** : la recharge ne démarre qu'à l'accord du serveur. Quitter le sol ou ouvrir un écran annule la
  charge. La charge ferme la concentration.

### 11.2 La loi

`c` = temps tenu / 1,2 s ; vitesse `1,5 + 1,65 × c` blocs par tick le long de la visée, élan vertical `0,6 + 0,35 × c`
ajouté même à plat, visée vers le bas comptée à plat, `vy ≤ 3,0` (sommet : **40 blocs**) ; en vol, frottement horizontal
× 0,98 (vanilla : × 0,91), gravité vanilla. Coût `0,3 + 2,7 × c²` % de la réserve, recharge `5 + 25 × c` s. Portée à plat
bornée à 96 blocs et par la distance de vue (§11.3).

| Tenu | Charge | À plat | Au mieux (angle) | Avant (à plat / au mieux) | Chakra | Recharge |
|---|---|---|---|---|---|---|
| 135 ms | 11 % | 24,5 | 37,4 (29°) | 22,2 / 27,4 | 0,33 % | 7,8 s |
| 300 ms | 25 % | 29,2 | 45,2 (30°) | 25,5 / 32,0 | 0,47 % | 11,3 s |
| 600 ms | 50 % | 38,6 | 59,9 (29°) | 31,5 / 40,1 | 0,98 % | 17,5 s |
| 900 ms | 75 % | 49,1 | 75,9 (28°) | 37,5 / 48,2 | 1,82 % | 23,8 s |
| 1,2 s | 100 % | **60,5** | **92,4** (29°) | 37,5 / 48,2 | 3 % | 30 s |

En blocs, sol plat, arrivée à la hauteur du départ (le tick de la réception avance en entier, comme dans le jeu). « Avant » :
l'ancien saut (vitesse `2,4 + 2,2 × c` en 0,9 s, frottement vanilla) **sans** poussée en l'air. **À chaque temps de maintien
de 135 ms à 1,2 s, le nouveau saut va plus loin, à plat comme au mieux, d'au moins un bloc** (vérifié tous les 15 ms par les
tests). La charge compte : de 24,5 à 60,5 blocs à plat (× 2,5), contre 22 à 37 avant (× 1,7). Sommet à 90° : 39,7 blocs.

La loi m1b du second tour (`s = 0,62 + 2,48 × c`, `vy ≤ 2,7`) est remplacée : elle allait moins loin qu'avant sous 0,85 s
et plafonnait à 33 blocs de haut.

### 11.3 Ce qui est vérifié, ce qui ne l'est pas

- **Le serveur décide** : il calcule la charge avec ses propres heures, vérifie le sol (par collision sous les pieds, jamais
  sur la parole du client), l'état, la garde, l'eau, la recharge et le chakra, ramène la visée dans 25° de son regard et
  répond `GO` ou `NON` (protocole : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §5.1 bis). Le
  mod joue le vol ; le serveur surveille la distance parcourue.
- **Le bond doit être pris** (critique du 09/11) : sans décollage dans `10 ticks + 2 × ping`, il tombe et il est remboursé
  (chakra, recharge) ; un `GO` arrivé trop tard n'est pas joué par le mod, qui le dit au serveur (remboursé aussi, aucune
  recharge à l'écran). Huit ticks après le décollage, le corps doit avoir suivi l'arc : se laisser tomber d'une falaise à la
  place du bond, ou heurter un mur d'entrée, retire la protection de chute. Un téléport finit le bond proprement.
- **L'ancien saut** (kind 5) ne passe plus par-dessus : fermé dès que le client parle le nouveau protocole, jamais pendant un
  bond, recharge partagée (avant : une seconde poussée gratuite en l'air et deux grands bonds par cycle).
- **Voie du Flux** : se retire pendant le bond (ni pilotage ni plafond de vitesse en l'air, ni gravité allégée, ni bond au
  double appui), sinon l'arrivée tombait en deçà du cercle.
- **Le cercle ne ment pas** : banc `REBORN_LOT5_DEBUG=saut` (09/11), 11 sauts réels (135 ms à 1,2 s, visée de 0 à 60°, un
  mur, un toit) : **écart de 0,00 bloc** entre l'arrivée prédite et l'arrivée réelle ; à plat, l'aperçu égale la loi pure.
  Sur un vrai serveur, la charge peut différer d'un ou deux ticks de latence (quelques blocs en charge partielle) : le cercle
  est recalculé au `GO` avec la vraie vitesse.
- **Distance de vue** : la **vraie arrivée**, relief compris, est gardée dans un chunk chargé, `(distance − 1) × 16`, 96 au
  plus. À 5 chunks, 64 blocs, et le filet l'écrit. Depuis un point haut, la portée à plat mentait (pleine charge à 29° :
  92 blocs à plat, 101 avec 20 blocs de chute, 111 avec 60) : serveur et mod simulent l'arc sur la carte des hauteurs et
  réduisent la vitesse jusqu'à tenir la borne. Chunk non chargé sur l'arc malgré tout : point pâle au dernier point connu,
  le saut part quand même.
  **Non vérifié sur le dev** (`view-distance`, `simulation-distance`, `prevent-moving-into-unloaded-chunks`) : la recette
  du §11.4 le mesure.
- **Abus restant** : prendre vraiment un petit bond au bord d'une falaise protège de la chute qui suit, tant que les dégâts
  de chute ne sont pas rétablis (§9) ; se laisser tomber sans prendre le bond ne protège plus.
- Pas encore : pose de charge vue des autres (état de `reborn:anim`), son à nous (vanilla `ENTITY_BREEZE_JUMP` en attendant).

### 11.4 Recette en jeu

1. Maj + Espace au sol : le filet se remplit en 1,2 s, le cercle se pose sur le sol, sur un toit, au pied d'un mur visé.
2. Relâcher à 135 ms, 300 ms, 600 ms, 1,2 s, à plat puis à 30° : on arrive dans le cercle, quoi qu'on tienne en l'air ; à
   plat environ 25, 29, 39, 60 blocs ; à 30° et pleine charge, environ 92.
3. Chakra en baisse de 0,3 %, 0,5 %, 1 %, 3 % ; icône de recharge à 8, 11, 17,5, 30 s.
4. Grand saut (90 blocs) : aucun dégât, aucun trou de chunk près de l'arrivée, FPS relevés (`REBORN_FPS_DEBUG=hud`).
5. Distance de rendu à 5 : « Portée bornée : 64 blocs », on arrive à 64. Depuis le mont des Hokage, pleine charge vers le
   village : le cercle et l'arrivée restent sous la borne, aucun trou de chunk.
6. À terre, étourdi, surchargé, en garde, sur l'eau : rien ne charge, aucune recharge. Sans chakra : « Pas assez de chakra ».
7. En concentration : la charge ferme le mode. En vol : double saut et Maj (escalade, onde) sans effet. Voie du Flux, course
   allumée : pendant le bond, tourner la caméra ne pilote pas, on arrive dans le cercle.
8. Deux comptes : l'autre voit le bond fluide et entend le départ.

## 12. Byakugan (lot 5, étape B, 09/11)

> Codé le 09/11, **non publié**. Demande du 08/11 : « c'est comme la vidéo que je te montre » ; puis du 09/11 : « l'encre ne
> donne rien c'est pas beau […] fais un vrai truc », et `ByakuganVision.java` (ShinobiSense) ne sera pas commité ; puis,
> le même jour : « faut tout de même faire comme sur ketto avec les encre c'est bien » (l'encre blanche revient, propre) ;
> le 10/11 : « tu peux pas juste faire un truc où les blocs et joueurs sont contourés ? » (5e tour, ci-dessous). Un
> vrai Byakugan à nous : ShinobiCore décide, le mod dessine. Technique : [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md)
> §1 quinquies, protocole et réglages : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §4 duodecies.
> Captures : `Maquettes/gameplan/17_lot5_mobilite/code_ab/byak_*.png` (hors dépôt).

### 12.1 Pour le joueur

- **Touche O**, en bascule : un appui ouvre la vision (fondu de 0,35 s, sans flash), un autre la ferme (0,27 s). Seulement
  pour les clans **Hyūga** et **Ōtsutsuki** (`dojutsu.byakugan.clans`), avec au moins un œil qui voit (un Hyūga qui a perdu
  ses deux yeux ne l'a plus). Pour les autres, la touche ne fait rien.
- **Ce qu'on voit** : le monde devient **pâle, clair, gris-blanc et froid**, presque sans couleur, avec une brume légère
  au loin ; les clairs s'arrêtent avant le blanc pur, les faces d'un bâtiment gardent leur écart (le village ne tourne plus
  au blanc sur blanc). **Depuis le 8e tour (14/11), seuls les volumes sont cernés** (voir plus bas ; la suite de ce paragraphe décrit le 7e tour, remplacé). Chaque bloc était contouré d'un trait blanc net (~2 px), l'encre de Ketto : silhouettes, arêtes
  entre les faces et limites entre deux blocs d'un même mur ou d'un même sol (deux pierres côte à côte sont cernées
  chacune), jamais sur les textures ni dans le ciel ; au loin le trait s'affine, et les limites s'effacent quand un bloc ne
  fait plus que quelques pixels (un toit lointain n'est pas un treillis) ; ce qui se découpe sur le ciel (toit, cime,
  façade) est toujours cerné ; un sol vu au ras ne porte pas de trait ; au bord du monde, le terrain et son trait se fondent
  ensemble dans l'horizon. Pendant la vision, **les feuilles sont pleines** (un houppier est une masse de blocs sombre,
  cernée bloc par bloc, sans dentelle) et **les plantes basses disparaissent** (herbes, fleurs, cultures : le Byakugan voit
  au travers) ; l'option « Feuilles découpées » du joueur n'est pas touchée et tout revient à la fermeture. Les masses
  sombres (toits, troncs, feuillages, roche à l'ombre) restent sombres, le trait blanc s'y lit. Les traits arrivent avant
  la couleur et partent avant elle.
  Les **personnes** à moins de 50 blocs ressortent en
  **cyan** : corps cyan pâle, réseau de chakra vif qui pulse depuis le cœur, tenketsu presque blancs ; de loin, une tache
  cyan franche ; elles sont **contourées de blanc** comme les blocs. **Derrière un mur**, on les devine par un liseré cyan
  pâle et doux (jamais de nom, jamais de chiffre). Soi-même : gris, sans réseau, contouré.
- **La nuit** reste la nuit, **sous terre** une grotte reste sombre : il ne reste presque que les traits blancs et le cyan,
  avec une faible lueur près de soi ; on suit un couloir à ses arêtes. Rien n'est lu à travers le décor sauf les personnes.
- **Coût** : 0,1 % de la réserve de chakra par seconde (réserve pleine : environ 16 min). Réserve vide : la vision se ferme.
- **Rappel** « Vision du Byakugan [O] » en bas à droite **pendant la vision seulement**. Aucune annonce au tchat.
- **Ce qui coupe** : KO (coupure nette, sans fondu), mort, cinématique, douane, porté ou toute entrave qui refuse les
  techniques, écran noir du staff, changement de personnage. Entravé ou sans mudra : la vision reste possible.
- **Concentration** : compatible. Le Byakugan prend l'effet d'écran ; la concentration reprend le sien à la fermeture.
- **Ce que voient les autres** : de près (12 blocs), les veines des tempes du porteur se gonflent. Rien d'autre.

### 12.2 Ce qui est vérifié, ce qui ne l'est pas

- **8e tour (14/11)** : le 7e est rejeté (« des fils de partout […] ne les mettre que sur la fin des volumes, pour les
  contours de bâtiment, d'arbres », « il manque l'effet d'encre »). **Plus de grille ni de trait d'arête** : un trait
  seulement là où un volume s'arrête devant quelque chose de nettement plus loin (plus d'un bloc environ) ou devant le
  ciel : contour d'un arbre, d'un bâtiment, d'un muret, d'une personne. **Papier et encre** : feuillages et troncs en
  masses sombres cernées de blanc, sol et murs éclairés en gris très pâle, ciel plus sombre que le sol ; le lointain
  pâlit et perd son trait (48 à 130 blocs) ; sur un fond pâle le trait passe à l'encre sombre (à confirmer). **Boucles
  d'encre** : quatre grands anneaux sombres qui dérivent lentement sur les bords de l'écran (à juger en jeu). **Sons à
  nous** : un tintement de cristal à l'ouverture, une retombée sourde à la fermeture (remplacent les sons vanilla).
  Coût : ~+1,2 ms par image (1080p, Intel Iris Xe). Captures `byak_v8_*.png`. Détail : `MOD_HUD.md` §1 quinquies.
- **7e tour (11/11)** : « les blocs contourés » partout : la grille des blocs cerne aussi deux blocs d'un même plan (murets,
  escaliers, places, toits), le pixel étant replacé dans le monde par la projection de l'image ; plis gardés sur une face vue
  en raccourci (le haut d'un muret lointain) ; bord du monde : terrain et encre fondus ensemble au ton de l'horizon (plus
  d'arbre fantôme cerné de gris) ; ciel de jour plus sombre (0,33 à 0,48) ; plus de pointillés aux pieds du porteur ni de
  grille sur lui. Captures `byak_v5_*.png` refaites. **Coût** (1080p, Intel Iris Xe) : **+1,2 à +1,8 ms** par image (~1,45 en
  moyenne) ; filtre compilé dès que le serveur donne le droit (première ouverture ~31 ms au lieu de 129) ; bascule des
  feuilles : image la plus longue ~14 à 16 ms à l'ouverture, 9 à 14 ms à la fermeture (recompilation étalée par anneaux).
- **6e tour (10/11 au soir)** : vues de Konoha et bêtes refaites → `byak_v5_*.png` (remplacées). Village d'en haut, au sol
  et de nuit : maison cernée en continu (faîte, avant-toit, piliers), plus de dentelle sous l'auvent ni de tirets aux murets ;
  arbre de près : plus de flammes blanches sur le sol vu au ras ; feuilles qui tombaient à l'ouverture retirées ; ciel et
  tons moyens plus sombres le jour (un village clair vu d'en haut n'est plus délavé). **Coût** (1080p, Intel Iris Xe) : **+2,15 à
  +2,3 ms** par image ; bascule des feuilles : image la plus longue ~17 ms à l'ouverture, ~22 ms à la fermeture (une à
  37 ms), 129 ms à la toute première ouverture, à froid.
- **5e tour (10/11)** : vues de la copie locale de Konoha (forêt de jour et de nuit, village d'en haut, au sol et de nuit,
  salle souterraine, pente, arbre de près) et vraies bêtes derrière un mur et à découvert (`byak_entites`) →
  `Maquettes/gameplan/17_lot5_mobilite/code_ab/byak_v5_*.png`. Houppiers cernés en continu bloc par bloc, ni dentelle ni
  tirets, aucun trait d'herbe. **Coût** (1080p, Intel Iris Xe, quatre alternances) : **+1,9 à +2,35 ms** par image ; bascule des
  feuilles : image la plus longue 14 ms à l'ouverture, 16,5 ms à la fermeture, aucune au-delà d'un 60e de seconde. Les
  mesures du 09/11 ci-dessous valent pour l'encre d'avant.
- Banc `REBORN_LOT5_DEBUG=byak` (09/11, `audit/Lot5ByakDebug`, monde de dev, serveur simulé au format exact du canal) :
  champ, rue façon Konoha (pierre, bois, enduit), forêt, deux personnes derrière un mur (détectées cachées : 2 sur 2), nuit,
  grotte fermée, fondu image par image (90 % à 0,35 s, sortie finie à 0,27 s), rappel, concentration puis Byakugan (un seul
  filtre), coupure au KO, 1366 × 768 : 0 échec (09/11 au soir, après la critique). **Encre mesurée** (part des pixels de
  traits) : rue du banc 1,4 %, champ et forêt du banc 0,6 à 0,8 %, vraie forêt de Konoha 0,6 à 0,7 % de jour comme de
  nuit, village de Konoha vu d'en haut 0,1 % de jour et 0,4 % de nuit, salle souterraine 0,4 % : **sous la cible de 2 à 6 %,
  par choix** (seuls les volumes portent l'encre ; les 2,2 % d'avant en forêt étaient des points isolés sur les feuilles).
  Fourmillement après un pas de 0,03 bloc : 1 à 17 % selon la vue, mesure peu fiable (un pas déplace de plus de 2 px les
  traits proches, les bêtes bougent) : la marche reste à juger en jeu. **Coût mesuré** (même vue de forêt, 1080p, fenêtre
  en arrière-plan, alterné deux fois) : **+1,5 à +2,5 ms par image** selon le passage (5,2 → 6,7 ms ; 5,3 → 7,7 ms), pour
  17 lectures par pixel.
- Serveur : `ByakuganReglesTest` (clans, yeux, coût, bornes, blocages, reprise de `/sense`). **Non joué sur le vrai Purpur**
  du dev : réponse du canal, débit réel du chakra, coupure au KO par `EtatChangedEvent`, signes vus par un second compte,
  reprise de `/sense dojutsu` à côté du jar ShinobiSense déployé.
- **Non vérifié** : EntityCulling et Sodium (le pack du launcher : Sodium compile les chunks à sa façon, la profondeur
  gardée, les feuilles pleines et les plantes cachées restent à voir avec lui) ; le portable iGPU ; le monde Konoha du serveur (le banc tourne sur des
  décors à nous et sur une copie locale de Konoha).

### 12.3 Recette en jeu

1. Un Hyūga : O, fondu doux, monde pâle et froid cerné de traits blancs fins, rappel « Vision du Byakugan [O] » ; O encore : retour en
   0,27 s. Un Uchiha : O ne fait rien, rien au tchat.
2. Un autre compte à 10, 30 et 45 blocs : silhouette cyan, réseau qui pulse ; derrière un mur et dans une maison : liseré
   cyan pâle ; à 55 blocs : rien. Aucun nom, aucun pourcentage nulle part.
3. Konoha de jour, la forêt, de nuit et dans une grotte : lisible, la nuit garde sa nuit, rien ne fourmille en marchant ;
   en forêt, chaque houppier est une masse pleine cernée bloc par bloc, sans dentelle ; l'herbe et les fleurs ont disparu ;
   à la fermeture, feuilles découpées (si l'option l'est) et herbe reviennent, et « Feuilles découpées » n'a pas bougé
   dans Options › Graphismes, même après avoir quitté le jeu en pleine vision.
4. Rester une minute : chakra en baisse d'environ 6 % ; à sec : la vision se ferme seule.
5. Passer KO pendant la vision : coupure nette ; se relever : O de nouveau possible.
6. Tenir Y (concentration) puis O : le Byakugan prend l'effet, la concentration revient quand on le ferme.
7. L'autre compte, à moins de 12 blocs du porteur : veines aux tempes ; plus loin : rien.
8. `/sense dojutsu` d'un Hyūga (et `/sense byakugan` du staff) : même bascule que O, plus d'ancienne lecture du chakra.
9. FPS relevés sans et avec, sur le portable (`REBORN_FPS_DEBUG=hud` n'en tient pas compte : comparer à la main).
---

## 13. Marche sur les murs : essai technique (lot 5, étape C0, 24/11 ; étape C1a, 26/11)

> **Essai C0** : publié le 24/11 (`reborn-hud` 0.4.207, ShinobiAbilities) et **validé en jeu** le même jour (« J'ai fait les
> trucs et ça marche bien ») : le corps à 90° avec sa boîte couchée tient sur le vrai serveur, l'approche est retenue.
> **Étape C1a** (26/11, `reborn-hud` 0.4.208, ShinobiAbilities) : les deux demandes du 24/11, une meilleure caméra et le
> passage de mur en mur. Caméra « suit la paroi » choisie le 25/11. Déploiement dans un ordre précis : §13.4.
> Ce n'est toujours **pas** la fonctionnalité : l'accès reste `/murtest on`, réservé au staff, pour soi.
> Demande du 08/11 : « la hitbox doit suivre le joueur sur le mur, il doit bien voir, et ça doit vraiment bien marcher ».
> Étude et cadrage (hors dépôt) : `Maquettes/gameplan/17_lot5_mobilite/_etude_murs_saut_v2.md`, `CADRAGE.md` §5.3.
> Protocole et commande : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md) §5.1 ter ; mod :
> [`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §1 septies. Décisions : [`../pilotage/DECISIONS.md`](../pilotage/DECISIONS.md)
> (24/11 et 25/11).

### 13.1 Ce que l'essai fait

- **Éteint pour tout le monde.** Un membre du staff tape `/murtest on` : lui seul peut alors s'accrocher. Aucun joueur ne
  peut l'activer, et sans permis ni le mod ni le serveur ne font quoi que ce soit. C1a ne change rien pour un joueur sans
  permis.
- **S'accrocher** : pousser trois ticks contre un mur dont la face tournée vers soi est pleine (la règle du serveur, que
  le mod suit aussi depuis C1a ; en C0 il exigeait un bloc entier). Le serveur valide, puis le corps bascule en 0,3 s :
  **pieds sur la paroi, corps à 90°, tête vers l'air**.
- **Marcher** (C1a) : Z Q S D comme au sol, dans le repère de la paroi. Avant = la visée de la caméra projetée sur le mur.
  Plus de crans ni de base figée : 10° de souris donnent 10° de cap. 3,2 blocs/s, 5,6 avec la touche de sprint. On
  s'arrête pile au bord du mur et sous un obstacle.
- **Passer de mur en mur** (C1a) : on pousse 2 ticks contre une limite, dans la direction voulue ; la limite choisit le
  passage. Le cap est gardé : aller à droite reste aller à droite, monter reste monter.

  | Passage | Quand | Ce qu'on voit | Durée |
  |---|---|---|---|
  | **Coin rentrant** | un mur perpendiculaire barre la marche | le corps passe sur ce mur | 4 ticks |
  | **Coin sortant** | le mur s'arrête, l'autre face du bloc d'angle est pleine | le corps tourne l'angle sur un arc de 1,2 | 10 ticks à la marche, 7 en course |
  | **Sommet** | la montée bute sur la fin du mur, un corps debout tient sur le toit | on monte de 0,6 puis on avance de 1,2 : debout, face au toit, 0,3 derrière l'arête | 6 ticks |
  | **Pied** | le sol est à moins d'un bloc sous le corps en descendant | on se pose au sol à 0,9 du mur, dos au mur | 4 à 6 ticks |

  Le serveur valide chaque coin. Un coin refusé **ne fait plus tomber** : on reste au bord, au mur, et il faut repousser.
  Sans réponse en une seconde, ou touche relâchée avant le départ, le coin est abandonné. Sous un auvent, le sommet n'est
  pas tenté : on reste au bord.
- **Lâcher** : Espace, ou la touche de course de chakra (X). Le serveur lâche de lui-même s'il n'y a plus de mur, au KO,
  à la mort, à une téléportation, si l'état du joueur refuse la mobilité (étourdi, douleur, mode staff).
- **Boîte de collision** : couchée avec le corps (1,75 × 0,6 × 0,6) chez tous les joueurs qui ont le mod. Pendant un
  virage, c'est le cube de 0,6. Le serveur, lui, tient un cube de 0,6 au milieu du corps (pose de nage fixée, gravité
  nulle).
- **Caméra** (C1a) : **la paroi devient le sol**. Le haut de l'écran suit la normale du mur (roulis compris) ; la souris
  tourne et incline la vue comme au sol. Entrée, sortie et coins tournent la vue en douceur, à 100° par seconde au plus :
  un quart de tour dure environ une seconde. Distance, épaule et hauteur sont celles de la caméra d'épaule du joueur. Plus
  de recul imposé, d'épaule côté air ni de corps effacé. La 1re personne passe en épaule le temps du mur, puis revient.
- **Réglage** « Caméra sur les murs » (Paramètres › Interface › Caméra) : « Suit la paroi » (défaut) ou « Droite ». « Droite » garde
  la vue d'aplomb, corps couché en travers de l'écran, avec des touches fixes : Z monte, S descend, Q et D vont à gauche
  et à droite de l'écran.
- **Tête** : elle suit le regard, bornée à 80° de côté et 75° en hauteur. Les autres joueurs voient le corps tourner aux
  coins, à 20° par tick au plus.
- **Hors essai** : ni chakra, ni déblocage, ni HUD, ni plafond, ni bond d'un mur vers un autre mur visé. L'étape C1b, après
  l'avis du propriétaire sur la caméra, apportera le déblocage par la mobilité, le chakra, la course exigée, la chute sur
  coup reçu, le contrôle de vitesse du serveur, les sons, le cercle de pas, la pose d'attente et le HUD.

### 13.2 Ce que l'essai doit prouver

**C0, les 8 essais de l'étude §5** (validés en jeu le 24/11 : l'approche est retenue) :

| N° | Essai | Réussi si |
|---|---|---|
| 1 | 60 s immobile puis 60 s de marche, sans filet | 0 expulsion, 0 paquet refusé, 0 correction |
| 2 | Le même avec 150 à 250 ms de latence | pas d'expulsion, corrections rares |
| 3 | Bords, pied du mur, obstacle au-dessus | arrêt net, aucun passage à travers un bloc |
| 4 | Second compte, F3 + B | la boîte blanche recouvre le corps couché |
| 5 | 30 clics par zone (crâne, torse, pieds) | au moins 27 comptés sur 30, aucun à 0,5 bloc à côté |
| 6 | Une flèche et trois techniques sur le marcheur | elles touchent le corps là où on le voit |
| 7 | Six cadrages (accroche, monter, descendre, de côté, le long, dos au mur) | on voit où l'on va |
| 8 | Nom et bulle, KO sur le mur, FPS | nom au-dessus de la tête, KO propre, pas de chute de FPS |

**C1a** : on sait toujours où va Z ; les coins, le sommet et le pied passent sans chute ; le second compte voit le corps
tourner aux angles avec la boîte sur le corps ; après deux minutes de coins, `/murtest bilan` lit 0 expulsion et 0 paquet
refusé. La recette est au §13.5.

### 13.3 Ce qui est vérifié, ce qui ne l'est pas

- **Banc `REBORN_MUR_SPIKE=1`** (monde solo, serveur simulé au format du canal). Contrôles de C0 : boîte couchée sur les
  quatre faces et qui contient le cube du serveur, pose tenue, distance à la paroi, vitesses (3,200 et 5,600 blocs/s),
  arrêts au bord et sous un obstacle, sorties, formats des messages, rien d'envoyé sans permis, abandon après une seconde
  sans réponse, écho d'un lâcher précédent. Ajouts de C1a :
  - caméra : haut de l'écran = normale du mur, 85° atteints en 1,01 s, au plus 100,4° par seconde, retour au monde en
    1,36 s sans saut (gel de 0,4 s compris), cadrage du joueur rendu, séisme au mur sans perte du roulis, « Droite » sans
    roulis avec ses touches fixes, 1re personne rendue ;
  - passages : `P` à 0,9000 de la nouvelle paroi après chaque coin, cap gardé, deux coins enchaînés, tour complet d'un
    tronc de 1 × 1 en 4 messages, refus de face (toujours au mur), annulation, abandon, sommet (sur le toit, face au toit),
    auvent (arrêt), pied (au sol à 0,900, dos au mur, aussi en visant le haut du mur), un seul `lache` par sortie ;
  - virage vu d'un voisin : 20° par tick au plus, cube pendant le virage.

  Deux passes de suite à « FIN (0 échec(s)) », 146 contrôles. 32 captures (`mur_cam_*`, `mur_coin_*`, `mur_sommet`,
  `mur_pied`, `mur_pied_haut`, et les 9 de C0).
- **Tests du plugin** (`MurReglesTest`, 45, contre 21 en C0) : lecture des messages et des numéros, faces voisines, arête
  du coin sortant, coin rentrant et sortant sur les 4 faces, coordonnées négatives, tronc de 1 × 1, mur d'un bloc
  d'épaisseur, absence de bloc d'angle, fenêtre de virage, tri de la cadence, lignes du bilan.
- **Jamais vérifié** : C1a contre le vrai serveur. Le banc ne parle qu'à un serveur simulé, et le solo garde un corps
  debout. Ni les coins avec de la latence, ni le sommet face au contrôle de déplacement de Paper, ni le virage vu d'un
  second compte n'ont tourné pour de vrai.
- **Limites connues** :
  - les autres joueurs voient le corps tourner un peu **avant** que le marcheur bouge (le serveur annonce la nouvelle
    face dès l'accord) ; un coin annulé après l'accord leur montre un aller-retour du corps ;
  - dans le creux d'un coin rentrant, la caméra est collée au corps le temps du virage (anti-obstacle de l'épaule) ;
    au pied du mur en visant le haut, elle bute sur le sol jusqu'au premier bloc de montée ;
  - un nom sur deux lignes (nom et « NPC » d'un mannequin) se chevauche sous le roulis ; un nom d'une ligne reste lisible ;
  - au pied, le roulis ne se défait qu'une fois debout : environ une seconde de vue encore penchée ;
  - coins enchaînés avec beaucoup de latence : le serveur peut juger depuis une position en retard et refuser ; on reste
    au bord, il faut repousser ;
  - au sommet, le serveur rend la gravité dès le début de la sortie : surveiller « Paquets de déplacement refusés au mur »
    dans `/murtest bilan` ;
  - plafonds absents ; la caméra « Droite » aux coins n'est pas couverte par le banc ;
  - comme en C0 : hors champ, nom et bulle sont placés comme pour un corps debout ; l'ombre au sol n'est pas retirée ;
    les modpacks qui cachent les entités d'après leur boîte (EntityCulling) n'ont pas été vus avec un corps couché.
- **Tout reste réservé au staff** par `/murtest on`.

### 13.4 Déploiement

**ShinobiAbilities d'abord**, puis redémarrage du serveur (par le propriétaire), **avant** d'essayer le mod 0.4.208. Un mod
C1a face au plugin C0 lâche le joueur à chaque coin. Au coin rentrant, le plugin C0 accepte la face mais renvoie un `etat`
sans numéro : le mod n'y voit pas d'accord et lâche (« face imprévue »). Au coin sortant, le plugin C0 refuse sans `a` et
lâche. On prendrait ces chutes pour un défaut de C1a. Le plugin C1a face au mod 0.4.207 ne gêne pas : ce mod n'émet pas
`face` et ignore les champs nouveaux.

### 13.5 Recette en jeu (C1a)

1. `/murtest on`, s'accrocher à un mur. Monter, longer, descendre : Z va toujours là où l'on regarde sur la paroi.
2. Angle rentrant d'une ruelle, angle sortant d'une maison, pilier de 1 × 1 : on tourne sans tomber.
3. Monter jusqu'au toit : on se retrouve debout sur le toit. Sous un auvent : arrêt au bord.
4. Descendre jusqu'au sol : on se pose debout, dos au mur.
5. Paramètres › Interface › Caméra › « Caméra sur les murs » = « Droite » : vue d'aplomb, Z monte, D va à droite de l'écran.
6. Second compte : le corps tourne aux angles, F3 + B montre la boîte sur le corps.
7. Après deux minutes de coins, `/murtest bilan` : 0 expulsion, 0 paquet refusé. Lire aussi « Changements de face
   refusés » et « Dernier refus de face ».
