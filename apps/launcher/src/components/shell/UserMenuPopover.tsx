import { useEffect, useRef } from "react";
import { motion } from "framer-motion";
import { LogOut, Settings as SettingsIcon } from "lucide-react";
import { NavLink, useNavigate } from "react-router";
import { useAuthStore } from "../../stores/auth-store";
import { mapRole } from "../sidebar/role";
import { RoleBadge } from "../sidebar/RoleBadge";
import { logout } from "../../lib/auth";

type Props = {
  onClose: () => void;
};

// Popover utilisateur ouvert au clic sur l'avatar en bas de sidebar.
// Contenu : avatar large + pseudo + RoleBadge + lien Parametres + bouton
// Se deconnecter. Click-outside ferme.
export function UserMenuPopover({ onClose }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);
  const user = useAuthStore((s) => s.user);

  const pseudo = user?.displayName ?? user?.minecraftUsername ?? "Joueur";
  const initial = pseudo.charAt(0).toUpperCase();
  const roleType = mapRole(user?.role);

  useEffect(() => {
    // setTimeout 0 pour eviter de fermer immediatement le popover qui vient
    // tout juste de s'ouvrir (le mousedown qui a declenche l'ouverture est
    // toujours en train de bubble).
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const id = window.setTimeout(
      () => document.addEventListener("mousedown", onDocClick),
      0,
    );
    return () => {
      window.clearTimeout(id);
      document.removeEventListener("mousedown", onDocClick);
    };
  }, [onClose]);

  async function handleLogout() {
    try {
      await logout();
    } finally {
      setSession(null);
      onClose();
      navigate("/login", { replace: true });
    }
  }

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, x: -6, scale: 0.96 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: -6, scale: 0.96 }}
      transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
      className="reborn-user-popover"
      role="menu"
    >
      <div className="reborn-user-popover-head">
        <div className="reborn-user-popover-avatar">{initial}</div>
        <div className="min-w-0">
          <div className="truncate text-sm font-semibold leading-tight text-[var(--color-foreground)]">
            {pseudo}
          </div>
          <div className="mt-1">
            <RoleBadge role={roleType} />
          </div>
        </div>
      </div>

      <NavLink
        to="/settings"
        onClick={onClose}
        className="reborn-user-popover-item"
        role="menuitem"
      >
        <SettingsIcon className="h-4 w-4" />
        <span>Paramètres</span>
      </NavLink>

      <button
        type="button"
        onClick={handleLogout}
        className="reborn-user-popover-item reborn-user-popover-item--danger"
        role="menuitem"
      >
        <LogOut className="h-4 w-4" />
        <span>Se déconnecter</span>
      </button>
    </motion.div>
  );
}
