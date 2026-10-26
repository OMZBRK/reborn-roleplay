'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { use, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { Badge, BadgeStatut, Tag } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, ListeKV } from '@/components/ui/Card';
import { Dialog } from '@/components/ui/Dialog';
import { Field, Input, Textarea } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { MenuPlus } from '@/components/ui/Menu';
import { Chargement, EmptyState, ErrorState, messageErreur } from '@/components/ui/States';
import { Chip } from '@/components/ui/Tabs';
import { api } from '@/lib/api';
import { dateHeure, heureCourte, jourLong, nomCompte } from '@/lib/candidature';
import { useDroits } from '@/lib/droits';
import { CAPACITE_BORNES } from '@/lib/douane';
import { pluriel } from '@/lib/format';
import { STATUT_RESERVATION_DOUANE, STATUT_RESULTAT_ORAL, STATUT_SEANCE_DOUANE } from '@/lib/statuts';
import type {
  EvenementPresence,
  ExaminateurPropose,
  ReservationStaff,
  SeanceStaffDetail,
} from '@/lib/types';

/**
 * Fiche d'une séance de douane (PANEL_V2 §4.3, maquette 07) : vagues de
 * convocation, présence en direct, absents, retraits avec motif, réglages,
 * clôture avec notes privées, annulation avec motif transmis aux candidats.
 * Les décisions se prennent au Poste de garde en jeu ; le panel les affiche.
 * Rafraîchie toutes les 5 s pendant la séance.
 */
export default function SeanceDouanePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Garde capacites={['douane.organiser']}>
      <Seance id={id} />
    </Garde>
  );
}

function enCoursMaintenant(s: { startAt: string; endAt: string; statut: string }) {
  const t = Date.now();
  return (
    (s.statut === 'OUVERTE' || s.statut === 'FERMEE') &&
    t >= Date.parse(s.startAt) - 30 * 60_000 &&
    t <= Date.parse(s.endAt)
  );
}

function Seance({ id }: { id: string }) {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'douane', 'seance', id],
    queryFn: () => api<SeanceStaffDetail>(`/admin/douane/seances/${id}`),
    refetchInterval: (q) => (q.state.data && enCoursMaintenant(q.state.data) ? 5_000 : 10_000),
  });

  if (isLoading) {
    return (
      <div className="page">
        <Chargement lignes={5} />
      </div>
    );
  }
  if (error || !data) {
    return (
      <div className="page">
        <ErrorState erreur={error ?? new Error('Séance introuvable.')} onReessayer={() => void refetch()} />
      </div>
    );
  }
  return <ContenuSeance s={data} />;
}

function useInvalider() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ['admin', 'douane'] });
    qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
  };
}

