# Publier le train « jeu » — mod client et plugins serveur

> **À lire avant tout build, publication ou déploiement game-side.** Remplace l'ancien `PUBLISH_PREFLIGHT.md`
> (le script `scripts/publish-mod-manifest.ps1` y fait encore référence sous ce nom). Mis à jour le 2026-10-12.
>
> Le train « web » (API, panel, bot sur le VPS) est dans [`MAINTENANCE.md`](./MAINTENANCE.md) et
> [`DEPLOY.md`](./DEPLOY.md). Le launcher : [`RELEASING.md`](./RELEASING.md).

---

## 0. Qui fait quoi

| Étape | Qui |
|---|---|
| Coder, compiler, **capturer les visuels**, vérifier les YAML, écrire le **prompt de publication** | Claude (session de travail) |
| Commiter, publier le mod, déployer les plugins, **pousser sur GitHub** | La **session terminal de l'utilisateur**, à partir du prompt, **avec son token** |
| **Redémarrer le serveur** (panel Minestrator) et tester en jeu | L'utilisateur |

- **Claude ne pousse jamais de lui-même** (règle du 2026-10-06). Le push part de la session terminal, sur
  instruction de l'utilisateur, avec son token.
- **Publier implique commiter** : tout ce qui part en prod ou sur le dev revient sur `origin/main` dans la foulée
  (sinon plus personne ne peut reproduire ce que les joueurs exécutent — c'est arrivé avec le launcher 0.3.42 et
  `reborn-hud` 0.4.134).
- **Deux trains, jamais dans le même commit** : jeu (mods + plugins) et web (API, panel, bot).

## 1. Avant de publier — la checklist

1. **Visuels montrés** — chaque nouvel écran, HUD ou overlay a été capturé dans le client de dev
   ([`../technique/MOD_HUD.md`](../technique/MOD_HUD.md) §5) et **montré à l'utilisateur**, retours intégrés.
2. **Version suivante libre** — partir de la version **en production** (`docs/ETAT_DES_LIEUX.md`, dernier
   `secrets/manifest-signed-v*.json`, ou `manifest-uploader get-manifest`), prendre la suivante. **Jamais de
   régression, jamais deux fois le même numéro.** Une autre session a peut-être publié entre-temps : vérifier
   `git log` et le manifest live. Monter `mod_version` dans `minecraft/mod-hud/gradle.properties`.
3. **Compilation** — mod : `./gradlew compileJava --offline -q` ; plugins : `mvn -o -q clean package -pl
   ShinobiAbilities -am` depuis `minecraft/shinobi` (**`-am` obligatoire**, sinon ShinobiCore n'est pas résolu).
4. **YAML validés avec un vrai parseur** — `plugin.yml` et `config.yml` de chaque plugin modifié, **et le
   `plugin.yml` extrait du jar**. Maven ne valide pas le YAML. Toute valeur contenant `: `, `#`, `[`, `|` se met
   entre guillemets. (Incident du 2026-10-11 : un « : » non quoté a empêché ShinobiCore de se charger, tous les
   plugins Shinobi sont tombés et la sélection de personnage a cassé.)
   ```bash
   python -c "import yaml,io,sys;[yaml.safe_load(io.open(f,encoding='utf-8')) for f in sys.argv[1:]];print('OK')" \
     minecraft/shinobi/ShinobiCore/src/main/resources/plugin.yml minecraft/shinobi/ShinobiCore/src/main/resources/config.yml
   ```
5. **Poids du jar** — `reborn-hud` fait ~23,9 Mo. Un jar qui grossit fortement contient un asset intrus (cas réel :
   74 Mo d'OST et un HDRI de 17 Mo glissés dedans, 21 → 94 Mo). L'OST est livrée **par le manifest**, jamais dans
   le jar. Le script refuse au-delà de ×1,5 (sauf `-AllowSizeJump`).
6. **Périmètre du commit** — stager **fichier par fichier** (jamais `git add -A` : plusieurs sessions partagent le
   dossier). Exclure ce qui appartient à d'autres sessions (aujourd'hui : `tools/blockbench-handpaint/**`,
   `tools/bbforge/**`, `minecraft/shinobi/ShinobiSense/**`, `scripts/publish-mod-manifest.ps1`).

## 2. Publier le mod (`reborn-hud`, `reborn-ost`, `reborn-integrity`)

```pwsh
$env:JAVA_HOME = "C:\Users\omarb\dev-cache\jdk-25.0.4.1+1"
.\scripts\publish-mod-manifest.ps1 -Version 0.4.170              # reborn-hud
.\scripts\publish-mod-manifest.ps1 -Mod mod-ost -Version 0.2.5   # autre mod
.\scripts\publish-mod-manifest.ps1 -Version 0.4.170 -DryRun      # s'arrête après signature
```

Le script fait tout le parcours : vérifie `mod_version`, build propre (`clean build -x test`), contrôle du poids,
part du dernier manifest signé, **remplace l'unique entrée du mod** (vérifie qu'une seule a changé), monte la
version du manifest (patch +1), signe en Ed25519 et vérifie, crée la release GitHub `mods-v…`, retélécharge l'URL
(HTTP 200 + même sha256), et envoie le manifest à l'API (**HTTP 201 exigé**). Options : `-SkipBuild` (relance après
un 401), `-ManifestVersion`, `-AllowSizeJump`, `-Notes`.

