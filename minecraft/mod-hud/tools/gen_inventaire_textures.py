# -*- coding: utf-8 -*-
"""
gen_inventaire_textures.py — textures du lot « inventaire et ramassage » (GAMEPLAN §2-3, maquettes validées le
13/10 : Maquettes/gameplan/02_inventaire/) : HUD en jeu subtil (DA §6 règle 9), donc très peu de pièces ; tout le
reste est du texte posé par le code.

Usage (depuis minecraft/mod-hud) :  python tools/gen_inventaire_textures.py

Contrat :
- aura.png 256×128 (texture du MONDE, teintée par la couleur de rareté au sommet) : halo doux 128×128 en (0, 0)
  (disque, alpha (1 − d)^1,8, lissé) ; anneau fin 128×128 en (128, 0) (ellipse lissée, ~3 texels). Posés à plat au
  sol sous l'objet (ground/AuraSol), un seul lot pour tous les objets.
- braise.png 8×8 : point de braise doux (blanc, teinté or), trois par objet Légendaire, qui montent.
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


def aura():
    im = np.zeros((128, 256, 4), np.float64)
    yy, xx = np.mgrid[0:128, 0:128].astype(float)
    d = np.sqrt(((xx + 0.5 - 64) / 63.5) ** 2 + ((yy + 0.5 - 64) / 63.5) ** 2)
    a = np.clip(1 - d, 0, 1) ** 1.8
    im[:, :128, :3] = 255
    im[:, :128, 3] = a * 255
    # anneau : sur-échantillonné ×4 puis réduit (lissé au pixel réel)
    big = Image.new("L", (512, 512), 0)
    dr = ImageDraw.Draw(big)
    dr.ellipse([4 * 18, 4 * 18, 4 * 110, 4 * 110], outline=255, width=10)
    ring = np.asarray(big.resize((128, 128), Image.LANCZOS)).astype(float) / 255.0
    # léger halo intérieur pour que l'anneau ne soit pas sec
    im[:, 128:, :3] = 255
    im[:, 128:, 3] = np.clip(ring, 0, 1) * 255
    S.sauver("aura.png", Image.fromarray(np.clip(im, 0, 255).astype(np.uint8), "RGBA"), (256, 128))


def braise():
    yy, xx = np.mgrid[0:8, 0:8].astype(float)
    d = np.sqrt((xx + 0.5 - 4) ** 2 + (yy + 0.5 - 4) ** 2) / 4.0
    a = np.clip(1 - d, 0, 1) ** 1.4
    p = np.zeros((8, 8, 4), np.uint8)
    p[..., 0], p[..., 1], p[..., 2] = 255, 246, 220
    p[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    S.sauver("braise.png", Image.fromarray(p, "RGBA"), (8, 8))


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
    aura()
    braise()
    touche()
    voile("voile_droite.png", False)
    voile("voile_centre.png", True)
    S.bilan()
