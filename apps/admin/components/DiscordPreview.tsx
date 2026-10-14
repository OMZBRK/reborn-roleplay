'use client';

import { Fragment, type ReactNode } from 'react';
import type { Bloc, Canal, PingOption } from '@/lib/discord';

/**
 * Aperçu « façon Discord » d'un message du bot : thème sombre du client,
 * en-tête du bot (badge APP), puis le cadre composants v2 (fond d'embed,
 * barre de couleur à gauche) avec textes markdown Discord, galeries,
 * séparateurs et boutons-liens. Reproduit le rendu de apps/bot/src/messages/cadre.ts.
 *
 * Le markdown est celui de Discord, pas du CommonMark : `#`/`##`/`###`,
 * sous-texte `-#`, listes, citations, gras/italique/souligné/barré, code,
 * liens masqués, et les mentions Reborn `{#salon}` / `{@rôle}` en pastilles.
 */

const D = {
  bg: '#313338',
  container: '#2b2d31',
  containerBorder: '#3f4147',
  text: '#dbdee1',
  heading: '#f2f3f5',
  muted: '#949ba4',
  link: '#00a8fc',
  mentionBg: 'rgba(88, 101, 242, 0.3)',
  mentionFg: '#c9cdfb',
  codeBg: '#1e1f22',
  button: '#4e5058',
  separator: '#3f4147',
  app: '#5865f2',
};

const FONT = '"gg sans", "Noto Sans", "Helvetica Neue", Helvetica, Arial, sans-serif';

// ── Markdown Discord (inline) ───────────────────────────

