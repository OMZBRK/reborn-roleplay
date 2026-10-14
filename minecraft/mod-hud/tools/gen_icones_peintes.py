# -*- coding: utf-8 -*-
"""
Icônes « peintes » de Reborn, dans le style des trois icônes dessinées par l'utilisateur
(textures/gui/ability/dash.png, double_jump.png, chakra_jump.png) :

  - cadre de 2 px (32 px) ou 1 px (16 px) recopié du gabarit de l'utilisateur : bandes horizontales
    clair → foncé de haut en bas, coins transparents, petit reflet à droite ; couleur par famille ;
  - fond intérieur sombre, teinté par la famille, en deux tons séparés par une ligne horizontale ;
  - sujet PEINT : dessiné en grand (sur-échantillonnage ×8), dégradés, reflets, lueurs additives, puis réduit
    (moyenne par blocs) — c'est ce qui donne le rendu doux de chakra_jump.png ;
  - animation : images empilées verticalement (comme dash.png), 4 images.

Aucun kanji (DA §3). Sortie : Maquettes/icones/png/ (pas les assets du mod tant que ce n'est pas validé).

    python tools/gen_icones_peintes.py            # icônes + GIF + planche
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
DEPOT = os.path.normpath(os.path.join(ICI, "..", "..", ".."))
MAQ = os.path.normpath(os.path.join(DEPOT, "..", "Maquettes", "icones"))
OUT_PNG = os.path.join(MAQ, "png")
OUT_GIF = os.path.join(MAQ, "gif")
ABILITY = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "ability")

SS = 8          # sur-échantillonnage du « tableau »
U = 100.0       # unités de dessin : 0..100 sur la zone intérieure


def hx(s):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32) / 255.0


# ─────────────────────────────── cadre : gabarit exact de l'utilisateur ───────────────────────────────
# Côté gauche/droit, y = 0..31 : a = haut, b = reflet, c = milieu, d = sombre, e = bas, g = éclat (droite, y 17)
GABARIT_COTE = ["T", "a", "a", "b", "b", "b", "a", "b", "b", "c", "c", "c", "c", "c", "c", "c",
                "d", "c", "d", "d", "d", "d", "d", "d", "d", "e", "d", "e", "e", "e", "e", "T"]

FAMILLES = {
    # cadres : 3 relevés sur les icônes de l'utilisateur, les autres dérivés avec les mêmes écarts de valeur
    "rouge":     dict(a="a73333", b="bd4040", c="9a0505", d="720404", e="610202", g="ab0808",
                      haut="353331", bas="1f1d1b", teinte="ff5a3c"),
    "brun":      dict(a="310e00", b="3b1300", c="1e0500", d="110000", e="0c0000", g="1e0500",
                      haut="3a2416", bas="22140c", teinte="ffa040"),
    "bleu":      dict(a="0021df", b="0429fe", c="0019a4", d="00127b", e="001068", g="001cb8",
                      haut="1b2d52", bas="12233f", teinte="3cc8ff"),
    "katon":     dict(a="c4521e", b="e0662a", c="a83006", d="7e2204", e="681a02", g="c03a0a",
                      haut="3a2420", bas="221512", teinte="ff7a1e"),
    "suiton":    dict(a="1a86c0", b="22a0e0", c="066094", d="044670", e="033a5e", g="0870aa",
                      haut="16304a", bas="0e2033", teinte="4ad8ff"),
    "doton":     dict(a="8a5a2c", b="a06a34", c="6a4018", d="4e2e10", e="40250c", g="7a4a1c",
                      haut="2a221c", bas="17120e", teinte="d8a060"),
    "futon":     dict(a="6cb88a", b="84d0a0", c="3c8c5e", d="2a6a44", e="225838", g="469a6a",
                      haut="22332b", bas="16221c", teinte="b8ffd0"),
    "raiton":    dict(a="d0aa22", b="ecc830", c="ac8406", d="806004", e="684e02", g="c09608",
                      haut="2e2c28", bas="1c1b18", teinte="fff27a"),
    "or":        dict(a="c49a3c", b="dcb24e", c="9e7414", d="76560a", e="604606", g="b08418",
                      haut="2f2a22", bas="1d1a15", teinte="ffd86a"),
    "vermillon": dict(a="c8462c", b="e05638", c="a82810", d="7e1e0a", e="661606", g="bc300e",
                      haut="33241f", bas="1f1613", teinte="ff6a40"),
    "ambre":     dict(a="c8841e", b="e09a2c", c="a06208", d="784a04", e="603c02", g="b47008",
                      haut="302820", bas="1e1914", teinte="ffb440"),
    "gris":      dict(a="7a7874", b="8e8c88", c="5a5856", d="42403e", e="363432", g="64625e",
                      haut="2c2b2a", bas="1c1b1a", teinte="c8c8c8"),
}


def cadre(n, fam):
    """Cadre RGBA n×n (32 → 2 px, 16 → 1 px), intérieur transparent."""
    p = FAMILLES[fam]
    col = {k: tuple(int(p[k][i:i + 2], 16) for i in (0, 2, 4)) + (255,) for k in "abcdeg"}
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    ep = 2 if n >= 32 else 1
    for y in range(n):
        k = GABARIT_COTE[min(31, round(y * 31 / (n - 1)))]
        for x in range(n):
            bord_h = y < ep or y >= n - ep
            bord_v = x < ep or x >= n - ep
            if not (bord_h or bord_v):
                continue
            if y < ep:
                c = "a"
            elif y >= n - ep:
                c = "e"
            else:
                c = k
            if c == "T":
                c = "a" if y < n / 2 else "e"
            # éclat : côté droit, à la hauteur 17/32
            if x >= n - ep and ep < y < n - ep and round(y * 31 / (n - 1)) == 17:
                c = "g"
            px[x, y] = col[c]
    for (x, y) in ((0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)):
        px[x, y] = (0, 0, 0, 0)
    return im


# ─────────────────────────────── le « tableau » : peinture sur-échantillonnée ───────────────────────────────
class Toile:
    def __init__(self, n, fam, seed=1):
        self.n = n                       # côté intérieur en pixels finaux
        self.S = n * SS
        self.fam = fam
        self.p = FAMILLES[fam]
        self.rng = np.random.default_rng(seed)
        S = self.S
        yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
        self.X = (xx + 0.5) / S * U
        self.Y = (yy + 0.5) / S * U
        self.c = np.zeros((S, S, 3), np.float32)

    # ── conversions ──
    def k(self, v):
        return v / U * self.S

    def pts(self, P):
        return [(self.k(x), self.k(y)) for (x, y) in P]

    # ── masques ──
    def _m(self, draw_fn):
        im = Image.new("L", (self.S, self.S), 0)
        draw_fn(ImageDraw.Draw(im))
        return np.asarray(im, np.float32) / 255.0

    def poly(self, P):
        return self._m(lambda d: d.polygon(self.pts(P), fill=255))

    def ell(self, cx, cy, rx, ry=None):
        ry = rx if ry is None else ry
        return self._m(lambda d: d.ellipse([self.k(cx - rx), self.k(cy - ry), self.k(cx + rx), self.k(cy + ry)], fill=255))

    def trait(self, P, w):
        def f(d):
            d.line(self.pts(P), fill=255, width=max(1, int(self.k(w))), joint="curve")
            r = self.k(w) / 2
            for (x, y) in (self.pts(P)[0], self.pts(P)[-1]):
                d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        return self._m(f)

    def flou(self, m, r):
        if r <= 0:
            return m
        im = Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8))
        im = im.filter(ImageFilter.GaussianBlur(self.k(r)))
        return np.asarray(im, np.float32) / 255.0

    def decale(self, m, dx, dy):
        sx, sy = int(round(self.k(dx))), int(round(self.k(dy)))
        o = np.zeros_like(m)
        H = m.shape[0]
        ys = slice(max(0, sy), H + min(0, sy)); yd = slice(max(0, -sy), H + min(0, -sy))
        xs = slice(max(0, sx), H + min(0, sx)); xd = slice(max(0, -sx), H + min(0, -sx))
        o[ys, xs] = m[yd, xd]
        return o

    # ── couleurs ──
    def lin(self, x0, y0, x1, y1, stops):
        """Dégradé linéaire ; stops = [(t, 'rrggbb'), …]."""
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1
        t = np.clip(((self.X - x0) * dx + (self.Y - y0) * dy) / L2, 0, 1)
        return self._rampe(t, stops)

    def rad(self, cx, cy, r, stops):
        t = np.clip(np.hypot(self.X - cx, self.Y - cy) / r, 0, 1)
        return self._rampe(t, stops)

    def _rampe(self, t, stops):
        ts = [s[0] for s in stops]
        cs = [hx(s[1]) for s in stops]
        out = np.zeros(t.shape + (3,), np.float32)
        for ch in range(3):
            out[..., ch] = np.interp(t, ts, [c[ch] for c in cs])
        return out

    # ── peinture ──
    def peindre(self, m, col, a=1.0):
        if isinstance(col, str):
            col = hx(col)
        m = np.clip(m, 0, 1)[..., None] * a
        self.c = self.c * (1 - m) + np.asarray(col, np.float32) * m

    def lueur(self, m, col, k=1.0, r=6):
        """Lueur additive (flou du masque)."""
        if isinstance(col, str):
            col = hx(col)
        g = self.flou(m, r)[..., None]
        self.c = np.clip(self.c + g * np.asarray(col, np.float32) * k, 0, 1)

    def gonfle(self, m, w):
        """Dilatation du masque de w unités."""
        im = Image.fromarray((np.clip(m, 0, 1) * 255).astype(np.uint8))
        k = int(self.k(w)) * 2 + 1
        return np.asarray(im.filter(ImageFilter.MaxFilter(k)), np.float32) / 255.0

    def contour(self, m, w=2.0, col="0a1020", a=0.9):
        """Contour sombre (lisibilité à petite taille), peint sous le sujet."""
        self.peindre(np.clip(self.gonfle(m, w) - m, 0, 1), col, a)

    def ombre(self, m, dx=1.5, dy=2.0, r=2.5, a=0.55):
        """Ombre portée douce sous un sujet."""
        s = self.flou(self.decale(m, dx, dy), r)
        self.c = self.c * (1 - s[..., None] * a)

    def reflet(self, m, dx, dy, col="ffffff", a=0.8, r=0.0):
        """Liseré de lumière sur le bord tourné vers (−dx, −dy) : m − m décalé."""
        e = np.clip(m - self.decale(m, dx, dy), 0, 1)
        if r:
            e = self.flou(e, r) * m
        self.peindre(e, col, a)

    def creux(self, m, dx, dy, col="000000", a=0.45, r=1.0):
        """Ombre propre sur le bord opposé à la lumière."""
        e = np.clip(m - self.decale(m, -dx, -dy), 0, 1)
        if r:
            e = self.flou(e, r) * m
        self.peindre(e, col, a)

    # ── fond façon utilisateur ──
    def fond(self, stries=0, sol=True, halo=None, sep=50.0):
        p = self.p
        haut, bas = hx(p["haut"]), hx(p["bas"])
        t = np.clip(self.Y / sep, 0, 1)[..., None]
        top = haut * (1.10 - 0.18 * t)                       # léger dégradé vers la séparation
        bot = bas * (1.0 - 0.10 * np.clip((self.Y - sep) / (U - sep), 0, 1))[..., None]
        self.c = np.where((self.Y < sep)[..., None], top, bot).astype(np.float32)
        if stries:
            # stries horizontales (traînées de vitesse), comme sur chakra_jump.png
            for i in range(stries):
                y = self.rng.uniform(8, 92)
                L = self.rng.uniform(25, 70)
                x0 = 0 if self.rng.random() < 0.6 else U - L
                band = ((self.Y > y) & (self.Y < y + 3.6) & (self.X > x0) & (self.X < x0 + L)).astype(np.float32)
                band = band * np.clip(1 - np.abs(self.X - (x0 + L / 2)) / (L / 2), 0, 1) ** 0.5
                self.c = np.clip(self.c + band[..., None] * hx(p["teinte"]) * 0.07, 0, 1)
        if sol:
            # bande de lumière au sol (sous le sujet), rangées 21-24 de chakra_jump
            g = np.exp(-((self.Y - 84) / 5.0) ** 2) * np.exp(-((self.X - 50) / 38.0) ** 2)
            self.c = np.clip(self.c + g[..., None] * hx(p["teinte"]) * 0.16, 0, 1)
        if halo:
            cx, cy, r, k = halo
            g = np.exp(-(np.hypot(self.X - cx, self.Y - cy) / r) ** 2)
            self.c = np.clip(self.c + g[..., None] * hx(p["teinte"]) * k, 0, 1)

    # ── réduction ──
    def reduire(self, nettete=0.5):
        n, s = self.n, SS
        small = self.c.reshape(n, s, n, s, 3).mean(axis=(1, 3))
        im = Image.fromarray((np.clip(small, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")
        if nettete:
            im = im.filter(ImageFilter.UnsharpMask(radius=1, percent=int(nettete * 100), threshold=2))
        return im


def assembler(toile, n_tot, nettete=0.5):
    """Intérieur peint + cadre de la famille → icône RGBA n_tot × n_tot."""
    ep = 2 if n_tot >= 32 else 1
    im = Image.new("RGBA", (n_tot, n_tot), (0, 0, 0, 0))
    im.paste(toile.reduire(nettete).convert("RGBA"), (ep, ep))
    im.alpha_composite(cadre(n_tot, toile.fam))
    return im


# ─────────────────────────────── outils de formes ───────────────────────────────
def rot(P, ang, cx=0, cy=0, tx=0, ty=0, sx=1.0, sy=1.0):
    c, s = math.cos(ang), math.sin(ang)
    return [(tx + (x * sx) * c - (y * sy) * s, ty + (x * sx) * s + (y * sy) * c) for (x, y) in P]


def bezier(P, n=24):
    """Courbe de Bézier (degré quelconque) échantillonnée."""
    out = []
    for i in range(n + 1):
        t = i / n
        Q = list(P)
        while len(Q) > 1:
            Q = [((1 - t) * a[0] + t * b[0], (1 - t) * a[1] + t * b[1]) for a, b in zip(Q, Q[1:])]
        out.append(Q[0])
    return out


def goutte(cx, cy, R, ang_queue, queue=1.0, p=3.0, rides=0.0, kr=7, phase=0.0, n=160):
    """Silhouette de flamme / goutte : cercle étiré vers ang_queue, bord ondulé."""
    P = []
    for i in range(n):
        th = 2 * math.pi * i / n
        d = math.cos(th - ang_queue)
        r = R * (1 + queue * max(0.0, d) ** p)
        r *= 1 + rides * math.sin(kr * th + phase) * (0.3 + 0.7 * max(0.0, d))
        P.append((cx + r * math.cos(th), cy + r * math.sin(th)))
    return P


# ═══════════════════════════════════════ sujets 32 px (recharges) ═══════════════════════════════════════
LUMIERE = (-0.7071, -0.7071)      # la lumière vient d'en haut à gauche, comme sur les icônes de l'utilisateur


def _kunai(t, ang, cx, cy, L=1.0, lame=("f4f8fa", "a8b4bc", "56626c", "7c8890")):
    """Un kunai peint : lame à deux pans (biseau), arête claire, manche bandé, anneau."""
    def R(P):
        return rot(P, ang, tx=cx, ty=cy, sx=L, sy=L)
    blade = [(52, 0), (26, 11), (11, 7), (5, 3.6), (5, -3.6), (11, -7), (26, -11)]
    up = [(52, 0), (26, -11), (11, -7), (5, -3.6), (5, 0)]
    lo = [(52, 0), (26, 11), (11, 7), (5, 3.6), (5, 0)]
    manche = [(6, -3.6), (6, 3.6), (-31, 3.2), (-31, -3.2)]
    mb = t.poly(R(blade)); mm = t.poly(R(manche))
    ring_o = t.poly(R(goutte(-36.5, 0, 5.6, 0, 0, n=48)))
    ring_i = t.poly(R(goutte(-36.5, 0, 2.6, 0, 0, n=48)))
    mr = np.clip(ring_o - ring_i, 0, 1)
    tout = np.clip(mb + mm + mr, 0, 1)
    t.ombre(tout, 2.0, 2.6, 2.2, 0.6)
    # quel pan regarde la lumière ?
    nx, ny = rot([(0, -1)], ang)[0]
    pan_clair, pan_sombre = (up, lo) if nx * LUMIERE[0] + ny * LUMIERE[1] < 0 else (lo, up)
    tip = R([(52, 0)])[0]; base = R([(6, 0)])[0]
    t.peindre(t.poly(R(pan_clair)), t.lin(base[0], base[1], tip[0], tip[1], [(0, lame[1]), (0.55, lame[0]), (1, "ffffff")]))
    t.peindre(t.poly(R(pan_sombre)), t.lin(base[0], base[1], tip[0], tip[1], [(0, lame[2]), (1, lame[3])]))
    t.peindre(t.trait(R([(7, 0), (47, 0)]), 1.1), "ffffff", 0.55)             # arête
    t.reflet(mb, 1.2 * -LUMIERE[0], 1.2 * -LUMIERE[1], "ffffff", 0.7)
    # manche : bande sombre + spires claires
    t.peindre(mm, "2c1f1b")
    for i in range(6):
        x = 3 - i * 5.6
        sp = [(x, -3.6), (x - 2.6, -3.6), (x - 4.6, 3.6), (x - 2.0, 3.6)]
        t.peindre(t.poly(R(sp)) * mm, "8c7660", 0.95)
    t.reflet(mm, 0.9 * -LUMIERE[0], 0.9 * -LUMIERE[1], "d8c4a6", 0.45)
    # anneau
    rc = R([(-36.5, 0)])[0]
    t.peindre(mr, t.rad(rc[0] - 3, rc[1] - 3, 11, [(0, "e6eef2"), (0.6, "8a96a0"), (1, "3e4850")]))
    t.reflet(mr, 0.8 * -LUMIERE[0], 0.8 * -LUMIERE[1], "ffffff", 0.5)
    return tout


def ic_combat(t, f=0, nf=1):
    t.fond(halo=(50, 42, 18, 0.22))
    # éclair d'impact derrière le croisement
    t.lueur(t.ell(50, 40, 8), "ff7a3a", 0.9, 5)
    _kunai(t, math.radians(-135), 53, 50, 1.0)
    _kunai(t, math.radians(-45), 47, 50, 1.0)
    # étincelles du choc
    star = [(50, 27), (52.3, 37.7), (62, 40), (52.3, 42.3), (50, 52), (47.7, 42.3), (38, 40), (47.7, 37.7)]
    m = t.poly(star)
    t.lueur(m, "ffb060", 1.2, 3.5)
    t.peindre(m, "fff4d8", 0.95)
    for (x, y, r) in ((66, 30, 1.6), (33, 58, 1.4), (70, 52, 1.2), (30, 33, 1.1)):
        e = t.ell(x, y, r)
        t.lueur(e, "ff9040", 1.0, 2.0)
        t.peindre(e, "ffe0a0")


def _echelle(P, s, cx=50, cy=55, dx=0, dy=0):
    f = lambda q: (cx + (q[0] - cx) * s + dx, cy + (q[1] - cy) * s + dy)
    return {k: ([f(q) for q in v] if isinstance(v, list) else f(v)) for k, v in P.items()}


def _corps(t, P, ep=1.0):
    """Masque d'une silhouette humaine à partir de points (tête, cou, bassin, membres) ; ep = épaisseur."""
    m = t.ell(P["tete"][0], P["tete"][1], 8.0 * ep)
    m = np.maximum(m, t.trait([P["cou"], P["bassin"]], 11.5 * ep))
    for k in ("bras1", "bras2"):
        if k in P:
            m = np.maximum(m, t.trait(P[k], 4.6 * ep))
    for k in ("jambe1", "jambe2"):
        if k in P:
            m = np.maximum(m, t.trait(P[k], 6.0 * ep))
    return m


