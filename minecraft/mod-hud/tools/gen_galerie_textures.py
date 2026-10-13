# -*- coding: utf-8 -*-
"""
gen_galerie_textures.py — textures de la galerie de captures (ui/GalleryScreen, ScreenshotDetailScreen,
ScreenshotEditorScreen, ScreenshotShareScreen) au niveau de la fiche shinobi (DA §4.1, §6), version épurée.

Signature de l'écran (conservée) : les tirages pincés sur des fils — chaque capture est un tirage sur
passe-partout de washi, tenu par une pince de bois sur un fil de chanvre tendu. La vue détail encadre le tirage
dans une grande planche laquée, avec la pellicule des captures voisines en bas.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_galerie_textures.py

Contrat :
- cartouche.png 184×38 : plaque 176×30 en (2, 2) ; titre et compte posés par le code.
- tirage.png 192×132 : passe-partout de washi 188×128 en (1, 1) (+ ombre) ; coupe capD = capB = 8.
- grand.png 868×484 : planche laquée 860×476 en (2, 2) avec passe-partout de washi 844×460 en (10, 10) ; coupe
  capD = capB = 24.
- boite.png 388×248 : planche laquée 380×240 en (2, 2) ; coupe capD = capB = 18.
- outils.png 168×548 : planche laquée 160×540 en (2, 2) ; coupe capD = capB = 18.
- pellicule.png 64×32 : bande de film 64×30 (perforations tous les 8), à répéter.
- pieces.png 136×48 : pince 9×16 (0, 0) ; cœurs 7×6 : vide (12, 0), survol (20, 0), plein (28, 0) ;
  plaquettes fléchées 24×24 : gauche (40, 0), gauche survolée (64, 0), droite (88, 0), droite survolée (112, 0) ;
  ligne de menu survolée 100×14 (0, 26), coupe horizontale capD 6.
"""
from __future__ import annotations

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("galerie", budget=400_000)


def cartouche():
    im = nouveau(184, 38)
    fa.panneau_opacite(im, 2, 2, 176, 30, 0.95, seed=81, kanagu_=False, eclats=False)
    fa.ferrures3(im, 2, 2, 176, 30)
    S.sauver("cartouche.png", im, (184, 38))


def tirage():
    im = nouveau(192, 132)
    kit.ombre_portee(im, 1, 1, 188, 128, dx=2, dy=3, force=0.55, rayon=1)
    kit.washi(im, 1, 1, 188, 128, "creme", seed=82, bords="nets", ombre=False, taches=False)
    S.sauver("tirage.png", im, (192, 132))


def grand():
    im = ja.fenetre(860, 476, 0.96, seed=83)
    kit.washi(im, 10, 10, 844, 460, "creme", seed=84, bords="nets", ombre=False, taches=False)
    S.sauver("grand.png", im, (868, 484), quantifier=True)


def boite():
    S.sauver("boite.png", ja.fenetre(380, 240, 0.96, seed=85), (388, 248))
    S.sauver("outils.png", ja.fenetre(160, 540, 0.95, seed=86), (168, 548))


def pellicule():
    im = nouveau(64, 32)
    rect(im, 0, 0, 64, 30, hexc("100C0E"))
    rect(im, 0, 0, 64, 1, hexc("2A2224"))
    rect(im, 0, 29, 64, 1, hexc("050304"))
    for x in range(2, 64, 8):
        rect(im, x, 2, 4, 3, hexc("3C3234"))
        rect(im, x, 25, 4, 3, hexc("3C3234"))
        rect(im, x, 2, 4, 1, hexc("50464A"))
        rect(im, x, 25, 4, 1, hexc("50464A"))
    S.sauver("pellicule.png", im, (64, 32))


def pieces():
    im = nouveau(136, 48)
    # pince à linge de bois
    ja.sprite(im, 0, 0, [
        ".aaaaaaa.", "abbbcbbba", "abbbcbbba", "abbbcbbba", "abbbcbbba", "addddddda", "aeeeeeeea", "abbbcbbba",
        "abbbcbbba", "abbbcbbba", "abbbcbbba", "abbbcbbba", "abbbcbbba", "abbbcbbba", ".abbcbba.", "..aaaaa..",
    ], {"a": "4A2A17", "b": "B08050", "c": "6E4828", "d": "8E8E96", "e": "5A5A62"})
    coeur_v = ["0110110", "1001001", "1000001", "0100010", "0010100", "0001000"]
    coeur_p = ["0110110", "1111111", "1111111", "0111110", "0011100", "0001000"]
    for ox, rows, col in ((12, coeur_v, "96826A"), (20, coeur_v, "C01E35"), (28, coeur_p, "C01E35")):
        ja.sprite(im, ox, 0, [r.replace("1", "a").replace("0", ".") for r in rows], {"a": col})
    # plaquettes fléchées 24×24 : gauche (repos, survol), droite (repos, survol)
    fl = ["....a", "...ab", "..abb", ".abbb", "abbbb", ".abbb", "..abb", "...ab", "....a"]
    for k, (ox, survol, droite) in enumerate(((40, False, False), (64, True, False), (88, False, True), (112, True, True))):
        fa.plaquette22(im, ox + 1, 1, "survol" if survol else "repos", seed=k)
        rows = [r[::-1] for r in fl] if droite else fl
        ja.sprite(im, ox + 10, 8, rows, {"a": R_OR[3], "b": R_OR[6] if survol else R_OR[5]})
    # ligne de menu contextuel survolée 100×14 en (0, 26)
    fa.lq(im, 0, 26, 100, 14, ["3A0A12", "4E0D17", "63111D", "7A1322"], seed=3, intensite=1.0)
    for j in range(14):
        rect(im, 0, 26 + j, 2, 1, hexc(R_OR[5] if j % 3 else R_OR[6]))
    S.sauver("pieces.png", im, (136, 48))


def main():
    cartouche()
    tirage()
    grand()
    boite()
    pellicule()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
