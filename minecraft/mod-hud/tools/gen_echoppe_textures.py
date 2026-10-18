# -*- coding: utf-8 -*-
"""
gen_echoppe_textures.py — textures de l'Échoppe (menu/widget/ShopScreen) au niveau de la fiche shinobi.

Signature conservée : l'échoppe de nuit — noren indigo au blason (pièce de ryo, sans kanji depuis le 18/10) sous sa hampe, chōchin rouges qui se balancent,
mur de planches, le personnage sur une estrade éclairée, le catalogue en étiquettes de papier (fuda) dans un tableau
laqué, deux onglets pendus (TENUES / CARTES), la bourse en ryo.

1 texel = 1 GUI (échelle « menu » : 960×540 en 1080p). Tout le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_echoppe_textures.py

Contrat :
- mur.png 1280×720 : planches + sol ; posé centré en x, CALÉ EN BAS (texel (640, 720) = (W/2, H)). Le plancher
  occupe les 64 dernières rangées (arête du sol en y 656 du texel, soit H − 64).
- noren.png 96×40 : un lé 80×36 en (0, 0) (répété, balancé d'1 GUI par le code) ; hampe 16×5 en (80, 0)
  (répétée) ; blason 26×26 en (0, ... ) → cf. blason.png.
- blason.png 28×28 : disque blanc cerné, pièce de ryo indigo à trou carré ; centre (14, 14).
- chochin.png 48×38 : 2 images 24×38 (repos, plus vive) ; attache au texel (12, 0).
- halo.png 64×64 : halo en crans (blanc, l'alpha porte le niveau), teint et posé à 64 × m.
- enseigne.png 156×62 : plaque suspendue 140×30 en (8, 12) ; cordons depuis la hampe (y 0) ; agemaki et
  glands sous les coins.
- planche.png 320×460 : panneau laqué 312×452 en (2, 2) (ombre comprise) ; coupe capD = capB = 18.
- onglets.png 140×60 : 3 rangées de 20 (repos, survol, actif) ; plaque 136×16 en (1, 1) ; coupe horizontale
  capD 6 ; anneau d'attache cuit au centre de la cellule (texel 68) : le code ne recentre pas (fil posé par le code).
- etiquettes.png 300×88 : 4 rangées de 22 (normal, survol, choisie, choisie survol) ; étiquette 296×18 en (1, 1) ;
  coupe horizontale capD 8 ; œillet en (6, 9).
- boutons.png 340×120 : 5 rangées de 24 (normal, survol, rouge, rouge survol, inactif) ; bouton 336×20 en (1, 1),
  coupe horizontale capD 6.
- icones.png 96×16 : robe 14×14 (0, 0), carte roulée 14×14 (16, 0), pièce ryo 10×10 (32, 0), sceau « à toi » (coche) 14×14
  (44, 0), sceau « porté » (tenue) 14×14 (60, 0), petit cadenas 9×10 (76, 0).
- estrade.png 184×34 : estrade 176×30 en (4, 2) ; le centre des pieds au texel (92, 12).
- pieces.png 420×64 : cabochon 42×12 (0, 0) ; rail de défilement 4×40 (404, 0) ; curseur 6×16 (410, 0) ;
  toast washi 400×18 (0, 20) coupe horizontale capD 6 ; bourse 160×18 (0, 42) coupe horizontale capD 6.
- etagere.png 128×100 : étagère de tissus (rouleaux, piles pliées) posée sur le mur ; planches en y 40 et 92.
- vignette.png 160×90 : vignette lisse (étirée).
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, coller, R_OR, R_LAQUE, R_LAQUE_ROUGE, nouveau  # noqa: E402
import pictos  # noqa: E402  (aucun kanji décoratif, 18/10)

S = ea.Sortie("echoppe", budget=900_000)
R_IND = ["0C1020", "121A30", "1A2442", "223056", "2C3C68", "3A4C7C", "4C5E90", "6476A0", "8C9CC0", "C8D0E4"]


def disque(s, r, cx=None, cy=None):
    cx = (s - 1) / 2 if cx is None else cx
    cy = (s - 1) / 2 if cy is None else cy
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


# ═════════════════════════════════════ mur et sol ═════════════════════════════════════
def mur():
    W, H = 1280, 720
    im = nouveau(W, H)
    # planches verticales (22 GUI), teinte propre à chaque planche, veinage, joints sombres
    rs = random.Random(5)
    x = 0
    k = 0
    while x < W:
        pw = rs.choice((22, 24, 26, 22))
        kit.bois(im, x, 0, pw, H - 64, "sombre", sens="v", seed=200 + k, noeuds=rs.choice((0, 1, 2)), biseau=False)
        rect(im, x, 0, 1, H - 64, hexc("0E0604"))
        rect(im, x + 1, 0, 1, H - 64, hexc("4A2A18"))
        x += pw
        k += 1
    # traverse (nuki) basse : bois moyen avec chevilles
    ty = H - 64 - 40
    kit.bois(im, 0, ty, W, 9, "moyen", sens="h", seed=230, noeuds=2)
    rect(im, 0, ty + 9, W, 1, hexc("0E0604"))
    for xx in range(40, W, 88):
        rect(im, xx, ty + 3, 3, 3, hexc("2C170C"))
        rect(im, xx, ty + 3, 1, 1, hexc("A96B3F"))
    # pénombre : plus sombre en haut (sous le noren) et sur les côtés, en crans
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    A = np.clip((yy - (H - 300)) / -420, 0, 1) ** 1.2 * 0.45
    d = np.abs(xx - W / 2) / (W / 2)
    A += np.clip((d - 0.45) / 0.55, 0, 1) ** 1.6 * 0.40
    q = kit.quantifier(np.clip(A, 0, 0.999), 5, 0, 0, tramage=0.16) / 4 * 0.9
    fa.assombrir(im, 0, 0, q)
    # sol : lames de bois en perspective légère, arête or éteint
    fy = H - 64
    for j in range(64):
        t = j / 63
        rect(im, 0, fy + j, W, 1, hexc(fa.mix("2C170E", "120806", t)))
    rs = random.Random(9)
    jy = [fy + int(round(64 * (i / 6) ** 1.35)) for i in range(7)]
    for a, b in zip(jy[:-1], jy[1:]):
        if b - a < 2:
            continue
        kit.bois(im, 0, a, W, b - a, "sombre", sens="h", seed=a, noeuds=0, biseau=False)
        rect(im, 0, a, W, 1, hexc("0A0403"))
        if b - a > 4:
            rect(im, 0, a + 1, W, 1, hexc("5C351D"))
        off = rs.randint(0, 120)
        for xx_ in range(off, W, 160 + (b - a) * 8):
            rect(im, xx_, a + 1, 1, b - a - 1, hexc("0A0403"))
    shade = np.clip((np.arange(64)[:, None] / 63.0) * 0.55 + (np.abs(np.arange(W)[None, :] - W / 2) / (W / 2)) ** 1.6 * 0.4, 0, 0.95)
    fa.assombrir(im, 0, fy, kit.quantifier(shade, 5, 0, fy, tramage=0.16) / 4 * 0.85)
    rect(im, 0, fy, W, 1, hexc(R_OR[3]))
    rect(im, 0, fy - 1, W, 1, hexc("0A0403"))
    S.sauver("mur.png", im, (W, H), quantifier=True)


# ═════════════════════════════════════ noren, blason, chōchin ═════════════════════════════════════
def noren():
    im = nouveau(96, 40)
    w, h = 80, 36
    kit.bande_tissu(im, 0, 4, w, h - 4, "indigo", "seigaiha", liseret=None, seed=3)
    a = np.asarray(im).copy()
    # passants (chichi) en haut, ourlet sombre en bas, bords du lé
    a[0:4, 0:w] = 0
    for cx in range(6, w, 17):
        a[0:5, cx:cx + 7] = hexc(R_IND[2])
        a[0, cx:cx + 7] = hexc(R_IND[4])
    a[h - 3:h, 0:w] = hexc(R_IND[1])
    a[h - 3, 0:w] = hexc(R_IND[3])
    a[4:h, 0] = hexc(R_IND[0])
    a[4:h, w - 1] = hexc(R_IND[0])
    a[4:h, 1] = hexc(R_IND[4])
    im = Image.fromarray(a, "RGBA")
    # hampe de bambou laqué (16×5)
    for k, c in enumerate(("050304", "6E4124", "4B2A17", "2C170E", "120806")):
        rect(im, 80, k, 16, 1, hexc(c))
    rect(im, 88, 1, 1, 3, hexc("A96B3F"))
    rect(im, 89, 1, 1, 3, hexc("120806"))
    S.sauver("noren.png", im, (96, 40))

    b = nouveau(28, 28)
    m = disque(28, 12.6)
    p = np.zeros((28, 28, 4), np.uint8)
    yy, xx = np.mgrid[0:28, 0:28].astype(float)
    d = np.sqrt((xx - 13.5) ** 2 + (yy - 13.5) ** 2)
    p[m] = hexc("ECE4D0")
    p[m & ((xx + yy) > 30)] = hexc("D8CCB2")
    p[m & (d > 11.4)] = hexc("B8AC94")
    p[m & (d > 12.0)] = hexc("1A2442")
    coller(b, p, 0, 0)
    # blason : une pièce de ryo indigo à trou carré (plus de kanji depuis le 18/10, DA §3)
    rm = disque(28, 7.6)
    trou = (np.abs(xx - 13.5) <= 2.5) & (np.abs(yy - 13.5) <= 2.5)
    q = np.zeros((28, 28, 4), np.uint8)
    q[rm & ~trou] = hexc("1F2848")
    q[rm & ~trou & (d > 5.9) & (d <= 6.6)] = hexc("3A4A78")
    coller(b, q, 0, 0)
    S.sauver("blason.png", b, (28, 28))


def chochin():
    im = nouveau(48, 38)
    for f in range(2):
        ox = f * 24
        R = kit.rampe("C23A2C", 9, ombre=0.22, lumiere=0.62)
        cx = 11.5
        for j in range(5, 33):
            t = (j - 5) / 27
            hw = 5 + 6.0 * math.sin(math.pi * t) ** 0.65
            for i in range(24):
                dx = (i - cx) / hw
                if abs(dx) <= 1:
                    v = 0.78 - 0.48 * dx * dx - 0.14 * dx + (0.10 if f else 0) - 0.22 * abs(t - 0.5)
                    if (j - 5) % 4 == 0:
                        v -= 0.16
                    k = int(np.clip(v, 0, 0.999) * len(R))
                    im.putpixel((ox + i, j), R[k])
                    if abs(dx) > 0.92:
                        im.putpixel((ox + i, j), R[max(0, k - 2)])
        for (j0, hh, ww) in ((2, 3, 10), (33, 3, 10)):
            fa.lq(im, ox + 12 - ww // 2, j0, ww, hh, "noir", seed=j0, reflet=False)
            rect(im, ox + 12 - ww // 2, j0, ww, 1, hexc("4A2E35"))
        rect(im, ox + 11, 0, 2, 2, hexc(R_OR[4]))
        rect(im, ox + 11, 0, 1, 1, hexc(R_OR[7]))
    S.sauver("chochin.png", im, (48, 38))


def halo():
    A = fa.masque_lumiere(31, 31, 5, expo=1.7)
    h, w = A.shape
    p = np.zeros((64, 64, 4), np.uint8)
    p[..., :3] = 255
    p[:h, :w, 3] = np.clip(A * 255, 0, 255).astype(np.uint8)
    S.sauver("halo.png", p, (64, 64))


# ═════════════════════════════════════ enseigne, planche, onglets ═════════════════════════════════════
def enseigne():
    im = nouveau(156, 62)
    x, y, w, h = 8, 12, 140, 30
    for ax in (x + 22, x + w - 23):
        fa.corde_v(im, ax, 0, y + 1, "rouge", ep=2)
    fa.panneau(im, x, y, w, h, seed=12, eclats=False)
    for ax in (x + 22, x + w - 23):
        fa.anneau_or(im, ax + 1, y + 1)
    for gx in (x + 6, x + w - 7):
        fa.agemaki(im, gx, y + h + 3)
        fa.gland3(im, gx, y + h + 8, 6)
    S.sauver("enseigne.png", im, (156, 62))


def planche():
    im = nouveau(320, 460)
    fa.panneau(im, 2, 2, 312, 452, seed=21, eclats=True)
    a = np.asarray(im)
    S.sauver("planche.png", a, (320, 460))


def onglets():
    im = nouveau(140, 60)
    for r, etat in enumerate(("repos", "survol", "actif")):
        x, y, w, h = 1, r * 20 + 1, 136, 16
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=2, force=0.55, rayon=1)
        if etat == "actif":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=r, intensite=1.3)
            fa.liseret(im, x, y, w, h)
            fa.ferrures3(im, x, y, w, h)
        elif etat == "survol":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=1.7)
            fa.liseret(im, x, y, w, h)
        else:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=0.9)
            fa.liseret_terne(im, x, y, w, h)
    S.sauver("onglets.png", im, (140, 60))


def etiquettes():
    """Étiquettes de papier (fuda) : washi crème, œillet et ficelle, ombre ; choisie : washi plus clair, trait de
    pinceau vermillon à gauche, liseré d'or."""
    im = nouveau(300, 88)
    for r, etat in enumerate(("normal", "survol", "choisie", "choisie_survol")):
        x, y, w, h = 1, r * 22 + 1, 296, 18
        lay = nouveau(300, 22)
        kit.ombre_portee(lay, x, 1, w, h, dx=1, dy=2, force=0.5, rayon=0)
        R = list(kit.R_WASHI["creme"])
        if etat == "survol" or etat == "choisie_survol":
            R = R[1:] + ["FFFFFF"]
        kit.washi(lay, x, 1, w, h, R, seed=40 + r, bords="nets", ombre=False, taches=False)
        if etat.startswith("choisie"):
            # trait de pinceau vermillon (bande verticale irrégulière)
            for j in range(h):
                ww = 3 + (1 if j % 5 in (1, 2) else 0)
                rect(lay, x + 1, 1 + j, ww, 1, hexc(fa.R_VERM[4] if j % 3 else fa.R_VERM[3]))
            c = hexc(R_OR[5])
            rect(lay, x, 1, w, 1, c); rect(lay, x, 1 + h - 1, w, 1, hexc(R_OR[3]))
            rect(lay, x + w - 1, 1, 1, h, hexc(R_OR[3]))
        # œillet (rondelle de laiton) + ficelle
        ox_, oy_ = x + 7, 1 + h // 2
        rect(lay, ox_ - 1, oy_ - 1, 3, 3, hexc(R_OR[4]))
        rect(lay, ox_ - 1, oy_ - 1, 1, 1, hexc(R_OR[7]))
        rect(lay, ox_, oy_, 1, 1, hexc("2A1A10"))
        rect(lay, 0, oy_, ox_ - 1, 1, hexc("8A7A60"))
        coller(im, lay, 0, r * 22)
    S.sauver("etiquettes.png", im, (300, 88))


