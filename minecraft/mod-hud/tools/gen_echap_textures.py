# -*- coding: utf-8 -*-
"""
gen_echap_textures.py — textures du menu Échap « Tokonoma » au niveau de la fiche shinobi (DA §4.1, §6).

Signature conservée : la pièce traditionnelle de nuit — fenêtre ronde sur le village, menu en plaques laquées
suspendues, poutre aux kamon des cinq villages et, dans l'alcôve (tokonoma), l'Échoppe (devanture noren et
chōchin), le Stream (kamishibai), le Dev blog (kawaraban) et les Récompenses (omamori). Ouverture par deux fusuma.

Espace : la scène fait 640×360 unités ; 1 texel = 1 unité ; le code l'affiche à un multiple entier k de pixels
écran (k = 3 en 1080p, 2 en 1366×768, 4 en 1440p) : aucun filtrage, une seule grille. Tout le texte est posé par le
code (Inter et ArcadePix à la résolution de l'écran) : aucune lettre n'est cuite ici.

Usage (depuis minecraft/mod-hud) :  python tools/gen_echap_textures.py

Contrat (coordonnées = unités de la scène) :
- piece.png 864×528 : la pièce entière, décor fixe compris ; la scène 640×360 y est posée en (MX, MY) = (112, 84)
  (ciel de la fenêtre en (51, 28) de la scène, devanture de l'Échoppe (236, 60, 177×121), kamishibai (429, 60),
  porte-amulettes (429, 197, 177×121), plaque STREAM (495, 54, 44×12), cordons du menu). Autour, la pièce se
  prolonge (mur, poutre, piliers, plafond, plancher) qui s'assombrit vers les bords : le code agrandit la scène au
  plus grand k entier dont la zone utile (600×332) tient dans la fenêtre, et ce prolongement remplit l'écran
  quel que soit le format (16:10, 21:9, fenêtre non maximisée) — aucune bande vide.
- fenetre.png 224×112 : cadre rond + croisillons (0, 0) ; lumières du village (112, 0) — posés en (51, 28).
- plaques.png 144×60 : 3 rangées de 20 (repos, survol, survol « danger ») ; plaque 132×15 en (8, 2) de la
  cellule, pointe d'or (survol) en (0, 6). Cellule posée en (x − 8, y − 2).
- kamons.png 110×78 : 5 colonnes (konoha suna kiri kumo iwa) × 3 rangées de 22 (repos, survol, le mien) ;
  disque 20×20 en (1, 1) ; posé en (x − 2, y − 2) pour une plaque logique 18×18 en (x, y). Rangée y 66 :
  petits emblèmes blancs 10×10 en (22·i, 66) (teints par le code).
- chochin.png 40×32 : 2 images 20×32 (repos, allumée) ; l'attache est au texel (10, 0).
- omamori.png 120×40 : 5 amulettes 24×40 (couleurs des activations) ; boucle au texel (12, 0).
- kawaraban.png 134×122 : auvent, poteaux, feuille de washi (texte : (13..121, 20..118)).
- reseaux.png 64×32 : Discord, X, YouTube, site (16×16) ; rangée 0 repos, rangée 1 survol.
- pieces.png 128×48 : cabochon 42×12 (0, 0) ; flèches < > 7×9 repos (44, 0) (52, 0) survol (60, 0) (68, 0) ;
  pièce RBCoin 10×10 (78, 0) ; badges LIVE 44×11 (0, 14) et HORS LIGNE (46, 14) (coupe horizontale, capD 4) ;
  pastille pulsante 3×3 (92, 14) ; pièce de ryo 6×6 (98, 14) ; filet or 60×1 (0, 28).
- infobulle.png 176×80 : washi 168×72 en (2, 2) (coupe, capD = capB = 10).
- halo.png 48×48 : halo en crans pleins (blanc, l'alpha porte le niveau), teint au blit.
- fusuma_l.png / fusuma_r.png 320×360 : portes coulissantes de l'ouverture.

Déterminisme : graines fixes ; deux exécutions écrivent les mêmes octets.
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, kit, hexc, rect, coller, R_OR, R_LAQUE, R_LAQUE_ROUGE, nouveau  # noqa: E402

S = ea.Sortie("echap", budget=900_000)
W, H = 640, 360
MX, MY = 112, 84                       # marge du prolongement de la pièce autour de la scène
EW, EH = W + 2 * MX, H + 2 * MY
TEX_ESC = os.path.join(ea.TEX, "esc")

# ───────────────────────── rampes de la pièce ─────────────────────────
# Enduit de terre (tsuchikabe) : ocre chaud, ombres pourpres (hue shifting du kit).
R_ENDUIT = ["34262A", "463232", "5C4436", "745840", "8C6E4E", "A4865E", "B89A6C", "C8AA7A", "D6BA88", "E2C898"]
R_ALCOVE = ["261C24", "34262A", "463232", "5C4436", "705440", "86684C", "9A7C5C", "AE906A", "BEA076"]
R_PLANCHER = ["120507", "1A070A", "22090D", "2C0C11", "380F15", "46131A", "561820", "6A1E27"]
R_INDIGO_N = ["0C1020", "121A30", "1A2442", "223056", "2C3C68", "3A4C7C", "4C5E90", "6476A0"]
CIEL_F = ["0A0812", "0D0A18", "110C1E", "150F24", "1A112A", "20142E", "281832", "301C36"]


def champ(w, h, seed, base=0.6, gros=0.10, fin=0.05, cellule=30):
    v = base + (kit.bruit(w, h, cellule, seed, 3) - 0.5) * gros
    v += (kit.bruit(w, h, 3, seed + 1, 1) - 0.5) * fin
    return v


def peindre(im, x, y, v, R, tramage=0.35):
    idx = kit.quantifier(np.clip(v, 0, 0.999), len(R), x, y, tramage=tramage)
    coller(im, kit._rampe_vers_rgba(R, idx), x, y)
    return idx


def susa(im, x, y, w, h, R, seed, n=None, masque=None):
    """Brins de paille de l'enduit : traits de 2 à 4 texels, un cran plus clairs ou plus sombres."""
    rs = random.Random(seed)
    pal = [np.array(hexc(c)) for c in R]
    a = np.asarray(im).copy()
    n = n or (w * h) // 110
    for _ in range(n):
        fx, fy = rs.uniform(x, x + w), rs.uniform(y, y + h)
        ang = rs.uniform(0, math.pi)
        ln = rs.randint(2, 4)
        clair = rs.random() < 0.45
        for _k in range(ln):
            ix, iy = int(fx), int(fy)
            if x <= ix < x + w and y <= iy < y + h and (masque is None or masque[iy - y, ix - x]):
                c = a[iy, ix, :3]
                d = [np.abs(p[:3] - c).sum() for p in pal]
                j = int(np.argmin(d))
                j = min(len(pal) - 1, j + 1) if clair else max(0, j - 1)
                a[iy, ix, :3] = pal[j][:3]
            fx += math.cos(ang)
            fy += math.sin(ang)
    im.paste(Image.fromarray(a, "RGBA"))


