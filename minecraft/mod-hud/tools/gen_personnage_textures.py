# -*- coding: utf-8 -*-
"""
gen_personnage_textures.py — textures de la SÉLECTION et de la CRÉATION de personnage (maquettes validées
Maquettes/personnage/, 01_selection … 09_creation_recapitulatif).

Génère dans src/main/resources/assets/reborn/textures/gui/personnage/ toutes les parties FIXES des deux écrans
(DA §6 : ce qui ne bouge pas = une texture pixel art affichée en un blit, 1 texel = 1 GUI). Les primitives sont
celles des maquettes (tools/perso_art.py) ; le texte, les lucioles, les braises, les emblèmes teintés, les
personnages et tout ce qui change d'état sont posés par le mod.

Usage (depuis minecraft/mod-hud) :
    python tools/gen_personnage_textures.py

Contrat (texels ↔ GUI de la fenêtre de référence 960×540, cx = W/2, d = H − 540) :
- fond_<village>.png 1280×540 (konoha suna kiri kumo iwa ame) : ciel, étoiles, lune, montagnes, silhouette du
  village, brume, étang (reflets, colonne de lune, rides) ; texel x = x_GUI − cx + 640, posé en (cx − 640, d).
- roseaux_<village>.png 520×180 : touffe gauche [0, 260) posée en (0, H − 180), droite [260, 520) en (W − 260, H − 180).
- pierres_lit.png 600×288 : 4 colonnes (crea, dais, sel, cote) × 6 villages, cellules 150×48, centre du dessus de la
  pierre au texel (75, 20) de la cellule ; ensō gravé sur crea et sel ; contre-jour à la couleur du village.
- pierres.png 720×24 : pierres éteintes (cf. PIERRES), cellules 72×24, centre au texel (36, 9).
- lotus.png 192×14 : 8 feuilles (cf. LOTUS), cellules 24×14, centre au texel (12, 8).
- ombre.png 80×24 (rendu ×2) : ombre de contact, posée en 40×12 GUI centrée sur les pieds (y − 6).
- lum_cle.png, lum_flaque.png : masques de lumière blancs (alpha = niveau), teints au blit, centrés.
- halo.png 64×64 : disque lisse (ovales des halos étirés au blit).
- kamon_sol.png 416×64 : anneau 116×30 en (0, 0) (centre (58, 15)) ; emblèmes couchés 52×16 en (0 + 52·(i % 8),
  30 + 16·(i / 8)) pour CLANS[i], centre (26, 8).
- emblemes.png 1792×176 : emblèmes blancs (luminance gardée) à la résolution écran, VILLAGES puis CLANS :
  rangée 0 cellules 64×64, y = 64 cellules 32×32, y = 96 cellules 24×24, y = 120 cellules 56×56.
- brume_avant.png 1280×180 : banc de brume devant le personnage (étape Nom), posé en (cx − 640, 360 + d).
- plaque_titre.png 340×50 : « CHOIX DU SHINOBI » ; texel (170, 0) = (cx, 0).
- progression.png 560×32 : fil kumihimo et cordelettes des 8 sceaux ; texel (280, 0) = (cx, 0).
- sceaux.png 240×102 : 8 colonnes (étapes) × 3 rangées (apposé, en cours, à venir), cellules 30×34 ;
  texel (15, 0) = (x de l'étape, 10).
- chip.png 32×16 : plaquettes 14×14 (+ombre) : croche, onde (voix).
- perche.png 300×16 : rangées 0-7 perche gauche (embout à droite), 8-15 perche droite (embout à gauche) ;
  rangée 1 du bloc = y de la perche (96).
- panneau_<nom>.png (w+8)×(16+h+28) : planche w×h, texel (4, 16) = coin haut gauche (cordelettes depuis la
  perche à y 96) ; contenu fixe cuit (séparateurs, réglettes, washi…).
- plaque_nom.png 366×71 : plaque de saisie du nom 360×64, texel (2, 2) = coin.
- tuiles_<nom>.png : cellules (w+4)×(h+5), tuile en (1, 1) : normal, survol, actif (, verrou pour les clans).
- boutons.png : rangées de 24 (BOUTONS), cellules (w+4)×24, bouton en (1, 1) : normal, survol, inactif ; kanji cuit.
- fleches.png 44×23 : ◀ ▶ (normal) puis 44..88 survol ; bouton 20×20 en (1, 1).
- curseurs.png 64×16 : curseur de réglette (12×14, texel (5, 6) = (X, y haut)) ; plaquette de toise
  (46×16 en x 16, texel (20, 7) = (bx, Y)).
- toise.png 24×314 : mât et crans de 0 à 214 cm (GUI_PAR_CM = 240/172) ; texel (6, 310) = (x du mât, sol).
- ornements.png 48×10 : losange 3×3 (0,0), cadenas 7×8 (4,0), chevron 5×5 (12,0), pointe 5×3 (18,0).
- cabochon.png 42×12 : touche des aides (bord gauche 4, milieu 16 répété depuis u 12, bord droit 6 depuis u 36).
- cartouche.png 296×70 : fiche courte de la sélection 290×64, texel (2, 2) = coin ; plaquette rouge cuite.
- silhouette.png 80×150 : emplacement libre (pointillé or) ; pieds au texel (40, 146).

Déterminisme : graines fixes ; deux exécutions écrivent les mêmes octets.
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
import perso_art as pa  # noqa: E402
from perso_art import kit, perso, hexc, rect, R_OR, Calque  # noqa: E402

SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "personnage")
TEX = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui")

OX = 160
W_FOND = 1280
Y_HOR = pa.Y_HOR
YP_C = 96
GUI_PAR_CM = 240.0 / 172.0   # 7,5 GUI/texel : un adulte de 172 cm mesure 240 GUI

VILLAGES = ["konoha", "suna", "kiri", "kumo", "iwa", "ame"]
VCOL = {"konoha": hexc("F2A548"), "suna": hexc("E8C88A"), "kiri": hexc("9ED8EA"), "kumo": hexc("F4EC8C"),
        "iwa": hexc("D39B6A"), "ame": hexc("A9B8DA")}
KANJI_VILLAGE = {"konoha": "火", "suna": "風", "kiri": "水", "kumo": "雷", "iwa": "土", "ame": "雨"}

# ───────────────────────── palettes des quatre villages absents de la maquette ─────────────────────────
pa.CIELS.update({
    "kiri": ["05070D", "080C15", "0C111D", "101725", "151D2D", "1B2535", "242E3E", "303A48"],
    "kumo": ["06060C", "0A0A14", "0F0F1C", "151524", "1C1B2C", "252334", "302C3A", "3E3840"],
    "iwa":  ["07060A", "0C0A10", "120E16", "19131A", "21181E", "2C1F22", "3A2826", "4A3228"],
    "ame":  ["06070C", "0A0C12", "0F1219", "14181F", "1A1F27", "21272F", "2A3038", "343A42"],
})
pa.EAU.update({
    "kiri": ["05080C", "080C12", "0B1119", "0F1620", "141C28"],
    "kumo": ["07070C", "0B0A11", "100E17", "15131D", "1B1824"],
    "iwa":  ["08060A", "0C090D", "110D12", "171217", "1E171C"],
    "ame":  ["06070A", "090B0F", "0D1015", "12161B", "181D23"],
})
pa.MONTAGNES.update({
    "kiri": [("1A2230", "222C3A", 260, 28, 70), ("121820", "18202A", 282, 16, 50)],
    "kumo": [("221E2C", "2C2738", 226, 70, 60), ("17141F", "1F1B29", 266, 40, 40)],
    "iwa":  [("2C1E22", "36262A", 246, 40, 120), ("1F161A", "271C20", 274, 24, 70)],
    "ame":  [("1A1E26", "222630", 270, 18, 90), ("13161C", "1A1E24", 288, 10, 60)],
})

ECRITS = []


def nouveau(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def sauver(nom, img, garder_rgb=False):
    if isinstance(img, np.ndarray):
        img = Image.fromarray(img, "RGBA")
    img = img.convert("RGBA")
    a = np.asarray(img).copy()
    if not garder_rgb:
        a[a[..., 3] == 0] = 0
    os.makedirs(SORTIE, exist_ok=True)
    chemin = os.path.join(SORTIE, nom)
    im = Image.fromarray(a, "RGBA")
    plat = a.reshape(-1, 4)
    if len(np.unique(plat, axis=0)) <= 256 and not garder_rgb:
        im.save(chemin, optimize=True)
    else:
        im.save(chemin, optimize=True)
    ECRITS.append((nom, img.size, os.path.getsize(chemin)))


def mix(a, b, t):
    return pa.mix(a, b, t)


# ═════════════════════════════════════ fonds ═════════════════════════════════════
def ciel_l(bg, village, seed):
    """pa.ciel sur 1 280 de large, voie lactée recentrée (décalage OX)."""
    W, H = bg.size
    R = [hexc(c) for c in pa.CIELS[village]]
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    v = np.clip(yy / Y_HOR, 0, 1) ** 1.15
    v += (kit.bruit(W, H, 110, seed, 3, etire=(3, 1)) - 0.5) * 0.10
    idx = kit.quantifier(v, len(R), 0, 0, tramage=0.12)
    pal = np.array(R, np.uint8)
    bg.paste(Image.fromarray(pal[idx], "RGBA"), (0, 0))
    d = ((xx - OX) * 0.42 - yy + 40) / 60.0
    band = np.exp(-d ** 2) * (yy < Y_HOR - 20) * (0.6 + 0.4 * kit.bruit(W, H, 40, seed + 9, 2))
    pa._ajouter(bg, 0, 0, band * 0.10, (150, 130, 190), niveaux=3, tramage=0.5, sat=1.0)


def etoiles_l(bg, seed, zones=()):
    """pa.etoiles sur toute la largeur (densité de la maquette conservée : 230 sur 960 → 307 sur 1 280)."""
    rs = random.Random(seed)
    pts = []
    essais = 0
    W = bg.width
    n = int(230 * W / 960)
    while len(pts) < n and essais < 60000:
        essais += 1
        x, y = rs.uniform(2, W - 2), rs.uniform(2, Y_HOR - 26)
        p_haut = 1.0 - 0.75 * (y / Y_HOR)
        d = ((x - OX) * 0.42 - y + 40) / 60.0
        p_band = 0.5 * math.exp(-d * d)
        if rs.random() > min(1.0, p_haut * 0.75 + p_band):
            continue
        if any(a <= x <= c and b <= y <= e for (a, b, c, e) in zones):
            continue
        if any(abs(px - x) < 7 and abs(py - y) < 7 for (px, py) in pts[-60:]):
            continue
        pts.append((x, y))
    for (x, y) in pts:
        X, Y = int(x), int(y)
        r = rs.random()
        if r < 0.62:
            pa._px_mix(bg, X, Y, hexc("B8B4D0"), rs.uniform(0.22, 0.45))
        elif r < 0.94:
            pa._px_mix(bg, X, Y, hexc("E8E6F4"), rs.uniform(0.55, 0.85))
        else:
            pa._px_mix(bg, X, Y, hexc("FFFFFF"), 0.95)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                pa._px_mix(bg, X + dx, Y + dy, hexc("C8C4E8"), 0.42)
    return pts


def _fenetres(bg, fen, K, rs, chaud=(255, 236, 200)):
    for (fx, fy) in fen:
        pa._px_set(bg, fx, fy, mix(K, chaud, rs.uniform(0.1, 0.45)), rs.uniform(0.6, 0.95))
        if rs.random() < 0.3:
            pa._px_set(bg, fx + 1, fy, K, 0.6)


def _tour_ronde(bg, cx, w, top, base, corps, lum, ombre):
    """Tour cylindrique : corps en colonnes (liseré lune à gauche, ombre à droite)."""
    for y in range(top, base):
        rect(bg, cx - w // 2, y, w, 1, hexc(corps))
        rect(bg, cx - w // 2, y, 1, 1, hexc(lum))
        rect(bg, cx + w - w // 2 - 1, y, 1, 1, hexc(ombre))


def _dome(bg, cx, r, base, corps, lum, ombre="1A1216"):
    for j in range(r + 1):
        half = int(round(r * math.sqrt(max(0, 1 - ((r - j) / r) ** 2))))
        y = base - (r - j)
        rect(bg, cx - half, y, 2 * half, 1, hexc(corps))
        if half:
            rect(bg, cx - half, y, max(1, half // 2), 1, hexc(lum))
            rect(bg, cx + half - 1, y, 1, 1, hexc(ombre))


def village_kiri(m, bg, seed=7):
    """Kiri dans la brume : maisons sur pilotis au ras de l'eau, tours cylindriques coiffées de dômes et de
    citernes, grand bâtiment du Mizukage (水), passerelles ; fenêtres bleu brume, voiles de brume épais."""
    rs = random.Random(seed)
    K = VCOL["kiri"]
    fen = []
    nuit, rim = "141C26", "2E3C4C"
    # pilotis et maisons basses
    x = 250
    while x < 712:
        w = rs.randint(10, 22)
        hb = rs.randint(6, 12)
        top = Y_HOR - 6 - hb
        rect(bg, x, top, w, hb, hexc("101820"))
        rect(bg, x, top, w, 1, hexc(rim))
        pa._toit(bg, x - 1, top - 3, w + 2, 3, hexc("0E151C"), hexc(rim))
        for k in range(x + 1, x + w, 4):
            rect(bg, k, Y_HOR - 6, 1, 6, hexc("0A1016"))
        for _ in range(rs.randint(0, 2)):
            fen.append((rs.randint(x + 2, x + w - 3), rs.randint(top + 2, top + hb - 2)))
        x += w + rs.randint(3, 9)
    # passerelle
    rect(bg, 300, Y_HOR - 14, 120, 1, hexc("1E2A36"))
    rect(bg, 520, Y_HOR - 12, 150, 1, hexc("1E2A36"))
    # tours cylindriques à dômes
    for (cx, w, top) in ((318, 14, 248), (356, 18, 232), (612, 16, 240), (650, 12, 256), (398, 12, 262)):
        _tour_ronde(bg, cx, w, top, Y_HOR - 6, nuit, rim, "0A0E14")
        _dome(bg, cx, w // 2 + 1, top, "18222E", "3A4A5C")
        rect(bg, cx - 1, top - w // 2 - 4, 2, 3, hexc("18222E"))
        for k in range(rs.randint(2, 4)):
            fen.append((rs.randint(cx - w // 2 + 2, cx + w // 2 - 3), rs.randint(top + 4, Y_HOR - 10)))
    # citernes (réservoirs ronds sur pieds)
    for (cx, y) in ((440, 266), (690, 270)):
        _dome(bg, cx, 7, y + 4, "1A2632", "3E5062")
        rect(bg, cx - 7, y + 4, 14, 5, hexc("16202A"))
        rect(bg, cx - 5, y + 9, 1, Y_HOR - y - 15, hexc("0E141A"))
        rect(bg, cx + 4, y + 9, 1, Y_HOR - y - 15, hexc("0E141A"))
    # bâtiment du Mizukage : large, toit en coupole, kanji 水
    bx, bw, btop = 520, 64, 236
    for y in range(btop, Y_HOR - 6):
        t = (y - btop) / (Y_HOR - 6 - btop)
        rect(bg, bx - bw // 2, y, bw, 1, mix("1C2836", "121A24", t))
        rect(bg, bx - bw // 2, y, 1, 1, hexc("3A4C60"))
        rect(bg, bx + bw // 2 - 1, y, 1, 1, hexc("0A0E14"))
    _dome(bg, bx, bw // 2, btop, "1E2C3A", "4A5E74")
    pa.kj_px(bg, bx, btop + 8, "水", mix(K, (40, 60, 80), 0.45))
    for k in range(6):
        pa._px_set(bg, bx - 25 + k * 10, btop + 30, mix(K, (255, 255, 255), 0.35), 0.9)
    pa.halo_px(m, bx, btop + 14, 18, K, force=0.07, niveaux=2)
    _fenetres(bg, fen, K, rs, chaud=(230, 250, 255))
    pa.lumiere(bg, 480, Y_HOR - 6, 250, K, force=0.08, ry=46, niveaux=4, tramage=0.35)
    return fen


def village_kumo(m, bg, seed=7):
    """Kumo sur ses pitons : aiguilles de roche verticales, maisons rondes perchées, tour du Raikage (雷) au dôme
    blanc ; câbles entre les pitons ; fenêtres jaune foudre."""
    rs = random.Random(seed)
    K = VCOL["kumo"]
    fen = []
    for (cx, w, top) in ((300, 30, 214), (352, 22, 242), (410, 26, 226), (560, 34, 206), (628, 24, 236),
                         (676, 28, 220), (466, 18, 258)):
        h = pa.crete(960, 0, 3, 5, seed + cx)
        for x in range(cx - w // 2, cx + w // 2):
            t = (x - (cx - w // 2)) / w
            yt = int(top + h[x] + 6 * (abs(t - 0.5) * 2) ** 3)
            for y in range(yt, Y_HOR):
                c = "3A3242" if x - (cx - w // 2) < 2 else ("221E2A" if (x * 7 + y) % 11 else "1E1A26")
                if cx + w // 2 - x < 2:
                    c = "15121C"
                rect(bg, x, y, 1, 1, hexc(c))
        # maison ronde au sommet
        _tour_ronde(bg, cx, w - 6, top - 12, top + 2, "262234", "4A4458", "141220")
        _dome(bg, cx, (w - 6) // 2, top - 12, "2C283A", "5A5470")
        for _ in range(rs.randint(1, 3)):
            fen.append((rs.randint(cx - w // 2 + 4, cx + w // 2 - 5), rs.randint(top - 9, top - 1)))
    # tour du Raikage
    bx, bw, btop = 500, 30, 196
    _tour_ronde(bg, bx, bw, btop, Y_HOR, "2A2636", "5A5470", "121020")
    _dome(bg, bx, bw // 2 + 2, btop, "3A3648", "8A86A0")
    pa.kj_px(bg, bx, btop + 10, "雷", mix(K, (60, 50, 30), 0.4))
    pa.halo_px(m, bx, btop + 16, 18, K, force=0.08, niveaux=2)
    for k in range(4):
        pa._px_set(bg, bx - 11 + k * 7, btop + 34, mix(K, (255, 255, 230), 0.4), 0.95)
    # câbles
    for (xa, ya, xb, yb) in ((300, 204, 352, 232), (410, 216, 500, 200), (560, 196, 628, 226)):
        for x in range(xa, xb):
            t = (x - xa) / (xb - xa)
            y = int(ya + (yb - ya) * t + 5 * math.sin(math.pi * t))
            pa._px_set(bg, x, y, hexc("3A3448"), 0.8)
    _fenetres(bg, fen, K, rs, chaud=(255, 255, 220))
    pa.lumiere(bg, 480, Y_HOR - 6, 250, K, force=0.08, ry=46, niveaux=4, tramage=0.35)
    return fen


def village_iwa(m, bg, seed=7):
    """Iwa taillée dans la roche : mesas en gradins, maisons cubiques encastrées, ponts de pierre, tour du
    Tsuchikage (土) en éperon ; fenêtres terre cuite."""
    rs = random.Random(seed)
    K = VCOL["iwa"]
    fen = []
    for (xa, xb, top) in ((256, 360, 246), (380, 470, 258), (560, 700, 240), (470, 560, 268)):
        hh = pa.crete(960, 0, 4, 9, seed + xa)
        for x in range(xa, xb):
            t = (x - xa) / (xb - xa)
            step = 6 * int(abs(t - 0.5) * 2 * 3)
            yt = int(top + hh[x] + step)
            for y in range(yt, Y_HOR):
                c = "4A3430" if y - yt < 1 else ("2A1E20" if (x + 3 * y) % 13 else "24191C")
                rect(bg, x, y, 1, 1, hexc(c))
        # maisons cubiques encastrées
        x = xa + 6
        while x < xb - 12:
            w = rs.randint(7, 12)
            y = rs.randint(top + 10, Y_HOR - 14)
            rect(bg, x, y, w, 7, hexc("1C1416"))
            rect(bg, x, y, w, 1, hexc("5A403A"))
            rect(bg, x - 1, y - 1, w + 2, 1, hexc("3A2A28"))
            fen.append((x + w // 2, y + 3))
            x += w + rs.randint(4, 10)
    # ponts de pierre
    for (xa, xb, y) in ((360, 380, 262), (540, 560, 270)):
        rect(bg, xa, y, xb - xa, 2, hexc("2E2224"))
        rect(bg, xa, y, xb - xa, 1, hexc("4A3634"))
    # tour du Tsuchikage
    bx, btop = 486, 214
    for y in range(btop, Y_HOR):
        t = (y - btop) / (Y_HOR - btop)
        half = int(8 + 14 * t ** 1.6)
        rect(bg, bx - half, y, 2 * half, 1, mix("32262A", "1E161A", t))
        rect(bg, bx - half, y, 1, 1, hexc("5A443E"))
    _dome(bg, bx, 12, btop, "3A2C2E", "6A5248")
    pa.kj_px(bg, bx, btop + 6, "土", mix(K, (60, 40, 30), 0.35))
    pa.halo_px(m, bx, btop + 12, 16, K, force=0.08, niveaux=2)
    _fenetres(bg, fen, K, rs)
    pa.lumiere(bg, 480, Y_HOR - 6, 250, K, force=0.09, ry=46, niveaux=4, tramage=0.35)
    return fen


def village_ame(m, bg, seed=7):
    """Ame sous la pluie : tours industrielles serrées, tuyaux, grande tour de Pain (雨) aux cornes de métal ;
    fenêtres bleu pluie, traits de pluie fins sur tout le ciel bas."""
    rs = random.Random(seed)
    K = VCOL["ame"]
    fen = []
    x = 248
    while x < 714:
        w = rs.randint(10, 20)
        top = rs.randint(232, 286)
        if 470 < x < 520:
            x += 4
            continue
        for y in range(top, Y_HOR):
            rect(bg, x, y, w, 1, hexc("161A22"))
        rect(bg, x, top, 1, Y_HOR - top, hexc("2E3644"))
        rect(bg, x + w - 1, top, 1, Y_HOR - top, hexc("0A0C10"))
        rect(bg, x - 1, top, w + 2, 2, hexc("222834"))
        if rs.random() < 0.5:
            rect(bg, x + w // 2, top - rs.randint(4, 10), 1, 10, hexc("1A1F28"))
        for _ in range(rs.randint(2, 5)):
            fen.append((rs.randint(x + 2, x + w - 3), rs.randint(top + 4, Y_HOR - 4)))
        x += w + rs.randint(2, 5)
    # tour de Pain
    bx, bw, btop = 494, 34, 190
    for y in range(btop, Y_HOR):
        rect(bg, bx - bw // 2, y, bw, 1, hexc("1C212A"))
        rect(bg, bx - bw // 2, y, 1, 1, hexc("3C4656"))
        rect(bg, bx + bw // 2 - 1, y, 1, 1, hexc("0A0C10"))
    for (dx, sgn) in ((-12, -1), (12, 1)):         # cornes
        for k in range(16):
            rect(bg, bx + dx + sgn * (k // 3), btop - k, 3, 1, hexc("1C212A"))
    pa.kj_px(bg, bx, btop + 8, "雨", mix(K, (40, 50, 70), 0.4))
    pa.halo_px(m, bx, btop + 14, 18, K, force=0.07, niveaux=2)
    # tuyaux horizontaux
    for (xa, xb, y) in ((262, 470, 292), (520, 700, 284)):
        rect(bg, xa, y, xb - xa, 2, hexc("1E242E"))
        rect(bg, xa, y, xb - xa, 1, hexc("343E4C"))
    _fenetres(bg, fen, K, rs, chaud=(220, 235, 255))
    pa.lumiere(bg, 480, Y_HOR - 6, 250, K, force=0.08, ry=46, niveaux=4, tramage=0.35)
    return fen


def pluie(bg, seed=3, n=520):
    """Traits de pluie fins et clairsemés (2-4 GUI en diagonale), plus denses vers l'horizon."""
    rs = random.Random(seed)
    W = bg.width
    for _ in range(n):
        x, y = rs.uniform(0, W), rs.uniform(0, Y_HOR + 200)
        L = rs.randint(2, 4)
        a = 0.10 + 0.12 * rs.random()
        for k in range(L):
            pa._px_mix(bg, int(x - k * 0.5), int(y + k), hexc("8A96B0"), a)


