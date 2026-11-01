'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState } from 'react';
import { toast } from 'sonner';
import { Button, Mot } from '@/components/ui/Button';
import { Dialog, useConfirm } from '@/components/ui/Dialog';
import { Field, Select, Textarea } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { Callout, messageErreur } from '@/components/ui/States';
import { api } from '@/lib/api';
import { dateHeure, ilYa } from '@/lib/format';
import { estStaff } from '@/lib/libelles';
import { MOTIFS_ORAL_A_REPASSER } from '@/lib/modeles';
import type { OralARepasser, OralDecision, ReponseOralARepasser } from '@/lib/types';

/**
 * « Faire repasser l'oral » (décision du 19/10 amendée le 01/11) : le staff
 * exige une nouvelle douane d'un compte déjà passé par l'oral (candidature
 * de clan, longue absence, test de la whitelist). POST
 * /admin/players/:id/oral-a-repasser remet le compte dans l'état « écrit
 * accepté, oral à passer » que tout le parcours lit déjà (réservation au
 * launcher, douane en jeu, décision au Poste de garde). Le motif est montré
 * au joueur et inscrit au journal avec le nom du staff. Jamais un staff :
 * l'API refuse, et `peutExigerOral` cache l'entrée.
 *
 * « Lever » passe par l'oral hors jeu existant (PATCH whitelist/:id/oral,
 * accepté à ton nom) : le joueur retrouve l'accès sans repasser la douane.
 */

/** Bornes du motif (RepasserOralDto côté API). */
const MOTIF_MIN = 3;
const MOTIF_MAX = 500;
/** La levée part comme motif du hors jeu (OralHorsJeuDto : 300 caractères au plus). */
const LEVEE_MAX = 300;

/** Ce qui dépend de l'oral d'un compte : joueurs (fiche, grade dans les listes), candidatures, douane, journal, tableau de bord. */
function invaliderOral(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['admin', 'players'] });
  qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
  qc.invalidateQueries({ queryKey: ['admin', 'douane'] });
  qc.invalidateQueries({ queryKey: ['admin', 'audit'] });
  qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
}

