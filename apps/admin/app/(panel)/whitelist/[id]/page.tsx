'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { use, useState } from 'react';
import { toast } from 'sonner';
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
  });

  const [pendingDecision, setPendingDecision] = useState<Decision | null>(null);
  const [notes, setNotes] = useState('');

  const decideMut = useMutation({
    mutationFn: (input: { status: Decision; reviewNotes?: string }) =>
      api(`/admin/whitelist/${id}`, { method: 'PATCH', body: input }),
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
      qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
      setPendingDecision(null);
      setNotes('');
      const labels: Record<Decision, string> = {
        APPROVED: 'Candidature acceptee',
        REJECTED: 'Candidature refusee',
        NEEDS_REVISION: 'Revision demandee au joueur',
      };
      toast.success(labels[vars.status], {
        description: 'Notification envoyee dans Discord et dans le launcher.',
      });
      router.push('/whitelist');
    },
    onError: (err) => {
      toast.error('Echec de la decision', {
        description: (err as Error).message,
      });
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

  const needsReason: Decision[] = ['REJECTED', 'NEEDS_REVISION'];
  const canDecide = (['PENDING', 'NEEDS_REVISION'] as AppStatus[]).includes(
    data.status,
  );

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
        <StatusBadge status={data.status} />
      </header>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-6">
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

          {data.messages.length > 0 && (
            <Card title="Echanges">
              <div className="space-y-3 -m-2">
                {data.messages.map((m) => (
                  <div
                    key={m.id}
                    className={`rounded-[10px] p-3 ${
                      m.authorType === 'STAFF'
                        ? 'bg-[var(--color-accent-soft)] border border-[var(--color-accent)]/30'
                        : 'bg-[var(--color-surface-elevated)] border border-[var(--color-border)]'
                    }`}
                  >
                    <div className="flex items-center gap-2 mb-1 text-xs text-[var(--color-foreground-muted)]">
                      <span
                        className={
                          m.authorType === 'STAFF'
                            ? 'text-[var(--color-accent)] font-medium'
                            : ''
                        }
                      >
                        {m.authorName ?? (m.authorType === 'STAFF' ? 'Staff' : 'Joueur')}
                      </span>
                      <span>·</span>
                      <span>
                        {new Date(m.createdAt).toLocaleString('fr-FR', {
                          dateStyle: 'short',
                          timeStyle: 'short',
                        })}
                      </span>
                    </div>
                    <div className="text-sm whitespace-pre-wrap">{m.content}</div>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>

        <aside className="space-y-6">
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
              <code className="text-xs font-mono text-[var(--color-foreground-subtle)]">
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
            {data.discordThreadId && (
              <Field label="Thread Discord">
                <code className="text-xs font-mono text-[var(--color-foreground-subtle)]">
                  {data.discordThreadId}
                </code>
              </Field>
            )}
          </Card>

          {canDecide && (
            <Card title="Decision">
              {pendingDecision ? (
                <div>
                  <div className="mb-3 text-xs text-[var(--color-foreground-subtle)]">
                    Action : <strong>{decisionLabel(pendingDecision)}</strong>
                  </div>
                  {needsReason.includes(pendingDecision) && (
                    <textarea
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder={
                        pendingDecision === 'REJECTED'
                          ? 'Raison du refus (visible par le joueur)…'
                          : 'Que doit preciser le joueur ?'
                      }
                      rows={4}
                      className="w-full rounded-[8px] border border-[var(--color-border-strong)] bg-[var(--color-background)] p-3 text-sm focus:border-[var(--color-accent)] focus:outline-none"
                    />
                  )}
                  <div className="mt-3 flex gap-2">
                    <button
                      type="button"
                      disabled={
                        decideMut.isPending ||
                        (needsReason.includes(pendingDecision) && notes.trim().length === 0)
                      }
                      onClick={() =>
                        decideMut.mutate({
                          status: pendingDecision,
                          reviewNotes: notes.trim() || undefined,
                        })
                      }
                      className="flex-1 rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] py-2 text-sm text-white disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {decideMut.isPending ? 'Envoi…' : 'Confirmer'}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setPendingDecision(null);
                        setNotes('');
                      }}
                      className="rounded-[8px] border border-[var(--color-border-strong)] px-3 py-2 text-sm text-[var(--color-foreground-subtle)] hover:bg-[var(--color-surface-elevated)]"
                    >
                      Annuler
                    </button>
                  </div>
                  {decideMut.error && (
                    <div className="mt-2 text-xs text-[var(--color-danger)]">
                      {(decideMut.error as Error).message}
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-2">
                  <DecisionButton
                    onClick={() => setPendingDecision('APPROVED')}
                    tone="success"
                  >
                    Accepter
                  </DecisionButton>
                  <DecisionButton
                    onClick={() => setPendingDecision('NEEDS_REVISION')}
                    tone="warning"
                  >
                    Demander une revision
                  </DecisionButton>
                  <DecisionButton
                    onClick={() => setPendingDecision('REJECTED')}
                    tone="danger"
                  >
                    Refuser
                  </DecisionButton>
                </div>
              )}
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}

function decisionLabel(d: Decision): string {
  return d === 'APPROVED' ? 'Accepter' : d === 'REJECTED' ? 'Refuser' : 'Demander une revision';
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-5">
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
    <div className={block ? '' : 'flex items-baseline gap-3'}>
      <div
        className={`text-xs text-[var(--color-foreground-muted)] ${
          block ? 'mb-1' : 'min-w-[110px]'
        }`}
      >
        {label}
      </div>
      <div
        className={`text-sm ${
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
}: {
  tone: 'success' | 'warning' | 'danger';
  onClick: () => void;
  children: React.ReactNode;
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
      className={`w-full rounded-[8px] border py-2 text-sm transition-colors ${cls}`}
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
