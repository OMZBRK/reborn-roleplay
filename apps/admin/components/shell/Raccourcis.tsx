'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { useDroits } from '@/lib/droits';
import { sectionsPermises } from '@/lib/navigation';
import { preferences } from '@/lib/preferences';
import { dansUnChamp } from '@/lib/raccourcis';
import { Dialog, fenetreOuverte } from '@/components/ui/Dialog';
import { Kbd } from '@/components/ui/Kbd';

/**
 * Raccourcis valables partout (PANEL_V2 §11) : Ctrl K ouvre la palette,
 * « / » va au champ de recherche de la page, « G » puis une lettre change de
 * section, « ? » ouvre l'aide. Inactifs dans un champ (sauf Ctrl K), quand
 * une fenêtre est ouverte ou quand le staff les a coupés.
 */
export function RaccourcisGlobaux({ onPalette }: { onPalette: () => void }) {
  const router = useRouter();
  const { capacites } = useDroits();
  const [aide, setAide] = useState(false);
  const attenteG = useRef<number | null>(null);
  const palette = useRef(onPalette);
  palette.current = onPalette;
  const caps = useRef(capacites);
  caps.current = capacites;

  useEffect(() => {
    function surTouche(e: KeyboardEvent) {
      if (e.defaultPrevented || e.isComposing) return;
      // Ctrl K : même dans un champ, même raccourcis coupés (c'est la recherche).
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        palette.current();
        return;
      }
      if (!preferences().raccourcis || fenetreOuverte()) return;
      if (e.ctrlKey || e.metaKey || e.altKey || dansUnChamp(e.target)) return;

      if (attenteG.current !== null) {
        window.clearTimeout(attenteG.current);
        attenteG.current = null;
        const s = sectionsPermises(caps.current).find(
          (x) => x.touche === e.key.toLowerCase(),
        );
        if (s) {
          e.preventDefault();
          e.stopPropagation();
          router.push(s.permis[0]!.href);
        }
        return;
      }
      if (e.key === 'g' || e.key === 'G') {
        attenteG.current = window.setTimeout(() => {
          attenteG.current = null;
        }, 1200);
        return;
      }
      if (e.key === '/') {
        const champ = document.querySelector<HTMLInputElement>(
          'main input[type="search"], main input[data-recherche]',
        );
        if (champ) {
          e.preventDefault();
          champ.focus();
        }
        return;
      }
      if (e.key === '?') {
        e.preventDefault();
        setAide(true);
      }
    }
    // Phase de capture : la séquence « G puis lettre » passe avant les
    // raccourcis de la page (« P » prend une ligne, « G P » va à Pilotage).
    window.addEventListener('keydown', surTouche, true);
    return () => window.removeEventListener('keydown', surTouche, true);
  }, [router]);

  return <AideRaccourcis ouvert={aide} onFermer={() => setAide(false)} />;
}

const AIDE: Array<{ ou: string; lignes: Array<[string[], string]> }> = [
  {
    ou: 'Partout',
    lignes: [
      [['Ctrl', 'K'], 'Rechercher un joueur, un personnage, une page'],
      [['/'], 'Aller au champ de recherche de la page'],
      [['G', 'A · J · C · M · W · P'], 'À traiter, Joueurs, Candidatures, Modération, Contenu, Pilotage'],
      [['Échap'], 'Fermer la fenêtre ou le tiroir'],
      [['?'], 'Cette aide'],
    ],
  },
  {
    ou: 'Listes',
    lignes: [
      [['↓', '↑'], 'Ligne suivante, précédente'],
      [['Entrée'], 'Ouvrir'],
      [['P'], 'Prendre (À traiter)'],
    ],
  },
  {
    ou: 'Fiche candidature',
    lignes: [
      [['1 à 4'], 'HRP, Personnage, Histoire, Voix'],
      [['Espace'], 'Lire ou mettre en pause la voix'],
      [['V'], 'Valider (« Valider l’écrit » si HRP et RP attendent)'],
      [['R'], 'À revoir sur la partie en cours de lecture'],
      [['N', 'Maj N'], 'Dossier suivant, précédent'],
      [['P'], 'Prendre'],
      [['E'], 'Échanges'],
      [['O'], 'Note privée'],
    ],
  },
  {
    ou: 'Fiche ticket',
    lignes: [
      [['R'], 'Répondre'],
      [['/'], 'Réponses types (en début de réponse)'],
      [['Ctrl', 'Entrée'], 'Envoyer'],
      [['Ctrl', 'Maj', 'Entrée'], 'Envoyer et résoudre'],
    ],
  },
];

export function AideRaccourcis({ ouvert, onFermer }: { ouvert: boolean; onFermer: () => void }) {
  return (
    <Dialog
      ouvert={ouvert}
      onFermer={onFermer}
      large
      titre="Raccourcis clavier"
      description="Inactifs dans un champ de saisie, sauf avec Ctrl. Refuser n’a jamais de touche seule. Désactivables dans Réglages › Préférences."
    >
      <div className="grid gap-5 sm:grid-cols-2">
        {AIDE.map((bloc) => (
          <div key={bloc.ou}>
            <div className="eyebrow mb-2">{bloc.ou}</div>
            <ul className="flex flex-col gap-2">
              {bloc.lignes.map(([touches, effet]) => (
                <li key={effet} className="flex items-start gap-3 text-[13px]">
                  <span className="flex min-w-[118px] flex-wrap gap-1">
                    {touches.map((t) => (
                      <Kbd key={t}>{t}</Kbd>
                    ))}
                  </span>
                  <span className="text-fg-2">{effet}</span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </Dialog>
  );
}
