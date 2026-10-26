import { useEffect } from "react";
import { create } from "zustand";
import { lireAccesJeu, type AccesJeu } from "../../lib/candidature";
import { refreshMe } from "../../lib/auth";
import { formatIpcError } from "../../lib/errors";
import { startPolling, type PollingHandle } from "../../hooks/use-polling";
import { useAuthStore } from "../../stores/auth-store";

// Accès au jeu pour le bouton de l'accueil (CANDIDATURE_ET_DOUANE §3.3) :
// « Jouer », ou « Passer la douane » dans la fenêtre d'entrée de la séance
// (H − 15 min à H + 30 min), ou grisé avec la raison. Même décision que le
// lancement (lib/candidature.ts › lireAccesJeu, côté Rust decider_acces) :
// la fenêtre est calculée par l'API avec SON horloge.
//
// Un seul sondage partagé entre le bouton et le bloc hero : toutes les
// minutes, au retour de la fenêtre, et pile à l'ouverture et à la fermeture
// de l'entrée. En pause pendant une partie. Un compte whitelisté ou staff
// n'appelle rien : il joue.

type EtatAcces = {
  acces: AccesJeu | null;
  /** Lecture impossible (réseau, session) : message complet. */
  erreur: string | null;
};

const useAccesStore = create<EtatAcces>(() => ({ acces: null, erreur: null }));

const ACCES_JOUEUR: AccesJeu = {
  autorise: true,
  portee: "play",
  phase: "ACCEPTE",
  refus: null,
  convocationAt: null,
  ouvreAt: null,
  fermeAt: null,
};

const SONDAGE_MS = 60_000;

let abonnes = 0;
let sondage: PollingHandle | null = null;
/** Compte (identifiant et rôle) de la dernière lecture. */
let dernierCompte: string | null = null;
let minuteries: number[] = [];

function effacerMinuteries() {
  minuteries.forEach((id) => window.clearTimeout(id));
  minuteries = [];
}

/** Relecture pile à l'ouverture et à la fermeture de l'entrée (+1 s de marge). */
function programmer(a: AccesJeu) {
  effacerMinuteries();
  for (const iso of [a.ouvreAt, a.fermeAt]) {
    const t = iso ? Date.parse(iso) : NaN;
    const ms = t - Date.now() + 1000;
    if (Number.isFinite(ms) && ms > 0 && ms < 2 ** 31 - 1) {
      minuteries.push(window.setTimeout(() => sondage?.trigger(), ms));
    }
  }
}

async function lire(signal: AbortSignal) {
  const user = useAuthStore.getState().user;
  if (!user) return;
  if (user.role !== "PLAYER") {
    effacerMinuteries();
    useAccesStore.setState({ acces: ACCES_JOUEUR, erreur: null });
    return;
  }
  try {
    const a = await lireAccesJeu();
    if (signal.aborted) return;
    // Hors du launcher (navigateur sans Tauri) : rien à lire.
    if (!a) return;
    useAccesStore.setState({ acces: a, erreur: null });
    programmer(a);
    // Accepté pendant que le launcher était ouvert : le Rust a relu le
    // compte ; on met le rôle à jour ici aussi (rail, seconde instance).
    if (a.autorise && a.portee === "play") {
      const frais = await refreshMe().catch(() => null);
      if (frais) useAuthStore.getState().setUser(frais);
    }
  } catch (err) {
    if (signal.aborted) return;
    useAccesStore.setState({ erreur: formatIpcError(err, "Vérification de ta candidature impossible") });
  }
}

function surFocus() {
  sondage?.trigger();
}

/** Relit l'accès tout de suite (après un refus de lancement, « Réessayer »). */
export function actualiserAcces() {
  sondage?.trigger();
}

export function useAccesJeu(): EtatAcces & { estCandidat: boolean } {
  const etat = useAccesStore();
  const role = useAuthStore((s) => s.user?.role ?? null);
  const compte = useAuthStore((s) => (s.user ? `${s.user.id}:${s.user.role}` : null));

  useEffect(() => {
    abonnes += 1;
    if (abonnes === 1) {
      sondage = startPolling(lire, SONDAGE_MS);
      window.addEventListener("focus", surFocus);
    }
    return () => {
      abonnes -= 1;
      if (abonnes === 0) {
        sondage?.stop();
        sondage = null;
        effacerMinuteries();
        window.removeEventListener("focus", surFocus);
      }
    };
  }, []);

  // Autre compte, ou rôle relu : on oublie l'ancien accès et on relit.
  useEffect(() => {
    if (compte === dernierCompte) return;
    const premier = dernierCompte === null;
    dernierCompte = compte;
    useAccesStore.setState({ acces: null, erreur: null });
    // Premier compte : le sondage vient de partir avec lui.
    if (compte && !premier) sondage?.trigger();
  }, [compte]);

  return { ...etat, estCandidat: role === "PLAYER" };
}
