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

/** Entier positif lu dans l'env, `defaut` si absent ou invalide. */
function entier(key: string, defaut: number): number {
  const n = Number(process.env[key]);
  return Number.isFinite(n) && n >= 0 && process.env[key]?.trim() ? Math.floor(n) : defaut;
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
  /** Salon ou le bot annonce les mises a jour de mods dispo (Modrinth).
   *  Fallback sur le salon tickets si non defini. */
  modsChannelId: process.env.DISCORD_MODS_CHANNEL_ID ?? require_("DISCORD_TICKETS_CHANNEL_ID"),
  /** Port HTTP sur lequel le bot ecoute les webhooks signes de l'API. */
  webhookPort: Number(process.env.BOT_HTTP_PORT ?? 3001),
  /** Secret partage avec l'API pour signer les webhooks (HMAC-SHA256). */
  webhookSecret: require_("REBORN_WEBHOOK_SECRET"),
  /** URL de base de l'API Reborn, pour les slash commands staff. */
  apiBaseUrl: process.env.REBORN_API_URL ?? "http://localhost:3000/v1",
  /** URL du panel staff Next.js — sert dans les DM bot pour pointer
   *  vers la candidature/le ticket dans le panel ("Voir dans le panel"). */
  adminBaseUrl: process.env.ADMIN_BASE_URL ?? "http://localhost:3002",
  /** Optionnel : notifs "Claude a fini de bosser" envoyees en DM a cet
   *  utilisateur Discord (son user ID). Prioritaire sur le salon. Vide = off. */
  claudeNotifyUserId: process.env.DISCORD_CLAUDE_NOTIFY_USER_ID ?? "",
  /** Optionnel : notifs "Claude a fini" postees dans ce salon. Utilise si
   *  aucun user DM n'est configure. Vide → fallback sur le salon tickets. */
  claudeNotifyChannelId: process.env.DISCORD_CLAUDE_NOTIFY_CHANNEL_ID ?? "",
  /** Serveur Discord STAFF (« RBN - STAFF »), cible des messages rédigés dans le panel
   *  (route /webhooks/staff-message). Vide = route désactivée (503). */
  staffGuildId: process.env.DISCORD_STAFF_GUILD_ID ?? "",
  /** Salon `annonces` du serveur staff (panel → « Annonces »). */
  staffAnnoncesChannelId: process.env.DISCORD_STAFF_ANNONCES_CHANNEL_ID ?? "",
  /** Salon `dev-updates` du serveur staff (panel → « Dev updates »). */
  staffDevUpdatesChannelId: process.env.DISCORD_STAFF_DEVUPDATES_CHANNEL_ID ?? "",
  /** Salon `build-updates` du serveur staff (panel → « Build updates »). */
  staffBuildUpdatesChannelId: process.env.DISCORD_STAFF_BUILDUPDATES_CHANNEL_ID ?? "",
  /** Optionnel : salon de test (ex. privé Direction) où « Aperçu » poste le message.
   *  Vide = l'aperçu renvoie seulement le rendu JSON, rien n'est posté. */
  staffApercuChannelId: process.env.DISCORD_STAFF_APERCU_CHANNEL_ID ?? "",
  /** Optionnel : rôle pingé par « @Testeur » en dev-updates. Vide = rôle nommé « Testeur ». */
  staffTesteurRoleId: process.env.DISCORD_STAFF_TESTEUR_ROLE_ID ?? "",

  // -------------------------------------------------------------------------------------------
  // Discord JOUEURS (« façon Ketto », docs/operations/DISCORD_JOUEURS.md §5). Tout est optionnel :
  // sans DISCORD_JOUEURS_GUILD_ID, rien ne tourne ; le Discord staff n'est jamais concerné.
  // -------------------------------------------------------------------------------------------
  /** Serveur Discord joueurs. Vide = accueil et salon piège désactivés. */
  joueursGuildId: process.env.DISCORD_JOUEURS_GUILD_ID ?? "",
  /** Salon `🪵` : un message par arrivée. Vide = accueil désactivé. */
  bienvenueChannelId: process.env.DISCORD_BIENVENUE_CHANNEL_ID ?? "",
  /** Optionnel : émoji du pied (« <:reborn:id> » pour le logo en émoji du serveur). Vide = pas d'émoji. */
  bienvenueEmoji: process.env.DISCORD_BIENVENUE_EMOJI ?? "",
  /** Couleur de la barre du cadre d'accueil : celle de l'avatar du bot (violet Reborn) par défaut. */
  bienvenueCouleur: process.env.DISCORD_BIENVENUE_COULEUR || "#6A2177",
  /** Au-delà de N arrivées sur 60 s, les arrivées sont regroupées en un message par minute (sans ping). */
  bienvenueSeuilGroupe: entier("DISCORD_BIENVENUE_SEUIL_GROUPE", 8),
  /** Au-delà de N arrivées sur 60 s (raid probable), l'accueil est suspendu 10 min et le journal alerte. */
  bienvenueSeuilRaid: entier("DISCORD_BIENVENUE_SEUIL_RAID", 25),
  /** Optionnel : salon staff du serveur joueurs où le bot écrit son journal (arrivées massives, piège). */
  joueursLogChannelId: process.env.DISCORD_JOUEURS_LOG_CHANNEL_ID ?? "",
  /** Optionnel : rôle « Staff » du serveur joueurs, jamais sanctionné par le piège. */
  joueursStaffRoleId: process.env.DISCORD_JOUEURS_STAFF_ROLE_ID ?? "",
  /** Salon piège `⚠️・ne-pas-écrire` (comptes piratés). Vide = piège désactivé (défaut). */
  piegeChannelId: process.env.DISCORD_PIEGE_CHANNEL_ID ?? "",
  /** Durée du bannissement du piège, en jours (0 = définitif). */
  piegeJours: entier("DISCORD_PIEGE_JOURS", 7),
  /** Messages du compte effacés partout au bannissement, en secondes (max 604800 = 7 j). */
  piegePurgeSecondes: Math.min(entier("DISCORD_PIEGE_PURGE_SECONDES", 3600), 604800),
  /** « 1 » = simulation : le message est effacé et journalisé, personne n'est banni. */
  piegeSimulation: ["1", "true", "oui"].includes((process.env.DISCORD_PIEGE_SIMULATION ?? "").trim().toLowerCase()),
};
