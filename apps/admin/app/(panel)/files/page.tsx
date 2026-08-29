'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import {
  IconChevronRight,
  IconCopy,
  IconDots,
  IconFile,
  IconFilePlus,
  IconFolder,
  IconFolderPlus,
  IconMove,
  IconOpen,
  IconPencil,
  IconRefresh,
  IconSearch,
  IconStar,
  IconTrash,
  IconUpload,
  IconX,
} from '@/components/icons';
import { SkeletonRows } from '@/components/Skeleton';
import {
  createAnimatedItem,
  deleteFile,
  getReloadTargets,
  getScopes,
  listDir,
  mkdir,
  moveFile,
  readFile,
  reload,
  uploadFile,
  writeFile,
} from '@/lib/files';
import type { AnimatedItemResult } from '@/lib/files';
import type { FileEntry } from '@/lib/types';
import { ApiError } from '@/lib/api';

// ── Helpers ────────────────────────────────────────────────

/** Taille lisible : o / Ko / Mo / Go (base 1024, libellés FR façon Mestrator). */
function humanSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} o`;
  const units = ['Ko', 'Mo', 'Go', 'To'];
  let v = bytes / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v < 10 ? 2 : 0)} ${units[i]}`;
}

/** Date + heure compacte : « 28/08/26 23:46 ». */
function humanDate(iso: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return `${d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit' })} ${d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
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

function basename(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1);
}

function parentDir(path: string): string {
  const i = path.lastIndexOf('/');
  return i === -1 ? '' : path.slice(0, i);
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

// ── Accès rapide (épinglés, persistés localStorage) ────────

interface Pin {
  path: string;
  name: string;
  type: 'dir' | 'file';
}
const PINS_KEY = 'reborn.files.pins.v1';

function usePins() {
  const [pins, setPins] = useState<Pin[]>([]);
  useEffect(() => {
    try {
      const raw = localStorage.getItem(PINS_KEY);
      if (raw) setPins(JSON.parse(raw));
    } catch {
      /* ignore */
    }
  }, []);
  const persist = useCallback((next: Pin[]) => {
    setPins(next);
    try {
      localStorage.setItem(PINS_KEY, JSON.stringify(next));
    } catch {
      /* ignore */
    }
  }, []);
  const toggle = useCallback(
    (p: Pin) => {
      persist(
        pins.some((x) => x.path === p.path)
          ? pins.filter((x) => x.path !== p.path)
          : [...pins, p],
      );
    },
    [pins, persist],
  );
  const isPinned = useCallback(
    (path: string) => pins.some((x) => x.path === path),
    [pins],
  );
  return { pins, toggle, isPinned };
}

// ── Page ───────────────────────────────────────────────────

export default function FilesPage() {
  const qc = useQueryClient();
  const [path, setPath] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const pins = usePins();

  const scopesQuery = useQuery({
    queryKey: ['files', 'scopes'],
    queryFn: () => getScopes(),
  });

  const scopes = scopesQuery.data;
  const roots = scopes?.roots ?? [];
  const canWrite = scopes?.canWrite ?? false;
  // Accès au pack Nexo (pour le générateur d'item animé) ?
  const canNexo = roots.some(
    (r) =>
      r.path === '' ||
      r.path === 'plugins/Nexo' ||
      'plugins/Nexo'.startsWith(`${r.path}/`),
  );

  const reloadTargetsQuery = useQuery({
    queryKey: ['files', 'reload-targets'],
    queryFn: () => getReloadTargets(),
  });
  const reloadTargets = reloadTargetsQuery.data ?? [];

  const reloadMutation = useMutation({
    mutationFn: (target: string) => reload(target),
    onSuccess: (res) => toast.success(res.message ?? 'Rechargement envoyé.'),
    onError: (err) => toast.error(errMessage(err)),
  });

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

  function openPin(p: Pin) {
    if (p.type === 'dir') openDir(p.path);
    else {
      setPath(parentDir(p.path));
      setSelected(p.path);
    }
  }

  return (
    <div className="px-8 py-8 max-w-[1400px] mx-auto">
      <header className="mb-6">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Administration serveur
        </div>
        <h1
          className="mt-1 text-4xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Fichiers serveur
        </h1>
        <div className="mt-3 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
        <div className="mt-3 text-sm text-[var(--color-foreground-subtle)]">
          Parcourez, éditez et gérez les fichiers de votre serveur
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
          {/* Racines (périmètres) + reload plugin */}
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
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
            {reloadTargets.length > 0 ? (
              <div className="flex flex-wrap items-center gap-2">
                {reloadTargets.map((t) => (
                  <button
                    key={t.key}
                    type="button"
                    disabled={reloadMutation.isPending}
                    onClick={() => reloadMutation.mutate(t.key)}
                    title={`Recharge ${t.label} sur le serveur`}
                    className="inline-flex items-center gap-1.5 rounded-[10px] border border-[var(--color-border-strong)] px-3.5 py-2 text-sm text-[var(--color-foreground-subtle)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)] disabled:opacity-50"
                  >
                    <IconRefresh className="h-4 w-4" />
                    {t.label}
                  </button>
                ))}
              </div>
            ) : null}
          </div>

          {/* Accès rapide */}
          {pins.pins.length > 0 && (
            <div className="mb-4">
              <div className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-[var(--color-foreground-muted)]">
                <IconStar className="h-3.5 w-3.5 text-[var(--color-accent)]" />
                Accès rapide
              </div>
              <div className="flex flex-wrap gap-2">
                {pins.pins.map((p) => (
                  <div
                    key={p.path}
                    className="group inline-flex items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-1 pl-2.5 pr-1.5 text-xs text-[var(--color-foreground-subtle)]"
                  >
                    <button
                      type="button"
                      onClick={() => openPin(p)}
                      className="inline-flex items-center gap-1.5 hover:text-[var(--color-foreground)]"
                    >
                      {p.type === 'dir' ? (
                        <IconFolder className="h-3.5 w-3.5 text-[var(--color-accent)]" />
                      ) : (
                        <IconFile className="h-3.5 w-3.5 text-[var(--color-foreground-muted)]" />
                      )}
                      {p.name}
                    </button>
                    <button
                      type="button"
                      onClick={() => pins.toggle(p)}
                      aria-label="Détacher"
                      className="rounded p-0.5 text-[var(--color-foreground-muted)] hover:text-[var(--color-danger)]"
                    >
                      <IconX className="h-3 w-3" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          <FileManager
            path={path}
            canWrite={canWrite}
            canNexo={canNexo}
            selected={selected}
            pins={pins}
            onOpenDir={openDir}
            onSelectFile={setSelected}
            onCloseFile={() => setSelected(null)}
            onInvalidate={() => {
              qc.invalidateQueries({ queryKey: ['files', 'list'] });
              qc.invalidateQueries({ queryKey: ['files', 'read'] });
            }}
          />
        </>
      )}
    </div>
  );
}

// ── Gestionnaire (toolbar + table + éditeur) ──────────────

type SortKey = 'name' | 'size' | 'modified';

function FileManager({
  path,
  canWrite,
  canNexo,
  selected,
  pins,
  onOpenDir,
  onSelectFile,
  onCloseFile,
  onInvalidate,
}: {
  path: string;
  canWrite: boolean;
  canNexo: boolean;
  selected: string | null;
  pins: ReturnType<typeof usePins>;
  onOpenDir: (path: string) => void;
  onSelectFile: (path: string) => void;
  onCloseFile: () => void;
  onInvalidate: () => void;
}) {
  const qc = useQueryClient();
  const uploadRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [sortAsc, setSortAsc] = useState(true);
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [menu, setMenu] = useState<{ entry: FileEntry; x: number; y: number } | null>(null);
  const [showAnim, setShowAnim] = useState(false);

  const listQuery = useQuery({
    queryKey: ['files', 'list', path],
    queryFn: () => listDir(path),
  });
  const listing = listQuery.data;

  // Vide la sélection quand on change de dossier.
  useEffect(() => {
    setChecked(new Set());
    setQuery('');
  }, [path]);

  // ── Mutations ──
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
    onSuccess: () => {
      toast.success('Fichier envoyé');
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Envoi impossible', { description: errMessage(err) }),
  });

  const mkdirMut = useMutation({
    mutationFn: (full: string) => mkdir(full),
    onSuccess: () => {
      toast.success('Dossier créé');
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Création impossible', { description: errMessage(err) }),
  });

  const newFileMut = useMutation({
    mutationFn: (full: string) => writeFile(full, ''),
    onSuccess: (res) => {
      toast.success('Fichier créé');
      onInvalidate();
      onSelectFile(res.path);
    },
    onError: (err) =>
      toast.error('Création impossible', { description: errMessage(err) }),
  });

  const moveMut = useMutation({
    mutationFn: ({ from, to }: { from: string; to: string }) => moveFile(from, to),
    onSuccess: (res) => {
      toast.success('Déplacé', { description: res.to });
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Déplacement impossible', { description: errMessage(err) }),
  });

  const deleteMut = useMutation({
    mutationFn: (target: string) => deleteFile(target),
    onSuccess: () => onInvalidate(),
    onError: (err) =>
      toast.error('Suppression impossible', { description: errMessage(err) }),
  });

  // ── Actions ──
  function doNewFolder() {
    const name = prompt('Nom du nouveau dossier :')?.trim();
    if (name) mkdirMut.mutate(joinPath(path, name));
  }
  function doNewFile() {
    const name = prompt('Nom du nouveau fichier (ex: fight1.png.mcmeta) :')?.trim();
    if (name) newFileMut.mutate(joinPath(path, name));
  }
  function doRefresh() {
    qc.invalidateQueries({ queryKey: ['files', 'list', path] });
    qc.invalidateQueries({ queryKey: ['files', 'read'] });
    toast.success('Actualisé');
  }
  function doRename(entry: FileEntry) {
    const next = prompt('Nouveau nom :', entry.name)?.trim();
    if (!next || next === entry.name) return;
    moveMut.mutate({ from: entry.path, to: joinPath(parentDir(entry.path), next) });
  }
  function doMoveOne(entry: FileEntry) {
    const dest = prompt(
      'Déplacer vers le dossier (chemin relatif au périmètre) :',
      parentDir(entry.path),
    )?.trim();
    if (dest == null) return;
    moveMut.mutate({ from: entry.path, to: joinPath(dest, entry.name) });
  }
  function doDeleteOne(entry: FileEntry) {
    if (entry.type === 'dir') {
      toast.error('Suppression de dossier non autorisée');
      return;
    }
    if (confirm(`Supprimer définitivement « ${entry.name} » ?`)) {
      deleteMut.mutate(entry.path, {
        onSuccess: () => {
          toast.success('Supprimé');
          if (selected === entry.path) onCloseFile();
        },
      });
    }
  }
  function copyName(entry: FileEntry) {
    navigator.clipboard
      ?.writeText(entry.name)
      .then(() => toast.success('Nom copié'))
      .catch(() => toast.error('Copie impossible'));
  }

  // ── Sélection multiple ──
  function toggleCheck(pathKey: string) {
    setChecked((prev) => {
      const next = new Set(prev);
      if (next.has(pathKey)) next.delete(pathKey);
      else next.add(pathKey);
      return next;
    });
  }
  const entries = listing?.entries ?? [];
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const base = q
      ? entries.filter((e) => e.name.toLowerCase().includes(q))
      : entries;
    const dir = sortAsc ? 1 : -1;
    return [...base].sort((a, b) => {
      // Dossiers toujours en premier.
      if (a.type !== b.type) return a.type === 'dir' ? -1 : 1;
      if (sortKey === 'name') return a.name.localeCompare(b.name) * dir;
      if (sortKey === 'size') return (a.size - b.size) * dir;
      return (
        (new Date(a.modified).getTime() - new Date(b.modified).getTime()) * dir
      );
    });
  }, [entries, query, sortKey, sortAsc]);

  const allChecked = filtered.length > 0 && filtered.every((e) => checked.has(e.path));
  function toggleAll() {
    if (allChecked) setChecked(new Set());
    else setChecked(new Set(filtered.map((e) => e.path)));
  }
  const selectedEntries = filtered.filter((e) => checked.has(e.path));

  function bulkDelete() {
    const files = selectedEntries.filter((e) => e.type === 'file');
    const dirs = selectedEntries.length - files.length;
    if (files.length === 0) {
      toast.error('Seuls les fichiers peuvent être supprimés');
      return;
    }
    if (
      !confirm(
        `Supprimer définitivement ${files.length} fichier(s) ?` +
          (dirs > 0 ? `\n(${dirs} dossier(s) ignoré(s))` : ''),
      )
    )
      return;
    Promise.allSettled(files.map((e) => deleteFile(e.path))).then(() => {
      toast.success(`${files.length} fichier(s) supprimé(s)`);
      setChecked(new Set());
      if (selected && files.some((e) => e.path === selected)) onCloseFile();
      onInvalidate();
    });
  }
  function bulkMove() {
    const dest = prompt(
      'Déplacer la sélection vers (chemin relatif au périmètre) :',
      path,
    )?.trim();
    if (dest == null) return;
    Promise.allSettled(
      selectedEntries.map((e) => moveFile(e.path, joinPath(dest, e.name))),
    ).then(() => {
      toast.success(`${selectedEntries.length} élément(s) déplacé(s)`);
      setChecked(new Set());
      onInvalidate();
    });
  }

  // Fil d'Ariane (inclut le fichier ouvert le cas échéant).
  const trail = crumbs(selected ?? path);
  const openHeaderName = selected ? basename(selected) : null;

  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      {/* Toolbar : fil d'Ariane + recherche + actions */}
      <div className="flex flex-col gap-3 border-b border-[var(--color-border)] px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
        {/* Fil d'Ariane */}
        <div className="flex min-w-0 flex-wrap items-center gap-1 text-sm">
          <button
            type="button"
            onClick={() => onOpenDir('')}
            className="inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
          >
            <IconFolder className="h-4 w-4" />
            Racine
          </button>
          {trail.map((c, i) => {
            const isLast = i === trail.length - 1;
            const isOpenFile = isLast && openHeaderName != null;
            return (
              <span key={c.path} className="flex min-w-0 items-center">
                <IconChevronRight className="h-3.5 w-3.5 shrink-0 text-[var(--color-foreground-muted)]" />
                <button
                  type="button"
                  onClick={() => (isOpenFile ? onCloseFile() : onOpenDir(c.path))}
                  className={`truncate rounded px-1.5 py-0.5 hover:bg-[var(--color-surface-elevated)] ${
                    isLast
                      ? 'text-[var(--color-foreground)]'
                      : 'text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]'
                  }`}
                >
                  {c.label}
                </button>
              </span>
            );
          })}
        </div>

        {/* Recherche + actions */}
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          <div className="relative">
            <IconSearch className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-foreground-muted)]" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Rechercher un fichier…"
              className="w-52 rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] py-1.5 pl-8 pr-3 text-sm text-[var(--color-foreground)] placeholder:text-[var(--color-foreground-muted)] focus:border-[var(--color-accent)] focus:outline-none"
            />
          </div>
          {canWrite && (
            <>
              <input
                ref={uploadRef}
                type="file"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) uploadMut.mutate(f);
                  e.target.value = '';
                }}
              />
              <ToolbarButton onClick={doNewFile} disabled={newFileMut.isPending}>
                <IconFilePlus className="h-4 w-4" />
                Nouveau fichier
              </ToolbarButton>
              <ToolbarButton onClick={doNewFolder} disabled={mkdirMut.isPending}>
                <IconFolderPlus className="h-4 w-4" />
                Nouveau dossier
              </ToolbarButton>
              <ToolbarButton
                onClick={() => uploadRef.current?.click()}
                disabled={uploadMut.isPending}
              >
                <IconUpload className="h-4 w-4" />
                {uploadMut.isPending ? 'Envoi…' : 'Envoyer'}
              </ToolbarButton>
              {canNexo && (
                <button
                  type="button"
                  onClick={() => setShowAnim(true)}
                  title="Créer un item animé Nexo à partir d'une spritesheet"
                  className="inline-flex shrink-0 items-center gap-1.5 rounded-[8px] border border-[var(--color-accent)]/50 bg-[var(--color-accent-soft)] px-3 py-1.5 text-sm text-[var(--color-accent)] hover:bg-[var(--color-accent)] hover:text-white"
                >
                  <IconStar className="h-4 w-4" />
                  Item animé
                </button>
              )}
            </>
          )}
          <button
            type="button"
            onClick={doRefresh}
            title="Actualiser"
            aria-label="Actualiser"
            className="inline-flex h-9 w-9 items-center justify-center rounded-[8px] border border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
          >
            <IconRefresh className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Corps : éditeur si un fichier est ouvert, sinon la table */}
      {selected ? (
        <FileEditor
          filePath={selected}
          canWrite={canWrite}
          onInvalidate={onInvalidate}
          onClose={onCloseFile}
          onDeleted={onCloseFile}
        />
      ) : listQuery.isLoading && !listing ? (
        <div className="p-4">
          <SkeletonRows count={6} />
        </div>
      ) : listQuery.error ? (
        <div className="m-4 rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {errMessage(listQuery.error)}
        </div>
      ) : (
        <div>
          {/* En-tête de colonnes */}
          <div className="flex items-center gap-3 border-b border-[var(--color-border)] px-4 py-2 text-xs font-medium uppercase tracking-wide text-[var(--color-foreground-muted)]">
            <label className="flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center">
              <input
                type="checkbox"
                checked={allChecked}
                onChange={toggleAll}
                className="h-4 w-4 accent-[var(--color-accent)]"
              />
            </label>
            <SortHeader
              label="Nom"
              active={sortKey === 'name'}
              asc={sortAsc}
              onClick={() => {
                if (sortKey === 'name') setSortAsc((v) => !v);
                else {
                  setSortKey('name');
                  setSortAsc(true);
                }
              }}
              className="flex-1"
            />
            <SortHeader
              label="Taille"
              active={sortKey === 'size'}
              asc={sortAsc}
              onClick={() => {
                if (sortKey === 'size') setSortAsc((v) => !v);
                else {
                  setSortKey('size');
                  setSortAsc(false);
                }
              }}
              className="hidden w-24 justify-end text-right sm:flex"
            />
            <SortHeader
              label="Date"
              active={sortKey === 'modified'}
              asc={sortAsc}
              onClick={() => {
                if (sortKey === 'modified') setSortAsc((v) => !v);
                else {
                  setSortKey('modified');
                  setSortAsc(false);
                }
              }}
              className="hidden w-36 justify-end text-right md:flex"
            />
            <div className="w-8 shrink-0" />
          </div>

          {/* Lignes */}
          {listing?.parent != null && (
            <button
              type="button"
              onClick={() => onOpenDir(listing.parent as string)}
              className="flex w-full items-center gap-3 border-b border-[var(--color-border)]/50 px-4 py-2.5 text-left text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
            >
              <span className="h-5 w-5 shrink-0" />
              <IconFolder className="h-4 w-4 shrink-0 text-[var(--color-foreground-muted)]" />
              ..
            </button>
          )}

          {filtered.length === 0 ? (
            <div className="px-4 py-12 text-center text-sm text-[var(--color-foreground-muted)]">
              {query ? 'Aucun fichier ne correspond.' : 'Dossier vide.'}
            </div>
          ) : (
            <div>
              {filtered.map((entry) => {
                const isDir = entry.type === 'dir';
                const isChecked = checked.has(entry.path);
                return (
                  <div
                    key={entry.path}
                    className={`group flex items-center gap-3 border-b border-[var(--color-border)]/40 px-4 py-2.5 transition-colors last:border-b-0 ${
                      isChecked
                        ? 'bg-[var(--color-accent-soft)]'
                        : 'hover:bg-[var(--color-surface-elevated)]'
                    }`}
                  >
                    <label className="flex h-5 w-5 shrink-0 cursor-pointer items-center justify-center">
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => toggleCheck(entry.path)}
                        className="h-4 w-4 accent-[var(--color-accent)]"
                      />
                    </label>
                    <button
                      type="button"
                      onClick={() =>
                        isDir ? onOpenDir(entry.path) : onSelectFile(entry.path)
                      }
                      className="flex min-w-0 flex-1 items-center gap-3 text-left"
                    >
                      {isDir ? (
                        <IconFolder className="h-4 w-4 shrink-0 text-[var(--color-accent)]" />
                      ) : (
                        <IconFile className="h-4 w-4 shrink-0 text-[var(--color-foreground-muted)]" />
                      )}
                      <span className="min-w-0 flex-1 truncate text-sm text-[var(--color-foreground)]">
                        {entry.name}
                      </span>
                    </button>
                    <span className="hidden w-24 shrink-0 text-right text-xs text-[var(--color-foreground-muted)] sm:block">
                      {isDir ? '—' : humanSize(entry.size)}
                    </span>
                    <span className="hidden w-36 shrink-0 text-right text-xs text-[var(--color-foreground-muted)] md:block">
                      {humanDate(entry.modified)}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => {
                        const r = (
                          e.currentTarget as HTMLElement
                        ).getBoundingClientRect();
                        setMenu({ entry, x: r.right, y: r.bottom });
                      }}
                      aria-label="Actions"
                      className="flex h-8 w-8 shrink-0 items-center justify-center rounded text-[var(--color-foreground-muted)] hover:bg-[var(--color-surface)] hover:text-[var(--color-foreground)]"
                    >
                      <IconDots className="h-4 w-4" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* Menu contextuel (3 points) */}
      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          entry={menu.entry}
          canWrite={canWrite}
          pinned={pins.isPinned(menu.entry.path)}
          onClose={() => setMenu(null)}
          onOpen={() =>
            menu.entry.type === 'dir'
              ? onOpenDir(menu.entry.path)
              : onSelectFile(menu.entry.path)
          }
          onPin={() =>
            pins.toggle({
              path: menu.entry.path,
              name: menu.entry.name,
              type: menu.entry.type,
            })
          }
          onCopyName={() => copyName(menu.entry)}
          onRename={() => doRename(menu.entry)}
          onMove={() => doMoveOne(menu.entry)}
          onDelete={() => doDeleteOne(menu.entry)}
        />
      )}

      {/* Générateur d'item animé Nexo */}
      {showAnim && (
        <AnimatedItemModal
          onClose={() => setShowAnim(false)}
          onCreated={() => onInvalidate()}
        />
      )}

      {/* Barre d'actions sur la sélection */}
      {checked.size > 0 && !selected && (
        <div className="pointer-events-none fixed inset-x-0 bottom-6 z-40 flex justify-center px-4">
          <div className="pointer-events-auto flex items-center gap-2 rounded-[12px] border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] px-3 py-2 shadow-[0_8px_30px_rgba(0,0,0,0.5)]">
            <span className="px-2 text-sm text-[var(--color-foreground)]">
              {checked.size} élément{checked.size > 1 ? 's' : ''} sélectionné
              {checked.size > 1 ? 's' : ''}
            </span>
            {canWrite && (
              <>
                <button
                  type="button"
                  onClick={bulkMove}
                  className="inline-flex items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface)] hover:text-[var(--color-foreground)]"
                >
                  <IconMove className="h-4 w-4" />
                  Déplacer
                </button>
                <button
                  type="button"
                  onClick={bulkDelete}
                  className="inline-flex items-center gap-1.5 rounded-[8px] border border-[var(--color-danger)]/40 px-3 py-1.5 text-sm text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]"
                >
                  <IconTrash className="h-4 w-4" />
                  Supprimer
                </button>
              </>
            )}
            <button
              type="button"
              onClick={() => setChecked(new Set())}
              aria-label="Annuler la sélection"
              className="rounded p-1.5 text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
            >
              <IconX className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Sous-composants ────────────────────────────────────────

function ToolbarButton({
  onClick,
  disabled,
  children,
}: {
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="inline-flex shrink-0 items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)] disabled:opacity-40"
    >
      {children}
    </button>
  );
}

function SortHeader({
  label,
  active,
  asc,
  onClick,
  className = '',
}: {
  label: string;
  active: boolean;
  asc: boolean;
  onClick: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-1 hover:text-[var(--color-foreground)] ${
        active ? 'text-[var(--color-foreground)]' : ''
      } ${className}`}
    >
      {label}
      <span className="text-[10px]">{active ? (asc ? '↑' : '↓') : '↕'}</span>
    </button>
  );
}

