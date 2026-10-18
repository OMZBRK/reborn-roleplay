# -*- coding: utf-8 -*-
"""
perso_art.py — primitives de dessin de la SÉLECTION et de la CRÉATION de personnage (pixel art, 1 texel = 1 GUI).

Copie versionnée des fonctions des maquettes validées (hors dépôt) :
  - Maquettes/fiche-shinobi/v2/portrait/maquette.py  (laque, liserés, ferrures, planches, cordelettes, sceaux,
    boutons, lumières tramées) — même code, recopié tel quel ;
  - Maquettes/personnage/maquette.py  (ciel, étoiles, lune, montagnes, villages, eau, pierres de gué, lotus,
    roseaux, réglettes, toise, tuiles, pastilles…) — « F. » retiré, halo_px rendu au pas GUI sur le calque.
Seules les parties FIXES sont dessinées ici (DA §6) ; texte Inter / ArcadePix ×0,5, emblèmes et personnages
sont posés par le mod à la résolution écran. Utilisé par tools/gen_personnage_textures.py.
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ICI, "style"))
import kit    # noqa: E402
import perso  # noqa: E402
from kit import PAL, R_OR, R_LAQUE, R_LAQUE_ROUGE, hexc, rect, arcade, coller  # noqa: E402

S = kit.ECHELLE_GUI
HALO_K = 1.0


class Calque:
    """Adaptateur de la Maquette du kit pour une texture : tout se dessine sur une seule image pixel ; le texte
    à la résolution écran (inter, arcade_fin) et les opérations finales sont ignorés (le mod les pose)."""

    def __init__(self, img):
        self._img = img

    @property
    def px(self):
        return self._img

    def calque(self):
        return self._img

    def final(self, fn):
        pass

    def inter(self, *a, **k):
        return 0

    def arcade_fin(self, *a, **k):
        return 0


def halo_px(m, cx, cy, r, couleur, force=0.15, niveaux=3, ry_k=1.0, sat=0.8, parts=None):
    """halo_px de la maquette (ovales concentriques nets au pas GUI, additifs), posé directement sur le calque."""
    img = m.px if hasattr(m, "px") else m
    force = force * HALO_K
    if force <= 0.002:
        return
    parts = parts or {1: [1.0], 2: [0.4, 1.0], 3: [0.27, 0.53, 1.0]}[niveaux]
    ry = r * ry_k
    gx0, gy0 = int(math.floor(cx - r - 2)), int(math.floor(cy - ry - 2))
    w, h = int(2 * r + 5), int(2 * ry + 5)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.sqrt(((xx + gx0 + 0.5 - cx) / r) ** 2 + ((yy + gy0 + 0.5 - cy) / ry) ** 2)
    n = len(parts)
    band = np.clip(np.floor((1 - d) * n + 1e-6), 0, n).astype(int)
    vals = np.array([0.0] + list(parts))
    q = vals[band] * (d < 1) * force
    col = np.array(desat(couleur, sat), float)
    X0, Y0 = max(0, gx0), max(0, gy0)
    X1, Y1 = min(img.width, gx0 + w), min(img.height, gy0 + h)
    if X1 <= X0 or Y1 <= Y0:
        return
    A = q[Y0 - gy0:Y1 - gy0, X0 - gx0:X1 - gx0]
    base = np.asarray(img.crop((X0, Y0, X1, Y1))).astype(float)
    base[..., :3] = np.minimum(255, base[..., :3] + col * A[..., None])
    img.paste(Image.fromarray(base.astype(np.uint8), "RGBA"), (X0, Y0))


OR = PAL["or_"]


OR_CLAIR = PAL["or_clair"]


OR_MOYEN = hexc("C8A05A")


OR_ETIQ = hexc("A8844A")


OR_ETEINT = hexc("8E6A30")


BISEAU_CLAIR = hexc("F0C987")


BISEAU_SOMBRE = hexc("8A6630")


CREME = PAL["creme"]


CREME_VAL = hexc("E8DCC0")


CREME_KJ = hexc("F2E6CC")


DOUX = PAL["creme_doux"]


ROUGE = PAL["rouge"]


SEP = hexc("2D181C")


KONOHA = hexc("F2A548")


FROID = hexc("6FA8D8")


BOIS_ECLAT = hexc("3A2418")


R_VERM = ["4E0C0B", "6E1511", "8E2018", "AE2C21", "C23A2C", "D2523E", "E0705A"]


def mix(a, b, t):
    a = np.array(hexc(a)[:3] if not isinstance(a, np.ndarray) else a[:3], float)
    b = np.array(hexc(b)[:3] if not isinstance(b, np.ndarray) else b[:3], float)
    return tuple(int(round(v)) for v in a * (1 - t) + b * t)


def desat(c, k=0.8):
    c = np.array(hexc(c)[:3], float)
    g = c.mean()
    return tuple(int(v) for v in c * k + g * (1 - k))


def _bord(m):
    """Bord intérieur d'un masque bool (les bords de l'image comptent comme du vide)."""
    P = np.pad(m, 1, constant_values=False)
    e = P[1:-1, 1:-1] & P[:-2, 1:-1] & P[2:, 1:-1] & P[1:-1, :-2] & P[1:-1, 2:]
    return m & ~e


def _dilate(m, n=1):
    for _ in range(n):
        d = m.copy()
        d[1:, :] |= m[:-1, :]; d[:-1, :] |= m[1:, :]; d[:, 1:] |= m[:, :-1]; d[:, :-1] |= m[:, 1:]
        m = d
    return m


def _ajouter(img, x0, y0, A, couleur, niveaux=4, tramage=0.16, sat=0.8):
    """Ajoute une lumière (carte A) quantifiée en crans ; jonctions tramées Bayer."""
    h, w = A.shape
    X0, Y0 = max(0, x0), max(0, y0)
    X1, Y1 = min(img.width, x0 + w), min(img.height, y0 + h)
    if X1 <= X0 or Y1 <= Y0:
        return
    A = A[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0]
    amax = float(A.max())
    if amax <= 1e-4:
        return
    q = kit.quantifier(A / amax, niveaux + 1, X0, Y0, tramage) / niveaux * amax
    col = np.array(desat(couleur, sat), float)
    arr = np.asarray(img.crop((X0, Y0, X1, Y1))).astype(float)
    arr[..., :3] = np.minimum(255, arr[..., :3] + col[None, None, :] * q[..., None])
    img.paste(Image.fromarray(arr.astype(np.uint8), "RGBA"), (X0, Y0))


def lumiere(img, cx, cy, r, couleur, force=0.2, ry=None, niveaux=4, expo=2.0, sat=0.8, tramage=0.16):
    ry = ry or r
    x0, y0 = int(cx - r), int(cy - ry)
    w, h = int(2 * r) + 2, int(2 * ry) + 2
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.sqrt(((xx + x0 - cx) / r) ** 2 + ((yy + y0 - cy) / ry) ** 2)
    A = np.clip(1 - d, 0, 1) ** expo * force
    _ajouter(img, x0, y0, A, couleur, niveaux, tramage, sat)


def assombrir(img, x0, y0, A):
    h, w = A.shape
    X0, Y0 = max(0, x0), max(0, y0)
    X1, Y1 = min(img.width, x0 + w), min(img.height, y0 + h)
    if X1 <= X0 or Y1 <= Y0:
        return
    A = A[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0]
    arr = np.asarray(img.crop((X0, Y0, X1, Y1))).astype(float)
    arr[..., :3] *= (1 - A[..., None])
    img.paste(Image.fromarray(arr.astype(np.uint8), "RGBA"), (X0, Y0))


def brume(bg, y, ep=12, force=0.06, seed=0, couleur=(120, 112, 160)):
    """Bande de brume tramée (Bayer) : sépare les plans."""
    W = bg.width
    y0 = int(y - 3 * ep)
    h = int(6 * ep)
    yy = np.arange(h)[:, None] + y0
    prof = np.exp(-((yy - y) / ep) ** 2)
    nz = kit.bruit(W, h, 70, seed, 2, etire=(5, 1))
    A = prof * (0.35 + 0.65 * nz) * force
    _ajouter(bg, 0, y0, A, couleur, niveaux=3, tramage=0.55, sat=1.0)


def _px_mix(img, x, y, c, a):
    if 0 <= x < img.width and 0 <= y < img.height:
        o = np.array(img.getpixel((x, y))[:3], float)
        n = o * (1 - a) + np.array(c[:3], float) * a
        img.putpixel((x, y), tuple(int(v) for v in n) + (255,))


def _px_set(img, x, y, c, a):
    if 0 <= x < img.width and 0 <= y < img.height:
        o = img.getpixel((x, y))
        if o[3] == 0:
            img.putpixel((x, y), tuple(int(v) for v in c[:3]) + (int(255 * a),))
        else:
            _px_mix(img, x, y, c, a)


def lq(img, x, y, w, h, ton="noir", reflet=True, seed=0, intensite=1.0):
    """Laque urushi : fond profond, dégradé vertical, reflet diagonal ; crans pleins, tramage
    aux seules jonctions, micro-grain."""
    if isinstance(ton, (list, tuple)) and not isinstance(ton, str):
        R = list(ton)
    else:
        R = R_LAQUE if ton == "noir" else R_LAQUE_ROUGE
    if w <= 0 or h <= 0:
        return
    n = len(R)
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    t = 1 - yy / max(1, h - 1)
    v = (0.40 + 0.16 * t) if ton == "rouge" else (0.12 + 0.16 * t)
    if reflet:
        d = (xx + yy * 0.9) / (w + h * 0.9)
        v += intensite * (0.20 * np.exp(-((d - 0.18) / 0.10) ** 2) + 0.12 * np.exp(-((d - 0.27) / 0.025) ** 2))
        v += intensite * 0.06 * np.exp(-((d - 0.80) / 0.12) ** 2)
    v += (kit.bruit(w, h, 14, seed, 2) - 0.5) * 0.04
    idx = kit.quantifier(v, n, x, y, tramage=0.18)
    p = kit._rampe_vers_rgba(R, idx)
    if reflet and h > 6 and w > 12:
        for i in range(2, int(w * 0.42)):
            p[1, i] = hexc(R[min(n - 1, int(idx[1, i]) + 1)])
    coller(img, p, x, y)


def liseret(img, x, y, w, h, terne=False):
    """Liseré or biseauté 1 GUI : clair en haut et à gauche, sombre en bas et à droite."""
    c1, c2, c3 = (BISEAU_CLAIR, BISEAU_SOMBRE, OR) if not terne else (hexc(R_OR[3]), hexc(R_OR[1]), hexc(R_OR[2]))
    rect(img, x, y, w, 1, c1)
    rect(img, x, y, 1, h, c1)
    rect(img, x, y + h - 1, w, 1, c2)
    rect(img, x + w - 1, y, 1, h, c2)
    rect(img, x, y + h - 1, 1, 1, c3)
    rect(img, x + w - 1, y, 1, 1, c3)


_KG10 = None


def kanagu10():
    """Ferrure d'angle 10x10 GUI en découpe de feuille, biseau or 2 tons, rivet central."""
    global _KG10
    if _KG10 is not None:
        return _KG10
    N = 10
    m = np.zeros((N, N), bool)
    for y in range(N):
        for x in range(N):
            if x >= y:
                if y <= 3 and x <= 7:
                    m[y, x] = True
                if x == 8 and 1 <= y <= 3:
                    m[y, x] = True
                if x == 9 and y == 2:
                    m[y, x] = True
    m[3, 6] = False
    m = m | m.T
    m[4, 4] = True
    p = np.zeros((N, N, 4), np.uint8)
    o = hexc("4A3418")
    p[m] = OR
    up = np.zeros_like(m); up[1:, :] = m[:-1, :]
    lf = np.zeros_like(m); lf[:, 1:] = m[:, :-1]
    dn = np.zeros_like(m); dn[:-1, :] = m[1:, :]
    rt = np.zeros_like(m); rt[:, :-1] = m[:, 1:]
    bord = m & ~(up & lf & dn & rt)
    p[m & (~dn | ~rt)] = BISEAU_SOMBRE
    p[m & (~up | ~lf)] = BISEAU_CLAIR
    p[bord & (~dn | ~rt) & ~(~up | ~lf)] = o
    for (yy, xx) in zip(*np.nonzero(m)):
        if yy == 1 and xx >= 1 and p[yy, xx, 0] == OR[0]:
            p[yy, xx] = hexc("E4B870")
        if xx == 1 and yy >= 1 and p[yy, xx, 0] == OR[0]:
            p[yy, xx] = hexc("E4B870")
    p[2, 2] = hexc("FFF2CC")
    p[3, 3] = hexc("6A4E24")
    _KG10 = p
    return p


