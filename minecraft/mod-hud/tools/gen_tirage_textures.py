# -*- coding: utf-8 -*-
"""
gen_tirage_textures.py — test de la Feuille v2 (16/10) : la feuille de papier à chakra tenue **entre deux doigts**, en
gros plan, sa lueur de chakra, et sa réaction selon la nature, image par image (menu/tirage/TirageScreen). Aucun kanji
(DA §3), pixel art léger (DA §6) : chaque image est une case d'atlas posée en UN blit ; le code choisit la case, pose la
lueur, la jauge et le texte.

Réactions (canon, cf. référence vidéo du 16/10, refaites à notre main) :
  - Katon  : elle prend feu par un coin du bas, la flamme remonte et la consume ; il reste un bout carbonisé sous les
             doigts, des braises qui s'éteignent, des cendres qui tombent, un filet de fumée ;
  - Suiton : l'humidité gagne depuis les doigts, la feuille s'assombrit, devient translucide, s'affaisse et goutte ;
  - Doton  : elle se dessèche (brunit depuis les bords), se craquelle et s'effrite par le bas en poussière ;
  - Raiton : des plis en zigzag la traversent comme des éclairs, puis elle se froisse en boule à facettes, crépitante ;
  - Fūton  : un souffle, puis une coupe nette en diagonale : le bas glisse le long de la coupe et s'envole.
  Double nature : le code coupe chaque image en bandes verticales (une par nature, UV) qui réagissent l'une après
  l'autre — les doigts et la feuille sont au même endroit dans toutes les cases, la jointure ne se voit pas.

1 texel = 1 GUI. Contrat (textures/gui/tirage/) :
- feuille.png             128×160 : la feuille intacte tenue par les deux doigts (confirmation, canalisation).
- feuille_<nature>.png   1024×640 : 32 images 128×160 (8 colonnes × 4 rangées, ordre de lecture), 70 ms chacune
                                    (2,24 s) ; la dernière reste affichée au résultat.
  Dans une case : feuille 60×80 en (34, 44) ; doigts du haut de la case jusqu'à y ≈ 56.
- aura.png                512×160 : 4 images 128×160 de la lueur de chakra autour de la feuille (contour vivant,
                                    bleu), posées par-dessus avec l'alpha de la charge, 110 ms chacune.
- pieces.png              192×48  : cabochon de touche (3 morceaux, coupe), cadre et remplissage de la jauge,
                                    pastille de nature, filet.
- halo.png                 64×64  : halo doux blanc (teinté et étiré par le code).
- bouton.png              100×40  : plaque laquée fine 96×16, repos / survol (pas de 20).
GIF de contrôle et planche : Maquettes/gameplan/test_feuille_v2/ (reaction_<nature>.gif, planche_reactions.png).

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
from jeu_art import ea, kit, nouveau  # noqa: E402

S = ea.Sortie("tirage", budget=1_400_000)
MAQ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..",
                                    "Maquettes", "gameplan", "test_feuille_v2"))

CW, CH = 128, 160        # case
LW, LH = 60, 80          # feuille
OX, OY = 34, 44          # feuille dans la case
NF, COLS = 32, 8
SS = 4                   # sur-échantillonnage des déformations
NATURES = ("katon", "suiton", "doton", "futon", "raiton")
GX = OX + LW // 2        # axe des doigts


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
    x, y = int(math.floor(x)), int(math.floor(y))
    if 0 <= x < dst.shape[1] and 0 <= y < dst.shape[0]:
        p = col.copy()
        if a is not None:
            p[3] = max(0.0, min(1.0, a))
        over(dst, p.reshape(1, 1, 4), x, y)


def ligne(dst, pts, col, a=None):
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        n = int(max(abs(xb - xa), abs(yb - ya))) + 1
        for k in range(n + 1):
            s = k / max(1, n)
            point(dst, xa + (xb - xa) * s, ya + (yb - ya) * s, col, a)


def agrandir(a, k=SS):
    return np.repeat(np.repeat(a, k, axis=0), k, axis=1)


def reduire(a, k=SS, seuil=0.5):
    """Moyenne k×k en alpha prémultiplié, puis alpha franchi (bords nets, pas de halo baveux)."""
    h, w = a.shape[0] // k, a.shape[1] // k
    pm = a[..., :3] * a[..., 3:4]
    rgb = pm.reshape(h, k, w, k, 3).mean(axis=(1, 3))
    al = a[..., 3:4].reshape(h, k, w, k, 1).mean(axis=(1, 3))
    rgb = np.where(al > 1e-5, rgb / np.maximum(al, 1e-5), 0)
    # alpha : les zones pleines restent pleines, les translucides (eau) gardent leur valeur, les franges disparaissent
    plein = a[..., 3:4].reshape(h, k, w, k, 1).max(axis=(1, 3))
    al = np.where(al >= seuil * plein, plein, 0.0)
    return np.concatenate([rgb, np.clip(al, 0, 1)], -1).astype(np.float32)


def echantillon(img, sx, sy):
    """Lecture au plus proche (coordonnées en pixels de img), 0 hors de l'image."""
    h, w = img.shape[:2]
    ix, iy = np.floor(sx).astype(int), np.floor(sy).astype(int)
    ok = (ix >= 0) & (ix < w) & (iy >= 0) & (iy < h)
    out = np.zeros(sx.shape + (4,), np.float32)
    out[ok] = img[iy[ok], ix[ok]]
    return out