def _pose_course(f):
    """Course ninja (bras tendus en arrière, buste penché), vers la droite ; f = 0..3."""
    j = [
        ([(42, 58), (55, 66), (63, 81)], [(42, 58), (31, 69), (17, 75)]),
        ([(42, 58), (49, 70), (47, 85)], [(42, 58), (38, 70), (29, 81)]),
        ([(42, 58), (31, 69), (17, 75)], [(42, 58), (55, 66), (63, 81)]),
        ([(42, 58), (38, 70), (29, 81)], [(42, 58), (49, 70), (47, 85)]),
    ][f % 4]
    bob = (0, -1.2, 0, -1.2)[f % 4]
    P = dict(tete=(66, 27 + bob), cou=(58, 36 + bob), bassin=(42, 57),
             bras1=[(55, 38 + bob), (40, 43), (22, 45)], bras2=[(54, 41 + bob), (39, 47), (21, 51)],
             jambe1=j[0], jambe2=j[1])
    return P


def ic_course_chakra(t, f=0, nf=4):
    t.fond(stries=0, sol=True)
    # traînées de chakra derrière (défilent d'une image à l'autre)
    for i, y in enumerate((30, 41, 52, 63, 74)):
        x1 = 44 - (i % 2) * 8
        off = ((f * 9 + i * 13) % 36)
        m = t.trait([(-6 + off - 20, y), (x1 - 4 + off * 0.2, y)], 2.6 if i % 2 else 1.8)
        t.lueur(m, "1a8cff", 0.8, 2.5)
        t.peindre(m, "6fd8ff", 0.55)
    P = _echelle(_pose_course(f), 1.12, 46, 56, 2, -2)
    body = _corps(t, P, 1.3)
    # bandeau frontal : les deux pans flottent derrière la tête
    hx_, hy_ = P["tete"]
    ond = (0, 2.0, 0.5, 2.5)[f % 4]
    pan1 = t.trait(bezier([(hx_ - 6, hy_ - 2), (hx_ - 14, hy_ - 6 - ond), (hx_ - 22, hy_ - 2 + ond), (hx_ - 31, hy_ - 7 - ond)], 16), 3.0)
    pan2 = t.trait(bezier([(hx_ - 6, hy_), (hx_ - 13, hy_ + 1 + ond), (hx_ - 20, hy_ + 3 - ond), (hx_ - 27, hy_ + 4 + ond)], 16), 2.6)
    aura = np.maximum(body, np.maximum(pan1, pan2))
    t.lueur(aura, "1f6dff", 1.1, 7)
    t.contour(aura, 1.6, "08122a", 0.85)
    t.peindre(aura, t.lin(hx_ + 6, hy_ - 8, 30, 92, [(0, "f2fdff"), (0.45, "8fdcff"), (1, "2f7fe0")]))
    t.creux(aura, 2.0, 1.6, "123a8a", 0.75, 1.2)       # ombre propre côté dos / dessous
    t.reflet(aura, 1.0, 1.0, "ffffff", 0.7)
    t.peindre(t.trait([(hx_ - 6.5, hy_ - 2.5), (hx_ + 7.5, hy_ - 4.5)], 3.0) * body, "2a4a8a", 0.9)   # plaque du bandeau
    # éclaboussure de chakra sous le pied d'appui
    pied = P["jambe1"][-1] if P["jambe1"][-1][1] > P["jambe2"][-1][1] else P["jambe2"][-1]
    k = (1.0, 0.7, 1.0, 0.7)[f % 4]
    spl = t.ell(pied[0], 88, 10 * k, 2.6)
    t.lueur(spl, "28b4ff", 1.2, 3)
    t.peindre(spl, "a8f0ff", 0.85)
    for (dx, h) in ((-7, 5), (6, 4), (-2, 7)):
        g = t.trait([(pied[0] + dx * k, 87), (pied[0] + dx * k * 1.3, 87 - h * k)], 1.6)
        t.peindre(g, "8ae6ff", 0.8)


