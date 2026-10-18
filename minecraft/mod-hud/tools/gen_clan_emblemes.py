# -*- coding: utf-8 -*-
"""Emblèmes de clan — les VRAIS blasons, au langage des emblèmes de village (gui/esc/village_<id>.png).

    python tools/gen_clan_emblemes.py                      -> les 22 emblèmes + la planche de contrôle
    python tools/gen_clan_emblemes.py uchiha               -> un seul (planche quand même)
    python tools/gen_clan_emblemes.py --planche <png> --refs <dossier des SVG de référence des villages>

Sortie : src/main/resources/assets/reborn/textures/gui/clans/clan_<id>.png — 48x48, blanc sur transparent,
teinté par le code comme les villages (blit avec tint ARGB). <id> = CharacterRules.slug(nom du clan).

Méthode (comme les villages, tools/ui-art/gen_esc_art.py::raster_svg) : chaque blason part du SVG de
Narutopedia (tools/clans_canon/<id>.svg, sources ci-dessous), rendu en x8 avec PyMuPDF. L'« encre » du
blason (le noir, et le rouge quand il est l'aplat du blason, ex. haut de l'éventail Uchiha) devient le
blanc ; le reste (fond, aplats décoratifs) devient transparent. Le dessin est cadré dans 46 px, son trait
ramené à l'épaisseur des villages (≈ 2,8 px) par dilatation/érosion d'un disque, puis réduit en LANCZOS.
Les aplats pleins du canon restent pleins.

Clans sans blason canon (vérifié sur Narutopedia, oct. 2026 puis 18/10) : emblème DESSINÉ (section « dessins »),
sobre, sans aucune écriture (règle du 14/10 : plus de kanji décoratif) — Hōki (feuilles et goutte : clan de
médecins), Hoshigaki (aileron de requin), Terumi (goutte qui bout : eau, vapeur, lave), Ōnoki (montagne), Sabaku
(gourde de Gaara), Kamizuru (abeille : le « Kamizuru_Symbol.svg » de Narutopedia est un placeholder de fan, le nom
上水流 en écriture sigillaire). Jusqu'au 18/10 ces clans avaient un kanji cerclé (医 鮫 水 土 風) ou ce sceau.
"""
from __future__ import annotations

import math
import os
import sys
import tempfile
from pathlib import Path

import numpy as np
import pymupdf
from PIL import Image, ImageDraw, ImageFont

ICI = Path(__file__).resolve().parent
RES = ICI.parent / "src/main/resources/assets/reborn/textures/gui"
SORTIE = RES / "clans"
CANON = ICI / "clans_canon"
LOGOS_VILLAGES = ICI.parents[2] / "tools/ui-art/logos"     # SVG Commons des villages (gen_esc_art.py)

T = 48                 # taille finale
K = 8                  # suréchantillonnage
EP = 2.8               # épaisseur du trait (px finaux) — mesurée sur village_ame / suna / iwa
CADRE = 46             # plus grande dimension du dessin (px finaux) — les villages vont de 44 à 48
NARUTOPEDIA = "https://naruto.fandom.com/wiki/File:"

