'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { Garde } from '@/components/Garde';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { Mot } from '@/components/ui/Button';
import { ListeKV } from '@/components/ui/Card';
import { DataTable, Pagination, type Colonne } from '@/components/ui/DataTable';
import { Drawer } from '@/components/ui/Dialog';
import { Select } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/States';
import { Chip, Segmented } from '@/components/ui/Tabs';
import { api } from '@/lib/api';
import { dateHeure, ilYa, nombre } from '@/lib/format';
import { FAMILLES_AUDIT, LIBELLE_SOURCE, libelleAction, libelleSource } from '@/lib/libelles';
import type { AuditLogItem, Paginated } from '@/lib/types';

/**
 * Modération › Journal (PANEL_V2 §4.4) : toutes les actions du staff,
 * inscrites avec une chaîne d'empreintes (modifier une entrée casse la chaîne
 * en aval). Libellés français, liens vers les entités, détail mis en forme
 * dans un tiroir. Filtres : personne (clic sur un nom), famille d'action,
 * source. Réservé aux administrateurs.
 */

const PAR_PAGE = 50;
type Source = 'toutes' | 'panel' | 'discord' | 'launcher' | 'game' | 'system';

export default function JournalPage() {
  return (
    <Garde capacites={['audit.voir']} message="Réservé aux administrateurs.">
      <Journal />
    </Garde>
  );
}

/** Lien vers l'entité visée (« whitelist:<id> », « ticket:<id> »…). */
function LienEntite({ entite }: { entite: string | null }) {
  if (!entite) return null;
  const i = entite.indexOf(':');
  const genre = i > 0 ? entite.slice(0, i) : '';
  const id = i > 0 ? entite.slice(i + 1) : entite;
  const lien =
    genre === 'whitelist'
      ? { href: `/whitelist/${id}`, label: 'Candidature' }
      : genre === 'ticket'
        ? { href: `/tickets/${id}`, label: 'Ticket' }
        : genre === 'douane-seance'
          ? { href: `/douane/${id}`, label: 'Séance de douane' }
          : null;
  if (lien) {
    return (
      <Link href={lien.href} className="lien">
        {lien.label}
      </Link>
    );
  }
  if (genre === 'file') return <span className="mono text-fg-3">{id}</span>;
  return <span className="text-fg-3">{genre || 'Élément'}</span>;
}

