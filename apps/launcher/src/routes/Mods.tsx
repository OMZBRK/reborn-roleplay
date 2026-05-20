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
  onModsPurged,
  purgeIncompatibleMods,
  type ModEntry,
  type ModsPurgedEvent,
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
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [purging, setPurging] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await listMods();
      setMods(list);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Impossible de lire le dossier des mods.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

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
