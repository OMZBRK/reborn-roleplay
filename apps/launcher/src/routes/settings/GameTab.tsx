import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  Check,
  Copy,
  FolderOpen,
  Gamepad2,
  Globe,
  HardDrive,
  MemoryStick,
  Minimize2,
  Monitor,
  Wand2,
  X,
} from "lucide-react";
import { useAuthStore } from "../../stores/auth-store";
import { getSystemSpecs, type SystemSpecs } from "../../lib/system";
import { getGameInfo, openInstallDir, type GameInfo } from "../../lib/launcher";
import { formatIpcError } from "../../lib/errors";
import type { OnGameStart, Preferences } from "../../lib/prefs";
import { usePreferences, type PreferencesEditor } from "./use-preferences";
import { CleanCard, IntegrityCard, ReinstallCard } from "./GameMaintenance";
import { GameLogsCard } from "./GameLogsCard";
import {
  Card,
  COMPACT_BUTTON,
  ErrorPanel,
  formatGo,
  LoadingLine,
  PrettySelect,
  RetryAction,
  Row,
  SaveToast,
  Segmented,
  SpecCard,
  StatusLine,
  Toggle,
} from "./ui";

// Onglet « Jeu » : installation, entretien des fichiers, mémoire Java,
// comportement au lancement, journal. Les cartes d'installation et
// d'entretien ne dépendent pas des préférences : elles restent utilisables
// même si prefs.json est illisible (c'est justement là qu'on a besoin de
// « Réparer »).

export function GameTab() {
  const editor = usePreferences();

  return (
    <>
      <InstallationCard />
      <IntegrityCard />
      <CleanCard />
      <ReinstallCard />

      {editor.prefs ? (
        <>
          <JavaCard prefs={editor.prefs} update={editor.update} />
          <BehaviorCard prefs={editor.prefs} update={editor.update} />
        </>
      ) : editor.loadError ? (
        <ErrorPanel message={editor.loadError} onRetry={editor.reload} retrying={editor.loading} />
      ) : (
        <div className="mb-5">
          <LoadingLine label="Chargement des préférences…" />
        </div>
      )}

      <GameLogsCard />

      <SaveFeedback editor={editor} />
    </>
  );
}

// ──────────────────────────────────────────────────────
//  Installation
// ──────────────────────────────────────────────────────

