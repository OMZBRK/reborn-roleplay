'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { toast } from 'sonner';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { SectionTabs } from '@/components/shell/SectionTabs';
import { BadgeStatut } from '@/components/ui/Badge';
import { Button, ButtonLink, Mot } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { DataTable, type Colonne, type GroupeLignes } from '@/components/ui/DataTable';
import { Drawer } from '@/components/ui/Dialog';
import { Field, Input } from '@/components/ui/Field';
import { PageHeader } from '@/components/ui/PageHeader';
import { Callout, EmptyState, ErrorState, messageErreur } from '@/components/ui/States';
import { Chip, Segmented } from '@/components/ui/Tabs';
import { api } from '@/lib/api';
import { heureCourte, jourLong, nomCompte, partiesParis } from '@/lib/candidature';
import { useDroits } from '@/lib/droits';
import {
  CAPACITE_BORNES,
  DUREE_SEANCE_BORNES_MIN,
  capaciteConseillee,
  horaireSeance,
  placesParVague,
  vaguesConvocation,
  valeurDate,
} from '@/lib/douane';
import { pluriel } from '@/lib/format';
import { STATUT_SEANCE_DOUANE } from '@/lib/statuts';
import type { ExaminateurPropose, SeanceStaff, SeanceStaffDetail } from '@/lib/types';

/**
 * Candidatures › Séances de douane (PANEL_V2 §4.3, §6.5) : la séance du jour
 * en tête, dans une carte (pas répétée dans le tableau), puis la liste par
 * semaine. « Planifier une séance » ouvre un tiroir prérempli avec les heures
 * et les examinateurs de la dernière séance ; la capacité suit le repère
 * examinateurs × heures × 4. Un verbe par sens : planifier (créer), voir
 * (ouvrir la fiche).
 */

type Onglet = 'A_VENIR' | 'TERMINEE' | 'ANNULEE' | 'TOUTES';

/** Historique chargé pour les onglets autres que « À venir ». */
const HISTORIQUE_JOURS = 90;

export default function SeancesDouanePage() {
  return (
    <Garde capacites={['douane.organiser']}>
      <Seances />
    </Garde>
  );
}

