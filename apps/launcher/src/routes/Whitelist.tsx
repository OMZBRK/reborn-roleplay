import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { RefreshCw, WifiOff } from "lucide-react";
import {
  lireCandidature,
  lireDouane,
  lireOptions,
  type CandidatureOptions,
} from "../lib/candidature";
import { useAuthStore } from "../stores/auth-store";
import { useWhitelistStore } from "../stores/whitelist-store";
import { SyncIndicator, useChatFeed } from "../components/chat/ChatThread";
import { BarreCandidature } from "../components/whitelist/Barre";
import { DossierCandidature } from "../components/whitelist/Dossier";
import { DouaneSeances } from "../components/whitelist/Douane";
import { FormulaireCandidature } from "../components/whitelist/Formulaire";
import { douaneOuverte, OngletsSuivi, SuiviCandidature, type VueSuivi } from "../components/whitelist/Suivi";
import { ONGLETS, OPTIONS_DEFAUT, type OngletId } from "../components/whitelist/regles";
import { EnTete } from "../components/whitelist/ui";

// Onglet Whitelist : la candidature v2 et la douane
// (docs/gameplay/CANDIDATURE_ET_DOUANE.md §1.2, §2, §3.3). L'écran suit la
// PHASE calculée par l'API (GET /whitelist/me › suivi.phase), jamais le rôle :
//   - BROUILLON : le formulaire à six onglets ;
//   - ECRIT_A_REVOIR : le suivi, puis le formulaire avec les seules parties
//     rouvertes (« Corriger ma candidature ») ;
//   - REFUSE : le suivi avec le compte à rebours, puis une nouvelle
//     candidature complète qui repart du dossier précédent ;
//   - les autres : suivi (frise, état, fil), « Mon dossier », « Douane ».
// La phase est relue toutes les 15 s (décision du staff, écrit accepté) et
// les séances toutes les 30 s quand elles comptent.
//
// Liens directs : ?onglet=vous|personnage|village|histoire|voix|envoi ouvre
// le formulaire sur cet onglet ; ?vue=dossier|douane ouvre cette vue du suivi.

/** Relecture du suivi. */
const SUIVI_POLL_MS = 15_000;
const DOUANE_POLL_MS = 30_000;

type Edition = null | "revoir" | "refaire";

