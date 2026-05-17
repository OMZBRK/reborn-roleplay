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
  discordUserId: string | null;
  dob: string;
  motivation: string;
  experience: string;
  availability: string;
  firstName: string;
  lastName: string;
  village: string;
  support: string | null;
  history: string;
  appearance: string;
  objectives: string;
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

// Payload de relais user→thread : l'API recoit un POST de l'utilisateur
// (POST /v1/whitelist/me/messages ou POST /v1/tickets/:id/messages) puis
// nous relaie le contenu pour qu'on l'affiche dans le thread Discord
// associe. On renvoie l'id du message Discord cree pour permettre la
// dedup cote API (eviter de re-relayer ce qu'on vient nous-meme de poster
// si messageCreate listener fires).
interface MessageRelayPayload {
  threadId: string;
  authorPseudo: string;
  content: string;
  // URLs des pieces jointes (CDN Discord ou autres). Vide si aucune.
  attachmentUrls?: string[];
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
  // Routes de relais user → discord thread. Symetriques pour whitelist
  // et tickets : meme payload, on poste juste un message dans le thread
  // pre-existant cote Discord (cree par les routes /webhooks/* ci-dessus).
  if (url === "/webhooks/whitelist-message" || url === "/webhooks/tickets-message") {
    const data = payload as MessageRelayPayload;
    console.log(`[webhook] relay ${url} thread=${data.threadId} from=${data.authorPseudo}`);
    try {
      const messageId = await postRelayMessage(client, data);
      return reply(res, 200, { messageId });
    } catch (err) {
      console.error(`[webhook] relay crash :`, err);
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
  const characterName = `${p.firstName} ${p.lastName}`.trim();
  const thread = await channel.threads.create({
    name: `Whitelist · ${p.userPseudo} · ${truncate(characterName, 32)}`,
    autoArchiveDuration: 10080,
    type: ChannelType.PublicThread,
    reason: `Whitelist application ${p.applicationId}`,
  });

  // Header : que les champs courts qui rentrent forcement dans 1024 chars
  // (identite + village + date + support). Les champs longs (motivation,
  // experience, disponibilite, histoire, apparence, objectifs) sont emis
  // comme embeds dedies via paginateLong : si > 4000 chars, split en
  // "X (1/2)", "X (2/2)" — Discord cap embed.description = 4096.
  const age = computeAge(p.dob);
  const dobDisplay = formatDateFr(p.dob);

  const embedHeader = new EmbedBuilder()
    .setTitle(`Candidature whitelist — ${characterName}`)
    .setColor(0x3b5bdb)
    .addFields(
      { name: "Joueur Reborn", value: `\`${p.userPseudo}\``, inline: true },
      {
        name: "Discord",
        value: p.discordUserId ? `<@${p.discordUserId}>` : "*non lie*",
        inline: true,
      },
      { name: "Village", value: p.village, inline: true },
      { name: "Personnage", value: characterName, inline: true },
      {
        name: "Date de naissance",
        value: age !== null ? `${dobDisplay} (${age} ans)` : dobDisplay,
        inline: true,
      },
      {
        name: "Support",
        value: p.support ? p.support : "*aucun*",
        inline: true,
      },
    )
    .setFooter({ text: `application ${p.applicationId}` })
    .setTimestamp(new Date());

  const narrative: EmbedBuilder[] = [
    ...paginateLong("Motivation", 0x3b5bdb, p.motivation),
    ...paginateLong("Expérience RP", 0x3b5bdb, p.experience),
    ...paginateLong("Disponibilité", 0x3b5bdb, p.availability),
    ...paginateLong("Histoire", 0x8b5cf6, p.history),
    ...paginateLong("Apparence et personnalité", 0x8b5cf6, p.appearance),
    ...paginateLong("Objectifs", 0x8b5cf6, p.objectives),
  ];

  // Discord cap : 10 embeds / message. On envoie le header seul puis
  // on pagine la narrative en groupes de 10. L'id du premier message
  // (header) est retourne pour la footer extraction cote thread-context.
  await thread.send({ embeds: [embedHeader] });
  for (const batch of chunk(narrative, 10)) {
    await thread.send({ embeds: batch });
  }
  return thread.id;
}

/**
 * Split un champ texte en autant d'embeds que necessaires pour respecter
 * la limite Discord embed.description (4096 chars). Le suffixe "(i/total)"
 * n'apparait que quand on a effectivement plusieurs pages.
 */
function paginateLong(
  title: string,
  color: number,
  content: string,
): EmbedBuilder[] {
  const trimmed = content.trim();
  if (trimmed.length === 0) {
    return [
      new EmbedBuilder()
        .setTitle(title)
        .setColor(color)
        .setDescription("*(vide)*"),
    ];
  }
  // 4000 chars / page : on garde une marge sous 4096 pour eviter les
  // crashes en cas d'echappement markdown qui rajoute des chars.
  const PAGE_SIZE = 4000;
  if (trimmed.length <= PAGE_SIZE) {
    return [
      new EmbedBuilder()
        .setTitle(title)
        .setColor(color)
        .setDescription(trimmed),
    ];
  }
  const total = Math.ceil(trimmed.length / PAGE_SIZE);
  const out: EmbedBuilder[] = [];
  for (let i = 0; i < total; i++) {
    const slice = trimmed.slice(i * PAGE_SIZE, (i + 1) * PAGE_SIZE);
    out.push(
      new EmbedBuilder()
        .setTitle(`${title} (${i + 1}/${total})`)
        .setColor(color)
        .setDescription(slice),
    );
  }
  return out;
}

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < arr.length; i += size) {
    out.push(arr.slice(i, i + size));
  }
  return out;
}

