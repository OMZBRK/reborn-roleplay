# -*- coding: utf-8 -*-
"""
gen_bibliotheque_textures.py — textures de la bibliothèque à tirage, de la lecture d'un rouleau et des
réglages staff (parchemin/BibliothequeScreen, LectureScreen, BibliothequeStaffScreen), au niveau de la fiche.

Signature de l'écran : le meuble à casiers (tansu) de bois sombre, linteau de laque noire au cordon d'or,
ferrures d'or (aucun kanji depuis le 18/10, DA §3) ; chaque rouleau (modèle 3D SNR, dessiné par le code) couché dans son casier, son étiquette
de rang (fuda) pendue au coin. Survol : fiche du rouleau sur washi. Lecture : makimono de washi entre deux
baguettes, brocart à la couleur du rang. Réglages : planche laquée, plaques-boutons, réglettes d'or.

Usage (depuis minecraft/mod-hud) :  python tools/gen_bibliotheque_textures.py

Contrat (1 texel = 1 GUI) — bibliothèque :
- entete.png 360×48 : plaque laquée 340×28 en (10, 8), cordons, agemaki ; nom en code (haut des
  capitales y 19, centré en x 180).
- meuble.png 376×274 : linteau (0..372, 0..28), corps (8..364, 28..258), socle jusqu'à 270, ombre.
  Casier (c, r) : (24 + 110·c, 44 + 68·r), 104×62.
- casiers.png 416×62 : cellules 104×62 : 0 ouvert (garni), 1 ouvert vide, 2 fermé, 3 survol (calque).
- fudas.png 70×20 : cellules 14×20 (D C B A S) ; fil depuis (6, 0), étiquette 10×13 en (1, 6).
- sceaux.png 240×28 : sceaux de rang 18 (cellules 20, centre (9, 9)) puis 26 (cellules 28 dès x 100,
  centre (13, 13)).
- carte.png 176×300 : washi 168×294 en (2, 2) (coupe verticale, capB 16).
- brocart.png 440×40 : rangées de 8 (D C B A S), brocart seigaiha à lisières d'or.
- pieces.png 210×50 : trait de pinceau vermillon 160×4 (0, 0) ; cabochon 42×12 (0, 6) ;
  jauge 204×12 (0, 20) ; remplissage or 196×6 (0, 34) ; remplissage laque 196×6 (0, 42).
- reglages.png 104×38 : 4 cellules 26×38 (réglages repos / survol, journal repos / survol : pictos roue et
  registre), plaque 22×22 en (2, 12).
Bibliothèques v2 :
- onglets.png 140×70 : onglets pendus, rangées de 20 (repos, survol, actif), plaque 136×16 en (1, 1)
  (coupe horizontale, capD 6) ; perche de bambou laqué 140×7 en (0, 61) (coupe horizontale, capD 6).
- acces.png 84×28 : sceau à cadenas « réservé aux membres » 24×24 centré en (14, 14) vermillon (non-membre),
  en (42, 14) laque noire et or (membre) ; cadenas 9×11 en (58, 0) vermillon (fermé), en (70, 0) or (ouvert).
- registre.png 336×268 : washi 328×260 en (2, 2) (coupe, capD 8, capB 16) — dépôt et journal.
- toast.png 264×20 : plaquette 260×16 (coupe horizontale, capD 8).
- vignette.png 160×90 : vignette lisse (étirée).
Lecture :
- rouleau.png 448×232 : washi 440×220 en (4, 4) ; le brocart (8 de haut) est posé par le code.
- baguettes.png 28×244 : cellules 14×244 (bois et embouts sombres ; embouts d'or pour A et S).
- perles.png 30×17 : 9×9 (fait, à faire, prochaine) en y 0 ; 7×7 en y 10.
- signe.png 120×12 : étiquette de signe (coupe horizontale, capD 4).
- bouton.png 112×66 : cellules 112×22 (prêt, survol, inactif), bouton 110×20 en (0, 0) (capD 6).
Réglages staff :
- panneau.png 576×348 : planche 568×340 en (2, 2) (coupe, capD = capB = 16).
- plaques.png 164×80 : rangées de 20 (normal, survol, actif, inactif), plaque 160×16 en (1, 1) (capD 6).
- reglette.png 404×30 : rainure 404×10 (0, 0), or 400×4 (2, 12), curseur 7×12 (0, 18) et survolé (8, 18).
- chevilles.png 52×12 : − repos, − survol, + repos, + survol (cellules 13×12, cheville 12×11).
- champ.png 152×14 : champ de recherche (coupe horizontale, capD 4).
- mini.png 48×26 : petit casier d'aperçu.
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, arcade, coller, R_OR, R_LAQUE, nouveau  # noqa: E402
import pictos  # noqa: E402  (aucun kanji décoratif, 18/10)

S = ea.Sortie("bibliotheque", budget=600_000)
RANGS = ea.RANGS
RCOL = {r: ea.RANG_COL[r] for r in RANGS}
CW, CH = 104, 62


def assombrir(im, x, y, w, h, k):
    a = np.asarray(im).copy()
    reg = a[y:y + h, x:x + w].astype(float)
    reg[..., :3] *= k
    a[y:y + h, x:x + w] = np.clip(reg, 0, 255).astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def degrade_v(im, x, y, w, h, couleur, a0, a1):
    """Voile vertical à paliers d'1 GUI (pas de flou) : a0 en haut, a1 en bas."""
    for j in range(h):
        t = j / max(1, h - 1)
        a = a0 + (a1 - a0) * t
        if a > 0.004:
            rect(im, x, y + j, w, 1, hexc(couleur)[:3] + (int(255 * a),))


