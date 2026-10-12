# Reprendre une session de travail

> **À lire par Claude au début de chaque session** (et par toute personne qui reprend le projet). Objectif :
> savoir en 10 minutes où on en est, comment l'utilisateur travaille et quelles règles sont non négociables,
> **sans qu'il ait à le répéter**. Mis à jour le 2026-10-12.

---

## 1. Le projet en trois phrases

**Reborn Roleplay** est un serveur Minecraft de roleplay **Naruto** (MC 26.2, Java 25), avec son launcher, son
API, son panel staff, son bot Discord, un mod client qui porte toute l'interface (`reborn-hud`) et des plugins
serveur qui portent le gameplay (les six `Shinobi*`). L'objectif est une **beta jouable le 31 décembre 2026** :
« créer un perso → entrer dans Konoha → combattre → apprendre des techniques → mourir / renaître → jouer RP ».
L'utilisateur (Omar) dirige le projet, conçoit la DA, teste en jeu et décide ; le staff du serveur suit
l'avancement sur Trello et Miro.

## 2. Lire, dans cet ordre (10 minutes)

1. [`CHANTIERS.md`](./CHANTIERS.md) **§0 et §4** — où on en est, ce qui attend une décision.
2. [`ETAT_DES_LIEUX.md`](./ETAT_DES_LIEUX.md) — versions en ligne, serveurs, artefacts. **Ne jamais citer une
   version de mémoire** : elle vient d'ici ou du fichier concerné (`gradle.properties`, `tauri.conf.json`).
3. La **fiche du système** sur lequel on va travailler, dans [`gameplay/`](./gameplay/) ou
   [`technique/`](./technique/).
4. [`pilotage/DECISIONS.md`](./pilotage/DECISIONS.md) pour ce domaine — pour ne pas rouvrir une question tranchée.
5. Au besoin : [`README.md`](./README.md) (carte de toute la doc).

## 3. Vérifier la réalité avant d'agir

```bash
git status --short          # du travail non commité traîne-t-il ? à qui est-il ?
git log --oneline -15       # qu'est-ce qui est arrivé depuis la dernière mise à jour des docs ?
```

- Si un document annonce du travail que `git log` ne montre pas, **le dire** : il est resté sur une autre machine
  ou dans une autre session. Ne pas le refaire.
- **Plusieurs sessions Claude partagent souvent le même dossier.** Fichiers modifiés par d'autres sessions à **ne
  pas toucher ni commiter** sans demande (état au 12/10) : `tools/blockbench-handpaint/**`, `tools/bbforge/**`,
  `minecraft/shinobi/ShinobiSense/**`, `scripts/publish-mod-manifest.ps1`.

## 4. Comment l'utilisateur travaille

- **Réponses courtes, en français, sans introduction.** Il répond souvent « ok », « go ».
- **Pas de questions de clarification** sauf vraie ambiguïté : prendre l'option évidente, la dire, avancer. Il
  redirige si besoin.
