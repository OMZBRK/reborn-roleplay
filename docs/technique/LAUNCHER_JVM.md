# Launcher — préparation du jeu et profil JVM

> Code : `apps/launcher/src-tauri/src/launcher/` (`jvm.rs`, `runtime.rs`, `mojang.rs`,
> `assets.rs`, `libraries.rs`, `fabric.rs`, `mods.rs`), `hardware/mod.rs`,
> `manifest/download.rs`. Rédigé le **2026-10-24**.
>
> Ce document explique *ce que la JVM reçoit* et *comment les fichiers du jeu sont
> vérifiés* entre le clic sur « Jouer » et l'ouverture de la fenêtre Minecraft.

## En une phrase

Profil G1 pensé **client** (pauses courtes, pas de pré-allocation), RAM bornée à
« RAM système − 2 Go », et des **témoins de vérification** qui évitent de re-hacher
~5500 fichiers Mojang à chaque lancement (≈ 0,4 s au lieu de plusieurs secondes,
voire dizaines de secondes avec un antivirus).

---

## 1. Profil JVM « client »

Construit par `jvm::heap_and_gc_flags`. Vérifié le 2026-10-24 sur le JRE Mojang
`java-runtime-epsilon` 25.0.1 (build Microsoft) et sur Temurin 25.0.4.1 : tous les
flags sont acceptés (`-XX:+PrintFlagsFinal -version`).

| Flag | Pourquoi |
|---|---|
| `-Xmx<N>M` | RAM choisie dans Paramètres › Jeu, bornée (§2). |
| `-Xms<N/2>M` (plancher 1024) | Évite les redimensionnements du tas pendant le chargement des mods, sans réserver toute la RAM d'entrée (cible : machines 4-8 Go). |
| `-XX:+UseG1GC` | GC par défaut, adaptatif, bon compromis débit/pauses sur un client. |
| `-XX:MaxGCPauseMillis=50` | Objectif de pause ≈ 3 images à 60 FPS. G1 ajuste la jeune génération pour le tenir. |
| `-XX:+UnlockExperimentalVMOptions` | Requis par `G1NewSizePercent` ; **doit précéder** les flags expérimentaux (testé). |
| `-XX:G1NewSizePercent=20` | Jeune génération minimale plus grande : le client alloue énormément d'objets très courts (rendu, maillage des chunks). |
| `-XX:G1ReservePercent=20` | Marge contre l'épuisement de l'espace de copie (pics de chargement de chunks). |
| `-XX:G1HeapRegionSize=8M` | Moins d'allocations « humongous » (tampons de textures, de maillage). |
| `-XX:+ParallelRefProcEnabled` | Traitement parallèle des références (déjà par défaut en 25, explicite). |
| `-XX:+DisableExplicitGC` | Neutralise les `System.gc()` de certains mods (GC complet = gel). |
| `-XX:+UseStringDeduplication` | Identifiants, clés NBT, noms de ressources : beaucoup de chaînes dupliquées. |
| `-XX:+UseCompactObjectHeaders` | En-têtes d'objets compacts : −10 à −20 % de tas. **Produit** depuis Java 25 (JEP 519, flag `{product}` constaté), plus besoin de déverrouillage. |
| `-XX:+PerfDisableSharedMem` | Pas de fichier `hsperfdata` mappé (écritures disque bloquantes sous antivirus). |
| `-Dstdout.encoding=UTF-8` / `-Dstderr.encoding=UTF-8` | Voir §5. |

**Retirés** (profil Aikar, pensé serveur Paper) :

| Flag retiré | Raison |
|---|---|
| `-XX:+AlwaysPreTouch` | Pré-touche tout le tas au boot : démarrage plus lent et RAM engagée d'emblée. |
| `-XX:InitiatingHeapOccupancyPercent=15` | Marquage concurrent quasi permanent : CPU volé au rendu sur les machines à peu de cœurs. On laisse l'IHOP adaptatif (défaut). |
| `-XX:MaxTenuringThreshold=1`, `-XX:SurvivorRatio=32` | Promotion immédiate en vieille génération : adapté à un tick serveur, pas à un client. |
| `-XX:G1MaxNewSizePercent=40`, `G1HeapWastePercent=5`, `G1MixedGCCountTarget=4`, `G1MixedGCLiveThresholdPercent=90`, `G1RSetUpdatingPauseTimePercent=5` | Réglages serveur ou valeurs par défaut : on garde les défauts adaptatifs de G1. |