# id -> recette. svg : fichier de clans_canon ; encre : "encre" (tout ce qui n'est pas blanc) ou "noir"
# (le noir seul : l'aplat rouge de l'Uzumaki est un fond) ; ep : épaisseur visée (None = celle du canon,
# mise à l'échelle) ; fins : (seuil, ajout) épaissit les traits plus fins que 2 x seuil ; gras : dilatation finale (px) ;
# fn : emblème dessiné (DESSINS, section « dessins ») pour les clans sans blason canon — jamais d'écriture.
CLANS = {
    "senju":     dict(svg="senju.svg", src=NARUTOPEDIA + "Senju_Symbol.svg", ep=2.2, statut="canon"),
    "uchiha":    dict(svg="uchiha.svg", src=NARUTOPEDIA + "Uchiha_Symbol.svg", statut="canon"),
    "hyuga":     dict(svg="hyuga.svg", src=NARUTOPEDIA + "Hy%C5%ABga_Symbol.svg",
                      statut="canon (guide Michi)"),
    "nara":      dict(svg="nara.svg", src=NARUTOPEDIA + "Nara_Symbol.svg", statut="canon (chapitre 533)"),
    "sarutobi":  dict(svg="sarutobi.svg", src=NARUTOPEDIA + "Sarutobi_Symbol.svg", statut="canon (guide Michi)"),
    "uzumaki":   dict(svg="uzumaki.svg", src=NARUTOPEDIA + "Uzumaki_Symbol.svg", encre="noir", statut="canon"),
    "aburame":   dict(svg="aburame.svg", src=NARUTOPEDIA + "Aburame_Symbol.svg", statut="canon (guide Michi)"),
    "akimichi":  dict(svg="akimichi.svg", src=NARUTOPEDIA + "Akimichi_Symbol.svg",
                      statut="canon (chapitre 533) ; variante : 食 cerclé de Chōji (partie I)"),
    "hatake":    dict(svg="hatake.svg", src=NARUTOPEDIA + "Hatake_Symbol.svg", ep=3.4, statut="canon (guide Michi)"),
    "yamanaka":  dict(svg="yamanaka.svg", src=NARUTOPEDIA + "Yamanaka_Symbol.svg", statut="canon (chapitre 533)"),
    "kurama":    dict(svg="kurama.svg", src=NARUTOPEDIA + "Kurama_Symbol.svg", ep=2.0, statut="canon (anime)"),
    "sabaku":    dict(fn="sabaku", src="aucun blason (Clan du Kazekage, manga/roman)",
                      statut="CRÉATION (aucun blason canon) : la gourde de sable de Gaara, cordon et sangle"),
    "hoki":      dict(fn="hoki", src="aucun blason (Famille Hōki, roman Gaara Hiden)",
                      statut="CRÉATION (aucun blason canon) : deux feuilles et une goutte, clan de médecins"),
    "hozuki":    dict(svg="hozuki.svg", src=NARUTOPEDIA + "H%C5%8Dzuki_Symbol.svg", ep=2.3,
                      statut="symbole Narutopedia (lanterne hōzuki), pas de blason canon"),
    "hoshigaki": dict(fn="hoshigaki", src="aucun blason (Clan Hoshigaki : symbole de Kiri seulement)",
                      statut="CRÉATION (aucun blason canon) : aileron de requin sur deux vagues (Kisame)"),
    "kaguya":    dict(svg="kaguya.svg", src=NARUTOPEDIA + "Kaguya_Symbol.svg", ep=None, gras=2.0,
                      statut="canon : marques frontales du clan (Kimimaro)"),
    "yuki":      dict(svg="yuki.svg", src=NARUTOPEDIA + "Yuki_Symbol.svg",
                      statut="symbole Narutopedia (flocon), pas de blason canon"),
    "terumi":    dict(fn="terumi", src="aucun blason (pas de page de clan)",
                      statut="CRÉATION (aucun blason canon) : goutte qui bout, eau, vapeur et lave de Mei"),
    "yotsuki":   dict(svg="yotsuki.svg", src=NARUTOPEDIA + "Yotsuki_Symbol.svg", ep=None, fins=(0.8, 0.7),
                      statut="symbole Narutopedia (lune et nuages = 夜月), pas de blason canon"),
    "kamizuru":  dict(fn="kamizuru", src="aucun blason (Kamizuru_Symbol.svg = placeholder de fan, nom en sigillaire)",
                      statut="CRÉATION (aucun blason canon) : l'abeille du clan des abeilles d'Iwa"),
    "onoki":     dict(fn="onoki", src="aucun blason (lignée du Tsuchikage)",
                      statut="CRÉATION (aucun blason canon) : la montagne enneigée, lignée de la Terre"),
    "autre":     dict(fn="autre", src="—", statut="générique (aucun clan)"),
}
VILLAGES = ["konoha", "suna", "kiri", "kumo", "iwa", "ame"]


