# Travailler depuis plusieurs postes

> Le dépôt est travaillé depuis **trois machines** : ce PC, le PC principal, et un Mac.
> Ce document dit ce qu'il faut pour qu'une machine puisse reprendre le travail d'une
> autre sans surprise — et surtout ce qui **n'est pas dans git** et doit voyager
> autrement.
>
> Écrit le 2026-09-08, après la passe d'assainissement des branches.

---

## 1. Les trois règles

1. **Pousser avant de changer de machine.** Une branche locale non poussée n'existe
   pour personne d'autre. `git status` doit être propre et `git log origin/<branche>..HEAD`
   vide avant de fermer le PC.
2. **Publier implique commiter.** Un launcher signé, un jar de mod dans un manifest ou
   un plugin uploadé doit revenir sur une branche distante **dans la foulée**. Sinon la
   machine qui a publié devient la seule à pouvoir reproduire la prod — c'est arrivé
   avec `reborn-hud 0.4.134` (cf. [`AUDIT_COHERENCE.md`](./AUDIT_COHERENCE.md) §3).
3. **Rebase avant de commencer.** `git pull --rebase` en début de session : les trois
   postes divergent vite, et un merge à 100 commits de retard finit en champ de mines
   (cf. le cas `feature/migrate-26.2`, §6).

---

## 2. Ce qui n'est PAS dans git

Volontairement — mais il faut donc le transférer à la main sur chaque nouvelle machine.

| Chemin | Contenu | Si absent |
|---|---|---|
| `.env` | secrets de dev (MS Client ID, Discord, HMAC, play-token, pubkey manifest) | l'API démarre mal, le launcher dit « Client ID Microsoft manquant » |
| `secrets/manifest_ed25519_private.pem` | clé de signature du manifest | impossible de publier un modpack |
| `secrets/tauri-updater.key` (+ `.pub`) | clé de signature de l'auto-update | impossible de publier un launcher |
| `secrets/manifest_ed25519_public.hex` | pubkey bakée dans le build | build launcher inutilisable |
| `.claude/` | état local de Claude Code, worktrees | rien de grave, c'est par machine |

**Transfert** : gestionnaire de mots de passe ou disque chiffré. Jamais par Discord,
jamais par mail, jamais dans un commit. La liste complète des secrets critiques et de
ce qu'on perd en les perdant est dans [`MAINTENANCE.md`](./MAINTENANCE.md) §12.

> ⚠️ **Les deux clés de signature n'ont pas de sauvegarde côté serveur.** Les perdre,
> c'est devoir redistribuer un launcher à la main à tous les staffs. Elles doivent
> exister sur **au moins deux supports** avant d'être considérées comme en sécurité.

---

## 3. Bootstrap d'une machine neuve

### Commun

```bash
git clone https://github.com/OMZBRK/reborn-roleplay.git
cd reborn-roleplay
pnpm install
# puis copier .env à la racine et le contenu de secrets/
```

### Windows

