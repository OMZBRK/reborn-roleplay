# Roadmap — Beta Fin 2026

> **Ce document décrit le FUTUR engagé.** Il est le miroir texte du Trello
> `📅 Plan de vol Beta 2026` + `🎯 Beta Fin 2026` et des frames Miro
> *Roadmap*, *Sprint Timeline*, *Matrice Combat*, *Progression Timeline*.
>
> Les trois supports doivent dire **la même chose**. En cas de divergence, ce
> fichier fait foi et les boards sont corrigés — pas l'inverse.
>
> Le présent est dans [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md).
> Établi le **2026-09-08**.

---

## 1. Objectif

Livrer un **gameplay loop complet et jouable** :

> créer un perso → entrer dans Konoha → combattre → apprendre des techniques →
> mourir / renaître → jouer RP

Cohérent bout-en-bout. **Pas parfait, jouable.**

**Deadline : 31 décembre 2026.** 17 semaines depuis le 4 septembre.

**Chemin critique** :
`Stats → Progression → Combat → Bibliothèque de parchemins → KO/Médical → Faction → Build → Playtest → Ship`

---

## 2. Les 4 sprints

Une semaine = du jeudi au mercredi, alignée sur le rythme des weekly meetings.

### Sprint 1 — Septembre · *Foundation & Design* (4 sept → 8 oct)

Verrouiller le design des systèmes non encore designés, poser les fondations code,
démarrer la production d'assets et le macro-terraforming.

| Semaine | Livrable |
|---|---|
| **S0 · 4-10 sept** | Consolidation : merge des branches en retard, remise à plat docs/Trello/Miro, décisions ouvertes tranchées (monnaies, comptage des techniques) |
| **S1 · 11-17 sept** | **Design Stats** : 5 stats (Force, Endurance, Chakra, Contrôle, Précision) + formules dérivées (HP, chakra, crit) + résistances par nature. **Design Progression** : table XP par rang + XP par activité + arbre 4 branches. **Design Parchemins** : item Nexo, jet caché %, loot tables, UI bibliothèque |
| **S2 · 18-24 sept** | **Design Faction Konoha** : permissions Kage/sections, trésor commun, whitelist géo, examens Chunin. **Design KO / mort RP** : flow ATA, règles de mort définitive validée staff, écran KO |
| **S3 · 25 sept - 1 oct** | **Code** : `StatsService` dans ShinobiCore (persistance + hooks event) ; peupler `ProgressionLadder` (6 rangs + seuils XP + events) ; 8 icônes de cooldown (dash, dodge, jump, block, katon-D, suiton-D, doton-D, fûton-D) |
| **S4 · 2-8 oct** | **Assets wave 1** : 3 clans starter complets (Uchiha, Hyuga, Neutre — tenue + cheveux + iris). **Build** : macro-terraforming du Pays du Feu (WorldMachine → WorldPainter) |

**DoD** — tous les designs verrouillés, foundation code prête, macro-terrain généré,
3 clans starter jouables au wizard.
**Dépendances** — aucune. **Débloque** — Sprint 2.

---

### Sprint 2 — Octobre · *Combat & centre de Konoha* (9 oct → 5 nov)

| Semaine | Livrable |
|---|---|
| **S1 · 9-15 oct** | **12 sorts Taïjutsu** (Basique ×3, Inter ×3, Expert ×3, Maître ×3) via `TechniqueRegistry` + anim + VFX + coût d'endurance. **Parade raffinée** : fenêtre 200 ms, counter stagger 1 s, coût 30 % endurance, 500 ms de vulnérabilité si raté |
| **S2 · 16-22 oct** | **12 sorts Kenjutsu** (même structure, arme katana). **Combo** : M1 → sort dans une fenêtre de 500 ms → +20 % dégâts + effet spécial (guard-break Ken / chakra-drain Nin / stun Tai). **Lock-on** façon Elden Ring : 20 blocs, strafe latéral, switch à la molette |
| **S3 · 23-29 oct** | **5 sorts Ninjutsu rang D** (1 par nature). **Résistances** via `Affinity` (triangle actif : +30 % / -30 %). **Déplacement** : 3 styles de Naruto run (Vif / Shadowstep / Suzaku) via `MobilityService` |
| **S4 · 30 oct - 5 nov** | **Konoha centre** : tour de l'Hokage, académie, marché central, auberges, portes ANBU. **Quartier Uchiha** (bundle : dojo, temple, éventail rouge) + ses 2 sorts claniques |

**DoD** — combat complet jouable, **29 techniques** disponibles (12 Tai + 12 Ken +
5 Nin D), Konoha centre visitable, bundle Uchiha live.
**Dépendances** — Sprint 1 (`StatsService`, `TechniqueRegistry` étendu, macro-terrain).

---

### Sprint 3 — Novembre · *Systèmes RP & Faction* (6 nov → 3 déc)