# ───────────────────────────── morphologie (numpy seul) ─────────────────────────────
def _disque(r):
    rr = int(math.ceil(r))
    yy, xx = np.mgrid[-rr:rr + 1, -rr:rr + 1]
    return (xx ** 2 + yy ** 2) <= r * r


def _dilate(m, noyau):
    """Dilatation binaire par FFT."""
    h, w = m.shape
    kh, kw = noyau.shape
    H, W = h + kh, w + kw
    f = np.fft.rfft2(m.astype(np.float32), (H, W)) * np.fft.rfft2(noyau.astype(np.float32), (H, W))
    c = np.fft.irfft2(f, (H, W))[kh // 2:kh // 2 + h, kw // 2:kw // 2 + w]
    return c > 0.5


def _erode(m, noyau):
    return ~_dilate(~m, noyau)


def _bord(m):
    e = m.copy()
    e[1:, :] &= m[:-1, :]; e[:-1, :] &= m[1:, :]
    e[:, 1:] &= m[:, :-1]; e[:, :-1] &= m[:, 1:]
    return m ^ e


def epaisseur(m):
    """Épaisseur moyenne des traits (px de la grille) : 2 x aire / périmètre, mesurée hors des aplats
    (les zones de plus de 6,4 px de large, comme le haut de l'éventail Uchiha, sont ignorées)."""
    d = _disque(3.2 * K)
    fins = m & ~_dilate(_erode(m, d), d)
    if fins.sum() < 0.05 * m.sum():
        fins = m
    return 2 * fins.sum() / max(1, _bord(fins).sum())


def ajuste(m, ep_actuelle, ep_visee):
    """Ramène le trait à ep_visee (px de la grille) par dilatation ou érosion d'un disque."""
    d = (ep_visee - ep_actuelle) / 2
    if abs(d) < 0.5:
        return m
    return _dilate(m, _disque(d)) if d > 0 else _erode(m, _disque(-d))


# ───────────────────────────── sources ─────────────────────────────
def rendu_svg(path, px):
    """SVG -> RGBA dont la plus grande dimension vaut px."""
    doc = pymupdf.open(path)
    pg = doc[0]
    z = px / max(pg.rect.width, pg.rect.height)
    pix = pg.get_pixmap(matrix=pymupdf.Matrix(z, z), alpha=True)
    return Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)


def encre(im, mode):
    """Masque de l'encre du blason (bool)."""
    a = np.asarray(im).astype(np.float32) / 255
    lum = a[..., :3].mean(axis=2)
    if mode == "noir":
        return (a[..., 3] > 0.5) & (lum < 0.22)
    return (a[..., 3] > 0.5) & (lum < 0.7)


