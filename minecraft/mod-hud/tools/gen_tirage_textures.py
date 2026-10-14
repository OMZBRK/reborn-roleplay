# -*- coding: utf-8 -*-
"""
gen_tirage_textures.py — test de la Feuille refait (14/10) : la feuille de papier à chakra et sa réaction selon la
nature, image par image (menu/tirage/TirageScreen). Aucun kanji (DA §3), pixel art léger (DA §6) : chaque image
de la réaction est une case d'atlas affichée en UN blit ; le code ne fait que choisir la case.

Réactions (canon) :
  - Katon  : la feuille s'enflamme par le bas et se consume ; il reste un coin carbonisé, des cendres sur le plateau ;
  - Fūton  : elle se fend en deux, les deux moitiés s'écartent dans un souffle ;
  - Raiton : des arcs crépitent et elle se froisse en boule à facettes ;
  - Doton  : elle prend la couleur de la terre, se craquelle et s'effrite en poussière sur le plateau ;
  - Suiton : une tache humide gagne toute la feuille, qui se détrempe, s'affaisse et goutte sur le plateau.
  Double nature : le code coupe chaque image en bandes verticales (une par nature, UV) — les cases ont toutes la
  même feuille au même endroit, la jointure ne se voit pas.

1 texel = 1 GUI. Contrat :
- feuille.png              96×112 : la feuille intacte au-dessus de son plateau (confirmation, concentration).
- feuille_<nature>.png    768×224 : 16 images 96×112 (8 colonnes × 2 rangées, ordre de lecture), 110 ms chacune ;
                                    la dernière reste affichée au résultat.
  Dans une case : feuille 48×64 en (24, 24) ; plateau (sanbō) de y = 98 à 110, surface à y = 98.
- halo.png                  64×64 : halo doux blanc (teinté et étiré par le code : lueur de chakra, voile de mise au
                                    point derrière la colonne).
- bouton.png               100×40 : plaque laquée fine 96×16, repos (rangée 0) / survol (rangée 1, pas de 20).
GIF de contrôle : Maquettes/test_feuille/reaction_<nature>.gif (+ double nature).

Usage (depuis minecraft/mod-hud) :  python tools/gen_tirage_textures.py
"""
from __future__ import annotations

import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import jeu_art as ja  # noqa: E402
from jeu_art import ea, kit, hexc, nouveau  # noqa: E402

S = ea.Sortie("tirage", budget=400_000)
MAQ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..",
                                    "Maquettes", "test_feuille"))

LW, LH = 48, 64          # feuille
CW, CH = 96, 112         # case
OX, OY = 24, 24          # feuille dans la case
SOL = 98                 # surface du plateau
NF, COLS = 16, 8
SS = 4                   # sur-échantillonnage pour les déformations (Fūton, Raiton, Suiton)
NATURES = ("katon", "suiton", "doton", "futon", "raiton")


# ═══════════════════════════════════════ outils ═══════════════════════════════════════
def c(h, a=1.0):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)] + [a], np.float32)


