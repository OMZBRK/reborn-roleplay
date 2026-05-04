# Changelog — Design v2 (refonte launcher)

Refonte visuelle complète du launcher Reborn vers un style "anime ninja gaming"
inspiré de Zenkai. Travail réalisé en 5 étapes d'intégration (5.1 → 5.5) après
4 sessions Claude Design qui ont produit les artefacts dans
`reborn-design-prep/reference-screen/`.

## Étape 5.1 — Design tokens v2 (`globals.css`)

- Tokens v2 substitués au précédent design system :
  - **Surfaces** : background / surface / surface-elevated / surface-overlay /
    border / border-strong / muted
  - **Foreground** : foreground / foreground-subtle / foreground-muted
  - **Accent (Zenkai blue)** : accent / accent-hover / accent-pressed /
    accent-soft / accent-glow / accent-glow-strong
  - **Sémantique** : success / warning / danger + variantes `*-soft`
  - **RP roles** : aligné sur l'enum backend `LauncherUser.role`
    (player gris, whitelisted **violet**, helper vert, moderator bleu accent,
    admin rouge, owner or). Alias legacy `role-staff` / `role-founder`
    conservés pour compat des fichiers de référence Claude Design.
  - **Typography** : `--font-display` passe de Cinzel (serif) à
    **Bebas Neue** (display gaming) ; `--font-sans` reste Inter ;
    `--font-mono` reste JetBrains Mono.
  - **Radius scale complète** : sm 6 / md 10 / lg 14 / xl 20 / full 9999.
    `radius-card: 12` conservé en alias legacy.
  - **Shadows** : sm / md / lg + glow-accent + glow-accent-strong.
  - **Motion** : durations fast/base/slow + easings out/in-out/spring.
- Utility classes ajoutées :
  - `.reborn-radial-bg`, `.reborn-radial-bg-strong` (gradients radiaux subtils)
  - `.reborn-glow-accent`, `.reborn-glow-accent-strong` (box-shadow glow)
  - `.reborn-pattern-bg`, `.reborn-pattern-overlay` (vagues seigaiha en SVG inline)
  - `.reborn-grid-overlay` (grille fine pour surfaces techniques)
  - `.reborn-hover-lift` (micro-interaction transform + shadow)
- `index.html` met à jour le preconnect Google Fonts (Bebas Neue + Inter).

## Étape 5.2 — Home

- **`src/components/home/PlayButton.tsx`** : refonte visuelle 3 états
  (Locked / Primary / Downloading avec progress ring SVG normalisé via
  `pathLength="100"`). **Toute la logique réelle conservée** (checkUpdate,
  applyUpdate, launchGame, lifecycle events, DownloadModal). Caption sous le
  bouton branchée sur les vraies données (version manifest, bytes téléchargés,
  taille totale).
- **`src/components/home/HeroSection.tsx`** : kicker tracking 0.32em + titre
  2 lignes Bebas Neue avec accent bleu sur "sa propre destinée" + PlayButton
  + animations framer-motion staggered.
- **`src/components/home/NewsCard.tsx`** : card cliquable avec gradient
  background + dégradé bottom pour lisibilité + icône kicker + titre Bebas
  Neue + excerpt 2 lignes + lien fléché. Hover lift -4px + scale 1.02 + glow
  accent inset.
- **`src/lib/mock-data.ts`** : configuration des 3 slots Home (gradients
  bleu/vert/violet en CSS strings, icons Lucide, hrefs, link labels) +
  contenus fallback patch & whitelist (chaque état) + slot `NEWS_RP_CARD`
  (TODO : à brancher quand un endpoint Calendrier RP existera).
- **`src/routes/Home.tsx`** : recâblé sur le nouveau layout, mêmes fetches
  (`fetchPatchnotes` + `fetchWhitelistMe`), résolution de contenu via
  `resolvePatchContent` / `resolveWhitelistContent`.
- **`globals.css`** : ajout d'une section "Home page" avec `.reborn-hero-bg`
  (radial bleu plus marqué que `radial-bg-strong`) et `@keyframes
  reborn-pulse-glow` + classe `.reborn-pulse-glow` (animation idle, désactivée
  au hover qui bascule sur `--shadow-glow-accent-strong`).
- Suppression de l'ancien `src/components/PlayButton.tsx` (déplacé sous
  `components/home/`).

## Étape 5.3 — Sidebar

- **`src/components/sidebar/role.ts`** : type `RoleType` exporté
  (`"player" | "whitelisted" | "helper" | "moderator" | "admin" | "owner"`),
  `ROLE_META` (label + couleur via vars CSS), `mapRole(role)` qui convertit
  l'enum backend (`PLAYER` / `WHITELISTED` / `HELPER` / `MODERATOR` /
  `WHITELIST_REVIEWER` / `ADMIN` / `OWNER`) — `WHITELIST_REVIEWER` mappe sur
  `moderator`.
- **`src/components/sidebar/RoleBadge.tsx`** : pill compacte avec icône check,
  fond/bord en `color-mix(in oklab, ...)` à partir de la couleur du rôle
  (14% / 40% alpha — supporté par WebView2 récent / Tauri 2 sur Windows).
