"""Barre de mudras (mod reborn-hud, 15/11) : les douze médaillons, tirés des vrais dessins des signes.

  assets/reborn/textures/gui/mudras/medaillons.png : 12 médaillons de 96 × 96 en ligne (1152 × 96), dans l'ordre du
  zodiaque (Rat, Buffle, Tigre, Lièvre, Dragon, Serpent, Cheval, Bélier, Singe, Coq, Chien, Sanglier). Un disque beige
  doré cerclé de sombre ; dessus, les mains du signe en blanc, cernées, avec les traits des doigts.

Source des mains : tools/mudras_src/<signe>.svg, dessins de Schlum (Wikimedia Commons, CC BY-SA 4.0, voir
tools/mudras_src/LICENCE.txt), fournis par le propriétaire le 15/11 (« prends les vraies images de mudra »). La texture
est une œuvre dérivée, sous la même licence (crédit posé à côté d'elle : CREDITS.txt).

Chaque dessin (mains blanches, trait noir fin, ombres grises) est rendu en grand, ramené à deux masques (la main, ses
traits), les traits épaissis pour rester lisibles à 96 px, puis posé sur le disque : les avant-bras qui dépassent sont
coupés par le bord du disque. Les ombres grises sont ramenées au blanc (aplats).

Usage : python -X utf8 minecraft/mod-hud/tools/gen_mudras_textures.py [--apercu chemin.png]
        (PyMuPDF et Pillow requis)
"""
import os
import sys

import pymupdf
from PIL import Image, ImageChops, ImageDraw, ImageFilter

T = 96          # côté d'un médaillon dans la texture
K = 4           # suréchantillonnage de la composition
GRAND = 1400    # plus grand côté du rendu d'un dessin
ICI = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ICI, "mudras_src")
SORTIE = os.path.normpath(os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui",
                                       "mudras"))

DISQUE = (214, 186, 128, 255)
DISQUE_HAUT = (226, 201, 146, 255)
CERCLE = (74, 56, 34, 255)
MAIN = (250, 248, 242, 255)
TRAIT = (70, 52, 34, 255)

ORDRE = ["rat", "boeuf", "tigre", "lievre", "dragon", "serpent", "cheval", "belier", "singe", "coq", "chien", "sanglier"]

R_BORD, R_FOND = T / 2 - 0.5, T / 2 - 4.0      # rayons du cercle sombre et du fond doré
LARGEUR = 1.30      # un dessin large peut déborder du disque jusqu'à cette part de son diamètre (les avant-bras)
HAUTEUR = 0.90      # un dessin haut tient dans cette part du diamètre
CERNE, DOIGT = 1.6, 1.15   # épaisseurs voulues à 96 px : contour de la main, traits intérieurs

# Réglage par signe : (grossissement, décalage x, décalage y) en parts du diamètre, pour centrer les mains.
REGLAGE = {}


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


def medaillon(nom):
    n = T * K
    forme, traits = rendre(nom)
    gros, dx, dy = REGLAGE.get(nom, (1.0, 0.0, 0.0))
    diam = 2 * R_FOND * K
    k = min(LARGEUR * diam / forme.width, HAUTEUR * diam / forme.height) * gros
    taille = (max(1, round(forme.width * k)), max(1, round(forme.height * k)))
    # traits épaissis avant la réduction, pour faire DOIGT pixels à l'arrivée
    r = max(1, round(DOIGT * K / k / 2))
    traits = traits.filter(ImageFilter.MaxFilter(2 * r + 1))
    forme = forme.resize(taille, Image.LANCZOS)
    traits = traits.resize(taille, Image.LANCZOS)
    pos = (round((n - taille[0]) / 2 + dx * diam), round((n - taille[1]) / 2 + dy * diam))
    m, t = Image.new("L", (n, n), 0), Image.new("L", (n, n), 0)
    m.paste(forme, pos)
    t.paste(traits, pos)

    def cercle(rayon):
        c = Image.new("L", (n, n), 0)
        ImageDraw.Draw(c).ellipse([n / 2 - rayon * K, n / 2 - rayon * K, n / 2 + rayon * K, n / 2 + rayon * K], fill=255)
        return c

    bord, fond = cercle(R_BORD), cercle(R_FOND)
    haut = cercle(R_FOND)
    ImageDraw.Draw(haut).ellipse([n / 2 - (R_FOND + 3) * K, n / 2 - (R_FOND - 9) * K, n / 2 + (R_FOND + 3) * K,
                                  n / 2 + (R_FOND + 24) * K], fill=0)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    img.paste(CERCLE, (0, 0), bord)
    img.paste(DISQUE, (0, 0), fond)
    img.paste(DISQUE_HAUT, (0, 0), haut)
    # la main, coupée par le fond du disque ; son cerne sombre autour, ses traits dedans
    m = ImageChops.multiply(m, fond)
    cerne = m.filter(ImageFilter.MaxFilter(2 * round(CERNE * K) + 1))
    img.paste(TRAIT, (0, 0), ImageChops.multiply(cerne, fond))
    img.paste(MAIN, (0, 0), m)
    img.paste(TRAIT, (0, 0), ImageChops.multiply(t, m))
    return img.resize((T, T), Image.LANCZOS)


def planche():
    out = Image.new("RGBA", (T * len(ORDRE), T), (0, 0, 0, 0))
    for i, nom in enumerate(ORDRE):
        out.paste(medaillon(nom), (i * T, 0))
    return out


CREDITS = """Médaillons des mudras (medaillons.png)

Mains tirées des dessins « Mudra-Naruto-*.svg » de Schlum, Wikimedia Commons
(https://commons.wikimedia.org/wiki/File:Mudra-Naruto-Tigre.svg), sous licence Creative Commons
Attribution - Partage dans les mêmes conditions 4.0 International (CC BY-SA 4.0,
https://creativecommons.org/licenses/by-sa/4.0/deed.fr).

Modifications : mains détourées, traits épaissis, ombres ramenées au blanc, posées sur un disque (Reborn Roleplay, 2026).
Cette texture est diffusée sous la même licence CC BY-SA 4.0.
"""

if __name__ == "__main__":
    p = planche()
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        fond = Image.new("RGBA", (p.width // 2 + 16, p.height * 2 + 24), (52, 74, 50, 255))
        fond.alpha_composite(p.crop((0, 0, p.width // 2, T)), (8, 8))
        fond.alpha_composite(p.crop((p.width // 2, 0, p.width, T)), (8, 16 + T))
        fond.resize((fond.width * 2, fond.height * 2), Image.LANCZOS).save(sys.argv[2])
        print("aperçu", sys.argv[2])
    else:
        os.makedirs(SORTIE, exist_ok=True)
        p.save(os.path.join(SORTIE, "medaillons.png"), optimize=True)
        # filtrage linéaire : la texture n'est pas du pixel art, elle est réduite ou agrandie selon la fenêtre
        with open(os.path.join(SORTIE, "medaillons.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
            f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
        with open(os.path.join(SORTIE, "CREDITS.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write(CREDITS)
        print("écrit", os.path.join(SORTIE, "medaillons.png"), p.size)
