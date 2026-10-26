'use client';

import { useQuery } from '@tanstack/react-query';
import { Garde } from '@/components/Garde';
import { Card, SectionTitre } from '@/components/ui/Card';
import { Icone } from '@/components/ui/Icone';
import { PageHeader } from '@/components/ui/PageHeader';
import { StatTile } from '@/components/ui/StatTile';
import { ErrorState } from '@/components/ui/States';
import { useEcritsEnAttente, useEtatServeur, useSeancesDuJour } from '@/lib/a-traiter';
import { api } from '@/lib/api';
import { heureCourte } from '@/lib/candidature';
import { useDroits } from '@/lib/droits';
import { attenteDepuis, nombre } from '@/lib/format';
import type { DashboardStats } from '@/lib/types';

/**
 * Pilotage (PANEL_V2 §8, STATISTIQUES.md) : les chiffres tirés des données
 * déjà en base (comptes, candidatures, tickets, séances, état du serveur).
 * Principe 7 : un bloc ou une tuile dont la collecte n'est pas en service
 * n'est PAS affiché (ni « à brancher », ni zéro inventé). Activité (sessions
 * de jeu), Économie, Démographie et la vue Détail arrivent avec leur donnée
 * (lots C, D et B de STATISTIQUES). Chaque bloc suit les droits de
 * STATISTIQUES Q6.
 */

const BLOCS = [
  { cle: 'maintenant', label: 'Maintenant', capacite: 'pilotage.voir' },
  { cle: 'comptes', label: 'Comptes', capacite: 'pilotage.analyses' },
  { cle: 'recrutement', label: 'Recrutement', capacite: 'pilotage.analyses' },
  { cle: 'staff', label: 'Staff', capacite: 'pilotage.voir' },
] as const;

export default function PilotagePage() {
  return (
    <Garde capacites={['pilotage.voir']}>
      <Pilotage />
    </Garde>
  );
}

function Pilotage() {
  const { peut } = useDroits();
  const tableau = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => api<DashboardStats>('/admin/dashboard'),
    refetchInterval: 30_000,
  });
  const d = tableau.data;
  const visibles = BLOCS.filter((b) => peut(b.capacite));
  const masques = BLOCS.filter((b) => !peut(b.capacite));

  return (
    <div className="page">
      <PageHeader
        titre="Pilotage"
        sousTitre="Serveur Reborn · chiffres tirés des données déjà en base, rafraîchis toutes les 30 s"
      />

      <nav className="anchors mb-2" aria-label="Blocs de la page">
        {visibles.map((b) => (
          <a key={b.cle} href={`#bloc-${b.cle}`}>
            {b.label}
          </a>
        ))}
        {masques.length > 0 && (
          <span className="ml-auto inline-flex items-center gap-1.5 pr-2 text-[12px] text-fg-3">
            <Icone nom="lock" taille={13} />
            {masques.map((b) => b.label).join(', ')} : à partir de Modérateur
          </span>
        )}
      </nav>

      {tableau.error && (
        <div className="mt-4">
          <ErrorState erreur={tableau.error} onReessayer={() => void tableau.refetch()} />
        </div>
      )}

      <BlocMaintenant d={d} />

      {peut('pilotage.analyses') && (
        <section className="sec" id="bloc-comptes" aria-labelledby="t-comptes">
          <SectionTitre droite="Comptes du launcher, pas encore des sessions en jeu">
            <span id="t-comptes">Comptes</span>
          </SectionTitre>
          <div className="tiles">
            <StatTile label="Comptes Reborn" icone="users" valeur={d?.users ? nombre(d.users.total) : '…'} detail="Comptes créés par le launcher." />
            <StatTile
              label="Whitelistés"
              icone="circle-check"
              valeur={d?.users ? nombre(d.users.whitelisted) : '…'}
              detail="Tous les grades sauf Joueur, staff compris."
            />
            <StatTile label="Nouveaux comptes · 24 h" icone="user" valeur={d?.users ? nombre(d.users.last24h) : '…'} detail="Création de compte, pas encore une session en jeu." />
          </div>
        </section>
      )}

      {peut('pilotage.analyses') && <BlocRecrutement d={d} />}

      <BlocStaff d={d} />

      <p className="help mt-8 flex items-center gap-2">
        <Icone nom="info" taille={14} />
        Activité en jeu, économie et démographie s’ajoutent ici dès que leur collecte est en service.
      </p>
    </div>
  );
}

// ──────────────────────────── Maintenant ────────────────────────────

