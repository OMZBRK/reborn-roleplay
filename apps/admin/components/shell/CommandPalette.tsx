'use client';

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { CLE_TICKETS_OUVERTS } from '@/lib/a-traiter';
import { api } from '@/lib/api';
import { useDroits } from '@/lib/droits';
import { libelleCategorie, libelleGrade } from '@/lib/libelles';
import { ONGLETS_REGLAGES, ongletsPermis, sectionsPermises } from '@/lib/navigation';
import { STATUT_PHASE } from '@/lib/statuts';
import type {
  Paginated,
  PlayerListItem,
  TicketListItem,
  WhitelistListe,
  WikiEntry,
} from '@/lib/types';
import { listEntries } from '@/lib/wiki';
import { MCAvatar } from '@/components/MCAvatar';
import { Icone, type NomIcone } from '@/components/ui/Icone';
import { Kbd } from '@/components/ui/Kbd';

/**
 * Palette Ctrl K (PANEL_V2 §4.2, §6.4) : depuis n'importe quelle page,
 * retrouver un joueur par son pseudo, son Discord ou son UUID, un personnage
 * par son nom (candidatures), un ticket ouvert, un article du wiki, une page.
 * Chaque groupe n'apparaît que si le grade y a accès.
 */

interface Resultat {
  cle: string;
  groupe: string;
  titre: string;
  sous?: string;
  icone?: NomIcone;
  visage?: string;
  droite?: ReactNode;
  aller: () => void;
}

function surligne(texte: string, q: string): ReactNode {
  if (!q) return texte;
  const i = texte.toLowerCase().indexOf(q.toLowerCase());
  if (i < 0) return texte;
  return (
    <>
      {texte.slice(0, i)}
      <mark>{texte.slice(i, i + q.length)}</mark>
      {texte.slice(i + q.length)}
    </>
  );
}

export function CommandPalette({
  ouvert,
  onFermer,
}: {
  ouvert: boolean;
  onFermer: () => void;
}) {
  if (!ouvert || typeof document === 'undefined') return null;
  return createPortal(<Palette onFermer={onFermer} />, document.body);
}