def ao_bande(im, x, y, w, h, cote, prof, force):
    """Occlusion en paliers d'1 texel le long d'un bord (cote : 'h' haut, 'b' bas, 'g', 'd')."""
    for k in range(prof):
        f = force * (1 - k / prof)
        if cote == "h":
            fa.assombrir(im, x, y + k, np.full((1, w), f))
        elif cote == "b":
            fa.assombrir(im, x, y + h - 1 - k, np.full((1, w), f))
        elif cote == "g":
            fa.assombrir(im, x + k, y, np.full((h, 1), f))
        else:
            fa.assombrir(im, x + w - 1 - k, y, np.full((h, 1), f))


def pilier(im, x0, x1, y0, y1, seed):
    w = x1 - x0
    kit.bois(im, x0, y0, w, y1 - y0, "moyen", sens="v", seed=seed, noeuds=1)
    rect(im, x0, y0, 1, y1 - y0, hexc("A96B3F"))
    rect(im, x1 - 2, y0, 2, y1 - y0, hexc("2C170C"))
    rect(im, x1 - 3, y0, 1, y1 - y0, hexc("3B2012"))
    # bague de laiton au raccord de la poutre
    fa.lq(im, x0, 16, w, 4, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=seed, reflet=False)
    rect(im, x0, 16, w, 1, hexc(R_OR[6]))
    rect(im, x0, 19, w, 1, hexc(R_OR[2]))


# ═════════════════════════════════════ fenêtre ═════════════════════════════════════
WX, WY, WS = 51, 28, 112
WC = (WS - 1) / 2


def disque_masque(s, r=None, cx=None, cy=None):
    r = (s / 2) if r is None else r
    cx = (s - 1) / 2 if cx is None else cx
    cy = (s - 1) / 2 if cy is None else cy
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r


def ciel_fenetre():
    """Nuit sur le village, vue par la fenêtre ronde : ciel en crans, lune, monts, toits ; les fenêtres
    allumées sont un calque à part (scintillement par le code)."""
    s = WS
    im = nouveau(s, s)
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    v = np.clip(yy / (s * 0.82), 0, 1) ** 0.9 + (kit.bruit(s, s, 40, 3, 2, etire=(3, 1)) - 0.5) * 0.10
    idx = kit.quantifier(np.clip(v, 0, 0.999), len(CIEL_F), WX, WY, tramage=0.12)
    coller(im, kit._rampe_vers_rgba(CIEL_F, idx), 0, 0)
    # lune (haut droit) : disque, terminateur, cratères
    mx, my, mr = 78, 30, 9
    md = (xx - mx) ** 2 + (yy - my) ** 2
    lune = md <= mr * mr
    R_LUNE = ["8A8070", "B8AE96", "D8CEB4", "EDE4CC", "FBF4E0"]
    lv = 0.62 - ((xx - mx) * 0.7 + (yy - my) * 0.5) / (mr * 2.0)
    lv += (kit.bruit(s, s, 3, 9, 1) - 0.5) * 0.12
    for (cx_, cy_, cr) in ((75, 27, 2.2), (81, 33, 1.7), (77, 35, 1.1), (83, 26, 1.0)):
        lv -= 0.22 * (((xx - cx_) ** 2 + (yy - cy_) ** 2) <= cr * cr)
    li = kit.quantifier(np.clip(lv, 0, 0.999), len(R_LUNE), WX, WY, tramage=0.25)
    p = kit._rampe_vers_rgba(R_LUNE, li, lune)
    coller(im, p, 0, 0)
    # halo de lune en anneaux pleins (fixe ; le code ajoute une respiration)
    fa.halo_anneaux_px(im, mx, my, [(mr + 2, 0.10), (mr + 6, 0.06), (mr + 12, 0.04)], (190, 170, 220))
    # étoiles fixes (les scintillantes sont posées par le code)
    rs = random.Random(17)
    for _ in range(26):
        x, y = rs.randint(6, s - 6), rs.randint(6, 62)
        if (x - mx) ** 2 + (y - my) ** 2 < (mr + 8) ** 2:
            continue
        c = rs.choice(("6A6488", "8C86A8", "B4B0CC"))
        rect(im, x, y, 1, 1, hexc(c))
    # monts lointains (deux plans) puis toits du village
    def crete(y0, amp, cell, seed):
        n = kit.bruit(s, 1, cell, seed, 2)[0]
        return [int(y0 - amp * n[i]) for i in range(s)]
    for (y0, amp, cell, seed, R) in ((78, 16, 22, 5, ["1E1830", "241C38"]), (88, 12, 14, 6, ["171226", "1C1630"])):
        top = crete(y0, amp, cell, seed)
        for i in range(s):
            for j in range(top[i], s):
                im.putpixel((i, j), hexc(R[1] if j == top[i] else R[0]))
    toits = nouveau(s, s)
    rs = random.Random(23)
    x = -4
    while x < s:
        bw = rs.randint(9, 16)
        by = rs.randint(86, 95)
        roof = rs.choice((2, 3))
        rect(toits, x, by, bw, s - by, hexc("0F0B16"))
        for k in range(roof + 1):
            rect(toits, x - roof + k, by - k, bw + 2 * (roof - k), 1, hexc("1A1424" if k == roof else "0F0B16"))
        rect(toits, x - roof, by, bw + 2 * roof, 1, hexc("261C30"))
        x += bw + rs.randint(1, 4)
    # pagode (tour du Hokage) à gauche
    for k, (w_, h_) in enumerate(((14, 7), (11, 6), (8, 6))):
        bx = 28 - w_ // 2
        by = 82 - k * 7
        rect(toits, bx, by, w_, h_, hexc("100C18"))
        rect(toits, bx - 2, by, w_ + 4, 1, hexc("2A2034"))
    im.alpha_composite(toits)
    rect(im, 0, 104, s, s - 104, hexc("0C0912"))
    m = disque_masque(s, WS / 2 - 0.5)
    a = np.asarray(im).copy()
    a[~m] = 0
    return Image.fromarray(a, "RGBA"), toits


def lumieres_fenetre(toits):
    s = WS
    im = nouveau(s, s)
    rs = random.Random(31)
    a = np.asarray(toits)
    for _ in range(90):
        x, y = rs.randint(3, s - 4), rs.randint(84, 106)
        if a[y, x, 3] == 0 or a[y - 1, x, 3] == 0:
            continue
        c = rs.choice(("F2A548", "E8742C", "FFD08A", "F2A548"))
        rect(im, x, y, 1, 1, hexc(c))
        if rs.random() < 0.3:
            rect(im, x + 1, y, 1, 1, hexc("B4501E"))
    m = disque_masque(s, WS / 2 - 0.5)
    b = np.asarray(im).copy()
    b[~m] = 0
    return Image.fromarray(b, "RGBA")


