import type { ReactNode } from 'react';
import type { Statut, TonBadge } from '@/lib/statuts';

/**
 * Pastille (PANEL_V2 §5.4) : par défaut « point + mot », sans fond. Teintée
 * seulement pour une information par ligne, quand le staff doit agir (info)
 * ou que c'est tranché (succès, danger). Pas d'or : l'or est l'interaction.
 * Le mot porte toujours l'information, la couleur ne fait que la doubler.
 */
export function Badge({
  ton = 'neutre',
  teinte = false,
  children,
  title,
}: {
  ton?: TonBadge;
  teinte?: boolean;
  children: ReactNode;
  title?: string;
}) {
  if (teinte) {
    // L'ambre n'est jamais un fond : il retombe sur neutre.
    const t = ton === 'attention' || ton === 'vide' ? 'neutre' : ton;
    return (
      <span className="pill" data-ton={t} title={title}>
        {children}
      </span>
    );
  }
  return (
    <span className="badge" data-ton={ton} title={title}>
      <span className="badge-dot" aria-hidden />
      {children}
    </span>
  );
}

/** Pastille lue dans le dictionnaire des statuts (lib/statuts.ts). */
export function BadgeStatut({ statut, title }: { statut: Statut; title?: string }) {
  return (
    <Badge ton={statut.ton} teinte={statut.teinte} title={title}>
      {statut.label}
    </Badge>
  );
}

/** Étiquette discrète en pointillés (« Ancien formulaire », « à brancher »). */
export function Tag({ children, title }: { children: ReactNode; title?: string }) {
  return (
    <span className="tag" title={title}>
      {children}
    </span>
  );
}
