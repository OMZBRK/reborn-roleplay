# -*- coding: utf-8 -*-
"""
texte_banc_compare.py : contrôle du brief §6 (lot 2), couches Java du banc REBORN_TEXTE_BANC contre la recette Python.

    python texte_banc_compare.py [dossier du banc] [--ref <_texte_raster.py>] [--sortie <dossier>]

1. Lit <banc>/manifest.json (écrit par audit/TexteBanc : entrées exactes de chaque cas, position et mise en page de
   la couche Java) et les PNG RGBA du banc.
2. Rend les mêmes entrées avec le moteur de référence des maquettes, _texte_raster.py, importé par son chemin
   (`rendre` pour un bloc, `rendre_ligne` pour une ligne de HUD, `poser_forme` pour une forme).
3. Aligne les deux couches sur leur position écran (union des deux boîtes, le reste transparent) et imprime, par cas,
   l'écart d'alpha moyen et maximal en 1/255. Cible : moyen < 1/255 ET max < 16/255.
4. Compare la mise en page (texte de chaque ligne, lignes de base, largeurs, em, capitales).
5. Contrôle les captures du banc (couches passées par TexteCache : texture, blit 1:1, lecture au plus proche) contre
   les couches PNG posées sur le même fond uni (run/screenshots/texte_banc_sombre.png et _clair.png).

Écrit <sortie>/ecarts_<cas>.png (couche Java | couche Python | écart d'alpha ×8, zoom ×4) et <sortie>/compare.txt.
Sortie 1 si un cas dépasse la cible. Il faut Python 3.14, Pillow, numpy et fontTools (ou REBORN_PYLIB).
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
BANC_DEFAUT = os.path.normpath(os.path.join(ICI, "..", "run", "texte_banc"))
REF_DEFAUT = os.environ.get("REBORN_TEXTE_RASTER") or \
    r"C:\Users\omarb\Desktop\Reborn - Gestion\Maquettes\gameplan\17_lot2_ketto\_texte_raster.py"
CIBLE_MOY, CIBLE_MAX = 1.0, 16.0           # en 1/255
FONDS = {"texte_banc_sombre": (0x14, 0x10, 0x12), "texte_banc_clair": (0xE6, 0xE9, 0xEE)}


def charger_ref(chemin):
    if not os.path.isfile(chemin):
        sys.exit("moteur de référence introuvable : %s (option --ref ou REBORN_TEXTE_RASTER)" % chemin)
    dossier = os.path.dirname(os.path.abspath(chemin))
    sys.dont_write_bytecode = True   # pas de __pycache__ dans le dossier des maquettes
    if dossier not in sys.path:
        sys.path.insert(0, dossier)
    spec = importlib.util.spec_from_file_location("_texte_raster", chemin)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["_texte_raster"] = mod
    spec.loader.exec_module(mod)
    return mod


def rendre_python(tr, c):
    """-> (couche RGBA, x, y, métriques ou None)."""
    if c["genre"] == "forme":
        masque = np.array(c["masque"], np.float32).reshape(c["mh"], c["mw"])
        saisi = {}
        vrai = tr.poser

        def capter(capture, couche, x, y):
            saisi["c"] = (couche, int(x), int(y))
            return capture

        tr.poser = capter            # poser_forme finit par poser(capture, couche, x − m, y − m)
        try:
            tr.poser_forme(Image.new("RGBA", (1, 1)), masque, c["mx"], c["my"], tuple(c["rgb"]), alpha=c["alpha"],
                           contour=c["contour"], ombre=c["ombre"], d=c["d"], r=c["r"])
        finally:
            tr.poser = vrai
        couche, x, y = saisi["c"]
        return couche, x, y, None
    eq = {"auto": "auto", "toujours": True, "jamais": False}[c["equilibrer"]]
    if c["genre"] == "bloc":
        r = tr.rendre(c["texte"], c["police"], c["preset"], c["cap"], c["largeur_max"], c["pas"], c["f"],
                      (c["cx"], c["cy"]), c["ancre"], c["interlettre"], "auto", 0.0, eq, c["typo"], None,
                      c["couleur_defaut"], False, None)
    else:
        r = tr.rendre_ligne(c["texte"], c["police"], c["preset"], c["cap"], x=c["cx"], y_centre=c["cy"],
                            ancre=c["ancre"], f=c["f"], interlettre=c["interlettre"], calage="auto",
                            cisaillement=0.0, typo=c["typo"], couleurs=None, couleur_defaut=c["couleur_defaut"],
                            centrage=c["centrage"])
    return r.couche, r.x, r.y, r.metriques


def aligner(a, ax, ay, b, bx, by):
    a, b = np.asarray(a.convert("RGBA")), np.asarray(b.convert("RGBA"))
    x0, y0 = min(ax, bx), min(ay, by)
    x1, y1 = max(ax + a.shape[1], bx + b.shape[1]), max(ay + a.shape[0], by + b.shape[0])
    A = np.zeros((y1 - y0, x1 - x0, 4), np.int32)
    B = np.zeros_like(A)
    A[ay - y0:ay - y0 + a.shape[0], ax - x0:ax - x0 + a.shape[1]] = a
    B[by - y0:by - y0 + b.shape[0], bx - x0:bx - x0 + b.shape[1]] = b
    return A, B, x0, y0


def planche_ecarts(A, B, chemin, z=4):
    """Couche Java | couche Python | écart d'alpha ×8 (rouge), sur un gris moyen, zoom ×z au plus proche."""
    h, w = A.shape[:2]
    gris = np.full((h, w, 4), (96, 92, 94, 255), np.uint8)
    out = Image.new("RGBA", (3 * w * z + 2 * 8, h * z), (13, 10, 11, 255))
    for i, X in enumerate((A, B)):
        fond = Image.fromarray(gris, "RGBA")
        fond.alpha_composite(Image.fromarray(X.astype(np.uint8), "RGBA"))
        out.paste(fond.resize((w * z, h * z), Image.NEAREST), (i * (w * z + 8), 0))
    d = np.clip(np.abs(A[..., 3] - B[..., 3]) * 8, 0, 255).astype(np.uint8)
    e = np.zeros((h, w, 4), np.uint8)
    e[..., 0], e[..., 3] = 255, d
    fond = Image.fromarray(np.full((h, w, 4), (0, 0, 0, 255), np.uint8), "RGBA")
    fond.alpha_composite(Image.fromarray(e, "RGBA"))
    out.paste(fond.resize((w * z, h * z), Image.NEAREST), (2 * (w * z + 8), 0))
    out.convert("RGB").save(chemin)