def voronoi(w, h, n, seed, marge=0):
    rs = np.random.RandomState(seed)
    P = np.stack([rs.uniform(-marge, w + marge, n), rs.uniform(-marge, h + marge, n)], 1)
    yy, xx = np.mgrid[0:h, 0:w]
    d = (xx[..., None] + 0.5 - P[:, 0]) ** 2 + (yy[..., None] + 0.5 - P[:, 1]) ** 2
    lab = d.argmin(-1)
    bord = np.zeros((h, w), bool)
    bord[:, :-1] |= lab[:, :-1] != lab[:, 1:]
    bord[:-1, :] |= lab[:-1, :] != lab[1:, :]
    return lab, bord, P


def contour(m):
    up = np.zeros_like(m); up[1:, :] = m[:-1, :]
    dn = np.zeros_like(m); dn[:-1, :] = m[1:, :]
    lf = np.zeros_like(m); lf[:, 1:] = m[:, :-1]
    rt = np.zeros_like(m); rt[:, :-1] = m[:, 1:]
    return m & ~(up & dn & lf & rt), up, dn, lf, rt


def bruit(w, h, cel, seed, oct_=2):
    return kit.bruit(w, h, cel, seed, oct_).astype(np.float32)


# ═══════════════════════════════════════ la feuille et les doigts ═══════════════════════════════════════
PEAU = ["7A4A30", "9A6242", "B87C56", "CF9468", "DEA87C", "EABB90", "F4CFA8"]
ONGLE = ["C89A86", "E2BCA8", "F2D6C8"]


def feuille_base():
    """Washi blanc 60×80, bords nets, modelé doux : lumière d'en haut à gauche, ombre des doigts en haut."""
    im = nouveau(LW, LH)
    kit.washi(im, 0, 0, LW, LH, "blanc", seed=7, bords="nets", ombre=False, taches=False)
    a = np.asarray(im, np.float32) / 255.0
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    mod = 1.0 + 0.035 * (1 - xx / LW) - 0.05 * (yy / LH) ** 2
    # légère courbure : la feuille pend, un pli doux au milieu
    mod -= 0.025 * np.exp(-((xx - LW * 0.62) / 5.0) ** 2) * (yy / LH)
    # ombre des doigts sur le haut
    d = np.hypot((xx - LW / 2) / 9.0, (yy - 2) / 8.0)
    mod -= 0.10 * np.clip(1 - d, 0, 1)
    a[..., :3] = np.clip(a[..., :3] * mod[..., None], 0, 1)
    return a


def doigt(hr_h, larg, long_, avant=True):
    """Un doigt vu de dos, pointe en bas, sur-échantillonné (SS) : couleur lissée puis réduite par l'appelant."""
    W, H = larg * SS, long_ * SS
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    X = (xx + 0.5) / SS - larg / 2
    Y = (yy + 0.5) / SS
    r = larg / 2
    # corps + bout arrondi
    bout = long_ - r
    dist = np.where(Y < bout, np.abs(X), np.hypot(X, Y - bout))
    m = dist <= r
    # modelé cylindrique, lumière à gauche
    n = np.clip(X / r, -1, 1)
    v = 0.62 - 0.30 * n + 0.10 * np.sqrt(np.clip(1 - n * n, 0, 1))
    v -= 0.10 * (Y < 2.5)                           # ombre en haut (sous la main)
    # pli de l'articulation
    for yk in (long_ * 0.30, long_ * 0.33):
        v -= 0.10 * (np.abs(Y - yk) < 0.6) * (np.abs(X) < r * 0.7)
    if not avant:
        v -= 0.16
    idx = np.clip((v * (len(PEAU) - 1) + 0.5).astype(int), 0, len(PEAU) - 1)
    pal = np.array([c(h) for h in PEAU])
    out = pal[idx]
    out[..., 3] = m.astype(np.float32)
    # ongle (vu de dos : au bout)
    if avant:
        on = (np.abs(X + 0.3) < r * 0.62) & (Y > bout - r * 0.95) & (Y < long_ - 1.1) & m
        out[on] = c(ONGLE[1])
        on2 = on & (X < -0.6) & (Y < bout)
        out[on2] = c(ONGLE[2])
        bord_on = on & ((np.abs(X + 0.3) > r * 0.5) | (Y > long_ - 1.9))
        out[bord_on] = c(ONGLE[0])
    # liseré sombre (lisible sur la feuille blanche et sur la nuit)
    e = m & (dist > r - 0.9)
    out[e, :3] = c(PEAU[0])[:3]
    return out


def calque_doigts(arriere):
    """Les deux doigts (index devant la feuille, majeur derrière), réduits à la taille de la case ; ils sortent de
    l'ombre en haut (fondu d'alpha sur 18 GUI : la main reste hors champ)."""
    hr = vide(CW * SS, CH * SS)
    haut = 10
    if arriere:
        d = doigt(None, 9, OY + 13 - haut, avant=False)
        over(hr, d, (GX + 1) * SS, haut * SS)
    else:
        d = doigt(None, 10, OY + 9 - haut, avant=True)
        over(hr, d, (GX - 9) * SS, haut * SS)
    a = reduire(hr)
    yy = np.arange(CH, dtype=np.float32)[:, None]
    a[..., 3] *= np.clip((yy - haut) / 18.0, 0, 1)
    return a


BASE = None
DOIGT_AR = None
DOIGT_AV = None


