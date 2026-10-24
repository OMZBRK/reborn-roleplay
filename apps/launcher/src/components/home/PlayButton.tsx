import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router";
import { AlertTriangle, Download, Lock, LogIn, Play, RefreshCw, Square, X } from "lucide-react";
import { LiquidProgress } from "./LiquidProgress";
import { Modal } from "../system/Modal";
import { useLaunchStore } from "../../stores/launch-store";
import { useAuthStore } from "../../stores/auth-store";
import { useDiagnosticsStore } from "../../stores/diagnostics-store";
import { useGameRunning, useGameStore, useGameWindowReady } from "../../stores/game-store";
import { useUpdateStore } from "../../stores/update-store";
import { logout } from "../../lib/auth";
import { formatIpcError, ipcErrorKind } from "../../lib/errors";
import {
  applyUpdate,
  checkUpdate,
  launchGame,
  stopGame,
  type UpdatePreview,
} from "../../lib/launcher";

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]";

/** Filet de securite : si `game:started` n'arrive pas apres un lancement
 *  reussi (evenement perdu), le bouton ne reste pas bloque sur « Preparation ». */
const STARTED_EVENT_GRACE_MS = 5_000;

function formatBytes(n: number): string {
  if (n < 1024) return `${n} o`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} Ko`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} Mo`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} Go`;
}

const BUSY_PHASES = new Set(["blocked", "running", "launching", "downloading", "checking"]);

/** Erreur affichee sous le bouton, avec son `kind` IPC (null si ce n'est pas
 *  une erreur IPC) : il decide du bouton d'action de l'encadre. */
type ShownError = { message: string; kind: string | null };

const shownError = (err: unknown): ShownError => ({
  message: formatIpcError(err),
  kind: ipcErrorKind(err),
});

/** Erreurs qu'une reconnexion regle : l'encadre propose « Se reconnecter ». */
const RELOGIN_KINDS = new Set([
  "mc_session_expired",
  "stored_credentials_expired",
  "not_authenticated",
  "session_expired",
  "unauthorized",
]);

// Le joueur peut quitter l'accueil pendant une longue preparation (premier
// lancement, telechargement) : l'appel continue cote Rust, mais ce bouton
// est demonte. Son issue passe alors par le launch-store (phase) et par
// cette erreur en attente, reprise par la prochaine instance du bouton.
let stashedError: ShownError | null = null;
function takeStashedError(): ShownError | null {
  const e = stashedError;
  stashedError = null;
  return e;
}

// Numero de la derniere verification, toutes instances confondues : seule
// la plus recente decide de la phase. Une verification dont le bouton a ete
// demonte (joueur parti dans Parametres) la conclut quand meme, sinon la
// phase resterait « checking » et verrouillerait l'entretien du jeu.
let lastCheckSeq = 0;

export function PlayButton() {
  const phase = useLaunchStore((s) => s.phase);
  const setPhase = useLaunchStore((s) => s.setPhase);
  const relaunchNonce = useLaunchStore((s) => s.relaunchNonce);
  const demoProgress = useLaunchStore((s) => s.demoProgress);
  // Derniers paquets de progression, tenus par le launch-store (remplis par
  // CrashStub) : ils survivent a un aller-retour hors de l'accueil.
  const progress = useLaunchStore((s) => s.downloadProgress);
  const launch = useLaunchStore((s) => s.launchProgress);
  const user = useAuthStore((s) => s.user);
  const setSession = useAuthStore((s) => s.setSession);
  const navigate = useNavigate();
  const gameRunning = useGameRunning();
  const windowReady = useGameWindowReady();
  // role === PLAYER = compte cree mais whitelist non acceptee. Le backend
  // refusera launch_game (GameError::NotWhitelisted), on bloque ici aussi
  // pour eviter un clic perdu et afficher le bon libelle.
  const isWhitelistGated = user?.role === "PLAYER";

  const [preview, setPreview] = useState<UpdatePreview | null>(null);
  const [error, setError] = useState<ShownError | null>(null);
  // Distingue « la verification a echoue » (reseau, session, API : le bouton
  // devient « Reessayer ») du vrai blocage « launcher trop ancien ».
  const [checkFailed, setCheckFailed] = useState(false);
  const [stopConfirm, setStopConfirm] = useState(false);
  const [stopping, setStopping] = useState(false);

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // Partie en cours : etat du game-store (evenements du backend), ou phase
  // « running » (ex. backend qui repond already_running apres un
  // rechargement de la fenetre, quand le store a perdu la partie).
  const isRunning = gameRunning || phase === "running";

  // Pre-check : manifest, signature, diff. C'est ce qui distingue « Jouer »
  // de « Mettre a jour ». Pendant une partie, il tourne en silence (la phase
  // suit la partie, le backend saute la purge).
  const runCheck = useCallback(async (keepError = false) => {
    const seq = ++lastCheckSeq;
    const inGame = () => useGameStore.getState().running || useLaunchStore.getState().phase === "running";
    // Bouton demonte : on ne touche a la phase que si elle est encore celle
    // de cette verification (rien d'autre ne l'a reprise entre-temps).
    const conclude = (next: "idle" | "ready" | "blocked") => {
      if (mounted.current || useLaunchStore.getState().phase === "checking") setPhase(next);
    };
    if (!inGame()) setPhase("checking");
    if (!keepError) setError(null);
    setCheckFailed(false);
    try {
      const p = await checkUpdate();
      if (seq !== lastCheckSeq) return;
      if (mounted.current) setPreview(p ?? null);
      if (inGame()) return;
      if (!p) conclude("idle");
      else if (p.launcherOutdated) conclude("blocked");
      else conclude(p.plan.length === 0 ? "ready" : "idle");
    } catch (err) {
      if (seq !== lastCheckSeq || inGame()) return;
      if (mounted.current) {
        setError(shownError(err));
        setCheckFailed(true);
      }
      conclude("idle");
    }
  }, [setPhase]);

  // Montage : verification, sauf si une preparation lancee par une instance
  // precedente est encore en cours (on n'ecrase pas sa phase).
  useEffect(() => {
    const stashed = takeStashedError();
    if (stashed) setError(stashed);
    const ph = useLaunchStore.getState().phase;
    if (ph === "launching" || ph === "downloading") return;
    void runCheck(!!stashed);
  }, [runCheck]);

  // Fin d'une preparation suivie par une instance precedente : on reprend
  // son erreur eventuelle et on refait la verification (pas d'apercu ici).
  const prevPhase = useRef(phase);
  useEffect(() => {
    const was = prevPhase.current;
    prevPhase.current = phase;
    if ((was !== "launching" && was !== "downloading") || (phase !== "idle" && phase !== "ready")) {
      return;
    }
    const stashed = takeStashedError();
    if (stashed) setError(stashed);
    if (!preview) void runCheck(!!stashed);
  }, [phase, preview, runCheck]);

  // Debut / fin de partie. La phase elle-meme est tenue par CrashStub (monte
  // en permanence) ; ici on nettoie l'affichage et, a la fin, on refait la
  // verification : le manifest a pu changer pendant la partie.
  const wasRunning = useRef(isRunning);
  useEffect(() => {
    if (isRunning) {
      useLaunchStore.getState().setLaunchProgress(null);
    } else if (wasRunning.current) {
      setStopConfirm(false);
      setStopping(false);
      void runCheck();
    }
    wasRunning.current = isRunning;
  }, [isRunning, runCheck]);

  // Relance demandee depuis l'exterieur (bouton « Relancer » du modal de
  // crash, qui ramene sur l'accueil) : requestRelaunch() incremente
  // relaunchNonce, et toute valeur non nulle est une demande en attente.
  // Elle est CONSOMMEE dans le store (remise a 0) juste avant le clic, pas
  // marquee dans une ref locale : le double montage des effets de
  // StrictMode, un remontage de l'accueil ou un nouveau rendu ne relisent
  // qu'une demande deja consommee et ne relancent jamais le jeu tout seuls.
  // Une verification ou un telechargement en cours est attendu (l'effet
  // repasse a chaque changement de phase), puis on clique une seule fois.
  const handleClickRef = useRef<() => Promise<void>>(async () => {});
  const tryPendingRelaunch = useCallback(() => {
    const store = useLaunchStore.getState();
    if (store.relaunchNonce === 0) return;
    const ph = store.phase;
    if (ph === "checking" || ph === "downloading" || ph === "launching") return;
    useLaunchStore.setState({ relaunchNonce: 0 });
    void handleClickRef.current();
  }, []);

  useEffect(() => {
    tryPendingRelaunch();
  }, [relaunchNonce, phase, tryPendingRelaunch]);

  async function handleClick() {
    if (isWhitelistGated) return;
    if (useGameStore.getState().running || BUSY_PHASES.has(useLaunchStore.getState().phase)) return;
    if (checkFailed || !preview) {
      await runCheck();
      return;
    }
    setError(null);
    // Nouvelle partie : on oublie le diagnostic mod d'une partie precedente
    // pour ne pas le correler par erreur a un eventuel crash de celle-ci.
    useDiagnosticsStore.getState().clear();

    if (preview.plan.length === 0) {
      // Le jeu se prepare (Java, bibliotheques, ressources, Fabric) AVANT de
      // demarrer : phase « launching » avec la progression des etapes.
      useLaunchStore.getState().setLaunchProgress(null);
      setPhase("launching");
      try {
        await launchGame();
        window.setTimeout(() => {
          if (
            useLaunchStore.getState().phase === "launching" &&
            !useGameStore.getState().running
          ) {
            useLaunchStore.getState().setPhase("ready");
          }
        }, STARTED_EVENT_GRACE_MS);
      } catch (err) {
        const shown = shownError(err);
        const store = useLaunchStore.getState();
        if (shown.kind === "already_running") {
          // Le backend a deja une partie (fenetre rechargee pendant le jeu) :
          // on affiche l'etat « en jeu » pour pouvoir l'arreter.
          store.setPhase("running");
        } else if (!useGameStore.getState().running && store.phase === "launching") {
          store.setPhase("ready");
        }
        store.setLaunchProgress(null);
        if (!mounted.current) {
          stashedError = shown;
          return;
        }
        setError(shown);
      }
      return;
    }

    setPhase("downloading");
    useLaunchStore.getState().setDownloadProgress(null);
    try {
      const result = await applyUpdate();
      if (result?.kind === "launcher_outdated") {
        const outdated: ShownError = {
          message: `Ton launcher (v${result.current}) est trop ancien : la version ${result.required} minimum est requise pour jouer. Installe la mise à jour du launcher puis relance.`,
          kind: null,
        };
        useLaunchStore.getState().setPhase("blocked");
        if (mounted.current) setError(outdated);
        else stashedError = outdated;
        return;
      }
      if (!mounted.current) {
        // La prochaine instance refera la verification (cf. prevPhase).
        useLaunchStore.getState().setPhase("idle");
        return;
      }
      await runCheck();
    } catch (err) {
      const shown = shownError(err);
      useLaunchStore.getState().setPhase("idle");
      if (mounted.current) setError(shown);
      else stashedError = shown;
    }
  }
  handleClickRef.current = handleClick;

  // « Se reconnecter » : meme sortie que le menu utilisateur (deconnexion
  // puis ecran de connexion), sans que le joueur ait a la chercher.
  async function relogin() {
    try {
      await logout();
    } catch (err) {
      console.warn("[jouer] déconnexion impossible :", err);
    } finally {
      setSession(null);
      navigate("/login", { replace: true });
    }
  }

  async function confirmStop() {
    setStopping(true);
    setError(null);
    try {
      // Rend la main quand le processus est reellement termine (10 s max) ;
      // game:exited suit et remet le bouton sur « Jouer ».
      await stopGame();
      if (mounted.current) setStopConfirm(false);
    } catch (err) {
      if (!mounted.current) return;
      setStopConfirm(false);
      if (ipcErrorKind(err) === "not_running") {
        // Deja ferme (ou etat desynchronise) : on revient a l'etat normal.
        if (useLaunchStore.getState().phase === "running") setPhase("ready");
        void runCheck();
      } else {
        setError(shownError(err));
      }
    } finally {
      if (mounted.current) setStopping(false);
    }
  }

  const isBlocked = phase === "blocked" || isWhitelistGated;
  const isDownloading = phase === "downloading";
  const isLaunching = phase === "launching" && !isRunning;
  const isChecking = phase === "checking" && !isRunning;
  const hasUpdate = !!preview && preview.plan.length > 0;

  const percent = progress
    ? Math.min(
        100,
        Math.round((progress.bytesDownloaded / Math.max(progress.bytesTotal, 1)) * 100),
      )
    : 0;

  // % de l'etape assets (seule a sous-progression). null pour les autres
  // etapes -> la barre progresse a mi-etape sans valeur figee.
  const launchPercent =
    launch && launch.total
      ? Math.min(100, Math.round(((launch.current ?? 0) / Math.max(launch.total, 1)) * 100))
      : null;

  // Progression globale du lancement pour la barre liquide : chaque etape
  // pese 1/totalSteps. Seule l'etape assets a une sous-progression : elle
  // s'annonce d'abord avec current=0 sans total (game.rs), compte alors
  // pour 0 (a mi-etape, la barre reculait au premier vrai paquet) ; les
  // autres etapes comptent a mi-parcours tant qu'elles tournent.
  const launchOverall = (() => {
    if (!launch) return 0;
    const sub = launch.total
      ? Math.min(1, (launch.current ?? 0) / launch.total)
      : launch.current != null
        ? 0
        : 0.5;
    return Math.min(1, (launch.step - 1 + sub) / Math.max(launch.totalSteps, 1));
  })();

  const needsRelogin = !!error?.kind && RELOGIN_KINDS.has(error.kind);

  const caption = (() => {
    if (isWhitelistGated) return "Candidature whitelist requise";
    if (isRunning) return windowReady || !gameRunning ? "Partie en cours" : "Ouverture de Minecraft…";
    if (isBlocked) return "Launcher trop ancien, mise à jour requise";
    if (isChecking) return "Vérification des fichiers…";
    // Avant « prêt à lancer » ou « Vérification impossible », qui
    // contrediraient l'encadré « session expirée » juste en dessous.
    if (needsRelogin) return "Reconnexion requise";
    if (checkFailed) return "Vérification impossible";
    if (isDownloading) {
      const dl = progress ? formatBytes(progress.bytesDownloaded) : "0 Mo";
      const tot = progress
        ? formatBytes(progress.bytesTotal)
        : preview
          ? formatBytes(preview.bytesTotal)
          : "";
      return `${preview?.version ? `Patch ${preview.version} · ` : ""}${dl}${tot ? ` / ${tot}` : ""}`;
    }
    if (isLaunching) {
      if (!launch) return "Préparation du lancement…";
      const pct = launchPercent != null ? ` ${launchPercent} %` : "…";
      return `Étape ${launch.step}/${launch.totalSteps} · ${launch.label}${pct}`;
    }
    if (hasUpdate && preview) return `Mise à jour ${preview.version} · ${formatBytes(preview.bytesTotal)}`;
    if (preview) return `v${preview.version} · prêt à lancer`;
    return " ";
  })();

  const label = isChecking
    ? "Vérification"
    : checkFailed
      ? "RÉESSAYER"
      : isRunning
        ? "EN JEU"
        : hasUpdate
          ? "METTRE À JOUR"
          : "JOUER";

  // w-full : la pile prend la largeur (fixe) de la colonne d'action de
  // l'accueil ; légende et encadré d'erreur s'y bornent et reviennent à la
  // ligne au lieu d'élargir la colonne.
  return (
    <div className="flex w-full min-w-0 flex-col items-center gap-3">
      {/* Hauteur de la barre liquide (84 px) réservée aussi pour le bouton :
          passer de « Jouer » à « Préparation » ne décale rien. */}
      <div className="flex min-h-[84px] w-full items-center justify-center">
        {demoProgress != null ? (
          <LiquidProgress progress={demoProgress} title="Téléchargement" />
        ) : isBlocked && !isRunning ? (
          <LockedButton label={isWhitelistGated ? "Whitelist requise" : "Inaccessible"} />
        ) : isDownloading ? (
          <LiquidProgress progress={percent / 100} title="Téléchargement" />
        ) : isLaunching ? (
          <LiquidProgress progress={launchOverall} title="Préparation" />
        ) : (
          <PrimaryButton
            label={label}
            disabled={isChecking || isRunning}
            checking={isChecking}
            onClick={() => void handleClick()}
          />
        )}
      </div>

      <div
        className="min-h-5 max-w-full text-center text-[11px] uppercase tracking-[0.18em] text-foreground-muted"
        aria-live="polite"
      >
        {caption}
      </div>

      {/* Emplacement de hauteur fixe sous la légende : « Arrêter le jeu »,
          l'encadré d'erreur (deux lignes au plus) ou « Mettre à jour le
          launcher », sinon rien. Prêt, en jeu et erreur gardent ainsi la
          même hauteur : la colonne étant centrée, chaque changement d'état
          faisait sauter le bouton et descendre les Actualités. */}
      <div className="flex min-h-[52px] w-full items-start justify-center">
        {error ? (
          <div
            role="alert"
            className="flex w-full items-start gap-2 rounded-md border border-danger/40 bg-danger/10 px-3 py-2 text-left text-xs leading-snug text-danger"
          >
            <AlertTriangle className="mt-px h-3.5 w-3.5 flex-shrink-0" />
            <span className="line-clamp-2 min-w-0 flex-1 break-words" title={error.message}>
              {error.message}
            </span>
            {needsRelogin ? (
              <ErrorAction icon={<LogIn className="h-3 w-3" />} onClick={() => void relogin()}>
                Se reconnecter
              </ErrorAction>
            ) : phase === "blocked" && !isWhitelistGated && !isRunning ? (
              <ErrorAction
                icon={<Download className="h-3 w-3" />}
                onClick={() => useUpdateStore.getState().requestOpen()}
              >
                Mettre à jour
              </ErrorAction>
            ) : isRunning ? (
              // Échec de « Arrêter le jeu » : l'encadré prend la place du
              // bouton, on garde de quoi réessayer.
              <ErrorAction
                icon={<Square className="h-2.5 w-2.5" fill="currentColor" />}
                onClick={() => setStopConfirm(true)}
              >
                Arrêter
              </ErrorAction>
            ) : null}
            <button
              type="button"
              onClick={() => setError(null)}
              aria-label="Masquer l'erreur"
              className={`flex h-4 w-4 flex-shrink-0 items-center justify-center rounded opacity-70 hover:opacity-100 ${FOCUS_RING}`}
            >
              <X className="h-3 w-3" />
            </button>
          </div>
        ) : isRunning ? (
          <button
            type="button"
            onClick={() => setStopConfirm(true)}
            disabled={stopping}
            className={`inline-flex items-center gap-2 rounded-lg border border-danger/40 bg-danger/10 px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-danger transition-colors hover:border-danger/70 hover:bg-danger/20 disabled:cursor-wait disabled:opacity-70 ${FOCUS_RING}`}
          >
            {stopping ? (
              <RefreshCw className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Square className="h-3 w-3" fill="currentColor" />
            )}
            {stopping ? "Arrêt en cours…" : "Arrêter le jeu"}
          </button>
        ) : phase === "blocked" && !isWhitelistGated ? (
          <button
            type="button"
            onClick={() => useUpdateStore.getState().requestOpen()}
            className={`inline-flex items-center gap-2 rounded-lg border border-border-strong bg-surface-elevated px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-foreground-subtle transition-colors hover:border-accent/60 hover:text-white ${FOCUS_RING}`}
          >
            <Download className="h-3.5 w-3.5" />
            Mettre à jour le launcher
          </button>
        ) : null}
      </div>

      <Modal
        open={stopConfirm}
        onClose={stopping ? undefined : () => setStopConfirm(false)}
        variant="warning"
        size="sm"
      >
        <h2 className="pr-7 pt-1 font-display text-[17px] font-semibold leading-tight tracking-wide">
          Arrêter le jeu ?
        </h2>
        <p className="mt-3 text-[12.5px] leading-relaxed text-[var(--color-foreground-subtle)]">
          Minecraft va être fermé immédiatement, comme une fermeture forcée. Quand c'est
          possible, préfère quitter depuis le menu du jeu.
        </p>
        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={() => setStopConfirm(false)}
            disabled={stopping}
            autoFocus
            className={`flex h-9 flex-1 items-center justify-center rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-overlay)] disabled:opacity-50 ${FOCUS_RING}`}
          >
            Annuler
          </button>
          <button
            type="button"
            onClick={() => void confirmStop()}
            disabled={stopping}
            className={`flex h-9 flex-1 items-center justify-center gap-2 rounded-md bg-[var(--color-danger)] text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:cursor-wait disabled:opacity-70 ${FOCUS_RING}`}
          >
            {stopping ? (
              <RefreshCw className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Square className="h-3 w-3" fill="currentColor" />
            )}
            {stopping ? "Arrêt en cours…" : "Arrêter le jeu"}
          </button>
        </div>
      </Modal>
    </div>
  );
}

/** Bouton d'action dans l'encadré d'erreur (se reconnecter, mettre à jour). */
function ErrorAction({
  icon,
  onClick,
  children,
}: {
  icon: ReactNode;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex flex-shrink-0 items-center gap-1.5 self-center whitespace-nowrap rounded border border-danger/50 px-2 py-1 text-[11px] font-medium leading-none text-danger transition-colors hover:border-danger/80 hover:bg-danger/20 ${FOCUS_RING}`}
    >
      {icon}
      {children}
    </button>
  );
}

