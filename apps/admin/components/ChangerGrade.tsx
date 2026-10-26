'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { LIBELLES_GRADES, ORDRE_GRADES, RESUME_GRADES, rangGrade } from '@/lib/libelles';
import type { Role } from '@/lib/types';
import { Button } from './ui/Button';
import { Dialog } from './ui/Dialog';
import { Field, Textarea } from './ui/Field';
import { messageErreur } from './ui/States';

/** Bornes du motif (ChangeRoleDto côté API). */
const MOTIF_MIN = 3;
const MOTIF_MAX = 500;

/**
 * Fenêtre « Changer de grade » (PANEL_V2 §4.2, §6.6) : choix, motif, Entrée.
 * Ne propose que des grades strictement inférieurs au sien (jamais
 * Propriétaire) ; l'API refuse le reste (pas de changement de son propre
 * grade, ni d'un pair ou d'un supérieur) et inscrit chaque changement au
 * journal, motif compris.
 */
export function peutChangerGrade(
  moi: { id: string; role: string } | undefined,
  cible: { id: string; role: string },
  aCapacite: boolean,
): boolean {
  if (!moi || !aCapacite || moi.id === cible.id) return false;
  return rangGrade(cible.role) < rangGrade(moi.role);
}

export function ChangerGrade({
  ouvert,
  onFermer,
  cible,
}: {
  ouvert: boolean;
  onFermer: () => void;
  cible: { id: string; pseudo: string; role: Role };
}) {
  const qc = useQueryClient();
  const { moi } = useDroits();
  const idForm = useId();
  const [choix, setChoix] = useState<Role | null>(null);
  const [motif, setMotif] = useState('');
  const monRang = rangGrade(moi?.role);
  const options = ORDRE_GRADES.filter((r) => rangGrade(r) < monRang && r !== 'OWNER');

  function fermer() {
    setChoix(null);
    setMotif('');
    onFermer();
  }

  const mut = useMutation({
    mutationFn: (v: { role: Role; motif: string }) =>
      api(`/admin/players/${cible.id}/role`, { method: 'PATCH', body: v }),
    onSuccess: (_d, v) => {
      qc.invalidateQueries({ queryKey: ['admin', 'players'] });
      qc.invalidateQueries({ queryKey: ['admin', 'audit'] });
      toast.success(`${cible.pseudo} passe ${LIBELLES_GRADES[v.role]}`, {
        description: 'Le changement et son motif sont inscrits au journal.',
      });
      fermer();
    },
    onError: (e) => toast.error('Grade non changé', { description: messageErreur(e) }),
  });

  const motifPret = motif.trim().length >= MOTIF_MIN;
  const pret = choix !== null && choix !== cible.role && motifPret;

  function envoyer() {
    if (pret && choix && !mut.isPending) mut.mutate({ role: choix, motif: motif.trim() });
  }

  return (
    <Dialog
      ouvert={ouvert}
      onFermer={fermer}
      titre={`Changer le grade de ${cible.pseudo}`}
      description={`Grade actuel : ${LIBELLES_GRADES[cible.role]}. Le changement est inscrit au journal avec ton nom et le motif.`}
      pied={
        <>
          <Button variante="ghost" onClick={fermer}>
            Annuler
          </Button>
          {/* Bouton d'envoi du formulaire (attribut form) : Entrée valide depuis les choix. */}
          <Button
            type="submit"
            form={idForm}
            variante="primary"
            disabled={!pret}
            enCours={mut.isPending}
            raccourci={pret ? 'Entrée' : undefined}
          >
            {!choix || choix === cible.role
              ? 'Choisis un grade'
              : !motifPret
                ? 'Écris le motif'
                : `Passer ${LIBELLES_GRADES[choix]}`}
          </Button>
        </>
      }
    >
      <form
        id={idForm}
        className="flex flex-col gap-4"
        onSubmit={(e) => {
          e.preventDefault();
          envoyer();
        }}
      >
        <fieldset className="flex flex-col gap-2">
          <legend className="sr-only">Nouveau grade</legend>
          {options.map((r) => (
            <label key={r} className="gopt">
              <input
                type="radio"
                name="grade"
                value={r}
                checked={choix === r}
                disabled={r === cible.role}
                onChange={() => setChoix(r)}
              />
              <span className="min-w-0">
                <b>{LIBELLES_GRADES[r]}</b>
                {r === cible.role && <span className="ml-2 text-[12px] text-fg-3">grade actuel</span>}
                <span className="block text-[12px] text-fg-3">{RESUME_GRADES[r]}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <Field
          label="Motif"
          compteur={`${motif.trim().length} / ${MOTIF_MAX}`}
          aide="Inscrit au journal avec le changement. Entrée valide, Maj + Entrée passe à la ligne."
        >
          {(id) => (
            <Textarea
              id={id}
              rows={2}
              maxLength={MOTIF_MAX}
              aria-required="true"
              value={motif}
              placeholder="Ex. : rejoint l’équipe d’accueil après sa période d’essai."
              onChange={(e) => setMotif(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  envoyer();
                }
              }}
            />
          )}
        </Field>
      </form>
    </Dialog>
  );
}
