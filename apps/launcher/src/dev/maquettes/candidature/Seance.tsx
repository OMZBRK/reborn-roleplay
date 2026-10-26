// MAQUETTE L9 — réservation d'une séance de douane (§3.3, Q5, Q12).
//
// Séances avec capacité ouvertes par le staff ; le candidat réserve une
// place, l'API lui attribue une heure de convocation (vagues de 30 min pour
// une séance longue). Règle unique : « Passer la douane » est actif de
// H − 15 min à H + 30 min. Rappel 1 h avant (notification du launcher + MP
// du bot). Pas de limite de séances manquées pour la beta (Q12).

import { useMemo, useState, type CSSProperties } from "react";
import { BellRing, CalendarCheck, CalendarX, Check, Mic, Shield, Users } from "lucide-react";
import { CoqueCandidature } from "./Coque";
import { OngletsSuivi } from "./Suivi";
import {
  MAINTENANT,
  SEANCES,
  compteARebours,
  convocation,
  fenetre,
  heure,
  jourCourt,
  jourLong,
  vagues,
  type Seance,
} from "./donnees";
import { EnTete } from "./ui";

export type VarianteSeance = "choix" | "reservee";

const cleJour = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
const majuscule = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

function Convocation({ seance }: { seance: Seance }) {
  const h = convocation(seance);
  const f = fenetre(h);
  return (
    <div className="mqc-convoc">
      <div>
        <div className="mqc-convoc-key">Convocation</div>
        <div className="mqc-convoc-val">{heure(h)}</div>
        <div className="mqc-convoc-sub">{majuscule(jourLong(h))}</div>
      </div>
      <div>
        <div className="mqc-convoc-key">Fenêtre d'entrée</div>
        <div className="mqc-convoc-val">
          {heure(f.ouvre)} → {heure(f.ferme)}
        </div>
        <div className="mqc-convoc-sub">15 min avant, 30 min après</div>
      </div>
      <div>
        <div className="mqc-convoc-key">Rappel</div>
        <div className="mqc-convoc-val">1 h avant</div>
        <div className="mqc-convoc-sub">Launcher et message Discord</div>
      </div>
    </div>
  );
}

function LigneSeance({ seance, choisie, onChoisir }: { seance: Seance; choisie: boolean; onChoisir: () => void }) {
  const libres = seance.capacite - seance.prises;
  const complet = libres <= 0;
  const duree = Math.round((seance.fin.getTime() - seance.debut.getTime()) / 3600000);
  return (
    <div className="mqc-seance" data-choisie={choisie || undefined} data-complet={complet || undefined}>
      <div>
        <div className="mqc-seance-time">
          {heure(seance.debut)} – {heure(seance.fin)}
        </div>
        <div className="mqc-seance-sub">
          {seance.examinateurs} examinateur{seance.examinateurs > 1 ? "s" : ""} · {duree} h
        </div>
      </div>
      <div>
        <div className="mqc-places">
          {complet ? (
            <strong>Complet</strong>
          ) : (
            <>
              <strong>
                {libres} place{libres > 1 ? "s" : ""}
              </strong>{" "}
              sur {seance.capacite}
            </>
          )}
          <span className="ml-2 text-[11.5px] text-foreground-muted">
            · convocations à {vagues(seance).map(heure).join(", ")}
          </span>
        </div>
        <div className="mqc-places-bar" aria-hidden>
          {Array.from({ length: seance.capacite }, (_, i) => (
            <i key={i} data-libre={i >= seance.prises || undefined} />
          ))}
        </div>
      </div>
      <button type="button" className="mqc-btn" disabled={complet} onClick={onChoisir} aria-pressed={choisie}>
        {choisie ? (
          <>
            <Check size={14} strokeWidth={3} aria-hidden /> Choisie
          </>
        ) : complet ? (
          "Complet"
        ) : (
          "Choisir"
        )}
      </button>
    </div>
  );
}

