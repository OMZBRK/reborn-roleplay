# -*- coding: utf-8 -*-
"""
gen_garde_textures.py — textures du Poste de garde et de l'Infirmerie (staff/StaffScreen, GardeScreen), au niveau
de la fiche shinobi (DA §4.1, §6).

Signature de l'écran (DA §1, §4 « Poste de garde v2 (bois et ferrures) ») : cadre de bois veiné à ferrures d'or,
papier washi qui se déroule (makimono) sous une poutre laquée, colonne de navigation à gauche ; chaque joueur est
une plaquette de bois de hinoki (fuda) pendue à une ficelle rouge, sanctionner revient à apposer le sceau 罰.
Infirmerie : chaque blessé est une bande de papier (tanzaku) pendue à une corde de paille, l'encens se consume.
Le code ne dessine que ce qui bouge ou dépend des données (texte, encens, jauges, carte, zones).

Usage (depuis minecraft/mod-hud) :  python tools/gen_garde_textures.py

Contrat (1 texel = 1 GUI) — dossier textures/gui/garde/ :
- cadre.png 668×528 : cadre de bois (contour sombre compris) 664×524 en (0, 0) + ombre (3, 4) ; ouverture du
  papier en (12, 12), 640×500 max. Coupe côté code : (sx0 − 12, sy0 − 12), (w + 28, h + 28), capD = capB = 48.
- washi.png 640×500 : papier (poutre laquée rouge 5 GUI en haut, ombre dessous, bande de navigation plus sombre
  0..79 et filet 79..80, ombres latérales). Coupe capD = capB = 8 ; le déroulé coupe en hauteur (scissor).
- entete.png 320×96 : rangées de 48 (0 Poste de garde 番, 1 Infirmerie 医) : plaque laquée 300×28 en (10, 8),
  cordons, agemaki et glands, sceau et titre ArcadePix cuits. Posée en (largeur/2 − 160, 0).
- boutons.png 200×91 : rangées de 13 (noir, noir survol, rouge, rouge survol, vert, vert survol, inactif) ;
  bouton 200×12 en (0, 13·r), ombre d'1 GUI dessous. Coupe horizontale, capD 6.
- fuda.png 560×176 : rangées de 88 (repos, survol) : plaquette de hinoki (contour compris) 557×85 en (0, 0),
  coins gauches biseautés de 5, ombre (3, 3). Coupe : (x − 1, y − 1), (w + 5, h + 5), capD = capB = 12.
- carte.png 560×124 : rangées de 62 (repos, survol) : carte 558×60 (washi, bord de bois) + ombre 2.
  Coupe capD = capB = 8.
- tanzaku.png 134×304 : bande de papier 130×300 (contour compris) en (0, 0), ombre (3, 3), feuilles d'or.
  Coupe capD = capB = 10.
- panneau.png 304×224 : carte de washi à bord de bois et équerres 300×220 en (0, 0), ombre 2. Coupe 10 / 10.
- carte_cadre.png 470×510 : cadre de la carte (bois 3 GUI + filet d'or), intérieur transparent. Coupe 12 / 12.
- sceau.png 64×64 : sceau 罰 vermillon (tampon, incliné).
- pieces.png 680×232 :
    (0, 0)    entrée de navigation active 90×13 (laque rouge, filet d'or à gauche), coupeH capD 6 ;
    (0, 13)   entrée survolée 90×13 (washi éclairci) ;
    (100, 0)  pastille compteur 24×11 (laque rouge) ; (124, 0) pastille vive (alerte) ; coupeH capD 4 ;
    (100, 12) sceau vierge 11×11 (groupes de navigation, kanji posé par le code) ;
    (0, 26)   trait de pinceau vermillon 240×5 (coupeH, capD 40 : la queue) ;
    (0, 32)   ficelle de fuda (trou + ficelle rouge) 14×7 ;
    (20, 32)  losange or 5×5 ;
    (0, 40)   planche « Fermer » 124×17 (repos) ; (130, 40) survol ;
    (0, 58)   badge de grade 160×14 (laque noire), coupeH capD 6 ;
    (0, 72)   sceau de score rouge 140×13 ; (0, 85) ambre ; coupeH capD 5 ;
    (0, 98)   bande de toast 440×16 (washi, extrémité laquée, sceau d'or), coupeH capD 6 ;
    (0, 114)  champ de saisie 600×15, coupeH capD 4 ;
    (0, 130)  flèches 16×16 : < actif, < survol, < éteint, > actif, > survol, > éteint (x 0..80) ;
    (100, 130) perles 6×6 (éteinte, allumée) ; (114, 130) drapeaux de preuve 13×13 (non, oui) ;
    (144, 130) coupelle d'encens 12×4 ;
    (0, 150)  rouleau (jiku) 680×9 (coupeH capD 14, embouts d'or) ;
    (0, 160)  corde de paille (shimenawa) 680×6, coupeH capD 8 ;
    (0, 168)  réglette : rainure 300×4 (coupeH capD 3), (0, 174) poignée 7×9 ;
    (320, 168) filigranes 64×64 : 番 (x 320), 医 (x 384), encre délavée.
"""
from __future__ import annotations

