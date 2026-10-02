"""Textures de la boutique « échoppe de nuit » (mod-hud, assets/reborn/textures/gui/shop/).

    python gen_shop_art.py

- wall.png    480×270  mur de planches sombres, lumière chaude au centre
- noren.png   64×40    un pan de noren indigo (répété sur la largeur), vagues blanches
- crest.png   28×28    blason 商 (cercle blanc) posé au centre du noren
- chochin.png 22×32    lanterne rouge 店
- coin.png    10×10    pièce de ryo (trou carré)
- stamp.png   22×22    tampon rouge 済 (déjà possédé)
- scroll.png  14×14    icône carte (parchemin roulé)    - robe.png 14×14 icône tenue
"""

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/shop"
KJ12 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 12)
KJ16 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 16)
KJ18 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 18)
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16


def wall():
    w, h = 480, 270
    r = np.random.default_rng(3)
    yy, xx = np.mgrid[0:h, 0:w]
    plank = xx // 24
    base = np.array((58, 36, 26), np.float32)
    tone = (r.uniform(0.85, 1.12, plank.max() + 1))[plank]
    grain = np.sin(yy / 5.0 + np.sin(xx / 7.0 + plank) * 2.2) * 0.05
    light = 1.15 - 0.55 * np.clip(np.hypot((xx - w * 0.35) / w, (yy - h * 0.55) / h) * 1.6, 0, 1)
    col = base[None, None] * (tone + grain)[..., None] * light[..., None]
    col[(xx % 24) == 0] *= 0.55
    q = np.floor(col / 6 + BAYER[yy % 4, xx % 4][..., None]) * 6
    Image.fromarray(q.clip(0, 255).astype(np.uint8), "RGB").convert("RGBA").save(OUT / "wall.png")


def noren():
    w, h = 64, 40
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((1, 0, w - 2, h - 1), fill=(34, 44, 92, 255))
    d.rectangle((1, 0, w - 2, 3), fill=(24, 30, 64, 255))
    for k in range(3):  # vagues (seigaiha simplifié)
        cy = h - 4 - k * 6
        for cx in range(-4, w + 8, 10):
            d.arc((cx - 5, cy - 5, cx + 5, cy + 5), 180, 360, fill=(200, 210, 236, 255))
    d.line((1, h - 1, w - 2, h - 1), fill=(20, 26, 56, 255))
    img.save(OUT / "noren.png")


def crest():
    s = 28
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    d.ellipse((0, 0, s - 1, s - 1), fill=(236, 232, 220, 255))
    d.ellipse((2, 2, s - 3, s - 3), outline=(34, 44, 92, 255))
    d.text((s / 2, s / 2 + 1), "商", font=KJ18, fill=(34, 44, 92, 255), anchor="mm")
    img.save(OUT / "crest.png")


def chochin():
    w, h = 22, 32
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    d.ellipse((0, 3, w - 1, h - 4), fill=(196, 40, 36, 255))
    for k in range(1, 6):
        y = 3 + (h - 7) * k / 6
        half = (w / 2) * math.sqrt(max(0, 1 - ((y - h / 2) / ((h - 7) / 2)) ** 2))
        d.line((w / 2 - half + 1, y, w / 2 + half - 1, y), fill=(150, 26, 24, 255))
    d.line((5, 6, 5, h - 8), fill=(240, 120, 90, 255))
    d.rectangle((5, 0, w - 6, 3), fill=(30, 22, 20, 255))
    d.rectangle((5, h - 4, w - 6, h - 1), fill=(30, 22, 20, 255))
    d.text((w / 2, h / 2), "店", font=KJ12, fill=(30, 14, 12, 255), anchor="mm")
    img.save(OUT / "chochin.png")


def coin():
    img = Image.new("RGBA", (10, 10), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, 9, 9), fill=(214, 170, 70, 255), outline=(140, 100, 30, 255))
    d.rectangle((4, 4, 5, 5), fill=(0, 0, 0, 0))
    d.point((2, 2), fill=(255, 230, 150, 255))
    img.save(OUT / "coin.png")


def stamp():
    s = 22
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    d.ellipse((0, 0, s - 1, s - 1), outline=(196, 40, 36, 220), width=2)
    d.text((s / 2, s / 2 + 1), "済", font=KJ12, fill=(196, 40, 36, 220), anchor="mm")
    img.save(OUT / "stamp.png")


def scroll_icon():
    img = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((3, 2, 11, 11), fill=(236, 220, 176, 255), outline=(120, 90, 50, 255))
    d.rectangle((1, 1, 12, 2), fill=(120, 70, 40, 255)); d.rectangle((1, 11, 12, 12), fill=(120, 70, 40, 255))
    d.line((5, 5, 9, 5), fill=(150, 120, 80, 255)); d.line((5, 7, 8, 7), fill=(150, 120, 80, 255))
    d.point((7, 9), fill=(196, 40, 36, 255))
    img.save(OUT / "scroll.png")


def robe_icon():
    img = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(4, 1), (10, 1), (13, 5), (11, 6), (11, 13), (3, 13), (3, 6), (1, 5)], fill=(70, 90, 150, 255),
              outline=(30, 36, 70, 255))
    d.line((7, 1, 5, 7), fill=(220, 220, 236, 255)); d.line((7, 1, 9, 7), fill=(220, 220, 236, 255))
    d.rectangle((3, 8, 11, 9), fill=(196, 40, 36, 255))
    img.save(OUT / "robe.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    wall(); noren(); crest(); chochin(); coin(); stamp(); scroll_icon(); robe_icon()
    print("->", OUT)
