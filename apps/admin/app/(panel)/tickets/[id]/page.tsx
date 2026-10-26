'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { use, useRef } from 'react';
import { toast } from 'sonner';
import { PriseEnCharge, invaliderApresPrise } from '@/components/AssignmentBlock';
import { ChatPanel, type ChatPanelRef } from '@/components/ChatPanel';
import { Garde } from '@/components/Garde';
import { MCAvatar } from '@/components/MCAvatar';
import { BadgeStatut } from '@/components/ui/Badge';
import { Mot } from '@/components/ui/Button';
import { Card, ListeKV } from '@/components/ui/Card';
import { Icone } from '@/components/ui/Icone';
import { Chargement, ErrorState, messageErreur } from '@/components/ui/States';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { dateHeure, ilYa } from '@/lib/format';
import { LIBELLE_CATEGORIE_TICKET_LONG, LIBELLE_STATUT_TICKET, libelleGrade } from '@/lib/libelles';
import { preferences } from '@/lib/preferences';
import { useRaccourcis } from '@/lib/raccourcis';
import { STATUT_TICKET } from '@/lib/statuts';
import type { TicketDetail, TicketStatus } from '@/lib/types';

/**
 * Fiche ticket (PANEL_V2 §4.4) : la conversation pleine hauteur avec la
 * réponse en bas (« / » ouvre les réponses types, Ctrl + Entrée envoie,
 * Ctrl + Maj + Entrée envoie et résout), et un panneau latéral : le joueur,
 * la catégorie, le statut en trois mots, qui l'a pris. La première réponse
 * prend le ticket (préférence « Prendre automatiquement »).
 */
export default function FicheTicketPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <Garde capacites={['tickets.traiter', 'tickets.bug']}>
      <Fiche id={id} />
    </Garde>
  );
}

