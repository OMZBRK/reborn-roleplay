'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { use, useState } from 'react';
import { toast } from 'sonner';
import { AssignmentBlock } from '@/components/AssignmentBlock';
import { ChatPanel } from '@/components/ChatPanel';
import { api } from '@/lib/api';
import type { AppStatus, WhitelistDetail } from '@/lib/types';

type Decision = 'APPROVED' | 'REJECTED' | 'NEEDS_REVISION';

export default function WhitelistDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const router = useRouter();
  const qc = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'whitelist', id],
    queryFn: () => api<WhitelistDetail>(`/admin/whitelist/${id}`),
    // Polling 5s identique aux tickets — le launcher poste cote candidat,
    // on veut voir ses messages arriver sans F5.
    refetchInterval: 5_000,
  });

  const [confirmReset, setConfirmReset] = useState(false);
  const resetMut = useMutation({
    mutationFn: () =>
      api<{ ok: boolean; roleDemoted: boolean }>(
        `/admin/whitelist/${id}/reset`,
        { method: 'POST' },
      ),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      toast.success('Candidature réinitialisée', {
        description: res.roleDemoted
          ? 'Le joueur repasse PLAYER et peut re-soumettre une candidature.'
          : 'Rôle staff conservé — le joueur peut re-soumettre une candidature.',
      });
      router.push('/whitelist');
    },
    onError: (err) => {
      toast.error('Echec du reset', { description: (err as Error).message });
    },
  });

  if (isLoading) {
    return <div className="p-10 text-[var(--color-foreground-subtle)]">Chargement…</div>;
  }
  if (error) {
    return (
      <div className="p-10">
        <Link
          href="/whitelist"
          className="text-sm text-[var(--color-accent)] hover:underline"
        >
          ← Retour
        </Link>
        <div className="mt-4 rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)] px-4 py-3 text-sm text-[var(--color-danger)]">
          {(error as Error).message}
        </div>
      </div>
    );
  }
  if (!data) return null;

  return (
    <div className="px-10 py-10 max-w-5xl mx-auto">
      <Link
        href="/whitelist"
        className="text-sm text-[var(--color-accent)] hover:underline"
      >
        ← Toutes les candidatures
      </Link>

      <header className="mt-3 mb-8 flex items-start justify-between gap-6">
        <div>
          <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
            Candidature
          </div>
          <h1
            className="mt-1 text-4xl leading-none"
            style={{ fontFamily: 'var(--font-display)' }}
          >
            {data.firstName} {data.lastName}
          </h1>
          <div className="mt-2 text-sm text-[var(--color-foreground-subtle)]">
            par{' '}
            <Link
              href={`/players/${data.user.id}`}
              className="font-medium text-[var(--color-foreground)] hover:text-[var(--color-accent)] hover:underline"
            >
              {data.user.minecraftUsername}
            </Link>
            {data.user.discordUsername && (
              <> · @{data.user.discordUsername}</>
            )}
          </div>
        </div>
        <div className="flex flex-col items-end gap-1.5">
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
              HRP
            </span>
            <StatusBadge status={data.hrpStatus} />
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)]">
              RP
            </span>
            <StatusBadge status={data.rpStatus} />
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_440px] gap-6 items-start">
        {/* Colonne gauche : contenu narratif + info froides */}
        <div className="space-y-6 min-w-0">
          <Card title="HRP — Hors roleplay">
            <Field label="Date de naissance">
              {new Date(data.dob).toLocaleDateString('fr-FR')}
            </Field>
            <Field label="Motivation" block>
              {data.motivation}
            </Field>
            <Field label="Experience RP" block>
              {data.experience}
            </Field>
            <Field label="Disponibilites" block>
              {data.availability}
            </Field>
          </Card>

          <Card title="Personnage">
            <Field label="Village">{data.village}</Field>
            {data.support && <Field label="Soutien (style RP)">{data.support}</Field>}
            <Field label="Histoire" block>
              {data.history}
            </Field>
            <Field label="Apparence" block>
              {data.appearance}
            </Field>
            <Field label="Objectifs" block>
              {data.objectives}
            </Field>
          </Card>

          <Card title="Joueur">
            <Field label="Pseudo MC">
              <Link
                href={`/players/${data.user.id}`}
                className="hover:text-[var(--color-accent)] hover:underline"
              >
                {data.user.minecraftUsername}
              </Link>
            </Field>
            <Field label="UUID">
              <code className="text-xs font-mono text-[var(--color-foreground-subtle)] break-all">
                {data.user.minecraftUuid}
              </code>
            </Field>
            <Field label="Role">{data.user.role}</Field>
            {data.user.discordUsername && (
              <Field label="Discord">@{data.user.discordUsername}</Field>
            )}
            <Link
              href={`/players/${data.user.id}`}
              className="mt-2 block rounded-[8px] border border-[var(--color-border-strong)] text-center py-2 text-xs text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-foreground)] transition-colors"
            >
              Voir le profil complet →
            </Link>
          </Card>

          <Card title="Meta">
            <Field label="Soumise le">
              {new Date(data.submittedAt).toLocaleString('fr-FR', {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}
            </Field>
            {data.reviewedAt && (
              <Field label="Revue le">
                {new Date(data.reviewedAt).toLocaleString('fr-FR', {
                  dateStyle: 'medium',
                  timeStyle: 'short',
                })}
              </Field>
            )}
            {data.reviewNotes && (
              <Field label="Notes de revue" block>
                {data.reviewNotes}
              </Field>
            )}
          </Card>

          {/* Zone admin : reset propre (remplace le DELETE SQL brut). Rétrograde
              WHITELISTED→PLAYER, jamais le staff. Dispo quel que soit le statut. */}
          <div className="rounded-[14px] border border-[var(--color-danger)]/30 bg-[var(--color-danger-soft)]/30 p-5">
            <div className="mb-2 text-xs uppercase tracking-wider text-[var(--color-danger)]">
              Zone admin — réinitialiser
            </div>
            <p className="mb-4 text-sm text-[var(--color-foreground-subtle)]">
              Supprime définitivement cette candidature (messages inclus). Le
              joueur pourra en re-soumettre une nouvelle. S'il est{' '}
              <strong>WHITELISTED</strong>, il repasse <strong>PLAYER</strong> —
              un rôle staff (HELPER+) est conservé.
            </p>
            {confirmReset ? (
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={resetMut.isPending}
                  onClick={() => resetMut.mutate()}
                  className="flex-1 rounded-[8px] bg-[var(--color-danger)] py-2 text-sm text-white hover:opacity-90 disabled:opacity-40"
                >
                  {resetMut.isPending ? 'Réinitialisation…' : 'Oui, réinitialiser'}
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmReset(false)}
                  className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
                >
                  Annuler
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmReset(true)}
                className="rounded-[8px] border border-[var(--color-danger)]/40 px-4 py-2 text-sm text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]"
              >
                Réinitialiser la candidature
              </button>
            )}
          </div>
        </div>

        {/* Colonne droite : assignation + decision + chat sticky */}
        <aside className="lg:sticky lg:top-6 lg:h-[calc(100vh-3rem)] flex flex-col gap-4">
          <AssignmentBlock
            kind="whitelist"
            id={data.id}
            assignee={data.assignee}
            assignedAt={data.assignedAt}
          />

          <PartDecisionCard
            id={data.id}
            part="HRP"
            label="Prévalidation HRP"
            hint="Après le test oral. HRP validé = verrouillé (non refait au resubmit)."
            current={data.hrpStatus}
          />
          <PartDecisionCard
            id={data.id}
            part="RP"
            label="Validation RP"
            hint="Le personnage. Un RP révisé/refusé renvoie le joueur éditer le RP seul."
            current={data.rpStatus}
          />

          <div className="flex-1 min-h-0">
            <ChatPanel
              endpoint={`/admin/whitelist/${data.id}/messages`}
              queryKey={['admin', 'whitelist', data.id]}
              messages={data.messages}
              canSend={
                data.status === 'PENDING' || data.status === 'NEEDS_REVISION'
              }
              playerUuid={data.user.minecraftUuid}
              playerName={data.user.minecraftUsername}
              emptyLabel="Aucun message — le candidat peut t'écrire depuis son launcher."
              closedLabel={`Candidature ${
                data.status === 'APPROVED' ? 'acceptée' : 'refusée'
              } — conversation cloturée.`}
            />
          </div>
        </aside>
      </div>
    </div>
  );
}

