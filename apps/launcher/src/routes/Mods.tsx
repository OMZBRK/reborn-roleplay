import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  AlertTriangle,
  Boxes,
  Check,
  Cpu,
  Film,
  Eye,
  Hammer,
  Layers,
  Lock,
  Mic,
  Mountain,
  Package,
  Shirt,
  RefreshCw,
  Search,
  ShieldOff,
  Smile,
  Sparkles,
  X,
  ZoomIn,
} from "lucide-react";
import {
  listMods,
  listOptionalMods,
  purgeIncompatibleMods,
  setModPref,
  type ModEntry,
  type OptionalMod,
} from "../lib/launcher";
import { formatIpcError } from "../lib/errors";
import { useTauriEvent } from "../hooks/use-tauri-event";
import { ModCard } from "../components/mods/ModCard";
import { ModsTabs, type ModsTab } from "../components/mods/ModsTabs";
import { useAuthStore } from "../stores/auth-store";
import { useGameRunning } from "../stores/game-store";

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]";

function formatMb(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  return `${mb.toLocaleString("fr-FR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} Mo`;
}

// Grades « staff » — aligné avec is_staff_role() côté Rust (launcher/game.rs)
// et STAFF_ROLES de BuilderButton. Sert à masquer les mods optionnels
// réservés au staff (ex. Axiom, outil de build) aux joueurs normaux.
const STAFF_ROLES = [
  "HELPER",
  "MODELISATEUR",
  "DEVELOPPEUR",
  "MODERATOR",
  "WHITELIST_REVIEWER",
  "ADMIN",
  "OWNER",
];

// Préfixes de fichiers de mods optionnels réservés au staff. Purement un
// filtre d'AFFICHAGE : Axiom sans permissions serveur ne peut rien casser,
// mais on évite de le proposer aux joueurs normaux.
const STAFF_ONLY_OPTIONAL_PREFIXES = ["Axiom"];

function isStaffOnlyOptional(filename: string): boolean {
  return STAFF_ONLY_OPTIONAL_PREFIXES.some((p) => filename.startsWith(p));
}

