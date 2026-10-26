// Réservation d'une séance de douane (§3.3, Q5, Q12), maquette L9.
//
// Séances avec capacité ouvertes par le staff ; le candidat réserve une
// place, l'API lui attribue une heure de convocation (vagues de 30 min pour
// une séance longue). Règle unique : l'entrée est permise de H − 15 min à
// H + 30 min (réglages lus dans l'API). Rappel 1 h avant, dans le fil. Pas
// de limite de séances manquées pour la beta (Q12).

import { useMemo, useState, type CSSProperties } from "react";
import { useNavigate } from "react-router";
import {
  ArrowRight,
  BellRing,
  CalendarCheck,
  CalendarX,
  Check,
  Info,
  Loader2,
  Mic,
  RefreshCw,
  Shield,
  Users,
} from "lucide-react";
import {
  annulerReservation,
  reserverSeance,
  type EtatDouane,
  type SeanceDouane,
} from "../../lib/candidature";
import { formatIpcError } from "../../lib/errors";
import { SyncIndicator, type ChatSync } from "../chat/ChatThread";
import { Modal } from "../system/Modal";
import { GrilleConvocation } from "./blocs";
import { cleJour, compteARebours, dateDe, heure, jourLong, jourSemaine, majuscule, MIN_MS, quand } from "./format";
import { useMaintenant } from "./horloge";
import { EnTete } from "./ui";

function LigneSeance({
  seance,
  choisie,
  bloqueeAvant,
  actuelle,
  onChoisir,
}: {
  seance: SeanceDouane;
  choisie: boolean;
  /** Séance trop tôt (douane à revoir : nouvelle séance dès le lendemain). */
  bloqueeAvant: Date | null;
  /** Séance de la place déjà réservée. */
  actuelle: boolean;
  onChoisir: () => void;
}) {
  const debut = new Date(seance.startAt);
  const fin = new Date(seance.endAt);
  const libres = seance.placesRestantes;
  const complet = seance.complet || libres <= 0;
  const heures = Math.max(1, Math.round((fin.getTime() - debut.getTime()) / (60 * MIN_MS)));
  const indisponible = complet || bloqueeAvant !== null || actuelle;
  const prises = Math.max(0, seance.placesTotal - libres);
  return (
    <div className="cand-seance" data-choisie={choisie || undefined} data-complet={indisponible || undefined}>
      <div>
        <div className="cand-seance-time">
          {heure(debut)} – {heure(fin)}
        </div>
        <div className="cand-seance-sub">
          {seance.nbExaminateurs} examinateur{seance.nbExaminateurs > 1 ? "s" : ""} · {heures} h
        </div>
      </div>
      <div>
        <div className="cand-places">
          {complet ? (
            <strong>Complet</strong>
          ) : (
            <>
              <strong>
                {libres} place{libres > 1 ? "s" : ""}
              </strong>{" "}
              sur {seance.placesTotal}
            </>
          )}
          {seance.vagues.length > 0 && (
            <span className="ml-2 text-[11.5px] text-foreground-muted">
              · convocation{seance.vagues.length > 1 ? "s" : ""} à{" "}
              {seance.vagues.map((v) => heure(new Date(v))).join(", ")}
            </span>
          )}
        </div>
        {seance.placesTotal <= 40 && (
          <div className="cand-places-bar" aria-hidden>
            {Array.from({ length: seance.placesTotal }, (_, i) => (
              <i key={i} data-libre={i >= prises || undefined} />
            ))}
          </div>
        )}
      </div>
      <button type="button" className="cand-btn" disabled={indisponible} onClick={onChoisir} aria-pressed={choisie}>
        {actuelle ? (
          "Ta séance"
        ) : choisie ? (
          <>
            <Check size={14} strokeWidth={3} aria-hidden /> Choisie
          </>
        ) : complet ? (
          "Complet"
        ) : bloqueeAvant ? (
          `Dès le ${jourLong(bloqueeAvant)}`
        ) : (
          "Choisir"
        )}
      </button>
    </div>
  );
}

type Props = {
  douane: EtatDouane | null;
  chargement: boolean;
  sync: ChatSync;
  onActualiser: () => void;
  /** Réservation, changement ou annulation faits : relire le suivi et les séances. */
  onChange: () => void;
};

