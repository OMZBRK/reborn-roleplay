# -*- coding: utf-8 -*-
"""
gen_signalement_textures.py — textures du signalement en jeu (menu/widget/ReportScreen, ouvert depuis l'Échap)
au niveau de la fiche shinobi (DA §4.1, §6), version épurée.

Signature de l'écran : la boîte à doléances (目安箱, meyasubako) — petit coffre de bois à toit, fente sur le
dessus, plaquette crème nue (plus de kanji depuis le 18/10, DA §3), posé en tête de la planche laquée. Le formulaire (catégorie, sujet, message)
est sur la planche ; le jeu reste visible derrière, sous un voile.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_signalement_textures.py

Contrat :
- planche.png 348×228 : panneau laqué 340×220 en (2, 2).
- boite.png 44×40 : la boîte à doléances (pied au bas de l'image) ; posée par le code au-dessus du bord haut.
- champ.png 304×22 : champ de saisie 300×18 en (1, 1) ; coupe horizontale capD 6.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, gf, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("signalement", budget=120_000)


def planche():
    S.sauver("planche.png", ja.fenetre(340, 220, 0.95, seed=91), (348, 228))


def boite():
    im = nouveau(44, 40)
    kit.ombre_portee(im, 4, 10, 36, 28, dx=1, dy=2, force=0.6, rayon=1)
    # corps de bois (planches verticales)
    kit.bois(im, 5, 14, 34, 24, "moyen", sens="v", seed=7, noeuds=0)
    for x in (13, 22, 31):
        rect(im, x, 15, 1, 22, hexc("2C170C"))
    # toit à deux pans, bois sombre, faîtage or
    for j in range(10):
        w = 22 + j * 2
        rect(im, 22 - w // 2, 4 + j, w, 1, hexc(["2C170C", "3B2012", "4B2A17", "5C351D"][min(3, j // 3)]))
    rect(im, 11, 4, 22, 1, hexc(R_OR[5]))
    rect(im, 1, 14, 42, 1, hexc("1A0E08"))
    # fente
    rect(im, 15, 6, 14, 2, hexc("0A0608"))
    # ferrures d'angle
    for (x, y) in ((5, 14), (36, 14), (5, 35), (36, 35)):
        rect(im, x, y, 3, 3, hexc(R_OR[4]))
        rect(im, x + 1, y + 1, 1, 1, hexc(R_OR[7]))
    # plaquette crème (nue)
    kit.washi(im, 13, 16, 18, 20, "creme", seed=2, bords="nets", ombre=False, fibres=False, taches=False)
    rect(im, 13, 16, 18, 1, hexc("8E1628"))
    S.sauver("boite.png", im, (44, 40))


def champ():
    S.sauver("champ.png", ja.champ(300, 18), (304, 22))


def main():
    planche()
    boite()
    champ()
    S.bilan()


if __name__ == "__main__":
    main()
