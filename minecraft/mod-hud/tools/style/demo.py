# -*- coding: utf-8 -*-
"""
demo.py — planches de contrôle des outils communs.

    python demo.py            -> demo_kit.png (toutes les primitives du kit)
                                 demo_perso.png (les personnages Reborn, face et 3/4)

Sert aussi d'exemple d'utilisation : chaque bloc montre l'appel correspondant.
"""
import os

from PIL import Image

import kit
import perso
from kit import (PAL, R_OR, STATS, Maquette, anneau_metal, arcade, bande_tissu, bois, bouton_plaque,
                 cordelette, gland, hanko, kanagu, kanji, liseret_or, noeud_agemaki, onglets_pendus,
                 panneau_laque, plaque_suspendue, rect, tampon, washi, hexc)  # noqa: F401

ICI = os.path.dirname(os.path.abspath(__file__))
OR = PAL["or_"]
CREME = PAL["creme"]
DOUX = PAL["creme_doux"]
GRIS = PAL["gris"]


def titre(m, x, y, texte, legende=None):
    """Titre de section : ArcadePix or + filet + légende Inter (nom de la fonction)."""
    px = m.px
    w = arcade(px, x, y, texte, 10, OR)
    rect(px, x, y + 10, w, 1, hexc(R_OR[3]))
    rect(px, x, y + 10, 6, 1, hexc(R_OR[6]))
    if legende:
        m.inter(x + w + 10, y, legende, 7, couleur=GRIS)


def note(m, x, y, texte, ancre="g"):
    m.inter(x, y, texte, 7, couleur=GRIS, ancre=ancre)


def loupe(m, src, dst, k=4, cadre=True):
    """Agrandit (au plus proche) une zone de l'image finale déjà composée : détail pixel."""
    S = kit.ECHELLE_GUI

    def op(im):
        x, y, w, h = [int(v * S) for v in src]
        crop = im.crop((x, y, x + w, y + h)).resize((w * k, h * k), Image.NEAREST)
        dx, dy = int(dst[0] * S), int(dst[1] * S)
        if cadre:
            kit.rect(im, dx - 2, dy - 2, w * k + 4, h * k + 4, hexc(R_OR[2]))
            kit.rect(im, dx - 1, dy - 1, w * k + 2, h * k + 2, hexc(R_OR[5]))
        im.alpha_composite(crop, (dx, dy))
    m.final(op)