VILLAGE_FN = {"konoha": pa.village_konoha, "suna": pa.village_suna, "kiri": village_kiri, "kumo": village_kumo,
              "iwa": village_iwa, "ame": village_ame}
SEEDS = {"konoha": 1, "suna": 3, "kiri": 5, "kumo": 7, "iwa": 9, "ame": 11}


def fond(village):
    seed = SEEDS[village]
    bg = Image.new("RGBA", (W_FOND, 540), (0, 0, 0, 255))
    ciel_l(bg, village, seed)
    etoiles_l(bg, seed + 1)
    old = pa.LUNE
    pa.LUNE = (old[0] + OX, old[1], old[2])
    try:
        pa.lune(Calque(bg), bg, village)
        pa.montagnes(bg, village, seed + 3)
        c = bg.crop((OX, 0, OX + 960, 540))
        VILLAGE_FN[village](Calque(c), c, seed + 6)
        bg.paste(c, (OX, 0))
        pa.brume(bg, 262, 10, 0.07, seed + 4, couleur=(140, 120, 170))
        if village == "suna":
            pa.brume(bg, Y_HOR - 16, 12, 0.10, seed + 9, couleur=(190, 140, 96))
        if village == "kiri":
            pa.brume(bg, Y_HOR - 10, 14, 0.14, seed + 9, couleur=(120, 150, 170))
            pa.brume(bg, 280, 12, 0.10, seed + 10, couleur=(110, 140, 160))
        if village == "iwa":
            pa.brume(bg, Y_HOR - 14, 10, 0.08, seed + 9, couleur=(170, 120, 90))
        if village == "ame":
            pluie(bg, seed + 12)
            pa.brume(bg, Y_HOR - 12, 12, 0.10, seed + 9, couleur=(110, 120, 150))
        if village == "kumo":
            pa.brume(bg, 250, 14, 0.09, seed + 9, couleur=(150, 140, 120))
        pa.lumiere(bg, 640, Y_HOR - 20, 520, (120, 80, 140), force=0.07, ry=120, niveaux=4, tramage=0.3)
        pa.eau(Calque(bg), bg, village, seed + 5)
    finally:
        pa.LUNE = old
    return bg