def case(feuille_couche, x=OX, y=OY, entre=None):
    """Assemble une case : majeur (derrière), feuille, [entre : flammes…], index (devant)."""
    a = vide(CW, CH)
    over(a, DOIGT_AR)
    if feuille_couche is not None:
        if feuille_couche.shape[:2] == (CH, CW):
            over(a, feuille_couche)
        else:
            over(a, feuille_couche, x, y)
    if entre is not None:
        over(a, entre)
    over(a, DOIGT_AV)
    return a


def grille_hr():
    H, W = CH * SS, CW * SS
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    return (xx + 0.5) / SS - OX, (yy + 0.5) / SS - OY     # coordonnées feuille (texels), pleine case


# ═══════════════════════════════════════ Katon ═══════════════════════════════════════
def katon(f):
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    ix, iy = LW * 0.20, LH + 2.0                          # allumage : coin bas gauche
    D = np.sqrt((xx - ix) ** 2 + ((yy - iy) * 0.78) ** 2) + (bruit(LW, LH, 7, 21, 3) - 0.5) * 11
    reste_y = 15                                           # le bout qui reste sous les doigts
    rmax = float(np.percentile(D[yy > reste_y], 99.5))
    if t < 0.06:
        R = 3.0 * t / 0.06
    else:
        R = 3.0 + (rmax - 3.0) * ease((t - 0.06) / 0.62)
    chaleur = 1.0 if t < 0.68 else max(0.0, 1.0 - (t - 0.68) / 0.26)
    e = D - R
    trou = bruit(LW, LH, 3, 22, 2)
    out = leaf.copy()
    # roussi devant le front
    k = (np.clip(1 - (e + 0.5) / 6.5, 0, 1) ** 1.5)[..., None] * ((e >= -0.5) & (e < 6))[..., None]
    out[..., :3] = out[..., :3] * (1 - 0.6 * k) + c("a8682c")[:3] * 0.6 * k
    # braise (vacille)
    vac = bruit(LW, LH, 2, 30 + f, 1)
    br = (e >= -2.8) & (e < -0.4)
    chaud = np.where((vac > 0.45)[..., None], c("ffe08a")[:3], c("ff8a24")[:3])
    froid = c("4a160c")[:3]
    col_br = chaud * chaleur + froid * (1 - chaleur)
    col_br = np.where(((e < -2.0) & (vac < 0.6))[..., None], c("c23a14")[:3] * max(chaleur, 0.3) + froid * (1 - max(chaleur, 0.3)), col_br)
    out[..., :3] = np.where(br[..., None], col_br, out[..., :3])
    # carbonisé, déchiqueté
    ch = (e >= -6.5) & (e < -2.8)
    out[..., :3] = np.where(ch[..., None], np.where((trou > 0.55)[..., None], c("2c201c")[:3], c("16100e")[:3]), out[..., :3])
    out[..., 3] = np.where(ch & (trou > 0.25 + (e + 6.5) / 5.0), 0, out[..., 3])
    out[..., 3] = np.where(e < -6.5, 0, out[..., 3])
    couche = None
    # flammes : langues qui montent au-dessus de la bande de braise
    if 0.03 < t < 0.92:
        mb = np.zeros((CH, CW), np.float32)
        mb[OY:OY + LH, OX:OX + LW] = (br & (out[..., 3] > 0)).astype(np.float32)
        force = chaleur * min(1.0, t / 0.12)
        K = int(5 + 13 * force)
        fl = np.zeros_like(mb)
        for kk in range(1, K + 1):
            dx = int(round(1.6 * math.sin(f * 1.9 + kk * 0.7)))
            sh = np.roll(np.roll(mb, -kk, 0), dx, 1)
            fl = np.maximum(fl, sh * (1 - kk / (K + 1)) ** 1.1)
        mod = bruit(CW, CH, 4, 40 + f * 3, 2)
        fl = fl * (0.45 + 0.85 * mod) * force
        couche = vide(CW, CH)
        for seuil, col in ((0.10, c("7a1a08", 0.8)), (0.24, c("d8481a")), (0.42, c("ff8c22")), (0.62, c("ffc64a")),
                           (0.80, c("fff2c0"))):
            couche[fl > seuil] = col
    a = case(out, entre=couche)
    # cendres et flammèches : se détachent du front et tombent en voletant
    rs = np.random.RandomState(5)
    for i in range(60):
        x0, y0 = rs.uniform(1, LW - 1), rs.uniform(reste_y, LH - 1)
        d0 = math.hypot(x0 - ix, (y0 - iy) * 0.78)
        ts = None
        for s in range(80):
            tt = s / 79
            RR = 3.0 * tt / 0.06 if tt < 0.06 else 3.0 + (rmax - 3.0) * ease((tt - 0.06) / 0.62)
            if RR - 5 > d0:
                ts = tt
                break
        if ts is None or t < ts:
            continue
        dt = t - ts
        y = OY + y0 + 70 * dt * dt + 10 * dt - (8 * dt if i % 4 == 0 else 0)
        x = OX + x0 + 2.4 * math.sin(dt * 10 + i) + (i % 3 - 1) * dt * 9
        if y > CH:
            continue
        if dt < 0.10 and chaleur > 0.4:
            col = c("ffb24a") if i % 2 else c("ff7a1c")
        else:
            col = c("4a4440") if i % 2 else c("2a2624")
        point(a, x, y, col)
        if i % 5 == 0 and dt >= 0.10:
            point(a, x + 1, y, c("3a3430"))
    # fumée : pixels tramés gris qui montent et s'effilochent
    if t > 0.25:
        rsf = np.random.RandomState(70)
        ph = (t - 0.25) / 0.75
        for i in range(34):
            a0 = rsf.uniform(0, 1)
            u = (ph * 1.3 + a0) % 1.0
            x = OX + LW * rsf.uniform(0.2, 0.8) + 4 * math.sin(u * 5 + i) + u * 6
            y = OY + reste_y - 2 - u * 46
            al = 0.30 * math.sin(math.pi * u) * min(1.0, ph * 3)
            point(a, x, y, c("a49e98"), al)
            if i % 3 == 0:
                point(a, x + 1, y - 1, c("8a8480"), al * 0.7)
    return a


