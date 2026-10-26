'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { usePrise } from '@/components/AssignmentBlock';
import { MCAvatar } from '@/components/MCAvatar';
import { Mot } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { DataTable, type Colonne, type GroupeLignes } from '@/components/ui/DataTable';
import { Icone } from '@/components/ui/Icone';
import { Kbd } from '@/components/ui/Kbd';
import { PageHeader } from '@/components/ui/PageHeader';
import { EmptyState, ErrorState } from '@/components/ui/States';
import { Chip, Segmented } from '@/components/ui/Tabs';
import {
  useEtatServeur,
  useFileATraiter,
  useSeancesDuJour,
  type ElementFile,
  type TypeElement,
} from '@/lib/a-traiter';
import { heureCourte, jourLong } from '@/lib/candidature';
import { useDroits } from '@/lib/droits';
import { attenteDepuis, dateHeure, nombre, pluriel } from '@/lib/format';
import { pageArrivee } from '@/lib/navigation';

/**
 * À traiter (PANEL_V2 §4.1) : une seule file de tout ce qui attend une
 * action du staff — écrits à relire, tickets, connexions suspectes — en
 * retard d'abord, puis du plus ancien au plus récent ; « à moi » en premier.
 * La séance de douane du jour est dans la bande « Aujourd'hui » : c'est un
 * rendez-vous, pas une tâche. Remplace le tableau de bord et « Mes prises ».
 */

type Filtre = 'tout' | 'moi' | 'nonpris';

const LIBELLE_TYPE: Record<TypeElement, string> = {
  ecrit: 'Candidatures',
  ticket: 'Tickets',
  alerte: 'Alertes',
};

