"""Point d'entrée Blender de la chaîne d'émotes Reborn.

    blender --background --disable-autoexec <emote_creator.blend> --python build_emote.py -- \
        --spec specs/meditation.json [--out <fichier.json>] [--renders <dossier>] [--times 0,2,4] \
        [--views face,dos] [--skin peau.png] [--ground] [--blend <copie.blend>] [--sequence vue:debut:fin:pas]

Toujours passer par ``emote.ps1`` / ``emote.sh`` (ils trouvent Blender et le rig). Voir README.md.
"""

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.dont_write_bytecode = True  # pas de __pycache__ dans le dépôt
sys.path.insert(0, HERE)

import emote_lib as L  # noqa: E402

#: vues de contrôle par défaut : les angles de la vidéo de référence de la méditation
DEFAULT_VIEWS = {
    "face":   {"yaw": 8,   "pitch": 12, "dist": 4.0, "fov": 40, "target": [0, 14, 0]},
    "dessus": {"yaw": 25,  "pitch": 52, "dist": 4.0, "fov": 40, "target": [0, 12, 0]},
    "dos":    {"yaw": 160, "pitch": 35, "dist": 4.0, "fov": 40, "target": [0, 14, 0]},
    "profil": {"yaw": 80,  "pitch": 25, "dist": 4.0, "fov": 40, "target": [0, 14, 0]},
}


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    ap = argparse.ArgumentParser(prog="build_emote")
    ap.add_argument("--spec", required=True)
    ap.add_argument("--out", help="JSON exporté (défaut : aucun export)")
    ap.add_argument("--renders", help="dossier des vignettes (défaut : aucune)")
    ap.add_argument("--times", help="temps (s) des vignettes, séparés par des virgules")
    ap.add_argument("--views", help="sous-ensemble des vues, séparées par des virgules")
    ap.add_argument("--views-json", help="fichier JSON {nom: vue} remplaçant les vues")
    ap.add_argument("--res", type=int, default=512)
    ap.add_argument("--skin", help="PNG 64x64 de peau (défaut : peau de contrôle générée)")
    ap.add_argument("--ground", action="store_true", help="ajoute un sol vert sous le joueur")
    ap.add_argument("--blend", help="sauve une copie du .blend avec l'action (pour retoucher à la main)")
    ap.add_argument("--sequence", help="vue:debut:fin:pas -> images d'une séquence dans <renders>/seq_<vue>/")
    ap.add_argument("--axes", action="store_true", help="imprime la table des axes locaux des os")
    a = ap.parse_args(argv)

    spec = L.load_spec(a.spec)
    if a.axes:
        print("\n".join(L.describe_axes()))
    L.build_action(spec)
    if a.out:
        L.export(spec, a.out)
    if a.renders:
        L.make_proxy_skin(a.skin)
        L.setup_render(a.res)
        if a.ground:
            L.add_ground()
        views = dict(spec.get("views") or DEFAULT_VIEWS)
        if a.views_json:
            with open(a.views_json, encoding="utf-8") as f:
                views = json.load(f)
        if a.views:
            keep = a.views.split(",")
            views = {k: v for k, v in views.items() if k in keep}
        times = [float(t) for t in a.times.split(",")] if a.times else None
        L.render_views(spec, a.renders, views, times)
        if a.sequence:
            v, s, e, st = a.sequence.split(":")
            L.render_sequence(os.path.join(a.renders, "seq_" + v), views[v], float(s), float(e), int(st), prefix=v)
    if a.blend:
        L.select_rig()
        L.save_blend(a.blend)


if __name__ == "__main__":
    main()
    # sortie immédiate : Blender 5.2 en --background plante parfois (EXCEPTION_ACCESS_VIOLATION) en libérant le
    # contexte GPU de Workbench après des rendus ; tout est déjà écrit à ce stade
    sys.stdout.flush()
    os._exit(0)