def cadre_fenetre():
    """Cadre rond de bois laqué + croisillons (shōji) et liseré or."""
    s = WS
    im = nouveau(s, s)
    yy, xx = np.mgrid[0:s, 0:s].astype(float)
    d = np.sqrt((xx - WC) ** 2 + (yy - WC) ** 2)
    R_CADRE = ["1E100A", "2C170E", "3B2012", "4B2A17", "5C351D", "6E4124", "814E2C"]
    anneau = (d <= s / 2 - 0.5) & (d >= s / 2 - 7.5)
    ang = np.arctan2(yy - WC, xx - WC)
    v = 0.45 - 0.30 * np.sin(ang + 0.8) + (kit.bruit(s, s, 3, 4, 1, etire=(1, 4)) - 0.5) * 0.18
    idx = kit.quantifier(np.clip(v, 0, 0.999), len(R_CADRE), WX, WY, tramage=0.3)
    coller(im, kit._rampe_vers_rgba(R_CADRE, idx, anneau), 0, 0)
    a = np.asarray(im).copy()
    bord_ext = (d <= s / 2 - 0.5) & (d > s / 2 - 1.5)
    bord_int = (d < s / 2 - 6.5) & (d >= s / 2 - 7.5)
    a[bord_ext & (yy < WC)] = hexc("0A0506")
    a[bord_ext & (yy >= WC)] = hexc("0A0506")
    or_ = (d < s / 2 - 7.5) & (d >= s / 2 - 8.5)
    clair = or_ & (np.sin(ang + 0.8) < 0)
    a[or_] = hexc(R_OR[3])
    a[clair] = hexc(R_OR[6])
    a[bord_int] = hexc("140A06")
    im = Image.fromarray(a, "RGBA")
    # croisillons verticaux et une traverse (kumiko)
    inner = d < s / 2 - 8
    lat = nouveau(s, s)
    for k in (-1, 0, 1):
        x = int(round(WC + k * 28))
        rect(lat, x - 1, 0, 3, s, hexc("3B2012"))
        rect(lat, x - 1, 0, 1, s, hexc("5C351D"))
        rect(lat, x + 1, 0, 1, s, hexc("1E100A"))
    rect(lat, 0, 66, s, 2, hexc("3B2012"))
    rect(lat, 0, 66, s, 1, hexc("5C351D"))
    la = np.asarray(lat).copy()
    la[~inner] = 0
    im.alpha_composite(Image.fromarray(la, "RGBA"))
    # ombre intérieure du cadre sur le ciel (paliers)
    sh = nouveau(s, s)
    sa = np.zeros((s, s, 4), np.uint8)
    for r_, al in ((s / 2 - 9.5, 120), (s / 2 - 10.5, 60)):
        ring = (d < r_ + 1) & (d >= r_) & (yy < WC + 20)
        sa[ring] = (6, 3, 4, al)
    sh = Image.fromarray(sa, "RGBA")
    out = nouveau(s, s)
    out.alpha_composite(sh)
    out.alpha_composite(im)
    return out


# ═════════════════════════════════════ la pièce ═════════════════════════════════════
def piece(ciel):
    im = nouveau(W, H)
    # enduit du mur (toute la pièce), plus sombre en haut sous la poutre et vers le sol
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    v = champ(W, H, 31, base=0.60)
    v -= 0.10 * np.clip((60 - yy) / 60, 0, 1) ** 1.5
    v -= 0.08 * np.clip((yy - 250) / 80, 0, 1)
    peindre(im, 0, 0, v, R_ENDUIT)
    susa(im, 0, 0, W, 330, R_ENDUIT, 5)
    # lumière froide de la lune autour de la fenêtre, chaude près du menu (lampe hors champ)
    fa.lumiere(im, WX + WC, WY + WC + 10, 96, (120, 120, 170), force=0.10, ry=88, niveaux=3)
    fa.lumiere(im, 107, 290, 110, (242, 165, 72), force=0.09, ry=70, niveaux=3)

    # alcôve (tokonoma) : enduit plus profond, occlusion sous la poutre et le long des joues
    ax, ay, aw, ah = 222, 48, 398, 270
    v = champ(aw, ah, 32, base=0.55, gros=0.08)
    v -= 0.22 * np.clip((40 - np.arange(ah)[:, None]) / 40, 0, 1) ** 1.3
    peindre(im, ax, ay, v, R_ALCOVE)
    susa(im, ax, ay, aw, ah, R_ALCOVE, 6)
    ao_bande(im, ax, ay, aw, ah, "g", 6, 0.35)
    ao_bande(im, ax, ay, aw, ah, "d", 6, 0.35)
    fa.lumiere(im, 421, 200, 190, (242, 190, 120), force=0.07, ry=110, niveaux=3)

    # poutre haute
    kit.bois(im, 0, 0, W, 16, "sombre", sens="h", seed=40, noeuds=2)
    rect(im, 0, 15, W, 1, hexc("0E0604"))
    ao_bande(im, 0, 16, W, 6, "h", 4, 0.30)

    # poutre aux kamon (otoshigake) : laque noire, liseré or, ferrures aux bouts
    bx, by, bw, bh = 222, 20, 398, 28
    kit.ombre_portee(im, bx, by, bw, bh, dx=0, dy=2, force=0.55, rayon=1)
    fa.lq(im, bx + 1, by + 1, bw - 2, bh - 2, "noir", seed=41, intensite=1.1)
    fa.liseret(im, bx, by, bw, bh)
    rect(im, bx + 3, by + 3, bw - 6, 1, hexc("6A1420"))
    rect(im, bx + 3, by + bh - 4, bw - 6, 1, hexc("6A1420"))
    kit.kanagu(im, bx, by, bw, bh, "petit", deborde=0)
    # sièges des kamon : petite cuvette sombre (les disques sont posés par le code)
    for i in range(5):
        cx = 262 + i * 70 + 9
        m = disque_masque(24, 11.5)
        fa.assombrir(im, cx - 12, by + 2, m * 0.45)

    # toko-gamachi (rebord laqué de l'alcôve)
    gx, gy, gw, gh = 222, 318, 398, 10
    fa.lq(im, gx, gy, gw, gh, "noir", seed=42, intensite=1.4)
    rect(im, gx, gy, gw, 1, hexc(R_OR[5]))
    rect(im, gx, gy + 1, gw, 1, hexc(R_OR[2]))

    # piliers
    for (x0, x1, sd) in ((0, 13, 50), (200, 214, 51), (620, 640, 52)):
        pilier(im, x0, x1, 0, 328, sd)
        if x1 < W:
            ao_bande(im, x1, 20, 4, 308, "g", 4, 0.35)
        if x0 > 0:
            ao_bande(im, x0 - 4, 20, 4, 308, "d", 3, 0.25)
    rect(im, 214, 20, 8, 298, hexc("2C170E"))
    kit.bois(im, 214, 20, 8, 298, "sombre", sens="v", seed=53, noeuds=0)

    # plancher laqué
    fy = 328
    v = np.full((H - fy, W), 0.45) + (kit.bruit(W, H - fy, 10, 60, 2, etire=(6, 1)) - 0.5) * 0.2
    v -= 0.25 * (np.arange(H - fy)[:, None] / (H - fy))
    peindre(im, 0, fy, v, R_PLANCHER, tramage=0.3)
    for x in range(0, W, 92):
        rect(im, x + 37, fy + 2, 1, H - fy - 2, hexc("0A0304"))
    rect(im, 0, fy, W, 1, hexc(R_OR[4]))
    rect(im, 0, fy + 1, W, 1, hexc("6A1E27"))
    rect(im, 0, fy + 2, W, 1, hexc("0A0304"))
    # reflet de la fenêtre sur la laque du sol
    fa.lumiere(im, 107, fy + 6, 40, (150, 140, 190), force=0.10, ry=4, niveaux=2)

    # fenêtre : ombre portée du cadre sur le mur, puis le ciel
    m = disque_masque(WS + 4, WS / 2 + 1.5)
    kit.ombre_portee(im, WX - 2, WY - 2, WS + 4, WS + 4, dx=2, dy=3, force=0.6, rayon=2, masque=m)
    im.alpha_composite(ciel, (WX, WY))

    # cordons du menu (fil rouge torsadé de plaque en plaque)
    for (y0, y1) in ((222, 230), (245, 252), (267, 274), (289, 296)):
        fa.corde_v(im, 106, y0, y1, "rouge", ep=2)
    kit.anneau_metal(im, 107, 222, 1)

    devanture(im)
    kamishibai(im)
    porte_amulettes(im)
    return im


