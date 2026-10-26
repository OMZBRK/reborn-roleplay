import { EmbedBuilder } from "discord.js";

/**
 * Limites Discord d'un embed, en caractères. `String.length` compte en UTF-16 :
 * il ne compte jamais moins que Discord, une longueur JS sous la limite passe donc.
 */
export const LIMITES_EMBED = {
  titre: 256,
  description: 4096,
  champNom: 256,
  champValeur: 1024,
} as const;

/** Champ d'embed (forme attendue par `EmbedBuilder.addFields`). */
export interface ChampEmbed {
  name: string;
  value: string;
  inline?: boolean;
}

/** Vrai si `code` est la première moitié d'une paire de substitution UTF-16 (émoji, etc.). */
function debutDePaire(code: number): boolean {
  return code >= 0xd800 && code <= 0xdbff;
}

/**
 * Découpe un texte en morceaux d'au plus `max` caractères, sans rien perdre :
 * coupe de préférence à un saut de paragraphe, sinon à un saut de ligne, sinon
 * à une espace, et jamais au milieu d'un caractère (paire de substitution).
 * Seuls les blancs autour des coupes disparaissent. Texte vide : aucun morceau.
 */
export function decouperTexte(texte: string, max: number): string[] {
  const morceaux: string[] = [];
  let reste = texte.trim();
  while (reste.length > max) {
    // +1 : un séparateur placé pile à la limite reste utilisable.
    const fenetre = reste.slice(0, max + 1);
    let coupe = -1;
    for (const sep of ["\n\n", "\n", " "]) {
      const i = fenetre.lastIndexOf(sep);
      // Pas de coupe trop tôt : on ne veut pas de morceaux minuscules.
      if (i >= max / 2) {
        coupe = i;
        break;
      }
    }
    if (coupe < 0) {
      coupe = debutDePaire(reste.charCodeAt(max - 1)) ? max - 1 : max;
    }
    const morceau = reste.slice(0, coupe).trimEnd();
    if (morceau.length > 0) morceaux.push(morceau);
    reste = reste.slice(coupe).trimStart();
  }
  if (reste.length > 0) morceaux.push(reste);
  return morceaux;
}

/**
 * Champs d'embed pour un texte qui peut dépasser 1 024 caractères : un champ
 * par tranche, « (suite) » à partir du deuxième. Texte vide : aucun champ.
 */
export function champsTexte(nom: string, texte: string): ChampEmbed[] {
  return decouperTexte(texte, LIMITES_EMBED.champValeur).map((value, i) => ({
    name: truncate(i === 0 ? nom : `${nom} (suite)`, LIMITES_EMBED.champNom),
    value,
  }));
}

/**
 * Découpe un texte long en autant d'embeds que nécessaire pour respecter la
 * limite d'une description (4 096 caractères), sans couper un mot quand c'est
 * possible. Le suffixe « (i/total) » n'apparaît que s'il y a plusieurs pages.
 */
export function paginateLong(
  title: string,
  color: number,
  content: string,
): EmbedBuilder[] {
  const pages = decouperTexte(content, LIMITES_EMBED.description);
  if (pages.length === 0) {
    return [
      new EmbedBuilder()
        .setTitle(truncate(title, LIMITES_EMBED.titre))
        .setColor(color)
        .setDescription("*(vide)*"),
    ];
  }
  return pages.map((page, i) => {
    const suffixe = pages.length > 1 ? ` (${i + 1}/${pages.length})` : "";
    return new EmbedBuilder()
      .setTitle(truncate(title, LIMITES_EMBED.titre - suffixe.length) + suffixe)
      .setColor(color)
      .setDescription(page);
  });
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

/**
 * Pied d'une annonce du salon staff : « application <id> · Pris par X · Statut : Y ».
 * Remplace le segment qui commence par l'un des `debuts` (ou l'ajoute à la fin),
 * sans toucher aux autres. Le premier segment (« application <id> ») reste en
 * tête : le bot le relit pour retrouver la candidature.
 */
export function remplacerSegmentPied(
  pied: string,
  debuts: readonly string[],
  segment: string | null,
): string {
  const morceaux = pied
    .split(" · ")
    .filter((m, i) => m.length > 0 && (i === 0 || !debuts.some((d) => m.startsWith(d))));
  if (segment) morceaux.push(segment);
  return morceaux.join(" · ");
}

/**
 * Débuts du segment de prise en charge du pied d'annonce (« Pris par X »,
 * « Disponible (libéré par X) », « Libéré par X ») : un seul à la fois.
 */
export const SEGMENTS_PRISE_EN_CHARGE: readonly string[] = ["Pris par", "Disponible", "Libéré par"];

/** Coupe à `max` caractères avec « … », sans jamais couper un caractère en deux. */
export function truncate(s: string, max: number): string {
  if (s.length <= max) return s;
  let fin = max - 1;
  if (fin > 0 && debutDePaire(s.charCodeAt(fin - 1))) fin--;
  return s.slice(0, fin) + "…";
}
