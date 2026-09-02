# Emotes RP (`/playemote`)

Système d'emotes serveur-autoritaire branché sur **EmoteCraft**. Une emote jouée
par un joueur est **visible par tous les joueurs proches** (comme les coups de
taïjutsu), déclenchable par commande, depuis le menu interactif, et depuis
**MagicSpells**.

## Vue d'ensemble

```
/playemote wave          MagicSpells commandspell          Menu interactif (R)
        │                        │                                │
        └──────────────┬─────────┴────────────────────────────────┘
                       ▼
        ShinobiCore · EmoteManager  (résout via emotes.yml)
                       │  diffuse {entityId, nom} sur reborn:emote
                       ▼  aux joueurs proches (≤ portée), acteur inclus
        mod-hud · EmoteAnimations  (chaque client)
                       │  résout le nom dans EmoteHolder.list (registre EmoteCraft)
                       ▼
        EmoteCraft joue l'animation sur l'avatar visé
```

Points clés :

- **Aucune animation n'est embarquée** dans les mods Reborn. On rejoue les emotes
  qu'**EmoteCraft** a déjà chargées côté client : ses emotes built-in, le dossier
  `emotes/` du client, et (le cas échéant) les emotes distribuées par le serveur.
- Le serveur ne transmet qu'un **nom résolu** ; chaque client le rejoue localement
  sur l'avatar → tout le monde voit l'emote. Purement visuel, le serveur reste
  autoritaire sur le gameplay.
- Marche sur un serveur **Paper pur** (pas besoin d'EmoteCraft côté serveur pour la
  lecture — voir « Hot-add » plus bas pour le cas serveur-distribué).

## Commandes

| Commande | Effet | Permission |
|---|---|---|
| `/playemote <nom>` | Joue l'emote sur soi | `shinobicore.emote` (true) |
| `/playemote <nom> <joueur>` | Joue sur un autre (staff / console) | `shinobicore.emote.others` (op) |
| `/playemote reload` | Recharge `emotes.yml` | `shinobicore.emote.admin` (op) |
| `/stopemote [joueur]` | Arrête l'emote en cours | `shinobicore.emote` |

Alias : `/emote`, `/pe` (play) ; `/se` (stop).

## Accès en jeu

- **Menu Reborn** (touche `.`) → onglet **ANIMATIONS** : liste des emotes chargées,
  molette pour défiler, clic pour jouer. « Arrêter l'emote » en tête.
- **Menu interactif** (touche `R`) → clic dans le vide → **Mes emotes** (raccourcis
  + « Toutes les emotes… » qui ouvre le menu ci-dessus). Le sous-menu **Animations**
  d'un joueur visé propose aussi les raccourcis.

## Catalogue `emotes.yml` (géré par les devs via le panel)

`plugins/ShinobiCore/emotes/emotes.yml` — accessible depuis le **panel staff**
(Fichiers) pour les grades Modélisateur / Développeur. Après édition :
`/playemote reload`.

```yaml
open-mode: true      # true = toute emote chargée est jouable par son nom
range: 48.0          # portée de diffusion (blocs)
emotes:
  wave:
    display: "Saluer"
    emote: "wave"    # nom de l'emote EmoteCraft à jouer
    aliases: [saluer, salut]
    permission: ""   # vide = tout le monde ; sinon un node de permission
```

- `open-mode: true` : pratique pour tester — n'importe quelle emote chargée côté
  client est jouable, même non déclarée. Passe à `false` pour verrouiller la liste.
- Le champ `emote` doit correspondre au **nom d'une emote EmoteCraft** chargée (voir
  la liste dans le menu `.` → ANIMATIONS, ou la roue EmoteCraft).

## MagicSpells (`commandspell`)

Un `commandspell` qui lance `/playemote` : voir
[`minecraft/server-config/magicspells/spells-emotes.yml`](../minecraft/server-config/magicspells/spells-emotes.yml).
Le pont est le même `Bukkit.dispatchCommand` que ShinobiAbilities utilise déjà.

```yaml
Salut:
  spell-class: ".command.CommandSpell"
  cast-item: ""
  execute-commands: [playSalut]
  playSalut:
    command: "playemote %a% wave"   # %a% = lanceur ; run par la console
    as-console: true
  # forcer sur une CIBLE : "playemote %t% wave" avec un TargetedSpell en amont.
```

