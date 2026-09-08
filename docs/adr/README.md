# Architecture Decision Records

Une décision structurante = un fichier. On n'édite pas un ADR accepté : on en écrit un
nouveau qui le remplace, et on met à jour le statut de l'ancien.

**Statuts** : `Proposé` · `Accepté` · `Remplacé par 000X` · `Résolu` (pour les ADR qui
documentaient un blocage externe levé depuis).

| # | Titre | Statut |
|---|---|---|
| [0001](./0001-microsoft-app-approval-required.md) | Approbation Microsoft du Client ID pour Minecraft Services | Résolu (2026-05-16) |
| [0002](./0002-cible-minecraft-26x-java-25.md) | Cible Minecraft 26.x, Java 25, plus de mappings | Accepté (2026-08-05) |
| [0003](./0003-shinobi-dans-le-monorepo-deux-trains-de-release.md) | Shinobi dans le monorepo, deux trains de release | Accepté (2026-08-19) |
| [0004](./0004-abandon-mcef-fond-menu-3d.md) | Abandon de MCEF pour le fond du menu principal | Accepté (2026-08-08) |
| [0005](./0005-pipeline-assets-3d-nexo-panel.md) | Pipeline d'assets 3D : Blockbench → Nexo → panel staff | Accepté (2026-08-28) |
| [0006](./0006-monnaies-ryo-et-rbcoins.md) | Deux monnaies : Ryo et RBCoins (retrait de « ZK Coin ») | **Proposé** — à valider |

## Quand écrire un ADR

Quand la réponse à « pourquoi c'est fait comme ça ? » n'est pas évidente à la lecture
du code, et que se tromper coûterait cher : choix de plateforme, abandon d'une
techno, séparation d'un pipeline, modèle de sécurité, règle produit non négociable.

Pas d'ADR pour un choix de librairie sans conséquence, un refactor local, ou une
convention déjà couverte par `CLAUDE.md`.
