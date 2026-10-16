/**
 * ============================================================================
 *  MESSAGES DU PANEL — annonces, dev updates et build updates du Discord staff
 * ============================================================================
 *
 *  Appelé par webhook-server.ts sur les routes signées HMAC (même secret, même vérification que les autres) :
 *    POST /webhooks/staff-message          → aperçu ou publication
 *    POST /webhooks/staff-message/edit     → réécrit un message publié par le bot
 *    POST /webhooks/staff-message/delete   → supprime un message publié par le bot
 *
 *  Le contenu (spec de cadre + images) est rédigé dans le panel staff (apps/admin, page « Discord ») et
 *  stocké par l'API (module staff-messages). Le rendu est celui de scripts/post-message.ts (messages/cadre.ts).
 *
 *  Salons : uniquement ceux fixés par l'environnement (aucun ID dans le dépôt) :
 *    DISCORD_STAFF_GUILD_ID, DISCORD_STAFF_ANNONCES_CHANNEL_ID, DISCORD_STAFF_DEVUPDATES_CHANNEL_ID,
 *    DISCORD_STAFF_BUILDUPDATES_CHANNEL_ID,
 *    DISCORD_STAFF_APERCU_CHANNEL_ID (optionnel : sans lui, l'aperçu renvoie seulement le rendu JSON).
 *  Chaque salon doit appartenir au serveur staff. Le bot ne modifie / supprime QUE ses propres messages.
 * ============================================================================
 */
import type { Client, Guild, Message, SendableChannels } from "discord.js";
import { config } from "../config.js";
import {
  construireCadre,
  ErreurCadre,
  payloadCadre,
  resoudreMentions,
  trouverRole,
  type CadreSpec,
  type Ping,
} from "./cadre.js";

export const CANAUX_STAFF = ["annonces", "dev-updates", "build-updates"] as const;
export type CanalStaff = (typeof CANAUX_STAFF)[number];
export type PingStaff = "aucun" | "testeurs" | "everyone";

export interface ImageTransmise {
  /** Nom de la pièce jointe, cité tel quel dans `spec.blocs[].images`. */
  nom: string;
  /** Contenu en base64 (voie normale : l'API lit le fichier et l'envoie). */
  base64?: string;
  /** Ou URL (signée) que le bot télécharge. */
  url?: string;
}

export interface StaffMessagePayload {
  canal: CanalStaff;
  mode: "apercu" | "publier";
  spec: CadreSpec;
  images?: ImageTransmise[];
  /** Désactivé par défaut. `testeurs` seulement en dev-updates, `everyone` seulement en annonces ; aucun en build-updates. */
  ping?: PingStaff;
  /** ID d'un message du bot à remplacer : supprimé APRÈS l'envoi du nouveau (même salon, auteur = le bot). */
  cleRemplacement?: string;
}

export interface StaffMessageEditPayload {
  canal: CanalStaff;
  messageId: string;
  spec: CadreSpec;
  images?: ImageTransmise[];
  ping?: PingStaff;
}

export interface StaffMessageDeletePayload {
  canal: CanalStaff | "apercu";
  messageId: string;
}

/** Erreur à renvoyer telle quelle au client HTTP (l'API) avec son statut. */
export class ErreurRequete extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly details?: string[],
  ) {
    super(message);
  }
}

const MAX_IMAGE_OCTETS = 10 * 1024 * 1024;
const MAX_TOTAL_OCTETS = 25 * 1024 * 1024;
const RE_SNOWFLAKE = /^\d{15,22}$/;
const RE_NOM = /^[A-Za-z0-9._-]{1,100}$/;

/** Salon (ID fixé par l'environnement) et nom de la variable, par canal. */
const SALONS: Record<CanalStaff | "apercu", { id: () => string; env: string }> = {
  annonces: { id: () => config.staffAnnoncesChannelId, env: "DISCORD_STAFF_ANNONCES_CHANNEL_ID" },
  "dev-updates": { id: () => config.staffDevUpdatesChannelId, env: "DISCORD_STAFF_DEVUPDATES_CHANNEL_ID" },
  "build-updates": { id: () => config.staffBuildUpdatesChannelId, env: "DISCORD_STAFF_BUILDUPDATES_CHANNEL_ID" },
  apercu: { id: () => config.staffApercuChannelId, env: "DISCORD_STAFF_APERCU_CHANNEL_ID" },
};

