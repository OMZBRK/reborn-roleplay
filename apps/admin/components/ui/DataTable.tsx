'use client';

import { useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { usePreferences } from '@/lib/preferences';
import { ErrorState } from './States';
import { Icone } from './Icone';

/**
 * Tableau dense du kit (PANEL_V2 §5.4) : en-têtes collants, lignes de 44 px
 * (36 en densité compacte) cliquables, colonnes déclarées, chargement en
 * squelette et état vide intégrés, navigation au clavier (↓ ↑ pour changer
 * de ligne, Entrée pour ouvrir). Sous 768 px, les lignes passent sur deux
 * lignes (`empilable`).
 */
export interface Colonne<T> {
  cle: string;
  titre: ReactNode;
  rendu: (ligne: T) => ReactNode;
  align?: 'droite';
  /** Texte principal (ivoire, gras moyen). */
  principal?: boolean;
  /** Pas de retour à la ligne. */
  serre?: boolean;
  /** Masquée sur téléphone. */
  cacherEtroit?: boolean;
  largeur?: string;
}

export interface GroupeLignes<T> {
  cle: string;
  titre: ReactNode;
  n?: number;
  lignes: T[];
  /** Groupe replié par défaut (« Pris par d'autres »). */
  replie?: boolean;
  /** Résumé affiché quand le groupe est replié. */
  resume?: ReactNode;
}

export function DataTable<T>({
  colonnes,
  lignes,
  groupes,
  cle,
  onOuvrir,
  chargement = false,
  erreur,
  onReessayer,
  vide,
  pied,
  label,
  ligneActive,
  estAttenue,
  onToucheLigne,
}: {
  colonnes: Colonne<T>[];
  lignes?: T[];
  groupes?: GroupeLignes<T>[];
  cle: (l: T) => string;
  onOuvrir?: (l: T) => void;
  chargement?: boolean;
  erreur?: Error | null;
  onReessayer?: () => void;
  vide?: ReactNode;
  pied?: ReactNode;
  /** Légende lue par les lecteurs d'écran. */
  label: string;
  /** Clé de la ligne mise en avant (sélection clavier pilotée par la page). */
  ligneActive?: string | null;
  estAttenue?: (l: T) => boolean;
  /** Touche pressée sur une ligne focalisée (raccourcis propres à la page). */
  onToucheLigne?: (e: KeyboardEvent<HTMLTableRowElement>, l: T) => void;
}) {
  const [prefs] = usePreferences();
  const corps = useRef<HTMLTableSectionElement>(null);
  const [ouverts, setOuverts] = useState<Record<string, boolean>>({});

  const toutes: Array<{ groupe?: GroupeLignes<T>; lignes: T[] }> = groupes
    ? groupes.map((g) => ({ groupe: g, lignes: g.lignes }))
    : [{ lignes: lignes ?? [] }];
  const total = toutes.reduce((n, g) => n + g.lignes.length, 0);

  function deplacer(depuis: HTMLElement, sens: 1 | -1) {
    const rangs = Array.from(
      corps.current?.querySelectorAll<HTMLTableRowElement>('tr[data-ligne]') ?? [],
    );
    const i = rangs.indexOf(depuis as HTMLTableRowElement);
    const cible = rangs[i + sens];
    if (cible) cible.focus();
  }

  function surTouche(e: KeyboardEvent<HTMLTableRowElement>, l: T) {
    if (e.target !== e.currentTarget) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      deplacer(e.currentTarget, 1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      deplacer(e.currentTarget, -1);
    } else if (e.key === 'Enter' && onOuvrir) {
      e.preventDefault();
      onOuvrir(l);
    } else {
      onToucheLigne?.(e, l);
    }
  }

  function surClic(e: React.MouseEvent<HTMLTableRowElement>, l: T) {
    // Un clic sur un lien ou un bouton de la ligne garde son propre effet.
    const t = e.target as HTMLElement;
    if (t.closest('a, button, input, select, textarea, label')) return;
    onOuvrir?.(l);
  }

  if (erreur && total === 0) {
    return <ErrorState erreur={erreur} onReessayer={onReessayer} />;
  }

  return (
    <div className="dt-wrap">
      <table className={`dt empilable ${prefs.densite === 'compacte' ? 'compacte' : ''}`}>
        <caption className="sr-only">{label}</caption>
        <thead>
          <tr>
            {colonnes.map((c) => (
              <th
                key={c.cle}
                scope="col"
                className={`${c.align === 'droite' ? 'r' : ''}`}
                style={c.largeur ? { width: c.largeur } : undefined}
              >
                {c.titre}
              </th>
            ))}
          </tr>
        </thead>
        <tbody ref={corps}>
          {chargement && total === 0 ? (
            Array.from({ length: 5 }).map((_, i) => (
              <tr key={`sq-${i}`} aria-hidden>
                {colonnes.map((c, j) => (
                  <td key={c.cle} className={c.cacherEtroit ? 'cacher-etroit' : ''}>
                    <span className="sk" style={{ width: `${45 + ((i + j) % 4) * 12}%` }} />
                  </td>
                ))}
              </tr>
            ))
          ) : total === 0 && !groupes ? (
            <tr>
              <td colSpan={colonnes.length} style={{ height: "auto", padding: 16 }}>
                {vide}
              </td>
            </tr>
          ) : (
            toutes.map(({ groupe, lignes: ls }) => {
              const replie = groupe
                ? !(ouverts[groupe.cle] ?? !groupe.replie)
                : false;
              if (groupe && ls.length === 0) return null;
              return (
                <GroupeRangs
                  key={groupe?.cle ?? 'tout'}
                  groupe={groupe}
                  replie={replie}
                  onBasculer={() =>
                    groupe &&
                    setOuverts((o) => ({ ...o, [groupe.cle]: replie }))
                  }
                  nbColonnes={colonnes.length}
                >
                  {!replie &&
                    ls.map((l) => {
                      const k = cle(l);
                      return (
                        <tr
                          key={k}
                          data-ligne
                          tabIndex={onOuvrir ? 0 : -1}
                          className={`${onOuvrir ? 'cliquable' : ''} ${
                            estAttenue?.(l) ? 'dim' : ''
                          }`}
                          data-actif={ligneActive === k || undefined}
                          onClick={(e) => surClic(e, l)}
                          onKeyDown={(e) => surTouche(e, l)}
                        >
                          {colonnes.map((c) => (
                            <td
                              key={c.cle}
                              className={[
                                c.align === 'droite' ? 'r' : '',
                                c.principal ? 'm' : '',
                                c.serre ? 'nw' : '',
                                c.cacherEtroit ? 'cacher-etroit' : '',
                              ].join(' ')}
                            >
                              {c.rendu(l)}
                            </td>
                          ))}
                        </tr>
                      );
                    })}
                </GroupeRangs>
              );
            })
          )}
          {groupes && total === 0 && !chargement && (
            <tr>
              <td colSpan={colonnes.length} style={{ height: "auto", padding: 16 }}>
                {vide}
              </td>
            </tr>
          )}
        </tbody>
      </table>
      {pied && <div className="dt-foot">{pied}</div>}
    </div>
  );
}

function GroupeRangs<T>({
  groupe,
  replie,
  onBasculer,
  nbColonnes,
  children,
}: {
  groupe?: GroupeLignes<T>;
  replie: boolean;
  onBasculer: () => void;
  nbColonnes: number;
  children: ReactNode;
}) {
  if (!groupe) return <>{children}</>;
  return (
    <>
      <tr className="grp">
        <td colSpan={nbColonnes}>
          {groupe.replie !== undefined ? (
            <button
              type="button"
              onClick={onBasculer}
              aria-expanded={!replie}
              className="inline-flex items-center gap-2 uppercase tracking-[0.08em]"
            >
              <Icone
                nom={replie ? 'chevron-right' : 'chevron-down'}
                taille={14}
              />
              {groupe.titre}
              {groupe.n != null && <span className="grp-n">{groupe.n}</span>}
              {replie && groupe.resume && (
                <span className="grp-n normal-case tracking-normal">
                  · {groupe.resume}
                </span>
              )}
            </button>
          ) : (
            <>
              {groupe.titre}
              {groupe.n != null && <span className="grp-n">{groupe.n}</span>}
            </>
          )}
        </td>
      </tr>
      {children}
    </>
  );
}

/** Pagination côté API : « 1 à 20 sur 148 » et numéros de page. */
export function Pagination({
  page,
  total,
  parPage,
  onPage,
  occupe = false,
}: {
  page: number;
  total: number;
  parPage: number;
  onPage: (p: number) => void;
  occupe?: boolean;
}) {
  const pages = Math.max(1, Math.ceil(total / parPage));
  const debut = total === 0 ? 0 : page * parPage + 1;
  const fin = Math.min(total, (page + 1) * parPage);
  const numeros = numerosPages(page, pages);
  return (
    <>
      <span className="tabular">
        {debut} à {fin} sur {total}
      </span>
      {pages > 1 && (
        <nav className="pager" aria-label="Pagination">
          <button
            type="button"
            onClick={() => onPage(page - 1)}
            disabled={page === 0 || occupe}
            aria-label="Page précédente"
          >
            <Icone nom="chevron-left" taille={14} />
          </button>
          {numeros.map((n, i) =>
            n === null ? (
              <span key={`e${i}`} className="px-1" aria-hidden>
                …
              </span>
            ) : (
              <button
                key={n}
                type="button"
                onClick={() => onPage(n)}
                aria-current={n === page ? 'page' : undefined}
                disabled={occupe}
              >
                {n + 1}
              </button>
            ),
          )}
          <button
            type="button"
            onClick={() => onPage(page + 1)}
            disabled={page + 1 >= pages || occupe}
            aria-label="Page suivante"
          >
            <Icone nom="chevron-right" taille={14} />
          </button>
        </nav>
      )}
    </>
  );
}

function numerosPages(page: number, pages: number): Array<number | null> {
  if (pages <= 7) return Array.from({ length: pages }, (_, i) => i);
  const garde = new Set([0, pages - 1, page - 1, page, page + 1]);
  const out: Array<number | null> = [];
  for (let i = 0; i < pages; i++) {
    if (garde.has(i)) out.push(i);
    else if (out[out.length - 1] !== null) out.push(null);
  }
  return out;
}
