# -*- coding: utf-8 -*-
"""
gen_communication_textures.py — décor de la cloche du village et des volières (16/10, maquettes
Maquettes/gameplan/37_faction/v3c 01 à 06, docs/gameplay/CLOCHE_ET_MISSIVES.md). Écrans centrés, AUCUN kanji ;
signatures : la cloche (filet d'or) et le corbeau perché. Pièces reprises du script des maquettes
(_maquettes_cloche_voliere.py : motifs, cachet de cire, petite cloche à ondes, missive provisoire).

Usage (depuis minecraft/mod-hud) :  python tools/gen_communication_textures.py

Contrat (textures/gui/communication/, pixel art posé à multiple entier en pixels écran par CommArt.sprite) :
- panneau.png 452×392 : panneau laqué noir du kit (kanagu « petit ») 446×386, ombre ; posé par coupe (capD 16, capB 16)
  à la taille voulue (cloche 440×364, volière 440×356 / 410×352, lettre 300×202).
- motif_cloche.png 27×17, motif_corbeau.png 29×16 : signatures, posées ×2 GUI (×4 px à l'échelle 2).
- cachet.png 15×15, cachet_brise.png 18×16 : cachet de cire entier / brisé (×1,5 GUI).
- cloche_normale.png 9×9, cloche_importante.png 15×9, cloche_danger.png 19×9 : marque de la bande (0, 1, 2 ondes).
- missive_scellee.png / missive_descellee.png 20×20 (liste de la volière), missive_bande.png 12×12 (marque de la
  bande « MISSIVE »), coche.png 7×7, loupe.png 9×9.

Et les visuels PROVISOIRES du pack Nexo (minecraft/server-config/nexo/pack, l'utilisateur modélise les vrais en gardant
les identifiants) : objets reborn:missive_scellee / reborn:missive_descellee (sprites 16×16, item/generated), meubles
reborn:cloche_village (potence et cloche) et reborn:perchoir_voliere (poteau, perchoir, pupitre) en cubes simples.
"""
from __future__ import annotations

import json
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import kit, hexc  # noqa: E402

S = ea.Sortie("communication", budget=400_000)
ICI = os.path.dirname(os.path.abspath(__file__))
NEXO = os.path.normpath(os.path.join(ICI, "..", "..", "server-config", "nexo", "pack", "assets", "reborn"))

OR, OR_CLAIR, OR_SOMBRE = "D9A95E", "F2D08A", "8E6A30"
TYPES = {"normale": (OR, 0), "importante": ("E8873A", 1), "danger": ("D2523E", 2)}


def panneau():
    w, h = 446, 386
    im = Image.new("RGBA", (w + 6, h + 6), (0, 0, 0, 0))
    kit.panneau_laque(im, 0, 0, w, h, ton="noir", modele_kanagu="petit")
    S.sauver("panneau.png", im, (w + 6, h + 6))


def _carte(rows, pal):
    h, w = len(rows), max(len(r) for r in rows)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, k in enumerate(r):
            if k in pal:
                im.putpixel((x, y), hexc(pal[k]))
    return im


def _contour(rows, plein="#"):
    h, w = len(rows), max(len(r) for r in rows)
    g = [[(rows[y][x] if x < len(rows[y]) else ".") for x in range(w)] for y in range(h)]
    out = []
    for y in range(h):
        s = ""
        for x in range(w):
            k = g[y][x]
            if k != plein:
                s += k
                continue
            vois = [(y + dy, x + dx) for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1))]
            bord = any(not (0 <= a < h and 0 <= b < w) or g[a][b] not in (plein, "e") for a, b in vois)
            haut = not (y > 0 and g[y - 1][x] == plein)
            s += ("h" if haut else "o") if bord else "f"
        out.append(s)
    return out


def motif_cloche():
    rows = [
        ".ppppppppppppppppppppppppp.",
        "..q.....................q..",
        "..q..........k..........q..",
        "..q.........###.........q..",
        "..q........#####........q..",
        "..q.......#######.......q..",
        "..q......#########......q..",
        "..q......#########......q..",
        "w.q......#########......q.w",
        ".wq......#########......qw.",
        "w.q.....###########.....q.w",
        "..q.....###########.....q..",
        "..q....#############....q..",
        "..q...###############...q..",
        "..q..........k..........q..",
        "..q.........kkk.........q..",
        ".rrr...................rrr.",
    ]
    S.sauver("motif_cloche.png", _carte(_contour(rows), {"o": OR, "h": OR_CLAIR, "f": "4A3418", "p": OR, "q": OR_SOMBRE,
                                                         "r": OR_SOMBRE, "k": OR_CLAIR, "w": OR_SOMBRE}), (27, 17))


