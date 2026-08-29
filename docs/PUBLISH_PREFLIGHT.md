# Pré-vol publication (mods & plugins) — checklist anti-régression

Codifie les pièges qui ont coûté du temps. À lire **avant** tout build/deploy/publish
game-side. Complète `docs/RELEASING.md` (procédure) et le skill `reborn-ops`.

## 0. Concordance des versions — se mettre sur les DERNIÈRES

Le code game-side vit dans le worktree `emote-bend-test`, souvent avec **beaucoup de
modifs non commitées** (le vrai « dernier état »). Le tip *commité* peut être en
retard de tout un sprint.

**Avant de build pour publier :**

1. `git -C .claude/worktrees/emote-bend-test status --short` — s'il y a du WIP,
   c'est probablement le dernier état voulu. Ne PAS publier depuis un tip commité
   périmé (règle d'or reborn-ops n°1).
2. Comparer les versions et ne jamais régresser :
   ```
   for wt in .claude/worktrees/*/; do
     grep '"version"' "$wt/apps/launcher/src-tauri/tauri.conf.json" 2>/dev/null
     grep mod_version "$wt/minecraft/mod-hud/gradle.properties" 2>/dev/null
   done
   ```
3. Vérifier la version **live** réellement publiée, pas seulement les fichiers
   `secrets/` (qui peuvent être périmés) : dernier `secrets/manifest-signed-v*.json`
   du dépôt **principal** = base ; l'entrée `files[]` du mod donne la version live
   (ex. `reborn-hud-0.4.110.jar`).
4. Si tu publies par-dessus du WIP : **le committer d'abord** (checkpoint propre),
   rebuild, et **rebaser** la branche feature dessus — pas publier un mélange
   worktree sale.

## 1. Sanity du POIDS du jar (LE piège de cette session)

Un mod qui **quadruple de taille** = quasi toujours des assets qui ne devraient
pas y être. Cas réel : 74 Mo de `.ogg` OST + un HDRI de 17 Mo s'étaient glissés
dans `reborn-hud` (21 Mo → 94 Mo). L'OST vit **uniquement dans `reborn-ost`**, pas
dans `reborn-hud`.

**Avant de publier un mod, comparer sa taille à la version live :**
```
python - <<'PY'
import zipfile,collections
z=zipfile.ZipFile("build/libs/reborn-hud-<ver>.jar")
g=collections.Counter()
for e in z.infolist():
    p="/".join(e.filename.split("/")[:3]); g[p]+=e.file_size
for k,v in g.most_common(10): print(f"{v/1e6:6.1f}MB {k}")
PY
```
Si le jar est très au-dessus du live (`files[].size` du manifest), **stop** :
trouver l'asset intrus. Ne jamais forcer +Xo à tous les joueurs en auto-update
sans comprendre d'où ça vient.

Règle mémo : `reborn-hud` ≈ 20 Mo (UI + dynamic-player). Pas d'OST audio dedans.

## 2. Deux trains de release DISTINCTS

- **Jeu** (mods clients + plugins Paper) : publié via le **manifest signé** (auto-update
  launcher) + **SFTP** pour les plugins. Base = worktree `emote-bend-test`. Ne
  déploie **PAS** vers `main`.
- **API / Panel** (`apps/api`, `apps/admin`) : déployé via **Docker sur le VPS** depuis
  `main`. Le module `apps/api/src/files/` (scopes panel) vit sur `origin/main`, **pas**
  dans le worktree game-side → un changement de scope se fait sur une branche basée
  sur `origin/main`, PR séparée, deploy `docker compose … up -d --build api admin`.

Ne jamais mélanger les deux dans un même commit/PR : ils ne partent pas au même
endroit ni au même moment.

## 3. SFTP plugin (Paper) — recette sûre

- Creds serveur **éphémères**, passés en **env inline** (jamais écrits dans un fichier) :
  `SFTP_HOST/PORT/USER/PWD` + paramiko.
- Toujours : localiser le jar existant, `remove` l'ancien `.bak`, `rename` l'actuel en
  `.bak`, `put` le nouveau, **vérifier `st_size` local == remote**.
- **RESTART du serveur requis** (Mystrator) — pas de hot-load pour un plugin Paper.
  (Nexo : pas de restart, `/nexo reload` suffit.)
- Ne jamais éditer un `config.yml`/`plugin.yml` d'un plugin **à chaud** sur le disque
  serveur (réécrit au shutdown) — passer par une section stable ou le panel hôte.

## 4. Publier un mod (résumé — détails dans reborn-ops §4)

1. Concordance versions (§0) + build **propre** (`clean`) depuis le bon état.
2. **Sanity poids** (§1) : diff vs live.
3. `sha256sum` + `stat -c %s` du jar.
4. Base = dernier `secrets/manifest-signed-v*.json` (principal) → bump `version` +
   `issuedAt`/`expiresAt`, swap l'**unique** entrée `files[]` du mod (path/sha256/size/url
   → `mods-v<X>/<jar>`), `pop('signature')`. **Assert : 1 seule entrée changée.**
5. Signer (`manifest-signer`) + `verify`.
6. `gh release create mods-v<X> <jar>` → **curl l'URL = HTTP 200 + sha256 identique**.
7. POST `manifest-uploader.exe manifest --file <signed.json>` → **HTTP 201**.
8. Ne jamais POST 2× la même version. Feu vert user par version (action sortante).