def kanagus(img, x, y, w, h, deborde=1):
    spr = kanagu10()
    N = spr.shape[0]
    o = deborde
    for (px_, py_, fx, fy) in ((x - o, y - o, False, False), (x + w - N + o, y - o, True, False),
                               (x - o, y + h - N + o, False, True), (x + w - N + o, y + h - N + o, True, True)):
        a = spr
        if fx:
            a = a[:, ::-1]
        if fy:
            a = a[::-1, :]
        a = np.ascontiguousarray(a).copy()
        msk = a[..., 3] > 0
        sh = np.zeros_like(a)
        sh[1:, 1:][msk[:-1, :-1]] = (6, 3, 4, 130)
        coller(img, sh, px_, py_)
        if fx or fy:
            m2 = a[..., 3] > 0
            up = np.zeros_like(m2); up[1:, :] = m2[:-1, :]
            lf = np.zeros_like(m2); lf[:, 1:] = m2[:, :-1]
            dn = np.zeros_like(m2); dn[:-1, :] = m2[1:, :]
            rt = np.zeros_like(m2); rt[:, :-1] = m2[:, 1:]
            a[m2] = OR
            a[m2 & (~dn | ~rt)] = BISEAU_SOMBRE
            a[m2 & (~up | ~lf)] = BISEAU_CLAIR
            ry_, rx_ = (N - 3 if fy else 2), (N - 3 if fx else 2)
            a[ry_, rx_] = hexc("FFF2CC")
            a[min(N - 1, ry_ + 1), min(N - 1, rx_ + 1)] = hexc("6A4E24")
        coller(img, a, px_, py_)


def ferrures3(px, x, y, w, h, terne=False):
    """Petites ferrures d'angle 3x3 GUI (boutons, plaquettes) : équerre or + rivet."""
    c0, c1, c2 = (BISEAU_CLAIR, OR, hexc(R_OR[3])) if not terne else (hexc(R_OR[4]), hexc(R_OR[3]), hexc(R_OR[2]))
    for (a, b, sx, sy) in ((x, y, 1, 1), (x + w - 1, y, -1, 1), (x, y + h - 1, 1, -1), (x + w - 1, y + h - 1, -1, -1)):
        rect(px, a, b, 1, 1, c0)
        rect(px, a + sx, b, 1, 1, c1)
        rect(px, a + 2 * sx, b, 1, 1, c2)
        rect(px, a, b + sy, 1, 1, c1)
        rect(px, a, b + 2 * sy, 1, 1, c2)
        rect(px, a + sx, b + sy, 1, 1, c2)


def agemaki(img, cx, cy, couleur="rouge"):
    """Nœud agemaki 9x9 GUI : quatre boucles pleines en 3 tons, centre d'1 GUI."""
    if couleur == "or":
        a, b, c = hexc(R_OR[2]), hexc(R_OR[4]), hexc(R_OR[6])
        o = hexc("2E2210")
        centre = hexc("FFF2C8")
    else:
        a, b, c = hexc("6E1220"), hexc("A42230"), hexc("D04A4E")
        o = hexc("3A0810")
        centre = hexc(R_OR[6])
    N = 9
    m = np.zeros((N, N), bool)
    tone = np.zeros((N, N), int)
    yy, xx = np.mgrid[0:N, 0:N].astype(float)
    for (bx, by) in ((4, 1.9), (1.9, 4), (6.1, 4), (4, 6.1)):
        d = np.sqrt((xx - bx) ** 2 + (yy - by) ** 2)
        inside = d <= 2.05
        m |= inside
        lit = inside & ((xx - bx) + (yy - by) < -0.6)
        dark = inside & ((xx - bx) + (yy - by) > 1.0)
        tone[lit] = np.maximum(tone[lit], 2)
        tone[inside & ~lit & ~dark] = np.maximum(tone[inside & ~lit & ~dark], 1)
    p = np.zeros((N, N, 4), np.uint8)
    pal = {0: a, 1: b, 2: c}
    for j in range(N):
        for i in range(N):
            if m[j, i]:
                p[j, i] = pal[tone[j, i]]
    p[_bord(m)] = o
    for (j, i) in ((0, 4), (4, 0), (4, 8), (8, 4)):
        if m[j, i]:
            p[j, i] = a
    p[4, 4] = centre
    p[3, 4] = c
    coller(img, p, cx - 4, cy - 4)


def gland3(img, cx, y, long=10):
    """Gland de soie de 3 GUI de large sous un nœud : tête dorée, franges en 3 tons."""
    a, b, c = hexc("6E1220"), hexc("A42230"), hexc("D04A4E")
    rect(img, cx - 1, y, 3, 1, hexc(R_OR[6]))
    rect(img, cx - 1, y + 1, 3, 1, hexc(R_OR[4]))
    for k in range(long):
        rect(img, cx - 1, y + 2 + k, 1, 1, c if k % 3 == 0 else b)
        rect(img, cx, y + 2 + k, 1, 1, b if k % 3 != 1 else c)
        if k < long - 1:
            rect(img, cx + 1, y + 2 + k, 1, 1, a)
    rect(img, cx - 1, y + 4, 3, 1, hexc(R_OR[5]))
    rect(img, cx - 1, y + 5, 3, 1, hexc(R_OR[3]))


def corde_v(img, x, y0, y1, couleur="rouge", ep=2):
    """Cordelette torsadée strictement verticale (aucune marche)."""
    R = [hexc(c) for c in kit.R_CORDE[couleur]]
    for k, y in enumerate(range(int(y0), int(y1) + 1)):
        if 0 <= y < img.height:
            if ep == 1:
                rect(img, x, y, 1, 1, R[3] if k % 2 == 0 else R[2])
            else:
                rect(img, x, y, 1, 1, (R[3], R[2], R[2])[k % 3])
                rect(img, x + 1, y, 1, 1, (R[1], R[3], R[1])[k % 3])


def corde_molle(img, pts, mou=3, couleur="rouge", ep=2):
    """Cordelette torsadée tendue entre des points d'attache, avec un léger mou (parabole)
    entre chaque paire : marches régulières d'1 GUI."""
    R = [hexc(c) for c in kit.R_CORDE[couleur]]
    k = 0
    for (xa, ya), (xb, yb) in zip(pts[:-1], pts[1:]):
        L = xb - xa
        for x in range(int(xa), int(xb) + 1):
            t = (x - xa) / max(1, L)
            y = int(round(ya + (yb - ya) * t + mou * 4 * t * (1 - t)))
            if ep == 1:
                rect(img, x, y, 1, 1, (R[3], R[2], R[4], R[2])[k % 4])
                rect(img, x, y + 1, 1, 1, (8, 4, 6, 110))
            else:
                rect(img, x, y, 1, 1, (R[3], R[2], R[2])[k % 3])
                rect(img, x, y + 1, 1, 1, (R[1], R[3], R[1])[k % 3])
            k += 1


def anneau_or(img, cx, cy):
    kit.anneau_metal(img, cx, cy, 2)