def lisse(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def ease(x):
    x = min(1.0, max(0.0, x))
    return 1 - (1 - x) ** 3


def vide(w, h):
    return np.zeros((h, w, 4), np.float32)


def over(dst, src, x=0, y=0):
    """Composite src (RGBA float, alpha droit) sur dst en (x, y), découpé aux bords."""
    h, w = src.shape[:2]
    H, W = dst.shape[:2]
    x0, y0, x1, y1 = max(0, x), max(0, y), min(W, x + w), min(H, y + h)
    if x0 >= x1 or y0 >= y1:
        return
    s = src[y0 - y:y1 - y, x0 - x:x1 - x]
    d = dst[y0:y1, x0:x1]
    sa = s[..., 3:4]
    da = d[..., 3:4]
    oa = sa + da * (1 - sa)
    rgb = np.where(oa > 1e-6, (s[..., :3] * sa + d[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-6), 0)
    d[..., :3], d[..., 3:4] = rgb, oa


def point(dst, x, y, col, a=None):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < dst.shape[1] and 0 <= y < dst.shape[0]:
        p = col.copy()
        if a is not None:
            p[3] = a
        over(dst, p.reshape(1, 1, 4), x, y)


def ligne(dst, pts, col, a=None):
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        n = int(max(abs(xb - xa), abs(yb - ya))) + 1
        for k in range(n + 1):
            s = k / max(1, n)
            point(dst, xa + (xb - xa) * s, ya + (yb - ya) * s, col, a)


def agrandir(a, k=SS):
    return np.repeat(np.repeat(a, k, axis=0), k, axis=1)


def reduire(a, k=SS):
    """Moyenne k×k en alpha prémultiplié, puis alpha franchi (bords nets, pas de halo baveux)."""
    h, w = a.shape[0] // k, a.shape[1] // k
    pm = a[..., :3] * a[..., 3:4]
    rgb = pm.reshape(h, k, w, k, 3).mean(axis=(1, 3))
    al = a[..., 3:4].reshape(h, k, w, k, 1).mean(axis=(1, 3))
    rgb = np.where(al > 1e-5, rgb / np.maximum(al, 1e-5), 0)
    al = np.where(al >= 0.5, np.maximum(al, 0.0), 0.0)
    al = np.where(al >= 0.5, np.minimum(1.0, al * 1.35), al)
    return np.concatenate([rgb, np.clip(al, 0, 1)], -1).astype(np.float32)


def echantillon(img, sx, sy):
    """Lecture au plus proche (coordonnées en pixels de img), 0 hors de l'image."""
    h, w = img.shape[:2]
    ix, iy = np.floor(sx).astype(int), np.floor(sy).astype(int)
    ok = (ix >= 0) & (ix < w) & (iy >= 0) & (iy < h)
    out = np.zeros(sx.shape + (4,), np.float32)
    out[ok] = img[iy[ok], ix[ok]]
    return out


def voronoi(w, h, n, seed):
    rs = np.random.RandomState(seed)
    P = np.stack([rs.uniform(0, w, n), rs.uniform(0, h, n)], 1)
    yy, xx = np.mgrid[0:h, 0:w]
    d = (xx[..., None] + 0.5 - P[:, 0]) ** 2 + (yy[..., None] + 0.5 - P[:, 1]) ** 2
    lab = d.argmin(-1)
    bord = np.zeros((h, w), bool)
    bord[:, :-1] |= lab[:, :-1] != lab[:, 1:]
    bord[:-1, :] |= lab[:-1, :] != lab[1:, :]
    return lab, bord, P


def feuille_base():
    im = nouveau(LW, LH)
    kit.washi(im, 0, 0, LW, LH, "blanc", seed=7, bords="nets", ombre=False, taches=False)
    return np.asarray(im, np.float32) / 255.0


def plateau():
    """Petit plateau d'offrande (sanbō) de hinoki, vu de face, 60×12, à poser sous la feuille."""
    im = nouveau(64, 14)
    cx = 32
    kit.ombre_portee(im, cx - 28, 0, 56, 11, dx=1, dy=2, force=0.45, rayon=1)
    kit.bois(im, cx - 28, 0, 56, 4, "clair", seed=3, noeuds=0)
    kit.bois(im, cx - 21, 4, 42, 7, "clair", seed=4, noeuds=0)
    ja.rect(im, cx - 28, 4, 56, 1, hexc("5E3E22"))
    kit.disque_lisse(im, cx, 8, 2, "2C170C")
    return np.asarray(im, np.float32) / 255.0


BASE = None
PLATEAU = None


def case_vide():
    a = vide(CW, CH)
    over(a, PLATEAU, CW // 2 - 32, SOL)
    return a


def feuille_hr(leaf):
    return agrandir(leaf)


def poser_hr(case, hr_cell):
    """Pose une couche sur-échantillonnée (taille de la case × SS) dans la case."""
    over(case, reduire(hr_cell), 0, 0)


# ═══════════════════════════════════════ Katon ═══════════════════════════════════════
def r_katon(t, rmax):
    """Rayon du front de brûlure : allumage, puis montée jusqu'au coin qui reste (à 68 % du temps)."""
    if t < 0.08:
        return 3.0 * t / 0.08
    return 3.0 + (rmax - 3.0) * ease((t - 0.08) / 0.6)


def katon(f):
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    ix, iy = LW * 0.58, LH + 1.5
    D = np.sqrt((xx - ix) ** 2 + ((yy - iy) * 0.82) ** 2) + (kit.bruit(LW, LH, 6, 21, 3) - 0.5) * 9
    rmax = np.percentile(D, 76)
    R = r_katon(t, rmax)
    chaleur = 1.0 if t < 0.64 else max(0.12, 1.0 - (t - 0.64) / 0.3 * 0.88)
    e = D - R
    trou = kit.bruit(LW, LH, 3, 22, 2)
    out = leaf.copy()
    # roussi devant le front
    rous = (e >= -0.5) & (e < 5)
    k = (np.clip(1 - (e + 0.5) / 5.5, 0, 1) ** 1.6)[..., None]
    out[..., :3] = np.where(rous[..., None], out[..., :3] * (1 - 0.55 * k) + c("b07434")[:3] * 0.55 * k, out[..., :3])
    # braise
    br = (e >= -2.6) & (e < -0.5)
    braise = np.where((e > -1.4)[..., None], c("ffd060")[:3], c("ff7a1c")[:3])
    froid = c("5a1a0c")[:3]
    col_br = braise * chaleur + froid * (1 - chaleur)
    out[..., :3] = np.where(br[..., None], col_br, out[..., :3])
    # carbonisé, déchiqueté vers la partie disparue
    ch = (e >= -6.0) & (e < -2.6)
    out[..., :3] = np.where(ch[..., None], np.where((trou > 0.5)[..., None], c("2a1e1a")[:3], c("15100e")[:3]), out[..., :3])
    out[..., 3] = np.where(ch & (trou > 0.3 + (e + 6.0) / 6), 0, out[..., 3])
    out[..., 3] = np.where(e < -6.0, 0, out[..., 3])
    case = case_vide()
    over(case, out, OX, OY)
    # flammes : montent au-dessus de la bande de braise
    if 0.05 < t < 0.97:
        mb = np.zeros((CH, CW), np.float32)
        mb[OY:OY + LH, OX:OX + LW] = (br & (out[..., 3] > 0)).astype(np.float32)
        K = int(4 + 9 * chaleur * min(1.0, t / 0.2))
        fl = np.zeros_like(mb)
        for kk in range(1, K + 1):
            dx = int(round(1.4 * math.sin(f * 1.7 + kk * 0.9)))
            sh = np.roll(np.roll(mb, -kk, 0), dx, 1)
            fl = np.maximum(fl, sh * (1 - kk / (K + 1)) ** 1.25)
        mod = kit.bruit(CW, CH, 3, 40 + f, 2)
        fl = fl * (0.55 + 0.75 * mod) * chaleur
        couche = vide(CW, CH)
        for seuil, col in ((0.12, c("9a240a", 0.85)), (0.30, c("ee5a16")), (0.50, c("ffa22a")), (0.74, c("fff0b0"))):
            m = fl > seuil
            couche[m] = col
        over(case, couche)
    # cendres : partent du front, tombent en voletant et se posent sur le plateau
    rs = np.random.RandomState(5)
    for i in range(46):
        x0, y0 = rs.uniform(2, LW - 2), rs.uniform(LH * 0.25, LH - 1)
        d0 = math.hypot(x0 - ix, (y0 - iy) * 0.82)
        ts = next((s / 60 for s in range(61) if r_katon(s / 60, rmax) - 4 > d0), None)
        if ts is None or t < ts:
            continue
        dt = t - ts
        y = OY + y0 + 34 * dt * dt * 3 + 8 * dt
        x = OX + x0 + 2.2 * math.sin(dt * 9 + i) + (i % 3 - 1) * dt * 6
        if y >= SOL - 1:
            y = SOL - 1 - (i % 3 == 0)
            x = OX + LW / 2 + (x0 - LW / 2) * 0.55
        chaud = dt < 0.07 and chaleur > 0.5
        point(case, x, y, c("ff9a3a") if chaud else (c("4a4440") if i % 2 else c("2a2624")))
    # petit tas de cendres sur le plateau
    tas = lisse(0.15, 0.8, t)
    if tas > 0:
        rsa = np.random.RandomState(71)
        larg = 6 + 12 * tas
        for x in range(int(CW / 2 - larg), int(CW / 2 + larg) + 1):
            u = (x - CW / 2) / larg
            hcol = (0.6 + 1.8 * tas) * max(0.0, 1 - u * u)
            for k in range(int(round(hcol))):
                point(case, x, SOL - 1 - k, c(("3a3430", "56504a", "6e6660")[rsa.randint(0, 3)]))
    # fumée : quelques pixels gris tramés qui montent (pas de disques flous)
    if t > 0.2:
        rsf = np.random.RandomState(70)
        ph = (t - 0.2) / 0.8
        for i in range(26):
            a0 = rsf.uniform(0, 1)
            u = (ph + a0) % 1.0
            x = OX + LW * rsf.uniform(0.25, 0.8) + 3 * math.sin(u * 6 + i)
            y = OY + LH * 0.2 - u * 26
            a = 0.32 * math.sin(math.pi * u) * min(1.0, ph * 4)
            point(case, x, y, c("9a948e"), a)
    return case


# ═══════════════════════════════════════ Fūton ═══════════════════════════════════════
def futon(f):
    t = f / (NF - 1)
    hrL = feuille_hr(BASE)
    H, W = CH * SS, CW * SS
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    X = (xx + 0.5) / SS - OX            # coordonnées feuille (texels)
    Y = (yy + 0.5) / SS - OY
    cut = lambda y: LW * 0.54 - y / LH * LW * 0.08  # noqa: E731
    sep = ease(lisse(0.36, 0.78, t))
    flot = 1.3 * math.sin(math.pi * min(1.0, t / 0.4)) * (1 - sep)
    case = case_vide()
    hr = vide(W, H)
    for cote in (-1, 1):
        ang = math.radians(7.5) * sep * cote
        tx, ty = 8.5 * sep * cote, (2.0 if cote < 0 else 3.0) * sep
        px, py = cut(0), 0.0                         # pivot : haut de la coupe
        # inverse : point de sortie → point source (rotation −ang autour du pivot, puis −translation)
        dx, dy = X - px - tx, Y - py - ty
        sx = px + dx * math.cos(-ang) - dy * math.sin(-ang)
        sy = py + dx * math.sin(-ang) + dy * math.cos(-ang)
        sx = sx - flot * np.sin(sy / LH * math.pi * 3 + f * 0.9)
        part = echantillon(hrL, sx * SS, sy * SS)
        moitie = (sx < cut(sy)) if cote < 0 else (sx >= cut(sy))
        if sep < 0.02:
            moitie = (sx < cut(sy)) if cote < 0 else (sx >= cut(sy))
        part[~moitie] = 0
        # arête de coupe (1 texel) : sombre côté gauche, claire côté droit
        prox = np.abs(sx - cut(sy)) < 1.0
        arete = prox & moitie & (part[..., 3] > 0)
        if sep > 0.02:
            part[arete, :3] = (c("A69E92")[:3] if cote < 0 else c("FFFFFF")[:3])
        over(hr, part)
    poser_hr(case, hr)
    # trait de coupe qui se trace du haut vers le bas
    p = lisse(0.2, 0.36, t)
    if 0.2 < t < 0.5:
        n = int(LH * p)
        lum = 1.0 if t < 0.4 else 1 - (t - 0.4) / 0.1
        for y in range(n):
            x = OX + cut(y)
            point(case, x, OY + y, c("ffffff"), 0.95 * lum)
            point(case, x - 1, OY + y, c("8af0b8"), 0.35 * lum)
            point(case, x + 1, OY + y, c("8af0b8"), 0.35 * lum)
    # traînées de vent
    rs = np.random.RandomState(9)
    for i in range(5):
        t0 = 0.02 + i * 0.1
        if not (t0 < t < t0 + 0.42):
            continue
        s = (t - t0) / 0.42
        y = OY + 6 + rs.uniform(0, LH - 8)
        L = 18 + rs.uniform(0, 10)
        x = -L + s * (CW + L)
        for k in range(int(L)):
            a = 0.9 * math.sin(math.pi * k / L) * (1 - abs(2 * s - 1) ** 3)
            yk = y + 1.2 * math.sin((x + k) / 9 + i)
            point(case, x + k, yk, c("f4fff8"), a)
            point(case, x + k, yk + 1, c("7ae8b0"), a * 0.5)
            point(case, x + k + 3, yk - 3, c("c8ffe0"), a * 0.35)
    return case


# ═══════════════════════════════════════ Raiton ═══════════════════════════════════════
LAB_R, BORD_R, _ = (None, None, None)


def raiton(f):
    t = f / (NF - 1)
    cr = lisse(0.1, 0.88, t)
    leaf = BASE.copy()
    lab, bord, P = voronoi(LW, LH, 24, 31)
    rs = np.random.RandomState(32)
    b = rs.uniform(-1, 1, len(P))
    fac = 1 + cr * 0.22 * b[lab]
    leaf[..., :3] = np.clip(leaf[..., :3] * fac[..., None], 0, 1)
    pli = bord & (leaf[..., 3] > 0)
    leaf[..., :3] = np.where(pli[..., None], leaf[..., :3] * (1 - 0.42 * cr), leaf[..., :3])
    clair = np.zeros_like(bord)
    clair[1:, 1:] = pli[:-1, :-1]
    clair &= ~pli
    leaf[..., :3] = np.where(clair[..., None], np.clip(leaf[..., :3] * (1 + 0.14 * cr), 0, 1), leaf[..., :3])
    hrL = feuille_hr(leaf)
    H, W = CH * SS, CW * SS
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    X = (xx + 0.5) / SS - OX - LW / 2
    Y = (yy + 0.5) / SS - OY - LH / 2 - 2 * cr      # la boule descend un peu
    th = np.arctan2(Y, X)
    n1 = np.sin(th * 5 + 1.3) * 0.5 + np.sin(th * 9 + 0.4) * 0.3 + np.sin(th * 14 + 2.2) * 0.2
    s = 1 - cr * (0.30 + 0.07 * n1)
    rot = math.radians(9) * cr
    xr = X * math.cos(rot) + Y * math.sin(rot)
    yr = -X * math.sin(rot) + Y * math.cos(rot)
    wr = kit.bruit(W, H, 6 * SS, 33, 2) - 0.5
    sx = xr / s + LW / 2 + wr * 1.6 * cr
    sy = yr / (s * (1 - 0.06 * cr)) + LH / 2 + wr * 1.2 * cr
    hr = echantillon(hrL, sx * SS, sy * SS)
    case = case_vide()
    poser_hr(case, hr)
    # arcs électriques
    if t < 0.92:
        rf = np.random.RandomState(100 + f)
        nb = 4 if t < 0.6 else 2
        for _ in range(nb):
            x, y = OX + rf.uniform(4, LW - 4), OY + rf.uniform(4, LH - 4)
            if t > 0.5:
                x, y = OX + LW / 2 + rf.uniform(-12, 12), OY + LH / 2 + rf.uniform(-12, 14)
            pts = [(x, y)]
            ang = rf.uniform(0, 2 * math.pi)
            for _ in range(rf.randint(4, 8)):
                ang += rf.uniform(-1.3, 1.3)
                L = rf.uniform(2, 4)
                x, y = x + L * math.cos(ang), y + L * math.sin(ang)
                pts.append((x, y))
            for (px, py) in pts:
                for (ddx, ddy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    point(case, px + ddx, py + ddy, c("ffd23a"), 0.6)
            ligne(case, pts, c("fffbe0"))
        for _ in range(2):
            point(case, OX + rf.uniform(-6, LW + 6), OY + rf.uniform(-6, LH + 4), c("fff2a0"), 0.9)
    return case


# ═══════════════════════════════════════ Doton ═══════════════════════════════════════
def doton(f):
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    v = (LH - 1 - yy) / LH + (kit.bruit(LW, LH, 5, 51, 2) - 0.5) * 0.3
    E = np.clip((t * 1.7 - v) / 0.3, 0, 1)
    terre = ["4e3420", "6a4a2c", "86603a", "a07a4c", "b8925e", "caa672"]
    nz = kit.bruit(LW, LH, 3, 52, 2)
    idx = np.clip((nz * len(terre)).astype(int), 0, len(terre) - 1)
    tc = np.array([c(h)[:3] for h in terre])[idx]
    leaf[..., :3] = leaf[..., :3] * (1 - E[..., None] * 0.92) + tc * E[..., None] * 0.92
    lab, bord, P = voronoi(LW, LH, 30, 53)
    fis = bord & (E > 0.55)
    leaf[..., :3] = np.where(fis[..., None], c("2e1e10")[:3], leaf[..., :3])
    case = case_vide()
    rs = np.random.RandomState(54)
    hsh = rs.rand(LH, LW)
    tomb = 0
    reste = leaf.copy()
    chute = vide(CW, CH)
    for i, (px, py) in enumerate(P):
        if py < LH * 0.3:
            continue
        ts = 0.3 + 0.52 * ((LH - py) / LH) + rs.uniform(-0.05, 0.05)
        if t < ts:
            continue
        tomb += 1
        m = lab == i
        reste[m] = 0
        dt = t - ts
        dy = 70 * dt * dt + 6 * dt
        keep = m & (hsh > dt * 3.2) & (leaf[..., 3] > 0)
        ys, xs = np.nonzero(keep)
        for y, x in zip(ys, xs):
            X = OX + x + (x - px) * dt * 1.4
            Y = OY + y + dy
            if Y < SOL - 1:
                point(chute, X, Y, np.concatenate([leaf[y, x, :3], [1.0]]).astype(np.float32))
    over(case, reste, OX, OY)
    over(case, chute)
    # tas de poussière sur le plateau, qui grossit avec ce qui est tombé
    frac = min(1.0, tomb / max(1, sum(1 for (_, py) in P if py >= LH * 0.3)))
    if frac > 0:
        hmax = 1 + 5 * frac
        larg = 14 + 16 * frac
        rsd = np.random.RandomState(55)
        for x in range(int(CW / 2 - larg), int(CW / 2 + larg) + 1):
            u = (x - CW / 2) / larg
            hcol = hmax * max(0.0, 1 - u * u) ** 0.8
            for k in range(int(round(hcol))):
                h = terre[min(len(terre) - 1, 1 + k + rsd.randint(0, 2))]
                point(case, x, SOL - 1 - k, c(h))
        # poussière en suspension
        for k in range(12):
            if rsd.rand() < frac:
                x = CW / 2 + rsd.uniform(-larg - 6, larg + 6)
                y = SOL - 2 - rsd.uniform(0, 10) * (1 - t * 0.4)
                point(case, x, y, c("b89a72"), 0.45)
    return case


# ═══════════════════════════════════════ Suiton ═══════════════════════════════════════
def suiton(f):
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    d = np.sqrt((xx - LW * 0.5) ** 2 + ((yy - LH * 0.32) * 0.85) ** 2) + (kit.bruit(LW, LH, 5, 61, 3) - 0.5) * 10
    r = 62 * ease(lisse(0.06, 0.7, t))
    mouille = d < r
    fib = kit.bruit(LW, LH, 2, 62, 1)
    tache = kit.bruit(LW, LH, 7, 63, 2)
    w = leaf[..., :3] * np.array([0.60, 0.70, 0.84]) * (0.88 + 0.12 * fib[..., None]) * (0.92 + 0.14 * tache[..., None])
    leaf[..., :3] = np.where(mouille[..., None], w, leaf[..., :3])
    leaf[..., 3] = np.where(mouille, leaf[..., 3] * 0.84, leaf[..., 3])
    aureole = (d >= r - 2.2) & (d < r) & (r < 60)
    leaf[..., :3] = np.where(aureole[..., None], leaf[..., :3] * np.array([0.66, 0.72, 0.86]), leaf[..., :3])
    # filets d'eau qui descendent vers les points de goutte
    for gx in (14, 32, 39):
        y0 = int(LH * 0.55)
        for y in range(y0, LH):
            if mouille[y, gx] and r > 40:
                k = (y - y0) / (LH - y0)
                leaf[y, gx, :3] = leaf[y, gx, :3] * (1 - 0.18 * k)
    # reflets humides
    if r > 20:
        for (gx, gy) in ((14, 18), (15, 18), (31, 40), (22, 50)):
            if d[gy, gx] < r - 3:
                leaf[gy, gx, :3] = c("f4fbff")[:3]
    hrL = feuille_hr(leaf)
    H, W = CH * SS, CW * SS
    yy2, xx2 = np.mgrid[0:H, 0:W].astype(np.float32)
    X = (xx2 + 0.5) / SS - OX
    Y = (yy2 + 0.5) / SS - OY
    aff = lisse(0.3, 0.95, t)
    cote = np.abs(X - LW / 2) / (LW / 2)
    sag = aff * 6.5 * np.clip(Y / LH, 0, 1) ** 2 * (0.35 + 0.65 * np.clip(cote, 0, 1) ** 1.6)
    sx = X - aff * 0.9 * np.sin(Y / LH * math.pi * 3)
    sy = Y - sag
    hr = echantillon(hrL, sx * SS, sy * SS)
    case = case_vide()
    poser_hr(case, hr)
    # gouttes : perle qui grossit au bord bas, se détache, tombe, éclabousse ; flaque sur le plateau
    flaque = 0
    for k, (gx, t0) in enumerate(((LW * 0.3, 0.42), (LW * 0.66, 0.55), (LW * 0.82, 0.7))):
        cote_k = abs(gx - LW / 2) / (LW / 2)
        bas = OY + LH + aff * 6.5 * (0.35 + 0.65 * cote_k ** 1.6) - 0.5
        x = OX + gx
        per = 0.27
        for cyc in range(4):
            a0 = t0 + cyc * per
            if t < a0:
                break
            s = (t - a0) / per
            if s < 0.45:                                   # perle
                rr = 1 if s < 0.2 else 2 if s < 0.35 else 3
                for j in range(rr):
                    point(case, x, bas + j, c("3a7ab8"))
                    if j == rr - 1 and rr == 3:
                        point(case, x - 1, bas + j - 1, c("3a7ab8"))
                        point(case, x + 1, bas + j - 1, c("2a5e94"))
                point(case, x, bas, c("bfe6ff"))
            elif s < 1.0:                                  # chute
                u = (s - 0.45) / 0.55
                y = bas + 2 + (SOL - 2 - bas) * u * u
                if y < SOL - 2:
                    point(case, x, y, c("2a5e94"))
                    point(case, x, y - 1, c("3a7ab8"))
                    point(case, x - 1, y, c("3a7ab8"), 0.7)
                    point(case, x, y - 2, c("7cc8f0"), 0.8)
                    point(case, x, y - 3, c("bfe6ff"), 0.35)
                else:
                    point(case, x - 1, SOL - 2, c("bfe6ff"), 0.8)
                    point(case, x + 1, SOL - 2, c("bfe6ff"), 0.8)
            if s >= 1.0:
                flaque += 1
    if flaque:
        L = min(22, 4 + 5 * flaque)
        for x in range(int(CW / 2 - L), int(CW / 2 + L) + 1):
            point(case, x, SOL - 1, c("5a9ed0"), 0.85)
            if (x * 7) % 5 == 0:
                point(case, x, SOL - 1, c("cfeeff"), 0.9)
    return case


REACTIONS = {"katon": katon, "futon": futon, "raiton": raiton, "doton": doton, "suiton": suiton}


# ═══════════════════════════════════════ sorties ═══════════════════════════════════════
def en_image(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def atlas(frames):
    im = nouveau(CW * COLS, CH * (NF // COLS))
    for i, fr in enumerate(frames):
        im.alpha_composite(en_image(fr), ((i % COLS) * CW, (i // COLS) * CH))
    return im


def halo():
    n = 64
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    r = np.hypot(xx + 0.5 - n / 2, yy + 0.5 - n / 2) / (n / 2)
    a = np.clip(1 - r, 0, 1) ** 2.2
    o = np.zeros((n, n, 4), np.uint8)
    o[..., :3] = 255
    o[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(o, "RGBA")


def gif(frames, chemin, echelle=3, fond=(14, 10, 12), tenir=6, bandes=None):
    imgs = []
    seq = list(frames) + [frames[-1]] * tenir
    for fr in seq:
        b = Image.new("RGBA", (CW, CH), fond + (255,))
        b.alpha_composite(en_image(fr))
        imgs.append(b.convert("RGB").resize((CW * echelle, CH * echelle), Image.NEAREST))
    imgs[0].save(chemin, save_all=True, append_images=imgs[1:], duration=110, loop=0)


def double(fa_, fb_):
    """Aperçu d'une double nature (comme le code) : moitié gauche de la 1re, moitié droite de la 2de."""
    out = []
    for a, b in zip(fa_, fb_):
        m = a.copy()
        m[:, CW // 2:] = b[:, CW // 2:]
        out.append(m)
    return out


def main():
    global BASE, PLATEAU
    BASE = feuille_base()
    PLATEAU = plateau()
    intacte = case_vide()
    over(intacte, BASE, OX, OY)
    S.sauver("feuille.png", en_image(intacte), (CW, CH))
    toutes = {}
    for n in NATURES:
        frames = [REACTIONS[n](f) for f in range(NF)]
        toutes[n] = frames
        S.sauver(f"feuille_{n}.png", atlas(frames), (CW * COLS, CH * (NF // COLS)))
    S.sauver("halo.png", halo(), (64, 64))
    S.sauver("bouton.png", ja.plaque_etats(96, 16, ("repos", "survol"), pas=20), (100, 40))
    S.bilan()
    os.makedirs(MAQ, exist_ok=True)
    for n, frames in toutes.items():
        gif(frames, os.path.join(MAQ, f"reaction_{n}.gif"))
    gif(double(toutes["suiton"], toutes["raiton"]), os.path.join(MAQ, "reaction_double_suiton_raiton.gif"))
    gif(double(toutes["futon"], toutes["katon"]), os.path.join(MAQ, "reaction_double_futon_katon.gif"))
    # planche : toutes les images de chaque nature
    pl = Image.new("RGBA", (CW * NF + 20, (CH + 6) * len(NATURES) + 14), (14, 10, 12, 255))
    for r, n in enumerate(NATURES):
        for i, fr in enumerate(toutes[n]):
            pl.alpha_composite(en_image(fr), (10 + i * CW, 7 + r * (CH + 6)))
    pl.convert("RGB").resize((pl.width * 2, pl.height * 2), Image.NEAREST).save(os.path.join(MAQ, "planche_reactions.png"))
    print("GIF ->", MAQ)


if __name__ == "__main__":
    main()
