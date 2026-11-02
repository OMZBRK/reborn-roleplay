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
- touche_rappel.png 44×11 (lot 2, 01/11 : rappels de touches du bas-droite, keybind/RappelsTouches ; maquettes c1 /
  c3, recette _maquettes_lot2.case_touche) : en (0, 0), la case de touche en 9 tranches de 3 texels (7×7, pixels
  écran 1:1) — coins de 2 px rognés, fond #2A2627 à 90 %, liseré #5A5254 à 90 % sur la rangée du haut (il suit les
  coins), ombre dure d'1 px en bas à droite (encre #0A0608 à 60 % × 90 %, sous le fond) ; les coins sont posés tels
  quels, les bords et le centre (1 texel) étirés : la même case à toute taille (largeur selon la touche, hauteur 22 px
  × kHud / 2). À côté, les pictogrammes posés dans la case, #F3F0EA plein : souris 7×11 en (8, 0) bouton gauche,
  (16, 0) bouton droit, (24, 0) molette, barre d'espace 12×4 en (32, 0).
- voile_centre.png 64×16 : voile de l'invite de ramassage, fondu des deux côtés, étiré. (voile_droite.png, l'ancien
  voile des notifications, est retiré au lot 2 : plus aucun fond derrière les textes du bas-droite.)
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


# Case de touche des rappels (lot 2) : couleurs des maquettes c1 / c3 (Ketto mesuré 45, 42, 43).
CASE_FOND = (0x2A, 0x26, 0x27)
CASE_LISERE = (0x5A, 0x52, 0x54)
ENCRE = (0x0A, 0x06, 0x08)          # MeBulles.OMBRE, encre du moteur TexteRaster
BLANC_HUD = (0xF3, 0xF0, 0xEA)      # MeBulles.TEXTE
CASE_ALPHA, CASE_OMBRE = 0.90, 0.60


def _masque_case(w, h):
    """Case w × h aux coins de 2 px (pixel art), comme _maquettes_lot2.masque_case."""
    m = np.ones((h, w), np.float32)
    for (yy, xx) in ((0, 0), (0, 1), (1, 0)):
        m[yy, xx] = m[yy, w - 1 - xx] = m[h - 1 - yy, xx] = m[h - 1 - yy, w - 1 - xx] = 0.0
    return m


def _couche(alphas_couleurs):
    """Composition « par-dessus » (alpha prémultiplié) de couches (alpha 0..1, RGB 0..255), du bas vers le haut, en
    RGBA 8 bits à alpha droit arrondi comme poser_forme (floor(v × 255 + 0,5))."""
    A = np.zeros(alphas_couleurs[0][0].shape, np.float32)
    C = np.zeros(A.shape + (3,), np.float32)
    for a, rgb in alphas_couleurs:
        col = np.array(rgb, np.float32) / 255.0
        C = col[None, None, :] * a[..., None] + C * (1.0 - a[..., None])
        A = a + A * (1.0 - a)
    out = np.zeros(A.shape + (4,), np.float32)
    out[..., :3] = np.where((A > 1e-6)[..., None], C / np.maximum(A, 1e-6)[..., None], 0)
    out[..., 3] = A
    return Image.fromarray(np.clip(np.floor(out * 255 + 0.5), 0, 255).astype(np.uint8), "RGBA")


def case_rappel(w=22, h=22):
    """La case complète (w + 1) × (h + 1), ombre comprise : couche de la case (ombre puis fond, poser_forme avec
    contour = 0, ombre = 0,6, alpha = 0,9) puis couche du liseré par-dessus (deux poses successives des maquettes)."""
    m = _masque_case(w, h)
    ombre = np.zeros((h + 1, w + 1), np.float32)
    ombre[1:, 1:] = m
    fond = np.zeros_like(ombre)
    fond[:h, :w] = m
    case = _couche([(ombre * CASE_OMBRE * CASE_ALPHA, ENCRE), (fond * CASE_ALPHA, CASE_FOND)])
    lis = np.zeros_like(ombre)
    lis[0, :w] = m[0, :]
    lis[1, 1] = lis[1, w - 2] = 1.0
    return Image.alpha_composite(case, _couche([(lis * CASE_ALPHA, CASE_LISERE)]))


def masque_souris(bouton):
    """Souris 7 × 11 px : bouton gauche (G) ou droit (D) plein, ou molette (M) — _maquettes_lot2.masque_souris."""
    m = np.zeros((11, 7), np.float32)
    m[0, 1:6] = m[10, 1:6] = 1.0
    m[1:10, 0] = m[1:10, 6] = 1.0
    m[4, :] = 1.0
    if bouton == "G":
        m[1:4, 3] = 1.0
        m[1:4, 1:3] = 1.0
    elif bouton == "D":
        m[1:4, 3] = 1.0
        m[1:4, 4:6] = 1.0
    else:
        m[1:3, 3] = 1.0
    return m


def masque_espace():
    """« ⎵ » 12 × 4 px — _maquettes_lot2.masque_espace."""
    m = np.zeros((4, 12), np.float32)
    m[3, :] = 1.0
    m[0:3, 0] = m[0:3, 11] = 1.0
    return m


def touche_rappel():
    a = np.zeros((11, 44, 4), np.uint8)
    w, h = 22, 22
    case = np.asarray(case_rappel(w, h))
    rangs = [0, 1, 2, 3, h - 2, h - 1, h]        # 9 tranches : 3 texels, 1 étiré, 3 (dont l'ombre)
    a[0:7, 0:7] = case[np.ix_(rangs, rangs)]
    for x0, m in ((8, masque_souris("G")), (16, masque_souris("D")), (24, masque_souris("M")), (32, masque_espace())):
        hh, ww = m.shape
        zone = a[0:hh, x0:x0 + ww]
        zone[m > 0] = BLANC_HUD + (255,)
    S.sauver("touche_rappel.png", Image.fromarray(a, "RGBA"), (44, 11))


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
    touche_rappel()
    voile("voile_centre.png", True)
    S.bilan()
