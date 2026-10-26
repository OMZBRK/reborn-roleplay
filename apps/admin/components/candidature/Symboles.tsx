import { Icone, type NomIcone } from '@/components/ui/Icone';
import type { AppStatus, PhaseCandidature } from '@/lib/types';

/**
 * Symboles de partie (PANEL_V2 §4.3) : chacun a SA forme — coche (validé),
 * horloge (en attente), flèche de reprise (à revoir), croix (refusé), tiret
 * (pas encore). La couleur ne fait que doubler la forme.
 */
type Etat = 'ok' | 'attente' | 'revoir' | 'refuse' | 'pas-encore';

const FORME: Record<Etat, { icone: NomIcone; classe: string; mot: string }> = {
  ok: { icone: 'check', classe: 't-ok', mot: 'validé' },
  attente: { icone: 'clock', classe: 't-wait', mot: 'en attente' },
  revoir: { icone: 'rotate-ccw', classe: 't-rev', mot: 'à revoir' },
  refuse: { icone: 'x', classe: 't-ko', mot: 'refusé' },
  'pas-encore': { icone: 'minus', classe: 't-none', mot: 'pas encore' },
};

function etatPartie(s: AppStatus): Etat {
  if (s === 'APPROVED') return 'ok';
  if (s === 'REJECTED') return 'refuse';
  if (s === 'NEEDS_REVISION') return 'revoir';
  return 'attente';
}

function etatOralTrio(e: {
  oralStatus: AppStatus;
  oralRequis: boolean;
  phase: PhaseCandidature;
}): Etat {
  if (e.oralStatus === 'APPROVED' || (!e.oralRequis && e.phase !== 'REFUSE')) return 'ok';
  if (e.oralStatus === 'REJECTED') return 'refuse';
  if (e.phase === 'DOUANE_A_REVOIR' || e.oralStatus === 'NEEDS_REVISION') return 'revoir';
  if (e.phase === 'DOUANE_RESERVEE' || e.phase === 'DOUANE_A_RESERVER') return 'attente';
  return 'pas-encore';
}

function Symbole({ etat, partie }: { etat: Etat; partie: string }) {
  const f = FORME[etat];
  const texte = `${partie} : ${f.mot}`;
  return (
    <span className={f.classe} title={texte}>
      <Icone nom={f.icone} taille={16} label={texte} />
    </span>
  );
}

export function TrioParties(e: {
  hrpStatus: AppStatus;
  rpStatus: AppStatus;
  oralStatus: AppStatus;
  oralRequis: boolean;
  phase: PhaseCandidature;
}) {
  return (
    <span className="trio">
      <Symbole etat={etatPartie(e.hrpStatus)} partie="Écrit HRP" />
      <Symbole etat={etatPartie(e.rpStatus)} partie="Écrit RP" />
      <Symbole etat={etatOralTrio(e)} partie="Oral" />
    </span>
  );
}

/** Légende des symboles (pied de la liste). */
export function LegendeSymboles() {
  return (
    <span className="inline-flex flex-wrap items-center gap-x-4 gap-y-1">
      {(['ok', 'attente', 'revoir', 'refuse'] as Etat[]).map((e) => (
        <span key={e} className="inline-flex items-center gap-1.5">
          <span className={`trio ${''}`}>
            <span className={FORME[e].classe}>
              <Icone nom={FORME[e].icone} taille={14} />
            </span>
          </span>
          {FORME[e].mot.charAt(0).toUpperCase() + FORME[e].mot.slice(1)}
        </span>
      ))}
    </span>
  );
}
