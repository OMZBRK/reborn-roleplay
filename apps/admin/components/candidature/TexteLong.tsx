'use client';

import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { compterMots, nombre } from '@/lib/candidature';

/**
 * Éléments rendus pour l'histoire : ceux de la barre de mise en forme du
 * launcher (Gras · Italique · Barré · Titre · Liste · Citation). Liens et
 * images sont réduits à leur texte : un texte de candidat ne charge jamais
 * une ressource externe dans le navigateur du staff.
 */
const ELEMENTS_AUTORISES = [
  'p',
  'br',
  'strong',
  'em',
  'del',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'ul',
  'ol',
  'li',
  'blockquote',
  'hr',
  'code',
];

/**
 * Texte long d'une candidature, replié au-delà de quelques lignes, avec le
 * compteur du launcher (caractères comptés comme l'API, mots indicatifs).
 */
export function TexteLong({
  texte,
  max,
  markdown = false,
  lignesRepliees = 8,
}: {
  texte: string;
  /** Maximum de la règle (affiché « 1 840 / 12 000 »). */
  max?: number;
  markdown?: boolean;
  lignesRepliees?: number;
}) {
  const [ouvert, setOuvert] = useState(false);
  const long = texte.length > 600 || texte.split('\n').length > lignesRepliees;

  return (
    <div className="min-w-0">
      <div
        className={`relative ${!ouvert && long ? 'overflow-hidden' : ''}`}
        style={
          !ouvert && long
            ? { maxHeight: `${lignesRepliees * 1.6}em` }
            : undefined
        }
      >
        {markdown ? (
          <div className="candidature-prose">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              allowedElements={ELEMENTS_AUTORISES}
              unwrapDisallowed
              skipHtml
            >
              {texte}
            </ReactMarkdown>
          </div>
        ) : (
          <div className="candidature-prose whitespace-pre-wrap">
            {texte}
          </div>
        )}
        {!ouvert && long && (
          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-[var(--color-surface)] to-transparent" />
        )}
      </div>
      <div className="mt-1.5 flex max-w-[72ch] items-center justify-between gap-3 text-[12px] text-fg-3">
        <span className="tabular-nums">
          {nombre(compterMots(texte))} mots · {nombre(texte.length)}
          {max ? ` / ${nombre(max)}` : ''} caractères
        </span>
        {long && (
          <button
            type="button"
            onClick={() => setOuvert((v) => !v)}
            aria-expanded={ouvert}
            className="mot"
          >
            {ouvert ? 'Replier' : 'Tout lire'}
          </button>
        )}
      </div>
    </div>
  );
}
