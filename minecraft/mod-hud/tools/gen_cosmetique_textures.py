# -*- coding: utf-8 -*-
"""
gen_cosmetique_textures.py — textures du repositionnement des cosmétiques (menu/cosmetic/RepositionScreen, ouvert
depuis la sacoche) au niveau de la fiche shinobi (DA §4.1, §6), version épurée.

Signature de l'écran : la pelote à épingles du tailleur (針刺し, harisashi) — un coussinet de soie vermillon
piqué d'épingles à tête d'or, en tête du panneau de configuration. Le joueur reste debout dans le vrai monde,
le gizmo (axes X/Y/Z) garde ses couleurs fonctionnelles.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_cosmetique_textures.py

Contrat :
- panneau.png 186×196 : panneau laqué 178×188 en (2, 2) ; coupe verticale côté code (capB 18).
- liste.png 172×172 : panneau de liste déroulante 164×164 en (2, 2) ; coupe capD = capB = 12.
- pieces.png 96×32 : pelote 20×16 (0, 0) ; chevron 7×4 (22, 0) ; icônes 12×12 de la barre : déplacer (32, 0),
  tourner (44, 0), échelle (56, 0), masquer (68, 0) ; mêmes icônes allumées en (32 + 12·i, 14).
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("cosmetique", budget=60_000)


def panneau():
    S.sauver("panneau.png", ja.fenetre(178, 188, 0.93, seed=111), (186, 196))
    S.sauver("liste.png", ja.fenetre(164, 164, 0.97, seed=112), (172, 172))


def pieces():
    im = nouveau(96, 32)
    # pelote : coussinet de soie vermillon (dôme), base laquée, épingles à tête d'or
    ja.sprite(im, 0, 4, [
        "....aaaaaaaaaaa.....",
        "..aabbbbbbbbbbbaa...",
        ".abbccbbbbbbccbbba..",
        "abbbbbbbbbbbbbbbbba.",
        "abbbbbbbbbbbbbbbbba.",
        ".aaaaaaaaaaaaaaaaa..",
        ".ddddddddddddddddd..",
        ".deeeeeeeeeeeeeeed..",
        "..ddddddddddddddd...",
    ], {"a": "5C0E18", "b": "A0182B", "c": "C84050", "d": "1A1013", "e": R_OR[3]})
    for (x, top) in ((5, 0), (9, 1), (13, 0), (16, 2)):
        rect(im, x, top + 1, 1, 4, hexc("B4B4BA"))
        rect(im, x, top, 1, 1, hexc(R_OR[6]))
    # chevron ▾
    ja.sprite(im, 22, 0, ["aaaaaaa", ".aaaaa.", "..aaa..", "...a..."], {"a": R_OR[5]})
    icones = [
        [".....a......", "....aaa.....", ".....a......", "..a..a..a...", ".aaaaaaaaa..", "..a..a..a...",
         ".....a......", "....aaa.....", ".....a......", "............", "............", "............"],
        ["....aaaa....", "..aa....a...", ".a.......a..", ".a......aaa.", "a.........a.", "a...........",
         "a...........", ".a.......a..", ".a.......a..", "..aa...aa...", "....aaa.....", "............"],
        ["aaaaaa......", "a....a......", "a....a......", "a....a......", "a....a......", "aaaaaa......",
         "......a.....", ".......a..a.", "........a.a.", ".......aaaa.", "............", "............"],
        ["...aaaaa....", "..a.....a...", ".a.......a..", "a...a.....a.", "a....a....a.", "a.....a...a.",
         "a......a..a.", ".a......aa..", "..a.....aa..", "...aaaaa....", "............", "............"],
    ]
    for i, rows in enumerate(icones):
        ja.sprite(im, 32 + 12 * i, 0, rows, {"a": "C2B59A"})
        ja.sprite(im, 32 + 12 * i, 14, rows, {"a": R_OR[6]})
    S.sauver("pieces.png", im, (96, 32))


def main():
    panneau()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