function Journal() {
  const [famille, setFamille] = useState('');
  const [source, setSource] = useState<Source>('toutes');
  const [acteur, setActeur] = useState<{ id: string; nom: string } | null>(null);
  const [page, setPage] = useState(0);
  const [detail, setDetail] = useState<AuditLogItem | null>(null);

  const { data, isLoading, isFetching, error, refetch } = useQuery({
    queryKey: ['admin', 'audit', famille, source, acteur?.id ?? '', page],
    queryFn: () => {
      const qs = new URLSearchParams();
      if (famille) qs.set('action', `${famille}.`);
      if (source !== 'toutes') qs.set('source', source);
      if (acteur) qs.set('actor', acteur.id);
      qs.set('skip', String(page * PAR_PAGE));
      qs.set('take', String(PAR_PAGE));
      return api<Paginated<AuditLogItem>>(`/admin/audit?${qs}`);
    },
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });

  const colonnes: Colonne<AuditLogItem>[] = [
    {
      cle: 'quand',
      titre: 'Quand',
      serre: true,
      rendu: (a) => <span title={dateHeure(a.createdAt)}>{ilYa(a.createdAt)}</span>,
    },
    {
      cle: 'qui',
      titre: 'Qui',
      serre: true,
      rendu: (a) => (
        <Mot
          ton="neutre"
          onClick={() => {
            setActeur({ id: a.actor.id, nom: a.actor.username });
            setPage(0);
          }}
          title="Voir seulement ses actions"
        >
          {a.actor.username}
        </Mot>
      ),
    },
    { cle: 'action', titre: 'Action', principal: true, rendu: (a) => <span title={a.action}>{libelleAction(a.action)}</span> },
    {
      cle: 'cible',
      titre: 'Cible',
      rendu: (a) => (
        <span className="inline-flex flex-wrap items-center gap-x-2">
          {a.targetUser && (
            <Link href={`/players/${a.targetUser.id}`} className="text-fg lien-discret">
              {a.targetUser.username}
            </Link>
          )}
          <LienEntite entite={a.targetEntity} />
        </span>
      ),
    },
    { cle: 'source', titre: 'Source', serre: true, cacherEtroit: true, rendu: (a) => libelleSource(a.source) },
    {
      cle: 'detail',
      titre: <span className="sr-only">Détail</span>,
      align: 'droite',
      serre: true,
      rendu: () => <Icone nom="chevron-right" taille={16} className="text-fg-3" />,
    },
  ];

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Modération' }]}
        titre="Modération"
        sousTitre={
          data ? (
            <>
              Journal · <b>{nombre(data.total)} entrées</b> · chaque action du staff, chaînée par empreintes
            </>
          ) : (
            'Journal'
          )
        }
      />
      <SectionTabs section="moderation" />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Segmented<Source>
          label="Source"
          valeur={source}
          onChange={(s) => {
            setSource(s);
            setPage(0);
          }}
          options={[
            { valeur: 'toutes', label: 'Toutes' },
            ...(['panel', 'discord', 'launcher', 'game', 'system'] as const).map((s) => ({
              valeur: s,
              label: LIBELLE_SOURCE[s]!,
            })),
          ]}
        />
        <Select
          aria-label="Famille d’action"
          value={famille}
          onChange={(e) => {
            setFamille(e.target.value);
            setPage(0);
          }}
          className="w-auto min-w-[200px]"
        >
          <option value="">Toutes les actions</option>
          {FAMILLES_AUDIT.map((f) => (
            <option key={f.prefixe} value={f.prefixe}>
              {f.label}
            </option>
          ))}
        </Select>
        {acteur && (
          <Chip
            actif
            onClick={() => {
              setActeur(null);
              setPage(0);
            }}
          >
            Par {acteur.nom} <Icone nom="x" taille={13} label="Retirer le filtre" />
          </Chip>
        )}
        {isFetching && data && <span className="spin text-fg-3" aria-label="Mise à jour" />}
      </div>

      <DataTable<AuditLogItem>
        label="Journal des actions du staff"
        colonnes={colonnes}
        lignes={data?.items}
        cle={(a) => a.id}
        chargement={isLoading}
        erreur={error as Error | null}
        onReessayer={() => void refetch()}
        onOuvrir={setDetail}
        vide={<EmptyState icone="history" titre="Rien au journal pour ces filtres." />}
        pied={
          data && data.total > 0 ? (
            <Pagination page={page} total={data.total} parPage={PAR_PAGE} onPage={setPage} occupe={isFetching} />
          ) : undefined
        }
      />

      <Drawer
        ouvert={!!detail}
        onFermer={() => setDetail(null)}
        titre={detail ? libelleAction(detail.action) : ''}
        description={detail ? `${dateHeure(detail.createdAt)} · ${libelleSource(detail.source)}` : undefined}
      >
        {detail && (
          <>
            <ListeKV
              largeur={100}
              lignes={[
                { cle: 'qui', label: 'Par', valeur: detail.actor.username },
                detail.targetUser
                  ? {
                      cle: 'cible',
                      label: 'Joueur',
                      valeur: (
                        <Link href={`/players/${detail.targetUser.id}`} className="lien">
                          {detail.targetUser.username}
                        </Link>
                      ),
                    }
                  : null,
                detail.targetEntity
                  ? { cle: 'entite', label: 'Élément', valeur: <LienEntite entite={detail.targetEntity} /> }
                  : null,
                { cle: 'code', label: 'Code', valeur: <span className="mono text-fg-3">{detail.action}</span> },
              ]}
            />
            <Details valeur={detail.metadata} />
          </>
        )}
      </Drawer>
    </div>
  );
}

/** Détail de l'entrée, mis en forme (clé · valeur), au lieu du JSON coupé. */
function Details({ valeur }: { valeur: unknown }) {
  if (valeur === null || valeur === undefined) {
    return <p className="text-[13px] text-fg-3">Pas de détail.</p>;
  }
  if (typeof valeur !== 'object' || Array.isArray(valeur)) {
    return <pre className="mono whitespace-pre-wrap text-fg-2">{JSON.stringify(valeur, null, 2)}</pre>;
  }
  const entrees = Object.entries(valeur as Record<string, unknown>);
  if (entrees.length === 0) return <p className="text-[13px] text-fg-3">Pas de détail.</p>;
  return (
    <div>
      <div className="eyebrow mb-2">Détail</div>
      <dl className="kv" style={{ gridTemplateColumns: '140px minmax(0, 1fr)' }}>
        {entrees.map(([k, v]) => (
          <div key={k} className="contents">
            <dt className="mono">{k}</dt>
            <dd>
              {v === null || v === undefined ? (
                <span className="text-fg-3">—</span>
              ) : typeof v === 'object' ? (
                <pre className="mono m-0 whitespace-pre-wrap text-fg-2">{JSON.stringify(v, null, 2)}</pre>
              ) : typeof v === 'boolean' ? (
                v ? 'oui' : 'non'
              ) : (
                String(v)
              )}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