def _pose_esquive():
    """Bond de côté, très penché, jambe d'appui fléchie, l'autre tendue derrière."""
    return dict(tete=(63, 22), cou=(57, 35), bassin=(47, 56),
                bras1=[(56, 38), (65, 47), (73, 43)], bras2=[(55, 39), (45, 44), (37, 39)],
                jambe1=[(47, 56), (59, 66), (63, 83)], jambe2=[(47, 56), (35, 68), (25, 81)])


def ic_esquive(t, f=0, nf=1):
    t.fond(stries=4, sol=True)
    P = _echelle(_pose_esquive(), 1.08, 50, 56, 10, 0)
    # images rémanentes à gauche (le corps vient de quitter la place)
    for (dx, a, col) in ((-36, 0.40, "8a1a14"), (-18, 0.70, "d8402c")):
        Q = {k: ([(x + dx, y) for (x, y) in v] if isinstance(v, list) else (v[0] + dx, v[1])) for k, v in P.items()}
        m = _corps(t, Q, 1.15)
        t.peindre(m, col, a)
        t.reflet(m, 1.0, 1.0, "ff9070", a * 0.8)
    m = _corps(t, P, 1.15)
    t.ombre(m, 1.5, 2, 2, 0.6)
    t.contour(m, 1.6, "140a0a", 0.9)
    t.peindre(m, t.lin(40, 20, 60, 85, [(0, "ffffff"), (1, "c9c3c0")]))
    t.creux(m, 1.4, 1.4, "5a4a48", 0.6, 1.0)
    t.reflet(m, 0.8, 0.8, "ffffff", 0.6)
    # arc de vitesse devant
    arc = t.trait(bezier([(80, 16), (96, 46), (82, 86)], 20), 2.4)
    t.lueur(arc, "ff6040", 0.8, 2.5)
    t.peindre(arc, "ffe8e0", 0.85)


