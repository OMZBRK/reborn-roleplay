# -*- coding: utf-8 -*-
"""
gen_monnaie_textures.py — décor fixe des écrans de la monnaie et de la banque (15/10, maquettes
Maquettes/gameplan/19_monnaie 02 à 05) : le guichet (monnaie/GuichetScreen) et le petit écran « Combien ? »
(monnaie/CombienScreen). Écrans centrés, épurés, AUCUN kanji (DA §3) ; signature du guichet : la balance du changeur.

Usage (depuis minecraft/mod-hud) :  python tools/gen_monnaie_textures.py

Contrat (1 texel = 1 GUI) :
- panneau.png 316×346 : panneau laqué noir du kit (kanagu « petit ») 310×340 en (0, 0), ombre jusqu'à 316×346 ;
  posé par coupe (DaKit.coupe, capD 16, capB 16) à la taille voulue (guichet 310 × 258…330, « Combien ? » 170 × 104).
- tenbin.png 44×28 : balance à fléau en filet d'or (plateau droit plus bas, une pièce).
- piece.png 12×12 : pièce de ryo (disque d'or, trou carré).
"""
from __future__ import annotations

import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import kit, hexc, R_OR  # noqa: E402

S = ea.Sortie("monnaie", budget=200_000)

OR, OR_CLAIR, OR_SOMBRE = "D9A95E", "F2D08A", "8E6A30"


def panneau():
    w, h = 310, 340
    im = Image.new("RGBA", (w + 6, h + 6), (0, 0, 0, 0))
    kit.panneau_laque(im, 0, 0, w, h, ton="noir", modele_kanagu="petit")
    S.sauver("panneau.png", im, (316, 346))


def tenbin(w=44, h=28):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    o, oc, os_ = hexc(OR), hexc(OR_CLAIR), hexc(OR_SOMBRE)
    cx = w // 2
    d.line([(cx, 3), (cx, h - 4)], fill=o)
    d.point((cx, 2), fill=oc)
    d.line([(cx - 5, h - 3), (cx + 5, h - 3)], fill=o)
    d.line([(cx - 7, h - 2), (cx + 7, h - 2)], fill=os_)
    d.line([(3, 5), (cx, 4)], fill=oc)
    d.line([(cx, 4), (w - 4, 7)], fill=o)
    d.line([(4, 6), (4, 14)], fill=os_)
    d.line([(4, 6), (1, 14)], fill=os_)
    d.line([(4, 6), (7, 14)], fill=os_)
    d.line([(0, 15), (8, 15)], fill=o)
    d.line([(1, 16), (7, 16)], fill=os_)
    xr = w - 4
    d.line([(xr, 8), (xr, 18)], fill=os_)
    d.line([(xr, 8), (xr - 3, 18)], fill=os_)
    d.line([(xr, 8), (xr + 3, 18)], fill=os_)
    d.line([(xr - 4, 19), (xr + 4, 19)], fill=o)
    d.line([(xr - 3, 20), (xr + 3, 20)], fill=os_)
    d.rectangle([xr - 1, 17, xr + 1, 18], fill=oc)
    S.sauver("tenbin.png", im, (w, h))


def piece():
    im = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    for y in range(12):
        for x in range(12):
            r = ((x - 5.5) ** 2 + (y - 5.5) ** 2) ** 0.5
            if r > 5.9:
                continue
            if 4 <= x <= 7 and 4 <= y <= 7:
                c = None if (5 <= x <= 6 and 5 <= y <= 6) else OR_SOMBRE
            elif r > 4.9:
                c = OR_SOMBRE
            elif x + y < 9:
                c = OR_CLAIR
            else:
                c = OR
            if c:
                im.putpixel((x, y), hexc(c))
    S.sauver("piece.png", im, (12, 12))


if __name__ == "__main__":
    panneau()
    tenbin()
    piece()
    S.bilan()
