# Reborn OST (mod client Fabric)

Lecteur audio in-game pour la BGM Reborn Roleplay. 100% client-side
(`environment: client`), aucun impact sur les serveurs vanilla.

## Build

**JDK 21 requis.** Fabric Loom refuse de configurer le projet avec un
Java daemon < 21. Si tu as plusieurs JDK installés, exporte `JAVA_HOME`
avant chaque commande :

```pwsh
$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk21.0.9_10"
./gradlew build       # → build/libs/reborn-ost-<ver>.jar
./gradlew runClient   # client MC 1.21.1 avec le mod
./gradlew test        # JUnit5 — codec packets + scan filesystem + atténuation
```

Ou lance via IntelliJ qui détecte le toolchain `JavaLanguageVersion.of(21)`
automatiquement (cf `build.gradle.kts`).

## Fichiers audio

**Les .ogg ne sont PAS packagés dans le jar.** Le mod scan
`~/.minecraft/reborn/ost/<categorie>/<nom>.ogg` au démarrage (voir
`OstLibrary`). Format requis :

- Conteneur : Ogg Vorbis (.ogg)
- Canaux    : **mono ou stéréo** — les deux supportés (cf Phase 1 ci-dessous)
- SampleRate: 44100 Hz recommandé

À la première exécution, le mod crée la structure de dossiers + un
`README.txt` qui rappelle ce format.

> **À venir** : le launcher Reborn alimentera ce dossier via le
> manifest signé. Trade-off à trancher : embed des 43 .ogg dans le jar
> (~74 MB) vs DL séparé via une archive listée dans le manifest. Cf
> `PLAN_CONCEPTION_LAUNCHER.md §9.6`.

## Keybind

Par défaut `M` (remappable via Options → Commandes Minecraft → catégorie
"Reborn OST"). Ouvre `OstScreen`.

## Modes

- **Mode Solo (ON par défaut)** : ignore les broadcasts serveur. Tu
  contrôles ce que tu écoutes depuis le menu OST.
- **Mode Solo OFF** : reçoit et joue les broadcasts émis par le plugin
  `reborn-ost-plugin` (commandes `/ost play|playat|playglobal|stop`).

## Architecture audio

Décodage Ogg Vorbis via `org.lwjgl.stb.STBVorbis` + lecture via
`org.lwjgl.openal.AL10` directement — pas de dépendance externe (LWJGL
est déjà bundlé par Minecraft). Cf `OstAudioEngine`.

### Atténuation positionnelle (Phase 1, livrée 2026-06-11)

Choix tech : on garde le **stéréo** pour préserver le master musical
immersif et on calcule l'atténuation par distance **à la main côté mod**
(OpenAL refuse de fade les buffers stéréo par spec — on bypass donc son
distance model).

- `OstAudioEngine.tickPositional(x,y,z)` recalcule `AL_GAIN` chaque
  tick client (20Hz) en fonction de la distance listener ↔ origine.
- Courbe linéaire clampée :
  `refDistance = 25% * radius` (core full volume) → fade linéaire vers
  0 jusqu'à `maxDistance = radius`.
- Hook `ClientTickEvents.END_CLIENT_TICK` dans `RebornOstClient`.
- Test unitaire `OstAudioEngineTest.distanceFactor` couvre 4 cas
  (in-ref, out-max, mid-fade, degenerate ref==max).

### Late-join sync (Phase 2, livrée 2026-06-11)

Quand un joueur entre dans une zone active (reconnect, téléport, /warp),
le plugin renvoie le packet `PlayAtPosition` avec un `secOffset`
calculé (`now - startedAtMs`). Le mod passe ensuite par
`AL11.AL_SEC_OFFSET` avant `alSourcePlay` pour reprendre au bon
timestamp. Si l'offset dépasse la durée de la track, OpenAL clamp et
la source passe `AL_STOPPED` (auto-clear côté mod).

## Tests

```pwsh
./gradlew test
```

Couvre :
- Round-trip encode/decode des packets `reborn:ost` (PLAY, PLAY_AT,
  STOP).
- Présence du champ `secOffset:float` dans `PlayAtPosition`.
- Rejet propre d'un type byte inconnu.
- Buffer tronqué → exception attrapée par le handler (pas de crash).
- Scan filesystem : .ogg case-insensitive, autres extensions ignorées,
  catégories rangées, favorites filtrées vs scan.
- Atténuation par distance (4 cas).

## Limites résiduelles connues (acceptables ship)

- **Reconnect très rapide (<5s)** rate parfois la track. Fix possible :
  gating du tick par join-age, ou bump période 1s → 3s côté plugin.
- **Multi-zones** : last-PLAY wins côté mod. Pas d'interpolation par
  distance entre deux zones qui se recouvrent.
- **Téléportation** : tick détecte bien l'entrée nouvelle zone mais
  n'envoie pas de STOP à l'ancienne ; le mod jongle via `play()` qui
  stop la précédente. OK single-zone, suspect multi.

## UI dans le menu principal

Le **lecteur OST visible dans le menu principal** (carte coin
haut-droite avec play/pause/skip et popup volume / playlist) vit dans
`mod-hud/` (séparation : `mod-ost/` = audio, `mod-hud/` = UI). Le mod
HUD utilise l'API publique d'`OstAudioEngine` pour piloter.
