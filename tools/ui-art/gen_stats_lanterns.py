"""Textures de la fiche shinobi « lanternes célestes » v2 (mod-hud, assets/reborn/textures/gui/stats/).

    python gen_stats_lanterns.py

Ciel / parallaxe (les silhouettes sont BLANCHES : teintées au rendu, crépuscule → nuit) :
- sky_dusk.png, sky_night.png  480×270  dégradés tramés (Bayer 4×4)
- mountains.png 480×120  crête lointaine        - village.png 480×80  collines + maisons + pagodes
- windows.png   480×80   fenêtres du village (même cadrage que village.png)
- moon.png      40×40    pleine lune (blanche, cratères gris)
Lanternes / interface :
- lantern_<i>.png 40×50  une lanterne céleste par stat (ordre StatDef), kanji peint, flamme
- glow.png 64×64 halo     - hitodama.png 10×16 flamme-esprit (un point à répartir)
- ema.png 92×56 plaque votive en bois (accrochée à la rambarde)
- seal_<i>.png 18×18 sceau kanji de la stat    - dk_<i>.png 12×12 kanji des valeurs dérivées

Dépendances : numpy, Pillow ; police CJK Windows (msgothic) pour les kanji.
"""

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/stats"
KJ12 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 12)
KJ14 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 14)
KJ16 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 16)

# ordre = StatDef : TAIJUTSU, KENJUTSU, NINJUTSU, CONTROLE, VIGUEUR, CHAKRA
STATS = [("体", (232, 116, 59)), ("剣", (185, 199, 214)), ("忍", (155, 107, 255)),
         ("制", (60, 203, 154)), ("力", (224, 72, 90)), ("気", (63, 184, 245))]
DERIVED = ["体", "気", "息", "癒", "省", "撃"]  # PV, chakra, endurance, régén, coûts, critique

BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16
WHITE = (255, 255, 255, 255)


def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def light(c, k): return tuple(min(255, int(v + (255 - v) * k)) for v in c)
def dark(c, k): return tuple(int(v * (1 - k)) for v in c)


def gradient(top, bottom, path):
    w, h = 480, 270
    yy, xx = np.mgrid[0:h, 0:w]
    t = np.clip(yy / (h * 0.95), 0, 1)
    q = np.clip(np.floor(t * 9 + BAYER[yy % 4, xx % 4]) / 9, 0, 1)
    col = np.array(top, np.float32)[None, None] * (1 - q[..., None]) + np.array(bottom, np.float32)[None, None] * q[..., None]
    Image.fromarray(col.astype(np.uint8), "RGB").convert("RGBA").save(OUT / path)


def ridge_pts(w, base, amp, f1, f2, ph):
    return [(x, base - amp * (0.6 * math.sin(x / f1 + ph) + 0.4 * math.sin(x / f2 + ph * 1.7))) for x in range(-4, w + 6, 2)]


def mountains():
    w, h = 480, 120
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(img).polygon(ridge_pts(w, 40, 26, 55, 18, 1.3) + [(w, h), (0, h)], fill=WHITE)
    img.save(OUT / "mountains.png")


def village():
    w, h = 480, 80
    sil = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    win = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d, dw = ImageDraw.Draw(sil), ImageDraw.Draw(win)
    base = 36
    def hy(x): return base - 6 * (0.6 * math.sin(x / 30) + 0.4 * math.sin(x / 9.5))
    d.polygon([(x, hy(x)) for x in range(-2, w + 3, 2)] + [(w, h), (0, h)], fill=WHITE)
    r = np.random.default_rng(4)
    x = 4
    while x < w - 8:
        by = hy(x) + 1
        hw = int(r.integers(6, 10))
        tall = r.random() < 0.12
        hh = 13 if tall else 5
        d.rectangle((x, by - hh, x + hw, by + 2), fill=WHITE)
        d.polygon([(x - 2, by - hh), (x + hw / 2, by - hh - 4), (x + hw + 2, by - hh)], fill=WHITE)
        if tall:
            d.polygon([(x - 1, by - hh + 5), (x + hw / 2, by - hh + 2), (x + hw + 1, by - hh + 5)], fill=WHITE)
        if r.random() < 0.85:
            wx = x + hw // 2 - 1
            dw.rectangle((wx, by - 3, wx + 1, by - 1), fill=WHITE)
        if tall and r.random() < 0.8:
            dw.rectangle((x + hw // 2 - 1, by - hh + 1, x + hw // 2, by - hh + 2), fill=WHITE)
        x += hw + int(r.integers(3, 12))
    sil.save(OUT / "village.png")
    win.save(OUT / "windows.png")


def moon():
    s = 40
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, s - 1, s - 1), fill=WHITE)
    for (cx, cy, r) in ((12, 14, 4), (26, 24, 5), (18, 30, 3), (28, 11, 2)):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(214, 210, 204, 255))
    d.arc((1, 1, s - 2, s - 2), 120, 300, fill=(236, 232, 226, 255))
    img.save(OUT / "moon.png")


