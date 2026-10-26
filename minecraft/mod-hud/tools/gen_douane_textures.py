# -*- coding: utf-8 -*-
"""
gen_douane_textures.py — textures de la douane côté candidat (douane/DouaneHud.java), maquettes J1 à J5 du 24/10
(Maquettes/gameplan/candidature/jeu/_maquettes_douane_candidat.py : mêmes masques, mêmes couleurs). Signature des
écrans de douane : la barrière du poste-frontière (fermée au hall et au refus, levée à « Bienvenue »). Aucun kanji.

- barriere.png : atlas
    (0, 0)   barrière LEVÉE, au trait d'or (bord or, arête haute claire, intérieur sombre, lisse rayée) ;
    (Wl, 0)  barrière FERMÉE, en gris terne (résultat négatif) ;
    (0, 64)  petit picto 15 × 7 de la ligne du haut, en BLANC (teinté or au blit).
  Les tailles exactes sont écrites à la fin (à reporter dans DouaneHud : constantes LEVEE, FERMEE, PICTO).
- nuit.png 960 × 540 : vide de nuit chaud (Bienvenue) : dégradé tramé (Bayer 4 × 4, paliers de 2), halo doux au
  centre, quelques étoiles, vignettage ; 1 texel = 1 unité menu (2 px en 1080p). Une texture, un blit.
- nuit_froid.png 960 × 540 : la même, plus froide (hall fermé, attente neutre, résultat négatif).

Usage (depuis minecraft/mod-hud) :  python tools/gen_douane_textures.py
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import hexc, kit  # noqa: E402

S = ea.Sortie("douane", budget=400_000)

OR, OR_CLAIR, OR_SOMBRE = "D9A95E", "F2D08A", "8E6A30"
PAL_OR = {"o": OR, "h": OR_CLAIR, "f": "3A2A1E", "s": OR_SOMBRE}
PAL_TERNE = {"o": "8A7E70", "h": "B4A898", "f": "2A2224", "s": "5A5048"}


def sprite(pleins, rayures, pal):
    """Masques booléens -> sprite au trait (bord, arête haute claire, intérieur sombre, rayures de la lisse)."""
    m = pleins
    H, W = m.shape
    pad = np.pad(m, 1)
    voisins_vides = ~pad[:-2, 1:-1] | ~pad[2:, 1:-1] | ~pad[1:-1, :-2] | ~pad[1:-1, 2:]
    bord = m & voisins_vides
    haut = m & ~pad[:-2, 1:-1]
    out = np.zeros((H, W, 4), np.uint8)
    out[m & ~bord] = hexc(pal["f"])
    out[m & ~bord & rayures] = hexc(pal["s"])
    out[bord] = hexc(pal["o"])
    out[haut] = hexc(pal["h"])
    im = Image.fromarray(out, "RGBA")
    return im.crop(im.getbbox())


def barriere(ouverte, pal):
    """Boîtier à chapeau (petite fenêtre), lisse rayée, fourche d'appui au bout. Fermée : lisse posée sur la fourche ;
    levée : lisse dressée à 70°."""
    W, H = 64, 70
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    m = np.zeros((H, W), bool)
    m |= (xx >= 2) & (xx <= 11) & (yy >= 40) & (yy <= 57)        # boîtier
    m |= (xx >= 1) & (xx <= 12) & (yy >= 38) & (yy <= 39)        # chapeau
    m |= (xx >= 0) & (xx <= 13) & (yy >= 58) & (yy <= 59)        # socle
    fenetre = (xx >= 5) & (xx <= 8) & (yy >= 47) & (yy <= 48)
    px, py = 7.0, 43.0
    a = math.radians(-70 if ouverte else 0)
    ux, uy = math.cos(a), math.sin(a)
    t = (xx - px) * ux + (yy - py) * uy
    n = -(xx - px) * uy + (yy - py) * ux
    lisse = (t >= 5) & (t <= 40) & (n >= -2.5) & (n < 1.5)
    m |= lisse
    rayures = lisse & ((np.floor((t - 5) / 4) % 2) == 1)
    fx = 44
    m |= (xx >= fx) & (xx <= fx + 1) & (yy >= 47) & (yy <= 57)
    m |= ((xx == fx - 1) | (xx == fx + 2)) & (yy >= 45) & (yy <= 47)
    m |= (xx >= fx - 1) & (xx <= fx + 2) & (yy >= 46) & (yy <= 47)
    m |= (xx >= fx - 2) & (xx <= fx + 3) & (yy >= 58) & (yy <= 59)
    m &= ~fenetre
    return sprite(m, rayures, pal)


PICTO = [
    "###............",
    "###############",
    "###.##.##.##.##",
    "###..........#.",
    "###..........#.",
    "###..........#.",
    "###.........###",
]


def picto():
    im = Image.new("RGBA", (15, 7), (0, 0, 0, 0))
    for y, r in enumerate(PICTO):
        for x, k in enumerate(r):
            if k == "#":
                im.putpixel((x, y), (255, 255, 255, 255))
    return im


def atlas():
    lev = barriere(True, PAL_OR)
    fer = barriere(False, PAL_TERNE)
    pic = picto()
    w = max(lev.width + fer.width, pic.width)
    h = max(lev.height, fer.height) + 2 + pic.height
    oy = max(lev.height, fer.height) + 2
    # hauteur de l'atlas en puissance de 2 inutile (GUI) ; on garde l'atlas serré
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    im.alpha_composite(lev, (0, 0))
    im.alpha_composite(fer, (lev.width, 0))
    im.alpha_composite(pic, (0, oy))
    S.sauver("barriere.png", im, (w, h))
    print(f"LEVEE  = {{0, 0, {lev.width}, {lev.height}}}")
    print(f"FERMEE = {{{lev.width}, 0, {fer.width}, {fer.height}}}")
    print(f"PICTO  = {{0, {oy}, {pic.width}, {pic.height}}}")
    print(f"ATLAS  = {w} x {h}")


def nuit(nom, seed, froid):
    W, H = 960, 540
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    haut = np.array((22, 14, 30) if not froid else (16, 14, 22), float)
    bas = np.array((9, 6, 11), float)
    tt = np.clip(yy / H, 0, 1) ** 0.8
    base = haut * (1 - tt[..., None]) + bas * tt[..., None]
    d = np.sqrt(((xx - W / 2) / (W * 0.42)) ** 2 + ((yy - H * 0.46) / (H * 0.42)) ** 2)
    halo = np.clip(1 - d, 0, 1) ** 2 * (7 if not froid else 4)
    base = base + halo[..., None] * np.array((1.0, 0.75, 0.55) if not froid else (0.8, 0.8, 0.9))
    # vignettage doux (kit.voile_vignette, force 0.6, couleur (6, 3, 4)), appliqué avant le tramage
    dv = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    av = np.clip((dv - 0.55) / 0.85, 0, 1) ** 1.6 * 0.6
    base = base * (1 - av[..., None]) + np.array((6, 3, 4), float) * av[..., None]
    by = kit.bayer(H, W)
    q = np.floor(base / 2 + by[..., None] * 0.9) * 2
    img = np.clip(q, 0, 255).astype(np.uint8)
    im = Image.fromarray(img, "RGB").convert("RGBA")
    p = im.load()
    for _ in range(140):
        x, y = int(rng.integers(0, W)), int(rng.integers(0, int(H * 0.8)))
        v = int(rng.integers(70, 150))
        p[x, y] = (v, v - 6, v + 8, 255)
    for _ in range(14):
        x, y = int(rng.integers(0, W)), int(rng.integers(0, int(H * 0.6)))
        p[x, y] = (210, 200, 220, 255)
    S.sauver(nom, im, (W, H))


if __name__ == "__main__":
    atlas()
    nuit("nuit.png", 4, False)
    nuit("nuit_froid.png", 9, True)
    S.bilan()
