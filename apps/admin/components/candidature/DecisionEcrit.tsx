'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { toast } from 'sonner';
import { PriseEnCharge } from '@/components/AssignmentBlock';
import { Badge, BadgeStatut } from '@/components/ui/Badge';
import { Button, Mot } from '@/components/ui/Button';
import { useConfirm } from '@/components/ui/Dialog';
import { Field, Select, Textarea } from '@/components/ui/Field';
import { Icone } from '@/components/ui/Icone';
import { messageErreur } from '@/components/ui/States';
import { api } from '@/lib/api';
import { dateHeure, etatOral } from '@/lib/candidature';
import { MOTIFS_CANDIDATURE } from '@/lib/modeles';
import { preferences } from '@/lib/preferences';
import { STATUT_PARTIE_ECRIT } from '@/lib/statuts';
import type { AppStatus, WhitelistDetail } from '@/lib/types';

/**
 * Panneau de décision de la fiche candidature (PANEL_V2 §4.3) :
 *  - deux lignes Écrit HRP · Écrit RP, chacune avec trois mots Valider ·
 *    À revoir · Refuser (décision par partie : PATCH whitelist/:id/decision) ;
 *  - le motif n'apparaît que sous la ligne choisie, avec les modèles,
 *    modifiable avant envoi, VISIBLE du candidat ;
 *  - l'oral en lecture seule (il se décide au Poste de garde ; secours hors
 *    jeu dans le bloc Douane) ;
 *  - « Valider l'écrit », seule action en or, valide HRP et RP d'un coup
 *    quand les deux attendent.
 * Une décision prend le dossier s'il n'est à personne (préférence « Prendre
 * automatiquement »).
 */

export type Decision = 'APPROVED' | 'NEEDS_REVISION' | 'REJECTED';
export type Partie = 'HRP' | 'RP';

const LIBELLE_DECISION: Record<Decision, string> = {
  APPROVED: 'validé',
  NEEDS_REVISION: 'à revoir',
  REJECTED: 'refusé',
};

/** Invalide tout ce qui dépend d'une décision sur la candidature. */
export function invaliderCandidature(qc: ReturnType<typeof useQueryClient>, id: string) {
  qc.invalidateQueries({ queryKey: ['admin', 'whitelist'] });
  qc.invalidateQueries({ queryKey: ['admin', 'whitelist', id] });
  qc.invalidateQueries({ queryKey: ['admin', 'douane'] });
  qc.invalidateQueries({ queryKey: ['admin', 'dashboard'] });
}

/**
 * État de l'écrit après une décision : `fini` quand plus rien n'attend sur
 * l'écrit (les deux parties tranchées, ou une partie refusée, qui clôt le
 * dossier). Sinon `reste` nomme la partie encore « En attente » : la fiche
 * ne doit pas enchaîner sur le dossier suivant.
 */
export interface EtatApresDecision {
  fini: boolean;
  reste: Partie | null;
}

export interface PanneauDecisionRef {
  /** Ouvre le motif « À revoir » (ou « Refuser ») sur une partie. */
  ouvrir: (partie: Partie, choix: Decision) => void;
  /** « V » : valide l'écrit (les deux parties si elles attendent). */
  valider: () => void;
}

export const PanneauDecision = forwardRef<
  PanneauDecisionRef,
  {
    data: WhitelistDetail;
    /** Après une décision réussie (bandeau « Suivante », enchaînement si l'écrit est fini). */
    onDecision: (resume: string, etat: EtatApresDecision) => void;
  }