def perche(px, x0, x1, y, bout="d"):
    """Perche de bambou laqué noir (5 GUI) qui entre par le bord de l'écran : nœuds
    irréguliers, embout de laiton biseauté du côté de la scène."""
    rows = ["050304", "4A2E35", "2A1A1F", "1A1013", "0F090B", "050304"]
    for k, c in enumerate(rows):
        rect(px, x0, y + k, x1 - x0, 1, hexc(c))
    rect(px, x0, y + 1, x1 - x0, 1, hexc("4A2E35"))
    rs = random.Random(x0 * 3 + 1)
    xn = x0 + 18
    while xn < x1 - 12:
        rect(px, xn, y + 1, 1, 4, hexc("6A444C"))
        rect(px, xn + 1, y + 1, 1, 4, hexc("0A0608"))
        xn += rs.choice((37, 43, 49, 41))
    cx_ = x1 - 6 if bout == "d" else x0
    lq(px, cx_, y - 1, 6, 7, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=cx_)
    liseret(px, cx_, y - 1, 6, 7)


def _masque(texte):
    """Masque bool d'un texte posé en pixel : ArcadePix x2 rogné à l'encre si le texte est ASCII (règle du 14/10 :
    plus de kanji décoratif), sinon glyphe unifont 16 (ancien chemin, gardé pour les maquettes d'archives)."""
    if all(ord(c) < 0x80 for c in texte):
        m = kit.arcade_masque(texte, 20)
        rows, cols = np.where(m.any(axis=1))[0], np.where(m.any(axis=0))[0]
        return m[rows[0]:rows[-1] + 1, cols[0]:cols[-1] + 1] if len(rows) else m
    return kit._glyphe_kanji_ligne(texte, 16, "unifont")


def kj_px(img, cx, y, texte, couleur, ombre=None, contour=None, ancre="c"):
    """Kanji de la police du jeu (unifont 16 px) posé sur la COUCHE PIXEL : 1 px de glyphe =
    1 GUI = 2x2 px écran, blit entier sans lissage. ombre : couleur d'ombre portée (1 GUI).
    Texte ASCII (chiffres, lettres) : ArcadePix x2 (14 GUI de haut) — c'est ce que posent les textures depuis que
    les kanji décoratifs sont retirés (18/10)."""
    g = _masque(texte)
    h, w = g.shape
    x = int(round(cx - w / 2)) if ancre == "c" else (int(round(cx - w)) if ancre == "d" else int(round(cx)))
    y = int(round(y))
    if contour is not None:
        d = _dilate(np.pad(g, 1), 1)
        p = np.zeros(d.shape + (4,), np.uint8)
        p[d] = hexc(contour)
        coller(img, p, x - 1, y - 1)
    if ombre is not None:
        p = np.zeros((h, w, 4), np.uint8)
        p[g] = hexc(ombre)
        coller(img, p, x + 1, y + 1)
    p = np.zeros((h, w, 4), np.uint8)
    p[g] = hexc(couleur)
    coller(img, p, x, y)
    return w


def hanko_px(img, cx, cy, kj, taille=20, ton=None, forme="carre", seed=0, encre=None, usure=0.10):
    """Sceau pixel : laque/vermillon en 4 tons (biseau clair en haut à gauche, sombre en bas à
    droite), kanji unifont 16 GUI en réserve couleur papier ; usure : quelques manques."""
    R = [hexc(c) for c in (ton or R_VERM)]
    n = len(R)
    T = int(taille)
    yy, xx = np.mgrid[0:T, 0:T].astype(float)
    c0 = (T - 1) / 2
    if forme == "rond":
        mk = (xx - c0) ** 2 + (yy - c0) ** 2 <= (T / 2 - 0.15) ** 2
    else:
        mk = np.ones((T, T), bool)
        for (a, b) in ((0, 0), (0, T - 1), (T - 1, 0), (T - 1, T - 1)):
            mk[a, b] = False
    nz = kit.bruit(T, T, 3, seed + 5, 2)
    v = 0.55 - 0.18 * (yy / T) + (nz - 0.5) * 0.25
    idx = np.clip((v * (n - 2)).astype(int) + 1, 1, n - 2)
    p = np.zeros((T, T, 4), np.uint8)
    pal = np.array(R, np.uint8)
    p[mk] = pal[idx[mk]]
    b = _bord(mk)
    lt = b & ((xx + yy) < T - 1)
    p[b] = R[0]
    p[lt] = R[n - 2]
    g = _masque(kj)
    gh, gw = g.shape
    gx, gy = int(round(c0 - gw / 2 + 0.5)), int(round(c0 - gh / 2 + 0.5))
    ink = np.zeros((T, T), bool)
    ink[max(0, gy):gy + gh, max(0, gx):gx + gw] = g[max(0, -gy):T - gy, max(0, -gx):T - gx]
    ink &= mk & ~b
    col = hexc(encre or CREME_KJ)
    p[ink] = col
    if sum(col[:3]) > 300:                       # réserve claire : ombre d'1 GUI sous le trait
        ombre = np.zeros_like(ink); ombre[1:, 1:] = ink[:-1, :-1]
        ombre &= mk & ~ink & ~b
        p[ombre] = R[0]
    rs = np.random.RandomState(seed)
    trous = mk & ~b & ~ink & (rs.rand(T, T) < usure * 0.35)
    p[trous] = R[min(n - 1, 2)]
    kit.ombre_portee(img, int(cx - T // 2), int(cy - T // 2), T, T, dx=1, dy=1, force=0.55, rayon=0, masque=mk)
    coller(img, p, int(cx - T // 2), int(cy - T // 2))


def panneau(img, x, y, w, h, seed=0, ton="noir", kanagu_=True, eclats=True):
    """Planche laquée : ombre portée (2/3 GUI), laque à reflet, micro-grain du bois sous la
    laque, éclats d'1 GUI aux coins, liseré or biseauté, gorge, filet rouge, kanagu 10x10."""
    kit.ombre_portee(img, x, y, w, h, dx=2, dy=3, force=0.6, rayon=2)
    rect(img, x - 1, y - 1, w + 2, h + 2, PAL["fond"])
    lq(img, x + 1, y + 1, w - 2, h - 2, ton=ton, seed=seed)
    iw, ih = w - 2, h - 2
    wood = Image.new("RGBA", (iw, ih), (0, 0, 0, 0))
    kit.bois(wood, 0, 0, iw, ih, "sombre", sens="h", seed=seed + 40, noeuds=0, biseau=False)
    reg = np.asarray(img.crop((x + 1, y + 1, x + 1 + iw, y + 1 + ih))).astype(float)
    wa = np.asarray(wood).astype(float)
    lum = wa[..., :3].mean(axis=2, keepdims=True)
    reg[..., :3] += (lum - lum.mean()) * (0.12 if ton == "noir" else 0.08)
    img.paste(Image.fromarray(np.clip(reg, 0, 255).astype(np.uint8), "RGBA"), (x + 1, y + 1))
    liseret(img, x, y, w, h)
    gorge = hexc(R_LAQUE[0]) if ton == "noir" else hexc(R_LAQUE_ROUGE[1])
    rect(img, x + 1, y + 1, w - 2, 1, gorge)
    rect(img, x + 1, y + 1, 1, h - 2, gorge)
    rect(img, x + 1, y + h - 2, w - 2, 1, gorge)
    rect(img, x + w - 2, y + 1, 1, h - 2, gorge)
    fc = hexc("6A1420") if ton == "noir" else hexc(R_OR[3])
    rect(img, x + 3, y + 3, w - 6, 1, fc); rect(img, x + 3, y + h - 4, w - 6, 1, fc)
    rect(img, x + 3, y + 3, 1, h - 6, fc); rect(img, x + w - 4, y + 3, 1, h - 6, fc)
    if eclats and ton == "noir" and w > 40 and h > 40:
        rs = random.Random(seed * 7 + 3)
        for (cx_, cy_, sx, sy) in ((x + 5, y + 5, 1, 1), (x + w - 6, y + 5, -1, 1),
                                    (x + 5, y + h - 6, 1, -1), (x + w - 6, y + h - 6, -1, -1)):
            for _ in range(rs.choice((1, 2))):
                ex = cx_ + sx * rs.randint(9, 15)
                ey = cy_ + sy * rs.randint(0, 2)
                if rs.random() < 0.5:
                    ex, ey = cx_ + sx * rs.randint(0, 2), cy_ + sy * rs.randint(9, 15)
                rect(img, ex, ey, 1, 1, BOIS_ECLAT)
    if kanagu_:
        kanagus(img, x, y, w, h)


def plaquette(px, x, y, w, h, kind="noir", seed=0, terne=False):
    """Petite plaquette laquée bordée d'or."""
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=1, force=0.55, rayon=0)
    if kind == "rouge":
        lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=seed)
    elif kind == "or":
        lq(px, x + 1, y + 1, w - 2, h - 2, ["5A4220", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=seed)
    else:
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=seed, intensite=1.3)
    liseret(px, x, y, w, h, terne=terne)


def losange_or(px, x, y):
    """Petit losange doré de 3 GUI (puce)."""
    for (dx, dy, c) in ((1, 0, R_OR[7]), (0, 1, R_OR[5]), (1, 1, R_OR[6]), (2, 1, R_OR[4]), (1, 2, R_OR[3])):
        rect(px, x + dx, y + dy, 1, 1, hexc(c))


def largeur_bouton(libelle, kj=True):
    return int(math.ceil(kit.arcade_largeur(libelle, 10) / 2)) + (20 if kj else 0) + 14


def mat_bambou(px, x, y0, y1, seed=0):
    """Mât de bambou laqué (4 GUI) : nœuds irréguliers (bourrelet clair + gorge), aucune
    graduation ; embout doré."""
    for k, c in enumerate(("050304", "3A2428", "5A3A40", "2A1A1F", "140C0F", "050304")):
        rect(px, x - 1 + k, y0, 1, y1 - y0, hexc(c))
    rs = random.Random(seed)
    y = y0 + rs.randint(14, 30)
    while y < y1 - 14:
        rect(px, x, y, 4, 1, hexc("7A5058"))
        rect(px, x, y + 1, 4, 1, hexc("0A0608"))
        rect(px, x - 1, y, 1, 2, hexc("050304")); rect(px, x + 4, y, 1, 2, hexc("050304"))
        y += rs.choice((47, 61, 53, 71, 58))
    lq(px, x - 1, y0 - 6, 6, 7, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=x)
    liseret(px, x - 1, y0 - 6, 6, 7)
    rect(px, x + 1, y0 - 8, 2, 2, hexc(R_OR[5])); rect(px, x + 1, y0 - 9, 1, 1, hexc(R_OR[7]))
    # pied du mât sur le plancher
    rect(px, x - 3, y1, 10, 2, hexc("050304"))
    lq(px, x - 2, y1 - 3, 8, 3, ["4A3518", "6A4E24", "8E6A30", "B08442"], seed=x + 1, reflet=False)



Y_PERCHE = 56


def bandeau_titre(m, x, y, w, titre, kj=None):
    """Bandeau de laque rouge encastré sous l'arête haute : titre ArcadePix x1 or clair ombré,
    kanji pixel 16 GUI or à droite."""
    px = m.px
    bx, by, bw, bh = x + 5, y + 5, w - 10, 22
    lq(px, bx, by, bw, bh, "rouge", seed=x)
    rect(px, bx, by + bh, bw, 1, hexc(R_OR[4]))
    rect(px, bx, by + bh + 1, bw, 1, hexc("120608"))
    rect(px, bx, by, bw, 1, hexc(R_LAQUE_ROUGE[2]))
    arcade(px, x + 14, by + 8, titre, 10, OR_CLAIR, ombre=True)
    if kj:
        kj_px(px, x + w - 20, by + 3, kj, R_OR[6], ombre=hexc("3A0A12"))


def planche(m, x, y, w, h, titre=None, gland="bd", seed=0, opacite=0.92, ancrages=None, kanji_titre=None):
    """Planche laquée pendue à sa perche par deux cordelettes rouges courtes ; nœud agemaki
    + gland sous un coin bas ; titre en bandeau rouge."""
    px = m.px
    ax = ancrages or (x + 34, x + w - 36)
    for a in ax:
        corde_v(px, a, Y_PERCHE + 5, y + 1, "rouge", ep=2)
    for a in ax:
        rect(px, a - 1, Y_PERCHE, 4, 1, hexc("A42230"))
        rect(px, a - 1, Y_PERCHE + 2, 4, 1, hexc("A42230"))
        rect(px, a - 1, Y_PERCHE + 4, 4, 1, hexc("7A1322"))
    lay = Image.new("RGBA", px.size, (0, 0, 0, 0))
    panneau(lay, x, y, w, h, seed=seed)
    a = np.asarray(lay).copy()
    a[..., 3] = (a[..., 3].astype(float) * opacite).astype(np.uint8)
    px.alpha_composite(Image.fromarray(a, "RGBA"))
    for a_ in ax:
        anneau_or(px, a_ + 1, y + 1)
    if gland:
        gx = x + w - 20 if gland == "bd" else x + 20
        agemaki(px, gx, y + h + 4)
        gland3(px, gx, y + h + 9, 11)
    if titre:
        bandeau_titre(m, x, y, w, titre, kanji_titre)


def bouton(m, x, y, libelle, etat="normal", kj=None, ton="noir", h=20, w=None):
    """Bouton-plaque : laque à reflet, liseré or biseauté, ferrures 3x3, (kanji pixel 16 GUI à
    gauche : plus utilisé depuis le 18/10) libellé ArcadePix x0,5. etat : normal | survol | actif | inactif | neutre.
    w : largeur réservée (position fixe quel que soit le libellé). Renvoie (x, y, w, h)."""
    lw = kit.arcade_largeur(libelle, 10) / 2
    w = w or int(math.ceil(lw)) + (20 if kj else 0) + 14
    px = m.px
    inactif = etat == "inactif"
    neutre = etat == "neutre"
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.6 if not inactif else 0.35, rayon=1)
    rect(px, x - 1, y - 1, w + 2, h + 2, PAL["fond"])
    if ton == "rouge" and not inactif:
        lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=w, intensite=1.2)
    elif ton == "rouge":
        lq(px, x + 1, y + 1, w - 2, h - 2, ["1E0A0D", "260C10", "2E0E13", "361117", "3E141A", "46171E"], seed=w,
           intensite=0.6)
    else:
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=w,
           intensite={"survol": 1.8, "normal": 1.1, "neutre": 0.8}.get(etat, 0.4))
    liseret(px, x, y, w, h, terne=inactif or neutre)
    ferrures3(px, x, y, w, h, terne=inactif or neutre)
    tx = x + 7 if kj else x + (w - lw) / 2      # sans kanji : libellé centré (comme le mod)
    if kj:
        kc = {"inactif": hexc("4A3C38"), "neutre": hexc(R_OR[3])}.get(etat, OR_CLAIR if ton == "rouge" else OR)
        kj_px(px, tx + 8, y + (h - 16) // 2, kj, kc, ombre=hexc("140608") if not inactif else None)
        tx += 20
    tc = CREME if (etat in ("actif", "survol") or (ton == "rouge" and not inactif)) else hexc("D8CCB0")
    if neutre:
        tc = hexc("B8A47E")
    if inactif:
        tc = hexc("6A5C52")
    m.arcade_fin(tx, y + h / 2 - 1.75, libelle, 0.5, tc, ombre=not inactif)
    return (x, y, w, h)


def cabochon(m, x, ymil, t, h=10):
    """Touche dessinée : cabochon laqué de 10 GUI (coins abattus, liseré or éteint), lettre
    ArcadePix x0,5 or clair. Renvoie la largeur (GUI)."""
    px = m.px
    tw = kit.arcade_largeur(t, 10) / 2
    w = int(math.ceil(tw)) + 7
    y = int(round(ymil - h / 2))
    x = int(round(x))
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
    lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=x, intensite=1.2)
    rect(px, x + 1, y, w - 2, 1, hexc(R_OR[4])); rect(px, x + 1, y + h - 1, w - 2, 1, hexc(R_OR[2]))
    rect(px, x, y + 1, 1, h - 2, hexc(R_OR[3])); rect(px, x + w - 1, y + 1, 1, h - 2, hexc(R_OR[2]))
    m.arcade_fin(x + w / 2, ymil - 1.75, t, 0.5, OR_CLAIR, ancre="c")
    return w



