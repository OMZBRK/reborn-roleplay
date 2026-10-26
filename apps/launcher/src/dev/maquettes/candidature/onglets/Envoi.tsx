// MAQUETTE L6 — onglet 6 « Envoi » (§2.6), et L7 « envoi refusé par l'API ».
//
// 1. « Il reste à compléter » : une pastille par onglet incomplet, avec le
//    nombre de manques ; un clic mène à l'onglet.
// 2. Relecture complète, repliable par onglet, groupée Partie HRP / Partie RP,
//    avec « Modifier » ; l'enregistrement se réécoute.
// 3. Règlement + consentement voix (Q2), puis « Envoyer ma candidature »,
//    grisé tant qu'il manque quelque chose.

import { useState, type ReactNode } from "react";
import { AlertTriangle, ArrowRight, Check, ChevronDown, CircleCheck, Play, Send } from "lucide-react";
import { DiscordGlyph } from "../../../../components/home/FooterMega";
import { Markdown } from "../../../../components/Markdown";
import { useAllerEcran, useDossier } from "../contexte";
import {
  ONGLETS,
  longueur,
  manques,
  manquesDe,
  mots,
  nomComplet,
  nombre,
  trancheAge,
  villageDe,
  type Dossier,
  type OngletId,
} from "../donnees";
import { Case, Section } from "../ui";

type Ligne = { cle: string; libelle: string; valeur: ReactNode; vide?: boolean };

