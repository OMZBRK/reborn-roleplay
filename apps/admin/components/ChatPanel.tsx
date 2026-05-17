'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useState } from 'react';
import { toast } from 'sonner';
import { api } from '@/lib/api';
import type { AdminMessage, CurrentUser } from '@/lib/types';
import { MCAvatar } from './MCAvatar';
import { IconBell, IconBellOff, IconChat, IconSend } from './icons';

const SOUND_PREF_KEY = 'reborn-admin.chat.sound';

function readSoundPref(): boolean {
  if (typeof window === 'undefined') return true;
  const v = window.localStorage.getItem(SOUND_PREF_KEY);
  // Default ON.
  return v !== 'off';
}

/**
 * Beep court genere via Web Audio API — pas de fichier asset a embarquer.
 * Sin onde a 880Hz / 0.15s avec decay exponentiel. Volume ~10% pour ne
 * pas surprendre.
 */
function playBeep() {
  try {
    const w = window as unknown as {
      AudioContext?: typeof AudioContext;
      webkitAudioContext?: typeof AudioContext;
    };
    const AudioCtor = w.AudioContext ?? w.webkitAudioContext;
    if (!AudioCtor) return;
    const ctx = new AudioCtor();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 880;
    osc.type = 'sine';
    gain.gain.setValueAtTime(0.0001, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.1, ctx.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.18);
    osc.start();
    osc.stop(ctx.currentTime + 0.2);
    osc.onended = () => ctx.close().catch(() => {});
  } catch {
    /* ignore */
  }
}

/**
 * ChatPanel : composant unifie pour la conversation staff↔candidat
 * sur whitelist ET ticket.
 *
 * Layout flex column avec :
 *   - header compact (titre + count)
 *   - zone messages scrollable (flex-1)
 *   - input docke en bas (shrink-0)
 *
 * Bubbles modernes : avatar staff a gauche / avatar joueur a droite,
 * timestamp en metadata discret, padding genereux. Le MCAvatar prend
 * l'UUID si fourni, sinon fallback initiale.
 */
export function ChatPanel({
  endpoint,
  queryKey,
  messages,
  canSend,
  playerUuid,
  playerName,
  emptyLabel = 'Aucun message pour l\'instant.',
  closedLabel = 'Conversation cloturée.',
}: {
  /** Path POST pour envoyer un message, ex: /admin/whitelist/<id>/messages */
  endpoint: string;
  /** Query key TanStack à invalider apres send. */
  queryKey: readonly unknown[];
  messages: AdminMessage[];
  canSend: boolean;
  playerUuid: string;
  playerName: string;
  emptyLabel?: string;
  closedLabel?: string;
}) {
  const qc = useQueryClient();
  const [draft, setDraft] = useState('');
  const [soundOn, setSoundOn] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  const lastCountRef = useRef(0);

  // Hydrate la pref son depuis localStorage au mount.
  useEffect(() => {
    setSoundOn(readSoundPref());
  }, []);

  const { data: me } = useQuery({
    queryKey: ['admin', 'me'],
    queryFn: () => api<CurrentUser>('/auth/me'),
    staleTime: 5 * 60_000,
  });

  // Auto-scroll + son sur nouveau message (autre que moi). Skip le tout
  // premier render (hydratation messages au mount) pour eviter un beep
  // au load de la page.
  useEffect(() => {
    if (messages.length > lastCountRef.current) {
      const isFirstRender = lastCountRef.current === 0;
      scrollRef.current?.scrollTo({
        top: scrollRef.current.scrollHeight,
        behavior: isFirstRender ? 'auto' : 'smooth',
      });
      if (!isFirstRender && soundOn) {
        const last = messages[messages.length - 1];
        const fromOther = !me || last?.authorId !== me.id;
        if (fromOther) playBeep();
      }
      lastCountRef.current = messages.length;
    }
  }, [messages, me, soundOn]);

  function toggleSound() {
    const next = !soundOn;
    setSoundOn(next);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(SOUND_PREF_KEY, next ? 'on' : 'off');
    }
  }

  const sendMut = useMutation({
    mutationFn: (content: string) =>
      api(endpoint, { method: 'POST', body: { content } }),
    onSuccess: () => {
      setDraft('');
      qc.invalidateQueries({ queryKey });
    },
    onError: (err) => {
      toast.error('Message non envoyé', {
        description: (err as Error).message,
      });
    },
  });

  return (
    <div className="flex h-full min-h-[420px] flex-col overflow-hidden rounded-[14px] border border-[var(--color-border)] bg-[var(--color-surface)]">
      {/* Header */}
      <div className="flex items-center justify-between gap-2 border-b border-[var(--color-border)] px-4 py-3">
        <div className="flex items-center gap-2">
          <IconChat className="text-[var(--color-accent)]" />
          <span className="text-sm font-medium">Conversation</span>
          {messages.length > 0 && (
            <span className="rounded-full bg-[var(--color-surface-elevated)] px-2 py-0.5 text-[10px] text-[var(--color-foreground-muted)]">
              {messages.length}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={toggleSound}
            title={soundOn ? 'Désactiver les notifications sonores' : 'Activer les notifications sonores'}
            className="text-[var(--color-foreground-muted)] hover:text-[var(--color-foreground)] transition-colors"
          >
            {soundOn ? <IconBell /> : <IconBellOff />}
          </button>
          <div className="text-[10px] uppercase tracking-wider text-[var(--color-foreground-muted)] flex items-center">
            live
            <span className="ml-1.5 inline-block h-1.5 w-1.5 rounded-full bg-[var(--color-success)] animate-pulse" />
          </div>
        </div>
      </div>

      {/* Messages */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto px-4 py-4 space-y-4"
      >
        {messages.length === 0 ? (
          <div className="flex flex-col items-center justify-center text-center py-12 text-[var(--color-foreground-muted)]">
            <IconChat className="mb-3 opacity-40" />
            <div className="text-sm">{emptyLabel}</div>
          </div>
        ) : (
          messages.map((m, i) => {
            const isStaff = m.authorType === 'STAFF';
            const isMe = isStaff && me && m.authorId === me.id;
            const prev = messages[i - 1];
            const isGrouped = prev && prev.authorType === m.authorType;
            return (
              <MessageBubble
                key={m.id}
                message={m}
                isStaff={isStaff}
                isMe={!!isMe}
                hideAvatar={isGrouped}
                playerUuid={playerUuid}
                playerName={playerName}
                meUuid={me?.minecraftUuid}
                meName={me?.minecraftUsername}
              />
            );
          })
        )}
      </div>

      {/* Composer */}
      <div className="border-t border-[var(--color-border)] bg-[var(--color-surface-elevated)]/30 px-4 py-3">
        {canSend ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const content = draft.trim();
              if (content.length === 0 || sendMut.isPending) return;
              sendMut.mutate(content);
            }}
            className="flex items-end gap-2"
          >
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  const content = draft.trim();
                  if (content.length > 0 && !sendMut.isPending) {
                    sendMut.mutate(content);
                  }
                }
              }}
              rows={1}
              placeholder={`Répondre à ${playerName}…`}
              className="min-h-[40px] max-h-[120px] flex-1 resize-none rounded-[10px] border border-[var(--color-border-strong)] bg-[var(--color-background)] px-3 py-2 text-sm focus:border-[var(--color-accent)] focus:outline-none placeholder:text-[var(--color-foreground-muted)]"
            />
            <button
              type="submit"
              disabled={draft.trim().length === 0 || sendMut.isPending}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)] disabled:opacity-40 disabled:cursor-not-allowed shadow-[var(--shadow-glow-accent)] transition-colors"
              title="Envoyer (Entrée)"
            >
              <IconSend />
            </button>
          </form>
        ) : (
          <div className="text-center text-xs text-[var(--color-foreground-muted)] py-1">
            {closedLabel}
          </div>
        )}
      </div>
    </div>
  );
}

