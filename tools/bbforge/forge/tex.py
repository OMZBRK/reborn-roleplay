"""bbforge.tex — peinture de l'atlas, patron par patron.

Parti pris : **uni**. Une face = un ton, plus un lisere clair en haut et sombre
en bas. Aucun bruit, aucun craquelement, aucun degrade dans la texture — le
volume est donne par l'ombrage de Gouraud du rasteriseur, pas par le pixel.

La couleur suit une logique de dragon, pas une logique de materiau :

    dos      bleu profond        le plus sombre, il recoit la lumiere du ciel
    flancs   bleu moyen          la teinte d'identite
    ventre   bleu tres pale      contre-ombre claire, c'est ce qui fait "dragon"
    crete    cyan lumineux       l'accent
    cornes   os bleute           presque blanc
    oeil     blanc chaud

L'identite Raiton tient en deux choses seulement : l'arete dorsale
incandescente et un filament qui court le long des flancs en se raccordant d'un
anneau au suivant. Tout le reste reste calme.
"""
from __future__ import annotations

import colorsys
import math
import io

import numpy as np

from forge.bb import FACES

# --------------------------------------------------------------------- rampes
# 5 tons par materiau, du plus sombre au plus clair

def _ramp(h0, h1, s0, s1, v0, v1, n=9):
    """Construit une rampe en HSV, du plus sombre au plus clair.

    Trois techniques classiques y sont appliquees d'un coup :
      - DECALAGE DE TEINTE : les ombres virent a l'indigo, les lumieres au cyan.
        Une rampe de valeur pure (meme teinte partout) sort toujours morte.
      - DESATURATION VERS LES CLAIRS : la lumiere lave la couleur.
      - BANDE TENUE : valeur et saturation restent dans la plage mesuree sur la
        reference (val 73-169, sat 24-60), ce qui garantit l'harmonie entre
        materiaux au lieu de la laisser au hasard.
    """
    out = []
    for i in range(n):
        a = i / (n - 1)
        h = (h0 + (h1 - h0) * a) / 360.0
        sat = (s0 + (s1 - s0) * a) / 100.0
        val = (v0 + (v1 - v0) * a) / 255.0
        c = colorsys.hsv_to_rgb(h, sat, val)
        out.append(tuple(int(round(x * 255)) for x in c))
    return out


