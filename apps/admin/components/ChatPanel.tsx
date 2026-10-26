'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import { dateHeure, ilYa } from '@/lib/format';
import { REPONSES_TYPES, type ReponseType } from '@/lib/modeles';
import { useDroits } from '@/lib/droits';
import type { AdminMessage } from '@/lib/types';
import { MCAvatar } from './MCAvatar';
import { Button, BoutonIcone } from './ui/Button';
import { Kbd } from './ui/Kbd';
import { messageErreur } from './ui/States';

const CLE_SON = 'reborn-admin.chat.sound';

function lireSon(): boolean {
  try {
    return window.localStorage.getItem(CLE_SON) !== 'off';
  } catch {
    return true;
  }
}

/** Bip court (Web Audio), sans fichier à embarquer. */
function bip() {
  try {
    const w = window as unknown as {
      AudioContext?: typeof AudioContext;
      webkitAudioContext?: typeof AudioContext;
    };
    const Ctor = w.AudioContext ?? w.webkitAudioContext;
    if (!Ctor) return;
    const ctx = new Ctor();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 880;
    gain.gain.setValueAtTime(0.0001, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.1, ctx.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.18);
    osc.start();
    osc.stop(ctx.currentTime + 0.2);
    osc.onended = () => ctx.close().catch(() => {});
  } catch {
    /* sans son */
  }
}

export interface ChatPanelRef {
  /** Met le curseur dans la zone de réponse (touche R). */
  repondre: () => void;
}

/**
 * Conversation staff ↔ joueur, commune à la candidature (tiroir
 * « Échanges ») et au ticket (PANEL_V2 §4.3, §4.4) : messages en fil,
 * réponse en bas. Ctrl + Entrée envoie ; avec les réponses types, « / » en
 * début de réponse ouvre la liste ; avec `onEnvoyerEtResoudre`,
 * Ctrl + Maj + Entrée envoie et résout.
 */
export const ChatPanel = forwardRef<
  ChatPanelRef,
  {
    endpoint: string;
    queryKey: readonly unknown[];
    messages: AdminMessage[];
    canSend: boolean;
    playerUuid: string;
    playerName: string;
    emptyLabel?: string;
    closedLabel?: string;
    /** Mot affiché à côté du nom du joueur (« Candidat », « Joueur »). */
    roleJoueur?: string;
    reponsesTypes?: boolean;
    /** Juste avant l'envoi (prendre le ticket à la première réponse). */
    avantEnvoi?: () => Promise<void>;
    /** Après un envoi réussi, pour « Envoyer et résoudre ». */
    resoudre?: () => Promise<void>;
    /** La zone de défilement occupe toute la hauteur disponible. */
    pleineHauteur?: boolean;
  }