def roseaux(village):
    out = nouveau(520, 180)
    g = nouveau(260, 180)
    pa.roseaux(g, "g", SEEDS[village] + 7, VCOL[village])
    d = nouveau(260, 180)
    pa.roseaux(d, "d", SEEDS[village] + 8, VCOL[village])
    out.alpha_composite(g, (0, 0))
    out.alpha_composite(d, (260, 0))
    return out


# ═════════════════════════════════════ pierres, lotus, ombre, lumières ═════════════════════════════════════
LIT = [("crea", 60, 12.5, 7, (48, 9.2)), ("dais", 68, 16, 7, None), ("sel", 62, 13, 7, (50, 9.5)),
       ("cote", 32, 7, 4, None)]
PIERRES = [("vide", 32, 7, 4, 4), ("ren", 22, 5, 3, 3), ("p15", 15, 3.4, 3, 5), ("p15b", 15, 3.4, 3, 7),
           ("p11", 11, 2.6, 2, 6), ("p10", 10, 2.4, 2, 8), ("p24", 24, 5.4, 4, 9), ("p14", 14, 3.2, 3, 5),
           ("p16", 16, 3.6, 3, 7), ("p9", 9, 2.2, 2, 8)]
LOTUS = [(10, True), (9, True), (8, True), (6, True), (9, False), (8, False), (7, False), (6, False)]