def comparer_mise(c, met):
    """Écarts de mise en page (liste de chaînes, vide si identique)."""
    pb = []
    if met is None:
        return pb
    if c["nb_lignes"] != met["nb_lignes"]:
        pb.append("lignes %d (Java) contre %d (Python)" % (c["nb_lignes"], met["nb_lignes"]))
    if [l for l in c["lignes"]] != met["lignes"]:
        for i, (lj, lp) in enumerate(zip(c["lignes"], met["lignes"])):
            if lj != lp:
                pb.append("ligne %d : %r / %r" % (i + 1, lj, lp))
                break
    if list(c["bases"]) != list(met["bases"]):
        pb.append("lignes de base %s / %s" % (c["bases"], met["bases"]))
    if c["largeurs"] and met["largeurs"] and len(c["largeurs"]) == len(met["largeurs"]):
        dl = max(abs(a - b) for a, b in zip(c["largeurs"], met["largeurs"]))
        if dl > 1e-6:
            pb.append("largeurs : écart max %.2e px" % dl)
    for cle in ("em", "capitales", "hauteur_x", "espace_mot"):
        if abs(c[cle] - met[cle]) > 1e-9:
            pb.append("%s %.6f / %.6f" % (cle, c[cle], met[cle]))
    if c.get("insecables_auto") != met.get("insecables_auto"):
        pb.append("insécables auto %s / %s" % (c.get("insecables_auto"), met.get("insecables_auto")))
    return pb


