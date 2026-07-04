import {
  ChatInputCommandInteraction,
  EmbedBuilder,
  PermissionFlagsBits,
  SlashCommandBuilder,
} from "discord.js";
import { ApiError, setPlayerRole, type RebornRole } from "../api-client.js";

/**
 * `/role set <pseudo> <role>` — change le rôle Reborn d'un joueur.
 *
 * Double garde-fou : la commande n'est visible qu'aux membres avec la perm
 * « Gérer le serveur » (gate Discord), et l'API exige que l'acteur (résolu
 * depuis son compte Discord lié) soit ADMIN+ et strictement au-dessus de la
 * cible et du rôle attribué. OWNER n'est pas proposé (réservé au SQL).
 */

const ROLE_CHOICES: { name: string; value: RebornRole }[] = [
  { name: "Joueur (retire la whitelist)", value: "PLAYER" },
  { name: "Whitelisté", value: "WHITELISTED" },
  { name: "Helper", value: "HELPER" },
  { name: "Reviewer whitelist", value: "WHITELIST_REVIEWER" },
  { name: "Modérateur", value: "MODERATOR" },
  { name: "Admin", value: "ADMIN" },
];

export const data = new SlashCommandBuilder()
  .setName("role")
  .setDescription("Gère le rôle Reborn d'un joueur (staff).")
  .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
  .addSubcommand((sub) =>
    sub
      .setName("set")
      .setDescription("Change le rôle Reborn d'un joueur.")
      .addStringOption((o) =>
        o
          .setName("pseudo")
          .setDescription("Pseudo Minecraft du joueur.")
          .setRequired(true)
          .setMaxLength(32),
      )
      .addStringOption((o) =>
        o
          .setName("role")
          .setDescription("Nouveau rôle à attribuer.")
          .setRequired(true)
          .addChoices(...ROLE_CHOICES),
      ),
  );

export async function execute(interaction: ChatInputCommandInteraction) {
  const pseudo = interaction.options.getString("pseudo", true);
  const role = interaction.options.getString("role", true) as RebornRole;

  await interaction.deferReply({ ephemeral: true });

  try {
    const res = await setPlayerRole(interaction.user.id, pseudo, role);
    if (!res.changed) {
      await interaction.editReply(
        `\`${res.minecraftUsername}\` a déjà le rôle **${res.role}** — rien à changer.`,
      );
      return;
    }
    const embed = new EmbedBuilder()
      .setColor(0x8b5cf6)
      .setTitle("Rôle mis à jour")
      .addFields(
        { name: "Joueur", value: `\`${res.minecraftUsername}\``, inline: true },
        {
          name: "Rôle",
          value: `${res.previousRole} → **${res.role}**`,
          inline: true,
        },
        { name: "Par", value: `<@${interaction.user.id}>`, inline: true },
      )
      .setTimestamp(new Date());
    await interaction.editReply({ content: "", embeds: [embed] });
  } catch (err) {
    const msg =
      err instanceof ApiError ? `API ${err.status} : ${err.message}` : String(err);
    await interaction.editReply(`Échec : ${msg}`);
  }
}
