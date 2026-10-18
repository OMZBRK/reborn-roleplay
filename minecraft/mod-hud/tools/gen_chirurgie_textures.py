# -*- coding: utf-8 -*-
"""
gen_chirurgie_textures.py — habillage de l'écran de chirurgie des yeux (prélèvement / greffe), au niveau de la
fiche shinobi « portrait en scène » (DA §4.1) : en-tête suspendu, étapes en plaques à kanji, planches laquées
pendues à des perches, fiole de scellement, trousse d'instruments, jauges, carte de résultat.

DA §6 : tout ce qui ne bouge pas est ici (1 texel = 1 GUI, un blit) ; le code ne dessine que ce qui bouge (lame,
marques, sceau, anneaux, remplissage des jauges, texte). L'œil, la peau, l'orbite et le plateau viennent de
gen_yeux_textures.py (inchangé).

Usage (depuis minecraft/mod-hud) :  python tools/gen_chirurgie_textures.py
Boîte à outils : tools/style/kit.py, tools/fiche_art.py, tools/gen_fiche_textures.py (lecture seule).

Contrat (texels ↔ GUI) — sortie dans src/main/resources/assets/reborn/textures/gui/yeux/ :
- entete_prelevement.png / entete_greffe.png 320×48 : texel (160, 0) = (cx, 0) ; bas de la plaque = y 36.
- etapes.png 104×36 : 4 cellules 26×36 (à venir, en cours, faite, ratée) ; texel (13, 0) = (tx, 36) ;
  plaquette 22×22 en (2, 8) (en cours : (2, 10)).
- perche.png 300×16 : rangées 0-7 perche gauche (embout à droite), 8-15 perche droite ; rangée 0 = y 55.
- panneau_patient.png 208×264, panneau_fiole.png / panneau_trousse.png 208×316 : planche de 200 en (4, 84),
  cordelettes depuis la perche (y 56) ; texture posée en (x − 4, 0).
- fiole_fond.png / fiole_face.png 64×112 : fiole sur sa soucoupe ; fond (verre arrière, liquide) puis le globe,
  puis face (reflets, contour, voile de liquide, bouchon, sceau de papier). Centre du ventre au texel (32, 76).
- trousse.png 168×132 : linge déroulé, 4 instruments (scalpel, aiguille, pince, fil) dans des passants ;
  passant i centré en x = 33 + 34·i. trousse_sel.png 28×112 : lueur dorée d'un passant (texel (14, 0) =
  centre x du passant, y = haut du linge + 10).
- jauge_v.png 13×24 : cadre de jauge verticale 3 tranches (haut 0-7, milieu 8-15 répété, bas 16-23) ; intérieur
  x 3..9 (7 de large).
- jauge_h.png 176×9 : cadre de jauge horizontale ; intérieur (2, 2) 172×5, repères aux quarts.
- pions.png 60×12 : cellules 12×12 : 0 erreur (vide), 1 erreur (sceau rouge), 2 perle vide, 3 perle pleine,
  4 coupe à faire (losange).
- resultat.png 248×112 : carte laquée 240×104 en (4, 2) ; sceaux réussite (coche) / échec (croix) : resultat_sceaux.png
  52×26 (2 × 26) ; aucun kanji (18/10, DA §3).
- bouton.png 184×20 : FERMER 90×18 en (1, 1) des cellules 92×20 (repos, survol).
- cabochon.png 42×12 (touche) ; vignette.png 64×36 (lisse, étirée) ; cecite_bord.png 64×4 (rampe lisse de la
  cécité, opaque à gauche, étirée).
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ICI)
import fiche_art as fa  # noqa: E402
import gen_fiche_textures as gf  # noqa: E402
from fiche_art import kit, hexc, rect, arcade, R_OR, R_LAQUE, R_LAQUE_ROUGE  # noqa: E402
import pictos  # noqa: E402  (aucun kanji décoratif, 18/10)

SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "yeux")
ECRITS = []

CREME = fa.CREME
OR_CLAIR = fa.OR_CLAIR
Y_PERCHE = 56
PW = 200


def nouveau(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def sauver(nom, img, taille, garder_rgb=False):
    if isinstance(img, np.ndarray):
        img = Image.fromarray(img, "RGBA")
    img = img.convert("RGBA")
    assert img.size == tuple(taille), f"{nom} : taille {img.size} au lieu de {taille}"
    a = np.asarray(img).copy()
    if not garder_rgb:
        a[a[..., 3] == 0] = 0
    chemin = os.path.join(SORTIE, nom)
    Image.fromarray(a, "RGBA").save(chemin, optimize=True)
    ECRITS.append((nom, tuple(taille), os.path.getsize(chemin)))


def px_set(img, x, y, c, a=255):
    if 0 <= x < img.width and 0 <= y < img.height:
        c = hexc(c) if isinstance(c, str) else c
        if a >= 255:
            img.putpixel((x, y), c[:3] + (255,))
        else:
            base = img.getpixel((x, y))
            t = a / 255.0
            if base[3] == 0:
                img.putpixel((x, y), c[:3] + (a,))
            else:
                img.putpixel((x, y), tuple(int(round(base[i] * (1 - t) + c[i] * t)) for i in range(3)) + (max(base[3], a),))


# ═════════════════════════════════════ en-tête, étapes, perches ═════════════════════════════════════
def entete(titre):
    """Comme fa.entete_fixe, titre au choix : cordons du plafond, plaque 300×28, agemaki + glands, losanges."""
    px = nouveau(320, 48)
    cx, w, y, h = 160, 300, 8, 28
    x = cx - w // 2
    R = kit.R_CORDE["rouge"]
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 30)
        for k in range(9):
            X = a + sgn * (8 - k)
            rect(px, X, k, 1, 1, hexc(R[3] if k % 2 == 0 else R[2]))
            rect(px, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(R[1]))
    fa.panneau(px, x, y, w, h, seed=7, eclats=False)
    for sgn in (-1, 1):
        fa.anneau_or(px, cx + sgn * (w // 2 - 30), y + 1)
    for sgn in (-1, 1):
        gx = cx + sgn * (w // 2 + 4)
        rect(px, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        fa.agemaki(px, gx, y + 16)
        fa.gland3(px, gx, y + 21, 10)
    arcade(px, cx, 19, titre, 10, CREME, ancre="c", ombre=True)
    demi = kit.arcade_largeur(titre, 10) // 2
    for sgn in (-1, 1):
        x1, x2 = cx + sgn * (demi + 12), cx + sgn * 128
        for X in (x1, x2):
            for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
                rect(px, X + dx, 22 + dy, 1, 1, hexc(c))
        a_, b_ = sorted((x1 + sgn * 6, x2 - sgn * 6))
        if b_ > a_:
            rect(px, a_, 22, b_ - a_ + 1, 1, hexc(R_OR[2]))
    return px


def etapes():
    im = nouveau(104, 36)
    for i, etat in enumerate(("avenir", "encours", "faite", "ratee")):
        cell = nouveau(26, 36)
        ty = 8 + (2 if etat == "encours" else 0)
        fa.corde_v(cell, 13, 0, ty, "or", ep=1)
        if etat == "encours":
            fa.plaquette22(cell, 2, ty, "actif", seed=i)
        elif etat == "faite":
            # plaquette d'or sombre : étape accomplie
            kit.ombre_portee(cell, 2, ty, 22, 22, dx=1, dy=2, force=0.55, rayon=1)
            fa.lq(cell, 3, ty + 1, 20, 20, ["2E2210", "3A2A14", "4A3518", "5A4220", "6A4E24"], seed=5)
            fa.liseret(cell, 2, ty, 22, 22)
            fa.ferrures3(cell, 2, ty, 22, 22)
        elif etat == "ratee":
            kit.ombre_portee(cell, 2, ty, 22, 22, dx=1, dy=2, force=0.55, rayon=1)
            fa.lq(cell, 3, ty + 1, 20, 20, ["1A0608", "240A0E", "2E0D12", "3A1016", "4A141C"], seed=6)
            c = hexc("6A1420")
            rect(cell, 2, ty, 22, 1, c); rect(cell, 2, ty + 21, 22, 1, c)
            rect(cell, 2, ty, 1, 22, c); rect(cell, 23, ty, 1, 22, c)
        else:
            fa.plaquette22(cell, 2, ty, "repos", seed=i)
        kit.anneau_metal(cell, 13, ty, 1)
        im.paste(cell, (i * 26, 0))
    return im


def perche():
    im = nouveau(300, 16)
    g = nouveau(302, 8)
    fa.perche(g, 0, 300, 1, "d")
    im.alpha_composite(g.crop((0, 0, 300, 8)), (0, 0))
    d = nouveau(300, 8)
    fa.perche(d, 0, 300, 1, "g")
    im.alpha_composite(d, (0, 8))
    return im


def panneau_tex(w, h, titre, gland, seed, seed_bandeau):
    im = nouveau(w + 8, 84 + h + 28)
    fa.planche(im, 4, 84, w, h, titre, gland=gland, seed=seed, seed_bandeau=seed_bandeau, y_perche=Y_PERCHE,
               ancrages=(4 + 30, 4 + w - 32))
    return im


# ═════════════════════════════════════ la fiole de scellement ═════════════════════════════════════
FW, FH = 64, 112
BX0, BX1 = 9, 55          # ventre (x inclus..exclus)
BY0, BY1 = 46, 100        # haut du ventre, bas (arrondi compris)
NX0, NX1 = 23, 41         # col
NY0 = 26                  # haut du col
LIQ = 58                  # surface du liquide


def _dans_ventre(x, y):
    cx = (BX0 + BX1) / 2
    rx = (BX1 - BX0) / 2
    if y < BY0 or y >= BY1:
        return False
    if y < BY0 + 8:                       # épaule arrondie vers le col
        t = (y + 0.5 - BY0) / 8.0
        demi = (NX1 - NX0) / 2 + (rx - (NX1 - NX0) / 2) * math.sqrt(max(0.0, 1 - (1 - t) ** 2))
        return abs(x + 0.5 - cx) < demi
    if y >= BY1 - 12:                     # fond arrondi
        t = (y + 0.5 - (BY1 - 12)) / 12.0
        demi = rx * math.sqrt(max(0.0, 1 - t * t))
        return abs(x + 0.5 - cx) < demi
    return abs(x + 0.5 - cx) < rx


def _dans_col(x, y):
    return NY0 <= y < BY0 + 1 and NX0 <= x < NX1


def _verre(x, y):
    return _dans_ventre(x, y) or _dans_col(x, y)


def _bord(x, y):
    if not _verre(x, y):
        return False
    return any(not _verre(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def fiole():
    fond = nouveau(FW, FH)
    face = nouveau(FW, FH)
    cx = (BX0 + BX1) / 2
    # soucoupe laquée (ombre, laque, liseré)
    kit.ombre_portee(fond, 6, 101, 52, 6, dx=1, dy=2, force=0.6, rayon=1)
    for y in range(100, 108):
        demi = 26 * math.sqrt(max(0.0, 1 - ((y + 0.5 - 103.5) / 4.2) ** 2))
        for x in range(FW):
            if abs(x + 0.5 - 32) < demi:
                c = R_LAQUE_ROUGE[3] if y < 103 else (R_LAQUE_ROUGE[2] if y < 106 else R_LAQUE_ROUGE[1])
                px_set(fond, x, y, c)
    for x in range(8, 56):
        px_set(fond, x, 100, R_OR[5] if 14 < x < 50 else R_OR[3])
    # verre arrière (bleuté, translucide) et liquide de conservation (ambre)
    for y in range(FH):
        for x in range(FW):
            if not _verre(x, y):
                continue
            if y >= LIQ and _dans_ventre(x, y):
                d = abs(x + 0.5 - cx) / ((BX1 - BX0) / 2)
                prof = (y - LIQ) / (BY1 - LIQ)
                r_ = [hexc("6A4A1C"), hexc("7E5A22"), hexc("94702E"), hexc("A88238")]
                k = 3 - min(3, int(d * 2.2 + prof * 1.4))
                px_set(fond, x, y, r_[k][:3], 225)
            else:
                px_set(fond, x, y, (150, 190, 205), 46)
    # surface du liquide (ménisque) côté fond
    for x in range(BX0 + 2, BX1 - 2):
        px_set(fond, x, LIQ, "C9A452", 235)
    # --- face : voile de liquide devant le globe, reflets, contour, bouchon, sceau
    for y in range(LIQ + 1, BY1):
        for x in range(FW):
            if _dans_ventre(x, y) and not _bord(x, y):
                px_set(face, x, y, (176, 136, 60), 52)
    for x in range(BX0 + 2, BX1 - 2):
        px_set(face, x, LIQ, "E6C478", 200)
        px_set(face, x, LIQ + 1, "B8913E", 90)
    for y in range(FH):
        for x in range(FW):
            if _bord(x, y):
                lum = x + 0.5 < cx
                px_set(face, x, y, "DCEEF4" if lum else "8FB0BC", 215 if lum else 190)
    # reflets verticaux (grand à gauche, fin à droite) et éclat d'épaule
    for y in range(BY0 + 6, BY1 - 12):
        px_set(face, BX0 + 3, y, "FFFFFF", 150)
        px_set(face, BX0 + 4, y, "FFFFFF", 70 if y % 7 else 120)
        if BY0 + 14 < y < BY1 - 22:
            px_set(face, BX1 - 5, y, "FFFFFF", 60)
    for (x, y) in ((NX0 + 2, NY0 + 3), (NX0 + 2, NY0 + 4), (NX0 + 2, NY0 + 5), (NX0 + 3, NY0 + 4)):
        px_set(face, x, y, "FFFFFF", 150)
    # lèvre du col (anneau de verre épais)
    for x in range(NX0 - 2, NX1 + 2):
        px_set(face, x, NY0 - 1, "B8D6E0", 230)
        px_set(face, x, NY0, "6E8E9A", 230)
    # bouchon de cire rouge, coulures, cordelette dorée
    for y in range(10, NY0 - 1):
        for x in range(NX0 - 1, NX1 + 1):
            t = (x - (NX0 - 1)) / (NX1 - NX0 + 1)
            c = R_LAQUE_ROUGE[7] if t < 0.25 else (R_LAQUE_ROUGE[6] if t < 0.7 else R_LAQUE_ROUGE[4])
            if y == 10:
                c = R_LAQUE_ROUGE[8]
            px_set(face, x, y, c)
    for (x, l) in ((NX0, 4), (NX0 + 5, 7), (NX0 + 11, 3), (NX1 - 2, 5)):
        for k in range(l):
            px_set(face, x, NY0 - 1 + k, R_LAQUE_ROUGE[5] if k < l - 1 else R_LAQUE_ROUGE[3])
    for x in range(NX0 - 1, NX1 + 1):
        px_set(face, x, 9, R_LAQUE_ROUGE[5])
    for x in range(NX0 - 2, NX1 + 2):
        px_set(face, x, 22, R_OR[6] if x % 2 else R_OR[4])
        px_set(face, x, 23, R_OR[3])
    # cordelette pendante + petit gland à droite
    for k in range(9):
        px_set(face, NX1 + 2 + (k // 3), 23 + k, R_OR[5] if k % 2 else R_OR[4])
    fa.gland3(face, NX1 + 5, 32, 8)
    # bandelette de papier scellé (washi) en travers du bouchon, petit cachet vermillon carré (plus de kanji, 18/10)
    for y in range(6, 34):
        for x in range(29, 35):
            c = "EFE4CC" if (x + y) % 5 else "E2D4B4"
            px_set(face, x, y, c)
        px_set(face, 28, y, "B8A888", 160)
    for (x, y) in ((30, 13), (31, 13), (32, 13), (33, 13), (30, 14), (33, 14), (30, 15), (31, 15), (32, 15),
                   (33, 15), (30, 16), (33, 16), (30, 17), (31, 17), (32, 17), (33, 17)):
        px_set(face, x, y, "B5281E")
    # couvercle de laque au-dessus de la cire
    for x in range(NX0 - 2, NX1 + 2):
        px_set(face, x, 7, R_OR[5])
        px_set(face, x, 8, R_LAQUE_ROUGE[2])
    return fond, face


# ═════════════════════════════════════ la trousse d'instruments ═════════════════════════════════════
TW, TH = 168, 132


def trousse():
    im = nouveau(TW, TH)
    x0, y0, w, h = 4, 6, 158, 120
    kit.ombre_portee(im, x0, y0, w, h, dx=2, dy=3, force=0.6, rayon=2)
    kit.washi(im, x0, y0, w, h, ton="creme", seed=13, bords="nets", ombre=False)
    # liserés de brocart rouge en haut et en bas
    for (yy, hh) in ((y0, 6), (y0 + h - 6, 6)):
        fa.lq(im, x0, yy, w, hh, "rouge", seed=yy)
        rect(im, x0, yy + (hh if yy == y0 else -1), w, 1, hexc(R_OR[4]))
    # 0 scalpel : manche de bois laqué, virole d'or, lame d'acier
    c0 = x0 + 29
    for y in range(y0 + 44, y0 + 108):
        rect(im, c0 - 2, y, 4, 1, hexc("3A2418")); px_set(im, c0 - 1, y, "5A3A22"); px_set(im, c0 + 1, y, "24160E")
    rect(im, c0 - 2, y0 + 42, 4, 3, hexc(R_OR[5])); rect(im, c0 - 2, y0 + 42, 4, 1, hexc(R_OR[7]))
    for k, y in enumerate(range(y0 + 16, y0 + 42)):
        wl = 3 if k > 6 else max(1, k // 2)
        rect(im, c0 - 1, y, wl, 1, hexc("B8BEC8"))
        px_set(im, c0 - 1, y, "E8ECF2")
        if wl >= 3:
            px_set(im, c0 + 1, y, "7E8490")
    # 1 aiguille courbe + chas
    c1 = x0 + 63
    for k in range(70):
        t = k / 69.0
        x = c1 + 5 * math.sin(t * math.pi * 0.9) - 2
        y = y0 + 16 + t * 90
        px_set(im, int(round(x)), int(round(y)), "D8DCE4")
        px_set(im, int(round(x)) + 1, int(round(y)), "8C909C")
    for (dx, dy) in ((-1, -2), (0, -3), (1, -2), (-1, -1), (1, -1)):
        px_set(im, c1 - 2 + dx, y0 + 16 + dy, "C8CCD4")
    # 2 pince (deux branches qui se rejoignent en haut)
    c2 = x0 + 97
    for k in range(86):
        y = y0 + 18 + k
        o = 1 + int(k * 4 / 85)
        px_set(im, c2 - o, y, "9CA0AA"); px_set(im, c2 + o, y, "70747E")
        px_set(im, c2 - o - 1, y, "C8CCD4")
    rect(im, c2 - 1, y0 + 16, 3, 3, hexc("70747E"))
    # 3 bobine de fil de suture (soie verte) sur cheville de bois
    c3 = x0 + 131
    rect(im, c3 - 1, y0 + 30, 3, 76, hexc("5A3A22"))
    for y in range(y0 + 36, y0 + 60):
        for x in range(c3 - 7, c3 + 8):
            d = abs(x + 0.5 - c3) / 7.5
            c = "4E9A6A" if d < 0.45 else ("3E8458" if d < 0.8 else "2A6040")
            if (y + x) % 3 == 0 and d < 0.8:
                c = "62B07E"
            px_set(im, x, y, c)
    rect(im, c3 - 8, y0 + 35, 17, 1, hexc("6A4428")); rect(im, c3 - 8, y0 + 60, 17, 1, hexc("6A4428"))
    for k in range(30):
        px_set(im, c3 + 7 + k // 10, y0 + 60 + k, "3E8458")
    # passants (bandes de tissu plus sombres) cousus par-dessus les instruments
    for i in range(4):
        cxp = x0 + 29 + 34 * i
        rect(im, cxp - 8, y0 + 82, 17, 11, hexc("C9B48A"))
        rect(im, cxp - 8, y0 + 82, 17, 1, hexc("E0CFA6"))
        rect(im, cxp - 8, y0 + 92, 17, 1, hexc("8E7A54"))
        for yy in range(y0 + 83, y0 + 92, 2):
            px_set(im, cxp - 8, yy, "8E7A54"); px_set(im, cxp + 8, yy, "8E7A54")
    return im


def trousse_sel():
    im = nouveau(28, 112)
    for y in range(112):
        for x in range(28):
            dx = abs(x + 0.5 - 14) / 14
            dy = abs(y + 0.5 - 56) / 56
            d = max(dx ** 2.2, dy ** 6)
            a = max(0.0, 1 - d)
            if a > 0.02:
                lvl = 0.12 if a < 0.4 else (0.22 if a < 0.75 else 0.32)
                im.putpixel((x, y), (242, 208, 138, int(255 * lvl)))
    for y in (0, 111):
        for x in range(4, 24):
            im.putpixel((x, y), hexc(R_OR[6]))
    for x in (0, 27):
        for y in range(4, 108):
            im.putpixel((x, y), hexc(R_OR[5]))
    return im


# ═════════════════════════════════════ jauges, pions, carte, bouton ═════════════════════════════════════
def jauge_v():
    im = nouveau(13, 24)
    # haut : chapeau d'or ; milieu : rails ; bas : chapeau + perle
    for y in range(24):
        rect(im, 1, y, 11, 1, hexc("120608"))
        rect(im, 2, y, 1, 1, hexc(R_OR[4])); rect(im, 10, y, 1, 1, hexc(R_OR[2]))
        rect(im, 3, y, 7, 1, hexc(R_LAQUE[1]))
    for (yy, top) in ((0, True), (21, False)):
        rect(im, 0, yy, 13, 3, hexc(R_OR[3]))
        rect(im, 0, yy, 13, 1, hexc(R_OR[6] if top else R_OR[4]))
        rect(im, 1, yy + 1, 11, 1, hexc(R_OR[5]))
    rect(im, 3, 3, 7, 1, hexc(R_LAQUE[0]))
    return im


def jauge_h():
    W = 176
    im = nouveau(W, 9)
    rect(im, 0, 0, W, 9, hexc("120608"))
    rect(im, 1, 1, W - 2, 1, hexc(R_OR[4])); rect(im, 1, 7, W - 2, 1, hexc(R_OR[2]))
    rect(im, 1, 1, 1, 7, hexc(R_OR[3])); rect(im, W - 2, 1, 1, 7, hexc(R_OR[2]))
    rect(im, 2, 2, W - 4, 5, hexc(R_LAQUE[1]))
    rect(im, 2, 2, W - 4, 1, hexc(R_LAQUE[0]))
    for x in range(2 + 43, W - 4, 43):
        rect(im, x, 6, 1, 1, hexc(R_OR[2]))
    return im


def pions():
    im = nouveau(60, 12)
    # 0 : emplacement d'erreur vide (petit carré laqué, liseré éteint)
    rect(im, 1, 1, 10, 10, hexc(R_LAQUE[2])); c = hexc(R_OR[2])
    rect(im, 1, 1, 10, 1, c); rect(im, 1, 10, 10, 1, c); rect(im, 1, 1, 1, 10, c); rect(im, 10, 1, 1, 10, c)
    # 1 : sceau d'erreur (laque vermillon, croix crème)
    gf.hanko_px(im, 18, 6, None, taille=10, seed=3)
    for k in range(4):
        px_set(im, 16 + k, 4 + k, "F2E6CC"); px_set(im, 19 - k, 4 + k, "F2E6CC")
    # 2 / 3 : perle vide / pleine
    for (cx, plein) in ((30, False), (42, True)):
        for y in range(12):
            for x in range(cx - 6, cx + 6):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - 6)
                if d <= 3.6:
                    if plein:
                        c = R_OR[7] if d < 1.4 and x < cx else (R_OR[6] if d < 2.6 else R_OR[4])
                        px_set(im, x, y, c)
                    elif d > 2.5:
                        px_set(im, x, y, R_OR[3])
    # 4 : coupe à faire (losange de laque sombre, liseré rouge)
    for k in range(4):
        for j in range(-k, k + 1):
            c = "7A1322" if abs(j) == k else "3A0A12"
            px_set(im, 54 + j, 2 + k, c)
            px_set(im, 54 + j, 9 - k, c)
    return im


def resultat():
    im = nouveau(248, 112)
    fa.panneau_opacite(im, 4, 2, 240, 104, 0.96, seed=31)
    # filet or sous le titre
    rect(im, 24, 36, 200, 1, hexc(R_OR[3]))
    for X in (20, 227):
        for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
            rect(im, X + dx, 36 + dy, 1, 1, hexc(c))
    sc = nouveau(52, 26)
    gf.hanko_px(sc, 13, 13, pictos.encre_px(22, "reussi"), taille=22, seed=8)
    gf.hanko_px(sc, 39, 13, pictos.encre_px(22, "echoue"), taille=22, seed=9,
                ton=["1A0608", "2A0D10", "3A1016", "4A141C", "5A1A22", "6A2028", "7A2630"])
    return im, sc


def bouton():
    im = nouveau(184, 20)
    for i, sv in enumerate((False, True)):
        X = i * 92 + 1
        kit.ombre_portee(im, X, 1, 90, 18, dx=1, dy=2, force=0.55, rayon=1)
        fa.lq(im, X + 1, 2, 88, 16, "rouge" if sv else "noir", seed=12 + i, intensite=1.4)
        fa.liseret(im, X, 1, 90, 18)
        fa.ferrures3(im, X, 1, 90, 18)
        arcade(im, X + 45, 7, "FERMER", 10, OR_CLAIR if sv else CREME, ancre="c", ombre=True)
    return im


def cabochon():
    im = nouveau(42, 12)
    gf.cabochon_fond(im, 0, 0, 40, 10)
    return im


def vignette():
    W, H = 64, 36
    yy, xx = np.mgrid[0:H, 0:W].astype(float) + 0.5
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    al = np.clip((d - 0.45) / 0.85, 0, 1) ** 1.5
    a = np.zeros((H, W, 4), np.uint8)
    a[..., :3] = hexc("0A0608")[:3]
    a[..., 3] = np.round(al * 255).astype(np.uint8)
    return a


def cecite_bord():
    """Rampe d'assombrissement (blanc teint au blit, alpha 1 → 0 de gauche à droite), lisse, étirée."""
    W = 64
    a = np.zeros((4, W, 4), np.uint8)
    a[..., :3] = 255
    t = (np.arange(W) + 0.5) / W
    a[..., 3] = np.round(255 * (1 - t) ** 2).astype(np.uint8)[None, :]
    return a