## Distribution serveur — « drop + link serveur, visible par tous »

Les devs peuvent ajouter des emotes **sans mise à jour du mod ni serveur Fabric** : il
suffit de déposer un fichier `.emotecraft` (ou `.json`) dans
`plugins/ShinobiCore/emotes/` (accessible via le **panel dev**). ShinobiCore les pousse
à **chaque client** à la connexion (canal `reborn:emotepack`) ; mod-hud les enregistre
dans EmoteCraft → jouables par `/playemote <nom>` **pour tous**.

- Le **nom** de l'emote = le nom du fichier sans extension (ex. `kenjutsu_slash1.emotecraft`
  → `/playemote kenjutsu_slash1`).
- Après avoir déposé/retiré des fichiers : **`/playemote reload`** (re-scanne le dossier
  et re-pousse aux joueurs connectés).
- Optionnel : déclarer un alias FR + permission dans `emotes.yml` (`emote:` = nom du fichier).
- Garde-fou : un fichier > 512 Ko est ignoré (les emotes font quelques Ko).

Pipeline de création : Blockbench / éditeur EmoteCraft → export `.emotecraft` → drop dans
le dossier → `/playemote reload`. Voir aussi [`emote-geckolib-import`] (mémoire projet).

## Modèle : animations M1 kenjutsu via emote

Objectif : un coup M1 (clic gauche) avec une arme de kenjutsu joue une **animation de
swing** visible par tous. Recette **sans nouveau code** (data-driven), une fois le swing
créé comme emote et distribué (ci-dessus) :

1. Le dev crée les swings (`kenjutsu_slash1.emotecraft`, `…2`, `…3` pour un combo) et les
   dépose dans `plugins/ShinobiCore/emotes/` → distribués à tous.
2. Sur l'arme (item Nexo katana), un sort MagicSpells `commandspell` déclenché au **clic
   gauche** joue le swing :
   ```yaml
   KenjutsuSlash:
     spell-class: ".command.CommandSpell"
     cast-item: <id-nexo-katana>
     cast-with-left-click: true
     cooldown: 0.4
     execute-commands: [slash]
     slash:
       command: "playemote %a% kenjutsu_slash1"
       as-console: true
   ```
   Pour un combo, alterner via plusieurs sorts + un `cycle` MagicSpells, ou une variable.
3. `/playemote` diffuse l'anim aux joueurs proches → **tout le monde voit le coup**.

Alternative (si le M1 doit rester géré par ShinobiCombat) : brancher, dans le handler M1
de `ShinobiCombat` pour une arme de kenjutsu, un appel `plugin.emotes()`-style qui diffuse
`kenjutsu_slashN` au lieu (ou en plus) de l'anim taïjutsu — à faire quand les swings
existent.

## Hot-add d'emotes sans mise à jour du mod (à investiguer côté serveur)

EmoteCraft sait charger des emotes depuis un **dossier serveur** (`emotesDir` +
`loadEmotesServerSide` dans sa config) et les **pousser aux clients** à la connexion
(`UniversalEmoteSerializer.SERVER_EMOTES` / `preparePackets()`, `ServerEmoteAPI`).
Ça permettrait aux devs de **déposer un `.emotecraft` dans un dossier serveur (via
le panel)** et de le voir jouable en jeu sans republier le mod.

**Prérequis / à vérifier sur l'hébergeur (Minestrator) :**

1. Le loader du serveur : les classes serveur d'EmoteCraft (`ServerEmoteAPI`,
   `server.network.*`) ne tournent que sur un serveur **Fabric ou hybride**
   (Mohist/Arclight/Banner), **pas sur Paper pur**. Confirmer le type de serveur.
2. Le dossier d'emotes exposé (nom réel dans le gestionnaire de fichiers). Une fois
   connu, il est ajouté au **scope Fichiers du panel** pour les devs (voir
   `apps/api/src/files/files.service.ts`, map `SCOPES`).

Tant que ce n'est pas confirmé, la lecture (`/playemote`, menu, MagicSpells)
fonctionne déjà avec les emotes présentes côté client.