def motif_corbeau():
    rows = [
        ".........###.................",
        ".......######................",
        "......##e#####...............",
        "..bbbb#########..............",
        ".bbb...#########.............",
        ".......############..........",
        "........##############.......",
        "........#################....",
        ".........##################..",
        "..........#################.#",
        "...........############...###",
        ".............#######.........",
        "..............t..t...........",
        "..............t..t...........",
        "...zzzzzzzzzzzzzzzzzzzzzzz...",
        ".......................zz....",
    ]
    S.sauver("motif_corbeau.png", _carte(_contour(rows), {"o": OR, "h": OR_CLAIR, "f": "3A2A1E", "e": OR_CLAIR,
                                                          "b": OR_CLAIR, "t": OR, "z": OR_SOMBRE}), (29, 16))


def cachet(brise=False):
    W0, W1, W2, W3 = "5E0C16", "9A1A2A", "C2364A", "E07A80"
    im = Image.new("RGBA", (15, 15), (0, 0, 0, 0))
    for y in range(15):
        for x in range(15):
            r = ((x - 7) ** 2 + (y - 7) ** 2) ** 0.5
            lim = 7.0 + (0.6 if (x * 7 + y * 3) % 5 == 0 else 0)
            if r > lim:
                continue
            c = W1
            if r > lim - 1.1:
                c = W0
            elif 4.2 < r < 5.2:
                c = W0 if (x + y) > 13 else W2
            elif x + y < 9 and r > 2:
                c = W2
            im.putpixel((x, y), hexc(c))
    for (x, y) in ((9, 4), (8, 5), (7, 6), (6, 7), (5, 8), (5, 9)):
        im.putpixel((x, y), hexc(W3))
    for (x, y) in ((8, 6), (7, 7), (6, 8)):
        im.putpixel((x, y), hexc(W0))
    if not brise:
        S.sauver("cachet.png", im, (15, 15))
        return
    out = Image.new("RGBA", (18, 16), (0, 0, 0, 0))
    jag = [7, 7, 8, 8, 7, 6, 6, 7, 8, 8, 7, 7, 6, 7, 7]
    for y in range(15):
        c = jag[y]
        for x in range(15):
            p = im.getpixel((x, y))
            if p[3] == 0:
                continue
            if x < c:
                out.putpixel((x, y), p)
            else:
                out.putpixel((x + 3, y + 1), p)
    S.sauver("cachet_brise.png", out, (18, 16))


def icone_cloche(typ):
    col, n = TYPES[typ]
    base = ["....#....", "...###...", "..#####..", "..#####..", "..#####..", ".#######.", "#########", "....#...."]
    pad = 2 * n + (1 if n else 0)
    w = 9 + 2 * pad
    im = Image.new("RGBA", (w, 9), (0, 0, 0, 0))
    for y, r in enumerate(base):
        for x, k in enumerate(r):
            if k == "#":
                im.putpixel((x + pad, y), hexc(col))
    for k in range(n):
        for side in (-1, 1):
            xx = pad - 2 - 2 * k if side < 0 else pad + 9 + 1 + 2 * k
            for yy in (3, 4, 5):
                im.putpixel((xx, yy), hexc(col, 200 - 70 * k))
    S.sauver(f"cloche_{typ}.png", im, (w, 9))