- Il **teste lui-même en jeu** et revient avec des retours concrets (« c'est moche », « trop gros »). Après chaque
  chantier : une **liste « À tester » concrète** (action précise → résultat attendu).
- **Maquettes avant le code** pour tout ce qui est visuel ; il aime voir plusieurs variantes.
- **Exigence très forte sur la DA** : lire [`design/DIRECTION_ARTISTIQUE.md`](./design/DIRECTION_ARTISTIQUE.md)
  avant de dessiner quoi que ce soit (ce qui a été rejeté y est listé).
- Il apprécie les idées de polish en plus de la demande, mais **refuse les refontes non demandées**.
- Il veut **finir le Sprint 1 avant d'ouvrir le Sprint 2**.
- Il dessine lui-même certaines choses (icônes) : lui dire **lesquelles** et à quel format.

## 5. Règles non négociables

| # | Règle | Pourquoi |
|---|---|---|
| 1 | **Ne jamais pousser sur GitHub.** Commiter seulement si demandé ; les publications et le push passent par un **prompt** que l'utilisateur colle dans sa session terminal | Il veut que l'identité GitHub soit la sienne |
| 2 | **Montrer les visuels avant tout prompt de publication** (captures du client de dev, cf. [`technique/MOD_HUD.md`](./technique/MOD_HUD.md) §5) | Plusieurs visuels jugés moches après coup |
| 3 | **Valider `plugin.yml` et `config.yml` avec un parseur YAML** avant de publier un plugin | Un « : » non quoté a cassé tout le serveur dev |
| 4 | **Stager fichier par fichier**, jamais `git add -A` | Sessions parallèles dans le même dossier |
| 5 | **Deux trains jamais mélangés** (jeu / web) ; un commit `docs(etat)` séparé | — |
| 6 | **Publier implique commiter** : rien ne tourne en prod ou sur le dev sans être sur `origin/main` | Prod irreproductible déjà arrivée |
| 7 | **Aucun secret** (mot de passe, token, IP de serveur) dans un fichier, une doc, une mémoire ou sur le **Trello public** | Dépôt et Trello publics |
| 8 | **Le redémarrage du serveur est fait par l'utilisateur** ; ne jamais toucher aux `config.yml` du serveur sans demande | — |
| 9 | **Mettre la doc à jour** en fin de chantier (§7) | C'est ce qui rend la reprise possible |

## 6. Outils et commandes de ce poste

| Quoi | Où / comment |
|---|---|
| JDK 25 | `C:\Users\omarb\dev-cache\jdk-25.0.4.1+1` (le chemin `D:\dev-cache` de vieilles docs n'existe pas) |
| Maven | `C:\Users\omarb\dev-cache\apache-maven-3.9.11\bin\mvn` |
| Plugins Shinobi | `cd minecraft/shinobi && mvn -o -q clean package -pl ShinobiAbilities -am` (le `-am` est obligatoire) → `*/target/Shinobi*-1.0.0-SNAPSHOT.jar` (pas `original-*`) |
| Mod | `cd minecraft/mod-hud && ./gradlew compileJava --offline -q` ; client de dev + bancs de captures : [`technique/MOD_HUD.md`](./technique/MOD_HUD.md) §5 |
| Serveur dev | SFTP, mot de passe dans le gestionnaire d'identification Windows (`reborn-sftp-dev`, lu avec `keyring` en Python) ; logs `logs/latest.log` — [`operations/PUBLICATION.md`](./operations/PUBLICATION.md) §3 |
| Python | `pip install --target <scratchpad>/pylib …` pour paramiko, keyring, pyyaml ; écrire les scripts dans un fichier (les heredocs longs cassent) |
| Pas de serveur Paper 26.2 local | Le code serveur ne se teste qu'après déploiement sur le dev |

## 7. En fin de chantier ou de session

1. **`CHANTIERS.md`** : mettre à jour la ligne du chantier (état, reste, décisions en attente) et le §0.
2. **`pilotage/DECISIONS.md`** : une ligne par décision prise par l'utilisateur (barrer celle qu'elle remplace).
3. **La fiche du système** (`gameplay/…`, `technique/…`) : ce qui est codé, réglages, commandes.
4. Après une publication : **`ETAT_DES_LIEUX.md`** (commit `docs(etat)`) et une ligne dans
   **`pilotage/JOURNAL_DES_LIVRAISONS.md`**.
5. **Boards** (Trello public / Miro) : cf. [`pilotage/BOARDS.md`](./pilotage/BOARDS.md).
6. Donner à l'utilisateur la **liste « À tester »**.

## 8. Prompts prêts à coller

**Reprise** (début de session, ou sur une autre machine) :

```text
Reprends le projet. Lis docs/REPRISE_DE_SESSION.md puis applique-le : lis CHANTIERS.md (§0, §4),
ETAT_DES_LIEUX.md, la fiche du système concerné et DECISIONS.md. Vérifie git status et git log -15,
signale tout écart entre les docs et le dépôt, puis dis-moi en quelques lignes où on en est et ce que tu
proposes de faire ensuite. Ne pousse rien.
```

**Clôture** (fin de session) :

```text
On s'arrête. Mets à jour CHANTIERS.md, DECISIONS.md et les fiches touchées selon REPRISE_DE_SESSION.md §7,
puis prépare-moi le prompt de commit (fichiers exacts, message Conventional Commits) pour ma session terminal.
```

> L'ancienne convention de dossiers de passation datés (`docs/archives/handoff/`) est remplacée par la mise à
> jour continue de `CHANTIERS.md`. Les dossiers du 8 et du 17 septembre restent en archive.
