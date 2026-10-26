'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { use, useState } from 'react';
import { toast } from 'sonner';
import { ChangerGrade, peutChangerGrade } from '@/components/ChangerGrade';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { Badge, BadgeStatut } from '@/components/ui/Badge';
import { BoutonIcone, Button, Mot } from '@/components/ui/Button';
import { Card, ListeKV } from '@/components/ui/Card';
import { DataTable, type Colonne } from '@/components/ui/DataTable';
import { useConfirm } from '@/components/ui/Dialog';
import { Icone, type NomIcone } from '@/components/ui/Icone';
import { MenuPlus } from '@/components/ui/Menu';
import { EmptyState, ErrorState, Chargement, Callout, messageErreur } from '@/components/ui/States';
import { Tabs } from '@/components/ui/Tabs';
import { api } from '@/lib/api';
import { STATUT_GLOBAL } from '@/lib/candidature';
import { useDroits } from '@/lib/droits';
import { dateHeure, dateJour, ilYa, pluriel, tempsDeJeu, uuidCourt } from '@/lib/format';
import {
  libelleAction,
  libelleCategorie,
  libelleGrade,
  libelleGradeRp,
  libelleServeur,
  libelleSource,
} from '@/lib/libelles';
import { STATUT_TICKET, statutCompte } from '@/lib/statuts';
import type {
  AuditLogItem,
  IpJoueur,
  LoginAnomaly,
  Paginated,
  PersonnageJoueur,
  PersonnagesJoueur,
  PlayerDetail,
} from '@/lib/types';

/**
 * Fiche joueur unique à onglets (PANEL_V2 §4.2) : carte d'identité fixe,
 * puis Aperçu · Candidature · Personnages · Tickets · Historique · Sécurité.
 * Personnages : synchro de ShinobiCore (tous serveurs). L'onglet Sanctions
 * arrive avec le casier remonté du jeu : il ne s'affiche pas avant.
 * L'IP est masquée ; « Afficher » la lit à la demande et l'inscrit au journal.
 */

type Onglet = 'apercu' | 'candidature' | 'personnages' | 'tickets' | 'historique' | 'securite';

export default function FicheJoueurPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Garde capacites={['joueurs.voir']}>
      <Fiche id={id} />
    </Garde>
  );
}

