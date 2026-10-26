import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Bundle minimal pour Docker : copie seulement les chunks runtime
  // necessaires dans .next/standalone (vs traîner tout node_modules).
  output: 'standalone',
  // Mode démo (dev/demo, `next dev` seulement) : pas de pastille de
  // développement de Next, elle masquerait le pied de la barre latérale sur
  // les captures.
  ...(process.env.NEXT_PUBLIC_PANEL_DEMO === '1' ? { devIndicators: false as const } : {}),
};

export default nextConfig;
