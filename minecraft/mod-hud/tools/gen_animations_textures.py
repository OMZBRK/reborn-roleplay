# -*- coding: utf-8 -*-
"""
gen_animations_textures.py — textures du menu des animations (animation/AnimationMenuScreen : emotes, démarches,
options) au niveau de la fiche shinobi (DA §4.1, §6), version épurée.

Signature de l'écran : l'éventail (sensu, 扇子) — un éventail de danse entrouvert, feuille de washi crème à
poussière d'or et liseré vermillon, brins de bambou laqué, posé à cheval sur le haut de la planche. La liste des
mouvements est une colonne de lignes laquées sobres ; l'aperçu du personnage reste à droite, sur le jeu.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_animations_textures.py

Contrat :
- panneau.png 228×348 : panneau laqué 220×340 en (2, 2) ; coupe verticale côté code (capB 18).
- eventail.png 132×64 : pivot de l'éventail en texel (66, 58) ; feuille entre les rayons 24 et 56.
- lignes.png 208×40 : 2 rangées de 20 (survol, choisie) ; bande 204×18 en (1, 1) ; coupe horizontale capD 6.
- pieces.png 64×16 : marqueurs 7×7 : choisi (0, 0), libre (8, 0), sous-menu (16, 0), arrêt (24, 0), retour
  (32, 0) ; flèches haut (40, 0) et bas (48, 0).
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("animations", budget=200_000)
SS = 4


def panneau():
    S.sauver("panneau.png", ja.fenetre(220, 340, 0.93, seed=41), (228, 348))


def eventail():
    W, H = 132, 64
    cx, cy = 66, 58
    r_in, r_out = 24, 56
    a0, a1 = 198, 342          # degrés (0 = 3 h, sens horaire PIL) : ouverture vers le haut
    n = 14                     # plis
    big = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    C = (cx * SS, cy * SS)

    def pt(r, a):
        return (C[0] + r * SS * math.cos(math.radians(a)), C[1] + r * SS * math.sin(math.radians(a)))
    # ombre portée
    sh = Image.new("RGBA", big.size, (0, 0, 0, 0))
    ds = ImageDraw.Draw(sh)
    ds.pieslice([C[0] - r_out * SS + 6, C[1] - r_out * SS + 10, C[0] + r_out * SS + 6, C[1] + r_out * SS + 10], a0, a1,
                fill=(4, 2, 3, 110))
    big.alpha_composite(sh)
    # feuille : plis alternés clair / ombré
    cremes = [(236, 224, 196), (222, 206, 174)]
    for k in range(n):
        b0 = a0 + (a1 - a0) * k / n
        b1 = a0 + (a1 - a0) * (k + 1) / n
        poly = [pt(r_in, b0), pt(r_out, b0), pt(r_out, (b0 + b1) / 2), pt(r_out, b1), pt(r_in, b1)]
        d.polygon(poly, fill=cremes[k % 2] + (255,))
    # liseré vermillon extérieur + fine bande or
    d.arc([C[0] - r_out * SS, C[1] - r_out * SS, C[0] + r_out * SS, C[1] + r_out * SS], a0, a1, fill=(160, 24, 43, 255),
          width=3 * SS)
    d.arc([C[0] - (r_out - 4) * SS, C[1] - (r_out - 4) * SS, C[0] + (r_out - 4) * SS, C[1] + (r_out - 4) * SS], a0, a1,
          fill=(200, 160, 90, 255), width=SS)
    d.arc([C[0] - r_in * SS, C[1] - r_in * SS, C[0] + r_in * SS, C[1] + r_in * SS], a0, a1, fill=(150, 120, 70, 255),
          width=SS)
    # brins (baguettes) du pivot au bord intérieur, brins de garde laqués jusqu'au bord
    for k in range(n + 1):
        a = a0 + (a1 - a0) * k / n
        garde = k in (0, n)
        d.line([pt(3, a), pt(r_out if garde else r_in, a)], fill=(26, 16, 19, 255) if garde else (74, 52, 32, 255),
               width=(3 if garde else 1) * SS)
    # rivet d'or au pivot
    d.ellipse([C[0] - 3 * SS, C[1] - 3 * SS, C[0] + 3 * SS, C[1] + 3 * SS], fill=(217, 169, 94, 255))
    d.ellipse([C[0] - 1 * SS, C[1] - 1 * SS, C[0] + 1 * SS, C[1] + 1 * SS], fill=(255, 242, 200, 255))
    im = big.resize((W, H), Image.LANCZOS)
    # poussière d'or sur la feuille (quelques pixels)
    import random
    rs = random.Random(5)
    px = im.load()
    for _ in range(70):
        r = rs.uniform(r_in + 4, r_out - 6)
        a = math.radians(rs.uniform(a0 + 4, a1 - 4))
        x, y = int(cx + r * math.cos(a)), int(cy + r * math.sin(a))
        if px[x, y][3] > 200:
            px[x, y] = hexc(R_OR[rs.choice((5, 6, 7))])
    S.sauver("eventail.png", im, (W, H))


def lignes():
    im = nouveau(208, 40)
    for r, etat in enumerate(("survol", "choisie")):
        x, y, w, h = 1, r * 20 + 1, 204, 18
        if etat == "survol":
            fa.lq(im, x, y, w, h, ["211418", "2A1A1F", "362228", "4A2E35"], seed=2, intensite=0.8)
            rect(im, x, y, w, 1, hexc(R_OR[2]))
            rect(im, x, y + h - 1, w, 1, hexc(R_OR[2]))
        else:
            fa.lq(im, x, y, w, h, ["3A0A12", "4E0D17", "63111D", "7A1322", "8E1628"], seed=3, intensite=1.0)
            fa.liseret(im, x, y, w, h)
        for j in range(h):
            rect(im, x + 1, y + j, 2, 1, hexc(R_OR[5] if j % 3 else R_OR[6]))
    S.sauver("lignes.png", im, (208, 40))


def pieces():
    im = nouveau(64, 16)
    o, g, c, s = "2A1A10", R_OR[5], R_OR[7], R_OR[3]
    ja.sprite(im, 0, 0, ["..aaa..", ".abbba.", "abbcbba", "abcccba", "abbcbba", ".abbba.", "..aaa.."], {"a": o, "b": g, "c": c})
    ja.sprite(im, 8, 0, ["..aaa..", ".a...a.", "a.....a", "a.....a", "a.....a", ".a...a.", "..aaa.."], {"a": s})
    ja.sprite(im, 16, 0, [".a.....", ".ba....", ".bba...", ".bbba..", ".bba...", ".ba....", ".a....."], {"a": s, "b": g})
    ja.sprite(im, 24, 0, [".......", ".aaaaa.", ".abbba.", ".abbba.", ".abbba.", ".aaaaa.", "......."], {"a": "5C0E18", "b": "C01E35"})
    ja.sprite(im, 32, 0, [".....a.", "....ab.", "...abb.", "..abbb.", "...abb.", "....ab.", ".....a."], {"a": s, "b": g})
    ja.sprite(im, 40, 0, [".......", "...a...", "..aba..", ".abbba.", "abbbbba", ".......", "......."], {"a": s, "b": g})
    ja.sprite(im, 48, 0, [".......", ".......", "abbbbba", ".abbba.", "..aba..", "...a...", "......."], {"a": s, "b": g})
    S.sauver("pieces.png", im, (64, 16))


def main():
    panneau()
    eventail()
    lignes()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
