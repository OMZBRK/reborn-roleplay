# Infra (dev local)

Docker compose pour les dépendances locales : Postgres 16 + Redis 7.

## Lancer

```pwsh
# Depuis la racine du repo
pnpm infra:up
# ou directement
docker compose -f infra/docker-compose.yml up -d
```

## Services

| Service  | Port (host) | URL/DSN                                                            |
|----------|-------------|--------------------------------------------------------------------|
| Postgres | 5432        | `postgresql://reborn:reborn_dev_password_change_me@localhost:5432/reborn` |
| Redis    | 6379        | `redis://localhost:6379`                                           |

> Les credentials par défaut viennent de `.env` (à la racine, copie de `.env.example`). Surcharge possible avec `POSTGRES_*` / `REDIS_PORT`.

## Persistance

Les données vivent dans deux volumes Docker nommés :
- `reborn-dev_reborn_postgres_data`
- `reborn-dev_reborn_redis_data`

Pour repartir de zéro : `docker compose -f infra/docker-compose.yml down -v`.

## Healthchecks

Les deux services ont un healthcheck — `docker compose ps` indique `healthy` quand ils sont prêts à accepter les connexions.
