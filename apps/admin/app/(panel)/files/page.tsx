'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import { StaggerItem } from '@/components/anim';
import {
  IconFile,
  IconFolder,
  IconTrash,
  IconUpload,
} from '@/components/icons';
import { SkeletonRows } from '@/components/Skeleton';
import {
  deleteFile,
  getScopes,
  listDir,
  readFile,
  uploadFile,
  writeFile,
} from '@/lib/files';
import { ApiError } from '@/lib/api';

// ── Helpers ────────────────────────────────────────────────

/** Taille lisible : B / KB / MB / GB (base 1024). */
function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = bytes / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v < 10 ? 1 : 0)} ${units[i]}`;
}

function humanDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', {
    dateStyle: 'short',
  });
}

/** Message d'erreur lisible : 403 = accès refusé, sinon message brut. */
function errMessage(err: unknown): string {
  if (err instanceof ApiError && err.status === 403) {
    return 'Accès refusé à ce chemin';
  }
  return (err as Error)?.message ?? 'Erreur inconnue';
}

/** Joint un dossier et un nom sans double slash ni slash de tête. */
function joinPath(dir: string, name: string): string {
  return [dir, name].filter(Boolean).join('/');
}

/** Découpe un chemin relatif en segments cumulés pour le fil d'Ariane. */
function crumbs(path: string): { label: string; path: string }[] {
  if (!path) return [];
  const parts = path.split('/').filter(Boolean);
  const out: { label: string; path: string }[] = [];
  let acc = '';
  for (const p of parts) {
    acc = acc ? `${acc}/${p}` : p;
    out.push({ label: p, path: acc });
  }
  return out;
}

// ── Page ───────────────────────────────────────────────────

export default function FilesPage() {
  const qc = useQueryClient();
  const [path, setPath] = useState('');
  const [selected, setSelected] = useState<string | null>(null);

  const scopesQuery = useQuery({
    queryKey: ['files', 'scopes'],
    queryFn: () => getScopes(),
  });

  const scopes = scopesQuery.data;
  const roots = scopes?.roots ?? [];
  const canWrite = scopes?.canWrite ?? false;

  // Racine active = le root le plus spécifique qui préfixe le chemin courant.
  const activeRoot = useMemo(() => {
    let best: string | null = null;
    for (const r of roots) {
      const matches = path === r.path || path.startsWith(`${r.path}/`);
      if (matches && r.path.length > (best?.length ?? -1)) best = r.path;
    }
    return best ?? roots[0]?.path ?? '';
  }, [roots, path]);

  function openRoot(rootPath: string) {
    setSelected(null);
    setPath(rootPath);
  }

  function openDir(next: string) {
    setSelected(null);
    setPath(next);
  }

  return (
    <div className="px-10 py-10 max-w-6xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Administration serveur
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Fichiers serveur
        </h1>
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        <div className="mt-3 text-sm text-[var(--color-foreground-subtle)]">
          Serveur de développement
          {scopes?.server ? (
            <span className="text-[var(--color-foreground-muted)]">
              {' '}
              · {scopes.server}
            </span>
          ) : null}
        </div>
      </header>

      {scopesQuery.isLoading && !scopes ? (
        <SkeletonRows count={4} />
      ) : scopesQuery.error ? (
        <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {errMessage(scopesQuery.error)}
        </div>
      ) : roots.length === 0 ? (
        <div className="rounded-[14px] border border-dashed border-[var(--color-border-strong)] py-16 text-center text-[var(--color-foreground-muted)]">
          Aucun accès fichier pour ton grade.
        </div>
      ) : (
        <>
          {/* Barre racines + bouton reload (stub) */}
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap gap-2">
              {roots.map((r) => {
                const active = r.path === activeRoot;
                return (
                  <button
                    key={r.path}
                    type="button"
                    onClick={() => openRoot(r.path)}
                    className={`rounded-full px-4 py-1.5 text-sm transition-colors ${
                      active
                        ? 'bg-[var(--color-accent)] text-white shadow-[var(--shadow-glow-accent)]'
                        : 'border border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]'
                    }`}
                  >
                    {r.label}
                  </button>
                );
              })}
            </div>
            <div className="flex flex-col items-end">
              <button
                type="button"
                disabled
                title="Bientôt (pont plugin)"
                className="cursor-not-allowed rounded-[10px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-muted)] opacity-60"
              >
                Recharger le plugin
              </button>
              <span className="mt-1 text-[11px] text-[var(--color-foreground-muted)]">
                Bientôt (pont plugin)
              </span>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
            <FileBrowser
              path={path}
              canWrite={canWrite}
              selected={selected}
              onOpenDir={openDir}
              onSelectFile={setSelected}
              onInvalidate={() =>
                qc.invalidateQueries({ queryKey: ['files', 'list'] })
              }
            />
            <FileViewer
              filePath={selected}
              canWrite={canWrite}
              onInvalidate={() => {
                qc.invalidateQueries({ queryKey: ['files', 'list'] });
                qc.invalidateQueries({ queryKey: ['files', 'read'] });
              }}
              onDeleted={() => setSelected(null)}
            />
          </div>
        </>
      )}
    </div>
  );
}

// ── Volet gauche : navigation ──────────────────────────────

function FileBrowser({
  path,
  canWrite,
  selected,
  onOpenDir,
  onSelectFile,
  onInvalidate,
}: {
  path: string;
  canWrite: boolean;
  selected: string | null;
  onOpenDir: (path: string) => void;
  onSelectFile: (path: string) => void;
  onInvalidate: () => void;
}) {
  const uploadRef = useRef<HTMLInputElement>(null);

  const listQuery = useQuery({
    queryKey: ['files', 'list', path],
    queryFn: () => listDir(path),
  });

  const listing = listQuery.data;

  const uploadMut = useMutation({
    mutationFn: async (file: File) => {
      const base64 = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
          const result = reader.result as string;
          const comma = result.indexOf(',');
          resolve(comma >= 0 ? result.slice(comma + 1) : result);
        };
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
      });
      return uploadFile(joinPath(path, file.name), base64);
    },
    onSuccess: (res) => {
      toast.success('Fichier importé', {
        description: res.backedUp ? 'Backup créé.' : undefined,
      });
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Import impossible', { description: errMessage(err) }),
  });

  const deleteMut = useMutation({
    mutationFn: (target: string) => deleteFile(target),
    onSuccess: () => {
      toast.success('Supprimé (backup créé)');
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Suppression impossible', { description: errMessage(err) }),
  });

  const trail = crumbs(path);
  const entries = listing?.entries ?? [];
  const dirs = entries.filter((e) => e.type === 'dir');
  const files = entries.filter((e) => e.type === 'file');
  const sorted = [...dirs, ...files];

  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      {/* Fil d'Ariane + upload */}
      <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] px-4 py-3">
        <div className="flex min-w-0 flex-wrap items-center gap-1 text-xs text-[var(--color-foreground-subtle)]">
          <button
            type="button"
            onClick={() => onOpenDir('')}
            className="rounded px-1.5 py-0.5 hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
          >
            racine
          </button>
          {trail.map((c) => (
            <span key={c.path} className="flex items-center gap-1">
              <span className="text-[var(--color-foreground-muted)]">/</span>
              <button
                type="button"
                onClick={() => onOpenDir(c.path)}
                className="truncate rounded px-1.5 py-0.5 hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
              >
                {c.label}
              </button>
            </span>
          ))}
        </div>
        {canWrite && (
          <>
            <input
              ref={uploadRef}
              type="file"
              accept="image/*,.json,.yml,.yaml"
              className="hidden"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) uploadMut.mutate(f);
                e.target.value = '';
              }}
            />
            <button
              type="button"
              disabled={uploadMut.isPending}
              onClick={() => uploadRef.current?.click()}
              className="inline-flex shrink-0 items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-xs text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)] disabled:opacity-40"
            >
              <IconUpload className="h-4 w-4" />
              {uploadMut.isPending ? 'Import…' : 'Importer'}
            </button>
          </>
        )}
      </div>

      {/* Listing */}
      <div className="p-2">
        {listQuery.isLoading && !listing ? (
          <div className="p-2">
            <SkeletonRows count={5} />
          </div>
        ) : listQuery.error ? (
          <div className="m-2 rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
            {errMessage(listQuery.error)}
          </div>
        ) : (
          <div className="space-y-1">
            {/* Remonter d'un cran */}
            {listing?.parent != null && (
              <button
                type="button"
                onClick={() => onOpenDir(listing.parent as string)}
                className="flex w-full items-center gap-3 rounded-[8px] px-3 py-2 text-left text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
              >
                <IconFolder className="h-4 w-4 text-[var(--color-foreground-muted)]" />
                ..
              </button>
            )}

            {sorted.length === 0 ? (
              <div className="px-3 py-8 text-center text-sm text-[var(--color-foreground-muted)]">
                Dossier vide.
              </div>
            ) : (
              sorted.map((entry, i) => {
                const isDir = entry.type === 'dir';
                const isSelected = !isDir && selected === entry.path;
                return (
                  <StaggerItem key={entry.path} index={i}>
                    <div
                      className={`group flex items-center gap-3 rounded-[8px] px-3 py-2 transition-colors ${
                        isSelected
                          ? 'bg-[var(--color-accent-soft)] shadow-[inset_0_0_0_1px_var(--color-accent)]'
                          : 'hover:bg-[var(--color-surface-elevated)]'
                      }`}
                    >
                      <button
                        type="button"
                        onClick={() =>
                          isDir
                            ? onOpenDir(entry.path)
                            : onSelectFile(entry.path)
                        }
                        className="flex min-w-0 flex-1 items-center gap-3 text-left"
                      >
                        {isDir ? (
                          <IconFolder className="h-4 w-4 shrink-0 text-[var(--color-accent)]" />
                        ) : (
                          <IconFile className="h-4 w-4 shrink-0 text-[var(--color-foreground-muted)]" />
                        )}
                        <span className="min-w-0 flex-1 truncate text-sm">
                          {entry.name}
                        </span>
                        {!isDir && (
                          <span className="shrink-0 text-xs text-[var(--color-foreground-muted)]">
                            {humanSize(entry.size)}
                          </span>
                        )}
                        <span className="hidden shrink-0 text-xs text-[var(--color-foreground-muted)] sm:inline">
                          {humanDate(entry.modified)}
                        </span>
                      </button>
                      {canWrite && !isDir && (
                        <button
                          type="button"
                          disabled={deleteMut.isPending}
                          onClick={() => {
                            if (
                              confirm(
                                `Supprimer définitivement « ${entry.name} » ?`,
                              )
                            )
                              deleteMut.mutate(entry.path);
                          }}
                          aria-label="Supprimer"
                          className="shrink-0 rounded p-1 text-[var(--color-foreground-muted)] opacity-0 transition-opacity hover:text-[var(--color-danger)] group-hover:opacity-100 disabled:opacity-40"
                        >
                          <IconTrash className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  </StaggerItem>
                );
              })
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Volet droit : lecture / édition ────────────────────────

function FileViewer({
  filePath,
  canWrite,
  onInvalidate,
  onDeleted,
}: {
  filePath: string | null;
  canWrite: boolean;
  onInvalidate: () => void;
  onDeleted: () => void;
}) {
  if (!filePath) {
    return (
      <div className="flex min-h-[280px] items-center justify-center rounded-[14px] border border-dashed border-[var(--color-border-strong)] text-center text-sm text-[var(--color-foreground-muted)]">
        Sélectionne un fichier pour l’afficher.
      </div>
    );
  }
  return (
    <FileViewerInner
      key={filePath}
      filePath={filePath}
      canWrite={canWrite}
      onInvalidate={onInvalidate}
      onDeleted={onDeleted}
    />
  );
}

function FileViewerInner({
  filePath,
  canWrite,
  onInvalidate,
  onDeleted,
}: {
  filePath: string;
  canWrite: boolean;
  onInvalidate: () => void;
  onDeleted: () => void;
}) {
  const [draft, setDraft] = useState<string | null>(null);

  const fileQuery = useQuery({
    queryKey: ['files', 'read', filePath],
    queryFn: () => readFile(filePath),
  });

  const file = fileQuery.data;
  // Contenu effectif de l'éditeur : brouillon local sinon valeur serveur.
  const value = draft ?? file?.content ?? '';
  const dirty = draft != null && draft !== file?.content;

  const saveMut = useMutation({
    mutationFn: () => writeFile(filePath, value),
    onSuccess: () => {
      toast.success('Enregistré (backup créé)');
      setDraft(null);
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Échec de l’enregistrement', { description: errMessage(err) }),
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteFile(filePath),
    onSuccess: () => {
      toast.success('Supprimé (backup créé)');
      onDeleted();
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Suppression impossible', { description: errMessage(err) }),
  });

  return (
    <div className="flex flex-col rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      {/* En-tête : chemin + taille + actions */}
      <div className="flex items-start justify-between gap-3 border-b border-[var(--color-border)] px-4 py-3">
        <div className="min-w-0">
          <div className="truncate font-mono text-sm text-[var(--color-foreground)]">
            {filePath}
          </div>
          {file && (
            <div className="mt-0.5 text-xs text-[var(--color-foreground-muted)]">
              {humanSize(file.size)} · {file.kind}
            </div>
          )}
        </div>
        {canWrite && (
          <button
            type="button"
            disabled={deleteMut.isPending}
            onClick={() => {
              if (confirm('Supprimer définitivement ce fichier ?'))
                deleteMut.mutate();
            }}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-[8px] border border-[var(--color-danger)]/40 px-3 py-1.5 text-xs text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)] disabled:opacity-40"
          >
            <IconTrash className="h-4 w-4" />
            Supprimer
          </button>
        )}
      </div>

      <div className="p-4">
        {fileQuery.isLoading && !file ? (
          <SkeletonRows count={4} />
        ) : fileQuery.error ? (
          <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
            {errMessage(fileQuery.error)}
          </div>
        ) : !file ? null : file.kind === 'text' ? (
          <div className="space-y-3">
            <textarea
              value={value}
              onChange={(e) => setDraft(e.target.value)}
              rows={20}
              spellCheck={false}
              className="w-full resize-y rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-3 font-mono text-sm leading-relaxed focus:border-[var(--color-accent)] focus:outline-none"
            />
            {canWrite && (
              <div className="flex items-center justify-end gap-2">
                {dirty && (
                  <button
                    type="button"
                    onClick={() => setDraft(null)}
                    className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
                  >
                    Réinitialiser
                  </button>
                )}
                <button
                  type="button"
                  disabled={saveMut.isPending || !file.editable || !dirty}
                  onClick={() => saveMut.mutate()}
                  className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2 text-sm font-medium text-white disabled:opacity-40"
                >
                  {saveMut.isPending ? 'Enregistrement…' : 'Enregistrer'}
                </button>
              </div>
            )}
          </div>
        ) : file.kind === 'image' ? (
          <div className="space-y-3">
            <div className="flex justify-center rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-4">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={`data:image/png;base64,${file.content}`}
                alt={filePath}
                className="max-h-[420px] max-w-full rounded"
                style={{ imageRendering: 'pixelated' }}
              />
            </div>
            <div className="text-xs text-[var(--color-foreground-muted)]">
              Aperçu — remplace via upload.
            </div>
          </div>
        ) : (
          <div className="rounded-[10px] border border-dashed border-[var(--color-border-strong)] py-12 text-center text-sm text-[var(--color-foreground-muted)]">
            Fichier binaire non éditable.
          </div>
        )}
      </div>
    </div>
  );
}
