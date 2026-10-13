# -*- coding: utf-8 -*-
"""
perso.py — le VRAI personnage Reborn pour les maquettes.

Compose un skin Minecraft 64x64 à partir des couches du créateur de perso du mod
(reborn-hud), exactement comme ``fr.reborn.hud.skin.RebornSkins#compose``, puis le
rend en vue de face ou de trois quarts (rasteriseur logiciel à z-buffer, modèle joueur
Minecraft aux proportions exactes, seconde couche gonflée comme en jeu).

Lecture seule des assets du dépôt :
    RBLAUNCHER/minecraft/mod-hud/src/main/resources/assets/reborn/textures/character/

API
---
    compose(sexe='homme', teint=4, tenue='', cheveux='Cheveux_Base',
            couleur_cheveux='3A2416', yeux='5A3A1E', **options) -> Image 64x64 RGBA
    rendu_face(skin, echelle=8, **options)        -> Image RGBA (fond transparent)
    rendu_trois_quarts(skin, echelle=8, **options) -> Image RGBA (fond transparent)
    rendu(skin, echelle, lacet, tangage, ...)      -> rendu générique (angle libre, pose)
    rendu_buste(skin, echelle=12, ...)             -> tête + épaules (photo de dossier)
    preset(nom) -> skin 64x64           ('ren_hyuga', 'ren_hyuga_f', 'ren_hyuga_combat',
                                         'yui_ceremonie', 'aki_uchiha', 'nue')
    poser(fond, rendu, pieds_xy)        colle un rendu en alignant ses pieds sur un point
    planche_catalogue(categorie, ...)   planche de choix (tenues, cheveux…) pour les artistes

Les images de rendu portent dans ``img.info`` :
    'pieds'   (x, y) du point au sol entre les deux pieds (pour poser le perso)
    'tete'    (x, y) du sommet du crâne (base, sans la seconde couche)
    'echelle' pixels par texel
    'slim'    carrure Alex (bras 3 px)

Usage type (maquette 960x540 -> x2) : le personnage est une ENTITÉ 3D rendue par le jeu
à la résolution de l'écran, pas à la grille GUI. On le rend donc directement sur l'image
FINALE 1920x1080 (après l'agrandissement x2) avec ``poser(final, rendu_face(skin, 10), (x*2, y*2))``.
Échelles typiques en 1080p : 5-6 px/texel (vignette), 8-10 (portrait de fiche), 12-14 (scène).
"""
from __future__ import annotations

import io
import json
import math
import os
import unicodedata

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# ───────────────────────── chemins ─────────────────────────
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))  # racine RBLAUNCHER
CHAR = os.path.join(REPO, r"minecraft\mod-hud\src\main\resources\assets\reborn\textures\character")

# ───────────────────────── constantes du mod (SkinSpec / RebornSkins) ─────────────────────────
SKIN_TEMPLATE = 4                      # SkinSpec.SKIN_TEMPLATE : <genre>/4.png = template de luminance
DEFAULT_SKIN = 0xE6B385
DEFAULT_BROW = 0x241A10
DEFAULT_HAIR = 0x3A2416
DEFAULT_EYE = 0x5A3A1E
UNDERWEAR_DEFAULT = 0xB0A090           # RebornSkins.UNDERWEAR_DEFAULT
DEFAULT_HAIR_ID = "Cheveux_Base"
DEFAULT_EYE_ID = "Yeux_Style1"

# Rampe de tons chair, pâle -> foncé (SkinSpec.SKIN_RAMP, 12 crans)
RAMPE_PEAU = [0xFFDDC6, 0xFEDAB4, 0xFFD1A8, 0xF6C59E, 0xE6B385, 0xD7A575,
              0xC19066, 0xAD7A61, 0x99664D, 0x87573B, 0x704228, 0x5B351B]


def _jround(x):
    """Math.round de Java (arrondi demi vers +inf), vectorisé."""
    return np.floor(np.asarray(x, dtype=np.float64) + 0.5)


