# -*- coding: utf-8 -*-
"""
kit.py — primitives de la DA Reborn (thème Akatsuki) pour les maquettes de la fiche shinobi.

FLUX RECOMMANDÉ (= ce que fait le jeu à l'échelle GUI 2 en 1080p)
------------------------------------------------------------------
1. COUCHE PIXEL 960x540 (unités GUI) : décors, panneaux laqués, washi, bois, kanagu,
   cordelettes, onglets, sceaux, lanternes, ArcadePix à l'échelle 1 (1 px de fonte = 1 GUI).
   Tout y est dessiné pixel par pixel, sans antialiasing.
2. AGRANDISSEMENT x2 au plus proche (NEAREST) -> 1920x1080 : chaque pixel GUI = 2x2 px.
3. COUCHE FINALE 1920x1080 (pixels écran) : tout ce que le jeu rend à la résolution de
   l'écran — texte Inter (TTF antialiasé, taille 9 GUI = 18 px), kanji de la police par
   défaut (unifont, 1 px écran par px de glyphe), personnage 3D (perso.py), cercles,
   anneaux et courbes lissés au pixel réel, halos des lanternes, braises.
   Coordonnées GUI x2.

La classe ``Maquette`` encapsule ce flux : on dessine sur ``m.px`` (couche pixel courante) et
on empile des opérations « finales » (m.inter, m.kanji, m.perso, m.anneau, m.halo…) qui
seront jouées APRÈS l'agrandissement, dans l'ordre d'appel. ``m.calque()`` ouvre une
nouvelle couche pixel AU-DESSUS des opérations finales déjà empilées (ex. un panneau qui
passe devant le personnage).

    m = Maquette(fond=PAL['fond'])
    panneau_laque(m.px, 40, 60, 300, 200)
    m.inter(56, 90, "Ren Hyūga", couleur=PAL['creme'])          # GUI -> rendu en 1080p
    m.perso(perso.rendu_face(skin, 10), (480, 470))             # pieds en coordonnées GUI
    m.rendre().save("ecran.png")                                 # 1920x1080

Toutes les primitives « pixel » prennent une Image PIL RGBA (la couche 960x540 en général,
mais elles marchent sur n'importe quelle image) et des coordonnées entières.
Les primitives « lisse » (disque_lisse, anneau_lisse, trait_lisse, halo, braises) prennent
l'image FINALE et des coordonnées écran (float acceptés).

API (couche pixel)
    panneau_laque(px, x, y, w, h, ton='noir'|'rouge', modele_kanagu=None) -> zone utile
    laque(px, x, y, w, h, ton, reflet=True, intensite=1)      liseret_or(px, x, y, w, h, ep=1|2)
    kanagu(px, x, y, w, h, 'grand'|'moyen'|'petit', coins='tous')
    plaque_suspendue(px, cx, y, w, h, attache_y=0, cordon='rouge', glands=False) -> zone
    onglets_pendus(px, cx, y_attache, y, libelles, actif, survol, kanjis=None) -> [rects]
    bouton_plaque(px, x, y, w|None, h, libelle, etat='normal'|'survol'|'actif'|'inactif',
                  kanji_=None, touche=None) -> rect
    washi(px, x, y, w, h, ton='creme'|'ancien'|'kraft'|'blanc', bords='dechires'|'nets') -> masque
    bois(px, x, y, w, h, ton='sombre'|'moyen'|'clair', sens='h'|'v', planches=0, noeuds=1)
    bande_tissu(px, x, y, w, h, couleur, motif='seigaiha'|'losanges'|'uroko'|'raye'|'points'|'uni')
    hanko(px, cx, cy, taille, '忍', forme='carre'|'rond'|'ovale', negatif=True, usure=0.1)
    tampon(px, cx, cy, 'APPROUVE'|'秘', taille, couleur='vermillon'|'encre'|'indigo',
           cadre='double'|'simple'|'rond', angle=-3)
    cordelette(px, x0, y0, x1, y1, couleur, mou=0, ep=2) · noeud_agemaki · gland · anneau_metal
    lanterne(px, cx, cy, stat, taille=1, allumee=1, survol=False) -> rect   (+ halo_lanterne)
    ombre_portee(px, x, y, w, h, ...) · rect · ligne · coller · texture_mod · lanterne_mod
    arcade(px, x, y, texte, taille=10, couleur, ancre='g'|'c'|'d', ombre) -> largeur
    kanji(px, x, y, texte, taille=16, police='unifont'|'gothic'|'gras'|'song', vertical=False)
API (couche finale 1920x1080, px écran)
    inter(img, x, y, texte, taille=18, gras=False, ancre, ancre_v) · paragraphe · inter_lignes
    disque_lisse · anneau_lisse(debut, fin) · trait_lisse(courbe=True) · halo · braises
    fond_jeu(capture, voile=0.72) -> fond 1920x1080 (le jeu sous un voile) · voile_vignette
Données : PAL (palette), R_* (rampes), rampe(couleur, n), STATS (6 stats : clé, ARCADE, nom,
couleur, kanji), PALIERS (Novice -> Légendaire), GRADES (Académicien -> Kage).

Ce module ne lit QUE des fichiers (polices et textures du mod, unifont du jeu) ; il n'écrit
rien dans le dépôt.
"""
from __future__ import annotations

import math
import os
import random
import unicodedata
import warnings
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ───────────────────────── chemins ─────────────────────────
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))  # racine RBLAUNCHER
ASSETS = os.path.join(REPO, r"minecraft\mod-hud\src\main\resources\assets\reborn")
FONT_DIR = os.path.join(ASSETS, "font")
TEX_DIR = os.path.join(ASSETS, "textures")
F_ARCADE = os.path.join(FONT_DIR, "arcadepix.ttf")
F_INTER = os.path.join(FONT_DIR, "inter-medium.ttf")
F_INTER_GRAS = os.path.join(FONT_DIR, "inter-bold.ttf")
F_INTER_NOIR = os.path.join(FONT_DIR, "inter-black.ttf")
# Unifont = police par défaut de Minecraft pour les kanji (asset du jeu installé par le launcher)
UNIFONT_ZIP = r"C:\Users\omarb\AppData\Roaming\RebornRoleplay\assets\objects\cc\ccd5ac4767ce0a9c71d1dd62f2dc25449789b5dd"
F_GOTHIC = r"C:\Windows\Fonts\msgothic.ttc"     # bitmaps intégrés nets de 10 à 16 px
F_GOTHIC_GRAS = r"C:\Windows\Fonts\YuGothB.ttc"  # kanji gras (sceaux, gros caractères)
F_SONG = r"C:\Windows\Fonts\simsun.ttc"          # empattements (style « mincho »)
CAPTURES = os.path.join(REPO, r"minecraft\mod-hud\run\screenshots")

GUI_W, GUI_H, ECHELLE_GUI = 960, 540, 2

# ───────────────────────── palette ─────────────────────────
def hexc(c, a=None):
    """'#RRGGBB' | 'RRGGBB' | (r,g,b[,a]) -> (r,g,b,a)."""
    if isinstance(c, str):
        c = c.lstrip("#")
        t = (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)
    else:
        t = tuple(int(v) for v in c) + ((255,) if len(c) == 3 else ())
    if a is not None:
        t = t[:3] + (int(a),)
    return t


PAL = {k: hexc(v) for k, v in dict(
    # surfaces (menu/Colors.java)
    fond="0A0608", surface="15090B", eleve="1F0E11", bordure="2D181C", bordure_forte="4A2127",
    # Akatsuki
    rouge="A0182B", rouge_survol="C01E35", rouge_presse="7A1322", rouge_sombre="5C0E18", rouge_doux="3A0A12",
    # or
    or_="D9A95E", or_clair="F2D08A", or_pale="FFF0C4", or_moyen="C8A05A", or_fonce="9A7234",
    or_ombre="5A4220", or_doux="3A2C14",
    # texte
    creme="F5E9D0", creme_doux="C2B59A", gris="7A6E5C", gris_sombre="4A4038",
    # encres
    encre="1E1618", encre_douce="4A3C3E", vermillon="C23A2C", indigo="2A3558",
).items()}

# Rampes pixel art (sombre -> clair)
R_LAQUE = ["0A0608", "0F090B", "140C0F", "1A1013", "211418", "2A1A1F", "362228", "4A2E35", "6A444C"]
R_LAQUE_ROUGE = ["2A070D", "3A0A12", "4E0D17", "63111D", "7A1322", "8E1628", "A0182B", "B51F33", "CC3A4C", "E26474"]
R_OR = ["2E2210", "4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E", "E8C27A", "F2D59A", "FFF2C8"]
R_BOIS = {
    "sombre": ["160A06", "21100A", "2C170E", "381E12", "452616", "532F1B", "623920", "744528"],
    "moyen":  ["2C170C", "3B2012", "4B2A17", "5C351D", "6E4124", "814E2C", "955C35", "A96B3F"],
    "clair":  ["5E3E22", "70492A", "835632", "96643B", "A97446", "BC8552", "CC9760", "DBAA70"],
}
R_WASHI = {
    "creme":  ["9C8662", "B6A07A", "CDB993", "DCCAA6", "E8DABA", "F1E6CC", "F8F0DE", "FDF9EE"],
    "blanc":  ["A69E92", "C2BAAE", "D8D1C6", "E6E0D6", "EFEAE2", "F6F2EC", "FBF8F3", "FFFFFF"],
    "ancien": ["7E6444", "967A55", "AE9168", "C2A67C", "D2B88E", "DFC9A2", "EAD8B4", "F3E6C8"],
    "kraft":  ["5E4126", "74522F", "8A6339", "9C7344", "AE8450", "BE945D", "CCA36B", "D9B27A"],
}
R_VERMILLON = ["4E0C0B", "6E1511", "8E2018", "AE2C21", "C23A2C", "D2523E", "E0705A"]
R_INDIGO = ["0E1222", "161C34", "1F2848", "2A3558", "38466E", "4C5C86", "6476A0"]
R_ENCRE = ["0C090A", "1E1618", "2E2426", "4A3C3E", "6A5C5C"]
R_CORDE = {
    "rouge": ["4A0C14", "7A1322", "A42230", "D04A4E", "EC8478"],
    "or":    ["4A3518", "8E6A30", "C8A05A", "E8C27A", "FFF0C4"],
    "noir":  ["0A0608", "1A1013", "2E2024", "4A3438", "6A5054"],
    "blanc": ["8A8478", "B8B2A6", "DAD4C8", "F0ECE2", "FFFFFF"],
    "violet": ["24142E", "3E2450", "5C3878", "7E54A0", "A47CC4"],
}

# Les six stats (StatDef.java) : clé, ArcadePix, nom, couleur, kanji de lanterne
STATS = [
    ("taijutsu", "TAIJUTSU", "Taïjutsu", hexc("E8743B"), "体"),
    ("kenjutsu", "KENJUTSU", "Kenjutsu", hexc("B9C7D6"), "剣"),
    ("ninjutsu", "NINJUTSU", "Ninjutsu", hexc("9B6BFF"), "忍"),
    ("controle", "CONTROLE", "Contrôle", hexc("3CCB9A"), "制"),
    ("vigueur", "VIGUEUR", "Vigueur", hexc("E0485A"), "力"),
    ("chakra", "CHAKRA", "Chakra", hexc("3FB8F5"), "気"),
]
PALIERS = ["Novice", "Apprenti", "Confirmé", "Expert", "Maître", "Légendaire"]
GRADES = ["Académicien", "Genin", "Chūnin", "Jōnin", "Kage"]


