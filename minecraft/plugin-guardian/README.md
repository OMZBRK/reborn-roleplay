# Reborn Guardian

Plugin Paper qui valide les clients Reborn Roleplay (cf `PLAN_CONCEPTION_LAUNCHER.md` §9.5).

## Stack

- **Paper API** 1.21.1 (R0.1-SNAPSHOT — aligne sur le serveur Reborn dev)
- **Java 21** toolchain
- **Gradle** 8.12.1 (Kotlin DSL — paperweight 2.x exige >= 8.12)
- **paperweight-userdev** (decompilation/remap automatique des sources Paper)
- **run-paper** (lance un serveur Paper de test directement via Gradle)

## Setup IntelliJ (recommande)

1. Ouvre **IntelliJ IDEA** (Community ou Ultimate, peu importe ici)
2. `File → Open` → selectionne **ce dossier** (`minecraft/plugin-guardian/`),
   pas la racine du monorepo
3. IntelliJ detecte automatiquement le projet Gradle et :
   - telecharge Gradle 8.10.2 si tu ne l'as pas
   - lit `build.gradle.kts` et resout les deps Paper (~5-10 min la premiere fois)
   - configure le SDK Java 21 (a verifier dans `File → Project Structure → Project SDK`)
4. Si IntelliJ ne te propose pas Java 21 : `File → Project Structure → SDKs → +` →
   pointe sur ton install JDK 21. Pour `winget` :
   ```pwsh
   winget install Microsoft.OpenJDK.21
   ```

## Build et test

Depuis IntelliJ, ouvre le panneau **Gradle** (a droite) et clique sur :

- `Tasks/build/build` — compile et package en `build/libs/reborn-guardian-0.1.0-dev.jar`
- `Tasks/run paper/runServer` — lance un serveur Paper 1.21.1 local avec le
  plugin charge. Premier run : telecharge ~80 Mo (Paper jar + worlds par
  defaut). Le serveur ecoute sur **localhost:25565** par defaut. Console
  interactive dans le panneau **Run** d'IntelliJ.

> **Note Windows / OneDrive** — le `runDirectory` est volontairement place
> dans `~/.reborn-guardian-run/` (hors Desktop). Si tu mets le projet dans
> un dossier synchronise (Desktop, Documents, OneDrive), le serveur Paper
> crashe au demarrage avec "session.lock : un autre processus en a
> verrouille une partie" parce que OneDrive intercepte les ecritures sur
> `world/session.lock`. Le placement hors-sync evite le probleme.

Depuis la ligne de commande (si Gradle est installe) :

```pwsh
./gradlew build
./gradlew runServer
```

> Le wrapper jar (`gradle-wrapper.jar`) n'est pas committe pour rester leger.
> IntelliJ le materialisera tout seul quand tu ouvres le projet, ou tu peux
> le generer avec `gradle wrapper` apres avoir installe Gradle (`winget install Gradle.Gradle`).

## Structure

```
plugin-guardian/
├── build.gradle.kts          # config Gradle + Paper + run-paper
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/           # version Gradle 8.10.2 (jar bootstrappe par IntelliJ)
└── src/main/
    ├── java/fr/reborn/guardian/
    │   ├── RebornGuardian.java        # main plugin class
    │   └── listener/PlayerJoinListener.java
    └── resources/
        └── paper-plugin.yml
```

## Mission actuelle

Vérification de l'attestation **play-token** envoyée par le mod
`reborn-integrity` au JOIN :

1. `PlayerJoinEvent` -> schedule un kick à T+8s via le scheduler
   Bukkit (`PendingAuthListener`).
2. `AuthChannelListener` reçoit le payload sur le canal Plugin
   Messaging `reborn:auth`.
3. `PlayTokenVerifier.verify(token)` recalcule HMAC-SHA256 avec
   `REBORN_PLAY_TOKEN_SECRET` partagé avec l'API, vérifie `exp`, et
   décode le payload JSON.
4. Vérification additionnelle : `payload.mcUuid == player.getUniqueId()`
   (sinon = token volé d'un autre joueur -> kick).
5. Si tout passe, `AuthSessionState.markAuthenticated` annule le kick.

### Pièges connus

- Le secret HMAC vit **uniquement** dans `.env` (API) et
  `System.getenv()` (plugin Paper). Le mod et le launcher ne le voient
  jamais.
- `./gradlew runServer` du plugin ne lit pas le `.env` du monorepo —
  le `build.gradle.kts` a un fallback dev hardcodé. Si tu l'utilises,
  mets la même valeur côté API.
- `MessageDigest.isEqual` est constant-time (JDK 6u17+) ; **ne pas**
  le remplacer par `Arrays.equals` qui short-circuite.
- `AuthChannelListener#kick` re-poste sur le main thread via
  `Bukkit#getScheduler#runTask` parce que les plugin messages peuvent
  arriver async selon les versions Paper.

## Roadmap

| Étape | État |
|---|---|
| Scaffold + listener bidon | Fait |
| Validation play-token HMAC (`PlayerJoinEvent` + canal `reborn:auth`) | Fait (`f43ae65`) |
| Webhook Discord (kick/ban) | TODO §10.12 |
| API HTTP interne (sanctions live de l'API Reborn) | TODO §9.5 |
| Anti-cheat passif (mouvement, NBT, vitesse) | v1.x |

## Note sur le placement dans le monorepo

Ce dossier est volontairement **isole** du monorepo pnpm — il a son propre
build Gradle, son propre Java, sa propre publication. Ne le `pnpm install`
pas, ne le mets pas dans le `pnpm-workspace.yaml`. Quand tu travailles dessus,
ouvre **uniquement** `minecraft/plugin-guardian/` dans IntelliJ.
