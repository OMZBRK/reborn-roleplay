# -*- coding: utf-8 -*-
"""
ost_compare.py : lecteur OST du lot 2 (étape 2a), captures du jeu contre la maquette, au pixel près.

    python ost_compare.py [dossier des captures] [--maquettes <dossier 17_lot2_ketto>] [--sortie <dossier>]

Le banc REBORN_FEATURE_PREVIEW=1 (audit/FeaturePreview) prend chaque état fixe deux fois de suite, monde gelé :
<cas>_sans.png (élément « Musique » masqué) puis <cas>.png (le bloc). Ce script repose la maquette — la fonction
ost() de _maquettes_lot2.py, importée par son chemin, avec le moteur de référence _texte_raster.py — sur la capture
nue, aux réglages du cas (100 %, 70 % barres figées, pause, titre long, sous l'indicateur du staff), puis compare au
bloc dessiné par le jeu sur la zone du lecteur (haut droit, 300 × 100 px) :

- dans l'empreinte du bloc (pixels que la maquette modifie, élargis d'1 px) : écart RVB moyen et maximal en 1/255 ;
  cible : moyen < 1/255 au-delà de la dérive du fond (ci-dessous) ET max < 16/255 ;
- hors de l'empreinte : le jeu ne doit rien dessiner de plus (aucun pixel à plus de 16/255 de la capture nue) ; la
  dérive moyenne du fond entre les deux captures est imprimée (ciel qui bouge d'une image à l'autre) ;
- planche <sortie>/ost_compare_<cas>.png : jeu | maquette sur le même fond | écart ×8, zoom ×3 au plus proche.

Écrit aussi <sortie>/ost_compare.txt. Sortie 1 si un cas dépasse la cible. Python 3.14, Pillow, numpy, fontTools.
"""
from __future__ import annotations

import argparse
import importlib.util
import os
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
CAPTURES = os.path.normpath(os.path.join(ICI, "..", "run", "screenshots"))
MAQUETTES = os.environ.get("REBORN_MAQUETTES_LOT2") or \
    r"C:\Users\omarb\Desktop\Reborn - Gestion\Maquettes\gameplan\17_lot2_ketto"
CIBLE_MOY, CIBLE_MAX = 1.0, 16.0
TITRE_LONG = "Les ombres s'allongent sur la Feuille"


def charger_maquettes(dossier):
    chemin = os.path.join(dossier, "_maquettes_lot2.py")
    if not os.path.isfile(chemin):
        sys.exit("maquettes introuvables : %s (option --maquettes)" % chemin)
    sys.dont_write_bytecode = True   # pas de __pycache__ dans le dossier des maquettes
    if dossier not in sys.path:
        sys.path.insert(0, dossier)
    spec = importlib.util.spec_from_file_location("_maquettes_lot2", chemin)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def reglages(cas, mq):
    """Paramètres d'ost() pour un cas du banc (d'après son nom)."""
    if cas.endswith("_70"):
        return dict(titre="Mystère", a=0.70, etat="fige", barres=(5, 8, 4))
    if cas.endswith("_pause"):
        return dict(titre="Mystère", etat="pause")
    if cas.endswith("_titre_long"):
        return dict(titre=TITRE_LONG)
    if cas.endswith("_sous_indicateur"):
        return dict(titre="Mystère", haut=mq.G.ost_haut + 28)
    return dict(titre="Mystère")


def elargir(m):
    e = m.copy()
    e[1:, :] |= m[:-1, :]
    e[:-1, :] |= m[1:, :]
    e[:, 1:] |= m[:, :-1]
    e[:, :-1] |= m[:, 1:]
    return e


def planche(jeu, maq, chemin, z=3):
    h, w = jeu.shape[:2]
    out = Image.new("RGB", (3 * w * z + 16, h * z), (13, 10, 11))
    for i, X in enumerate((jeu, maq)):
        out.paste(Image.fromarray(X.astype(np.uint8), "RGB").resize((w * z, h * z), Image.NEAREST), (i * (w * z + 8), 0))
    d = np.clip(np.abs(jeu - maq).max(axis=2) * 8, 0, 255).astype(np.uint8)
    e = np.zeros((h, w, 3), np.uint8)
    e[..., 0] = d
    out.paste(Image.fromarray(e, "RGB").resize((w * z, h * z), Image.NEAREST), (2 * (w * z + 8), 0))
    out.save(chemin)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("captures", nargs="?", default=CAPTURES)
    ap.add_argument("--maquettes", default=MAQUETTES)
    ap.add_argument("--sortie", default=None)
    args = ap.parse_args()
    sortie = args.sortie or args.captures
    os.makedirs(sortie, exist_ok=True)
    mq = charger_maquettes(args.maquettes)
    cas = sorted(f[:-9] for f in os.listdir(args.captures) if f.startswith("ost_") and f.endswith("_sans.png"))
    if not cas:
        sys.exit("aucune paire ost_*_sans.png / ost_*.png dans %s" % args.captures)
    lignes, echec = [], False
    for c in cas:
        nu = Image.open(os.path.join(args.captures, c + "_sans.png")).convert("RGBA")
        jeu = Image.open(os.path.join(args.captures, c + ".png")).convert("RGBA")
        W, H = nu.size
        g = mq.Geo(W, H, 1.0) if (W, H) != (1920, 1080) else mq.G
        r = reglages(c, mq)
        titre = r.pop("titre")
        sc = nu.copy()
        info = mq.ost(sc, titre, "B", g=g, **r)
        boite = (W - 300, 0, W, 100)
        A = np.asarray(jeu.crop(boite).convert("RGB"), np.int32)
        B = np.asarray(sc.crop(boite).convert("RGB"), np.int32)
        N = np.asarray(nu.crop(boite).convert("RGB"), np.int32)
        emp = elargir(np.abs(B - N).max(axis=2) > 0)
        d = np.abs(A - B).max(axis=2)
        moy, mx = float(d[emp].mean()), int(d[emp].max())
        hors = np.abs(A - N).max(axis=2)[~emp]
        derive, hors_max = float(hors.mean()), int(hors.max())
        # le ciel peut bouger d'une ou deux unités entre les deux images (fin d'un fondu de météo) : sous les pixels
        # semi-transparents du bloc, cette dérive se retrouve dans l'écart, d'où la tolérance du moyen
        ok = moy < CIBLE_MOY + derive and mx < CIBLE_MAX and hors_max < CIBLE_MAX
        echec |= not ok
        lignes.append("%-30s bloc : moyen %5.2f/255 max %3d/255 (%4d px) · hors bloc : max %3d/255, dérive du fond %4.2f "
                      "· %s · « %s », filet %d px, haut %d"
                      % (c, moy, mx, int(emp.sum()), hors_max, derive, "OK" if ok else "HORS CIBLE", info["titre"],
                         info["filet"], info["haut"]))
        planche(A, B, os.path.join(sortie, "ost_compare_%s.png" % c))
    rapport = "\n".join(lignes)
    print(rapport)
    with open(os.path.join(sortie, "ost_compare.txt"), "w", encoding="utf-8") as f:
        f.write(rapport + "\n")
    sys.exit(1 if echec else 0)


if __name__ == "__main__":
    main()
