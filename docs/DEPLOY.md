# Déploiement production Reborn

Stack : Docker Compose sur VPS Linux (Debian/Ubuntu recommandé). Caddy
en reverse proxy avec auto-TLS (Let's Encrypt). Postgres + Redis
internes (jamais exposés publiquement). 3 apps containerisées : API,
panel admin, bot Discord.

Le launcher Tauri n'est PAS dans Docker (binaire end-user, distribué
via l'auto-updater — cf `docs/RELEASING.md`).

---

## Pré-requis VPS

- **OS** : Debian 12+ ou Ubuntu 22.04+ (autre distro à adapter).
- **RAM** : 2 Go minimum (4 Go confortable avec Postgres + Redis +
  3 Node + Caddy).
- **Disque** : 20 Go SSD minimum (Postgres grossit, prévoir 50 Go).
- **Réseau** : IPv4 publique, ports 80 + 443 ouverts (UDP/443 aussi pour
  HTTP/3).
- **DNS** : 2 enregistrements A pointant sur l'IP du VPS :
  - `api.reborn-rp.fr → IP`
  - `panel.reborn-rp.fr → IP`

### Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# Reconnecte la session SSH pour que le groupe prenne effet.
```

Vérifier : `docker --version && docker compose version`.

---

## Premier déploiement

### 1. Clone le repo

```bash
git clone https://github.com/<org>/reborn-roleplay.git
cd reborn-roleplay
```

### 2. Configure les secrets

```bash
cp .env.prod.example .env.prod
nano .env.prod
```

À remplir au minimum :
- `API_DOMAIN`, `ADMIN_DOMAIN`, `ACME_EMAIL` (cf DNS)
- `POSTGRES_PASSWORD` (`openssl rand -base64 32`)
- `JWT_SECRET` (`openssl rand -base64 48`)
- `MS_CLIENT_ID` (cf ADR 0001 pour l'approbation Microsoft)
- Tous les `DISCORD_*` (Developer Portal Discord)
- `REBORN_WEBHOOK_SECRET` (`openssl rand -hex 32`)
- `REBORN_PLAY_TOKEN_SECRET` (`openssl rand -base64 48`)
- `MANIFEST_PUBLIC_KEY_HEX` (cf packages/manifest-signer)

### 3. Premier `docker compose up`

```bash
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d --build
```

Premier build = ~5-10 min (pull images + 3 builds multi-stage).

Caddy va automatiquement obtenir les certificats Let's Encrypt au boot
(quelques secondes par domaine). Si ça fail, vérifier :
- DNS résout bien vers le VPS (`dig api.reborn-rp.fr`)
- Port 80 + 443 ouverts (Let's Encrypt utilise HTTP-01 challenge sur :80)

### 4. Vérifier que tout monte

```bash
docker compose -f infra/docker-compose.prod.yml ps
# Tout doit etre "running" + "healthy"

# Logs en live
docker compose -f infra/docker-compose.prod.yml logs -f api
docker compose -f infra/docker-compose.prod.yml logs -f admin
docker compose -f infra/docker-compose.prod.yml logs -f bot
docker compose -f infra/docker-compose.prod.yml logs -f caddy
```

Tester :
- `curl https://api.reborn-rp.fr/v1/health` → `{"status":"ok",...}`
- `curl -I https://panel.reborn-rp.fr` → 200 OK

### 5. Promouvoir le 1er compte staff

L'API ne peut pas savoir qui est admin tant que tu n'as pas remonté
le rôle d'un user. Connecte-toi via le launcher (ce qui crée le user
en BDD), puis SQL direct :

```bash
docker compose -f infra/docker-compose.prod.yml exec postgres \
  psql -U reborn -d reborn \
  -c "UPDATE \"User\" SET role='OWNER' WHERE \"minecraftUsername\"='TonPseudo';"
```

Puis lie ton compte Discord via Settings du launcher → tu peux login au panel.

---

## Updates / redéploiement

```bash
cd reborn-roleplay
git pull
docker compose -f infra/docker-compose.prod.yml --env-file .env.prod up -d --build
```

Les migrations Prisma sont appliquées au boot de l'API (cf
`prisma migrate deploy` dans le CMD du Dockerfile). Pas de step manuel.

Caddy reload sa config automatiquement sur changement du Caddyfile.

**Downtime** : ~30s à 2min selon ce qui a changé. Pour zero-downtime
on basculerait sur une stratégie blue-green ou un orchestrateur, mais
c'est overkill pour le MVP.

---

## Backup

### Postgres

Snapshot quotidien recommandé. Exemple cron + pg_dump :

```bash
# /etc/cron.daily/reborn-pgbackup
#!/bin/bash
DATE=$(date +%Y%m%d-%H%M)
docker compose -f /opt/reborn/infra/docker-compose.prod.yml exec -T postgres \
  pg_dump -U reborn reborn | gzip > /backup/reborn-$DATE.sql.gz
# Rotation 30 jours
find /backup -name 'reborn-*.sql.gz' -mtime +30 -delete
```

Pour un vrai setup prod : copier les backups off-site (rsync vers un
autre serveur, S3 Object Lock, etc.). Cf PLAN §14.

### Restore

```bash
docker compose -f infra/docker-compose.prod.yml exec -T postgres \
  psql -U reborn -d reborn < backup.sql
```

### Volumes Caddy

`reborn_caddy_data` contient les certs Let's Encrypt. Le perdre =
renouvellement forcé au prochain boot (rate-limit risque). Backup
recommandé :

```bash
docker run --rm -v reborn-prod_reborn_caddy_data:/data -v /backup:/backup \
  alpine tar czf /backup/caddy-$(date +%Y%m%d).tar.gz -C /data .
```

---

## Dépannage

**Caddy ne peut pas obtenir le cert** :
- `docker logs reborn-caddy` → cherche "ACME" errors
- Causes fréquentes : DNS pas propagé, port 80 bloqué par firewall,
  rate-limit Let's Encrypt (max 5 echecs/heure/domaine).

**API ne démarre pas, "DATABASE_URL invalid"** :
- Vérifie `.env.prod` : `DATABASE_URL` est construit dynamiquement depuis
  `POSTGRES_*` dans le compose, pas a définir manuellement.
- `docker logs reborn-api` → l'erreur exacte.

**Bot connect Discord puis spam reconnect** :
- Token Discord invalide / révoqué → régénère dans le Developer Portal,
  remplace dans `.env.prod`, restart `docker compose up -d bot`.

**Panel renvoie 401 sur tout** :
- `NEXT_PUBLIC_API_BASE_URL` au build pointait vers une mauvaise URL
  (`http://localhost...` au lieu de `https://api...`). Rebuild :
  `docker compose -f infra/docker-compose.prod.yml build admin && up -d admin`.

**Migrations Prisma fail au boot api** :
- Vérifie que Postgres est `healthy` (`docker compose ps`).
- Schema drift : `docker exec reborn-api pnpm exec prisma migrate status`.

---

## Limitations MVP

- **1 seul VPS** : pas de haute dispo, pas de load balancing. Si le VPS
  tombe, tout tombe. Pour la suite : 2 VPS + Cloudflare Load Balancer.
- **Pas de backup automatique** off-site (à mettre en place selon le
  cron exemple ci-dessus).
- **Logs en local Docker** uniquement — pas de centralisation (Loki,
  Datadog, ...) prévue.
- **Pas de monitoring** prêt à l'emploi — ajouter Uptime Kuma ou
  équivalent en complément.
