// Onglet 5 « Voix » (partie RP), §2.3, Q2 et complément du 25/10, maquette L5.
//
// Obligatoire, 15 à 60 s (bornes de l'API), test du micro d'abord : la douane
// se passe à la voix. L'enregistrement part tout de suite, en corps brut,
// dans le brouillon du serveur (deposerVoix) ; il ne rejoint la candidature
// qu'à l'envoi, avec le consentement. Un seul fichier : refaire remplace le
// précédent.

import { useEffect, useState } from "react";
import { openUrl } from "@tauri-apps/plugin-opener";
import { Check, Loader2, Mic, MicOff, Pause, Play, RotateCcw, ShieldCheck, Square, UploadCloud } from "lucide-react";
import { deposerVoix, ecouterMaVoix } from "../../../lib/candidature";
import { formatIpcError } from "../../../lib/errors";
import { isTauri } from "../../../lib/tauri";
import { useFormulaire } from "../contexte";
import { dateDe, mmss, quand } from "../format";
import { MESSAGES_MICRO, useEnregistreur, useLecture, usePics, useTestMicro, type RefusMicro } from "../voix";
import { Champ, Section } from "../ui";

const BARRES_VU = 18;
const BARRES_ONDE = 84;

function VuMetre({ niveau }: { niveau: number }) {
  const allumees = Math.round(niveau * BARRES_VU);
  return (
    <div className="cand-meter" aria-hidden>
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

async function ouvrirReglagesMicro(): Promise<boolean> {
  if (!isTauri) return false;
  try {
    await openUrl("ms-settings:privacy-microphone");
    return true;
  } catch {
    return false;
  }
}

function RefusDuMicro({ refus, onReessayer }: { refus: RefusMicro; onReessayer: () => void }) {
  const [reglagesKo, setReglagesKo] = useState(false);
  return (
    <div className="flex flex-col gap-3">
      <div className="cand-note" data-ton="danger" role="alert">
        <MicOff size={15} aria-hidden />
        <span>{MESSAGES_MICRO[refus]}</span>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="button" className="cand-btn" onClick={onReessayer}>
          <RotateCcw size={14} aria-hidden /> Réessayer
        </button>
        {refus === "refuse" && (
          <button
            type="button"
            className="cand-link"
            onClick={() => void ouvrirReglagesMicro().then((ok) => setReglagesKo(!ok))}
          >
            Ouvrir les réglages du micro
          </button>
        )}
        {reglagesKo && (
          <span className="text-[11.5px] text-foreground-muted">
            Ouvre Paramètres Windows › Confidentialité et sécurité › Microphone.
          </span>
        )}
      </div>
    </div>
  );
}

type Depot =
  | { kind: "repos" }
  | { kind: "envoi" }
  | { kind: "erreur"; message: string }
  | { kind: "trop-court"; duree: number };

export function OngletVoix() {
  const { f, maj, options, voixServeur, setVoixServeur, voixLocale, setVoixLocale, voixOk, montrerErreurs, erreursApi } =
    useFormulaire();
  const min = options.voix.dureeMinSec;
  const max = options.voix.dureeMaxSec;
  const [depot, setDepot] = useState<Depot>({ kind: "repos" });
  const [lectureKo, setLectureKo] = useState<string | null>(null);

  const test = useTestMicro(() => maj("micTeste", true));
  const micOk = f.micTeste || voixOk;

  async function deposer(blob: Blob, duree: number) {
    setDepot({ kind: "envoi" });
    try {
      const { voix } = await deposerVoix(blob, duree);
      setVoixServeur({ mimeType: voix.mimeType, durationSec: voix.durationSec, uploadedAt: voix.uploadedAt });
      setVoixLocale({ blob, dureeSec: voix.durationSec, depose: true });
      setDepot({ kind: "repos" });
    } catch (err) {
      setDepot({ kind: "erreur", message: formatIpcError(err, "Envoi de l'enregistrement impossible") });
    }
  }

  const rec = useEnregistreur(max, (blob, duree) => {
    setVoixLocale({ blob, dureeSec: duree, depose: false });
    if (duree < min) {
      setDepot({ kind: "trop-court", duree });
      return;
    }
    void deposer(blob, duree);
  });

  // Enregistrement déjà sur le serveur (autre session, autre PC) : relu pour
  // la réécoute et la forme d'onde.
  useEffect(() => {
    if (!voixServeur || voixLocale) return;
    let annule = false;
    ecouterMaVoix(voixServeur.mimeType)
      .then((blob) => {
        if (!annule && blob.size > 0) {
          setVoixLocale({ blob, dureeSec: voixServeur.durationSec ?? 0, depose: true });
        }
      })
      .catch((err: unknown) => {
        if (!annule) setLectureKo(formatIpcError(err, "Réécoute de ton enregistrement impossible"));
      });
    return () => {
      annule = true;
    };
  }, [voixServeur, voixLocale, setVoixLocale]);

  const lecture = useLecture(voixLocale?.blob ?? null);
  const pics = usePics(voixLocale?.blob ?? null, BARRES_ONDE);

  const enCours = rec.etat.kind === "enregistrement" || rec.etat.kind === "ouverture";
  const secondes = rec.etat.kind === "enregistrement" ? rec.etat.secondes : 0;
  // Durée montrée : l'enregistrement local, sinon celui du serveur.
  const duree = voixLocale?.dureeSec ?? voixServeur?.durationSec ?? null;
  const aUnEnregistrement = voixLocale !== null || voixServeur !== null;
  const tropCourt = depot.kind === "trop-court";
  const etape2Ok = voixOk && !enCours && depot.kind !== "envoi" && !tropCourt;
  const refusMicro: RefusMicro | null =
    test.etat.kind === "refus" ? test.etat.refus : rec.etat.kind === "refus" ? rec.etat.refus : null;
  const deposeLe = dateDe(voixServeur?.uploadedAt);

  const erreurEnregistrement =
    erreursApi.voix ??
    (tropCourt
      ? `${min} secondes au moins. Refais un enregistrement un peu plus long.${
          voixServeur ? " Ton enregistrement précédent reste en place d'ici là." : ""
        }`
      : depot.kind === "erreur"
        ? depot.message
        : montrerErreurs && !voixOk
          ? "Enregistre ta voix : elle est obligatoire."
          : null);

  return (
    <>
      <Section
        titre="Sa voix"
        chapo="La douane se passe à la voix, en jeu. Vérifie ton micro, puis fais entendre ton personnage."
      />

      {/* ── 1. Test du micro ── */}
      <div className="cand-voice-step mb-8" data-champ="micTeste">
        <span className="cand-voice-num" data-ok={micOk || undefined}>
          {micOk ? <Check size={15} strokeWidth={3} /> : "1"}
        </span>
        <Champ label="Tester le micro" className="cand-flush">
          <div className="cand-card">
            {refusMicro ? (
              <RefusDuMicro refus={refusMicro} onReessayer={() => void test.lancer()} />
            ) : test.etat.kind === "ecoute" ? (
              <div className="flex items-center gap-5">
                <VuMetre niveau={test.niveau} />
                <div className="text-[13px] text-foreground-subtle">Parle normalement, on écoute…</div>
              </div>
            ) : test.etat.kind === "silence" ? (
              <div className="flex flex-col gap-3">
                <div className="cand-note" data-ton="warning" role="alert">
                  <MicOff size={15} aria-hidden />
                  <span>
                    On n'entend rien. Vérifie que ton micro n'est pas coupé (bouton du casque) et qu'il est choisi comme
                    micro par défaut dans Windows.
                  </span>
                </div>
                <div>
                  <button type="button" className="cand-btn" onClick={() => void test.lancer()}>
                    <RotateCcw size={14} aria-hidden /> Refaire le test
                  </button>
                </div>
              </div>
            ) : micOk ? (
              <div className="flex items-center gap-5">
                <VuMetre niveau={enCours ? rec.niveau : 0.45} />
                <div className="min-w-0 flex-1">
                  <div className="text-[13px] font-semibold text-success">Ton micro fonctionne</div>
                  <div className="mt-0.5 truncate text-[11.5px] text-foreground-muted">
                    {test.etat.kind === "ok" && test.etat.appareil ? test.etat.appareil : "Micro par défaut de Windows"}
                  </div>
                </div>
                <button
                  type="button"
                  className="cand-link"
                  disabled={enCours}
                  onClick={() => {
                    maj("micTeste", false);
                    void test.lancer();
                  }}
                >
                  Refaire le test
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-5">
                <button type="button" className="cand-btn" onClick={() => void test.lancer()}>
                  <Mic size={15} aria-hidden /> Tester le micro
                </button>
                <span className="text-[12.5px] text-foreground-muted">
                  Quelques secondes : dis une phrase, on vérifie que ça capte.
                </span>
              </div>
            )}
          </div>
        </Champ>
      </div>

      {/* ── 2. Enregistrement ── */}
      <div className="cand-voice-step" data-champ="voix">
        <span className="cand-voice-num" data-ok={etape2Ok || undefined}>
          {etape2Ok ? <Check size={15} strokeWidth={3} /> : "2"}
        </span>
        <Champ
          label="Enregistrement vocal"
          droite={<span className="cand-optional">{`${min} s à ${max === 60 ? "1 min" : `${max} s`}`}</span>}
          erreur={erreurEnregistrement}
          aide="Un seul fichier : refaire remplace le précédent."
          className="cand-flush"
        >
          <div className="cand-card" style={{ opacity: micOk || aUnEnregistrement ? 1 : 0.5 }}>
            <p className="m-0 text-[13px] leading-relaxed text-foreground">
              Fais entendre ton personnage : qui il est, d'où il vient. Une minute au plus.
            </p>

            {enCours ? (
              <div className="mt-4 flex items-center gap-4">
                <span className="cand-rec-dot" aria-hidden />
                <span className="cand-timer">
                  <strong>{mmss(secondes)}</strong> / {mmss(max)}
                </span>
                <div className="flex-1">
                  <div className="cand-progress">
                    <i style={{ width: `${(secondes / max) * 100}%`, background: "var(--color-accent-bright)" }} />
                    <b style={{ left: `${(min / max) * 100}%` }} title={`${min} s minimum`} />
                  </div>
                </div>
                <VuMetre niveau={rec.niveau} />
                <button type="button" className="cand-btn" data-ton="rec" onClick={rec.arreter}>
                  <Square size={12} fill="currentColor" aria-hidden /> Arrêter
                </button>
              </div>
            ) : aUnEnregistrement && duree !== null ? (
              <>
                <div className="mt-4 flex items-center gap-4">
                  <button
                    type="button"
                    className="cand-play"
                    aria-label={lecture.enLecture ? "Pause" : "Réécouter"}
                    disabled={!voixLocale}
                    onClick={() => void lecture.basculer()}
                  >
                    {lecture.enLecture ? (
                      <Pause size={17} fill="currentColor" />
                    ) : (
                      <Play size={17} fill="currentColor" className="ml-0.5" />
                    )}
                  </button>
                  <div className="cand-wave" aria-hidden>
                    {Array.from({ length: BARRES_ONDE }, (_, i) => {
                      // La piste fait la durée maximale : seule la part enregistrée porte du son.
                      const vide = i / BARRES_ONDE >= duree / max;
                      const h = pics ? pics[Math.min(pics.length - 1, Math.floor((i / BARRES_ONDE) * (max / Math.max(duree, 1)) * pics.length))] : 0.3;
                      const lu = lecture.position !== null && i / BARRES_ONDE < lecture.position / max;
                      return (
                        <i
                          key={i}
                          style={{ height: vide ? "2px" : `${(h ?? 0.3) * 100}%` }}
                          data-vide={vide || undefined}
                          data-lu={(!vide && lu) || undefined}
                        />
                      );
                    })}
                  </div>
                  <span className="cand-timer">
                    <strong>{mmss(lecture.position ?? duree)}</strong> / {mmss(max)}
                  </span>
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-3">
                  <button type="button" className="cand-btn" disabled={!voixLocale} onClick={() => void lecture.basculer()}>
                    <Play size={13} aria-hidden /> Réécouter
                  </button>
                  <button type="button" className="cand-btn" onClick={() => void rec.demarrer()} disabled={!micOk}>
                    <RotateCcw size={13} aria-hidden /> Refaire
                  </button>
                  {depot.kind === "erreur" && voixLocale && !voixLocale.depose && (
                    <button
                      type="button"
                      className="cand-btn"
                      onClick={() => void deposer(voixLocale.blob, voixLocale.dureeSec)}
                    >
                      <UploadCloud size={13} aria-hidden /> Réessayer l'envoi
                    </button>
                  )}
                  <span className="ml-auto text-[11.5px] text-foreground-muted">
                    {depot.kind === "envoi" ? (
                      <span className="inline-flex items-center gap-1.5">
                        <Loader2 size={12} className="animate-spin" aria-hidden /> Envoi de l'enregistrement…
                      </span>
                    ) : voixLocale && !voixLocale.depose ? (
                      "Pas encore sur le serveur"
                    ) : deposeLe ? (
                      `Enregistré le ${quand(deposeLe)}`
                    ) : null}
                  </span>
                </div>
                {(lecture.erreur || (lectureKo && !voixLocale)) && (
                  <div className="mt-3 text-[11.5px] text-foreground-muted">{lecture.erreur ?? lectureKo}</div>
                )}
              </>
            ) : (
              <div className="mt-4 flex items-center gap-4">
                <button
                  type="button"
                  className="cand-btn"
                  data-ton="rec"
                  onClick={() => void rec.demarrer()}
                  disabled={!micOk}
                >
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

      <div className="cand-note mt-8">
        <ShieldCheck size={14} aria-hidden />
        <span>
          Seule l'équipe whitelist écoute ton enregistrement. Tant que ta candidature n'est pas envoyée, il reste dans
          ton brouillon : sans activité sur le brouillon pendant 30 jours, il est effacé. Après la décision finale, il est
          effacé au bout de 30 jours, ou tout de suite si tu retires ta candidature.
        </span>
      </div>
    </>
  );
}