- **`src/components/sidebar/UserBlock.tsx`** : avatar circulaire avec gradient
  accent + glow + ring de statut online (vert pulse). `useAuthStore` pour
  pseudo (`displayName ?? minecraftUsername ?? "Joueur"`) et role mappé.
  Action row : Bell (badge notif si > 0), Settings (NavLink → `/settings`),
  Coins (gold-tinted box). Notifications + coins TODO.
- **`src/components/sidebar/NavItem.tsx`** : `<NavLink>` avec border-left 3px
  accent + glow quand actif, gradient bg actif, hover bg accent/4 + scale
  1.01, icône passe en accent-hover quand actif. Stagger via `animationDelay
  = 120 + i*35ms`.
- **`src/components/sidebar/NavSection.tsx`** : header uppercase
  tracking-[0.18em] + items, séparateur via padding-top conditionnel.
- **`src/components/sidebar/ServerStatusFooter.tsx`** : mini-card avec dot vert
  pulsé, capacité players/capacity en gros, barre de remplissage avec halo,
  ping/IP en font-mono, bouton "Se déconnecter" qui passe en danger au hover.
  Données toujours hardcodées (TODO endpoint `/v1/server/status`).
- **`src/components/Sidebar.tsx`** : réduit à un orchestrateur. 8 routes
  existantes regroupées en 3 sections (Principal / Communauté / Contenu).
  Largeur passée de 260 → 280px. `handleLogout` inchangé. Toute l'intégration
  React Router / `useAuthStore` préservée.
- **`globals.css`** : section "Sidebar" avec 4 nouvelles animations —
  `reborn-sidebar-slide-in` (400ms ease-out, mount), `reborn-nav-item-in`
  (360ms, stagger), `reborn-online-dot` (pulse 2.2s), `reborn-status-dot`
  (pulse + halo).

## Étape 5.4 — Lore + Règlement (3 niveaux)

- **`src/lib/content-data.tsx`** : mock data complète des sections Règlement
  (6 catégories : Roleplay / HRP / Lexique RP / RPK / Diplomatie / Clans avec
  leurs sections accordion respectives) et Lore (8 catégories : Monde Shinobi
  / Konoha / Suna / Uchiha / Senju / Nara / Akimichi / Hyuga avec leurs
  détails long-form). Types `Category`, `RulesSection`, `LoreSection`,
  `RulesCategory`, `LoreCategory`, `LoreDetail` exportés. Helpers
  `findRulesCategory` / `findLoreCategory`. **TODO** : remplacer par des
  appels API quand `/v1/rules/categories` et `/v1/lore/categories` existeront
  — le format API actuel (un seul markdown par doc) n'est plus compatible
  avec la structure 3-niveaux.
- **`src/components/content-pages/SplashHeader.tsx`** : niveau 1 — splash
  cinematic. Props `variant` (`"rules" | "lore"`), title, subtitle, badge
  top-right, mascot placeholder à droite, chevron-down centré. Animations
  Framer Motion : letter-spacing reveal sur le titre, slide depuis la droite
  pour le badge, fade pour le subtitle.
- **`src/components/content-pages/CategoryCard.tsx`** : card de catégorie avec
  badge numéroté top-right, silhouette SVG en filigrane à 18% opacity,
  filigrane grille fine + hover lift -3px + glow couleur du badge.
- **`src/components/content-pages/CategoryGrid.tsx`** : niveau 2 — grid
  3 colonnes (rules) ou 4 colonnes (lore), titre Bebas Neue avec mot accent
  coloré, bouton chevron-up pour revenir au splash.
- **`src/components/content-pages/Accordion.tsx`** : accordion avec animation
  d'ouverture via `grid-template-rows: 0fr → 1fr` (transition smooth
  height-auto), chevron qui pivote 180°, border + glow couleur de section
  quand ouvert.
- **`src/components/content-pages/ContentDetail.tsx`** : niveau 3 — page
  détail. Bouton retour, pill catégorie avec dot coloré, titre Bebas Neue.
  Variante `rules` : accordions (premier ouvert par défaut). Variante
  `lore` : long-form markdown-like (h2 avec barre + glow violet, h3, p,
  blockquote avec border-left + soft glow, timeline-pill).
- **`src/routes/Rules.tsx`** : refait — gère niveaux 1 & 2 via state
  interne. `useLocation().state?.level` permet de revenir directement sur la
  grid quand on revient depuis le détail.
- **`src/routes/RuleDetail.tsx`** : nouveau — niveau 3 pour `/rules/:slug`.
  Slug invalide → redirect vers `/rules` avec state level=2.
- **`src/routes/Lore.tsx`** : refait — même pattern que Rules.
- **`src/routes/LoreDetail.tsx`** : nouveau — niveau 3 pour `/lore/:slug`.
- **`src/App.tsx`** : ajout des routes `/rules/:slug` (`RuleDetail`) et
  `/lore/:slug` (`LoreDetail`).
