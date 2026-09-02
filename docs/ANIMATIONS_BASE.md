# Animations de base du personnage — catalogue de référence

> Référence design pour l'animation du joueur Reborn (mod-hud + PlayerAnimationLibrary).
> Inspirée des systèmes d'Elden Ring, Sekiro, Ghost of Tsushima et des jeux Naruto
> (Ultimate Ninja Storm, Shinobi Striker). Sert de socle au **système de combat**.
>
> Statut technique de la couche anim : cf. `MovementAnimations.java`. Aujourd'hui les
> démarches/course/saut sont **locales** ; le passage en **multi-joueurs** (voir les
> autres) est en cours (Phase A client-side pour les états observables, Phase B canal
> serveur `reborn:anim` pour les états non-observables).

## Comment lire ce catalogue

Chaque animation a :
- **Prio** — P0 (indispensable, ressenti « le perso est vivant »), P1 (important),
  P2 (polish / plus tard).
- **Trigger** — ce qui la déclenche.
- **Réseau** — `observable` (chaque client la déduit seul → pas de packet, marche
  déjà cross-joueur par la Phase A) ou `sync` (état interne → nécessite le canal
  `reborn:anim` pour être vu des autres).
- **Loop** — bouclée / one-shot / hold-last (fige la dernière frame).
- **KF** — faisabilité keyframes que **je** peux te livrer (voir § dernière section).

---

## 1. Locomotion (P0 — le socle)

