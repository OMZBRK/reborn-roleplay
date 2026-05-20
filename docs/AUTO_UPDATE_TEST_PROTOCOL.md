# Protocole de test auto-update launcher

Marche à suivre **reproductible** pour valider que le pipeline auto-update
fonctionne après une modification du code Tauri / du back release /
du frontend updater. À exécuter au minimum :

- Avant toute release distribuée aux staffs/joueurs
- Après bump d'une dépendance majeure (`@tauri-apps/plugin-updater`,
  `tauri-plugin-updater`)
- Après modification de `tauri.conf.json` (endpoint, pubkey, channel)
- Après modification de `apps/launcher/src/components/UpdateChecker.tsx`
- Après modification de `apps/api/src/releases/`

---

## Pré-requis

- Avoir une version `N` du launcher déjà installée sur la machine de test
  (le `.exe` distribué aux staffs). Sinon : installer la version `N`
  avant de bumper.
- Avoir accès SSH au VPS (pour vérifier logs API si besoin).
- Avoir `gh` (GitHub CLI) configuré (`gh auth login`) et le repo
  `OMZBRK/reborn-roleplay` cloné en local.
- Avoir les secrets locaux :
  - `secrets/tauri-updater.key` (clé privée signing)
  - `secrets/tauri-updater.key.pub` (clé publique, doit matcher
    `tauri.conf.json#plugins.updater.pubkey`)

---

## 1. Bump version dans les 4 fichiers

Aligne strictement la même version partout (sinon le build échoue ou
le check `current` est inconsistent) :

| Fichier | Champ |
|---|---|
| `apps/launcher/package.json` | `"version"` |
| `apps/launcher/src-tauri/Cargo.toml` | `version` |
| `apps/launcher/src-tauri/tauri.conf.json` | `"version"` |
| `apps/launcher/src-tauri/Cargo.lock` | section `[[package]] name = "launcher"` → `version` |

(`Cargo.lock` se met à jour seul au prochain `cargo check`, mais le commiter
explicite évite que le diff pollue le commit suivant.)

Vérif rapide après bump :

```pwsh
grep '"version"' apps/launcher/package.json apps/launcher/src-tauri/tauri.conf.json
grep '^version' apps/launcher/src-tauri/Cargo.toml
```

Les 3 doivent retourner la nouvelle version.

Commit local :

```pwsh
git add apps/launcher/package.json apps/launcher/src-tauri/Cargo.toml `
        apps/launcher/src-tauri/tauri.conf.json apps/launcher/src-tauri/Cargo.lock
git commit -m "chore(launcher): bump version to X.Y.Z (auto-update test)"
```

(Pas de push immédiat — on push une fois que le test end-to-end a réussi.)

---

## 2. Build signé

Toutes les vars `_BUILD` doivent être présentes dans l'env de la session
PowerShell, sinon le `.exe` final n'aura ni le bon API endpoint, ni la
bonne MS Client ID, etc.

```pwsh
cd "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER"

$env:REBORN_API_URL_BUILD = "https://api.reborn-rp.com/v1"
$env:MANIFEST_PUBLIC_KEY_HEX_BUILD = (Get-Content .\secrets\manifest_ed25519_public.hex -Raw).Trim()
$env:MS_CLIENT_ID_BUILD = "affd0327-8cb3-479c-80aa-a8be73b8ba4d"
$env:REBORN_SERVER_HOST_BUILD = "play.reborn-rp.com"
$env:REBORN_SERVER_PORT_BUILD = "27106"
$env:DISCORD_CLIENT_ID_BUILD = "1500528272939684001"
$env:TAURI_SIGNING_PRIVATE_KEY = ".\secrets\tauri-updater.key"
$env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = "rebornlauncher"