RAMPS = {
    # Kirin de l'anime : un corps DE foudre. Les ombres tirent vers l'indigo
    # violet, les lumieres vers le cyan blanc ; le ventre et la gueule sont
    # les zones les plus chaudes, l'oeil est le seul accent chaud (or).
    #              teinte      saturation   valeur
    "back":    _ramp(242, 214, 74, 52,  46, 148),
    "body":    _ramp(230, 200, 72, 40,  72, 214),
    "head":    _ramp(228, 199, 68, 38,  80, 218),
    "belly":   _ramp(214, 190, 44, 10, 150, 250),
    "jaw":     _ramp(214, 192, 40, 12, 150, 246),
    "horn":    _ramp(216, 198, 22,  6, 150, 246),
    "tooth":   _ramp(206, 194, 14,  2, 196, 252),
    "claw":    _ramp(212, 198, 26, 10, 160, 240),
    "crest":   _ramp(204, 186, 90, 36, 110, 252),
    "fin":     _ramp(204, 188, 84, 40, 110, 246),
    "whisker": _ramp(200, 188, 44,  8, 190, 254),
    "eye":     _ramp( 30,  48, 98, 62,  90, 250),
    "maw":     _ramp(200, 190, 70,  6, 140, 255),
    "rock":    _ramp(232, 226, 30, 18,  30,  92),
    # cavites : orbite, narine — presque noir, un peu d'indigo
    "socket":  _ramp(238, 226, 54, 34,  12,  48),
    # nuages : la couche orageuse sous laquelle le Kirin plonge
    "cloud":   _ramp(226, 208, 52, 26,  54, 176),
    "storm":   _ramp(240, 222, 58, 36,  20,  86),
    # eclair : blanc cyan, tout en emission
    "bolt":    _ramp(196, 188, 40,  0, 230, 255),
    # Son Goku : fourrure rouge (ombres cramoisies, lumieres orangees),
    # poitrail plus clair, face creme, os des cretes / cornes / griffes
    # (teintes > 360 : la rampe doit tourner par le rouge, pas par le vert)
    "fur":       _ramp(357, 368, 94, 82,  62, 212),
    "fur_light": _ramp(  4,  16, 82, 62, 104, 232),
    "fur_dark":  _ramp(352, 364, 92, 80,  38, 150),
    "skin":      _ramp( 34,  44, 52, 22, 150, 248),
    "bone":      _ramp( 38,  48, 40, 16, 124, 238),
    # accessoires et sorts (sac, corbeau, boule de feu, mur, lame)
    "leather":   _ramp( 22,  30, 64, 46,  66, 186),
    "strap":     _ramp( 20,  28, 60, 44,  44, 140),
    "cloth":     _ramp( 38,  44, 36, 18, 128, 236),
    "buckle":    _ramp( 42,  50, 58, 22, 120, 246),
    "stone":     _ramp( 28,  36, 16,  6,  62, 172),
    "earth":     _ramp( 24,  30, 52, 36,  54, 148),
    "moss":      _ramp( 92,  80, 55, 35,  70, 170),
    "fire_core": _ramp( 50,  58, 60,  6, 236, 255),
    "fire":      _ramp( 16,  42, 98, 78, 190, 255),
    "ember":     _ramp(  4,  20, 96, 88, 100, 214),
    "smoke":     _ramp( 20,  30, 10,  4,  40,  96),
    "chakra":    _ramp(202, 192, 78, 12, 168, 255),
    "steel":     _ramp(216, 210, 12,  4,  96, 232),
    "wrap":      _ramp(226, 220, 14,  8,  36, 110),
    "feather":   _ramp(230, 244, 40, 18,  26, 118),
    "beak":      _ramp( 38,  46, 36, 18,  84, 168),
    "glint":     _ramp( 40,  44, 20,  4, 200, 255),
    # parchemins : papier washi, sceau vermillon, cordons a la couleur du rang
    "washi":     _ramp( 32,  42, 44, 22, 168, 252),
    "seal":      _ramp(352, 358, 86, 72,  86, 196),
    "gold":      _ramp( 32,  46, 82, 48, 104, 244),
    "cord_d":    _ramp(206, 212, 18,  8,  84, 196),
    "cord_c":    _ramp(142, 150, 52, 30,  60, 188),
    "cord_b":    _ramp(224, 214, 64, 40,  64, 206),
    "cord_a":    _ramp(274, 262, 54, 32,  66, 210),
    "cord_s":    _ramp(352, 362, 84, 66,  80, 222),
}

ARC = [(96, 178, 226), (156, 216, 246), (208, 240, 253), (246, 253, 255)]

# emission : seuls la crete, les filaments, les moustaches et l'oeil portent
# de la lumiere. Le corps reste mat, sinon tout se noie.
EMIS = {
    "back": 0.08, "body": 0.14, "head": 0.12, "belly": 0.34, "jaw": 0.30,
    "horn": 0.06, "tooth": 0.16, "claw": 0.08, "crest": 0.52, "fin": 0.40,
    "whisker": 0.85, "eye": 1.20, "maw": 0.95, "rock": 0.0,
    "socket": 0.0, "cloud": 0.10, "storm": 0.0, "bolt": 1.35,
    "fur": 0.0, "fur_light": 0.0, "fur_dark": 0.0, "skin": 0.02, "bone": 0.02,
    "leather": 0.0, "strap": 0.0, "cloth": 0.0, "buckle": 0.04, "stone": 0.0,
    "earth": 0.0, "moss": 0.0, "fire_core": 1.5, "fire": 1.1, "ember": 0.55,
    "smoke": 0.0, "chakra": 1.25, "steel": 0.03, "wrap": 0.0, "feather": 0.0,
    "beak": 0.0, "glint": 0.5,
    "washi": 0.0, "seal": 0.0, "gold": 0.06, "cord_d": 0.0, "cord_c": 0.0, "cord_b": 0.0, "cord_a": 0.0, "cord_s": 0.04,
}

