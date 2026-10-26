// Relecture d'un dossier, groupée Partie HRP / Partie RP, repliable par
// onglet (maquette L6) : onglet Envoi (avec « Modifier ») et « Mon dossier »
// une fois la candidature envoyée (lecture seule).

import { useEffect, useState, type ReactNode } from "react";
import { Check, ChevronDown, Loader2, Lock, Pause, Play } from "lucide-react";
import type { CandidatureOptions } from "../../lib/candidature";
import { formatIpcError } from "../../lib/errors";
import { mmss } from "./format";
import {
  libelleDe,
  longueur,
  markdownSur,
  mots,
  nomComplet,
  nombre,
  ONGLETS,
  trancheAge,
  type Formulaire,
  type Manque,
  type OngletId,
} from "./regles";
import { MarkdownSansLien } from "./ui";
import { useLecture } from "./voix";

type Ligne = { cle: string; libelle: string; valeur: ReactNode; vide?: boolean };

/** Lecteur compact de la relecture : le Blob est là, ou chargé au premier clic. */
export function LecteurCompact({
  blob,
  charger,
  dureeSec,
  max,
}: {
  blob: Blob | null;
  charger?: () => Promise<Blob>;
  dureeSec: number | null;
  max: number;
}) {
  const [charge, setCharge] = useState<Blob | null>(null);
  const [chargement, setChargement] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [jouerApres, setJouerApres] = useState(false);
  const source = blob ?? charge;
  const lecture = useLecture(source);
  const { basculer } = lecture;

  // Chargé au premier clic : la lecture part dès que le fichier est là.
  useEffect(() => {
    if (!jouerApres || !source) return;
    setJouerApres(false);
    void basculer();
  }, [jouerApres, source, basculer]);

  async function cliquer() {
    if (source) {
      void lecture.basculer();
      return;
    }
    if (!charger) return;
    setChargement(true);
    setErreur(null);
    try {
      setCharge(await charger());
      setJouerApres(true);
    } catch (err) {
      setErreur(formatIpcError(err, "Réécoute impossible"));
    } finally {
      setChargement(false);
    }
  }

  return (
    <span className="inline-flex flex-wrap items-center gap-2.5">
      <button
        type="button"
        className="cand-play !h-7 !w-7"
        aria-label={lecture.enLecture ? "Pause" : "Réécouter"}
        onClick={() => void cliquer()}
      >
        {chargement ? (
          <Loader2 size={12} className="animate-spin" />
        ) : lecture.enLecture ? (
          <Pause size={12} fill="currentColor" />
        ) : (
          <Play size={12} fill="currentColor" className="ml-px" />
        )}
      </button>
      {mmss(lecture.position ?? dureeSec ?? 0)} / {mmss(max)}
      {(erreur || lecture.erreur) && <span className="text-[11px] text-foreground-muted">{erreur ?? lecture.erreur}</span>}
    </span>
  );
}

export type DonneesRelecture = {
  f: Formulaire;
  options: CandidatureOptions;
  /** Rendu de la ligne « Enregistrement » (lecteur), ou null s'il n'y en a pas. */
  voix: ReactNode | null;
  /** Le micro a été testé (formulaire) ; non montré pour un dossier envoyé. */
  micTeste?: boolean;
  /** Champs de l'ancien formulaire, montrés seulement s'ils sont remplis (dossier migré). */
  ancien?: { motivation: string; experience: string; appearance: string };
};

