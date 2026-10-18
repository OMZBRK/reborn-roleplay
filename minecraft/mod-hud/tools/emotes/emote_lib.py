"""Bibliothèque Blender (bpy) de la chaîne d'émotes Reborn.

Ne s'exécute QUE dans Blender (``blender --background ... --python build_emote.py``) : pilote le rig officiel
d'Emotecraft (``emote_creator.blend``, KosmX/emotes, dossier ``blender/``), y construit une action à partir d'une
description JSON (``specs/*.json``), l'exporte avec l'exporteur embarqué dans le rig (JSON PlayerAnimationLib, celui
que lisent ``MeditationAnimation``, ``MovementAnimations``, ``EmoteAnimations``) et rend des vignettes de contrôle.

Repères (vérifiés sur le rig 2.0, commit 4f7d946e) :

* Blender : Z en haut, le joueur regarde vers **-Y**, sa gauche est **+X**. 1 unité Blender = **4 px** Minecraft
  (le joueur debout mesure 8 unités = 32 px ; pivot du bassin à z = 3 = 12 px).
* « Espace joueur » des specs (``at``) : ``[x, y, z]`` en **pixels**, x = gauche du joueur, y = haut (0 = sol),
  z = devant lui. Converti en ``(x/4, -z/4, y/4)``.
* ``rot`` : euler XYZ **local à l'os**, en degrés, exactement ce qu'affiche le panneau N de Blender.
  ``loc`` : translation locale à l'os, en pixels (÷ 4). ``bend`` : pli BendableCuboids = rotation X de l'os
  ``<os>_bend`` (seul axe permis), en degrés.
"""

import json
import math
import os

import bpy
from mathutils import Matrix, Vector

RIG = "export_armature"
MESH = "player_mesh"
PX = 0.25  # unités Blender par pixel Minecraft
MODEL_SCALE = 0.9375  # le rendu du joueur dans Minecraft est réduit à 15/16 (PlayerRenderer)

#: os exportés (même liste que export.py du rig)
EXPORT_BONES = ["body", "body_control", "head", "left_arm", "right_arm", "left_leg", "right_leg",
                "waist", "torso", "cape", "left_item", "right_item"]
#: os « posables » qu'on clé à chaque clé de la spec (contrôles + IK), pour que chaque axe exporté ait une clé
KEYED_BONES = ["body", "body_control", "waist", "torso", "torso_bend", "head", "cape", "cape_bend",
               "left_arm", "left_arm_bend", "right_arm", "right_arm_bend",
               "left_leg", "left_leg_bend", "right_leg", "right_leg_bend",
               "left_arm_ik_goal", "right_arm_ik_goal", "left_leg_ik_goal", "right_leg_ik_goal",
               "leftArm_ik_pole", "rightArm_ik_pole", "leftLeg_ik_pole", "rightLeg_ik_pole", "head_goal"]
IK_PROPS = {"leftArm": "leftArm IK", "rightArm": "rightArm IK", "leftLeg": "leftLeg IK", "rightLeg": "rightLeg IK"}
EASINGS = {
    "sine": ("SINE", "EASE_IN_OUT"), "linear": ("LINEAR", "AUTO"), "bezier": ("BEZIER", "AUTO"),
    "quad": ("QUAD", "EASE_IN_OUT"), "cubic": ("CUBIC", "EASE_IN_OUT"), "constant": ("CONSTANT", "AUTO"),
}


# --------------------------------------------------------------------------------------------- repères

def player_to_world(p):
    """Pixels espace joueur (x gauche, y haut, z devant) -> unités Blender monde."""
    return Vector((p[0] * PX, -p[2] * PX, p[1] * PX))


def world_to_player(v):
    return [round(v.x / PX, 2), round(v.z / PX, 2), round(-v.y / PX, 2)]


def rig():
    return bpy.data.objects[RIG]