function MessageBubble({
  message,
  isStaff,
  isMe,
  hideAvatar,
  playerUuid,
  playerName,
  meUuid,
  meName,
}: {
  message: AdminMessage;
  isStaff: boolean;
  isMe: boolean;
  hideAvatar: boolean;
  playerUuid: string;
  playerName: string;
  meUuid?: string;
  meName?: string;
}) {
  const align = isStaff ? 'justify-end' : 'justify-start';
  const bubbleClass = isStaff
    ? isMe
      ? 'bg-[var(--color-accent)] text-white border-[var(--color-accent)]'
      : 'bg-[var(--color-accent-soft)] text-[var(--color-foreground)] border-[var(--color-accent)]/30'
    : 'bg-[var(--color-surface-elevated)] text-[var(--color-foreground)] border-[var(--color-border)]';

  // L'UUID a afficher : joueur ↔ playerUuid, staff ↔ meUuid si c'est moi.
  // Pour un staff autre que moi on n'a pas l'UUID dans le message →
  // fallback initiale.
  const avatarUuid = isStaff ? (isMe ? meUuid : null) : playerUuid;
  const avatarName = isStaff
    ? message.authorName ?? (isMe ? meName : 'Staff') ?? 'Staff'
    : playerName;

  return (
    <div className={`flex items-end gap-2 ${align}`}>
      {!isStaff &&
        (hideAvatar ? (
          <div className="w-7 shrink-0" />
        ) : (
          <MCAvatar
            uuid={playerUuid}
            username={avatarName}
            size={28}
            rounded="lg"
            className="shrink-0"
          />
        ))}
      <div
        className={`max-w-[78%] rounded-[12px] border px-3 py-2 ${bubbleClass}`}
      >
        {!hideAvatar && (
          <div
            className={`mb-0.5 text-[10px] font-medium ${
              isMe ? 'text-white/70' : 'text-[var(--color-foreground-muted)]'
            }`}
          >
            {avatarName}
          </div>
        )}
        <div className="text-sm whitespace-pre-wrap leading-relaxed">
          {message.content}
        </div>
        <div
          className={`mt-1 text-[9px] ${
            isMe
              ? 'text-white/50'
              : isStaff
                ? 'text-[var(--color-foreground-muted)]'
                : 'text-[var(--color-foreground-muted)]'
          }`}
        >
          {new Date(message.createdAt).toLocaleTimeString('fr-FR', {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </div>
      </div>
      {isStaff &&
        (hideAvatar ? (
          <div className="w-7 shrink-0" />
        ) : avatarUuid ? (
          <MCAvatar
            uuid={avatarUuid}
            username={avatarName}
            size={28}
            rounded="lg"
            className="shrink-0"
          />
        ) : (
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[10px] bg-[var(--color-accent-soft)] text-[10px] font-medium text-[var(--color-accent)]">
            {(avatarName ?? '?').slice(0, 2).toUpperCase()}
          </div>
        ))}
    </div>
  );
}
