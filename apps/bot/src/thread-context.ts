import {
  ChannelType,
  type ChatInputCommandInteraction,
  type ThreadChannel,
} from "discord.js";

/**
 * Lit le premier message du thread courant pour en extraire l'ID stocke
 * dans le footer de l'embed du bot ("application <uuid>" ou
 * "ticket <uuid>"). Retourne null si la commande n'est pas executee
 * dans un thread, ou si le thread n'a pas ete cree par le bot.
 */
export async function extractIdFromThread(
  interaction: ChatInputCommandInteraction,
  prefix: "application" | "ticket",
): Promise<string | null> {
  const channel = interaction.channel;
  if (!channel || !channel.isThread()) return null;
  const thread = channel as ThreadChannel;
  if (
    thread.parent?.type !== ChannelType.GuildText &&
    thread.parent?.type !== ChannelType.GuildAnnouncement
  ) {
    return null;
  }
  const messages = await thread.messages.fetch({ limit: 10 });
  const botMessage = messages.find(
    (m) =>
      m.author.id === interaction.client.user?.id && m.embeds.length > 0,
  );
  const footer = botMessage?.embeds[0]?.footer?.text ?? "";
  const match = new RegExp(`^${prefix}\\s+([\\w-]+)$`).exec(footer);
  return match?.[1] ?? null;
}
