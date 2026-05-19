# Maintenance & mises à jour — Reborn Roleplay

Doc opérationnel pour les opérations courantes du déploiement actuel.
Tous les `.md` parlent de **setup from scratch** ; ce fichier-ci parle
de **mise à jour d'un setup qui tourne déjà**.

Lis-le en premier quand tu veux changer quelque chose en prod.

---

## 0. Variables d'environnement de référence

| Domaine | Valeur |
|---|---|
| VPS | `ubuntu@91.134.136.120` (OVH VPS-1 Gravelines) |
| Repo SSH path | `/opt/reborn` |
| Domaine | `reborn-rp.com` |
| API publique | `https://api.reborn-rp.com/v1` |
| Panel | `https://panel.reborn-rp.com` |
| Serveur MC | `play.reborn-rp.com:27106` (Minestrator) |
| Repo GitHub | `OMZBRK/reborn-roleplay` |
| Mods release tag | `mods-v1` |

Tous les secrets vivent dans `/opt/reborn/.env.prod` (VPS) + `secrets/`
(machine locale, **jamais commit**).

---

## 1. Connexion SSH au VPS

```pwsh
ssh ubuntu@91.134.136.120
# password : généré par OVH, dans tes mails
```

Pour copier un fichier local vers le VPS :

```pwsh
scp .\fichier.txt ubuntu@91.134.136.120:/opt/reborn/
```

---

## 2. Ajouter, changer ou mettre à jour un mod du serveur

**Cas concret** : tu remplaces Sodium 0.6.13 par 0.7.0, ou tu ajoutes Iris,
ou tu update le mod `reborn-integrity` après modif du code.

### a) Mettre à jour les jars locaux

```pwsh
cd "C:\Users\omarb\Desktop\Reborn - Gestion\mods-release"
# Supprime l'ancien jar :
Remove-Item .\sodium-fabric-0.6.13+mc1.21.1.jar
# Drag-drop le nouveau dans ce dossier
```

### b) Mettre à jour la GitHub Release `mods-v1`

1. Va sur https://github.com/OMZBRK/reborn-roleplay/releases/tag/mods-v1
2. Clique **"Edit release"**
3. Dans les assets, supprime l'ancien jar (croix à droite)
4. Drag-drop le nouveau jar
5. **"Update release"**

### c) Régénérer + signer + publier le manifest

```pwsh
cd "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\packages\manifest-signer"

# 1. Génère le manifest unsigned depuis le dossier (bump --version à chaque fois)
pnpm exec tsx src/build-from-folder.ts "C:\Users\omarb\Desktop\Reborn - Gestion\mods-release" `
  --base-url https://github.com/OMZBRK/reborn-roleplay/releases/download/mods-v1 `
  --version 1.0.2 `
  --mc 1.21.1 `
  --out "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\secrets\manifest-unsigned.json"

# 2. Signe
pnpm exec tsx src/cli.ts sign "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\secrets\manifest-unsigned.json" `
  --key "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\secrets\manifest_ed25519_private.pem" `
  --out "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\secrets\manifest-signed.json"

# 3. Publie sur l'API (lit le JWT depuis le Credential Manager du launcher)
cd ..\manifest-uploader
cargo run --release -- `
  --api https://api.reborn-rp.com/v1 `
  --file "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\secrets\manifest-signed.json"
```

**Important** : bump le `--version` à chaque manifest (1.0.0 → 1.0.1 → 1.0.2…).
Le launcher détecte le changement et purge l'ancien jar du dossier local
des joueurs.

### d) Côté staffs

Au prochain lancement, le launcher DL le diff (nouveaux jars uniquement,
les inchangés restent en cache local). Aucune action staff requise.

---

## 3. Promote / Demote un utilisateur (rôle staff)

Rôles disponibles : `PLAYER` < `HELPER` < `MODERATOR` < `ADMIN` < `OWNER`.

```bash
ssh ubuntu@91.134.136.120
cd /opt/reborn

# Voir les users
sudo docker compose -f infra/docker-compose.prod.yml exec postgres \
  psql -U reborn -d reborn -c "SELECT \"minecraftUsername\", role FROM \"User\";"

# Promote (remplace pseudo + role)
sudo docker compose -f infra/docker-compose.prod.yml exec postgres \
  psql -U reborn -d reborn \
  -c "UPDATE \"User\" SET role='ADMIN' WHERE \"minecraftUsername\"='PseudoMC';"
