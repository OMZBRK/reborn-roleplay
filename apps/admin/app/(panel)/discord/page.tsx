'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import { DiscordMarkdown, DiscordPreview } from '@/components/DiscordPreview';
import {
  IconArrowDown,
  IconArrowUp,
  IconExternal,
  IconImage,
  IconRefresh,
  IconTrash,
  IconUpload,
} from '@/components/icons';
import { ApiError } from '@/lib/api';
import {
  CANAL_LIBELLE,
  CANAL_PING,
  CANAL_SALON,
  CANAUX,
  COULEURS,
  LIMITE_IMAGES,
  LIMITE_LIENS,
  LIMITE_TEXTE,
  MAX_IMAGE_OCTETS,
  createMessage,
  listMessages,
  modeles,
  removeMessage,
  updateMessage,
  uploadMedia,
  type ActionMessage,
  type Bloc,
  type Canal,
  type Lien,
  type PingOption,
  type StaffDiscordMessage,
} from '@/lib/discord';

/**
 * Page « Discord » : rédiger, prévisualiser et publier les messages du bot
 * dans `annonces`, `dev-updates` et `build-updates` du Discord staff (cadre composants v2).
 * Historique avec modification (réécrit le message Discord) et retrait.
 * API : /v1/admin/discord/* (ADMIN+), bot : /webhooks/staff-message.
 */

type TypeBloc = 'texte' | 'images' | 'separateur' | 'liens';

interface BlocEd {
  key: string;
  type: TypeBloc;
  texte: string;
  images: string[];
  liens: Lien[];
}

let seq = 0;
const nk = () => `b${Date.now().toString(36)}${(seq++).toString(36)}`;

function nouveauBloc(type: TypeBloc): BlocEd {
  return { key: nk(), type, texte: '', images: [], liens: type === 'liens' ? [{ libelle: '', url: '' }] : [] };
}

/** API → éditeur : un bloc API à plusieurs clés devient plusieurs blocs, dans l'ordre du rendu. */
function versEditeur(blocs: Bloc[]): BlocEd[] {
  const out: BlocEd[] = [];
  for (const b of blocs) {
    if (b.texte !== undefined) out.push({ ...nouveauBloc('texte'), texte: b.texte });
    if (b.images !== undefined) out.push({ ...nouveauBloc('images'), images: [...b.images] });
    if (b.separateur) out.push(nouveauBloc('separateur'));
    if (b.liens !== undefined) out.push({ ...nouveauBloc('liens'), liens: b.liens.map((l) => ({ ...l })) });
  }
  return out;
}

/** Éditeur → API : un bloc par élément, blocs vides retirés. */
function versApi(blocs: BlocEd[]): Bloc[] {
  const out: Bloc[] = [];
  for (const b of blocs) {
    if (b.type === 'texte' && b.texte.trim()) out.push({ texte: b.texte });
    if (b.type === 'images' && b.images.length) out.push({ images: b.images });
    if (b.type === 'separateur') out.push({ separateur: true });
    if (b.type === 'liens') {
      const liens = b.liens
        .filter((l) => l.libelle.trim() || l.url.trim())
        .map((l) => ({ libelle: l.libelle.trim(), url: l.url.trim(), ...(l.emoji?.trim() ? { emoji: l.emoji.trim() } : {}) }));
      if (liens.length) out.push({ liens });
    }
  }
  return out;
}

function messageErreur(err: unknown): { titre: string; details?: string } {
  if (err instanceof ApiError) {
    const body = err.body as { message?: string | string[]; details?: string[] } | undefined;
    const msg = Array.isArray(body?.message) ? body?.message.join(' · ') : body?.message ?? err.message;
    return { titre: msg, details: body?.details?.join(' · ') };
  }
  return { titre: (err as Error).message };
}

const STATUT_STYLE: Record<StaffDiscordMessage['statut'], { label: string; cls: string }> = {
  brouillon: { label: 'Brouillon', cls: 'border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)]' },
  publie: { label: 'Publié', cls: 'border-[var(--color-success)]/40 bg-[var(--color-success-soft)] text-[var(--color-success)]' },
  retire: { label: 'Retiré', cls: 'border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] text-[var(--color-danger)]' },
};

const INPUT =
  'w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] px-3 py-2 text-sm focus:border-[var(--color-accent)] focus:outline-none';

