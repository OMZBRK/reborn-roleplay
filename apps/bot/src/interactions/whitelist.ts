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
import {
  ApiError,
  claimAssignment,
  decideWhitelistPart,
  messageEchec,
  releaseAssignment,
  type AppStatus,
  type DecisionEcrit,
  type PartieEcrit,
  type WhitelistPartDecisionResponse,
} from "../api-client.js";
import { config } from "../config.js";
import {
  champsTexte,
  computeAge,
  formatDateFr,
  packEmbedsForMessages,
  paginateLong,
  remplacerSegmentPied,
  SEGMENTS_PRISE_EN_CHARGE,
  truncate,
  type ChampEmbed,
} from "../embeds.js";

/**
 * Candidatures whitelist côté Discord (CANDIDATURE_ET_DOUANE.md §6.1, lot H).
 *
 * - Annonce dans le salon staff : un résumé et « Prendre en charge ».
 * - Prise en charge : le dossier complet part en MP, avec les boutons de
 *   décision PAR PARTIE de l'écrit (HRP, RP). Plus de décision globale.
 * - L'oral ne se décide pas ici : douane en jeu, ou carte « Oral » du panel.
 * - Aucun rôle Discord n'est posé à l'acceptation (décision du 24/10) : le
 *   statut whitelist se voit dans le launcher.
 */

/**
 * Cache en RAM des payloads reçus via /webhooks/whitelist : sert à construire
 * le dossier complet quand un staff clique sur « Prendre en charge » (MP). Pas
 * de TTL : gardé jusqu'au redémarrage du bot ; sans cache, le MP renvoie au panel.
 */
const PAYLOAD_CACHE = new Map<string, WhitelistPayloadFull>();

/**
 * Payload de /webhooks/whitelist (apps/api/src/webhooks/webhooks.service.ts ›
 * WhitelistWebhookPayload). Candidature v2 (formVersion 2, §2.3) : les listes
 * fermées arrivent en codes ASCII, traduits ici. Les champs retirés de la v2
 * (date de naissance, « Pourquoi Reborn ? », expérience du RP, caractère et
 * apparence) arrivent vides pour une v2 et ne s'affichent que s'ils sont remplis.
 *
 * Un ancien dossier peut mêler les deux formats après un renvoi partiel :
 * formVersion suit la partie RP (v1 qui renvoie sa partie RP → v2, HRP v1
 * gardé ; v1 qui renvoie seulement sa partie HRP → reste v1 avec un HRP v2).
 * L'affichage HRP se décide donc d'après les champs présents, pas d'après
 * formVersion (voir hrpV2 / rpV2).
 */
export interface WhitelistPayloadFull {
  applicationId: string;
  userPseudo: string;
  userId: string;
  discordUserId: string | null;
  // Partie HRP (onglet « Vous »)
  availability: string;
  // Partie RP (onglets Personnage, Village, Histoire, Voix)
  firstName: string;
  lastName: string;
  village: string;
  support: string | null;
  history: string;
  objectives: string;
  // Candidature v2
  formVersion?: number;
  playerAge?: number | null;
  rpYears?: string | null;
  narutoKnowledge?: string | null;
  discoverySource?: string | null;
  characterType?: string | null;
  characterSex?: string | null;
  characterAge?: number | null;
  /** Durée de la voix jointe ; le fichier est privé, il s'écoute dans le panel. */
  voiceDurationSec?: number | null;
  /**
   * Facultatifs : état des deux parties au moment de l'envoi. Après un renvoi
   * « à revoir », une partie déjà validée n'est plus proposée à la décision.
   */
  hrpStatus?: AppStatus | null;
  rpStatus?: AppStatus | null;
  // Ancien formulaire (sans formVersion 2), lu seulement pour un ancien dossier.
  dob?: string;
  motivation?: string;
  experience?: string;
  appearance?: string;
}

export function cacheWhitelistPayload(payload: WhitelistPayloadFull) {
  PAYLOAD_CACHE.set(payload.applicationId, payload);
}

// ── Libellés ────────────────────────────────────────────