function idSalon(canal: CanalStaff | "apercu"): string {
  const { id, env } = SALONS[canal];
  const valeur = id();
  if (!valeur) throw new ErreurRequete(503, `salon « ${canal} » non configuré (${env})`);
  return valeur;
}

async function serveurStaff(client: Client): Promise<Guild> {
  if (!config.staffGuildId) throw new ErreurRequete(503, "DISCORD_STAFF_GUILD_ID non configuré");
  const guild = await client.guilds.fetch(config.staffGuildId).catch(() => null);
  if (!guild) throw new ErreurRequete(503, "le bot n'est pas sur le serveur staff");
  // Caches salons + rôles pour résoudre {#salon} / {@rôle}.
  await guild.channels.fetch();
  await guild.roles.fetch();
  return guild;
}

async function salon(client: Client, guild: Guild, canal: CanalStaff | "apercu"): Promise<SendableChannels> {
  const id = idSalon(canal);
  const ch = await client.channels.fetch(id).catch(() => null);
  if (!ch || ch.isDMBased() || ch.guildId !== guild.id) {
    throw new ErreurRequete(503, `salon « ${canal} » introuvable sur le serveur staff`);
  }
  if (!ch.isSendable()) throw new ErreurRequete(503, `salon « ${canal} » : le bot ne peut pas y écrire`);
  return ch;
}

async function chargerImages(images: ImageTransmise[] = []): Promise<Map<string, Buffer>> {
  const out = new Map<string, Buffer>();
  let total = 0;
  for (const img of images) {
    if (!img || typeof img.nom !== "string" || !RE_NOM.test(img.nom)) {
      throw new ErreurRequete(400, `nom d'image invalide : ${String(img?.nom)}`);
    }
    let buf: Buffer;
    if (img.base64) buf = Buffer.from(img.base64, "base64");
    else if (img.url && /^https?:\/\//.test(img.url)) {
      const res = await fetch(img.url, { signal: AbortSignal.timeout(20_000) });
      if (!res.ok) throw new ErreurRequete(400, `image ${img.nom} : téléchargement ${res.status}`);
      buf = Buffer.from(await res.arrayBuffer());
    } else throw new ErreurRequete(400, `image ${img.nom} : ni base64 ni URL`);
    if (buf.length === 0) throw new ErreurRequete(400, `image ${img.nom} vide`);
    if (buf.length > MAX_IMAGE_OCTETS) throw new ErreurRequete(413, `image ${img.nom} > 10 Mo`);
    total += buf.length;
    out.set(img.nom, buf);
  }
  if (total > MAX_TOTAL_OCTETS) throw new ErreurRequete(413, "images > 25 Mo au total");
  return out;
}

function pingPour(guild: Guild | null, canal: CanalStaff, ping: PingStaff | undefined, notifier: boolean): Ping | undefined {
  if (!ping || ping === "aucun") return undefined;
  if (ping === "everyone") {
    if (canal !== "annonces") throw new ErreurRequete(400, "@everyone réservé aux annonces");
    return { texte: "@everyone", mentions: notifier ? { parse: ["everyone"] } : { parse: [] } };
  }
  if (ping === "testeurs") {
    if (canal !== "dev-updates") throw new ErreurRequete(400, "@Testeur réservé aux dev updates");
    if (!guild) return { texte: "@Testeur", mentions: { parse: [] } };
    const role = config.staffTesteurRoleId
      ? guild.roles.cache.get(config.staffTesteurRoleId)
      : trouverRole(guild, "Testeur");
    if (!role) throw new ErreurRequete(503, "rôle « Testeur » introuvable sur le serveur staff");
    return { texte: `<@&${role.id}>`, mentions: notifier ? { roles: [role.id] } : { parse: [] } };
  }
  throw new ErreurRequete(400, `ping inconnu : ${String(ping)}`);
}

function rendre(guild: Guild | null, spec: CadreSpec, images: Map<string, Buffer>) {
  try {
    return construireCadre(spec, {
      texte: (md) => resoudreMentions(guild, md),
      image: (ref) => {
        const donnees = images.get(ref);
        if (!donnees) throw new ErreurCadre([`image « ${ref} » citée mais non transmise`]);
        return { nom: ref, donnees };
      },
    });
  } catch (err) {
    if (err instanceof ErreurCadre) throw new ErreurRequete(400, "spec invalide", err.details);
    throw err;
  }
}

