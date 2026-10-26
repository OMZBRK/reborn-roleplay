'use client';

import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Icone, type NomIcone } from './Icone';

/**
 * Menu « Plus » (⋯) : gestes rares ou à risque, rangés hors de la vue
 * (remise à zéro…). Échap ou un clic dehors le referme ; flèches pour
 * passer d'une entrée à l'autre.
 */
export function MenuPlus({
  label = 'Plus d’actions',
  entrees,
  discret = false,
}: {
  label?: string;
  /** Dans une ligne de tableau : bouton sans bordure (un mot, pas un bouton). */
  discret?: boolean;
  entrees: Array<{
    cle: string;
    label: ReactNode;
    icone?: NomIcone;
    danger?: boolean;
    onClick: () => void;
  }>;
}) {
  const [ouvert, setOuvert] = useState(false);
  const racine = useRef<HTMLDivElement>(null);
  const bouton = useRef<HTMLButtonElement>(null);
  const id = useId();

  useEffect(() => {
    if (!ouvert) return;
    const premier = racine.current?.querySelector<HTMLButtonElement>('[role="menuitem"]');
    premier?.focus();
    function dehors(e: MouseEvent) {
      if (!racine.current?.contains(e.target as Node)) setOuvert(false);
    }
    function touche(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.stopPropagation();
        setOuvert(false);
        bouton.current?.focus();
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        const items = Array.from(
          racine.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [],
        );
        const i = items.indexOf(document.activeElement as HTMLButtonElement);
        const n = e.key === 'ArrowDown' ? i + 1 : i - 1;
        items[(n + items.length) % items.length]?.focus();
      }
    }
    document.addEventListener('mousedown', dehors);
    document.addEventListener('keydown', touche);
    return () => {
      document.removeEventListener('mousedown', dehors);
      document.removeEventListener('keydown', touche);
    };
  }, [ouvert]);

  if (entrees.length === 0) return null;

  return (
    <div className="relative" ref={racine}>
      <button
        ref={bouton}
        type="button"
        className={`btn btn-icon ${discret ? 'btn-ghost' : ''}`}
        aria-label={label}
        title={label}
        aria-haspopup="menu"
        aria-expanded={ouvert}
        aria-controls={id}
        onClick={() => setOuvert((v) => !v)}
      >
        <Icone nom="ellipsis" taille={16} />
      </button>
      {ouvert && (
        <div id={id} role="menu" className="pop menu-pop">
          {entrees.map((e) => (
            <button
              key={e.cle}
              type="button"
              role="menuitem"
              className={`menu-item ${e.danger ? 'danger' : ''}`}
              onClick={() => {
                setOuvert(false);
                e.onClick();
              }}
            >
              {e.icone && <Icone nom={e.icone} taille={15} />}
              {e.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
