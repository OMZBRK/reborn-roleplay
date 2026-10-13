# -*- coding: utf-8 -*-
"""
gen_sacoche_textures.py — textures de la sacoche « inrō » (menu/inventory/InventoryScreen), au niveau de
la fiche shinobi (DA §4.1, §6) : tout le décor fixe est ici, le code ne pose que des blits à 1 texel = 1 GUI.

Signature de l'écran (inchangée) : l'inrō, boîte laquée à étages portée à la ceinture ; chaque étage est un
filtre (TOUT / SAC / DIVERS) et l'étage actif glisse vers le plateau de laque rouge qui contient le sac. Le
cordon passe par les deux flancs des étages, perle (ojime) en haut, netsuke en bas. La barre 1-9 est une obi
de soie indigo à losanges (hishi). À gauche, le personnage sur un socle laqué entre deux colonnes de plaques.

Usage (depuis minecraft/mod-hud) :  python tools/gen_sacoche_textures.py

Contrat (1 texel = 1 GUI) :
- vignette.png 160×90 : vignette lisse (étirée sur l'écran).
- entete.png 320×48 : plaque laquée 280×28 en (20, 8), cordons, agemaki ; titre en code (haut des capitales
  y 19, centré en x 160).
- onglets.png 156×36 : cellules 26×36, colonne = onglet (装 équipement, 飾 cosmétique) × état (repos, survol,
  actif) : cellule (2·onglet + ... ) -> u = (onglet·3 + état)·26. Fil depuis (13, 0), plaquette 22 en (2, 8)
  (actif : (2, 10)).
- fermer.png 52×38 : cellules 26×38 (repos, survol), plaque 閉 22×22 en (2, 12).
- inro.png 168×108 : étages 56×36, rangée = étage (全 TOUT, 袋 SAC, 雑 DIVERS), colonne = état (repos, survol,
  actif). Corps 52×34 en (1, 0), ombre jusqu'à 56×36 ; kanji cuit centré (haut y 5) ; libellé en code (haut des capitales y 24).
  Étages empilés au pas de 35, cellule posée en (x inrō − 1, y).
- cordon.png 60×176 : cordon derrière les étages ; repère (0, 0) = (x inrō − 4, y premier étage − 20) ;
  ojime centré en (30, 8), brins des flancs en x 2 et x 56, netsuke centré en (30, 160).
- plateau3.png 250×136, plateau4.png 250×161 : plateau 246×132 / 246×157 en (0, 0) (+ ombre) ; grille dès
  (12, 30), filet bas en y h − 28.
- cases.png 176×22 : cases 22×22 : 0 sac, 1 sac survol, 2 sac choisie, 3 case absente (sac plus petit),
  4 ceinture, 5 ceinture survol, 6 ceinture choisie, 7 dépôt possible.
- plaques.png 96×25 : plaques cosmétiques 22×22 (+ombre) : 0 vide, 1 survol, 2 garnie, 3 dépôt possible.
- kanji.png 208×16 : 笠 額 面 耳 衣 上 装 帯 下 背 袋 刀 頭 (cellules 16×16, or éteint) posés sur les plaques vides
  (刀 = emplacement Arme / étui, 頭 = rappel « Tête » de l'onglet Équipement, ajoutés le 13/10).
- socle.png 124×22 : socle laqué sous le personnage, pieds en (62, 3).
- obi.png 274×34 : obi 270×30 en (0, 0) (+ ombre) ; cases dès (12, 4).
- champ.png 124×14 : champ de recherche (coupe horizontale, capD 4).
- pieces.png 210×74 : cabochon 42×12 (0, 0) ; rainure de jauge 204×12 (0, 14) ; or 196×6 (0, 28) ;
  laque rouge 196×6 (0, 36) ; sceau 袋 26×26 (+ombre) (0, 44) ; ojime lueur 9×9 (30, 44).
- detail.png 208×158 : planche fixe 200×150 en (4, 4) (+ombre), bandeau rouge (9, 9, 190×18).
- boutons.png 104×80 : plaques-boutons 100×16 en (1, 1) par rangées de 20 : normal, survol, rouge (danger),
  rouge survol (coupe horizontale, capD 6).
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, R_OR, R_LAQUE, R_LAQUE_ROUGE, nouveau, coller  # noqa: E402

S = ea.Sortie("sacoche", budget=400_000)

KJ_COSM = ["笠", "額", "面", "耳", "衣", "上", "装", "帯", "下", "背", "袋", "刀", "頭"]   # + 刀 arme, 頭 tête (13/10)
KJ_ETAGES = ["全", "袋", "雑"]
KJ_ONGLETS = ["装", "飾"]
PLATEAU = ["1E0609", "2A070D", "3A0A12", "4A0C16", "5A0F1A", "6E1220", "821426"]   # laque sang-de-bœuf


# ═════════════════════════════════════ fond, en-tête, onglets, fermer ═════════════════════════════════════
def vignette():
    S.sauver("vignette.png", ea.vignette_lisse(160, 90, force=0.8, expo=2.0), (160, 90))


def entete():
    px = nouveau(320, 48)
    cx, w, y, h = 160, 280, 8, 28
    x = cx - w // 2
    R = kit.R_CORDE["rouge"]
    for sgn in (-1, 1):
        a = cx + sgn * (w // 2 - 30)
        for k in range(9):
            X = a + sgn * (8 - k)
            rect(px, X, k, 1, 1, hexc(R[3] if k % 2 == 0 else R[2]))
            rect(px, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(R[1]))
    fa.panneau(px, x, y, w, h, seed=7, eclats=False)
    for sgn in (-1, 1):
        fa.anneau_or(px, cx + sgn * (w // 2 - 30), y + 1)
        gx = cx + sgn * (w // 2 + 4)
        rect(px, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
        fa.agemaki(px, gx, y + 16)
        fa.gland3(px, gx, y + 21, 10)
    # losanges or reliés par un filet de part et d'autre du titre (titre posé par le code)
    for sgn in (-1, 1):
        for X in (cx + sgn * 52, cx + sgn * 104):
            for (dx, dy, c) in ((0, -1, R_OR[6]), (-1, 0, R_OR[5]), (1, 0, R_OR[4]), (0, 1, R_OR[3]), (0, 0, R_OR[7])):
                rect(px, X + dx, 22 + dy, 1, 1, hexc(c))
        rect(px, min(cx + sgn * 58, cx + sgn * 98), 22, 41, 1, hexc(R_OR[2]))
    S.sauver("entete.png", px, (320, 48))


def onglets():
    im = nouveau(156, 36)
    for o, kj in enumerate(KJ_ONGLETS):
        for e, etat in enumerate(("repos", "survol", "actif")):
            cell = nouveau(26, 36)
            ty = 8 + (2 if etat == "actif" else 0)
            fa.corde_v(cell, 13, 0, ty, "or", ep=1)
            fa.plaquette22(cell, 2, ty, etat, seed=o * 3 + e)
            kit.anneau_metal(cell, 13, ty, 1)
            col = ea.OR_CLAIR if etat == "actif" else (ea.OR if etat == "survol" else hexc(R_OR[4]))
            gf.kj_px(cell, 13, ty + 3, kj, col, ombre=hexc("1A0608") if etat == "actif" else hexc("050304"))
            im.paste(cell, ((o * 3 + e) * 26, 0))
    S.sauver("onglets.png", im, (156, 36))


def fermer():
    im = nouveau(52, 38)
    for i in range(2):
        cell = nouveau(26, 38)
        fa.corde_v(cell, 13, 0, 12, "rouge", ep=2)
        fa.plaquette22(cell, 2, 12, "fermer_survol" if i else "fermer")
        kit.anneau_metal(cell, 13, 12, 1)
        gf.kj_px(cell, 13, 15, "閉", gf.CREME_KJ, ombre=hexc("050304"))
        im.paste(cell, (i * 26, 0))
    S.sauver("fermer.png", im, (52, 38))


# ═════════════════════════════════════ inrō ═════════════════════════════════════
def masque_etage(w, h, r=3):
    """Étage d'inrō : rectangle aux flancs arrondis (rayon r aux quatre coins)."""
    m = np.ones((h, w), bool)
    for j in range(r):
        k = r - int(round(np.sqrt(max(0.0, r * r - (r - j - 0.5) ** 2))))
        m[j, :k] = False; m[j, w - k:] = False
        m[h - 1 - j, :k] = False; m[h - 1 - j, w - k:] = False
    return m


