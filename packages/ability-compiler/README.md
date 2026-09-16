# @reborn/ability-compiler

Compilateur des **graphes de techniques** Reborn. C'est le cœur du *Technique
Creator* (Track B). Le graphe (`graph.json`) est la **source de vérité** ; il
**compile** vers des artefacts que les plugins savent déjà lire — aucun
interpréteur de graphe ne tourne côté serveur.

## Principe

> **ShinobiCore décide, le moteur dessine.**

- Les nœuds d'**autorité** (`gate`, `cost`, `cooldown`, `mastery`) → `abilities.yml`
  v2 (lu par `AbilityRegistry` / `Requirement` côté ShinobiCore).
- Les nœuds de **rendu** (`effectExternal` + `particles`/`sound`/`damage`…) →
  sorts **MagicSpells** ou skills **MythicMobs**, via un template par provider.
  Les deux moteurs sont à égalité (choisis par nœud) et passent par le même tuyau
  console (`JutsuExecutionManager.dispatchCommands`) → **le moteur est réversible**.

MagicSpells n'a besoin d'**aucun patch** : il résout toute particule 26.2 via le
registre Bukkit, et gère déjà les particules paramétrées (dust_color_transition,
block, item, trail) + EffectLib (cône/sphère/anneau/hélice).

## Cibles de compilation

| Fichier généré | Consommateur |
|---|---|
| `abilities.generated.yml` | ShinobiCore `AbilityRegistry` (forme liste `abilities:`) |
| `spells-reborn-generated.yml` | MagicSpells (`spells:` → DummySpell + `effects:`) |
| `Reborn_generated.yml` | MythicMobs (skillname → `Skills:`) |

## Usage

```bash
pnpm ability build [graphsDir] [--out <dir>]   # valide + compile + émet
pnpm ability validate [graphsDir]              # valide seulement (DAG + réfs)
pnpm build                                     # tsc (type-check strict)
```

Défauts : `graphsDir = ./graphs`, `out = ./out`. Exemple fourni :
`graphs/katon_gokakyu.graph.json` (dépend de `controle_chakra` → démontre la
validation de dépendances).

## Garanties de validation (ce que le YAML à la main n'avait pas)

- **Schéma Zod** : un champ manquant/mal typé = erreur claire, pas un
  `generic_fallback` silencieux.
- **DAG** : cycle interdit, arête vers un nœud inconnu = erreur.
- **Références croisées** : un prérequis `ability`/`mastery` vers une technique
  inexistante = **erreur de compilation** (répond à « qui dépend de X ? »).

## Prochaines étapes

- B3 : l'éditeur à nœuds (`apps/admin`, React Flow) appelle `compile()` /
  `validateGraph()` en process, puis Save → SFTP (`FilesService`) → `sa reload`
  (déjà whitelisté dans `PanelBridge`).
- B4 : `/sa preview <id>` + bouton « Tester sur moi ».
- Matérialisation à compléter : nœuds `damage`/`status`/`block`/`sequence`/`delay`
  (aujourd'hui reconnus + avertis, pas encore émis).
