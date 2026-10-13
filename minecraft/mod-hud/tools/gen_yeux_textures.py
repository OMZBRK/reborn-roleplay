"""Textures pixel art des yeux pour l'écran de chirurgie (prélèvement / greffe).

Génère dans src/main/resources/assets/reborn/textures/gui/yeux/ :
  peau.png             patch de visage rond (96x96) : peau ombrée, sourcil, arcade
  oeil_<type>.png      l'œil dans ses paupières (72x44) : normal, sharingan, byakugan, rinnegan
  orbite.png           l'orbite vide, sang (72x44)
  globe_<type>.png     le globe retiré (36x36)
  plateau.png          plateau laqué rond, liseré or, ombre (112x112)

Usage : python tools/gen_yeux_textures.py   (Pillow requis)
Œil gauche du patient (vu de face, il est à droite de l'écran) : coin interne à gauche, coin externe à droite.
"""
import math
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "yeux")

def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))

# --- palette peau
SKIN_HI, SKIN, SKIN_SH, SKIN_DEEP, SKIN_LINE = (hexc("#E6BE9C"), hexc("#CF9F7C"), hexc("#B07E5E"),
                                                hexc("#8E5E44"), hexc("#6A4030"))
LASH, LID_LOW, CARUNCLE = hexc("#1E100C"), hexc("#7E4C3A"), hexc("#D98A84")
SCL_HI, SCL, SCL_SH, SCL_DEEP = hexc("#FBF8F3"), hexc("#EFE8E0"), hexc("#CFC5BE"), hexc("#A89C96")

W, H = 72, 44
CX, CY = 36, 22
AW, AH = 29, 12          # demi-largeur / demi-hauteur de l'amande

def almond(dy):
    """demi-largeur de l'amande à la hauteur dy du centre (paupière haute un peu plus bombée)."""
    h = AH + (1.5 if dy < 0 else -0.5)
    rl = (AW * AW + h * h) / (2 * h)
    c = rl - h
    d = abs(dy) + c
    v = rl * rl - d * d
    return math.sqrt(v) if v > 0 else 0.0

def inside(x, y):
    return abs(x + 0.5 - CX) < almond(y + 0.5 - CY)

def upper_edge(x):
    """y (float) du bord supérieur de l'amande à la colonne x."""
    for y in range(H):
        if inside(x, y):
            return y
    return None

def lower_edge(x):
    for y in range(H - 1, -1, -1):
        if inside(x, y):
            return y
    return None

def iris_colors(kind):
    return {
        "normal":    (hexc("#2E1A0E"), hexc("#5E3A1E"), hexc("#9A6A3A"), hexc("#140A06")),
        "sharingan": (hexc("#5A0408"), hexc("#B8141C"), hexc("#E8444A"), hexc("#2A0204")),
        "byakugan":  (hexc("#B6AECA"), hexc("#DCD7EA"), hexc("#F2F0F8"), hexc("#9C92B4")),
        "rinnegan":  (hexc("#5E4E8E"), hexc("#9A8AC6"), hexc("#C8BCE8"), hexc("#2E2450")),
    }[kind]