function ContextMenu({
  x,
  y,
  entry,
  canWrite,
  pinned,
  onClose,
  onOpen,
  onPin,
  onCopyName,
  onRename,
  onMove,
  onDelete,
}: {
  x: number;
  y: number;
  entry: FileEntry;
  canWrite: boolean;
  pinned: boolean;
  onClose: () => void;
  onOpen: () => void;
  onPin: () => void;
  onCopyName: () => void;
  onRename: () => void;
  onMove: () => void;
  onDelete: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    function onDoc(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  // Positionne le menu à gauche du bouton, en évitant de sortir de l'écran.
  const left = Math.max(8, x - 208);
  const top = Math.min(y + 4, (typeof window !== 'undefined' ? window.innerHeight : 800) - 300);

  function run(fn: () => void) {
    onClose();
    fn();
  }

  return (
    <div
      ref={ref}
      style={{ position: 'fixed', left, top, width: 200, zIndex: 50 }}
      className="overflow-hidden rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] py-1 shadow-[0_8px_30px_rgba(0,0,0,0.5)]"
    >
      <MenuItem onClick={() => run(onOpen)} icon={<IconOpen className="h-4 w-4" />}>
        Ouvrir
      </MenuItem>
      <MenuItem
        onClick={() => run(onPin)}
        icon={<IconStar className="h-4 w-4" />}
      >
        {pinned ? "Retirer de l'accès rapide" : "Épingler dans l'accès rapide"}
      </MenuItem>
      <MenuItem
        onClick={() => run(onCopyName)}
        icon={<IconCopy className="h-4 w-4" />}
      >
        Copier le nom
      </MenuItem>
      {canWrite && (
        <>
          <div className="my-1 h-px bg-[var(--color-border)]" />
          <MenuItem
            onClick={() => run(onRename)}
            icon={<IconPencil className="h-4 w-4" />}
          >
            Renommer
          </MenuItem>
          <MenuItem
            onClick={() => run(onMove)}
            icon={<IconMove className="h-4 w-4" />}
          >
            Déplacer
          </MenuItem>
          {entry.type === 'file' && (
            <MenuItem
              onClick={() => run(onDelete)}
              icon={<IconTrash className="h-4 w-4" />}
              danger
            >
              Supprimer
            </MenuItem>
          )}
        </>
      )}
    </div>
  );
}

function MenuItem({
  onClick,
  icon,
  danger,
  children,
}: {
  onClick: () => void;
  icon: React.ReactNode;
  danger?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm transition-colors ${
        danger
          ? 'text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]'
          : 'text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface)] hover:text-[var(--color-foreground)]'
      }`}
    >
      <span className="shrink-0">{icon}</span>
      {children}
    </button>
  );
}

// ── Générateur d'item animé Nexo ───────────────────────────

function AnimatedItemModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: () => void;
}) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const [base64, setBase64] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [dims, setDims] = useState<{ w: number; h: number } | null>(null);
  const [animated, setAnimated] = useState(true);
  const [frames, setFrames] = useState(1);
  const [frametime, setFrametime] = useState(2);
  const [result, setResult] = useState<AnimatedItemResult | null>(null);

  const createMut = useMutation({
    mutationFn: () =>
      createAnimatedItem({
        id: id.trim(),
        spriteBase64: base64 ?? '',
        name: name.trim() || undefined,
        frames,
        frametime,
        animated,
      }),
    onSuccess: (res) => {
      setResult(res);
      toast.success(`Item créé : ${res.itemId}`, {
        description: res.reloadQueued
          ? 'Nexo rechargé — reconnecte-toi pour voir le pack.'
          : 'Écrit dans le pack (reload Nexo à faire).',
      });
      onCreated();
    },
    onError: (err) =>
      toast.error('Création impossible', { description: errMessage(err) }),
  });

  function onPick(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result as string;
      const comma = dataUrl.indexOf(',');
      setBase64(comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl);
      setPreview(dataUrl);
      // Auto-détecte les dimensions → nombre de frames (feuille verticale).
      const img = new Image();
      img.onload = () => {
        const w = img.naturalWidth;
        const h = img.naturalHeight;
        setDims({ w, h });
        if (w > 0 && h % w === 0 && h / w > 1) {
          setFrames(h / w);
          setAnimated(true);
        } else {
          setFrames(1);
          setAnimated(false);
        }
      };
      img.src = dataUrl;
      // Pré-remplit l'id depuis le nom de fichier si vide.
      if (!id) {
        const stem = file.name
          .replace(/\.[^.]+$/, '')
          .toLowerCase()
          .replace(/[^a-z0-9_]+/g, '_')
          .replace(/^_+|_+$/g, '');
        if (stem) setId(stem);
      }
    };
    reader.readAsDataURL(file);
  }

  const idValid = /^[a-z0-9_]+$/.test(id.trim());
  const canSubmit = idValid && !!base64 && !createMut.isPending;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="absolute inset-0 bg-black/60"
        onClick={() => (createMut.isPending ? null : onClose())}
      />
      <div className="relative z-10 w-full max-w-lg overflow-hidden rounded-[16px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
        <div className="flex items-center justify-between border-b border-[var(--color-border)] px-5 py-4">
          <div>
            <div className="flex items-center gap-2 text-base font-medium text-[var(--color-foreground)]">
              <IconStar className="h-4 w-4 text-[var(--color-accent)]" />
              Nouvel item animé
            </div>
            <div className="mt-0.5 text-xs text-[var(--color-foreground-muted)]">
              Une spritesheet PNG → modèle + animation + entrée Nexo, prêt pour
              MagicSpells.
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded p-1.5 text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
            aria-label="Fermer"
          >
            <IconX className="h-4 w-4" />
          </button>
        </div>

        {result ? (
          <div className="space-y-4 px-5 py-5">
            <div className="rounded-[10px] border border-[var(--color-accent)]/40 bg-[var(--color-accent-soft)] px-4 py-3 text-sm text-[var(--color-foreground)]">
              <div className="font-mono text-[var(--color-accent)]">
                {result.itemId}
              </div>
              <div className="mt-1 text-xs text-[var(--color-foreground-muted)]">
                {result.animated
                  ? `Animé · ${result.frames} frames · frametime ${result.frametime}`
                  : 'Statique (non animé)'}
                {result.reloadQueued
                  ? ' · Nexo rechargé'
                  : ' · reload Nexo à lancer'}
              </div>
            </div>
            <div>
              <div className="mb-1.5 text-xs font-medium uppercase tracking-wide text-[var(--color-foreground-muted)]">
                Effet MagicSpells (itemdisplay)
              </div>
              <pre className="overflow-x-auto rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-3 text-xs text-[var(--color-foreground-subtle)]">
                {result.snippet}
              </pre>
              <button
                type="button"
                onClick={() =>
                  navigator.clipboard
                    ?.writeText(result.snippet)
                    .then(() => toast.success('Snippet copié'))
                }
                className="mt-2 inline-flex items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-xs text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
              >
                <IconCopy className="h-3.5 w-3.5" />
                Copier
              </button>
            </div>
            <div className="text-xs text-[var(--color-foreground-muted)]">
              Fichiers écrits : {result.files.length} · reconnecte-toi en jeu
              pour recharger le resource pack.
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setResult(null);
                  setId('');
                  setName('');
                  setBase64(null);
                  setPreview(null);
                  setDims(null);
                }}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
              >
                Créer un autre
              </button>
              <button
                type="button"
                onClick={onClose}
                className="rounded-[8px] bg-[var(--color-accent)] px-5 py-2 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)]"
              >
                Terminé
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-4 px-5 py-5">
            {/* Spritesheet */}
            <div>
              <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-[var(--color-foreground-muted)]">
                Spritesheet PNG (verticale : frames carrées empilées)
              </label>
              <div className="flex items-center gap-3">
                <label className="inline-flex cursor-pointer items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]">
                  <IconUpload className="h-4 w-4" />
                  Choisir un PNG
                  <input
                    type="file"
                    accept="image/png"
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) onPick(f);
                      e.target.value = '';
                    }}
                  />
                </label>
                {preview && (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={preview}
                    alt="aperçu"
                    className="h-14 w-14 rounded border border-[var(--color-border-strong)] bg-[var(--color-background)] object-contain"
                    style={{ imageRendering: 'pixelated' }}
                  />
                )}
                {dims && (
                  <span className="text-xs text-[var(--color-foreground-muted)]">
                    {dims.w}×{dims.h}px
                  </span>
                )}
              </div>
            </div>

            {/* Id + nom */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-[var(--color-foreground-muted)]">
                  Identifiant
                </label>
                <input
                  value={id}
                  onChange={(e) => setId(e.target.value)}
                  placeholder="slash_katon"
                  className={`w-full rounded-[8px] border bg-[var(--color-surface)] px-3 py-1.5 font-mono text-sm text-[var(--color-foreground)] focus:outline-none ${
                    id && !idValid
                      ? 'border-[var(--color-danger)]'
                      : 'border-[var(--color-border-strong)] focus:border-[var(--color-accent)]'
                  }`}
                />
                <div className="mt-1 text-[10px] text-[var(--color-foreground-muted)]">
                  → nexo:{idValid ? id.trim() : '<id>'}
                </div>
              </div>
              <div>
                <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-[var(--color-foreground-muted)]">
                  Nom affiché
                </label>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="(optionnel)"
                  className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-3 py-1.5 text-sm text-[var(--color-foreground)] focus:border-[var(--color-accent)] focus:outline-none"
                />
              </div>
            </div>

            {/* Animation */}
            <div className="flex items-center gap-4">
              <label className="inline-flex cursor-pointer items-center gap-2 text-sm text-[var(--color-foreground-subtle)]">
                <input
                  type="checkbox"
                  checked={animated}
                  onChange={(e) => setAnimated(e.target.checked)}
                  className="h-4 w-4 accent-[var(--color-accent)]"
                />
                Animé
              </label>
              <div className="flex items-center gap-2">
                <span className="text-xs text-[var(--color-foreground-muted)]">
                  Frames
                </span>
                <input
                  type="number"
                  min={1}
                  max={256}
                  value={frames}
                  disabled={!animated}
                  onChange={(e) => setFrames(Math.max(1, Number(e.target.value)))}
                  className="w-16 rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-2 py-1 text-sm text-[var(--color-foreground)] focus:border-[var(--color-accent)] focus:outline-none disabled:opacity-40"
                />
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-[var(--color-foreground-muted)]">
                  Frametime
                </span>
                <input
                  type="number"
                  min={1}
                  max={200}
                  value={frametime}
                  disabled={!animated}
                  onChange={(e) =>
                    setFrametime(Math.max(1, Number(e.target.value)))
                  }
                  className="w-16 rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] px-2 py-1 text-sm text-[var(--color-foreground)] focus:border-[var(--color-accent)] focus:outline-none disabled:opacity-40"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={onClose}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
              >
                Annuler
              </button>
              <button
                type="button"
                disabled={!canSubmit}
                onClick={() => createMut.mutate()}
                className="rounded-[8px] bg-[var(--color-accent)] px-5 py-2 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)] disabled:opacity-40"
              >
                {createMut.isPending ? 'Création…' : 'Créer l’item'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Éditeur / aperçu (plein largeur) ───────────────────────

function FileEditor(props: {
  filePath: string;
  canWrite: boolean;
  onInvalidate: () => void;
  onClose: () => void;
  onDeleted: () => void;
}) {
  return <FileEditorInner key={props.filePath} {...props} />;
}

function FileEditorInner({
  filePath,
  canWrite,
  onInvalidate,
  onClose,
  onDeleted,
}: {
  filePath: string;
  canWrite: boolean;
  onInvalidate: () => void;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const [draft, setDraft] = useState<string | null>(null);

  const fileQuery = useQuery({
    queryKey: ['files', 'read', filePath],
    queryFn: () => readFile(filePath),
  });

  const file = fileQuery.data;
  const value = draft ?? file?.content ?? '';
  const dirty = draft != null && draft !== file?.content;

  const saveMut = useMutation({
    mutationFn: () => writeFile(filePath, value),
    onSuccess: () => {
      toast.success('Sauvegardé');
      setDraft(null);
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Échec de la sauvegarde', { description: errMessage(err) }),
  });

  const deleteMut = useMutation({
    mutationFn: () => deleteFile(filePath),
    onSuccess: () => {
      toast.success('Supprimé');
      onDeleted();
      onInvalidate();
    },
    onError: (err) =>
      toast.error('Suppression impossible', { description: errMessage(err) }),
  });

  return (
    <div className="flex flex-col">
      {/* En-tête éditeur : nom + Sauvegarder + Fermer */}
      <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <IconFile className="h-4 w-4 shrink-0 text-[var(--color-foreground-muted)]" />
          <span className="truncate font-mono text-sm text-[var(--color-foreground)]">
            {basename(filePath)}
          </span>
          {file && (
            <span className="shrink-0 text-xs text-[var(--color-foreground-muted)]">
              · {humanSize(file.size)} · {file.kind}
            </span>
          )}
          {dirty && (
            <span className="shrink-0 rounded-full bg-[var(--color-accent-soft)] px-2 py-0.5 text-[10px] text-[var(--color-accent)]">
              non enregistré
            </span>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {canWrite && file?.kind === 'text' && (
            <button
              type="button"
              disabled={saveMut.isPending || !file.editable || !dirty}
              onClick={() => saveMut.mutate()}
              className="inline-flex items-center gap-1.5 rounded-[8px] bg-[var(--color-accent)] px-4 py-1.5 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)] disabled:opacity-40"
            >
              {saveMut.isPending ? 'Enregistrement…' : 'Sauvegarder'}
            </button>
          )}
          {canWrite && (
            <button
              type="button"
              disabled={deleteMut.isPending}
              onClick={() => {
                if (confirm('Supprimer définitivement ce fichier ?'))
                  deleteMut.mutate();
              }}
              aria-label="Supprimer"
              className="inline-flex items-center gap-1.5 rounded-[8px] border border-[var(--color-danger)]/40 px-3 py-1.5 text-sm text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)] disabled:opacity-40"
            >
              <IconTrash className="h-4 w-4" />
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="inline-flex items-center gap-1.5 rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)]"
          >
            <IconX className="h-4 w-4" />
            Fermer
          </button>
        </div>
      </div>

      <div className="p-4">
        {fileQuery.isLoading && !file ? (
          <SkeletonRows count={6} />
        ) : fileQuery.error ? (
          <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
            {errMessage(fileQuery.error)}
          </div>
        ) : !file ? null : file.kind === 'text' ? (
          <div className="space-y-3">
            {!file.editable && (
              <div className="rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] px-3 py-2 text-xs text-[var(--color-foreground-muted)]">
                Fichier trop volumineux — affiché en lecture seule (tronqué).
              </div>
            )}
            <textarea
              value={value}
              onChange={(e) => setDraft(e.target.value)}
              rows={24}
              spellCheck={false}
              readOnly={!canWrite || !file.editable}
              className="w-full resize-y rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-3 font-mono text-sm leading-relaxed focus:border-[var(--color-accent)] focus:outline-none"
            />
            {canWrite && dirty && (
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setDraft(null)}
                  className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
                >
                  Réinitialiser
                </button>
                <button
                  type="button"
                  disabled={saveMut.isPending || !file.editable}
                  onClick={() => saveMut.mutate()}
                  className="rounded-[8px] bg-[var(--color-accent)] px-5 py-2 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)] disabled:opacity-40"
                >
                  {saveMut.isPending ? 'Enregistrement…' : 'Sauvegarder'}
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
                className="max-h-[520px] max-w-full rounded"
                style={{ imageRendering: 'pixelated' }}
              />
            </div>
            <div className="text-xs text-[var(--color-foreground-muted)]">
              Aperçu — remplace via « Envoyer ».
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
