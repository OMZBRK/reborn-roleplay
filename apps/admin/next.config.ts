import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Bundle minimal pour Docker : copie seulement les chunks runtime
  // necessaires dans .next/standalone (vs traîner tout node_modules).
  output: 'standalone',
};

export default nextConfig;
