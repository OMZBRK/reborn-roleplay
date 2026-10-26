// MAQUETTE L8 — suivi après l'envoi (§2.7), dans la coque.
//
// Frise ① Écrit envoyé → ② Écrit relu → ③ Douane → ④ Bienvenue, puis la carte
// d'état, puis le fil avec le staff (vrai composant ChatThread, messages
// fictifs). Aucun délai de relecture promis (Q11).
//
// Variantes : relecture (écrit en attente), accepte (écrit accepté → réserver
// la douane), revoir (seuls les onglets visés rouverts), refuse (compte à
// rebours avant une nouvelle candidature).

import { useMemo, useState, type CSSProperties, type ReactNode } from "react";
import { ArrowRight, CalendarClock, Check, Clock, Lock, PenLine, X } from "lucide-react";
import { ChatThread, type ChatMessage } from "../../../components/chat/ChatThread";
import { DiscordGlyph } from "../../../components/home/FooterMega";
import { CoqueCandidature } from "./Coque";
import { useAllerEcran } from "./contexte";
import {
  ENVOYE_LE,
  FIL_SUIVI,
  MAINTENANT,
  NOTE_STAFF_ACCEPTE,
  NOTE_STAFF_REFUS,
  NOTE_STAFF_REVOIR,
  NOUVELLE_CANDIDATURE_LE,
  REFUSE_LE,
  RELU_LE,
  compteARebours,
  heure,
  jourLong,
} from "./donnees";
import { EnTete } from "./ui";

export type VarianteSuivi = "relecture" | "accepte" | "revoir" | "refuse";
export type OngletSuivi = "suivi" | "dossier" | "douane";

const quand = (d: Date) => `${jourLong(d)} à ${heure(d)}`;

/** Onglets de la barre du haut une fois la candidature envoyée. */
export function OngletsSuivi({ actif, douaneOuverte }: { actif: OngletSuivi; douaneOuverte: boolean }) {
  const aller = useAllerEcran();
  const items: { id: OngletSuivi; libelle: string; ecran: string; verrou?: boolean }[] = [
    { id: "suivi", libelle: "Suivi", ecran: douaneOuverte ? "L8-accepte" : "L8" },
    { id: "dossier", libelle: "Mon dossier", ecran: "L6-complet" },
    { id: "douane", libelle: "Douane", ecran: "L9", verrou: !douaneOuverte },
  ];
  return (
    <>
      {items.map((it) => (
        <button
          key={it.id}
          type="button"
          className="mqc-tab"
          data-active={actif === it.id || undefined}
          disabled={it.verrou}
          title={it.verrou ? "S'ouvre quand ton écrit est accepté" : undefined}
          onClick={() => aller(it.ecran)}
        >
          {it.verrou && <Lock size={13} aria-hidden />}
          {it.libelle}
        </button>
      ))}
    </>
  );
}

type Etape = { libelle: string; date: string; etat: "fait" | "courant" | "futur" | "refus" | "revoir" };

function Frise({ etapes }: { etapes: Etape[] }) {
  return (
    <ol className="mqc-frise m-0 list-none p-0" aria-label="Avancement">
      {etapes.map((e, i) => (
        <li key={e.libelle} className="mqc-frise-step" data-etat={e.etat}>
          <span className="mqc-frise-dot" aria-hidden>
            {e.etat === "fait" ? (
              <Check size={15} strokeWidth={3} />
            ) : e.etat === "refus" ? (
              <X size={15} strokeWidth={3} />
            ) : e.etat === "revoir" ? (
              <PenLine size={13} strokeWidth={2.5} />
            ) : (
              i + 1
            )}
          </span>
          <div className="mqc-frise-label">{e.libelle}</div>
          <div className="mqc-frise-date">{e.date}</div>
        </li>
      ))}
    </ol>
  );
}

function CarteEtat({
  ton,
  surtitre,
  titre,
  children,
}: {
  ton: string;
  surtitre: string;
  titre: string;
  children: ReactNode;
}) {
  return (
    <section className="mqc-status" style={{ "--mqc-ton": ton } as CSSProperties}>
      <div className="mqc-status-kicker">{surtitre}</div>
      <h2 className="mqc-status-title">{titre}</h2>
      {children}
    </section>
  );
}

function NoteStaff({ qui, texte }: { qui: string; texte: string }) {
  return (
    <blockquote className="mqc-quote">
      <span className="mqc-quote-who">{qui}</span>
      {texte}
    </blockquote>
  );
}