def main():
    os.makedirs(SORTIE, exist_ok=True)
    sauver("entete_prelevement.png", entete("PRELEVEMENT"), (320, 48))
    sauver("entete_greffe.png", entete("GREFFE"), (320, 48))
    sauver("etapes.png", etapes(), (104, 36))
    sauver("perche.png", perche(), (300, 16))
    sauver("panneau_patient.png", panneau_tex(PW, 152, "PATIENT", "bd", 51, 24), (PW + 8, 84 + 152 + 28))
    sauver("panneau_fiole.png", panneau_tex(PW, 204, "FIOLE", "bg", 52, 696), (PW + 8, 84 + 204 + 28))
    sauver("panneau_trousse.png", panneau_tex(PW, 204, "TROUSSE", "bg", 53, 696), (PW + 8, 84 + 204 + 28))
    fo, fc = fiole()
    sauver("fiole_fond.png", fo, (FW, FH))
    sauver("fiole_face.png", fc, (FW, FH))
    sauver("trousse.png", trousse(), (TW, TH))
    sauver("trousse_sel.png", trousse_sel(), (28, 112))
    sauver("jauge_v.png", jauge_v(), (13, 24))
    sauver("jauge_h.png", jauge_h(), (176, 9))
    sauver("pions.png", pions(), (60, 12))
    r, s = resultat()
    sauver("resultat.png", r, (248, 112))
    sauver("resultat_sceaux.png", s, (52, 26))
    sauver("bouton.png", bouton(), (184, 20))
    sauver("cabochon.png", cabochon(), (42, 12))
    sauver("vignette.png", vignette(), (64, 36), garder_rgb=True)
    sauver("cecite_bord.png", cecite_bord(), (64, 4), garder_rgb=True)
    total = sum(e[2] for e in ECRITS)
    for nom, (w, h), o in ECRITS:
        print(f"{nom:<26} {f'{w}x{h}':>9} {o:>8}")
    print(f"{len(ECRITS)} fichiers, {total} octets dans {os.path.normpath(SORTIE)}")


if __name__ == "__main__":
    main()
