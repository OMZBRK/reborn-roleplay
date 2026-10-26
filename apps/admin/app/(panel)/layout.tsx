'use client';

import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { MCAvatar } from '@/components/MCAvatar';
import { CommandPalette } from '@/components/shell/CommandPalette';
import { RaccourcisGlobaux } from '@/components/shell/Raccourcis';
import { Sidebar } from '@/components/shell/Sidebar';
import { Icone } from '@/components/ui/Icone';
import { Kbd } from '@/components/ui/Kbd';
import { ErrorState } from '@/components/ui/States';
import { useCompteursMenu } from '@/lib/a-traiter';
import { clearTokens, isAuthenticated } from '@/lib/auth';
import { useDroits } from '@/lib/droits';
import { pageArrivee, sectionsPermises } from '@/lib/navigation';
import logo from '@/components/shell/logo.png';

/**
 * Coquille du panel v2 (PANEL_V2 §3.2, §9) : barre latérale à six sections +
 * Réglages, palette Ctrl K, raccourcis globaux. Sous 1 024 px la barre
 * devient un tiroir ouvert par le bouton menu de la barre du haut ; sur
 * téléphone (moins de 768 px), une barre du bas garde À traiter · Rechercher ·
 * Moi sous le pouce.
 */
export default function PanelLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const chemin = usePathname();
  const qc = useQueryClient();
  const [pret, setPret] = useState(false);
  const [menu, setMenu] = useState(false);
  const [palette, setPalette] = useState(false);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace('/');
      return;
    }
    setPret(true);
  }, [router]);

  // Le tiroir se referme à chaque changement de page.
  useEffect(() => setMenu(false), [chemin]);

  function deconnecter() {
    clearTokens();
    qc.clear();
    router.replace('/');
  }

  if (!pret) return null;

  return (
    <div className="app-shell">
      <a href="#contenu" className="skip-link">
        Aller au contenu
      </a>
      <Sidebar
        ouvert={menu}
        onNaviguer={() => setMenu(false)}
        onRechercher={() => {
          setMenu(false);
          setPalette(true);
        }}
        onDeconnecter={deconnecter}
      />
      {menu && <div className="veil lg:hidden" onClick={() => setMenu(false)} aria-hidden />}
      <div className="min-w-0">
        <div className="topbar">
          <button
            type="button"
            className="btn btn-icon btn-ghost"
            aria-label="Ouvrir le menu"
            aria-expanded={menu}
            onClick={() => setMenu(true)}
          >
            <Icone nom="menu" />
          </button>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={logo.src} alt="" width={24} height={24} className="rounded-[6px]" />
          <span className="side-brand-t text-[18px]">Reborn</span>
          <button
            type="button"
            className="side-search ml-auto mb-0 w-auto min-w-[44px]"
            onClick={() => setPalette(true)}
            aria-label="Rechercher"
          >
            <Icone nom="search" taille={16} />
            <span className="hidden sm:inline">Rechercher…</span>
            <span className="hidden md:inline-flex">
              <Kbd>Ctrl K</Kbd>
            </span>
          </button>
        </div>
        <main id="contenu" className="main" tabIndex={-1}>
          <GardeProfil>
            <Suspense fallback={null}>{children}</Suspense>
          </GardeProfil>
        </main>
      </div>
      <BarreBas onRechercher={() => setPalette(true)} />
      <CommandPalette ouvert={palette} onFermer={() => setPalette(false)} />
      <RaccourcisGlobaux onPalette={() => setPalette((v) => !v)} />
    </div>
  );
}

/**
 * Barre du bas, téléphone seulement (PANEL_V2 §9) : À traiter (ou la section
 * d'arrivée d'un grade sans file), Rechercher (palette), Moi (Réglages ›
 * Compte). Le reste passe par le tiroir.
 */
function BarreBas({ onRechercher }: { onRechercher: () => void }) {
  const chemin = usePathname() ?? '';
  const { moi, capacites, charge } = useDroits();
  const compteurs = useCompteursMenu();
  if (charge || !moi) return null;
  const file = sectionsPermises(capacites).find((s) => s.cle === 'a-traiter');
  const accueil = file ? '/dashboard' : pageArrivee(capacites);
  const n = file ? (compteurs['a-traiter']?.n ?? 0) : 0;
  const retard = file ? !!compteurs['a-traiter']?.retard : false;
  const surAccueil = chemin === accueil || (file !== undefined && chemin.startsWith('/inbox'));
  const surMoi = chemin === '/settings';
  return (
    <nav className="barre-bas" aria-label="Accès rapide">
      <Link href={accueil} className="barre-bas-l" aria-current={surAccueil ? 'page' : undefined}>
        <span className="relative">
          <Icone nom={file ? 'inbox' : 'book-open'} />
          {n > 0 && (
            <span className={`barre-bas-n ${retard ? 'late' : ''}`} aria-hidden>
              {n > 99 ? '99+' : n}
            </span>
          )}
        </span>
        <span>{file ? 'À traiter' : 'Accueil'}</span>
        {n > 0 && <span className="sr-only">{`, ${n} en attente`}</span>}
      </Link>
      <button type="button" className="barre-bas-l" onClick={onRechercher}>
        <Icone nom="search" />
        <span>Rechercher</span>
      </button>
      <Link href="/settings" className="barre-bas-l" aria-current={surMoi ? 'page' : undefined}>
        <MCAvatar uuid={moi.minecraftUuid} size={22} />
        <span>Moi</span>
      </Link>
    </nav>
  );
}

/**
 * Le profil (et donc les capacités) doit être lu avant d'afficher une page :
 * sans lui, le panel ne sait pas ce qu'il peut montrer.
 */
function GardeProfil({ children }: { children: React.ReactNode }) {
  const { moi, charge, erreur } = useDroits();
  if (charge) return null;
  if (erreur && !moi) {
    return (
      <div className="page-narrow mt-10">
        <ErrorState erreur={erreur} onReessayer={() => window.location.reload()} />
      </div>
    );
  }
  return <>{children}</>;
}