>(function PanneauDecision({ data, onDecision }, ref) {
  const qc = useQueryClient();
  const { confirmer } = useConfirm();
  const [ouverte, setOuverte] = useState<{ partie: Partie; choix: Decision } | null>(null);
  const verrouillee = data.phase === 'ACCEPTE';
  const deuxAttendent = data.hrpStatus === 'PENDING' && data.rpStatus === 'PENDING';

  async function priseAuto() {
    if (data.assignee || !preferences().priseAuto) return;
    try {
      await api(`/admin/whitelist/${data.id}/assign`, { method: 'POST', body: { force: false } });
    } catch {
      /* déjà pris entre-temps : la décision reste possible */
    }
  }

  const decider = useMutation({
    mutationFn: async (input: { parties: Partie[]; status: Decision; reviewNotes?: string }) => {
      await priseAuto();
      for (const part of input.parties) {
        await api(`/admin/whitelist/${data.id}/decision`, {
          method: 'PATCH',
          body: { part, status: input.status, reviewNotes: input.reviewNotes },
        });
      }
    },
    onSuccess: (_d, v) => {
      invaliderCandidature(qc, data.id);
      setOuverte(null);
      const quoi =
        v.parties.length === 2
          ? `Écrit ${LIBELLE_DECISION[v.status]}`
          : `Écrit ${v.parties[0]} ${LIBELLE_DECISION[v.status]}`;
      toast.success(quoi, { description: 'Le candidat est prévenu dans son launcher.' });
      const hrpApres: AppStatus = v.parties.includes('HRP') ? v.status : data.hrpStatus;
      const rpApres: AppStatus = v.parties.includes('RP') ? v.status : data.rpStatus;
      const refus = hrpApres === 'REJECTED' || rpApres === 'REJECTED';
      const fini = refus || (hrpApres !== 'PENDING' && rpApres !== 'PENDING');
      onDecision(quoi, {
        fini,
        reste: fini ? null : hrpApres === 'PENDING' ? 'HRP' : 'RP',
      });
    },
    onError: (e) => toast.error('Décision non enregistrée', { description: messageErreur(e) }),
  });

  // « Valider l'écrit » n'existe que quand l'écrit attend (même condition que
  // le bouton) : V ne valide pas une partie « à revoir » que le candidat
  // corrige, ni une partie refusée.
  const validationOuverte = !verrouillee && (deuxAttendent || data.phase === 'ECRIT_EN_ATTENTE');

  async function validerEcrit() {
    if (!validationOuverte || decider.isPending) return;
    const parties: Partie[] = deuxAttendent
      ? ['HRP', 'RP']
      : (['HRP', 'RP'] as Partie[]).filter((p) =>
          (p === 'HRP' ? data.hrpStatus : data.rpStatus) !== 'APPROVED',
        );
    if (parties.length === 0) return;
    const ok = await confirmer({
      titre: parties.length === 2 ? 'Valider l’écrit ?' : `Valider l’écrit ${parties[0]} ?`,
      message:
        parties.length === 2
          ? `HRP et RP de ${data.firstName} ${data.lastName} passent « Validé ». Le candidat pourra réserver sa douane.`
          : `La partie ${parties[0]} passe « Validé ».`,
      confirmer: parties.length === 2 ? 'Valider l’écrit' : `Valider ${parties[0]}`,
    });
    if (ok) decider.mutate({ parties, status: 'APPROVED' });
  }

  useImperativeHandle(ref, () => ({
    ouvrir: (partie, choix) => {
      if (!verrouillee) setOuverte({ partie, choix });
    },
    valider: () => void validerEcrit(),
  }));

  const oral = etatOral({
    oralStatus: data.oral.status,
    oralRequis: data.suivi.oralRequis,
    phase: data.phase,
    convocationAt: data.suivi.reservation?.convocationAt,
  });

  return (
    <section className="card" aria-labelledby="titre-decision">
      <div className="card-h">
        <h2 id="titre-decision" className="card-t">
          Décision
        </h2>
        <PriseEnCharge
          genre="whitelist"
          id={data.id}
          assignee={data.assignee}
          ferme={data.phase === 'ACCEPTE' || data.phase === 'REFUSE'}
        />
      </div>
      <div className="card-b">
        <LignePartie
          titre="Écrit HRP"
          partie="HRP"
          statut={data.hrpStatus}
          data={data}
          verrouillee={verrouillee}
          ouverte={ouverte?.partie === 'HRP' ? ouverte.choix : null}
          onOuvrir={(c) => setOuverte(c ? { partie: 'HRP', choix: c } : null)}
          enCours={decider.isPending}
          onEnvoyer={(status, notes) =>
            decider.mutate({ parties: ['HRP'], status, reviewNotes: notes })
          }
        />
        <LignePartie
          titre="Écrit RP"
          partie="RP"
          statut={data.rpStatus}
          data={data}
          verrouillee={verrouillee}
          ouverte={ouverte?.partie === 'RP' ? ouverte.choix : null}
          onOuvrir={(c) => setOuverte(c ? { partie: 'RP', choix: c } : null)}
          enCours={decider.isPending}
          onEnvoyer={(status, notes) =>
            decider.mutate({ parties: ['RP'], status, reviewNotes: notes })
          }
        />
        {/* Une seule ligne pour l'écrit : l'API garde le dernier relecteur et
            sa date, pas un auteur par partie. */}
        {data.reviewedBy && data.reviewedAt && (data.hrpStatus !== 'PENDING' || data.rpStatus !== 'PENDING') && (
          <p className="dl-m px-0.5 pb-1">
            Dernière décision sur l’écrit : {data.reviewedBy.minecraftUsername} · {dateHeure(data.reviewedAt)}
          </p>
        )}
        <div className="dline">
          <div className="flex items-center justify-between gap-3">
            <span className="dl-l">Oral</span>
            <Badge
              ton={
                oral.ton === 'success'
                  ? 'succes'
                  : oral.ton === 'danger'
                    ? 'danger'
                    : oral.ton === 'warning'
                      ? 'attention'
                      : oral.ton === 'accent'
                        ? 'info'
                        : 'vide'
              }
            >
              {oral.label === '—' ? 'Après l’écrit' : oral.label}
            </Badge>
          </div>
          <div className="dl-m">
            {!data.suivi.oralRequis
              ? 'Oral déjà réussi sur ce compte : l’écrit validé suffit.'
              : data.suivi.douaneEnJeu
                ? 'Se décide au Poste de garde, en jeu.'
                : 'Douane en jeu pas encore ouverte : oral hors jeu dans le bloc Douane.'}
            {oral.detail && oral.label !== '—' && <> · {oral.detail}</>}
          </div>
        </div>

        {validationOuverte && (
          <div className="mt-4">
            <Button
              variante="primary"
              bloc
              raccourci="V"
              enCours={decider.isPending && !ouverte}
              onClick={() => void validerEcrit()}
              disabled={data.hrpStatus === 'APPROVED' && data.rpStatus === 'APPROVED'}
            >
              Valider l’écrit
            </Button>
            <p className="help mt-2">
              « À revoir » ou « Refuser » sur une partie ouvre le motif (modèles), visible du
              candidat.
            </p>
          </div>
        )}
        {verrouillee && (
          <p className="help mt-3">
            Candidature acceptée, oral compris. Pour la rouvrir : remise à zéro (menu ⋯).
          </p>
        )}
      </div>
    </section>
  );
});

