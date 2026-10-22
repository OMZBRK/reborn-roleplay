/**
 * ============================================================================
 *  SALON PIÈGE « façon network » — `⚠️・ne-pas-écrire` (comptes piratés)
 * ============================================================================
 *
 *  Un compte piraté poste ses liens d'arnaque (faux giveaways, « MrBeast »…) dans TOUS les salons
 *  où il peut écrire. Un salon où personne ne doit écrire le démasque : tout message posté dans
 *  `DISCORD_PIEGE_CHANNEL_ID` (serveur `DISCORD_JOUEURS_GUILD_ID`) entraîne :
 *    1. un MP bilingue à l'auteur (au mieux : MP fermés = ignoré) ;
 *    2. un bannissement de `DISCORD_PIEGE_JOURS` jours (défaut 7, 0 = définitif) qui efface ses
 *       messages des `DISCORD_PIEGE_PURGE_SECONDES` dernières secondes PARTOUT (défaut 1 h) ;
 *    3. une ligne au journal (console + `DISCORD_JOUEURS_LOG_CHANNEL_ID`).
 *  Débannissement automatique : la date de fin est écrite dans le motif du bannissement
 *  (« [piège Reborn] fin=<ISO> »), relue au démarrage puis toutes les 30 min. Aucun état local :
 *  un redémarrage ou un nouveau conteneur ne perd rien.
 *
 *  Jamais sanctionnés : bots et webhooks, propriétaire du serveur, rôle `DISCORD_JOUEURS_STAFF_ROLE_ID`,
 *  tout membre ayant une permission de modération (Administrateur, Gérer le serveur / les messages,
 *  Bannir, Expulser, Exclure). Un membre au-dessus du bot est seulement journalisé.
 *  `DISCORD_PIEGE_SIMULATION=1` : le message est effacé et journalisé, personne n'est banni.
 *  Désactivé par défaut (variable vide). Le Discord staff n'est jamais concerné.
 *  Doc : docs/operations/DISCORD_JOUEURS.md §5.
 * ============================================================================
 */
import { Events, PermissionFlagsBits, type Client, type Guild, type Message } from "discord.js";
import { config } from "../config.js";
import { journal } from "./journal.js";

const MOTIF = "[piège Reborn]";
const RE_FIN = /^\[piège Reborn\] fin=(\S+)/;
const CONTROLE_MS = 30 * 60_000;
/** Un même compte qui poste en rafale n'est traité qu'une fois. */
const enCours = new Set<string>();

const PERMS_MODERATION = [
  PermissionFlagsBits.Administrator,
  PermissionFlagsBits.ManageGuild,
  PermissionFlagsBits.ManageMessages,
  PermissionFlagsBits.BanMembers,
  PermissionFlagsBits.KickMembers,
  PermissionFlagsBits.ModerateMembers,
];

export function startPiege(client: Client<true>): void {
  if (!config.joueursGuildId || !config.piegeChannelId) {
    console.log("[piège] désactivé (DISCORD_PIEGE_CHANNEL_ID vide)");
    return;
  }
  client.on(Events.MessageCreate, (message) => {
    if (message.channelId !== config.piegeChannelId || message.guildId !== config.joueursGuildId) return;
    traiter(client, message).catch((err) => console.error("[piège] crash :", err));
  });
  const controle = () => debannirEchus(client).catch((err) => console.warn(`[piège] contrôle des fins : ${(err as Error).message}`));
  void controle();
  setInterval(controle, CONTROLE_MS);
  const duree = config.piegeJours > 0 ? `${config.piegeJours} j` : "définitif";
  console.log(`[piège] actif${config.piegeSimulation ? " — SIMULATION (aucun bannissement)" : ""} : bannissement ${duree}`);
}

