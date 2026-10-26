// MAQUETTE L4 — onglet 4 « Histoire » (partie RP), §2.3 et Q10.
//
// Barre Markdown légère comme Ketto (G · I · S · Titre · Liste · Citation),
// qui insère la syntaxe autour de la sélection. Compteur mots + caractères
// (« 312 mots · 1 840 / 12 000 »), minimum 150, maximum 12 000 ; la zone
// grandit avec le texte, rien n'est jamais coupé. En plus de Ketto : un
// aperçu de la mise en forme, tel que le staff le lira. Puis les objectifs
// (400 à 8 000) et le support visuel, comme dans la fiche (§2.3, onglet 4).

import { useId, useRef, useState } from "react";
import { Eye, PenLine } from "lucide-react";
import { Markdown } from "../../../../components/Markdown";
import { useDossier } from "../contexte";
import { BORNES, longueur, messageSupport, messageTexte } from "../donnees";
import { Champ, ChampTexte, Compteur, Facultatif, Section, ZoneTexte } from "../ui";

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
  const { dossier: d, maj, scenario } = useDossier();
  const zone = useRef<HTMLTextAreaElement>(null);
  const [apercu, setApercu] = useState(false);
  const [quitte, setQuitte] = useState(false);
  const idSupport = useId();
  const [supportQuitte, setSupportQuitte] = useState(false);

  const message = messageTexte(d.history, BORNES.history);
  const trop = longueur(d.history) > BORNES.history.max;
  const visible = message !== null && (quitte || scenario.montrerErreurs || trop);
  const erreurSupport = messageSupport(d.support);

  function appliquer(o: Outil) {
    const el = zone.current;
    if (!el) return;
    const { selectionStart: a, selectionEnd: b, value } = el;
    let suivant: string;
    let curseur: [number, number];
    if (o.ligne) {
      const debut = value.lastIndexOf("\n", a - 1) + 1;
      suivant = value.slice(0, debut) + o.avant + value.slice(debut);
      curseur = [a + o.avant.length, b + o.avant.length];
    } else {
      const sel = value.slice(a, b) || "texte";
      suivant = value.slice(0, a) + o.avant + sel + (o.apres ?? "") + value.slice(b);
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
        droite={<Compteur valeur={d.history} bornes={BORNES.history} avecMots />}
        aide="D'où vient ton personnage, qui l'a formé, ce qu'il cherche. Les mots sont comptés à titre indicatif."
        erreur={visible ? message : null}
      >
        <div className="mqc-editor" data-invalid={visible || undefined}>
          <div className="mqc-toolbar" role="toolbar" aria-label="Mise en forme">
            {OUTILS.map((o) => (
              <button
                key={o.id}
                type="button"
                className={`mqc-tool ${o.style ?? ""}`}
                title={o.titre}
                aria-label={o.titre}
                disabled={apercu}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => appliquer(o)}
              >
                {o.libelle}
              </button>
            ))}
            <span className="mqc-tool-sep" aria-hidden />
            {OUTILS_LIGNE.map((o) => (
              <button
                key={o.id}
                type="button"
                className="mqc-tool"
                title={o.titre}
                disabled={apercu}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => appliquer(o)}
              >
                {o.libelle}
              </button>
            ))}
            <span className="flex-1" />
            <button type="button" className="mqc-tool gap-1.5" aria-pressed={!apercu} onClick={() => setApercu(false)}>
              <PenLine size={13} aria-hidden /> Écrire
            </button>
            <button type="button" className="mqc-tool gap-1.5" aria-pressed={apercu} onClick={() => setApercu(true)}>
              <Eye size={13} aria-hidden /> Aperçu
            </button>
          </div>
          {apercu ? (
            <div className="mqc-editor-preview">
              <Markdown content={d.history} className="[&_blockquote]:border-l-2 [&_blockquote]:border-gold/60 [&_blockquote]:pl-3 [&_blockquote_p]:italic [&_del]:opacity-70" />
            </div>
          ) : (
            <ZoneTexte
              zoneRef={zone}
              valeur={d.history}
              onChange={(v) => maj("history", v)}
              onBlur={() => setQuitte(true)}
              minLignes={12}
              invalide={visible}
              placeholder="Raconte d'où vient ton personnage…"
            />
          )}
          <div className="mqc-editor-foot">
            <span>Mise en forme Markdown · le staff lit la version mise en forme</span>
            <span>La zone s'agrandit avec ton texte</span>
          </div>
        </div>
      </Champ>

      <ChampTexte
        label="Objectifs roleplay"
        valeur={d.objectives}
        onChange={(v) => maj("objectives", v)}
        bornes={BORNES.objectives}
        message={messageTexte(d.objectives, BORNES.objectives)}
        aide="Ce qu'il veut devenir, ce que tu veux lui faire vivre : court, moyen et long terme."
      />

      <Champ
        label="Support visuel"
        htmlFor={idSupport}
        droite={<Facultatif />}
        aide="Un lien YouTube, Canva ou une image qui montre ton personnage."
        erreur={erreurSupport && (supportQuitte || scenario.montrerErreurs) ? erreurSupport : null}
      >
        <input
          id={idSupport}
          className="mqc-input"
          placeholder="https://"
          value={d.support}
          aria-invalid={(erreurSupport !== null && supportQuitte) || undefined}
          onChange={(e) => maj("support", e.target.value)}
          onBlur={() => setSupportQuitte(true)}
        />
      </Champ>
    </>
  );
}
