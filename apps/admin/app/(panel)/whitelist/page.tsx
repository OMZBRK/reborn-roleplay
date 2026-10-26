'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AssigneeBadge } from '@/components/AssignmentBlock';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { LegendeSymboles, TrioParties } from '@/components/candidature/Symboles';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { BadgeStatut, Tag } from '@/components/ui/Badge';
import { DataTable, Pagination, type Colonne } from '@/components/ui/DataTable';
import { InputRecherche } from '@/components/ui/Field';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/States';
import { Segmented } from '@/components/ui/Tabs';
import { ONGLETS_DOSSIERS, ongletDossiers, useDossiers, type CleOngletDossiers } from '@/lib/dossiers';
import { attenteDepuis, dateHeure, ecouleDepuis, ilYa, pluriel } from '@/lib/format';
import { SEUIL_ECRIT_MS } from '@/lib/preferences';
import { STATUT_PHASE } from '@/lib/statuts';
import type { WhitelistListItem } from '@/lib/types';

/**
 * Candidatures › Dossiers (PANEL_V2 §4.3) : onglets À relire · Douane ·
 * À revoir · Acceptées · Refusées · Toutes ; une seule pastille par ligne (la
 * phase) et trois symboles Écrit HRP · Écrit RP · Oral, chacun avec sa forme.
 * Une candidature de première whitelist n'a pas de clan : la ligne montre le
 * nom choisi par le candidat et son village, jamais un clan.
 */

const PAR_PAGE = 20;

export default function DossiersPage() {
  return (
    <Garde capacites={['candidatures.relire']}>
      <Dossiers />
    </Garde>
  );
}

function Dossiers() {
  const router = useRouter();
  const params = useSearchParams();
  const [cle, setCle] = useState<CleOngletDossiers>(
    ongletDossiers(params.get('onglet')).cle,
  );
  const [saisie, setSaisie] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => setQ(saisie.trim()), 300);
    return () => clearTimeout(t);
  }, [saisie]);
  useEffect(() => setPage(0), [cle, q]);

  const onglet = ongletDossiers(cle);
  const { data, isLoading, isFetching, error, refetch } = useDossiers(onglet, q, page, PAR_PAGE);
  const compteurs = data?.compteurs;
  const items = data?.items ?? [];
  const plusAncien = cle === 'ECRIT' && page === 0 && !q && items.length > 0 ? items[0]!.submittedAt : null;

  const colonnes: Colonne<WhitelistListItem>[] = [
    {
      cle: 'perso',
      titre: 'Personnage',
      rendu: (w) => (
        <span className="two block min-w-0">
          <span className="block truncate font-medium text-fg">
            {`${w.firstName} ${w.lastName}`.trim()}
          </span>
          <span className="sub2 truncate">
            {w.village}
            {w.formVersion < 2 && (
              <>
                {' · '}
                <Tag title="Envoyée avec l’ancien launcher">ancien formulaire</Tag>
              </>
            )}
          </span>
        </span>
      ),
    },
    {
      cle: 'joueur',
      titre: 'Joueur',
      rendu: (w) => (
        <span className="who">
          <MCAvatar uuid={w.user.minecraftUuid} size={22} />
          <span className="truncate text-fg">{w.user.minecraftUsername}</span>
        </span>
      ),
    },
    {
      cle: 'envoi',
      titre: 'Envoyée',
      serre: true,
      rendu: (w) => {
        const enRetard =
          w.phase === 'ECRIT_EN_ATTENTE' && ecouleDepuis(w.submittedAt) > SEUIL_ECRIT_MS;
        return (
          <span className={enRetard ? 'late' : ''} title={dateHeure(w.submittedAt)}>
            {ilYa(w.submittedAt)}
          </span>
        );
      },
    },
    {
      cle: 'phase',
      titre: 'Phase',
      serre: true,
      rendu: (w) => <BadgeStatut statut={STATUT_PHASE[w.phase]} />,
    },
    {
      cle: 'parties',
      titre: (
        <span title="Écrit HRP · Écrit RP · Oral">HRP · RP · Oral</span>
      ),
      serre: true,
      rendu: (w) => <TrioParties {...w} />,
    },
    {
      cle: 'pris',
      titre: 'Pris par',
      serre: true,
      cacherEtroit: true,
      rendu: (w) => <AssigneeBadge assignee={w.assignee} />,
    },
  ];

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Candidatures' }]}
        titre="Candidatures"
        sousTitre={
          compteurs ? (
            <>
              <b>{pluriel(compteurs.ECRIT, 'écrit')} à relire</b>
              {plusAncien && ` · plus ancien : ${attenteDepuis(plusAncien)}`}
              {` · ${pluriel(compteurs.DOUANE, 'candidat')} à la douane`}
            </>
          ) : (
            'Écrits à relire, douane, décisions'
          )
        }
      />
      <SectionTabs section="candidatures" compteurs={{ dossiers: compteurs?.ECRIT ?? null }} />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Segmented<CleOngletDossiers>
          label="Onglets des dossiers"
          valeur={cle}
          onChange={setCle}
          options={ONGLETS_DOSSIERS.map((o) => ({
            valeur: o.cle,
            label: o.label,
            n: o.filtre && compteurs ? compteurs[o.filtre] : o.cle === cle && data ? data.total : null,
          }))}
        />
        <InputRecherche
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          placeholder="Personnage, pseudo ou Discord"
          aria-label="Rechercher une candidature"
          raccourci="/"
          maxLength={64}
          conteneur="w-full sm:w-80"
        />
      </div>

      {cle === 'DOUANE' && (
        <p className="help mb-3">
          Écrit accepté, oral à passer : place à réserver, séance réservée ou douane à revoir. Les plus anciens d’abord.
        </p>
      )}

      <DataTable<WhitelistListItem>
        label="Dossiers de candidature"
        colonnes={colonnes}
        lignes={items}
        cle={(w) => w.id}
        chargement={isLoading}
        erreur={error as Error | null}
        onReessayer={() => void refetch()}
        onOuvrir={(w) => router.push(`/whitelist/${w.id}?onglet=${cle}`)}
        vide={
          <EmptyState
            icone="scroll-text"
            titre={q ? `Aucune candidature pour « ${q} ».` : 'Aucune candidature dans cet onglet.'}
            aide={cle === 'ECRIT' ? 'Les nouveaux écrits arrivent ici dès leur envoi depuis le launcher.' : undefined}
          />
        }
        pied={
          data && data.total > 0 ? (
            <>
              <span className="inline-flex flex-wrap items-center gap-4">
                <Pagination
                  page={page}
                  total={data.total}
                  parPage={PAR_PAGE}
                  onPage={setPage}
                  occupe={isFetching}
                />
              </span>
              <LegendeSymboles />
            </>
          ) : undefined
        }
      />
    </div>
  );
}