def ic_garde(t, f=0, nf=1):
    """Garde : écu d'acier cerclé, coup de lame dévié en gerbe d'étincelles."""
    t.fond(halo=(50, 46, 24, 0.20))
    # trace de la lame qui vient frapper (derrière)
    sl = t.trait(bezier([(96, 8), (70, 18), (58, 34)], 16), 3.0)
    t.lueur(sl, "ff6a40", 0.7, 2.5)
    t.peindre(sl, "ffe8e0", 0.75)
    ecu = bezier([(16, 18), (50, 10), (84, 18)], 16) + bezier([(84, 18), (86, 58), (50, 92)], 20)         + bezier([(50, 92), (14, 58), (16, 18)], 20)
    m = t.poly(ecu)
    t.ombre(m, 2.2, 2.8, 2.5, 0.65)
    t.contour(m, 1.4, "0c0a0a", 0.9)
    t.peindre(m, t.lin(20, 12, 76, 90, [(0, "5a3a26"), (1, "2a160c")]))          # cerclage de cuir
    inn = t.poly([(50 + (x - 50) * 0.78, 50 + (y - 50) * 0.8 - 2) for (x, y) in ecu])
    t.peindre(inn, t.lin(26, 16, 70, 84, [(0, "eef4f6"), (0.45, "98a6b0"), (1, "3a444e")]))
    gauche = t.poly([(50, 14), (50, 82), (24, 54), (23, 20)]) * inn                  # pan d'ombre (biseau)
    t.peindre(gauche, "ffffff", 0.18)
    droite = t.poly([(50, 14), (77, 20), (76, 54), (50, 82)]) * inn
    t.peindre(droite, "1a2028", 0.28)
    t.peindre(t.trait([(50, 15), (50, 80)], 1.6) * inn, "ffffff", 0.55)
    for (x, y) in ((26, 22), (74, 22), (50, 84)):
        r = t.ell(x, y, 2.4)
        t.peindre(r, "e8c878")
        t.reflet(r, 0.6, 0.6, "fff8e0", 0.8)
    t.reflet(m, 1.4, 1.4, "f0d0b0", 0.55)
    t.reflet(inn, 1.0, 1.0, "ffffff", 0.7)
    # impact : étoile + gerbe
    ix, iy = 64, 30
    star = [(ix, iy - 13), (ix + 2.4, iy - 2.4), (ix + 13, iy), (ix + 2.4, iy + 2.4),
            (ix, iy + 11), (ix - 2.4, iy + 2.4), (ix - 10, iy), (ix - 2.4, iy - 2.4)]
    st = t.poly(star)
    t.lueur(st, "ff8a40", 1.3, 4)
    t.peindre(st, "fff2d0")
    for (dx, dy) in ((16, -10), (20, 4), (14, 14), (6, -18)):
        e = t.trait([(ix + dx * 0.45, iy + dy * 0.45), (ix + dx, iy + dy)], 1.4)
        t.lueur(e, "ff7030", 0.9, 1.5)
        t.peindre(e, "ffd8a0")


# ── éléments (rang D) ──
def _langue(t, x, y, ang, L, w, courbe=0.0):
    """Langue de flamme : triangle courbe, base de demi-largeur w en (x, y), pointe à L dans la direction ang."""
    ux, uy = math.cos(ang), math.sin(ang)
    nx, ny = -uy, ux
    tip = (x + ux * L + nx * courbe, y + uy * L + ny * courbe)
    mid = (x + ux * L * 0.5 + nx * courbe * 0.2, y + uy * L * 0.5 + ny * courbe * 0.2)
    g = bezier([(x + nx * w, y + ny * w), (mid[0] + nx * w * 0.9, mid[1] + ny * w * 0.9), tip], 14)
    d = bezier([tip, (mid[0] - nx * w * 0.9, mid[1] - ny * w * 0.9), (x - nx * w, y - ny * w)], 14)
    return t.poly(g + d)


def _flamme(t, cx, cy, R, q, L, ph, k=1.0):
    """Masque de flamme : tête ronde + langues pointues vers q (longueurs qui vivent avec ph)."""
    m = t.ell(cx, cy, R)
    for j, (d, lf, wf) in enumerate(((-0.62, 0.62, 0.42), (-0.22, 1.0, 0.55), (0.2, 0.86, 0.5), (0.6, 0.58, 0.4))):
        lj = R * L * lf * (1 + 0.16 * math.sin(ph + j * 1.9)) * k
        a = q + d + 0.07 * math.sin(ph * 1.3 + j)
        m = np.maximum(m, _langue(t, cx, cy, a, R + lj, R * wf, courbe=R * 0.35 * math.sin(ph + j)))
    return m


def ic_katon(t, f=0, nf=4):
    """Boule de feu qui file vers le haut à droite, langues de flammes ; 4 images de flamboiement."""
    t.fond(sol=False, halo=(58, 40, 26, 0.30))
    ph = f * math.pi / 2
    cx, cy = 62, 36
    q = math.radians(138)                         # la traînée part vers le bas à gauche
    couches = [(22, 1.85, "a81c08"), (18, 1.7, "e84414"), (13.5, 1.5, "ff8a1e"), (9, 1.25, "ffc83c"), (5.4, 1.0, "fff4c8")]
    out = _flamme(t, cx, cy, 22, q, 1.85, ph)
    t.lueur(out, "ff3a08", 0.9, 7)
    t.contour(out, 1.2, "2a0800", 0.75)
    for i, (R, L, col) in enumerate(couches):
        m = _flamme(t, cx + i * 0.9, cy - i * 0.9, R, q, L, ph + i * 0.7)
        t.peindre(m, col)
    t.reflet(out, 1.4, 1.4, "ffe080", 0.55)
    # flammèches détachées
    for i, (d, s_) in enumerate(((60, 3.4), (70, 2.4))):
        a = q + 0.3 * math.sin(ph + i * 2.1) + (0.25 if i else -0.2)
        x, y = cx + d * math.cos(a), cy + d * math.sin(a)
        m = t.poly(goutte(x, y, s_, q, 1.6, 3))
        t.lueur(m, "ff6a1a", 0.8, 2.0)
        t.peindre(m, ("ff9a26", "e84414")[i])
    rng = np.random.default_rng(7 + f)
    for _ in range(4):
        x, y = rng.uniform(15, 90), rng.uniform(12, 90)
        e = t.ell(x, y, 1.1)
        t.lueur(e, "ff7a20", 0.9, 1.6)
        t.peindre(e, "ffd890")


def ic_suiton(t, f=0, nf=4):
    """Vague qui s'enroule (rouleau), écume et gouttes ; la crête ondule sur 4 images."""
    t.fond(sol=True)
    ph = f * math.pi / 2
    w = 1.6 * math.sin(ph)
    # corps de la vague : de la base gauche, monte, s'enroule vers la droite et retombe
    ext = bezier([(4, 92), (8, 52), (30, 18 + w), (62, 14 + w), (86, 24), (92, 46), (76, 58), (62, 50)], 60)
    inn = bezier([(62, 50), (70, 40), (64, 32), (50, 36), (40, 56), (46, 80), (60, 92)], 40)
    P = ext + inn + [(96, 92), (96, 96), (4, 96)]
    P = ext + inn + [(4, 92)]
    m = t.poly(ext + inn + [(4, 96)])
    t.lueur(m, "1a9cff", 0.9, 6)
    t.contour(m, 1.4, "06142a", 0.8)
    t.peindre(m, t.lin(20, 90, 70, 18, [(0, "0a3a8a"), (0.45, "1680d8"), (0.8, "4cc8ff"), (1, "c8f4ff")]))
    t.creux(m, 2.2, 2.2, "06204e", 0.7, 1.4)
    # creux du rouleau, plus sombre
    tube = t.poly(bezier([(60, 49), (67, 41), (62, 34), (52, 37), (46, 52), (52, 66)], 30) + [(58, 60)])
    t.peindre(t.flou(tube, 1.0), "062a66", 0.75)
    # écume de la crête
    cr = t.trait([(x + 0.8, y + 2.0) for (x, y) in ext[14:50]], 3.4)
    t.lueur(cr, "9ae8ff", 0.9, 2.5)
    t.peindre(cr, "f2fcff", 0.95)
    for i, (x, y, r) in enumerate(((88, 12, 2.0), (93, 28, 1.4), (76, 6, 1.3))):
        dy = ((f + i) % 4) * 1.5
        e = t.ell(x, y + dy, r)
        t.lueur(e, "4ad0ff", 0.8, 1.5)
        t.peindre(e, "e8faff")
    # stries d'eau dans le corps
    for (a, b2) in (((18, 80), (30, 50)), ((30, 86), (40, 60))):
        t.peindre(t.trait([a, b2], 1.4) * m, "8ae0ff", 0.45)