>(function ChatPanel(
  {
    endpoint,
    queryKey,
    messages,
    canSend,
    playerUuid,
    playerName,
    emptyLabel = 'Aucun message pour l’instant.',
    closedLabel = 'Conversation close.',
    roleJoueur = 'Joueur',
    reponsesTypes = false,
    avantEnvoi,
    resoudre,
    pleineHauteur = true,
  },
  ref,
) {
  const qc = useQueryClient();
  const { moi } = useDroits();
  const [brouillon, setBrouillon] = useState('');
  const [son, setSon] = useState(true);
  const [indexSlash, setIndexSlash] = useState(0);
  const fil = useRef<HTMLDivElement>(null);
  const zone = useRef<HTMLTextAreaElement>(null);
  const dernierNombre = useRef(0);

  useImperativeHandle(ref, () => ({ repondre: () => zone.current?.focus() }));
  useEffect(() => setSon(lireSon()), []);

  // Défile et sonne à l'arrivée d'un message d'un autre (pas au chargement).
  useEffect(() => {
    if (messages.length > dernierNombre.current) {
      const premier = dernierNombre.current === 0;
      fil.current?.scrollTo({ top: fil.current.scrollHeight, behavior: premier ? 'auto' : 'smooth' });
      if (!premier && son) {
        const dernier = messages[messages.length - 1];
        if (!moi || dernier?.authorId !== moi.id) bip();
      }
      dernierNombre.current = messages.length;
    }
  }, [messages, moi, son]);

  function basculerSon() {
    const v = !son;
    setSon(v);
    try {
      window.localStorage.setItem(CLE_SON, v ? 'on' : 'off');
    } catch {
      /* préférence pour la session seulement */
    }
  }

  const envoi = useMutation({
    mutationFn: async ({ contenu, etResoudre }: { contenu: string; etResoudre: boolean }) => {
      if (avantEnvoi) await avantEnvoi();
      await api(endpoint, { method: 'POST', body: { content: contenu } });
      if (etResoudre && resoudre) await resoudre();
    },
    onSuccess: (_d, v) => {
      setBrouillon('');
      qc.invalidateQueries({ queryKey });
      if (v.etResoudre) toast.success('Réponse envoyée, ticket résolu');
    },
    onError: (e) => toast.error('Message non envoyé', { description: messageErreur(e) }),
  });

  // « / » en début de réponse : réponses types filtrées par ce qui suit.
  const slash = reponsesTypes && brouillon.startsWith('/') && !brouillon.includes('\n');
  const filtre = slash ? brouillon.slice(1).toLowerCase() : '';
  const choix: ReponseType[] = slash
    ? REPONSES_TYPES.filter(
        (r) => r.code.startsWith(filtre) || r.nom.toLowerCase().includes(filtre),
      )
    : [];
  useEffect(() => setIndexSlash(0), [filtre]);

  function inserer(r: ReponseType) {
    setBrouillon(r.texte);
    setTimeout(() => {
      const z = zone.current;
      if (z) {
        z.focus();
        z.setSelectionRange(r.texte.length, r.texte.length);
      }
    }, 0);
  }

  function envoyer(etResoudre: boolean) {
    const contenu = brouillon.trim();
    if (!contenu || envoi.isPending || (slash && choix.length > 0)) return;
    envoi.mutate({ contenu, etResoudre });
  }

  return (
    <div className={`flex min-h-0 flex-col ${pleineHauteur ? 'h-full' : ''}`}>
      <div ref={fil} className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-5 py-5" aria-live="polite">
        {messages.length === 0 ? (
          <p className="m-auto text-center text-[13px] text-fg-3">{emptyLabel}</p>
        ) : (
          messages.map((m) => (
            <Message
              key={m.id}
              m={m}
              moiId={moi?.id}
              moiUuid={moi?.minecraftUuid}
              playerUuid={playerUuid}
              playerName={playerName}
              roleJoueur={roleJoueur}
            />
          ))
        )}
      </div>

      <div className="relative border-t border-[var(--color-border)] px-5 pb-4 pt-4">
        {slash && choix.length > 0 && (
          <div className="pop slash" role="listbox" aria-label="Réponses types">
            <div className="slash-h">Réponses types</div>
            {choix.map((r, i) => (
              <button
                key={r.code}
                type="button"
                role="option"
                aria-selected={i === indexSlash}
                className="slash-i"
                onMouseEnter={() => setIndexSlash(i)}
                onClick={() => inserer(r)}
              >
                <span className="mono">/{r.code}</span>
                <span className="min-w-0 flex-1 truncate">{r.nom}</span>
                {i === indexSlash && <Kbd>Entrée</Kbd>}
              </button>
            ))}
          </div>
        )}
        {canSend ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              envoyer(false);
            }}
          >
            <label htmlFor={`${endpoint}-reponse`} className="sr-only">
              Répondre à {playerName}
            </label>
            <textarea
              id={`${endpoint}-reponse`}
              ref={zone}
              value={brouillon}
              onChange={(e) => setBrouillon(e.target.value)}
              onKeyDown={(e) => {
                if (slash && choix.length > 0) {
                  if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    setIndexSlash((i) => (i + 1) % choix.length);
                    return;
                  }
                  if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    setIndexSlash((i) => (i - 1 + choix.length) % choix.length);
                    return;
                  }
                  if (e.key === 'Enter' || e.key === 'Tab') {
                    e.preventDefault();
                    inserer(choix[indexSlash]!);
                    return;
                  }
                  if (e.key === 'Escape') {
                    e.preventDefault();
                    setBrouillon('');
                    return;
                  }
                }
                if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                  e.preventDefault();
                  envoyer(e.shiftKey && !!resoudre);
                }
              }}
              rows={3}
              maxLength={4000}
              placeholder={
                reponsesTypes
                  ? `Répondre à ${playerName}… (« / » pour les réponses types)`
                  : `Répondre à ${playerName}…`
              }
              className="input"
            />
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <span className="hints">
                <Kbd>Ctrl</Kbd>+<Kbd>Entrée</Kbd> envoyer
                {resoudre && (
                  <>
                    {' · '}
                    <Kbd>Ctrl</Kbd>+<Kbd>Maj</Kbd>+<Kbd>Entrée</Kbd> envoyer et résoudre
                  </>
                )}
              </span>
              <span className="ml-auto flex items-center gap-2">
                <BoutonIcone
                  icone={son ? 'bell' : 'bell-off'}
                  label={son ? 'Couper le son des nouveaux messages' : 'Activer le son des nouveaux messages'}
                  onClick={basculerSon}
                  className={son ? '' : 'opacity-60'}
                />
                <Button
                  type="submit"
                  variante={resoudre ? 'secondary' : 'primary'}
                  enCours={envoi.isPending && !envoi.variables?.etResoudre}
                  disabled={brouillon.trim().length === 0 || envoi.isPending}
                >
                  Envoyer
                </Button>
                {resoudre && (
                  <Button
                    variante="primary"
                    enCours={envoi.isPending && !!envoi.variables?.etResoudre}
                    disabled={brouillon.trim().length === 0 || envoi.isPending}
                    onClick={() => envoyer(true)}
                  >
                    Envoyer et résoudre
                  </Button>
                )}
              </span>
            </div>
          </form>
        ) : (
          <p className="text-center text-[13px] text-fg-3">{closedLabel}</p>
        )}
      </div>
    </div>
  );
});

