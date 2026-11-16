"""Barre de mudras (mod reborn-hud) : les textures de la barre, dans la direction artistique des menus Reborn.

Maquette retenue le 16/11 (« R2 avec les signes comme avant, dessins lisses ») : douze cases du kit des menus (laque en
creux, liseré d'or biseauté, les cases de la sacoche agrandies), sans plaque derrière ; dans chaque case, le vrai dessin
du signe.

  assets/reborn/textures/gui/mudras/cases.png : 2 cases de 34 × 34 dans des cellules de 38 × 38 (76 × 38), pixel art au
      texel : 0 au repos (laque noire), 1 choisie (laque rouge, équerres d'angle). L'ombre portée d'un texel est dans
      la cellule ; la case est en (2, 1).
  assets/reborn/textures/gui/mudras/mains_petit.png : les mêmes mains réduites à 48 × 48 (576 × 48), pour les petites
      fenêtres (texel de 1 px : la main fait 30 px, réduite depuis 96 elle crénelait).
  assets/reborn/textures/gui/mudras/mains.png : 12 mains de 96 × 96 en ligne (1152 × 96), dans l'ordre du zodiaque (Rat,
      Buffle, Tigre, Lièvre, Dragon, Serpent, Cheval, Bélier, Singe, Coq, Chien, Sanglier), sur fond transparent : mains
      ivoire, cernées, avec les traits des doigts. Lissée (mains.png.mcmeta « blur ») : elle se réduit sans crénelage.

Source des mains : tools/mudras_src/<signe>.svg, dessins de Schlum (Wikimedia Commons, CC BY-SA 4.0, voir
tools/mudras_src/LICENCE.txt), fournis par le propriétaire le 15/11 (« prends les vraies images de mudra »). mains.png
est une œuvre dérivée, sous la même licence (crédit posé à côté d'elle : credits.txt).

Chaque dessin (mains blanches, trait noir fin, ombres grises) est rendu en grand, ramené à deux masques (la main, ses
traits), les traits épaissis pour rester lisibles, puis posé dans son carré : les avant-bras qui dépassent sont coupés
par le bord. Les ombres grises sont ramenées à l'ivoire (aplats).

Usage : python -X utf8 minecraft/mod-hud/tools/gen_mudras_textures.py [--apercu chemin.png]
        (PyMuPDF et Pillow requis ; les cases demandent la boîte à outils de style du mod, tools/ecrans_art.py)
"""
import os
import sys

import pymupdf
from PIL import Image, ImageChops, ImageFilter

T = 96          # côté d'une main dans la texture
K = 4           # suréchantillonnage de la composition
GRAND = 1400    # plus grand côté du rendu d'un dessin
N = 34          # côté d'une case, en texels
CELLULE = 38    # cellule de l'atlas des cases (la case en (2, 1), son ombre autour)
ICI = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ICI, "mudras_src")
SORTIE = os.path.normpath(os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui",
                                       "mudras"))

MAIN = (247, 242, 230, 255)
TRAIT = (58, 40, 30, 255)

ORDRE = ["rat", "boeuf", "tigre", "lievre", "dragon", "serpent", "cheval", "belier", "singe", "coq", "chien", "sanglier"]

LARGEUR = 1.22      # un dessin large peut déborder de son carré jusqu'à cette part du côté (les avant-bras)
HAUTEUR = 0.94      # un dessin haut tient dans cette part du côté
CERNE, DOIGT = 1.5, 1.15   # épaisseurs voulues à 96 px : contour de la main, traits intérieurs


def rendre(nom):
    """Le dessin en grand : (masque de la main, masque de ses traits)."""
    doc = pymupdf.open(os.path.join(SRC, nom + ".svg"))
    page = doc[0]
    z = GRAND / max(page.rect.width, page.rect.height)
    pix = page.get_pixmap(matrix=pymupdf.Matrix(z, z), alpha=True)
    img = Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)
    doc.close()
    forme = img.getchannel("A").point(lambda v: 255 if v >= 96 else 0)
    gris = img.convert("L")
    traits = Image.composite(gris.point(lambda v: 255 if v < 120 else 0), Image.new("L", img.size, 0), forme)
    boite = forme.getbbox()
    return forme.crop(boite), traits.crop(boite)


def main(nom):
    """La main du signe dans son carré de T × T, sur fond transparent."""
    n = T * K
    forme, traits = rendre(nom)
    k = min(LARGEUR * n / forme.width, HAUTEUR * n / forme.height)
    taille = (max(1, round(forme.width * k)), max(1, round(forme.height * k)))
    # traits épaissis avant la réduction, pour faire DOIGT pixels à l'arrivée
    r = max(1, round(DOIGT * K / k / 2))
    traits = traits.filter(ImageFilter.MaxFilter(2 * r + 1))
    forme = forme.resize(taille, Image.LANCZOS)
    traits = traits.resize(taille, Image.LANCZOS)
    pos = (round((n - taille[0]) / 2), round((n - taille[1]) / 2))
    m, t = Image.new("L", (n, n), 0), Image.new("L", (n, n), 0)
    m.paste(forme, pos)
    t.paste(traits, pos)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    # la main, son cerne sombre autour, ses traits dedans
    img.paste(TRAIT, (0, 0), m.filter(ImageFilter.MaxFilter(2 * round(CERNE * K) + 1)))
    img.paste(MAIN, (0, 0), m)
    img.paste(TRAIT, (0, 0), ImageChops.multiply(t, m))
    return img.resize((T, T), Image.LANCZOS)