def lantern(i, kanji, col):
    w, h = 40, 50
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    ph = 42
    top_w, bot_w = 38, 26
    cx = w / 2
    for k in range(ph):
        t = k / (ph - 1)
        hw = top_w / 2 - (top_w / 2 - bot_w / 2) * t
        c = light(lerp((255, 222, 168), col, 0.25 + 0.75 * t), 0.22 * (1 - t))
        x0, x1 = round(cx - hw), round(cx + hw) - 1
        d.line((x0, k, x1, k), fill=(*c, 255))
        for f in (-0.34, 0.0, 0.34):  # plis du papier
            d.point((round(cx + hw * f), k), fill=(*dark(c, 0.16), 255))
        d.point((x0, k), fill=(*dark(c, 0.25), 255)); d.point((x1, k), fill=(*dark(c, 0.25), 255))
    d.line((round(cx - top_w / 2), 0, round(cx + top_w / 2) - 1, 0), fill=(*light(col, 0.6), 255))
    # anneau de bambou
    d.rectangle((round(cx - bot_w / 2), ph - 2, round(cx + bot_w / 2) - 1, ph), fill=(70, 46, 30, 255))
    d.line((round(cx - bot_w / 2), ph - 2, round(cx + bot_w / 2) - 1, ph - 2), fill=(150, 110, 70, 255))
    d.text((cx, ph * 0.43), kanji, font=KJ16, fill=(*dark(col, 0.72), 255), anchor="mm")
    # flamme
    d.polygon([(cx - 3, ph + 6), (cx, ph - 2), (cx + 3, ph + 6)], fill=(255, 236, 170, 255))
    d.rectangle((cx - 1, ph + 2, cx, ph + 4), fill=(255, 255, 240, 255))
    img.save(OUT / f"lantern_{i}.png")


def glow():
    s = 64
    yy, xx = np.mgrid[0:s, 0:s]
    dist = np.hypot(xx - (s - 1) / 2, yy - (s - 1) / 2) / (s / 2)
    a = np.round(np.clip(1 - dist, 0, 1) ** 2.2 * 12) / 12
    img = np.zeros((s, s, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(OUT / "glow.png")


def hitodama():
    img = Image.new("RGBA", (10, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(1, 9), (5, 0), (9, 9)], fill=(170, 220, 255, 255))
    d.ellipse((1, 5, 9, 13), fill=(210, 240, 255, 255))
    d.ellipse((3, 7, 7, 11), fill=(255, 255, 255, 255))
    for k in range(3): d.point((5 + k, 13 + k), fill=(120, 180, 240, 200 - k * 50))
    img.save(OUT / "hitodama.png")


def ema():
    w, h = 92, 56
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    body = [(0, 8), (w / 2, 0), (w - 1, 8), (w - 1, h - 1), (0, h - 1)]
    d.polygon(body, fill=(214, 182, 132, 255), outline=(120, 82, 48, 255))
    r = np.random.default_rng(2)
    for y in range(12, h - 2, 3):  # veinage du bois
        x0 = int(r.integers(2, 20)); x1 = int(r.integers(w - 30, w - 3))
        d.line((x0, y, x1, y), fill=(204, 170, 120, 255))
    d.line((3, 10, w - 4, 10), fill=(186, 150, 104, 255))
    d.ellipse((w / 2 - 2, 3, w / 2 + 2, 7), fill=(90, 60, 36, 255))  # trou de la cordelette
    img.save(OUT / "ema.png")


def seal(i, kanji, col):
    img = Image.new("RGBA", (18, 18), (*col, 255))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    d.rectangle((0, 0, 17, 17), outline=(*dark(col, 0.35), 255))
    d.text((9, 9), kanji, font=KJ12, fill=(30, 18, 14, 255), anchor="mm")
    img.save(OUT / f"seal_{i}.png")


def dk(i, kanji):
    img = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.fontmode = "1"
    d.text((6, 6), kanji, font=KJ12, fill=(214, 86, 74, 255), anchor="mm")
    img.save(OUT / f"dk_{i}.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for old in ("sky.png",):
        (OUT / old).unlink(missing_ok=True)
    gradient((64, 52, 110), (236, 132, 92), "sky_dusk.png")
    gradient((6, 6, 22), (66, 34, 62), "sky_night.png")
    mountains(); village(); moon(); glow(); hitodama(); ema()
    for i, (k, c) in enumerate(STATS):
        lantern(i, k, c)
        seal(i, k, c)
    for i, k in enumerate(DERIVED):
        dk(i, k)
    print("->", OUT)