function Fiche({ id }: { id: string }) {
  const router = useRouter();
  const recherche = useSearchParams();
  const qc = useQueryClient();
  const { confirmer } = useConfirm();
  const { moi, peut, peutUne } = useDroits();
  const [grade, setGrade] = useState(false);

  const { data: j, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'players', id],
    queryFn: () => api<PlayerDetail>(`/admin/players/${id}`),
    refetchInterval: 10_000,
  });
  const persos = useQuery({
    queryKey: ['admin', 'players', id, 'characters'],
    queryFn: () => api<PersonnagesJoueur>(`/admin/players/${id}/characters`),
  });

  const onglets: Array<{ cle: Onglet; label: string; n?: number; icone?: NomIcone }> = [
    { cle: 'apercu', label: 'Aperçu' },
    { cle: 'candidature', label: 'Candidature' },
    { cle: 'personnages', label: 'Personnages', n: persos.data?.characters.length },
    { cle: 'tickets', label: 'Tickets', n: j?.tickets.length },
  ];
  if (peut('audit.voir')) onglets.push({ cle: 'historique', label: 'Historique' });
  if (peut('joueurs.ip')) onglets.push({ cle: 'securite', label: 'Sécurité', icone: 'lock' });

  const demande = recherche.get('onglet') as Onglet | null;
  const onglet: Onglet = onglets.some((o) => o.cle === demande) ? demande! : 'apercu';

  const reset = useMutation({
    mutationFn: (applicationId: string) =>
      api<{ ok: boolean; roleDemoted: boolean }>(`/admin/whitelist/${applicationId}/reset`, {
        method: 'POST',
      }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ['admin', 'players', id] });
      qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
      toast.success('Candidature remise à zéro', {
        description: r.roleDemoted
          ? 'Le joueur repasse joueur et peut envoyer une nouvelle candidature.'
          : 'Grade staff conservé : le joueur peut envoyer une nouvelle candidature.',
      });
    },
    onError: (e) => toast.error('Remise à zéro impossible', { description: messageErreur(e) }),
  });

  if (isLoading) {
    return (
      <div className="page">
        <Chargement lignes={4} />
      </div>
    );
  }
  if (error || !j) {
    return (
      <div className="page">
        <ErrorState erreur={error ?? new Error('Joueur introuvable.')} onReessayer={() => void refetch()} />
      </div>
    );
  }

  const changer = peutChangerGrade(moi, { id: j.id, role: j.role }, peut('grades.changer'));
  const entreesPlus = [];
  if (j.whitelist && peut('candidatures.reinitialiser')) {
    const app = j.whitelist;
    entreesPlus.push({
      cle: 'reset',
      label: 'Remettre la candidature à zéro',
      icone: 'rotate-ccw' as NomIcone,
      danger: true,
      onClick: async () => {
        const ok = await confirmer({
          titre: `Remettre à zéro la candidature de ${j.minecraftUsername} ?`,
          message:
            'Supprime définitivement la candidature (messages et voix compris). S’il est whitelisté, il redevient joueur ; un grade staff est conservé.',
          confirmer: 'Remettre à zéro',
          danger: true,
          retaper: j.minecraftUsername,
        });
        if (ok) reset.mutate(app.id);
      },
    });
  }

  const statut = statutCompte(j);

  return (
    <div className="page">
      {/* Carte d'identité fixe */}
      <Card corps={false} className="mb-5">
        <div className="flex flex-wrap items-center gap-5 px-6 py-5">
          <MCAvatar uuid={j.minecraftUuid} size={64} />
          {/* Base de 220 px : sur téléphone, les actions passent dessous au lieu d'écraser le nom. */}
          <div className="min-w-0 grow basis-[220px]">
            <nav className="crumbs" aria-label="Fil d’Ariane">
              <Link href="/players">Joueurs</Link>
              <Icone nom="chevron-right" taille={12} />
              <span>{j.minecraftUsername}</span>
            </nav>
            <div className="flex flex-wrap items-baseline gap-3">
              <h1 className="ph-title">{j.minecraftUsername}</h1>
              <span className="text-[15px] text-fg-2">{libelleGrade(j.role)}</span>
            </div>
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-fg-2">
              <BadgeStatut statut={statut} />
              <span className="vsep" aria-hidden />
              <span title={j.lastLoginAt ? dateHeure(j.lastLoginAt) : undefined}>
                {j.lastLoginAt ? `Dernière connexion ${ilYa(j.lastLoginAt)}` : 'Jamais connecté'}
              </span>
              {j.discordUsername && (
                <>
                  <span className="vsep" aria-hidden />
                  <span className="text-fg-3">@{j.discordUsername}</span>
                </>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {changer && (
              <Button variante="ghost" onClick={() => setGrade(true)}>
                Changer de grade
              </Button>
            )}
            <MenuPlus entrees={entreesPlus} />
          </div>
        </div>
      </Card>

      {j.banned && (
        <div className="alertline" role="status">
          <Icone nom="triangle-alert" taille={16} />
          <span>
            <b>Banni</b>
            {j.bannedUntil ? ` jusqu’au ${dateHeure(j.bannedUntil)}` : ' sans date de fin'}
            {j.banReason ? ` · ${j.banReason}` : ''}
          </span>
        </div>
      )}

      <Tabs
        label="Onglets de la fiche"
        onglets={onglets.map((o) => ({
          cle: o.cle,
          label: o.label,
          icone: o.icone,
          n: o.n ?? null,
          actif: o.cle === onglet,
          href: o.cle === 'apercu' ? `/players/${id}` : `/players/${id}?onglet=${o.cle}`,
        }))}
      />

      {onglet === 'apercu' && <Apercu j={j} />}
      {onglet === 'candidature' && <OngletCandidature j={j} lienFiche={peut('candidatures.relire')} />}
      {onglet === 'personnages' && (
        <OngletPersonnages
          data={persos.data}
          chargement={persos.isLoading}
          erreur={persos.error as Error | null}
          onReessayer={() => void persos.refetch()}
        />
      )}
      {onglet === 'tickets' && (
        <OngletTickets
          j={j}
          ouvrir={(cat) => peut('tickets.traiter') || (cat === 'BUG' && peutUne('tickets.bug'))}
          onOuvrir={(tid) => router.push(`/tickets/${tid}`)}
        />
      )}
      {onglet === 'historique' && <OngletHistorique id={j.id} />}
      {onglet === 'securite' && <OngletSecurite j={j} />}

      {changer && (
        <ChangerGrade
          ouvert={grade}
          onFermer={() => setGrade(false)}
          cible={{ id: j.id, pseudo: j.minecraftUsername, role: j.role }}
        />
      )}
    </div>
  );
}

// ──────────────────────────── Aperçu ────────────────────────────

function Apercu({ j }: { j: PlayerDetail }) {
  const { peut } = useDroits();
  const ouverts = j.tickets.filter((t) => t.status === 'OPEN' || t.status === 'IN_PROGRESS').length;
  const base = `/players/${j.id}`;
  const lignes: Array<{ cle: string; icone: NomIcone; titre: string; texte: string; href?: string }> = [
    {
      cle: 'candidature',
      icone: 'scroll-text',
      titre: 'Candidature',
      texte: j.whitelist
        ? `${STATUT_GLOBAL[j.whitelist.status].label} · ${j.whitelist.firstName} ${j.whitelist.lastName}, ${j.whitelist.village} · envoyée le ${dateJour(j.whitelist.submittedAt)}`
        : 'Aucune candidature envoyée',
      href: `${base}?onglet=candidature`,
    },
    {
      cle: 'tickets',
      icone: 'ticket',
      titre: 'Tickets',
      texte:
        j.tickets.length === 0
          ? 'Aucun ticket'
          : `${pluriel(j.tickets.length, 'ticket')}, ${ouverts === 0 ? 'aucun ouvert' : pluriel(ouverts, 'ouvert')}`,
      href: `${base}?onglet=tickets`,
    },
    {
      cle: 'ban',
      icone: 'gavel',
      titre: 'Bannissement',
      texte: j.banned
        ? `Banni${j.bannedUntil ? ` jusqu’au ${dateJour(j.bannedUntil)}` : ''}${j.banReason ? ` · ${j.banReason}` : ''}`
        : 'Aucun bannissement en cours',
    },
  ];
  if (peut('audit.voir')) {
    lignes.push({
      cle: 'historique',
      icone: 'history',
      titre: 'Historique',
      texte: 'Décisions, prises, changements de grade et lectures d’IP',
      href: `${base}?onglet=historique`,
    });
  }

  return (
    <div className="split">
      <Card titre="Résumé" meta="Une ligne par onglet">
        <div className="-mt-1">
          {lignes.map((l) => {
            const contenu = (
              <>
                <span className="sum-ic">
                  <Icone nom={l.icone} taille={16} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="sum-t">{l.titre}</span>
                  <span className="sum-x">{l.texte}</span>
                </span>
                {l.href && <Icone nom="chevron-right" taille={16} className="text-fg-3" />}
              </>
            );
            return l.href ? (
              <Link key={l.cle} href={l.href} className="sumline hover:text-fg">
                {contenu}
              </Link>
            ) : (
              <div key={l.cle} className="sumline">
                {contenu}
              </div>
            );
          })}
        </div>
      </Card>
      <Card titre="Compte">
        <ListeKV
          largeur={124}
          lignes={[
            {
              cle: 'discord',
              label: 'Discord',
              valeur: j.discordUsername ? `@${j.discordUsername}` : <span className="text-fg-3">non lié</span>,
            },
            {
              cle: 'uuid',
              label: 'UUID',
              valeur: <Copiable valeur={j.minecraftUuid} affiche={uuidCourt(j.minecraftUuid)} />,
            },
            j.steamUsername ? { cle: 'steam', label: 'Steam', valeur: j.steamUsername } : null,
            { cle: 'cree', label: 'Compte créé', valeur: dateJour(j.createdAt) },
            {
              cle: 'connexion',
              label: 'Dernière connexion',
              valeur: j.lastLoginAt ? (
                <span title={dateHeure(j.lastLoginAt)}>{ilYa(j.lastLoginAt)}</span>
              ) : (
                'jamais'
              ),
            },
            { cle: 'grade', label: 'Grade', valeur: libelleGrade(j.role) },
          ]}
        />
      </Card>
    </div>
  );
}

function Copiable({ valeur, affiche }: { valeur: string; affiche: string }) {
  return (
    <span className="inline-flex items-center gap-1">
      <span className="mono" title={valeur}>
        {affiche}
      </span>
      <BoutonIcone
        icone="copy"
        taille={14}
        className="!h-6 !w-6 !min-h-0"
        label="Copier l’UUID"
        onClick={() => {
          void navigator.clipboard?.writeText(valeur).then(
            () => toast.success('Copié'),
            () => toast.error('Copie impossible'),
          );
        }}
      />
    </span>
  );
}

// ──────────────────────────── Candidature ────────────────────────────

function OngletCandidature({ j, lienFiche }: { j: PlayerDetail; lienFiche: boolean }) {
  if (!j.whitelist) {
    return <EmptyState icone="scroll-text" titre="Aucune candidature." aide="Le joueur n’a pas encore envoyé de candidature depuis le launcher." />;
  }
  const w = j.whitelist;
  const s = STATUT_GLOBAL[w.status];
  return (
    <Card
      titre={`${w.firstName} ${w.lastName}`}
      action={
        lienFiche ? (
          <Link href={`/whitelist/${w.id}`} className="mot">
            Ouvrir la fiche
          </Link>
        ) : undefined
      }
    >
      <ListeKV
        lignes={[
          {
            cle: 'statut',
            label: 'Statut',
            valeur: (
              <Badge ton={s.ton === 'success' ? 'succes' : s.ton === 'danger' ? 'danger' : s.ton === 'warning' ? 'attention' : 'info'}>
                {s.label}
              </Badge>
            ),
          },
          { cle: 'village', label: 'Village', valeur: w.village },
          { cle: 'envoi', label: 'Envoyée', valeur: dateHeure(w.submittedAt) },
          w.reviewedAt ? { cle: 'relue', label: 'Relue', valeur: dateHeure(w.reviewedAt) } : null,
        ]}
      />
    </Card>
  );
}

// ──────────────────────────── Personnages ────────────────────────────

const ETAT_PERSONNAGE: Record<PersonnageJoueur['state'], { label: string; ton: 'succes' | 'danger' | 'vide' }> = {
  ALIVE: { label: 'En vie', ton: 'succes' },
  DEAD: { label: 'Mort RP', ton: 'danger' },
  DELETED: { label: 'Supprimé', ton: 'vide' },
};

function OngletPersonnages({
  data,
  chargement,
  erreur,
  onReessayer,
}: {
  data?: PersonnagesJoueur;
  chargement: boolean;
  erreur: Error | null;
  onReessayer: () => void;
}) {
  const launcher = data?.serveursLauncher ?? [];
  const colonnes: Colonne<PersonnageJoueur>[] = [
    {
      cle: 'nom',
      titre: 'Personnage',
      principal: true,
      rendu: (p) => (
        <span className="ellip block" title={p.characterId}>
          {[p.firstName, p.lastName].filter(Boolean).join(' ')}
          {p.playerNumber && <span className="muted"> · N° {p.playerNumber}</span>}
          {p.fromApplication && <span className="muted"> · candidature</span>}
        </span>
      ),
    },
    {
      cle: 'serveur',
      titre: 'Serveur',
      serre: true,
      rendu: (p) => (
        <Badge
          ton={launcher.includes(p.serverId) ? 'info' : 'neutre'}
          title={launcher.includes(p.serverId) ? 'Montré dans « Mes personnages » du launcher' : 'Pas montré dans le launcher'}
        >
          {libelleServeur(p.serverId)}
        </Badge>
      ),
    },
    {
      cle: 'village',
      titre: 'Village · clan',
      cacherEtroit: true,
      rendu: (p) => <span className="ellip block">{[p.village, p.clan].filter(Boolean).join(' · ') || '—'}</span>,
    },
    { cle: 'grade', titre: 'Grade', serre: true, rendu: (p) => (p.type === 'CIVIL' ? 'Civil' : libelleGradeRp(p.rank)) },
    {
      cle: 'etat',
      titre: 'État',
      serre: true,
      rendu: (p) => <Badge ton={ETAT_PERSONNAGE[p.state].ton}>{ETAT_PERSONNAGE[p.state].label}</Badge>,
    },
    {
      cle: 'temps',
      titre: 'Temps de jeu',
      serre: true,
      align: 'droite',
      rendu: (p) => <span title={`Compté depuis le ${dateJour(p.playMinutesSince)}`}>{tempsDeJeu(p.playMinutes)}</span>,
    },
    {
      cle: 'vu',
      titre: 'Dernière partie',
      serre: true,
      cacherEtroit: true,
      rendu: (p) => (p.lastSeenAt ? <span title={dateHeure(p.lastSeenAt)}>{ilYa(p.lastSeenAt)}</span> : '—'),
    },
  ];
  return (
    <DataTable<PersonnageJoueur>
      label="Personnages du joueur"
      colonnes={colonnes}
      lignes={data?.characters}
      cle={(p) => `${p.serverId}:${p.characterId}`}
      chargement={chargement}
      erreur={erreur}
      onReessayer={onReessayer}
      vide={<EmptyState icone="user" titre="Aucun personnage synchronisé depuis le jeu." />}
      pied={launcher.length > 0 ? `Launcher : ${launcher.map(libelleServeur).join(', ')}` : undefined}
    />
  );
}

// ──────────────────────────── Tickets ────────────────────────────

type TicketJoueur = PlayerDetail['tickets'][number];

function OngletTickets({
  j,
  ouvrir,
  onOuvrir,
}: {
  j: PlayerDetail;
  ouvrir: (categorie: string) => boolean;
  onOuvrir: (id: string) => void;
}) {
  const colonnes: Colonne<TicketJoueur>[] = [
    { cle: 'sujet', titre: 'Sujet', principal: true, rendu: (t) => <span className="ellip block">{t.subject}</span> },
    { cle: 'cat', titre: 'Catégorie', serre: true, rendu: (t) => libelleCategorie(t.category) },
    { cle: 'statut', titre: 'Statut', serre: true, rendu: (t) => <BadgeStatut statut={STATUT_TICKET[t.status]} /> },
    {
      cle: 'maj',
      titre: 'Dernière activité',
      serre: true,
      rendu: (t) => <span title={dateHeure(t.updatedAt)}>{ilYa(t.updatedAt)}</span>,
    },
  ];
  return (
    <DataTable<TicketJoueur>
      label="Tickets du joueur"
      colonnes={colonnes}
      lignes={j.tickets}
      cle={(t) => t.id}
      onOuvrir={(t) => (ouvrir(t.category) ? onOuvrir(t.id) : undefined)}
      vide={<EmptyState icone="ticket" titre="Aucun ticket." />}
    />
  );
}

// ──────────────────────────── Historique ────────────────────────────

function OngletHistorique({ id }: { id: string }) {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'audit', 'joueur', id],
    queryFn: () => api<Paginated<AuditLogItem>>(`/admin/audit?targetUserId=${id}&take=100`),
  });
  const colonnes: Colonne<AuditLogItem>[] = [
    {
      cle: 'quand',
      titre: 'Quand',
      serre: true,
      rendu: (a) => <span title={dateHeure(a.createdAt)}>{ilYa(a.createdAt)}</span>,
    },
    { cle: 'action', titre: 'Action', principal: true, rendu: (a) => <span title={a.action}>{libelleAction(a.action)}</span> },
    { cle: 'qui', titre: 'Par', serre: true, rendu: (a) => a.actor.username },
    { cle: 'source', titre: 'Source', serre: true, cacherEtroit: true, rendu: (a) => libelleSource(a.source) },
  ];
  return (
    <DataTable<AuditLogItem>
      label="Historique du joueur"
      colonnes={colonnes}
      lignes={data?.items}
      cle={(a) => a.id}
      chargement={isLoading}
      erreur={error as Error | null}
      onReessayer={() => void refetch()}
      vide={<EmptyState icone="history" titre="Rien au journal pour ce joueur." />}
      pied={data && data.total > data.items.length ? `${data.items.length} plus récents sur ${data.total}` : undefined}
    />
  );
}

