// MAQUETTES : bloc hero de l'accueil (L10), sur les classes réelles de
// HeroLaunchCard (.reborn-home-hero…), en deux modes :
//
//  - « joueur » (phase ACCEPTE) : identique à l'accueil actuel, prêt à lancer ;
//  - « douane » (phase DOUANE_RESERVEE, §3.3) : le bouton principal devient
//    « Passer la douane ». Règle unique de la fenêtre d'entrée : actif de
//    H − 15 min à H + 30 min, H = heure de convocation. Avant : grisé avec
//    « Ta séance : samedi 21 h ». Après : « Séance manquée », nouvelle
//    réservation possible (§3.10).
//
// Le vrai PlayButton porte la logique de mise à jour et de lancement (stores,
// IPC) : on en reprend ici le dessin exact (PrimaryButton, LockedButton,
// légende, emplacement de 52 px), pas la logique.

import type { ReactNode } from "react";
import {
  CalendarPlus,
  Check,
  Clock,
  Cpu,
  DoorOpen,
  Layers,
  Lock,
  Play,
  ScrollText,
  Square,
  Wifi,
} from "lucide-react";
import {
  fermetureDe,
  MAINTENANT,
  ouvertureDe,
  SEANCE,
  type EtatDouane,
  type SeanceDouane,
} from "./donnees";
import { duree, heure } from "./format";

const FOCUS_RING =
  "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]";

export type ModeHero =
  | { mode: "joueur" }
  | { mode: "douane"; etat: EtatDouane; seance?: SeanceDouane };

export function HeroAccueil(props: ModeHero) {
  const douane = props.mode === "douane";
  return (
    <section className="reborn-home-hero reborn-pattern-overlay">
      <div className="reborn-home-hero-inner">
        <div className="reborn-home-hero-left">
          <div className="reborn-home-hero-kicker">
            <span>REBORN ROLEPLAY</span>
            <span className="reborn-home-hero-dot" />
            <span>NARUTO EDITION</span>
          </div>
          <h1 className="reborn-home-hero-title">REBORN</h1>
          <p className="reborn-home-hero-sub">
            {douane
              ? "Ton écrit est accepté. Dernière étape : la douane, en jeu, face à un examinateur."
              : "Entre le silence et le fracas, chaque shinobi trace sa propre voie."}
          </p>
          {douane && <FriseCandidature seance={props.seance ?? SEANCE} etat={props.etat} />}
          <div className="reborn-home-hero-meta">
            <span className="reborn-home-hero-chip">
              <Cpu className="h-2.5 w-2.5" />
              Fabric Loader
            </span>
            <span className="reborn-home-hero-chip">
              <Layers className="h-2.5 w-2.5" />
              Minecraft 26.2
            </span>
            <span className="reborn-home-hero-chip">
              <Wifi className="h-2.5 w-2.5" style={{ color: "var(--color-success)" }} />
              23/120 en ligne
            </span>
          </div>
        </div>
        <div className="reborn-home-hero-right">
          {douane ? (
            <ColonneDouane etat={props.etat} seance={props.seance ?? SEANCE} />
          ) : (
            <ColonneJoueur />
          )}
        </div>
      </div>
    </section>
  );
}

// ──────────────────────────────────────────────────────
//  Colonne d'action : joueur (accueil actuel)
// ──────────────────────────────────────────────────────