def recadre(m, cadre=CADRE):
    """Recadre le masque sur son contenu, à l'échelle cadre x cadre (px finaux), centré dans 48x48 (x K)."""
    ys, xs = np.nonzero(m)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = m.shape
    s = cadre * K / max(h, w)
    nw, nh = max(1, round(w * s)), max(1, round(h * s))
    im = Image.fromarray((m * 255).astype(np.uint8), "L").resize((nw, nh), Image.LANCZOS)
    out = Image.new("L", (T * K, T * K), 0)
    out.paste(im, ((T * K - nw) // 2, (T * K - nh) // 2))
    return np.asarray(out) > 127, s


# ───────────────────────────── les emblèmes ─────────────────────────────
def masque_clan(cle):
    r = CLANS[cle]
    if "svg" in r:
        im = rendu_svg(CANON / r["svg"], 1600)
        m = encre(im, r.get("encre", "encre"))
        m, s = recadre(m, r.get("cadre", CADRE))
        ep_vis = r.get("ep", EP)
        if ep_vis is not None:
            m = ajuste(m, epaisseur(m), ep_vis * K)
        if r.get("fins"):                       # épaissit les seuls traits fins (le cercle de la lune Yotsuki)
            seuil, plus = r["fins"]
            d = _disque(seuil * K)
            fins = m & ~_dilate(_erode(m, d), d)
            m = m | _dilate(fins, _disque(plus * K))
        if r.get("gras"):                       # grossit tout le dessin (les deux points des Kaguya)
            m = _dilate(m, _disque(r["gras"] * K))
        return m
    return DESSINS[r["fn"]]()


# ───────────────────────────── dessins (clans sans blason canon) ─────────────────────────────
# Coordonnées en px finaux (grille 48), tracées en x K ; trait EP comme les villages ; réserves (vides) d'au moins
# 1,1 px pour rester lisibles à 24 px. Aucune écriture.
N = T * K


class Toile:
    def __init__(self):
        self.im = Image.new("L", (N, N), 0)
        self.d = ImageDraw.Draw(self.im)

    def m(self):
        return np.asarray(self.im) > 127

    def poly(self, pts, fill=255):
        self.d.polygon([(x * K, y * K) for x, y in pts], fill=fill)

    def trait(self, pts, ep=EP, fill=255, ferme=False):
        w = int(round(ep * K))
        q = [(x * K, y * K) for x, y in pts]
        if ferme:
            q = q + [q[0]]
        self.d.line(q, fill=fill, width=w, joint="curve")
        for x, y in (q if not ferme else q[:-1]):
            self.d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=fill)

    def disque(self, cx, cy, r, fill=255):
        self.d.ellipse(((cx - r) * K, (cy - r) * K, (cx + r) * K, (cy + r) * K), fill=fill)


def bez(p0, p1, p2, p3, n=48):
    out = []
    for i in range(n + 1):
        t = i / n
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def ellipse_pts(cx, cy, rx, ry, ang=0.0, n=72):
    ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))
    return [(cx + rx * math.cos(2 * math.pi * i / n) * ca - ry * math.sin(2 * math.pi * i / n) * sa,
             cy + rx * math.cos(2 * math.pi * i / n) * sa + ry * math.sin(2 * math.pi * i / n) * ca) for i in range(n)]


def vague(x0, x1, y, amp, periode, n=80):
    return [(x0 + (x1 - x0) * i / n, y + amp * math.sin(2 * math.pi * ((x1 - x0) * i / n) / periode)) for i in range(n + 1)]


def goutte(cx, cy, r, tip, n=72):
    """Contour d'une goutte : pointe (cx, tip), arc de cercle (cx, cy, r) par le bas."""
    a = math.asin(r / (cy - tip))
    ph = np.linspace(-(math.pi / 2 - a), math.pi + (math.pi / 2 - a), n)
    return [(cx, tip)] + [(cx + r * math.cos(p), cy + r * math.sin(p)) for p in ph]


def creuse(m, autre, marge):
    """m privé de autre dilaté de marge (px finaux)."""
    if marge <= 0:
        return m & ~autre
    return m & ~_dilate(autre, _disque(marge * K))


# ─────────────── Sabaku : la gourde de sable ───────────────
def sabaku():
    t = Toile()
    t.disque(24, 32.5, 13.0)             # panse
    t.disque(24, 14.0, 8.2)              # tête de la gourde
    t.poly([(19.5, 20.5), (28.5, 20.5), (29.5, 23.5), (18.5, 23.5)])
    corps = t.m()
    # cordon noué à l'étranglement (réserve de part et d'autre)
    c = Toile(); c.poly([(16.8, 21.0), (31.2, 21.0), (31.2, 24.0), (16.8, 24.0)]); cm = c.m()
    m = creuse(corps, cm, 1.1) | cm
    # sangle de Gaara : deux filets réservés en écharpe, sur la panse seulement
    r = Toile(); r.trait([(10, 27.5), (38, 41.5)], ep=1.3); r.trait([(10, 32.0), (35.5, 44.8)], ep=1.3)
    m &= ~r.m()
    s = Toile(); s.poly([(21.6, 1.8), (26.4, 1.8), (26.9, 5.2), (21.1, 5.2)]); sm = s.m()
    m = creuse(m, sm, 1.2) | sm
    return m


