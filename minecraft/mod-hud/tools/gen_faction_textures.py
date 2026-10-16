# -*- coding: utf-8 -*-
"""
gen_faction_textures.py — atlas des insignes du volet Faction (touche J, FACTION.md v3, 16/10).

Insignes de section dessinés au trait (repris des maquettes v2/v3, Maquettes/gameplan/37_faction) : masque ANBU,
shuriken de la Police, croix médicale cerclée, livre de l'Académie, rouleau des missions, grande porte de la Garde,
chapeau du Conseil, bandeau frontal, croissant de lune (organisation) ; puis les emblèmes **réels** des six villages
(textures/gui/esc/village_<v>.png). Aucun kanji, aucun sceau (DA §3). Blancs sur transparent, teintés au rendu.

Comme l'atlas du Poste de garde (gen_garde_icones.py), chaque insigne est rendu à **chaque taille en pixels écran**
utilisée (taille GUI × échelle d'interface) : nets à toutes les échelles, sans filtrage (DA §6, règle 3).

Usage (depuis minecraft/mod-hud) :  python -B tools/gen_faction_textures.py

Contrat — textures/gui/faction/insignes.png :
- rangées par taille p (px) dans l'ordre de TAILLES ; rangée de hauteur p + 2, empilées depuis y = 0 ;
- dans une rangée, l'insigne d'indice i (ordre de NOMS) est en x = i × (p + 2), carré p × p ;
- NOMS et TAILLES sont recopiés dans faction/FactionArt.java (même ordre).
"""
from __future__ import annotations

import os

import numpy as np
from PIL import Image, ImageDraw

ICI = os.path.dirname(os.path.abspath(__file__))
GUI = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui")
SORTIE = os.path.join(GUI, "faction", "insignes.png")

NOMS = ["anbu", "police", "medical", "academie", "missions", "garde", "conseil", "bandeau", "lune",
        "konoha", "suna", "kiri", "kumo", "iwa", "ame"]
TAILLES = [8, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 30, 32, 36, 40, 44, 48, 54, 60, 64, 72, 80, 96]

K = 8
T = 48
EP = int(2.9 * K)
C = 192


def _toile():
    im = Image.new("L", (T * K, T * K), 0)
    return im, ImageDraw.Draw(im)


def _trait(d, pts, ep=EP):
    d.line(pts, fill=255, width=ep, joint="curve")
    r = ep / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)


def _cercle(d, cx, cy, r, ep=EP):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=255, width=ep)


def _courbe(p0, p1, p2, n=24):
    out = []
    for i in range(n + 1):
        t = i / n
        out.append(((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]))
    return out


def ins_anbu():
    m, d = _toile()
    c = C
    contour = (_courbe((c, 22), (54, 26), (52, 150)) + _courbe((52, 150), (60, 300), (c, 362))[1:])
    droite = [(2 * c - x, y) for x, y in reversed(contour)]
    _trait(d, contour + droite[1:])
    for s in (-1, 1):
        ex = c + s * 70
        oeil = _courbe((ex - 38, 178), (ex, 140), (ex + 38, 178)) + _courbe((ex + 38, 178), (ex, 206), (ex - 38, 178))[1:]
        d.polygon(oeil, fill=255)
        _trait(d, _courbe((ex + s * 18, 240), (ex + s * 30, 262), (ex + s * 22, 292)), ep=int(EP * 0.8))
    _trait(d, _courbe((c - 46, 70), (c, 98), (c + 46, 70)), ep=int(EP * 0.8))
    _trait(d, [(c, 214), (c, 266)], ep=int(EP * 0.7))
    return m


def ins_medical():
    m, d = _toile()
    c = C
    _cercle(d, c, c, 168)
    a, b = 40, 118
    croix = [(c - a, c - b), (c + a, c - b), (c + a, c - a), (c + b, c - a), (c + b, c + a), (c + a, c + a),
             (c + a, c + b), (c - a, c + b), (c - a, c + a), (c - b, c + a), (c - b, c - a), (c - a, c - a),
             (c - a, c - b)]
    d.line(croix, fill=255, width=EP, joint="curve")
    return m


def ins_police():
    import math
    m, d = _toile()
    c = C
    pts = []
    for i in range(8):
        ang = math.radians(-90 + i * 45)
        r = 176 if i % 2 == 0 else 52
        dec = math.radians(10) if i % 2 else 0
        pts.append((c + r * math.cos(ang + dec), c + r * math.sin(ang + dec)))
    pts.append(pts[0])
    d.line(pts, fill=255, width=EP, joint="curve")
    _cercle(d, c, c, 26, ep=int(EP * 0.9))
    return m


