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
  // Le thread peut contenir plusieurs embeds du bot apres une premiere
  // commande staff (status update du genre "Pris en charge"). On NE PEUT
  // PAS prendre le premier message du bot trouve par messages.find()
  // parce que la collection est ordonnee most-recent-first — on ferait
  // matcher l'embed de status, qui n'a pas de footer UUID.
  //
  // A la place, on filtre directement sur le pattern attendu du footer.
  // Seul l'embed initial cree par les webhooks pose ce footer, donc on
  // tombe sur le bon a coup sur, peu importe l'ordre.
  const messages = await thread.messages.fetch({ limit: 50 });
  const pattern = new RegExp(`^${prefix}\\s+([\\w-]+)$`);
  for (const m of messages.values()) {
    if (m.author.id !== interaction.client.user?.id) continue;
    for (const embed of m.embeds) {
      const footer = embed.footer?.text ?? "";
      const match = pattern.exec(footer);
      if (match?.[1]) return match[1];
    }
  }
  return null;
}
