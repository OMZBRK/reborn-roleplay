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
# tsx n'est pas lié au package tant que `pnpm install` n'a pas tourné → passer par
# manifest-signer qui a déjà tsx (le CLI résout ses chemins depuis le fichier) :
cd packages/manifest-signer
pnpm exec tsx ../modrinth-sync/src/cli.ts check      # dry-run : quels mods auto ont un update ?
pnpm exec tsx ../modrinth-sync/src/cli.ts prepare    # construit le manifeste candidat
```

- **`check`** interroge Modrinth pour chaque mod `auto`, compare la dernière version
  compatible au fichier du manifeste live, liste les updates (type release/beta).
  Ne télécharge ni ne publie rien. Slug faux → suggestions via la recherche Modrinth.
- **`prepare`** télécharge les jars à jour, vérifie l'intégrité (sha512 Modrinth),
  calcule sha256 + taille, et écrit `secrets/manifest-candidate-v<next>.json` (URLs =
  CDN Modrinth, vérifiées sha256 côté launcher). N'écrit qu'un fichier local ; ne
  signe pas, ne publie pas.

## Roadmap (slices)

1. ✅ **check** — détection dry-run.
2. ✅ **prepare** — DL + vérif + manifeste candidat (URLs CDN Modrinth) + diff.
3. **publish (1-clic local)** — signe le candidat avec la clé hors-ligne
   (`manifest-signer sign`) + POST `/v1/admin/manifest` (`manifest-uploader`) + notif
   **bot Reborn** (webhook HMAC). Planification (cron/GitHub Actions) pour `check`.
   Option 2b : ré-héberger les jars en release GitHub au lieu du CDN Modrinth si tu
   préfères self-host (swap des URLs avant signature).
