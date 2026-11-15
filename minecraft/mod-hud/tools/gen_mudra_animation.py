"""Pose des mudras au clavier (mod reborn-hud, 14/11) : animations/mudra_signe.json.

Les mains jointes devant le torse : seuls les bras, la tête et le buste, pour que le personnage puisse marcher.
En-tête, tête et buste repris de mudra_chakratest.json (l'animation du Test de la Feuille, du propriétaire) ; les bras
sont posés ici, rigides. À chaque signe
l'animation repart : les mains s'écartent un peu (0 s) puis se rejoignent (0,1 s), et la pose tient.
Provisoire : une seule pose pour les douze signes.

Usage : python -X utf8 minecraft/mod-hud/tools/gen_mudra_animation.py
"""
import copy
import json
import os

ICI = os.path.dirname(os.path.abspath(__file__))
DOSSIER = os.path.normpath(os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn-hud", "animations"))
ECART = 0.84   # part de la pose à l'image 0 (mains un peu écartées)

src = json.load(open(os.path.join(DOSSIER, "mudra_chakratest.json"), encoding="utf-8"))
a = src["animations"]["mudra_chakratest"]


def derniere(canal):
    cles = sorted(canal.keys(), key=float)
    return copy.deepcopy(canal[cles[-1]])


def ecarte(image):
    out = copy.deepcopy(image)
    if "vector" in out:
        out["vector"] = [v * ECART if isinstance(v, (int, float)) else v for v in out["vector"]]
    if "value" in out:
        out["value"] = out["value"] * ECART
    return out


# Bras rigides : dans le jeu le coude ne plie pas, la pose pliée du Test de la Feuille laissait les mains écartées.
# Les deux bras montent devant le torse et rentrent vers l'axe du corps : les poings se touchent.
POSE = {"right_arm": [-62.0, -34.0, 0.0], "left_arm": [-62.0, 34.0, 0.0]}
bones = {}
for bras, rot in POSE.items():
    fin = {"vector": rot, "easing": "easeOutSine"}
    bones[bras] = {"rotation": {"0.0": ecarte(fin), "0.1": fin}}
bones["head"] = {"rotation": {"0.0": derniere(a["bones"]["head"]["rotation"])}}
bones["torso"] = {"bend": {"0.0": derniere(a["bones"]["torso"]["bend"])}}

out = {k: v for k, v in src.items() if k != "animations"}
out["animations"] = {"mudra_signe": {
    "model": a["model"],
    "parents": a["parents"],
    "animation_length": 0.1,
    "loop": "hold_on_last_frame",
    "bones": bones,
}}
json.dump(out, open(os.path.join(DOSSIER, "mudra_signe.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("écrit mudra_signe.json", os.path.getsize(os.path.join(DOSSIER, "mudra_signe.json")), "octets")

# Sortie : de la pose des mains jointes au repos en 0,3 s (les bras redescendent, la tête et le buste se redressent),
# jouée 0,65 s après le dernier signe. Sans elle, la fin passait par la pose du Test de la Feuille (relecture du 15/11).
ZERO = {"vector": [0.0, 0.0, 0.0], "easing": "easeInOutSine"}
fin = {}
for bras, rot in POSE.items():
    fin[bras] = {"rotation": {"0.0": {"vector": rot, "easing": "linear"}, "0.3": ZERO}}
fin["head"] = {"rotation": {"0.0": derniere(a["bones"]["head"]["rotation"]), "0.3": ZERO}}
fin["torso"] = {"bend": {"0.0": derniere(a["bones"]["torso"]["bend"]), "0.3": {"value": 0.0, "easing": "easeInOutSine"}}}
out["animations"] = {"mudra_signe_fin": {
    "model": a["model"],
    "parents": a["parents"],
    "animation_length": 0.3,
    "loop": False,
    "bones": fin,
}}
json.dump(out, open(os.path.join(DOSSIER, "mudra_signe_fin.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("écrit mudra_signe_fin.json", os.path.getsize(os.path.join(DOSSIER, "mudra_signe_fin.json")), "octets")