/** Copie des libellés de l'API (apps/api/src/whitelist/whitelist.rules.ts › LIBELLES). */
const LIBELLES: Record<
  "rpYears" | "narutoKnowledge" | "discoverySource" | "characterType" | "characterSex",
  Record<string, string>
> = {
  rpYears: {
    JAMAIS: "Jamais",
    MOINS_D_UN_AN: "Moins d'un an",
    UN_A_DEUX_ANS: "1 à 2 ans",
    TROIS_A_CINQ_ANS: "3 à 5 ans",
    SIX_ANS_ET_PLUS: "6 ans et plus",
  },
  narutoKnowledge: {
    DEBUTANT: "Débutant",
    QUELQUES_ARCS: "Quelques arcs",
    BONNE_CONNAISSANCE: "Bonne connaissance",
    INCOLLABLE: "Incollable, Boruto compris",
  },
  discoverySource: {
    DISCORD: "Discord",
    TIKTOK: "TikTok",
    YOUTUBE: "YouTube",
    AMI: "Un ami",
    AUTRE_SERVEUR: "Un autre serveur",
    RECHERCHE: "Une recherche",
    AUTRE: "Autre",
  },
  characterType: { NINJA: "Ninja", CIVIL: "Civil" },
  characterSex: { HOMME: "Homme", FEMME: "Femme" },
};

function libelle(
  liste: keyof typeof LIBELLES,
  code: string | null | undefined,
  vide = "—",
): string {
  if (!code) return vide;
  return LIBELLES[liste][code] ?? code;
}

/** Tranche d'âge du personnage, comme à la candidature (§2.3). */
function trancheAge(age: number): string {
  if (age <= 12) return "Académie";
  if (age <= 16) return "Jeune genin";
  return "Adulte";
}

function dureeVoix(sec: number): string {
  return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;
}

/** Valeur de champ jamais vide (Discord refuse un champ vide), coupée à 1 024 caractères. */
function valeur(texte: string | null | undefined, vide = "—"): string {
  const t = texte?.trim();
  return t ? truncate(t, 1024) : vide;
}

function nomPersonnage(p: WhitelistPayloadFull): string {
  return `${p.firstName} ${p.lastName}`.trim() || "(sans nom)";
}

/** Partie HRP au format v2 (âge déclaré, roleplay, Naruto), quel que soit formVersion. */
function hrpV2(p: WhitelistPayloadFull): boolean {
  return p.playerAge != null || !!p.rpYears || !!p.narutoKnowledge;
}

/** Partie RP au format v2 (type, sexe, âge du personnage, voix) : formVersion suit la partie RP. */
function rpV2(p: WhitelistPayloadFull): boolean {
  return p.formVersion === 2;
}

const COULEUR_HRP = 0x3b5bdb;
const COULEUR_RP = 0x8b5cf6;

// ── Décisions par partie ────────────────────────────────

const PARTIES: readonly PartieEcrit[] = ["HRP", "RP"];

const ACTIONS = {
  accept: {
    statut: "APPROVED",
    bouton: "Accepter",
    style: ButtonStyle.Success,
    emoji: "✅",
    titreModal: "Accepter la partie",
  },
  revise: {
    statut: "NEEDS_REVISION",
    bouton: "À revoir",
    style: ButtonStyle.Secondary,
    emoji: "📝",
    titreModal: "À revoir : partie",
  },
  reject: {
    statut: "REJECTED",
    bouton: "Refuser",
    style: ButtonStyle.Danger,
    emoji: "❌",
    titreModal: "Refuser la partie",
  },
} as const satisfies Record<
  string,
  { statut: DecisionEcrit; bouton: string; style: ButtonStyle; emoji: string; titreModal: string }
>;
type ActionDecision = keyof typeof ACTIONS;