# ═══════════════════════════════════════ Suiton ═══════════════════════════════════════
def suiton(f):
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    d = np.sqrt((xx - LW * 0.5) ** 2 + ((yy + 4) * 0.78) ** 2) + (bruit(LW, LH, 6, 61, 3) - 0.5) * 12
    r = 84 * ease(lisse(0.04, 0.58, t))
    mouille = d < r
    fib = bruit(LW, LH, 2, 62, 1)
    tache = bruit(LW, LH, 8, 63, 2)
    w = leaf[..., :3] * np.array([0.66, 0.73, 0.82]) * (0.84 + 0.16 * fib[..., None]) * (0.86 + 0.20 * tache[..., None])
    leaf[..., :3] = np.where(mouille[..., None], w, leaf[..., :3])
    leaf[..., 3] = np.where(mouille, 0.80 + 0.12 * tache, leaf[..., 3])
    aureole = (d >= r - 2.4) & (d < r) & (r < 80)
    leaf[..., :3] = np.where(aureole[..., None], leaf[..., :3] * np.array([0.60, 0.64, 0.72]), leaf[..., :3])
    leaf[..., 3] = np.where(aureole, 1.0, leaf[..., 3])
    # rigoles qui descendent vers les points de goutte
    for gx in (9, 30, 51):
        y0 = int(LH * 0.45)
        for y in range(y0, LH):
            if mouille[y, gx]:
                k = (y - y0) / (LH - y0)
                leaf[y, gx, :3] = leaf[y, gx, :3] * (1 - 0.22 * k)
                leaf[y, gx + 1, :3] = np.clip(leaf[y, gx + 1, :3] * (1 + 0.10 * k), 0, 1)
    # reflets humides qui glissent
    if r > 24:
        for (gx, gy) in ((14, 20), (15, 20), (16, 21), (40, 34), (41, 35), (24, 56), (44, 64)):
            gy2 = int(gy + 10 * lisse(0.5, 1.0, t))
            if gy2 < LH and d[gy2, gx] < r - 3:
                leaf[gy2, gx, :3] = c("eef8ff")[:3]
                leaf[gy2, gx, 3] = 1.0
    hrL = agrandir(leaf)
    X, Y = grille_hr()
    aff = lisse(0.28, 0.92, t)
    cote = np.abs(X - LW / 2) / (LW / 2)
    yn = np.clip(Y / LH, 0, 1)
    sag = aff * 9.0 * yn ** 2 * (0.25 + 0.75 * np.clip(cote, 0, 1) ** 1.5)
    pince = 1 + aff * 0.10 * yn ** 2          # le bas se resserre un peu (papier détrempé)
    sx = (X - LW / 2) * pince + LW / 2 - aff * 1.1 * np.sin(Y / LH * math.pi * 2.5 + 0.5)
    sy = Y - sag
    a = case(reduire(echantillon(hrL, sx * SS, sy * SS)))
    # gouttes : perle qui grossit au bord bas, se détache, tombe en s'étirant
    for k, (gx, t0, per) in enumerate(((LW * 0.15, 0.40, 0.22), (LW * 0.5, 0.50, 0.26), (LW * 0.85, 0.58, 0.20))):
        cote_k = abs(gx - LW / 2) / (LW / 2)
        bas = OY + LH + aff * 9.0 * (0.25 + 0.75 * cote_k ** 1.5) - 1.0
        x = OX + LW / 2 + (gx - LW / 2) / (1 + aff * 0.10)
        for cyc in range(5):
            a0 = t0 + cyc * per
            if t < a0:
                break
            s = (t - a0) / per
            if s < 0.5:                                    # perle
                rr = 1 if s < 0.15 else 2 if s < 0.28 else 3 if s < 0.40 else 4
                for j in range(rr):
                    larg = 0 if j == 0 else 1
                    for dx in range(-larg, larg + 1):
                        point(a, x + dx, bas + j, c("4a8cc8") if dx < 0 else c("2a5e94") if dx > 0 else c("3a78b4"))
                point(a, x - (rr >= 3), bas + rr - 2 if rr > 1 else bas, c("d8f2ff"))
            elif s < 1.0:                                  # chute
                u = (s - 0.5) / 0.5
                y = bas + 2 + (CH + 6 - bas) * u * u
                for j, (col, al) in enumerate(((c("2a5e94"), 1), (c("3a78b4"), 1), (c("7cc8f0"), 0.85), (c("c8ecff"), 0.5))):
                    point(a, x, y - j * (1 + u), col, al)
                point(a, x - 1, y - 1, c("4a8cc8"), 0.9)
                point(a, x + 1, y - 1, c("2a5e94"), 0.9)
                point(a, x - 1, y - 2, c("d8f2ff"), 0.9)
    return a


# ═══════════════════════════════════════ Doton ═══════════════════════════════════════
DOTON_PRE = None