---

## 2. RAM

Politique unique dans `hardware/mod.rs` :

| Valeur | Règle |
|---|---|
| Défaut | 4096 Mo (`Preferences::default`, `DEFAULT_RAM_MB`). |
| Plafond | RAM système − 2 Go, jamais sous 2048 (`max_ram_mb`). |
| Recommandée | min(4096, plafond) (`recommended_ram_mb()`). 16 Go → 4096 ; 5 Go → 3072 ; 4 Go → 2048. |
| Au lancement | `-Xmx = clamp(choix, 2048, plafond)` (`clamp_ram_mb`), avec un avertissement dans les logs si le choix est ramené. |

Exposé au front par la commande existante `system_specs_get` :
`recommendedRamMb` et **`maxRamMb`** (nouveau ; 16384 si la RAM système est inconnue).

---

## 3. Préparation des fichiers : témoins de vérification

### Ensembles

Chaque étape vérifie un **ensemble** de fichiers et, après une vérification
complète réussie, écrit un témoin dans `<game_dir>/.reborn-verify/<ensemble>.json` :

| Ensemble | Contenu | Empreinte |
|---|---|---|
| `runtime-java-runtime-epsilon` | ~410 fichiers du JRE | SHA-1 du manifeste piston-meta + liste (chemin, taille) stockée dans le témoin |
| `libraries-<mc>` | client jar + libraries (+ jars natives) | SHA-1 des lignes « chemin\|sha1\|taille » triées |
| `assets-<index>` | ~5000 objets d'assets | idem |
| `fabric-<loader>-<mc>` | libraries Fabric | idem |

### Chemin rapide

Si le témoin porte la même empreinte que l'ensemble attendu **et** que chaque fichier
existe avec la bonne taille (`stat`, un seul aller-retour sur le pool bloquant), on
ne relit rien. Pour le JRE, la liste attendue est celle du témoin : **zéro réseau**.

Sinon, **vérification complète comme avant** : SHA-1 de chaque fichier présent,
téléchargement de ce qui manque ou diffère, puis réécriture du témoin. Un fichier
supprimé ou tronqué (antivirus, nettoyage manuel) déclenche donc la vérification
complète au lancement suivant. Limite assumée : un fichier remplacé par un contenu
différent **de même taille** n'est détecté que par « Réparer ».

Mesures (2026-10-24, copie du game dir de dev, cache disque chaud) :
vérification complète **4,1 s**, chemin rapide **0,42 s** (dont 0,33 s pour les
5057 assets). Sur un disque froid avec antivirus, l'écart est bien plus grand.

### « Réparer »

`launcher::mojang::force_full_verify(&game_dir)` supprime `.reborn-verify/` :
le lancement suivant refait la vérification complète de tout (et réinterroge le
catalogue Java de Mojang). Idempotent. Exposé par la commande Tauri
`launcher_repair` (refusée pendant une partie, kind `game_running`), appelée par
le bouton « Réparer les fichiers du jeu » de Paramètres › Jeu, qui enchaîne avec
`launcher_apply_update` pour resynchroniser tout de suite le modpack.

### Caches et repli hors ligne

- **Version json** (`versions/<mc>/<mc>.json`) : si piston-meta est injoignable, on
  relance avec le cache (vérifié à son écriture).
- **Profil Fabric** (`versions/<mc>/fabric-profile.json`) : on prend toujours le
  dernier loader stable publié, mais en cas de panne de Fabric Meta on relance avec
  le dernier profil connu.
- **JRE** : si le catalogue est injoignable et qu'un `javaw.exe` est déjà installé,
  on l'utilise sans vérification (comportement d'avant les témoins).
