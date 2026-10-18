# -*- coding: utf-8 -*-
"""
gen_interaction_textures.py — textures du menu d'interaction (interaction/InteractionMode.java).

Même kit que la fiche : planche de laque noire à liseré or, gorge et filet rouge, ferrures kanagu ;
bandeau de laque rouge pour le nom de la cible avec un sceau qui dit à qui l'on s'adresse (picto : un
joueur, un corps, soi, un bloc ou une chose ; plus de kanji depuis le 18/10, DA §3) ; ligne survolée en laque rouge sombre
à réglette d'or ; chevron d'or des sous-menus ; étiquette du mode curseur avec sa touche en cabochon.

Le menu est dessiné en « unités menu » (DaKit.echelleMenu : la taille de l'échelle 2 en 1080p),
1 texel = 1 unité.

Usage (depuis minecraft/mod-hud) :  python tools/gen_interaction_textures.py

Contrat :
- panneau.png 208×232 : planche 200×224 au texel (2, 2), bandeau rouge (4, 4, 192×16), filet or
  y 20, lignes dès y 24. Coupe côté code : region (w+8)×(h+8) posée en (x−2, y−2), capD = capB = 16.
- sous_panneau.png 208×200 : planche 200×192 au texel (2, 2), sans bandeau ; même coupe.
- survol.png 196×14 : ligne survolée (coupe horizontale, capD 6).
- pieces.png 96×34 : chevron 4×7 repos (0, 0), survol (5, 0) ; sceaux 18×18 (+ombre) en
  (12 + 20·i, 0) pour joueur, corps, soi, chose ; cabochon 42×12 en (0, 22) (bords 4 / 6).
- etiquette.png 132×16 : plaquette 130×14 en (0, 0) (coupe horizontale, capD 8).
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, R_OR, nouveau  # noqa: E402
import pictos  # noqa: E402  (aucun kanji décoratif, 18/10)

S = ea.Sortie("interaction", budget=200_000)
SCEAUX = ["joueur", "corps", "soi", "chose"]


def planche(w, h, titre):
    im = nouveau(w + 8, h + 8)
    fa.panneau_opacite(im, 2, 2, w, h, 0.95, seed=61 if titre else 62, eclats=False)
    if titre:
        bx, by, bw, bh = 2 + 4, 2 + 4, w - 8, 16
        fa.lq(im, bx, by, bw, bh, "rouge", seed=8)
        rect(im, bx, by, bw, 1, hexc(R_LAQUE_ROUGE_2))
        rect(im, bx, by + bh, bw, 1, hexc(R_OR[4]))
        rect(im, bx, by + bh + 1, bw, 1, hexc("120608"))
        fa.kanagus(im, 2, 2, w, h)
    return im


R_LAQUE_ROUGE_2 = ea.R_LAQUE_ROUGE[2]


def panneaux():
    S.sauver("panneau.png", planche(200, 224, True), (208, 232))
    S.sauver("sous_panneau.png", planche(200, 192, False), (208, 200))


def survol():
    im = nouveau(196, 14)
    fa.lq(im, 0, 0, 196, 14, ["2A070D", "3A0A12", "4E0D17", "63111D", "7A1322"], seed=4, intensite=0.8)
    rect(im, 0, 0, 196, 1, hexc(R_OR[3]))
    rect(im, 0, 13, 196, 1, hexc(R_OR[2]))
    rect(im, 2, 3, 2, 8, hexc(R_OR[5]))
    rect(im, 2, 3, 1, 8, hexc(R_OR[7]))
    S.sauver("survol.png", im, (196, 14))


def chevron(im, x, y, couleurs):
    for k in range(4):
        rect(im, x + k, y + k, 1, 1, hexc(couleurs[0]))
        rect(im, x + k, y + 6 - k, 1, 1, hexc(couleurs[1] if k < 3 else couleurs[0]))


def pieces():
    im = nouveau(96, 34)
    chevron(im, 0, 0, (R_OR[3], R_OR[2]))
    chevron(im, 5, 0, (R_OR[7], R_OR[5]))
    for i, kj in enumerate(SCEAUX):
        gf.hanko_px(im, 12 + 20 * i + 9, 9, pictos.encre_px(18, kj), 18, ea.R_VERM, seed=50 + i, usure=0.06)
    ea.cabochon(im, 0, 22, 40, 10)
    S.sauver("pieces.png", im, (96, 34))


def etiquette():
    im = nouveau(132, 16)
    gf.plaquette4(im, 0, 0, 130, 14, "noir", seed=12)
    fa.ferrures3(im, 0, 0, 130, 14)
    S.sauver("etiquette.png", im, (132, 16))


if __name__ == "__main__":
    panneaux()
    survol()
    pieces()
    etiquette()
    S.bilan()
