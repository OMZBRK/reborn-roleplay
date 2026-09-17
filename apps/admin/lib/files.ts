import { api } from './api';
import type {
  DirListing,
  FileContent,
  FileScopesResponse,
} from './types';

/**
 * Wrappers typés sur `/v1/files/*` (gestionnaire de fichiers serveur staff).
 * Passent tous par le fetch authentifié `api()` (bearer + refresh auto). Le
 * serveur applique le scope par grade — l'UI ne fait que refléter ses réponses
 * (403 = accès refusé, remonté en `ApiError`).
 */

export function getScopes(): Promise<FileScopesResponse> {
  return api<FileScopesResponse>('/files/scopes');
}

export function listDir(path: string): Promise<DirListing> {
  return api<DirListing>(`/files/list?path=${encodeURIComponent(path)}`);
}

export function readFile(path: string): Promise<FileContent> {
  return api<FileContent>(`/files/read?path=${encodeURIComponent(path)}`);
}

export function writeFile(
  path: string,
  content: string,
): Promise<{ path: string; size: number }> {
  return api('/files/write', { method: 'POST', body: { path, content } });
}

export function uploadFile(
  path: string,
  contentBase64: string,
): Promise<{ path: string; size: number }> {
  return api('/files/upload', {
    method: 'POST',
    body: { path, contentBase64 },
  });
}

export function deleteFile(path: string): Promise<{ deleted: true }> {
  return api(`/files?path=${encodeURIComponent(path)}`, { method: 'DELETE' });
}

export function mkdir(
  path: string,
): Promise<{ created: boolean; path: string }> {
  return api('/files/mkdir', { method: 'POST', body: { path } });
}

export function moveFile(
  from: string,
  to: string,
): Promise<{ moved: boolean; from: string; to: string }> {
  return api('/files/move', { method: 'POST', body: { from, to } });
}

export function getReloadTargets(): Promise<{ key: string; label: string }[]> {
  return api<{ key: string; label: string }[]>('/files/reload-targets');
}

export function reload(
  target: string,
): Promise<{ queued: boolean; message: string; id?: string }> {
  return api('/files/reload', { method: 'POST', body: { target } });
}

export interface AnimatedItemResult {
  itemId: string;
  animated: boolean;
  frames: number;
  frametime: number;
  files: string[];
  reloadQueued: boolean;
  snippet: string;
}

/**
 * Générateur « item animé Nexo » : une spritesheet PNG → modèle + animation +
 * entrée Nexo écrits d'un coup côté serveur, puis `nexo reload`. Renvoie
 * `nexo:<id>` prêt pour un effet MagicSpells `itemdisplay`.
 */
export function createAnimatedItem(body: {
  id: string;
  spriteBase64: string;
  name?: string;
  frames?: number;
  frametime?: number;
  animated?: boolean;
}): Promise<AnimatedItemResult> {
  return api('/files/nexo/animated-item', { method: 'POST', body });
}
