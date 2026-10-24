# -*- coding: utf-8 -*-
"""Icônes d'état de la zone d'effets du HUD (GAMEPLAN §4, 19/10). Aucun kanji (DA §3), pixel art léger (DA §6).

Mêmes règles que ``gen_nourriture_textures.py`` : un fichier 10×10 par icône, nom stable,
``src/main/resources/assets/reborn/textures/gui/effets/<nom>.png`` ; remplaçables par des icônes peintes (16 ou
32 px, bande verticale = animation) sans toucher au code (``nourriture/EffetsIcones``).

    python tools/gen_etats_icones.py
"""
import os

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "effets")


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def grille(rows, pal):
    im = Image.new("RGBA", (10, 10), (0, 0, 0, 0))
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch in pal:
                im.putpixel((i, j), hexc(pal[ch]))
    return im


PICTOS = {
    # ATA (affaibli après un KO) : cœur fendu, rouge éteint
    "ata": ([".aa..aa...", "abbaa.ba..", "abbba.bba.", "abbb.bbba.", "abbba.bba.", ".abb.bba..",
             "..ab.ba...", "...a.a....", "....a.....", ".........."],
            {"a": "4A1418", "b": "A8403C"}),
    # menottes : deux anneaux d'acier liés
    "menottes": (["..........", ".aaa..aaa.", "a...aa...a", "a..a..a..a", "a...aa...a", ".aaa..aaa.",
                  "..........", "..........", "..........", ".........."],
                 {"a": "B4B8BC"}),
    # prison : lucarne à barreaux
    "prison": (["..........", "aaaaaaaaa.", ".b.b.b.b..", ".b.b.b.b..", ".b.b.b.b..", ".b.b.b.b..",
                ".b.b.b.b..", "aaaaaaaaa.", "..........", ".........."],
               {"a": "8A8278", "b": "C4BCB2"}),
    # étourdi : trois éclats qui tournent
    "etourdi": (["....a.....", "...aba....", "....a.....", ".a......a.", "aba....aba", ".a......a.",
                 "..........", "....a.....", "...aba....", "....a....."],
                {"a": "E8C27A", "b": "FFF2D0"}),
    # douleur : éclat brisé, vermillon
    "douleur": (["....a.....", "....aa....", ".a..aba...", "..aabba.a.", "...abbbaa.", ".aabbba...",
                 "...abaa...", "..aa..a...", "..a.......", ".........."],
                {"a": "8E2A22", "b": "D2523E"}),
    # soin : croix douce
    "soin": (["..........", "...aaaa...", "...abba...", ".aaabbaaa.", ".abbbbbba.", ".abbbbbba.",
              ".aaabbaaa.", "...abba...", "...aaaa...", ".........."],
             {"a": "3E5A3A", "b": "9CC890"}),
}


def main():
    os.makedirs(MOD, exist_ok=True)
    for nom, (rows, pal) in PICTOS.items():
        grille(rows, pal).save(os.path.join(MOD, nom + ".png"))
    print("HUD :", len(PICTOS), "icônes d'état dans", os.path.normpath(MOD))


if __name__ == "__main__":
    main()