/** Récupère un message du salon et vérifie qu'il a été posté par CE bot. */
async function messageDuBot(client: Client, ch: SendableChannels, messageId: string): Promise<Message | null> {
  if (!RE_SNOWFLAKE.test(messageId)) throw new ErreurRequete(400, "messageId invalide");
  if (!("messages" in ch)) throw new ErreurRequete(503, "salon sans historique de messages");
  const msg = await ch.messages.fetch(messageId).catch(() => null);
  if (!msg) return null;
  if (msg.author.id !== client.user?.id) throw new ErreurRequete(403, "ce message n'a pas été posté par le bot");
  return msg;
}

function verifierCanal(canal: unknown): asserts canal is CanalStaff {
  if (!(CANAUX_STAFF as readonly unknown[]).includes(canal)) throw new ErreurRequete(400, `canal inconnu : ${String(canal)}`);
}

export async function posterStaffMessage(client: Client, p: StaffMessagePayload) {
  verifierCanal(p.canal);
  if (p.mode !== "apercu" && p.mode !== "publier") throw new ErreurRequete(400, `mode inconnu : ${String(p.mode)}`);
  const images = await chargerImages(p.images);

  if (p.mode === "apercu") {
    if (!config.staffApercuChannelId) {
      // Sans serveur staff joignable, les mentions restent en texte (`#salon`, `@rôle`).
      const guild = config.staffGuildId ? await serveurStaff(client).catch(() => null) : null;
      const { container, files } = rendre(guild, p.spec, images);
      const ping = pingPour(guild, p.canal, p.ping, false);
      // Pas de salon de test : on renvoie le rendu brut (le panel a son propre aperçu visuel).
      const rendu = payloadCadre(container, [], { ping });
      return {
        messageId: null,
        url: null,
        rendu: {
          components: rendu.components.map((c) => c.toJSON()),
          pieces: files.map((f) => f.name),
        },
      };
    }
    const guild = await serveurStaff(client);
    const { container, files } = rendre(guild, p.spec, images);
    const ping = pingPour(guild, p.canal, p.ping, false);
    const ch = await salon(client, guild, "apercu");
    const entete = `-# Aperçu · ${p.canal === "annonces" ? "annonces" : "dev updates"} · non publié`;
    const sent = await ch.send(payloadCadre(container, files, { ping, entete }));
    await supprimerAncien(client, ch, p.cleRemplacement);
    return { messageId: sent.id, channelId: sent.channelId, url: sent.url };
  }

  const guild = await serveurStaff(client);
  const { container, files } = rendre(guild, p.spec, images);
  const ch = await salon(client, guild, p.canal);
  const ping = pingPour(guild, p.canal, p.ping, true);
  const sent = await ch.send(payloadCadre(container, files, { ping }));
  await supprimerAncien(client, ch, p.cleRemplacement);
  return { messageId: sent.id, channelId: sent.channelId, url: sent.url };
}

async function supprimerAncien(client: Client, ch: SendableChannels, id: string | undefined) {
  if (!id) return;
  try {
    const old = await messageDuBot(client, ch, id);
    await old?.delete();
  } catch (err) {
    console.warn(`[staff-message] remplacement : ancien message ${id} non supprimé :`, (err as Error).message);
  }
}

export async function modifierStaffMessage(client: Client, p: StaffMessageEditPayload) {
  verifierCanal(p.canal);
  const guild = await serveurStaff(client);
  const ch = await salon(client, guild, p.canal);
  const msg = await messageDuBot(client, ch, p.messageId);
  if (!msg) throw new ErreurRequete(404, "message introuvable sur Discord (supprimé ?)");
  const images = await chargerImages(p.images);
  const { container, files } = rendre(guild, p.spec, images);
  // Une modification ne notifie jamais : la ligne de ping reste affichée, sans mention active.
  const ping = pingPour(guild, p.canal, p.ping, false);
  const payload = payloadCadre(container, files, { ping });
  // `attachments: []` retire les anciennes pièces jointes ; `files` pose les nouvelles.
  const edited = await msg.edit({ ...payload, attachments: [] });
  return { messageId: edited.id, channelId: edited.channelId, url: edited.url };
}

export async function supprimerStaffMessage(client: Client, p: StaffMessageDeletePayload) {
  if (p.canal !== "apercu") verifierCanal(p.canal);
  const guild = await serveurStaff(client);
  const ch = await salon(client, guild, p.canal);
  const msg = await messageDuBot(client, ch, p.messageId);
  if (!msg) return { deleted: true, absent: true };
  await msg.delete();
  return { deleted: true, absent: false };
}