# ═════════════════════════════ maquette personnage ═════════════════════════════
LUCIOLE = hexc("E2F07A")


LUCIOLE_COEUR = hexc("FCFFD8")


VILLAGES = [
    # clé, nom long, pays, couleur du chakra (villageColor), kanji
    ("konoha", "Konohagakure", "Pays du Feu", hexc("F2A548"), "火"),
    ("suna", "Sunagakure", "Pays du Vent", hexc("E8C88A"), "風"),
    ("kiri", "Kirigakure", "Pays de l'Eau", hexc("9ED8EA"), "水"),
    ("kumo", "Kumogakure", "Pays de la Foudre", hexc("F4EC8C"), "雷"),
    ("iwa", "Iwagakure", "Pays de la Terre", hexc("D39B6A"), "土"),
    ("ame", "Amegakure", "Pays de la Pluie", hexc("A9B8DA"), "雨"),
]


VCOL = {k: c for (k, _, _, c, _) in VILLAGES}


CIELS = {
    "konoha": ["07060F", "0B0916", "100C1D", "161024", "1D132A", "27172F", "341B31", "472232"],
    "suna":   ["05060E", "090A16", "0F0F1D", "171522", "221B26", "302328", "442E2C", "5C3E30"],
}


EAU = {
    "konoha": ["07060C", "0B0911", "100C17", "15101D", "1B1424"],
    "suna":   ["07060A", "0B090D", "100D11", "171216", "1F171A"],
}


Y_HOR = 316          # rive lointaine (ligne d'eau)


LUNE = (612, 116, 14)


def ciel(bg, village="konoha", seed=1):
    """Ciel de nuit en 8 crans pleins, jonctions ondulées tramées ; voie lactée diagonale très
    discrète (lueur + poussière d'étoiles), lueur chaude du village à l'horizon."""
    W, H = bg.size
    R = [hexc(c) for c in CIELS[village]]
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    v = np.clip(yy / Y_HOR, 0, 1) ** 1.15
    v += (kit.bruit(W, H, 110, seed, 3, etire=(3, 1)) - 0.5) * 0.10
    idx = kit.quantifier(v, len(R), 0, 0, tramage=0.12)
    pal = np.array(R, np.uint8)
    bg.paste(Image.fromarray(pal[idx], "RGBA"), (0, 0))
    # voie lactée : bande diagonale, lueur à peine visible
    d = (xx * 0.42 - yy + 40) / 60.0
    band = np.exp(-d ** 2) * (yy < Y_HOR - 20) * (0.6 + 0.4 * kit.bruit(W, H, 40, seed + 9, 2))
    _ajouter(bg, 0, 0, band * 0.10, (150, 130, 190), niveaux=3, tramage=0.5, sat=1.0)


def etoiles(bg, village="konoha", seed=2, zones=()):
    """Étoiles RÉPARTIES sur tout le ciel (échantillonnage à distance minimale), plus denses en haut
    et dans la voie lactée ; 3 tailles : point terne, point vif, petite croix scintillante."""
    rs = random.Random(seed)
    pts = []
    essais = 0
    while len(pts) < 230 and essais < 40000:
        essais += 1
        x, y = rs.uniform(2, 958), rs.uniform(2, Y_HOR - 26)
        p_haut = 1.0 - 0.75 * (y / Y_HOR)
        d = (x * 0.42 - y + 40) / 60.0
        p_band = 0.5 * math.exp(-d * d)
        if rs.random() > min(1.0, p_haut * 0.75 + p_band):
            continue
        if any(a <= x <= c and b <= y <= e for (a, b, c, e) in zones):
            continue
        if any(abs(px - x) < 7 and abs(py - y) < 7 for (px, py) in pts[-60:]):
            continue
        pts.append((x, y))
    for k, (x, y) in enumerate(pts):
        X, Y = int(x), int(y)
        r = rs.random()
        if r < 0.62:
            _px_mix(bg, X, Y, hexc("B8B4D0"), rs.uniform(0.22, 0.45))
        elif r < 0.94:
            _px_mix(bg, X, Y, hexc("E8E6F4"), rs.uniform(0.55, 0.85))
        else:                                   # croix : scintille en jeu (2 images)
            _px_mix(bg, X, Y, hexc("FFFFFF"), 0.95)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                _px_mix(bg, X + dx, Y + dy, hexc("C8C4E8"), 0.42)
    return pts


