# -*- coding: utf-8 -*-
"""
gen_natures_textures.py — symboles des NATURES de chakra et icônes de recharge du HUD.

1. Symboles des cinq natures (Katon, Suiton, Doton, Fūton, Raiton) — décision du 14/10 : « fais comme pour les clans
   ou les villages, prends les VRAIES SVG ». Source : tools/natures_canon/*.svg (icônes Narutopedia de ShounenSuki,
   CC BY-SA 3.0, cf. natures_canon/README.md) : octogone au dégradé radial de la nature, contour et caractère noirs.
   Même pipeline que les blasons de clan (tools/gen_clan_emblemes.py, importé) : rendu PyMuPDF à 1600 px, encre
   (le noir) recadrée dans 46/48 de la taille, ramenée à l'épaisseur des blasons (2,8 px à 48) par dilatation /
   érosion d'un disque, réduction LANCZOS et alpha franchi de la même façon ((a − 30) / 195) ; en COULEURS
   CANONIQUES : le dégradé de l'octogone vient du SVG lui-même (rendu une seconde fois sans le caractère, contour
   au dégradé) et l'encre reste noire, au lieu du blanc teinté par le code des blasons.
      textures/gui/nature/<nature>_64.png   64×64 (révélation du test de la Feuille)
      textures/gui/nature/<nature>_48.png   48×48 (la taille des blasons)
      textures/gui/nature/<nature>.png      32×32
      textures/gui/nature/<nature>_16.png   16×16
   Chaque taille est rendue depuis le SVG ; ui/NatureIcones.java pose celle qui tombe juste au pixel écran. Ces
   symboles remplacent les kanji de nature (fiche, lecture d'un rouleau) et sont la pièce maîtresse du test de la
   Feuille (révélation).

2. Icônes de RECHARGE / d'état du HUD (combat/CooldownHud), peintes par tools/gen_icones_peintes.py (importé, non
   modifié) :
      textures/gui/ability/combat.png, course_chakra.png (4 images, 100 ms), garde.png  (32 px)
      textures/gui/ability/<nom>_16.png pour toutes les icônes du HUD (dash, double_jump, chakra_jump comprises :
      réduction par moyenne 2×2 des icônes dessinées par l'utilisateur), utilisées quand l'icône tombe à 16 px écran.
   L'esquive n'est PAS intégrée (en attente). Les icônes peintes katon / suiton / … ne servent pas : chaque technique
   aura plus tard sa propre icône.

    python tools/gen_natures_textures.py     (+ Maquettes/test_feuille/planche_symboles_natures_vs_clans.png)
"""
import os
import re
import sys
import tempfile

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ICI)
import gen_icones_peintes as gp  # noqa: E402
import gen_clan_emblemes as ce  # noqa: E402

GUI = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui")
NATURE = os.path.join(GUI, "nature")
ABILITY = os.path.join(GUI, "ability")

NATURES = ("katon", "suiton", "doton", "futon", "raiton")
CANON = os.path.join(ICI, "natures_canon")
MAQ = os.path.normpath(os.path.join(ICI, "..", "..", "..", "..", "Maquettes", "test_feuille"))

RECHARGES = [
    ("combat", "rouge", gp.ic_combat, 1),
    ("course_chakra", "bleu", gp.ic_course_chakra, 4),
    ("garde", "rouge", gp.ic_garde, 1),
]

UTILISATEUR = ("dash", "double_jump", "chakra_jump")


def cadre_ep(n, fam, ep):
    """Cadre du gabarit de l'utilisateur (gp.cadre) à une épaisseur quelconque (3 px pour 48)."""
    p = gp.FAMILLES[fam]
    col = {k: tuple(int(p[k][i:i + 2], 16) for i in (0, 2, 4)) + (255,) for k in "abcdeg"}
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = im.load()
    for y in range(n):
        k = gp.GABARIT_COTE[min(31, round(y * 31 / (n - 1)))]
        for x in range(n):
            if not (y < ep or y >= n - ep or x < ep or x >= n - ep):
                continue
            c = "a" if y < ep else "e" if y >= n - ep else k
            if c == "T":
                c = "a" if y < n / 2 else "e"
            if x >= n - ep and ep < y < n - ep and round(y * 31 / (n - 1)) == 17:
                c = "g"
            px[x, y] = col[c]
    for (x, y) in ((0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)):
        px[x, y] = (0, 0, 0, 0)
    # coins adoucis d'un texel de plus à 48 (le gabarit 32 n'en retire qu'un)
    if ep >= 3:
        for (x, y) in ((1, 0), (0, 1), (n - 2, 0), (n - 1, 1), (0, n - 2), (1, n - 1), (n - 2, n - 1), (n - 1, n - 2)):
            px[x, y] = (0, 0, 0, 0)
    return im