function Seances() {
  const router = useRouter();
  const params = useSearchParams();
  const { moi } = useDroits();
  const [onglet, setOnglet] = useState<Onglet>('A_VENIR');
  const [planifier, setPlanifier] = useState(false);
  const demandePlanifier = params.get('planifier') === '1';

  // `?planifier=1` (action « Planifier une séance » de Ctrl K) ouvre le
  // tiroir, au montage comme quand on est déjà sur la page.
  useEffect(() => {
    if (demandePlanifier) setPlanifier(true);
  }, [demandePlanifier]);

  // L'historique sert aussi à préremplir « Planifier une séance ».
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'douane', 'seances', 'historique'],
    queryFn: () =>
      api<SeanceStaff[]>(
        `/admin/douane/seances?take=200&depuis=${encodeURIComponent(
          new Date(Date.now() - HISTORIQUE_JOURS * 86_400_000).toISOString(),
        )}`,
      ),
    refetchInterval: 30_000,
  });

  const toutes = useMemo(() => data ?? [], [data]);
  const maintenant = Date.now();
  const aujourdhui = valeurDate(new Date());
  const duJour = toutes.filter(
    (s) => valeurDate(new Date(s.startAt)) === aujourdhui && s.statut !== 'ANNULEE',
  );

  const filtrees = useMemo(() => {
    const l = toutes.filter((s) => {
      if (onglet === 'A_VENIR') {
        return (s.statut === 'OUVERTE' || s.statut === 'FERMEE') && valeurDate(new Date(s.startAt)) !== aujourdhui;
      }
      if (onglet === 'TERMINEE') return s.statut === 'TERMINEE';
      if (onglet === 'ANNULEE') return s.statut === 'ANNULEE';
      return true;
    });
    return [...l].sort((a, b) =>
      onglet === 'A_VENIR' ? a.startAt.localeCompare(b.startAt) : b.startAt.localeCompare(a.startAt),
    );
  }, [toutes, onglet, aujourdhui]);

  const groupes = grouperParSemaine(filtrees);
  const cetteSemaine = toutes.filter(
    (s) => s.statut !== 'ANNULEE' && semaineDe(new Date(s.startAt)) === semaineDe(new Date()),
  );
  const places = cetteSemaine.reduce((n, s) => n + s.capacite, 0);
  const reservees = cetteSemaine.reduce((n, s) => n + s.placesPrises, 0);

  const colonnes: Colonne<SeanceStaff>[] = [
    {
      cle: 'jour',
      titre: 'Jour',
      principal: true,
      serre: true,
      rendu: (s) => <span className="first-letter:uppercase inline-block">{jourLong(s.startAt)}</span>,
    },
    {
      cle: 'horaire',
      titre: 'Horaire',
      serre: true,
      rendu: (s) => (
        <span className="tabular">
          {heureCourte(s.startAt)} – {heureCourte(s.endAt)}
        </span>
      ),
    },
    { cle: 'inscrits', titre: 'Inscrits', serre: true, rendu: (s) => <Remplissage s={s} /> },
    {
      cle: 'examinateurs',
      titre: 'Examinateurs',
      cacherEtroit: true,
      rendu: (s) =>
        s.examinateurs.length > 0 ? (
          <span className="flex min-w-0 items-center gap-2.5">
            <Visages comptes={s.examinateurs} />
            <span className="ellip">
              {s.examinateurs.map((e) => (moi?.id === e.id ? 'toi' : nomCompte(e))).join(', ')}
            </span>
          </span>
        ) : (
          <span className="text-fg-3">aucun</span>
        ),
    },
    {
      cle: 'etat',
      titre: 'État',
      serre: true,
      rendu: (s) =>
        s.statut === 'TERMINEE' ? (
          <span className="text-fg-3">
            {s.compte.DONE} passés · {s.compte.MISSED} absents
          </span>
        ) : (
          <BadgeStatut statut={STATUT_SEANCE_DOUANE[s.statut]} />
        ),
    },
    {
      cle: 'voir',
      titre: <span className="sr-only">Action</span>,
      align: 'droite',
      serre: true,
      rendu: (s) => (
        <Link href={`/douane/${s.id}`} className="mot">
          Voir
        </Link>
      ),
    },
  ];

  return (
    <div className="page">
      <PageHeader
        fil={[{ label: 'Candidatures', href: '/whitelist' }]}
        titre="Candidatures"
        sousTitre={
          data ? (
            <>
              <b>{pluriel(cetteSemaine.length, 'séance')}</b> cette semaine · {pluriel(places, 'place')} ·{' '}
              {pluriel(reservees, 'réservée')}
            </>
          ) : (
            'Séances de douane'
          )
        }
        actions={
          <Button variante="primary" icone="plus" onClick={() => setPlanifier(true)}>
            Planifier une séance
          </Button>
        }
      />
      <SectionTabs section="candidatures" />

      {error && !data && <ErrorState erreur={error} onReessayer={() => void refetch()} />}

      {duJour.map((s) => (
        <CarteDuJour key={s.id} s={s} moiId={moi?.id} enCours={maintenant >= Date.parse(s.startAt) && maintenant <= Date.parse(s.endAt)} />
      ))}

      <div className="mb-3 mt-2 flex flex-wrap items-center gap-3">
        <Segmented<Onglet>
          label="Séances affichées"
          valeur={onglet}
          onChange={setOnglet}
          options={[
            { valeur: 'A_VENIR', label: 'À venir' },
            { valeur: 'TERMINEE', label: 'Closes' },
            { valeur: 'ANNULEE', label: 'Annulées' },
            { valeur: 'TOUTES', label: 'Toutes' },
          ]}
        />
        {onglet !== 'A_VENIR' && <span className="help">{HISTORIQUE_JOURS} derniers jours</span>}
      </div>

      <DataTable<SeanceStaff>
        label="Séances de douane"
        colonnes={colonnes}
        groupes={groupes}
        cle={(s) => s.id}
        chargement={isLoading}
        onOuvrir={(s) => router.push(`/douane/${s.id}`)}
        estAttenue={(s) => s.statut === 'TERMINEE' || s.statut === 'ANNULEE'}
        vide={
          <EmptyState
            icone="landmark"
            titre={onglet === 'A_VENIR' ? 'Aucune autre séance à venir.' : 'Aucune séance dans cet onglet.'}
            aide={onglet === 'A_VENIR' ? 'Planifie la prochaine : le tiroir reprend les heures et les examinateurs de la dernière.' : undefined}
          />
        }
      />

      <PlanifierSeance
        ouvert={planifier}
        onFermer={() => {
          setPlanifier(false);
          if (params.get('planifier')) router.replace('/douane');
        }}
        derniere={[...toutes].filter((s) => s.statut !== 'ANNULEE').sort((a, b) => b.startAt.localeCompare(a.startAt))[0] ?? null}
        historiquePret={!isLoading}
      />
    </div>
  );
}