export default function DiscordPage() {
  const qc = useQueryClient();

  // ── État de l'éditeur ──
  const [editId, setEditId] = useState<string | null>(null);
  const [statut, setStatut] = useState<StaffDiscordMessage['statut'] | null>(null);
  const [discordUrl, setDiscordUrl] = useState<string | null>(null);
  const [canal, setCanal] = useState<Canal>('dev-updates');
  const [couleur, setCouleur] = useState('#A0182B');
  const [ping, setPing] = useState<PingOption>('aucun');
  const [blocs, setBlocs] = useState<BlocEd[]>(() => versEditeur(modeles()[0]!.blocs));
  const [medias, setMedias] = useState<Record<string, string>>({});
  const [confirmPublier, setConfirmPublier] = useState(false);
  const [filtre, setFiltre] = useState<Canal | 'tous'>('tous');

  const blocsApi = useMemo(() => versApi(blocs), [blocs]);
  const nbTexte = blocsApi.reduce((n, b) => n + (b.texte?.length ?? 0), 0);
  const nbImages = blocsApi.reduce((n, b) => n + (b.images?.length ?? 0), 0);
  const publie = statut === 'publie';

  const historique = useQuery({
    queryKey: ['admin', 'discord', 'messages'],
    queryFn: () => listMessages(),
    refetchInterval: 30_000,
  });

  function charger(m: StaffDiscordMessage) {
    setEditId(m.id);
    setStatut(m.statut);
    setDiscordUrl(m.discordUrl);
    setCanal(m.canal);
    setCouleur(m.couleur);
    setPing(m.ping);
    setBlocs(versEditeur(m.blocs));
    setMedias((prev) => ({ ...prev, ...m.medias }));
  }

  function nouveau() {
    setEditId(null);
    setStatut(null);
    setDiscordUrl(null);
    setPing('aucun');
  }

  function appliquerModele(id: string) {
    const mod = modeles().find((x) => x.id === id);
    if (!mod) return;
    const nonVide = blocsApi.some((b) => b.texte?.trim() || b.images?.length);
    if (nonVide && !window.confirm(`Remplacer le contenu actuel par le modèle « ${mod.nom} » ?`)) return;
    nouveau();
    setCanal(mod.canal);
    setCouleur(mod.couleur);
    setBlocs(versEditeur(mod.blocs));
  }

  const sauver = useMutation({
    mutationFn: (action: ActionMessage) => {
      const input = { canal, couleur, blocs: blocsApi, ping, action };
      return editId ? updateMessage(editId, input) : createMessage(input);
    },
    onSuccess: (m, action) => {
      qc.invalidateQueries({ queryKey: ['admin', 'discord', 'messages'] });
      setEditId(m.id);
      setStatut(m.statut);
      setDiscordUrl(m.discordUrl);
      setMedias((prev) => ({ ...prev, ...m.medias }));
      if (action === 'brouillon') toast.success('Brouillon enregistré');
      if (action === 'apercu') {
        if (m.apercu?.url) {
          toast.success('Aperçu posté dans le salon de test', {
            action: { label: 'Ouvrir', onClick: () => window.open(m.apercu!.url!, '_blank') },
          });
        } else {
          toast.success('Rendu validé par le bot', {
            description: 'Aucun salon de test configuré : rien n’a été posté sur Discord.',
          });
        }
      }
      if (action === 'publier') {
        toast.success(publie ? 'Message mis à jour sur Discord' : 'Message publié sur Discord', {
          action: m.discordUrl ? { label: 'Ouvrir', onClick: () => window.open(m.discordUrl!, '_blank') } : undefined,
        });
      }
    },
    onError: (err) => {
      const e = messageErreur(err);
      toast.error(e.titre, { description: e.details });
    },
  });

  const retirer = useMutation({
    mutationFn: (m: StaffDiscordMessage) => removeMessage(m.id),
    onSuccess: (res, m) => {
      qc.invalidateQueries({ queryKey: ['admin', 'discord', 'messages'] });
      toast.success(res.deleted ? 'Brouillon supprimé' : 'Message retiré de Discord');
      if (m.id === editId) {
        if (res.deleted) nouveau();
        else {
          setStatut('retire');
          setDiscordUrl(null);
        }
      }
    },
    onError: (err) => {
      const e = messageErreur(err);
      toast.error(e.titre, { description: e.details });
    },
  });

  // ── Opérations sur les blocs ──
  const maj = (key: string, patch: Partial<BlocEd>) =>
    setBlocs((bs) => bs.map((b) => (b.key === key ? { ...b, ...patch } : b)));
  const deplacer = (key: string, d: -1 | 1) =>
    setBlocs((bs) => {
      const i = bs.findIndex((b) => b.key === key);
      const j = i + d;
      if (i < 0 || j < 0 || j >= bs.length) return bs;
      const copy = [...bs];
      [copy[i], copy[j]] = [copy[j]!, copy[i]!];
      return copy;
    });
  const supprimer = (key: string) => setBlocs((bs) => bs.filter((b) => b.key !== key));
  const ajouter = (type: TypeBloc) => setBlocs((bs) => [...bs, nouveauBloc(type)]);

  async function televerser(key: string, files: File[]) {
    const images = files.filter((f) => /^image\/(png|jpe?g|webp|gif)$/.test(f.type));
    if (images.length < files.length) toast.error('Images PNG, JPEG, WebP ou GIF uniquement.');
    const place = LIMITE_IMAGES - nbImages;
    if (images.length > place) toast.error(`${LIMITE_IMAGES} images maximum par message.`);
    for (const f of images.slice(0, Math.max(0, place))) {
      if (f.size > MAX_IMAGE_OCTETS) {
        toast.error(`${f.name} : plus de 8 Mo.`);
        continue;
      }
      try {
        const { id, url } = await uploadMedia(f);
        setMedias((prev) => ({ ...prev, [id]: url }));
        setBlocs((bs) => bs.map((b) => (b.key === key ? { ...b, images: [...b.images, id] } : b)));
      } catch (err) {
        toast.error(`${f.name} : ${messageErreur(err).titre}`);
      }
    }
  }

  const peutEnvoyer = blocsApi.length > 0 && nbTexte <= LIMITE_TEXTE && nbImages <= LIMITE_IMAGES && !sauver.isPending;
  const historiqueFiltre = (historique.data ?? []).filter((m) => filtre === 'tous' || m.canal === filtre);
  const optionPing = CANAL_PING[canal];

  return (
    <div className="px-10 py-10 max-w-[1500px] mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">Discord staff</div>
        <h1 className="mt-1 text-4xl leading-none" style={{ fontFamily: 'var(--font-display)' }}>
          Annonces Discord
        </h1>
        <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
          Rédige un message pour <span className="text-[var(--color-foreground)]">annonces</span>,{' '}
          <span className="text-[var(--color-foreground)]">dev-updates</span> ou{' '}
          <span className="text-[var(--color-foreground)]">build-updates</span>, vérifie l’aperçu, puis publie : le bot le poste
          dans un cadre. Un message publié se modifie sur place.
        </p>
      </header>

      <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_minmax(0,600px)]">
        {/* ── Éditeur ── */}
        <section className="min-w-0 space-y-4">
          <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <span className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                  {editId ? 'Modification' : 'Nouveau message'}
                </span>
                {statut && (
                  <span className={`rounded-full border px-2 py-0.5 text-[11px] ${STATUT_STYLE[statut].cls}`}>
                    {STATUT_STYLE[statut].label}
                  </span>
                )}
                {discordUrl && (
                  <a href={discordUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-xs text-[var(--color-accent-hover)] hover:underline">
                    Voir sur Discord <IconExternal width={12} height={12} />
                  </a>
                )}
              </div>
              {editId && (
                <button type="button" onClick={nouveau} className="text-xs text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]">
                  Repartir de ce contenu comme nouveau message
                </button>
              )}
            </div>

            <div>
              <div className="mb-2 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">Modèles</div>
              <div className="flex flex-wrap gap-2">
                {modeles().map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => appliquerModele(m.id)}
                    className="rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] px-3 py-2 text-left hover:border-[var(--color-accent)] transition-colors"
                  >
                    <div className="text-sm text-[var(--color-foreground)]">{m.nom}</div>
                    <div className="text-[11px] text-[var(--color-foreground-muted)]">{m.description}</div>
                  </button>
                ))}
              </div>
            </div>

            <div className="flex flex-wrap items-end gap-6">
              <div>
                <div className="mb-2 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">Salon</div>
                <div className="inline-flex rounded-[10px] border border-[var(--color-border-strong)] p-1 bg-[var(--color-background)]">
                  {CANAUX.map((c) => (
                    <button
                      key={c}
                      type="button"
                      disabled={publie && c !== canal}
                      title={publie && c !== canal ? 'Salon fixé après publication' : undefined}
                      onClick={() => {
                        setCanal(c);
                        setPing('aucun');
                      }}
                      className={`rounded-[7px] px-3 py-1.5 text-sm transition-colors disabled:opacity-30 ${
                        canal === c
                          ? 'bg-[var(--color-accent-soft)] text-[var(--color-foreground)] shadow-[inset_0_0_0_1px_var(--color-accent)]'
                          : 'text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]'
                      }`}
                    >
                      {CANAL_LIBELLE[c]}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <div className="mb-2 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">Couleur du cadre</div>
                <div className="flex items-center gap-2">
                  {COULEURS.map((c) => (
                    <button
                      key={c.valeur}
                      type="button"
                      title={c.nom}
                      onClick={() => setCouleur(c.valeur)}
                      className={`h-7 w-7 rounded-full border-2 transition-transform ${
                        couleur.toUpperCase() === c.valeur ? 'border-white scale-110' : 'border-transparent'
                      }`}
                      style={{ background: c.valeur }}
                    />
                  ))}
                  <input
                    type="color"
                    value={couleur}
                    onChange={(e) => setCouleur(e.target.value.toUpperCase())}
                    className="h-7 w-9 cursor-pointer rounded border border-[var(--color-border-strong)] bg-transparent"
                    title="Autre couleur"
                  />
                  <span className="font-mono text-xs text-[var(--color-foreground-muted)]">{couleur}</span>
                </div>
              </div>

              {optionPing && (
                <label className="flex items-center gap-2 text-sm text-[var(--color-foreground-subtle)] cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={ping === optionPing}
                    onChange={(e) => setPing(e.target.checked ? optionPing : 'aucun')}
                    className="h-4 w-4 accent-[var(--color-accent)]"
                  />
                  Mentionner {optionPing === 'everyone' ? '@everyone' : '@Testeur'}
                  <span className="text-[11px] text-[var(--color-foreground-muted)]">(notifie à la publication)</span>
                </label>
              )}
            </div>
          </div>

          {/* Blocs */}
          {blocs.map((b, i) => (
            <BlocCard
              key={b.key}
              bloc={b}
              index={i}
              total={blocs.length}
              medias={medias}
              imagesRestantes={LIMITE_IMAGES - nbImages}
              onChange={(patch) => maj(b.key, patch)}
              onMove={(d) => deplacer(b.key, d)}
              onDelete={() => supprimer(b.key)}
              onUpload={(files) => televerser(b.key, files)}
            />
          ))}

          <div className="flex flex-wrap items-center gap-2 rounded-[14px] border border-dashed border-[var(--color-border-strong)] px-4 py-3">
            <span className="mr-2 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">Ajouter</span>
            {(
              [
                ['texte', 'Texte'],
                ['images', 'Images'],
                ['separateur', 'Séparateur'],
                ['liens', 'Boutons-liens'],
              ] as [TypeBloc, string][]
            ).map(([t, l]) => (
              <button
                key={t}
                type="button"
                onClick={() => ajouter(t)}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-1.5 text-sm text-[var(--color-foreground-subtle)] hover:border-[var(--color-accent)] hover:text-[var(--color-foreground)]"
              >
                + {l}
              </button>
            ))}
          </div>

          {/* Barre d'actions */}
          <div className="sticky bottom-0 z-10 -mx-1 rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface-elevated)]/95 px-5 py-4 backdrop-blur flex flex-wrap items-center justify-between gap-3 shadow-[var(--shadow-md)]">
            <div className="text-xs text-[var(--color-foreground-muted)] space-x-3">
              <span className={nbTexte > LIMITE_TEXTE ? 'text-[var(--color-danger)]' : ''}>
                {nbTexte} / {LIMITE_TEXTE} caractères
              </span>
              <span className={nbImages > LIMITE_IMAGES ? 'text-[var(--color-danger)]' : ''}>
                {nbImages} / {LIMITE_IMAGES} images
              </span>
            </div>
            <div className="flex flex-wrap gap-2">
              {!publie && (
                <button
                  type="button"
                  disabled={!peutEnvoyer}
                  onClick={() => sauver.mutate('brouillon')}
                  className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)] disabled:opacity-40"
                >
                  Enregistrer le brouillon
                </button>
              )}
              <button
                type="button"
                disabled={!peutEnvoyer}
                onClick={() => sauver.mutate('apercu')}
                className="rounded-[8px] border border-[var(--color-accent)]/60 px-4 py-2 text-sm text-[var(--color-foreground)] hover:bg-[var(--color-accent-soft)] disabled:opacity-40"
              >
                Aperçu Discord
              </button>
              <button
                type="button"
                disabled={!peutEnvoyer}
                onClick={() => setConfirmPublier(true)}
                className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2 text-sm font-medium text-white disabled:opacity-40"
              >
                {sauver.isPending ? 'Envoi…' : publie ? 'Mettre à jour sur Discord' : 'Publier'}
              </button>
            </div>
          </div>
        </section>

        {/* ── Aperçu ── */}
        <aside className="min-w-0">
          <div className="xl:sticky xl:top-6 space-y-2">
            <div className="flex items-center justify-between">
              <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">Aperçu</div>
              <div className="text-[11px] text-[var(--color-foreground-muted)]">rendu du client Discord, thème sombre</div>
            </div>
            <DiscordPreview canal={canal} couleur={couleur} blocs={blocsApi} ping={ping} medias={medias} salon={CANAL_SALON[canal]} />
          </div>
        </aside>
      </div>

      {/* ── Historique ── */}
      <section className="mt-12">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-2xl leading-none" style={{ fontFamily: 'var(--font-display)' }}>
            Historique
          </h2>
          <div className="flex items-center gap-2">
            {(['tous', ...CANAUX] as const).map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setFiltre(f)}
                className={`rounded-[8px] px-3 py-1.5 text-xs ${
                  filtre === f
                    ? 'bg-[var(--color-accent-soft)] text-[var(--color-foreground)] shadow-[inset_0_0_0_1px_var(--color-accent)]'
                    : 'text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]'
                }`}
              >
                {f === 'tous' ? 'Tous' : CANAL_LIBELLE[f]}
              </button>
            ))}
            <button
              type="button"
              onClick={() => historique.refetch()}
              title="Rafraîchir"
              className="rounded-[8px] p-1.5 text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)]"
            >
              <IconRefresh width={16} height={16} />
            </button>
          </div>
        </div>

        {historique.isLoading && <div className="text-sm text-[var(--color-foreground-subtle)]">Chargement…</div>}
        {historique.error && (
          <div className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
            {messageErreur(historique.error).titre}
          </div>
        )}
        {historique.data && historiqueFiltre.length === 0 && (
          <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] px-6 py-10 text-center text-sm text-[var(--color-foreground-subtle)]">
            Aucun message pour l’instant.
          </div>
        )}
        {historiqueFiltre.length > 0 && (
          <div className="overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                <tr className="border-b border-[var(--color-border)]">
                  <th className="px-4 py-3 font-normal">Message</th>
                  <th className="px-4 py-3 font-normal">Salon</th>
                  <th className="px-4 py-3 font-normal">Statut</th>
                  <th className="px-4 py-3 font-normal">Auteur</th>
                  <th className="px-4 py-3 font-normal">Date</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {historiqueFiltre.map((m) => (
                  <tr key={m.id} className={`border-b border-[var(--color-border)] last:border-0 ${m.id === editId ? 'bg-[var(--color-accent-soft)]/40' : ''}`}>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="h-3 w-1 rounded-full" style={{ background: m.couleur }} />
                        <span className="text-[var(--color-foreground)]">{m.titre}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-[var(--color-foreground-subtle)]">{CANAL_LIBELLE[m.canal]}</td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full border px-2 py-0.5 text-[11px] ${STATUT_STYLE[m.statut].cls}`}>
                        {STATUT_STYLE[m.statut].label}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-[var(--color-foreground-subtle)]">
                      {m.auteur}
                      {m.modifiePar && m.modifiePar !== m.auteur && (
                        <span className="text-[11px] text-[var(--color-foreground-muted)]"> · modifié par {m.modifiePar}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-[var(--color-foreground-subtle)] whitespace-nowrap">
                      {fmtDate(m.publieLe ?? m.modifieLe)}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        {m.discordUrl && m.statut === 'publie' && (
                          <a href={m.discordUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 rounded-[6px] px-2 py-1 text-xs text-[var(--color-accent-hover)] hover:underline">
                            Discord <IconExternal width={12} height={12} />
                          </a>
                        )}
                        <button
                          type="button"
                          onClick={() => {
                            charger(m);
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                          }}
                          className="rounded-[6px] border border-[var(--color-border-strong)] px-3 py-1.5 text-xs text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]"
                        >
                          {m.statut === 'retire' ? 'Republier' : 'Modifier'}
                        </button>
                        {m.statut !== 'retire' && (
                          <button
                            type="button"
                            disabled={retirer.isPending}
                            onClick={() => {
                              const q =
                                m.statut === 'publie'
                                  ? `Retirer « ${m.titre} » de Discord ? Le message sera supprimé du salon (gardé ici dans l’historique).`
                                  : `Supprimer le brouillon « ${m.titre} » ?`;
                              if (window.confirm(q)) retirer.mutate(m);
                            }}
                            className="rounded-[6px] border border-[var(--color-danger)]/40 px-3 py-1.5 text-xs text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)] disabled:opacity-40"
                          >
                            {m.statut === 'publie' ? 'Retirer' : 'Supprimer'}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {confirmPublier && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setConfirmPublier(false)}>
          <div
            className="w-full max-w-md rounded-[14px] border border-[var(--color-border-strong)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-md)]"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="text-lg text-[var(--color-foreground)]">
              {publie ? 'Mettre à jour le message sur Discord ?' : `Publier dans ${CANAL_SALON[canal]} ?`}
            </div>
            <p className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
              {publie
                ? 'Le message publié est réécrit sur place (même lien). Une modification ne renvoie aucune notification.'
                : ping !== 'aucun'
                  ? `Le message mentionnera ${ping === 'everyone' ? '@everyone' : '@Testeur'} : tout le monde concerné sera notifié.`
                  : 'Aucune mention : personne n’est notifié.'}
            </p>
            <div className="mt-6 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmPublier(false)}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm text-[var(--color-foreground-subtle)]"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={() => {
                  setConfirmPublier(false);
                  sauver.mutate('publier');
                }}
                className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-5 py-2 text-sm font-medium text-white"
              >
                {publie ? 'Mettre à jour' : 'Publier'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

// ── Carte d'un bloc ─────────────────────────────────────

const TITRE_BLOC: Record<TypeBloc, string> = {
  texte: 'Texte',
  images: 'Galerie d’images',
  separateur: 'Séparateur',
  liens: 'Boutons-liens',
};

function BlocCard({
  bloc,
  index,
  total,
  medias,
  imagesRestantes,
  onChange,
  onMove,
  onDelete,
  onUpload,
}: {
  bloc: BlocEd;
  index: number;
  total: number;
  medias: Record<string, string>;
  imagesRestantes: number;
  onChange: (patch: Partial<BlocEd>) => void;
  onMove: (d: -1 | 1) => void;
  onDelete: () => void;
  onUpload: (files: File[]) => Promise<void>;
}) {
  const [apercuTexte, setApercuTexte] = useState(false);
  const [survol, setSurvol] = useState(false);
  const [envoi, setEnvoi] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const zoneRef = useRef<HTMLTextAreaElement>(null);

  async function upload(files: File[]) {
    setEnvoi(true);
    try {
      await onUpload(files);
    } finally {
      setEnvoi(false);
    }
  }

  /** Insère un motif markdown autour de la sélection du textarea. */
  function inserer(avant: string, apres = '', ligne = false) {
    const ta = zoneRef.current;
    const v = bloc.texte;
    const s = ta?.selectionStart ?? v.length;
    const e = ta?.selectionEnd ?? v.length;
    let debut = s;
    if (ligne) debut = v.lastIndexOf('\n', s - 1) + 1;
    const next = ligne ? v.slice(0, debut) + avant + v.slice(debut) : v.slice(0, s) + avant + v.slice(s, e) + apres + v.slice(e);
    onChange({ texte: next });
    requestAnimationFrame(() => {
      ta?.focus();
      const pos = ligne ? e + avant.length : e + avant.length;
      ta?.setSelectionRange(pos, pos);
    });
  }

  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      <div className="flex items-center justify-between border-b border-[var(--color-border)] px-4 py-2">
        <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
          {index + 1} · {TITRE_BLOC[bloc.type]}
        </div>
        <div className="flex items-center gap-1 text-[var(--color-foreground-muted)]">
          {bloc.type === 'texte' && (
            <button
              type="button"
              onClick={() => setApercuTexte((x) => !x)}
              className="mr-2 rounded-[6px] px-2 py-1 text-xs hover:text-[var(--color-foreground)]"
            >
              {apercuTexte ? 'Écrire' : 'Aperçu'}
            </button>
          )}
          <button type="button" disabled={index === 0} onClick={() => onMove(-1)} className="rounded p-1 hover:text-[var(--color-foreground)] disabled:opacity-30" title="Monter">
            <IconArrowUp width={15} height={15} />
          </button>
          <button type="button" disabled={index === total - 1} onClick={() => onMove(1)} className="rounded p-1 hover:text-[var(--color-foreground)] disabled:opacity-30" title="Descendre">
            <IconArrowDown width={15} height={15} />
          </button>
          <button type="button" onClick={onDelete} className="rounded p-1 hover:text-[var(--color-danger)]" title="Supprimer le bloc">
            <IconTrash width={15} height={15} />
          </button>
        </div>
      </div>

      <div className="p-4">
        {bloc.type === 'texte' &&
          (apercuTexte ? (
            <div className="min-h-[96px] rounded-[8px] bg-[#2b2d31] p-3 text-[15px] text-[#dbdee1]" style={{ fontFamily: '"gg sans", "Noto Sans", Helvetica, Arial, sans-serif', lineHeight: 1.375 }}>
              <DiscordMarkdown source={bloc.texte || ' '} />
            </div>
          ) : (
            <>
              <div className="mb-2 flex flex-wrap gap-1">
                {(
                  [
                    ['Titre', () => inserer('## ', '', true)],
                    ['Sous-texte', () => inserer('-# ', '', true)],
                    ['Gras', () => inserer('**', '**')],
                    ['Italique', () => inserer('*', '*')],
                    ['Liste', () => inserer('- ', '', true)],
                    ['Code', () => inserer('`', '`')],
                    ['{#salon}', () => inserer('{#retours-de-test}')],
                    ['{@rôle}', () => inserer('{@Testeur}')],
                  ] as [string, () => void][]
                ).map(([l, fn]) => (
                  <button
                    key={l}
                    type="button"
                    onClick={fn}
                    className="rounded-[6px] border border-[var(--color-border)] px-2 py-0.5 text-[11px] text-[var(--color-foreground-subtle)] hover:border-[var(--color-accent)] hover:text-[var(--color-foreground)]"
                  >
                    {l}
                  </button>
                ))}
              </div>
              <textarea
                ref={zoneRef}
                value={bloc.texte}
                onChange={(e) => onChange({ texte: e.target.value })}
                rows={Math.min(14, Math.max(3, bloc.texte.split('\n').length + 1))}
                placeholder="Markdown Discord : # titre, ## section, -# petit texte, **gras**, - liste, {#salon}, {@rôle}"
                className={`${INPUT} font-mono text-[13px] leading-relaxed`}
              />
            </>
          ))}

        {bloc.type === 'images' && (
          <div>
            <div
              onDragOver={(e) => {
                e.preventDefault();
                setSurvol(true);
              }}
              onDragLeave={() => setSurvol(false)}
              onDrop={(e) => {
                e.preventDefault();
                setSurvol(false);
                void upload(Array.from(e.dataTransfer.files));
              }}
              onClick={() => inputRef.current?.click()}
              className={`flex cursor-pointer flex-col items-center justify-center gap-1 rounded-[10px] border border-dashed px-4 py-5 text-sm transition-colors ${
                survol
                  ? 'border-[var(--color-accent)] bg-[var(--color-accent-soft)] text-[var(--color-foreground)]'
                  : 'border-[var(--color-border-strong)] text-[var(--color-foreground-subtle)] hover:border-[var(--color-accent)]'
              }`}
            >
              {envoi ? <IconUpload className="animate-pulse" /> : <IconImage />}
              <span>{envoi ? 'Envoi…' : 'Glisse des images ici ou clique pour choisir'}</span>
              <span className="text-[11px] text-[var(--color-foreground-muted)]">
                PNG, JPEG, WebP, GIF · 8 Mo max · encore {Math.max(0, imagesRestantes)} possible(s)
              </span>
              <input
                ref={inputRef}
                type="file"
                multiple
                accept="image/png,image/jpeg,image/webp,image/gif"
                className="hidden"
                onChange={(e) => {
                  void upload(Array.from(e.target.files ?? []));
                  e.target.value = '';
                }}
              />
            </div>
            {bloc.images.length > 0 && (
              <div className="mt-3 grid grid-cols-4 gap-2 sm:grid-cols-5">
                {bloc.images.map((id, j) => (
                  <div key={id} className="group relative aspect-video overflow-hidden rounded-[8px] border border-[var(--color-border)] bg-[var(--color-background)]">
                    {medias[id] ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={medias[id]} alt="" className="h-full w-full object-cover" />
                    ) : (
                      <div className="flex h-full items-center justify-center text-[10px] text-[var(--color-foreground-muted)]">image</div>
                    )}
                    <div className="absolute inset-x-0 bottom-0 flex justify-between bg-black/60 px-1 py-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button
                        type="button"
                        disabled={j === 0}
                        onClick={() => {
                          const imgs = [...bloc.images];
                          [imgs[j - 1], imgs[j]] = [imgs[j]!, imgs[j - 1]!];
                          onChange({ images: imgs });
                        }}
                        className="px-1 text-xs text-white disabled:opacity-30"
                      >
                        ←
                      </button>
                      <button type="button" onClick={() => onChange({ images: bloc.images.filter((x) => x !== id) })} className="px-1 text-xs text-white hover:text-[var(--color-danger)]">
                        ✕
                      </button>
                      <button
                        type="button"
                        disabled={j === bloc.images.length - 1}
                        onClick={() => {
                          const imgs = [...bloc.images];
                          [imgs[j + 1], imgs[j]] = [imgs[j]!, imgs[j + 1]!];
                          onChange({ images: imgs });
                        }}
                        className="px-1 text-xs text-white disabled:opacity-30"
                      >
                        →
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {bloc.type === 'separateur' && <div className="h-px bg-[var(--color-border-strong)]" />}

        {bloc.type === 'liens' && (
          <div className="space-y-2">
            {bloc.liens.map((l, j) => (
              <div key={j} className="flex items-center gap-2">
                <input
                  value={l.emoji ?? ''}
                  onChange={(e) => onChange({ liens: bloc.liens.map((x, k) => (k === j ? { ...x, emoji: e.target.value } : x)) })}
                  placeholder="⬇️"
                  className={`${INPUT} w-14 text-center`}
                  title="Emoji (optionnel)"
                />
                <input
                  value={l.libelle}
                  maxLength={80}
                  onChange={(e) => onChange({ liens: bloc.liens.map((x, k) => (k === j ? { ...x, libelle: e.target.value } : x)) })}
                  placeholder="Libellé"
                  className={`${INPUT} w-48`}
                />
                <input
                  value={l.url}
                  onChange={(e) => onChange({ liens: bloc.liens.map((x, k) => (k === j ? { ...x, url: e.target.value } : x)) })}
                  placeholder="https://…"
                  className={`${INPUT} flex-1 ${l.url && !/^https?:\/\/\S+$/.test(l.url) ? 'border-[var(--color-danger)]' : ''}`}
                />
                <button
                  type="button"
                  onClick={() => onChange({ liens: bloc.liens.filter((_, k) => k !== j) })}
                  className="rounded p-1 text-[var(--color-foreground-muted)] hover:text-[var(--color-danger)]"
                  title="Retirer le bouton"
                >
                  <IconTrash width={15} height={15} />
                </button>
              </div>
            ))}
            {bloc.liens.length < LIMITE_LIENS && (
              <button
                type="button"
                onClick={() => onChange({ liens: [...bloc.liens, { libelle: '', url: '' }] })}
                className="text-xs text-[var(--color-foreground-subtle)] hover:text-[var(--color-foreground)]"
              >
                + Ajouter un bouton ({bloc.liens.length}/{LIMITE_LIENS})
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