export function ReservationSeance({ variante = "choix", coque = true }: { variante?: VarianteSeance; coque?: boolean }) {
  const jours = useMemo(() => {
    const m = new Map<string, { date: Date; seances: Seance[] }>();
    for (const s of SEANCES) {
      const k = cleJour(s.debut);
      if (!m.has(k)) m.set(k, { date: s.debut, seances: [] });
      m.get(k)!.seances.push(s);
    }
    return [...m.values()];
  }, []);
  const [jour, setJour] = useState(0);
  const [choisie, setChoisie] = useState<string | null>("s1");
  const [reservee, setReservee] = useState(variante === "reservee");
  const seance = SEANCES.find((s) => s.id === choisie) ?? null;

  return (
    <CoqueCandidature onglets={<OngletsSuivi actif="douane" douaneOuverte />} coque={coque} aides={false}>
      <EnTete
        surtitre={
          <>
            Écrit accepté<span className="mqc-eyebrow-sep" aria-hidden />
            Dernière étape
          </>
        }
        titre="Ta séance de douane"
        chapo="La douane est l'oral d'entrée, en jeu et à la voix. Choisis une séance : tu seras convoqué à une heure précise, avec les autres candidats."
      />

      {reservee && seance ? (
        <>
          <section className="mqc-status" style={{ "--mqc-ton": "var(--color-success)" } as CSSProperties}>
            <div className="mqc-status-kicker">Place réservée</div>
            <h2 className="mqc-status-title">
              {majuscule(jourLong(convocation(seance)))} · {heure(convocation(seance))}
            </h2>
            <p className="mqc-status-text">
              Convocation {compteARebours(convocation(seance), MAINTENANT)}. Arrive dans la fenêtre d'entrée : tu attendras
              ton tour dans le hall, 30 minutes au plus.
            </p>
            <Convocation seance={seance} />
            <div className="mt-5 flex items-center gap-5">
              <div className="mqc-play-preview" aria-label="Aperçu du bouton de l'accueil">
                <Shield size={18} aria-hidden /> Passer la douane
              </div>
              <div className="text-[12.5px] leading-relaxed text-foreground-subtle">
                Sur l'accueil, ce bouton remplace « Jouer ».
                <br />
                Il s'active le {jourLong(fenetre(convocation(seance)).ouvre)} à {heure(fenetre(convocation(seance)).ouvre)}.
              </div>
            </div>
            <div className="mt-6 flex items-center gap-4 border-t border-border pt-4">
              <button type="button" className="mqc-btn" onClick={() => setReservee(false)}>
                <CalendarCheck size={14} aria-hidden /> Changer de séance
              </button>
              <button type="button" className="mqc-link !text-foreground-muted" onClick={() => setReservee(false)}>
                <CalendarX size={13} aria-hidden /> Annuler ma réservation
              </button>
              <span className="ml-auto text-[11.5px] text-foreground-muted">Un empêchement ? Annule pour libérer la place.</span>
            </div>
          </section>

          <h3 className="mb-3 mt-9 font-display text-[22px] font-normal tracking-wide text-foreground">Avant d'y aller</h3>
          <ul className="m-0 grid list-none grid-cols-3 gap-3 p-0">
            {[
              { icon: Mic, titre: "Ton micro", texte: "L'oral se passe à la voix, dans le jeu." },
              { icon: Users, titre: "Sous ton pseudo", texte: "Pas encore de personnage : il se crée après la douane." },
              { icon: BellRing, titre: "Le rappel", texte: "1 h avant : une notification du launcher et un message Discord." },
            ].map(({ icon: Icon, titre, texte }) => (
              <li key={titre} className="mqc-card !p-4">
                <Icon size={16} className="text-gold" aria-hidden />
                <div className="mt-2 text-[13px] font-semibold text-foreground">{titre}</div>
                <div className="mt-1 text-[12px] leading-relaxed text-foreground-muted">{texte}</div>
              </li>
            ))}
          </ul>
        </>
      ) : (
        <>
          <div className="mqc-days" role="radiogroup" aria-label="Jour">
            {jours.map((j, i) => (
              <button
                key={cleJour(j.date)}
                type="button"
                role="radio"
                aria-checked={jour === i}
                className="mqc-day"
                onClick={() => setJour(i)}
              >
                <span className="mqc-day-dow">{jourCourt(j.date).split(" ")[0]}</span>
                <span className="mqc-day-num">{j.date.getDate()}</span>
                <span className="mqc-day-count">
                  {j.seances.length} séance{j.seances.length > 1 ? "s" : ""}
                </span>
              </button>
            ))}
          </div>

          <div className="mb-3 text-[12.5px] text-foreground-muted">{majuscule(jourLong(jours[jour].date))}</div>
          {jours[jour].seances.map((s) => (
            <LigneSeance key={s.id} seance={s} choisie={choisie === s.id} onChoisir={() => setChoisie(s.id)} />
          ))}

          {seance && (
            <section className="mqc-card mt-7">
              <div className="flex items-baseline justify-between">
                <h3 className="m-0 font-display text-[22px] font-normal tracking-wide text-foreground">
                  Séance du {jourLong(seance.debut)}
                </h3>
                <span className="text-[11.5px] text-foreground-muted">Ton heure est fixée quand tu réserves</span>
              </div>
              <Convocation seance={seance} />
              <div className="mt-5 flex items-center gap-4">
                <button type="button" className="wl-btn-primary glow wl-btn-cta-big" onClick={() => setReservee(true)}>
                  <CalendarCheck size={16} aria-hidden /> Réserver ma place
                </button>
                <span className="text-[12px] leading-relaxed text-foreground-muted">
                  Tu pourras annuler ou changer de séance ici, jusqu'au dernier moment.
                </span>
              </div>
            </section>
          )}
        </>
      )}
    </CoqueCandidature>
  );
}