def draw_iris(img, cx, cy, r, kind, clip=None, rot=0.0):
    top, mid, low, ring = iris_colors(kind)
    px = img.load()
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if not (0 <= x < img.width and 0 <= y < img.height):
                continue
            if clip and not clip(x, y):
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            t = (dy + r) / (2 * r)                       # haut sombre -> bas clair
            col = mix(top, mid, t * 1.6) if t < 0.62 else mix(mid, low, (t - 0.62) / 0.38)
            if d > r - 1.1:
                col = ring                                 # anneau limbique
            elif d > r - 2.2:
                col = mix(col, ring, 0.45)
            px[x, y] = col
    pupil = {"normal": 3.6, "sharingan": 2.6, "byakugan": 0, "rinnegan": 1.6}[kind]
    for y in range(int(cy - r), int(cy + r + 1)):
        for x in range(int(cx - r), int(cx + r + 1)):
            if not (0 <= x < img.width and 0 <= y < img.height) or (clip and not clip(x, y)):
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if kind == "sharingan" and abs(d - r * 0.56) < 0.55:
                px[x, y] = hexc("#2A0204")                 # cercle des tomoe
            if kind == "rinnegan":
                for k in (0.32, 0.55, 0.78):
                    if abs(d - r * k) < 0.5:
                        px[x, y] = hexc("#2E2450")
            if kind == "byakugan" and abs(d - r * 0.62) < 0.5:
                px[x, y] = hexc("#C4BAD8")
            if pupil and d <= pupil:
                px[x, y] = hexc("#080404")
    if kind == "sharingan":                                 # trois tomoe : virgule = disque + queue
        for k in range(3):
            a = rot + k * 2 * math.pi / 3 - math.pi / 2
            tx, ty = cx + math.cos(a) * r * 0.56, cy + math.sin(a) * r * 0.56
            for y in range(int(ty - 3), int(ty + 4)):
                for x in range(int(tx - 3), int(tx + 4)):
                    if not (0 <= x < img.width and 0 <= y < img.height) or (clip and not clip(x, y)):
                        continue
                    if math.hypot(x + 0.5 - tx, y + 0.5 - ty) <= max(1.7, r * 0.19):
                        px[x, y] = hexc("#080404")
            for s in range(1, 7):                            # queue qui s'enroule et s'affine
                b = a + 0.16 * s
                rr = r * (0.56 + 0.035 * s)
                qx, qy = cx + math.cos(b) * rr, cy + math.sin(b) * rr
                for (ox, oy) in ((0, 0), (0.5, 0)) if s < 4 else ((0, 0),):
                    x, y = int(qx + ox), int(qy + oy)
                    if 0 <= x < img.width and 0 <= y < img.height and (not clip or clip(x, y)):
                        px[x, y] = hexc("#080404")
    # reflets
    for (hx, hy, c) in ((cx - r * 0.42, cy - r * 0.45, hexc("#FFFFFF")), (cx - r * 0.42 + 1, cy - r * 0.45, hexc("#FFFFFF", 200)),
                        (cx - r * 0.42, cy - r * 0.45 + 1, hexc("#FFFFFF", 200)), (cx + r * 0.38, cy + r * 0.36, hexc("#FFFFFF", 150))):
        x, y = int(hx), int(hy)
        if 0 <= x < img.width and 0 <= y < img.height and (not clip or clip(x, y)):
            px[x, y] = c

