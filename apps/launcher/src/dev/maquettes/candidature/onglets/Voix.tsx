// MAQUETTE L5 — onglet 5 « Voix » (partie RP), §2.3 et Q2.
//
// Obligatoire, 15 à 60 s, test du micro d'abord (la douane se passe à la
// voix). Tout est simulé : aucun accès réel au micro dans la maquette.

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, Mic, MicOff, Pause, Play, RotateCcw, ShieldCheck, Square } from "lucide-react";
import { useDossier } from "../contexte";
import { BORNES } from "../donnees";
import { Champ, Section } from "../ui";

const BARRES_VU = 18;

/** Vu-mètre simulé : une voix qui parle, avec des respirations. */
function useNiveau(actif: boolean): number {
  const [niveau, setNiveau] = useState(0.45);
  useEffect(() => {
    if (!actif) return;
    let t = 0;
    const id = window.setInterval(() => {
      t += 1;
      const souffle = Math.sin(t / 5) * 0.5 + 0.5;
      setNiveau(Math.min(1, Math.max(0.05, souffle * 0.75 + Math.random() * 0.3 - 0.05)));
    }, 110);
    return () => window.clearInterval(id);
  }, [actif]);
  return niveau;
}

function VuMetre({ niveau }: { niveau: number }) {
  const allumees = Math.round(niveau * BARRES_VU);
  return (
    <div className="mqc-meter" aria-hidden>
      {Array.from({ length: BARRES_VU }, (_, i) => (
        <i
          key={i}
          style={{ height: `${8 + (i / BARRES_VU) * 18}px` }}
          data-on={i < allumees || undefined}
          data-haut={i >= BARRES_VU - 4 || undefined}
        />
      ))}
    </div>
  );
}

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

