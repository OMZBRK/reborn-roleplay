"""Textures du menu Échap « tokonoma » (mod-hud, assets/reborn/textures/gui/esc/).

    python gen_esc_art.py

Espace de dessin virtuel 640×360 (l'écran est mis à l'échelle en jeu).
- room.png          640×360  pièce : enduit, piliers, poutre à kamon, alcôve, plancher laqué
- window.png        140×140  vue nocturne du village (disque, transparent autour) + window_lights.png
- window_frame.png  140×140  cadre rond + croisillons
- fusuma_l/r.png    320×360  portes coulissantes de l'ouverture
- kamishibai.png    178×132  théâtre de papier (volets ouverts, écran vide)
- kawaraban.png     134×122  feuille d'annonce sous auvent (en-tête noir vide)
- rack.png          226×122  porte-amulettes laqué
- omamori.png       22×36    amulette blanche (teintée en jeu) ; omamori_label.png 12×18
- rbcoin.png        12×12    pièce RBCoin
- kamon_plate.png   48×48    disque d'emblème (teinté en jeu)
- village_<id>.png  48×48    emblèmes des villages, blancs (teintés en jeu) — sources : logos/*.svg (Wikimedia Commons)
- social_<id>.png   32×32    Discord, X, YouTube (Simple Icons) + site (globe)
"""

import math
from pathlib import Path

import numpy as np
import pymupdf
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/esc"
GUI = ROOT / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui"
LOGOS = Path(__file__).resolve().parent / "logos"
KJ10 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 10)
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16


def dither(col, step=6):
    h, w = col.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    return np.floor(col / step + BAYER[yy % 4, xx % 4][..., None]) * step


