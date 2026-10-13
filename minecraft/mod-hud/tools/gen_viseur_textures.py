# -*- coding: utf-8 -*-
"""
gen_viseur_textures.py — textures de l'éditeur de viseur (crosshair/CrosshairScreen) au niveau de la fiche
shinobi (DA §4.1, §6), en version épurée (retour du 13/10 : « faut que ce soit subtil »).

Signature de l'écran : le mato de kyūdō (霞的, kasumi-mato) — la cible de papier tendue sur son cerceau, plantée
dans la butte de sable (azuchi) sous l'auvent du pas de tir, deux flèches fichées dans le sable. Le viseur est
dessiné par le code au centre de la cible (aperçu en direct). À droite, la planche laquée des réglages (contrôles
des Paramètres : interrupteurs, réglettes d'or, plaques segmentées) et la rangée des modèles.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_viseur_textures.py

Contrat :
- entete.png 160×44 : cartouche 136×22 en (12, 8) (sans cordons : posé à cheval sur le bord haut de la planche) ; titre posé par le code (haut des capitales y 16, centré en x 80).
- mato.png 208×308 : panneau laqué 200×300 en (2, 2) ; scène dans le panneau ; centre de la cible = texel
  (102, 134) ; rayon 52.
- planche.png 428×308 : panneau laqué 420×300 en (2, 2).
- cellules.png 78×26 : 3 cellules 22×22 (repos, survol, actif) en (1 + 26·i, 1).
- pieces.png 64×48 : cadre de pastille 15×15 ×3 (repos, survol, choisie) en (16·i, 0) ; cabochon 42×12 (0, 18) ;
  losange 3×3 (44, 18).
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, gf, kit, hexc, rect, coller, R_OR, R_LAQUE, nouveau  # noqa: E402

S = ea.Sortie("viseur", budget=300_000)

SABLE = ["2A2018", "362A1E", "433424", "50402C", "5E4C34", "6C583C", "7A6444"]


def entete():
    S.sauver("entete.png", ja.entete(136, 22, cordons=False), (160, 44))


def mato():
    W, H = 208, 308
    im = ja.fenetre(200, 300, 0.95, seed=11)
    ix, iy, iw, ih = 6, 6, 192, 292            # intérieur (sous le filet rouge)
    sc = nouveau(iw, ih)
    # fond : mur de planches sombres du matoba, la nuit
    kit.bois(sc, 0, 0, iw, ih, "sombre", sens="v", seed=5, noeuds=0, biseau=False)
    fa.assombrir(sc, 0, 0, np.full((ih, iw), 0.45))
    for x in range(0, iw, 24):
        rect(sc, x, 0, 1, ih, hexc("0C0604"))
    # auvent : bord de toit (poutre + tuiles), très sobre
    rect(sc, 0, 0, iw, 16, hexc("120A08"))
    for x in range(0, iw, 8):
        rect(sc, x, 10, 7, 5, hexc("2A2024"))
        rect(sc, x, 10, 7, 1, hexc("3E3236"))
    rect(sc, 0, 16, iw, 2, hexc(R_OR[2]))
    rect(sc, 0, 18, iw, 1, hexc("080404"))
    # butte de sable (azuchi) : pente douce, plus claire vers le bas
    yb = 112
    yy, xx = np.mgrid[0:ih - yb, 0:iw].astype(float)
    v = 0.25 + 0.55 * (yy / (ih - yb)) + (kit.bruit(iw, ih - yb, 24, 3, 2) - 0.5) * 0.06
    idx = kit.quantifier(np.clip(v, 0, 0.999), len(SABLE), ix, iy + yb, tramage=0.25)
    p = kit._rampe_vers_rgba(SABLE, idx)
    crete = (kit.bruit(iw, 1, 40, 8, 2)[0] - 0.5) * 6
    for i in range(iw):
        p[:max(0, int(4 + crete[i])), i, 3] = 0
    coller(sc, p, 0, yb)
    # cible : kasumi-mato (霞的), papier blanc cassé, anneaux noirs, cerceau de bois
    cx, cy, R = 96, 128, 52
    kit.disque_lisse(sc, cx + 2, cy + 3, R + 2, "000000", alpha=110)            # ombre portée sur le sable
    kit.disque_lisse(sc, cx, cy, R + 2, "5A3A20")                                 # cerceau
    kit.disque_lisse(sc, cx, cy, R, "E9E1CF")
    for (r, ep) in ((R - 2, 3.5), (R * 0.74, 4.5), (R * 0.56, 2.5), (R * 0.47, 2.5), (R * 0.30, 6)):
        kit.anneau_lisse(sc, cx, cy, r, ep, "16100E")
    kit.anneau_lisse(sc, cx, cy, R + 1.5, 1, R_OR[3])
    # ombre douce en bas du papier (volume)
    m = np.zeros((ih, iw))
    yy, xx = np.mgrid[0:ih, 0:iw].astype(float)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    m[(d < R) & (yy > cy + R * 0.35)] = 0.10
    fa.assombrir(sc, 0, 0, m)
    # flèches fichées dans le sable (fût bambou, empennage blanc), discrètes
    for (x0, y0, x1, y1) in ((36, 214, 22, 168), (160, 226, 172, 182)):
        kit.trait_lisse(sc, [(x0, y0), (x1, y1)], 1.2, "B08442")
        dx, dy = x1 - x0, y1 - y0
        n = math.hypot(dx, dy)
        ux, uy = dx / n, dy / n
        for k in range(3, 10):
            px_, py_ = x1 - ux * k, y1 - uy * k
            rect(sc, int(px_) - 1, int(py_), 1, 1, hexc("E8E2D4"))
            rect(sc, int(px_) + 1, int(py_), 1, 1, hexc("C8C0B0"))
        rect(sc, int(x0 - ux * 1), int(y0 - uy * 1), 2, 1, hexc("3A2A1A"))
    # vignette
    fa.assombrir(sc, 0, 0, np.clip((np.abs(xx - iw / 2) / (iw / 2)) ** 3 * 0.5, 0, 0.5))
    im.alpha_composite(sc, (ix, iy))
    # filet d'or bas (place de l'étiquette « aperçu »)
    ja.filet_or(im, 30, 174, 282)
    S.sauver("mato.png", im, (W, H), quantifier=True)


def planche():
    S.sauver("planche.png", ja.fenetre(420, 300, 0.93, seed=23), (428, 308))


def cellules():
    S.sauver("cellules.png", ja.cellule_etats(22), (78, 26))


def pieces():
    im = nouveau(64, 48)
    for i, etat in enumerate(("repos", "survol", "choisie")):
        x, y = 16 * i, 0
        kit.ombre_portee(im, x, y, 15, 15, dx=1, dy=1, force=0.5, rayon=0)
        rect(im, x, y, 15, 15, hexc("0A0608"))
        if etat == "choisie":
            fa.liseret(im, x, y, 15, 15)
        elif etat == "survol":
            rect(im, x, y, 15, 1, hexc(R_OR[5])); rect(im, x, y + 14, 15, 1, hexc(R_OR[3]))
            rect(im, x, y, 1, 15, hexc(R_OR[5])); rect(im, x + 14, y, 1, 15, hexc(R_OR[3]))
        else:
            fa.liseret_terne(im, x, y, 15, 15)
    ja.cabochon(im, 0, 18, 42, 12)
    ja.losange(im, 45, 19)
    S.sauver("pieces.png", im, (64, 48))


def main():
    entete()
    mato()
    planche()
    cellules()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
