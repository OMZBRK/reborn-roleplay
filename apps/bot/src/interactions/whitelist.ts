import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonInteraction,
  ButtonStyle,
  EmbedBuilder,
  MessageFlags,
  ModalBuilder,
  ModalSubmitInteraction,
  TextInputBuilder,
  TextInputStyle,
  type Client,
} from "discord.js";
import { ApiError, claimAssignment, decideWhitelist, releaseAssignment } from "../api-client.js";
import { config } from "../config.js";
import {
  computeAge,
  formatDateFr,
  packEmbedsForMessages,
  paginateLong,
  truncate,
} from "../embeds.js";

/**
 * Cache en RAM des payloads whitelist recus via /webhooks/whitelist :
 * sert a re-construire le contenu complet (les 6 sections RP) quand un
 * staff clique sur "Prendre en charge" → DM. Pas de TTL : on garde
 * jusqu'au prochain restart du bot. Si cache miss (restart), le DM
 * tombe en fallback "voir le panel".
 */
const PAYLOAD_CACHE = new Map<string, WhitelistPayloadFull>();

export interface WhitelistPayloadFull {
  applicationId: string;
  userPseudo: string;
  userId: string;
  discordUserId: string | null;
  dob: string;
  motivation: string;
  experience: string;
  availability: string;
  firstName: string;
  lastName: string;
  village: string;
  support: string | null;
  history: string;
  appearance: string;
  objectives: string;
}

export function cacheWhitelistPayload(payload: WhitelistPayloadFull) {
  PAYLOAD_CACHE.set(payload.applicationId, payload);
}

/**
 * Construit le message public d'annonce dans le salon staff : teaser
 * compact + bouton "Prendre en charge". Le contenu RP detaille n'est
 * PAS dans le canal public : le staff doit prendre en charge pour le
 * voir en DM, ce qui evite (a) le bruit dans le salon et (b) le double-
 * traitement par plusieurs staff en meme temps.
 */
export function buildWhitelistAnnouncement(payload: WhitelistPayloadFull): {
  embeds: EmbedBuilder[];
  components: ActionRowBuilder<ButtonBuilder>[];
} {
  const characterName = `${payload.firstName} ${payload.lastName}`.trim();
  const age = computeAge(payload.dob);
  const dobDisplay = formatDateFr(payload.dob);
  const teaser = new EmbedBuilder()
    .setTitle(`Nouvelle candidature whitelist — ${characterName}`)
    .setColor(0x3b5bdb)
    .setDescription(
      [
        `**${payload.userPseudo}**` +
          (payload.discordUserId ? ` · <@${payload.discordUserId}>` : ""),
        `**Village** : ${payload.village}`,
        `**Date de naissance** : ${dobDisplay}` +
          (age !== null ? ` (${age} ans)` : ""),
        "",
        "_Clique sur **Prendre en charge** pour recevoir le dossier complet en DM._",
      ].join("\n"),
    )
    .setFooter({ text: `application ${payload.applicationId}` })
    .setTimestamp(new Date());

  const claimButton = new ButtonBuilder()
    .setCustomId(`wl:claim:${payload.applicationId}`)
    .setLabel("Prendre en charge")
    .setStyle(ButtonStyle.Primary)
    .setEmoji("📥");

  const row = new ActionRowBuilder<ButtonBuilder>().addComponents(claimButton);
  return { embeds: [teaser], components: [row] };
}

/**
 * Construit la sequence d'embeds qui sera envoyee en DM au staff
 * apres le claim : header identite + 6 sections RP paginees.
 */
function buildWhitelistDmEmbeds(payload: WhitelistPayloadFull): EmbedBuilder[] {
  const characterName = `${payload.firstName} ${payload.lastName}`.trim();
  const age = computeAge(payload.dob);
  const dobDisplay = formatDateFr(payload.dob);

  const header = new EmbedBuilder()
    .setTitle(`Candidature — ${characterName}`)
    .setColor(0x3b5bdb)
    .addFields(
      { name: "Joueur Reborn", value: `\`${payload.userPseudo}\``, inline: true },
      {
        name: "Discord",
        value: payload.discordUserId ? `<@${payload.discordUserId}>` : "*non lie*",
        inline: true,
      },
      { name: "Village", value: payload.village, inline: true },
      { name: "Personnage", value: characterName, inline: true },
      {
        name: "Date de naissance",
        value: age !== null ? `${dobDisplay} (${age} ans)` : dobDisplay,
        inline: true,
      },
      {
        name: "Support",
        value: payload.support ? payload.support : "*aucun*",
        inline: true,
      },
    )
    .setFooter({ text: `application ${payload.applicationId}` });

  return [
    header,
    ...paginateLong("Motivation", 0x3b5bdb, payload.motivation),
    ...paginateLong("Expérience RP", 0x3b5bdb, payload.experience),
    ...paginateLong("Disponibilité", 0x3b5bdb, payload.availability),
    ...paginateLong("Histoire", 0x8b5cf6, payload.history),
    ...paginateLong("Apparence et personnalité", 0x8b5cf6, payload.appearance),
    ...paginateLong("Objectifs", 0x8b5cf6, payload.objectives),
  ];
}

