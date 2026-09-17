# Spec — `StatsService` (Sprint 1 S2 · code dû le 1er oct)

> Livrable du design **Stats** du Sprint 1 S2. Applique la **voie A** tranchée le
> 2026-09-17 : le modèle du plan fait foi, `StatsService` devient la source de
> vérité des pools, `LevelTable` et `AffinityMultipliers` sont retirés.
>
> Constat de départ, chiffres à l'appui : [`AUDIT_STATS_ET_PROGRESSION.md`](./AUDIT_STATS_ET_PROGRESSION.md).
> Ancrage produit : carte Trello [`tstHtbat`](https://trello.com/c/tstHtbat).

---

## 1. Le modèle

> ⚠️ **Les cinq catégories ci-dessous sont provisoires.** Elles reprennent le plan
> d'origine ; l'étude comparative [`ETUDE_STATS_COMPARATIF.md`](./ETUDE_STATS_COMPARATIF.md)
> (2026-09-17) montre qu'elles ne parlent pas le langage du jeu — pas de stat pour
> le kenjutsu, deux stats redondantes sur l'axe ninjutsu, et Précision/Force en
> position de *dump stats*. Trois options y sont chiffrées, décision en attente.
>
> **Ce qui ne dépend pas de ce choix et reste valable tel quel** : l'API (§3), la
> persistance et la migration (§4), l'alignement des rangs (§5), la séparation
> leviers / structurel (§6) et le découpage d'implémentation (§7). Seuls les noms
> des stats et les formules dérivées (§1.2) bougeront.

Cinq stats, **entières**, allouées par le joueur. Pas de table de niveaux.

| Stat | Ce qu'elle gouverne |
|---|---|
| **Force** | dégâts taïjutsu M1, poids maximum porté (sacoche RP) |
| **Endurance** | HP max, régénération d'endurance, résistance aux debuffs |
| **Chakra** | pool de chakra max et sa régénération |
| **Contrôle** | efficacité des sorts (magnitude, moins d'échecs) |
| **Précision** | taux de critique, précision des projectiles |

### 1.1 Allocation

| | Valeur |
|---|---|
| Valeur de départ | **1** par stat (5 points « gratuits ») |
| Gain par passage de rang | **3 points** à répartir librement |
| Passages de rang | 5 (Académie→Genin→Chunin→Jonin→Sp. Jonin→ANBU/Kage) |
| Points distribuables au total | **15** |
| Plafond par stat | **10** |
| Total investi au cap | 20 points sur 50 possibles (5 stats × 10) |

Le plafond de 10 est ce qui rend les builds lisibles : un personnage au cap ne peut
maxer qu'**une seule** stat et répartir les 6 points restants. Il n'y a pas de build
qui fait tout.

> **Reset.** Un reset personnel est prévu au design de progression (retour Genin
> avec bonus de prestige). `StatsService` doit donc savoir **réallouer**, pas
> seulement incrémenter — cf. §3.

### 1.2 Formules dérivées

```
HP max         = 100 + (endurance × 10)          →  110 au départ,  200 au cap
Chakra max     = 50  + (chakra    × 15)          →   65 au départ,  200 au cap
Crit           = 5 % + (précision × 0,5 %)       →  5,5 % au départ, 10 % au cap
Dégâts d'un sort = base × (1 + contrôle × 0,02) × (1 + rangTechnique × 0,5)
Dégâts M1 taï    = baseM1 × (1 + force × 0,03)
```

`rangTechnique` : D=1, C=2, B=3, A=4, S=5.

### 1.3 Vérification de l'économie — la raison d'être de la reprise

Les 218 abilities coûtent **6 à 50 chakra** (médiane 50). Contre le nouveau pool :

| Moment | Chakra | Pool | Casts à 50 | Casts à 6 |
|---|---:|---:|---:|---:|
| Départ (Académie) | 1 | 65 | 1 | 10 |
| Milieu (chakra 5) | 5 | 125 | 2 | 20 |
| Cap (chakra 10) | 10 | 200 | **4** | 33 |

À comparer aux **2 000 casts** que permet le pool actuel au niveau 17. La ressource
redevient une contrainte de combat à tous les stades. C'est l'objectif ; si le
playtest la trouve trop rude, le levier est le coefficient `15` de la formule chakra,
pas le retour à l'ancienne échelle.

---

## 2. Ce que ça remplace

| Retiré | Remplacé par |
|---|---|
| `character/LevelTable.java` (17 niveaux, HP/chakra en dur) | formules dérivées §1.2 |
| `character/AffinityMultipliers.java` (1 axe, ×1.10→×1.40) | les 5 stats, qui couvrent les 3 axes et plus |
| `Affinity` **comme multiplicateur** | — |

**`Affinity` (`STRENGTH`/`INTELLIGENCE`/`AGILITY`) survit-il ?** Oui, mais **dégradé
en étiquette RP** : il décrit l'archétype corporel du personnage (affiché au wizard
et sur la fiche), sans effet mécanique. La raison : il est persisté sur tous les
personnages existants et lu par le créateur ; le retirer élargit la migration sans
bénéfice. Son `multiplier()` devient inutilisé et part avec `AffinityMultipliers`.

> ⚠️ Ne pas confondre avec **`ChakraAffinity`** (`KATON`/`SUITON`/`FUTON`/`DOTON`/
> `RAITON`), qui porte le triangle des natures et n'est pas concerné ici. La roadmap
> écrivait « résistances via `Affinity` » — c'est `ChakraAffinity`, corrigé.

---

## 3. API

Dans `com.reborn.shinobicore.api`, annotée `@Stable`, aux côtés de `ResourceService`
et `ProgressionLadder`.

```java
public interface StatsService {

    /** Les cinq stats. L'ordre est celui de l'affichage. */
    enum Stat { FORCE, ENDURANCE, CHAKRA, CONTROLE, PRECISION }

    int MIN = 1, MAX = 10, POINTS_PER_RANK = 3;

    /** Valeur brute allouée. Jamais hors [MIN, MAX]. */
    int get(UUID character, Stat stat);

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
    double critChance(UUID character);
    double spellDamage(UUID character, double base, int techniqueRank);
    double meleeDamage(UUID character, double base);
}
```

**Règle d'or** : aucun appelant ne réimplémente une formule du §1.2. `ShinobiCombat`,
`ShinobiAbilities` et le HUD passent tous par les méthodes dérivées. C'est ce qui
rend l'équilibrage possible sans chasser les copies éparpillées.

### 3.1 Événements

Dans `api/event`, à côté de l'existant :

- `CharacterStatsChangedEvent` — émis après `allocate` / `respec`. Porte l'ancien et
  le nouveau jeu de stats.
- Conséquence obligatoire : **recalculer les pools**. Monter Endurance augmente le
  HP max ; la vie courante est conservée en valeur absolue (on ne soigne pas
  gratuitement), le pool de chakra suit la même règle.

### 3.2 Frontières entre plugins

L'endurance de combat vit dans **`ShinobiCombat`** (`combat/StaminaManager.java`),
pas dans ShinobiCore. `StatsService` expose la **valeur maximale** et la régénération
dérivées de la stat Endurance ; `StaminaManager` reste propriétaire de la
consommation et du tick. Il doit cesser de porter son propre max.

`ResourceService` / `ResourcePool` gardent la gestion du chakra courant ; leur
**maximum** vient désormais de `StatsService.maxChakra`.

---

## 4. Persistance et migration

Les personnages sont des YAML côté serveur (`plugins/ShinobiCore/characters/`),
jamais dans le dépôt. `YamlCharacterRepository` a déjà un précédent de migration
(`backupIfLegacyLearnedShape`) — même approche.

### 4.1 Nouveau bloc

```yaml
stats:
  force: 1
  endurance: 1
  chakra: 1
  controle: 1
  precision: 1
  unspent: 0
```

### 4.2 Conversion des personnages existants

Un personnage legacy a un `level` (1-17) et une `affinity`. Conversion au premier
chargement, sans perte de progression ressentie :

1. `points = min(15, (level - 1) × 15 / 16)` arrondi — le niveau 17 donne les 15
   points, le niveau 1 en donne 0.
2. Répartition automatique guidée par l'ancienne `affinity`, pour que le personnage
   reste reconnaissable :
   - `STRENGTH` → 60 % des points en Endurance, 40 % en Force
   - `INTELLIGENCE` → 60 % en Chakra, 40 % en Contrôle
   - `AGILITY` → 60 % en Précision, 40 % en Endurance
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

- les coefficients des formules §1.2 (`10`, `15`, `0,5 %`, `0,02`, `0,03`)
- les `base` de dégâts par technique
- `POINTS_PER_RANK`

**Structurel** — change la forme du modèle, pas un réglage :

- le nombre de stats, leurs noms
- `MIN` / `MAX`
- le fait que les pools dérivent des stats

Cette séparation est la raison d'être du service : on doit pouvoir nerfer sans
toucher au code, et refuser les changements qui obligeraient à migrer.

---

## 7. Découpage de l'implémentation (S3, 25 sept → 1 oct)

| Étape | Contenu |
|---|---|
| 1 | Enum `Stat`, interface `StatsService`, `@Stable` |
| 2 | Implémentation + persistance YAML + `CharacterStatsChangedEvent` |
| 3 | Migration legacy (§4.2) avec `.bak` et test sur une copie |
| 4 | Branchement `ResourceService` (max chakra) et `StaminaManager` (max endurance) |
| 5 | Retrait de `LevelTable` et `AffinityMultipliers` ; `Affinity` dégradé en étiquette |
| 6 | Alignement de `Rank` (§5) |
| 7 | Repasse des 218 `chakra-cost` sur la nouvelle échelle (vérification) |

Les étapes 1-2 sont le chemin critique du Sprint 2 : sans elles, aucune des 29
techniques d'octobre ne peut être équilibrée.

---

## 8. Questions ouvertes

1. **Répartition libre ou guidée ?** La spec suppose une allocation libre. Une
   variante « l'arbre à 4 branches du design Progression contraint les stats
   accessibles » est possible — à trancher avec le design Progression, qui suit
   immédiatement.
2. **Que fait la Force sur le poids porté ?** La sacoche RP a déjà un système de
   poids. Formule à définir avec le chantier Inventaire.
3. **Les dégâts M1 taïjutsu** utilisent `baseM1`, qui n'existe pas encore comme
   valeur nommée dans `ShinobiCombat`. À extraire lors de l'étape 4.
