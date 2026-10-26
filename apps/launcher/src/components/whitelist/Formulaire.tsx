// Formulaire de la candidature v2 : six onglets (maquettes L1 à L7),
// brouillon sûr, envoi.
//
// Onglets mieux que Ketto (§2.2) : un onglet complet porte une coche verte ;
// un onglet incomplet garde son numéro et une pastille avec le nombre de
// manques (rouge s'il contient une erreur bloquante : texte trop long, âge
// hors bornes, nom de clan), les mêmes nombres que « Il reste à compléter ».
// A / E changent d'onglet hors d'un champ de saisie, Ctrl+PgPréc / Ctrl+PgSuiv
// partout, Échap revient au suivi.
//
// Brouillon (§2.5) : chaque modification est gardée sur ce PC
// (whitelist-store) et part sur le serveur 1,5 s après la dernière frappe, au
// changement d'onglet et quand la fenêtre perd le focus. À l'ouverture, la
// version la plus récente des deux gagne ; plus d'une heure d'écart, on
// demande laquelle reprendre.
//
// Jamais d'écrasement à l'aveugle :
//   - rien ne part tant que le brouillon du serveur n'a pas été LU (ouverture
//     hors ligne, API redémarrée) : il est relu d'abord, et s'il porte autre
//     chose que ce PC, on demande laquelle garder ;
//   - au retour sur la fenêtre (focus, fenêtre de nouveau visible), le
//     brouillon est relu : modifié ailleurs (autre PC), il est repris tel quel
//     si ce PC n'avait rien en attente, sinon on demande ;
//   - tant que la question est posée, rien ne part ;
//   - un brouillon refusé par l'API (texte bien trop long) n'est pas renvoyé
//     en boucle : l'indicateur le dit, la prochaine modification réessaie.
//
// Deux modes :
//   - « nouvelle » : première candidature, ou nouvelle candidature complète
//     après un refus (le dossier précédent sert de point de départ, une
//     nouvelle voix est exigée) → POST /whitelist ;
//   - « revoir » : seules les parties rouvertes se modifient, les autres sont
//     verrouillées → PATCH /whitelist/me.

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { ArrowLeft, ArrowRight, Check, CloudOff, Laptop, Lock, Server } from "lucide-react";
import {
  ecrireBrouillon,
  envoyerCandidature,
  erreursParChamp,
  lireBrouillon,
  renvoyerCandidature,
  type BrouillonVue,
  type CandidatureOptions,
  type CandidatureV2,
  type VoixVue,
} from "../../lib/candidature";
import { formatIpcError, ipcErrorKind } from "../../lib/errors";
import { useAuthStore } from "../../stores/auth-store";
import { useWhitelistStore } from "../../stores/whitelist-store";
import { BarreCandidature } from "./Barre";
import { FormulaireCtx, type ValeurFormulaire, type VoixLocale } from "./contexte";
import { HEURE_MS, quand } from "./format";
import {
  depuisBrouillon,
  depuisCandidature,
  formulaireVide,
  FORMULAIRE_VIDE,
  manques as calculerManques,
  memesDonnees,
  ONGLETS,
  partieDe,
  versBrouillon,
  versEnvoi,
  versRenvoi,
  type Cases,
  type Formulaire,
  type OngletId,
  type Partie,
} from "./regles";
import { EnTete, Enregistrement, type EtatEnregistrement } from "./ui";
import { OngletVous } from "./onglets/Vous";
import { OngletPersonnage } from "./onglets/Personnage";
import { OngletVillage } from "./onglets/Village";
import { OngletHistoire } from "./onglets/Histoire";
import { OngletVoix } from "./onglets/Voix";
import { OngletEnvoi, type EtatEnvoi } from "./onglets/Envoi";

/** Délai de l'enregistrement automatique après la dernière frappe. */
const DELAI_ENREGISTREMENT_MS = 1500;
/** Nouvel essai après un échec. */
const NOUVEL_ESSAI_MS = 10_000;
const KINDS_HORS_LIGNE = new Set(["network", "http", "timeout"]);
/** Brouillon refusé par l'API (texte au-delà de deux fois son maximum, plus
 *  de 256 Ko) : le renvoyer tel quel échouerait encore. */
