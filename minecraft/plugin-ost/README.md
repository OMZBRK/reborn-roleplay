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

## Test E2E manuel

1. `./gradlew runServer` (laisser tourner).
2. Sur un autre terminal : `cd ../mod-ost && ./gradlew runClient`.
3. Dans le client, rejoindre `localhost`.
4. Dans le serveur : `op <pseudo>` puis `/ost playglobal combat/test 1.0`.
5. Côté client : le warning "trackId inconnu" doit apparaître dans
   `logs/latest.log` (puisque `combat/test.ogg` n'existe pas par défaut).
   Si tu drop un vrai `.ogg` dans `~/.minecraft/reborn/ost/combat/test.ogg`
   et que tu relances, le son joue.
