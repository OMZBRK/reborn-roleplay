// MAQUETTES — briques de formulaire de la candidature v2 (dev seulement).
//
// Mieux que Ketto (§2.2) :
//   - compteur minimum ET maximum, mots en indication, or près du maximum,
//     rouge au-delà avec « N caractères de trop » ; le texte n'est jamais coupé ;
//   - zones de texte qui s'agrandissent avec le contenu (jusqu'à 60 % de la
//     hauteur, puis défilement) ;
//   - âge en simple nombre, sans calendrier ;
//   - message d'erreur clair sous le champ, à la sortie du champ.

import { useId, useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from "react";
import { AlertCircle, Check, ChevronDown, ChevronUp, Cloud, CloudOff, Loader2 } from "lucide-react";
import { cn } from "../../../lib/cn";
import { etatTexte, longueur, mots, nombre, type Bornes } from "./donnees";
import { useDossier, type EtatEnregistrement } from "./contexte";

// ──────────────────────────────────────────────────────
//  Champ : étiquette, compteur à droite, aide et erreur dessous
// ──────────────────────────────────────────────────────

type ChampProps = {
  label: string;
  htmlFor?: string;
  droite?: ReactNode;
  aide?: ReactNode;
  erreur?: string | null;
  children: ReactNode;
  className?: string;
};

export function Champ({ label, htmlFor, droite, aide, erreur, children, className }: ChampProps) {
  return (
    <div className={cn("mqc-field", className)}>
      <div className="mqc-field-row">
        <label className="mqc-label" htmlFor={htmlFor}>
          {label}
        </label>
        {droite}
      </div>
      {children}
      {erreur ? (
        <div className="mqc-error" role="alert">
          <AlertCircle size={13} aria-hidden />
          <span>{erreur}</span>
        </div>
      ) : (
        aide && <div className="mqc-help">{aide}</div>
      )}
    </div>
  );
}

export function Facultatif() {
  return <span className="mqc-optional">facultatif</span>;
}

// ──────────────────────────────────────────────────────
//  Compteur
// ──────────────────────────────────────────────────────

export function Compteur({ valeur, bornes, avecMots = false }: { valeur: string; bornes: Bornes; avecMots?: boolean }) {
  const n = longueur(valeur);
  const etat = etatTexte(valeur, bornes);
  const m = mots(valeur);
  return (
    <span className="mqc-counter" data-etat={etat} aria-live="polite">
      {avecMots && (
        <span className="mqc-counter-words">
          {nombre(m)} mot{m > 1 ? "s" : ""} ·
        </span>
      )}
      {etat === "ok" && <Check size={12} aria-hidden />}
      {etat === "vide" || etat === "court" ? (
        <>
          <span>
            {nombre(n)} / {nombre(bornes.min)} caractères minimum
          </span>
          <span className="mqc-counter-max">· {nombre(bornes.max)} max</span>
        </>
      ) : etat === "trop" ? (
        <span>
          {nombre(n)} / {nombre(bornes.max)} · {nombre(n - bornes.max)} de trop
        </span>
      ) : (
        <span>
          {nombre(n)} / {nombre(bornes.max)}
        </span>
      )}
    </span>
  );
}

// ──────────────────────────────────────────────────────
//  Zone de texte qui s'agrandit
// ──────────────────────────────────────────────────────

type ZoneProps = {
  id?: string;
  valeur: string;
  onChange: (v: string) => void;
  onBlur?: () => void;
  placeholder?: string;
  minLignes?: number;
  invalide?: boolean;
  zoneRef?: RefObject<HTMLTextAreaElement | null>;
  className?: string;
};

export function ZoneTexte({ id, valeur, onChange, onBlur, placeholder, minLignes = 4, invalide, zoneRef, className }: ZoneProps) {
  const local = useRef<HTMLTextAreaElement>(null);
  const ref = zoneRef ?? local;

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    // Capture en longueur : le plafond reste celui de la fenêtre du launcher (800 px).
    const fenetre = document.documentElement.dataset.maquettePleine ? 800 : window.innerHeight;
    const max = Math.round(fenetre * 0.6);
    el.style.height = "auto";
    const voulu = el.scrollHeight + 2;
    el.style.height = `${Math.min(voulu, max)}px`;
    el.style.overflowY = voulu > max ? "auto" : "hidden";
    // Au plafond, la zone défile : barre discrète visible (le launcher masque
    // toutes les barres, un texte au plafond aurait l'air coupé).
    if (voulu > max) el.setAttribute("data-defile", "true");
    else el.removeAttribute("data-defile");
  }, [valeur, ref]);

  return (
    <textarea
      id={id}
      ref={ref}
      className={cn("mqc-textarea", className)}
      rows={minLignes}
      value={valeur}
      placeholder={placeholder}
      aria-invalid={invalide || undefined}
      onChange={(e) => onChange(e.target.value)}
      onBlur={onBlur}
    />
  );
}

