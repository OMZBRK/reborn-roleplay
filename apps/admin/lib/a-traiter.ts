'use client';

import { useQueries, useQuery } from '@tanstack/react-query';
import type { NomIcone } from '@/components/ui/Icone';
import { api } from './api';
import { partiesParis } from './candidature';
import { useDroits } from './droits';
import { ecouleDepuis } from './format';
import { libelleCategorie } from './libelles';
import { SEUIL_ECRIT_MS, SEUIL_TICKET_MS } from './preferences';
import type {
  Assignee,
  EtatServeur,
  LoginAnomaly,
  Paginated,
  PlayerDetail,
  SeanceStaff,
  TicketListItem,
  TicketStatus,
  WhitelistListe,
} from './types';

/**
 * File « À traiter » (PANEL_V2 §4.1), assemblée à partir des routes
 * existantes (lot L3) : écrits à relire, tickets ouverts ou en cours,
 * connexions suspectes non vues. Les mêmes requêtes nourrissent les
 * compteurs de la barre latérale, rafraîchis toutes les 30 s. Les clés
 * commencent par celles des listes (['admin', 'whitelist'], ['admin',
 * 'tickets']) : toute décision ou prise les invalide.
 */

export const CLE_ECRITS = ['admin', 'whitelist', 'a-traiter'] as const;
export const CLE_TICKETS_OUVERTS = ['admin', 'tickets', 'a-traiter'] as const;
export const CLE_ALERTES = ['admin', 'anomalies', 'a-traiter'] as const;

const RAFRAICHI_MS = 30_000;

export type TypeElement = 'ecrit' | 'ticket' | 'alerte';

export interface ElementFile {
  cle: string;
  type: TypeElement;
  libelleType: string;
  icone: NomIcone;
  sujet: string;
  detail: string;
  joueur: { id: string | null; pseudo: string; uuid?: string } | null;
  depuis: string;
  retard: boolean;
  assignee: Assignee | null;
  href: string;
  /** Ce que « Prendre » vise, s'il s'applique. */
  prise: { genre: 'whitelist' | 'tickets'; id: string; statutTicket?: TicketStatus } | null;
}

export function useEcritsEnAttente(actif: boolean) {
  return useQuery({
    queryKey: CLE_ECRITS,
    queryFn: () => api<WhitelistListe>('/admin/whitelist?filtre=ECRIT&take=200'),
    enabled: actif,
    refetchInterval: RAFRAICHI_MS,
  });
}

/** Tickets ouverts et en cours (deux requêtes, l'API filtre un statut à la fois). */
export function useTicketsOuverts(actif: boolean) {
  return useQuery({
    queryKey: CLE_TICKETS_OUVERTS,
    queryFn: async () => {
      const [ouverts, enCours] = await Promise.all([
        api<Paginated<TicketListItem>>('/admin/tickets?status=OPEN&take=200'),
        api<Paginated<TicketListItem>>('/admin/tickets?status=IN_PROGRESS&take=200'),
      ]);
      return {
        total: ouverts.total + enCours.total,
        items: [...ouverts.items, ...enCours.items],
      };
    },
    enabled: actif,
    refetchInterval: RAFRAICHI_MS,
  });
}

export function useAlertesNonVues(actif: boolean) {
  return useQuery({
    queryKey: CLE_ALERTES,
    queryFn: () => api<LoginAnomaly[]>('/admin/anomalies?unack=true'),
    enabled: actif,
    refetchInterval: RAFRAICHI_MS,
  });
}

/**
 * Pseudos de comptes connus par leur seul identifiant (connexions
 * suspectes) : fiche joueur en cache, au plus 30 à la fois.
 */
