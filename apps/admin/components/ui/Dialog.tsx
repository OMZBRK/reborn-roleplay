'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from 'react';
import { createPortal } from 'react-dom';
import { Button } from './Button';
import { Field, Input } from './Field';
import { Icone } from './Icone';

/**
 * Fenêtres du panel (PANEL_V2 §5.4, §10) : un voile, rayon 12, Échap ferme,
 * focus gardé dans la fenêtre puis rendu à l'élément d'origine,
 * `role="dialog"` et `aria-modal`. Remplacent les `confirm` / `prompt` du
 * navigateur (`useConfirm`). Une suppression définitive demande de retaper
 * le nom.
 */

// Pile des fenêtres ouvertes : seule la plus haute reçoit Échap et Tab.
const pile: symbol[] = [];

const FOCUSABLES =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

function usePiegeFocus(
  ref: RefObject<HTMLElement | null>,
  ouvert: boolean,
  onFermer: () => void,
) {
  const fermer = useRef(onFermer);
  fermer.current = onFermer;

  useEffect(() => {
    if (!ouvert) return;
    const moi = Symbol('fenetre');
    pile.push(moi);
    const precedent = document.activeElement as HTMLElement | null;
    const el = ref.current;
    const liste = () =>
      el ? Array.from(el.querySelectorAll<HTMLElement>(FOCUSABLES)) : [];
    const cible =
      el?.querySelector<HTMLElement>('[data-autofocus]') ?? liste()[0] ?? el;
    cible?.focus();
    const debordement = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function surTouche(e: KeyboardEvent) {
      if (pile[pile.length - 1] !== moi) return;
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        fermer.current();
        return;
      }
      if (e.key === 'Tab' && el) {
        const f = liste();
        if (f.length === 0) {
          e.preventDefault();
          return;
        }
        const premier = f[0]!;
        const dernier = f[f.length - 1]!;
        if (e.shiftKey && document.activeElement === premier) {
          e.preventDefault();
          dernier.focus();
        } else if (!e.shiftKey && document.activeElement === dernier) {
          e.preventDefault();
          premier.focus();
        } else if (!el.contains(document.activeElement)) {
          e.preventDefault();
          premier.focus();
        }
      }
    }
    document.addEventListener('keydown', surTouche, true);
    return () => {
      document.removeEventListener('keydown', surTouche, true);
      const i = pile.indexOf(moi);
      if (i >= 0) pile.splice(i, 1);
      if (pile.length === 0) document.body.style.overflow = debordement;
      precedent?.focus?.();
    };
  }, [ouvert, ref]);
}

/** Vrai si une fenêtre ou un tiroir est ouvert (les raccourcis de page se taisent). */
export function fenetreOuverte(): boolean {
  return pile.length > 0;
}