def ic_doton(t, f=0, nf=1):
    """Mur de terre : trois dalles de roche jaillissent du sol, éclats et poussière."""
    t.fond(sol=False, halo=(50, 70, 30, 0.22))
    dalles = [
        ([(10, 92), (14, 52), (24, 40), (34, 50), (36, 92)], ("b07a48", "6a4424", "3a2412")),
        ([(62, 92), (64, 44), (76, 32), (88, 46), (90, 92)], ("b07a48", "6a4424", "3a2412")),
        ([(28, 92), (32, 30), (44, 14), (58, 26), (66, 92)], ("ecc080", "9a6630", "4a2e16")),
    ]
    for P, (c1, c2, c3) in dalles:
        m = t.poly(P)
        t.ombre(m, 2.2, 1.0, 2.0, 0.7)
        t.contour(m, 1.2, "140c06", 0.8)
        xs = [p[0] for p in P]; ys = [p[1] for p in P]
        t.peindre(m, t.lin(min(xs), min(ys), max(xs), max(ys), [(0, c1), (0.5, c2), (1, c3)]))
        # facette éclairée (moitié gauche du sommet)
        top = min(P, key=lambda q: q[1])
        fac = [P[1], top, (top[0] - 2, top[1] + 30), (P[0][0] + 2, P[0][1])]
        t.peindre(t.poly(fac) * m, "f0c890", 0.35)
        t.reflet(m, 1.4, 1.2, "ffe0b0", 0.7)
        # fissures
        cx = (min(xs) + max(xs)) / 2
        t.peindre(t.trait([(cx - 2, top[1] + 14), (cx + 3, top[1] + 26), (cx - 1, top[1] + 36)], 1.2) * m, "2a180a", 0.8)
    # sol fracturé et éclats
    sol = t.poly([(0, 88), (100, 88), (100, 100), (0, 100)])
    t.peindre(sol, t.lin(0, 88, 0, 100, [(0, "5a3c22"), (1, "24160c")]))
    t.peindre(t.trait([(0, 88), (100, 88)], 1.2), "c08a50", 0.7)
    for (x, y, r) in ((20, 22, 2.6), (84, 18, 2.2), (70, 12, 1.6), (12, 34, 1.6)):
        e = t.poly(goutte(x, y, r, 0.6, 0.6, 1.5))
        t.peindre(e, "a07040")
        t.reflet(e, 0.6, 0.6, "f0c890", 0.8)
    # poussière
    for (x, y, r) in ((8, 84, 7), (94, 82, 7), (50, 90, 9)):
        t.peindre(t.flou(t.ell(x, y, r, r * 0.6), 2.5), "c8a078", 0.35)


def ic_futon(t, f=0, nf=4):
    """Lame de vent en croissant + tourbillon qui tourne (4 images)."""
    t.fond(stries=5, sol=False, halo=(50, 50, 30, 0.22))
    ph = f * math.pi / 8
    for i in range(3):
        a0 = ph + i * 2 * math.pi / 3
        pts = []
        for k in range(28):
            s = k / 27
            a = a0 + s * 2.6
            r = 10 + s * 30
            pts.append((50 + r * math.cos(a), 52 + r * math.sin(a) * 0.8))
        m = t.trait(pts, 4.2 - i * 0.6)
        t.lueur(m, "4ae890", 0.9, 3)
        t.peindre(m, t.rad(50, 52, 42, [(0, "f4fff8"), (0.6, "a8f4c8"), (1, "3cb878")]), 0.95)
    # croissant (lame de vent) qui traverse
    out = bezier([(14, 70), (40, 92), (80, 74), (94, 30)], 30)
    inn = bezier([(94, 30), (74, 64), (42, 80), (14, 70)], 30)
    m = t.poly(out + inn)
    t.lueur(m, "6affb0", 1.0, 4)
    t.peindre(m, t.lin(14, 70, 94, 30, [(0, "6ad8a0"), (0.6, "e8fff2"), (1, "ffffff")]))
    t.reflet(m, -1.0, 1.0, "ffffff", 0.6)
    # feuille emportée
    lf = rot([(-5, 0), (0, -2.6), (5, 0), (0, 2.6)], ph * 2 + 0.5, tx=26, ty=26)
    e = t.poly(lf)
    t.peindre(e, "8ae070")
    t.reflet(e, 0.5, 0.5, "e8ffd0", 0.8)


def ic_raiton(t, f=0, nf=4):
    """Éclair en zigzag, branches qui crépitent (4 images)."""
    t.fond(sol=True, halo=(50, 48, 32, 0.25))
    rng = np.random.default_rng(11 + f)
    tronc = [(62, 4), (44, 36), (58, 38), (36, 70), (50, 70), (32, 96)]
    jit = [(x + rng.uniform(-1.5, 1.5), y) for (x, y) in tronc]
    m = t.trait(jit, 7.5)
    t.lueur(m, "ffd020", 1.3, 8)
    t.contour(m, 1.2, "1a1400", 0.7)
    t.peindre(m, t.lin(40, 0, 50, 100, [(0, "fff8b0"), (1, "ffd030")]))
    coeur = t.trait(jit, 2.8)
    t.peindre(coeur, "ffffff")
    # branches
    for k in range(3):
        i = rng.integers(1, len(jit) - 1)
        x, y = jit[i]
        s = 1 if rng.random() < 0.5 else -1
        br = [(x, y), (x + s * rng.uniform(8, 14), y + rng.uniform(4, 10)), (x + s * rng.uniform(14, 22), y + rng.uniform(10, 18))]
        b = t.trait(br, 2.0)
        t.lueur(b, "ffe040", 0.9, 2.5)
        t.peindre(b, "fff6c0")
    # étincelles
    for _ in range(6):
        x, y = rng.uniform(12, 88), rng.uniform(10, 90)
        e = t.ell(x, y, 1.0)
        t.lueur(e, "ffe060", 1.0, 1.5)
        t.peindre(e, "ffffe0")


# ═══════════════════════════════════════ sujets 16 px (effets, états) ═══════════════════════════════════════
def _coeur_pts(cx, cy, s):
    P = []
    for i in range(80):
        a = 2 * math.pi * i / 80
        x = 16 * math.sin(a) ** 3
        y = -(13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))
        P.append((cx + x * s, cy + y * s))
    return P


def _coeur(t, cols=("ff8a8a", "e02a3a", "7a0c1a"), lueur="ff3050"):
    m = t.poly(_coeur_pts(50, 50, 2.55))
    t.lueur(m, lueur, 0.7, 6)
    t.contour(m, 3.0, "1a0608", 0.85)
    t.peindre(m, t.lin(30, 22, 66, 88, [(0, cols[0]), (0.45, cols[1]), (1, cols[2])]))
    t.peindre(t.ell(34, 36, 8, 6), "ffffff", 0.75)           # reflet
    t.creux(m, 3, 3, "000000", 0.35, 2)
    return m


def fx_coeur(t, f=0, nf=1):
    t.fond(sol=False, halo=(50, 50, 30, 0.25))
    _coeur(t)


def fx_coeur_pause(t, f=0, nf=1):
    t.fond(sol=False)
    _coeur(t, ("c8c8c8", "8a8a8a", "3a3a3a"), "a0a0a0")


def _bol(t, plein=True, fissure=False, incline=0.0, cols=("c8402a", "7a1a10", "3a0a06")):
    cx, cy = 50, 62
    bol = rot([(-40, -10), (40, -10), (34, 8), (20, 22), (-20, 22), (-34, 8)], incline, tx=cx, ty=cy)
    m = t.poly(bol)
    t.contour(m, 3.0, "120604", 0.85)
    t.peindre(m, t.lin(20, 50, 80, 90, [(0, cols[0]), (0.6, cols[1]), (1, cols[2])]))
    bord = t.poly(rot([(-41, -13), (41, -13), (40, -7), (-40, -7)], incline, tx=cx, ty=cy))
    t.peindre(bord, "f0d8b0" if plein else "c8b090")
    if plein:
        dedans = t.poly(rot([(-36, -13), (36, -13), (30, -20), (-30, -20)], incline, tx=cx, ty=cy))
        t.peindre(dedans, "f2c050")                     # nouilles / bouillon
        t.peindre(t.ell(cx - 12, cy - 16, 6, 3), "ffeccc", 0.9)
        t.peindre(t.ell(cx + 14, cy - 16, 5, 3), "8a2a1a", 0.9)
    t.peindre(t.poly(rot([(-10, 22), (10, 22), (12, 28), (-12, 28)], incline, tx=cx, ty=cy)), "4a140c")
    t.reflet(m, 2.5, 2.5, "ffb090", 0.5)
    if fissure:
        t.peindre(t.trait([(cx + 4, cy - 8), (cx - 2, cy + 4), (cx + 6, cy + 10), (cx + 1, cy + 20)], 3.0), "140604", 0.95)
    return m


def _vapeur(t, xs=(36, 52, 66), y0=40, h=30, ph=0.0, col="f0f0f0", a=0.8, w=4.5):
    for i, x in enumerate(xs):
        pts = [(x + 5 * math.sin(k / 8 * 2 * math.pi + ph + i), y0 - k / 8 * h) for k in range(9)]
        t.peindre(t.trait(pts, w), col, a)


def fx_bol(t, f=0, nf=1):
    t.fond(sol=False, halo=(50, 60, 30, 0.20))
    _bol(t)


def fx_vapeur(t, f=0, nf=1):
    t.fond(sol=False, halo=(50, 60, 30, 0.20))
    _bol(t)
    _vapeur(t, y0=38, h=30)