def lune(m, bg, village="konoha"):
    """Lune pâle (disque pixel 4 tons, mers sombres, liseré clair côté source) + halo en 3 ovales nets,
    traversée par deux fils de nuage tramés."""
    cx, cy, r = LUNE
    R = [hexc(c) for c in ("8E8AA0", "B2AEC2", "D2CEDC", "ECE8F0", "FFFDF6")]
    T = 2 * r + 1
    yy, xx = np.mgrid[0:T, 0:T].astype(float) - r
    d = np.sqrt(xx ** 2 + yy ** 2)
    mk = d <= r + 0.2
    v = 0.78 - 0.30 * ((xx + yy) / (2 * r)) + (kit.bruit(T, T, 5, 3, 2) - 0.5) * 0.25
    mers = kit.bruit(T, T, 7, 11, 2) < 0.33
    v[mers] -= 0.22
    idx = kit.quantifier(np.clip(v, 0, 1), len(R), cx, cy, tramage=0.3)
    p = np.zeros((T, T, 4), np.uint8)
    p[mk] = np.array(R, np.uint8)[idx[mk]]
    b = _bord(mk)
    p[b & ((xx + yy) < 0)] = R[4]
    p[b & ((xx + yy) >= 0)] = R[1]
    halo_px(m, cx, cy, r * 4.2, "B8B0E0", force=0.10, niveaux=3)
    halo_px(m, cx, cy, r * 1.9, "E8E0FF", force=0.08, niveaux=2)
    coller(bg, p, cx - r, cy - r)
    # deux fils de nuage effilés qui passent devant (bord éclairé par la lune)
    rs = random.Random(31)
    for (y0, x0, x1, ep) in ((cy + 8, cx - 120, cx + 10, 3), (cy + 22, cx - 40, cx + 120, 2), (cy - 24, cx + 30, cx + 150, 2)):
        for x in range(x0, x1):
            t = (x - x0) / (x1 - x0)
            k = math.sin(math.pi * t) ** 0.8
            e = max(1, int(round(ep * k + 0.3 * math.sin(x * 0.21))))
            yo = int(round(1.2 * math.sin(x * 0.05 + y0)))
            for j in range(e):
                y = y0 + yo + j
                if (x - cx) ** 2 + (y - cy) ** 2 < (r + 5) ** 2:
                    continue
                c = hexc("4A3E5A") if j == 0 else hexc("231A2E")
                a = 0.45 * k
                if kit.bayer(1, 1, x, y)[0, 0] < 0.25 + 0.75 * k:
                    _px_mix(bg, x, y, c, a)


def crete(W, base, amp, cellule, seed):
    n = kit.bruit(W, 1, cellule, seed, 3)[0]
    return base - amp * n


MONTAGNES = {"konoha": [("2A1A30", "34203A", 252, 46, 90), ("1A1122", "22152A", 278, 30, 60)],
             "suna": [("3A2830", "46303A", 266, 22, 160), ("2A1D24", "33242A", 288, 12, 90)]}


def montagnes(bg, village="konoha", seed=4):
    """Deux crêtes de montagnes, la plus lointaine plus claire (perspective atmosphérique)."""
    W = bg.width
    tons = MONTAGNES[village]
    for k, (c1, c2, base, amp, cel) in enumerate(tons):
        h = crete(W, base, amp, cel, seed + k * 7)
        for x in range(W):
            top = int(h[x])
            for y in range(top, Y_HOR):
                rect(bg, x, y, 1, 1, hexc(c2) if y - top < 2 else hexc(c1))


def _toit(bg, x, y, w, h, coul, rim, eclat=None):
    """Toit japonais en silhouette : pente, rive relevée aux deux bouts, faîtage."""
    for j in range(h):
        t = j / max(1, h - 1)
        half = int(round(w / 2 * (0.35 + 0.65 * t)))
        cx = x + w // 2
        rect(bg, cx - half, y + j, 2 * half, 1, coul)
        if j == 0:
            rect(bg, cx - half, y, 2 * half, 1, rim)
    # rives relevées
    rect(bg, x - 1, y + h - 2, 2, 1, coul)
    rect(bg, x + w - 1, y + h - 2, 2, 1, coul)
    rect(bg, x - 2, y + h - 3, 1, 1, rim)
    rect(bg, x + w + 1, y + h - 3, 1, 1, rim)
    if eclat:
        rect(bg, x + 2, y + 1, 1, 1, eclat)


