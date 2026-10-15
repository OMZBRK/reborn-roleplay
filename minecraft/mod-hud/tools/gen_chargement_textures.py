# -*- coding: utf-8 -*-
"""
gen_chargement_textures.py — scène de chargement « nuit de pleine lune » (trajet Rejoindre → arrivée en jeu).

Ciel bleu de nuit, immense pleine lune, cumulus éclairés par la lune (cuits dans le ciel), voiles et brume qui
dérivent, village aux fenêtres chaudes et pagode, arbres et lisière de cimes au premier plan, corbeaux et feuilles. Notre propre scène (aucune image reprise de la vidéo de référence), en pixel art
à aplats propres, sans aucun caractère décoratif (DA §3). Ce qui ne bouge pas est une texture ; le code ne fait que
déplacer des couches et poser quelques sprites (DA §6). 8 textures, ~40 Ko sur disque, ~2,2 Mo de VRAM.

Espace : la scène fait 480×270 unités ; 1 texel = 1 unité ; le code l'affiche à k pixels écran par unité (k = 4 en
1080p, 3 en 1366×768, 5 en 1440p, 8 en 4K) et la prolonge au-delà (ciel, sol) pour remplir n'importe quel format.

Usage (depuis minecraft/mod-hud) :  python tools/gen_chargement_textures.py [--apercu dossier]

Contrat (coordonnées = unités de la scène ; reprises dans menu/connect/ChargementArt.java) :
- ciel.png 704×300 : ciel, halo de la lune, étoiles et cumulus fixes ; posé en (-112, -30). Au-dessus : aplat CIEL_HAUT.
- lune.png 208×208 : disque r = 100 centré au texel (104, 104) ; posé en (136, 14) (centre de la lune (240, 118)).
- nuages_voile.png 960×84 : voiles minces devant la lune (alpha en 3 paliers, se répète en x) ; y = 36, 4,5 u/s.
- village.png 704×100 : collines boisées, toits, pagode, fenêtres ; posé en (-112, 170).
- nuages_bas.png 960×72 : brume basse devant le village (se répète) ; posée en y = 198, 7 u/s.
- avant.png 704×152 : massifs d'arbres aux bords et lisière de cimes en bas (texte et barre s'y posent) ; posé en
  (-112, 118). Sous y = 270 : aplat SOL.
- corbeau.png 4 images 12×7 (atlas 48×7) : corbeau de profil vers la droite, battement (haut, mi-haut, plat, bas).
- feuille.png 4 images 5×5 (atlas 20×5) : rotation d'une feuille.
"""
from __future__ import annotations

import argparse
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "style"))
from kit import ASSETS, bayer, bruit, hexc  # noqa: E402

OUT = os.path.join(ASSETS, "textures", "gui", "chargement")

VW, VH = 480, 270
EXT_X, EXT_Y = 112, 30          # prolongement du ciel et du premier plan autour de la scène
LUNE_C = (240, 118)
LUNE_R = 100


def rgba(c, a=255):
    r, g, b, _ = hexc(c)
    return np.array([r, g, b, a], np.uint8)


def sauver(arr, nom):
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(os.path.join(OUT, nom + ".png"), optimize=True)


def quant(v, n, x0=0, y0=0, tramage=0.45):
    """0..1 -> indices 0..n-1, transitions tramées étroites (Bayer 4x4 aligné sur la scène)."""
    v = np.clip(v, 0, 1) * (n - 1)
    b = 0.5 + (bayer(v.shape[0], v.shape[1], x0, y0) - 0.5) * tramage
    return np.clip(np.floor(v + b), 0, n - 1).astype(int)


# ═══════════════════════════════════════════════════════════════ ciel
CIEL = ["03050D", "050918", "080E22", "0B132C", "0F1937", "132042", "18284F", "1E305C", "26396A", "2F4479",
        "3A5089", "475F9A", "566FAA"]
CIEL_HAUT = CIEL[0]