function ColonneJoueur() {
  return (
    <div className="flex flex-col items-center gap-4">
      <PileBouton
        bouton={<BoutonPrincipal label="JOUER" icone={<Play className="h-[26px] w-[26px]" strokeWidth={2} fill="currentColor" />} />}
        legende="v3.1.149 · prêt à lancer"
        emplacement={null}
      />
      <RangeeSecondaire>
        <BoutonSecondaire icone={<ScrollText className="h-3.5 w-3.5" />}>Journal du jeu</BoutonSecondaire>
      </RangeeSecondaire>
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Colonne d'action : douane
// ──────────────────────────────────────────────────────

/** Colonne complète (bouton + légende + emplacement + rangée secondaire). */
export function ColonneDouane({ etat, seance = SEANCE }: { etat: EtatDouane; seance?: SeanceDouane }) {
  return (
    <div className="flex flex-col items-center gap-4">
      <BoutonDouane etat={etat} seance={seance} />
      <RangeeSecondaire>
        <BoutonSecondaire icone={<ScrollText className="h-3.5 w-3.5" />}>Journal du jeu</BoutonSecondaire>
        {/* Séance manquée : plus de séance à montrer, la réservation est juste au-dessus. */}
        {etat !== "manquee" && (
          <BoutonSecondaire icone={<Clock className="h-3.5 w-3.5" />}>Ma séance</BoutonSecondaire>
        )}
      </RangeeSecondaire>
    </div>
  );
}

/** Le bouton principal en phase DOUANE_RESERVEE, avec sa légende et son emplacement fixe. */
export function BoutonDouane({ etat, seance = SEANCE }: { etat: EtatDouane; seance?: SeanceDouane }) {
  const maintenant = MAINTENANT[etat];
  const ouverture = ouvertureDe(seance);
  const fermeture = fermetureDe(seance);
  const h = heure(seance.convocation);

  if (etat === "avant") {
    return (
      <PileBouton
        bouton={<BoutonVerrouille label="PASSER LA DOUANE" />}
        legende={`Ta séance : ${seance.jourCourt} ${h}`}
        emplacement={
          <Encart ton="neutre" icone={<Clock className="h-3.5 w-3.5" />}>
            Entrée possible à partir de <b>{heure(ouverture)}</b>, dans {duree(ouverture - maintenant)}.
          </Encart>
        }
      />
    );
  }

  if (etat === "manquee") {
    return (
      <PileBouton
        bouton={<BoutonVerrouille label="SÉANCE MANQUÉE" />}
        legende={`Convocation de ${h} · entrée fermée à ${heure(fermeture)}`}
        emplacement={
          <BoutonSecondaire icone={<CalendarPlus className="h-3.5 w-3.5" />} accent>
            Réserver une autre séance
          </BoutonSecondaire>
        }
      />
    );
  }

  if (etat === "en-jeu") {
    return (
      <PileBouton
        bouton={<BoutonPrincipal label="EN DOUANE" desactive icone={<DoorOpen className="h-[26px] w-[26px]" strokeWidth={2} />} />}
        legende="Partie en cours · hall de la douane"
        emplacement={
          <button
            type="button"
            className={`inline-flex items-center gap-2 rounded-lg border border-danger/40 bg-danger/10 px-4 py-2 text-[11px] uppercase tracking-[0.14em] text-danger transition-colors hover:border-danger/70 hover:bg-danger/20 ${FOCUS_RING}`}
          >
            <Square className="h-3 w-3" fill="currentColor" />
            Arrêter le jeu
          </button>
        }
      />
    );
  }

  // Fenêtre ouverte.
  return (
    <PileBouton
      bouton={<BoutonPrincipal label="PASSER LA DOUANE" icone={<DoorOpen className="h-[26px] w-[26px]" strokeWidth={2} />} />}
      legende={`Convocation ${h} · ${seance.jour}`}
      emplacement={<FenetreEntree seance={seance} maintenant={maintenant} />}
    />
  );
}

/**
 * Frise de la fenêtre d'entrée : H − 15 → H + 30, repère de l'heure de
 * convocation et curseur « maintenant ». Tient dans l'emplacement de 52 px.
 */
function FenetreEntree({ seance, maintenant }: { seance: SeanceDouane; maintenant: number }) {
  const ouverture = ouvertureDe(seance);
  const fermeture = fermetureDe(seance);
  const span = fermeture - ouverture;
  const pct = (m: number) => `${Math.max(0, Math.min(100, ((m - ouverture) / span) * 100))}%`;
  const reste = fermeture - maintenant;
  return (
    <div className="mqa-fenetre-entree" role="group" aria-label="Fenêtre d'entrée de la douane">
      <div className="mqa-fenetre-entree-tete">
        <span>
          Entrée ouverte <b>{heure(ouverture)} → {heure(fermeture)}</b>
        </span>
        <span className="mqa-fenetre-entree-reste">ferme dans {duree(reste)}</span>
      </div>
      <div className="mqa-fenetre-entree-piste" aria-hidden>
        <span className="mqa-fenetre-entree-rempli" style={{ width: pct(maintenant) }} />
        <span
          className="mqa-fenetre-entree-h"
          style={{ left: pct(seance.convocation) }}
          title={`Convocation : ${heure(seance.convocation)}`}
        />
        <span className="mqa-fenetre-entree-curseur" style={{ left: pct(maintenant) }} />
      </div>
    </div>
  );
}

/** Mini-frise Écrit · Douane · Bienvenue (reprise de la frise du suivi, §2.7). */
function FriseCandidature({ seance, etat }: { seance: SeanceDouane; etat: EtatDouane }) {
  const douaneLabel =
    etat === "manquee" ? "Douane · à réserver" : `Douane · ${etat === "avant" ? seance.jourCourt : "ce soir"} ${heure(seance.convocation)}`;
  return (
    <ol className="mqa-frise" aria-label="Avancement de la candidature">
      <li data-etat="fait">
        <span className="mqa-frise-pt">
          <Check className="h-2.5 w-2.5" strokeWidth={3} />
        </span>
        Écrit accepté
      </li>
      <li data-etat="encours">
        <span className="mqa-frise-pt" />
        {douaneLabel}
      </li>
      <li data-etat="avenir">
        <span className="mqa-frise-pt" />
        Bienvenue
      </li>
    </ol>
  );
}

// ──────────────────────────────────────────────────────
//  Briques visuelles (dessin du PlayButton réel)
// ──────────────────────────────────────────────────────

/** Pile de PlayButton : 84 px pour le bouton, légende, emplacement fixe de 52 px. */
function PileBouton({
  bouton,
  legende,
  emplacement,
}: {
  bouton: ReactNode;
  legende: string;
  emplacement: ReactNode;
}) {
  return (
    <div className="flex w-full min-w-0 flex-col items-center gap-3">
      <div className="flex min-h-[84px] w-full items-center justify-center">{bouton}</div>
      <div className="min-h-5 max-w-full text-center text-[11px] uppercase tracking-[0.18em] text-foreground-muted">
        {legende}
      </div>
      <div className="flex min-h-[52px] w-full items-start justify-center">{emplacement}</div>
    </div>
  );
}

function BoutonPrincipal({
  label,
  icone,
  desactive,
}: {
  label: string;
  icone: ReactNode;
  desactive?: boolean;
}) {
  // Icône en absolu à gauche (comme le vrai PrimaryButton) : le libellé reste
  // centré. Un libellé long (« PASSER LA DOUANE ») élargit les marges pour ne
  // pas toucher l'icône.
  const marges = label.length > 8 ? "px-[60px]" : "px-12";
  return (
    <button
      type="button"
      disabled={desactive}
      className={`${desactive ? "" : "reborn-pulse-glow"} group relative flex items-center justify-center rounded-[14px] ${marges} py-4 font-display text-[28px] tracking-[0.08em] text-white transition-transform duration-200 hover:-translate-y-0.5 disabled:cursor-default disabled:opacity-80 disabled:hover:translate-y-0 ${FOCUS_RING} focus-visible:outline-[var(--color-gold-bright)]`}
      style={{
        background: "linear-gradient(180deg, var(--color-accent-hover) 0%, var(--color-accent) 100%)",
        border: "1px solid rgba(255,255,255,0.08)",
        minWidth: 240,
      }}
    >
      <span className="absolute left-5 flex items-center">{icone}</span>
      <span className="text-center">{label}</span>
    </button>
  );
}

function BoutonVerrouille({ label }: { label: string }) {
  return (
    <button
      type="button"
      disabled
      className="inline-flex items-center gap-3 rounded-[14px] border border-border-strong bg-surface-elevated px-9 py-4 font-display text-[28px] tracking-[0.08em] text-foreground-muted"
      style={{ minWidth: 240, cursor: "not-allowed" }}
    >
      <Lock className="h-[22px] w-[22px]" strokeWidth={2.2} />
      <span>{label}</span>
    </button>
  );
}

function Encart({ ton, icone, children }: { ton: "neutre"; icone: ReactNode; children: ReactNode }) {
  return (
    <div className="mqa-encart" data-ton={ton}>
      <span className="mqa-encart-icone">{icone}</span>
      <span>{children}</span>
    </div>
  );
}

function RangeeSecondaire({ children }: { children: ReactNode }) {
  return <div className="flex items-start gap-3">{children}</div>;
}

function BoutonSecondaire({
  icone,
  accent,
  children,
}: {
  icone: ReactNode;
  accent?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      className={`inline-flex items-center gap-2 rounded-lg border bg-surface-elevated px-4 py-2 text-[11px] uppercase tracking-[0.14em] transition-colors hover:text-white ${
        accent
          ? "border-gold/50 text-gold hover:border-gold"
          : "border-border-strong text-foreground-subtle hover:border-accent/60"
      } ${FOCUS_RING}`}
    >
      {icone}
      {children}
    </button>
  );
}
