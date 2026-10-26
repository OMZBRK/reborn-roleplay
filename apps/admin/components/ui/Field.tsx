'use client';

import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { Icone, type NomIcone } from './Icone';
import { Kbd } from './Kbd';

/**
 * Champ du kit (PANEL_V2 §5.4) : libellé, aide, erreur, compteur ; hauteur
 * 36 px, focus or. `Field` relie le libellé au contrôle passé en enfant par
 * une fonction (`{(id) => <Input id={id} />}`) ou par `htmlFor`.
 */
export function Field({
  label,
  aide,
  erreur,
  compteur,
  htmlFor,
  children,
  className = '',
}: {
  label: ReactNode;
  aide?: ReactNode;
  erreur?: ReactNode;
  /** Texte à droite du libellé (« 120 / 2 000 », « Visible du joueur »). */
  compteur?: ReactNode;
  htmlFor?: string;
  children: ReactNode | ((id: string) => ReactNode);
  className?: string;
}) {
  const auto = useId();
  const id = htmlFor ?? auto;
  return (
    <div className={`field ${className}`}>
      <label className="label" htmlFor={id}>
        <span>{label}</span>
        {compteur && <span className="help tabular">{compteur}</span>}
      </label>
      {typeof children === 'function' ? children(id) : children}
      {erreur ? (
        <div className="error-text" role="alert">
          {erreur}
        </div>
      ) : aide ? (
        <div className="help">{aide}</div>
      ) : null}
    </div>
  );
}

export const Input = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement> & { grand?: boolean }
>(function Input({ className = '', grand = false, ...reste }, ref) {
  return (
    <input ref={ref} className={`input ${grand ? 'input-lg' : ''} ${className}`} {...reste} />
  );
});

/** Champ de recherche avec icône et touche affichée. */
export const InputRecherche = forwardRef<
  HTMLInputElement,
  InputHTMLAttributes<HTMLInputElement> & {
    icone?: NomIcone;
    raccourci?: string;
    grand?: boolean;
    conteneur?: string;
  }
>(function InputRecherche(
  { icone = 'search', raccourci, grand = false, conteneur = '', className = '', ...reste },
  ref,
) {
  return (
    <div className={`input-icone ${conteneur}`}>
      <Icone nom={icone} taille={16} />
      <input
        ref={ref}
        type="search"
        className={`input ${grand ? 'input-lg' : ''} ${className}`}
        {...reste}
      />
      {raccourci && <Kbd>{raccourci}</Kbd>}
    </div>
  );
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className = '', children, ...reste }, ref) {
    return (
      <select ref={ref} className={`select ${className}`} {...reste}>
        {children}
      </select>
    );
  },
);

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement>
>(function Textarea({ className = '', ...reste }, ref) {
  return <textarea ref={ref} className={`textarea ${className}`} {...reste} />;
});

/** Interrupteur (préférences). */
export function Interrupteur({
  actif,
  onChange,
  label,
  disabled,
}: {
  actif: boolean;
  onChange: (v: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={actif}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!actif)}
      className="toggle"
    />
  );
}
