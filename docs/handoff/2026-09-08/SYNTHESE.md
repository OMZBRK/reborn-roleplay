# Synthèse — journée du 2026-09-08

> **Trois sessions Claude Code en parallèle sur le PC portable**, même working tree.
> Ce fichier est la vue d'ensemble ; le détail est dans les trois fiches à côté.
>
> **À lire en premier depuis une autre machine.** La procédure de reprise est dans
> [`../README.md`](../README.md).

---

## En une page

| Session | Sujet | Code touché | Où c'est |
|---|---|---|---|
| [audit documentaire](./session-audit-documentaire.md) | Cohérence docs/Trello/Miro, branches GitHub, portabilité Mac, Creator Tools Blockbench | docs + `tools/` + `.gitattributes` | ✅ `main` (PR #8, #9, #10, #12) · 🔄 PR #11, #13 ouvertes |
| [correctifs retours staff](./session-correctifs-retours-staff.md) | 5 remontées du test staff : F1 cinéma, free-look, tête invisible en réseau, Naruto Run, éditeur HUD | **19 fichiers Java/YAML** | 🔄 **PR #14 ouverte — NON COMPILÉE** |
| [audit techniques](./session-audit-techniques-magicspells.md) | Audit des 6 plugins Shinobi + pont MagicSpells + particules, spec du Technique Creator | aucun (document seul) | ✅ `main` |

**Le fait le plus important de la journée** : la production n'était sur aucune
branche partagée. Le launcher **0.3.42** tournait chez les staffs depuis le
2 septembre alors que `main` était à 0.3.40. C'est réparé pour le launcher, **pas
encore pour `reborn-hud` 0.4.134** — cf. §4.

---

## 1. Ce qui est sur `main` et utilisable

**Documentation** — le dépôt décrivait encore Minecraft 1.21.1 et Java 21, soit
**deux migrations de retard** sur la production (26.2 / Java 25), et ignorait les
6 plugins Shinobi. Corrigé, avec quatre documents neufs qui font désormais foi :
`ETAT_DES_LIEUX.md` (le présent), `ROADMAP_BETA_2026.md` (le futur),
`MIGRATION_26.2.md` (la migration courante, qui n'était documentée nulle part) et
`AUDIT_COHERENCE.md` (les écarts relevés). Plus 5 ADR et
`AUDIT_TECHNIQUES_ET_CREATOR.md`.

**Branches** — `main` contient maintenant le launcher 0.3.41 + 0.3.42 et le mode
« build » serveur, tous deux déjà en production mais absents du dépôt.
`feature/migrate-26.2` a été archivée en `archive/migrate-26.2-wip` : son merge
aurait ressuscité 43 fichiers audio supprimés volontairement et 10 de ses 22
commits étaient déjà sur `main` sous un autre hash.

**Portabilité** — les 5 `gradlew` étaient en mode `100644`, donc `Permission
denied` sur Mac après chaque clone. `.gitattributes` ajouté. Un jar décompressé
(289 `.class` + 253 assets dupliqués + un second `fabric.mod.json`) retiré de
`minecraft/mod-hud/`.

**Trello + Miro** réalignés sur les mêmes chiffres que la roadmap.

## 2. Ce qui attend un merge

| PR | Contenu | Risque | Prérequis |
|---|---|---|---|
| **#11** | `modrinth-sync` — suivi auto des MAJ de mods | faible | `pnpm install` (ajoute `@nestjs/schedule`) puis `docker compose … up -d --build api bot` |
| **#13** | Creator Tools Blockbench | faible | aucun — 55 tests, typecheck et build verts. Jamais chargé dans Blockbench |
| **#14** | **Correctifs des retours staff** | 🔴 **élevé** | **doit être compilé et testé en jeu avant merge** |

### ⚠️ La PR #14 mérite qu'on s'y arrête

Dix-neuf fichiers Java/YAML, cinq bugs traités de bout en bout — dont un **vrai
bug réseau** : le client ne posait que `setYHeadRot` à l'arrêt, or seul le couple
`(yRot, xRot)` part sur le réseau, donc **la rotation de tête était invisible pour
les autres joueurs**. Et le Naruto Run n'a jamais fonctionné en serveur parce que
le mod émettait sur `reborn:naruto` pendant que le plugin écoutait `reborn:run` —
tout le code serveur tournait à vide.

**Mais rien n'a été compilé ni testé** : voir §3. La checklist de test est en fin
de fiche `session-correctifs-retours-staff.md` — cinq scénarios précis, dont un
test à deux joueurs pour la caméra.

## 3. 🔧 Pourquoi rien n'est compilé — contrainte de ce poste

**Le PC portable n'a ni JDK 25 ni Maven.** `java` y est en 17 ou 23 selon le
shell, `mvn` est absent du PATH, et le JDK portable `D:\dev-cache\jdk25\jdk-25.0.4+7`
annoncé dans `CLAUDE.md` **n'existe pas sur cette machine** (il n'y a pas de
disque `D:`). Le cache Loom du projet est resté en 1.21.1 / Yarn.

Conséquence directe et à assumer : **aucun mod Fabric ni plugin Shinobi n'est
compilable depuis ce poste**, donc rien de la PR #14 n'a pu être validé, ni par
une session ni par une autre. Ce qui a été fait à la place : une passe `javac`
sur les fichiers touchés en filtrant les erreurs de symboles inévitables — elle
prouve la syntaxe, **rien de plus**.

Rappel de `MIGRATION_26.2.md` : **les mixins échouent au lancement, pas au
build.** Un `./gradlew build` vert ne suffit pas, il faut un `runClient`.

## 4. 🔴 Ce qui reste bloqué sur une autre machine

**`reborn-hud` 0.4.134 est en production** (release `mods-v3.1.90`, 4 septembre)
et ne vit **sur aucune branche distante**. Son source n'est pas sur le PC
portable : pas de worktree, pas de disque `D:`. Il est sur la machine qui a
publié.

Tant que ce n'est pas commité depuis cette machine-là, `main` ne reproduit pas la
production, et la PR #14 se construit sur une base (`0.4.133`) qui n'est pas celle
que les joueurs exécutent.

**C'est la première chose à faire en arrivant sur le PC principal.**

## 5. Décisions qui attendent le user

Consolidées depuis les trois sessions, par ordre d'urgence.

| # | Décision | Échéance | Fiche |
|---|---|---|---|
| 1 | **Quel moteur d'effet pour les techniques ?** MagicSpells n'est pas installé et n'a peut-être pas de build 26.2 ; MythicMobs l'est déjà. La recommandation de l'audit est de ne pas installer MagicSpells. | **avant le sprint du 9 octobre** (12 sorts taïjutsu) | techniques |
| 2 | **Monnaies** — Ryo (gagné) + RBCoins (acheté / bug bounty), sans conversion, et retrait de « ZK Coin », vestige Zenkai sur le produit payant. ADR 0006, statut *proposé*. | avant l'implémentation de la boutique (v1.1) | audit doc |
| 3 | **`refimage.png` : capture du problème ou maquette cible ?** L'éditeur HUD a été corrigé en supposant « capture de l'état actuel ». Si une refonte visuelle était visée, le travail est à reprendre. | avant de valider la PR #14 | staff |
| 4 | **Sorts claniques : 18 ou 20 ?** Tranché à 18 (9 clans × 2), Neutre et Autre n'ayant pas de kit. Si « Autre » doit en recevoir un, les boards sont à réaligner. | avant le Sprint 3 | audit doc |
| 5 | **Va-t-on jusqu'à l'éditeur de techniques graphique**, ou s'arrête-t-on au compilateur CLI ? 3-5 semaines à peser contre 57 techniques dues d'ici le 3 décembre. | — | techniques |
| 6 | **Les 204 stubs d'abilities** — les sortir dans `abilities-debug.yml` ou les supprimer ? Ils faussent `/sa importspells` et rendent les diffs illisibles. | — | techniques |
| 7 | Réglages à doser en jeu : cooldown Naruto Run (30 s), `step-height-bonus` (1.0), `speed-multiplier` (1.6, jamais ressenti puisque le canal était cassé), largeur du panneau éditeur (148 px). | au premier test | staff |

## 6. Deux quick wins repérés, non faits

- **`PanelBridge.ALLOWED_PREFIXES`** n'autorise pas `sa reload` : le panel peut
  recharger MagicSpells mais pas le registre de techniques. Deux lignes.
- **`Effects.forwardCone`** envoie **un paquet réseau par particule** — 156 par
  cast de Gōkakyū, diffusés à tous les spectateurs. À batcher.

---

## 7. Reprendre demain, dans l'ordre

1. **Sur le PC principal** — commiter `reborn-hud` 0.4.134 et pousser. C'est le
   bloqueur (§4).
2. `git fetch --prune && git switch main && git pull --ff-only && pnpm install`
3. Lire cette synthèse, puis les trois fiches.
4. **Compiler et tester la PR #14** avec un vrai JDK 25 — checklist en fin de
   fiche `session-correctifs-retours-staff.md`. Ne pas merger avant.
5. Trancher les décisions §5, au moins la n°1 (échéance 9 octobre).
6. Merger #11 et #13 si le déploiement est prêt.
7. Avant de revenir sur le portable : **tout pousser**, et écrire un nouveau
   dossier `docs/handoff/<date>/` pour le trajet retour.

---

## 8. Note de méthode

Trois sessions ont travaillé sur **le même working tree** toute la journée. Un
`git add -A` a ramassé le travail des autres une fois — défait sans perte, mais
c'est le mode de panne à connaître. La règle qui en découle est dans
[`../README.md`](../README.md) : **jamais `git add -A` à plusieurs**, stager
fichier par fichier, un seul commiteur à la fois. `ListAgents` et `SendMessage`
servent à se coordonner ; c'est comme ça que cette synthèse a été produite.