function Remplissage({ s }: { s: SeanceStaff }) {
  const ratio = s.capacite > 0 ? Math.min(1, s.placesPrises / s.capacite) : 0;
  return (
    <span className="fillbar">
      <span className="fb" aria-hidden>
        <i style={{ width: `${ratio * 100}%` }} />
      </span>
      {s.placesPrises} / {s.capacite}
    </span>
  );
}

/** Visages empilés des examinateurs (trois au plus), nom en info-bulle. */
function Visages({ comptes }: { comptes: Array<{ id: string; minecraftUsername: string; discordUsername: string | null }> }) {
  return (
    <span className="faces" aria-hidden>
      {comptes.slice(0, 3).map((c) => (
        <span key={c.id} title={nomCompte(c)}>
          <MCAvatar uuid={c.minecraftUsername} size={22} />
        </span>
      ))}
    </span>
  );
}

function CarteDuJour({ s, moiId, enCours }: { s: SeanceStaff; moiId?: string; enCours: boolean }) {
  const examinateur = !!moiId && s.examinateurs.some((e) => e.id === moiId);
  return (
    <Card corps={false} className="mb-4 border-border-strong">
      <div className="flex flex-wrap items-center gap-x-8 gap-y-4 px-5 py-4">
        <div className="min-w-0 flex-1">
          <div className="eyebrow">{enCours ? 'En cours' : 'Aujourd’hui'}</div>
          <div className="mt-1 text-[18px] font-semibold text-fg first-letter:uppercase">
            {jourLong(s.startAt)} · {heureCourte(s.startAt)} – {heureCourte(s.endAt)}
          </div>
          <div className="text-[12px] text-fg-3">
            {s.vagues.length > 1
              ? `${s.vagues.length} vagues : ${s.vagues.map((v) => heureCourte(v)).join(', ')}`
              : `Convocation unique à ${heureCourte(s.startAt)}`}
          </div>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[12px] text-fg-3">Inscrits</span>
          <Remplissage s={s} />
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[12px] text-fg-3">Examinateurs</span>
          <span className="inline-flex items-center gap-2 text-[13px] text-fg-2">
            {s.examinateurs.length > 0 && <Visages comptes={s.examinateurs} />}
            {s.examinateurs.length === 0
              ? 'aucun'
              : `${s.examinateurs.length}${examinateur ? ', dont toi' : ''}`}
          </span>
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[12px] text-fg-3">État</span>
          <BadgeStatut statut={STATUT_SEANCE_DOUANE[s.statut]} />
        </div>
        <ButtonLink href={`/douane/${s.id}`}>Voir la séance</ButtonLink>
      </div>
    </Card>
  );
}