// Page Mods branchée sur la vraie data du launcher (listMods Rust qui scan
// le dossier mods/ et parse fabric.mod.json). Les mods optionnels du manifest
// (required:false, ex. Distant Horizons) sont listes a part via
// listOptionalMods() avec un toggle activer/desactiver par entree
// (setModPref -> DL ou purge du .jar au prochain lancement). Le user voit
// aussi l'etat reel des mods installes + peut purger les incompatibles.
//
// Lifecycle :
//   - mount → listMods() + listOptionalMods()
//   - event mods:purged (purge des incompatibles au lancement, ou des
//     orphelins par la verification des fichiers) → re-fetch
//   - clic « Purger les mods incompatibles » → purgeIncompatibleMods() puis
//     re-fetch ; refuse par le backend pendant une partie (bouton grise).
export function Mods() {
  const [tab, setTab] = useState<ModsTab>("mods");
  const [query, setQuery] = useState("");
  const [mods, setMods] = useState<ModEntry[]>([]);
  const [optionals, setOptionals] = useState<OptionalMod[]>([]);
  const [loading, setLoading] = useState(true);
  // Erreur de lecture du dossier mods/ (bloque la liste) et erreur d'une
  // action (purge, bascule d'un optionnel) affichees separement : le
  // « Reessayer » de la premiere relit bien le dossier.
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [purging, setPurging] = useState(false);
  const [togglingFile, setTogglingFile] = useState<string | null>(null);
  const [optionalsError, setOptionalsError] = useState<string | null>(null);
  const user = useAuthStore((s) => s.user);
  const isStaff = !!user && STAFF_ROLES.includes(user.role);
  const gameRunning = useGameRunning();

  const refreshSeq = useRef(0);
  const refresh = useCallback(async () => {
    const seq = ++refreshSeq.current;
    setLoading(true);
    setError(null);
    try {
      // listOptionalMods peut echouer (session, reseau : il lit le manifest
      // du serveur). On le tolere : la liste des mods installes reste
      // affichee, la section optionnels montre la vraie cause.
      let optError: unknown = null;
      const [list, opt] = await Promise.all([
        listMods(),
        listOptionalMods().catch((e: unknown) => {
          optError = e ?? "erreur inconnue";
          return null;
        }),
      ]);
      if (seq !== refreshSeq.current) return;
      setMods(Array.isArray(list) ? list : []);
      // Echec transitoire (API lente, token en cours de refresh) : on garde
      // la derniere liste connue au lieu de faire disparaitre la section.
      if (opt) {
        setOptionals(opt);
        setOptionalsError(null);
      } else if (optError) {
        setOptionalsError(
          formatIpcError(optError, "Impossible de récupérer les mods optionnels depuis le serveur"),
        );
      }
    } catch (err) {
      if (seq !== refreshSeq.current) return;
      setError(formatIpcError(err, "Impossible de lire le dossier des mods"));
    } finally {
      if (seq === refreshSeq.current) setLoading(false);
    }
  }, []);

  async function handleToggleOptional(filename: string, nextEnabled: boolean) {
    setTogglingFile(filename);
    setActionError(null);
    // Optimistic update : flip immediatement la valeur, refetch en fond.
    setOptionals((prev) =>
      prev.map((m) => (m.filename === filename ? { ...m, enabled: nextEnabled } : m)),
    );
    try {
      await setModPref(filename, nextEnabled);
      // Pas de re-fetch immediat : l'effet (DL/purge) ne s'applique qu'au
      // prochain "Jouer". Le state local est cohérent avec la pref persistée.
    } catch (err) {
      // Rollback optimistic + remonter l'erreur.
      setOptionals((prev) =>
        prev.map((m) => (m.filename === filename ? { ...m, enabled: !nextEnabled } : m)),
      );
      const label = lookupModMeta(filename).label;
      setActionError(
        formatIpcError(err, `Impossible ${nextEnabled ? "d'activer" : "de désactiver"} ${label}`),
      );
    } finally {
      setTogglingFile(null);
    }
  }

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Le Rust a purge des mods (au lancement ou a la verification) → on
  // rafraichit la liste. Le toast global (DiagnosticToast) previent deja.
  useTauriEvent("mods:purged", () => void refresh());

  async function handlePurge() {
    setPurging(true);
    setActionError(null);
    setNotice(null);
    try {
      const removed = await purgeIncompatibleMods();
      // Le backend renvoie des noms de fichiers ; on tolere d'anciens chemins.
      const names = (Array.isArray(removed) ? removed : []).map(
        (p) => p.split(/[\\/]/).pop() ?? p,
      );
      const s = names.length > 1 ? "s" : "";
      setNotice(
        names.length === 0
          ? "Aucun mod incompatible à supprimer."
          : `${names.length} mod${s} supprimé${s} : ${names.join(", ")}.`,
      );
      await refresh();
    } catch (err) {
      setActionError(formatIpcError(err, "Échec de la purge des mods incompatibles"));
    } finally {
      setPurging(false);
    }
  }

  const filteredMods = useMemo(() => {
    if (!query) return mods;
    const q = query.toLowerCase();
    return mods.filter((m) => {
      const name = (m.modId ?? m.fileName).toLowerCase();
      return name.includes(q) || m.fileName.toLowerCase().includes(q);
    });
  }, [mods, query]);

  // Les mods optionnels staff-only (Axiom) ne sont proposés qu'au staff.
  const visibleOptionals = useMemo(
    () => (isStaff ? optionals : optionals.filter((m) => !isStaffOnlyOptional(m.filename))),
    [optionals, isStaff],
  );

  const incompatibleCount = mods.filter((m) => m.incompatibleWithTarget).length;
  const totalSizeBytes = mods.reduce((acc, m) => acc + m.sizeBytes, 0);

  return (
    <div className="reborn-mods-page reborn-radial-bg-strong reborn-pattern-overlay">
      <div className="reborn-mods-scroll">
        <header className="reborn-page-header">
          <div>
            <div className="reborn-page-kicker">REBORN ROLEPLAY — CLIENT</div>
            <h1 className="reborn-page-title">Mods</h1>
            <p className="reborn-page-sub">
              Mods Fabric installés dans le dossier <code>mods/</code> du launcher.
              La liste vient du serveur via le manifest signé — au prochain
              lancement, les mods manquants sont téléchargés et les incompatibles
              purgés automatiquement.
            </p>
          </div>
          <div className="reborn-page-header-side">
            <div className="reborn-mods-search">
              <Search className="h-3.5 w-3.5" />
              <input
                type="text"
                placeholder="Rechercher un mod…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Rechercher dans les mods"
              />
            </div>
          </div>
        </header>

        <div className="reborn-mods-body">
          <ModsTabs
            active={tab}
            modsCount={mods.length}
            shadersCount={0}
            packsCount={1}
            onChange={setTab}
          />

          <div className="reborn-mods-main">
            <AnimatePresence mode="wait">
              {tab === "mods" && (
                <motion.div
                  key="mods"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.2 }}
                  className="reborn-mods-main-inner"
                >
                  {error && (
                    <div className="reborn-mods-error" role="alert">
                      <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                      <span className="min-w-0 flex-1 leading-relaxed">{error}</span>
                      <button
                        type="button"
                        onClick={() => void refresh()}
                        disabled={loading}
                        className={`reborn-mods-reset-btn flex-shrink-0 ${FOCUS_RING}`}
                      >
                        <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
                        Réessayer
                      </button>
                    </div>
                  )}
                  {actionError && (
                    <div className={`reborn-mods-error ${error ? "mt-2" : ""}`} role="alert">
                      <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                      <span className="min-w-0 flex-1 leading-relaxed">{actionError}</span>
                      <button
                        type="button"
                        onClick={() => setActionError(null)}
                        aria-label="Masquer l'erreur"
                        className={`flex h-6 w-6 flex-shrink-0 items-center justify-center rounded opacity-70 hover:opacity-100 ${FOCUS_RING}`}
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  )}
                  {notice && (
                    <div
                      role="status"
                      className={`flex items-center gap-2 rounded-[var(--radius-md)] border border-[var(--color-success)]/40 bg-[var(--color-success-soft)]/50 px-3 py-2.5 text-xs text-[var(--color-foreground)] ${error || actionError ? "mt-2" : ""}`}
                    >
                      <Check className="h-4 w-4 flex-shrink-0 text-[var(--color-success)]" />
                      <span className="min-w-0 flex-1 break-words leading-relaxed">{notice}</span>
                      <button
                        type="button"
                        onClick={() => setNotice(null)}
                        aria-label="Masquer"
                        className={`flex h-6 w-6 flex-shrink-0 items-center justify-center rounded text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
                      >
                        <X className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  )}
                  {loading && mods.length === 0 && !error ? (
                    <div className="reborn-mods-loading">
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      <span>Chargement des mods installés…</span>
                    </div>
                  ) : filteredMods.length === 0 && mods.length === 0 ? (
                    error ? null : <ModsEmpty />
                  ) : filteredMods.length === 0 ? (
                    <div className="reborn-mods-empty-filter">
                      Aucun mod ne correspond à « {query} ».{" "}
                      <button
                        type="button"
                        onClick={() => setQuery("")}
                        className={`underline underline-offset-2 hover:text-[var(--color-foreground)] ${FOCUS_RING}`}
                      >
                        Effacer la recherche
                      </button>
                    </div>
                  ) : (
                    <div className="reborn-mods-grid">
                      {filteredMods.map((m) => (
                        <ModCard key={m.absolutePath} mod={m} />
                      ))}
                    </div>
                  )}

                  {optionalsError && visibleOptionals.length === 0 && (
                    <div className="reborn-mods-error mt-6" role="alert">
                      <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                      <span className="min-w-0 flex-1 leading-relaxed">{optionalsError}</span>
                      <button
                        type="button"
                        onClick={() => void refresh()}
                        disabled={loading}
                        className={`reborn-mods-reset-btn flex-shrink-0 ${FOCUS_RING}`}
                      >
                        <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
                        Réessayer
                      </button>
                    </div>
                  )}

                  {visibleOptionals.length > 0 && (
                    <section className="mt-6">
                      <div className="mb-3 flex items-baseline justify-between">
                        <h2 className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[var(--color-foreground-muted)]">
                          Mods optionnels disponibles
                        </h2>
                        <span className="text-[10.5px] text-[var(--color-foreground-muted)]">
                          {visibleOptionals.filter((m) => m.enabled).length} /{" "}
                          {visibleOptionals.length} activé
                          {visibleOptionals.filter((m) => m.enabled).length > 1 ? "s" : ""}
                        </span>
                      </div>
                      <p className="mb-3 text-[11.5px] leading-relaxed text-[var(--color-foreground-subtle)]">
                        Mods recommandés pour l'expérience RP Reborn. Coche pour les
                        installer au prochain lancement, décoche pour les retirer
                        proprement (le launcher purgera le .jar du dossier mods/).
                      </p>
                      <div className="reborn-mods-optional-grid">
                        {visibleOptionals.map((m) => (
                          <OptionalModCard
                            key={m.filename}
                            mod={m}
                            disabled={togglingFile === m.filename}
                            onToggle={(next) => void handleToggleOptional(m.filename, next)}
                          />
                        ))}
                      </div>
                    </section>
                  )}
                </motion.div>
              )}

              {tab === "shaders" && (
                <motion.div
                  key="shaders"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.2 }}
                  className="reborn-mods-locked"
                >
                  <div className="reborn-mods-locked-icon">
                    <Sparkles className="h-7 w-7" />
                  </div>
                  <div className="reborn-mods-locked-title">
                    SHADERS — À VENIR
                  </div>
                  <div className="reborn-mods-locked-desc">
                    La détection automatique des shaderpacks installés via Iris
                    arrivera dans une prochaine mise à jour du launcher. Pour
                    l'instant, ajoute tes shaderpacks dans le dossier
                    <code> shaderpacks/</code> du dossier mods Reborn.
                  </div>
                </motion.div>
              )}

              {tab === "packs" && (
                <motion.div
                  key="packs"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.2 }}
                  className="reborn-mods-locked"
                >
                  <div className="reborn-mods-locked-icon">
                    <Lock className="h-7 w-7" />
                  </div>
                  <div className="reborn-mods-locked-title">
                    PACK DE TEXTURES VERROUILLÉ
                  </div>
                  <div className="reborn-mods-locked-desc">
                    Le pack de textures Reborn est forcé par le serveur pour
                    garantir l'identité visuelle du RP. Tu ne peux pas en
                    charger d'autres.
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        <footer className="reborn-mods-footer">
          <div className="reborn-mods-footer-stats">
            <span className="reborn-mods-stat">
              <span className="reborn-mods-stat-dot reborn-mods-stat-dot--required" />
              {mods.length} mod{mods.length > 1 ? "s" : ""} installé
              {mods.length > 1 ? "s" : ""}
            </span>
            {incompatibleCount > 0 && (
              <span className="reborn-mods-stat reborn-mods-stat--warn">
                <span className="reborn-mods-stat-dot reborn-mods-stat-dot--warn" />
                {incompatibleCount} incompatible
                {incompatibleCount > 1 ? "s" : ""}
              </span>
            )}
            <span className="reborn-mods-stat reborn-mods-stat--muted">
              {formatMb(totalSizeBytes)}
            </span>
          </div>
          <button
            type="button"
            onClick={() => void handlePurge()}
            disabled={purging || incompatibleCount === 0 || gameRunning}
            className={`reborn-mods-reset-btn ${FOCUS_RING}`}
            title={
              gameRunning
                ? "Ferme le jeu avant de purger les mods (Minecraft utilise ces fichiers)"
                : incompatibleCount === 0
                  ? "Aucun mod incompatible à purger"
                  : "Supprime du dossier mods/ les .jar incompatibles avec la version de Minecraft ciblée"
            }
          >
            {purging ? (
              <>
                <RefreshCw className="h-3 w-3 animate-spin" />
                Purge en cours…
              </>
            ) : (
              <>
                <RefreshCw className="h-3 w-3" />
                Purger les mods incompatibles
              </>
            )}
          </button>
        </footer>
      </div>
    </div>
  );
}

// Mapping mod-prefix -> {icon, gradient, label} pour rendre les cards
// optionnelles plus identifiables visuellement (style Feather Client).
// Les mods non-mappes tombent sur un fallback "Package" generique.
const MOD_META: Record<
  string,
  { icon: typeof Sparkles; gradient: string; label: string; tag: string }
> = {
  "iris-": {
    icon: Sparkles,
    gradient: "from-cyan-500/40 to-blue-600/40",
    label: "Iris Shaders",
    tag: "Shaders",
  },
  "modmenu-": {
    icon: Layers,
    gradient: "from-violet-500/40 to-purple-700/40",
    label: "Mod Menu",
    tag: "UI",
  },
  "plasmovoice-": {
    icon: Mic,
    gradient: "from-emerald-500/40 to-teal-700/40",
    label: "Plasmo Voice",
    tag: "Audio",
  },
  "emotecraft-": {
    icon: Smile,
    gradient: "from-amber-400/40 to-orange-600/40",
    label: "Emotecraft",
    tag: "RP",
  },
  "continuity-": {
    icon: Boxes,
    gradient: "from-stone-400/40 to-stone-700/40",
    label: "Continuity",
    tag: "Textures",
  },
  "NoChatReports-": {
    icon: ShieldOff,
    gradient: "from-rose-500/40 to-pink-700/40",
    label: "No Chat Reports",
    tag: "Privacy",
  },
  "zoomify-": {
    icon: ZoomIn,
    gradient: "from-sky-500/40 to-indigo-700/40",
    label: "Zoomify",
    tag: "QoL",
  },
  "replaymod-": {
    icon: Film,
    gradient: "from-red-500/40 to-rose-700/40",
    label: "Replay Mod",
    tag: "Capture",
  },
  "entity_model_features-": {
    icon: Sparkles,
    gradient: "from-fuchsia-500/40 to-pink-700/40",
    label: "Entity Model Features",
    tag: "Mobs",
  },
  "DistantHorizons-": {
    icon: Mountain,
    gradient: "from-teal-500/40 to-emerald-700/40",
    label: "Distant Horizons",
    tag: "Vue",
  },
  "firstperson-": {
    icon: Eye,
    gradient: "from-indigo-500/40 to-blue-700/40",
    label: "First-person Model",
    tag: "Caméra",
  },
  "skinlayers3d-": {
    icon: Shirt,
    gradient: "from-orange-400/40 to-amber-600/40",
    label: "3D Skin Layers",
    tag: "Skin",
  },
  Axiom: {
    icon: Hammer,
    gradient: "from-orange-500/40 to-amber-700/40",
    label: "Axiom",
    tag: "Staff · Build",
  },
  "ferritecore-": {
    icon: Cpu,
    gradient: "from-slate-400/40 to-slate-600/40",
    label: "FerriteCore",
    tag: "Perf",
  },
};

function lookupModMeta(filename: string) {
  for (const [prefix, meta] of Object.entries(MOD_META)) {
    if (filename.startsWith(prefix)) return meta;
  }
  return {
    icon: Package,
    gradient: "from-slate-500/40 to-slate-700/40",
    label: filename.replace(/\.jar$/, ""),
    tag: "Mod",
  };
}

function OptionalModCard({
  mod,
  disabled,
  onToggle,
}: {
  mod: OptionalMod;
  disabled: boolean;
  onToggle: (next: boolean) => void;
}) {
  const meta = lookupModMeta(mod.filename);
  const Icon = meta.icon;

  return (
    <div
      className={[
        "group relative flex overflow-hidden rounded-lg border transition-colors",
        mod.enabled
          ? "border-[var(--color-accent)]"
          : "border-[var(--color-border)] hover:border-[var(--color-border-strong)]",
        disabled && "opacity-60",
      ]
        .filter(Boolean)
        .join(" ")}
      style={{
        background: "var(--color-surface-overlay)",
      }}
    >
      {/* Vignette icone gauche avec degrade thematique */}
      <div
        className={[
          "flex h-[84px] w-[84px] flex-shrink-0 items-center justify-center bg-gradient-to-br",
          meta.gradient,
        ].join(" ")}
      >
        <Icon className="h-8 w-8 text-white drop-shadow" strokeWidth={1.8} />
      </div>

      {/* Contenu droit : nom + tag + metas + switch */}
      <div className="flex min-w-0 flex-1 flex-col justify-between p-3">
        <div className="min-w-0">
          <div className="flex min-w-0 items-center justify-between gap-2">
            <h3
              title={meta.label}
              className="min-w-0 flex-1 truncate text-[13px] font-semibold tracking-tight text-[var(--color-foreground)]"
            >
              {meta.label}
            </h3>
            <span className="flex-shrink-0 rounded-sm bg-[var(--color-surface-elevated)] px-1.5 py-0.5 text-[9px] font-medium uppercase tracking-wider text-[var(--color-foreground-muted)]">
              {meta.tag}
            </span>
          </div>
          <div className="mt-0.5 truncate text-[10.5px] text-[var(--color-foreground-muted)]">
            {formatMb(mod.sizeBytes)}
            {mod.installed && mod.enabled && (
              <span className="ml-2 text-[var(--color-success)]">● Installé</span>
            )}
            {!mod.installed && mod.enabled && (
              <span className="ml-2 text-[var(--color-accent)]">● Au prochain lancement</span>
            )}
            {mod.installed && !mod.enabled && (
              <span className="ml-2 text-[var(--color-warning)]">● Sera retiré</span>
            )}
          </div>
        </div>

        {/* Switch toggle style Feather (pill avec circle) */}
        <div className="mt-2 flex justify-end">
          <button
            type="button"
            role="switch"
            aria-checked={mod.enabled}
            aria-label={meta.label}
            title={mod.enabled ? `Désactiver ${meta.label}` : `Activer ${meta.label}`}
            disabled={disabled}
            onClick={() => onToggle(!mod.enabled)}
            className={[
              `relative inline-flex h-5 w-9 cursor-pointer items-center rounded-full transition-colors ${FOCUS_RING}`,
              mod.enabled
                ? "bg-[var(--color-accent)]"
                : "bg-[var(--color-surface-elevated)] hover:bg-[var(--color-border-strong)]",
              disabled && "cursor-not-allowed",
            ]
              .filter(Boolean)
              .join(" ")}
          >
            <span
              className={[
                "inline-block h-3.5 w-3.5 transform rounded-full bg-white shadow-md transition-transform",
                mod.enabled ? "translate-x-[18px]" : "translate-x-[3px]",
              ].join(" ")}
            />
          </button>
        </div>
      </div>
    </div>
  );
}

function ModsEmpty() {
  return (
    <div className="reborn-mods-locked">
      <div className="reborn-mods-locked-icon">
        <Package className="h-7 w-7" />
      </div>
      <div className="reborn-mods-locked-title">AUCUN MOD INSTALLÉ</div>
      <div className="reborn-mods-locked-desc">
        Lance le jeu une fois depuis l'accueil : le launcher téléchargera les
        mods déclarés par le serveur. Tu retrouveras la liste ici après le
        premier lancement.
      </div>
    </div>
  );
}
