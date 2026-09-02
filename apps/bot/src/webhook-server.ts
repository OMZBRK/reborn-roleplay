import { createHmac, timingSafeEqual } from "node:crypto";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  ChannelType,
  Client,
  EmbedBuilder,
  type TextChannel,
} from "discord.js";
import { config } from "./config.js";
import { packEmbedsForMessages, paginateLong } from "./embeds.js";
import {
  buildTicketAnnouncement,
  cacheTicketPayload,
  type TicketPayloadFull,
} from "./interactions/ticket.js";
import {
  buildWhitelistAnnouncement,
  cacheWhitelistPayload,
  type WhitelistPayloadFull,
} from "./interactions/whitelist.js";

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

// WhitelistPayload extrait dans interactions/whitelist.ts sous le
// nom WhitelistPayloadFull (utilise aussi par le DM handler).

// TicketPayload extrait dans interactions/ticket.ts sous le nom
// TicketPayloadFull (utilise aussi par le DM handler).

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

// Notification d'un changement de statut declenche cote API (panel staff,
// launcher user, ou bot). On vient le refleter dans le thread Discord
// associe : embed annonce le changement, et on lock+archive le thread si
// le statut est terminal pour eviter que la conversation continue dans le
// vide cote Discord pendant que l'API/launcher la considere finie.
// Alerte securite (anomalie login p.ex.) postee dans le salon staff.
interface SecurityAlertPayload {
  userPseudo: string;
  userId: string;
  kind: string;
  reason: string;
  ip?: string;
  country?: string;
  userAgent?: string;
}

// Push d'un nouveau message du joueur vers le DM du staff assigne.
// Envoye par l'API quand le joueur poste dans le launcher et que la
// candidature/ticket a un staff assigne avec discordUserId lie.
interface DirectMessagePayload {
  discordUserId: string;
  context: {
    kind: "whitelist" | "ticket";
    entityId: string;
    subject?: string;
  };
  fromPseudo: string;
  content: string;
}

// Notif d'un claim/release pour qu'on edite le message public du salon
// staff : ajoute/retire le bouton "Prendre en charge" et marque
// "Pris par X" / "Disponible".
interface AssignmentChangedPayload {
  kind: "whitelist" | "ticket";
  entityId: string;
  messageId: string | null;
  action: "claimed" | "released";
  actorName: string;
}