def piece_etendue(scene):
    """La pièce prolongée sur MX / MY texels autour de la scène : enduit, poutre haute et plancher continus, piliers
    supplémentaires, plafond de bois sombre au-dessus de la poutre ; assombrie en paliers vers les bords."""
    im = nouveau(EW, EH)
    yy, xx = np.mgrid[0:EH, 0:EW].astype(float)
    v = champ(EW, EH, 71, base=0.58)
    v -= 0.08 * np.clip((yy - (MY + 250)) / 80, 0, 1)
    peindre(im, 0, 0, v, R_ENDUIT)
    susa(im, 0, MY, EW, 330, R_ENDUIT, 72)
    # plafond (au-dessus de la poutre haute) : bois sombre, une traverse
    kit.bois(im, 0, 0, EW, MY, "sombre", sens="h", seed=73, noeuds=2)
    rect(im, 0, MY - 30, EW, 6, hexc("2C170E"))
    rect(im, 0, MY - 30, EW, 1, hexc("553220"))
    rect(im, 0, MY - 25, EW, 1, hexc("0E0604"))
    # poutre haute, continue
    kit.bois(im, 0, MY, EW, 16, "sombre", sens="h", seed=40, noeuds=3)
    rect(im, 0, MY + 15, EW, 1, hexc("0E0604"))
    ao_bande(im, 0, MY + 16, EW, 6, "h", 4, 0.30)
    # piliers du prolongement (bague de laiton au raccord de la poutre, comme ceux de la scène)
    for (x0, x1, sd) in ((MX - 100, MX - 86, 74), (EW - MX + 86, EW - MX + 100, 75)):
        kit.bois(im, x0, MY, x1 - x0, 328, "moyen", sens="v", seed=sd, noeuds=1)
        rect(im, x0, MY, 1, 328, hexc("A96B3F"))
        rect(im, x1 - 2, MY, 2, 328, hexc("2C170C"))
        rect(im, x1 - 3, MY, 1, 328, hexc("3B2012"))
        fa.lq(im, x0, MY + 16, x1 - x0, 4, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=sd, reflet=False)
        rect(im, x0, MY + 16, x1 - x0, 1, hexc(R_OR[6]))
        rect(im, x0, MY + 19, x1 - x0, 1, hexc(R_OR[2]))
        ao_bande(im, x1, MY + 20, 4, 308, "g", 4, 0.35)
        ao_bande(im, x0 - 4, MY + 20, 4, 308, "d", 3, 0.25)
    # ombre des piliers de bord de la scène sur le mur du prolongement
    ao_bande(im, MX - 4, MY + 20, 4, 308, "d", 3, 0.25)
    ao_bande(im, MX + W, MY + 20, 4, 308, "g", 4, 0.35)
    # plancher laqué, continu (joints alignés sur ceux de la scène)
    fy = MY + 328
    v = np.full((EH - fy, EW), 0.45) + (kit.bruit(EW, EH - fy, 10, 76, 2, etire=(6, 1)) - 0.5) * 0.2
    v -= 0.30 * np.clip(np.arange(EH - fy)[:, None] / (H - 328 + MY), 0, 1)
    peindre(im, 0, fy, v, R_PLANCHER, tramage=0.3)
    for x in range(MX + 37 - 92 * 2, EW, 92):
        rect(im, x, fy + 2, 1, EH - fy - 2, hexc("0A0304"))
    rect(im, 0, fy, EW, 1, hexc(R_OR[4]))
    rect(im, 0, fy + 1, EW, 1, hexc("6A1E27"))
    rect(im, 0, fy + 2, EW, 1, hexc("0A0304"))
    # assombri selon la distance à la scène, en 6 niveaux tramés (le regard reste au centre)
    dx = np.maximum(np.maximum(MX - xx, xx - (MX + W - 1)), 0)
    dy = np.maximum(np.maximum(MY - yy, yy - (MY + H - 1)), 0)
    d = np.maximum(dx, dy)
    g = np.clip(d / 104.0, 0, 1) ** 0.85
    A = kit.quantifier(g, 6, 0, 0, tramage=0.5) / 5.0 * 0.5
    fa.assombrir(im, 0, 0, A)
    # chaque partie garde sa propre palette de 256 couleurs : la scène reste identique au texel près
    im = quantifiee(im)
    im.paste(quantifiee(scene), (MX, MY))
    return im


def quantifiee(im):
    """Même réduction que Sortie.sauver(quantifier=True) (médiane, 256 couleurs, sans tramage)."""
    a = np.asarray(im.convert("RGBA"))
    q = Image.fromarray(a[..., :3].copy(), "RGB").quantize(256, method=Image.Quantize.MEDIANCUT,
                                                           dither=Image.Dither.NONE)
    return q.convert("RGBA")


# ───────────── Échoppe : devanture (planches, comptoir, noren, blason) ─────────────
SX, SY, SW, SH = 236, 60, 177, 121