const KINDS_REFUS = new Set(["invalid_input", "payload_too_large"]);

const CHAMPS_HRP: (keyof Formulaire)[] = ["playerAge", "rpYears", "narutoKnowledge", "discoverySource", "availability"];
const CHAMPS_RP: (keyof Formulaire)[] = [
  "firstName",
  "lastName",
  "characterType",
  "characterSex",
  "characterAge",
  "village",
  "history",
  "objectives",
  "support",
];

/** « À revoir » : les parties validées restent celles du dossier envoyé. */
function superposerVerrous(f: Formulaire, dossier: Formulaire, parties: Partie[]): Formulaire {
  const out: Formulaire = { ...f };
  const copier = (champs: (keyof Formulaire)[]) => {
    for (const c of champs) (out as Record<string, unknown>)[c] = dossier[c];
  };
  if (!parties.includes("HRP")) copier(CHAMPS_HRP);
  if (!parties.includes("RP")) copier(CHAMPS_RP);
  return out;
}

function estChampDeSaisie(t: EventTarget | null): boolean {
  if (!(t instanceof HTMLElement)) return false;
  return t.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(t.tagName);
}

/** Deux versions du brouillon. `affiche` : celle que l'écran montre (et que
 *  le candidat peut continuer à modifier pendant que la question est posée). */
type Conflit = { pc: Formulaire; pcLe: number; serveur: Formulaire; serveurLe: number; affiche: "pc" | "serveur" };

type Props = {
  mode: "nouvelle" | "revoir";
  /** « revoir » : le dossier à corriger ; « nouvelle » après un refus : le dossier précédent. */
  application: CandidatureV2 | null;
  /** « revoir » : parties rouvertes (suivi.partiesRouvertes). */
  partiesRouvertes: Partie[];
  options: CandidatureOptions;
  /** Onglet ouvert au départ (lien direct ?onglet=). */
  ongletInitial?: OngletId;
  onEnvoye: (app: CandidatureV2 | null) => void;
  /** Retour au suivi (« revoir », nouvelle candidature après un refus). */
  onRetour?: () => void;
  /** Le serveur refuse le brouillon (409) : la phase a changé, le suivi est à relire. */
  onPhaseChangee: () => void;
};

