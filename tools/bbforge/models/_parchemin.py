"""Rouleau de parchemin pose (meuble Nexo / objet en main), une variante par rang.

1 unite = 1/16 bloc. Le rouleau est couche le long de X, ~13 unites de long,
3 de diametre : papier washi (deux cubes croises pour arrondir), embouts de
bois (or pour les rangs A et S), cordon noue au centre a la couleur du rang,
pompon pose sur la table, petite etiquette scellee sur le dessus.
"""
from __future__ import annotations

from forge.bb import Model
from models._anim import anim, w

CAMERA_HINT = dict(az0=210.0, el=24.0, dist_mul=1.0)
VIEW = 1.0
DENS = 4


def build(rank: str) -> Model:
    noble = rank in ("A", "S")
    cap = "gold" if noble else "strap"
    cord = "cord_" + rank.lower()
    m = Model("parchemin_" + rank.lower())
    m.visible_box = [2, 1, 0]
    s = m.root.bone("scroll", (0, 0, 0))
    # papier roule
    s.cube("paper", (-5, 0.5, -1.5), (5, 2.5, 1.5), "washi")
    s.cube("paper_x", (-5, 0, -1), (5, 3, 1), "washi", hide=("east", "west"))
    # bord du papier qui depasse a peine du rouleau
    s.cube("edge", (-5, 2.75, 0.75), (5, 3.0, 1.5), "washi", hide=("down",))
    # embouts (jiku) et leurs boutons
    for sx, tag in ((-1, "l"), (1, "r")):
        x0, x1 = (-6.25, -5) if sx < 0 else (5, 6.25)
        s.cube("cap_" + tag, (x0, 0.25, -1.25), (x1, 2.75, 1.25), cap)
        k0, k1 = (-6.75, -6.25) if sx < 0 else (6.25, 6.75)
        s.cube("knob_" + tag, (k0, 0.75, -0.75), (k1, 2.25, 0.75), cap)
    # cordon noue et pompon couche sur la table
    s.cube("cord", (-0.75, -0.1, -1.6), (0.75, 3.1, 1.6), cord)
    s.cube("knot", (-0.5, 1.0, 1.6), (0.5, 2.0, 2.2), cord)
    s.cube("tassel", (-0.35, 0.0, 2.2), (0.35, 0.5, 4.2), cord)
    # etiquette scellee sur le dessus
    s.cube("tag", (2.0, 3.0, -0.75), (4.0, 3.15, 0.75), "washi")
    s.cube("seal", (2.5, 3.15, -0.4), (3.5, 3.25, 0.4), "seal")
    return m


def _idle(t):
    return {"scroll": {"rotation": (0.0, 0.0, 0.0), "position": (0.0, 0.15 * w(t, 4.0), 0.0)}}


def anims():
    return {"idle": anim(_idle, 4.0, [(0.0, "POSE")], True, dict(turns=1.0, az0=210.0))}