/** « acceptée », « à revoir », « refusée » (une partie est féminine). */
const PARTICIPE: Record<DecisionEcrit, string> = {
  APPROVED: "acceptée",
  NEEDS_REVISION: "à revoir",
  REJECTED: "refusée",
};
const ICONE: Record<DecisionEcrit, string> = {
  APPROVED: "✅",
  NEEDS_REVISION: "📝",
  REJECTED: "❌",
};
const COULEUR_DECISION: Record<DecisionEcrit, number> = {
  APPROVED: 0x16a34a,
  NEEDS_REVISION: 0xf59e0b,
  REJECTED: 0xef4444,
};

export const MSG_COMMENTAIRE_OBLIGATOIRE =
  "Le commentaire est obligatoire pour « À revoir » et « Refuser » : il est montré au candidat.";

/** customId d'un bouton (ou modal) de décision : `wl:hrp-accept:<id>`, `wl:rp-reject-modal:<id>`. */
const MOTIF_ACTION = /^(hrp|rp)-(accept|revise|reject)(-modal)?$/;

function lireAction(
  tag: string,
): { partie: PartieEcrit; action: ActionDecision; modal: boolean } | null {
  const m = MOTIF_ACTION.exec(tag);
  if (!m?.[1] || !m[2]) return null;
  return {
    partie: m[1] === "hrp" ? "HRP" : "RP",
    action: m[2] as ActionDecision,
    modal: m[3] !== undefined,
  };
}

type EtatParties = Partial<Record<PartieEcrit, AppStatus | null | undefined>>;

/**
 * Une ligne de boutons par partie de l'écrit. Une partie déjà tranchée
 * (acceptée, à revoir, refusée) affiche son état, bouton grisé ; une partie
 * en attente (ou d'état inconnu) propose Accepter · À revoir · Refuser.
 */
function buildDecisionRows(
  applicationId: string,
  etat: EtatParties = {},
): ActionRowBuilder<ButtonBuilder>[] {
  return PARTIES.map((partie) => {
    const row = new ActionRowBuilder<ButtonBuilder>();
    const st = etat[partie];
    if (st && st !== "PENDING") {
      return row.addComponents(
        new ButtonBuilder()
          .setCustomId(`wl:${partie.toLowerCase()}-fait:${applicationId}`)
          .setLabel(`${partie} · ${PARTICIPE[st]}`)
          .setStyle(ButtonStyle.Secondary)
          .setEmoji(ICONE[st])
          .setDisabled(true),
      );
    }
    for (const [action, a] of Object.entries(ACTIONS)) {
      row.addComponents(
        new ButtonBuilder()
          .setCustomId(`wl:${partie.toLowerCase()}-${action}:${applicationId}`)
          .setLabel(`${partie} · ${a.bouton}`)
          .setStyle(a.style)
          .setEmoji(a.emoji),
      );
    }
    return row;
  });
}

function buildReleaseRow(
  applicationId: string,
  avecLiberer = true,
): ActionRowBuilder<ButtonBuilder> {
  const row = new ActionRowBuilder<ButtonBuilder>();
  if (avecLiberer) {
    row.addComponents(
      new ButtonBuilder()
        .setCustomId(`wl:release:${applicationId}`)
        .setLabel("Libérer (passer la main)")
        .setStyle(ButtonStyle.Secondary)
        .setEmoji("↩️"),
    );
  }
  return row.addComponents(
    new ButtonBuilder()
      .setURL(`${config.adminBaseUrl}/whitelist/${applicationId}`)
      .setLabel("Ouvrir dans le panel")
      .setStyle(ButtonStyle.Link)
      .setEmoji("🪟"),
  );
}

function estFinal(status: AppStatus): boolean {
  return status === "APPROVED" || status === "REJECTED";
}

/**
 * Vrai quand plus aucune partie de l'écrit n'attend de décision (écrit
 * accepté, ou les deux parties tranchées). « Libérer » n'a alors plus de
 * sens : il remettrait « Prendre en charge » sur un dossier déjà relu.
 */
function plusRienATrancher(etat: EtatParties): boolean {
  return PARTIES.every((p) => {
    const st = etat[p];
    return !!st && st !== "PENDING";
  });
}

