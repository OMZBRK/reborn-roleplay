"""Parchemin pose, rang S (voir models/_parchemin.py)."""
from models._parchemin import CAMERA_HINT, DENS, VIEW, anims, build  # noqa: F401

TITLE = "PARCHEMIN · RANG S"
SUBTITLE = "OBJET · REBORN RP"
FOCUS_BONES = {"head": "scroll"}
SHADOW_BONES = ["scroll"]


def build_model():
    return build("S")


ANIMS = anims()
ANIMATED_BONES = ["scroll"]
