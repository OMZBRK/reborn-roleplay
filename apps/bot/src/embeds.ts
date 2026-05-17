import { EmbedBuilder } from "discord.js";

/**
 * Split un champ texte en autant d'embeds que necessaires pour respecter
 * la limite Discord embed.description (4096 chars). Le suffixe "(i/total)"
 * n'apparait que quand on a plusieurs pages.
 */
export function paginateLong(
  title: string,
  color: number,
  content: string,
): EmbedBuilder[] {
  const trimmed = content.trim();
  if (trimmed.length === 0) {
    return [
      new EmbedBuilder()
        .setTitle(title)
        .setColor(color)
        .setDescription("*(vide)*"),
    ];
  }
  const PAGE_SIZE = 4000;
  if (trimmed.length <= PAGE_SIZE) {
    return [
      new EmbedBuilder().setTitle(title).setColor(color).setDescription(trimmed),
    ];
  }
  const total = Math.ceil(trimmed.length / PAGE_SIZE);
  const out: EmbedBuilder[] = [];
  for (let i = 0; i < total; i++) {
    const slice = trimmed.slice(i * PAGE_SIZE, (i + 1) * PAGE_SIZE);
    out.push(
      new EmbedBuilder()
        .setTitle(`${title} (${i + 1}/${total})`)
        .setColor(color)
        .setDescription(slice),
    );
  }
  return out;
}

/**
 * Pack des embeds en batches respectant les deux caps Discord par message :
 * 10 embeds max ET 6000 chars cumules (toutes proprietes confondues —
 * title + description + fields + author + footer). Marge 200 chars pour
 * absorber les overheads JSON.
 */
export function packEmbedsForMessages(embeds: EmbedBuilder[]): EmbedBuilder[][] {
  const MAX_COUNT = 10;
  const MAX_CHARS = 5800;
  const out: EmbedBuilder[][] = [];
  let current: EmbedBuilder[] = [];
  let currentChars = 0;
  for (const e of embeds) {
    const size = embedCharSize(e);
    const wouldOverflow =
      current.length >= MAX_COUNT ||
      (current.length > 0 && currentChars + size > MAX_CHARS);
    if (wouldOverflow) {
      out.push(current);
      current = [];
      currentChars = 0;
    }
    current.push(e);
    currentChars += size;
  }
  if (current.length > 0) out.push(current);
  return out;
}

export function embedCharSize(e: EmbedBuilder): number {
  const d = e.toJSON();
  let n = 0;
  if (d.title) n += d.title.length;
  if (d.description) n += d.description.length;
  if (d.footer?.text) n += d.footer.text.length;
  if (d.author?.name) n += d.author.name.length;
  for (const f of d.fields ?? []) n += f.name.length + f.value.length;
  return n;
}

export function computeAge(iso: string): number | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - d.getFullYear();
  const m = now.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < d.getDate())) age--;
  return age;
}

export function formatDateFr(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const months = [
    "janvier", "février", "mars", "avril", "mai", "juin",
    "juillet", "août", "septembre", "octobre", "novembre", "décembre",
  ];
  return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

export function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, max - 1) + "…";
}