def missive(scellee=True, n=16):
    """Parchemin roulé, cordon, cachet de cire entier ou brisé (dessiné à 16, agrandi au plus proche pour 20)."""
    P0, P1, P2, P3 = (120, 92, 60), (196, 168, 120), (226, 204, 160), (244, 230, 196)
    W0, W1, W2 = (96, 14, 24), (160, 24, 43), (214, 72, 84)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([2, 4, 13, 11], fill=P2)
    d.line([(2, 4), (13, 4)], fill=P3)
    d.line([(2, 11), (13, 11)], fill=P1)
    for x in (1, 14):
        d.rectangle([x, 3, x, 12], fill=P1)
    d.line([(0, 4), (0, 11)], fill=P0)
    d.line([(15, 4), (15, 11)], fill=P0)
    d.line([(3, 7), (12, 7)], fill=(150, 30, 40))
    if scellee:
        d.ellipse([5, 5, 10, 10], fill=W1, outline=W0)
        d.point((6, 6), fill=W2)
    else:
        d.line([(3, 7), (5, 7)], fill=(150, 30, 40))
        d.line([(10, 8), (12, 9)], fill=(150, 30, 40))
        d.polygon([(4, 5), (7, 5), (6, 8), (4, 9)], fill=W1, outline=W0)
        d.polygon([(9, 7), (11, 6), (12, 9), (9, 10)], fill=W1, outline=W0)
        d.rectangle([7, 6, 8, 9], fill=P2)
    if n == 16:
        return im
    if n == 12:
        # petite marque de bande : parchemin 12×8 et cachet plein
        im2 = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
        d2 = ImageDraw.Draw(im2)
        d2.rectangle([1, 3, 10, 8], fill=P2)
        d2.line([(1, 3), (10, 3)], fill=P3)
        d2.line([(1, 8), (10, 8)], fill=P1)
        d2.line([(0, 3), (0, 8)], fill=P0)
        d2.line([(11, 3), (11, 8)], fill=P0)
        d2.line([(2, 5), (9, 5)], fill=(150, 30, 40))
        d2.rectangle([4, 4, 7, 7], fill=W1)
        d2.point((4, 4), fill=W2)
        return im2
    # 20 : redessin à la main (pas d'agrandissement flou)
    im3 = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d3 = ImageDraw.Draw(im3)
    d3.rectangle([2, 5, 17, 14], fill=P2)
    d3.line([(2, 5), (17, 5)], fill=P3)
    d3.line([(2, 14), (17, 14)], fill=P1)
    for x in (1, 18):
        d3.rectangle([x, 4, x, 15], fill=P1)
    d3.line([(0, 5), (0, 14)], fill=P0)
    d3.line([(19, 5), (19, 14)], fill=P0)
    d3.line([(3, 9), (16, 9)], fill=(150, 30, 40))
    d3.line([(3, 10), (16, 10)], fill=(110, 22, 30))
    if scellee:
        d3.ellipse([6, 6, 13, 13], fill=W1, outline=W0)
        d3.point((8, 8), fill=W2)
        d3.point((9, 8), fill=W2)
    else:
        d3.line([(3, 9), (6, 9)], fill=(150, 30, 40))
        d3.polygon([(5, 6), (9, 6), (8, 10), (5, 12)], fill=W1, outline=W0)
        d3.polygon([(11, 9), (14, 7), (15, 12), (11, 13)], fill=W1, outline=W0)
        d3.rectangle([9, 7, 10, 12], fill=P2)
    return im3


def coche():
    im = Image.new("RGBA", (7, 7), (0, 0, 0, 0))
    for (x, y) in ((0, 3), (1, 4), (2, 5), (3, 4), (4, 3), (5, 2), (6, 1), (1, 3), (2, 4), (3, 3), (4, 2), (5, 1)):
        im.putpixel((x, y), hexc("F2D08A"))
    S.sauver("coche.png", im, (7, 7))


def loupe():
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    c = hexc("8C8478")
    for (x, y) in ((2, 0), (3, 0), (4, 0), (1, 1), (5, 1), (0, 2), (6, 2), (0, 3), (6, 3), (0, 4), (6, 4), (1, 5), (5, 5),
                   (2, 6), (3, 6), (4, 6), (6, 6), (7, 7), (8, 8), (7, 8), (8, 7)):
        im.putpixel((x, y), c)
    S.sauver("loupe.png", im, (9, 9))


# ════════════════════════════════════════ pack Nexo (provisoire) ════════════════════════════════════════

CREDIT = "Reborn — provisoire (tools/gen_communication_textures.py, 2026-10-16), à remplacer par le modèle Blockbench"


def _ecrire_json(chemin, obj):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def _item_def(nom):
    _ecrire_json(os.path.join(NEXO, "items", nom + ".json"), {"model": {"type": "minecraft:model", "model": "reborn:item/" + nom}})


def nexo_missives():
    for sc, nom in ((True, "missive_scellee"), (False, "missive_descellee")):
        im = missive(sc, 16)
        os.makedirs(os.path.join(NEXO, "textures", "item"), exist_ok=True)
        im.save(os.path.join(NEXO, "textures", "item", nom + ".png"))
        _ecrire_json(os.path.join(NEXO, "models", "item", nom + ".json"),
                     {"credit": CREDIT, "parent": "minecraft:item/generated", "textures": {"layer0": "reborn:item/" + nom}})
        _item_def(nom)


