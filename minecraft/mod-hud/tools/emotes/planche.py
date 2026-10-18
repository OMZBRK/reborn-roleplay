"""Planches de contrôle (Python système + Pillow, hors Blender).

    python tools/emotes/planche.py grille <sortie.png> <img...> [--cols 4] [--cell 320] [--fond 6a9a4a]
    python tools/emotes/planche.py calque <sortie.png> <reference.png> <rendu.png> [--alpha 0.55]
        [--crop x0,y0,x1,y1] [--decal dx,dy] [--echelle s]

``grille`` : vignettes (fond transparent) posées sur un fond uni, nom du fichier en légende.
``calques`` : les calques de toutes les vues d'un fichier de vues qui portent une ``ref`` (image de la vidéo,
relative au dossier des références) et, au besoin, ``decal`` [dx, dy] et ``echelle`` :

    python tools/emotes/planche.py calques <sortie.png> <vues.json> <dossier_rendus> <dossier_refs> [--prefixe meditation] [--t 00.00]

``calque`` : superpose un rendu Blender (transparent) sur une image de la vidéo de référence, en demi-teinte et en
contour, pour caler la silhouette (même cadrage que la caméra Blender : voir README, § Caler sur une vidéo).
"""

import argparse
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter


def _hex(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def grille(out, imgs, cols=4, cell=320, fond="6a9a4a", legendes=True):
    rows = (len(imgs) + cols - 1) // cols
    lh = 18 if legendes else 0
    c = Image.new("RGB", (cols * cell, rows * (cell + lh)), _hex(fond))
    d = ImageDraw.Draw(c)
    for i, p in enumerate(imgs):
        im = Image.open(p).convert("RGBA")
        im.thumbnail((cell, cell), Image.LANCZOS)
        x, y = (i % cols) * cell, (i // cols) * (cell + lh)
        c.paste(im, (x + (cell - im.width) // 2, y + (cell - im.height) // 2), im)
        if legendes:
            d.text((x + 4, y + cell + 2), os.path.splitext(os.path.basename(p))[0][-38:], fill=(255, 255, 255))
    c.save(out)
    return out


def calque(out, ref, rendu, alpha=0.55, crop=None, decal=(0, 0), echelle=1.0):
    r = Image.open(ref).convert("RGBA")
    if crop:
        r = r.crop(crop)
    b = Image.open(rendu).convert("RGBA")
    if echelle != 1.0:
        b = b.resize((int(b.width * echelle), int(b.height * echelle)), Image.LANCZOS)
    calqueb = Image.new("RGBA", r.size, (0, 0, 0, 0))
    calqueb.paste(b, (int((r.width - b.width) / 2 + decal[0]), int((r.height - b.height) / 2 + decal[1])), b)
    # demi-teinte du rendu + contour rouge de sa silhouette
    a = calqueb.getchannel("A")
    semi = calqueb.copy()
    semi.putalpha(a.point(lambda v: int(v * alpha)))
    bord = ImageChops.subtract(a.filter(ImageFilter.MaxFilter(5)), a)
    contour = Image.new("RGBA", r.size, (255, 40, 40, 0))
    contour.putalpha(bord)
    o = Image.alpha_composite(Image.alpha_composite(r, semi), contour)
    # côte à côte : référence | superposition | rendu seul
    side = Image.new("RGBA", (r.width * 3, r.height), (40, 40, 40, 255))
    side.paste(r, (0, 0))
    side.paste(o, (r.width, 0))
    fondv = Image.new("RGBA", r.size, (106, 154, 74, 255))
    side.paste(Image.alpha_composite(fondv, calqueb), (2 * r.width, 0))
    side.convert("RGB").save(out)
    return out


def calques(out, vues_json, rendus, refs, prefixe, t, alpha=0.55):
    import json
    with open(vues_json, encoding="utf-8") as f:
        vues = json.load(f)
    lignes = []
    for nom, v in vues.items():
        if "ref" not in v:
            continue
        tmp = os.path.join(rendus, f"_calque_{nom}.png")
        calque(tmp, os.path.join(refs, v["ref"]), os.path.join(rendus, f"{prefixe}_{nom}_{t}.png"), alpha,
               None, tuple(v.get("decal", (0, 0))), float(v.get("echelle", 1.0)))
        lignes.append(Image.open(tmp))
    w = max(i.width for i in lignes)
    c = Image.new("RGB", (w, sum(i.height for i in lignes)), (40, 40, 40))
    y = 0
    for i in lignes:
        c.paste(i, (0, y))
        y += i.height
    c.save(out)
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    sp = ap.add_subparsers(dest="cmd", required=True)
    g = sp.add_parser("grille")
    g.add_argument("out")
    g.add_argument("imgs", nargs="+")
    g.add_argument("--cols", type=int, default=4)
    g.add_argument("--cell", type=int, default=320)
    g.add_argument("--fond", default="6a9a4a")
    g.add_argument("--sans-legende", action="store_true")
    c = sp.add_parser("calque")
    c.add_argument("out")
    c.add_argument("ref")
    c.add_argument("rendu")
    c.add_argument("--alpha", type=float, default=0.55)
    c.add_argument("--crop")
    c.add_argument("--decal", default="0,0")
    c.add_argument("--echelle", type=float, default=1.0)
    cs = sp.add_parser("calques")
    cs.add_argument("out")
    cs.add_argument("vues")
    cs.add_argument("rendus")
    cs.add_argument("refs")
    cs.add_argument("--prefixe", default="meditation")
    cs.add_argument("--t", default="00.00")
    cs.add_argument("--alpha", type=float, default=0.55)
    a = ap.parse_args()
    if a.cmd == "calques":
        print(calques(a.out, a.vues, a.rendus, a.refs, a.prefixe, a.t, a.alpha))
    elif a.cmd == "grille":
        print(grille(a.out, a.imgs, a.cols, a.cell, a.fond, not a.sans_legende))
    else:
        cr = tuple(int(v) for v in a.crop.split(",")) if a.crop else None
        dx, dy = (int(v) for v in a.decal.split(","))
        print(calque(a.out, a.ref, a.rendu, a.alpha, cr, (dx, dy), a.echelle))