def plaster(w, h, base, var=0.08, seed=1):
    r = np.random.default_rng(seed)
    n = r.random((h // 3 + 1, w // 3 + 1))
    n = np.asarray(Image.fromarray((n * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), np.float32) / 255
    col = np.array(base, np.float32)[None, None] * (1 - var / 2 + var * n[..., None])
    return dither(col)


def to_img(arr):
    return Image.fromarray(np.concatenate([arr.clip(0, 255).astype(np.uint8), np.full(arr.shape[:2] + (1,), 255, np.uint8)], -1))


# ------------------------------------------------------------------ pièce
def room():
    w, h = 640, 360
    img = to_img(plaster(w, h, (198, 178, 138), seed=31))
    d = ImageDraw.Draw(img)
    # poutre haute
    d.rectangle((0, 0, w, 16), fill=(52, 30, 20)); d.line((0, 16, w, 16), fill=(30, 18, 12))
    # alcôve (tokonoma) : fond plus sombre, ombre portée sous la poutre à kamon
    al = plaster(398, 279, (178, 158, 120), var=0.06, seed=32)
    yy = np.arange(279)[:, None, None]
    al = al * (0.82 + 0.18 * np.clip(yy / 60, 0, 1))
    img.paste(to_img(dither(al)), (222, 48))
    d = ImageDraw.Draw(img)
    d.rectangle((222, 20, 620, 47), fill=(62, 38, 24)); d.line((222, 20, 620, 20), fill=(96, 60, 36))
    d.line((222, 47, 620, 47), fill=(28, 16, 10))
    for x in range(232, 612, 24):  # veinage léger
        d.line((x, 23, x + 10, 23), fill=(70, 44, 28))
    d.rectangle((222, 318, 620, 327), fill=(24, 14, 12)); d.line((222, 318, 620, 318), fill=(70, 40, 30))  # toko-gamachi
    # piliers
    for x0, x1 in ((0, 13), (200, 214), (620, 640)):
        d.rectangle((x0, 0, x1, 327), fill=(100, 62, 34))
        d.rectangle((x1 - 3, 0, x1, 327), fill=(70, 40, 22))
        d.rectangle((x0, 0, x0 + 1, 327), fill=(126, 82, 48))
    # plancher laqué
    d.rectangle((0, 328, w, h), fill=(42, 11, 15))
    for y in range(331, h, 5): d.line((0, y, w, y), fill=(56, 15, 20))
    d.line((0, 328, w, 328), fill=(120, 36, 40))
    img.save(OUT / "room.png")


# ------------------------------------------------------------------ fenêtre ronde
def tint(im, rgb):
    a = np.asarray(im).astype(np.float32)
    a[..., :3] = a[..., :3] * np.array(rgb, np.float32)[None, None] / 255
    return Image.fromarray(a.clip(0, 255).astype(np.uint8))


def window():
    """Vue de nuit (mêmes calques que la fiche, teintes « nuit »). Les fenêtres allumées
    sont un calque séparé (window_lights.png) qui scintille en jeu."""
    s = 140
    sky = Image.open(GUI / "stats/sky_night.png").convert("RGBA")
    mnt = tint(Image.open(GUI / "stats/mountains.png").convert("RGBA"), (30, 22, 46))
    vil = tint(Image.open(GUI / "stats/village.png").convert("RGBA"), (18, 12, 24))
    win = tint(Image.open(GUI / "stats/windows.png").convert("RGBA"), (190, 110, 56))
    moon = tint(Image.open(GUI / "stats/moon.png").convert("RGBA"), (248, 238, 210))
    scene = sky.copy()
    scene.alpha_composite(mnt, (0, 140)); scene.alpha_composite(vil, (0, 186))
    ImageDraw.Draw(scene).rectangle((0, 266, 480, 270), fill=(18, 12, 24, 255))
    box = (250, 112, 250 + s, 112 + s)
    view = scene.crop(box)
    view.alpha_composite(moon, (84, 16))
    m = Image.new("L", (s, s), 0); ImageDraw.Draw(m).ellipse((0, 0, s - 1, s - 1), fill=255)
    view.putalpha(m)
    view.save(OUT / "window.png")
    lights = Image.new("RGBA", (480, 270), (0, 0, 0, 0)); lights.alpha_composite(win, (0, 186))
    lights = lights.crop(box)
    la = np.minimum(np.asarray(lights)[..., 3], np.asarray(m)); lights.putalpha(Image.fromarray(la))
    lights.save(OUT / "window_lights.png")
    fr = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(fr)
    for k in (-1, 0, 1):
        x = s // 2 + k * 36
        half = int(math.sqrt(max(0, (s / 2 - 4) ** 2 - (x - s / 2) ** 2)))
        d.rectangle((x - 1, s // 2 - half, x, s // 2 + half), fill=(74, 44, 24, 255))
    d.ellipse((0, 0, s - 1, s - 1), outline=(70, 40, 22, 255), width=6)
    d.ellipse((1, 1, s - 2, s - 2), outline=(110, 68, 38, 255), width=1)
    d.ellipse((5, 5, s - 6, s - 6), outline=(40, 22, 12, 255), width=1)
    fr.save(OUT / "window_frame.png")


# ------------------------------------------------------------------ fusuma
def fusuma(side):
    w, h = 320, 360
    img = to_img(plaster(w, h, (226, 214, 184), var=0.06, seed=40 + side))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w - 1, h - 1), outline=(46, 26, 16), width=6)
    # kumiko : croisillons fins sur le tiers haut
    for x in range(6, w - 6, 26): d.line((x, 6, x, 120), fill=(120, 84, 52))
    for y in range(6, 120, 26): d.line((6, y, w - 7, y), fill=(120, 84, 52))
    d.line((6, 120, w - 7, 120), fill=(70, 44, 26), width=2)
    # motif : vagues d'or discrètes en bas
    for k in range(4):
        cy = h - 30 - k * 12
        for cx in range(-10, w + 20, 24):
            d.arc((cx - 12, cy - 12, cx + 12, cy + 12), 180, 360, fill=(196, 160, 90))
    # poignée (hikite) côté jonction
    hx = w - 30 if side == 0 else 30
    d.ellipse((hx - 9, h / 2 - 9, hx + 9, h / 2 + 9), fill=(30, 20, 14), outline=(200, 160, 90))
    d.ellipse((hx - 5, h / 2 - 5, hx + 5, h / 2 + 5), fill=(60, 40, 26))
    img.save(OUT / ("fusuma_l.png" if side == 0 else "fusuma_r.png"))


# ------------------------------------------------------------------ kamishibai
def kamishibai():
    w, h = 178, 132
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    wood, dark, light = (120, 72, 38, 255), (60, 34, 18, 255), (156, 100, 58, 255)
    d.polygon([(22, 16), (2, 24), (2, h - 12), (22, h - 4)], fill=wood, outline=dark)
    d.polygon([(w - 23, 16), (w - 3, 24), (w - 3, h - 12), (w - 23, h - 4)], fill=wood, outline=dark)
    for y in range(30, h - 14, 10):
        d.line((5, y, 19, y + 1), fill=(104, 62, 32, 255)); d.line((w - 20, y + 1, w - 6, y), fill=(104, 62, 32, 255))
    d.rectangle((22, 14, w - 23, h - 2), fill=(98, 58, 30, 255), outline=dark)
    d.line((23, 15, w - 24, 15), fill=light)
    d.rectangle((30, 22, w - 31, h - 10), fill=(0, 0, 0, 0), outline=dark)
    # petit toit de la boîte
    d.polygon([(18, 14), (w / 2, 4), (w - 19, 14)], fill=(70, 40, 22, 255), outline=dark)
    img.save(OUT / "kamishibai.png")


# ------------------------------------------------------------------ kawaraban
def kawaraban():
    w, h = 134, 122
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle((5, 12, 9, h - 1), fill=(90, 58, 32, 255)); d.rectangle((w - 10, 12, w - 6, h - 1), fill=(90, 58, 32, 255))
    d.polygon([(0, 14), (w / 2, 0), (w - 1, 14)], fill=(40, 26, 20, 255), outline=(20, 12, 10, 255))
    for x in range(8, w - 8, 6): d.line((x, 14 - abs(x - w / 2) * 14 / (w / 2) + 2, x, 14), fill=(56, 38, 30, 255))
    paper = plaster(108, 98, (236, 222, 184), var=0.05, seed=61)
    img.paste(to_img(paper), (13, 20))
    d = ImageDraw.Draw(img)
    d.rectangle((13, 20, 120, 117), outline=(120, 90, 50, 255))
    d.rectangle((13, 20, 120, 31), fill=(30, 26, 24, 255))
    img.save(OUT / "kawaraban.png")


# ------------------------------------------------------------------ porte-amulettes
def rack():
    w, h = 226, 122
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rectangle((0, 0, w - 1, h - 1), fill=(46, 12, 16, 255), outline=(246, 204, 120, 255))
    d.rectangle((2, 2, w - 3, h - 3), outline=(110, 40, 40, 255))
    d.rectangle((8, 24, w - 9, 27), fill=(110, 68, 38, 255)); d.line((8, 24, w - 9, 24), fill=(150, 100, 60, 255))
    d.line((8, h - 22, w - 9, h - 22), fill=(90, 34, 36, 255))
    img.save(OUT / "rack.png")


def omamori():
    w, h = 22, 36
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse((8, 0, 13, 5), outline=(255, 255, 255, 255))                      # boucle
    d.polygon([(2, 9), (6, 5), (15, 5), (19, 9), (19, h - 1), (2, h - 1)], fill=(255, 255, 255, 255))
    d.line((2, h - 1, 19, h - 1), fill=(170, 170, 170, 255)); d.line((19, 9, 19, h - 1), fill=(190, 190, 190, 255))
    d.line((3, 9, 3, h - 2), fill=(255, 255, 255, 255))
    for y in range(11, h - 3, 4): d.point((17, y), fill=(210, 210, 210, 255))   # trame tissée
    img.save(OUT / "omamori.png")
    lab = Image.new("RGBA", (12, 18), (236, 226, 200, 255)); ld = ImageDraw.Draw(lab)
    ld.rectangle((0, 0, 11, 17), outline=(190, 170, 130, 255))
    ld.fontmode = "1"
    ld.text((6, 9), "守", font=KJ10, fill=(150, 30, 30, 255), anchor="mm")
    lab.save(OUT / "omamori_label.png")


def rbcoin():
    s = 12
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse((0, 0, s - 1, s - 1), fill=(240, 196, 90, 255), outline=(150, 100, 30, 255))
    d.ellipse((2, 2, s - 3, s - 3), fill=(190, 40, 40, 255))
    for (x, y) in ((4, 3), (4, 4), (4, 5), (4, 6), (4, 7), (4, 8), (5, 3), (6, 3), (7, 4), (6, 5), (5, 5), (6, 6), (7, 7), (7, 8)):
        d.point((x, y), fill=(255, 236, 200, 255))
    d.point((3, 3), fill=(255, 240, 200, 255))
    img.save(OUT / "rbcoin.png")


def kamon_plate():
    s = 48
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.ellipse((0, 0, s - 1, s - 1), fill=(150, 130, 100, 255))
    d.ellipse((2, 2, s - 3, s - 3), fill=(236, 226, 204, 255))
    d.ellipse((4, 4, s - 5, s - 5), outline=(214, 200, 172, 255))
    img.save(OUT / "kamon_plate.png")


# ------------------------------------------------------------------ logos vectoriels
def raster_svg(path, size):
    doc = pymupdf.open(path); pg = doc[0]
    z = size * 4 / max(pg.rect.width, pg.rect.height)
    pix = pg.get_pixmap(matrix=pymupdf.Matrix(z, z), alpha=True)
    im = Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)
    big = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    big.alpha_composite(im, ((size * 4 - im.width) // 2, (size * 4 - im.height) // 2))
    return big.resize((size, size), Image.LANCZOS)


def solid(im, rgb):
    a = np.asarray(im)[..., 3]
    out = np.zeros(a.shape + (4,), np.uint8); out[..., :3] = rgb; out[..., 3] = a
    return Image.fromarray(out)


def villages():
    for vid, f in (("konoha", "Konohagakure"), ("suna", "Sunagakure"), ("kiri", "Kirigakure"),
                   ("kumo", "Kumogakure"), ("iwa", "Iwagakure")):
        solid(raster_svg(LOGOS / f"{f}.svg", 48), (255, 255, 255)).save(OUT / f"village_{vid}.png")


def socials():
    solid(raster_svg(LOGOS / "discord.svg", 32), (114, 137, 255)).save(OUT / "social_discord.png")
    solid(raster_svg(LOGOS / "x.svg", 32), (240, 236, 228)).save(OUT / "social_x.png")
    yt = raster_svg(LOGOS / "youtube.svg", 32)
    a = np.asarray(yt)[..., 3].astype(np.float32)
    body = np.zeros((32, 32), np.float32)
    ys, xs = np.nonzero(a > 40)
    body[ys.min():ys.max() + 1, xs.min():xs.max() + 1] = 1          # fond du logo (trou du triangle inclus)
    rounded = body
    out = np.zeros((32, 32, 4), np.uint8)
    inner = np.zeros_like(body); inner[ys.min() + 6:ys.max() - 5, xs.min() + 8:xs.max() - 7] = 1
    hole = (inner > 0.5) & (a < 128)
    out[..., 0], out[..., 1], out[..., 2] = 232, 40, 40
    out[..., 3] = a.astype(np.uint8)
    out[hole] = (255, 255, 255, 255)
    Image.fromarray(out).save(OUT / "social_youtube.png")
    g = Image.open(GUI / "icons/globe.png").convert("RGBA").resize((32, 32), Image.LANCZOS)
    solid(g, (236, 220, 180)).save(OUT / "social_site.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    room(); window(); fusuma(0); fusuma(1); kamishibai(); kawaraban(); rack(); omamori(); rbcoin(); kamon_plate()
    villages(); socials()
    print("->", OUT)