def boutons():
    im = nouveau(340, 120)
    for r, etat in enumerate(("normal", "survol", "rouge", "rouge_survol", "inactif")):
        x, y, w, h = 1, r * 24 + 1, 336, 20
        if etat in ("normal", "survol"):
            gf.bouton4(im, x, y, etat, ton="noir", h=h, w=w)
        elif etat == "rouge":
            gf.bouton4(im, x, y, "normal", ton="rouge", h=h, w=w)
        elif etat == "rouge_survol":
            gf.bouton4(im, x, y, "survol", ton="rouge", h=h, w=w)
        else:
            gf.bouton4(im, x, y, "inactif", ton="noir", h=h, w=w)
    S.sauver("boutons.png", im, (340, 120))


# ═════════════════════════════════════ icônes, estrade, pièces ═════════════════════════════════════
def icones():
    im = nouveau(96, 16)
    # robe (kimono) 14×14
    robe = [
        "...aa....aa...",
        "..abba..abba..",
        ".abbbbaabbbba.",
        "abbbbcddcbbbba",
        "abbbcdeedcbbba",
        ".aabcdeedcbaa.",
        "...acdeedca...",
        "...affffffa...",
        "...acdeedca...",
        "...acdeedca...",
        "...acdeedca...",
        "...acdeedca...",
        "...acdeedca...",
        "...aaaaaaaa...",
    ]
    pal = {"a": hexc("1A2442"), "b": hexc("38466E"), "c": hexc("4C5E90"), "d": hexc("6476A0"), "e": hexc("8C9CC0"),
           "f": hexc("A0182B")}
    for j, row in enumerate(robe):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(im, i, j, 1, 1, pal[ch])
    # carte roulée 14×14
    carte = [
        "..............",
        ".aaaaaaaaaaaa.",
        "abccccccccccba",
        "abddeeddeeddba",
        ".addeeddeedda.",
        ".adeeddefddea.",
        ".addeeddeedda.",
        ".adeeddeeddea.",
        ".addeefdeedda.",
        ".adeeddeeddea.",
        "abccccccccccba",
        "abbbbbbbbbbbba",
        ".aaaaaaaaaaaa.",
        "..............",
    ]
    pal = {"a": hexc("4A3018"), "b": hexc("8A6438"), "c": hexc("B08C58"), "d": hexc("E8DABA"), "e": hexc("CDB993"),
           "f": hexc("A0182B")}
    for j, row in enumerate(carte):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(im, 16 + i, j, 1, 1, pal[ch])
    # pièce de ryo (mon) 10×10 : or, trou carré
    m = disque(10, 5.0)
    yy, xx = np.mgrid[0:10, 0:10].astype(float)
    d = np.sqrt((xx - 4.5) ** 2 + (yy - 4.5) ** 2)
    p = np.zeros((10, 10, 4), np.uint8)
    p[m] = hexc(R_OR[5])
    p[m & ((xx + yy) < 7)] = hexc(R_OR[7])
    p[m & ((xx + yy) > 11)] = hexc(R_OR[4])
    p[m & (d > 4.1)] = hexc(R_OR[2])
    p[3:7, 3:7] = hexc(R_OR[2])
    p[4:6, 4:6] = 0
    coller(im, p, 32, 0)
    # sceaux « déjà à toi » (coche) et « porté » (tenue) : pictos, plus de kanji (18/10, DA §3)
    for k, (ox, enc, ton) in enumerate(((44, "reussi", fa.R_VERM),
                                        (60, "tenue", ["1E3A2A", "264638", "345C4A", "467560", "5E8E78", "7CAA94", "9CC4B0"]))):
        gf.hanko_px(im, ox + 7, 7, pictos.encre_px(14, enc), 14, ton, seed=2 + k, usure=0.08)
    # cadenas 9×10
    cad = [".aaaaa...", "a.....a..", "a.....a..", "bbbbbbbb.", "bcccccb..", "bcc.ccb..", "bcc.ccb..", "bcccccb..",
           "bbbbbbb.."]
    pal = {"a": hexc("8A7A6A"), "b": hexc("5A4A3A"), "c": hexc("8E6A30")}
    for j, row in enumerate(cad):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(im, 76 + i, j, 1, 1, pal[ch])
    S.sauver("icones.png", im, (96, 16))


