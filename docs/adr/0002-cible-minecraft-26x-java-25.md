# ADR 0002 — Cible Minecraft 26.x, Java 25, plus de mappings

**Statut** : Accepté — 2026-08-05, exécuté 2026-08-06 (26.1.2) puis 2026-08-19 (26.2)
**Remplace** : la cible 1.21.1 / Java 21 / Yarn de la conception d'origine
**Documents liés** : [`MIGRATION_26.1.md`](../MIGRATION_26.1.md) (archive),
[`MIGRATION_26.2.md`](../MIGRATION_26.2.md) (courant)

## Contexte

Le serveur **build** tournait déjà en Paper 26.1.2 pendant que le reste de
l'écosystème (launcher, 3 mods clients, 2 plugins, modpack) restait en 1.21.1. Deux
plateformes en parallèle = un modpack impossible à valider et des joueurs qui ne
peuvent pas rejoindre le serveur de référence.

Trois faits ont forcé la décision :

1. **Yarn est discontinué** au-delà de 1.21.11. Depuis 26.x, Mojang livre le client
   **déobfusqué** : les noms officiels sont dans le jar, `version.json` n'a plus de
   `client_mappings`.
2. **Java 25** est requis par la 26.x — saut depuis 21 sur *toutes* les toolchains
   (mods, plugins, serveur).
3. **`DrawContext` / `GuiGraphics` a été supprimé** en 26.x, remplacé par un modèle
   « extraction / retained » (`GuiGraphicsExtractor`). Ce n'est pas un renommage :
   c'est une réécriture de toute la couche UI.

Le supposé fork serveur « Rasengan » a été inspecté : c'est **du Purpur 1.21.1 stock**,
zéro classe custom. Il n'y avait donc rien à rebaser.

## Décision

1. **Cible = Minecraft 26.x**, actuellement **26.2**, sur Purpur/Paper côté serveur.
2. **Java 25** partout. JDK portable dans `D:\dev-cache\jdk25\jdk-25.0.4+7`.
3. **Aucune ligne `mappings(...)`** dans les `build.gradle` — on utilise les noms
   officiels du jar déobfusqué. Corollaire : **plus de `modImplementation`**, supprimé
   par le Loom déobfusqué ; `implementation` partout.
4. **`build.gradle` en Groovy**, pas en Kotlin DSL, pour les mods clients.
5. Le serveur dev passe sur **Purpur officiel** (drop-in), pas sur un fork.

## Conséquences

- ✅ Un seul écosystème de version, modpack validable.
- ✅ Les plugins serveur ont survécu au saut **sans modification de code** (26.1 → 26.2 :
  zéro ligne ; 1.21.1 → 26.1 : quelques constantes d'attributs et de sons).
- ⚠️ **Le coût est concentré côté client** : `mod-hud` a dû être réécrit sur le modèle
  extraction (~28 mixins, tous les écrans, tout le HUD).
- ⚠️ **Piège récurrent** : un mixin qui ne résout plus ne se voit pas au build, seulement
  au lancement (crash « Initializing game »). Vérifier la résolution des mixins fait
  partie de la check-list de bump.
- ⚠️ Toute documentation, tout snippet ou toute mémoire antérieurs à août 2026 qui
  parlent de Yarn, de `DrawContext` ou de Java 21 sont **caducs**.

## Alternatives écartées

- **Rester en 1.21.1** : aurait figé le serveur build sur une version morte et interdit
  les mods tiers récents (Sodium 0.9, Iris 1.11, EmoteCraft 3.4).
- **Forker Purpur** : aucun besoin établi, coût de maintenance permanent.
- **Attendre une LTS** : Minecraft n'en a pas.
