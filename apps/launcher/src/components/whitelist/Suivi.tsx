// Suivi après l'envoi (§2.7), maquette L8 : frise ① Écrit envoyé → ② Écrit
// relu → ③ Douane → ④ Bienvenue, carte d'état selon la phase calculée par
// l'API, fil avec le staff (ChatThread existant, sondé toutes les 5 s).
// Aucun délai de relecture promis (Q11).
//
// Phases : écrit en relecture, écrit à revoir (seuls les onglets visés
// rouverts), écrit accepté (réserver la douane), place réservée, douane à
// revoir (nouvelle séance dès le lendemain), refusé (compte à rebours avant
// une nouvelle candidature complète), accepté.

import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { ArrowRight, CalendarClock, Clock, Lock, PenLine, RefreshCw, Shield } from "lucide-react";
import {
  demanderReprise,
  envoyerMessage,
  lireMessages,
  retirerCandidature,
  type CandidatureMe,
  type EtatDouane,
  type MessageCandidature,
  type StatutDecision,
} from "../../lib/candidature";
import { formatIpcError } from "../../lib/errors";
import { ChatThread, useChatFeed, type ChatMessage } from "../chat/ChatThread";
import { DiscordGlyph } from "../home/FooterMega";
import { openDiscordInvite } from "../home/DiscordCard";
import { Modal } from "../system/Modal";
import { CarteEtat, Frise, GrilleConvocation, NoteStaff, type EtapeFrise } from "./blocs";
import { compteARebours, dateDe, duree, heure, HEURE_MS, jourLong, majuscule, quand } from "./format";
import { useMaintenant } from "./horloge";
import { ONGLETS, partieDe } from "./regles";
import { EnTete } from "./ui";

export type VueSuivi = "suivi" | "dossier" | "douane";

const PHASES_DOUANE = new Set(["DOUANE_A_RESERVER", "DOUANE_RESERVEE", "DOUANE_A_REVOIR"]);
/** « Demander une reprise » possible 4 h après la prise en charge. */
const REPRISE_APRES_MS = 4 * HEURE_MS;

/** Onglets de la barre du haut une fois la candidature envoyée. */
export function OngletsSuivi({
  actif,
  douaneOuverte,
  avecDossier,
  onChoisir,
}: {
  actif: VueSuivi;
  douaneOuverte: boolean;
  avecDossier: boolean;
  onChoisir: (v: VueSuivi) => void;
}) {
  const items: { id: VueSuivi; libelle: string; verrou?: boolean; cache?: boolean }[] = [
    { id: "suivi", libelle: "Suivi" },
    { id: "dossier", libelle: "Mon dossier", cache: !avecDossier },
    { id: "douane", libelle: "Douane", verrou: !douaneOuverte },
  ];
  return (
    <>
      {items
        .filter((it) => !it.cache)
        .map((it) => (
          <button
            key={it.id}
            type="button"
            className="cand-tab"
            data-active={actif === it.id || undefined}
            disabled={it.verrou}
            title={it.verrou ? "S'ouvre quand ton écrit est accepté" : undefined}
            onClick={() => onChoisir(it.id)}
          >
            {it.verrou && <Lock size={13} aria-hidden />}
            {it.libelle}
          </button>
        ))}
    </>
  );
}

export function douaneOuverte(phase: string): boolean {
  return PHASES_DOUANE.has(phase);
}

function toChatMessage(m: MessageCandidature): ChatMessage {
  return {
    id: m.id,
    author: m.authorType === "USER" ? "me" : m.authorType === "STAFF" ? "staff" : "system",
    authorName: m.authorName,
    content: m.content,
    attachments: m.attachments ?? [],
    createdAt: m.createdAt,
  };
}

const jourEtHeure = (d: Date) => `${jourLong(d)}, ${heure(d)}`;

