// Blocs communs du suivi et de la douane (maquettes L8 et L9) : frise,
// carte d'état, note du staff, grille de convocation.

import type { CSSProperties, ReactNode } from "react";
import { Check, PenLine, X } from "lucide-react";
import { heure, jourLong, majuscule } from "./format";

export type EtapeFrise = { libelle: string; date: string; etat: "fait" | "courant" | "futur" | "refus" | "revoir" };

/** Frise ① Écrit envoyé → ② Écrit relu → ③ Douane → ④ Bienvenue (§2.7). */
export function Frise({ etapes }: { etapes: EtapeFrise[] }) {
  return (
    <ol className="cand-frise m-0 list-none p-0" aria-label="Avancement">
      {etapes.map((e, i) => (
        <li key={e.libelle} className="cand-frise-step" data-etat={e.etat}>
          <span className="cand-frise-dot" aria-hidden>
            {e.etat === "fait" ? (
              <Check size={15} strokeWidth={3} />
            ) : e.etat === "refus" ? (
              <X size={15} strokeWidth={3} />
            ) : e.etat === "revoir" ? (
              <PenLine size={13} strokeWidth={2.5} />
            ) : (
              i + 1
            )}
          </span>
          <div className="cand-frise-label">{e.libelle}</div>
          <div className="cand-frise-date">{e.date}</div>
        </li>
      ))}
    </ol>
  );
}

export function CarteEtat({
  ton,
  surtitre,
  titre,
  children,
}: {
  ton: string;
  surtitre: string;
  titre: string;
  children?: ReactNode;
}) {
  return (
    <section className="cand-status" style={{ "--cand-ton": ton } as CSSProperties}>
      <div className="cand-status-kicker">{surtitre}</div>
      <h2 className="cand-status-title">{titre}</h2>
      {children}
    </section>
  );
}

export function NoteStaff({ qui, texte }: { qui: string; texte: string }) {
  return (
    <blockquote className="cand-quote">
      <span className="cand-quote-who">{qui}</span>
      {texte}
    </blockquote>
  );
}

/** Convocation, fenêtre d'entrée (H − 15 min à H + 30 min, réglages de l'API) et rappel. */
export function GrilleConvocation({
  convocation,
  ouvre,
  ferme,
  avanceMin,
  retardMin,
}: {
  convocation: Date;
  ouvre: Date;
  ferme: Date;
  avanceMin: number;
  retardMin: number;
}) {
  return (
    <div className="cand-convoc">
      <div>
        <div className="cand-convoc-key">Convocation</div>
        <div className="cand-convoc-val">{heure(convocation)}</div>
        <div className="cand-convoc-sub">{majuscule(jourLong(convocation))}</div>
      </div>
      <div>
        <div className="cand-convoc-key">Fenêtre d'entrée</div>
        <div className="cand-convoc-val">
          {heure(ouvre)} → {heure(ferme)}
        </div>
        <div className="cand-convoc-sub">
          {avanceMin} min avant, {retardMin} min après
        </div>
      </div>
      <div>
        <div className="cand-convoc-key">Rappel</div>
        <div className="cand-convoc-val">1 h avant</div>
        <div className="cand-convoc-sub">Message dans le fil de ta candidature</div>
      </div>
    </div>
  );
}
