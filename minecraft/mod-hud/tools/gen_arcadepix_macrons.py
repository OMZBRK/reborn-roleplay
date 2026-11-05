"""Voyelles à macron dans ArcadePix (Game Plan n° 17, lot 3, CADRAGE §11, planche n1 variante A).

Ajoute dix glyphes à ``assets/reborn/font/arcadepix.ttf`` : Ā ā Ē ē Ī ī Ō ō Ū ū (U+0100, 0101, 0112, 0113, 012A,
012B, 014C, 014D, 016A, 016B), dessinés sur la grille de la police (1000 unités par em, 100 unités par pixel) :

- capitales : le glyphe de la lettre, plus une barre d'1 px **détachée** (1 px d'espace au-dessus de la lettre),
  x 100 à 600 (5 px), y 800 à 900 ; Ī : x 100 à 500 (4 px) ;
- bas de casse : la lettre, plus une barre x 100 à 500 (4 px), y 700 à 800 ;
- ī : le fût du i sans son point, la barre x 0 à 300 à la place du point.

Le script est rejouable : il repart toujours des glyphes de base et remplace les macrons s'ils existent déjà. Les
métriques (ascendante 800, descendante 200, avances) ne changent pas : la barre des capitales dépasse l'ascendante
d'1 px, comme sur la planche n1 (l'inverse décalerait tout le texte ArcadePix).

Usage : python tools/gen_arcadepix_macrons.py [--verifier image.png]
"""
from __future__ import annotations

import argparse
from pathlib import Path

from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

RACINE = Path(__file__).resolve().parent.parent
TTF = RACINE / "src/main/resources/assets/reborn/font/arcadepix.ttf"

PX = 100
# caractère → (lettre de base, nom du glyphe, barre (x0, x1, y0) en unités, retirer le point du i)
MACRONS = {
    "Ā": ("A", "Amacron", (100, 600, 800), False),
    "Ē": ("E", "Emacron", (100, 600, 800), False),
    "Ī": ("I", "Imacron", (100, 500, 800), False),
    "Ō": ("O", "Omacron", (100, 600, 800), False),
    "Ū": ("U", "Umacron", (100, 600, 800), False),
    "ā": ("a", "amacron", (100, 500, 700), False),
    "ē": ("e", "emacron", (100, 500, 700), False),
    "ī": ("i", "imacron", (0, 300, 700), True),
    "ō": ("o", "omacron", (100, 500, 700), False),
    "ū": ("u", "umacron", (100, 500, 700), False),
}


def _aire(points):
    """Aire signée (y vers le haut) : négative = sens horaire, le sens des contours extérieurs en TrueType."""
    s = 0
    for i in range(len(points)):
        x0, y0 = points[i]
        x1, y1 = points[(i + 1) % len(points)]
        s += x0 * y1 - x1 * y0
    return s / 2


def _contours(glyphset, nom):
    """Contours du glyphe (listes de points en lignes droites : ArcadePix n'a que des segments)."""
    pen = DecomposingRecordingPen(glyphset)
    glyphset[nom].draw(pen)
    contours, cour = [], None
    for op, args in pen.value:
        if op == "moveTo":
            cour = [args[0]]
        elif op == "lineTo":
            cour.append(args[0])
        elif op in ("closePath", "endPath"):
            if cour:
                if len(cour) > 1 and cour[0] == cour[-1]:
                    cour.pop()
                contours.append(cour)
            cour = None
        else:
            raise SystemExit(f"courbe inattendue dans {nom} : {op}")
    return contours


def generer(font: TTFont) -> None:
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    glyf, hmtx = font["glyf"], font["hmtx"]
    ordre = list(font.getGlyphOrder())
    horaire = None
    for ch, (base, nom, (x0, x1, y0), sans_point) in MACRONS.items():
        nb = cmap[ord(base)]
        contours = _contours(gs, nb)
        if horaire is None:
            # sens des contours extérieurs de la police (celui du plus grand contour du A)
            horaire = _aire(max(contours, key=lambda c: abs(_aire(c)))) < 0
        if sans_point:
            contours = [c for c in contours if min(p[1] for p in c) < 650]   # le point (y 696 à 800) part
        barre = [(x0, y0), (x0, y0 + PX), (x1, y0 + PX), (x1, y0)]   # horaire (y vers le haut)
        if not horaire:
            barre.reverse()
        pen = TTGlyphPen(None)
        for c in contours + [barre]:
            pen.moveTo(c[0])
            for p in c[1:]:
                pen.lineTo(p)
            pen.closePath()
        g = pen.glyph()
        g.recalcBounds(glyf)
        if nom not in ordre:
            ordre.append(nom)
        glyf.glyphs[nom] = g
        avance = hmtx[nb][0]
        hmtx.metrics[nom] = (avance, g.xMin)
        for table in font["cmap"].tables:
            if table.isUnicode():
                table.cmap[ord(ch)] = nom
    # hdmx (largeurs pré-calculées par taille, pour le hinting de Windows) : facultative, ignorée par stb_truetype
    # (Minecraft) ; on la retire plutôt que de la tenir à jour glyphe par glyphe.
    if "hdmx" in font:
        del font["hdmx"]
    font.setGlyphOrder(ordre)
    glyf.glyphOrder = ordre
    font["maxp"].numGlyphs = len(ordre)
    if font["post"].formatType == 2.0:
        font["post"].extraNames = []
        font["post"].mapping = {}


def verifier(ttf: Path, sortie: Path) -> None:
    """Planche de contrôle : les dix glyphes à la taille du jeu (10 px) × 8, puis « HYŪGA ŌTSUTSUKI KŌGA »."""
    from PIL import Image, ImageDraw, ImageFont

    f10 = ImageFont.truetype(str(ttf), 10)
    lignes = ["ĀĒĪŌŪ āēīōū", "AEIOU aeiou", "HYŪGA ŌTSUTSUKI KŌGA", "Hyūga Ōtsutsuki Kōga"]
    w = max(int(f10.getlength(t)) for t in lignes) + 8
    h = 16 * len(lignes) + 4
    im = Image.new("RGB", (w, h), (24, 18, 20))
    d = ImageDraw.Draw(im)
    for i, t in enumerate(lignes):
        d.text((4, 4 + 16 * i), t, font=f10, fill=(250, 238, 214))
    im = im.resize((w * 8, h * 8), Image.NEAREST)
    # grille d'1 px de police
    d = ImageDraw.Draw(im)
    for x in range(0, im.width, 8):
        d.line([(x, 0), (x, im.height)], fill=(40, 32, 36))
    for y in range(0, im.height, 8):
        d.line([(0, y), (im.width, y)], fill=(40, 32, 36))
    sortie.parent.mkdir(parents=True, exist_ok=True)
    im.save(sortie)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--verifier", type=Path, help="écrit une planche de contrôle PNG")
    ap.add_argument("--ttf", type=Path, default=TTF)
    a = ap.parse_args()
    font = TTFont(str(a.ttf))
    generer(font)
    font.save(str(a.ttf))
    relu = TTFont(str(a.ttf))
    cm = relu.getBestCmap()
    manque = [hex(ord(c)) for c in MACRONS if ord(c) not in cm]
    if manque:
        raise SystemExit(f"glyphes absents après écriture : {manque}")
    print(f"{a.ttf.name} : {len(cm)} caractères, macrons ajoutés")
    if a.verifier:
        verifier(a.ttf, a.verifier)
        print(f"planche : {a.verifier}")


if __name__ == "__main__":
    main()