export function useNomsJoueurs(ids: string[], actif: boolean) {
  const uniques = [...new Set(ids)].slice(0, 30);
  const res = useQueries({
    queries: uniques.map((id) => ({
      queryKey: ['admin', 'players', id],
      queryFn: () => api<PlayerDetail>(`/admin/players/${id}`),
      enabled: actif,
      staleTime: 5 * 60_000,
    })),
  });
  const noms = new Map<string, { pseudo: string; uuid: string }>();
  res.forEach((r, i) => {
    if (r.data) {
      noms.set(uniques[i]!, { pseudo: r.data.minecraftUsername, uuid: r.data.minecraftUuid });
    }
  });
  return noms;
}

/** Les éléments de la file, triés : en retard d'abord, puis du plus ancien au plus récent. */
export function useFileATraiter() {
  const { moi, peut, peutUne, charge: chargeDroits } = useDroits();
  const voitEcrits = peut('candidatures.relire');
  const voitTickets = peutUne('tickets.traiter', 'tickets.bug');
  const voitAlertes = peut('securite.voir');

  const ecrits = useEcritsEnAttente(voitEcrits);
  const tickets = useTicketsOuverts(voitTickets);
  const alertes = useAlertesNonVues(voitAlertes);
  const noms = useNomsJoueurs(
    (alertes.data ?? []).map((a) => a.userId),
    voitAlertes && peut('joueurs.voir'),
  );

  const elements: ElementFile[] = [];

  for (const w of ecrits.data?.items ?? []) {
    const nom = `${w.firstName} ${w.lastName}`.trim();
    // Renvoi : l'API le sait (`renvoi`). `reviewedAt` ne le dit pas : il est
    // posé à chaque décision partielle et remis à null par le renvoi.
    const valide =
      w.hrpStatus === 'APPROVED' && w.rpStatus !== 'APPROVED'
        ? 'HRP'
        : w.rpStatus === 'APPROVED' && w.hrpStatus !== 'APPROVED'
          ? 'RP'
          : null;
    const reste = valide === 'HRP' ? 'RP' : valide === 'RP' ? 'HRP' : null;
    const detail = w.renvoi
      ? reste
        ? `renvoyé, partie ${reste} à relire`
        : 'renvoyé, écrit complet à relire'
      : valide
        ? `1ᵉʳ envoi, ${valide} déjà validé`
        : '1ᵉʳ envoi';
    elements.push({
      cle: `w:${w.id}`,
      type: 'ecrit',
      libelleType: w.renvoi ? 'Écrit à relire · renvoyé' : 'Écrit à relire',
      icone: 'scroll-text',
      sujet: nom || 'Personnage sans nom',
      detail: `${w.village} · ${detail}`,
      joueur: { id: w.user.id, pseudo: w.user.minecraftUsername, uuid: w.user.minecraftUuid },
      depuis: w.submittedAt,
      retard: ecouleDepuis(w.submittedAt) > SEUIL_ECRIT_MS,
      assignee: w.assignee,
      href: `/whitelist/${w.id}`,
      prise: { genre: 'whitelist', id: w.id },
    });
  }

  for (const t of tickets.data?.items ?? []) {
    elements.push({
      cle: `t:${t.id}`,
      type: 'ticket',
      // Un signalement se lit comme tel (PANEL_V2 §4.1), pas « Ticket « Signalement » ».
      libelleType: t.category === 'REPORT_PLAYER' ? 'Signalement' : `Ticket « ${libelleCategorie(t.category)} »`,
      icone: t.category === 'BUG' ? 'bug' : t.category === 'REPORT_PLAYER' ? 'flag' : 'ticket',
      sujet: t.subject,
      detail: t.status === 'OPEN' ? 'Sans réponse' : 'En cours',
      joueur: { id: t.user.id, pseudo: t.user.minecraftUsername },
      depuis: t.createdAt,
      // Retard : ticket encore « Ouvert » au-delà du seuil (personne n'y a touché).
      retard: t.status === 'OPEN' && ecouleDepuis(t.createdAt) > SEUIL_TICKET_MS,
      assignee: t.assignee,
      href: `/tickets/${t.id}`,
      prise: { genre: 'tickets', id: t.id, statutTicket: t.status },
    });
  }

  for (const a of alertes.data ?? []) {
    const n = noms.get(a.userId);
    elements.push({
      cle: `a:${a.id}`,
      type: 'alerte',
      libelleType: 'Connexion suspecte',
      icone: 'shield',
      sujet: a.reason,
      detail: a.country ? `Pays : ${a.country}` : 'Pays inconnu',
      joueur: n ? { id: a.userId, pseudo: n.pseudo, uuid: n.uuid } : null,
      depuis: a.createdAt,
      retard: false,
      assignee: null,
      href: '/anomalies',
      prise: null,
    });
  }

  elements.sort((x, y) => {
    if (x.retard !== y.retard) return x.retard ? -1 : 1;
    return x.depuis.localeCompare(y.depuis);
  });

  const moiId = moi?.id ?? null;
  const aMoi = elements.filter((e) => e.assignee && e.assignee.id === moiId);
  const nonPris = elements.filter((e) => !e.assignee);
  const autres = elements.filter((e) => e.assignee && e.assignee.id !== moiId);

  const erreurs = [ecrits.error, tickets.error, alertes.error].filter(Boolean) as Error[];

  return {
    elements,
    aMoi,
    nonPris,
    autres,
    voit: { ecrits: voitEcrits, tickets: voitTickets, alertes: voitAlertes },
    chargement:
      chargeDroits ||
      (voitEcrits && ecrits.isLoading) ||
      (voitTickets && tickets.isLoading) ||
      (voitAlertes && alertes.isLoading),
    erreur: erreurs[0] ?? null,
    compteurEcrits: ecrits.data?.compteurs.ECRIT ?? null,
    recharger: () => {
      void ecrits.refetch();
      void tickets.refetch();
      void alertes.refetch();
    },
  };
}

