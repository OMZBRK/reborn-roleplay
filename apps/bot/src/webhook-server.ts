import { createHmac, timingSafeEqual } from "node:crypto";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import {
  ChannelType,
  Client,
  EmbedBuilder,
  type TextChannel,
} from "discord.js";
import { config } from "./config.js";

/**
 * Serveur HTTP minimal qui ecoute les webhooks de l'API Reborn et cree
 * des threads Discord dans le salon configure (DISCORD_TICKETS_CHANNEL_ID).
 *
 * Auth : chaque requete doit porter un header `X-Reborn-Signature` qui
 * est l'HMAC-SHA256 du body brut, en hex, signe avec `REBORN_WEBHOOK_SECRET`.
 *
 * Endpoints :
 *   POST /webhooks/whitelist  → cree un thread "Whitelist · <pseudoMC>"
 *   POST /webhooks/tickets    → cree un thread "Ticket · <category> · <subject>"
 *   GET  /healthz             → 200 ok (pour readiness probe)
 */

interface WhitelistPayload {
  applicationId: string;
  userPseudo: string;
  userId: string;
  characterName: string;
  characterAge: number;
  background: string;
  motivation: string;
  discordUserId: string | null;
}

interface TicketPayload {
  ticketId: string;
  userPseudo: string;
  userId: string;
  category: string;
  subject: string;
  message: string;
  discordUserId: string | null;
}

export function startWebhookServer(client: Client) {
  const server = createServer(async (req, res) => {
    try {
      await handle(client, req, res);
    } catch (err) {
      console.error("[webhook] handler crash :", err);
      reply(res, 500, { error: "internal error" });
    }
  });
  server.listen(config.webhookPort, () => {
    console.log(`Webhook server : http://localhost:${config.webhookPort}`);
  });
  return server;
}

