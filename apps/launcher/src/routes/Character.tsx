import { HeartPulse, Skull, Sparkles, UserRound } from "lucide-react";

/**
 * Onglet « Mon personnage » — recensement des personnages RP que le joueur
 * possède sur le serveur (gérés par ShinobiCore in-game : création, mort/RPK,
 * multi-perso). Le launcher n'affiche pas un créateur : il reflète le roster.
 *
 * ⚠️ Source de données pas encore branchée : les persos vivent dans la DB de
 * ShinobiCore (plugin de jeu), pas dans l'API Reborn. Tant que le pont
 * ShinobiCore → API → launcher n'existe pas, on affiche l'état vide + la forme
 * cible d'une fiche (carte ghostée) pour que le joueur sache ce qui arrivera.
 */
export function Character() {
  return (
    <div className="h-full overflow-y-auto px-8 py-8">
      <div className="mx-auto max-w-4xl">
        <header className="mb-6">
          <p className="text-xs uppercase tracking-[0.28em] text-foreground-subtle">
            Recensement
          </p>
          <h1 className="mt-1 font-display text-3xl font-semibold text-foreground">
            Mes personnages
          </h1>
          <p className="mt-2 max-w-2xl text-[13px] leading-relaxed text-foreground-subtle">
            Retrouve ici les personnages que tu incarnes sur le serveur — leur
            village, leur état et leur temps de jeu. La création et la gestion se
            font <strong className="text-foreground">en jeu</strong> ; cette page
            n'est qu'un miroir de ton roster.
          </p>
        </header>

        {/* État vide honnête : aucune donnée liée pour l'instant. */}
        <div className="flex flex-col items-center justify-center gap-4 rounded-xl border border-border/60 bg-surface/40 px-8 py-10 text-center">
          <div className="flex h-14 w-14 items-center justify-center rounded-full border border-border-strong bg-surface-elevated">
            <UserRound className="h-6 w-6 text-foreground-muted" />
          </div>
          <div>
            <h2 className="font-display text-[18px] tracking-wide text-foreground">
              Aucun personnage lié pour l'instant
            </h2>
            <p className="mx-auto mt-2 max-w-md text-[13px] leading-relaxed text-foreground-subtle">
              Tes personnages sont créés lors de ta première connexion en jeu. Ils
              apparaîtront ici automatiquement une fois le pont serveur ↔ launcher
              en place.
            </p>
          </div>
        </div>

        {/* Aperçu de la fiche cible (ghostée) pour montrer la forme du roster. */}
        <div className="mt-8">
          <div className="mb-3 flex items-center gap-2 text-xs uppercase tracking-wider text-foreground-muted">
            <Sparkles size={13} />
            Aperçu d'une fiche à venir
          </div>
          <div className="grid grid-cols-1 gap-4 opacity-45 sm:grid-cols-2">
            <GhostCharacterCard
              name="Personnage"
              village="Village"
              status="alive"
            />
            <GhostCharacterCard
              name="Personnage"
              village="Village"
              status="dead"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

/** Fiche perso ghostée — reflète la forme finale d'une entrée du recensement. */
function GhostCharacterCard({
  name,
  village,
  status,
}: {
  name: string;
  village: string;
  status: "alive" | "dead";
}) {
  return (
    <div className="flex items-center gap-4 rounded-xl border border-border/60 bg-surface p-4">
      <div className="flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-lg border border-border-strong bg-surface-elevated">
        <UserRound className="h-7 w-7 text-foreground-muted" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate font-display text-[17px] font-medium text-foreground">
          {name}
        </div>
        <div className="mt-0.5 text-[12px] text-foreground-subtle">{village}</div>
        <div className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-border-strong px-2.5 py-1 text-[11px]">
          {status === "alive" ? (
            <>
              <HeartPulse size={12} className="text-emerald-400" />
              <span className="text-foreground-subtle">En vie</span>
            </>
          ) : (
            <>
              <Skull size={12} className="text-red-400" />
              <span className="text-foreground-subtle">Décédé (RPK)</span>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
