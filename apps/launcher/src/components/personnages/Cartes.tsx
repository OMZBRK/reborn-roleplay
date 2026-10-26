// « Mes personnages » : carte de la liste, blason, état, N° copiable et
// emplacement libre. Reprise de la maquette validée L11
// (dev/maquettes/accueil/MesPersonnages.tsx), branchée sur les vraies
// données (lib/personnages.ts).

import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import {
  CalendarClock,
  Check,
  Copy,
  FileCheck2,
  HeartPulse,
  Plus,
  Skull,
  Timer,
  Trash2,
  UserRound,
} from "lucide-react";
import type { PersonnageCompte } from "../../lib/personnages";
import { dateDe, dernierePartie, tempsDeJeu } from "./format";
import {
  emblemeClan,
  emblemeVillage,
  eteint,
  ligneClanVillage,
  ligneProfil,
  nomComplet,
  nomFamille,
  prenom,
} from "./identite";

// ──────────────────────────────────────────────────────
//  Carte de la liste
// ──────────────────────────────────────────────────────

export function CartePersonnage({
  perso,
  choisi,
  onChoisir,
}: {
  perso: PersonnageCompte;
  choisi: boolean;
  onChoisir: () => void;
}) {
  const famille = nomFamille(perso);
  const vu = dateDe(perso.lastSeenAt);
  return (
    <div
      role="listitem"
      className="reborn-perso-carte"
      data-choisi={choisi ? "true" : undefined}
      data-eteint={eteint(perso) ? "true" : undefined}
    >
      <button
        type="button"
        className="reborn-perso-carte-zone"
        onClick={onChoisir}
        aria-pressed={choisi}
        aria-label={`Voir la fiche de ${nomComplet(perso)}`}
      />
      <div className="reborn-perso-carte-haut">
        <Blason perso={perso} taille={64} />
        <div className="reborn-perso-carte-identite">
          <div className="reborn-perso-carte-ligne1">
            <h2 className="reborn-perso-carte-nom">
              {prenom(perso)}
              {famille && <span> {famille}</span>}
            </h2>
            <EtatPersonnage perso={perso} />
          </div>
          <p className="reborn-perso-carte-meta">{ligneClanVillage(perso)}</p>
          <p className="reborn-perso-carte-meta reborn-perso-carte-meta--doux">{ligneProfil(perso)}</p>
          {perso.fromApplication && (
            <span className="reborn-perso-chip reborn-perso-chip--or">
              <FileCheck2 className="h-3 w-3" aria-hidden />
              Repris de ta candidature
            </span>
          )}
        </div>
      </div>
      <div className="reborn-perso-carte-bas">
        <Numero numero={perso.playerNumber} />
        <Donnee icone={<Timer className="h-3 w-3" />} label="Temps de jeu" valeur={tempsDeJeu(perso.playMinutes)} />
        <Donnee
          icone={<CalendarClock className="h-3 w-3" />}
          label="Dernière partie"
          valeur={vu ? dernierePartie(vu) : "Pas encore"}
          doux={!vu}
        />
      </div>
    </div>
  );
}

export function EtatPersonnage({ perso }: { perso: PersonnageCompte }) {
  switch (perso.state) {
    case "DEAD":
      return (
        <span className="reborn-perso-etat reborn-perso-etat--mort">
          <Skull className="h-3 w-3" aria-hidden />
          Mort RP
        </span>
      );
    case "DELETED":
      return (
        <span className="reborn-perso-etat reborn-perso-etat--supprime">
          <Trash2 className="h-3 w-3" aria-hidden />
          Supprimé
        </span>
      );
    default:
      return (
        <span className="reborn-perso-etat reborn-perso-etat--vivant">
          <HeartPulse className="h-3 w-3" aria-hidden />
          En vie
        </span>
      );
  }
}

/**
 * N° 00042 + « Copier » (pour un ticket). Au-dessus de la zone cliquable de
 * la carte. Le N° n'existe que dans cette vue du propriétaire.
 */