def ciel():
    W, H = VW + 2 * EXT_X, VH + EXT_Y
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    sy = ys - EXT_Y                                   # y scène
    sx = xs - EXT_X
    # dégradé vertical : nuit profonde en haut, plus claire vers l'horizon
    t = np.clip((sy + EXT_Y) / (H), 0, 1)
    v = 0.04 + 0.40 * t ** 1.35
    # halo de la lune : fort contre le disque, s'éteint vers r = 200
    d = np.hypot(sx - LUNE_C[0], sy - LUNE_C[1])
    g = np.clip(1 - (d - LUNE_R) / 105.0, 0, 1)
    v = v + 0.62 * g ** 2.2 + 0.10 * g
    idx = quant(v, len(CIEL), EXT_X, EXT_Y, tramage=0.5)
    pal = np.array([hexc(c) for c in CIEL], np.uint8)
    img = pal[idx]
    # étoiles fixes, loin du halo
    rs = np.random.RandomState(7)
    for _ in range(150):
        x, y = rs.randint(0, W), rs.randint(0, 175)
        if v[y, x] > 0.30 or d[y, x] < LUNE_R + 40:
            continue
        b = rs.rand()
        c = "C9D3EA" if b > 0.85 else ("8C9CC4" if b > 0.45 else "56658F")
        img[y, x] = hexc(c)
        if b > 0.96 and 0 < x < W - 1 and 0 < y < H - 1:      # rares étoiles en croix
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[y + dy, x + dx] = hexc("56658F")
    return nuages_lunaires(img)


# ═══════════════════════════════════════════════════════════════ lune
LUNE = ["A9B3CA", "BCC5D7", "C9D1E0", "D6DCE8", "E2E7EF", "ECEFF5", "F7F9FC"]


def lune():
    S = 208
    c = 104
    ys, xs = np.mgrid[0:S, 0:S].astype(float)
    dx, dy = xs + 0.5 - c, ys + 0.5 - c
    r = np.hypot(dx, dy)
    disque = r <= LUNE_R + 0.5
    n = r / LUNE_R
    # base claire, assombrissement du limbe en deux paliers
    v = np.full((S, S), 5.0)
    v[n > 0.84] = 4
    v[n > 0.96] = 3
    # mers : bruit seuillé, pondéré pour tomber où sont les vraies (haut gauche, droite, ouest)
    b1 = bruit(S, S, cellule=26, seed=11, octaves=3)
    b2 = bruit(S, S, cellule=9, seed=12, octaves=2)
    poids = np.zeros((S, S))
    for (mx, my, rx, ry, w) in [(-30, -26, 40, 30, 1.0), (14, -38, 26, 22, 0.8), (36, -2, 26, 34, 0.9),
                                (-56, 18, 30, 42, 0.9), (-4, 26, 30, 16, 0.6)]:
        e = ((dx - mx) / rx) ** 2 + ((dy - my) / ry) ** 2
        poids = np.maximum(poids, w * np.clip(1.1 - e, 0, 1))
    m = poids * (0.55 + 0.45 * b1) + (b2 - 0.5) * 0.18
    v[(m > 0.36) & disque] -= 1
    v[(m > 0.62) & disque] -= 1
    # quelques cratères discrets
    for (cx, cy, cr) in [(18, 64, 4), (-42, -60, 3), (54, 34, 3), (-66, -14, 3), (44, -58, 2), (70, 8, 2)]:
        rr = np.hypot(dx - cx, dy - cy)
        v[(rr <= cr) & disque] -= 1
        v[(rr > cr) & (rr <= cr + 1) & (dy - cy > 0) & disque] += 1
    rr = np.hypot(dx - 18, dy - 64)
    v[(rr <= 1.6) & disque] = 6
    v = np.clip(v, 0, len(LUNE) - 1).astype(int)
    pal = np.array([hexc(c) for c in LUNE], np.uint8)
    img = pal[v]
    img[~disque] = 0
    return img


