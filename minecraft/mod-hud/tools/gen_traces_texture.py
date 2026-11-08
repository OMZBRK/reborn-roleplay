# -*- coding: utf-8 -*-
"""
Texture des traces de pas (lot 4, étape C, planche c7) :
    python minecraft/mod-hud/tools/gen_traces_texture.py
    -> src/main/resources/assets/reborn/textures/concentration/traces.png
    -> src/main/resources/assets/reborn/textures/concentration/traces_contour.png

Semelle V1 (pied gauche, orteils en haut, 6 x 10 texels, blanc pur : la couleur vient du réglage du joueur, par la teinte
du quad), en cases de 8 x 12 (une marge d'un texel, NEAREST ne bave pas sur la voisine). Le pied droit est le même, en
miroir par les UV. Sprint : les 6 premières rangées d'une case. Accroupi et chargé : le quad (opacité, largeur).

    colonnes : effacement par grains, de la semelle pleine (0) à la plus effritée (5) ;
    rangées 0 à 2 : trois ordres d'effritement (le pas choisit selon son id, les pistes ne se répètent pas) ;
    rangée 3 : le sang (option serveur, coupée au départ), deux texels éteints au talon, ordre 0.

Les grains partent d'abord du bord, dans un ordre fixe : la silhouette reste lisible jusqu'au bout.

Le contour (même découpe, un texel autour de la semelle, jamais sur elle) : autour d'une semelle sombre, sur un sol clair, la nuit
seulement : un sable n'y est plus qu'à 10 % de luminance et c'est lui qui fait lire le pas ; de jour il se verrait et élargirait le
pied (Traces.contourNuit, 08/11). Rangées 0 à 3 : les pas entiers ; rangées 4 à 7 : les mêmes coupés au sprint, fermés au talon.
"""
import os
import random

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
SORTIE = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "concentration", "traces.png")
SORTIE_CONTOUR = os.path.join(os.path.dirname(SORTIE), "traces_contour.png")

SEMELLE = ["..XXX.",
           ".XXXXX",
           "XXXXXX",
           "XXXXXX",
           "XXXXX.",
           ".XXX..",
           ".XXX..",
           ".XXXX.",
           ".XXXX.",
           "..XX.."]
CASE_W, CASE_H = 8, 12
COLONNES = 6
RANGEES = 4
RANGS_SPRINT = 6
# part des grains partis à chaque colonne
PARTIS = [0.0, 0.10, 0.20, 0.33, 0.47, 0.62]
BLANC = (255, 255, 255, 255)
SANG = (150, 28, 34, 255)
TALON = {(8, 2), (7, 3)}


def bord(j, i):
    """Nombre de voisins vides : un texel du bord part avant ceux du cœur."""
    n = 0
    for dj, di in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        jj, ii = j + dj, i + di
        if jj < 0 or jj >= len(SEMELLE) or ii < 0 or ii >= len(SEMELLE[0]) or SEMELLE[jj][ii] != "X":
            n += 1
    return n


def ordre(graine):
    r = random.Random(graine)
    texels = [(j, i) for j, ln in enumerate(SEMELLE) for i, c in enumerate(ln) if c == "X"]
    texels.sort(key=lambda t: r.random() - 0.22 * bord(*t))
    return texels


def main():
    img = Image.new("RGBA", (CASE_W * COLONNES, CASE_H * RANGEES), (255, 255, 255, 0))
    ordres = [ordre(11), ordre(29), ordre(53), ordre(11)]
    for rang in range(RANGEES):
        for col in range(COLONNES):
            n_partis = round(PARTIS[col] * len(ordres[rang]))
            partis = set(ordres[rang][:n_partis])
            for j, ln in enumerate(SEMELLE):
                for i, c in enumerate(ln):
                    if c != "X" or (j, i) in partis:
                        continue
                    px = SANG if rang == 3 and (j, i) in TALON else BLANC
                    img.putpixel((col * CASE_W + 1 + i, rang * CASE_H + 1 + j), px)
    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    img.save(SORTIE)
    print("écrit", os.path.normpath(SORTIE), img.size)
    contour = Image.new("RGBA", (img.width, img.height * 2), (255, 255, 255, 0))
    # le sprint : les RANGS_SPRINT premières rangées de la semelle (après la marge d'un texel)
    sprint = img.copy()
    for y in range(sprint.height):
        if y % CASE_H > RANGS_SPRINT:
            for x in range(sprint.width):
                sprint.putpixel((x, y), (255, 255, 255, 0))
    for source, decale in ((img, 0), (sprint, img.height)):
        for y in range(source.height):
            for x in range(source.width):
                if source.getpixel((x, y))[3] != 0:
                    continue
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    vx, vy = x + dx, y + dy
                    # dans la même case seulement
                    if vx // CASE_W == x // CASE_W and vy // CASE_H == y // CASE_H and 0 <= vx < source.width and 0 <= vy < source.height \
                            and source.getpixel((vx, vy))[3] != 0:
                        contour.putpixel((x, y + decale), BLANC)
                        break
    contour.save(SORTIE_CONTOUR)
    print("écrit", os.path.normpath(SORTIE_CONTOUR), contour.size)


if __name__ == "__main__":
    main()
