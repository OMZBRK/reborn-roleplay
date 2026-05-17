import type { Client, Interaction } from "discord.js";
import { MessageFlags } from "discord.js";
import {
  handleWhitelistButton,
  handleWhitelistModal,
} from "./whitelist.js";

/**
 * Dispatch des interactions composants (boutons + modals) par prefixe
 * de customId. Format : `<kind>:<action>:<id>` ou `<kind>:<action>-modal:<id>`.
 *
 * Les slash commands sont toujours gerees ailleurs (index.ts).
 */
export async function dispatchComponentInteraction(
  client: Client,
  interaction: Interaction,
): Promise<void> {
  try {
    if (interaction.isButton()) {
      const prefix = interaction.customId.split(":")[0];
      if (prefix === "wl") {
        await handleWhitelistButton(client, interaction);
        return;
      }
      // (ticket: cf C4)
      await interaction.reply({
        content: `Composant inconnu : ${interaction.customId}`,
        flags: MessageFlags.Ephemeral,
      });
      return;
    }
    if (interaction.isModalSubmit()) {
      const prefix = interaction.customId.split(":")[0];
      if (prefix === "wl") {
        await handleWhitelistModal(interaction);
        return;
      }
      await interaction.reply({
        content: `Modal inconnu : ${interaction.customId}`,
        flags: MessageFlags.Ephemeral,
      });
    }
  } catch (err) {
    console.error("[interactions] crash :", err);
    if (
      (interaction.isButton() || interaction.isModalSubmit()) &&
      !interaction.replied &&
      !interaction.deferred
    ) {
      await interaction
        .reply({
          content: "Erreur interne — voir les logs bot.",
          flags: MessageFlags.Ephemeral,
        })
        .catch(() => {});
    }
  }
}
