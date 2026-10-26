'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { usePrise } from '@/components/AssignmentBlock';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { BadgeStatut } from '@/components/ui/Badge';
import { Mot } from '@/components/ui/Button';
import { DataTable, Pagination, type Colonne } from '@/components/ui/DataTable';
import { InputRecherche, Select } from '@/components/ui/Field';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/States';
import { Segmented } from '@/components/ui/Tabs';
import { useTicketsOuverts } from '@/lib/a-traiter';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { attenteDepuis, dateHeure, ecouleDepuis, ilYa, pluriel } from '@/lib/format';
import { CATEGORIES_TICKET, LIBELLE_CATEGORIE_TICKET_LONG, libelleCategorie } from '@/lib/libelles';
import { SEUIL_TICKET_MS } from '@/lib/preferences';
import { STATUT_TICKET } from '@/lib/statuts';
import type { Paginated, TicketCategory, TicketListItem, TicketStatus } from '@/lib/types';

/**
 * Modération › Tickets (PANEL_V2 §4.4) : « Ouverts » par défaut (ouverts et
 * en cours), filtres par catégorie et par « pris par », rafraîchi toutes les
 * 30 s. Un seul « Prendre » (prend et passe en cours). Avec la seule capacité
 * `tickets.bug` (développeur), l'API ne renvoie que les tickets « Bug ».
 */

type Onglet = 'OUVERTS' | 'RESOLVED' | 'CLOSED' | 'TOUS';
type FiltrePris = 'tous' | 'moi' | 'personne';

const PAR_PAGE = 50;

export default function TicketsPage() {
  return (
    <Garde capacites={['tickets.traiter', 'tickets.bug']}>
      <Tickets />
    </Garde>
  );
}