async function handle(client: Client, req: IncomingMessage, res: ServerResponse) {
  console.log(`[webhook] ${req.method} ${req.url}`);
  if (req.method === "GET" && (req.url === "/" || req.url === "/healthz")) {
    return reply(res, 200, {
      service: "reborn-bot",
      status: "online",
      hint: "Endpoints : POST /webhooks/whitelist, POST /webhooks/tickets (HMAC requis).",
    });
  }
  if (req.method !== "POST") {
    return reply(res, 405, { error: "method not allowed" });
  }

  const body = await readBody(req);
  if (!verifySignature(body, req.headers["x-reborn-signature"])) {
    console.warn(`[webhook] ${req.url} 401 signature invalide`);
    return reply(res, 401, { error: "signature invalide" });
  }

  let payload: unknown;
  try {
    payload = JSON.parse(body.toString("utf8"));
  } catch {
    return reply(res, 400, { error: "json invalide" });
  }

  const url = req.url ?? "";
  if (url === "/webhooks/whitelist") {
    const data = payload as WhitelistPayload;
    console.log(`[webhook] whitelist applicationId=${data.applicationId} pseudo=${data.userPseudo}`);
    try {
      const threadId = await postWhitelistThread(client, data);
      console.log(`[webhook] whitelist thread cree : ${threadId}`);
      return reply(res, 200, { threadId });
    } catch (err) {
      console.error(`[webhook] whitelist thread crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  if (url === "/webhooks/tickets") {
    const data = payload as TicketPayload;
    console.log(`[webhook] ticket ticketId=${data.ticketId} pseudo=${data.userPseudo}`);
    try {
      const threadId = await postTicketThread(client, data);
      console.log(`[webhook] ticket thread cree : ${threadId}`);
      return reply(res, 200, { threadId });
    } catch (err) {
      console.error(`[webhook] ticket thread crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  console.warn(`[webhook] 404 route inconnue : ${url}`);
  reply(res, 404, { error: "route inconnue" });
}

function readBody(req: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

function verifySignature(body: Buffer, header: string | string[] | undefined): boolean {
  const provided = Array.isArray(header) ? header[0] : header;
  if (!provided || typeof provided !== "string") return false;
  const expected = createHmac("sha256", config.webhookSecret).update(body).digest("hex");
  // timingSafeEqual exige des Buffers de meme taille — sinon il leve, donc
  // on borde avec une comparaison de longueur prealable.
  if (expected.length !== provided.length) return false;
  try {
    return timingSafeEqual(Buffer.from(expected, "hex"), Buffer.from(provided, "hex"));
  } catch {
    return false;
  }
}

function reply(res: ServerResponse, status: number, body: unknown) {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
}

async function postWhitelistThread(client: Client, p: WhitelistPayload): Promise<string> {
  const channel = await fetchTextChannel(client);
  // PublicThread : visible directement dans la sidebar du salon, lisible
  // par tout staff ayant acces au salon. Avant on utilisait PrivateThread
  // mais ca ne creait que le thread sans inviter personne — staff aurait
  // du le retrouver manuellement via l'API. Pour scaler en multi-staff
  // sensible, on switchera en PrivateThread + members.add(staffIds).
  const thread = await channel.threads.create({
    name: `Whitelist · ${p.userPseudo} · ${truncate(p.characterName, 32)}`,
    autoArchiveDuration: 10080,
    type: ChannelType.PublicThread,
    reason: `Whitelist application ${p.applicationId}`,
  });
  const embed = new EmbedBuilder()
    .setTitle(`Candidature whitelist — ${p.characterName}`)
    .setColor(0x3b5bdb)
    .addFields(
      { name: "Joueur Reborn", value: `\`${p.userPseudo}\``, inline: true },
      {
        name: "Discord",
        value: p.discordUserId ? `<@${p.discordUserId}>` : "*non lie*",
        inline: true,
      },
      { name: "Personnage", value: p.characterName, inline: true },
      { name: "Age IC", value: String(p.characterAge), inline: true },
      { name: "Background", value: clip(p.background, 1024) },
      { name: "Motivation", value: clip(p.motivation, 1024) },
    )
    .setFooter({ text: `application ${p.applicationId}` })
    .setTimestamp(new Date());
  await thread.send({ embeds: [embed] });
  return thread.id;
}

async function postTicketThread(client: Client, p: TicketPayload): Promise<string> {
  const channel = await fetchTextChannel(client);
  const thread = await channel.threads.create({
    name: `Ticket · ${TICKET_CATEGORY_LABEL[p.category] ?? p.category} · ${truncate(p.subject, 40)}`,
    autoArchiveDuration: 10080,
    type: ChannelType.PublicThread,
    reason: `Ticket ${p.ticketId}`,
  });
  const embed = new EmbedBuilder()
    .setTitle(`Ticket — ${p.subject}`)
    .setColor(TICKET_CATEGORY_COLOR[p.category] ?? 0x6b7280)
    .addFields(
      { name: "Auteur", value: `\`${p.userPseudo}\``, inline: true },
      {
        name: "Discord",
        value: p.discordUserId ? `<@${p.discordUserId}>` : "*non lie*",
        inline: true,
      },
      { name: "Categorie", value: TICKET_CATEGORY_LABEL[p.category] ?? p.category, inline: true },
      { name: "Message", value: clip(p.message, 1900) },
    )
    .setFooter({ text: `ticket ${p.ticketId}` })
    .setTimestamp(new Date());
  await thread.send({ embeds: [embed] });
  return thread.id;
}

async function fetchTextChannel(client: Client): Promise<TextChannel> {
  const channel = await client.channels.fetch(config.ticketsChannelId);
  if (!channel || channel.type !== ChannelType.GuildText) {
    throw new Error(
      `DISCORD_TICKETS_CHANNEL_ID ${config.ticketsChannelId} : pas un salon texte standard`,
    );
  }
  return channel;
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, max - 1) + "…";
}

function clip(s: string, max: number): string {
  if (s.length <= max) return s;
  return s.slice(0, max - 1) + "…";
}

const TICKET_CATEGORY_LABEL: Record<string, string> = {
  BUG: "Bug",
  REPORT_PLAYER: "Signalement",
  WHITELIST_APPEAL: "Appel whitelist",
  PURCHASE_ISSUE: "Achat",
  OTHER: "Autre",
};

const TICKET_CATEGORY_COLOR: Record<string, number> = {
  BUG: 0xef4444,
  REPORT_PLAYER: 0xf59e0b,
  WHITELIST_APPEAL: 0x3b5bdb,
  PURCHASE_ISSUE: 0x10b981,
  OTHER: 0x6b7280,
};
