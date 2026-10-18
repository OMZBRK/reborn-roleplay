# -*- coding: utf-8 -*-
"""
gen_fiche_textures.py — textures de la fiche shinobi « Le portrait en scène » (v2, maquette FINALE tour 4).

Génère dans src/main/resources/assets/reborn/textures/gui/fiche/ toutes les parties FIXES de la
fiche (DA §6 : ce qui ne bouge pas = une texture pixel art affichée en un blit, 1 texel = 1 GUI).
Référence : Maquettes/fiche-shinobi/v2/portrait/maquette.py (tour 4) — les primitives propres à la
maquette sont portées plus bas (section « primitives tour 4 »), celles du tour 2 viennent de
tools/fiche_art.py, la boîte à outils commune de tools/style/kit.py (lecture seule).

Usage (depuis minecraft/mod-hud) :
    python tools/gen_fiche_textures.py

Contrat (ancres en texels ↔ coordonnées GUI de la fenêtre 960×540, d = H − 540) :
- fond_*.png 1280×540 : texel x = x_GUI − cx + 640 ; posé en (cx − 640, d).
- entete.png 320×48 : texel (160, 0) = (cx, 0).
- onglets.png 78×36 : 3 cellules 26×36 (repos, survol, actif) ; texel (13, 0) = (TAB_X, 36).
- onglets_pictos.png 192×16 : pictos 16×16 des onglets (identité, attributs, techniques, état) × état (repos,
  survol, actif) : u = (onglet·3 + état)·16 ; posé en (TAB_X − 8, y plaquette + 3). Aucun kanji (18/10, DA §3).
- fermer.png 52×38 : 2 cellules 26×38 (repos, survol), plaque à croix ; texel (13, 0) = (W − 40, 0).
- perche.png 300×16 : rangées 0-7 perche gauche (embout à droite), 8-15 perche droite (embout à
  gauche) ; rangée 0 du bloc = y 55.
- panneau_*.png (w+8)×(84+h+28), w = 240 : texel (4, 84) = coin haut gauche de la planche ;
  cordelettes depuis la perche (y 56).
- liste_bas.png 248×28 : texel (4, 0) = (x0, y0 + h − 22) de la liste des techniques (fondu).
- infobulle.png 226×44 : planche 218 en (4, 2) ; HAUT 0-15 / MILIEU 16-23 / BAS 24-43.
- lanternes_s.png 180×288 (30×48), lanternes_l.png 204×312 (34×52) : colonnes = CRANS, rangées =
  stats ; flammes_s / flammes_l : 6 calques plein format (grande ×2, moyenne ×2, braise ×2).
- hanko.png 240×100 : cellules 20×20, sceau 18×18 en (0, 0) (ombre +1) ; depuis le 18/10 (aucun kanji) seules
  servent la rangée 3 (rangs D C B A S en 6-10, sceau papier « de base » à losange en 11) et la rangée 4 (pictos
  de blessure : contusion, brûlure, fracture, plaie, infection) ; les rangées 0-2 et 3 (0-5) sont vides.
- ornements.png 80×8 : anneau, perle, pastille, petit anneau, losange, tampon plein, tampon vide,
  nœud ; soucoupe 16×5 en (64, 0).
- ema.png 576×168 : cellules 96×84 (repos, survol) ; texel (48, 0) = (LAN_X, 360) : piquet, ema
  92×56, cordelette, anneau, nom de la stat.
- chevilles.png 80×10 : − actif, − inactif, + actif, + inactif, − survol, + survol, − pressé, + pressé.
- mat.png 24×348 : deux mâts de bambou ; texel (4, 0) = (MAT_X − 1, 87).
- seuil.png 64×26 : fil d'or 48×2 (période 3) en (0, 0), agemaki or 9×9 en (48, 0), plaque SEUIL
  pendue : texel (0, 10) = (ex, ey).
- barre.png 368×36 : barre d'actions 360×28 en (2, 2).
- boutons.png 348×104 : rangées de 26 : RÉINITIALISER 82 (neutre, survol, inactif, armé),
  ANNULER 70 (normal, survol, inactif), compteur 94, VALIDER 82 (actif, inactif, survol) ; bouton
  en (1, 1) de chaque cellule, plaque nue (libellé centré posé par le code).
- ligne_choisie.png 228×24 : texel (3, 0) = (x0, yc − 11).
- carte_blessure.png 448×44 : 2 cellules 224×44, carte 218×38 en (1, 1).
- meches.png 18×8 (vive, vacillante, faible) ; kumihimo.png 32×2 ; cabochon.png 42×12.

Déterminisme : graines fixes partout ; deux exécutions écrivent les mêmes octets.
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ICI)
import fiche_art as fa  # noqa: E402
from fiche_art import kit, hexc, rect, arcade, coller, R_OR, R_LAQUE, R_LAQUE_ROUGE, STATS, PAL  # noqa: E402
import pictos  # noqa: E402  (pictogrammes : plus aucun kanji décoratif, 18/10)

SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "fiche")
TEX_ESC = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "esc")
BUDGET = 1_500_000
OX = 160                      # décalage fenêtre 960 -> fond 1280
CRANS_L = [0.45, 0.70, 0.80, 0.875, 0.94, 1.00]

ECRITS = []                   # (nom, (w, h), octets, mode)

# ───────────────────────── palette (maquette tour 4) ─────────────────────────
OR = fa.OR
OR_CLAIR = fa.OR_CLAIR
OR_MOYEN = hexc("C8A05A")
OR_ETIQ = hexc("A8844A")
OR_ETEINT = hexc("8E6A30")
BISEAU_CLAIR = fa.BISEAU_CLAIR
BISEAU_SOMBRE = fa.BISEAU_SOMBRE
CREME = fa.CREME
CREME_KJ = hexc("F2E6CC")
KONOHA = hexc("F2A548")
ENCRE_LANTERNE = (42, 14, 18)

R_VERM = ["4E0C0B", "6E1511", "8E2018", "AE2C21", "C23A2C", "D2523E", "E0705A"]
R_IND = ["0E1222", "161C34", "1F2848", "2A3558", "38466E", "4C5C86", "6476A0"]
R_JADE = ["10201A", "1A3228", "264638", "345C4A", "467560", "5E8E78", "7CAA94"]
R_ORL = ["3A2A12", "5A4220", "7A5A2A", "9A7434", "B88C44", "D2A75A", "E8C27A"]
R_BRUN = ["2A1C10", "3E2A18", "584026", "765638", "947050", "B08C68", "C8A884"]
RANG_COL = {"D": "3E8F62", "C": "C9A332", "B": "B0303A", "A": "7E52B4", "S": "4E4A42"}

CIEL = ["0B0812", "0E0A17", "110C1C", "140E21", "181026", "1D1229", "23152B"]
Y_SOL = 404
Y_LOIN = 324
Y_PERCHE = 56
PG_X, PD_X, PW = 24, 696, 240


def nouveau(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def mix(a, b, t):
    return fa.mix(a, b, t)


def desat(c, k=0.8):
    return fa.desat(c, k)


_bord = fa._bord


def _dilate(m, n=1):
    for _ in range(n):
        d = m.copy()
        d[1:, :] |= m[:-1, :]; d[:-1, :] |= m[1:, :]; d[:, 1:] |= m[:, :-1]; d[:, :-1] |= m[:, 1:]
        m = d
    return m


def sauver(nom, img, taille, garder_rgb=False, quantifier=False):
    """Écrit un PNG après vérification de la taille. garder_rgb : laisse la couleur des texels
    transparents (masques blancs étirés au filtrage lisse) ; sinon ils deviennent (0,0,0,0).
    quantifier : image opaque réduite à 256 couleurs."""
    if isinstance(img, np.ndarray):
        img = Image.fromarray(img, "RGBA")
    img = img.convert("RGBA")
    assert img.size == tuple(taille), f"{nom} : taille {img.size} au lieu de {taille}"
    a = np.asarray(img).copy()
    if not garder_rgb:
        a[a[..., 3] == 0] = 0
    chemin = os.path.join(SORTIE, nom)
    plat = a.reshape(-1, 4)
    couleurs, inv = np.unique(plat, axis=0, return_inverse=True)
    if len(couleurs) <= 256:
        p = Image.fromarray(inv.reshape(a.shape[:2]).astype(np.uint8), "P")
        p.putpalette(couleurs[:, :3].astype(np.uint8).flatten().tolist(), rawmode="RGB")
        if (couleurs[:, 3] < 255).any():
            p.info["transparency"] = bytes(couleurs[:, 3].astype(np.uint8).tolist())
            p.save(chemin, optimize=True, transparency=p.info["transparency"])
        else:
            p.save(chemin, optimize=True)
        mode = f"P{len(couleurs)}"
        relu = np.asarray(Image.open(chemin).convert("RGBA"))
        assert (relu == a).all(), f"{nom} : relecture différente"
    elif quantifier:
        assert (a[..., 3] == 255).all(), f"{nom} : quantification réservée aux images opaques"
        q = Image.fromarray(a[..., :3], "RGB").quantize(256, method=Image.Quantize.MEDIANCUT,
                                                        dither=Image.Dither.NONE)
        q.save(chemin, optimize=True)
        mode = "P256q"
    else:
        Image.fromarray(a, "RGBA").save(chemin, optimize=True)
        mode = "RGBA"
    with Image.open(chemin) as v:
        assert v.size == tuple(taille), f"{nom} : relu en {v.size}"
    ECRITS.append((nom, tuple(taille), os.path.getsize(chemin), mode))


# ═════════════════════════════════════ primitives tour 4 (portées de maquette.py) ═════════════════════════════════════
def _ajouter(img, x0, y0, A, couleur, niveaux=4, tramage=0.16, sat=0.8):
    fa._ajouter(img, x0, y0, A, couleur, niveaux, tramage, sat)


def halo_px1(img, cx, cy, r, couleur, force=0.15, niveaux=3, ry_k=1.0, sat=0.8):
    """halo_px de la maquette, à la résolution GUI (1 texel = 1 GUI) : ovales concentriques nets."""
    if force <= 0.002:
        return
    parts = {1: [1.0], 2: [0.4, 1.0], 3: [0.27, 0.53, 1.0]}[niveaux]
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


def brume4(bg, y, ep=12, force=0.06, seed=0, couleur=(120, 112, 160)):
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


def mini_lanterne4(img, X, Y, s_, t1, t2, op):
    w = max(2, int(round(s_ * 0.7)))
    for j in range(s_):
        ww = w - (2 if (j == 0 and s_ >= 5 and w >= 4) else 0)
        ox = (w - ww) // 2
        ton = t1 if j < max(1, s_ // 2) else t2
        for i in range(ww):
            _px_mix(img, X + ox + i, Y + j, ton, op)
    _px_mix(img, X + (w - 1) // 2, Y + s_ - 1, hexc("FFD27A"), min(1.0, op + 0.3))


def lanternes_lointaines4(bg, zones, seed=5, n=32, ox=0, cotes=0):
    """Plan lointain (tirage IDENTIQUE à la maquette dans la fenêtre 960, décalé de `ox`) ; `cotes`
    lanternes de plus par côté dans les bandes prolongées. Renvoie les halos [(cx, cy, r, col, force)]."""
    rs = random.Random(seed)
    poses = []
    essais = 0
    while len(poses) < n and essais < 60000:
        essais += 1
        x = rs.uniform(10, 950)
        y = 14 + (Y_LOIN - 14) * rs.random() ** 0.85
        ok = all(not (a - mg <= x <= c + mg and b - mg <= y <= d + mg) for (a, b, c, d, mg) in zones)
        if ok:
            for (px_, py_, _s) in poses:
                if math.hypot(px_ - x, (py_ - y) * 1.3) < 34:
                    ok = False
                    break
        if ok:
            prof = (y - 14) / (Y_LOIN - 14)
            s_ = int(round(2 + 4 * prof + rs.uniform(-0.5, 0.5)))
            poses.append((x, y, max(2, min(6, s_))))
    tout = [(x + ox, y, s_) for (x, y, s_) in poses]
    if cotes and ox > 0:
        r2 = random.Random(seed * 7919 + 13)
        for cote in (-1, 1):
            k_, essais = 0, 0
            while k_ < cotes and essais < 5000:
                essais += 1
                x = r2.uniform(8, ox - 4) if cote < 0 else r2.uniform(ox + 964, ox * 2 + 952)
                y = 14 + (Y_LOIN - 14) * r2.random() ** 0.85
                if all(math.hypot(a - x, (b - y) * 1.3) >= 34 for (a, b, _s) in tout):
                    prof = (y - 14) / (Y_LOIN - 14)
                    s_ = max(2, min(6, int(round(2 + 4 * prof + r2.uniform(-0.5, 0.5)))))
                    tout.append((x, y, s_))
                    k_ += 1
    halos = []
    for k, (x, y, s_) in enumerate(tout):
        prof = (y - 14) / (Y_LOIN - 14)
        op = 0.45 + 0.40 * prof
        if k % 7 == 3:
            c = STATS[(k // 7) % 6][3]
            t1, t2 = mix(c, (20, 10, 16), 0.45), mix(c, (255, 220, 170), 0.10)
        else:
            t1, t2 = hexc("8A4A22"), hexc("C77A3A")
        mini_lanterne4(bg, int(x), int(y), s_, t1, t2, op)
        if s_ >= 4:
            w = max(2, int(round(s_ * 0.7)))
            halos.append((int(x) + w / 2, int(y) + s_ * 0.7, s_ * 1.5, t2, 0.05 + 0.04 * prof))
    return halos


VP_Y = 292.0
LAME = 46.0
RANGS = [404, 414, 425, 437, 450, 464, 479, 495, 512, 530, 549]
GAINS = [0.62, 0.78, 0.92, 1.05, 1.20]
_TEX_LAME = {}


def _tex_lame(k):
    if k not in _TEX_LAME:
        tmp = Image.new("RGBA", (48, 300), (0, 0, 0, 0))
        kit.bois(tmp, 0, 0, 48, 300, "sombre", sens="v", seed=211 + 53 * k, noeuds=1 if k == 2 else 0, biseau=False)
        _TEX_LAME[k] = np.asarray(tmp).astype(float)
    return _TEX_LAME[k]


def plancher4(bg, seed=3, vernis=(26, 20, 38), sombre=0.50, vpx=480.0, ox=0):
    """Plancher de scène laqué (maquette tour 4) : lames qui fuient vers le personnage, 5 valeurs,
    joints d'1 GUI, aboutements décalés, arête or éteint, vernis spéculaire tramé."""
    W = bg.width
    y_bas = 540
    ys = np.arange(Y_SOL, y_bas)
    xs = np.arange(0, W)
    T = ((ys + 0.5 - VP_Y) / (540 - VP_Y))[:, None]
    U = (xs[None, :] + 0.5 - vpx) / T
    b = np.floor(U / LAME + 0.5).astype(int)
    frac = (U / LAME + 0.5) - np.floor(U / LAME + 0.5)
    rang = np.searchsorted(RANGS, ys, side="right")[:, None] - 1
    seg = (rang + (b * 2) % 3) // 3
    h = (b * 7349 + seg * 1931 + seed * 97) % 1000
    var = h % 4
    gain = np.array(GAINS)[(h * 37 // 7) % 5]
    off = (h * 13) % 200
    Z = 1.0 / T
    rowtex = ((Z - 1.0) * 120 + off).astype(int) % 300
    coltex = np.clip((frac * 48).astype(int), 0, 47)
    out = np.zeros((len(ys), len(xs), 3))
    for k in range(4):
        tex = _tex_lame(k)
        sel = var == k
        out[sel] = tex[rowtex[sel], coltex[sel], :3]
    prof = 1 - (T - T.min()) / (T.max() - T.min())
    lum = (sombre + 0.10 * (1 - prof)) * gain
    out = out * lum[..., None] + np.array(vernis)[None, None, :] * (0.16 + 0.26 * prof)[..., None] \
        + (1 - prof)[..., None] * np.array([8, 6, 8])
    jt = np.zeros_like(b, bool)
    jt[:, 1:] = b[:, 1:] != b[:, :-1]
    out[jt] *= 0.26
    jc = np.zeros_like(jt)
    jc[:, 1:] = jt[:, :-1]
    out[jc] = np.minimum(255, out[jc] * 1.22 + 4)
    for r_, yr in enumerate(RANGS[1:], start=1):
        if yr >= y_bas:
            break
        j = yr - Y_SOL
        stop = ((r_ + (b[j] * 2) % 3) % 3) == 0
        out[j, stop] *= 0.30
        if j + 1 < out.shape[0]:
            out[j + 1, stop] = np.minimum(255, out[j + 1, stop] * 1.18 + 3)
    for j in range(2, 11):
        a = 0.20 * (1 - (j - 2) / 9)
        bay = kit.bayer(1, len(xs), 0, Y_SOL + j)[0]
        lit = bay < (a / 0.20) * 0.85
        out[j, lit] = out[j, lit] + np.array([54, 44, 66]) * a * 1.6
    arr = np.asarray(bg).astype(float).copy()
    arr[Y_SOL:y_bas, :, :3] = np.clip(out, 0, 255)
    arr[Y_SOL:y_bas, :, 3] = 255
    bg.paste(Image.fromarray(arr.astype(np.uint8), "RGBA"), (0, 0))
    rect(bg, 0, Y_SOL - 1, W, 1, hexc("050304"))
    rect(bg, 0, Y_SOL, W, 1, hexc(R_OR[2]))
    rect(bg, 0, Y_SOL + 1, W, 1, hexc("1A1012"))
    for x in range((7 + ox) % 61, W, 61):
        rect(bg, x, Y_SOL, 2, 1, hexc(R_OR[4]))


def liseret4(img, x, y, w, h, terne=False):
    c1, c2, c3 = (BISEAU_CLAIR, BISEAU_SOMBRE, OR) if not terne else (hexc(R_OR[3]), hexc(R_OR[1]), hexc(R_OR[2]))
    rect(img, x, y, w, 1, c1)
    rect(img, x, y, 1, h, c1)
    rect(img, x, y + h - 1, w, 1, c2)
    rect(img, x + w - 1, y, 1, h, c2)
    rect(img, x, y + h - 1, 1, 1, c3)
    rect(img, x + w - 1, y, 1, 1, c3)


def agemaki4(img, cx, cy, couleur="rouge"):
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


def corde_molle(img, pts, mou=3, couleur="rouge", ep=2):
    R = [hexc(c) for c in kit.R_CORDE[couleur]]
    k = 0
    for (xa, ya), (xb, yb) in zip(pts[:-1], pts[1:]):
        L = xb - xa
        for x in range(int(xa), int(xb) + 1):
            t = (x - xa) / max(1, L)
            y = int(round(ya + (yb - ya) * t + mou * 4 * t * (1 - t)))
            rect(img, x, y, 1, 1, (R[3], R[2], R[2])[k % 3])
            rect(img, x, y + 1, 1, 1, (R[1], R[3], R[1])[k % 3])
            k += 1


def kj_px(img, cx, y, texte, couleur, ombre=None, ancre="c"):
    """Kanji unifont 16 px posé sur la couche GUI (1 px de glyphe = 1 GUI)."""
    g = kit._glyphe_kanji_ligne(texte, 16, "unifont")
    h, w = g.shape
    x = int(round(cx - w / 2)) if ancre == "c" else (int(round(cx - w)) if ancre == "d" else int(round(cx)))
    y = int(round(y))
    if ombre is not None:
        p = np.zeros((h, w, 4), np.uint8)
        p[g] = hexc(ombre)
        coller(img, p, x + 1, y + 1)
    p = np.zeros((h, w, 4), np.uint8)
    p[g] = hexc(couleur)
    coller(img, p, x, y)
    return w


def hanko_px(img, cx, cy, kj, taille=18, ton=None, forme="carre", seed=0, encre=None, usure=0.10):
    """Sceau pixel (maquette hanko_px) : laque 4 tons, biseaux, kanji unifont en réserve."""
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
    if isinstance(kj, np.ndarray) or kj:
        if isinstance(kj, np.ndarray):           # encre fournie (picto, tools/pictos.py) : masque T×T
            ink = kj.copy()
        else:
            g = kit._glyphe_kanji(kj, 16, "unifont")
            gh, gw = g.shape
            gx, gy = int(round(c0 - gw / 2 + 0.5)), int(round(c0 - gh / 2 + 0.5))
            ink = np.zeros((T, T), bool)
            ink[max(0, gy):gy + gh, max(0, gx):gx + gw] = g[max(0, -gy):T - gy, max(0, -gx):T - gx]
        ink &= mk & ~b
        col = hexc(encre or CREME_KJ)
        p[ink] = col
        if sum(col[:3]) > 300:
            ombre = np.zeros_like(ink); ombre[1:, 1:] = ink[:-1, :-1]
            ombre &= mk & ~ink & ~b
            p[ombre] = R[0]
    else:
        ink = np.zeros((T, T), bool)
    rs = np.random.RandomState(seed)
    trous = mk & ~b & ~ink & (rs.rand(T, T) < usure * 0.35)
    p[trous] = R[min(n - 1, 2)]
    kit.ombre_portee(img, int(cx - T // 2), int(cy - T // 2), T, T, dx=1, dy=1, force=0.55, rayon=0, masque=mk)
    coller(img, p, int(cx - T // 2), int(cy - T // 2))


def ton_stat(i):
    return [tuple(int(v) for v in c[:3]) for c in kit.rampe(STATS[i][3], 7, ombre=0.25, lumiere=0.35)]


def plaquette4(px, x, y, w, h, kind="noir", seed=0, terne=False):
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=1, force=0.55, rayon=0)
    if kind == "rouge":
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=seed)
    elif kind == "or":
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, ["5A4220", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=seed)
    else:
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=seed, intensite=1.3)
    liseret4(px, x, y, w, h, terne=terne)


def losange_or(px, x, y):
    for (dx, dy, c) in ((1, 0, R_OR[7]), (0, 1, R_OR[5]), (1, 1, R_OR[6]), (2, 1, R_OR[4]), (1, 2, R_OR[3])):
        rect(px, x + dx, y + dy, 1, 1, hexc(c))


def tampon_palier(px, x, yc, plein=True):
    c1, c2 = (hexc(R_OR[6]), hexc(R_OR[3])) if plein else (hexc(R_OR[3]), hexc(R_OR[1]))
    y = int(yc) - 2
    for (dx, dy) in ((2, 0), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (2, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (2, 4)):
        rect(px, x + dx, y + dy, 1, 1, c1 if dy <= 1 or (dy == 2 and dx <= 2) else c2)


def sceau_rang4(px, cx, cy, r, taille=18):
    if r is None:
        hanko_px(px, cx, cy, pictos.encre_px(taille, "base"), taille, ["3A2E1E", "6A5A40", "C2B28E", "C8B894", "CCBC98", "E2D6B6"],
                 seed=3, encre=hexc("3A2414"), usure=0.0)
        return
    R = [tuple(int(v) for v in c[:3]) for c in kit.rampe(RANG_COL[r], 6, ombre=0.30, lumiere=0.35)]
    x, y = int(cx - taille // 2), int(cy - taille // 2)
    kit.ombre_portee(px, x, y, taille, taille, dx=1, dy=1, force=0.55, rayon=0)
    fa.lq(px, x + 1, y + 1, taille - 2, taille - 2, R, seed=ord(r), intensite=1.0)
    rect(px, x, y, taille, 1, R[4]); rect(px, x, y, 1, taille, R[4])
    rect(px, x, y + taille - 1, taille, 1, R[0]); rect(px, x + taille - 1, y, 1, taille, R[0])
    lw = kit.arcade_largeur(r, 10)
    arcade(px, x + (taille - lw + 1) // 2, y + (taille - 7) // 2, r, 10, CREME_KJ, ombre=True)


# ─────────── lanterne céleste (tour 4) ───────────
def _profil4(W, Hp, d):
    hw = []
    Wh = W / 2
    for j in range(Hp):
        t = j / (Hp - 1)
        u = min(1.0, max(0.0, (t - 0.22) / 0.78))
        h = Wh * (1 - 0.26 * (u * u * (3 - 2 * u)) ** 0.9)
        if j < d:
            k = (d - j - 0.5) / d
            h = min(h, Wh * (1 - k ** 2.2) ** (1 / 2.2))
        hw.append(h)
    return hw


_LCACHE = {}


def lanterne_sprite4(stat, W=30, H=48, L=1.0, kanji=True, seed=0):
    """Lanterne céleste de la maquette tour 4 (sans flamme : calque à part)."""
    key = (stat, W, H, round(L, 3), kanji, seed)
    if key in _LCACHE:
        return _LCACHE[key]
    col, kj = STATS[stat][3], STATS[stat][4]
    R = kit.rampe(col, 9, ombre=0.18, lumiere=0.78)
    pale = (255, 241, 196)
    ry = 3
    Hp = H - ry - 3
    d = 10
    hw = _profil4(W, Hp, d)
    cx = (W - 1) / 2
    m = np.zeros((H, W), bool)
    for j in range(Hp):
        for i in range(W):
            if abs(i - cx) <= hw[j] - 0.15:
                m[j, i] = True
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    fy = Hp + 0.5
    dist = np.sqrt(((xx - cx) / (W * 0.66)) ** 2 + ((yy - fy) / (Hp * 1.0)) ** 2)
    v = np.clip(1.0 - dist, 0, 1) * (0.55 + 0.45 * L) + 0.10 * L
    u = np.abs(xx - cx) / (W / 2)
    v -= 0.10 * u ** 3
    fib = kit.bruit(W, H, 2.0, seed + 11, 2, etire=(1, 3))
    v += (fib - 0.5) * 0.04
    idx = kit.quantifier(np.clip(v, 0, 0.999), 4, 0, 0, tramage=0.18)
    steps = [2, 4, 6, 7] if L > 0.85 else ([2, 3, 5, 6] if L > 0.6 else [1, 2, 3, 4])
    p = np.zeros((H, W, 4), np.uint8)
    for j in range(H):
        for i in range(W):
            if m[j, i]:
                k = steps[idx[j, i]]
                c = R[k][:3]
                if idx[j, i] == 3 and L > 0.85 and v[j, i] > 0.80:
                    c = mix(c, pale, 0.40)
                p[j, i, :3] = c
                p[j, i, 3] = 255
    rs = np.random.RandomState(seed + 3)
    for (j, i) in zip(*np.nonzero(m)):
        r = rs.rand()
        if r < 0.03:
            p[j, i, :3] = mix(p[j, i], pale, 0.16)
        elif r < 0.055:
            p[j, i, :3] = mix(p[j, i], (20, 8, 10), 0.12)
    for frac in (-0.66, -0.22, 0.22, 0.66):
        for j in range(d - 2, Hp - 1):
            i = int(round(cx + frac * hw[j]))
            if 0 <= i < W and m[j, i]:
                p[j, i, :3] = mix(p[j, i], (24, 8, 12), 0.28)
    for i in range(W):
        if m[d - 3, i]:
            p[d - 3, i, :3] = mix(p[d - 3, i], (24, 8, 12), 0.26)
        if m[Hp - 1, i]:
            p[Hp - 1, i, :3] = mix(p[Hp - 1, i], (30, 12, 10), 0.35)
    for j in range(0, 3):
        for i in range(W):
            if m[j, i] and ((i - cx) / 3.4) ** 2 + ((j - 0.4) / 1.5) ** 2 <= 1:
                p[j, i, :3] = mix(R[0], (12, 4, 8), 0.5)
    b = _bord(m)
    p[b, :3] = mix(R[0], (16, 6, 12), 0.45)
    for i in range(W):
        c_ = np.nonzero(m[:, i])[0]
        if len(c_) > 3 and 3.8 < abs(i - cx) < W * 0.34:
            p[c_[0] + 1, i, :3] = mix(p[c_[0] + 1, i], (255, 236, 210), 0.20)
    if kanji and kj:
        g = kit._glyphe_kanji(kj, 16, "unifont")
        gh, gw = g.shape
        gy0 = int(round(Hp * 0.44 - gh / 2))
        gx0 = int(round(cx - gw / 2 + 0.5))
        ink = np.zeros((H, W), bool)
        ink[gy0:gy0 + gh, gx0:gx0 + gw] = g
        ink &= m & ~b
        sous = [p[j, i, :3].mean() for (j, i) in zip(*np.nonzero(ink))]
        if sous and np.mean(sous) < 95:
            for (j, i) in zip(*np.nonzero(ink)):
                p[j, i, :3] = mix(CREME_KJ, p[j, i], 0.22)
        else:
            dessous = np.zeros_like(ink); dessous[1:, :] = ink[:-1, :]
            dessous &= ~ink & m & ~b
            for (j, i) in zip(*np.nonzero(dessous)):
                p[j, i, :3] = mix(p[j, i], pale, 0.42)
            for (j, i) in zip(*np.nonzero(ink)):
                p[j, i, :3] = ENCRE_LANTERNE
    ycen = Hp + ry - 1
    rx = hw[-1] + 0.4
    bb = {"o": (34, 20, 12), "d": (82, 54, 30), "m": (140, 98, 54), "l": (200, 160, 104)}
    inner_m = np.zeros((H, W), bool)
    for j in range(Hp - 2, H):
        for i in range(W):
            e = ((i - cx) / rx) ** 2 + ((j - ycen) / (ry + 0.5)) ** 2
            if e <= 1.0:
                inner = ((i - cx) / (rx - 1.6)) ** 2 + ((j - ycen) / (ry - 0.6)) ** 2
                if inner < 1.0 and j >= ycen - ry + 1:
                    inner_m[j, i] = True
                    p[j, i, :3] = mix((26, 10, 8), (130, 60, 24), 0.55 * L)
                elif j <= ycen:
                    p[j, i, :3] = bb["d"]
                else:
                    p[j, i, :3] = bb["l"] if j <= ycen + 1 else (bb["m"] if j < ycen + ry else bb["d"])
                p[j, i, 3] = 255
    mm = p[..., 3] > 0
    rb = _bord(mm)
    rb[:Hp, :] = False
    p[rb, :3] = bb["o"]
    ij = list(zip(*np.nonzero(inner_m)))
    if ij:
        js = [a for a, _ in ij]
        is_ = [b_ for _, b_ in ij]
        j0, j1, i0, i1 = min(js), max(js), min(is_), max(is_)
        for t in np.linspace(0, 1, 40):
            for (a0, a1) in ((i0, i1), (i1, i0)):
                i = int(round(a0 + (a1 - a0) * t))
                j = int(round(j0 + (j1 - j0) * t))
                if inner_m[j, i]:
                    p[j, i, :3] = (62, 38, 22)
    _LCACHE[key] = p
    return p


FLAMMES4 = {
    "grande": ([["b", "a"], ["c", "b"], ["c", "c"]], [["a", "b"], ["b", "c"], ["c", "c"]]),
    "moyenne": ([["b", "a"], ["c", "b"]], [["a", "b"], ["b", "c"]]),
    "braise": ([["a", "b"], ["b", "a"]], [["b", "a"], ["a", "b"]]),
}


def flamme4(W, H, flamme, image):
    p = np.zeros((H, W, 4), np.uint8)
    ry = 3
    Hp = H - ry - 3
    ycen = Hp + ry - 1
    cx = (W - 1) / 2
    spr = FLAMMES4[flamme][image]
    if flamme == "braise":
        fc = {"a": (150, 52, 24), "b": (226, 110, 46)}
    else:
        fc = {"a": (232, 116, 44), "b": (255, 176, 64), "c": (255, 241, 196)}
    fx0 = int(round(cx - 0.5))
    for jj, row in enumerate(spr):
        for ii, ch in enumerate(row):
            p[ycen - len(spr) + 1 + jj, fx0 + ii, :3] = fc[ch]
            p[ycen - len(spr) + 1 + jj, fx0 + ii, 3] = 255
    return p


def hitodama4(frame=0):
    """Hitodama 12×20 de la maquette tour 4 : tête ronde en bas, langue qui monte en virgule."""
    W, H = 12, 20
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    m = np.zeros((H, W), bool)
    hx, hy, hr = 5.5, 14.2, 4.3
    ph = 0.22 if frame else 0.0
    for t in np.linspace(0, 1, 60):
        px_ = hx + 2.6 * math.sin(t * math.pi * 1.05 + ph) - 0.6 * t - (0.6 if frame else 0.0) * t
        py_ = hy - 12.5 * t
        r = hr * (1 - t) ** 0.85 + 0.45
        d = np.sqrt((xx - px_) ** 2 + (yy - py_) ** 2)
        m |= d <= r
    dh = np.sqrt((xx - hx) ** 2 + (yy - hy) ** 2)
    lv = np.clip(1 - dh / 7.5, 0, 1)
    p = np.zeros((H, W, 4), np.uint8)
    pal = [hexc("5A64C0"), hexc("8C9CF0"), hexc("BFC8FF"), hexc("E6EAFF"), hexc("FFF6E8")]
    q = np.clip((lv * 5).astype(int), 0, 4)
    q[(yy < 9) & (q > 2)] = 2
    p[m] = np.array(pal, np.uint8)[q[m]]
    p[_bord(m)] = hexc("1E2450")
    p[(dh < 1.6) & m] = hexc("FFFFFF")
    return p


# ─────────── attributs ───────────
EMA_W, EMA_H = 92, 56


def ema4(img, x, y, stat, lumiere_k=0.6, survol=False, seed=0):
    W, H = EMA_W, EMA_H
    col = np.array(STATS[stat][3][:3], float)
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    toit = 9
    pente = (np.abs(xx - (W - 1) / 2) / ((W - 1) / 2)) * (toit - 1)
    mask = (yy >= pente) & (yy < H)
    mask[H - 1, 0] = mask[H - 1, W - 1] = False
    tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    kit.bois(tmp, 0, 0, W, H, "moyen", sens="h", seed=seed + 17, noeuds=0, biseau=False)
    p = np.asarray(tmp).astype(float).copy()
    p[..., :3] *= 0.60
    for (y_a, y_b, f) in ((toit + 3, toit + 8, 0.30), (toit + 8, toit + 13, 0.18), (toit + 13, toit + 18, 0.09)):
        band = mask & (yy >= y_a) & (yy < y_b)
        p[band, :3] = p[band, :3] * (1 - f * 0.4) + col * f * lumiere_k + 6 * f
    p = np.clip(p, 0, 255).astype(np.uint8)
    b = _bord(mask)
    p[b] = hexc("1A0E08")
    if survol:
        p[b] = hexc(R_OR[6])
        b2 = _bord(mask & ~b)
        p[b2] = hexc(R_OR[3])
    p[~mask] = 0
    Wt = W + 2
    roof = np.zeros((toit + 5, Wt, 4), np.uint8)
    Lq = [hexc(c) for c in R_LAQUE]
    for i in range(Wt):
        yc = abs(i - (Wt - 1) / 2) / ((Wt - 1) / 2) * (toit - 1)
        y_ = int(round(yc))
        for k in range(4):
            if 0 <= y_ + k < roof.shape[0]:
                roof[y_ + k, i] = Lq[(7, 5, 3, 1)[k]] if k < 3 else (8, 4, 6, 160)
        if i % 9 == 4 and 0 <= y_ + 1 < roof.shape[0]:
            roof[y_ + 1, i] = Lq[8]
    roof[0, Wt // 2 - 2:Wt // 2 + 2] = hexc(R_OR[6])
    roof[1, Wt // 2 - 1:Wt // 2 + 1] = hexc(R_OR[4])
    kit.ombre_portee(img, x, y, W, H, dx=2, dy=2, force=0.6, rayon=1, masque=mask)
    coller(img, p, x, y)
    coller(img, roof, x - 1, y - 1)


def cheville4(img, x, y, signe, etat="actif"):
    """Cheville 9×9 (maquette) : actif | inactif ; survol / presse : variantes de jeu."""
    m_ = np.ones((9, 9), bool)
    for (a, b) in ((0, 0), (0, 8), (8, 0), (8, 8)):
        m_[a, b] = False
    p = np.zeros((9, 9, 4), np.uint8)
    Lq = [hexc(c) for c in R_LAQUE]
    actif = etat != "inactif"
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
        p[0, 1:8] = BISEAU_SOMBRE; p[1:8, 0] = BISEAU_SOMBRE
        p[8, 1:8] = BISEAU_CLAIR; p[1:8, 8] = hexc(R_OR[4])
        o = hexc(R_OR[5])
        dy = 1
    else:
        p[m_] = Lq[2] if actif else Lq[1]
        p[1:4, 1:5] = Lq[4] if actif else Lq[2]
        p[1, 2:4] = Lq[6] if actif else Lq[3]
        p[0, 1:8] = BISEAU_CLAIR if actif else hexc(R_OR[3]); p[1:8, 0] = hexc(R_OR[4] if actif else R_OR[2])
        p[8, 1:8] = BISEAU_SOMBRE if actif else hexc(R_OR[1]); p[1:8, 8] = BISEAU_SOMBRE if actif else hexc(R_OR[1])
        o = hexc(R_OR[6]) if actif else hexc("4A3C34")
    p[4 + dy, 2:7] = o
    if actif:
        p[5 + dy, 3:7] = hexc(R_LAQUE[0])
    if signe == "+":
        p[2 + dy:7 + dy, 4] = o
    p[~m_] = 0
    kit.ombre_portee(img, x, y, 9, 9, dx=1, dy=1, force=0.45, rayon=0)
    coller(img, p, x, y)


def piquet(px, x, y0, y1):
    R = [hexc(c) for c in kit.R_BOIS["moyen"]]
    for yy in range(y0, y1):
        rect(px, x - 2, yy, 1, 1, R[1]); rect(px, x - 1, yy, 1, 1, R[4 if yy % 7 else 3])
        rect(px, x, yy, 1, 1, R[5]); rect(px, x + 1, yy, 1, 1, R[2])
    rect(px, x - 2, y0, 4, 1, R[6]); rect(px, x - 1, y0 - 1, 2, 1, R[5])
    rect(px, x - 1, y0 + 3, 2, 2, hexc(R_OR[5])); rect(px, x - 1, y0 + 3, 1, 1, hexc(R_OR[7]))
    kit.ombre_portee(px, x - 5, y1 - 1, 11, 2, dx=0, dy=0, force=0.6, rayon=1)


def mat_bambou(px, x, y0, y1, seed=0):
    for k, c in enumerate(("050304", "3A2428", "5A3A40", "2A1A1F", "140C0F", "050304")):
        rect(px, x - 1 + k, y0, 1, y1 - y0, hexc(c))
    rs = random.Random(seed)
    y = y0 + rs.randint(14, 30)
    while y < y1 - 14:
        rect(px, x, y, 4, 1, hexc("7A5058"))
        rect(px, x, y + 1, 4, 1, hexc("0A0608"))
        rect(px, x - 1, y, 1, 2, hexc("050304")); rect(px, x + 4, y, 1, 2, hexc("050304"))
        y += rs.choice((47, 61, 53, 71, 58))
    fa.lq(px, x - 1, y0 - 6, 6, 7, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=x)
    liseret4(px, x - 1, y0 - 6, 6, 7)
    rect(px, x + 1, y0 - 8, 2, 2, hexc(R_OR[5])); rect(px, x + 1, y0 - 9, 1, 1, hexc(R_OR[7]))
    rect(px, x - 3, y1, 10, 2, hexc("050304"))
    fa.lq(px, x - 2, y1 - 3, 8, 3, ["4A3518", "6A4E24", "8E6A30", "B08442"], seed=x + 1, reflet=False)


def soucoupe(px, cx, y):
    sx = int(cx) - 8
    kit.ombre_portee(px, sx, y, 16, 3, dx=0, dy=1, force=0.6, rayon=1)
    rect(px, sx, y, 16, 1, hexc(R_OR[6]))
    rect(px, sx + 1, y + 1, 14, 1, hexc(R_LAQUE[6]))
    rect(px, sx + 2, y + 2, 12, 1, hexc(R_LAQUE[2]))
    rect(px, sx, y, 1, 1, hexc(R_OR[3])); rect(px, sx + 15, y, 1, 1, hexc(R_OR[3]))
    rect(px, sx + 3, y, 3, 1, hexc(R_OR[8]))


def bouton4(px, x, y, etat="normal", kj=None, ton="noir", h=20, w=80):
    inactif = etat == "inactif"
    neutre = etat == "neutre"
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=2, force=0.6 if not inactif else 0.35, rayon=1)
    rect(px, x - 1, y - 1, w + 2, h + 2, PAL["fond"])
    if ton == "rouge" and not inactif:
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, "rouge", seed=w, intensite=1.6 if etat == "survol" else 1.2)
    elif ton == "rouge":
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, ["1E0A0D", "260C10", "2E0E13", "361117", "3E141A", "46171E"], seed=w,
              intensite=0.6)
    else:
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=w,
              intensite={"survol": 1.8, "normal": 1.1, "neutre": 0.8}.get(etat, 0.4))
    liseret4(px, x, y, w, h, terne=inactif or neutre)
    fa.ferrures3(px, x, y, w, h, terne=inactif or neutre)
    if kj:
        kc = {"inactif": hexc("4A3C38"), "neutre": hexc(R_OR[3])}.get(etat, OR_CLAIR if ton == "rouge" else OR)
        kj_px(px, x + 7 + 8, y + (h - 16) // 2, kj, kc, ombre=hexc("140608") if not inactif else None)


def meche4(px, x, y, etat):
    ICONES = {
        "vive": ["..o...", ".obo..", ".obco.", "obccbo", "obccbo", "oabbao", ".oaao.", "..oo.."],
        "vacillante": ["...o..", "..obo.", ".obo..", ".obco.", "obcbo.", "oabbao", ".oaao.", "..oo.."],
        "faible": ["......", "......", "......", "..o...", ".obo..", "oabao.", ".oaao.", "..oo.."],
    }
    pal = {"o": hexc("2A1208"), "a": hexc("B4501E"), "b": hexc("F2A548"), "c": hexc("FFF1C4")}
    if etat == "faible":
        pal = {"o": hexc("2A1208"), "a": hexc("7A3416"), "b": hexc("C0702E"), "c": hexc("E8B070")}
    for j, row in enumerate(ICONES[etat]):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(px, x + i, y + j, 1, 1, pal[ch])


def petit_noeud(px, x, y):
    rect(px, x - 1, y - 1, 3, 3, hexc("A42230"))
    rect(px, x - 1, y - 1, 1, 1, hexc("D04A4E")); rect(px, x + 1, y + 1, 1, 1, hexc("6E1220"))
    rect(px, x, y, 1, 1, hexc(R_OR[6]))


def cabochon_fond(px, x, y, w, h=10):
    kit.ombre_portee(px, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
    fa.lq(px, x + 1, y + 1, w - 2, h - 2, "noir", seed=x, intensite=1.2)
    rect(px, x + 1, y, w - 2, 1, hexc(R_OR[4])); rect(px, x + 1, y + h - 1, w - 2, 1, hexc(R_OR[2]))
    rect(px, x, y + 1, 1, h - 2, hexc(R_OR[3])); rect(px, x + w - 1, y + 1, 1, h - 2, hexc(R_OR[2]))


# ═════════════════════════════════════ FONDS 1280×540 ═════════════════════════════════════
def arc_positions(cx=480, pas=42, y_haut=120, fleche=52):
    out = []
    osc = [1, -1, 0, 1, -1, 0]
    for i in range(6):
        x = cx + (i - 2.5) * pas
        y = y_haut + fleche * ((x - cx) / (pas * 2.5)) ** 2 + osc[i]
        out.append((int(round(x)), int(round(y))))
    return out


ZONE_PERSO = (420, 240, 540, 460, 30)
ZONE_ENTETE = (322, 0, 638, 84, 10)
ZONE_FERMER = (898, 0, 944, 50, 10)
ZONE_PG = (PG_X - 8, 50, PG_X + PW + 8, 352, 12)
ZONE_PD = (PD_X - 8, 50, PD_X + PW + 8, 352, 12)
ZONE_BAS = (16, 476, 944, 540, 6)
LAN_X = [86, 184, 282, 678, 776, 874]


def _zones(onglet):
    pos = arc_positions()
    if onglet == "identite":
        z = [ZONE_PERSO, ZONE_ENTETE, ZONE_FERMER, ZONE_PG, ZONE_PD, ZONE_BAS]
        return z + [(x - 15, y - 24, x + 15, y + 24, 22) for (x, y) in pos]
    if onglet == "attributs":
        tops = [210, 250, 210, 210, 250, 210]
        z = [ZONE_PERSO, ZONE_ENTETE, ZONE_FERMER, (250, 84, 710, 100, 6), ZONE_BAS]
        z += [(LAN_X[i] - 17, tops[i], LAN_X[i] + 17, tops[i] + 52, 36) for i in range(6)]
        z += [(14, 80, 30, 430, 8), (932, 80, 948, 430, 8), (0, 180, 960, 212, 2)]
        return z
    if onglet == "techniques":
        z = [ZONE_PERSO, ZONE_ENTETE, ZONE_FERMER, ZONE_PG, (PD_X - 8, 50, PD_X + PW + 8, 430, 12), ZONE_BAS]
        return z + [(x - 15, y - 30, x + 15, y + 24, 22) for (x, y) in pos]
    z = [ZONE_PERSO, ZONE_ENTETE, ZONE_FERMER, ZONE_PG, ZONE_PD, ZONE_BAS, (330, 250, 630, 400, 10)]
    return z + [(x - 15, y - 24, x + 15, y + 24, 22) for (x, y) in pos]


FONDS = {"identite": (1, 32), "attributs": (7, 30), "techniques": (1, 30), "etat": (1, 28)}


def fond(onglet):
    seed, n = FONDS[onglet]
    bg = Image.new("RGBA", (1280, 540), PAL["fond"])
    fa.ciel(bg, seed, CIEL)
    halos = lanternes_lointaines4(bg, _zones(onglet), seed=seed + 4, n=n, ox=OX, cotes=5)
    brume4(bg, 250, 13, 0.09, seed + 2)
    brume4(bg, 332, 16, 0.11, seed + 3, couleur=(130, 110, 150))
    plancher4(bg, seed + 5, vpx=480.0 + OX, ox=OX)
    fa.vignette_px(bg, 0.5, cx=480 + OX, demi_l=480)
    for (hx, hy, r, col, f) in halos:
        halo_px1(bg, hx, hy, r, col, force=f, niveaux=1)
    rect(bg, 0, 0, 1280, 1, hexc(CIEL[0]))
    sauver(f"fond_{onglet}.png", bg, (1280, 540), quantifier=True)


# ═════════════════════════════════════ masques et lumières ═════════════════════════════════════
def masque(nom, rx, ry, niveaux, tramage=0.16, expo=2.0):
    q = fa.masque_lumiere(rx, ry, niveaux, tramage, expo)
    a = np.zeros(q.shape + (4,), np.uint8)
    a[..., :3] = 255
    a[..., 3] = np.round(q * 255).astype(np.uint8)
    sauver(nom, a, (int(2 * rx) + 2, int(2 * ry) + 2), garder_rgb=True)


def lumieres():
    masque("lum_village.png", 170, 180, 6, tramage=0.25, expo=1.35)
    masque("lum_coeur.png", 80, 100, 3)
    masque("lum_flaque_l.png", 150, 30, 4, tramage=0.3)
    masque("lum_buste.png", 160, 160, 5, tramage=0.25, expo=1.4)
    masque("lum_flaque_m.png", 150, 30, 3)
    masque("lum_nuit.png", 150, 160, 5, tramage=0.25, expo=1.35)
    masque("lum_flaque_etat.png", 150, 30, 4, tramage=0.3)

    # reflet_sol 7×90 : coulée blanche, 3 crans nets (100 / 60 / 30 %), bords à mi-force
    L = 90
    a = np.zeros((L, 7, 4), np.uint8)
    a[..., :3] = 255
    for k in range(L):
        t = k / L
        v = 1.0 if t < 0.34 else (0.6 if t < 0.67 else 0.3)
        a[k, 2:5, 3] = int(round(255 * v))
        a[k, 1, 3] = a[k, 5, 3] = int(round(255 * v * 0.4))
    sauver("reflet_sol.png", a, (7, 90), garder_rgb=True)

    yy, xx = np.mgrid[0:64, 0:64].astype(float)
    d = np.sqrt((xx + 0.5 - 32) ** 2 + (yy + 0.5 - 32) ** 2)
    cov = np.clip(31.5 - d + 0.5, 0, 1)
    a = np.zeros((64, 64, 4), np.uint8)
    a[..., :3] = 255
    a[..., 3] = np.round(cov * 255).astype(np.uint8)
    sauver("halo.png", a, (64, 64), garder_rgb=True)

    W, H = 64, 36
    yy, xx = np.mgrid[0:H, 0:W].astype(float) + 0.5
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    al = np.clip((d - 0.55) / 0.85, 0, 1) ** 1.6
    a = np.zeros((H, W, 4), np.uint8)
    a[..., :3] = hexc("0A0608")[:3]
    a[..., 3] = np.round(al * 255).astype(np.uint8)
    sauver("vignette_jeu.png", a, (64, 36), garder_rgb=True)

    # ombre_pieds 60×14 : ellipses 25×5 (30 %) et 15×3 (55 %), pieds au texel (30, 7)
    yy, xx = np.mgrid[0:14, 0:60].astype(float) + 0.5
    A = np.zeros((14, 60))
    for (rx, ry, a_) in ((25, 5, 0.30), (15, 3, 0.55)):
        e = np.sqrt(((xx - 30.0) / rx) ** 2 + ((yy - 7.0) / ry) ** 2)
        A = np.maximum(A, a_ * (e <= 1.0))
    a = np.zeros((14, 60, 4), np.uint8)
    a[..., 3] = np.round(A * 255).astype(np.uint8)
    sauver("ombre_pieds.png", a, (60, 14))


# ═════════════════════════════════════ en-tête, onglets, fermer, perches ═════════════════════════════════════
def entete_onglets_fermer():
    im = nouveau(320, 48)
    fa.entete_fixe(im, 160, w=300)
    sauver("entete.png", im, (320, 48))

    im = nouveau(78, 36)
    for i, etat in enumerate(("repos", "survol", "actif")):
        cell = nouveau(26, 36)
        ty = 8 + (2 if etat == "actif" else 0)
        fa.corde_v(cell, 13, 0, ty, "or", ep=1)
        fa.plaquette22(cell, 2, ty, etat, seed=i)
        kit.anneau_metal(cell, 13, ty, 1)
        im.paste(cell, (i * 26, 0))
    sauver("onglets.png", im, (78, 36))

    im = nouveau(52, 38)
    for i in range(2):
        cell = nouveau(26, 38)
        fa.corde_v(cell, 13, 0, 12, "rouge", ep=2)
        fa.plaquette22(cell, 2, 12, "fermer_survol" if i else "fermer")
        kit.anneau_metal(cell, 13, 12, 1)
        pictos.ico_px(cell, 13, 18, "fermer", CREME_KJ, ombre=hexc("050304"))
        im.paste(cell, (i * 26, 0))
    sauver("fermer.png", im, (52, 38))

    im = nouveau(192, 16)
    for o, ic in enumerate(("identite", "attributs", "techniques", "etat")):
        for e in range(3):
            col = OR_CLAIR if e == 2 else (OR if e == 1 else hexc(R_OR[4]))
            pictos.ico_milieu(im, (o * 3 + e) * 16 + 8, 8, ic, col, ombre=hexc("1A0608") if e == 2 else hexc("050304"))
    sauver("onglets_pictos.png", im, (192, 16))

    im = nouveau(300, 16)
    g = nouveau(300 + 2, 8)
    fa.perche(g, 0, 300, 1, "d")
    im.alpha_composite(g.crop((0, 0, 300, 8)), (0, 0))
    d = nouveau(300, 8)
    fa.perche(d, 0, 300, 1, "g")
    im.alpha_composite(d, (0, 8))
    sauver("perche.png", im, (300, 16))


# ═════════════════════════════════════ panneaux ═════════════════════════════════════
def panneau_tex(nom, w, h, titre, gland, seed, seed_bandeau=None):
    im = nouveau(w + 8, 84 + h + 28)
    fa.planche(im, 4, 84, w, h, titre, gland=gland, seed=seed, seed_bandeau=seed_bandeau, y_perche=Y_PERCHE)
    if titre is None:                       # bandeau sans titre (le titre est du texte)
        pass
    bande = np.asarray(im)[84 + 100:84 + h - 60, :, :3].reshape(-1, 3)
    for interdit in (fa.BOIS_ECLAT, hexc("FFF2CC"), hexc("E4B870")):
        assert not (bande == np.array(interdit[:3])).all(axis=1).any(), f"{nom} : ornement dans la bande de coupe"
    sauver(nom, im, (w + 8, 84 + h + 28))


def panneaux():
    panneau_tex("panneau_registre.png", 240, 262, "REGISTRE", "bd", 3, seed_bandeau=24)
    panneau_tex("panneau_lignee.png", 240, 244, "LIGNEE ET SERVICE", "bg", 4, seed_bandeau=696)
    panneau_tex("panneau_detail.png", 240, 278, None, "bg", 22, seed_bandeau=24)
    panneau_tex("panneau_techniques.png", 240, 336, "TECHNIQUES", "bd", 21, seed_bandeau=696)
    panneau_tex("panneau_corps.png", 240, 184, "CORPS", "bd", 41, seed_bandeau=24)
    panneau_tex("panneau_ressens.png", 240, 184, "CE QUE TU RESSENS", "bg", 42, seed_bandeau=696)
    panneau_tex("panneau_ressens_grand.png", 240, 272, "CE QUE TU RESSENS", "bg", 42, seed_bandeau=696)

    # liste_bas 248×28 : fondu de défilement, fond plein, filets, liseré, kanagu, flèche
    w, h = 240, 336
    lay = nouveau(248, 28)
    x0, y0 = 4, -(h - 22)
    y_fin = y0 + h - 10
    fy0 = y_fin - 12
    for k, a in enumerate((0.35, 0.35, 0.35, 0.35, 0.6, 0.6, 0.6, 0.6, 0.82, 0.82, 0.82, 0.82)):
        rect(lay, x0 + 4, fy0 + k, w - 8, 1, hexc(R_LAQUE[2])[:3] + (int(255 * a),))
    rect(lay, x0 + 1, y_fin, w - 2, h - (y_fin - y0) - 1, hexc(R_LAQUE[2]))
    rect(lay, x0 + 1, y0 + h - 2, w - 2, 1, hexc(R_LAQUE[0]))
    rect(lay, x0 + 3, y0 + h - 4, w - 6, 1, hexc("6A1420"))
    rect(lay, x0 + 3, fy0, 1, y0 + h - 3 - fy0, hexc("6A1420"))
    rect(lay, x0 + w - 4, fy0, 1, y0 + h - 3 - fy0, hexc("6A1420"))
    fa.liseret(lay, x0, y0, w, h)
    fa.kanagus(lay, x0, y0, w, h)
    rect(lay, x0 + w // 2 - 2, y0 + h - 7, 5, 1, hexc(R_OR[4]))
    rect(lay, x0 + w // 2 - 1, y0 + h - 6, 3, 1, hexc(R_OR[4]))
    rect(lay, x0 + w // 2, y0 + h - 5, 1, 1, hexc(R_OR[4]))
    sauver("liste_bas.png", lay, (248, 28))

    im = nouveau(226, 44)
    fa.panneau_opacite(im, 4, 2, 218, 37, 0.94, seed=11)
    a = np.asarray(im).copy()
    a[17:24] = a[16]
    sauver("infobulle.png", a, (226, 44))


# ═════════════════════════════════════ lanternes, flammes, hitodama ═════════════════════════════════════
def lanternes():
    for nom, (W, H) in (("lanternes_s.png", (30, 48)), ("lanternes_l.png", (34, 52))):
        im = nouveau(6 * W, 6 * H)
        for st in range(6):
            for c, L in enumerate(CRANS_L):
                kit.coller(im, lanterne_sprite4(st, W, H, L, False, seed=st), c * W, st * H)
        sauver(nom, im, (6 * W, 6 * H))
    for nom, (W, H) in (("flammes_s.png", (30, 48)), ("flammes_l.png", (34, 52))):
        im = nouveau(6 * W, H)
        k = 0
        for fl in ("grande", "moyenne", "braise"):
            for image in (0, 1):
                kit.coller(im, flamme4(W, H, fl, image), k * W, 0)
                k += 1
        sauver(nom, im, (6 * W, H))
    spr = lanterne_sprite4(0, 34, 52, 1.0, False)
    b = _bord(spr[..., 3] > 0)
    out = np.zeros((52, 34, 4), np.uint8)
    out[b] = OR
    sauver("lanterne_l_contour.png", out, (34, 52))
    im = nouveau(48, 20)
    for f in (0, 1):
        spr = Image.fromarray(hitodama4(f), "RGBA")
        kit.coller(im, spr, f * 12, 0)
        kit.coller(im, spr.resize((9, 15), Image.NEAREST), 24 + f * 12, 0)
    sauver("hitodama.png", im, (48, 20))


# ═════════════════════════════════════ ATTRIBUTS ═════════════════════════════════════
def attributs():
    im = nouveau(576, 168)
    for st in range(6):
        for r, survol in enumerate((False, True)):
            cell = nouveau(96, 84)
            piquet(cell, 48, 2, 80)
            ema4(cell, 2, 12, st, lumiere_k=1.0 if survol else 0.9, survol=survol, seed=st)
            fa.corde_v(cell, 47, 8, 10, "rouge", ep=2)
            kit.anneau_metal(cell, 48, 10, 1)
            arcade(cell, 2 + 9, 12 + 16, STATS[st][1], 10, CREME, ombre=True)
            im.paste(cell, (st * 96, r * 84))
    sauver("ema.png", im, (576, 168))

    im = nouveau(80, 10)
    for k, (sig, etat) in enumerate((("-", "actif"), ("-", "inactif"), ("+", "actif"), ("+", "inactif"),
                                     ("-", "survol"), ("+", "survol"), ("-", "presse"), ("+", "presse"))):
        cheville4(im, k * 10, 0, sig, etat)
    sauver("chevilles.png", im, (80, 10))

    im = nouveau(24, 348)
    mat_bambou(im, 4, 9, 345, seed=11)
    mat_bambou(im, 16, 9, 345, seed=12)
    sauver("mat.png", im, (24, 348))

    im = nouveau(64, 26)
    R = [hexc(c) for c in kit.R_CORDE["or"]]
    for k in range(48):
        rect(im, k, 0, 1, 1, (R[3], R[2], R[2])[k % 3])
        rect(im, k, 1, 1, 1, (R[1], R[3], R[1])[k % 3])
    agemaki4(im, 52, 4, "or")
    fa.corde_v(im, 13, 10, 14, "or", ep=1)
    plaquette4(im, 0, 14, 30, 11, "noir", seed=3)
    sauver("seuil.png", im, (64, 26))

    im = nouveau(368, 36)
    bx, by, bw, bh = 2, 2, 360, 28
    kit.ombre_portee(im, bx, by, bw, bh, dx=2, dy=3, force=0.6, rayon=2)
    rect(im, bx - 1, by - 1, bw + 2, bh + 2, PAL["fond"])
    fa.lq(im, bx + 1, by + 1, bw - 2, bh - 2, "noir", seed=12, intensite=0.8)
    liseret4(im, bx, by, bw, bh)
    fa.kanagus(im, bx, by, bw, bh)
    sauver("barre.png", im, (368, 36))

    im = nouveau(348, 104)
    for k, etat in enumerate(("neutre", "survol", "inactif", "arme")):
        c = nouveau(87, 26)
        if etat == "arme":
            bouton4(c, 1, 1, "actif", ton="rouge", w=82)
        else:
            bouton4(c, 1, 1, etat, w=82)
        im.paste(c, (k * 87, 0))
    for k, etat in enumerate(("normal", "survol", "inactif")):
        c = nouveau(75, 26)
        bouton4(c, 1, 1, etat, w=70)
        im.paste(c, (k * 75, 26))
    c = nouveau(99, 26)
    fa.lq(c, 1, 1, 94, 20, "rouge", seed=5, intensite=1.0)
    liseret4(c, 1, 1, 94, 20)
    im.paste(c, (0, 52))
    for k, etat in enumerate(("actif", "inactif", "survol")):
        c = nouveau(87, 26)
        bouton4(c, 1, 1, etat, ton="rouge", w=82)
        im.paste(c, (k * 87, 78))
    sauver("boutons.png", im, (348, 104))


# ═════════════════════════════════════ petites pièces ═════════════════════════════════════
def pieces():
    im = nouveau(80, 8)
    fa.anneau_or(im, 3, 3)
    fa.perle_or(im, 8 + 3, 3)
    nx, ny = 16, 0
    rect(im, nx, ny, 5, 5, hexc(R_OR[5]))
    rect(im, nx, ny, 5, 1, BISEAU_CLAIR); rect(im, nx, ny + 4, 5, 1, BISEAU_SOMBRE)
    rect(im, nx + 1, ny + 1, 3, 3, hexc("C01E35"))
    rect(im, nx + 1, ny + 1, 1, 1, hexc("E26474"))
    kit.anneau_metal(im, 24 + 3, 3, 1)
    losange_or(im, 32, 0)
    tampon_palier(im, 40, 2, plein=True)
    tampon_palier(im, 48, 2, plein=False)
    petit_noeud(im, 57, 1)
    soucoupe(im, 72, 0)
    sauver("ornements.png", im, (80, 8))

    im = nouveau(228, 24)
    bx, by, bw, bh = 9, 0, 218, 22
    fa.lq(im, bx, by, bw, bh, ["2A070D", "3A0A12", "4E0D17", "63111D"], seed=4)
    rect(im, bx, by, bw, 1, hexc(R_OR[5]))
    rect(im, bx, by + 1, bw, 1, hexc("120608"))
    rect(im, bx, by + bh - 1, bw, 1, hexc(R_OR[4]))
    agemaki4(im, 5, by + 4)
    fa.gland3(im, 5, by + 9, 11)
    sauver("ligne_choisie.png", im, (228, 24))

    im = nouveau(448, 44)
    for i, sv in enumerate((False, True)):
        c = nouveau(224, 44)
        x, y, w, h = 1, 1, 218, 38
        kit.ombre_portee(c, x, y, w, h, dx=1, dy=2, force=0.5, rayon=1)
        fa.lq(c, x, y, w, h, "noir", seed=717, intensite=1.6 if sv else 1.2)
        if sv:
            liseret4(c, x, y, w, h)
            fa.kanagus(c, x, y, w, h)
            rect(c, x + 3, y + 3, 2, h - 6, fa.ROUGE)
        else:
            rect(c, x, y, w, 1, hexc(R_OR[3])); rect(c, x, y + h - 1, w, 1, hexc(R_OR[1]))
            rect(c, x, y, 1, h, hexc(R_OR[3])); rect(c, x + w - 1, y, 1, h, hexc(R_OR[1]))
        im.paste(c, (i * 224, 0))
    sauver("carte_blessure.png", im, (448, 44))

    im = nouveau(18, 8)
    for k, e in enumerate(("vive", "vacillante", "faible")):
        meche4(im, k * 6, 0, e)
    sauver("meches.png", im, (18, 8))

    im = nouveau(32, 2)
    for k in range(32):
        rect(im, k, 0, 1, 1, hexc((R_OR[5], R_OR[3], "A42230", R_OR[3])[k % 4]))
        rect(im, k, 1, 1, 1, (6, 3, 4, 120))
    sauver("kumihimo.png", im, (32, 2))

    im = nouveau(42, 12)
    cabochon_fond(im, 0, 0, 40, 10)
    sauver("cabochon.png", im, (42, 12))


# ═════════════════════════════════════ sceaux (résolution GUI) ═════════════════════════════════════
BLESSURES = ["contusion", "brulure", "fracture", "plaie", "infection"]


def hanko_atlas():
    """Sceaux de la fiche, sans aucun kanji (18/10, DA §3) : rangs D…S (lettres), sceau papier « de base » à
    losange, pictos de blessure. Les autres cellules restent vides (contrat inchangé)."""
    im = nouveau(240, 100)

    def c(col, row):
        return col * 20 + 9, row * 20 + 9

    for i, r in enumerate(["D", "C", "B", "A", "S"]):
        sceau_rang4(im, *c(6 + i, 3), r)
    sceau_rang4(im, *c(11, 3), None)
    for i, nom in enumerate(BLESSURES):
        hanko_px(im, *c(i, 4), pictos.encre_px(18, nom), 18, R_VERM, seed=i + 3)
    sauver("hanko.png", im, (240, 100))


def emblemes_k(k, t):
    im = nouveau(6 * t * k, t * k)
    for i, v in enumerate(("konoha", "suna", "kiri", "kumo", "iwa", "ame")):
        src = Image.open(os.path.join(TEX_ESC, f"village_{v}.png")).convert("RGBA")
        r = np.asarray(src.resize((t * k, t * k), Image.BOX)).astype(float)
        lum = r[..., :3].mean(axis=2) / 255.0
        a = np.zeros(r.shape, np.uint8)
        g = np.round(255 * (0.55 + 0.45 * lum)).astype(np.uint8)
        on = r[..., 3] > 110
        a[on, 0] = g[on]; a[on, 1] = g[on]; a[on, 2] = g[on]; a[on, 3] = 255
        kit.coller(im, a, i * t * k, 0)
    return im


def petits_k(k):
    im = nouveau(12 * k, 4 * k)
    c = 2 * k
    kit.disque_lisse(im, c, c, 1.5 * k, fa.OR)
    kit.disque_lisse(im, c - 0.4 * k, c - 0.5 * k, 0.6 * k, fa.OR_CLAIR)
    kit.anneau_lisse(im, 4 * k + c, c, 1.5 * k, 0.6 * k, hexc(R_OR[2]))
    kit.disque_lisse(im, 8 * k + c, c, 0.9 * k, fa.OR_ETEINT)
    return im


def atlas_ecran():
    for k in (1, 2, 3):
        sauver(f"emblemes_{k}.png", emblemes_k(k, 12), (72 * k, 12 * k))
        sauver(f"emblemes16_{k}.png", emblemes_k(k, 16), (96 * k, 16 * k))
    p2, p3 = petits_k(2), petits_k(3)
    sauver("petits_2.png", p2, (24, 8))
    sauver("petits_3.png", p3, (36, 12))
    sauver("petits_1.png", p2.resize((12, 4), Image.BOX), (12, 4))


# ═════════════════════════════════════ principal ═════════════════════════════════════
def main():
    os.makedirs(SORTIE, exist_ok=True)
    for o in FONDS:
        fond(o)
    lumieres()
    entete_onglets_fermer()
    panneaux()
    lanternes()
    attributs()
    pieces()
    hanko_atlas()
    atlas_ecran()
    total = sum(e[2] for e in ECRITS)
    print(f"{'fichier':<28} {'taille':>10} {'octets':>9}  mode")
    for nom, (w, h), o, mode in ECRITS:
        print(f"{nom:<28} {f'{w}x{h}':>10} {o:>9}  {mode}")
    print(f"{len(ECRITS)} fichiers, {total} octets ({total / 1024:.0f} Ko) dans {os.path.normpath(SORTIE)}")
    attendus = {e[0] for e in ECRITS}
    presents = {f for f in os.listdir(SORTIE) if f.endswith(".png")}
    obsoletes = sorted(presents - attendus)
    for f in obsoletes:                      # anciennes sorties de ce générateur
        os.remove(os.path.join(SORTIE, f))
    if obsoletes:
        print("supprimés (obsolètes) :", ", ".join(obsoletes))
    assert total < BUDGET, f"budget dépassé : {total} octets (max {BUDGET})"


if __name__ == "__main__":
    main()