def estrade():
    im = nouveau(184, 34)
    y_top, y_av, ep = 2, 18, 7
    ox = 4
    kit.ombre_portee(im, ox, y_av, 176, ep, dx=2, dy=2, force=0.65, rayon=2)
    for y in range(y_top, y_av):
        t = (y - y_top) / (y_av - y_top)
        half = 72 + 16 * t
        x0, x1 = int(round(92 - half)), int(round(92 + half))
        c = fa.mix("2C170E", "6E4124", 0.25 + 0.6 * t)
        rect(im, x0, y, x1 - x0, 1, c)
        rect(im, x0, y, 1, 1, hexc(R_OR[3]))
        rect(im, x1 - 1, y, 1, 1, hexc(R_OR[2]))
    for x in range(30, 160, 18):
        rect(im, x, y_top + 2, 1, y_av - y_top - 3, hexc("1E100A"))
    rect(im, 20, y_top, 144, 1, hexc(R_OR[4]))
    fa.lq(im, ox, y_av, 176, ep, "rouge", seed=3)
    rect(im, ox, y_av, 176, 1, fa.BISEAU_CLAIR)
    rect(im, ox, y_av + ep - 1, 176, 1, hexc("1A0608"))
    fa.ferrures3(im, ox, y_av, 176, ep)
    S.sauver("estrade.png", im, (184, 34))