| Semaine | Livrable |
|---|---|
| **S1 · 6-12 nov** | **Bibliothèque de parchemins** : item Nexo custom, drop tables (mob/zone/event/boss), jet % d'apprentissage, UI bibliothèque perso, set/consomme. **Quartier Hyuga** + Byakugan (vision 360°) + Jyuken (fermeture des points de chakra) + ses 2 sorts claniques |
| **S2 · 13-19 nov** | **KO / mort RP** : `KoService` étendu (ATA, revive, porter un allié inconscient, validation staff pour la mort définitive). **Médical basique** : bandages (+20 HP, cast 3 s), chakra médical Iryo (+100 HP, cast 5 s), antidotes, interaction avec le KO |
| **S3 · 20-26 nov** | **Faction Konoha** : Kage (staff-managed) + 4 sections (ANBU, Corps médical, Académie, Patrouille) + trésor commun + whitelist géo. **Communication RP** : volière (courrier asynchrone) + annonce publique de village. **Nin rangs C + B** : 10 techniques (5 par rang, 1 par nature) |
| **S4 · 27 nov - 3 déc** | **14 sorts claniques restants** (7 clans × 2 — cf. §3.2). **Assets wave 2** : 5 tenues, 10 coupes de cheveux, 5 iris (dojutsu Sharingan / Byakugan / Rinnegan) |

