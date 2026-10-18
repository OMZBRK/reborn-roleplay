# Émotes Reborn — chaîne Blender (rig officiel d'Emotecraft)

Créer une animation du joueur (émote, pose, démarche) **dans Blender**, l'exporter au format que le mod lit, la vérifier
par des vignettes, la caler au besoin sur une vidéo, puis la contrôler **en jeu**. Première émote faite ainsi : la
méditation v4 (18/10). Tout se pilote en ligne de commande (Blender en `--background`) ; on peut aussi ouvrir le
`.blend` et retoucher à la main.

## 1. Installation (une fois par poste — rien de tout ça n'est dans le dépôt)

| Outil | Version | Source officielle | Emplacement sur le poste principal |
|---|---|---|---|
| Blender portable | **5.2.2 LTS** (zip Windows x64) | `https://download.blender.org/release/Blender5.2/blender-5.2.2-windows-x64.zip`, somme SHA-256 publiée à côté (`blender-5.2.2.sha256`) : `3849d17a682cba006075aaa3f3597ecb5c9c30ec31035b2e092c53e40679b535` | `C:\Users\omarb\dev-cache\blender-5.2.2\blender-5.2.2-windows-x64\blender.exe` |
| Rig Emotecraft `emote_creator.blend` | **rig 2.0**, commit `4f7d946e` de `KosmX/emotes` (branche `dev`, dossier `blender/`) — fait pour Blender 5.2+ et PlayerAnimationLib 1.2.5+ (le mod embarque 1.2.6) | `https://github.com/KosmX/emotes/tree/dev/blender` (lien « Download the tools » de la page Modrinth d'Emotecraft) ; doc : `https://docs.zigythebird.com/emotecraft/creatingemotes/blender` | `C:\Users\omarb\dev-cache\emotecraft-rig-4f7d946e\` (`emote_creator.blend`, `export.py`, `export_functions/`, `LICENSE`) |

- **Licence du rig : GPL-3.0** (licence du dépôt KosmX/emotes). On ne le copie pas dans notre dépôt : il se télécharge
  à la source (fichiers bruts `https://raw.githubusercontent.com/KosmX/emotes/4f7d946e…/blender/<fichier>`). Les
  émotes **exportées** (nos `.json`) sont nos données, pas du code du rig.
- Le `.blend` embarque ses scripts (`action_settings_panel.py`, `collect_animation_data.py`, `set_up_bedrock.py`,
  `export.py`) : ils ont été relus et sont identiques aux fichiers du dépôt officiel. Blender est **toujours** lancé avec
  `--disable-autoexec` ; nos scripts exécutent explicitement ces modules embarqués (aucun réseau, écriture seulement
  du `.json` demandé).
- Autre poste : poser Blender et le rig où l'on veut et exporter `REBORN_BLENDER` (chemin de `blender.exe`) et
  `REBORN_EMOTE_RIG` (chemin de `emote_creator.blend`). Python système + Pillow + numpy pour les planches.

## 2. Ce que le mod sait lire

Le format est le **JSON « bedrock » de PlayerAnimationLib** (celui que produit l'exporteur du rig) :
`{"format_version": "1.8.0", "animations": {"<nom>": {"model": {pivots}, "parents": {…}, "animation_length": s,
"loop": true|false|"hold_on_last_frame", "player_animation_library": {name, author, description}, "bones": {…}}}}`.

| Lecteur | Fichiers | Comment |
|---|---|---|
| `meditation/MeditationAnimation` | `assets/reborn-hud/animations/meditation.json`, `meditation_fin.json` | première entrée de `animations`, `PlayerAnimLib.GSON` → `Animation` |
| `animation/MovementAnimations`, `combat/…` | `animations/*.json` (démarches, garde, parade…) | `loadGeckolib` (même parseur) ; les `.emotecraft` passent par Emotecraft |
| `emote/EmoteAnimations` | émotes serveur (`plugins/ShinobiCore/emotes/`) | `UniversalEmoteSerializer` (choisit le lecteur à l'extension : `.json` / `.emotecraft`), sinon repli sur le parseur PAL ci-dessus : le `.json` du rig se dépose tel quel |

Os exportés : `body` (tout le modèle), `body_control` (pivot du bassin, 12 px), `waist` (buste, pivot 12 px), `torso`,
`head`, `left_arm`, `right_arm`, `left_leg`, `right_leg`, `cape` ; canaux `position` (px), `rotation` (degrés),
`scale`, et `bend` (pli BendableCuboids) pour bras, jambes, buste et cape.

## 3. Créer une émote par script

1. Écrire une **spec** `specs/<nom>.json` (voir `specs/saluer.json`, la plus simple, et `specs/meditation.json`) :

```json
{
 "name": "saluer", "title": "Saluer", "author": "Reborn", "description": "…",
 "fps": 20, "length": 2.0, "loop": false, "easing": "sine",
 "seat_px": 0,
 "ik": {"leftLeg": 1, "rightLeg": 1},
 "pose": { "<os>": {"rot": [x, y, z], "loc": [x, y, z], "bend": deg, "at": [x, y, z]} },
 "keys": { "0": {}, "0.5": {"set": {…}}, "1.0": {"add": {…}} }
}
```

   - `pose` = pose de base ; chaque clé de `keys` (temps en secondes) la reprend, avec `set` (remplace) et `add`
     (s'ajoute : respiration, balancement). **Chaque clé pose une clé sur tous les canaux de tous les os** (règle de
     l'exporteur : chaque axe exporté doit avoir au moins une clé).
   - `rot` : euler XYZ **local à l'os** en degrés (ce qu'affiche le panneau N de Blender) ; `loc` : translation locale
     en **pixels** ; `bend` : pli de l'os (rotation X de `<os>_bend`, seul axe permis) ; `at` : position **absolue en
     pixels dans l'espace du joueur** (x = sa gauche, y = haut depuis le sol, z = devant lui) — pour les buts d'IK
     (`left_leg_ik_goal`, `right_arm_ik_goal`…), leurs pôles (`leftLeg_ik_pole`… : vers où pointe le genou / le coude)
     et `head_goal`.
   - `ik` : interrupteurs du rig (`leftArm`, `rightArm`, `leftLeg`, `rightLeg`, 0 ou 1). Jambes en IK = pieds plantés
     quand le bassin bouge. L'IK est **cuite** à l'export (rotations + plis), le jeu ne fait pas d'IK.
   - `easing` : `sine` (exporté en `easeinoutsine`), `bezier` (tangentes « auto clamped » : plates aux extrêmes,
     continues ailleurs — le bon choix pour une boucle à plusieurs rythmes), `linear`, `quad`, `cubic`, `constant`.
   - `loop` : `true` (boucle, retour au temps `loop_from`, 0 par défaut), `false`, `"hold"` (tenue sur la dernière image).
   - `seat_px` : décalage vertical **de rendu seulement** (voir § 7, hauteur assise) ; jamais exporté.
   - `model_scale` : échelle de rendu (0,9375 par défaut, comme en jeu) ; jamais exportée.
   - `drop_bones` : os retirés du fichier (l'os reste alors vanilla).

2. Exporter et rendre les vignettes :

```bash
tools/emotes/emote.sh --spec tools/emotes/specs/saluer.json \
  --out tools/emotes/exports/saluer.json \
  --renders /tmp/saluer --times 0,0.5,1 --ground
```

   (PowerShell : `.\tools\emotes\emote.ps1` mêmes arguments.) Options : `--views face,dos` (sous-ensemble),
   `--views-json <fichier>` (vos caméras), `--res 512`, `--skin peau.png` (peau 64×64, sinon peau de contrôle aux
   couleurs du personnage de la vidéo), `--sequence face:0:2:2` (images d'une séquence pour un GIF),
   `--blend copie.blend` (le `.blend` avec l'action générée, pour retoucher à la main), `--axes` (table des axes).

3. Planche : `python tools/emotes/planche.py grille planche.png /tmp/saluer/*.png --cols 4`.

Vues par défaut (`build_emote.py`) : `face`, `dessus` (¾ avant d'en haut), `dos` (¾ arrière), `profil`. Une vue :
`{"yaw": 0 = devant le joueur, 90 = à sa gauche, 180 = dans son dos ; "pitch": > 0 vu d'en haut ; "dist": blocs ;
"fov": degrés (vertical) ; "target": [px espace joueur] ; "shift": [x, y] décentrement ; "res": [l, h]}`.

## 4. Créer ou retoucher dans Blender (interactif)

1. Ouvrir `emote_creator.blend` (ou la copie `--blend` d'une spec) dans Blender 5.2.
2. Éditeur de texte : exécuter `action_settings_panel.py` (ajoute les champs d'émote au panneau Action de l'éditeur
   d'actions ; on peut cocher *Register* pour qu'il tourne à l'ouverture).
3. Sélectionner l'armature `export_armature`, mode Pose. Une **action par émote** (bouton « nouvelle action », faux
   utilisateur). Os de contrôle : voir le tableau des axes ci-dessous ; `settings` porte les interrupteurs IK et le
   mode vanilla ; `head` a « look at marker » (regarde `head_goal`).
4. Panneau Action : nom, description, auteur, cyclique (+ « Hold on last frame »), plage manuelle, dossier de
   sortie, puis **Export**. Mettre `Scene > Frame Start` à 0 avant d'exporter une boucle (voir pièges).
5. Recopier les valeurs utiles dans la spec si l'on veut garder la chaîne scriptée comme source.

Axes locaux des os (rig 2.0, en espace joueur) — `rot` X positif = vers l'avant pour `waist`, `torso_bend`, `head`
(pencher, baisser la tête) ; X **négatif** = lever vers l'avant pour bras et jambes ; `bend` positif plie le genou
« normalement » (tibia vers l'arrière) :

| Os | X | Y | Z |
|---|---|---|---|
| `body`, `body_control` | gauche | derrière | haut |
| `waist`, `torso_bend`, `head`, `head_goal` | gauche | haut | devant |
| `torso`, `cape`, bras, jambes, `*_bend`, buts et pôles d'IK | gauche | bas | derrière |

(`--axes` réimprime ce tableau depuis le rig.)

## 5. Caler une émote sur une vidéo

1. Extraire des images : `ffmpeg -ss 2.0 -i video.mp4 -frames:v 1 refs/f_2.0.png` (une par angle utile).
2. Fichier de vues (`specs/meditation_vues_video.json`) : pour chaque vue, la caméra de départ, `res` = taille de
   l'image, `ref` (nom de l'image) et `roi` [x0, y0, x1, y1] (rectangle autour du personnage).
3. Masques par partie du corps : `python tools/emotes/video_masks.py vues.json refs/ masques/` (cheveux, vêtement
   clair, peau, sombre ; contrôle à l'œil dans `masques/*_masque.png` ; seuils dans `video_masks.py`).
4. Calage : `tools/emotes/emote.sh --fit --spec specs/<nom>.json --views-json vues.json --masks masques/ --params
   specs/<nom>_calage.json --out-spec spec_calee.json --out-views vues_calees.json [--rounds 12]` — descente par
   coordonnées sur les paramètres listés (caméras et/ou pose), score = IoU silhouette + IoU par partie, moyenné sur les
   vues (~18 rendus/s ; 8 vues × 10 tours ≈ 10 min). Procéder en deux temps : **caméras seules** (pose figée) puis
   **caméras + pose**. Bornes serrées et graines de caméra justes, sinon il tombe dans un minimum local (vue de profil
   prise pour une face…) ou invente une pose absurde (pied sous le sol). Noms de vues sans point.
   Chemins : `pose.<os>.<canal>.<i>`, `views.<vue>.yaw|pitch|dist|fov|shift.0|shift.1|target.1` ;
   `"mirror"` lie deux valeurs (symétrie, réglage commun).
5. Contrôle : `tools/emotes/emote.sh --spec spec_calee.json --views-json vues_calees.json --renders r/ --times 0`
   puis `python tools/emotes/planche.py calques calques.png vues_calees.json r/ refs/` (image vidéo | superposition
   demi-teinte + contour rouge | rendu seul).
6. Méditation (18/10) : 8 images de `Downloads/20261017-2146-21.9116595.mp4` (t = 0 ; 0,5 ; 2 ; 2,5 ; 3 ; 5,5 ; 6 ;
   8,5 s, fichiers `f_<t>.png` hors dépôt), vues et cadrages retrouvés dans `specs/meditation_vues_video.json`,
   paramètres dans `specs/meditation_calage.json`. Résultat utile pour la suite : **la vidéo est une capture en
   3ᵉ personne vanilla** (caméra à 4 blocs, FOV 70, image 1080p recadrée en 662×602) — on refait donc ses angles au
   banc à l'identique (caméra FRONT : lacet = −θ, tangage = −h ; BACK : lacet = 180 − θ, tangage = h, θ = angle autour
   du joueur depuis sa face, h = hauteur).

## 6. Brancher dans le mod et vérifier en jeu

- Déposer le `.json` dans `src/main/resources/assets/reborn-hud/animations/` et le charger comme les autres
  (`MeditationAnimation`, `MovementAnimations`…), ou en émote serveur.
- **Banc de la méditation** : copier le `.json` en `run/med_essai_0.json` (`_1`, `_2`… pour comparer des variantes) et
  lancer le client de dev avec `REBORN_MEDITATION_DEBUG=pose` (+ `REBORN_MEDITATION_FILM=1` pour un film de 8 s de
  l'essai 0, une capture par tick → `run/screenshots/medposefilm_*.png` → `ffmpeg -framerate 20`). Captures
  `medpose_<k>_{face,dessus,dos,cote}.png`, joueur assis sur le même support invisible que `MeditationManager`, un
  mannequin assis joue la même pose (« autre joueur »).
- **BendableCuboids** (pack du launcher) doit être présent pour voir les plis des genoux et des coudes : en dev,
  copier le temps de l'essai `BendableCuboidsMerged-*.jar` (et le pack Sodium) depuis `%APPDATA%\RebornRoleplay\mods`
  dans `run/mods`, puis les retirer.

## 7. Pièges (tous rencontrés)

- **Unités et repères** : 1 unité Blender = 4 px ; Z haut ; le joueur regarde **-Y**, sa gauche est **+X**. Pivot du
  bassin (`body_control`, `waist`) à 12 px ; épaules à 22 px ; coude et genou à mi-membre (bras : 4 px de l'épaule au
  coude, 6 px d'avant-bras ; jambe : 6 + 6).
- **Boucle** : l'exporteur écrit `loopTick = scene.frame_start` et le rig livré a `frame_start = 4` → boucle qui
  repart à 0,2 s. `build_action` le met à `loop_from` (0). Première et dernière clé identiques ; avec `bezier` les
  extrêmes ont des tangentes plates : mettre la boucle sur un extrême de chaque rythme (respiration expirée,
  balancement au bout) et des rythmes multiples l'un de l'autre (4 s et 8 s).
- **Fondus** : pas dans le fichier — le code les fait (`replaceAnimationWithFade`, 8 ticks d'entrée, 6 en sortie vers
  `meditation_fin.json` qui ramène tout à zéro en 0,3 s).
- **Hauteur assise** : en jeu le méditant est **passager** d'un support invisible posé au sol ; Minecraft l'abaisse de
  l'attache de véhicule du joueur (0,6 bloc = 9,6 px). Dans Blender, `seat_px: -9.6` décale l'armature **et le
  maillage** (le maillage n'est pas enfant de l'armature : décaler l'une sans l'autre fait partir la déformation en
  vrille) pour juger le contact au sol ; l'export ne contient que le `body_control` relatif.
- **Plis (BendableCuboids)** : seul l'axe X d'un os `_bend` est permis, pas de translation ; dans Blender le pli est
  une déformation lisse (pas d'interstice), en jeu ce sont deux blocs articulés : un pli > 120° se juge **en jeu**.
  Sans BendableCuboids, PlayerAnimationLib ne plie rien (membres raides).
- **IK** : pôles = direction du genou / du coude ; but hors de portée (membre 12 px) = membre tendu. L'IK est cuite à
  l'export (fichier plus gros, courbes Bézier ajustées). **Un membre sous IK ne porte qu'une clé dans l'action** :
  l'exporteur ne remplace la courbe d'origine que là où la cuisson diffère ; une clé « 0 » laissée à chaque temps de la
  spec faisait claquer bras et jambes au repos toutes les 2 s en jeu (corrigé dans `build_action`).
- **Pli du buste** (`torso` bend) : n'emporte ni la tête ni les bras, ni dans le rig ni en jeu (`applyBendToOtherBones`
  est coupé dans l'exporteur) — un grand pli les laisse décrochés ; pencher plutôt `waist`.
- **Échelle** : Minecraft rend le joueur à 15/16 (0,9375) ; `model_scale` (défaut 0,9375) l'applique au rendu
  Blender pour que vignettes et calage tombent juste. Vérifié : rendu Blender et capture du jeu se superposent à la
  même caméra (œil du méditant à 16,3 px du sol, 4 blocs, FOV 70).
- **Ordre de rotation** : XYZ seulement (sauf `body`, XZY dans le rig) ; pas de pilotes (drivers) à l'export.
- **Peau de contrôle** : générée (cheveux violets, chemise claire, mains brunes, pantalon sombre) ; `--skin` pour une
  vraie peau 64×64.
- **Python** : jamais lancer Python dans le dossier du rig téléchargé ; nos scripts restent dans `tools/emotes/`.
- **Blender 5.2 en `--background`** plante parfois (`EXCEPTION_ACCESS_VIOLATION`) en se fermant après des rendus
  Workbench : nos scripts sortent par `os._exit(0)` une fois tout écrit. Un plantage avant « exporté » / « vignettes » :
  relancer.
- **Banc et corps** : en 3ᵉ personne, au-delà de ~50° entre regard et corps, Minecraft tourne le corps avec la tête ;
  le banc tient le corps au sud pour les 4 angles, et le film reste à ±40° de dos puis de face.

## 8. Fichiers

| Fichier | Rôle |
|---|---|
| `emote.sh`, `emote.ps1` | lanceurs (Blender + rig, `--fit` pour le calage) |
| `build_emote.py` | point d'entrée : spec → action → export JSON, vignettes, séquence, copie `.blend` |
| `emote_lib.py` | bibliothèque bpy (repères, pose depuis la spec, IK, export par l'exporteur du rig, caméras, rendu, peau) |
| `fit_video.py` | calage pose + caméras sur des images de vidéo |
| `video_masks.py`, `planche.py` | masques par partie du corps (Python système) ; planches `grille` / `calque` / `calques` |
| `specs/meditation.json`, `meditation_fin.json` | la méditation v4 et sa sortie (→ `assets/reborn-hud/animations/`) |
| `specs/meditation_vues_video.json`, `meditation_calage.json` | vues retrouvées de la vidéo, paramètres de calage |
| `specs/saluer.json` → `exports/saluer.json` | démonstration (salut de la main), **non branchée** dans le jeu |
