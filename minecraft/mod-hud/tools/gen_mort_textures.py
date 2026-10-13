# -*- coding: utf-8 -*-
"""
gen_mort_textures.py — textures de la scène de mort (ko/DeathScreen.java), au niveau de la fiche.

Sobre et solennel : sur l'écran noir, une plaque de laque noire bordée d'or, un sceau vermillon 終
posé à cheval sur l'arête haute, la question en ArcadePix crème, un filet d'or à losanges, deux
boutons-plaques (見 OUI, 去 NON). Vue cinéma : bandes noires, nom au filet d'or, touches en cabochons.

Usage (depuis minecraft/mod-hud) :  python tools/gen_mort_textures.py

Contrat (1 texel = 1 GUI, écran de référence 960×540) :
- plaque.png 312×128 : planche 300×104 au texel (6, 10) ; sceau 終 centré (156, 10) ; question
  cuite (haut des capitales y 33) ; filet à losanges y 47. Le code pose le sous-titre (milieu
  y 61) et les boutons (haut y 78) relativement au coin de la planche + (6, 10).
- boutons.png 190×48 : cellules 95×24 (colonnes : normal, survol ; rangées : OUI, NON) ; bouton
  92×20 au texel (1, 1) de la cellule.
- pieces.png 64×12 : losange or 3×3 (0, 0) ; cabochon de touche 42×12 en (16, 0) (bords 4 / 6).
- vignette.png 160×90 : vignette lisse (étirée au blit).
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, arcade, R_OR, nouveau  # noqa: E402

S = ea.Sortie("mort", budget=200_000)

PW, PH = 300, 104
OX, OY = 6, 10
QUESTION = "ASSISTER A VOTRE REELLE FIN ?"


def plaque():
    im = nouveau(312, 128)
    fa.panneau_opacite(im, OX, OY, PW, PH, 0.96, seed=13)
    cx = OX + PW // 2
    # sceau 終 à cheval sur l'arête haute, sur un petit coussin de laque
    gf.hanko_px(im, cx, OY, "終", 18, ea.R_VERM, seed=31, usure=0.08)
    arcade(im, cx, OY + 23, QUESTION, 10, ea.CREME, ancre="c", ombre=True)
    ea.filet_losanges(im, cx, OY + 37, 92, ecart=8)
    gf.losange_or(im, cx - 1, OY + 36)
    S.sauver("plaque.png", im, (312, 128))


def boutons():
    im = nouveau(190, 48)
    for r, (kj, lib) in enumerate((("見", "OUI"), ("去", "NON"))):
        for c, etat in enumerate(("normal", "survol")):
            cell = nouveau(95, 24)
            ea.bouton(cell, 1, 1, 92, 20, "actif" if etat == "survol" else "normal", kj=kj)
            col = ea.CREME if etat == "survol" else hexc("D8CCB0")
            arcade(cell, 1 + 26 + (92 - 26 - 8) // 2, 1 + 7, lib, 10, col, ancre="c", ombre=True)
            im.paste(cell, (c * 95, r * 24))
    S.sauver("boutons.png", im, (190, 48))


def pieces():
    im = nouveau(64, 12)
    gf.losange_or(im, 0, 0)
    ea.cabochon(im, 16, 0, 40, 10)
    S.sauver("pieces.png", im, (64, 12))


def vignette():
    S.sauver("vignette.png", ea.vignette_lisse(160, 90, force=0.9, expo=1.8), (160, 90))


if __name__ == "__main__":
    plaque()
    boutons()
    pieces()
    vignette()
    S.bilan()