def _pierre_sprite(w, h, cx, cy, rx, ry, ep, lum, seed):
    img = nouveau(w, h)
    pa.pierre(img, cx, cy, rx, ry, ep, lumiere_c=lum, seed=seed)
    a = np.asarray(img).copy()
    ride = (a[..., 3] == 0) & ((a[..., 0] > 0) | (a[..., 1] > 0) | (a[..., 2] > 0))
    a[ride] = (176, 166, 214, 46)
    return Image.fromarray(a, "RGBA")


def pierres_lit():
    out = nouveau(600, 288)
    for vi, v in enumerate(VILLAGES):
        K = VCOL[v]
        for ci, (nom, rx, ry, ep, enso) in enumerate(LIT):
            cell = _pierre_sprite(150, 48, 75, 20, rx, ry, ep, K, 1 if nom != "cote" else 2)
            if enso:
                ex, ey = enso
                col = mix(K, (255, 240, 210), 0.35)
                vus = set()
                for s in range(900):
                    ang = s / 900 * 2 * math.pi
                    if 0.10 * math.pi < ang < 0.22 * math.pi:
                        continue
                    p = (int(round(75 + math.cos(ang) * ex)), int(round(20 + math.sin(ang) * ey)))
                    if p in vus:
                        continue
                    vus.add(p)
                    k = 0.55 + 0.45 * math.sin(ang * 0.5) ** 2
                    pa._px_set(cell, p[0], p[1], col, 0.95 * k)
            out.alpha_composite(cell, (ci * 150, vi * 48))
    return out