# ─────────────── Hoshigaki : l'aileron du requin ───────────────
def hoshigaki():
    t = Toile()
    ar = bez((9, 31), (14, 20), (22, 9), (33, 5))
    fu = bez((33, 5), (29, 12), (29, 23), (37, 31))
    t.poly(ar + fu)
    m = t.m()
    v = Toile(); v.trait(vague(3, 45, 32.5, 1.6, 10.5)); vm = v.m()
    m = creuse(m, vm, 1.3) | vm
    v2 = Toile(); v2.trait(vague(8, 40, 40.5, 1.6, 10.5)); m |= v2.m()
    return m


# ─────────────── Terumi : la goutte qui bout ───────────────
def terumi():
    t = Toile()
    pts = goutte(24, 29.5, 14.5, 3)
    t.trait(pts, ferme=True)
    contour = t.m()
    plein = Toile(); plein.poly(pts); interieur = plein.m() & ~contour
    interieur = _erode(interieur, _disque(1.4 * K))
    nv = Toile(); nv.poly(vague(4, 44, 27.5, 1.7, 9.5) + [(44, 48), (4, 48)]); bas = nv.m()
    m = contour | (interieur & bas)
    bu = Toile(); bu.disque(21.5, 20.5, 2.0); bu.disque(26.2, 15.5, 1.5); m |= bu.m() & interieur
    return m


# ─────────────── Ōnoki : la montagne ───────────────
def onoki():
    t = Toile(); t.poly([(27, 6), (44, 39), (10, 39)]); grand = t.m()
    nei = Toile(); nei.trait([(19.6, 20.5), (23.2, 17.0), (26.2, 20.6), (29.4, 16.6), (33.6, 21.0)], ep=2.0); nm = nei.m()
    grand &= ~nm
    p = Toile(); p.trait([(4, 39), (14, 21), (22, 33)]); petit = p.m()
    m = creuse(petit, grand, 1.4) | grand
    sol = Toile(); sol.trait([(3, 43.5), (45, 43.5)]); m |= sol.m()
    return m


# ─────────────── Hōki : les feuilles qui soignent ───────────────
def feuille(base, tip, larg):
    bx, by = base; tx, ty = tip
    dx, dy = tx - bx, ty - by
    L = math.hypot(dx, dy); nx, ny = -dy / L, dx / L
    c1 = (bx + dx * 0.3 + nx * larg, by + dy * 0.3 + ny * larg)
    c2 = (bx + dx * 0.75 + nx * larg * 0.8, by + dy * 0.75 + ny * larg * 0.8)
    c3 = (bx + dx * 0.75 - nx * larg * 0.8, by + dy * 0.75 - ny * larg * 0.8)
    c4 = (bx + dx * 0.3 - nx * larg, by + dy * 0.3 - ny * larg)
    return bez(base, c1, c2, tip) + bez(tip, c3, c4, base)


def hoki():
    t = Toile()
    t.poly(feuille((22.5, 41), (5, 17), 7.5))
    t.poly(feuille((25.5, 41), (43, 17), 7.5))
    m = t.m()
    nerv = Toile(); nerv.trait([(21, 37.5), (11, 24)], ep=1.6); nerv.trait([(27, 37.5), (37, 24)], ep=1.6)
    m &= ~nerv.m()
    tige = Toile(); tige.trait([(24, 39), (24, 45)]); m |= tige.m()
    gt = Toile(); gt.poly(goutte(24, 22.5, 5.2, 9.5)); gm = gt.m()
    m = creuse(m, gm, 1.3) | gm
    return m


