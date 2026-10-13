# KO, RP médical et statistiques : Nydo et Ninshu face à Reborn

> **Ce que contient ce document :** comment NydoMod 5.5 et Ninshu Origins 2.1.8 gèrent le KO, le médical et les
> statistiques (gameplay et visuel), ce que Reborn a déjà, et ce qu'on peut intégrer au serveur. Établi le
> 2026-10-12 à partir du code décompilé (formules lues dans `NinjaStatFormula`, `PlayerEvents`,
> `MedicalBedRestHandler`…). Contexte général : [`BENCHMARK_MODS_ET_SERVEURS.md`](./BENCHMARK_MODS_ET_SERVEURS.md).

---

## 1. Où en est Reborn (12/10)

| Système | Ce qui est codé (serveur de test) | Ce qui manque |
|---|---|---|
| **KO** ([fiche](../gameplay/KO_ET_MORT_RP.md)) | À terre 45 s (ramper, chuchoter, `/aide`) → inconscient 5 min → `/hopital` → ATA ; PV persistants, plancher 30 % au repos ; blessures ; menu sur les corps (ausculter, porter, gorge, fouiller, RPK) ; RPK validée par le staff ; exécutions ; scène de mort | **Jamais testé à deux comptes** ; technique `volonte` absente (personne n'a la Volonté) ; aucune animation (exécutions, chute, reptation) ; coma au 3ᵉ KO et saignement non branchés |
| **Médical** | Paume de soin (`/paume`, rangs 1 et 2), `/soigner` des blessures avec recettes, armoires à médicaments, encyclopédie, Infirmerie du Poste de garde, lits et hôpitaux | `MedicalService` (antidotes, coma) ; aucun acte médical « lourd » ; chantier Médical prévu au Sprint 3 |
| **Stats** ([fiche](../gameplay/STATS_ET_PROGRESSION.md)) | 6 stats de 1 à 10 (plafond doux à 6), PV 300 → 1 000, chakra 16 000 → 100 000, écran « lanternes célestes » sans chiffres, XP invisible (1 niveau = 1 point) | Usage du Mérite ; bonus de grade à trancher ; plafond de points ; **dégâts pas à l'échelle des PV** ; chaque stat ne fait qu'une ou deux choses |

---

## 2. Le KO chez eux

### NydoMod : pas de KO

Mort vanilla. La seule exception est le bijū : à un coup mortel, il sauve son hôte (PV à 15 % et possession).
**Rien à reprendre** côté KO.

### Ninshu Origins : « Downed », plus simple que le nôtre

| Étape | Fonctionnement (`PlayerEvents`) |
|---|---|
| Coup mortel | Annulé une fois : le joueur tombe à 1 PV, état **à terre 40 s** (800 ticks) |
| Pendant | Il peut **abandonner** (touche « give up ») |
| Réanimation par un allié | Stat Médical ≥ 50 : canal de 5 s à 4,5 blocs, 10 chakra → relevé à 4 PV |
| Auto-réanimation | Stat Médical ≥ 25 : canal de 7 s, 50 chakra |
| Soin interrompu | Plus de ~3 ticks d'interruption → **le canal repart de zéro** |
| Fin du délai | Mort par hémorragie (`bled_out`), **créditée à celui qui a mis à terre** (`recordDowner`) |
| Lit médical | S'y coucher **réanime** un joueur à terre, soigne 1 PV/s, accélère ×4 la guérison des blessures persistantes |

**Verdict** : notre KO est bien plus riche (inconscience, hôpital, ATA, blessures, corps, RPK). Trois détails
valent d'être repris (§5).

---

## 3. Le RP médical chez eux

| | Ninshu | Nydo |
|---|---|---|
| **Stat Médical** | Débloque des **actes par palier** : auto-réanimation (25), réanimer un allié (50), soin de groupe (100) ; +PV max = Médical / 2 (plafond +1 000) | Soins × (1 + 1,25 % par point) ; difficulté de chirurgie −40 % au maximum ; protège l'intégrité d'un œil |
| **Actes lourds** | Implant d'ADN (réussite liée au Médical, échec = échantillon perdu) ; arrachage d'œil (Médical de l'attaquant contre résistance) | **Chirurgie oculaire** en mini-jeu à 3 phases (incision, connexion du chakra, sutures ; 5 erreurs = échec) ; chirurgiens PNJ payants |
| **Blessures** | **Blessures persistantes** des Huit Portes (2 à 16 min, survivent à la déconnexion, ×4 au lit) | Cécité (demi-écran), usure du Mangekyō |
| **Objets** | Lit médical, seringue, **bandage oculaire** | Fiole à œil, table d'opération |
| **Soin entre joueurs au quotidien** | Réanimation seulement | Quasi absent |

**Verdict** : leur médical tient en deux idées. **Des actes qui se débloquent par niveau de compétence**, et
**un mini-jeu de chirurgie pour les actes lourds**. Le soin du quotidien est plus abouti chez nous (paume, recettes,
armoires, ATA).

---

## 4. Les statistiques chez eux

### Gameplay

**NydoMod** — 8 stats (Ninjutsu, Taijutsu, Genjutsu, Contrôle du chakra, Vitalité, Défense, Agilité, Médical),
0 à 120 (180 avec bonus), 2 points par niveau + bonus de promotion. **Chaque stat a plusieurs effets, dont des
effets d'« utilité »** qui ne sont pas des dégâts (`NinjaStatFormula`, valeurs au maximum) :

| Stat | Effets |
|---|---|
| Ninjutsu | Puissance +1,25 %/pt ; **signes −45 %** ; charge −12 % ; on garde plus de mobilité en incantant |
| Taijutsu | Puissance +1,25 %/pt ; **pression de garde +60 %** ; endurance +1/pt |
| Contrôle du chakra | **Coût −30 %** ; **régénération +60 %** ; charge −16 % |
| Vitalité | PV +1,25 %/pt |
| Défense | Dégâts reçus × 100 / (100 + Défense) (rendement décroissant) |
| Agilité | **Déplacement +35 %**, vitesse de combat +25 %, **recharges −20 %** |
| Genjutsu | Puissance et résistance |
| Médical | Soins, chirurgie, protection des yeux (§3) |

Les techniques **exigent une stat minimale** selon leur rang (12 / 24 / 48 / 72 / 96) : contraire à notre règle.

**Ninshu Origins** — 14 stats (Ninjutsu, Genjutsu, Shurikenjutsu, Invocation, Mitejutsu, Kinjutsu, Médical,
Endurance, Senjutsu, Vitesse, Taïjutsu, Kenjutsu, Intelligence, Wit) et 4 réserves de points. Le **rang affiché
dépend de la seule stat Ninjutsu**. Leur idée intéressante est ailleurs : des **sous-stats de maîtrise cachées**
par discipline (Puissance, Forme, Jeu de jambes, Condition) qui **montent en pratiquant** (frapper, mannequin,
banc de musculation) et réduisent recharges et coûts d'endurance.

### Visuel

- **Ninshu** : une page parchemin qui liste « Nom : valeur » (textures `ninjapagestats`, `statpagenew1`). Très
  basique.
- **Nydo** : une « Ninja Card » avec portrait, rang, niveau, PV, chakra, **radar de stats** et points à dépenser
  (`NinjaCardRenderer`).
- **Reborn** : les lanternes célestes, avec des infobulles en phrases et sans chiffres. C'est **plus abouti que
  les deux**. Seul le **radar** pourrait servir, par exemple sur la fiche d'un autre joueur ou au Poste de garde.

---

## 5. Ce qu'on peut intégrer au serveur

| # | Idée | Origine | Pourquoi | Effort |
|---|---|---|---|---|
| **KO** | | | | |
| K1 | **Attribuer la mise à terre** : on retient qui a mis à terre, affiché au staff (journal, Poste de garde, demande de RPK) | Ninshu | Arbitrage des RPK et des plaintes | Facile |
| K2 | **Soin interrompu = repart de zéro** (paume, `/soigner`) | Ninshu | Oblige à sécuriser la zone, donne un rôle aux escortes | Facile |
| K3 | **Lit d'hôpital qui accélère** la guérison des blessures et de l'ATA | Ninshu | Rend l'hôpital utile hors réveil | Facile |
| K4 | Exécutions mises en scène (cinématique + animation couplée) | Nydo, Ninshu | Exécutions prévues comme cosmétiques vendables | Moyen |
| **Médical** | | | | |
| M1 | **Actes débloqués par rang médical** : relever un blessé à terre sur place, réanimation d'urgence, chirurgie | Ninshu | Progression médicale lisible et RP, sans stat bloquante (le rang est donné en RP) | Moyen |
| M2 | **Mini-jeu de chirurgie** pour les actes lourds : blessure Urgente, greffe d'œil, retrait d'un sceau | Nydo | Scène médicale jouée, échec possible | Moyen |
| M3 | **Bandage** (œil, bras) posé par un médic, visible sur le skin | Ninshu (idée prolongée) | RP visible des blessures | Facile |
| M4 | Blessures persistantes qui **survivent à la déconnexion** (à vérifier chez nous) | Ninshu | Pas d'esquive par la déco | Facile |
| **Stats** | | | | |
| S1 | **Donner des effets d'utilité à nos 6 stats**, sans en ajouter : Ninjutsu accélère les signes, Taïjutsu augmente la pression de garde, Contrôle réduit aussi la charge, Vigueur réduit le temps à terre… | Nydo | Chaque point devient sensible en jeu, pas seulement en dégâts | Facile à moyen |
| S2 | **Maîtrise cachée par discipline** qui monte en pratiquant (Taï / Ken / Nin), invisible comme l'XP | Ninshu | Récompense la pratique sans grind visible, cohérent avec l'XP invisible | Moyen |
| S3 | **Rendement décroissant** du type 100 / (100 + x) pour toute future défense ou résistance | Nydo | Évite les valeurs absurdes en haut de l'échelle | Facile |
| S4 | **Radar de stats** sur la fiche d'un autre joueur (inspection RP) ou au Poste de garde | Nydo | Lisible d'un coup d'œil | Facile |
| — | Stat requise pour une technique, rang = une stat, 14 stats | Nydo, Ninshu | **Contraire à nos décisions** : à écarter | — |

## 6. Ordre conseillé

1. **Tester la 0.4.169 à deux comptes** (KO, RPK, exécution, scène de mort) : c'est bloquant, rien n'a été vérifié
   à deux.
2. **Décider** : technique `volonte` (créer ou `pour-tous`), bonus de grade, usage du Mérite (cf.
   [`CHANTIERS.md`](../CHANTIERS.md) §4).
3. **Quick wins** K1, K2, K3, M3, M4, S3 : quelques heures chacun, côté ShinobiCore.
4. **Stats S1** (effets d'utilité), en même temps que la **mise à l'échelle des dégâts par rapport aux PV**, au
   playtest.
5. **Design médical (Sprint 3)** : M1 + M2, puis S2.
