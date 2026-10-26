'use client';

import { usePathname, useSearchParams } from 'next/navigation';
import { useDroits } from '@/lib/droits';
import {
  ONGLETS_REGLAGES,
  SECTIONS,
  ongletActif,
  ongletsPermis,
  type SectionNav,
} from '@/lib/navigation';
import { Tabs } from '@/components/ui/Tabs';

/**
 * Onglets d'une section (Candidatures : Dossiers · Séances de douane ;
 * Modération : Tickets · Sécurité · Journal ; Contenu : Wiki · Idées ·
 * Annonces · Fichiers · Techniques). Liens vers les adresses existantes,
 * filtrés par capacités. Rien n'est affiché s'il n'y a qu'un onglet permis.
 */
export function SectionTabs({
  section,
  compteurs,
}: {
  section: SectionNav['cle'];
  compteurs?: Partial<Record<string, number | null>>;
}) {
  const chemin = usePathname() ?? '';
  const { capacites } = useDroits();
  const s = SECTIONS.find((x) => x.cle === section);
  if (!s) return null;
  const permis = ongletsPermis(s.onglets, capacites);
  if (permis.length < 2) return null;
  const actif = ongletActif(permis, chemin);
  return (
    <Tabs
      label={`Onglets de ${s.label}`}
      onglets={permis.map((o) => ({
        cle: o.cle,
        label: o.label,
        href: o.href,
        icone: o.icone,
        actif: actif?.cle === o.cle,
        n: compteurs?.[o.cle] ?? null,
      }))}
    />
  );
}

/** Onglets de Réglages (paramètre `onglet` de l'adresse). */
export function ReglagesTabs() {
  const params = useSearchParams();
  const { capacites } = useDroits();
  const courant = params.get('onglet') ?? 'compte';
  const permis = ongletsPermis(ONGLETS_REGLAGES, capacites);
  return (
    <Tabs
      label="Onglets des réglages"
      onglets={permis.map((o) => ({
        cle: o.cle,
        label: o.label,
        href: o.href,
        actif: o.cle === courant,
      }))}
    />
  );
}
