/**
 * Architecture du panel v2 (PANEL_V2 §3.1) : six sections + Réglages en pied
 * de barre. Les onglets sont des liens vers les adresses existantes (le bot
 * renvoie vers /tickets/<id>, le jeu vers /whitelist/<id>) : aucune adresse
 * ne change. Chaque onglet déclare les capacités qui l'ouvrent ; une section
 * sans onglet permis n'apparaît pas.
 */

import type { NomIcone } from '@/components/ui/Icone';
import {
  CAPACITES_FILE,
  UNE_ZONE_FICHIERS,
  aUneCapacite,
  type ExigenceCapacite,
} from './droits';

export interface OngletNav {
  cle: string;
  label: string;
  href: string;
  /** Au moins une de ces capacités ouvre l'onglet. */
  capacites: readonly ExigenceCapacite[];
  icone?: NomIcone;
  /** Préfixes d'adresse qui rendent l'onglet actif (par défaut : href). */
  prefixes?: readonly string[];
}

export interface SectionNav {
  cle: 'a-traiter' | 'joueurs' | 'candidatures' | 'moderation' | 'contenu' | 'pilotage';
  label: string;
  icone: NomIcone;
  /** Lettre du raccourci « G puis … ». */
  touche: string;
  onglets: readonly OngletNav[];
}

export const SECTIONS: readonly SectionNav[] = [
  {
    cle: 'a-traiter',
    label: 'À traiter',
    icone: 'inbox',
    touche: 'a',
    onglets: [
      {
        cle: 'file',
        label: 'À traiter',
        href: '/dashboard',
        capacites: CAPACITES_FILE,
        prefixes: ['/dashboard', '/inbox'],
      },
    ],
  },
  {
    cle: 'joueurs',
    label: 'Joueurs',
    icone: 'users',
    touche: 'j',
    onglets: [
      { cle: 'liste', label: 'Joueurs', href: '/players', capacites: ['joueurs.voir'] },
    ],
  },
  {
    cle: 'candidatures',
    label: 'Candidatures',
    icone: 'scroll-text',
    touche: 'c',
    onglets: [
      {
        cle: 'dossiers',
        label: 'Dossiers',
        href: '/whitelist',
        capacites: ['candidatures.relire'],
        icone: 'scroll-text',
      },
      {
        cle: 'seances',
        label: 'Séances de douane',
        href: '/douane',
        capacites: ['douane.organiser'],
        icone: 'landmark',
      },
      {
        // Ancien système (un candidat par créneau), servi jusqu'à la mise en
        // service de la douane en jeu.
        cle: 'creneaux',
        label: 'Anciens créneaux',
        href: '/oral-slots',
        capacites: ['douane.organiser'],
        icone: 'clock',
      },
    ],
  },
  {
    cle: 'moderation',
    label: 'Modération',
    icone: 'shield',
    touche: 'm',
    onglets: [
      {
        cle: 'tickets',
        label: 'Tickets',
        href: '/tickets',
        capacites: ['tickets.traiter', 'tickets.bug'],
        icone: 'ticket',
      },
      // Sanctions : onglet ajouté avec le casier remonté du jeu (lot L8).
      {
        cle: 'securite',
        label: 'Sécurité',
        href: '/anomalies',
        capacites: ['securite.voir'],
        icone: 'shield',
      },
      {
        cle: 'journal',
        label: 'Journal',
        href: '/audit',
        capacites: ['audit.voir'],
        icone: 'history',
      },
    ],
  },
  {
    cle: 'contenu',
    label: 'Contenu',
    icone: 'book-open',
    touche: 'w',
    onglets: [
      {
        cle: 'wiki',
        label: 'Wiki',
        href: '/wiki',
        capacites: ['wiki.editer'],
        icone: 'book-open',
      },
      {
        cle: 'idees',
        label: 'Idées',
        href: '/wiki/ideas',
        capacites: ['wiki.editer'],
        icone: 'lightbulb',
      },
      {
        cle: 'annonces',
        label: 'Annonces Discord',
        href: '/discord',
        capacites: ['discord.publier'],
        icone: 'megaphone',
      },
      {
        cle: 'fichiers',
        label: 'Fichiers',
        href: '/files',
        capacites: [UNE_ZONE_FICHIERS],
        icone: 'server',
      },
      {
        cle: 'techniques',
        label: 'Techniques',
        href: '/abilities',
        capacites: ['techniques.editer'],
        icone: 'sparkles',
      },
    ],
  },
  {
    cle: 'pilotage',
    label: 'Pilotage',
    icone: 'chart-line',
    touche: 'p',
    onglets: [
      { cle: 'pilotage', label: 'Pilotage', href: '/pilotage', capacites: ['pilotage.voir'] },
    ],
  },
];

