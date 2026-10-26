// Onglet 4 « Histoire » (partie RP), §2.3 et Q10, maquette L4.
//
// Barre Markdown légère comme Ketto (G · I · S · Titre · Liste · Citation),
// qui insère la syntaxe autour de la sélection. Compteur mots + caractères
// (« 312 mots · 1 840 / 12 000 »), bornes de l'API (150 à 12 000) ; la zone
// grandit avec le texte, rien n'est jamais coupé. En plus de Ketto : un
// aperçu de la mise en forme, tel que le staff le lira. Puis les objectifs
// et le support visuel.

import { useId, useRef, useState } from "react";
import { Eye, PenLine } from "lucide-react";
import { useFormulaire } from "../contexte";
import { longueur, markdownSur, messageSupport, messageTexte } from "../regles";
import { Champ, ChampTexte, Compteur, Facultatif, MarkdownSansLien, Section, ZoneTexte } from "../ui";

type Outil = { id: string; libelle: string; titre: string; style?: string; avant: string; apres?: string; ligne?: boolean };

const OUTILS: Outil[] = [
  { id: "g", libelle: "G", titre: "Gras (Ctrl+B)", style: "font-bold", avant: "**", apres: "**" },
  { id: "i", libelle: "I", titre: "Italique (Ctrl+I)", style: "italic", avant: "*", apres: "*" },
  { id: "s", libelle: "S", titre: "Barré", style: "line-through", avant: "~~", apres: "~~" },
];
const OUTILS_LIGNE: Outil[] = [
  { id: "titre", libelle: "Titre", titre: "Titre de partie", avant: "## ", ligne: true },
  { id: "liste", libelle: "Liste", titre: "Liste à puces", avant: "- ", ligne: true },
  { id: "citation", libelle: "Citation", titre: "Citation", avant: "> ", ligne: true },
];

export function OngletHistoire() {
  const { f, maj, options, montrerErreurs, erreursApi } = useFormulaire();
  const b = options.bornes;
  const zone = useRef<HTMLTextAreaElement>(null);
  const [apercu, setApercu] = useState(false);
  const [quitte, setQuitte] = useState(false);
  const idSupport = useId();
  const [supportQuitte, setSupportQuitte] = useState(false);

  const message = messageTexte(f.history, b.history);
  const trop = longueur(f.history) > b.history.max;
  const visible = message !== null && (quitte || montrerErreurs || trop);
  const erreurHistoire = erreursApi.history ?? (visible ? message : null);
  const erreurSupport = messageSupport(f.support, b.support);
  const supportVisible = erreurSupport !== null && (supportQuitte || montrerErreurs);

  function appliquer(o: Outil) {
    const el = zone.current;
    if (!el) return;
    const { selectionStart: a, selectionEnd: z, value } = el;
    let suivant: string;
    let curseur: [number, number];
    if (o.ligne) {
      const debut = value.lastIndexOf("\n", a - 1) + 1;
      suivant = value.slice(0, debut) + o.avant + value.slice(debut);
      curseur = [a + o.avant.length, z + o.avant.length];
    } else {
      const sel = value.slice(a, z) || "texte";
      suivant = value.slice(0, a) + o.avant + sel + (o.apres ?? "") + value.slice(z);
      curseur = [a + o.avant.length, a + o.avant.length + sel.length];
    }
    maj("history", suivant);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(curseur[0], curseur[1]);
    });
  }

  return (
    <>
      <Section
        titre="Son histoire"
        chapo="Le cœur de ta candidature. Écris comme tu veux : la barre met en forme, l'aperçu montre ce que le staff lira."
      />

      <Champ
        label="Histoire du personnage"
        ancre="history"
        droite={<Compteur valeur={f.history} bornes={b.history} avecMots />}
        aide="D'où vient ton personnage, qui l'a formé, ce qu'il cherche. Les mots sont comptés à titre indicatif."
        erreur={erreurHistoire}
      >
        <div className="cand-editor" data-invalid={erreurHistoire !== null || undefined}>
          <div className="cand-toolbar" role="toolbar" aria-label="Mise en forme">
            {OUTILS.map((o) => (
              <button
                key={o.id}
                type="button"
                className={`cand-tool ${o.style ?? ""}`}
                title={o.titre}
                aria-label={o.titre}
                disabled={apercu}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => appliquer(o)}
              >
                {o.libelle}
              </button>
            ))}
            <span className="cand-tool-sep" aria-hidden />
            {OUTILS_LIGNE.map((o) => (
              <button
                key={o.id}
                type="button"
                className="cand-tool"
                title={o.titre}
                disabled={apercu}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => appliquer(o)}
              >
                {o.libelle}
              </button>
            ))}
            <span className="flex-1" />
            <button type="button" className="cand-tool gap-1.5" aria-pressed={!apercu} onClick={() => setApercu(false)}>
              <PenLine size={13} aria-hidden /> Écrire
            </button>
            <button type="button" className="cand-tool gap-1.5" aria-pressed={apercu} onClick={() => setApercu(true)}>
              <Eye size={13} aria-hidden /> Aperçu
            </button>
          </div>
          {apercu ? (
            <div className="cand-editor-preview">
              {f.history.trim() ? (
                // Un lien deviné par le Markdown (adresse nue) n'ouvre rien ici.
                <MarkdownSansLien
                  content={markdownSur(f.history)}
                  className="[&_blockquote]:border-l-2 [&_blockquote]:border-gold/60 [&_blockquote]:pl-3 [&_blockquote_p]:italic [&_del]:opacity-70"
                />
              ) : (
                <p className="m-0 text-[13px] text-foreground-muted">Rien à afficher pour l'instant.</p>
              )}
            </div>
          ) : (
            <ZoneTexte
              zoneRef={zone}
              valeur={f.history}
              onChange={(v) => maj("history", v)}
              onBlur={() => setQuitte(true)}
              minLignes={12}
              invalide={erreurHistoire !== null}
              placeholder="Raconte d'où vient ton personnage…"
            />
          )}
          <div className="cand-editor-foot">
            <span>Mise en forme Markdown · le staff lit la version mise en forme</span>
            <span>La zone s'agrandit avec ton texte</span>
          </div>
        </div>
      </Champ>

      <ChampTexte
        label="Objectifs roleplay"
        ancre="objectives"
        valeur={f.objectives}
        onChange={(v) => maj("objectives", v)}
        bornes={b.objectives}
        message={messageTexte(f.objectives, b.objectives)}
        forcer={montrerErreurs}
        erreurApi={erreursApi.objectives}
        aide="Ce qu'il veut devenir, ce que tu veux lui faire vivre : court, moyen et long terme."
      />

      <Champ
        label="Support visuel"
        htmlFor={idSupport}
        ancre="support"
        droite={<Facultatif />}
        aide="Un lien YouTube, Canva ou une image qui montre ton personnage."
        erreur={erreursApi.support ?? (supportVisible ? erreurSupport : null)}
      >
        <input
          id={idSupport}
          className="cand-input"
          placeholder="https://"
          value={f.support}
          autoComplete="off"
          spellCheck={false}
          aria-invalid={supportVisible || !!erreursApi.support || undefined}
          onChange={(e) => maj("support", e.target.value)}
          onBlur={() => setSupportQuitte(true)}
        />
      </Champ>
    </>
  );
}
