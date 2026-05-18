# Première release staff-only — checklist complète

Document end-to-end pour passer d'un repo en local à un launcher
distribué à 5-10 staffs qui se connectent au serveur Minecraft.

Si tu n'as encore aucune infrastructure en ligne, suivre dans l'ordre.
Sinon, saute les sections déjà faites.

---

## 0. Coûts récapitulatifs

| Item | Coût |
|---|---|
| Nom de domaine `.fr` (OVH/Gandi) | ~10€/an |
| VPS Hetzner CX22 (4 Go RAM) | 4€/mois |
| Tout le reste (DNS, TLS, Discord, Microsoft App, Mojang, mc-heads.net, ip-api.com) | gratuit |

**Total mini réaliste : ~60€/an** (50€ VPS + 10€ domaine).

Variante 0€ : Oracle Cloud Always Free (4 vCPU ARM 24 Go RAM gratuit
à vie si dispo) + sous-domaine duckdns.org gratuit. Moche mais ça
marche.

---

## 1. Pré-requis externes (services tiers)

### Microsoft Azure App (auth Minecraft)

Déjà fait (cf `docs/adr/0001-microsoft-app-approval-required.md` —
résolu en mai 2026). `MS_CLIENT_ID` est dans le `.env.example`.

### Discord Application

1. <https://discord.com/developers/applications> → New Application
2. **Bot** tab → Reset Token → garder secret → `DISCORD_BOT_TOKEN`
3. **OAuth2** tab → Client ID + Client Secret → `DISCORD_CLIENT_ID` + `DISCORD_CLIENT_SECRET`
4. **OAuth2 → Redirects** → ajouter :
   - `https://api.reborn-rp.fr/v1/auth/discord/callback`
   - `https://api.reborn-rp.fr/v1/auth/discord/staff/callback`
5. **Privileged Gateway Intents** → activer `MESSAGE CONTENT INTENT` + `SERVER MEMBERS INTENT`
6. Inviter le bot sur ton serveur Discord :
   - OAuth2 → URL Generator → scopes `bot` + `applications.commands`
   - permissions `Send Messages`, `Manage Threads`, `Embed Links`, `Read Message History`
   - Coller l'URL générée, choisir le serveur, autoriser
7. Identifier les IDs nécessaires :
   - `DISCORD_GUILD_ID` : clic droit sur ton serveur → "Copier l'ID" (active Developer Mode dans Discord Settings → Advanced)
   - `DISCORD_TICKETS_CHANNEL_ID` : crée un salon `#staff-tickets`, copie l'ID

---

## 2. Nom de domaine + DNS

Achete un domaine (OVH, Gandi, Cloudflare Registrar) pour ~10€/an.
Crée 2 enregistrements A :

```
api.reborn-rp.fr   →  <IP_DU_VPS>
panel.reborn-rp.fr →  <IP_DU_VPS>
```

Propagation : 5 min à 24h. Vérifie avec `dig api.reborn-rp.fr`
(doit retourner ton IP).

Variante 0€ : utilise duckdns.org → `https://www.duckdns.org` →
crée 2 sous-domaines : `reborn-api.duckdns.org` + `reborn-panel.duckdns.org`,
mets l'IP du VPS.

---

## 3. VPS

### Provisioning

- **Hetzner CX22** (4€/mois) : compte Hetzner Cloud → Server → CX22 →
  Debian 12 → SSH key
- **Oracle Cloud Free** : Always Free → Ampere A1 → Ubuntu 22.04 →
  4 vCPU 24 Go RAM
- **OVH VPS Starter** : ~3.5€/mois, équivalent

### Setup initial

SSH sur le VPS :

```bash
# 1. Update + install docker
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# Reconnect SSH

# 2. Firewall : autorise SSH + HTTP + HTTPS
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp  # HTTP/3
sudo ufw enable

# 3. Clone le repo
mkdir -p /opt/reborn && cd /opt/reborn
git clone https://github.com/<ton-org>/reborn-roleplay.git .
```

---

## 4. Configuration `.env.prod`

```bash
cd /opt/reborn
cp .env.prod.example .env.prod
nano .env.prod
```

Remplir **dans cet ordre** :

