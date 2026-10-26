// Briques de formulaire de la candidature v2, reprises des maquettes validées
// (src/dev/maquettes/candidature/ui.tsx).
//
// Mieux que Ketto (§2.2) :
//   - compteur minimum ET maximum, mots en indication, or près du maximum,
//     rouge au-delà avec « N caractères de trop » ; le texte n'est jamais coupé ;
//   - zones de texte qui s'agrandissent avec le contenu (jusqu'à 60 % de la
//     hauteur de la fenêtre, puis défilement) ;
//   - âge en simple nombre, sans calendrier ;
//   - message d'erreur clair sous le champ, à la sortie du champ.

import { useId, useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from "react";
import { AlertCircle, Check, ChevronDown, ChevronUp, Cloud, CloudOff, Loader2 } from "lucide-react";
import type { Bornes } from "../../lib/candidature";
import { cn } from "../../lib/cn";
import { Markdown } from "../Markdown";
import { etatTexte, longueur, mots, nombre } from "./regles";

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
  /** Ancre du champ (« Aller au champ » depuis l'onglet Envoi). */
  ancre?: string;
};

export function Champ({ label, htmlFor, droite, aide, erreur, children, className, ancre }: ChampProps) {
  return (
    <div className={cn("cand-field", className)} data-champ={ancre}>
      <div className="cand-field-row">
        <label className="cand-label" htmlFor={htmlFor}>
          {label}
        </label>
        {droite}
      </div>
      {children}
      {erreur ? (
        <div className="cand-error" role="alert">
          <AlertCircle size={13} aria-hidden />
          <span>{erreur}</span>
        </div>
      ) : (
        aide && <div className="cand-help">{aide}</div>
      )}
    </div>
  );
}

export function Facultatif() {
  return <span className="cand-optional">facultatif</span>;
}

// ──────────────────────────────────────────────────────
//  Compteur
// ──────────────────────────────────────────────────────