def rampe(c, n=7, ombre=0.28, lumiere=0.55):
    """Rampe pixel art autour d'une couleur : ombres décalées vers le pourpre, lumières
    vers le jaune chaud (hue shifting). Renvoie n tuples (r,g,b,255), sombre -> clair."""
    r, g, b = [v / 255 for v in hexc(c)[:3]]
    out = []
    for i in range(n):
        t = i / (n - 1) * 2 - 1                       # -1 .. 1
        if t < 0:
            k = -t
            tr, tg, tb = r * (1 - k * (1 - ombre)), g * (1 - k * (1 - ombre)), b * (1 - k * (1 - ombre))
            tr, tb = tr + 0.10 * k * (0.35 - tr) * 0.6, tb + 0.12 * k * (0.45 - tb) * 0.6
        else:
            k = t * lumiere
            tr, tg, tb = r + (1 - r) * k, g + (0.94 - g) * k, b + (0.78 - b) * k
        out.append(tuple(int(max(0, min(255, round(v * 255)))) for v in (tr, tg, tb)) + (255,))
    return out


def _R(r):
    return [hexc(c) for c in r]


# ───────────────────────── outils numériques ─────────────────────────
_B4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], float) + 0.5) / 16


def bayer(h, w, x0=0, y0=0):
    """Matrice de Bayer 4x4 alignée sur la grille globale (les motifs se raccordent)."""
    ys = (np.arange(h) + y0) % 4
    xs = (np.arange(w) + x0) % 4
    return _B4[ys[:, None], xs[None, :]]


def quantifier(v, n, x0=0, y0=0, tramage=0.4):
    """Valeurs 0..1 -> indices 0..n-1. tramage 0..1 = largeur de la zone tramée (Bayer 4x4)
    autour de chaque seuil : 0 = aplats nets, 0,3-0,5 = aplats avec transitions tramées
    étroites (pixel art « à la main »), 1 = tramage partout."""
    v = np.clip(v, 0, 1) * (n - 1)
    k = float(tramage) if tramage is not True else 1.0
    b = 0.5 + (bayer(v.shape[0], v.shape[1], x0, y0) - 0.5) * k
    return np.clip(np.floor(v + b), 0, n - 1).astype(int)


def bruit(w, h, cellule=8.0, seed=0, octaves=2, etire=(1.0, 1.0)):
    """Bruit de valeur lissé 0..1 (h, w). etire=(sx, sy) allonge les motifs (fibres, veines)."""
    rs = np.random.RandomState(seed)
    acc = np.zeros((h, w), float)
    amp, tot = 1.0, 0.0
    c = float(cellule)
    for _ in range(octaves):
        gw = max(2, int(math.ceil(w / (c * etire[0]))) + 2)
        gh = max(2, int(math.ceil(h / (c * etire[1]))) + 2)
        g = (rs.rand(gh, gw) * 255).astype(np.uint8)
        im = Image.fromarray(g, "L").resize((int(gw * c * etire[0]), int(gh * c * etire[1])), Image.BICUBIC)
        a = np.asarray(im, float)[:h, :w] / 255.0
        if a.shape != (h, w):
            a = np.pad(a, ((0, h - a.shape[0]), (0, w - a.shape[1])), mode="edge")
        acc += a * amp
        tot += amp
        amp *= 0.5
        c /= 2.0
    acc /= tot
    lo, hi = acc.min(), acc.max()
    return (acc - lo) / (hi - lo + 1e-9)


def _patch(h, w):
    return np.zeros((h, w, 4), np.uint8)


def coller(img, patch, x, y):
    """alpha_composite d'un patch (np RGBA ou Image) à (x, y), avec découpe aux bords."""
    if isinstance(patch, np.ndarray):
        patch = Image.fromarray(patch, "RGBA")
    x, y = int(round(x)), int(round(y))
    sx, sy = max(0, -x), max(0, -y)
    dx, dy = max(0, x), max(0, y)
    w = min(patch.width - sx, img.width - dx)
    h = min(patch.height - sy, img.height - dy)
    if w <= 0 or h <= 0:
        return img
    img.alpha_composite(patch, dest=(dx, dy), source=(sx, sy, sx + w, sy + h))
    return img


def _peindre(p, masque, couleur):
    c = hexc(couleur)
    p[masque] = c
    return p


def _rampe_vers_rgba(R, idx, masque=None):
    pal = np.array([hexc(c) for c in R], np.uint8)
    out = pal[idx]
    if masque is not None:
        out[~masque] = 0
    return out


def rect(img, x, y, w, h, couleur, alpha=None):
    """Rectangle plein (pixel)."""
    if w <= 0 or h <= 0:
        return
    c = hexc(couleur, alpha)
    coller(img, Image.new("RGBA", (int(w), int(h)), c), x, y)


def ligne(img, x0, y0, x1, y1, couleur, alpha=None):
    """Segment 1 px (Bresenham)."""
    d = ImageDraw.Draw(img)
    d.line([(x0, y0), (x1, y1)], fill=hexc(couleur, alpha), width=1)


# ───────────────────────── ombres pixel ─────────────────────────
def ombre_portee(img, x, y, w, h, dx=2, dy=3, force=0.55, rayon=2, couleur=(4, 2, 3), masque=None):
    """Ombre portée en paliers de 1 px (pas de flou baveux sur la couche pixel).
    masque : np bool (h, w) pour une forme quelconque (washi aux bords déchirés…)."""
    if masque is None:
        masque = np.ones((h, w), bool)
    H, W = masque.shape
    pad = rayon + 1
    acc = np.zeros((H + 2 * pad, W + 2 * pad), float)
    m = np.zeros_like(acc, bool)
    m[pad:pad + H, pad:pad + W] = masque
    cur = m.copy()
    for r in range(rayon + 1):
        acc += cur * (force / (rayon + 1))
        nxt = cur.copy()
        nxt[1:, :] |= cur[:-1, :]
        nxt[:-1, :] |= cur[1:, :]
        nxt[:, 1:] |= cur[:, :-1]
        nxt[:, :-1] |= cur[:, 1:]
        cur = nxt
    p = np.zeros(acc.shape + (4,), np.uint8)
    p[..., :3] = couleur[:3]
    p[..., 3] = np.clip(acc * 255, 0, 255).astype(np.uint8)
    coller(img, p, x + dx - pad, y + dy - pad)


# ───────────────────────── matières ─────────────────────────
def laque(img, x, y, w, h, ton="noir", reflet=True, seed=0, rampe_=None, intensite=1.0):
    """Laque urushi : fond profond, léger dégradé vertical, reflet diagonal tramé en haut à
    gauche, filet de lumière sous l'arête haute. ton : 'noir' | 'rouge' | liste de couleurs."""
    if isinstance(ton, (list, tuple)) and not isinstance(ton, str):
        R = list(ton)
    else:
        R = rampe_ or (R_LAQUE if ton == "noir" else R_LAQUE_ROUGE)
    if w <= 0 or h <= 0:
        return
    n = len(R)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    t = 1 - yy / max(1, h - 1)
    if ton == "rouge":
        v = 0.40 + 0.16 * t
    else:
        v = 0.12 + 0.16 * t
    if reflet:
        d = (xx + yy * 0.9) / (w + h * 0.9)
        v += intensite * (0.20 * np.exp(-((d - 0.18) / 0.10) ** 2) + 0.12 * np.exp(-((d - 0.27) / 0.025) ** 2))
        v += intensite * 0.06 * np.exp(-((d - 0.80) / 0.12) ** 2)
    v += (bruit(w, h, 14, seed, 2) - 0.5) * 0.05
    idx = quantifier(v, n, x, y, tramage=0.35)
    p = _rampe_vers_rgba(R, idx)
    if reflet and h > 6 and w > 12:         # filet de lumière sous l'arête haute
        for i in range(2, int(w * 0.42)):
            p[1, i] = hexc(R[min(n - 1, int(idx[1, i]) + 1)])
    coller(img, p, x, y)


def liseret_or(img, x, y, w, h, ep=1, biseau=True):
    """Cadre doré : arêtes haute/gauche éclairées, basse/droite dans l'ombre (biseau).
    ep=2 : double filet (or + gorge sombre)."""
    O = _R(R_OR)
    for k in range(ep):
        X, Y, W, H = x + k, y + k, w - 2 * k, h - 2 * k
        if W <= 1 or H <= 1:
            break
        if k == 0:
            hi, mid, lo = (O[6], O[5], O[3]) if biseau else (O[5], O[5], O[5])
        else:
            hi, mid, lo = O[2], O[1], O[1]
        rect(img, X, Y, W, 1, hi)
        rect(img, X, Y, 1, H, hi)
        rect(img, X, Y + H - 1, W, 1, lo)
        rect(img, X + W - 1, Y, 1, H, lo)
        rect(img, X, Y + H - 1, 1, 1, mid)
        rect(img, X + W - 1, Y, 1, 1, mid)


# Sprites de ferrures dessinés à la main (triangle supérieur x >= y ; le bras gauche est
# obtenu par transposition). o contour, h reflet, l clair, m or, e ciselure, d ombre.
_KANAGU_SPEC = {
    "moyen": [            # 12x12 : bras de 4 px, bulbe lobé, rivet au coin, ciselure en points
        "oooooooooo..",
        " hlhhhhhhlo.",
        "  dmememmmmo",
        "   mooooomdo",
        "    .....oo.",
    ],
    "petit": [            # 7x7 : équerre simple + rivet (boutons, onglets)
        "oooooo.",
        " hlhmo.",
        "  doo..",
    ],
}
_KANAGU_PAL = {"o": 1, "h": 8, "l": 6, "m": 5, "e": 3, "d": 3}
_KCACHE = {}


def _decale(a, dy, dx):
    o = np.zeros_like(a)
    H, W = a.shape
    o[max(0, dy):H + min(0, dy), max(0, dx):W + min(0, dx)] = a[max(0, -dy):H + min(0, -dy), max(0, -dx):W + min(0, -dx)]
    return o


def _eroder(a):
    return a & _decale(a, 1, 0) & _decale(a, -1, 0) & _decale(a, 0, 1) & _decale(a, 0, -1)


def _kanagu_grand(T=6, L=13, R=3.5):
    """Grande ferrure (18 px) : plaque large biseautée, rainure ciselée (arête sombre +
    arête claire), embouts lobés séparés par un col, rivet au centre de l'équerre."""
    S = L + 5
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    m = np.zeros((S, S), bool)
    m[0:T, 0:L] = True
    m[0:L, 0:T] = True
    c = (T - 1) / 2
    m |= (xx - (L + 0.5)) ** 2 + (yy - c) ** 2 <= R ** 2
    m |= (yy - (L + 0.5)) ** 2 + (xx - c) ** 2 <= R ** 2
    m[0, L - 2] = m[T - 1, L - 2] = m[L - 2, 0] = m[L - 2, T - 1] = False
    m[T, T] = True
    e1 = _eroder(m)
    e2 = _eroder(e1)
    g = np.full((S, S), ".", object)
    g[m] = "o"
    r1 = e1 & ~e2
    g[r1] = "m"
    g[r1 & (~_decale(e1, 1, 0) | ~_decale(e1, 0, 1))] = "h"
    g[r1 & (~_decale(e1, -1, 0) | ~_decale(e1, 0, -1))] = "d"
    g[e2] = "m"
    haut = e2 & (~_decale(e2, 1, 0) | ~_decale(e2, 0, 1))
    g[haut] = "e"
    g[e2 & (~_decale(e2, -1, 0) | ~_decale(e2, 0, -1)) & ~haut] = "l"
    k = T // 2
    g[k, k] = "h"; g[k, k + 1] = "l"; g[k + 1, k] = "l"; g[k + 1, k + 1] = "d"
    return g