def ensure_rig_scripts():
    """Enregistre le panneau d'action du rig (propriété ``Action.emote`` lue par l'exporteur).

    C'est le script ``action_settings_panel.py`` EMBARQUÉ dans le .blend (identique au dépôt officiel) : on
    l'exécute explicitement, Blender étant lancé avec ``--disable-autoexec``.
    """
    if not hasattr(bpy.types.Action, "emote"):
        bpy.data.texts["action_settings_panel.py"].as_module().register()


def select_rig():
    r = rig()
    for o in bpy.context.view_layer.objects:
        o.select_set(False)
    r.select_set(True)
    bpy.context.view_layer.objects.active = r
    if r.mode != 'POSE':
        bpy.ops.object.mode_set(mode='POSE')
    return r


# --------------------------------------------------------------------------------------------- spec -> action

def load_spec(path):
    with open(path, encoding="utf-8") as f:
        spec = json.load(f)
    spec["_path"] = path
    return spec


def _merge(base, add, mode):
    """Fusionne deux dictionnaires {os: {canal: valeur}} (mode 'set' remplace, 'add' additionne)."""
    out = {b: {c: (list(v) if isinstance(v, list) else v) for c, v in ch.items()} for b, ch in base.items()}
    for b, ch in (add or {}).items():
        dst = out.setdefault(b, {})
        for c, v in ch.items():
            if mode == "add" and c in dst:
                if isinstance(v, list):
                    dst[c] = [a + d for a, d in zip(dst[c], v)]
                else:
                    dst[c] = dst[c] + v
            else:
                dst[c] = list(v) if isinstance(v, list) else v
    return out


def pose_at_keys(spec):
    """Liste triée [(temps_s, {os: {canal: valeur}})] : pose de base + surcharges ('set') + écarts ('add')."""
    base = spec.get("pose", {})
    keys = spec.get("keys") or {"0": {}}
    out = []
    for t in sorted(keys, key=float):
        k = keys[t] or {}
        p = _merge(base, k.get("set"), "set")
        p = _merge(p, k.get("add"), "add")
        out.append((float(t), p))
    return out


def _reset_pose(r):
    for pb in r.pose.bones:
        pb.location = (0, 0, 0)
        pb.rotation_mode = pb.rotation_mode  # garde l'ordre du rig (XYZ, sauf body en XZY)
        pb.rotation_euler = (0, 0, 0)
        pb.scale = (1, 1, 1)


def _apply_pose(r, pose):
    """Pose le rig (canaux locaux) d'après un dictionnaire {os: {canal: valeur}}."""
    _reset_pose(r)
    bpy.context.view_layer.update()
    # 1) canaux locaux directs
    for name, ch in pose.items():
        if name == "settings":
            continue
        pb = r.pose.bones[name]
        if "rot" in ch:
            pb.rotation_euler = [math.radians(a) for a in ch["rot"]]
        if "loc" in ch:
            pb.location = [v * PX for v in ch["loc"]]
        if "scale" in ch:
            pb.scale = ch["scale"]
        if "bend" in ch:
            r.pose.bones[name + "_bend"].rotation_euler = (math.radians(ch["bend"]), 0, 0)
    bpy.context.view_layer.update()
    # 2) cibles en espace joueur (buts d'IK, pôles, regard) : posées après, parents à jour
    for name, ch in pose.items():
        if name == "settings" or "at" not in ch:
            continue
        pb = r.pose.bones[name]
        target = r.matrix_world.inverted() @ player_to_world(ch["at"])
        m = pb.matrix.copy()
        m.translation = target
        pb.matrix = m
        bpy.context.view_layer.update()