interface StatusUpdatePayload {
  kind: "whitelist" | "ticket";
  // Legacy flow : id du thread Discord cree a la soumission.
  threadId?: string | null;
  // Nouveau flow C3 : id du message public dans le salon staff (avec
  // bouton Prendre en charge).
  messageId?: string | null;
  // AppStatus | TicketStatus | "DELETED" (cas withdraw/remove).
  status: string;
  // Qui a fait l'action : "@pseudo (staff)", "le joueur", "système", etc.
  actorName: string;
  // Notes/raison eventuelles (review notes pour whitelist, contexte
  // libre pour un close de ticket). Optionnel.
  reason?: string;
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
    const data = payload as WhitelistPayloadFull;
    console.log(
      `[webhook] whitelist applicationId=${data.applicationId} pseudo=${data.userPseudo}`,
    );
    try {
      // Nouveau flow C3 : message public dans le salon staff (pas un
      // thread) avec un bouton "Prendre en charge". Le contenu RP
      // detaille n'est pas dans le canal — il part en DM quand un staff
      // clique. On cache la payload pour pouvoir construire le DM sans
      // recharger l'API.
      cacheWhitelistPayload(data);
      const messageId = await postWhitelistAnnouncement(client, data);
      console.log(`[webhook] whitelist message cree : ${messageId}`);
      // Retourne aussi threadId=null pour clarte cote API logs.
      return reply(res, 200, { messageId, threadId: null });
    } catch (err) {
      console.error(`[webhook] whitelist message crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  if (url === "/webhooks/tickets") {
    const data = payload as TicketPayloadFull;
    console.log(`[webhook] ticket ticketId=${data.ticketId} pseudo=${data.userPseudo}`);
    try {
      cacheTicketPayload(data);
      const messageId = await postTicketAnnouncement(client, data);
      console.log(`[webhook] ticket message cree : ${messageId}`);
      return reply(res, 200, { messageId, threadId: null });
    } catch (err) {
      console.error(`[webhook] ticket message crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  if (url === "/webhooks/security-alert") {
    const data = payload as SecurityAlertPayload;
    console.log(
      `[webhook] security-alert ${data.kind} userPseudo=${data.userPseudo}`,
    );
    try {
      await postSecurityAlert(client, data);
      return reply(res, 200, { ok: true });
    } catch (err) {
      console.error(`[webhook] security-alert crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  if (url === "/webhooks/dm") {
    const data = payload as DirectMessagePayload;
    console.log(
      `[webhook] dm → ${data.discordUserId} (${data.context.kind} ${data.context.entityId})`,
    );
    try {
      await postDirectMessage(client, data);
      return reply(res, 200, { ok: true });
    } catch (err) {
      console.error(`[webhook] dm crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  if (url === "/webhooks/assignment-update") {
    const data = payload as AssignmentChangedPayload;
    console.log(
      `[webhook] assignment-update ${data.kind} ${data.entityId} → ${data.action} par ${data.actorName}`,
    );
    try {
      await postAssignmentUpdate(client, data);
      return reply(res, 200, { ok: true });
    } catch (err) {
      console.error(`[webhook] assignment-update crash :`, err);
      return reply(res, 500, { error: (err as Error).message });
    }
  }
  if (url === "/webhooks/status-update") {
    const data = payload as StatusUpdatePayload;
    console.log(
      `[webhook] status-update ${data.kind} thread=${data.threadId} → ${data.status}`,
    );
    try {
      await postStatusUpdate(client, data);
      return reply(res, 200, { ok: true });
    } catch (err) {
      console.error(`[webhook] status-update crash :`, err);
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
  if (url === "/webhooks/mods-update") {
    const data = payload as ModsUpdatePayload;
    console.log(`[webhook] mods-update count=${data.count} version=${data.version ?? "?"}`);
    try {
      await postModsUpdate(client, data);
      return reply(res, 200, { ok: true });
    } catch (err) {
      console.error(`[webhook] mods-update crash :`, err);
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

async function postWhitelistAnnouncement(
  client: Client,
  payload: WhitelistPayloadFull,
): Promise<string> {
  const channel = await fetchTextChannel(client);
  const { embeds, components } = buildWhitelistAnnouncement(payload);
  const sent = await channel.send({ embeds, components });
  return sent.id;
}

// Helpers paginateLong / packEmbedsForMessages / embedCharSize /
// computeAge / formatDateFr / truncate sont desormais dans embeds.ts —
// partages entre ce module et interactions/whitelist.ts.

async function postTicketAnnouncement(
  client: Client,
  payload: TicketPayloadFull,
): Promise<string> {
  const channel = await fetchTextChannel(client);
  const { embeds, components } = buildTicketAnnouncement(payload);
  const sent = await channel.send({ embeds, components });
  return sent.id;
}

async function fetchTextChannel(
  client: Client,
  channelId: string = config.ticketsChannelId,
): Promise<TextChannel> {
  const channel = await client.channels.fetch(channelId);
  if (!channel || channel.type !== ChannelType.GuildText) {
    throw new Error(`salon ${channelId} : pas un salon texte standard`);
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
  for (const batch of packEmbedsForMessages(embeds)) {
    const sent = await thread.send({ embeds: batch });
    if (firstId === null) firstId = sent.id;
  }
  return firstId ?? "";
}

interface StatusDescriptor {
  label: string;
  color: number;
  terminal: boolean; // si true → thread locked + archived apres post
}

const WHITELIST_STATUS: Record<string, StatusDescriptor> = {
  PENDING: { label: "En attente", color: 0x6b7280, terminal: false },
  APPROVED: { label: "Acceptée", color: 0x16a34a, terminal: true },
  REJECTED: { label: "Refusée", color: 0xef4444, terminal: true },
  NEEDS_REVISION: {
    label: "Révision demandée",
    color: 0xf59e0b,
    terminal: false,
  },
  DELETED: { label: "Retirée par le joueur", color: 0x6b7280, terminal: true },
};

const TICKET_STATUS: Record<string, StatusDescriptor> = {
  OPEN: { label: "Ouvert", color: 0x3b5bdb, terminal: false },
  IN_PROGRESS: { label: "En cours", color: 0xf59e0b, terminal: false },
  RESOLVED: { label: "Résolu", color: 0x16a34a, terminal: false },
  CLOSED: { label: "Fermé", color: 0x6b7280, terminal: true },
  DELETED: { label: "Supprimé par le joueur", color: 0x6b7280, terminal: true },
};

async function postStatusUpdate(
  client: Client,
  p: StatusUpdatePayload,
): Promise<void> {
  const table = p.kind === "whitelist" ? WHITELIST_STATUS : TICKET_STATUS;
  const desc = table[p.status] ?? {
    label: p.status,
    color: 0x6b7280,
    terminal: false,
  };

  const recap = new EmbedBuilder()
    .setTitle(`Statut → ${desc.label}`)
    .setColor(desc.color)
    .setDescription(`Mis à jour par **${p.actorName}**.`)
    .setTimestamp(new Date());
  if (p.reason) {
    recap.addFields({ name: "Notes", value: p.reason.slice(0, 1024) });
  }

  // Nouveau flow C3 : messageId set → on edite le message public dans
  // le salon staff (badge statut + footer + retire les boutons). On
  // n'archive plus rien parce que ce n'est plus un thread.
  if (p.messageId) {
    try {
      const fetched = await fetchChannelMessage(client, p.messageId);
      if (fetched) {
        const original = fetched.embeds[0]?.toJSON() ?? {};
        const updatedHeader = new EmbedBuilder(original)
          .setColor(desc.color)
          .setFooter({
            text: `${original.footer?.text ?? ""} · Statut : ${desc.label} (${p.actorName})`,
          });
        await fetched.edit({
          embeds: [updatedHeader, recap],
          components: [], // retire le bouton "Prendre en charge" s'il restait
        });
        return;
      }
      console.warn(`[status-update] message ${p.messageId} introuvable, skip.`);
    } catch (err) {
      console.warn(`[status-update] edit message echec :`, err);
    }
    return;
  }

  // Legacy : threadId set → ancien comportement (poste recap + lock).
  if (p.threadId) {
    const channel = await client.channels.fetch(p.threadId);
    if (!channel || !channel.isThread()) {
      console.warn(`[status-update] thread ${p.threadId} introuvable, skip.`);
      return;
    }
    const embedLegacy = new EmbedBuilder(recap.toJSON()).setDescription(
      `Mis à jour par **${p.actorName}**.${
        desc.terminal
          ? "\n\nLe thread est désormais verrouillé : la conversation continue côté launcher / panel si besoin."
          : ""
      }`,
    );
    await channel.send({ embeds: [embedLegacy] });
    if (desc.terminal && !channel.locked) {
      try {
        await channel.setLocked(
          true,
          `Reborn ${p.kind} status ${p.status} par ${p.actorName}`,
        );
        await channel.setArchived(
          true,
          `Reborn ${p.kind} status ${p.status} par ${p.actorName}`,
        );
      } catch (err) {
        console.warn(`[status-update] lock/archive echec :`, err);
      }
    }
    return;
  }

  console.warn(`[status-update] aucun threadId ni messageId fourni, skip.`);
}

/**
 * Resout un message Discord par son ID dans le salon staff configure.
 * Discord n'expose pas un endpoint global fetch-by-id, on doit specifier
 * un channel. On tape le salon principal staff par defaut.
 */
async function fetchChannelMessage(client: Client, messageId: string) {
  try {
    const channel = await fetchTextChannel(client);
    return await channel.messages.fetch(messageId);
  } catch {
    return null;
  }
}

/**
 * Edite le message public d'annonce dans le salon staff selon l'action :
 *   - "claimed"  : retire le bouton "Prendre en charge", ajoute le footer
 *     "Pris par X" en remplacant un eventuel "Disponible".
 *   - "released" : remet le bouton "Prendre en charge", remplace le footer
 *     par "Disponible (libéré par X)".
 *
 * Le message est resolu via fetchChannelMessage(messageId). Si introuvable
 * (supprime manuellement, ancienne candidature sans messageId, etc.), on
 * log et on no-op.
 */
async function postSecurityAlert(
  client: Client,
  p: SecurityAlertPayload,
): Promise<void> {
  const channel = await fetchTextChannel(client);
  const embed = new EmbedBuilder()
    .setColor(0xf59e0b)
    .setTitle(`⚠️ Alerte sécurité — ${p.kind}`)
    .setDescription(`**${p.userPseudo}** : ${p.reason}`)
    .setTimestamp(new Date());
  if (p.country) embed.addFields({ name: 'Pays', value: p.country, inline: true });
  if (p.ip) embed.addFields({ name: 'IP', value: `\`${p.ip}\``, inline: true });
  if (p.userAgent)
    embed.addFields({ name: 'User-Agent', value: p.userAgent.slice(0, 1024) });
  embed.setFooter({ text: `user ${p.userId}` });
  await channel.send({ embeds: [embed] });
}

/** Payload de l'annonce « mises a jour de mods disponibles » (API → bot). */
interface ModsUpdatePayload {
  /** Nombre de mods avec une version plus recente compatible. */
  count: number;
  /** Version de manifeste candidate suggeree (optionnel, info). */
  version?: string;
  /** Lignes lisibles « slug : ancienne → nouvelle (type) ». */
  mods: string[];
}

/**
 * Poste dans le salon mods (fallback tickets) une annonce des updates de mods
 * detectees par le cron API. Pilote humain : le staff lance ensuite `prepare` +
 * `publish` en local (cle de signature hors-ligne).
 */
async function postModsUpdate(client: Client, p: ModsUpdatePayload): Promise<void> {
  const channel = await fetchTextChannel(client, config.modsChannelId);
  const embed = new EmbedBuilder()
    .setColor(0x22c55e)
    .setTitle(`🔄 ${p.count} mise${p.count > 1 ? "s" : ""} à jour de mods disponible${p.count > 1 ? "s" : ""}`)
    .setDescription(p.mods.slice(0, 25).join("\n").slice(0, 4096) || "—")
    .setFooter({ text: "Lance `prepare` + `publish` en local pour valider (clé hors-ligne)." })
    .setTimestamp(new Date());
  if (p.version) embed.addFields({ name: "Manifeste candidat", value: `v${p.version}`, inline: true });
  await channel.send({ embeds: [embed] });
}

async function postDirectMessage(
  client: Client,
  p: DirectMessagePayload,
): Promise<void> {
  const user = await client.users.fetch(p.discordUserId);
  const dm = await user.createDM();
  const panelPath = p.context.kind === "whitelist" ? "whitelist" : "tickets";
  const panelUrl = `${config.adminBaseUrl}/${panelPath}/${p.context.entityId}`;

  const embed = new EmbedBuilder()
    .setColor(0x3b5bdb)
    .setAuthor({ name: `${p.fromPseudo} · ${p.context.kind === "whitelist" ? "candidature" : "ticket"}` })
    .setTitle(p.context.subject ?? "Nouveau message")
    .setDescription(p.content.length > 4000 ? p.content.slice(0, 4000) + "…" : p.content)
    .setTimestamp(new Date())
    .setFooter({ text: "Répondre depuis le panel" });

  const openButton = new ButtonBuilder()
    .setURL(panelUrl)
    .setLabel("Ouvrir dans le panel")
    .setStyle(ButtonStyle.Link)
    .setEmoji("💬");

  const row = new ActionRowBuilder<ButtonBuilder>().addComponents(openButton);

  await dm.send({ embeds: [embed], components: [row] });
}

async function postAssignmentUpdate(
  client: Client,
  p: AssignmentChangedPayload,
): Promise<void> {
  if (!p.messageId) {
    console.warn(`[assignment-update] pas de messageId, skip.`);
    return;
  }
  const msg = await fetchChannelMessage(client, p.messageId);
  if (!msg) {
    console.warn(`[assignment-update] message ${p.messageId} introuvable, skip.`);
    return;
  }
  const original = msg.embeds[0]?.toJSON() ?? {};
  // On nettoie un eventuel suffixe " · Pris par ..." ou " · Disponible
  // ..." accumule des cycles precedents pour eviter la concat infinie.
  const baseFooter = (original.footer?.text ?? "")
    .replace(/\s·\s(?:Pris par|Disponible).*$/u, "")
    .trim();

  if (p.action === "claimed") {
    const updated = new EmbedBuilder(original).setFooter({
      text: `${baseFooter} · Pris par ${p.actorName}`,
    });
    await msg.edit({ embeds: [updated], components: [] });
    return;
  }

  // released : re-attache le bouton de claim et indique "Disponible".
  const prefix = p.kind === "whitelist" ? "wl" : "tk";
  const claimButton = new ButtonBuilder()
    .setCustomId(`${prefix}:claim:${p.entityId}`)
    .setLabel("Prendre en charge")
    .setStyle(ButtonStyle.Primary)
    .setEmoji("📥");
  const row = new ActionRowBuilder<ButtonBuilder>().addComponents(claimButton);
  const updated = new EmbedBuilder(original).setFooter({
    text: `${baseFooter} · Disponible (libéré par ${p.actorName})`,
  });
  await msg.edit({ embeds: [updated], components: [row] });
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
