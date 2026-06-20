# Reborn OST Plugin

Plugin Paper qui broadcast les pistes OST aux clients équipés du mod
`reborn-ost` via le canal custom plugin messaging `reborn:ost`.

Compatible serveur Paper VANILLA — pas de dépendance à d'autres plugins
ou mods serveur. Les clients sans le mod ignorent silencieusement les
plugin messages.

## Build

```pwsh
./gradlew build       # → build/libs/reborn-ost-plugin-<ver>.jar
./gradlew runServer   # serveur Paper 1.21.1 local avec le plugin
```

## Commandes

Permission requise : `reborn.ost.broadcast` (op par défaut).

| Commande | Effet |
|---|---|
| `/ost play <trackId> <radius> [volume]` | Broadcast positionnel depuis le sender |
| `/ost playat <world> <x> <y> <z> <trackId> <radius> [volume]` | Broadcast positionnel explicite |
| `/ost playglobal <trackId> [volume]` | Broadcast global tous joueurs |
| `/ost stop [radius]` | Stop pour les joueurs dans radius (ou tous) |

`trackId` est libre, format `categorie/nom-fichier` (sans extension).
Le client résout vers `~/.minecraft/reborn/ost/<categorie>/<nom>.ogg`.
Si le fichier n'existe pas côté client, warning log mais pas de crash.

## Zone registry + late-join sync (Phase 2, livrée 2026-06-11)

Pour qu'un joueur qui se reconnecte (ou se téléporte) **dans** une
zone OST active reprenne la track au bon timestamp, le plugin
maintient un registre :

- `OstZoneRegistry` : `Map<zoneId, ZoneRecord>` (track, position,
  radius, `startedAtMs`) + `Map<playerUUID, Set<zoneId>>`
  (subscriptions actives par joueur).
- `OstBroadcaster.playAtPosition()` enregistre la zone et envoie un
  PLAY initial avec `secOffset = 0`.
- `OstBroadcaster.tick()` (scheduler 1Hz) scanne joueurs × zones
  actives : toute nouvelle subscription déclenche un PLAY avec
  `secOffset = (now - startedAtMs) / 1000`.
- Cleanup : `PlayerQuitEvent` purge les subs ; `removeZonesNear`
  purge les orphan subs.
- Zones persistent jusqu'à `/ost stop` explicite — pas d'expiration
  auto (le plugin ne connaît pas la durée des tracks).

### Late-join robuste après JOIN

`PlayerJoinEvent` schedule un `scanPlayer(player)` ciblé avec un delay
de **60 ticks (3s)** parce que le tick périodique (1s) peut firer
pendant la fenêtre où le canal réseau client n'est pas encore prêt
après respawn → `sendPluginMessage` réussit mais le receiver client
n'est pas branché → drop silencieux. Le scan retardé garantit qu'on
ré-envoie après que le client est prêt.

## Wire format

Le packet `PlayAtPosition` inclut un champ `secOffset:float` (offset
en secondes). Cf `mod-ost/src/test/.../PacketCodecTest` pour le
round-trip.

## Test E2E manuel

1. `./gradlew runServer` (laisser tourner).
2. Sur un autre terminal : `cd ../mod-ost && ./gradlew runClient`.
3. Dans le client, rejoindre `localhost`.
4. Dans le serveur : `op <pseudo>` puis `/ost playglobal combat/test 1.0`.
5. Côté client : le warning "trackId inconnu" doit apparaître dans
   `logs/latest.log` (puisque `combat/test.ogg` n'existe pas par défaut).
   Si tu drop un vrai `.ogg` dans `~/.minecraft/reborn/ost/combat/test.ogg`
   et que tu relances, le son joue.

### E2E late-join

1. Lance `/ost play <track> 50 1.0` (ou playglobal).
2. Disconnect côté client (Esc → Save and Quit).
3. Attends ~5s, reconnect.
4. La track doit reprendre **au timestamp courant** (pas du début).
   Testé OK à 89s, 171s, 633s.

## Limites résiduelles connues

- Reconnect <5s peut rater le scan. Fix possible : gating du tick par
  join-age côté serveur.
- Multi-zones : last-wins côté mod (pas de sélection par distance).
- Téléportation : pas de STOP envoyé à l'ancienne zone (le mod gère
  via `play()` qui stop la précédente).

## Voir aussi

- `minecraft/mod-ost/` — côté client, décodage et lecture audio
- `PLAN_CONCEPTION_LAUNCHER.md §9.6` — spec écosystème mods Reborn
