# Stats, progression et grades

> **Fiche système, à jour au 2026-10-12** (ShinobiCore sur le serveur dev avec la 0.4.169). Trois mécaniques
> distinctes, à ne pas confondre :
>
> | Mécanique | Ce qu'elle donne | Comment on la gagne | Visible par le joueur ? |
> |---|---|---|---|
> | **XP → niveau** | **1 point de stats par niveau** | Jouer : présence RP, entraînement, apprentissage | **Non** — seul le compteur de points bouge |
> | **Grade** (Genin → Kage) | **Points de Mérite** + bonus passifs | Décision RP (examen, mérite) | Oui |
> | **Points de stats** | Répartis dans les 6 stats (`/stats`, touche K) | Niveaux + bonus du staff | Oui |
>
> Historique des décisions : spec technique [`../technique/SPEC_STATS_SERVICE.md`](../technique/SPEC_STATS_SERVICE.md),
> étude comparative et audit dans [`../archives/`](../archives/), ancienne fiche grades
> [`../archives/conception/GRADES_2026-10-08.md`](../archives/conception/GRADES_2026-10-08.md).

---

## 1. Les six stats

| Stat | Abrév. | Ce qu'elle fait |
|---|---|---|
| **Taïjutsu** | `tai` | Dégâts des coups à mains nues (M1) et des techniques de taïjutsu ; un peu d'endurance |
| **Kenjutsu** | `ken` | Dégâts des coups d'arme et des techniques de kenjutsu |
| **Ninjutsu** | `nin` | Puissance des techniques de ninjutsu |
| **Contrôle** | `con` | Baisse le coût des techniques, accélère la régénération du chakra, soins plus rapides, critique |
| **Vigueur** | `vig` | PV max et endurance ; facilite la relève (Volonté) |
| **Chakra** | `cha` | Réserve de chakra |

Décision du 2026-09-23 (« option B ») : une stat par branche de l'arbre (Taï / Ken / Nin / Médical-Contrôle)
plus deux ressources. Genjutsu absent de la beta.

**Trois principes, prioritaires sur tout le reste :**

1. **Aucune stat ne ferme une porte RP** (examen chūnin, activité, mission).
2. **L'apprentissage des techniques est libre** : aucune stat requise. Un rang élevé est plus dur à maîtriser,
   coûte plus cher et frappe plus fort, c'est tout.
3. **Grande échelle de chakra** : ~100 000 au maximum ; une technique de rang S se lance 2 à 3 fois à pleine réserve.

### 1.1 Valeurs et formules (`stats:` de `ShinobiCore/config.yml`)

- Chaque stat va de **1 à 10**. **Plafond doux à 6** : au-delà, chaque point compte pour moitié (valeur effective
  `eff(10) = 8`).
- **PV max** = 200 + eff(Vigueur) × 100 → de **300** (Vigueur 1) à **1 000** (Vigueur 10), plus le bonus de grade.
- **Chakra max** = 4 000 + eff(Chakra) × 12 000 → de **16 000** à **100 000**, plus le bonus de grade.
- **Endurance max** = 100 + eff(Vigueur) × 8 + eff(Taïjutsu) × 4.
- **Régénération du chakra** (par 10 s, en méditant — seule source de régénération) = chakra max × (1 % +
  0,15 % × eff(Contrôle)).
- **Coût d'une technique** : fixé par son **rang**, réduit de 3 % par point effectif de Contrôle.

| Rang | E | D | C | B | A | S (et Hiden) |
|---|---|---|---|---|---|---|
| Coût en chakra | 400 | 800 | 2 000 | 5 000 | 12 000 | 35 000 |
| Coût en endurance (Taï / Ken) | 12 | 18 | 30 | 45 | 60 | 75 |

- **Dégâts d'une technique** = base × (1 + Σ poids(lettre) × eff(stat)). Chaque technique a des **lettres de
  scaling** par stat (S 0,12 · A 0,10 · B 0,07 · C 0,04 · D 0,02), écrites dans `abilities.yml` ou déduites de sa
  catégorie.
