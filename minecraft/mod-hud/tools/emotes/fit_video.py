"""Calage automatique d'une pose (et des caméras) sur des images d'une vidéo de référence — dans Blender.

    tools/emotes/emote.sh --fit --spec specs/<emote>.json --views-json specs/<emote>_vues_video.json \
        --masks <dossier des .npy de video_masks.py> --params specs/<emote>_calage.json \
        --out-spec <spec calée.json> --out-views <vues calées.json> [--rounds 12] [--t 0]

Principe : la pose de la spec (au temps --t) est rendue, peau en couleurs pures par classe (cheveux, vêtement clair,
peau, sombre, autre), sous chaque vue qui a un masque ``<vue>.npy`` ; le score est l'IoU de la silhouette + l'IoU de
chaque classe, moyenné sur les vues. Une descente par coordonnées ajuste les paramètres listés dans le fichier de
calage : ``{"params": [{"p": "pose.head.rot.0", "step": 4, "min": -10, "max": 60}, ...]}`` avec des chemins dans la
spec (``pose.<os>.<canal>.<i>``) ou dans les vues (``views.<vue>.yaw|pitch|dist|fov|shift.0|shift.1``), et
``"mirror": [{"of": <chemin>, "p": <chemin lié>, "sign": 1|-1}]`` pour lier deux valeurs (symétrie gauche/droite,
caméras partageant un réglage). Les noms de vues ne doivent pas contenir de point (séparateur des chemins).
"""

import argparse
import json
import os
import sys
import tempfile

import bpy
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True  # pas de __pycache__ dans le dépôt
sys.path.insert(0, HERE)
import emote_lib as L  # noqa: E402

POIDS = {0: 1.0, 1: 0.5, 2: 0.3, 3: 0.3, 4: 0.4}  # 0 = silhouette


def _get(root, path):
    o = root
    for k in path.split("."):
        o = o[int(k)] if isinstance(o, list) else o[k]
    return o


def _set(root, path, val):
    ks = path.split(".")
    o = root
    for k in ks[:-1]:
        o = o[int(k)] if isinstance(o, list) else o[k]
    last = ks[-1]
    if isinstance(o, list):
        o[int(last)] = val
    else:
        o[last] = val


class Caleur:
    def __init__(self, spec, views, masks_dir, t):
        self.spec, self.views, self.t = spec, views, t
        self.masks = {}
        for nom in views:
            f = os.path.join(masks_dir, nom + ".npy")
            if os.path.exists(f):
                self.masks[nom] = np.load(f)
        if not self.masks:
            raise SystemExit("aucun masque .npy trouvé")
        L.ensure_rig_scripts()
        L.make_proxy_skin(fit=True)
        L.setup_render(256)
        sc = bpy.context.scene
        sc.display.shading.light = 'FLAT'
        sc.display.render_aa = 'OFF'
        sc.view_settings.view_transform = 'Standard'
        sc.view_settings.look = 'None'
        self.tmp = os.path.join(tempfile.gettempdir(), "reborn_fit_render.png")
        self.n = 0

    def _classes(self, path):
        img = bpy.data.images.load(path, check_existing=False)
        w, h = img.size
        px = np.empty(w * h * 4, np.float32)
        img.pixels.foreach_get(px)
        bpy.data.images.remove(img)
        px = px.reshape(h, w, 4)[::-1]
        r, g, b, a = (px[..., i] > 0.5 for i in range(4))
        lab = np.zeros((h, w), np.uint8)
        lab[a & r & ~g & ~b] = 1
        lab[a & ~r & g & ~b] = 2
        lab[a & ~r & ~g & b] = 3
        lab[a & r & g & ~b] = 4
        lab[a & r & ~g & b] = 5
        lab[a & (lab == 0)] = 5
        return lab

    def rendre(self, nom):
        sc = bpy.context.scene
        m = self.masks[nom]
        sc.render.resolution_x, sc.render.resolution_y = m.shape[1], m.shape[0]
        L.place_camera(sc.camera, self.views[nom])
        sc.render.filepath = self.tmp
        bpy.ops.render.render(write_still=True)
        self.n += 1
        return self._classes(self.tmp)

    def score(self, detail=False):
        L.pose_static(self.spec, self.t)
        tot, det = 0.0, {}
        for nom, ref in self.masks.items():
            lab = self.rendre(nom)
            s = 0.0
            for k, w in POIDS.items():
                A = (lab > 0) if k == 0 else (lab == k)
                B = (ref > 0) if k == 0 else (ref == k)
                u = np.logical_or(A, B).sum()
                s += w * (np.logical_and(A, B).sum() / u if u else 1.0)
            s /= sum(POIDS.values())
            det[nom] = round(float(s), 4)
            tot += s
        tot /= len(self.masks)
        return (tot, det) if detail else tot