def devanture(im):
    x, y, w, h = SX, SY, SW, SH
    kit.ombre_portee(im, x, y, w, h, dx=2, dy=3, force=0.6, rayon=2)
    kit.bois(im, x, y, w, h, "sombre", sens="v", seed=70, planches=7, noeuds=2)
    # lumière chaude des chōchin sur les planches
    for lx in (x + 17, x + w - 17):
        fa.lumiere(im, lx, y + 64, 34, (242, 140, 60), force=0.22, ry=40, niveaux=4)
    # comptoir
    cy = y + h - 16
    kit.bois(im, x, cy, w, 16, "moyen", sens="h", seed=71, noeuds=1)
    rect(im, x, cy, w, 1, hexc("C08850"))
    rect(im, x, cy - 1, w, 1, hexc("0E0604"))
    # noren indigo (seigaiha), quatre lés fendus, hampe de bambou
    ny, nh = y + 1, 40
    kit.bande_tissu(im, x + 1, ny + 3, w - 2, nh - 3, "indigo", "seigaiha", liseret=None, seed=2)
    a = np.asarray(im).copy()
    for k in range(1, 4):
        sx = x + 1 + k * (w - 2) // 4
        a[ny + 14:ny + nh, sx] = (8, 10, 22, 255)
        a[ny + 14:ny + nh, sx + 1] = np.minimum(255, a[ny + 14:ny + nh, sx + 1].astype(int) + 18).astype(np.uint8)
    a[ny + nh - 2:ny + nh, x + 1:x + w - 1, :3] = (18, 24, 48)
    im.paste(Image.fromarray(a, "RGBA"))
    kit.ombre_portee(im, x + 1, ny + nh, w - 2, 1, dx=0, dy=1, force=0.6, rayon=1)
    rect(im, x, ny, w, 3, hexc("2A1A10"))
    rect(im, x, ny, w, 1, hexc("6E4124"))
    # blason 商 : disque blanc cerné d'indigo
    cx, cyb = x + w // 2, ny + 17
    disc = disque_masque(23, 11.2)
    p = np.zeros((23, 23, 4), np.uint8)
    p[disc] = hexc("E8E0CC")
    ring = disc & ~disque_masque(23, 9.6)
    p[ring] = hexc("C8BCA4")
    coller(im, p, cx - 11, cyb - 11)
    kit.kanji(im, cx, cyb - 7, "商", 14, police="gras", couleur=hexc("1F2848"), ancre="c")
    # cadre : liseré or et ferrures
    fa.liseret(im, x, y, w, h)
    kit.kanagu(im, x, y, w, h, "moyen", deborde=1)


# ───────────── Stream : kamishibai (théâtre de papier) ─────────────
KX, KY = 429, 60


def kamishibai(im):
    x, y = KX, KY + 4
    w, h = 177, 117
    R_B = kit.R_BOIS["moyen"]
    # volets ouverts (en perspective)
    for sgn in (-1, 1):
        x_in = x + 22 if sgn < 0 else x + w - 23
        x_out = x + 2 if sgn < 0 else x + w - 3
        vol = nouveau(w, h + 4)
        for i in range(21):
            t = i / 20
            xx = int(round(x_in + (x_out - x_in) * t)) - x
            y_top = int(round(14 + 8 * t))
            y_bot = int(round(h - 2 - 8 * t))
            for j in range(y_top, y_bot):
                ton = 4 if (j - y_top) % 10 not in (0,) else 2
                if i in (0, 20):
                    ton = 1
                if j in (y_top, y_bot - 1):
                    ton = 6 if j == y_top else 1
                vol.putpixel((xx, j), hexc(R_B[ton] if sgn < 0 else R_B[max(0, ton - 1)]))
        im.alpha_composite(vol, (x, y))
    # caisse centrale
    cx0, cy0, cw, ch = x + 22, y + 10, w - 44, h - 10
    kit.ombre_portee(im, cx0, cy0, cw, ch, dx=2, dy=3, force=0.55, rayon=2)
    kit.bois(im, cx0, cy0, cw, ch, "moyen", sens="h", seed=80, noeuds=1)
    rect(im, cx0, cy0, cw, 1, hexc(R_B[7]))
    # ouverture de l'écran (le code y pose le contenu) : fond nuit
    sx, sy, sw, sh = x + 30, y + 19, 117, 89
    rect(im, sx - 2, sy - 2, sw + 4, sh + 4, hexc("1E100A"))
    rect(im, sx - 1, sy - 1, sw + 2, 1, hexc("0A0506"))
    v = np.full((sh, sw), 0.4) + (kit.bruit(sw, sh, 20, 81, 2) - 0.5) * 0.12
    v -= 0.2 * (np.arange(sh)[:, None] / sh)
    peindre(im, sx, sy, v, ["0C0A14", "100E1A", "141220", "181626", "1C1A2C"], tramage=0.2)
    fa.liseret(im, sx - 2, sy - 2, sw + 4, sh + 4)
    # petit toit
    for k in range(9):
        rect(im, x + 18 + k * 2, y + 9 - k, w - 36 - k * 4, 1, hexc("2C170E" if k else "120806"))
    rect(im, x + 16, y + 9, w - 32, 1, hexc("120806"))
    rect(im, x + 18, y + 10, w - 36, 1, hexc("6E4124"))
    # plaque STREAM (laque indigo) au-dessus
    px, py, pw, ph = KX + 88 - 22, KY - 6, 44, 12
    fa.corde_v(im, px + 10, 48, py, "or", ep=1)
    fa.corde_v(im, px + pw - 11, 48, py, "or", ep=1)
    kit.ombre_portee(im, px, py, pw, ph, dx=1, dy=2, force=0.55, rayon=1)
    fa.lq(im, px + 1, py + 1, pw - 2, ph - 2, R_INDIGO_N[:6], seed=82, intensite=1.2)
    fa.liseret(im, px, py, pw, ph)
    fa.ferrures3(im, px, py, pw, ph)


# ───────────── Récompenses : porte-amulettes ─────────────
RX, RY, RW, RH = 429, 197, 177, 121


