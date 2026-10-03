"""Décors de la création de personnage — un par village (mod-hud, textures/gui/creation/bg_<village>.png).

    python gen_creation_backgrounds.py

Pixel art v2 — un par village, chacun à son heure et sa météo (480×270).
Technique : rampes de couleurs + tramage de Bayer (vrai rendu pixel art), relief et nuages modelés
par une lumière directionnelle, perspective atmosphérique, architecture détaillée, cadrage au premier plan.
Konoha = soleil couchant doré · Suna = plein midi · Kiri = matin de brume · Kumo = orage · Iwa = crépuscule"""
import math, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
from PIL import ImageFont

KJ14 = ImageFont.truetype(r"C:\Windows\Fonts\msgothic.ttc", 14)
OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/creation"

W, H = 480, 270
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], np.float32) / 16 - 0.5
YY, XX = np.mgrid[0:H, 0:W]


# ------------------------------------------------------------------ outils
def noise(w, h, scale, seed, octaves=4):
    r = np.random.default_rng(seed); out = np.zeros((h, w), np.float32); amp, tot = 1.0, 0.0
    for o in range(octaves):
        s = max(2, int(scale / 2 ** o))
        g = r.random((h // s + 2, w // s + 2)).astype(np.float32)
        g = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize(((w // s + 2) * s, (h // s + 2) * s), Image.BICUBIC), np.float32)[:h, :w] / 255
        out += g * amp; tot += amp; amp *= 0.5
    return out / tot


def ramp_map(v, ramp, dither=1.0):
    """v ∈ [0,1] → couleur d'une rampe, tramage de Bayer entre deux teintes voisines."""
    ramp = np.array(ramp, np.float32); n = len(ramp)
    h, w = v.shape
    t = np.clip(v, 0, 1) * (n - 1) + BAYER[np.arange(h)[:, None] % 4, np.arange(w)[None, :] % 4] * dither
    i = np.clip(np.round(t), 0, n - 1).astype(int)
    return ramp[i]


def lerp(a, b, t): return tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))


class Canvas:
    def __init__(self):
        self.c = np.zeros((H, W, 3), np.float32)

    def paint(self, mask, colors):
        m = mask.astype(bool)
        self.c[m] = colors[m] if colors.ndim == 3 else colors

    def img(self):
        return Image.fromarray(self.c.clip(0, 255).astype(np.uint8)).convert("RGBA")

    def from_img(self, im):
        self.c = np.asarray(im.convert("RGB"), np.float32).copy()


def sky(cv, stops, sun=None, sun_col=(255, 240, 200), glow=60):
    """Dégradé multi-arrêts tramé + soleil avec halo."""
    ys = np.linspace(0, 1, H)
    pos = [p for p, _ in stops]; cols = np.array([c for _, c in stops], np.float32)
    rgb = np.stack([np.interp(ys, pos, cols[:, k]) for k in range(3)], -1)[:, None, :].repeat(W, 1)
    if sun:
        sx, sy, sr = sun
        dist = np.hypot(XX - sx, YY - sy)
        halo = np.clip(1 - dist / (sr * glow / 10), 0, 1) ** 2
        rgb = rgb + (np.array(sun_col, np.float32) - rgb) * halo[..., None] * 0.65
    rgb = np.floor(rgb / 6 + BAYER[YY % 4, XX % 4][..., None] + 0.5) * 6
    cv.c = rgb
    if sun:
        disc = np.hypot(XX - sx, YY - sy) < sr
        cv.paint(disc, np.array(sun_col, np.float32))


def clouds(cv, y0, y1, scale, seed, ramp, cover=0.55, light_dir=-1):
    """Nuages modelés : densité lissée, sommets éclairés, bases ombrées, bords nets (pixel art)."""
    n = noise(W, H, scale, seed, 3)
    n = np.asarray(Image.fromarray((n * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2)), np.float32) / 255
    band = np.clip(1 - np.abs((YY - (y0 + y1) / 2) / ((y1 - y0) / 2)), 0, 1) ** 0.7
    dens = (n - (1 - cover)) * band
    mask = dens > 0.03
    above = np.roll(dens, 4, axis=0); side = np.roll(dens, 4 * light_dir, axis=1)
    v = 0.55 + np.clip(dens - above, -0.2, 0.2) * 2.2 + np.clip(dens - side, -0.2, 0.2) * 1.0 + dens * 0.4
    cv.paint(mask, ramp_map(v, ramp, 0.5))


def ridge(cv, base, amp, scale, seed, ramp, sun_x=None, rough=1.0):
    """Ligne de crête douce (somme de sinus, sans pointes régulières) + modelé par la pente."""
    r = np.random.default_rng(seed)
    xs = np.arange(W, dtype=np.float32)
    acc = np.zeros(W, np.float32); tot = 0.0
    for k, (f, wgt) in enumerate(((scale, 1.0), (scale / 2.7, 0.5), (scale / 6.3, 0.22 * rough), (scale / 13.0, 0.08 * rough))):
        acc += np.sin(xs / f * 2 + r.uniform(0, 6.28)) * wgt; tot += wgt
    hline = base - amp * (0.5 + 0.5 * acc / tot)
    mask = YY > hline[None, :]
    slope = np.gradient(hline)[None, :].repeat(H, 0)
    depth = np.clip((YY - hline[None, :]) / 70, 0, 1)
    sgn = 1 if (sun_x or 0) > W / 2 else -1
    v = 0.58 - depth * 0.38 + np.clip(slope * sgn * 0.25, -0.15, 0.15) + (noise(W, H, 10, seed, 3) - 0.5) * 0.3
    cv.paint(mask, ramp_map(v, ramp, 0.7))
    return hline


def jroof(d, x, y, w, h, ramp_roof, eave=5):
    """Toit japonais : pente, rangs de tuiles (lumière/ombre), rives relevées, faîtage."""
    dark, mid, light = ramp_roof
    d.polygon([(x - eave, y + h), (x + w + eave, y + h), (x + w * 0.82, y), (x + w * 0.18, y)], fill=mid)
    for k in range(2, int(h), 3):
        t = k / h
        xl = x + w * 0.18 - (w * 0.18 + eave) * t; xr = x + w * 0.82 + (w * 0.18 + eave) * t
        d.line((xl, y + k, xr, y + k), fill=dark)
        d.line((xl, y + k - 1, xr, y + k - 1), fill=light if k % 6 == 2 else mid)
    d.line((x + w * 0.18, y, x + w * 0.82, y), fill=light, width=2)
    d.line((x - eave, y + h, x - eave - 3, y + h - 3), fill=mid, width=2); d.line((x + w + eave, y + h, x + w + eave + 3, y + h - 3), fill=mid, width=2)
    d.line((x - eave, y + h + 1, x + w + eave, y + h + 1), fill=dark)


def jhouse(d, x, y, w, h, wall, roof, beam, window, lit_side="right", r=None):
    """Maison : mur enduit (face éclairée / ombrée), colombage, fenêtres, auvent."""
    wl, wd = wall
    d.rectangle((x, y, x + w, y + h), fill=wl)
    if lit_side == "right": d.rectangle((x, y, x + w * 0.35, y + h), fill=wd)
    else: d.rectangle((x + w * 0.65, y, x + w, y + h), fill=wd)
    d.rectangle((x, y, x + w, y + 1), fill=beam); d.rectangle((x, y + h - 2, x + w, y + h), fill=beam)
    for bx in range(int(x), int(x + w), max(8, int(w // 3))): d.line((bx, y, bx, y + h), fill=beam)
    r = r or random.Random(1)
    for _ in range(max(1, int(w // 16))):
        wx = r.randint(int(x + 3), int(x + w - 8)); wy = int(y + h * 0.35)
        d.rectangle((wx, wy, wx + 4, wy + 4), fill=window); d.line((wx, wy + 2, wx + 4, wy + 2), fill=beam)
    jroof(d, x - 1, y - max(6, h * 0.6), w + 2, max(6, h * 0.6), roof)


def foliage(d, cx, cy, rad, ramp, seed, light=(-1, -1)):
    """Bouquet de feuillage pixel : amas de disques, éclairage directionnel par disque."""
    r = random.Random(seed)
    blobs = [(cx + r.uniform(-rad, rad), cy + r.uniform(-rad * 0.6, rad * 0.5), r.uniform(rad * 0.35, rad * 0.6)) for _ in range(9)]
    for (bx, by, br) in blobs: d.ellipse((bx - br, by - br, bx + br, by + br), fill=ramp[1])
    for (bx, by, br) in blobs:
        d.ellipse((bx - br * 0.7 + light[0] * br * 0.25, by - br * 0.7 + light[1] * br * 0.25, bx + br * 0.4 + light[0] * br * 0.25, by + br * 0.4 + light[1] * br * 0.25), fill=ramp[2])
    for (bx, by, br) in blobs:
        d.ellipse((bx - br * 0.35 + light[0] * br * 0.4, by - br * 0.35 + light[1] * br * 0.4, bx + light[0] * br * 0.4, by + light[1] * br * 0.4), fill=ramp[3])
    for _ in range(int(rad * 2)):  # feuilles isolées sur le pourtour
        a = r.uniform(0, 6.28); d.point((cx + math.cos(a) * rad * 1.1, cy + math.sin(a) * rad * 0.8), fill=ramp[1])


def rays(img, sx, sy, col, n=7, alpha=26, spread=1.2, length=420, seed=3):
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0)); d = ImageDraw.Draw(lay); r = random.Random(seed)
    for k in range(n):
        a = math.pi / 2 + (k - n / 2) * spread / n + r.uniform(-0.05, 0.05)
        w = r.uniform(0.02, 0.05)
        d.polygon([(sx, sy), (sx + math.cos(a - w) * length, sy + math.sin(a - w) * length), (sx + math.cos(a + w) * length, sy + math.sin(a + w) * length)],
                  fill=col + (alpha,))
    img.alpha_composite(lay.filter(ImageFilter.GaussianBlur(2)))


def fog(img, y0, h, col, alpha, seed, scale=40):
    n = noise(W, h, scale, seed, 3)
    a = np.clip((n - 0.35) * 2.2, 0, 1) * alpha
    t = np.linspace(0, 1, h)[:, None]
    a = a * (0.4 + 0.6 * np.sin(np.pi * t))
    lay = np.zeros((h, W, 4), np.uint8); lay[..., :3] = col; lay[..., 3] = a.clip(0, 255).astype(np.uint8)
    img.alpha_composite(Image.fromarray(lay), (0, y0))


# ============================================================ Konoha — soleil couchant doré
def konoha():
    cv = Canvas()
    sky(cv, [(0, (58, 70, 140)), (0.35, (196, 120, 130)), (0.6, (250, 170, 100)), (0.8, (255, 210, 130)), (1, (255, 220, 150))],
        sun=(380, 150, 14), sun_col=(255, 236, 180), glow=90)
    clouds(cv, 30, 110, 60, 1, [(120, 80, 110), (180, 110, 120), (240, 160, 130), (255, 210, 160), (255, 236, 200)], cover=0.5, light_dir=1)
    ridge(cv, 168, 26, 90, 2, [(110, 90, 120), (140, 104, 128), (176, 120, 128), (210, 140, 130)], sun_x=380, rough=0.6)
    img = cv.img(); rays(img, 380, 150, (255, 220, 160), n=9, alpha=22)
    cv.from_img(img)
    img = cv.img(); d = ImageDraw.Draw(img); r = random.Random(4)
    d.rectangle((0, 196, W, 240), fill=(40, 60, 40))
    for row, (y, rad, ramp) in enumerate(((184, 12, [(46, 64, 50), (66, 88, 58), (94, 112, 66), (170, 150, 90)]),
                                          (200, 15, [(34, 52, 36), (52, 80, 44), (84, 112, 54), (190, 170, 80)]))):
        for x in range(-10, W + 20, 18 - row * 2):
            foliage(d, x + r.uniform(-4, 4), y + r.uniform(-5, 5), rad, ramp, seed=x * 7 + row, light=(1, -1))
    # canopée lointaine
    for x in range(200, W + 20, 16):
        foliage(d, x, 196 + r.uniform(-4, 4), 14, [(30, 48, 34), (48, 76, 44), (78, 110, 56), (150, 150, 70)], seed=x, light=(1, -1))
    # le village : maisons à toits rouges, la tour ronde rouge marquée 火
    for (x, y, w, h) in ((214, 214, 34, 16), (254, 210, 40, 18), (300, 216, 30, 14), (446, 214, 34, 16)):
        jhouse(d, x, y, w, h, ((236, 214, 180), (196, 168, 140)), ((110, 34, 30), (150, 50, 40), (200, 90, 60)), (90, 60, 40), (255, 200, 110), lit_side="left", r=r)
    tx, ty = 362, 160
    d.ellipse((tx - 36, 216, tx + 36, 236), fill=(120, 40, 32))
    d.rectangle((tx - 34, ty + 14, tx + 34, 226), fill=(186, 60, 44)); d.rectangle((tx + 6, ty + 14, tx + 34, 226), fill=(150, 44, 36))
    d.ellipse((tx - 34, ty + 6, tx + 34, ty + 24), fill=(206, 76, 54))
    for k in range(5): d.rectangle((tx - 28 + k * 12, ty + 30, tx - 24 + k * 12, ty + 36), fill=(255, 210, 120))
    d.ellipse((tx - 12, ty + 40, tx + 12, ty + 62), fill=(236, 214, 180), outline=(120, 40, 32))
    d.text((tx, ty + 51), "火", font=KJ14, fill=(186, 40, 30), anchor="mm")
    jroof(d, tx - 24, ty - 8, 48, 14, ((90, 30, 26), (130, 44, 36), (190, 80, 56)))
    # premier plan : branche qui cadre en haut à droite + feuilles qui tombent
    d.line((W, 6, 400, 24), fill=(56, 36, 28), width=5); d.line((440, 16, 420, 44), fill=(56, 36, 28), width=3)
    for (x, y) in ((420, 20), (448, 10), (470, 30), (404, 34), (432, 46)):
        foliage(d, x, y, 13, [(30, 44, 26), (54, 80, 40), (96, 124, 52), (200, 190, 90)], seed=x + y, light=(-1, 1))
    for _ in range(14):
        x, y = r.uniform(240, W), r.uniform(60, 230); d.polygon([(x, y), (x + 3, y + 1), (x + 1, y + 3)], fill=r.choice([(200, 160, 60), (110, 150, 60), (220, 120, 50)]))
    d.rectangle((0, 236, W, H), fill=(60, 80, 46))
    g = noise(W, H - 236, 6, 5) ; gm = np.zeros((H - 236, W, 4), np.uint8); gm[..., :3] = ramp_map(g, [(48, 66, 38), (66, 92, 48), (96, 120, 58), (150, 150, 70)]); gm[..., 3] = 255
    img.alpha_composite(Image.fromarray(gm), (0, 236))
    return img


# ============================================================ Suna — plein midi
def suna():
    cv = Canvas()
    sky(cv, [(0, (60, 130, 210)), (0.5, (140, 190, 230)), (0.85, (230, 220, 190)), (1, (240, 220, 180))], sun=(400, 26, 11), sun_col=(255, 252, 230), glow=80)
    clouds(cv, 20, 60, 50, 7, [(170, 190, 210), (200, 214, 226), (230, 236, 240), (250, 250, 252)], cover=0.35)
    ridge(cv, 150, 20, 120, 8, [(170, 130, 100), (196, 152, 112), (220, 180, 132), (236, 204, 156)], sun_x=400, rough=0.3)
    # dunes : longues courbes, crêtes éclairées, creux ombrés
    for k, (base, amp, f, ramp) in enumerate(((190, 22, 70, [(160, 110, 70), (196, 140, 88), (228, 176, 112), (246, 210, 150)]),
                                               (226, 18, 50, [(150, 100, 62), (190, 132, 80), (224, 168, 104), (244, 204, 140)]))):
        xs = np.arange(W); hl = base - amp * np.sin(xs / f + k * 2) ** 2 - 6 * np.sin(xs / 17 + k)
        mask = YY > hl[None, :]
        slope = np.gradient(hl)[None, :].repeat(H, 0)
        v = 0.6 - slope * 1.6 - np.clip((YY - hl[None, :]) / 50, 0, 1) * 0.3 + noise(W, H, 10, 20 + k) * 0.15
        cv.paint(mask, ramp_map(v, ramp))
    img = cv.img(); d = ImageDraw.Draw(img); r = random.Random(9)
    # rempart rocheux de la cité (Suna est cernée de falaises)
    rock = [(150, 100, 74), (184, 128, 92), (214, 160, 112), (236, 196, 140)]
    for (x0, x1, top) in ((236, 262, 120), (452, W + 4, 104)):
        pts = [(x0, 236), (x0 + 4, top + 10), (x0 + 12, top), (x1 - 8, top + 4), (x1, 236)]
        d.polygon(pts, fill=rock[1]); d.polygon([(x0, 236), (x0 + 4, top + 10), (x0 + 12, top), (x0 + 16, 236)], fill=rock[0])
        for y in range(top + 8, 236, 7): d.line((x0 + 4, y, x1 - 2, y + r.randint(-1, 1)), fill=rock[0])
        d.line((x0 + 12, top, x1 - 8, top + 4), fill=rock[3])
    # maisons cylindriques à coupole (ombre portée nette, soleil au zénith)
    sand = [(150, 104, 70), (196, 146, 98), (226, 184, 130), (246, 220, 170)]
    for (x, y, w, h) in ((266, 200, 30, 30), (300, 190, 40, 40), (344, 204, 26, 26), (436, 196, 22, 34), (290, 220, 50, 16)):
        d.rectangle((x, y, x + w, y + h), fill=sand[2]); d.rectangle((x + w * 0.7, y, x + w, y + h), fill=sand[1])
        d.pieslice((x - 1, y - w * 0.42, x + w + 1, y + w * 0.42), 180, 360, fill=sand[3])
        d.pieslice((x + w * 0.55, y - w * 0.42, x + w + 1, y + w * 0.42), 270, 360, fill=sand[2])
        for k in range(max(1, w // 13)):
            wx = x + 5 + k * 12; d.ellipse((wx, y + h * 0.35, wx + 5, y + h * 0.35 + 5), fill=(70, 44, 30))
        d.rectangle((x, y + h, x + w + 6, y + h + 2), fill=sand[0])   # ombre au sol
    # le grand dôme (風), tuyaux d'aération
    cx = 396
    d.rectangle((cx - 30, 150, cx + 30, 226), fill=sand[2]); d.rectangle((cx + 10, 150, cx + 30, 226), fill=sand[1])
    d.pieslice((cx - 32, 122, cx + 32, 178), 180, 360, fill=sand[3]); d.pieslice((cx, 122, cx + 32, 178), 270, 360, fill=sand[2])
    d.ellipse((cx - 11, 160, cx + 11, 182), fill=(110, 60, 40), outline=sand[0]); d.text((cx, 171), "風", font=KJ14, fill=(250, 220, 160), anchor="mm")
    for k in range(4): d.rectangle((cx - 26 + k * 16, 196, cx - 22 + k * 16, 204), fill=(70, 44, 30))
    for px in (cx - 20, cx + 18): d.rectangle((px, 128, px + 3, 140), fill=sand[0])
    # sable soulevé par le vent
    for _ in range(40):
        x, y, l = r.uniform(0, W), r.uniform(180, H), r.uniform(10, 34); d.line((x, y, x + l, y - 2), fill=(246, 222, 176))
    return img


# ============================================================ Kiri — matin de brume
def kiri():
    cv = Canvas()
    sky(cv, [(0, (120, 140, 160)), (0.5, (170, 186, 196)), (1, (200, 210, 214))], sun=(370, 70, 12), sun_col=(240, 240, 232), glow=70)
    ridge(cv, 150, 30, 100, 11, [(120, 136, 150), (136, 150, 162), (150, 164, 174)], rough=0.5)
    img = cv.img(); fog(img, 110, 80, (210, 220, 226), 200, 12)
    cv.from_img(img)
    # lac
    lake = np.zeros((H, W, 3), np.float32)
    lv = 0.45 + noise(W, H, 8, 13) * 0.25 + (YY - 205) / 140
    cv.paint(YY > 205, ramp_map(lv, [(70, 90, 104), (96, 116, 128), (130, 148, 158), (170, 184, 190)]))
    img = cv.img(); d = ImageDraw.Draw(img); r = random.Random(14)
    # tours cylindriques (de la plus lointaine/pâle à la plus proche/contrastée)
    towers = [(250, 120, 18, 0.35), (284, 96, 24, 0.5), (446, 110, 22, 0.55), (318, 120, 22, 0.65), (408, 86, 30, 0.85), (352, 70, 34, 1.0)]
    for (x, top, w, k) in towers:
        fogc = (190, 202, 208)
        light, dark = lerp(fogc, (150, 170, 180), k), lerp(fogc, (80, 96, 108), k)
        d.rectangle((x, top, x + w, 206), fill=light); d.rectangle((x + w * 0.6, top, x + w, 206), fill=dark)
        d.ellipse((x - 3, top - 7, x + w + 3, top + 5), fill=lerp(fogc, (110, 130, 140), k))
        d.ellipse((x - 3, top - 9, x + w + 3, top + 1), fill=lerp(fogc, (176, 192, 198), k))
        for yy in range(top + 8, 200, 12):
            d.line((x, yy, x + w, yy), fill=dark)
            for wx in range(int(x) + 3, int(x + w) - 4, 8):
                d.rectangle((wx, yy + 3, wx + 3, yy + 6), fill=lerp(fogc, (255, 236, 180) if r.random() < 0.4 else (60, 70, 80), k))
        d.line((x + w * 0.3, top - 9, x + w * 0.3, top - 18), fill=dark); d.line((x + w * 0.3, top - 18, x + w * 0.3 + 6, top - 18), fill=dark)
        for px in (x + 2, x + w - 2): d.line((px, 206, px, 214), fill=dark)
    # passerelles suspendues
    for (a, b, y) in ((308, 318, 150), (342, 352, 140), (386, 408, 134)): d.line((a, y, b, y + 2), fill=(70, 86, 96), width=2)
    # reflets + brume au ras de l'eau
    refl = img.crop((240, 110, 480, 206)).transpose(Image.FLIP_TOP_BOTTOM)
    a = np.asarray(refl).copy(); a[..., 3] = (a[..., 3] * 0.28).astype(np.uint8)
    img.alpha_composite(Image.fromarray(a), (240, 208))
    fog(img, 160, 70, (220, 228, 232), 210, 15); fog(img, 196, 40, (230, 236, 238), 230, 16, 30)
    # roseaux au premier plan à droite
    d = ImageDraw.Draw(img)
    for k in range(18):
        x = 440 + k * 2.4; top = 200 + r.randint(0, 30)
        d.line((x, H, x + r.uniform(-4, 4), top), fill=(60, 74, 66)); d.ellipse((x - 1, top - 4, x + 2, top + 2), fill=(70, 60, 50))
    return img


# ============================================================ Kumo — orage
def kumo():
    cv = Canvas()
    sky(cv, [(0, (30, 32, 48)), (0.5, (70, 72, 96)), (1, (110, 112, 136))])
    clouds(cv, 0, 130, 70, 21, [(26, 26, 40), (44, 44, 62), (70, 70, 92), (110, 110, 136), (170, 170, 196)], cover=0.75, light_dir=1)
    img = cv.img()
    rays(img, 300, 40, (230, 230, 255), n=5, alpha=30, spread=0.8, seed=5)
    d = ImageDraw.Draw(img); r = random.Random(22)
    # éclair ramifié avec halo
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(lay)
    def bolt(x, y, depth, length):
        for _ in range(length):
            nx, ny = x + r.randint(-6, 6), y + r.randint(6, 10)
            ld.line((x, y, nx, ny), fill=(255, 255, 255, 255), width=2 if depth == 0 else 1); x, y = nx, ny
            if depth < 2 and r.random() < 0.18: bolt(x, y, depth + 1, length // 2)
    bolt(250, 60, 0, 12)
    glowl = lay.filter(ImageFilter.GaussianBlur(4))
    img.alpha_composite(glowl); img.alpha_composite(glowl); img.alpha_composite(lay)
    # pitons de roche clairs avec bâtiments blancs (style Kumo)
    rock = [(60, 62, 80), (90, 92, 110), (130, 132, 150), (180, 182, 196)]
    for (cx, top, w) in ((300, 120, 40), (352, 84, 54), (418, 104, 46), (466, 128, 30)):
        pts = [(cx - w / 2, H), (cx - w / 2 + 5, top + 26), (cx - w / 4, top), (cx + w / 4, top), (cx + w / 2 - 5, top + 26), (cx + w / 2, H)]
        d.polygon(pts, fill=rock[1]); d.polygon([(cx, top), (cx + w / 4, top), (cx + w / 2 - 5, top + 26), (cx + w / 2, H), (cx + 4, H)], fill=rock[0])
        d.line((cx - w / 4, top, cx - w / 2 + 5, top + 26), fill=rock[3])
        for y in range(int(top) + 10, H, 9): d.line((cx - w / 2 + 6, y, cx + w / 2 - 6, y + r.randint(-2, 2)), fill=rock[0])
        bw = w * 0.74
        d.rectangle((cx - bw / 2, top - 16, cx + bw / 2, top), fill=(206, 208, 220)); d.rectangle((cx + bw * 0.1, top - 16, cx + bw / 2, top), fill=(160, 162, 180))
        d.pieslice((cx - bw / 2 - 2, top - 26, cx + bw / 2 + 2, top - 6), 180, 360, fill=(226, 228, 238))
        for k in range(int(bw // 10)): d.rectangle((cx - bw / 2 + 4 + k * 10, top - 11, cx - bw / 2 + 7 + k * 10, top - 6), fill=(255, 220, 130))
    d.rectangle((330, 52, 374, 84), fill=(214, 216, 228)); d.pieslice((326, 38, 378, 66), 180, 360, fill=(236, 238, 246))
    d.text((352, 72), "雷", font=KJ14, fill=(60, 70, 120), anchor="mm")
    for (a, b, y) in ((318, 326, 112), (378, 396, 100)): d.line((a, y, b, y - 8), fill=(50, 50, 66))
    # mer de nuages + pluie fine
    fog(img, 180, 90, (170, 172, 196), 230, 23, 30); fog(img, 214, 56, (200, 202, 222), 240, 24, 24)
    d = ImageDraw.Draw(img)
    for _ in range(160):
        x, y = r.uniform(0, W), r.uniform(0, 200); d.line((x, y, x - 2, y + 7), fill=(140, 146, 180))
    return img


# ============================================================ Iwa — crépuscule
def iwa():
    cv = Canvas()
    sky(cv, [(0, (40, 30, 70)), (0.35, (110, 60, 96)), (0.6, (220, 110, 80)), (0.78, (250, 170, 100)), (1, (250, 190, 120))],
        sun=(430, 200, 13), sun_col=(255, 200, 120), glow=70)
    r0 = np.random.default_rng(31)
    for _ in range(40):
        x, y = r0.integers(0, W), r0.integers(0, 70); cv.c[y, x] = (255, 240, 220)
    clouds(cv, 60, 120, 60, 32, [(90, 50, 80), (150, 70, 90), (220, 120, 90), (250, 180, 120)], cover=0.42, light_dir=1)
    ridge(cv, 176, 30, 70, 33, [(80, 46, 60), (110, 60, 66), (150, 80, 70), (190, 110, 80)], sun_x=430, rough=0.4)
    img = cv.img(); d = ImageDraw.Draw(img); r = random.Random(34)
    rock = [(70, 40, 40), (104, 60, 50), (140, 84, 62), (196, 124, 80)]
    # mesas en terrasses, tranches éclairées côté soleil
    pts = [(230, H), (236, 176), (262, 168), (270, 128), (300, 122), (310, 156), (338, 150), (350, 100), (394, 94), (404, 140), (428, 134), (438, 112), (480, 106), (480, H)]
    d.polygon(pts, fill=rock[1])
    st = Image.new("RGBA", (W, H), (0, 0, 0, 0)); sd = ImageDraw.Draw(st)
    for y in range(96, H, 8):
        sd.line((230, y, 480, y + r.randint(-2, 2)), fill=rock[0] + (255,))
        sd.line((230, y + 1, 480, y + 1), fill=rock[2] + (90,))
    m = Image.new("L", (W, H), 0); ImageDraw.Draw(m).polygon(pts, fill=255)
    st.putalpha(Image.fromarray(np.minimum(np.asarray(st.split()[3]), np.asarray(m)))); img.alpha_composite(st); d = ImageDraw.Draw(img)
    for i in range(1, len(pts) - 2):  # arêtes éclairées par le couchant
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        if y1 <= y0 + 2: d.line((x0, y0, x1, y1), fill=rock[3], width=2)
    # tours et ponts de pierre, fenêtres chaudes
    for (x, top, w) in ((276, 92, 22), (364, 62, 30), (446, 78, 24)):
        d.rectangle((x, top, x + w, 200), fill=rock[2]); d.rectangle((x + w * 0.6, top, x + w, 200), fill=rock[1])
        d.polygon([(x - 4, top), (x + w / 2, top - 14), (x + w + 4, top)], fill=rock[0]); d.line((x + w / 2, top - 14, x + w + 4, top), fill=rock[3])
        for k in range(top + 8, 196, 11): d.rectangle((x + w / 2 - 2, k, x + w / 2 + 1, k + 5), fill=(255, 190, 100))
    d.rectangle((356, 46, 402, 62), fill=rock[2]); d.text((379, 54), "土", font=KJ14, fill=(255, 210, 140), anchor="mm")
    for (x0, y0, x1) in ((298, 130, 364), (394, 118, 446)):
        d.arc((x0, y0 - 14, x1, y0 + 14), 180, 360, fill=rock[3], width=3); d.line((x0, y0, x1, y0), fill=rock[0], width=2)
    for _ in range(30):
        x, y = r.randint(240, 470), r.randint(150, 240); d.rectangle((x, y, x + 2, y + 3), fill=(255, 170, 80))
    d.rectangle((0, 244, W, H), fill=(54, 32, 36))
    fog(img, 220, 40, (230, 150, 110), 70, 35)
    return img


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (("konoha", konoha), ("suna", suna), ("kiri", kiri), ("kumo", kumo), ("iwa", iwa)):
        fn().save(OUT / ("bg_%s.png" % name))
    print("->", OUT)