def pieces():
    im = nouveau(420, 64)
    ea.cabochon(im, 0, 0, 42, 12)
    # rail 4×40 : laque sombre, filets d'or éteint
    rect(im, 404, 0, 4, 40, hexc("140C0F"))
    rect(im, 404, 0, 1, 40, hexc(R_OR[2]))
    rect(im, 407, 0, 1, 40, hexc(R_OR[1]))
    # curseur 6×16 : laque rouge, liseré or
    fa.lq(im, 411, 1, 4, 14, "rouge", seed=1, reflet=False)
    fa.liseret(im, 410, 0, 6, 16)
    # toast washi 400×18
    kit.ombre_portee(im, 0, 20, 398, 16, dx=1, dy=2, force=0.5, rayon=0)
    kit.washi(im, 0, 20, 398, 16, "creme", seed=50, bords="nets", ombre=False, taches=False)
    rect(im, 0, 20, 398, 1, hexc("8E1628"))
    # bourse 160×18 : plaquette laquée noire, liseré or, ferrures
    kit.ombre_portee(im, 0, 42, 158, 16, dx=1, dy=2, force=0.55, rayon=0)
    fa.lq(im, 1, 43, 156, 14, "noir", seed=8, intensite=1.2)
    fa.liseret(im, 0, 42, 158, 16)
    fa.ferrures3(im, 0, 42, 158, 16)
    S.sauver("pieces.png", im, (420, 64))