- Délai des requêtes de métadonnées : 15 s (`METADATA_TIMEOUT`).

Testé hors ligne (proxy injoignable) : avec témoins et caches, la préparation
aboutit ; sans cache Fabric, l'erreur est explicite (§4).

### Téléchargements

- Tous les fichiers Mojang/Fabric passent par `mojang::download_file` : flux haché
  à la volée, écrit en `.part` puis renommé ; 4 tentatives (backoff 0,5 s / 1 s / 2 s)
  sur les erreurs réseau transitoires et disque ; **un hash faux reste fatal**
  (et c'est bien ce mismatch, attendu/obtenu, qui remonte).
- Concurrence bornée par `buffer_unordered` : 8 (assets, libraries), 6 (JRE),
  4 (Fabric, manifest Reborn). La progression avance au fil des fichiers terminés.
- Entrées dupliquées (même objet d'asset sous deux noms) dédupliquées : elles se
  marchaient dessus sur le même `.part`.
- Natives : un fichier déjà extrait à la même taille n'est pas réécrit (une 2e
  instance garde les `.dll` de la première verrouillées).

### Manifest Reborn (mods)

Les fichiers du manifest signé sont **toujours** re-hachés en SHA-256 au calcul du
plan : c'est la surface anti-triche du modpack, pas de témoin taille/date ici. Le
hachage est parallélisé (4) et fait hors du runtime async. `manifest:progress` est
émis à chaque fichier terminé **et** toutes les 250 ms (octets reçus).

---

## 4. Messages d'erreur

Toutes les erreurs de ces modules sont des phrases françaises complètes qui portent
la **vraie cause** et **quoi faire** :

- réseau : hôte seul (jamais l'URL complète, qui peut porter des paramètres signés),
  code HTTP, cause racine OS — ex. « Service Fabric (meta.fabricmc.net) injoignable
  et aucun profil en cache : connexion impossible à meta.fabricmc.net (Aucune
  connexion n'a pu être établie… (os error 10061)) — vérifie ta connexion internet,
  ton pare-feu ou ton antivirus » ;
- disque : chemin complet + raison OS (localisée par Windows) + piste (antivirus,
  disque plein, fichier ouvert par le jeu) ;
- intégrité : fichier, SHA attendu, SHA obtenu, et « Réparer » ;
- version : « Minecraft X exige Java Y » si la version MC demande un Java plus
  récent que le JRE fourni (au lieu d'un `UnsupportedClassVersionError` au boot).

Helpers partagés : `mojang::describe_http_error`, `mojang::describe_io_error`.

---

## 5. Encodage des sorties du jeu

Quand stdout/stderr sont **redirigés** (cas du launcher), la JVM Windows écrit en
`native.encoding`, soit **Cp1252** sur un Windows français (constaté :
`stdout.encoding = Cp1252` sans option). Les accents (chemins `C:\Users\Clément`,
pseudos, traductions) arrivaient alors en octets non UTF-8 côté Rust. Le launcher
force `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` (valeur explicitement
supportée par la JDK) ; la lecture côté Rust reste tolérante de toute façon.

---

## 6. Pièges

- **URL du catalogue Java** (`runtime.rs::ALL_JSON_URL`) : Mojang la fait tourner.
  Un 404 donne « l'adresse du catalogue a changé : mets à jour le launcher ». Seules
  les nouvelles installations et « Réparer » l'interrogent.
- **Version du loader Fabric** : le launcher prend le dernier loader **stable**
  publié pour la version MC (0.20.1 le 2026-10-24), pas une version figée.
- **Java requis** : `RUNTIME_COMPONENT` et `RUNTIME_MAJOR` (runtime.rs) vont ensemble ;
  à rebumper ensemble si une version MC exige un nouveau Java.
- **Ordre des flags** : un flag expérimental placé avant
  `-XX:+UnlockExperimentalVMOptions` fait échouer le démarrage de la JVM (test
  `unlock_precedes_experimental_flags`).