def controle_ecran(banc, cas, couches, sortie, lignes):
    """Captures du banc contre les couches PNG posées sur le même fond, dans l'ordre du dessin."""
    shots = os.path.normpath(os.path.join(banc, "..", "screenshots"))
    ok = True
    for nom, fond in FONDS.items():
        p = os.path.join(shots, nom + ".png")
        if not os.path.isfile(p):
            lignes.append("écran %s : capture absente" % nom)
            ok = False
            continue
        cap = Image.open(p).convert("RGBA")
        att = Image.new("RGBA", cap.size, fond + (255,))
        boites = []
        for c in cas:
            if not c.get("ecran"):
                continue
            couche = couches[c["nom"]]
            x, y = c["x"], c["y"]
            if x < 0 or y < 0 or x + couche.width > cap.width or y + couche.height > cap.height:
                continue
            att.alpha_composite(couche, (x, y))
            boites.append((x, y, x + couche.width, y + couche.height))
        a = np.asarray(cap, np.int32)[..., :3]
        b = np.asarray(att, np.int32)[..., :3]
        d = np.abs(a - b).max(axis=2)
        dans = np.zeros(d.shape, bool)
        for x0, y0, x1, y1 in boites:
            dans[y0:y1, x0:x1] = True
        dmax_in = int(d[dans].max()) if dans.any() else 0
        dmoy_in = float(d[dans].mean()) if dans.any() else 0.0
        dmax_out = int(d[~dans].max()) if (~dans).any() else 0
        bon = dmax_in <= 2 and dmax_out == 0
        ok &= bon
        lignes.append("écran %-18s %d couches · dans les couches : écart RVB moyen %.3f, max %d · hors couches : max %d"
                      " · %s" % (nom, len(boites), dmoy_in, dmax_in, dmax_out, "OK" if bon else "ÉCART"))
        if not bon:
            e = np.clip(d * 32, 0, 255).astype(np.uint8)
            Image.fromarray(e, "L").save(os.path.join(sortie, "ecran_ecart_%s.png" % nom))
    return ok


def main():
    ap = argparse.ArgumentParser(description="Couches Java du banc REBORN_TEXTE_BANC contre _texte_raster.py")
    ap.add_argument("banc", nargs="?", default=BANC_DEFAUT, help="dossier run/texte_banc")
    ap.add_argument("--ref", default=REF_DEFAUT, help="chemin de _texte_raster.py")
    ap.add_argument("--sortie", default=None, help="dossier des planches d'écarts (défaut : <banc>/ecarts)")
    a = ap.parse_args()
    tr = charger_ref(a.ref)
    with open(os.path.join(a.banc, "manifest.json"), encoding="utf-8") as f:
        man = json.load(f)
    sortie = a.sortie or os.path.join(a.banc, "ecarts")
    os.makedirs(sortie, exist_ok=True)
    lignes = ["Banc %s, FreeType %s (Java) ; référence %s" % (man.get("moteur"), man.get("freetype"), a.ref), ""]
    lignes.append("%-24s %8s %6s %6s %9s %8s  %s" % ("cas", "taille", "moy", "max", "px≥8/255", "RVB×α", "verdict"))
    echecs, couches = 0, {}
    for c in man["cas"]:
        java = Image.open(os.path.join(a.banc, c["fichier"])).convert("RGBA")
        couches[c["nom"]] = java
        py, px, pyy, met = rendre_python(tr, c)
        A, B, _, _ = aligner(java, c["x"], c["y"], py, px, pyy)
        da = np.abs(A[..., 3] - B[..., 3])
        moy, mx = float(da.mean()), int(da.max())
        n8 = int((da >= 8).sum())
        pa = A[..., :3] * A[..., 3:4] / 255.0
        pb = B[..., :3] * B[..., 3:4] / 255.0
        drgb = float(np.abs(pa - pb).max())
        mise = comparer_mise(c, met)
        bon = moy < CIBLE_MOY and mx < CIBLE_MAX and not mise
        echecs += not bon
        lignes.append("%-24s %8s %6.3f %6d %9d %8.1f  %s" % (
            c["nom"], "%dx%d" % (A.shape[1], A.shape[0]), moy, mx, n8, drgb, "OK" if bon else "ÉCHEC"))
        for m in mise:
            lignes.append("    mise en page : " + m)
        if (java.width, java.height, c["x"], c["y"]) != (py.width, py.height, px, pyy):
            lignes.append("    boîte Java %s, Python %s" % ((c["x"], c["y"], java.width, java.height),
                                                        (px, pyy, py.width, py.height)))
        planche_ecarts(A, B, os.path.join(sortie, "ecarts_%s.png" % c["nom"]))
    lignes.append("")
    ecran_ok = controle_ecran(a.banc, man["cas"], couches, sortie, lignes)
    lignes.append("")
    lignes.append("%d cas, %d hors cible (moyen < %g/255, max < %g/255, mise en page identique) · écran %s" % (
        len(man["cas"]), echecs, CIBLE_MOY, CIBLE_MAX, "OK" if ecran_ok else "ÉCART"))
    texte = "\n".join(lignes)
    print(texte)
    with open(os.path.join(sortie, "compare.txt"), "w", encoding="utf-8") as f:
        f.write(texte + "\n")
    sys.exit(1 if echecs or not ecran_ok else 0)


if __name__ == "__main__":
    main()