export function Compteur({ valeur, bornes, avecMots = false }: { valeur: string; bornes: Bornes; avecMots?: boolean }) {
  const n = longueur(valeur);
  const etat = etatTexte(valeur, bornes);
  const m = mots(valeur);
  return (
    <span className="cand-counter" data-etat={etat} aria-live="polite">
      {avecMots && (
        <span className="cand-counter-words">
          {nombre(m)} mot{m > 1 ? "s" : ""} ·
        </span>
      )}
      {etat === "ok" && <Check size={12} aria-hidden />}
      {etat === "vide" || etat === "court" ? (
        <>
          <span>
            {nombre(n)} / {nombre(bornes.min)} caractères minimum
          </span>
          <span className="cand-counter-max">· {nombre(bornes.max)} max</span>
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
    const max = Math.round(window.innerHeight * 0.6);
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
      className={cn("cand-textarea", className)}
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
  forcer = false,
  erreurApi,
  placeholder,
  minLignes,
  avecMots,
  facultatif,
  ancre,
}: {
  label: string;
  valeur: string;
  onChange: (v: string) => void;
  bornes: Bornes;
  aide?: ReactNode;
  /** Message calculé (messageTexte) ; affiché à la sortie du champ, ou si `forcer`. */
  message: string | null;
  /** Montrer le message même sans être passé par le champ (après un envoi refusé). */
  forcer?: boolean;
  /** Message renvoyé par l'API pour ce champ (prioritaire). */
  erreurApi?: string;
  placeholder?: string;
  minLignes?: number;
  avecMots?: boolean;
  facultatif?: boolean;
  ancre?: string;
}) {
  const id = useId();
  const [quitte, setQuitte] = useState(false);
  // Trop long : signalé tout de suite, même pendant la frappe (rien n'est coupé).
  const trop = longueur(valeur) > bornes.max;
  const visible = (quitte || forcer || trop) && message !== null;
  const erreur = erreurApi ?? (visible ? message : null);
  return (
    <Champ
      label={label}
      htmlFor={id}
      ancre={ancre}
      droite={
        <span className="inline-flex items-center gap-2">
          {facultatif && <Facultatif />}
          <Compteur valeur={valeur} bornes={bornes} avecMots={avecMots} />
        </span>
      }
      aide={aide}
      erreur={erreur}
    >
      <ZoneTexte
        id={id}
        valeur={valeur}
        onChange={onChange}
        onBlur={() => setQuitte(true)}
        placeholder={placeholder}
        minLignes={minLignes}
        invalide={erreur !== null}
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
    const base = valeur !== "" && Number.isFinite(n) ? n : delta > 0 ? min - 1 : max + 1;
    onChange(String(Math.min(max, Math.max(min, base + delta))));
  };
  return (
    <div className="cand-number">
      <div className="cand-number-box">
        <input
          id={id}
          className="cand-input"
          inputMode="numeric"
          autoComplete="off"
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
        <div className="cand-number-steps">
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
//  Segments (liste fermée : code stocké, libellé affiché)
// ──────────────────────────────────────────────────────

export function Segments<T extends string>({
  options,
  valeur,
  onChange,
  label,
}: {
  options: { valeur: T; libelle: string }[];
  valeur: T | null;
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div className="cand-segments" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.valeur}
          type="button"
          role="radio"
          aria-checked={valeur === o.valeur}
          className="cand-segment"
          onClick={() => onChange(o.valeur)}
        >
          {o.libelle}
        </button>
      ))}
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Liste déroulante (facultative : « Pas de réponse » en tête)
// ──────────────────────────────────────────────────────

export function Liste<T extends string>({
  id,
  options,
  valeur,
  onChange,
  vide,
}: {
  id?: string;
  options: { valeur: T; libelle: string }[];
  valeur: T | null;
  onChange: (v: T | null) => void;
  /** Libellé de l'absence de réponse. */
  vide: string;
}) {
  return (
    <div className="cand-select-wrap">
      <select
        id={id}
        className="cand-select"
        value={valeur ?? ""}
        onChange={(e) => onChange(e.target.value === "" ? null : (e.target.value as T))}
      >
        <option value="">{vide}</option>
        {options.map((o) => (
          <option key={o.valeur} value={o.valeur}>
            {o.libelle}
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
    <label className="cand-check">
      <input type="checkbox" checked={coche} onChange={(e) => onChange(e.target.checked)} />
      <span className="cand-check-box" aria-hidden>
        <Check size={12} strokeWidth={3} />
      </span>
      <span>{children}</span>
    </label>
  );
}

// ──────────────────────────────────────────────────────
//  Indicateur « Enregistré »
// ──────────────────────────────────────────────────────

/** « refuse » : l'API refuse le brouillon (texte au-delà de deux fois son
 *  maximum) ; nouvel essai à la prochaine modification seulement. */
export type EtatEnregistrement = "enregistre" | "enregistrement" | "hors-ligne" | "echec" | "refuse" | "local";

export function Enregistrement({ etat }: { etat: EtatEnregistrement }) {
  return (
    <span className="cand-save" data-etat={etat} role="status" aria-live="polite">
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
      {etat === "refuse" && (
        <>
          <AlertCircle size={13} aria-hidden />
          Non enregistré : texte trop long
        </>
      )}
      {etat === "local" && (
        <>
          <Cloud size={13} aria-hidden />
          Gardé sur ce PC
        </>
      )}
    </span>
  );
}

// ──────────────────────────────────────────────────────
//  Texte du joueur mis en forme, sans lien actif
// ──────────────────────────────────────────────────────

/**
 * Markdown d'un texte du joueur (aperçu de l'histoire, relecture, « Mon
 * dossier »). Le texte passe par markdownSur (ni HTML ni lien écrit), mais le
 * Markdown devine encore les adresses nues (https://…, www.…) : un clic
 * remplacerait la fenêtre du launcher par le site. Ces clics sont bloqués.
 */
export function MarkdownSansLien({ content, className }: { content: string; className?: string }) {
  return (
    <div
      onClickCapture={(e) => {
        if ((e.target as HTMLElement).closest("a")) e.preventDefault();
      }}
    >
      <Markdown content={content} className={className} />
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Emblème du mod, teinté par la couleur du texte (masque CSS)
// ──────────────────────────────────────────────────────

/** Les emblèmes du mod sont blancs sur fond transparent : on s'en sert comme
 *  masque pour les peindre dans la couleur voulue (crème, or, gris). */
export function Emblem({ src, taille, className }: { src: string | undefined; taille: number; className?: string }) {
  if (!src) return <span aria-hidden className={className} style={{ width: taille, height: taille }} />;
  const masque = `url("${src}")`;
  return (
    <span
      aria-hidden
      className={cn("cand-emblem", className)}
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
    <header className="cand-head">
      <div>
        <div className="cand-eyebrow">{surtitre}</div>
        <h1 className="cand-title">{titre}</h1>
        <p className="cand-lead">{chapo}</p>
      </div>
      {droite}
    </header>
  );
}

export function Section({ titre, chapo }: { titre: string; chapo?: ReactNode }) {
  return (
    <div className="cand-section">
      <h2 className="cand-section-title">{titre}</h2>
      {chapo && <p className="cand-section-lead">{chapo}</p>}
    </div>
  );
}