export default function ATraiterPage() {
  const router = useRouter();
  const params = useSearchParams();
  const { capacites, peut } = useDroits();
  const f = useFileATraiter();
  const aUneFile = f.voit.ecrits || f.voit.tickets || f.voit.alertes;

  // Les grades sans file (modélisateur) arrivent sur leur section. Juste après
  // la connexion (`?arrivee=1`), la page d'arrivée suit le grade : le
  // développeur part sur Techniques, mais peut revenir ici par la barre.
  const arrivee = params.get('arrivee') === '1';
  useEffect(() => {
    const cible = pageArrivee(capacites);
    if (!aUneFile || (arrivee && cible !== '/dashboard')) router.replace(cible);
    else if (arrivee) router.replace('/dashboard');
  }, [aUneFile, arrivee, capacites, router]);

  const filtreUrl = params.get('filtre');
  const [filtre, setFiltre] = useState<Filtre>(
    filtreUrl === 'moi' || filtreUrl === 'nonpris' ? filtreUrl : 'tout',
  );
  const [types, setTypes] = useState<TypeElement[]>([]);

  const parType = (l: ElementFile[]) =>
    types.length === 0 ? l : l.filter((e) => types.includes(e.type));

  const aMoi = parType(f.aMoi);
  const nonPris = parType(f.nonPris);
  const autres = parType(f.autres);

  const groupes: GroupeLignes<ElementFile>[] = [];
  if (filtre !== 'nonpris') {
    groupes.push({ cle: 'moi', titre: 'À moi', n: aMoi.length, lignes: aMoi });
  }
  if (filtre !== 'moi') {
    groupes.push({ cle: 'nonpris', titre: 'Non pris', n: nonPris.length, lignes: nonPris });
  }
  if (filtre === 'tout') {
    const noms = [...new Set(autres.map((e) => e.assignee?.minecraftUsername).filter(Boolean))];
    groupes.push({
      cle: 'autres',
      titre: 'Pris par d’autres',
      n: autres.length,
      lignes: autres,
      replie: true,
      resume: noms.slice(0, 4).join(', '),
    });
  }

  const totalVisible = aMoi.length + nonPris.length;
  const comptesType = useMemo(() => {
    const c: Record<TypeElement, number> = { ecrit: 0, ticket: 0, alerte: 0 };
    for (const e of [...f.aMoi, ...f.nonPris]) c[e.type]++;
    return c;
  }, [f.aMoi, f.nonPris]);
  const typesVisibles = (Object.keys(LIBELLE_TYPE) as TypeElement[]).filter(
    (t) =>
      (t === 'ecrit' && f.voit.ecrits) ||
      (t === 'ticket' && f.voit.tickets) ||
      (t === 'alerte' && f.voit.alertes),
  );

  if (!aUneFile) return null;

  const colonnes: Colonne<ElementFile>[] = [
    {
      cle: 'type',
      titre: 'Type',
      serre: true,
      rendu: (e) => (
        <span className="inline-flex items-center gap-2.5 text-fg-2">
          <Icone nom={e.icone} taille={16} className="text-fg-3" />
          {e.libelleType}
        </span>
      ),
    },
    {
      cle: 'sujet',
      titre: 'Sujet',
      rendu: (e) => (
        <span className="two block min-w-0">
          <Link href={e.href} className="block max-w-[420px] truncate font-medium text-fg lien-discret">
            {e.sujet}
          </Link>
          <span className="sub2 truncate">{e.detail}</span>
        </span>
      ),
    },
    {
      cle: 'joueur',
      titre: 'Joueur',
      rendu: (e) =>
        e.joueur ? (
          <span className="who">
            <MCAvatar uuid={e.joueur.uuid ?? e.joueur.pseudo} size={22} />
            {peut('joueurs.voir') && e.joueur.id ? (
              <Link href={`/players/${e.joueur.id}`} className="truncate text-fg lien-discret">
                {e.joueur.pseudo}
              </Link>
            ) : (
              <span className="truncate text-fg">{e.joueur.pseudo}</span>
            )}
          </span>
        ) : (
          <span className="text-fg-3">—</span>
        ),
    },
    {
      cle: 'attente',
      titre: 'Attente',
      align: 'droite',
      serre: true,
      rendu: (e) => (
        <span
          className={`tabular ${e.retard ? 'late' : ''}`}
          title={`Depuis le ${dateHeure(e.depuis)}${e.retard ? ' · au-delà du seuil de retard' : ''}`}
        >
          {attenteDepuis(e.depuis)}
        </span>
      ),
    },
    {
      cle: 'pris',
      titre: 'Pris par',
      cacherEtroit: true,
      rendu: (e) => <PrisPar element={e} />,
    },
    {
      cle: 'action',
      titre: 'Action',
      align: 'droite',
      serre: true,
      rendu: (e) => <ActionLigne element={e} />,
    },
  ];

  return (
    <div className="page">
      <PageHeader
        titre="À traiter"
        sousTitre={
          <>
            <span className="first-letter:uppercase inline-block">{jourLong(new Date())}</span>
            {' · '}
            <b>{pluriel(totalVisible, 'élément')}</b>
            {f.aMoi.length > 0 && <>, dont {nombre(aMoi.length)} à toi</>}
            {' · '}en retard d’abord, puis le plus ancien
          </>
        }
        actions={
          <Segmented<Filtre>
            label="Filtrer la file"
            valeur={filtre}
            onChange={setFiltre}
            options={[
              { valeur: 'tout', label: 'Tout', n: totalVisible },
              { valeur: 'moi', label: 'À moi', n: aMoi.length },
              { valeur: 'nonpris', label: 'Non pris', n: nonPris.length },
            ]}
          />
        }
      />

      <BandeAujourdhui />

      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2" role="group" aria-label="Types">
          {typesVisibles.length > 1 &&
            typesVisibles.map((t) => (
              <Chip
                key={t}
                actif={types.includes(t)}
                onClick={() =>
                  setTypes((l) => (l.includes(t) ? l.filter((x) => x !== t) : [...l, t]))
                }
                icone={t === 'ecrit' ? 'scroll-text' : t === 'ticket' ? 'ticket' : 'shield'}
                n={comptesType[t]}
              >
                {LIBELLE_TYPE[t]}
              </Chip>
            ))}
        </div>
        <div className="hints hidden md:flex">
          <Kbd>↑</Kbd>
          <Kbd>↓</Kbd> choisir · <Kbd>Entrée</Kbd> ouvrir · <Kbd>P</Kbd> prendre
        </div>
      </div>

      {f.erreur && f.elements.length > 0 && (
        <div className="mb-3">
          <ErrorState erreur={f.erreur} onReessayer={f.recharger} />
        </div>
      )}

      <DataTable<ElementFile>
        label="File des éléments à traiter"
        colonnes={colonnes}
        groupes={groupes}
        cle={(e) => e.cle}
        chargement={f.chargement}
        erreur={f.elements.length === 0 ? f.erreur : null}
        onReessayer={f.recharger}
        onOuvrir={(e) => router.push(e.href)}
        onToucheLigne={(ev, e) => {
          if (ev.key.toLowerCase() === 'p' && !ev.ctrlKey && !ev.metaKey && e.prise && !e.assignee) {
            ev.preventDefault();
            ev.currentTarget.querySelector<HTMLButtonElement>('[data-prendre]')?.click();
          }
        }}
        vide={
          <EmptyState
            titre="Rien à traiter."
            aide="Les nouvelles candidatures et les tickets arrivent ici."
          />
        }
      />
    </div>
  );
}