```bash
# Domaines (cf §2)
API_DOMAIN=api.reborn-rp.fr
ADMIN_DOMAIN=panel.reborn-rp.fr
ACME_EMAIL=ton@email.fr
ADMIN_BASE_URL=https://panel.reborn-rp.fr
NEXT_PUBLIC_API_BASE_URL=https://api.reborn-rp.fr/v1

# Postgres (génère un mdp fort)
POSTGRES_USER=reborn
POSTGRES_PASSWORD=$(openssl rand -base64 32)  # colle la sortie
POSTGRES_DB=reborn

# JWT
JWT_SECRET=$(openssl rand -base64 48)         # colle la sortie

# Microsoft (cf §1)
MS_CLIENT_ID=<ton client id Azure>

# Discord (cf §1)
DISCORD_CLIENT_ID=...
DISCORD_CLIENT_SECRET=...
DISCORD_BOT_TOKEN=...
DISCORD_GUILD_ID=...
DISCORD_TICKETS_CHANNEL_ID=...
DISCORD_REDIRECT_URI=https://api.reborn-rp.fr/v1/auth/discord/callback
DISCORD_STAFF_REDIRECT_URI=https://api.reborn-rp.fr/v1/auth/discord/staff/callback

# Webhooks
REBORN_WEBHOOK_SECRET=$(openssl rand -hex 32)

# Play token
REBORN_PLAY_TOKEN_SECRET=$(openssl rand -base64 48)

# Manifest pubkey : à générer plus tard (§6)
MANIFEST_PUBLIC_KEY_HEX=

# Beta fermée staff-only
LAUNCHER_BETA_GATE=HELPER
```

---

## 5. Premier `docker compose up`

```bash
cd /opt/reborn
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d --build
```

Premier build : ~5-10 min (téléchargement images + 3 builds).

Vérifier :

```bash
docker compose -f infra/docker-compose.prod.yml ps
# Tout doit être "running" et "healthy"

curl https://api.reborn-rp.fr/v1/health
# → {"status":"ok","uptime":12.34}
```

Si Caddy bloque sur le certificat, vérifier que ton DNS pointe bien
vers le VPS et que les ports 80/443 sont ouverts.

---

## 6. Clés de signature (sur ta machine locale)

Tu auras besoin de 2 paires de clés Ed25519 (différentes) :

1. **Manifest signing** : signe la liste des mods/fichiers
2. **Tauri updater signing** : signe les `.exe` de release

### Manifest

Sur ta machine locale (PAS le VPS) :

```bash
cd packages/manifest-signer
pnpm exec tsx src/cli.ts gen-keys --out-dir ../../secrets
# → secrets/manifest_ed25519_private.pem
# → secrets/manifest_ed25519_public.pem
# → secrets/manifest_ed25519_public.hex
```

**Copie le contenu de `manifest_ed25519_public.hex`** et mets-le
dans `MANIFEST_PUBLIC_KEY_HEX` du `.env.prod` du VPS, puis :

```bash
ssh user@vps
cd /opt/reborn
nano .env.prod  # update MANIFEST_PUBLIC_KEY_HEX
docker compose -f infra/docker-compose.prod.yml restart api
```

### Tauri updater (auto-update)

Déjà fait dans ta session précédente, cf `docs/RELEASING.md`. Pubkey
déjà dans `apps/launcher/src-tauri/tauri.conf.json`.

---

## 7. Mods Fabric à shipper

Liste recommandée pour un serveur RP performant :

| Mod | But | Source |
|---|---|---|
| **Fabric API** | Dépendance commune | <https://modrinth.com/mod/fabric-api> |
| **Sodium** | Optimisation rendering | <https://modrinth.com/mod/sodium> |
| **Lithium** | Optimisation server tick | <https://modrinth.com/mod/lithium> |
| **FerriteCore** | Réduit usage RAM | <https://modrinth.com/mod/ferrite-core> |
| **Reborn Integrity** | Notre mod d'attestation (cf `minecraft/mod-integrity`) | local |

Pour chaque mod :

1. Télécharge le `.jar` pour Minecraft 1.21.1
2. Calcule le sha256 : `sha256sum mod.jar`
3. Upload le `.jar` sur un endroit accessible (S3, GitHub Releases,
   ou le VPS lui-même via un volume Caddy)
4. Note l'URL + sha256

### Build et upload Reborn Integrity

```bash
cd minecraft/mod-integrity
./gradlew build
# → build/libs/reborn-integrity-0.1.0-dev.jar
```

Upload le jar et note l'URL + sha256.

---

## 8. Signer + publier le manifest

Sur ta machine locale :