def village_konoha(m, bg, seed=7):
    """Konoha au loin, la nuit : falaise des Hokage (quatre visages taillés, arbres au sommet), toits
    serrés aux rives relevées, tour ronde du Hokage (laque sombre, 火 éteint), fenêtres allumées à la
    couleur du village ; lueur orangée au-dessus des toits."""
    rs = random.Random(seed)
    K = VCOL["konoha"]
    nuit, nuit2, rim = hexc("140C16"), hexc("1B111C"), hexc("3A2430")
    # falaise
    x0, x1, top = 548, 700, 238
    hcr = crete(960, 0, 6, 9, seed)
    for x in range(x0, x1):
        t = (x - x0) / (x1 - x0)
        y_t = int(top + hcr[x] + 10 * max(0, (t - 0.86) / 0.14) ** 1.4 + 6 * max(0, (0.06 - t) / 0.06))
        for y in range(y_t, Y_HOR):
            c = nuit2 if y - y_t > 1 else rim
            rect(bg, x, y, 1, 1, c)
        # arbres sur le sommet
        if rs.random() < 0.55:
            hh = rs.randint(2, 5)
            rect(bg, x, y_t - hh, 1, hh, hexc("0E0910"))
    # quatre visages taillés, à peine sortis de la roche : front éclairé par la lune, orbites sombres
    for i, fx in enumerate((570, 602, 634, 666)):
        fy = top + 16 + (i % 2)
        for j in range(22):
            half = int(round(9 * math.sqrt(max(0, 1 - ((j - 10) / 12.5) ** 2))))
            rect(bg, fx - half, fy + j, 2 * half, 1, hexc("1F1420"))
            rect(bg, fx - half, fy + j, 1, 1, hexc("2A1C29"))
        rect(bg, fx - 7, fy, 14, 2, hexc("271A26"))
        rect(bg, fx - 6, fy + 1, 5, 1, hexc("33222F"))
        rect(bg, fx - 5, fy + 8, 3, 1, hexc("0E080D")); rect(bg, fx + 2, fy + 8, 3, 1, hexc("0E080D"))
        rect(bg, fx - 1, fy + 11, 1, 3, hexc("150D14"))
        rect(bg, fx - 3, fy + 16, 6, 1, hexc("120B11"))
    # tour du Hokage (ronde, toit conique), à gauche du personnage
    tx, tw, ttop = 330, 36, 262
    for y in range(ttop, Y_HOR):
        t = (y - ttop) / (Y_HOR - ttop)
        c = mix("2A0E14", "1A080C", t)
        rect(bg, tx, y, tw, 1, c)
        rect(bg, tx, y, 1, 1, hexc("4A1820"))
        rect(bg, tx + tw - 1, y, 1, 1, hexc("12060A"))
    _toit(bg, tx - 5, ttop - 10, tw + 10, 10, hexc("120A10"), rim)
    rect(bg, tx + tw // 2 - 1, ttop - 14, 2, 4, hexc("120A10"))
    for k in range(5):                                     # anneau de fenêtres
        wx = tx + 4 + k * 7
        _px_set(bg, wx, ttop + 26, mix(K, (255, 230, 190), 0.3), 0.85)
        _px_set(bg, wx + 1, ttop + 26, K, 0.7)
    # toits du village, trois rangs (le plus proche plus sombre)
    rangs = [(282, 296, 8, 6, "1C121B"), (290, 306, 11, 8, "160E16"), (300, 316, 13, 9, "110A12")]
    fen = []
    for (ya, yb, wmin, hmin, c) in rangs:
        x = 250 + rs.randint(0, 10)
        while x < 712:
            w = rs.randint(wmin, wmin + 12)
            if not (tx - 6 < x + w and x < tx + tw + 6) or ya > 296:
                y = rs.randint(ya, yb - hmin)
                h = rs.randint(hmin, hmin + 4)
                _toit(bg, x, y, w, 4, hexc(c), rim)
                rect(bg, x + 1, y + 4, w - 2, Y_HOR - y - 4, hexc(c))
                for _ in range(rs.randint(0, 2)):
                    fen.append((rs.randint(x + 2, x + w - 3), rs.randint(y + 5, min(Y_HOR - 2, y + 5 + h))))
            x += w + rs.randint(2, 7)
    for (fx, fy) in fen:
        c = mix(K, (255, 236, 200), rs.uniform(0.1, 0.45))
        _px_set(bg, fx, fy, c, rs.uniform(0.6, 0.95))
        if rs.random() < 0.3:
            _px_set(bg, fx + 1, fy, K, 0.6)
    # lueur du village sur le ciel bas et la falaise
    lumiere(bg, 480, Y_HOR - 6, 250, K, force=0.10, ry=46, niveaux=4, tramage=0.35)
    return fen


def village_suna(m, bg, seed=7):
    """Suna au loin, la nuit : dunes aux crêtes lunaires, falaises d'enceinte, dômes de terre crue éclairés
    par la lune en haut à gauche, fenêtres rondes couleur sable, grand dôme du Kazekage (風)."""
    rs = random.Random(seed)
    K = VCOL["suna"]
    # dunes (deux plans), crêtes éclairées
    for (base, amp, cel, c1, c2, cr) in ((286, 14, 120, "2E2026", "382830", "5E4644"),
                                        (300, 9, 70, "241A1F", "2C2026", "4E3A38")):
        h = crete(960, base, amp, cel, seed + base)
        for x in range(960):
            top = int(h[x])
            for y in range(top, Y_HOR):
                rect(bg, x, y, 1, 1, hexc(cr) if y == top else (hexc(c2) if y - top < 4 else hexc(c1)))
    # falaises d'enceinte (deux masses de roche qui encadrent le village)
    for (xa, xb, top) in ((262, 318, 244), (642, 708, 238)):
        hh = crete(960, 0, 5, 7, seed + xa)
        for x in range(xa, xb):
            t = (x - xa) / (xb - xa)
            yt = int(top + hh[x] + 3 * abs(t - 0.5) * 2 + 34 * max(0.0, 0.22 - min(t, 1 - t)) / 0.22)
            for y in range(yt, Y_HOR):
                c = "4A3632" if y - yt < 2 else ("2A1C20" if (x + y) % 9 else "251A1E")
                if (x - xa) < 2:
                    c = "3A2A2A"
                rect(bg, x, y, 1, 1, hexc(c))
    fen = []

    def dome(cx, r, base, corps="2E2024", lum="6A5048", haut=7):
        for j in range(r + 1):
            half = int(round(r * math.sqrt(max(0, 1 - ((r - j) / r) ** 2))))
            y = base - haut - (r - j)
            rect(bg, cx - half, y, 2 * half, 1, hexc(corps))
            if half:
                rect(bg, cx - half, y, max(1, half // 2), 1, hexc(lum))     # lune en haut à gauche
                rect(bg, cx + half - 1, y, 1, 1, hexc("1A1216"))
        rect(bg, cx - r, base - haut, 2 * r, haut, hexc(corps))
        rect(bg, cx - r, base - haut, 1, haut, hexc(lum))
        rect(bg, cx + r - 1, base - haut, 1, haut, hexc("1A1216"))
        for _ in range(rs.randint(1, 3)):
            fen.append((rs.randint(cx - r + 3, cx + r - 4), base - rs.randint(2, haut - 1)))

    for (cx, r, b) in ((338, 12, 312), (366, 16, 314), (400, 10, 310), (430, 14, 314), (530, 12, 312),
                       (566, 18, 315), (610, 11, 311), (300, 9, 310), (680, 10, 312), (455, 9, 309)):
        dome(cx, r, b)
    # dôme du Kazekage : le plus grand, 風 qui luit
    dome(592, 26, 300, corps="342428", lum="7A5C50", haut=18)
    halo_px(m, 592, 270, 18, K, force=0.08, niveaux=2)
    for k in range(5):
        _px_set(bg, 574 + k * 9, 292, mix(K, (255, 240, 210), 0.35), 0.95)
    for (fx, fy) in fen:
        _px_set(bg, fx, fy, mix(K, (255, 236, 200), rs.uniform(0.1, 0.4)), rs.uniform(0.7, 1.0))
        if rs.random() < 0.4:
            _px_set(bg, fx + 1, fy, K, 0.6)
    lumiere(bg, 480, Y_HOR - 6, 250, K, force=0.10, ry=44, niveaux=4, tramage=0.35)
    return fen


def eau(m, bg, village="konoha", seed=5):
    """Étang au premier plan : eau noire en 5 crans, reflet inversé et ridé du ciel bas et du village
    (fenêtres qui s'étirent), colonne de reflet de la lune en tirets, rides claires plus serrées vers
    le spectateur, liseré de brume sur la rive."""
    W, H = bg.size
    arr = np.asarray(bg).astype(float).copy()
    R = np.array([hexc(c)[:3] for c in EAU[village]], float)
    ys = np.arange(Y_HOR, H)
    t = (ys - Y_HOR) / (H - Y_HOR)
    v = 0.85 - 0.75 * t ** 0.7
    base = np.zeros((len(ys), W, 3))
    q = kit.quantifier(np.repeat(v[:, None], W, 1) + (kit.bruit(W, len(ys), 60, seed, 2, etire=(6, 1)) - 0.5) * 0.12,
                       len(R), 0, Y_HOR, tramage=0.3)
    base = R[q]
    # reflet inversé (rides : décalage horizontal par rangée), fondu sur 90 GUI
    L = 96
    src = arr[Y_HOR - L:Y_HOR, :, :3][::-1]
    rs = np.random.RandomState(seed)
    for j in range(min(L, len(ys))):
        dx = int(round(math.sin(j * 0.9 + rs.rand() * 0.6) * (0.6 + j * 0.05)))
        row = np.roll(src[j], dx, axis=0)
        k = 0.62 * (1 - j / L) ** 1.3
        if j % 3 == 2:
            k *= 0.55                       # coupure de ride
        base[j] = base[j] * (1 - k) + row * 0.55 * k + base[j] * 0.0
    arr[Y_HOR:H, :, :3] = base
    arr[Y_HOR:H, :, 3] = 255
    bg.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA"), (0, 0))
    # rive : un liseré sombre puis une ligne de brume claire
    rect(bg, 0, Y_HOR, W, 1, hexc("08060A"))
    brume(bg, Y_HOR - 4, 7, 0.10, seed + 1, couleur=(150, 128, 170))
    brume(bg, Y_HOR + 10, 9, 0.07, seed + 2, couleur=(120, 110, 160))
    # colonne de reflet de la lune
    cx = LUNE[0]
    rs2 = random.Random(seed)
    y = Y_HOR + 3
    while y < 470:
        prog = (y - Y_HOR) / 160
        w = int(rs2.uniform(2, 7) * (1 + prog * 1.6))
        x = int(cx + rs2.uniform(-3, 3) * (1 + prog) - w / 2)
        a = 0.55 * (1 - min(1, prog * 0.9))
        for i in range(w):
            _px_mix(bg, x + i, y, hexc("C8C2E0"), a * (0.6 if i in (0, w - 1) else 1))
        y += rs2.choice((2, 3, 3, 4)) + int(prog * 3)
    # rides claires (lune) : tirets horizontaux, plus longs et plus espacés vers le bas
    for _ in range(260):
        yy = int(Y_HOR + 4 + (H - Y_HOR - 4) * rs2.random() ** 1.3)
        prog = (yy - Y_HOR) / (H - Y_HOR)
        w = int(rs2.uniform(2, 6) * (1 + prog * 3))
        xx = int(rs2.uniform(0, W))
        dist = abs(xx - cx) / 480
        a = (0.10 + 0.16 * (1 - dist)) * (1 - 0.45 * prog)
        for i in range(w):
            _px_mix(bg, xx + i, yy, hexc("8E86B0"), a)


def pierre(bg, cx, cy, rx, ry, ep=4, lumiere_c=None, seed=0, mousse=True):
    """Pierre plate de gué (tobi-ishi) : dessus elliptique en 5 tons (lune en haut à gauche, contre-jour
    chaud au bord du fond), tranche sombre, anneau de ride clair cassé dans l'eau, reflet sombre."""
    rs = random.Random(seed)
    R = [hexc(c) for c in ("16121A", "221C26", "2E2632", "3C3240", "4E4252", "625466")]
    W, H = int(2 * rx + 6), int(2 * ry + ep + 8)
    x0, y0 = int(cx - rx - 3), int(cy - ry - 3)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    X = xx + x0 + 0.5 - cx
    Y = yy + y0 + 0.5 - cy
    nz = kit.bruit(W, H, 4, seed, 2)
    irr = 1 + (nz - 0.5) * 0.10
    top = (X / rx) ** 2 + (Y / ry) ** 2 <= irr ** 2
    flanc = ((X / rx) ** 2 + ((Y - ep) / ry) ** 2 <= irr ** 2) & ~top & (Y > 0)
    # anneau de ride
    rr = (X / (rx + 4)) ** 2 + ((Y - ep) / (ry + 2.2)) ** 2
    ride = (rr <= 1.0) & (rr >= 0.78) & ~top & ~flanc & (Y > -ry * 0.2)
    p = np.zeros((H, W, 4), np.uint8)
    v = 0.55 - 0.25 * (X / rx + Y / ry) / 2 + (nz - 0.5) * 0.35
    idx = kit.quantifier(np.clip(v, 0, 1), len(R), x0, y0, tramage=0.4)
    p[top] = np.array(R, np.uint8)[idx[top]]
    b = _bord(top)
    p[b & (X + Y < 0)] = R[5]
    p[b & (X + Y >= 0)] = R[1]
    p[flanc] = R[0]
    p[flanc & (Y > ep + ry * 0.6)] = hexc("0C090E")
    if lumiere_c is not None:                    # contre-jour au bord du fond (couleur du village)
        rim = b & (Y < -ry * 0.45)
        c = np.array(mix(lumiere_c, R[4], 0.45))
        p[rim, :3] = c
        p[rim, 3] = 255
    keep = np.asarray(bg.crop((x0, y0, x0 + W, y0 + H))).copy()
    cols = keep[..., :3].astype(float)
    rm = ride & (rs.random() >= 0)               # ride : éclaircit l'eau, cassée (tramage)
    br = kit.bayer(H, W, x0, y0) < 0.55
    cols[rm & br] = np.minimum(255, cols[rm & br] * 1.0 + np.array([40, 36, 56]))
    keep[..., :3] = cols.astype(np.uint8)
    keep[top | flanc] = p[top | flanc]
    bg.paste(Image.fromarray(keep, "RGBA"), (x0, y0))
    if mousse:                                    # 2-3 touffes de mousse sombre sur le dessus
        for _ in range(rs.randint(1, 3)):
            a = rs.uniform(0.2 * math.pi, 0.8 * math.pi)
            mx, my = int(cx + math.cos(a) * rx * 0.75), int(cy + math.sin(a) * ry * 0.55)
            rect(bg, mx, my, 2, 1, hexc("1C2420"))
            rect(bg, mx + 1, my - 1, 1, 1, hexc("2A3628"))


def lotus(bg, cx, cy, r, seed=0, fleur=False):
    """Feuille de lotus flottante (ellipse sarcelle sombre, encoche, nervure), parfois une fleur pâle."""
    ry = max(2, r * 0.36)
    for j in range(-int(ry), int(ry) + 1):
        half = int(round(r * math.sqrt(max(0, 1 - (j / ry) ** 2))))
        for i in range(-half, half + 1):
            if j < 0 and abs(i) <= max(1, half // 6) and i >= 0:
                continue                         # encoche
            c = "16261E" if j >= 0 else "1E3226"
            if j == -int(ry) or abs(i) == half:
                c = "2E4A34"
            rect(bg, cx + i, cy + j, 1, 1, hexc(c))
    rect(bg, cx - half if False else cx - int(r * 0.5), cy, int(r), 1, hexc("1A2C22"))
    if fleur:
        for (dx, dy, c) in ((0, -3, "E8C8D0"), (-1, -2, "D8A8B8"), (1, -2, "F4DCE0"), (0, -2, "FFF0F0"),
                            (-2, -1, "B88898"), (2, -1, "D8A8B8"), (-1, -1, "E8C8D0"), (1, -1, "F4DCE0"),
                            (0, -1, "FFF4E8")):
            rect(bg, cx + dx, cy + dy, 1, 1, hexc(c))


def roseaux(bg, cote="g", seed=0, couleur_rim=None, hauteur=170):
    """Roseaux et susuki en silhouette au premier plan (coin bas), avec un fil de contre-jour."""
    rs = random.Random(seed)
    W, H = bg.size
    rim = mix(couleur_rim, (60, 40, 50), 0.35) if couleur_rim is not None else hexc("3A2A30")
    n = 34
    for k in range(n):
        xb = rs.uniform(-10, 120) if cote == "g" else rs.uniform(W - 120, W + 10)
        hh = rs.uniform(0.35, 1.0) * hauteur * (1 - 0.5 * (abs(xb - (0 if cote == "g" else W)) / 130))
        pen = rs.uniform(-0.35, 0.35) + (0.25 if cote == "g" else -0.25)
        courb = rs.uniform(0.2, 0.9) * (1 if pen > 0 else -1)
        ep = 2 if rs.random() < 0.6 else 1
        last = None
        steps = int(hh)
        for s in range(steps):
            t = s / max(1, steps)
            x = xb + pen * hh * t + courb * 18 * t * t
            y = H - s
            X, Y = int(round(x)), int(round(y))
            rect(bg, X, Y, ep, 1, hexc("050306"))
            if t > 0.45 and k % 3 == 0:
                _px_mix(bg, X + ep if cote == "g" else X - 1, Y, rim, 0.22 * t)
            last = (X, Y)
        if last and rs.random() < 0.45:          # plumet de susuki
            X, Y = last
            for j in range(10):
                for i in range(-2, 3):
                    if rs.random() < 0.45 - abs(i) * 0.1:
                        _px_mix(bg, X + i + (j // 3) * (1 if cote == "g" else -1), Y + j, hexc("2A2026"), 0.9)
                        if rs.random() < 0.2:
                            _px_mix(bg, X + i + 1, Y + j, rim, 0.18)


def note_de_musique(px, x, y, c):
    """Croche pixel 7x8."""
    for (dx, dy) in ((4, 0), (5, 0), (4, 1), (6, 1), (4, 2), (6, 2), (4, 3), (4, 4), (4, 5),
                     (1, 5), (2, 5), (3, 5), (0, 6), (1, 6), (2, 6), (3, 6), (4, 6), (1, 7), (2, 7), (3, 7)):
        rect(px, x + dx, y + dy, 1, 1, c)


def onde(px, x, y, c):
    """Icône de voix : 5 barres d'onde centrées sur y + 4."""
    for i, hh in enumerate((3, 6, 8, 5, 2)):
        rect(px, x + i * 2, y + 4 - hh // 2, 1, hh, c)


def chevron(px, cx, y):
    """Repère doré au-dessus du personnage choisi : petit losange facetté."""
    for (dx, dy, cc) in ((0, 0, R_OR[7]), (-1, 1, R_OR[6]), (0, 1, R_OR[5]), (1, 1, R_OR[4]), (-2, 2, R_OR[5]),
                         (-1, 2, R_OR[6]), (0, 2, R_OR[5]), (1, 2, R_OR[4]), (2, 2, R_OR[3]), (-1, 3, R_OR[4]),
                         (0, 3, R_OR[4]), (1, 3, R_OR[3]), (0, 4, R_OR[3])):
        rect(px, cx + dx, y + dy, 1, 1, hexc(cc))


# Étapes de la création et la marque de leur sceau : le NUMÉRO de l'étape (ArcadePix), plus de kanji depuis le 18/10.
ETAPES = [("VILLAGE", "1"), ("CLAN", "2"), ("SEXE", "3"), ("NOM", "4"), ("AGE", "5"), ("TAILLE", "6"),
          ("APPARENCE", "7"), ("SCEAU", "8")]


XS_ETAPES = [int(round(480 - 3.5 * 62 + i * 62)) for i in range(8)]


def cadenas(px, x, y):
    """Cadenas pixel 7x8 or éteint."""
    c1, c2, c3 = hexc(R_OR[4]), hexc(R_OR[2]), hexc("1A1012")
    for (dx, dy) in ((2, 0), (3, 0), (4, 0), (1, 1), (5, 1), (1, 2), (5, 2)):
        rect(px, x + dx, y + dy, 1, 1, c2)
    rect(px, x, y + 3, 7, 5, c1)
    rect(px, x, y + 7, 7, 1, c2)
    rect(px, x + 3, y + 4, 1, 2, c3)


def tuile(px, x, y, w, h, etat="normal", seed=0):
    if etat == "actif":
        kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.55, rayon=1)
        lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=seed)
        liseret(px, x, y, w, h)
        ferrures3(px, x, y, w, h)
    else:
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=seed, intensite=1.6 if etat == "survol" else 0.75)
        c = hexc(R_OR[4] if etat == "survol" else R_OR[2])
        rect(px, x, y, w, 1, c); rect(px, x, y + h - 1, w, 1, hexc(R_OR[1]))
        rect(px, x, y, 1, h, c); rect(px, x + w - 1, y, 1, h, hexc(R_OR[1]))


def pastille(px, x, y, c, sel=False, t=14):
    """Pastille de couleur carrée (cadre or, reflet), sélection = double cadre or vif + pointe dessous."""
    if sel:
        rect(px, x - 3, y - 3, t + 6, t + 6, hexc(R_OR[6]))
        rect(px, x - 2, y - 2, t + 4, t + 4, hexc("120A0C"))
    rect(px, x - 1, y - 1, t + 2, t + 2, hexc(R_OR[3] if not sel else R_OR[5]))
    rect(px, x, y, t, t, hexc(c))
    rect(px, x, y, t, 1, mix(c, (255, 255, 255), 0.35))
    rect(px, x, y + t - 1, t, 1, mix(c, (0, 0, 0), 0.3))
    if sel:
        for j in range(3):
            rect(px, x + t // 2 - 2 + j, y + t + 4 + j, 5 - 2 * j, 1, hexc(R_OR[6]))


def oeil(px, x, y, iris, sel=False):
    """Vignette d'œil : cartouche 38x16, blanc de l'œil, iris 4x4 à la couleur, pupille pâle (dōjutsu)."""
    w, h = 38, 16
    if sel:
        rect(px, x - 3, y - 3, w + 6, h + 6, hexc(R_OR[6]))
        rect(px, x - 2, y - 2, w + 4, h + 4, hexc("120A0C"))
    rect(px, x - 1, y - 1, w + 2, h + 2, hexc(R_OR[3] if not sel else R_OR[5]))
    rect(px, x, y, w, h, hexc("E9C2A0"))
    rect(px, x, y, w, 2, hexc("2A2022"))
    for (ox) in (6, 22):
        rect(px, x + ox, y + 5, 10, 6, hexc("F4F0F4"))
        rect(px, x + ox + 3, y + 5, 4, 6, hexc(iris))
        rect(px, x + ox + 3, y + 5, 4, 1, mix(iris, (0, 0, 0), 0.25))
        rect(px, x + ox + 4, y + 7, 2, 2, mix(iris, (255, 255, 255), 0.45))
        rect(px, x + ox, y + 4, 10, 1, hexc("2A2022"))
    if sel:
        for j in range(3):
            rect(px, x + w // 2 - 2 + j, y + h + 4 + j, 5 - 2 * j, 1, hexc(R_OR[6]))


def regle(m, x, y, w, vmin, vmax, v, pas_petit=1, pas_grand=10, etiquettes=(), fin=False):
    """Réglette de bois graduée (curseur laqué rouge à flèche or) : un cran par unité, grands crans tous
    les 10, valeurs ArcadePix x0,5 sous les grands crans ; partie parcourue soulignée d'un fil or."""
    px = m.px
    lq(px, x, y, w, 9, ["2C170C", "3B2012", "4B2A17", "5C351D", "6E4124"], seed=x)
    rect(px, x, y, w, 1, hexc("8A5A34"))
    rect(px, x, y + 8, w, 1, hexc("160A06"))
    n = vmax - vmin
    pos = lambda val: x + 2 + (val - vmin) / n * (w - 4)
    for val in range(vmin, vmax + 1, pas_petit):
        X = int(round(pos(val)))
        grand = val % pas_grand == 0
        moyen = val % 5 == 0
        hh = 6 if grand else (4 if moyen else 2)
        if fin or grand or moyen or (vmax - vmin) <= 60:
            rect(px, X, y + 1, 1, hh, hexc(R_OR[5] if grand else (R_OR[3] if moyen else "6A4A2A")))
    for val in etiquettes:
        m.arcade_fin(pos(val), y + 12, str(val), 0.5, hexc("B8A27E"), ancre="c")
    X = int(round(pos(v)))
    rect(px, x + 2, y + 9, X - x - 2, 1, hexc(R_OR[5]))
    # curseur
    kit.ombre_portee(px, X - 4, y - 5, 9, 10, dx=1, dy=1, force=0.5, rayon=0)
    lq(px, X - 3, y - 4, 7, 8, "rouge", seed=X)
    liseret(px, X - 4, y - 5, 9, 10)
    for j in range(3):
        rect(px, X - 2 + j, y + 5 + j, 5 - 2 * j, 1, hexc(R_OR[5]))


def pm(m, x, y, cm=False):
    """Paire de boutons − / + (cabochons 18x14)."""
    for i, s in enumerate(("-", "+")):
        bx = x + i * 22
        px = m.px
        kit.ombre_portee(px, bx, y, 18, 14, dx=1, dy=1, force=0.5, rayon=0)
        lq(px, bx + 1, y + 1, 16, 12, "noir", seed=bx, intensite=1.3)
        liseret(px, bx, y, 18, 14)
        rect(px, bx + 6, y + 6, 6, 2, hexc(R_OR[5]))
        if s == "+":
            rect(px, bx + 8, y + 4, 2, 6, hexc(R_OR[5]))


def fleche_bouton(m, x, y, sens="g"):
    """Bouton carré 20x20 laqué, flèche pixel or."""
    px = m.px
    kit.ombre_portee(px, x, y, 20, 20, dx=1, dy=2, force=0.6, rayon=1)
    rect(px, x - 1, y - 1, 22, 22, PAL["fond"])
    lq(px, x + 1, y + 1, 18, 18, "noir", seed=x, intensite=1.1)
    liseret(px, x, y, 20, 20)
    ferrures3(px, x, y, 20, 20)
    for j in range(7):
        w = 4 - abs(j - 3)
        for i in range(w):
            xx = x + 8 + i if sens == "d" else x + 11 - i
            rect(px, xx, y + 6 + j, 1, 1, hexc(R_OR[5] if j < 4 else R_OR[4]))


def toise(m, x, ysol, cm, adulte=None):
    """La toise (shinchō-kei) plantée à côté du personnage : mât de bambou, crans tous les 5 cm, valeurs
    tous les 10 cm de 150 à 210 (ArcadePix x0,5), bornes 153 / 207 cm en rouge sourd ; plaquette laquée
    coulissante au sommet du crâne, reliée à la tête par un fil or pointillé."""
    px = m.calque()
    y_cm = lambda c: ysol - c * GUI_PAR_CM
    top = int(y_cm(214))
    mat_bambou(px, x, top, ysol + 2, seed=3)
    for c in range(0, 211, 5):
        Y = int(round(y_cm(c)))
        grand = c % 10 == 0
        rect(px, x + 6, Y, 5 if grand else 3, 1, hexc(R_OR[5] if grand else R_OR[3]))
        if grand and c >= 150 and c != 170:
            m.arcade_fin(x + 15, Y - 1.75, str(c), 0.5, hexc("A8946E"))
    for c in (153, 207):
        Y = int(round(y_cm(c)))
        rect(px, x - 3, Y, 14, 1, hexc("8E2A2A"))
    if adulte:                                    # taille adulte : repère pointillé or éteint
        Ya = int(round(y_cm(adulte)))
        for i in range(x - 3, x + 12, 2):
            rect(px, i, Ya, 1, 1, hexc(R_OR[3]))
        m.arcade_fin(x - 5, Ya - 1.75, f"ADULTE {adulte}", 0.5, hexc("C8A870"), ancre="d")
    Y = int(round(y_cm(cm)))
    for i in range(x - 62, x - 2, 2):
        rect(px, i, Y, 1, 1, hexc(R_OR[4]))
    bx = x + 13
    kit.ombre_portee(px, bx, Y - 7, 40, 14, dx=1, dy=1, force=0.55, rayon=0)
    lq(px, bx + 1, Y - 6, 38, 12, "rouge", seed=5)
    liseret(px, bx, Y - 7, 40, 14)
    for j in range(4):
        rect(px, bx - 1 - j, Y - j, 1, 2 * j + 1, hexc(R_OR[5]))
    m.arcade_fin(bx + 20, Y - 1.75, "168 CM", 0.5, CREME, ancre="c", ombre=True)
    halo_px(m, bx + 20, Y, 22, OR, force=0.07, niveaux=2)


def brume_avant(m, y0, y1, couleur=(34, 26, 46), force=0.86):
    """Banc de brume au ras de l'eau, DEVANT le personnage (plan rapproché) : 5 paliers d'opacité au pas
    GUI, bord haut ondulé et tramé (Bayer) — une texture fixe en jeu."""
    W = 960
    lay = m.calque()
    h = y1 - y0
    yy, xx = np.mgrid[0:h, 0:W].astype(float)
    ond = (kit.bruit(W, 1, 40, 5, 2)[0] - 0.5) * 14
    t = np.clip((yy - ond[None, :] * 0.6) / (h * 0.55), 0, 1)
    q = kit.quantifier(t, 6, 0, y0, tramage=0.6) / 5.0
    p = np.zeros((h, W, 4), np.uint8)
    p[..., :3] = couleur
    p[..., 3] = (q * 255 * force).astype(np.uint8)
    coller(lay, p, 0, y0)
    # volutes plus claires sur le bord haut (lune)
    rs = random.Random(4)
    for _ in range(26):
        x0 = rs.randint(-20, 940)
        yv = int(y0 + h * 0.18 + ond[min(959, max(0, x0))] * 0.6 + rs.uniform(-6, 10))
        L = rs.randint(30, 90)
        for i in range(L):
            t = i / L
            if kit.bayer(1, 1, x0 + i, yv)[0, 0] < math.sin(math.pi * t) * 0.8:
                _px_set(lay, x0 + i, yv, (70, 60, 92), 0.55)


def enso_sol(m, px, cx, cy, rx, ry, couleur, ouverture=(0.10, 0.22)):
    """Ensō de chakra posé sur la pierre (trait pixel d'1 GUI, ouvert comme un coup de pinceau) + halo net."""
    pts = []
    for s in range(900):
        a = s / 900 * 2 * math.pi
        if ouverture[0] * math.pi < a < ouverture[1] * math.pi:
            continue
        pts.append((int(round(cx + math.cos(a) * rx)), int(round(cy + math.sin(a) * ry)), a))
    vus = set()
    for (x, y, a) in pts:
        if (x, y) in vus:
            continue
        vus.add((x, y))
        k = 0.55 + 0.45 * math.sin(a * 0.5) ** 2
        _px_set(px, x, y, mix(couleur, (255, 240, 210), 0.35), 0.95 * k)
    halo_px(m, cx, cy, rx * 1.12, couleur, force=0.10, niveaux=2, ry_k=ry / rx)


def kamon_sol(m, bg, cx, cy, cid, couleur):
    """L'emblème du clan choisi (la vraie texture gui/clans/clan_<id>.png), gravé en lumière sur le dais de
    pierre, DEVANT le perso (qui se tient au fond du dais) : anneau elliptique + emblème couché en perspective
    (aplati à 30 %), au pas GUI. (cx, cy) = pieds du perso. En jeu : la même texture posée à plat sur la pierre,
    teintée à la couleur du clan."""
    col = mix(couleur, (255, 255, 255), 0.30)
    yc = cy + DAIS_DY
    vus = set()
    for s_ in range(1200):
        t = s_ / 1200 * 2 * math.pi
        p = (int(round(cx + math.cos(t) * 56)), int(round(yc + math.sin(t) * 12.5)))
        if p not in vus:
            vus.add(p)
            _px_set(bg, p[0], p[1], col, 0.80)
    em = kit.texture_mod(f"gui/clans/clan_{cid}.png")
    W, H = 50, 15
    a = np.asarray(em.resize((W * 4, H * 4), Image.LANCZOS))[..., 3].astype(float)
    a = a.reshape(H, 4, W, 4).mean(axis=(1, 3))
    y0 = yc + 5 - H / 2
    for j in range(H):
        for i in range(W):
            if a[j, i] > 70:
                _px_set(bg, int(cx - W / 2 + i), int(y0 + j), col, 0.45 + 0.40 * min(1, a[j, i] / 160))
    halo_px(m, cx, yc + 4, 50, couleur, force=0.14, niveaux=2, ry_k=0.26)


def lueur_cle(bg, cx, cy, couleur, force=0.22):
    """Lumière clé : halo chaud derrière le personnage + flaque de lumière sur l'eau autour de sa pierre."""
    lumiere(bg, cx, cy - 120, 150, couleur, force=force * 0.80, ry=170, niveaux=6, expo=1.4, tramage=0.25)
    lumiere(bg, cx, cy + 4, 130, couleur, force=force, ry=28, niveaux=4, tramage=0.3)


DAIS_DY, EP_DAIS = 8, 15


GUI_PAR_CM = 1.4286        # 168 cm = 240 GUI (échelle de la figure : 15 px/texel)


def plaque_titre(m, texte, cx=480, y=8, w=300, h=28, sous=None):
    """Plaque laquée suspendue (même facture que l'en-tête de la fiche) : cordelettes, glands,
    titre ArcadePix x1 crème, filets or à losanges."""
    px = m.px
    x = cx - w // 2
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 30)
        for k in range(y + 1):
            X = a + sgn * max(0, (8 - k))
            rect(px, X, k, 1, 1, hexc(kit.R_CORDE["rouge"][3] if k % 2 == 0 else kit.R_CORDE["rouge"][2]))
            rect(px, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(kit.R_CORDE["rouge"][1]))
    panneau(px, x, y, w, h, seed=2, eclats=False)
    for sgn in (-1, 1):
        anneau_or(px, cx + sgn * (w // 2 - 30), y + 1)
        gx = cx + sgn * (w // 2 + 4)
        rect(px, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        agemaki(px, gx, y + 16)
        gland3(px, gx, y + 21, 10)
    tw = kit.arcade_largeur(texte, 10)
    arcade(px, cx, y + 11, texte, 10, CREME, ancre="c", ombre=True)
    for sgn in (-1, 1):
        X0 = cx + sgn * (tw // 2 + 14)
        X1 = cx + sgn * (w // 2 - 22)
        for X in (X0, X1):
            for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
                rect(px, X + dx, y + 14 + dy, 1, 1, hexc(c))
        rect(px, min(X0, X1) + 3, y + 14, abs(X1 - X0) - 5, 1, hexc(R_OR[2]))
    if sous:
        m.inter(cx, y + h + 10, sous, 8, couleur=DOUX, ancre="c", ancre_v="milieu", alpha=210)