function PrisPar({ element: e }: { element: ElementFile }) {
  const { moi } = useDroits();
  if (!e.assignee) return <span className="text-fg-3">—</span>;
  return (
    <span className="who text-fg-2">
      {/* Visage tiré de l'UUID Minecraft, jamais du pseudo Discord. */}
      <MCAvatar uuid={e.assignee.minecraftUuid} size={22} />
      {moi && e.assignee.id === moi.id ? 'Toi' : e.assignee.minecraftUsername}
    </span>
  );
}

function ActionLigne({ element: e }: { element: ElementFile }) {
  const router = useRouter();
  if (!e.prise || e.assignee) {
    return (
      <Mot onClick={() => router.push(e.href)} aria-label={`Ouvrir : ${e.sujet}`}>
        Ouvrir
      </Mot>
    );
  }
  return <MotPrendre element={e} />;
}

function MotPrendre({ element: e }: { element: ElementFile }) {
  const { prendre } = usePrise(e.prise!.genre, e.prise!.id, {
    statutTicket: e.prise!.statutTicket,
  });
  return (
    <Mot
      data-prendre
      enCours={prendre.isPending}
      onClick={() => prendre.mutate(false)}
      aria-label={`Prendre : ${e.sujet}`}
      title="Prendre · P"
    >
      Prendre
    </Mot>
  );
}

/**
 * Bande « Aujourd'hui », une ligne, seulement si elle a du contenu : la
 * séance de douane du jour (si le grade organise la douane) et l'état du
 * serveur de jeu.
 */
function BandeAujourdhui() {
  const { moi, peut } = useDroits();
  const seances = useSeancesDuJour(peut('douane.organiser'));
  const serveur = useEtatServeur();
  const liste = seances.data ?? [];
  const etat = serveur.data;
  if (liste.length === 0 && !etat) return null;

  return (
    <Card corps={false} className="mb-4">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3 text-[13px] text-fg-2">
        <span className="eyebrow">Aujourd’hui</span>
        {liste.length > 0 && <span className="h-4 w-px bg-border" aria-hidden />}
        {liste.map((s) => {
          const examinateur = !!moi && s.examinateurs.some((x) => x.id === moi.id);
          return (
            <span key={s.id} className="inline-flex flex-wrap items-center gap-2">
              <Icone nom="landmark" taille={16} className="text-fg-3" />
              <b className="font-medium text-fg">Séance de douane {heureCourte(s.startAt)}</b>
              <span className="tabular">
                · {s.placesPrises} / {s.capacite} inscrits
              </span>
              {examinateur && <span>· tu es examinateur</span>}
              <Link href={`/douane/${s.id}`} className="mot">
                Voir
              </Link>
            </span>
          );
        })}
        {etat && (
          <span className="ml-auto inline-flex items-center gap-2">
            <span
              className="badge-dot"
              style={{ background: etat.online ? 'var(--color-success)' : 'var(--color-fg-3)' }}
              aria-hidden
            />
            {etat.online ? `Reborn · ${nombre(etat.players.online)} en ligne` : 'Reborn · serveur hors ligne'}
          </span>
        )}
      </div>
    </Card>
  );
}
