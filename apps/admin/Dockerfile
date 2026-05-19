# syntax=docker/dockerfile:1.7
# Dockerfile panel staff Next.js — utilise output: 'standalone' (cf
# next.config.ts) qui bundle uniquement les chunks runtime necessaires
# dans .next/standalone. L'image runner est ~150 Mo au lieu de 1+ Go.
ARG NODE_VERSION=20-alpine

FROM node:${NODE_VERSION} AS deps
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
ENV CI=true
WORKDIR /repo
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/api/package.json apps/api/
COPY apps/bot/package.json apps/bot/
COPY apps/admin/package.json apps/admin/
COPY apps/launcher/package.json apps/launcher/
COPY packages/manifest-signer/package.json packages/manifest-signer/
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @reborn/admin...

FROM node:${NODE_VERSION} AS builder
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
ENV CI=true
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/apps/admin/node_modules ./apps/admin/node_modules
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/admin ./apps/admin
COPY packages ./packages

# NEXT_PUBLIC_* vars sont inlined au build. On accepte les valeurs via
# ARG (deux ARG par var pour pouvoir override en CI) puis on les expose
# en ENV avant le build.
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL:-http://localhost:3000/v1}

WORKDIR /repo/apps/admin
RUN pnpm exec next build

# ────────────────────────────────────────────────────────
# runner : juste le bundle standalone + .next/static + public/
# ────────────────────────────────────────────────────────
FROM node:${NODE_VERSION} AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -S reborn && adduser -S reborn -G reborn

# Standalone contient son propre minimal node_modules + server.js.
COPY --from=builder --chown=reborn:reborn /repo/apps/admin/.next/standalone ./
COPY --from=builder --chown=reborn:reborn /repo/apps/admin/.next/static ./apps/admin/.next/static
# Pas de public/ pour l'instant (l'app n'utilise pas d'assets statiques
# servis depuis /public). Decommente si on en ajoute :
# COPY --from=builder --chown=reborn:reborn /repo/apps/admin/public ./apps/admin/public

USER reborn

EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --quiet --spider http://localhost:3000/ || exit 1

# Le standalone server vit dans apps/admin/server.js a partir du repo root.
CMD ["node", "apps/admin/server.js"]
