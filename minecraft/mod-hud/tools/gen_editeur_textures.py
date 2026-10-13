# -*- coding: utf-8 -*-
"""
gen_editeur_textures.py — textures de l'éditeur de HUD (ui/HudEditScreen, HudEditSidePanel, HudHelpScreen) au
niveau de la fiche shinobi (DA §4.1, §6), version épurée.

Signature de l'écran : les outils du charpentier — l'équerre graduée (sashigane, 差金) et le cordeau à encre
(sumitsubo, 墨壺). L'emblème (équerre + encrier) marque l'en-tête du panneau, la règle graduée d'or sert de
filet sous l'en-tête, et la grille du canevas est tracée comme des lignes de cordeau. Le panneau latéral est une
planche laquée étroite ; le jeu (et le HUD qu'on déplace) reste visible.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_editeur_textures.py

Contrat :
- panneau.png 156×532 : panneau laqué 148×524 en (2, 2) ; coupe côté code (capD = capB = 18).
- languette.png 22×532 : panneau replié 14×524 en (2, 2) ; coupe verticale (capB 18).
- pieces.png 160×64 :
    emblème 24×18 (0, 0) (équerre + encrier) ;
    règle graduée 132×5 (0, 20) (coupe horizontale capD 6) ;
    chevrons 5×9 : replier (28, 0), déplier (34, 0) ;
    poignée de redimensionnement 7×7 (40, 0) ;
    losange 3×3 (48, 1) ; cabochon 42×12 (60, 0).
- boutons.png 140×76 : 4 rangées de 19 (repos, survol, actif, inactif) ; plaque 136×15 en (1, 1) ; coupe
  horizontale capD 6.
- lignes.png 140×32 : 2 rangées de 16 (survol, choisie) ; bande 136×14 en (1, 1) ; coupe horizontale capD 6.
- champ.png 140×19 : champ 136×15 en (1, 1) ; coupe horizontale capD 6.
- aide.png 384×300 : feuillet de l'aide (panneau laqué 376×292 en (2, 2)) avec l'emblème en tête.
"""
from __future__ import annotations

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, fa, kit, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("editeur", budget=200_000)

ACIER = ["2A2A2E", "3E3E44", "56565E", "72727A", "909098", "B4B4BA"]


def embleme(px, ox, oy):
    """Équerre de charpentier (acier, graduée) posée sur un encrier sumitsubo en bois (24×18)."""
    # encrier : petite barque de bois sombre, roue du cordeau, cordeau tendu
    for j, row in enumerate([
        "..........aaaaaa......",
        "........aabbbbbbaa....",
        ".......abbccccccbba...",
        "......abbcddddddcbba..",
        ".....aabbccccccccbbaa.",
        "....aabbbbbbbbbbbbbbba",
        "....aaaaaaaaaaaaaaaaa.",
    ]):
        for i, ch in enumerate(row):
            c = {"a": "2C170C", "b": "955C35", "c": "BC8552", "d": "0A0608"}.get(ch)
            if c:
                rect(px, ox + i + 2, oy + 11 + j, 1, 1, hexc(c))
    kit.anneau_lisse(px, ox + 14, oy + 14, 2.6, 1.2, R_OR[5])            # roue du cordeau (or)
    rect(px, ox + 14, oy + 14, 1, 1, hexc(R_OR[7]))
    # cordeau d'encre qui part vers la gauche
    rect(px, ox, oy + 15, 6, 1, hexc("E8DCC0"))
    # équerre : deux branches d'acier à liseré clair, graduations
    rect(px, ox + 3, oy, 2, 13, hexc(ACIER[3]))
    rect(px, ox + 3, oy, 1, 13, hexc(ACIER[5]))
    rect(px, ox + 3, oy, 14, 2, hexc(ACIER[3]))
    rect(px, ox + 3, oy, 14, 1, hexc(ACIER[5]))
    for k in range(6, 17, 2):
        rect(px, ox + k, oy + 2, 1, 1, hexc(ACIER[1]))
    for k in range(3, 13, 2):
        rect(px, ox + 5, oy + k, 1, 1, hexc(ACIER[1]))


def regle(px, ox, oy, w=132):
    """Règle graduée d'or (5 de haut) : filet or, graduation tous les 4, grande tous les 20."""
    rect(px, ox, oy + 4, w, 1, hexc(R_OR[2]))
    rect(px, ox, oy + 3, w, 1, hexc(R_OR[4]))
    for x in range(0, w, 4):
        h = 3 if x % 20 == 0 else 1
        rect(px, ox + x, oy + 3 - h, 1, h, hexc(R_OR[5] if h == 3 else R_OR[3]))


def panneau():
    S.sauver("panneau.png", ja.fenetre(148, 524, 0.94, seed=61), (156, 532))
    S.sauver("languette.png", ja.fenetre(14, 524, 0.94, seed=62), (22, 532))


def pieces():
    im = nouveau(160, 64)
    embleme(im, 0, 0)
    regle(im, 0, 20)
    ja.sprite(im, 28, 0, ["a....", "ba...", ".ba..", "..ba.", "...ba", "..ba.", ".ba..", "ba...", "a...."],
              {"a": R_OR[3], "b": R_OR[6]})
    ja.sprite(im, 34, 0, ["....a", "...ab", "..ab.", ".ab..", "ab...", ".ab..", "..ab.", "...ab", "....a"],
              {"a": R_OR[3], "b": R_OR[6]})
    # poignée : coin laqué rouge bordé d'or
    fa.lq(im, 41, 1, 5, 5, "rouge", seed=3, reflet=False)
    fa.liseret(im, 40, 0, 7, 7)
    ja.losange(im, 49, 2)
    ja.cabochon(im, 60, 0, 42, 12)
    S.sauver("pieces.png", im, (160, 64))


def boutons():
    S.sauver("boutons.png", ja.plaque_etats(136, 15, ("repos", "survol", "actif", "inactif")), (140, 76))


def lignes():
    im = nouveau(140, 32)
    for r, etat in enumerate(("survol", "choisie")):
        x, y, w, h = 1, r * 16 + 1, 136, 14
        if etat == "survol":
            fa.lq(im, x, y, w, h, ["211418", "2A1A1F", "362228", "4A2E35"], seed=2, intensite=0.8)
            rect(im, x, y + h - 1, w, 1, hexc(R_OR[2]))
        else:
            fa.lq(im, x, y, w, h, ["3A0A12", "4E0D17", "63111D", "7A1322", "8E1628"], seed=3, intensite=1.0)
            fa.liseret(im, x, y, w, h)
        for j in range(h):
            rect(im, x + 1, y + j, 1, 1, hexc(R_OR[5] if j % 3 else R_OR[6]))
    S.sauver("lignes.png", im, (140, 32))


def champ():
    S.sauver("champ.png", ja.champ(136, 15), (140, 19))


def aide():
    im = ja.fenetre(376, 292, 0.95, seed=63)
    embleme(im, 16, 12)
    regle(im, 16, 36, 344)
    S.sauver("aide.png", im, (384, 300))


def main():
    panneau()
    pieces()
    boutons()
    lignes()
    champ()
    aide()
    S.bilan()


if __name__ == "__main__":
    main()
