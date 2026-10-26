import Link from 'next/link';
import type { ReactNode } from 'react';
import { Icone } from './Icone';

/**
 * En-tête de page (PANEL_V2 §5.4) : fil d'Ariane, titre en Bebas 28, une
 * ligne de sous-titre facultative, actions à droite. Les onglets de la
 * section se posent dessous (`SectionTabs`).
 */
export function PageHeader({
  titre,
  sousTitre,
  fil,
  actions,
  apresTitre,
}: {
  titre: ReactNode;
  sousTitre?: ReactNode;
  fil?: Array<{ label: ReactNode; href?: string }>;
  actions?: ReactNode;
  /** Pastille ou mention à côté du titre. */
  apresTitre?: ReactNode;
}) {
  return (
    <header className="ph">
      <div className="min-w-0 flex-1">
        {fil && fil.length > 0 && (
          <nav className="crumbs" aria-label="Fil d’Ariane">
            {fil.map((f, i) => (
              <span key={i} className="inline-flex items-center gap-1.5">
                {i > 0 && <Icone nom="chevron-right" taille={12} />}
                {f.href ? <Link href={f.href}>{f.label}</Link> : <span>{f.label}</span>}
              </span>
            ))}
          </nav>
        )}
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="ph-title">{titre}</h1>
          {apresTitre}
        </div>
        {sousTitre && <div className="ph-sub">{sousTitre}</div>}
      </div>
      {actions && <div className="ph-act">{actions}</div>}
    </header>
  );
}
