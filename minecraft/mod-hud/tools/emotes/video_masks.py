"""Masques par partie du corps tirés d'images de la vidéo de référence (Python système + Pillow + numpy).

    python tools/emotes/video_masks.py <vues.json> <dossier_refs> <dossier_sortie> [--echelle 0.5]

Pour chaque vue qui a une ``ref`` (image extraite de la vidéo, ex. ``ffmpeg -ss 2.0 -i video.mp4 -frames:v 1 f_2.0.png``)
et une ``roi`` [x0, y0, x1, y1] (rectangle autour du personnage, en pixels de l'image), écrit ``<vue>.npy`` : une carte
d'étiquettes 0 fond, 1 cheveux, 2 vêtement clair, 3 peau, 4 pantalon/chaussures (sombre), 5 autre partie du personnage,
à l'échelle demandée, et ``<vue>_masque.png`` pour vérifier à l'œil. Les seuils de couleur sont ceux du personnage de
la vidéo de méditation (cheveux violets, peau brune, chemise claire, pantalon violet sombre) : pour une autre vidéo,
ajuster ``SEUILS`` ou passer ``--seuils fichier.json``.
"""

import argparse
import json
import os

import numpy as np
from PIL import Image, ImageFilter

COULEURS = np.array([[0, 0, 0], [170, 70, 210], [225, 245, 245], [160, 95, 50], [70, 25, 80], [230, 190, 40]],
                    np.uint8)


def etiquettes(img, roi):
    a = np.asarray(img.convert("RGB")).astype(int)
    R, G, B = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(-1), a.min(-1)
    sat = mx - mn
    lab = np.zeros(R.shape, np.uint8)
    vert = (G > R + 25) & (G > B + 10)                       # herbe, halo vert, feuillage
    cheveux = (R > G + 25) & (B > G + 25) & (mx > 90)
    sombre = (mx < 80) & ~vert & ~cheveux
    clair = (mn > 115) & (sat < 80) & (B - R >= 8) & (G - R >= 4) & ~cheveux & ~((G > R + 40) & (G > B + 20))
    peau = (R > G + 12) & (G > B) & (R > 70) & (R < 205) & (sat < 130) & (R - B > 30) & ~cheveux
    autre = (R > 180) & (G > 140) & (B < 90)                 # liserés or
    lab[autre] = 5
    lab[sombre] = 4
    lab[peau] = 3
    lab[clair] = 2
    lab[cheveux] = 1
    m = np.zeros_like(lab)
    x0, y0, x1, y1 = roi
    m[y0:y1, x0:x1] = lab[y0:y1, x0:x1]
    # ne garder que la plus grosse tache connexe (le personnage), trous comblés par un filtre médian
    sil = Image.fromarray(((m > 0) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(5))
    sil = np.asarray(sil) > 0
    sil = _plus_grosse_tache(sil)
    m[~sil] = 0
    m[sil & (m == 0)] = 5
    return m


def _plus_grosse_tache(b):
    h, w = b.shape
    vu = np.zeros_like(b)
    best, best_n = None, 0
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            if b[y, x] and not vu[y, x]:
                pile, pts = [(y, x)], []
                vu[y, x] = True
                while pile:
                    cy, cx = pile.pop()
                    pts.append((cy, cx))
                    for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                        if 0 <= ny < h and 0 <= nx < w and b[ny, nx] and not vu[ny, nx]:
                            vu[ny, nx] = True
                            pile.append((ny, nx))
                if len(pts) > best_n:
                    best, best_n = pts, len(pts)
    out = np.zeros_like(b)
    if best:
        ys, xs = zip(*best)
        out[list(ys), list(xs)] = True
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("vues")
    ap.add_argument("refs")
    ap.add_argument("sortie")
    ap.add_argument("--echelle", type=float, default=0.5)
    a = ap.parse_args()
    with open(a.vues, encoding="utf-8") as f:
        vues = json.load(f)
    os.makedirs(a.sortie, exist_ok=True)
    for nom, v in vues.items():
        if "ref" not in v or "roi" not in v:
            continue
        img = Image.open(os.path.join(a.refs, v["ref"]))
        lab = etiquettes(img, v["roi"])
        w, h = int(img.width * a.echelle), int(img.height * a.echelle)
        lab_s = np.asarray(Image.fromarray(lab).resize((w, h), Image.NEAREST))
        np.save(os.path.join(a.sortie, nom + ".npy"), lab_s)
        apercu = np.concatenate([np.asarray(img.convert("RGB")), COULEURS[lab]], 1)
        Image.fromarray(apercu).save(os.path.join(a.sortie, nom + "_masque.png"))
        print(nom, "->", {k: int((lab_s == k).sum()) for k in range(1, 6)})


if __name__ == "__main__":
    main()