function LockedButton({ label }: { label: string }) {
  return (
    <div className="group relative inline-block">
      <button
        type="button"
        disabled
        className="inline-flex items-center gap-3 rounded-[14px] border border-border-strong bg-surface-elevated px-9 py-4 font-display text-[28px] tracking-[0.08em] text-foreground-muted"
        style={{ minWidth: 240, cursor: "not-allowed" }}
      >
        <Lock className="h-[22px] w-[22px]" strokeWidth={2.2} />
        <span>{label}</span>
      </button>
    </div>
  );
}

function PrimaryButton({
  label,
  disabled,
  checking,
  onClick,
}: {
  label: string;
  disabled: boolean;
  checking: boolean;
  onClick: () => void;
}) {
  // Icone absolute-positioned a gauche pour ne pas decaler le centrage du
  // texte. Sans ca, "Créer mon personnage" (21 chars) tirait le label
  // hors-centre vs "JOUER" (5 chars) qui paraissait centre.
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`reborn-pulse-glow group relative flex items-center justify-center rounded-[14px] px-12 py-4 font-display text-[28px] tracking-[0.08em] text-white transition-transform duration-200 hover:-translate-y-0.5 disabled:cursor-default disabled:opacity-80 disabled:hover:translate-y-0 ${FOCUS_RING} focus-visible:outline-[var(--color-gold-bright)]`}
      style={{
        background: "linear-gradient(180deg, var(--color-accent-hover) 0%, var(--color-accent) 100%)",
        border: "1px solid rgba(255,255,255,0.08)",
        minWidth: 240,
      }}
    >
      <span className="absolute left-5 flex items-center">
        {checking ? (
          <RefreshCw className="h-[26px] w-[26px] animate-spin" strokeWidth={2} />
        ) : (
          <Play className="h-[26px] w-[26px]" strokeWidth={2} fill="currentColor" />
        )}
      </span>
      <span className="text-center">{label}</span>
    </button>
  );
}
