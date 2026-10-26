import type { Metadata } from 'next';
import localFont from 'next/font/local';
import { QueryProvider } from './providers';
import './globals.css';

/**
 * Polices auto-hébergées, copiées du launcher (PANEL_V2 §5.3) : Inter
 * Variable pour tout le texte, Bebas Neue pour le titre de page et les
 * chiffres clés. next/font les embarque dans le build (.next/static), sans
 * dépendre du dossier public absent de l'image Docker.
 */
const inter = localFont({
  src: './fonts/inter-variable.woff2',
  variable: '--font-inter',
  weight: '100 900',
  display: 'swap',
});

const bebas = localFont({
  src: './fonts/bebas-neue.ttf',
  variable: '--font-bebas',
  weight: '400',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Panel staff · Reborn',
  description: 'Outils du staff de Reborn : candidatures, tickets, joueurs, contenu.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr" className={`${inter.variable} ${bebas.variable}`}>
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
