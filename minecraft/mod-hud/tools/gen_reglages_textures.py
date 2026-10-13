# -*- coding: utf-8 -*-
"""
gen_reglages_textures.py — textures des Paramètres Reborn (menu/screens/ConfigShellScreen + menu/settings/*) au
niveau de la fiche shinobi (DA §4.1, §6).

Signature de l'écran : le byōbu — un grand paravent à feuilles d'or (kinpaku), brumes et monts peints, déplié dans
une pièce de laque noire, la nuit ; devant lui, la planche laquée des réglages. En-tête suspendu « PARAMÈTRES »,
huit onglets pendus (plaquettes à kanji), plaque 閉 pour fermer. Contrôles : réglettes d'or, interrupteurs
laqués à cheville d'or, plaques segmentées, boutons-plaques.

1 texel = 1 GUI (échelle « menu » : 960×540 en 1080p). Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_reglages_textures.py

Contrat :
- fond.png 1280×720 : posé centré en x, calé en haut (texel (640, 0) = (W/2, 0)).
- entete.png 540×48 : plaque laquée 520×28 en (10, 8), cordons depuis le haut, agemaki + glands ; titre posé
  par le code (haut des capitales y 19, centré).
- onglets.png 78×36 : 3 cellules 26×36 (repos, survol, actif) ; texel (13, 0) = point d'attache ; plaquette
  22×22 en (2, 8) (actif : (2, 10)) ; kanji posé par le code.
- fermer.png 52×38 : 2 cellules 26×38 (repos, survol), 閉 cuit ; texel (13, 0) = point d'attache.
- planche.png 624×656 : panneau laqué 616×648 en (2, 2) (ombre comprise) ; coupe capD = capB = 18.
- reglette.png 304×30 : rainure 304×10 (0, 0) coupe horizontale capD 6 ; remplissage or 300×4 (2, 12) ;
  curseur 7×12 (0, 18), survolé (8, 18).
- interrupteur.png 44×64 : 4 rangées de 16 (arrêt, marche, arrêt survolé, marche survolé) ; 40×14 en (1, 1).
- segments.png 160×60 : 3 rangées de 20 (repos, survol, actif) ; plaque 156×16 en (1, 1), coupe horizontale
  capD 6.
- boutons.png 600×96 : 4 rangées de 24 (normal, survol, inactif, rouge) ; bouton 596×20 en (1, 1), coupe
  horizontale capD 6.
- pieces.png 120×40 : cabochon 42×12 (0, 0) ; filet à losanges 90×3 (0, 14) (moitié gauche, la droite par
  symétrie côté code) ; flèche 5×7 (0, 20) ; rail 4×40 (100, 0) ; curseur 6×16 (108, 0).
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

S = ea.Sortie("reglages", budget=900_000)

# Feuille d'or assombrie par la nuit (le paravent reste une présence, pas un éclat)
R_KIN = ["1E160C", "2A1E10", "36280F", "443216", "523D1A", "60481E", "6E5424", "7E602A"]


# ═════════════════════════════════════ fond : pièce + byōbu ═════════════════════════════════════
def fond():
    W, H = 1280, 720
    im = nouveau(W, H)
    # pièce de laque noire : lambris verticaux, plus sombre en haut
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    v = 0.30 + 0.12 * (yy / H) + (kit.bruit(W, H, 40, 3, 2, etire=(1, 6)) - 0.5) * 0.10
    idx = kit.quantifier(np.clip(v, 0, 0.999), 6, 0, 0, tramage=0.18)
    coller(im, kit._rampe_vers_rgba(R_LAQUE[:6], idx), 0, 0)
    for x in range(0, W, 64):
        rect(im, x, 0, 1, H, hexc(R_LAQUE[0]))
        rect(im, x + 1, 0, 1, H, hexc(R_LAQUE[4]))

    # byōbu : 8 panneaux de 150, de y 92 à 650, feuilles d'or en damier, brumes, monts, pins
    n, pw = 8, 150
    bx0 = W // 2 - n * pw // 2
    by0, by1 = 92, 650
    bh = by1 - by0
    kin = nouveau(n * pw, bh)
    v = np.full((bh, n * pw), 0.50)
    # feuilles carrées de 15 GUI, ton propre à chacune, joints à peine visibles
    v[::15, :] -= 0.03
    v[:, ::15] -= 0.03
    v += (kit.bruit(n * pw, bh, 60, 5, 2) - 0.5) * 0.06
    # brumes (suyari-gasumi) : bandes horizontales plus claires aux bords arrondis
    for (cy, x0_, x1_, ep) in ((70, 40, 520, 18), (60, 700, 1150, 16), (230, 0, 380, 20), (260, 520, 1000, 22),
                               (420, 120, 640, 18), (450, 820, 1200, 20)):
        for j in range(-ep, ep):
            t = 1 - (j / ep) ** 2
            a = int((x1_ - x0_) * 0.04 * (1 - t))
            if 0 <= cy + j < bh:
                v[cy + j, max(0, x0_ + a):min(n * pw, x1_ - a)] += 0.22 * t ** 0.5
    vv = np.clip(v, 0, 0.999)
    idx = kit.quantifier(vv, len(R_KIN), bx0, by0, tramage=0.12)
    coller(kin, kit._rampe_vers_rgba(R_KIN, idx), 0, 0)
    # monts à l'encre (silhouettes lointaines) et pins
    ka = np.asarray(kin).copy()
    for (base, amp, cell, seed, col) in ((360, 90, 90, 21, "3A2A16"), (400, 60, 50, 22, "2C2012")):
        crest = kit.bruit(n * pw, 1, cell, seed, 2)[0]
        for i in range(n * pw):
            top = int(base - amp * crest[i])
            for j in range(max(0, top), min(bh, base + 40)):
                if not (330 <= j <= 470 and False):
                    m = 0.75 if j > base + 20 else 1.0
                    ka[j, i, :3] = (ka[j, i, :3] * 0.55 + np.array(hexc(col)[:3]) * 0.45 * m).astype(np.uint8)
    kin = Image.fromarray(ka, "RGBA")
    im.alpha_composite(kin, (bx0, by0))
    # montants laqués et charnières d'or
    for k in range(n + 1):
        x = bx0 + k * pw
        rect(im, x - 3, by0 - 4, 6, bh + 8, hexc(R_LAQUE[2]))
        rect(im, x - 3, by0 - 4, 1, bh + 8, hexc(R_LAQUE[6]))
        rect(im, x + 2, by0 - 4, 1, bh + 8, hexc(R_LAQUE[0]))
        if 0 < k < n:
            for hy in (by0 + 60, by0 + bh - 70):
                rect(im, x - 2, hy, 4, 10, hexc(R_OR[4]))
                rect(im, x - 2, hy, 4, 1, hexc(R_OR[7]))
                rect(im, x - 2, hy + 9, 4, 1, hexc(R_OR[2]))
    rect(im, bx0 - 3, by0 - 6, n * pw + 6, 4, hexc(R_LAQUE[3]))
    rect(im, bx0 - 3, by0 - 6, n * pw + 6, 1, hexc(R_OR[3]))
    rect(im, bx0 - 3, by1 + 2, n * pw + 6, 5, hexc(R_LAQUE[1]))
    rect(im, bx0 - 3, by1 + 2, n * pw + 6, 1, hexc(R_OR[3]))
    # pliure : un panneau sur deux légèrement plus sombre (relief du paravent)
    for k in range(n):
        if k % 2:
            fa.assombrir(im, bx0 + k * pw + 3, by0, np.full((bh, pw - 6), 0.18))
    # sol sombre et pénombre
    rect(im, 0, by1 + 7, W, H - by1 - 7, hexc("0A0608"))
    rect(im, 0, by1 + 7, W, 1, hexc(R_LAQUE[4]))
    d = np.sqrt(((xx - W / 2) / (W * 0.62)) ** 2 + ((yy - H * 0.52) / (H * 0.75)) ** 2)
    A = np.clip((d - 0.45) / 0.65, 0, 1) ** 1.4
    fa.assombrir(im, 0, 0, kit.quantifier(A, 5, 0, 0, tramage=0.16) / 4 * 0.75)
    S.sauver("fond.png", im, (W, H), quantifier=True)


# ═════════════════════════════════════ en-tête, onglets, fermer ═════════════════════════════════════
def entete():
    im = nouveau(540, 48)
    cx, w, y, h = 270, 520, 8, 28
    x = cx - w // 2
    R = kit.R_CORDE["rouge"]
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 40)
        for k in range(9):
            X = a + sgn * (8 - k)
            rect(im, X, k, 1, 1, hexc(R[3] if k % 2 == 0 else R[2]))
            rect(im, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(R[1]))
    fa.panneau(im, x, y, w, h, seed=7, eclats=False)
    for sgn in (-1, 1):
        fa.anneau_or(im, cx + sgn * (w // 2 - 40), y + 1)
        gx = cx + sgn * (w // 2 + 4)
        rect(im, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        fa.agemaki(im, gx, y + 16)
        fa.gland3(im, gx, y + 21, 10)
    for sgn in (-1, 1):
        for X in (cx + sgn * 80, cx + sgn * 200):
            for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
                rect(im, X + dx, 22 + dy, 1, 1, hexc(c))
        rect(im, min(cx + sgn * 86, cx + sgn * 194), 22, 109, 1, hexc(R_OR[2]))
    S.sauver("entete.png", im, (540, 48))

    im = nouveau(78, 36)
    for i, etat in enumerate(("repos", "survol", "actif")):
        cell = nouveau(26, 36)
        ty = 8 + (2 if etat == "actif" else 0)
        fa.corde_v(cell, 13, 0, ty, "or", ep=1)
        fa.plaquette22(cell, 2, ty, etat, seed=i)
        kit.anneau_metal(cell, 13, ty, 1)
        im.paste(cell, (i * 26, 0))
    S.sauver("onglets.png", im, (78, 36))

    im = nouveau(52, 38)
    for i in range(2):
        cell = nouveau(26, 38)
        fa.corde_v(cell, 13, 0, 12, "rouge", ep=2)
        fa.plaquette22(cell, 2, 12, "fermer_survol" if i else "fermer")
        kit.anneau_metal(cell, 13, 12, 1)
        gf.kj_px(cell, 13, 15, "閉", gf.CREME_KJ, ombre=hexc("050304"))
        im.paste(cell, (i * 26, 0))
    S.sauver("fermer.png", im, (52, 38))


def planche():
    im = nouveau(624, 656)
    fa.panneau_opacite(im, 2, 2, 616, 648, 0.95, seed=31)
    S.sauver("planche.png", im, (624, 656))


# ═════════════════════════════════════ contrôles ═════════════════════════════════════
def reglette():
    im = nouveau(304, 30)
    # rainure laquée creusée, filets d'or éteint
    fa.lq(im, 1, 1, 302, 8, "noir", seed=2, intensite=0.5, reflet=False)
    rect(im, 0, 0, 304, 1, hexc(R_OR[2])); rect(im, 0, 9, 304, 1, hexc(R_OR[4]))
    rect(im, 0, 0, 1, 10, hexc(R_OR[2])); rect(im, 303, 0, 1, 10, hexc(R_OR[3]))
    rect(im, 1, 1, 302, 1, hexc("050304"))
    for x in range(30, 300, 30):
        rect(im, x, 7, 1, 2, hexc(R_OR[2]))
    # remplissage or (dégradé vertical en crans)
    for j, c in enumerate((R_OR[7], R_OR[6], R_OR[5], R_OR[4])):
        rect(im, 2, 12 + j, 300, 1, hexc(c))
    # curseur : plaquette or 7×12 + survol
    for k, ox in enumerate((0, 8)):
        x, y = ox, 18
        kit.ombre_portee(im, x, y, 7, 12, dx=1, dy=1, force=0.5, rayon=0)
        fa.lq(im, x + 1, y + 1, 5, 10, "rouge" if k else "noir", seed=k, reflet=False, intensite=1.2)
        fa.liseret(im, x, y, 7, 12)
        rect(im, x + 3, y + 3, 1, 6, hexc(R_OR[6] if k else R_OR[4]))
    S.sauver("reglette.png", im, (304, 30))


def interrupteur():
    im = nouveau(44, 64)
    for r, (marche, survol) in enumerate(((False, False), (True, False), (False, True), (True, True))):
        x, y, w, h = 1, r * 16 + 1, 40, 14
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
        if marche:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=r, intensite=1.5 if survol else 1.1)
            fa.liseret(im, x, y, w, h)
        else:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=1.5 if survol else 0.7)
            (fa.liseret if survol else fa.liseret_terne)(im, x, y, w, h)
        # rainure + cheville d'or
        rect(im, x + 5, y + 6, w - 10, 2, hexc("050304"))
        cx = x + w - 11 if marche else x + 3
        kit.ombre_portee(im, cx, y + 2, 8, 10, dx=1, dy=1, force=0.6, rayon=0)
        fa.lq(im, cx + 1, y + 3, 6, 8, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E", "E8C27A"] if marche
              else ["2E2210", "4A3518", "6A4E24", "8E6A30"], seed=r, reflet=False)
        fa.liseret(im, cx, y + 2, 8, 10) if marche else fa.liseret_terne(im, cx, y + 2, 8, 10)
        # voyant : kanji absent, juste un point (marche = or vif)
        rect(im, x + (8 if marche else w - 9), y + 6, 2, 2, hexc(R_OR[7] if marche else R_OR[2]))
    S.sauver("interrupteur.png", im, (44, 64))


def segments():
    im = nouveau(160, 60)
    for r, etat in enumerate(("repos", "survol", "actif")):
        x, y, w, h = 1, r * 20 + 1, 156, 16
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=2, force=0.5, rayon=0)
        if etat == "actif":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=r, intensite=1.3)
            fa.liseret(im, x, y, w, h)
            fa.ferrures3(im, x, y, w, h)
        elif etat == "survol":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=1.7)
            fa.liseret(im, x, y, w, h)
        else:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=0.8)
            fa.liseret_terne(im, x, y, w, h)
    S.sauver("segments.png", im, (160, 60))


def boutons():
    im = nouveau(600, 96)
    for r, etat in enumerate(("normal", "survol", "inactif", "rouge")):
        x, y, w, h = 1, r * 24 + 1, 596, 20
        if etat == "rouge":
            gf.bouton4(im, x, y, "survol", ton="rouge", h=h, w=w)
        else:
            gf.bouton4(im, x, y, etat, ton="noir", h=h, w=w)
    S.sauver("boutons.png", im, (600, 96))


def pieces():
    im = nouveau(120, 40)
    ea.cabochon(im, 0, 0, 42, 12)
    # filet à losanges (moitié gauche, 90×3) : filet or éteint, losanges aux bouts
    rect(im, 3, 15, 87, 1, hexc(R_OR[2]))
    for X in (1, 30):
        for (dx, dy, c) in ((1, 0, R_OR[6]), (0, 1, R_OR[5]), (1, 1, R_OR[7]), (2, 1, R_OR[4]), (1, 2, R_OR[3])):
            rect(im, X + dx, 14 + dy, 1, 1, hexc(c))
    # flèche → 5×7
    for j, row in enumerate(("#....", "##...", "###..", "####.", "###..", "##...", "#....")):
        for i, ch in enumerate(row):
            if ch == "#":
                rect(im, i, 20 + j, 1, 1, hexc(R_OR[6] if i < 2 else R_OR[4]))
    # rail + curseur
    rect(im, 100, 0, 4, 40, hexc("140C0F"))
    rect(im, 100, 0, 1, 40, hexc(R_OR[2]))
    rect(im, 103, 0, 1, 40, hexc(R_OR[1]))
    fa.lq(im, 109, 1, 4, 14, "rouge", seed=1, reflet=False)
    fa.liseret(im, 108, 0, 6, 16)
    S.sauver("pieces.png", im, (120, 40))


def vignette():
    S.sauver("vignette.png", ea.vignette_lisse(160, 90, force=0.8), (160, 90))


def main():
    fond()
    entete()
    planche()
    reglette()
    interrupteur()
    segments()
    boutons()
    pieces()
    vignette()
    S.bilan()


if __name__ == "__main__":
    main()
