# Reborn Integrity

Mod **Fabric client-side** qui atteste le client auprès du serveur
Reborn au moment du JOIN, via un play-token signé par l'API.

> **Périmètre cible** — ce mod fait **uniquement** l'attestation. Toute
> la couche UI (menu principal, ESC, ConnectScreen, sub-screens
> Options/Lore, OST player UI, etc.) appartient à `mod-hud/`. Le code
> UI vit encore ici historiquement (cf §11 du PLAN) — le move vers
> `mod-hud/` est planifié en session dédiée pour éviter de polluer les
> WIPs en cours.

## Architecture du flow d'attestation

```
[launcher Rust]
   |  POST /v1/play/session  (JWT user, gate role != PLAYER)
   v
[API NestJS]
   |  retourne playToken =
   |    base64(JSON{sub, mcUuid, mcUsername, iat, exp})
   |    .base64(HMAC-SHA256(REBORN_PLAY_TOKEN_SECRET, payload))
   v
[launcher Rust]
   |  écrit dans <gameDir>/.reborn-play-token (+ -dev pour un staff)
   |  lance JVM avec -Dreborn.playTokenPath=<chemin absolu>
   |                 (-Dreborn.playTokenPath.dev=<chemin> pour un staff)
   |  réécrit ces fichiers toutes les 3 min tant que le jeu tourne
   |  (launcher/play_tokens.rs : jeton de 5 min, écriture atomique)
   v
[mod Reborn Integrity — ICI]
   onInitializeClient :
     - registre AuthPayload sur le canal C2S "reborn:auth"
   ClientPlayConnectionEvents.JOIN (à CHAQUE connexion) :
     - adresse rejointe = reborn.server.dev.host/.port ? fichier dev
       (absent : jeton principal), sinon fichier principal
     - relit le fichier (jeton frais), l'envoie au serveur
   v
[plugin Reborn Guardian]
   - PlayerJoinEvent schedule un kick à T+8s
   - sur réception "reborn:auth" : vérifie HMAC + match UUID + exp,
     marque le joueur OK -> annule le kick
```

Le **secret HMAC** (`REBORN_PLAY_TOKEN_SECRET`, 32+ chars) est partagé
**uniquement** entre l'API et le plugin Guardian. Le launcher et le
mod ne le voient jamais — ils transportent le token tel quel. Un
client patché/recompilé **ne peut donc pas forger d'attestation**
sans appeler l'API authentifiée.

## Format wire

Canal C2S : `reborn:auth`. Payload = bytes UTF-8 bruts du play-token,
sans préfixe de longueur (le custom payload MC porte déjà la taille).
Cf `AuthPayload.java`.

## Comportement standalone

Si la sysprop `reborn.playTokenPath` est absente (lancement hors
launcher Reborn — vanilla server, LAN, dev) le mod log un warn et ne
fait rien. Le client reste exploitable : il ne s'attestera juste pas
et se fera kick par notre serveur s'il s'y connecte.

Fichier illisible au moment de la connexion : le mod renvoie le dernier
jeton lu de ce fichier (Guardian tranche s'il a expiré).

## Build

JDK 25 requis. Projet isolé du monorepo pnpm.

```pwsh
./gradlew build       # → build/libs/reborn-integrity-<ver>.jar
./gradlew runClient   # client MC 26.2 avec le mod
```

## Packaging par le launcher

1. Embarqué dans le manifest signé (cf `packages/manifest-signer`)
   sous `mods/reborn-integrity-<ver>.jar` avec son hash SHA-256.
2. Téléchargé par le launcher dans `<gameDir>/mods/` au prochain check.

## Versions

Alignées sur `REBORN_MC_VERSION=26.2`. À bumper en lockstep avec le
serveur Paper et le plugin Guardian. 0.3.2 : jeton relu à chaque
connexion, jeton dev choisi selon l'adresse rejointe.

## Voir aussi

- `minecraft/plugin-guardian/` — vérification serveur du play-token
- `minecraft/mod-hud/` — toute l'UI client (cible post-migration)
- `apps/api/src/play/` — émission du play-token côté backend
- `PLAN_CONCEPTION_LAUNCHER.md` §9.4–9.5 — spec de l'integrity loop