Prérequis : JDK 25, `secrets/manifest_ed25519_private.pem` + `…_public.pem`, `gh` authentifié,
`packages/manifest-uploader` compilé (`cargo build --release`), **session staff Microsoft active dans le launcher**
(l'uploader lit le JWT dans le gestionnaire d'identifiants Windows).

> ⚠️ Le script a été réécrit pour la 26.2 le 2026-10-08 mais **n'est pas commité** (chantier d'une autre
> session). Ne pas le modifier.

**Incidents connus**

| Symptôme | Cause | Que faire |
|---|---|---|
| HTTP 401 « Refresh token replay » / « Session de l'uploader expirée » | Depuis le 15/10, l'uploader a **sa propre famille de sessions** (entrées `fr.reborn-rp.manifest-uploader` du gestionnaire d'identifiants), ouverte depuis le jeton du launcher sans le consommer, et un replay ne coupe plus que sa famille. Si elle a expiré et que le jeton du launcher est mort aussi | Ouvrir le launcher, **reconnexion Microsoft**, puis relancer **une seule fois** l'uploader (`manifest-uploader manifest --file …`) — ne pas réessayer en boucle |
| Statut HTTP invisible (sortie tronquée) | — | Vérifier avec `manifest-uploader get-manifest`, **ne jamais renvoyer** deux fois |
| `issuedAt` dans le futur | Horloge / date saisie | Re-signer avec l'heure réelle |
| Le joueur a encore l'ancienne version | Le launcher a lu le manifest avant la publication | Relancer le launcher ; vérifier la version du jar dans `mods/` |

Après publication : mettre à jour `docs/ETAT_DES_LIEUX.md` (version + numéro de manifest) dans un **commit
`docs(etat)` séparé**, puis pousser.

> Les tags `mods-v*` ne sont pas fiables pour savoir ce qui est livré : lire le manifest live.

## 3. Déployer un plugin sur le serveur dev (Minestrator)

- Serveur **dev** : Purpur 26.2 chez Minestrator. Accès SFTP : le mot de passe est rangé dans le **gestionnaire
  d'identification Windows** (entrée keyring `reborn-sftp-dev`). On le relit au moment de l'envoi et on le passe en
  variable d'environnement à un petit script paramiko ; **il n'est jamais écrit dans un fichier ni dans une doc**.
  Sans accès : panel Minestrator → Fichiers → `plugins/`.
- **Recette** pour chaque jar :
  1. repérer le nom exact dans `plugins/` ;
  2. supprimer l'ancien `.bak`, renommer le jar actuel en `.bak` ;
     *exception : si le jar en place est cassé, garder le `.bak` existant (dernière version qui démarrait) et
     remplacer directement* ;
  3. envoyer le nouveau **sous le même nom** (`ShinobiCore-1.0.0-SNAPSHOT.jar`, **pas** le `original-*`) ;
  4. vérifier **taille locale = taille distante**.
- **Ne pas toucher aux `config.yml` du serveur** sauf demande explicite. Une clé nouvelle prend la valeur par
  défaut du code. S'il faut éditer : sauvegarde d'abord, et **serveur arrêté** (un plugin réécrit son config à
  l'arrêt).
- **Redémarrage : toujours par l'utilisateur**, depuis le panel (pas d'API de redémarrage). Paper ne recharge pas
  un plugin à chaud. Nexo : `/nexo reload all` suffit.
- **Après le redémarrage, lire les logs** (`logs/latest.log`, récupérable par SFTP) : `Enabling ShinobiCore`,
  puis chaque addon, aucune `NoClassDefFoundError`, aucun `Invalid plugin.yml`.
- Les plugins non Shinobi (Guardian, OST) se déploient pareil.

**Radio (ShinobiRadio, 14/10)** — premier déploiement :
- Jar **`ShinobiRadio-1.0.0-SNAPSHOT.jar`** (nouveau, dans `plugins/`), **avec** `ShinobiCore-1.0.0-SNAPSHOT.jar`
  (API ajoutée : `InventoryService#cosmetique|poserCosmetique`, `KoService#isDowned`, catégorie de journal `RADIO`). Build :
  `mvn -o -q clean package -pl ShinobiRadio -am` depuis `minecraft/shinobi`.
- **Plugin serveur Plasmo Voice requis** (`depend` : sans lui ShinobiRadio ne se charge pas) : le jar
  `plasmovoice-spigot-2.1.17` (Modrinth, Paper / Purpur 26.1.2 → 26.3) déjà installé selon l'utilisateur — **vérifier la
  version sur le panel** (2.1.x). Le port UDP de Plasmo Voice doit rester ouvert comme aujourd'hui.
- **Modpack** : passer le client Plasmo Voice à **`plasmovoice-fabric-26.2-2.1.17`** (le mod compile contre 2.1.17 ; le
  manifest signé 3.1.132 livre 2.1.16, compatible en principe mais à aligner).
- **Pack Nexo** : nouveaux fichiers `nexo/pack/assets/reborn/{items,models/item,textures/item}/oreillette.*` et l'entrée
  `oreillette` de `nexo/items/reborn_cosmetiques.yml` → copier dans `plugins/Nexo/`, puis `/nexo reload all`.
- Au premier démarrage : `plugins/ShinobiRadio/config.yml` est créé ; logs attendus : `[radio] Activation et ligne
  « radio » enregistrées dans Plasmo Voice.` puis `ShinobiRadio activé — 0 escouade(s), voix radio prête.`
- Vérifier aussi le YAML de `ShinobiRadio/src/main/resources/plugin.yml` et `config.yml` (et ceux du jar), §1.4.

**Incidents connus** : SFTP et port de jeu muets en même temps = panne réseau ou hôte, pas les identifiants.
Le serveur **prod** n'est pas sur les mêmes accès que le dev — vérifier `server.properties` avant de toucher à
quoi que ce soit.

## 4. Le prompt de publication (modèle)

Claude écrit le prompt, l'utilisateur le colle dans sa session terminal :

```text
Publication DEV : reborn-hud <version> + <plugins> — <sujet>.
Lis docs/operations/PUBLICATION.md. Ne touche PAS à : <chantiers des autres sessions>.

1. Commit (Conventional Commits, corps en français, pourquoi + référence à la fiche gameplay) :
   <type>(<scope>): <résumé> (hud <version>)
   - <liste exacte des fichiers>
   Vérifie `git status` : rien d'autre d'indexé.
2. Plugins : build Maven, SFTP dev (recette §3), <éventuelles clés de config>. Ne redémarre pas.
3. Mod : .\scripts\publish-mod-manifest.ps1 -Version <version> (contrôle du poids).
4. Push sur origin/main avec MON token, puis docs/ETAT_DES_LIEUX.md dans un commit docs(etat) séparé, puis push.
```

Suivi d'une **liste « À tester en jeu »** concrète (actions précises et résultat attendu), à faire après le
redémarrage.
