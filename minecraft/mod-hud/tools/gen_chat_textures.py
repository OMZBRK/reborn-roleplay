# -*- coding: utf-8 -*-
"""
gen_chat_textures.py — textures du chat RP en panneau DA (chat/ChatPanel, chat/EmojiPicker), au niveau de la
fiche shinobi (DA §4.1, §6). Le chat est un élément EN JEU : il reste discret et lisible, et coûte quelques blits
par image au lieu des centaines de fill() de l'ancien rendu (veines du bois, vagues seigaiha dessinées en anneaux).

Signature du chat (inchangée) : un panneau tenu entre deux poutres de bois (onglets en haut, saisie en bas),
ferrures d'or, et des vagues seigaiha d'or (maki-e) dans le coin bas droit de la zone des messages. Le fond de la
zone des messages n'est PAS dans la texture : le code le remplit à l'opacité choisie par le joueur.

Usage (depuis minecraft/mod-hud) :  python tools/gen_chat_textures.py

Contrat (1 texel = 1 GUI) :
- cadre.png 334×224 : panneau 330×220 en (0, 0) (+ ombre 4) : poutre haute 19, poutre basse 26, montants,
  liseré or, ferrures, filet d'or intérieur ; intérieur transparent. Pour un panneau w×h (240 ≤ w ≤ 330,
  136 ≤ h ≤ 220) : DaKit.coupe(région (w + 4)×(h + 4), capD 84, capB 62). Colonnes 150..250 et rangées
  70..162 identiques : la coupe ne se voit pas.
- pieces.png 150×120 :
  · (0, 0) onglet actif 120×15 (laque rouge, 13 + ombre ; coupe horizontale, capD 4) ;
  · (0, 16) onglet survolé 120×15 (laque noire) ;
  · (0, 32) plaque 16 (laque noire) 120×18 ; (0, 50) survol ; (0, 68) laque rouge ; (0, 86) rouge survol
    (16 + ombre ; coupe horizontale, capD 4) ;
  · (122, 0) point « non lu » 5×5 ; (122, 8) case emoji survolée 22×20 ;
  · (0, 104) fond d'icône survolée 14×13.
- washi.png 304×34 : bande de washi 300×16 (saisie) en (0, 0), 300×15 (recherche) en (0, 18) (coupe
  horizontale, capD 4).
- emojis.png 144×104 : panneau du sélecteur 140×100 en (0, 0) (+ ombre) ; titre en code (7, 3), filet y 12.
- reglages.png 548×280 : fenêtre des réglages du chat (chat/ChatSettingsScreen), même signature que le panneau :
  · panneau 520×272 en (1, 1) (contour au texel 0, ombre jusqu'à 524×276) : laque noire, poutre haute 22 (cartouche
    du titre 168×15 en (176, 4) de la fenêtre, titre posé par le code), poutre basse 26 (bouton Fermer posé par le
    code), filets d'or, séparation des deux colonnes en x 260 (y 34 → 232), vagues seigaiha dans le coin bas droit ;
  · (528, 0) cadre de pastille 15×15 (repos), (528, 16) survol, (528, 32) choisie : pastille 11×11 en (2, 2),
    couleur posée par le code.
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, R_OR, R_LAQUE, R_LAQUE_ROUGE, nouveau, coller  # noqa: E402

S = ea.Sortie("chat", budget=200_000)
W, H, HDR, BEAM = 330, 220, 19, 26
CAPD, CAPB = 84, 62


def figer_colonnes(im, x0, x1):
    a = np.asarray(im).copy()
    a[:, x0:x1 + 1] = a[:, x0:x0 + 1]
    return Image.fromarray(a, "RGBA")


def figer_rangees(im, y0, y1):
    a = np.asarray(im).copy()
    a[y0:y1 + 1] = a[y0:y0 + 1]
    return Image.fromarray(a, "RGBA")


def poutre(im, x, y, w, h, seed, haut):
    """Poutre de bois veiné (fil horizontal), arête claire en haut, chanfrein sombre en bas."""
    kit.bois(im, x, y, w, h, "moyen" if haut else "sombre", sens="h", seed=seed, noeuds=0, biseau=False)
    a = np.asarray(im).copy()
    for j in range(h):           # léger volume : plus clair au milieu de la poutre
        t = j / max(1, h - 1)
        k = 0.82 + 0.26 * (1 - abs(t - 0.4) * 1.6)
        a[y + j, x:x + w, :3] = np.clip(a[y + j, x:x + w, :3].astype(float) * k, 0, 255).astype(np.uint8)
    im2 = Image.fromarray(a, "RGBA")
    rect(im2, x, y, w, 1, hexc("C8945C"))
    rect(im2, x, y + 1, w, 1, hexc("8A5A34"))
    rect(im2, x, y + h - 1, w, 1, hexc("1A0D06"))
    return im2


def seigaiha(im, x, y, w, h):
    """Vagues seigaiha d'or en maki-e : arcs pixel, plus présents vers le coin bas droit (alpha en paliers)."""
    for r in range(h // 5 + 2):
        cy = y + r * 5 + 6
        off = (r % 2) * 6
        for cx in range(x - 6 + off, x + w + 12, 12):
            t = min(1.0, max(0.0, (cx - x) / w * 0.75 + (cy - y) / h * 0.45))
            if t < 0.25:
                continue
            al = int(round([0, 22, 34, 48, 62][min(4, int(t * 5))]))
            for (rad, k) in ((6, 1.0), (3, 0.66)):
                for dx in range(-rad, rad + 1):
                    for dy in range(-rad, 1):
                        d = (dx * dx + dy * dy) ** 0.5
                        if abs(d - rad) < 0.5:
                            X, Y = cx + dx, cy + dy
                            if x <= X < x + w and y <= Y < y + h:
                                rect(im, X, Y, 1, 1, hexc(R_OR[5])[:3] + (int(al * k),))


def cadre():
    im = nouveau(W + 4, H + 4)
    kit.ombre_portee(im, 0, 0, W, H, dx=2, dy=3, force=0.5, rayon=2)
    # montants : laque noire 3 GUI de chaque côté, l'intérieur reste transparent
    for X in (0, W - 3):
        fa.lq(im, X, 0, 3, H, "noir", seed=3 + X, reflet=False)
    im = poutre(im, 0, 0, W, HDR, seed=21, haut=True)
    im = poutre(im, 0, H - BEAM, W, BEAM, seed=22, haut=False)
    # filets d'or : sous la poutre haute, au-dessus de la poutre basse ; filet intérieur discret
    rect(im, 3, HDR, W - 6, 1, hexc(R_OR[3]))
    rect(im, 3, HDR + 1, W - 6, 2, (8, 4, 6, 110))
    rect(im, 3, H - BEAM - 1, W - 6, 1, hexc(R_OR[3]))
    oc = hexc(R_OR[5])[:3] + (46,)
    rect(im, 4, HDR + 3, W - 8, 1, oc); rect(im, 4, H - BEAM - 3, W - 8, 1, oc)
    rect(im, 4, HDR + 3, 1, H - BEAM - HDR - 5, oc); rect(im, W - 5, HDR + 3, 1, H - BEAM - HDR - 5, oc)
    seigaiha(im, W - 4 - 66, H - BEAM - 4 - 30, 66, 30)
    fa.liseret(im, 0, 0, W, H)
    fa.kanagus(im, 0, 0, W, H, deborde=0)
    # ferrures de jonction poutre / montant (petits rivets)
    for X in (5, W - 7):
        for Y in (HDR - 4, H - BEAM + 3):
            rect(im, X, Y, 2, 2, hexc(R_OR[4])); rect(im, X, Y, 1, 1, hexc(R_OR[7]))
    im = figer_colonnes(im, 150, W + 4 - CAPD)
    im = figer_rangees(im, 70, H + 4 - CAPB)
    S.sauver("cadre.png", im, (W + 4, H + 4))


def plaque(im, x, y, w, h, ton, survol):
    kit.ombre_portee(im, x, y, w, h, dx=1, dy=1, force=0.5, rayon=0)
    rect(im, x, y, w, h, hexc("120806"))
    if ton == "rouge":
        fa.lq(im, x + 1, y + 1, w - 2, h - 2, "rouge", seed=w + h, intensite=1.7 if survol else 1.2)
        rect(im, x + 1, y + 1, w - 2, 1, hexc("E26474" if survol else "C03044"))
    else:
        fa.lq(im, x + 1, y + 1, w - 2, h - 2, "noir", seed=w + h, intensite=1.9 if survol else 1.1)
    c1, c2 = (R_OR[6], R_OR[4]) if survol or ton == "rouge" else (R_OR[4], R_OR[2])
    rect(im, x + 1, y, w - 2, 1, hexc(c1)); rect(im, x, y + 1, 1, h - 2, hexc(c1))
    rect(im, x + 1, y + h - 1, w - 2, 1, hexc(c2)); rect(im, x + w - 1, y + 1, 1, h - 2, hexc(c2))


def pieces():
    im = nouveau(150, 120)
    plaque(im, 0, 0, 119, 13, "rouge", False)
    rect(im, 2, 11, 115, 1, hexc(R_OR[5]))
    plaque(im, 0, 16, 119, 13, "noir", True)
    for i, (ton, sv) in enumerate((("noir", False), ("noir", True), ("rouge", False), ("rouge", True))):
        plaque(im, 0, 32 + i * 18, 119, 16, ton, sv)
    # point « non lu »
    rect(im, 122, 0, 5, 5, hexc(R_OR[5])); rect(im, 123, 1, 3, 3, hexc("C01E35")); rect(im, 123, 1, 1, 1, hexc("E26474"))
    # case emoji survolée
    rect(im, 122, 8, 22, 20, hexc(R_OR[5])[:3] + (40,))
    for (a, b, w_, h_) in ((122, 8, 22, 1), (122, 27, 22, 1), (122, 8, 1, 20), (143, 8, 1, 20)):
        rect(im, a, b, w_, h_, hexc(R_OR[5])[:3] + (150,))
    fa.kanagus_mini(im, 122, 8, 22, 20)
    # fond d'icône survolée
    rect(im, 1, 104, 12, 13, (0, 0, 0, 80)); rect(im, 0, 105, 14, 11, (0, 0, 0, 80))
    S.sauver("pieces.png", im, (150, 120))


def washi():
    im = nouveau(304, 34)
    for y, h, seed in ((0, 16, 31), (18, 15, 32)):
        kit.washi(im, 0, y, 300, h, "creme", seed=seed, bords="nets", ombre=False, fibres=False, taches=False)
        rect(im, 0, y, 300, 1, hexc("6B5434")); rect(im, 0, y, 1, h, hexc("6B5434"))
        rect(im, 0, y + h - 1, 300, 1, hexc("F8F0DE")); rect(im, 299, y, 1, h, hexc("F8F0DE"))
    S.sauver("washi.png", im, (304, 34))


def emojis():
    im = nouveau(144, 104)
    fa.panneau_opacite(im, 0, 0, 140, 100, 1.0, seed=12, eclats=False)
    rect(im, 5, 12, 130, 1, hexc(R_OR[3]))
    rect(im, 5, 13, 130, 1, hexc("0A0506"))
    S.sauver("emojis.png", im, (144, 104))


RW, RH = 520, 272           # fenêtre des réglages
RHDR, RBEAM = 22, 26


def reglages():
    im = nouveau(548, 280)
    x0, y0 = 1, 1
    fa.panneau(im, x0, y0, RW, RH, seed=41, eclats=False, kanagu_=False)
    im = poutre(im, x0, y0, RW, RHDR, seed=43, haut=True)
    im = poutre(im, x0, y0 + RH - RBEAM, RW, RBEAM, seed=44, haut=False)
    # filets d'or sous / sur les poutres ; filet intérieur discret
    rect(im, x0 + 1, y0 + RHDR, RW - 2, 1, hexc(R_OR[3]))
    rect(im, x0 + 1, y0 + RHDR + 1, RW - 2, 2, (8, 4, 6, 110))
    rect(im, x0 + 1, y0 + RH - RBEAM - 1, RW - 2, 1, hexc(R_OR[3]))
    oc = hexc(R_OR[5])[:3] + (46,)
    ih = RH - RBEAM - RHDR - 7
    rect(im, x0 + 4, y0 + RHDR + 3, RW - 8, 1, oc); rect(im, x0 + 4, y0 + RH - RBEAM - 3, RW - 8, 1, oc)
    rect(im, x0 + 4, y0 + RHDR + 3, 1, ih, oc); rect(im, x0 + RW - 5, y0 + RHDR + 3, 1, ih, oc)
    seigaiha(im, x0 + RW - 5 - 96, y0 + RH - RBEAM - 4 - 40, 96, 40)
    # séparation des colonnes : filet or éteint + ombre, losanges aux bouts
    cx = x0 + RW // 2
    ya, yb = y0 + 34, y0 + RH - 40
    rect(im, cx, ya, 1, yb - ya, hexc(R_OR[2]))
    rect(im, cx + 1, ya, 1, yb - ya, (8, 4, 6, 140))
    for Y in (ya - 2, yb):
        for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
            rect(im, cx + dx, Y + dy, 1, 1, hexc(c))
    # cartouche du titre sur la poutre haute (laque noire, liseré or, équerres)
    tx, tw, th = x0 + RW // 2 - 84, 168, 15
    kit.ombre_portee(im, tx, y0 + 4, tw, th, dx=1, dy=1, force=0.55, rayon=0)
    fa.lq(im, tx + 1, y0 + 5, tw - 2, th - 2, "noir", seed=45, intensite=1.2)
    fa.liseret(im, tx, y0 + 4, tw, th)
    fa.kanagus_mini(im, tx, y0 + 4, tw, th)
    for X in (tx - 8, tx + tw + 6):        # clous de part et d'autre
        rect(im, X, y0 + 10, 2, 2, hexc(R_OR[4])); rect(im, X, y0 + 10, 1, 1, hexc(R_OR[7]))
    fa.liseret(im, x0, y0, RW, RH)
    fa.kanagus(im, x0, y0, RW, RH, deborde=0)
    for X in (x0 + 5, x0 + RW - 7):
        for Y in (y0 + RHDR - 4, y0 + RH - RBEAM + 3):
            rect(im, X, Y, 2, 2, hexc(R_OR[4])); rect(im, X, Y, 1, 1, hexc(R_OR[7]))
    # cadres de pastille de couleur : repos, survol, choisie
    for i, etat in enumerate(("repos", "survol", "choisie")):
        X, Y = 528, i * 16
        kit.ombre_portee(im, X, Y, 15, 15, dx=1, dy=1, force=0.5, rayon=0)
        rect(im, X + 1, Y + 1, 13, 13, hexc("120806"))
        if etat == "repos":
            fa.liseret_terne(im, X, Y, 15, 15)
        else:
            fa.liseret(im, X, Y, 15, 15)
        if etat == "choisie":
            fa.kanagus_mini(im, X, Y, 15, 15)
        rect(im, X + 2, Y + 2, 11, 11, (0, 0, 0, 0))   # la couleur est posée par le code
    S.sauver("reglages.png", im, (548, 280))


if __name__ == "__main__":
    reglages()
    cadre()
    pieces()
    washi()
    emojis()
    S.bilan()