function LignePartie({
  titre,
  partie,
  statut,
  data,
  verrouillee,
  ouverte,
  onOuvrir,
  onEnvoyer,
  enCours,
}: {
  titre: string;
  partie: Partie;
  statut: AppStatus;
  data: WhitelistDetail;
  verrouillee: boolean;
  ouverte: Decision | null;
  onOuvrir: (c: Decision | null) => void;
  onEnvoyer: (status: Decision, notes?: string) => void;
  enCours: boolean;
}) {
  const [motif, setMotif] = useState('');
  const zone = useRef<HTMLTextAreaElement>(null);
  const requis = ouverte === 'NEEDS_REVISION' || ouverte === 'REJECTED';

  useEffect(() => {
    if (ouverte) {
      setMotif('');
      // Le motif s'ouvre sous la ligne : on y met le curseur.
      setTimeout(() => zone.current?.focus(), 0);
    }
  }, [ouverte]);

  return (
    <div className="dline">
      <div className="flex items-center justify-between gap-3">
        <span className="dl-l">{titre}</span>
        <BadgeStatut statut={STATUT_PARTIE_ECRIT[statut]} />
      </div>
      {!verrouillee && !ouverte && (
        <div className="dl-w">
          {statut !== 'APPROVED' && (
            <Mot onClick={() => onOuvrir('APPROVED')} disabled={enCours} title="Valider · V">
              Valider
            </Mot>
          )}
          {statut !== 'NEEDS_REVISION' && (
            <Mot onClick={() => onOuvrir('NEEDS_REVISION')} disabled={enCours} title="À revoir · R">
              À revoir
            </Mot>
          )}
          {statut !== 'REJECTED' && (
            <Mot ton="danger" onClick={() => onOuvrir('REJECTED')} disabled={enCours}>
              Refuser
            </Mot>
          )}
        </div>
      )}
      {ouverte && (
        <form
          className="mt-3 flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (requis && motif.trim().length === 0) return;
            onEnvoyer(ouverte, motif.trim() || undefined);
          }}
        >
          {requis && (
            <Field label="Modèle de motif">
              {(id) => (
                <Select
                  id={id}
                  defaultValue=""
                  onChange={(e) => {
                    const m = MOTIFS_CANDIDATURE.find((x) => x.id === e.target.value);
                    if (m) setMotif(m.texte);
                    zone.current?.focus();
                  }}
                >
                  <option value="">Choisir un modèle…</option>
                  {MOTIFS_CANDIDATURE.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.nom}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          )}
          <Field
            label={
              ouverte === 'APPROVED'
                ? 'Un mot pour le candidat (facultatif)'
                : ouverte === 'REJECTED'
                  ? `Pourquoi l’écrit ${partie} est refusé`
                  : `Ce que le candidat doit reprendre (${partie})`
            }
            compteur="Visible du candidat"
            aide={
              ouverte === 'REJECTED'
                ? 'Refus : nouvelle candidature complète possible 1 jour plus tard.'
                : ouverte !== 'APPROVED' && data.phase === 'DOUANE_RESERVEE'
                  ? 'L’écrit ne sera plus validé : la place de douane réservée est rendue et le candidat prévenu.'
                  : ouverte === 'NEEDS_REVISION'
                    ? `« À revoir » rouvre la partie ${partie} au candidat.`
                    : undefined
            }
          >
            {(id) => (
              <Textarea
                id={id}
                ref={zone}
                rows={4}
                maxLength={2000}
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    if (!requis || motif.trim()) onEnvoyer(ouverte, motif.trim() || undefined);
                  }
                }}
              />
            )}
          </Field>
          <div className="flex items-center gap-2">
            <Button
              type="submit"
              variante={ouverte === 'REJECTED' ? 'danger-outline' : 'secondary'}
              enCours={enCours}
              disabled={requis && motif.trim().length === 0}
            >
              {ouverte === 'APPROVED'
                ? `Valider ${partie}`
                : ouverte === 'REJECTED'
                  ? `Refuser ${partie}`
                  : `Renvoyer ${partie} à revoir`}
            </Button>
            <Button variante="ghost" onClick={() => onOuvrir(null)}>
              Annuler
            </Button>
            <span className="help ml-auto hidden sm:inline">Ctrl + Entrée</span>
          </div>
        </form>
      )}
    </div>
  );
}