def main():
    argv = sys.argv[sys.argv.index("--") + 1:]
    ap = argparse.ArgumentParser(prog="fit_video")
    ap.add_argument("--fit", action="store_true")
    ap.add_argument("--spec", required=True)
    ap.add_argument("--views-json", required=True)
    ap.add_argument("--masks", required=True)
    ap.add_argument("--params", required=True)
    ap.add_argument("--out-spec", required=True)
    ap.add_argument("--out-views", required=True)
    ap.add_argument("--rounds", type=int, default=12)
    ap.add_argument("--t", type=float, default=0.0)
    a = ap.parse_args(argv)

    spec = L.load_spec(a.spec)
    spec.pop("_path", None)
    with open(a.views_json, encoding="utf-8") as f:
        views = json.load(f)
    with open(a.params, encoding="utf-8") as f:
        cfg = json.load(f)
    root = {"pose": spec["pose"], "views": views, "spec": spec}
    params = [dict(p) for p in cfg["params"]]
    mirrors = cfg.get("mirror", [])

    def appliquer(path, val):
        _set(root, path, val)
        for m in mirrors:
            if m["of"] == path:
                _set(root, m["p"], val * m.get("sign", 1))

    c = Caleur(spec, views, a.masks, a.t)
    best, det = c.score(detail=True)
    print(f"[calage] départ {best:.4f} {det}", flush=True)
    for rnd in range(a.rounds):
        mieux = False
        for p in params:
            for sens in (1, -1):
                v0 = _get(root, p["p"])
                v1 = max(p.get("min", -1e9), min(p.get("max", 1e9), v0 + sens * p["step"]))
                if v1 == v0:
                    continue
                appliquer(p["p"], v1)
                s = c.score()
                if s > best + 1e-5:
                    best, mieux = s, True
                    # on insiste dans la même direction tant que ça paie
                    while True:
                        v2 = max(p.get("min", -1e9), min(p.get("max", 1e9), v1 + sens * p["step"]))
                        if v2 == v1:
                            break
                        appliquer(p["p"], v2)
                        s2 = c.score()
                        if s2 > best + 1e-5:
                            best, v1 = s2, v2
                        else:
                            appliquer(p["p"], v1)
                            break
                    break
                appliquer(p["p"], v0)
        if not mieux:
            for p in params:
                p["step"] /= 2
            if all(p["step"] < p.get("min_step", 0.25) for p in params):
                break
        _, det = c.score(detail=True)
        print(f"[calage] tour {rnd + 1} : {best:.4f} {det} ({c.n} rendus)", flush=True)
    for p in cfg["params"]:
        v = _get(root, p["p"])
        _set(root, p["p"], round(v, 2))
    with open(a.out_spec, "w", encoding="utf-8") as f:
        json.dump(spec, f, ensure_ascii=False, indent=1)
    with open(a.out_views, "w", encoding="utf-8") as f:
        json.dump(views, f, ensure_ascii=False, indent=1)
    print(f"[calage] fini : {best:.4f} -> {a.out_spec}, {a.out_views}")


if __name__ == "__main__":
    main()
    # sortie immédiate : Blender 5.2 en --background plante parfois (EXCEPTION_ACCESS_VIOLATION) en libérant le
    # contexte GPU de Workbench après des rendus ; tout est déjà écrit à ce stade
    sys.stdout.flush()
    os._exit(0)
