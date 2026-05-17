import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonInteraction,
  ButtonStyle,
  EmbedBuilder,
  MessageFlags,
  type Client,
} from "discord.js";
import {
  ApiError,
  claimAssignment,
  releaseAssignment,
  setTicketStatus,
} from "../api-client.js";
import { config } from "../config.js";
import { packEmbedsForMessages, paginateLong } from "../embeds.js";

const PAYLOAD_CACHE = new Map<string, TicketPayloadFull>();

export interface TicketPayloadFull {
  ticketId: string;
  userPseudo: string;
  userId: string;
  discordUserId: string | null;
  category: string;
  subject: string;
  message: string;
}

export function cacheTicketPayload(payload: TicketPayloadFull) {
  PAYLOAD_CACHE.set(payload.ticketId, payload);
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

export function buildTicketAnnouncement(payload: TicketPayloadFull): {
  embeds: EmbedBuilder[];
  components: ActionRowBuilder<ButtonBuilder>[];
} {
  const color = TICKET_CATEGORY_COLOR[payload.category] ?? 0x6b7280;
  const teaser = new EmbedBuilder()
    .setTitle(`Nouveau ticket — ${payload.subject}`)
    .setColor(color)
    .setDescription(
      [
        `**${payload.userPseudo}**` +
          (payload.discordUserId ? ` · <@${payload.discordUserId}>` : ""),
        `**Categorie** : ${TICKET_CATEGORY_LABEL[payload.category] ?? payload.category}`,
        "",
        "_Clique sur **Prendre en charge** pour recevoir le contenu complet en DM._",
      ].join("\n"),
    )
    .setFooter({ text: `ticket ${payload.ticketId}` })
    .setTimestamp(new Date());

  const claimButton = new ButtonBuilder()
    .setCustomId(`tk:claim:${payload.ticketId}`)
    .setLabel("Prendre en charge")
    .setStyle(ButtonStyle.Primary)
    .setEmoji("📥");

  const row = new ActionRowBuilder<ButtonBuilder>().addComponents(claimButton);
  return { embeds: [teaser], components: [row] };
}

function buildTicketDmEmbeds(payload: TicketPayloadFull): EmbedBuilder[] {
  const color = TICKET_CATEGORY_COLOR[payload.category] ?? 0x6b7280;
  const header = new EmbedBuilder()
    .setTitle(`Ticket — ${payload.subject}`)
    .setColor(color)
    .addFields(
      { name: "Auteur", value: `\`${payload.userPseudo}\``, inline: true },
      {
        name: "Discord",
        value: payload.discordUserId
          ? `<@${payload.discordUserId}>`
          : "*non lie*",
        inline: true,
      },
      {
        name: "Categorie",
        value: TICKET_CATEGORY_LABEL[payload.category] ?? payload.category,
        inline: true,
      },
    )
    .setFooter({ text: `ticket ${payload.ticketId}` });

  return [header, ...paginateLong("Message initial", color, payload.message)];
}

function buildActionRow(ticketId: string): ActionRowBuilder<ButtonBuilder> {
  return new ActionRowBuilder<ButtonBuilder>().addComponents(
    new ButtonBuilder()
      .setCustomId(`tk:resolve:${ticketId}`)
      .setLabel("Marquer résolu")
      .setStyle(ButtonStyle.Success)
      .setEmoji("✅"),
    new ButtonBuilder()
      .setCustomId(`tk:close:${ticketId}`)
      .setLabel("Fermer")
      .setStyle(ButtonStyle.Danger)
      .setEmoji("🔒"),
    new ButtonBuilder()
      .setCustomId(`tk:release:${ticketId}`)
      .setLabel("Libérer")
      .setStyle(ButtonStyle.Secondary)
      .setEmoji("↩️"),
    new ButtonBuilder()
      .setURL(`${config.adminBaseUrl}/tickets/${ticketId}`)
      .setLabel("Ouvrir dans le panel")
      .setStyle(ButtonStyle.Link),
  );
}

// ── Handlers ────────────────────────────────────────────

export async function handleTicketButton(
  client: Client,
  interaction: ButtonInteraction,
): Promise<void> {
  const parts = interaction.customId.split(":");
  const action = parts[1];
  const ticketId = parts[2];
  if (!action || !ticketId) {
    await interaction.reply({
      content: "Bouton invalide.",
      flags: MessageFlags.Ephemeral,
    });
    return;
  }
  switch (action) {
    case "claim":
      return claimTicket(client, interaction, ticketId);
    case "release":
      return releaseTicketDm(interaction, ticketId);
    case "resolve":
      return updateStatus(interaction, ticketId, "RESOLVED");
    case "close":
      return updateStatus(interaction, ticketId, "CLOSED");
    default:
      await interaction.reply({
        content: `Action inconnue : ${action}`,
        flags: MessageFlags.Ephemeral,
      });
  }
}

async function claimTicket(
  _client: Client,
  interaction: ButtonInteraction,
  ticketId: string,
): Promise<void> {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  try {
    const result = await claimAssignment("ticket", ticketId, interaction.user.id);

    const payload = PAYLOAD_CACHE.get(ticketId);
    const dm = await interaction.user.createDM();
    if (payload) {
      const intro = new EmbedBuilder()
        .setColor(0x16a34a)
        .setDescription(
          `Tu as **pris en charge** le ticket de **${payload.userPseudo}**.\n` +
            `Continue la conversation dans le panel, puis utilise les boutons en bas pour cloturer.`,
        );
      await dm.send({ embeds: [intro] });
      const embeds = buildTicketDmEmbeds(payload);
      const batches = packEmbedsForMessages(embeds);
      for (let i = 0; i < batches.length; i++) {
        const isLast = i === batches.length - 1;
        await dm.send({
          embeds: batches[i],
          components: isLast ? [buildActionRow(ticketId)] : [],
        });
      }
    } else {
      const fallback = new EmbedBuilder()
        .setColor(0xf59e0b)
        .setTitle("Ticket pris en charge")
        .setDescription(
          `Le contenu complet n'est plus en cache (le bot a redemarre depuis la creation).\n` +
            `Ouvre le ticket dans le panel pour le contenu et la conversation :\n` +
            `→ ${config.adminBaseUrl}/tickets/${ticketId}`,
        );
      await dm.send({
        embeds: [fallback],
        components: [buildActionRow(ticketId)],
      });
    }

    // Edite le message public : retire le bouton + ajoute "Pris par @X".
    const claimedBy = result.assignee?.discordUsername ?? interaction.user.username;
    try {
      const original = interaction.message.embeds[0]?.toJSON() ?? {};
      const updated = new EmbedBuilder(original).setFooter({
        text: `${original.footer?.text ?? ""} · Pris par ${claimedBy}`,
      });
      await interaction.message.edit({ embeds: [updated], components: [] });
    } catch (err) {
      console.warn("[tk:claim] edit announcement failed :", err);
    }

    await interaction.editReply({
      content: `✅ Pris en charge — verifie tes DM.`,
    });
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Echec : ${message}` });
  }
}

async function releaseTicketDm(
  interaction: ButtonInteraction,
  ticketId: string,
): Promise<void> {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  try {
    await releaseAssignment("ticket", ticketId, interaction.user.id);
    try {
      await interaction.message.edit({ components: [] });
    } catch {
      /* ignore */
    }
    await interaction.editReply({
      content: "✅ Libéré.",
    });
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Echec : ${message}` });
  }
}

async function updateStatus(
  interaction: ButtonInteraction,
  ticketId: string,
  status: "RESOLVED" | "CLOSED",
): Promise<void> {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  try {
    await setTicketStatus(ticketId, status);
    if (interaction.message) {
      const recap = new EmbedBuilder()
        .setColor(status === "RESOLVED" ? 0x16a34a : 0x6b7280)
        .setTitle(status === "RESOLVED" ? "✅ Ticket résolu" : "🔒 Ticket fermé");
      try {
        await interaction.message.edit({ components: [] });
        const dm = await interaction.user.createDM();
        await dm.send({ embeds: [recap] });
      } catch {
        /* ignore */
      }
    }
    await interaction.editReply({
      content: `Statut mis a jour : ${status}.`,
    });
    if (status === "CLOSED") {
      PAYLOAD_CACHE.delete(ticketId);
    }
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Echec : ${message}` });
  }
}