// ──────────────────────────── Sécurité ────────────────────────────

function OngletSecurite({ j }: { j: PlayerDetail }) {
  const { peut } = useDroits();
  const [ip, setIp] = useState<IpJoueur | null>(null);
  const lire = useMutation({
    mutationFn: () => api<IpJoueur>(`/admin/players/${j.id}/ip`),
    onSuccess: setIp,
    onError: (e) => toast.error('Adresse non affichée', { description: messageErreur(e) }),
  });
  // Seulement les connexions de ce joueur (sans IP : elle se lit une à une).
  const anomalies = useQuery({
    queryKey: ['admin', 'anomalies', 'joueur', j.id],
    queryFn: () => api<LoginAnomaly[]>(`/admin/anomalies?userId=${encodeURIComponent(j.id)}`),
    enabled: peut('securite.voir'),
  });
  const siennes = anomalies.data ?? [];

  return (
    <div className="split">
      <Card titre="Dernière connexion" meta="Réservé aux administrateurs">
        <ListeKV
          largeur={124}
          lignes={[
            {
              cle: 'ip',
              label: 'Adresse IP',
              valeur: !j.ipConnue ? (
                <span className="text-fg-3">aucune connue</span>
              ) : ip ? (
                <span className="mono">{ip.ip ?? 'aucune'}</span>
              ) : (
                <span className="inline-flex items-center gap-3">
                  <span className="mono text-fg-3" aria-label="Adresse masquée">
                    •••.•••.•••.••
                  </span>
                  <Mot onClick={() => lire.mutate()} enCours={lire.isPending}>
                    <span className="inline-flex items-center gap-1">
                      <Icone nom="eye" taille={14} /> Afficher
                    </span>
                  </Mot>
                </span>
              ),
            },
            {
              // Le pays suit l'IP : masqué jusqu'à « Afficher » (lecture journalisée).
              cle: 'pays',
              label: 'Pays',
              valeur: ip ? (
                (ip.pays ?? 'inconnu')
              ) : (
                <span className="text-fg-3">{j.ipConnue ? 'masqué' : 'inconnu'}</span>
              ),
            },
          ]}
        />
        <p className="help mt-4 flex items-center gap-1.5">
          <Icone nom="eye" taille={14} /> Chaque affichage d’une adresse IP et de son pays est inscrit au journal.
        </p>
      </Card>
      {peut('securite.voir') && (
        <Card titre="Connexions suspectes" meta={siennes.length > 0 ? pluriel(siennes.length, 'signal', 'signaux') : undefined}>
          {anomalies.isLoading ? (
            <Chargement />
          ) : siennes.length === 0 ? (
            <p className="text-[13px] text-fg-3">Aucune connexion suspecte pour ce joueur.</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {siennes.map((a) => (
                <li key={a.id} className="text-[13px]">
                  <div className="text-fg">{a.reason}</div>
                  <div className="text-fg-3">
                    {a.country ?? 'Pays inconnu'} · <span title={dateHeure(a.createdAt)}>{ilYa(a.createdAt)}</span>
                    {a.acknowledged ? ' · vue' : ' · à voir'}
                  </div>
                </li>
              ))}
            </ul>
          )}
          <div className="mt-4">
            <Callout ton="neutre">
              Détail et « Marquer vue » dans <Link href="/anomalies" className="lien">Modération › Sécurité</Link>.
            </Callout>
          </div>
        </Card>
      )}
    </div>
  );
}