- **M1** × (1 + 0,08 × eff(Taïjutsu ou Kenjutsu)).
- **Critique** (techniques seulement) : 5 % + 0,5 % par point de Contrôle, ×1,5 — à confirmer ou retirer au
  playtest.
- **Roue des natures** : Katon › Fūton › Raiton › Doton › Suiton › Katon — ×1,25 dans le sens du cycle, ×0,8
  contre.
- **Fenêtre d'attribution** : les sorts MagicSpells / MythicMobs / ModelEngine infligent leurs dégâts hors de
  ShinobiCore ; tout dégât du lanceur pendant 4 s après le lancer (0,7 s pour la mêlée) est rattaché à sa technique
  et multiplié selon ses stats. Option `scaling-mode: MANUAL` + `%power%` pour un sort qui se scale lui-même.

> ⚠️ **Risque d'équilibrage connu** : les PV vont de 300 à 1 000 alors que le M1 fait 3 à 8 et que les sorts
> gardent les dégâts de leurs fichiers. Les dégâts ne sont pas encore à l'échelle des PV : réglage de contenu à
> faire au playtest.

### 1.2 La fiche shinobi (`/stats`, touche K)

> **Depuis la 0.4.170 (13/10)** : l'écran est remplacé par la **fiche shinobi « portrait en scène »** (package
> `fr.reborn.hud.menu.fiche`, textures `gui/fiche/` générées par `tools/gen_fiche_textures.py`, banc
> `REBORN_FICHE_DEBUG=1`) : personnage réel au centre, onglets **Identité / Attributs / Techniques / État**, lanternes
> en arc (signature conservée), répartition des points dans l'onglet Attributs. Données serveur ajoutées au paquet
> (ShinobiCore `fiche/`) : âge, taille, sexe, caractère et citation, dōjutsu, palier de Mérite, **états de service**
> (journal), techniques apprises et en apprentissage, état du corps en phrases. Commandes : joueur
> `/stats caractere <t1> <t2> <t3>`, `/stats citation [texte]` ; staff `/stats edit <joueur> caractere|citation|dojutsu …`,
> `/stats service <joueur> mission <rang> [texte] | ajouter <type> <texte> | masquer <n>`, `/stats json <joueur>`.
> La description ci-dessous de l'ancien écran reste valable pour la logique de répartition.


- Écran du mod « lanternes célestes » : une lanterne par stat (sa hauteur = sa valeur), plaques ema avec
  −/+, points en attente en silhouette dorée, « VALIDER » fait s'envoler les lanternes ; sons dédiés.
- **Infobulles en phrases, sans chiffres exacts** (« Tes techniques te coûtent moins cher », paliers Novice →
  Légendaire). Les tuiles (PV, chakra…) affichent encore des nombres : question ouverte.
- Onglet Techniques : lettres de scaling, puissance relative, coût, nombre de lancers à réserve pleine.
- **Ni le niveau ni l'XP n'y apparaissent.** Le sous-titre de la plaque ne montre plus « vers <grade> (+3) ».
- Commandes : `/stats` (ouvrir), `/stats alloc nin=3,vig=2`, `/stats respec`, `/stats jeton` ; staff
  `/stats give <personnage> <n>` (nom du **personnage**), `set`, `reset`, `/stats jeton give|take|set <joueur> <n>`.

### 1.3 Réinitialiser ses stats

- **Jetons de réinitialisation**, liés au **compte** (pas au personnage), stockés dans `respec-tokens.yml`.
  1 jeton = 1 réinitialisation. Le staff réinitialise gratuitement.
