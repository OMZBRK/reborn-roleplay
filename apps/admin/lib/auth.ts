const ACCESS_KEY = 'reborn-admin.access';
const REFRESH_KEY = 'reborn-admin.refresh';

/**
 * Token storage for the staff panel. We use localStorage for MVP simplicity ;
 * to move to HTTP-only cookies later we'll need same-domain hosting or
 * cross-domain cookie config on the API.
 *
 * All helpers are safe to call during SSR : they return null when window
 * is undefined.
 */

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

export function setTokens(access: string, refresh: string): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(ACCESS_KEY, access);
  window.localStorage.setItem(REFRESH_KEY, refresh);
}

export function clearTokens(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ACCESS_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
}

export function isAuthenticated(): boolean {
  return getAccessToken() !== null;
}
