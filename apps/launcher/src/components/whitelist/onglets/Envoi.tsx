// Onglet 6 « Envoi » (§2.6), maquettes L6 et L7 « envoi refusé ».
//
// 1. « Il reste à compléter » : une pastille par onglet incomplet, avec le
//    nombre de manques ; un clic mène au premier champ fautif.
// 2. Relecture complète, repliable par onglet, groupée Partie HRP / Partie RP,
//    avec « Modifier » ; l'enregistrement se réécoute.
// 3. Règlement + consentement voix (Q2, complément du 25/10), puis « Envoyer
//    ma candidature », grisé tant qu'il manque quelque chose.
// Un refus de l'API est traduit champ par champ, jamais « Erreur 400 » seul.

import { useState } from "react";
import { useNavigate } from "react-router";
import { AlertTriangle, ArrowRight, CircleCheck, Loader2, Send } from "lucide-react";
import { ecouterMaVoix } from "../../../lib/candidature";
import { DiscordGlyph } from "../../home/FooterMega";
import { openDiscordInvite } from "../../home/DiscordCard";
import { useFormulaire } from "../contexte";
import { BlocRelecture, LecteurCompact, type DonneesRelecture } from "../Relecture";
import { CHAMPS_API, ONGLETS, ONGLETS_FORMULAIRE, type Cases, type OngletId } from "../regles";
import { Case, Section } from "../ui";

export type EtatEnvoi =
  | { kind: "repos" }
  | { kind: "envoi" }
  | { kind: "erreur"; message: string; parChamp: Record<string, string> };

function BandeauApi({ envoi }: { envoi: Extract<EtatEnvoi, { kind: "erreur" }> }) {
  const { allerA } = useFormulaire();
  const items = Object.entries(envoi.parChamp).map(([champ, texte]) => {
    const c = CHAMPS_API[champ];
    const o = c ? ONGLETS.find((x) => x.id === c.onglet) : undefined;
    return { champ, texte, onglet: c?.onglet ?? null, ou: o && c ? `${o.numero} · ${o.libelle} › ${c.libelle}` : champ };
  });
  return (
    <div className="cand-api" role="alert">
      <div className="cand-api-head">
        <AlertTriangle size={18} aria-hidden />
        <div>
          <div className="cand-api-title">Ta candidature n'est pas partie</div>
          <div className="cand-api-lead">
            {items.length > 0
              ? `Le serveur Reborn l'a refusée ${items.length > 1 ? `pour ${items.length} raisons` : "pour une raison"}, détaillée champ par champ. Rien n'est perdu : ton brouillon est enregistré.`
              : `${envoi.message} Rien n'est perdu : ton brouillon est enregistré.`}
          </div>
        </div>
      </div>
      {items.map((it) => (
        <div key={it.champ} className="cand-api-item">
          <span className="cand-api-where">{it.ou}</span>
          <span className="flex-1">{it.texte}</span>
          {it.onglet && (
            <button type="button" className="cand-link shrink-0" onClick={() => allerA(it.onglet as OngletId, it.champ)}>
              Aller au champ <ArrowRight size={12} aria-hidden />
            </button>
          )}
        </div>
      ))}
      {items.length > 0 && (
        <details className="cand-api-raw">
          <summary>Réponse du serveur</summary>
          <pre>{envoi.message}</pre>
        </details>
      )}
    </div>
  );
}