FILAMENT = 1.05


def _hash(x, y, seed):
    h = (int(x) * 374761393 + int(y) * 668265263 + int(seed) * 2654435761) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


class Canvas:
    def __init__(self, size):
        self.w, self.h = size if isinstance(size, (tuple, list)) else (size, size)
        self.size = self.w
        self.rgb = np.zeros((self.h, self.w, 3), np.uint8)
        self.emis = np.zeros((self.h, self.w), np.float32)

    def fill(self, r, color, emis=None):
        x1, y1, x2, y2 = r
        self.rgb[y1:y2, x1:x2] = np.array(color, np.uint8)
        if emis is not None:
            self.emis[y1:y2, x1:x2] = emis

    def px(self, x, y, color, emis=None):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            self.rgb[y, x] = color
            if emis is not None:
                self.emis[y, x] = max(float(self.emis[y, x]), emis)

    def hline(self, x1, x2, y, color, emis=None):
        for x in range(int(x1), int(x2)):
            self.px(x, y, color, emis)

    def vline(self, x, y1, y2, color, emis=None):
        for y in range(int(y1), int(y2)):
            self.px(x, y, color, emis)

    def png(self) -> bytes:
        from PIL import Image
        a = np.full((self.h, self.w, 1), 255, np.uint8)
        im = Image.fromarray(np.concatenate([self.rgb, a], 2), "RGBA")
        buf = io.BytesIO()
        im.save(buf, "PNG")
        return buf.getvalue()


# --------------------------------------------------------------------- briques

# Bayer 8x8 : le motif est assez fin pour disparaitre a distance, la ou un
# 4x4 reste lisible comme un damier.
BAYER = np.array([[0, 32, 8, 40, 2, 34, 10, 42], [48, 16, 56, 24, 50, 18, 58, 26],
                  [12, 44, 4, 36, 14, 46, 6, 38], [60, 28, 52, 20, 62, 30, 54, 22],
                  [3, 35, 11, 43, 1, 33, 9, 41], [51, 19, 59, 27, 49, 17, 57, 25],
                  [15, 47, 7, 39, 13, 45, 5, 37], [63, 31, 55, 23, 61, 29, 53, 21]],
                 np.float32) / 64.0
DITHER = 0.55      # largeur de la zone tramee, en fraction d'un cran de rampe