- Node 20+, pnpm 10+, Docker Desktop
- Rust via [rustup](https://rustup.rs/) — toolchain `stable-x86_64-pc-windows-msvc`
- **Build Tools Visual Studio 2022** avec la charge de travail C++ (sans elle, `cargo`
  échoue au link sur des erreurs peu lisibles)
- JDK 25 — portable dans `D:\dev-cache\jdk25\jdk-25.0.4+7` sur les postes actuels

### macOS

- Node 20+, pnpm 10+, Docker Desktop
- Rust via rustup — toolchain `stable-aarch64-apple-darwin` (Apple Silicon)
- **Xcode Command Line Tools** : `xcode-select --install`
- JDK 25 : `brew install --cask temurin@25`, puis
  `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`
- Maven : `brew install maven`

**Spécificités Mac à connaître :**

- `JAVA_HOME` n'est **pas** `D:\dev-cache\...`. Les commandes des docs sont écrites pour
  Windows ; sur Mac, remplacer par `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`.
- Les `gradlew` portent désormais le **bit exécutable** dans git (mode `100755`). Ils ne
  l'avaient pas : `./gradlew build` répondait `Permission denied` sur Mac. Si ça revient
  un jour : `git update-index --chmod=+x minecraft/*/gradlew`.
- **`scripts/*.ps1` sont du PowerShell.** Sur Mac, installer `pwsh`
  (`brew install --cask powershell`) ou faire les étapes à la main — elles sont détaillées
  dans [`RELEASING.md`](./RELEASING.md) et [`AUTO_UPDATE_TEST_PROTOCOL.md`](./AUTO_UPDATE_TEST_PROTOCOL.md).
- **Le launcher se build en `.app`/`.dmg`, pas en `.exe`.** Le NSIS est Windows-only.
  Un build macOS non signé déclenche Gatekeeper : `xattr -dr com.apple.quarantine` pour
  tester en local. Une vraie distribution Mac demande un compte Apple Developer —
  hors périmètre beta.
- Le fix `-XstartOnFirstThread` (PR #7) est **indispensable** sur Mac : sans lui, GLFW
  crashe à l'initialisation du rendu. Il est sur `main`.

---

## 4. Fins de ligne

`.gitattributes` impose **LF partout** dans le dépôt et dans les copies de travail,
sauf `.bat` / `.cmd` / `.ps1` qui restent en CRLF.

Sans ça, chaque poste applique son propre `core.autocrlf` et on obtient des diffs
fantômes où un fichier entier apparaît modifié alors que seule la fin de ligne a changé —
et des conflits de merge insolubles entre Windows et Mac.

Rien à configurer par machine : le fichier `.gitattributes` gagne toujours sur la config
locale. Si un doute : `git ls-files --eol` doit montrer `i/lf` sur tous les fichiers texte.

---

## 5. Ce qui est machine-local et ne voyage pas

- **Les worktrees.** `docs/PUBLISH_PREFLIGHT.md` §0 parle d'un worktree game-side
  (`.claude/worktrees/emote-bend-test`). Il n'existe **que sur la machine qui l'a créé**,
  `.claude/` étant ignoré par git. Sur une autre machine, cette instruction est sans
  objet — et si le travail publié depuis ce worktree n'a pas été poussé, il est
  **inaccessible** depuis les autres postes. C'est exactement ce qui bloque aujourd'hui
  pour `reborn-hud 0.4.134`.
- **Les caches de build** : `build/`, `target/`, `node_modules/`, `.gradle/`. Ils se
  reconstruisent, ne pas chercher à les synchroniser.
- **Le JWT staff** du `manifest-uploader`, stocké dans le gestionnaire d'identifiants
  Windows. Sur une machine neuve, il se recrée au premier login du launcher.

---

## 6. Hygiène des branches

État après la passe du 2026-09-08 :

| Branche | Rôle |
|---|---|
| `main` | trunk. Tout ce qui est en production doit y être. |
| `feature/modrinth-sync` | suivi auto des mises à jour de mods — PR #11 ouverte |
| `archive/migrate-26.2-wip` | **archive figée**, ne pas rebaser, ne pas merger |

**Ce qu'on a appris de `feature/migrate-26.2`** — une branche laissée 103 commits
derrière `main` devient un piège : 10 de ses 22 commits avaient été re-landés sur `main`
sous un autre hash, et son merge aurait **ressuscité des fichiers supprimés
volontairement** (43 `.ogg` d'OST dans le jar, module backpack). Elle a été archivée et
son travail utile rebâti proprement sur `main`.

Règle qui en découle : **une branche de feature vit quelques jours, pas trois mois.**
Si elle prend du retard, la resynchroniser (`git merge main`) au lieu d'attendre.

### Nettoyer ses branches locales périmées

```bash
git fetch --prune                       # supprime les refs distantes disparues
git branch -vv | grep ': gone]'         # branches locales dont le distant n'existe plus
git branch -D <branche>                 # une fois vérifié qu'il n'y a rien dessus
```

---

## 7. Check-list de reprise sur une autre machine

```bash
git fetch --prune
git switch main && git pull --ff-only
pnpm install                # si package.json a bougé
```

Puis, selon ce qu'on touche :

| Je touche… | Je vérifie |
|---|---|
| API / panel / bot | `pnpm exec prisma generate` dans `apps/api` (⚠️ arrêter `api:dev` d'abord sur Windows, la DLL du query engine est verrouillée) |
| launcher | les variables `*_BUILD` sont bien exportées avant `pnpm launcher:build` ([`MAINTENANCE.md`](./MAINTENANCE.md) §7) |
| mods / plugins | `JAVA_HOME` pointe sur un **JDK 25** |
| publication | [`PUBLISH_PREFLIGHT.md`](./PUBLISH_PREFLIGHT.md) en entier, sans sauter le §0 |
