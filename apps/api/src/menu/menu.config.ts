/**
 * Configuration + helpers purs du compteur Discord du panneau public
 * /v1/menu/panel (CANDIDATURE_ET_DOUANE §5.2).
 *
 * Le compteur lit l'invitation publique du serveur des joueurs
 * (`GET /invites/<code>?with_counts=true`), sans jeton : il ne dépend ni du
 * bot ni d'un identifiant de serveur, et compte par construction le serveur
 * où mène le bouton « Rejoindre ». L'ancien compteur lisait
 * `DISCORD_GUILD_ID`, c'est-à-dire un autre serveur.
 *
 * Tout est isolé dans des fonctions pures et testables (menu.config.spec.ts).
 */

/** Compteur frais : Discord limite l'endpoint d'invitation par adresse. */
export const DISCORD_FRESH_MS = 5 * 60_000;
/** Repli : dernière valeur connue servie au plus 30 min si Discord se tait. */
export const DISCORD_STALE_MAX_MS = 30 * 60_000;
/** Après une panne passagère (réseau, 5xx, réponse illisible). */
export const DISCORD_RETRY_MS = 60_000;
/** Après une invitation inconnue ou expirée : inutile d'insister chaque minute. */
export const DISCORD_RETRY_UNKNOWN_INVITE_MS = 10 * 60_000;
/** Plafond d'attente demandé par Discord (429) que l'on accepte d'honorer. */
export const DISCORD_RETRY_MAX_MS = 15 * 60_000;

/** Format d'un code d'invitation Discord : lettres, chiffres, tirets. */
const INVITE_CODE_RE = /^[A-Za-z0-9-]{2,32}$/;

/** Lien complet collé à la place du code (discord.gg/…, discord.com/invite/…). */
const INVITE_LINK_RE =
  /^(?:https?:\/\/)?(?:www\.)?(?:discord\.gg|discord(?:app)?\.com\/invite)\/([^/?#\s]+)\/?$/i;

/**
 * Code d'invitation lu dans `DISCORD_INVITE_CODE`. Accepte un lien complet
 * collé par erreur. `null` si la variable est absente ou mal formée : le
 * compteur est alors indisponible, le launcher et le mod gardent leur lien
 * de repli.
 */
export function discordInviteCode(
  env: NodeJS.ProcessEnv = process.env,
): string | null {
  let raw = env.DISCORD_INVITE_CODE?.trim();
  if (!raw) return null;
  const link = INVITE_LINK_RE.exec(raw);
  if (link) raw = link[1];
  return INVITE_CODE_RE.test(raw) ? raw : null;
}

/** Lien public d'invitation, celui qu'ouvrent le launcher et le mod. */
export function discordInviteUrl(code: string): string {
  return `https://discord.gg/${code}`;
}

/** Endpoint public de Discord qui renvoie l'invitation avec ses compteurs. */
export function discordInviteApiUrl(code: string): string {
  return `https://discord.com/api/v10/invites/${encodeURIComponent(code)}?with_counts=true`;
}

/** Ce que le panneau garde de la réponse de Discord. */
export interface DiscordInviteCounts {
  members: number;
  online: number;
  /** Nom du serveur tel que Discord le renvoie, null s'il manque. */
  name: string | null;
}

const isCount = (v: unknown): v is number =>
  typeof v === 'number' && Number.isInteger(v) && v >= 0;

/**
 * Extrait les compteurs de `GET /invites/<code>?with_counts=true`. `null`
 * si la réponse ne les porte pas (forme inattendue) : mieux vaut « compteur
 * indisponible » qu'un « 0 membre » faux.
 */
export function parseDiscordInvite(json: unknown): DiscordInviteCounts | null {
  if (!json || typeof json !== 'object') return null;
  const o = json as Record<string, unknown>;
  const members = o.approximate_member_count;
  const online = o.approximate_presence_count;
  if (!isCount(members) || !isCount(online)) return null;
  const guild = o.guild;
  let name: string | null = null;
  if (guild && typeof guild === 'object') {
    const raw = (guild as Record<string, unknown>).name;
    if (typeof raw === 'string' && raw.trim()) name = raw.trim().slice(0, 100);
  }
  return { members, online, name };
}

/**
 * Délai avant le prochain appel à Discord après un échec.
 *  - 404 : invitation inconnue ou expirée, on réessaie toutes les 10 min ;
 *  - 429 : on respecte `Retry-After` (secondes), borné entre 1 et 15 min ;
 *  - le reste (réseau, 5xx, réponse illisible) : 1 min.
 */
export function discordRetryDelayMs(
  status: number | null,
  retryAfter: string | null = null,
): number {
  if (status === 404) return DISCORD_RETRY_UNKNOWN_INVITE_MS;
  if (status === 429) {
    const seconds = Number(retryAfter);
    if (Number.isFinite(seconds) && seconds > 0) {
      return Math.min(
        DISCORD_RETRY_MAX_MS,
        Math.max(DISCORD_RETRY_MS, Math.ceil(seconds * 1000)),
      );
    }
  }
  return DISCORD_RETRY_MS;
}
