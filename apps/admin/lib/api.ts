import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './auth';

export const API_BASE = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:3000/v1'
).replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: unknown,
    message: string,
  ) {
    super(message);
  }
}

interface FetchOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  /** Objet sérialisé en JSON, ou FormData envoyé tel quel (multipart). */
  body?: unknown;
  /** Set false to bypass the bearer header (used by login flows). */
  authenticated?: boolean;
}

/**
 * Un seul refresh en vol, partagé par toutes les requêtes qui prennent un 401
 * en même temps — évite N refresh concurrents (et donc des rotations qui se
 * révoquent mutuellement) quand plusieurs queries expirent ensemble.
 */
let refreshInFlight: Promise<boolean> | null = null;

/**
 * Tente d'échanger le refresh token contre un nouveau couple access/refresh.
 * Le backend fait tourner (rotate) le refresh token, donc on persiste bien les
 * DEUX valeurs renvoyées. Retourne false si pas de refresh token ou échec.
 */
async function tryRefresh(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;
    try {
      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
        cache: 'no-store',
      });
      if (!res.ok) return false;
      const data = (await res.json()) as {
        accessToken?: string;
        refreshToken?: string;
      };
      if (!data?.accessToken || !data?.refreshToken) return false;
      setTokens(data.accessToken, data.refreshToken);
      return true;
    } catch {
      return false;
    }
  })();
  try {
    return await refreshInFlight;
  } finally {
    refreshInFlight = null;
  }
}

/** Panne du serveur ou du proxy, sans message utile (PANEL_V2 §5.5). */
export const MESSAGE_SERVEUR_INDISPONIBLE = 'Le serveur ne répond pas. Réessaie dans un instant.';

/**
 * Message lisible d'une réponse d'erreur. Le ValidationPipe de l'API renvoie
 * un tableau de messages (un par champ) : on les joint. Jamais de chemin ni
 * de code HTTP au staff : une panne (5xx) sans message de l'API, ou avec le
 * message par défaut de Nest (« Internal server error »), devient « Le
 * serveur ne répond pas » ; le reste, « Erreur inattendue. ». Un message
 * écrit par l'API (« Serveur de fichiers injoignable… ») passe tel quel.
 */
function messageErreur(parsed: unknown, status: number): string {
  const m = (parsed as { message?: unknown } | undefined)?.message;
  if (Array.isArray(m) && m.length > 0) return m.map(String).join(' · ');
  const defautNest = typeof m === 'string' && /^internal server error$/i.test(m.trim());
  if (typeof m === 'string' && m.trim().length > 0 && !defautNest) return m;
  return status >= 500 ? MESSAGE_SERVEUR_INDISPONIBLE : 'Erreur inattendue.';
}

/**
 * Thin fetch wrapper for the staff panel. Attaches the JWT from localStorage,
 * parses JSON, throws `ApiError` on non-2xx. Sur 401 d'une requête
 * authentifiée, tente un refresh silencieux puis rejoue la requête une fois —
 * on ne déconnecte (clearTokens → redirect /login) que si le refresh échoue.
 */
export async function api<T>(path: string, opts: FetchOptions = {}): Promise<T> {
  const res = await fetchAuthentifie(path, opts);

  let parsed: unknown;
  const text = await res.text();
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  if (!res.ok) {
    if (res.status === 401) clearTokens();
    throw new ApiError(res.status, parsed, messageErreur(parsed, res.status));
  }
  return parsed as T;
}

/**
 * Fichier privé lu avec le jeton du staff (ex. la voix d'une candidature,
 * jamais servie par une URL publique). Renvoie un Blob ; l'appelant en fait
 * une URL locale (`URL.createObjectURL`) et la libère après usage.
 */
export async function apiBlob(path: string): Promise<Blob> {
  const res = await fetchAuthentifie(path, {});
  if (!res.ok) {
    if (res.status === 401) clearTokens();
    let parsed: unknown;
    const text = await res.text();
    try {
      parsed = text ? JSON.parse(text) : undefined;
    } catch {
      parsed = text;
    }
    throw new ApiError(res.status, parsed, messageErreur(parsed, res.status));
  }
  return res.blob();
}

/** Requête avec le jeton, refresh silencieux et rejeu unique sur 401. */
async function fetchAuthentifie(
  path: string,
  opts: FetchOptions,
): Promise<Response> {
  // Mode démo (développement seulement, dev/demo) : réponses simulées, aucun
  // appel réseau. La condition est écrite en entier ici, pas dans une
  // constante : `next build` la réduit à `false` et retire l'import, le mode
  // démo n'existe donc pas dans le build de production.
  if (process.env.NODE_ENV !== 'production' && process.env.NEXT_PUBLIC_PANEL_DEMO === '1') {
    const { reponseDemo } = await import('@/dev/demo');
    return reponseDemo(path, { method: opts.method ?? 'GET', body: opts.body });
  }

  const authed = opts.authenticated !== false;

  const doFetch = () => {
    const headers: Record<string, string> = {};
    // FormData (upload multipart) : le navigateur pose lui-même le
    // Content-Type avec la frontière ; on ne sérialise pas.
    const isForm = typeof FormData !== 'undefined' && opts.body instanceof FormData;
    if (opts.body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
    if (authed) {
      const token = getAccessToken();
      if (token) headers['Authorization'] = `Bearer ${token}`;
    }
    return fetch(`${API_BASE}${path}`, {
      method: opts.method ?? 'GET',
      headers,
      body:
        opts.body === undefined
          ? undefined
          : isForm
            ? (opts.body as FormData)
            : JSON.stringify(opts.body),
      cache: 'no-store',
    });
  };

  let res = await doFetch();

  // Access token expiré → refresh silencieux + retry unique. On ne tente jamais
  // de refresh sur l'endpoint /auth/refresh lui-même (garde anti-boucle).
  if (res.status === 401 && authed && path !== '/auth/refresh') {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await doFetch();
    } else {
      clearTokens();
    }
  }
  return res;
}
