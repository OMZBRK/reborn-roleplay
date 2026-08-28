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
): Promise<{ path: string; size: number; backedUp: boolean }> {
  return api('/files/write', { method: 'POST', body: { path, content } });
}

export function uploadFile(
  path: string,
  contentBase64: string,
): Promise<{ path: string; size: number; backedUp: boolean }> {
  return api('/files/upload', {
    method: 'POST',
    body: { path, contentBase64 },
  });
}

export function deleteFile(
  path: string,
): Promise<{ deleted: true; backedUp: boolean }> {
  return api(`/files?path=${encodeURIComponent(path)}`, { method: 'DELETE' });
}

export function reload(
  target: string,
): Promise<{ queued: boolean; message: string }> {
  return api('/files/reload', { method: 'POST', body: { target } });
}
