'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { use, useEffect, useRef, useState, type ReactNode } from 'react';
import { toast } from 'sonner';
import { usePrise } from '@/components/AssignmentBlock';
import { ChatPanel, type ChatPanelRef } from '@/components/ChatPanel';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { BlocDouane } from '@/components/candidature/BlocDouane';
import {
  NotePriveeEcrit,
  PanneauDecision,
  type EtatApresDecision,
  type NotePriveeRef,
  type PanneauDecisionRef,
} from '@/components/candidature/DecisionEcrit';
import { OralHorsJeu } from '@/components/candidature/OralHorsJeu';
import { TexteLong } from '@/components/candidature/TexteLong';
import { EVENEMENT_VOIX, VoixLecteur } from '@/components/candidature/VoixLecteur';
import { Tag } from '@/components/ui/Badge';
import { BoutonIcone, Button } from '@/components/ui/Button';
import { ListeKV } from '@/components/ui/Card';
import { Drawer, useConfirm } from '@/components/ui/Dialog';
import { Icone } from '@/components/ui/Icone';
import { Kbd } from '@/components/ui/Kbd';
import { MenuPlus } from '@/components/ui/Menu';
import { Chargement, ErrorState, messageErreur } from '@/components/ui/States';
import { api } from '@/lib/api';
import {
  LIBELLE_DECOUVERTE,
  LIBELLE_NARUTO,
  LIBELLE_RP_YEARS,
  LIBELLE_SEXE,
  LIBELLE_TYPE_PERSO,
  dateHeure,
  jourLong,
  trancheAge,
} from '@/lib/candidature';
import { ongletDossiers, useOrdreDossiers } from '@/lib/dossiers';
import { useDroits } from '@/lib/droits';
import { attenteDepuis } from '@/lib/format';
import { libelleGrade } from '@/lib/libelles';
import { preferences } from '@/lib/preferences';
import { useRaccourcis } from '@/lib/raccourcis';
import type { WhitelistDetail } from '@/lib/types';

/**
 * Fiche candidature (PANEL_V2 §4.3) : à gauche le dossier (ancres HRP ·
 * Personnage · Histoire · Voix, touches 1 à 4), à droite le panneau de
 * décision collant (décision par partie, « Valider l'écrit »), le bloc Douane
 * (séance, oral, secours hors jeu) et la note privée. Les échanges avec le
 * candidat sont dans un tiroir. Après une décision : « Décision enregistrée ·
 * Suivante », et la suivante s'ouvre si « Enchaîner » est actif.
 *
 * Première whitelist : aucun clan (tous les clans passent par une session en
 * jeu). Le dossier montre le nom de famille choisi, jamais un clan.
 */

type Ancre = 'hrp' | 'perso' | 'histoire' | 'voix';
const ANCRES: Array<{ cle: Ancre; label: string; touche: string }> = [
  { cle: 'hrp', label: 'HRP', touche: '1' },
  { cle: 'perso', label: 'Personnage', touche: '2' },
  { cle: 'histoire', label: 'Histoire', touche: '3' },
  { cle: 'voix', label: 'Voix', touche: '4' },
];

export default function FicheCandidaturePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Garde capacites={['candidatures.relire']}>
      <Fiche id={id} />
    </Garde>
  );
}

