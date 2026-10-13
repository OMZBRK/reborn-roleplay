# -*- coding: utf-8 -*-
"""
gen_carte_textures.py — habillage de la carte du monde (map/WorldMapScreen) au niveau de la fiche shinobi.

Signature conservée : le parchemin du lieu sous l'intro en nuages (CloudIntro), glisser / molette / marqueurs.
Ce qui change : le cadre du rouleau (baguettes laquées en haut et en bas, lisières de brocart sur les côtés),
l'en-tête suspendu, la rose des vents, les cartes de washi (lieu choisi, échelle, infobulle), les boutons laqués
et les aides en cabochons. Le parchemin lui-même (textures/gui/map/<id>.png) n'est pas touché.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_carte_textures.py

Contrat :
- cadre.png 64×64 : baguette haute 64×12 (0, 0) (répétée en x) ; baguette basse 64×12 (0, 12) ; embout gauche
  10×14 (0, 24), embout droit 10×14 (12, 24) ; lisière de brocart 8×24 (24, 24) (répétée en y), lisière droite
  8×24 (34, 24).
- entete.png 340×48 : plaque laquée 320×28 en (10, 8), cordons depuis la baguette (y 0), agemaki + glands ;
  titre posé par le code (haut des capitales y 19).
- boussole.png 48×48 : rose des vents (disque laqué, couronne d'or, aiguille vermillon), centre (24, 24).
- washi.png 200×120 : carte de washi 192×112 en (2, 2) (ombre comprise) ; coupe capD = capB = 12 ; bande vermillon
  de 3 en haut.
- etiquette.png 560×40 : étiquette de washi 316×16 en (1, 1) ; bandeau laqué 556×16 en (1, 21) (aides) ;
  coupe horizontale capD 6.
- boutons.png 160×48 : 2 rangées de 24 (rouge, rouge survol) ; bouton 156×20 en (1, 1), coupe horizontale capD 6.
- pieces.png 64×16 : cabochon 42×12 (0, 0) ; sceau doré du lieu choisi 9×9 (44, 0).
- halo.png 64×64 : halo en crans (blanc, l'alpha porte le niveau), teint.
- vignette.png 160×90 : vignette lisse brun d'encre (étirée).
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, coller, R_OR, R_LAQUE, nouveau  # noqa: E402

S = ea.Sortie("carte", budget=300_000)


def disque(s, r, cx=None, cy=None):
    cx = (s - 1) / 2 if cx is None else cx
    cy = (s - 1) / 2 if cy is None else cy
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def cadre():
    im = nouveau(64, 64)
    # baguettes (jiku) : bois laqué noir, filet d'or ; ombre portée sur le papier (côté intérieur)
    rows = ["050304", "4A2E35", "362228", "2A1A1F", "211418", "1A1013", "140C0F", "0F090B", "0A0608"]
    for k, oy in enumerate((0, 12)):
        r0 = oy if k == 0 else oy + 3
        for j, c in enumerate(rows):
            rect(im, 0, r0 + j, 64, 1, hexc(c))
        rect(im, 0, r0 + 2, 64, 1, hexc(R_OR[4]))
        ombre = ((9, 150), (10, 90), (11, 40)) if k == 0 else ((2, 150), (1, 90), (0, 40))
        for j, a in ombre:
            rect(im, 0, oy + j, 64, 1, (40, 24, 12, a))
    # embouts d'or (bouts de baguette)
    for ox in (0, 12):
        fa.lq(im, ox, 24, 10, 14, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E", "E8C27A"], seed=ox, reflet=True)
        fa.liseret(im, ox, 24, 10, 14)
        rect(im, ox + 2, 28, 6, 1, hexc(R_OR[2]))
        rect(im, ox + 2, 33, 6, 1, hexc(R_OR[2]))
    # lisières de brocart (côtés)
    for ox in (24, 34):
        kit.bande_tissu(im, ox, 24, 8, 24, "indigo", "losanges", liseret="or", sens="v", seed=2)
    S.sauver("cadre.png", im, (64, 64))


def entete():
    im = nouveau(340, 48)
    cx, w, y, h = 170, 320, 8, 28
    x = cx - w // 2
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 30)
        fa.corde_v(im, a, 0, y + 1, "rouge", ep=2)
    fa.panneau(im, x, y, w, h, seed=9, eclats=False)
    for sgn in (-1, 1):
        fa.anneau_or(im, cx + sgn * (w // 2 - 30) + 1, y + 1)
        gx = cx + sgn * (w // 2 + 4)
        rect(im, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        fa.agemaki(im, gx, y + 16)
        fa.gland3(im, gx, y + 21, 10)
    S.sauver("entete.png", im, (340, 48))


def boussole():
    s = 48
    im = nouveau(s, s)
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    c = 23.5
    d = np.sqrt((xx - c) ** 2 + (yy - c) ** 2)
    sh = np.zeros((s, s, 4), np.uint8)
    sh[d <= 22] = (40, 24, 12, 110)
    coller(im, sh, 1, 2)
    p = np.zeros((s, s, 4), np.uint8)
    disc = d <= 22.5
    # disque laqué
    v = 0.35 + 0.3 * (1 - (xx + yy) / (2 * s))
    idx = kit.quantifier(np.clip(v, 0, 0.999), 6, 0, 0, tramage=0.3)
    lq = kit._rampe_vers_rgba(R_LAQUE[1:7], idx)
    p[disc] = lq[disc]
    # couronne d'or
    cour = disc & (d > 19.2)
    p[cour & ((xx + yy) < 47)] = hexc(R_OR[6])
    p[cour & ((xx + yy) >= 47)] = hexc(R_OR[3])
    p[disc & (d > 21.6)] = hexc("2A1A10")
    p[disc & (d > 18.6) & (d <= 19.2)] = hexc(R_OR[1])
    # graduations
    for k in range(16):
        a = k * math.pi / 8
        r0 = 15.5 if k % 4 == 0 else 17
        for r in np.arange(r0, 18.6, 0.5):
            X, Y = int(round(c + r * math.cos(a))), int(round(c + r * math.sin(a)))
            p[Y, X] = hexc(R_OR[5] if k % 4 == 0 else R_OR[3])
    coller(im, p, 0, 0)
    # aiguilles : nord vermillon, sud or éteint, est/ouest fines
    def losange(cx_, cy_, dx, dy, larg, col_g, col_d):
        L = int(math.hypot(dx, dy))
        for t in range(L + 1):
            f = t / max(1, L)
            ww = larg * (1 - f) if t > 2 else larg * (t / 3)
            px_, py_ = cx_ + dx * f, cy_ + dy * f
            nx, ny = -dy / L, dx / L
            for q in np.arange(-ww, ww + 0.01, 0.25):
                X, Y = int(math.floor(px_ + nx * q + 0.5)), int(math.floor(py_ + ny * q + 0.5))
                if 0 <= X < s and 0 <= Y < s:
                    im.putpixel((X, Y), hexc(col_g if q < 0 else col_d))
    losange(c, c, 0, -14, 3.2, "E0705A", "AE2C21")
    losange(c, c, 0, 14, 3.2, "B08442", "6A4E24")
    losange(c, c, 13, 0, 2.0, "8E6A30", "5A4220")
    losange(c, c, -13, 0, 2.0, "8E6A30", "5A4220")
    for (X, Y, col) in ((23, 23, R_OR[7]), (24, 23, R_OR[6]), (23, 24, R_OR[5]), (24, 24, R_OR[4])):
        rect(im, X, Y, 1, 1, hexc(col))
    S.sauver("boussole.png", im, (s, s))


def washi():
    im = nouveau(200, 120)
    kit.ombre_portee(im, 2, 2, 192, 112, dx=2, dy=3, force=0.45, rayon=1)
    kit.washi(im, 2, 2, 192, 112, "creme", seed=88, bords="nets", ombre=False, taches=False)
    rect(im, 2, 2, 192, 3, hexc("8E1628"))
    rect(im, 2, 5, 192, 1, hexc("5C0E18"))
    S.sauver("washi.png", im, (200, 120))

    im = nouveau(560, 40)
    kit.ombre_portee(im, 1, 1, 316, 16, dx=1, dy=2, force=0.45, rayon=0)
    kit.washi(im, 1, 1, 316, 16, "creme", seed=89, bords="nets", ombre=False, taches=False)
    # bandeau laqué (aides) : laque noire, liseré or
    kit.ombre_portee(im, 1, 21, 556, 16, dx=1, dy=2, force=0.5, rayon=0)
    fa.lq(im, 2, 22, 554, 14, "noir", seed=3, intensite=1.1)
    fa.liseret(im, 1, 21, 556, 16)
    fa.ferrures3(im, 1, 21, 556, 16)
    S.sauver("etiquette.png", im, (560, 40))


def boutons():
    im = nouveau(160, 48)
    for r, etat in enumerate(("normal", "survol")):
        gf.bouton4(im, 1, r * 24 + 1, etat, ton="rouge", h=20, w=156)
    S.sauver("boutons.png", im, (160, 48))


def pieces():
    im = nouveau(64, 16)
    ea.cabochon(im, 0, 0, 42, 12)
    m = disque(9, 4.4)
    p = np.zeros((9, 9, 4), np.uint8)
    p[m] = hexc(R_OR[5])
    yy, xx = np.mgrid[0:9, 0:9]
    p[m & ((xx + yy) < 6)] = hexc(R_OR[7])
    p[m & ((xx + yy) > 10)] = hexc(R_OR[3])
    p[3:6, 3:6] = hexc("A0182B")
    p[4, 4] = hexc("E26474")
    coller(im, p, 44, 0)
    S.sauver("pieces.png", im, (64, 16))


def halo():
    A = fa.masque_lumiere(31, 31, 5, expo=1.6)
    h, w = A.shape
    p = np.zeros((64, 64, 4), np.uint8)
    p[..., :3] = 255
    p[:h, :w, 3] = np.clip(A * 255, 0, 255).astype(np.uint8)
    S.sauver("halo.png", p, (64, 64))


def vignette():
    S.sauver("vignette.png", ea.vignette_lisse(160, 90, force=0.55, couleur=(40, 24, 12)), (160, 90))


def main():
    cadre()
    entete()
    boussole()
    washi()
    boutons()
    pieces()
    halo()
    vignette()
    S.bilan()


if __name__ == "__main__":
    main()