def doton_pre():
    lab, bord, P = voronoi(LW, LH, 30, 53)
    edist = distance(bord, 8)               # distance au bord de l'éclat (pour l'effritement)
    return lab, bord, P, edist


def doton(f):
    global DOTON_PRE
    if DOTON_PRE is None:
        DOTON_PRE = doton_pre()
    lab, bord, P, edist = DOTON_PRE
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    bord_d = np.minimum.reduce([xx, LW - 1 - xx, LH - 1 - yy, yy + 20]).astype(np.float32)
    v = bord_d / 30 + (bruit(LW, LH, 6, 51, 2) - 0.5) * 0.30
    E = np.clip((t * 3.2 - v) / 0.45, 0, 1)                # le dessèchement gagne depuis les bords (fini à t ≈ 0,4)
    terre = ["5a3c22", "74522e", "8c683c", "a4804e", "b8945e", "c8a670", "d6b884"]
    nz = bruit(LW, LH, 3, 52, 2) * 0.55 + bruit(LW, LH, 9, 56, 2) * 0.45
    idx = np.clip((nz * 0.75 + 0.25 * (1 - yy / LH)) * len(terre), 0, len(terre) - 1).astype(int)
    tc = np.array([c(h)[:3] for h in terre])[idx]
    leaf[..., :3] = leaf[..., :3] * (1 - E[..., None] * 0.92) + tc * E[..., None] * 0.92
    # craquelures : du bas vers le haut, brun sombre, arête claire dessous (relief)
    cr = lisse(0.20, 0.52, t)
    fis = bord & (E > 0.6) & ((LH - yy) / LH < cr * 1.25)
    leaf[..., :3] = np.where(fis[..., None], c("3a2414")[:3], leaf[..., :3])
    sous = np.zeros_like(fis); sous[1:, :] = fis[:-1, :]
    leaf[..., :3] = np.where((sous & ~fis)[..., None], np.clip(leaf[..., :3] * 1.16, 0, 1), leaf[..., :3])
    ombre = np.zeros_like(fis); ombre[:-1, :] = fis[1:, :]
    leaf[..., :3] = np.where((ombre & ~fis)[..., None], leaf[..., :3] * 0.86, leaf[..., :3])
    rs = np.random.RandomState(54)
    reste = leaf.copy()
    chute = vide(CW, CH)
    for i, (px, py) in enumerate(P):
        if py < LH * 0.34:
            continue
        ts = 0.44 + 0.38 * ((LH - py) / LH) + rs.uniform(-0.04, 0.04)
        sp = rs.uniform(-1, 1)
        if t < ts:
            continue
        m = lab == i
        reste[m] = 0
        dt = t - ts
        dy = 260 * dt * dt + 10 * dt
        ero = dt * 20                                    # l'éclat s'effrite par ses bords en tombant
        keep = m & (edist >= ero) & (leaf[..., 3] > 0)
        ys, xs = np.nonzero(keep)
        ddx, ddy = round(sp * dt * 12), round(dy)          # translation entière : l'éclat reste plein
        for y, x in zip(ys, xs):
            X = OX + x + ddx
            Y = OY + y + ddy
            if Y < CH:
                point(chute, X, Y, np.concatenate([leaf[y, x, :3], [1.0]]).astype(np.float32))
        # poussière : quelques grains arrachés au bord de l'éclat, qui traînent derrière lui
        if dt > 0.02:
            grains = np.nonzero(m & (edist < ero) & (edist >= ero - 2.5))
            for k, (y, x) in enumerate(zip(*grains)):
                if (k * 7 + i) % 6:
                    continue
                X = OX + x + (x - px) * dt * 2 + math.sin(k + dt * 9) * 2
                Y = OY + y + dy * 0.75 - (k % 5) * 2
                if Y < CH:
                    point(chute, X, Y, c("c8a878" if k % 2 else "8c6a44"), 0.75)
    a = case(reste)
    over(a, chute)
    # voile de poussière sous la feuille
    if t > 0.48:
        rsd = np.random.RandomState(55)
        ph = (t - 0.48) / 0.52
        for k in range(30):
            u = (ph * 0.8 + rsd.uniform(0, 1)) % 1.0
            x = OX + LW / 2 + rsd.uniform(-LW * 0.6, LW * 0.6) + math.sin(u * 4 + k) * 3
            y = OY + LH * 0.5 + u * 64
            al = 0.35 * math.sin(math.pi * u) * min(1.0, ph * 2.5)
            point(a, x, y, c("b89a72" if k % 3 else "8a6a46"), al)
    return a


# ═══════════════════════════════════════ Raiton ═══════════════════════════════════════

def zigzags():
    """Trois grands plis en zigzag (comme des éclairs) qui traversent toute la largeur de la feuille."""
    rs = np.random.RandomState(81)
    zs = []
    for k, y0 in enumerate((22, 42, 62)):
        pts = []
        x = -2.0
        y = y0 + rs.uniform(-2, 2)
        sgn = 1 if k % 2 == 0 else -1
        while x < LW + 2:
            pts.append((x, y))
            x += rs.uniform(6, 9)
            y += sgn * rs.uniform(4, 7)
            sgn = -sgn
        pts.append((x, y))
        zs.append(pts)
    return zs


ZZ = None


def masque_zz(pts, ep=0.8):
    m = np.zeros((LH, LW), bool)
    for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
        n = int(max(abs(xb - xa), abs(yb - ya)) * 2) + 1
        for s in range(n + 1):
            u = s / n
            x, y = xa + (xb - xa) * u, ya + (yb - ya) * u
            if 0 <= int(x) < LW and 0 <= int(y) < LH:
                m[int(y), int(x)] = True
    return m



