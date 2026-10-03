"""Textures de la sacoche « inrō » (mod-hud, assets/reborn/textures/gui/sacoche/).

    python gen_sacoche_art.py

- tier_on.png / tier_off.png  52×38  étage d'inrō : laque noire, reflet, liseré or, maki-e (vagues + grue)
- netsuke.png                  22×22  netsuke en ivoire sculpté (忍)
- ojime.png                    10×10  perle dorée du cordon
- obi.png                      48×30  motif de ceinture (soie indigo, vagues, liserés d'or) — se répète
- pedestal.png                 96×16  socle laqué sous le personnage
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/sacoche"
KJ10 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 11)
GOLD, GOLD_D = (246, 204, 120, 255), (170, 128, 52, 255)
BLACK_L, SHINE = (14, 10, 12, 255), (70, 56, 60, 255)


def tier(active):
    w, h = 52, 38
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h - 1), 6, fill=BLACK_L, outline=GOLD if active else GOLD_D)
    d.line((6, 2, w - 7, 2), fill=SHINE); d.line((8, 3, w // 2, 3), fill=(46, 38, 42, 255))
    d.line((3, h - 3, w - 4, h - 3), fill=(4, 2, 4, 255))
    # maki-e : vagues en bas, coupées au cadre
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0)); ld = ImageDraw.Draw(lay)
    col = (214, 170, 80, 255) if active else (120, 94, 46, 255)
    for k, cy in enumerate(range(h + 4, h - 10, -5)):
        for cx in range(-6 + (k % 2) * 5, w + 6, 10):
            ld.arc((cx - 5, cy - 5, cx + 5, cy + 5), 200, 340, fill=col)
    gx, gy = w - 22, h - 20                                     # grue
    ld.line((gx, gy + 6, gx + 8, gy + 2, gx + 16, gy + 6), fill=col); ld.line((gx + 8, gy + 2, gx + 10, gy + 8), fill=col)
    m = Image.new("L", (w, h), 0); ImageDraw.Draw(m).rounded_rectangle((2, 3, w - 3, h - 3), 4, fill=255)
    a = np.minimum(np.asarray(lay)[..., 3], np.asarray(m)); lay.putalpha(Image.fromarray(a))
    img.alpha_composite(lay)
    img.save(OUT / ("tier_on.png" if active else "tier_off.png"))


def netsuke():
    s = 22
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse((0, 1, s - 1, s - 1), fill=(228, 212, 176, 255), outline=(140, 110, 70, 255))
    d.arc((4, 5, s - 5, s - 7), 200, 340, fill=(160, 130, 90, 255))
    d.fontmode = "1"
    d.text((s / 2, s / 2 + 1), "忍", font=KJ10, fill=(120, 80, 50, 255), anchor="mm")
    img.save(OUT / "netsuke.png")


def ojime():
    s = 10
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse((1, 1, s - 2, s - 2), fill=(240, 200, 110, 255), outline=(150, 100, 40, 255))
    d.point((3, 3), fill=(255, 245, 210, 255)); d.point((4, 3), fill=(255, 236, 190, 255))
    img.save(OUT / "ojime.png")


def obi():
    w, h = 48, 30
    img = Image.new("RGBA", (w, h), (28, 36, 78, 255)); d = ImageDraw.Draw(img)
    for k, cy in enumerate(range(h, 0, -5)):
        for cx in range((k % 2) * 6 - 12, w + 12, 12):
            d.arc((cx - 6, cy - 6, cx + 6, cy + 6), 200, 340, fill=(48, 60, 116, 255))
    d.line((0, 1, w, 1), fill=GOLD); d.line((0, h - 2, w, h - 2), fill=GOLD)
    img.save(OUT / "obi.png")


def pedestal():
    w, h = 96, 16
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse((1, 3, w - 2, h - 1), fill=(0, 0, 0, 160))
    d.ellipse((2, 0, w - 3, h - 4), fill=BLACK_L, outline=GOLD_D)
    d.arc((8, 2, w - 9, h - 6), 200, 340, fill=SHINE)
    img.save(OUT / "pedestal.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    tier(True); tier(False); netsuke(); ojime(); obi(); pedestal()
    print("->", OUT)