def icone(fn, fam, n, f, nf):
    """Une image de l'icône à n px (cadre 1 / 2 / 3 px selon n), peinte par la Toile de gen_icones_peintes."""
    ep = 1 if n < 32 else 2 if n < 48 else 3
    t = gp.Toile(n - 2 * ep, fam, seed=3)
    fn(t, f, nf)
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    im.paste(t.reduire(0.6 if n < 32 else 0.55).convert("RGBA"), (ep, ep))
    im.alpha_composite(cadre_ep(n, fam, ep))
    return im


def bande(fn, fam, n, nf):
    b = Image.new("RGBA", (n, n * nf), (0, 0, 0, 0))
    for f in range(nf):
        b.alpha_composite(icone(fn, fam, n, f, nf), (0, n * f))
    return b


def moitie(im):
    """Réduction 2:1 par moyenne (alpha prémultiplié) : version 16 px d'une icône 32 px dessinée à la main."""
    a = np.asarray(im.convert("RGBA"), np.float32) / 255.0
    rgb, al = a[..., :3] * a[..., 3:4], a[..., 3:4]
    h, w = al.shape[:2]
    rs = rgb.reshape(h // 2, 2, w // 2, 2, 3).mean(axis=(1, 3))
    als = al.reshape(h // 2, 2, w // 2, 2, 1).mean(axis=(1, 3))
    out = np.where(als > 1e-4, rs / np.maximum(als, 1e-4), 0)
    als = np.where(als > 0.6, 1.0, np.where(als < 0.3, 0.0, als))  # bords nets (coins transparents gardés)
    o = np.concatenate([out, als], axis=-1)
    return Image.fromarray((np.clip(o, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")




# ═══════════════════════════════════ symboles canoniques des natures ═══════════════════════════════════
def _degrade(nom):
    """Dégradé radial canonique de l'octogone, lu dans le SVG : (centre x, y, rayon) en unités SVG, couleur au centre,
    couleur au bord. MuPDF ne rend pas les dégradés SVG (l'octogone sortirait noir) : on le peint nous-mêmes."""
    txt = open(os.path.join(CANON, nom + ".svg"), encoding="utf-8").read()
    rad = re.search(r"<radialGradient([^>]*)/?>", txt, flags=re.S).group(1)
    val = lambda k: float(re.search(r'(?:^|\s)' + k + r'="([-0-9.e]+)"', rad).group(1))  # noqa: E731
    cx, cy, r = val("cx"), val("cy"), val("r")
    m = re.search(r'gradientTransform="matrix\(([^)]*)\)"', rad)
    if m:
        a_, b_, c_, d_, e_, f_ = (float(v) for v in m.group(1).split(","))
        cx, cy, r = a_ * cx + c_ * cy + e_, b_ * cx + d_ * cy + f_, r * a_
    lien = re.search(r'xlink:href="#([^"]+)"', rad).group(1)
    lin = re.search(r'<linearGradient id="' + re.escape(lien) + r'">(.*?)</linearGradient>', txt, flags=re.S).group(1)
    stops = sorted((float(re.search(r'offset="([0-9.]+)"', st).group(1)),
                    re.search(r"stop-color:#([0-9a-fA-F]{6})", st).group(1))
                   for st in re.findall(r"<stop[^>]*/>", lin))
    rgb = lambda h: np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)  # noqa: E731
    return cx, cy, r, rgb(stops[0][1]), rgb(stops[-1][1])


def symbole(nom, T):
    """Symbole canonique T×T au traitement des blasons de clan (voir l'en-tête)."""
    K = ce.K
    # rendu de l'encre : le dégradé (que MuPDF ne sait pas peindre, il le rend noir) remplacé par du blanc
    txt = open(os.path.join(CANON, nom + ".svg"), encoding="utf-8").read()
    txt = re.sub(r"fill:url\(#[^)]+\)", "fill:#ffffff", txt)
    f = tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False, encoding="utf-8")
    f.write(txt)
    f.close()
    try:
        orig = np.asarray(ce.rendu_svg(f.name, 1600)).astype(np.float32) / 255
    finally:
        os.remove(f.name)
    # le fond : dégradé radial du SVG peint sur la grille du rendu (1600 px pour 100 unités)
    cx, cy, r, c0, c1 = _degrade(nom)
    u = 100.0 / 1600
    yy, xx = np.mgrid[0:orig.shape[0], 0:orig.shape[1]].astype(np.float32)
    t = np.clip(np.hypot((xx + 0.5) * u - cx, (yy + 0.5) * u - cy) / r, 0, 1)[..., None]
    fond = c0 * (1 - t) + c1 * t
    forme = orig[..., 3] > 0.5
    encre = forme & (orig[..., :3].mean(axis=2) < 0.22)
    ys, xs = np.nonzero(forme)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    N, cote = T * K, round(T * 46 / 48) * K

    def place(img_mode, arr):
        crop = arr[y0:y1, x0:x1]
        if img_mode == "L":
            im = Image.fromarray((crop * 255).astype(np.uint8), "L").resize((cote, cote), Image.LANCZOS)
            out = Image.new("L", (N, N), 0)
        else:
            im = Image.fromarray((np.clip(crop, 0, 1) * 255).astype(np.uint8), "RGB").resize((cote, cote), Image.LANCZOS)
            out = Image.new("RGB", (N, N), (0, 0, 0))
        out.paste(im, ((N - cote) // 2, (N - cote) // 2))
        return np.asarray(out)

    forme_n = place("L", forme) > 127
    encre_n = place("L", encre) > 127
    ep = max(1.0, ce.EP * T / 48)                     # 2,8 px à 48 comme les blasons, proportionnel ailleurs
    encre_n = ce.ajuste(encre_n, ce.epaisseur(encre_n), ep * K) & forme_n
    rgb_fond = place("RGB", fond).astype(np.float32) / 255

    def reduire(m):
        v = np.asarray(Image.fromarray((m * 255).astype(np.uint8), "L").resize((T, T), Image.LANCZOS), np.float32)
        return np.clip((v - 30) / (225 - 30), 0, 1)

    alpha, cov = reduire(forme_n), reduire(encre_n)
    w = forme_n.astype(np.float32)
    acc = (rgb_fond * w[..., None]).reshape(T, K, T, K, 3).sum(axis=(1, 3))
    n = w.reshape(T, K, T, K).sum(axis=(1, 3))[..., None]
    moy = (rgb_fond * w[..., None]).sum(axis=(0, 1)) / max(1.0, w.sum())
    grad = np.where(n > 0, acc / np.maximum(n, 1), moy)
    noir = np.array([0.0, 0.0, 0.0])
    rgb = grad * (1 - cov[..., None]) + noir * cov[..., None]
    out = np.zeros((T, T, 4), np.uint8)
    out[..., :3] = (np.clip(rgb, 0, 1) * 255 + 0.5).astype(np.uint8)
    out[..., 3] = (alpha * 255 + 0.5).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def planche_comparaison(ims):
    """Les cinq symboles (48, 32, 16 px ; ×4 puis ×1) au-dessus de blasons de clan, sur la laque des menus."""
    from PIL import ImageDraw, ImageFont
    clans = ["uchiha", "senju", "hyuga", "uzumaki", "nara", "sarutobi"]
    W = Image.new("RGBA", (1180, 560), (28, 18, 22, 255))
    d = ImageDraw.Draw(W)
    try:
        f = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 14)
    except OSError:
        f = ImageFont.load_default()
    creme = (245, 233, 208)
    d.text((20, 12), "Symboles canoniques des natures (Narutopedia, ShounenSuki, CC BY-SA 3.0) - 48 px x4, puis 48 / 32 / 16 x1 et 32 x2",
           font=f, fill=creme)
    for i, n in enumerate(NATURES):
        x = 20 + i * 230
        W.alpha_composite(ims[(n, 48)].resize((192, 192), Image.NEAREST), (x, 40))
        W.alpha_composite(ims[(n, 48)], (x, 246))
        W.alpha_composite(ims[(n, 32)], (x + 54, 254))
        W.alpha_composite(ims[(n, 16)], (x + 92, 262))
        W.alpha_composite(ims[(n, 32)].resize((64, 64), Image.NEAREST), (x + 116, 238))
        d.text((x, 306), n, font=f, fill=creme)
    d.text((20, 336), "Blasons de clan du mod (meme pipeline, blancs teintes creme par le code) - 48 px x3, puis x1",
           font=f, fill=creme)
    for i, c in enumerate(clans):
        p = os.path.join(GUI, "clans", f"clan_{c}.png")
        if not os.path.exists(p):
            continue
        a = np.asarray(Image.open(p).convert("RGBA")).astype(np.float32)
        a[..., 0], a[..., 1], a[..., 2] = creme
        im = Image.fromarray(a.astype(np.uint8), "RGBA")
        x = 20 + i * 190
        W.alpha_composite(im.resize((144, 144), Image.NEAREST), (x, 362))
        W.alpha_composite(im, (x + 150, 362))
        d.text((x, 512), c, font=f, fill=(180, 160, 130))
    os.makedirs(MAQ, exist_ok=True)
    chemin = os.path.join(MAQ, "planche_symboles_natures_vs_clans.png")
    W.convert("RGB").save(chemin)
    return chemin


def main():
    os.makedirs(NATURE, exist_ok=True)
    ims = {}
    for nom in NATURES:
        for T, suf in ((64, "_64"), (48, "_48"), (32, ""), (16, "_16")):
            ims[(nom, T)] = symbole(nom, T)
            ims[(nom, T)].save(os.path.join(NATURE, nom + suf + ".png"))
    for nom, fam, fn, nf in RECHARGES:
        bande(fn, fam, 32, nf).save(os.path.join(ABILITY, nom + ".png"))
        bande(fn, fam, 16, nf).save(os.path.join(ABILITY, nom + "_16.png"))
    for nom in UTILISATEUR:
        moitie(Image.open(os.path.join(ABILITY, nom + ".png"))).save(os.path.join(ABILITY, nom + "_16.png"))
    print("ok : natures ->", os.path.normpath(NATURE), "; recharges ->", os.path.normpath(ABILITY))
    print("planche :", planche_comparaison(ims))


if __name__ == "__main__":
    main()
