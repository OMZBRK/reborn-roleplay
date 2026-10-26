// Micro de la candidature (onglet Voix, §2.3 et §2.9 « Micro dans le
// launcher ») : test du micro avec vu-mètre, enregistrement 15 à 60 s par
// MediaRecorder (Opus, audio/webm ou audio/ogg), réécoute, forme d'onde.
//
// Les octets partent ensuite en corps brut par lib/candidature.ts ›
// deposerVoix (jamais d'appel HTTP depuis la WebView). Le flux du micro est
// libéré dès la fin du test ou de l'enregistrement.

import { useCallback, useEffect, useRef, useState } from "react";

// ──────────────────────────────────────────────────────
//  Accès au micro
// ──────────────────────────────────────────────────────

export type RefusMicro = "refuse" | "absent" | "occupe" | "indisponible";

export const MESSAGES_MICRO: Record<RefusMicro, string> = {
  refuse: "Le launcher n'a pas accès au micro. Autorise-le dans Windows › Confidentialité › Microphone, puis réessaie.",
  absent: "Aucun micro détecté. Branche un micro (ou un casque avec micro), puis réessaie.",
  occupe:
    "Le micro est déjà utilisé par une autre application, ou Windows le bloque. Ferme-la (Discord, OBS…), puis réessaie.",
  indisponible: "Le launcher ne peut pas enregistrer sur ce système. Mets Windows et le launcher à jour, puis réessaie.",
};

function refusDe(e: unknown): RefusMicro {
  const nom = e instanceof DOMException ? e.name : "";
  if (nom === "NotAllowedError" || nom === "SecurityError" || nom === "PermissionDeniedError") return "refuse";
  if (nom === "NotFoundError" || nom === "DevicesNotFoundError" || nom === "OverconstrainedError") return "absent";
  if (nom === "NotReadableError" || nom === "TrackStartError" || nom === "AbortError") return "occupe";
  return "indisponible";
}

class ErreurMicro extends Error {
  constructor(readonly refus: RefusMicro) {
    super(MESSAGES_MICRO[refus]);
  }
}

async function ouvrirMicro(): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) throw new ErreurMicro("indisponible");
  try {
    return await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    });
  } catch (e) {
    throw new ErreurMicro(refusDe(e));
  }
}

function fermer(stream: MediaStream | null) {
  stream?.getTracks().forEach((t) => t.stop());
}

/** Niveau sonore (0 à 1) lu en continu sur un flux, pour le vu-mètre. */
function mesureur(stream: MediaStream, surNiveau: (n: number, rms: number) => void): () => void {
  const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctx) return () => {};
  const ctx = new Ctx();
  const source = ctx.createMediaStreamSource(stream);
  const analyseur = ctx.createAnalyser();
  analyseur.fftSize = 1024;
  source.connect(analyseur);
  const tampon = new Float32Array(analyseur.fftSize);
  let raf = 0;
  let dernier = 0;
  const tour = (t: number) => {
    raf = requestAnimationFrame(tour);
    if (t - dernier < 70) return;
    dernier = t;
    analyseur.getFloatTimeDomainData(tampon);
    let somme = 0;
    for (const x of tampon) somme += x * x;
    const rms = Math.sqrt(somme / tampon.length);
    surNiveau(Math.min(1, rms * 7), rms);
  };
  raf = requestAnimationFrame(tour);
  return () => {
    cancelAnimationFrame(raf);
    source.disconnect();
    void ctx.close().catch(() => {});
  };
}

// ──────────────────────────────────────────────────────
//  Test du micro
// ──────────────────────────────────────────────────────

export type EtatTest =
  | { kind: "repos" }
  | { kind: "ecoute" }
  | { kind: "ok"; appareil: string | null }
  | { kind: "silence" }
  | { kind: "refus"; refus: RefusMicro };

/** Durée de l'écoute du test. */
const TEST_MS = 3200;
/** Seuil de signal (RMS) qui prouve que le micro capte une voix. */
const SEUIL_VOIX = 0.02;