import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ecrans_art as ea  # noqa: E402
from ecrans_art import fa, gf, kit, hexc, rect, arcade, coller, R_OR, R_LAQUE, nouveau  # noqa: E402

S = ea.Sortie("garde", budget=700_000)

PW, PH = 640, 500          # papier maximal
B = 12                     # contour 1 + bois 11
NAVB = 80                  # bande de navigation (0..79) + filet

WOOD_DARK = hexc("2E1C11")
R_HINOKI = ["C9A87C", "D2B286", "DABC91", "E0C49B", "E6CBA4", "EBD2AD", "F0D9B6", "F4E0C0"]
R_WASHI_PAPIER = ["BBA37C", "CDB68E", "D6C09A", "DCC7A2", "E0CCA8", "E4D1AE", "E8D6B4", "EDDCBC"]
R_WASHI_NAV = ["AA9068", "BCA27A", "C6AD86", "CCB38D", "D1B993", "D6BF9A", "DBC5A1", "E1CCA9"]
R_WASHI_TANZAKU = ["D8CCB2", "E4D8BE", "EADFC6", "EEE4CC", "F1E8D2", "F4ECD8", "F7F0DF", "FAF5E8"]
R_LAQUE_VERTE = ["0B1610", "102016", "152A1D", "1B3524", "22412C", "2A5036", "336140", "3F744D"]


def arr(im):
    return np.asarray(im).copy()


def assombrir(im, x, y, w, h, k):
    """Multiplie la couleur d'une zone (paliers nets)."""
    a = np.asarray(im).copy()
    reg = a[y:y + h, x:x + w].astype(float)
    reg[..., :3] *= k
    a[y:y + h, x:x + w] = np.clip(reg, 0, 255).astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def ombre_bande(im, x, y, w, h, sens, ks):
    """Ombre en paliers d'1 GUI : ks = facteurs successifs depuis le bord (sens 'h' haut, 'g' gauche, 'd' droite)."""
    for i, k in enumerate(ks):
        if sens == "h":
            im = assombrir(im, x, y + i, w, 1, k)
        elif sens == "g":
            im = assombrir(im, x + i, y, 1, h, k)
        else:
            im = assombrir(im, x + w - 1 - i, y, 1, h, k)
    return im


def rivet(px, x, y):
    rect(px, x, y, 2, 2, hexc(R_OR[4]))
    rect(px, x, y, 1, 1, hexc(R_OR[7]))
    rect(px, x + 1, y + 1, 1, 1, hexc(R_OR[2]))


