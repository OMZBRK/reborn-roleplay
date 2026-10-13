# -*- coding: utf-8 -*-
"""
fiche_art.py — primitives pixel art de la fiche shinobi « Le portrait en scène » (v2, tour 2).

Portage dans le dépôt des primitives propres à la maquette (Maquettes/fiche-shinobi/v2/portrait/
maquette.py, tour 2), pour que le générateur de textures ne dépende JAMAIS du dossier Maquettes/.
La boîte à outils commune tools/style/kit.py est importée en LECTURE SEULE (rampes, tramage Bayer,
bois, hanko, ArcadePix…) ; rien n'y est modifié.

Différences voulues avec la maquette :
- tout est dessiné à la résolution GUI (1 texel = 1 GUI) : les halos en anneaux, que la maquette
  posait à la résolution écran, sont ici des anneaux pleins à bord net d'1 GUI ;
- le décor peut être plus large que 960 (fonds 1280) : décalage `ox` et point de fuite paramétrés ;
- les lanternes et les flammes sont séparées (calques), pour l'animation côté code ;
- aucun texte dynamique (kanji des titres, valeurs) : seuls les titres fixes en ArcadePix sont cuits.

Ce module n'écrit aucun fichier ; il ne fait que dessiner sur des images PIL RGBA.
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
import kit  # noqa: E402  (boîte à outils partagée, lecture seule)
from kit import PAL, R_OR, R_LAQUE, R_LAQUE_ROUGE, STATS, hexc, rect, arcade, coller  # noqa: E402,F401

# ───────────────────────── palette (maquette tour 2) ─────────────────────────
OR = PAL["or_"]
OR_CLAIR = PAL["or_clair"]
OR_MOYEN = hexc("C8A05A")
OR_ETEINT = hexc("8E6A30")
BISEAU_CLAIR = hexc("F0C987")
BISEAU_SOMBRE = hexc("8A6630")
CREME = PAL["creme"]
DOUX = PAL["creme_doux"]
ROUGE = PAL["rouge"]
BOIS_ECLAT = hexc("3A2418")
OMBRE_FIL = hexc("120A06")

CIEL = ["0B0812", "0E0A17", "110C1C", "140E21", "181026", "1D1229", "23152B"]
CIEL_FROID = ["090A13", "0B0D18", "0D101D", "101422", "131827", "171C2C", "1B2030"]
Y_SOL = 404
Y_LEVRE = 527
TRAME = 0.16          # largeur des transitions tramées : aplats purs entre les crans

ENCRE_LANTERNE = (42, 20, 24)    # #2A1418 : kanji des lanternes, brun-noir opaque
Y_PERCHE = 56                     # perches de bambou laqué (panneaux suspendus)

RANG_COL = {"D": "4E7A60", "C": "A08A44", "B": "8E4048", "A": "6E5890", "S": "5A5650"}


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


# ═════════════════════════════════════ lumière pixel ═════════════════════════════════════
def _ajouter(img, x0, y0, A, couleur, niveaux=4, tramage=TRAME, sat=0.8):
    """Lumière additive quantifiée en crans pleins ; seule la jonction entre deux crans est
    tramée sur une bande étroite (jamais de damier sur une surface)."""
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


def lumiere(img, cx, cy, r, couleur, force=0.2, ry=None, niveaux=4, expo=2.0, sat=0.8, tramage=TRAME):
    ry = ry or r
    x0, y0 = int(cx - r), int(cy - ry)
    w, h = int(2 * r) + 2, int(2 * ry) + 2
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.sqrt(((xx + x0 - cx) / r) ** 2 + ((yy + y0 - cy) / ry) ** 2)
    A = np.clip(1 - d, 0, 1) ** expo * force
    _ajouter(img, x0, y0, A, couleur, niveaux, tramage, sat)


def masque_lumiere(rx, ry, niveaux, tramage=TRAME, expo=2.0):
    """Masque de lumière normalisé (0..1, cœur = 1) : ellipse rx × ry, déclin (1−d)^expo,
    `niveaux` crans pleins, transitions tramées étroites. Taille (2ry+2) × (2rx+2) ; centre au
    texel (rx, ry). Même calcul que lumiere()/_ajouter(), la couleur et la force en moins."""
    w, h = int(2 * rx) + 2, int(2 * ry) + 2
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.sqrt(((xx - rx) / rx) ** 2 + ((yy - ry) / ry) ** 2)
    A = np.clip(1 - d, 0, 1) ** expo
    return kit.quantifier(A / A.max(), niveaux + 1, 0, 0, tramage) / niveaux


def assombrir(img, x0, y0, A):
    h, w = A.shape
    X0, Y0 = max(0, x0), max(0, y0)
    X1, Y1 = min(img.width, x0 + w), min(img.height, y0 + h)
    A = A[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0]
    arr = np.asarray(img.crop((X0, Y0, X1, Y1))).astype(float)
    arr[..., :3] *= (1 - A[..., None])
    img.paste(Image.fromarray(arr.astype(np.uint8), "RGBA"), (X0, Y0))


def halo_anneaux_px(img, cx, cy, anneaux, couleur):
    """Halo en anneaux pleins (additif) À LA RÉSOLUTION GUI, bord net d'1 texel.
    anneaux : [(rayon, alpha)] en GUI ; chaque disque ajoute son alpha (le centre cumule)."""
    R = max(r for r, _ in anneaux) + 2
    x0, y0 = int(math.floor(cx - R)), int(math.floor(cy - R))
    X0, Y0 = max(0, x0), max(0, y0)
    X1, Y1 = min(img.width, x0 + int(2 * R) + 2), min(img.height, y0 + int(2 * R) + 2)
    if X1 <= X0 or Y1 <= Y0:
        return
    yy, xx = np.mgrid[Y0:Y1, X0:X1].astype(float)
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
    A = np.zeros_like(d)
    for r, a in anneaux:
        A += a * (d <= r)
    c = np.array(hexc(couleur)[:3], float)
    base = np.asarray(img.crop((X0, Y0, X1, Y1))).astype(float)
    base[..., :3] = np.minimum(255, base[..., :3] + c * A[..., None])
    img.paste(Image.fromarray(base.astype(np.uint8), "RGBA"), (X0, Y0))


# ═════════════════════════════════════ décor ═════════════════════════════════════
def ciel(bg, seed=1, teinte=None):
    """Vide indigo-noir : 7 crans pleins, jonctions ondulées par un bruit lent."""
    W, H = bg.size
    rampe_ = [hexc(c) for c in (teinte or CIEL)]
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    v = np.clip(yy / Y_SOL, 0, 1) ** 0.85
    v += (kit.bruit(W, H, 120, seed, 3, etire=(3, 1)) - 0.5) * 0.10
    idx = kit.quantifier(v, len(rampe_), 0, 0, tramage=0.10)
    pal = np.array(rampe_, np.uint8)
    bg.paste(Image.fromarray(pal[idx], "RGBA"), (0, 0))


def vignette_px(bg, force=0.5, cx=480, demi_l=480):
    """Vignette en crans tramés étroits. Le centre et la demi-largeur restent ceux de la fenêtre
    960 de la maquette, même sur un décor plus large (les côtés prolongés s'assombrissent)."""
    W, H = bg.size
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    d = np.sqrt(((xx - cx) / demi_l) ** 2 + ((yy - H * 0.55) / (H * 0.62)) ** 2)
    A = np.clip((d - 0.62) / 0.75, 0, 1) ** 1.5
    q = kit.quantifier(A, 5, 0, 0, TRAME) / 4 * force
    assombrir(bg, 0, 0, q)


def brume(bg, y, ep=12, force=0.06, seed=0, couleur=(120, 112, 160)):
    W = bg.width
    y0 = int(y - 3 * ep)
    h = int(6 * ep)
    yy = np.arange(h)[:, None] + y0
    prof = np.exp(-((yy - y) / ep) ** 2)
    nz = kit.bruit(W, h, 80, seed, 2, etire=(4, 1))
    A = prof * (0.45 + 0.55 * nz) * force
    _ajouter(bg, 0, y0, A, couleur, niveaux=3, tramage=0.25, sat=1.0)


def _px_mix(img, x, y, c, a):
    if 0 <= x < img.width and 0 <= y < img.height:
        o = np.array(img.getpixel((x, y))[:3], float)
        n = o * (1 - a) + np.array(c[:3], float) * a
        img.putpixel((x, y), tuple(int(v) for v in n) + (255,))


def mini_lanterne(img, X, Y, s_, base, op):
    w = max(2, int(round(s_ * 0.75)))
    pale = np.array([255, 236, 196], float)
    for j in range(s_):
        t = j / max(1, s_ - 1)
        ww = w - (2 if (j == 0 and s_ >= 5 and w >= 4) else 0)
        ox = (w - ww) // 2
        ton = base * (0.42 if t < 0.5 else 0.78)
        if j == s_ - 1 and s_ >= 3:
            ton = base * 0.75 + pale * 0.25
        for i in range(ww):
            _px_mix(img, X + ox + i, Y + j, ton, op)
    _px_mix(img, X + (w - 1) // 2, Y + s_ - 1, pale, min(1.0, op * 1.5))


def lanternes_lointaines(bg, interdits=(), seed=5, n=30, y_max=296, voile=1.0, ox=0, cotes=0, braises=False):
    """Plan lointain (tirage IDENTIQUE à la maquette dans la fenêtre 960, décalé de `ox`) :
    petites lanternes de 2 à 6 GUI, 35 GUI au moins entre deux, aucune colonne, 12 GUI au moins
    de toute interface. `cotes` lanternes de plus par côté dans les bandes prolongées (tirage à
    part, la fenêtre 960 reste celle de la maquette). Renvoie les halos à cuire plus tard :
    [(cx, cy, rayon, alpha, couleur)] en coordonnées du décor."""
    rs = random.Random(seed)
    interdits = [(a, b, c, max(d, y_max + 14) if (c - a) > 150 else d) for (a, b, c, d) in interdits]
    poses = []
    essais = 0
    while len(poses) < n and essais < 20000:
        essais += 1
        x = rs.uniform(8, 952)
        y = rs.uniform(14, y_max)
        ok = all(not (a - 12 <= x <= c + 12 and b - 12 <= y <= d + 12) for (a, b, c, d) in interdits)
        if ok:
            for (px_, py_, _s) in poses:
                if math.hypot(px_ - x, py_ - y) < 35 or (abs(px_ - x) < 9 and abs(py_ - y) < 110):
                    ok = False
                    break
        if ok:
            prof = (y - 14) / (y_max - 14)
            s_ = int(round(2 + 4 * prof + rs.uniform(-0.6, 0.6)))
            poses.append((x, y, max(2, min(6, s_))))
    teintes = []
    for k, (x, y, s_) in enumerate(poses):
        if k % 6 == 2:
            base = np.array(STATS[(k // 6) % 6][3][:3], float) * 0.6 + np.array([242, 165, 72]) * 0.4
        else:
            base = np.array(rs.choice([(242, 165, 72), (236, 146, 62), (248, 190, 104), (230, 128, 70)]), float)
        teintes.append(base)
    # côtés prolongés (hors de la fenêtre 960) : tirage séparé
    if cotes and ox > 0:
        r2 = random.Random(seed * 7919 + 13)
        extra = []
        for cote in (-1, 1):
            k_ = 0
            essais = 0
            while k_ < cotes and essais < 5000:
                essais += 1
                x = r2.uniform(8 - ox, 0) if cote < 0 else r2.uniform(960, 952 + ox)
                y = r2.uniform(14, y_max)
                if all(math.hypot(a - x, b - y) >= 35 and not (abs(a - x) < 9 and abs(b - y) < 110)
                       for (a, b, _s) in poses + extra):
                    prof = (y - 14) / (y_max - 14)
                    s_ = max(2, min(6, int(round(2 + 4 * prof + r2.uniform(-0.6, 0.6)))))
                    extra.append((x, y, s_))
                    teintes.append(np.array(r2.choice([(242, 165, 72), (236, 146, 62), (248, 190, 104)]), float))
                    k_ += 1
        poses = poses + extra
    halos = []
    for (x, y, s_), base in zip(poses, teintes):
        prof = (y - 14) / (y_max - 14)
        op = (0.30 + 0.30 * prof) * voile
        X = int(math.floor(x)) + ox
        if braises:
            # Tour 3 : simples braises de 1 à 2 texels, plus sombres, sans halo (aucun anneau).
            d = 1 if s_ <= 3 else 2
            sombre = base * 0.55
            for j in range(d):
                for i in range(d):
                    _px_mix(bg, X + i, int(y) + j, sombre, op * 0.9)
            continue
        mini_lanterne(bg, X, int(y), s_, base, op)
        w = max(2, int(round(s_ * 0.75)))
        halos.append((X + w / 2, int(y) + s_ * 0.7, s_ * 1.3, 0.03 + 0.05 * op, tuple(int(v) for v in base)))
    return halos


# ─────────── plancher de scène laqué ───────────
_VARIANTES = {}


def _variante_bois(k, W=1800, H=26):
    if (k, W) not in _VARIANTES:
        tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        kit.bois(tmp, 0, 0, W, H, "sombre", sens="h", seed=101 + 37 * k, noeuds=0, biseau=False)
        _VARIANTES[(k, W)] = np.asarray(tmp).astype(float)
    return _VARIANTES[(k, W)]


def joints_plancher(bas=Y_LEVRE):
    h = bas - Y_SOL
    N = 7
    return [Y_SOL + int(round(h * (k / N) ** 1.42)) for k in range(N + 1)]


def plancher(bg, seed=3, vernis=(26, 20, 38), sombre=0.42, vpx=480.0, ox=0, levre=True):
    """Plancher de scène laqué : lames en perspective (point de fuite vpx, 300), 4 variantes de
    veinage, aboutements qui fuient, reflet d'1 GUI sur l'arête de chaque lame (plus fort au
    centre), lèvre or éteint, face laquée rouge et clous tous les 96 GUI."""
    W = bg.width
    bas = Y_LEVRE if levre else bg.height       # sans lèvre : les lames descendent jusqu'au bas (tour 3)
    ys = joints_plancher(bas)
    vpy = 300.0
    rs = random.Random(seed)
    arr = np.asarray(bg).astype(float).copy()
    for k in range(len(ys) - 1):
        a, b = ys[k], ys[k + 1]
        hb = b - a
        prof = 1 - (k + 0.5) / (len(ys) - 1)
        bords = []
        xw = -rs.uniform(0, 260)
        while xw < W + 400:
            bords.append(xw)
            xw += rs.uniform(170, 330)
        for bi in range(len(bords) - 1):
            v = rs.randrange(4)
            tex = _variante_bois(v)
            off = rs.randrange(0, tex.shape[1] - W - 1)
            gain = rs.uniform(0.88, 1.08)
            for y in range(a, b):
                t = (y - vpy) / (bas - vpy)
                xa = int(round(vpx + (bords[bi] - vpx) * t))
                xb = int(round(vpx + (bords[bi + 1] - vpx) * t))
                xa, xb = max(0, xa), min(W, xb)
                if xb <= xa:
                    continue
                row = tex[min(tex.shape[0] - 1, int((y - a) * 26 / max(1, hb))), off + xa:off + xb, :3]
                arr[y, xa:xb, :3] = row * (sombre + 0.10 * (1 - prof)) * gain + np.array(vernis) * (0.22 + 0.30 * prof)
                arr[y, xa:xb, 3] = 255
            for y in range(a + 1, b):
                t = (y - vpy) / (bas - vpy)
                x = int(round(vpx + (bords[bi + 1] - vpx) * t))
                if 0 <= x < W:
                    arr[y, x, :3] *= 0.40
                if 0 <= x + 1 < W:
                    arr[y, x + 1, :3] = np.minimum(255, arr[y, x + 1, :3] * 1.20 + 3)
        arr[a, :, :3] *= 0.32
        if hb > 2:
            xs = np.arange(W)
            dc = np.abs(xs - vpx)
            gain = np.where(dc < 150, 30, np.where(dc < 270, 18, np.where(dc < 390, 8, 2))) * (0.6 + 0.4 * (1 - prof))
            arr[a + 1, :, :3] = np.minimum(255, arr[a + 1, :, :3] + gain[:, None] * np.array([1.0, 0.92, 1.05]))
    bg.paste(Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA"), (0, 0))
    rect(bg, 0, Y_SOL, W, 1, hexc("050304"))
    if not levre:
        return
    rect(bg, 0, Y_LEVRE, W, 1, hexc(R_OR[3]))
    rect(bg, 0, Y_LEVRE + 1, W, 1, hexc(R_OR[1]))
    face = Image.new("RGBA", (W, 540 - Y_LEVRE - 2), (0, 0, 0, 0))
    lq(face, 0, 0, W, face.height, ["2A070D", "3A0A12", "4E0D17", "63111D", "7A1322"], reflet=False, seed=seed)
    bg.paste(face, (0, Y_LEVRE + 2))
    rect(bg, 0, Y_LEVRE + 2, W, 1, hexc("120608"))
    for x in range((14 + ox) % 96, W, 96):          # même phase que la maquette dans la fenêtre 960
        rect(bg, x, Y_LEVRE, 2, 1, hexc(R_OR[5]))


def reflet_masque(longueur, largeur=3, seed=0):
    """Coulée de lumière verticale sous une source (maquette : reflet()), force 1 : renvoie la
    carte A (longueur × largeur+4) avant quantification."""
    L = int(longueur)
    w = largeur + 4
    A = np.zeros((L, w))
    for k in range(L):
        a = (1 - k / L) ** 1.2
        wob = 1 if (k // 6 + seed) % 7 == 3 else 0
        c = w // 2 + wob
        for dx in range(-(largeur // 2) - 1, largeur // 2 + 2):
            wt = 1.0 if abs(dx) <= largeur // 2 - 1 else (0.55 if abs(dx) <= largeur // 2 else 0.2)
            if 0 <= c + dx < w:
                A[k, c + dx] = max(A[k, c + dx], a * wt)
    return A


# ═════════════════════════════════════ matières ═════════════════════════════════════
def lq(img, x, y, w, h, ton="noir", reflet=True, seed=0, intensite=1.0):
    """Laque urushi : fond profond, dégradé vertical, reflet diagonal ; crans pleins, tramage
    seulement aux jonctions."""
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


def liseret(img, x, y, w, h):
    """Liseré or biseauté 1 GUI : clair en haut et à gauche, sombre en bas et à droite."""
    rect(img, x, y, w, 1, BISEAU_CLAIR)
    rect(img, x, y, 1, h, BISEAU_CLAIR)
    rect(img, x, y + h - 1, w, 1, BISEAU_SOMBRE)
    rect(img, x + w - 1, y, 1, h, BISEAU_SOMBRE)
    rect(img, x, y + h - 1, 1, 1, OR)
    rect(img, x + w - 1, y, 1, 1, OR)


_KG10 = None


def kanagu10():
    """Ferrure d'angle 10x10 en découpe de feuille, biseau or 2 tons, rivet central."""
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
        if yy == 1 and xx >= 1 and m[yy, xx] and p[yy, xx, 0] == OR[0]:
            p[yy, xx] = hexc("E4B870")
        if xx == 1 and yy >= 1 and m[yy, xx] and p[yy, xx, 0] == OR[0]:
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
            a[ry_ + 1 if ry_ + 1 < N else ry_, rx_ + 1 if rx_ + 1 < N else rx_] = hexc("6A4E24")
        coller(img, a, px_, py_)


def kanagus_mini(px, x, y, w, h):
    """Équerres d'angle 3x3 (plaquettes de mudra)."""
    for (a, b, sx, sy) in ((x, y, 1, 1), (x + w - 1, y, -1, 1), (x, y + h - 1, 1, -1), (x + w - 1, y + h - 1, -1, -1)):
        rect(px, a, b, 1, 1, BISEAU_CLAIR)
        rect(px, a + sx, b, 1, 1, OR)
        rect(px, a, b + sy, 1, 1, OR)


def agemaki(img, cx, cy):
    """Nœud agemaki 9x9 : quatre boucles pleines en rouge 3 tons, centre or d'1 GUI."""
    a, b, c = hexc("6E1220"), hexc("A42230"), hexc("D04A4E")
    o = hexc("3A0810")
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
    p[4, 4] = hexc(R_OR[6])
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
    """Cordelette torsadée strictement verticale."""
    R = [hexc(c) for c in kit.R_CORDE[couleur]]
    for k, y in enumerate(range(int(y0), int(y1) + 1)):
        if 0 <= y < img.height:
            if ep == 1:
                rect(img, x, y, 1, 1, R[3] if k % 2 == 0 else R[2])
            else:
                rect(img, x, y, 1, 1, (R[3], R[2], R[2])[k % 3])
                rect(img, x + 1, y, 1, 1, (R[1], R[3], R[1])[k % 3])


def corde_h(img, x0, x1, y, couleur="rouge"):
    """Cordelette torsadée strictement horizontale (2 GUI), motif de période 3."""
    R = [hexc(c) for c in kit.R_CORDE[couleur]]
    for k, x in enumerate(range(int(x0), int(x1) + 1)):
        rect(img, x, y, 1, 1, (R[3], R[2], R[2])[k % 3])
        rect(img, x, y + 1, 1, 1, (R[1], R[3], R[1])[k % 3])


def perle_or(img, cx, cy):
    """Perle d'or 3x3 enfilée sur un fil."""
    rect(img, cx - 1, cy - 1, 3, 3, OR)
    rect(img, cx - 1, cy - 1, 2, 1, BISEAU_CLAIR)
    rect(img, cx - 1, cy, 1, 1, BISEAU_CLAIR)
    rect(img, cx + 1, cy + 1, 1, 1, BISEAU_SOMBRE)
    rect(img, cx, cy + 1, 1, 1, hexc(R_OR[4]))
    rect(img, cx + 1, cy, 1, 1, hexc(R_OR[4]))


def anneau_or(img, cx, cy):
    kit.anneau_metal(img, cx, cy, 2)


def losange_or(img, x, y):
    """Puce en losange or 3x3, calée en (x, y)."""
    for (dx, dy, c) in ((1, 0, R_OR[7]), (0, 1, R_OR[5]), (1, 1, R_OR[6]), (2, 1, R_OR[4]), (1, 2, R_OR[3])):
        rect(img, x + dx, y + dy, 1, 1, hexc(c))


# ─────────── planches laquées ───────────
def panneau(img, x, y, w, h, seed=0, ton="noir", kanagu_=True, eclats=True):
    """Planche laquée : ombre portée, contour, laque à reflet, grain du bois sous la laque,
    éclats d'1 GUI aux coins, liseré or biseauté, gorge, filet rouge, kanagu 10x10."""
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


def panneau_opacite(img, x, y, w, h, opacite=0.92, **kw):
    """panneau() dessiné sur un calque puis posé à `opacite` (comme la maquette)."""
    lay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    panneau(lay, x, y, w, h, **kw)
    a = np.asarray(lay).copy()
    a[..., 3] = (a[..., 3].astype(float) * opacite).astype(np.uint8)
    img.alpha_composite(Image.fromarray(a, "RGBA"))


def bandeau_titre(img, x, y, w, titre=None, seed=None):
    """Bandeau de laque rouge sous l'arête haute ; titre ArcadePix x1 or clair ombré cuit (le
    kanji du titre reste du texte, posé par le code en (x+w−20, by+7))."""
    bx, by, bw, bh = x + 5, y + 5, w - 10, 22
    lq(img, bx, by, bw, bh, "rouge", seed=x if seed is None else seed)
    rect(img, bx, by + bh, bw, 1, hexc(R_OR[4]))
    rect(img, bx, by + bh + 1, bw, 1, hexc("120608"))
    rect(img, bx, by, bw, 1, hexc(R_LAQUE_ROUGE[2]))
    if titre:
        arcade(img, x + 14, by + 8, titre, 10, OR_CLAIR, ombre=True)


def planche(img, x, y, w, h, titre=None, gland="bd", seed=0, opacite=0.92, ancrages=None, bandeau=True,
            seed_bandeau=None, y_perche=None):
    """Planche laquée suspendue : deux cordelettes rouges depuis le plafond (y −2), œillets
    dorés, panneau à 92 %, nœud agemaki + gland sous un coin bas, bandeau titre rouge."""
    ax = ancrages or (x + 34, x + w - 36)
    for a in ax:
        corde_v(img, a, -2 if y_perche is None else y_perche + 5, y + 1, "rouge", ep=2)
    if y_perche is not None:
        # tours de cordelette autour de la perche (posés sur la perche, dessinée avant le panneau)
        for a in ax:
            rect(img, a - 1, y_perche, 4, 1, hexc("A42230"))
            rect(img, a - 1, y_perche + 2, 4, 1, hexc("A42230"))
            rect(img, a - 1, y_perche + 4, 4, 1, hexc("7A1322"))
    panneau_opacite(img, x, y, w, h, opacite, seed=seed)
    for a_ in ax:
        anneau_or(img, a_ + 1, y + 1)
    if gland:
        gx = x + w - 20 if gland == "bd" else x + 20
        agemaki(img, gx, y + h + 4)
        gland3(img, gx, y + h + 9, 11)
    if bandeau:
        bandeau_titre(img, x, y, w, titre, seed_bandeau)


def plaquette(px, x, y, w, h, kind="noir", seed=0):
    """Petite plaquette laquée bordée d'or (sceaux de stats, mudras, Mérite, 師)."""
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=1, force=0.55, rayon=0)
    if kind == "rouge":
        lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=seed)
    elif kind == "or":
        lq(px, x + 1, y + 1, w - 2, h - 2, ["5A4220", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=seed)
    else:
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=seed, intensite=1.3)
    liseret(px, x, y, w, h)


# ═════════════════════════════════════ en-tête, onglets, fermer ═════════════════════════════════════
def entete_fixe(px, cx, w=300):
    """Partie fixe de l'en-tête (tour 3, maquette : 300 de large) : cordons rouges du plafond en
    diagonale 1:1, plaque laquée w×28 en (cx−w/2, 8), œillets, agemaki + glands hors de la plaque,
    losanges or reliés par un filet, « FICHE SHINOBI » ArcadePix x1 crème ombré (haut y 19)."""
    y, h = 8, 28
    x = cx - w // 2
    R = kit.R_CORDE["rouge"]
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 30)
        for k in range(9):
            X = a + sgn * (8 - k)
            rect(px, X, k, 1, 1, hexc(R[3] if k % 2 == 0 else R[2]))
            rect(px, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(R[1]))
    panneau(px, x, y, w, h, seed=2, eclats=False)
    for sgn in (-1, 1):
        anneau_or(px, cx + sgn * (w // 2 - 30), y + 1)
    for sgn in (-1, 1):
        gx = cx + sgn * (w // 2 + 4)
        rect(px, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        agemaki(px, gx, y + 16)
        gland3(px, gx, y + 21, 10)
    arcade(px, cx, 19, "FICHE SHINOBI", 10, CREME, ancre="c", ombre=True)
    for sgn in (-1, 1):
        for X in (cx + sgn * 66, cx + sgn * 112):
            for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
                rect(px, X + dx, 22 + dy, 1, 1, hexc(c))
        rect(px, min(cx + sgn * 72, cx + sgn * 106), 22, 35, 1, hexc(R_OR[2]))


def onglet(px, tx, y_attache, etat, seed=0):
    """Onglet pendu : fil or de 1 GUI depuis y_attache, plaquette 16x16 + ombre, anneau métal.
    etat : 'repos' | 'survol' | 'actif'. Renvoie ty (haut de la plaquette)."""
    ac = etat == "actif"
    ty = y_attache + 10 + (2 if ac else 0)
    X = tx - 8
    corde_v(px, tx, y_attache, ty, "or", ep=1)
    kit.ombre_portee(px, X, ty, 16, 16, dx=1, dy=2, force=0.55, rayon=1)
    if ac:
        lq(px, X + 1, ty + 1, 14, 14, "rouge", seed=seed)
        liseret(px, X, ty, 16, 16)
    elif etat == "survol":
        lq(px, X + 1, ty + 1, 14, 14, "noir", seed=seed, intensite=1.6)
        c = hexc(R_OR[5])
        rect(px, X, ty, 16, 1, c); rect(px, X, ty + 15, 16, 1, c)
        rect(px, X, ty, 1, 16, c); rect(px, X + 15, ty, 1, 16, c)
    else:
        lq(px, X + 1, ty + 1, 14, 14, "noir", seed=seed, intensite=0.9)
        rect(px, X, ty, 16, 1, hexc(R_OR[4])); rect(px, X, ty + 15, 16, 1, hexc(R_OR[2]))
        rect(px, X, ty, 1, 16, hexc(R_OR[3])); rect(px, X + 15, ty, 1, 16, hexc(R_OR[2]))
    kit.anneau_metal(px, tx, ty, 1)
    return ty


def fermer_plaque(px, x, y, survol=False):
    """Plaque de fermeture 18x18 : laque noire, liseré or, ombre (1, 2), croix pixel or 7x7."""
    kit.ombre_portee(px, x, y, 18, 18, dx=1, dy=2, force=0.55, rayon=1)
    lq(px, x + 1, y + 1, 16, 16, "noir", seed=4, intensite=1.6 if survol else 1.0)
    liseret(px, x, y, 18, 18)
    for k in range(7):
        rect(px, x + 7 + k, y + 6 + k, 1, 1, hexc(R_OR[1]))
        rect(px, x + 7 + (6 - k), y + 6 + k, 1, 1, hexc(R_OR[1]))
    for k in range(7):
        for (a, b) in ((k, k), (6 - k, k)):
            c = R_OR[7] if survol else (R_OR[6] if b < 3 else R_OR[5])
            rect(px, x + 6 + a, y + 6 + b, 1, 1, hexc(c))


# ═════════════════════════════════════ lanternes célestes ═════════════════════════════════════
# Kanji pixel dessinés à la main (10x10 petite lanterne, 11x11 grande).
K10 = {
    "体": ["..#...#...", ".#....#...", ".#.#######", "##...###..", ".#..#.#.#.",
           ".#.#..#..#", ".#....#...", ".#..#####.", ".#....#...", ".#....#..."],
    "剣": ["...#.....#", "..#.#..#.#", ".#...#.#.#", "#.###..#.#", ".#####.#.#",
           ".#.#.#.#.#", ".#####.#.#", "...#.....#", "..#.#....#", ".#...#..##"],
    "忍": [".########.", "....#...#.", "..#.#...#.", "...##...#.", "...#....#.",
           ".##...##..", "....#.....", ".#.#.#..#.", ".#.#...#.#", "#..####..."],
    "制": ["...#.....#", ".#.#...#.#", ".#####.#.#", "#..#...#.#", ".#####.#.#",
           "...#...#.#", ".#####.#.#", ".#.#.#.#.#", ".#.#.#...#", "...#....##"],
    "力": ["....#.....", "#########.", "....#....#", "....#....#", "....#....#",
           "...#.....#", "...#.....#", "..#......#", ".#.......#", "#......##."],
    "気": [".#........", ".########.", ".#........", "#.#######.", "#########.",
           ".....#..#.", "..#.#...#.", "...##...##", "..#..#...#", ".#.......#"],
}
K11 = {
    "体": ["..#....#...", "..#....#...", ".#..#######", ".#.....#...", "##....###..", ".#...#.#.#.",
           ".#..#..#..#", ".#.....#...", ".#...#####.", ".#.....#...", ".#.....#..."],
    "剣": ["...#......#", "..#.#..#..#", ".#...#.#..#", "#.###..#..#", "...#...#..#", ".#####.#..#",
           ".#.#.#.#..#", ".#####.#..#", "...#......#", "..#.#.....#", ".#...#...##"],
    "忍": [".#########.", ".....#...#.", "...#.#...#.", "....#....#.", "...#.#...#.", ".##....##..",
           "....#......", ".....#...#.", ".#.#......#", ".#.#....#..", "#..######.."],
    "制": ["...#......#", ".#.#...#..#", ".#####.#..#", "#..#...#..#", ".#####.#..#", "...#...#..#",
           ".#####.#..#", ".#.#.#.#..#", ".#.#.#.#..#", ".#.#.#....#", "...#.....##"],
    "力": ["....#......", "....#......", "##########.", "....#....#.", "....#....#.", "....#....#.",
           "...#.....#.", "...#.....#.", "..#......#.", ".#.......#.", "#......##.."],
    "気": [".#.........", ".#########.", ".#.........", "#.#######..", "...........", "#########..",
           ".....#..#..", "..#.#...#..", "...##...#.#", "..#..#...##", ".#........#"],
}

# Flammes (2 images : la seconde est une variante d'1 px qui alterne pour l'animation).
FLAMMES = {
    "grande": ([".a.", "aba", "bcb", "bcb", ".b."], ["..a", "aba", "bcb", "bcb", ".b."]),
    "moyenne": ([".a.", "aba", "bcb"], ["a..", "aba", "bcb"]),
    "braise": (["ab", "ba"], ["ba", "ab"]),
}
FLAMME_PETITE = (["a", "b"], ["b", "a"])          # lanternes de moins de 20 GUI de large
FLAMME_COUL = {"a": (232, 116, 44), "b": (255, 176, 64), "c": (255, 246, 220)}
BRAISE_COUL = {"a": (196, 64, 28), "b": (255, 146, 58)}


def _rampe_lanterne(col):
    R = kit.rampe(hexc(col), 9, ombre=0.18, lumiere=0.75)
    return dict(contour=mix(R[0], (18, 8, 14), 0.45), t0=R[2][:3], t1=R[3][:3], t2=R[5][:3], t3=R[6][:3],
                h1=mix(R[7], (255, 226, 176), 0.35), h2=mix(R[8], (255, 238, 204), 0.55), h3=(255, 248, 230))


def _profil(W, Hp, d):
    """Demi-largeur par rangée : dôme en super-ellipse, épaules larges, base resserrée."""
    hw = []
    Wh = W / 2
    for j in range(Hp):
        t = j / (Hp - 1)
        u = min(1.0, max(0.0, (t - 0.22) / 0.78))
        h = Wh * (1 - 0.28 * (u * u * (3 - 2 * u)) ** 0.9)
        if j < d:
            k = (d - j - 0.5) / d
            h = min(h, Wh * (1 - k ** 2.4) ** (1 / 2.4))
        hw.append(h)
    return hw


def geometrie_lanterne(W, H):
    ry = 2 if H < 46 else 3
    Hp = H - ry - 2
    return dict(ry=ry, Hp=Hp, cx=(W - 1) / 2, ycen=Hp + ry - 1)


_LCACHE = {}


def lanterne_sprite(stat, W=26, H=40, L=1.0, flamme="aucune", kanji=True, seed=0):
    """Lanterne céleste pixel art (maquette tour 2, copie fidèle) : silhouette kongming, 4 côtes,
    washi 3 tons + fibres rares, arête haute éclairée, contour net, point chaud en 3 crans selon L,
    kanji pixel à l'encre brun-rouge, anneau de bambou vu d'en dessous. L : luminosité."""
    key = (stat, W, H, round(L, 3), flamme, kanji, seed)
    if key in _LCACHE:
        return _LCACHE[key]
    col, kj = (STATS[stat][3], STATS[stat][4]) if not isinstance(stat, tuple) else (hexc(stat[0]), stat[1])
    T = _rampe_lanterne(col)
    rs = np.random.RandomState(seed + 7)
    ry = 2 if H < 46 else 3
    Hp = H - ry - 2
    d = 7 if H < 46 else 9
    if W < 20:
        d = 4
    hw = _profil(W, Hp, d)
    cx = (W - 1) / 2
    yh = Hp - 3.0
    nhot = 3 if L >= 0.75 else (2 if L >= 0.5 else (1 if L >= 0.3 else 0))
    names = ["t0", "t1", "t2", "t3", "h1", "h2", "h3"]
    m = np.zeros((H, W), bool)
    idx = np.full((H, W), -1, int)
    for j in range(Hp):
        for i in range(W):
            if abs(i - cx) <= hw[j] - 0.15:
                m[j, i] = True
                u = abs(i - cx) / max(1.0, hw[j])
                b = 0.58 * (1 - u ** 2) + 0.42 * (j / (Hp - 1))
                k = 0 if b < 0.28 else (1 if b < 0.46 else (2 if b < 0.64 else 3))
                if j < 2:
                    k -= 1
                hot = math.sqrt(((i - cx) / (W * 0.20)) ** 2 + ((j - (yh - 1.5)) / (Hp * 0.22)) ** 2)
                if nhot >= 1 and hot < 1.0:
                    k = 4
                if nhot >= 2 and hot < 0.64:
                    k = 5
                if nhot >= 3 and hot < 0.34:
                    k = 6
                idx[j, i] = max(0, k)
    cote = np.zeros((H, W), bool)
    fracs = (-0.84, -0.40, 0.40, 0.84) if W >= 20 else (-0.45, 0.45)
    for frac in fracs:
        for j in range(2, Hp):
            i = int(round(cx + frac * hw[j]))
            if 0 <= i < W and m[j, i]:
                cote[j, i] = True
                if i + 1 < W and m[j, i + 1] and idx[j, i + 1] < 4:
                    idx[j, i + 1] = max(0, idx[j, i + 1] - 1)
    if W >= 20:
        for _ in range(int(W * Hp / 75)):
            i, j = rs.randint(2, W - 2), rs.randint(d, Hp - 4)
            for jj in (j, j + 1):
                if m[jj, i] and not cote[jj, i] and 1 <= idx[jj, i] <= 3:
                    idx[jj, i] = min(3, idx[jj, i] + 1)
    p = np.zeros((H, W, 4), np.uint8)
    for j in range(H):
        for i in range(W):
            if m[j, i]:
                k = idx[j, i]
                c = T[names[k]]
                if cote[j, i]:
                    c = mix(T[names[max(0, k - 2)]], (40, 18, 12), 0.15) if k < 4 else T[names[k - 1]]
                p[j, i, :3] = c
                p[j, i, 3] = 255
    for i in range(W):
        if m[Hp - 1, i]:
            p[Hp - 1, i, :3] = mix(p[Hp - 1, i], T["contour"], 0.35)
    p[_bord(m), :3] = T["contour"]
    for i in range(W):
        c_ = np.nonzero(m[:, i])[0]
        if len(c_) > 3 and c_[0] <= 1 and abs(i - cx) < W * 0.30:
            p[c_[0] + 1, i, :3] = mix(T["t3"], (255, 240, 210), 0.30)
    if kanji and kj:
        g = (K11 if W >= 32 else K10).get(kj)
        if g:
            n = len(g)
            gy0 = int(round(Hp * 0.42 - n / 2))
            gx0 = int(round(cx - (n - 1) / 2))
            for jj, row in enumerate(g):
                for ii, ch in enumerate(row):
                    if ch == "#":
                        y2, x2 = gy0 + jj, gx0 + ii
                        if m[y2, x2]:
                            p[y2, x2, :3] = ENCRE_LANTERNE      # trait plein d'1 texel, opaque (tour 3)
    ycen = Hp + ry - 1
    rx = hw[-1] + 0.6
    bb = {"o": (36, 22, 12), "d": (78, 52, 30), "m": (142, 100, 56), "l": (204, 164, 108)}
    for j in range(Hp - 2, H):
        for i in range(W):
            e = ((i - cx) / rx) ** 2 + ((j - ycen) / (ry + 0.5)) ** 2
            if e <= 1.0:
                inner = ((i - cx) / (rx - 1.6)) ** 2 + ((j - ycen) / (ry - 0.6)) ** 2
                if inner < 1.0 and j >= ycen - ry + 1:
                    g_ = 1 - inner
                    c = mix((30, 12, 8), (120, 52, 20), (0.55 if g_ > 0.5 else 0.25) * (1 if L > 0.45 else 0.4))
                    p[j, i, :3] = c
                elif j <= ycen:
                    p[j, i, :3] = bb["d"]
                else:
                    p[j, i, :3] = bb["l"] if (j <= ycen + 1) else (bb["m"] if j < ycen + ry else bb["d"])
                p[j, i, 3] = 255
    mm = p[..., 3] > 0
    rb = _bord(mm)
    rb[:Hp, :] = False
    p[rb, :3] = bb["o"]
    if W >= 20:
        for frac in (-0.62, 0.62):
            i = int(round(cx + frac * rx))
            for j in (ycen + 1, ycen + 2):
                if 0 <= j < H and p[j, i, 3]:
                    p[j, i, :3] = (122, 19, 34)
    if flamme != "aucune":
        _poser_flamme(p, W, H, flamme, 0)
    if L < 0.98:
        a = p[..., :3].astype(float)
        lum = a.mean(axis=2, keepdims=True)
        k = 1 - L
        a = a + (lum - a) * 0.30 * k
        a = a * (1 - 0.55 * k)
        p[..., :3] = np.clip(a, 0, 255).astype(np.uint8)
    _LCACHE[key] = p
    return p


def _poser_flamme(p, W, H, flamme, image):
    """Pose la flamme (image 0 ou 1) dans le tableau p (H, W, 4), au-dessus de l'anneau."""
    g = geometrie_lanterne(W, H)
    if W < 20 and flamme in ("grande", "moyenne"):
        fl = FLAMME_PETITE[image]
    else:
        fl = FLAMMES[flamme][image]
    fc = BRAISE_COUL if flamme == "braise" else FLAMME_COUL
    fx0 = int(round(g["cx"] - (len(fl[0]) - 1) / 2))
    for jj, row in enumerate(fl):
        for ii, ch in enumerate(row):
            if ch in fc:
                y2 = g["ycen"] + 1 - len(fl) + jj
                p[y2, fx0 + ii, :3] = fc[ch]
                p[y2, fx0 + ii, 3] = 255


def flamme_calque(W, H, flamme, image):
    """Calque PLEIN FORMAT (W x H) transparent hors de la flamme, à poser sur la lanterne."""
    p = np.zeros((H, W, 4), np.uint8)
    _poser_flamme(p, W, H, flamme, image)
    return p


def silhouette_pointillee(W, H, couleur=None):
    """Contour de la silhouette de la lanterne en pointillé or (pixels où x+y est pair)."""
    spr = lanterne_sprite(0, W, H, 1.0, "aucune", False)
    b = _bord(spr[..., 3] > 0)
    out = np.zeros((H, W, 4), np.uint8)
    ys, xs = np.nonzero(b)
    col = hexc(couleur or OR)
    for y, x in zip(ys, xs):
        if (x + y) % 2 == 0:
            out[y, x] = col
    return out


def lanterne_moyenne(bg, cx, cy, L=0.62, seed=40, lumiere_seule=False, sprite_seul=False):
    """Lanterne moyenne ambrée 12x18 sans kanji, cuite dans le décor : lumière quantifiée, halo
    en 2 anneaux pleins + cœur chaud (bord net d'1 GUI), puis le sprite à flamme moyenne."""
    W, H = 12, 18
    col = hexc("E8963C")
    x0, y0 = int(cx - W // 2), int(cy - H // 2)
    g = geometrie_lanterne(W, H)
    hx, hy = x0 + W / 2, y0 + g["Hp"] - 4
    if not sprite_seul:
        lumiere(bg, hx, hy, W * 2.3, col, force=(0.05 + 0.12 * L) * 0.6, niveaux=4)
    if lumiere_seule:
        return
    c = desat(col, 0.8)
    k = 0.7 * (0.4 + 0.6 * L)
    halo_anneaux_px(bg, hx, hy, [(W * 0.56, 0.11 * k), (W * 0.82, 0.06 * k)], c)
    if L >= 0.5:
        halo_anneaux_px(bg, hx, y0 + H - 3, [(W * 0.30, 0.16 * 0.7)], "FFBE6E")
    spr = lanterne_sprite(("E8963C", None), W, H, L, "moyenne", False, seed)
    coller(bg, spr, x0, y0)


# ═════════════════════════════════════ hitodama ═════════════════════════════════════
def hitodama_sprite(frame=0):
    """Flamme-esprit 12x20 : cœur blanc, langue bleu-violet pâle en virgule, contour #161A44."""
    W, H = 12, 20
    yy, xx = np.mgrid[0:H, 0:W].astype(float) + 0.5
    hx, hy, hr = 6.0, 6.4, 4.4
    d_tete = np.sqrt((xx - hx) ** 2 + (yy - hy) ** 2)
    mask = d_tete <= hr
    ondule = 0.5 if frame else 0.0
    for k in range(40):
        s = k / 39
        tx = hx + 0.6 - 1.2 * s - 3.6 * s ** 2 + ondule * math.sin(s * 3.1)
        ty = hy + 2.0 + 11.0 * s
        tr = 3.7 * (1 - s) ** 1.25 + 0.35
        dd = np.sqrt((xx - tx) ** 2 + (yy - ty) ** 2) / tr
        mask |= dd <= 1.0
    p = np.zeros((H, W, 4), np.uint8)
    C = {"blanc": hexc("FFF8EC"), "creme": hexc("E8F0FF"), "pale": hexc("BFD0FF"), "bleu": hexc("8E9CF2"),
         "violet": hexc("7464D2"), "sombre": hexc("4A3EA8"), "contour": hexc("161A44")}
    tone = np.full((H, W), "violet", object)
    tone[d_tete <= hr] = "bleu"
    tone[d_tete <= hr - 1.1] = "pale"
    tone[(d_tete <= 2.4)] = "creme"
    tone[(np.sqrt((xx - hx + 0.6) ** 2 + (yy - hy + 0.7) ** 2) <= 1.5)] = "blanc"
    queue = (d_tete > hr) & mask
    tone[queue & (yy > 14)] = "sombre"
    for j in range(H):
        for i in range(W):
            if mask[j, i]:
                p[j, i] = C[tone[j, i]]
    dil = mask | np.roll(mask, 1, 0) | np.roll(mask, -1, 0) | np.roll(mask, 1, 1) | np.roll(mask, -1, 1)
    p[dil & ~mask] = C["contour"]
    e = mask & np.roll(mask, 1, 0) & np.roll(mask, -1, 0) & np.roll(mask, 1, 1) & np.roll(mask, -1, 1)
    p[mask & ~e & (yy < hy)] = C["bleu"]
    return p


def badge_hitodama(px, x, y):
    """Badge 5x7 : petite hitodama (plaquette ATTRIBUTS)."""
    spr = [".aaa.", "abcba", "abccb", ".abb.", "..ab.", "...a.", "....a"]
    pal = {"a": hexc("4A3EA8"), "b": hexc("BFD0FF"), "c": hexc("FFF8EC")}
    for j, row in enumerate(spr):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(px, x + i, y + j, 1, 1, pal[ch])
    for j, row in enumerate(spr):
        for i, ch in enumerate(row):
            if ch == ".":
                voisin = any(0 <= j + dy < 7 and 0 <= i + dx < 5 and spr[j + dy][i + dx] != "."
                             for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if voisin:
                    rect(px, x + i, y + j, 1, 1, hexc("161A44"))


# ═════════════════════════════════════ ATTRIBUTS : ema, chevilles ═════════════════════════════════════
EMA_W, EMA_H = 84, 48


def ema_px(img, x, y, stat, lumiere_k=0.6, survol=False, seed=0, chevilles=True):
    """Plaque ema 84x48 en bois sombre, toit à pignon et chaperon laqué, clou forgé au faîte,
    deux trous de cordon ; éclairée par le haut (3 crans, couleur de la stat) ; ombre 2 GUI.
    Aucune jauge."""
    W, H = EMA_W, EMA_H
    col = np.array(STATS[stat][3][:3], float)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    toit = 10
    pente = (np.abs(xx - (W - 1) / 2) / ((W - 1) / 2)) * (toit - 1)
    mask = (yy >= pente) & (yy < H)
    mask[H - 1, 0] = mask[H - 1, W - 1] = False
    tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    kit.bois(tmp, 0, 0, W, H, "moyen", sens="h", seed=seed + 17, noeuds=0, biseau=False)
    p = np.asarray(tmp).astype(float).copy()
    p[..., :3] *= 0.58
    for (y_a, y_b, f) in ((toit, toit + 5, 0.30), (toit + 5, toit + 10, 0.18), (toit + 10, toit + 15, 0.09)):
        band = mask & (yy >= y_a) & (yy < y_b)
        p[band, :3] = p[band, :3] * (1 - f * 0.4) + col * f * lumiere_k + 6 * f
    toitm = mask & (yy < toit)
    p[toitm, :3] = p[toitm, :3] * 0.55 + np.array([20, 10, 12]) * 0.45
    for d_ in (0, 1):
        band = mask & (yy >= pente + d_ - 0.01) & (yy < pente + d_ + 1) & (yy < toit + 1)
        p[band, :3] = np.minimum(255, p[band, :3] * 0.6 + col * (0.45 if d_ == 0 else 0.22) * lumiere_k + 30 * (1 - d_))
    p[toit, :, :3] *= 0.45
    p = np.clip(p, 0, 255).astype(np.uint8)
    b = _bord(mask)
    p[b] = hexc("1A0E08")
    if survol:
        p[b] = hexc(R_OR[6])
        b2 = _bord(mask & ~b)
        p[b2] = hexc(R_OR[3])
    p[toit + 1, 2:W - 2, :3] = np.minimum(255, p[toit + 1, 2:W - 2, :3].astype(int) + 22).astype(np.uint8)
    p[~mask] = 0
    for (tx, ty) in ((20, 7), (63, 7)):
        p[ty, tx] = hexc("0E0806"); p[ty, tx + 1] = hexc("1E120A")
        p[ty + 1, tx] = hexc("3A2414"); p[ty + 1, tx + 1] = hexc("4E3220")
    cxn = W // 2
    for (dx, dy, c) in ((-1, 2, "2A2626"), (0, 2, "8A847E"), (1, 2, "3A3634"), (-1, 3, "3A3634"),
                        (0, 3, "C2BCB4"), (1, 3, "2A2626"), (0, 4, "1A1616")):
        p[dy, cxn + dx] = hexc(c)
    kit.ombre_portee(img, x, y, W, H, dx=2, dy=2, force=0.6, rayon=1, masque=mask)
    coller(img, p, x, y)
    if chevilles:
        for k, sig in enumerate("-+"):
            cheville(img, x + W - 25 + k * 12, y + H - 13, sig)
    return dict(clou=(x + cxn, y + 2), trous=[(x + 20, y + 7), (x + 63, y + 7)], texte=(x + 8, y + 14))


def cheville(img, x, y, signe, etat="repos"):
    """Cheville laquée 9x9 (coins abattus), glyphe or, ombre d'1 GUI.
    etat : 'repos' (cuite dans l'ema) | 'survol' | 'presse' | 'inactif' (voilée à 50 %)."""
    m_ = np.ones((9, 9), bool)
    for (a, b) in ((0, 0), (0, 8), (8, 0), (8, 8)):
        m_[a, b] = False
    p = np.zeros((9, 9, 4), np.uint8)
    Lq = [hexc(c) for c in R_LAQUE]
    o = hexc(R_OR[6])
    dy = 0
    if etat == "survol":
        p[m_] = Lq[4]
        p[1:4, 1:5] = Lq[6]
        p[1, 2:4] = Lq[8]
        p[0, 1:8] = hexc(R_OR[7]); p[1:8, 0] = hexc(R_OR[5])
        p[8, 1:8] = BISEAU_SOMBRE; p[1:8, 8] = BISEAU_SOMBRE
        o = hexc(R_OR[8])
    elif etat == "presse":
        p[m_] = Lq[1]
        p[0, 1:8] = BISEAU_SOMBRE; p[1:8, 0] = BISEAU_SOMBRE       # biseau inversé : enfoncée
        p[8, 1:8] = BISEAU_CLAIR; p[1:8, 8] = hexc(R_OR[4])
        o = hexc(R_OR[5])
        dy = 1
    else:
        p[m_] = Lq[2]
        p[1:4, 1:5] = Lq[4]
        p[1, 2:4] = Lq[6]
        p[0, 1:8] = BISEAU_CLAIR; p[1:8, 0] = hexc(R_OR[4])
        p[8, 1:8] = BISEAU_SOMBRE; p[1:8, 8] = BISEAU_SOMBRE
    p[4 + dy, 2:7] = o
    p[5 + dy, 3:7] = hexc(R_LAQUE[0])
    if signe == "+":
        p[2 + dy:7 + dy, 4] = o
        p[5 + dy, 4] = o
    p[~m_] = 0
    if etat == "inactif":
        f = np.array(PAL["fond"][:3], float)
        q = p[..., :3].astype(float)
        p[..., :3] = (q * 0.5 + f * 0.5).astype(np.uint8)
    kit.ombre_portee(img, x, y, 9, 9, dx=1, dy=1, force=0.45, rayon=0)
    coller(img, p, x, y)


# ═════════════════════════════════════ boutons ═════════════════════════════════════
def bouton_fond(px, x, y, w, h, etat="normal"):
    """Bouton-plaque sans libellé (maquette bouton()), ombre (1, 2) en paliers d'1 GUI.
    etat : 'normal' | 'survol' | 'actif' | 'inactif' | 'compteur'."""
    if etat == "actif":
        kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.6, rayon=1)
        lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=7)
        liseret(px, x, y, w, h)
        kanagus(px, x, y, w, h)
    elif etat == "inactif":
        rect(px, x, y, w, h, hexc("140D0F"))
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=7, intensite=0.3)
        c = hexc("3A2C26")
        rect(px, x, y, w, 1, c); rect(px, x, y + h - 1, w, 1, c)
        rect(px, x, y, 1, h, c); rect(px, x + w - 1, y, 1, h, c)
    elif etat == "compteur":
        kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.6, rayon=1)
        rect(px, x - 1, y - 1, w + 2, h + 2, PAL["fond"])
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=5, intensite=1.2)
        liseret(px, x, y, w, h)
        kanagus(px, x, y, w, h)
    else:
        kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.5, rayon=1)
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=7, intensite=1.6 if etat == "survol" else 0.9)
        liseret(px, x, y, w, h)


# ═════════════════════════════════════ TECHNIQUES ═════════════════════════════════════
def sceau_rang(px, x, y, r, taille=12):
    """Sceau de rang : plaquette laquée, liseré d'1 GUI à la couleur du rang, lettre ArcadePix x1
    or ombrée ; r=None : BASE (liseré R_OR[2], kanji 基 posé en texte par le code)."""
    kit.ombre_portee(px, x, y, taille, taille, dx=1, dy=1, force=0.5, rayon=0)
    lq(px, x + 1, y + 1, taille - 2, taille - 2, "noir", seed=taille + (ord(r) if r else 0), intensite=1.2)
    c = hexc(RANG_COL[r]) if r else hexc(R_OR[2])
    rect(px, x, y, taille, 1, c); rect(px, x, y + taille - 1, taille, 1, c)
    rect(px, x, y, 1, taille, c); rect(px, x + taille - 1, y, 1, taille, c)
    if r:
        lw = kit.arcade_largeur(r, 10)
        arcade(px, x + (taille - lw + 1) // 2, y + (taille - 7) // 2, r, 10, OR, ombre=True)


def sceau_stat(px, x, y, st, taille=12):
    """Mini-sceau de stat : plaquette noire, liseré or, pastille 2x2 de la stat (kanji en texte)."""
    plaquette(px, x, y, taille, taille, "noir", seed=st + 3)
    rect(px, x + taille - 4, y + taille - 4, 2, 2, desat(STATS[st][3], 0.6))


def cran_maitrise(px, X, Y, plein):
    """Cran de maîtrise 6x6 (maquette)."""
    rect(px, X, Y, 5, 1, hexc("050304"))
    rect(px, X, Y, 1, 5, hexc("050304"))
    if plein:
        rect(px, X + 1, Y + 1, 4, 3, hexc(R_OR[4]))
        rect(px, X + 1, Y + 1, 4, 1, hexc(R_OR[6]))
    else:
        rect(px, X + 1, Y + 1, 4, 3, hexc("1C1214"))
    rect(px, X, Y + 5, 5, 1, hexc("4A3438"))
    rect(px, X + 5, Y, 1, 6, hexc("3A2A2C"))


def ligne_choisie(px, x0, yy, w):
    """Ligne choisie : bande de laque rouge sombre (x0+6, yy−9, w−20, 18), liserés or, signet
    agemaki + gland de 11 en x0+2."""
    bx, by, bw, bh = x0 + 6, int(yy) - 9, w - 20, 18
    lq(px, bx, by, bw, bh, ["2A070D", "3A0A12", "4E0D17", "63111D"], seed=4)
    rect(px, bx, by, bw, 1, hexc(R_OR[5]))
    rect(px, bx, by + 1, bw, 1, hexc("120608"))
    rect(px, bx, by + bh - 1, bw, 1, hexc(R_OR[4]))
    agemaki(px, x0 + 2, by + 4)
    gland3(px, x0 + 2, by + 9, 11)


# ═════════════════════════════════════ ÉTAT ═════════════════════════════════════
MECHES = {
    "vive": (["...a....", "...ab...", "..abb...", "..abcb..", ".abccb..", ".bccb...", "..bb....", "..dddd.."],
             ["....a...", "...ab...", "..abb...", "..abcb..", ".abccb..", ".bccb...", "..bb....", "..dddd.."]),
    "vacillante": (["........", ".....a..", "....ab..", "...abb..", "..abcb..", ".abcb...", "..bb....", "..dddd.."],
                   ["........", "........", "...ab...", "...abb..", "..abcb..", ".abcb...", "..bb....", "..dddd.."]),
    "faible": (["........", "........", "........", "........", "...a....", "..aba...", "...b....", "..dddd.."],
               ["........", "........", "........", "........", "........", "...a....", "..ab....", "..dddd.."]),
}


def meche(px, x, y, etat="vive", couleur="E8742C", image=0):
    """Mèche pixel 8x8 : vive / vacillante / faible ; image 1 = variante qui vacille."""
    spr = MECHES[etat][image]
    R = kit.rampe(couleur, 7, ombre=0.25, lumiere=0.8)
    pal = {"a": R[2], "b": R[4], "c": hexc("FFF4DC"), "d": hexc("4A3018")}
    for j, row in enumerate(spr):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(px, int(x) + i, int(y) + j, 1, 1, pal[ch])


def sceau_blessure_fond(px, x, y, rouge=False, seed=0):
    """Fond du petit sceau laqué 9x9 de blessure (liseré or) ; le kanji est posé à part."""
    kit.ombre_portee(px, x, y, 9, 9, dx=1, dy=1, force=0.55, rayon=0)
    lq(px, x + 1, y + 1, 7, 7, "rouge" if rouge else "noir", seed=seed, intensite=1.3)
    liseret(px, x, y, 9, 9)


def carte_blessure_fond(px, x, y, w, h, survole=False, seed=0):
    """Carte laquée d'une blessure, sans texte ni sceau : laque 1,2 (1,6 au survol), bords
    R_OR[3]/R_OR[1] au repos, liseré + kanagu au survol, ombre (1, 2), anneau or à gauche."""
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.5, rayon=1)
    lq(px, x, y, w, h, "noir", seed=seed, intensite=1.6 if survole else 1.2)
    if survole:
        liseret(px, x, y, w, h)
        kanagus(px, x, y, w, h)
    else:
        rect(px, x, y, w, 1, hexc(R_OR[3])); rect(px, x, y + h - 1, w, 1, hexc(R_OR[1]))
        rect(px, x, y, 1, h, hexc(R_OR[3])); rect(px, x + w - 1, y, 1, h, hexc(R_OR[1]))
    anneau_or(px, x, y + h // 2)


# ═════════════════════════════════════ tour 3 : pièces de la maquette v2 ═════════════════════════════════════
R_VERM = ["4E0C0B", "6E1511", "8E2018", "AE2C21", "C23A2C", "D2523E", "E0705A"]
LAQUE_ROUGE_SOMBRE = ["1E0A0D", "260C10", "2E0E13", "361117", "3E141A", "46171E"]


def ferrures3(px, x, y, w, h, terne=False):
    """Petites ferrures d'angle 3x3 GUI (boutons, plaquettes) : équerre or + rivet (maquette)."""
    c0, c1, c2 = (BISEAU_CLAIR, OR, hexc(R_OR[3])) if not terne else (hexc(R_OR[4]), hexc(R_OR[3]), hexc(R_OR[2]))
    for (a, b, sx, sy) in ((x, y, 1, 1), (x + w - 1, y, -1, 1), (x, y + h - 1, 1, -1), (x + w - 1, y + h - 1, -1, -1)):
        rect(px, a, b, 1, 1, c0)
        rect(px, a + sx, b, 1, 1, c1)
        rect(px, a + 2 * sx, b, 1, 1, c2)
        rect(px, a, b + sy, 1, 1, c1)
        rect(px, a, b + 2 * sy, 1, 1, c2)
        rect(px, a + sx, b + sy, 1, 1, c2)


def liseret_terne(img, x, y, w, h):
    """Liseré or éteint (boutons inactifs)."""
    rect(img, x, y, w, 1, hexc(R_OR[3]))
    rect(img, x, y, 1, h, hexc(R_OR[3]))
    rect(img, x, y + h - 1, w, 1, hexc(R_OR[1]))
    rect(img, x + w - 1, y, 1, h, hexc(R_OR[1]))


def perche(px, x0, x1, y, bout="d"):
    """Perche de bambou laqué noir (6 GUI) : nœuds irréguliers, embout de laiton biseauté du côté
    de la scène (maquette perche())."""
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


def plaquette22(px, X, ty, etat, seed=0, T=22):
    """Plaquette d'onglet / de fermeture T×T (maquette entete()/fermer()) : ombre (1, 2), laque,
    liseré. etat : 'repos' | 'survol' | 'actif' | 'fermer' | 'fermer_survol'."""
    kit.ombre_portee(px, X, ty, T, T, dx=1, dy=2, force=0.55, rayon=1)
    if etat == "actif":
        lq(px, X + 1, ty + 1, T - 2, T - 2, "rouge", seed=seed)
        liseret(px, X, ty, T, T)
        ferrures3(px, X, ty, T, T)
    elif etat in ("fermer", "fermer_survol"):
        lq(px, X + 1, ty + 1, T - 2, T - 2, "noir", seed=4, intensite=1.8 if etat == "fermer_survol" else 1.2)
        liseret(px, X, ty, T, T)
        ferrures3(px, X, ty, T, T)
    elif etat == "survol":
        lq(px, X + 1, ty + 1, T - 2, T - 2, "noir", seed=seed, intensite=1.7)
        c = hexc(R_OR[5])
        rect(px, X, ty, T, 1, BISEAU_CLAIR); rect(px, X, ty + T - 1, T, 1, c)
        rect(px, X, ty, 1, T, BISEAU_CLAIR); rect(px, X + T - 1, ty, 1, T, c)
    else:
        lq(px, X + 1, ty + 1, T - 2, T - 2, "noir", seed=seed, intensite=0.9)
        rect(px, X, ty, T, 1, hexc(R_OR[4])); rect(px, X, ty + T - 1, T, 1, hexc(R_OR[2]))
        rect(px, X, ty, 1, T, hexc(R_OR[3])); rect(px, X + T - 1, ty, 1, T, hexc(R_OR[2]))


def cabochon_fond(px, x, y, w, h=11):
    """Touche dessinée (maquette cabochon()) : laque noire, coins abattus, liseré or éteint, ombre."""
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
    lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=x, intensite=1.2)
    rect(px, x + 1, y, w - 2, 1, hexc(R_OR[4])); rect(px, x + 1, y + h - 1, w - 2, 1, hexc(R_OR[2]))
    rect(px, x, y + 1, 1, h - 2, hexc(R_OR[3])); rect(px, x + w - 1, y + 1, 1, h - 2, hexc(R_OR[2]))


def creneau_bouton(px, x, y, w, h, etat):
    """Emplacement de bouton dans la barre d'actions : 'normal' | 'survol' | 'actif' (laque rouge)
    | 'inactif' | 'rouge_inactif'."""
    rect(px, x - 1, y - 1, w + 2, h + 2, PAL["fond"])
    if etat == "actif":
        lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=w, intensite=1.2)
        liseret(px, x, y, w, h)
        ferrures3(px, x, y, w, h)
    elif etat == "rouge_inactif":
        lq(px, x + 1, y + 1, w - 2, h - 2, LAQUE_ROUGE_SOMBRE, seed=w, intensite=0.6)
        liseret_terne(px, x, y, w, h)
        ferrures3(px, x, y, w, h, terne=True)
    elif etat == "inactif":
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=w, intensite=0.35)
        liseret_terne(px, x, y, w, h)
        ferrures3(px, x, y, w, h, terne=True)
    else:
        lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=w, intensite=1.8 if etat == "survol" else 1.1)
        liseret(px, x, y, w, h)
        ferrures3(px, x, y, w, h)


def pastille_rang(px, x, y, r, taille=18):
    """Pastille de rang (maquette sceau_rang()) : laque aux couleurs du rang D/C/B/A/S, grosse lettre
    ArcadePix x1 crème ombrée ; r=None : pastille neutre papier (le 基 est posé en texte x2)."""
    if r is None:
        R = [hexc(c) for c in ("3A2E1E", "6A5A40", "C2B28E", "C8B894", "CCBC98", "E2D6B6")]
    else:
        R = [tuple(int(v) for v in c[:3]) for c in kit.rampe(RANG_COL[r], 6, ombre=0.30, lumiere=0.35)]
    kit.ombre_portee(px, x, y, taille, taille, dx=1, dy=1, force=0.55, rayon=0)
    lq(px, x + 1, y + 1, taille - 2, taille - 2, R, seed=ord(r) if r else 3, intensite=1.0)
    rect(px, x, y, taille, 1, R[4]); rect(px, x, y, 1, taille, R[4])
    rect(px, x, y + taille - 1, taille, 1, R[0]); rect(px, x + taille - 1, y, 1, taille, R[0])
    if r:
        lw = kit.arcade_largeur(r, 10)
        arcade(px, x + (taille - lw + 1) // 2, y + (taille - 7) // 2, r, 10, hexc("F2E6CC"), ombre=True)


def marqueur_blessure(px, x, y, survol=False, taille=18):
    """Gros marqueur de blessure en laque vermillon (maquette hanko_px R_VERM) sans kanji (posé en
    texte x2 par le code) : biseau clair en haut à gauche, sombre en bas à droite, ombre."""
    R = [hexc(c) for c in R_VERM]
    kit.ombre_portee(px, x, y, taille, taille, dx=1, dy=1, force=0.6, rayon=0)
    lq(px, x + 1, y + 1, taille - 2, taille - 2, R[1:6], seed=17, intensite=1.5 if survol else 1.0)
    rect(px, x, y, taille, 1, R[5] if not survol else BISEAU_CLAIR)
    rect(px, x, y, 1, taille, R[5] if not survol else BISEAU_CLAIR)
    rect(px, x, y + taille - 1, taille, 1, R[0] if not survol else BISEAU_SOMBRE)
    rect(px, x + taille - 1, y, 1, taille, R[0] if not survol else BISEAU_SOMBRE)
    if survol:
        ferrures3(px, x, y, taille, taille)


def estrade(px, ox=0, oy=0):
    """Estrade sous le personnage (variante en jeu, maquette estrade()) : plateau de laque noire en
    perspective, arête or, tranche de 6 GUI laquée rouge, ferrures, ombre. Repère : (ox, oy) = (cx−76,
    pieds−14) ; occupe 156×26."""
    y_top, y_av, ep = 0, 16, 6
    kit.ombre_portee(px, ox, oy + y_av, 152, ep, dx=2, dy=2, force=0.65, rayon=2)
    for y in range(y_top, y_av):
        t = (y - y_top) / (y_av - y_top)
        half = 64 + 12 * t
        x0, x1 = int(round(76 - half)), int(round(76 + half))
        c = mix(R_LAQUE[3], R_LAQUE[6], 0.25 + 0.55 * t)
        rect(px, ox + x0, oy + y, x1 - x0, 1, c)
        rect(px, ox + x0, oy + y, 1, 1, hexc(R_OR[3])); rect(px, ox + x1 - 1, oy + y, 1, 1, hexc(R_OR[2]))
    rect(px, ox + 12, oy + y_top, 128, 1, hexc(R_OR[4]))
    for x in range(32, 126, 22):
        rect(px, ox + x, oy + y_top + 2, 1, y_av - y_top - 3, hexc(R_LAQUE[1]))
    lq(px, ox, oy + y_av, 152, ep, "rouge", seed=3)
    rect(px, ox, oy + y_av, 152, 1, BISEAU_CLAIR)
    rect(px, ox, oy + y_av + ep - 1, 152, 1, hexc("1A0608"))
    ferrures3(px, ox, oy + y_av, 152, ep)


def halo_trame(R, expo=1.7, niveaux=6):
    """Halo de lanterne pré-dessiné : disque blanc de rayon R, alpha (1−d)^expo quantifié en
    `niveaux` crans et TRAMÉ (Bayer 4x4) sur toute la pente : aucun anneau en escalier. Centre au
    texel (R, R) ; taille 2R+1."""
    n = 2 * R + 1
    yy, xx = np.mgrid[0:n, 0:n].astype(float)
    d = np.sqrt((xx - R) ** 2 + (yy - R) ** 2) / (R + 0.5)
    A = np.clip(1 - d, 0, 1) ** expo
    q = kit.quantifier(A, niveaux + 1, 0, 0, tramage=1.0) / niveaux
    a = np.zeros((n, n, 4), np.uint8)
    a[..., :3] = 255
    a[..., 3] = np.round(q * 255).astype(np.uint8)
    return a
