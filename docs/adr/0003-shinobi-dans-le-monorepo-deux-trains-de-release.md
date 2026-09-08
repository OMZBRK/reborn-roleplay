# ADR 0003 — Shinobi dans le monorepo, et deux trains de release distincts

**Statut** : Accepté — 2026-08-19 (import Shinobi), formalisé 2026-09-08
**Documents liés** : [`PUBLISH_PREFLIGHT.md`](../PUBLISH_PREFLIGHT.md),
[`ETAT_DES_LIEUX.md §6`](../ETAT_DES_LIEUX.md)

## Contexte

Les plugins **Shinobi** (Core, Abilities, Combat, Learning, Sense, Tail) vivaient dans
un dépôt Maven séparé, `ShinobiReborn`. Or ce sont eux qui portent le gameplay :
personnages, chakra, techniques, KO, progression, mobilité, inventaire RP, emotes. La
moitié des features du projet demandent une modification **simultanée** de `mod-hud`
(client) et de `ShinobiCore` (serveur) — un canal de plugin messaging à chaque fois.

Travailler dans deux dépôts pour un seul changement fonctionnel générait des paires
de commits désynchronisées et des builds incompatibles.

Par ailleurs, le monorepo contient deux natures d'artefacts qui ne se déploient **ni
au même endroit ni au même moment** :

- le **jeu** : mods clients (auto-update via le manifest signé) + plugins Paper (upload
  manuel sur le panel Minestrator, restart serveur obligatoire) ;
- le **web** : `apps/api`, `apps/admin`, `apps/bot`, déployés en Docker sur le VPS
  depuis `main`.

## Décision

1. **`minecraft/shinobi/` fait autorité.** Les 6 modules Maven sont dans ce monorepo,
   avec un POM agrégateur en racine (`mvn clean package` compile Core puis les autres
   en un seul reactor). Le dépôt `ShinobiReborn` d'origine reste comme historique.
2. **Deux trains de release, jamais mélangés dans un même commit ou une même PR.**
   Un changement de scope du panel ne part pas avec un changement de gameplay.
3. Le train *jeu* se base sur le worktree game-side ; le train *web* se base sur
   `origin/main`.

## Conséquences

- ✅ Un changement client+serveur = un commit, une revue, un état cohérent.
- ✅ `mvn clean package` à la racine de `minecraft/shinobi/` suffit.
- ⚠️ **Effet de bord observé et à corriger** : la séparation des trains a dérivé en
  « l'état publié ne revient jamais sur `main` ». Au 2026-09-08, le launcher 0.3.42 et
  `reborn-hud` 0.4.134 sont en production sans être sur `main`.
  **Règle ajoutée** : *publier implique commiter et pousser sur une branche partagée,
  dans la foulée.* Un artefact en production qui n'est pas reproductible depuis une
  branche distante est un incident, pas une méthode de travail.
- ⚠️ Deux écosystèmes de build cohabitent (Gradle pour les mods, Maven pour Shinobi).
  C'est assumé : ce sont deux mondes, les forcer sous un seul outil coûterait plus cher
  que la friction actuelle.
- ⚠️ Les plugins Paper n'ont **pas de hot-reload** : tout déploiement plugin = restart
  serveur. Le déploiement se fait à la main via le panel web de l'hébergeur.
