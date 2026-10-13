# -*- coding: utf-8 -*-
"""
gen_deconnexion_textures.py — textures de la confirmation « Se déconnecter ? » (menu/widget/DisconnectConfirmScreen,
ouverte depuis l'Échap) au niveau de la fiche shinobi (DA §4.1, §6), version épurée.

Signature de l'écran : le chapeau de voyageur (sugegasa, 菅笠) — chapeau conique de paille tressée à cordon
vermillon, posé sur le haut de la planche : se déconnecter, c'est prendre la route. Planche laquée sobre,
deux plaques (Annuler / Se déconnecter).

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_deconnexion_textures.py

Contrat :
- planche.png 268×132 : panneau laqué 260×124 en (2, 2).
- chapeau.png 64×30 : le sugegasa (bord bas du chapeau à y 22, centre x 32), cordon pendant dessous.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("deconnexion", budget=60_000)

PAILLE = ["5E4626", "735830", "8A6B3C", "A08048", "B69656", "CCAC68", "DCC07E"]


def planche():
    S.sauver("planche.png", ja.fenetre(260, 124, 0.95, seed=95), (268, 132))


def chapeau():
    im = nouveau(64, 30)
    cx, base = 32, 22
    # ombre portée douce sous le bord
    for x in range(4, 60):
        rect(im, x + 1, base + 1, 1, 1, (4, 2, 3, 120))
    # cône : du sommet (cx, 4) au bord (x 3..61) ; bandes de tressage concentriques
    for y in range(4, base + 1):
        t = (y - 4) / (base - 4)
        half = int(round(1 + t * 28))
        for x in range(cx - half, cx + half + 1):
            # lumière venant du haut gauche, tressage tous les 3 rangs
            l = 0.62 - 0.35 * ((x - (cx - half)) / max(1, 2 * half)) + 0.15 * (1 - t)
            if (y - 4) % 3 == 2:
                l -= 0.18
            if (x + y) % 4 == 0 and (y - 4) % 3 != 2:
                l += 0.08
            k = max(0, min(len(PAILLE) - 1, int(l * len(PAILLE))))
            rect(im, x, y, 1, 1, hexc(PAILLE[k]))
    # liseré du bord et pointe
    rect(im, cx - 29, base, 59, 1, hexc("3E2A14"))
    rect(im, cx, 3, 1, 1, hexc("3E2A14"))
    # cordon vermillon qui pend sous le bord, nœud
    R = kit.R_CORDE["rouge"]
    for k in range(6):
        rect(im, cx - 9 + k // 2, base + 1 + k, 1, 1, hexc(R[3] if k % 2 else R[2]))
        rect(im, cx + 9 - k // 2, base + 1 + k, 1, 1, hexc(R[3] if k % 2 else R[2]))
    rect(im, cx - 6, base + 6, 13, 1, hexc(R[2]))
    rect(im, cx - 1, base + 5, 3, 2, hexc(R[4]))
    S.sauver("chapeau.png", im, (64, 30))


def main():
    planche()
    chapeau()
    S.bilan()


if __name__ == "__main__":
    main()
