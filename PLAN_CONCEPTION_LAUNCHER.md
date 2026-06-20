# Plan de conception — Launcher Reborn Roleplay

> **Document de référence v1.4**  
> À utiliser comme contexte permanent dans Claude Code pendant le développement.  
> Ce document est la source de vérité du projet : toute décision technique non documentée ici doit être ajoutée avant implémentation.
>
> **Changelog**  
> v1.4 — Réécriture §9.4/§9.5 pour refléter le pivot vers play-token signé API (au lieu du challenge nonce + modlist_hash initial). Ajout §9.6 décrivant l'écosystème mods Reborn complet (HUD + OST). Mise à jour roadmap §11 MVP.  
> v1.3 — Restructuration des paramètres en onglets, ajout de Steam OAuth, nouvelle release v1.0.5 dédiée au social (amis + DM + mini-fenêtre)  
> v1.2 — Ajout des sections 14 (sécurité avancée), 15 (staff tooling Discord+panel), 16 (features inspirées d'autres launchers)  
> v1.1 — Ajout de la section 13 (Packaging & expérience d'installation)  
> v1.0 — Version initiale

---

## Table des matières

1. [Vision & objectifs](#1-vision--objectifs)
2. [Architecture globale](#2-architecture-globale)
3. [Stack technique](#3-stack-technique)
4. [Sécurité & anti-tampering](#4-sécurité--anti-tampering)
5. [Modèle de données](#5-modèle-de-données)
6. [Spécification des écrans](#6-spécification-des-écrans)
7. [Flow d'authentification Microsoft](#7-flow-dauthentification-microsoft)
8. [Flow de lancement Minecraft](#8-flow-de-lancement-minecraft)
9. [Système de mods : allowlist & vérification](#9-système-de-mods--allowlist--vérification)
10. [API Backend — endpoints](#10-api-backend--endpoints)
11. [Roadmap par phases](#11-roadmap-par-phases)
12. [Setup du projet & arborescence](#12-setup-du-projet--arborescence)
13. [Packaging & expérience d'installation](#13-packaging--expérience-dinstallation)
14. [Sécurité avancée — durcissement complet](#14-sécurité-avancée--durcissement-complet)
15. [Staff tooling : Panel web + Bot Discord](#15-staff-tooling--panel-web--bot-discord)
16. [Features inspirées des autres launchers](#16-features-inspirées-des-autres-launchers)
17. [Système social — amis, DM, mini-fenêtre (v1.0.5)](#17-système-social--amis-dm-mini-fenêtre-v105)
18. [Prochaines étapes immédiates](#18-prochaines-étapes-immédiates)

---

## 1. Vision & objectifs

### Vision produit
Un launcher desktop autonome pour le network **Reborn Roleplay**, dans la lignée visuelle de **Zenkai Launcher** (split design login + dashboard sidebar), qui remplace le client Minecraft officiel pour offrir :
- Une expérience visuelle premium qui renforce l'identité de la communauté
- Un environnement de jeu strictement contrôlé (mods, ressources, configs)
- Un point d'entrée unique pour toutes les interactions joueur (boutique, whitelist, lore, tickets…)

### Objectifs techniques non négociables
| Critère | Exigence |
|---|---|
| **Poids binaire** | ≤ 20 Mo installeur, ≤ 50 Mo après install (hors fichiers Minecraft) |
| **Sécurité** | Zero-trust : aucune action côté client n'est crue par défaut |
| **Auth** | Microsoft OAuth officiel uniquement (comptes premium) |
| **Intégrité du client** | Aucun fichier mod/config modifiable par le joueur sans détection |
| **Performance** | Démarrage à froid ≤ 2 s, lancement Minecraft optimisé |
| **Multi-OS** | Windows priorité 1 ; macOS et Linux supportés en v1.x |

### Hors scope (ne pas le coder)
- Pas de version web du launcher
- Pas de support des comptes "cracked" (offline-mode)
- Pas de gestion de plusieurs serveurs distincts (un seul serveur cible)

---

## 2. Architecture globale

```
┌────────────────────────────────────────────────────────────────────┐
│                   POSTE DU JOUEUR (Windows / Mac)                  │
│                                                                    │
│   ┌──────────────────────────────────────────────────────────┐     │
│   │              LAUNCHER REBORN (Tauri 2)                    │    │
│   │                                                           │    │
│   │   ┌────────────────┐         ┌────────────────────────┐   │    │
│   │   │  Frontend      │  IPC    │  Backend Rust          │   │    │
│   │   │  React + TS    │ ◀────▶  │  - MS OAuth            │   │    │
│   │   │  Tailwind      │         │  - Téléchargements     │   │    │
│   │   │  Framer Motion │         │  - SHA-256 / vérif     │   │    │
│   │   └────────────────┘         │  - Spawn JVM           │   │    │
│   │                              │  - FS watcher          │   │    │
│   │                              └────────┬───────────────┘   │    │
│   └──────────────────────────────────────│───────────────────┘    │
│                                          │                        │
│       ┌──────────────────────────────────┴──────────┐             │
│       │  %APPDATA%/RebornRoleplay/  (game dir isolé)│            │
│       │   ├── runtime/         (JRE bundlé)          │            │
│       │   ├── versions/        (jar + libs MC)       │            │
│       │   ├── mods/            (allowlist only)      │            │
│       │   ├── resourcepacks/   (RP custom)           │            │
│       │   ├── config/          (configs verrouillées)│            │
│       │   ├── options.txt      (généré par launcher) │            │
│       │   └── auth/            (tokens chiffrés)     │            │
│       └─────────────────────────────────────────────┘             │
└────────────────────────────────────────────────────────────────────┘
                            │  HTTPS
                            ▼
┌────────────────────────────────────────────────────────────────────┐
│                          INFRASTRUCTURE                            │
│                                                                    │
│   ┌──────────────────┐    ┌─────────────────┐   ┌──────────────┐   │
│   │  API Backend     │    │  CDN Statique   │   │  Serveur MC  │   │
│   │  NestJS + TS     │    │  (S3 / R2 /     │   │  Paper +     │   │
│   │  PostgreSQL      │    │   Bunny.net)    │   │  Plugin RP   │   │
│   │                  │    │                 │   │              │   │
│   │  - /auth/*       │    │  - mods/*.jar   │   │  Vérifie le  │   │
│   │  - /manifest     │    │  - assets/*     │   │  modlist au  │   │
│   │  - /patchnotes   │    │  - rp/*.zip     │   │  join via    │   │
│   │  - /whitelist    │    │  - launcher/    │   │  Plugin Msg  │   │
│   │  - /shop         │    │    updates/     │   │              │   │
│   │  - /tickets      │    │                 │   │              │   │
│   └──────────────────┘    └─────────────────┘   └──────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

### Principe directeur : la triple validation
Tout fichier critique (mod, config) est validé **trois fois** :
1. **Au téléchargement** : le launcher vérifie le hash contre le manifest signé reçu de l'API
2. **Au lancement** : re-vérification de tous les hashs avant de démarrer la JVM
3. **À la connexion serveur** : le plugin Paper demande au client (via Plugin Messaging) la liste des mods chargés et leurs hashs ; mismatch → kick

Cette triple validation rend l'injection d'un mod non autorisé pratiquement impossible sans compromettre simultanément le launcher, l'API et le serveur.

---

## 3. Stack technique

### 3.1 Launcher (desktop)

| Couche | Choix | Justification |
|---|---|---|
| **Framework** | Tauri 2.x | Binaire ~10 Mo, backend Rust sécurisé, WebView natif système |
| **Frontend** | React 18 + TypeScript 5 + Vite | Standard moderne, excellent DX, type safety |
| **Styling** | TailwindCSS 4 + tailwind-merge | Reproduit le style Zenkai rapidement, classes utilitaires |
| **Animations** | Framer Motion 11 | Transitions fluides entre écrans (style Zenkai) |
| **State** | Zustand 5 | Léger, simple, pas de boilerplate Redux |
| **Routing** | React Router 7 | Navigation entre les vues du dashboard |
| **Forms** | React Hook Form + Zod | Validation type-safe des formulaires (login, register, whitelist) |
| **Icons** | Lucide React | Cohérent avec le look Zenkai (icônes ligne fines) |
| **HTTP client (front)** | Tauri HTTP API (via Rust) | Pas d'appels HTTP directs depuis le webview = plus sécurisé |

### 3.2 Backend Rust (intégré au launcher)

```toml
# Cargo.toml — dépendances clés
tauri = { version = "2", features = ["protocol-asset"] }
tauri-plugin-shell = "2"
tauri-plugin-fs = "2"
tauri-plugin-dialog = "2"
tauri-plugin-updater = "2"
tauri-plugin-store = "2"          # stockage clé-valeur chiffré
tauri-plugin-stronghold = "2"     # tokens auth chiffrés (recommandé)

tokio = { version = "1", features = ["full"] }
reqwest = { version = "0.12", features = ["json", "stream", "rustls-tls"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"

sha2 = "0.10"                     # vérification d'intégrité
hex = "0.4"
zip = "2"                         # extraction de resource packs
notify = "7"                      # filesystem watcher anti-tampering

oauth2 = "5"                      # Microsoft OAuth flow
url = "2"
keyring = "3"                     # fallback stockage tokens OS

thiserror = "2"                   # gestion d'erreurs propre
tracing = "0.1"                   # logging structuré
tracing-subscriber = "0.3"
```

### 3.3 API Backend

| Couche | Choix | Justification |
|---|---|---|
| **Framework** | NestJS 11 + TypeScript | Architecture modulaire, DI, validation native, parfait pour API métier |
| **ORM** | Prisma 6 | Type safety end-to-end, migrations versionnées |
| **Base de données** | PostgreSQL 16 | Robuste, transactions ACID, JSON natif |
| **Cache / sessions** | Redis 7 | Sessions, rate limiting, jobs en file |
| **Auth API** | JWT (RS256) + refresh tokens | Tokens signés asymétriquement, rotation automatique |
| **Validation** | class-validator + class-transformer | Validation déclarative des DTOs |
| **Rate limiting** | @nestjs/throttler | Anti-bruteforce sur endpoints sensibles |
| **Logs** | Pino | Performance, structuré JSON |
| **Déploiement** | Docker + docker-compose | Reproductible, simple à scaler |

### 3.4 Serveur Minecraft

| Couche | Choix | Justification |
|---|---|---|
| **Core** | Paper 1.21.x | Fork performant de Spigot, plus d'API |
| **Plugin de vérif** | Plugin custom Java/Kotlin | Vérifie modlist via Plugin Messaging, kick si mismatch |
| **Online-mode** | `true` (obligatoire avec MS OAuth) | Validation native Mojang des sessions |
| **Whitelist** | Gérée via API + plugin sync | Le plugin lit la whitelist depuis l'API |

### 3.5 CDN

Recommandation : **Bunny.net** (Storage Zone + Pull Zone) ou **Cloudflare R2** + **Cloudflare CDN**.
- Coût négligeable au volume d'un launcher RP
- Latence faible en Europe
- Support des Range requests (téléchargements reprenables)

---

## 4. Sécurité & anti-tampering

### 4.1 Modèle de menace
Adversaires considérés et mesures correspondantes :

| Menace | Risque | Contre-mesure |
|---|---|---|
| Joueur ajoute un mod dans `mods/` | Avantage compétitif, triche | FS watcher + revérif au lancement + vérif serveur |
| Joueur modifie un mod existant | Bypass via patch local | Hash SHA-256 vérifié 3 fois |
| Joueur intercepte les requêtes API | Vol de tokens | HTTPS strict + certificate pinning optionnel |
| Joueur extrait des tokens du disque | Usurpation | Stronghold (chiffré par mot de passe OS) ou keyring |
| Brute-force sur API auth | Compromission compte | Rate limiting agressif sur `/auth/*` |
| Distribution d'un launcher modifié | Communauté infectée | Updater Tauri avec signature Ed25519 |
| Extraction des assets RP propriétaires | Fuite contenu | Pas de protection technique réelle, accepter le risque |

### 4.2 Game directory isolé
**Règle absolue** : on ne touche JAMAIS au `.minecraft` standard. Tout est dans :

- Windows : `%APPDATA%\RebornRoleplay\`
- macOS : `~/Library/Application Support/RebornRoleplay/`
- Linux : `~/.local/share/RebornRoleplay/`

Cela garantit que :
- Le joueur peut continuer à utiliser Minecraft normalement à côté
- Notre environnement est complètement contrôlé
- Désinstaller le launcher = supprimer un seul dossier

### 4.3 Triple vérification d'intégrité

Chaque mod, chaque config, chaque resource pack a un **hash SHA-256** de référence dans un manifest signé.

**Manifest JSON exemple** (signé côté API) :
```json
{
  "version": "1.0.4",
  "minecraft_version": "1.21.4",
  "issued_at": "2026-05-03T12:00:00Z",
  "expires_at": "2026-05-10T12:00:00Z",
  "files": [
    {
      "path": "mods/sodium-fabric-0.6.2.jar",
      "sha256": "a3f5...e2c1",
      "size": 1234567,
      "url": "https://cdn.reborn-rp.fr/mods/sodium-fabric-0.6.2.jar",
      "required": true
    }
  ],
  "signature": "ed25519:9c4e...8b2a"
}
```

Le launcher embarque la **clé publique Ed25519** dans son binaire. Toute modif du manifest invalide la signature → refus.

### 4.4 Filesystem watcher (Rust)
Pendant que Minecraft tourne, un thread Rust utilise `notify` pour surveiller :
- `mods/` : tout fichier ajouté/modifié → kill JVM + alert UI + log incident
- `config/` : idem (les configs sont verrouillées par le launcher)

```rust
// Pseudo-code
use notify::{Watcher, RecommendedWatcher, RecursiveMode, Event};

fn watch_mods_directory(mods_path: &Path, jvm_pid: u32) {
    let (tx, rx) = std::sync::mpsc::channel();
    let mut watcher = notify::recommended_watcher(tx).unwrap();
    watcher.watch(mods_path, RecursiveMode::Recursive).unwrap();
    
    for event in rx {
        if let Ok(Event { kind, paths, .. }) = event {
            if is_suspicious(&kind) {
                kill_process(jvm_pid);
                report_tampering_incident(&paths);
                break;
            }
        }
    }
}
```

### 4.5 Stockage des tokens

**Ordre de préférence** :
1. **Tauri Stronghold** : coffre chiffré, mot de passe dérivé d'un secret embarqué + identifiant machine
2. **Keyring OS** : Windows Credential Manager / macOS Keychain / Secret Service
3. **JAMAIS en clair sur disque**

Les refresh tokens Microsoft sont rotés à chaque usage (Microsoft impose la rotation).

### 4.6 Validation côté serveur Minecraft

Le plugin Paper expose un canal Plugin Messaging custom : `reborn:integrity`.

**Au login** :
1. Le serveur envoie un défi (nonce 32 bytes) au client
2. Le client (mod système, voir §9.4) calcule HMAC-SHA256(nonce, modlist_hash) et répond
3. Le serveur compare avec le manifest courant
4. Mismatch → `Player#kick("Intégrité du client compromise")`

Cette étape rend la triche par mod non autorisé techniquement très coûteuse : il faudrait reverse l'algo de signature ET maintenir la cohérence avec un manifest qui change à chaque mise à jour.

### 4.7 Auto-update du launcher
Tauri Updater + signature Ed25519 :
- Mise à jour vérifiée cryptographiquement avant application
- Possibilité de forcer une version min côté API (l'écran "Mise à jour obligatoire" du Zenkai)
- Le launcher refuse de lancer Minecraft s'il est en dessous de la version min

---

## 5. Modèle de données

### 5.1 Schéma PostgreSQL (Prisma)

```prisma
// schema.prisma

model User {
  id                String   @id @default(uuid())
  msAccountId       String   @unique           // Microsoft Account ID (sub)
  minecraftUuid     String   @unique           // UUID Mojang
  minecraftUsername String                     // pseudo MC actuel
  email             String?  @unique
  createdAt         DateTime @default(now())
  lastLoginAt       DateTime?
  
  // Liaison Discord
  discordUserId     String?  @unique           // snowflake Discord
  discordUsername   String?                    // ex: "omz" (cache)
  discordLinkedAt   DateTime?
  
  // Liaison Steam (optionnelle)
  steamId           String?  @unique           // SteamID64
  steamUsername     String?                    // displayName Steam
  steamLinkedAt     DateTime?
  
  // Liaison Twitch (optionnelle, pour streamers)
  twitchUserId      String?  @unique
  twitchUsername    String?
  twitchLinkedAt    DateTime?
  
  // Profil
  displayName       String?                    // nom d'affichage custom
  avatarUrl         String?                    // photo de profil custom (sinon skin head MC)
  
  // Préférences
  newsletterOptIn   Boolean  @default(false)
  notifEvents       Boolean  @default(true)
  notifFriends      Boolean  @default(true)
  notifTickets      Boolean  @default(true)
  richPresenceOn    Boolean  @default(true)
  language          String   @default("fr")
  
  // Monnaie virtuelle (boutique deux-temps)
  zkCoinBalance     Int      @default(0)       // ZK Coin solde
  
  role              Role     @default(PLAYER)
  banned            Boolean  @default(false)
  banReason         String?
  bannedUntil       DateTime?
  
  // Sécurité
  twoFactorSecret   String?                    // TOTP secret (staff only)
  twoFactorEnabled  Boolean  @default(false)
  lastKnownIp       String?
  lastKnownCountry  String?                    // pour anomaly detection
  
  whitelistApp      WhitelistApplication?
  tickets           Ticket[]
  purchases         Purchase[]
  sessions          Session[]
  incidents         IntegrityIncident[]
  staffActions      AuditLog[] @relation("StaffActor")
  targetedActions   AuditLog[] @relation("TargetUser")
  
  // Système social (v1.0.5)
  friendshipsInitiated Friendship[] @relation("FriendshipFrom")
  friendshipsReceived  Friendship[] @relation("FriendshipTo")
  blockedUsers      UserBlock[] @relation("BlockerRelation")
  blockedByUsers    UserBlock[] @relation("BlockedRelation")
  sentMessages      DirectMessage[] @relation("MessageSender")
  receivedMessages  DirectMessage[] @relation("MessageReceiver")
  
  @@index([minecraftUuid])
  @@index([discordUserId])
  @@index([steamId])
}

enum Role {
  PLAYER          // joueur lambda
  WHITELISTED     // accepté en RP
  HELPER          // staff support niveau 1 (tickets)
  MODERATOR       // staff modération (kick/mute)
  WHITELIST_REVIEWER // staff dédié aux candidatures
  ADMIN           // accès complet sauf gestion staff
  OWNER           // accès total
}

model Session {
  id           String   @id @default(uuid())
  userId       String
  user         User     @relation(fields: [userId], references: [id])
  refreshToken String   @unique
  userAgent    String?
  ipAddress    String?
  createdAt    DateTime @default(now())
  expiresAt    DateTime
  revokedAt    DateTime?
}

model WhitelistApplication {
  id           String   @id @default(uuid())
  userId       String   @unique
  user         User     @relation(fields: [userId], references: [id])
  status       AppStatus @default(PENDING)
  characterName String
  characterAge Int
  background   String   @db.Text
  motivation   String   @db.Text
  submittedAt  DateTime @default(now())
  reviewedAt   DateTime?
  reviewedBy   String?
  reviewNotes  String?  @db.Text
}

enum AppStatus {
  PENDING
  APPROVED
  REJECTED
  NEEDS_REVISION
}

model Manifest {
  id               String   @id @default(uuid())
  version          String   @unique           // ex: "1.0.4"
  minecraftVersion String
  files            Json                       // structure du §4.3
  signature        String                     // Ed25519
  isCurrent        Boolean  @default(false)
  minLauncherVersion String                   // bloque les vieux launchers
  publishedAt      DateTime @default(now())
  
  @@index([isCurrent])
}

model PatchNote {
  id          String   @id @default(uuid())
  version     String
  title       String
  content     String   @db.Text             // markdown
  thumbnail   String?                       // URL CDN
  publishedAt DateTime @default(now())
  pinned      Boolean  @default(false)
}

model Ticket {
  id          String   @id @default(uuid())
  userId      String
  user        User     @relation(fields: [userId], references: [id])
  category    TicketCategory
  subject     String
  status      TicketStatus @default(OPEN)
  messages    TicketMessage[]
  createdAt   DateTime @default(now())
  updatedAt   DateTime @updatedAt
}

enum TicketCategory {
  BUG
  REPORT_PLAYER
  WHITELIST_APPEAL
  PURCHASE_ISSUE
  OTHER
}

enum TicketStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }

model TicketMessage {
  id        String   @id @default(uuid())
  ticketId  String
  ticket    Ticket   @relation(fields: [ticketId], references: [id])
  authorId  String
  content   String   @db.Text
  createdAt DateTime @default(now())
}

model ShopProduct {
  id          String   @id @default(uuid())
  sku         String   @unique
  name        String
  description String   @db.Text
  priceCents  Int                           // €0.01
  imageUrl    String
  category    String
  active      Boolean  @default(true)
  metadata    Json?                         // ex: items à donner in-game
}

model Purchase {
  id          String   @id @default(uuid())
  userId      String
  user        User     @relation(fields: [userId], references: [id])
  productId   String
  amountCents Int
  status      PurchaseStatus @default(PENDING)
  provider    String                        // "stripe" / "paypal"
  providerRef String?
  createdAt   DateTime @default(now())
  deliveredAt DateTime?
}

enum PurchaseStatus { PENDING, PAID, DELIVERED, REFUNDED, FAILED }

model IntegrityIncident {
  id          String   @id @default(uuid())
  userId      String?
  user        User?    @relation(fields: [userId], references: [id])
  kind        String                        // "mod_added" / "mod_modified" / "config_tampered"
  details     Json
  reportedAt  DateTime @default(now())
}

model AuditLog {
  id            String   @id @default(uuid())
  actorId       String                        // staff qui a fait l'action
  actor         User     @relation("StaffActor", fields: [actorId], references: [id])
  action        String                        // "whitelist.accept" / "user.ban" / "ticket.close" / "purchase.refund"
  targetUserId  String?
  targetUser    User?    @relation("TargetUser", fields: [targetUserId], references: [id])
  targetEntity  String?                       // "whitelist:uuid" / "ticket:uuid"
  metadata      Json                          // payload complet (ancienne et nouvelle valeur)
  ipAddress     String?
  userAgent     String?
  source        String                        // "panel" / "bot" / "api"
  createdAt     DateTime @default(now())
  
  // Hash chaîné pour immutabilité (chain comme une blockchain simplifiée)
  previousHash  String?
  hash          String   @unique
  
  @@index([actorId])
  @@index([targetUserId])
  @@index([action])
  @@index([createdAt])
}

model LoginAnomaly {
  id          String   @id @default(uuid())
  userId      String
  ipAddress   String
  country     String?
  reason      String                          // "new_country" / "new_ip" / "concurrent_session"
  acknowledged Boolean @default(false)        // joueur a confirmé "c'est moi"
  notifiedVia String[]                        // ["email", "discord"]
  createdAt   DateTime @default(now())
}

model ScheduledEvent {
  id          String   @id @default(uuid())
  title       String
  description String   @db.Text
  imageUrl    String?
  startsAt    DateTime
  endsAt      DateTime?
  category    String                          // "war" / "tournament" / "lore" / "social"
  factions    String[]                        // factions concernées
  visibility  EventVisibility @default(PUBLIC)
  createdAt   DateTime @default(now())
  
  @@index([startsAt])
}

enum EventVisibility { PUBLIC, WHITELISTED_ONLY, FACTION_ONLY }

// ──────────────────────────────────────────────────────
// SYSTÈME SOCIAL (v1.0.5)
// ──────────────────────────────────────────────────────

model Friendship {
  id          String   @id @default(uuid())
  fromUserId  String
  fromUser    User     @relation("FriendshipFrom", fields: [fromUserId], references: [id])
  toUserId    String
  toUser      User     @relation("FriendshipTo", fields: [toUserId], references: [id])
  status      FriendshipStatus @default(PENDING)
  createdAt   DateTime @default(now())
  acceptedAt  DateTime?
  
  @@unique([fromUserId, toUserId])
  @@index([fromUserId, status])
  @@index([toUserId, status])
}

enum FriendshipStatus { PENDING, ACCEPTED, DECLINED }

model UserBlock {
  id           String   @id @default(uuid())
  blockerId    String
  blocker      User     @relation("BlockerRelation", fields: [blockerId], references: [id])
  blockedId    String
  blocked      User     @relation("BlockedRelation", fields: [blockedId], references: [id])
  reason       String?
  createdAt    DateTime @default(now())
  
  @@unique([blockerId, blockedId])
}

model DirectMessage {
  id          String   @id @default(uuid())
  senderId    String
  sender      User     @relation("MessageSender", fields: [senderId], references: [id])
  receiverId  String
  receiver    User     @relation("MessageReceiver", fields: [receiverId], references: [id])
  content     String   @db.Text
  readAt      DateTime?
  editedAt    DateTime?
  deletedAt   DateTime?                       // soft delete
  createdAt   DateTime @default(now())
  
  @@index([senderId, receiverId, createdAt])
  @@index([receiverId, readAt])
}
```

---

## 6. Spécification des écrans

L'arborescence des écrans suit le modèle Zenkai. Les noms entre crochets sont les routes React Router.

### 6.1 Écrans non authentifiés

#### `/login` — Connexion
**Layout** : split 50/50, panneau gauche fond noir avec form, panneau droit artwork RP plein écran (image fixe ou parallax léger).

**Comportement** :
- Pas de form email/password — un seul bouton "Se connecter avec Microsoft"
- Click → ouvre une fenêtre OAuth Microsoft (flow §7)
- Succès → redirige vers `/home`
- Checkbox "Se souvenir de moi" → stocke le refresh token de manière persistante
- Lien "Pas de compte Microsoft ?" → ouvre `https://signup.live.com` dans le navigateur système
- Footer : version du launcher (ex: `v1.0.0`) en bas à droite

**États** :
- `idle` (initial)
- `authenticating` (spinner sur le bouton)
- `error` (toast d'erreur, conserve le bouton actif)

#### `/update-required` — Mise à jour obligatoire
Modal bloquante au-dessus du `/login`, identique au screenshot Zenkai.
- Affiche version actuelle / nouvelle version
- Bouton "Installer" lance l'updater Tauri
- Pas de "Plus tard" si la version min est dépassée

### 6.2 Écrans authentifiés

Layout commun : sidebar gauche fixe (260 px) + zone principale.

**Sidebar (haut → bas)** :
- Bloc utilisateur : avatar (skin head MC) + pseudo + badge rôle + cloche notifs + roue paramètres + compteur monnaie boutique
- Séparateur
- Liens de navigation (icône + label) :
  - 🏠 Accueil → `/home`
  - 🛒 Boutique → `/shop`
  - ✅ Whitelist → `/whitelist`
  - 📜 Règlement → `/rules`
  - 📖 Lore → `/lore`
  - 📰 Patch Notes → `/patchnotes`
  - 🎫 Tickets → `/tickets`
  - 📚 Documentation → `/docs`
- Footer sidebar : statut serveur (ping + nombre joueurs en ligne, refresh 30s)

**Zone principale haut** : background image RP + bouton de fenêtre (min/max/close, fenêtre frameless Tauri).

#### `/home` — Accueil
- Header artwork RP avec slogan ("Dans l'ombre ou la lumière, chaque ninja écrit sa propre destinée")
- **Bouton de lancement** central, 3 états :
  - `Inaccessible` (gris) : whitelist non validée OU version manquante
  - `Jouer` (vert/bleu) : prêt à lancer
  - `Téléchargement X%` : pendant DL/install
- Cartes en bas :
  - **Patch [version]** : last patch note (image + titre + extrait)
  - **Whitelist** : statut + bouton vers `/whitelist`
  - **Actus RP** : derniers événements RP (depuis API)
- Modal "Téléchargement en cours" si version pas à jour

#### `/shop` — Boutique
- Grid de produits (img + nom + prix + bouton "Acheter")
- Filtres par catégorie
- Modal détail produit
- Intégration paiement (Stripe Checkout en redirect navigateur) — **v1.1**

#### `/whitelist` — Whitelist
- Si pas encore postulé : formulaire (nom personnage, âge, background, motivation)
- Si en cours : statut "En attente de validation" + date soumission
- Si rejeté : feedback du staff + bouton "Modifier et resoumettre"
- Si validé : ✅ "Bienvenue dans le RP" + lien vers le règlement

#### `/rules` — Règlement
- Contenu markdown depuis l'API (versionné)
- Table des matières sticky à droite
- Bouton "J'ai lu et j'accepte" si pas encore validé (obligatoire pour whitelist)

#### `/lore` — Lore
- Pour le MVP : iframe vers le site web ou page markdown
- v1.x : système de timeline interactive, fiches personnages, factions

#### `/patchnotes` — Patch Notes
- Liste antéchronologique
- Click sur un patch → modal détail avec markdown + screenshots
- Patch épinglé en haut (latest)

#### `/tickets` — Tickets
- Liste des tickets de l'utilisateur (status, dernière activité)
- Bouton "Nouveau ticket" → form (catégorie, sujet, message)
- Détail ticket = thread de messages

#### `/docs` — Documentation
- Markdown depuis l'API, structuré par sections (commandes, jobs, factions, etc.)
- Recherche full-text côté client (Fuse.js)

#### `/settings` — Paramètres (page complète, pas modal)
La page Paramètres adopte une **structure à onglets** inspirée de Zenkai (4 onglets horizontaux avec underline animé). Chaque onglet a son propre contenu, scrollable indépendamment.

**Onglet 1 — `Profil`** (par défaut)
- **Photo de profil** : bouton `Changer` (upload JPG/PNG max 2 Mo) / `Supprimer`. Par défaut = head MC du joueur. Si custom, prévalue dans toute l'UI.
- **Nom d'affichage** : champ texte (différent du pseudo MC, sert dans les DM, le panel staff, les listes d'amis). Modifiable max 1× tous les 30 jours.
- **Langue** : sélecteur (FR/EN — EN en v1.x)
- **Newsletter** : toggle "Recevez les actus et événements RP par e-mail"

**Onglet 2 — `Compte`**
- **Adresse e-mail** : affichée en lecture seule (récupérée du profil Microsoft, badge "Microsoft"). Lien `Gérer mon compte Microsoft` → ouvre `account.microsoft.com` dans le navigateur système.
- **Pseudo Minecraft** : affiché en lecture seule + UUID (pour copy/paste si support)
- **Authentification 2FA** (visible uniquement si rôle ≥ HELPER) : statut activé/désactivé, bouton activer (QR code TOTP + 10 backup codes)
- **Sessions actives** : liste des sessions (device, IP masquée, dernière activité) avec bouton `Révoquer`
- **Version du launcher** : `v1.0.0` (badge)
- **Zone danger** :
  - `Télécharger mes données` (RGPD) → génère un export ZIP
  - `Supprimer mon compte` → flow de confirmation à plusieurs étapes

**Onglet 3 — `Connexions`**
Cartes de comptes liés, comme dans Zenkai (screen 2). Chaque carte : icône du service + username + ID + badge `Lié` (vert) ou bouton `Lier` (bleu).

| Service | Statut MVP | Pourquoi |
|---|---|---|
| **Discord** | ✅ Obligatoire pour whitelist | DM staff, rôles auto, notifs |
| **Steam** | 🟡 Optionnel | Anti-fraude (ancienneté du compte), recherche d'amis par SteamID, signal de confiance |
| **Twitch** | 🟡 Optionnel (v1.1+) | Pour les streamers — affichage "Live" dans le launcher, badge streamer |
| **Microsoft** | ✅ Obligatoire (= login) | Auth principale, non déliable |

Pour chaque compte lié non-obligatoire : bouton `Délier` avec confirmation. Cooldown de 30 jours avant nouvelle liaison sur le même service (anti-abus).

**Onglet 4 — `Jeu`**
- **Dossier d'installation** : chemin actuel (par défaut `%APPDATA%\RebornRoleplay\`), bouton `Modifier` (file picker). Migration automatique des fichiers si changement.
- **Version du jeu** : badge `1.21.4 (Reborn Build 1.0.4)` ou `Non installé` si premier lancement
- **Allocation RAM** : slider de 2 Go à RAM_max - 2 Go. Recommandation auto basée sur le hardware détecté (cf §16.1.2). Avertissement si l'utilisateur descend sous le minimum recommandé.
- **Résolution Minecraft** : presets (HD 720p / FullHD 1080p / 2K / 4K / Plein écran) ou personnalisée
- **JVM args avancés** : zone texte pour utilisateurs experts (avertissement "À vos risques")
- **Discord Rich Presence** : toggle on/off
- **Auto-connect au serveur** : toggle (par défaut ON)
- **Vérifier l'intégrité maintenant** : bouton qui force un re-check complet du manifest
- **Réinstaller le client** : bouton dangereux (confirmation), efface mods/configs/assets et re-télécharge tout
- **Ouvrir le dossier de jeu** : bouton qui ouvre `%APPDATA%\RebornRoleplay\` dans l'explorateur (utile pour le support)
- **Logs** : bouton `Exporter les logs` (zip à joindre aux tickets)

**Onglet 5 — `Notifications`** (v1.0.5+)
- Événements RP (rappel 30 min / 5 min avant)
- Demandes d'amis
- Messages directs
- Réponses aux tickets
- Achats livrés
- Anomalies de sécurité

Chaque type avec toggles séparés pour : in-launcher / Discord DM / e-mail.

---

## 7. Flow d'authentification Microsoft

### 7.1 Vue d'ensemble

Microsoft Authentication pour Minecraft = chaîne de **5 appels** :

```
1. Microsoft OAuth        →  ms_access_token
2. Xbox Live              →  xbl_token  
3. XSTS                   →  xsts_token + userhash
4. Minecraft Services     →  mc_access_token
5. Minecraft Profile      →  uuid + username + skins
```

### 7.2 Choix du flow OAuth

**Authorization Code Flow + PKCE** dans une fenêtre WebView Tauri dédiée.

Pourquoi pas Device Code ? → moins ergonomique (le joueur doit aller sur un autre site).
Pourquoi PKCE ? → obligatoire pour les apps publiques (pas de client_secret).

### 7.3 Étapes détaillées

```rust
// Pseudo-code Rust

// Étape 1 : générer le code_verifier + code_challenge (PKCE)
let verifier = generate_code_verifier();           // 43-128 chars random
let challenge = sha256_b64url(&verifier);

// Étape 2 : ouvrir la WebView sur l'URL d'autorisation
let auth_url = format!(
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?\
     client_id={CLIENT_ID}&\
     response_type=code&\
     redirect_uri=http://localhost:53682/callback&\
     scope=XboxLive.signin%20offline_access&\
     code_challenge={challenge}&\
     code_challenge_method=S256"
);
// Le launcher écoute sur localhost:53682 pour récupérer le code

// Étape 3 : échanger le code contre un token
let ms_tokens = http_post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", {
    "client_id": CLIENT_ID,
    "code": code,
    "grant_type": "authorization_code",
    "redirect_uri": "http://localhost:53682/callback",
    "code_verifier": verifier,
}).await?;
// → ms_tokens.access_token, ms_tokens.refresh_token

// Étape 4 : Xbox Live
let xbl = http_post("https://user.auth.xboxlive.com/user/authenticate", {
    "Properties": {
        "AuthMethod": "RPS",
        "SiteName": "user.auth.xboxlive.com",
        "RpsTicket": format!("d={}", ms_tokens.access_token),
    },
    "RelyingParty": "http://auth.xboxlive.com",
    "TokenType": "JWT"
}).await?;

// Étape 5 : XSTS
let xsts = http_post("https://xsts.auth.xboxlive.com/xsts/authorize", {
    "Properties": {
        "SandboxId": "RETAIL",
        "UserTokens": [xbl.token],
    },
    "RelyingParty": "rp://api.minecraftservices.com/",
    "TokenType": "JWT"
}).await?;
// → xsts.token, xsts.display_claims.xui[0].uhs (userhash)

// Étape 6 : Minecraft Services
let mc = http_post("https://api.minecraftservices.com/authentication/login_with_xbox", {
    "identityToken": format!("XBL3.0 x={};{}", userhash, xsts.token),
}).await?;
// → mc.access_token (le précieux)

// Étape 7 : récupérer le profil Minecraft
let profile = http_get("https://api.minecraftservices.com/minecraft/profile",
    bearer = mc.access_token
).await?;
// → profile.id (UUID), profile.name (pseudo)
```

### 7.4 Stockage et rafraîchissement

- **`ms_refresh_token`** : stocké dans Stronghold/keyring (long-lived, rotation à chaque usage)
- **`mc_access_token`** : en mémoire uniquement, expire en 24h
- Au redémarrage du launcher : on lit le refresh, on rejoue les étapes 3 → 7

### 7.5 Erreurs spécifiques Xbox/Minecraft

| Erreur XSTS | Signification | Action UI |
|---|---|---|
| `2148916233` | Pas de compte Xbox | "Crée d'abord un compte Xbox" + lien |
| `2148916235` | Pays interdit Xbox Live | Message d'erreur dédié |
| `2148916238` | Compte enfant non lié à un parent | Message + lien guide |
| Pas de profil MC | Pas de licence Minecraft | "Tu n'as pas Minecraft Java sur ce compte" |

### 7.6 Côté serveur API
Notre API ne stocke **jamais** le `ms_refresh_token`. Au lieu de ça :
- Le launcher envoie le `mc_access_token` à `/auth/login` de notre API
- L'API valide l'`access_token` côté Mojang (`/minecraft/profile`)
- Si OK → l'API émet **son propre JWT** lié au `minecraftUuid`
- Toutes les requêtes API ultérieures utilisent ce JWT

→ La compromission de l'API ne donne pas accès aux comptes Microsoft.

---

## 8. Flow de lancement Minecraft

### 8.1 Séquence complète

```
Click "Jouer"
    │
    ▼
[1] GET /api/manifest/current  ────────► récupère le manifest signé
    │
    ▼
[2] Vérification signature Ed25519 ──── si invalide : abort + erreur
    │
    ▼
[3] Diff manifest vs fichiers locaux
    │   ├─ Présent + hash OK → skip
    │   ├─ Manquant → à télécharger
    │   └─ Hash KO → à re-télécharger
    │
    ▼
[4] Téléchargement parallèle (max 4 simultanés)
    │   - Range requests pour reprise
    │   - Progress UI (modal "Téléchargement en cours X%")
    │   - Vérif hash après chaque DL
    │
    ▼
[5] Téléchargement runtime Java si absent
    │   - JRE Mojang via piston-meta (le launcher officiel utilise ça)
    │   - Stocké dans runtime/jre-21/
    │
    ▼
[6] Refresh du Minecraft access token (cf §7)
    │
    ▼
[7] Génération options.txt (verrouillage de certains paramètres)
    │
    ▼
[8] Construction de la commande JVM
    │
    ▼
[9] Spawn du processus Java + capture stdout/stderr
    │
    ▼
[10] Démarrage du FS watcher (mods/, config/)
    │
    ▼
[11] UI passe en mode "En jeu" (bouton désactivé, log viewer optionnel)
    │
    ▼
[12] Attente de la fin du processus
    │
    ▼
[13] Arrêt watcher + retour à l'écran d'accueil
```

### 8.2 Construction de la commande JVM

```
"runtime/jre-21/bin/java.exe"
  -Xmx{ramMo}M
  -Xms512M
  -XX:+UseG1GC
  -XX:+ParallelRefProcEnabled
  -XX:MaxGCPauseMillis=200
  -XX:+UnlockExperimentalVMOptions
  -XX:+DisableExplicitGC
  -XX:+AlwaysPreTouch
  -XX:G1NewSizePercent=30
  -XX:G1MaxNewSizePercent=40
  -XX:G1HeapRegionSize=8M
  -XX:G1ReservePercent=20
  -XX:G1HeapWastePercent=5
  -XX:G1MixedGCCountTarget=4
  -XX:InitiatingHeapOccupancyPercent=15
  -XX:G1MixedGCLiveThresholdPercent=90
  -XX:G1RSetUpdatingPauseTimePercent=5
  -XX:SurvivorRatio=32
  -XX:+PerfDisableSharedMem
  -XX:MaxTenuringThreshold=1
  -Djava.library.path={natives_dir}
  -Dminecraft.launcher.brand=reborn-launcher
  -Dminecraft.launcher.version={launcher_version}
  -cp {classpath_libs};{client_jar}
  net.minecraft.client.main.Main
  --username {minecraft_username}
  --version "Reborn 1.21.4"
  --gameDir {game_dir}
  --assetsDir {assets_dir}
  --assetIndex {asset_index}
  --uuid {minecraft_uuid}
  --accessToken {mc_access_token}
  --userType msa
  --versionType release
  --width {width}
  --height {height}
  --server {server_ip}     ← auto-connect direct au serveur
  --port {server_port}
```

### 8.3 Auto-connect au serveur
Les flags `--server` et `--port` font que Minecraft saute le menu principal et tente une connexion directe. Énorme gain UX.

### 8.4 Verrouillage des options
Le launcher écrase `options.txt` à chaque lancement avec :
- Resource packs forcés (RP du serveur en haut de la pile)
- Désactivation de certaines options si pertinent (ex: `cheats`, `chatLinks` selon le RP)
- Le joueur peut customiser le reste in-game (rebinds, FOV, etc.)

---

## 9. Système de mods : allowlist & vérification

### 9.1 Politique
**Aucun mod ajouté manuellement n'est toléré.** Seuls les mods listés dans le manifest courant sont chargés. Le manifest est la seule source de vérité.

### 9.2 Loader
Comme le serveur est **vanilla + plugins**, les mods doivent être :
- Soit purement client-side (Sodium, Iris Shaders, MiniHUD, etc.)
- Soit compatibles avec un serveur vanilla (donc aucun mod modifiant la mécanique)

**Recommandation : Fabric côté client**, vanilla côté serveur. Le launcher installe Fabric Loader et les mods autorisés. Le serveur ne sait rien de Fabric (il voit un client vanilla qui se connecte normalement).

### 9.3 Liste type d'allowlist (à valider avec ton équipe)

| Mod | Type | Justification |
|---|---|---|
| Fabric API | Lib | Requis par les autres |
| Sodium | Perf | Améliore les FPS sans gameplay |
| Lithium | Perf | Optimisation moteur, server-safe |
| Iris Shaders | Visuel | Shaders, esthétique RP |
| Mod Menu | UI | Gestion des mods (lecture seule pour l'utilisateur) |
| MiniHUD (config locked) | QoL | Coordonnées, FPS — config verrouillée |
| ReplayMod | Streaming | Pour les YT/streamers RP |
| Distant Horizons | Visuel | Immersion paysages |

À **bannir** explicitement (côté serveur : kick si détecté) :
- Toute mini-map révélant les joueurs
- Xray, freecam, fly client
- Optifine (préférer Sodium+Iris pour des raisons de licence et de modularité)

### 9.4 Mod système "Reborn Integrity"

> **Implémentation actuelle (depuis `f43ae65`)** — la spec initiale parlait d'un challenge nonce + HMAC sur `modlist_hash` envoyé par le serveur. L'implémentation a pivoté vers un **play-token signé par l'API** : c'est plus simple, plus difficile à forger côté client (le secret HMAC ne descend jamais), et compatible mod-loading dynamique.

Mod Fabric **client-side** embarqué obligatoirement, dont le rôle est limité à **prouver au serveur que le client a bien obtenu un play-token via l'API authentifiée** :

1. À l'init, lit la sysprop `reborn.playTokenPath` écrite par le launcher.
2. Charge le contenu du fichier (le play-token) en mémoire.
3. Enregistre le payload `AuthPayload` sur le canal C2S `reborn:auth`.
4. Sur `ClientPlayConnectionEvents.JOIN`, envoie le payload au serveur.

Le mod **ne voit jamais le secret HMAC** — il est passe-plat entre le launcher (qui pose le fichier) et le plugin Guardian (qui vérifie). Un client patché/recompilé ne peut donc pas forger d'attestation sans appeler l'API authentifiée d'abord.

Le hash du jar est dans le manifest signé Reborn, téléchargé par le launcher dans `<gameDir>/mods/`, donc impossible à substituer sans compromettre l'API ou le binaire du launcher.

**Périmètre du mod** : uniquement l'attestation. Toute la couche UI (menu principal custom, ESC menu, ConnectScreen, sub-screens, HUD in-game, chat custom) vit dans `mod-hud/` — cf §9.6.

### 9.5 Plugin serveur "Reborn Guardian"

Plugin Paper Java 21 qui vérifie le play-token côté serveur :

1. Sur `PlayerJoinEvent`, schedule un kick à T+8s (`PendingAuthListener`). 8s = marge empirique pour absorber le RTT du custom payload sur connexions résidentielles lentes.
2. Sur réception du payload sur le canal `reborn:auth` (`AuthChannelListener`) :
   - `PlayTokenVerifier.verify(token)` : recalcule HMAC-SHA256 avec `REBORN_PLAY_TOKEN_SECRET` (32+ chars, partagé uniquement entre API et plugin), vérifie `exp`, décode le payload JSON.
   - Vérification additionnelle : `payload.mcUuid == player.getUniqueId()` (sinon = token volé d'un autre joueur → kick).
   - Si tout passe, `AuthSessionState.markAuthenticated` annule le kick.
3. Log toute tentative échouée (à brancher sur la BDD API quand le webhook sera prêt).
4. *(TODO)* Synchronise la whitelist depuis l'API toutes les 60s.

**Pièges importants** :
- `MessageDigest.isEqual` (constant-time, JDK 6u17+) — **jamais** `Arrays.equals` qui short-circuite.
- `Player#kick` n'est pas thread-safe : re-poster sur le main thread via `Bukkit#getScheduler#runTask`.
- Le `runServer` Gradle ne lit pas le `.env` du monorepo — fallback dev hardcodé dans `build.gradle.kts`.

### 9.6 Mods écosystème Reborn

En complément de l'integrity loop (mod-integrity + plugin-guardian), trois mods/plugins additionnels constituent l'expérience Reborn côté Minecraft. Tous sont signés et listés dans le manifest, donc impossibles à substituer.

#### Reborn HUD (`minecraft/mod-hud`)

Mod Fabric client. Regroupe **toute la couche UI Reborn** côté jeu :

- **En jeu** :
  - Éditeur visuel drag/resize/hide pour tous les éléments HUD vanilla (chat, scoreboard, bossbar, action bar, hotbar, health/hunger/armor/air, experience bar). Touche **H** par défaut. Snapping/alignment guides, undo/redo, presets.
  - Chat custom Reborn : onglets, classifier de messages, détecteur de mentions, timestamps, dropdown quick-commands, écran de settings dédié.
- **Hors jeu (cible post-migration depuis `mod-integrity`)** :
  - TitleScreen custom : logo REBORN, background procédural ou `DynamicPlayerBackground` (scène 3D du joueur via MCEF), boutons Reborn empilés, server info card, lecteur OST UI, masque des entrées vanilla non pertinentes (Skin / RP / Realms).
  - ConnectScreen custom (`ConnectingRenderer`).
  - GameMenuScreen (ESC) refondu en 4 panels + community bar.
  - OptionsScreen redirigé vers `RebornOptionsScreen` (5 onglets) avec persistance dans `RebornPrefs`.
  - `RulesLoreScreen` (règlement + lore in-game).

Persistance dans `config/reborn-hud.json` (positions, presets, préférences chat).

#### Reborn OST (`minecraft/mod-ost` + `minecraft/plugin-ost`)

Système de BGM contextuelle pilotable par le serveur. 100% optionnel côté serveur vanilla (les clients sans le mod ignorent les plugin messages).

- **Côté client (`mod-ost`)** : décodage Ogg Vorbis via `STBVorbis` + lecture OpenAL directe. Scan `~/.minecraft/reborn/ost/<categorie>/<nom>.ogg`. Touche **M** ouvre `OstScreen`. Deux modes : Solo (contrôle local) ou broadcast (écoute le plugin).
  - Atténuation positionnelle **stéréo** calculée à la main (Phase 1) — bypass du distance model OpenAL qui refuse les buffers stéréo.
  - Late-join via `AL_SEC_OFFSET` (Phase 2).
- **Côté serveur (`plugin-ost`)** : 4 commandes (`/ost play|playat|playglobal|stop`), broadcast via plugin messaging `reborn:ost`. `OstZoneRegistry` + tick 1Hz pour late-join sync : un joueur qui rejoint une zone active reçoit un PLAY avec `secOffset = now - startedAtMs` calculé serveur-side.
- **UI lecteur OST in-menu** : pilote l'audio depuis `mod-hud` (séparation : `mod-ost` = audio, `mod-hud` = UI).

**Trade-off non encore tranché** : livraison des 43 .ogg côté joueur. Options :
- Embed dans le jar du mod (~74 MB, simple, force re-DL à chaque MAJ track).
- Archive séparée listée dans le manifest signé (jar léger, MAJ track sans rebuild, cache local, recommandé).

#### Recap responsabilités

| Mod/Plugin | Côté | Responsabilité |
|---|---|---|
| `mod-integrity` | Client | Attestation play-token au JOIN |
| `plugin-guardian` | Serveur | Vérification play-token + kick si invalide |
| `mod-hud` | Client | UI complète (menu, ESC, ConnectScreen, sub-screens, HUD in-game, chat) |
| `mod-ost` | Client | Décodage et lecture audio Ogg Vorbis |
| `plugin-ost` | Serveur | Broadcast OST + zone registry + late-join sync |

---

## 10. API Backend — endpoints

Toutes les routes sous `https://api.reborn-rp.fr/v1/`. Auth par JWT Bearer sauf indication.

### 10.1 Auth

| Méthode | Endpoint | Body | Auth | Description |
|---|---|---|---|---|
| POST | `/auth/login` | `{ mcAccessToken }` | Non | Valide le token MC, crée/maj user, retourne JWT + refresh |
| POST | `/auth/refresh` | `{ refreshToken }` | Non | Roule le refresh, retourne nouveau JWT |
| POST | `/auth/logout` | — | Oui | Révoque la session courante |
| GET | `/auth/me` | — | Oui | Retourne l'user courant |

### 10.2 Manifest & launcher

| Méthode | Endpoint | Body | Auth | Description |
|---|---|---|---|---|
| GET | `/manifest/current` | — | Oui | Manifest signé courant |
| GET | `/launcher/latest` | — | Non | Dernière version du launcher (pour updater Tauri) |
| POST | `/incidents` | `{ kind, details }` | Oui | Le launcher remonte un incident d'intégrité |

### 10.3 Whitelist

| Méthode | Endpoint | Body | Auth | Description |
|---|---|---|---|---|
| GET | `/whitelist/me` | — | Oui | Statut de la candidature |
| POST | `/whitelist` | `{ characterName, characterAge, background, motivation }` | Oui | Soumet une candidature |
| PATCH | `/whitelist/me` | partial | Oui | Modifie si statut = NEEDS_REVISION |

### 10.4 Patch notes / docs / lore / rules

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/patchnotes?page=1&size=10` | Oui | Liste paginée |
| GET | `/patchnotes/:id` | Oui | Détail |
| GET | `/docs` | Oui | Tree de documentation |
| GET | `/docs/:slug` | Oui | Page markdown |
| GET | `/rules/current` | Oui | Règlement actuel |
| GET | `/lore/timeline` | Oui | (v1.x) |

### 10.5 Tickets

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/tickets` | Oui | Mes tickets |
| POST | `/tickets` | Oui | Nouveau ticket |
| GET | `/tickets/:id` | Oui | Détail + messages |
| POST | `/tickets/:id/messages` | Oui | Ajouter un message |

### 10.6 Shop (v1.1)

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/shop/products` | Oui | Liste produits |
| POST | `/shop/checkout` | Oui | Crée une session Stripe |
| POST | `/shop/webhook/stripe` | (signature) | Webhook Stripe |
| GET | `/shop/purchases/me` | Oui | Mes achats |

### 10.7 Server status (public)

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/server/status` | Non | Players online, MOTD, ping (cache 30s) |
| GET | `/server/population/24h` | Oui | Graph population dernières 24h (cache 5 min) |

### 10.8 Discord (liaison joueur)

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/discord/oauth/url` | Oui | URL d'autorisation Discord OAuth |
| POST | `/discord/oauth/callback` | Oui | Échange code contre infos Discord, lie le compte |
| DELETE | `/discord/unlink` | Oui | Délie le compte Discord (cooldown 30j) |

### 10.9 Événements RP

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/events/upcoming?limit=10` | Oui | Prochains événements visibles par le joueur |
| GET | `/events/:id` | Oui | Détail d'un événement |
| POST | `/events/:id/subscribe` | Oui | Active la notif pour cet événement |
| DELETE | `/events/:id/subscribe` | Oui | Désactive la notif |

### 10.10 Statistiques joueur

| Méthode | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/me/stats` | Oui | Mes stats (temps de jeu, achievements, etc.) |
| GET | `/me/playtime` | Oui | Détail temps de jeu par jour/semaine |

### 10.11 Endpoints staff (`/staff/*`)
**Auth** : JWT valide + rôle suffisant + 2FA validée pour actions critiques.

| Méthode | Endpoint | Rôle min | Description |
|---|---|---|---|
| GET | `/staff/dashboard` | HELPER | KPIs synthétiques |
| GET | `/staff/whitelist?status=PENDING` | WL_REVIEWER | Liste des candidatures |
| GET | `/staff/whitelist/:id` | WL_REVIEWER | Détail candidature |
| POST | `/staff/whitelist/:id/accept` | WL_REVIEWER | Accepter (notes optionnelles) |
| POST | `/staff/whitelist/:id/reject` | WL_REVIEWER | Refuser (raison obligatoire) |
| POST | `/staff/whitelist/:id/request-revision` | WL_REVIEWER | Demander révision (précisions) |
| GET | `/staff/tickets?status=OPEN` | HELPER | Liste tickets |
| POST | `/staff/tickets/:id/messages` | HELPER | Répondre à un ticket |
| POST | `/staff/tickets/:id/close` | HELPER | Fermer ticket |
| POST | `/staff/tickets/:id/assign` | HELPER | Assigner à un staff |
| GET | `/staff/players/search?q=...` | HELPER | Recherche joueur |
| GET | `/staff/players/:id` | HELPER | Profil joueur complet |
| POST | `/staff/players/:id/ban` | MODERATOR | Bannir (durée + raison) |
| POST | `/staff/players/:id/mute` | MODERATOR | Mute |
| DELETE | `/staff/players/:id/ban` | ADMIN | Débannir |
| GET | `/staff/manifest` | ADMIN | Liste versions manifest |
| POST | `/staff/manifest` | ADMIN | Créer un nouveau manifest |
| POST | `/staff/manifest/:id/publish` | ADMIN | Publier (devient courant) |
| GET | `/staff/audit?actor=...&action=...` | ADMIN | Recherche audit log |
| GET | `/staff/incidents` | ADMIN | Liste incidents intégrité |
| GET | `/staff/anomalies` | ADMIN | Anomalies de login |
| POST | `/staff/events` | MODERATOR | Créer événement RP |

### 10.12 Webhooks (API → Bot Discord)
L'API envoie des webhooks signés HMAC vers le bot pour notifier d'événements. Le bot vérifie la signature avant traitement.

| Événement | Endpoint bot | Payload |
|---|---|---|
| Nouvelle candidature | `POST {BOT_URL}/webhooks/whitelist.submitted` | `{ applicationId, user, content }` |
| Candidature traitée | `POST {BOT_URL}/webhooks/whitelist.reviewed` | `{ applicationId, status, reason, reviewer }` |
| Nouveau ticket | `POST {BOT_URL}/webhooks/ticket.created` | `{ ticketId, user, category, subject }` |
| Message ticket | `POST {BOT_URL}/webhooks/ticket.message` | `{ ticketId, author, content }` |
| Joueur banni | `POST {BOT_URL}/webhooks/player.banned` | `{ userId, reason, duration, by }` |
| Achat boutique | `POST {BOT_URL}/webhooks/shop.purchased` | `{ purchaseId, user, product }` |
| Anomalie login | `POST {BOT_URL}/webhooks/security.anomaly` | `{ userId, kind, details }` |
| Incident intégrité | `POST {BOT_URL}/webhooks/security.incident` | `{ userId, kind, details }` |

### 10.13 Sécurité transversale
- Cloudflare en frontal (DDoS, WAF, Turnstile sur endpoints sensibles)
- Rate limit global : 60 req/min/IP
- Rate limit `/auth/*` : 10 req/min/IP
- Rate limit `/staff/*` : 120 req/min/user (plus généreux pour staff)
- HMAC obligatoire sur les requêtes (cf §14.2)
- 2FA obligatoire pour rôles `HELPER+` (cf §14.4)
- CORS strict : origin = `tauri://localhost` + domaines admin/web
- Headers : HSTS, CSP, X-Content-Type-Options, X-Frame-Options
- Toutes les entrées validées par class-validator
- Audit log immuable pour toutes les actions `/staff/*` (cf §14.5)

---

## 11. Roadmap par phases

### MVP (v1.0) — objectif : "ça marche, c'est sécurisé, c'est joli"
**Durée estimée** : 8-12 semaines à temps plein selon expérience.

| Module | Statut |
|---|---|
| Auth Microsoft OAuth complet | ✅ requis |
| Liaison Discord OAuth | ✅ requis |
| Écran login Zenkai-style | ✅ requis |
| Auto-update du launcher | ✅ requis |
| Téléchargement + vérif manifest signé | ✅ requis |
| Lancement Minecraft + auto-connect serveur | ✅ requis |
| Allowlist mods + FS watcher | ✅ requis |
| Plugin Paper Reborn Guardian | ✅ requis |
| Mod Reborn Integrity | ✅ requis |
| Mod Reborn HUD (UI menu + HUD in-game + chat custom) | ✅ requis |
| Mod Reborn OST + plugin (BGM contextuelle pilotée serveur) | ✅ requis |
| Sidebar + écran Accueil + cartes patch/whitelist/actus | ✅ requis |
| Page Configurations système (avec auto-détection) | ✅ requis |
| Whitelist (form + statut + Discord DM via bot) | ✅ requis |
| Patch Notes (lecture + grid view + détail markdown) | ✅ requis |
| Règlement (splash + grid catégories + accordion) | ✅ requis |
| Lore (grid + détail markdown) | ✅ requis |
| Tickets (avec sync Discord thread) | ✅ requis |
| Settings (RAM, résolution, Discord, 2FA, déco) | ✅ requis |
| API NestJS + Postgres + Redis + Cloudflare | ✅ requis |
| Panel staff Next.js (dashboard, whitelist, tickets, players) | ✅ requis |
| Bot Discord (commandes whitelist, tickets, ban, sync rôles) | ✅ requis |
| Discord Rich Presence | ✅ requis |
| Sécurité avancée (HMAC, 2FA staff, audit log, anomalies) | ✅ requis |
| Déploiement Docker | ✅ requis |
| Boutique (avec ZK Coin et Stripe) | ❌ v1.1 |
| Documentation (lecture) | ❌ v1.1 |
| Calendrier d'événements RP | ❌ v1.1 |
| Système d'amis + DM + mini-fenêtre | ❌ v1.0.5 |
| Liaison Steam | ❌ v1.0.5 |
| Skin manager | ❌ v1.2 |
| Stats joueur / profil | ❌ v1.2 |
| Achievements | ❌ v1.3 |

### v1.0.5 — Module social (juste après MVP)
**Durée estimée** : 3-4 semaines.
- Liaison Steam (OpenID)
- Système d'amis (demandes, accept/decline, block)
- Mini-fenêtre flottante (multi-window Tauri)
- DM temps réel (Socket.IO + Redis adapter)
- Présence en ligne / en jeu / absent / hors-ligne
- Recherche utilisateurs (MC pseudo, display name, SteamID)
- Invitations à rejoindre le serveur
- Modération des DM (filtre + signalement)

### v1.1 — Communauté & monétisation
- Boutique avec Stripe (achat ZK Coin → dépense en items)
- Documentation full
- Notifications in-launcher (cloche)
- Calendrier d'événements RP avec notifications
- Population serveur 24h en graph
- News carousel sur l'accueil
- Liaison Twitch (badge streamer + notif live)

### v1.2 — Engagement
- Lore interactif (timeline, fiches dynamiques)
- Profil joueur (succès, stats serveur)
- Skin manager 3D
- Système de parrainage

### v1.3 — Gamification
- Achievements / quêtes
- Leaderboards (factions, joueurs)
- Système de réputation entre factions
- Wiki RP collaboratif

### v2.0+
- Multi-langue (FR/EN/ES)
- Support macOS / Linux complet
- Mode mini-fenêtre pendant le jeu
- App mobile compagnon (consultation seulement, notifs events)
- Bug bounty program officiel

---

## 12. Setup du projet & arborescence

### 12.1 Repo monorepo (recommandé)

Utiliser **pnpm workspaces** pour partager les types entre launcher et API.

```
reborn-roleplay/
├── README.md
├── PLAN_CONCEPTION_LAUNCHER.md          ← ce document
├── pnpm-workspace.yaml
├── package.json
│
├── apps/
│   ├── launcher/                        ← Tauri 2 + React
│   │   ├── src/                         ← React frontend
│   │   │   ├── main.tsx
│   │   │   ├── App.tsx
│   │   │   ├── windows/                 ← multi-window Tauri
│   │   │   │   ├── main/                ← fenêtre principale
│   │   │   │   │   └── App.tsx
│   │   │   │   └── friends/             ← mini-fenêtre amis (v1.0.5)
│   │   │   │       ├── App.tsx
│   │   │   │       ├── tabs/
│   │   │   │       │   ├── FriendsList.tsx
│   │   │   │       │   ├── Messages.tsx
│   │   │   │       │   └── AddFriend.tsx
│   │   │   │       └── components/
│   │   │   ├── routes/
│   │   │   │   ├── Login.tsx
│   │   │   │   ├── Home.tsx
│   │   │   │   ├── Whitelist.tsx
│   │   │   │   ├── Settings/
│   │   │   │   │   ├── index.tsx        ← layout avec onglets
│   │   │   │   │   ├── ProfileTab.tsx
│   │   │   │   │   ├── AccountTab.tsx
│   │   │   │   │   ├── ConnectionsTab.tsx
│   │   │   │   │   └── GameTab.tsx
│   │   │   │   └── ...
│   │   │   ├── components/
│   │   │   │   ├── Sidebar.tsx
│   │   │   │   ├── PlayButton.tsx
│   │   │   │   ├── UpdateModal.tsx
│   │   │   │   └── ui/                  ← composants atomiques
│   │   │   ├── hooks/
│   │   │   ├── stores/                  ← Zustand
│   │   │   ├── lib/
│   │   │   │   ├── tauri.ts             ← wrappers invoke()
│   │   │   │   └── socket.ts            ← client Socket.IO (v1.0.5)
│   │   │   └── styles/
│   │   ├── src-tauri/                   ← backend Rust
│   │   │   ├── Cargo.toml
│   │   │   ├── tauri.conf.json
│   │   │   ├── build.rs
│   │   │   ├── icons/
│   │   │   └── src/
│   │   │       ├── main.rs
│   │   │       ├── auth/
│   │   │       │   ├── mod.rs
│   │   │       │   ├── microsoft.rs
│   │   │       │   ├── discord.rs       ← OAuth Discord
│   │   │       │   ├── steam.rs         ← OpenID Steam (v1.0.5)
│   │   │       │   ├── xbox.rs
│   │   │       │   └── minecraft.rs
│   │   │       ├── manifest/
│   │   │       │   ├── mod.rs
│   │   │       │   ├── verify.rs        ← signature Ed25519
│   │   │       │   └── download.rs
│   │   │       ├── launcher/
│   │   │       │   ├── mod.rs
│   │   │       │   ├── jvm.rs           ← construction commande
│   │   │       │   ├── runtime.rs       ← gestion JRE
│   │   │       │   └── options.rs
│   │   │       ├── integrity/
│   │   │       │   ├── mod.rs
│   │   │       │   ├── hashing.rs
│   │   │       │   └── watcher.rs       ← FS watcher
│   │   │       ├── social/              ← v1.0.5
│   │   │       │   ├── mod.rs
│   │   │       │   ├── window.rs        ← création friends window
│   │   │       │   ├── socket.rs        ← client Socket.IO
│   │   │       │   └── presence.rs
│   │   │       ├── hardware/            ← détection sysinfo (cf §16.1.2)
│   │   │       │   └── detect.rs
│   │   │       ├── api/
│   │   │       │   ├── mod.rs           ← client HTTP vers notre API
│   │   │       │   └── types.rs
│   │   │       ├── storage/
│   │   │       │   └── secrets.rs       ← stronghold/keyring
│   │   │       └── commands.rs          ← #[tauri::command]
│   │   ├── public/
│   │   │   ├── artworks/
│   │   │   └── logos/
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── tsconfig.json
│   │   └── tailwind.config.ts
│   │
│   ├── api/                             ← NestJS (cœur backend)
│   │   ├── src/
│   │   │   ├── main.ts
│   │   │   ├── app.module.ts
│   │   │   ├── auth/
│   │   │   ├── users/
│   │   │   ├── manifest/
│   │   │   ├── whitelist/
│   │   │   ├── patchnotes/
│   │   │   ├── tickets/
│   │   │   ├── shop/
│   │   │   ├── server-status/
│   │   │   ├── incidents/
│   │   │   ├── audit/
│   │   │   ├── discord/                 ← intégration Discord (OAuth, webhooks vers bot)
│   │   │   ├── steam/                   ← OpenID Steam (v1.0.5)
│   │   │   ├── social/                  ← amis, DM, présence (v1.0.5)
│   │   │   │   ├── friends.controller.ts
│   │   │   │   ├── messages.controller.ts
│   │   │   │   ├── presence.gateway.ts  ← Socket.IO
│   │   │   │   └── messages.gateway.ts  ← Socket.IO
│   │   │   ├── staff/                   ← endpoints /staff/*
│   │   │   ├── events/                  ← événements RP
│   │   │   └── common/
│   │   │       ├── guards/
│   │   │       │   ├── jwt.guard.ts
│   │   │       │   ├── role.guard.ts
│   │   │       │   ├── hmac.guard.ts    ← signature HMAC
│   │   │       │   └── two-factor.guard.ts
│   │   │       ├── interceptors/
│   │   │       ├── pipes/
│   │   │       └── decorators/
│   │   ├── prisma/
│   │   │   ├── schema.prisma
│   │   │   ├── migrations/
│   │   │   └── seed.ts
│   │   ├── test/
│   │   ├── package.json
│   │   ├── nest-cli.json
│   │   ├── tsconfig.json
│   │   └── Dockerfile
│   │
│   ├── admin/                           ← Panel staff Next.js
│   │   ├── src/
│   │   │   ├── app/                     ← App Router
│   │   │   │   ├── layout.tsx
│   │   │   │   ├── login/
│   │   │   │   ├── dashboard/
│   │   │   │   ├── whitelist/
│   │   │   │   ├── tickets/
│   │   │   │   ├── players/
│   │   │   │   ├── shop/
│   │   │   │   ├── manifest/
│   │   │   │   ├── events/
│   │   │   │   ├── audit/
│   │   │   │   ├── stats/
│   │   │   │   └── staff/
│   │   │   ├── components/
│   │   │   ├── lib/
│   │   │   └── hooks/
│   │   ├── package.json
│   │   └── Dockerfile
│   │
│   └── bot/                             ← Bot Discord
│       ├── src/
│       │   ├── index.ts
│       │   ├── client.ts
│       │   ├── commands/                ← slash commands
│       │   │   ├── whitelist/
│       │   │   ├── ticket/
│       │   │   ├── player/
│       │   │   ├── event/
│       │   │   └── stats/
│       │   ├── interactions/            ← boutons, modals
│       │   ├── handlers/
│       │   │   ├── webhook.handler.ts   ← reçoit webhooks de l'API
│       │   │   ├── presence.handler.ts  ← Rich Presence
│       │   │   └── role-sync.handler.ts ← sync rôles
│       │   ├── services/
│       │   │   ├── api.service.ts       ← client de l'API
│       │   │   └── redis.service.ts
│       │   └── utils/
│       ├── package.json
│       └── Dockerfile
│
├── packages/
│   ├── shared-types/                    ← types TS partagés (DTO, manifest)
│   │   ├── src/
│   │   │   ├── manifest.ts
│   │   │   ├── auth.ts
│   │   │   └── index.ts
│   │   └── package.json
│   └── manifest-signer/                 ← outil CLI pour signer les manifests
│       ├── src/
│       └── package.json
│
├── minecraft/
│   ├── plugin-guardian/                 ← plugin Paper (Java/Kotlin)
│   │   ├── build.gradle.kts
│   │   ├── src/main/kotlin/...
│   │   └── plugin.yml
│   ├── mod-integrity/                   ← mod Fabric côté client
│   │   ├── build.gradle.kts
│   │   ├── src/main/java/...
│   │   └── fabric.mod.json
│   └── server-config/                   ← templates server.properties, etc.
│
├── infra/
│   ├── docker-compose.yml               ← postgres, redis, api
│   ├── docker-compose.prod.yml
│   └── nginx/
│
└── docs/
    ├── deployment.md
    ├── ops-runbook.md
    └── adr/                             ← Architecture Decision Records
        ├── 0001-tauri-vs-electron.md
        ├── 0002-microsoft-only-auth.md
        └── ...
```

### 12.2 Conventions de code
- **Frontend** : ESLint + Prettier, conventions React Hook Rules strictes
- **Rust** : `cargo fmt` + `cargo clippy -- -D warnings`
- **API** : ESLint + Prettier, NestJS conventions
- **Commits** : Conventional Commits (`feat:`, `fix:`, `chore:`...)
- **Branches** : `main` (prod), `develop` (intégration), `feature/*`, `fix/*`
- **PRs** : review obligatoire avant merge sur `develop`

### 12.3 CI/CD (GitHub Actions)
- Build + tests sur chaque PR
- Build du launcher en release sur tag `launcher-v*`
- Build & push Docker de l'API sur tag `api-v*`
- Signature des binaires en release uniquement

---

## 13. Packaging & expérience d'installation

### 13.1 Objectif visuel
On reproduit le pattern **Zenkai-like** : après installation, le dossier d'application contient **uniquement le launcher et son désinstalleur**. Aucun fichier technique visible (DLL, configs, mods, JRE, jar Minecraft) n'apparaît à cet endroit. Le joueur a l'impression d'une application desktop premium, pas d'un dossier de développement.

**Résultat attendu après installation** :

```
C:\Users\<user>\AppData\Local\Programs\RebornLauncher\
├── RebornLauncher.exe       ← unique exécutable visible
└── uninstall.exe            ← désinstalleur NSIS
```

Tout le reste — JRE, fichiers Minecraft, mods, resource packs, configs, tokens d'auth, logs — vit dans le **game directory isolé** (`%APPDATA%\RebornRoleplay\`, voir section 4.2). Cette séparation a trois bénéfices :

1. **Esthétique** : dossier d'install minimaliste, signature pro
2. **Pratique** : pas de droits admin requis pour les updates de jeu (tout se passe en `%APPDATA%`)
3. **Désinstallation propre** : l'uninstaller demande au joueur s'il veut aussi supprimer les fichiers de jeu, sans tout effacer par défaut

### 13.2 Configuration Tauri
Tauri 2 utilise **NSIS** comme bundler Windows par défaut, ce qui produit nativement le comportement souhaité. La configuration se fait dans `src-tauri/tauri.conf.json` :

```json
{
  "bundle": {
    "active": true,
    "targets": ["nsis"],
    "identifier": "fr.reborn-rp.launcher",
    "publisher": "Reborn Roleplay",
    "copyright": "© 2026 Reborn Roleplay",
    "category": "Game",
    "shortDescription": "Launcher officiel Reborn Roleplay",
    "longDescription": "Le launcher officiel du network Reborn Roleplay.",
    "icon": [
      "icons/32x32.png",
      "icons/128x128.png",
      "icons/128x128@2x.png",
      "icons/icon.icns",
      "icons/icon.ico"
    ],
    "windows": {
      "nsis": {
        "displayLanguageSelector": false,
        "languages": ["French"],
        "installMode": "perMachine",
        "installerIcon": "icons/installer.ico",
        "headerImage": "icons/nsis-header.bmp",
        "sidebarImage": "icons/nsis-sidebar.bmp",
        "installerHooks": "./installer-hooks.nsh",
        "compression": "lzma"
      },
      "webviewInstallMode": {
        "type": "embedBootstrapper"
      }
    }
  }
}
```

**Points importants** :
- `installMode: "perMachine"` : installation pour tous les utilisateurs (Program Files). Alternative : `"currentUser"` (AppData/Local/Programs) si tu veux éviter UAC à l'install — c'est ce que fait Zenkai et c'est plus convivial
- `compression: "lzma"` : compression maximale de l'installeur (plus petit à télécharger, légèrement plus lent à installer)
- `webviewInstallMode: "embedBootstrapper"` : si Edge WebView2 manque sur la machine du joueur (Windows 10 ancien), l'installeur le télécharge automatiquement

### 13.3 Recommandation : `currentUser` plutôt que `perMachine`
Pour un launcher de jeu, l'install **par utilisateur** est préférable :
- Pas de prompt UAC à l'installation (zéro friction)
- Pas de prompt UAC pour les auto-updates
- Le joueur n'a pas besoin de droits admin sur sa machine (cas fréquent sur PC familial / pro)
- Désinstallation propre via "Applications et fonctionnalités" Windows

**Inconvénient** : si plusieurs comptes Windows sur la même machine veulent jouer, chacun doit réinstaller. Acceptable pour ton cas d'usage.

→ **Décision** : `installMode: "currentUser"`. Le launcher s'installera dans `%LOCALAPPDATA%\Programs\RebornLauncher\`.

### 13.4 Premier lancement : provisioning des dossiers
Au tout premier démarrage du launcher (juste après installation), le code Rust :

1. Crée l'arborescence `%APPDATA%\RebornRoleplay\` :
   ```
   %APPDATA%\RebornRoleplay\
   ├── runtime\          (vide, sera peuplé au premier lancement de jeu)
   ├── versions\
   ├── mods\
   ├── resourcepacks\
   ├── config\
   ├── assets\
   ├── auth\
   ├── logs\
   └── launcher.lock     (marqueur de premier lancement effectué)
   ```
2. Initialise le coffre Stronghold pour les secrets
3. Affiche l'écran de login

Le dossier `%APPDATA%\RebornRoleplay\` n'existe **pas** tant que le joueur n'a pas lancé le launcher au moins une fois. Bon comportement : tant qu'il n'a pas joué, rien n'est créé.

### 13.5 Désinstallation propre
Le hook NSIS personnalisé (`installer-hooks.nsh`) intercepte l'uninstall pour proposer une option :

```nsis
!macro NSIS_HOOK_PREUNINSTALL
  MessageBox MB_YESNO|MB_ICONQUESTION \
    "Voulez-vous également supprimer les fichiers de jeu, vos paramètres et vos données de connexion ?$\n$\n\
     (Si vous comptez réinstaller, choisissez Non pour conserver vos préférences.)" \
    IDNO skip_appdata
    
  RMDir /r "$APPDATA\RebornRoleplay"
  
  skip_appdata:
!macroend
```

Comportement résultant :
- **Désinstallation simple** (Non) : supprime `%LOCALAPPDATA%\Programs\RebornLauncher\` uniquement → le joueur peut réinstaller et retrouver ses préférences
- **Désinstallation totale** (Oui) : supprime aussi `%APPDATA%\RebornRoleplay\` → tabula rasa

### 13.6 Signature de code (critique pour la confiance)
Sans signature, Windows SmartScreen affichera un avertissement effrayant à chaque téléchargement. Pour un launcher pro, **il faut signer**.

**Options** :
- **Certificat OV (Organization Validation)** : ~200-400€/an, build immédiatement reconnu mais accumule la "réputation SmartScreen" sur quelques téléchargements
- **Certificat EV (Extended Validation)** : ~400-700€/an, reconnu instantanément par SmartScreen (zéro warning) mais nécessite une clé hardware (HSM/Yubikey)
- **Azure Trusted Signing** : nouveau service Microsoft, ~10$/mois, EV-équivalent, **fortement recommandé** pour 2026

→ **Recommandation MVP** : démarrer sans signature pour les bêta-tests internes, intégrer Azure Trusted Signing avant le lancement public.

L'auto-updater Tauri exige par ailleurs une signature **Ed25519** des releases (différente du code signing Windows) : c'est gratuit et obligatoire, à mettre en place dès le départ.

### 13.7 Distribution
Le `RebornLauncher_1.0.0_x64-setup.exe` (~5-10 Mo) est hébergé sur le CDN à une URL stable :

```
https://cdn.reborn-rp.fr/launcher/latest/RebornLauncher-setup.exe
https://cdn.reborn-rp.fr/launcher/v1.0.0/RebornLauncher-setup.exe
```

Le site web pointe vers `latest/` pour le download, et `/launcher/latest/release.json` (manifest Tauri Updater) est utilisé par les launchers déjà installés pour détecter les mises à jour.

### 13.8 Récap visuel pour le joueur

| Étape | Ce que voit le joueur |
|---|---|
| 1. Télécharge `RebornLauncher-setup.exe` (~8 Mo) | 1 fichier dans Téléchargements |
| 2. Double-clic → installeur NSIS | Wizard simple, pas de prompt UAC |
| 3. Installation terminée | Raccourci Bureau + menu Démarrer |
| 4. Ouvre le dossier d'install | **2 fichiers : `RebornLauncher.exe` et `uninstall.exe`** ✅ |
| 5. Premier lancement | Login Microsoft → téléchargement des fichiers de jeu |
| 6. En jeu | Tout fonctionne, le joueur n'a jamais vu le moindre fichier technique |

C'est exactement l'expérience Zenkai.

---

## 14. Sécurité avancée — durcissement complet

Cette section complète la section 4 avec les mesures de **durcissement professionnel**. Sans elles, le launcher est solide mais reste vulnérable à des attaques opportunistes. Avec elles, on atteint un niveau "économiquement non rentable à attaquer", qui est l'objectif réaliste pour un projet à ce niveau.

### 14.1 Cloudflare devant l'API (gratuit, 1h de setup)
Tous les domaines API (`api.reborn-rp.fr`) doivent passer par **Cloudflare** :
- **DDoS protection** : automatique, encaisse des attaques massives
- **WAF (Web Application Firewall)** : bloque SQL injection, XSS, OWASP Top 10
- **Rate limiting** : couche supplémentaire avant même d'atteindre l'API
- **Bot Fight Mode** : filtre automatiquement les bots malveillants
- **Cloudflare Turnstile** : CAPTCHA invisible/discret sur registration et `/auth/login`
- **Cache et compression** : améliore aussi la performance perçue

Configuration recommandée :
- Page Rules : forcer HTTPS, désactiver le cache pour `/api/*` sauf endpoints publics
- Firewall Rules : geo-block les pays non desservis si pertinent
- Workers : possibilité de signer chaque requête côté edge

### 14.2 HMAC sur les requêtes API (en plus du JWT)
Le JWT seul est vulnérable au replay si l'attaquant l'intercepte. On ajoute une **signature HMAC** par requête :

```
Header: X-Reborn-Timestamp: 1730635200
Header: X-Reborn-Nonce: 8f3c2a...
Header: X-Reborn-Signature: HMAC-SHA256(secret, timestamp + nonce + method + path + body)
```

Le serveur :
1. Vérifie que `timestamp` est dans une fenêtre de ±30s
2. Vérifie que `nonce` n'a jamais été vu (Redis SET avec TTL 60s)
3. Recalcule le HMAC et compare en **temps constant** (`crypto.timingSafeEqual`)

Le `secret` est dérivé du JWT au login (PBKDF2). Cela rend tout replay impossible et protège contre la lecture mémoire partielle.

### 14.3 Détection d'anomalies de login
À chaque authentification, on enregistre l'IP et la géolocalisation (via Cloudflare ou MaxMind). On compare avec le dernier login connu :
- **Nouveau pays** → email + DM Discord au joueur : *"Connexion détectée depuis [pays]. C'était toi ?"*
- **Login simultané depuis 2 pays différents** → invalide tous les tokens et force re-login
- **5 tentatives de login échouées** → blocage temporaire de l'IP

Le joueur peut "valider" l'anomalie via un lien dans l'email. Sinon, après 24h sans réponse, la session est terminée.

### 14.4 2FA obligatoire pour le staff
Tous les comptes `HELPER`, `MODERATOR`, `WHITELIST_REVIEWER`, `ADMIN`, `OWNER` doivent activer **TOTP (Google Authenticator / Authy / Aegis)** :
- Setup via QR code lors de la première connexion au panel
- Backup codes générés (10 codes one-shot)
- Re-vérification 2FA pour les actions critiques (ban, refund, modification de manifest)

Stockage : `twoFactorSecret` chiffré avec une clé serveur dérivée de l'env.

### 14.5 Audit log immuable (chained hashes)
Chaque action staff génère un `AuditLog` avec un **hash chaîné** :
```
hash_n = SHA-256(hash_{n-1} || action || actor || timestamp || metadata)
```
Si quelqu'un modifie une entrée passée, toute la chaîne suivante devient invalide. Détection via job nightly qui vérifie l'intégrité.

Bonus : copie quotidienne des logs vers un service externe (S3 Object Lock en mode Compliance) → impossible à altérer même par un admin de la BDD.

### 14.6 CVE monitoring & supply chain
- **Dependabot** activé sur GitHub → PR auto pour chaque vuln dans une dépendance
- **`cargo audit`** dans la CI → fail build si CVE non patchée
- **`npm audit --omit=dev`** dans la CI → idem
- **Trivy** scan des images Docker → vuln OS
- **Subresource Integrity (SRI)** sur les CDN externes (s'il y en a)
- **Pinning exact** des dépendances : pas de `^` ni `~`, version figée
- Revue manuelle de toute nouvelle dépendance ajoutée (provenance, mainteneurs, popularité)

### 14.7 Plan de rotation des secrets
Document dans `docs/security/key-rotation.md` :

| Secret | Fréquence | Procédure |
|---|---|---|
| Clé Ed25519 du manifest | Annuelle ou si compromis | Nouveau release launcher avec nouvelle pubkey, période de transition 30j avec double signature |
| JWT signing key | Tous les 6 mois | Clé secondaire ajoutée, attente expiration tokens, suppression ancienne |
| Database password | Tous les 3 mois | Rotation via Postgres `ALTER USER`, mise à jour Vault/secret manager |
| Microsoft OAuth secret | Annuelle | Rotation via Azure portal, déploiement nouvelle version |
| Discord bot token | Annuelle | Reset depuis Developer Portal, redéploiement bot |
| Stripe API keys | Annuelle | Rotation depuis dashboard Stripe |

**En cas de fuite suspectée** : rotation immédiate, invalidation de tous les tokens, communication à la communauté.

### 14.8 Backups chiffrés et plan de DR
- **Postgres** : `pg_dump` quotidien chiffré GPG → S3 / Backblaze B2 (région différente du serveur prod)
- **Rétention** : 7 quotidiens + 4 hebdomadaires + 12 mensuels
- **Test de restauration** mensuel obligatoire (sinon on ne sait pas si les backups marchent)
- **RPO (Recovery Point Objective)** : 24h max
- **RTO (Recovery Time Objective)** : 4h max
- **Documentation runbook** : étapes exactes pour restaurer, qui appeler, ordre de redémarrage

### 14.9 Anti-cheat serveur (en plus de l'integrity check)
Le mod check empêche l'ajout de mods, mais ne couvre PAS :
- **Reach hacks** (frapper plus loin que 4 blocs) → faux mouvements packets
- **Killaura** (auto-attaquer plusieurs cibles)
- **Anti-knockback**, **fly**, **speed**, **scaffold**, **crystaaura**

**Recommandation** : installer **Vulcan** (payant, ~30€) ou **Matrix** (gratuit, pas mauvais) en complément du plugin Reborn Guardian. Vulcan est le standard pour serveurs PvP/RP sérieux.

### 14.10 Bug bounty program (optionnel mais classe)
Quand le projet est mature, lancer un programme :
- Page `/security` avec PGP key pour rapports chiffrés
- Récompenses ZK Coin + crédit dans le launcher pour vulns trouvées
- Severity tiers : Low (50 ZC) / Medium (500 ZC) / High (5000 ZC) / Critical (rôle in-game custom)

Cela transforme les hackers en alliés. Beaucoup de serveurs RP ne le font pas et c'est dommage.

### 14.11 Anti-tampering du launcher lui-même
Au démarrage, le launcher vérifie sa propre intégrité :
- Hash SHA-256 de l'exe au démarrage, comparé à celui hébergé sur l'API
- Si mismatch → refuse de démarrer + alerte
- Code signing Windows (Azure Trusted Signing) : si l'exe est modifié, la signature devient invalide

Limite : un attaquant déterminé peut patcher cette vérif elle-même. Mais cela arrête 99% des modifications opportunistes.

### 14.12 Constant-time comparison & memory zeroization
Tout dans le code Rust qui compare des secrets (tokens, hashs, signatures) doit utiliser **`subtle::ConstantTimeEq`** ou `ring::constant_time::verify_slices_are_equal`. Sinon, attaque par timing possible.

Les `String` contenant des secrets (tokens MS, JWT) doivent être effacés de la mémoire après usage avec **`zeroize`** :
```rust
use zeroize::Zeroize;

let mut token = fetch_token();
// ... usage ...
token.zeroize(); // overwrites memory before drop
```

### 14.13 Récapitulatif — checklist de durcissement

- [ ] Cloudflare devant l'API
- [ ] Turnstile CAPTCHA sur endpoints sensibles
- [ ] HMAC sur les requêtes API
- [ ] Détection anomalies de login
- [ ] 2FA staff obligatoire
- [ ] Audit log avec hashs chaînés
- [ ] Dependabot + cargo audit + npm audit dans la CI
- [ ] Plan de rotation des secrets documenté
- [ ] Backups chiffrés off-site + test de restauration mensuel
- [ ] Anti-cheat Vulcan/Matrix sur le serveur
- [ ] Anti-tampering du launcher + code signing
- [ ] Constant-time comparison + zeroization en Rust
- [ ] Pen test avant lancement public
- [ ] Politique de divulgation responsable (page `/security`)

---

## 15. Staff tooling : Panel web + Bot Discord

### 15.1 Vision
Le staff (modération, whitelisteurs, admins) doit pouvoir gérer la communauté **où qu'ils soient** :
- **Sur leur ordinateur** → Panel web (Next.js)
- **Depuis Discord** → Bot avec commandes slash + boutons interactifs
- Les deux sont **strictement synchronisés** car ils tapent dans la même API

L'API NestJS expose un namespace `/api/staff/*` accessible uniquement aux comptes ayant le rôle approprié.

### 15.2 Architecture

```
                    ┌──────────────────────┐
                    │   API NestJS (core)  │
                    │   + Postgres + Redis │
                    │                      │
                    │  /api/staff/*        │
                    │  /api/webhooks/*     │
                    └──────────┬───────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
   ┌──────────┐         ┌─────────────┐       ┌──────────┐
   │ LAUNCHER │         │ PANEL STAFF │       │   BOT    │
   │  Tauri   │         │  Next.js    │       │ DISCORD  │
   │ (joueur) │         │ (web staff) │       │ discord.js│
   └──────────┘         └─────────────┘       └──────────┘
        │                     │                     │
        │ Discord OAuth       │ Discord OAuth       │ Bot token
        │ (lier compte)       │ (login staff)       │ (events)
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
                    ┌──────────────────────┐
                    │ Discord (officiel)   │
                    │ - Channels staff     │
                    │ - DM joueurs         │
                    │ - Rôles auto         │
                    │ - Rich Presence      │
                    └──────────────────────┘
```

### 15.3 Liaison comptes externes (Discord obligatoire, Steam/Twitch optionnels)

**Discord — obligatoire pour whitelist**

Flow OAuth :
1. Dans l'onglet Settings → Connexions, bouton **"Lier mon compte Discord"**
2. Ouvre Discord OAuth dans la WebView : `https://discord.com/api/oauth2/authorize?client_id=...&redirect_uri=...&scope=identify`
3. Discord redirige avec un `code`
4. API échange contre un token, récupère `user.id`, `user.username`, `user.global_name`, `user.avatar`
5. Stocke `discordUserId`, `discordUsername`, `discordLinkedAt` sur le User
6. Bot reçoit un webhook : il assigne le rôle Discord `Joueur` automatiquement

**Liaison Discord obligatoire pour** :
- Postuler à la whitelist (sinon le staff ne peut pas DM le joueur)
- Ouvrir un ticket
- Acheter dans la boutique (pour les notifications de livraison)

**Liaison Discord optionnelle pour** : se connecter au launcher, rejoindre le serveur

**Anti-abus Discord** : 
- Un compte Discord ne peut être lié qu'à **un seul** compte Minecraft (et vice-versa)
- Si délier puis relier, cooldown de 30 jours pour éviter le partage de compte
- Blocage si le compte Discord a moins de **7 jours d'ancienneté** (anti-bot)
- Blocage si le compte Discord n'a pas vérifié son email
- Blocage si le compte Discord n'est pas membre du serveur Discord officiel

**Steam — optionnel mais recommandé**

Steam utilise **OpenID 2.0** (pas OAuth, mais le flow utilisateur est similaire) :
1. Dans Settings → Connexions, bouton **"Lier mon compte Steam"**
2. Redirection vers `https://steamcommunity.com/openid/login?...`
3. Steam authentifie et renvoie un `claimed_id` contenant le SteamID64
4. API valide la signature OpenID et récupère le profil via `ISteamUser/GetPlayerSummaries` (Steam Web API key requise)
5. Stocke `steamId`, `steamUsername`, `steamLinkedAt`

**Bénéfices de la liaison Steam** :
- **Signal de confiance** : on lit l'ancienneté du compte (`timecreated`), le niveau Steam, le statut VAC. Un compte Steam de 5+ ans avec niveau ≥ 5 peut bypass la file d'attente whitelist (mécanisme optionnel)
- **Recherche d'amis par SteamID** (cf §17.4)
- **Anti-fraude boutique** : score de confiance pour les achats à risque
- **Bénéfices in-game** : badges, cosmétiques exclusifs si Steam lié

**Anti-abus Steam** : compte privé refusé, compte VAC banni signalé au staff, cooldown 30j pour relier.

**Twitch — optionnel, prévu v1.1+**

Pour les streamers du serveur :
- OAuth Twitch standard (`scope=user:read:email`)
- Permet d'afficher un badge **"Streamer"** dans la liste d'amis et le profil
- Quand le streamer est en live ET joue sur Reborn → notification dans le launcher pour les autres joueurs (avec lien Twitch)
- Permet l'intégration du chat Twitch dans une mini-fenêtre (v2)
- Permet la synchronisation des abonnés Twitch avec un rôle Discord/in-game

### 15.4 Panel web staff (`apps/admin`)

**Stack** :
- Next.js 15 + TypeScript
- Authentification : Discord OAuth → vérif rôle via API
- UI : Shadcn/ui + Tailwind (cohérent avec le launcher)
- Tables/data : TanStack Table + TanStack Query

**Sections du panel** :

| Section | Permissions requises | Fonctionnalités |
|---|---|---|
| Dashboard | HELPER+ | KPIs (joueurs en ligne, candidatures pending, tickets ouverts, incidents 24h) |
| Candidatures whitelist | WHITELIST_REVIEWER+ | Liste filtrable, détail, accepter/refuser/demander révision, historique |
| Tickets | HELPER+ | Liste tickets ouverts, conversation, assign, close |
| Joueurs | MODERATOR+ | Recherche, profil, ban/mute/kick, historique modération, lookup via UUID/pseudo/Discord |
| Boutique | ADMIN+ | Gestion produits, stock, prix, refunds, livraisons en attente |
| Patch notes | ADMIN+ | Création/édition (markdown editor), prévisualisation, planification |
| Lore & règles | ADMIN+ | Gestion contenu markdown, versioning |
| Manifest | ADMIN+ | Création nouvelle version, upload mods, signature, publication |
| Événements | MODERATOR+ | Calendrier RP, création/édition |
| Audit log | ADMIN+ | Recherche dans l'historique des actions staff |
| Sécurité | ADMIN+ | Incidents d'intégrité, anomalies de login, sessions actives |
| Stats | ADMIN+ | Graphs population, nouveaux joueurs, revenus |
| Staff management | OWNER | Promouvoir/rétrograder staff, voir leurs stats d'activité |

### 15.5 Bot Discord (`apps/bot`)

**Stack** :
- Node.js + TypeScript + discord.js v15
- Hébergé en process séparé (Docker container)
- Communication API : HTTP avec token bot dédié (rôle `BOT_INTERNAL`)
- Persistance : Redis pour les états transitoires (modal en cours, etc.)

**Notifications staff (depuis API → channels)** :

Quand l'API reçoit un événement, elle envoie un webhook au bot qui poste dans le bon channel.

| Événement | Channel | Format |
|---|---|---|
| Nouvelle candidature whitelist | `#staff-whitelist` | Embed riche + boutons Accepter/Refuser/Réviser |
| Nouveau ticket | `#staff-tickets` | Embed avec catégorie + bouton "Prendre en charge" |
| Réponse à un ticket | thread du ticket | Synchro auto |
| Incident d'intégrité critique | `#staff-security` | Alerte rouge + ping `@SecurityTeam` |
| Anomalie de login | `#staff-security` | Embed avec détails |
| Achat boutique | `#staff-boutique` | Pour livraison manuelle si nécessaire |
| Joueur banni/mute | `#staff-moderation` | Log de l'action |

**Notifications joueur (DM)** :

| Événement | Format DM |
|---|---|
| Candidature acceptée | "Bienvenue à Reborn ! Tu as accès au serveur. Lis le règlement et bon RP." |
| Candidature refusée | "Ta candidature n'a pas été acceptée. Raison : [...]. Tu peux refaire une candidature." |
| Candidature en révision | "Le staff te demande de préciser : [...]. Modifie ta candidature dans le launcher." |
| Ticket répondu | "Réponse à ton ticket #123 : [...]. Continue la conversation dans le launcher." |
| Achat livré | "Ton achat [...] a été livré in-game. Profite !" |
| Ban | "Tu as été banni du serveur. Raison : [...]. Durée : [...]. Faire appel : ouvrir un ticket." |
| Anomalie de login | "Connexion détectée depuis [pays]. Si ce n'est pas toi, réagis avec ❌." |

**Slash commands** :

```
/whitelist accept @joueur                    [WHITELIST_REVIEWER+]
/whitelist reject @joueur reason:"..."       [WHITELIST_REVIEWER+]
/whitelist info @joueur                      [HELPER+]

/ticket close id:123                         [HELPER+]
/ticket assign id:123 @staff                 [HELPER+]
/ticket transfer id:123 @staff               [HELPER+]

/player info @joueur                         [HELPER+]
/player ban @joueur duration:7d reason:"..." [MODERATOR+]
/player mute @joueur duration:1h             [MODERATOR+]
/player unban @joueur                        [ADMIN+]

/event create                                [MODERATOR+]
/event list
/event delete id:123                         [MODERATOR+]

/announce channel:#general message:"..."     [ADMIN+]
/manifest current                            [ADMIN+]
/manifest publish version:1.0.5              [ADMIN+]

/stats online
/stats today                                 [HELPER+]
/stats week                                  [ADMIN+]
```

**Boutons interactifs** (sur les embeds de candidature) :

```
┌────────────────────────────────────────────┐
│ Nouvelle candidature whitelist              │
│ ─────────────────────────────────────────── │
│ Joueur : OMZ (Discord: @omz)                │
│ MC : OMZ (UUID: ...)                        │
│ Personnage : Hikamatsu Nara                 │
│ Âge : 22 ans                                │
│ Background : ...                            │
│ Motivation : ...                            │
│ Soumise il y a 5 minutes                    │
│ ─────────────────────────────────────────── │
│ [✅ Accepter] [❌ Refuser] [📝 Révision] [👁️ Voir] │
└────────────────────────────────────────────┘
```

- **✅ Accepter** : confirmation modale → API → DM joueur + assignation rôle Discord `Whitelisté` + log audit
- **❌ Refuser** : modale Discord pour saisir la raison → API → DM joueur + log audit
- **📝 Révision** : modale pour préciser ce qui manque → API → DM joueur + statut `NEEDS_REVISION`
- **👁️ Voir** : ouvre le détail dans le panel web (lien direct)

**Configuration permissions** :
- Les commandes `/whitelist *` ne sont visibles que dans `#staff-whitelist`
- Les commandes `/player ban` exigent une raison min 10 caractères
- Toutes les actions sont loggées dans le canal `#staff-actions` (audit visuel) en plus de l'AuditLog DB

### 15.6 Thread Discord par ticket (synchro bidirectionnelle)
Pour chaque ticket ouvert, le bot crée un **thread privé** dans `#staff-tickets` avec le staff de garde + le joueur (si lié Discord).

- Joueur écrit dans le launcher → API → bot poste dans le thread (en tant que webhook avec avatar joueur)
- Staff répond dans le thread → bot relaie vers l'API → s'affiche dans le launcher
- Fermeture du ticket dans le launcher OU via `/ticket close` → bot archive le thread

**Avantages** : staff peut répondre depuis n'importe où sur Discord, joueur a une notif Discord en plus de l'in-app, l'historique reste dans les deux interfaces.

### 15.7 Rôles Discord auto-assignés
Le bot synchronise les rôles Discord en fonction du statut joueur :

| Statut joueur | Rôle Discord ajouté |
|---|---|
| Compte créé (lié) | `Joueur` |
| Whitelist acceptée | `Whitelisté` (+ retire `Joueur`) |
| Faction Uchiha | `Uchiha` |
| Helper | `Staff - Helper` |
| Modérateur | `Staff - Modération` |
| Admin | `Staff - Admin` |
| Banni | `Banni` (+ retire tous les autres) |

Synchronisation toutes les 5 min + immédiate sur événement (acceptation, ban, etc.).

### 15.8 Logs et observabilité
- Toutes les interactions bot ↔ API sont loggées (Pino structuré JSON)
- Métriques exposées en `/metrics` (Prometheus) : nb commandes par minute, latence API, erreurs
- Dashboard Grafana : santé du bot, taux d'erreur, KPIs
- Alerting (Alertmanager / Better Stack) : si bot down > 2 min, ping `@OnCall` Discord

---

## 16. Features inspirées des autres launchers

Cette section liste des features observées dans Lunar Client, Feather Client, Badlion, Modrinth App, etc., évaluées pour notre contexte RP.

### 16.1 À intégrer dès le MVP ou peu après

#### 16.1.1 Discord Rich Presence
**Source d'inspiration** : Feather Client, Lunar Client.

Quand le joueur est en jeu, son statut Discord affiche :
```
┌─────────────────────────────┐
│ 🎮 Joue à Reborn Roleplay   │
│ Hikamatsu Nara — Konoha     │
│ Pays du Feu                 │
│ ⏱️ 1h 23min                 │
└─────────────────────────────┘
```

Implémentation : crate Rust `discord-rich-presence`, mise à jour toutes les 30s avec position du joueur (récupérée du serveur via API). Formidable bouche-à-oreille gratuit.

**Effort** : 1 jour. **Impact** : énorme.

#### 16.1.2 Auto-détection hardware (page Configurations système)
**Source d'inspiration** : page Zenkai (screen 2) + GDLauncher.

Au premier lancement, le launcher lit (via crates Rust `sysinfo`, `wgpu`) :
- CPU model + cores
- RAM totale
- GPU model + VRAM
- OS + version
- Espace disque libre

Compare avec les seuils Minimal/Recommandé/Haut de gamme. Affiche :
```
┌──────────────────────────────────────────┐
│ Ta configuration                         │
│ ────────────────────────────────────────  │
│ Intel i7-12700K  ✅ Haut de gamme         │
│ 32 Go RAM         ✅ Haut de gamme         │
│ RTX 3070          ✅ Recommandée           │
│ 245 Go libres     ✅ OK                   │
│                                          │
│ → Ta machine peut viser 144+ FPS 🎯      │
└──────────────────────────────────────────┘
```

Bonus : pré-règle automatiquement la RAM allouée et la résolution.

**Effort** : 2 jours. **Impact** : super UX, montre du soin.

#### 16.1.3 Calendrier d'événements RP
**Source d'inspiration** : Badlion daily challenges + serveurs RP existants.

Section dédiée dans le launcher (ou widget sur l'accueil) avec les **prochains événements RP** :
```
┌──────────────────────────────────────────┐
│ 📅 Cette semaine                         │
├──────────────────────────────────────────┤
│ 🏯 Tournoi des Chuunin                   │
│    Sam 10/05 — 20h00                     │
│    🔔 Me notifier 30 min avant           │
├──────────────────────────────────────────┤
│ ⚔️ Guerre Konoha vs Suna                 │
│    Dim 11/05 — 21h00                     │
│    🔒 Réservé Whitelist                   │
└──────────────────────────────────────────┘
```

Notifications push 30 min / 5 min avant + DM Discord. **Critique pour un RP** car c'est ce qui fait que les joueurs se connectent au bon moment et que les events ne tombent pas à plat.

**Effort** : 3-4 jours (incluant gestion staff côté panel). **Impact** : énorme rétention.

#### 16.1.4 Population serveur en temps réel (graph)
**Source d'inspiration** : Lunar Client server browser.

Sur l'accueil, un mini-graph des dernières 24h de population :
```
Joueurs en ligne aujourd'hui
   ▁▂▃▅▇█▇▆▄▃▂▁▁▂▄▆█▇▅▃
   00h    06h    12h    18h    24h
   📊 Pic : 47 joueurs à 21h32
   🟢 Maintenant : 23 joueurs
```

Aide les joueurs à savoir quand le serveur est actif. **Effort** : 1 jour. **Impact** : moyen mais sympa.

### 16.2 À ajouter en v1.1 / v1.2

#### 16.2.1 Skin manager
**Source d'inspiration** : Feather Client.

Onglet dans Settings :
- Preview 3D du skin actuel (Three.js `skinview3d`)
- Upload d'un nouveau skin (.png 64x64)
- Historique des skins utilisés (5 derniers, swap rapide)
- Application via API Mojang (nécessite le `mc_access_token`)

**Effort** : 2-3 jours. **Impact** : adoré des joueurs.

#### 16.2.2 Statistiques personnelles
**Source d'inspiration** : Badlion, Hypixel.

Page profil avec :
- Temps de jeu total / cette semaine
- Scènes RP participées (compteur incrémenté par le plugin serveur)
- Faction actuelle + historique
- Combats RP (victoires/défaites)
- ZK Coin gagnés/dépensés
- Achievements débloqués

Engagement par gamification douce.

#### 16.2.3 Liste d'amis
**Source d'inspiration** : Lunar Client friends system.

- Ajouter par pseudo MC ou Discord
- Voir statut (offline / online launcher / in-game)
- Voir où ils jouent (faction, position approximative pour RP)
- Inviter à rejoindre via le launcher
- Privacy settings (apparaître offline, etc.)

**Effort** : 5 jours. **Impact** : grande rétention sociale.

#### 16.2.4 Achievements / Quêtes
**Source d'inspiration** : Badlion daily challenges.

- "Première mission RP réussie"
- "Survivre une semaine sans mourir RP"
- "Atteindre le grade Jōnin"
- "Participer à 10 events"

Récompenses : ZK Coin, badges visibles dans le profil, cosmétiques exclusifs.

#### 16.2.5 News carousel riche
**Source d'inspiration** : Lunar Client home page.

Sur l'accueil, un carrousel automatique avec :
- Image/vidéo en arrière-plan
- Titre + extrait
- CTA (lire plus, participer, voir l'event)

Contenu géré par le staff via le panel web. Maintien l'accueil "vivant".

### 16.3 À considérer pour la v2+

- **Mode mini-fenêtre** quand MC tourne (Lunar) : petite fenêtre flottante avec stats serveur, liste amis, chat
- **Boutique de cosmétiques avec 3D** (Feather) : preview en rotation des items
- **Mod settings unifié** : configurer Sodium/Iris depuis le launcher, plus depuis Mod Menu
- **Système de parrainage** : code parrain → bonus ZK Coin pour les deux
- **Wiki collaboratif RP** : fiches personnages, factions, lore community-driven
- **Streaming integration** : un bouton "Live sur Twitch" qui cherche les streamers du serveur

### 16.4 À éviter (mauvaises idées pour notre contexte)

- **Anti-debug agressif type kernel-mode** : intrusif, casse facilement, frustre les utilisateurs sous Linux/VM légitimes. On laisse l'integrity check serveur faire son travail.
- **Hardware fingerprinting agressif** : RGPD compliqué, faux positifs sur changement de matériel.
- **Mod browser** : on a une allowlist stricte, ça contredit notre modèle.
- **Multi-comptes / account switcher** : un compte = un personnage RP, pas de switching.
- **Achats in-launcher de comptes Minecraft** : juridiquement gris.

---

## 17. Système social — amis, DM, mini-fenêtre (v1.0.5)

Cette section décrit le module social, qui constitue la **release v1.0.5** dédiée juste après le MVP. Inspiré directement du panneau d'amis Zenkai (screens 5 et 6) et adapté au contexte RP.

### 17.1 Vision
Permettre aux joueurs de **rester connectés socialement** au sein de l'écosystème Reborn, sans dépendre uniquement de Discord. Trois piliers :
1. **Liste d'amis** avec présence en temps réel
2. **Messages directs (DM)** pour échanger HRP rapidement
3. **Mini-fenêtre flottante** détachable (style Zenkai) qui peut rester visible pendant le jeu sur un second écran

### 17.2 Architecture

```
┌─────────────────────────────────────┐
│        LAUNCHER (Tauri)             │
│                                     │
│  ┌───────────────┐  ┌────────────┐  │
│  │ Main window   │  │ Friends    │  │
│  │ (1280×800)    │  │ window     │  │
│  │               │  │ (380×720)  │  │
│  │               │  │ détachable │  │
│  └───────┬───────┘  └─────┬──────┘  │
│          │                │         │
│          └────────┬───────┘         │
│                   │                 │
│        Tauri events (locaux)        │
│                   │                 │
│         ┌─────────┴─────────┐       │
│         │  Rust backend     │       │
│         │  Socket.IO client │       │
│         └─────────┬─────────┘       │
└───────────────────│─────────────────┘
                    │ WebSocket (WSS)
                    ▼
        ┌───────────────────────┐
        │   API NestJS          │
        │   Socket.IO server    │
        │   + Redis adapter     │
        │                       │
        │   /presence namespace │
        │   /messages namespace │
        └───────────────────────┘
```

### 17.3 Multi-window Tauri
La fenêtre amis est une **seconde fenêtre Tauri** créée à la volée :

```rust
// src-tauri/src/social/window.rs
use tauri::{WindowBuilder, WindowUrl, Manager};

pub fn open_friends_window(app: &tauri::AppHandle) -> tauri::Result<()> {
    if let Some(window) = app.get_webview_window("friends") {
        window.set_focus()?;
        return Ok(());
    }
    
    WindowBuilder::new(app, "friends", WindowUrl::App("friends.html".into()))
        .title("Reborn — Amis")
        .inner_size(380.0, 720.0)
        .min_inner_size(360.0, 600.0)
        .resizable(true)
        .decorations(false)              // pas de barre native, on en met une custom
        .transparent(true)               // pour les coins arrondis
        .always_on_top(false)            // toggle utilisateur dans Settings
        .skip_taskbar(false)             // visible dans la barre des tâches
        .build()?;
    
    Ok(())
}
```

**Persistance** : position (x, y) et taille (w, h) de la fenêtre amis sauvegardées dans `tauri-plugin-store` à chaque déplacement/resize. Restaurées au prochain démarrage.

**Communication entre les deux fenêtres** : événements Tauri locaux (`emit_to`). Quand un message arrive, le backend Rust émet `message:new` vers les deux fenêtres ; chacune décide quoi en faire (badge dans la sidebar de la main window, notification visuelle dans la friends window).

### 17.4 Onglets de la mini-fenêtre amis

Comme dans Zenkai (screens 5 et 6), 3 onglets :

**Onglet 1 — Liste d'amis** (icône groupe)
- En-tête : avatar + pseudo + statut (En ligne / En jeu / Absent / Hors-ligne)
- Search bar pour filtrer
- Liste triée : En jeu > En ligne > Absent > Hors-ligne, puis ordre alpha
- Click droit sur un ami : `Envoyer un message`, `Inviter à rejoindre`, `Voir le profil`, `Bloquer`, `Retirer`
- Statut "En jeu" affiche aussi : faction RP, sous-zone (si autorisé par le joueur, privacy setting)
- État vide : *"Aucun ami pour le moment. Ajoutez-en un !"*

**Onglet 2 — Messages** (icône bulle)
- Liste des conversations (DM)
- Tri par dernière activité, badge de messages non-lus
- Click → ouvre la conversation dans la même fenêtre (slide animation)
- Conversation : historique paginé (20 messages, scroll = load older), zone de saisie en bas
- Support markdown léger (`**gras**`, `*italique*`, `` `code` ``), mentions `@pseudo`
- Pas de pièces jointes en v1.0.5 (peut-être en v1.1)

**Onglet 3 — Ajouter** (icône personne+)
- Search bar : `Pseudo MC, Nom d'affichage, Steam ID...`
- Recherche multi-source :
  - Pseudo Minecraft exact
  - Nom d'affichage (fuzzy)
  - SteamID64 (si lié)
  - Discord username (si lié)
- Résultats : avatar + pseudo + petit bouton `Ajouter` qui envoie une demande
- Liste des demandes envoyées en attente
- Liste des demandes reçues avec boutons Accepter/Refuser

### 17.5 Présence temps réel

**Stack** : Socket.IO 4 + Redis adapter pour le scaling horizontal.

**Connexion** :
- Au login du launcher, connexion WebSocket avec le JWT en handshake
- Le client envoie un `presence:update` toutes les 30s (heartbeat)
- Si le serveur ne reçoit rien pendant 90s → statut "Hors-ligne"

**États** :
| État | Déclencheur |
|---|---|
| `online` | Launcher ouvert, pas en jeu |
| `in_game` | Minecraft lancé (le launcher pousse l'event au spawn JVM) |
| `away` | Inactivité OS détectée > 5 min |
| `offline` | Launcher fermé ou heartbeat perdu |

**Confidentialité** : un toggle `Apparaître hors-ligne` dans Settings → Notifications, qui force `offline` côté serveur peu importe l'activité réelle.

**Diffusion** : quand un user change d'état, le serveur push à tous ses amis acceptés (room Socket.IO `friends:{userId}`).

### 17.6 Messages directs

**Persistance** : tous les messages dans `DirectMessage` (cf §5).

**Rate limiting** : 30 messages/min/user (anti-spam). Soft mute après 3 violations en 24h.

**Modération** :
- Filtre automatique de mots interdits (configurable côté admin)
- Bouton `Signaler` sur chaque message → crée un ticket auto en `MODERATION` avec contexte
- Le staff peut consulter les conversations signalées via le panel (audit log de toute consultation)

**Privacy** :
- Par défaut, on ne reçoit des DM que des amis acceptés
- Toggle `Autoriser les messages d'inconnus` dans Settings → Notifications
- Bloquer un user → tous ses messages disparaissent de l'historique de l'autre, plus de demande d'ami possible

### 17.7 Invitation à rejoindre le serveur
Sur un ami `online` (pas en jeu), bouton `Inviter à jouer` :
- Envoie un message système (`type: 'invite'`)
- Côté receveur : popup non intrusive *"OMZ t'invite à rejoindre Reborn — Accepter / Refuser"*
- Accept → click déclenche le launch Minecraft directement

### 17.8 Endpoints API du module social

| Méthode | Endpoint | Description |
|---|---|---|
| GET | `/social/friends` | Liste mes amis (acceptés) avec leur statut |
| GET | `/social/friends/requests` | Demandes reçues + envoyées |
| POST | `/social/friends/request` | `{ targetUserId }` envoie une demande |
| POST | `/social/friends/:id/accept` | Accepter |
| POST | `/social/friends/:id/decline` | Refuser |
| DELETE | `/social/friends/:id` | Retirer un ami / annuler une demande |
| POST | `/social/blocks` | `{ targetUserId, reason? }` bloquer |
| DELETE | `/social/blocks/:id` | Débloquer |
| GET | `/social/users/search?q=...` | Recherche utilisateur (pseudo MC, display name, SteamID) |
| GET | `/social/dm` | Liste de mes conversations (avec dernier message) |
| GET | `/social/dm/:userId?cursor=...&limit=20` | Historique avec un user |
| POST | `/social/dm/:userId` | `{ content }` envoyer un message |
| PATCH | `/social/dm/:messageId` | Éditer (15 min max) |
| DELETE | `/social/dm/:messageId` | Supprimer (soft delete) |
| POST | `/social/dm/:userId/read` | Marquer comme lu |
| POST | `/social/messages/:id/report` | Signaler un message |

### 17.9 WebSocket events (Socket.IO)

**Namespace `/presence`** :
- `presence:update` (client → server) : heartbeat
- `presence:friend_changed` (server → client) : un ami a changé d'état
- `presence:friend_request` (server → client) : nouvelle demande d'ami
- `presence:friend_accepted` (server → client)

**Namespace `/messages`** :
- `message:send` (client → server) : envoyer un DM
- `message:new` (server → client) : nouveau DM reçu
- `message:read` (client → server) : marquer comme lu
- `message:edited` (server → client)
- `message:deleted` (server → client)
- `typing:start` / `typing:stop` (bidir, anti-spam : 1 émission max / 3s)

### 17.10 Performances & scalabilité
- **Redis adapter Socket.IO** : permet de faire tourner plusieurs instances de l'API derrière un load balancer, sans perte de présence/messages
- **Indexation Postgres** : index composé sur `(senderId, receiverId, createdAt)` pour les requêtes d'historique
- **Pagination cursor-based** sur les DM (évite OFFSET coûteux)
- **Limite de friends** : 200 amis par user (raisonnable, à ajuster)
- **Limite d'historique DM** : illimité côté DB, mais l'UI ne charge que les 20 derniers + scroll-to-load

### 17.11 UI/UX et thème
La mini-fenêtre amis suit la **même charte** que la main window (sombre, blue accent #3B5BDB), avec :
- Coins arrondis 12px
- Barre de titre custom (drag region) avec bouton minimize
- Animations Framer Motion pour les transitions d'onglets
- Avatar en cercle, statut en pastille colorée en bas-droite (vert online, jaune away, rouge offline, bleu in-game)
- Notifications visuelles : badge rouge avec compteur sur l'icône Messages

---

## 18. Prochaines étapes immédiates

Ce document posé, voici l'ordre concret pour démarrer le code :

### Semaine 1 — Fondations
1. **Créer le repo monorepo** avec l'arborescence ci-dessus (vide, juste les dossiers)
2. **Initialiser le projet Tauri** : `pnpm create tauri-app` dans `apps/launcher/`
3. **Initialiser le projet NestJS** : `nest new api` dans `apps/`
4. **Mettre en place Docker compose** avec Postgres + Redis pour le dev local
5. **Créer le schéma Prisma** et lancer la première migration
6. **Brancher le frontend Tailwind + Framer Motion + React Router**
7. **Créer la maquette statique** des écrans login + home (sans logique, juste le visuel pour valider le look Zenkai-like)

### Semaine 2 — Auth Microsoft
1. **Enregistrer une app Azure AD** (compte Microsoft Entra) pour récupérer le `CLIENT_ID`
2. **Implémenter le flow OAuth complet en Rust** (`src-tauri/src/auth/`)
3. **Implémenter `/auth/login` côté API** qui valide le `mc_access_token` et émet un JWT
4. **Brancher le bouton "Se connecter avec Microsoft"** sur le flow réel
5. **Stocker le refresh token** via Stronghold

### Semaine 3 — Manifest & téléchargement
1. **Outil CLI `manifest-signer`** : générer Ed25519 keypair, signer un manifest JSON
2. **Endpoint `/manifest/current`** côté API
3. **Vérification de signature en Rust** (clé publique embarquée)
4. **Logique de diff + téléchargement parallèle** avec progress
5. **UI de téléchargement** (modal Zenkai-like)

### Semaine 4 — Lancement & sécurité
1. **Téléchargement du JRE** (piston-meta)
2. **Construction de la commande JVM** + spawn
3. **Auto-connect au serveur**
4. **FS watcher anti-tampering**
5. **Plugin Paper Reborn Guardian** : prototype qui kick si modlist absent

### Semaines 5-6 — Le reste du MVP
- Whitelist
- Patch notes
- Règlement
- Settings
- Auto-update Tauri
- Polish UI

### Avant la mise en prod
- **Audit de sécurité manuel** : passer en revue chaque endpoint, chaque storage de secret, chaque entrée utilisateur
- **Tests de charge** sur l'API (k6 ou Artillery)
- **Tests de pénétration** sur le launcher : tenter de bypass le mod check, le FS watcher, l'auth
- **Plan de réponse aux incidents** : que faire si la clé Ed25519 fuite ? (rotation : nouveau release launcher avec nouvelle clé pub)

---

## Annexe A — Configuration Microsoft Azure (obligatoire)

Pour utiliser MS OAuth, il faut :
1. Aller sur https://portal.azure.com → Microsoft Entra ID → App registrations
2. Créer une nouvelle app :
   - Name : "Reborn Roleplay Launcher"
   - Supported account types : "Personal Microsoft accounts only"
   - Redirect URI : Public client/native → `http://localhost:53682/callback`
3. Récupérer le `Application (client) ID`
4. Dans **Authentication** : autoriser "Allow public client flows" = Yes
5. Dans **API permissions** : ajouter `XboxLive.signin` et `offline_access` (Microsoft Graph)
6. Dans **Branding** : ajouter logo + URL d'accueil + URL de politique de confidentialité

> ⚠️ La politique de confidentialité est **obligatoire** dès qu'on collecte des données. Prévoir une page hostée publiquement.

---

## Annexe B — Considérations légales

- **RGPD** : on stocke email, UUID MC, IPs de session → registre des traitements obligatoire, page de politique, droit à l'oubli implémenté (`DELETE /auth/me`)
- **Mojang EULA** : interdit de vendre des avantages gameplay (P2W). La boutique doit se limiter à du cosmétique, des grades de chat, des avantages non gameplay
- **Microsoft Branding Guidelines** : le bouton "Se connecter avec Microsoft" doit respecter les guidelines visuelles (logo officiel, fond blanc/noir, taille mini)
- **Mods redistribués** : vérifier la licence de chaque mod (Sodium = LGPL, Iris = LGPL → OK ; Optifine = pas de redistribution → exclu)

---

**Fin du document.**

> Toute modification de ce document doit être versionnée. À chaque changement majeur d'architecture, créer un ADR dans `docs/adr/`.