/** Compteurs de la barre latérale. */
export function useCompteursMenu() {
  const f = useFileATraiter();
  const aTraiter = f.aMoi.length + f.nonPris.length;
  const aTraiterRetard = [...f.aMoi, ...f.nonPris].some((e) => e.retard);
  const ecrits = f.elements.filter((e) => e.type === 'ecrit');
  const ticketsNonPris = f.nonPris.filter((e) => e.type === 'ticket');
  return {
    'a-traiter': { n: aTraiter, retard: aTraiterRetard },
    candidatures: {
      n: f.voit.ecrits ? (f.compteurEcrits ?? ecrits.length) : 0,
      retard: ecrits.some((e) => e.retard),
    },
    moderation: {
      n: ticketsNonPris.length,
      retard: ticketsNonPris.some((e) => e.retard),
    },
  } as Record<string, { n: number; retard: boolean }>;
}

/** Même jour à Paris. */
function memeJourParis(a: Date, b: Date): boolean {
  const x = partiesParis(a);
  const y = partiesParis(b);
  return x.an === y.an && x.mois === y.mois && x.jour === y.jour;
}

/** Séances de douane du jour (ouvertes, fermées aux réservations ou closes), rafraîchies toutes les 30 s. */
export function useSeancesDuJour(actif: boolean) {
  return useQuery({
    queryKey: ['admin', 'douane', 'seances', 'jour'],
    queryFn: async () => {
      const seances = await api<SeanceStaff[]>('/admin/douane/seances?take=50');
      const maintenant = new Date();
      return seances
        .filter((s) => s.statut !== 'ANNULEE' && memeJourParis(new Date(s.startAt), maintenant))
        .sort((a, b) => a.startAt.localeCompare(b.startAt));
    },
    enabled: actif,
    refetchInterval: RAFRAICHI_MS,
  });
}

/** État du serveur de jeu (route publique), rafraîchi toutes les 30 s. */
export function useEtatServeur() {
  return useQuery({
    queryKey: ['serveur', 'etat'],
    queryFn: () => api<EtatServeur>('/server/status', { authenticated: false }),
    refetchInterval: RAFRAICHI_MS,
    retry: false,
  });
}
