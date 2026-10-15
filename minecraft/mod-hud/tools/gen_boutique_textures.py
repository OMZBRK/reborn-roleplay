# -*- coding: utf-8 -*-
"""
gen_boutique_textures.py — décor fixe des boutiques-lieux (15/10, BOUTIQUES.md, maquettes
Maquettes/gameplan/42_boutiques 02 à 04) : l'écran du tailleur et celui du bazar (boutique/BoutiqueScreen), et
l'écran « Boutique — bientôt » de l'Échap (boutique/BoutiqueVideScreen). Écrans par-dessus le jeu, centrés, épurés,
AUCUN kanji (DA §3). Signatures : le MÈTRE RUBAN d'or du tailleur, la BALANCE ROMAINE du marchand (bazar).

Usage (depuis minecraft/mod-hud) :  python tools/gen_boutique_textures.py

Contrat (1 texel = 1 GUI) :
- panneau.png 236×392 : panneau laqué du kit (sans reflet) 230×386 en (0, 0), ombre jusqu'à 236×392 ; posé par coupe
  (DaKit.coupe, capD 16, capB 16) : catalogue 230 × 380, confirmation 236 × 142, bazar 300 × …
- metre.png 80×9 : mètre ruban d'or (graduations, boîtier rond à droite).
- balance.png 48×22 : balance romaine (fléau gradué, crochet, plateau, poids).
- bourse.png 12×12 : la bourse (sac de tissu rouge noué d'or).
- cadenas.png 8×10 : cadenas d'une pièce verrouillée (teint au rendu).
- piece.png 8×8 : pièce de ryo des prix (disque d'or, trou carré), plus petite que celle du guichet.
"""
from __future__ import annotations

import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import kit, hexc  # noqa: E402

S = ea.Sortie("boutique", budget=200_000)

OR, OR_CLAIR, OR_SOMBRE, OR_NUIT = "D9A95E", "F2D08A", "8E6A30", "4A3518"


def panneau():
    w, h = 230, 386
    im = Image.new("RGBA", (w + 6, h + 6), (0, 0, 0, 0))
    kit.panneau_laque(im, 0, 0, w, h, ton="noir", modele_kanagu="petit", reflet=False)
    S.sauver("panneau.png", im, (236, 392))


def metre(w=80, h=9):
    """Ruban gradué tendu, boîtier rond à droite (la signature du tailleur, au-dessus du titre)."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    o, oc, os_, on = hexc(OR), hexc(OR_CLAIR), hexc(OR_SOMBRE), hexc(OR_NUIT)
    y0, y1 = 3, 5                      # le ruban : 3 texels de haut
    fin = w - 10
    d.rectangle([1, y0, fin, y1], fill=o)
    d.line([(1, y0), (fin, y0)], fill=oc)
    d.line([(1, y1), (fin, y1)], fill=os_)
    for x in range(3, fin, 3):         # graduations : longues tous les 5
        d.line([(x, y0), (x, y0 + (2 if x % 15 == 0 else 1))], fill=on)
    d.rectangle([0, y0 - 1, 1, y1 + 1], fill=os_)   # languette du bout
    # boîtier rond
    cx, cy, r = w - 5, 4, 4
    for yy in range(h):
        for xx in range(w - 10, w):
            rr = ((xx - cx) ** 2 + (yy - cy) ** 2) ** 0.5
            if rr <= r + 0.3:
                c = os_ if rr > r - 0.9 else (oc if xx + yy < cx + cy - 1 else o)
                im.putpixel((xx, yy), c)
    im.putpixel((cx, cy), on)
    S.sauver("metre.png", im, (w, h))


def balance(w=48, h=22):
    """Balance romaine du marchand : fléau gradué, crochet et plateau à gauche, poids qui coulisse à droite."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    o, oc, os_, on = hexc(OR), hexc(OR_CLAIR), hexc(OR_SOMBRE), hexc(OR_NUIT)
    fy = 5
    d.line([(4, fy), (w - 3, fy - 1)], fill=oc)
    d.line([(4, fy + 1), (w - 3, fy)], fill=os_)
    for x in range(16, w - 4, 4):
        d.point((x, fy + 1), fill=on)
    # anneau de suspension
    d.line([(12, 0), (12, fy - 1)], fill=o)
    d.point((11, 0), fill=o)
    d.point((13, 0), fill=o)
    # crochet et plateau
    d.line([(5, fy + 1), (5, 12)], fill=os_)
    d.line([(5, fy + 1), (1, 14)], fill=os_)
    d.line([(5, fy + 1), (9, 14)], fill=os_)
    d.line([(0, 15), (10, 15)], fill=o)
    d.line([(1, 16), (9, 16)], fill=os_)
    # poids coulissant
    px = 34
    d.line([(px, fy + 1), (px, 10)], fill=os_)
    d.rectangle([px - 2, 11, px + 2, 15], fill=o)
    d.line([(px - 2, 11), (px + 2, 11)], fill=oc)
    d.line([(px - 2, 15), (px + 2, 15)], fill=os_)
    S.sauver("balance.png", im, (w, h))


def bourse():
    """La bourse : sac de tissu rouge, col froncé noué d'un cordon d'or."""
    im = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    R = ["5A0E18", "7E1622", "A0182B", "C2283A", "D8485A"]
    for y in range(12):
        for x in range(12):
            if y < 4:
                ok = abs(x - 5.5) <= (1.5 if y < 2 else 2.5) and y >= 1
            else:
                ok = ((x - 5.5) / 5.4) ** 2 + ((y - 7.6) / 4.2) ** 2 <= 1.0
            if not ok:
                continue
            k = 2
            if x + y < 9:
                k = 3
            if x + y < 7 and y > 4:
                k = 4
            if x > 7 or y > 9:
                k = 1
            if (x > 8 and y > 8) or y >= 11:
                k = 0
            im.putpixel((x, y), hexc(R[k]))
    for x in range(3, 9):
        im.putpixel((x, 3), hexc(OR if x % 2 else OR_CLAIR))
    im.putpixel((8, 4), hexc(OR_SOMBRE))
    im.putpixel((9, 5), hexc(OR))
    S.sauver("bourse.png", im, (12, 12))


def cadenas():
    """Cadenas blanc (teint au rendu : gris pour une pièce verrouillée, ambre dans le détail)."""
    im = Image.new("RGBA", (8, 10), (0, 0, 0, 0))
    b, m = (255, 255, 255, 255), (170, 170, 170, 255)
    for y in range(0, 4):
        for x in (1, 6):
            im.putpixel((x, y + 1), b)
    for x in range(2, 6):
        im.putpixel((x, 0), b)
    for y in range(4, 10):
        for x in range(8):
            im.putpixel((x, y), b if y < 9 else m)
    im.putpixel((3, 6), (0, 0, 0, 0))
    im.putpixel((4, 6), (0, 0, 0, 0))
    im.putpixel((3, 7), (0, 0, 0, 0))
    S.sauver("cadenas.png", im, (8, 10))


def piece():
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y in range(8):
        for x in range(8):
            r = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            if r > 3.95:
                continue
            if 3 <= x <= 4 and 3 <= y <= 4:
                c = None
            elif r > 3.1:
                c = OR_SOMBRE
            elif x + y < 6:
                c = OR_CLAIR
            else:
                c = OR
            if c:
                im.putpixel((x, y), hexc(c))
    S.sauver("piece.png", im, (8, 8))


if __name__ == "__main__":
    panneau()
    piece()
    metre()
    balance()
    bourse()
    cadenas()
    S.bilan()