- **`globals.css`** : 6 nouveaux tokens (`--color-lore`, `--color-lore-soft`,
  `--color-lore-glow`, `--color-rules`, `--color-rules-soft`,
  `--color-rules-glow`) + grosse section "Content pages" (~370 lignes) avec
  splash variants (gradients + patterns SVG inline : griffes pour rules,
  oiseaux pour lore), grid, cards, detail page, accordion (transition
  grid-template-rows pour height-auto smooth), lore-doc (h2 avec barre +
  glow, blockquote, timeline-pill, etc.).
- `react-markdown` **non installé** : la mock data est du HTML pur
  (`<b>` / `<em>` / `<ul>` / `<h3>` / `<blockquote>`), rendu via
  `dangerouslySetInnerHTML` comme dans le mockup d'origine. Le package
  `marked` (déjà installé pour `Markdown.tsx` utilisé par Patchnotes) prendra
  le relais quand l'API renverra du vrai markdown.
- Anciens `Lore.tsx` et `Rules.tsx` (qui appelaient `fetchLore()` /
  `fetchRules()` et rendaient un seul document markdown via `<Markdown />`)
  remplacés. Les exports `fetchLore` / `fetchRules` / `LoreDocument` /
  `RulesDocument` dans `lib/content.ts` sont **conservés** (orphelins pour
  l'instant) en attendant la refonte API qui exposera les catégories.

## Étape 5.5 — Cleanup final

### Accents UI corrigés
Strings utilisateur visibles passées de l'ASCII pur vers l'orthographe
française correcte (Bebas Neue + Inter via Google Fonts rendent les accents
correctement, contrairement à l'ancien Cinzel) :
- `Settings.tsx` : Parametres → Paramètres ; Se deconnecter → Se déconnecter ;
  "Aucune liaison detectee apres 5 min" + "Si tu as valide cote Discord" +
  "Rafraichir" → version accentuée ; "Memoire allouee a Java" + "recommande" →
  version accentuée.
- `Whitelist.tsx` : "Pour acceder au serveur, soumets ta candidature au staff.
  Reponse sous 48h." → version accentuée.
- `DownloadModal.tsx` : Mise a jour → Mise à jour ; Telechargement →
  Téléchargement ; "coupee si tu fermes la fenetre" + "verifies par signature"
  → version accentuée.
- `App.tsx` PlaceholderPage : "implementee" → "implémentée" + ajout commentaire
  TODO sur Boutique / Documentation.
- `Tickets.tsx` : Categorie → Catégorie.
- `mock-data.ts` : harmonisation des liens whitelist (REJECTED &
  NEEDS_REVISION).

### Imports / orphelins
- TypeScript strict (`noUnusedLocals`, `noUnusedParameters`) + `tsc -b` →
  ✅ zéro erreur sur l'ensemble du launcher après refonte.
- `lib/content.ts` : `fetchRules` / `fetchLore` / `RulesDocument` /
  `LoreDocument` conservés en orphelins, à supprimer ou retravailler quand
  l'API exposera les catégories.

### Placeholders restants
- `/shop` (Boutique) et `/docs` (Documentation) → routés sur `PlaceholderPage`
  (App.tsx). TODO commenté.

### WhitelistPage
- Aucun log PowerShell présent (déjà clean, le mock initial du dev a été
  retiré avant cette refonte).

### Outils
- `tsc -b` : ✅ pass
- `eslint` : non installé dans `apps/launcher` (cf CLAUDE.md — la config
  ESLint+Prettier est sur l'agenda mais pas dans la branche actuelle). Le
  script `pnpm lint` est défini dans `package.json` mais inutilisable.

## Notes pour la suite

- **Mascotte RP** : actuellement placeholder dashed dans `SplashHeader`. Quand
  l'asset arrivera, remplacer le bloc `.reborn-splash-mascot` par une image.
- **API content** : le format un-seul-markdown des endpoints `rules_current`
  et `lore_current` ne match plus la structure 3-niveaux. Refondre côté
  `apps/api` pour exposer les catégories individuellement (un seed.ts
  alimentera la prod).
- **Calendrier RP** : la 3ème card de la Home (slot `NEWS_RP_CARD`) est
  100% mockée. À brancher sur un endpoint dédié quand le calendrier RP sera
  modélisé en base.
- **Server status** : `Sidebar / ServerStatusFooter` affiche des données
  hardcodées (23/200, ping 28ms, `play.reborn-rp.fr`). À brancher sur
  `/v1/server/status` (nouvel endpoint à créer).
- **Notifications + coins** : 0 hardcodé dans `UserBlock`. À brancher quand
  les modèles de notifications et de monnaie virtuelle seront stabilisés.
- **`color-mix(in oklab, …)`** dans `RoleBadge` : nécessite WebView2 ≥
  Chromium 111. Tauri 2 sur Windows 11 utilise la version auto-update de
  WebView2, OK en pratique. Si un user signale un badge sans couleur,
  fallback possible : précalculer les hex+alpha côté JS dans `ROLE_META`.
- **Bebas Neue mono-poids** : la font n'a qu'un seul poids (regular). Les
  classes `font-semibold` / `font-bold` Tailwind appliquées à du
  `font-display` n'ont aucun effet visuel. Pas un blocker, à nettoyer
  opportunistement plus tard.
