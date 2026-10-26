'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { BadgeStatut } from '@/components/ui/Badge';
import { ButtonLink } from '@/components/ui/Button';
import { ListeKV } from '@/components/ui/Card';
import { Chargement, ErrorState } from '@/components/ui/States';
import { api } from '@/lib/api';
import {
  ETAT_FENETRE,
  LIBELLE_VIA,
  dateHeure,
  heureCourte,
  jourCourt,
  jourLong,
  jourProche,
  nomCompte,
} from '@/lib/candidature';
import {
  STATUT_PHASE,
  STATUT_RESERVATION_DOUANE,
  STATUT_RESULTAT_ORAL,
} from '@/lib/statuts';
import type { DouaneCandidature, SuiviCandidature } from '@/lib/types';

/**
 * Bloc « Douane » de la fiche candidature : séance réservée et fenêtre
 * d'entrée, résultat de l'oral (examinateur, motif visible, notes privées),
 * historique des places, puis la carte « Oral hors jeu » repliée (enfant).
 * Lu sur GET /admin/douane/candidatures/:applicationId.
 */
export function BlocDouane({
  applicationId,
  suivi,
  children,
}: {
  applicationId: string;
  suivi: SuiviCandidature;
  children?: ReactNode;
}) {
  const { data, error, isLoading, refetch } = useQuery({
    queryKey: ['admin', 'douane', 'candidature', applicationId],
    queryFn: () => api<DouaneCandidature>(`/admin/douane/candidatures/${applicationId}`),
    refetchInterval: 10_000,
  });

  return (
    <section className="card" aria-labelledby="titre-douane">
      <div className="card-h">
        <h2 id="titre-douane" className="card-t">
          Douane
        </h2>
        <BadgeStatut statut={STATUT_PHASE[suivi.phase]} />
      </div>
      <div className="card-b flex flex-col gap-4">
        {isLoading && <Chargement />}
        {error && <ErrorState erreur={error} onReessayer={() => void refetch()} />}
        {data && <ContenuDouane data={data} suivi={suivi} />}
        {children}
      </div>
    </section>
  );
}

