'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { ChangerGrade, peutChangerGrade } from '@/components/ChangerGrade';
import { CodeSixCases } from '@/components/CodeSixCases';
import { MCAvatar } from '@/components/MCAvatar';
import { ReglagesTabs } from '@/components/shell/SectionTabs';
import { Badge, BadgeStatut } from '@/components/ui/Badge';
import { Button, Mot } from '@/components/ui/Button';
import { Card, ListeKV } from '@/components/ui/Card';
import { DataTable, type Colonne } from '@/components/ui/DataTable';
import { InputRecherche } from '@/components/ui/Field';
import { Interrupteur } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { Kbd } from '@/components/ui/Kbd';
import { PageHeader } from '@/components/ui/PageHeader';
import { Callout, Chargement, EmptyState, messageErreur } from '@/components/ui/States';
import { Segmented } from '@/components/ui/Tabs';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { dateHeure, ilYa } from '@/lib/format';
import { estStaff, libelleGrade } from '@/lib/libelles';
import { MOTIFS_CANDIDATURE, REPONSES_TYPES } from '@/lib/modeles';
import { usePreferences } from '@/lib/preferences';
import { statutCompte } from '@/lib/statuts';
import type { PlayerListItem, Role } from '@/lib/types';

/**
 * Réglages (PANEL_V2 §4.7), en pied de barre : Compte (profil, double
 * authentification), Préférences (propres à chaque membre, dans le
 * navigateur), Équipe (administrateurs : changer un grade), Modèles
 * (motifs et réponses types communs).
 */
export default function ReglagesPage() {
  const params = useSearchParams();
  const { peut } = useDroits();
  const onglet = params.get('onglet') ?? 'compte';
  const sousTitre: Record<string, string> = {
    compte: 'Ton compte et la sécurité de ton accès au panel',
    preferences: 'Tes préférences, gardées dans ce navigateur',
    equipe: 'Grades du staff · réservé aux administrateurs',
    modeles: 'Motifs de candidature et réponses types, communs à tout le staff',
  };
  return (
    <div className="page">
      <PageHeader titre="Réglages" sousTitre={sousTitre[onglet] ?? sousTitre.compte} />
      <ReglagesTabs />
      {onglet === 'preferences' ? (
        <Preferences />
      ) : onglet === 'equipe' && peut('grades.changer') ? (
        <Equipe />
      ) : onglet === 'modeles' && peut('reponses-types.gerer') ? (
        <Modeles />
      ) : (
        <Compte />
      )}
    </div>
  );
}

// ──────────────────────────── Compte ────────────────────────────

interface EtatDoubleAuth {
  enabled: boolean;
}
interface Enrolement {
  secret: string;
  otpauthUri: string;
  qrDataUrl: string;
}

function Compte() {
  const { moi } = useDroits();
  return (
    <div className="narrow">
      <Card titre="Profil">
        {!moi ? (
          <Chargement />
        ) : (
          <div className="flex flex-wrap items-center gap-5">
            <MCAvatar uuid={moi.minecraftUuid} size={56} />
            <ListeKV
              largeur={140}
              lignes={[
                { cle: 'pseudo', label: 'Pseudo Minecraft', valeur: moi.minecraftUsername },
                { cle: 'nom', label: 'Nom affiché', valeur: moi.displayName ?? '—' },
                { cle: 'grade', label: 'Grade', valeur: libelleGrade(moi.role) },
                {
                  cle: 'discord',
                  label: 'Discord',
                  valeur: moi.discord ? `@${moi.discord.username}` : <span className="text-fg-3">non lié</span>,
                },
              ]}
            />
          </div>
        )}
      </Card>
      <DoubleAuth />
    </div>
  );
}