# ═════════════════════════════════════════ demo_kit ═════════════════════════════════════════
def demo_kit(chemin):
    m = Maquette(fond=PAL["fond"], taille=(960, 860))
    px = m.px
    # fond : laque très sombre + vignette (on veut juger les primitives, pas un décor)
    kit.laque(px, 0, 0, 960, 860, ton="noir", reflet=False)

    # ── 1. En-tête : plaque suspendue + onglets pendus ─────────────────────────────
    zone = plaque_suspendue(px, 480, 14, 232, 40, attache_y=0)
    arcade(px, 480, 22, "FICHE SHINOBI", 20, CREME, ancre="c", ombre=True)
    m.inter(480, 44.5, "Ren Hyūga  ·  Genin  ·  Konohagakure", 7, couleur=hexc("E6B4A0"), ancre="c")
    onglets_pendus(px, 480, 54, 66, ["REGISTRE", "ATTRIBUTS", "TECHNIQUES", "ETAT"], actif=1, survol=2,
                   kanjis=["録", "灯", "術", "身"])
    titre(m, 20, 16, "EN-TETE", None)
    note(m, 20, 32, "plaque_suspendue(px, cx, y, w, h)")
    note(m, 20, 44, "onglets_pendus(px, cx, y_attache, y, libelles,")
    note(m, 20, 54, "    actif=1, survol=2, kanjis=[...])")
    note(m, 700, 32, "actif : laque rouge, or vif, pend 2 px plus bas")
    note(m, 700, 44, "survol : laque éclaircie, équerres")
    note(m, 700, 56, "repos : or éteint, crème doux")
    loupe(m, (364, 6, 34, 28), (860, 70), k=2)

    # ── 2. Panneaux et matières ────────────────────────────────────────────────
    y0 = 112
    titre(m, 20, y0, "PANNEAUX ET MATIERES")
    # panneau noir avec contenu
    x, y, w, h = 20, y0 + 22, 226, 150
    zx, zy, zw, zh = panneau_laque(m.px, x, y, w, h)
    arcade(m.px, x + 14, y + 13, "REGISTRE", 10, OR)
    rect(m.px, x + 14, y + 23, 24, 1, PAL["rouge"])
    lignes = [("NOM", "Ren Hyūga"), ("CLAN", "Hyūga"), ("VILLAGE", "Konohagakure"), ("GRADE", "Genin")]
    for i, (k, v) in enumerate(lignes):
        yy = y + 34 + i * 18
        arcade(m.px, x + 14, yy, k, 10, GRIS)
        m.inter(x + 80, yy, v, 9, couleur=CREME)
        if i < len(lignes) - 1:
            rect(m.px, x + 14, yy + 12, w - 28, 1, hexc("2A1A1F"))
    m.paragraphe(x + 14, y + 112, "Corps de texte en Inter 9 (18 px), posé après l'agrandissement x2.", w - 28, 7,
                 couleur=DOUX)
    note(m, x, y + h + 8, "panneau_laque(px, x, y, w, h) -> zone utile")
    # panneau rouge + liseret seul
    panneau_laque(m.px, 262, y0 + 22, 118, 62, ton="rouge")
    arcade(m.px, 321, y0 + 46, "LAQUE ROUGE", 10, CREME, ancre="c", ombre=True)
    note(m, 262, y0 + 92, "ton='rouge'")
    liseret_or(m.px, 262, y0 + 108, 118, 26)
    kit.laque(m.px, 263, y0 + 109, 116, 24, reflet=False)
    arcade(m.px, 321, y0 + 117, "LISERE OR", 10, DOUX, ancre="c")
    note(m, 262, y0 + 140, "liseret_or(px, x, y, w, h, ep=1|2)")
    # kanagu seuls + loupe
    kit.laque(m.px, 262, y0 + 152, 40, 34, reflet=False)
    liseret_or(m.px, 262, y0 + 152, 40, 34)
    kanagu(m.px, 262, y0 + 152, 40, 34, "grand", coins=["hg", "bd"])
    kit.laque(m.px, 308, y0 + 152, 36, 34, reflet=False)
    liseret_or(m.px, 308, y0 + 152, 36, 34)
    kanagu(m.px, 308, y0 + 152, 36, 34, "moyen")
    kit.laque(m.px, 350, y0 + 158, 30, 22, reflet=False)
    liseret_or(m.px, 350, y0 + 158, 30, 22)
    kanagu(m.px, 350, y0 + 158, 30, 22, "petit")
    note(m, 262, y0 + 192, "kanagu(px, x, y, w, h, modele)")
    loupe(m, (258, y0 + 148, 22, 22), (397, y0 + 148), k=3)
    # washi avec sceaux et tampon
    wx, wy, ww, wh = 466, y0 + 20, 196, 170
    washi(m.px, wx, wy, ww, wh, "creme", seed=4)
    arcade(m.px, wx + 14, wy + 14, "ORDRE DE MISSION", 10, PAL["encre"])
    rect(m.px, wx + 14, wy + 24, 60, 1, hexc("B83024"))
    m.paragraphe(wx + 14, wy + 34, "Escorter le marchand Tazuna jusqu'au pont du pays des Vagues. Rang C.",
                 ww - 60, 8, couleur=PAL["encre"])
    hanko(m.px, wx + ww - 24, wy + 26, 22, "忍", seed=1)
    hanko(m.px, wx + ww - 24, wy + 58, 26, "木葉", seed=2, usure=0.06)
    tampon(m.px, wx + 62, wy + 112, "APPROUVE", 10, seed=3)
    tampon(m.px, wx + 150, wy + 130, "秘", 14, couleur="encre", cadre="rond", angle=4, seed=5)
    note(m, wx, wy + wh + 6, "washi(px, x, y, w, h, 'creme') · hanko(...) · tampon(...)")
    # autres papiers
    washi(m.px, 680, y0 + 20, 120, 76, "ancien", seed=7)
    arcade(m.px, 692, y0 + 32, "ANCIEN", 10, PAL["encre_douce"])
    washi(m.px, 680, y0 + 104, 120, 76, "kraft", seed=8, bords="nets")
    arcade(m.px, 692, y0 + 116, "KRAFT", 10, hexc("2A1A10"))
    hanko(m.px, 778, y0 + 160, 18, "印", forme="rond", negatif=False, seed=9)
    washi(m.px, 816, y0 + 20, 124, 76, "blanc", seed=11)
    arcade(m.px, 828, y0 + 32, "BLANC", 10, PAL["encre_douce"])
    note(m, 680, y0 + 188, "tons : creme · ancien · kraft · blanc ; bords='dechires'|'nets'")
    # bois
    bois(m.px, 816, y0 + 104, 124, 34, "sombre", planches=2, seed=2)
    bois(m.px, 816, y0 + 142, 60, 40, "moyen", sens="v", seed=5)
    bois(m.px, 880, y0 + 142, 60, 40, "clair", seed=6, noeuds=2)
    note(m, 680, y0 + 200, "bois(px, x, y, w, h, 'sombre'|'moyen'|'clair', sens='h'|'v', planches=n)")

    # ── 3. Tissus, cordes, sceaux ─────────────────────────────────────────────
    y0 = 340
    titre(m, 20, y0, "TISSUS, CORDES, SCEAUX")
    motifs = [("rouge", "seigaiha"), ("indigo", "losanges"), ("noir", "uroko"), ("or", "raye"), ("violet", "points")]
    for i, (c, mo) in enumerate(motifs):
        bande_tissu(m.px, 20, y0 + 22 + i * 20, 170, 14, c, mo)
        note(m, 198, y0 + 25 + i * 20, f"'{c}', '{mo}'")
    note(m, 20, y0 + 126, "bande_tissu(px, x, y, w, h, couleur, motif)")
    # cordes
    cx0 = 300
    for i, c in enumerate(["rouge", "or", "noir", "blanc", "violet"]):
        cordelette(m.px, cx0 + i * 26, y0 + 22, cx0 + i * 26, y0 + 92, c, ep=2)
    cordelette(m.px, 300, y0 + 104, 420, y0 + 104, "rouge", mou=10)
    noeud_agemaki(m.px, 444, y0 + 30, "rouge")
    cordelette(m.px, 444, y0 + 34, 444, y0 + 44, "rouge", ep=1)
    gland(m.px, 444, y0 + 44, 14, "rouge")
    noeud_agemaki(m.px, 470, y0 + 30, "or")
    cordelette(m.px, 470, y0 + 34, 470, y0 + 44, "or", ep=1)
    gland(m.px, 470, y0 + 44, 14, "or")
    for i in range(3):
        anneau_metal(m.px, 444 + i * 12, y0 + 82, 2 if i < 2 else 3, "or" if i != 1 else "fer")
    note(m, 300, y0 + 126, "cordelette(px, x0, y0, x1, y1, couleur, mou) · noeud_agemaki · gland · anneau_metal")
    # sceaux
    sx = 520
    hanko(m.px, sx + 14, y0 + 40, 24, "忍", seed=1)
    hanko(m.px, sx + 46, y0 + 40, 26, "火影", seed=2)
    hanko(m.px, sx + 80, y0 + 40, 28, "木葉隠里", seed=3)
    hanko(m.px, sx + 112, y0 + 40, 24, "印", forme="rond", negatif=False, seed=4)
    hanko(m.px, sx + 140, y0 + 40, 26, "日向", forme="ovale", seed=6)
    note(m, sx, y0 + 60, "hanko(px, cx, cy, taille, texte, forme, negatif)")
    tampon(m.px, sx + 44, y0 + 90, "APPROUVE", 10, seed=3)
    tampon(m.px, sx + 132, y0 + 88, "検", 14, couleur="indigo", cadre="rond", angle=-6, seed=8)
    tampon(m.px, sx + 196, y0 + 90, "VU", 10, couleur="encre", cadre="simple", angle=3, seed=4)
    note(m, sx, y0 + 112, "tampon(px, cx, cy, texte, taille, couleur, cadre, angle)")
    # fond washi pour juger les sceaux sur papier
    washi(m.px, 760, y0 + 18, 180, 104, "creme", seed=21)
    hanko(m.px, 790, y0 + 52, 26, "日向", seed=12)
    hanko(m.px, 830, y0 + 52, 22, "忍", forme="rond", seed=13)
    tampon(m.px, 890, y0 + 52, "VU", 20, seed=14, angle=-8)
    tampon(m.px, 836, y0 + 98, "CONFIDENTIEL", 10, couleur="encre", cadre="simple", angle=2, seed=15)

    # ── 4. Boutons-plaques ───────────────────────────────────────────────────
    y0 = 486
    titre(m, 20, y0, "BOUTONS-PLAQUES")
    bx = 20
    for e in ("normal", "survol", "actif", "inactif"):
        r = bouton_plaque(m.px, bx, y0 + 24, None, 18, "VALIDER", e)
        note(m, bx + r[2] / 2, y0 + 48, e, ancre="c")
        bx += r[2] + 14
    r = bouton_plaque(m.px, 20, y0 + 66, None, 18, "REPARTIR", "actif", kanji_="灯")
    bouton_plaque(m.px, 20 + r[2] + 14, y0 + 66, None, 18, "FERMER", "normal", touche="K")
    note(m, 20, y0 + 92, "bouton_plaque(px, x, y, w|None, h, libelle, etat, kanji_=, touche=)")

    # ── 5. Formes lissées ─────────────────────────────────────────────────────
    y0 = 600
    titre(m, 20, y0, "FORMES LISSEES AU PIXEL REEL")
    m.anneau(62, y0 + 66, 36, 3, hexc(R_OR[3]), alpha=160)
    m.anneau(62, y0 + 66, 36, 3, OR, debut=-90, fin=150)
    m.disque(62, y0 + 66, 6, PAL["rouge"])
    m.anneau(150, y0 + 66, 30, 7, PAL["rouge"], debut=200, fin=520, alpha=230)
    m.trait([(196, y0 + 98), (222, y0 + 40), (258, y0 + 86), (288, y0 + 32), (314, y0 + 94)], 2.2, CREME,
            courbe=True)
    m.halo(400, y0 + 66, 44, "3FB8F5", force=0.55)
    m.braises((350, y0 + 22, 470, y0 + 112), n=18, couleur="E8742C", seed=3)
    note(m, 20, y0 + 116, "m.anneau · m.disque · m.trait(courbe=True) · m.halo · m.braises")
    note(m, 20, y0 + 128, "(couche finale, sur-échantillonnage x4 : jamais d'escalier GUI)")

    # ── 6. Lanternes célestes ───────────────────────────────────────────────
    y0 = 486
    titre(m, 520, y0, "LANTERNES CELESTES")
    note(m, 940, y0 + 1, "signature des stats", ancre="d")
    hauteurs = [0.55, 0.20, 0.85, 0.50, 0.68, 0.38]
    paliers = [2, 0, 4, 2, 3, 1]
    for i in range(6):
        cx = 552 + i * 70
        cy = y0 + 78 + int((1 - hauteurs[i]) * 46)
        allumee = 0.0 if i == 1 else 1.0
        m.lanterne(cx, cy, i, taille=1.1, allumee=allumee, eclat=hauteurs[i], survol=(i == 2))
        # fil de lumière jusqu'au sol (comme drawLanterns)
        px2 = m.px
        for yy in range(cy + 30, y0 + 170, 3):
            rect(px2, cx, yy, 1, 1, hexc(STATS[i][3]), alpha=110 if i != 1 else 40)
        arcade(px2, cx, y0 + 176, STATS[i][1], 10, hexc(STATS[i][3]) if i != 1 else GRIS, ancre="c")
        m.inter(cx, y0 + 190, kit.PALIERS[paliers[i]], 7, couleur=DOUX, ancre="c")
    note(m, 520, y0 + 206, "m.lanterne(cx, cy, stat, taille, allumee, eclat, survol) : halo lissé + corps pixel")
    note(m, 520, y0 + 218, "KENJUTSU éteinte (allumee=0) · NINJUTSU survolée · paliers en mots, jamais de chiffres")

    # ── 7. Typographie ──────────────────────────────────────────────────────
    y0 = 752
    px = m.calque()
    rect(px, 20, y0 - 8, 920, 1, hexc("2A1A1F"))
    titre(m, 20, y0, "TYPOGRAPHIE")
    yy = y0 + 22
    arcade(px, 20, yy, "ARCADEPIX 10 - ECHELLE 1", 10, CREME)
    note(m, 260, yy, "couche pixel : 1 px de fonte = 1 GUI = 2x2 px écran")
    arcade(px, 20, yy + 16, "TITRE X2", 20, OR, ombre=True)
    note(m, 260, yy + 20, "taille=20, ombre du jeu")
    m.arcade_fin(20, yy + 40, "ARCADEPIX ECHELLE 0,5 - AU PIXEL ECRAN", 0.5, DOUX)
    m.arcade_fin(20, yy + 50, "ECHELLE 1,5", 1.5, CREME)
    note(m, 260, yy + 54, "m.arcade_fin(x, y, texte, echelle) : 0,5 / 1,5 au pixel écran")
    note(m, 20, yy + 72, "majuscules sans accents imposées (sans_accents = EscData.arcadeSafe)")
    m.inter(520, yy, "Inter 9 — Byakugan, Chūnin, Jōnin, Hyūga.", 9, couleur=CREME)
    m.inter(520, yy + 14, "Inter 9 gras — Les yeux de ton clan s'éveillent.", 9, gras=True, couleur=OR)
    m.inter(520, yy + 28, "Inter 7 — légendes, aides, raccourcis.", 7, couleur=DOUX)
    note(m, 940, yy + 30, "m.inter(x, y, texte, taille_gui, gras)", ancre="d")
    m.kanji(520, yy + 46, "体剣忍制力気", 16, "unifont", CREME)
    note(m, 584, yy + 48, "unifont 16 = police du jeu")
    kanji(px, 680, yy + 44, "術身名", 12, "gothic", CREME)
    note(m, 724, yy + 48, "gothic 12")
    kanji(px, 780, yy + 42, "火影", 16, "gras", OR)
    kanji(px, 822, yy + 42, "日向", 16, "song", DOUX)
    note(m, 862, yy + 48, "gras · song")
    note(m, 520, yy + 72, "kanji(px, …) sur la couche pixel (x2) · m.kanji(…) au pixel écran")

    m.vignette(0.35)
    img = m.rendre()
    img.save(chemin)
    return img