function computeAge(iso: string): number | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - d.getFullYear();
  const m = now.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < d.getDate())) age--;
  return age;
}

function formatDateFr(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const months = [
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
  ];
  return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

async function postTicketThread(client: Client, p: TicketPayload): Promise<string> {
  const channel = await fetchTextChannel(client);
  const thread = await channel.threads.create({
    name: `Ticket · ${TICKET_CATEGORY_LABEL[p.category] ?? p.category} · ${truncate(p.subject, 40)}`,
    autoArchiveDuration: 10080,
    type: ChannelType.PublicThread,
    reason: `Ticket ${p.ticketId}`,
  });
  const color = TICKET_CATEGORY_COLOR[p.category] ?? 0x6b7280;
  const header = new EmbedBuilder()
    .setTitle(`Ticket — ${p.subject}`)
    .setColor(color)
    .addFields(
      { name: "Auteur", value: `\`${p.userPseudo}\``, inline: true },
      {
        name: "Discord",
        value: p.discordUserId ? `<@${p.discordUserId}>` : "*non lie*",
        inline: true,
      },
      {
        name: "Categorie",
        value: TICKET_CATEGORY_LABEL[p.category] ?? p.category,
        inline: true,
      },
    )
    .setFooter({ text: `ticket ${p.ticketId}` })
    .setTimestamp(new Date());

  await thread.send({ embeds: [header] });
  // Le premier message peut etre long (description detaillee d'un bug,
  // historique d'un signalement, etc.). On le pagine au-dela de 4000 chars
  // au lieu de tronquer silencieusement comme avant.
  for (const batch of chunk(paginateLong("Message initial", color, p.message), 10)) {
    await thread.send({ embeds: batch });
  }
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

/**
 * Poste un message utilisateur dans un thread existant (relais user→discord).
 * Le format est volontairement simple : un embed compact avec le pseudo
 * Reborn et le contenu, et eventuellement les pieces jointes en lien.
 *
 * Le message a un footer `from-launcher <pseudo>` qui sert de signal au
 * listener messageCreate cote bot pour eviter de re-relayer ce qu'on vient
 * de poster nous-meme (sinon on creerait une boucle user→bot→API→bot).
 */
async function postRelayMessage(client: Client, p: MessageRelayPayload): Promise<string> {
  const thread = await client.channels.fetch(p.threadId);
  if (!thread || !thread.isThread()) {
    throw new Error(`thread ${p.threadId} introuvable ou pas un thread`);
  }
  const content = p.content || "*(piece jointe seule)*";
  const PAGE_SIZE = 4000;
  const pages: string[] =
    content.length <= PAGE_SIZE
      ? [content]
      : Array.from(
          { length: Math.ceil(content.length / PAGE_SIZE) },
          (_, i) => content.slice(i * PAGE_SIZE, (i + 1) * PAGE_SIZE),
        );

  // On envoie une suite d'embeds : un par page. Le footer `from-launcher`
  // sert au listener messageCreate du bot pour eviter une boucle de
  // relais ; on le pose sur chaque page pour qu'aucune ne soit "relayee
  // par accident".
  const embeds: EmbedBuilder[] = pages.map((page, i) =>
    new EmbedBuilder()
      .setAuthor({
        name:
          pages.length > 1
            ? `${p.authorPseudo} · joueur (${i + 1}/${pages.length})`
            : `${p.authorPseudo} · joueur`,
      })
      .setDescription(page)
      .setColor(0x3b5bdb)
      .setFooter({ text: `from-launcher ${p.authorPseudo}` })
      .setTimestamp(new Date()),
  );

  const attachments =
    p.attachmentUrls && p.attachmentUrls.length > 0
      ? p.attachmentUrls.map((u, i) => `[Pièce jointe ${i + 1}](${u})`).join("\n")
      : null;
  if (attachments) {
    // On accroche les pieces jointes au dernier embed pour qu'elles
    // soient visuellement contigues au texte de fin. `embeds` est
    // toujours non-vide ici (pages.length >= 1 garanti).
    const last = embeds[embeds.length - 1];
    if (last) last.addFields({ name: "Fichiers", value: attachments });
  }

  // L'id du PREMIER message est celui qu'on persiste cote API comme
  // discordMessageId (cf WhitelistMessagesService / TicketsService) ;
  // c'est lui qui sert d'ancre pour la dedup. Les pages suivantes sont
  // visuellement separees mais n'apparaissent pas en double cote launcher
  // car le user n'a poste qu'un seul message original.
  let firstId: string | null = null;
  for (const batch of chunk(embeds, 10)) {
    const sent = await thread.send({ embeds: batch });
    if (firstId === null) firstId = sent.id;
  }
  return firstId ?? "";
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, max - 1) + "…";
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