def porte_amulettes(im):
    x, y, w, h = RX, RY, RW, RH
    kit.ombre_portee(im, x, y, w, h, dx=2, dy=3, force=0.6, rayon=2)
    fa.lq(im, x + 1, y + 1, w - 2, h - 2, ["1E0A0D", "260C10", "2E0E13", "361117", "3E141A", "46171E", "561C24"],
          seed=90, intensite=1.0)
    fa.liseret(im, x, y, w, h)
    rect(im, x + 3, y + 3, w - 6, 1, hexc(R_OR[3]))
    rect(im, x + 3, y + h - 4, w - 6, 1, hexc(R_OR[3]))
    rect(im, x + 3, y + 3, 1, h - 6, hexc(R_OR[3]))
    rect(im, x + w - 4, y + 3, 1, h - 6, hexc(R_OR[3]))
    kit.kanagu(im, x, y, w, h, "moyen", deborde=1)
    # bandeau titre (laque noire) et filet bas
    fa.lq(im, x + 5, y + 5, w - 10, 14, "noir", seed=91, intensite=1.2)
    rect(im, x + 5, y + 19, w - 10, 1, hexc(R_OR[4]))
    rect(im, x + 5, y + 20, w - 10, 1, hexc("120608"))
    rect(im, x + 8, y + h - 23, w - 16, 1, hexc(R_OR[2]))
    # tringle de bambou laqué aux embouts d'or
    ty = y + 24
    rect(im, x + 8, ty, w - 16, 4, hexc("2C170E"))
    kit.bois(im, x + 8, ty, w - 16, 4, "moyen", sens="h", seed=92, noeuds=0)
    rect(im, x + 8, ty + 4, w - 16, 1, hexc("0A0304"))
    for ex in (x + 6, x + w - 11):
        fa.lq(im, ex, ty - 1, 5, 6, ["4A3518", "6A4E24", "8E6A30", "B08442", "D9A95E"], seed=ex, reflet=False)
        fa.liseret(im, ex, ty - 1, 5, 6)


