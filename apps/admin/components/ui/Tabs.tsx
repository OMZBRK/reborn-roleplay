'use client';

import Link from 'next/link';
import type { ReactNode } from 'react';
import { Icone, type NomIcone } from './Icone';

/**
 * Onglets soulignés d'or, avec compteur (PANEL_V2 §5.4). Un onglet est un
 * lien vers une adresse (navigation normale) ou, à l'intérieur d'une page,
 * un bouton.
 */
export interface Onglet {
  cle: string;
  label: ReactNode;
  href?: string;
  onClick?: () => void;
  actif: boolean;
  n?: number | null;
  icone?: NomIcone;
}

export function Tabs({ onglets, label }: { onglets: Onglet[]; label: string }) {
  return (
    <nav className="tabs" aria-label={label}>
      {onglets.map((o) => {
        const contenu = (
          <>
            {o.icone && <Icone nom={o.icone} taille={16} />}
            {o.label}
            {o.n != null && <span className="tab-n">{o.n}</span>}
          </>
        );
        return o.href ? (
          <Link
            key={o.cle}
            href={o.href}
            className="tab"
            aria-current={o.actif ? 'page' : undefined}
          >
            {contenu}
          </Link>
        ) : (
          <button
            key={o.cle}
            type="button"
            className="tab"
            aria-selected={o.actif}
            onClick={o.onClick}
          >
            {contenu}
          </button>
        );
      })}
    </nav>
  );
}

/** Segments pour 2 à 6 choix exclusifs (filtres d'une liste). */
export function Segmented<T extends string>({
  options,
  valeur,
  onChange,
  label,
  className = '',
}: {
  options: Array<{ valeur: T; label: ReactNode; n?: number | null }>;
  valeur: T;
  onChange: (v: T) => void;
  label: string;
  className?: string;
}) {
  return (
    <div className={`seg ${className}`} role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.valeur}
          type="button"
          className="seg-btn"
          aria-pressed={o.valeur === valeur}
          onClick={() => onChange(o.valeur)}
        >
          {o.label}
          {o.n != null && <span className="seg-n">{o.n}</span>}
        </button>
      ))}
    </div>
  );
}

/** Puce de filtre (bascule). */
export function Chip({
  actif,
  onClick,
  children,
  n,
  icone,
}: {
  actif: boolean;
  onClick: () => void;
  children: ReactNode;
  n?: number | null;
  icone?: NomIcone;
}) {
  return (
    <button type="button" className="chip" aria-pressed={actif} onClick={onClick}>
      {icone && <Icone nom={icone} taille={15} />}
      {children}
      {n != null && <span className="chip-n">{n}</span>}
    </button>
  );
}
