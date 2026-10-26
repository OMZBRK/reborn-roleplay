// MAQUETTE L11 : écran « Mes personnages » (CANDIDATURE_ET_DOUANE §4.6).
//
// Liste des personnages du compte sur le serveur principal (GET
// /v1/me/characters), une carte par personnage : blason du clan (vrai
// emblème du mod) et emblème du village, prénom + nom de famille, N°
// (copiable, pour un ticket), village, type (Ninja / Civil, Q3), grade, âge,
// sexe, état (en vie / mort RP), temps de jeu, dernière partie. Le premier
// personnage est sans clan (Q4 : la candidature n'en propose aucun) ; un
// clan ne s'obtient qu'en jeu, lors d'une session de clan (Ren Hozuki). Le premier porte « Repris de ta candidature » et
// ouvre son dossier d'entrée en lecture seule. Un emplacement libre invite à
// créer en jeu. Écran en lecture seule : on crée et on joue en jeu.
//
// Le N° n'apparaît que dans cette vue du propriétaire (GAMEPLAN §1.4, §1.9).

import { useState, type CSSProperties, type ReactNode } from "react";
import {
  CalendarClock,
  Check,
  Copy,
  FileCheck2,
  HeartPulse,
  Info,
  LifeBuoy,
  Play,
  Plus,
  Skull,
  Timer,
  UsersRound,
} from "lucide-react";
import { CoqueLauncher } from "./CoqueLauncher";
import { emblemeClan, emblemeVillage } from "./emblemes";
import {
  COMPTE_JOUEUR,
  EMPLACEMENTS,
  NOM_VILLAGE,
  PERSONNAGES,
  TEMPS_COMPTE_DEPUIS,
  type Personnage,
} from "./donnees";

type Props = {
  /** Liste affichée ; vide = état vide. */
  personnages?: Personnage[];
  /** Emplacements du compte (2 de base, plus ceux débloqués par le staff). */
  emplacements?: number;
  /** Personnage ouvert au départ (id) ; par défaut le premier. */
  choixInitial?: string;
  pleineHauteur?: boolean;
};

const EMPLACEMENTS_FICTIFS = EMPLACEMENTS.base + EMPLACEMENTS.debloques;

/** L11 dans la coque du launcher. */
export function MesPersonnagesMaquette({
  personnages = PERSONNAGES,
  emplacements = EMPLACEMENTS_FICTIFS,
  choixInitial,
  pleineHauteur,
}: Props) {
  return (
    <CoqueLauncher actif="personnages" compte={COMPTE_JOUEUR} pleineHauteur={pleineHauteur}>
      <MesPersonnages personnages={personnages} emplacements={emplacements} choixInitial={choixInitial} />
    </CoqueLauncher>
  );
}