def pierres():
    out = nouveau(72 * len(PIERRES), 24)
    for i, (nom, rx, ry, ep, seed) in enumerate(PIERRES):
        out.alpha_composite(_pierre_sprite(72, 24, 36, 9, rx, ry, ep, None, seed), (i * 72, 0))
    return out


def lotus():
    out = nouveau(24 * len(LOTUS), 14)
    for i, (r, fl) in enumerate(LOTUS):
        pa.lotus(out, i * 24 + 12, 8, r, seed=i, fleur=fl)
    return out


def ombre():
    S = 2
    W, H = 80, 24
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    cx, cy = 40, 12
    A = np.zeros_like(xx)
    for (rx, ry, a_) in ((18, 4, 0.30), (11, 2.6, 0.55)):
        e = np.sqrt(((xx + 0.5 - cx) / (rx * S)) ** 2 + ((yy + 0.5 - cy) / (ry * S)) ** 2)
        cov = np.clip((1 - e) * rx * S * 0.5 + 0.5, 0, 1)
        A = np.maximum(A, a_ * cov)
    out = np.zeros((H, W, 4), np.uint8)
    out[..., 3] = np.round(A * 255).astype(np.uint8)
    return out


def masque_lumiere(r, ry, niveaux, expo, tramage):
    w, h = int(2 * r) + 2, int(2 * ry) + 2
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    d = np.sqrt(((xx - r) / r) ** 2 + ((yy - ry) / ry) ** 2)
    A = np.clip(1 - d, 0, 1) ** expo
    q = kit.quantifier(A, niveaux + 1, 0, 0, tramage) / niveaux
    out = np.zeros((h, w, 4), np.uint8)
    out[..., :3] = 255
    out[..., 3] = np.round(np.clip(q, 0, 1) * 255).astype(np.uint8)
    return out


def halo():
    yy, xx = np.mgrid[0:64, 0:64].astype(float)
    d = np.sqrt((xx + 0.5 - 32) ** 2 + (yy + 0.5 - 32) ** 2)
    cov = np.clip(31.5 - d + 0.5, 0, 1)
    a = np.zeros((64, 64, 4), np.uint8)
    a[..., :3] = 255
    a[..., 3] = np.round(cov * 255).astype(np.uint8)
    return a


def liste_clans():
    return sorted(f[5:-4] for f in os.listdir(os.path.join(TEX, "clans")) if f.startswith("clan_") and f.endswith(".png"))


