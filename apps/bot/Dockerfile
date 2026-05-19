# syntax=docker/dockerfile:1.7
# Dockerfile bot Discord — meme pattern que apps/api (cf comments la-bas).
ARG NODE_VERSION=20-alpine

FROM node:${NODE_VERSION} AS deps
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
WORKDIR /repo
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/api/package.json apps/api/
COPY apps/bot/package.json apps/bot/
COPY apps/admin/package.json apps/admin/
COPY apps/launcher/package.json apps/launcher/
COPY packages/manifest-signer/package.json packages/manifest-signer/
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @reborn/bot...

FROM node:${NODE_VERSION} AS builder
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY --from=deps /repo/apps/bot/node_modules ./apps/bot/node_modules
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/bot ./apps/bot
COPY packages ./packages

WORKDIR /repo/apps/bot
RUN pnpm exec tsc

WORKDIR /repo
RUN --mount=type=cache,id=pnpm-store,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --filter @reborn/bot... --prod

FROM node:${NODE_VERSION} AS runner
RUN corepack enable && corepack prepare pnpm@10.33.2 --activate
WORKDIR /app
ENV NODE_ENV=production
ENV BOT_HTTP_PORT=3001

RUN addgroup -S reborn && adduser -S reborn -G reborn

COPY --from=builder --chown=reborn:reborn /repo/node_modules ./node_modules
COPY --from=builder --chown=reborn:reborn /repo/apps/bot/node_modules ./apps/bot/node_modules
COPY --from=builder --chown=reborn:reborn /repo/apps/bot/dist ./apps/bot/dist
COPY --from=builder --chown=reborn:reborn /repo/apps/bot/package.json ./apps/bot/
COPY --from=builder --chown=reborn:reborn /repo/package.json /repo/pnpm-workspace.yaml ./

USER reborn

EXPOSE 3001
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --quiet --spider http://localhost:3001/healthz || exit 1

WORKDIR /app/apps/bot
CMD ["node", "dist/index.js"]