function Message({
  m,
  moiId,
  moiUuid,
  playerUuid,
  playerName,
  roleJoueur,
}: {
  m: AdminMessage;
  moiId?: string;
  moiUuid?: string;
  playerUuid: string;
  playerName: string;
  roleJoueur: string;
}) {
  if (m.authorType === 'SYSTEM') {
    return (
      <span className="sys" title={dateHeure(m.createdAt)}>
        {texteSysteme(m.content)} · {ilYa(m.createdAt)}
      </span>
    );
  }
  const staff = m.authorType === 'STAFF';
  const estMoi = staff && !!moiId && m.authorId === moiId;
  const nom = staff ? (m.authorName ?? 'Staff') : playerName;
  return (
    <div className={`msg ${staff ? 'staff' : ''}`}>
      {staff ? (
        estMoi && moiUuid ? (
          <MCAvatar uuid={moiUuid} size={30} />
        ) : (
          <span
            className="face flex items-center justify-center text-[11px] font-semibold text-fg-2"
            style={{ width: 30, height: 30 }}
            aria-hidden
          >
            {nom.slice(0, 2).toUpperCase()}
          </span>
        )
      ) : (
        <MCAvatar uuid={playerUuid} size={30} />
      )}
      <div className="min-w-0">
        <div className="msg-h">
          <b>{estMoi ? `${nom} (toi)` : nom}</b>
          <span>{staff ? 'Staff' : roleJoueur}</span>
          <span title={dateHeure(m.createdAt)}>{ilYa(m.createdAt)}</span>
        </div>
        <div className="msg-t">{m.content}</div>
      </div>
    </div>
  );
}

/**
 * Message système affiché au staff : sans l'emoji de tête (écrit pour le
 * launcher, PANEL_V2 §5.5 : aucun emoji au panel) ni le point final, suivi
 * de « · il y a … ».
 */
function texteSysteme(contenu: string): string {
  return contenu
    .replace(/^[\p{Extended_Pictographic}️‍\s]+/u, '')
    .replace(/[.\s]+$/u, '');
}
