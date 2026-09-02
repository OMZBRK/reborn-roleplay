# @reborn/modrinth-sync

Auto-update **semi-automatique** des mods tiers du modpack Reborn depuis Modrinth.
Conçu autour de 3 garde-fous (décisions user) :

- **Validation 1-clic** — rien n'est publié sans ton feu vert.
- **Clé de signature hors-ligne** — la préparation est automatisable, mais la
  signature + publication du manifeste se font là où vit la clé privée.
- **Mods fragiles épinglés** — Axiom, emotecraft, PlayerAnimationLib, BendableCuboids
  ne s'auto-updatent jamais (`policy: pinned`). Les optimiseurs / shaders / QoL sont
  en `policy: auto` → dernière version compatible **MC 26.2 + Fabric**.

La purge des anciennes versions est déjà gérée par le launcher (il supprime tout
jar absent du nouveau manifeste) — pas besoin de code ici pour ça.

## Mapping

`mods.config.json` : `{ prefix, slug, policy }` par mod. `prefix` = début du nom de
fichier dans le manifeste ; `slug` = projet Modrinth.

## Commandes

```bash
cd packages/modrinth-sync
pnpm exec tsx src/cli.ts check            # dry-run : quels mods auto ont un update ?
pnpm exec tsx src/cli.ts check --manifest ../../secrets/manifest-signed-v3.1.85.json
```

`check` interroge Modrinth pour chaque mod `auto`, compare la dernière version
compatible au fichier du manifeste live, et liste les updates. **Ne télécharge ni
ne publie rien.** Si un `slug` est faux, il propose des suggestions via la recherche
Modrinth.

## Roadmap (slices)

1. ✅ **check** — détection dry-run (ce qui est ici).
2. **prepare** — télécharge les jars à jour, vérifie la compat (dépendances Modrinth,
   game_versions), ré-héberge en release GitHub, construit un **manifeste candidat**
   non-signé + un diff lisible, notifie Discord.
3. **publish (1-clic local)** — signe le candidat avec la clé hors-ligne + POST
   `/v1/admin/manifest`. Planification (cron/GitHub Actions) pour la détection.