```bash
cd packages/manifest-signer

# Edite examples/sample-manifest.json avec ta vraie liste de mods :
cat > /tmp/reborn-manifest.json <<EOF
{
  "version": "1.0.0",
  "minecraftVersion": "1.21.1",
  "minLauncherVersion": "0.1.0",
  "issuedAt": "2026-05-18T12:00:00Z",
  "expiresAt": "2027-05-18T12:00:00Z",
  "files": [
    {
      "path": "mods/fabric-api-0.111.0+1.21.1.jar",
      "sha256": "...",
      "size": 2345678,
      "url": "https://<host>/mods/fabric-api-...jar",
      "required": true
    },
    {
      "path": "mods/sodium-0.6.0+mc1.21.1.jar",
      "sha256": "...",
      "size": 1234567,
      "url": "https://<host>/mods/sodium-...jar",
      "required": true
    },
    {
      "path": "mods/reborn-integrity-0.1.0.jar",
      "sha256": "...",
      "size": 123456,
      "url": "https://<host>/mods/reborn-integrity-...jar",
      "required": true
    }
  ]
}
EOF

pnpm exec tsx src/cli.ts sign /tmp/reborn-manifest.json \
  --key ../../secrets/manifest_ed25519_private.pem \
  --out /tmp/reborn-manifest-signed.json
```

Le fichier signé contient les `files` + la `signature` à la fin.

Publie via le panel (login en tant que ADMIN+) ou directement curl :

```bash
TOKEN="<JWT_du_login_panel>"  # cf login Discord staff

curl -X POST https://api.reborn-rp.fr/v1/admin/manifest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @/tmp/reborn-manifest-signed.json
```

Vérifier que c'est bien le current :

```bash
curl https://api.reborn-rp.fr/v1/manifest/current
# Doit refléter la version 1.0.0
```

---

## 9. Build du launcher

Sur ta machine locale :

```bash
# 1. Configure le pointeur API dans le launcher
# Edite apps/launcher/src-tauri/src/api/mod.rs ou la constante REBORN_API
# pour pointer sur https://api.reborn-rp.fr/v1 en release.

# 2. Build
pnpm launcher:build

# 3. Le .exe sort dans :
# apps/launcher/src-tauri/target/release/bundle/nsis/RebornLauncher_0.1.0_x64-setup.exe
```

Cet `.exe` est **prêt à être distribué**.

---

## 10. Distribuer aux staffs

1. Upload le `.exe` quelque part (Discord upload direct, ou
   `transfer.sh`, ou GitHub Releases)
2. Promote chaque staff en `HELPER` ou plus via la DB :

```bash
ssh user@vps
docker compose -f /opt/reborn/infra/docker-compose.prod.yml exec postgres \
  psql -U reborn -d reborn \
  -c "UPDATE \"User\" SET role='ADMIN' WHERE \"minecraftUsername\"='PseudoDuStaff';"
```

Attention : le staff doit s'être déjà connecté au moins une fois pour
exister en DB. Si pas encore : il faut temporairement enlever le
`LAUNCHER_BETA_GATE` pour qu'il puisse créer son user, puis le promote,
puis remettre le gate.

3. Le staff DM-é reçoit le `.exe`, l'installe, login Microsoft → OK
   parce que son role est HELPER+.

---

## 11. Workflow update (cf `docs/RELEASING.md`)

Quand tu push une nouvelle version du launcher (par exemple la mise à
jour avec le main menu Minecraft custom) :

1. Bump version × 3 fichiers (`apps/launcher/package.json`, `Cargo.toml`,
   `tauri.conf.json`)
2. `pnpm launcher:build`
3. `pnpm launcher:sign <exe>`
4. Upload l'`.exe` quelque part
5. POST `/v1/admin/releases` avec `{version, target, url, signature, notes}`
6. Les launchers staff détectent au prochain poll (max 30 min) → installent
   automatiquement → redémarrent → nouveau menu visible

---

## 12. Limitations connues de la première version

- **FS watcher → kill-on-tamper** : le watcher détecte la modif des mods
  mais ne kill pas la JVM (le PLAN §4.4 dit "intentionally not connected
  yet"). À câbler dans une future update.
- **Pas de backup automatique** off-site. À mettre en place avec un cron
  pg_dump → S3 (cf `docs/DEPLOY.md`).
- **Pas de monitoring**. Recommandé : Uptime Kuma sur le même VPS pour
  voir les uptime des services.
- **Pas de bouton "Publier manifest" dans le panel** — pour l'instant
  curl. UI dédiée = future itération.