function Fiche({ id }: { id: string }) {
  const router = useRouter();
  const recherche = useSearchParams();
  const qc = useQueryClient();
  const { confirmer } = useConfirm();
  const { peut } = useDroits();
  const onglet = ongletDossiers(recherche.get('onglet'));
  const ordre = useOrdreDossiers(onglet);
  const decision = useRef<PanneauDecisionRef>(null);
  const note = useRef<NotePriveeRef>(null);
  const chat = useRef<ChatPanelRef>(null);
  const [echanges, setEchanges] = useState(false);
  const [ancre, setAncre] = useState<Ancre>('hrp');
  const [bandeau, setBandeau] = useState<{
    resume: string;
    suivante: string | null;
    /** Partie qui attend encore une décision : pas d'enchaînement. */
    reste: 'HRP' | 'RP' | null;
  } | null>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'whitelist', id],
    queryFn: () => api<WhitelistDetail>(`/admin/whitelist/${id}`),
    // Fiche ouverte : 10 s (les messages du candidat arrivent sans recharger).
    refetchInterval: 10_000,
  });

  const { prendre } = usePrise('whitelist', id);

  // Nouvelle fiche : bandeau et tiroir remis à zéro.
  useEffect(() => {
    setBandeau(null);
    setEchanges(false);
    setAncre('hrp');
  }, [id]);

  // Ancre active selon le défilement.
  useEffect(() => {
    const els = ANCRES.map((a) => document.getElementById(`dossier-${a.cle}`)).filter(
      (e): e is HTMLElement => !!e,
    );
    if (els.length === 0) return;
    const obs = new IntersectionObserver(
      (entrees) => {
        const visible = entrees
          .filter((e) => e.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
        if (visible) setAncre(visible.target.id.replace('dossier-', '') as Ancre);
      },
      { rootMargin: '-80px 0px -55% 0px' },
    );
    els.forEach((e) => obs.observe(e));
    return () => obs.disconnect();
  }, [data?.id]);

  const ids = ordre.data ?? [];
  const position = ids.indexOf(id);
  const precedente = position > 0 ? ids[position - 1]! : null;
  const suivante = position >= 0 ? (ids[position + 1] ?? null) : (ids.find((x) => x !== id) ?? null);
  const lien = (cible: string) => `/whitelist/${cible}?onglet=${onglet.cle}`;

  function allerA(a: Ancre) {
    setAncre(a);
    document.getElementById(`dossier-${a}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  function apresDecision(resume: string, etat: EtatApresDecision) {
    // La plus proche dans l'onglet, sans la fiche qui vient d'être tranchée.
    const restantes = ids.filter((x) => x !== id);
    const prochaine = position >= 0 ? (ids.slice(position + 1).find((x) => x !== id) ?? restantes[0] ?? null) : (restantes[0] ?? null);
    // Une seule partie tranchée et l'autre attend : on reste sur la fiche,
    // même avec « Enchaîner » (sinon le dossier part à moitié tranché).
    if (etat.fini && preferences().enchainer && prochaine) {
      toast.message(`${resume} · dossier suivant`);
      router.push(lien(prochaine));
      return;
    }
    setBandeau({ resume, suivante: prochaine, reste: etat.fini ? null : etat.reste });
  }

  const reset = useMutation({
    mutationFn: () =>
      api<{ ok: boolean; roleDemoted: boolean }>(`/admin/whitelist/${id}/reset`, { method: 'POST' }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
      toast.success('Candidature remise à zéro', {
        description: r.roleDemoted
          ? 'Le joueur repasse joueur et peut envoyer une nouvelle candidature.'
          : 'Grade staff conservé : le joueur peut envoyer une nouvelle candidature.',
      });
      router.push('/whitelist');
    },
    onError: (e) => toast.error('Remise à zéro impossible', { description: messageErreur(e) }),
  });

  const partieEnLecture = ancre === 'hrp' ? 'HRP' : 'RP';
  useRaccourcis(
    [
      { touche: '1', action: () => allerA('hrp') },
      { touche: '2', action: () => allerA('perso') },
      { touche: '3', action: () => allerA('histoire') },
      { touche: '4', action: () => allerA('voix') },
      { touche: ' ', action: () => window.dispatchEvent(new Event(EVENEMENT_VOIX)) },
      { touche: 'v', action: () => decision.current?.valider() },
      { touche: 'r', action: () => decision.current?.ouvrir(partieEnLecture, 'NEEDS_REVISION') },
      { touche: 'n', maj: false, action: () => suivante && router.push(lien(suivante)) },
      { touche: 'n', maj: true, action: () => precedente && router.push(lien(precedente)) },
      { touche: 'e', action: () => setEchanges(true) },
      { touche: 'o', action: () => note.current?.ouvrir() },
      {
        touche: 'p',
        action: () => {
          if (data && !data.assignee) prendre.mutate(false);
        },
      },
    ],
    !!data,
  );

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
        <ErrorState erreur={error ?? new Error('Candidature introuvable.')} onReessayer={() => void refetch()} />
      </div>
    );
  }

  const v1 = data.formVersion < 2;
  const nom = `${data.firstName} ${data.lastName}`.trim();
  // Pastille « Échanges » : messages du candidat restés sans réponse du staff
  // (après le dernier message STAFF), pas le total de la conversation.
  const dernierStaff = data.messages.map((m) => m.authorType).lastIndexOf('STAFF');
  const nbMessages = data.messages
    .slice(dernierStaff + 1)
    .filter((m) => m.authorType === 'USER').length;
  const ouverteAuxMessages = data.status === 'PENDING' || data.status === 'NEEDS_REVISION';

  const entreesPlus = [
    ...(peut('joueurs.voir')
      ? [
          {
            cle: 'joueur',
            label: 'Ouvrir la fiche du joueur',
            icone: 'user' as const,
            onClick: () => router.push(`/players/${data.user.id}`),
          },
        ]
      : []),
    ...(peut('candidatures.reinitialiser')
      ? [
          {
            cle: 'reset',
            label: 'Remettre la candidature à zéro',
            icone: 'rotate-ccw' as const,
            danger: true,
            onClick: async () => {
              const ok = await confirmer({
                titre: `Remettre à zéro la candidature de ${data.user.minecraftUsername} ?`,
                message:
                  'Supprime définitivement la candidature (messages et voix compris). Le joueur pourra en envoyer une nouvelle. S’il est whitelisté, il redevient joueur ; un grade staff est conservé.',
                confirmer: 'Remettre à zéro',
                danger: true,
                retaper: data.user.minecraftUsername,
              });
              if (ok) reset.mutate();
            },
          },
        ]
      : []),
  ];

  return (
    <div className="page">
      {/* En-tête */}
      <header className="ph">
        <div className="min-w-0 flex-1">
          <nav className="crumbs" aria-label="Fil d’Ariane">
            <Link href="/whitelist">Candidatures</Link>
            <Icone nom="chevron-right" taille={12} />
            <Link href={`/whitelist?onglet=${onglet.cle}`}>Dossiers</Link>
            <Icone nom="chevron-right" taille={12} />
            <span>{nom}</span>
          </nav>
          <h1 className="ph-title">{nom}</h1>
          <div className="ph-sub flex flex-wrap items-center gap-x-2 gap-y-1">
            <MCAvatar uuid={data.user.minecraftUuid} size={20} />
            {peut('joueurs.voir') ? (
              <Link href={`/players/${data.user.id}`} className="font-medium text-fg lien-discret">
                {data.user.minecraftUsername}
              </Link>
            ) : (
              <span className="font-medium text-fg">{data.user.minecraftUsername}</span>
            )}
            <span>· {data.village}</span>
            <span title={dateHeure(data.submittedAt)}>· envoyée il y a {attenteDepuis(data.submittedAt)}</span>
            {data.writtenApprovedAt && <span>· écrit validé le {jourLong(data.writtenApprovedAt)}</span>}
            {v1 && <Tag title="Envoyée avec l’ancien launcher">ancien formulaire</Tag>}
          </div>
        </div>
        <div className="ph-act">
          <Button icone="message-square" onClick={() => setEchanges(true)} title="Échanges · E">
            Échanges
            {nbMessages > 0 && (
              <span className="compte-or" title="Messages du candidat sans réponse">
                {nbMessages}
              </span>
            )}
          </Button>
          {position >= 0 && ids.length > 1 && (
            <span className="inline-flex items-center gap-1.5">
              <BoutonIcone
                icone="chevron-left"
                variante="secondary"
                label="Dossier précédent · Maj N"
                disabled={!precedente}
                onClick={() => precedente && router.push(lien(precedente))}
              />
              <span className="tabular text-[12px] text-fg-3">
                {position + 1} / {ids.length}
              </span>
              <BoutonIcone
                icone="chevron-right"
                variante="secondary"
                label="Dossier suivant · N"
                disabled={!suivante}
                onClick={() => suivante && router.push(lien(suivante))}
              />
            </span>
          )}
          <MenuPlus entrees={entreesPlus} />
        </div>
      </header>

      {bandeau && (
        <div className="mb-4 flex flex-wrap items-center gap-3 rounded-[8px] border border-border-strong bg-surface-2 px-4 py-3 text-[13px] text-fg" role="status">
          <Icone nom="check" taille={16} className="text-success" />
          <span className="flex-1">
            Décision enregistrée · {bandeau.resume}
            {bandeau.reste && <> · reste la partie {bandeau.reste}</>}
          </span>
          {bandeau.suivante ? (
            <Link href={lien(bandeau.suivante)} className="mot inline-flex items-center gap-2">
              Suivante ({Math.max(0, ids.filter((x) => x !== id).length)}) <Kbd>N</Kbd>
            </Link>
          ) : (
            <Link href={`/whitelist?onglet=${onglet.cle}`} className="mot">
              Retour aux dossiers
            </Link>
          )}
        </div>
      )}

      <div className="split">
        {/* ── Le dossier ── */}
        <div className="stack">
          <nav className="anchors" aria-label="Parties du dossier">
            {ANCRES.map((a) => (
              <button
                key={a.cle}
                type="button"
                aria-current={ancre === a.cle ? 'true' : undefined}
                onClick={() => allerA(a.cle)}
              >
                {a.label} <Kbd>{a.touche}</Kbd>
              </button>
            ))}
            <span className="ml-auto pr-2 text-[12px] text-fg-3">Formulaire v{data.formVersion}</span>
          </nav>

          <Section id="hrp" titre="Partie HRP">
            <Faits
              faits={[
                data.playerAge !== null
                  ? ['Âge déclaré', `${data.playerAge} ans`]
                  : data.dob
                    ? ['Date de naissance', new Date(data.dob).toLocaleDateString('fr-FR')]
                    : null,
                data.rpYears ? ['Roleplay', LIBELLE_RP_YEARS[data.rpYears]] : null,
                data.narutoKnowledge ? ['Univers Naruto', LIBELLE_NARUTO[data.narutoKnowledge]] : null,
                !v1 ? ['Découverte', data.discoverySource ? LIBELLE_DECOUVERTE[data.discoverySource] : 'Pas de réponse'] : null,
                ['Discord', data.user.discordUsername ? `@${data.user.discordUsername}` : 'non lié'],
                ['Grade du compte', libelleGrade(data.user.role)],
              ]}
            />
            <h4>Disponibilités</h4>
            <p className="candidature-prose whitespace-pre-wrap">{data.availability}</p>
            {data.motivation && (
              <>
                <h4>Pourquoi Reborn ? (ancien formulaire)</h4>
                <TexteLong texte={data.motivation} />
              </>
            )}
            {data.experience && (
              <>
                <h4>Expérience du roleplay (ancien formulaire)</h4>
                <TexteLong texte={data.experience} />
              </>
            )}
          </Section>

          <Section id="perso" titre="Personnage">
            <Faits
              faits={[
                ['Prénom', data.firstName],
                // Ancien formulaire : ce champ contenait en pratique le clan, non retenu.
                [v1 ? 'Nom (ancien formulaire, clan non retenu)' : 'Nom de famille', data.lastName],
                ['Village', data.village],
                data.characterType ? ['Type', LIBELLE_TYPE_PERSO[data.characterType]] : null,
                data.characterSex ? ['Sexe', LIBELLE_SEXE[data.characterSex]] : null,
                data.characterAge !== null
                  ? ['Âge', `${data.characterAge} ans · ${trancheAge(data.characterAge)}`]
                  : null,
              ]}
            />
            {!v1 && (
              <p className="help mt-2">
                Première candidature : pas de clan. Les clans se rejoignent en jeu, en session de clan.
              </p>
            )}
            {data.appearance && (
              <>
                <h4>Caractère et apparence (ancien formulaire)</h4>
                <TexteLong texte={data.appearance} />
              </>
            )}
          </Section>

          <Section id="histoire" titre="Histoire">
            <h4 className="mt-0">Histoire du personnage</h4>
            <TexteLong texte={data.history} max={v1 ? 4000 : 12000} markdown={!v1} lignesRepliees={14} />
            <h4>Objectifs roleplay</h4>
            <TexteLong texte={data.objectives} max={8000} />
            {data.support && (
              <>
                <h4>Support visuel</h4>
                {/^https:\/\//i.test(data.support) ? (
                  <a href={data.support} target="_blank" rel="noopener noreferrer nofollow" className="lien break-all text-[14px]">
                    {data.support}
                  </a>
                ) : (
                  <p className="text-[14px] text-fg-2">{data.support}</p>
                )}
              </>
            )}
          </Section>

          <Section id="voix" titre="Voix">
            <VoixLecteur
              applicationId={data.id}
              voix={data.voix}
              voiceDeletedAt={data.voiceDeletedAt}
              voiceConsentAt={data.voiceConsentAt}
            />
          </Section>

          <Section id="suivi" titre="Suivi de l’écrit">
            <ListeKV
              largeur={150}
              lignes={[
                { cle: 'envoi', label: 'Envoyée le', valeur: dateHeure(data.submittedAt) },
                data.reviewedAt
                  ? {
                      cle: 'relue',
                      label: 'Relue le',
                      valeur: `${dateHeure(data.reviewedAt)}${data.reviewedBy ? ` · par ${data.reviewedBy.minecraftUsername}` : ''}`,
                    }
                  : null,
                data.writtenApprovedAt
                  ? { cle: 'acceptee', label: 'Écrit accepté', valeur: dateHeure(data.writtenApprovedAt) }
                  : null,
                data.rulesAcceptedAt
                  ? { cle: 'regles', label: 'Règlement', valeur: `accepté le ${dateHeure(data.rulesAcceptedAt)}` }
                  : null,
                data.suivi.partiesRouvertes.length > 0
                  ? {
                      cle: 'rouvert',
                      label: 'Rouvert au candidat',
                      valeur: data.suivi.partiesRouvertes.map((p) => `partie ${p}`).join(' et '),
                    }
                  : null,
                data.suivi.phase === 'REFUSE' && data.suivi.canReapplyAt
                  ? {
                      cle: 'reprise',
                      label: 'Nouvelle candidature',
                      valeur: `possible le ${jourLong(data.suivi.canReapplyAt)}`,
                    }
                  : null,
                data.user.oralPassedAt
                  ? { cle: 'oral', label: 'Oral réussi', valeur: dateHeure(data.user.oralPassedAt) }
                  : null,
              ]}
            />
            <h4>Commentaire de l’écrit · visible du candidat</h4>
            <p className="candidature-prose whitespace-pre-wrap">
              {data.reviewNotes ?? <span className="text-fg-3">Aucun commentaire.</span>}
            </p>
          </Section>
        </div>

        {/* ── Décision ── */}
        <aside className="stack collant" aria-label="Décision">
          <PanneauDecision ref={decision} data={data} onDecision={apresDecision} />
          <BlocDouane applicationId={data.id} suivi={data.suivi}>
            <OralHorsJeu id={data.id} phase={data.phase} douaneEnJeu={data.suivi.douaneEnJeu} />
          </BlocDouane>
          <NotePriveeEcrit ref={note} id={data.id} note={data.writtenStaffNotes} />
          <div className="hints px-1">
            <Kbd>1</Kbd>–<Kbd>4</Kbd> parties · <Kbd>Espace</Kbd> voix · <Kbd>N</Kbd> suivante ·{' '}
            <Kbd>?</Kbd> aide
          </div>
        </aside>
      </div>

      {/* Écran étroit (moins de 1 024 px) : le panneau de décision passe sous le
          dossier ; une barre « Décider » collée en bas y mène (au-dessus de la
          barre du bas du téléphone). */}
      <div className="h-16 lg:hidden" aria-hidden />
      <div className="fixed inset-x-0 bottom-0 z-20 flex items-center gap-3 border-t border-border bg-surface px-4 py-3 max-md:bottom-14 lg:hidden">
        <span className="min-w-0 flex-1 truncate text-[13px] text-fg-2">{nom}</span>
        <Button icone="message-square" taille="sm" onClick={() => setEchanges(true)}>
          Échanges
        </Button>
        <Button
          variante="primary"
          taille="sm"
          onClick={() =>
            document.getElementById('titre-decision')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
          }
        >
          Décider
        </Button>
      </div>

      <Drawer
        ouvert={echanges}
        onFermer={() => setEchanges(false)}
        titre="Échanges"
        description={`Avec ${data.user.minecraftUsername}${data.discordThreadId ? ' · relayés sur le fil Discord' : ''}`}
        corpsBrut
      >
        <ChatPanel
            ref={chat}
            endpoint={`/admin/whitelist/${data.id}/messages`}
            queryKey={['admin', 'whitelist', data.id]}
            messages={data.messages}
            canSend={ouverteAuxMessages}
            playerUuid={data.user.minecraftUuid}
            playerName={data.user.minecraftUsername}
            roleJoueur="Candidat"
            emptyLabel="Aucun message : le candidat peut t’écrire depuis son launcher."
            closedLabel={`Candidature ${data.status === 'APPROVED' ? 'acceptée' : 'refusée'} : conversation close.`}
          />
      </Drawer>
    </div>
  );
}

function Section({ id, titre, children }: { id: string; titre: string; children: ReactNode }) {
  return (
    <section className="card dsec" id={`dossier-${id}`} aria-labelledby={`titre-${id}`}>
      <h2 id={`titre-${id}`} className="eyebrow mb-3.5">
        {titre}
      </h2>
      {children}
    </section>
  );
}

function Faits({ faits }: { faits: Array<[string, string] | null> }) {
  const liste = faits.filter((f): f is [string, string] => !!f);
  return (
    <dl className="facts">
      {liste.map(([dt, dd]) => (
        <div key={dt}>
          <dt>{dt}</dt>
          <dd>{dd}</dd>
        </div>
      ))}
    </dl>
  );
}
