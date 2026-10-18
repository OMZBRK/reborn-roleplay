# -*- coding: utf-8 -*-
"""
gen_duel_textures.py — textures du duel consenti (duel/DuelHud.java), version « HUD subtil » (retour du
13/10 : « c'est gros, faut que ce soit subtil ») : plus de plaques ni de gros boutons, seulement trois
petites pièces (deux depuis le 18/10 : le sceau kanji est retiré) ; tout le reste est du texte posé par le code.

- voile.png 64×16 : voile nuit très léger, lisse (dégradé d'alpha vers les bords), ÉTIRÉ au blit sous
  l'invitation et l'indicateur pour garder le texte lisible sur un ciel clair (DA §6 : étirement
  réservé aux textures lisses).
- pieces.png 8×4 : losange or 3×3 en (0, 0) (bouts du fil d'or), point or 1×1 en (4, 1).

Dessiné en « unités menu » (DaKit.echelleMenu), 1 texel = 1 unité.

Usage (depuis minecraft/mod-hud) :  python tools/gen_duel_textures.py
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import gf, hexc, rect, R_OR, nouveau  # noqa: E402

S = ea.Sortie("duel", budget=40_000)


def voile():
    w, h = 64, 16
    x = (np.arange(w) + 0.5) / w
    y = (np.arange(h) + 0.5) / h
    ax = np.clip(np.minimum(x, 1 - x) / 0.22, 0, 1) ** 1.6       # plateau au centre, fondu aux bouts
    ay = np.clip(np.minimum(y, 1 - y) / 0.35, 0, 1) ** 1.2
    a = np.outer(ay, ax) * 0.62
    p = np.zeros((h, w, 4), np.uint8)
    p[..., 0], p[..., 1], p[..., 2] = 12, 7, 9
    p[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    S.sauver("voile.png", Image.fromarray(p, "RGBA"), (w, h))


def pieces():
    im = nouveau(8, 4)
    gf.losange_or(im, 0, 0)
    rect(im, 4, 1, 1, 1, hexc(R_OR[5]))
    S.sauver("pieces.png", im, (8, 4))


if __name__ == "__main__":
    voile()
    pieces()
    S.bilan()
