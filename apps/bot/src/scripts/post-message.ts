/**
 * ============================================================================
 *  MESSAGE MIS EN FORME — un cadre (composants v2) décrit par un fichier JSON
 * ============================================================================
 *
 *  Poste un message « à la façon des serveurs de référence » : un seul cadre (conteneur) qui enchaîne des
 *  blocs de texte (markdown), des galeries d'images, des séparateurs et des boutons-liens. Sert aux dev
 *  updates (#dev-updates), aux annonces (installation du launcher…) et à tout message rédigé à l'avance.
 *  Le fichier de description vit HORS dépôt (Desktop\Reborn - Gestion\Discord\posts\<nom>.json).
 *
 *  Format :
 *    { "cle": "devupdate-2026-10-14", "salon": "dev-updates", "couleur": "#A0182B",
 *      "blocs": [ { "texte": "## Titre\n…" }, { "images": ["img/a.jpg", "img/b.jpg"] },
 *                 { "separateur": true }, { "liens": [ { "libelle": "Télécharger", "url": "https://…", "emoji": "⬇️" } ] } ] }
 *  Les chemins d'images sont relatifs au fichier JSON. Aucune mention ne notifie (allowedMentions vide).
 *  Option `"banniere": "img/x.png"` : bannière posée AU-DESSUS du cadre, hors barre de couleur (façon Ketto
 *  `informations` / `tuto-whitelist`, Discord joueurs : docs/operations/DISCORD_JOUEURS.md §4).
 *
 *  Garde-fous : SIMULATION par défaut (`--envoyer` pour poster) ; idempotent par `cle` (état dans
 *  Discord\posts\postes.json) ; `--refaire` supprime d'abord le message que CE script a posté pour cette clé.
 *
 *  Usage : pnpm --filter @reborn/bot post:message --guild <id> --fichier <chemin.json> [--envoyer] [--refaire]
 * ============================================================================
 */
import {
  AttachmentBuilder,
  ChannelType,
  Client,
  Events,
  GatewayIntentBits,
  MediaGalleryBuilder,
  MediaGalleryItemBuilder,
  type ForumChannel,
  type TextBasedChannel,
} from "discord.js";
import { config as loadDotenv } from "dotenv";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { construireCadre, payloadCadre, resoudreMentions, trouverSalon, type CadreSpec } from "../messages/cadre.js";

const here = dirname(fileURLToPath(import.meta.url));
function findUp(start: string, name: string): string | null {
  let dir = start;
  for (let i = 0; i < 8; i++) {
    const c = join(dir, name);
    if (existsSync(c)) return c;
    const p = dirname(dir);
    if (p === dir) break;
    dir = p;
  }
  return null;
}
const envPath = findUp(here, ".env");
if (envPath) loadDotenv({ path: envPath });

/** Format du fichier : le cadre (`couleur`, `blocs`, cf. messages/cadre.ts) + où et sous quelle clé le poster. */
interface Spec extends CadreSpec {
  cle: string;
  salon: string;
  /** Pour un forum : titre du post créé. */
  titreForum?: string;
  /** Bannière au-dessus du cadre (hors barre de couleur), chemin relatif au fichier JSON. */
  banniere?: string;
}

const argv = process.argv.slice(2);
let guildId = "";
let fichier = "";
let envoyer = false;
let refaire = false;
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === "--guild") guildId = argv[++i] ?? "";
  else if (a === "--fichier") fichier = argv[++i] ?? "";
  else if (a === "--envoyer") envoyer = true;
  else if (a === "--refaire") refaire = true;
}
if (!/^\d{15,22}$/.test(guildId) || !fichier || !existsSync(fichier)) {
  console.error("Usage : post:message --guild <id> --fichier <chemin.json> [--envoyer] [--refaire]");
  process.exit(2);
}
const token = process.env.DISCORD_BOT_TOKEN;
if (!token) {
  console.error("DISCORD_BOT_TOKEN absent.");
  process.exit(2);
}
const specPath = resolve(fichier);
const spec = JSON.parse(readFileSync(specPath, "utf8")) as Spec;
const specDir = dirname(specPath);