def couleur(c):
    """'#RRGGBB' | 'RRGGBB' | 0xRRGGBB | (r, g, b) -> (r, g, b)."""
    if c is None:
        return None
    if isinstance(c, (tuple, list)):
        return tuple(int(v) for v in c[:3])
    if isinstance(c, str):
        c = int(c.strip().lstrip('#'), 16)
    return ((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF)


def teinte_peau(t):
    """Couleur de peau. int 0..11 = cran de la rampe ; float 0..1 = position (interpolée
    comme SkinSpec.skinRamp) ; sinon une couleur ('E6B385', (r,g,b)…)."""
    if isinstance(t, bool):
        t = int(t)
    if isinstance(t, int) and 0 <= t < len(RAMPE_PEAU):
        return couleur(RAMPE_PEAU[t])
    if isinstance(t, float):
        t = min(1.0, max(0.0, t))
        f = t * (len(RAMPE_PEAU) - 1)
        i = int(f)
        if i >= len(RAMPE_PEAU) - 1:
            return couleur(RAMPE_PEAU[-1])
        a, b, k = couleur(RAMPE_PEAU[i]), couleur(RAMPE_PEAU[i + 1]), f - i
        return tuple(int(_jround(a[j] + (b[j] - a[j]) * k)) for j in range(3))
    return couleur(t)


# ───────────────────────── catalogue ─────────────────────────
_CAT = None


def catalogue():
    """catalog.json du mod : {categorie: [asset…]} (hair, outfit, eyes, underwear, …)."""
    global _CAT
    if _CAT is None:
        with open(os.path.join(CHAR, "catalog.json"), encoding="utf-8") as f:
            raw = json.load(f)
        _CAT = {k: v for k, v in raw.items() if not k.startswith("_")}
    return _CAT


def asset(categorie, ident):
    if not ident:
        return None
    for a in catalogue().get(categorie, []):
        if a["id"] == ident:
            return a
    # le mod résout n'importe quel id présent dans le catalogue ; on tolère aussi un PNG
    # présent sur disque mais absent du JSON (tint none), pratique pour explorer.
    p = os.path.join(CHAR, categorie, ident + ".png")
    return {"id": ident, "tint": "none"} if os.path.exists(p) else None


_TEX = {}


def _png(rel):
    if rel not in _TEX:
        _TEX[rel] = np.array(Image.open(os.path.join(CHAR, rel)).convert("RGBA"), dtype=np.uint8)
    return _TEX[rel].copy()


def _luma(a):
    a = a.astype(np.float32)
    return np.float32(0.299) * a[..., 0] + np.float32(0.587) * a[..., 1] + np.float32(0.114) * a[..., 2]


# ───────────────────────── composition (port de RebornSkins.compose) ─────────────────────────
def _base_peau(female, skin_rgb, brow_rgb):
    """composeSkinBase : template de luminance recoloré, sourcils cuits recolorés."""
    t = _png(f"skin/{'female' if female else 'male'}/{SKIN_TEMPLATE}.png")
    op = t[..., 3] != 0
    lum = _luma(t)
    vals = np.sort(lum[op])
    ref = float(vals[len(vals) // 2]) if len(vals) else 105.0
    if ref <= 1.0:
        ref = 105.0
    brow = np.zeros((64, 64), bool)
    brow[8:16, 8:16] = op[8:16, 8:16] & (lum[8:16, 8:16] < np.float32(0.55) * np.float32(ref))
    f = lum / np.float32(ref)
    out = t.copy()
    for j in range(3):
        out[..., j] = np.clip(_jround(skin_rgb[j] * f), 0, 255).astype(np.uint8)
    out[..., 3] = np.where(op, 255, out[..., 3])
    for j in range(3):
        out[..., j] = np.where(brow, brow_rgb[j], out[..., j])
    # pixels transparents : on garde la transparence (RGB indifférent)
    out[~op] = t[~op]
    return out


def _is_red(a):
    r, g, b = (a[..., i].astype(np.float32) for i in range(3))
    return (r > g * np.float32(1.5)) & (r > b * np.float32(1.5)) & (r > 50)


def _overlay(img, cat, ident, couleurs):
    """overlayAsset : chaque pixel opaque de l'asset REMPLACE celui du skin (setPixel,
    alpha compris) ; teinte 'all' (luminance préservée) ou 'red' (iris, split G/D)."""
    a = asset(cat, ident)
    if a is None:
        return
    src = _png(f"{cat}/{a['id']}.png")
    op = src[..., 3] != 0
    if not op.any():
        return
    lum = _luma(src)
    ref_luma = max(1.0, float(lum[op].max()))
    red = _is_red(src) & op
    ref_red = max(1, int(src[..., 0][red].max())) if red.any() else 1
    tint = (a.get("tint") or "none").lower()
    out = src.copy()
    if tint == "all":
        col = couleur(couleurs[0]) if couleurs else (255, 255, 255)
        f = np.minimum(np.float32(1.0), lum / np.float32(ref_luma))
        for j in range(3):
            out[..., j] = _jround(col[j] * f).astype(np.uint8)
        out[..., 3] = 255
    elif tint == "red":
        split = int(a.get("split", 11))
        left = couleur(couleurs[0]) if couleurs else (255, 255, 255)
        right = couleur(couleurs[1]) if len(couleurs) > 1 and couleurs[1] is not None else left
        f = np.minimum(np.float32(1.0), src[..., 0].astype(np.float32) / np.float32(ref_red))
        xs = np.arange(64)[None, :].repeat(64, 0)
        for j in range(3):
            c = np.where(xs <= split, left[j], right[j]).astype(np.float32)
            out[..., j] = np.where(red, _jround(c * f), src[..., j]).astype(np.uint8)
        out[..., 3] = np.where(red, 255, src[..., 3])
    img[op] = out[op]


def compose(sexe="homme", teint=4, tenue="", cheveux=DEFAULT_HAIR_ID, couleur_cheveux=DEFAULT_HAIR,
            yeux=DEFAULT_EYE, *, sourcils=DEFAULT_BROW, yeux_style=DEFAULT_EYE_ID,
            pilosite="", couleur_pilosite=DEFAULT_HAIR, accessoire="", couleur_accessoire=0xFFFFFF,
            sous_vetement=UNDERWEAR_DEFAULT, slim=None):
    """Skin 64x64 RGBA composé comme en jeu.

    sexe            'homme' | 'femme' (template de peau + sous-vêtement ; femme = toujours slim)
    teint           cran 0..11 de la rampe, float 0..1, ou couleur
    tenue           id d'outfit du catalogue ('' = sous-vêtement seul) : Complet_*, tenueh1..47
    cheveux         id hair ('' = chauve) ; couleur_cheveux = teinte (luminance du PNG gardée)
    yeux            couleur d'iris, ou (gauche, droite) pour l'hétérochromie
    sourcils        couleur des sourcils cuits dans le template
    slim            None = auto (femme -> True, homme -> False)

    Ordre des calques = RebornSkins.compose : peau -> sous-vêtement -> tenue -> yeux ->
    pilosité -> cheveux -> accessoire.
    """
    female = str(sexe).lower().startswith(("f", "w"))
    if slim is None:
        slim = female
    slim = bool(slim or female)
    img = _base_peau(female, teinte_peau(teint), couleur(sourcils))
    _overlay(img, "underwear", "Sous_Femme" if female else "Sous_Homme", [sous_vetement])
    _overlay(img, "outfit", tenue, [0xFFFFFF] * 4)
    ycols = list(yeux) if isinstance(yeux, (tuple, list)) and len(yeux) == 2 and not isinstance(yeux[0], int) \
        else [yeux, yeux]
    _overlay(img, "eyes", yeux_style, ycols)
    _overlay(img, "facial", pilosite, [couleur_pilosite])
    _overlay(img, "hair", cheveux, [couleur_cheveux])
    _overlay(img, "accessory", accessoire, [couleur_accessoire])
    im = Image.fromarray(img, "RGBA")
    im.info["slim"] = slim
    im.info["reborn"] = dict(sexe="femme" if female else "homme", tenue=tenue, cheveux=cheveux)
    return im


# ───────────────────────── préréglages ─────────────────────────
# Choix faits sur planche_catalogue('outfit') : Complet_Ceremonie = kimono croisé blanc,
# obi noir, hakama clair (le plus proche d'un Hyūga) ; Cheveux_Itachi = longs, attachés
# dans le dos, sombres ; iris lavande très pâle (Byakugan au repos). Variante féminine :
# Complet_Femme1 (haut blanc croisé, hakama mauve : la tenue claire dessinée pour la
# carrure Alex ; Complet_Ceremonie sur une femme laisse voir la brassière grise dans le
# col), cheveux longs lâchés bleu nuit (Cheveux_Femme). Autres tenues claires utiles :
# tenueh46 (chemise blanche, bandes aux bras, pantalon sombre), tenueh40, tenueh20.
PRESETS = {
    "ren_hyuga": dict(sexe="homme", teint=3, tenue="Complet_Ceremonie", cheveux="Cheveux_Itachi",
                      couleur_cheveux="2B2124", yeux="E4DDF0", sourcils="2A2022"),
    "ren_hyuga_f": dict(sexe="femme", teint=2, tenue="Complet_Femme1", cheveux="Cheveux_Femme",
                        couleur_cheveux="262035", yeux="E4DDF0", sourcils="262035"),
    "ren_hyuga_combat": dict(sexe="homme", teint=3, tenue="tenueh46", cheveux="Cheveux_Itachi",
                             couleur_cheveux="2B2124", yeux="E4DDF0", sourcils="2A2022"),
    "yui_ceremonie": dict(sexe="femme", teint=1, tenue="Complet_Ceremonie2", cheveux="Cheveux_Tsunade",
                          couleur_cheveux="1E1A24", yeux="6A3A2A", sourcils="1E1A24"),
    "aki_uchiha": dict(sexe="homme", teint=4, tenue="Complet_Uchiha", cheveux="Cheveux_Sasuke",
                       couleur_cheveux="1C1A22", yeux="241A1A"),
    "nue": dict(sexe="homme", teint=4, tenue="", cheveux="Cheveux_Base"),
}


def preset(nom):
    """Skin d'un préréglage (voir PRESETS ; modifiable/étendable par les artistes)."""
    return compose(**PRESETS[nom])


# ───────────────────────── modèle joueur ─────────────────────────
# Unités = texels. Pieds à y=0, sommet du crâne à y=32. X vers la droite de l'écran quand le
# perso fait face (son bras DROIT est donc à x<0), Z vers le spectateur.
def _parts(slim):
    aw = 3 if slim else 4
    #        nom        boîte (x0,y0,z0,x1,y1,z1)    uv base   uv couche 2  gonflement  pivot
    return [("tete",    (-4, 24, -4, 4, 32, 4),      (0, 0),   (32, 0),     0.5,  (0, 24, 0)),
            ("corps",   (-4, 12, -2, 4, 24, 2),      (16, 16), (16, 32),    0.25, (0, 24, 0)),
            ("bras_d",  (-4 - aw, 12, -2, -4, 24, 2), (40, 16), (40, 32),   0.25, (-5, 22, 0)),
            ("bras_g",  (4, 12, -2, 4 + aw, 24, 2),  (32, 48), (48, 48),    0.25, (5, 22, 0)),
            ("jambe_d", (-4, 0, -2, 0, 12, 2),       (0, 16),  (0, 32),     0.25, (-2, 12, 0)),
            ("jambe_g", (0, 0, -2, 4, 12, 2),        (16, 48), (0, 48),     0.25, (2, 12, 0))]


def _faces(box, uv, g):
    """Faces d'un pavé (convention UV des modèles Minecraft). Renvoie
    [(P0, Eu, Ev, (tu, tv, tw, th), normale)] ; P0 = coin haut-gauche de la texture."""
    x0, y0, z0, x1, y1, z1 = box
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    u, v = uv
    X0, Y0, Z0, X1, Y1, Z1 = x0 - g, y0 - g, z0 - g, x1 + g, y1 + g, z1 + g
    W, H, D = X1 - X0, Y1 - Y0, Z1 - Z0
    return [
        ((X0, Y1, Z1), (W, 0, 0), (0, -H, 0), (u + d, v + d, w, h), (0, 0, 1)),            # avant
        ((X1, Y1, Z0), (-W, 0, 0), (0, -H, 0), (u + 2 * d + w, v + d, w, h), (0, 0, -1)),  # arrière
        ((X0, Y1, Z0), (0, 0, D), (0, -H, 0), (u, v + d, d, h), (-1, 0, 0)),               # côté droit du perso
        ((X1, Y1, Z1), (0, 0, -D), (0, -H, 0), (u + d + w, v + d, d, h), (1, 0, 0)),       # côté gauche du perso
        ((X0, Y1, Z0), (W, 0, 0), (0, 0, D), (u + d, v, w, d), (0, 1, 0)),                 # dessus
        ((X0, Y0, Z1), (W, 0, 0), (0, 0, -D), (u + d + w, v, w, d), (0, -1, 0)),           # dessous
    ]


def _rot(rx=0.0, ry=0.0, rz=0.0):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


# Éclairage par face (style Minecraft : faces à ombrage constant), pondéré par la normale.
ECLAIRAGE = dict(avant=1.0, arriere=0.62, gauche=0.70, droite=0.82, dessus=1.06, dessous=0.50)


def _lumiere(n, ecl):
    nx, ny, nz = n
    return (nx * nx * (ecl["droite"] if nx < 0 else ecl["gauche"]) +
            ny * ny * (ecl["dessus"] if ny > 0 else ecl["dessous"]) +
            nz * nz * (ecl["avant"] if nz > 0 else ecl["arriere"]))


def rendu(skin, echelle=8, lacet=0.0, tangage=0.0, *, slim=None, pose=None, couche2=True,
          gonflement=True, relief=True, ombre_sol=True, ombre_couleur=(8, 4, 6), eclairage=None,
          marge=None):
    """Rendu orthographique du modèle joueur.

    echelle   pixels par texel (rendu à la résolution de l'image où on le pose)
    lacet     rotation autour de l'axe vertical, en degrés (>0 : on voit le flanc DROIT du
              perso, à gauche de l'écran ; <0 : le flanc gauche)
    tangage   caméra au-dessus (>0) : on voit le dessus de la tête
    pose      {'tete': (rx, ry, rz), 'bras_d': …, 'bras_g', 'jambe_d', 'jambe_g', 'corps'} en
              degrés autour des pivots (cou, épaules, hanches). rx>0 bascule le HAUT de la
              pièce vers le spectateur (tête baissée ; bras/jambe partent en arrière, donc
              rx<0 = bras levé vers l'avant) ; ry tourne la pièce ; rz<0 écarte le bras droit,
              rz>0 le bras gauche. Voir POSE_REPOS, POSE_GARDE.
    couche2   dessiner la seconde couche (chapeau, veste, manches, jambières)
    gonflement couche 2 gonflée comme en jeu (+0,5 tête, +0,25 ailleurs). False = calée sur
              la grille des texels (plus « propre » en pixel art, moins fidèle)
    relief    ombre portée douce de la couche 2 sur la couche 1 (donne l'épaisseur)
    ombre_sol ombre douce elliptique sous les pieds
    """
    if slim is None:
        slim = bool(skin.info.get("slim", False))
    ecl = dict(ECLAIRAGE, **(eclairage or {}))
    pose = pose or {}
    tex = np.array(skin.convert("RGBA"), dtype=np.uint8)
    s = float(echelle)
    cam = _rot(rx=tangage) @ _rot(ry=-lacet)

    # 1) collecte des faces transformées (espace caméra, unités texel)
    faces = []   # (couche, pièce, P0, Eu, Ev, rect, lum, nz)
    for pid, (nom, box, uvb, uvo, g, piv) in enumerate(_parts(slim)):
        R = _rot(*pose.get(nom, (0, 0, 0)))
        piv = np.array(piv, float)
        layers = [(0, uvb, 0.0)]
        if couche2:
            layers.append((1, uvo, g if gonflement else 0.02))
        for couche, uv, gg in layers:
            for P0, Eu, Ev, rect, n in _faces(box, uv, gg):
                P0 = cam @ (R @ (np.array(P0, float) - piv) + piv)
                Eu = cam @ (R @ np.array(Eu, float))
                Ev = cam @ (R @ np.array(Ev, float))
                nm = R @ np.array(n, float)          # normale « monde » pour l'éclairage
                nc = cam @ nm                        # normale caméra pour le culling
                if couche == 0 and nc[2] <= 1e-6:
                    continue                         # couche 1 : faces arrière éliminées
                faces.append((couche, pid, P0, Eu, Ev, rect, _lumiere(nm, ecl), nc[2]))

    # 2) cadre de l'image
    pts = []
    for _, _, P0, Eu, Ev, *_ in faces:
        for a, b in ((0, 0), (1, 0), (0, 1), (1, 1)):
            q = P0 + a * Eu + b * Ev
            pts.append((q[0], -q[1]))
    pts = np.array(pts) * s
    m = marge if marge is not None else int(math.ceil(3 * s))
    minx, miny = np.floor(pts.min(0)) - m
    maxx, maxy = np.ceil(pts.max(0)) + m
    W, H = int(maxx - minx), int(maxy - miny)
    ox, oy = -minx, -miny

    col = [np.zeros((H, W, 3), np.float32), np.zeros((H, W, 3), np.float32)]
    dep = [np.full((H, W), -1e9, np.float32), np.full((H, W), -1e9, np.float32)]
    ids = [np.full((H, W), -1, np.int16), np.full((H, W), -1, np.int16)]

    # 3) rastérisation (centre de pixel, échantillonnage au plus proche, z-buffer)
    for couche, pid, P0, Eu, Ev, (tu, tv, tw, th), lum, nz in faces:
        s0 = np.array([P0[0] * s + ox, -P0[1] * s + oy])
        su = np.array([Eu[0] * s, -Eu[1] * s])
        sv = np.array([Ev[0] * s, -Ev[1] * s])
        det = su[0] * sv[1] - su[1] * sv[0]
        if abs(det) < 1e-6 * s * s:
            continue                                 # face vue par la tranche
        corners = np.array([s0, s0 + su, s0 + sv, s0 + su + sv])
        bx0, by0 = np.floor(corners.min(0)).astype(int)
        bx1, by1 = np.ceil(corners.max(0)).astype(int)
        bx0, by0 = max(bx0, 0), max(by0, 0)
        bx1, by1 = min(bx1, W), min(by1, H)
        if bx1 <= bx0 or by1 <= by0:
            continue
        yy, xx = np.mgrid[by0:by1, bx0:bx1]
        px = xx + 0.5 - s0[0]
        py = yy + 0.5 - s0[1]
        a = (px * sv[1] - py * sv[0]) / det
        b = (su[0] * py - su[1] * px) / det
        inside = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
        if not inside.any():
            continue
        ti = np.clip(np.clip(np.floor(a * tw).astype(int), 0, tw - 1) + tu, 0, 63)
        tj = np.clip(np.clip(np.floor(b * th).astype(int), 0, th - 1) + tv, 0, 63)
        texel = tex[tj, ti]
        vis = inside & (texel[..., 3] >= 26)          # alpha < 0,1 : jeté (comme le shader)
        z = P0[2] + a * Eu[2] + b * Ev[2]
        zb = dep[couche][by0:by1, bx0:bx1]
        win = vis & (z > zb)
        if not win.any():
            continue
        zb[win] = z[win]
        ids[couche][by0:by1, bx0:bx1][win] = pid * 2 + couche
        c = texel[..., :3].astype(np.float32) * lum
        if couche == 1 and nz < 0:
            c = c * 0.55                              # intérieur de la couche 2 (vu à travers)
        cb = col[couche][by0:by1, bx0:bx1]
        cb[win] = c[win]

    base_m = dep[0] > -1e8
    over_m = (dep[1] > -1e8) & ((dep[1] >= dep[0] - 1e-4) | ~base_m)
    alpha = base_m | over_m
    out = np.where(over_m[..., None], col[1], col[0])
    D = np.where(over_m, dep[1], dep[0])
    ID = np.where(over_m, ids[1], ids[0])

    # 4) relief : ombres de contact douces (lumière en haut à gauche). Une surface est
    #    ombrée quand une AUTRE pièce (ou la couche 2 de la même) passe devant elle dans la
    #    direction de la lumière : couche 2 sur couche 1, tête sur le col, bras sur le flanc.
    if relief:
        acc = np.zeros((H, W), np.float32)
        n = 0
        r0 = 0.42 * s
        for ang in np.linspace(0, 2 * math.pi, 10, endpoint=False):
            for rad in (0.0, 0.22 * s):
                dx = int(round(r0 + rad * math.cos(ang)))
                dy = int(round(r0 + rad * math.sin(ang)))
                Ds = np.full((H, W), -1e9, np.float32)
                Is = np.full((H, W), -1, np.int16)
                Ds[dy:, dx:] = D[:H - dy, :W - dx]
                Is[dy:, dx:] = ID[:H - dy, :W - dx]
                acc += ((Ds - D > 0.12) & (Is != ID) & (Is >= 0)).astype(np.float32)
                n += 1
        k = (acc / n) * alpha
        out = out * (1.0 - 0.40 * k)[..., None]

    rgba = np.zeros((H, W, 4), np.uint8)
    rgba[..., :3] = np.clip(out, 0, 255).astype(np.uint8)
    rgba[..., 3] = alpha * 255
    fig = Image.fromarray(rgba, "RGBA")

    # 5) ombre au sol douce
    pieds = (ox, oy)          # (0,0,0) -> pieds
    sol = (cam @ np.array([0.0, 0.0, 0.0]))
    pieds = (sol[0] * s + ox, -sol[1] * s + oy)
    res = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if ombre_sol:
        sh = Image.new("L", (W, H), 0)
        d = ImageDraw.Draw(sh)
        rx = 7.5 * s
        ry = max(1.2 * s, 7.5 * s * abs(math.sin(math.radians(max(tangage, 9)))))
        d.ellipse([pieds[0] - rx, pieds[1] - ry, pieds[0] + rx, pieds[1] + ry], fill=150)
        rx2, ry2 = 4.2 * s, ry * 0.55
        d.ellipse([pieds[0] - rx2, pieds[1] - ry2, pieds[0] + rx2, pieds[1] + ry2], fill=215)
        sh = sh.filter(ImageFilter.GaussianBlur(max(1.0, 0.9 * s)))
        shadow = Image.new("RGBA", (W, H), tuple(ombre_couleur) + (0,))
        shadow.putalpha(sh)
        res.alpha_composite(shadow)
    res.alpha_composite(fig)
    tete = cam @ np.array([0.0, 32.0, 0.0])
    res.info.update(pieds=(float(pieds[0]), float(pieds[1])),
                    tete=(float(tete[0] * s + ox), float(-tete[1] * s + oy)),
                    echelle=s, slim=slim)
    return res


def rendu_face(skin, echelle=8, **kw):
    """Vue de face orthographique (tête 8x8, corps 8x12, bras 4x12 ou 3x12, jambes 4x12),
    seconde couche gonflée + ombre portée douce, ombre au sol. Voir rendu() pour les options."""
    return rendu(skin, echelle, 0.0, 0.0, **kw)


def rendu_trois_quarts(skin, echelle=8, cote="droite", angle=32.0, tangage=11.0, **kw):
    """Vue de trois quarts : face avant pleine lumière, flanc assombri, dessus du crâne
    visible. cote='droite' montre le flanc droit du perso (à gauche de l'écran),
    'gauche' l'autre. Voir rendu() pour les options (pose, gonflement…)."""
    lacet = angle if cote == "droite" else -angle
    return rendu(skin, echelle, lacet, tangage, **kw)


POSE_REPOS = {"bras_d": (0, 0, -3), "bras_g": (0, 0, 3)}
POSE_GARDE = {"tete": (4, -8, 0), "bras_d": (-18, 0, -6), "bras_g": (10, 0, 5),
              "jambe_d": (8, 0, 0), "jambe_g": (-8, 0, 0)}


def rendu_buste(skin, echelle=12, lacet=0.0, tangage=0.0, bas=7, **kw):
    """Buste (tête + haut du torse, `bas` texels sous le cou) : photo d'identité du dossier,
    médaillon, portrait de registre. Fond transparent, sans ombre au sol."""
    kw.setdefault("ombre_sol", False)
    img = rendu(skin, echelle, lacet, tangage, **kw)
    tx, ty = img.info["tete"]
    s = float(echelle)
    m = int(round(2.4 * s))
    top = max(0, int(ty - m))
    bottom = min(img.height, int(ty + (8 + bas) * s * math.cos(math.radians(tangage)) + m * 0.2))
    out = img.crop((0, top, img.width, bottom))
    out.info.update(img.info)
    out.info["tete"] = (tx, ty - top)
    out.info["pieds"] = (tx, out.height)          # pour poser() : aligne le bas du buste
    return out


def poser(fond, img, pieds_xy):
    """Colle (alpha) un rendu sur `fond` en alignant img.info['pieds'] sur pieds_xy."""
    px, py = img.info.get("pieds", (img.width / 2, img.height))
    x, y = int(round(pieds_xy[0] - px)), int(round(pieds_xy[1] - py))
    fond.alpha_composite(img, dest=(max(0, x), max(0, y)),
                         source=(max(0, -x), max(0, -y)))
    return fond


# ───────────────────────── outils pour les artistes ─────────────────────────
def planche_catalogue(categorie="outfit", sexe="homme", echelle=4, colonnes=10, base=None):
    """Planche de choix : chaque asset de la catégorie, porté par un perso de base, vu de
    face (+ son id). Ex. planche_catalogue('outfit').save('tenues.png')."""
    from PIL import ImageFont
    base = dict(base or {})
    ids = [a["id"] for a in catalogue().get(categorie, [])]
    cells = []
    for i in ids:
        kw = dict(sexe=sexe, cheveux="Cheveux_Base")
        kw.update(base)
        if categorie == "outfit":
            kw["tenue"] = i
        elif categorie == "hair":
            kw["cheveux"] = i
        cells.append((i, rendu_face(compose(**kw), echelle, ombre_sol=False, marge=echelle)))
    cw = max(c.width for _, c in cells) + 8
    ch = max(c.height for _, c in cells) + 18
    rows = (len(cells) + colonnes - 1) // colonnes
    sheet = Image.new("RGBA", (cw * colonnes, ch * rows), (24, 16, 18, 255))
    d = ImageDraw.Draw(sheet)
    for k, (i, c) in enumerate(cells):
        x, y = (k % colonnes) * cw, (k // colonnes) * ch
        sheet.alpha_composite(c, (x + (cw - c.width) // 2, y + 2))
        d.text((x + 4, y + ch - 14), i, fill=(230, 210, 180, 255))
    return sheet


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    for nom in PRESETS:
        preset(nom).save(os.path.join(out, f"skin_{nom}.png"))
    print("skins écrits :", ", ".join(PRESETS))
