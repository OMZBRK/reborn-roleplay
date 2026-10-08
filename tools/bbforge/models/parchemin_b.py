"""Parchemin pose, rang B (voir models/_parchemin.py)."""
from models._parchemin import CAMERA_HINT, DENS, VIEW, anims, build  # noqa: F401

TITLE = "PARCHEMIN · RANG B"
SUBTITLE = "OBJET · REBORN RP"
FOCUS_BONES = {"head": "scroll"}
SHADOW_BONES = ["scroll"]


def build_model():
    return build("B")


ANIMS = anims()
ANIMATED_BONES = ["scroll"]