function DoubleAuth() {
  const qc = useQueryClient();
  const { data: etat, isLoading } = useQuery({
    queryKey: ['admin', '2fa', 'status'],
    queryFn: () => api<EtatDoubleAuth>('/auth/2fa/status'),
  });
  const [enrolement, setEnrolement] = useState<Enrolement | null>(null);
  const [code, setCode] = useState('');
  const [desactiver, setDesactiver] = useState(false);
  const [codeDesactivation, setCodeDesactivation] = useState('');

  const enroler = useMutation({
    mutationFn: () => api<Enrolement>('/auth/2fa/enroll', { method: 'POST' }),
    onSuccess: (d) => {
      setEnrolement(d);
      setCode('');
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });
  const confirmer = useMutation({
    mutationFn: (c: string) => api('/auth/2fa/confirm', { method: 'POST', body: { code: c } }),
    onSuccess: () => {
      toast.success('Double authentification activée', {
        description: 'Un code te sera demandé à chaque connexion.',
      });
      setEnrolement(null);
      setCode('');
      qc.invalidateQueries({ queryKey: ['admin', '2fa', 'status'] });
    },
    onError: (e) => toast.error('Code refusé', { description: messageErreur(e) }),
  });
  const couper = useMutation({
    mutationFn: (c: string) => api('/auth/2fa/disable', { method: 'POST', body: { code: c } }),
    onSuccess: () => {
      toast.success('Double authentification désactivée');
      setDesactiver(false);
      setCodeDesactivation('');
      qc.invalidateQueries({ queryKey: ['admin', '2fa', 'status'] });
    },
    onError: (e) => toast.error('Code refusé', { description: messageErreur(e) }),
  });

  return (
    <Card
      titre="Double authentification"
      action={
        !isLoading && etat ? (
          <Badge ton={etat.enabled ? 'succes' : 'attention'}>{etat.enabled ? 'Activée' : 'Non activée'}</Badge>
        ) : undefined
      }
    >
      {isLoading ? (
        <Chargement />
      ) : etat?.enabled ? (
        desactiver ? (
          <form
            className="flex flex-col gap-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (codeDesactivation.length === 6) couper.mutate(codeDesactivation);
            }}
          >
            <p className="text-[13px] text-fg-2">Saisis ton code actuel pour désactiver la double authentification.</p>
            <CodeSixCases valeur={codeDesactivation} onChange={setCodeDesactivation} autoFocus />
            <div className="flex gap-2">
              <Button type="submit" variante="danger-outline" disabled={codeDesactivation.length < 6} enCours={couper.isPending}>
                Désactiver
              </Button>
              <Button variante="ghost" onClick={() => setDesactiver(false)}>
                Annuler
              </Button>
            </div>
          </form>
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-[13px] text-fg-2">Un code à six chiffres t’est demandé après la connexion Discord.</p>
            <Mot ton="danger" onClick={() => setDesactiver(true)}>
              Désactiver
            </Mot>
          </div>
        )
      ) : enrolement ? (
        <div className="flex flex-col gap-4">
          <p className="text-[13px] text-fg-2">
            Scanne ce QR code avec ton application d’authentification, puis saisis le code à six chiffres affiché.
          </p>
          <div className="flex flex-wrap items-start gap-4">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={enrolement.qrDataUrl} alt="QR code de double authentification" width={168} height={168} className="rounded-[8px] bg-white" />
            <div className="min-w-0 flex-1 text-[12px] text-fg-3">
              Sans appareil photo, saisis cette clé à la main :
              <code className="mono mt-1 block break-all rounded-[6px] bg-surface-2 px-2 py-1 text-fg-2">{enrolement.secret}</code>
            </div>
          </div>
          <form
            className="flex flex-col gap-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (code.length === 6) confirmer.mutate(code);
            }}
          >
            <CodeSixCases valeur={code} onChange={setCode} autoFocus />
            <div className="flex gap-2">
              <Button type="submit" variante="primary" disabled={code.length < 6} enCours={confirmer.isPending}>
                Activer
              </Button>
              <Button variante="ghost" onClick={() => setEnrolement(null)}>
                Annuler
              </Button>
            </div>
          </form>
        </div>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="max-w-[52ch] text-[13px] text-fg-2">
            Recommandée pour tout le staff, indispensable pour les administrateurs : un code à six chiffres est demandé
            après la connexion Discord.
          </p>
          <Button variante="primary" enCours={enroler.isPending} onClick={() => enroler.mutate()}>
            Activer
          </Button>
        </div>
      )}
    </Card>
  );
}

// ──────────────────────────── Préférences ────────────────────────────