| Animation | Prio | Trigger | Réseau | Loop | KF |
|---|---|---|---|---|---|
| **Idle** (respiration légère, poids sur une jambe) | P0 | immobile au sol | observable | loop | ✅ simple |
| **Idle combat** (garde basse, corps tourné 3/4) | P0 | immobile + arme/mode combat | sync | loop | ✅ |
| **Marche** (déjà 6 styles Reborn) | P0 | déplacement lent | observable | loop | ✅ (existe) |
| **Course** (déjà) | P0 | sprint | observable | loop | ✅ (existe) |
| **Course ninja** (Naruto-run, bras en arrière) | P1 | touche dédiée | **sync** | loop | ✅ (existe) |
| **Saut** (déjà) | P0 | `!onGround` montant | observable | one-shot | ✅ (existe) |
| **Chute / airtime** (bras qui cherchent l'équilibre) | P1 | en l'air en descente | observable | loop | ✅ |
| **Réception / landing** (léger fléchi genoux) | P1 | contact sol après chute | observable | one-shot | ✅ |
| **Roulade / esquive** (dodge-roll Elden Ring) | P1 | double-tap direction / touche | sync | one-shot | ⚠️ Blender |
| **Backstep** (petit bond arrière Sekiro) | P2 | touche esquive sans direction | sync | one-shot | ⚠️ Blender |
| **Marche accroupie** (sneak) | P2 | shift + move | observable | loop | ✅ |
| **Strafe** (pas latéraux visés vers la cible) | P2 | lock-on + move latéral | sync | loop | ⚠️ |

**Note lock-on** : Elden Ring/Sekiro orientent le buste vers la cible en permanence
(strafe). C'est le plus gros multiplicateur de « ça fait AAA ». Dépend du système de
ciblage combat → à câbler avec la caméra 3e personne épaule (cf. mémoire
`camera-3e-personne`).

## 2. Idle / ambiant (P1 — la vie du perso)

Sekiro/GoT ont des micro-anims qui tournent aléatoirement quand on reste immobile.
Énorme sur le ressenti RP, faible coût.

| Animation | Prio | Trigger | Réseau | Loop |
|---|---|---|---|---|
| **Idle fidget** (rouler l'épaule, regarder autour) | P1 | immobile > ~8 s, aléatoire | observable | one-shot |
| **Idle « froid »** (se frotter les bras) | P2 | immobile + biome froid | observable | one-shot |
| **Ajuster le bandeau** (signature ninja) | P1 | immobile, aléatoire | observable | one-shot |
| **Regarder ses mains / craquer les doigts** | P2 | immobile long | observable | one-shot |

## 3. Combat taïjutsu (P0 pour le système de combat)

C'est le cœur du système que tu veux poser. Découpage M1/M2 façon Ultimate Ninja Storm
+ feel Sekiro (poussée/posture).

| Animation | Prio | Trigger | Réseau | Loop | Notes |
|---|---|---|---|---|---|
| **Combo M1 #1** (jab avant) | P0 | clic gauche | **sync** | one-shot | 1er de la chaîne |
| **Combo M1 #2** (crochet) | P0 | clic gauche enchaîné | sync | one-shot | fenêtre de combo |
| **Combo M1 #3** (coup de pied retourné) | P0 | 3e clic | sync | one-shot | finisher léger |
| **Attaque lourde M2** (coup chargé) | P0 | clic droit maintenu | sync | one-shot | hyper-armor début |
| **Lancer** (grab/projection) | P1 | touche saisie au corps-à-corps | sync | one-shot | + anim « projeté » sur la cible |
| **Parade / deflect** (Sekiro) | P1 | clic droit au bon timing | sync | one-shot | fenêtre parry |
| **Garde** (block maintenu) | P1 | clic droit maintenu | sync | loop | réduit dégâts/posture |
| **Esquive de combat** (i-frames) | P1 | touche esquive en combat | sync | one-shot | voir roulade §1 |
| **Guard break / posture cassée** (chancelle) | P1 | posture à 0 | sync | one-shot | ouverture punish |
| **Coup critique / execution** | P2 | punish sur ennemi ouvert | sync | one-shot | cinématique courte |

**Règle d'or combat** : toutes les anims de combat sont `sync` (état interne, pas
déductible par les autres clients) → elles **exigent** le canal `reborn:anim`. La Phase A
(observable) ne les couvre pas. C'est pour ça que le link cross-joueur doit être fait
avant/en même temps que le combat.

## 4. Réactions / hit (P1 — le feedback)

Sans réactions, les coups « traversent » et le combat est mou. Priorité haute pour le feel.

| Animation | Prio | Trigger | Réseau | Loop |
|---|---|---|---|---|
| **Hitstun léger** (tressaille) | P0 | encaisse un M1 | sync | one-shot |
| **Knockback** (repoussé) | P1 | encaisse un M2 | sync | one-shot |
| **Projeté au sol / knockdown** | P1 | grab / gros coup | sync | one-shot |
| **Relevée (get-up)** | P1 | fin de knockdown | sync | one-shot |
| **Bloqué avec succès** (recul de garde) | P1 | block qui absorbe | sync | one-shot |
| **Mort / KO** (RPK ShinobiCore) | P0 | HP/état = mort | sync | hold-last |

## 5. Spécifique ninja (P1/P2 — l'identité Naruto)

| Animation | Prio | Trigger | Réseau | Loop | Notes |
|---|---|---|---|---|---|
| **Signes de mains** (hand-seals) | P0 | cast jutsu | sync | one-shot | séquence, cf. `zenkai-mechanics-roadmap` |
| **Focus chakra** (aura, posture concentrée) | P1 | maintien touche chakra | sync | loop | + particules |
| **Substitution (Kawarimi)** | P1 | esquive parfaite | sync | one-shot | téléport + bûche |
| **Mode éveil** (transformation) | P2 | mode spécial | sync | one-shot→loop | buff visuel |
| **Course sur mur / eau** | P2 | contact surface + chakra | sync | loop | mécanique mouvement ninja |
| **Atterrissage héroïque** (3-point landing) | P2 | grosse chute + chakra | sync | one-shot | style |

## 6. Social / RP (P1 — c'est un serveur RP)

| Animation | Prio | Trigger | Réseau | Loop |
|---|---|---|---|---|
| **Salut / bow** | P1 | émote | sync | one-shot |
| **Assis** (déjà : `idle_sit`) | P1 | émote / banc | sync | loop |
| **Parler** (gestes de main en discutant) | P2 | chat de proximité actif | sync | loop |
| **Pointer** | P2 | émote | sync | one-shot |
| **Provocation / taunt** | P1 | émote combat | sync | one-shot |
| **Croiser les bras** | P2 | émote | sync | loop |

---

## Ordre d'implémentation conseillé

1. **Phase A — link observable** (en cours) : idle / marche / course / saut / chute /
   landing visibles sur tous les joueurs. Zéro packet. Gros gain immédiat, c'est « comme
   en 1.21 » pour la locomotion.
2. **Canal `reborn:anim`** (Phase B) : petit protocole client↔serveur (ShinobiCore ou
   plugin dédié) qui broadcast `{uuid, animId, action:start/stop}`. Débloque **tout** le
   reste (walk-style choisi, Naruto-run, combat, émotes, hit-reactions).
3. **Combat M1/M2 + hitstun** : dès que le canal existe, le triptyque
   `combo → hit → knockback` donne un combat qui « claque ».
4. **Idle ambiant + social** : polish continu, faible coût.
5. **Ninja avancé** (seals, substitution, mur/eau) : couche identité, après le socle.

## Keyframes : ce que je peux te livrer, honnêtement

- ✅ **Je peux hand-author** les `.json` GeckoLib (même format que `jumpanimation.json`)
  pour les anims **simples et lisibles** : idle respiration, idle combat, garde, focus
  chakra, hitstun léger, croiser les bras, pointer, bow. Ce sont des poses + interpolation
  douce sur 2–6 keyframes → propre à la main.
- ⚠️ **À faire dans Blender** (toi, avec le rig) : tout ce qui a du **poids, de la rotation
  full-body ou du contact précis** — roulade, backstep, combos de coups, projection,
  relevée, atterrissages. La main-authoring donnerait un rendu « robot ». Le pipeline
  Blender→GeckoLib est déjà validé (cf. `emote-geckolib-import` + le bend Bendable Cuboids).
- **Proposition** : je te génère un **starter pack** de 4–6 anims simples (idle, idle
  combat, garde, focus chakra, hitstun, bow) directement en `.json` prêtes à charger, tu
  juges le rendu IG, et on garde Blender pour les mouvements lourds. Dis-moi si je lance
  ce starter pack.
