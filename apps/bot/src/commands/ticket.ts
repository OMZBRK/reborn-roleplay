import {
  ChatInputCommandInteraction,
  EmbedBuilder,
  SlashCommandBuilder,
  ThreadChannel,
} from "discord.js";
import { ApiError, setTicketStatus } from "../api-client.js";
import { extractIdFromThread } from "../thread-context.js";

export const data = new SlashCommandBuilder()
  .setName("ticket")
  .setDescription("Gere un ticket support (commandes staff).")
  .addSubcommand((sub) =>
    sub
      .setName("progress")
      .setDescription("Marque le ticket comme pris en charge."),
  )
  .addSubcommand((sub) =>
    sub
      .setName("resolve")
      .setDescription("Marque le ticket comme resolu (le user peut le rouvrir).")
      .addStringOption((o) =>
        o
          .setName("note")
          .setDescription("Note de resolution (visible par le user).")
          .setMaxLength(2000),
      ),
  )
  .addSubcommand((sub) =>
    sub
      .setName("close")
      .setDescription("Ferme definitivement le ticket.")
      .addStringOption((o) =>
        o
          .setName("note")
          .setDescription("Raison de fermeture (visible par le user).")
          .setMaxLength(2000),
      ),
  );

export async function execute(interaction: ChatInputCommandInteraction) {
  const sub = interaction.options.getSubcommand();
  const ticketId = await extractIdFromThread(interaction, "ticket");
  if (!ticketId) {
    await interaction.reply({
      content:
        "Cette commande doit etre lancee dans un thread de ticket (cree par le bot).",
      ephemeral: true,
    });
    return;
  }

  const status =
    sub === "progress"
      ? "IN_PROGRESS"
      : sub === "resolve"
        ? "RESOLVED"
        : "CLOSED";
  const note = interaction.options.getString("note") ?? undefined;

  await interaction.deferReply({ ephemeral: true });

  try {
    await setTicketStatus(ticketId, status);
  } catch (err) {
    const msg = err instanceof ApiError ? `API ${err.status} : ${err.message}` : String(err);
    await interaction.editReply(`Echec : ${msg}`);
    return;
  }

  await interaction.editReply(
    `${LABEL[status]} — ticket \`${ticketId}\` mis a jour.`,
  );

  const thread = interaction.channel as ThreadChannel;
  const embed = new EmbedBuilder()
    .setColor(COLORS[status] ?? 0x6b7280)
    .setTitle(`${ICON[status]} ${TITLE[status]}`)
    .addFields({
      name: "Action par",
      value: `<@${interaction.user.id}>`,
      inline: true,
    })
    .setTimestamp(new Date());
  if (note) embed.addFields({ name: "Note", value: note });
  await thread.send({ embeds: [embed] });

  if (status === "CLOSED" || status === "RESOLVED") {
    try {
      await thread.setArchived(true, `ticket ${status.toLowerCase()}`);
    } catch {
      // Best-effort.
    }
  }
}

const LABEL: Record<string, string> = {
  IN_PROGRESS: "Pris en charge",
  RESOLVED: "Resolu",
  CLOSED: "Ferme",
};

const TITLE: Record<string, string> = {
  IN_PROGRESS: "Ticket en cours",
  RESOLVED: "Ticket resolu",
  CLOSED: "Ticket ferme",
};

const ICON: Record<string, string> = {
  IN_PROGRESS: "🔧",
  RESOLVED: "✅",
  CLOSED: "🔒",
};

const COLORS: Record<string, number> = {
  IN_PROGRESS: 0x3b5bdb,
  RESOLVED: 0x16a34a,
  CLOSED: 0x6b7280,
};