# ─────────────── Kamizuru : l'abeille ───────────────
def kamizuru():
    t = Toile()
    t.disque(24, 10.5, 3.6)
    t.poly(ellipse_pts(24, 19.2, 5.0, 4.6))
    t.poly(ellipse_pts(24, 33, 7.2, 10.6))
    t.poly([(22.6, 42.5), (25.4, 42.5), (24, 46.5)])
    m = t.m()
    r = Toile()
    for y in (29.0, 34.4):
        r.trait([(14, y), (34, y)], ep=1.7)
    r.trait([(14, 23.6), (34, 23.6)], ep=1.4)
    r.trait([(14, 14.4), (34, 14.4)], ep=1.2)
    m &= ~r.m()
    ai = Toile()
    ai.trait(ellipse_pts(12.5, 19.5, 9.2, 4.8, -28), ep=2.4, ferme=True)
    ai.trait(ellipse_pts(35.5, 19.5, 9.2, 4.8, 28), ep=2.4, ferme=True)
    am = ai.m()
    m = m | creuse(am, t.m(), 1.3)
    an = Toile()
    an.trait(bez((22.4, 8), (21, 5), (19, 3.6), (16.5, 3.2)), ep=1.8)
    an.trait(bez((25.6, 8), (27, 5), (29, 3.6), (31.5, 3.2)), ep=1.8)
    m |= an.m()
    return m


# « Autre » : maison au toit à pignon (une famille, un nom à écrire) — pas de clan, pas de canon.
def autre():
    t = Toile()
    t.trait([(3.5, 21), (24, 6.5), (44.5, 21)])
    t.trait([(10, 21), (10, 44), (38, 44), (38, 21)])
    t.trait([(20, 44), (20, 31), (28, 31), (28, 44)])
    return t.m()


DESSINS = dict(hoki=hoki, hoshigaki=hoshigaki, terumi=terumi, onoki=onoki, sabaku=sabaku, kamizuru=kamizuru,
               autre=autre)



def rendu(m) -> Image.Image:
    petit = Image.fromarray((m * 255).astype(np.uint8), "L").resize((T, T), Image.LANCZOS)
    a = np.asarray(petit).astype(np.float32)
    a = np.clip((a - 30) / (225 - 30), 0, 1) * 255
    out = np.zeros((T, T, 4), np.uint8)
    out[..., :3] = 255
    out[..., 3] = a.astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def emblem(cle) -> Image.Image:
    return rendu(masque_clan(cle))


def stats(im):
    a = np.asarray(im)[..., 3]
    return (a > 128).sum(), im.getbbox()


