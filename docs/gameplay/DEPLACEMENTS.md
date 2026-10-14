# Déplacements : marche, trot, sprint

> **Fiche système, à jour au 2026-10-14** (lot « allures », codé, **non publié** : ShinobiAbilities, `reborn-hud`).
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