/**
 * Note PRIVÉE du staff sur l'écrit (writtenStaffNotes) : les points à
 * creuser à l'oral, lus par l'examinateur. Enregistrée à part
 * (PATCH whitelist/:id/note-privee), sans trancher, jamais montrée au joueur.
 * Repliée par défaut : une ligne d'aperçu.
 */
export interface NotePriveeRef {
  ouvrir: () => void;
}

export const NotePriveeEcrit = forwardRef<NotePriveeRef, { id: string; note: string | null }>(
  function NotePriveeEcrit({ id, note }, ref) {
    const qc = useQueryClient();
    const [ouvert, setOuvert] = useState(false);
    const [valeur, setValeur] = useState(note ?? '');
    const [modifiee, setModifiee] = useState(false);
    const zone = useRef<HTMLTextAreaElement>(null);

    useImperativeHandle(ref, () => ({
      ouvrir: () => {
        setOuvert(true);
        setTimeout(() => zone.current?.focus(), 0);
      },
    }));

    // La fiche se rafraîchit : on ne réécrit pas une saisie en cours.
    useEffect(() => {
      if (!modifiee) setValeur(note ?? '');
    }, [note, modifiee]);

    const mut = useMutation({
      mutationFn: (texte: string) =>
        api<{ writtenStaffNotes: string | null }>(`/admin/whitelist/${id}/note-privee`, {
          method: 'PATCH',
          body: { note: texte },
        }),
      onSuccess: (res) => {
        setModifiee(false);
        setValeur(res.writtenStaffNotes ?? '');
        qc.invalidateQueries({ queryKey: ['admin', 'whitelist', id] });
        qc.invalidateQueries({ queryKey: ['admin', 'douane'] });
        toast.success('Note privée enregistrée');
      },
      onError: (e) => toast.error('Note non enregistrée', { description: messageErreur(e) }),
    });

    const apercu = (note ?? '').trim();

    return (
      <section className="card">
        <button
          type="button"
          className="flex w-full items-center gap-3 px-5 py-4 text-left"
          aria-expanded={ouvert}
          onClick={() => setOuvert((v) => !v)}
          title="Note privée · O"
        >
          <Icone nom="lock" taille={16} className="text-fg-3" />
          <span className="min-w-0 flex-1">
            <span className="block text-[14px] font-medium text-fg">Note privée de l’écrit</span>
            {!ouvert && (
              <span className="block truncate text-[12px] text-fg-3">
                {apercu ? `« ${apercu} »` : 'aucune'}
              </span>
            )}
          </span>
          <Icone nom={ouvert ? 'chevron-down' : 'chevron-right'} taille={16} className="text-fg-3" />
        </button>
        {ouvert && (
          <div className="flex flex-col gap-3 px-5 pb-5">
            <Field
              label="Les points à creuser à l’oral"
              compteur={`Privé · ${valeur.length} / 4 000`}
              aide="Transmise à l’examinateur, jamais au joueur."
            >
              {(fid) => (
                <Textarea
                  id={fid}
                  ref={zone}
                  rows={4}
                  maxLength={4000}
                  value={valeur}
                  placeholder="Ex. : histoire floue sur le maître du personnage, vérifier l’âge déclaré…"
                  onChange={(e) => {
                    setValeur(e.target.value);
                    setModifiee(true);
                  }}
                />
              )}
            </Field>
            <div className="flex justify-end gap-2">
              {modifiee && (
                <Button
                  variante="ghost"
                  taille="sm"
                  onClick={() => {
                    setValeur(note ?? '');
                    setModifiee(false);
                  }}
                >
                  Annuler
                </Button>
              )}
              <Button
                taille="sm"
                disabled={!modifiee}
                enCours={mut.isPending}
                onClick={() => mut.mutate(valeur)}
              >
                Enregistrer la note
              </Button>
            </div>
          </div>
        )}
      </section>
    );
  },
);