/** Boutons du MP après une décision, d'après la réponse de l'API. */
function composantsApresDecision(
  applicationId: string,
  partie: PartieEcrit,
  statut: DecisionEcrit,
  rep: WhitelistPartDecisionResponse,
): ActionRowBuilder<ButtonBuilder>[] {
  // Candidature acceptée ou refusée : plus rien à trancher ici.
  if (estFinal(rep.status)) return [buildReleaseRow(applicationId, false)];
  const etat: EtatParties = { HRP: rep.hrpStatus, RP: rep.rpStatus };
  etat[partie] ??= statut;
  if (rep.writtenApprovedAt) {
    // Écrit accepté : les deux parties sont validées, même sans leur état détaillé.
    etat.HRP ??= "APPROVED";
    etat.RP ??= "APPROVED";
  }
  return [
    ...buildDecisionRows(applicationId, etat),
    buildReleaseRow(applicationId, !plusRienATrancher(etat)),
  ];
}

/** Ce qui se passe ensuite, d'après la réponse de l'API. */
function suiteDecision(
  rep: WhitelistPartDecisionResponse,
  partie: PartieEcrit,
  statut: DecisionEcrit,
): string {
  if (rep.status === "APPROVED") {
    return "Candidature acceptée : l'oral était déjà passé sur ce compte.";
  }
  if (rep.status === "REJECTED") {
    return "Candidature refusée : nouvelle candidature complète possible après 1 jour.";
  }
  if (rep.writtenApprovedAt) {
    return "Écrit accepté : le candidat réserve sa séance de douane dans le launcher.";
  }
  const aRevoir = PARTIES.filter(
    (p) => (p === "HRP" ? rep.hrpStatus : rep.rpStatus) === "NEEDS_REVISION",
  );
  if (aRevoir.length === 0 && statut === "NEEDS_REVISION") aRevoir.push(partie);
  if (aRevoir.length > 0) {
    const quoi = aRevoir.length === 2 ? "les parties HRP et RP" : `la partie ${aRevoir[0]}`;
    return `Le candidat corrige ${quoi} dans le launcher, puis renvoie sa candidature.`;
  }
  const autre: PartieEcrit = partie === "HRP" ? "RP" : "HRP";
  if ((autre === "HRP" ? rep.hrpStatus : rep.rpStatus) === "PENDING") {
    return `La partie ${autre} reste à relire.`;
  }
  return "Candidature mise à jour.";
}

export interface IssueDecision {
  reponse: WhitelistPartDecisionResponse;
  /** Récapitulatif à poster (MP, fil historique). */
  recap: EmbedBuilder;
  /** Une ligne pour la réponse éphémère. */
  resume: string;
}

/**
 * Envoie la décision d'UNE partie de l'écrit à l'API, au nom du staff Discord
 * `acteurDiscordId` (contrôle du rôle et audit côté API), et prépare le
 * récapitulatif. Commun aux boutons du MP et à `/whitelist`. Lève ApiError.
 */
export async function deciderPartie(
  applicationId: string,
  partie: PartieEcrit,
  statut: DecisionEcrit,
  commentaire: string | undefined,
  acteurDiscordId: string,
): Promise<IssueDecision> {
  const reponse = await decideWhitelistPart(applicationId, {
    part: partie,
    status: statut,
    actorDiscordId: acteurDiscordId,
    reviewNotes: commentaire,
  });
  const titre = `${ICONE[statut]} Partie ${partie} ${PARTICIPE[statut]}`;
  const suite = suiteDecision(reponse, partie, statut);
  const champs: ChampEmbed[] = [
    { name: "Décision par", value: `<@${acteurDiscordId}>`, inline: true },
    ...(commentaire
      ? champsTexte("Commentaire (visible du candidat)", commentaire)
      : [{ name: "Commentaire", value: "*aucun*" }]),
    { name: "Suite", value: suite },
  ];
  const recap = new EmbedBuilder()
    .setColor(COULEUR_DECISION[statut])
    .setTitle(titre)
    .addFields(champs)
    .setTimestamp(new Date());
  if (estFinal(reponse.status)) {
    PAYLOAD_CACHE.delete(applicationId);
  } else {
    // État des parties gardé à jour : une prise en charge suivante (après
    // « Libérer ») grise la partie déjà tranchée au lieu de la reproposer.
    const c = PAYLOAD_CACHE.get(applicationId);
    if (c) {
      c.hrpStatus = reponse.hrpStatus ?? (partie === "HRP" ? statut : c.hrpStatus);
      c.rpStatus = reponse.rpStatus ?? (partie === "RP" ? statut : c.rpStatus);
    }
  }
  return { reponse, recap, resume: `${titre}. ${suite}` };
}