// ──────────────────────────── Planifier une séance ────────────────────────────

function hhmm(iso: string): string {
  const { h, mi } = partiesParis(new Date(iso));
  return `${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`;
}

/** Identifiant du formulaire : le bouton du pied (hors du formulaire) l'envoie, Entrée aussi. */
const FORM_PLANIFIER = 'form-planifier';

function PlanifierSeance({
  ouvert,
  onFermer,
  derniere,
  historiquePret,
}: {
  ouvert: boolean;
  onFermer: () => void;
  derniere: SeanceStaff | null;
  /** Liste des séances chargée : sans elle, `derniere` n'est pas encore connue. */
  historiquePret: boolean;
}) {
  const qc = useQueryClient();
  const router = useRouter();
  const { moi } = useDroits();
  const { data: examinateurs, error: erreurExaminateurs } = useQuery({
    queryKey: ['admin', 'examinateurs'],
    queryFn: () => api<ExaminateurPropose[]>('/admin/examinateurs'),
    staleTime: 5 * 60_000,
    enabled: ouvert,
  });

  const [jour, setJour] = useState(() => valeurDate(new Date()));
  const [debut, setDebut] = useState('21:00');
  const [fin, setFin] = useState('23:00');
  const [choisis, setChoisis] = useState<string[]>([]);
  const [capacite, setCapacite] = useState(8);
  const [capaciteManuelle, setCapaciteManuelle] = useState(false);
  const [prerempli, setPrerempli] = useState(false);

  // Prérempli à l'ouverture : heures et examinateurs de la dernière séance.
  // On attend AUSSI la liste des séances (ouverture directe par Ctrl K : elle
  // arrive parfois après les examinateurs) ; sinon les valeurs par défaut
  // seraient posées puis figées.
  useEffect(() => {
    if (!ouvert) {
      setPrerempli(false);
      return;
    }
    if (prerempli || !examinateurs || !historiquePret) return;
    const d = derniere ? hhmm(derniere.startAt) : '21:00';
    const f = derniere ? hhmm(derniere.endAt) : '23:00';
    setDebut(d);
    setFin(f);
    const eligibles = new Set(examinateurs.map((e) => e.id));
    const ids = derniere
      ? derniere.examinateurs.map((e) => e.id).filter((x) => eligibles.has(x))
      : moi && eligibles.has(moi.id)
        ? [moi.id]
        : [];
    setChoisis(ids);
    // Si l'heure de début est passée aujourd'hui, on propose demain.
    const auj = horaireSeance(valeurDate(new Date()), d, f);
    setJour(
      auj && auj.startAt.getTime() > Date.now()
        ? valeurDate(new Date())
        : valeurDate(new Date(Date.now() + 86_400_000)),
    );
    setCapaciteManuelle(false);
    setPrerempli(true);
  }, [ouvert, prerempli, examinateurs, historiquePret, derniere, moi]);

  const horaire = horaireSeance(jour, debut, fin);
  const dureeMin = horaire ? Math.round((horaire.endAt.getTime() - horaire.startAt.getTime()) / 60_000) : 0;
  const vagues = horaire ? vaguesConvocation(horaire.startAt, horaire.endAt) : [];
  const repere = horaire ? capaciteConseillee(horaire.startAt, horaire.endAt, choisis.length) : null;

  // Tant que la capacité n'est pas saisie à la main, elle suit le repère.
  useEffect(() => {
    if (!capaciteManuelle && repere !== null) {
      setCapacite(Math.min(CAPACITE_BORNES.max, Math.max(CAPACITE_BORNES.min, repere)));
    }
  }, [repere, capaciteManuelle]);

  const erreur = !horaire
    ? 'Renseigne le jour, le début et la fin.'
    : dureeMin < DUREE_SEANCE_BORNES_MIN.min || dureeMin > DUREE_SEANCE_BORNES_MIN.max
      ? `Une séance dure de ${DUREE_SEANCE_BORNES_MIN.min} min à ${DUREE_SEANCE_BORNES_MIN.max / 60} h.`
      : horaire.endAt.getTime() <= Date.now()
        ? 'Cette séance serait déjà terminée.'
        : capacite < CAPACITE_BORNES.min || capacite > CAPACITE_BORNES.max
          ? `La capacité va de ${CAPACITE_BORNES.min} à ${CAPACITE_BORNES.max} places.`
          : null;

  const mut = useMutation({
    mutationFn: () =>
      api<SeanceStaffDetail>('/admin/douane/seances', {
        method: 'POST',
        body: {
          startAt: horaire!.startAt.toISOString(),
          endAt: horaire!.endAt.toISOString(),
          capacite,
          examinateurIds: choisis,
        },
      }),
    onSuccess: (s) => {
      qc.invalidateQueries({ queryKey: ['admin', 'douane'] });
      toast.success('Séance planifiée', {
        description: `${jourLong(s.startAt)}, ${heureCourte(s.startAt)} – ${heureCourte(s.endAt)} · ${s.capacite} places. Les candidats peuvent réserver.`,
        action: { label: 'Voir', onClick: () => router.push(`/douane/${s.id}`) },
      });
      onFermer();
    },
    onError: (e) => toast.error('Séance non planifiée', { description: messageErreur(e) }),
  });

  function basculer(id: string) {
    setChoisis((c) => (c.includes(id) ? c.filter((x) => x !== id) : [...c, id]));
  }

  return (
    <Drawer
      ouvert={ouvert}
      onFermer={onFermer}
      titre="Planifier une séance"
      description={
        derniere
          ? `Prérempli d’après la séance du ${jourLong(derniere.startAt)}`
          : 'Heures de Paris'
      }
      pied={
        <>
          <Button variante="ghost" onClick={onFermer}>
            Annuler
          </Button>
          <Button
            type="submit"
            form={FORM_PLANIFIER}
            variante="primary"
            className="ml-auto"
            disabled={erreur !== null || !prerempli}
            enCours={mut.isPending}
            raccourci="Entrée"
          >
            Planifier la séance
          </Button>
        </>
      }
    >
      <form
        id={FORM_PLANIFIER}
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          if (!erreur && prerempli && !mut.isPending) mut.mutate();
        }}
      >
        <Field label="Jour">
          {(id) => <Input id={id} type="date" value={jour} onChange={(e) => setJour(e.target.value)} data-autofocus />}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Début">
            {(id) => <Input id={id} type="time" value={debut} onChange={(e) => setDebut(e.target.value)} />}
          </Field>
          <Field label="Fin">
            {(id) => <Input id={id} type="time" value={fin} onChange={(e) => setFin(e.target.value)} />}
          </Field>
        </div>

        <div className="field">
          <span className="label">Examinateurs</span>
          {erreurExaminateurs ? (
            <ErrorState erreur={erreurExaminateurs} />
          ) : !examinateurs ? (
            <span className="help">Chargement…</span>
          ) : examinateurs.length === 0 ? (
            <span className="help">Aucun compte au grade Recruteur ou au-dessus.</span>
          ) : (
            <div className="flex flex-wrap gap-2" role="group" aria-label="Examinateurs">
              {examinateurs.map((e) => (
                <Chip key={e.id} actif={choisis.includes(e.id)} onClick={() => basculer(e.id)}>
                  {moi?.id === e.id ? 'Toi' : nomCompte(e)}
                </Chip>
              ))}
            </div>
          )}
          <span className="help">
            En jeu, l’examinateur doit aussi avoir son grade staff et la permission d’examinateur, avec ce compte Minecraft.
          </span>
        </div>

        <Field
          label="Capacité"
          aide={
            repere !== null ? (
              <>
                Proposée : {choisis.length} examinateur{choisis.length > 1 ? 's' : ''} ×{' '}
                {Math.round((dureeMin / 60) * 10) / 10} h × 4 = {repere}
                {capaciteManuelle && capacite !== repere && (
                  <>
                    {' · '}
                    <Mot onClick={() => setCapaciteManuelle(false)}>Reprendre {repere}</Mot>
                  </>
                )}
              </>
            ) : (
              'Choisis au moins un examinateur pour la capacité proposée.'
            )
          }
        >
          {(id) => (
            <Input
              id={id}
              type="number"
              className="w-28"
              min={CAPACITE_BORNES.min}
              max={CAPACITE_BORNES.max}
              value={capacite}
              onChange={(e) => {
                setCapaciteManuelle(true);
                setCapacite(Math.round(Number(e.target.value) || 0));
              }}
            />
          )}
        </Field>

        <div className="field">
          <span className="label">Vagues de convocation</span>
          {horaire ? (
            <span className="text-[13px] text-fg-2">
              {vagues.length > 1
                ? `${vagues.length} vagues, ≈ ${placesParVague(capacite, vagues.length)} places chacune : `
                : 'Tout le monde est convoqué au début : '}
              <span className="tabular text-fg">{vagues.map((v) => heureCourte(v)).join(', ')}</span>
            </span>
          ) : (
            <span className="help">—</span>
          )}
        </div>

        {erreur && <p className="error-text">{erreur}</p>}

        <Callout>
          La séance apparaît dans le launcher dès qu’elle est planifiée : les candidats dont l’écrit est validé y réservent leur place.
        </Callout>
      </form>
    </Drawer>
  );
}

