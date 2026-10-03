"""Textures du menu OST, DA Reborn (mod-ost, assets/reborn-ost/textures/gui/).

    python gen_ost_art.py

- disc.png  64×64  disque de laque noire : sillons, arcs de maki-e dorés, étiquette vermillon « 音 »
"""

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-ost/src/main/resources/assets/reborn-ost/textures/gui"
KJ = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 12)


def disc():
    s = 64
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse((0, 0, s - 1, s - 1), fill=(12, 9, 11, 255), outline=(170, 128, 52, 255))
    for r in range(28, 12, -3):
        d.ellipse((c - r, c - r, c + r - 1, c + r - 1), outline=(26, 20, 23, 255))
    for k in range(5):   # arcs de maki-e
        a0 = k * 72
        d.arc((5, 5, s - 6, s - 6), a0, a0 + 32, fill=(214, 170, 80, 255), width=2)
    d.arc((3, 3, s - 4, s - 4), 200, 250, fill=(90, 80, 86, 255))          # reflet de laque
    d.ellipse((c - 11, c - 11, c + 10, c + 10), fill=(170, 30, 34, 255), outline=(246, 204, 120, 255))
    d.fontmode = "1"
    d.text((c, c), "音", font=KJ, fill=(250, 238, 214, 255), anchor="mm")
    img.save(OUT / "disc.png")


if __name__ == "__main__":
    disc()
    print("->", OUT)