function EnTete({
  idTitre,
  idDesc,
  titre,
  description,
  onFermer,
}: {
  idTitre: string;
  idDesc: string;
  titre: ReactNode;
  description?: ReactNode;
  onFermer: () => void;
}) {
  return (
    <>
      <div className="min-w-0">
        <h2 id={idTitre} className="dlg-t">
          {titre}
        </h2>
        {description && (
          <div id={idDesc} className="dlg-d">
            {description}
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={onFermer}
        aria-label="Fermer"
        title="Fermer · Échap"
        className="btn btn-icon btn-ghost"
      >
        <Icone nom="x" taille={16} />
      </button>
    </>
  );
}

export function Dialog({
  ouvert,
  onFermer,
  titre,
  description,
  children,
  pied,
  large = false,
  alerte = false,
}: {
  ouvert: boolean;
  onFermer: () => void;
  titre: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  pied?: ReactNode;
  large?: boolean;
  /** `role="alertdialog"` (confirmation d'un geste à risque). */
  alerte?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const idTitre = useId();
  const idDesc = useId();
  usePiegeFocus(ref, ouvert, onFermer);
  if (!ouvert || typeof document === 'undefined') return null;
  return createPortal(
    <>
      <div className="veil" onClick={onFermer} aria-hidden />
      <div
        ref={ref}
        role={alerte ? 'alertdialog' : 'dialog'}
        aria-modal="true"
        aria-labelledby={idTitre}
        aria-describedby={description ? idDesc : undefined}
        className={`dialog ${large ? 'dialog-lg' : ''}`}
        tabIndex={-1}
      >
        <div className="dlg-h">
          <EnTete
            idTitre={idTitre}
            idDesc={idDesc}
            titre={titre}
            description={description}
            onFermer={onFermer}
          />
        </div>
        {children && <div className="dlg-b">{children}</div>}
        {pied && <div className="dlg-f">{pied}</div>}
      </div>
    </>,
    document.body,
  );
}

/** Tiroir pleine hauteur à droite (échanges, planifier une séance, détail). */
export function Drawer({
  ouvert,
  onFermer,
  titre,
  description,
  children,
  pied,
  large = false,
  corpsBrut = false,
}: {
  ouvert: boolean;
  onFermer: () => void;
  titre: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  pied?: ReactNode;
  large?: boolean;
  /** Contenu sans marge ni défilement propre (conversation pleine hauteur). */
  corpsBrut?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const idTitre = useId();
  const idDesc = useId();
  usePiegeFocus(ref, ouvert, onFermer);
  if (!ouvert || typeof document === 'undefined') return null;
  return createPortal(
    <>
      <div className="veil" onClick={onFermer} aria-hidden />
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitre}
        aria-describedby={description ? idDesc : undefined}
        className={`drawer ${large ? 'drawer-lg' : ''}`}
        tabIndex={-1}
      >
        <div className="drawer-h">
          <EnTete
            idTitre={idTitre}
            idDesc={idDesc}
            titre={titre}
            description={description}
            onFermer={onFermer}
          />
        </div>
        {corpsBrut ? (
          <div className="flex min-h-0 flex-1 flex-col">{children}</div>
        ) : (
          <div className="drawer-b">{children}</div>
        )}
        {pied && <div className="drawer-f">{pied}</div>}
      </div>
    </>,
    document.body,
  );
}

// ──────────────────────────── useConfirm ────────────────────────────

export interface OptionsConfirmation {
  titre: ReactNode;
  message?: ReactNode;
  /** Verbe précis du bouton (« Supprimer », « Déployer sur … »), jamais « OK ». */
  confirmer: string;
  annuler?: string;
  /** Geste destructif : bouton rouge, pas de validation par Entrée seule. */
  danger?: boolean;
  /** Texte à retaper à l'identique pour confirmer (suppression définitive). */
  retaper?: string;
  /** Contenu supplémentaire (récapitulatif). */
  contenu?: ReactNode;
  large?: boolean;
}

export interface OptionsSaisie {
  titre: ReactNode;
  message?: ReactNode;
  label: string;
  valeurInitiale?: string;
  placeholder?: string;
  aide?: ReactNode;
  confirmer: string;
  /** Renvoie un message d'erreur, ou null si la saisie est valable. */
  valider?: (v: string) => string | null;
}

interface ContexteConfirmation {
  confirmer: (o: OptionsConfirmation) => Promise<boolean>;
  demander: (o: OptionsSaisie) => Promise<string | null>;
}

const Contexte = createContext<ContexteConfirmation | null>(null);

type Demande =
  | { genre: 'confirmation'; options: OptionsConfirmation; resoudre: (v: boolean) => void }
  | { genre: 'saisie'; options: OptionsSaisie; resoudre: (v: string | null) => void };

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [demande, setDemande] = useState<Demande | null>(null);

  const confirmer = useCallback(
    (options: OptionsConfirmation) =>
      new Promise<boolean>((resoudre) =>
        setDemande({ genre: 'confirmation', options, resoudre }),
      ),
    [],
  );
  const demander = useCallback(
    (options: OptionsSaisie) =>
      new Promise<string | null>((resoudre) =>
        setDemande({ genre: 'saisie', options, resoudre }),
      ),
    [],
  );

  return (
    <Contexte.Provider value={{ confirmer, demander }}>
      {children}
      {demande?.genre === 'confirmation' && (
        <FenetreConfirmation
          options={demande.options}
          onFin={(v) => {
            demande.resoudre(v);
            setDemande(null);
          }}
        />
      )}
      {demande?.genre === 'saisie' && (
        <FenetreSaisie
          options={demande.options}
          onFin={(v) => {
            demande.resoudre(v);
            setDemande(null);
          }}
        />
      )}
    </Contexte.Provider>
  );
}

/** `confirmer(...)` et `demander(...)` à la place de `confirm` / `prompt`. */
export function useConfirm(): ContexteConfirmation {
  const c = useContext(Contexte);
  if (!c) throw new Error('useConfirm hors de ConfirmProvider');
  return c;
}

function FenetreConfirmation({
  options: o,
  onFin,
}: {
  options: OptionsConfirmation;
  onFin: (v: boolean) => void;
}) {
  const [saisie, setSaisie] = useState('');
  const pret = !o.retaper || saisie === o.retaper;
  return (
    <Dialog
      ouvert
      alerte={o.danger}
      onFermer={() => onFin(false)}
      titre={o.titre}
      description={o.message}
      large={o.large}
      pied={
        <>
          <Button variante="ghost" onClick={() => onFin(false)}>
            {o.annuler ?? 'Annuler'}
          </Button>
          <Button
            variante={o.danger ? 'danger-solid' : 'primary'}
            disabled={!pret}
            onClick={() => onFin(true)}
            raccourci={o.danger || o.retaper ? undefined : 'Entrée'}
            data-autofocus={!o.retaper && !o.danger ? true : undefined}
          >
            {o.confirmer}
          </Button>
        </>
      }
    >
      {o.contenu}
      {o.retaper && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (pret) onFin(true);
          }}
        >
          <Field label="Retape le nom pour confirmer" aide={`« ${o.retaper} »`}>
            {(id) => (
              <Input
                id={id}
                data-autofocus
                autoComplete="off"
                spellCheck={false}
                value={saisie}
                onChange={(e) => setSaisie(e.target.value)}
              />
            )}
          </Field>
        </form>
      )}
    </Dialog>
  );
}