function etapes(me: CandidatureMe, douaneEnJeu: boolean): EtapeFrise[] {
  const app = me.application;
  const s = me.suivi;
  const envoyeLe = dateDe(app?.submittedAt);
  const reluLe = dateDe(app?.writtenApprovedAt ?? app?.reviewedAt);
  const oralLe = dateDe(app?.oralDecidedAt);
  const envoye: EtapeFrise = {
    libelle: "Écrit envoyé",
    date: envoyeLe ? jourEtHeure(envoyeLe) : "—",
    etat: app ? "fait" : "futur",
  };
  const reluOk: EtapeFrise = {
    libelle: "Écrit relu",
    date: reluLe ? `Accepté le ${jourLong(reluLe)}` : "Accepté",
    etat: "fait",
  };
  const douaneFutur: EtapeFrise = { libelle: "Douane", date: douaneEnJeu ? "Oral en jeu" : "Oral à la voix", etat: "futur" };
  const bienvenue: EtapeFrise = { libelle: "Bienvenue", date: "Ton personnage", etat: "futur" };
  const resa = dateDe(s.reservation?.convocationAt);
  switch (s.phase) {
    case "ECRIT_EN_ATTENTE":
      return [envoye, { libelle: "Écrit relu", date: "En cours de relecture", etat: "courant" }, douaneFutur, bienvenue];
    case "ECRIT_A_REVOIR":
      return [
        envoye,
        { libelle: "Écrit relu", date: `À revoir · partie ${s.partiesRouvertes.join(" et ")}`, etat: "revoir" },
        douaneFutur,
        bienvenue,
      ];
    case "DOUANE_A_RESERVER":
      return [envoye, reluOk, { libelle: "Douane", date: "Réserve ta séance", etat: "courant" }, bienvenue];
    case "DOUANE_RESERVEE":
      return [
        envoye,
        reluOk,
        { libelle: "Douane", date: resa ? jourEtHeure(resa) : "Place réservée", etat: "courant" },
        bienvenue,
      ];
    case "DOUANE_A_REVOIR":
      return [envoye, reluOk, { libelle: "Douane", date: "À revoir · nouvelle séance", etat: "revoir" }, bienvenue];
    case "REFUSE":
      if (s.refusPar === "ORAL") {
        return [
          envoye,
          reluOk,
          { libelle: "Douane", date: oralLe ? `Refusée le ${jourLong(oralLe)}` : "Refusée", etat: "refus" },
          { ...bienvenue, date: "—" },
        ];
      }
      return [
        envoye,
        {
          libelle: "Écrit relu",
          date: dateDe(app?.reviewedAt) ? `Refusé le ${jourLong(dateDe(app?.reviewedAt) as Date)}` : "Refusé",
          etat: app ? "refus" : "futur",
        },
        { ...douaneFutur, date: "—" },
        { ...bienvenue, date: "—" },
      ];
    case "ACCEPTE":
      return [
        envoye,
        reluOk,
        {
          libelle: "Douane",
          date: !s.oralRequis && !oralLe ? "Déjà passée" : oralLe ? `Validée le ${jourLong(oralLe)}` : "Validée",
          etat: "fait",
        },
        { libelle: "Bienvenue", date: "Crée ton personnage en jeu", etat: "fait" },
      ];
    default:
      return [envoye, douaneFutur, bienvenue];
  }
}

function libellePartie(statut: StatutDecision): { texte: string; ton: string } {
  switch (statut) {
    case "APPROVED":
      return { texte: "validée", ton: "ok" };
    case "NEEDS_REVISION":
      return { texte: "à revoir", ton: "warning" };
    case "REJECTED":
      return { texte: "refusée", ton: "danger" };
    default:
      return { texte: "en relecture", ton: "gold" };
  }
}

