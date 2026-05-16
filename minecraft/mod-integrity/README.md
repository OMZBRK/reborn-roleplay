# Reborn Integrity

Mod **Fabric client-side** qui atteste le client aupres du serveur Reborn
au moment du JOIN, via un play-token signe par l'API.

## Architecture du flow

```
[launcher Rust]
   |  POST /v1/play/session  (JWT user, gate role != PLAYER)
   v
[API NestJS]
   |  retourne playToken = base64(payload).base64(HMAC-SHA256(secret, payload))
   v
[launcher Rust] ecrit dans <gameDir>/.reborn-play-token, lance JVM avec
   -Dreborn.playTokenPath=<chemin absolu>
   |
   v
[mod Reborn Integrity ICI]
   onInitializeClient :
     - lit le fichier pointe par la sysprop
     - registre AuthPayload sur le canal C2S "reborn:auth"
     - ClientPlayConnectionEvents.JOIN -> envoie le payload au serveur
   |
   v
[plugin Reborn Guardian]
   - PlayerJoinEvent schedule un kick a T+8s
   - sur reception "reborn:auth" : verifie HMAC + UUID match + exp,
     marque le joueur OK -> annule le kick
```

Le **secret HMAC** est partage entre l'API et le plugin Guardian.
Le launcher et le mod ne le voient pas — ils ne font que transporter le
token. Un client patche/recompile ne peut donc pas forger d'attestation.

## Build

Le projet est isole du monorepo pnpm. Ouvre **ce dossier** dans IntelliJ
en standalone pour l'integration Gradle.

Premiere fois (genere `gradlew` + `gradlew.bat` pour les fois suivantes) :

```pwsh
gradle wrapper
```

Builds standards (apres avoir le wrapper) :

```pwsh
./gradlew build       # -> build/libs/reborn-integrity-<ver>.jar
./gradlew runClient   # lance un client MC 1.21.1 avec le mod
```

Le jar produit doit etre :

1. Embarque dans le manifest signe (cf `packages/manifest-signer`) sous
   `mods/reborn-integrity-<ver>.jar` avec son hash SHA-256.
2. Telecharge par le launcher dans `<gameDir>/mods/` au prochain check.

## Versions

Alignees sur `REBORN_MC_VERSION=1.21.1` du launcher et sur le serveur
Paper 1.21.1 du plugin Guardian. A bumper en lockstep.
