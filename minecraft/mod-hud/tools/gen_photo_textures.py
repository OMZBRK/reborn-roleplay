# -*- coding: utf-8 -*-
"""
gen_photo_textures.py — textures du mode photo (ui/PhotoModeScreen) au niveau de la fiche shinobi (DA §4.1,
§6), version épurée. Le mode photo « façon viseur » a plu (DA §4) : on garde le viseur, on le dessine au kit.

Signature de l'écran (conservée) : le viseur — équerres d'angle à l'encre et à l'or, grille des tiers, cercle
central — et le déclencheur rond de laque vermillon cerclé d'or. Cartouche « Mode photo » (sans sceau), panneau
laqué étroit des réglages à droite, tirages des dernières photos en bas à gauche.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_photo_textures.py

Contrat :
- panneau.png 108×230 : panneau laqué 100×222 en (2, 2).
- cartouche.png 164×38 : plaque 156×30 en (2, 2), sans sceau (aucun kanji depuis le 18/10, DA §3) ; texte posé
  par le code à partir de x 12.
- declencheur.png 72×36 : 2 cellules 36×36 (repos, survol), centre en (18, 18) de chaque cellule, rayon 15.
- coins.png 64×16 : équerre d'angle 16×16 haut gauche (0, 0), haut droit (16, 0), bas gauche (32, 0), bas droit
  (48, 0).
- pieces.png 112×32 : cadre de pastille 18×18 ×3 (repos, survol, choisie) en (20·i, 0) ; passe-partout de
  tirage 42×24 en (60, 0) (+ ombre) ; cercle central 15×15 (0, 18).
"""
from __future__ import annotations

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, gf, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("photo", budget=200_000)


def panneau():
    S.sauver("panneau.png", ja.fenetre(100, 222, 0.92, seed=71), (108, 230))


def cartouche():
    im = nouveau(164, 38)
    fa.panneau_opacite(im, 2, 2, 156, 30, 0.94, seed=72, kanagu_=False, eclats=False)
    fa.ferrures3(im, 2, 2, 156, 30)
    S.sauver("cartouche.png", im, (164, 38))


def declencheur():
    im = nouveau(72, 36)
    for k in range(2):
        cx, cy = 36 * k + 18, 18
        kit.disque_lisse(im, cx + 1, cy + 2, 16, "000000", alpha=110)
        kit.disque_lisse(im, cx, cy, 16, R_OR[3])
        kit.anneau_lisse(im, cx, cy, 15.2, 1.6, R_OR[6] if k else R_OR[5])
        kit.disque_lisse(im, cx, cy, 13, "A0182B" if not k else "C01E35")
        kit.anneau_lisse(im, cx, cy, 12.5, 1, "5C0E18")
        # reflet de laque
        kit.anneau_lisse(im, cx, cy, 10, 1.5, "E26474", debut=200, fin=250, alpha=150)
        kit.disque_lisse(im, cx, cy, 5.5, "F5E9D0")
        kit.anneau_lisse(im, cx, cy, 5.5, 1, R_OR[4])
    S.sauver("declencheur.png", im, (72, 36))


def coins():
    im = nouveau(64, 16)
    base = nouveau(16, 16)
    # équerre : trait or 2 px liseré d'encre, bout en losange
    rect(base, 0, 0, 16, 3, hexc("0A0608"))
    rect(base, 0, 0, 3, 16, hexc("0A0608"))
    rect(base, 0, 0, 15, 2, hexc(R_OR[5]))
    rect(base, 0, 0, 2, 15, hexc(R_OR[5]))
    rect(base, 0, 0, 15, 1, hexc(R_OR[7]))
    rect(base, 0, 0, 1, 15, hexc(R_OR[7]))
    for i, (fx, fy) in enumerate(((False, False), (True, False), (False, True), (True, True))):
        c = base
        if fx:
            c = c.transpose(Image.FLIP_LEFT_RIGHT)
        if fy:
            c = c.transpose(Image.FLIP_TOP_BOTTOM)
        im.paste(c, (16 * i, 0))
    S.sauver("coins.png", im, (64, 16))


def pieces():
    im = nouveau(112, 32)
    for i, etat in enumerate(("repos", "survol", "choisie")):
        x, y = 20 * i, 0
        kit.ombre_portee(im, x, y, 18, 18, dx=1, dy=1, force=0.5, rayon=0)
        rect(im, x, y, 18, 18, hexc("0A0608"))
        if etat == "choisie":
            fa.liseret(im, x, y, 18, 18)
        elif etat == "survol":
            rect(im, x, y, 18, 1, hexc(R_OR[5])); rect(im, x, y + 17, 18, 1, hexc(R_OR[3]))
            rect(im, x, y, 1, 18, hexc(R_OR[5])); rect(im, x + 17, y, 1, 18, hexc(R_OR[3]))
        else:
            fa.liseret_terne(im, x, y, 18, 18)
    # passe-partout de tirage (washi crème, ombre)
    kit.ombre_portee(im, 60, 0, 42, 24, dx=1, dy=2, force=0.5, rayon=0)
    kit.washi(im, 60, 0, 42, 24, "creme", seed=3, bords="nets", ombre=False, taches=False)
    # cercle central du viseur
    kit.anneau_lisse(im, 7, 25, 6, 1.2, "F4EAD6", alpha=210)
    S.sauver("pieces.png", im, (112, 32))


def main():
    panneau()
    cartouche()
    declencheur()
    coins()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