function Tickets() {
  const router = useRouter();
  const { moi, peut } = useDroits();
  const bugSeul = !peut('tickets.traiter');
  const [onglet, setOnglet] = useState<Onglet>('OUVERTS');
  const [categorie, setCategorie] = useState<TicketCategory | ''>('');
  const [pris, setPris] = useState<FiltrePris>('tous');
  const [saisie, setSaisie] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [onglet]);

  const ouverts = useTicketsOuverts(true);
  const autres = useQuery({
    queryKey: ['admin', 'tickets', 'liste', onglet, page],
    queryFn: () => {
      const statut: TicketStatus | null = onglet === 'RESOLVED' || onglet === 'CLOSED' ? onglet : null;
      return api<Paginated<TicketListItem>>(
        `/admin/tickets?take=${PAR_PAGE}&skip=${page * PAR_PAGE}${statut ? `&status=${statut}` : ''}`,
      );
    },
    enabled: onglet !== 'OUVERTS',
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
  const source = onglet === 'OUVERTS' ? ouverts : autres;

  const lignes = useMemo(() => {
    const terme = saisie.trim().toLowerCase();
    const base = [...(source.data?.items ?? [])];
    if (onglet === 'OUVERTS') {
      // Ouverts : le plus ancien d'abord (ce qui attend depuis longtemps en haut).
      base.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    }
    return base.filter((t) => {
      if (categorie && t.category !== categorie) return false;
      if (pris === 'moi' && t.assignee?.id !== moi?.id) return false;
      if (pris === 'personne' && t.assignee) return false;
      if (!terme) return true;
      return [t.subject, t.user.minecraftUsername, t.user.discordUsername ?? '', t.lastMessagePreview ?? '']
        .join(' ')
        .toLowerCase()
        .includes(terme);
    });
  }, [source.data, categorie, pris, saisie, moi, onglet]);

  const nbOuverts = ouverts.data?.total ?? null;
  const sansReponse = (ouverts.data?.items ?? []).filter(
    (t) => t.status === 'OPEN' && ecouleDepuis(t.createdAt) > SEUIL_TICKET_MS,
  ).length;

  const colonnes: Colonne<TicketListItem>[] = [
    {
      cle: 'sujet',
      titre: 'Sujet',
      principal: true,
      rendu: (t) => (
        <span className="ellip block max-w-[420px]" title={t.lastMessagePreview ?? undefined}>
          {t.subject}
        </span>
      ),
    },
    { cle: 'cat', titre: 'Catégorie', serre: true, rendu: (t) => libelleCategorie(t.category) },
    {
      cle: 'joueur',
      titre: 'Joueur',
      rendu: (t) => (
        <span className="who">
          <MCAvatar uuid={t.user.minecraftUsername} size={22} />
          <span className="truncate text-fg">{t.user.minecraftUsername}</span>
        </span>
      ),
    },
    { cle: 'statut', titre: 'Statut', serre: true, rendu: (t) => <BadgeStatut statut={STATUT_TICKET[t.status]} /> },
    {
      cle: 'activite',
      titre: 'Dernière activité',
      serre: true,
      cacherEtroit: true,
      rendu: (t) => <span title={dateHeure(t.updatedAt)}>{ilYa(t.updatedAt)}</span>,
    },
    {
      cle: 'attente',
      titre: 'Ouvert depuis',
      align: 'droite',
      serre: true,
      rendu: (t) => {
        const retard = t.status === 'OPEN' && ecouleDepuis(t.createdAt) > SEUIL_TICKET_MS;
        const clos = t.status === 'RESOLVED' || t.status === 'CLOSED';
        return (
          <span className={`tabular ${retard ? 'late' : clos ? 'text-fg-3' : ''}`} title={dateHeure(t.createdAt)}>
            {clos ? ilYa(t.createdAt) : attenteDepuis(t.createdAt)}
          </span>
        );
      },
    },
    {
      cle: 'pris',
      titre: 'Pris par',
      serre: true,
      rendu: (t) => <PrisOuPrendre t={t} moiId={moi?.id} />,
    },
  ];

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Modération' }]}
        titre="Modération"
        sousTitre={
          nbOuverts !== null ? (
            <>
              <b>{pluriel(nbOuverts, 'ticket')} ouvert{nbOuverts > 1 ? 's' : ''}</b>
              {sansReponse > 0 && <> · {sansReponse} sans prise en charge depuis plus de 24 h</>}
              {bugSeul && ' · ton grade traite les tickets « Bug »'}
            </>
          ) : (
            'Tickets'
          )
        }
      />
      <SectionTabs section="moderation" compteurs={{ tickets: nbOuverts }} />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Segmented<Onglet>
          label="Tickets affichés"
          valeur={onglet}
          onChange={setOnglet}
          options={[
            { valeur: 'OUVERTS', label: 'Ouverts', n: nbOuverts },
            { valeur: 'RESOLVED', label: 'Résolus' },
            { valeur: 'CLOSED', label: 'Fermés' },
            { valeur: 'TOUS', label: 'Tous' },
          ]}
        />
        {!bugSeul && (
          <Select
            aria-label="Catégorie"
            value={categorie}
            onChange={(e) => setCategorie(e.target.value as TicketCategory | '')}
            className="w-auto min-w-[200px]"
          >
            <option value="">Toutes les catégories</option>
            {CATEGORIES_TICKET.map((c) => (
              <option key={c} value={c}>
                {LIBELLE_CATEGORIE_TICKET_LONG[c]}
              </option>
            ))}
          </Select>
        )}
        <Select
          aria-label="Pris par"
          value={pris}
          onChange={(e) => setPris(e.target.value as FiltrePris)}
          className="w-auto min-w-[150px]"
        >
          <option value="tous">Pris par : tous</option>
          <option value="moi">Pris par : moi</option>
          <option value="personne">Pris par : personne</option>
        </Select>
        <InputRecherche
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          placeholder="Titre ou joueur"
          aria-label="Rechercher un ticket dans la liste"
          raccourci="/"
          conteneur="w-full sm:ml-auto sm:w-72"
        />
      </div>

      <DataTable<TicketListItem>
        label="Tickets"
        colonnes={colonnes}
        lignes={lignes}
        cle={(t) => t.id}
        chargement={source.isLoading}
        erreur={source.error as Error | null}
        onReessayer={() => void source.refetch()}
        onOuvrir={(t) => router.push(`/tickets/${t.id}`)}
        estAttenue={(t) => t.status === 'RESOLVED' || t.status === 'CLOSED'}
        vide={
          <EmptyState
            icone="ticket"
            titre={saisie || categorie || pris !== 'tous' ? 'Aucun ticket ne correspond aux filtres.' : 'Aucun ticket ici.'}
            aide={onglet === 'OUVERTS' ? 'Les tickets ouverts depuis le launcher arrivent ici.' : undefined}
          />
        }
        pied={
          onglet === 'OUVERTS' ? (
            <span>Ouverts et en cours, le plus ancien d’abord · rafraîchi toutes les 30 s</span>
          ) : autres.data && autres.data.total > 0 ? (
            <Pagination page={page} total={autres.data.total} parPage={PAR_PAGE} onPage={setPage} occupe={autres.isFetching} />
          ) : undefined
        }
      />
    </div>
  );
}

function PrisOuPrendre({ t, moiId }: { t: TicketListItem; moiId?: string }) {
  const { prendre } = usePrise('tickets', t.id, { statutTicket: t.status });
  if (t.assignee) {
    return (
      <span className="who text-fg-2">
        <MCAvatar uuid={t.assignee.minecraftUuid} size={22} />
        <span className="truncate">{t.assignee.id === moiId ? 'Toi' : t.assignee.minecraftUsername}</span>
      </span>
    );
  }
  if (t.status === 'RESOLVED' || t.status === 'CLOSED') return <span className="text-fg-3">—</span>;
  return (
    <Mot enCours={prendre.isPending} onClick={() => prendre.mutate(false)} aria-label={`Prendre : ${t.subject}`}>
      Prendre
    </Mot>
  );
}
