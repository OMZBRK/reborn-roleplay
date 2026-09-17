# Audit stats & progression — entrée du design Sprint 1 S2

> Mesuré sur le code de `main` le **2026-09-17**, avant d'écrire la spec `StatsService`
> (Sprint 1 S2, échéance code au 1er oct).
>
> Motif : le modèle de stats décrit par la carte Trello
> [`tstHtbat`](https://trello.com/c/tstHtbat) et par `ROADMAP_BETA_2026.md` **ne
> correspond pas** à ce qui tourne dans ShinobiCore. Écrire la spec sans trancher
> l'écart produirait un service qui ne se branche sur rien.

---

## 1. Ce qui tourne aujourd'hui (mesuré)

La progression est une **table de niveaux**, pas une allocation de points.

`character/LevelTable.java` — 17 niveaux, valeurs en dur :

| Niveau | 1 | 4 | 7 | 10 | 13 | 16 | 17 |
|---|---:|---:|---:|---:|---:|---:|---:|
| **HP** | 100 | 300 | 500 | 800 | 1 200 | 1 700 | 2 000 |
| **Chakra** | 300 | 1 800 | 5 000 | 12 000 | 30 000 | 60 000 | 100 000 |

`character/Affinity.java` — **trois** axes de build, un seul stat buffé chacun :

| Affinity | Effet |
|---|---|
| `STRENGTH` | HP max |
| `INTELLIGENCE` | pool de chakra |
| `AGILITY` | vitesse de déplacement (après les effets de potion) |

`character/AffinityMultipliers.java` — un multiplicateur par paliers de niveau
(`×1.10` → `×1.40`), appliqué au seul stat correspondant à l'affinité.

Autres pièces en place :

- `character/ChakraAffinity.java` — **c'est ici** que vit le triangle des natures
  (`KATON`, `SUITON`, `FUTON`, `DOTON`, `RAITON`).
- `character/Rank.java` — `GENIN`, `CHUNIN`, `SPECIAL_JONIN`, `JONIN`, `ANBU`, `KAGE`.
- L'endurance vit dans **`ShinobiCombat`** (`combat/StaminaManager.java`), pas dans
  ShinobiCore.
- Les personnages sont persistés en YAML côté serveur
  (`plugins/ShinobiCore/characters/`), jamais dans le dépôt.

---

## 2. Ce que le plan décrit

**5 stats allouables** : Force, Endurance, Chakra, Contrôle, Précision.

```
HP max          = 100 + (endurance × 10)
Chakra max      = 50  + (chakra × 15)
Dégâts d'un sort = base × (1 + contrôle × 0,02) × (1 + rang × 0,5)
Chance de crit   = 5 % + (précision × 0,5 %)
```

**Rangs** : Académie → Genin (5 000 XP) → Chunin (20 000) → Jonin (60 000) →
Special Jonin (150 000) → ANBU **ou** Kage.

---

## 3. Les cinq divergences

| # | Sujet | Code | Plan |
|---|---|---|---|
| 1 | **Modèle** | table de 17 niveaux | 5 stats allouables |
| 2 | **Échelle HP** | 100 → 2 000 | 100 → ~200 |
| 3 | **Échelle chakra** | 300 → 100 000 | 50 → ~200 |
| 4 | **Rangs** | pas d'`ACADEMIE` ; `SPECIAL_JONIN` **avant** `JONIN` | Académie en entrée ; Special Jonin **après** Jonin |
| 5 | **Nom de classe** | le triangle des natures est `ChakraAffinity` | la roadmap écrit « résistances via `Affinity` » — mauvaise classe |

Les divergences 1 à 3 sont structurelles : les formules du plan ne peuvent pas
s'appliquer telles quelles aux personnages existants. Un facteur **500** sépare
les deux échelles de chakra au niveau maximum.

La divergence 4 n'est pas cosmétique non plus : l'ordre `SPECIAL_JONIN` avant
`JONIN` en code inverse deux paliers que le plan sépare de 90 000 XP.

La divergence 5 se corrige d'une ligne dans la roadmap.

---

## 4. L'incohérence de fond, indépendante du choix

Les 218 entrées d'`abilities.yml` déclarent un `chakra-cost` compris entre
**6 et 50**, médiane **50**.

Face au pool actuel, ça donne :

| Niveau | Pool | Casts avant épuisement (coût 50) |
|---|---:|---:|
| 1 | 300 | 6 |
| 7 | 5 000 | 100 |
| 13 | 30 000 | 600 |
| 17 | 100 000 | **2 000** |

Le pool est multiplié par 333 pendant que les coûts restent plats. **La ressource
cesse d'être une contrainte dès le niveau 7.** Ce n'est causé par aucune des deux
voies ci-dessous — c'est un déséquilibre déjà présent, que le design des stats
est l'occasion de corriger.

À titre de comparaison, les chiffres du plan (pool ~200, coût 50) donnent
**4 casts** — une ressource qui compte à chaque combat.

---

## 5. Deux voies

### Voie A — le plan fait foi, on reprend l'échelle

`StatsService` devient la source de vérité ; `LevelTable` et `AffinityMultipliers`
sont retirés au profit des 5 stats.

- ✅ Corrige l'économie de chakra du §4.
- ✅ Aligne code, Trello, roadmap et Miro sur un seul modèle.
- ✅ Le coût de migration ne sera **jamais plus bas qu'aujourd'hui** : la beta est
  fermée (`LAUNCHER_BETA_GATE=HELPER`), la population est le staff. Après
  l'ouverture de décembre, la même reprise touche tous les joueurs.
- ⚠️ Impose un reset des personnages existants (ou un script de conversion).
- ⚠️ Les `chakra-cost` des 218 abilities sont à revoir — mais ils tiennent déjà
  dans la fourchette 6-50, cohérente avec un pool de 200.

### Voie B — le code fait foi, les stats deviennent des modificateurs

`LevelTable` reste la base ; les 5 stats s'appliquent par-dessus, en remplaçant
`AffinityMultipliers` (qui n'a qu'un axe là où il en faut cinq).

- ✅ Aucune migration, aucun reset.
- ✅ Livrable plus vite — c'est la voie sûre si le 1er oct est serré.
- ⚠️ Laisse l'incohérence du §4 intacte, ou repousse sa correction.
- ⚠️ Les formules du plan sont à réécrire ; Trello, roadmap et Miro doivent suivre.

### Recommandation : **voie A**

L'argument décisif n'est pas l'élégance, c'est le calendrier. La reprise d'échelle
devra avoir lieu de toute façon — le §4 n'est pas tenable pour une beta publique.
La faire maintenant coûte le reset de quelques personnages staff ; la faire après
le 31 décembre coûte la confiance des joueurs.

Si le 1er oct paraît trop serré pour la voie A, la position de repli n'est pas la
voie B : c'est de décaler `StatsService` d'une semaine en prenant sur S4 (assets),
qui n'est sur le chemin critique de personne.

---

## 6. Ce qui est bloqué tant que ce n'est pas tranché

- La spec `StatsService` (Sprint 1 S2) — elle décrit un modèle ou l'autre.
- Le peuplement de `ProgressionLadder` (Sprint 1 S3) — les seuils XP dépendent du
  nombre de paliers, et `Rank` diverge déjà du plan (divergence 4).
- L'équilibrage des 29 techniques du Sprint 2 — les formules de dégâts consomment
  les stats.

Ne dépendent **pas** de la décision, et peuvent avancer en parallèle : les designs
Parchemins, Faction et KO.