// ── Annonce et dossier ──────────────────────────────────

/** « À relire : partie RP (HRP déjà validée) », si l'API transmet l'état des parties. */
function ligneARelire(p: WhitelistPayloadFull): string | null {
  if (!p.hrpStatus && !p.rpStatus) return null;
  const valides = PARTIES.filter((x) => (x === "HRP" ? p.hrpStatus : p.rpStatus) === "APPROVED");
  const aRelire = PARTIES.filter((x) => !valides.includes(x));
  if (aRelire.length === 0) return null;
  const quoi = aRelire.length === 2 ? "HRP et RP" : `partie ${aRelire[0]}`;
  return `**À relire** : ${quoi}` + (valides.length === 1 ? ` (${valides[0]} déjà validée)` : "");
}

/**
 * Message public d'annonce dans le salon staff : résumé compact + bouton
 * « Prendre en charge ». Le dossier détaillé n'est PAS dans le salon : il part
 * en MP au staff qui prend en charge (moins de bruit, pas de double traitement).
 */
export function buildWhitelistAnnouncement(payload: WhitelistPayloadFull): {
  embeds: EmbedBuilder[];
  components: ActionRowBuilder<ButtonBuilder>[];
} {
  const lignes = [
    `**${payload.userPseudo}**` +
      (payload.discordUserId ? ` · <@${payload.discordUserId}>` : ""),
  ];
  // Partie HRP : âge déclaré (v2), sinon date de naissance (ancien format).
  if (hrpV2(payload) && payload.playerAge != null) {
    lignes.push(`**Âge déclaré** : ${payload.playerAge} ans`);
  } else if (payload.dob) {
    const age = computeAge(payload.dob);
    lignes.push(
      `**Date de naissance** : ${formatDateFr(payload.dob)}` +
        (age !== null ? ` (${age} ans)` : ""),
    );
  }
  const rp2 = rpV2(payload);
  if (rp2) {
    const perso = [
      libelle("characterType", payload.characterType, ""),
      libelle("characterSex", payload.characterSex, ""),
      payload.characterAge != null ? `${payload.characterAge} ans` : "",
    ].filter(Boolean);
    if (perso.length > 0) lignes.push(`**Personnage** : ${perso.join(" · ")}`);
  }
  lignes.push(`**Village** : ${valeur(payload.village)}`);
  if (rp2) {
    lignes.push(
      `**Voix** : ${payload.voiceDurationSec ? dureeVoix(payload.voiceDurationSec) : "aucune"}`,
    );
  }
  const aRelire = ligneARelire(payload);
  if (aRelire) lignes.push(aRelire);
  lignes.push("", "_Clique sur **Prendre en charge** pour recevoir le dossier complet en MP._");

  const teaser = new EmbedBuilder()
    .setTitle(truncate(`Nouvelle candidature — ${nomPersonnage(payload)}`, 256))
    .setColor(COULEUR_HRP)
    .setDescription(truncate(lignes.join("\n"), 4096))
    // Le pied « application <id> » est relu par le bot : ne pas le changer.
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

function champEtat(partie: PartieEcrit, st: AppStatus | null | undefined): ChampEmbed[] {
  if (!st || st === "PENDING") return [];
  const texte = st === "APPROVED" ? "Déjà validée" : `État : ${PARTICIPE[st]}`;
  return [{ name: `Partie ${partie}`, value: texte, inline: true }];
}

/**
 * Dossier complet envoyé en MP après la prise en charge : partie HRP, puis
 * partie RP, chaque texte long découpé (4 096 caractères par description).
 */
function buildWhitelistDmEmbeds(payload: WhitelistPayloadFull): EmbedBuilder[] {
  const rp2 = rpV2(payload);

  const champsHrp: ChampEmbed[] = [
    { name: "Joueur Reborn", value: `\`${payload.userPseudo}\``, inline: true },
    {
      name: "Discord",
      value: payload.discordUserId ? `<@${payload.discordUserId}>` : "*non lié*",
      inline: true,
    },
  ];
  if (hrpV2(payload)) {
    champsHrp.push(
      {
        name: "Âge déclaré",
        value: payload.playerAge != null ? `${payload.playerAge} ans` : "—",
        inline: true,
      },
      { name: "Roleplay", value: libelle("rpYears", payload.rpYears), inline: true },
      { name: "Univers Naruto", value: libelle("narutoKnowledge", payload.narutoKnowledge), inline: true },
      {
        name: "Découverte",
        value: libelle("discoverySource", payload.discoverySource, "Pas de réponse"),
        inline: true,
      },
    );
  }
  if (payload.dob) {
    // Ancien format, ou partie HRP v1 déjà validée d'un dossier passé en v2.
    const age = computeAge(payload.dob);
    champsHrp.push({
      name: "Date de naissance",
      value: `${formatDateFr(payload.dob)}${age !== null ? ` (${age} ans)` : ""}`,
      inline: true,
    });
  }
  champsHrp.push(...champEtat("HRP", payload.hrpStatus));

  const enteteHrp = new EmbedBuilder()
    .setTitle(truncate(`Partie HRP — ${payload.userPseudo}`, 256))
    .setColor(COULEUR_HRP)
    .addFields(champsHrp)
    .setFooter({ text: `application ${payload.applicationId}` });

  const champsRp: ChampEmbed[] = [
    { name: "Personnage", value: valeur(nomPersonnage(payload)), inline: true },
    { name: "Village", value: valeur(payload.village), inline: true },
  ];
  if (rp2) {
    champsRp.push(
      { name: "Type", value: libelle("characterType", payload.characterType), inline: true },
      { name: "Sexe", value: libelle("characterSex", payload.characterSex), inline: true },
      {
        name: "Âge du personnage",
        value:
          payload.characterAge != null
            ? `${payload.characterAge} ans · ${trancheAge(payload.characterAge)}`
            : "—",
        inline: true,
      },
      {
        name: "Voix",
        value: payload.voiceDurationSec
          ? `${dureeVoix(payload.voiceDurationSec)} — à écouter dans le panel`
          : "*aucune*",
        inline: true,
      },
    );
  }
  champsRp.push(
    { name: "Support", value: valeur(payload.support, "*aucun*"), inline: !rp2 },
    ...champEtat("RP", payload.rpStatus),
  );

  const enteteRp = new EmbedBuilder()
    .setTitle(truncate(`Partie RP — ${nomPersonnage(payload)}`, 256))
    .setColor(COULEUR_RP)
    .addFields(champsRp);

  // Ancien formulaire : ses textes retirés de la v2 ne s'affichent que s'il y en
  // a (l'API les envoie vides pour une v2, remplis pour une partie v1 gardée).
  const ancien = (titre: string, couleur: number, texte: string | undefined) =>
    texte?.trim() ? paginateLong(titre, couleur, texte) : [];

  return [
    enteteHrp,
    ...paginateLong("Disponibilités", COULEUR_HRP, payload.availability),
    ...ancien("Motivation", COULEUR_HRP, payload.motivation),
    ...ancien("Expérience RP", COULEUR_HRP, payload.experience),
    enteteRp,
    ...paginateLong("Histoire", COULEUR_RP, payload.history),
    ...ancien("Apparence et personnalité", COULEUR_RP, payload.appearance),
    ...paginateLong("Objectifs", COULEUR_RP, payload.objectives),
  ];
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

  if (action === "claim") return claimWhitelist(client, interaction, applicationId);
  if (action === "release") return releaseWhitelistDm(interaction, applicationId);

  const decision = lireAction(action);
  if (decision && !decision.modal) {
    return openDecisionModal(interaction, decision.partie, decision.action, applicationId);
  }

  // Boutons d'un ancien MP (décision globale, retirée) : on propose les boutons par partie.
  if (action === "accept" || action === "reject" || action === "revise") {
    await interaction.reply({
      content:
        "La décision globale est retirée : l'écrit se décide maintenant partie par partie (HRP, puis RP).",
      components: buildDecisionRows(applicationId),
      flags: MessageFlags.Ephemeral,
    });
    return;
  }

  await interaction.reply({
    content: `Action inconnue : ${action}`,
    flags: MessageFlags.Ephemeral,
  });
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

    // 1. MP au staff : dossier complet + boutons de décision par partie.
    const payload = PAYLOAD_CACHE.get(applicationId);
    const dm = await interaction.user.createDM();
    if (payload) {
      const embeds = buildWhitelistDmEmbeds(payload);
      const batches = packEmbedsForMessages(embeds);
      const intro = new EmbedBuilder()
        .setColor(0x16a34a)
        .setDescription(
          `Tu as **pris en charge** la candidature de **${payload.userPseudo}**.\n` +
            "Voici le dossier complet. Décide chaque partie de l'écrit avec les boutons en bas " +
            "(HRP, puis RP), ou continue dans le panel. La voix s'écoute dans le panel.",
        );
      await dm.send({ embeds: [intro] });
      const etat: EtatParties = { HRP: payload.hrpStatus, RP: payload.rpStatus };
      for (let i = 0; i < batches.length; i++) {
        const isLast = i === batches.length - 1;
        await dm.send({
          embeds: batches[i],
          components: isLast
            ? [
                ...buildDecisionRows(applicationId, etat),
                buildReleaseRow(applicationId, !plusRienATrancher(etat)),
              ]
            : [],
        });
      }
    } else {
      // Cache vide (bot redémarré depuis l'envoi) : repli minimal mais fonctionnel.
      const fallback = new EmbedBuilder()
        .setColor(0xf59e0b)
        .setTitle("Candidature prise en charge")
        .setDescription(
          "Le dossier n'est plus en cache (le bot a redémarré depuis l'envoi).\n" +
            "Consulte-le dans le panel :\n" +
            `→ ${config.adminBaseUrl}/whitelist/${applicationId}\n\n` +
            "Tu peux ensuite décider chaque partie de l'écrit avec les boutons ci-dessous.",
        );
      await dm.send({
        embeds: [fallback],
        components: [...buildDecisionRows(applicationId), buildReleaseRow(applicationId)],
      });
    }

    // 2. Message public : retire le bouton et ajoute « Pris par X ».
    const claimedBy = result.assignee?.discordUsername ?? interaction.user.username;
    await editAnnouncementClaimed(interaction, claimedBy);

    await interaction.editReply({
      content: "✅ Pris en charge — vérifie tes MP.",
    });
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Échec : ${message}` });
  }
}

async function releaseWhitelistDm(
  interaction: ButtonInteraction,
  applicationId: string,
): Promise<void> {
  await interaction.deferReply({ flags: MessageFlags.Ephemeral });
  try {
    await releaseAssignment("whitelist", applicationId, interaction.user.id);
    // Retire les boutons du MP (au mieux : le message a pu être modifié entre-temps).
    try {
      await interaction.message.edit({ components: [] });
    } catch {
      /* ignore */
    }
    await interaction.editReply({
      content: "✅ Libéré — un autre staff peut reprendre depuis le salon.",
    });
    // Le message public retrouve son bouton par /webhooks/assignment-update (API).
  } catch (err) {
    const message = err instanceof ApiError ? err.message : String(err);
    await interaction.editReply({ content: `❌ Échec : ${message}` });
  }
}

async function openDecisionModal(
  interaction: ButtonInteraction,
  partie: PartieEcrit,
  action: ActionDecision,
  applicationId: string,
): Promise<void> {
  const a = ACTIONS[action];
  const obligatoire = action !== "accept";
  const placeholder: Record<ActionDecision, string> = {
    accept: "Facultatif : un mot pour le candidat.",
    revise: `Ce que le candidat doit reprendre dans la partie ${partie}…`,
    reject: "La raison du refus, lue par le candidat dans le launcher…",
  };

  const modal = new ModalBuilder()
    .setCustomId(`wl:${partie.toLowerCase()}-${action}-modal:${applicationId}`)
    .setTitle(truncate(`${a.titreModal} ${partie}`, 45))
    .addComponents(
      new ActionRowBuilder<TextInputBuilder>().addComponents(
        new TextInputBuilder()
          .setCustomId("notes")
          .setLabel(obligatoire ? "Commentaire (visible du candidat)" : "Commentaire (facultatif, visible)")
          .setStyle(TextInputStyle.Paragraph)
          .setPlaceholder(placeholder[action])
          .setRequired(obligatoire)
          .setMaxLength(2000),
      ),
    );
  await interaction.showModal(modal);
}

export async function handleWhitelistModal(
  interaction: ModalSubmitInteraction,
): Promise<void> {
  // customId = wl:<partie>-<action>-modal:<applicationId>
  const parts = interaction.customId.split(":");
  const decision = lireAction(parts[1] ?? "");
  const applicationId = parts[2] ?? "";
  if (!decision?.modal || !applicationId) {
    await interaction.reply({
      content:
        "Formulaire périmé : l'écrit se décide maintenant partie par partie (HRP, puis RP). Rouvre le dossier.",
      flags: MessageFlags.Ephemeral,
    });
    return;
  }
  const { partie, action } = decision;
  const statut = ACTIONS[action].statut;
  const commentaire = interaction.fields.getTextInputValue("notes").trim() || undefined;
  if (statut !== "APPROVED" && !commentaire) {
    await interaction.reply({ content: MSG_COMMENTAIRE_OBLIGATOIRE, flags: MessageFlags.Ephemeral });
    return;
  }

  // Formulaire ouvert depuis un bouton : on modifie ce message (MP, ou message
  // éphémère d'un ancien MP), puis on répond à la suite.
  const depuisMessage = interaction.isFromMessage();
  if (depuisMessage) await interaction.deferUpdate();
  else await interaction.deferReply({ flags: MessageFlags.Ephemeral });

  try {
    const issue = await deciderPartie(
      applicationId,
      partie,
      statut,
      commentaire,
      interaction.user.id,
    );
    if (depuisMessage) {
      try {
        await interaction.editReply({
          components: composantsApresDecision(applicationId, partie, statut, issue.reponse),
        });
      } catch {
        /* message disparu : le récapitulatif suffit */
      }
      // Récapitulatif gardé dans le MP, comme trace de la décision.
      await interaction.followUp({ embeds: [issue.recap] });
    } else {
      await interaction.editReply({ content: issue.resume });
    }
  } catch (err) {
    const texte = `❌ Échec : ${messageEchec(err)}`;
    if (depuisMessage) {
      await interaction.followUp({ content: texte, flags: MessageFlags.Ephemeral });
    } else {
      await interaction.editReply({ content: texte });
    }
  }
}

/**
 * Modifie l'annonce publique : retire le bouton « Prendre en charge » et ajoute
 * « Pris par X » au pied. Au mieux : si le message a disparu, on journalise.
 */
async function editAnnouncementClaimed(
  interaction: ButtonInteraction,
  claimedBy: string,
): Promise<void> {
  const msg = interaction.message;
  try {
    const original = msg.embeds[0]?.toJSON() ?? {};
    const updated = new EmbedBuilder(original).setFooter({
      text: remplacerSegmentPied(
        original.footer?.text ?? "",
        SEGMENTS_PRISE_EN_CHARGE,
        `Pris par ${claimedBy}`,
      ),
    });
    // Un récapitulatif de statut déjà posé (2e embed) reste en place.
    const autres = msg.embeds.slice(1).map((e) => new EmbedBuilder(e.toJSON()));
    await msg.edit({ embeds: [updated, ...autres], components: [] });
  } catch (err) {
    console.warn("[wl:claim] edit announcement failed :", err);
  }
}
