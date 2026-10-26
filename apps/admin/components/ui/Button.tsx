'use client';

import Link from 'next/link';
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Icone, type NomIcone } from './Icone';
import { Kbd } from './Kbd';

/**
 * Bouton du kit (PANEL_V2 §5.4). Une seule action principale en or par
 * écran (`primary`) ; le reste en bordure (`secondary`) ou en mot (`ghost`) ;
 * `danger` = texte rouge. Le fond rouge plein (`danger-solid`) ne sert que
 * dans une fenêtre de confirmation.
 */
export type VarianteBouton =
  | 'primary'
  | 'secondary'
  | 'ghost'
  | 'danger'
  | 'danger-solid'
  | 'danger-outline';

const CLASSE_VARIANTE: Record<VarianteBouton, string> = {
  primary: 'btn-primary',
  secondary: '',
  ghost: 'btn-ghost',
  danger: 'btn-danger',
  'danger-solid': 'btn-danger-solid',
  'danger-outline': 'btn-danger-outline',
};

export interface BoutonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: VarianteBouton;
  taille?: 'sm' | 'md';
  /** Action en cours : roue + bouton désactivé. */
  enCours?: boolean;
  /** Touche affichée dans le bouton (« V », « Entrée »). */
  raccourci?: string;
  icone?: NomIcone;
  bloc?: boolean;
}

export function classesBouton({
  variante = 'secondary',
  taille = 'md',
  bloc = false,
  className = '',
}: {
  variante?: VarianteBouton;
  taille?: 'sm' | 'md';
  bloc?: boolean;
  className?: string;
}): string {
  return [
    'btn',
    CLASSE_VARIANTE[variante],
    taille === 'sm' ? 'btn-sm' : '',
    bloc ? 'btn-block' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');
}

export const Button = forwardRef<HTMLButtonElement, BoutonProps>(function Button(
  {
    variante = 'secondary',
    taille = 'md',
    enCours = false,
    raccourci,
    icone,
    bloc = false,
    className,
    children,
    disabled,
    type = 'button',
    ...reste
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || enCours}
      aria-busy={enCours || undefined}
      className={classesBouton({ variante, taille, bloc, className })}
      {...reste}
    >
      {enCours ? (
        <span className="spin" aria-hidden />
      ) : icone ? (
        <Icone nom={icone} taille={taille === 'sm' ? 15 : 16} />
      ) : null}
      {children}
      {raccourci && !enCours && <Kbd>{raccourci}</Kbd>}
    </button>
  );
});

/** Lien qui a l'allure d'un bouton (navigation, jamais une action). */
export function ButtonLink({
  href,
  variante = 'secondary',
  taille = 'md',
  icone,
  bloc,
  className,
  children,
  externe = false,
}: {
  href: string;
  variante?: VarianteBouton;
  taille?: 'sm' | 'md';
  icone?: NomIcone;
  bloc?: boolean;
  className?: string;
  children: ReactNode;
  externe?: boolean;
}) {
  const cls = classesBouton({ variante, taille, bloc, className });
  const contenu = (
    <>
      {icone && <Icone nom={icone} taille={taille === 'sm' ? 15 : 16} />}
      {children}
    </>
  );
  if (externe) {
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" className={cls}>
        {contenu}
      </a>
    );
  }
  return (
    <Link href={href} className={cls}>
      {contenu}
    </Link>
  );
}

/** Un mot cliquable dans une ligne de tableau ou un panneau (« Prendre »). */
export const Mot = forwardRef<
  HTMLButtonElement,
  ButtonHTMLAttributes<HTMLButtonElement> & {
    ton?: 'or' | 'neutre' | 'danger';
    enCours?: boolean;
  }
>(function Mot({ ton = 'or', enCours, className = '', children, disabled, type = 'button', ...reste }, ref) {
  const cls = ton === 'neutre' ? 'mot mot-neutre' : ton === 'danger' ? 'mot mot-danger' : 'mot';
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || enCours}
      aria-busy={enCours || undefined}
      className={`${cls} ${className}`}
      {...reste}
    >
      {children}
    </button>
  );
});

/** Bouton icône seule : toujours un libellé accessible. */
export function BoutonIcone({
  icone,
  label,
  onClick,
  variante = 'ghost',
  disabled,
  className = '',
  taille = 16,
}: {
  icone: NomIcone;
  label: string;
  onClick?: () => void;
  variante?: 'ghost' | 'secondary';
  disabled?: boolean;
  className?: string;
  taille?: number;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      className={`btn btn-icon ${variante === 'ghost' ? 'btn-ghost' : ''} ${className}`}
    >
      <Icone nom={icone} taille={taille} />
    </button>
  );
}