function ContenuDouane({ data, suivi }: { data: DouaneCandidature; suivi: SuiviCandidature }) {
  const enCours = data.reservations.find((r) => r.statut === 'BOOKED') ?? null;
  const manquees = data.reservations.filter((r) => r.statut === 'MISSED').length;
  const oral = data.oral;
  const oralTranche = oral.decidedAt !== null && oral.status !== 'PENDING';

  return (
    <>
      {!data.writtenApprovedAt && (
        <p className="text-[13px] text-fg-2">
          Une fois l’écrit validé (HRP et RP), le candidat réserve sa séance depuis le launcher.
        </p>
      )}

      {enCours && (
        <>
          <div className="rounded-[8px] border border-[var(--color-border)] bg-surface-2 px-4 py-3">
            <div className="font-display text-[24px] leading-tight tracking-[0.03em] text-fg first-letter:uppercase">
              {jourProche(enCours.convocationAt)} · {heureCourte(enCours.convocationAt)}
            </div>
            <div className="text-[12px] text-fg-3">
              Séance de {heureCourte(enCours.seance.startAt)} à {heureCourte(enCours.seance.endAt)} ·
              entrée de {heureCourte(enCours.fenetre.ouvreAt)} à {heureCourte(enCours.fenetre.fermeAt)}
            </div>
          </div>
          <ListeKV
            largeur={104}
            lignes={[
              { cle: 'fenetre', label: 'Entrée', valeur: ETAT_FENETRE[enCours.fenetre.etat] },
              {
                cle: 'presence',
                label: 'Présence',
                valeur: enCours.presentAt
                  ? `Arrivé à ${heureCourte(enCours.presentAt)}${enCours.calledAt ? ` · appelé à ${heureCourte(enCours.calledAt)}` : ''}`
                  : 'Pas encore arrivé',
              },
              {
                cle: 'passages',
                label: 'Passages',
                valeur: `${rangPassage(data.reservations.filter((r) => r.statut === 'DONE').length + 1)}${manquees > 0 ? ` · ${manquees} séance${manquees > 1 ? 's' : ''} manquée${manquees > 1 ? 's' : ''}` : ''}`,
              },
            ]}
          />
          <ButtonLink href={`/douane/${enCours.seanceId}`} icone="landmark" bloc>
            Voir la séance
          </ButtonLink>
        </>
      )}

      {!enCours && (data.writtenApprovedAt || data.oralPassedAt || manquees > 0) && (
        <ListeKV
          largeur={124}
          lignes={[
            data.writtenApprovedAt
              ? { cle: 'ecrit', label: 'Écrit accepté', valeur: dateHeure(data.writtenApprovedAt) }
              : null,
            data.oralPassedAt
              ? { cle: 'oral', label: 'Oral réussi', valeur: `${dateHeure(data.oralPassedAt)} · plus d’oral sur ce compte` }
              : null,
            suivi.phase === 'DOUANE_A_REVOIR' && suivi.reservableDes
              ? { cle: 'nouvelle', label: 'Nouvelle séance', valeur: `dès le ${jourLong(suivi.reservableDes)}` }
              : null,
            manquees > 0 ? { cle: 'manquees', label: 'Séances manquées', valeur: String(manquees) } : null,
          ]}
        />
      )}

      {oralTranche && (
        <div className="flex flex-col gap-2 border-t border-[var(--color-line)] pt-3">
          <div className="flex items-center justify-between gap-2">
            <span className="text-[13px] font-semibold text-fg-2">Résultat de l’oral</span>
            <BadgeStatut statut={STATUT_RESULTAT_ORAL[oral.status]} />
          </div>
          <ListeKV
            largeur={104}
            lignes={[
              {
                cle: 'quand',
                label: 'Tranché',
                valeur: `${oral.decidedAt ? dateHeure(oral.decidedAt) : '—'}${oral.via ? ` · ${LIBELLE_VIA[oral.via]}` : ''}`,
              },
              {
                cle: 'qui',
                label: 'Examinateur',
                valeur: oral.examinateur ? nomCompte(oral.examinateur) : '—',
              },
            ]}
          />
          {oral.reason && (
            <div className="text-[13px]">
              <div className="mb-1 text-fg-3">Motif · visible du candidat</div>
              <div className="whitespace-pre-wrap text-fg-2 [overflow-wrap:anywhere]">{oral.reason}</div>
            </div>
          )}
          {oral.notes && (
            <div className="text-[13px]">
              <div className="mb-1 text-fg-3">Notes de l’examinateur · privé</div>
              <div className="whitespace-pre-wrap text-fg-2 [overflow-wrap:anywhere]">{oral.notes}</div>
            </div>
          )}
        </div>
      )}

      {data.reservations.length > 0 && (
        <div className="border-t border-[var(--color-line)] pt-3">
          <div className="mb-2 text-[13px] font-semibold text-fg-2">
            Places ({data.reservations.length})
          </div>
          <ul className="flex flex-col gap-1.5 text-[12px]">
            {data.reservations.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Link href={`/douane/${r.seanceId}`} className="tabular text-fg lien-discret">
                  {jourCourt(r.convocationAt)} · {heureCourte(r.convocationAt)}
                </Link>
                <BadgeStatut statut={STATUT_RESERVATION_DOUANE[r.statut]} />
                {r.resultat && <BadgeStatut statut={STATUT_RESULTAT_ORAL[r.resultat]} />}
                {r.examinateur && <span className="text-fg-3">par {nomCompte(r.examinateur)}</span>}
                {r.motifAnnulation && <span className="text-fg-3">· {r.motifAnnulation}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}

/** « 1ᵉʳ passage », « 2ᵉ passage ». */
function rangPassage(n: number): string {
  return n === 1 ? '1ᵉʳ passage' : `${n}ᵉ passage`;
}