**DoD** — un Genin peut apprendre une technique, combattre, mourir, être soigné,
s'organiser en section et communiquer en asynchrone.
**Dépendances** — Sprint 2 (combat), Sprint 1 (stats pour l'équilibrage).

---

### Sprint 4 — Décembre · *Build & SHIP* 🚀 (4 déc → 31 déc)

| Semaine | Livrable |
|---|---|
| **S1 · 4-10 déc** | **Domaines claniques restants** (Nara, Yamanaka, Akimichi, Aburame, Inuzuka, Senju) — ouverture RP progressive, le staff débloque les bundles au fil des semaines |
| **S2 · 11-17 déc** | **Capitale du Pays du Feu** (résidence du Daimyo). **Temple de l'Ardeur**. **3 villages secondaires**. **2 grottes/donjons** (loot de parchemins rares). **HUD icônes complètes** : 20 icônes de sorts (5 natures × 4 rangs) + status (buff / debuff / silence) |
| **S3 · 18-24 déc** | **Playtest fermé staff 48 h** (5-10 staff, tous rangs). Dashboard d'équilibrage live. **Rush fix bugs P0** (5 jours dédiés). Polish anims P0 : idle combat, landing après chute, roulade d'esquive |
| **S4 · 25-31 déc** | **SHIP** : annonce Discord (+ trailer optionnel), ouverture des inscriptions whitelist beta (form + DM Discord automatisé), release du manifest final + launcher stable signé, monitoring post-launch (2 jours intensifs + astreinte) |

**DoD** — beta jouable end-to-end, whitelist ouverte, communication faite, monitoring
actif, kick-off v1.1 démarré.

---

## 3. Chiffres consolidés — la référence unique

Ces chiffres remplacent tous ceux qui traînaient sur le Trello et le Miro.

### 3.1 Techniques de combat

| | Beta (déc. 2026) | Cible complète (post-beta) |
|---|---|---|
| Taïjutsu | 12 (4 rangs × 3) | 12 |
| Kenjutsu | 12 (4 rangs × 3) | 12 |
| Ninjutsu | **15** (rangs D, C, B — 5 natures × 3 rangs) | 25 (+ rangs A et S) |
| **Total** | **39** | **49** |

> La **matrice Miro** décrit la cible **49**. Le **périmètre beta est 39** : les rangs
> A et S sont explicitement dans la *cut list*. L'ancienne mention « cap à ~35 » du
> Master Plan est remplacée par ce chiffre exact.

### 3.2 Sorts claniques — ⚠️ décision à valider

11 entrées de clan existent au wizard, mais toutes n'ont pas de sorts signature :

| Clan | 2 sorts signature ? | Livré en |
|---|---|---|
| Uchiha | ✅ | Sprint 2 S4 |
| Hyuga | ✅ | Sprint 3 S1 |
| Senju, Nara, Yamanaka, Akimichi, Aburame, Inuzuka, Rock | ✅ (7 clans) | Sprint 3 S4 |
| **Neutre** | ❌ — polyvalent, *pas de bonus clanique* par définition | — |
| **Autre** | ❌ — personnalisable par le staff au cas par cas | — |

**⇒ 9 clans × 2 = 18 sorts claniques**, dont **14 restants** au Sprint 3 S4.

*Chiffres antérieurs, désormais caducs* : « 2 × 11 = 22 » (Miro) et
« 20 restants, 10 clans × 2 » (Trello). Les deux comptaient Neutre et/ou Autre.
**À confirmer** : si tu veux donner 2 sorts à « Autre » (kit générique staff), on
repasse à 20 — dis-le et les boards sont réalignés.

### 3.3 Rangs de progression

Académie → Genin (5 000 XP) → Chunin (20 000) → Jonin (60 000) → Special Jonin
(150 000) → **ANBU** (recruté par le Kage) **ou Kage** (staff-managed).

Sources d'XP : combat (10-100/action), RP actif (30/10 min avec un autre joueur),
quêtes staff (D 200 → S 15 000), entraînement au dojo (20/10 min, cap quotidien),
mentorat (50 % de l'XP de l'élève, Jonin+).

Arbre : 4 branches (Tai / Ken / Nin / Médical). Genin ouvre 1 branche, Chunin 2,
Jonin 3, Special Jonin maîtrise 1 branche. 1 reset personnel possible (retour Genin
avec bonus de prestige).

---

## 4. Les 16 chantiers

Chaque chantier a une carte Trello dans `🎯 Beta Fin 2026`. Le **sprint** remplace
l'ancienne indication « T3/T4 2026 », qui était contradictoire avec le plan de vol.

| Axe | Chantier | Prio | Sprint |
|---|---|---|---|
| 🎨 Visuel | HUD & icônes (spells, actions, status) | P0 | 1 → 4 |
| 🎨 Visuel | Assets character design (tenues, cheveux, yeux, corps) | P0 | 1 (wave 1) · 3 (wave 2) |
| 🎨 Visuel | Modélisation 3D (VFX, spells, entités) | P1 | 2 → 4 |
| 🎨 Visuel | Animations combat & mouvement | P0 | 2 → 4 |
| ⚔️ Combat | Système de combat complet (Tai / Ken / Nin / Parry) | P0 | 2 → 3 |
| ⚔️ Combat | Spécificités par clan | P0 | 2 → 3 |
| ⚔️ Combat | Système de déplacement | P1 | 2 |
| ⚔️ Combat | Statistiques joueur & équilibrage | P0 | 1 |
| 📖 RP | Système de progression | P0 | 1 (design) · 2-3 (impl) |
| 📖 RP | Bibliothèque de parchemins & apprentissage | P0 | 3 |
| 📖 RP | Système KO / mort RP | P0 | 1 (design) · 3 (impl) |
| 📖 RP | Système de faction | P0 | 1 (design) · 3 (impl) |
| 📖 RP | Système médical | P1 | 3 |
| 📖 RP | Communication RP (volière, annonce) | P1 | 3 |
| 📖 RP | Système de méditation | **P2 — cut list** | post-beta |
| 🏗️ Monde | Build (Pays du Feu, Konoha, bundles) | P0 | 1 → 4 |

---

## 5. Cut list — explicitement reporté après la beta

- Modèles 3D avancés : Susanoo, Kurama, Gamabunta
- Ninjutsu rangs **A** et **S** (10 techniques)
- Radio moderne (volière + annonce suffisent pour la beta)
- Système de méditation
- Médical avancé (Iryo master, résurrection)
- Autres villages : Suna, Kiri, Kumo, Iwa
- Animations P1/P2 : finishers, backstep, strafe lock-on
- Achievements + leaderboards
- Guerre inter-villages
- Sorts signature pour « Neutre » et « Autre »

**Post-beta = v1.1**, aligné sur `PLAN_CONCEPTION_LAUNCHER.md` §11 (module social,
Steam, boutique RBCoins/Stripe, Twitch, lore interactif, profil joueur, achievements).
Ces items vivent dans la liste Trello `👑 Post-beta (v1.0.5 → v1.3)`.

---

## 6. Risques

| # | Risque | Mitigation |
|---|---|---|
| 1 | **Charge assets art** — la plus lourde du projet | 3 clans starter complets, 8 autres au minimum viable. Outils Blockbench maison (compositeur + hand-paint) pour réduire le temps manuel |
| 2 | **39 techniques reste ambitieux** | Ordre de cut défini d'avance : Nin B → Nin C → sorts claniques des clans les moins joués |
| 3 | **Konoha trop grande pour être finie** | Ouverture RP progressive par bundles ; un quartier non fini reste fermé, il ne bloque pas le ship |
| 4 | **Le playtest révèle des bugs bloquants** | 2 semaines dédiées (S3 + marge S4). En dernier recours : couper Nin C/B ou reporter les alentours |
| 5 | 🔴 **Dette Git** — la prod n'est pas sur `main` | À résorber **avant le Sprint 1 S1** (cf `AUDIT_COHERENCE.md` §3 et §11) |

---

## 7. Ancrages techniques

Chaque chantier se branche sur une API existante de **ShinobiCore**
(`com.reborn.shinobicore.api.*`) — cf. le frame Miro *ShinobiCore — API existante*.

| Chantier | Ancrage |
|---|---|
| Progression | `ProgressionLadder` — peupler rangs, seuils XP, events |
| Combat | `TechniqueRegistry` + `SkillService` + `Affinity` |
| Parchemins | **nouveau service** au-dessus de `SkillService` + `ItemGiveService` |
| KO / mort RP | étendre `KoService` (ATA + validation staff) |
| Déplacement | étendre `MobilityService` (3 presets de Naruto run) |
| HUD | `HudService` pousse au client ; le rendu est dans `mod-hud` |
| Médical | **nouveau `MedicalService`** (`CharacterDamageEvent` + `ResourceDepletedEvent`) |
| Faction | **nouveau `FactionService`** (permissions, trésor, whitelist géo) |
| Stats | **nouveau `StatsService`** (persistance + hooks event) |

Annotations : `@Stable` = point d'extension sûr, `@Internal` = ne pas utiliser hors
de ShinobiCore.