export function Whitelist() {
  const [params] = useSearchParams();
  const setPhase = useWhitelistStore((s) => s.setPhase);
  const compte = useAuthStore((s) => s.user?.id ?? null);
  const aUneCopie = useWhitelistStore((s) => !!s.copieLocale && s.copieLocale.compte === compte);

  // Listes fermées et bornes de l'API ; les mêmes valeurs en repli tant
  // qu'elles ne sont pas arrivées (le formulaire reste utilisable hors ligne).
  const [options, setOptions] = useState<CandidatureOptions>(OPTIONS_DEFAUT);
  useEffect(() => {
    let annule = false;
    lireOptions()
      .then((o) => {
        if (!annule && o && o.formVersion === 2 && Array.isArray(o.villages) && o.bornes) setOptions(o);
      })
      .catch((e: unknown) => console.warn("[candidature] options indisponibles, valeurs par défaut :", e));
    return () => {
      annule = true;
    };
  }, []);

  const me = useChatFeed(() => lireCandidature(), {
    intervalMs: SUIVI_POLL_MS,
    context: "État de ta candidature non synchronisé",
  });
  const phase = me.data?.suivi.phase ?? null;
  useEffect(() => {
    if (phase) setPhase(phase);
  }, [phase, setPhase]);

  const avecDouane = phase !== null && douaneOuverte(phase);
  const douane = useChatFeed(() => lireDouane(), {
    enabled: avecDouane,
    intervalMs: DOUANE_POLL_MS,
    context: "Séances de douane non synchronisées",
  });

  const [edition, setEdition] = useState<Edition>(null);
  const [vue, setVue] = useState<VueSuivi>(() => {
    const v = params.get("vue");
    return v === "douane" || v === "dossier" ? v : "suivi";
  });
  // Onglet demandé par l'adresse, lu à l'ouverture seulement.
  const [ongletInitial] = useState<OngletId | undefined>(
    () => ONGLETS.find((o) => o.id === params.get("onglet"))?.id,
  );
  const [horsLigne, setHorsLigne] = useState(false);

  const { refresh: relireSuivi } = me;
  const { refresh: relireDouane } = douane;
  const toutRelire = useCallback(() => {
    relireSuivi();
    relireDouane();
  }, [relireSuivi, relireDouane]);

  // La phase a changé (décision, envoi depuis un autre PC) : on sort de l'édition.
  useEffect(() => {
    if (edition === "revoir" && phase !== "ECRIT_A_REVOIR") setEdition(null);
    if (edition === "refaire" && phase !== "REFUSE") setEdition(null);
  }, [edition, phase]);

  const apresEnvoi = useCallback(() => {
    setEdition(null);
    setVue("suivi");
    setHorsLigne(false);
    relireSuivi();
  }, [relireSuivi]);

  // ── Premier chargement ─────────────────────────────────────
  if (!me.data) {
    if (horsLigne) {
      // Clé propre : quand le suivi revient, le formulaire de la phase se
      // remonte et rejoue l'ouverture (lecture du brouillon serveur) au lieu
      // de pousser la copie de ce PC par-dessus.
      return (
        <FormulaireCandidature
          key="hors-ligne"
          mode="nouvelle"
          application={null}
          partiesRouvertes={[]}
          options={options}
          ongletInitial={ongletInitial}
          onEnvoye={apresEnvoi}
          onPhaseChangee={relireSuivi}
        />
      );
    }
    return (
      <BarreCandidature onglets={null}>
        <EnTete
          surtitre="Candidature whitelist"
          titre="Ma candidature"
          chapo={me.loading ? "Chargement de ta candidature…" : "Ta candidature n'a pas pu être chargée."}
        />
        {me.loading ? (
          <div className="cand-chargement" role="status">
            <RefreshCw size={14} className="animate-spin" aria-hidden /> Chargement…
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <SyncIndicator variant="block" sync={me.sync} onRetry={me.refresh} />
            {aUneCopie && (
              <div className="cand-card">
                <div className="flex items-center gap-2 text-[13px] font-semibold text-foreground">
                  <WifiOff size={14} aria-hidden /> Ton brouillon est gardé sur ce PC
                </div>
                <p className="mb-0 mt-1 text-[12.5px] leading-relaxed text-foreground-muted">
                  Tu peux continuer à le remplir hors ligne : il partira sur le serveur dès que la connexion revient.
                  L'envoi, lui, attend la connexion.
                </p>
                <button type="button" className="cand-btn mt-3" onClick={() => setHorsLigne(true)}>
                  Continuer mon brouillon
                </button>
              </div>
            )}
          </div>
        )}
      </BarreCandidature>
    );
  }

  const { application, suivi } = me.data;

  // ── Formulaire ─────────────────────────────────────────────
  if (suivi.phase === "BROUILLON") {
    return (
      <FormulaireCandidature
        key="nouvelle"
        mode="nouvelle"
        application={null}
        partiesRouvertes={[]}
        options={options}
        ongletInitial={ongletInitial}
        onEnvoye={apresEnvoi}
        onPhaseChangee={relireSuivi}
      />
    );
  }
  if (edition === "revoir" && suivi.phase === "ECRIT_A_REVOIR" && application) {
    return (
      <FormulaireCandidature
        key="revoir"
        mode="revoir"
        application={application}
        partiesRouvertes={suivi.partiesRouvertes}
        options={options}
        onEnvoye={apresEnvoi}
        onRetour={() => setEdition(null)}
        onPhaseChangee={relireSuivi}
      />
    );
  }
  if (edition === "refaire" && suivi.phase === "REFUSE") {
    return (
      <FormulaireCandidature
        key="refaire"
        mode="nouvelle"
        application={application}
        partiesRouvertes={[]}
        options={options}
        onEnvoye={apresEnvoi}
        onRetour={() => setEdition(null)}
        onPhaseChangee={relireSuivi}
      />
    );
  }

  // ── Suivi, dossier, douane ─────────────────────────────────
  const vueEffective: VueSuivi =
    vue === "douane" && avecDouane ? "douane" : vue === "dossier" && application ? "dossier" : "suivi";

  return (
    <BarreCandidature
      cle={vueEffective}
      onglets={
        <OngletsSuivi
          actif={vueEffective}
          douaneOuverte={avecDouane}
          avecDossier={!!application}
          onChoisir={setVue}
        />
      }
    >
      {vueEffective !== "douane" && (
        <SyncIndicator variant="block" className="mb-4" sync={me.sync} onRetry={me.refresh} />
      )}
      {vueEffective === "dossier" && application ? (
        <DossierCandidature application={application} options={options} />
      ) : vueEffective === "douane" ? (
        <DouaneSeances
          douane={douane.data}
          chargement={douane.loading}
          sync={douane.sync}
          onActualiser={douane.refresh}
          onChange={toutRelire}
        />
      ) : (
        <SuiviCandidature
          me={me.data}
          douane={avecDouane ? douane.data : null}
          onCorriger={() => setEdition("revoir")}
          onRefaire={() => setEdition("refaire")}
          onDouane={() => setVue("douane")}
          onRetire={() => {
            useWhitelistStore.getState().oublierCopieLocale();
            setVue("suivi");
            toutRelire();
          }}
        />
      )}
    </BarreCandidature>
  );
}