function BlocMaintenant({ d }: { d: DashboardStats | undefined }) {
  const { peut } = useDroits();
  const serveur = useEtatServeur();
  const ecrits = useEcritsEnAttente(peut('candidatures.relire'));
  const seances = useSeancesDuJour(peut('douane.organiser'));
  const plusAncien = ecrits.data?.items[0]?.submittedAt ?? null;
  const duJour = seances.data ?? [];

  return (
    <section className="sec" id="bloc-maintenant" aria-labelledby="t-maintenant">
      <SectionTitre droite="Rafraîchi toutes les 30 s">
        <span id="t-maintenant" className="inline-flex items-center gap-2.5">
          Maintenant
          {serveur.data?.online && (
            <span className="inline-flex items-center gap-1.5 text-[12px] font-medium normal-case tracking-normal text-success">
              <span className="badge-dot" style={{ background: 'var(--color-success)' }} aria-hidden />
              en direct
            </span>
          )}
        </span>
      </SectionTitre>
      <div className="tiles">
        {serveur.data ? (
          <StatTile
            label="En ligne"
            icone="users"
            valeur={serveur.data.online ? nombre(serveur.data.players.online) : '—'}
            unite={serveur.data.online ? `/ ${serveur.data.players.max}` : undefined}
            detail={serveur.data.online ? 'Reborn' : 'Serveur hors ligne'}
          />
        ) : (
          <StatTile label="En ligne" icone="users" valeur="…" detail={serveur.error ? 'État du serveur indisponible' : 'Reborn'} />
        )}
        {peut('candidatures.relire') ? (
          <StatTile
            label="Écrits en attente"
            icone="scroll-text"
            valeur={ecrits.data ? nombre(ecrits.data.compteurs.ECRIT) : '…'}
            detail={plusAncien ? `Plus ancien : ${attenteDepuis(plusAncien)}` : 'Aucun écrit en attente'}
          />
        ) : (
          <StatTile
            label="Candidatures en cours"
            icone="scroll-text"
            valeur={d ? nombre(d.whitelist.pending) : '…'}
            detail="Écrit ou douane pas encore tranchés."
          />
        )}
        {peut('douane.organiser') && (
          <StatTile
            label="Douane du jour"
            icone="landmark"
            valeur={duJour.length > 0 ? nombre(duJour.reduce((n, s) => n + s.placesPrises, 0)) : '—'}
            unite={duJour.length > 0 ? `/ ${duJour.reduce((n, s) => n + s.capacite, 0)} places` : undefined}
            detail={
              duJour.length > 0
                ? duJour.map((s) => `${heureCourte(s.startAt)} · ${s.examinateurs.length} examinateur${s.examinateurs.length > 1 ? 's' : ''}`).join(' · ')
                : 'Aucune séance aujourd’hui'
            }
          />
        )}
        <StatTile
          label="Tickets ouverts"
          icone="ticket"
          valeur={d ? nombre(d.tickets.open + d.tickets.inProgress) : '…'}
          detail={d ? `${nombre(d.tickets.open)} ouverts, ${nombre(d.tickets.inProgress)} en cours` : undefined}
        />
      </div>
    </section>
  );
}

// ──────────────────────────── Recrutement ────────────────────────────

function Barres({ lignes }: { lignes: Array<{ label: string; n: number }> }) {
  const max = Math.max(1, ...lignes.map((l) => l.n));
  return (
    <ul className="flex flex-col gap-2.5">
      {lignes.map((l) => (
        <li key={l.label} className="grid grid-cols-[220px_minmax(0,1fr)_56px] items-center gap-3 text-[13px]">
          <span className="truncate text-fg-2">{l.label}</span>
          <span className="h-3.5 overflow-hidden rounded-r-[4px] bg-surface-2" aria-hidden>
            <span className="block h-full rounded-r-[4px] bg-data-1" style={{ width: `${(l.n / max) * 100}%` }} />
          </span>
          <span className="text-right font-medium tabular text-fg">{nombre(l.n)}</span>
        </li>
      ))}
    </ul>
  );
}

function BlocRecrutement({ d }: { d: DashboardStats | undefined }) {
  // Détail renvoyé seulement avec pilotage.analyses (sinon null) : ce bloc n'est montré qu'avec elle.
  const w = d?.whitelist;
  const detail = w && w.needsRevision !== null && w.approved !== null && w.rejected !== null;
  return (
    <section className="sec" id="bloc-recrutement" aria-labelledby="t-recrutement">
      <SectionTitre droite="Effectifs bruts, sans taux sur de petits nombres">
        <span id="t-recrutement">Recrutement</span>
      </SectionTitre>
      <Card titre="Candidatures par statut" meta="Toutes périodes">
        {w && detail ? (
          <Barres
            lignes={[
              { label: 'En cours (écrit ou douane)', n: w.pending },
              { label: 'À revoir (chez le candidat)', n: w.needsRevision ?? 0 },
              { label: 'Acceptées', n: w.approved ?? 0 },
              { label: 'Refusées', n: w.rejected ?? 0 },
            ]}
          />
        ) : (
          <span className="help">Chargement…</span>
        )}
      </Card>
    </section>
  );
}

// ──────────────────────────── Staff ────────────────────────────

function BlocStaff({ d }: { d: DashboardStats | undefined }) {
  return (
    <section className="sec" id="bloc-staff" aria-labelledby="t-staff">
      <SectionTitre droite="La charge de l’équipe, jamais un classement">
        <span id="t-staff">Staff</span>
      </SectionTitre>
      <Card titre="Tickets par statut" meta="Toutes périodes">
        {d ? (
          <Barres
            lignes={[
              { label: 'Ouverts', n: d.tickets.open },
              { label: 'En cours', n: d.tickets.inProgress },
              { label: 'Résolus', n: d.tickets.resolved },
              { label: 'Fermés', n: d.tickets.closed },
            ]}
          />
        ) : (
          <span className="help">Chargement…</span>
        )}
      </Card>
    </section>
  );
}
