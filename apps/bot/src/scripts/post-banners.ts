/**
 * ============================================================================
 *  BANNIÈRES DU DISCORD STAFF — pose des messages d'en-tête par salon
 * ============================================================================
 *
 *  Poste, dans les salons clés de RBN - STAFF, un message d'en-tête en COMPOSANTS V2 : un seul cadre
 *  (conteneur) avec la bannière en haut (titre seul, sans sous-titre) et, pour quelques salons, un court
 *  texte dessous à la même largeur (hiérarchie, modèle d'absence, mode d'emploi des retours de test) —
 *  comme sur les serveurs de référence. Décision du 14/10 : docs/operations/DISCORD_STAFF.md §7.
 *
 *  Garde-fous :
 *   - SIMULATION par défaut : affiche ce qui serait posté. Rien n'est envoyé sans `--envoyer`.
 *   - Idempotent : les messages posés sont notés dans un fichier d'état HORS dépôt
 *     (`Desktop\Reborn - Gestion\Discord\bannieres-postees.json`) ; un salon déjà fait est sauté.
 *     `--refaire <clé>` (ou `--refaire-tout`) SUPPRIME d'abord ce que CE script a posté (ID noté dans
 *     l'état, auteur = le bot, et rien d'autre), puis reposte.
 *   - N'écrit que des messages (et un post de forum) : aucun salon, rôle ou permission n'est modifié.
 *
 *  Usage (depuis la racine du monorepo) :
 *    pnpm --filter @reborn/bot post:banners --guild <id>                    → simulation
 *    pnpm --filter @reborn/bot post:banners --guild <id> --envoyer          → envoi
 *    pnpm --filter @reborn/bot post:banners --guild <id> --refaire-tout --envoyer
 *    options : --bannieres <dossier> (défaut Desktop\Reborn - Gestion\Maquettes\discord\bannieres)
 * ============================================================================
 */
import {
  AttachmentBuilder,
  ChannelType,
  Client,
  ContainerBuilder,
  Events,
  GatewayIntentBits,
  MediaGalleryBuilder,
  MediaGalleryItemBuilder,
  MessageFlags,
  SeparatorBuilder,
  SeparatorSpacingSize,
  TextDisplayBuilder,
  type ForumChannel,
  type Guild,
  type GuildBasedChannel,
  type TextBasedChannel,
} from "discord.js";
import { config as loadDotenv } from "dotenv";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

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

const argv = process.argv.slice(2);
let guildId = "";
let envoyer = false;
let refaireTout = false;
const refaire = new Set<string>();
const base = join(homedir(), "Desktop", "Reborn - Gestion");
let bannieres = join(base, "Maquettes", "discord", "bannieres");
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a === "--guild") guildId = argv[++i] ?? "";
  else if (a === "--envoyer") envoyer = true;
  else if (a === "--refaire") refaire.add(argv[++i] ?? "");
  else if (a === "--refaire-tout") refaireTout = true;
  else if (a === "--bannieres") bannieres = argv[++i] ?? bannieres;
}
if (!/^\d{15,22}$/.test(guildId)) {
  console.error("Usage : post:banners --guild <id> [--envoyer] [--refaire <clé> | --refaire-tout] [--bannieres <dossier>]");
  process.exit(2);
}
const token = process.env.DISCORD_BOT_TOKEN;
if (!token) {
  console.error("DISCORD_BOT_TOKEN absent.");
  process.exit(2);
}

interface Etat {
  channelId: string;
  messageId: string;
  /** Ancienne version : l'embed de texte était un 2e message posté juste après la bannière. */
  extraIds?: string[];
  thread?: boolean;
  at: string;
}
const statePath = join(base, "Discord", "bannieres-postees.json");
const state: Record<string, Etat> = existsSync(statePath) ? JSON.parse(readFileSync(statePath, "utf8")) : {};
function saveState(): void {
  writeFileSync(statePath, JSON.stringify(state, null, 2), "utf8");
}

const LAQUE = 0xa0182b;

/** Nom de salon sans emoji ni séparateur, en minuscules, sans accents. */
function norm(s: string): string {
  return s
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^\p{L}\p{N}-]+/gu, " ")
    .trim()
    .toLowerCase();
}
function findChannel(guild: Guild, name: string): GuildBasedChannel | undefined {
  const n = norm(name);
  return guild.channels.cache.find((c) => norm(c.name) === n);
}
function mention(guild: Guild, name: string): string {
  const c = findChannel(guild, name);
  return c ? `<#${c.id}>` : `#${name}`;
}
function role(guild: Guild, name: string): string {
  const r = guild.roles.cache.find((x) => x.name === name);
  return r ? `<@&${r.id}>` : `**${name}**`;
}