def _grain(gax, seed, amount):
    """Variation fine le long d'un axe — le grain du bois des manches Reborn."""
    g = np.zeros_like(gax, np.float32)
    for k in (1, 3):
        h = ((gax // k) * 374761393 + seed * 2654435761) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        g += (((h ^ (h >> 16)) & 0xFFFF) / 65535.0 - 0.5)
    return g * amount


def _grad(c, r, ramp, em, lo=0.15, hi=0.92, dens=1, axis="v", profile="ramp",
          grain=0.55, seed=0, em2=None):
    """Degrade tramé, oriente et profile selon la face.

    Deux choses apprises des textures d'armes Reborn :

    - le degrade court EN TRAVERS de la piece, pas le long. Sur une face de
      dessus (largeur x longueur) un degrade longitudinal fait varier la teinte
      d'un anneau a l'autre et cree des bandes ; en travers, il fait lire un
      CYLINDRE — c'est le manche du kendo, clair au bord, sombre au coeur.
    - une variation fine le long de la piece (le grain) casse l'aplat sans
      salir, parce qu'elle est monodimensionnelle.

    `axis` : "v" (haut->bas) ou "u" (gauche->droite).
    `profile` : "ramp" (lineaire) ou "tube" (clair au centre, sombre aux bords).
    `lo`/`hi` sont des FRACTIONS de rampe : changer le nombre de tons ne demande
    de retoucher aucun appel.
    """
    x1, y1, x2, y2 = r
    h, w = y2 - y1, x2 - x1
    if h < 1 or w < 1:
        return
    n = len(ramp) - 1
    ys, xs = np.mgrid[0:h, 0:w]
    if axis == "v":
        t = ys / max(1, h - 1)
        gax = xs
    else:
        t = xs / max(1, w - 1)
        gax = ys
    if profile == "tube":
        t = np.abs(t - 0.45) * 1.9          # crete decalee : lumiere d'en haut
    v = (hi - (hi - lo) * np.clip(t, 0, 1)) * n
    v = v + _grain(gax, seed, grain)
    # occlusion de contact sur le bord bas de la face
    v = v - np.clip(1.0 - (h - 1 - ys) / max(1.0, 1.6 * dens), 0, 1) * 0.20 * n
    idx = np.floor(v + (BAYER[ys % 8, xs % 8] - 0.5) * DITHER + 0.5).astype(np.int32)
    c.rgb[y1:y2, x1:x2] = np.array(ramp, np.uint8)[np.clip(idx, 0, n)]
    if em2 is None:
        c.emis[y1:y2, x1:x2] = em
    else:                                   # em en haut de la face, em2 en bas
        c.emis[y1:y2, x1:x2] = em + (em2 - em) * np.clip(t, 0, 1)


def _filament(c, r, y_in, y_out, seed, dens=1, color=None, emis=None):
    """Trait electrique qui traverse la face et se raccorde au segment voisin.

    Longs segments droits, cassures franches : a 32x un zigzag texel par texel
    lit comme une chaine de maillons.
    """
    x1, y1, x2, y2 = r
    h, w = y2 - y1, x2 - x1
    if w < 5 * dens or h < 4 * dens:
        return
    color = color or ARC[2]
    emis = FILAMENT if emis is None else emis
    step = max(3, int(5 * dens))
    xs = list(range(x1, x2, step)) + [x2 - 1]
    pts = []
    for i, x in enumerate(xs):
        a = (x - x1) / max(1, w - 1)
        y = y1 + (y_in + (y_out - y_in) * a) * h + (_hash(i, 1, seed) - 0.5) * 2.0 * dens
        pts.append((x, max(y1 + 1, min(y2 - 2, y))))
    for i in range(len(pts) - 1):
        (xa, ay), (xb, by) = pts[i], pts[i + 1]
        span = max(1, xb - xa)
        for x in range(xa, xb + 1):
            c.px(x, round(ay + (by - ay) * (x - xa) / span), color, emis)


def _eye(c, r, dens=1):
    """Globe dore a fente verticale : le seul accent chaud du modele.

    Degrade vertical or sombre -> or clair (la paupiere ombre le haut), fente
    noire au centre, reflet blanc en haut a gauche. L'emission est forte :
    dans le rendu c'est l'oeil qui accroche le bloom, pas le corps.
    """
    x1, y1, x2, y2 = r
    ramp = RAMPS["eye"]
    w, h = x2 - x1, y2 - y1
    if w < 1 or h < 1:
        return
    _grad(c, r, ramp, EMIS["eye"], 0.25, 0.92, dens, axis="v", profile="tube",
          grain=0.0)
    if h >= 3:
        c.hline(x1, x2, y1, ramp[1], 0.4)                 # ombre de paupiere
    if w >= 3:
        pw = max(1, int(round(dens)))
        cx = x1 + w // 2 - pw // 2
        for k in range(pw):
            c.vline(cx + k, y1, y2, RAMPS["socket"][0])
            for y in range(y1, y2):
                c.emis[y, cx + k] = 0.0
    if w >= 4 and h >= 3:
        for k in range(max(1, dens // 2)):
            c.px(x1 + 1 + k, y1 + 1, (255, 252, 236), 1.5)


def _eye_socket(c, r, dens=1):
    """Oeil + orbite sur un seul cube : bord sombre, globe lumineux, fente."""
    x1, y1, x2, y2 = r
    w, h = x2 - x1, y2 - y1
    sock = RAMPS["socket"]
    ramp = RAMPS["eye"]
    c.fill(r, sock[1], 0.0)
    b = max(1, int(round(1.2 * dens)))
    if w <= 2 * b + 2 or h <= 2 * b + 2:
        return
    inner = (x1 + b, y1 + b, x2 - b, y2 - b)
    _eye(c, inner, dens)
    # coin interne de l'orbite : un cran plus clair, l'os autour de l'oeil
    c.hline(x1, x2, y1, sock[3])


def _rockface(c, r, seed, face, dens=1):
    """Roche : deux tons, une cassure horizontale. Volontairement pauvre — la
    lisibilite du decor vient de l'ombrage, pas de la texture."""
    x1, y1, x2, y2 = r
    ramp = RAMPS["rock"]
    base = 3 if face == "up" else 2
    c.fill(r, ramp[base], 0.0)
    h = y2 - y1
    if h >= 4:
        cut = y1 + 1 + int(_hash(x1, y1, seed) * (h - 2))
        c.hline(x1, x2, cut, ramp[max(0, base - 1)])
        c.hline(x1, x2, y1, ramp[min(base + 1, 4)])
        c.hline(x1, x2, y2 - 1, ramp[0])


# --------------------------------------------------------------------- routage

def paint_face(c, cube, face, kind, seed, dens=1):
    r = cube.uv[face]
    x1, y1, x2, y2 = r
    if x2 <= x1 or y2 <= y1:
        return
    p = cube.paint
    ramp = RAMPS.get(kind, RAMPS["body"])
    em = EMIS.get(kind, 0.06)

    if kind == "eye":
        # face exterieure (est/ouest) : oeil complet ; autres faces : os de l'orbite
        if face in ("east", "west"):
            _eye_socket(c, r, dens)
        else:
            c.fill(r, RAMPS["socket"][2], 0.0)
        return
    if kind == "rock":
        _rockface(c, r, seed, face, dens)
        return
    if kind in ("cloud", "storm"):
        # nuage : sommet clair, ventre sombre, aucun grain — la masse doit
        # lire de loin, pas le texel
        if face == "up":
            _grad(c, r, ramp, em * 1.4, 0.48, 0.92, dens, axis="u", profile="tube",
                  grain=0.15, seed=seed)
        elif face == "down":
            _grad(c, r, ramp, 0.0, 0.00, 0.30, dens, axis="u", profile="tube",
                  grain=0.15, seed=seed)
        else:
            _grad(c, r, ramp, em, 0.08, 0.78, dens, grain=0.2, seed=seed)
        return
    if kind == "bolt":
        c.fill(r, ramp[len(ramp) - 1], em)
        x1, y1, x2, y2 = r
        if x2 - x1 >= 3:                      # coeur blanc, bords cyan
            c.vline(x1, y1, y2, ARC[1], em * 0.7)
            c.vline(x2 - 1, y1, y2, ARC[1], em * 0.7)
        return
    if kind == "socket":
        c.fill(r, ramp[1], 0.0)
        return
    if kind == "maw":
        # fond de gueule incandescent : blanc au centre, cyan sur les bords
        _grad(c, r, ramp, em, 0.30, 1.00, dens, axis="v", profile="tube", grain=0.0)
        return

    if kind in ("body", "head"):
        if face in ("east", "west"):
            # flanc = tube de foudre : clair au coeur, sombre vers le dos et
            # le ventre ; c'est ce qui donne le volume rond au Kirin
            _grad(c, r, ramp, em, 0.16, 1.00, dens, axis="v", profile="tube",
                  grain=0.35, seed=seed)
            if p.get("vein", True):
                vf, vb = p.get("vfront", 0.45), p.get("vback", 0.45)
                # face est : le bord gauche du patron est l'arriere du cube ;
                # face ouest : c'est l'avant. On echange pour raccorder.
                y_in, y_out = (vb, vf) if face == "east" else (vf, vb)
                _filament(c, r, y_in, y_out, seed, dens)
                # second filament, plus fin et plus faible, qui s'ecarte du
                # premier : deux traits font un reseau, un seul fait une rayure
                _filament(c, r, y_in + 0.22, y_out - 0.18, seed + 31, dens,
                          color=ARC[1], emis=FILAMENT * 0.55)
        elif face in ("up", "down"):
            _grad(c, r, ramp, em, 0.20, 1.00, dens, axis="u", profile="tube", seed=seed)
        else:
            _grad(c, r, ramp, em, 0.14, 0.94, dens, seed=seed)
            if p.get("nostrils") and face == "north" and x2 - x1 >= 8:
                w = x2 - x1
                sock = RAMPS["socket"]
                for fx in (0.26, 0.74):
                    nx = x1 + int(w * fx)
                    ny = y1 + (y2 - y1) // 2
                    for dx in range(-dens // 2, dens // 2 + 1):
                        c.px(nx + dx, ny, sock[1], 0.0)
                        c.px(nx + dx, ny + 1, sock[0], 0.0)
    elif kind == "back":
        if face == "up":
            _grad(c, r, ramp, em, 0.24, 1.00, dens, axis="u", profile="tube", seed=seed)
            if x2 - x1 >= 3:
                cx = (x1 + x2) // 2                  # arete dorsale incandescente
                for k in range(max(1, dens // 2)):
                    c.vline(cx + k, y1, y2, ARC[3], FILAMENT)
                    c.vline(cx - 1 + k, y1, y2, ARC[0], FILAMENT * 0.4)
        else:
            _grad(c, r, ramp, em, 0.16, 0.96, dens, seed=seed)
    elif kind == "belly":
        if face in ("up", "down"):
            _grad(c, r, ramp, em, 0.34, 1.00, dens, axis="u", profile="tube", seed=seed)
        else:
            _grad(c, r, ramp, em, 0.28, 1.00, dens, seed=seed)
    elif kind == "jaw":
        _grad(c, r, ramp, em, 0.22, 0.90, dens, seed=seed)
    elif kind in ("crest", "fin"):
        _grad(c, r, ramp, em, 0.10, 0.96, dens, grain=0.35, seed=seed)
        if face in ("east", "west", "north", "south"):
            for k in range(max(1, dens // 2)):      # bord d'attaque
                c.hline(x1, x2, y1 + k, ramp[len(ramp) - 1], em * 2.6)
    elif kind in ("leather", "strap"):
        # cuir : degrade + une couture claire en pointille a 2 px du bord
        _grad(c, r, ramp, em, 0.16, 0.92, dens, grain=0.35, seed=seed)
        w_, h_ = x2 - x1, y2 - y1
        if w_ >= 8 and h_ >= 6:
            thread = ramp[len(ramp) - 2]
            for y in (y1 + 2 * dens // 1, y2 - 1 - 2 * dens // 1):
                for x in range(x1 + 2, x2 - 2, 2 * max(1, dens)):
                    c.px(x, y, thread, 0.0)
                    if dens >= 2:
                        c.px(x + 1, y, thread, 0.0)
    elif kind == "washi":
        # papier roule : profil tube dans la longueur, fibres discretes
        _grad(c, r, ramp, em, 0.30, 1.00, dens, axis="v", profile="tube", grain=0.45, seed=seed)
    elif kind.startswith("cord_"):
        _grad(c, r, ramp, em, 0.18, 0.96, dens, axis="v", profile="tube", grain=0.2, seed=seed)
    elif kind == "cloth":
        _grad(c, r, ramp, em, 0.24, 0.96, dens, axis="u", profile="tube", grain=0.5, seed=seed)
    elif kind in ("buckle", "steel", "gold"):
        # metal : profil tube, reflet net en haut
        _grad(c, r, ramp, em, 0.12, 1.00, dens, axis="v", profile="tube", grain=0.2, seed=seed)
        c.hline(x1, x2, y1, ramp[len(ramp) - 1], em)
    elif kind == "stone":
        _grad(c, r, ramp, em, 0.14, 0.90, dens, grain=0.6, seed=seed)
        w_, h_ = x2 - x1, y2 - y1
        if w_ >= 6 and h_ >= 6:                            # une fissure par face
            x = x1 + int(_hash(seed, 1, 3) * (w_ - 2)) + 1
            for y in range(y1 + 1, y2 - 1):
                x = min(x2 - 2, max(x1 + 1, x + int(_hash(y, seed, 5) * 3) - 1))
                c.px(x, y, ramp[0], 0.0)
            c.hline(x1, x2, y2 - 1, ramp[0], 0.0)
            c.vline(x2 - 1, y1, y2, ramp[1], 0.0)
    elif kind == "earth":
        _grad(c, r, ramp, em, 0.14, 0.88, dens, grain=0.8, seed=seed)
        w_, h_ = x2 - x1, y2 - y1
        for k in range(max(1, w_ * h_ // 40)):             # cailloux
            px_ = x1 + int(_hash(k, seed, 7) * max(1, w_ - 2))
            py_ = y1 + int(_hash(k, seed, 9) * max(1, h_ - 1))
            c.px(px_, py_, ramp[1], 0.0)
            c.px(px_ + 1, py_, ramp[2], 0.0)
    elif kind == "moss":
        _grad(c, r, ramp, em, 0.20, 0.96, dens, grain=0.6, seed=seed)
        w_, h_ = x2 - x1, y2 - y1
        for k in range(max(1, w_ * h_ // 30)):
            c.px(x1 + int(_hash(k, seed, 11) * w_), y1 + int(_hash(k, seed, 13) * h_),
                 ramp[len(ramp) - 1], 0.0)
    elif kind in ("fire_core", "fire", "ember", "chakra"):
        # energie : coeur clair, bords colores, emission qui suit
        _grad(c, r, ramp, em, 0.20, 1.00, dens, axis="v", profile="tube", grain=0.0,
              seed=seed, em2=em * 0.45)
    elif kind == "feather":
        _grad(c, r, ramp, em, 0.08, 0.78, dens, grain=0.4, seed=seed)
        if y2 - y1 >= 3:
            c.hline(x1, x2, y2 - 1, ramp[min(len(ramp) - 1, 5)], 0.0)   # pointe des plumes
    elif kind in ("fur", "fur_light", "fur_dark"):
        # fourrure : degrade doux, grain plus marque dans le sens du poil (v)
        _grad(c, r, ramp, em, 0.12, 0.92, dens, grain=0.3, seed=seed)
    elif kind in ("skin", "bone"):
        _grad(c, r, ramp, em, 0.22, 0.96, dens, grain=0.25, seed=seed)
    elif kind == "horn":
        # corne = cylindre : profil tube en travers, grain dans la longueur
        if face in ("east", "west", "north", "south"):
            _grad(c, r, ramp, em, 0.18, 1.00, dens, axis="v", profile="tube",
                  grain=0.7, seed=seed)
        else:
            _grad(c, r, ramp, em, 0.18, 1.00, dens, axis="u", profile="tube", seed=seed)
    elif kind in ("tooth", "claw"):
        _grad(c, r, ramp, em, 0.34, 1.00, dens, grain=0.25, seed=seed)
        n = 3 if kind == "claw" else 4
        w = x2 - x1
        if w >= n * 2 and face != "up":
            for k in range(1, n):
                x = x1 + round(k * w / n)
                for j in range(max(1, dens // 2)):
                    c.vline(x + j, y1, y2, ramp[0], em * 0.5)
            if y2 - y1 >= 3:
                c.hline(x1, x2, y2 - 1, ramp[0], em * 0.4)
                for k in range(n):
                    x = x1 + round((k + 0.5) * w / n)
                    c.px(x, y2 - 2, ramp[len(ramp) - 1], em * 1.6)
    elif kind == "whisker":
        _grad(c, r, ramp, em, 0.30, 1.00, dens, axis="v", profile="tube",
              grain=0.4, seed=seed)
        c.hline(x1, x2, y1, ramp[len(ramp) - 1], FILAMENT)
    else:
        _grad(c, r, ramp, em, 0.14, 0.90, dens, seed=seed)


def paint_model(model, seed0=1000, dens=1):
    """Peint tous les patrons d'un modele deja passe par pack_box_uv().

    `dens` doit valoir la densite utilisee au packing : les motifs sont
    dimensionnes en texels, il faut les remettre a l'echelle sinon un atlas 32x
    affiche simplement le meme motif deux fois plus gros.
    """
    c = Canvas(model.res)
    for mat, r in getattr(model, "swatches", {}).items():
        ramp = RAMPS.get(mat, RAMPS["body"])
        c.fill(r, ramp[3], EMIS.get(mat, 0.06))
    for i, cube in enumerate(model.all_cubes()):
        cd = cube.dens or dens
        for face in FACES:
            if face in cube.hide:
                continue
            paint_face(c, cube, face, cube.mat_of(face), seed0 + i * 7, cd)
    return c
