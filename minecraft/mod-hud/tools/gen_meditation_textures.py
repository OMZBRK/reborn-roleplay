"""Texture du halo de méditation (18/10) — `assets/reborn/textures/effet/meditation.png`.

Une petite planche en niveaux de gris (le mod la teinte et l'additionne au sol) :
- disque radial 40×40 (1 texel = 1/16 de bloc : rayon 1,25 bloc), en aplats concentriques
  (7 tons, pas de tramage) : plateau doux au centre, léger anneau, bord qui s'éteint ;
- traînée de particule 1×4 en (44, 0) : tête claire en haut, queue qui s'efface.
RGB = intensité, alpha = intensité (le rendu additif ne lit que le RGB).

Lancement : python tools/gen_meditation_textures.py (depuis minecraft/mod-hud).
"""
import math
import os

from PIL import Image

W, H = 48, 40
D = 40                      # diamètre du disque (texels)
TONS = [0.0, 0.08, 0.18, 0.32, 0.50, 0.72, 0.90, 1.0]


def smoothstep(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def profil(r):
    """Intensité continue du halo à la distance normalisée r (0 centre, 1 bord)."""
    if r >= 0.98:
        return 0.0
    base = 1.0 - smoothstep(0.28, 0.98, r)
    anneau = 0.16 * math.exp(-((r - 0.60) / 0.13) ** 2)
    return max(0.0, min(1.0, 0.86 * base + anneau))


def ton(v):
    return min(TONS, key=lambda t: abs(t - v))


def main():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    c = D / 2.0
    for y in range(D):
        for x in range(D):
            r = math.hypot(x + 0.5 - c, y + 0.5 - c) / c
            v = int(round(ton(profil(r)) * 255))
            px[x, y] = (v, v, v, v)
    # traînée de particule (tête en haut)
    for k, v in enumerate([1.0, 0.62, 0.32, 0.12]):
        g = int(round(v * 255))
        px[44, k] = (g, g, g, g)
    out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "reborn",
                       "textures", "effet", "meditation.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("écrit", os.path.normpath(out))


if __name__ == "__main__":
    main()