def _kanagu_sprite(modele):
    if modele in _KCACHE:
        return _KCACHE[modele]
    if modele == "grand":
        g = _kanagu_grand()
        S = g.shape[0]
    else:
        spec = _KANAGU_SPEC[modele]
        S = len(spec[0])
        g = np.full((S, S), ".", object)
        for y, row in enumerate(spec):
            for x, ch in enumerate(row):
                if x >= y and ch != " ":
                    g[y, x] = ch
                    g[x, y] = ch
    O = _R(R_OR)
    pal = dict(_KANAGU_PAL, e=2) if modele == "grand" else _KANAGU_PAL
    p = _patch(S, S)
    for y in range(S):
        for x in range(S):
            ch = g[y, x]
            if ch in pal:
                p[y, x] = O[pal[ch]]
    _KCACHE[modele] = p
    return p


def kanagu(img, x, y, w, h, modele="moyen", coins="tous", deborde=1):
    """Ferrures kanagu dorées aux coins d'un rectangle (sprites dessinés à la main) :
    équerre à bras lobés, rivet bombé, ciselure. modele : 'grand' (18 px, grands panneaux,
    rainure ciselée) | 'moyen' (12 px, panneaux, plaques) | 'petit' (7 px, boutons, onglets). coins : 'tous' ou liste parmi
    'hg', 'hd', 'bg', 'bd'. deborde : la ferrure dépasse du cadre de n px (posée dessus)."""
    spr = _kanagu_sprite(modele)
    S = spr.shape[0]
    o = deborde
    pos = {"hg": (x - o, y - o, False, False), "hd": (x + w - S + o, y - o, True, False),
           "bg": (x - o, y + h - S + o, False, True), "bd": (x + w - S + o, y + h - S + o, True, True)}
    keys = pos.keys() if coins == "tous" else coins
    for k in keys:
        px, py, fx, fy = pos[k]
        a = spr
        if fx:
            a = a[:, ::-1]
        if fy:
            a = a[::-1, :]
        coller(img, np.ascontiguousarray(a), px, py)


def panneau_laque(img, x, y, w, h, ton="noir", kanagu_=True, modele_kanagu=None, filet=True,
                  ombre=True, contour=True, seed=0, reflet=True):
    """Panneau laqué du kit : ombre en paliers, contour sombre, liseré or biseauté, gorge,
    filet rouge intérieur, laque à reflet, ferrures kanagu aux coins.
    Renvoie la zone utile intérieure (x, y, w, h)."""
    if ombre:
        ombre_portee(img, x, y, w, h, dx=0, dy=3, force=0.6, rayon=3)
    if contour:
        rect(img, x - 1, y - 1, w + 2, h + 2, PAL["fond"])
    laque(img, x + 1, y + 1, w - 2, h - 2, ton=ton, seed=seed, reflet=reflet)
    liseret_or(img, x, y, w, h, ep=1)
    gorge = hexc(R_LAQUE[0]) if ton == "noir" else hexc(R_LAQUE_ROUGE[1])
    rect(img, x + 1, y + 1, w - 2, 1, gorge)
    rect(img, x + 1, y + 1, 1, h - 2, gorge)
    rect(img, x + 1, y + h - 2, w - 2, 1, gorge)
    rect(img, x + w - 2, y + 1, 1, h - 2, gorge)
    if filet:
        fc = hexc("6A1420") if ton == "noir" else hexc(R_OR[3])
        X, Y, W, H = x + 3, y + 3, w - 6, h - 6
        rect(img, X, Y, W, 1, fc)
        rect(img, X, Y + H - 1, W, 1, fc)
        rect(img, X, Y, 1, H, fc)
        rect(img, X + W - 1, Y, 1, H, fc)
    if kanagu_:
        mk = modele_kanagu or ("grand" if min(w, h) >= 110 else ("moyen" if min(w, h) >= 28 else "petit"))
        kanagu(img, x, y, w, h, mk)
    return (x + 5, y + 5, w - 10, h - 10)


# ───────────────────────── cordes, anneaux, glands ─────────────────────────
def cordelette(img, x0, y0, x1, y1, couleur="rouge", mou=0.0, ep=2):
    """Cordelette torsadée (kumihimo) de (x0,y0) à (x1,y1), avec un ventre de `mou` px.
    ep=1 : fil simple alternant clair/sombre ; ep=2 : torsade en diagonale."""
    R = _R(R_CORDE[couleur] if isinstance(couleur, str) else couleur)
    L = max(abs(x1 - x0), abs(y1 - y0))
    n = max(2, int(L * 1.6))
    pts = []
    for i in range(n + 1):
        t = i / n
        px = x0 + (x1 - x0) * t
        py = y0 + (y1 - y0) * t + mou * 4 * t * (1 - t)
        pts.append((int(round(px)), int(round(py))))
    seen = []
    for p in pts:
        if not seen or seen[-1] != p:
            seen.append(p)
    vert = abs(y1 - y0) >= abs(x1 - x0)
    p = img.load()
    W, H = img.size
    for k, (px, py) in enumerate(seen):
        for s in range(ep):
            qx, qy = (px + s, py) if vert else (px, py + s)
            if not (0 <= qx < W and 0 <= qy < H):
                continue
            if ep == 1:
                c = R[3] if k % 2 == 0 else R[2]
            else:                                # torsade : reflet qui descend en diagonale
                c = (R[3], R[2], R[2])[k % 3] if s == 0 else (R[1], R[3], R[1])[k % 3]
            p[qx, qy] = c


def anneau_metal(img, cx, cy, r=2, metal="or"):
    """Petit anneau / œillet métallique pixel (r=2 -> 5x5)."""
    R = _R(R_OR) if metal == "or" else _R(["1A1416", "2E2828", "4A4444", "6E6A68", "9A9692", "C8C4BE", "E8E4DE", "FFFFFF", "FFFFFF"])
    s = 2 * r + 1
    yy, xx = np.mgrid[0:s, 0:s] - r
    d = np.sqrt(xx ** 2 + yy ** 2)
    m = (d <= r + 0.35) & (d >= r - 0.85)
    pa = _patch(s, s)
    pa[m] = R[5]
    pa[m & (yy < 0) & (xx <= 0)] = R[7]
    pa[m & (yy > 0) & (xx >= 0)] = R[3]
    coller(img, pa, cx - r, cy - r)


def gland(img, cx, y, long=10, couleur="rouge"):
    """Gland de soie (fusa) pendu sous un nœud : tête dorée + franges torsadées."""
    R = _R(R_CORDE[couleur] if isinstance(couleur, str) else couleur)
    O = _R(R_OR)
    rect(img, cx - 1, y, 3, 2, O[6]); rect(img, cx - 1, y + 1, 3, 1, O[4])
    rect(img, cx - 2, y + 2, 5, 1, O[3])
    for i, dx in enumerate((-2, -1, 0, 1, 2)):
        ll = long - (1 if abs(dx) == 2 else 0) - (i % 2)
        for k in range(ll):
            c = R[3] if (k + i) % 3 == 0 else (R[2] if dx <= 0 else R[1])
            rect(img, cx + dx, y + 3 + k, 1, 1, c)
    rect(img, cx - 2, y + 4, 5, 1, O[5])


