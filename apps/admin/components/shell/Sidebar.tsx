'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCompteursMenu } from '@/lib/a-traiter';
import { useDroits } from '@/lib/droits';
import { libelleGrade } from '@/lib/libelles';
import { pageArrivee, sectionActive, sectionsPermises } from '@/lib/navigation';
import { MCAvatar } from '@/components/MCAvatar';
import { Icone } from '@/components/ui/Icone';
import { Kbd } from '@/components/ui/Kbd';
import logo from './logo.png';

/**
 * Barre latérale (PANEL_V2 §3.2) : collante, 232 px ; rail d'icônes de
 * 64 px entre 1 024 et 1 279 px ; tiroir sous 1 024 px. Six sections
 * filtrées par capacités (aucune entrée qui mène à un refus), compteurs
 * rafraîchis toutes les 30 s, en rouge seulement au-delà du seuil de retard.
 * Le sélecteur de serveur n'apparaît qu'à partir de deux serveurs (lot L9).
 */
export function Sidebar({
  ouvert,
  onNaviguer,
  onRechercher,
  onDeconnecter,
}: {
  ouvert: boolean;
  onNaviguer: () => void;
  onRechercher: () => void;
  onDeconnecter: () => void;
}) {
  const chemin = usePathname() ?? '';
  const { moi, capacites } = useDroits();
  const compteurs = useCompteursMenu();
  const sections = sectionsPermises(capacites);
  const active = sectionActive(chemin);
  const reglagesActif = chemin === '/settings' || chemin.startsWith('/settings/');

  return (
    <aside className="side" data-ouvert={ouvert} aria-label="Navigation du panel">
      <Link href={pageArrivee(capacites)} className="side-brand" onClick={onNaviguer} aria-label="Panel staff Reborn">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={logo.src} alt="" width={30} height={30} />
        <div className="side-brand-text">
          <div className="side-brand-t">Reborn</div>
          <div className="side-brand-s">Panel staff</div>
        </div>
      </Link>

      <nav className="side-nav" aria-label="Sections">
        {sections.map((s) => {
          const c = compteurs[s.cle];
          const n = c && c.n > 0 ? c.n : null;
          return (
            <Link
              key={s.cle}
              href={s.permis[0]!.href}
              onClick={onNaviguer}
              className="side-link"
              aria-current={active?.cle === s.cle ? 'page' : undefined}
              aria-label={n !== null ? `${s.label}, ${n} en attente${c!.retard ? ', dont en retard' : ''}` : s.label}
              title={s.label}
            >
              <Icone nom={s.icone} />
              <span className="side-label">{s.label}</span>
              {n !== null && (
                <>
                  <span className={`side-count ${c!.retard ? 'late' : ''}`} aria-hidden>
                    {n}
                  </span>
                  <span className={`side-count-dot ${c!.retard ? 'late' : ''}`} aria-hidden />
                </>
              )}
            </Link>
          );
        })}
      </nav>

      <div className="side-foot">
        <button
          type="button"
          className="side-search"
          onClick={onRechercher}
          title="Rechercher · Ctrl K"
          aria-label="Rechercher (Ctrl K)"
        >
          <Icone nom="search" taille={16} />
          <span className="side-label">Rechercher…</span>
          <Kbd>Ctrl K</Kbd>
        </button>
        <nav aria-label="Réglages">
          <Link
            href="/settings"
            onClick={onNaviguer}
            className="side-link"
            aria-current={reglagesActif ? 'page' : undefined}
            aria-label="Réglages"
            title="Réglages"
          >
            <Icone nom="settings" />
            <span className="side-label">Réglages</span>
          </Link>
        </nav>
        <div className="side-me">
          {moi ? (
            <MCAvatar uuid={moi.minecraftUuid} size={30} />
          ) : (
            <span className="face" style={{ width: 30, height: 30 }} />
          )}
          <div className="side-me-text min-w-0">
            <div className="side-me-n truncate">
              {moi ? (moi.displayName ?? moi.minecraftUsername) : '…'}
            </div>
            <div className="side-me-g">{moi ? libelleGrade(moi.role) : ''}</div>
          </div>
          <button
            type="button"
            onClick={onDeconnecter}
            className="btn btn-icon btn-ghost"
            aria-label="Se déconnecter"
            title="Se déconnecter"
          >
            <Icone nom="log-out" taille={16} />
          </button>
        </div>
      </div>
    </aside>
  );
}