function decisionLabel(d: Decision): string {
  return d === 'APPROVED' ? 'Valider' : d === 'REJECTED' ? 'Refuser' : 'Demander une révision';
}

/**
 * Carte de décision pour UNE partie (HRP ou RP) — L5. Autonome : gère son
 * propre état pending/notes et poste sur PATCH /admin/whitelist/:id/decision.
 * Toujours ré-actionnable (le staff peut re-trancher une partie).
 */
function PartDecisionCard({
  id,
  part,
  label,
  hint,
  current,
}: {
  id: string;
  part: 'HRP' | 'RP';
  label: string;
  hint: string;
  current: AppStatus;
}) {
  const qc = useQueryClient();
  const [pending, setPending] = useState<Decision | null>(null);
  const [notes, setNotes] = useState('');
  const needsReason = pending === 'REJECTED' || pending === 'NEEDS_REVISION';

  const mut = useMutation({
    mutationFn: (input: { status: Decision; reviewNotes?: string }) =>
      api(`/admin/whitelist/${id}/decision`, {
        method: 'PATCH',
        body: { part, status: input.status, reviewNotes: input.reviewNotes },
      }),
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      setPending(null);
      setNotes('');
      toast.success(`${label} — ${decisionLabel(vars.status)}`, {
        description: 'Notification envoyée dans Discord et le launcher.',
      });
    },
    onError: (err) =>
      toast.error('Échec de la décision', {
        description: (err as Error).message,
      }),
  });

  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
          {label}
        </div>
        <StatusBadge status={current} />
      </div>
      <p className="mb-3 text-[11px] leading-snug text-[var(--color-foreground-subtle)]">
        {hint}
      </p>
      {pending ? (
        <div>
          <div className="mb-2 text-xs text-[var(--color-foreground-subtle)]">
            Action : <strong>{decisionLabel(pending)}</strong>
          </div>
          {needsReason && (
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder={
                pending === 'REJECTED'
                  ? 'Raison du refus (visible par le joueur)…'
                  : 'Que doit préciser le joueur ?'
              }
              rows={3}
              className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-2 text-sm focus:border-[var(--color-accent)] focus:outline-none"
            />
          )}
          <div className="mt-2 flex gap-2">
            <button
              type="button"
              disabled={mut.isPending || (needsReason && notes.trim().length === 0)}
              onClick={() =>
                mut.mutate({
                  status: pending,
                  reviewNotes: notes.trim() || undefined,
                })
              }
              className="flex-1 rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] py-2 text-sm text-white disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {mut.isPending ? 'Envoi…' : 'Confirmer'}
            </button>
            <button
              type="button"
              onClick={() => {
                setPending(null);
                setNotes('');
              }}
              className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
            >
              Annuler
            </button>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-2">
          <DecisionButton onClick={() => setPending('APPROVED')} tone="success" compact>
            Valider
          </DecisionButton>
          <DecisionButton onClick={() => setPending('NEEDS_REVISION')} tone="warning" compact>
            Réviser
          </DecisionButton>
          <DecisionButton onClick={() => setPending('REJECTED')} tone="danger" compact>
            Refuser
          </DecisionButton>
        </div>
      )}
    </div>
  );
}

