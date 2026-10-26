import { useState } from "react";
import { useNavigate } from "react-router";
import { AlertTriangle, CloudOff, FileText, Info, Play, RefreshCw, UsersRound } from "lucide-react";
import { CartePersonnage, EmplacementLibre } from "../components/personnages/Cartes";
import { DetailPersonnage } from "../components/personnages/Detail";
import { dateDe, dateJour } from "../components/personnages/format";
import { EMPLACEMENTS_DE_BASE } from "../components/personnages/identite";
import "../components/personnages/personnages.css";
import { usePolling } from "../hooks/use-polling";
import { formatIpcError, isIpcErrorKind } from "../lib/errors";
import { fetchMesPersonnages, type PersonnageCompte } from "../lib/personnages";
import { useAuthStore } from "../stores/auth-store";
import { useGameRunning } from "../stores/game-store";
import { useLaunchStore } from "../stores/launch-store";

/**
 * « Mes personnages » (CANDIDATURE_ET_DOUANE.md §4.6, lot J) : les
 * personnages du compte sur le serveur de prod, synchronisés par ShinobiCore
 * (GET /v1/me/characters). Spécification visuelle : la maquette validée L11
 * (dev/maquettes/accueil/MesPersonnages.tsx).
 *
 * Écran en lecture seule : on crée et on joue en jeu. Une carte par
 * personnage (état, N° copiable pour un ticket, village, clan seulement s'il
 * est acquis en session, type, grade, temps de jeu, dernière partie) ; la
 * fiche du personnage choisi à droite, avec le dossier d'entrée du premier
 * (« repris de ta candidature »). Le N° n'apparaît que dans cette vue du
 * propriétaire (GAMEPLAN §1.4).
 */

/** La sauvegarde automatique de ShinobiCore pousse toutes les 60 s. */
const POLL_MS = 60_000;

type Etat =
  | { kind: "chargement" }
  | { kind: "ok"; personnages: PersonnageCompte[]; alerte: string | null }
  | { kind: "erreur"; message: string };

/** Début du décompte du temps de jeu : la plus ancienne première synchronisation. */
function compteDepuis(personnages: PersonnageCompte[]): string | null {
  const dates = personnages.map((p) => dateDe(p.playMinutesSince)).filter((d): d is Date => d !== null);
  if (dates.length === 0) return null;
  return dateJour(new Date(Math.min(...dates.map((d) => d.getTime()))));
}

