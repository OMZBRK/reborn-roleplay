# -*- coding: utf-8 -*-
"""
centre_compare.py : texte au centre du lot 2 (étape 2c) et narration du staff (étape 2d), captures du jeu contre le
moteur des maquettes, au pixel près.

    python centre_compare.py [dossier run] [--serie centre|narration] [--ref <_texte_raster.py>]
                             [--maquettes <dossier 17_lot2_ketto>] [--sortie <dossier>] [--planches <dossier>]

--serie narration (REBORN_FEATURE_PREVIEW=narration) : même mesure sur <run>/narration_banc.txt et les captures
narration_*.png (messages reborn:narration simulés, passés au vrai receveur), dans l'allure « narration » plein écran
(décision du 02/11, recit/Gabarit : provisoire, puis la variante V1 de la planche d1) ; rapport narration_compare.txt,
planche planche_narration_scenes.png (écrans entiers réduits ÷ 3, captures à 1:1 copiées à côté). Les lignes « note »
du journal (vérifications OK / ÉCART du banc) sont recopiées en tête du rapport ; un cas marqué « sans_reference »
(mot plus large que la ligne : le moteur Java le coupe, celui des maquettes non) est listé sans être comparé.

Le banc REBORN_FEATURE_PREVIEW=centre (audit/FeaturePreview) prend chaque état deux fois, monde gelé et horloge du
texte figée : <cas>_sans.png (couche non posée, RecitHud.debugMasque : tout le reste de l'image est identique) puis
<cas>.png, et journalise la demande posée dans <run>/centre_banc.txt (une ligne JSON : fenêtre, gabarit et allure —
préréglage, rayon du contour, ombre douce, approche, coupes figées, voile —, f, capitales, largeur, interligne, ancre,
opacité, texte balisé de la page, couche Java). Ce script rend la même demande avec le moteur de référence des
maquettes (_texte_raster.rendre, police du récit, préréglage du journal ; ombre douce : la recette de la planche d1,
_maquettes_narration_d.rasteriser_narration ; ancien journal sans allure : préréglage RÉCIT), la pose sur la capture
nue avec l'opacité du cas (le fondu passe par l'alpha des sommets : toute la couche d'un bloc ; voile du gabarit
dessous), puis compare au jeu dans la boîte de la couche élargie de 24 px :

- position : la couche Java (journal) et la couche Python au même pixel et de même taille ;
- cœur (pixels où la couche est pleine : le fond n'y compte pas) : écart maximal < 8/255 ;
- dans l'empreinte (pixels que le texte modifie, élargis d'1 px) : écart RVB moyen < 1/255 au-delà de la dérive du
  fond (le maximal est imprimé : sur les bords à demi transparents, un fond qui bouge s'y voit) ;
- hors de l'empreinte, dans la boîte : sur un fond fixe, le jeu n'y dessine rien de plus (max < 16/255) ; si le fond
  bouge entre les deux captures (avatar au repos, grain du filtre du KO : dérive ≥ 0,02/255), c'est indicatif.

Planches <sortie>/centre_compare_<cas>.png : jeu | moteur des maquettes | écart ×8, zoom ×2 au plus proche ; rapport
<sortie>/centre_compare.txt. Avec --planches <dossier> : les captures finales (centre_*.png, sans les fonds nus) y sont
copiées, avec les planches « maquette | jeu » (a1, a2, a3, a4, a6, a8). Sortie 1 si un cas dépasse la cible.
Python 3.14, Pillow, numpy, fontTools (ou REBORN_PYLIB).
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
RUN = os.path.normpath(os.path.join(ICI, "..", "run"))
MAQUETTES = os.environ.get("REBORN_MAQUETTES_LOT2") or \
    r"C:\Users\omarb\Desktop\Reborn - Gestion\Maquettes\gameplan\17_lot2_ketto"
POLICE = "alegreya_sans_bi"
# police du journal (TexteRaster.Police) -> clé du moteur des maquettes
POLICES = {"RECIT": "alegreya_sans_bi", "PHILOSOPHER": "philosopher_b", "INTER_GRAS": "inter_bold", "INTER_MEDIUM": "inter_medium"}
# Philosopher Bold (narration du staff, choix du 02/11) : absente du registre des maquettes, ajoutée ici (même TTF que
# le mod, _sources/fonts/Philosopher/Philosopher-Bold.ttf, OFL)
PHILOSOPHER_B = dict(f=("Philosopher", "Philosopher-Bold.ttf"), nom="Philosopher", graisse="Bold", poids=700,
                     style="romain", licence="OFL")
CIBLE_MOY, CIBLE_MAX, CIBLE_COEUR = 1.0, 16.0, 8.0   # en 1/255
MARGE = 24
INTER = os.path.normpath(os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "font", "inter-bold.ttf"))
TUILE = (680, 423, 1240, 743)          # recadrage 560 × 320 des maquettes a1 / a4 (centre du bloc à 54 %)
A6_BOITE = (560, 280, 1360, 880)       # recadrage 800 × 600 de la maquette a6


def charger(nom, chemin):
    if not os.path.isfile(chemin):
        sys.exit("introuvable : %s" % chemin)
    dossier = os.path.dirname(os.path.abspath(chemin))
    sys.dont_write_bytecode = True   # pas de __pycache__ dans le dossier des maquettes
    if dossier not in sys.path:
        sys.path.insert(0, dossier)
    spec = importlib.util.spec_from_file_location(nom, chemin)
    mod = importlib.util.module_from_spec(spec)
    sys.modules[nom] = mod
    spec.loader.exec_module(mod)
    return mod


def avec_alpha(couche, a):
    if a >= 0.999:
        return couche
    arr = np.asarray(couche).copy()
    arr[..., 3] = np.clip(np.floor(arr[..., 3].astype(np.float32) * a + 0.5), 0, 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def elargir(m):
    e = m.copy()
    e[1:, :] |= m[:-1, :]
    e[:-1, :] |= m[1:, :]
    e[:, 1:] |= m[:, :-1]
    e[:, :-1] |= m[:, 1:]
    return e


def police_legende(taille=15):
    try:
        return ImageFont.truetype(INTER, taille)
    except OSError:
        return ImageFont.load_default()


def planche(jeu, ref, chemin, z=2):
    h, w = jeu.shape[:2]
    out = Image.new("RGB", (3 * w * z + 16, h * z), (13, 10, 11))
    for i, X in enumerate((jeu, ref)):
        out.paste(Image.fromarray(X.astype(np.uint8), "RGB").resize((w * z, h * z), Image.NEAREST), (i * (w * z + 8), 0))
    d = np.clip(np.abs(jeu - ref).max(axis=2) * 8, 0, 255).astype(np.uint8)
    e = np.zeros((h, w, 3), np.uint8)
    e[..., 0] = d
    out.paste(Image.fromarray(e, "RGB").resize((w * z, h * z), Image.NEAREST), (2 * (w * z + 8), 0))
    out.save(chemin)


def rasteriser_ombre_couleurs(tr, nd, mise, r, a_contour, ombre, gamma=0.85):
    """La recette rasteriser_narration de la planche d1 (ombre douce, contour, remplissage), mais avec un remplissage par
    clé de couleur comme tr.composer_rgba : la planche ne montre que du blanc, la narration garde ses mots de couleur
    (V2 du 02/11 : blanc par défaut, couleurs permises). Mêmes couches, même ordre, mêmes arrondis."""
    import math
    police, em = mise.police, mise.em
    dx, dy, sig, a_o = ombre
    ext = r + abs(dx) + abs(dy) + 3.0 * sig + 4
    xs = [l[3] for l in mise.lignes] + [l[3] + l[2] for l in mise.lignes]
    bs = [l[4] for l in mise.lignes]
    pad = em * 1.2 + ext
    X0 = math.floor(min(xs) - pad)
    Y0 = math.floor(min(bs) - em * 1.3 - ext)
    W = math.ceil(max(xs) + pad) - X0
    H = math.ceil(max(bs) + em * 0.7 + ext) - Y0
    f16 = police.pil(em * tr._SS)
    sw16 = int(round(tr._SS * r))
    remp = {}
    T = np.zeros((H, W), np.float32)
    for a, b, w, x0, base in mise.lignes:
        for j in range(a, b):
            it = mise.items[j]
            if it.dessin is None:
                continue
            x = x0 + mise.pos[j] - mise.pos[a]
            X, Y = x - X0, float(base - Y0)
            dess = [(it.dessin, X, Y)]
            if it.macron is not None:
                dess.append(tr._macron(police, em, it, X, Y))
            cible = remp.setdefault(it.cle, np.zeros((H, W), np.float32))
            for ch, gx, gy in dess:
                arr, ox, oy = tr._glyphe1(f16, ch, gx, gy, 0, 0.0)
                tr._cumuler(cible, arr, ox, oy, "somme")
                if sw16 > 0:
                    arr, ox, oy = tr._glyphe1(f16, ch, gx, gy, sw16, 0.0)
                    tr._cumuler(T, arr, ox, oy, "max")
    remplis = {c: np.clip(v, 0.0, 1.0) for c, v in remp.items()}
    F = np.zeros((H, W), np.float32)
    for v in remplis.values():
        F = np.maximum(F, v)
    S = np.maximum(np.clip(T, 0.0, 1.0), F)
    O = nd._flouter(nd._decaler(S, dx, dy), sig)
    A = np.zeros((H, W), np.float32)
    C = np.zeros((H, W, 3), np.float32)
    encre = np.array(tr.ENCRE, np.float32) / 255.0

    def sur(alpha, rgb):
        nonlocal A, C
        C = rgb[None, None, :] * alpha[..., None] + C * (1.0 - alpha[..., None])
        A = alpha + A * (1.0 - alpha)

    sur(O * a_o, encre)
    sur(S * a_contour, encre)
    cols = dict(tr.COULEURS)
    for cle, Fc in remplis.items():
        rgb = cols.get(cle, cols.get("", (255, 255, 255)))
        sur(np.power(Fc, gamma), np.array(rgb, np.float32) / 255.0)
    out = np.zeros((H, W, 4), np.float32)
    ok = A > 1e-6
    out[..., :3] = np.where(ok[..., None], C / np.maximum(A, 1e-6)[..., None], 0.0)
    out[..., 3] = A
    rgba = np.clip(np.floor(out * 255.0 + 0.5), 0, 255).astype(np.uint8)
    ys_, xs_ = np.nonzero(rgba[..., 3] > 0)
    y0, y1, x0, x1 = ys_.min(), ys_.max() + 1, xs_.min(), xs_.max() + 1
    return Image.fromarray(rgba[y0:y1, x0:x1], "RGBA"), X0 + int(x0), Y0 + int(y0)


class _Couche:
    """Couche de référence (comme tr.Rendu) : image RGBA, coin haut gauche, métriques."""

    def __init__(self, couche, x, y, metriques):
        self.couche, self.x, self.y, self.metriques = couche, x, y, metriques


def reference(tr, cas, maquettes):
    """Rend la demande du journal avec le moteur des maquettes, dans l'allure posée (gabarit du journal) : préréglage
    (contour, ombre, gamma), rayon du contour, approche, équilibrage (coupes figées : texte avec ses retours à la ligne,
    largeur sans limite, aucun équilibrage). Ombre douce (narration plein écran, planche d1) : la recette de la planche
    elle-même, _maquettes_narration_d.rasteriser_narration. Ancien journal (étape 2c) : préréglage RÉCIT, rayon = f."""
    pr = cas.get("preset")
    preset = {"contour": pr["contour"], "ombre": pr["ombre"], "gamma": pr["gamma"]} if isinstance(pr, dict) else "RECIT"
    rayon = float(cas.get("rayon", cas["f"]))
    inter = float(cas.get("interlettre", 0.0))
    equilibrer = False if cas.get("equilibrage") == "JAMAIS" else "auto"
    centre = (float(cas["cx"]), float(cas["cy"]))
    cle = POLICES.get(cas.get("police", "RECIT"), POLICE)
    if cas.get("ombre"):
        nd = charger("_maquettes_narration_d", os.path.join(maquettes, "_maquettes_narration_d.py"))
        police = tr.charger(cle)
        mise = tr.mettre_en_page(cas["texte"], police, float(cas["cap"]), float(cas["L"]), int(cas["P"]), centre, "c",
                                 inter, "auto", equilibrer)
        o = cas["ombre"]
        couche, x, y = rasteriser_ombre_couleurs(tr, nd, mise, rayon, float(pr["contour"]),
                                                 (int(o["dx"]), int(o["dy"]), float(o["flou"]), float(pr["ombre"])),
                                                 float(pr["gamma"]))
        return _Couche(couche, x, y, {"nb_lignes": len(mise.lignes)})
    r = tr.rendre(cas["texte"], cle, preset, float(cas["cap"]), float(cas["L"]), int(cas["P"]), rayon, centre,
                  interlettre=inter, equilibrer=equilibrer)
    return _Couche(r.couche, r.x, r.y, r.metriques)


def comparer(tr, cas, captures, sortie, serie="centre", maquettes=MAQUETTES):
    nu = Image.open(os.path.join(captures, cas["sans"] + ".png")).convert("RGBA")
    jeu = Image.open(os.path.join(captures, cas["cas"] + ".png")).convert("RGBA")
    W, H = nu.size
    a = float(cas["a"])
    r = reference(tr, cas, maquettes)
    ref = nu.copy()
    voile = float(cas.get("voile", 0.0)) * a
    if voile > 0.002:   # voile plein écran du gabarit (encre), sous le texte, au fondu du texte
        v = Image.new("RGBA", ref.size, tr.ENCRE + (int(round(min(1.0, voile) * 255)),))
        ref = Image.alpha_composite(ref, v)
    # vignette du gabarit (planche d1) au fondu du texte, ou bords assombris par la concentration (lot 4) : la plus forte
    # depuis la revue du 02/11 : la vignette suit la narration entière (pas l'opacité de la page) et passe sous le HUD ;
    # le journal donne la force posée (vignette_force), sinon l'ancienne règle (vignette × opacité du texte)
    if "vignette_force" in cas:
        vignette = float(cas["vignette_force"])
    else:
        vignette = max(float(cas.get("vignette", 0.0)) * a, float(cas.get("bords", 0.0)))
    if vignette > 0.002:
        nd = charger("_maquettes_narration_d", os.path.join(maquettes, "_maquettes_narration_d.py"))
        ref = nd.vignette(ref, vignette)
    if a > 0.002:
        tr.poser(ref, avec_alpha(r.couche, a), r.x, r.y)
    jx, jy, jw, jh = [int(v) for v in cas["couche"].split(",")]
    pos_ok = (jx, jy, jw, jh) == (r.x, r.y, r.couche.width, r.couche.height)
    x0, y0 = max(0, r.x - MARGE), max(0, r.y - MARGE)
    x1, y1 = min(W, r.x + r.couche.width + MARGE), min(H, r.y + r.couche.height + MARGE)
    A = np.asarray(jeu.crop((x0, y0, x1, y1)).convert("RGB"), np.int32)
    B = np.asarray(ref.crop((x0, y0, x1, y1)).convert("RGB"), np.int32)
    N = np.asarray(nu.crop((x0, y0, x1, y1)).convert("RGB"), np.int32)
    emp = elargir(np.abs(B - N).max(axis=2) > 0)
    d = np.abs(A - B).max(axis=2)
    if emp.any():
        moy, mx = float(d[emp].mean()), int(d[emp].max())
    else:
        moy, mx = 0.0, int(d.max())
    # cœur : pixels où la couche (opacité du cas comprise) est pleine — le fond n'y entre pour rien, seul le texte compte
    alpha = np.zeros((H, W), np.uint8)
    if a > 0.002:
        cou = np.asarray(avec_alpha(r.couche, a))[..., 3]
        ax0, ay0 = max(0, r.x), max(0, r.y)
        ax1, ay1 = min(W, r.x + cou.shape[1]), min(H, r.y + cou.shape[0])
        alpha[ay0:ay1, ax0:ax1] = cou[ay0 - r.y:ay1 - r.y, ax0 - r.x:ax1 - r.x]
    coeur = alpha[y0:y1, x0:x1] >= 250
    c_moy = float(d[coeur].mean()) if coeur.any() else 0.0
    c_max = int(d[coeur].max()) if coeur.any() else 0
    hors = np.abs(A - N).max(axis=2)[~emp]
    derive = float(hors.mean()) if hors.size else 0.0
    hors_max = int(hors.max()) if hors.size else 0
    # fond qui bouge entre les deux captures (avatar au repos, grain du filtre du KO) : « hors » n'est qu'indicatif
    fixe = derive < 0.02
    ok = pos_ok and c_max < CIBLE_COEUR and moy < CIBLE_MOY + derive and (not fixe or hors_max < CIBLE_MAX)
    m = r.metriques
    ligne = ("%-38s %s · cœur : moyen %4.2f max %2d (%5d px) · empreinte : moyen %5.2f max %3d (%5d px) · hors : max %3d, "
             "dérive %4.2f%s · couche %s %s · %s, %d lignes, %d×%d, f %.2f, ancre (%.1f ; %.1f), opacité %.2f, page %s/%s"
             % (cas["cas"], "OK" if ok else "HORS CIBLE", c_moy, c_max, int(coeur.sum()), moy, mx, int(emp.sum()),
                hors_max, derive, "" if fixe else " (fond animé)",
                "%d,%d %dx%d" % (r.x, r.y, r.couche.width, r.couche.height), "=" if pos_ok else "≠ jeu " + cas["couche"],
                cas.get("gabarit", "stele"), m["nb_lignes"], W, H, float(cas["f"]), float(cas["cx"]), float(cas["cy"]), a,
                cas.get("page", 0) + 1, cas.get("pages", 1)))
    planche(A, B, os.path.join(sortie, "%s_compare_%s.png" % (serie, cas["cas"])))
    return ok, ligne


def titre(img, x, y, texte, f):
    ImageDraw.Draw(img).text((x, y), texte, fill=(242, 236, 228), font=f)


def planches_maquettes(captures, maquettes, dossier):
    """Planches « maquette | jeu » (recadrages à 1:1) pour a1, a2, a3, a6, a8, et la bande des fondus (a4)."""
    f = police_legende(15)
    faites = []

    def cap(nom):
        p = os.path.join(captures, nom + ".png")
        return Image.open(p).convert("RGB") if os.path.isfile(p) else None

    def maq(nom):
        p = os.path.join(maquettes, nom)
        return Image.open(p).convert("RGB") if os.path.isfile(p) else None

    # a1, a2, a3, a8 : la scène 1920 × 1080 est en haut de la maquette
    paires = [("a1_scene_salle_sombre_AlegreyaSans-BoldItalic.png", "centre_01_salle_sombre", TUILE, "a1 · salle sombre"),
              ("a2_narration_jour.png", "centre_02_jour", TUILE, "a2 · jour"),
              ("a3_narration_courte_neige.png", "centre_03_neige_court", (680, 503, 1240, 663), "a3 · texte court, neige"),
              ("a8_tchat_ouvert.png", "centre_07_tchat_e4", (880, 423, 1480, 743), "a8 · tchat ouvert, échelle 4")]
    rangs = []
    for m_nom, c_nom, boite, legende in paires:
        m, c = maq(m_nom), cap(c_nom)
        if m is None or c is None:
            continue
        rangs.append((legende, m.crop(boite), c.crop(boite)))
    # a6 : tuiles 800 × 600 de la maquette (ordre : noir, noir + texte, inconscient, à terre, inconscient court)
    a6 = maq("a6_etats.png")
    if a6 is not None:
        noms = ["centre_06_noir", "centre_06_noir_texte", "centre_06_inconscient", "centre_06_a_terre",
                "centre_06_inconscient_court"]
        legendes = ["a6 · écran noir", "a6 · écran noir avec texte", "a6 · inconscient", "a6 · à terre",
                    "a6 · inconscient, texte court"]
        for i, (c_nom, legende) in enumerate(zip(noms, legendes)):
            c = cap(c_nom)
            if c is None:
                continue
            x = 16 + (i % 2) * (800 + 24)
            y = 110 + (i // 2) * (600 + 64) + 40
            rangs.append((legende, a6.crop((x, y, x + 800, y + 600)), c.crop(A6_BOITE)))
    if rangs:
        largeur = max(m.width + c.width for _, m, c in rangs) + 3 * 16
        hauteur = sum(m.height for _, m, _ in rangs) + len(rangs) * 40 + 60
        out = Image.new("RGB", (largeur, hauteur), (13, 10, 11))
        titre(out, 16, 14, "Texte au centre (lot 2, étape 2c) : maquette v2 à gauche, jeu à droite, recadrages à 1:1", f)
        y = 50
        for legende, m, c in rangs:
            titre(out, 16, y, legende + " — maquette", f)
            titre(out, 32 + m.width, y, "jeu (reborn-hud)", f)
            out.paste(m, (16, y + 24))
            out.paste(c, (32 + m.width, y + 24))
            y += m.height + 40
        chemin = os.path.join(dossier, "planche_centre_maquette_vs_jeu.png")
        out.save(chemin)
        faites.append(chemin)
    # a4 : les six vignettes du jeu (recadrage 560 × 320, à 1:1), comme la maquette
    tuiles = [("t = 0 ms", "centre_04_fondu_t000"), ("t = 60 ms", "centre_04_fondu_t060"),
              ("t = 120 ms", "centre_04_fondu_t120"), ("t = 180 ms : fin du fondu", "centre_04_fondu_t180"),
              ("plein (pendant la durée)", "centre_04_fondu_plein"), ("sortie : 150 ms sur 300", "centre_04_fondu_sortie150")]
    imgs = [(l, cap(n)) for l, n in tuiles]
    if all(c is not None for _, c in imgs):
        tw, th = TUILE[2] - TUILE[0], TUILE[3] - TUILE[1]
        out = Image.new("RGB", (16 + 3 * (tw + 24), 60 + 2 * (th + 40)), (13, 10, 11))
        titre(out, 16, 14, "a4 · fondu d'entrée 180 ms (ease-out) et de sortie 300 ms, jeu (vignettes 560 × 320 à 1:1)", f)
        for i, (l, c) in enumerate(imgs):
            x = 16 + (i % 3) * (tw + 24)
            y = 50 + (i // 3) * (th + 40)
            titre(out, x, y, l, f)
            out.paste(c.crop(TUILE), (x, y + 22))
        chemin = os.path.join(dossier, "centre_04_apparition.png")
        out.save(chemin)
        faites.append(chemin)
    return faites


# narration (étape 2d, allure « narration » plein écran du 02/11) : scènes du banc et légendes, dans l'ordre de la planche
NARRATION = [
    ("narration_01_file_auto", "01 · file, durée auto, salle sombre avec le HUD"),
    ("narration_01_jour", "01 · la même, de jour (forêt)"),
    ("narration_01_neige", "01 · la même, sur la neige"),
    ("narration_02_file_1", "02 · deux en file : n2 (4 s) à l'écran, n3 attend"),
    ("narration_02_file_sortie150", "02 · n2 : fondu de sortie, 150 ms sur 300"),
    ("narration_02_file_ecart", "02 · écart de 400 ms : rien à l'écran"),
    ("narration_02_file_2", "02 · n3, 400 ms après la fin de n2"),
    ("narration_03_avant_remplace", "03 · n4 à l'écran, n5 et n6 en file"),
    ("narration_03_coupe075", "03 · « remplace » : n4 coupée, 0,50 à 75 ms"),
    ("narration_03_remplace", "03 · n7 ; la file est vidée"),
    ("narration_03_apres", "03 · après n7 : n5 et n6 ne passent jamais"),
    ("narration_04_tenue", "04 · tenue (-1), deux minutes plus tard"),
    ("narration_04_fin150", "04 · narration_fin : 150 ms sur 300"),
    ("narration_05_tout_efface", "05 · narration_fin à id vide : écran et file vidés"),
    ("narration_06_couleurs", "06 · rouge, or, ombre, gris ; clé inconnue = blanc"),
    ("narration_06_couleurs_neige", "06 · les mêmes sur la neige"),
    ("narration_07_longue_p1", "07 · 998 caractères : page 1"),
    ("narration_07_longue_p2", "07 · 998 caractères : page 2 (prorata)"),
    ("narration_07_longue_p3", "07 · 998 caractères : page 3"),
    ("narration_07_longue_p4", "07 · 998 caractères : page 4"),
    ("narration_08_pos30", "08 · pos 30 : centre du bloc à 30 %"),
    ("narration_08_pos_hors_bornes", "08 · pos 150 (hors bornes) : place par défaut"),
    ("narration_09_mot_long", "09 · mot plus large que la ligne : coupé"),
    ("narration_10_rattrapage", "10 · rattrapage : s 90, ecoule 23 417 ms"),
    ("narration_10_rattrapage_t060", "10 · rattrapage à 30 s : page de départ, fondu à 60 ms"),
    ("narration_10_rattrapage_page2", "10 · rattrapage à 30 s : page de départ"),
    ("narration_12_noir", "12 · écran noir du staff"),
    ("narration_12_noir_texte", "12 · écran noir avec texte : dessous"),
    ("narration_12_inconscient", "12 · inconscient : sous le bloc KO"),
    ("narration_12_a_terre", "12 · à terre : 40 %, au-dessus du groupe « À terre »"),
    ("narration_12_tchat", "12 · tchat ouvert (échelle 2)"),
    ("narration_12_cinema_clean", "12 · F1 « CLEAN » : masquée, minuteur qui continue"),
    ("narration_12_cinema_bars", "12 · F1 « BARS » : après les bandes"),
    ("narration_12_stele", "12 · stèle lue (lot 4) : l'autre allure"),
    ("narration_12_devant_stele", "12 · une narration passe devant la stèle"),
    ("narration_12_stele_retour", "12 · la stèle revient"),
    ("narration_13_1366", "13 · 1366 × 768"),
    ("narration_13_1440_e3", "13 · 2560 × 1440, échelle 3"),
    ("narration_14_v1_jour", "14 · planche d1, V1 « Grand récit », de jour"),
    ("narration_14_v1_salle", "14 · V1, salle sombre"),
    ("narration_14_v1_neige", "14 · V1, sur la neige"),
    ("narration_14_v1_1366", "14 · V1, 1366 × 768 (coupes figées à 1080p)"),
    ("narration_14_v1_1440", "14 · V1, 2560 × 1440"),
    ("narration_15_reseau", "15 · par le vrai réseau (serveur intégré)"),
]


def planches_narration(captures, maquettes, dossier):
    """Planche des scènes de la narration (allure plein écran) : chaque capture entière réduite ÷ 3, trois par rang ;
    les captures à 1:1 sont copiées à côté (le texte ne se juge qu'à 100 %)."""
    f = police_legende(15)
    tuiles = []
    for nom, legende in NARRATION:
        chemin = os.path.join(captures, nom + ".png")
        if os.path.isfile(chemin):
            img = Image.open(chemin).convert("RGB")
            tuiles.append((legende, img.resize((img.width // 3, img.height // 3), Image.LANCZOS)))
    if not tuiles:
        return []
    tw = max(t.width for _, t in tuiles)
    th = max(t.height for _, t in tuiles)
    rangs = (len(tuiles) + 2) // 3
    out = Image.new("RGB", (16 + 3 * (tw + 16), 60 + rangs * (th + 40)), (13, 10, 11))
    titre(out, 16, 14, "Narration du staff, allure « narration » plein écran (provisoire, décision du 02/11) : écrans "
          "entiers réduits ÷ 3 — captures à 1:1 à côté", f)
    for i, (legende, img) in enumerate(tuiles):
        x = 16 + (i % 3) * (tw + 16)
        y = 50 + (i // 3) * (th + 40)
        titre(out, x, y, legende, f)
        out.paste(img, (x, y + 22))
    chemin = os.path.join(dossier, "planche_narration_scenes.png")
    out.save(chemin)
    return [chemin]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run", nargs="?", default=RUN)
    ap.add_argument("--serie", default="centre", choices=["centre", "narration"])
    try:
        sys.stdout.reconfigure(errors="replace")   # console Windows (cp1252) : « → » du journal sans planter
    except (AttributeError, ValueError):
        pass
    ap.add_argument("--ref", default=None)
    ap.add_argument("--maquettes", default=MAQUETTES)
    ap.add_argument("--sortie", default=None)
    ap.add_argument("--planches", default=None)
    args = ap.parse_args()
    captures = os.path.join(args.run, "screenshots")
    serie = args.serie
    sortie = args.sortie or os.path.join(captures, serie + "_compare")
    os.makedirs(sortie, exist_ok=True)
    tr = charger("_texte_raster", args.ref or os.path.join(args.maquettes, "_texte_raster.py"))
    tr.POLICES.setdefault("philosopher_b", PHILOSOPHER_B)
    journal = os.path.join(args.run, serie + "_banc.txt")
    if not os.path.isfile(journal):
        sys.exit("journal introuvable : %s (banc REBORN_FEATURE_PREVIEW=%s)" % (journal, serie))
    lignes, notes, echec = [], [], False
    with open(journal, encoding="utf-8") as fj:
        for brut in fj:
            brut = brut.strip()
            if not brut:
                continue
            cas = json.loads(brut)
            if "note" in cas:
                notes.append(cas["note"])
                continue
            if not os.path.isfile(os.path.join(captures, cas["cas"] + ".png")) or \
                    not os.path.isfile(os.path.join(captures, cas["sans"] + ".png")):
                lignes.append("%-38s capture absente" % cas["cas"])
                echec = True
                continue
            if not cas.get("texte"):
                lignes.append("%-38s aucun texte posé (%s)" % (cas["cas"], cas.get("id", "")))
                continue
            if cas.get("sans_reference"):
                # le moteur des maquettes ne sait pas rendre ce cas (mot plus large que la ligne : il ne coupe pas)
                lignes.append("%-38s sans référence : %s" % (cas["cas"], cas["sans_reference"]))
                continue
            ok, ligne = comparer(tr, cas, captures, sortie, serie, args.maquettes)
            echec |= not ok
            lignes.append(ligne)
    rapport = "\n".join(notes + [""] + lignes)
    print(rapport)
    rapport_nom = serie + "_compare.txt"
    with open(os.path.join(sortie, rapport_nom), "w", encoding="utf-8") as f:
        f.write(rapport + "\n")
    if args.planches:
        os.makedirs(args.planches, exist_ok=True)
        for nom in sorted(os.listdir(captures)):
            if nom.startswith(serie + "_") and nom.endswith(".png") and not nom.endswith("_sans.png"):
                shutil.copy2(os.path.join(captures, nom), os.path.join(args.planches, nom))
        if serie == "narration":
            faites = planches_narration(captures, args.maquettes, args.planches)
        else:
            faites = planches_maquettes(captures, args.maquettes, args.planches)
        for p in faites:
            print("planche : %s" % p)
        shutil.copy2(os.path.join(sortie, rapport_nom), os.path.join(args.planches, rapport_nom))
    sys.exit(1 if echec else 0)


if __name__ == "__main__":
    main()