function ContenuSeance({ s }: { s: SeanceStaffDetail }) {
  const { moi } = useDroits();
  const invalider = useInvalider();
  const [modifier, setModifier] = useState(false);
  const [clore, setClore] = useState(false);
  const [annuler, setAnnuler] = useState(false);
  const active = s.statut === 'OUVERTE' || s.statut === 'FERMEE';
  const direct = enCoursMaintenant(s);

  const vagues = useMemo(() => {
    const parHeure = new Map<string, ReservationStaff[]>();
    for (const v of s.vagues) parHeure.set(v, []);
    for (const r of s.reservations) {
      if (!parHeure.has(r.convocationAt)) parHeure.set(r.convocationAt, []);
      parHeure.get(r.convocationAt)!.push(r);
    }
    return [...parHeure.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([heure, reservations]) => ({ heure, reservations }));
  }, [s.vagues, s.reservations]);

  const arrives = s.reservations.filter((r) => r.presentAt && r.statut !== 'CANCELLED').length;
  const acceptes = s.reservations.filter((r) => r.resultat === 'APPROVED').length;

  const statutReservations = useMutation({
    mutationFn: (statut: 'OUVERTE' | 'FERMEE') =>
      api(`/admin/douane/seances/${s.id}`, { method: 'PATCH', body: { statut } }),
    onSuccess: (_d, statut) => {
      invalider();
      toast.success(statut === 'FERMEE' ? 'Réservations fermées' : 'Réservations rouvertes');
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });

  const entreesPlus = active
    ? [
        s.statut === 'OUVERTE'
          ? {
              cle: 'fermer',
              label: 'Fermer les réservations',
              icone: 'lock' as const,
              onClick: () => statutReservations.mutate('FERMEE'),
            }
          : {
              cle: 'rouvrir',
              label: 'Rouvrir les réservations',
              icone: 'rotate-ccw' as const,
              onClick: () => statutReservations.mutate('OUVERTE'),
            },
        { cle: 'modifier', label: 'Modifier la capacité et les examinateurs', icone: 'pencil' as const, onClick: () => setModifier(true) },
        { cle: 'annuler', label: 'Annuler la séance…', icone: 'x' as const, danger: true, onClick: () => setAnnuler(true) },
      ]
    : [];

  const titre = `Séance du ${jourLong(s.startAt)} · ${heureCourte(s.startAt)}`;

  return (
    <div className="page">
      <header className="ph">
        <div className="min-w-0 flex-1">
          <nav className="crumbs" aria-label="Fil d’Ariane">
            <Link href="/whitelist">Candidatures</Link>
            <Icone nom="chevron-right" taille={12} />
            <Link href="/douane">Séances de douane</Link>
            <Icone nom="chevron-right" taille={12} />
            <span className="first-letter:uppercase">{jourLong(s.startAt)}</span>
          </nav>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="ph-title first-letter:uppercase">{titre}</h1>
            {direct ? (
              <Badge ton="succes" teinte>
                En cours
              </Badge>
            ) : (
              <BadgeStatut statut={STATUT_SEANCE_DOUANE[s.statut]} />
            )}
          </div>
          <div className="ph-sub">
            {heureCourte(s.startAt)} – {heureCourte(s.endAt)}
            {s.openedBy && <> · planifiée par {nomCompte(s.openedBy)}</>}
            {active && <> · rafraîchie toutes les {direct ? '5' : '10'} s</>}
          </div>
        </div>
        <div className="ph-act">
          {active && (
            <Button onClick={() => setClore(true)} icone="check">
              Clore la séance
            </Button>
          )}
          <MenuPlus entrees={entreesPlus} />
        </div>
      </header>

      <Card corps={false} className="mb-5">
        <div className="flex flex-wrap items-center gap-x-9 gap-y-3 px-6 py-4">
          <Chiffre label="Inscrits" valeur={`${s.placesPrises}`} sur={s.capacite} />
          <Chiffre label="Arrivés" valeur={String(arrives)} />
          <Chiffre label="Passés" valeur={String(s.compte.DONE)} />
          <Chiffre label="Acceptés" valeur={String(acceptes)} />
          <Chiffre label="Manquées" valeur={String(s.compte.MISSED)} rouge={s.compte.MISSED > 0} />
          <div className="ml-auto text-[13px] text-fg-2">
            {s.examinateurs.length > 0 ? (
              <span className="inline-flex flex-wrap items-center gap-x-4 gap-y-1">
                {s.examinateurs.map((e) => {
                  // Au bureau : un candidat appelé par cet examinateur n'est pas encore sorti.
                  const auBureau =
                    direct &&
                    s.reservations.some(
                      (r) => r.examinateur?.id === e.id && r.calledAt && !r.departAt && !r.resultat,
                    );
                  return (
                    <span key={e.id} className="inline-flex items-center gap-2">
                      <span
                        className="badge-dot"
                        style={
                          auBureau
                            ? { background: 'var(--color-success)' }
                            : { background: 'transparent', boxShadow: 'inset 0 0 0 1px var(--color-fg-3)' }
                        }
                        aria-hidden
                      />
                      {moi?.id === e.id ? 'Toi' : nomCompte(e)}
                      {direct && <span className="text-fg-3">· {auBureau ? 'au bureau' : 'libre'}</span>}
                    </span>
                  );
                })}
              </span>
            ) : (
              <span className="inline-flex items-center gap-2">
                <span className="badge-dot" style={{ background: 'var(--color-warning)' }} aria-hidden />
                Aucun examinateur
              </span>
            )}
          </div>
        </div>
      </Card>

      <div className="split">
        <div className="stack">
          {s.reservations.length === 0 ? (
            <EmptyState
              icone="landmark"
              titre="Aucune réservation pour l’instant."
              aide="Les candidats dont l’écrit est validé réservent depuis le launcher."
            />
          ) : (
            vagues
              .filter((v) => v.reservations.length > 0)
              .map((v, i) => (
                <Vague key={v.heure} numero={i + 1} heure={v.heure} reservations={v.reservations} active={active} />
              ))
          )}
          {active && vagues.some((v) => v.reservations.length === 0) && (
            <p className="help">
              Vagues encore vides :{' '}
              {vagues
                .filter((v) => v.reservations.length === 0)
                .map((v) => heureCourte(v.heure))
                .join(' · ')}
            </p>
          )}
        </div>

        <aside className="stack">
          <Card titre="La séance">
            <ListeKV
              largeur={110}
              lignes={[
                {
                  cle: 'vagues',
                  label: s.vagues.length > 1 ? 'Vagues' : 'Convocation',
                  valeur: <span className="tabular">{s.vagues.map((v) => heureCourte(v)).join(' · ')}</span>,
                },
                { cle: 'capacite', label: 'Capacité', valeur: pluriel(s.capacite, 'place') },
                s.capaciteConseillee !== null
                  ? { cle: 'repere', label: 'Repère', valeur: `${s.capaciteConseillee} (examinateurs × heures × 4)` }
                  : null,
                {
                  cle: 'resa',
                  label: 'Réservations',
                  valeur: s.statut === 'OUVERTE' ? 'ouvertes' : s.statut === 'FERMEE' ? 'fermées' : '—',
                },
              ]}
            />
            {s.statut === 'FERMEE' && <p className="help mt-3">Fermée : la séance a lieu, mais plus personne ne réserve.</p>}
          </Card>

          {active && (
            <Card titre="Retraits et incidents">
              <p className="text-[13px] text-fg-3">
                Un retrait se fait ici (motif visible du candidat) ou au Poste de garde. Les absents passent en « séance
                manquée » à la clôture.
              </p>
            </Card>
          )}

          {s.statut === 'TERMINEE' && (
            <Card titre="Notes de clôture" meta="Privé · pour l’équipe">
              <p className="text-[12px] text-fg-3">Close le {s.clotureeAt ? dateHeure(s.clotureeAt) : '—'}</p>
              <p className="mt-2 whitespace-pre-wrap text-[13px] text-fg-2 [overflow-wrap:anywhere]">
                {s.notes ?? 'Aucune note.'}
              </p>
            </Card>
          )}
          {s.statut === 'ANNULEE' && (
            <Card titre="Annulation" meta="Visible des candidats">
              <p className="text-[12px] text-fg-3">Annulée le {s.annuleeAt ? dateHeure(s.annuleeAt) : '—'}</p>
              <p className="mt-2 whitespace-pre-wrap text-[13px] text-fg-2 [overflow-wrap:anywhere]">{s.motifAnnulation}</p>
            </Card>
          )}
        </aside>
      </div>

      {active && <ModifierSeance s={s} ouvert={modifier} onFermer={() => setModifier(false)} />}
      {active && <CloreSeance s={s} ouvert={clore} onFermer={() => setClore(false)} />}
      {active && <AnnulerSeance s={s} ouvert={annuler} onFermer={() => setAnnuler(false)} />}
    </div>
  );
}

function Chiffre({ label, valeur, sur, rouge = false }: { label: string; valeur: string; sur?: number; rouge?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-[12px] text-fg-3">{label}</span>
      <span className={`font-display text-[28px] leading-none tracking-[0.02em] tabular ${rouge ? 'text-danger-text' : 'text-fg'}`}>
        {valeur}
        {sur !== undefined && <small className="ml-1 font-sans text-[13px] tracking-normal text-fg-3">/ {sur}</small>}
      </span>
    </div>
  );
}

// ──────────────────────────── Vague et réservations ────────────────────────────

function Vague({
  numero,
  heure,
  reservations,
  active,
}: {
  numero: number;
  heure: string;
  reservations: ReservationStaff[];
  active: boolean;
}) {
  const fenetre = reservations[0]?.fenetre;
  return (
    <div className="dt-wrap">
      <table className="dt empilable [table-layout:fixed]">
        <caption className="sr-only">Vague {numero}, convocation à {heureCourte(heure)}</caption>
        {/* Mêmes colonnes d'une vague à l'autre : les tableaux s'alignent. */}
        <colgroup>
          <col style={{ width: '27%' }} />
          <col style={{ width: '14%' }} />
          <col />
          <col style={{ width: '16%' }} />
          <col style={{ width: '52px' }} />
        </colgroup>
        <thead>
          <tr>
            <th scope="col">
              Vague {numero} · {heureCourte(heure)}
              <span className="ml-2 font-normal">{pluriel(reservations.length, 'candidat')}</span>
            </th>
            <th scope="col">État</th>
            <th scope="col" className="cacher-etroit">
              Détail
              {fenetre && (
                <span className="ml-2 font-normal">
                  · entrée {heureCourte(fenetre.ouvreAt)}–{heureCourte(fenetre.fermeAt)}, {ETAT_ENTREE_COURT[fenetre.etat]}
                </span>
              )}
            </th>
            <th scope="col" className="cacher-etroit">Examinateur</th>
            <th scope="col" className="r">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {reservations.map((r) => (
            <LigneReservation key={r.id} r={r} active={active} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** État de la fenêtre d'entrée, après « entrée 22 h 15–23 h, … ». */
const ETAT_ENTREE_COURT: Record<ReservationStaff['fenetre']['etat'], string> = {
  A_VENIR: 'pas encore ouverte',
  OUVERTE: 'ouverte',
  FERMEE: 'fermée',
};

function etatPresence(r: ReservationStaff): { label: string; ton: 'succes' | 'info' | 'neutre' | 'attention' | 'danger' | 'vide' } {
  if (r.resultat) {
    const s = STATUT_RESULTAT_ORAL[r.resultat];
    return { label: s.label, ton: s.ton };
  }
  if (r.statut !== 'BOOKED') {
    const s = STATUT_RESERVATION_DOUANE[r.statut];
    return { label: s.label, ton: s.ton };
  }
  if (r.departAt) return { label: 'Sorti', ton: 'neutre' };
  if (r.calledAt) return { label: 'Au bureau', ton: 'info' };
  if (r.presentAt) return { label: 'Dans le hall', ton: 'neutre' };
  if (r.fenetre.etat === 'FERMEE') return { label: 'En retard', ton: 'attention' };
  return { label: 'Attendu', ton: 'vide' };
}

function LigneReservation({ r, active }: { r: ReservationStaff; active: boolean }) {
  const invalider = useInvalider();
  const [retrait, setRetrait] = useState(false);
  const reservee = r.statut === 'BOOKED';
  const presenceModifiable = active && (reservee || r.statut === 'DONE');
  const etat = etatPresence(r);

  const presence = useMutation({
    mutationFn: (evenement: EvenementPresence) =>
      api(`/admin/douane/reservations/${r.id}/presence`, { method: 'PATCH', body: { evenement } }),
    onSuccess: invalider,
    onError: (e) => toast.error('Présence non enregistrée', { description: messageErreur(e) }),
  });
  const absence = useMutation({
    mutationFn: () => api(`/admin/douane/reservations/${r.id}/absence`, { method: 'POST' }),
    onSuccess: () => {
      invalider();
      toast.success(`${r.candidat.minecraftUsername} : séance manquée`, {
        description: 'Le candidat peut réserver une autre séance.',
      });
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });
  const occupe = presence.isPending || absence.isPending;

  const actions = presenceModifiable && !occupe
    ? [
        ...(!r.presentAt ? [{ cle: 'arrivee', label: 'Arrivé au hall', icone: 'landmark' as const, onClick: () => presence.mutate('ARRIVEE') }] : []),
        ...(!r.calledAt ? [{ cle: 'appel', label: 'Appelé au bureau', icone: 'megaphone' as const, onClick: () => presence.mutate('APPEL') }] : []),
        ...((r.presentAt || r.calledAt) && !r.departAt
          ? [{ cle: 'depart', label: 'Sorti de la douane', icone: 'log-out' as const, onClick: () => presence.mutate('DEPART') }]
          : []),
        ...(reservee && !r.presentAt
          ? [{ cle: 'absent', label: 'Marquer absent', icone: 'circle-x' as const, onClick: () => absence.mutate() }]
          : []),
        ...(reservee ? [{ cle: 'retrait', label: 'Retirer de la séance…', icone: 'x' as const, danger: true, onClick: () => setRetrait(true) }] : []),
      ]
    : [];

  return (
    <tr>
      <td>
        <span className="two block min-w-0">
          <Link href={`/whitelist/${r.candidature.id}`} className="block truncate font-medium text-fg lien-discret">
            {`${r.candidature.firstName} ${r.candidature.lastName}`.trim()}
          </Link>
          <span className="sub2 flex flex-wrap items-center gap-1.5">
            {r.candidat.minecraftUsername} · {r.candidature.village}
            {r.candidature.secondPassage && <Tag>2ᵉ passage</Tag>}
            {r.candidat.seancesManquees > 0 && (
              <Tag title="Séances manquées sur ce compte">
                {r.candidat.seancesManquees} manquée{r.candidat.seancesManquees > 1 ? 's' : ''}
              </Tag>
            )}
          </span>
        </span>
      </td>
      <td className="nw">
        <Badge ton={etat.ton}>{etat.label}</Badge>
      </td>
      <td className="cacher-etroit">
        <span className="text-[13px] text-fg-2">{detailPresence(r)}</span>
      </td>
      <td className="cacher-etroit nw">
        {r.examinateur ? (
          <span className="who text-fg-2">
            <MCAvatar uuid={r.examinateur.minecraftUsername} size={22} />
            {nomCompte(r.examinateur)}
          </span>
        ) : (
          <span className="text-fg-3">—</span>
        )}
      </td>
      <td className="r nw">
        {actions.length > 0 && <MenuPlus label="Présence et retrait" entrees={actions} discret />}
        {occupe && <span className="spin text-fg-3" aria-label="Enregistrement" />}
        {retrait && <RetirerCandidat r={r} onFermer={() => setRetrait(false)} />}
      </td>
    </tr>
  );
}

/**
 * Une ligne de détail, la plus utile selon l'avancement (maquette
 * « séance en cours ») : passage et durée, appel, attente dans le hall,
 * retard. Le chronologique complet reste au journal.
 */
function detailPresence(r: ReservationStaff): string {
  const minutes = (a: string, b: string) => Math.max(0, Math.round((Date.parse(b) - Date.parse(a)) / 60_000));
  if (r.statut === 'CANCELLED') return r.motifAnnulation ? `Retiré · motif : ${r.motifAnnulation}` : 'Retiré de la séance';
  if (r.statut === 'MISSED') return `Absent · entrée fermée à ${heureCourte(r.fenetre.fermeAt)}`;
  if (r.resultat && r.calledAt) {
    const fin = r.departAt ?? r.decidedAt;
    return `Passé à ${heureCourte(r.calledAt)}${fin ? ` · ${minutes(r.calledAt, fin)} min` : ''}`;
  }
  if (r.departAt) return `Sorti à ${heureCourte(r.departAt)}`;
  if (r.calledAt) return `Appelé à ${heureCourte(r.calledAt)}`;
  if (r.presentAt) return `Arrivé à ${heureCourte(r.presentAt)} · attend depuis ${minutes(r.presentAt, new Date().toISOString())} min`;
  if (r.fenetre.etat === 'FERMEE') return `Pas arrivé · entrée fermée à ${heureCourte(r.fenetre.fermeAt)}`;
  if (r.fenetre.etat === 'OUVERTE') return `Pas arrivé · entrée jusqu’à ${heureCourte(r.fenetre.fermeAt)}`;
  return `Convoqué à ${heureCourte(r.convocationAt)}`;
}

function RetirerCandidat({ r, onFermer }: { r: ReservationStaff; onFermer: () => void }) {
  const invalider = useInvalider();
  const [motif, setMotif] = useState('');
  const mut = useMutation({
    mutationFn: () =>
      api(`/admin/douane/reservations/${r.id}/annulation`, { method: 'POST', body: { motif: motif.trim() } }),
    onSuccess: () => {
      invalider();
      toast.success(`${r.candidat.minecraftUsername} retiré de la séance`, {
        description: 'Le candidat est prévenu avec le motif.',
      });
      onFermer();
    },
    onError: (e) => toast.error('Retrait impossible', { description: messageErreur(e) }),
  });
  const pret = motif.trim().length >= 3;
  return (
    <Dialog
      ouvert
      alerte
      onFermer={onFermer}
      titre={`Retirer ${r.candidat.minecraftUsername} de la séance ?`}
      description="Sa place est libérée ; il est prévenu dans son launcher avec le motif."
      pied={
        <>
          <Button variante="ghost" onClick={onFermer}>
            Garder
          </Button>
          <Button variante="danger-solid" disabled={!pret} enCours={mut.isPending} onClick={() => mut.mutate()}>
            Retirer de la séance
          </Button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (pret) mut.mutate();
        }}
      >
        <Field label="Motif du retrait" compteur="Visible du candidat">
          {(id) => (
            <Input
              id={id}
              data-autofocus
              value={motif}
              maxLength={500}
              onChange={(e) => setMotif(e.target.value)}
              placeholder="Ex. : comportement au hall, place libérée à sa demande…"
            />
          )}
        </Field>
      </form>
    </Dialog>
  );
}

// ──────────────────────────── Actions sur la séance ────────────────────────────

function ModifierSeance({ s, ouvert, onFermer }: { s: SeanceStaffDetail; ouvert: boolean; onFermer: () => void }) {
  const invalider = useInvalider();
  const [capacite, setCapacite] = useState(s.capacite);
  const [choisis, setChoisis] = useState<string[]>(s.examinateurs.map((e) => e.id));
  const { data: examinateurs } = useQuery({
    queryKey: ['admin', 'examinateurs'],
    queryFn: () => api<ExaminateurPropose[]>('/admin/examinateurs'),
    staleTime: 5 * 60_000,
    enabled: ouvert,
  });

  const eligibles = useMemo(() => new Set((examinateurs ?? []).map((e) => e.id)), [examinateurs]);
  // Les comptes de la séance qui ne sont plus examinateurs restent affichés pour être décochés.
  const pastilles = useMemo(() => {
    if (!examinateurs) return null;
    const anciens = s.examinateurs.filter((e) => !eligibles.has(e.id)).map((e) => ({ compte: e, eligible: false }));
    return [...examinateurs.map((e) => ({ compte: e, eligible: true })), ...anciens];
  }, [examinateurs, eligibles, s.examinateurs]);

  const initiaux = s.examinateurs.map((e) => e.id);
  const listeModifiee = choisis.length !== initiaux.length || choisis.some((x) => !initiaux.includes(x));
  const nonEligibles = examinateurs ? choisis.filter((x) => !eligibles.has(x)).length : 0;
  const bloquee = listeModifiee && nonEligibles > 0;
  const capaciteValide = capacite >= Math.max(CAPACITE_BORNES.min, s.placesPrises) && capacite <= CAPACITE_BORNES.max;

  const mut = useMutation({
    mutationFn: () =>
      api<SeanceStaffDetail>(`/admin/douane/seances/${s.id}`, {
        method: 'PATCH',
        body: listeModifiee ? { capacite, examinateurIds: choisis.filter((x) => eligibles.has(x)) } : { capacite },
      }),
    onSuccess: () => {
      invalider();
      toast.success('Séance modifiée');
      onFermer();
    },
    onError: (e) => toast.error('Séance non modifiée', { description: messageErreur(e) }),
  });

  return (
    <Dialog
      ouvert={ouvert}
      onFermer={onFermer}
      titre="Modifier la séance"
      description="L’horaire ne change plus dès qu’un candidat est convoqué : annule la séance (les candidats sont prévenus) et planifies-en une autre."
      pied={
        <>
          <Button variante="ghost" onClick={onFermer}>
            Annuler
          </Button>
          <Button variante="primary" disabled={!capaciteValide || bloquee} enCours={mut.isPending} onClick={() => mut.mutate()}>
            Enregistrer
          </Button>
        </>
      }
    >
      <Field
        label="Capacité"
        aide={s.placesPrises > 0 ? `${pluriel(s.placesPrises, 'place')} déjà prise${s.placesPrises > 1 ? 's' : ''} : pas en dessous.` : undefined}
      >
        {(id) => (
          <Input
            id={id}
            type="number"
            className="w-28"
            min={Math.max(CAPACITE_BORNES.min, s.placesPrises)}
            max={CAPACITE_BORNES.max}
            value={capacite}
            onChange={(e) => setCapacite(Math.round(Number(e.target.value) || 0))}
          />
        )}
      </Field>
      <div className="field">
        <span className="label">Examinateurs</span>
        {!pastilles ? (
          <span className="help">Chargement…</span>
        ) : (
          <div className="flex flex-wrap gap-2" role="group" aria-label="Examinateurs">
            {pastilles.map(({ compte: e, eligible }) => (
              <Chip
                key={e.id}
                actif={choisis.includes(e.id)}
                onClick={() => setChoisis((c) => (c.includes(e.id) ? c.filter((x) => x !== e.id) : [...c, e.id]))}
              >
                {nomCompte(e)}
                {!eligible && ' · n’est plus examinateur'}
              </Chip>
            ))}
          </div>
        )}
        {bloquee && <span className="error-text">Décoche les comptes qui ne sont plus examinateurs pour enregistrer la liste.</span>}
      </div>
    </Dialog>
  );
}

function CloreSeance({ s, ouvert, onFermer }: { s: SeanceStaffDetail; ouvert: boolean; onFermer: () => void }) {
  const invalider = useInvalider();
  const [notes, setNotes] = useState('');
  const restantes = s.compte.BOOKED;
  const mut = useMutation({
    mutationFn: () =>
      api<SeanceStaffDetail>(`/admin/douane/seances/${s.id}/cloture`, {
        method: 'POST',
        body: { notes: notes.trim() || undefined },
      }),
    onSuccess: () => {
      invalider();
      toast.success('Séance close');
      onFermer();
    },
    onError: (e) => toast.error('Clôture impossible', { description: messageErreur(e) }),
  });
  return (
    <Dialog
      ouvert={ouvert}
      onFermer={onFermer}
      titre="Clore la séance"
      description={
        restantes > 0
          ? `${pluriel(restantes, 'place')} encore réservée${restantes > 1 ? 's' : ''} : arrivée enregistrée = annulée sans pénalité, sinon manquée. Les candidats sont prévenus.`
          : 'Toutes les places sont soldées.'
      }
      pied={
        <>
          <Button variante="ghost" onClick={onFermer}>
            Annuler
          </Button>
          <Button variante="primary" enCours={mut.isPending} onClick={() => mut.mutate()}>
            Clore la séance
          </Button>
        </>
      }
    >
      <Field label="Notes de clôture" compteur="Privé · pour l’équipe">
        {(id) => (
          <Textarea
            id={id}
            data-autofocus
            rows={5}
            maxLength={4000}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Ce qui s’est bien passé, ce qui a coincé, les candidats à suivre…"
          />
        )}
      </Field>
    </Dialog>
  );
}

function AnnulerSeance({ s, ouvert, onFermer }: { s: SeanceStaffDetail; ouvert: boolean; onFermer: () => void }) {
  const invalider = useInvalider();
  const { peut } = useDroits();
  const [motif, setMotif] = useState('');
  const touches = s.compte.BOOKED;
  const mut = useMutation({
    mutationFn: () =>
      api<SeanceStaffDetail>(`/admin/douane/seances/${s.id}/annulation`, {
        method: 'POST',
        body: { motif: motif.trim() },
      }),
    onSuccess: () => {
      invalider();
      toast.success('Séance annulée', {
        description: touches > 0 ? `${pluriel(touches, 'candidat')} prévenu${touches > 1 ? 's' : ''}.` : undefined,
      });
      onFermer();
    },
    onError: (e) => toast.error('Annulation impossible', { description: messageErreur(e) }),
  });
  const pret = motif.trim().length >= 3 && peut('douane.organiser');
  return (
    <Dialog
      ouvert={ouvert}
      alerte
      onFermer={onFermer}
      titre="Annuler la séance ?"
      description={
        touches > 0
          ? `${pluriel(touches, 'place')} réservée${touches > 1 ? 's' : ''} : chaque candidat est prévenu dans son launcher et peut réserver une autre séance.`
          : 'Aucune place réservée.'
      }
      pied={
        <>
          <Button variante="ghost" onClick={onFermer}>
            Garder la séance
          </Button>
          <Button variante="danger-solid" disabled={!pret} enCours={mut.isPending} onClick={() => mut.mutate()}>
            Annuler la séance
          </Button>
        </>
      }
    >
      <Field label="Motif" compteur="Visible des candidats">
        {(id) => (
          <Textarea
            id={id}
            data-autofocus
            rows={3}
            maxLength={500}
            value={motif}
            onChange={(e) => setMotif(e.target.value)}
            placeholder="Ex. : examinateurs indisponibles, séance reportée à samedi…"
          />
        )}
      </Field>
    </Dialog>
  );
}
