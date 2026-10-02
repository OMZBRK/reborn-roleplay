"""Textures de la fiche shinobi « lanternes célestes » (mod-hud, assets/reborn/textures/gui/stats/).

    python gen_stats_lanterns.py

- sky.png      480×270  ciel de nuit en dégradé tramé (Bayer 4×4) + étoiles fixes
- village.png  480×56   collines + maisons en silhouette, fenêtres chaudes (fond transparent)
- lantern_<i>.png 30×36 une lanterne céleste par stat (ordre StatDef), kanji peint
- glow.png     64×64    halo radial blanc (teinté/alpha au rendu)

Les nuages réutilisent textures/gui/map/cloud_*.png (teintés de nuit au rendu).
Dépendances : numpy, Pillow ; police CJK Windows (msgothic) pour les kanji.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/stats"
KJ = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 12)

# ordre = StatDef : TAIJUTSU, KENJUTSU, NINJUTSU, CONTROLE, VIGUEUR, CHAKRA
STATS = [("体", (232, 116, 59)), ("剣", (185, 199, 214)), ("忍", (155, 107, 255)),
         ("制", (60, 203, 154)), ("力", (224, 72, 90)), ("気", (63, 184, 245))]

BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16


def sky():
    w, h = 480, 270
    top, bottom = np.array((6, 6, 22), np.float32), np.array((70, 36, 64), np.float32)
    yy, xx = np.mgrid[0:h, 0:w]
    t = np.clip(yy / (h * 0.92), 0, 1) ** 1.15
    levels = 9
    q = np.clip(np.floor(t * levels + BAYER[yy % 4, xx % 4]) / levels, 0, 1)
    col = top[None, None] * (1 - q[..., None]) + bottom[None, None] * q[..., None]
    img = Image.fromarray(col.astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)
    r = np.random.default_rng(11)
    for _ in range(140):
        x, y = int(r.integers(0, w)), int(r.integers(0, int(h * 0.7)))
        b = int(r.uniform(90, 200))
        d.point((x, y), fill=(b, b, min(255, b + 25), 255))
    img.save(OUT / "sky.png")


def village():
    w, h = 480, 56
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hill = (18, 12, 22, 255)
    pts = [(x, 22 - 7 * np.sin(x / 45) - 3 * np.sin(x / 15.5)) for x in range(0, w + 3, 3)]
    d.polygon(pts + [(w, h), (0, h)], fill=hill)
    r = np.random.default_rng(4)
    x = 6
    while x < w - 10:
        base = 22 - 7 * np.sin(x / 45) - 3 * np.sin(x / 15.5) + 2
        hw = int(r.integers(7, 11))
        d.rectangle((x, base - 6, x + hw, base + 2), fill=(26, 18, 28, 255))
        d.polygon([(x - 2, base - 6), (x + hw / 2, base - 10 - int(r.integers(0, 2))), (x + hw + 2, base - 6)], fill=(26, 18, 28, 255))
        if r.random() < 0.8:
            wx = x + hw // 2 - 1
            d.rectangle((wx, base - 4, wx + 2, base - 2), fill=(170, 104, 54, 255))
        x += hw + int(r.integers(10, 26))
    img.save(OUT / "village.png")


def lerp(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))
def light(c, k): return tuple(min(255, int(v + (255 - v) * k)) for v in c)
def dark(c, k): return tuple(int(v * (1 - k)) for v in c)


def lantern(i, kanji, col):
    w, h = 30, 36
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    top_w, bot_w, ph = 28, 20, 30          # trapèze : large en haut
    cx = w / 2
    for k in range(ph):
        t = k / (ph - 1)
        hw = top_w / 2 - (top_w / 2 - bot_w / 2) * t
        c = lerp((255, 214, 150), col, 0.35 + 0.65 * t)
        c = light(c, 0.18 * (1 - t))
        d.line((round(cx - hw), k, round(cx + hw) - 1, k), fill=(*c, 255))
    # côtes verticales + bord bas
    for f in (-0.2, 0.2):
        d.line((round(cx + top_w * f), 1, round(cx + bot_w * f), ph - 2), fill=(*dark(col, 0.35), 255))
    d.line((round(cx - bot_w / 2), ph - 1, round(cx + bot_w / 2) - 1, ph - 1), fill=(*dark(col, 0.5), 255))
    d.line((round(cx - top_w / 2), 0, round(cx + top_w / 2) - 1, 0), fill=(*light(col, 0.5), 255))
    # kanji
    d.text((cx, ph * 0.46), kanji, font=KJ, fill=(*dark(col, 0.7), 255), anchor="mm")
    # flamme sous la lanterne
    d.polygon([(cx - 3, ph + 4), (cx, ph - 3), (cx + 3, ph + 4)], fill=(255, 240, 190, 255))
    d.rectangle((cx - 1, ph, cx, ph + 2), fill=(255, 255, 235, 255))
    img.save(OUT / f"lantern_{i}.png")


def glow():
    s = 64
    yy, xx = np.mgrid[0:s, 0:s]
    dist = np.hypot(xx - (s - 1) / 2, yy - (s - 1) / 2) / (s / 2)
    a = np.clip(1 - dist, 0, 1) ** 2.2
    a = np.round(a * 12) / 12            # paliers doux, cohérents avec le pixel-art
    img = np.zeros((s, s, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(OUT / "glow.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    sky(); village(); glow()
    for i, (k, c) in enumerate(STATS):
        lantern(i, k, c)
    print("->", OUT)
