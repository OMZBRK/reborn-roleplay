# -*- coding: utf-8 -*-
"""
gen_tablist_textures.py — textures de la liste des joueurs Reborn (menu/tablist/TablistScreen) au niveau de la
fiche shinobi.

Signature de l'écran : le registre (名簿, meibo) — une planche laquée posée sur le jeu (qui reste visible
autour), son sceau vermillon, ses onglets pendus (Connaissances, Amis, Staff, Général) et ses lignes à filets.
Le panneau reste discret : laque à 90 %, ombre en paliers, pas de voile lourd.

1 texel = 1 GUI. Le texte est posé par le code (DaKit).

Usage (depuis minecraft/mod-hud) :  python tools/gen_tablist_textures.py

Contrat :
- planche.png 664×424 : panneau laqué 656×416 en (2, 2) à 90 % (ombre comprise) ; coupe capD = capB = 18.
- onglets.png 160×60 : 3 rangées de 20 (repos, survol, actif) ; plaque 156×16 en (1, 1) ; coupe horizontale capD 6.
- lignes.png 640×40 : 2 rangées de 20 (survol, choisie) ; bande 636×18 en (1, 1) ; coupe horizontale capD 6.
- champ.png 260×18 : champ de recherche (rainure laquée, liseré or éteint) 256×14 en (1, 1), coupe horizontale
  capD 6.
- fiche.png 336×180 : carte de washi 328×172 en (2, 2) (ombre comprise), bande vermillon ; coupe capD = capB = 14.
- pieces.png 96×32 : sceau 名 20×20 (1, 1) ; marqueurs 9×9 : soi (24, 0), staff (34, 0), ami (44, 0),
  connaissance (54, 0) ; barres de ping 15×9 : 4 crans (64, 0) (64, 10) (64, 20) et vide (80, 0) ;
  rail 4×20 (24, 12) ; curseur 6×12 (30, 12).
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, coller, R_OR, R_LAQUE, nouveau  # noqa: E402

S = ea.Sortie("tablist", budget=300_000)


def planche():
    im = nouveau(664, 424)
    fa.panneau_opacite(im, 2, 2, 656, 416, 0.90, seed=51)
    S.sauver("planche.png", im, (664, 424))


def onglets():
    im = nouveau(160, 60)
    for r, etat in enumerate(("repos", "survol", "actif")):
        x, y, w, h = 1, r * 20 + 1, 156, 16
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=2, force=0.5, rayon=0)
        if etat == "actif":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=r, intensite=1.3)
            fa.liseret(im, x, y, w, h)
            fa.ferrures3(im, x, y, w, h)
        elif etat == "survol":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=1.7)
            fa.liseret(im, x, y, w, h)
        else:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=r, intensite=0.8)
            fa.liseret_terne(im, x, y, w, h)
    S.sauver("onglets.png", im, (160, 60))


def lignes():
    im = nouveau(640, 40)
    for r, etat in enumerate(("survol", "choisie")):
        x, y, w, h = 1, r * 20 + 1, 636, 18
        if etat == "survol":
            fa.lq(im, x, y, w, h, ["211418", "2A1A1F", "362228", "4A2E35"], seed=2, intensite=0.8)
            rect(im, x, y, w, 1, hexc(R_OR[2]))
            rect(im, x, y + h - 1, w, 1, hexc(R_OR[2]))
        else:
            fa.lq(im, x, y, w, h, ["3A0A12", "4E0D17", "63111D", "7A1322", "8E1628"], seed=3, intensite=1.0)
            fa.liseret(im, x, y, w, h)
            for j in range(h):
                rect(im, x + 1, y + j, 2, 1, hexc(R_OR[5] if j % 3 else R_OR[6]))
    S.sauver("lignes.png", im, (640, 40))


def champ():
    im = nouveau(260, 18)
    x, y, w, h = 1, 1, 256, 14
    fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=4, intensite=0.4, reflet=False)
    rect(im, x, y, w, 1, hexc(R_OR[1]))
    rect(im, x, y, 1, h, hexc(R_OR[1]))
    rect(im, x, y + h - 1, w, 1, hexc(R_OR[3]))
    rect(im, x + w - 1, y, 1, h, hexc(R_OR[3]))
    rect(im, x + 1, y + 1, w - 2, 1, hexc("050304"))
    S.sauver("champ.png", im, (260, 18))


def fiche():
    im = nouveau(336, 180)
    kit.ombre_portee(im, 2, 2, 328, 172, dx=2, dy=3, force=0.55, rayon=2)
    kit.washi(im, 2, 2, 328, 172, "creme", seed=61, bords="nets", ombre=False, taches=False)
    rect(im, 2, 2, 328, 3, hexc("8E1628"))
    rect(im, 2, 5, 328, 1, hexc("5C0E18"))
    S.sauver("fiche.png", im, (336, 180))


def pieces():
    im = nouveau(96, 32)
    gf.hanko_px(im, 11, 11, "名", 20, fa.R_VERM, seed=3, usure=0.06)
    # marqueurs 9×9
    def spr(ox, rows, pal):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in pal:
                    rect(im, ox + i, j, 1, 1, hexc(pal[ch]))
    disque = ["..aaaaa..", ".abbbbba.", "abbcccbba", "abcccccba", "abcccccba", "abcccccba", "abbcccbba", ".abbbbba.", "..aaaaa.."]
    spr(24, disque, {"a": "5A4E46", "b": "E8DCC0", "c": "FFFFFF"})                       # soi
    etoile = ["....a....", "...aba...", "...aba...", "aaabcbaaa", ".abcccba.", "..abcba..", ".abba.ab.", ".aba..aba", ".a.....a."]
    spr(34, etoile, {"a": "6A4E24", "b": "D9A95E", "c": "FFF2C8"})                       # staff
    spr(44, disque, {"a": "1E3A2A", "b": "467560", "c": "7CAA94"})                       # ami
    anneau = ["..aaaaa..", ".abbbbba.", "abba.abba", "aba...aba", "aba...aba", "aba...aba", "abba.abba", ".abbbbba.", "..aaaaa.."]
    spr(54, anneau, {"a": "6E1511", "b": "D2523E"})                                      # connaissance
    # barres de ping (4 crans) : 3 couleurs selon la qualité, + vide
    for k, (col, n) in enumerate((("7CAA94", 4), ("D9A95E", 2), ("D2523E", 1))):
        oy = k * 10
        for b in range(4):
            hh = 2 + b * 2
            c = col if b < (4 if k == 0 else n) else "3A2C26"
            rect(im, 64 + b * 4, oy + 9 - hh, 3, hh, hexc(c))
            rect(im, 64 + b * 4, oy + 9 - hh, 3, 1, hexc(fa.mix(c, "FFFFFF", 0.3)))
    for b in range(4):
        hh = 2 + b * 2
        rect(im, 80 + b * 4, 9 - hh, 3, hh, hexc("3A2C26"))
    # rail + curseur
    rect(im, 24, 12, 4, 20, hexc("140C0F"))
    rect(im, 24, 12, 1, 20, hexc(R_OR[2]))
    rect(im, 27, 12, 1, 20, hexc(R_OR[1]))
    fa.lq(im, 31, 13, 4, 10, "rouge", seed=1, reflet=False)
    fa.liseret(im, 30, 12, 6, 12)
    S.sauver("pieces.png", im, (96, 32))


def main():
    planche()
    onglets()
    lignes()
    champ()
    fiche()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