export function OngletEnvoi({
  cases,
  setCases,
  envoi,
  onEnvoyer,
}: {
  cases: Cases;
  setCases: (c: Cases) => void;
  envoi: EtatEnvoi;
  onEnvoyer: () => void;
}) {
  const ctx = useFormulaire();
  const { f, options, manques, allerA, editable, mode, voixLocale, voixServeur, voixOk } = ctx;
  const navigate = useNavigate();
  const parOnglet = ONGLETS_FORMULAIRE.map((o) => ({ ...o, liste: manques.filter((m) => m.onglet === o.id) })).filter(
    (o) => o.liste.length > 0,
  );
  // Ouvert au départ : le premier onglet incomplet ; dossier complet, tout
  // est ouvert (relecture complète, §2.6) ; envoi refusé, tout est replié
  // sous le bandeau.
  const [ouverts, setOuverts] = useState<Set<OngletId>>(() =>
    envoi.kind === "erreur"
      ? new Set<OngletId>()
      : manques.length === 0
        ? new Set<OngletId>(ONGLETS_FORMULAIRE.filter((o) => editable(o.id)).map((o) => o.id))
        : new Set<OngletId>([manques[0].onglet]),
  );
  const nbCases = (cases.rulesAccepted ? 0 : 1) + (cases.voiceConsent ? 0 : 1);
  const pret = manques.length === 0 && nbCases === 0 && envoi.kind !== "envoi";

  const bascule = (o: OngletId) =>
    setOuverts((s) => {
      const n = new Set(s);
      if (n.has(o)) n.delete(o);
      else n.add(o);
      return n;
    });

  const donnees: DonneesRelecture = {
    f,
    options,
    micTeste: f.micTeste,
    voix: voixOk ? (
      <LecteurCompact
        // Le fichier qui partira : celui du serveur (un essai trop court resté
        // sur ce PC ne compte pas).
        blob={voixLocale?.depose ? voixLocale.blob : null}
        charger={voixServeur ? () => ecouterMaVoix(voixServeur.mimeType) : undefined}
        dureeSec={voixServeur?.durationSec ?? (voixLocale?.depose ? voixLocale.dureeSec : null)}
        max={options.voix.dureeMaxSec}
      />
    ) : null,
  };
  const bloc = (o: OngletId) => (
    <BlocRelecture
      key={o}
      onglet={o}
      donnees={donnees}
      ouvert={ouverts.has(o)}
      onBascule={() => bascule(o)}
      manques={manques.filter((m) => m.onglet === o)}
      verrouille={!editable(o)}
      onModifier={() => allerA(o)}
    />
  );

  return (
    <>
      <Section
        titre="Relecture et envoi"
        chapo={
          mode === "revoir"
            ? "Relis tes corrections : seules les parties rouvertes repartent vers l'équipe whitelist."
            : "Une dernière relecture : ce que tu envoies est ce que l'équipe whitelist lira."
        }
      />

      {envoi.kind === "erreur" && <BandeauApi envoi={envoi} />}

      {parOnglet.length > 0 ? (
        <div className="cand-card mb-8">
          <div className="text-[13px] font-medium text-foreground">Il reste à compléter :</div>
          <div className="cand-todo">
            {parOnglet.map((o) => (
              <button
                key={o.id}
                type="button"
                className="cand-todo-chip"
                onClick={() => allerA(o.id, o.liste[0]?.champ)}
                title={o.liste.map((m) => m.libelle).join(", ")}
              >
                {o.libelle}
                <span className="cand-tab-pill" data-ton={o.liste.some((m) => m.dur) ? "danger" : undefined}>
                  {o.liste.length}
                </span>
                <ArrowRight size={13} aria-hidden />
              </button>
            ))}
          </div>
        </div>
      ) : envoi.kind === "erreur" ? null : (
        <div className="cand-note mb-8">
          <CircleCheck size={15} aria-hidden style={{ color: "var(--color-success)" }} />
          <span>
            {mode === "revoir"
              ? "Les onglets rouverts sont complets. Relis, coche les deux cases et renvoie."
              : "Tous les onglets sont complets. Relis une dernière fois, coche les deux cases et envoie."}
          </span>
        </div>
      )}

      <div className="cand-part-label">Partie HRP · toi</div>
      {bloc("vous")}
      <div className="cand-part-label">Partie RP · ton personnage</div>
      {(["personnage", "village", "histoire", "voix"] as const).map(bloc)}

      <div className="mt-9 flex flex-col gap-4" data-champ="rulesAccepted">
        <Case coche={cases.rulesAccepted} onChange={(v) => setCases({ ...cases, rulesAccepted: v })}>
          J'ai lu le{" "}
          <button
            type="button"
            className="cand-link !text-[13px]"
            onClick={(e) => {
              e.preventDefault();
              navigate("/rules");
            }}
          >
            règlement
          </button>{" "}
          et <strong>ces réponses sont de moi</strong>.
        </Case>
        <Case coche={cases.voiceConsent} onChange={(v) => setCases({ ...cases, voiceConsent: v })}>
          <strong>J'accepte que ma voix soit enregistrée pour ma candidature.</strong>
          <span className="mt-1 block text-[12px] leading-relaxed text-foreground-muted">
            Elle sert à présenter ton personnage. Seule l'équipe whitelist l'écoute. Elle est effacée 30 jours après la
            décision finale, ou dès que tu retires ta candidature.
          </span>
        </Case>
      </div>

      <div className="cand-actions">
        <button type="button" className="cand-prev" onClick={() => allerA("voix")}>
          Étape précédente
        </button>
        <button
          type="button"
          className="wl-btn-primary glow wl-btn-cta-big cand-cta"
          disabled={!pret}
          onClick={onEnvoyer}
        >
          {envoi.kind === "envoi" ? <Loader2 size={16} className="animate-spin" aria-hidden /> : <Send size={16} aria-hidden />}{" "}
          {envoi.kind === "envoi"
            ? "Envoi…"
            : mode === "revoir"
              ? "Renvoyer ma candidature"
              : envoi.kind === "erreur"
                ? "Renvoyer ma candidature"
                : "Envoyer ma candidature"}
        </button>
        <span className="cand-actions-note">
          {pret
            ? "Tu suivras la relecture ici même, et sur Discord."
            : envoi.kind === "envoi"
              ? "Envoi en cours…"
              : [
                  manques.length > 0 && `${manques.length} champ${manques.length > 1 ? "s" : ""} à compléter`,
                  nbCases > 0 && `${nbCases} case${nbCases > 1 ? "s" : ""} à cocher`,
                ]
                  .filter(Boolean)
                  .join(" · ")}
        </span>
      </div>

      <div className="mt-10 flex items-center gap-4 border-t border-border pt-5">
        <span className="grid h-9 w-9 place-items-center rounded-full bg-[#5865f2] text-white">
          <DiscordGlyph className="h-4 w-4" />
        </span>
        <span className="flex-1 text-[12.5px] text-foreground-subtle">
          Les annonces de la whitelist passent par le Discord : séances de douane, sessions de clan, réponses.
        </span>
        <button type="button" className="cand-btn" onClick={() => void openDiscordInvite().catch(() => {})}>
          Rejoindre le Discord
        </button>
      </div>
    </>
  );
}