def setup_rig(spec):
    """Réglages fixes du rig pour une spec : interrupteurs IK, mode vanilla, regard, hauteur d'assise."""
    r = rig()
    settings = r.pose.bones["settings"]
    for k, prop in IK_PROPS.items():
        settings[prop] = float(spec.get("ik", {}).get(k, 0.0))
    settings["vanilla"] = bool(spec.get("vanilla", False))
    r.pose.bones["head"]["look at marker"] = float(spec.get("look_at", 0.0))
    # objet armature décalé = hauteur d'assise en jeu (siège) : visuel seulement, jamais exporté
    # (le maillage n'est pas enfant de l'armature : il faut le décaler AUSSI, sinon le modificateur Armature
    #  déforme des sommets décalés par rapport aux os et tout part en vrille)
    # + échelle du modèle : Minecraft rend le joueur à 15/16 (0,9375) autour de ses pieds
    r.location = (0, 0, float(spec.get("seat_px", 0.0)) * PX)
    k = float(spec.get("model_scale", MODEL_SCALE))
    r.scale = (k, k, k)
    m = bpy.data.objects[MESH]
    m.location, m.scale = tuple(r.location), (k, k, k)
    bpy.context.view_layer.update()


def pose_static(spec, t=0.0):
    """Pose fixe (sans action) : la pose de la spec au temps t (clé la plus proche). Pour le calage."""
    r = select_rig()
    if r.animation_data is not None:
        r.animation_data.action = None
    setup_rig(spec)
    keys = pose_at_keys(spec)
    pose = min(keys, key=lambda k: abs(k[0] - t))[1]
    _apply_pose(r, pose)


def build_action(spec):
    """Crée (ou remplace) l'action de la spec sur le rig, avec une clé par temps et par canal."""
    ensure_rig_scripts()
    r = select_rig()
    sc = bpy.context.scene
    fps = int(spec.get("fps", 20))
    length = float(spec["length"])
    sc.render.fps, sc.render.fps_base = fps, 1.0
    # ATTENTION : l'exporteur prend loopTick = scene.frame_start ; le rig livré a frame_start = 4.
    sc.frame_start = int(round(float(spec.get("loop_from", 0.0)) * fps))
    sc.frame_end = int(round(length * fps))

    name = spec["name"]
    old = bpy.data.actions.get(name)
    if old is not None:
        bpy.data.actions.remove(old)
    act = bpy.data.actions.new(name)
    act.use_fake_user = True
    if r.animation_data is None:
        r.animation_data_create()
    r.animation_data.action = act
    if r.animation_data.action_slot is None and r.animation_data.action_suitable_slots:
        r.animation_data.action_slot = r.animation_data.action_suitable_slots[0]

    loop = spec.get("loop", False)
    act.use_cyclic = bool(loop)
    act.emote.hold_on_last_frame = (loop == "hold")
    act.emote.name = spec.get("title", name)
    act.emote.author = spec.get("author", "Reborn")
    act.emote.description = spec.get("description", "")
    act.emote.value_precision = int(spec.get("precision", 3))
    act.emote.baking_error_threshold = float(spec.get("baking_error", 0.001))

    setup_rig(spec)

    interp, ease = EASINGS[spec.get("easing", "sine")]
    # Membres pilotés par l'IK : une seule clé (à 0, valeur locale nulle). L'exporteur cuit l'IK puis ne remplace
    # la courbe d'origine que là où la cuisson diffère ; une clé « 0 » laissée à chaque temps de la spec ferait
    # revenir bras/jambes au repos à ces instants (piège rencontré : membres qui claquent toutes les 2 s).
    sous_ik = set()
    for k, on in spec.get("ik", {}).items():
        if on:
            membre = {"leftArm": "left_arm", "rightArm": "right_arm", "leftLeg": "left_leg", "rightLeg": "right_leg"}[k]
            sous_ik |= {membre, membre + "_bend"}
    for n, (t, pose) in enumerate(pose_at_keys(spec)):
        frame = int(round(t * fps))
        sc.frame_set(frame)
        _apply_pose(r, pose)
        for bn in KEYED_BONES:
            pb = r.pose.bones.get(bn)
            if pb is None or (n > 0 and bn in sous_ik):
                continue
            for path in ("location", "rotation_euler", "scale"):
                pb.keyframe_insert(path, frame=frame, group=bn)
    # interpolation / easing de toutes les clés posées
    for fc in _fcurves(r):
        for kp in fc.keyframe_points:
            kp.interpolation = interp
            kp.easing = ease
            if interp == 'BEZIER':  # tangentes plates aux extrêmes, continues ailleurs (boucle sans à-coup)
                kp.handle_left_type = kp.handle_right_type = 'AUTO_CLAMPED'
        fc.update()
    sc.frame_set(0)
    return act


