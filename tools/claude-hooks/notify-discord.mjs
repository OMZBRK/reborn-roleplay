#!/usr/bin/env node
/**
 * Hook Claude Code → notification Discord via le bot Reborn.
 *
 * Branché sur les events `Stop` (Claude a fini son tour) et `Notification`
 * (Claude attend une action / input). Lit le payload de l'event sur stdin,
 * signe le corps en HMAC-SHA256 avec REBORN_WEBHOOK_SECRET (le même secret
 * que les autres webhooks bot), et POST sur /webhooks/claude-notify.
 *
 * Règle d'or : ce script ne DOIT jamais bloquer Claude. Toute erreur est
 * avalée et on sort en code 0. Timeout réseau court.
 *
 * Config (env ou racine .env) :
 *   REBORN_WEBHOOK_SECRET   (requis)   secret HMAC partagé avec le bot
 *   REBORN_BOT_WEBHOOK_URL  (optionnel, défaut http://localhost:3001)
 *
 * Wiring settings.json (voir tools/claude-hooks/README.md) :
 *   "Stop":        [{ "hooks": [{ "type": "command", "command": "node <abs>/notify-discord.mjs" }] }]
 *   "Notification":[{ "hooks": [{ "type": "command", "command": "node <abs>/notify-discord.mjs" }] }]
 */

import { createHmac } from "node:crypto";
import { readFileSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

/** Parse minimal d'un .env (KEY=VALUE), sans dépendance. */
function loadEnvFile(path) {
  const out = {};
  if (!existsSync(path)) return out;
  const text = readFileSync(path, "utf8");
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    let val = line.slice(eq + 1).trim();
    if (
      (val.startsWith('"') && val.endsWith('"')) ||
      (val.startsWith("'") && val.endsWith("'"))
    ) {
      val = val.slice(1, -1);
    }
    out[key] = val;
  }
  return out;
}

/** Résout une variable : env process d'abord, puis .env remonté depuis ce script. */
function resolveConfig() {
  // Claude Code lance le hook avec cwd = racine du projet → on cherche le
  // .env depuis le cwd EN PREMIER (marche même si le script est déployé
  // hors-repo dans ~/.claude/hooks). Puis fallback relatif au script.
  const cwd = process.cwd();
  const candidates = [
    resolve(cwd, ".env"),
    resolve(cwd, "../.env"),
    resolve(cwd, "../../.env"),
    resolve(__dirname, "../../.env"), // racine monorepo (tools/claude-hooks → racine)
    resolve(__dirname, "../.env"),
    resolve(__dirname, ".env"),
  ];
  let fileEnv = {};
  for (const c of candidates) {
    if (existsSync(c)) {
      fileEnv = loadEnvFile(c);
      break;
    }
  }
  const get = (k) => process.env[k] ?? fileEnv[k];
  return {
    secret: get("REBORN_WEBHOOK_SECRET"),
    botUrl: get("REBORN_BOT_WEBHOOK_URL") ?? "http://localhost:3001",
  };
}

function readStdin() {
  return new Promise((resolve) => {
    let data = "";
    if (process.stdin.isTTY) return resolve(""); // lancé à la main sans pipe
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (c) => (data += c));
    process.stdin.on("end", () => resolve(data));
    process.stdin.on("error", () => resolve(data));
  });
}

async function main() {
  const { secret, botUrl } = resolveConfig();
  if (!secret) {
    // Pas de secret → on ne peut pas signer. Silencieux (ne pas polluer Claude).
    return;
  }

  const stdin = await readStdin();
  let hook = {};
  try {
    hook = stdin ? JSON.parse(stdin) : {};
  } catch {
    hook = {};
  }

  // Champs standards d'un event de hook Claude Code (tolérant aux variantes).
  const event = hook.hook_event_name ?? hook.event ?? "Stop";
  const cwd = hook.cwd ?? process.cwd();
  const sessionId = hook.session_id ?? hook.sessionId ?? "";
  // `message` n'est présent que sur les events Notification.
  const summary = hook.message ?? hook.summary ?? undefined;

  const body = JSON.stringify({
    event,
    cwd,
    sessionId,
    summary,
  });

  const signature = createHmac("sha256", secret).update(body).digest("hex");

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 4000);
  try {
    await fetch(`${botUrl}/webhooks/claude-notify`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-reborn-signature": signature,
      },
      body,
      signal: controller.signal,
    });
  } catch {
    // Bot éteint / réseau : on ignore. La notif est best-effort.
  } finally {
    clearTimeout(timer);
  }
}

main()
  .catch(() => {})
  .finally(() => process.exit(0));
