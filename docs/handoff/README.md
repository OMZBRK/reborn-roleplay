# Passation entre machines

> Le projet est travaillé depuis **trois machines** (PC portable, PC principal,
> Mac) et souvent par **plusieurs sessions Claude Code en parallèle sur le même
> working tree**. Ce dossier est le point de rendez-vous : chaque journée de
> travail y laisse de quoi être reprise ailleurs sans rien redécouvrir.
>
> Convention posée le 2026-09-08.

---

## À quoi ça sert

Git dit *ce qui* a changé. Il ne dit pas **pourquoi**, ni **ce qui est fini**,
ni **ce qui casse si on lance tel quel**. C'est précisément ce qui manque quand
on rouvre le projet sur une autre machine trois jours plus tard.

Un dossier de passation répond à quatre questions, dans cet ordre :

1. Qu'est-ce qui a été fait, et pour corriger quoi ?
2. Qu'est-ce qui est **testé**, qu'est-ce qui ne l'est pas ?
3. Qu'est-ce qui est **en cours** et cassé si on le lance ?
4. Qu'est-ce qui attend une **décision** du user ?

---

## Structure

```
docs/handoff/
├── README.md              ← ce fichier (la procédure, durable)
└── AAAA-MM-JJ/
    ├── SYNTHESE.md        ← vue d'ensemble de la journée, écrite en dernier
    └── session-<sujet>.md ← une fiche par session Claude
```

Une fiche de session contient **toujours** ces six sections, dans cet ordre :

| # | Section | Ce qu'on y met |
|---|---|---|
| 1 | **Sujet** | une phrase |
| 2 | **Ce qui est fait** | les changements réels, avec le *pourquoi* — pas « modifié X » mais ce que ça corrige |
| 3 | **État de compilation / test** | compile ? testé en jeu ? pas testé ? **Être franc ici est ce qui a le plus de valeur** |
| 4 | **Ce qui n'est PAS fini** | le WIP, ce qui est cassé en l'état |
| 5 | **Décisions ouvertes** | ce qui attend un arbitrage |
| 6 | **Fichiers touchés** | chemins exacts, créés / modifiés / supprimés |

---

## 🖥️ Reprendre le travail sur une autre machine

**À faire lire à Claude en début de session sur la nouvelle machine.**

### 1. Se remettre à niveau

```bash
git fetch --prune
git switch main && git pull --ff-only
pnpm install                 # si package.json ou pnpm-lock.yaml a bougé
```

### 2. Lire, dans cet ordre

1. **Le dernier `docs/handoff/*/SYNTHESE.md`** — la vue d'ensemble.
2. Les fiches `session-*.md` du même dossier — le détail.
3. [`docs/ETAT_DES_LIEUX.md`](../ETAT_DES_LIEUX.md) — versions, URLs, artefacts
   publiés. **Ne jamais citer un numéro de version de mémoire** : il vient d'ici
   ou du fichier concerné.
4. [`docs/ROADMAP_BETA_2026.md`](../ROADMAP_BETA_2026.md) — ce qui est engagé.

### 3. Faire l'état des lieux réel

```bash
gh pr list --state open           # ce qui attend un merge
git ls-remote --heads origin      # les branches vivantes
git log --oneline -15             # ce qui est arrivé depuis la dernière fois
git status --short                # du WIP local traîne-t-il ?
```

Comparer avec les fiches : **si une fiche annonce du travail et que
`git log` ne le montre pas, il est resté sur l'autre machine.** C'est le mode de
panne principal — le signaler tout de suite plutôt que de le refaire.

### 4. Trier

Pour chaque élément des fiches, classer :

- **✅ Intégré** — commité, poussé, mergé. Rien à faire.
- **🔄 En attente de merge** — une PR ouverte. Décider de la merger ou non.
- **⏳ WIP poussé** — commité sur une branche mais non fini. Reprendre là.
- **❌ Bloqué sur l'autre machine** — annoncé mais absent du dépôt. **Ne pas le
  refaire** : le récupérer depuis la machine d'origine.
- **🤔 Décision en attente** — poser la question au user, ne pas trancher seul.

### 5. Repartir avec les deux côtés

Avant de revenir sur la machine d'origine, **pousser tout** : c'est la seule
chose qui fait circuler le travail. Puis écrire une nouvelle fiche dans un
nouveau dossier daté, pour que le trajet retour ait la même carte.

---

## Les règles qui rendent ça fiable

1. **Pousser avant de changer de machine.** Une branche locale non poussée
   n'existe pour personne. `git status` propre et `git log origin/<branche>..HEAD`
   vide avant de fermer.
2. **Publier implique commiter.** Un launcher signé, un jar dans un manifest ou
   un plugin uploadé revient sur une branche distante **dans la foulée**. Sinon
   la machine qui a publié devient la seule à pouvoir reproduire la prod — c'est
   arrivé, cf. [`AUDIT_COHERENCE.md`](../AUDIT_COHERENCE.md) §3.
3. **Une branche de feature vit quelques jours, pas trois mois.** Passé un
   certain retard elle devient un piège : `feature/migrate-26.2` avait 10 de ses
   22 commits déjà re-landés sur `main` sous un autre hash, et son merge aurait
   ressuscité 43 fichiers audio supprimés volontairement.
4. **Être franc sur ce qui n'est pas testé.** Une fiche qui dit « ça compile,
   jamais lancé en jeu » vaut dix fois mieux qu'une fiche qui laisse croire.

---

## ⚠️ Plusieurs sessions sur le même working tree

C'est le cas courant sur le PC portable : deux ou trois sessions Claude Code
éditent **le même dossier en même temps**. Conséquences concrètes :

- **Ne jamais faire `git add -A` ni `git add .`** — on ramasse le travail en
  cours des autres sessions, souvent au milieu d'une modification. Stager
  **fichier par fichier**, ou par préfixe de chemin.
- **Un seul commiteur à la fois.** L'index git est partagé : deux commits
  simultanés se marchent dessus. Se coordonner par `SendMessage`, ou désigner
  une session qui commite pour les autres à partir de leur liste de fichiers.
- **`ListAgents`** montre les sessions vivantes de la machine ; `SendMessage`
  permet de leur demander leur récapitulatif avant de consolider.
- Avant de commiter, vérifier que le diff ne contient **que** ce qu'on a écrit :
  `git diff --cached --stat` puis relire la liste.

---

## Créer un nouveau dossier de passation

En fin de journée de travail, ou avant de changer de machine :

```bash
mkdir -p docs/handoff/$(date +%F)
```

Chaque session y écrit sa fiche. La dernière session active écrit la `SYNTHESE.md`
(vue d'ensemble, tableau de l'état de chaque chantier, ce qui attend une
décision), commite l'ensemble et pousse.