function Fil({ ferme, approuve, envoyeLe }: { ferme: boolean; approuve: boolean; envoyeLe: Date | null }) {
  const feed = useChatFeed(() => lireMessages(), { context: "Fil de ta candidature non synchronisé" });
  const messages = useMemo(() => (feed.data ?? []).map(toChatMessage), [feed.data]);
  return (
    <div className="mt-9">
      <div className="mb-1 flex items-baseline justify-between">
        <h3 className="m-0 font-display text-[22px] font-normal tracking-wide text-foreground">Échanges avec le staff</h3>
        <span className="text-[11.5px] text-foreground-muted">Réponses aussi sur Discord</span>
      </div>
      <div className="cand-thread">
        <ChatThread
          messages={messages}
          loading={feed.loading}
          sync={feed.sync}
          onRetry={feed.refresh}
          onSend={async (content, attachmentUrls) => {
            const cree = await envoyerMessage(content, attachmentUrls);
            if (cree) feed.mutate((prev) => [...(prev ?? []), cree]);
            else feed.refresh();
          }}
          intro={envoyeLe ? `Candidature envoyée le ${quand(envoyeLe)}.` : "Candidature envoyée."}
          meLabel="Toi"
          placeholder="Écris au staff… (Ctrl+Entrée pour envoyer)"
          closedNotice={
            ferme ? (
              <div className="mb-5 rounded-md border border-border px-4 py-3 text-center text-[12.5px] text-foreground-muted">
                {approuve
                  ? "Ta candidature est acceptée : pour toute question, ouvre un ticket."
                  : "Le fil est fermé avec la décision. Une question ? Ouvre un ticket."}
              </div>
            ) : undefined
          }
        />
      </div>
    </div>
  );
}

type Props = {
  me: CandidatureMe;
  douane: EtatDouane | null;
  onCorriger: () => void;
  onRefaire: () => void;
  onDouane: () => void;
  /** Candidature retirée : relire le suivi. */
  onRetire: () => void;
};

