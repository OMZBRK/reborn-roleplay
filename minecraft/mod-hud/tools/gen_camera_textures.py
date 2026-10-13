# -*- coding: utf-8 -*-
"""
gen_camera_textures.py — textures du menu de la caméra épaule (camera/CameraScreen) au niveau de la fiche
shinobi (DA §4.1, §6), version épurée.

Signature de l'écran : le miroir de bronze (dōkyō, 銅鏡) — un disque de bronze poli, cerclé de patine, posé sur
sa planche ; la vue de dessus (joueur, caméra, champ de vision) se reflète en direct dans le miroir
(dessinée par le code). Le panneau reste étroit, à droite : le jeu est visible et la caméra bouge en temps réel.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_camera_textures.py

Contrat :
- panneau.png 208×344 : panneau laqué 200×336 en (2, 2) ; miroir centré en texel (102, 62), rayon utile 40
  (surface polie), cercles de distance gravés à 2, 4 et 6 blocs (5,5 GUI par bloc) autour du centre.
- entete.png 160×44 : cartouche 136×22 en (12, 8) (sans cordons).
- pieces.png 32×16 : marqueur caméra 7×7 (0, 0) ; joueur 5×5 (8, 0) ; losange 3×3 (14, 1).
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, coller, R_OR, nouveau  # noqa: E402

S = ea.Sortie("camera", budget=200_000)

BRONZE = ["1C1A14", "26231A", "302C20", "3A3526", "463F2C", "524832", "5E5238"]
PATINE = ["1A2A26", "22362F", "2C4238", "3A5244", "4A6450"]
CX, CY, R = 102, 62, 40


def panneau():
    im = ja.fenetre(200, 336, 0.94, seed=31)
    # (le cartouche du titre, posé par le code, couvre le haut du panneau)
    # ombre du miroir sur la laque
    kit.disque_lisse(im, CX + 2, CY + 3, R + 6, "000000", alpha=120)
    # cerclage de patine (bronze vert-de-gris), biseau
    kit.disque_lisse(im, CX, CY, R + 5, PATINE[1])
    kit.anneau_lisse(im, CX, CY, R + 4, 2, PATINE[3])
    kit.anneau_lisse(im, CX, CY, R + 5, 1, "0C1210")
    # anneau d'or fin entre patine et surface
    kit.anneau_lisse(im, CX, CY, R + 1.2, 1.4, R_OR[3])
    # surface polie : bronze sombre, reflet diagonal doux tramé
    yy, xx = np.mgrid[0:2 * R + 1, 0:2 * R + 1].astype(float)
    dx, dy = xx - R, yy - R
    d = np.sqrt(dx * dx + dy * dy) / R
    v = 0.30 + 0.20 * np.exp(-(((dx + dy) / (1.4 * R) + 0.35) / 0.35) ** 2) - 0.12 * d ** 3
    v += (kit.bruit(2 * R + 1, 2 * R + 1, 12, 7, 2) - 0.5) * 0.04
    idx = kit.quantifier(np.clip(v, 0, 0.999), len(BRONZE), CX - R, CY - R, tramage=0.2)
    p = kit._rampe_vers_rgba(BRONZE, idx)
    p[d > 1.0] = 0
    lay = nouveau(2 * R + 1, 2 * R + 1)
    coller(lay, p, 0, 0)
    im.alpha_composite(lay, (CX - R, CY - R))
    # cercles de distance gravés (2, 4, 6 blocs × 5,5 GUI), très discrets
    for b in (2, 4, 6):
        kit.anneau_lisse(im, CX, CY, b * 5.5, 0.8, "6E6046", alpha=90)
    # bouton central (chū) du revers suggéré par un point, croix de repère
    rect(im, CX, CY - R + 3, 1, 2 * R - 6, hexc("4A4230"))
    rect(im, CX - R + 3, CY, 2 * R - 6, 1, hexc("4A4230"))
    # filets d'or séparateurs (sous le miroir)
    ja.filet_or(im, 14, 190, 114)
    S.sauver("panneau.png", im, (208, 344), quantifier=True)


def entete():
    S.sauver("entete.png", ja.entete(136, 22, cordons=False), (160, 44))


def pieces():
    im = nouveau(32, 16)
    # caméra : petite plaquette laquée rouge, liseré or
    fa.lq(im, 1, 1, 5, 5, "rouge", seed=2, reflet=False)
    fa.liseret(im, 0, 0, 7, 7)
    # joueur : point crème cerclé de sombre
    ja.sprite(im, 8, 0, [".aaa.", "abbba", "abcba", "abbba", ".aaa."], {"a": "1A1013", "b": "E8DCC0", "c": "FFFFFF"})
    ja.losange(im, 15, 2)
    S.sauver("pieces.png", im, (32, 16))


def main():
    panneau()
    entete()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