function Preferences() {
  const [prefs, maj] = usePreferences();
  return (
    <div className="narrow">
      <Card titre="Affichage">
        <div className="set">
          <div>
            <div className="set-l">Densité des tableaux</div>
            <div className="set-h">Compacte : lignes de 36 px. Confortable : lignes de 44 px.</div>
          </div>
          <div className="set-c">
            <Segmented
              label="Densité des tableaux"
              valeur={prefs.densite}
              onChange={(v) => maj({ densite: v })}
              options={[
                { valeur: 'compacte', label: 'Compacte' },
                { valeur: 'confortable', label: 'Confortable' },
              ]}
            />
          </div>
        </div>
      </Card>
      <Card titre="Travail">
        <LignePreference
          titre="Enchaîner après une décision"
          aide="Ouvre le dossier suivant du même onglet après « Valider » ou « À revoir »."
          actif={prefs.enchainer}
          onChange={(v) => maj({ enchainer: v })}
        />
        <LignePreference
          titre="Raccourcis clavier"
          aide={
            <>
              Inactifs dans un champ de saisie, sauf avec Ctrl. <Kbd>?</Kbd> ouvre la liste.
            </>
          }
          actif={prefs.raccourcis}
          onChange={(v) => maj({ raccourcis: v })}
        />
        <LignePreference
          titre="Prendre automatiquement"
          aide="Une décision ou une première réponse te donne le dossier ou le ticket s’il n’est à personne."
          actif={prefs.priseAuto}
          onChange={(v) => maj({ priseAuto: v })}
        />
      </Card>
      <Card titre="Seuils de retard" meta="Pour toute l’équipe">
        <div className="set">
          <div>
            <div className="set-l">Écrit en attente</div>
            <div className="set-h">Au-delà, l’attente passe en rouge dans la file et dans le compteur du menu.</div>
          </div>
          <div className="set-c tabular text-fg">48 heures</div>
        </div>
        <div className="set">
          <div>
            <div className="set-l">Ticket sans prise en charge</div>
          </div>
          <div className="set-c tabular text-fg">24 heures</div>
        </div>
        <p className="help mt-3 flex items-center gap-1.5">
          <Icone nom="lock" taille={14} /> Réglables par un administrateur quand le réglage d’équipe sera branché.
        </p>
      </Card>
    </div>
  );
}

function LignePreference({
  titre,
  aide,
  actif,
  onChange,
}: {
  titre: string;
  aide: React.ReactNode;
  actif: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="set">
      <div>
        <div className="set-l">{titre}</div>
        <div className="set-h">{aide}</div>
      </div>
      <div className="set-c">
        <Interrupteur actif={actif} onChange={onChange} label={titre} />
      </div>
    </div>
  );
}

// ──────────────────────────── Équipe ────────────────────────────

