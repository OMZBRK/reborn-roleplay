'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { BadgeStatut } from '@/components/ui/Badge';
import { DataTable, type Colonne } from '@/components/ui/DataTable';
import { InputRecherche, Select } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState } from '@/components/ui/States';
import { Segmented } from '@/components/ui/Tabs';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { dateHeure, ilYa, pluriel } from '@/lib/format';
import { LIBELLES_GRADES, ORDRE_GRADES, libelleGrade } from '@/lib/libelles';
import { statutCompte } from '@/lib/statuts';
import type { PlayerListItem, Role } from '@/lib/types';

/**
 * Joueurs (PANEL_V2 §4.2) : un seul champ (pseudo, Discord, UUID), filtres
 * par grade et statut appliqués par l'API (avant la limite de 100), tableau
 * dense trié par dernière connexion. Un nom de personnage se cherche avec
 * Ctrl K, pour qui relit les candidatures.
 */

/** Résultats renvoyés par /admin/players/search (borne de l'API). */
const MAX = 100;

type FiltreStatut = 'tous' | 'whiteliste' | 'non' | 'banni';

export default function JoueursPage() {
  return (
    <Garde capacites={['joueurs.voir']}>
      <Liste />
    </Garde>
  );
}

function Liste() {
  const router = useRouter();
  const { peut } = useDroits();
  const [saisie, setSaisie] = useState('');
  const [q, setQ] = useState('');
  const [grade, setGrade] = useState<Role | ''>('');
  const [statut, setStatut] = useState<FiltreStatut>('tous');

  useEffect(() => {
    const t = window.setTimeout(() => setQ(saisie.trim()), 250);
    return () => window.clearTimeout(t);
  }, [saisie]);

  // Grade et statut filtrés par l'API : « Bannis » compte tous les bannis,
  // pas seulement ceux des 100 derniers connectés.
  const { data, isLoading, isFetching, error, refetch } = useQuery({
    queryKey: ['admin', 'players', 'search', q, grade, statut],
    queryFn: () => {
      const p = new URLSearchParams({ take: String(MAX) });
      if (q) p.set('q', q);
      if (grade) p.set('role', grade);
      if (statut !== 'tous') p.set('statut', statut);
      return api<PlayerListItem[]>(`/admin/players/search?${p.toString()}`);
    },
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });

  // Même filtre côté panel : la liste reste juste le temps que la nouvelle
  // réponse arrive (placeholderData garde la précédente).
  const lignes = useMemo(() => {
    return (data ?? []).filter((j) => {
      if (grade && j.role !== grade) return false;
      if (statut === 'banni') return j.banned;
      if (statut === 'whiteliste') return !j.banned && j.role !== 'PLAYER';
      if (statut === 'non') return !j.banned && j.role === 'PLAYER';
      return true;
    });
  }, [data, grade, statut]);

  const colonnes: Colonne<PlayerListItem>[] = [
    {
      cle: 'joueur',
      titre: 'Joueur',
      rendu: (j) => (
        <span className="who">
          <MCAvatar uuid={j.minecraftUuid} size={26} />
          <span className="two min-w-0">
            <span className="block truncate font-medium text-fg">{j.minecraftUsername}</span>
            {j.discordUsername && <span className="sub2 truncate">@{j.discordUsername}</span>}
          </span>
        </span>
      ),
    },
    { cle: 'grade', titre: 'Grade', serre: true, rendu: (j) => libelleGrade(j.role) },
    {
      cle: 'statut',
      titre: 'Statut',
      serre: true,
      rendu: (j) => <BadgeStatut statut={statutCompte(j)} />,
    },
    {
      cle: 'connexion',
      titre: 'Dernière connexion',
      serre: true,
      rendu: (j) =>
        j.lastLoginAt ? (
          <span title={dateHeure(j.lastLoginAt)}>{ilYa(j.lastLoginAt)}</span>
        ) : (
          <span className="text-fg-3">jamais</span>
        ),
    },
    {
      cle: 'ouvrir',
      titre: <span className="sr-only">Ouvrir</span>,
      align: 'droite',
      cacherEtroit: true,
      rendu: () => <Icone nom="chevron-right" taille={16} className="text-fg-3" />,
    },
  ];

  const borne = (data?.length ?? 0) >= MAX;

  return (
    <div className="page">
      <PageHeader
        titre="Joueurs"
        sousTitre={
          data ? (
            <>
              <b>{pluriel(lignes.length, 'compte')}</b>
              {q ? ` pour « ${q} »` : ' · les derniers connectés d’abord'}
              {borne && ` · ${MAX} au plus, affine la recherche`}
            </>
          ) : (
            'Comptes Reborn : grade, statut, dernière connexion'
          )
        }
      />

      <InputRecherche
        grand
        value={saisie}
        onChange={(e) => setSaisie(e.target.value)}
        placeholder="Pseudo, Discord ou UUID"
        aria-label="Rechercher un joueur"
        raccourci="/"
        maxLength={128}
        conteneur="mb-3"
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Select
          value={grade}
          onChange={(e) => setGrade(e.target.value as Role | '')}
          aria-label="Filtrer par grade"
          className="w-auto min-w-[160px]"
        >
          <option value="">Tous les grades</option>
          {ORDRE_GRADES.map((r) => (
            <option key={r} value={r}>
              {LIBELLES_GRADES[r]}
            </option>
          ))}
        </Select>
        <Segmented<FiltreStatut>
          label="Filtrer par statut"
          valeur={statut}
          onChange={setStatut}
          options={[
            { valeur: 'tous', label: 'Tous' },
            { valeur: 'whiteliste', label: 'Whitelistés' },
            { valeur: 'non', label: 'Non whitelistés' },
            { valeur: 'banni', label: 'Bannis' },
          ]}
        />
        {isFetching && data && <span className="spin text-fg-3" aria-label="Mise à jour" />}
        {/* La palette ne cherche les personnages qu'avec candidatures.relire. */}
        {peut('candidatures.relire') && <span className="help ml-auto">Nom de personnage : Ctrl K</span>}
      </div>

      <DataTable<PlayerListItem>
        label="Comptes"
        colonnes={colonnes}
        lignes={lignes}
        cle={(j) => j.id}
        chargement={isLoading}
        erreur={error as Error | null}
        onReessayer={() => void refetch()}
        onOuvrir={(j) => router.push(`/players/${j.id}`)}
        vide={
          <EmptyState
            icone="users"
            titre={q ? `Aucun compte pour « ${q} ».` : 'Aucun compte.'}
            aide="Vérifie l’orthographe, ou cherche par UUID ou pseudo Discord."
          />
        }
      />
    </div>
  );
}
