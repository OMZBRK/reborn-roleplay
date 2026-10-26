'use client';

import { useRef } from 'react';

/**
 * Saisie d'un code de double authentification en six cases (PANEL_V2 §4.8).
 * Collage d'un code entier accepté ; retour arrière revient à la case
 * précédente. `onComplet` part dès la sixième chiffre.
 */
export function CodeSixCases({
  valeur,
  onChange,
  onComplet,
  label = 'Code à six chiffres',
  autoFocus = false,
  desactive = false,
}: {
  valeur: string;
  onChange: (v: string) => void;
  onComplet?: (v: string) => void;
  label?: string;
  autoFocus?: boolean;
  desactive?: boolean;
}) {
  const cases = useRef<Array<HTMLInputElement | null>>([]);
  const chiffres = valeur.padEnd(6, ' ').slice(0, 6).split('');

  function poser(texte: string, depuis: number) {
    const propre = texte.replace(/\D/g, '');
    if (!propre) return;
    const base = valeur.slice(0, depuis);
    const nouveau = (base + propre).slice(0, 6);
    onChange(nouveau);
    const suivant = Math.min(5, nouveau.length);
    cases.current[suivant]?.focus();
    if (nouveau.length === 6) onComplet?.(nouveau);
  }

  return (
    <div role="group" aria-label={label} className="flex justify-center gap-2">
      {chiffres.map((c, i) => (
        <input
          key={i}
          ref={(el) => {
            cases.current[i] = el;
          }}
          type="text"
          inputMode="numeric"
          autoComplete={i === 0 ? 'one-time-code' : 'off'}
          aria-label={`Chiffre ${i + 1} sur 6`}
          maxLength={6}
          autoFocus={autoFocus && i === 0}
          disabled={desactive}
          value={c.trim()}
          onChange={(e) => poser(e.target.value, i)}
          onPaste={(e) => {
            e.preventDefault();
            poser(e.clipboardData.getData('text'), 0);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Backspace') {
              e.preventDefault();
              if (valeur.length > i) onChange(valeur.slice(0, i) + valeur.slice(i + 1));
              else if (i > 0) {
                onChange(valeur.slice(0, i - 1));
                cases.current[i - 1]?.focus();
              }
            } else if (e.key === 'ArrowLeft' && i > 0) {
              cases.current[i - 1]?.focus();
            } else if (e.key === 'ArrowRight' && i < 5) {
              cases.current[i + 1]?.focus();
            }
          }}
          className="input input-lg w-11 px-0 text-center font-mono text-[20px] tabular"
        />
      ))}
    </div>
  );
}