export function DouaneSeances({ douane, chargement, sync, onActualiser, onChange }: Props) {
  const navigate = useNavigate();
  const [changer, setChanger] = useState(false);
  const [jour, setJour] = useState<string | null>(null);
  const [choisie, setChoisie] = useState<string | null>(null);
  const [action, setAction] = useState<{ enCours: boolean; erreur: string | null }>({ enCours: false, erreur: null });
  const [confirmerAnnulation, setConfirmerAnnulation] = useState(false);

  const reservation = douane?.reservation ?? null;
  const convocation = dateDe(reservation?.convocationAt);
  const ouvre = dateDe(reservation?.fenetre.ouvreAt);
  const ferme = dateDe(reservation?.fenetre.fermeAt);
  const now = useMaintenant(30_000, [ouvre?.getTime(), ferme?.getTime()]);
  const avance = douane?.regles.avanceEntreeMin ?? 15;
  const retard = douane?.regles.retardMaxMin ?? 30;
  const enJeu = douane?.regles.douaneEnJeu ?? true;
  const prochaineDes = dateDe(douane?.prochaineSeanceDes);

  const jours = useMemo(() => {
    const m = new Map<string, { cle: string; date: Date; seances: SeanceDouane[] }>();
    for (const s of douane?.seances ?? []) {
      const d = new Date(s.startAt);
      const k = cleJour(d);
      if (!m.has(k)) m.set(k, { cle: k, date: d, seances: [] });
      m.get(k)?.seances.push(s);
    }
    return [...m.values()].sort((a, b) => a.date.getTime() - b.date.getTime());
  }, [douane?.seances]);

  const jourCourant = jours.find((j) => j.cle === jour) ?? jours[0] ?? null;
  const montrerListe = !reservation || changer;
  // Première réservation : la première séance libre du jour affiché est
  // proposée d'emblée (maquette L9), le candidat garde la main sur le choix.
  const proposee =
    !reservation && choisie === null
      ? (jourCourant?.seances.find(
          (s) =>
            !s.complet && s.placesRestantes > 0 && !(prochaineDes && new Date(s.startAt) < prochaineDes),
        ) ?? null)
      : null;
  const idChoisi = choisie ?? proposee?.id ?? null;
  const seance = douane?.seances.find((s) => s.id === idChoisi) ?? null;

  async function reserver() {
    if (!seance) return;
    setAction({ enCours: true, erreur: null });
    try {
      await reserverSeance(seance.id, !!reservation);
      setAction({ enCours: false, erreur: null });
      setChanger(false);
      setChoisie(null);
      onChange();
    } catch (err) {
      setAction({ enCours: false, erreur: formatIpcError(err, "Réservation impossible") });
      onActualiser();
    }
  }

  async function annuler() {
    if (!reservation) return;
    setAction({ enCours: true, erreur: null });
    try {
      await annulerReservation(reservation.id);
      setAction({ enCours: false, erreur: null });
      setConfirmerAnnulation(false);
      onChange();
    } catch (err) {
      setAction({ enCours: false, erreur: formatIpcError(err, "Annulation impossible") });
    }
  }

  const derniere = douane?.derniereReservation ?? null;
  const derniereLe = dateDe(derniere?.convocationAt);

  return (
    <>
      <EnTete
        surtitre={
          douane?.phase === "DOUANE_A_REVOIR" ? (
            <>
              Douane à revoir<span className="cand-eyebrow-sep" aria-hidden />
              Nouvelle séance
            </>
          ) : (
            <>
              Écrit accepté<span className="cand-eyebrow-sep" aria-hidden />
              Dernière étape
            </>
          )
        }
        titre="Ta séance de douane"
        chapo={
          enJeu
            ? "La douane est l'oral d'entrée, en jeu et à la voix. Choisis une séance : tu seras convoqué à une heure précise, avec les autres candidats."
            : "L'oral d'entrée, à la voix, face à un examinateur. Choisis une séance : tu seras convoqué à une heure précise ; les consignes arrivent dans le fil."
        }
      />

      <SyncIndicator variant="block" className="mb-5" sync={sync} onRetry={onActualiser} />

      {derniere && derniereLe && !reservation && (
        <div className="cand-note mb-6" data-ton="warning">
          <Info size={14} aria-hidden />
          <span>
            {derniere.statut === "MISSED"
              ? `Ta séance du ${quand(derniereLe)} a été manquée : l'entrée a fermé à ${heure(new Date(derniere.fermeAt))}. Réserve une autre séance.`
              : `Ta place du ${quand(derniereLe)} a été annulée${
                  derniere.motifAnnulation ? ` : ${derniere.motifAnnulation.trim().replace(/\.$/, "")}` : ""
                }. Tu peux réserver une autre séance.`}
          </span>
        </div>
      )}

      {!douane ? (
        <div className="cand-chargement" role="status">
          {chargement ? "Chargement des séances…" : "Séances indisponibles pour l'instant."}
        </div>
      ) : (
        <>
          {reservation && convocation && ouvre && ferme && (
            <section className="cand-status" style={{ "--cand-ton": "var(--color-success)" } as CSSProperties}>
              <div className="cand-status-kicker">Place réservée</div>
              <h2 className="cand-status-title">
                {majuscule(jourLong(convocation))} · {heure(convocation)}
              </h2>
              <p className="cand-status-text">
                Convocation {compteARebours(convocation, now)}.{" "}
                {enJeu
                  ? `Arrive dans la fenêtre d'entrée : tu attendras ton tour dans le hall, ${retard} minutes au plus.`
                  : "Les consignes de l'oral arrivent dans le fil de ta candidature."}
              </p>
              <GrilleConvocation convocation={convocation} ouvre={ouvre} ferme={ferme} avanceMin={avance} retardMin={retard} />
              {enJeu && (
                <div className="mt-5 flex items-center gap-5">
                  {reservation.fenetre.etat === "OUVERTE" ? (
                    // Entrée ouverte : l'aperçu s'allume et mène au vrai bouton, sur l'accueil.
                    <button
                      type="button"
                      className="cand-play-preview"
                      data-actif
                      title="Le bouton est actif sur l'accueil"
                      onClick={() => navigate("/home")}
                    >
                      <Shield size={18} aria-hidden /> Passer la douane
                    </button>
                  ) : (
                    <div className="cand-play-preview" aria-label="Aperçu du bouton de l'accueil">
                      <Shield size={18} aria-hidden /> Passer la douane
                    </div>
                  )}
                  <div className="text-[12.5px] leading-relaxed text-foreground-subtle">
                    Sur l'accueil, ce bouton remplace « Jouer ».
                    <br />
                    {reservation.fenetre.etat === "OUVERTE"
                      ? "L'entrée est ouverte : lance-toi depuis l'accueil."
                      : reservation.fenetre.etat === "FERMEE"
                        ? "L'entrée est fermée : réserve une autre séance."
                        : `Il s'active le ${jourLong(ouvre)} à ${heure(ouvre)}.`}
                    {reservation.fenetre.etat === "OUVERTE" && (
                      <div className="mt-2">
                        <button type="button" className="cand-link" onClick={() => navigate("/home")}>
                          Aller à l'accueil <ArrowRight size={12} aria-hidden />
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              )}
              <div className="mt-6 flex flex-wrap items-center gap-4 border-t border-border pt-4">
                <button
                  type="button"
                  className="cand-btn"
                  disabled={!douane.peutChanger || action.enCours}
                  onClick={() => setChanger((v) => !v)}
                  aria-expanded={changer}
                >
                  <CalendarCheck size={14} aria-hidden /> {changer ? "Garder cette séance" : "Changer de séance"}
                </button>
                <button
                  type="button"
                  className="cand-link !text-foreground-muted"
                  disabled={action.enCours}
                  onClick={() => setConfirmerAnnulation(true)}
                >
                  <CalendarX size={13} aria-hidden /> Annuler ma réservation
                </button>
                <span className="ml-auto text-[11.5px] text-foreground-muted">Un empêchement ? Annule pour libérer la place.</span>
              </div>
              {action.erreur && !montrerListe && <p className="mt-3 text-[12px] text-[#f87171]">{action.erreur}</p>}
            </section>
          )}

          {!reservation && !douane.peutReserver && douane.raison && (
            <div className="cand-note mb-6">
              <Info size={14} aria-hidden />
              <span>{douane.raison}</span>
            </div>
          )}

          {montrerListe && (douane.peutReserver || (reservation && douane.peutChanger)) && (
            <div className={reservation ? "mt-8" : undefined}>
              {reservation && (
                <h3 className="mb-3 font-display text-[22px] font-normal tracking-wide text-foreground">Choisir une autre séance</h3>
              )}
              {jours.length === 0 ? (
                <div className="cand-card">
                  <div className="text-[13px] font-semibold text-foreground">Aucune séance ouverte pour l'instant</div>
                  <p className="mb-0 mt-1 text-[12.5px] leading-relaxed text-foreground-muted">
                    Les séances sont ouvertes par l'équipe et annoncées sur le Discord. Cette page se met à jour toute seule.
                  </p>
                  <button type="button" className="cand-link mt-3" onClick={onActualiser}>
                    <RefreshCw size={12} aria-hidden /> Actualiser
                  </button>
                </div>
              ) : (
                <>
                  <div className="cand-days" role="radiogroup" aria-label="Jour">
                    {jours.map((j) => (
                      <button
                        key={j.cle}
                        type="button"
                        role="radio"
                        aria-checked={jourCourant?.cle === j.cle}
                        className="cand-day"
                        onClick={() => setJour(j.cle)}
                      >
                        <span className="cand-day-dow">{jourSemaine(j.date)}</span>
                        <span className="cand-day-num">{j.date.getDate()}</span>
                        <span className="cand-day-count">
                          {j.seances.length} séance{j.seances.length > 1 ? "s" : ""}
                        </span>
                      </button>
                    ))}
                  </div>

                  {jourCourant && (
                    <>
                      <div className="mb-3 text-[12.5px] text-foreground-muted">{majuscule(jourLong(jourCourant.date))}</div>
                      {jourCourant.seances.map((s) => (
                        <LigneSeance
                          key={s.id}
                          seance={s}
                          choisie={idChoisi === s.id}
                          actuelle={reservation?.seanceId === s.id}
                          bloqueeAvant={prochaineDes && new Date(s.startAt) < prochaineDes ? prochaineDes : null}
                          onChoisir={() => {
                            setChoisie(s.id);
                            setAction({ enCours: false, erreur: null });
                          }}
                        />
                      ))}
                    </>
                  )}

                  {seance && (
                    <section className="cand-card mt-7">
                      <div className="flex items-baseline justify-between gap-4">
                        <h3 className="m-0 font-display text-[22px] font-normal tracking-wide text-foreground">
                          Séance du {jourLong(new Date(seance.startAt))}
                        </h3>
                        <span className="text-[11.5px] text-foreground-muted">Ton heure est fixée quand tu réserves</span>
                      </div>
                      {(() => {
                        const h = dateDe(seance.convocationIndicative) ?? new Date(seance.startAt);
                        return (
                          <GrilleConvocation
                            convocation={h}
                            ouvre={new Date(h.getTime() - avance * MIN_MS)}
                            ferme={new Date(h.getTime() + retard * MIN_MS)}
                            avanceMin={avance}
                            retardMin={retard}
                          />
                        );
                      })()}
                      <div className="mt-5 flex items-center gap-4">
                        <button
                          type="button"
                          className="wl-btn-primary glow wl-btn-cta-big"
                          disabled={action.enCours}
                          onClick={() => void reserver()}
                        >
                          {action.enCours ? (
                            <Loader2 size={16} className="animate-spin" aria-hidden />
                          ) : (
                            <CalendarCheck size={16} aria-hidden />
                          )}
                          {reservation ? "Prendre cette séance" : "Réserver ma place"}
                        </button>
                        <span className="text-[12px] leading-relaxed text-foreground-muted">
                          {reservation
                            ? "Ta place actuelle n'est libérée que si la nouvelle réservation réussit."
                            : "Tu pourras annuler ou changer de séance ici, jusqu'au dernier moment."}
                        </span>
                      </div>
                      {action.erreur && <p className="mb-0 mt-3 text-[12px] text-[#f87171]">{action.erreur}</p>}
                    </section>
                  )}
                </>
              )}
            </div>
          )}

          {reservation && !changer && enJeu && (
            <>
              <h3 className="mb-3 mt-9 font-display text-[22px] font-normal tracking-wide text-foreground">Avant d'y aller</h3>
              <ul className="m-0 grid list-none grid-cols-3 gap-3 p-0">
                {[
                  { icon: Mic, titre: "Ton micro", texte: "L'oral se passe à la voix, dans le jeu." },
                  { icon: Users, titre: "Sous ton pseudo", texte: "Pas encore de personnage : il se crée après la douane." },
                  { icon: BellRing, titre: "Le rappel", texte: "1 h avant : un message dans le fil de ta candidature." },
                ].map(({ icon: Icon, titre, texte }) => (
                  <li key={titre} className="cand-card !p-4">
                    <Icon size={16} className="text-gold" aria-hidden />
                    <div className="mt-2 text-[13px] font-semibold text-foreground">{titre}</div>
                    <div className="mt-1 text-[12px] leading-relaxed text-foreground-muted">{texte}</div>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}

      <Modal
        open={confirmerAnnulation}
        onClose={action.enCours ? undefined : () => setConfirmerAnnulation(false)}
        variant="warning"
        size="sm"
      >
        <h2 className="pr-7 pt-1 font-display text-[17px] font-semibold leading-tight tracking-wide">
          Annuler ta réservation ?
        </h2>
        <p className="mt-3 text-[12.5px] leading-relaxed text-[var(--color-foreground-subtle)]">
          Ta place {convocation ? `du ${quand(convocation)} ` : ""}est libérée pour un autre candidat. Tu pourras en
          réserver une autre ensuite.
        </p>
        {action.erreur && <p className="mt-3 text-[12px] text-[#f87171]">{action.erreur}</p>}
        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={() => setConfirmerAnnulation(false)}
            disabled={action.enCours}
            autoFocus
            className="flex h-9 flex-1 items-center justify-center rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-overlay)] disabled:opacity-50"
          >
            Garder ma place
          </button>
          <button
            type="button"
            onClick={() => void annuler()}
            disabled={action.enCours}
            className="flex h-9 flex-1 items-center justify-center gap-2 rounded-md bg-[var(--color-danger)] text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:cursor-wait disabled:opacity-70"
          >
            {action.enCours && <RefreshCw className="h-3.5 w-3.5 animate-spin" />}
            Annuler la réservation
          </button>
        </div>
      </Modal>
    </>
  );
}