def _fcurves(r):
    from bpy_extras import anim_utils
    cb = anim_utils.action_get_channelbag_for_slot(r.animation_data.action, r.animation_data.action_slot)
    return cb.fcurves if cb else []


# --------------------------------------------------------------------------------------------- export

def export(spec, out_path):
    """Exporte l'action active avec l'exporteur EMBARQUÉ du rig (collect_animation_data + set_up_bedrock),
    comme ``export.py`` mais sans l'icône (base64 inutile pour nos fichiers du mod)."""
    r = select_rig()
    sc = bpy.context.scene
    act = r.animation_data.action
    bones = list(EXPORT_BONES)
    for pv in act.emote.pivot_bones:
        bones.append(pv.name)
    collect = bpy.data.texts["collect_animation_data.py"].as_module().collect_animation_data
    create = bpy.data.texts["set_up_bedrock.py"].as_module().create_emote
    sc.frame_set(0)
    data, work = collect(r, bones)
    emote = create(r, bones, data)
    bpy.data.actions.remove(work)
    anim = emote["animations"][act.name]
    pal = anim["player_animation_library"]
    if not pal.get("bages"):
        pal.pop("bages", None)
    # os sans aucun mouvement retirés si demandé (fichier plus léger ; PAL laisse alors l'os vanilla)
    for b in spec.get("drop_bones", []):
        anim["bones"].pop(b, None)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(emote, f, ensure_ascii=False, indent=1)
    print(f"[emote] exporté : {out_path} ({os.path.getsize(out_path)} o)")
    return emote


# --------------------------------------------------------------------------------------------- rendu

#: couleurs pures de la peau de calage, par classe (mêmes classes que video_masks.py)
FIT_COLORS = {1: (1, 0, 0), 2: (0, 1, 0), 3: (0, 0, 1), 4: (1, 1, 0), 5: (1, 0, 1)}