function lignes(d: Dossier, onglet: OngletId): Ligne[] {
  const t = (cle: string, libelle: string, v: string, avecMots = false): Ligne => ({
    cle,
    libelle,
    valeur: v ? (
      <>
        {avecMots ? (
          <Markdown content={v} className="mqc-md mqc-defile max-h-[360px] overflow-y-auto pr-3 [&_h2]:!mb-1 [&_h2]:!mt-4 [&_h2]:!text-[18px] [&_h2]:!font-normal [&_h2]:!tracking-wide [&_h2]:!text-gold-bright [&_h2:first-child]:!mt-0 [&_blockquote]:border-l-2 [&_blockquote]:border-gold/60 [&_blockquote]:pl-3" />
        ) : (
          <span className="block">{v}</span>
        )}
        <span className="mt-1 block text-[11px] text-foreground-muted">
          {avecMots && `${nombre(mots(v))} mots · `}
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
    case "vous":
      return [
        v("playerAge", "Âge", d.playerAge ? `${d.playerAge} ans` : null),
        v("rpYears", "Roleplay depuis", d.rpYears),
        v("narutoKnowledge", "Univers Naruto", d.narutoKnowledge),
        t("availability", "Disponibilités", d.availability),
        { cle: "discoverySource", libelle: "Découverte", valeur: d.discoverySource || "Pas de réponse" },
      ];
    case "personnage": {
      const tr = trancheAge(Number(d.characterAge), d.characterType);
      const nom = nomComplet(d);
      return [
        v("firstName", "Prénom", d.firstName),
        v("nomFamille", "Nom de famille", d.nomFamille.trim() || null),
        { cle: "nomEnJeu", libelle: "Nom en jeu", valeur: nom || "À compléter", vide: !nom },
        v("characterType", "Type", d.characterType),
        v("characterSex", "Sexe", d.characterSex),
        v("characterAge", "Âge", d.characterAge ? `${d.characterAge} ans${tr ? ` · ${tr}` : ""}` : null),
      ];
    }
    case "village": {
      const village = villageDe(d.village);
      return [
        v("village", "Village", village ? `${village.court} · ${village.nom}` : null),
        { cle: "clan", libelle: "Clan", valeur: "Sans clan · les clans se rejoignent lors des sessions de clan" },
      ];
    }
    case "histoire":
      return [
        t("history", "Histoire", d.history, true),
        t("objectives", "Objectifs roleplay", d.objectives),
        { cle: "support", libelle: "Support visuel", valeur: d.support || "Aucun (facultatif)" },
      ];
    case "voix":
      return [
        v("micTeste", "Micro", d.micTeste ? "Testé, il fonctionne" : null),
        {
          cle: "voixDuree",
          libelle: "Enregistrement",
          vide: d.voixDuree === null,
          valeur:
            d.voixDuree === null ? (
              "À enregistrer"
            ) : (
              <span className="inline-flex items-center gap-2.5">
                <button type="button" className="mqc-play !h-7 !w-7" aria-label="Réécouter">
                  <Play size={12} fill="currentColor" className="ml-px" />
                </button>
                0:{String(d.voixDuree).padStart(2, "0")} / 1:00
              </span>
            ),
        },
      ];
    case "envoi":
      return [];
  }
}

function BlocRelecture({ onglet, ouvert, onBascule }: { onglet: OngletId; ouvert: boolean; onBascule: () => void }) {
  const { dossier: d, allerA } = useDossier();
  const o = ONGLETS.find((x) => x.id === onglet)!;
  const liste = manquesDe(d, onglet);
  const n = liste.length;
  const manquants = new Set(liste.map((m) => m.champ));
  return (
    <div className="mqc-review">
      <button type="button" className="mqc-review-head" aria-expanded={ouvert} onClick={onBascule}>
        <span className="mqc-review-title">
          {o.numero} · {o.libelle}
        </span>
        {n === 0 ? (
          <span className="mqc-review-state inline-flex items-center gap-1 text-success">
            <Check size={13} strokeWidth={3} aria-hidden /> Complet
          </span>
        ) : (
          <span className="mqc-review-state text-gold">
            {n} à compléter
          </span>
        )}
        <span
          role="link"
          tabIndex={0}
          className="mqc-link ml-3"
          onClick={(e) => {
            e.stopPropagation();
            allerA(onglet);
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.stopPropagation();
              allerA(onglet);
            }
          }}
        >
          Modifier
        </span>
        <ChevronDown
          size={16}
          className="ml-2 text-foreground-muted transition-transform"
          style={{ transform: ouvert ? "rotate(180deg)" : undefined }}
          aria-hidden
        />
      </button>
      {ouvert && (
        <div className="mqc-review-body">
          {lignes(d, onglet).map((l) => (
            <div key={l.cle} className="mqc-review-row">
              <span className="mqc-review-key">
                {l.libelle}
                {!l.vide && manquants.has(l.cle) && <span className="mt-0.5 block text-[11px] font-semibold text-[#f87171]">à compléter</span>}
              </span>
              <span className="mqc-review-val" data-vide={l.vide || undefined}>
                {l.valeur}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function BandeauApi() {
  const { allerA } = useDossier();
  const items: { ou: string; onglet: OngletId; texte: string }[] = [
    {
      ou: "5 · Voix › Enregistrement",
      onglet: "voix",
      texte: "Ton enregistrement n'est pas arrivé jusqu'au serveur (envoi interrompu). Réécoute-le, puis renvoie ta candidature.",
    },
  ];
  return (
    <div className="mqc-api" role="alert">
      <div className="mqc-api-head">
        <AlertTriangle size={18} aria-hidden />
        <div>
          <div className="mqc-api-title">Ta candidature n'est pas partie</div>
          <div className="mqc-api-lead">
            Le serveur Reborn l'a refusée{" "}
            {items.length > 1 ? `pour ${items.length} raisons` : "pour une raison"}, détaillée champ par champ. Rien
            n'est perdu : ton brouillon est enregistré.
          </div>
        </div>
      </div>
      {items.map((it) => (
        <div key={it.ou} className="mqc-api-item">
          <span className="mqc-api-where">{it.ou}</span>
          <span className="flex-1">{it.texte}</span>
          <button type="button" className="mqc-link shrink-0" onClick={() => allerA(it.onglet)}>
            Aller au champ <ArrowRight size={12} aria-hidden />
          </button>
        </div>
      ))}
      <details className="mqc-api-raw">
        <summary>Réponse du serveur (400)</summary>
        <pre>{`POST /v1/whitelist  →  400 Bad Request
{
  "statusCode": 400,
  "error": "Bad Request",
  "message": [
    "voiceFile: fichier audio absent ou illisible (audio/webm attendu)"
  ]
}`}</pre>
      </details>
    </div>
  );
}

export function OngletEnvoi() {
  const { dossier: d, maj, allerA, scenario } = useDossier();
  const aller = useAllerEcran();
  const tous = manques(d);
  const parOnglet = ONGLETS.filter((o) => o.id !== "envoi")
    .map((o) => ({ ...o, n: tous.filter((m) => m.onglet === o.id).length }))
    .filter((o) => o.n > 0);
  // Ouvert au départ : le premier onglet incomplet ; dossier complet, tout
  // est ouvert (c'est la relecture complète, §2.6) ; envoi refusé, tout est
  // replié sous le bandeau.
  const [ouverts, setOuverts] = useState<Set<OngletId>>(() =>
    scenario.erreurApi
      ? new Set<OngletId>()
      : tous.length === 0
        ? new Set<OngletId>(["vous", "personnage", "village", "histoire", "voix"])
        : new Set<OngletId>([tous[0].onglet]),
  );
  const cases = (d.reglement ? 0 : 1) + (d.consentVoix ? 0 : 1);
  const pret = tous.length === 0 && cases === 0;
  const precedent = ONGLETS[4].id;

  const bascule = (o: OngletId) =>
    setOuverts((s) => {
      const n = new Set(s);
      if (n.has(o)) n.delete(o);
      else n.add(o);
      return n;
    });

  return (
    <>
      <Section titre="Relecture et envoi" chapo="Une dernière relecture : ce que tu envoies est ce que l'équipe whitelist lira." />

      {scenario.erreurApi && <BandeauApi />}

      {parOnglet.length > 0 ? (
        <div className="mqc-card mb-8">
          <div className="text-[13px] font-medium text-foreground">Il reste à compléter :</div>
          <div className="mqc-todo">
            {parOnglet.map((o) => (
              <button key={o.id} type="button" className="mqc-todo-chip" onClick={() => allerA(o.id)}>
                {o.libelle}
                <span className="mqc-tab-pill">{o.n}</span>
                <ArrowRight size={13} aria-hidden />
              </button>
            ))}
          </div>
        </div>
      ) : scenario.erreurApi ? null : (
        <div className="mqc-note mb-8">
          <CircleCheck size={15} aria-hidden style={{ color: "var(--color-success)" }} />
          <span>Tous les onglets sont complets. Relis une dernière fois, coche les deux cases et envoie.</span>
        </div>
      )}

      <div className="mqc-part-label">Partie HRP · toi</div>
      <BlocRelecture onglet="vous" ouvert={ouverts.has("vous")} onBascule={() => bascule("vous")} />
      <div className="mqc-part-label">Partie RP · ton personnage</div>
      {(["personnage", "village", "histoire", "voix"] as const).map((o) => (
        <BlocRelecture key={o} onglet={o} ouvert={ouverts.has(o)} onBascule={() => bascule(o)} />
      ))}

      <div className="mt-9 flex flex-col gap-4">
        <Case coche={d.reglement} onChange={(v) => maj("reglement", v)}>
          J'ai lu le{" "}
          <button type="button" className="mqc-link !text-[13px]">
            règlement
          </button>{" "}
          et <strong>ces réponses sont de moi</strong>.
        </Case>
        <Case coche={d.consentVoix} onChange={(v) => maj("consentVoix", v)}>
          <strong>J'accepte que ma voix soit enregistrée pour ma candidature.</strong>
          <span className="mt-1 block text-[12px] leading-relaxed text-foreground-muted">
            Elle sert à présenter ton personnage. Seule l'équipe whitelist l'écoute. Elle est effacée 30 jours après la
            décision finale, ou dès que tu retires ta candidature.
          </span>
        </Case>
      </div>

      <div className="mqc-actions">
        <button type="button" className="mqc-prev" onClick={() => allerA(precedent)}>
          Étape précédente
        </button>
        <button
          type="button"
          className="wl-btn-primary glow wl-btn-cta-big mqc-cta"
          disabled={!pret}
          onClick={() => aller("L8")}
        >
          <Send size={16} aria-hidden /> {scenario.erreurApi ? "Renvoyer ma candidature" : "Envoyer ma candidature"}
        </button>
        <span className="mqc-actions-note">
          {pret
            ? "Tu suivras la relecture ici même, et sur Discord."
            : [
                tous.length > 0 && `${tous.length} champ${tous.length > 1 ? "s" : ""} à compléter`,
                cases > 0 && `${cases} case${cases > 1 ? "s" : ""} à cocher`,
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
        <button type="button" className="mqc-btn">
          Rejoindre le Discord
        </button>
      </div>
    </>
  );
}