def raiton(f):
    t = f / (NF - 1)
    leaf = BASE.copy()
    yy, xx = np.mgrid[0:LH, 0:LW].astype(np.float32)
    # 1. les plis en zigzag se tracent l'un après l'autre (l'éclair court de gauche à droite) : un pli = une arête
    #    sombre, la pente ombrée dessous, une arête claire dessus
    for k, pts in enumerate(ZZ):
        t0 = 0.03 + k * 0.08
        p = lisse(t0, t0 + 0.10, t)
        if p <= 0:
            continue
        xs_ = max(1, int(round((LW + 4) * p)))
        m = masque_zz(pts) & (xx < xs_ - 2)
        haut = np.zeros_like(m); haut[:-1, :] = m[1:, :]
        bas1 = np.zeros_like(m); bas1[1:, :] = m[:-1, :]
        bas2 = np.zeros_like(m); bas2[2:, :] = m[:-2, :]
        leaf[..., :3] = np.where((bas2 & ~m)[..., None], leaf[..., :3] * 0.90, leaf[..., :3])
        leaf[..., :3] = np.where((bas1 & ~m)[..., None], leaf[..., :3] * 0.80, leaf[..., :3])
        leaf[..., :3] = np.where((haut & ~m)[..., None], np.clip(leaf[..., :3] * 1.06 + 0.02, 0, 1), leaf[..., :3])
        leaf[..., :3] = np.where(m[..., None], leaf[..., :3] * 0.58, leaf[..., :3])
    # 2. froissage : facettes contrastées + contour qui se rétracte vers les doigts en boule à facettes
    cr = lisse(0.36, 0.90, t)
    lab, bord, P = voronoi(LW, LH, 40, 31)
    rs = np.random.RandomState(32)
    b = rs.uniform(-1, 1, len(P))
    fac = 1 + cr * 0.30 * b[lab]
    leaf[..., :3] = np.clip(leaf[..., :3] * fac[..., None], 0, 1)
    pli = bord & (leaf[..., 3] > 0) & (cr > 0.05)
    leaf[..., :3] = np.where(pli[..., None], leaf[..., :3] * (1 - 0.35 * cr), leaf[..., :3])
    hrL = agrandir(leaf)
    X, Y = grille_hr()
    cyb = 10.0                                            # centre de la boule, sous les doigts
    Xc, Yc = X - LW / 2, Y - cyb
    th = np.arctan2(Xc, Yc)
    rsn = np.random.RandomState(34)
    lobes = rsn.uniform(-1, 1, 24)
    ia = ((th + math.pi) / (2 * math.pi) * 24).astype(int) % 24
    dent = lobes[ia] * 0.06 + np.abs(((th * 9 / math.pi) % 2) - 1) * 0.04
    s = 1 - cr * (0.52 + dent)
    wr = bruit(CW * SS, CH * SS, 5 * SS, 33, 2) - 0.5
    sx = Xc / s + LW / 2 + wr * 2.4 * cr
    sy = Yc / (s * (1 - 0.18 * cr)) + cyb + wr * 2.0 * cr
    a = case(reduire(echantillon(hrL, sx * SS, sy * SS)))
    # 3. arcs électriques le long des plis, puis autour de la boule ; étincelles
    if t < 0.88:
        rf = np.random.RandomState(100 + f)
        actifs = [k for k in range(len(ZZ)) if t > 0.03 + k * 0.08]
        for k in actifs:
            pts = ZZ[k]
            j = rf.randint(0, len(pts) - 1)
            x, y = pts[j]
            if t >= 0.36:
                x = LW / 2 + (x - LW / 2) * (1 - cr * 0.52)
                y = cyb + (y - cyb) * (1 - cr * 0.52) * (1 - 0.18 * cr)
            seg = [(OX + x, OY + y)]
            ang = rf.uniform(0, 2 * math.pi)
            for _ in range(rf.randint(3, 6)):
                ang += rf.uniform(-1.4, 1.4)
                L = rf.uniform(2, 4)
                seg.append((seg[-1][0] + L * math.cos(ang), seg[-1][1] + L * math.sin(ang)))
            for (px, py) in seg:
                for (ddx, ddy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    point(a, px + ddx, py + ddy, c("ffd23a"), 0.55)
            ligne(a, seg, c("fffbe0"))
        for _ in range(3):
            point(a, OX + rf.uniform(-4, LW + 4), OY + rf.uniform(0, LH * (1 - cr * 0.5)), c("fff2a0"), 0.95)
    return a


# ═══════════════════════════════════════ Fūton ═══════════════════════════════════════
def futon(f):
    t = f / (NF - 1)
    hrL = agrandir(BASE)
    X, Y = grille_hr()
    # la coupe : droite en diagonale, du bord gauche (y = 30) au bord droit (y = 46)
    ya, yb = 30.0, 46.0
    coupe = lambda x: ya + (yb - ya) * x / LW  # noqa: E731
    pente = math.atan2(yb - ya, LW)
    # souffle avant la coupe : la feuille ondule
    flot = 1.4 * math.sin(math.pi * min(1.0, t / 0.18)) if t < 0.18 else 0.0
    sep = lisse(0.26, 1.0, t)
    a_hr = vide(CW * SS, CH * SS)
    for moitie in (0, 1):           # 0 = haut (reste), 1 = bas (s'envole)
        if moitie == 0:
            # balancement amorti du haut après la coupe
            ang = math.radians(4.0) * math.sin((t - 0.26) * 22) * math.exp(-(t - 0.26) * 5) if t > 0.26 else 0.0
            tx, ty = 0.0, 0.0
            px, py = LW / 2, 0.0
        else:
            u = sep
            d = 14 * u + 60 * u * u                         # glisse le long de la coupe puis s'envole
            tx = math.cos(pente) * d * 0.6 + 40 * u * u
            ty = math.sin(pente) * d * 0.6 + 80 * u * u
            ang = math.radians(-26) * u * u + math.radians(6) * math.sin(u * 7) * u
            px, py = LW, yb
        dx, dy = X - px - tx, Y - py - ty
        sx = px + dx * math.cos(-ang) - dy * math.sin(-ang)
        sy = py + dx * math.sin(-ang) + dy * math.cos(-ang)
        sx = sx - flot * np.sin(sy / LH * math.pi * 3 + f * 0.9)
        part = echantillon(hrL, sx * SS, sy * SS)
        cote = sy < coupe(sx) if moitie == 0 else sy >= coupe(sx)
        if t >= 0.24:
            part[~cote] = 0
            # arête nette de la coupe
            prox = np.abs(sy - coupe(sx)) < 1.0
            ar = prox & cote & (part[..., 3] > 0)
            part[ar, :3] = (c("ffffff")[:3] if moitie == 0 else c("b8b0a4")[:3])
        elif moitie == 1:
            continue
        # la moitié qui s'envole pâlit en sortant de la case
        over(a_hr, part)
    a = case(reduire(a_hr, seuil=0.45))
    # le trait de la coupe : un éclat blanc vert qui traverse de gauche à droite
    if 0.14 < t < 0.34:
        p = lisse(0.14, 0.24, t)
        fade = 1.0 if t < 0.26 else 1 - (t - 0.26) / 0.08
        xs = -14 + (LW + 28) * p
        for x in np.arange(-14, xs, 0.5):
            y = coupe(x)
            al = fade * max(0.0, 1 - (xs - x) / 26)
            point(a, OX + x, OY + y, c("ffffff"), 0.95 * al)
            point(a, OX + x, OY + y - 1, c("9af8c8"), 0.45 * al)
            point(a, OX + x, OY + y + 1, c("9af8c8"), 0.45 * al)
    # traînées de vent
    rs = np.random.RandomState(9)
    for i in range(8):
        t0 = -0.05 + i * 0.12
        if not (t0 < t < t0 + 0.36):
            continue
        s = (t - t0) / 0.36
        y = OY - 6 + rs.uniform(0, LH + 18)
        L = 16 + rs.uniform(0, 14)
        x = -L + s * (CW + L)
        for k in range(int(L)):
            al = 0.75 * math.sin(math.pi * k / L) * (1 - abs(2 * s - 1) ** 3)
            yk = y + 1.4 * math.sin((x + k) / 9 + i)
            point(a, x + k, yk, c("eafff4"), al)
            point(a, x + k, yk + 1, c("6ee0a8"), al * 0.45)
    return a


REACTIONS = {"katon": katon, "futon": futon, "raiton": raiton, "doton": doton, "suiton": suiton}


# ═══════════════════════════════════════ lueur de chakra ═══════════════════════════════════════
def distance(m, rmax):
    """Distance euclidienne au masque (jusqu'à rmax, au-delà : rmax + 1), sans scipy."""
    H, W = m.shape
    ys, xs = np.nonzero(m)
    d = np.full((H, W), float(rmax + 1), np.float32)
    yy, xx = np.mgrid[0:H, 0:W]
    for dy in range(-rmax, rmax + 1):
        for dx in range(-rmax, rmax + 1):
            r = math.hypot(dx, dy)
            if r > rmax:
                continue
            sh = np.zeros_like(m)
            y0, y1 = max(0, dy), min(H, H + dy)
            x0, x1 = max(0, dx), min(W, W + dx)
            sh[y0:y1, x0:x1] = m[y0 - dy:y1 - dy, x0 - dx:x1 - dx]
            d = np.where(sh & (d > r), r, d)
    return d


def aura(k):
    """Contour vivant de chakra autour de la feuille (et des doigts), image k sur 4."""
    m = np.zeros((CH, CW), bool)
    m[OY:OY + LH, OX:OX + LW] = BASE[..., 3] > 0.5
    m |= (DOIGT_AV[..., 3] > 0.5) | (DOIGT_AR[..., 3] > 0.5)
    m[:OY - 8, :] = False                                   # pas de lueur sur la main hors champ
    dist = distance(m, 12)
    yy, xx = np.mgrid[0:CH, 0:CW].astype(np.float32)
    n = bruit(CW, CH * 2, 5, 90, 2)
    off = int(k * CH / 2)
    nz = np.roll(n, -off, 0)[:CH]                           # le bruit monte : flammèches de chakra
    lim = 3.2 + 4.2 * nz + 1.6 * (yy < OY + 10)
    v = np.clip(1 - dist / lim, 0, 1)
    v[m] = 0
    v = v ** 1.3
    out = vide(CW, CH)
    for seuil, col, al in ((0.02, "1f5fb8", 0.30), (0.22, "3c8ce8", 0.55), (0.48, "7cc4ff", 0.80), (0.74, "d6f0ff", 0.95)):
        sel = v > seuil
        out[sel] = c(col, al)
    return out


# ═══════════════════════════════════════ pièces d'interface ═══════════════════════════════════════
def pieces():
    """192×48 : cabochon de touche (0..23 × 0..13 : gauche 0-5, milieu 6-17, droite 18-23), cadre de jauge
    (0..159 × 16..25), remplissage (0..159 × 28..33), filet d'or (0..95 × 40), pastille 7×7 (160, 16)."""
    im = nouveau(192, 48)
    px = np.zeros((48, 192, 4), np.uint8)
    def hx(h, a=255):
        h = h.lstrip("#")
        return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a], np.uint8)
    # cabochon 24×14 : laque sombre, liseré or, ombre d'1 GUI
    for x in range(24):
        for y in range(14):
            bord = x in (0, 23) or y in (0, 12)
            coin = (x in (0, 23)) and (y in (0, 12))
            if y == 13:
                if 1 <= x <= 22:
                    px[y, x] = hx("05020A", 150)
                continue
            if coin:
                continue
            if bord:
                px[y, x] = hx("D9A95E" if y == 0 or x == 0 else "8E6A30")
            else:
                px[y, x] = hx("1E1214" if y > 6 else "2A1A1E")
    # cadre de jauge 160×10 : fond nuit, liseré or 1 GUI, petites encoches au quart
    for x in range(160):
        for y in range(10):
            yy = 16 + y
            bord = x in (0, 159) or y in (0, 9)
            coin = x in (0, 159) and y in (0, 9)
            if coin:
                continue
            if bord:
                px[yy, x] = hx("D9A95E" if y == 0 else "8E6A30") if not (x in (0, 159)) else hx("B08442")
            else:
                px[yy, x] = hx("0E080C", 230)
        if x in (40, 80, 120):
            px[17, x] = hx("6A4E24")
            px[24, x] = hx("6A4E24")
    # remplissage 160×6 : chakra (bleu profond → bleu clair), reflet en haut
    for x in range(160):
        for y in range(6):
            col = ["9ADCFF", "6CC4FF", "4AA6F2", "3488DC", "2A6EC4", "1F56A6"][y]
            px[28 + y, x] = hx(col)
    # filet d'or 96×1
    for x in range(96):
        a = int(255 * min(1.0, min(x, 95 - x) / 24))
        px[40, x] = hx("D9A95E", a)
    # pastille 7×7 (losange d'or)
    for y in range(7):
        for x in range(7):
            if abs(x - 3) + abs(y - 3) <= 3:
                px[16 + y, 160 + x] = hx("F2D08A" if abs(x - 3) + abs(y - 3) <= 1 else "D9A95E" if x + y < 6 else "8E6A30")
    return Image.fromarray(px, "RGBA")


