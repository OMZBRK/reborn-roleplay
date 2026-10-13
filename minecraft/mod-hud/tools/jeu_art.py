# -*- coding: utf-8 -*-
"""
jeu_art.py — pièces communes des écrans « en jeu » remis au niveau de la fiche le 13/10 (viseur, caméra,
animations, éditeur de HUD, mode photo, galerie, signalement, déconnexion, test de la Feuille, repositionnement
des cosmétiques, Volonté).

Rien n'est inventé : on assemble les primitives de la fiche (tools/fiche_art.py), de ses pièces du tour 4
(tools/gen_fiche_textures.py) et de la boîte à outils (tools/style/kit.py), toutes en lecture seule. Chaque écran
garde sa signature (un objet propre), dessinée dans son générateur tools/gen_<ecran>_textures.py.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, coller, R_OR, R_LAQUE, R_LAQUE_ROUGE, nouveau  # noqa: E402,F401

NUIT = "0A0608"


def losange(px, X, y):
    """Petit losange d'or 3×3 centré en (X, y)."""
    for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
        rect(px, X + dx, y + dy, 1, 1, hexc(c))


def entete(w, h=24, cordon="rouge", glands=True, cordons=True):
    """Plaque laquée suspendue (titre posé par le code au centre, haut des capitales à y = 8 + (h − 7) / 2) :
    cordons depuis le haut, anneaux d'or, agemaki + glands aux bouts, filets à losanges dans la plaque.
    Image (w + 24) × (h + 22) ; la plaque est en (12, 8)."""
    W, H = w + 24, h + 22
    im = nouveau(W, H)
    x, y = 12, 8
    R = kit.R_CORDE[cordon]
    for a in ((x + 24, x + w - 25) if cordons else ()):
        for k in range(y + 1):
            rect(im, a, k, 2, 1, hexc(R[3] if k % 2 == 0 else R[2]))
            rect(im, a + 1, k, 1, 1, hexc(R[1]))
    fa.panneau(im, x, y, w, h, seed=w, eclats=False)
    for a in ((x + 24, x + w - 25) if cordons else ()):
        fa.anneau_or(im, a + 1, y + 1)
    if glands:
        for gx in (x - 4, x + w + 3):
            fa.agemaki(im, gx, y + h // 2 + 2)
            fa.gland3(im, gx, y + h // 2 + 7, 8)
    return im


def plaque_etats(w, h, etats=("repos", "survol", "actif"), pas=None):
    """Plaques laquées empilées (une rangée par état) : repos (laque éteinte, liseré terne), survol (laque vive,
    liseré or), actif (laque rouge, liseré or, ferrures), inactif (sombre). Plaque w×h en (1, 1) de chaque
    rangée ; rangées de h + 4. Coupe horizontale côté code (bords de 6)."""
    pas = pas or h + 4
    im = nouveau(w + 4, pas * len(etats))
    for r, etat in enumerate(etats):
        x, y = 1, r * pas + 1
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=2, force=0.5, rayon=0)
        if etat == "actif":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=r + w, intensite=1.3)
            fa.liseret(im, x, y, w, h)
            fa.ferrures3(im, x, y, w, h)
        elif etat == "survol":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r + w, intensite=1.7)
            fa.liseret(im, x, y, w, h)
        elif etat == "inactif":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, ["0E0A0C", "130D10", "181114"], seed=r, intensite=0.3, reflet=False)
            fa.liseret_terne(im, x, y, w, h)
        else:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r + w, intensite=0.8)
            fa.liseret_terne(im, x, y, w, h)
    return im


def champ(w, h=14):
    """Champ de saisie : rainure laquée, liseré or éteint creusé. Image (w + 4) × (h + 4), champ en (1, 1)."""
    im = nouveau(w + 4, h + 4)
    x, y = 1, 1
    fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=4, intensite=0.4, reflet=False)
    rect(im, x, y, w, 1, hexc(R_OR[1]))
    rect(im, x, y, 1, h, hexc(R_OR[1]))
    rect(im, x, y + h - 1, w, 1, hexc(R_OR[3]))
    rect(im, x + w - 1, y, 1, h, hexc(R_OR[3]))
    rect(im, x + 1, y + 1, w - 2, 1, hexc("050304"))
    return im


def cellule_etats(T, etats=("repos", "survol", "actif")):
    """Cellules carrées T×T (vignettes de modèles, emplacements) côte à côte : une par état."""
    im = nouveau((T + 4) * len(etats), T + 4)
    for i, etat in enumerate(etats):
        x, y = i * (T + 4) + 1, 1
        kit.ombre_portee(im, x, y, T, T, dx=1, dy=2, force=0.5, rayon=0)
        if etat == "actif":
            fa.lq(im, x + 1, y + 1, T - 2, T - 2, ["2A0A10", "380D16", "46111C", "561522"], seed=i, intensite=0.9)
            fa.liseret(im, x, y, T, T)
            fa.ferrures3(im, x, y, T, T)
        elif etat == "survol":
            fa.lq(im, x + 1, y + 1, T - 2, T - 2, "noir", seed=i, intensite=1.6)
            fa.liseret(im, x, y, T, T)
        else:
            fa.lq(im, x + 1, y + 1, T - 2, T - 2, "noir", seed=i, intensite=0.7)
            fa.liseret_terne(im, x, y, T, T)
    return im


def fenetre(w, h, opacite=0.93, seed=0, marge=2):
    """Planche laquée w×h (ombre comprise dans la marge) : image (w + 2·marge + 4) × (h + 2·marge + 4),
    panneau en (marge, marge)."""
    im = nouveau(w + 2 * marge + 4, h + 2 * marge + 4)
    fa.panneau_opacite(im, marge, marge, w, h, opacite, seed=seed)
    return im


def filet_or(px, x0, x1, y):
    """Filet d'or éteint de x0 à x1 (losanges aux bouts)."""
    rect(px, x0 + 2, y, x1 - x0 - 4, 1, hexc(R_OR[2]))
    losange(px, x0 + 1, y)
    losange(px, x1 - 2, y)


def cabochon(px, x, y, w=42, h=12):
    ea.cabochon(px, x, y, w, h)


def rail(px, x, y, h=40):
    rect(px, x, y, 4, h, hexc("140C0F"))
    rect(px, x, y, 1, h, hexc(R_OR[2]))
    rect(px, x + 3, y, 1, h, hexc(R_OR[1]))


def curseur_rail(px, x, y, h=16):
    fa.lq(px, x + 1, y + 1, 4, h - 2, "rouge", seed=1, reflet=False)
    fa.liseret(px, x, y, 6, h)


def sprite(px, ox, oy, rows, pal):
    """Petit sprite pixel art depuis des chaînes (« . » = vide)."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(px, ox + i, oy + j, 1, 1, hexc(pal[ch]))


def voile_vertical(w, h, haut=0.0, bas=0.6, couleur=(8, 4, 6)):
    """Dégradé lisse vertical (texture étirée côté code)."""
    a = np.linspace(haut, bas, h)[:, None].repeat(w, 1)
    p = np.zeros((h, w, 4), np.uint8)
    p[..., 0], p[..., 1], p[..., 2] = couleur
    p[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(p, "RGBA")