pnpm launcher:build
```

**La signature Ed25519 du `.exe` est automatique** parce que
`TAURI_SIGNING_PRIVATE_KEY` est set. Un fichier `.sig` est généré à côté du
`.exe` — c'est lui qui sera envoyé à l'API.

Sortie attendue (chemins) :

```
apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_X.Y.Z_x64-setup.exe
apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_X.Y.Z_x64-setup.exe.sig
```

Si le `.sig` n'est PAS généré : le `TAURI_SIGNING_PRIVATE_KEY` n'a pas été
pris en compte. Vérifie qu'il est bien set AVANT le `pnpm launcher:build`.

---

## 3. Récupérer la signature

```pwsh
$sig = (Get-Content ".\apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_X.Y.Z_x64-setup.exe.sig" -Raw).Trim()
Write-Host "Signature : $sig"
$sig | Set-Clipboard
```

La signature est un blob base64 de ~200 chars commençant par
`dW50cnVzdGVkIGNvbW1lbnQ6IHNpZ25hdHVyZSBmcm9tIHRhdXJpIHNlY3JldCBrZXk6` (en clair :
"untrusted comment: signature from tauri secret key:").

---

## 4. Upload sur GitHub Release

```pwsh
gh release create launcher-vX.Y.Z `
  ".\apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_X.Y.Z_x64-setup.exe" `
  ".\apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_X.Y.Z_x64-setup.exe.sig" `
  --title "Launcher vX.Y.Z" `
  --notes "Description courte des changements."
```

URL résultante (déterministe) :
```
https://github.com/OMZBRK/reborn-roleplay/releases/download/launcher-vX.Y.Z/RebornLauncher_X.Y.Z_x64-setup.exe
```

C'est cette URL qu'on enverra à l'API.

---

## 5. Publier dans l'API (`POST /v1/admin/releases`)

L'endpoint exige un JWT `MinRole ADMIN`. Deux approches :

### Approche A — via `manifest-uploader` (préférée)

Le binaire `packages/manifest-uploader` lit le JWT depuis Windows Credential
Manager + refresh automatique. Pour pouvoir l'utiliser sur l'endpoint
`/admin/releases`, étendre le binaire avec une sous-commande dédiée
(non-implémentée aujourd'hui — TODO).

### Approche B — PowerShell direct avec JWT manuel

Récupère le JWT (méthode au choix : DevTools du launcher, dump via mini
script Rust, ou ajouter temporairement un `tracing::info!` dans la commande
Tauri `auth_me` pour le voir dans la console dev). Puis :

```pwsh
$jwt = "<colle-le-token-ici>"
$sig = Get-Clipboard

$body = @{
  version   = "X.Y.Z"
  target    = "windows-x86_64"
  channel   = "stable"
  url       = "https://github.com/OMZBRK/reborn-roleplay/releases/download/launcher-vX.Y.Z/RebornLauncher_X.Y.Z_x64-setup.exe"
  signature = $sig
  notes     = "Description courte affichee dans la toast."
} | ConvertTo-Json

Invoke-RestMethod `
  -Method POST `
  -Uri "https://api.reborn-rp.com/v1/admin/releases" `
  -Headers @{ Authorization = "Bearer $jwt" } `
  -ContentType "application/json" `
  -Body $body
```

Réponse attendue : `201 Created` avec l'objet release complet.

Vérification rapide que la release est bien servie côté `/launcher/update` :

```pwsh
Invoke-RestMethod -Uri "https://api.reborn-rp.com/v1/launcher/update?current=0.1.0&target=windows-x86_64&arch=x86_64"
```

Doit retourner le JSON Tauri avec `version`, `pub_date`, `url`, `signature`, `notes`.
Si tu mets `current=X.Y.Z` (= la version qu'on vient de publier), tu dois
recevoir `204 No Content` (= déjà à jour).

---

## 6. Tester l'auto-update sur le launcher version `N`

Sur la machine où le launcher version `N` (< X.Y.Z) est installé :

1. **Lance le launcher** (login Microsoft si nécessaire).
2. Attends **30 minutes max** (intervalle de poll). Ou : redémarre le
   launcher — le `check()` se déclenche aussi au boot dans `useEffect`.
3. Une **toast** apparaît en bas-droite : "Mise à jour X.Y.Z disponible"
   avec les release notes.
4. Clique **"Installer et redémarrer"**.
5. Progress bar de téléchargement → "Installation prête" → "Redémarrage…"
6. Le launcher redémarre en version `X.Y.Z`.

### Checks de validation visuels

- Le numéro de version dans Settings → À propos (ou Window title) doit
  passer à `X.Y.Z`.
- Aucune erreur "signature invalide" dans la toast — si erreur, le `.sig`
  envoyé à l'API ne matche pas le `.exe` upload (re-upload après
  modification ?).
