'use client';

import type { ReactNode } from 'react';
import type { Ton } from '@/lib/candidature';
import type { TonBadge } from '@/lib/statuts';
import { Badge } from '@/components/ui/Badge';

/**
 * Briques d'affichage de la fiche candidature et des séances de douane,
 * passées au kit v2 (PANEL_V2 §5.4, lot L1) : elles réexposent les
 * composants de components/ui/ sous leurs anciens noms pour ne rien casser.
 */

/** Ancien ton (lib/candidature.ts) → ton de pastille v2. L'or n'est jamais un statut. */
const TON_V2: Record<Ton, TonBadge> = {
  accent: 'info',
  warning: 'attention',
  success: 'succes',
  danger: 'danger',
  muted: 'neutre',
};

/** Pastille « point + mot » (teintée si `teinte`). */
export function Pastille({
  ton,
  children,
  title,
  teinte = false,
}: {
  ton: Ton;
  children: ReactNode;
  /** Ancienne taille réduite : sans effet, la pastille v2 a une seule taille. */
  petite?: boolean;
  title?: string;
  teinte?: boolean;
}) {
  return (
    <Badge ton={TON_V2[ton]} teinte={teinte} title={title}>
      {children}
    </Badge>
  );
}

export function Carte({
  titre,
  action,
  children,
  className = '',
  id,
}: {
  titre: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
  /** Ancien liseré « privé » : la mention suffit désormais. */
  ton?: 'prive';
  id?: string;
}) {
  return (
    <section className={`card ${className}`} id={id}>
      <div className="card-h">
        <h2 className="card-t">{titre}</h2>
        {action}
      </div>
      <div className="card-b flex flex-col gap-3">{children}</div>
    </section>
  );
}

export function Champ({
  label,
  bloc,
  aide,
  children,
}: {
  label: string;
  bloc?: boolean;
  /** Petite précision à droite du libellé (bornes, compteur…). */
  aide?: ReactNode;
  children: ReactNode;
}) {
  if (bloc) {
    return (
      <div className="min-w-0">
        <div className="mb-1 flex items-baseline justify-between gap-3 text-[13px] font-semibold text-fg-2">
          <span>{label}</span>
          {aide && <span className="help tabular font-normal">{aide}</span>}
        </div>
        <div className="min-w-0 whitespace-pre-wrap text-[14px] leading-relaxed text-fg-2 [overflow-wrap:anywhere]">
          {children}
        </div>
      </div>
    );
  }
  return (
    <div className="flex min-w-0 items-baseline gap-3 text-[13px]">
      <div className="w-[120px] shrink-0 text-fg-3">{label}</div>
      <div className="min-w-0 text-fg-2 [overflow-wrap:anywhere]">
        {children}
        {aide && <span className="ml-2 text-[12px] text-fg-3">{aide}</span>}
      </div>
    </div>
  );
}

/** Mention « Privé · jamais montré au joueur ». */
export function MentionPrivee({ children }: { children?: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 text-[12px] text-fg-3">
      <span aria-hidden>·</span>
      {children ?? 'Privé'}
    </span>
  );
}

/** Mention « Visible du joueur ». */
export function MentionVisible() {
  return <span className="text-[12px] text-fg-3">Visible du joueur</span>;
}

export const CLASSE_SAISIE = 'input';

export const CLASSE_BOUTON_PRINCIPAL = 'btn btn-primary';

export const CLASSE_BOUTON_SECONDAIRE = 'btn';

/** Petit bouton d'action dans une ligne (présence, absence…). */
export function BoutonLigne({
  ton = 'neutre',
  onClick,
  disabled,
  children,
  title,
}: {
  ton?: 'neutre' | 'success' | 'warning' | 'danger';
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
  title?: string;
}) {
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={`btn btn-sm ${ton === 'danger' ? 'btn-danger-outline' : ''}`}
    >
      {children}
    </button>
  );
}

/** Choix en trois mots (Valider · À revoir · Refuser), comme au Poste de garde. */
export function ChoixDecision<T extends string>({
  options,
  valeur,
  onChange,
}: {
  options: Array<{ valeur: T; label: string; ton: 'success' | 'warning' | 'danger' }>;
  valeur: T | null;
  onChange: (v: T) => void;
}) {
  return (
    <div className="flex flex-wrap gap-4 text-[13px]" role="group">
      {options.map((o) => {
        const actif = valeur === o.valeur;
        return (
          <button
            key={o.valeur}
            type="button"
            aria-pressed={actif}
            onClick={() => onChange(o.valeur)}
            className={`mot ${o.ton === 'danger' ? 'mot-danger' : ''} ${
              actif ? 'underline underline-offset-4' : ''
            }`}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
