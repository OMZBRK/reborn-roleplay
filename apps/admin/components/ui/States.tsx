'use client';

import type { ReactNode } from 'react';
import { ApiError, MESSAGE_SERVEUR_INDISPONIBLE } from '@/lib/api';
import { Icone, type NomIcone } from './Icone';

/** État vide : une phrase et une aide (PANEL_V2 §2 principe 8). */
export function EmptyState({
  titre,
  aide,
  icone = 'inbox',
  action,
}: {
  titre: ReactNode;
  aide?: ReactNode;
  icone?: NomIcone;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="empty-i">
        <Icone nom={icone} taille={18} />
      </div>
      <div className="empty-t">{titre}</div>
      {aide && <div className="empty-d">{aide}</div>}
      {action && <div className="mt-3">{action}</div>}
    </div>
  );
}

/**
 * Message d'erreur lisible : un refus de droit dit pourquoi, en français.
 * lib/api.ts a déjà remplacé les pannes muettes (5xx) et les codes HTTP ;
 * une erreur réseau (fetch rejeté) dit « Connexion perdue ».
 */
export function messageErreur(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 0) return 'Connexion perdue.';
    return e.message || (e.status >= 500 ? MESSAGE_SERVEUR_INDISPONIBLE : 'Erreur inattendue.');
  }
  if (e instanceof TypeError) return 'Connexion perdue.';
  return e instanceof Error && e.message ? e.message : 'Erreur inattendue.';
}

/**
 * Erreur dans la page : un refus (403) s'affiche avec un cadenas et le motif
 * de l'API (« Réservé aux administrateurs. ») ; une panne propose de
 * réessayer.
 */
export function ErrorState({
  erreur,
  onReessayer,
}: {
  erreur: unknown;
  onReessayer?: () => void;
}) {
  const refus = erreur instanceof ApiError && erreur.status === 403;
  const reseau =
    erreur instanceof TypeError || (erreur instanceof ApiError && erreur.status >= 500);
  return (
    <div className="err-line" role="alert">
      <Icone nom={refus ? 'lock' : reseau ? 'wifi-off' : 'triangle-alert'} taille={16} />
      <span className="min-w-0 flex-1">{messageErreur(erreur)}</span>
      {onReessayer && !refus && (
        <button type="button" className="mot" onClick={onReessayer}>
          Réessayer
        </button>
      )}
    </div>
  );
}

/** Page refusée au grade (accès direct par l'adresse). */
export function RefusAcces({ message }: { message?: string }) {
  return (
    <div className="page-narrow">
      <div className="empty mt-10">
        <div className="empty-i">
          <Icone nom="lock" taille={18} />
        </div>
        <div className="empty-t">Cette page n’est pas ouverte à ton grade.</div>
        <div className="empty-d">
          {message ?? 'Si tu en as besoin, demande à un administrateur.'}
        </div>
      </div>
    </div>
  );
}

/** Squelette de chargement d'un bloc. */
export function Chargement({ lignes = 3 }: { lignes?: number }) {
  return (
    <div className="flex flex-col gap-3 py-2" aria-busy="true" aria-label="Chargement">
      {Array.from({ length: lignes }).map((_, i) => (
        <span key={i} className="sk" style={{ width: `${70 - i * 12}%`, height: 12 }} />
      ))}
    </div>
  );
}

/** Ligne d'information (bleue), d'attention (ambre) ou neutre. */
export function Callout({
  ton = 'info',
  icone,
  children,
}: {
  ton?: 'info' | 'attention' | 'neutre';
  icone?: NomIcone;
  children: ReactNode;
}) {
  const cls = ton === 'attention' ? 'callout warn' : ton === 'neutre' ? 'callout neutre' : 'callout';
  const ic: NomIcone = icone ?? (ton === 'attention' ? 'triangle-alert' : 'info');
  return (
    <div className={cls}>
      <Icone nom={ic} taille={16} />
      <div className="min-w-0">{children}</div>
    </div>
  );
}
