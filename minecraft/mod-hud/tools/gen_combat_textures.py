# -*- coding: utf-8 -*-
"""
gen_combat_textures.py — pièces du HUD de combat discret (combat/CooldownHud : icônes de recharge), au niveau de
la fiche shinobi (DA §4.1, §6), version épurée (retour du 13/10 : HUD petits, fins, peu de cadre).

L'icône de capacité est posée dans un petit créneau de laque (22×22) : liseré d'or quand elle est prête, liseré
éteint pendant la recharge (le voile radial et les secondes restent dessinés par le code).

Usage (depuis minecraft/mod-hud) :  python tools/gen_combat_textures.py

Contrat :
- creneau.png 52×26 : 2 cellules 26×26 (prête, en recharge) ; créneau 22×22 en (1, 1) de chaque cellule.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, nouveau  # noqa: E402

S = ea.Sortie("combat", budget=20_000)


def creneau():
    im = nouveau(52, 26)
    for k in range(2):
        x, y = 26 * k + 1, 1
        kit.ombre_portee(im, x, y, 22, 22, dx=1, dy=1, force=0.45, rayon=0)
        fa.lq(im, x + 1, y + 1, 20, 20, "noir", seed=k, intensite=0.9 if k == 0 else 0.5)
        (fa.liseret if k == 0 else fa.liseret_terne)(im, x, y, 22, 22)
    S.sauver("creneau.png", im, (52, 26))


def main():
    creneau()
    S.bilan()


if __name__ == "__main__":
    main()
