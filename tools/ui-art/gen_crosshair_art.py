"""Textures de l'éditeur de viseur (mod-hud, assets/reborn/textures/gui/crosshair_editor/).

    python gen_crosshair_art.py

- mato.png   128×128  cible de kyūdō (anneaux noirs/blancs, liseré d'or), fond transparent
- stand.png   64×40   pied laqué de la cible
"""

from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/crosshair_editor"


def mato():
    s = 128
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    c = s / 2
    rings = [(62, (24, 18, 18)), (55, (236, 230, 214)), (46, (24, 18, 18)), (39, (236, 230, 214)),
             (30, (24, 18, 18)), (23, (236, 230, 214)), (14, (24, 18, 18))]
    d.ellipse((c - 64, c - 64, c + 63, c + 63), fill=(246, 204, 120, 255))
    for r, col in rings:
        d.ellipse((c - r, c - r, c + r - 1, c + r - 1), fill=col + (255,))
    d.arc((c - 60, c - 60, c + 59, c + 59), 200, 260, fill=(255, 255, 255, 60), width=2)   # reflet
    img.save(OUT / "mato.png")


def stand():
    w, h = 64, 40
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle((w // 2 - 3, 0, w // 2 + 2, h - 10), fill=(14, 10, 12, 255))
    d.polygon([(4, h - 1), (w - 5, h - 1), (w - 14, h - 10), (13, h - 10)], fill=(14, 10, 12, 255), outline=(170, 128, 52, 255))
    d.line((w // 2 - 2, 0, w // 2 - 2, h - 12), fill=(70, 56, 60, 255))
    img.save(OUT / "stand.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    mato(); stand()
    print("->", OUT)