# ═════════════════════════════════════════ demo_perso ═════════════════════════════════════════
def demo_perso(chemin):
    m = Maquette(fond=PAL["fond"], taille=(960, 600))
    px = m.px
    kit.laque(px, 0, 0, 960, 600, ton="noir", reflet=False)
    plaque_suspendue(px, 480, 10, 300, 34, attache_y=0)
    arcade(px, 480, 20, "PERSONNAGES REBORN", 20, CREME, ancre="c", ombre=True)
    m.inter(480, 54, "Skins composés comme RebornSkins.compose  ·  modèle joueur exact, rendu z-buffer, "
            "2e couche gonflée comme en jeu  ·  posés au pixel écran", 7, couleur=GRIS, ancre="c")

    presets = [("ren_hyuga", "REN HYUGA", "Homme · Complet_Ceremonie · Cheveux_Itachi · iris lavande", "9B6BFF"),
               ("ren_hyuga_f", "REN HYUGA - VARIANTE F", "Femme (carrure Alex) · Complet_Femme1 · Cheveux_Femme",
                "3FB8F5")]
    for k, (nom, titre_, sous, col) in enumerate(presets):
        sk = perso.preset(nom)
        X0 = 16 + k * 474
        panneau_laque(m.px, X0, 70, 454, 516)
        arcade(m.px, X0 + 18, 86, titre_, 10, OR)
        rect(m.px, X0 + 18, 96, 30, 1, PAL["rouge"])
        m.inter(X0 + 18, 102, sous, 7, couleur=DOUX)
        # scène : halo coloré + braises (comme la sélection de personnage)
        m.halo(X0 + 150, 470, 150, col, force=0.18)
        m.halo(X0 + 150, 560, 110, col, force=0.22)
        m.braises((X0 + 20, 140, X0 + 300, 560), n=16, couleur=col, seed=k + 1)
        # photo d'identité (buste) agrafée sur une fiche de washi
        washi(m.px, X0 + 18, 122, 132, 150, "creme", seed=30 + k)
        rect(m.px, X0 + 30, 134, 108, 96, hexc("3A3438"))
        rect(m.px, X0 + 31, 135, 106, 94, hexc("4E474B"))
        b = perso.rendu_buste(sk, 10, lacet=12 if k == 0 else -12, bas=6, ombre_sol=False)
        m.final(lambda im, b=b, x=X0: kit.coller(im, b, int((x + 84) * 2 - b.info["tete"][0]), (229 * 2) - b.height))
        rect(m.calque(), X0 + 74, 130, 20, 3, hexc("9A9692"))
        arcade(m.px, X0 + 30, 240, "FICHE 0412", 10, PAL["encre"])
        hanko(m.px, X0 + 128, 250, 18, "日向" if k == 0 else "忍", seed=40 + k)
        note(m, X0 + 84, 278, "rendu_buste(skin, 10)", ancre="c")
        # grand : échelle 14 (portrait de fiche)
        m.perso(perso.rendu_face(sk, 14), (X0 + 86, 548))
        m.perso(perso.rendu_trois_quarts(sk, 14, pose=perso.POSE_REPOS), (X0 + 222, 548))
        note(m, X0 + 86, 558, "rendu_face(skin, 14)", ancre="c")
        note(m, X0 + 222, 558, "rendu_trois_quarts(skin, 14)", ancre="c")
        # colonne droite : skin brut + petites échelles
        cx = X0 + 376
        rect(m.px, cx - 34, 122, 68, 68, hexc("2A1A1F"))
        for yy in range(0, 64, 8):
            for xx in range(0, 64, 8):
                if (xx // 8 + yy // 8) % 2:
                    rect(m.px, cx - 32 + xx, 124 + yy, 8, 8, hexc("241418"))
        m.final(lambda im, s=sk, x=cx: im.alpha_composite(s.resize((128, 128), Image.NEAREST), ((x - 32) * 2, 124 * 2)))
        note(m, cx, 196, "compose(...) 64x64", ancre="c")
        m.perso(perso.rendu_face(sk, 6), (cx - 26, 330))
        m.perso(perso.rendu_trois_quarts(sk, 6, cote="gauche"), (cx + 28, 330))
        note(m, cx, 340, "échelle 6 · face / 3/4 gauche", ancre="c")
        m.perso(perso.rendu(sk, 4, 180, 8), (cx - 38, 450))
        m.perso(perso.rendu(sk, 4, 90, 6), (cx, 450))
        m.perso(perso.rendu_trois_quarts(sk, 4, pose=perso.POSE_GARDE), (cx + 38, 450))
        note(m, cx, 460, "échelle 4 · dos · profil · garde", ancre="c")
        m.perso(perso.rendu_face(sk, 2), (cx - 20, 540))
        m.perso(perso.rendu_trois_quarts(sk, 2), (cx + 20, 540))
        note(m, cx, 550, "échelle 2 (vignette)", ancre="c")
        note(m, X0 + 18, 572, "perso.preset('%s')" % nom)
    m.vignette(0.3)
    img = m.rendre()
    img.save(chemin)
    return img


if __name__ == "__main__":
    demo_kit(os.path.join(ICI, "demo_kit.png"))
    demo_perso(os.path.join(ICI, "demo_perso.png"))
    print("demo_kit.png et demo_perso.png écrits dans", ICI)
