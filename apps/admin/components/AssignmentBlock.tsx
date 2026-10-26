'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import type { Assignee, TicketStatus } from '@/lib/types';
import { MCAvatar } from './MCAvatar';
import { Mot } from './ui/Button';
import { messageErreur } from './ui/States';

/**
 * Prise en charge d'un dossier ou d'un ticket (PANEL_V2 §4.3, §4.4) : un
 * seul « Prendre » (pour un ticket ouvert, il le passe aussi « En cours »),
 * « Relâcher » pour le sien, « Reprendre » pour celui d'un autre avec la
 * capacité `prises.forcer`. L'API reste juge (409 si déjà pris).
 */

export type GenrePrise = 'whitelist' | 'tickets';

/** Invalide les listes, la fiche, la file « À traiter » et les compteurs. */
export function invaliderApresPrise(
  qc: ReturnType<typeof useQueryClient>,
  genre: GenrePrise,
  id: string,
) {
  qc.invalidateQueries({ queryKey: ['admin', genre] });
  qc.invalidateQueries({ queryKey: ['admin', genre, id] });
  qc.invalidateQueries({ queryKey: ['admin', 'me', 'inbox'] });
  qc.invalidateQueries({ queryKey: ['admin', 'a-traiter'] });
}

export function usePrise(
  genre: GenrePrise,
  id: string,
  opts: { statutTicket?: TicketStatus } = {},
) {
  const qc = useQueryClient();

  const prendre = useMutation({
    mutationFn: async (force: boolean) => {
      await api(`/admin/${genre}/${id}/assign`, { method: 'POST', body: { force } });
      // Un seul « Prendre » : un ticket ouvert passe aussi « En cours ».
      if (genre === 'tickets' && opts.statutTicket === 'OPEN') {
        await api(`/admin/tickets/${id}`, {
          method: 'PATCH',
          body: { status: 'IN_PROGRESS' },
        });
      }
    },
    onSuccess: (_d, force) => {
      invaliderApresPrise(qc, genre, id);
      toast.success(force ? 'Repris' : 'Pris');
    },
    onError: (e) => toast.error('Prise impossible', { description: messageErreur(e) }),
  });

  const relacher = useMutation({
    mutationFn: () => api(`/admin/${genre}/${id}/assign`, { method: 'DELETE' }),
    onSuccess: () => {
      invaliderApresPrise(qc, genre, id);
      toast.success('Relâché');
    },
    onError: (e) => toast.error('Échec', { description: messageErreur(e) }),
  });

  return { prendre, relacher };
}

/**
 * Ligne « Pris par » d'une fiche : qui l'a, et le mot qui convient.
 */
export function PriseEnCharge({
  genre,
  id,
  assignee,
  statutTicket,
  ferme = false,
}: {
  genre: GenrePrise;
  id: string;
  assignee: Assignee | null;
  statutTicket?: TicketStatus;
  /** Dossier ou ticket clos : plus de prise. */
  ferme?: boolean;
}) {
  const { moi, peut } = useDroits();
  const { prendre, relacher } = usePrise(genre, id, { statutTicket });
  const aMoi = !!moi && assignee?.id === moi.id;
  const occupe = prendre.isPending || relacher.isPending;

  return (
    <span className="inline-flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px]">
      <span className="text-fg-2">
        {assignee ? (aMoi ? 'Pris par toi' : `Pris par ${assignee.minecraftUsername}`) : 'Personne ne l’a pris'}
      </span>
      {!ferme && !assignee && (
        <Mot onClick={() => prendre.mutate(false)} enCours={occupe} title="Prendre · P">
          Prendre
        </Mot>
      )}
      {aMoi && (
        <Mot ton="neutre" onClick={() => relacher.mutate()} enCours={occupe}>
          Relâcher
        </Mot>
      )}
      {!ferme && assignee && !aMoi && peut('prises.forcer') && (
        <Mot onClick={() => prendre.mutate(true)} enCours={occupe}>
          Reprendre
        </Mot>
      )}
    </span>
  );
}

/** Variante compacte pour une liste : le nom de qui l'a pris, ou « — ». */
export function AssigneeBadge({ assignee }: { assignee: Assignee | null }) {
  const { moi } = useDroits();
  if (!assignee) return <span className="text-fg-3">—</span>;
  // Visage + pseudo Minecraft, comme dans la file À traiter. Le visage vient de
  // l'UUID : le nom affiché peut être un pseudo Discord, qui donnerait à
  // mc-heads la tête de Steve ou celle d'un joueur inconnu.
  return (
    <span className="who text-fg-2">
      <MCAvatar uuid={assignee.minecraftUuid} size={22} />
      <span className="truncate">{moi && assignee.id === moi.id ? 'Toi' : assignee.minecraftUsername}</span>
    </span>
  );
}

/** Ancien bloc (gardé pour compatibilité) : même contenu que PriseEnCharge. */
export function AssignmentBlock(props: {
  kind: GenrePrise;
  id: string;
  assignee: Assignee | null;
  assignedAt: string | null;
  showDuration?: boolean;
}) {
  return <PriseEnCharge genre={props.kind} id={props.id} assignee={props.assignee} />;
}