const INLINE =
  /(`[^`\n]+`)|(\{#[^}\n]+\})|(\{@[^}\n]+\})|(\[[^\]\n]+\]\(https?:\/\/[^)\s]+\))|(\*\*\*[^*\n]+\*\*\*)|(\*\*[^\n]+?\*\*)|(__[^\n]+?__)|(\*[^*\n]+\*)|(_[^_\n]+_)|(~~[^\n]+?~~)|(\|\|[^\n]+?\|\|)|(https?:\/\/[^\s<]+)/g;

function inline(src: string, keyBase = 'i'): ReactNode[] {
  const out: ReactNode[] = [];
  let last = 0;
  let k = 0;
  for (const m of src.matchAll(INLINE)) {
    const idx = m.index ?? 0;
    if (idx > last) out.push(src.slice(last, idx));
    const t = m[0];
    const key = `${keyBase}-${k++}`;
    if (m[1]) {
      out.push(
        <code key={key} style={{ background: D.codeBg, borderRadius: 4, padding: '0 3px', fontSize: '0.85em', fontFamily: 'Consolas, "Courier New", monospace' }}>
          {t.slice(1, -1)}
        </code>,
      );
    } else if (m[2] || m[3]) {
      out.push(
        <span key={key} style={{ background: D.mentionBg, color: D.mentionFg, borderRadius: 3, padding: '0 2px', fontWeight: 500 }}>
          {m[2] ? '#' : '@'}
          {t.slice(2, -1)}
        </span>,
      );
    } else if (m[4]) {
      const mm = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(t);
      out.push(
        <a key={key} href={mm?.[2]} target="_blank" rel="noreferrer" style={{ color: D.link }}>
          {inline(mm?.[1] ?? '', key)}
        </a>,
      );
    } else if (m[5]) {
      out.push(<strong key={key}><em>{inline(t.slice(3, -3), key)}</em></strong>);
    } else if (m[6]) {
      out.push(<strong key={key} style={{ fontWeight: 700 }}>{inline(t.slice(2, -2), key)}</strong>);
    } else if (m[7]) {
      out.push(<u key={key}>{inline(t.slice(2, -2), key)}</u>);
    } else if (m[8] || m[9]) {
      out.push(<em key={key}>{inline(t.slice(1, -1), key)}</em>);
    } else if (m[10]) {
      out.push(<s key={key}>{inline(t.slice(2, -2), key)}</s>);
    } else if (m[11]) {
      out.push(
        <span key={key} style={{ background: D.codeBg, borderRadius: 3, padding: '0 2px' }}>
          {inline(t.slice(2, -2), key)}
        </span>,
      );
    } else if (m[12]) {
      out.push(
        <a key={key} href={t} target="_blank" rel="noreferrer" style={{ color: D.link }}>
          {t}
        </a>,
      );
    }
    last = idx + t.length;
  }
  if (last < src.length) out.push(src.slice(last));
  return out;
}

// ── Markdown Discord (blocs) ────────────────────────────

export function DiscordMarkdown({ source }: { source: string }) {
  const lines = source.replace(/\r\n/g, '\n').split('\n');
  const nodes: ReactNode[] = [];
  let i = 0;
  let k = 0;
  while (i < lines.length) {
    const line = lines[i]!;
    const key = `b${k++}`;
    if (line.startsWith('```')) {
      const code: string[] = [];
      i++;
      while (i < lines.length && !lines[i]!.startsWith('```')) code.push(lines[i++]!);
      i++;
      nodes.push(
        <pre key={key} style={{ background: D.codeBg, border: `1px solid ${D.containerBorder}`, borderRadius: 4, padding: 8, margin: '4px 0', fontSize: 13, whiteSpace: 'pre-wrap', fontFamily: 'Consolas, "Courier New", monospace' }}>
          {code.join('\n')}
        </pre>,
      );
      continue;
    }
    const h = /^(#{1,3})\s+(.*)$/.exec(line);
    if (h) {
      const level = h[1]!.length;
      const size = level === 1 ? 24 : level === 2 ? 20 : 16;
      nodes.push(
        <div key={key} style={{ color: D.heading, fontSize: size, fontWeight: 700, lineHeight: 1.375, margin: `${level === 1 ? 8 : 6}px 0 2px` }}>
          {inline(h[2]!, key)}
        </div>,
      );
      i++;
      continue;
    }
    const sub = /^-#\s+(.*)$/.exec(line);
    if (sub) {
      nodes.push(
        <div key={key} style={{ color: D.muted, fontSize: 12, lineHeight: 1.33 }}>
          {inline(sub[1]!, key)}
        </div>,
      );
      i++;
      continue;
    }
    if (/^>>>\s?/.test(line)) {
      const rest = [line.replace(/^>>>\s?/, ''), ...lines.slice(i + 1)].join('\n');
      nodes.push(
        <div key={key} style={{ borderLeft: `4px solid ${D.containerBorder}`, paddingLeft: 12, margin: '2px 0' }}>
          <DiscordMarkdown source={rest} />
        </div>,
      );
      break;
    }
    if (/^>\s?/.test(line)) {
      const quote: string[] = [];
      while (i < lines.length && /^>\s?/.test(lines[i]!)) quote.push(lines[i++]!.replace(/^>\s?/, ''));
      nodes.push(
        <div key={key} style={{ borderLeft: `4px solid ${D.containerBorder}`, paddingLeft: 12, margin: '2px 0' }}>
          <DiscordMarkdown source={quote.join('\n')} />
        </div>,
      );
      continue;
    }
    if (/^\s*[-*]\s+/.test(line)) {
      const items: { depth: number; text: string }[] = [];
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i]!)) {
        const l = lines[i++]!;
        items.push({ depth: /^\s{2,}/.test(l) ? 1 : 0, text: l.replace(/^\s*[-*]\s+/, '') });
      }
      nodes.push(
        <ul key={key} style={{ margin: '2px 0', paddingLeft: 0, listStyle: 'none' }}>
          {items.map((it, j) => (
            <li key={j} style={{ display: 'flex', gap: 8, marginLeft: 6 + it.depth * 20 }}>
              <span style={{ color: D.text }}>{it.depth ? '◦' : '•'}</span>
              <span>{inline(it.text, `${key}-${j}`)}</span>
            </li>
          ))}
        </ul>,
      );
      continue;
    }
    if (/^\d+\.\s+/.test(line)) {
      const items: { n: string; text: string }[] = [];
      while (i < lines.length && /^\d+\.\s+/.test(lines[i]!)) {
        const l = lines[i++]!;
        const mm = /^(\d+)\.\s+(.*)$/.exec(l)!;
        items.push({ n: mm[1]!, text: mm[2]! });
      }
      nodes.push(
        <ol key={key} style={{ margin: '2px 0', paddingLeft: 0, listStyle: 'none' }}>
          {items.map((it, j) => (
            <li key={j} style={{ display: 'flex', gap: 6, marginLeft: 4 }}>
              <span style={{ color: D.text, minWidth: 16, textAlign: 'right' }}>{it.n}.</span>
              <span>{inline(it.text, `${key}-${j}`)}</span>
            </li>
          ))}
        </ol>,
      );
      continue;
    }
    if (line.trim() === '') {
      nodes.push(<div key={key} style={{ height: '1.375em' }} />);
      i++;
      continue;
    }
    nodes.push(<div key={key}>{inline(line, key)}</div>);
    i++;
  }
  return <>{nodes}</>;
}

// ── Galerie ─────────────────────────────────────────────

/** Répartition des images par rangée, comme le client Discord. */
function rangees(n: number): number[] {
  switch (n) {
    case 1: return [1];
    case 2: return [2];
    case 3: return [3];
    case 4: return [2, 2];
    case 5: return [2, 3];
    case 6: return [3, 3];
    case 7: return [1, 3, 3];
    case 8: return [2, 3, 3];
    case 9: return [3, 3, 3];
    default: return [1, 3, 3, 3];
  }
}

