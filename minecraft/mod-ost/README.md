# Reborn OST (mod client Fabric)

Lecteur audio in-game pour la BGM Reborn Roleplay. 100% client-side
(`environment: client`), aucun impact sur les serveurs vanilla.

## Build

**JDK 21 requis.** Fabric Loom refuse de configurer le projet avec un
Java daemon < 21. Si tu as Corretto 21 installé en parallèle d'un autre
JDK (ex: 17 par défaut), exporte `JAVA_HOME` avant chaque commande :

```pwsh
$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk21.0.9_10"
./gradlew build       # → build/libs/reborn-ost-<ver>.jar
./gradlew runClient   # client MC 1.21.1 avec le mod
./gradlew test        # JUnit5 — codec packets + scan filesystem
```

Ou lance via IntelliJ qui détecte le toolchain `JavaLanguageVersion.of(21)`
automatiquement (cf `build.gradle.kts`).

## Fichiers audio

**Les .ogg ne sont PAS packagés dans le jar.** Le mod scan
`~/.minecraft/reborn/ost/<categorie>/<nom>.ogg` au démarrage (voir
`OstLibrary`). Format requis :

- Conteneur : Ogg Vorbis (.ogg)
- Canaux    : **mono** (sinon attenuation par distance désactivée)
- SampleRate: 44100 Hz recommandé

À la première exécution, le mod crée la structure de dossiers + un
`README.txt` qui rappelle ce format.

> TODO (futur) : le launcher Reborn alimentera ce dossier via le
> manifest signé. Cf `PLAN_CONCEPTION_LAUNCHER.md §9`.

## Keybind

Par défaut `M` (remappable via Options → Commandes Minecraft → catégorie
"Reborn OST").

## Modes

- **Mode Solo (ON par défaut)** : ignore les broadcasts serveur. Tu
  contrôles ce que tu écoutes depuis le menu OST.
- **Mode Solo OFF** : reçoit et joue les broadcasts émis par le plugin
  `reborn-ost-plugin` (commandes `/ost play|playat|playglobal|stop`).

## Architecture audio

Décodage Ogg Vorbis via `org.lwjgl.stb.STBVorbis` + lecture via
`org.lwjgl.openal.AL10` directement — pas de dépendance externe
(LWJGL est déjà bundlé par Minecraft). Cf `OstAudioEngine`.

Limitation connue : la position du listener OpenAL est gérée par le
`SoundEngine` vanilla, donc l'attenuation 3D suit naturellement la
position du joueur. Les .ogg stéréo ne bénéficient pas de l'attenuation
positionnelle (limitation OpenAL native).

## Tests

```pwsh
./gradlew test
```

Couvre :
- Round-trip encode/decode des 3 types de packets `reborn:ost`.
- Rejet propre d'un type byte inconnu.
- Buffer tronqué → exception attrapée par le handler (pas de crash).
- Scan filesystem : .ogg case-insensitive, autres extensions ignorées,
  catégories rangées, favorites filtrées vs scan.
