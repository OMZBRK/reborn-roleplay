# -*- coding: utf-8 -*-
"""
gen_inventaire_textures.py — textures du lot « inventaire et ramassage » (GAMEPLAN §2-3, maquettes validées le
13/10 : Maquettes/gameplan/02_inventaire/) : HUD en jeu subtil (DA §6 règle 9), donc très peu de pièces ; tout le
reste est du texte posé par le code.

Usage (depuis minecraft/mod-hud) :  python tools/gen_inventaire_textures.py

Contrat :
- lueur.png 44×11 (texture du MONDE, teintée par la couleur de rareté au sommet ; 15/10, remplace aura.png et
  braise.png) : halo PIXEL ART 11×11 (1 texel = 1/16 de bloc, comme les objets), disque en escalier à 3 tons d'alpha
  (cœur, milieu, bord), blanc ; 4 images côte à côte : 0 = halo seul, 1-3 = halo + 1-2 pixels qui scintillent. Posé à
  plat au sol sous l'objet (sol/AuraSol), un quad par objet, un seul lot, rendu NEAREST.
- touche.png 11×11 : touche du clavier pour l'invite « [F] Ramasser » : fond nuit alpha 170, liseré or d'1 texel,
  coins rognés ; la lettre est posée par le code (ArcadePix), unités menu.
- voile_droite.png 64×16 : voile nuit très léger des notifications, transparent à gauche → 0,42 à droite (dernier
  tiers plein), ÉTIRÉ sous chaque ligne (alignée à droite).
- voile_centre.png 64×16 : voile de l'invite de ramassage, fondu des deux côtés, étiré.
- Ajoute 刀 (Arme) et 頭 (Tête) au bout de textures/gui/sacoche/kanji.png (208×16) : régénérer la sacoche aussi
  (python tools/gen_sacoche_textures.py).
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("inventaire", budget=120_000)


LUEUR = [
    "...eeeee...",
    "..eemmmee..",
    ".emmmmmmme.",
    "eemmcccmmee",
    "emmcccccmme",
    "emmcccccmme",
    "emmcccccmme",
    "eemmcccmmee",
    ".emmmmmmme.",
    "..eemmmee..",
    "...eeeee...",
]
LUEUR_ALPHA = {"e": 0.20, "m": 0.38, "c": 0.58, "s": 1.0}
# pixels qui scintillent (x, y) par image (1 à 3)
LUEUR_ETINCELLES = [[], [(3, 4)], [(7, 3), (6, 7)], [(4, 7)]]


def lueur():
    im = np.zeros((11, 44, 4), np.uint8)
    for f, pts in enumerate(LUEUR_ETINCELLES):
        for y, ligne in enumerate(LUEUR):
            for x, ch in enumerate(ligne):
                if ch == ".":
                    continue
                if (x, y) in pts:
                    ch = "s"
                im[y, f * 11 + x] = (255, 255, 255, round(LUEUR_ALPHA[ch] * 255))
    S.sauver("lueur.png", Image.fromarray(im, "RGBA"), (44, 11))


def touche():
    im = nouveau(11, 11)
    rect(im, 1, 1, 9, 9, (10, 6, 8, 170))
    o = hexc(R_OR[5])
    rect(im, 1, 0, 9, 1, o); rect(im, 1, 10, 9, 1, hexc(R_OR[3]))
    rect(im, 0, 1, 1, 9, o); rect(im, 10, 1, 1, 9, hexc(R_OR[3]))
    rect(im, 1, 1, 9, 1, hexc(R_OR[2])[:3] + (120,))
    S.sauver("touche.png", im, (11, 11))


def voile(nom, centre):
    w, h = 64, 16
    x = (np.arange(w) + 0.5) / w
    y = (np.arange(h) + 0.5) / h
    if centre:
        ax = np.clip(np.minimum(x, 1 - x) / 0.25, 0, 1) ** 1.3
        force = 0.38
    else:
        ax = np.clip(x / 0.66, 0, 1)
        force = 0.42
    ay = np.clip(np.minimum(y, 1 - y) / 0.3, 0, 1) ** 1.1
    a = np.outer(ay, ax) * force
    p = np.zeros((h, w, 4), np.uint8)
    p[..., 0], p[..., 1], p[..., 2] = 10, 6, 8
    p[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    S.sauver(nom, Image.fromarray(p, "RGBA"), (w, h))


if __name__ == "__main__":
    lueur()
    touche()
    voile("voile_droite.png", False)
    voile("voile_centre.png", True)
    S.bilan()
