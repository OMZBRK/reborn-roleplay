# Publier une nouvelle version du launcher

Ce document décrit le workflow complet pour cut une nouvelle release du
launcher Reborn et la pousser via l'auto-updater Tauri.

## Vue d'ensemble

```
[1] bump version       (cargo + tauri.conf.json + package.json)
[2] build NSIS         (pnpm launcher:build)
[3] sign le binaire    (pnpm tauri signer sign …)
[4] upload le .exe     (où tu veux : self-hosted, GitHub Release, R2)
[5] POST /admin/releases  (panel staff → publie la release pour le polling)
```

Le launcher des joueurs détectera l'update dans les 30 minutes (ou au
prochain démarrage), telechargera le binaire, **vérifiera la signature
Ed25519** contre le `pubkey` figé dans `tauri.conf.json`, et redémarrera.

---

## Setup initial (une seule fois)

### Générer la paire de clés Ed25519

Un keypair unique sert pour toutes les releases. **La clé privée ne doit
jamais être commit** — elle reste dans `secrets/` (ignoré par `.gitignore`).

```pwsh
cd apps/launcher
pnpm exec tauri signer generate -w ../../secrets/tauri-updater.key
```

La commande va te demander une passphrase. Mets-en une ou laisse vide pour
le MVP (mais documente-la dans un password manager).

La sortie affiche aussi le **public key (base64)**. Copie-le dans
`apps/launcher/src-tauri/tauri.conf.json` :

```json
"plugins": {
  "updater": {
    "pubkey": "COLLE_ICI_LE_PUBLIC_KEY"
  }
}
```

Commit le `tauri.conf.json` modifié, **pas** la `secrets/tauri-updater.key`.

---

## Workflow par release

### 1. Bump version

Aligne ces 3 fichiers sur la même version :

- `apps/launcher/package.json` — `version`
- `apps/launcher/src-tauri/Cargo.toml` — `version`
- `apps/launcher/src-tauri/tauri.conf.json` — `version`

### 2. Build NSIS

Depuis la racine du monorepo :

```pwsh
pnpm launcher:build
```

Le `.exe` sort dans `apps/launcher/src-tauri/target/release/bundle/nsis/`.
Nom typique : `RebornLauncher_0.2.0_x64-setup.exe`.

### 3. Signer le binaire

```pwsh
cd apps/launcher
pnpm exec tauri signer sign `
  -f ../../secrets/tauri-updater.key `
  -p rebornlauncher `
  src-tauri/target/release/bundle/nsis/RebornLauncher_0.3.0_x64-setup.exe
```

> **Piège CLI Tauri 2** — `-k` = `--private-key` (clé en **string base64**),
> `-f` = `--private-key-path` (**fichier**). Si tu passes un chemin avec `-k`,
> tauri-signer essaie de le décoder en base64 et plante avec
> `failed to decode base64 key: Invalid symbol 46, offset 0` (le `.` du
> chemin relatif). Utiliser `-f` est l'usage normal.

> **Autre piège env** — si `.env` contient `TAURI_SIGNING_PRIVATE_KEY=./secrets/tauri-updater.key`
> (un chemin au lieu de la clé), tauri-signer est confus et override les flags
> CLI. Soit retire l'entree du `.env` et utilise `-f`, soit y mets la valeur
> base64 du contenu du fichier. La meme remarque vaut pour
> `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` qui passe en `-p`.

Tu obtiens un fichier `.sig` à côté du `.exe`. Ouvre-le, c'est la signature
base64 à envoyer à l'API. Tu peux aussi pipe vers le clipboard avec
`| Set-Clipboard`.

### 4. Upload le binaire

Choisis ton hébergement :

- **Local / staging** : copie le `.exe` dans `secrets/releases/` et expose
  via nginx ou directement l'API (à câbler).
- **GitHub Release** : `gh release create v0.2.0 RebornLauncher_*.exe`.
  URL = `https://github.com/<org>/<repo>/releases/download/v0.2.0/RebornLauncher_0.2.0_x64-setup.exe`.
- **Cloudflare R2 / S3** : `wrangler r2 object put reborn-releases/v0.2.0/...`.

Note l'URL absolue — elle sera dans le payload POST.

### 5. Publier dans l'API

Depuis le panel staff (à venir : UI dédiée) ou directement curl :

```pwsh
$body = @{
  version = "0.2.0"
  target = "windows-x86_64"
  url = "https://example.com/RebornLauncher_0.2.0_x64-setup.exe"
  signature = "BASE64_SIG_DU_FICHIER_.SIG"
  notes = "Fix : sticky chat panel. Feature : DM relay messages."
} | ConvertTo-Json

# Token JWT staff (role ADMIN+) recupere via le login Discord du panel
$headers = @{ Authorization = "Bearer $TOKEN" }

Invoke-RestMethod `
  -Method POST `
  -Uri http://localhost:3000/v1/admin/releases `
  -Headers $headers `
  -ContentType "application/json" `
  -Body $body
```

Réponse : `201 Created` avec l'objet release.

À partir de ce moment, tous les launchers qui poll `GET /v1/launcher/update`
verront cette nouvelle release et la téléchargeront.

---

## Anatomie du polling

Le plugin `tauri-plugin-updater` hit l'endpoint :

```
GET /v1/launcher/update?current=0.1.0&target=windows-x86_64&arch=x86_64
```

L'API répond :

```json
{
  "version": "0.2.0",
  "pub_date": "2026-05-18T14:30:00.000Z",
  "url": "https://example.com/...",
  "signature": "dW50cnVzdGVkIGNvbW1lbnQ6IH...",
  "notes": "Fix : sticky chat panel..."
}
```

Ou `204 No Content` si pas d'update — le plugin considère ça comme "déjà
à jour".

Le plugin :
1. Télécharge `url`
2. Vérifie `signature` avec le `pubkey` figé en config
3. Si OK → installe + propose `relaunch()` (cf `UpdateChecker.tsx`)
4. Si signature invalide → erreur propagée à l'UI

Sécurité : la clé privée ne sortant jamais de ta machine, **un attaquant
qui hijack notre API ne peut pas pousser un binaire malveillant** — la
signature ne matchera pas, le plugin refusera l'install.

---

## Channels (stable / beta)

Le champ `channel` est libre — par défaut `stable`. Pour un canal beta :

- Le binaire est upload pareil
- Le POST `/admin/releases` met `channel: "beta"`
- Le launcher peut être configuré pour suivre un autre canal en passant
  `&channel=beta` dans l'URL d'endpoint (cf `tauri.conf.json`)

MVP : on reste sur `stable` pour tout le monde. Le multi-canal arrivera
quand on aura une UI Settings → "Canal de mise à jour".

---

## Dépannage

**`signature invalide` au check-and-install** :
- Le `.exe` uploadé n'est pas celui qui a été signé (re-uploadé après modif ?).
- La signature dans la DB ne correspond pas à celle générée localement.
- Le `pubkey` dans `tauri.conf.json` ne match pas la clé privée utilisée
  pour signer. Régénère-les ensemble.

**`204 No Content` alors qu'on attend une update** :
- `current` envoyé par le launcher est déjà >= version DB. Vérifie le
  bump version.
- Le `target` query ne matche aucune release (typo `windows-x64` vs
  `windows-x86_64`).

**Le launcher ne propose jamais l'update même si endpoint OK** :
- Le `pubkey` dans `tauri.conf.json` est encore le placeholder.
- Le DevTools console (clic-droit dans la fenêtre) montre une erreur
  `tauri-plugin-updater`.