function Fiche({ id }: { id: string }) {
  const qc = useQueryClient();
  const { moi, peut } = useDroits();
  const chat = useRef<ChatPanelRef>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'tickets', id],
    queryFn: () => api<TicketDetail>(`/admin/tickets/${id}`),
    refetchInterval: 10_000,
  });

  const statut = useMutation({
    mutationFn: (s: TicketStatus) => api(`/admin/tickets/${id}`, { method: 'PATCH', body: { status: s } }),
    onSuccess: (_d, s) => {
      invaliderApresPrise(qc, 'tickets', id);
      const messages: Record<TicketStatus, string> = {
        OPEN: 'Ticket rouvert',
        IN_PROGRESS: 'Ticket en cours',
        RESOLVED: 'Ticket résolu',
        CLOSED: 'Ticket fermé',
      };
      toast.success(messages[s]);
    },
    onError: (e) => toast.error('Statut non changé', { description: messageErreur(e) }),
  });

  useRaccourcis([{ touche: 'r', action: () => chat.current?.repondre() }], !!data);

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
        <ErrorState erreur={error ?? new Error('Ticket introuvable.')} onReessayer={() => void refetch()} />
      </div>
    );
  }

  const ferme = data.status === 'CLOSED';
  const clos = data.status === 'CLOSED' || data.status === 'RESOLVED';

  /** Première réponse : prend le ticket s'il n'est à personne, et le passe en cours. */
  async function avantEnvoi() {
    if (!data || !preferences().priseAuto) return;
    if (!data.assignee && !clos) {
      try {
        await api(`/admin/tickets/${id}/assign`, { method: 'POST', body: { force: false } });
      } catch {
        /* déjà pris entre-temps : la réponse part quand même */
      }
    }
    if (data.status === 'OPEN') {
      await api(`/admin/tickets/${id}`, { method: 'PATCH', body: { status: 'IN_PROGRESS' } });
    }
    invaliderApresPrise(qc, 'tickets', id);
  }

  async function resoudre() {
    await api(`/admin/tickets/${id}`, { method: 'PATCH', body: { status: 'RESOLVED' } });
    invaliderApresPrise(qc, 'tickets', id);
  }

  return (
    <div className="page">
      <header className="ph">
        <div className="min-w-0 flex-1">
          <nav className="crumbs" aria-label="Fil d’Ariane">
            <Link href="/tickets">Modération</Link>
            <Icone nom="chevron-right" taille={12} />
            <Link href="/tickets">Tickets</Link>
          </nav>
          <h1 className="ph-title">{data.subject}</h1>
          <div className="ph-sub">
            {LIBELLE_CATEGORIE_TICKET_LONG[data.category]} · ouvert{' '}
            <span title={dateHeure(data.createdAt)}>{ilYa(data.createdAt)}</span> par{' '}
            <b>{data.user.minecraftUsername}</b>
            {data.discordThreadId && ' · fil Discord lié'}
          </div>
        </div>
        <div className="ph-act">
          {ferme ? (
            <Mot onClick={() => statut.mutate('OPEN')} enCours={statut.isPending}>
              Rouvrir le ticket
            </Mot>
          ) : (
            <div className="seg" role="group" aria-label="Statut du ticket">
              {(['IN_PROGRESS', 'RESOLVED', 'CLOSED'] as TicketStatus[]).map((s) => (
                <button
                  key={s}
                  type="button"
                  className="seg-btn"
                  aria-pressed={data.status === s}
                  disabled={statut.isPending}
                  onClick={() => data.status !== s && statut.mutate(s)}
                >
                  {LIBELLE_STATUT_TICKET[s]}
                </button>
              ))}
            </div>
          )}
        </div>
      </header>

      <div className="split">
        <section className="card flex min-h-[520px] flex-col" style={{ height: 'calc(100vh - 190px)' }} aria-label="Conversation">
          <ChatPanel
            ref={chat}
            endpoint={`/admin/tickets/${data.id}/messages`}
            queryKey={['admin', 'tickets', data.id]}
            messages={data.messages}
            canSend={!ferme}
            playerUuid={data.user.minecraftUuid}
            playerName={data.user.minecraftUsername}
            roleJoueur="Joueur"
            reponsesTypes
            avantEnvoi={avantEnvoi}
            resoudre={data.status !== 'RESOLVED' ? resoudre : undefined}
            emptyLabel={`Aucun message : réponds à ${data.user.minecraftUsername} ci-dessous.`}
            closedLabel="Ce ticket est fermé. Rouvre-le pour répondre."
          />
        </section>

        <aside className="stack">
          <Card titre="Joueur">
            <div className="flex items-center gap-3">
              <MCAvatar uuid={data.user.minecraftUuid} size={36} />
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium text-fg">{data.user.minecraftUsername}</div>
                <div className="text-[12px] text-fg-3">
                  {libelleGrade(data.user.role)}
                  {data.user.discordUsername && ` · @${data.user.discordUsername}`}
                </div>
              </div>
              {peut('joueurs.voir') && (
                <Link href={`/players/${data.user.id}`} className="mot">
                  Fiche
                </Link>
              )}
            </div>
          </Card>

          <Card>
            <ListeKV
              largeur={96}
              lignes={[
                { cle: 'cat', label: 'Catégorie', valeur: LIBELLE_CATEGORIE_TICKET_LONG[data.category] },
                { cle: 'statut', label: 'Statut', valeur: <BadgeStatut statut={STATUT_TICKET[data.status]} /> },
                {
                  cle: 'pris',
                  label: 'Pris par',
                  valeur: (
                    <PriseEnCharge
                      genre="tickets"
                      id={data.id}
                      assignee={data.assignee}
                      statutTicket={data.status}
                      ferme={clos}
                    />
                  ),
                },
                { cle: 'ouvert', label: 'Ouvert', valeur: dateHeure(data.createdAt) },
                { cle: 'maj', label: 'Activité', valeur: <span title={dateHeure(data.updatedAt)}>{ilYa(data.updatedAt)}</span> },
              ]}
            />
          </Card>
          {moi && data.assignee && data.assignee.id !== moi.id && !clos && (
            <p className="help px-1">Pris par {data.assignee.minecraftUsername} : tu peux quand même répondre.</p>
          )}
          <p className="help px-1">
            <span className="kbd mr-1">R</span> répondre · <span className="kbd">/</span> réponses types en début de réponse
          </p>
        </aside>
      </div>
    </div>
  );
}
