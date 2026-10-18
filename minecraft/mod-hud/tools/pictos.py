# -*- coding: utf-8 -*-
"""
pictos.py — petits pictogrammes pixel art communs aux générateurs (18/10, DA §3 : aucun kanji décoratif).

Remplacent les kanji des onglets pendus (fiche, réglages), des plaques de fermeture, des sceaux de blessure, de la
bibliothèque, de l'échoppe, du menu d'interaction, de la chirurgie et de l'Échap. Même convention que les icônes de la sacoche
(tools/gen_sacoche_textures.py, 15/10) : « # » = trait à la couleur donnée, « o » = remplissage assombri (55 %),
« . » = vide ; ombre portée d'1 px en bas à droite si demandée.

    from pictos import ico_px, encre_px
    ico_px(img, cx, y_haut, "video", couleur, ombre="050304")
    encre_px(masque_bool, cx, cy, "brulure")    # encre d'un sceau (un seul ton)
"""
from __future__ import annotations

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fiche_art import hexc, rect  # noqa: E402

ICONES = {
    # ── onglets de la fiche shinobi ──
    "identite": ["....####....", "...#oooo#...", "...#oooo#...", "...#oooo#...", "....####....", "............",
                 "..########..", ".#oooooooo#.", "#oooooooooo#", "############"],
    "attributs": ["....####....", "..########..", ".#oooooooo#.", ".##########.", ".#oooooooo#.", ".##########.",
                  ".#oooooooo#.", "..########..", "....####....", ".....##....."],
    "techniques": [".##########.", "#o#oooooo#o#", "#o#o####o#o#", "#o#oooooo#o#", "#o#o###oo#o#", "#o#oooooo#o#",
                   "#o#o####o#o#", "#o#oooooo#o#", ".##########."],
    "etat": [".###...###.", "#ooo#.#ooo#", "#oooo#oooo#", "#ooooooooo#", ".#ooooooo#.", "..#ooooo#..",
             "...#ooo#...", "....#o#....", ".....#....."],
    # ── onglets des réglages ──
    "video": ["############", "#oooooooooo#", "#oooooooooo#", "#oooooooooo#", "#oooooooooo#", "############",
              ".....##.....", "...######..."],
    "audio": [".....#......", "....##...#..", "###o#..#..#.", "#ooo#...#.#.", "#ooo#...#.#.", "#ooo#...#.#.",
              "###o#..#..#.", "....##...#..", ".....#......"],
    "controles": [".##########.", "#oooooooooo#", "#o#oooooo#o#", "####oooo#o##", "#o#oooooo#o#",
                  "#oooooooooo#", ".###....###."],
    "camera": ["...####.....", "############", "#oooo##oooo#", "#ooo#oo#ooo#", "#ooo#oo#ooo#", "#oooo##oooo#",
               "############"],
    "interface": ["############", "#o#oooooooo#", "############", "#oo#ooooooo#", "#oo#ooooooo#", "#oo#ooooooo#",
                  "############"],
    "discord": [".##########.", "#oooooooooo#", "#oo#oo#oo#o#", "#oooooooooo#", ".##########.", "..##........",
                "..#........."],
    "compte": ["....####....", "...#oooo#...", "...#oooo#...", "...#oooo#...", "....####....", "............",
               "..########..", ".#oooooooo#.", "#oooooooooo#", "############"],
    "minecraft": [".....#.....", "...##o##...", ".##ooooo##.", "#ooooooooo#", "###ooooo###", "#..##o##..#",
                  "#....#....#", ".##..#..##.", "...#####...", ".....#....."],
    # ── fermer (comme la sacoche) ──
    "fermer": ["##......##", "###....###", ".###..###.", "..######..", "...####...", "...####...", "..######..",
               ".###..###.", "###....###", "##......##"],
    # ── bibliothèque ──
    "reglages": ["...#..#...", ".#.####.#.", "..##oo##..", "###o..o###", "..#o..o#..", "..#o..o#..",
                 "###o..o###", "..##oo##..", ".#.####.#.", "...#..#..."],
    "journal": [".#########", "#ooooooo#.", "#o#####o#.", "#ooooooo#.", "#o####oo#.", "#ooooooo#.", "#o#####o#.",
                "#ooooooo#.", ".########."],
    "cadenas": ["...####...", "..#....#..", "..#....#..", "..#....#..", "##########", "#oooooooo#", "#oooo#ooo#",
                "#oooo#ooo#", "#oooooooo#", "##########"],
}