/** Réglages : pied de barre, ouverts à tout le staff (onglets filtrés). */
export const ONGLETS_REGLAGES: readonly OngletNav[] = [
  { cle: 'compte', label: 'Compte', href: '/settings', capacites: [] },
  {
    cle: 'preferences',
    label: 'Préférences',
    href: '/settings?onglet=preferences',
    capacites: [],
  },
  {
    cle: 'equipe',
    label: 'Équipe',
    href: '/settings?onglet=equipe',
    capacites: ['grades.changer'],
  },
  {
    cle: 'modeles',
    label: 'Modèles',
    href: '/settings?onglet=modeles',
    capacites: ['reponses-types.gerer'],
  },
];

export function ongletsPermis(
  onglets: readonly OngletNav[],
  capacites: readonly string[],
): OngletNav[] {
  return onglets.filter((o) => aUneCapacite(capacites, o.capacites));
}

/** Sections visibles pour ce compte, avec leurs onglets permis. */
export function sectionsPermises(capacites: readonly string[]) {
  return SECTIONS.map((s) => ({ ...s, permis: ongletsPermis(s.onglets, capacites) })).filter(
    (s) => s.permis.length > 0,
  );
}

function correspondChemin(chemin: string, prefixe: string): boolean {
  const base = prefixe.split('?')[0]!;
  return chemin === base || chemin.startsWith(`${base}/`);
}

/** L'onglet le plus précis qui couvre le chemin (/wiki/ideas avant /wiki). */
export function ongletActif(
  onglets: readonly OngletNav[],
  chemin: string,
): OngletNav | null {
  let meilleur: { o: OngletNav; n: number } | null = null;
  for (const o of onglets) {
    for (const p of o.prefixes ?? [o.href]) {
      const base = p.split('?')[0]!;
      if (correspondChemin(chemin, base) && base.length > (meilleur?.n ?? -1)) {
        meilleur = { o, n: base.length };
      }
    }
  }
  return meilleur?.o ?? null;
}

/** Section qui contient le chemin courant. */
export function sectionActive(chemin: string): SectionNav | null {
  let meilleur: { s: SectionNav; n: number } | null = null;
  for (const s of SECTIONS) {
    for (const o of s.onglets) {
      for (const p of o.prefixes ?? [o.href]) {
        const base = p.split('?')[0]!;
        if (correspondChemin(chemin, base) && base.length > (meilleur?.n ?? -1)) {
          meilleur = { s, n: base.length };
        }
      }
    }
  }
  return meilleur?.s ?? null;
}

/**
 * Page d'arrivée après connexion (PANEL_V2 §3.2) : À traiter pour les grades
 * qui ont une file ; les grades techniques arrivent sur Contenu. Le
 * développeur a une file (tickets « Bug ») mais son métier est Contenu : il
 * arrive sur Techniques, le compteur Bug reste dans la barre.
 */
export function pageArrivee(capacites: readonly string[]): string {
  const technique =
    capacites.includes('techniques.editer') &&
    !capacites.includes('tickets.traiter') &&
    !capacites.includes('candidatures.relire');
  if (technique) return '/abilities';
  const sections = sectionsPermises(capacites);
  const file = sections.find((s) => s.cle === 'a-traiter');
  if (file) return file.permis[0]!.href;
  const contenu = sections.find((s) => s.cle === 'contenu');
  if (contenu) return contenu.permis[0]!.href;
  return sections[0]?.permis[0]?.href ?? '/settings';
}
