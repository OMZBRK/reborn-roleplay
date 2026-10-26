import type { ReactNode } from 'react';
import { Icone, type NomIcone } from './Icone';

/**
 * Tuile de chiffre (PANEL_V2 §8.3) : libellé portant sa fenêtre (« · 7 jours »),
 * chiffre en Bebas 40, détail dessous. Sans donnée collectée, la tuile dit
 * d'où viendra le chiffre (`aBrancher`), jamais un zéro inventé.
 */
export function StatTile({
  label,
  icone,
  valeur,
  unite,
  detail,
  aBrancher,
}: {
  label: ReactNode;
  icone?: NomIcone;
  valeur?: ReactNode;
  unite?: ReactNode;
  detail?: ReactNode;
  /** Explique pourquoi le chiffre n'est pas encore là. */
  aBrancher?: ReactNode;
}) {
  const vide = aBrancher !== undefined;
  return (
    <div className={`card tile ${vide ? 'vide' : ''}`}>
      <div className="tile-l">
        {icone && <Icone nom={icone} taille={16} />}
        {label}
      </div>
      {vide ? (
        <>
          <div className="tile-v">À brancher</div>
          <div className="tile-d">{aBrancher}</div>
        </>
      ) : (
        <>
          <div className="tile-v">
            {valeur}
            {unite && <small>{unite}</small>}
          </div>
          <div className="tile-d">{detail}</div>
        </>
      )}
    </div>
  );
}