function Equipe() {
  const { moi, peut } = useDroits();
  const [saisie, setSaisie] = useState('');
  const [q, setQ] = useState('');
  const [cible, setCible] = useState<{ id: string; pseudo: string; role: Role } | null>(null);
  useEffect(() => {
    const t = setTimeout(() => setQ(saisie.trim()), 250);
    return () => clearTimeout(t);
  }, [saisie]);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'players', 'search', 'equipe', q],
    queryFn: () =>
      api<PlayerListItem[]>(`/admin/players/search?take=100${q ? `&q=${encodeURIComponent(q)}` : ''}`),
    placeholderData: keepPreviousData,
  });
  // Sans recherche : le staff parmi les derniers connectés ; avec : tous les comptes trouvés.
  const lignes = (data ?? []).filter((j) => (q ? true : estStaff(j.role)));

  const colonnes: Colonne<PlayerListItem>[] = [
    {
      cle: 'membre',
      titre: 'Compte',
      rendu: (j) => (
        <span className="who">
          <MCAvatar uuid={j.minecraftUuid} size={26} />
          <span className="two min-w-0">
            <span className="block truncate font-medium text-fg">
              {j.minecraftUsername}
              {moi?.id === j.id && <span className="ml-1 font-normal text-fg-3">· toi</span>}
            </span>
            {j.discordUsername && <span className="sub2 truncate">@{j.discordUsername}</span>}
          </span>
        </span>
      ),
    },
    { cle: 'grade', titre: 'Grade', serre: true, rendu: (j) => libelleGrade(j.role) },
    { cle: 'statut', titre: 'Statut', serre: true, cacherEtroit: true, rendu: (j) => <BadgeStatut statut={statutCompte(j)} /> },
    {
      cle: 'activite',
      titre: 'Dernière connexion',
      serre: true,
      rendu: (j) => (j.lastLoginAt ? <span title={dateHeure(j.lastLoginAt)}>{ilYa(j.lastLoginAt)}</span> : 'jamais'),
    },
    {
      cle: 'action',
      titre: <span className="sr-only">Action</span>,
      align: 'droite',
      serre: true,
      rendu: (j) =>
        peutChangerGrade(moi, { id: j.id, role: j.role }, peut('grades.changer')) ? (
          <Mot onClick={() => setCible({ id: j.id, pseudo: j.minecraftUsername, role: j.role })}>Changer de grade</Mot>
        ) : (
          <span className="text-fg-3">—</span>
        ),
    },
  ];

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <InputRecherche
          value={saisie}
          onChange={(e) => setSaisie(e.target.value)}
          placeholder="Pseudo, Discord ou UUID"
          aria-label="Rechercher un compte"
          conteneur="w-full sm:w-96"
        />
        <span className="help">
          {q ? 'Tous les comptes trouvés : un joueur peut être promu.' : 'Le staff parmi les 100 derniers connectés.'}
        </span>
      </div>
      <DataTable<PlayerListItem>
        label="Membres du staff"
        colonnes={colonnes}
        lignes={lignes}
        cle={(j) => j.id}
        chargement={isLoading}
        erreur={error as Error | null}
        onReessayer={() => void refetch()}
        vide={<EmptyState icone="users" titre={q ? `Aucun compte pour « ${q} ».` : 'Aucun membre du staff récemment connecté.'} aide="Cherche un pseudo pour le retrouver." />}
        pied={<span>Un grade s’affiche en mot. Chaque changement est inscrit au journal.</span>}
      />
      <div className="mt-4">
        <Callout ton="neutre">
          La liste complète de l’équipe (double authentification, dernière activité) arrivera avec sa route dédiée.
        </Callout>
      </div>
      {cible && <ChangerGrade ouvert onFermer={() => setCible(null)} cible={cible} />}
    </>
  );
}

// ──────────────────────────── Modèles ────────────────────────────

type FiltreModeles = 'tous' | 'candidatures' | 'tickets';

function Modeles() {
  const [filtre, setFiltre] = useState<FiltreModeles>('tous');
  const lignes = [
    ...MOTIFS_CANDIDATURE.map((m) => ({ cle: `m:${m.id}`, nom: m.nom, type: 'Motif de candidature', texte: m.texte, genre: 'candidatures' as const })),
    ...REPONSES_TYPES.map((r) => ({ cle: `r:${r.code}`, nom: `/${r.code}`, type: 'Réponse de ticket', texte: r.texte, genre: 'tickets' as const })),
  ].filter((l) => filtre === 'tous' || l.genre === filtre);
  type Ligne = (typeof lignes)[number];
  const colonnes: Colonne<Ligne>[] = [
    { cle: 'nom', titre: 'Nom', principal: true, serre: true, rendu: (l) => (l.genre === 'tickets' ? <span className="mono text-gold">{l.nom}</span> : l.nom) },
    { cle: 'type', titre: 'Type', serre: true, cacherEtroit: true, rendu: (l) => l.type },
    { cle: 'texte', titre: 'Texte', rendu: (l) => <span className="block max-w-[640px] text-fg-2">{l.texte}</span> },
  ];
  return (
    <>
      <div className="mb-4">
        <Segmented<FiltreModeles>
          label="Modèles affichés"
          valeur={filtre}
          onChange={setFiltre}
          options={[
            { valeur: 'tous', label: 'Tous', n: MOTIFS_CANDIDATURE.length + REPONSES_TYPES.length },
            { valeur: 'candidatures', label: 'Candidatures', n: MOTIFS_CANDIDATURE.length },
            { valeur: 'tickets', label: 'Tickets', n: REPONSES_TYPES.length },
          ]}
        />
      </div>
      <DataTable<Ligne>
        label="Modèles de texte"
        colonnes={colonnes}
        lignes={lignes}
        cle={(l) => l.cle}
        pied={<span>Un modèle se modifie avant envoi : c’est un point de départ, pas un texte figé.</span>}
      />
      <div className="mt-4">
        <Callout ton="neutre">
          Jeu commun livré avec le panel. Créer et modifier les modèles depuis cette page arrivera avec leur route
          d’enregistrement.
        </Callout>
      </div>
    </>
  );
}