def kamon_sol(clans):
    out = nouveau(416, 30 + 16 * ((len(clans) + 7) // 8))
    vus = set()
    for s_ in range(1200):
        t = s_ / 1200 * 2 * math.pi
        p = (int(round(58 + math.cos(t) * 56)), int(round(15 + math.sin(t) * 12.5)))
        if p not in vus:
            vus.add(p)
            pa._px_set(out, p[0], p[1], (255, 255, 255), 0.80)
    for i, cid in enumerate(clans):
        em = Image.open(os.path.join(TEX, "clans", f"clan_{cid}.png")).convert("RGBA")
        W, H = 50, 15
        a = np.asarray(em.resize((W * 4, H * 4), Image.LANCZOS))[..., 3].astype(float)
        a = a.reshape(H, 4, W, 4).mean(axis=(1, 3))
        x0, y0 = (i % 8) * 52 + 1, 30 + (i // 8) * 16
        for j in range(H):
            for k in range(W):
                if a[j, k] > 70:
                    pa._px_set(out, x0 + k, y0 + j, (255, 255, 255), 0.45 + 0.40 * min(1, a[j, k] / 160))
    return out


def emblemes(clans):
    cles = [("esc", f"village_{v}") for v in VILLAGES] + [("clans", f"clan_{c}") for c in clans]
    out = nouveau(64 * len(cles), 176)
    for i, (dossier, nom) in enumerate(cles):
        em = Image.open(os.path.join(TEX, dossier, nom + ".png")).convert("RGBA")
        for (t, y, filtre) in ((64, 0, Image.LANCZOS), (32, 64, Image.BOX), (24, 96, Image.BOX), (56, 120, Image.LANCZOS)):
            e = em.resize((t, t), filtre if t != em.width else Image.NEAREST)
            a = np.asarray(e).astype(float)
            lum = a[..., :3].mean(axis=2) / 255.0
            o = np.zeros((t, t, 4), np.uint8)
            g = np.clip(255 * (0.55 + 0.45 * lum), 0, 255).astype(np.uint8)
            o[..., 0] = o[..., 1] = o[..., 2] = g
            o[..., 3] = np.where(a[..., 3] > 110, 255, 0)
            coller(out, o, i * 64, y)
    return out


def coller(img, patch, x, y):
    kit.coller(img, patch, x, y)


def brume_avant():
    W, y0, y1 = W_FOND, 360, 540
    lay = nouveau(W, 180)
    h = y1 - y0
    yy, xx = np.mgrid[0:h, 0:W].astype(float)
    ond = (kit.bruit(W, 1, 40, 5, 2)[0] - 0.5) * 14
    t = np.clip((yy - ond[None, :] * 0.6) / (h * 0.55), 0, 1)
    q = kit.quantifier(t, 6, 0, y0, tramage=0.6) / 5.0
    p = np.zeros((h, W, 4), np.uint8)
    p[..., :3] = (34, 26, 46)
    p[..., 3] = (q * 255 * 0.86).astype(np.uint8)
    coller(lay, p, 0, 0)
    rs = random.Random(4)
    for _ in range(35):
        x0 = rs.randint(-20, W - 20)
        yv = int(h * 0.18 + ond[min(W - 1, max(0, x0))] * 0.6 + rs.uniform(-6, 10))
        L = rs.randint(30, 90)
        for i in range(L):
            tt = i / L
            if kit.bayer(1, 1, x0 + i, yv + y0)[0, 0] < math.sin(math.pi * tt) * 0.8:
                pa._px_set(lay, x0 + i, yv, (70, 60, 92), 0.55)
    return lay


# ═════════════════════════════════════ interface ═════════════════════════════════════
def plaque_titre():
    img = nouveau(960, 60)
    pa.plaque_titre(Calque(img), "CHOIX DU SHINOBI")
    return img.crop((480 - 170, 0, 480 + 170, 50))


def progression():
    img = nouveau(960, 40)
    xs = pa.XS_ETAPES
    yfil = 9
    pts = [(xs[0] - 47, 0)] + [(x, yfil) for x in xs] + [(xs[-1] + 47, 0)]
    pa.corde_molle(img, pts, mou=2, couleur="or", ep=1)
    for cx in xs:
        pa.corde_v(img, cx, yfil + 1, 15, "or", ep=1)
    return img.crop((480 - 280, 0, 480 + 280, 32))


def sceaux():
    out = nouveau(240, 102)
    for i, (lib, kj) in enumerate(pa.ETAPES):
        for etat in range(3):
            c = nouveau(30, 60)
            cx = 15
            if etat == 0:
                pa.hanko_px(c, cx, 26, kj, 20, pa.R_VERM, seed=40 + i)
            elif etat == 1:
                T = 24
                kit.ombre_portee(c, cx - T // 2, 15, T, T, dx=1, dy=2, force=0.55, rayon=1)
                pa.lq(c, cx - T // 2 + 1, 16, T - 2, T - 2, "rouge", seed=i)
                pa.liseret(c, cx - T // 2, 15, T, T)
                pa.ferrures3(c, cx - T // 2, 15, T, T)
                kit.anneau_metal(c, cx, 15, 1)
                pa.kj_px(c, cx, 19, kj, pa.OR_CLAIR, ombre=hexc("1A0608"))
            else:
                T = 20
                x = cx - T // 2
                kit.ombre_portee(c, x, 16, T, T, dx=1, dy=1, force=0.5, rayon=0)
                pa.lq(c, x + 1, 17, T - 2, T - 2, "noir", seed=i, intensite=0.7)
                rect(c, x, 16, T, 1, hexc(R_OR[3])); rect(c, x, 16 + T - 1, T, 1, hexc(R_OR[1]))
                rect(c, x, 16, 1, T, hexc(R_OR[2])); rect(c, x + T - 1, 16, 1, T, hexc(R_OR[1]))
                pa.kj_px(c, cx, 18, kj, hexc("5A4632"))
            out.alpha_composite(c.crop((0, 10, 30, 44)), (i * 30, etat * 34))
    return out


def chip():
    img = nouveau(32, 16)
    pa.plaquette(img, 0, 0, 14, 14, "noir", seed=7)
    pa.note_de_musique(img, 3, 3, pa.OR)
    pa.plaquette(img, 16, 0, 14, 14, "noir", seed=8)
    pa.onde(img, 19, 3, pa.OR)
    return img


def perche():
    img = nouveau(300, 16)
    pa.perche(img, 0, 300, 1, "d")
    pa.perche(img, 0, 300, 9, "g")
    return img


def regle_fond(px, x, y, w, vmin, vmax, pas_grand=10, fin=False):
    """pa.regle sans la partie mobile (fil or et curseur) : bois et crans."""
    pa.lq(px, x, y, w, 9, ["2C170C", "3B2012", "4B2A17", "5C351D", "6E4124"], seed=x)
    rect(px, x, y, w, 1, hexc("8A5A34"))
    rect(px, x, y + 8, w, 1, hexc("160A06"))
    n = vmax - vmin
    for val in range(vmin, vmax + 1):
        X = int(round(x + 2 + (val - vmin) / n * (w - 4)))
        grand = val % pas_grand == 0
        moyen = val % 5 == 0
        hh = 6 if grand else (4 if moyen else 2)
        if fin or grand or moyen or n <= 60:
            rect(px, X, y + 1, 1, hh, hexc(R_OR[5] if grand else (R_OR[3] if moyen else "6A4A2A")))


# planches : nom -> (w, h, titre, kanji, gland, seed)
PLANCHES = {
    "village": (240, 300, "VILLAGE", "里", "bd", 3),
    "clan": (240, 330, "CLAN", "血", "bd", 6),
    "sexe": (240, 232, "SEXE", "性", "bd", 8),
    "age": (240, 184, "AGE", "齢", "bd", 9),
    "taille": (240, 172, "TAILLE", "丈", "bd", 10),
    "apparence": (286, 352, "APPARENCE", "貌", "bd", 12),
    "tenue": (276, 352, "TENUE", "衣", "bg", 13),
    "dossier": (240, 254, "DOSSIER", "影", "bd", 14),
    "registre": (240, 214, "TON REGISTRE", "籍", "bg", 4),
    "registre_recap": (240, 262, "TON REGISTRE", "籍", "bg", 4),
}


def planche_tex(nom):
    w, h, titre, kj, gland, seed = PLANCHES[nom]
    x, y = 24, YP_C + 12
    img = nouveau(960, 540)
    m = Calque(img)
    pa.Y_PERCHE = YP_C
    pa.planche(m, x, y, w, h, titre, gland=gland, seed=seed, kanji_titre=kj, ancrages=(x + 34, x + w - 36))
    X, WL = x + 14, w - 28
    if nom in ("age", "taille"):
        yy = y + 50
        pa.pm(m, X + WL - 44, int(yy) - 7)
        ry = int(yy) + 22
        if nom == "age":
            regle_fond(img, X + 2, ry, WL - 4, 10, 60)
        else:
            regle_fond(img, X + 2, ry, WL - 4, 153, 207, fin=True)
            rect(img, X, ry + 44, WL, 1, pa.SEP)
    elif nom == "dossier":
        wx, wy, ww, wh = x + 14, y + 36, 96, 112
        kit.washi(img, wx, wy, ww, wh, ton="ancien", seed=5, bords="nets")
        rect(img, wx + 4, wy + 4, ww - 8, wh - 8, hexc("3A2A2E"))
        pa.lumiere(img, wx + ww / 2, wy + 40, 60, hexc("F2A548"), force=0.10, ry=60, niveaux=3)
        cx0 = wx + ww + 10
        pa.plaquette(img, cx0 + 16, wy + 6, 44, 44, "rouge", seed=6)
    elif nom.startswith("registre"):
        yy = y + 46
        for k in range(6):
            rect(img, X, int(yy + 12), WL, 1, pa.SEP)
            yy += 24
        if nom == "registre_recap":
            pa.hanko_px(img, x + w - 52, y + 222, "認", 30, pa.R_VERM, seed=77, usure=0.18)
    return img.crop((x - 4, y - 16, x + w + 4, y + h + 28))


def plaque_nom():
    img = nouveau(960, 540)
    m = Calque(img)
    x, y, w, h = 300, 388, 360, 64
    pa.panneau(img, x, y, w, h, seed=13)
    pa.bandeau_titre(m, x, y, w, "NOM ET PRENOM", "名")
    rect(img, x + 20, y + 51, w - 40, 1, hexc(R_OR[3]))
    return img.crop((x - 2, y - 2, x + w + 4, y + h + 5))


# tuiles : nom -> (w, h, états)
TUILES = {
    "village": (220, 38, ("normal", "survol", "actif")),
    "clan": (70, 64, ("normal", "survol", "actif", "verrou")),
    "sexe": (104, 168, ("normal", "survol", "actif")),
    "age": (68, 34, ("normal", "survol", "actif")),
    "cheveux": (60, 46, ("normal", "survol", "actif")),
    "tenue": (80, 92, ("normal", "survol", "actif")),
    "vue": (66, 84, ("normal", "survol", "actif")),
}


def tuiles(nom):
    w, h, etats = TUILES[nom]
    cw, ch = w + 4, h + 5
    out = nouveau(cw * len(etats), ch)
    for i, e in enumerate(etats):
        c = nouveau(cw, ch)
        if e == "verrou":
            pa.lq(c, 2, 2, w - 2, h - 2, "noir", seed=20 + i, intensite=0.7)
            cc = hexc(R_OR[1])
            rect(c, 1, 1, w, 1, cc); rect(c, 1, h, w, 1, hexc(R_OR[1]))
            rect(c, 1, 1, 1, h, cc); rect(c, w, 1, 1, h, hexc(R_OR[1]))
            pa.cadenas(c, w - 11, 5)
        else:
            pa.tuile(c, 1, 1, w, h, e, seed=20 + i + w)
        out.alpha_composite(c, (i * cw, 0))
    return out


# boutons : (clé, libellé, kanji, ton, largeur)
def _lb(t):
    return pa.largeur_bouton(t)


BOUTONS = [
    ("retour", "RETOUR", "戻", "noir", _lb("RETOUR")),
    ("suivant", "SUIVANT", "進", "rouge", _lb("SUIVANT") + 10),
    ("entrer", "ENTRER EN JEU", "入", "rouge", _lb("ENTRER EN JEU") + 16),
    ("creer", "CREER", "新", "rouge", _lb("ENTRER EN JEU") + 16),
    ("oui", "OUI, C'EST MOI", "是", "rouge", _lb("OUI, C'EST MOI") + 10),
    ("hasard", "AU HASARD", "運", "noir", _lb("AU HASARD")),
    ("large", "LARGE", "剛", "rouge", 70),
    ("fine", "FINE", "柔", "rouge", 70),
]


def boutons():
    wmax = max(b[4] for b in BOUTONS) + 4
    out = nouveau(wmax * 3, 24 * len(BOUTONS))
    for r, (cle, lib, kj, ton, w) in enumerate(BOUTONS):
        for e, etat in enumerate(("normal", "survol", "inactif")):
            c = nouveau(w + 4, 24)
            m = Calque(c)
            if cle in ("large", "fine"):
                # normal = non choisi (noir neutre), survol = survol (noir), inactif = CHOISI (rouge)
                if etat == "inactif":
                    pa.bouton(m, 1, 1, lib, "survol", kj=kj, ton="rouge", w=w)
                else:
                    pa.bouton(m, 1, 1, lib, "neutre" if etat == "normal" else "survol", kj=kj, ton="noir", w=w)
            elif ton == "rouge" and etat == "survol":
                pa.bouton(m, 1, 1, lib, "survol", kj=kj, ton="rouge", w=w)
                a = np.asarray(c).astype(float)
                inner = np.zeros(a.shape[:2], bool)
                inner[3:20, 3:w - 1] = True
                a[inner, :3] = np.minimum(255, a[inner, :3] * 1.18 + 6)
                c = Image.fromarray(a.astype(np.uint8), "RGBA")
            else:
                pa.bouton(m, 1, 1, lib, etat, kj=kj, ton=ton, w=w)
            out.alpha_composite(c, (e * wmax, r * 24))
    return out, wmax


def fleches():
    out = nouveau(88, 23)
    for k, intens in enumerate((1.1, 1.8)):
        for i, sens in enumerate(("g", "d")):
            c = nouveau(22, 23)
            m = Calque(c)
            kit.ombre_portee(c, 1, 1, 20, 20, dx=1, dy=2, force=0.6, rayon=1)
            pa.lq(c, 2, 2, 18, 18, "noir", seed=i, intensite=intens)
            pa.liseret(c, 1, 1, 20, 20)
            pa.ferrures3(c, 1, 1, 20, 20)
            for j in range(7):
                ww = 4 - abs(j - 3)
                for q in range(ww):
                    xx = 1 + 8 + q if sens == "d" else 1 + 11 - q
                    rect(c, xx, 1 + 6 + j, 1, 1, hexc(R_OR[5] if j < 4 else R_OR[4]))
            del m
            out.alpha_composite(c, (k * 44 + i * 22, 0))
    return out


def curseurs():
    out = nouveau(64, 16)
    X, y = 5, 6
    kit.ombre_portee(out, X - 4, y - 5, 9, 10, dx=1, dy=1, force=0.5, rayon=0)
    pa.lq(out, X - 3, y - 4, 7, 8, "rouge", seed=X)
    pa.liseret(out, X - 4, y - 5, 9, 10)
    for j in range(3):
        rect(out, X - 2 + j, y + 5 + j, 5 - 2 * j, 1, hexc(R_OR[5]))
    bx, Y = 20, 7
    kit.ombre_portee(out, bx, Y - 7, 40, 14, dx=1, dy=1, force=0.55, rayon=0)
    pa.lq(out, bx + 1, Y - 6, 38, 12, "rouge", seed=5)
    pa.liseret(out, bx, Y - 7, 40, 14)
    for j in range(4):
        rect(out, bx - 1 - j, Y - j, 1, 2 * j + 1, hexc(R_OR[5]))
    return out


def toise():
    img = nouveau(24, 314)
    x, ysol = 6, 310
    y_cm = lambda c: ysol - c * GUI_PAR_CM  # noqa: E731
    top = int(round(y_cm(214)))
    pa.mat_bambou(img, x, top, ysol + 2, seed=3)
    for c in range(0, 211, 5):
        Y = int(round(y_cm(c)))
        grand = c % 10 == 0
        rect(img, x + 6, Y, 5 if grand else 3, 1, hexc(R_OR[5] if grand else R_OR[3]))
    for c in (153, 207):
        Y = int(round(y_cm(c)))
        rect(img, x - 3, Y, 14, 1, hexc("8E2A2A"))
    return img, top


def ornements():
    img = nouveau(48, 10)
    pa.losange_or(img, 0, 0)
    pa.cadenas(img, 4, 0)
    pa.chevron(img, 14, 0)
    for j in range(3):
        rect(img, 18 + j, j, 5 - 2 * j, 1, hexc(R_OR[6]))
    return img


def cabochon():
    img = nouveau(42, 12)
    x, y, w, h = 0, 0, 40, 10
    kit.ombre_portee(img, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
    pa.lq(img, x + 1, y + 1, w - 2, h - 2, "noir", seed=x, intensite=1.2)
    rect(img, x + 1, y, w - 2, 1, hexc(R_OR[4])); rect(img, x + 1, y + h - 1, w - 2, 1, hexc(R_OR[2]))
    rect(img, x, y + 1, 1, h - 2, hexc(R_OR[3])); rect(img, x + w - 1, y + 1, 1, h - 2, hexc(R_OR[2]))
    return img


def cartouche():
    img = nouveau(960, 540)
    x, y, w, h = 24, 440, 290, 64
    lay = nouveau(960, 540)
    pa.panneau(lay, x, y, w, h, seed=11)
    a = np.asarray(lay).copy()
    a[..., 3] = (a[..., 3].astype(float) * 0.94).astype(np.uint8)
    img.alpha_composite(Image.fromarray(a, "RGBA"))
    pa.plaquette(img, x + 10, y + 12, 40, 40, "rouge", seed=3)
    return img.crop((x - 2, y - 2, x + w + 4, y + h + 4))


def silhouette():
    sk = perso.preset("nue")
    im = perso.rendu(sk, 9, 10, 4, ombre_sol=False, relief=False, pose=perso.POSE_REPOS)
    fx, fy = im.info["pieds"]
    # passage au pas GUI (9 px écran par texel -> 4,5 GUI par texel)
    small = im.resize((im.width // 2, im.height // 2), Image.NEAREST)
    a = np.asarray(small)[..., 3] > 0
    H_, W_ = a.shape
    P = np.pad(a, 1)
    inner = P[2:, 1:-1] & P[:-2, 1:-1] & P[1:-1, 2:] & P[1:-1, :-2]
    b = a & ~inner
    yy, xx = np.mgrid[0:H_, 0:W_]
    pointille = b & (((yy // 2 + xx // 2) % 2) == 0)
    out = np.zeros((H_, W_, 4), np.uint8)
    c = np.array(pa.OR[:3], np.uint8)
    out[a, :3] = c
    ys = yy[a]
    grad = np.clip((yy - ys.min()) / max(1, (ys.max() - ys.min())), 0, 1)
    out[a, 3] = (14 + 40 * grad[a] ** 2).astype(np.uint8)
    out[pointille, :3] = mix(c, (255, 240, 200), 0.3)
    out[pointille, 3] = 175
    fig = Image.fromarray(out, "RGBA")
    res = nouveau(80, 150)
    px, py = int(round(fx / 2)), int(round(fy / 2))
    res.alpha_composite(fig, (40 - px, 146 - py))
    return res


# ═════════════════════════════════════ sortie ═════════════════════════════════════
def main():
    seul = set(sys.argv[1:])

    def veut(k):
        return not seul or k in seul

    clans = liste_clans()
    if veut("fonds"):
        for v in VILLAGES:
            sauver(f"fond_{v}.png", fond(v))
            sauver(f"roseaux_{v}.png", roseaux(v))
    if veut("scene"):
        sauver("pierres_lit.png", pierres_lit())
        sauver("pierres.png", pierres())
        sauver("lotus.png", lotus())
        sauver("ombre.png", ombre())
        sauver("lum_cle.png", masque_lumiere(150, 170, 6, 1.4, 0.25), garder_rgb=True)
        sauver("lum_flaque.png", masque_lumiere(130, 28, 4, 2.0, 0.3), garder_rgb=True)
        sauver("halo.png", halo(), garder_rgb=True)
        sauver("kamon_sol.png", kamon_sol(clans))
        sauver("emblemes.png", emblemes(clans))
        sauver("brume_avant.png", brume_avant())
        sauver("silhouette.png", silhouette())
    if veut("ui"):
        sauver("plaque_titre.png", plaque_titre())
        sauver("progression.png", progression())
        sauver("sceaux.png", sceaux())
        sauver("chip.png", chip())
        sauver("perche.png", perche())
        for nom in PLANCHES:
            sauver(f"panneau_{nom}.png", planche_tex(nom))
        sauver("plaque_nom.png", plaque_nom())
        for nom in TUILES:
            sauver(f"tuiles_{nom}.png", tuiles(nom))
        b, wmax = boutons()
        sauver("boutons.png", b)
        sauver("fleches.png", fleches())
        sauver("curseurs.png", curseurs())
        t, top = toise()
        sauver("toise.png", t)
        sauver("ornements.png", ornements())
        sauver("cabochon.png", cabochon())
        sauver("cartouche.png", cartouche())
        print("boutons : cellule", wmax, "largeurs", {k: w for (k, _, _, _, w) in BOUTONS})
        print("toise : sommet texel", top)
    print("clans :", clans)
    total = 0
    for (nom, taille, octets) in ECRITS:
        total += octets
        print(f"  {nom:28s} {taille[0]:5d}x{taille[1]:<5d} {octets / 1024:7.1f} Ko")
    print(f"total {total / 1024:.0f} Ko")


if __name__ == "__main__":
    main()