def make_proxy_skin(path=None, fit=False):
    """Peau de contrôle (64x64) aux couleurs du personnage de la vidéo : cheveux violets, peau brune,
    chemise claire, mains brunes, pantalon violet sombre, chaussures noires à liseré or. Ou une peau fournie."""
    if path:
        img = bpy.data.images.load(path, check_existing=True)
    else:
        nom = "reborn_fit" if fit else "reborn_proxy"
        img = bpy.data.images.get(nom) or bpy.data.images.new(nom, 64, 64, alpha=True)
        W = 64
        px = [0.0] * (W * W * 4)

        def rect(x0, y0, x1, y1, c):  # coordonnées « image » (y vers le bas)
            for y in range(y0, y1):
                for x in range(x0, x1):
                    i = ((W - 1 - y) * W + x) * 4
                    px[i:i + 4] = [c[0], c[1], c[2], 1.0]

        hair, skin, shirt, shirt2 = (0.42, 0.20, 0.55), (0.42, 0.24, 0.14), (0.78, 0.87, 0.87), (0.62, 0.72, 0.73)
        pants, shoe, gold, eye = (0.17, 0.08, 0.18), (0.05, 0.04, 0.06), (0.85, 0.65, 0.15), (0.95, 0.95, 0.95)
        if fit:  # classes en couleurs pures (calage sur vidéo) : cheveux, vêtement clair, peau, sombre, autre
            F = FIT_COLORS
            hair, shirt, shirt2, skin, eye = F[1], F[2], F[2], F[3], F[3]
            pants, shoe, gold = F[4], F[4], F[5]
        rect(0, 0, 32, 16, hair)                    # tête : cheveux partout
        rect(8, 10, 16, 16, skin)                   # visage
        rect(9, 12, 11, 13, eye); rect(13, 12, 15, 13, eye)
        rect(0, 13, 8, 16, skin); rect(16, 13, 24, 16, skin); rect(24, 14, 32, 16, skin)  # joues, nuque
        rect(16, 16, 40, 32, shirt); rect(20, 20, 28, 32, shirt)
        rect(26, 20, 30, 32, shirt2)                # ombre de flanc (lisibilité)
        for (x0, y0) in ((40, 16), (32, 48)):       # bras droit, bras gauche
            rect(x0, y0, x0 + 16, y0 + 16, shirt)
            rect(x0, y0 + 11, x0 + 16, y0 + 16, skin)   # avant-bras/mains bruns
            rect(x0 + 8, y0, x0 + 12, y0 + 4, skin)     # dessous (main)
            rect(x0, y0 + 10, x0 + 16, y0 + 11, gold)   # bracelet
        for (x0, y0) in ((0, 16), (16, 48)):        # jambes
            rect(x0, y0, x0 + 16, y0 + 16, pants)
            rect(x0, y0 + 13, x0 + 16, y0 + 16, shoe)
            rect(x0, y0 + 12, x0 + 16, y0 + 13, gold)
            rect(x0 + 8, y0, x0 + 12, y0 + 4, shoe)
        img.pixels[:] = px
        img.pack()
    mat = bpy.data.objects[MESH].data.materials[0]
    for n in mat.node_tree.nodes:
        if n.type == 'TEX_IMAGE':
            n.image = img
            n.interpolation = 'Closest'
    return img


def setup_render(res=512, engine="workbench"):
    sc = bpy.context.scene
    sc.render.resolution_x = sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'
    if engine == "workbench":
        sc.render.engine = 'BLENDER_WORKBENCH'
        sh = sc.display.shading
        sh.light = 'STUDIO'
        sh.color_type = 'TEXTURE'
        sh.show_cavity = False
        sh.show_shadows = False
        sc.display.render_aa = '8'
    for o in bpy.data.objects:  # seuls le joueur (et les objets ajoutés par nous) au rendu
        if o.type == 'MESH' and o.name != MESH and not o.name.startswith("reborn_"):
            o.hide_render = True
    bpy.data.objects[RIG].hide_render = True
    cam = bpy.data.objects.get("reborn_cam")
    if cam is None:
        cam = bpy.data.objects.new("reborn_cam", bpy.data.cameras.new("reborn_cam"))
        sc.collection.objects.link(cam)
    sc.camera = cam
    return cam


def add_ground(size_px=64, color=(0.30, 0.55, 0.22, 1.0)):
    """Petit sol (plan) pour juger le contact au sol ; l'ombre n'est pas rendue."""
    if bpy.data.objects.get("reborn_ground"):
        return
    me = bpy.data.meshes.new("reborn_ground")
    s = size_px * PX / 2
    me.from_pydata([(-s, -s, 0), (s, -s, 0), (s, s, 0), (-s, s, 0)], [], [(0, 1, 2, 3)])
    ob = bpy.data.objects.new("reborn_ground", me)
    mat = bpy.data.materials.new("reborn_ground")
    mat.diffuse_color = color
    me.materials.append(mat)
    bpy.context.scene.collection.objects.link(ob)