function InstallationCard() {
  const user = useAuthStore((s) => s.user);
  const [info, setInfo] = useState<GameInfo | null>(null);
  const [infoError, setInfoError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const loadInfo = useCallback(async () => {
    setInfoError(null);
    try {
      setInfo((await getGameInfo()) ?? null);
    } catch (err) {
      setInfoError(formatIpcError(err, "Informations d'installation indisponibles"));
    }
  }, []);

  useEffect(() => {
    void loadInfo();
  }, [loadInfo]);

  async function openDir() {
    setActionError(null);
    try {
      await openInstallDir();
    } catch (err) {
      setActionError(formatIpcError(err, "Impossible d'ouvrir le dossier d'installation"));
    }
  }

  async function copyUuid() {
    if (!user?.minecraftUuid) return;
    setActionError(null);
    try {
      await navigator.clipboard.writeText(user.minecraftUuid);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setActionError(
        "Copie dans le presse-papiers impossible : sélectionne l'UUID et copie-le avec Ctrl+C.",
      );
    }
  }

  const placeholder = infoError ? "Indisponible" : "…";
  const diskPercent =
    info && info.diskTotalGb > 0
      ? Math.min(100, ((info.diskTotalGb - info.diskFreeGb) / info.diskTotalGb) * 100)
      : 0;

  return (
    <Card
      title="Installation"
      description="Emplacement des fichiers du jeu, version et identifiant du compte."
      icon={<FolderOpen className="h-4 w-4" />}
    >
      <div className="px-6 py-5">
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-widest text-foreground-muted">
          Dossier d'installation
        </p>
        <div className="flex items-center gap-2">
          <div className="flex min-w-0 flex-1 items-center gap-2 rounded-md border border-border bg-surface-elevated px-3 py-2.5">
            <FolderOpen className="h-3.5 w-3.5 flex-shrink-0 text-foreground-muted" />
            <span
              className="truncate font-mono text-xs text-foreground-subtle"
              title={info?.installDir}
            >
              {info?.installDir ?? placeholder}
            </span>
          </div>
          <button type="button" onClick={() => void openDir()} className={COMPACT_BUTTON}>
            <FolderOpen className="h-3.5 w-3.5" />
            Ouvrir
          </button>
        </div>
        {infoError && (
          <StatusLine tone="error" action={<RetryAction onClick={() => void loadInfo()} />}>
            {infoError}
          </StatusLine>
        )}
      </div>

      <div className="grid grid-cols-1 gap-3 px-6 pb-5 sm:grid-cols-2">
        <SpecCard
          icon={Gamepad2}
          label="Version du jeu"
          value={info ? `Minecraft ${info.mcVersion}` : placeholder}
          sub="Modpack Reborn"
        />
        <div className="rounded-[12px] border border-border bg-surface-elevated/60 px-4 py-3.5">
          <div className="flex items-center gap-2">
            <HardDrive className="h-3.5 w-3.5 text-foreground-muted" />
            <p className="text-[11px] font-semibold uppercase tracking-widest text-foreground-muted">
              Stockage disque
            </p>
          </div>
          <div
            className="mt-2 h-1.5 overflow-hidden rounded-full bg-border"
            role="progressbar"
            aria-label="Espace disque utilisé"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(diskPercent)}
          >
            <div
              className="h-full rounded-full"
              style={{
                width: `${diskPercent}%`,
                background:
                  "linear-gradient(90deg, var(--color-accent) 0%, var(--color-accent-hover) 100%)",
              }}
            />
          </div>
          <p className="mt-1.5 text-[11.5px] text-foreground-subtle">
            {info
              ? `${info.diskFreeGb.toLocaleString("fr-FR", { maximumFractionDigits: 0 })} Go libres sur ${info.diskTotalGb.toLocaleString("fr-FR", { maximumFractionDigits: 0 })} Go`
              : placeholder}
          </p>
        </div>
      </div>

      <div className="px-6 pb-5">
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-widest text-foreground-muted">
          UUID du compte
        </p>
        <div className="flex items-center gap-2">
          <div className="flex min-w-0 flex-1 items-center rounded-md border border-border bg-surface-elevated px-3 py-2.5">
            <span className="select-all truncate font-mono text-xs text-success">
              {user?.minecraftUuid ?? "—"}
            </span>
          </div>
          <button
            type="button"
            onClick={() => void copyUuid()}
            disabled={!user?.minecraftUuid}
            className={COMPACT_BUTTON}
          >
            {copied ? <Check className="h-3.5 w-3.5 text-success" /> : <Copy className="h-3.5 w-3.5" />}
            {copied ? "Copié" : "Copier"}
          </button>
        </div>
        {actionError && <StatusLine tone="error">{actionError}</StatusLine>}
      </div>
    </Card>
  );
}

// ──────────────────────────────────────────────────────
//  Java & fenêtre
// ──────────────────────────────────────────────────────

const RAM_MIN_MB = 2048;
const RAM_STEP_MB = 512;
/** Plafond du curseur quand la RAM du PC est inconnue (ancien backend). */
const RAM_FALLBACK_MAX_MB = 16384;
/** Délai avant d'écrire prefs.json pendant qu'on fait glisser le curseur. */
const RAM_SAVE_DEBOUNCE_MS = 400;

const RESOLUTIONS = [
  { value: "1280x720", label: "HD 720p" },
  { value: "1920x1080", label: "FullHD 1080p" },
  { value: "2560x1440", label: "2K (1440p)" },
  { value: "3840x2160", label: "4K (2160p)" },
];

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

