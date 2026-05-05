import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router";

// Stub : le module Créateur de personnage sera implémenté dans une PR dédiée.
// Cette route existe uniquement pour que le bouton "Créer mon personnage" de
// AcceptedPage ait une cible navigable.
export function Character() {
  const navigate = useNavigate();
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 px-8 py-8 text-center">
      <p className="text-xs uppercase tracking-widest text-foreground-subtle">
        Reborn Roleplay
      </p>
      <h1 className="font-display text-4xl font-semibold">
        Créateur de personnage
      </h1>
      <p className="max-w-xl text-sm text-foreground-subtle">
        Bientôt disponible. Tu pourras y configurer ton personnage RP avant ta
        première session sur le serveur.
      </p>
      <button
        type="button"
        className="wl-btn-ghost mt-2"
        onClick={() => navigate("/whitelist")}
      >
        <ArrowLeft size={16} /> Retour à la whitelist
      </button>
    </div>
  );
}
