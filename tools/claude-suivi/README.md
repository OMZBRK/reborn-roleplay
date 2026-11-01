# Suivi Reborn — panneau Claude Code

Mod Claude Code (hooks de fonction) qui affiche, à côté du chat, où en est le projet et ce qui attend l'utilisateur :

- **le chantier en vedette** : pourcentage pondéré par la durée des étapes, heure à laquelle il sera prêt pour
  l'utilisateur (« Prêt pour toi 3 h 40, dans ~7 h »), étape en cours, une case par étape ;
- **le Game Plan beta** (lu dans `docs/GAMEPLAN.md` §54) : pourcentage, points livrés / en cours / à faire, fin
  projetée au rythme des points livrés depuis le début de la méthode ;
- **À toi** : ce qui attend l'utilisateur, avec les boutons « Fait » et « Copier » ;
- **les autres chantiers** ;
- replié sous « Tout le détail » : les workflows en cours dans toutes les sessions du projet (worktrees compris),
  les décisions en attente de `docs/CHANTIERS.md` §4 et les versions en ligne de `docs/ETAT_DES_LIEUX.md` §2.

Les cartes sont des SVG au kit « nuit / laque / or » (`hooks/visuel.ts`) ; le terminal les affiche en texte.

## Données

`.claude/suivi/etat.json`, local (`.claude/` est ignoré par git) ; son format est rappelé dans la clé `_format` :

- `aToi[]` : `id`, `type` (`commande`, `test`, `decision`, `deploiement`, `validation`, `info`), `titre`, `detail`,
  `commande` (texte copiable), `chantier`, `depuis`, `fait` et `faitLe`.
- `chantiers[]` : `id`, `titre`, `groupe`, `etat` (`en-cours`, `recette`, `a-deployer`, `attente`, `bloque`,
  `a-faire`, `plus-tard`, `fait`), `vedette`, `workflows[]`, `prochaine`, `ensuite`, `branche`, `majLe`, et
  `etapes[]` : `titre`, `etat`, `estimation` (minutes), `toi` (la part de l'utilisateur), `agent` (le label d'un agent
  des workflows listés : l'étape prend son état et sa durée mesurée).

Les sessions Claude tiennent ce fichier à jour à chaque jalon ([`docs/REPRISE_DE_SESSION.md`](../../docs/REPRISE_DE_SESSION.md) §7).
Jamais de secret ni d'adresse de serveur dedans.

## Installation

Chargé dans toutes les sessions (terminal et application de bureau) par `~/.claude/settings.json` :

```json
"env": {
  "CLAUDE_CODE_PLUGIN_DIRS": "<dossier du dépôt>\\tools\\claude-suivi",
  "CLAUDE_CODE_PLUGIN_DIR_WATCH": "1"
}
```

Le panneau s'ouvre au démarrage d'une session ; `/suivi` le rouvre. Avec `CLAUDE_CODE_PLUGIN_DIR_WATCH`, une
modification du mod se recharge d'elle-même dans les sessions ouvertes.

## Développer

```pwsh
claude plugin validate tools/claude-suivi
claude plugin test tools/claude-suivi
```

`tsc -p tools/claude-suivi` fonctionne une fois que Claude Code a posé ses types dans `.claude-plugin/types/`
(ignoré par git) : `tsconfig.json` les étend.