/** Texte long complet : étiquette + compteur + zone + aide / erreur. */
export function ChampTexte({
  label,
  valeur,
  onChange,
  bornes,
  aide,
  message,
  placeholder,
  minLignes,
  avecMots,
  facultatif,
}: {
  label: string;
  valeur: string;
  onChange: (v: string) => void;
  bornes: Bornes;
  aide?: ReactNode;
  /** Message calculé (messageTexte) ; affiché à la sortie du champ ou si l'écran le demande. */
  message: string | null;
  placeholder?: string;
  minLignes?: number;
  avecMots?: boolean;
  facultatif?: boolean;
}) {
  const id = useId();
  const { scenario } = useDossier();
  const [quitte, setQuitte] = useState(false);
  const visible = (quitte || scenario.montrerErreurs) && message !== null;
  // Trop long : signalé tout de suite, même pendant la frappe (rien n'est coupé).
  const trop = longueur(valeur) > bornes.max;
  return (
    <Champ
      label={label}
      htmlFor={id}
      droite={
        <span className="inline-flex items-center gap-2">
          {facultatif && <Facultatif />}
          <Compteur valeur={valeur} bornes={bornes} avecMots={avecMots} />
        </span>
      }
      aide={aide}
      erreur={visible || trop ? message : null}
    >
      <ZoneTexte
        id={id}
        valeur={valeur}
        onChange={onChange}
        onBlur={() => setQuitte(true)}
        placeholder={placeholder}
        minLignes={minLignes}
        invalide={visible || trop}
      />
    </Champ>
  );
}

// ──────────────────────────────────────────────────────
//  Nombre (âges)
// ──────────────────────────────────────────────────────