def fx_satiete_prolongee(t, f=0, nf=1):
    t.fond(sol=False, halo=(50, 60, 30, 0.20))
    _bol(t)
    for dy in (0, 7):                                    # baguettes posées en travers
        b = t.trait([(22, 22 + dy), (86, 44 + dy)], 4.5)
        t.contour(b, 2, "120604", 0.6)
        t.peindre(b, t.lin(22, 20, 86, 50, [(0, "f0d8a0"), (1, "a07840")]))


def fx_affame(t, f=0, nf=1):
    t.fond(sol=False)
    _bol(t, plein=False, incline=-0.25, cols=("e09a3a", "9a5a14", "4a2806"))


def fx_faim(t, f=0, nf=1):
    t.fond(sol=False)
    _bol(t, plein=False, fissure=True, incline=-0.25, cols=("c8402a", "7a1a10", "3a0a06"))


def fx_souffle(t, f=0, nf=1):
    """Endurance : trois filets de vent qui s'enroulent."""
    t.fond(sol=False, halo=(50, 50, 30, 0.2))
    for (y, L, w, r) in ((28, 70, 7, 9), (52, 84, 8, 11), (76, 58, 6, 8)):
        x0 = 6
        pts = [(x0 + k / 20 * (L - 10), y) for k in range(21)]
        cx, cy = x0 + L - 10, y - r
        pts += [(cx + r * math.sin(a), cy + r * math.cos(a)) for a in np.linspace(0, math.pi * 1.4, 14)]
        m = t.trait(pts, w)
        t.contour(m, 2.5, "0e1a14", 0.7)
        t.peindre(m, t.lin(0, 0, 100, 0, [(0, "a8e8c8"), (1, "ffffff")]))


def fx_meditation(t, f=0, nf=1):
    """Thé vert : un bol à thé, un filet de chakra bleu au-dessus."""
    t.fond(sol=False, halo=(50, 55, 30, 0.2))
    tasse = [(20, 44), (80, 44), (74, 80), (62, 90), (38, 90), (26, 80)]
    m = t.poly(tasse)
    t.contour(m, 3, "0a120a", 0.85)
    t.peindre(m, t.lin(20, 40, 80, 92, [(0, "a8c890"), (0.5, "5a8a4a"), (1, "22401c")]))
    t.peindre(t.poly([(21, 44), (79, 44), (78, 50), (22, 50)]), "e8f0d0")
    t.peindre(t.poly([(26, 44), (74, 44), (70, 40), (30, 40)]), "8ac050")
    t.reflet(m, 2.5, 2.5, "e8ffd8", 0.5)
    w = t.trait([(50 + 7 * math.sin(k / 10 * 5), 38 - k * 3.2) for k in range(11)], 6)
    t.lueur(w, "2a8cff", 1.0, 5)
    t.peindre(w, t.lin(0, 38, 0, 4, [(0, "7ad8ff"), (1, "e8faff")]))


def fx_pilule(t, f=0, nf=1):
    """Pilule du soldat : bille brun-vert, reflet."""
    t.fond(sol=False, halo=(50, 50, 30, 0.25))
    m = t.ell(50, 52, 30)
    t.ombre(m, 3, 4, 4, 0.6)
    t.contour(m, 3, "0e0a04", 0.85)
    t.peindre(m, t.rad(38, 38, 52, [(0, "d8c070"), (0.45, "8a7a2a"), (1, "2a2208")]))
    t.peindre(t.ell(38, 38, 9, 6), "ffffff", 0.8)
    t.peindre(t.ell(62, 66, 4, 3), "f0e0a0", 0.4)


def fx_fatigue(t, f=0, nf=1):
    """Contrecoup : deux chevrons qui tombent."""
    t.fond(sol=False)
    for i, y in enumerate((24, 52)):
        m = t.trait([(18, y), (50, y + 22), (82, y)], 11)
        t.contour(m, 3, "1a0604", 0.8)
        t.peindre(m, t.lin(0, y - 6, 0, y + 30, [(0, "ffd0c0"), (1, ("e05a3a", "a82810")[i])]))


def fx_poison(t, f=0, nf=1):
    t.fond(sol=False)
    arc = [(50 + 26 * math.cos(a), 66 + 26 * math.sin(a)) for a in np.linspace(0, math.pi, 30)]
    m = t.poly(bezier([(50, 8), (56, 28), (76, 46), (76, 66)], 20) + arc + bezier([(24, 66), (24, 46), (44, 28), (50, 8)], 20))
    t.lueur(m, "8a40ff", 0.6, 5)
    t.contour(m, 3, "10061a", 0.85)
    t.peindre(m, t.lin(30, 20, 70, 90, [(0, "d8a8ff"), (0.4, "8a3ad8"), (1, "3a0a6a")]))
    t.peindre(t.ell(40, 54, 6, 9), "ffffff", 0.6)
    for (x, y, r) in ((60, 70, 5), (48, 78, 3.5)):
        t.peindre(t.ell(x, y, r), "b8f060", 0.85)


def _sac(t, cols=("5a7ad8", "2a3e8a", "121a40")):
    """Baluchon noué (furoshiki) : panse large, col serré, deux oreilles."""
    corps = t.poly(bezier([(30, 44), (4, 62), (10, 94), (50, 96), (90, 94), (96, 62), (70, 44)], 40))
    col = t.poly([(34, 46), (66, 46), (60, 34), (40, 34)])
    oreilles = np.maximum(t.poly(goutte(40, 28, 7, math.radians(-125), 0.9, 2)),
                          t.poly(goutte(60, 28, 7, math.radians(-55), 0.9, 2)))
    m = np.maximum(np.maximum(corps, col), oreilles)
    t.contour(m, 3, "0a0a14", 0.9)
    t.peindre(m, t.lin(20, 20, 80, 96, [(0, cols[0]), (0.55, cols[1]), (1, cols[2])]))
    # motif clair du tissu + cordon
    for (x, y) in ((30, 70), (52, 62), (70, 78), (44, 84)):
        t.peindre(t.ell(x, y, 3.2), "dfe8ff", 0.55)
    lien = t.trait([(36, 42), (64, 42)], 6)
    t.peindre(lien, "e8c070")
    t.reflet(m, 2.5, 2.5, "c8d8ff", 0.55)
    return m


def fx_charge(t, f=0, nf=1):
    t.fond(sol=False)
    _sac(t)


def fx_surcharge(t, f=0, nf=1):
    t.fond(sol=False)
    _sac(t)
    fl = t.poly([(76, 8), (90, 8), (90, 30), (98, 30), (83, 50), (68, 30), (76, 30)])
    t.contour(fl, 3, "1a0604", 0.95)
    t.peindre(fl, "ffe4d8")


def fx_immobile(t, f=0, nf=1):
    t.fond(sol=False)
    _sac(t)
    b = t.trait([(10, 90), (90, 10)], 9)
    t.contour(b, 2.5, "1a0604", 0.85)
    t.peindre(b, "ffe0d0")


def fx_sprint(t, f=0, nf=1):
    """Sprint : deux chevrons vers l'avant, traits de vitesse."""
    t.fond(sol=False, halo=(56, 50, 30, 0.2))
    for y in (24, 76):
        t.peindre(t.trait([(4, y), (30, y)], 4), "ffe8b0", 0.6)
    for i, x in enumerate((22, 50)):
        m = t.trait([(x, 18), (x + 24, 50), (x, 82)], 12)
        t.contour(m, 3, "140c04", 0.85)
        t.peindre(m, t.lin(x, 0, x + 24, 0, [(0, ("e8b050", "fff0c0")[i]), (1, "ffffff")]))


def fx_essouffle(t, f=0, nf=1):
    t.fond(sol=False)
    for (x, y, r) in ((34, 56, 16), (58, 46, 20), (76, 62, 13)):
        m = t.ell(x, y, r)
        t.contour(m, 3, "1a0604", 0.7)
        t.peindre(m, t.rad(x - r * 0.4, y - r * 0.4, r * 1.6, [(0, "ffffff"), (1, "c8b8b0")]))


def fx_sac(t, f=0, nf=1):
    t.fond(sol=False, halo=(50, 55, 30, 0.2))
    _sac(t)


def fx_interdit(t, f=0, nf=1):
    t.fond(sol=False)
    o = t.ell(50, 50, 36)
    i = t.ell(50, 50, 24)
    b = t.poly(rot([(-30, -6), (30, -6), (30, 6), (-30, 6)], math.radians(-45), tx=50, ty=50))
    m = np.clip(np.maximum(o - i, b * i), 0, 1)
    t.contour(m, 3, "1a0604", 0.8)
    t.peindre(m, t.lin(20, 20, 80, 80, [(0, "ffb0a0"), (1, "e0402a")]))