export function FaireRepasserOral({
  ouvert,
  onFermer,
  cible,
  activite,
}: {
  ouvert: boolean;
  onFermer: () => void;
  cible: { userId: string; pseudo: string };
  /** Dernière connexion et dernière partie, quand la page les connaît (fiche joueur). */
  activite?: { lastLoginAt?: string | null; dernierePartie?: string | null };
}) {
  const qc = useQueryClient();
  const idForm = useId();
  const zone = useRef<HTMLTextAreaElement>(null);
  const [motif, setMotif] = useState('');

  function fermer() {
    setMotif('');
    onFermer();
  }

  const mut = useMutation({
    mutationFn: (m: string) =>
      api<ReponseOralARepasser>(`/admin/players/${cible.userId}/oral-a-repasser`, {
        method: 'POST',
        body: { motif: m },
      }),
    onSuccess: () => {
      invaliderOral(qc);
      toast.success('Oral à repasser', {
        description: 'Il repasse la douane avant de rejouer. Inscrit au journal.',
      });
      fermer();
    },
    onError: (e) => toast.error('Demande non enregistrée', { description: messageErreur(e) }),
  });

  const longueur = motif.trim().length;
  const pret = longueur >= MOTIF_MIN;

  function envoyer() {
    if (pret && !mut.isPending) mut.mutate(motif.trim());
  }

  const vu = [
    activite?.lastLoginAt ? `dernière connexion ${ilYa(activite.lastLoginAt)}` : null,
    activite?.dernierePartie ? `dernière partie ${ilYa(activite.dernierePartie)}` : null,
  ]
    .filter((m): m is string => !!m)
    .join(' · ');

  return (
    <Dialog
      ouvert={ouvert}
      alerte
      onFermer={fermer}
      titre={`Faire repasser l’oral à ${cible.pseudo} ?`}
      description="Il redevient candidat : avant de rejouer, il devra passer une séance de douane, réservée depuis le launcher (après son écrit, s’il est encore en relecture). Ses personnages et son inventaire ne bougent pas. Inscrit au journal avec ton nom et le motif."
      pied={
        <>
          <Button variante="ghost" onClick={fermer}>
            Annuler
          </Button>
          {/* Bouton d'envoi du formulaire (attribut form) ; depuis le motif, Ctrl + Entrée seulement (geste à risque). */}
          <Button
            type="submit"
            form={idForm}
            variante="danger-solid"
            disabled={!pret}
            enCours={mut.isPending}
          >
            {pret ? 'Faire repasser l’oral' : 'Écris le motif'}
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
        {vu && (
          <p className="help flex items-center gap-1.5">
            <Icone nom="clock" taille={14} />
            {vu.charAt(0).toUpperCase() + vu.slice(1)}
          </p>
        )}
        <Field label="Modèle de motif">
          {(id) => (
            <Select
              id={id}
              defaultValue=""
              onChange={(e) => {
                const m = MOTIFS_ORAL_A_REPASSER.find((x) => x.id === e.target.value);
                if (m) setMotif(m.texte);
                zone.current?.focus();
              }}
            >
              <option value="">Choisir un modèle…</option>
              {MOTIFS_ORAL_A_REPASSER.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nom}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field
          label="Motif"
          compteur={`Visible du joueur · ${longueur} / ${MOTIF_MAX}`}
          aide="Ctrl + Entrée valide, Entrée passe à la ligne."
        >
          {(id) => (
            <Textarea
              id={id}
              ref={zone}
              data-autofocus
              rows={3}
              maxLength={MOTIF_MAX}
              aria-required="true"
              value={motif}
              placeholder="Ex. : candidature au clan Nara, le conseil demande un nouvel oral."
              onChange={(e) => setMotif(e.target.value)}
              onKeyDown={(e) => {
                // Jamais Entrée seule (Dialog › danger) : le MP au joueur ne se rattrape pas.
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
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

/**
 * Ligne « Oral à repasser · motif · demandé par … le … » : sous l'en-tête de
 * la fiche joueur (`page`, comme la ligne de bannissement) ou en tête du bloc
 * Douane de la candidature (`carte`). « Lever » demande un motif (privé) puis
 * valide l'oral hors jeu à ton nom.
 */
export function LigneOralARepasser({
  o,
  applicationId,
  pseudo,
  peutLever,
  variante,
}: {
  o: OralARepasser;
  /** Candidature dont l'oral se valide pour lever ; null (candidature remise à zéro) : rien à lever. */
  applicationId: string | null;
  pseudo: string;
  /** Capacité `candidatures.relire` et phase où l'oral se tranche (PHASES_ORAL) : sinon l'API refuserait. */
  peutLever: boolean;
  variante: 'page' | 'carte';
}) {
  const qc = useQueryClient();
  const { demander } = useConfirm();

  const lever = useMutation({
    mutationFn: (motifHorsJeu: string) =>
      api<OralDecision>(`/admin/whitelist/${applicationId}/oral`, {
        method: 'PATCH',
        body: { resultat: 'APPROVED', motifHorsJeu },
      }),
    onSuccess: () => {
      invaliderOral(qc);
      toast.success('Oral levé', {
        description: `${pseudo} retrouve l’accès sans repasser la douane et en est prévenu dans le launcher. Inscrit au journal.`,
      });
    },
    onError: (e) => toast.error('Oral non levé', { description: messageErreur(e) }),
  });

  async function demanderLevee() {
    const motif = await demander({
      titre: `Lever l’oral à repasser de ${pseudo} ?`,
      message:
        'Il retrouve l’accès sans repasser la douane : oral validé hors jeu à ton nom. Il lit « Oral validé par le staff » dans le launcher, et le fil Discord du dossier passe à « Acceptée ».',
      label: 'Motif',
      placeholder: 'Ex. : demandé par erreur, oral passé en vocal…',
      aide: 'Privé : inscrit au journal, jamais montré au joueur.',
      confirmer: 'Lever l’oral',
      valider: (v) =>
        v.length < MOTIF_MIN
          ? `Motif : ${MOTIF_MIN} caractères au moins.`
          : v.length > LEVEE_MAX
            ? `Motif : ${LEVEE_MAX} caractères au plus.`
            : null,
    });
    if (motif) lever.mutate(motif);
  }

  const texte = (
    <>
      <b>Oral à repasser</b> · {o.motif} · demandé par {o.demandePar?.minecraftUsername ?? '—'} le{' '}
      {dateHeure(o.demandeLe)}
    </>
  );
  const action =
    peutLever && applicationId ? (
      <Mot
        ton="neutre"
        enCours={lever.isPending}
        title="Valider l’oral hors jeu, sans nouvelle douane"
        onClick={() => void demanderLevee()}
      >
        Lever
      </Mot>
    ) : null;

  if (variante === 'carte') {
    return (
      <Callout ton="attention" icone="landmark">
        <div className="[overflow-wrap:anywhere]">{texte}</div>
        {action && <div className="mt-1.5">{action}</div>}
      </Callout>
    );
  }
  return (
    <div className="alertline" role="status">
      <Icone nom="landmark" taille={16} />
      <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">{texte}</span>
      {action}
    </div>
  );
}

/**
 * La remise à zéro ne propose « refaire aussi l'oral » qu'à un joueur qui a
 * réussi l'oral : sans oral réussi il le passera de toute façon, et un staff
 * ne passe pas la douane.
 */
export function proposerRefaireOral(c: { role: string; oralPassedAt: string | null }): boolean {
  return !!c.oralPassedAt && !estStaff(c.role);
}

/**
 * Toast de la remise à zéro, d'après le grade du compte avant la remise :
 * « grade staff conservé » pour le staff seulement (un simple joueur n'est
 * pas rétrogradé non plus, `roleDemoted` ne suffit pas à les distinguer).
 */
export function descriptionRemiseAZero(
  r: { roleDemoted: boolean; oralReinitialise: boolean },
  role: string,
): string {
  const debut = r.roleDemoted
    ? 'Le joueur repasse joueur et peut envoyer une nouvelle candidature.'
    : estStaff(role)
      ? 'Grade staff conservé : le joueur peut envoyer une nouvelle candidature.'
      : 'Le joueur peut envoyer une nouvelle candidature.';
  return debut + (r.oralReinitialise ? ' Il repassera aussi l’oral.' : '');
}

/**
 * Case « Lui faire aussi repasser l'oral » de la remise à zéro, glissée dans
 * la confirmation (useConfirm › contenu), au-dessus du nom à retaper. Elle
 * garde son état et le remonte à chaque clic ; la page lit la valeur une fois
 * la remise confirmée. Décochée par défaut.
 */
export function CaseRefaireOral({ onChange }: { onChange: (coche: boolean) => void }) {
  const [coche, setCoche] = useState(false);
  return (
    <label className="flex cursor-pointer items-start gap-3">
      <input
        type="checkbox"
        className="check mt-0.5"
        checked={coche}
        onChange={(e) => {
          setCoche(e.target.checked);
          onChange(e.target.checked);
        }}
      />
      <span className="min-w-0">
        <span className="block text-[13px] font-medium text-fg">Lui faire aussi repasser l’oral</span>
        <span className="help block">Sinon il garde son oral réussi : l’écrit validé suffira.</span>
      </span>
    </label>
  );
}