def etagere():
    """Étagère de tissus (128×100) : deux planches sur consoles, rouleaux d'étoffe (tanmono) et piles de
    vêtements pliés, aux couleurs des tenues ; ombre portée sur le mur."""
    im = nouveau(128, 100)
    rs = random.Random(12)
    tons = ["2A3558", "8E1628", "B08442", "3E6A4E", "4A2A66", "6A4E24", "C8BCA4", "1A2442"]
    for (py, n) in ((40, 6), (92, 5)):
        # rouleaux / piles posés sur la planche
        x = 6
        while x < 118:
            kind = rs.choice(("rouleau", "rouleau", "pile"))
            col = rs.choice(tons)
            R = kit.rampe(col, 6, ombre=0.3, lumiere=0.45)
            if kind == "rouleau":
                # rouleaux couchés vus par le bout (spirale d'étoffe), en pyramide 3-2-1
                d = 9
                base = rs.choice((2, 3))
                for rang in range(base):
                    for q in range(base - rang):
                        c3 = rs.choice(tons)
                        R3 = kit.rampe(c3, 6, ombre=0.3, lumiere=0.5)
                        ox = x + rang * (d // 2) + q * d
                        oy = py - (rang + 1) * (d - 1)
                        for j in range(d):
                            for i in range(d):
                                dd = math.hypot(i - (d - 1) / 2, j - (d - 1) / 2)
                                if dd <= d / 2 - 0.1:
                                    ring = int(dd * 1.6) % 2
                                    k = 4 if ring else 2
                                    if i + j < d - 2:
                                        k += 1
                                    if dd > d / 2 - 1.1:
                                        k = 1
                                    im.putpixel((ox + i, oy + j), R3[k])
                        im.putpixel((ox + d // 2, oy + d // 2), R3[0])
                x += base * d + 3
            else:
                w = rs.choice((22, 26))
                y = py
                for k_ in range(rs.choice((3, 4, 5))):
                    c2 = rs.choice(tons)
                    R2 = kit.rampe(c2, 6, ombre=0.3, lumiere=0.45)
                    hh = 4
                    rect(im, x, y - hh, w, hh, R2[3])
                    rect(im, x, y - hh, w, 1, R2[5])
                    rect(im, x, y - 1, w, 1, R2[1])
                    rect(im, x + w - 1, y - hh, 1, hh, R2[1])
                    y -= hh
                x += w + 3
        # planche + consoles
        kit.ombre_portee(im, 0, py, 128, 6, dx=1, dy=2, force=0.6, rayon=1)
        kit.bois(im, 0, py, 128, 6, "moyen", sens="h", seed=py, noeuds=0)
        rect(im, 0, py, 128, 1, hexc("C08850"))
        for cx in (12, 112):
            rect(im, cx - 2, py + 6, 4, 6, hexc("3B2012"))
            rect(im, cx - 2, py + 6, 1, 6, hexc("6E4124"))
    S.sauver("etagere.png", im, (128, 100))


def vignette():
    S.sauver("vignette.png", ea.vignette_lisse(160, 90, force=0.85), (160, 90))


def main():
    mur()
    noren()
    chochin()
    halo()
    enseigne()
    planche()
    onglets()
    etiquettes()
    boutons()
    icones()
    estrade()
    pieces()
    etagere()
    vignette()
    S.bilan()


if __name__ == "__main__":
    main()