/** Les 3 boutons d'action proposes dans le DM du staff. */
function buildDecisionRow(applicationId: string): ActionRowBuilder<ButtonBuilder> {
  return new ActionRowBuilder<ButtonBuilder>().addComponents(
    new ButtonBuilder()
      .setCustomId(`wl:accept:${applicationId}`)
      .setLabel("Accepter")
      .setStyle(ButtonStyle.Success)
      .setEmoji("✅"),
    new ButtonBuilder()
      .setCustomId(`wl:revise:${applicationId}`)
      .setLabel("Demander une révision")
      .setStyle(ButtonStyle.Secondary)
      .setEmoji("📝"),
    new ButtonBuilder()
      .setCustomId(`wl:reject:${applicationId}`)
      .setLabel("Refuser")
      .setStyle(ButtonStyle.Danger)
      .setEmoji("❌"),
  );
}

function buildReleaseRow(applicationId: string): ActionRowBuilder<ButtonBuilder> {
  return new ActionRowBuilder<ButtonBuilder>().addComponents(
    new ButtonBuilder()
      .setCustomId(`wl:release:${applicationId}`)
      .setLabel("Libérer (passer la main)")
      .setStyle(ButtonStyle.Secondary)
      .setEmoji("↩️"),
    new ButtonBuilder()
      .setURL(`${config.adminBaseUrl}/whitelist/${applicationId}`)
      .setLabel("Ouvrir dans le panel")
      .setStyle(ButtonStyle.Link)
      .setEmoji("🪟"),
  );
}

// ── Handlers ────────────────────────────────────────────

export async function handleWhitelistButton(
  client: Client,
  interaction: ButtonInteraction,
): Promise<void> {
  const parts = interaction.customId.split(":");
  // parts = ['wl', action, applicationId]
  const action = parts[1];
  const applicationId = parts[2];
  if (!action || !applicationId) {
    await interaction.reply({
      content: "Bouton invalide (customId malformé).",
      flags: MessageFlags.Ephemeral,
    });
    return;
  }

  switch (action) {
    case "claim":
      return claimWhitelist(client, interaction, applicationId);
    case "release":
      return releaseWhitelistDm(interaction, applicationId);
    case "accept":
    case "reject":
    case "revise":
      return openDecisionModal(interaction, action, applicationId);
    default:
      await interaction.reply({
        content: `Action inconnue : ${action}`,
        flags: MessageFlags.Ephemeral,
      });
  }
}

async function claimWhitelist(
  client: Client,
  interaction: ButtonInteraction,
  applicationId: string,
): Promise<void> {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });

  try {
    const result = await claimAssignment(
      "whitelist",
      applicationId,
      interaction.user.id,
    );

    // 1. DM le staff avec le contenu complet + boutons de decision.
    const payload = PAYLOAD_CACHE.get(applicationId);
    const dm = await interaction.user.createDM();
    if (payload) {
      const embeds = buildWhitelistDmEmbeds(payload);
      const batches = packEmbedsForMessages(embeds);
      // Premier message DM avec l'entete contextuelle + les boutons en
      // pied. Si plusieurs batches, les suivants n'ont que les embeds.
      const intro = new EmbedBuilder()
        .setColor(0x16a34a)
        .setDescription(
          `Tu as **pris en charge** la candidature de **${payload.userPseudo}**.\n` +
            `Voici le dossier complet — utilise les boutons en bas pour decider, ou continue la conversation dans le panel staff.`,
        );
      await dm.send({ embeds: [intro] });
      for (let i = 0; i < batches.length; i++) {
        const isLast = i === batches.length - 1;
        await dm.send({
          embeds: batches[i],
          components: isLast
            ? [buildDecisionRow(applicationId), buildReleaseRow(applicationId)]
            : [],
        });
      }
    } else {
      // Cache miss (bot restart) — fallback minimal mais fonctionnel.
      const fallback = new EmbedBuilder()
        .setColor(0xf59e0b)
        .setTitle("Candidature prise en charge")
        .setDescription(
          `Le contenu complet n'est plus en cache (le bot a redemarre depuis la soumission).\n` +
            `Tu peux consulter et decider depuis le panel staff :\n` +
            `→ ${config.adminBaseUrl ?? "(ADMIN_BASE_URL non configure)"}/whitelist/${applicationId}\n\n` +
            `Ou utilise les boutons ci-dessous pour decider directement.`,
        );
      await dm.send({
        embeds: [fallback],
        components: [buildDecisionRow(applicationId), buildReleaseRow(applicationId)],
      });
    }

    // 2. Edite le message public : remplace le bouton par un badge
    //    "Pris par @X" et retire les actions.
    const claimedBy = result.assignee?.discordUsername ?? interaction.user.username;
    await editAnnouncementClaimed(interaction, claimedBy);

    await interaction.editReply({
      content: `✅ Pris en charge — verifie tes DM.`,
    });
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Echec : ${message}` });
  }
}

async function releaseWhitelistDm(
  interaction: ButtonInteraction,
  applicationId: string,
): Promise<void> {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  try {
    await releaseAssignment("whitelist", applicationId, interaction.user.id);
    // Disable les boutons du DM (best-effort, peut throw si message
    // d'origine deja edite).
    try {
      await interaction.message.edit({ components: [] });
    } catch {
      /* ignore */
    }
    await interaction.editReply({
      content: "✅ Libéré — un autre staff peut reprendre depuis le salon public.",
    });
    // TODO : ideally on edite aussi le message public pour ré-afficher
    // le bouton Prendre en charge. Necessite de stocker discordMessageId
    // associe — laisse pour C6 (panel sync).
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Echec : ${message}` });
  }
}