const statePath = join(homedir(), "Desktop", "Reborn - Gestion", "Discord", "posts", "postes.json");
const state: Record<string, { channelId: string; messageId: string; thread?: boolean; at: string }> = existsSync(statePath)
  ? JSON.parse(readFileSync(statePath, "utf8"))
  : {};
const saveState = (): void => writeFileSync(statePath, JSON.stringify(state, null, 2), "utf8");

/** Images = chemins relatifs au fichier JSON ; le nom de la pièce jointe est le nom du fichier. */
function imageDepuisFichier(rel: string): { nom: string; donnees: string } {
  const p = resolve(specDir, rel);
  if (!existsSync(p)) throw new Error(`image introuvable : ${p}`);
  return { nom: basename(p), donnees: p };
}

/** Ajoute la bannière (galerie d'une image) au-dessus du cadre. */
function avecBanniere(p: ReturnType<typeof payloadCadre>, ref: string) {
  const { nom, donnees } = imageDepuisFichier(ref);
  if (p.files.some((f) => f.name === nom)) throw new Error(`bannière « ${nom} » : nom déjà pris par une image du cadre`);
  if (p.files.length + 1 > 10) throw new Error("trop d'images (max 10 par message, bannière comprise)");
  return {
    ...p,
    components: [new MediaGalleryBuilder().addItems(new MediaGalleryItemBuilder().setURL(`attachment://${nom}`)), ...p.components],
    files: [new AttachmentBuilder(donnees, { name: nom }), ...p.files],
  };
}

const client = new Client({ intents: [GatewayIntentBits.Guilds] });
client.once(Events.ClientReady, async (ready) => {
  let code = 0;
  try {
    const guild = ready.guilds.cache.get(guildId);
    if (!guild) throw new Error(`Le bot n'est pas sur ${guildId}.`);
    await guild.channels.fetch();
    await guild.roles.fetch();
    const ch = trouverSalon(guild, spec.salon);
    if (!ch) throw new Error(`salon « ${spec.salon} » introuvable`);
    const { container, files } = construireCadre(spec, {
      image: imageDepuisFichier,
      texte: (md) => resoudreMentions(guild, md),
    });
    console.log(`${envoyer ? "ENVOI" : "SIMULATION"} — « ${spec.cle} » → « ${ch.name} » (${ChannelType[ch.type]}), ${files.length} image(s)${spec.banniere ? " + bannière" : ""}`);
    if (state[spec.cle] && !refaire) {
      console.log(`  = déjà posté (--refaire pour remplacer)`);
      return;
    }
    if (!envoyer) return;
    const old = state[spec.cle];
    if (old) {
      if (old.thread) {
        const th = await guild.channels.fetch(old.messageId).catch(() => null);
        if (th && th.isThread() && th.ownerId === ready.user.id) await th.delete("Message refait");
      } else {
        const oc = (await guild.channels.fetch(old.channelId).catch(() => null)) as TextBasedChannel | null;
        const m = await oc?.messages.fetch(old.messageId).catch(() => null);
        if (m && m.author.id === ready.user.id) await m.delete();
      }
    }
    const base = payloadCadre(container, files);
    const payload = spec.banniere ? avecBanniere(base, spec.banniere) : base;
    if (ch.type === ChannelType.GuildForum) {
      const th = await (ch as ForumChannel).threads.create({ name: spec.titreForum ?? spec.cle, message: payload });
      state[spec.cle] = { channelId: ch.id, messageId: th.id, thread: true, at: new Date().toISOString() };
    } else if (ch.isTextBased()) {
      const msg = await ch.send(payload);
      state[spec.cle] = { channelId: ch.id, messageId: msg.id, at: new Date().toISOString() };
    } else throw new Error("salon non textuel");
    saveState();
    console.log("  ✔ posté");
  } catch (err) {
    console.error("[message] échec :", err);
    code = 1;
  } finally {
    await ready.destroy();
    process.exit(code);
  }
});
await client.login(token);