interface Poste {
  cle: string;
  salon: string;
  fichier: string;
  /** Texte (markdown) affiché sous la bannière, dans le même cadre. */
  texte?: (g: Guild) => string;
  /** Pour un forum : titre du post épinglé créé. */
  postForum?: string;
}

const POSTES: Poste[] = [
  {
    cle: "reglement",
    salon: "règlement",
    fichier: "welcome.png",
    texte: (g) =>
      [
        "## Bienvenue dans l'équipe Reborn",
        "Le serveur du staff du réseau **Reborn**.",
        "",
        `Avant tout : lis ${mention(g, "règlement-staff")} et ${mention(g, "hiérarchie")}, puis passe dire bonjour dans ${mention(g, "général-staff")}.`,
      ].join("\n"),
  },
  { cle: "reglement-staff", salon: "règlement-staff", fichier: "rules.png" },
  {
    cle: "hierarchie",
    salon: "hiérarchie",
    fichier: "hierarchy.png",
    texte: (g) =>
      [
        "## La hiérarchie du réseau",
        "Deux pôles : **Roleplay** fait vivre un projet (il porte toujours le tag du projet), **Création** fabrique les serveurs du réseau. Le tag de pôle compte ; les spécialités servent à mentionner les bons métiers.",
        "### Direction",
        `${role(g, "| Fondateur")} — porte le réseau et ses projets`,
        `${role(g, "| Co-Fondateur")} — seconde les fondateurs`,
        `${role(g, "| Direction")} — gouverne le réseau au quotidien`,
        `${role(g, "Haut Staff")} — pour mentionner Direction, Heads et Leads`,
        "### Gérance",
        `${role(g, "| Head of Reborn")} · ${role(g, "| Head of Projet X")} — un chef par projet du réseau`,
        `${role(g, "| Lead Roleplay")} — dirige le pôle Roleplay`,
        `${role(g, "| Lead Creation")} — dirige le pôle Création`,
        `${role(g, "| Lead Dev")} — mods, plugins, launcher, bot`,
        `${role(g, "| Lead Communication")} — partenariats, langues, réseaux`,
        `${role(g, "| Lead Staff")} — recrutement, formation, absences`,
        "### Responsables",
        `${role(g, "| Responsable Roleplay")} · ${role(g, "| Responsable Création")} — encadrent le staff de leur pôle`,
        "### Staff",
        `${role(g, "| Roleplay")} · ${role(g, "| Création")} — le tag de pôle`,
        `${role(g, "Shinobi Reborn")} · ${role(g, "Projet X")} — le tag de projet`,
        `${role(g, "Staff")} — le socle commun · ${role(g, "En formation")} · ${role(g, "Absent")}`,
        "### Communauté",
        `${role(g, "| Invité(e)")} · ${role(g, "| Partenaire")}`,
      ].join("\n"),
  },
  { cle: "annonces", salon: "annonces", fichier: "announcements.png" },
  { cle: "dev-updates", salon: "dev-updates", fichier: "dev_updates.png" },
  {
    cle: "retours-de-test",
    salon: "retours-de-test",
    fichier: "test_feedback.png",
    postForum: "📌 Comment faire un retour de test",
    texte: () =>
      [
        "## Un post par sujet",
        "**Titre** : le numéro de la recette + le sujet (ex. « 10.3 — nom pas teinté »).",
        "**Version** : la version du mod (Échap → en bas).",
        "**Ce que tu as fait** → **ce que tu as vu** → **ce qui était attendu**.",
        "**Capture** ou courte vidéo si c'est visuel, **FPS** si c'est lent.",
        "",
        "-# Ce qui casse le jeu d'abord, les détails visuels ensuite.",
      ].join("\n"),
  },
  {
    cle: "absences",
    salon: "absences",
    fichier: "absences.png",
    texte: () =>
      ["## Prévenir une absence", "```", "Du : jj/mm", "Au : jj/mm", "Raison (facultatif) :", "Joignable : oui / non", "```"].join("\n"),
  },
  { cle: "reunion", salon: "Réunion", fichier: "meeting.png" },
];

