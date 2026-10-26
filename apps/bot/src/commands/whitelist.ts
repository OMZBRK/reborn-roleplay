import {
  ChatInputCommandInteraction,
  MessageFlags,
  PermissionFlagsBits,
  SlashCommandBuilder,
  type SlashCommandSubcommandBuilder,
} from "discord.js";
import { messageEchec, type DecisionEcrit, type PartieEcrit } from "../api-client.js";
import { deciderPartie, MSG_COMMENTAIRE_OBLIGATOIRE } from "../interactions/whitelist.js";
import { extractIdFromThread } from "../thread-context.js";

/**
 * `/whitelist hrp|rp decision:<…> [commentaire] [candidature]` — décide UNE
 * partie de l'écrit d'une candidature (CANDIDATURE_ET_DOUANE.md §6.1, lot H).
 * Plus de décision globale : HRP et RP se tranchent séparément, comme au panel.
 * L'oral ne se décide pas ici (douane en jeu, ou carte « Oral » du panel).
 *
 * Droits, en deux temps :
 *  - Discord : commande réservée par défaut aux membres qui peuvent déplacer
 *    des membres en vocal (le rôle `| Recrutement` et au-dessus) ; réglable
 *    dans Paramètres du serveur › Intégrations ;
 *  - API : le staff est transmis (`actorDiscordId`) ; l'API exige un compte
 *    Reborn lié de rôle suffisant, et inscrit la décision à l'audit.
 *
 * Candidature visée : l'option `candidature` (identifiant au pied de l'annonce
 * du salon staff), ou, sans elle, le fil de candidature historique où la
 * commande est lancée.
 */

/**
 * Identifiant seul, ou pied d'annonce collé tel quel, segments compris
 * (« application <id> · Pris par X · Statut : … ») : seul l'identifiant compte,
 * même tolérance que thread-context.ts.
 */
const SAISIE_CANDIDATURE =
  /^(?:application\s+)?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\s·\s.*)?$/i;

const DECISIONS: { name: string; value: DecisionEcrit }[] = [
  { name: "Accepter", value: "APPROVED" },
  { name: "À revoir", value: "NEEDS_REVISION" },
  { name: "Refuser", value: "REJECTED" },
];

function optionsDecision(sub: SlashCommandSubcommandBuilder): SlashCommandSubcommandBuilder {
  return sub
    .addStringOption((o) =>
      o
        .setName("decision")
        .setDescription("Décision sur cette partie de l'écrit.")
        .setRequired(true)
        .addChoices(...DECISIONS),
    )
    .addStringOption((o) =>
      o
        .setName("commentaire")
        .setDescription("Visible du candidat ; obligatoire pour « À revoir » et « Refuser ».")
        .setMaxLength(2000),
    )
    .addStringOption((o) =>
      o
        .setName("candidature")
        .setDescription("Identifiant de la candidature (pied de l'annonce) ; inutile dans un fil de candidature.")
        // Le pied complet (« application <id> · Pris par X · Statut : … ») peut être collé.
        .setMaxLength(300),
    );
}

export const data = new SlashCommandBuilder()
  .setName("whitelist")
  .setDescription("Décide une partie de l'écrit d'une candidature whitelist (staff).")
  .setDefaultMemberPermissions(PermissionFlagsBits.MoveMembers)
  .addSubcommand((sub) =>
    optionsDecision(sub.setName("hrp").setDescription("Décide la partie HRP (onglet Vous).")),
  )
  .addSubcommand((sub) =>
    optionsDecision(
      sub.setName("rp").setDescription("Décide la partie RP (personnage, village, histoire, voix)."),
    ),
  );

export async function execute(interaction: ChatInputCommandInteraction) {
  const partie: PartieEcrit = interaction.options.getSubcommand() === "hrp" ? "HRP" : "RP";
  const statut = interaction.options.getString("decision", true) as DecisionEcrit;
  const commentaire = interaction.options.getString("commentaire")?.trim() || undefined;
  const saisie = interaction.options.getString("candidature")?.trim();
  const idSaisi = saisie ? SAISIE_CANDIDATURE.exec(saisie)?.[1] : undefined;

  if (saisie && !idSaisi) {
    await interaction.reply({
      content: "Identifiant de candidature invalide : copie celui du pied de l'annonce (« application … »).",
      flags: MessageFlags.Ephemeral,
    });
    return;
  }
  const applicationId = idSaisi
    ? idSaisi.toLowerCase()
    : await extractIdFromThread(interaction, "application");
  if (!applicationId) {
    await interaction.reply({
      content:
        "Indique la candidature avec l'option `candidature` (identifiant au pied de l'annonce du salon staff), " +
        "ou lance la commande dans un fil de candidature.",
      flags: MessageFlags.Ephemeral,
    });
    return;
  }
  if (statut !== "APPROVED" && !commentaire) {
    await interaction.reply({ content: MSG_COMMENTAIRE_OBLIGATOIRE, flags: MessageFlags.Ephemeral });
    return;
  }

  await interaction.deferReply({ flags: MessageFlags.Ephemeral });

  let issue;
  try {
    issue = await deciderPartie(applicationId, partie, statut, commentaire, interaction.user.id);
  } catch (err) {
    await interaction.editReply(`❌ Échec : ${messageEchec(err)}`);
    return;
  }

  await interaction.editReply(`${issue.resume}\nCandidature \`${applicationId}\`.`);

  // Fil historique de cette candidature : le récapitulatif y reste visible du
  // staff. Le verrouillage du fil suit le statut renvoyé par l'API
  // (/webhooks/status-update). Dans le salon, l'annonce est mise à jour par
  // l'API : rien à poster.
  const channel = interaction.channel;
  if (!saisie && channel?.isThread()) {
    try {
      await channel.send({ embeds: [issue.recap] });
    } catch (err) {
      console.warn("[whitelist] récapitulatif du fil non posté :", err);
    }
  }
}