# ═══════════════════════════════════════════════════════════════ nuages
def bouffees(W, H, specs, rampe, fond=None, aplati=0.62, seed=0, irregulier=0.9):
    """Banc de nuages fait de bouffées dessinées de l'arrière (hautes) vers l'avant (basses). Chaque bouffée a un bord
    supérieur éclairé fin (1 à 2 unités, puis 2 à 3 de demi-teinte) et un corps sombre : les recouvrements dessinent
    les festons. Bords irréguliers (bruit). Se répète en x (W). specs = [(cx, cy, r)]."""
    img = np.zeros((H, W, 4), np.uint8)
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    pal = [rgba(c) for c in rampe]
    bn = bruit(W, H, cellule=5, seed=seed + 100, octaves=2)
    bn = (bn + np.roll(bn, W // 2, axis=1)) / 2       # raccord du bruit au bord de la répétition (à peu près)
    for (cx, cy, r) in sorted(specs, key=lambda s: s[1] - s[2] * aplati):
        dx = (xs + 0.5 - cx + W / 2) % W - W / 2
        ry = r * (aplati if r < 20 else aplati * 0.78)
        e = (dx / r) ** 2 + ((ys + 0.5 - cy) / ry) ** 2 + (bn - 0.5) * irregulier * 0.5
        dedans = e <= 1
        # profondeur sous le bord haut de la bouffée
        haut = cy - ry * np.sqrt(np.clip(1 - (dx / r) ** 2, 0, 1))
        p = ys + 0.5 - haut
        ton = np.zeros((H, W), int)
        cote = np.abs(dx / r)
        ton[p < 6] = 1
        ton[(p < 3) & (cote < 0.8)] = 2
        ton[(p < 1.2) & (cote < 0.6)] = 3
        for i in range(4):
            img[dedans & (ton == i)] = pal[i]
    if fond is not None and fond is not False:
        img[int(fond):, :] = pal[0]
    return img


def voiles(W, H, n, seed, rx_, ry_, corps, liseré, paliers):
    rs = np.random.RandomState(seed)
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    acc = np.zeros((H, W))
    for _ in range(n):
        cx, cy = rs.randint(0, W), rs.uniform(ry_[1] + 2, H - ry_[1] - 2)
        rx, ry = rs.uniform(*rx_), rs.uniform(*ry_)
        for (fx, fy, fs) in ((0, 0, 1.0), (rs.choice([-0.65, 0.65]), 0.8, 0.55), (rs.uniform(-0.4, 0.4), -0.6, 0.45)):
            dx = (xs + 0.5 - (cx + fx * rx) + W / 2) % W - W / 2
            e = (dx / (rx * fs)) ** 2 + ((ys + 0.5 - (cy + fy * ry)) / (ry * fs)) ** 2
            # fuseau : effilé aux deux bouts
            acc = np.maximum(acc, np.clip(1 - e, 0, 1) * (1.0 if fs == 1.0 else 0.85))
    a = np.zeros((H, W, 4), np.uint8)
    plein = acc > 0.04
    a[plein] = rgba(corps)
    for seuil, al in paliers:
        a[acc > seuil, 3] = al
    if liseré:
        haut = plein & ~np.roll(plein, 1, axis=0)
        a[haut] = rgba(liseré[0], liseré[1])
    return a


def nuages_voile():
    """Voiles minces qui passent devant la lune : fuseaux sombres semi-transparents (3 paliers), liseré clair."""
    return voiles(960, 84, 11, 9, (34, 90), (3.0, 6.5), "26345E", ("8090BA", 140),
                  [(0.04, 84), (0.30, 128), (0.62, 166)])


def nuages_bas():
    """Brume basse devant la forêt : longues nappes sombres (2 paliers d'alpha)."""
    return voiles(960, 72, 12, 5, (60, 140), (5, 11), "0B1331", ("18244C", 170),
                  [(0.04, 120), (0.40, 190)])


NUAGE_LUNE = ["0E1838", "142044", "1B2A52", "243663", "304678", "40598F", "5873A8", "7590C2"]


def nuages_lunaires(img_ciel):
    """Grands cumulus fixes de part et d'autre de la lune, éclairés par elle (côté tourné vers la lune plus clair,
    liseré argenté) et éteints vers les bords. Cuits dans le ciel : ils ne coûtent rien à l'image."""
    W, H = img_ciel.shape[1], img_ciel.shape[0]
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    sx, sy = xs - EXT_X + 0.5, ys - EXT_Y + 0.5
    rs = np.random.RandomState(52)
    bn = bruit(W, H, cellule=5, seed=53, octaves=2)
    specs = []
    for cote in (-1, 1):
        # masse principale, montant vers la lune ; bouffées plus petites sur le dessus
        for i in range(16):
            u = i / 15
            cx = 240 + cote * (60 + u * 300) + rs.uniform(-10, 10)
            cy = 176 - 34 * (1 - u) ** 1.3 + rs.uniform(-6, 8) + 14 * u
            r = rs.uniform(18, 30) * (1.15 - 0.4 * u)
            specs.append((cx, cy, r))
            for _ in range(2):
                a = rs.uniform(-0.7, 0.7)
                specs.append((cx + a * r, cy - r * 0.55 * math.sqrt(1 - a * a) + rs.uniform(1, 4), r * rs.uniform(0.35, 0.55)))
        for i in range(10):
            specs.append((240 + cote * rs.uniform(90, 380), 206 + rs.uniform(-6, 10), rs.uniform(24, 36)))
    out = img_ciel.copy()
    pal = np.array([hexc(c) for c in NUAGE_LUNE], np.uint8)
    for (cx, cy, r) in sorted(specs, key=lambda s: s[1] - s[2] * 0.6):
        ry = r * 0.66
        dx, dy = sx - cx, sy - cy
        e = (dx / r) ** 2 + (dy / ry) ** 2 + (bn - 0.5) * 0.45
        dedans = e <= 1
        if not dedans.any():
            continue
        # normale de la bouffée et direction de la lune
        nx, ny = dx / r, dy / ry
        lx, ly = LUNE_C[0] - cx, LUNE_C[1] - cy
        ln = math.hypot(lx, ly) or 1
        lx, ly = lx / ln, ly / ln
        eclair = np.clip(nx * lx * 0.8 + ny * ly * 0.6 - ny * 0.45, -1, 1)          # un peu de lumière zénithale
        proche = np.clip(1 - (np.hypot(sx - LUNE_C[0], sy - LUNE_C[1]) - LUNE_R) / 230, 0, 1)
        v = 1.2 + proche * 2.6 + eclair * (1.2 + 2.2 * proche)
        bord = (e > 0.78) & (eclair > 0.35)
        v = np.where(bord, v + 1.4 * proche + 0.4, v)
        b = 0.5 + (bayer(H, W) - 0.5) * 0.5
        idx = np.clip(np.floor(v + b), 0, len(pal) - 1).astype(int)
        out[dedans] = pal[idx[dedans]]
    return out


# ═══════════════════════════════════════════════════════════════ village et premier plan
FORET = ["070C1E", "0C1430", "131D42"]
VILLAGE = {"mur": "0B132C", "toit": "0F1838", "arete": "2A3B6C", "fen": "B9773A", "fen2": "E5AE5E", "fen3": "6E4426"}
AVANT = ["020409", "04070E", "0B1328", "16234A"]


def silhouette_arbres(W, H, rs, n, hmin, hmax, base, pins=0.5, larg=(10, 26)):
    """Masque d'une ligne d'arbres : sapins (triangles en étages) et feuillus (bouffées bruitées)."""
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    m = np.zeros((H, W), bool)
    m[ys >= base] = True
    bn = bruit(W, H, cellule=3, seed=rs.randint(0, 999), octaves=2)
    for _ in range(n):
        cx = rs.uniform(0, W)
        h = rs.uniform(hmin, hmax)
        top = base - h
        if rs.rand() < pins:
            w = rs.uniform(*larg) * 0.6
            etages = 4
            for k in range(etages):
                y0 = top + k * h / (etages + 0.6)
                y1 = y0 + h / etages * 1.25
                ww = w * (0.45 + 0.55 * (k + 1) / etages)
                t = np.clip((ys - y0) / max(1, y1 - y0), 0, 1)
                m |= (ys >= y0) & (ys <= y1) & (np.abs(xs - cx) <= ww * t + 0.6)
        else:
            w = rs.uniform(*larg)
            for _ in range(5):
                bx = cx + rs.uniform(-w * 0.5, w * 0.5)
                by = top + rs.uniform(0, h * 0.5) + w * 0.35
                br = w * rs.uniform(0.35, 0.6)
                e = ((xs - bx) / br) ** 2 + ((ys - by) / (br * 0.85)) ** 2
                m |= e <= 1 + (bn - 0.5) * 0.9
            m |= (np.abs(xs - cx) <= max(1, w * 0.08)) & (ys >= top + h * 0.4)
    return m


def toit(d, x0, x1, y_bas, h, relevé=2, code=1):
    """Toit à pans courbes : trapèze dont les coins d'égout se relèvent (dessin PIL, sans lissage)."""
    w = x1 - x0
    pts = [(x0 - 3, y_bas - relevé), (x0 + w * 0.22, y_bas - h), (x1 - w * 0.22, y_bas - h), (x1 + 3, y_bas - relevé),
           (x1 + 1, y_bas), (x0 - 1, y_bas)]
    d.polygon(pts, fill=code)


def village():
    """Ligne de collines boisées puis le village : toits à pans courbes sur deux rangs, une pagode à gauche, quelques
    fenêtres chaudes. Statique (704×100, posé en (-112, 170))."""
    from PIL import ImageDraw
    W, H = VW + 2 * EXT_X, 100
    oy = 170
    rs = np.random.RandomState(31)
    foret_m = silhouette_arbres(W, H, rs, 70, 8, 24, 40, pins=0.65, larg=(8, 16))
    lay = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(lay)
    fen = []
    for rang, (base, hmin, hmax) in enumerate(((58, 6, 10), (72, 8, 13))):
        x = rs.randint(0, 20)
        while x < W:
            w = rs.randint(14, 30) if rang else rs.randint(10, 22)
            hm = rs.randint(hmin, hmax)
            d.rectangle([x, base - hm, x + w, H], fill=1)
            toit(d, x, x + w, base - hm, rs.randint(5, 8), code=2)
            if rs.rand() < 0.45:
                for _ in range(rs.randint(1, 3)):
                    fx = x + rs.randint(2, max(3, w - 3))
                    fy = base - hm + rs.randint(2, max(3, hm - 3))
                    fen.append((fx, fy, rs.rand()))
            x += w + rs.randint(1, 6)
    # pagode à cinq toits, à gauche de la lune
    px = EXT_X + 92
    y = 70
    for k in range(5):
        larg = 26 - k * 4
        hh = 8
        d.rectangle([px - larg // 2 + 3, y - hh, px + larg // 2 - 3, y], fill=1)
        toit(d, px - larg // 2, px + larg // 2, y - hh + 2, 5, relevé=2, code=2)
        if k in (1, 3):
            fen.append((px - 1, y - 4, 0.9))
        y -= hh
    d.line([(px, y - 8), (px, y + 1)], fill=2, width=1)
    t = np.array(lay)
    img = np.zeros((H, W, 4), np.uint8)
    img[foret_m] = rgba(FORET[0])
    haut = foret_m & ~np.roll(foret_m, 1, axis=0)
    img[haut] = rgba(FORET[1])
    img[t == 1] = rgba(VILLAGE["mur"])
    img[t == 2] = rgba(VILLAGE["toit"])
    plein = t > 0
    arete = (t == 2) & ~np.roll(plein, 1, axis=0)
    img[arete] = rgba(VILLAGE["arete"])
    for (fx, fy, r) in fen:
        if 0 <= fx < W - 1 and 0 <= fy < H - 1 and t[fy, fx] == 1:
            c = VILLAGE["fen2"] if r > 0.8 else VILLAGE["fen"] if r > 0.3 else VILLAGE["fen3"]
            img[fy:fy + 2, fx:fx + 2] = rgba(c)
    return img


def avant():
    """Premier plan : deux massifs d'arbres qui cadrent la scène et une lisière de cimes sombres tout le long du bas
    (plus basse au centre : la lune reste dégagée), sol continu sous laquelle se posent le texte et la barre."""
    W, H = VW + 2 * EXT_X, 152
    oy = 118
    ys, xs = np.mgrid[0:H, 0:W].astype(float)
    sx, sy = xs - EXT_X, ys + oy
    bn = bruit(W, H, cellule=4, seed=77, octaves=3)
    m = sy >= 250

    def massif(cx, base, haut, large, nb, graine, rmin=12, rmax=24):
        r2 = np.random.RandomState(graine)
        mm = np.zeros((H, W), bool)
        for _ in range(nb):
            bx = cx + r2.uniform(-large, large)
            tt = 1 - abs(bx - cx) / large
            by = base - haut * (0.35 + 0.65 * tt) + r2.uniform(-6, 6)
            br = r2.uniform(rmin, rmax)
            e = ((sx - bx) / br) ** 2 + ((sy - by) / (br * 0.8)) ** 2
            mm |= e <= 1 + (bn - 0.5) * 1.1
        return mm

    arbres = massif(-20, 254, 118, 72, 22, 1) | massif(500, 254, 122, 72, 22, 3)
    arbres |= massif(58, 258, 40, 26, 7, 2) | massif(424, 258, 38, 26, 7, 4)
    arbres |= (np.abs(sx - 22) <= 2) & (sy >= 190)
    arbres |= (np.abs(sx - 460) <= 2) & (sy >= 190)
    # lisière de cimes : bouffées le long du bas, plus hautes vers les bords
    r3 = np.random.RandomState(9)
    x = -EXT_X
    while x < VW + EXT_X:
        u = abs(x - 240) / 240
        base = 252 - 10 * min(1, u) ** 1.5
        br = r3.uniform(7, 13)
        by = base - r3.uniform(0, 5)
        e = ((sx - x) / br) ** 2 + ((sy - by) / (br * 0.75)) ** 2
        arbres |= e <= 1 + (bn - 0.5) * 1.0
        x += r3.uniform(6, 12)
    m |= arbres
    img = np.zeros((H, W, 4), np.uint8)
    img[m] = rgba(AVANT[0])
    haut = m & ~np.roll(m, 1, axis=0)
    gauche = m & ~np.roll(m, 1, axis=1)
    droite = m & ~np.roll(m, -1, axis=1)
    vers_lune = (gauche & (sx > 240)) | (droite & (sx < 240))
    proche = np.hypot(sx - LUNE_C[0], sy - LUNE_C[1]) < 230
    img[(haut | vers_lune) & proche] = rgba(AVANT[2])
    img[haut & (np.hypot(sx - LUNE_C[0], sy - LUNE_C[1]) < 160)] = rgba(AVANT[3])
    return img


# liserés de lune (bord des silhouettes devant le halo)
RIM_LUNE = ["BCC8E2", "6F80AE"]


# ═══════════════════════════════════════════════════════════════ corbeaux, feuilles
CB_W, CB_H, CB_N = 12, 7, 4
CORBEAU = [
    ["....#.......",
     "....##......",
     ".....##.....",
     "#....###.##.",
     ".###########",
     "..######....",
     "............"],
    ["............",
     "..###.......",
     "...####.....",
     "#...####.##.",
     ".###########",
     "..######....",
     "............"],
    ["............",
     "............",
     "............",
     "#.######.##.",
     ".###########",
     "..######....",
     "............"],
    ["............",
     "............",
     "............",
     "#........##.",
     ".###########",
     "..#######...",
     "....####...."],
]


FEUILLE = [
    ["..##.", ".###.", "###..", ".#...", "....."],
    [".....", ".###.", "#####", ".###.", "....."],
    ["..#..", ".###.", ".###.", ".##..", "..#.."],
    [".....", "##...", ".###.", "..##.", "...#."],
]


def atlas_ascii(frames, coul):
    h, w = len(frames[0]), len(frames[0][0])
    a = np.zeros((h, w * len(frames), 4), np.uint8)
    for i, fr in enumerate(frames):
        for y, ligne in enumerate(fr):
            for x, ch in enumerate(ligne):
                if ch == "#":
                    a[y, i * w + x] = rgba(coul)
    return a


# ═══════════════════════════════════════════════════════════════ aperçu
def composer(tex, t=0.0):
    """Aperçu de la scène (même ordre et mêmes positions que ChargementScene)."""
    W, H = VW, VH
    img = Image.new("RGBA", (W, H), hexc(CIEL_HAUT))

    def pose(arr, x, y):
        im = Image.fromarray(arr, "RGBA")
        img.alpha_composite(im, dest=(0, 0), source=(0, 0, 0, 0)) if False else None
        canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        canvas.paste(im, (int(x), int(y)))
        img.alpha_composite(canvas)

    def pose_rep(arr, y, dx):
        w = arr.shape[1]
        x = -((int(dx) % w))
        while x < W:
            pose(arr, x, y)
            x += w

    pose(tex["ciel"], -EXT_X, -EXT_Y)
    pose(tex["lune"], LUNE_C[0] - 104, LUNE_C[1] - 104)
    pose_rep(tex["nuages_voile"], 36, 4.5 * t + 300)
    pose(tex["village"], -EXT_X, 170)
    pose_rep(tex["nuages_bas"], 198, 7 * t + 120)
    pose(tex["avant"], -EXT_X, 118)
    for i, (x, y) in enumerate([(150, 70), (166, 62), (176, 76)]):
        fr = (int(t * 8) + i) % 4
        pose(tex["corbeau"][:, fr * CB_W:(fr + 1) * CB_W], x, y)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu", default=None)
    a = ap.parse_args()
    tex = {
        "ciel": ciel(), "lune": lune(), "nuages_voile": nuages_voile(), "village": village(), "nuages_bas": nuages_bas(), "avant": avant(), "corbeau": atlas_ascii(CORBEAU, "03050B"), "feuille": atlas_ascii(FEUILLE, "0A0E1C"),
    }
    for k, v in tex.items():
        sauver(v, k)
        print(f"{k}.png {v.shape[1]}x{v.shape[0]}")
    if a.apercu:
        os.makedirs(a.apercu, exist_ok=True)
        for t in (0.0, 3.0):
            im = composer(tex, t)
            im.resize((VW * 4, VH * 4), Image.NEAREST).save(os.path.join(a.apercu, f"apercu_{t:.0f}.png"))


if __name__ == "__main__":
    main()