async function openDecisionModal(
  interaction: ButtonInteraction,
  action: "accept" | "reject" | "revise",
  applicationId: string,
): Promise<void> {
  const config: Record<typeof action, { title: string; label: string; required: boolean; placeholder: string }> = {
    accept: {
      title: "Accepter la candidature",
      label: "Notes (optionnelles)",
      required: false,
      placeholder: "Bienvenue, bon RP… (vide = pas de message annexe)",
    },
    revise: {
      title: "Demander une révision",
      label: "Que doit préciser le joueur ?",
      required: true,
      placeholder: "Tu dois etoffer ton background…",
    },
    reject: {
      title: "Refuser la candidature",
      label: "Raison du refus (visible par le joueur)",
      required: true,
      placeholder: "Bg trop léger, manque de RP…",
    },
  };
  const cfg = config[action];

  const modal = new ModalBuilder()
    .setCustomId(`wl:${action}-modal:${applicationId}`)
    .setTitle(cfg.title)
    .addComponents(
      new ActionRowBuilder<TextInputBuilder>().addComponents(
        new TextInputBuilder()
          .setCustomId("notes")
          .setLabel(truncate(cfg.label, 45))
          .setStyle(TextInputStyle.Paragraph)
          .setPlaceholder(cfg.placeholder)
          .setRequired(cfg.required)
          .setMaxLength(2000),
      ),
    );
  await interaction.showModal(modal);
}

export async function handleWhitelistModal(
  interaction: ModalSubmitInteraction,
): Promise<void> {
  // customId = wl:<action>-modal:<applicationId>
  const parts = interaction.customId.split(":");
  const actionTag = parts[1] ?? "";
  const applicationId = parts[2] ?? "";
  const action = actionTag.replace("-modal", "");
  if (!["accept", "reject", "revise"].includes(action) || !applicationId) {
    await interaction.reply({
      content: "Modal invalide.",
      flags: MessageFlags.Ephemeral,
    });
    return;
  }

  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  const notes = interaction.fields.getTextInputValue("notes").trim();
  const statusMap: Record<string, "APPROVED" | "REJECTED" | "NEEDS_REVISION"> = {
    accept: "APPROVED",
    reject: "REJECTED",
    revise: "NEEDS_REVISION",
  };
  const labels: Record<string, string> = {
    accept: "✅ Candidature acceptée",
    reject: "❌ Candidature refusée",
    revise: "📝 Révision demandée",
  };

  try {
    await decideWhitelist(applicationId, statusMap[action]!, notes || undefined);

    // Edite le message DM courant : retire les boutons + ajoute un
    // embed recap.
    if (interaction.message) {
      const recap = new EmbedBuilder()
        .setColor(
          action === "accept"
            ? 0x16a34a
            : action === "reject"
              ? 0xef4444
              : 0xf59e0b,
        )
        .setTitle(labels[action]!)
        .setDescription(notes ? `**Notes :** ${notes}` : "_(aucune note)_");
      try {
        await interaction.message.edit({ components: [] });
        // Renvoie le recap dans le meme DM via le user (channel.send
        // pas dispo sur PartialGroupDMChannel — on passe par createDM).
        const dm = await interaction.user.createDM();
        await dm.send({ embeds: [recap] });
      } catch {
        /* DM channel can fail in rare cases */
      }
    }

    await interaction.editReply({
      content: `${labels[action]} — le joueur recevra la notif dans le launcher.`,
    });

    // Vide le cache : la candidature est decidee.
    if (action === "accept" || action === "reject") {
      PAYLOAD_CACHE.delete(applicationId);
    }
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Echec : ${message}` });
  }
}

/**
 * Edite le message public d'annonce pour retirer le bouton Prendre en
 * charge et ajouter "Pris par @X" en footer. Best-effort : si le
 * message est introuvable (supprime manuellement), on log et on
 * continue.
 */
async function editAnnouncementClaimed(
  interaction: ButtonInteraction,
  claimedBy: string,
): Promise<void> {
  const msg = interaction.message;
  try {
    const original = msg.embeds[0]?.toJSON() ?? {};
    const updated = new EmbedBuilder(original).setFooter({
      text: `${original.footer?.text ?? ""} · Pris par ${claimedBy}`,
    });
    await msg.edit({ embeds: [updated], components: [] });
  } catch (err) {
    console.warn("[wl:claim] edit announcement failed :", err);
  }
}
