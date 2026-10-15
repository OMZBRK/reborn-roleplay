# Déplacements : marche, trot, sprint

> **Fiche système, à jour au 2026-10-15** (lot « allures », codé, **non publié** : ShinobiAbilities, `reborn-hud` ;
> **marche sur l'eau** ajoutée le 15/10, §10, non publiée).
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

**On marche par défaut, on trotte en tenant la touche de sprint, on sprint en la tapant deux fois** : la marche est
l'allure RP (lente, discrète), le trot est gratuit, le sprint est rapide mais court (endurance), et rien ne s'affiche
tant qu'on ne sprinte pas.

---

## 2. Les trois allures

| Allure | Touche | Vitesse (× la marche Minecraft, ~4,3 blocs/s) | Coût | Animation |
|---|---|---|---|---|
| **Marche** | rien (ZQSD) | **0,75** → ~3,2 blocs/s | — | la **démarche** choisie (menu « . »), cadence 0,85 |
| **Trot** | **touche de sprint maintenue** (Ctrl gauche par défaut) | **1,15** → ~5 blocs/s | gratuit (faim vanilla) | course, cadence 1 |
| **Sprint** | **double appui** sur la touche de sprint, le second maintenu | **1,55** → ~6,7 blocs/s | **20 d'endurance par seconde** (~5 s avec 100) | course, cadence 1,3 + **traînée de fumée blanche** |

- La touche est **celle du sprint de Minecraft** (Options → Commandes → Déplacement → Sprint), donc **rebindable** et
  sans conflit avec les touches Reborn (R interaction, F ramasser, Z dégainer, E sacoche, X lâcher, L course ninja,
  V dash, C garde…). RedLine utilise Shift, qui est chez nous l'accroupi (et le saut de chakra Shift + Espace).
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
| **Étourdi** (ShinobiCombat) | ✅ | ✅ | ❌ |
| **Porter quelqu'un** | ralentie (porteur) | selon l'état | ❌ |
| **Garde** (C maintenue) | immobile | — | ❌ |
| Faim ≤ 30 % (vanilla) | ✅ | ❌ | ❌ |
| **KO**, **porté**, véhicule, nage, vol, créatif | allure **neutre** (le modificateur est retiré, une autre mécanique gère la vitesse) |
| **Course ninja** (L) / Voie du Flux | allure **neutre** : la course possède la vitesse ; lancer la course coupe le sprint |

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

## 9. En attente

- ❓ **Course ninja depuis le sprint** : aujourd'hui la course de chakra reste sur sa touche (L). Proposition : ne la
  permettre que depuis le sprint (« quatrième rapport »), ce qui ferait du sprint la porte d'entrée de la course ninja.
- ❓ **Faim** : le vanilla coupe trot et sprint sous 30 % de faim ; RedLine ne coupe le sprint qu'à 0 %.
- ❓ **Dégâts de chute et roulade** (GAMEPLAN §7.3) : non traités ici.

---

## 10. Marche sur l'eau (15/10)

> Codé le 15/10, **non publié** (ShinobiAbilities `mobility/MarcheEau`, mod `eau/`). Demande : « fais la marche sur
> l'eau avec les mêmes visuels d'eau qui bouge en marchant ; il faut vraiment que ce soit aussi bien, si ce n'est
> mieux » (référence Shinoda, [`../etudes/CONCURRENCE_SHINODA.md`](../etudes/CONCURRENCE_SHINODA.md)). Captures, GIF
> et planche comparative (hors dépôt) : `Maquettes/gameplan/eau_v6/` (version actuelle ; v5 : `eau_v5/`,
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

- À **chaque pas** (pied gauche puis droit), une **onde** part du pied : petite au point d'impact, elle s'étale vite
  puis ralentit, son contour ondoie pendant l'expansion, puis elle s'efface (~1,6 s) ; une **seconde onde** plus faible
  la suit 0,15 s plus tard. Allure : galet arrondi, convexe et irrégulier (0,8 à 1,3 bloc à la fin), **trait fin et
  doux** bleu-blanc pâle (eau du biome mêlée de blanc, ~30 % d'opacité), très léger éclaircissement intérieur, même
  lumière que l'eau (sombre la nuit) et brouillard. Les ondes des pas successifs se chevauchent.
- **Gouttelettes** : à chaque pas, 3 à 4 gouttes d'eau du jeu jaillissent du pied et retombent (6 à 7 au sprint) ; à
  la **retombée d'un saut**, une onde plus grande et une petite gerbe (12 gouttes). Pas de nuage blanc (la bouffée du
  sprint des allures reste coupée sur l'eau).
- **Sons** : un bruit de pas dans l'eau à chaque pas (éclaboussure légère + clapotis, pitch et volume variés), plus
  marqué au sprint, une éclaboussure à la retombée. Pluie et orage : rien de plus.
- Les autres joueurs voient les ondes et gouttes et entendent les pas **sans réseau** : chaque client détecte lui-même les joueurs debout à la
  surface de l'eau (pieds à la surface, pas dans l'eau) et pose leurs effets.
- Réglages : constantes en tête de `eau/Ondes.java` (diamètre, aplatissement, irrégularité, durées, seconde onde,
  ondoiement, expansion, retombée, flou et opacité du trait, intérieur, part de blanc, écart des pieds, gouttelettes,
  volume des sons).

### 10.3 Technique

- **Physique** : Minecraft calcule le mouvement côté client. Quand le serveur le permet (`reborn:eau`), le mod donne au
  bloc d'eau de surface une **forme de collision** (hauteur de l'eau + 2 cm, seulement si les pieds sont au-dessus,
  seulement pour le joueur local — `mixin/BlockStateEauMixin`). Le serveur accepte ces positions (aucun contrôle de
  vol ne se déclenche au-dessus de l'eau) ; il détecte lui-même qui se tient sur l'eau pour débiter le chakra.
- **Rendu** (Sodium et Iris) : un décalque très léger dessiné **après l'eau translucide**
  (`LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN`) avec le pipeline de l'ombre des entités du jeu (translucide, sans
  écriture de profondeur, décalé vers la caméra, brouillard) : chaque flaque est un petit maillage de 48 × 3 quads
  (remplissage très faible, puis le trait en deux bandes dont l'opacité monte jusqu'au contour et redescend : trait fin
  et doux, lisse à toute distance), texture d'un seul texel blanc (`textures/environment/flaque.png`), couleur
  assombrie selon la lumière. L'eau des chunks n'est jamais touchée.
- **Coût** mesuré au banc (portable, iGPU Intel Iris Xe, avec Sodium) : ~0,24 ms CPU par image en moyenne pendant le
  banc (~5 ondes à l'écran, ~730 quads), 0 quand il n'y a pas d'onde ; gouttelettes = quelques particules du jeu par
  pas. Mémoire ajoutée : texture 1 texel, ~9 Ko de tableaux (128 ondes au plus), tampons réutilisés de 64 Ko (CPU)
  et 3 × 128 Ko (GPU). Aucune allocation par image ; rien au-delà de 56 blocs ni caméra sous l'eau.
- **Shader Iris** : rien de particulier, le décalque est dessiné après l'eau du pack (non vérifié en jeu : Iris
  n'est pas dans le modpack).
- **Limites** : un joueur téléporté au-dessus de l'eau avant que ses chunks n'arrivent tombe dedans (Saut pour
  remonter) ; les plantes aquatiques qui affleurent portent aussi (eau dans le bloc) ; une chute d'eau ne porte pas.

### 10.4 Réglages

`ShinobiAbilities/config.yml`, `mobility.marche-eau` : `enabled` (true), `chakra-par-seconde.immobile` 0.3, `marche`
0.5, `trot` 0.8, `sprint` 1.5 (défauts identiques dans le code). Canal : [`../technique/PLUGINS_REFERENCE.md`](../technique/PLUGINS_REFERENCE.md)
(`reborn:eau`). Banc : `REBORN_EAU_DEBUG=1` ([`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5).