def bande_fer(px, x, y, w, h):
    """Ferrure en bande (plat d'or ciselé, deux rivets) posée en travers d'un montant."""
    rect(px, x, y, w, h, hexc(R_OR[5]))
    if w >= h:   # bande horizontale ? (sur un montant vertical)
        rect(px, x, y, w, 1, hexc(R_OR[7]))
        rect(px, x, y + h - 1, w, 1, hexc(R_OR[2]))
        rect(px, x, y + 1, 1, h - 2, hexc(R_OR[6]))
        rect(px, x + w - 1, y + 1, 1, h - 2, hexc(R_OR[3]))
        rivet(px, x + 2, y + h // 2 - 1)
        rivet(px, x + w - 4, y + h // 2 - 1)
    else:
        rect(px, x, y, 1, h, hexc(R_OR[7]))
        rect(px, x + w - 1, y, 1, h, hexc(R_OR[2]))
        rect(px, x + 1, y, w - 2, 1, hexc(R_OR[6]))
        rect(px, x + 1, y + h - 1, w - 2, 1, hexc(R_OR[3]))
        rivet(px, x + w // 2 - 1, y + 2)
        rivet(px, x + w // 2 - 1, y + h - 4)


# ═════════════════════════════════════ cadre de bois ═════════════════════════════════════
def cadre():
    W, H = PW + 2 * B, PH + 2 * B
    im = nouveau(W + 4, H + 4)
    kit.ombre_portee(im, 0, 0, W, H, dx=3, dy=4, force=0.6, rayon=2)
    # contour sombre (anneau)
    rect(im, 0, 0, W, H, WOOD_DARK)
    # traverses (fil horizontal) et montants (fil vertical), assemblage à mi-bois
    kit.bois(im, 1, 1, W - 2, B - 1, "moyen", sens="h", seed=3, noeuds=2)
    kit.bois(im, 1, H - B, W - 2, B - 1, "moyen", sens="h", seed=4, noeuds=2)
    kit.bois(im, 1, B, B - 1, H - 2 * B, "moyen", sens="v", seed=5, noeuds=1)
    kit.bois(im, W - B, B, B - 1, H - 2 * B, "moyen", sens="v", seed=6, noeuds=1)
    im = assombrir(im, 1, B, B - 1, H - 2 * B, 0.86)
    im = assombrir(im, W - B, B, B - 1, H - 2 * B, 0.86)
    # joints des montants
    for X in (B - 1, W - B):
        rect(im, X - (0 if X == B - 1 else 0), B, 1, H - 2 * B, hexc("1A0E08"))
    rect(im, 1, B - 1, W - 2, 1, hexc("1A0E08"))
    rect(im, 1, H - B, W - 2, 1, hexc("3A2414"))
    # chants : lumière en haut / à gauche, ombre en bas / à droite
    rect(im, 1, 1, W - 2, 1, hexc("A96B3F"))
    rect(im, 1, 1, 1, H - 2, hexc("955C35"))
    rect(im, 1, H - 2, W - 2, 1, hexc("1A0E08"))
    rect(im, W - 2, 1, 1, H - 2, hexc("1A0E08"))
    # lèvre de l'ouverture : gorge sombre puis filet d'or biseauté
    X, Y, Wo, Ho = B - 2, B - 2, PW + 4, PH + 4
    rect(im, X, Y, Wo, 1, WOOD_DARK); rect(im, X, Y + Ho - 1, Wo, 1, WOOD_DARK)
    rect(im, X, Y, 1, Ho, WOOD_DARK); rect(im, X + Wo - 1, Y, 1, Ho, WOOD_DARK)
    X, Y, Wo, Ho = B - 1, B - 1, PW + 2, PH + 2
    rect(im, X, Y, Wo, 1, hexc(R_OR[3])); rect(im, X, Y, 1, Ho, hexc(R_OR[3]))
    rect(im, X, Y + Ho - 1, Wo, 1, hexc(R_OR[5])); rect(im, X + Wo - 1, Y, 1, Ho, hexc(R_OR[5]))
    # l'intérieur est transparent (le papier est une autre texture)
    a = arr(im)
    a[B:B + PH, B:B + PW] = 0
    im = Image.fromarray(a, "RGBA")
    # ferrures : bandes en travers près des angles (dans les bandes de coupe), grandes équerres d'angle
    for x0 in (36, W - 36 - 8):
        bande_fer(im, x0, 1, 8, B - 1)
        bande_fer(im, x0, H - B, 8, B - 1)
    for y0 in (36, H - 36 - 8):
        bande_fer(im, 1, y0, B - 1, 8)
        bande_fer(im, W - B, y0, B - 1, 8)
    kit.kanagu(im, 0, 0, W, H, "grand", deborde=0)
    S.sauver("cadre.png", im, (W + 4, H + 4))


# ═════════════════════════════════════ papier ═════════════════════════════════════
def washi():
    im = nouveau(PW, PH)
    kit.washi(im, 0, 0, PW, PH, R_WASHI_PAPIER, seed=7, bords="nets", ombre=False, taches=False)
    nav = nouveau(NAVB - 1, PH)
    kit.washi(nav, 0, 0, NAVB - 1, PH, R_WASHI_NAV, seed=8, bords="nets", ombre=False, taches=False)
    a = arr(im)
    a[:, :NAVB - 1] = arr(nav)
    im = Image.fromarray(a, "RGBA")
    # filet de la navigation : creux + arête claire
    rect(im, NAVB - 1, 0, 1, PH, hexc("8A7050"))
    rect(im, NAVB, 0, 1, PH, hexc("F3E6C8"))
    im = ombre_bande(im, 0, 0, NAVB - 1, PH, "d", [0.90, 0.95])
    # poutre laquée rouge + filet d'or + ombre dessous
    fa.lq(im, 0, 0, PW, 5, "rouge", seed=12)
    rect(im, 0, 0, PW, 1, hexc("CC3A4C"))
    rect(im, 0, 5, PW, 1, hexc(R_OR[2]))
    im = ombre_bande(im, 0, 6, PW, 7, "h", [0.70, 0.78, 0.84, 0.89, 0.93, 0.96, 0.98])
    im = ombre_bande(im, 0, 6, PW, PH - 6, "g", [0.82, 0.88, 0.93, 0.97])
    im = ombre_bande(im, 0, 6, PW, PH - 6, "d", [0.82, 0.88, 0.93, 0.97])
    S.sauver("washi.png", im, (PW, PH))


# ═════════════════════════════════════ en-tête ═════════════════════════════════════
def entete():
    im = nouveau(320, 96)
    for row, (kj, titre) in enumerate((("番", "POSTE DE GARDE"), ("医", "INFIRMERIE"))):
        px = nouveau(320, 48)
        cx, w, y, h = 160, 300, 8, 28
        x = cx - w // 2
        R = kit.R_CORDE["rouge"]
        for sgn in (-1, 1):
            a = cx + sgn * (w // 2 - 30)
            for k in range(9):
                X = a + sgn * (8 - k)
                rect(px, X, k, 1, 1, hexc(R[3] if k % 2 == 0 else R[2]))
                rect(px, X + 1 if sgn > 0 else X - 1, k, 1, 1, hexc(R[1]))
        fa.panneau(px, x, y, w, h, seed=21 + row, eclats=False)
        for sgn in (-1, 1):
            fa.anneau_or(px, cx + sgn * (w // 2 - 30), y + 1)
            gx = cx + sgn * (w // 2 + 4)
            rect(px, gx - (3 if sgn < 0 else -1), y + 13, 4, 1, hexc("A42230"))
            fa.agemaki(px, gx, y + 16)
            fa.gland3(px, gx, y + 21, 10)
        tw = kit.arcade_largeur(titre, 10)
        tx = cx + 10
        arcade(px, tx, 19, titre, 10, fa.CREME, ancre="c", ombre=True)
        gf.hanko_px(px, tx - tw // 2 - 20, y + 14, kj, taille=18)
        ea.filet_losanges(px, tx, 22, tw // 2 + 46, ecart=tw // 2 + 8)
        coller(im, arr(px), 0, row * 48)
    S.sauver("entete.png", im, (320, 96))


# ═════════════════════════════════════ boutons ═════════════════════════════════════
def bouton(px, x, y, w, h, ton, survol=False, inactif=False):
    rect(px, x, y + h, w, 1, (6, 3, 4, 110))
    rect(px, x, y, w, h, hexc("120A08"))
    if inactif:
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, ["4A4038", "554A40", "5E5248", "675A4E", "706254"], seed=w, intensite=0.4)
        c1, c2 = hexc("8A7A66"), hexc("5A4E42")
    else:
        rampe = {"noir": "noir", "rouge": "rouge", "vert": R_LAQUE_VERTE}[ton]
        fa.lq(px, x + 1, y + 1, w - 2, h - 2, rampe, seed=w + (7 if survol else 0), intensite=1.8 if survol else 1.1)
        c1, c2 = (hexc(R_OR[7]), hexc(R_OR[4])) if survol else (fa.BISEAU_CLAIR, fa.BISEAU_SOMBRE)
    rect(px, x + 1, y, w - 2, 1, c1); rect(px, x, y + 1, 1, h - 2, c1)
    rect(px, x + 1, y + h - 1, w - 2, 1, c2); rect(px, x + w - 1, y + 1, 1, h - 2, c2)
    if not inactif:
        for (a, b) in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
            rect(px, a, b, 1, 1, hexc(R_OR[6] if survol else R_OR[5]))


def boutons():
    im = nouveau(200, 13 * 7)
    specs = [("noir", False, False), ("noir", True, False), ("rouge", False, False), ("rouge", True, False),
             ("vert", False, False), ("vert", True, False), ("noir", False, True)]
    for r, (ton, sv, off) in enumerate(specs):
        bouton(im, 0, 13 * r, 200, 12, ton, sv, off)
    S.sauver("boutons.png", im, (200, 91))


# ═════════════════════════════════════ fuda (plaquettes de hinoki) ═════════════════════════════════════
def plaquette_bois(im, x, y, w, h, survol, seed):
    """Plaquette de hinoki contour compris (w × h), coins gauches biseautés de 5, ombre (3, 3)."""
    m = np.ones((h, w), bool)
    for k in range(5):
        m[k, :5 - k] = False
        m[h - 1 - k, :5 - k] = False
    lay = nouveau(w, h)
    rampe = R_HINOKI if not survol else R_HINOKI[1:] + ["F8E8CC"]
    kit.bois(lay, 0, 0, w, h, rampe, sens="h", seed=seed, noeuds=1, biseau=False)
    a = arr(lay)
    a[~m] = 0
    # contour + biseau
    up = np.zeros_like(m); up[1:, :] = m[:-1, :]
    dn = np.zeros_like(m); dn[:-1, :] = m[1:, :]
    lf = np.zeros_like(m); lf[:, 1:] = m[:, :-1]
    rt = np.zeros_like(m); rt[:, :-1] = m[:, 1:]
    bord = m & ~(up & dn & lf & rt)
    a[bord] = WOOD_DARK
    inner = m & ~bord
    up2 = np.zeros_like(inner); up2[1:, :] = inner[:-1, :]
    lf2 = np.zeros_like(inner); lf2[:, 1:] = inner[:, :-1]
    dn2 = np.zeros_like(inner); dn2[:-1, :] = inner[1:, :]
    rt2 = np.zeros_like(inner); rt2[:, :-1] = inner[:, 1:]
    a[inner & ~up2] = hexc("FCEFD6")
    a[inner & ~lf2 & up2] = hexc("F6E6C8")
    a[inner & ~dn2] = hexc("B8946A")
    a[inner & ~rt2 & dn2] = hexc("C4A076")
    if survol:
        a[bord] = hexc(R_OR[3])
    kit.ombre_portee(im, x, y, w, h, dx=3, dy=3, force=0.42 if not survol else 0.55, rayon=1, masque=m)
    coller(im, a, x, y)


def fuda():
    im = nouveau(560, 176)
    for r in range(2):
        plaquette_bois(im, 0, 88 * r, 557, 85, r == 1, seed=31)
    S.sauver("fuda.png", im, (560, 176))


# ═════════════════════════════════════ cartes d'infraction, panneaux, tanzaku ═════════════════════════════════════
def carte_washi(im, x, y, w, h, survol=False, seed=0, ton="creme"):
    kit.ombre_portee(im, x, y, w, h, dx=2, dy=2, force=0.45, rayon=0)
    rect(im, x, y, w, h, WOOD_DARK)
    kit.washi(im, x + 1, y + 1, w - 2, h - 2, ton, seed=seed, bords="nets", ombre=False, taches=False)
    if survol:
        a = arr(im)
        reg = a[y + 1:y + h - 1, x + 1:x + w - 1].astype(float)
        reg[..., :3] = reg[..., :3] * 0.85 + 255 * 0.15
        a[y + 1:y + h - 1, x + 1:x + w - 1] = reg.astype(np.uint8)
        coller(im, a[y:y + h, x:x + w], x, y)
        c = hexc(R_OR[5])
        rect(im, x, y, w, 1, c); rect(im, x, y + h - 1, w, 1, c); rect(im, x, y, 1, h, c); rect(im, x + w - 1, y, 1, h, c)
    rect(im, x + 1, y + 1, w - 2, 1, hexc("FFF8E8"))
    rect(im, x + 1, y + h - 2, w - 2, 1, hexc("B6A07A"))


def cartes():
    im = nouveau(560, 124)
    for r in range(2):
        carte_washi(im, 0, 62 * r, 558, 60, r == 1, seed=41)
    S.sauver("carte.png", im, (560, 124))


def panneau():
    im = nouveau(304, 224)
    x, y, w, h = 0, 0, 300, 220
    kit.ombre_portee(im, x, y, w, h, dx=2, dy=2, force=0.5, rayon=1)
    rect(im, x, y, w, h, WOOD_DARK)
    kit.bois(im, x + 1, y + 1, w - 2, h - 2, "clair", sens="h", seed=51, noeuds=0)
    kit.washi(im, x + 3, y + 3, w - 6, h - 6, "creme", seed=52, bords="nets", ombre=False, taches=False)
    rect(im, x + 3, y + 3, w - 6, 1, hexc("B6A07A"))
    rect(im, x + 3, y + 3, 1, h - 6, hexc("C9B48E"))
    fa.kanagus_mini(im, x + 1, y + 1, w - 2, h - 2)
    S.sauver("panneau.png", im, (304, 224))


def tanzaku():
    im = nouveau(134, 304)
    x, y, w, h = 0, 0, 130, 300
    kit.ombre_portee(im, x, y, w, h, dx=3, dy=3, force=0.45, rayon=1)
    rect(im, x, y, w, h, hexc("6A5434"))
    kit.washi(im, x + 1, y + 1, w - 2, h - 2, R_WASHI_TANZAKU, seed=61, bords="nets", ombre=False, taches=False)
    # feuilles d'or (kinpaku) éparses vers le haut
    rs = np.random.RandomState(62)
    for _ in range(22):
        fx, fy = rs.randint(3, w - 4), int(abs(rs.normal(0, 9))) + 5
        if fy < 30:
            c = R_OR[rs.choice([6, 7])]
            rect(im, fx, fy, 1 + rs.randint(0, 2), 1, hexc(c))
    rect(im, x + 1, y + 1, w - 2, 1, hexc("FFFBF0"))
    rect(im, x + 1, y + h - 2, w - 2, 1, hexc("B6A07A"))
    S.sauver("tanzaku.png", im, (134, 304))


def carte_cadre():
    W, H = 466, 506
    im = nouveau(W + 4, H + 4)
    kit.ombre_portee(im, 0, 0, W, H, dx=2, dy=3, force=0.5, rayon=1)
    rect(im, 0, 0, W, H, WOOD_DARK)
    kit.bois(im, 1, 1, W - 2, 3, "sombre", sens="h", seed=71, noeuds=0)
    kit.bois(im, 1, H - 4, W - 2, 3, "sombre", sens="h", seed=72, noeuds=0)
    kit.bois(im, 1, 4, 3, H - 8, "sombre", sens="v", seed=73, noeuds=0)
    kit.bois(im, W - 4, 4, 3, H - 8, "sombre", sens="v", seed=74, noeuds=0)
    X, Y, Wo, Ho = 4, 4, W - 8, H - 8
    rect(im, X, Y, Wo, 1, hexc(R_OR[3])); rect(im, X, Y, 1, Ho, hexc(R_OR[3]))
    rect(im, X, Y + Ho - 1, Wo, 1, hexc(R_OR[5])); rect(im, X + Wo - 1, Y, 1, Ho, hexc(R_OR[5]))
    a = arr(im)
    a[5:H - 5, 5:W - 5] = 0
    im = Image.fromarray(a, "RGBA")
    fa.kanagus(im, 0, 0, W, H, deborde=0)
    S.sauver("carte_cadre.png", im, (W + 4, H + 4))


def sceau():
    im = nouveau(64, 64)
    lay = nouveau(64, 64)
    kit.hanko(lay, 32, 32, taille=42, texte="罰", forme="carre", negatif=True, seed=3, usure=0.14)
    lay = lay.rotate(12, resample=Image.NEAREST, center=(32, 32))
    im.alpha_composite(lay)
    S.sauver("sceau.png", im, (64, 64))


# ═════════════════════════════════════ pièces ═════════════════════════════════════
def pieces():
    im = nouveau(680, 232)
    # navigation : entrée active (laque rouge) et survolée (washi éclairci)
    rect(im, 0, 0, 90, 13, hexc("2A070D"))
    fa.lq(im, 1, 1, 88, 11, "rouge", seed=81)
    rect(im, 1, 1, 88, 1, hexc("CC3A4C"))
    rect(im, 1, 11, 88, 1, hexc("4E0D17"))
    rect(im, 0, 2, 2, 9, hexc(R_OR[5])); rect(im, 0, 2, 1, 9, hexc(R_OR[7]))
    survol = nouveau(90, 13)
    kit.washi(survol, 0, 0, 90, 13, "creme", seed=82, bords="nets", ombre=False, taches=False, fibres=False)
    a = arr(survol); a[..., 3] = 150
    coller(im, a, 0, 13)
    rect(im, 0, 15, 1, 9, hexc(R_OR[4]))
    # pastilles compteur
    for i, (lo, hi) in enumerate((("rouge", None), ("vive", None))):
        X = 100 + 24 * i
        rect(im, X, 0, 24, 11, hexc("2A070D"))
        if i == 0:
            fa.lq(im, X + 1, 1, 22, 9, "rouge", seed=83)
        else:
            fa.lq(im, X + 1, 1, 22, 9, ["A0182B", "B51F33", "CC3A4C", "E04050", "F05A64", "FF7A7A"], seed=84)
        rect(im, X + 1, 1, 22, 1, hexc(R_OR[5] if i == 0 else "FFD0C0"))
    # sceau vierge 11×11 (groupes)
    gf.hanko_px(im, 105, 17, None, taille=11)
    # trait de pinceau vermillon : attaque ronde de 3, corps de 2, queue sèche d'1 (les 40 derniers)
    R = ["6E1511", "8E2018", "AE2C21", "C23A2C"]
    rs = np.random.RandomState(85)
    for x in range(240):
        if x < 5:
            col = [(1, R[1]), (2, R[2]), (3, R[2])] if x in (1, 2, 3) else [(2, R[1])]
        elif x < 200:
            col = [(1, R[1]), (2, R[2])]
        else:
            t = (x - 200) / 40
            if rs.rand() < t * 0.9:
                continue
            col = [(2 if t > 0.5 else 1, R[2] if t > 0.5 else R[1])] + ([(2, R[2])] if t < 0.3 else [])
        for (dy, c) in col:
            rect(im, x, 26 + dy, 1, 1, hexc(c))
    # ficelle de fuda : trou percé + ficelle rouge qui sort à gauche
    rect(im, 9, 34, 4, 4, WOOD_DARK); rect(im, 10, 35, 2, 2, hexc("4A2E1A"))
    fa.corde_h(im, 0, 9, 35, "rouge")
    rect(im, 0, 33, 2, 6, hexc("A42230")); rect(im, 0, 33, 1, 6, hexc("D04A4E"))
    # losange or
    for (dx, dy, c) in ((2, 0, 7), (1, 1, 6), (2, 1, 6), (3, 1, 5), (0, 2, 6), (1, 2, 5), (2, 2, 7), (3, 2, 4), (4, 2, 3),
                        (1, 3, 4), (2, 3, 4), (3, 3, 3), (2, 4, 2)):
        rect(im, 20 + dx, 32 + dy, 1, 1, hexc(R_OR[c]))
    # planche « Fermer » : bois + embouts d'or (repos, survol)
    for i in range(2):
        X = 130 * i
        rect(im, X + 4, 40, 116, 17, WOOD_DARK)
        kit.bois(im, X + 5, 41, 114, 15, "moyen" if i == 0 else "clair", sens="h", seed=86, noeuds=1)
        if i == 1:
            rect(im, X + 10, 54, 104, 1, hexc(R_OR[5]))
        for ex in (X, X + 120):
            rect(im, ex, 43, 4, 11, hexc(R_OR[2]))
            rect(im, ex, 44, 4, 9, hexc(R_OR[5]))
            rect(im, ex, 44, 4, 1, hexc(R_OR[7]))
            rect(im, ex, 52, 4, 1, hexc(R_OR[3]))
            rivet(im, ex + 1, 47)
    # badge de grade (laque noire)
    rect(im, 0, 58, 160, 14, hexc("0A0608"))
    fa.lq(im, 1, 59, 158, 12, "noir", seed=87, intensite=1.3)
    fa.liseret(im, 0, 58, 160, 14)
    # sceaux de score
    for i, R in enumerate(("rouge", ["3A2008", "4A2A0A", "5E360C", "6A4508", "7E540C", "8A5A0C", "A06E14", "B8841E"])):
        Y = 72 + 13 * i
        rect(im, 0, Y, 140, 13, hexc("120A08"))
        fa.lq(im, 1, Y + 1, 138, 11, R, seed=88 + i, intensite=1.2)
        rect(im, 2, Y + 2, 136, 1, hexc(R_OR[4])); rect(im, 2, Y + 10, 136, 1, hexc(R_OR[3]))
        rect(im, 2, Y + 2, 1, 9, hexc(R_OR[4])); rect(im, 137, Y + 2, 1, 9, hexc(R_OR[3]))
    # bande de toast : washi + extrémité laquée + sceau d'or
    rect(im, 0, 98, 440, 16, WOOD_DARK)
    kit.washi(im, 1, 99, 438, 14, "creme", seed=89, bords="nets", ombre=False, taches=False)
    rect(im, 1, 99, 10, 14, hexc("7A1322"))
    fa.lq(im, 1, 99, 10, 14, "rouge", seed=90)
    rect(im, 4, 103, 4, 6, hexc(R_OR[5])); rect(im, 4, 103, 4, 1, hexc(R_OR[7]))
    rect(im, 11, 99, 1, 14, hexc(R_OR[3]))
    # champ de saisie
    rect(im, 0, 114, 600, 15, WOOD_DARK)
    kit.washi(im, 1, 115, 598, 13, "blanc", seed=91, bords="nets", ombre=False, taches=False, fibres=False)
    rect(im, 1, 115, 598, 1, hexc("A69E92"))
    rect(im, 1, 115, 1, 13, hexc("C2BAAE"))
    # flèches 16×16
    for i, (sens, etat) in enumerate([(-1, "on"), (-1, "sv"), (-1, "off"), (1, "on"), (1, "sv"), (1, "off")]):
        X, Y = 16 * i, 130
        rect(im, X, Y, 16, 16, WOOD_DARK if etat != "off" else hexc("5A4838"))
        if etat == "off":
            fa.lq(im, X + 1, Y + 1, 14, 14, ["5A4A44", "62524A", "6A5A50", "726256"], seed=92, intensite=0.3)
            fc = hexc("9A8A7A")
        else:
            fa.lq(im, X + 1, Y + 1, 14, 14, "rouge", seed=93 + i, intensite=1.8 if etat == "sv" else 1.1)
            fc = hexc(R_OR[7] if etat == "sv" else R_OR[6])
            fa.liseret(im, X, Y, 16, 16)
        cx = X + 8
        for k in range(4):                       # triangle plein, pointe vers le sens
            xx = (cx + 1 - k) if sens > 0 else (cx - 2 + k)
            rect(im, xx, Y + 7 - k, 1, 2 * k + 2, fc)
    # perles
    for i, on in enumerate((False, True)):
        X, Y = 100 + 7 * i, 130
        rect(im, X, Y, 6, 6, WOOD_DARK)
        if on:
            rect(im, X + 1, Y + 1, 4, 4, hexc(R_OR[5]))
            rect(im, X + 1, Y + 1, 3, 1, hexc(R_OR[8])); rect(im, X + 1, Y + 2, 1, 2, hexc(R_OR[7]))
            rect(im, X + 4, Y + 2, 1, 3, hexc(R_OR[3]))
        else:
            rect(im, X + 1, Y + 1, 4, 4, hexc("8A6A45"))
            rect(im, X + 2, Y + 2, 2, 2, hexc("E2D2AE"))
    # drapeaux de preuve 13×13
    for i, on in enumerate((False, True)):
        X, Y = 114 + 14 * i, 130
        rect(im, X, Y, 13, 13, WOOD_DARK)
        if on:
            fa.lq(im, X + 1, Y + 1, 11, 11, "rouge", seed=95)
        else:
            rect(im, X + 1, Y + 1, 11, 11, hexc("EFE3C6"))
            rect(im, X + 1, Y + 1, 11, 1, hexc("FFF8E8"))
        mat = hexc("F5E9D0") if on else hexc("A0182B")
        rect(im, X + 4, Y + 3, 1, 8, mat)
        rect(im, X + 5, Y + 3, 4, 3, mat)
        rect(im, X + 5, Y + 6, 2, 1, mat)
    # coupelle d'encens
    rect(im, 144, 131, 12, 1, hexc(R_OR[4]))
    rect(im, 145, 132, 10, 2, hexc("3A2C14"))
    rect(im, 145, 132, 10, 1, hexc("5A4626"))
    rect(im, 147, 134, 6, 1, hexc("2A1E0C"))
    # rouleau jiku : bâton de bois sombre, embouts d'or
    rect(im, 0, 150, 680, 9, (0, 0, 0, 0))
    rect(im, 9, 152, 662, 5, WOOD_DARK)
    kit.bois(im, 9, 153, 662, 3, "sombre", sens="h", seed=96, noeuds=0, biseau=False)
    rect(im, 9, 153, 662, 1, hexc("6A4528"))
    for ex in (0, 671):
        rect(im, ex, 150, 9, 9, hexc(R_OR[2]))
        rect(im, ex + 1, 151, 7, 7, hexc(R_OR[5]))
        rect(im, ex + 1, 151, 7, 1, hexc(R_OR[7])); rect(im, ex + 1, 151, 1, 7, hexc(R_OR[6]))
        rect(im, ex + 1, 157, 7, 1, hexc(R_OR[3]))
        rect(im, ex + 3, 153, 3, 3, hexc(R_OR[4])); rect(im, ex + 4, 154, 1, 1, hexc(R_OR[8]))
    # corde de paille torsadée (shimenawa) 680×6
    P = ["5E4A26", "7E6634", "A08848", "C2AA62", "D8C27C"]
    for x in range(680):
        for y in range(6):
            ph = (x + y * 2) % 8
            c = 4 if ph < 2 else (3 if ph < 4 else (2 if ph < 6 else 1))
            if y == 0 or y == 5:
                c = max(0, c - 2)
            rect(im, x, 160 + y, 1, 1, hexc(P[c]))
    # réglette : rainure et poignée
    rect(im, 0, 168, 300, 4, WOOD_DARK)
    rect(im, 1, 169, 298, 2, hexc("8A6A45"))
    rect(im, 1, 169, 298, 1, hexc("6A4E30"))
    rect(im, 0, 174, 7, 9, WOOD_DARK)
    rect(im, 1, 175, 5, 7, hexc(R_OR[5]))
    rect(im, 1, 175, 5, 1, hexc(R_OR[7])); rect(im, 1, 175, 1, 7, hexc(R_OR[6]))
    rect(im, 1, 181, 5, 1, hexc(R_OR[3])); rect(im, 5, 175, 1, 7, hexc(R_OR[3]))
    # filigranes : grand kanji à l'encre délavée (coin bas droit du papier), 番 puis 医
    for i, kj in enumerate("番医"):
        kit.kanji(im, 320 + 64 * i + 32, 168, kj, 60, police="song", couleur="6B5434", ancre="c", alpha=34)
    S.sauver("pieces.png", im, (680, 232))


def main():
    cadre()
    washi()
    entete()
    boutons()
    fuda()
    cartes()
    panneau()
    tanzaku()
    carte_cadre()
    sceau()
    pieces()
    S.bilan()


if __name__ == "__main__":
    main()