- Le DevTools console (clic droit → Inspect si activé en dev) ne doit pas
  log d'erreur `[updater] check failed`.

---

## 7. Troubleshooting

### Le launcher ne propose jamais l'update

- **Pubkey mismatch** : `tauri.conf.json#plugins.updater.pubkey` doit matcher
  `secrets/tauri-updater.key.pub`. Vérifie avec :
  ```pwsh
  Get-Content .\secrets\tauri-updater.key.pub -Raw
  ```
  La pubkey base64 affichée doit être encodée et collée dans `tauri.conf.json`.
- **Endpoint unreachable** : tester en direct
  ```pwsh
  Invoke-RestMethod "https://api.reborn-rp.com/v1/launcher/update?current=0.1.0&target=windows-x86_64&arch=x86_64"
  ```
- **Version comparison failed** : si `current` >= version DB, retour 204.
  Vérifier en DB :
  ```bash
  ssh ubuntu@91.134.136.120
  sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml exec postgres \
    psql -U reborn -d reborn -c 'SELECT version, target, channel, url FROM "LauncherRelease" ORDER BY "publishedAt" DESC LIMIT 5;'
  ```

### Signature invalide à l'install

- Le `.exe` upload n'est pas celui qui a été signé (re-upload après modif sans
  re-sign).
- Le `.sig` envoyé à l'API ne correspond pas au `.exe` (mauvais copy-paste).
- La pubkey dans `tauri.conf.json` ne matche pas la clé privée utilisée.
  → Vérifier `secrets/tauri-updater.key.pub` vs `pubkey` du conf.

### `gh release create` échoue : "release already exists"

```pwsh
gh release delete launcher-vX.Y.Z --yes
# puis re-create
```

### Toast n'apparait pas même après 30min

- `UpdateChecker` est monté dans `AuthenticatedLayout`, donc **après login**.
  Si tu testes sans login (écran Microsoft OAuth), le composant n'est pas
  monté.
- Vérifier la console DevTools (`Ctrl+Shift+I` si dev) :
  - `[updater] check failed: ...` → endpoint down, vérifier API
  - Pas de log mais pas de toast → le `check()` retourne `null` (= pas
    d'update dispo selon l'API).

---

## 8. Rollback d'urgence

Si une release casse les launchers en production :

1. Supprimer la release de la DB (depuis le VPS) :
   ```bash
   sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml exec postgres \
     psql -U reborn -d reborn -c 'DELETE FROM "LauncherRelease" WHERE version = '\''X.Y.Z'\'';'
   ```
2. Les launchers déjà mis à jour restent en X.Y.Z (pas de rollback auto).
   Les launchers en N (pas encore mis à jour) ne verront plus la release
   cassée.
3. Re-publier une version `X.Y.Z+1` (corrective) en suivant le flow normal.

---

## 9. Améliorations à venir (cf docs/RELEASING.md)

| Item | Priorité | Estimation |
|---|---|---|
| Sous-commande `release` dans `manifest-uploader` | 🟡 medium | 30 min |
| Script `scripts/release-launcher.ps1` qui enchaîne bump + build + sign + POST | 🟡 medium | 1h |
| Workflow GitHub Actions sur push tag `launcher-v*` | 🔵 long terme | 2-3h |
| UI admin Next.js pour publier les releases | 🔵 long terme | 2h |
| Switch channel (stable/beta) dans Settings | 🔵 long terme | 1h |