async function traiter(client: Client<true>, message: Message): Promise<void> {
  const auteur = message.author;
  if (auteur.bot || message.webhookId || message.system || auteur.id === client.user.id) return;
  const guild = message.guild;
  if (!guild) return;
  if (auteur.id === guild.ownerId) return;
  const member = message.member ?? (await guild.members.fetch(auteur.id).catch(() => null));
  if (member) {
    if (config.joueursStaffRoleId && member.roles.cache.has(config.joueursStaffRoleId)) return;
    if (member.permissions.any(PERMS_MODERATION)) return;
  }
  if (enCours.has(auteur.id)) {
    await message.delete().catch(() => undefined);
    return;
  }
  enCours.add(auteur.id);
  setTimeout(() => enCours.delete(auteur.id), 60_000);

  const extrait = message.content.replace(/\s+/g, " ").slice(0, 120) || "(sans texte)";
  const qui = `<@${auteur.id}> (${auteur.tag}, ${auteur.id})`;

  if (config.piegeSimulation) {
    await message.delete().catch(() => undefined);
    await journal(client, `🪤 **Piège (simulation)** : ${qui} a écrit « ${extrait} ». Message effacé, aucun bannissement.`);
    return;
  }
  if (member && !member.bannable) {
    await message.delete().catch(() => undefined);
    await journal(client, `🪤 **Piège** : ${qui} a écrit « ${extrait} » mais le bot ne peut pas le bannir (rôle au-dessus du sien). Message effacé.`);
    return;
  }

  const fin = config.piegeJours > 0 ? new Date(Date.now() + config.piegeJours * 86_400_000) : null;
  await envoyerMp(auteur.id, client, fin);
  try {
    await guild.bans.create(auteur.id, {
      reason: fin ? `${MOTIF} fin=${fin.toISOString()} — message dans le salon piège` : `${MOTIF} définitif — message dans le salon piège`,
      deleteMessageSeconds: config.piegePurgeSecondes,
    });
  } catch (err) {
    await message.delete().catch(() => undefined);
    await journal(client, `🪤 **Piège** : échec du bannissement de ${qui} (${(err as Error).message}). Message effacé.`);
    return;
  }
  const quand = fin ? `jusqu'au ${fin.toLocaleString("fr-FR", { timeZone: "Europe/Paris" })}` : "définitivement";
  await journal(client, `🪤 **Piège** : ${qui} banni ${quand} (compte probablement piraté). Message : « ${extrait} ».`);
}

async function envoyerMp(userId: string, client: Client<true>, fin: Date | null): Promise<void> {
  const duree = fin ? `${config.piegeJours} jours` : "une durée indéterminée";
  const dureeEn = fin ? `${config.piegeJours} days` : "an indefinite period";
  const texte = [
    `**Reborn Roleplay** — tu as été banni pour ${duree} : ton compte a écrit dans un salon piège où personne ne doit écrire.`,
    "Ton compte est probablement **piraté**. Change ton mot de passe Discord et active la double authentification (2FA).",
    "",
    `**Reborn Roleplay** — you have been banned for ${dureeEn}: your account posted in a trap channel where nobody should write.`,
    "Your account was likely **hacked**. Change your Discord password and enable two-factor authentication (2FA).",
  ].join("\n");
  try {
    const user = await client.users.fetch(userId);
    await Promise.race([user.send({ content: texte, allowedMentions: { parse: [] } }), new Promise((r) => setTimeout(r, 3000))]);
  } catch {
    // MP fermés : rien à faire.
  }
}

/** Lève les bannissements du piège arrivés à échéance (date relue dans le motif du bannissement). */
async function debannirEchus(client: Client<true>): Promise<void> {
  const guild: Guild | undefined = client.guilds.cache.get(config.joueursGuildId);
  if (!guild) return;
  const bans = await guild.bans.fetch();
  const now = Date.now();
  for (const ban of bans.values()) {
    const m = RE_FIN.exec(ban.reason ?? "");
    if (!m) continue;
    const fin = Date.parse(m[1]!);
    if (!Number.isFinite(fin) || fin > now) continue;
    try {
      await guild.bans.remove(ban.user.id, `${MOTIF} fin du bannissement automatique`);
      await journal(client, `🪤 **Piège** : fin du bannissement de <@${ban.user.id}> (${ban.user.tag}).`);
    } catch (err) {
      console.warn(`[piège] débannissement de ${ban.user.id} impossible : ${(err as Error).message}`);
    }
  }
}