function Numero({ numero }: { numero: string | null }) {
  const [copie, setCopie] = useState(false);
  const minuteur = useRef<number | null>(null);
  useEffect(
    () => () => {
      if (minuteur.current !== null) window.clearTimeout(minuteur.current);
    },
    [],
  );

  if (!numero) {
    return (
      <div className="reborn-perso-donnee">
        <span className="reborn-perso-donnee-label">N° de personnage</span>
        <span className="reborn-perso-donnee-valeur reborn-perso-donnee-valeur--doux">En attente</span>
      </div>
    );
  }

  async function copier(valeur: string) {
    try {
      await navigator.clipboard.writeText(valeur);
      setCopie(true);
      if (minuteur.current !== null) window.clearTimeout(minuteur.current);
      minuteur.current = window.setTimeout(() => setCopie(false), 1500);
    } catch (e) {
      // Presse-papiers refusé par la WebView : le N° reste lisible à l'écran.
      console.warn("[personnages] copie du N° impossible :", e);
    }
  }

  return (
    <div className="reborn-perso-donnee">
      <span className="reborn-perso-donnee-label">N° de personnage</span>
      <span className="reborn-perso-numero-ligne">
        <span className="reborn-perso-numero-valeur">{numero}</span>
        <button
          type="button"
          className="reborn-perso-numero-copier"
          onClick={() => void copier(numero)}
          title="Copier le N° (à donner dans un ticket)"
          aria-label={`Copier le N° ${numero}`}
        >
          {copie ? <Check className="h-3 w-3" aria-hidden /> : <Copy className="h-3 w-3" aria-hidden />}
          {copie ? "Copié" : "Copier"}
        </button>
      </span>
    </div>
  );
}

function Donnee({
  icone,
  label,
  valeur,
  doux,
}: {
  icone?: ReactNode;
  label: string;
  valeur: string;
  doux?: boolean;
}) {
  return (
    <div className="reborn-perso-donnee">
      <span className="reborn-perso-donnee-label">
        <span aria-hidden className="inline-flex">
          {icone}
        </span>
        {label}
      </span>
      <span className={doux ? "reborn-perso-donnee-valeur reborn-perso-donnee-valeur--doux" : "reborn-perso-donnee-valeur"}>
        {valeur}
      </span>
    </div>
  );
}

/**
 * suivant : un personnage existe déjà ; le suivant est libre (Q8), sans
 * candidature ni douane.
 */
export function EmplacementLibre({ suivant = false }: { suivant?: boolean }) {
  return (
    <div role="listitem" className="reborn-perso-libre">
      <span className="reborn-perso-libre-icone" aria-hidden>
        <Plus className="h-5 w-5" />
      </span>
      <div>
        <p className="reborn-perso-libre-titre">Emplacement libre</p>
        <p className="reborn-perso-libre-texte">
          {suivant
            ? "Crée-le en jeu, depuis l'écran de sélection : village au choix, sans clan au départ, sans candidature ni douane."
            : "Crée ton personnage en jeu, depuis l'écran de sélection."}
        </p>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Blason (clan + village)
// ──────────────────────────────────────────────────────

const masque = (url: string): CSSProperties => ({ maskImage: `url(${url})`, WebkitMaskImage: `url(${url})` });

/**
 * Tuile laquée : blason du clan teinté or, emblème du village en médaillon.
 * Sans clan (premier personnage, Q4) : l'emblème du village seul, en grand.
 * Ni l'un ni l'autre connu : une silhouette.
 */
export function Blason({ perso, taille }: { perso: PersonnageCompte; taille: 64 | 96 }) {
  const style = { "--reborn-perso-taille": `${taille}px` } as CSSProperties;
  const clan = emblemeClan(perso.clan);
  const village = emblemeVillage(perso.village);
  const principal = clan ?? village;
  return (
    <span
      className="reborn-perso-blason"
      style={style}
      data-eteint={eteint(perso) ? "true" : undefined}
      aria-hidden
    >
      {principal ? (
        <span className="reborn-perso-blason-clan" style={masque(principal)} />
      ) : (
        <UserRound size={Math.round(taille * 0.42)} />
      )}
      {clan && village && (
        <span className="reborn-perso-blason-village">
          <span style={masque(village)} />
        </span>
      )}
    </span>
  );
}
