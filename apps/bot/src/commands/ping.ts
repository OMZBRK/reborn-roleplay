import {
  ChatInputCommandInteraction,
  SlashCommandBuilder,
} from "discord.js";

export const data = new SlashCommandBuilder()
  .setName("ping")
  .setDescription("Verifie que le bot Reborn est en ligne.");

export async function execute(interaction: ChatInputCommandInteraction) {
  const sent = await interaction.reply({
    content: "Pong! Calcul de la latence…",
    fetchReply: true,
  });
  const roundtrip = sent.createdTimestamp - interaction.createdTimestamp;
  const ws = interaction.client.ws.ping;
  await interaction.editReply(
    `Pong ! Latence aller-retour ${roundtrip} ms · websocket ${Math.round(ws)} ms.`,
  );
}
