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

> **Livraison (0.2.6, 01/11)** : les pistes arrivent par le manifest signé
> du launcher (`reborn/ost/<catégorie>/*.ogg`, fichiers `required`
> téléchargés avant tout lancement : le bouton Jouer ne lance le jeu que
> lorsque le plan de téléchargement est vide). L'ancien seed de 43 pistes
> (`assets/reborn-ost/seed-bundle/`, 71 Mo) n'était plus jamais extrait
> (bibliothèque déjà peuplée) : il reste dans les sources mais est exclu du
> jar (73,5 Mo → ~0,1 Mo) et `OstSeedExtractor` est retiré. En dev
> (`runClient`), déposer soi-même une piste dans `run/reborn/ost/<catégorie>/`.

## Lecteur en haut à droite (0.2.6)

Le lecteur est dessiné par `reborn-hud` (élément « Musique » de l'éditeur
de HUD, `ost/OstHud`). Ce mod publie l'état de la lecture dans
l'`ObjectShare` de Fabric Loader, clé `reborn-ost:lecture` =
`Object[]{String[]{trackId, titreHud}, long[]{état 0/1/2, audible 0/1,
changéÀ}}`, deux tableaux partagés par référence et mis à jour à chaque fin
de tick (`OstLecture`). Le titre du HUD est la catégorie de la piste
(« Mystère », « Combat »…), jamais le nom de fichier. Le bandeau
`ui/OstHudOverlay` ne sert plus que de secours : il n'est dessiné que si
`reborn-hud` n'a pas déposé `reborn-hud:ost-rendu` au `CLIENT_STARTED`
(reborn-hud 0.4.194 ou plus ancien). Banc : `REBORN_OST_BANC=1`.

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

**Lecture en flux** (0.2.6, revue du 02/11) : le .ogg est gardé en mémoire
(quelques Mo) et décodé par tranches de 8 192 images (≈ 0,19 s) dans 12
tampons OpenAL en file sur la source (≈ 2,2 s d'avance). Au lancement,
4 tranches seulement sont décodées sur le fil qui appelle ; le fil
`Reborn-OST` remplit le reste toutes les 40 ms. Avant, toute la piste était
décodée d'un coup sur le fil principal (≈ 109 Mo de PCM et plusieurs
secondes de gel pour une piste de 10 min). Saut et rattrapage par
`stb_vorbis_seek` ; une source arrêtée faute de son repart, seule la fin du
fichier est une fin de piste.

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
