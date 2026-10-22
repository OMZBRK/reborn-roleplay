import type { Client } from "discord.js";
import { config } from "../config.js";

/**
 * Journal du Discord JOUEURS : console, et salon staff `DISCORD_JOUEURS_LOG_CHANNEL_ID` s'il est renseigné.
 * Aucune mention ne notifie (allowedMentions vide) : les pseudos cités restent cliquables sans ping.
 */
export async function journal(client: Client, texte: string): Promise<void> {
  console.log(`[joueurs] ${texte.replace(/\*\*/g, "")}`);
  if (!config.joueursLogChannelId) return;
  try {
    const ch = await client.channels.fetch(config.joueursLogChannelId);
    if (ch && ch.isSendable()) await ch.send({ content: texte.slice(0, 1900), allowedMentions: { parse: [] } });
  } catch (err) {
    console.warn(`[joueurs] journal Discord indisponible : ${(err as Error).message}`);
  }
}
