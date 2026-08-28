'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';
import { IconShield, IconPlayers, IconChat } from '@/components/icons';
import { api } from '@/lib/api';
import type { CurrentUser, Role } from '@/lib/types';

interface TwoFAStatus {
  enabled: boolean;
}

interface EnrollResponse {
  secret: string;
  otpauthUri: string;
  qrDataUrl: string;
}

const ROLE_LABELS: Record<Role, string> = {
  PLAYER: 'Joueur',
  WHITELISTED: 'Whitelisté',
  HELPER: 'Helper',
  MODELISATEUR: 'Modélisateur',
  DEVELOPPEUR: 'Développeur',
  WHITELIST_REVIEWER: 'Reviewer WL',
  MODERATOR: 'Modérateur',
  ADMIN: 'Administrateur',
  OWNER: 'Fondateur',
};

export default function SettingsPage() {
  return (
    <div className="px-10 py-10 max-w-4xl mx-auto">
      <header className="mb-8">
        <div className="text-xs uppercase tracking-[0.32em] text-[var(--color-foreground-muted)]">
          Mon compte
        </div>
        <h1
          className="mt-1 text-5xl leading-none bg-gradient-to-r from-white to-white/40 bg-clip-text text-transparent"
          style={{ fontFamily: 'var(--font-display)' }}
        >
          Paramètres
        </h1>
        <p className="mt-3 text-sm text-[var(--color-foreground-subtle)]">
          Gère ton profil staff et la sécurité de ton accès au panel.
        </p>
        <div className="mt-4 h-[2px] w-24 bg-gradient-to-r from-[var(--color-accent)] to-transparent shadow-[var(--shadow-glow-accent)]" />
      </header>

      <div className="space-y-6">
        <ProfileSection />
        <TwoFASection />
      </div>
    </div>
  );
}

