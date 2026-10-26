'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Field, Input, Textarea } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { messageErreur } from '@/components/ui/States';
import { api } from '@/lib/api';
import { PHASES_ORAL, heureCourte, jourLong } from '@/lib/candidature';
import type { OralDecision, PhaseCandidature } from '@/lib/types';
import { invaliderCandidature } from './DecisionEcrit';
import { ChoixDecision } from './ui';

type Resultat = 'APPROVED' | 'NEEDS_REVISION' | 'REJECTED';

const OPTIONS_ORAL: Array<{ valeur: Resultat; label: string; ton: 'success' | 'warning' | 'danger' }> = [
  { valeur: 'APPROVED', label: 'Accepter', ton: 'success' },
  { valeur: 'NEEDS_REVISION', label: 'À revoir', ton: 'warning' },
  { valeur: 'REJECTED', label: 'Refuser', ton: 'danger' },
];

/** Motif proposé pendant la transition (douane en jeu pas encore ouverte, §3.11). */
const MOTIF_TRANSITION = 'Douane en jeu pas encore ouverte';

/**
 * « Oral hors jeu » (CANDIDATURE §3.11, Q9) : l'oral passé en vocal ou en
 * appel, tranché au panel par l'examinateur. Secours une fois la douane
 * ouverte en jeu (crash, micro du jeu) : le motif du hors jeu est toujours
 * demandé et tracé au journal. PATCH whitelist/:id/oral. Replié par défaut.
 */
export function OralHorsJeu({
  id,
  phase,
  douaneEnJeu,
}: {
  id: string;
  phase: PhaseCandidature;
  douaneEnJeu: boolean;
}) {
  const qc = useQueryClient();
  const [ouvert, setOuvert] = useState(false);
  const [resultat, setResultat] = useState<Resultat | null>(null);
  const [motifHorsJeu, setMotifHorsJeu] = useState(douaneEnJeu ? '' : MOTIF_TRANSITION);
  const [motif, setMotif] = useState('');
  const [notes, setNotes] = useState('');
  const possible = PHASES_ORAL.includes(phase);
  const motifRequis = resultat === 'NEEDS_REVISION' || resultat === 'REJECTED';
  const pret =
    resultat !== null &&
    motifHorsJeu.trim().length >= 3 &&
    (!motifRequis || motif.trim().length > 0);

  const mut = useMutation({
    mutationFn: () =>
      api<OralDecision>(`/admin/whitelist/${id}/oral`, {
        method: 'PATCH',
        body: {
          resultat,
          motifHorsJeu: motifHorsJeu.trim(),
          motif: motif.trim() || undefined,
          notes: notes.trim() || undefined,
        },
      }),
    onSuccess: (res) => {
      invaliderCandidature(qc, id);
      setResultat(null);
      setMotif('');
      setNotes('');
      const suite =
        res.resultat === 'APPROVED'
          ? 'Le joueur est whitelisté.'
          : res.resultat === 'NEEDS_REVISION' && res.prochaineSeanceDes
            ? `Nouvelle séance possible dès le ${jourLong(res.prochaineSeanceDes)}.`
            : res.canReapplyAt
              ? `Nouvelle candidature possible le ${jourLong(res.canReapplyAt)} à ${heureCourte(res.canReapplyAt)}.`
              : undefined;
      toast.success('Oral tranché', { description: suite });
    },
    onError: (e) => toast.error('Oral non tranché', { description: messageErreur(e) }),
  });

  return (
    <div className="border-t border-[var(--color-line)] pt-3">
      <button
        type="button"
        className="flex w-full items-center gap-3 text-left text-[13px] text-fg-2"
        aria-expanded={ouvert}
        onClick={() => setOuvert((v) => !v)}
      >
        <Icone nom="headphones" taille={16} className="text-fg-3" />
        <span className="flex-1">{douaneEnJeu ? 'Oral hors jeu (secours)' : 'Oral hors jeu (transition)'}</span>
        <Icone nom={ouvert ? 'chevron-down' : 'chevron-right'} taille={16} className="text-fg-3" />
      </button>

      {ouvert && (
        <div className="mt-3 flex flex-col gap-3">
          <p className="help">
            {douaneEnJeu
              ? 'Secours seulement (crash, micro du jeu) : l’oral se passe à la douane en jeu.'
              : 'La douane en jeu n’est pas ouverte : l’oral se passe en vocal puis se tranche ici.'}
          </p>
          {!possible ? (
            <p className="help">
              {phase === 'ACCEPTE'
                ? 'Candidat accepté : pas d’oral à trancher.'
                : phase === 'REFUSE'
                  ? 'Candidature refusée : une nouvelle candidature complète est nécessaire.'
                  : 'L’oral se tranche une fois l’écrit accepté (HRP et RP validés).'}
            </p>
          ) : (
            <>
              <ChoixDecision
                options={OPTIONS_ORAL}
                valeur={resultat}
                onChange={(v) => setResultat((c) => (c === v ? null : v))}
              />
              {resultat && (
                <form
                  className="flex flex-col gap-3"
                  onSubmit={(e) => {
                    e.preventDefault();
                    if (pret) mut.mutate();
                  }}
                >
                  <Field label="Pourquoi hors jeu ?" compteur="Journal">
                    {(fid) => (
                      <Input
                        id={fid}
                        value={motifHorsJeu}
                        onChange={(e) => setMotifHorsJeu(e.target.value)}
                        maxLength={300}
                        placeholder="Crash du jeu, micro du jeu en panne…"
                      />
                    )}
                  </Field>
                  <Field
                    label={motifRequis ? 'Motif' : 'Un mot de l’examinateur (facultatif)'}
                    compteur="Visible du candidat"
                  >
                    {(fid) => (
                      <Textarea
                        id={fid}
                        value={motif}
                        onChange={(e) => setMotif(e.target.value)}
                        maxLength={2000}
                        rows={3}
                        placeholder={
                          resultat === 'REJECTED'
                            ? 'Pourquoi la douane n’est pas validée…'
                            : resultat === 'NEEDS_REVISION'
                              ? 'Ce que le candidat doit travailler avant la prochaine séance…'
                              : 'Un mot pour le candidat…'
                        }
                      />
                    )}
                  </Field>
                  <Field label="Notes de l’examinateur (facultatif)" compteur="Privé">
                    {(fid) => (
                      <Textarea
                        id={fid}
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        maxLength={4000}
                        rows={3}
                        placeholder="Réponses aux questions, impressions, doutes sur l’âge…"
                      />
                    )}
                  </Field>
                  <p className="help">
                    {resultat === 'APPROVED'
                      ? 'Accepter : le compte passe whitelisté, la place de douane en cours est soldée.'
                      : resultat === 'NEEDS_REVISION'
                        ? 'À revoir : l’écrit est gardé, nouvelle séance dès le lendemain.'
                        : 'Refuser : nouvelle candidature complète possible 1 jour plus tard.'}
                  </p>
                  <div className="flex gap-2">
                    <Button
                      type="submit"
                      variante={resultat === 'REJECTED' ? 'danger-outline' : 'secondary'}
                      disabled={!pret}
                      enCours={mut.isPending}
                    >
                      Trancher l’oral
                    </Button>
                    <Button variante="ghost" onClick={() => setResultat(null)}>
                      Annuler
                    </Button>
                  </div>
                </form>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
