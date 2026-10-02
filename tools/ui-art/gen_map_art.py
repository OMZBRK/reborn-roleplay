"""Textures d'interface de la carte du monde (mod-hud, assets/reborn/textures/gui/map/).

    python gen_map_art.py

- marker_lieu.png / marker_pnj.png / marker_porte.png  16×16  icônes de marqueurs (contour sombre)
- compass.png 40×40  rose des vents (N rouge)
- arrow.png   13×13  flèche du joueur, pointe vers le haut (tournée au rendu)
"""

from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/textures/gui/map"
INK = (43, 32, 24, 255)


def outline(img):
    """Contour sombre 1 px autour des pixels opaques (lisible sur toute la carte)."""
    w, h = img.size
    src = img.load()
    out = img.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] == 0 and any(
                    0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                o[x, y] = INK
    return out


def marker_lieu():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    red, dark = (214, 52, 40, 255), (150, 30, 26, 255)
    d.rectangle((1, 2, 14, 3), fill=red)           # kasagi
    d.rectangle((0, 2, 0, 2), fill=red); d.rectangle((15, 2, 15, 2), fill=red)
    d.rectangle((2, 5, 13, 5), fill=red)           # nuki
    d.rectangle((4, 4, 5, 14), fill=red); d.rectangle((10, 4, 11, 14), fill=red)
    d.rectangle((5, 4, 5, 14), fill=dark); d.rectangle((11, 4, 11, 14), fill=dark)
    d.rectangle((1, 1, 14, 1), fill=(60, 40, 34, 255))
    img = outline(img)
    img.save(OUT / "marker_lieu.png")


def marker_pnj():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    teal, skin = (63, 184, 175, 255), (240, 206, 168, 255)
    d.ellipse((4, 1, 11, 8), fill=skin)
    d.rectangle((4, 1, 11, 3), fill=(70, 50, 40, 255))   # cheveux
    d.rectangle((5, 4, 10, 4), fill=(40, 60, 110, 255))  # bandeau
    d.rectangle((2, 9, 13, 14), fill=teal)
    d.rectangle((7, 9, 8, 14), fill=(40, 130, 124, 255))
    img = outline(img)
    img.save(OUT / "marker_pnj.png")


def marker_porte():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    wood, roof = (150, 92, 50, 255), (176, 40, 44, 255)
    d.polygon([(0, 5), (8, 1), (15, 5)], fill=roof)
    d.rectangle((1, 5, 3, 14), fill=wood); d.rectangle((12, 5, 14, 14), fill=wood)
    d.rectangle((4, 6, 11, 14), fill=(70, 46, 30, 255))
    d.rectangle((7, 6, 8, 14), fill=(110, 70, 40, 255))
    img = outline(img)
    img.save(OUT / "marker_porte.png")


def compass():
    s = 40
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse((2, 2, s - 3, s - 3), fill=(236, 224, 190, 230), outline=INK)
    d.ellipse((6, 6, s - 7, s - 7), outline=(150, 120, 80, 255))
    # branches E/O/S
    for (x, y) in ((c, s - 5), (5, c), (s - 5, c)):
        d.polygon([(c, c), (x, y), (c + (2 if x == c else 0), c + (2 if y == c else 0))], fill=(80, 60, 44, 255))
    d.polygon([(c - 3, c), (c, s - 5), (c + 3, c)], fill=(80, 60, 44, 255))
    d.polygon([(c, c - 3), (5, c), (c, c + 3)], fill=(80, 60, 44, 255))
    d.polygon([(c, c - 3), (s - 5, c), (c, c + 3)], fill=(110, 86, 60, 255))
    # nord rouge
    d.polygon([(c - 4, c), (c, 4), (c + 4, c)], fill=(200, 40, 36, 255))
    d.polygon([(c, c), (c, 4), (c + 4, c)], fill=(150, 26, 24, 255))
    d.ellipse((c - 2, c - 2, c + 2, c + 2), fill=(201, 158, 74, 255))
    img.save(OUT / "compass.png")


def arrow():
    img = Image.new("RGBA", (13, 13), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(6, 0), (12, 12), (6, 9), (0, 12)], fill=(255, 255, 255, 255), outline=INK)
    d.polygon([(6, 2), (6, 8), (10, 11)], fill=(200, 40, 36, 255))
    img.save(OUT / "arrow.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    marker_lieu(); marker_pnj(); marker_porte(); compass(); arrow()
    print("->", OUT)
