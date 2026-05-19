# syntax=docker/dockerfile:1.7
# Multi-stage build pour l'API NestJS Reborn. Construit depuis la racine
# du monorepo (cf docker-compose.prod.yml `context: ..` `dockerfile: apps/api/Dockerfile`).

ARG NODE_VERSION=20-alpine

# ────────────────────────────────────────────────────────
# deps : install pnpm + dependencies workspace
# ────────────────────────────────────────────────────────
FROM node:${NODE_VERSION} AS deps
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
# CI=true desactive les prompts interactifs pnpm (ex: confirmer une purge
# de node_modules quand on passe en mode --prod apres avoir installe les
# devDeps). Sans ca, le install --prod plante en NO_TTY dans Docker.
ENV CI=true
WORKDIR /repo

# Copy les manifests pour rendre la couche cachable tant que les deps ne
# changent pas. On copie TOUS les package.json du workspace parce que
# pnpm install verifie la coherence globale.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/api/package.json apps/api/
COPY apps/bot/package.json apps/bot/
COPY apps/admin/package.json apps/admin/
COPY apps/launcher/package.json apps/launcher/
COPY packages/manifest-signer/package.json packages/manifest-signer/

# `--filter @reborn/api...` installe l'api ET ses deps transitives uniquement.
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @reborn/api...

# ────────────────────────────────────────────────────────
# builder : copy source + generate prisma client + nest build
# ────────────────────────────────────────────────────────
FROM node:${NODE_VERSION} AS builder
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
ENV CI=true
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/apps/api/node_modules ./apps/api/node_modules
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/api ./apps/api
COPY packages ./packages

WORKDIR /repo/apps/api
RUN pnpm exec prisma generate
RUN pnpm exec nest build

# Retire les dev-deps apres build pour shrinker l'image runtime.
WORKDIR /repo
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @reborn/api... --prod

# ────────────────────────────────────────────────────────
# runner : minimal image
# ────────────────────────────────────────────────────────
FROM node:${NODE_VERSION} AS runner
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000

# User non-root pour reduire la surface d'attaque.
RUN addgroup -S reborn && adduser -S reborn -G reborn

COPY --from=builder --chown=reborn:reborn /repo/node_modules ./node_modules
COPY --from=builder --chown=reborn:reborn /repo/apps/api/node_modules ./apps/api/node_modules
COPY --from=builder --chown=reborn:reborn /repo/apps/api/dist ./apps/api/dist
COPY --from=builder --chown=reborn:reborn /repo/apps/api/prisma ./apps/api/prisma
COPY --from=builder --chown=reborn:reborn /repo/apps/api/package.json ./apps/api/
COPY --from=builder --chown=reborn:reborn /repo/package.json /repo/pnpm-workspace.yaml ./

USER reborn

EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget --quiet --spider http://localhost:3000/v1/health || exit 1

# entrypoint applique les migrations Prisma puis lance Nest. Si migrate
# fail (BDD pas prête, lock), on retry plus tard via le restart docker.
# Note : nest build sort dans dist/src/main.js (pas dist/main.js)
# parce que sourceRoot=src dans nest-cli.json.
WORKDIR /app/apps/api
CMD ["sh", "-c", "pnpm exec prisma migrate deploy && node dist/src/main.js"]
