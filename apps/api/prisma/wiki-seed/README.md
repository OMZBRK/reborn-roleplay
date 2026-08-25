# Import wiki — « La GLOBALITÉ »

Contenu (`globalite.json`) extrait de la feuille Google « La GLOBALITÉ [REBORN] »
→ 12 entrées wiki taggées. Import **idempotent** (upsert par slug).

## Lancer en prod (sur le VPS, via ta clé SSH)

```bash
cd /opt/reborn && git pull
docker cp apps/api/prisma/wiki-seed/import-wiki.js  reborn-api:/app/apps/api/_imp.js
docker cp apps/api/prisma/wiki-seed/globalite.json  reborn-api:/app/apps/api/_glob.json
docker exec -w /app/apps/api reborn-api node _imp.js _glob.json
docker exec reborn-api rm -f /app/apps/api/_imp.js /app/apps/api/_glob.json
```

Le script lit `DATABASE_URL` + `@prisma/client` du conteneur. Relançable sans
créer de doublons (met à jour les entrées existantes par slug).