export function SuiviCandidature({ me, douane, onCorriger, onRefaire, onDouane, onRetire }: Props) {
  const navigate = useNavigate();
  const app = me.application;
  const s = me.suivi;
  const douaneEnJeu = douane?.regles.douaneEnJeu ?? s.douaneEnJeu;
  const avance = douane?.regles.avanceEntreeMin ?? 15;
  const retard = douane?.regles.retardMaxMin ?? 30;
  const reapply = dateDe(s.canReapplyAt);
  const reservableDes = dateDe(douane?.prochaineSeanceDes ?? s.reservableDes);
  const now = useMaintenant(30_000, [reapply?.getTime(), reservableDes?.getTime()]);
  const [confirmer, setConfirmer] = useState(false);
  const [retrait, setRetrait] = useState<{ enCours: boolean; erreur: string | null }>({ enCours: false, erreur: null });
  const [reprise, setReprise] = useState<{ enCours: boolean; texte: string | null; ok: boolean }>({
    enCours: false,
    texte: null,
    ok: true,
  });

  const frise = useMemo(() => etapes(me, douaneEnJeu), [me, douaneEnJeu]);
  const envoyeLe = dateDe(app?.submittedAt);
  const ferme = app?.status === "APPROVED" || app?.status === "REJECTED";
  const reviewer = app?.reviewerName ? ` · ${app.reviewerName}` : "";
  const nbSeances = douane?.seances.filter((x) => !x.complet).length ?? null;

  const assignee = app?.assignee?.username ?? null;
  const assigneLe = dateDe(app?.assignedAt);
  const depuis = assigneLe ? now.getTime() - assigneLe.getTime() : 0;

  async function retirer() {
    setRetrait({ enCours: true, erreur: null });
    try {
      await retirerCandidature();
      setConfirmer(false);
      setRetrait({ enCours: false, erreur: null });
      onRetire();
    } catch (err) {
      setRetrait({ enCours: false, erreur: formatIpcError(err, "Retrait de ta candidature impossible") });
    }
  }

  async function reprendre() {
    setReprise({ enCours: true, texte: null, ok: true });
    try {
      await demanderReprise();
      setReprise({ enCours: false, ok: true, texte: "Demande envoyée : un autre membre de l'équipe peut reprendre ton dossier." });
    } catch (err) {
      setReprise({ enCours: false, ok: false, texte: formatIpcError(err, "Demande de reprise impossible") });
    }
  }

  // Place réservée : celle de la douane (fenêtre calculée par l'API), sinon celle du suivi.
  const place = douane?.reservation
    ? {
        convocation: dateDe(douane.reservation.convocationAt),
        ouvre: dateDe(douane.reservation.fenetre.ouvreAt),
        ferme: dateDe(douane.reservation.fenetre.fermeAt),
      }
    : s.reservation
      ? {
          convocation: dateDe(s.reservation.convocationAt),
          ouvre: dateDe(s.reservation.entreeOuvreAt),
          ferme: dateDe(s.reservation.entreeFermeAt),
        }
      : null;

  return (
    <>
      <EnTete
        surtitre="Candidature whitelist"
        titre="Suivi de ta candidature"
        chapo={
          douaneEnJeu
            ? "L'écrit d'abord, relu par l'équipe whitelist. Puis la douane : un oral à la voix, en jeu, face à un examinateur."
            : "L'écrit d'abord, relu par l'équipe whitelist. Puis l'oral, à la voix, face à un examinateur."
        }
      />

      {app && <Frise etapes={frise} />}

      {s.phase === "ECRIT_EN_ATTENTE" && (
        <CarteEtat ton="var(--color-gold)" surtitre="Écrit en relecture" titre="L'équipe whitelist lit ton dossier">
          <p className="cand-status-text">
            {envoyeLe ? `Envoyé le ${quand(envoyeLe)}. ` : ""}Chaque dossier est lu en entier, par une personne : la
            réponse arrive ici et sur Discord. En attendant, tu peux répondre aux questions du staff dans le fil.
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {app && (
              <>
                <span className="cand-chip" data-ton={libellePartie(app.hrpStatus).ton}>
                  <Clock size={12} aria-hidden /> Partie HRP · {libellePartie(app.hrpStatus).texte}
                </span>
                <span className="cand-chip" data-ton={libellePartie(app.rpStatus).ton}>
                  <Clock size={12} aria-hidden /> Partie RP · {libellePartie(app.rpStatus).texte}
                </span>
              </>
            )}
            {assignee && (
              <span className="cand-chip">
                Prise en charge par {assignee}
                {assigneLe ? ` depuis ${duree(depuis)}` : ""}
              </span>
            )}
            {assignee && depuis >= REPRISE_APRES_MS && (
              <button type="button" className="cand-link" disabled={reprise.enCours} onClick={() => void reprendre()}>
                {reprise.enCours ? <RefreshCw size={12} className="animate-spin" aria-hidden /> : null}
                Demander une reprise
              </button>
            )}
          </div>
          {reprise.texte && (
            <p className="mt-3 text-[12px]" style={{ color: reprise.ok ? "var(--color-foreground-subtle)" : "#f87171" }}>
              {reprise.texte}
            </p>
          )}
        </CarteEtat>
      )}

      {s.phase === "ECRIT_A_REVOIR" && (
        <CarteEtat ton="var(--color-warning)" surtitre="Écrit à revoir" titre="Quelques points à reprendre">
          <p className="cand-status-text">
            Ce n'est pas un refus : corrige les onglets rouverts et renvoie. Le reste de ton dossier est gardé tel quel.
          </p>
          {app?.reviewNotes && <NoteStaff qui={`Commentaire du staff${reviewer}`} texte={app.reviewNotes} />}
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className="mr-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-foreground-muted">Rouverts</span>
            {ONGLETS.filter((o) => {
              const p = partieDe(o.id);
              return p !== null && s.partiesRouvertes.includes(p);
            }).map((o) => (
              <span key={o.id} className="cand-chip" data-ton="warning">
                <PenLine size={11} aria-hidden /> {o.numero} {o.libelle}
              </span>
            ))}
            {!s.partiesRouvertes.includes("HRP") && (
              <span className="cand-chip ml-auto" data-ton="lock" title="Partie HRP validée, verrouillée">
                <Lock size={11} aria-hidden /> 1 Vous · validé
              </span>
            )}
            {!s.partiesRouvertes.includes("RP") && (
              <span className="cand-chip ml-auto" data-ton="lock" title="Partie RP validée, verrouillée">
                <Lock size={11} aria-hidden /> 2 à 5 · validés
              </span>
            )}
          </div>
          <div className="mt-5">
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big" onClick={onCorriger}>
              Corriger ma candidature <ArrowRight size={16} aria-hidden />
            </button>
          </div>
        </CarteEtat>
      )}

      {s.phase === "DOUANE_A_RESERVER" && (
        <CarteEtat ton="var(--color-success)" surtitre="Écrit accepté" titre="Réserve ta séance de douane">
          <p className="cand-status-text">
            {douaneEnJeu
              ? "Ton écrit est validé. Dernière étape : la douane, un oral à la voix, en jeu. Tu arrives dans le hall avec les autres candidats, un examinateur t'appelle dans son bureau pour une dizaine de minutes."
              : "Ton écrit est validé. Dernière étape : l'oral, à la voix, face à un examinateur, une dizaine de minutes. Choisis une séance : les consignes arrivent dans le fil."}
          </p>
          {app?.reviewNotes && <NoteStaff qui={`Note du staff${reviewer}`} texte={app.reviewNotes} />}
          <div className="mt-5 flex items-center gap-4">
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big" onClick={onDouane}>
              <CalendarClock size={16} aria-hidden /> Choisir une séance
            </button>
            {nbSeances !== null && (
              <span className="text-[12px] text-foreground-muted">
                {nbSeances === 0
                  ? "Aucune séance ouverte pour l'instant : elles sont annoncées sur le Discord."
                  : `${nbSeances} séance${nbSeances > 1 ? "s" : ""} ouverte${nbSeances > 1 ? "s" : ""}`}
              </span>
            )}
          </div>
        </CarteEtat>
      )}

      {s.phase === "DOUANE_RESERVEE" && place?.convocation && (
        <CarteEtat
          ton="var(--color-success)"
          surtitre="Place réservée"
          titre={`${majuscule(jourLong(place.convocation))} · ${heure(place.convocation)}`}
        >
          <p className="cand-status-text">
            Convocation {compteARebours(place.convocation, now)}.{" "}
            {douaneEnJeu
              ? "Arrive dans la fenêtre d'entrée : tu attendras ton tour dans le hall. Sur l'accueil, « Passer la douane » remplace « Jouer »."
              : "L'oral se passe hors du jeu pour le moment : suis les consignes du staff dans le fil."}
          </p>
          {place.ouvre && place.ferme && (
            <GrilleConvocation
              convocation={place.convocation}
              ouvre={place.ouvre}
              ferme={place.ferme}
              avanceMin={avance}
              retardMin={retard}
            />
          )}
          <div className="mt-5 flex items-center gap-4">
            <button type="button" className="cand-btn" onClick={onDouane}>
              <Shield size={14} aria-hidden /> Ma séance
            </button>
            {douaneEnJeu && (
              <button type="button" className="cand-link" onClick={() => navigate("/home")}>
                Aller à l'accueil <ArrowRight size={12} aria-hidden />
              </button>
            )}
          </div>
        </CarteEtat>
      )}

      {s.phase === "DOUANE_A_REVOIR" && (
        <CarteEtat ton="var(--color-warning)" surtitre="Douane à revoir" titre="Une nouvelle séance, et c'est reparti">
          <p className="cand-status-text">
            Ton écrit est gardé tel quel : seule la douane est à repasser.
            {/* La réservation est permise tout de suite, pour une séance qui
                commence au plus tôt le lendemain (Q7) : rien n'attend minuit. */}
            {reservableDes && reservableDes > now
              ? ` Tu peux réserver une séance dès maintenant, pour le ${jourLong(reservableDes)} ou plus tard.`
              : " Tu peux réserver une nouvelle séance."}
          </p>
          {app?.oralReason && <NoteStaff qui="Motif de l'examinateur" texte={app.oralReason} />}
          <div className="mt-5">
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big cand-cta" onClick={onDouane}>
              <CalendarClock size={16} aria-hidden /> Choisir une séance
            </button>
          </div>
        </CarteEtat>
      )}

      {s.phase === "REFUSE" && (
        <CarteEtat
          ton="var(--color-danger)"
          surtitre={s.refusPar === "ORAL" ? "Douane refusée" : "Écrit refusé"}
          titre="Ta candidature n'est pas retenue"
        >
          {s.refusPar === "ORAL" && app?.oralReason && <NoteStaff qui="Motif de l'examinateur" texte={app.oralReason} />}
          {app?.reviewNotes && (
            <NoteStaff
              qui={s.refusPar === "ORAL" ? `Commentaire de l'écrit${reviewer}` : `Commentaire du staff${reviewer}`}
              texte={app.reviewNotes}
            />
          )}
          <p className="cand-status-text">
            {app
              ? "Ton dossier est gardé : tu pourras repartir de lui pour écrire la nouvelle candidature, complète, partie HRP comprise."
              : "Tu pourras écrire une nouvelle candidature complète après le délai."}
          </p>
          <div className="mt-5 flex items-center gap-5">
            <button
              type="button"
              className="wl-btn-primary glow wl-btn-cta-big cand-cta"
              disabled={!!reapply && reapply > now}
              onClick={onRefaire}
            >
              Refaire une candidature
            </button>
            {reapply && reapply > now && (
              <div>
                <div className="text-[13px] font-semibold text-foreground">Possible le {quand(reapply)}</div>
                <div className="mt-0.5 text-[12px] text-gold">{compteARebours(reapply, now)}</div>
              </div>
            )}
          </div>
        </CarteEtat>
      )}

      {s.phase === "ACCEPTE" && (
        <CarteEtat ton="var(--color-success)" surtitre="Candidature acceptée" titre="Bienvenue sur Reborn">
          <p className="cand-status-text">
            Lance le jeu depuis l'accueil : ton premier personnage se crée en jeu. Prénom et nom de famille sont pré-remplis
            et modifiables ; le village et l'âge sont verrouillés, le sexe reste libre.
          </p>
          {app?.reviewNotes && <NoteStaff qui={`Note du staff${reviewer}`} texte={app.reviewNotes} />}
          <div className="mt-5">
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big" onClick={() => navigate("/home")}>
              Aller à l'accueil <ArrowRight size={16} aria-hidden />
            </button>
          </div>
        </CarteEtat>
      )}

      {app && <Fil ferme={ferme} approuve={app.status === "APPROVED"} envoyeLe={envoyeLe} />}

      <div className="mt-8 flex items-center gap-4 border-t border-border pt-5">
        <span className="grid h-9 w-9 place-items-center rounded-full bg-[#5865f2] text-white">
          <DiscordGlyph className="h-4 w-4" />
        </span>
        <span className="flex-1 text-[12.5px] text-foreground-subtle">
          Les décisions et les séances de douane sont aussi annoncées sur le Discord.
        </span>
        <button type="button" className="cand-btn" onClick={() => void openDiscordInvite().catch(() => {})}>
          Ouvrir le Discord
        </button>
        {/* Refusé (maquette L8) : pas de retrait, il effacerait le dossier
            dont la nouvelle candidature repart. */}
        {app && app.status !== "APPROVED" && s.phase !== "ACCEPTE" && s.phase !== "REFUSE" && (
          <button type="button" className="cand-link !text-foreground-muted" onClick={() => setConfirmer(true)}>
            Retirer ma candidature
          </button>
        )}
      </div>

      <Modal open={confirmer} onClose={retrait.enCours ? undefined : () => setConfirmer(false)} variant="danger" size="sm">
        <h2 className="pr-7 pt-1 font-display text-[17px] font-semibold leading-tight tracking-wide">
          Retirer ta candidature ?
        </h2>
        <p className="mt-3 text-[12.5px] leading-relaxed text-[var(--color-foreground-subtle)]">
          Ton dossier, ton enregistrement vocal et ton brouillon sont effacés, ta place de douane est libérée. Tu pourras
          en envoyer une nouvelle.
        </p>
        {retrait.erreur && <p className="mt-3 text-[12px] text-[#f87171]">{retrait.erreur}</p>}
        <div className="mt-5 flex gap-2">
          <button
            type="button"
            onClick={() => setConfirmer(false)}
            disabled={retrait.enCours}
            autoFocus
            className="flex h-9 flex-1 items-center justify-center rounded-md border border-[var(--color-border-strong)] bg-[var(--color-surface)] text-xs text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-surface-overlay)] disabled:opacity-50"
          >
            Annuler
          </button>
          <button
            type="button"
            onClick={() => void retirer()}
            disabled={retrait.enCours}
            className="flex h-9 flex-1 items-center justify-center gap-2 rounded-md bg-[var(--color-danger)] text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:cursor-wait disabled:opacity-70"
          >
            {retrait.enCours && <RefreshCw className="h-3.5 w-3.5 animate-spin" />}
            Retirer
          </button>
        </div>
      </Modal>
    </>
  );
}