function ProfileSection() {
  const { data: me, isLoading } = useQuery({
    queryKey: ['admin', 'me'],
    queryFn: () => api<CurrentUser>('/auth/me'),
    staleTime: 5 * 60_000,
  });

  return (
    <section className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-6">
      <div className="mb-5 flex items-center gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
          <IconPlayers />
        </div>
        <div>
          <div className="text-sm font-medium">Profil staff</div>
          <div className="text-xs text-[var(--color-foreground-muted)]">
            Informations de ton compte (lecture seule).
          </div>
        </div>
      </div>

      {isLoading || !me ? (
        <div className="text-sm text-[var(--color-foreground-subtle)]">
          Chargement…
        </div>
      ) : (
        <div className="flex flex-col gap-6 sm:flex-row sm:items-center">
          {/* Avatar */}
          <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-[14px] border border-[var(--color-border-strong)] bg-[var(--color-surface-elevated)] text-xl font-medium uppercase text-[var(--color-foreground-subtle)]">
            {me.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={me.avatarUrl}
                alt={me.minecraftUsername}
                width={64}
                height={64}
                className="h-full w-full object-cover"
              />
            ) : (
              (me.displayName ?? me.minecraftUsername).slice(0, 2)
            )}
          </div>

          {/* Champs profil */}
          <div className="grid flex-1 grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Pseudo Minecraft" value={me.minecraftUsername} mono />
            <Field
              label="Nom affiché"
              value={me.displayName ?? '—'}
            />
            <div>
              <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                Rôle
              </div>
              <div className="mt-1.5">
                <span className="inline-block rounded-full border border-[var(--color-accent)]/40 bg-[var(--color-accent-soft)] px-2.5 py-0.5 text-xs text-[var(--color-accent)]">
                  {ROLE_LABELS[me.role]}
                </span>
              </div>
            </div>
            <div>
              <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
                Discord
              </div>
              <div className="mt-1.5 flex items-center gap-2 text-sm">
                <IconChat className="text-[var(--color-foreground-muted)]" />
                {me.discord ? (
                  <span className="text-[var(--color-foreground-subtle)]">
                    @{me.discord.username}
                  </span>
                ) : (
                  <span className="text-[var(--color-foreground-muted)]">
                    non lié
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function Field({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-[var(--color-foreground-muted)]">
        {label}
      </div>
      <div
        className={`mt-1.5 text-sm text-[var(--color-foreground)] ${
          mono ? 'font-mono' : ''
        }`}
      >
        {value}
      </div>
    </div>
  );
}

function TwoFASection() {
  const qc = useQueryClient();
  const { data: status, isLoading } = useQuery({
    queryKey: ['admin', '2fa', 'status'],
    queryFn: () => api<TwoFAStatus>('/auth/2fa/status'),
  });

  const [enrolling, setEnrolling] = useState<EnrollResponse | null>(null);
  const [confirmCode, setConfirmCode] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [showDisable, setShowDisable] = useState(false);

  const enrollMut = useMutation({
    mutationFn: () => api<EnrollResponse>('/auth/2fa/enroll', { method: 'POST' }),
    onSuccess: (data) => {
      setEnrolling(data);
      setConfirmCode('');
    },
    onError: (err) => toast.error((err as Error).message),
  });

  const confirmMut = useMutation({
    mutationFn: (code: string) =>
      api('/auth/2fa/confirm', { method: 'POST', body: { code } }),
    onSuccess: () => {
      toast.success('2FA activé', {
        description: 'Tu devras saisir un code TOTP à chaque login.',
      });
      setEnrolling(null);
      setConfirmCode('');
      qc.invalidateQueries({ queryKey: ['admin', '2fa', 'status'] });
    },
    onError: (err) => toast.error((err as Error).message),
  });

  const disableMut = useMutation({
    mutationFn: (code: string) =>
      api('/auth/2fa/disable', { method: 'POST', body: { code } }),
    onSuccess: () => {
      toast.success('2FA désactivé');
      setShowDisable(false);
      setDisableCode('');
      qc.invalidateQueries({ queryKey: ['admin', '2fa', 'status'] });
    },
    onError: (err) => toast.error((err as Error).message),
  });

  return (
    <section className="rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)] p-6">
      <div className="mb-5 flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-accent-soft)] text-[var(--color-accent)]">
            <IconShield />
          </div>
          <div>
            <div className="text-sm font-medium">
              Authentification à deux facteurs (2FA)
            </div>
            <div className="text-xs text-[var(--color-foreground-muted)]">
              Demande un code TOTP en plus de Discord à chaque login.
            </div>
          </div>
        </div>
        {!isLoading && (
          <span
            className={`shrink-0 rounded-full border px-2.5 py-0.5 text-xs ${
              status?.enabled
                ? 'border-[var(--color-success)]/40 bg-[var(--color-success-soft)] text-[var(--color-success)]'
                : 'border-[var(--color-border-strong)] text-[var(--color-foreground-muted)]'
            }`}
          >
            {status?.enabled ? 'Actif' : 'Inactif'}
          </span>
        )}
      </div>

      {isLoading ? (
        <div className="text-sm text-[var(--color-foreground-subtle)]">
          Chargement…
        </div>
      ) : status?.enabled ? (
        <div className="space-y-3">
          <div className="rounded-[10px] border border-[var(--color-success)]/40 bg-[var(--color-success-soft)] px-3 py-2.5 text-sm text-[var(--color-success)]">
            ✓ 2FA actif sur ton compte.
          </div>
          {showDisable ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (disableCode.length === 6) disableMut.mutate(disableCode);
              }}
              className="rounded-[10px] border border-[var(--color-danger)]/40 bg-[var(--color-danger-soft)]/30 p-4"
            >
              <div className="text-xs text-[var(--color-foreground-subtle)] mb-2">
                Saisis ton code actuel pour confirmer la désactivation :
              </div>
              <input
                type="text"
                inputMode="numeric"
                value={disableCode}
                onChange={(e) =>
                  setDisableCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                }
                placeholder="000000"
                className="w-full rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] py-2 px-3 text-center text-lg font-mono tracking-[0.4em] focus:border-[var(--color-danger)] focus:outline-none"
              />
              <div className="mt-3 flex gap-2">
                <button
                  type="submit"
                  disabled={disableCode.length < 6 || disableMut.isPending}
                  className="flex-1 rounded-[8px] bg-[var(--color-danger)] py-2 text-sm text-white disabled:opacity-40"
                >
                  {disableMut.isPending ? 'Désactivation…' : 'Désactiver'}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowDisable(false);
                    setDisableCode('');
                  }}
                  className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm hover:bg-[var(--color-surface-elevated)] transition-colors"
                >
                  Annuler
                </button>
              </div>
            </form>
          ) : (
            <button
              type="button"
              onClick={() => setShowDisable(true)}
              className="rounded-[8px] border border-[var(--color-danger)]/40 px-4 py-2 text-sm text-[var(--color-danger)] hover:bg-[var(--color-danger-soft)] transition-colors"
            >
              Désactiver le 2FA
            </button>
          )}
        </div>
      ) : enrolling ? (
        <div className="space-y-4">
          <div className="text-sm text-[var(--color-foreground-subtle)]">
            Scanne ce QR avec Google Authenticator, Authy, 1Password, etc. Puis
            saisis le code à 6 chiffres affiché.
          </div>
          <div className="flex flex-col items-start gap-4 sm:flex-row">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={enrolling.qrDataUrl}
              alt="QR code 2FA"
              width={180}
              height={180}
              className="rounded-[10px] border border-[var(--color-border-strong)] bg-white"
            />
            <div className="flex-1">
              <div className="text-xs text-[var(--color-foreground-muted)]">
                Secret manuel (si tu ne peux pas scanner) :
              </div>
              <code className="mt-1 block break-all rounded-[6px] bg-[var(--color-surface-elevated)] px-2 py-1 text-[11px] font-mono">
                {enrolling.secret}
              </code>
            </div>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (confirmCode.length === 6) confirmMut.mutate(confirmCode);
            }}
          >
            <input
              type="text"
              inputMode="numeric"
              autoFocus
              value={confirmCode}
              onChange={(e) =>
                setConfirmCode(e.target.value.replace(/\D/g, '').slice(0, 6))
              }
              placeholder="000000"
              className="w-full rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] py-2.5 px-3 text-center text-xl font-mono tracking-[0.5em] focus:border-[var(--color-accent)] focus:outline-none"
            />
            <div className="mt-3 flex gap-2">
              <button
                type="submit"
                disabled={confirmCode.length < 6 || confirmMut.isPending}
                className="flex-1 rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] py-2 text-sm text-white disabled:opacity-40 shadow-[var(--shadow-glow-accent)]"
              >
                {confirmMut.isPending ? 'Activation…' : 'Activer le 2FA'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setEnrolling(null);
                  setConfirmCode('');
                }}
                className="rounded-[8px] border border-[var(--color-border-strong)] px-4 py-2 text-sm hover:bg-[var(--color-surface-elevated)] transition-colors"
              >
                Annuler
              </button>
            </div>
          </form>
        </div>
      ) : (
        <div>
          <p className="text-sm text-[var(--color-foreground-subtle)] mb-4">
            Recommandé pour les comptes admin/owner. Quand actif, après le login
            Discord il te sera demandé un code à 6 chiffres avant d&apos;accéder
            au panel.
          </p>
          <button
            type="button"
            onClick={() => enrollMut.mutate()}
            disabled={enrollMut.isPending}
            className="rounded-[8px] bg-[var(--color-accent)] hover:bg-[var(--color-accent-hover)] px-4 py-2 text-sm text-white disabled:opacity-40 shadow-[var(--shadow-glow-accent)]"
          >
            {enrollMut.isPending ? 'Génération…' : 'Activer le 2FA'}
          </button>
        </div>
      )}
    </section>
  );
}