# ═════════════════════════════════════ en-tête ═════════════════════════════════════
def entete():
    px = nouveau(360, 48)
    cx, w, y, h = 180, 340, 8, 28
    x = cx - w // 2
    R = kit.R_CORDE["rouge"]
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 34)
        for k in range(9):
            X = a + sgn * (8 - k)
            rect(px, X, k, 1, 1, hexc(R[3] if k % 2 == 0 else R[2]))
            rect(px, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(R[1]))
    fa.panneau(px, x, y, w, h, seed=5, eclats=False)
    for sgn in (-1, 1):
        fa.anneau_or(px, cx + sgn * (w // 2 - 34), y + 1)
        gx = cx + sgn * (w // 2 + 4)
        rect(px, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        fa.agemaki(px, gx, y + 16)
        fa.gland3(px, gx, y + 21, 10)
    S.sauver("entete.png", px, (360, 48))


# ═════════════════════════════════════ meuble ═════════════════════════════════════
def rivet(px, x, y):
    rect(px, x, y, 2, 2, hexc(R_OR[4]))
    rect(px, x, y, 1, 1, hexc(R_OR[7]))
    rect(px, x + 1, y + 1, 1, 1, hexc(R_OR[2]))


def meuble():
    im = nouveau(376, 274)
    bx, by, bw, bh = 8, 28, 356, 230
    kit.ombre_portee(im, bx, by, bw, bh + 12, dx=3, dy=4, force=0.6, rayon=2)
    # corps : bois sombre veiné, montants verticaux
    kit.bois(im, bx, by, bw, bh, "sombre", sens="h", seed=11, noeuds=2)
    for c in range(4):
        X = bx + 10 + c * 110
        kit.bois(im, X, by, 6, bh, "sombre", sens="v", seed=20 + c, noeuds=0)
    kit.bois(im, bx, by, 10, bh, "moyen", sens="v", seed=31, noeuds=0)
    kit.bois(im, bx + bw - 10, by, 10, bh, "moyen", sens="v", seed=32, noeuds=0)
    im = assombrir(im, bx, by, 10, bh, 0.62)
    im = assombrir(im, bx + bw - 10, by, 10, bh, 0.62)
    # traverses
    for r in range(4):
        Y = by + 10 + r * 68
        kit.bois(im, bx + 10, Y, bw - 20, 6, "sombre", sens="h", seed=40 + r, noeuds=0)
    # creux des casiers (sous les cellules dessinées par le code)
    for r in range(3):
        for c in range(3):
            rect(im, 24 + 110 * c, 44 + 68 * r, CW, CH, hexc("0C0705"))
    # chants clairs et ombres des montants
    rect(im, bx, by, bw, 1, hexc("744528"))
    rect(im, bx, by + bh - 1, bw, 1, hexc("120805"))
    rect(im, bx, by, 1, bh, hexc("623920"))
    rect(im, bx + bw - 1, by, 1, bh, hexc("120805"))
    # ferrures d'angle du corps et rivets aux croisements
    fa.kanagus(im, bx, by, bw, bh)
    for r in range(4):
        for c in range(4):
            rivet(im, bx + 12 + c * 110, by + 12 + r * 68)
    # socle et pieds
    sy = by + bh
    kit.bois(im, bx + 4, sy, bw - 8, 5, "moyen", sens="h", seed=51, noeuds=0, biseau=True)
    im = assombrir(im, bx + 4, sy, bw - 8, 5, 0.55)
    for X in (bx + 8, bx + bw - 26):
        kit.bois(im, X, sy + 5, 18, 7, "sombre", sens="h", seed=X, noeuds=0)
        rect(im, X, sy + 10, 18, 2, hexc(R_OR[4]))
        rect(im, X, sy + 10, 18, 1, hexc(R_OR[6]))
        rect(im, X, sy + 11, 18, 1, hexc(R_OR[2]))
    # linteau de laque noire, liseré or, filet rouge, embouts d'or
    fa.lq(im, 0, 0, 372, 28, "noir", seed=3)
    fa.liseret(im, 0, 0, 372, 28)
    rect(im, 3, 3, 366, 1, hexc("6A1420"))
    rect(im, 3, 24, 366, 1, hexc("6A1420"))
    rect(im, 0, 28, 372, 1, hexc("0A0506"))
    for X in (0, 372 - 10):
        fa.kanagus(im, X, 0, 10, 28, deborde=0)
    # cordon d'or continu sur le linteau, petit losange d'or au centre (plus de plaque à kanji, 18/10)
    for (dx, dy, c) in ((0, -2, R_OR[6]), (-1, -1, R_OR[6]), (0, -1, R_OR[7]), (1, -1, R_OR[5]), (-2, 0, R_OR[5]),
                        (-1, 0, R_OR[6]), (0, 0, R_OR[7]), (1, 0, R_OR[5]), (2, 0, R_OR[4]), (-1, 1, R_OR[4]),
                        (0, 1, R_OR[5]), (1, 1, R_OR[3]), (0, 2, R_OR[3])):
        rect(im, 186 + dx, 14 + dy, 1, 1, hexc(c))
    for sgn in (-1, 1):
        x0 = 186 + sgn * 5
        x1 = 186 + sgn * 150
        rect(im, min(x0, x1), 14, abs(x1 - x0), 1, hexc(R_OR[2]))
        gf.losange_or(im, x1 - 1, 13)
        gf.losange_or(im, x0 - 1, 13)
    S.sauver("meuble.png", im, (376, 274))


# ═════════════════════════════════════ casiers ═════════════════════════════════════
def casier_ouvert(vide=False, seed=0):
    c = nouveau(CW, CH)
    kit.bois(c, 0, 0, CW, CH, "sombre", sens="v", seed=60 + seed, noeuds=0, planches=0, biseau=False)
    c = assombrir(c, 0, 0, CW, CH, 0.42)
    # lames verticales du fond
    for X in range(13, CW, 13):
        rect(c, X, 0, 1, CH - 7, hexc("080403"))
    # lumière chaude venue du haut (paliers), ombre du haut du casier
    degrade_v(c, 0, 0, CW, 7, "000000", 0.62, 0.0)
    fa.lumiere(c, CW / 2, 0, 56, (242, 196, 130), force=0.16, ry=44, niveaux=3, expo=1.8)
    # côtés dans l'ombre
    rect(c, 0, 0, 3, CH, (0, 0, 0, 120))
    rect(c, CW - 3, 0, 3, CH, (0, 0, 0, 90))
    # plancher du casier et sa lèvre
    kit.bois(c, 0, CH - 7, CW, 7, "moyen", sens="h", seed=70 + seed, noeuds=0, biseau=False)
    c = assombrir(c, 0, CH - 7, CW, 7, 0.75)
    rect(c, 0, CH - 7, CW, 1, hexc("B08454"))
    rect(c, 0, CH - 6, CW, 1, hexc("6E4124"))
    rect(c, 0, CH - 1, CW, 1, hexc("1A0D07"))
    if vide:
        rs = np.random.RandomState(5 + seed)
        for _ in range(16):
            x = rs.randint(6, CW - 6)
            rect(c, x, CH - 8, rs.randint(1, 3), 1, (200, 180, 160, 70))
        # marque claire d'un rouleau parti
        rect(c, 30, CH - 8, 44, 1, (230, 200, 160, 40))
    return c


def casier_ferme():
    c = nouveau(CW, CH)
    for i in range(2):
        X = i * 52
        kit.bois(c, X, 0, 52, CH, "moyen", sens="v", seed=80 + i, noeuds=1)
        c = assombrir(c, X, 0, 52, CH, 0.62)
        # cadre de la porte coulissante
        rect(c, X, 0, 52, 3, hexc("2C170E")); rect(c, X, CH - 3, 52, 3, hexc("2C170E"))
        rect(c, X, 0, 3, CH, hexc("2C170E")); rect(c, X + 49, 0, 3, CH, hexc("2C170E"))
        rect(c, X + 1, 1, 50, 1, hexc("6E4124"))
        rect(c, X + 3, CH // 2 - 1, 46, 2, hexc("2C170E"))
        rect(c, X + 3, CH // 2 - 1, 46, 1, hexc("5C351D"))
    rect(c, 51, 0, 2, CH, hexc("0E0705"))
    for X in (44, 59):
        kit.anneau_metal(c, X, CH // 2, 2)
    return c


def casier_survol():
    c = nouveau(CW, CH)
    fa.lumiere(c, CW / 2, CH - 8, 46, (242, 212, 154), force=0.20, ry=16, niveaux=3, expo=1.6)
    fa.lumiere(c, CW / 2, 0, 50, (242, 212, 154), force=0.10, ry=30, niveaux=2, expo=1.6)
    fa.liseret(c, 0, 0, CW, CH)
    rect(c, 1, 1, CW - 2, 1, hexc(R_OR[3]))
    fa.kanagus_mini(c, 0, 0, CW, CH)
    return c


def casiers():
    im = nouveau(4 * CW, CH)
    im.alpha_composite(casier_ouvert(False, 0), (0, 0))
    im.alpha_composite(casier_ouvert(True, 1), (CW, 0))
    im.alpha_composite(casier_ferme(), (2 * CW, 0))
    im.alpha_composite(casier_survol(), (3 * CW, 0))
    S.sauver("casiers.png", im, (4 * CW, CH))


# ═════════════════════════════════════ fudas, sceaux, brocart ═════════════════════════════════════
def fudas():
    im = nouveau(70, 20)
    for i, r in enumerate(RANGS):
        x0 = i * 14
        for y in range(0, 6):
            rect(im, x0 + 6, y, 1, 1, hexc("8A6A48" if y % 2 else "B08A5E"))
        kit.ombre_portee(im, x0 + 1, 6, 10, 13, dx=1, dy=1, force=0.55, rayon=0)
        kit.washi(im, x0 + 1, 6, 10, 13, "creme", seed=i, bords="nets", ombre=False, fibres=False, taches=False)
        col = hexc(RCOL[r])
        rect(im, x0 + 1, 6, 10, 2, col)
        rect(im, x0 + 1, 8, 10, 1, tuple(int(v * 0.6) for v in col[:3]) + (255,))
        lw = kit.arcade_largeur(r, 10)
        arcade(im, x0 + 1 + (10 - lw + 1) // 2, 10, r, 10, hexc("2A1A10"))
        rect(im, x0 + 5, 6, 3, 1, hexc(R_OR[5]))
    S.sauver("fudas.png", im, (70, 20))


def sceaux():
    im = nouveau(240, 28)
    for i, r in enumerate(RANGS):
        gf.sceau_rang4(im, i * 20 + 9, 9, r, 18)
        gf.sceau_rang4(im, 100 + i * 28 + 13, 13, r, 26)
    S.sauver("sceaux.png", im, (240, 28))


def brocart():
    im = nouveau(440, 40)
    for i, r in enumerate(RANGS):
        kit.bande_tissu(im, 0, i * 8, 440, 8, RCOL[r], motif="seigaiha", liseret="or", seed=i)
    S.sauver("brocart.png", im, (440, 40))


# ═════════════════════════════════════ carte, pièces ═════════════════════════════════════
def carte():
    im = nouveau(176, 300)
    kit.washi(im, 2, 2, 168, 294, "creme", seed=17, bords="nets", ombre=True, fibres=True, taches=True)
    S.sauver("carte.png", im, (176, 300))


def trait_pinceau(im, x, y, w):
    rs = np.random.RandomState(3)
    for i in range(w):
        u = i / max(1, w - 1)
        ep = 1 if u < 0.04 or u > 0.9 else (3 if 0.12 < u < 0.6 else 2)
        y0 = y + (3 - ep) // 2 + (1 if rs.rand() < 0.05 else 0)
        for j in range(ep):
            if u > 0.75 and rs.rand() < (u - 0.75) * 2.5:
                continue
            rect(im, x + i, y0 + j, 1, 1, hexc("A0182B" if j < ep - 1 or ep == 1 else "7A1322"))


def pieces():
    im = nouveau(210, 50)
    trait_pinceau(im, 0, 0, 160)
    ea.cabochon(im, 0, 6, 40, 10)
    # jauge de la sacoche : rainure laquée, liseré or
    kit.ombre_portee(im, 0, 20, 202, 10, dx=1, dy=1, force=0.5, rayon=0)
    fa.lq(im, 1, 21, 200, 8, ["050304", "0A0608", "0F090B", "140C0F"], seed=2, reflet=False)
    rect(im, 0, 20, 202, 1, hexc(R_OR[4])); rect(im, 0, 29, 202, 1, hexc(R_OR[2]))
    rect(im, 0, 20, 1, 10, hexc(R_OR[3])); rect(im, 201, 20, 1, 10, hexc(R_OR[2]))
    fa.lq(im, 0, 34, 196, 6, ["6A4E24", "8E6A30", "B08442", "D9A95E", "E8C27A"], seed=4)
    rect(im, 0, 34, 196, 1, hexc(R_OR[7]))
    fa.lq(im, 0, 42, 196, 6, "rouge", seed=5)
    rect(im, 0, 42, 196, 1, hexc("E26474"))
    S.sauver("pieces.png", im, (210, 50))


def reglages():
    im = nouveau(104, 38)
    for k, ic in enumerate(("reglages", "journal")):
        for i in range(2):
            cell = nouveau(26, 38)
            fa.corde_v(cell, 13, 0, 12, "or", ep=1)
            kit.ombre_portee(cell, 2, 12, 22, 22, dx=1, dy=2, force=0.55, rayon=1)
            fa.lq(cell, 3, 13, 20, 20, "noir", seed=4 + k, intensite=1.8 if i else 1.0)
            fa.liseret(cell, 2, 12, 22, 22)
            kit.anneau_metal(cell, 13, 12, 1)
            pictos.ico_milieu(cell, 13, 23, ic, ea.OR_CLAIR if i else ea.OR, ombre=hexc("050304"))
            im.paste(cell, ((2 * k + i) * 26, 0))
    S.sauver("reglages.png", im, (104, 38))


# ═════════════════════════════════════ bibliothèques v2 ═════════════════════════════════════
def onglets():
    """Onglets pendus FONDS / ARRIVAGES (comme l'échoppe) et la perche de bambou laqué qui les porte."""
    im = nouveau(140, 70)
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
    # perche : bambou laqué, embouts de laiton aux deux bouts
    fa.perche(im, 0, 140, 62, "d")
    fa.lq(im, 0, 61, 6, 7, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=3)
    fa.liseret(im, 0, 61, 6, 7)
    S.sauver("onglets.png", im, (140, 70))


def cadenas(im, x, y, cols, ouvert=False):
    """Cadenas 9×11 : anse (ouverte : décalée vers le haut et à droite), corps biseauté, trou de serrure."""
    fonce, moyen, clair, trou = (hexc(c) for c in cols)
    dy = 0 if not ouvert else -1
    for (X, Y) in ((2, 1), (2, 2), (2, 3), (3, 0), (4, 0), (5, 0), (6, 1), (6, 2), (6, 3)):
        if ouvert and X == 2 and Y >= 2:
            continue
        rect(im, x + X, y + Y + dy + 1, 1, 1, moyen)
    rect(im, x, y + 5, 9, 6, fonce)
    rect(im, x + 1, y + 5, 7, 5, moyen)
    rect(im, x + 1, y + 5, 7, 1, clair)
    rect(im, x + 1, y + 5, 1, 5, clair)
    rect(im, x + 4, y + 7, 1, 2, trou)


def acces():
    im = nouveau(84, 28)
    gf.hanko_px(im, 14, 14, pictos.encre_px(24, "cadenas"), taille=24, seed=31, usure=0.10)
    gf.hanko_px(im, 42, 14, pictos.encre_px(24, "cadenas"), taille=24, ton=kit.R_LAQUE[1:8], seed=32, encre=ea.OR_CLAIR, usure=0.04)
    fa.liseret(im, 30, 2, 24, 24)
    cadenas(im, 58, 0, ("4E0C0B", "AE2C21", "E0705A", "2A0606"))
    cadenas(im, 70, 0, ("4A3518", "B08442", "F2D59A", "2E2210"), ouvert=True)
    S.sauver("acces.png", im, (84, 28))


def registre():
    im = nouveau(336, 268)
    kit.washi(im, 2, 2, 328, 260, "creme", seed=29, bords="nets", ombre=True, fibres=True, taches=True)
    S.sauver("registre.png", im, (336, 268))


def toast():
    im = nouveau(264, 20)
    gf.plaquette4(im, 0, 0, 260, 16, "noir", seed=21)
    fa.ferrures3(im, 0, 0, 260, 16)
    S.sauver("toast.png", im, (264, 20))


# ═════════════════════════════════════ lecture ═════════════════════════════════════
def rouleau():
    im = nouveau(448, 232)
    kit.washi(im, 4, 4, 440, 220, "creme", seed=23, bords="nets", ombre=True, fibres=True, taches=True)
    # ombre douce des baguettes sur le papier, sous le brocart (paliers)
    for k, a in enumerate((0.22, 0.14, 0.08, 0.04)):
        rect(im, 4 + k, 4, 1, 220, (40, 24, 12, int(255 * a)))
        rect(im, 443 - k, 4, 1, 220, (40, 24, 12, int(255 * a)))
    S.sauver("rouleau.png", im, (448, 232))


def baguettes():
    im = nouveau(28, 244)
    for i, noble in enumerate((False, True)):
        X = i * 14
        kit.ombre_portee(im, X + 3, 6, 8, 232, dx=2, dy=2, force=0.5, rayon=1)
        kit.bois(im, X + 3, 6, 8, 232, "moyen", sens="v", seed=90 + i, noeuds=0)
        rect(im, X + 4, 6, 1, 232, hexc("C48A58"))
        rect(im, X + 10, 6, 1, 232, hexc("2C170C"))
        for (y0, h) in ((0, 7), (237, 7)):
            if noble:
                fa.lq(im, X + 1, y0, 12, h, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E", "E8C27A"], seed=y0)
                fa.liseret(im, X + 1, y0, 12, h)
            else:
                fa.lq(im, X + 1, y0, 12, h, "noir", seed=y0, intensite=1.4)
                rect(im, X + 1, y0, 12, 1, hexc(R_OR[3])); rect(im, X + 1, y0 + h - 1, 12, 1, hexc(R_OR[1]))
    S.sauver("baguettes.png", im, (28, 244))


def perle(im, x, y, t, etat):
    cx = cy = (t - 1) / 2
    yy, xx = np.mgrid[0:t, 0:t].astype(float)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    m = d <= t / 2 - 0.2
    p = np.zeros((t, t, 4), np.uint8)
    if etat == "fait":
        p[m] = hexc("A0182B")
        p[m & ((xx + yy) < t - 2)] = hexc("B51F33")
        p[fa._bord(m)] = hexc("4E0D17")
        p[int(cy) - 1, int(cx) - 1] = hexc("E26474")
    elif etat == "afaire":
        p[fa._bord(m)] = (70, 40, 16, 150)
        p[m & ~fa._bord(m)] = (70, 40, 16, 28)
    else:
        p[m] = (70, 40, 16, 28)
        p[fa._bord(m)] = hexc(R_OR[5])
        p[m & (d <= 1.2)] = hexc(R_OR[6])
    coller(im, p, x, y)


def perles():
    im = nouveau(30, 17)
    for i, e in enumerate(("fait", "afaire", "prochaine")):
        perle(im, i * 10, 0, 9, e)
        perle(im, i * 8, 10, 7, e)
    S.sauver("perles.png", im, (30, 17))


def signe():
    im = nouveau(120, 12)
    kit.washi(im, 0, 0, 120, 11, "ancien", seed=8, bords="nets", ombre=False, fibres=True, taches=False)
    rect(im, 0, 0, 120, 1, hexc("8C6E48")); rect(im, 0, 10, 120, 1, hexc("6B5434"))
    rect(im, 0, 0, 1, 11, hexc("8C6E48")); rect(im, 119, 0, 1, 11, hexc("6B5434"))
    rect(im, 1, 11, 120, 1, (40, 24, 12, 60))
    S.sauver("signe.png", im, (120, 12))


def bouton():
    im = nouveau(112, 66)
    for i, etat in enumerate(("actif", "survol", "inactif")):
        cell = nouveau(112, 22)
        if etat == "survol":
            gf.bouton4(cell, 0, 0, "survol", ton="rouge", h=20, w=110)
        elif etat == "actif":
            gf.bouton4(cell, 0, 0, "normal", ton="rouge", h=20, w=110)
        else:
            gf.bouton4(cell, 0, 0, "inactif", ton="noir", h=20, w=110)
        im.paste(cell, (0, i * 22))
    S.sauver("bouton.png", im, (112, 66))


# ═════════════════════════════════════ réglages staff ═════════════════════════════════════
def panneau_staff():
    im = nouveau(576, 348)
    fa.panneau_opacite(im, 2, 2, 568, 340, 0.95, seed=71, eclats=False)
    S.sauver("panneau.png", im, (576, 348))


def plaques():
    im = nouveau(164, 80)
    for i, etat in enumerate(("normal", "survol", "actif", "inactif")):
        cell = nouveau(164, 20)
        ea.bouton(cell, 1, 1, 160, 16, etat)
        im.paste(cell, (0, i * 20))
    S.sauver("plaques.png", im, (164, 80))


def reglette():
    im = nouveau(404, 30)
    fa.lq(im, 0, 0, 404, 10, ["050304", "0A0608", "0F090B", "140C0F", "1A1013"], seed=6, reflet=False)
    rect(im, 0, 0, 404, 1, hexc(R_OR[2])); rect(im, 0, 9, 404, 1, hexc(R_OR[4]))
    rect(im, 0, 0, 1, 10, hexc(R_OR[2])); rect(im, 403, 0, 1, 10, hexc(R_OR[3]))
    for X in range(2, 400, 10):
        rect(im, X, 1, 1, 3 if (X - 2) % 50 else 4, hexc(R_OR[2] if (X - 2) % 50 else R_OR[3]))
    fa.lq(im, 2, 12, 400, 4, ["8E6A30", "B08442", "D9A95E", "E8C27A"], seed=7, reflet=False)
    rect(im, 2, 12, 400, 1, hexc(R_OR[7]))
    for i, hot in enumerate((False, True)):
        X = i * 8
        cols = (R_OR[3], R_OR[5], R_OR[7]) if not hot else (R_OR[4], R_OR[6], R_OR[8])
        rect(im, X, 18, 7, 12, hexc("0A0506"))
        rect(im, X + 1, 19, 5, 10, hexc(cols[1]))
        rect(im, X + 1, 19, 5, 1, hexc(cols[2]))
        rect(im, X + 1, 19, 1, 10, hexc(cols[2]))
        rect(im, X + 5, 20, 1, 9, hexc(cols[0]))
        rect(im, X + 2, 23, 3, 1, hexc("A0182B"))
    S.sauver("reglette.png", im, (404, 30))


def chevilles():
    im = nouveau(52, 12)
    for i, (signe_, hot) in enumerate((("-", False), ("-", True), ("+", False), ("+", True))):
        X = i * 13
        gf.plaquette4(im, X, 0, 12, 11, "rouge" if hot else "noir", seed=i)
        c = hexc(R_OR[7] if hot else R_OR[5])
        rect(im, X + 3, 5, 6, 1, c)
        if signe_ == "+":
            rect(im, X + 6 - 1 + 1, 2, 1, 7, c)
    S.sauver("chevilles.png", im, (52, 12))


def champ():
    im = nouveau(152, 14)
    fa.lq(im, 0, 0, 152, 14, ["050304", "0A0608", "0F090B", "140C0F"], seed=9, reflet=False)
    rect(im, 0, 0, 152, 1, hexc("050304")); rect(im, 0, 0, 1, 14, hexc("050304"))
    rect(im, 0, 13, 152, 1, hexc(R_OR[3])); rect(im, 151, 0, 1, 14, hexc(R_OR[2]))
    S.sauver("champ.png", im, (152, 14))


def mini():
    c = casier_ouvert(False, 3).resize((48, 26), Image.NEAREST)
    im = nouveau(48, 26)
    im.alpha_composite(c)
    fa.liseret(im, 0, 0, 48, 26)
    S.sauver("mini.png", im, (48, 26))


def vignette():
    S.sauver("vignette.png", ea.vignette_lisse(160, 90, force=0.75, expo=2.0), (160, 90))


if __name__ == "__main__":
    entete()
    meuble()
    casiers()
    fudas()
    sceaux()
    brocart()
    carte()
    pieces()
    reglages()
    onglets()
    acces()
    registre()
    toast()
    rouleau()
    baguettes()
    perles()
    signe()
    bouton()
    panneau_staff()
    plaques()
    reglette()
    chevilles()
    champ()
    mini()
    vignette()
    S.bilan()