def ins_academie():
    m, d = _toile()
    c = C
    for s in (-1, 1):
        haut = _courbe((c, 108), (c + s * 70, 80), (c + s * 160, 96))
        bas = _courbe((c + s * 160, 300), (c + s * 70, 286), (c, 312))
        _trait(d, haut + [(c + s * 160, 96), (c + s * 160, 300)] + bas)
        for k in range(3):
            y = 150 + k * 46
            _trait(d, _courbe((c + s * 40, y + 6), (c + s * 80, y - 8), (c + s * 122, y)), ep=int(EP * 0.6))
    _trait(d, [(c, 108), (c, 312)])
    return m


def ins_missions():
    m, d = _toile()
    c = C
    d.rounded_rectangle([14, 124, 370, 260], radius=60, outline=255, width=EP)
    for x in (78, 306):
        _trait(d, _courbe((x, 132), (x + 22, c), (x, 252)), ep=int(EP * 0.7))
    d.rectangle([164, 124, 220, 260], fill=255)
    d.rectangle([178, 140, 206, 244], fill=0)
    return m.rotate(32, resample=Image.BICUBIC, center=(c, c))


def ins_garde():
    m, d = _toile()
    c = C
    _trait(d, _courbe((22, 96), (c, 46), (362, 96)), ep=int(EP * 1.2))
    _trait(d, [(60, 104), (324, 104)])
    d.rectangle([84, 136, 300, 170], outline=255, width=int(EP * 0.8))
    for x in (110, 274):
        d.rectangle([x - 22, 104, x + 22, 340], outline=255, width=int(EP * 0.85))
    _trait(d, [(132, 330), (252, 330)], ep=int(EP * 0.6))
    _trait(d, [(c, 186), (c, 330)], ep=int(EP * 0.6))
    return m


def ins_conseil():
    m, d = _toile()
    c = C
    _trait(d, [(c, 62), (34, 236)])
    _trait(d, [(c, 62), (350, 236)])
    _trait(d, _courbe((34, 236), (c, 286), (350, 236)))
    _trait(d, _courbe((96, 188), (c, 214), (288, 188)), ep=int(EP * 0.6))
    _trait(d, _courbe((120, 262), (106, 300), (124, 338)), ep=int(EP * 0.75))
    _trait(d, _courbe((264, 262), (278, 300), (260, 338)), ep=int(EP * 0.75))
    return m


def ins_bandeau():
    m, d = _toile()
    d.rounded_rectangle([90, 128, 294, 256], radius=26, outline=255, width=EP)
    for x, y in ((122, 158), (262, 158), (122, 226), (262, 226)):
        d.ellipse([x - 11, y - 11, x + 11, y + 11], fill=255)
    _trait(d, [(90, 166), (22, 150)])
    _trait(d, [(90, 218), (22, 236)])
    _trait(d, [(294, 166), (362, 150)])
    _trait(d, [(294, 218), (362, 236)])
    return m


def ins_lune():
    m, d = _toile()
    d.ellipse([40, 40, 344, 344], outline=255, width=EP)
    d.ellipse([110, 20, 400, 310], fill=0)
    d.arc([110, 20, 400, 310], 100, 250, fill=255, width=EP)
    return m


def village(v):
    """Emblème réel du village : alpha seul (blanc), recadré carré."""
    src = Image.open(os.path.join(GUI, "esc", f"village_{v}.png")).convert("RGBA")
    a = np.asarray(src)[..., 3]
    m = Image.fromarray(a, "L")
    bb = m.getbbox()
    if bb:
        m = m.crop(bb)
    w, h = m.size
    s = max(w, h)
    sq = Image.new("L", (s, s), 0)
    sq.paste(m, ((s - w) // 2, (s - h) // 2))
    return sq.resize((T * K, T * K), Image.LANCZOS)


DESSINS = {"anbu": ins_anbu, "police": ins_police, "medical": ins_medical, "academie": ins_academie,
           "missions": ins_missions, "garde": ins_garde, "conseil": ins_conseil, "bandeau": ins_bandeau,
           "lune": ins_lune}


def main():
    grands = {}
    for n in NOMS:
        grands[n] = DESSINS[n]() if n in DESSINS else village(n)
    h = sum(p + 2 for p in TAILLES)
    w = max(len(NOMS) * (p + 2) for p in TAILLES)
    atlas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    y = 0
    for p in TAILLES:
        for i, n in enumerate(NOMS):
            a = np.asarray(grands[n].resize((p, p), Image.LANCZOS))
            out = np.zeros((p, p, 4), np.uint8)
            out[..., :3] = 255
            out[..., 3] = a
            atlas.alpha_composite(Image.fromarray(out, "RGBA"), (i * (p + 2), y))
        y += p + 2
    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    atlas.save(SORTIE, optimize=True)
    print(f"{SORTIE} : {w}x{h}, {len(NOMS)} insignes x {len(TAILLES)} tailles")


if __name__ == "__main__":
    main()
