# -*- coding: utf-8 -*-
"""
ecrans_art.py — pièces communes des générateurs de textures des écrans « au niveau de la fiche »
(scène de mort, menu d'interaction, concentration, bibliothèque / lecture / réglages).

Rien n'est inventé ici : on réutilise la boîte à outils tools/style/kit.py (lecture seule), les
primitives de la fiche (tools/fiche_art.py) et ses pièces du tour 4 (tools/gen_fiche_textures.py,
importé sans exécuter son main). Ce module ajoute seulement :
- Sortie : écriture déterministe des PNG d'un dossier (palette si ≤ 256 couleurs, budget, ménage
  des sorties obsolètes du même générateur) ;
- quelques pièces paramétrées par la largeur (boutons, bandeaux, jauges) dont les bandes de coupe
  restent sans ornement, pour la coupe côté code (DaKit.coupe / coupeH).
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ICI)
import fiche_art as fa  # noqa: E402
import gen_fiche_textures as gf  # noqa: E402  (main non exécuté)
from fiche_art import kit, hexc, rect, arcade, coller, R_OR, R_LAQUE, R_LAQUE_ROUGE, PAL  # noqa: E402,F401

TEX = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui")

OR = fa.OR
OR_CLAIR = fa.OR_CLAIR
CREME = fa.CREME
CREME_KJ = gf.CREME_KJ
BISEAU_CLAIR = fa.BISEAU_CLAIR
BISEAU_SOMBRE = fa.BISEAU_SOMBRE
R_VERM = gf.R_VERM
RANG_COL = gf.RANG_COL
RANGS = ["D", "C", "B", "A", "S"]


def nouveau(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


class Sortie:
    """Écrit les PNG d'un dossier textures/gui/<dossier>/ et fait le bilan."""

    def __init__(self, dossier, budget=1_000_000):
        self.dir = os.path.normpath(os.path.join(TEX, dossier))
        self.budget = budget
        self.ecrits = []
        os.makedirs(self.dir, exist_ok=True)

    def sauver(self, nom, img, taille=None, quantifier=False):
        if isinstance(img, np.ndarray):
            img = Image.fromarray(img, "RGBA")
        img = img.convert("RGBA")
        if taille is not None:
            assert img.size == tuple(taille), f"{nom} : taille {img.size} au lieu de {taille}"
        a = np.asarray(img).copy()
        a[a[..., 3] == 0] = 0
        chemin = os.path.join(self.dir, nom)
        plat = a.reshape(-1, 4)
        couleurs, inv = np.unique(plat, axis=0, return_inverse=True)
        if len(couleurs) <= 256:
            p = Image.fromarray(inv.reshape(a.shape[:2]).astype(np.uint8), "P")
            p.putpalette(couleurs[:, :3].astype(np.uint8).flatten().tolist(), rawmode="RGB")
            if (couleurs[:, 3] < 255).any():
                p.save(chemin, optimize=True, transparency=bytes(couleurs[:, 3].astype(np.uint8).tolist()))
            else:
                p.save(chemin, optimize=True)
            mode = "P"
        elif quantifier and (a[..., 3] == 255).all():
            q = Image.fromarray(a[..., :3], "RGB").quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
            q.save(chemin, optimize=True)
            mode = "P~"
        else:
            Image.fromarray(a, "RGBA").save(chemin, optimize=True)
            mode = "RGBA"
        self.ecrits.append((nom, img.size, os.path.getsize(chemin), mode))

    def bilan(self):
        total = sum(e[2] for e in self.ecrits)
        for nom, (w, h), o, mode in self.ecrits:
            print(f"{nom:<30} {f'{w}x{h}':>10} {o:>9}  {mode}")
        print(f"{len(self.ecrits)} fichiers, {total} octets ({total / 1024:.0f} Ko) dans {self.dir}")
        attendus = {e[0] for e in self.ecrits}
        for f in sorted(set(x for x in os.listdir(self.dir) if x.endswith(".png")) - attendus):
            os.remove(os.path.join(self.dir, f))
            print("supprimé (obsolète) :", f)
        assert total < self.budget, f"budget dépassé : {total} octets (max {self.budget})"


# ───────────────────────── pièces ─────────────────────────
def bouton(px, x, y, w, h, etat="normal", ton="noir", kj=None):
    """Bouton-plaque de la fiche (bouton4) : ombre, laque, liseré or, ferrures 3x3, kanji cuit
    optionnel à gauche. etat : normal | survol | actif | inactif | neutre."""
    if etat == "actif":
        gf.bouton4(px, x, y, "survol", kj=kj, ton="rouge", h=h, w=w)
    else:
        gf.bouton4(px, x, y, etat, kj=kj, ton=ton, h=h, w=w)


def filet_losanges(px, cx, y, demi, ecart=10, couleur_fil=None):
    """Filet or fin horizontal de part et d'autre d'un centre, losange à chaque bout et au
    ras du centre (comme l'en-tête de la fiche)."""
    cf = couleur_fil or hexc(R_OR[2])
    for sgn in (-1, 1):
        a, b = cx + sgn * ecart, cx + sgn * demi
        x0, x1 = min(a, b), max(a, b)
        rect(px, x0, y, x1 - x0 + 1, 1, cf)
        for X in (a, b):
            for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
                rect(px, X + dx, y + dy, 1, 1, hexc(c))


def vignette_lisse(w, h, force=1.0, expo=2.2, couleur=(4, 2, 3)):
    """Vignette lisse (texture étirée au blit : autorisé pour les dégradés lisses)."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    dx = (xx + 0.5 - w / 2) / (w / 2)
    dy = (yy + 0.5 - h / 2) / (h / 2)
    d = np.clip(np.sqrt(dx * dx * 0.85 + dy * dy), 0, 1.6)
    a = np.clip((d - 0.35) / 1.05, 0, 1) ** expo * force
    p = np.zeros((h, w, 4), np.uint8)
    p[..., 0], p[..., 1], p[..., 2] = couleur
    p[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(p, "RGBA")


def sceau_rang(px, cx, cy, r, taille=18):
    gf.sceau_rang4(px, cx, cy, r, taille)


def cabochon(px, x, y, w, h=10):
    gf.cabochon_fond(px, x, y, w, h)
