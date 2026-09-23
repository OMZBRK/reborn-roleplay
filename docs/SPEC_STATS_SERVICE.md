# Spec — `StatsService` (Sprint 1 S2 · code dû le 1er oct)

> Livrable du design **Stats** du Sprint 1 S2. Applique la **voie A** tranchée le
> 2026-09-17 : le modèle du plan fait foi, `StatsService` devient la source de
> vérité des pools, `LevelTable` et `AffinityMultipliers` sont retirés.
>
> **Catégories tranchées le 2026-09-23 : option B**, six stats alignées sur les
> quatre branches de l'arbre et les deux ressources. Le §1 est réécrit en
> conséquence ; les valeurs numériques y sont des **valeurs de départ pour le
> prototype et le playtest**, pas des décisions — voir §1.5 pour ce qui reste à
> discuter avec le staff.
>
> Constat de départ, chiffres à l'appui : [`AUDIT_STATS_ET_PROGRESSION.md`](./AUDIT_STATS_ET_PROGRESSION.md).
> Pourquoi ces catégories : [`ETUDE_STATS_COMPARATIF.md`](./ETUDE_STATS_COMPARATIF.md) §5 et §7.
> Ancrage produit : carte Trello [`tstHtbat`](https://trello.com/c/tstHtbat).
> Prototype interactif (hors dépôt) : <https://claude.ai/artifact/PL9B5T2UtbUwVKoqG7fSrU>.

---

## 1. Le modèle — ✅ option B, tranchée le 2026-09-23

Six stats, **entières**, allouées par le joueur. Pas de table de niveaux.

| Stat | Ce qu'elle gouverne | Branche / ressource |
|---|---|---|
| **Taïjutsu** | dégâts des techniques Tai et du M1 à mains nues, part de l'endurance | branche Tai |
| **Kenjutsu** | dégâts des techniques Ken et du M1 armé, parade, combos | branche Ken |
| **Ninjutsu** | dégâts des techniques de chakra, toutes natures | branche Nin |
| **Contrôle** | coût des techniques, magnitude des soins, échecs, vitesse des sceaux | branche Médical (et support) |
| **Vigueur** | PV max, endurance max, résistance aux debuffs | ressource vie |
| **Chakra** | réserve de chakra et sa régénération | ressource chakra |

Ce qui a disparu par rapport au plan, et pourquoi (détail dans l'étude §7) :

- **Force** — ne servait qu'au M1 : *dump stat* pour tout ninjutsuka. Le M1 dérive
  désormais du Taïjutsu (ou du Kenjutsu si armé). Le poids porté par la sacoche RP
  se règle avec le chantier Inventaire, sans stat dédiée (§8.2).
- **Précision** — cinq points de critique au cap, le pire investissement du lot.
  « Mes techniques font mieux leur travail », c'est déjà Contrôle. Le critique
  devient un dérivé du Contrôle, ou disparaît au playtest (§1.5).
- **Endurance** — renommée **Vigueur** pour ne pas confondre la stat avec la
  ressource d'endurance de combat qu'elle alimente (`StaminaManager`).

### 1.0 Trois principes qui cadrent tout le reste

Ils viennent de la décision du 2026-09-23 et priment sur n'importe quel chiffre
de cette spec.

1. **Les stats ne ferment aucune porte RP.** L'examen Chunin, l'entrée dans une
   section, l'accès à une activité ou à une scène ne dépendent **jamais** d'un seuil
   de stat. Ce qui gate le RP, c'est le rang shinobi, le RP lui-même et le staff.
   La proposition « seuils d'accès » de l'atlas (H) est **retirée** pour tout ce qui
   est RP.
2. **L'apprentissage des techniques est libre.** Aucune technique n'exige une
   valeur de stat. Le rang d'une technique (D → S) fixe trois choses : sa
   **difficulté de maîtrise** (le jet d'apprentissage des parchemins,
   `ShinobiLearning`), son **coût** en chakra ou en endurance, et ses **dégâts de
   base**. Les stats agissent **indirectement** : un Genin peut apprendre un rang A,
   mais avec une petite réserve il ne le lancera qu'une fois, et avec un Ninjutsu
   bas il fera peu de dégâts. Le gate `requires:` du Technique Creator reste
   disponible pour le clan, la nature ou le rang shinobi si le design Progression le
   demande — **jamais pour une stat**.
3. **La réserve de chakra est grande.** Pas de plafond à 200 : l'échelle monte à
   l'ordre de **100 000 au cap**, et l'équilibrage se fait par les coûts. Un rang S
   doit être lançable **deux ou trois fois** par un personnage au cap, pas plus.
   C'est un réglage de coûts et de dégâts, à faire au playtest ; la spec fixe
   seulement la forme des formules.

### 1.1 Allocation

| | Valeur de départ | Nature |
|---|---|---|
| Valeur minimale | **1** par stat (6 points « gratuits ») | structurel |
| Gain par passage de rang | **3 points** à répartir librement | **levier — à discuter avec le staff (§1.5)** |
| Passages de rang | 5 (Académie → Genin → Chunin → Jonin → Sp. Jonin → ANBU/Kage) | design Progression |
| Points distribuables au total | **15** | dérivé |
| Plafond par stat | **10** | structurel — mais peut **monter** sans migration (§6) |
| Total investi au cap | 21 points sur 60 possibles | dérivé |

La répartition est **libre** : l'arbre à quatre branches du design Progression
gate les techniques apprises, pas les stats (réponse à l'ancienne question §8.1).

> **Sur le nombre de points.** Trois par rang est le chiffre du plan ; il est jugé
> limitant, et c'est une question de ressenti à trancher avec le staff. Deux
> réglages sont à mettre sur la table : **5 points par rang, plafond 15** (un
> personnage au cap maxe une stat et en monte deux autres) ou **6 par rang,
> plafond 20** (fiche plus « RPG », builds hybrides possibles). `POINTS_PER_RANK`
> est un levier ; `MAX` peut être augmenté après coup sans migration (jamais
> réduit). On peut donc coder avec 3 / 10 et changer au playtest.

> **Reset.** Un reset personnel est prévu au design de progression (retour Genin
> avec bonus de prestige). `StatsService` doit donc savoir **réallouer**, pas
> seulement incrémenter — cf. §3.

### 1.2 La courbe : soft caps

Chaque stat passe par une fonction d'efficacité avant d'entrer dans une formule :

```
eff(s) = s                       si s ≤ CAP
eff(s) = CAP + (s − CAP) × 0,5   sinon            CAP = 6 (levier)
```

Plein rendement jusqu'au seuil, rendement réduit au-delà. Monter une stat à 10
n'est pas interdit, c'est un investissement moins rentable — et le joueur le sent
sans lire un wiki. Le HUD marque le seuil sur chaque barre de stat. Avec CAP = 6,
`eff(10) = 8`.

Coupe de secours si le playtest trouve la courbe illisible : un palier tous les
deux points (façon D&D), même code, autre fonction.

### 1.3 Formules dérivées — valeurs de départ

```
PV max          = 200 + eff(Vigueur) × 100                        →   300 au départ · 1 000 au cap
Chakra max      = 4 000 + eff(Chakra) × 12 000                    → 16 000 au départ · 100 000 au cap
Endurance max   = 100 + eff(Vigueur) × 8 + eff(Taïjutsu) × 4      →   112 au départ · ~196 au cap
Régén chakra    = Chakra max × (1 % + 0,15 % × eff(Contrôle))  par 10 s au repos

Coût technique  = base(rang) × (1 − 3 % × eff(Contrôle))
                  base : D 800 · C 2 000 · B 5 000 · A 12 000 · S 35 000   (chakra)
                  base : D 18 · C 30 · B 45 · A 60                          (endurance, Tai/Ken)

Dégâts / soin   = base(rang) × (1 + Σ poids(lettre) × eff(stat)) × nature
                  poids : S 0,12 · A 0,10 · B 0,07 · C 0,04 · D 0,02

Dégâts M1       = baseM1 × (1 + 0,08 × eff(Taïjutsu))         (mains nues)
                = baseM1ken × (1 + 0,08 × eff(Kenjutsu))      (armé)

Critique        = 5 % + 0,5 % × eff(Contrôle)                 (à confirmer ou retirer au playtest)
```

**Grades de scaling par technique (E).** Chaque technique porte, dans le Technique
Creator, un champ `scaling:` qui note chaque stat d'une lettre S/A/B/C/D ou rien.
Le Gōkakyū scale S en Ninjutsu et C en Contrôle ; le Shōsen S en Contrôle et C en
Ninjutsu ; l'Omote Renge S en Taïjutsu et B en Vigueur. C'est ce champ qui rend le
lien stat → technique visible au moment du choix, et c'est le levier d'équilibrage
par technique.

**Roue des natures (M).** Le cycle canon est fermé : Katon bat Fūton, Fūton bat
Raiton, Raiton bat Doton, Doton bat Suiton, Suiton bat Katon. Multiplicateur de
dégâts **×1,25 dans le sens, ×0,8 contre**, annulation à nature et rang égaux, et
un écart de deux rangs efface le malus. Hors stats : la nature vient du Test de la
Feuille et vit dans `ChakraAffinity`, qui existe déjà. Il manque le multiplicateur
dans la formule de dégâts, rien d'autre.

### 1.4 Vérification de l'économie — ce que les coûts font vraiment

Le levier est le coût, pas la réserve. Lancers possibles avec la réserve pleine,
selon le Chakra et le Contrôle du personnage :

| Personnage | Chakra max | Rang D (800) | Rang B (5 000) | Rang S (35 000) |
|---|---:|---:|---:|---:|
| Départ (Chakra 1, Contrôle 1) | 16 000 | 20 | 3 | 0 |
| Ninjutsuka moyen (Chakra 5, Contrôle 4) | 64 000 | 90 | 14 | **2** |
| Médic (Chakra 4, Contrôle 7) | 52 000 | 80 | 12 | **1** |
| Cap (Chakra 10, Contrôle 10) | 100 000 | 164 | 26 | **3** |

À comparer aux **2 000 casts** que permet le pool actuel au niveau 17, pour un
coût médian de 50. La ressource redevient une contrainte de combat pour les rangs
hauts à tous les stades ; les rangs bas restent spammables, ce qui est voulu (un
Genin doit pouvoir lancer son Hōsenka). Si le playtest trouve l'échelle trop rude
ou trop lâche, on bouge `base(rang)` ou le coefficient `12 000`, jamais la forme.

Les 218 `chakra-cost` actuels (6 à 50) sont sur l'ancienne échelle : ils se
convertissent par rang, pas un par un (étape 7 du §7).

### 1.5 À discuter avec le staff avant de figer

Ce paragraphe liste ce que la décision du 2026-09-23 laisse explicitement ouvert.
Rien ici ne bloque l'écriture du code : tout est un levier ou un `MAX` qu'on peut
augmenter.

| Sujet | Valeur de départ | Alternatives sur la table |
|---|---|---|
| Points par rang / plafond | 3 / 10 | 5 / 15 · 6 / 20 |
| Seuil du soft cap | 6 | 5 · 7, ou palier tous les deux points |
| Échelle de chakra au cap | 100 000 | 50 000 · 200 000 — la forme ne change pas |
| Coût d'un rang S | 35 000 | tout ce qui donne 2 à 3 lancers au cap |
| Critique | dérivé du Contrôle | retiré de la beta |
| Poids des lettres S → D | 0,12 → 0,02 | à resserrer si les rangs bas dominent |
| Bonus clanique | bonus de départ sur la stat du clan | multiplicateur permanent (v1.1) |

Ce qui **n'est pas** à rediscuter, parce que structurel ou déjà tranché : les six
stats et leurs noms, le minimum à 1, l'allocation libre, l'apprentissage libre des
techniques, l'absence de seuil de stat sur le RP, la grande échelle de chakra.

---

## 2. Ce que ça remplace

| Retiré | Remplacé par |
|---|---|
| `character/LevelTable.java` (17 niveaux, HP/chakra en dur) | formules dérivées §1.3 |
| `character/AffinityMultipliers.java` (1 axe, ×1.10→×1.40) | les six stats et les grades de scaling |
| `Affinity` **comme multiplicateur** | — |

**`Affinity` (`STRENGTH`/`INTELLIGENCE`/`AGILITY`) survit-il ?** Oui, mais **dégradé
en étiquette RP** : il décrit l'archétype corporel du personnage (affiché au wizard
et sur la fiche), sans effet mécanique. La raison : il est persisté sur tous les
personnages existants et lu par le créateur ; le retirer élargit la migration sans
bénéfice. Son `multiplier()` devient inutilisé et part avec `AffinityMultipliers`.

> ⚠️ Ne pas confondre avec **`ChakraAffinity`** (`KATON`/`SUITON`/`FUTON`/`DOTON`/
> `RAITON`), qui porte le triangle des natures et **entre dans la formule de
> dégâts** (§1.3). La roadmap Sprint 2 S3 écrivait « résistances via `Affinity` » —
> c'est `ChakraAffinity`, corrigé.

---

## 3. API

Dans `com.reborn.shinobicore.api`, annotée `@Stable`, aux côtés de `ResourceService`
et `ProgressionLadder`.

```java
public interface StatsService {

    /** Les six stats. L'ordre est celui de l'affichage. */
    enum Stat { TAIJUTSU, KENJUTSU, NINJUTSU, CONTROLE, VIGUEUR, CHAKRA }

    /** Lettre de scaling d'une technique sur une stat. */
    enum Grade { S, A, B, C, D }

    int MIN = 1;
    /** Peut être augmenté sans migration, jamais réduit. */
    int MAX = 10;
    /** Levier. */
    int POINTS_PER_RANK = 3;

    /** Valeur brute allouée. Jamais hors [MIN, MAX]. */
    int get(UUID character, Stat stat);

    /** Valeur après soft cap — celle qui entre dans les formules. */
    double effective(UUID character, Stat stat);

    /** Vue complète — une seule lecture pour le HUD et la fiche. */
    Map<Stat, Integer> all(UUID character);

    /** Points gagnés et non encore dépensés. */
    int unspentPoints(UUID character);

    /**
     * Dépense des points. Atomique : soit tout passe, soit rien.
     * Échoue si le total dépasse les points disponibles ou si une stat
     * franchit MAX.
     */
    AllocationResult allocate(UUID character, Map<Stat, Integer> deltas);

    /** Remet toutes les stats à MIN et rend les points. Pour le reset de prestige. */
    void respec(UUID character);

    // --- Valeurs dérivées : personne ne recalcule les formules dans son coin ---
    double maxHp(UUID character);
    double maxChakra(UUID character);
    double maxStamina(UUID character);
    double chakraRegenPer10s(UUID character);
    double critChance(UUID character);

    /** Coût d'une technique pour ce personnage, après réduction par le Contrôle. */
    double techniqueCost(UUID character, int techniqueRank, CostKind kind);

    /** Dégâts ou soin d'une technique : base × (1 + Σ poids × eff) — sans la nature. */
    double techniquePower(UUID character, double base, Map<Stat, Grade> scaling);

    /** Multiplicateur de nature (roue) entre deux techniques. Vaut 0 en cas d'annulation. */
    double natureMultiplier(ChakraAffinity attacker, int attackerRank,
                            ChakraAffinity defender, int defenderRank);

    double meleeDamage(UUID character, double base, boolean armed);

    enum CostKind { CHAKRA, STAMINA }
}
```

`techniqueRank` : D=1, C=2, B=3, A=4, S=5.

**Règle d'or** : aucun appelant ne réimplémente une formule du §1.3. `ShinobiCombat`,
`ShinobiAbilities` et le HUD passent tous par les méthodes dérivées. C'est ce qui
rend l'équilibrage possible sans chasser les copies éparpillées.

### 3.1 Événements

Dans `api/event`, à côté de l'existant :

- `CharacterStatsChangedEvent` — émis après `allocate` / `respec`. Porte l'ancien et
  le nouveau jeu de stats.
- Conséquence obligatoire : **recalculer les pools**. Monter Vigueur augmente le
  PV max ; la vie courante est conservée en valeur absolue (on ne soigne pas
  gratuitement), le pool de chakra suit la même règle.

### 3.2 Frontières entre plugins

L'endurance de combat vit dans **`ShinobiCombat`** (`combat/StaminaManager.java`),
pas dans ShinobiCore. `StatsService` expose la **valeur maximale** et la régénération
dérivées de Vigueur et Taïjutsu ; `StaminaManager` reste propriétaire de la
consommation et du tick. Il doit cesser de porter son propre max.

`ResourceService` / `ResourcePool` gardent la gestion du chakra courant ; leur
**maximum** vient désormais de `StatsService.maxChakra`.

Le champ `scaling:` d'une technique est porté par le Technique Creator et lu par
`ShinobiAbilities`, qui appelle `techniquePower` puis `natureMultiplier`. Aucun
seuil de stat n'est lu à l'apprentissage ni au lancer (§1.0).

---

## 4. Persistance et migration

Les personnages sont des YAML côté serveur (`plugins/ShinobiCore/characters/`),
jamais dans le dépôt. `YamlCharacterRepository` a déjà un précédent de migration
(`backupIfLegacyLearnedShape`) — même approche.

### 4.1 Nouveau bloc

```yaml
stats:
  taijutsu: 1
  kenjutsu: 1
  ninjutsu: 1
  controle: 1
  vigueur: 1
  chakra: 1
  unspent: 0
```

### 4.2 Conversion des personnages existants

Un personnage legacy a un `level` (1-17) et une `affinity`. Conversion au premier
chargement, sans perte de progression ressentie :

1. `points = min(15, (level - 1) × 15 / 16)` arrondi — le niveau 17 donne les 15
   points, le niveau 1 en donne 0.
2. Répartition automatique guidée par l'ancienne `affinity`, pour que le personnage
   reste reconnaissable :
   - `STRENGTH` → 60 % des points en Taïjutsu, 40 % en Vigueur
   - `INTELLIGENCE` → 60 % en Ninjutsu, 40 % en Contrôle
   - `AGILITY` → 60 % en Kenjutsu, 40 % en Vigueur
3. Plafonner chaque stat à `MAX`, reverser le surplus dans `unspent`.
4. Écrire `.bak` **avant** réécriture, comme `backupIfLegacyLearnedShape`.
5. Retirer `level` du YAML une fois converti.

> Le joueur peut de toute façon tout réallouer via `respec`. La conversion vise à ne
> pas le planter à zéro, pas à reproduire exactement son ancien build.

**Périmètre** : la beta est fermée (`LAUNCHER_BETA_GATE=HELPER`), la population est
le staff. C'est ce qui rend l'opération peu coûteuse aujourd'hui — et ce qui la
rendrait coûteuse après l'ouverture de décembre.

---

## 5. Alignement des rangs — à traiter dans la foulée

`character/Rank.java` diverge du plan sur deux points :

| | Code actuel | Plan |
|---|---|---|
| Entrée | pas d'`ACADEMIE` | Académie est le rang de départ |
| Ordre | `CHUNIN` → `SPECIAL_JONIN` → `JONIN` | Chunin → Jonin → Special Jonin |

Les deux se corrigent ensemble :

- Ajouter `ACADEMIE` en première position.
- Remettre `SPECIAL_JONIN` **après** `JONIN`.
- `Rank.from()` est déjà tolérant (repli sur `GENIN`) : aucun YAML ne casse, mais un
  personnage `SPECIAL_JONIN` legacy change de position relative. Sur une population
  staff, c'est acceptable ; le signaler dans les notes de déploiement.

C'est un prérequis du peuplement de `ProgressionLadder` (Sprint 1 S3) : les seuils
XP du plan (5 000 / 20 000 / 60 000 / 150 000) supposent l'ordre du plan.

---

## 6. Ce qui est un levier d'équilibrage, et ce qui ne l'est pas

**Leviers** — modifiables à chaud, sans redéploiement, via le dashboard staff prévu
au Sprint 4 S3 :

- les coefficients des formules §1.3 (`100`, `12 000`, `8`, `4`, `3 %`, `0,08`, `0,5 %`)
- `CAP` du soft cap et le facteur post-seuil (`0,5`)
- les `base` de coût et de dégâts par rang, et le `scaling:` de chaque technique
- les multiplicateurs de nature (`1,25` / `0,8`)
- `POINTS_PER_RANK`
- `MAX`, **à la hausse seulement**

**Structurel** — change la forme du modèle, pas un réglage :

- le nombre de stats, leurs noms
- `MIN`, et toute **baisse** de `MAX`
- le fait que les pools dérivent des stats
- l'absence de seuil de stat à l'apprentissage et au lancer d'une technique (§1.0)

Cette séparation est la raison d'être du service : on doit pouvoir nerfer sans
toucher au code, et refuser les changements qui obligeraient à migrer.

---

## 7. Découpage de l'implémentation (S3, 25 sept → 1 oct)

| Étape | Contenu |
|---|---|
| 1 | Enums `Stat` et `Grade`, interface `StatsService`, `@Stable` |
| 2 | Implémentation + soft cap + persistance YAML + `CharacterStatsChangedEvent` |
| 3 | Migration legacy (§4.2) avec `.bak` et test sur une copie |
| 4 | Branchement `ResourceService` (max chakra) et `StaminaManager` (max endurance) ; extraction de `baseM1` |
| 5 | Retrait de `LevelTable` et `AffinityMultipliers` ; `Affinity` dégradé en étiquette |
| 6 | Alignement de `Rank` (§5) |
| 7 | Conversion des 218 `chakra-cost` sur la nouvelle échelle, **par rang** (D 800 … S 35 000), et champ `scaling:` dans le Technique Creator |

Les étapes 1-2 sont le chemin critique du Sprint 2 : sans elles, aucune des 29
techniques d'octobre ne peut être équilibrée. La roue des natures (`natureMultiplier`)
se branche au Sprint 2 S3, semaine où la roadmap la prévoit déjà.

---

## 8. Questions ouvertes

1. ~~**Répartition libre ou guidée ?**~~ **Tranché le 2026-09-23 : libre.** L'arbre
   à quatre branches gate ce qu'on apprend, pas où vont les points.
2. **Que devient le poids porté ?** La sacoche RP a déjà un système de poids ; le
   plan le faisait dépendre de Force, qui n'existe plus. Formule à définir avec le
   chantier Inventaire — Vigueur est le candidat naturel, ou un plafond fixe par rang.
3. **Les dégâts M1** utilisent `baseM1` (mains nues) et `baseM1ken` (armé), qui
   n'existent pas encore comme valeurs nommées dans `ShinobiCombat`. À extraire lors
   de l'étape 4.
4. **Le critique** : dérivé du Contrôle, ou retiré de la beta ? Se tranche au
   playtest, pas avant.
5. **Points par rang et plafond** : voir §1.5, à discuter avec le staff. Le code
   part sur 3 / 10 et n'a rien à changer si la réponse est 5 / 15.