function JavaCard({ prefs, update }: { prefs: Preferences; update: PreferencesEditor["update"] }) {
  const [specs, setSpecs] = useState<SystemSpecs | null>(null);

  useEffect(() => {
    let cancelled = false;
    getSystemSpecs()
      .then((s) => {
        if (!cancelled) setSpecs(s ?? null);
      })
      // Sans specs, le curseur garde ses bornes par défaut : rien de bloquant.
      .catch((err: unknown) => console.warn("[settings] détection de la RAM impossible :", err));
    return () => {
      cancelled = true;
    };
  }, []);

  // Bornes : plancher 2 Go, plafond = RAM du PC − 2 Go (règle du backend,
  // appliquée de toute façon à -Xmx au lancement), multiple du pas.
  // (Lecture prudente : un backend plus ancien n'envoie pas maxRamMb.)
  const rawMax: number | undefined = specs?.maxRamMb;
  const max = Math.max(
    RAM_MIN_MB,
    Math.floor((typeof rawMax === "number" && rawMax > 0 ? rawMax : RAM_FALLBACK_MAX_MB) / RAM_STEP_MB) *
      RAM_STEP_MB,
  );
  const recommended = clamp(specs?.recommendedRamMb ?? 4096, RAM_MIN_MB, max);
  const value = clamp(prefs.ramMb, RAM_MIN_MB, max);
  const capped = specs !== null && typeof rawMax === "number" && prefs.ramMb > max;
  const ramPercent = max > RAM_MIN_MB ? ((value - RAM_MIN_MB) / (max - RAM_MIN_MB)) * 100 : 100;

  const resolution = `${prefs.width}x${prefs.height}`;
  const resolutionOptions = RESOLUTIONS.some((r) => r.value === resolution)
    ? RESOLUTIONS
    : [...RESOLUTIONS, { value: resolution, label: `${prefs.width} × ${prefs.height}` }];

  return (
    <Card
      title="Java & fenêtre"
      description="Mémoire allouée et résolution — pris en compte au prochain lancement."
      icon={<MemoryStick className="h-4 w-4" />}
    >
      <div className="px-6 py-5">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <p id="ram-label" className="text-[13px] font-medium text-foreground">
                Mémoire allouée à Java
              </p>
              {specs && (
                <span className="rounded-full bg-accent/10 px-2 py-0.5 text-2xs font-semibold tracking-wide text-accent">
                  Recommandé : {formatGo(recommended)}
                </span>
              )}
            </div>
            <p className="mt-1 text-[11.5px] text-foreground-subtle">
              4 Go suffisent pour Reborn ; au-delà, le gain est faible. Windows doit garder au moins
              2 Go pour lui
              {specs && specs.totalRamMb > 0
                ? ` : ton PC a ${formatGo(specs.totalRamMb)} de RAM, donc ${formatGo(max)} maximum pour le jeu.`
                : "."}
            </p>
          </div>
          <div className="flex items-baseline gap-1">
            <span
              className="font-display text-2xl tabular-nums text-foreground"
              style={{ letterSpacing: "0.02em" }}
            >
              {(value / 1024).toLocaleString("fr-FR", {
                minimumFractionDigits: 1,
                maximumFractionDigits: 1,
              })}
            </span>
            <span className="text-xs uppercase tracking-widest text-foreground-muted">Go</span>
          </div>
        </div>

        <div className="relative mt-4">
          <div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 overflow-hidden rounded-full bg-border">
            <div
              className="h-full rounded-full transition-all duration-200"
              style={{
                width: `${ramPercent}%`,
                background:
                  "linear-gradient(90deg, var(--color-accent) 0%, var(--color-accent-hover) 100%)",
                boxShadow: "0 0 12px var(--color-accent-glow)",
              }}
            />
          </div>
          <input
            type="range"
            min={RAM_MIN_MB}
            max={max}
            step={RAM_STEP_MB}
            value={value}
            disabled={max <= RAM_MIN_MB}
            aria-labelledby="ram-label"
            aria-valuetext={formatGo(value)}
            onChange={(e) =>
              update({ ramMb: Number(e.target.value) }, { debounceMs: RAM_SAVE_DEBOUNCE_MS })
            }
            className="reborn-ram-slider relative z-10 w-full rounded-full disabled:cursor-not-allowed"
          />
          <div className="mt-2 flex justify-between text-2xs font-medium uppercase tracking-widest text-foreground-muted">
            <span>{formatGo(RAM_MIN_MB)}</span>
            <span>{formatGo(max)}</span>
          </div>
        </div>

        {capped && (
          <StatusLine tone="warning">
            Réglage enregistré : {formatGo(prefs.ramMb)}, au-delà de ce que ce PC peut donner au
            jeu. Il démarrera avec {formatGo(max)} ; déplace le curseur pour corriger.
          </StatusLine>
        )}

        {specs && prefs.ramMb !== recommended && (
          <button
            type="button"
            onClick={() => update({ ramMb: recommended })}
            className="mt-3 inline-flex items-center gap-1.5 rounded-md border border-accent/30 bg-accent/8 px-3 py-1.5 text-[11px] font-medium text-accent transition hover:bg-accent/15"
          >
            <Wand2 className="h-3 w-3" />
            Utiliser la valeur recommandée ({formatGo(recommended)})
          </button>
        )}
      </div>

      <Row label="Résolution" hint="Taille de la fenêtre Minecraft à l'ouverture.">
        <PrettySelect
          ariaLabel="Résolution de la fenêtre Minecraft"
          value={resolution}
          onChange={(v) => {
            const [w, h] = v.split("x").map(Number);
            if (w > 0 && h > 0) update({ width: w, height: h });
          }}
          options={resolutionOptions}
        />
      </Row>
    </Card>
  );
}