export function ChampNombre({
  id,
  valeur,
  onChange,
  min,
  max,
  invalide,
  onBlur,
  aDroite,
}: {
  id?: string;
  valeur: string;
  onChange: (v: string) => void;
  min: number;
  max: number;
  invalide?: boolean;
  onBlur?: () => void;
  aDroite?: ReactNode;
}) {
  const pas = (delta: number) => {
    const n = Number(valeur);
    const base = Number.isFinite(n) && valeur !== "" ? n : delta > 0 ? min - 1 : max + 1;
    onChange(String(Math.min(max, Math.max(min, base + delta))));
  };
  return (
    <div className="mqc-number">
      <div className="mqc-number-box">
        <input
          id={id}
          className="mqc-input"
          inputMode="numeric"
          maxLength={2}
          value={valeur}
          aria-invalid={invalide || undefined}
          onChange={(e) => onChange(e.target.value.replace(/[^0-9]/g, ""))}
          onBlur={onBlur}
          onKeyDown={(e) => {
            if (e.key === "ArrowUp") {
              e.preventDefault();
              pas(1);
            } else if (e.key === "ArrowDown") {
              e.preventDefault();
              pas(-1);
            }
          }}
        />
        <div className="mqc-number-steps">
          <button type="button" tabIndex={-1} aria-label="Plus" onClick={() => pas(1)}>
            <ChevronUp size={11} />
          </button>
          <button type="button" tabIndex={-1} aria-label="Moins" onClick={() => pas(-1)}>
            <ChevronDown size={11} />
          </button>
        </div>
      </div>
      {aDroite}
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Segments
// ──────────────────────────────────────────────────────

export function Segments<T extends string>({
  options,
  valeur,
  onChange,
  label,
}: {
  options: readonly T[];
  valeur: T | null;
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div className="mqc-segments" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button
          key={o}
          type="button"
          role="radio"
          aria-checked={valeur === o}
          className="mqc-segment"
          onClick={() => onChange(o)}
        >
          {o}
        </button>
      ))}
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Liste déroulante
// ──────────────────────────────────────────────────────

export function Liste({
  id,
  options,
  valeur,
  onChange,
}: {
  id?: string;
  options: readonly string[];
  valeur: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="mqc-select-wrap">
      <select id={id} className="mqc-select" value={valeur || options[0]} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
      <ChevronDown size={15} aria-hidden />
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Case à cocher
// ──────────────────────────────────────────────────────

export function Case({ coche, onChange, children }: { coche: boolean; onChange: (v: boolean) => void; children: ReactNode }) {
  return (
    <label className="mqc-check">
      <input type="checkbox" checked={coche} onChange={(e) => onChange(e.target.checked)} />
      <span className="mqc-check-box" aria-hidden>
        <Check size={12} strokeWidth={3} />
      </span>
      <span>{children}</span>
    </label>
  );
}

// ──────────────────────────────────────────────────────
//  Indicateur « Enregistré »
// ──────────────────────────────────────────────────────

export function Enregistrement({ etat }: { etat: EtatEnregistrement }) {
  return (
    <span className="mqc-save" data-etat={etat} role="status" aria-live="polite">
      {etat === "enregistrement" && (
        <>
          <Loader2 size={12} className="animate-spin" aria-hidden />
          Enregistrement…
        </>
      )}
      {etat === "enregistre" && (
        <>
          <Cloud size={13} aria-hidden />
          Enregistré
        </>
      )}
      {etat === "hors-ligne" && (
        <>
          <CloudOff size={13} aria-hidden />
          Hors ligne — gardé sur ce PC
        </>
      )}
      {etat === "echec" && (
        <>
          <AlertCircle size={13} aria-hidden />
          Échec, nouvel essai dans 10 s
        </>
      )}
    </span>
  );
}

// ──────────────────────────────────────────────────────
//  Emblème du mod, teinté par la couleur du texte (masque CSS)
// ──────────────────────────────────────────────────────

/** Les emblèmes du mod sont blancs sur fond transparent : on s'en sert comme
 *  masque pour les peindre dans la couleur voulue (crème, or, gris). */
export function Emblem({ src, taille, className }: { src: string; taille: number; className?: string }) {
  const masque = `url("${src}")`;
  return (
    <span
      aria-hidden
      className={cn("mqc-emblem", className)}
      style={{ width: taille, height: taille, WebkitMaskImage: masque, maskImage: masque }}
    />
  );
}

// ──────────────────────────────────────────────────────
//  En-tête de page et section
// ──────────────────────────────────────────────────────

export function EnTete({
  surtitre,
  titre,
  chapo,
  droite,
}: {
  surtitre: ReactNode;
  titre: string;
  chapo: ReactNode;
  droite?: ReactNode;
}) {
  return (
    <header className="mqc-head">
      <div>
        <div className="mqc-eyebrow">{surtitre}</div>
        <h1 className="mqc-title">{titre}</h1>
        <p className="mqc-lead">{chapo}</p>
      </div>
      {droite}
    </header>
  );
}

export function Section({ titre, chapo }: { titre: string; chapo?: ReactNode }) {
  return (
    <div className="mqc-section">
      <h2 className="mqc-section-title">{titre}</h2>
      {chapo && <p className="mqc-section-lead">{chapo}</p>}
    </div>
  );
}