function Galerie({ ids, medias }: { ids: string[]; medias: Record<string, string> }) {
  if (ids.length === 3) {
    // 3 images : une grande à gauche, deux empilées à droite.
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gridTemplateRows: '1fr 1fr', gap: 4, height: 260, borderRadius: 8, overflow: 'hidden' }}>
        <Img src={medias[ids[0]!]} style={{ gridRow: '1 / span 2' }} />
        <Img src={medias[ids[1]!]} />
        <Img src={medias[ids[2]!]} />
      </div>
    );
  }
  let start = 0;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, borderRadius: 8, overflow: 'hidden' }}>
      {rangees(ids.length).map((count, r) => {
        const row = ids.slice(start, start + count);
        start += count;
        const h = count === 1 ? (ids.length === 1 ? 'auto' : 220) : count === 2 ? 180 : 120;
        return (
          <div key={r} style={{ display: 'grid', gridTemplateColumns: `repeat(${count}, 1fr)`, gap: 4 }}>
            {row.map((id) => (
              <Img key={id} src={medias[id]} style={{ height: h, maxHeight: ids.length === 1 ? 360 : undefined }} />
            ))}
          </div>
        );
      })}
    </div>
  );
}

function Img({ src, style }: { src?: string; style?: React.CSSProperties }) {
  if (!src) {
    return (
      <div style={{ background: D.codeBg, color: D.muted, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, minHeight: 80, ...style }}>
        image
      </div>
    );
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt="" style={{ width: '100%', objectFit: 'cover', display: 'block', ...style }} />;
}

// ── Message complet ─────────────────────────────────────

export function DiscordPreview({
  canal,
  couleur,
  blocs,
  ping,
  medias,
  salon,
}: {
  canal: Canal;
  couleur: string;
  blocs: Bloc[];
  ping: PingOption;
  medias: Record<string, string>;
  salon: string;
}) {
  const now = new Date();
  const heure = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  const pingTexte = ping === 'everyone' && canal === 'annonces' ? '@everyone' : ping === 'testeurs' && canal === 'dev-updates' ? '@Testeur' : null;
  const visibles = blocs.filter(
    (b) => b.texte?.trim() || (b.images?.length ?? 0) > 0 || b.separateur || (b.liens?.length ?? 0) > 0,
  );

  return (
    <div style={{ background: D.bg, fontFamily: FONT, color: D.text, fontSize: 16, lineHeight: 1.375, borderRadius: 12, overflow: 'hidden' }}>
      <div style={{ padding: '10px 16px', borderBottom: '1px solid #26282c', color: D.heading, fontWeight: 600, fontSize: 15 }}>
        {salon}
      </div>
      <div style={{ display: 'flex', gap: 16, padding: '16px 16px 20px 16px' }}>
        <div style={{ width: 40, height: 40, borderRadius: '50%', flexShrink: 0, background: 'radial-gradient(circle at 30% 30%, #C01E35, #4a0b14)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#F5E9D0', fontWeight: 800, fontSize: 16 }}>
          R
        </div>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{ color: D.heading, fontWeight: 600 }}>Reborn Roleplay</span>
            <span style={{ background: D.app, color: '#fff', fontSize: 10, fontWeight: 700, borderRadius: 4, padding: '1px 5px', lineHeight: '15px' }}>
              ✓ APP
            </span>
            <span style={{ color: D.muted, fontSize: 12 }}>Aujourd’hui à {heure}</span>
          </div>
          {pingTexte && (
            <div style={{ marginTop: 2 }}>
              <span style={{ background: D.mentionBg, color: D.mentionFg, borderRadius: 3, padding: '0 2px', fontWeight: 500 }}>
                {pingTexte}
              </span>
            </div>
          )}
          <div
            style={{
              marginTop: 6,
              maxWidth: 560,
              background: D.container,
              border: `1px solid ${D.containerBorder}`,
              borderLeft: `4px solid ${couleur}`,
              borderRadius: 8,
              padding: 16,
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
            }}
          >
            {visibles.length === 0 && <div style={{ color: D.muted, fontSize: 14 }}>Message vide : ajoute un bloc.</div>}
            {visibles.map((b, i) => (
              <Fragment key={i}>
                {b.texte?.trim() && (
                  <div style={{ fontSize: 15, overflowWrap: 'anywhere' }}>
                    <DiscordMarkdown source={b.texte} />
                  </div>
                )}
                {b.images && b.images.length > 0 && <Galerie ids={b.images} medias={medias} />}
                {b.separateur && <div style={{ height: 1, background: D.separator, margin: '4px 0' }} />}
                {b.liens && b.liens.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {b.liens.map((l, j) => (
                      <a
                        key={j}
                        href={l.url || undefined}
                        target="_blank"
                        rel="noreferrer"
                        style={{ background: D.button, color: '#fff', borderRadius: 8, height: 32, padding: '0 14px', display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 14, fontWeight: 500, textDecoration: 'none' }}
                      >
                        {l.emoji && <span>{l.emoji}</span>}
                        <span>{l.libelle || 'Bouton'}</span>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M14 4h6v6" />
                          <path d="M20 4 11 13" />
                          <path d="M19 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h5" />
                        </svg>
                      </a>
                    ))}
                  </div>
                )}
              </Fragment>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