def mains():
    out = Image.new("RGBA", (T * len(ORDRE), T), (0, 0, 0, 0))
    for i, nom in enumerate(ORDRE):
        out.paste(main(nom), (i * T, 0))
    return out


def cases():
    """Les deux cases du kit (repos, choisie), au texel, avec leur ombre portée."""
    sys.path.insert(0, ICI)
    from ecrans_art import fa, kit, hexc, rect, R_OR, nouveau   # boîte à outils de style du mod

    noir = ["0A0608", "0F090B", "140C0F", "1A1013", "211418"]
    rouge = ["3A0A12", "4E0D17", "63111D", "7A1322", "8E1628"]
    out = nouveau(2 * CELLULE, CELLULE)
    for i, choisie in enumerate((False, True)):
        x, y = i * CELLULE + 2, 1
        kit.ombre_portee(out, x, y, N, N, dx=1, dy=2, force=0.5, rayon=1)
        c = nouveau(N, N)
        fa.lq(c, 1, 1, N - 2, N - 2, rouge if choisie else noir, seed=5, reflet=choisie)
        # creux : ombre en haut et à gauche
        rect(c, 1, 1, N - 2, 1, hexc("0A0204"))
        rect(c, 1, 1, 1, N - 2, hexc("0A0204"))
        # liseré d'or biseauté : sombre en haut et à gauche, clair en bas et à droite
        b1, b2 = (R_OR[4], R_OR[7]) if choisie else (R_OR[3], R_OR[5])
        rect(c, 0, 0, N, 1, hexc(b1))
        rect(c, 0, 0, 1, N, hexc(b1))
        rect(c, 0, N - 1, N, 1, hexc(b2))
        rect(c, N - 1, 0, 1, N, hexc(b2))
        if choisie:
            fa.kanagus_mini(c, 0, 0, N, N)
        out.alpha_composite(c, (x, y))
    return out


CREDITS = """Mains des mudras (mains.png)

Tirées des dessins « Mudra-Naruto-*.svg » de Schlum, Wikimedia Commons
(https://commons.wikimedia.org/wiki/File:Mudra-Naruto-Tigre.svg), sous licence Creative Commons
Attribution - Partage dans les mêmes conditions 4.0 International (CC BY-SA 4.0,
https://creativecommons.org/licenses/by-sa/4.0/deed.fr).

Modifications : mains détourées, traits épaissis, ombres ramenées à l'ivoire (Reborn Roleplay, 2026).
Cette texture est diffusée sous la même licence CC BY-SA 4.0.
"""

if __name__ == "__main__":
    m, c = mains(), cases()
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        z = 2
        fond = Image.new("RGBA", (12 * 56 * z + 40, 60 * z), (52, 74, 50, 255))
        for i in range(12):
            cellule = c.crop((CELLULE if i == 2 else 0, 0, (2 if i == 2 else 1) * CELLULE, CELLULE))
            fond.alpha_composite(cellule.resize((CELLULE * z, CELLULE * z), Image.NEAREST), (20 + i * 56 * z, 20))
            h = m.crop((i * T, 0, (i + 1) * T, T)).resize((30 * z, 30 * z), Image.LANCZOS)
            fond.alpha_composite(h, (20 + i * 56 * z + 4 * z, 20 + 3 * z))
        fond.save(sys.argv[2])
        print("aperçu", sys.argv[2])
    else:
        os.makedirs(SORTIE, exist_ok=True)
        m.save(os.path.join(SORTIE, "mains.png"), optimize=True)
        petit = Image.new("RGBA", (48 * len(ORDRE), 48), (0, 0, 0, 0))
        for i in range(len(ORDRE)):
            petit.paste(m.crop((i * T, 0, (i + 1) * T, T)).resize((48, 48), Image.LANCZOS), (i * 48, 0))
        petit.save(os.path.join(SORTIE, "mains_petit.png"), optimize=True)
        c.save(os.path.join(SORTIE, "cases.png"), optimize=True)
        # filtrage linéaire pour les mains seulement : ce n'est pas du pixel art, elles sont réduites selon la fenêtre
        for nom in ("mains.png.mcmeta", "mains_petit.png.mcmeta"):
            with open(os.path.join(SORTIE, nom), "w", encoding="utf-8", newline="\n") as f:
                f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
        # nom en minuscules : un chemin de ressource à majuscules est refusé au chargement (ligne d'erreur au journal)
        with open(os.path.join(SORTIE, "credits.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write(CREDITS)
        print("écrit mains.png", m.size, "cases.png", c.size)