def eye(kind):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    # peau (pour fondre l'œil dans le patch) : léger halo d'ombre autour de l'amande
    for y in range(H):
        for x in range(W):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            e = math.hypot(dx / (AW + 6), dy / (AH + 7))
            if e < 1.0:
                px[x, y] = mix(hexc("#B07E5E", 0), hexc("#9A6A4E", 150), (1 - e) * 1.4)
    # blanc de l'œil, ombré sous la paupière et vers les coins
    for y in range(H):
        for x in range(W):
            if not inside(x, y):
                continue
            ue = upper_edge(x)
            down = (y - ue) if ue is not None else 9
            col = SCL
            if down <= 0: col = SCL_DEEP
            elif down <= 1: col = SCL_SH
            elif down <= 3: col = mix(SCL_SH, SCL, (down - 1) / 2)
            edge = abs(x + 0.5 - CX) / AW
            if edge > 0.72: col = mix(col, SCL_SH, (edge - 0.72) / 0.28)
            if 2 < down < 6 and abs(x + 0.5 - CX) < AW * 0.5: col = mix(col, SCL_HI, 0.4)
            px[x, y] = col
    # caroncule (coin interne, à gauche)
    for (x, y) in ((CX - AW + 1, CY), (CX - AW + 2, CY), (CX - AW + 2, CY + 1), (CX - AW + 1, CY - 1)):
        px[x, y] = CARUNCLE
    # iris, découpé par les paupières (le haut est caché par la paupière)
    draw_iris(img, CX, CY + 1, 12.4, kind, clip=lambda x, y: inside(x, y), rot=0.35)
    # ombre de la paupière supérieure sur l'iris
    for x in range(W):
        ue = upper_edge(x)
        if ue is None: continue
        for k, a in ((0, 120), (1, 60)):
            y = ue + k
            if inside(x, y):
                r, g, b, al = px[x, y]
                px[x, y] = (round(r * (1 - a / 255 * 0.6)), round(g * (1 - a / 255 * 0.6)), round(b * (1 - a / 255 * 0.6)), al)
    # trait de paupière supérieure épais + cils, paupière inférieure fine
    for x in range(W):
        ue, le = upper_edge(x), lower_edge(x)
        if ue is None: continue
        thick = 2 if abs(x + 0.5 - CX) < AW * 0.85 else 1
        for k in range(1, thick + 1):
            if ue - k >= 0: px[x, ue - k] = LASH
        if le + 1 < H: px[x, le + 1] = LID_LOW if abs(x + 0.5 - CX) < AW * 0.8 else SKIN_DEEP
    # pli de la paupière (au-dessus du trait)
    for x in range(W):
        ue = upper_edge(x)
        if ue is None or abs(x + 0.5 - CX) > AW * 0.75: continue
        y = ue - 5
        if y >= 0: px[x, y] = SKIN_LINE[:3] + (130,)
    # cils de l'angle externe (à droite), qui remontent
    ox = CX + AW
    for (dx, dy) in ((0, -1), (1, -2), (2, -2), (3, -3), (1, -1), (2, -1), (3, -1)):
        x, y = ox + dx - 1, CY - 1 + dy
        if 0 <= x < W and 0 <= y < H: px[x, y] = LASH
    if kind == "byakugan":                                  # veines gonflées vers les tempes
        for (x0, y0, steps, dir_) in ((CX + AW + 2, CY - 3, 7, 1), (CX - AW - 2, CY - 3, 7, -1)):
            x, y = x0, y0
            for s in range(steps):
                if 0 <= x < W and 0 <= y < H: px[x, y] = hexc("#8E5E50")
                x += dir_
                y -= 1 if s % 2 == 0 else 0
    return img

def socket():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            e = math.hypot(dx / (AW + 6), dy / (AH + 7))
            if e < 1.0:
                px[x, y] = mix(hexc("#8E5444", 0), hexc("#7A3A30", 190), (1 - e) * 1.5)
    for y in range(H):
        for x in range(W):
            if not inside(x, y): continue
            d = math.hypot((x + 0.5 - CX) / AW, (y + 0.5 - CY) / AH)
            px[x, y] = mix(hexc("#1A0606"), hexc("#4A1612"), d ** 1.5)
            if d > 0.86: px[x, y] = hexc("#6A1E18")
    for x in range(W):
        ue, le = upper_edge(x), lower_edge(x)
        if ue is None: continue
        if ue - 1 >= 0: px[x, ue - 1] = hexc("#3A1A14")
        if le + 1 < H: px[x, le + 1] = hexc("#5A2A20")
    for (bx, by, r) in ((CX - 14, CY + 9, 2.2), (CX - 12, CY + 13, 1.4), (CX + 17, CY + 8, 1.8), (CX + 3, CY + 12, 1.2)):
        for y in range(int(by - 3), int(by + 4)):
            for x in range(int(bx - 3), int(bx + 4)):
                if 0 <= x < W and 0 <= y < H and math.hypot(x + 0.5 - bx, y + 0.5 - by) <= r:
                    px[x, y] = hexc("#8A0E14") if math.hypot(x + 0.5 - bx + 0.5, y + 0.5 - by + 0.5) > r * 0.5 else hexc("#C02A30")
    return img

def globe(kind):
    S = 36
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    c = S / 2
    R = S / 2 - 1.5
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy)
            if d > R: continue
            light = max(0.0, 1 - math.hypot(dx + R * 0.35, dy + R * 0.35) / (R * 1.6))
            col = mix(SCL_DEEP, SCL_HI, 0.25 + light * 0.9)
            if d > R - 1: col = hexc("#8A7C76")
            px[x, y] = col
    for k in range(6):                                       # veinules rouges
        a = k * 1.05 + 0.2
        for s in range(5):
            r_ = R - 1 - s * 1.3
            x, y = int(c + math.cos(a + s * 0.08) * r_), int(c + math.sin(a + s * 0.08) * r_)
            if 0 <= x < S and 0 <= y < S: px[x, y] = hexc("#C24A48", 200)
    draw_iris(img, c + 1, c + 1, 8.5, kind, clip=lambda x, y: math.hypot(x + 0.5 - c, y + 0.5 - c) <= R, rot=0.2)
    return img

