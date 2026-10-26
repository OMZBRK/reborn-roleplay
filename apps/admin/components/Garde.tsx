'use client';

import type { ReactNode } from 'react';
import { useDroits, type ExigenceCapacite } from '@/lib/droits';
import { RefusAcces } from './ui/States';

/**
 * Garde d'une page : le menu ne propose déjà que ce que le grade permet ; si
 * l'on arrive quand même par l'adresse, la page dit en français qu'elle n'est
 * pas ouverte, au lieu d'un refus brut de l'API.
 */
export function Garde({
  capacites,
  message,
  children,
}: {
  /** Au moins une de ces capacités ouvre la page. */
  capacites: readonly ExigenceCapacite[];
  message?: string;
  children: ReactNode;
}) {
  const { peutUne } = useDroits();
  if (!peutUne(...capacites)) return <RefusAcces message={message} />;
  return <>{children}</>;
}