# Encres de sceau (un seul ton, posées en réserve dans la laque) : blessures, technique de base, résultat.
ENCRES = {
    "contusion": [".....#.....", ".#...#...#.", "..#.###.#..", "...#####...", "..#######..", "###########",
                  "..#######..", "...#####...", "..#.###.#..", ".#...#...#.", ".....#....."],
    "brulure": ["....#....", "....##...", "...###...", "...####..", "..#####.#", ".########", ".##.#####",
                "##..#####", "##...####", ".##..###.", "..#####.."],
    "fracture": [".##.......##.", "####.....####", ".#####.#####.", "####.....####", ".##.......##."],
    "plaie": [".........##", "........##.", ".......##..", "......##...", ".....##....", "....##.....",
              "...##...#..", "..##...###.", ".##....###.", "##......#.."],
    "infection": ["....#....", "....#....", "...###...", "...###...", "..#####..", ".#######.", "###.#####",
                  "##.######", "#########", ".#######.", "..#####.."],
    "cadenas": ["....######....", "...#......#...", "..#........#..", "..#........#..", "..#........#..",
                "##############", "##############", "######..######", "######..######", "######..######",
                "##############", "##############"],
    "tenue": ["..###..###..", "############", "############", "...######...", "...######...", "...######...",
              "...######..."],
    "joueur": ["...####...", "..######..", "..######..", "...####...", "..........", ".########.", "##########",
               "##########"],
    "corps": [".##.........", "####.######.", "####.#######", ".##..######.", "............", "############"],
    "soi": ["...####...", "..######..", "..######..", "...####...", "..........", ".########.", "##########",
            "..........", "..######.."],
    "chose": ["##########", "##########", "..........", ".########.", ".###..###.", ".########.", ".########."],
    "base": ["..#..", ".###.", "#####", ".###.", "..#.."],
    "reussi": ["........##", ".......###", "......###.", "##...###..", "###.###...", ".#####....", "..###.....",
               "...#......"],
    "echoue": ["##......##", "###....###", ".###..###.", "..######..", "...####...", "...####...", "..######..",
               ".###..###.", "###....###", "##......##"],
}


def taille(nom):
    rows = ICONES.get(nom) or ENCRES[nom]
    return max(len(r) for r in rows), len(rows)


def ico_px(img, cx, y, nom, couleur, ombre=None, echelle=1):
    """Pose l'icône {nom} centrée en cx, haut en y ; « o » = trait assombri ; ombre portée d'1 px (bas droite)."""
    rows = ICONES[nom]
    w = max(len(r) for r in rows) * echelle
    x0 = int(round(cx - w / 2))
    c = couleur if isinstance(couleur, tuple) else hexc(couleur)
    sombre = tuple(int(v * 0.55) for v in c[:3]) + (255,)
    if ombre is not None:
        oc = ombre if isinstance(ombre, tuple) else hexc(ombre)
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    rect(img, x0 + i * echelle + 1, y + j * echelle + 1, echelle, echelle, oc)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                rect(img, x0 + i * echelle, y + j * echelle, echelle, echelle, c if ch == "#" else sombre)


def ico_milieu(img, cx, cy, nom, couleur, ombre=None):
    """Comme ico_px, centrée verticalement en cy."""
    _, h = taille(nom)
    ico_px(img, cx, int(round(cy - h / 2)), nom, couleur, ombre)


def encre_px(T, nom):
    """Masque booléen T×T de l'encre {nom}, centrée (même placement que le kanji unifont des sceaux)."""
    rows = ENCRES[nom]
    w, h = taille(nom)
    m = np.zeros((T, T), bool)
    x0, y0 = (T - w + 1) // 2, (T - h + 1) // 2
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#" and 0 <= y0 + j < T and 0 <= x0 + i < T:
                m[y0 + j, x0 + i] = True
    return m
