# -*- coding: utf-8 -*-
"""
gen_radio_textures.py — textures de l'écran de l'escouade (touche N, radio/RadioScreen), maquette validée
Maquettes/gameplan/18_radio/v3/01_ecran_escouade.png (décisions du 14/10) : panneau laqué noir CENTRÉ, épuré, petites
ferrures aux coins, deux filets d'or éteints (sous le titre, au-dessus des actions). AUCUN kanji ni sceau (DA §3).

1 texel = 1 GUI (échelle des menus). Le texte, les têtes, les points d'état et le survol sont posés par le code.

Usage (depuis minecraft/mod-hud) :  python tools/gen_radio_textures.py

Contrat (un panneau par hauteur utile : pas de coupe, donc pas de couture dans le reflet de la laque) :
- panneau_<h>.png (230 + 8) × (h + 8) : panneau laqué 230×h en (4, 4), ombre comprise dans la marge ;
  filet haut : x 79..158, y 4 + 43 ; filet bas (sauf h = 112) : x 79..158, y 4 + h − 34.
- h = 112 (pas d'escouade), 196 (jusqu'à 5 membres), puis + 21 par membre : 217, 238, 259 (8 membres).
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402,F401
from jeu_art import ea, kit, hexc, rect, nouveau  # noqa: E402

S = ea.Sortie("radio", budget=60_000)

W, M = 230, 4
HAUTEURS = (112, 196, 217, 238, 259)


def panneau(h):
    im = nouveau(W + 2 * M, h + 2 * M)
    kit.panneau_laque(im, M, M, W, h, ton="noir", modele_kanagu="petit", seed=18)
    cx = M + W // 2
    filets = (M + 43,) if h == 112 else (M + 43, M + h - 34)
    for y in filets:
        rect(im, cx - 40, y, 80, 1, hexc("6A4E24"))
    S.sauver(f"panneau_{h}.png", im, (W + 2 * M, h + 2 * M))


def main():
    vieux = os.path.join(S.dir, "panneau.png")
    if os.path.exists(vieux):
        os.remove(vieux)
    for h in HAUTEURS:
        panneau(h)
    S.bilan()


if __name__ == "__main__":
    main()