// ──────────────────────────── Regroupement par semaine ────────────────────────────

/** Lundi de la semaine (date de Paris) d'un instant, au format AAAA-MM-JJ. */
function semaineDe(d: Date): string {
  const p = partiesParis(d);
  const utc = new Date(Date.UTC(p.an, p.mois - 1, p.jour));
  const jourSemaine = (utc.getUTCDay() + 6) % 7; // lundi = 0
  utc.setUTCDate(utc.getUTCDate() - jourSemaine);
  return utc.toISOString().slice(0, 10);
}

function grouperParSemaine(seances: SeanceStaff[]): GroupeLignes<SeanceStaff>[] {
  const actuelle = semaineDe(new Date());
  const precedente = semaineDe(new Date(Date.now() - 7 * 86_400_000));
  const suivante = semaineDe(new Date(Date.now() + 7 * 86_400_000));
  const groupes: GroupeLignes<SeanceStaff>[] = [];
  for (const s of seances) {
    const cle = semaineDe(new Date(s.startAt));
    let g = groupes.find((x) => x.cle === cle);
    if (!g) {
      const lundi = new Date(`${cle}T12:00:00Z`);
      const dimanche = new Date(lundi.getTime() + 6 * 86_400_000);
      const mois = (d: Date) => d.toLocaleDateString('fr-FR', { month: 'long', timeZone: 'UTC' });
      const jour = (d: Date) => (d.getUTCDate() === 1 ? '1er' : String(d.getUTCDate()));
      const plage =
        lundi.getUTCMonth() === dimanche.getUTCMonth()
          ? `${jour(lundi)} – ${jour(dimanche)} ${mois(dimanche)}`
          : `${jour(lundi)} ${mois(lundi)} – ${jour(dimanche)} ${mois(dimanche)}`;
      const titre =
        cle === actuelle
          ? `Cette semaine · ${plage}`
          : cle === precedente
            ? `Semaine dernière · ${plage}`
            : cle === suivante
              ? `Semaine prochaine · ${plage}`
              : `Semaine du ${plage}`;
      g = { cle, titre, lignes: [] };
      groupes.push(g);
    }
    g.lignes.push(s);
  }
  return groupes.map((g) => ({ ...g, n: g.lignes.length }));
}
