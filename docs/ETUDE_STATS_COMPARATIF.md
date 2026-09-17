# Étude comparative — systèmes de statistiques

> Entrée du design **Stats** (Sprint 1 S2). Établie le **2026-09-17**.
>
> Motif : les cinq catégories du plan (Force, Endurance, Chakra, Contrôle,
> Précision) sont **provisoires**. Avant de les figer, regarder ce qui marche
> ailleurs — grands RPG d'un côté, concurrents Naruto de l'autre.
>
> Ce document ne tranche pas. Il expose ce qui se fait, en tire des principes, et
> pose trois options chiffrées pour Reborn. La décision vient après.
>
> Suite du dossier : [`AUDIT_STATS_ET_PROGRESSION.md`](./AUDIT_STATS_ET_PROGRESSION.md)
> (l'existant en code) et [`SPEC_STATS_SERVICE.md`](./SPEC_STATS_SERVICE.md)
> (la mécanique, dont le §1 dépend de cette étude).

---

## 1. Les grands RPG

### 1.1 Elden Ring — 8 attributs, et une courbe qui fait le travail

| Attribut | Gouverne |
|---|---|
| Vigor | HP max, résistances aux statuts |
| Mind | jauge de FP (le « chakra » du jeu) |
| Endurance | stamina + charge transportable |
| Strength | dégâts des armes lourdes |
| Dexterity | armes légères, **réduit le temps d'incantation**, dégâts de chute |
| Intelligence | sorts |
| Faith | incantations |
| Arcane | saignement, taux de drop |

Le mécanisme central n'est pas la liste, c'est le **soft cap** : un seuil au-delà
duquel chaque point rapporte nettement moins, sans être interdit.

| Attribut | Soft caps |
|---|---|
| Vigor | **40**, puis 60 |
| Mind | **50**, résiduel jusqu'à ~60 |
| Endurance | 15, 30, **50** |

**Ce qu'il faut en retenir** : la courbe remplace la règle. Personne n'interdit de
monter Vigor à 80 — c'est simplement un mauvais investissement, et le joueur le
sent sans lire un wiki. C'est exactement le « impact au fur et à mesure de
l'allocation » recherché.

### 1.2 Le piège documenté : la *dump stat*

La littérature de game design est convergente : dès qu'une stat n'est utile qu'à
certains builds, elle devient celle où personne ne met rien. Les parades
identifiées :

1. **Rendements décroissants** — répartir devient mécaniquement plus rentable que
   tout empiler.
2. **Coût croissant** — passer de 4 à 5 coûte 5 points, de 1 à 2 en coûte 2.
3. **Structure de classe** — le problème s'atténue si le personnage est cadré.
   En allocation libre, il est maximal.
4. **Formules transparentes** — le joueur doit voir la conversion stat → effet.

Le corollaire, et c'est le critère qui devrait trier nos options : **toute stat
doit être désirable pour tout build, à des degrés divers.** Une stat que le
kenjutsu ignore complètement est une stat morte pour la moitié du serveur.

---

## 2. Les concurrents Naruto

### 2.1 Nin Online — le voisin le plus proche

MMORPG Naruto, allocation libre, **5 points par niveau**, départ à 5 dans chaque
stat, 250 HP et 50 chakra de base.

| Stat | Effet | Conversion |
|---|---|---|
| **Strength** | dégâts d'arme (sabre, kunai) + jutsu de maîtrise d'arme | 4 pts → +1 dégât |
| **Agility** | dégâts taïjutsu, **temps d'incantation**, vitesse d'attaque | 3 pts → +1 poing · 100 pts → −200 ms |
| **Intellect** | ninjutsu et genjutsu, dégâts de shuriken | 2 pts → +1 ninjutsu |
| **Chakra** | pool de chakra | 1 pt → +5 chakra |
| **Fortitude** | HP max + stamina | 1 pt → +8 HP |

Remarquable : les noms sont classiques (Strength/Agility/Intellect) mais les
**effets sont mappés sur les disciplines** — Strength = armes, Agility = taijutsu,
Intellect = ninjutsu. Le joueur qui veut « être un sabreur » sait où mettre ses
points.

### 2.2 Shindo Life — 4 stats, brutalement lisibles

| Stat | Effet |
|---|---|
| **Ninjutsu** | dégâts ninjutsu et bloodline, capacité et régén de Chi |
| **Taijutsu** | dégâts mêlée et arme, capacité de stamina, attaques lourdes |
| **Chi** | capacité de Chi, dégâts des modes |
| **Health** | HP + jauge de blocage |

18 points par niveau. Les stats portent **littéralement le nom des disciplines de
la fiction**. Aucune traduction mentale nécessaire.

### 2.3 Naruto Online — offense et défense appariées

Attack / Defence (taijutsu), Ninjutsu / Resistance (ninjutsu), plus Initiative
(ordre d'action) et Critical. Chaque type de dégâts a **sa contre-stat dédiée**.
Pertinent si on veut que la défense soit un choix de build et pas une conséquence
passive des HP.

### 2.4 Naruto Precursors (Minecraft) — la piste clanique

Feuille de stats custom où **chaque clan multiplie en permanence les stats dans
lesquelles il est bon**, et où dojutsu et modes ajoutent des stats tant qu'ils sont
actifs. Directement transposable : Reborn a 11 clans et des dojutsu.

### 2.5 Tableau de synthèse

| Jeu | Nb stats | Mappées sur les disciplines ? | Pools comme stats ? |
|---|---:|---|---|
| Elden Ring | 8 | oui (armes lourdes / légères / sorts) | oui (Vigor, Mind, Endurance) |
| Nin Online | 5 | oui (armes / tai / nin) | oui (Chakra, Fortitude) |
| Shindo Life | 4 | oui, explicitement nommées | oui (Chi, Health) |
| Naruto Online | 4 + 2 | oui, en paires off/def | non |
| **Reborn (plan)** | **5** | **non** | oui (Endurance, Chakra) |

---

## 3. Le constat qui saute aux yeux

**Tous les concurrents Naruto mappent leurs stats sur la trinité de la fiction.
Le plan de Reborn est le seul à ne pas le faire.**

Reborn a pourtant une trinité de combat parfaitement établie — 12 Taïjutsu,
12 Kenjutsu, 15 Ninjutsu — et un arbre de progression à **4 branches**
(Tai / Ken / Nin / Médical). Or dans les 5 stats du plan :

- Un joueur qui veut être **sabreur** n'a aucune stat où mettre ses points. Le
  kenjutsu n'apparaît nulle part.
- **Force** ne sert qu'au M1 taïjutsu et au poids porté → dump stat pour tout
  ninjutsuka.
- **Précision** ne donne que du crit (+5 % au cap) → l'investissement le moins
  attractif du lot, donc la deuxième dump stat.
- **Contrôle** et **Chakra** font tous deux « être meilleur en ninjutsu » par deux
  chemins différents, ce qui est redondant.

Autrement dit : les catégories du plan sont des abstractions à la D&D posées sur
un jeu dont la structure est ailleurs. Ton instinct que « les catégories sont assez
floues » est corroboré par la comparaison.

---

## 4. La question de la courbe

Tu veux que l'impact se ressente **au fur et à mesure** de l'allocation. Quatre
formes possibles, par ordre croissant d'intérêt :

| Forme | Comportement | Effet sur les builds |
|---|---|---|
| **Linéaire** (le plan actuel) | +10 HP par point, toujours | prévisible, mais récompense le all-in — fabrique des dump stats |
| **Coût croissant** | le point N coûte N | freine le all-in, mais l'arithmétique est opaque en jeu |
| **Paliers / breakpoints** | un effet se débloque à 5, 10, 15 | très lisible, crée des objectifs — mais des plateaux morts entre deux paliers |
| **Soft caps** (Elden Ring) | plein effet jusqu'au seuil, puis rendement réduit | **recommandé** : se ressent sans être lu, pousse à diversifier sans interdire de se spécialiser |

Rien n'empêche de combiner **soft caps** (la courbe de fond) et **breakpoints**
(un déblocage identitaire à 10, par exemple l'accès aux techniques de rang B d'une
discipline). C'est ce que font la plupart des jeux cités.

---

## 5. Trois options pour Reborn

### Option A — miroir des disciplines (5 stats)

`Taïjutsu` · `Kenjutsu` · `Ninjutsu` · `Vigueur` (HP + endurance) · `Chakra` (pool + régén)

- ✅ Alignée sur la structure réelle du jeu et sur tous les concurrents.
- ✅ Chaque stat porte une fantaisie de build immédiate : « je suis un sabreur ».
- ✅ Aucune dump stat évidente : tout le monde veut Vigueur et Chakra.
- ⚠️ La branche **Médical** de l'arbre de progression n'a pas de stat.
- ⚠️ Un pur ninjutsuka met 0 en Kenjutsu — spécialisation assumée, à encadrer par
  les soft caps.

### Option B — miroir des quatre branches (6 stats)

`Taïjutsu` · `Kenjutsu` · `Ninjutsu` · `Contrôle` (soin, coût des sorts, échecs) ·
`Vigueur` · `Chakra`

- ✅ Correspondance **exacte** avec les 4 branches de l'arbre de progression.
- ✅ `Contrôle` donne une identité au support/médic, absent de l'option A.
- ✅ 6 stats reste dans la fourchette confortable (4-8).
- ⚠️ Une stat de plus à équilibrer et à afficher au HUD.

### Option C — le plan actuel, corrigé

Garder Force / Endurance / Chakra / Contrôle / Précision, en fusionnant Précision
dans Contrôle et en ajoutant une stat d'arme.

- ✅ Le moins de changement par rapport aux documents existants.
- ⚠️ Ne corrige pas le problème de fond du §3 : les catégories ne parlent pas le
  langage du jeu.
- ⚠️ Conserve deux stats redondantes sur l'axe ninjutsu.

### Recommandation

**Option B**, pour une raison structurelle plus que esthétique : elle fait
coïncider les stats, les 4 branches de l'arbre de progression et les 3 disciplines
de combat. Ces trois systèmes doivent de toute façon être cohérents entre eux ; les
faire coïncider dès maintenant évite une traduction permanente entre trois
vocabulaires — et c'est précisément le genre de dette qu'on vient de passer une
journée à rembourser.

Avec la couche clanique de Naruto Precursors en option : chaque clan multiplie la
stat qu'il incarne (Uchiha → Ninjutsu, Hyuga → Taïjutsu, Rock → Taïjutsu…), ce qui
donne du poids au choix de clan sans ajouter de système.

---

## 6. Ce que ça ne tranche pas

Volontairement laissé ouvert, parce que ça dépend de ton retour :

- Le **nombre de points par rang** et le plafond par stat (la spec propose 3 et 10).
- Les **seuils de soft cap** — ils se calibrent au playtest, pas sur le papier.
- La branche **Médical** : stat dédiée (option B) ou dérivée du Contrôle sur
  l'arbre uniquement ?
- Le **multiplicateur clanique** : bonus permanent, ou seulement un bonus de départ ?
- Les **résistances par nature** (`ChakraAffinity`) : stat ou hors stats ?

> Rappel de cadrage, qui vaut pour toute la suite : l'objectif est **attrayant et
> jouable**, pas parfait. Les soft caps et les coefficients sont des leviers
> réglables à chaud — on les calibre en jouant, on ne les démontre pas sur le papier.

---

## Sources

- [Elden Ring Wiki — Stats (Fextralife)](https://eldenring.wiki.fextralife.com/Stats)
- [Elden Ring Stat Caps Explained (GameRant)](https://gamerant.com/elden-ring-stat-attribute-soft-hard-caps-diminishing-returns/)
- [All Elden Ring stats explained (GamesRadar+)](https://www.gamesradar.com/elden-ring-stats-attributes/)
- [Nin Online Wiki — Statistics](https://ninonline.fandom.com/wiki/Statistics)
- [Nin Online — Optimized Builds & Stat Distribution (Steam)](https://steamcommunity.com/sharedfiles/filedetails/?id=3696557940)
- [Shindo Life Wiki — Stats](https://shindo-life-rell.fandom.com/wiki/Stats)
- [Shindo Life Stats Guide (Gamer Empire)](https://gamerempire.net/shindo-life-stats-guide/)
- [Naruto Online — Ninja Stats Guide](https://narutoguides.com/ninja-stats-guide/)
- [RPG Stat Systems Explained (StraySpark)](https://www.strayspark.studio/blog/rpg-stat-systems-character-progression-design)
- [Dump Stat (All The Tropes)](https://allthetropes.org/wiki/Dump_Stat)
- [Naruto Precursors — Minecraft Server](https://www.planetminecraft.com/server/naruto-precursors/)