```

**Attention** : l'utilisateur doit s'être déjà connecté une fois pour
exister en DB.

---

## 4. Ouvrir / fermer la beta staff

Le gate `LAUNCHER_BETA_GATE=HELPER` empêche les non-staffs de login.

### Ouvrir temporairement (pour qu'un nouveau staff puisse créer son compte)

```bash
ssh ubuntu@91.134.136.120
cd /opt/reborn

# Désactive le gate
sudo sed -i 's/^LAUNCHER_BETA_GATE=.*/LAUNCHER_BETA_GATE=/' .env.prod
sudo docker compose -f infra/docker-compose.prod.yml up -d --force-recreate api

# → le staff se connecte une fois pour créer son user en DB
# → tu le promote (cf §3)
# → tu réactives le gate :

sudo sed -i 's/^LAUNCHER_BETA_GATE=$/LAUNCHER_BETA_GATE=HELPER/' .env.prod
sudo docker compose -f infra/docker-compose.prod.yml up -d --force-recreate api
```

---

## 5. Changer une variable d'environnement de l'API

Exemple : changer le serveur MC pointé par la sidebar du launcher.

```bash
ssh ubuntu@91.134.136.120
cd /opt/reborn
sudo nano .env.prod
# Edite la valeur (ex: REBORN_SERVER_PORT=27106 → 27999)
# Ctrl+O, Entrée, Ctrl+X