export function OngletVoix() {
  const { dossier: d, maj, scenario } = useDossier();
  const [test, setTest] = useState<"repos" | "ecoute">("repos");
  const [enCours, setEnCours] = useState(false);
  const [chrono, setChrono] = useState(0);
  const [lecture, setLecture] = useState<number | null>(null);
  const timer = useRef<number | null>(null);

  const micOk = d.micTeste && !scenario.micRefuse;
  const duree = d.voixDuree;
  const niveau = useNiveau(test === "ecoute" || micOk || enCours);

  useEffect(
    () => () => {
      if (timer.current !== null) window.clearInterval(timer.current);
    },
    [],
  );

  function lancerTest() {
    setTest("ecoute");
    window.setTimeout(() => {
      setTest("repos");
      maj("micTeste", true);
    }, 2200);
  }

  function arreterChrono() {
    if (timer.current !== null) window.clearInterval(timer.current);
    timer.current = null;
  }

  function enregistrer() {
    setLecture(null);
    setChrono(0);
    setEnCours(true);
    arreterChrono();
    timer.current = window.setInterval(() => {
      setChrono((c) => {
        if (c + 1 >= BORNES.voix.max) {
          arreterChrono();
          setEnCours(false);
          maj("voixDuree", BORNES.voix.max);
          return BORNES.voix.max;
        }
        return c + 1;
      });
    }, 1000);
  }

  function arreter() {
    arreterChrono();
    setEnCours(false);
    maj("voixDuree", chrono);
  }

  function ecouter() {
    if (duree === null) return;
    if (lecture !== null) {
      arreterChrono();
      setLecture(null);
      return;
    }
    setLecture(0);
    arreterChrono();
    timer.current = window.setInterval(() => {
      setLecture((p) => {
        const suivant = (p ?? 0) + 0.25;
        if (suivant >= duree) {
          arreterChrono();
          return null;
        }
        return suivant;
      });
    }, 250);
  }

  // Forme d'onde figée (la maquette n'a pas de vrai son).
  const onde = useMemo(
    () =>
      Array.from({ length: 84 }, (_, i) => {
        const v = Math.abs(Math.sin(i * 0.55) * 0.6 + Math.sin(i * 1.7) * 0.3 + Math.sin(i * 0.13) * 0.25);
        return 0.15 + Math.min(1, v) * 0.85;
      }),
    [],
  );

  const secondes = enCours ? chrono : (duree ?? 0);
  const tropCourt = !enCours && duree !== null && duree < BORNES.voix.min;
  const lu = lecture !== null && duree ? lecture / duree : 0;

  return (
    <>
      <Section
        titre="Sa voix"
        chapo="La douane se passe à la voix, en jeu. Vérifie ton micro, puis fais entendre ton personnage."
      />

      {/* ── 1. Test du micro ── */}
      <div className="mqc-voice-step mb-8">
        <span className="mqc-voice-num" data-ok={micOk || undefined}>
          {micOk ? <Check size={15} strokeWidth={3} /> : "1"}
        </span>
        <Champ label="Tester le micro" className="mqc-flush">
          <div className="mqc-card">
            {scenario.micRefuse ? (
              <div className="flex flex-col gap-3">
                <div className="mqc-note" data-ton="danger" role="alert">
                  <MicOff size={15} aria-hidden />
                  <span>
                    Le launcher n'a pas accès au micro. Autorise-le dans{" "}
                    <strong className="text-foreground">Windows › Confidentialité › Microphone</strong>, puis réessaie.
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <button type="button" className="mqc-btn">
                    <RotateCcw size={14} aria-hidden /> Réessayer
                  </button>
                  <button type="button" className="mqc-link">
                    Ouvrir les réglages du micro
                  </button>
                </div>
              </div>
            ) : micOk ? (
              <div className="flex items-center gap-5">
                <VuMetre niveau={niveau} />
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-semibold text-success">Ton micro fonctionne</div>
                  <div className="mt-0.5 text-[11.5px] text-foreground-muted">Micro par défaut · Casque (USB)</div>
                </div>
                <button type="button" className="mqc-link" onClick={() => maj("micTeste", false)}>
                  Refaire le test
                </button>
              </div>
            ) : test === "ecoute" ? (
              <div className="flex items-center gap-5">
                <VuMetre niveau={niveau} />
                <div className="text-[13px] text-foreground-subtle">Parle normalement, on écoute…</div>
              </div>
            ) : (
              <div className="flex items-center gap-5">
                <button type="button" className="mqc-btn" onClick={lancerTest}>
                  <Mic size={15} aria-hidden /> Tester le micro
                </button>
                <span className="text-[12.5px] text-foreground-muted">Quelques secondes : dis une phrase, on vérifie que ça capte.</span>
              </div>
            )}
          </div>
        </Champ>
      </div>

      {/* ── 2. Enregistrement ── */}
      <div className="mqc-voice-step">
        <span className="mqc-voice-num" data-ok={(duree !== null && !tropCourt && !enCours) || undefined}>
          {duree !== null && !tropCourt && !enCours ? <Check size={15} strokeWidth={3} /> : "2"}
        </span>
        <Champ
          label="Enregistrement vocal"
          droite={<span className="mqc-optional">15 s à 1 min</span>}
          erreur={tropCourt ? "15 secondes au moins. Refais un enregistrement un peu plus long." : null}
          aide="Un seul fichier : refaire remplace le précédent."
          className="mqc-flush"
        >
          <div className="mqc-card" style={{ opacity: micOk ? 1 : 0.5 }}>
            <p className="m-0 text-[13px] leading-relaxed text-foreground">
              Fais entendre ton personnage : qui il est, d'où il vient. Une minute au plus.
            </p>

            {enCours ? (
              <div className="mt-4 flex items-center gap-4">
                <span className="mqc-rec-dot" aria-hidden />
                <span className="mqc-timer">
                  <strong>{mmss(secondes)}</strong> / 1:00
                </span>
                <div className="flex-1">
                  <div className="mqc-progress">
                    <i style={{ width: `${(secondes / BORNES.voix.max) * 100}%`, background: "var(--color-accent-bright)" }} />
                    <b style={{ left: `${(BORNES.voix.min / BORNES.voix.max) * 100}%` }} title="15 s minimum" />
                  </div>
                </div>
                <VuMetre niveau={niveau} />
                <button type="button" className="mqc-btn" data-ton="rec" onClick={arreter}>
                  <Square size={12} fill="currentColor" aria-hidden /> Arrêter
                </button>
              </div>
            ) : duree !== null ? (
              <>
                <div className="mt-4 flex items-center gap-4">
                  <button type="button" className="mqc-play" aria-label={lecture !== null ? "Pause" : "Réécouter"} onClick={ecouter}>
                    {lecture !== null ? <Pause size={17} fill="currentColor" /> : <Play size={17} fill="currentColor" className="ml-0.5" />}
                  </button>
                  <div className="mqc-wave" aria-hidden>
                    {onde.map((h, i) => {
                      // La piste fait 1:00 : seule la part enregistrée porte du son.
                      const vide = i / onde.length >= (duree ?? 0) / BORNES.voix.max;
                      return (
                        <i
                          key={i}
                          style={{ height: vide ? "2px" : `${h * 100}%` }}
                          data-vide={vide || undefined}
                          data-lu={(!vide && lecture !== null && i / onde.length < (lu * (duree ?? 0)) / BORNES.voix.max) || undefined}
                        />
                      );
                    })}
                  </div>
                  <span className="mqc-timer">
                    <strong>{mmss(lecture ?? secondes)}</strong> / 1:00
                  </span>
                </div>
                <div className="mt-4 flex items-center gap-3">
                  <button type="button" className="mqc-btn" onClick={ecouter}>
                    <Play size={13} aria-hidden /> Réécouter
                  </button>
                  <button type="button" className="mqc-btn" onClick={enregistrer} disabled={!micOk}>
                    <RotateCcw size={13} aria-hidden /> Refaire
                  </button>
                  <span className="ml-auto text-[11.5px] text-foreground-muted">Enregistré le 24 octobre à 18 h 21</span>
                </div>
              </>
            ) : (
              <div className="mt-4 flex items-center gap-4">
                <button type="button" className="mqc-btn" data-ton="rec" onClick={enregistrer} disabled={!micOk}>
                  <span className="h-2.5 w-2.5 rounded-full bg-accent-bright" aria-hidden /> Enregistrer
                </button>
                <span className="text-[12.5px] text-foreground-muted">
                  {micOk ? "Prends quelques secondes pour te préparer." : "Teste d'abord ton micro."}
                </span>
              </div>
            )}
          </div>
        </Champ>
      </div>

      <div className="mqc-note mt-8">
        <ShieldCheck size={14} aria-hidden />
        <span>
          Seule l'équipe whitelist écoute ton enregistrement. Il est effacé 30 jours après la décision finale, ou tout de suite
          si tu retires ta candidature.
        </span>
      </div>
    </>
  );
}
