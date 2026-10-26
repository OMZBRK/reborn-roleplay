import type { ReactNode } from 'react';

/**
 * Carte du kit (PANEL_V2 §5.4) : surface, bordure, rayon 8, en-tête avec un
 * titre de 16 px et une action ou une méta à droite.
 */
export function Card({
  titre,
  meta,
  action,
  children,
  className = '',
  corps = true,
  id,
}: {
  titre?: ReactNode;
  meta?: ReactNode;
  action?: ReactNode;
  children?: ReactNode;
  className?: string;
  /** false : pas de marge intérieure (tableau, liste pleine largeur). */
  corps?: boolean;
  id?: string;
}) {
  return (
    <section className={`card ${className}`} id={id}>
      {(titre || meta || action) && (
        <div className="card-h">
          {titre ? <h2 className="card-t">{titre}</h2> : <span />}
          {action ?? (meta ? <span className="card-meta">{meta}</span> : null)}
        </div>
      )}
      {corps ? <div className="card-b">{children}</div> : children}
    </section>
  );
}

/** En-tête de section en petites capitales (seul emploi des capitales). */
export function SectionTitre({
  children,
  droite,
  id,
}: {
  children: ReactNode;
  droite?: ReactNode;
  id?: string;
}) {
  return (
    <div className="sec-h" id={id}>
      <h2 className="eyebrow m-0">{children}</h2>
      {droite && <div className="help">{droite}</div>}
    </div>
  );
}

/** Liste « libellé · valeur » (fiche joueur, panneau latéral). */
export function ListeKV({
  lignes,
  largeur = 120,
}: {
  lignes: Array<{ cle: string; label: ReactNode; valeur: ReactNode } | null | false>;
  largeur?: number;
}) {
  return (
    <dl className="kv" style={{ gridTemplateColumns: `${largeur}px minmax(0, 1fr)` }}>
      {lignes
        .filter((l): l is { cle: string; label: ReactNode; valeur: ReactNode } => !!l)
        .map((l) => (
          <div key={l.cle} className="contents">
            <dt>{l.label}</dt>
            <dd>{l.valeur}</dd>
          </div>
        ))}
    </dl>
  );
}