export function useTestMicro(onOk: () => void) {
  const [etat, setEtat] = useState<EtatTest>({ kind: "repos" });
  const [niveau, setNiveau] = useState(0);
  const arret = useRef<(() => void) | null>(null);
  // Génération du test : un micro qui finit de s'ouvrir après un arrêt, une
  // relance ou le démontage de l'onglet est refermé aussitôt.
  const gen = useRef(0);
  const monte = useRef(true);
  const onOkRef = useRef(onOk);
  onOkRef.current = onOk;

  const stop = useCallback(() => {
    gen.current += 1;
    arret.current?.();
    arret.current = null;
  }, []);

  useEffect(() => {
    monte.current = true;
    return () => {
      monte.current = false;
      stop();
    };
  }, [stop]);

  const lancer = useCallback(async () => {
    stop();
    const g = gen.current;
    setEtat({ kind: "ecoute" });
    let stream: MediaStream;
    try {
      stream = await ouvrirMicro();
    } catch (e) {
      if (g === gen.current && monte.current) {
        setEtat({ kind: "refus", refus: e instanceof ErreurMicro ? e.refus : "indisponible" });
      }
      return;
    }
    if (g !== gen.current || !monte.current) {
      fermer(stream);
      return;
    }
    let max = 0;
    const finMesure = mesureur(stream, (n, rms) => {
      setNiveau(n);
      if (rms > max) max = rms;
    });
    const minuterie = window.setTimeout(() => {
      const appareil = stream.getAudioTracks()[0]?.label || null;
      stop();
      if (max >= SEUIL_VOIX) {
        setEtat({ kind: "ok", appareil });
        onOkRef.current();
      } else {
        setEtat({ kind: "silence" });
      }
    }, TEST_MS);
    arret.current = () => {
      window.clearTimeout(minuterie);
      finMesure();
      fermer(stream);
      setNiveau(0);
    };
  }, [stop]);

  const annuler = useCallback(() => {
    stop();
    setEtat({ kind: "repos" });
  }, [stop]);

  return { etat, niveau, lancer, annuler };
}

// ──────────────────────────────────────────────────────
//  Enregistrement
// ──────────────────────────────────────────────────────

const TYPES_PREFERES = ["audio/webm;codecs=opus", "audio/ogg;codecs=opus", "audio/webm", "audio/ogg"];

function typeEnregistrement(): string | null {
  if (typeof MediaRecorder === "undefined") return null;
  return TYPES_PREFERES.find((t) => MediaRecorder.isTypeSupported(t)) ?? null;
}

export type EtatEnregistreur =
  | { kind: "repos" }
  | { kind: "ouverture" }
  | { kind: "enregistrement"; secondes: number }
  | { kind: "refus"; refus: RefusMicro };

type EntreeEnregistreur = { recorder: MediaRecorder | null; stop: () => void; jeter: boolean };

/**
 * Enregistreur : `demarrer()` ouvre le micro et enregistre jusqu'à `arreter()`
 * ou `maxSec` ; `onFini(blob, dureeSec)` reçoit l'enregistrement complet.
 * Démonter le composant pendant un enregistrement le jette ; pendant
 * l'ouverture du micro (invite d'autorisation, autre onglet choisi), le micro
 * est refermé dès qu'il répond et rien n'est enregistré ni déposé.
 */
export function useEnregistreur(maxSec: number, onFini: (blob: Blob, dureeSec: number) => void) {
  const [etat, setEtat] = useState<EtatEnregistreur>({ kind: "repos" });
  const [niveau, setNiveau] = useState(0);
  const enCours = useRef<EntreeEnregistreur | null>(null);
  const monte = useRef(true);
  const onFiniRef = useRef(onFini);
  onFiniRef.current = onFini;

  useEffect(() => {
    monte.current = true;
    return () => {
      monte.current = false;
      const e = enCours.current;
      if (e) {
        e.jeter = true;
        e.stop();
      }
    };
  }, []);

  const arreter = useCallback(() => {
    enCours.current?.stop();
  }, []);

  const demarrer = useCallback(async () => {
    if (enCours.current) return;
    const type = typeEnregistrement();
    if (!type) {
      setEtat({ kind: "refus", refus: "indisponible" });
      return;
    }
    // Réservation pendant l'ouverture du micro : bloque un double démarrage,
    // et « Arrêter » ou le démontage d'ici là annulent l'enregistrement.
    const reservation: EntreeEnregistreur = {
      recorder: null,
      jeter: false,
      stop: () => {
        reservation.jeter = true;
      },
    };
    enCours.current = reservation;
    setEtat({ kind: "ouverture" });
    let stream: MediaStream;
    try {
      stream = await ouvrirMicro();
    } catch (e) {
      if (enCours.current === reservation) enCours.current = null;
      if (monte.current) setEtat({ kind: "refus", refus: e instanceof ErreurMicro ? e.refus : "indisponible" });
      return;
    }
    if (!monte.current || reservation.jeter) {
      fermer(stream);
      if (enCours.current === reservation) enCours.current = null;
      if (monte.current) setEtat({ kind: "repos" });
      return;
    }
    let recorder: MediaRecorder;
    try {
      recorder = new MediaRecorder(stream, { mimeType: type, audioBitsPerSecond: 48_000 });
    } catch {
      fermer(stream);
      if (enCours.current === reservation) enCours.current = null;
      setEtat({ kind: "refus", refus: "indisponible" });
      return;
    }
    const morceaux: Blob[] = [];
    const debut = performance.now();
    let fin = debut;
    const finMesure = mesureur(stream, (n) => setNiveau(n));
    const chrono = window.setInterval(() => {
      const s = (performance.now() - debut) / 1000;
      setEtat({ kind: "enregistrement", secondes: Math.min(maxSec, s) });
      if (s >= maxSec) entree.stop();
    }, 200);

    const entree: EntreeEnregistreur = {
      recorder,
      jeter: false,
      stop: () => {
        if (recorder.state === "inactive") return;
        fin = performance.now();
        window.clearInterval(chrono);
        try {
          recorder.stop();
        } catch {
          // Déjà arrêté : onstop a déjà fait le ménage.
        }
      },
    };
    enCours.current = entree;

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) morceaux.push(e.data);
    };
    recorder.onstop = () => {
      window.clearInterval(chrono);
      finMesure();
      fermer(stream);
      setNiveau(0);
      enCours.current = null;
      if (entree.jeter) return;
      setEtat({ kind: "repos" });
      // Secondes entières COMPLÈTES : 14,9 s reste 14 s, sous le minimum de
      // 15 s (arrondir au plus proche laissait passer 14,5 s).
      const dureeSec = Math.min(maxSec, Math.floor((fin - debut) / 1000));
      onFiniRef.current(new Blob(morceaux, { type: recorder.mimeType || type }), dureeSec);
    };
    recorder.start(250);
    setEtat({ kind: "enregistrement", secondes: 0 });
  }, [maxSec]);

  return { etat, niveau, demarrer, arreter };
}