// ──────────────────────────────────────────────────────
//  Comportement
// ──────────────────────────────────────────────────────

const ON_GAME_START_OPTIONS: {
  value: OnGameStart;
  label: string;
  icon: typeof Minimize2;
}[] = [
  { value: "minimize", label: "Réduire le launcher", icon: Minimize2 },
  { value: "stay", label: "Le laisser ouvert", icon: Monitor },
];

function BehaviorCard({
  prefs,
  update,
}: {
  prefs: Preferences;
  update: PreferencesEditor["update"];
}) {
  return (
    <Card
      title="Comportement"
      description="Fenêtre du launcher et options appliquées à chaque lancement du jeu."
      icon={<Gamepad2 className="h-4 w-4" />}
    >
      <Row
        label="Au lancement du jeu"
        hint={
          prefs.onGameStart === "stay"
            ? "Le launcher reste affiché pendant la partie : pratique pour suivre le journal du jeu."
            : "Le launcher se réduit dès que la fenêtre Minecraft s'ouvre, et revient quand tu quittes le jeu."
        }
      >
        <Segmented
          ariaLabel="Au lancement du jeu"
          value={prefs.onGameStart}
          onChange={(v) => update({ onGameStart: v })}
          options={ON_GAME_START_OPTIONS}
        />
      </Row>
      <Row
        label="Fermer le launcher le garde dans la zone de notification"
        hint={
          prefs.closeToTray
            ? "La croix et Alt+F4 le gardent ouvert en arrière-plan : son icône est dans les icônes cachées (flèche ^ près de l'horloge). Un clic dessus le rouvre ; clic droit › « Quitter » le ferme vraiment, « Redémarrer le launcher » le relance."
            : "La croix et Alt+F4 ferment complètement le launcher."
        }
      >
        <Toggle
          label="Fermer le launcher le garde dans la zone de notification"
          checked={prefs.closeToTray}
          onChange={(v) => update({ closeToTray: v })}
        />
      </Row>
      <Row
        label="Connexion automatique au serveur"
        hint="Saute le menu principal et se connecte directement au serveur Reborn."
      >
        <Toggle
          label="Connexion automatique au serveur"
          checked={prefs.autoConnect}
          onChange={(v) => update({ autoConnect: v })}
        />
      </Row>
      <Row
        label="Discord Rich Presence"
        hint="Affiche ton statut RP dans Discord pendant que tu joues."
      >
        <Toggle
          label="Discord Rich Presence"
          checked={prefs.discordRichPresence}
          onChange={(v) => update({ discordRichPresence: v })}
        />
      </Row>
      <Row label="Langue" hint="L'anglais arrive en v1.1 — pour l'instant tout est en français.">
        <PrettySelect
          ariaLabel="Langue"
          value={prefs.language}
          onChange={(v) => update({ language: v })}
          options={[
            { value: "fr", label: "Français" },
            { value: "en", label: "English (bientôt)" },
          ]}
          leadingIcon={Globe}
        />
      </Row>
    </Card>
  );
}

// ──────────────────────────────────────────────────────
//  Retour d'enregistrement
// ──────────────────────────────────────────────────────

/** Pastille « Enregistré » ; en cas d'échec, encadré fixe avec la vraie
 *  cause et « Réessayer » (visible quel que soit le défilement). */
function SaveFeedback({ editor }: { editor: PreferencesEditor }) {
  const [dismissed, setDismissed] = useState<string | null>(null);
  // Nouvelle tentative = l'erreur repart à null : un nouvel échec, même
  // identique, se réaffiche.
  useEffect(() => {
    if (!editor.saveError) setDismissed(null);
  }, [editor.saveError]);
  const error = editor.saveError && editor.saveError !== dismissed ? editor.saveError : null;

  if (!error) return <SaveToast state={editor.saveState} />;
  return (
    <div
      role="alert"
      className="fixed bottom-6 right-6 z-30 flex max-w-[480px] items-start gap-2.5 rounded-[12px] border border-danger/40 bg-surface-elevated px-4 py-3 text-xs leading-snug text-danger shadow-lg"
    >
      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
      <span className="min-w-0 flex-1 break-words">{error}</span>
      <RetryAction onClick={editor.retrySave} />
      <button
        type="button"
        aria-label="Fermer"
        onClick={() => setDismissed(error)}
        className="flex-shrink-0 rounded-md p-1 text-foreground-muted transition hover:text-foreground"
      >
        <X className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}
