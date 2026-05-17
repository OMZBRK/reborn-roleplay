import type { Metadata } from 'next';
import { QueryProvider } from './providers';
import './globals.css';

export const metadata: Metadata = {
  title: 'Reborn — Panel Staff',
  description: 'Outils staff Reborn Roleplay : whitelist, tickets, moderation.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr">
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