# ═══════════════════════════════════════ sorties ═══════════════════════════════════════
def en_image(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


def atlas(frames, cols=COLS):
    rows = (len(frames) + cols - 1) // cols
    im = nouveau(CW * cols, CH * rows)
    for i, fr in enumerate(frames):
        im.alpha_composite(en_image(fr), ((i % cols) * CW, (i // cols) * CH))
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


def gif(frames, chemin, echelle=3, fond=(14, 10, 12), tenir=14, ms=70):
    imgs = []
    seq = [frames[0]] * 6 + list(frames) + [frames[-1]] * tenir
    for fr in seq:
        b = Image.new("RGBA", (CW, CH), fond + (255,))
        b.alpha_composite(en_image(fr))
        imgs.append(b.convert("RGB").resize((CW * echelle, CH * echelle), Image.NEAREST))
    imgs[0].save(chemin, save_all=True, append_images=imgs[1:], duration=ms, loop=0)


def main():
    global BASE, DOIGT_AR, DOIGT_AV, ZZ
    BASE = feuille_base()
    DOIGT_AR = calque_doigts(True)
    DOIGT_AV = calque_doigts(False)
    ZZ = zigzags()
    intacte = case(BASE)
    S.sauver("feuille.png", en_image(intacte), (CW, CH))
    seule = [n for n in sys.argv[1:] if n in NATURES]
    toutes = {}
    for n in (seule or NATURES):
        frames = [REACTIONS[n](f) for f in range(NF)]
        toutes[n] = frames
        S.sauver(f"feuille_{n}.png", atlas(frames), (CW * COLS, CH * (NF // COLS)))
    S.sauver("aura.png", atlas([aura(k) for k in range(4)], cols=4), (CW * 4, CH))
    S.sauver("pieces.png", pieces(), (192, 48))
    S.sauver("halo.png", halo(), (64, 64))
    S.sauver("bouton.png", ja.plaque_etats(96, 16, ("repos", "survol"), pas=20), (100, 40))
    S.bilan()
    os.makedirs(MAQ, exist_ok=True)
    for n, frames in toutes.items():
        gif(frames, os.path.join(MAQ, f"reaction_{n}.gif"))
    # planche : 1 image sur 2 de chaque nature
    pas = 2
    pl = Image.new("RGBA", (CW * (NF // pas) + 20, (CH + 6) * len(toutes) + 14), (14, 10, 12, 255))
    for r, n in enumerate(toutes):
        for i, fr in enumerate(toutes[n][::pas]):
            pl.alpha_composite(en_image(fr), (10 + i * CW, 7 + r * (CH + 6)))
    pl.convert("RGB").save(os.path.join(MAQ, "planche_reactions.png"))
    print("GIF ->", MAQ)


if __name__ == "__main__":
    main()