// ──────────────────────────────────────────────────────
//  Réécoute et forme d'onde
// ──────────────────────────────────────────────────────

/** Lecture d'un enregistrement : position en secondes (null à l'arrêt). */
export function useLecture(blob: Blob | null) {
  const [position, setPosition] = useState<number | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const audio = useRef<HTMLAudioElement | null>(null);
  const url = useRef<string | null>(null);

  const liberer = useCallback(() => {
    audio.current?.pause();
    audio.current = null;
    if (url.current) URL.revokeObjectURL(url.current);
    url.current = null;
    setPosition(null);
  }, []);

  // Nouvel enregistrement : l'ancienne lecture s'arrête.
  useEffect(() => liberer, [blob, liberer]);

  const basculer = useCallback(async () => {
    if (!blob) return;
    if (audio.current && !audio.current.paused) {
      audio.current.pause();
      setPosition(null);
      return;
    }
    setErreur(null);
    if (!audio.current) {
      url.current = URL.createObjectURL(blob);
      const a = new Audio(url.current);
      a.ontimeupdate = () => setPosition(a.currentTime);
      a.onended = () => {
        a.currentTime = 0;
        setPosition(null);
      };
      audio.current = a;
    }
    try {
      audio.current.currentTime = 0;
      setPosition(0);
      await audio.current.play();
    } catch {
      setPosition(null);
      setErreur("Lecture impossible sur ce PC : vérifie la sortie audio de Windows.");
    }
  }, [blob]);

  return { position, enLecture: position !== null, basculer, erreur };
}

/** Pics de l'enregistrement, `n` barres de 0,15 à 1 ; null tant qu'ils ne sont pas calculés. */
export function usePics(blob: Blob | null, n = 84): number[] | null {
  const [pics, setPics] = useState<number[] | null>(null);
  useEffect(() => {
    setPics(null);
    if (!blob) return;
    let annule = false;
    // Décodage hors ligne de préférence : pas de sortie audio ouverte pour
    // dessiner l'onde, et ça marche aussi sans périphérique de lecture.
    let ctx: BaseAudioContext;
    let fermer = () => {};
    if (typeof OfflineAudioContext !== "undefined") {
      ctx = new OfflineAudioContext(1, 1, 44_100);
    } else {
      const Ctx =
        window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctx) return;
      const enDirect = new Ctx();
      ctx = enDirect;
      fermer = () => void enDirect.close().catch(() => {});
    }
    void (async () => {
      try {
        const audio = await ctx.decodeAudioData(await blob.arrayBuffer());
        const data = audio.getChannelData(0);
        const pas = Math.max(1, Math.floor(data.length / n));
        const brut: number[] = [];
        for (let i = 0; i < n; i++) {
          let max = 0;
          const fin = Math.min(data.length, (i + 1) * pas);
          for (let j = i * pas; j < fin; j++) {
            const v = Math.abs(data[j]);
            if (v > max) max = v;
          }
          brut.push(max);
        }
        const plafond = Math.max(...brut, 0.0001);
        if (!annule) setPics(brut.map((v) => 0.15 + Math.min(1, v / plafond) * 0.85));
      } catch {
        // Format illisible par le décodeur : forme d'onde neutre.
        if (!annule) setPics(Array.from({ length: n }, () => 0.35));
      } finally {
        fermer();
      }
    })();
    return () => {
      annule = true;
    };
  }, [blob, n]);
  return pics;
}
