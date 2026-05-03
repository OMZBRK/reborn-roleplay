import { config as loadDotenv } from "dotenv";
import { dirname, resolve } from "node:path";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

// Le bot tourne depuis apps/bot/ ; on cherche le .env de la racine du
// monorepo en remontant. dotenv n'echoue pas silencieusement si le
// fichier n'existe pas, mais on log explicitement pour faciliter le
// debug en dev.
const __dirname = dirname(fileURLToPath(import.meta.url));
const candidates = [
  resolve(__dirname, "../.env"),
  resolve(__dirname, "../../.env"),
  resolve(__dirname, "../../../.env"),
];
const envPath = candidates.find((p) => existsSync(p));
if (envPath) {
  loadDotenv({ path: envPath });
  console.log(`[bot] .env charge depuis ${envPath}`);
} else {
  console.warn("[bot] aucun .env trouve, lecture depuis process.env uniquement");
}

function require_(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new Error(`Variable d'env manquante : ${key}`);
  }
  return value;
}

export const config = {
  /** Token du bot (Discord Developer Portal -> Bot -> Reset Token). */
  botToken: require_("DISCORD_BOT_TOKEN"),
  /** Client ID de l'application Discord (utilise pour register les commandes). */
  clientId: require_("DISCORD_CLIENT_ID"),
  /** Guild ID du serveur Reborn (commandes deployees uniquement la). */
  guildId: require_("DISCORD_GUILD_ID"),
  /** Salon ou le bot poste les threads tickets / whitelist. */
  ticketsChannelId: require_("DISCORD_TICKETS_CHANNEL_ID"),
  /** Port HTTP sur lequel le bot ecoute les webhooks signes de l'API. */
  webhookPort: Number(process.env.BOT_HTTP_PORT ?? 3001),
  /** Secret partage avec l'API pour signer les webhooks (HMAC-SHA256). */
  webhookSecret: require_("REBORN_WEBHOOK_SECRET"),
  /** URL de base de l'API Reborn, pour les slash commands staff. */
  apiBaseUrl: process.env.REBORN_API_URL ?? "http://localhost:3000/v1",
};
