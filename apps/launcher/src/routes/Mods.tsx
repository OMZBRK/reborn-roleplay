import { useCallback, useEffect, useMemo, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  AlertTriangle,
  Lock,
  Package,
  RefreshCw,
  Search,
  Sparkles,
} from "lucide-react";
import {
  listMods,
  listOptionalMods,
  onModsPurged,
  purgeIncompatibleMods,
  setModPref,
  type ModEntry,
  type ModsPurgedEvent,
  type OptionalMod,
} from "../lib/launcher";
import { ModCard } from "../components/mods/ModCard";
import { ModsTabs, type ModsTab } from "../components/mods/ModsTabs";

// Page Mods branchée sur la vraie data du launcher (listMods Rust qui scan
// le dossier mods/ et parse fabric.mod.json). Pas de toggle activer/desactiver
// pour cette PR — il n'y a pas encore de mecanisme cote serveur pour
// distinguer "requis" vs "optionnel" (le manifest signe n'a pas ces flags),
// donc afficher des toggles serait trompeur. Le user voit l'etat reel des
// mods installes + peut purger les incompatibles.
//
// Lifecycle :
//   - mount → listMods()
//   - event mods:purged (emis par le Rust quand purge_incompatible_mods
//     tourne au launch ou manuellement) → re-fetch
//   - clic bouton "Purger incompatibles" → purgeIncompatibleMods() puis
//     re-fetch
export function Mods() {
  const [tab, setTab] = useState<ModsTab>("mods");
  const [query, setQuery] = useState("");
  const [mods, setMods] = useState<ModEntry[]>([]);
  const [optionals, setOptionals] = useState<OptionalMod[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [purging, setPurging] = useState(false);
  const [togglingFile, setTogglingFile] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // listOptionalMods peut planter si non-authentifie (fetch_manifest
      // exige le JWT). On le tolere : on garde la liste optionnels vide
      // au lieu d'effacer toute la page.
      const [list, opt] = await Promise.all([
        listMods(),
        listOptionalMods().catch((e) => {
          console.warn("[mods] listOptionalMods failed:", e);
          return [] as OptionalMod[];
        }),
      ]);
      setMods(list);
      setOptionals(opt);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Impossible de lire le dossier des mods.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  async function handleToggleOptional(filename: string, nextEnabled: boolean) {
    setTogglingFile(filename);
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
      setError(
        err instanceof Error
          ? `Impossible de basculer ${filename} : ${err.message}`
          : `Impossible de basculer ${filename}.`,
      );
    } finally {
      setTogglingFile(null);
    }
  }

  useEffect(() => {
    void refresh();

    let unlisten: (() => void) | null = null;
    onModsPurged((event: ModsPurgedEvent) => {
      // Le Rust a purge des mods (au launch typiquement) → on rafraichit
      // la liste. Pas de toast ici, le DiagnosticToast s'en charge en
      // amont avec un message MOD_MC_VERSION_MISMATCH.
      void event;
      void refresh();
    }).then((fn) => {
      unlisten = fn;
    });
    return () => {
      if (unlisten) unlisten();
    };
  }, [refresh]);

  async function handlePurge() {
    setPurging(true);
    try {
      await purgeIncompatibleMods();
      await refresh();
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Échec de la purge des mods incompatibles.",
      );
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

  const incompatibleCount = mods.filter((m) => m.incompatibleWithTarget).length;
  const totalSizeBytes = mods.reduce((acc, m) => acc + m.sizeBytes, 0);
  const totalSizeMb = (totalSizeBytes / (1024 * 1024)).toFixed(1);

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
                    <div className="reborn-mods-error">
                      <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                      <span>{error}</span>
                      <button
                        type="button"
                        onClick={() => void refresh()}
                        className="reborn-mods-reset-btn"
                      >
                        <RefreshCw className="h-3 w-3" />
                        Réessayer
                      </button>
                    </div>
                  )}
                  {loading && mods.length === 0 ? (
                    <div className="reborn-mods-loading">
                      <RefreshCw className="h-4 w-4 animate-spin" />
                      <span>Chargement des mods installés…</span>
                    </div>
                  ) : filteredMods.length === 0 && mods.length === 0 ? (
                    <ModsEmpty />
                  ) : filteredMods.length === 0 ? (
                    <div className="reborn-mods-empty-filter">
                      Aucun mod ne correspond à la recherche.
                    </div>
                  ) : (
                    <div className="reborn-mods-grid">
                      {filteredMods.map((m) => (
                        <ModCard key={m.absolutePath} mod={m} />
                      ))}
                    </div>
                  )}

                  {optionals.length > 0 && (
                    <section className="mt-6">
                      <div className="mb-3 flex items-baseline justify-between">
                        <h2 className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[var(--color-foreground-muted)]">
                          Mods optionnels disponibles
                        </h2>
                        <span className="text-[10.5px] text-[var(--color-foreground-muted)]">
                          {optionals.filter((m) => m.enabled).length} / {optionals.length}{" "}
                          activé{optionals.filter((m) => m.enabled).length > 1 ? "s" : ""}
                        </span>
                      </div>
                      <p className="mb-3 text-[11.5px] leading-relaxed text-[var(--color-foreground-subtle)]">
                        Mods recommandés pour l'expérience RP Reborn. Coche pour les
                        installer au prochain lancement, décoche pour les retirer
                        proprement (le launcher purgera le .jar du dossier mods/).
                      </p>
                      <div className="grid gap-2">
                        {optionals.map((m) => (
                          <OptionalModRow
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
              {totalSizeMb} MB
            </span>
          </div>
          <button
            type="button"
            onClick={() => void handlePurge()}
            disabled={purging || incompatibleCount === 0}
            className="reborn-mods-reset-btn"
            title={
              incompatibleCount === 0
                ? "Aucun mod incompatible à purger"
                : "Supprime du dossier mods/ les .jar incompatibles avec la version MC ciblée"
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

function OptionalModRow({
  mod,
  disabled,
  onToggle,
}: {
  mod: OptionalMod;
  disabled: boolean;
  onToggle: (next: boolean) => void;
}) {
  const sizeMb = (mod.sizeBytes / (1024 * 1024)).toFixed(1);
  // Affiche le nom "lisible" en retirant les patterns de version courants
  // (X.Y.Z+mc1.21.1, X.Y.Z-fabric, etc.) pour eviter de polluer la liste.
  const displayName = mod.filename
    .replace(/-fabric/, "")
    .replace(/-\d+\.\d+(\.\d+)?(?:-[\w.]+)?(?:\+mc\d+(?:\.\d+)*)?/, "")
    .replace(/-for-MC[\d.]+/, "")
    .replace(/\.jar$/, "");
  return (
    <label
      className={[
        "flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2.5 transition-colors",
        mod.enabled
          ? "border-[var(--color-accent)] bg-[var(--color-accent-soft)]"
          : "border-[var(--color-border)] bg-[var(--color-surface-overlay)] hover:border-[var(--color-border-strong)]",
        disabled && "opacity-60",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <input
        type="checkbox"
        checked={mod.enabled}
        disabled={disabled}
        onChange={(e) => onToggle(e.target.checked)}
        className="h-4 w-4 cursor-pointer accent-[var(--color-accent)]"
      />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[12.5px] font-medium text-[var(--color-foreground)]">
          {displayName}
        </div>
        <div className="mt-0.5 truncate text-[10.5px] text-[var(--color-foreground-muted)]">
          {mod.filename} · {sizeMb} MB
        </div>
      </div>
      {mod.installed && mod.enabled && (
        <span className="rounded-sm bg-[var(--color-success-soft)] px-1.5 py-0.5 text-[9.5px] font-semibold uppercase tracking-wider text-[var(--color-success)]">
          Installé
        </span>
      )}
      {!mod.installed && mod.enabled && (
        <span className="rounded-sm bg-[var(--color-accent-soft)] px-1.5 py-0.5 text-[9.5px] font-semibold uppercase tracking-wider text-[var(--color-accent)]">
          DL au lancement
        </span>
      )}
      {mod.installed && !mod.enabled && (
        <span className="rounded-sm bg-[var(--color-warning-soft)] px-1.5 py-0.5 text-[9.5px] font-semibold uppercase tracking-wider text-[var(--color-warning)]">
          Sera retiré
        </span>
      )}
    </label>
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
        Lance le jeu une fois depuis l'accueil — le launcher téléchargera les
        mods déclarés dans le manifest serveur. Tu retrouveras la liste ici
        après le premier launch.
      </div>
    </div>
  );
}
