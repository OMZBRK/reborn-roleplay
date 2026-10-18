# -*- coding: utf-8 -*-
"""Calque des sourcils du créateur de personnage (couche 64x64, niveaux de gris prêts à teinter).

Génère src/main/resources/assets/reborn/textures/character/brows/Sourcils_Base.png.

Jusqu'au 18/10 les sourcils étaient cuits dans le gabarit de peau (skin/<genre>/4.png : six pixels sombres
#301B17 sur la ligne 12, x 8-10 et 13-15) et repeints d'un aplat par RebornSkins. Ils ont maintenant leur
calque : RebornSkins repeint ces six pixels en peau, puis pose ce calque teinté (tint=all, couleur x luma /
luma max) juste après les yeux et avant pilosité et cheveux (une frange peut donc les couvrir).

Mêmes emplacements que les sourcils cuits (les yeux de Yeux_Style1 sont juste dessous, lignes 13-14) : le
visage ne bouge pas, seule la couleur devient libre. Niveaux de gris : blanc pur côté nez (luma max = 255,
la couleur choisie y est rendue exacte), un cran plus sombre au milieu, plus sombre côté tempe, où le
sourcil s'affine. Aplat propre, pas de motif.

Usage : python tools/gen_sourcils.py   (Pillow requis)
"""
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "reborn",
                   "textures", "character", "brows", "Sourcils_Base.png")

NEZ, MILIEU, TEMPE = 255, 236, 196   # niveaux de gris, du nez vers la tempe
Y = 12                               # ligne des sourcils (face : x 8..15, y 8..15)

# face vue de l'extérieur : x 8 = côté droit du perso (gauche de l'écran)
SOURCILS = {
    8: TEMPE, 9: MILIEU, 10: NEZ,    # sourcil droit du perso
    13: NEZ, 14: MILIEU, 15: TEMPE,  # sourcil gauche du perso
}


def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x, v in SOURCILS.items():
        img.putpixel((x, Y), (v, v, v, 255))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("écrit", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
