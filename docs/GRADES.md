# Grades shinobi

> Décidé le 2026-10-08. Remplace l'échelle « Académie → Genin → Chunin → Jonin → Special Jonin → ANBU / Sannin /
> Kage » et les seuils d'XP du plan d'origine : **on ne monte pas de grade à l'XP**.

## 1. L'échelle

Académicien → Genin → Genin confirmé → Chūnin → Konin → Tokubetsu Jōnin → Jōnin → Commandant Jōnin → Kage.

Code : `ShinobiCore/character/Rank.java` (persisté par nom). Les anciens noms sont relus :
`ACADEMY` → Académicien, `SPECIAL_JONIN` → Tokubetsu Jōnin, `ANBU` et `SANNIN` → Jōnin.

**Les fonctions RP ne sont pas des grades.** ANBU, bras droit du Kage, conseiller, conseil du village… se cumulent
avec le grade et donnent des avantages (techniques réservées, permissions, tenues), pas de stats. Elles relèvent du
design Faction Konoha (à venir).

## 2. Ce que donne un grade

Deux choses, réglables dans le bloc `grades:` de `config.yml` (relu au `/sc reload`) :

- **Des points de stats** : 3 par passage (`stats.points-per-rank`), soit 24 au grade de Kage.
- **Des bonus passifs**, qui marquent la différence entre grades sans écraser les stats :

| Grade | PV | Chakra | Endurance | Bonus sur les 6 stats |
|---|---|---|---|---|
| Académicien | — | — | — | — |
| Genin | +40 | +2 000 | +5 | — |
| Genin confirmé | +80 | +4 000 | +10 | — |
| Chūnin | +130 | +7 000 | +15 | +1 |
| Konin | +180 | +10 000 | +20 | +1 |
| Tokubetsu Jōnin | +240 | +13 000 | +25 | +1 |
| Jōnin | +300 | +17 000 | +30 | +2 |
| Commandant Jōnin | +350 | +20 000 | +35 | +2 |
| Kage | +400 | +25 000 | +45 | +3 |

Repères : les stats seules mènent les PV de 300 à 1 000 et le chakra de 16 000 à 100 000 (`SPEC_STATS_SERVICE.md`
§1.3). Le bonus de stat s'ajoute à la valeur allouée **avant** la courbe de rendement (`StatFormulas.eff`) ; il peut
dépasser le plafond d'allocation de 10.

## 3. Passer de grade

Le passage est **RP** : une décision du village ou un examen réussi en scène. La commande ne fait qu'enregistrer.

```
/grade                                   son grade, ses bonus, les grades qu'on peut donner
/grade voir <joueur>                     le grade d'un autre personnage
/grade <joueur> <grade> [merite|examen]  promouvoir ou rétrograder
```

Un joueur gradé ne donne un grade que **dans son village**, toujours **inférieur au sien**, et selon ce tableau :

| Grade donné | Au mérite | Sur examen réussi |
|---|---|---|
| Genin, Genin confirmé | Tokubetsu Jōnin et plus | — |
| Chūnin | Jōnin et plus | Tokubetsu Jōnin et plus |
| Konin | Jōnin et plus | — |
| Tokubetsu Jōnin, Jōnin | Commandant Jōnin et plus | — |
| Commandant Jōnin | Kage | — |
| Kage | staff | — |

Rétrograder : un Kage, sur un grade inférieur au sien (`grades.retrogradation`). Le staff
(`shinobicore.grade.staff`) n'a aucune limite. Chaque passage est noté dans le journal du Poste de garde.

## 4. Après la mise en production

- Les personnages existants gagnent des points : le nombre de passages d'un Jōnin passe de 3 à 6.
- Les personnages ANBU ou Sannin deviennent Jōnin ; leur fonction sera rendue avec le design Faction.