function FenetreSaisie({
  options: o,
  onFin,
}: {
  options: OptionsSaisie;
  onFin: (v: string | null) => void;
}) {
  const [valeur, setValeur] = useState(o.valeurInitiale ?? '');
  const [touche, setTouche] = useState(false);
  const erreur = touche ? (o.valider?.(valeur.trim()) ?? null) : null;
  const vide = valeur.trim().length === 0;

  function envoyer() {
    setTouche(true);
    const e = o.valider?.(valeur.trim()) ?? null;
    if (!e && !vide) onFin(valeur.trim());
  }

  return (
    <Dialog
      ouvert
      onFermer={() => onFin(null)}
      titre={o.titre}
      description={o.message}
      pied={
        <>
          <Button variante="ghost" onClick={() => onFin(null)}>
            Annuler
          </Button>
          <Button variante="primary" disabled={vide} onClick={envoyer} raccourci="Entrée">
            {o.confirmer}
          </Button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          envoyer();
        }}
      >
        <Field label={o.label} aide={o.aide} erreur={erreur}>
          {(id) => (
            <Input
              id={id}
              data-autofocus
              autoComplete="off"
              spellCheck={false}
              value={valeur}
              placeholder={o.placeholder}
              aria-invalid={erreur ? true : undefined}
              onChange={(e) => setValeur(e.target.value)}
            />
          )}
        </Field>
      </form>
    </Dialog>
  );
}