- Mode `stats.respec` : `TOKEN` (défaut), `FREE` (test), `STAFF`.
- ⚠️ **Aucune boutique ne vend encore de jetons** (pistes : boutique du launcher, ou jeton en ryo dans l'échoppe).

### 1.4 Les natures de chakra : le test de la Feuille

- **Tirage** (inchangé, serveur autoritaire, `ShinobiCore` `character/LeafTestManager`) : objet « Test de la Feuille »
  (clic droit) ou `/testfeuille` (`/testfeuille give [joueur]` donne l'objet) → confirmation → tirage pondéré par le
  clan (20 par nature, +40 sur celle du clan), attribution et sauvegarde. 3 natures au plus : 2ᵉ au niveau 6, 3ᵉ au
  niveau 12. L'objet est consommé au tirage.
- **Mise en scène v2 (16/10, mod `menu/tirage/`)** : après « Confirmer », la caméra quitte l'épaule et **glisse** vers
  un plan fixe de ¾ face posé dans le monde (`CameraTirage` : premier angle dont la vue n'est coupée par aucun bloc,
  sinon raccourci devant l'obstacle) ; le personnage lève la feuille **entre deux doigts** devant son visage, l'autre
  main en demi-sceau (animation `feuille_canal`, respiration en boucle). À droite, écran sobre : la feuille en gros plan
  (pixel art ×2), « **Maintenir [Espace] pour canaliser** » (ou clic maintenu) et sa jauge (2,2 s ; elle redescend si
  on lâche), la **lueur de chakra** qui grandit autour de la feuille, la liste des cinq natures et leur effet. Jauge
  pleine → le client envoie `go`, le serveur tire → **réaction animée** (32 images, 2,24 s) : Katon s'enflamme et se
  consume, Suiton se gorge d'eau et goutte, Doton se dessèche et s'effrite, Raiton se froisse en zigzag, Fūton se coupe
  net en deux. Puis le symbole canonique, le nom, une phrase, « Valider » ; la caméra revient en glissant.
- **v3 (16/10)** : au gros plan, les deux doigts dessinés sont remplacés, même place et même taille, par **le bout du
  bras Minecraft du joueur** qui pince la feuille par le haut (`TirageBras` : court tronçon carré 4 px, 3 en « slim »,
  face avant + dessous de la main, son skin manche comprise, texels carrés ×5, sans étirement) ; réactions inchangées.
- **Double nature** : la feuille se partage en bandes qui réagissent **l'une après l'autre** (natures déjà éveillées
  d'abord, la nouvelle en dernier).
- **Renoncer** (Échap avant la fin de la jauge) n'envoie rien : la feuille n'est pas consommée.
- **Vu des autres** : bouffée de chakra puis effet de la nature à la main du joueur (flammèches et fumée, gouttes,
  poussière de terre, étincelles, coup de vent), une seconde bouffée une seconde plus tard ; son de balise.
  L'animation du personnage reste locale (pas de synchro d'animation entre joueurs aujourd'hui).
- Textures `gui/tirage/` générées par `minecraft/mod-hud/tools/gen_tirage_textures.py`, animations par
  `tools/gen_tirage_animation.py` ; banc `REBORN_TIRAGE_DEBUG` ([`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5).

---

## 2. Les points de stats viennent de l'XP

**Points à répartir = (niveau − 1) × 1 + bonus du staff.** Le grade **ne donne plus** de points de stats
(décision du 2026-10-10).

- Ce changement a été appliqué une fois au chargement : un personnage qui avait réparti des points venus de son
  grade a vu ses stats remises au minimum et tout lui a été remboursé (log console « Stats : … remboursé »).
  Comme personne n'avait de niveau, la plupart sont repartis de **0 point**.
- Pour donner un niveau à quelqu'un : `/xp niveau <joueur> <n>`.

---

## 3. L'XP et les niveaux — invisibles

- **Le joueur ne voit ni son niveau, ni son XP, ni aucune annonce** de passage de niveau. Seul le compteur de points
  de `/stats` bouge (décision du 2026-10-10). Le niveau a été retiré de la tab list, de `/character list`, de
  l'ancien écran de sélection et de la liste côté serveur.
- **Courbe** : 400 XP pour passer du niveau 1 au 2, puis +120 par niveau (niveau 10 → 11 : 1 480 XP). **Niveau
  max 40** (`progression.niveau-max`).

| Source | Gain | Clé |
|---|---|---|
| Minute active (le joueur a bougé dans les 3 min, pas KO) avec un autre joueur à moins de 20 blocs | 3 XP | `gains.presence-rp`, `gains.rayon-presence` |
| Minute active seul | 1 XP | `gains.seul` |
| Minute où l'on frappe dans un terrain d'entraînement | +4 XP, plafond 120 / jour | `gains.entrainement`, `…-max-jour` |
| Séance de parchemin réussie | 40 × rang | `gains.seance-parchemin` |
| Technique apprise par parchemin | 100 × rang | `gains.technique-apprise` |
| Staff (missions, examens, events) | libre | `/xp donner` |

Multiplicateur de rang : D 1 · C 1,5 · B 2 · A 3 · S 5. **Aucune XP pour le combat hors zone** : on ne
récompense pas l'agression.

- Commande **réservée au staff** : `/xp voir | donner | retirer <joueur> <n> [raison] | niveau <joueur> <n>`.
- Les autres plugins donnent de l'XP via le service `XpService` (ex. ShinobiAbilities pour les séances).
- Code : `stats/Progression.java`, `stats/XpCommand.java`, `api/XpService.java`.

> ⚠️ **À revoir** : les paliers de niveau des tests de la Feuille (`LeafTestManager.LEVEL_GATE_*`) et les
> techniques à niveau minimum (`Requirement.LEVEL`) ont été pensés pour des niveaux de 1 à 17.

---

## 4. Les grades shinobi

### 4.1 L'échelle

**Académicien → Genin → Genin confirmé → Chūnin → Konin → Tokubetsu Jōnin → Jōnin → Commandant Jōnin → Kage.**

- Code : `character/Rank.java` (persisté par nom). Anciens noms relus : `ACADEMY` → Académicien,
  `SPECIAL_JONIN` → Tokubetsu Jōnin, `ANBU` et `SANNIN` → Jōnin.
- **On ne monte pas de grade à l'XP.** Le passage est **RP** : décision du village ou examen réussi en scène.
- **Les fonctions RP ne sont pas des grades** : ANBU, bras droit du Kage, conseiller, conseil du village… se
  cumulent avec le grade et donnent des avantages (techniques réservées, permissions, tenues), pas de stats. Elles
  relèvent du design Faction (à venir).

### 4.2 Ce que donne un grade

**Des points de Mérite** — 3 par passage (`stats.points-per-rank`), soit 24 au grade de Kage. **Séparés des stats,
ils ne servent encore à rien** : ils sont réservés à d'autres usages de gameplay, à définir. Ils sont transmis au
mod dans la fiche (`merit`) mais pas affichés.

**Des bonus passifs** (bloc `grades:` du config, relu au `/sc reload`) :

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

Le bonus de stat s'ajoute à la valeur allouée **avant** le plafond doux ; il peut dépasser 10.

> ❓ **Question ouverte** : ces bonus passifs restent en place depuis la séparation XP / Mérite. L'utilisateur n'a
> pas encore dit s'il faut les garder.

### 4.3 Promouvoir

```
/grade                                   son grade, ses bonus, les grades qu'on peut donner
/grade voir <joueur>                     le grade d'un autre personnage
/grade <joueur> <grade> [merite|examen]  promouvoir ou rétrograder
```

Un joueur gradé ne donne un grade que **dans son village**, toujours **inférieur au sien** :

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

### 4.4 Plafond de vie du serveur

La vie RP utilise l'attribut `MAX_HEALTH` de Minecraft, plafonné à 1 024 par défaut. Un Kage aux stats maximales a
~1 550 PV : `settings.attribute.maxHealth.max: 100000.0` dans `spigot.yml` (**fait sur le dev**, à faire en prod).
ShinobiCore l'écrit une fois dans la console si une vie est coupée.

---

## 5. Reste à faire

| Sujet | État |
|---|---|
| Usage des points de **Mérite** | à concevoir |
| Garder ou non les bonus passifs de grade | **à trancher** par l'utilisateur |
| Paliers de niveau (tests de la Feuille, `Requirement.LEVEL`) pensés pour 1-17 | à revoir |
| Mise à l'échelle des dégâts par rapport aux PV | playtest |
| Vente des jetons de réinitialisation | non branchée |
| Afficher ou non les chiffres des tuiles de la fiche | question ouverte |
| `spigot.yml` (plafond de vie) en prod | à faire au passage en prod |
| Tester en jeu : `/xp niveau`, compteur de points, remboursement | à faire |
