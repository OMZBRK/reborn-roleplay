"""Convertit un .bbmodel bbforge (cubes sans rotation) en modele d'objet Java 26.x pour un resource pack.

    python java_item.py <modele> <dossier assets> <namespace> [--id <id objet>]

Ecrit <assets>/<ns>/items/<id>.json (definition d'objet), <assets>/<ns>/models/item/<id>.json (elements, UV
ramenees a l'echelle 16) et <assets>/<ns>/textures/item/<id>.png (l'atlas). Les cubes sont recentres sur le bloc
(x et z + 8). Les transformations d'affichage (inventaire, main, sol, presentoir) sont reglees pour un objet couche.
"""
import json
import os
import shutil
import sys

DISPLAY = {
    "gui": {"rotation": [25, -40, 0], "translation": [0, 3.5, 0], "scale": [1.35, 1.35, 1.35]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, 45, 25], "translation": [2, 6, 0], "scale": [0.35, 0.35, 0.35]},
    "firstperson_lefthand": {"rotation": [0, 45, 25], "translation": [2, 6, 0], "scale": [0.35, 0.35, 0.35]},
}


def convert(model, assets, ns, item_id):
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dist", model)
    bb = json.load(open(os.path.join(src, model + ".bbmodel"), encoding="utf-8"))
    w, h = bb["resolution"]["width"], bb["resolution"]["height"]
    elements = []
    for e in bb["elements"]:
        f, t = e["from"], e["to"]
        el = {
            "name": e.get("name", ""),
            "from": [round(f[0] + 8, 4), round(f[1], 4), round(f[2] + 8, 4)],
            "to": [round(t[0] + 8, 4), round(t[1], 4), round(t[2] + 8, 4)],
            "faces": {},
        }
        for face, fd in e["faces"].items():
            u = fd.get("uv")
            if not u:
                continue
            el["faces"][face] = {"uv": [round(u[0] * 16 / w, 4), round(u[1] * 16 / h, 4),
                                        round(u[2] * 16 / w, 4), round(u[3] * 16 / h, 4)], "texture": "#0"}
        elements.append(el)
    tex = ns + ":item/" + item_id
    out_model = {"texture_size": [w, h], "textures": {"0": tex, "particle": tex},
                 "elements": elements, "display": DISPLAY}
    for sub in ("items", "models/item", "textures/item"):
        os.makedirs(os.path.join(assets, ns, sub), exist_ok=True)
    json.dump({"model": {"type": "minecraft:model", "model": ns + ":item/" + item_id}},
              open(os.path.join(assets, ns, "items", item_id + ".json"), "w", encoding="utf-8"), indent=2)
    json.dump(out_model, open(os.path.join(assets, ns, "models/item", item_id + ".json"), "w", encoding="utf-8"),
              indent=1)
    shutil.copyfile(os.path.join(src, model + ".png"), os.path.join(assets, ns, "textures/item", item_id + ".png"))
    print("ok", ns + ":" + item_id, len(elements), "cubes", "%dx%d" % (w, h))


if __name__ == "__main__":
    a = sys.argv[1:]
    item = a[a.index("--id") + 1] if "--id" in a else a[0]
    convert(a[0], a[1], a[2], item)
