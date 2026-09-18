# Session — boards et planning

## 1. Sujet

Réaligner documents, Trello et Miro sur l'état réel après la réunification, puis
poser des dates d'échéance et des checklists sur les quatre sprints pour que la
dérive se voie sans ouvrir le dépôt.

## 2. Ce qui est fait

### Les documents — `f4c54da`, `2321e34`

Trois documents affirmaient encore que la production ne vivait sur aucune branche
partagée. Faux depuis la fusion du jour.

- **`ETAT_DES_LIEUX.md` §2** — tableau des artefacts remis à plat : launcher
  **0.3.42** et `reborn-hud` **0.4.135** tous deux sur `main`, plus aucun écart.
  L'encart rouge « dette à résorber » est remplacé par la note de résolution.
- **`ETAT_DES_LIEUX.md` §2 (ajout)** — nouvel avertissement : **les tags `mods-v*`
  ne sont pas des marqueurs de version fiables.** Ils ont été posés sur `main`
  pendant que les jars étaient buildés depuis un worktree en avance, d'où
  `mods-v3.1.90` → 0.4.132 et `mods-v3.1.91` → 0.4.133 alors que 0.4.134 puis
  0.4.135 étaient déjà publiées. La version livrée se lit dans le manifest live.
- **`ETAT_DES_LIEUX.md` §7** — `modrinth-sync` passe de « en cours » à « atterri » ;
  le Technique Creator entre dans les chantiers actifs.
- **`ROADMAP_BETA_2026.md` §6** — risque 5 (« Dette Git ») levé.
- **`AUDIT_COHERENCE.md` §3** — note de résolution ajoutée. Le constat daté du
  2026-09-08 n'est **pas** réécrit : c'est un instantané, il garde sa valeur.

### Le re-séquencement du Sprint 1 — `2321e34`

Sprint 1 S1 (11-17 sept) a dérivé : les 3 designs prévus (Stats, Progression,
Parchemins) n'ont pas été faits, la semaine est partie sur le Technique Creator
et la réunification du tronc. La roadmap l'écrit noir sur blanc plutôt que de
laisser le plan mentir.

**Choix acté : absorber, pas décaler.** Le ship reste au 31 décembre, S3 et S4 du
Sprint 1 sont inchangés, et S2 devient une semaine chargée à **5 designs**. Le
pari : les 3 designs reportés étaient déjà écrits à ~80 % sur leurs cartes Trello
— le coût réel est de la mise au propre, pas de la conception.

Si S2 déborde, l'ordre de coupe reste celui du §6 : **Nin rang B d'abord**.

### Trello

- **Carte `modrinth-sync`** ([`A1wrwmZ4`](https://trello.com/c/A1wrwmZ4)) — le
  blocage « 22 commits non mergés / 103 de retard » et la consigne de renommage
  n'ont plus d'objet.
- **Carte créée : 🧬 Technique Creator**
  ([`xylVgQ58`](https://trello.com/c/xylVgQ58)) — deux semaines de travail
  n'apparaissaient nulle part sur le board. Placée dans `☄️⚡️In Production`
  plutôt que dans les chantiers, pour ne pas désynchroniser le mapping
  « 16 chantiers » ⇄ roadmap §4.
- **Carte clans** ([`ScwJ0pMp`](https://trello.com/c/ScwJ0pMp)) — le chiffre de
  **18 sorts claniques** est acté, la question fermée.
- **Dates d'échéance** sur les 4 cartes Sprint : 8 oct · 5 nov · 3 déc · 31 déc
  (18 h heure locale).
- **Checklists** sur les 4 cartes :

| Sprint | Items | Cochés |
|---|---:|---:|
| 1 — Foundation & Design | 17 | **6** |
| 2 — Combat & Konoha centre | 10 | 0 |
| 3 — Systèmes RP & Faction | 9 | 0 |
| 4 — Build & SHIP | 8 | 0 |

La checklist Sprint 1 dit la vérité plutôt que le plan : S0 coché, les 3 designs
marqués `[reporté de S1]`, et les conséquences de la voie A ajoutées (script de
conversion, repasse des 218 `chakra-cost`, alignement de `Rank`).

### Miro

Frame **« 📌 État réel — versions & artefacts »** repassé au 17/09 : bloc
artefacts corrigé, et l'encart « 🔴 DETTE À SOLDER » devenu « ✅ DETTE SOLDÉE »
— passé du rouge-alarme au liseré or, pour rester dans la palette Akatsuki.
Vérifié sans débordement (textes rendus à 1047 dans un cadre qui va à 1110).

## 3. État de compilation / test

Sans objet — documents et boards uniquement, aucun code touché.

Les liens Trello et Miro ont été vérifiés par relecture après écriture (les
réponses d'API confirment le contenu enregistré).

## 4. Ce qui n'est PAS fini

- ⚠️ **Sur Miro, seul le frame « État réel » a été repris.** Les frames *⚔️
  Système Combat — Matrice* et *📈 Progression Shinobi — Timeline* n'ont pas été
  relus. La matrice décrit la cible à 49 techniques (le périmètre beta est 39) et
  peut encore porter l'ancien comptage « 2 × 11 = 22 » pour les sorts claniques,
  désormais caduc.
- ⚠️ **Le MCP Trello s'est déconnecté en fin de session** (`ENOTFOUND
  mcp.trello.com`). **Toutes les écritures listées ci-dessus sont passées avant
  la coupure** et sont confirmées par les réponses d'API. Mais si tu reprends sur
  le board, vérifier que la connexion revient.
- Les cartes Sprint n'ont **ni membre assigné**. Volontaire : pas d'information
  fiable sur qui fait quoi.

## 5. Décisions ouvertes

Tranchées ce jour :

- ✅ **Absorber la semaine perdue**, ship maintenu au 31 décembre.
- ✅ **18 sorts claniques** (9 clans à mécaniques × 2 ; « Neutre » est polyvalent
  sans bonus par définition, « Autre » reste du sur-mesure staff).

Restent ouvertes :

- **Les dates supposent le scénario « absorber ».** Si tu changes d'avis et
  préfères décaler d'une semaine (ship au 7 janvier), les 4 dates sont triviales
  à déplacer.
- **L'axe social** dans les stats — beta ou post-beta ? Cf. la fiche design.

## 6. Fichiers touchés

**Dépôt** :

```
M  docs/ETAT_DES_LIEUX.md        (§2 tableau + avertissement tags, §7 chantiers)
M  docs/ROADMAP_BETA_2026.md     (§6 risque 5, Sprint 1 S1/S2, §3.2 clans)
M  docs/AUDIT_COHERENCE.md       (§3 note de résolution)
```

**Trello** — board *[WIP] FR - Naruto* :

```
~  carte A1wrwmZ4  modrinth-sync         (description)
+  carte xylVgQ58  Technique Creator     (créée, liste In Production)
~  carte ScwJ0pMp  Spécificités par clan (description, 18 acté)
~  cartes L72RSjdK / UoFMizpN / zIlrZylj / jDN8pkpu  (due + checklist)
```

**Miro** — board *[WIP] FR - Shinobi Reborn* :

```
~  frame 3458764683042789174  « État réel — versions & artefacts »
   (titre, bloc artefacts, encart dette → soldée)
```