// WhitelistChat retire — remplace par <ChatPanel /> (components/ChatPanel.tsx),
// composant reutilise aussi sur la page detail ticket.

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0 overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5">
      <div className="mb-4 text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
        {title}
      </div>
      <div className="space-y-3">{children}</div>
    </div>
  );
}

function Field({
  label,
  block,
  children,
}: {
  label: string;
  block?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={block ? 'min-w-0' : 'flex items-baseline gap-3 min-w-0'}>
      <div
        className={`text-xs text-[var(--color-foreground-muted)] ${
          block ? 'mb-1' : 'min-w-[110px] shrink-0'
        }`}
      >
        {label}
      </div>
      <div
        className={`text-sm min-w-0 break-words [overflow-wrap:anywhere] ${
          block ? 'whitespace-pre-wrap text-[var(--color-foreground)]' : ''
        }`}
      >
        {children}
      </div>
    </div>
  );
}

function DecisionButton({
  tone,
  onClick,
  children,
  compact = false,
}: {
  tone: 'success' | 'warning' | 'danger';
  onClick: () => void;
  children: React.ReactNode;
  compact?: boolean;
}) {
  const cls =
    tone === 'success'
      ? 'border-[var(--color-success)]/40 text-[var(--color-success)] hover:bg-[var(--color-success-soft)]'
      : tone === 'warning'
        ? 'border-[var(--color-warning)]/40 text-[var(--color-warning)] hover:bg-[var(--color-warning-soft)]'
        : 'border-[var(--color-danger)]/40 text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)]';
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full rounded-[8px] border ${compact ? 'py-1.5 text-xs' : 'py-2 text-sm'} transition-colors ${cls}`}
    >
      {children}
    </button>
  );
}

function StatusBadge({ status }: { status: AppStatus }) {
  const map: Record<AppStatus, { label: string; cls: string }> = {
    PENDING: {
      label: 'A traiter',
      cls: 'bg-[var(--color-accent-soft)] text-[var(--color-accent)] border-[var(--color-accent)]/40',
    },
    NEEDS_REVISION: {
      label: 'Revision demandee',
      cls: 'bg-[var(--color-warning-soft)] text-[var(--color-warning)] border-[var(--color-warning)]/40',
    },
    APPROVED: {
      label: 'Approuvee',
      cls: 'bg-[var(--color-success-soft)] text-[var(--color-success)] border-[var(--color-success)]/40',
    },
    REJECTED: {
      label: 'Refusee',
      cls: 'bg-[var(--color-danger-soft)] text-[var(--color-danger)] border-[var(--color-danger)]/40',
    },
  };
  const tone = map[status];
  return (
    <span
      className={`inline-block rounded-full border px-3 py-1 text-xs ${tone.cls}`}
    >
      {tone.label}
    </span>
  );
}