# ═════════════════════════════════════ sprites ═════════════════════════════════════
def plaques():
    """Plaques laquées du menu, 132×15 : repos (laque noire, or éteint), survol (laque rouge, or vif, pointe
    d'or), danger (laque rouge sombre, liseré vermillon)."""
    im = nouveau(144, 60)
    for r, etat in enumerate(("repos", "survol", "danger")):
        oy = r * 20
        x, y, w, h = 8, oy + 2, 132, 15
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=2, force=0.6, rayon=1)
        if etat == "repos":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=7, intensite=1.0)
            fa.liseret_terne(im, x, y, w, h)
            fa.ferrures3(im, x, y, w, h, terne=True)
        elif etat == "survol":
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=7, intensite=1.5)
            fa.liseret(im, x, y, w, h)
            fa.ferrures3(im, x, y, w, h)
        else:
            fa.lq(im, x + 1, y + 1, w - 2, h - 2, fa.LAQUE_ROUGE_SOMBRE + ["561C24", "6A2028"], seed=7, intensite=1.3)
            c = hexc("D2523E")
            rect(im, x, y, w, 1, c); rect(im, x, y, 1, h, c)
            rect(im, x, y + h - 1, w, 1, hexc("6E1511")); rect(im, x + w - 1, y, 1, h, hexc("6E1511"))
            fa.ferrures3(im, x, y, w, h)
        kit.anneau_metal(im, x + w // 2 - 1, y, 1)
        if etat != "repos":
            # pointe d'or (losange étiré) à gauche
            for k in range(4):
                rect(im, k, oy + 6 + k, 1, 7 - 2 * k, hexc(R_OR[6] if k < 2 else R_OR[4]))
            rect(im, 0, oy + 6, 1, 1, hexc(R_OR[7]))
    S.sauver("plaques.png", im, (144, 60))


VILLAGES = ("konoha", "suna", "kiri", "kumo", "iwa")


def embleme(v, t):
    src = Image.open(os.path.join(TEX_ESC, f"village_{v}.png")).convert("RGBA")
    r = np.asarray(src.resize((t, t), Image.BOX)).astype(float)
    return r[..., 3] > 100


def kamons():
    im = nouveau(110, 78)
    for i, v in enumerate(VILLAGES):
        em = embleme(v, 12)
        for r, etat in enumerate(("repos", "survol", "mien")):
            ox, oy = i * 22 + 1, r * 22 + 1
            m = disque_masque(20, 9.8)
            # ombre
            sh = np.zeros((20, 20, 4), np.uint8)
            sh[m] = (4, 2, 3, 150)
            coller(im, sh, ox + 1, oy + 1)
            # disque : jante d'or, fond laqué
            yy, xx = np.mgrid[0:20, 0:20].astype(float)
            d = np.sqrt((xx - 9.5) ** 2 + (yy - 9.5) ** 2)
            p = np.zeros((20, 20, 4), np.uint8)
            jante = m & (d > 7.6)
            haut = (xx + yy) < 19
            Rf = R_LAQUE_ROUGE if etat == "mien" else R_LAQUE
            fond = m & ~jante
            fv = 0.35 + 0.35 * (1 - (xx + yy) / 38) + (0.15 if etat == "survol" else 0)
            fi = kit.quantifier(np.clip(fv, 0, 0.999), 5, 0, 0, tramage=0.3)
            base = 3 if etat == "mien" else 2
            for j in range(20):
                for k in range(20):
                    if fond[j, k]:
                        p[j, k] = hexc(Rf[min(len(Rf) - 1, base + fi[j, k])])
            if etat == "repos":
                p[jante & haut] = hexc(R_OR[4]); p[jante & ~haut] = hexc(R_OR[2])
            else:
                p[jante & haut] = hexc(R_OR[6]); p[jante & ~haut] = hexc(R_OR[3])
            p[m & (d > 9.0)] = hexc("2A1A10")
            p[jante & (d <= 7.9) & (d > 7.6)] = hexc(R_OR[1])
            # emblème
            col = {"repos": hexc(R_OR[3]), "survol": hexc(R_OR[5]), "mien": hexc("FBEBC8")}[etat]
            ec = np.zeros((20, 20), bool)
            ec[4:16, 4:16] = em
            if etat == "mien":
                p[np.roll(np.roll(ec, 1, 0), 1, 1) & fond & ~ec] = hexc("2A070D")
            p[ec] = col
            coller(im, p, ox, oy)
        # petit emblème blanc 10×10 (teint par le code : ligne d'identité)
        pe = np.zeros((10, 10, 4), np.uint8)
        pe[embleme(v, 10)] = (255, 255, 255, 255)
        coller(im, pe, i * 22, 66)
    S.sauver("kamons.png", im, (110, 78))


def chochin():
    """Chōchin rouge 店 (20×32) : anneaux laqués noirs, côtes de bambou, papier rouge en crans ; 2 images
    (repos, plus vive au survol)."""
    im = nouveau(40, 32)
    for f in range(2):
        ox = f * 20
        R = kit.rampe("C23A2C", 8, ombre=0.25, lumiere=0.6)
        cx = 9.5
        for j in range(4, 28):
            t = (j - 4) / 23
            hw = 4 + 5.2 * math.sin(math.pi * t) ** 0.7
            for i in range(20):
                dx = (i - cx) / hw
                if abs(dx) <= 1:
                    v = 0.75 - 0.45 * dx * dx - 0.15 * dx + (0.12 if f else 0) - 0.2 * abs(t - 0.45)
                    if (j - 4) % 4 == 0:
                        v -= 0.18
                    k = int(np.clip(v, 0, 0.999) * len(R))
                    im.putpixel((ox + i, j), R[k])
        for (j0, hh, ww) in ((2, 3, 8), (28, 3, 8)):
            fa.lq(im, ox + 10 - ww // 2, j0, ww, hh, "noir", seed=j0, reflet=False)
            rect(im, ox + 10 - ww // 2, j0, ww, 1, hexc("4A2E35"))
        rect(im, ox + 9, 0, 2, 2, hexc(R_OR[4]))
        rect(im, ox + 9, 0, 1, 1, hexc(R_OR[7]))
        kit.kanji(im, ox + 10, 11, "店", 10, police="gothic", couleur=hexc("1E0A0A"), ancre="c")
    S.sauver("chochin.png", im, (40, 32))


CHARMES = ("DC5AC8", "468CDC", "46AA64", "965AF0", "E6B43C")


def omamori():
    """Amulettes de brocart (24×40) : cordon et boucle, sachet aux épaules arrondies, motif tissé, étiquette 守
    et nœud d'or."""
    im = nouveau(120, 40)
    for i, col in enumerate(CHARMES):
        ox = i * 24
        R = kit.rampe(col, 7, ombre=0.25, lumiere=0.55)
        # boucle de cordon
        kit.anneau_metal(im, ox + 12, 2, 2, "or")
        rect(im, ox + 12, 4, 1, 4, hexc(R_OR[5]))
        x0, y0, w, h = ox + 3, 7, 18, 31
        m = np.ones((h, w), bool)
        for (j, cut) in ((0, 3), (1, 2), (2, 1)):
            m[j, :cut] = False
            m[j, w - cut:] = False
        yy, xx = np.mgrid[0:h, 0:w]
        v = 0.6 - 0.25 * (xx / w) + 0.1 * (1 - yy / h)
        motif = (((xx + yy) % 6) == 0) | (((xx - yy) % 6) == 0)
        v = v + 0.12 * motif
        idx = kit.quantifier(np.clip(v, 0, 0.999), 7, x0, y0, tramage=0.3)
        p = kit._rampe_vers_rgba(R, idx, m)
        e = fa._bord(m)
        p[e] = R[0]
        coller(im, p, x0, y0)
        # nœud d'or au col
        rect(im, ox + 9, 9, 7, 2, hexc(R_OR[5]))
        rect(im, ox + 9, 9, 7, 1, hexc(R_OR[7]))
        rect(im, ox + 9, 11, 7, 1, hexc(R_OR[2]))
        # étiquette de washi 守
        lx, ly = ox + 7, 14
        lab = np.zeros((18, 10, 4), np.uint8)
        lab[:, :] = hexc("ECE2C8")
        lab[0, :] = hexc("F8F0DE"); lab[:, 0] = hexc("F8F0DE")
        lab[-1, :] = hexc("B6A07A"); lab[:, -1] = hexc("B6A07A")
        coller(im, lab, lx, ly)
        kit.kanji(im, lx + 5, ly + 4, "守", 10, police="gothic", couleur=hexc("8E1628"), ancre="c")
    S.sauver("omamori.png", im, (120, 40))


def kawaraban():
    """Feuille d'annonces sous auvent : poteaux, toit de tuiles, washi aux bords déchirés, bandeau d'encre."""
    w, h = 134, 122
    im = nouveau(w, h)
    for px_ in (5, w - 10):
        kit.bois(im, px_, 12, 5, h - 12, "moyen", sens="v", seed=px_, noeuds=0)
    kit.ombre_portee(im, 13, 20, 108, 98, dx=2, dy=3, force=0.55, rayon=2)
    kit.washi(im, 13, 20, 108, 98, "creme", seed=61, bords="dechires", ombre=False)
    # bandeau noir (titre posé par le code)
    fa.lq(im, 15, 21, 104, 12, "noir", seed=62, intensite=1.0)
    rect(im, 15, 33, 104, 1, hexc("8E1628"))
    # punaises de bois
    for (px_, py_) in ((17, 37), (w - 19, 37)):
        rect(im, px_, py_, 2, 2, hexc("5C351D"))
        rect(im, px_, py_, 1, 1, hexc("A96B3F"))
    # toit de tuiles
    for k in range(14):
        y = 13 - k
        half = int((w / 2) * (1 - k / 16))
        x0 = w // 2 - half
        rect(im, x0, y, 2 * half, 1, hexc("1E1618" if k % 2 else "2E2426"))
    for x in range(4, w - 4, 6):
        rect(im, x, 13, 1, 1, hexc("4A3C3E"))
    rect(im, 0, 14, w, 2, hexc("0C090A"))
    rect(im, 0, 14, w, 1, hexc("4A3C3E"))
    S.sauver("kawaraban.png", im, (w, h))


def reseaux():
    im = nouveau(64, 32)
    for i, n in enumerate(("discord", "x", "youtube", "site")):
        src = Image.open(os.path.join(TEX_ESC, f"social_{n}.png")).convert("RGBA")
        small = np.asarray(src.resize((14, 14), Image.BOX)).astype(float)
        a = small[..., 3]
        for r in range(2):
            p = np.zeros((16, 16, 4), np.uint8)
            on = a > 110
            rgb = small[..., :3]
            if r == 0:
                rgb = rgb * 0.78 + np.array([30, 22, 18]) * 0.22
            pp = np.zeros((14, 14, 4), np.uint8)
            pp[on, :3] = np.clip(rgb[on], 0, 255).astype(np.uint8)
            pp[on, 3] = 255
            sh = np.zeros((14, 14, 4), np.uint8)
            sh[on] = (6, 3, 4, 170)
            pim = Image.fromarray(p, "RGBA")
            coller(pim, sh, 2, 2)
            coller(pim, pp, 1, 1)
            coller(im, pim, i * 16, r * 16)
    S.sauver("reseaux.png", im, (64, 32))


def pieces():
    im = nouveau(128, 48)
    ea.cabochon(im, 0, 0, 42, 12)
    # flèches < > (repos, survol)
    fl = ["...##", "..##.", ".##..", "##...", ".##..", "..##.", "...##"]
    for k, (ox, col) in enumerate(((44, R_OR[4]), (60, R_OR[7]))):
        for j, row in enumerate(fl):
            for i, ch in enumerate(row):
                if ch == "#":
                    rect(im, ox + 1 + i, 1 + j, 1, 1, hexc(col))
                    rect(im, ox + 8 + 1 + (4 - i), 1 + j, 1, 1, hexc(col))
    # pièce RBCoin 10×10
    m = disque_masque(10, 5.0)
    yy, xx = np.mgrid[0:10, 0:10].astype(float)
    d = np.sqrt((xx - 4.5) ** 2 + (yy - 4.5) ** 2)
    p = np.zeros((10, 10, 4), np.uint8)
    p[m] = hexc(R_OR[5])
    p[m & ((xx + yy) < 7)] = hexc(R_OR[7])
    p[m & (d > 4.1)] = hexc(R_OR[3])
    inner = m & (d <= 3.1)
    p[inner] = hexc("A0182B")
    p[inner & ((xx + yy) < 8)] = hexc("CC3A4C")
    for (x, y) in ((4, 3), (4, 4), (4, 5), (4, 6), (5, 3), (6, 4), (5, 5), (6, 6)):
        p[y, x] = hexc("FBEBC8")
    coller(im, p, 78, 0)
    # badges (coupe horizontale)
    for k, ton in enumerate(("rouge", "noir")):
        x, y, w, h = k * 46, 14, 44, 11
        kit.ombre_portee(im, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
        fa.lq(im, x + 1, y + 1, w - 2, h - 2, ton, seed=k, intensite=1.3)
        if ton == "rouge":
            fa.liseret(im, x, y, w, h)
        else:
            fa.liseret_terne(im, x, y, w, h)
    # pièce de ryo 7×7 (trou carré)
    for (x, y, c) in ((99, 14, R_OR[3]), (100, 14, R_OR[5]), (101, 14, R_OR[5]), (102, 14, R_OR[3]),
                      (98, 15, R_OR[5]), (99, 15, R_OR[7]), (100, 15, R_OR[6]), (101, 15, R_OR[6]), (102, 15, R_OR[5]), (103, 15, R_OR[3]),
                      (98, 16, R_OR[5]), (99, 16, R_OR[6]), (102, 16, R_OR[5]), (103, 16, R_OR[3]),
                      (98, 17, R_OR[5]), (99, 17, R_OR[6]), (102, 17, R_OR[4]), (103, 17, R_OR[3]),
                      (98, 18, R_OR[4]), (99, 18, R_OR[5]), (100, 18, R_OR[5]), (101, 18, R_OR[4]), (102, 18, R_OR[4]), (103, 18, R_OR[2]),
                      (99, 19, R_OR[3]), (100, 19, R_OR[3]), (101, 19, R_OR[3]), (102, 19, R_OR[2])):
        rect(im, x, y, 1, 1, hexc(c))
    rect(im, 100, 16, 2, 2, hexc(R_OR[2]))
    # pastille (point rouge pulsant)
    rect(im, 93, 14, 1, 3, hexc("E26474")); rect(im, 92, 15, 3, 1, hexc("E26474")); rect(im, 93, 15, 1, 1, hexc("FFD0D0"))
    # filet or
    rect(im, 0, 28, 60, 1, hexc(R_OR[3]))
    rect(im, 0, 28, 4, 1, hexc(R_OR[6]))
    S.sauver("pieces.png", im, (128, 48))


def infobulle():
    im = nouveau(176, 80)
    kit.ombre_portee(im, 2, 2, 168, 72, dx=2, dy=3, force=0.5, rayon=1)
    kit.washi(im, 2, 2, 168, 72, "creme", seed=77, bords="nets", ombre=False)
    rect(im, 2, 2, 168, 2, hexc("8E1628"))
    a = np.asarray(im).copy()
    # bandes de coupe sans ornement : on recopie une colonne et une rangée neutres
    S.sauver("infobulle.png", a, (176, 80))


def halo():
    A = fa.masque_lumiere(23, 23, 4, expo=1.6)
    h, w = A.shape
    p = np.zeros((48, 48, 4), np.uint8)
    p[..., :3] = 255
    p[:h, :w, 3] = np.clip(A * 255, 0, 255).astype(np.uint8)
    S.sauver("halo.png", p, (48, 48))


def fusuma(cote):
    """Fusuma : washi crème dans un cadre de bois laqué, kumiko au tiers haut, vagues d'or, hikite laqué."""
    w, h = 320, 360
    im = nouveau(w, h)
    fa.lq(im, 0, 0, w, h, "noir", seed=100 + cote, intensite=0.8)
    kit.washi(im, 8, 8, w - 16, h - 16, "creme", seed=101 + cote, bords="nets", ombre=False, taches=False)
    # vagues d'or (seigaiha large) au tiers bas
    for k in range(5):
        cy = h - 22 - k * 11
        for cx in range(-12 + (k % 2) * 12, w + 24, 24):
            for t in range(0, 181, 6):
                a = math.radians(180 + t)
                x = int(round(cx + 11 * math.cos(a)))
                y = int(round(cy + 11 * math.sin(a)))
                if 8 <= x < w - 8 and 8 <= y < h - 8:
                    im.putpixel((x, y), hexc(R_OR[4] if k % 2 else R_OR[5]))
    # kumiko
    for x in range(8, w - 8, 26):
        rect(im, x, 8, 2, 112, hexc("5C351D"))
        rect(im, x, 8, 1, 112, hexc("814E2C"))
    for y in range(8, 120, 26):
        rect(im, 8, y, w - 16, 2, hexc("5C351D"))
        rect(im, 8, y, w - 16, 1, hexc("814E2C"))
    rect(im, 8, 120, w - 16, 3, hexc("2C170E"))
    rect(im, 8, 120, w - 16, 1, hexc(R_OR[4]))
    fa.liseret(im, 0, 0, w, h)
    rect(im, 7, 7, w - 14, 1, hexc("0A0506"))
    rect(im, 7, 7, 1, h - 14, hexc("0A0506"))
    # hikite (poignée creuse laquée, cerclée d'or) côté jonction
    hx = w - 30 if cote == 0 else 30
    m = disque_masque(19, 9.2)
    p = np.zeros((19, 19, 4), np.uint8)
    yy, xx = np.mgrid[0:19, 0:19].astype(float)
    d = np.sqrt((xx - 9) ** 2 + (yy - 9) ** 2)
    p[m] = hexc(R_OR[5])
    p[m & ((xx + yy) > 20)] = hexc(R_OR[3])
    p[m & (d < 7.2)] = hexc("140A0C")
    p[m & (d < 7.2) & ((xx + yy) > 19)] = hexc("2A1A1F")
    coller(im, p, hx - 9, h // 2 - 9)
    S.sauver(f"fusuma_{'l' if cote == 0 else 'r'}.png", im, (w, h))


def main():
    ciel, toits = ciel_fenetre()
    S.sauver("piece.png", piece_etendue(piece(ciel)), (EW, EH))
    fen = nouveau(224, 112)
    fen.alpha_composite(cadre_fenetre(), (0, 0))
    fen.alpha_composite(lumieres_fenetre(toits), (112, 0))
    S.sauver("fenetre.png", fen, (224, 112))
    plaques()
    kamons()
    chochin()
    omamori()
    kawaraban()
    reseaux()
    pieces()
    infobulle()
    halo()
    fusuma(0)
    fusuma(1)
    S.bilan()


if __name__ == "__main__":
    main()
