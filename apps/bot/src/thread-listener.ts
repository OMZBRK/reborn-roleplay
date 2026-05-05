import { createHmac } from "node:crypto";
import { Client, Events, type Message } from "discord.js";
import { config } from "./config.js";
import { extractIdFromMessage } from "./thread-context.js";

/**
 * Listener Discord messageCreate : detecte les messages staff postes dans
 * un thread whitelist/ticket cree par le bot, et les relaie a l'API
 * (POST /v1/staff/{whitelist,tickets}/:id/messages, signe HMAC).
 *
 * Filtres de declenchement :
 *  - Le message est dans un thread (sinon on ignore — chat de salon principal).
 *  - Le thread parent est notre `DISCORD_TICKETS_CHANNEL_ID`.
 *  - L'auteur n'est PAS le bot lui-meme (sinon boucle infinie : on a poste
 *    un relais, on s'auto-detecterait, on relaierait a l'API, qui pousserait
 *    a nouveau...). On verifie aussi le footer "from-launcher" sur les embeds
 *    du bot pour double-securiser.
 *  - Le message a un contenu non-vide OU des pieces jointes.
 *
 * Identification du thread (whitelist vs ticket) : on lit le footer du
 * premier embed du bot pour matcher "application <uuid>" ou "ticket <uuid>".
 */
export function startThreadListener(client: Client) {
  client.on(Events.MessageCreate, async (message) => {
    try {
      await handleMessage(message);
    } catch (err) {
      console.error("[thread-listener] crash :", err);
    }
  });
  console.log("[thread-listener] actif");
}

async function handleMessage(message: Message) {
  // Ignorer les messages hors thread.
  if (!message.channel.isThread()) return;
  const thread = message.channel;

  // Ignorer si le parent du thread n'est pas notre salon de tickets.
  if (thread.parentId !== config.ticketsChannelId) return;

  // Ignorer le bot lui-meme (sinon boucle).
  if (message.author.id === message.client.user?.id) return;

  // Ignorer les bots tiers.
  if (message.author.bot) return;

  // Ignorer les messages purement embed-only (les commands d'autres bots).
  // Discord.js considere un message comme contenu vide ssi content === '' && attachments.size === 0.
  if (!message.content && message.attachments.size === 0) return;

  // Identifier whitelist vs ticket via le footer de l'embed initial.
  const applicationId = await extractIdFromMessage(message, "application");
  const ticketId = applicationId ? null : await extractIdFromMessage(message, "ticket");
  if (!applicationId && !ticketId) {
    // Thread non géré (ni whitelist ni ticket Reborn), on ignore.
    return;
  }

  // Construire le payload pour l'API.
  const payload = {
    discordMessageId: message.id,
    authorDiscordId: message.author.id,
    authorName: message.member?.displayName ?? message.author.username,
    content: message.content,
    attachmentUrls: Array.from(message.attachments.values()).map((a) => a.url),
  };

  // config.apiBaseUrl contient deja `/v1` (cf config.ts default), donc on
  // ne le repete pas dans le path.
  const path = applicationId
    ? `/staff/whitelist/${applicationId}/messages`
    : `/staff/tickets/${ticketId}/messages`;

  await postSignedToApi(path, payload);
}

async function postSignedToApi(path: string, payload: unknown) {
  const body = JSON.stringify(payload);
  const signature = createHmac("sha256", config.webhookSecret)
    .update(body)
    .digest("hex");
  const url = `${config.apiBaseUrl.replace(/\/$/, "")}${path}`;
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-reborn-signature": signature,
      },
      body,
      signal: AbortSignal.timeout(5000),
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      console.warn(
        `[thread-listener] POST ${path} → ${res.status} ${text.slice(0, 200)}`,
      );
    }
  } catch (err) {
    console.warn(
      `[thread-listener] POST ${path} reseau echec : ${(err as Error).message}`,
    );
  }
}