// Message système posé par l'API avec la décision (§1.3).
const DECISION: Record<VarianteSuivi, string | null> = {
  relecture: null,
  accepte: "Écrit accepté. Réserve ta séance de douane dans le launcher.",
  revoir: "Candidature à revoir : partie RP. Les onglets concernés sont rouverts.",
  refuse: "Candidature refusée.",
};

function Fil({ variante }: { variante: VarianteSuivi }) {
  const ferme = variante === "refuse";
  const [messages, setMessages] = useState<ChatMessage[]>(() => {
    const fil: ChatMessage[] = FIL_SUIVI.map((m) => ({ ...m, attachments: [] }));
    const decision = DECISION[variante];
    if (decision) {
      fil.push({ id: "decision", author: "system", authorName: null, content: decision, attachments: [], createdAt: "2026-10-24T14:05:00" });
    }
    return fil;
  });
  return (
    <div className="mt-9">
      <div className="mb-1 flex items-baseline justify-between">
        <h3 className="m-0 font-display text-[22px] font-normal tracking-wide text-foreground">Échanges avec le staff</h3>
        <span className="text-[11.5px] text-foreground-muted">Réponses aussi sur Discord</span>
      </div>
      <div className="mqc-thread">
        <ChatThread
          messages={messages}
          sync={{ error: null, offline: false }}
          onRetry={() => {}}
          onSend={async (content) => {
            setMessages((l) => [
              ...l,
              { id: `local-${l.length}`, author: "me", authorName: null, content, attachments: [], createdAt: new Date().toISOString() },
            ]);
          }}
          intro={`Candidature envoyée le ${quand(ENVOYE_LE)}.`}
          meLabel="Toi"
          placeholder="Écris au staff… (Ctrl+Entrée pour envoyer)"
          closedNotice={
            ferme ? (
              <div className="mb-5 rounded-md border border-border px-4 py-3 text-center text-[12.5px] text-foreground-muted">
                Le fil est fermé avec la décision. Une question ? Ouvre un ticket.
              </div>
            ) : undefined
          }
        />
      </div>
    </div>
  );
}

