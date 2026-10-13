# -*- coding: utf-8 -*-
"""
gen_tirage_textures.py — textures du test de la Feuille (menu/tirage/TirageScreen : confirmation et résultat) au
niveau de la fiche shinobi (DA §4.1, §6), version épurée. Le test lui-même reste « in-world » (particules,
caméra de profil) : seuls la confirmation et le résultat ont une planche.

Signature de l'écran : la feuille de papier à chakra — un feuillet de washi posé sur un petit plateau d'offrande
laqué (sanbō) ; au résultat, la feuille a réagi selon la nature : brûlée (Katon), mouillée (Suiton), froissée
(Raiton), fendue (Fûton), effritée (Doton).

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_tirage_textures.py

Contrat :
- confirmation.png 308×128 : panneau laqué 300×120 en (2, 2) ; feuille sur son sanbō à gauche (dans le décor).
- resultat.png 240×204 : panneau laqué 232×196 en (2, 2).
- feuilles.png 240×56 : 5 feuilles réagies 44×56 (katon, suiton, raiton, futon, doton) en (48·i, 0).
"""
from __future__ import annotations

import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("tirage", budget=120_000)
FW, FH = 30, 40


def feuille(seed=1):
    """Feuillet de washi blanc cassé FW×FH, bord net, fibres légères."""
    im = nouveau(FW, FH)
    kit.washi(im, 0, 0, FW, FH, "blanc", seed=seed, bords="nets", ombre=False, taches=False)
    return im


def ombre(px, f, x, y, dx=1, dy=2, force=0.45):
    """Ombre portée qui suit la découpe de la feuille (alpha décalé)."""
    a = np.asarray(f)[..., 3].astype(float) / 255 * force * 255
    o = np.zeros((f.height, f.width, 4), np.uint8)
    o[..., 0], o[..., 1], o[..., 2] = 4, 2, 3
    o[..., 3] = a.astype(np.uint8)
    px.alpha_composite(Image.fromarray(o, "RGBA"), (x + dx, y + dy))


def sanbo(px, cx, y):
    """Petit plateau d'offrande (sanbō) de hinoki clair vu de face : plateau 44×4 sur un pied ajouré."""
    kit.ombre_portee(px, cx - 22, y, 44, 14, dx=1, dy=2, force=0.5, rayon=1)
    kit.bois(px, cx - 22, y, 44, 4, "clair", seed=3, noeuds=0)
    kit.bois(px, cx - 16, y + 4, 32, 10, "clair", seed=4, noeuds=0)
    rect(px, cx - 22, y + 4, 44, 1, hexc("5E3E22"))
    kit.disque_lisse(px, cx, y + 9, 3, "2C170C")


def confirmation():
    im = ja.fenetre(300, 120, 0.95, seed=101)
    cx, y = 46, 22
    f = feuille(3)
    ombre(im, f, cx - FW // 2, y)
    im.alpha_composite(f, (cx - FW // 2, y))
    sanbo(im, cx, y + FH + 2)
    S.sauver("confirmation.png", im, (308, 128))


def resultat():
    S.sauver("resultat.png", ja.fenetre(232, 196, 0.96, seed=102), (240, 204))


def reagie(nature, seed):
    im = nouveau(44, 56)
    f = feuille(seed)
    a = np.asarray(f).astype(float)
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    rs = random.Random(seed)
    if nature == "katon":
        # bords carbonisés, cœur intact, braise orangée au front de brûlure
        d = np.minimum.reduce([xx, w - 1 - xx, yy * 0.8, (h - 1 - yy) * 1.4]) + kit.bruit(w, h, 5, seed, 2) * 4
        a[d < 3, 3] = 0
        m = (d >= 3) & (d < 5)
        a[m, :3] = [40, 22, 14]
        m2 = (d >= 5) & (d < 6.5)
        a[m2, :3] = [232, 116, 44]
    elif nature == "suiton":
        wet = kit.bruit(w, h, 7, seed, 2)
        m = wet > 0.52
        a[m, :3] = a[m, :3] * np.array([0.84, 0.89, 0.95])
        a[(wet > 0.505) & (wet <= 0.52), :3] = [150, 170, 190]
    elif nature == "raiton":
        for x0 in (8, 16, 23):
            x = x0
            for y in range(h):
                if y % 3 == 0:
                    x = max(2, min(w - 3, x + rs.choice((-1, 1))))
                a[y, x, :3] *= 0.80
                a[y, x + 1, :3] = np.minimum(255, a[y, x + 1, :3] * 1.04)
    elif nature == "futon":
        # fendue en deux le long d'une ligne légèrement oblique
        for y in range(h):
            c = int(w / 2 + (y - h / 2) * 0.12)
            a[y, max(0, c - 1):c + 1, 3] = 0
    elif nature == "doton":
        d = (h - 1 - yy) + kit.bruit(w, h, 4, seed, 2) * 9
        a[d < 5, 3] = 0
        m = (d >= 5) & (d < 8)
        a[m, :3] = a[m, :3] * 0.55 + np.array([120, 84, 44]) * 0.45
        a[..., :3] = a[..., :3] * 0.96
    f = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")
    ox, oy = (44 - FW) // 2, 6
    ombre(im, f, ox, oy)
    im.alpha_composite(f, (ox, oy))
    if nature == "doton":
        for _ in range(9):
            rect(im, ox + rs.randint(0, FW - 2), oy + FH + rs.randint(0, 6), 1, 1, hexc("6E5434"))
    return im


def feuilles():
    im = nouveau(240, 56)
    for i, n in enumerate(("katon", "suiton", "raiton", "futon", "doton")):
        im.alpha_composite(reagie(n, 10 + i), (48 * i, 0))
    S.sauver("feuilles.png", im, (240, 56))


def main():
    confirmation()
    resultat()
    feuilles()
    S.bilan()


if __name__ == "__main__":
    main()
