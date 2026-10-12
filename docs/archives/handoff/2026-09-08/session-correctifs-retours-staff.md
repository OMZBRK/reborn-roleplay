# Passation — Correctifs des retours de test staff (mod-hud + ShinobiAbilities)

**Session** : « correctifs retours staff » · **Date** : 2026-09-08 · **Branche de départ** : `main` @ `8461d8c`

---

## 1. Sujet

Traitement des 5 remontées de la session de test du staff : bascule des bandes cinéma sur F1, correctif du free-look, rotation de tête invisible pour les autres joueurs, finalisation du Naruto Run (vitesse / auto-step / retrait au coup + cooldown), et refonte de l'éditeur d'interface (taille du panneau, saisie chiffrée, repli, bugs de déplacement / redimensionnement).

---

## 2. Ce qui est fait

### 2.1 Bandes cinéma sur F1

- **`keybind/HudKeybinds.java`** — défaut du bind `toggle_cinema` passé de `K` à `F1`, et la `KeyMapping` est exposée en `public static CINEMA` pour que le mixin puisse la tester.
- **`mixin/KeyboardCinemaMixin.java`** *(nouveau)* — injecte au HEAD de `KeyboardHandler#keyPress` et **annule** l'évènement pour F1, puis appelle lui-même `CinemaBars.toggle()`.
  - *Pourquoi ce détour* : le F1 vanilla ne passe pas par une `KeyMapping`, il bascule directement `Hud.isHidden` dans `keyPress`. Sans le cancel, une pression sur F1 déclenchait **les deux** (HUD masqué par Minecraft *et* cycle cinéma Reborn) — la touche devenait inutilisable. Comme le cancel empêche aussi `KeyMapping.click`, c'est le mixin qui déclenche le cycle.
  - Le remplacement est **conditionné à `CINEMA.matches(event)`** : si le joueur rebinde « bandes cinéma » ailleurs, on ne cancelle plus rien et le F1 vanilla revient. Idem si un écran est ouvert (l'éditeur HUD utilise F1 pour son aide).
- **`reborn-hud.mixins.json`** — enregistrement de `KeyboardCinemaMixin`.
- **`immersion/CinemaBars.java`**, **`RebornHudClient.java`** — javadoc/commentaires qui citaient encore « touche K ».

### 2.2 Free-look — le perso bougeait quand même

- **`mixin/LocalPlayerBodyMixin.java`** — pendant le free-look, le perso est maintenant **totalement gelé** : yaw, pitch, `yBodyRot` et `yHeadRot` sont capturés au front montant (champs `@Unique reborn$frozen*`) puis ré-appliqués à chaque tick.
  - *Ce qui cassait* : l'ancien code ne testait `cam.freeLook()` que dans la branche « en déplacement ». À l'arrêt, la tête suivait donc la caméra malgré le free-look ; et en course, la logique vanilla (`aiStep` → `tickHeadTurn`) re-tournait le corps vers la direction de déplacement. D'où la remontée « ma tête tourne quand même / elle s'actualise quand je cours ».
- **`camera/RebornCamera.java`** — `setFreeLook` mémorise l'instant du relâchement, et `alignFactor()` renvoie `0.35` pendant 250 ms puis `1.0`.
  - *Pourquoi* : geler complètement le perso implique un rattrapage au relâchement. Sans lissage il claquait jusqu'à 180° en une frame, et les autres joueurs le voyaient aussi. 0.35/tick converge en ~5 ticks.
- **`mixin/KeyboardInputMixin.java`** — les deux branches qui alignent le perso sur la caméra passent par `Mth.rotLerp(cam.alignFactor(), ...)`. Hors rattrapage `alignFactor() == 1`, donc le comportement d'origine (collage instantané) est strictement conservé.

### 2.3 Caméra — tête invisible pour les autres + rendu saccadé

C'était le point le plus profond des cinq : un **vrai bug réseau**, pas un problème de rendu.

- **`mixin/KeyboardInputMixin.java`** — à l'arrêt, on pose désormais `yRot` (et `xRot`) vers la caméra, **dans le tick du clavier**.
  - *Ce qui cassait* : le client n'envoie qu'un couple `(yRot, xRot)` via `ServerboundMovePlayerPacket` ; le serveur en dérive le yaw de tête ET de corps. Un `setYHeadRot` posé côté client seul est **purement cosmétique et invisible pour les autres**. L'ancien code ne touchait à l'arrêt que `setYHeadRot` + `setXRot` → seul le haut/bas partait sur le réseau, exactement la remontée du staff.
  - *Pourquoi ici et pas dans `LocalPlayerBodyMixin`* : `LocalPlayer#sendPosition` part **après** `aiStep`, donc une écriture au TAIL du tick joueur arrive un tick trop tard. `KeyboardInput#tick` s'exécute avant, dans `aiStep`.
- **`mixin/LocalPlayerBodyMixin.java`** — le rendu local dérive maintenant de `self.getYRot()` (la valeur qui vient d'être envoyée) au lieu de recalculer depuis `camYaw`, et le clamp de nuque passe de 70° à **75°** pour coller au `tickHeadTurn` vanilla que les clients distants appliquent sur notre avatar. Local et distant décrivent donc la même orientation, rattrapage post-free-look compris.
- **`camera/RebornCamera.java`** — `rotateCamera` ramène `camYaw` dans `[-180, 180[` via `Mth.wrapDegrees` ; `initOrientation` fait pareil et clampe le pitch.
  - *Pourquoi* : `camYaw` s'accumulait sans borne (+360 par tour complet). Au bout d'une session un peu longue la valeur devenait assez grande pour que la conversion en `float` (caméra, `setYRot`) **quantifie visiblement la rotation** — c'est très probablement une bonne part du « mouvement de tête pas fluide ». C'est ce que fait `Entity#turn` en vanilla.

### 2.4 Naruto Run

**Cause racine : le canal client ↔ serveur ne correspondait pas.** Le mod émettait sur `reborn:naruto`, le plugin écoutait `reborn:run` → aucun paquet n'atteignait jamais le serveur. Ni vitesse, ni particules, ni interruption : tout le code serveur existant tournait à vide. Second bug par-dessus : `RunChannelListener` lisait l'octet comme un opcode où `1 = toggle`, donc même avec le bon canal l'extinction (octet `0`) aurait été ignorée.

Côté mod :

- **`animation/NarutoRunPayload.java`** — identifiant corrigé en `reborn:run`, payload documenté comme **bidirectionnel**.
- **`animation/NarutoRun.java`** — `toggle()` applique l'état localement (réponse immédiate de la touche, et le mouvement client continue de marcher en solo / sans plugin) puis **envoie l'état souhaité**, pas un toggle. Nouveau `setActive(boolean)` pour appliquer le verdict serveur sans re-notifier (sinon boucle).
- **`RebornHudClient.java`** — enregistrement du type `clientboundPlay` + `registerGlobalReceiver` sur `reborn:run`.

Côté plugin :

- **`mobility/RunChannelListener.java`** — protocole 1 octet **idempotent** : `1` = démarrer, `0` = arrêter, autre = bascule. Un paquet perdu ou ré-émis ne laisse plus client et serveur en désaccord. `CHANNEL_LEGACY = "reborn:naruto"` conservé en alias, exposé via `CHANNELS[]`.
- **`ShinobiAbilities.java`** — enregistre les **deux** canaux entrants + le canal **sortant** `reborn:run`.
- **`mobility/NarutoRun.java`** :
  - `requestStart` / `requestStop` + `syncToClient(Player, boolean)` qui pousse l'état autoritaire sur **tous** les chemins (démarrage, refus, arrêt, interruption, chakra épuisé, KO). C'est ce qui rend « le retrait du mode à chaque coup » visible côté joueur : avant, le serveur coupait la course mais le mod restait en mode Naruto Run (animation + mouvement libre) sans le moindre bonus.
  - **Auto-step** : `applyStepHeight` / `removeStepHeight`, modificateur transient `STEP_HEIGHT` par clé (`naruto_run_step`), config `step-height-bonus: 1.0` → 0.6 vanilla + 1.0 = un bloc plein franchi sans sauter. Sauté pour la Voie du Flux, qui pose déjà le sien (sinon empilement).
  - **Cooldown** : la constante `INTERRUPT_LOCKOUT_MS` devient la config `interrupt-lockout-ms` (défaut 30 000), et l'action bar affiche les secondes restantes.
  - Les refus silencieux (`enabled`, perso absent, slot non débloqué) renvoient maintenant un feedback + l'état `false`.
- **`mobility/MobilityListener.java`**, **`command/MobilityPathCommand.java`** — l'auto-step est retiré dans le balayage d'état résiduel au join et au changement de voie, même hygiène que le modificateur de vitesse.
- **`resources/config.yml`** — `step-height-bonus` et `interrupt-lockout-ms` documentés.

> La vitesse elle-même n'a pas été touchée : elle était déjà fixe à ×1.6 dès l'activation (`speed-multiplier`), elle n'atteignait simplement jamais le serveur.

### 2.5 Éditeur d'interface

L'image de référence fournie par le user (`refimage.png`) a été lue comme une **capture de l'état actuel** montrant le problème — elle correspond au code existant à l'identique (header, liste ÉLÉMENTS, presets DEFAULT/STREAMER/RP/COMPACT, footer) — et **pas** comme une maquette cible. À confirmer, cf. §5.

- **`element/HudElementBounds.java`** — nouvelle méthode `offsetForTopLeft(...)`, inverse exacte de `currentFor`.
  - *Ce qui cassait le déplacement* : le drag calculait l'offset en soustrayant `vanilla.x()` de la position visée. Or l'offset est mesuré **depuis l'ancre**, pas depuis le coin haut-gauche : il restait un résidu de `(largeurVanilla − largeurScalée) × anchor.fx`. À l'échelle 1 le résidu est nul (d'où « ça marchait »), mais **dès qu'un élément est mis à l'échelle la box glisse sous le curseur**. Vitals (×0.80), Cooldowns (×0.60) et Endurance (×0.45) le sont par défaut → visible au premier drag.
- **`ui/HudEditScreen.java`** :
  - drag, redimensionnement et champ de taille passent tous par `offsetForTopLeft` ;
  - **hit-test de la poignée corrigé** : il portait sur l'élément *survolé* (`elementUnderMouse`) alors que la poignée n'est dessinée que sur la *sélection*. Dès qu'une autre box recouvrait le coin (chat / hotbar / cooldowns se chevauchent en bas d'écran), le clic partait en déplacement au lieu du redimensionnement. `resizeHandleRect` est maintenant l'unique définition partagée rendu ↔ hit-test ;
  - **plus de saut d'échelle** au premier pixel de drag (`resizeGrabDX/DY` mémorise l'écart curseur ↔ coin) et le **coin haut-gauche reste épinglé** pendant tout le resize, quel que soit l'anchor ;
  - **inspecteur** : trois `EditBox` (X, Y, taille en %) appliquées à chaque frappe valide, filtre de saisie maison (`isNumericDraft`), historique d'undo débouncé à 800 ms, bouton « Réinit. » par élément ;
  - molette au-dessus de la liste = défilement ; `Entrée`/`Échap` sortent d'un champ numérique sans que les flèches nudgent l'élément.
- **`ui/HudEditSidePanel.java`** :
  - largeur **178 → 148** (à GUI Size 3–4 l'ancienne carte mangeait plus d'un tiers de la largeur utile et cachait tout le bord droit) ;
  - **repli** : chevron dans le header, languette de 14 px pour rouvrir, `leftEdge()` rend la largeur au canvas ;
  - **liste d'éléments scrollable** par lignes entières (donc aucun clipping / scissor nécessaire) + barre de défilement fine ;
  - bloc **inspecteur** (cadres + libellés ; le texte est rendu par les `EditBox` de l'écran) ;
  - la géométrie est **toujours** recalculée, même repliée, pour qu'un redimensionnement de fenêtre pendant le repli ne laisse pas les `EditBox` à des positions périmées ;
  - garde-fou de layout : à très petite hauteur utile la liste est rognée jusqu'à ce que le bloc presets tienne au-dessus du footer (avant, presets et boutons se chevauchaient et devenaient incliquables).
- **`src/test/.../HudElementBoundsTest.java`** — 2 tests ajoutés : round-trip `offsetForTopLeft` → `currentFor` sur **tous** les éléments × 5 échelles, et épinglage du coin lors d'un changement d'échelle.

---

## 3. État de compilation / test

**Rien n'a été compilé, rien n'a été testé en jeu.** À prendre au pied de la lettre.

- **Ce poste n'a ni JDK 25 ni Maven.** `D:\dev-cache\jdk25\...` (le chemin annoncé dans `CLAUDE.md` § Prerequisites) n'existe pas sur cette machine, il n'y a pas de `mvn` sur le PATH, et les JDK installés plafonnent à Corretto 23. Le cache Loom du projet est resté en 1.21.1 / Yarn : le mod 26.2 n'a jamais été construit ici. `./gradlew build` et `mvn package` échouent donc avant de commencer.
- **Ce qui a été vérifié** : passe `javac` 21 sur les 21 fichiers touchés, en filtrant `cannot find symbol` / `package does not exist` / `method does not override` (artefacts inévitables sans les dépendances MC/Paper). Résultat : **zéro erreur de syntaxe**, structure de classes valide partout. Ça ne dit **rien** de la résolution des symboles.
- **Rappel `docs/MIGRATION_26.2.md`** : les mixins échouent **au lancement, pas au build**. `KeyboardCinemaMixin` cible `KeyboardHandler#keyPress(long, int, KeyEvent)` — même signature que `KeyboardInteractionMixin`, qui fonctionne déjà en 26.2, donc le risque est faible mais non nul.
- **Un seul appel d'API non recoupé avec un usage existant du repo** : `EditBox.setFilter` dans `HudEditScreen.numericField`. Si le build râle dessus, la validation est déjà dans `isNumericDraft` — il suffit de supprimer la ligne `box.setFilter(...)`, rien d'autre n'en dépend. Toutes les autres API utilisées (`EditBox.setVisible`, `setResponder`, `KeyMapping.matches(KeyEvent)`, `Attribute.STEP_HEIGHT` + `Operation.ADD_NUMBER`, `mc.gui.screen()`) ont été recoupées avec un usage déjà en place ailleurs dans le repo.

---

## 4. Ce qui n'est PAS fini

- **Aucun WIP, aucune modif à moitié écrite** : les 5 sujets sont traités de bout en bout. Le seul manque est la validation.
- **Ce qui casse si on lance tel quel** : rien de connu, mais rien n'est prouvé. Le premier `./gradlew build` peut révéler des erreurs de symboles (cf. §3), et le premier `runClient` peut révéler un échec de mixin.
- **Non fait, volontairement** : pas de bump de version (`gradle.properties` est toujours à `0.4.133` alors que `0.4.134` est en prod), pas de build, pas de publication, pas de commit.
- **Déploiement — les deux artefacts vont ensemble**, mais la dégradation est propre et volontaire :
  - plugin seul → fonctionne avec les mods déjà publiés grâce à l'alias `reborn:naruto` (l'ancien client envoyait `1` au démarrage et `0` à l'arrêt, ce qui correspond au nouveau protocole) ; simplement pas de correction S2C ;
  - mod seul → `canSend` est faux, retour au mode client-only, aucun crash.
  - Le plugin passe par un upload manuel sur le panel Minestrator + redémarrage serveur.

---

## 5. Décisions ouvertes

1. **`refimage.png` : capture du problème ou maquette cible ?** J'ai tranché pour « capture de l'état actuel » (elle est identique au code existant) et j'ai donc corrigé/réduit l'existant plutôt que de reconstruire vers une autre direction visuelle. Si le user visait une refonte graphique, il faut le redire.
2. **Durée du cooldown Naruto Run** : la valeur historique de 30 s est conservée, désormais dans `config.yml`. C'est long pour un « cd » ; à arbitrer en test.
3. **Valeur de l'auto-step** : `step-height-bonus: 1.0` franchit un bloc plein. À doser en jeu (0.5 = marches et slabs seulement).
4. **`speed-multiplier: 1.6`** n'a jamais été ressenti en jeu puisque le canal était cassé — la valeur est probablement à réétalonner une fois que ça arrive vraiment au serveur.
5. **Largeur du panneau à 148 px** : choisie sans pouvoir la voir. À confirmer à GUI Size 3–4, c'est là que la plainte est née.

---

## 6. Fichiers touchés

### Modifiés (18)

```
minecraft/mod-hud/src/main/java/fr/reborn/hud/RebornHudClient.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/animation/NarutoRun.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/animation/NarutoRunPayload.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/camera/RebornCamera.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/element/HudElementBounds.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/immersion/CinemaBars.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/keybind/HudKeybinds.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/mixin/KeyboardInputMixin.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/mixin/LocalPlayerBodyMixin.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/ui/HudEditScreen.java
minecraft/mod-hud/src/main/java/fr/reborn/hud/ui/HudEditSidePanel.java
minecraft/mod-hud/src/main/resources/reborn-hud.mixins.json
minecraft/mod-hud/src/test/java/fr/reborn/hud/element/HudElementBoundsTest.java
minecraft/shinobi/ShinobiAbilities/src/main/java/com/reborn/shinobiabilities/ShinobiAbilities.java
minecraft/shinobi/ShinobiAbilities/src/main/java/com/reborn/shinobiabilities/command/MobilityPathCommand.java
minecraft/shinobi/ShinobiAbilities/src/main/java/com/reborn/shinobiabilities/mobility/MobilityListener.java
minecraft/shinobi/ShinobiAbilities/src/main/java/com/reborn/shinobiabilities/mobility/NarutoRun.java
minecraft/shinobi/ShinobiAbilities/src/main/java/com/reborn/shinobiabilities/mobility/RunChannelListener.java
minecraft/shinobi/ShinobiAbilities/src/main/resources/config.yml
```

### Créés (2)

```
minecraft/mod-hud/src/main/java/fr/reborn/hud/mixin/KeyboardCinemaMixin.java
docs/handoff/2026-09-08/session-correctifs-retours-staff.md
```

> `docs/AUDIT_TECHNIQUES_ET_CREATOR.md` (non suivi, présent dans le working tree) **n'est pas à moi** — ne pas l'inclure dans mon commit.

---

## 7. Checklist de test pour celui qui reprend

```pwsh
$env:JAVA_HOME = "<jdk25>"
cd minecraft\mod-hud ; .\gradlew build -x test --no-daemon
cd minecraft\shinobi ; mvn clean package
```

1. **F1** — en jeu : F1 → HUD masqué proprement (sans bandes) ; F1 → bandes noires ; F1 → normal. Le HUD ne doit **pas** clignoter ni rester masqué. Puis rebinder « Bandes noires cinéma » sur K dans Commandes → F1 doit redevenir le masquage vanilla.
2. **Free-look** — à l'arrêt, maintenir ALT et faire des cercles à la souris : le perso ne doit **strictement pas** bouger (ni tête, ni pitch). Refaire en courant. Relâcher après un demi-tour → réalignement doux, pas un claquement.
3. **Caméra à 2 joueurs** *(le test le plus important)* — A immobile tourne la caméra gauche/droite ; **B doit voir la tête de A tourner**, le corps suivre en retard au-delà de 75°, sans à-coups. Refaire après 5–10 min de jeu : le bug de précision `camYaw` n'apparaissait qu'après accumulation.
4. **Naruto Run** — touche L : action bar « Course Shinobi activée » (si rien : le canal n'est pas ouvert, vérifier les logs du plugin). Vitesse nette. Courir sur marches / blocs isolés → franchis sans sauter. Se faire taper → la course coupe **et le mod sort du mode**. Re-appuyer sur L dans les 30 s → « reprends ton souffle (N s) », rien ne s'active.
5. **Éditeur (H)** — sélectionner **Vitals** ou **Cooldowns** (les éléments scalés par défaut) et draguer : la box doit rester collée au curseur. Poignée bas-droite : pas de saut au premier pixel, le coin haut-gauche ne bouge pas. Taper `150` dans le champ % → l'élément grandit sur place. Chevron du header → panneau replié, canvas plein écran, languette pour rouvrir. Molette sur la liste → défilement. Tester à GUI Size 4 (petite hauteur utile) que presets et footer ne se chevauchent pas.