export function SuiviCandidature({ variante = "relecture", coque = true }: { variante?: VarianteSuivi; coque?: boolean }) {
  const aller = useAllerEcran();

  const etapes = useMemo<Etape[]>(() => {
    const envoye: Etape = { libelle: "Écrit envoyé", date: `${jourLong(ENVOYE_LE)}, ${heure(ENVOYE_LE)}`, etat: "fait" };
    switch (variante) {
      case "relecture":
        return [
          envoye,
          { libelle: "Écrit relu", date: "En cours de relecture", etat: "courant" },
          { libelle: "Douane", date: "Oral en jeu", etat: "futur" },
          { libelle: "Bienvenue", date: "Ton personnage", etat: "futur" },
        ];
      case "accepte":
        return [
          envoye,
          { libelle: "Écrit relu", date: `Accepté le ${jourLong(RELU_LE)}`, etat: "fait" },
          { libelle: "Douane", date: "Réserve ta séance", etat: "courant" },
          { libelle: "Bienvenue", date: "Ton personnage", etat: "futur" },
        ];
      case "revoir":
        return [
          envoye,
          { libelle: "Écrit relu", date: "À revoir · partie RP", etat: "revoir" },
          { libelle: "Douane", date: "Oral en jeu", etat: "futur" },
          { libelle: "Bienvenue", date: "Ton personnage", etat: "futur" },
        ];
      case "refuse":
        return [
          envoye,
          { libelle: "Écrit relu", date: `Refusé le ${jourLong(REFUSE_LE)}`, etat: "refus" },
          { libelle: "Douane", date: "—", etat: "futur" },
          { libelle: "Bienvenue", date: "—", etat: "futur" },
        ];
    }
  }, [variante]);

  return (
    <CoqueCandidature
      onglets={<OngletsSuivi actif="suivi" douaneOuverte={variante === "accepte"} />}
      coque={coque}
      aides={false}
      dotWhitelist={variante === "relecture"}
    >
      <EnTete
        surtitre="Candidature whitelist"
        titre="Suivi de ta candidature"
        chapo="L'écrit d'abord, relu par l'équipe whitelist. Puis la douane : un oral à la voix, en jeu, face à un examinateur."
      />

      <Frise etapes={etapes} />

      {variante === "relecture" && (
        <CarteEtat ton="var(--color-gold)" surtitre="Écrit en relecture" titre="L'équipe whitelist lit ton dossier">
          <p className="mqc-status-text">
            Envoyé le {quand(ENVOYE_LE)}. Chaque dossier est lu en entier, par une personne : la réponse arrive ici et
            sur Discord. En attendant, tu peux répondre aux questions du staff dans le fil.
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className="mqc-chip" data-ton="gold">
              <Clock size={12} aria-hidden /> Partie HRP · en relecture
            </span>
            <span className="mqc-chip" data-ton="gold">
              <Clock size={12} aria-hidden /> Partie RP · en relecture
            </span>
            <span className="mqc-chip">Prise en charge par Ayame</span>
          </div>
        </CarteEtat>
      )}

      {variante === "accepte" && (
        <CarteEtat ton="var(--color-success)" surtitre="Écrit accepté" titre="Réserve ta séance de douane">
          <p className="mqc-status-text">
            Ton écrit est validé. Dernière étape : la douane, un oral à la voix, en jeu. Tu arrives dans le hall avec les
            autres candidats, un examinateur t'appelle dans son bureau pour une dizaine de minutes.
          </p>
          <NoteStaff qui="Note du staff · Ayame" texte={NOTE_STAFF_ACCEPTE} />
          <div className="mt-5 flex items-center gap-4">
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big" onClick={() => aller("L9")}>
              <CalendarClock size={16} aria-hidden /> Choisir une séance
            </button>
            <span className="text-[12px] text-foreground-muted">5 séances ouvertes cette semaine</span>
          </div>
        </CarteEtat>
      )}

      {variante === "revoir" && (
        <CarteEtat ton="var(--color-warning)" surtitre="Écrit à revoir" titre="Quelques points à reprendre">
          <p className="mqc-status-text">
            Ce n'est pas un refus : corrige les onglets rouverts et renvoie. Le reste de ton dossier est gardé tel quel.
          </p>
          <NoteStaff qui="Commentaire du staff · Ayame" texte={NOTE_STAFF_REVOIR} />
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className="mr-1 text-[11px] font-semibold uppercase tracking-[0.12em] text-foreground-muted">
              Rouverts
            </span>
            {["2 Personnage", "3 Village", "4 Histoire", "5 Voix"].map((o) => (
              <span key={o} className="mqc-chip" data-ton="warning">
                <PenLine size={11} aria-hidden /> {o}
              </span>
            ))}
            <span className="mqc-chip ml-auto" data-ton="lock" title="Partie HRP validée, verrouillée">
              <Lock size={11} aria-hidden /> 1 Vous · validé
            </span>
          </div>
          <div className="mt-5">
            <button type="button" className="wl-btn-primary glow wl-btn-cta-big" onClick={() => aller("L2")}>
              Corriger ma candidature <ArrowRight size={16} aria-hidden />
            </button>
          </div>
        </CarteEtat>
      )}

      {variante === "refuse" && (
        <CarteEtat ton="var(--color-danger)" surtitre="Écrit refusé" titre="Ta candidature n'est pas retenue">
          <NoteStaff qui="Commentaire du staff · Ayame" texte={NOTE_STAFF_REFUS} />
          <p className="mqc-status-text">Ton dossier est gardé : tu pourras repartir de lui pour écrire la nouvelle candidature.</p>
          <div className="mt-5 flex items-center gap-5">
            <button type="button" className="wl-btn-primary wl-btn-cta-big mqc-cta" disabled>
              Refaire une candidature
            </button>
            <div>
              <div className="text-[13px] font-semibold text-foreground">
                Possible le {quand(NOUVELLE_CANDIDATURE_LE)}
              </div>
              <div className="mt-0.5 text-[12px] text-gold">{compteARebours(NOUVELLE_CANDIDATURE_LE, MAINTENANT)}</div>
            </div>
          </div>
        </CarteEtat>
      )}

      <Fil variante={variante} />

      <div className="mt-8 flex items-center gap-4 border-t border-border pt-5">
        <span className="grid h-9 w-9 place-items-center rounded-full bg-[#5865f2] text-white">
          <DiscordGlyph className="h-4 w-4" />
        </span>
        <span className="flex-1 text-[12.5px] text-foreground-subtle">
          Les décisions et les séances de douane sont aussi annoncées sur le Discord.
        </span>
        <button type="button" className="mqc-btn">
          Ouvrir le Discord
        </button>
        {variante !== "refuse" && (
          <button type="button" className="mqc-link !text-foreground-muted">
            Retirer ma candidature
          </button>
        )}
      </div>
    </CoqueCandidature>
  );
}