function Palette({ onFermer }: { onFermer: () => void }) {
  const router = useRouter();
  const qc = useQueryClient();
  const { peut, peutUne, capacites } = useDroits();
  const [saisie, setSaisie] = useState('');
  const [q, setQ] = useState('');
  const [index, setIndex] = useState(0);
  const champ = useRef<HTMLInputElement>(null);
  const liste = useRef<HTMLDivElement>(null);
  const precedent = useRef<HTMLElement | null>(null);

  useEffect(() => {
    precedent.current = document.activeElement as HTMLElement | null;
    champ.current?.focus();
    const debordement = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = debordement;
      precedent.current?.focus?.();
    };
  }, []);

  useEffect(() => {
    const t = setTimeout(() => setQ(saisie.trim()), 200);
    return () => clearTimeout(t);
  }, [saisie]);

  useEffect(() => setIndex(0), [q]);

  const assez = q.length >= 2;

  const joueurs = useQuery({
    queryKey: ['admin', 'palette', 'joueurs', q],
    queryFn: () =>
      api<PlayerListItem[]>(`/admin/players/search?q=${encodeURIComponent(q)}&take=5`),
    enabled: assez && peut('joueurs.voir'),
    staleTime: 30_000,
  });
  const personnages = useQuery({
    queryKey: ['admin', 'palette', 'personnages', q],
    queryFn: () =>
      api<WhitelistListe>(`/admin/whitelist?q=${encodeURIComponent(q)}&take=5`),
    enabled: assez && peut('candidatures.relire'),
    staleTime: 30_000,
  });
  const wiki = useQuery({
    queryKey: ['admin', 'palette', 'wiki', q],
    queryFn: () => listEntries({ q }),
    enabled: assez && peut('wiki.editer'),
    staleTime: 30_000,
  });
  // Tickets : ceux de la file (ouverts et en cours), déjà en cache.
  const tickets = qc.getQueryData<Paginated<TicketListItem>>(CLE_TICKETS_OUVERTS);

  const resultats = useMemo<Resultat[]>(() => {
    const aller = (href: string) => () => {
      onFermer();
      router.push(href);
    };
    const out: Resultat[] = [];
    const ql = q.toLowerCase();

    for (const w of personnages.data?.items ?? []) {
      out.push({
        cle: `p:${w.id}`,
        groupe: 'Personnages',
        titre: `${w.firstName} ${w.lastName}`.trim(),
        sous: `${w.village} · ${STATUT_PHASE[w.phase].label.toLowerCase()} · ${w.user.minecraftUsername}`,
        visage: w.user.minecraftUuid,
        aller: aller(`/whitelist/${w.id}`),
      });
    }
    for (const j of joueurs.data ?? []) {
      out.push({
        cle: `j:${j.id}`,
        groupe: 'Joueurs',
        titre: j.minecraftUsername,
        sous: [libelleGrade(j.role), j.discordUsername ? `@${j.discordUsername}` : null]
          .filter(Boolean)
          .join(' · '),
        visage: j.minecraftUuid,
        aller: aller(`/players/${j.id}`),
      });
    }
    if (assez && peutUne('tickets.traiter', 'tickets.bug')) {
      for (const t of (tickets?.items ?? [])
        .filter(
          (t) =>
            t.subject.toLowerCase().includes(ql) ||
            t.user.minecraftUsername.toLowerCase().includes(ql),
        )
        .slice(0, 4)) {
        out.push({
          cle: `t:${t.id}`,
          groupe: 'Tickets ouverts',
          titre: t.subject,
          sous: `${libelleCategorie(t.category)} · ${t.user.minecraftUsername}`,
          icone: 'ticket',
          aller: aller(`/tickets/${t.id}`),
        });
      }
    }
    for (const e of ((wiki.data ?? []) as WikiEntry[]).slice(0, 4)) {
      out.push({
        cle: `w:${e.id}`,
        groupe: 'Wiki',
        titre: e.title,
        sous: e.summary ?? 'Article du wiki',
        icone: 'book-open',
        aller: aller(`/wiki?entree=${encodeURIComponent(e.id)}`),
      });
    }

    // Pages et actions : toujours proposées, filtrées par la saisie.
    const pages: Resultat[] = [];
    for (const s of sectionsPermises(capacites)) {
      for (const o of s.permis) {
        const titre = o.label === s.label ? s.label : `${s.label} › ${o.label}`;
        pages.push({
          cle: `nav:${o.href}`,
          groupe: 'Pages',
          titre,
          icone: o.icone ?? s.icone,
          aller: aller(o.href),
        });
      }
    }
    for (const o of ongletsPermis(ONGLETS_REGLAGES, capacites)) {
      pages.push({
        cle: `nav:${o.href}`,
        groupe: 'Pages',
        titre: `Réglages › ${o.label}`,
        icone: 'settings',
        aller: aller(o.href),
      });
    }
    if (peut('douane.organiser')) {
      pages.push({
        cle: 'act:planifier',
        groupe: 'Actions',
        titre: 'Planifier une séance de douane',
        icone: 'plus',
        aller: aller('/douane?planifier=1'),
      });
    }
    out.push(
      ...pages.filter((p) => !q || p.titre.toLowerCase().includes(ql)).slice(0, q ? 6 : 12),
    );
    return out;
  }, [q, assez, personnages.data, joueurs.data, wiki.data, tickets, capacites, peut, peutUne, onFermer, router]);

  const enCours =
    assez && (joueurs.isFetching || personnages.isFetching || wiki.isFetching);

  function surTouche(e: React.KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault();
      onFermer();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setIndex((i) => Math.min(resultats.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setIndex((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      resultats[index]?.aller();
    } else if (e.key === 'Tab') {
      // Le focus reste dans la palette.
      e.preventDefault();
    }
  }

  useEffect(() => {
    liste.current
      ?.querySelector<HTMLElement>(`[data-index="${index}"]`)
      ?.scrollIntoView({ block: 'nearest' });
  }, [index]);

  let groupeCourant = '';
  return (
    <>
      <div className="veil" onClick={onFermer} aria-hidden />
      <div
        className="palette pop"
        role="dialog"
        aria-modal="true"
        aria-label="Rechercher dans le panel"
        onKeyDown={surTouche}
      >
        <div className="pk-in">
          <Icone nom="search" />
          <input
            ref={champ}
            value={saisie}
            onChange={(e) => setSaisie(e.target.value)}
            placeholder="Pseudo, Discord, UUID, nom de personnage, page…"
            role="combobox"
            aria-expanded="true"
            aria-controls="palette-resultats"
            aria-activedescendant={resultats[index] ? `pk-${index}` : undefined}
            aria-autocomplete="list"
            spellCheck={false}
            autoComplete="off"
          />
          {enCours && <span className="spin text-fg-3" aria-hidden />}
          <Kbd>Échap</Kbd>
        </div>
        <div className="pk-l" id="palette-resultats" role="listbox" ref={liste}>
          {resultats.length === 0 ? (
            <div className="px-3 py-6 text-center text-[13px] text-fg-3">
              {assez && !enCours ? `Rien ne correspond à « ${q} ».` : 'Tape au moins deux lettres.'}
            </div>
          ) : (
            resultats.map((r, i) => {
              const entete = r.groupe !== groupeCourant;
              groupeCourant = r.groupe;
              return (
                <div key={r.cle}>
                  {entete && <div className="pk-g">{r.groupe}</div>}
                  <button
                    type="button"
                    id={`pk-${i}`}
                    data-index={i}
                    role="option"
                    aria-selected={i === index}
                    className="pk-i"
                    onMouseEnter={() => setIndex(i)}
                    onClick={r.aller}
                    tabIndex={-1}
                  >
                    {r.visage ? (
                      <MCAvatar uuid={r.visage} size={26} />
                    ) : (
                      <span className="pk-ic">
                        <Icone nom={r.icone ?? 'chevron-right'} taille={15} />
                      </span>
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="pk-m block truncate">{surligne(r.titre, q)}</span>
                      {r.sous && <span className="pk-s block truncate">{r.sous}</span>}
                    </span>
                    {i === index && <Kbd>Entrée</Kbd>}
                  </button>
                </div>
              );
            })
          )}
        </div>
        <div className="pk-f">
          <span className="inline-flex items-center gap-1">
            <Kbd>↑</Kbd>
            <Kbd>↓</Kbd> choisir
          </span>
          <span className="inline-flex items-center gap-1">
            <Kbd>Entrée</Kbd> ouvrir
          </span>
          <span className="ml-auto">Pseudo, Discord, UUID ou personnage</span>
        </div>
      </div>
    </>
  );
}