def noeud_agemaki(img, cx, cy, couleur="rouge"):
    """Nœud décoratif (agemaki) 9x7 : à poser à l'attache d'une cordelette."""
    R = _R(R_CORDE[couleur] if isinstance(couleur, str) else couleur)
    spr = ["..aba.aba..", ".abccbccba.", "abc..c..cba", ".abccbccba.", "..ab.c.ba..", "...abcba...", "....aba...."]
    pal = {"a": R[1], "b": R[2], "c": R[3]}
    for j, row in enumerate(spr):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(img, cx - len(row) // 2 + i, cy - len(spr) // 2 + j, 1, 1, pal[ch])


# ───────────────────────── plaque suspendue & onglets pendus ─────────────────────────
def plaque_suspendue(img, cx, y, w, h, attache_y=0, ecart=None, cordon="rouge", ton="noir",
                     glands=False, seed=0):
    """Plaque laquée suspendue (en-tête des écrans) : deux cordelettes depuis attache_y
    jusqu'à deux œillets dorés posés sur l'arête haute. Renvoie la zone intérieure."""
    x = int(cx - w // 2)
    e = ecart if ecart is not None else w // 2 - 10
    top = int(attache_y)
    for sgn in (-1, 1):
        ax = int(cx + sgn * (e + 8))
        bx = int(cx + sgn * e)
        cordelette(img, ax - (1 if sgn < 0 else 0), top, bx - (1 if sgn < 0 else 0), y + 1, cordon, mou=0, ep=2)
    zone = panneau_laque(img, x, y, w, h, ton=ton, seed=seed, modele_kanagu="moyen")
    for sgn in (-1, 1):
        anneau_metal(img, int(cx + sgn * e), y + 1, 2)
    if glands:
        for sgn in (-1, 1):
            gx = int(cx + sgn * (w // 2 - 6))
            cordelette(img, gx, y + h, gx, y + h + 4, cordon, ep=1)
            gland(img, gx, y + h + 4, 9, cordon)
    return zone


def onglets_pendus(img, cx, y_attache, y, libelles, actif=0, survol=None, gap=8, padding=9,
                   hauteur=15, cordon="or", kanjis=None, desactives=()):
    """Rangée d'onglets pendus sous la plaque : chaque onglet est une petite plaque laquée
    accrochée par un fil et un œillet. Libellés ArcadePix (échelle 1, majuscules sans
    accents). actif = index (rouge + or vif), survol = index (laque éclaircie, or).
    kanjis : liste optionnelle de kanji (glyphe 10 px MS Gothic) à gauche du libellé.
    Renvoie [(x, y, w, h)] par onglet."""
    largeurs = []
    for i, lb in enumerate(libelles):
        lw = arcade_largeur(lb, 10)
        if kanjis and kanjis[i]:
            lw += 12
        largeurs.append(lw + 2 * padding)
    total = sum(largeurs) + gap * (len(libelles) - 1)
    x = int(cx - total // 2)
    rects = []
    for i, lb in enumerate(libelles):
        w = largeurs[i]
        ac, sv, off = i == actif, i == survol, i in desactives
        mx = x + w // 2
        dy = 2 if ac else 0                     # l'onglet actif pend un peu plus bas
        cordelette(img, mx, y_attache, mx, y + dy, cordon, ep=1)
        if ac:
            ombre_portee(img, x, y + dy, w, hauteur, dx=0, dy=2, force=0.55, rayon=2)
            laque(img, x + 1, y + dy + 1, w - 2, hauteur - 2, ton="rouge", seed=i)
            liseret_or(img, x, y + dy, w, hauteur)
            kanagu(img, x, y + dy, w, hauteur, "petit", deborde=1)
            tc, sh = PAL["creme"], True
        else:
            ombre_portee(img, x, y + dy, w, hauteur, dx=0, dy=2, force=0.45, rayon=1)
            laque(img, x + 1, y + dy + 1, w - 2, hauteur - 2, ton="noir", seed=i,
                  intensite=1.6 if sv else 0.8)
            bc = R_OR[5] if sv else ("3E2E26" if off else R_OR[3])
            c = hexc(bc)
            rect(img, x, y + dy, w, 1, c); rect(img, x, y + dy + hauteur - 1, w, 1, c)
            rect(img, x, y + dy, 1, hauteur, c); rect(img, x + w - 1, y + dy, 1, hauteur, c)
            if sv:
                kanagu(img, x, y + dy, w, hauteur, "petit", deborde=0)
            tc = PAL["creme"] if sv else (hexc("5A4E46") if off else PAL["creme_doux"])
            sh = False
        anneau_metal(img, mx, y + dy, 1)
        tx = x + padding
        if kanjis and kanjis[i]:
            kanji(img, tx, y + dy + (hauteur - 10) // 2, kanjis[i], 10, police="gothic",
                  couleur=PAL["or_clair"] if ac else (PAL["or_"] if sv else hexc(R_OR[4])))
            tx += 12
        arcade(img, tx, y + dy + (hauteur - 7) // 2, lb, 10, tc, ombre=sh)
        rects.append((x, y + dy, w, hauteur))
        x += w + gap
    return rects


# ───────────────────────── washi, bois, tissu ─────────────────────────
def washi(img, x, y, w, h, ton="creme", seed=0, bords="dechires", ombre=True, fibres=True,
          taches=True, liseret=None):
    """Papier washi : marbrure douce tramée, fibres de kōzo, bords irréguliers (déchirés
    ou nets), ombre portée en paliers. ton : 'creme' | 'blanc' | 'ancien' | 'kraft'.
    Renvoie le masque (np bool h x w) du papier."""
    R = R_WASHI[ton] if isinstance(ton, str) else ton
    n = len(R)
    rs = np.random.RandomState(seed)
    v = 0.67 + (bruit(w, h, 26, seed, 3) - 0.5) * 0.13 + (bruit(w, h, 5, seed + 1, 1) - 0.5) * 0.06
    yy, xx = np.mgrid[0:h, 0:w]
    bord_d = np.minimum.reduce([xx, yy, w - 1 - xx, h - 1 - yy]).astype(float)
    v -= 0.10 * np.exp(-bord_d / 3.0)          # bord légèrement plus dense
    if taches:
        for _ in range(max(1, (w * h) // 9000)):
            tx, ty, tr = rs.randint(0, w), rs.randint(0, h), rs.randint(4, 12)
            d = np.sqrt((xx - tx) ** 2 + ((yy - ty) * 1.3) ** 2)
            v -= 0.07 * np.exp(-(d / tr) ** 2)
    idx = quantifier(v, n, x, y, tramage=0.45)
    m = np.ones((h, w), bool)
    if bords == "dechires":
        prof = [bruit(max(w, h) + 4, 1, 3, seed + k, 2)[0] for k in range(4)]
        for i in range(w):
            a = int(round(prof[0][i] * 2.4 + (rs.rand() < 0.08)))
            b = int(round(prof[1][i] * 2.4 + (rs.rand() < 0.08)))
            m[:a, i] = False
            m[h - b:, i] = False
        for j in range(h):
            a = int(round(prof[2][j] * 2.4 + (rs.rand() < 0.08)))
            b = int(round(prof[3][j] * 2.4 + (rs.rand() < 0.08)))
            m[j, :a] = False
            m[j, w - b:] = False
        for (cy, cx) in ((0, 0), (0, w - 1), (h - 1, 0), (h - 1, w - 1)):
            yy0, xx0 = np.mgrid[0:h, 0:w]
            m &= (np.abs(yy0 - cy) + np.abs(xx0 - cx)) > 2
    p = _rampe_vers_rgba(R, idx, m)
    if fibres:
        # fibres de kōzo : courtes, à peine plus claires (ou plus sombres) que le papier
        for _ in range(max(4, (w * h) // 190)):
            fx, fy = rs.uniform(0, w), rs.uniform(0, h)
            ang = rs.uniform(0, math.pi)
            cur = rs.uniform(-0.3, 0.3)
            ln = rs.randint(2, 7)
            clair = rs.rand() < 0.7
            for k in range(ln):
                ix, iy = int(fx), int(fy)
                if 0 <= ix < w and 0 <= iy < h and m[iy, ix]:
                    j = min(n - 1, idx[iy, ix] + 1) if clair else max(0, idx[iy, ix] - 1)
                    p[iy, ix] = hexc(R[j])
                fx += math.cos(ang); fy += math.sin(ang); ang += cur
    # arête : 1 px plus sombre le long du bord (lisible sur fond clair comme sombre)
    up = np.zeros_like(m); up[1:, :] = m[:-1, :]
    dn = np.zeros_like(m); dn[:-1, :] = m[1:, :]
    lf = np.zeros_like(m); lf[:, 1:] = m[:, :-1]
    rt = np.zeros_like(m); rt[:, :-1] = m[:, 1:]
    edge = m & ~(up & dn & lf & rt)
    p[edge & (~dn | ~rt)] = hexc(R[1])
    p[edge & (~up | ~lf) & ~(~dn | ~rt)] = hexc(R[3])
    if liseret:
        p[edge] = hexc(liseret)
    if ombre:
        ombre_portee(img, x, y, w, h, dx=2, dy=3, force=0.5, rayon=2, masque=m)
    coller(img, p, x, y)
    return m


def bois(img, x, y, w, h, ton="moyen", sens="h", seed=0, planches=0, noeuds=1, biseau=True):
    """Bois veiné : veines ondulées, fil fin étiré, nœuds à cernes, lames optionnelles
    (joint sombre + arête claire). ton : 'sombre' | 'moyen' | 'clair'. sens : 'h' | 'v'."""
    R = R_BOIS[ton] if isinstance(ton, str) else ton
    n = len(R)
    if sens == "v":
        W, H = h, w
    else:
        W, H = w, h
    rs = np.random.RandomState(seed)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    warp = (bruit(W, H, 30, seed, 2, etire=(4, 1)) - 0.5) * 9
    fil = bruit(W, H, 3, seed + 3, 2, etire=(9, 1))
    vv = yy + warp
    for _ in range(noeuds):
        kx, ky = rs.uniform(W * 0.15, W * 0.85), rs.uniform(H * 0.2, H * 0.8)
        d = np.sqrt(((xx - kx) / 2.2) ** 2 + (yy - ky) ** 2)
        vv += 6 * np.exp(-(d / 5.5) ** 2) * np.sign(yy - ky + 0.01)
    period = 5.0 + rs.rand() * 1.5
    ph = (vv / period) % 1.0
    veine = np.exp(-((ph - 0.5) / 0.12) ** 2)
    v = 0.52 + (fil - 0.5) * 0.30 - 0.30 * veine + (bruit(W, H, 18, seed + 7, 2) - 0.5) * 0.18
    for _ in range(noeuds):
        kx, ky = rs.uniform(W * 0.15, W * 0.85), rs.uniform(H * 0.2, H * 0.8)
    if planches:
        lame = H / planches
        for k in range(1, planches):
            yj = int(round(k * lame))
            v[yj, :] = 0.0
            if yj + 1 < H:
                v[yj + 1, :] = np.maximum(v[yj + 1, :], 0.80)
    if sens == "v":
        v = v.T
    idx = quantifier(v, n, x, y, tramage=0.45)
    p = _rampe_vers_rgba(R, idx)
    if biseau and w > 3 and h > 3:
        p[0, :] = hexc(R[-1]); p[:, 0] = hexc(R[-2])
        p[-1, :] = hexc(R[0]); p[:, -1] = hexc(R[1])
    coller(img, p, x, y)


_MOTIFS = {
    # 12x6 : vagues seigaiha
    "seigaiha": ["...aaaaaa...", ".aa......aa.", "a...bbbb...a", "..bb....bb..", ".b...aa...b.", "b..aa..aa..b"],
    # 8x8 : losanges (hishi)
    "losanges": ["...aa...", "..a..a..", ".a.bb.a.", "a.b..b.a", "a.b..b.a", ".a.bb.a.", "..a..a..", "...aa..."],
    # 6x6 : points (same komon)
    "points": ["......", ".a....", "......", "....a.", "......", "......"],
    # 8x4 : rayures obliques
    "raye": ["aa......", ".aa.....", "..aa....", "...aa..."],
    # 8x8 : écailles (uroko)
    "uroko": ["a.......", "aa......", "aaa.....", "aaaa....", "....a...", "....aa..", "....aaa.", "....aaaa"],
}


def bande_tissu(img, x, y, w, h, couleur="rouge", motif="seigaiha", liseret="or", sens="h",
                seed=0, trame=True):
    """Bande de tissu (obi, brocart, ruban) : fond tissé (trame 2 tons), motif répété
    (seigaiha | losanges | points | raye | uroko | uni), lisières à fil d'or."""
    base = couleur
    if isinstance(couleur, str) and couleur in ("rouge", "noir", "indigo", "or", "violet", "creme"):
        base = {"rouge": "8E1628", "noir": "1A1013", "indigo": "2A3558", "or": "B08442",
                "violet": "4A2A66", "creme": "E8DABA"}[couleur]
    R = rampe(base, 7)
    yy, xx = np.mgrid[0:h, 0:w]
    v = np.full((h, w), 3)
    if trame:   # armure du tissu : fils alternés, discret
        v = v - ((((xx + x) + (yy + y) * 3) % 6) == 0).astype(int)
    if motif and motif != "uni":
        spr = _MOTIFS[motif]
        th, tw = len(spr), len(spr[0])
        for j in range(h):
            for i in range(w):
                a, b = (i, j) if sens == "h" else (j, i)
                ch = spr[(b + (seed % th)) % th][(a + (b // th) * (tw // 2 if motif == "seigaiha" else 0)) % tw]
                if ch == "a":
                    v[j, i] = 5
                elif ch == "b":
                    v[j, i] = 4 if motif == "seigaiha" else 1
    v = np.clip(v, 0, 6)
    p = _rampe_vers_rgba(R, v)
    if liseret:
        O = _R(R_OR) if liseret == "or" else _R(R_CORDE[liseret])
        if sens == "h":
            p[0, :] = O[2]; p[1, :] = O[6]; p[-1, :] = O[2]; p[-2, :] = O[4]
        else:
            p[:, 0] = O[2]; p[:, 1] = O[6]; p[:, -1] = O[2]; p[:, -2] = O[4]
    coller(img, p, x, y)


# ───────────────────────── sceaux et tampons ─────────────────────────
def hanko(img, cx, cy, taille=20, texte="忍", forme="carre", negatif=True, encre=None, seed=0,
          usure=0.10, cadre=True):
    """Sceau hanko vermillon. forme : 'carre' | 'rond' | 'ovale'. negatif=True : kanji en
    réserve (le support apparaît dans les traits) ; False : kanji en relief dans un cadre.
    texte : 1 à 4 kanji (2 -> colonne, 4 -> 2x2, lus de droite à gauche). usure : manques
    d'encre 0..0,4."""
    R = _R(encre or R_VERMILLON)
    rs = np.random.RandomState(seed)
    S = int(taille)
    Wd = S if forme != "ovale" else int(S * 0.72)
    yy, xx = np.mgrid[0:S, 0:Wd].astype(float)
    cxl, cyl = (Wd - 1) / 2, (S - 1) / 2
    if forme == "rond":
        forme_m = (xx - cxl) ** 2 + (yy - cyl) ** 2 <= (S / 2 - 0.2) ** 2
    elif forme == "ovale":
        forme_m = ((xx - cxl) / (Wd / 2 - 0.2)) ** 2 + ((yy - cyl) / (S / 2 - 0.2)) ** 2 <= 1
    else:
        forme_m = np.ones((S, Wd), bool)
        for (a, b) in ((0, 0), (0, Wd - 1), (S - 1, 0), (S - 1, Wd - 1)):
            forme_m[a, b] = False
    # glyphes
    chars = list(texte)
    nc = len(chars)
    inner = int(S * (0.70 if negatif else 0.62))
    if nc == 1:
        cells = [(0, 0, inner)]
    elif nc == 2:
        g = inner // 2
        cells = [(0, -g // 2 - 1, g + 1), (0, g // 2 + 1, g + 1)]
    else:
        g = inner // 2
        cells = [(g // 2 + 1, -g // 2 - 1, g + 1), (g // 2 + 1, g // 2 + 1, g + 1),
                 (-g // 2 - 1, -g // 2 - 1, g + 1), (-g // 2 - 1, g // 2 + 1, g + 1)]
    gm = np.zeros((S, Wd), bool)
    for ch, (ox, oy, gs) in zip(chars, cells):
        gi = _glyphe_kanji(ch, gs, "gras" if gs >= 13 else "gothic")
        gy0 = int(round(cyl + oy - gi.shape[0] / 2 + 0.5))
        gx0 = int(round(cxl + ox - gi.shape[1] / 2 + 0.5))
        for j in range(gi.shape[0]):
            for i in range(gi.shape[1]):
                if gi[j, i] and 0 <= gy0 + j < S and 0 <= gx0 + i < Wd:
                    gm[gy0 + j, gx0 + i] = True
    if negatif:
        ink = forme_m & ~gm
    else:
        # cadre de 2 px + kanji en relief
        er = forme_m.copy()
        for _ in range(2):
            e2 = er.copy()
            e2[1:, :] &= er[:-1, :]; e2[:-1, :] &= er[1:, :]; e2[:, 1:] &= er[:, :-1]; e2[:, :-1] &= er[:, 1:]
            er = e2
        ink = ((forme_m & ~er) | gm) if cadre else gm
    # texture d'encre : grain + manques
    nz = bruit(Wd, S, 3, seed, 2)
    grad = (xx / max(1, Wd - 1)) * 0.5 + (yy / max(1, S - 1)) * 0.5
    trou = (nz * 0.7 + grad * 0.3) < usure * 1.3
    trou &= rs.rand(S, Wd) < 0.85
    ink &= ~trou
    # bord rongé
    edge = forme_m & ~np.roll(forme_m, 1, 0) | forme_m & ~np.roll(forme_m, -1, 0) | \
        forme_m & ~np.roll(forme_m, 1, 1) | forme_m & ~np.roll(forme_m, -1, 1)
    ink &= ~(edge & (rs.rand(S, Wd) < 0.12))
    p = _patch(S, Wd)
    tone = np.full((S, Wd), 4)
    tone -= (nz < 0.35).astype(int)
    tone += (rs.rand(S, Wd) < 0.10).astype(int)
    tone -= (rs.rand(S, Wd) < 0.08).astype(int)
    tone = np.clip(tone, 0, len(R) - 1)
    pal = np.array(R, np.uint8)
    p[ink] = pal[tone[ink]]
    coller(img, p, int(cx - Wd // 2), int(cy - S // 2))


def tampon(img, cx, cy, texte, taille=10, couleur="vermillon", cadre="double", angle=-3.0,
           seed=0, usure=0.15, marge=4):
    """Tampon encré (administration du village) : texte ArcadePix (ou kanji si le texte
    n'est pas ASCII) dans un cadre simple/double/rond, légèrement tourné, encre irrégulière
    (manques, grain). couleur : 'vermillon' | 'encre' | 'indigo' | (r,g,b)."""
    R = {"vermillon": R_VERMILLON, "encre": R_ENCRE, "indigo": R_INDIGO}.get(couleur, None)
    R = _R(R) if R else rampe(couleur, 7)
    rs = np.random.RandomState(seed)
    ascii_ = all(ord(c) < 128 for c in sans_accents(texte)) and not any(ord(c) > 0x2E80 for c in texte)
    if ascii_:
        g = arcade_masque(texte, taille)
    else:
        g = _glyphe_kanji_ligne(texte, max(10, taille + 4), "gras")
    gh, gw = g.shape
    if cadre == "rond":
        S = int(max(gw, gh) * 1.15 + 2 * marge + 4)
        Wt, Ht = S, S
    else:
        Wt, Ht = gw + 2 * marge + (4 if cadre == "double" else 2), gh + 2 * marge + (4 if cadre == "double" else 2)
    m = np.zeros((Ht, Wt), bool)
    if cadre in ("simple", "double"):
        m[0, :] = m[-1, :] = True; m[:, 0] = m[:, -1] = True
        if cadre == "double":
            m[2, 2:-2] = m[-3, 2:-2] = True; m[2:-2, 2] = m[2:-2, -3] = True
    elif cadre == "rond":
        yy, xx = np.mgrid[0:Ht, 0:Wt].astype(float)
        d = np.sqrt((xx - (Wt - 1) / 2) ** 2 + (yy - (Ht - 1) / 2) ** 2)
        m |= (d <= Wt / 2 - 0.3) & (d >= Wt / 2 - 1.5)
        m |= (d <= Wt / 2 - 2.8) & (d >= Wt / 2 - 3.8)
    oy, ox = (Ht - gh) // 2, (Wt - gw) // 2
    m[oy:oy + gh, ox:ox + gw] |= g
    im = Image.fromarray((m * 255).astype(np.uint8), "L")
    if angle:
        im = im.rotate(angle, resample=Image.NEAREST, expand=True)
    m = np.asarray(im) > 127
    H2, W2 = m.shape
    nz = bruit(W2, H2, 4, seed, 2)
    side = np.linspace(0, 1, W2)[None, :] * np.ones((H2, 1))
    m &= ~((nz * 0.65 + side * 0.35) < usure * 1.4)
    m &= ~(rs.rand(H2, W2) < usure * 0.25)
    p = _patch(H2, W2)
    tone = np.clip(3 + (rs.rand(H2, W2) < 0.15).astype(int) - (nz < 0.3).astype(int), 0, len(R) - 1)
    pal = np.array(R, np.uint8)
    p[m] = pal[tone[m]]
    p[m, 3] = 225
    coller(img, p, int(cx - W2 // 2), int(cy - H2 // 2))


# ───────────────────────── boutons-plaques ─────────────────────────
def bouton_plaque(img, x, y, w, h, libelle="", etat="normal", kanji_=None, touche=None, padding=10):
    """Bouton-plaque laqué. etat : 'normal' | 'survol' | 'actif' | 'inactif'.
    w=None : largeur auto (texte + 2 x padding). touche : petit cartouche à droite
    (ex. 'K', 'ECHAP'). Renvoie (x, y, w, h)."""
    if w is None:
        w = (arcade_largeur(libelle, 10) if libelle else 0) + (12 if kanji_ else 0) + 2 * padding
    if etat == "actif":
        ombre_portee(img, x, y, w, h, dx=0, dy=2, force=0.6, rayon=2)
        laque(img, x + 1, y + 1, w - 2, h - 2, ton="rouge")
        liseret_or(img, x, y, w, h)
        kanagu(img, x, y, w, h, "petit", deborde=1)
        tc = PAL["creme"]
    elif etat == "survol":
        ombre_portee(img, x, y, w, h, dx=0, dy=2, force=0.55, rayon=2)
        laque(img, x + 1, y + 1, w - 2, h - 2, ton="noir", intensite=1.7)
        liseret_or(img, x, y, w, h)
        kanagu(img, x, y, w, h, "petit", deborde=0)
        tc = PAL["creme"]
    elif etat == "inactif":
        rect(img, x, y, w, h, hexc("16100F"))
        c = hexc("33282A")
        rect(img, x, y, w, 1, c); rect(img, x, y + h - 1, w, 1, c)
        rect(img, x, y, 1, h, c); rect(img, x + w - 1, y, 1, h, c)
        tc = hexc("54483F")
    else:
        ombre_portee(img, x, y, w, h, dx=0, dy=2, force=0.45, rayon=1)
        laque(img, x + 1, y + 1, w - 2, h - 2, ton="noir", intensite=0.8)
        c = hexc(R_OR[3])
        rect(img, x, y, w, 1, hexc(R_OR[4])); rect(img, x, y + h - 1, w, 1, hexc(R_OR[2]))
        rect(img, x, y, 1, h, c); rect(img, x + w - 1, y, 1, h, c)
        for (a, b) in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
            rect(img, a, b, 1, 1, hexc(R_OR[6]))
        tc = PAL["creme_doux"]
    tw = arcade_largeur(libelle, 10) if libelle else 0
    kw = 12 if kanji_ else 0
    tx = x + (w - tw - kw) // 2
    if kanji_:
        kanji(img, tx, y + (h - 10) // 2, kanji_, 10, police="gothic",
              couleur=PAL["or_clair"] if etat in ("actif", "survol") else hexc(R_OR[4]))
        tx += kw
    if libelle:
        arcade(img, tx, y + (h - 7) // 2, libelle, 10, tc, ombre=(etat == "actif"))
    if touche:
        kw2 = arcade_largeur(touche, 10) + 6
        kx = x + w + 4
        rect(img, kx, y + (h - 11) // 2, kw2, 11, hexc("120B0D"))
        c = hexc(R_OR[3])
        ky = y + (h - 11) // 2
        rect(img, kx, ky, kw2, 1, c); rect(img, kx, ky + 10, kw2, 1, c)
        rect(img, kx, ky, 1, 11, c); rect(img, kx + kw2 - 1, ky, 1, 11, c)
        arcade(img, kx + 3, ky + 2, touche, 10, PAL["or_"])
    return (x, y, w, h)


# ───────────────────────── lanternes célestes (signature des stats) ─────────────────────────
def lanterne(img, cx, cy, stat=0, taille=1.0, allumee=1.0, kanji_=True, survol=False, seed=0):
    """Lanterne céleste pixel art (signature de la fiche de stats) centrée en (cx, cy).
    stat : index 0..5 ou clé ('taijutsu'…) -> couleur + kanji de StatDef ; ou (couleur, kanji).
    taille 1.0 = corps 34x44 px (proche de la texture du mod, 40x50 avec marges).
    allumee 0..1 : éteinte (papier terne, sans flamme) -> pleine lumière.
    survol : filet doré au-dessus (comme StatsScreen). Le halo lissé se pose à part, sur la
    couche finale : halo_lanterne(final, …) — ou tout en un avec Maquette.lanterne(…).
    Renvoie (x, y, w, h) du corps."""
    if isinstance(stat, str):
        stat = [s_[0] for s_ in STATS].index(stat)
    if isinstance(stat, tuple):
        col, kj = hexc(stat[0]), stat[1]
    else:
        col, kj = STATS[stat][3], STATS[stat][4]
    W = max(10, int(round(34 * taille)) // 2 * 2)
    Wb = max(6, int(round(W * 0.66)) // 2 * 2)
    H = max(14, int(round(44 * taille)))
    R = rampe(col, 9, ombre=0.20, lumiere=0.80)
    n = len(R)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    t = yy / max(1, H - 1)
    cxl = (W - 1) / 2
    half = (W / 2) * (1 - t) + (Wb / 2) * t + 0.9 * taille * np.sin(np.pi * t)
    half[0] -= 2.0
    if H > 2:
        half[1] -= 0.8
    m = np.abs(xx - cxl) <= half - 0.01
    u = (xx - cxl) / np.maximum(half, 1)
    foyer = np.exp(-((t - 0.95) / 0.62) ** 2) * np.exp(-(u / 1.05) ** 2)
    v = 0.26 + 0.70 * foyer - 0.13 * np.abs(u) ** 2.2 - 0.07 * (1 - t) ** 3
    v += (bruit(W, H, 7, seed, 1) - 0.5) * 0.03
    idx = quantifier(np.clip(v, 0, 1), n, int(cx), int(cy), tramage=0.5)
    pal = np.array(R, np.uint8)
    p = np.zeros((H, W, 4), np.uint8)
    p[m] = pal[idx[m]]
    # cœur chaud près du foyer
    chaud = np.clip((foyer - 0.70) * 2.6, 0, 1) * (allumee > 0.5)
    warm = np.array([255, 238, 206], float)
    p[..., :3] = (p[..., :3] * (1 - chaud[..., None] * 0.55) + warm * chaud[..., None] * 0.55).astype(np.uint8)
    # deux lattes de bambou discrètes
    for frac in (-0.48, 0.48):
        for j in range(2, H - 2):
            i = int(round(cxl + frac * half[j, 0]))
            if 0 <= i < W and m[j, i]:
                p[j, i, :3] = (p[j, i, :3] * 0.86).astype(np.uint8)
    # arêtes : flanc gauche éclairé, flanc droit dans l'ombre ; bord haut replié
    for j in range(H):
        row = np.nonzero(m[j])[0]
        if len(row):
            p[j, row[0], :3] = (p[j, row[0], :3] * 0.78).astype(np.uint8)
            p[j, row[-1], :3] = (p[j, row[-1], :3] * 0.60).astype(np.uint8)
    p[0, m[0], :3] = (p[0, m[0], :3] * 0.62).astype(np.uint8)
    if H > 3:
        p[1, m[1], :3] = np.minimum(255, p[1, m[1], :3].astype(int) + 14).astype(np.uint8)
    # anneau de bambou en bas (ouverture)
    bb = [hexc(c) for c in ("2E1C10", "5E3E24", "8E6638", "B88C52")]
    rows = np.nonzero(m[H - 1])[0]
    if len(rows):
        x0, x1 = max(0, rows[0]), min(W - 1, rows[-1])
        p[H - 2, x0:x1 + 1] = bb[3]
        p[H - 1, x0:x1 + 1] = bb[1]
        p[H - 2, x0] = bb[2]; p[H - 2, x1] = bb[1]
        p[H - 1, x0] = bb[0]; p[H - 1, x1] = bb[0]
    # kanji à l'encre (unifont du jeu à taille >= 0,85)
    if kanji_:
        gs = 16 if taille >= 0.85 else (12 if taille >= 0.6 else 10)
        g = _glyphe_kanji(kj, gs, "unifont" if gs == 16 else "gothic")
        gy0 = int(round(H * 0.40 - g.shape[0] / 2))
        gx0 = int(round(cxl - g.shape[1] / 2 + 0.5))
        ink = np.array(rampe(col, 9, ombre=0.12)[0][:3], float) * 0.7
        for j in range(g.shape[0]):
            for i in range(g.shape[1]):
                if g[j, i]:
                    y2, x2 = gy0 + j, gx0 + i
                    if 0 <= y2 < H and 0 <= x2 < W and m[y2, x2]:
                        p[y2, x2, :3] = (p[y2, x2, :3] * 0.22 + ink * 0.78).astype(np.uint8)
    # éteinte : papier terne
    if allumee < 1:
        k = 1 - allumee
        g_ = p[m, :3].astype(float)
        lum = g_.mean(axis=1, keepdims=True)
        terne = (g_ * 0.35 + lum * 0.65) * 0.42 + np.array([14, 10, 14])
        p[m, :3] = (g_ * (1 - k) + terne * k).astype(np.uint8)
    # flamme dans l'ouverture
    if allumee >= 0.5:
        fl = [".a.", "aba", "bcb", ".b."] if taille >= 0.7 else [".a.", "bcb"]
        fc = {"a": hexc("E8742C"), "b": hexc("FFC45A"), "c": hexc("FFF6D8")}
        for j, row in enumerate(fl):
            for i, ch in enumerate(row):
                if ch in fc:
                    y2, x2 = H - 2 - len(fl) + j + 1, int(cxl - 1 + i + 0.5)
                    if 0 <= y2 < H and 0 <= x2 < W:
                        p[y2, x2] = fc[ch]
    out = p
    if survol:
        out = np.zeros((H + 3, W + 2, 4), np.uint8)
        out[3:, 1:W + 1] = p
        out[0, :] = hexc(R_OR[6])
        coller(img, out, int(cx - W // 2) - 1, int(cy - H // 2) - 3)
    else:
        coller(img, out, int(cx - W // 2), int(cy - H // 2))
    return (int(cx - W // 2), int(cy - H // 2), W, H)


def lanterne_mod(i):
    """Texture réelle de lanterne du mod (gui/stats/lantern_<i>.png, 40x50)."""
    return Image.open(os.path.join(TEX_DIR, "gui", "stats", f"lantern_{i}.png")).convert("RGBA")


def texture_mod(chemin):
    """Charge une texture du mod : texture_mod('gui/esc/village_konoha.png')."""
    return Image.open(os.path.join(TEX_DIR, chemin.replace("/", os.sep))).convert("RGBA")


# ───────────────────────── formes lissées au pixel réel (couche finale) ─────────────────────────
_SS = 4


def _calque_lisse(img, bbox, dessin, couleur, alpha=255):
    x0, y0, x1, y1 = [int(math.floor(v)) for v in bbox[:2]] + [int(math.ceil(v)) for v in bbox[2:]]
    x0, y0 = max(0, x0 - 2), max(0, y0 - 2)
    x1, y1 = min(img.width, x1 + 2), min(img.height, y1 + 2)
    if x1 <= x0 or y1 <= y0:
        return
    W, H = x1 - x0, y1 - y0
    m = Image.new("L", (W * _SS, H * _SS), 0)
    d = ImageDraw.Draw(m)
    dessin(d, lambda X, Y: ((X - x0) * _SS, (Y - y0) * _SS), _SS)
    m = m.resize((W, H), Image.BOX)
    c = hexc(couleur)
    lay = Image.new("RGBA", (W, H), c[:3] + (0,))
    a = np.asarray(m, float) * (alpha / 255.0) * (c[3] / 255.0)
    lay.putalpha(Image.fromarray(a.astype(np.uint8), "L"))
    img.alpha_composite(lay, (x0, y0))


def disque_lisse(img, cx, cy, r, couleur, alpha=255):
    """Disque antialiasé au pixel écran (sur-échantillonnage x4)."""
    _calque_lisse(img, (cx - r, cy - r, cx + r, cy + r),
                  lambda d, T, s: d.ellipse([*T(cx - r, cy - r), *T(cx + r, cy + r)], fill=255), couleur, alpha)


def anneau_lisse(img, cx, cy, r, ep, couleur, debut=0.0, fin=360.0, alpha=255, bouts_ronds=True):
    """Anneau / arc antialiasé (degrés, 0 = 3 h, sens horaire comme PIL). Pour les jauges
    circulaires, ensō, cadres ronds. bouts_ronds arrondit les extrémités d'un arc."""
    def dessin(d, T, s):
        x0, y0 = T(cx - r, cy - r)
        x1, y1 = T(cx + r, cy + r)
        if fin - debut >= 360:
            d.ellipse([x0, y0, x1, y1], outline=255, width=max(1, int(round(ep * s))))
        else:
            d.arc([x0, y0, x1, y1], debut, fin, fill=255, width=max(1, int(round(ep * s))))
            if bouts_ronds:
                for a in (debut, fin):
                    rr = r - ep / 2
                    px, py = cx + rr * math.cos(math.radians(a)), cy + rr * math.sin(math.radians(a))
                    d.ellipse([*T(px - ep / 2, py - ep / 2), *T(px + ep / 2, py + ep / 2)], fill=255)
    _calque_lisse(img, (cx - r, cy - r, cx + r, cy + r), dessin, couleur, alpha)


def trait_lisse(img, points, ep, couleur, alpha=255, courbe=False):
    """Polyligne antialiasée ; courbe=True lisse les points en Catmull-Rom."""
    pts = list(points)
    if courbe and len(pts) >= 3:
        res = []
        P = [pts[0]] + pts + [pts[-1]]
        for i in range(1, len(P) - 2):
            p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
            for k in range(12):
                t = k / 12
                t2, t3 = t * t, t * t * t
                res.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                        + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in range(2)))
        res.append(pts[-1])
        pts = res
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]

    def dessin(d, T, s):
        q = [T(x, y) for x, y in pts]
        d.line(q, fill=255, width=max(1, int(round(ep * s))), joint="curve")
        for X, Y in (q[0], q[-1]):
            rr = ep * s / 2
            d.ellipse([X - rr, Y - rr, X + rr, Y + rr], fill=255)
    _calque_lisse(img, (min(xs) - ep, min(ys) - ep, max(xs) + ep, max(ys) + ep), dessin, couleur, alpha)


def halo(img, cx, cy, r, couleur, force=0.6, additif=True):
    """Halo radial doux (comme blitGlow du mod) : additif par défaut (lumière)."""
    x0, y0 = int(cx - r), int(cy - r)
    S = int(2 * r) + 1
    yy, xx = np.mgrid[0:S, 0:S].astype(float)
    d = np.sqrt((xx + x0 - cx) ** 2 + (yy + y0 - cy) ** 2) / r
    a = np.clip(1 - d, 0, 1) ** 2.2 * force
    c = np.array(hexc(couleur)[:3], float)
    X0, Y0 = max(0, x0), max(0, y0)
    X1, Y1 = min(img.width, x0 + S), min(img.height, y0 + S)
    if X1 <= X0 or Y1 <= Y0:
        return
    a = a[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0]
    base = np.asarray(img.crop((X0, Y0, X1, Y1)), float)
    if additif:
        base[..., :3] = np.minimum(255, base[..., :3] + c * a[..., None])
        base[..., 3] = np.maximum(base[..., 3], a * 255)
    else:
        base[..., :3] = base[..., :3] * (1 - a[..., None]) + c * a[..., None]
        base[..., 3] = np.maximum(base[..., 3], a * 255)
    img.paste(Image.fromarray(base.astype(np.uint8), "RGBA"), (X0, Y0))


def braises(img, zone, n=40, couleur="E8742C", seed=0, taille=(1.2, 3.2)):
    """Braises de chakra (scène de sélection) : petits points lumineux lissés + halo."""
    rs = random.Random(seed)
    x0, y0, x1, y1 = zone
    for _ in range(n):
        x, y = rs.uniform(x0, x1), rs.uniform(y0, y1)
        r = rs.uniform(*taille)
        k = rs.uniform(0.35, 1.0)
        halo(img, x, y, r * 5, couleur, force=0.25 * k)
        disque_lisse(img, x, y, r, hexc("FFE2B0") if r < 2 else couleur, alpha=int(255 * k))


def halo_lanterne(img, cx, cy, stat=0, taille=1.0, allumee=1.0, eclat=0.6):
    """Halos d'une lanterne (couleur de la stat + cœur chaud), coordonnées ÉCRAN,
    (cx, cy) = centre de la lanterne. Reprend drawLanterns (deux blitGlow)."""
    if isinstance(stat, str):
        stat = [s[0] for s in STATS].index(stat)
    col = STATS[stat][3] if not isinstance(stat, tuple) else hexc(stat[0])
    halo(img, cx, cy + 12 * taille, 120 * taille * (0.6 + 0.4 * eclat), col, force=(0.20 + 0.35 * eclat) * allumee)
    halo(img, cx, cy + 34 * taille, 34 * taille, "FFBE6E", force=0.45 * allumee)


# ───────────────────────── texte : ArcadePix (pixel) ─────────────────────────
def sans_accents(s):
    """Comme EscData.arcadeSafe (sans troncature) : majuscules ASCII, accents retirés."""
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").upper()
    s = "".join(c if 0x20 <= ord(c) < 0x7F else " " for c in s)
    while "  " in s:
        s = s.replace("  ", " ")
    return s.strip()


_AGLYPH = {}
_AFONT = None


def _arcade_glyphe(ch):
    """Glyphe ArcadePix exact (grille 10 px/em) : rendu à 100 px puis échantillonné au
    centre de chaque case -> aucun pixel perdu par le hinting FreeType."""
    global _AFONT
    if ch in _AGLYPH:
        return _AGLYPH[ch]
    if _AFONT is None:
        _AFONT = ImageFont.truetype(F_ARCADE, 100)
    adv = int(round(_AFONT.getlength(ch) / 10))
    im = Image.new("L", (adv * 10 + 20, 120), 0)
    ImageDraw.Draw(im).text((0, 0), ch, font=_AFONT, fill=255)
    a = np.asarray(im)[5::10, 5::10] > 127      # rangées 0..11 ; ligne de base = 8
    g = a[:10, :max(1, adv)]
    _AGLYPH[ch] = (g, adv)
    return _AGLYPH[ch]


def arcade_masque(texte, taille=10):
    """Masque bool du texte ArcadePix. Haut du masque = haut des capitales.
    taille = corps en px de la couche visée (multiple de 10 -> pixels exacts)."""
    s = sans_accents(texte)
    k = taille / 10.0
    if abs(k - round(k)) > 1e-6:
        warnings.warn(f"arcade : taille {taille} n'est pas un multiple de 10 -> pixels inégaux (comme une échelle non entière en jeu)")
    cols = []
    for ch in s:
        g, adv = _arcade_glyphe(ch)
        cols.append(g[1:9, :adv])                # capitales sur 7 rangées (1..7), +1 sous la ligne de base
    if not cols:
        return np.zeros((8, 1), bool)
    m = np.concatenate(cols, axis=1)
    if m.shape[1] > 1:
        m = m[:, :-1] if not m[:, -1].any() else m
    if k != 1:
        im = Image.fromarray((m * 255).astype(np.uint8), "L")
        im = im.resize((max(1, int(round(m.shape[1] * k))), max(1, int(round(m.shape[0] * k)))), Image.NEAREST)
        m = np.asarray(im) > 127
    return m


def arcade_largeur(texte, taille=10):
    return arcade_masque(texte, taille).shape[1]


def arcade(img, x, y, texte, taille=10, couleur=None, ancre="g", ombre=False, alpha=255, ancre_v="haut"):
    """Pose du texte ArcadePix pixel parfait, sans antialiasing.
    y = haut des capitales (ancre_v='haut'), ou ligne de base ('base'), ou milieu ('milieu').
    ancre : 'g' | 'c' | 'd'. ombre : ombre portée du jeu (décalage 1 px de fonte, 25 %).
    Renvoie la largeur en px."""
    couleur = hexc(couleur or PAL["creme"], alpha)
    m = arcade_masque(texte, taille)
    k = max(1, int(round(taille / 10)))
    h, w = m.shape
    if ancre == "c":
        x -= w // 2
    elif ancre == "d":
        x -= w
    cap = 7 * k
    if ancre_v == "base":
        y -= cap
    elif ancre_v == "milieu":
        y -= cap // 2
    if ombre:
        sc = (int(couleur[0] * 0.25), int(couleur[1] * 0.25), int(couleur[2] * 0.25), couleur[3])
        p = _patch(h, w); p[m] = sc
        coller(img, p, x + k, y + k)
    p = _patch(h, w)
    p[m] = couleur
    coller(img, p, x, y)
    return w


# ───────────────────────── texte : kanji ─────────────────────────
_UNI = None


def _unifont():
    global _UNI
    if _UNI is None:
        _UNI = {}
        try:
            z = zipfile.ZipFile(UNIFONT_ZIP)
            name = [n for n in z.namelist() if n.endswith(".hex")][0]
            for line in z.read(name).decode("ascii").splitlines():
                cp, bits = line.split(":")
                c = int(cp, 16)
                if c >= 0x2E80:
                    _UNI[c] = bits
        except Exception as e:     # pragma: no cover
            warnings.warn(f"unifont introuvable ({e}) : repli sur MS Gothic 16")
    return _UNI


_KG = {}


def _glyphe_kanji(ch, taille=16, police="unifont"):
    """Masque bool d'un kanji. police : 'unifont' (police par défaut du jeu, 16 px) |
    'gothic' (MS Gothic, bitmaps nets 10-16 px) | 'gras' (Yu Gothic Bold, toute taille) |
    'song' (SimSun, empattements)."""
    key = (ch, taille, police)
    if key in _KG:
        return _KG[key]
    g = None
    if police == "unifont":
        bits = _unifont().get(ord(ch))
        if bits:
            wpx = len(bits) * 4 // 16
            rows = []
            for r in range(16):
                v = int(bits[r * wpx // 4:(r + 1) * wpx // 4], 16)
                rows.append([(v >> (wpx - 1 - c)) & 1 for c in range(wpx)])
            g = np.array(rows, bool)
            if taille != 16:
                k = taille / 16
                im = Image.fromarray((g * 255).astype(np.uint8), "L").resize(
                    (int(round(wpx * k)), int(round(16 * k))), Image.NEAREST)
                g = np.asarray(im) > 127
        else:
            police = "gothic"
    if g is None:
        path = {"gothic": F_GOTHIC, "gras": F_GOTHIC_GRAS, "song": F_SONG}.get(police, F_GOTHIC)
        f = ImageFont.truetype(path, int(taille))
        im = Image.new("L", (int(taille) + 4, int(taille) + 4), 0)
        d = ImageDraw.Draw(im)
        d.fontmode = "1"
        d.text((0, 0), ch, font=f, fill=255)
        a = np.asarray(im) > 127
        ys, xs = np.nonzero(a)
        if len(ys):
            a = a[:int(taille) + 2, :int(taille) + 2]
            # recadrage centré sur une case carrée de `taille`
            bb = (ys.min(), xs.min(), ys.max(), xs.max())
            gh, gw = bb[2] - bb[0] + 1, bb[3] - bb[1] + 1
            out = np.zeros((int(taille), max(int(taille), gw)), bool)
            oy = max(0, (int(taille) - gh) // 2)
            ox = max(0, (out.shape[1] - gw) // 2)
            sub = np.asarray(im)[bb[0]:bb[2] + 1, bb[1]:bb[3] + 1] > 127
            out[oy:oy + min(gh, out.shape[0] - oy), ox:ox + gw] = sub[:out.shape[0] - oy]
            g = out
        else:
            g = np.zeros((int(taille), int(taille)), bool)
    _KG[key] = g
    return g


def _glyphe_kanji_ligne(texte, taille, police, vertical=False, interlettre=1):
    gs = [_glyphe_kanji(c, taille, police) for c in texte]
    if vertical:
        W = max(g.shape[1] for g in gs)
        H = sum(g.shape[0] for g in gs) + interlettre * (len(gs) - 1)
        out = np.zeros((H, W), bool)
        y = 0
        for g in gs:
            ox = (W - g.shape[1]) // 2
            out[y:y + g.shape[0], ox:ox + g.shape[1]] = g
            y += g.shape[0] + interlettre
        return out
    H = max(g.shape[0] for g in gs)
    W = sum(g.shape[1] for g in gs) + interlettre * (len(gs) - 1)
    out = np.zeros((H, W), bool)
    x = 0
    for g in gs:
        out[:g.shape[0], x:x + g.shape[1]] = g
        x += g.shape[1] + interlettre
    return out


def kanji(img, x, y, texte, taille=16, police="unifont", couleur=None, ancre="g", vertical=False,
          alpha=255, ombre=False):
    """Pose des kanji pixel (sans antialiasing). Sur la couche finale, police='unifont'
    taille 16 = rendu EXACT d'un kanji de la police par défaut du jeu à l'échelle 2
    (taille 8 GUI). Sur la couche 960, taille 16 = kanji 2x plus grand. Renvoie la largeur."""
    couleur = hexc(couleur or PAL["creme"], alpha)
    m = _glyphe_kanji_ligne(texte, taille, police, vertical)
    h, w = m.shape
    if ancre == "c":
        x -= w // 2
    elif ancre == "d":
        x -= w
    if ombre:
        sc = tuple(int(v * 0.25) for v in couleur[:3]) + (couleur[3],)
        p = _patch(h, w); p[m] = sc
        coller(img, p, x + 1, y + 1)
    p = _patch(h, w)
    p[m] = couleur
    coller(img, p, x, y)
    return w


# ───────────────────────── texte : Inter (couche finale, antialiasé) ─────────────────────────
_IF = {}


def _inter_font(taille, gras=False, noir=False):
    k = (taille, gras, noir)
    if k not in _IF:
        _IF[k] = ImageFont.truetype(F_INTER_NOIR if noir else (F_INTER_GRAS if gras else F_INTER), int(round(taille)))
    return _IF[k]


def inter_largeur(texte, taille=18, gras=False, noir=False):
    return _inter_font(taille, gras, noir).getlength(texte)


def inter(img, x, y, texte, taille=18, gras=False, couleur=None, ancre="g", ombre=False, alpha=255,
          ancre_v="haut", noir=False):
    """Texte Inter sur l'image FINALE (px écran), antialiasé comme le rendu TTF du jeu.
    taille 18 = Inter 9 GUI à l'échelle 2 (corps de texte du mod). y = haut des capitales
    (ancre_v 'haut'), ligne de base ('base') ou milieu des capitales ('milieu').
    ombre : ombre du jeu (décalage 2 px, couleur à 25 %). Renvoie la largeur (px)."""
    f = _inter_font(taille, gras, noir)
    c = hexc(couleur or PAL["creme"], alpha)
    w = f.getlength(texte)
    bb = f.getbbox("H")
    cap_top, base = bb[1], bb[3]
    if ancre == "c":
        x -= w / 2
    elif ancre == "d":
        x -= w
    if ancre_v == "base":
        y -= base - cap_top
    elif ancre_v == "milieu":
        y -= (base - cap_top) / 2
    X, Y = int(round(x)), int(round(y - cap_top))
    d = ImageDraw.Draw(img)
    if ombre:
        d.text((X + 2, Y + 2), texte, font=f, fill=(int(c[0] * .25), int(c[1] * .25), int(c[2] * .25), c[3]))
    d.text((X, Y), texte, font=f, fill=c)
    return w


def inter_lignes(texte, largeur_max, taille=18, gras=False):
    """Coupe un texte en lignes de largeur max (px) — retours à la ligne respectés."""
    out = []
    for para in texte.split("\n"):
        cur = ""
        for mot in para.split(" "):
            t = (cur + " " + mot).strip()
            if inter_largeur(t, taille, gras) <= largeur_max or not cur:
                cur = t
            else:
                out.append(cur)
                cur = mot
        out.append(cur)
    return out


def paragraphe(img, x, y, texte, largeur_max, taille=18, interligne=None, gras=False, couleur=None,
               ancre="g", alpha=255):
    """Paragraphe Inter (couche finale). interligne par défaut = 1,45 x taille.
    Renvoie la hauteur occupée (px)."""
    il = interligne or int(round(taille * 1.45))
    lignes = inter_lignes(texte, largeur_max, taille, gras)
    for i, ln in enumerate(lignes):
        inter(img, x, y + i * il, ln, taille, gras, couleur, ancre, alpha=alpha)
    return len(lignes) * il


# ───────────────────────── fonds ─────────────────────────
def fond_jeu(capture="2026-10-08_15.29.06.png", voile=0.72, teinte=(10, 6, 8), flou=0.0, recadrer=True):
    """Le jeu visible derrière l'écran, sous un voile (DA §2) : capture réelle du client de
    dev (run/screenshots), recadrée pour ôter la barre d'action et le chat, agrandie en
    1920x1080, puis assombrie. Renvoie une Image RGBA 1920x1080 pour Maquette(fond=…)."""
    p = capture if os.path.isabs(capture) else os.path.join(CAPTURES, capture)
    im = Image.open(p).convert("RGB")
    W0, H0 = im.size                       # efface le viseur au centre de la capture
    r = max(10, W0 // 80)
    box = (W0 // 2 - r, H0 // 2 - r, W0 // 2 + r, H0 // 2 + r)
    big = im.crop((box[0] - 2 * r, box[1] - 2 * r, box[2] + 2 * r, box[3] + 2 * r)).filter(ImageFilter.MedianFilter(9))
    im.paste(big.crop((2 * r, 2 * r, 4 * r, 4 * r)), box[:2])
    if recadrer:      # ôte barre d'action, chat, notifications et main du joueur (bas d'écran)
        W, H = im.size
        ch = int(H * 0.66)
        cw = int(ch * 16 / 9)
        x0 = int((W - cw) * 0.35)
        im = im.crop((x0, 0, x0 + cw, ch))
    im = im.resize((1920, 1080), Image.BILINEAR)
    if flou:
        im = im.filter(ImageFilter.GaussianBlur(flou))
    a = np.asarray(im, float)
    t = np.array(teinte, float)
    a = a * (1 - voile) + t * voile
    return Image.fromarray(a.astype(np.uint8), "RGB").convert("RGBA")


def voile_vignette(img, force=0.55, couleur=(6, 3, 4)):
    """Vignettage doux (couche finale) : assombrit les bords, garde le centre."""
    W, H = img.size
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    a = np.clip((d - 0.55) / 0.85, 0, 1) ** 1.6 * force
    lay = Image.new("RGBA", (W, H), tuple(couleur) + (0,))
    lay.putalpha(Image.fromarray((a * 255).astype(np.uint8), "L"))
    img.alpha_composite(lay)


# ───────────────────────── la maquette (flux 960 -> x2 -> texte) ─────────────────────────
class Maquette:
    """Écran 960x540 GUI rendu en 1920x1080.

    m.px            couche pixel courante (Image RGBA 960x540) — dessiner dessus avec le kit
    m.calque()      nouvelle couche pixel au-dessus de tout ce qui est déjà empilé
    m.final(fn)     opération libre sur l'image finale : fn(img1920)
    m.inter / m.paragraphe / m.kanji / m.arcade_fin / m.perso / m.disque / m.anneau /
    m.trait / m.halo / m.halo_lanterne / m.braises   -> coordonnées GUI (float ok), rendu en 1080p
    m.rendre()      -> Image RGB 1920x1080
    """

    def __init__(self, fond=None, taille=(GUI_W, GUI_H)):
        self.W, self.H = taille
        self.etapes = []
        if isinstance(fond, Image.Image):
            fb = fond.convert("RGBA")
            if fb.size == (self.W, self.H):
                self.etapes.append(("px", fb.copy()))
            else:
                if fb.size != (self.W * ECHELLE_GUI, self.H * ECHELLE_GUI):
                    fb = fb.resize((self.W * ECHELLE_GUI, self.H * ECHELLE_GUI), Image.BILINEAR)
                self.etapes.append(("op", lambda im, fb=fb: im.alpha_composite(fb)))
                self.etapes.append(("px", Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))))
        else:
            c = hexc(fond) if fond is not None else PAL["fond"]
            self.etapes.append(("px", Image.new("RGBA", (self.W, self.H), c)))

    @property
    def px(self):
        if self.etapes[-1][0] != "px":
            self.etapes.append(("px", Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))))
        return self.etapes[-1][1]

    def calque(self):
        self.etapes.append(("px", Image.new("RGBA", (self.W, self.H), (0, 0, 0, 0))))
        return self.etapes[-1][1]

    def final(self, fn):
        self.etapes.append(("op", fn))

    # — texte (GUI -> écran) —
    def inter(self, x, y, texte, taille=9, gras=False, couleur=None, ancre="g", ombre=False, alpha=255,
              ancre_v="haut", noir=False):
        """Inter : taille en unités GUI (9 = corps du mod). Renvoie la largeur en GUI."""
        S = ECHELLE_GUI
        self.final(lambda im: inter(im, x * S, y * S, texte, taille * S, gras, couleur, ancre, ombre, alpha, ancre_v, noir))
        return inter_largeur(texte, taille * S, gras, noir) / S

    def paragraphe(self, x, y, texte, largeur_max, taille=9, interligne=None, gras=False, couleur=None,
                   ancre="g", alpha=255):
        S = ECHELLE_GUI
        il = (interligne * S) if interligne else None
        self.final(lambda im: paragraphe(im, x * S, y * S, texte, largeur_max * S, taille * S, il, gras, couleur, ancre, alpha))
        n = len(inter_lignes(texte, largeur_max * S, taille * S, gras))
        return n * ((interligne or round(taille * S * 1.45) / S))

    def kanji(self, x, y, texte, taille=16, police="unifont", couleur=None, ancre="g", vertical=False, alpha=255):
        """Kanji rendus à la résolution écran (taille en px ÉCRAN : 16 = police par défaut
        du jeu à l'échelle 2). x, y en GUI."""
        S = ECHELLE_GUI
        self.final(lambda im: kanji(im, int(round(x * S)), int(round(y * S)), texte, taille, police, couleur, ancre, vertical, alpha))

    def arcade_fin(self, x, y, texte, echelle=0.5, couleur=None, ancre="g", ombre=False, alpha=255):
        """ArcadePix à une échelle non entière en GUI (0,5 ; 1,5…) rendue au pixel écran :
        taille écran = 20 x echelle (0,5 -> 10 px, 1,5 -> 30 px, toujours pixel exact)."""
        S = ECHELLE_GUI
        t = int(round(10 * S * echelle))
        self.final(lambda im: arcade(im, int(round(x * S)), int(round(y * S)), texte, t, couleur, ancre, ombre, alpha))
        return arcade_largeur(texte, t) / S

    # — entités et formes lissées —
    def perso(self, rendu_img, pieds):
        """Personnage (perso.rendu_*) posé à la résolution écran, pieds en (x, y) GUI."""
        S = ECHELLE_GUI

        def op(im):
            px, py = rendu_img.info.get("pieds", (rendu_img.width / 2, rendu_img.height))
            coller(im, rendu_img, int(round(pieds[0] * S - px)), int(round(pieds[1] * S - py)))
        self.final(op)

    def disque(self, cx, cy, r, couleur, alpha=255):
        S = ECHELLE_GUI
        self.final(lambda im: disque_lisse(im, cx * S, cy * S, r * S, couleur, alpha))

    def anneau(self, cx, cy, r, ep, couleur, debut=0.0, fin=360.0, alpha=255, bouts_ronds=True):
        S = ECHELLE_GUI
        self.final(lambda im: anneau_lisse(im, cx * S, cy * S, r * S, ep * S, couleur, debut, fin, alpha, bouts_ronds))

    def trait(self, points, ep, couleur, alpha=255, courbe=False):
        S = ECHELLE_GUI
        self.final(lambda im: trait_lisse(im, [(a * S, b * S) for a, b in points], ep * S, couleur, alpha, courbe))

    def halo(self, cx, cy, r, couleur, force=0.6, additif=True):
        S = ECHELLE_GUI
        self.final(lambda im: halo(im, cx * S, cy * S, r * S, couleur, force, additif))

    def halo_lanterne(self, cx, cy, stat=0, taille=1.0, allumee=1.0, eclat=0.6):
        S = ECHELLE_GUI
        self.final(lambda im: halo_lanterne(im, cx * S, cy * S, stat, taille * S / 2, allumee, eclat))

    def lanterne(self, cx, cy, stat=0, taille=1.0, allumee=1.0, eclat=0.6, survol=False, seed=0):
        """Lanterne complète : halo lissé (couche finale) PUIS corps pixel au-dessus."""
        self.halo_lanterne(cx, cy, stat, taille, allumee, eclat)
        return lanterne(self.calque(), cx, cy, stat, taille, allumee, survol=survol, seed=seed)

    def braises(self, zone, n=40, couleur="E8742C", seed=0):
        S = ECHELLE_GUI
        self.final(lambda im: braises(im, [v * S for v in zone], n, couleur, seed))

    def vignette(self, force=0.55):
        self.final(lambda im: voile_vignette(im, force))

    def rendre(self):
        S = ECHELLE_GUI
        out = Image.new("RGBA", (self.W * S, self.H * S), (0, 0, 0, 255))
        for kind, val in self.etapes:
            if kind == "px":
                out.alpha_composite(val.resize((self.W * S, self.H * S), Image.NEAREST))
            else:
                val(out)
        return out.convert("RGB")

    def sauver(self, chemin):
        self.rendre().save(chemin)
        return chemin