def fx_ryo(t, f=0, nf=1):
    """Pièce de ryo : disque d'or percé d'un trou carré."""
    t.fond(sol=False, halo=(50, 50, 30, 0.25))
    o = t.ell(50, 50, 36)
    trou = t.poly([(42, 42), (58, 42), (58, 58), (42, 58)])
    m = np.clip(o - trou, 0, 1)
    t.contour(m, 3, "140c02", 0.85)
    t.peindre(m, t.rad(36, 34, 70, [(0, "fff0a0"), (0.5, "d8a830"), (1, "6a4a08")]))
    t.peindre(np.clip(t.ell(50, 50, 28) - t.ell(50, 50, 25), 0, 1) * m, "8a6410", 0.6)
    t.reflet(m, 2.5, 2.5, "ffffff", 0.5)


# ═══════════════════════════════════════ catalogue ═══════════════════════════════════════
# (fichier, famille du cadre, fonction, images, ce que c'est)
RECHARGES = [
    ("combat",        "rouge",  ic_combat,        1, "En combat : deux kunai croisés, éclat du choc"),
    ("course_chakra", "bleu",   ic_course_chakra, 4, "Course de chakra (L) : course ninja"),
    ("esquive",       "rouge",  ic_esquive,       1, "Esquive : pas de côté, images rémanentes"),
    ("garde",         "rouge",  ic_garde,         1, "Garde : écu d’acier cerclé, coup paré"),
    ("katon",         "katon",  ic_katon,         4, "Katon rang D : boule de feu"),
    ("suiton",        "suiton", ic_suiton,        4, "Suiton rang D : vague qui s'enroule"),
    ("doton",         "doton",  ic_doton,         1, "Doton rang D : dalles de roche"),
    ("futon",         "futon",  ic_futon,         4, "Fūton rang D : lame de vent, tourbillon"),
    ("raiton",        "raiton", ic_raiton,        4, "Raiton rang D : éclair"),
]

EFFETS = [
    ("coeur",             "or",        fx_coeur,             "Régénération (bonus)"),
    ("coeur_pause",       "gris",      fx_coeur_pause,       "Régénération en pause (coup reçu)"),
    ("satiete_prolongee", "or",        fx_satiete_prolongee, "Satiété prolongée (ramen miso, bento)"),
    ("souffle",           "or",        fx_souffle,           "Endurance + (dango)"),
    ("meditation",        "or",        fx_meditation,        "Méditation × (thé vert)"),
    ("pilule",            "or",        fx_pilule,            "Pilule du soldat"),
    ("fatigue",           "vermillon", fx_fatigue,           "Fatigue (contrecoup de la pilule)"),
    ("poison",            "vermillon", fx_poison,            "Poison"),
    ("affame",            "ambre",     fx_affame,            "Affamé (< 20 %)"),
    ("faim",              "vermillon", fx_faim,              "FAIM (0 %)"),
    ("bol",               "or",        fx_bol,               "Repas / plat (notification, filet)"),
    ("vapeur",            "or",        fx_vapeur,            "Plat chaud / préparation"),
    ("charge",            "ambre",     fx_charge,            "Chargé (100-115 %)"),
    ("surcharge",         "vermillon", fx_surcharge,         "Surchargé (115-130 %)"),
    ("immobile",          "vermillon", fx_immobile,          "Immobile (> 130 %)"),
    ("sprint",            "or",        fx_sprint,            "Sprint (remplace le kanji de la barre)"),
    ("essouffle",         "vermillon", fx_essouffle,         "Essoufflé / récupération"),
    ("sac",               "or",        fx_sac,               "Notif. sacoche (place, sac équipé)"),
    ("interdit",          "vermillon", fx_interdit,          "Notif. « impossible » (KO, verrou)"),
    ("ryo",               "or",        fx_ryo,               "Notif. ryo (don, paiement)"),
]


def rendre_recharge(nom, fam, fn, nf):
    """Bande verticale 32 × 32·nf (images empilées, comme dash.png) + liste des images."""
    imgs = []
    for f in range(nf):
        t = Toile(28, fam, seed=3)
        fn(t, f, nf)
        imgs.append(assembler(t, 32, 0.55))
    bande = Image.new("RGBA", (32, 32 * nf), (0, 0, 0, 0))
    for i, im in enumerate(imgs):
        bande.alpha_composite(im, (0, 32 * i))
    return bande, imgs


def rendre_effet(nom, fam, fn):
    t = Toile(14, fam, seed=3)
    fn(t)
    return assembler(t, 16, 0.6)


def gif(imgs, chemin, echelle=4, ms=100, fond=(24, 22, 26)):
    frames = []
    for im in imgs:
        b = Image.new("RGBA", im.size, fond + (255,))
        b.alpha_composite(im)
        frames.append(b.convert("RGB").resize((im.width * echelle, im.height * echelle), Image.NEAREST))
    frames[0].save(chemin, save_all=True, append_images=frames[1:], duration=ms, loop=0)


def frames_utilisateur(nom):
    im = Image.open(os.path.join(ABILITY, nom + ".png")).convert("RGBA")
    return [im.crop((0, 32 * i, 32, 32 * i + 32)) for i in range(im.height // 32)]


# ═══════════════════════════════════════ planche de validation ═══════════════════════════════════════
FONTS = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "font")
BG = (22, 21, 26, 255)
CARTE = (34, 32, 38, 255)
CREME = (236, 226, 204, 255)
GRIS = (150, 144, 136, 255)
OR = (214, 178, 92, 255)


def police(t, gras=True):
    return ImageFont.truetype(os.path.join(FONTS, "inter-bold.ttf" if gras else "inter-medium.ttf"), t)


def en_jeu(im32, cote=22):
    """Ce que le GPU affiche : réduction au plus proche (CooldownHud dessine 18 unités × échelle HUD)."""
    return im32.resize((cote, cote), Image.NEAREST)


def carte32(nom, desc, imgs, ref=False):
    W = 300
    anim = len(imgs) > 1
    H = 256 + 14 + 128 + 56 + (80 if anim else 0)
    c = Image.new("RGBA", (W, H), (46, 36, 30, 255) if ref else CARTE)
    d = ImageDraw.Draw(c)
    c.alpha_composite(imgs[0].resize((256, 256), Image.NEAREST), (22, 12))
    y = 12 + 256 + 12
    c.alpha_composite(imgs[0].resize((128, 128), Image.NEAREST), (22, y))
    c.alpha_composite(imgs[0], (166, y))
    d.text((166, y + 36), "×1", font=police(11, False), fill=GRIS)
    c.alpha_composite(en_jeu(imgs[0]), (210, y + 5))
    d.text((206, y + 36), "en jeu", font=police(11, False), fill=GRIS)
    d.text((166, y + 60), "×4 / ×1 / HUD", font=police(11, False), fill=GRIS)
    y += 128 + 8
    if anim:
        for i, im in enumerate(imgs):
            c.alpha_composite(im.resize((64, 64), Image.NEAREST), (22 + i * 66, y))
        y += 72
    d.text((22, y), nom + ".png" + ("  · réf. utilisateur" if ref else ""), font=police(15), fill=OR if ref else CREME)
    d.text((22, y + 20), desc, font=police(11, False), fill=GRIS)
    return c


def carte16(nom, desc, im, fam):
    W, H = 176, 128 + 64 + 60
    c = Image.new("RGBA", (W, H), CARTE)
    d = ImageDraw.Draw(c)
    c.alpha_composite(im.resize((128, 128), Image.NEAREST), (24, 10))
    c.alpha_composite(im.resize((64, 64), Image.NEAREST), (24, 146))
    c.alpha_composite(im, (100, 170))
    d.text((124, 172), "×1", font=police(10, False), fill=GRIS)
    d.text((10, 216), nom, font=police(12), fill=CREME)
    d.text((10, 232), desc[:30], font=police(9, False), fill=GRIS)
    return c


def creneau():
    return Image.open(os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures",
                                   "gui", "combat", "creneau.png")).convert("RGBA")


def rangee_recharges(icones, k=1.2, fracs=None):
    """Simule CooldownHud : créneau 26 + voile + icône 18 (au plus proche) + secondes, à l'échelle k px/unité."""
    cr = creneau()
    n = len(icones)
    W = int(round((n * 22 + (n - 1) * 4 + 2) * k)) + 2
    H = int(round(34 * k)) + 2
    row = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(row)
    f = police(max(7, int(round(7 * k))))
    for i, (im, prete, secs) in enumerate(icones):
        x = 1 + i * 26
        X = lambda u: int(round(u * k))
        cc = cr.crop((0 if prete else 26, 0, (0 if prete else 26) + 26, 26)).resize((X(26), X(26)), Image.NEAREST)
        row.alpha_composite(cc, (X(x - 1) + 1, 1))
        row.alpha_composite(im.resize((X(18), X(18)), Image.NEAREST), (X(x + 2) + 1, X(3) + 1))
        if not prete:
            # voile radial 0x55000000 sur l'arc restant (ici ~60 %)
            ov = Image.new("RGBA", (X(22), X(22)), (0, 0, 0, 0))
            ImageDraw.Draw(ov).pieslice([0, 0, X(22) - 1, X(22) - 1], -90 + 360 * 0.4, 270, fill=(0, 0, 0, 0x55))
            row.alpha_composite(ov, (X(x) + 1, X(1) + 1))
            s = "%.1f" % secs
            tw = d.textlength(s, font=f)
            cx, cy = X(x + 11) + 1, X(1 + 22 + 3) + 1
            for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                d.text((cx - tw / 2 + ox, cy + oy), s, font=f, fill=(20, 14, 10, 255))
            d.text((cx - tw / 2, cy), s, font=f, fill=CREME)
    return row


