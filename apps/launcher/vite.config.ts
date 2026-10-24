import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

const host = process.env.TAURI_DEV_HOST;

// Découpage des dépendances en chunks stables : le code applicatif change à
// chaque version, les vendors presque jamais. Les pages sont déjà chargées
// à la demande (React.lazy dans App.tsx) ; ici on évite qu'un vendor lourd
// partagé (framer-motion, marked…) soit dupliqué ou collé au chunk d'entrée.
const VENDOR_CHUNKS: Array<[chunk: string, packages: string[]]> = [
  ["react", ["react", "react-dom", "react-router", "scheduler", "cookie", "set-cookie-parser"]],
  ["motion", ["framer-motion", "motion-dom", "motion-utils"]],
  ["tauri", ["@tauri-apps"]],
  ["markdown", ["marked"]],
  ["zod", ["zod"]],
  // Icônes : un seul chunk (tree-shaké) plutôt qu'une poussière de
  // micro-chunks partagés entre pages.
  ["icons", ["lucide-react"]],
];

function vendorChunk(id: string): string | undefined {
  const i = id.lastIndexOf("node_modules/");
  if (i === -1) return undefined;
  const rest = id.slice(i + "node_modules/".length);
  // Nom du paquet : "@scope/nom" ou "nom".
  const parts = rest.split("/");
  const pkg = rest.startsWith("@") ? `${parts[0]}/${parts[1]}` : parts[0];
  for (const [chunk, packages] of VENDOR_CHUNKS) {
    if (packages.some((p) => pkg === p || pkg.startsWith(`${p}/`))) return chunk;
  }
  return undefined;
}

// https://vite.dev/config/
export default defineConfig(async () => ({
  plugins: [react(), tailwindcss()],

  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },

  build: {
    // WebView2 (Chromium récent) uniquement : pas de transpilation inutile.
    target: "chrome120",
    // Builds de debug Tauri : sourcemaps + code lisible pour diagnostiquer.
    minify: process.env.TAURI_ENV_DEBUG ? false : ("esbuild" as const),
    sourcemap: Boolean(process.env.TAURI_ENV_DEBUG),
    rollupOptions: {
      output: {
        manualChunks: (id: string) => vendorChunk(id.replace(/\\/g, "/")),
      },
    },
  },

  // Vite options tailored for Tauri development and only applied in `tauri dev` or `tauri build`
  //
  // 1. prevent Vite from obscuring rust errors
  clearScreen: false,
  // 2. tauri expects a fixed port, fail if that port is not available
  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host
      ? {
          protocol: "ws",
          host,
          port: 1421,
        }
      : undefined,
    watch: {
      // 3. tell Vite to ignore watching `src-tauri`
      ignored: ["**/src-tauri/**"],
    },
  },
}));