def skin():
    S = 96
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    c = S / 2
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy)
            if d > S / 2 - 0.5: continue
            # lumière en haut à gauche, ombre en bas à droite, creux de l'orbite au centre
            l = (-dx - dy) / S
            col = mix(SKIN_SH, SKIN_HI, 0.5 + l * 1.3)
            hollow = math.hypot(dx / 38, (dy - 2) / 24)
            if hollow < 1: col = mix(col, SKIN_SH, (1 - hollow) * 0.55)
            if d > S / 2 - 2.5: col = mix(col, SKIN_DEEP, 0.6)
            px[x, y] = col
    # sourcil : épais côté nez, monte jusqu'à un pic puis s'affine vers la tempe (aplat propre, liseré clair)
    for x in range(15, 84):
        t = (x - 15) / 68
        yc = 29 - 8 * math.sin(min(1.0, t / 0.68) * math.pi / 2) if t < 0.68 else 21 + (t - 0.68) / 0.32 * 5
        th = 5.0 - 2.0 * min(1.0, t / 0.68) if t < 0.68 else 3.0 - 1.8 * (t - 0.68) / 0.32
        y0, y1 = int(round(yc - th / 2)), int(round(yc + th / 2))
        for y in range(y0, y1 + 1):
            if 0 <= y < S:
                px[x, y] = hexc("#4A2C1C") if y == y0 else hexc("#2A160E")
    # arcade (ombre sous le sourcil)
    for x in range(18, 80):
        t = (x - 18) / 62
        y = int(34 - math.sin(t * math.pi) * 4)
        for k in range(2):
            if 0 <= y + k < S:
                r, g, b, a = px[x, y + k]
                px[x, y + k] = (round(r * 0.88), round(g * 0.88), round(b * 0.88), a)
    return img

def plateau():
    """Plateau laqué rond sous la tête du patient (rayon ~52 texels = 1,78 × la demi-largeur de l'œil)."""
    S = 112
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    c = S / 2 - 1.5
    R = 52
    for y in range(S):
        for x in range(S):
            d_sh = math.hypot(x + 0.5 - (c + 1.5), y + 0.5 - (c + 2.5))
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if d > R and d_sh <= R:
                px[x, y] = (0, 0, 0, 110)                            # ombre portée
            if d > R:
                continue
            if d > R - 2.2:                                          # liseré or, éclairé en haut à gauche
                a = math.atan2(y + 0.5 - c, x + 0.5 - c)
                lit = 0.5 + 0.5 * math.cos(a + 2.35)
                px[x, y] = mix(hexc("#8A6428"), hexc("#F2D49A"), lit)
            elif d > R - 3.2:
                px[x, y] = hexc("#5A3A18")
            else:                                                    # laque noire-rouge, reflet en arc
                t = d / R
                col = mix(hexc("#3A1214"), hexc("#1E0A0C"), t)
                a = math.atan2(y + 0.5 - c, x + 0.5 - c)
                if 0.78 < t < 0.86 and -2.6 < a < -1.2:
                    col = mix(col, hexc("#7A3A34"), 0.55)
                if abs(d - (R - 7)) < 0.6:
                    col = hexc("#6E2A26")                            # filet intérieur
                px[x, y] = col
    return img

def main():
    os.makedirs(OUT, exist_ok=True)
    skin().save(os.path.join(OUT, "peau.png"))
    plateau().save(os.path.join(OUT, "plateau.png"))
    socket().save(os.path.join(OUT, "orbite.png"))
    for k in ("normal", "sharingan", "byakugan", "rinnegan"):
        eye(k).save(os.path.join(OUT, f"oeil_{k}.png"))
        globe(k).save(os.path.join(OUT, f"globe_{k}.png"))
    print("textures ->", os.path.abspath(OUT))

if __name__ == "__main__":
    main()