def effacer(im, x0, y0, x1, y1):
    """Efface une zone de la capture par interpolation horizontale entre ses deux bords (fond en dégradé)."""
    a = np.asarray(im).astype(np.float32).copy()
    for y in range(y0, y1):
        g, d = a[y, x0 - 1], a[y, x1]
        for x in range(x0, x1):
            k = (x - x0 + 1) / (x1 - x0 + 1)
            a[y, x] = g * (1 - k) + d * k
    im.paste(Image.fromarray(a.astype(np.uint8), "RGBA"), (0, 0))


def situ_recharges(rec, util):
    shot = os.path.join(ICI, "..", "run", "screenshots", "refonte_apres", "audit_hud_cooldowns__s2_1920x1080.png")
    base = Image.open(shot).convert("RGBA").crop((0, 0, 900, 330))
    # masque les deux anciennes icônes de la capture (bande prise juste au-dessus)
    effacer(base, 60, 196, 170, 262)
    ic = [(util["dash"][0], False, 4.2), (util["double_jump"][0], False, 7.9), (util["chakra_jump"][0], False, 21.3)]
    ic += [(rec[n][1][0], (n in ("combat", "course_chakra")), 3.4 + i * 1.7) for i, n in enumerate(rec)]
    row = rangee_recharges(ic, 1.2)
    base.alpha_composite(row, (70, 196))
    zoom = row.resize((row.width * 3, row.height * 3), Image.NEAREST)
    return base, zoom


EFFETS_SITU = [("coeur", "×2 · 4:12", 0.84), ("satiete_prolongee", "−50 % · 17:40", 0.88), ("pilule", "2:45", 0.92),
               ("fatigue", "4:52", 0.95), ("coeur_pause", "24 s", 0.6), ("affame", "Affamé", None)]


def situ_effets(eff):
    shot = os.path.join(ICI, "..", "run", "screenshots", "refonte_apres", "audit_hud_cooldowns__s2_1920x1080.png")
    base = Image.open(shot).convert("RGBA").crop((0, 0, 900, 330))
    effacer(base, 60, 196, 170, 262)
    d = ImageDraw.Draw(base)
    f = police(11)
    x, y = 122, 124
    for nom, txt, reste in EFFETS_SITU:
        im = eff[nom]
        base.alpha_composite(im, (x, y))
        if reste is not None:
            d.line([(x, y + 18), (x + int(15 * reste), y + 18)], fill=(214, 178, 92, 255) if nom != "fatigue" else (224, 86, 56, 255))
        col = CREME if nom not in ("affame",) else (232, 160, 60, 255)
        d.text((x + 20, y + 1), txt, font=f, fill=(10, 8, 6, 255))
        d.text((x + 19, y), txt, font=f, fill=col)
        x += 20 + int(d.textlength(txt, font=f)) + 12
    zoom = base.crop((100, 0, 900, 160))
    zoom = zoom.resize((zoom.width * 2, zoom.height * 2), Image.NEAREST)
    return base, zoom


def planche(rec, eff, util):
    os.makedirs(MAQ, exist_ok=True)
    cartes_ref = [carte32(n, dsc, util[n], ref=True) for n, dsc in
                  (("dash", "Dash (V) — 4 images"), ("double_jump", "Double saut"), ("chakra_jump", "Saut de chakra"))]
    cartes = [carte32(n, rec[n][2], rec[n][1]) for n in rec]
    cartes16 = [carte16(n, eff[n][1], eff[n][0], eff[n][2]) for n in eff]
    s_rec, z_rec = situ_recharges(rec, util)
    s_eff, z_eff = situ_effets({k: v[0] for k, v in eff.items()})

    M = 40
    colW = 300
    gx = M + colW + 50
    cols = 4
    hcar = max(c.height for c in cartes)
    rows = (len(cartes) + cols - 1) // cols
    W = gx + cols * (colW + 20) + M - 20
    y_eff = M + 70 + max(rows * (hcar + 20), sum(c.height + 20 for c in cartes_ref)) + 40
    per = (W - 2 * M + 16) // (176 + 16)
    rows16 = (len(cartes16) + per - 1) // per
    y_situ = y_eff + 60 + rows16 * (252 + 16) + 30
    H = y_situ + 60 + max(s_rec.height, s_eff.height) + 30 + z_rec.height + 30 + z_eff.height + M
    P = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(P)
    d.text((M, M), "Icônes peintes — planche de validation (rien n'est intégré au jeu)", font=police(30), fill=CREME)
    d.text((M, M + 40), "À gauche, les trois icônes dessinées par l'utilisateur (référence). Chaque icône : ×8, ×4, ×1 et "
           "« en jeu » (réduction au plus proche à 22 px, échelle HUD 0,6 en 1080p). Les animées : 4 images empilées.",
           font=police(14, False), fill=GRIS)
    y = M + 80
    d.text((M, y - 4), "RÉFÉRENCE", font=police(13), fill=OR)
    yy = y + 18
    for c in cartes_ref:
        P.alpha_composite(c, (M, yy))
        yy += c.height + 20
    d.text((gx, y - 4), "NOUVELLES ICÔNES DE RECHARGE (32 px)", font=police(13), fill=OR)
    for i, c in enumerate(cartes):
        P.alpha_composite(c, (gx + (i % cols) * (colW + 20), y + 18 + (i // cols) * (hcar + 20)))
    d.text((M, y_eff), "ICÔNES D'EFFETS ET D'ÉTATS (16 px, cadre 1 px) — or = bonus, vermillon = malus, ambre = alerte, "
           "gris = pause", font=police(13), fill=OR)
    for i, c in enumerate(cartes16):
        P.alpha_composite(c, (M + (i % per) * (176 + 16), y_eff + 26 + (i // per) * (252 + 16)))
    d.text((M, y_situ), "MISE EN SITUATION (capture réelle du client de dev, icônes composées par-dessus)", font=police(13), fill=OR)
    d.text((M, y_situ + 20), "Gauche : HUD des recharges (3 réf. en recharge + 9 nouvelles ; combat et course de chakra = "
           "indicateurs d'état, prêts). Droite : effets sous les vitals.", font=police(12, False), fill=GRIS)
    P.alpha_composite(s_rec, (M, y_situ + 50))
    P.alpha_composite(s_eff, (M + s_rec.width + 30, y_situ + 50))
    yz = y_situ + 50 + s_rec.height + 30
    d.text((M, yz - 18), "Recharges ×3 (au plus proche)", font=police(12), fill=GRIS)
    P.alpha_composite(z_rec, (M, yz))
    yz += z_rec.height + 30
    d.text((M, yz - 18), "Effets sous les vitals ×2", font=police(12), fill=GRIS)
    P.alpha_composite(z_eff, (M, yz))
    P.convert("RGB").save(os.path.join(MAQ, "planche_icones.png"))
    return P


def main():
    os.makedirs(OUT_PNG, exist_ok=True)
    os.makedirs(OUT_GIF, exist_ok=True)
    os.makedirs(os.path.join(OUT_PNG, "effet"), exist_ok=True)
    rec = {}
    for nom, fam, fn, nf, desc in RECHARGES:
        bande, imgs = rendre_recharge(nom, fam, fn, nf)
        bande.save(os.path.join(OUT_PNG, nom + ".png"))
        if nf > 1:
            gif(imgs, os.path.join(OUT_GIF, nom + ".gif"), 4, 100)
        rec[nom] = (bande, imgs, desc)
    eff = {}
    for nom, fam, fn, desc in EFFETS:
        im = rendre_effet(nom, fam, fn)
        im.save(os.path.join(OUT_PNG, "effet", nom + ".png"))
        eff[nom] = (im, desc, fam)
    util = {n: frames_utilisateur(n) for n in ("dash", "double_jump", "chakra_jump")}
    gif(util["dash"], os.path.join(OUT_GIF, "dash_reference.gif"), 4, 100)
    planche(rec, eff, util)
    # fichiers séparés de mise en situation (pour les regarder de près)
    s, z = situ_recharges(rec, util)
    z.convert("RGB").save(os.path.join(MAQ, "situ_recharges_x3.png"))
    s, z = situ_effets({k: v[0] for k, v in eff.items()})
    z.convert("RGB").save(os.path.join(MAQ, "situ_effets_x2.png"))
    print("ok :", len(rec), "recharges,", len(eff), "effets ->", MAQ)


if __name__ == "__main__":
    main()