/** Le contenu de la page seul (sans coque). */
export function MesPersonnages({
  personnages = PERSONNAGES,
  emplacements = EMPLACEMENTS_FICTIFS,
  choixInitial,
}: {
  personnages?: Personnage[];
  emplacements?: number;
  choixInitial?: string;
}) {
  const total = emplacements;
  const libres = Math.max(0, total - personnages.length);
  const [choisi, setChoisi] = useState<string | null>(choixInitial ?? personnages[0]?.id ?? null);
  const selection = personnages.find((p) => p.id === choisi) ?? personnages[0];

  return (
    <div className="mqa-perso-page">
      <header className="mqa-perso-tete">
        <div>
          <p className="mqa-kicker">Recensement</p>
          <h1 className="mqa-perso-titre">Mes personnages</h1>
          <p className="mqa-perso-sous">
            Les personnages de ton compte sur Reborn. On les crée et on les joue en jeu ; ici, tu
            les retrouves, avec leur N° à donner dans un ticket.
          </p>
        </div>
        <div className="mqa-perso-resume">
          <span className="mqa-chip">
            <UsersRound className="h-3 w-3" aria-hidden />
            {personnages.length} / {total} emplacements
          </span>
          {personnages.length > 0 && (
            <span className="mqa-perso-note">
              <Info className="h-3 w-3" aria-hidden />
              Temps de jeu compté depuis le {TEMPS_COMPTE_DEPUIS}
            </span>
          )}
        </div>
      </header>

      {personnages.length === 0 ? (
        <EtatVide total={total} />
      ) : (
        <div className="mqa-perso-grille">
          <div className="mqa-perso-liste" role="list">
            {personnages.map((p) => (
              <CartePersonnage
                key={p.id}
                perso={p}
                choisi={p.id === selection?.id}
                onChoisir={() => setChoisi(p.id)}
              />
            ))}
            {Array.from({ length: libres }, (_, i) => (
              <EmplacementLibre key={`libre-${i}`} suivant />
            ))}
          </div>
          {selection && <DetailPersonnage perso={selection} />}
        </div>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────
//  Carte de la liste
// ──────────────────────────────────────────────────────

function CartePersonnage({
  perso,
  choisi,
  onChoisir,
}: {
  perso: Personnage;
  choisi: boolean;
  onChoisir: () => void;
}) {
  const mort = perso.etat === "mort";
  return (
    <div
      role="listitem"
      className="mqa-carte"
      data-choisi={choisi ? "true" : undefined}
      data-mort={mort ? "true" : undefined}
    >
      <button
        type="button"
        className="mqa-carte-zone"
        onClick={onChoisir}
        aria-pressed={choisi}
        aria-label={`Voir la fiche de ${perso.prenom} ${perso.nomFamille}`}
      />
      <div className="mqa-carte-haut">
        <Blason perso={perso} taille={64} />
        <div className="mqa-carte-identite">
          <div className="mqa-carte-ligne1">
            <h2 className="mqa-carte-nom">
              {perso.prenom} <span>{perso.nomFamille}</span>
            </h2>
            <EtatPersonnage perso={perso} />
          </div>
          <p className="mqa-carte-meta">
            {perso.clan ? `Clan ${perso.nomFamille}, rejoint en session` : "Sans clan"} · {NOM_VILLAGE[perso.village]}
          </p>
          <p className="mqa-carte-meta mqa-carte-meta--doux">
            {perso.type === "Civil" ? "Civil" : `${perso.type} · ${perso.grade}`} · {perso.age} ans · {perso.sexe}
          </p>
          {perso.issuDeCandidature && (
            <span className="mqa-chip mqa-chip--or">
              <FileCheck2 className="h-3 w-3" aria-hidden />
              Repris de ta candidature
            </span>
          )}
        </div>
      </div>
      <div className="mqa-carte-bas">
        <Numero numero={perso.numero} />
        <Donnee icone={<Timer className="h-3 w-3" />} label="Temps de jeu" valeur={perso.tempsDeJeu} />
        <Donnee
          icone={<CalendarClock className="h-3 w-3" />}
          label="Dernière partie"
          valeur={perso.dernierePartie}
        />
      </div>
    </div>
  );
}

function EtatPersonnage({ perso }: { perso: Personnage }) {
  if (perso.etat === "mort") {
    return (
      <span className="mqa-etat mqa-etat--mort">
        <Skull className="h-3 w-3" aria-hidden />
        Mort RP{perso.mortLe ? ` · ${perso.mortLe}` : ""}
      </span>
    );
  }
  return (
    <span className="mqa-etat mqa-etat--vivant">
      <HeartPulse className="h-3 w-3" aria-hidden />
      En vie
    </span>
  );
}

/** N° 00042 + « Copier » (pour un ticket). Au-dessus de la zone cliquable de la carte. */
function Numero({ numero }: { numero: string }) {
  const [copie, setCopie] = useState(false);
  async function copier() {
    try {
      await navigator.clipboard.writeText(numero);
      setCopie(true);
      window.setTimeout(() => setCopie(false), 1500);
    } catch {
      // Presse-papiers refusé (aperçu hors contexte sécurisé) : rien à faire.
    }
  }
  return (
    <div className="mqa-donnee mqa-numero">
      <span className="mqa-donnee-label">N° de personnage</span>
      <span className="mqa-numero-ligne">
        <span className="mqa-numero-valeur">{numero}</span>
        <button
          type="button"
          className="mqa-numero-copier"
          onClick={() => void copier()}
          title="Copier le N° (à donner dans un ticket)"
          aria-label={`Copier le N° ${numero}`}
        >
          {copie ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
          {copie ? "Copié" : "Copier"}
        </button>
      </span>
    </div>
  );
}

function Donnee({ icone, label, valeur }: { icone?: ReactNode; label: string; valeur: string }) {
  return (
    <div className="mqa-donnee">
      <span className="mqa-donnee-label">
        {icone}
        {label}
      </span>
      <span className="mqa-donnee-valeur">{valeur}</span>
    </div>
  );
}

/** suivant : un personnage existe déjà ; le suivant est libre (Q8), sans candidature ni douane. */
function EmplacementLibre({ suivant = false }: { suivant?: boolean }) {
  return (
    <div role="listitem" className="mqa-libre">
      <span className="mqa-libre-icone" aria-hidden>
        <Plus className="h-5 w-5" />
      </span>
      <div>
        <p className="mqa-libre-titre">Emplacement libre</p>
        <p className="mqa-libre-texte">
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

/**
 * Tuile laquée : blason du clan teinté or, emblème du village en médaillon.
 * Sans clan (premier personnage, Q4) : l'emblème du village seul, en grand.
 */
function Blason({ perso, taille }: { perso: Personnage; taille: 64 | 96 }) {
  const style = { "--mqa-taille": `${taille}px` } as CSSProperties;
  const principal = perso.clan ? emblemeClan(perso.clan) : emblemeVillage(perso.village);
  return (
    <span className="mqa-blason" style={style} data-mort={perso.etat === "mort" ? "true" : undefined} aria-hidden>
      <span className="mqa-blason-clan" style={{ maskImage: `url(${principal})`, WebkitMaskImage: `url(${principal})` }} />
      {perso.clan && (
        <span className="mqa-blason-village">
          <span
            style={{
              maskImage: `url(${emblemeVillage(perso.village)})`,
              WebkitMaskImage: `url(${emblemeVillage(perso.village)})`,
            }}
          />
        </span>
      )}
    </span>
  );
}

// ──────────────────────────────────────────────────────
//  Panneau de détail
// ──────────────────────────────────────────────────────

function DetailPersonnage({ perso }: { perso: Personnage }) {
  const dossier = perso.dossier;
  return (
    <aside className="mqa-detail" aria-label={`Fiche de ${perso.prenom} ${perso.nomFamille}`}>
      <div className="mqa-detail-tete">
        <Blason perso={perso} taille={96} />
        <div className="min-w-0">
          <p className="mqa-kicker mqa-kicker--or">
            {dossier ? (
              <>
                <FileCheck2 className="h-3 w-3" aria-hidden />
                Dossier d'entrée
              </>
            ) : (
              "Fiche du personnage"
            )}
          </p>
          <h2 className="mqa-detail-nom">
            {perso.prenom} {perso.nomFamille}
          </h2>
          <p className="mqa-detail-sous">
            {dossier
              ? `Validé à la douane le ${dossier.douaneLe}`
              : perso.etat === "mort"
                ? `Mort RP le ${perso.mortLe ?? "—"}`
                : `Créé le ${perso.creeLe}`}
            <span className="mqa-detail-sous-doux">
              {dossier && dossier.douaneLe === perso.creeLe
                ? " · créé le soir même"
                : dossier || perso.etat === "mort"
                  ? ` · créé le\u00a0${perso.creeLe}`
                  : ""}
            </span>
          </p>
        </div>
      </div>

      <dl className="mqa-detail-identite">
        <Ligne terme="Village" valeur={NOM_VILLAGE[perso.village]} />
        <Ligne terme="Clan" valeur={perso.clan ? perso.nomFamille : "Sans clan"} />
        <Ligne terme="Type" valeur={perso.type} />
        <Ligne terme="Grade" valeur={perso.type === "Civil" ? "—" : perso.grade} />
        <Ligne terme="Âge · sexe" valeur={`${perso.age} ans · ${perso.sexe}`} />
        <Ligne terme="N°" valeur={perso.numero} mono />
      </dl>

      {dossier ? (
        <div className="mqa-detail-sections">
          <Section titre="Histoire" texte={dossier.histoire} />
          <Section titre="Objectifs" texte={dossier.objectifs} />
        </div>
      ) : (
        <p className="mqa-detail-vide">
          Ce personnage n'est pas issu de ta candidature : il n'a pas de dossier d'entrée.
          {perso.clan &&
            ` Clan ${perso.nomFamille} rejoint en jeu, lors d'une session de clan${perso.clanRejointLe ? ` (${perso.clanRejointLe})` : ""}.`}
          {perso.etat === "mort" && " Mort RP, il reste dans ton recensement mais ne se joue plus."}
        </p>
      )}

      <div className="mqa-detail-pied">
        <Info className="h-3.5 w-3.5 flex-shrink-0" aria-hidden />
        <span>
          Lecture seule. Pour corriger un nom, un village ou un âge, ouvre un ticket avec le N°.
        </span>
        <button type="button" className="mqa-lien">
          <LifeBuoy className="h-3 w-3" aria-hidden />
          Ouvrir un ticket
        </button>
      </div>
    </aside>
  );
}

function Ligne({ terme, valeur, mono }: { terme: string; valeur: string; mono?: boolean }) {
  return (
    <div>
      <dt>{terme}</dt>
      <dd className={mono ? "mqa-mono" : undefined}>{valeur}</dd>
    </div>
  );
}

/** Texte du dossier, replié à 3 lignes, « Lire la suite » s'il dépasse. */
function Section({ titre, texte }: { titre: string; texte: string }) {
  const [ouvert, setOuvert] = useState(false);
  const long = texte.length > 170;
  return (
    <section className="mqa-section">
      <h3>{titre}</h3>
      <p data-replie={long && !ouvert ? "true" : undefined}>{texte}</p>
      {long && (
        <button type="button" className="mqa-lien" onClick={() => setOuvert((v) => !v)}>
          {ouvert ? "Replier" : "Lire la suite"}
        </button>
      )}
    </section>
  );
}

// ──────────────────────────────────────────────────────
//  État vide
// ──────────────────────────────────────────────────────

function EtatVide({ total }: { total: number }) {
  return (
    <div className="mqa-vide">
      <div className="mqa-vide-carte">
        <span className="mqa-vide-icone" aria-hidden>
          <UsersRound className="h-6 w-6" />
        </span>
        <h2 className="mqa-vide-titre">Aucun personnage pour l'instant</h2>
        <p className="mqa-vide-texte">
          Ton premier personnage se crée en jeu, à ta première connexion, à partir de ta
          candidature.
        </p>
        <button type="button" className="mqa-vide-bouton">
          <Play className="h-3.5 w-3.5" fill="currentColor" aria-hidden />
          Lancer le jeu
        </button>
        <ol className="mqa-etapes">
          <li>
            <span className="mqa-etape-num">1</span>
            <b>Lance le jeu</b>
            <span>La sélection s'ouvre sur « Nouveau shinobi ».</span>
          </li>
          <li>
            <span className="mqa-etape-num">2</span>
            <b>Création pré-remplie</b>
            <span>Repris de ta candidature, sans clan. Village et âge verrouillés ; prénom et sexe modifiables.</span>
          </li>
          <li>
            <span className="mqa-etape-num">3</span>
            <b>Il arrive ici</b>
            <span>En moins d'une minute, avec son N°.</span>
          </li>
        </ol>
      </div>
      <div className="mqa-perso-liste mqa-perso-liste--vide" role="list">
        {Array.from({ length: total }, (_, i) => (
          <EmplacementLibre key={i} />
        ))}
      </div>
    </div>
  );
}
