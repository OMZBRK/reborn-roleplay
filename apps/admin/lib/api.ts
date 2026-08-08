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

/**
 * Thin fetch wrapper for the staff panel. Attaches the JWT from localStorage,
 * parses JSON, throws `ApiError` on non-2xx. Sur 401 d'une requête
 * authentifiée, tente un refresh silencieux puis rejoue la requête une fois —
 * on ne déconnecte (clearTokens → redirect /login) que si le refresh échoue.
 */
export async function api<T>(path: string, opts: FetchOptions = {}): Promise<T> {
  const authed = opts.authenticated !== false;

  const doFetch = () => {
    const headers: Record<string, string> = {};
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    if (authed) {
      const token = getAccessToken();
      if (token) headers['Authorization'] = `Bearer ${token}`;
    }
    return fetch(`${API_BASE}${path}`, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
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
    const message =
      (parsed as { message?: string })?.message ??
      `${opts.method ?? 'GET'} ${path} → HTTP ${res.status}`;
    throw new ApiError(res.status, parsed, message);
  }
  return parsed as T;
}