def _tex_meuble(nom, palette):
    """Texture 16×16 en bandes de 4 lignes : une teinte par matière (bois, bronze, or, ombre)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    for i, (base, clair, sombre) in enumerate(palette):
        y = i * 4
        d.rectangle([0, y, 15, y + 3], fill=hexc(base))
        d.line([(0, y), (15, y)], fill=hexc(clair))
        d.line([(0, y + 3), (15, y + 3)], fill=hexc(sombre))
        for x in range(0, 16, 5):
            d.point((x + (i % 2), y + 1), fill=hexc(sombre))
    im.save(os.path.join(NEXO, "textures", "item", nom + ".png"))


def _cube(fr, to, v, rot=None):
    """Un cube texturé d'une bande (v = indice de bande 0..3) sur toutes ses faces."""
    uv = [0, v * 4, 16, v * 4 + 4]
    faces = {f: {"uv": uv, "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")}
    e = {"from": fr, "to": to, "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


def nexo_meubles():
    os.makedirs(os.path.join(NEXO, "textures", "item"), exist_ok=True)
    # bandes : 0 bois sombre, 1 bronze, 2 or, 3 pierre
    _tex_meuble("cloche_village", [("4A2E1C", "6A4428", "2E1A10"), ("8A5A26", "B07A3A", "5A3A18"),
                                   ("D9A95E", "F2D08A", "8E6A30"), ("6E6A64", "8E8A84", "4A4642")])
    els = [
        _cube([0, 0, 0], [16, 1, 16], 3),            # socle
        _cube([1, 1, 7], [3, 30, 9], 0),             # poteaux
        _cube([13, 1, 7], [15, 30, 9], 0),
        _cube([0, 30, 6.5], [16, 32, 9.5], 0),       # poutre
        _cube([7.5, 27, 7.5], [8.5, 30, 8.5], 2),    # attache
        _cube([5, 18, 5], [11, 27, 11], 1),          # cloche
        _cube([4, 16, 4], [12, 18, 12], 1),          # bord évasé
        _cube([7.5, 14, 7.5], [8.5, 16, 8.5], 2),    # battant
    ]
    _ecrire_json(os.path.join(NEXO, "models", "item", "cloche_village.json"), {
        "credit": CREDIT, "textures": {"0": "reborn:item/cloche_village", "particle": "reborn:item/cloche_village"},
        "elements": els,
        "display": {"fixed": {"scale": [1, 1, 1]}, "gui": {"rotation": [30, 225, 0], "scale": [0.35, 0.35, 0.35],
                                                             "translation": [0, -3, 0]}}})
    _item_def("cloche_village")
    _tex_meuble("perchoir_voliere", [("4A2E1C", "6A4428", "2E1A10"), ("1E1A1C", "3A3236", "0E0A0C"),
                                     ("D9A95E", "F2D08A", "8E6A30"), ("7A5A3A", "9A7A54", "5A3E26")])
    els = [
        _cube([4, 0, 4], [12, 1, 12], 0),            # pied
        _cube([7, 1, 7], [9, 20, 9], 0),             # poteau
        _cube([2, 20, 7.5], [14, 21, 8.5], 3),       # perchoir
        _cube([3, 12, 3], [13, 13, 13], 3),          # pupitre (planche)
        _cube([9, 21, 7], [12, 24, 9], 1),           # corbeau (bloc sombre)
        _cube([11.5, 23, 7.5], [13, 23.6, 8.5], 2),  # bec
    ]
    _ecrire_json(os.path.join(NEXO, "models", "item", "perchoir_voliere.json"), {
        "credit": CREDIT, "textures": {"0": "reborn:item/perchoir_voliere", "particle": "reborn:item/perchoir_voliere"},
        "elements": els,
        "display": {"fixed": {"scale": [1, 1, 1]}, "gui": {"rotation": [30, 225, 0], "scale": [0.5, 0.5, 0.5],
                                                             "translation": [0, -2, 0]}}})
    _item_def("perchoir_voliere")


if __name__ == "__main__":
    panneau()
    motif_cloche()
    motif_corbeau()
    cachet(False)
    cachet(True)
    for t in TYPES:
        icone_cloche(t)
    S.sauver("missive_scellee.png", missive(True, 20), (20, 20))
    S.sauver("missive_descellee.png", missive(False, 20), (20, 20))
    S.sauver("missive_bande.png", missive(True, 12), (12, 12))
    coche()
    loupe()
    S.bilan()
    nexo_missives()
    nexo_meubles()
    print("pack Nexo :", NEXO)