def etage(kj, etat, seed):
    W, H = 52, 34
    cell = nouveau(56, 36)
    m = masque_etage(W, H)
    kit.ombre_portee(cell, 1, 0, W, H, dx=1, dy=2, force=0.6, rayon=1, masque=m)
    body = nouveau(W, H)
    actif = etat == "actif"
    if actif:
        fa.lq(body, 0, 0, W, H, "rouge", seed=seed, intensite=1.3)
    else:
        fa.lq(body, 0, 0, W, H, "noir", seed=seed, intensite=1.7 if etat == "survol" else 1.15)
    a = np.asarray(body).copy()
    # volume : flancs plus sombres (boîte arrondie), reflet vertical à gauche
    for i in range(W):
        u = i / (W - 1)
        k = 1.0 - 0.55 * max(0.0, abs(u - 0.40) / 0.60) ** 1.8 + (0.18 if 0.20 <= u <= 0.24 else 0.0)
        a[:, i, :3] = np.clip(a[:, i, :3].astype(float) * k, 0, 255).astype(np.uint8)
    a[~m] = 0
    body = Image.fromarray(a, "RGBA")
    # arêtes : lèvre haute claire, joint bas sombre, liseré or sur le pourtour
    b = fa._bord(m)
    pa = np.asarray(body).copy()
    yy, xx = np.mgrid[0:H, 0:W]
    clair = b & ((yy < 2) | (xx < W // 2))
    pa[b] = hexc(R_OR[2] if not actif else R_OR[3])
    pa[clair] = hexc(R_OR[4] if etat == "repos" else (R_OR[6] if etat == "survol" else R_OR[5]))
    body = Image.fromarray(pa, "RGBA")
    # lèvre du couvercle (joint entre étages) : filet d'or 1 GUI à 3 GUI du haut
    rect(body, 3, 3, W - 6, 1, hexc(R_OR[3] if etat == "repos" else R_OR[5]))
    rect(body, 3, 4, W - 6, 1, hexc("0A0506" if not actif else "3A0A12"))
    # maki-e : vague d'or au bas de l'étage (aplat, 2 tons)
    wave = R_OR[4] if etat == "repos" else R_OR[5]
    for i in range(4, W - 4):
        v = int(round(1.0 * np.sin(i / 3.2 + seed)))
        rect(body, i, H - 4 + v, 1, 1, hexc(wave))
        if i % 9 == 4:
            rect(body, i, H - 5 + v, 1, 1, hexc(R_OR[6]))
    # passages du cordon (himotoshi) : anneau d'or sur chaque flanc
    for X in (3, W - 4):
        rect(body, X - 1, 15, 3, 3, hexc(R_OR[2]))
        rect(body, X, 16, 1, 1, hexc("0A0506"))
        rect(body, X - 1, 15, 1, 1, hexc(R_OR[6]))
    # kanji cuit
    kc = ea.OR_CLAIR if actif else (ea.OR if etat == "survol" else hexc(R_OR[4]))
    gf.kj_px(body, W // 2, 5, kj, kc, ombre=hexc("1A0608") if actif else hexc("050304"))
    cell.alpha_composite(body, (1, 0))
    return cell


def inro():
    im = nouveau(168, 108)
    for t, kj in enumerate(KJ_ETAGES):
        for e, etat in enumerate(("repos", "survol", "actif")):
            im.paste(etage(kj, etat, seed=11 + t), (e * 56, t * 36))
    S.sauver("inro.png", im, (168, 108))


def perle(im, cx, cy, r, R):
    """Perle pixel (ojime) : disque 2r+1 à 4 tons, reflet en haut à gauche."""
    for j in range(-r, r + 1):
        for i in range(-r, r + 1):
            d = (i * i + j * j) ** 0.5
            if d > r + 0.3:
                continue
            t = (i + j) / (2 * r + 0.01)
            k = 3 if t < -0.45 else (2 if t < 0.05 else (1 if t < 0.5 else 0))
            if d > r - 0.7:
                k = 0
            rect(im, cx + i, cy + j, 1, 1, hexc(R[k]))
    rect(im, cx - r // 2, cy - r // 2, 1, 1, hexc(R[4]))


def netsuke(im, cx, cy):
    """Netsuke manjū : disque d'ivoire 21 GUI, gorge gravée, kanji 根 (racine) à l'encre brune."""
    R = ["3A2A18", "6E5A3E", "A89070", "D2BE98", "E8DABA", "F6EEDA"]
    r = 10
    kit.ombre_portee(im, cx - r, cy - r, 2 * r + 1, 2 * r + 1, dx=1, dy=2, force=0.55, rayon=1,
                     masque=(np.add.outer((np.arange(2 * r + 1) - r) ** 2, (np.arange(2 * r + 1) - r) ** 2) <= (r + 0.3) ** 2))
    for j in range(-r, r + 1):
        for i in range(-r, r + 1):
            d = (i * i + j * j) ** 0.5
            if d > r + 0.3:
                continue
            t = (i * 0.8 + j) / (2 * r)
            k = 4 if t < -0.25 else (3 if t < 0.2 else 2)
            if d > r - 0.8:
                k = 0 if (i + j) > -2 else 1
            elif d > r - 3.2 and d < r - 2.2:
                k = max(1, k - 1)
            rect(im, cx + i, cy + j, 1, 1, hexc(R[k]))
    rect(im, cx - 5, cy - 7, 3, 1, hexc(R[5]))
    rect(im, cx - 7, cy - 5, 1, 3, hexc(R[5]))
    for j in range(-r, r + 1):          # gravure : anneau et bouton central
        for i in range(-r, r + 1):
            d = (i * i + j * j) ** 0.5
            if 4.6 < d < 5.6:
                rect(im, cx + i, cy + j, 1, 1, hexc(R[1] if (i + j) > 0 else R[0]))
            elif d < 1.6:
                rect(im, cx + i, cy + j, 1, 1, hexc(R[1]))
    # anneau d'or où le cordon passe
    kit.anneau_metal(im, cx, cy - r - 1, 1)


def cordon():
    im = nouveau(60, 176)
    top, xs = 8, (2, 56)
    yb = 20 + 3 * 35 - 1          # bas du dernier étage (étages au pas de 35)
    # deux brins qui montent en V vers l'ojime, descendent le long des flancs et se rejoignent sous l'inrō
    for X in xs:
        kit.cordelette(im, 30, top + 4, X, top + 14, "rouge", mou=0.0, ep=2)
        kit.cordelette(im, X, top + 14, X, yb + 4, "rouge", ep=2)
        kit.cordelette(im, X, yb + 4, 30, yb + 20, "rouge", ep=2)
    kit.cordelette(im, 30, yb + 20, 30, 148, "rouge", ep=2)
    fa.agemaki(im, 30, yb + 21)
    netsuke(im, 30, 160)
    perle(im, 30, top, 4, ["5A1A10", "8E2A18", "C2502C", "E07A4A", "FFD2A0"])
    rect(im, 29, 0, 2, top - 4, hexc(kit.R_CORDE["rouge"][2]))
    S.sauver("cordon.png", im, (60, 176))


# ═════════════════════════════════════ plateau, cases, obi ═════════════════════════════════════
def plateau(rows):
    w, h = 246, 30 + rows * 25 - 3 + 30
    im = nouveau(w + 4, h + 4)
    fa.panneau(im, 0, 0, w, h, seed=31 + rows, ton=PLATEAU, eclats=False)
    # filet sous l'en-tête (recherche, compte) et au-dessus du poids
    for Y, c1, c2 in ((24, R_OR[4], "2A070D"), (h - 28, R_OR[4], "2A070D")):
        rect(im, 10, Y, w - 20, 1, hexc(c2))
        rect(im, 10, Y + 1, w - 20, 1, hexc(c1))
        for X in (10, w - 11):
            rect(im, X, Y, 1, 2, hexc(R_OR[6]))
    S.sauver(f"plateau{rows}.png", im, (w + 4, h + 4))


def case(kind):
    c = nouveau(22, 22)
    ceinture = kind.startswith("ceinture")
    if kind == "absente":
        fa.lq(c, 1, 1, 20, 20, ["1E0609", "24070B", "2A080D", "300A10"], seed=3, reflet=False)
        rect(c, 0, 0, 22, 1, hexc("1A0507")); rect(c, 0, 0, 1, 22, hexc("1A0507"))
        rect(c, 0, 21, 22, 1, hexc("5A121C")); rect(c, 21, 0, 1, 22, hexc("5A121C"))
        for k in range(3, 19, 4):
            rect(c, k, 21 - k, 1, 1, hexc("3A0C12"))
        return c
    if ceinture:
        R = ["05060C", "080A14", "0B0E1C", "0F1424", "141A2E"]
    else:
        R = ["1A0408", "24060B", "2E080F", "3A0A12", "460C16"] if kind != "sac_choisie" else \
            ["3A0A12", "4E0D17", "63111D", "7A1322", "8E1628"]
    fa.lq(c, 1, 1, 20, 20, R, seed=5, reflet=False)
    # creux : ombre en haut et à gauche, arête claire en bas et à droite
    sh = hexc("0A0204" if not ceinture else "020308")
    rect(c, 1, 1, 20, 1, sh); rect(c, 1, 1, 1, 20, sh)
    rect(c, 2, 2, 18, 1, hexc(R[0])); rect(c, 2, 2, 1, 18, hexc(R[0]))
    if kind in ("sac", "ceinture"):
        b1, b2 = (R_OR[2], R_OR[3]) if not ceinture else (R_OR[3], R_OR[4])
    elif kind == "depot":
        b1, b2 = R_OR[5], R_OR[7]
    else:
        b1, b2 = R_OR[4], R_OR[7]
    rect(c, 0, 0, 22, 1, hexc(b1)); rect(c, 0, 0, 1, 22, hexc(b1))
    rect(c, 0, 21, 22, 1, hexc(b2)); rect(c, 21, 0, 1, 22, hexc(b2))
    if kind.endswith("survol") or kind.endswith("choisie") or kind == "depot":
        fa.kanagus_mini(c, 0, 0, 22, 22)
    if kind == "depot":
        rect(c, 3, 3, 16, 16, (217, 169, 94, 40))
    return c


def cases():
    im = nouveau(176, 22)
    for i, k in enumerate(("sac", "sac_survol", "sac_choisie", "absente", "ceinture", "ceinture_survol",
                           "ceinture_choisie", "depot")):
        im.paste(case(k), (i * 22, 0))
    S.sauver("cases.png", im, (176, 22))


def plaques():
    im = nouveau(96, 25)
    for i, etat in enumerate(("vide", "survol", "garnie", "depot")):
        cell = nouveau(24, 25)
        kit.ombre_portee(cell, 0, 0, 22, 22, dx=1, dy=2, force=0.55, rayon=1)
        if etat == "depot":
            fa.lq(cell, 1, 1, 20, 20, "rouge", seed=6)
        else:
            fa.lq(cell, 1, 1, 20, 20, "noir", seed=6 + i, intensite={"vide": 0.8, "survol": 1.8, "garnie": 1.2}[etat])
        if etat == "vide":
            rect(cell, 0, 0, 22, 1, hexc(R_OR[3])); rect(cell, 0, 0, 1, 22, hexc(R_OR[3]))
            rect(cell, 0, 21, 22, 1, hexc(R_OR[2])); rect(cell, 21, 0, 1, 22, hexc(R_OR[2]))
        else:
            fa.liseret(cell, 0, 0, 22, 22)
        fa.ferrures3(cell, 0, 0, 22, 22, terne=etat == "vide")
        im.paste(cell, (i * 24, 0))
    S.sauver("plaques.png", im, (96, 25))


def kanji_cosm():
    im = nouveau(16 * len(KJ_COSM), 16)
    for i, kj in enumerate(KJ_COSM):
        gf.kj_px(im, i * 16 + 8, 0, kj, hexc(R_OR[3]))
    S.sauver("kanji.png", im, (208, 16))


def socle():
    W, H = 124, 22
    px = nouveau(W, H)
    y_av, ep = 12, 6
    kit.ombre_portee(px, 2, y_av, W - 6, ep, dx=2, dy=2, force=0.6, rayon=2)
    for y in range(0, y_av):
        t = y / y_av
        half = 46 + 13 * t
        x0, x1 = int(round(W / 2 - 2 - half)), int(round(W / 2 - 2 + half))
        c = fa.mix(R_LAQUE[3], R_LAQUE[6], 0.25 + 0.55 * t)
        rect(px, x0, y, x1 - x0, 1, c)
        rect(px, x0, y, 1, 1, hexc(R_OR[3])); rect(px, x1 - 1, y, 1, 1, hexc(R_OR[2]))
    rect(px, 16, 0, W - 36, 1, hexc(R_OR[4]))
    fa.lq(px, 1, y_av, W - 6, ep, "rouge", seed=3)
    rect(px, 1, y_av, W - 6, 1, ea.BISEAU_CLAIR)
    rect(px, 1, y_av + ep - 1, W - 6, 1, hexc("1A0608"))
    fa.ferrures3(px, 1, y_av, W - 6, ep)
    S.sauver("socle.png", px, (W, H))


def obi():
    W, H = 270, 30
    im = nouveau(W + 4, H + 4)
    kit.ombre_portee(im, 0, 0, W, H, dx=2, dy=3, force=0.6, rayon=2)
    kit.bande_tissu(im, 0, 0, W, H, couleur="1C2442", motif="losanges", liseret="or", seed=2)
    # pli de la soie : bandes sombres en haut et en bas sous les lisières
    a = np.asarray(im).copy()
    for j, k in ((2, 0.72), (3, 0.86), (H - 4, 0.80), (H - 3, 0.66)):
        a[j, 0:W, :3] = (a[j, 0:W, :3].astype(float) * k).astype(np.uint8)
    im = Image.fromarray(a, "RGBA")
    # bouts de l'obi : coupe nette laquée d'or
    for X in (0, W - 2):
        rect(im, X, 0, 2, H, hexc(R_OR[3]))
        rect(im, X, 0, 1, H, hexc(R_OR[5]))
    S.sauver("obi.png", im, (W + 4, H + 4))


def champ():
    im = nouveau(124, 14)
    fa.lq(im, 0, 0, 124, 14, ["2A070D", "300810", "3A0A12", "420B14"], seed=9, reflet=False)
    rect(im, 0, 0, 124, 1, hexc("1A0407")); rect(im, 0, 0, 1, 14, hexc("1A0407"))
    rect(im, 0, 13, 124, 1, hexc(R_OR[3])); rect(im, 123, 0, 1, 14, hexc(R_OR[2]))
    S.sauver("champ.png", im, (124, 14))


def pieces():
    im = nouveau(210, 74)
    ea.cabochon(im, 0, 0, 40, 10)
    kit.ombre_portee(im, 0, 14, 202, 10, dx=1, dy=1, force=0.5, rayon=0)
    fa.lq(im, 1, 15, 200, 8, ["050304", "0A0608", "0F090B", "140C0F"], seed=2, reflet=False)
    rect(im, 0, 14, 202, 1, hexc(R_OR[4])); rect(im, 0, 23, 202, 1, hexc(R_OR[2]))
    rect(im, 0, 14, 1, 10, hexc(R_OR[3])); rect(im, 201, 14, 1, 10, hexc(R_OR[2]))
    for X in range(21, 200, 20):
        rect(im, X, 16, 1, 2, hexc(R_OR[2]))
    fa.lq(im, 0, 28, 196, 6, ["6A4E24", "8E6A30", "B08442", "D9A95E", "E8C27A"], seed=4)
    rect(im, 0, 28, 196, 1, hexc(R_OR[7]))
    fa.lq(im, 0, 36, 196, 6, "rouge", seed=5)
    rect(im, 0, 36, 196, 1, hexc("E26474"))
    gf.hanko_px(im, 13, 57, "袋", 26, ea.R_VERM, seed=8, usure=0.05)
    # lueur de l'ojime (petite étoile d'or, posée à alpha variable par le code)
    for (dx, dy, c, al) in ((4, 4, R_OR[8], 255), (3, 4, R_OR[6], 200), (5, 4, R_OR[6], 200), (4, 3, R_OR[6], 200),
                            (4, 5, R_OR[6], 200), (2, 4, R_OR[5], 110), (6, 4, R_OR[5], 110), (4, 2, R_OR[5], 110),
                            (4, 6, R_OR[5], 110), (4, 1, R_OR[4], 60), (4, 7, R_OR[4], 60), (1, 4, R_OR[4], 60),
                            (7, 4, R_OR[4], 60)):
        rect(im, 30 + dx, 44 + dy, 1, 1, hexc(c)[:3] + (al,))
    S.sauver("pieces.png", im, (210, 74))


def detail():
    W, H = 200, 150
    im = nouveau(W + 8, H + 8)
    fa.panneau_opacite(im, 4, 4, W, H, 0.96, seed=41, eclats=False)
    bx, by, bw, bh = 4 + 5, 4 + 5, W - 10, 18
    fa.lq(im, bx, by, bw, bh, "rouge", seed=8)
    rect(im, bx, by, bw, 1, hexc(R_LAQUE_ROUGE[2]))
    rect(im, bx, by + bh, bw, 1, hexc(R_OR[4]))
    rect(im, bx, by + bh + 1, bw, 1, hexc("120608"))
    S.sauver("detail.png", im, (W + 8, H + 8))


def boutons():
    im = nouveau(104, 80)
    for i, (etat, ton) in enumerate((("normal", "noir"), ("survol", "noir"), ("normal", "rouge"), ("survol", "rouge"))):
        cell = nouveau(104, 20)
        gf.bouton4(cell, 1, 1, etat, ton=ton, h=16, w=100)
        im.paste(cell, (0, i * 20))
    S.sauver("boutons.png", im, (104, 80))


if __name__ == "__main__":
    vignette()
    entete()
    onglets()
    fermer()
    inro()
    cordon()
    plateau(3)
    plateau(4)
    cases()
    plaques()
    kanji_cosm()
    socle()
    obi()
    champ()
    pieces()
    detail()
    boutons()
    S.bilan()