/** Un seul cadre : bannière pleine largeur, puis le texte dessous. */
function cadre(p: Poste, g: Guild): ContainerBuilder {
  const c = new ContainerBuilder()
    .setAccentColor(LAQUE)
    .addMediaGalleryComponents(
      new MediaGalleryBuilder().addItems(new MediaGalleryItemBuilder().setURL(`attachment://${p.fichier}`)),
    );
  const t = p.texte?.(g);
  if (t) {
    c.addSeparatorComponents(new SeparatorBuilder().setDivider(true).setSpacing(SeparatorSpacingSize.Small));
    c.addTextDisplayComponents(new TextDisplayBuilder().setContent(t));
  }
  return c;
}

/** Supprime ce que CE script a posté pour une clé : messages du bot seulement, ou le post de forum qu'il a créé. */
async function nettoyer(guild: Guild, cle: string, botId: string): Promise<void> {
  const st = state[cle];
  if (!st) return;
  const ch = await guild.channels.fetch(st.channelId).catch(() => null);
  if (ch && (st.thread || ch.type === ChannelType.GuildForum)) {
    const th = await guild.channels.fetch(st.messageId).catch(() => null);
    if (th && th.isThread() && th.ownerId === botId) await th.delete("Bannière refaite (composants v2)");
  } else if (ch && ch.isTextBased()) {
    const tc = ch as TextBasedChannel;
    const first = await tc.messages.fetch(st.messageId).catch(() => null);
    const ids = new Set<string>([st.messageId, ...(st.extraIds ?? [])]);
    if (first) {
      const suivants = await tc.messages.fetch({ after: st.messageId, limit: 3 }).catch(() => null);
      suivants?.forEach((m) => {
        if (m.author.id === botId && Math.abs(m.createdTimestamp - first.createdTimestamp) < 60_000) ids.add(m.id);
      });
    }
    for (const id of ids) {
      const m = await tc.messages.fetch(id).catch(() => null);
      if (m && m.author.id === botId) await m.delete();
    }
  }
  delete state[cle];
  saveState();
}

const client = new Client({ intents: [GatewayIntentBits.Guilds] });
client.once(Events.ClientReady, async (ready) => {
  let code = 0;
  try {
    const guild = ready.guilds.cache.get(guildId);
    if (!guild) throw new Error(`Le bot n'est pas sur ${guildId}.`);
    await guild.channels.fetch();
    await guild.roles.fetch();
    console.log(`${envoyer ? "ENVOI" : "SIMULATION"} — ${guild.name}\n`);
    for (const p of POSTES) {
      const ch = findChannel(guild, p.salon);
      const img = join(bannieres, p.fichier);
      if (!ch) {
        console.log(`  ✗ ${p.cle} : salon « ${p.salon} » introuvable`);
        code = 1;
        continue;
      }
      if (!existsSync(img)) {
        console.log(`  ✗ ${p.cle} : bannière ${img} introuvable`);
        code = 1;
        continue;
      }
      const redo = refaireTout || refaire.has(p.cle);
      if (state[p.cle] && !redo) {
        console.log(`  = ${p.cle} : déjà posté dans « ${ch.name} » (--refaire ${p.cle} pour reposter)`);
        continue;
      }
      console.log(
        `  → ${p.cle} : « ${ch.name} » (${ChannelType[ch.type]}) — ${p.fichier}${p.texte ? " + texte" : ""}` +
          `${p.postForum ? ` · post « ${p.postForum} »` : ""}${state[p.cle] ? " · remplace l'ancien" : ""}`,
      );
      if (!envoyer) continue;
      if (state[p.cle]) await nettoyer(guild, p.cle, ready.user.id);
      const file = new AttachmentBuilder(img, { name: p.fichier });
      const payload = { components: [cadre(p, guild)], files: [file], flags: MessageFlags.IsComponentsV2 as const };
      let messageId: string;
      let thread = false;
      if (ch.type === ChannelType.GuildForum) {
        const th = await (ch as ForumChannel).threads.create({ name: p.postForum ?? "📌 À lire", message: payload });
        await th.pin().catch(() => undefined);
        messageId = th.id;
        thread = true;
      } else if (ch.isTextBased()) {
        const msg = await ch.send(payload);
        messageId = msg.id;
      } else {
        console.log(`    ✗ salon non textuel`);
        code = 1;
        continue;
      }
      state[p.cle] = { channelId: ch.id, messageId, thread, at: new Date().toISOString() };
      saveState();
      console.log(`    ✔ posté`);
    }
  } catch (err) {
    console.error("[bannières] échec :", err);
    code = 1;
  } finally {
    await ready.destroy();
    process.exit(code);
  }
});
await client.login(token);
