import {
  ChatInputCommandInteraction,
  EmbedBuilder,
  SlashCommandBuilder,
  ThreadChannel,
} from "discord.js";
import { ApiError, decideWhitelist } from "../api-client.js";
import { extractIdFromThread } from "../thread-context.js";

export const data = new SlashCommandBuilder()
  .setName("whitelist")
  .setDescription("Gere une candidature whitelist (commandes staff).")
  .addSubcommand((sub) =>
    sub
      .setName("accept")
      .setDescription("Accepte la candidature liee au thread courant.")
      .addStringOption((o) =>
        o
          .setName("notes")
          .setDescription("Note optionnelle visible par le candidat.")
          .setMaxLength(2000),
      ),
  )
  .addSubcommand((sub) =>
    sub
      .setName("reject")
      .setDescription("Refuse definitivement la candidature liee au thread.")
      .addStringOption((o) =>
        o
          .setName("reason")
          .setDescription("Raison du refus (visible par le candidat).")
          .setRequired(true)
          .setMaxLength(2000),
      ),
  )
  .addSubcommand((sub) =>
    sub
      .setName("revise")
      .setDescription("Demande au candidat de modifier sa candidature.")
      .addStringOption((o) =>
        o
          .setName("notes")
          .setDescription("Ce qu'il doit corriger (visible par le candidat).")
          .setRequired(true)
          .setMaxLength(2000),
      ),
  );

export async function execute(interaction: ChatInputCommandInteraction) {
  const sub = interaction.options.getSubcommand();
  const applicationId = await extractIdFromThread(interaction, "application");
  if (!applicationId) {
    await interaction.reply({
      content:
        "Cette commande doit etre lancee dans un thread de candidature whitelist (cree par le bot).",
      ephemeral: true,
    });
    return;
  }

  const status =
    sub === "accept"
      ? "APPROVED"
      : sub === "reject"
        ? "REJECTED"
        : "NEEDS_REVISION";
  const notes =
    sub === "accept"
      ? interaction.options.getString("notes") ?? undefined
      : interaction.options.getString(sub === "reject" ? "reason" : "notes") ??
        undefined;

  await interaction.deferReply({ ephemeral: true });

  try {
    await decideWhitelist(applicationId, status, notes);
  } catch (err) {
    const msg = err instanceof ApiError ? `API ${err.status} : ${err.message}` : String(err);
    await interaction.editReply(`Echec : ${msg}`);
    return;
  }

  await interaction.editReply(
    `${VERB[sub]} OK — application \`${applicationId}\` mise a jour.`,
  );

  // Post un message de status dans le thread, visible par tout le staff.
  const thread = interaction.channel as ThreadChannel;
  const embed = new EmbedBuilder()
    .setColor(COLORS[status] ?? 0x6b7280)
    .setTitle(`${ICON[status]} ${TITLE[status]}`)
    .addFields({
      name: "Decision par",
      value: `<@${interaction.user.id}>`,
      inline: true,
    })
    .setTimestamp(new Date());
  if (notes) {
    embed.addFields({ name: "Notes", value: notes });
  }
  await thread.send({ embeds: [embed] });

  // Si accepte, archive le thread (liberant la sidebar).
  if (status === "APPROVED" || status === "REJECTED") {
    try {
      await thread.setArchived(true, `whitelist ${status.toLowerCase()}`);
    } catch {
      // Sans pas critique — tant pis pour l'archive auto.
    }
  }
}

const VERB: Record<string, string> = {
  accept: "Acceptee",
  reject: "Refusee",
  revise: "Revision demandee",
};

const TITLE: Record<string, string> = {
  APPROVED: "Candidature acceptee",
  REJECTED: "Candidature refusee",
  NEEDS_REVISION: "Revision demandee",
};

const ICON: Record<string, string> = {
  APPROVED: "✅",
  REJECTED: "❌",
  NEEDS_REVISION: "✏️",
};

const COLORS: Record<string, number> = {
  APPROVED: 0x16a34a,
  REJECTED: 0xef4444,
  NEEDS_REVISION: 0xf59e0b,
};