export function Personnages() {
  const [etat, setEtat] = useState<Etat>({ kind: "chargement" });
  const [choisi, setChoisi] = useState<string | null>(null);

  const relire = usePolling(async (signal) => {
    try {
      const { characters } = await fetchMesPersonnages();
      if (signal.aborted) return;
      setEtat({ kind: "ok", personnages: characters, alerte: null });
    } catch (e) {
      if (signal.aborted) return;
      // API pas encore à jour (route absente) : le launcher peut sortir avant elle.
      const message = isIpcErrorKind(e, "not_found")
        ? "« Mes personnages » n'est pas encore disponible côté Reborn : réessaie après la prochaine mise à jour du serveur."
        : formatIpcError(e, "Chargement de tes personnages impossible");
      // Liste déjà affichée : on la garde et on signale seulement l'échec.
      setEtat((prev) => (prev.kind === "ok" ? { ...prev, alerte: message } : { kind: "erreur", message }));
    }
  }, POLL_MS);

  const personnages = etat.kind === "ok" ? etat.personnages : [];
  const selection = personnages.find((p) => p.characterId === choisi) ?? personnages[0];
  const actifs = personnages.filter((p) => p.state !== "DELETED").length;
  const libres = Math.max(0, EMPLACEMENTS_DE_BASE - actifs);
  const depuis = compteDepuis(personnages);

  return (
    <div className="reborn-perso-cadre">
      <div className="reborn-perso-page">
        <header className="reborn-perso-tete">
          <div>
            <p className="reborn-perso-kicker">Recensement</p>
            <h1 className="reborn-perso-titre">Mes personnages</h1>
            <p className="reborn-perso-sous">
              Les personnages de ton compte sur Reborn. On les crée et on les joue en jeu ; ici, tu les
              retrouves, avec leur N° à donner dans un ticket.
            </p>
          </div>
          {etat.kind === "ok" && (personnages.length > 0 || etat.alerte) && (
            <div className="reborn-perso-resume">
              {personnages.length > 0 && (
                <span className="reborn-perso-chip">
                  <UsersRound className="h-3 w-3" aria-hidden />
                  {personnages.length} {personnages.length > 1 ? "personnages" : "personnage"}
                </span>
              )}
              {depuis && (
                <span className="reborn-perso-note">
                  <Info className="h-3 w-3" aria-hidden />
                  Temps de jeu compté depuis le {depuis}
                </span>
              )}
              {etat.alerte && (
                <span className="reborn-perso-note reborn-perso-note--alerte" role="status" title={etat.alerte}>
                  <AlertTriangle className="h-3 w-3" aria-hidden />
                  Mise à jour impossible, nouvel essai dans une minute
                </span>
              )}
            </div>
          )}
        </header>

        {etat.kind === "chargement" && <Chargement />}
        {etat.kind === "erreur" && (
          <Erreur
            message={etat.message}
            onReessayer={() => {
              setEtat({ kind: "chargement" });
              relire();
            }}
          />
        )}
        {etat.kind === "ok" &&
          (personnages.length === 0 ? (
            <EtatVide />
          ) : (
            <div className="reborn-perso-grille">
              <div className="reborn-perso-liste" role="list" aria-label="Tes personnages">
                {personnages.map((p) => (
                  <CartePersonnage
                    key={p.characterId}
                    perso={p}
                    choisi={p.characterId === selection?.characterId}
                    onChoisir={() => setChoisi(p.characterId)}
                  />
                ))}
                {Array.from({ length: libres }, (_, i) => (
                  <EmplacementLibre key={`libre-${i}`} suivant />
                ))}
              </div>
              {selection && <DetailPersonnage key={selection.characterId} perso={selection} />}
            </div>
          ))}
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Chargement, erreur
// ──────────────────────────────────────────────────────

function Chargement() {
  return (
    <div className="reborn-perso-grille" role="status" aria-label="Chargement de tes personnages">
      <div className="reborn-perso-liste">
        <div className="reborn-perso-squelette animate-pulse" />
        <div className="reborn-perso-squelette animate-pulse" />
      </div>
      <div className="reborn-perso-squelette reborn-perso-squelette--detail animate-pulse" />
    </div>
  );
}

function Erreur({ message, onReessayer }: { message: string; onReessayer: () => void }) {
  return (
    <div className="reborn-perso-erreur" role="alert">
      <span className="reborn-perso-vide-icone" aria-hidden>
        <CloudOff className="h-6 w-6" />
      </span>
      <h2 className="reborn-perso-vide-titre">Personnages indisponibles</h2>
      <p className="reborn-perso-vide-texte">{message}</p>
      <button type="button" className="reborn-perso-bouton-second" onClick={onReessayer}>
        <RefreshCw className="h-3.5 w-3.5" aria-hidden />
        Réessayer
      </button>
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  État vide
// ──────────────────────────────────────────────────────

function EtatVide() {
  const navigate = useNavigate();
  // Compte PLAYER : candidature ou douane pas encore validées.
  const candidat = useAuthStore((s) => s.user?.role === "PLAYER");
  const enJeu = useGameRunning();

  // Le bouton Jouer ne vit que sur l'accueil et garde seul le parcours de
  // lancement (mise à jour, accès par phase) : on y ramène le joueur et on
  // lui demande un lancement, comme « Relancer » après un crash.
  function lancer() {
    navigate("/home");
    useLaunchStore.getState().requestRelaunch();
  }

  return (
    <div className="reborn-perso-vide">
      <div className="reborn-perso-vide-carte">
        <span className="reborn-perso-vide-icone" aria-hidden>
          <UsersRound className="h-6 w-6" />
        </span>
        <h2 className="reborn-perso-vide-titre">Aucun personnage pour l'instant</h2>
        <p className="reborn-perso-vide-texte">
          {candidat
            ? "Ton premier personnage se crée en jeu, une fois ta candidature et ta douane validées, à partir de ce que tu as écrit."
            : "Ton premier personnage se crée en jeu, à ta première connexion, à partir de ta candidature."}
        </p>
        {candidat ? (
          <button type="button" className="reborn-perso-vide-bouton" onClick={() => navigate("/whitelist")}>
            <FileText className="h-4 w-4" aria-hidden />
            Voir ma candidature
          </button>
        ) : (
          <button type="button" className="reborn-perso-vide-bouton" onClick={lancer} disabled={enJeu}>
            <Play className="h-3.5 w-3.5" fill="currentColor" aria-hidden />
            {enJeu ? "Partie en cours" : "Lancer le jeu"}
          </button>
        )}
        <ol className="reborn-perso-etapes">
          <li>
            <span className="reborn-perso-etape-num">1</span>
            <b>Lance le jeu</b>
            <span>La sélection s'ouvre sur « Nouveau shinobi ».</span>
          </li>
          <li>
            <span className="reborn-perso-etape-num">2</span>
            <b>Création pré-remplie</b>
            <span>Repris de ta candidature, sans clan. Village et âge verrouillés ; prénom et sexe modifiables.</span>
          </li>
          <li>
            <span className="reborn-perso-etape-num">3</span>
            <b>Il arrive ici</b>
            <span>En moins d'une minute, avec son N°.</span>
          </li>
        </ol>
      </div>
      <div className="reborn-perso-liste" role="list" aria-label="Emplacements">
        {Array.from({ length: EMPLACEMENTS_DE_BASE }, (_, i) => (
          <EmplacementLibre key={i} />
        ))}
      </div>
    </div>
  );
}