export function FormulaireCandidature({
  mode,
  application,
  partiesRouvertes,
  options,
  ongletInitial,
  onEnvoye,
  onRetour,
  onPhaseChangee,
}: Props) {
  const user = useAuthStore((s) => s.user);
  const compte = user?.id ?? null;
  const parties: Partie[] = mode === "revoir" ? partiesRouvertes : ["HRP", "RP"];
  const partiesCle = parties.join();
  const editable = useCallback(
    (o: OngletId) => {
      const p = partieDe(o);
      return p === null || partiesCle.split(",").includes(p);
    },
    [partiesCle],
  );

  const [pret, setPret] = useState(false);
  const [f, setF] = useState<Formulaire>(FORMULAIRE_VIDE);
  const fRef = useRef<Formulaire>(FORMULAIRE_VIDE);
  const [conflit, setConflitEtat] = useState<Conflit | null>(null);
  // Copie lisible hors rendu (enregistrement automatique, frappe).
  const conflitRef = useRef<Conflit | null>(null);
  const setConflit = useCallback((c: Conflit | null) => {
    conflitRef.current = c;
    setConflitEtat(c);
  }, []);
  const [voixServeur, setVoixServeur] = useState<VoixVue | null>(null);
  const [voixLocale, setVoixLocale] = useState<VoixLocale | null>(null);
  const [onglet, setOnglet] = useState<OngletId>(
    () => ongletInitial ?? (mode === "revoir" && !partiesRouvertes.includes("HRP") ? "personnage" : "vous"),
  );
  const [cible, setCible] = useState<{ champ: string; n: number } | null>(null);
  const [enregistrement, setEnregistrement] = useState<EtatEnregistrement>("enregistre");
  const [erreursApi, setErreursApi] = useState<Record<string, string>>({});
  const [montrerErreurs, setMontrerErreurs] = useState(false);
  const [cases, setCases] = useState<Cases>({ rulesAccepted: false, voiceConsent: false });
  const [envoi, setEnvoi] = useState<EtatEnvoi>({ kind: "repos" });

  // ── Enregistrement automatique ─────────────────────────────
  const minuterie = useRef<number | null>(null);
  const enCours = useRef(false);
  const relancer = useRef(false);
  const actif = useRef(true);
  /** Données telles que le serveur les a (JSON de versBrouillon). */
  const dernierEnvoye = useRef<string | null>(null);
  /** Le brouillon du serveur a été lu (ouverture, ou relecture avant le
   *  premier envoi) : d'ici là, rien ne part, pour ne jamais remplacer un
   *  brouillon serveur que ce PC n'a pas vu. */
  const serveurLu = useRef(false);
  /** `updatedAt` du brouillon serveur tel que ce PC l'a vu en dernier (GET, PUT). */
  const serveurMajLe = useRef<string | null>(null);
  const enregistrerRef = useRef<() => Promise<void>>(async () => {});
  const relireRef = useRef<() => Promise<void>>(async () => {});
  const onPhaseChangeeRef = useRef(onPhaseChangee);
  onPhaseChangeeRef.current = onPhaseChangee;

  // Un brouillon plus ancien que le dernier envoi ne se reprend pas.
  const seuil = application ? Date.parse(application.submittedAt) || 0 : 0;
  const dossierDepart = (): Formulaire => (application ? depuisCandidature(application, options) : FORMULAIRE_VIDE);
  /** Formulaire tel que l'écran le montrerait (« À revoir » : parties validées verrouillées). */
  const telQueMontre = (x: Formulaire): Formulaire =>
    mode === "revoir" ? superposerVerrous(x, dossierDepart(), partiesRouvertes) : x;
  /** Ce PC porte des réponses à lui (frappe, copie locale), au-delà du dossier de départ. */
  const aSesReponses = (): boolean =>
    !memesDonnees({ ...fRef.current, micTeste: false }, { ...telQueMontre(dossierDepart()), micTeste: false });

  /**
   * Brouillon du serveur relu (avant le premier envoi, au retour sur la
   * fenêtre) face à ce PC. true : l'envoi peut suivre (rien d'autre sur le
   * serveur, ou version du serveur reprise) ; false : la question « Deux
   * versions » est posée, rien ne part. `enAttente` : ce PC a des
   * modifications pas encore envoyées ; sinon la version du serveur, plus
   * récente, est reprise sans question.
   */
  const accorder = (b: BrouillonVue | null, enAttente: boolean): boolean => {
    if (b) serveurMajLe.current = b.updatedAt;
    const serveur = b && Date.parse(b.updatedAt) > seuil ? b : null;
    if (!serveur) return true;
    const fbBrut = depuisBrouillon(serveur.data, options);
    const jsonServeur = JSON.stringify(versBrouillon(fbBrut));
    // Déjà vu (voix déposée entre-temps, par exemple) : rien de neuf.
    if (jsonServeur === dernierEnvoye.current) return true;
    dernierEnvoye.current = jsonServeur;
    // Un brouillon sans réponse (voix seule) ne remplace rien.
    if (formulaireVide({ ...fbBrut, micTeste: false })) return true;
    const fb = telQueMontre({ ...fbBrut, micTeste: fbBrut.micTeste || fRef.current.micTeste });
    if (memesDonnees(fb, fRef.current)) return true;
    if (!enAttente) {
      fRef.current = fb;
      setF(fb);
      if (compte) useWhitelistStore.getState().garderLocalement(compte, versBrouillon(fb));
      return true;
    }
    const copie = useWhitelistStore.getState().copieLocale;
    setConflit({
      pc: fRef.current,
      pcLe: (copie && Date.parse(copie.majLe)) || Date.now(),
      serveur: fb,
      serveurLe: Date.parse(serveur.updatedAt),
      affiche: "pc",
    });
    setEnregistrement("local");
    return false;
  };

  const annulerMinuterie = () => {
    if (minuterie.current !== null) window.clearTimeout(minuterie.current);
    minuterie.current = null;
  };

  const planifier = useCallback((ms: number) => {
    annulerMinuterie();
    minuterie.current = window.setTimeout(() => {
      minuterie.current = null;
      void enregistrerRef.current();
    }, ms);
  }, []);

  enregistrerRef.current = async () => {
    if (!actif.current) return;
    annulerMinuterie();
    // Question « Deux versions » posée : rien ne part avant la réponse.
    if (conflitRef.current) return;
    if (enCours.current) {
      relancer.current = true;
      return;
    }
    enCours.current = true;
    try {
      if (!serveurLu.current) {
        // Brouillon du serveur jamais lu (ouverture hors ligne, API
        // indisponible) : on le lit avant d'écrire quoi que ce soit.
        setEnregistrement("enregistrement");
        const lu = (await lireBrouillon())?.brouillon ?? null;
        if (!actif.current) return;
        serveurLu.current = true;
        if (lu?.voix) setVoixServeur(lu.voix);
        // Formulaire encore vierge sur ce PC : la version du serveur est
        // reprise ; sinon, deux versions différentes → on demande.
        if (!accorder(lu, aSesReponses())) return;
      }
      const data = versBrouillon(fRef.current);
      const json = JSON.stringify(data);
      if (json === dernierEnvoye.current) {
        setEnregistrement("enregistre");
        return;
      }
      setEnregistrement("enregistrement");
      const rep = await ecrireBrouillon(data);
      dernierEnvoye.current = json;
      if (rep?.brouillon?.updatedAt) serveurMajLe.current = rep.brouillon.updatedAt;
      if (!actif.current) return;
      if (JSON.stringify(versBrouillon(fRef.current)) === json) setEnregistrement("enregistre");
      else relancer.current = true;
    } catch (err) {
      if (!actif.current) return;
      const kind = ipcErrorKind(err) ?? "";
      if (kind === "conflict") {
        // La candidature a changé de phase ailleurs (envoyée, acceptée) :
        // plus d'enregistrement, la copie de ce PC reste.
        actif.current = false;
        setEnregistrement("local");
        onPhaseChangeeRef.current();
        return;
      }
      if (KINDS_REFUS.has(kind)) {
        // Refus de l'API (texte bien trop long) : le renvoyer tel quel
        // échouerait encore. Le message va sous le champ ; la prochaine
        // modification réessaie, pas de boucle toutes les 10 s.
        const parChamp = erreursParChamp(err);
        if (Object.keys(parChamp).length > 0) setErreursApi((e) => ({ ...e, ...parChamp }));
        setEnregistrement("refuse");
        return;
      }
      setEnregistrement(KINDS_HORS_LIGNE.has(kind) ? "hors-ligne" : "echec");
      planifier(NOUVEL_ESSAI_MS);
    } finally {
      enCours.current = false;
      if (relancer.current && actif.current && !conflitRef.current) {
        relancer.current = false;
        void enregistrerRef.current();
      }
    }
  };

  // Retour sur la fenêtre (focus, fenêtre de nouveau visible : launcher
  // laissé ouvert dans la zone de notification pendant qu'on écrivait sur un
  // autre PC) : le brouillon du serveur est relu avant toute frappe.
  relireRef.current = async () => {
    if (!actif.current || !serveurLu.current || conflitRef.current || enCours.current) return;
    let lu: BrouillonVue | null;
    try {
      lu = (await lireBrouillon())?.brouillon ?? null;
    } catch {
      // Hors ligne : l'enregistrement automatique relira de lui-même.
      return;
    }
    if (!actif.current || conflitRef.current || enCours.current || !lu) return;
    if (lu.updatedAt === serveurMajLe.current) return;
    const enAttente =
      minuterie.current !== null || JSON.stringify(versBrouillon(fRef.current)) !== dernierEnvoye.current;
    if (!accorder(lu, enAttente)) annulerMinuterie();
  };

  useEffect(() => {
    let enLecture = false;
    const relire = () => {
      if (enLecture) return;
      enLecture = true;
      void relireRef.current().finally(() => {
        enLecture = false;
      });
    };
    const surVisibilite = () => {
      if (document.visibilityState === "visible") relire();
    };
    window.addEventListener("focus", relire);
    document.addEventListener("visibilitychange", surVisibilite);
    return () => {
      window.removeEventListener("focus", relire);
      document.removeEventListener("visibilitychange", surVisibilite);
    };
  }, []);

  /** Envoi immédiat de ce qui attend (changement d'onglet, perte du focus). */
  const vider = useCallback(() => {
    if (minuterie.current !== null) void enregistrerRef.current();
  }, []);

  useEffect(() => {
    // (Re)montage, StrictMode compris : l'enregistrement est de nouveau actif.
    actif.current = true;
    window.addEventListener("blur", vider);
    return () => {
      window.removeEventListener("blur", vider);
      // Démontage (autre page) : ce qui attend part quand même, sans toucher à l'écran.
      if (minuterie.current !== null) {
        void enregistrerRef.current().finally(() => {
          actif.current = false;
        });
      } else {
        actif.current = false;
      }
      annulerMinuterie();
    };
  }, [vider]);

  // ── Ouverture : brouillon du serveur, copie du PC, dossier ─────
  useEffect(() => {
    let annule = false;
    void (async () => {
      const dossier = dossierDepart();
      let lu = false;
      let horsLigne = false;
      let brouillon: BrouillonVue | null = null;
      try {
        brouillon = (await lireBrouillon())?.brouillon ?? null;
        lu = true;
      } catch (err) {
        horsLigne = KINDS_HORS_LIGNE.has(ipcErrorKind(err) ?? "");
        if (!horsLigne) console.warn("[candidature] lecture du brouillon impossible :", err);
      }
      if (annule) return;
      // Lecture réussie, même sans brouillon : l'enregistrement peut écrire.
      // Sinon, il relira le serveur avant d'écrire.
      serveurLu.current = lu;
      serveurMajLe.current = brouillon?.updatedAt ?? null;
      const copie = useWhitelistStore.getState().copieLocale;
      const local = copie && compte && copie.compte === compte && Date.parse(copie.majLe) > seuil ? copie : null;
      const serveur = brouillon && Date.parse(brouillon.updatedAt) > seuil ? brouillon : null;
      // Un brouillon sans réponse (voix seule) ne remplace pas le dossier repris.
      const fbBrut = serveur ? depuisBrouillon(serveur.data, options) : null;
      const fb = fbBrut && !formulaireVide({ ...fbBrut, micTeste: false }) ? fbBrut : null;
      const flBrut = local ? depuisBrouillon(local.data, options) : null;
      const fl = flBrut && !formulaireVide({ ...flBrut, micTeste: false }) ? flBrut : null;

      let choisi: Formulaire = dossier;
      let aPousser = false;
      if (fb && fl && serveur && local) {
        if (memesDonnees(fb, fl)) choisi = fb;
        else {
          const tb = Date.parse(serveur.updatedAt);
          const tl = Date.parse(local.majLe);
          choisi = tl > tb ? fl : fb;
          if (Math.abs(tl - tb) > HEURE_MS) {
            setConflit({ pc: fl, pcLe: tl, serveur: fb, serveurLe: tb, affiche: tl > tb ? "pc" : "serveur" });
            setEnregistrement("local");
          } else aPousser = tl > tb;
        }
      } else if (fb) choisi = fb;
      else if (fl) {
        choisi = fl;
        aPousser = true;
      }
      if (fbBrut?.micTeste) choisi = { ...choisi, micTeste: true };
      if (mode === "revoir") choisi = superposerVerrous(choisi, dossier, partiesRouvertes);

      dernierEnvoye.current = serveur ? JSON.stringify(versBrouillon(depuisBrouillon(serveur.data, options))) : null;
      fRef.current = choisi;
      setF(choisi);
      // La voix qui compte : celle du brouillon ; en « À revoir », celle du
      // dossier tant qu'aucune nouvelle n'est déposée. Après un refus, une
      // nouvelle voix est exigée (l'API la prend dans le brouillon).
      const voixDossier =
        mode === "revoir" && application?.voix && !application.voiceDeletedAt ? application.voix : null;
      setVoixServeur(brouillon?.voix ?? voixDossier);
      if (!lu) {
        // Le nouvel essai relit d'abord le serveur (enregistrerRef).
        setEnregistrement(horsLigne ? "hors-ligne" : "echec");
        planifier(NOUVEL_ESSAI_MS);
      } else if (aPousser) {
        planifier(0);
      }
      setPret(true);
    })();
    return () => {
      annule = true;
    };
    // Ouverture seulement : le formulaire vit ensuite par lui-même.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Modifications ─────────────────────────────────────────
  const maj = useCallback(
    <K extends keyof Formulaire>(champ: K, valeur: Formulaire[K]) => {
      const next = { ...fRef.current, [champ]: valeur };
      fRef.current = next;
      setF(next);
      if (compte) useWhitelistStore.getState().garderLocalement(compte, versBrouillon(next));
      setErreursApi((e) => {
        if (!(champ in e)) return e;
        const copie = { ...e };
        delete copie[champ as string];
        return copie;
      });
      // Question « Deux versions » posée : gardé sur ce PC seulement, rien
      // ne part avant la réponse.
      if (!actif.current || conflitRef.current) return;
      setEnregistrement((e) => (e === "hors-ligne" || e === "echec" ? e : "enregistrement"));
      planifier(DELAI_ENREGISTREMENT_MS);
    },
    [compte, planifier],
  );

  const setVoixServeurEtEffacer = useCallback((v: VoixVue | null) => {
    setVoixServeur(v);
    setErreursApi((e) => {
      if (!("voix" in e)) return e;
      const copie = { ...e };
      delete copie.voix;
      return copie;
    });
  }, []);

  function choisirVersion(version: "pc" | "serveur") {
    const c = conflitRef.current;
    if (!c) return;
    // La version affichée a pu être modifiée pendant la question : elle est
    // gardée avec ces modifications.
    const garde = version === c.affiche ? fRef.current : version === "pc" ? c.pc : c.serveur;
    const choisi = telQueMontre(garde);
    fRef.current = choisi;
    setF(choisi);
    if (compte) useWhitelistStore.getState().garderLocalement(compte, versBrouillon(choisi));
    setConflit(null);
    // Dans les deux cas : sans effet si le serveur a déjà ces données
    // (dernierEnvoye), sinon la version retenue remplace l'autre.
    planifier(0);
  }

  // ── Navigation entre onglets ──────────────────────────────
  const allerA = useCallback(
    (o: OngletId, champ?: string) => {
      vider();
      setOnglet(o);
      if (champ) setCible((c) => ({ champ, n: (c?.n ?? 0) + 1 }));
    },
    [vider],
  );

  // Champ visé (« Il reste à compléter », « Aller au champ ») : on le montre et on y met le curseur.
  useEffect(() => {
    if (!cible) return;
    const id = window.requestAnimationFrame(() => {
      const el = document.querySelector<HTMLElement>(`[data-champ="${cible.champ}"]`);
      if (!el) return;
      el.scrollIntoView({ block: "center", behavior: "smooth" });
      el.querySelector<HTMLElement>("input:not([type=checkbox]), textarea, select, button")?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(id);
  }, [cible, onglet]);

  const i = ONGLETS.findIndex((o) => o.id === onglet);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.defaultPrevented) return;
      let delta = 0;
      if (e.ctrlKey && e.key === "PageUp") delta = -1;
      else if (e.ctrlKey && e.key === "PageDown") delta = 1;
      else if (!e.ctrlKey && !e.altKey && !e.metaKey && !estChampDeSaisie(e.target)) {
        if (e.key === "a" || e.key === "A") delta = -1;
        else if (e.key === "e" || e.key === "E") delta = 1;
        else if (e.key === "Escape" && onRetour) {
          e.preventDefault();
          vider();
          onRetour();
          return;
        }
      }
      if (delta === 0) return;
      const j = i + delta;
      if (j < 0 || j >= ONGLETS.length) return;
      e.preventDefault();
      allerA(ONGLETS[j].id);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [i, allerA, onRetour, vider]);

  // ── Validation ────────────────────────────────────────────
  const min = options.voix.dureeMinSec;
  const max = options.voix.dureeMaxSec;
  const voixOk =
    voixServeur !== null &&
    (voixServeur.durationSec === null || (voixServeur.durationSec >= min && voixServeur.durationSec <= max));
  const manques = useMemo(() => calculerManques(f, { options, voixOk, editable }), [f, options, voixOk, editable]);

  // ── Envoi ─────────────────────────────────────────────────
  async function envoyer() {
    if (envoi.kind === "envoi") return;
    setEnvoi({ kind: "envoi" });
    try {
      const app =
        mode === "revoir"
          ? await renvoyerCandidature(versRenvoi(fRef.current, parties, cases))
          : await envoyerCandidature(versEnvoi(fRef.current, cases));
      actif.current = false;
      annulerMinuterie();
      useWhitelistStore.getState().oublierCopieLocale();
      onEnvoye(app ?? null);
    } catch (err) {
      const parChamp = erreursParChamp(err);
      setErreursApi(parChamp);
      setMontrerErreurs(true);
      setEnvoi({ kind: "erreur", message: formatIpcError(err, "Envoi de ta candidature impossible"), parChamp });
      document.getElementById("cand-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
    }
  }

  const valeur: ValeurFormulaire = {
    f,
    maj,
    options,
    mode,
    partiesRouvertes: parties,
    editable,
    onglet,
    allerA,
    enregistrement,
    manques,
    montrerErreurs,
    erreursApi,
    voixServeur,
    setVoixServeur: setVoixServeurEtEffacer,
    voixLocale,
    setVoixLocale,
    voixOk,
    discord: user?.discord?.username ?? null,
  };

  const courant = ONGLETS[i];
  const precedent = i > 0 ? ONGLETS[i - 1] : null;
  const suivant = i < ONGLETS.length - 1 ? ONGLETS[i + 1] : null;
  const n = manques.filter((m) => m.onglet === onglet).length;
  const verrou = !editable(onglet);

  const onglets = (
    <>
      {ONGLETS.map((o) => {
        const liste = manques.filter((m) => m.onglet === o.id);
        const ferme = !editable(o.id);
        const complet = o.id !== "envoi" && !ferme && liste.length === 0;
        return (
          <button
            key={o.id}
            type="button"
            className="cand-tab"
            data-active={onglet === o.id || undefined}
            data-complet={complet || undefined}
            data-verrou={ferme || undefined}
            aria-current={onglet === o.id ? "step" : undefined}
            title={ferme ? "Partie validée, verrouillée" : undefined}
            onClick={() => allerA(o.id)}
          >
            {ferme ? (
              <Lock size={13} className="cand-tab-lock" aria-label="verrouillé" />
            ) : complet ? (
              <Check size={15} strokeWidth={3} className="cand-tab-check" aria-label="complet" />
            ) : (
              <span className="cand-tab-num">{o.numero}</span>
            )}
            {o.libelle}
            {!complet && !ferme && o.id !== "envoi" && (
              <span
                className="cand-tab-pill"
                data-ton={liste.some((m) => m.dur) ? "danger" : undefined}
                title={`${liste.length} à compléter`}
              >
                {liste.length}
              </span>
            )}
          </button>
        );
      })}
    </>
  );

  const surtitre: ReactNode =
    courant.partie === null ? (
      <>
        Étape 6 sur 6<span className="cand-eyebrow-sep" aria-hidden />
        {mode === "revoir" ? "Relecture et renvoi" : "Relecture et envoi"}
      </>
    ) : (
      <>
        Étape {courant.numero} sur 6<span className="cand-eyebrow-sep" aria-hidden />
        Partie {courant.partie}
        {verrou && (
          <>
            <span className="cand-eyebrow-sep" aria-hidden />
            validée
          </>
        )}
      </>
    );

  const chapo =
    mode === "revoir"
      ? "Corrige les onglets rouverts puis renvoie. Le reste de ton dossier est gardé tel quel."
      : application
        ? "Nouvelle candidature complète. Ton dossier précédent est repris : relis tout, corrige ce qui doit l'être et enregistre une nouvelle voix."
        : "Le passage obligé pour rejoindre Reborn. Une quinzaine de minutes ; tout s'enregistre au fil de l'eau, tu peux reprendre plus tard, même depuis un autre PC.";

  return (
    <FormulaireCtx.Provider value={valeur}>
      <BarreCandidature onglets={onglets} aides retour={!!onRetour} cle={onglet}>
        {onRetour && (
          <button type="button" className="cand-back" onClick={onRetour}>
            <ArrowLeft size={13} aria-hidden /> Retour au suivi
          </button>
        )}
        <EnTete
          surtitre={surtitre}
          titre="Candidature whitelist"
          chapo={chapo}
          droite={pret ? <Enregistrement etat={enregistrement} /> : undefined}
        />

        {mode === "revoir" && application?.reviewNotes && (
          <blockquote className="cand-quote cand-quote-tete">
            <span className="cand-quote-who">
              Commentaire du staff{application.reviewerName ? ` · ${application.reviewerName}` : ""}
            </span>
            {application.reviewNotes}
          </blockquote>
        )}

        {conflit && (
          <div className="cand-conflit" role="alert">
            <div className="cand-conflit-titre">
              <CloudOff size={15} aria-hidden /> Deux versions de ton brouillon
            </div>
            <p className="cand-conflit-texte">
              Celle de ce PC date du {quand(new Date(conflit.pcLe))}, celle du serveur du {quand(new Date(conflit.serveurLe))}.
              Laquelle reprendre ? L'autre sera remplacée.
            </p>
            <div className="flex flex-wrap gap-3">
              <button type="button" className="cand-btn" onClick={() => choisirVersion("pc")}>
                <Laptop size={14} aria-hidden /> Celle de ce PC
              </button>
              <button type="button" className="cand-btn" onClick={() => choisirVersion("serveur")}>
                <Server size={14} aria-hidden /> Celle du serveur
              </button>
            </div>
          </div>
        )}

        {!pret ? (
          <div className="cand-chargement" role="status">
            Chargement de ton brouillon…
          </div>
        ) : (
          <>
            {verrou && (
              <div className="cand-note cand-note-verrou">
                <Lock size={14} aria-hidden />
                <span>
                  Partie {courant.partie} validée par le staff : elle ne change plus. Seules les parties rouvertes se
                  modifient.
                </span>
              </div>
            )}
            <fieldset className="cand-fieldset" disabled={verrou}>
              {onglet === "vous" && <OngletVous />}
              {onglet === "personnage" && <OngletPersonnage />}
              {onglet === "village" && <OngletVillage />}
              {onglet === "histoire" && <OngletHistoire />}
              {onglet === "voix" && <OngletVoix />}
            </fieldset>
            {onglet === "envoi" && (
              <OngletEnvoi cases={cases} setCases={setCases} envoi={envoi} onEnvoyer={() => void envoyer()} />
            )}

            {onglet !== "envoi" && (
              <div className="cand-actions">
                {precedent && (
                  <button type="button" className="cand-prev" onClick={() => allerA(precedent.id)}>
                    Étape précédente
                  </button>
                )}
                {suivant && (
                  <button
                    type="button"
                    className="wl-btn-primary glow wl-btn-cta-big cand-cta"
                    onClick={() => allerA(suivant.id)}
                  >
                    Continuer <ArrowRight size={16} aria-hidden />
                  </button>
                )}
                <span className="cand-actions-note">
                  {verrou
                    ? "Partie validée, verrouillée."
                    : n === 0
                      ? "Onglet complet."
                      : `${n} champ${n > 1 ? "s" : ""} à compléter ici · tu peux y revenir plus tard.`}
                </span>
              </div>
            )}
          </>
        )}
      </BarreCandidature>
    </FormulaireCtx.Provider>
  );
}
