/**
 * ============================================================================
 *  ACCUEIL « FAÇON KETTO » — un message par arrivée dans `⛩・bienvenue`
 * ============================================================================
 *
 *  À chaque arrivée sur le serveur JOUEURS (`DISCORD_JOUEURS_GUILD_ID`), le bot poste dans
 *  `DISCORD_BIENVENUE_CHANNEL_ID` :
 *    @pseudo
 *    ┃ [bannière BIENVENUE]
 *    ┃ -# <émoji> Reborn Roleplay • 1 234 membres
 *  Le nouveau membre est le seul notifié (jamais @everyone ni rôle).
 *
 *  Anti-flood (raids) : fenêtre glissante de 60 s.
 *    - jusqu'à `DISCORD_BIENVENUE_SEUIL_GROUPE` arrivées : un message par arrivée ;
 *    - au-delà : un seul message par minute qui cite les arrivants, sans ping ;
 *    - au-delà de `DISCORD_BIENVENUE_SEUIL_RAID` : accueil suspendu 10 min, alerte au journal
 *      (penser à mettre les invitations en pause : Paramètres du serveur → Invitations).
 *  Les comptes bots ne sont pas accueillis. Désactivé si l'une des deux variables est vide.
 *  Doc : docs/operations/DISCORD_JOUEURS.md §5.
 * ============================================================================
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  AttachmentBuilder,
  ContainerBuilder,
  Events,
  MediaGalleryBuilder,
  MediaGalleryItemBuilder,
  MessageFlags,
  TextDisplayBuilder,
  type Client,
  type GuildMember,
  type MessageMentionOptions,
} from "discord.js";
import { config } from "../config.js";
import { journal } from "./journal.js";

const FENETRE_MS = 60_000;
const SUSPENSION_MS = 10 * 60_000;
/** Mentions affichées au plus dans un message groupé (le reste : « et N autres »). */
const MAX_CITES = 20;

/** Bannière livrée avec le bot (apps/bot/assets), même chemin relatif depuis src/ (tsx) et dist/ (node). */
const BANNIERE = resolve(dirname(fileURLToPath(import.meta.url)), "../../assets/bienvenue.png");
const NOM_BANNIERE = "bienvenue.png";

let banniere: Buffer | null = null;
const arrivees: number[] = [];
let tampon: GuildMember[] = [];
let minuterieGroupe: NodeJS.Timeout | null = null;
let suspenduJusqua = 0;
/** File d'envoi : un message à la fois, dans l'ordre (évite les rafales vers l'API Discord). */
let file: Promise<void> = Promise.resolve();

export function startBienvenue(client: Client<true>): void {
  if (!config.joueursGuildId || !config.bienvenueChannelId) {
    console.log("[bienvenue] désactivé (DISCORD_JOUEURS_GUILD_ID / DISCORD_BIENVENUE_CHANNEL_ID vides)");
    return;
  }
  if (existsSync(BANNIERE)) banniere = readFileSync(BANNIERE);
  else console.warn(`[bienvenue] bannière absente (${BANNIERE}) : message sans image`);

  client.on(Events.GuildMemberAdd, (member) => {
    if (member.guild.id !== config.joueursGuildId || member.user.bot) return;
    try {
      surArrivee(client, member);
    } catch (err) {
      console.error("[bienvenue] crash :", err);
    }
  });
  console.log(
    `[bienvenue] actif (groupé au-delà de ${config.bienvenueSeuilGroupe}/min, suspendu au-delà de ${config.bienvenueSeuilRaid}/min)`,
  );
}

function surArrivee(client: Client<true>, member: GuildMember): void {
  const now = Date.now();
  arrivees.push(now);
  while (arrivees.length && arrivees[0]! < now - FENETRE_MS) arrivees.shift();
  const rythme = arrivees.length;

  if (now < suspenduJusqua) return;
  if (rythme > config.bienvenueSeuilRaid) {
    suspenduJusqua = now + SUSPENSION_MS;
    tampon = [];
    if (minuterieGroupe) clearTimeout(minuterieGroupe);
    minuterieGroupe = null;
    void journal(
      client,
      `⚠️ **Arrivées massives** : ${rythme} en 1 min sur « ${member.guild.name} ». Accueil suspendu 10 min. ` +
        "Raid possible : mettre les invitations en pause (Paramètres du serveur → Invitations) et surveiller.",
    );
    return;
  }
  if (rythme > config.bienvenueSeuilGroupe || minuterieGroupe) {
    if (!minuterieGroupe) {
      void journal(client, `Arrivées rapides (${rythme} en 1 min) : accueil regroupé, un message par minute.`);
      minuterieGroupe = setTimeout(() => vider(client), FENETRE_MS);
    }
    tampon.push(member);
    return;
  }
  envoyer(client, [member], member.guild.memberCount);
}

function vider(client: Client<true>): void {
  minuterieGroupe = null;
  const lot = tampon;
  tampon = [];
  if (!lot.length || Date.now() < suspenduJusqua) return;
  envoyer(client, lot, lot[lot.length - 1]!.guild.memberCount);
}

function envoyer(client: Client<true>, membres: GuildMember[], total: number): void {
  file = file.then(async () => {
    try {
      const ch = await client.channels.fetch(config.bienvenueChannelId);
      if (!ch || !ch.isSendable()) throw new Error("salon introuvable ou sans droit d'écriture");
      await ch.send(messageBienvenue(membres, total));
    } catch (err) {
      console.warn(`[bienvenue] envoi impossible : ${(err as Error).message}`);
    }
  });
}

/** Charge utile composants v2 : mention(s) au-dessus, cadre laque avec la bannière et le pied. */
export function messageBienvenue(membres: GuildMember[], total: number) {
  const seul = membres.length === 1;
  const cites = membres.slice(0, MAX_CITES).map((m) => `<@${m.id}>`).join(" ");
  const reste = membres.length - MAX_CITES;
  const entete = reste > 0 ? `${cites} et ${reste} autre${reste > 1 ? "s" : ""}` : cites;

  const cadre = new ContainerBuilder().setAccentColor(parseInt(config.bienvenueCouleur.replace("#", ""), 16));
  const files: AttachmentBuilder[] = [];
  if (banniere) {
    files.push(new AttachmentBuilder(banniere, { name: NOM_BANNIERE }));
    cadre.addMediaGalleryComponents(
      new MediaGalleryBuilder().addItems(new MediaGalleryItemBuilder().setURL(`attachment://${NOM_BANNIERE}`)),
    );
  } else {
    cadre.addTextDisplayComponents(new TextDisplayBuilder().setContent("## Bienvenue sur Reborn"));
  }
  const emoji = config.bienvenueEmoji ? `${config.bienvenueEmoji} ` : "";
  const arrives = seul ? "" : ` • ${membres.length} arrivées`;
  cadre.addTextDisplayComponents(
    new TextDisplayBuilder().setContent(`-# ${emoji}**Reborn Roleplay** • ${total.toLocaleString("fr-FR")} membres${arrives}`),
  );

  // Seul le nouveau membre est notifié ; en mode groupé (flood), personne.
  const allowedMentions: MessageMentionOptions = seul ? { parse: [], users: [membres[0]!.id] } : { parse: [] };
  return {
    components: [new TextDisplayBuilder().setContent(entete), cadre],
    files,
    flags: MessageFlags.IsComponentsV2 as const,
    allowedMentions,
  };
}