function lignes(d: DonneesRelecture, onglet: OngletId): Ligne[] {
  const { f, options } = d;
  const l = options.listes;
  const t = (cle: string, libelle: string, v: string, markdown = false): Ligne => ({
    cle,
    libelle,
    valeur: v ? (
      <>
        {markdown ? (
          <MarkdownSansLien
            content={markdownSur(v)}
            className="cand-md cand-defile max-h-[360px] overflow-y-auto pr-3 [&_h2]:!mb-1 [&_h2]:!mt-4 [&_h2]:!text-[18px] [&_h2]:!font-normal [&_h2]:!tracking-wide [&_h2]:!text-gold-bright [&_h2:first-child]:!mt-0 [&_blockquote]:border-l-2 [&_blockquote]:border-gold/60 [&_blockquote]:pl-3"
          />
        ) : (
          <span className="block">{v}</span>
        )}
        <span className="mt-1 block text-[11px] text-foreground-muted">
          {markdown && `${nombre(mots(v))} mots · `}
          {nombre(longueur(v))} caractères
        </span>
      </>
    ) : (
      "À compléter"
    ),
    vide: !v,
  });
  const v = (cle: string, libelle: string, val: string | null | undefined): Ligne => ({
    cle,
    libelle,
    valeur: val || "À compléter",
    vide: !val,
  });
  switch (onglet) {
    case "vous": {
      const out = [
        v("playerAge", "Âge", f.playerAge ? `${f.playerAge} ans` : null),
        v("rpYears", "Roleplay depuis", libelleDe(l.rpYears, f.rpYears)),
        v("narutoKnowledge", "Univers Naruto", libelleDe(l.narutoKnowledge, f.narutoKnowledge)),
        t("availability", "Disponibilités", f.availability),
        {
          cle: "discoverySource",
          libelle: "Découverte",
          valeur: libelleDe(l.discoverySource, f.discoverySource) ?? "Pas de réponse",
        },
      ];
      if (d.ancien?.experience) out.push(t("experience", "Expérience (ancien formulaire)", d.ancien.experience));
      if (d.ancien?.motivation) out.push(t("motivation", "Pourquoi Reborn ? (ancien formulaire)", d.ancien.motivation));
      return out;
    }
    case "personnage": {
      const tr = trancheAge(f.characterAge, f.characterType);
      const nom = nomComplet(f);
      const out = [
        v("firstName", "Prénom", f.firstName.trim() || null),
        v("lastName", "Nom de famille", f.lastName.trim() || null),
        { cle: "nomEnJeu", libelle: "Nom en jeu", valeur: nom || "À compléter", vide: !nom },
        v("characterType", "Type", libelleDe(l.characterType, f.characterType)),
        v("characterSex", "Sexe", libelleDe(l.characterSex, f.characterSex)),
        v("characterAge", "Âge", f.characterAge ? `${f.characterAge} ans${tr ? ` · ${tr}` : ""}` : null),
      ];
      if (d.ancien?.appearance) out.push(t("appearance", "Caractère et apparence (ancien formulaire)", d.ancien.appearance));
      return out;
    }
    case "village": {
      const village = options.villages.find((x) => x.valeur === f.village);
      return [
        v("village", "Village", village ? `${village.court} · ${village.valeur}` : f.village),
        { cle: "clan", libelle: "Clan", valeur: "Sans clan · les clans se rejoignent lors des sessions de clan" },
      ];
    }
    case "histoire":
      return [
        t("history", "Histoire", f.history, true),
        t("objectives", "Objectifs roleplay", f.objectives),
        { cle: "support", libelle: "Support visuel", valeur: f.support.trim() || "Aucun (facultatif)" },
      ];
    case "voix": {
      const out: Ligne[] = [];
      if (d.micTeste !== undefined) {
        out.push(v("micTeste", "Micro", d.micTeste || d.voix ? "Testé, il fonctionne" : null));
      }
      out.push({
        cle: "voix",
        libelle: "Enregistrement",
        vide: d.voix === null,
        valeur: d.voix ?? "À enregistrer",
      });
      return out;
    }
    case "envoi":
      return [];
  }
}

export function BlocRelecture({
  onglet,
  donnees,
  ouvert,
  onBascule,
  manques,
  verrouille,
  onModifier,
}: {
  onglet: OngletId;
  donnees: DonneesRelecture;
  ouvert: boolean;
  onBascule: () => void;
  /** Manques de cet onglet (formulaire) ; absent pour un dossier envoyé. */
  manques?: Manque[];
  /** Partie validée, verrouillée (« À revoir » partiel). */
  verrouille?: boolean;
  onModifier?: () => void;
}) {
  const o = ONGLETS.find((x) => x.id === onglet);
  const n = manques?.length ?? 0;
  const manquants = new Set((manques ?? []).map((m) => m.champ));
  return (
    <div className="cand-review">
      <button type="button" className="cand-review-head" aria-expanded={ouvert} onClick={onBascule}>
        <span className="cand-review-title">
          {o?.numero} · {o?.libelle}
        </span>
        {manques === undefined ? null : verrouille ? (
          <span className="cand-review-state inline-flex items-center gap-1 text-foreground-muted">
            <Lock size={12} aria-hidden /> Validé
          </span>
        ) : n === 0 ? (
          <span className="cand-review-state inline-flex items-center gap-1 text-success">
            <Check size={13} strokeWidth={3} aria-hidden /> Complet
          </span>
        ) : (
          <span className="cand-review-state text-gold">{n} à compléter</span>
        )}
        {onModifier && !verrouille && (
          <span
            role="link"
            tabIndex={0}
            className="cand-link ml-3"
            onClick={(e) => {
              e.stopPropagation();
              onModifier();
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.stopPropagation();
                onModifier();
              }
            }}
          >
            Modifier
          </span>
        )}
        <ChevronDown
          size={16}
          className="ml-2 text-foreground-muted transition-transform"
          style={{ transform: ouvert ? "rotate(180deg)" : undefined }}
          aria-hidden
        />
      </button>
      {ouvert && (
        <div className="cand-review-body">
          {lignes(donnees, onglet).map((l) => (
            <div key={l.cle} className="cand-review-row">
              <span className="cand-review-key">
                {l.libelle}
                {!l.vide && manquants.has(l.cle) && (
                  <span className="mt-0.5 block text-[11px] font-semibold text-[#f87171]">à compléter</span>
                )}
              </span>
              <span className="cand-review-val" data-vide={l.vide || undefined}>
                {l.valeur}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
