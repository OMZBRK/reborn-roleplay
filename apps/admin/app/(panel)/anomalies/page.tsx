'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { Badge } from '@/components/ui/Badge';
import { Button, Mot } from '@/components/ui/Button';
import { DataTable, type Colonne } from '@/components/ui/DataTable';
import { Icone } from '@/components/ui/Icone';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, messageErreur } from '@/components/ui/States';
import { Segmented } from '@/components/ui/Tabs';
import { useNomsJoueurs } from '@/lib/a-traiter';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { dateHeure, ilYa, pluriel } from '@/lib/format';
import type { IpAnomalie, LoginAnomaly } from '@/lib/types';

/**
 * Modération › Sécurité (PANEL_V2 §4.4) : connexions suspectes, avec lien
 * vers la fiche du joueur, pays, IP masquée. La liste n'apporte plus l'IP au
 * navigateur : « Afficher » lit l'adresse de CETTE connexion par une route
 * journalisée (`anomaly.ip.view`, cible = la connexion) ; « Vue » en mot,
 * sélection multiple possible. Réservé aux administrateurs.
 */

type Filtre = 'avoir' | 'vues' | 'toutes';

export default function SecuritePage() {
  return (
    <Garde capacites={['securite.voir']} message="Réservé aux administrateurs.">
      <Securite />
    </Garde>
  );
}