def place_camera(cam, view):
    """view = {yaw, pitch, dist (blocs), lens (mm) | fov (deg, vertical), target [px espace joueur],
    ortho (px) optionnel, res [l, h] optionnel (sinon --res), shift [x, y] décentrement optique (fraction de
    la plus grande dimension de l'image : recadre sans changer la perspective)}.

    yaw 0 = devant le joueur (on voit le visage), 90 = à sa gauche, 180 = dans le dos. pitch > 0 = vu d'en haut.
    """
    yaw, pitch = math.radians(view.get("yaw", 0)), math.radians(view.get("pitch", 15))
    target = player_to_world(view.get("target", [0, 10, 0]))
    d = float(view.get("dist", 3.0)) * 16 * PX  # blocs -> unités (1 bloc = 16 px)
    # devant le joueur = -Y ; sa gauche = +X
    direction = Vector((math.sin(yaw) * math.cos(pitch), -math.cos(yaw) * math.cos(pitch), math.sin(pitch)))
    cam.location = target + direction * d
    cam.rotation_mode = 'QUATERNION'
    cam.rotation_quaternion = (-direction).to_track_quat('-Z', 'Y')
    c = cam.data
    if "ortho" in view:
        c.type = 'ORTHO'
        c.ortho_scale = view["ortho"] * PX
    else:
        c.type = 'PERSP'
        c.sensor_fit = 'VERTICAL'
        if "fov" in view:
            c.angle_y = math.radians(view["fov"])
        else:
            c.lens = view.get("lens", 50)
    c.shift_x, c.shift_y = view.get("shift", [0.0, 0.0])
    c.clip_start, c.clip_end = 0.05, 500


def render_views(spec, out_dir, views, times=None, prefix=None):
    """Rend chaque vue à chaque temps -> ``<out>/<prefix>_<vue>_<t>.png`` (fond transparent)."""
    os.makedirs(out_dir, exist_ok=True)
    sc = bpy.context.scene
    cam = sc.camera
    fps = sc.render.fps
    times = times if times is not None else spec.get("preview_times", [0.0])
    base_res = (sc.render.resolution_x, sc.render.resolution_y)
    prefix = prefix or spec["name"]
    files = []
    for t in times:
        sc.frame_set(int(round(t * fps)))
        for vname, v in views.items():
            place_camera(cam, v)
            w, h = v.get("res", [base_res[0], base_res[1]])
            sc.render.resolution_x, sc.render.resolution_y = int(w), int(h)
            p = os.path.join(out_dir, f"{prefix}_{vname}_{t:05.2f}.png")
            sc.render.filepath = p
            bpy.ops.render.render(write_still=True)
            files.append(p)
    print(f"[emote] {len(files)} vignettes dans {out_dir}")
    return files


def render_sequence(out_dir, view, start_s, end_s, step_frames=1, prefix="seq"):
    """Rend une séquence d'images (pour un GIF/MP4 de contrôle)."""
    os.makedirs(out_dir, exist_ok=True)
    sc = bpy.context.scene
    place_camera(sc.camera, view)
    fps = sc.render.fps
    n = 0
    for f in range(int(start_s * fps), int(end_s * fps) + 1, step_frames):
        sc.frame_set(f)
        sc.render.filepath = os.path.join(out_dir, f"{prefix}_{n:04d}.png")
        bpy.ops.render.render(write_still=True)
        n += 1
    return n


def describe_axes():
    """Table des axes locaux de chaque os de contrôle, en espace joueur (pour le README)."""
    r = rig()
    names = {(1, 0, 0): "gauche", (-1, 0, 0): "droite", (0, -1, 0): "devant", (0, 1, 0): "derrière",
             (0, 0, 1): "haut", (0, 0, -1): "bas"}
    lines = []
    for bn in KEYED_BONES:
        b = r.data.bones.get(bn)
        if b is None:
            continue
        m = b.matrix_local.to_3x3()
        ax = []
        for i in range(3):
            v = m.col[i]
            k = max(range(3), key=lambda j: abs(v[j]))
            key = tuple((1 if v[j] > 0 else -1) if j == k else 0 for j in range(3))
            ax.append(names[key])
        lines.append(f"| `{bn}` | X → {ax[0]} | Y → {ax[1]} | Z → {ax[2]} |")
    return lines


def save_blend(path):
    bpy.ops.wm.save_as_mainfile(filepath=path, copy=True)
    print(f"[emote] .blend sauvé : {path}")