sudo docker compose -f infra/docker-compose.prod.yml up -d --force-recreate api
```

**Si tu ajoutes une NOUVELLE variable** qui n'est pas encore dans
`infra/docker-compose.prod.yml` → ajoute-la d'abord dans la section
`environment:` du service `api`, puis fais `force-recreate`. Sinon
docker-compose ne la propage pas au container.

---

## 6. Update le plugin Guardian (côté serveur Minecraft)

Le plugin `reborn-guardian.jar` valide les play-tokens HMAC.

### Si tu modifies le code

```pwsh
# Sur ta machine locale (depuis Windows ou WSL, JDK 21 requis)
cd "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER\minecraft\plugin-guardian"
.\gradlew build
# → build\libs\reborn-guardian-0.1.0-dev.jar
```

### Upload + restart

1. Sur Minestrator → **Fichiers** → `plugins/`
2. Supprime l'ancien `reborn-guardian-*.jar`
3. Upload le nouveau (drag-drop)
4. **Redémarre le serveur** (Stop puis Start)

### Vérifier dans la console serveur

Au boot, tu dois voir :
```
[RebornGuardian] Enabling RebornGuardian v0.1.0-dev
[RebornGuardian] Secret play-token charge (source : config.yml#play-token-secret).
[RebornGuardian] [guardian] canal reborn:auth enregistre, attestation active.
```

---

## 7. Update le launcher (code Rust ou React)

Après modif dans `apps/launcher/`, il faut rebuild et redistribuer.

### Build

```pwsh
cd "C:\Users\omarb\Desktop\Reborn - Gestion\RBLAUNCHER"
git pull  # si tu bossais sur une autre machine

# Set TOUTES les vars compile-time (sinon valeurs incorrectes bakées dans le .exe)
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

Le `.exe` final se trouve dans :
```
apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_<version>_x64-setup.exe
```

### Distribuer

#### Cas A — auto-update (préféré quand le launcher tourne déjà chez les staffs)

Cf `docs/RELEASING.md` pour le workflow complet : bump version,
sign, upload, POST `/v1/admin/releases`. Les staffs récupèrent
auto au prochain démarrage.

#### Cas B — distribution manuelle (premier install, ou auto-update cassé)

1. Upload le `.exe` sur Discord (MP staff, ou canal staff privé)
2. Le staff désinstalle l'ancien (Panneau de config Windows → Apps)
3. Installe le nouveau setup
4. Login Microsoft

---

## 8. Voir les logs

### API NestJS (le plus utile)

```bash
ssh ubuntu@91.134.136.120
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml logs -f --tail=50 api
```

### Tout d'un coup (api + bot + caddy)

```bash
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml logs -f --tail=30
```

### Caddy (TLS / reverse proxy)

```bash
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml logs --tail=30 caddy
```

### Postgres

```bash
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml logs --tail=30 postgres
```

### Serveur Minecraft

Sur le panel Minestrator, dans la console (écran principal).

### Launcher (côté staff)

`%APPDATA%\RebornRoleplay\logs\last-stderr.txt` (forensic post-crash).

---

## 9. Restart un service

```bash
# API seule
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml restart api

# Force recreate (rebuild env vars depuis .env.prod, indispensable après modif)
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml up -d --force-recreate api

# Tout
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml restart
```

---

## 10. Backup de la base de données

```bash
ssh ubuntu@91.134.136.120
sudo docker compose -f /opt/reborn/infra/docker-compose.prod.yml exec postgres \
  pg_dump -U reborn -d reborn > /tmp/reborn-backup-$(date +%F).sql

# Récupère sur ta machine locale :
exit  # quitte SSH
scp ubuntu@91.134.136.120:/tmp/reborn-backup-*.sql .
```

À automatiser plus tard via cron (cf `docs/DEPLOY.md`).

---

## 11. Pull le code mis à jour côté VPS

Quand tu push une modif côté API/bot/admin :

```bash
ssh ubuntu@91.134.136.120
cd /opt/reborn
sudo git pull
sudo docker compose -f infra/docker-compose.prod.yml up -d --build api
# ou --build bot / --build admin selon ce qui a changé
```

Le `--build` force la reconstruction de l'image Docker. Sans ça,
docker réutilise l'image cachée et ton code ne sera pas pris en compte.

---

## 12. Secrets à backup ailleurs (pertes = catastrophe)

| Fichier | Localisation | Si perdu = |
|---|---|---|
| `secrets/manifest_ed25519_private.pem` | Machine locale | Impossible de re-signer les manifests → tous les launchers staff cassés tant qu'on n'a pas re-build et redistribué une nouvelle version avec une nouvelle pubkey |
| `secrets/tauri-updater.key` | Machine locale | Impossible de signer les nouveaux releases auto-update → les staffs ne peuvent plus update sans réinstall manuel |
| `/opt/reborn/.env.prod` | VPS | Tous les secrets HMAC + DB password perdus, faut tout régénérer + restart tous les services |
| Postgres data volume | VPS (Docker volume) | Tous les comptes, sessions, manifestes en DB perdus |

Backup recommandé : copie chiffrée sur un drive privé ET dans un
password manager (1Password, Bitwarden, KeePass).

---

## 13. Pièges fréquents

### Docker compose ne lit pas `.env.prod`

Compose v2 cherche `.env` à côté du fichier compose. Le symlink doit
être dans `infra/` :

```bash
ls -la /opt/reborn/infra/.env
# doit montrer : infra/.env -> /opt/reborn/.env.prod
```

Si pas le cas :
```bash
sudo ln -sf /opt/reborn/.env.prod /opt/reborn/infra/.env
```

### `nest start --watch` ne reload pas après modif `.env`

C'est seulement pour `.ts`. Restart à la main : `restart api`.

### Launcher distribué sans `MS_CLIENT_ID` (erreur "Client ID Microsoft manquant")

Tu as builé sans `MS_CLIENT_ID_BUILD` dans l'env. Refais le build avec
toutes les vars `_BUILD` set (§7).

### Mod purgé à tort par le launcher au démarrage

Le parser de contraintes Fabric peut être trop strict. Le fix du
2026-05-19 (commit `b7a8f5c`) gère les contraintes "1.21" (prefixe) et
les contraintes composées (espaces) en mode conservateur. Si tu vois
encore des purges injustifiées : ouvre le `fabric.mod.json` du mod
incriminé (7-Zip) et regarde la valeur de `depends.minecraft`.

### Manifest fetch retourne 404

Soit aucun manifest n'a été publié, soit celui en cours est expiré
(`expiresAt < now`). Republie via §2.

### Port serveur MC custom Minestrator

Le serveur MC écoute sur un port custom (ex: 27106), pas 25565. Faut le
baker dans le launcher (`REBORN_SERVER_PORT_BUILD`) ET dans l'API
(`REBORN_SERVER_PORT` du `.env.prod`).

---

## 14. Outils maison

| Outil | Usage |
|---|---|
| `packages/manifest-signer/src/build-from-folder.ts` | Génère un manifest unsigned depuis un dossier de jars (calcule sha256 + size). Cf §2.c |
| `packages/manifest-signer/src/cli.ts` | Sign / verify / gen-keys du manifest |
| `packages/manifest-uploader` (Rust) | Lit le JWT du Credential Manager, refresh auto, POST le manifest signé. Cf §2.c |

---

## 15. Le plan d'origine

`PLAN_CONCEPTION_LAUNCHER.md` à la racine — ~2500 lignes, source de
vérité pour les décisions produit + technique. Quand tu doutes du
*pourquoi* d'un choix, c'est là.

`CLAUDE.md` à la racine — guide architectural pour la prochaine session
de dev assistée (contexte cross-fichier, pièges connus).
