import { motion } from "framer-motion";
import { AlertTriangle } from "lucide-react";
import type { ModEntry } from "../../lib/launcher";
import { colorForMod, formatModSize, modInitials } from "../../lib/mods-mock";

type Props = {
  mod: ModEntry;
};

// Card individuelle d'un mod installe. Lecture seule pour cette PR :
// les toggles activer/desactiver n'ont pas de mecanisme backend (le serveur
// force le manifest, on ne peut pas decider d'omettre un mod a la volee).
// On affiche : icone (gradient base sur hash modId) + nom + version +
// taille + badge OK/INCOMPATIBLE.
export function ModCard({ mod }: Props) {
  const displayName = mod.modId ?? mod.fileName.replace(/\.jar$/i, "");
  const color = colorForMod(mod.modId ?? mod.fileName);
  const incompatible = mod.incompatibleWithTarget;

  return (
    <motion.div
      whileHover={{ y: -2 }}
      transition={{ type: "spring", stiffness: 360, damping: 26 }}
      data-incompatible={incompatible || undefined}
      className="reborn-mod-card"
    >
      <div
        className="reborn-mod-icon"
        style={{
          background: `linear-gradient(135deg, ${color}, ${color}55)`,
        }}
      >
        <span>{modInitials(displayName)}</span>
      </div>
      <div className="reborn-mod-body">
        <div className="reborn-mod-name">
          {displayName}
          {mod.modVersion && (
            <span className="reborn-mod-version"> · v{mod.modVersion}</span>
          )}
        </div>
        <div className="reborn-mod-desc">
          {mod.fileName} · {formatModSize(mod.sizeBytes)}
          {mod.minecraftConstraint && ` · MC ${mod.minecraftConstraint}`}
        </div>
      </div>
      <div className="reborn-mod-right">
        {incompatible ? (
          <span
            data-incompatible
            className="reborn-mod-badge"
            title="Incompatible avec la version MC ciblée — sera purgé au prochain lancement"
          >
            <AlertTriangle className="h-3 w-3" />
            INCOMPATIBLE
          </span>
        ) : (
          <span
            data-required
            className="reborn-mod-badge"
            title="Mod fourni par le serveur via le manifest signé — obligatoire pour rejoindre"
          >
            REQUIS
          </span>
        )}
      </div>
    </motion.div>
  );
}