function Securite() {
  const qc = useQueryClient();
  const { peut } = useDroits();
  const [filtre, setFiltre] = useState<Filtre>('avoir');
  const [selection, setSelection] = useState<string[]>([]);
  const [revelees, setRevelees] = useState<Record<string, IpAnomalie>>({});

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'anomalies', 'toutes'],
    queryFn: () => api<LoginAnomaly[]>('/admin/anomalies'),
    refetchInterval: 30_000,
  });
  const noms = useNomsJoueurs((data ?? []).map((a) => a.userId), peut('joueurs.voir'));

  const lignes = useMemo(
    () =>
      (data ?? []).filter((a) =>
        filtre === 'avoir' ? !a.acknowledged : filtre === 'vues' ? a.acknowledged : true,
      ),
    [data, filtre],
  );
  const aVoir = (data ?? []).filter((a) => !a.acknowledged).length;

  const marquer = useMutation({
    mutationFn: async (ids: string[]) => {
      for (const id of ids) await api(`/admin/anomalies/${id}/ack`, { method: 'POST' });
    },
    onSuccess: (_d, ids) => {
      qc.invalidateQueries({ queryKey: ['admin', 'anomalies'] });
      setSelection([]);
      toast.success(ids.length > 1 ? `${ids.length} connexions marquées vues` : 'Connexion marquée vue');
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });

  // « Afficher » : l'adresse de cette connexion, lue à la demande ; l'API
  // l'inscrit au journal avant de la renvoyer.
  const reveler = useMutation({
    mutationFn: (a: LoginAnomaly) => api<IpAnomalie>(`/admin/anomalies/${a.id}/ip`),
    onSuccess: (ip, a) => setRevelees((r) => ({ ...r, [a.id]: ip })),
    onError: (e) => toast.error('Adresse non affichée', { description: messageErreur(e) }),
  });

  const colonnes: Colonne<LoginAnomaly>[] = [
    {
      cle: 'sel',
      titre: <span className="sr-only">Sélection</span>,
      largeur: '44px',
      rendu: (a) =>
        a.acknowledged ? null : (
          <input
            type="checkbox"
            className="check"
            aria-label="Sélectionner"
            checked={selection.includes(a.id)}
            onChange={(e) =>
              setSelection((s) => (e.target.checked ? [...s, a.id] : s.filter((x) => x !== a.id)))
            }
          />
        ),
    },
    {
      cle: 'quand',
      titre: 'Quand',
      serre: true,
      rendu: (a) => <span title={dateHeure(a.createdAt)}>{ilYa(a.createdAt)}</span>,
    },
    {
      cle: 'joueur',
      titre: 'Joueur',
      rendu: (a) => {
        const n = noms.get(a.userId);
        return n ? (
          <span className="who">
            <MCAvatar uuid={n.uuid} size={22} />
            <Link href={`/players/${a.userId}?onglet=securite`} className="truncate text-fg lien-discret">
              {n.pseudo}
            </Link>
          </span>
        ) : (
          <Link href={`/players/${a.userId}?onglet=securite`} className="lien">
            Ouvrir la fiche
          </Link>
        );
      },
    },
    { cle: 'signal', titre: 'Signal', rendu: (a) => <span className="text-fg">{a.reason}</span> },
    { cle: 'pays', titre: 'Pays', serre: true, rendu: (a) => a.country ?? <span className="text-fg-3">inconnu</span> },
    {
      cle: 'ip',
      titre: 'Adresse IP',
      serre: true,
      cacherEtroit: true,
      rendu: (a) =>
        !a.ipConnue ? (
          <span className="text-fg-3">aucune</span>
        ) : revelees[a.id] ? (
          <span className="mono">{revelees[a.id]!.ip ?? 'aucune'}</span>
        ) : (
          <span className="inline-flex items-center gap-3">
            <span className="mono text-fg-3" aria-label="Adresse masquée">
              •••.•••.•••.••
            </span>
            {peut('joueurs.ip') && (
              <Mot onClick={() => reveler.mutate(a)} enCours={reveler.isPending && reveler.variables?.id === a.id}>
                <span className="inline-flex items-center gap-1">
                  <Icone nom="eye" taille={14} /> Afficher
                </span>
              </Mot>
            )}
          </span>
        ),
    },
    {
      cle: 'statut',
      titre: 'Statut',
      serre: true,
      align: 'droite',
      rendu: (a) =>
        a.acknowledged ? (
          <span className="text-fg-3">Vue</span>
        ) : (
          <span className="inline-flex items-center gap-3">
            <Badge ton="attention">À voir</Badge>
            <Mot onClick={() => marquer.mutate([a.id])} disabled={marquer.isPending}>
              Marquer vue
            </Mot>
          </span>
        ),
    },
  ];

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Modération' }]}
        titre="Modération"
        sousTitre={
          <>
            Connexions suspectes · <b>{pluriel(aVoir, 'à voir', 'à voir')}</b> · réservé aux administrateurs
          </>
        }
      />
      <SectionTabs section="moderation" compteurs={{ securite: aVoir || null }} />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Segmented<Filtre>
          label="Connexions affichées"
          valeur={filtre}
          onChange={(f) => {
            setFiltre(f);
            setSelection([]);
          }}
          options={[
            { valeur: 'avoir', label: 'À voir', n: aVoir },
            { valeur: 'vues', label: 'Vues' },
            { valeur: 'toutes', label: 'Toutes' },
          ]}
        />
        {selection.length > 0 && (
          <div className="inline-flex items-center gap-3 rounded-[8px] border border-border bg-surface-2 px-3 py-1.5 text-[13px]">
            <span>{pluriel(selection.length, 'sélectionnée')}</span>
            <Button taille="sm" enCours={marquer.isPending} onClick={() => marquer.mutate(selection)}>
              Marquer vues
            </Button>
            <Mot ton="neutre" onClick={() => setSelection([])}>
              Annuler
            </Mot>
          </div>
        )}
      </div>

      <DataTable<LoginAnomaly>
        label="Connexions suspectes"
        colonnes={colonnes}
        lignes={lignes}
        cle={(a) => a.id}
        chargement={isLoading}
        erreur={error as Error | null}
        onReessayer={() => void refetch()}
        estAttenue={(a) => a.acknowledged}
        vide={<EmptyState icone="shield" titre="Rien à signaler." aide="Une connexion depuis un nouveau pays apparaît ici." />}
        pied={
          <>
            <span className="inline-flex items-center gap-1.5">
              <Icone nom="eye" taille={14} /> Chaque affichage d’une adresse IP est inscrit au journal.
            </span>
          </>
        }
      />
    </div>
  );
}