# ───────────────────────────── planche de contrôle ─────────────────────────────
def planche(chemin_png, ims, refs=None):
    """Pour chaque village puis chaque clan : référence canon (SVG d'origine, en couleurs, sur papier) |
    notre emblème x3 crème sur laque, puis à taille réelle (48, 24, 16 px). Statut sous le nom."""
    try:
        f = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 13)
        fk = ImageFont.truetype(r"C:\Windows\Fonts\YuGothB.ttc", 15)
    except OSError:
        f = fk = ImageFont.load_default()
    cw, ch, cols = 400, 236, 4
    items = [("v", v) for v in VILLAGES] + [("c", k) for k in CLANS]
    rows = math.ceil(len(items) / cols)
    W = Image.new("RGBA", (cols * cw, rows * ch), (28, 18, 22, 255))
    d = ImageDraw.Draw(W)
    creme, gris, papier = (245, 233, 208), (180, 160, 130), (236, 228, 212, 255)

    def teinte(im, rgb):
        a = np.asarray(im).astype(np.float32)
        a[..., 0], a[..., 1], a[..., 2] = rgb
        return Image.fromarray(a.astype(np.uint8), "RGBA")

    def ref_svg(path):
        im = rendu_svg(path, 136)
        c = Image.new("RGBA", (144, 144), papier)
        c.alpha_composite(im, ((144 - im.width) // 2, (144 - im.height) // 2))
        return c

    for i, (genre, k) in enumerate(items):
        x, y = (i % cols) * cw + 10, (i // cols) * ch + 8
        # gauche : canon
        ref = None
        if genre == "v":
            noms = {"konoha": "Konohagakure", "suna": "Sunagakure", "kiri": "Kirigakure", "kumo": "Kumogakure",
                    "iwa": "Iwagakure"}
            p = LOGOS_VILLAGES / f"{noms.get(k, '-')}.svg"
            if not p.exists() and refs:
                p = Path(refs) / f"{k}.svg"
            if p.exists():
                ref = ref_svg(p)
            im = Image.open(RES / f"esc/village_{k}.png").convert("RGBA")
        else:
            r = CLANS[k]
            if "svg" in r:
                ref = ref_svg(CANON / r["svg"])
            im = ims[k]
        if ref is None:
            ref = Image.new("RGBA", (144, 144), papier)
            rd = ImageDraw.Draw(ref)
            rd.text((72, 62), "pas de blason", font=f, fill=(120, 40, 40), anchor="mm")
            if genre == "c" and "fn" in CLANS[k] and k != "autre":
                rd.text((72, 82), "création Reborn", font=f, fill=(60, 60, 60), anchor="mm")
        W.alpha_composite(ref, (x, y))
        # droite : nous
        W.alpha_composite(teinte(im, creme).resize((144, 144), Image.NEAREST), (x + 152, y))
        d.rectangle((x + 151, y - 1, x + 296, y + 144), outline=(70, 50, 50, 255))
        W.alpha_composite(teinte(im, creme), (x + 304, y))
        W.alpha_composite(teinte(im, creme).resize((24, 24), Image.BOX), (x + 304, y + 56))
        W.alpha_composite(teinte(im, (217, 169, 94)).resize((16, 16), Image.BOX), (x + 334, y + 60))
        nom = ("VILLAGE " + k) if genre == "v" else k
        d.text((x, y + 150), nom, font=f, fill=creme)
        if genre == "c":
            n, bb = stats(im)
            d.text((x + 150, y + 150), f"{n} px  {bb[2] - bb[0]}x{bb[3] - bb[1]}", font=f, fill=gris)
            st = CLANS[k]["statut"]
            lignes, cur = [], ""
            for mot in st.split(" "):
                if len(cur) + len(mot) > 40:
                    lignes.append(cur); cur = mot
                else:
                    cur = (cur + " " + mot).strip()
            lignes.append(cur)
            col = (150, 200, 150) if st.startswith("CRÉATION") else gris
            for j, l in enumerate(lignes[:3]):
                d.text((x, y + 168 + j * 18), l, font=fk, fill=col)
    W.save(chemin_png)
    return chemin_png


def main(argv):
    pl = refs = None
    for opt in ("--planche", "--refs"):
        if opt in argv:
            i = argv.index(opt)
            if opt == "--planche":
                pl = argv[i + 1]
            else:
                refs = argv[i + 1]
            argv = argv[:i] + argv[i + 2:]
    cles = argv or list(CLANS)
    SORTIE.mkdir(parents=True, exist_ok=True)
    ims = {}
    for k in CLANS:
        ims[k] = emblem(k)
        if k in cles:
            ims[k].save(SORTIE / f"clan_{k}.png")
            n, bb = stats(ims[k])
            print(f"clan_{k}.png  {n:4d} px  boîte {bb}")
    pl = pl or os.path.join(tempfile.gettempdir(), "planche_clans.png")
    print("planche :", planche(pl, ims, refs))


if __name__ == "__main__":
    main(sys.argv[1:])
