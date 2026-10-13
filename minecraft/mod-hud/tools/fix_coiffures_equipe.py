# -*- coding: utf-8 -*-
"""Correctifs de deux coiffures de l'équipe qui se teignaient mal + bandeau en accessoire.

  * Cheveux_Sasuke : le bandeau frontal (bande bleue + plaque grise) était peint dans la
    couche cheveux, donc reteint avec la couleur des cheveux et la plaque fixait la luma
    de référence. On retire le bandeau (cheveux reconstitués à sa place : mèches des
    côtés / de l'arrière prolongées, frange ouverte sur le front), on passe la coiffure
    dans la convention (niveaux de gris, reflet à 255) et le bandeau sort en accessoire.
  * Cheveux_SakuraAgee : pixels blancs (plaque) et rouge vif (lien) dans la texture ->
    luma de référence à 255 sur un pixel qui n'est pas du cheveu, le rose/blond sortait
    terne. Conversion dans la convention en gardant le dessin : la plaque devient le
    reflet du dessus du crâne, le lien rouge une ombre de mèche.
  * Accessoire_BandeauKonoha : bandeau frontal de Konoha (tissu bleu sombre + plaque de
    métal gravée), sur la seconde couche de la tête, tint none (affiché tel que peint,
    composé APRÈS les cheveux par RebornSkins.compose).

Convention (tools/gen_coiffures.py) : niveaux de gris R=G=B, RebornSkins teinte en
tint=all couleur × (luma / luma max) ; luma max = 255 sur un pixel de cheveu, masse des
cheveux entre ~120 (p5) et ~230 (p95), médiane ~175.

Les originaux sont lus dans tools/coiffures_origine/ (copie de sauvegarde, jamais
réécrite) : le script est rejouable.

Usage :
    python tools/fix_coiffures_equipe.py [--planche <png>] [--sans-catalogue]
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
CHAR = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "character")
HAIR = os.path.join(CHAR, "hair")
ACC = os.path.join(CHAR, "accessory")
CATALOG = os.path.join(CHAR, "catalog.json")
ORIGINE = os.path.join(ICI, "coiffures_origine")

SASUKE = "Cheveux_Sasuke"
SAKURA = "Cheveux_SakuraAgee"
BANDEAU = "Accessoire_BandeauKonoha"
BANDEAU_NOM = "Bandeau frontal de Konoha"

# Cibles de la convention (statistiques des 23 coiffures générées).
CIBLE = ((0.05, 120.0), (0.50, 175.0), (0.95, 230.0))
PLANCHER, PLAFOND = 78.0, 244.0      # pixel le plus sombre / le plus clair hors reflet


def charger(ident):
    src = os.path.join(ORIGINE, ident + ".png")
    if not os.path.exists(src):                       # première exécution : sauvegarde
        os.makedirs(ORIGINE, exist_ok=True)
        shutil.copy2(os.path.join(HAIR, ident + ".png"), src)
    return np.array(Image.open(src).convert("RGBA")).astype(np.float32)


def luma(a):
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def remap(lum, op, ref=None):
    """Luminances d'origine -> convention, par interpolation des quantiles p5/p50/p95.
    `ref` = masque des pixels de cheveux servant à mesurer les quantiles."""
    ref = op if ref is None else ref
    v = lum[ref]
    xs = [float(v.min())] + [float(np.quantile(v, q)) for q, _ in CIBLE] + [float(v.max())]
    ys = [PLANCHER] + [t for _, t in CIBLE] + [PLAFOND]
    for i in range(1, len(xs)):                       # quantiles strictement croissants
        xs[i] = max(xs[i], xs[i - 1] + 1e-3)
    out = np.interp(lum, xs, ys).astype(np.float32)
    return np.where(op, out, np.nan)


def vers_png(g, chemin):
    op = ~np.isnan(g)
    g8 = np.clip(np.nan_to_num(g) + 0.5, 0, 255).astype(np.uint8)
    rgba = np.zeros((64, 64, 4), np.uint8)
    for c in range(3):
        rgba[..., c] = np.where(op, g8, 0)
    rgba[..., 3] = np.where(op, 255, 0)
    Image.fromarray(rgba, "RGBA").save(chemin)


# ───────────────────────── Sasuke ─────────────────────────
def sasuke():
    a = charger(SASUKE)
    op = a[..., 3] > 0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    bande = op & (b - r > 40)                         # tissu bleu (les cheveux : b-r <= 30)
    plaque = np.zeros((64, 64), bool)
    plaque[10:12, 10:14] = True                       # plaque grise, face rangées 2-3
    lum = luma(a)
    cheveux = op & ~bande & ~plaque
    lum = np.where(cheveux, lum, np.nan)

    # 1) Côtés + arrière (couche de base, rangées 10-11) : on prolonge chaque mèche
    #    entre la rangée 9 et la rangée 12 de la même colonne.
    for x0 in (0, 16, 24):                            # R, L, B
        for x in range(x0, x0 + 8):
            hau, bas_ = lum[9, x], lum[12, x]
            if np.isnan(hau):
                hau = bas_
            if np.isnan(bas_):
                bas_ = hau
            if np.isnan(hau):
                continue
            lum[10, x] = hau * 0.62 + bas_ * 0.38
            lum[11, x] = hau * 0.30 + bas_ * 0.70
    # 2) Face (base, x 8..15) : frange ouverte. Rangée 10 : mèches aux tempes + une
    #    pointe décentrée ; rangée 11 : seules les tempes (le front apparaît au milieu).
    fr9 = lum[9, 8:16].copy()
    for i, c in enumerate("###.####"):
        lum[10, 8 + i] = fr9[i] * 1.08 if c == "#" else np.nan
    for i, c in enumerate("##....##"):
        lum[11, 8 + i] = fr9[i] * 1.02 if c == "#" else np.nan
    lum[10, 12] = fr9[4] * 0.92                       # pointe de la mèche centrale (plus sombre)
    # 3) Seconde couche : l'épaisseur du bandeau sur les côtés devient du cheveu (volume
    #    des mèches conservé) ; le nœud derrière la tête (hB, x 56..63) disparaît.
    for (y, x) in zip(*np.nonzero(bande)):
        if x >= 56:
            lum[y, x] = np.nan
        elif x >= 32:
            base = lum[y, x - 32]
            lum[y, x] = (base if not np.isnan(base) else np.nanmean(lum[9, 0:8])) * 1.06
    op2 = ~np.isnan(lum)
    out = remap(np.nan_to_num(lum), op2)
    # 4) Reflet : le pixel le plus clair du dessus du crâne côté front -> 255.
    top = out[5:7, 8:16]
    j, i = np.unravel_index(np.nanargmax(top), top.shape)
    out[5 + j, 8 + i] = 255.0
    vers_png(out, os.path.join(HAIR, SASUKE + ".png"))


# ───────────────────────── Sakura ─────────────────────────
def sakura():
    a = charger(SAKURA)
    op = a[..., 3] > 0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = np.maximum(np.maximum(r, g), b), np.minimum(np.minimum(r, g), b)
    gris = op & (mx - mn < 12)                        # plaque blanche / grise (dessus du crâne)
    # lien rouge vif : rouge pur (g ~ b, b faible) — les roses ont b nettement > g
    rouge = op & ~gris & (r > 90) & (b < g + 14) & (r > 2.2 * b)
    rouge |= op & ~gris & (r > 110) & (g < 40) & (b < 40)
    rouge |= op & ~gris & (r >= 0x7a) & (g < 0x10) & (b < 0x20)
    cheveux = op & ~gris & ~rouge
    lum = luma(a)
    out = remap(lum, op, ref=cheveux)
    # Plaque -> reflet du dessus du crâne (dessin gardé, luminances du haut de la rampe).
    lg = lum[gris]
    if lg.size:
        t = (lum - lg.min()) / max(1.0, lg.max() - lg.min())
        out = np.where(gris, 214.0 + 30.0 * t, out)
    # Lien rouge -> ombre de mèche : un cran sous la mèche voisine la plus sombre.
    for (y, x) in zip(*np.nonzero(rouge)):
        vois = [out[yy, xx] for yy, xx in ((y, x - 1), (y, x + 1), (y - 1, x), (y + 1, x))
                if 0 <= yy < 64 and 0 <= xx < 64 and cheveux[yy, xx]]
        out[y, x] = (min(vois) if vois else 140.0) * 0.86
    out = np.where(op, np.clip(out, PLANCHER * 0.9, 255.0), np.nan)
    # Reflet unique à 255 : le pixel le plus clair de la plaque (dessus du crâne, front).
    if gris.any():
        cand = np.where(gris, lum, -1.0)
    else:
        cand = np.where(cheveux, lum, -1.0)
    y, x = np.unravel_index(np.argmax(cand), cand.shape)
    out = np.where(op, np.minimum(out, 246.0), np.nan)
    out[y, x] = 255.0
    vers_png(out, os.path.join(HAIR, SAKURA + ".png"))
    return int(gris.sum()), int(rouge.sum())


# ───────────────────────── Bandeau de Konoha (accessoire) ─────────────────────────
TISSU = {"h": (0x34, 0x4A, 0x78), "m": (0x26, 0x37, 0x5C), "s": (0x1A, 0x26, 0x42),
         "k": (0x13, 0x1C, 0x31)}
METAL = {"H": (0xCF, 0xD5, 0xDD), "1": (0xB6, 0xBD, 0xC8), "2": (0xA2, 0xAA, 0xB6),
         "3": (0x8E, 0x96, 0xA3), "R": (0x74, 0x7C, 0x89), "E": (0x50, 0x57, 0x64)}
# Plaque 6×3 (face, colonnes 1..6, rangées 9..11) : bord R, métal dégradé H→3 de haut
# en bas, symbole de Konoha gravé E (spirale ouverte + pointe de feuille en bas à gauche).
PLAQUE = ["_1EEH_",       # _ = coin arrondi : la plaque dépasse du tissu d'un texel
          "REHE1R",
          "EEE23R"]


def bandeau():
    img = np.zeros((64, 64, 4), np.uint8)

    def px(x, y, col):
        img[y, x, :3] = col
        img[y, x, 3] = 255

    pal = {**TISSU, **METAL}
    # Seconde couche de la tête (x+32) : R 32..39, F 40..47, L 48..55, B 56..63 ; tissu
    # sur les rangées 10 (haut, éclairé) et 11 (bas, ombre). Les plis aux coins de la
    # tête sont un cran plus sombres.
    for x in range(32, 64):
        loc = (x - 32) % 8
        coin = loc in (0, 7)
        px(x, 10, pal["m" if coin else "h"])
        px(x, 11, pal["k" if coin else "s"])
    # Plaque (face, colonnes 1..6 = x 41..46, rangées 9..11), symbole de Konoha gravé
    # (spirale + pointe de feuille) sur les colonnes 2..5.
    plaque = PLAQUE
    for j, ligne in enumerate(plaque):
        for i, c in enumerate(ligne):
            if c != "_":                              # _ = coin arrondi (cheveux visibles)
                px(41 + i, 9 + j, pal[c])
    # Nœud derrière la tête (B, x 56..63 ; col 0 = côté gauche du perso) + deux pans.
    px(59, 10, pal["s"]); px(60, 10, pal["m"])
    px(59, 11, pal["k"]); px(60, 11, pal["s"])
    px(59, 12, pal["m"]); px(60, 12, pal["s"])
    px(58, 13, pal["s"]); px(60, 13, pal["k"])
    os.makedirs(ACC, exist_ok=True)
    Image.fromarray(img, "RGBA").save(os.path.join(ACC, BANDEAU + ".png"))


# ───────────────────────── catalogue ─────────────────────────
def maj_catalogue():
    """Ajoute l'accessoire si absent (édition textuelle : le reste du fichier intact)."""
    with open(CATALOG, encoding="utf-8") as f:
        txt = f.read()
    cat = json.loads(txt)
    if any(a.get("id") == BANDEAU for a in cat.get("accessory", [])):
        return False
    entree = {"id": BANDEAU, "name": BANDEAU_NOM, "tint": "none"}
    if cat.get("accessory") == [] and '"accessory": []' in txt:
        bloc = json.dumps([entree], ensure_ascii=False, indent=2).replace("\n", "\n  ")
        txt = txt.replace('"accessory": []', '"accessory": ' + bloc, 1)
    else:
        cat.setdefault("accessory", []).append(entree)
        txt = json.dumps(cat, ensure_ascii=False, indent=2) + "\n"
    json.loads(txt)                                   # garde-fou : JSON valide
    with open(CATALOG, "w", encoding="utf-8", newline="\n") as f:
        f.write(txt)
    return True


# ───────────────────────── planche avant / après ─────────────────────────
COULEURS = (("noir", "2A2630"), ("blond", "F0D080"), ("rose", "F09AC0"))
VUES = (("face", 0.0, 0.0), ("3/4", 34.0, 10.0), ("dos", 150.0, 10.0))


def planche(chemin, echelle=7):
    sys.path.insert(0, os.path.join(ICI, "style"))
    import perso                                      # noqa: E402
    perso._CAT = None
    try:
        police = ImageFont.truetype("arial.ttf", 15)
        petite = ImageFont.truetype("arial.ttf", 12)
    except OSError:
        police = petite = ImageFont.load_default()

    # Les originaux sont rendus via un id temporaire posé à côté (supprimé ensuite).
    lignes = [
        ("Sasuke — AVANT (bandeau peint dans les cheveux)", SASUKE + "__avant", SASUKE, "", False),
        ("Sasuke — APRÈS (coiffure seule)", SASUKE, None, "", False),
        ("Sasuke — APRÈS + accessoire « " + BANDEAU_NOM + " »", SASUKE, None, BANDEAU, False),
        ("Bandeau seul sur d'autres coiffures (Court / Hime)", "Cheveux_Court", None, BANDEAU, False),
        ("Sakura — AVANT (plaque blanche + lien rouge)", SAKURA + "__avant", SAKURA, "", True),
        ("Sakura — APRÈS (convention niveaux de gris)", SAKURA, None, "", True),
    ]
    temp = []
    try:
        for _, ident, orig, _, _ in lignes:
            if orig:
                dst = os.path.join(HAIR, ident + ".png")
                shutil.copy2(os.path.join(ORIGINE, orig + ".png"), dst)
                temp.append(dst)
        perso._TEX.clear()
        cat = perso.catalogue()
        for _, ident, orig, _, _ in lignes:
            if orig:
                cat["hair"].append({"id": ident, "name": ident, "tint": "all"})
        cellules = []
        for titre, ident, _, acc, femme in lignes:
            rendus = []
            for _, col in COULEURS:
                cheveux = ident
                fem = femme
                if titre.startswith("Bandeau seul"):
                    cheveux, fem = ("Cheveux_Court", False) if col != "F09AC0" else ("Cheveux_Hime", True)
                sk = perso.compose(sexe="femme" if fem else "homme", teint=3,
                                   tenue="Complet_Femme1" if fem else "tenueh46",
                                   cheveux=cheveux, couleur_cheveux=col, sourcils="2A2022",
                                   accessoire=acc)
                for _, lac, tan in VUES:
                    rendus.append(perso.rendu_buste(sk, echelle, lacet=lac, tangage=tan, bas=12))
            cellules.append((titre, rendus))
    finally:
        for p in temp:
            if os.path.exists(p):
                os.remove(p)
        perso._CAT = None

    pw = max(r.width for _, rs in cellules for r in rs)
    ph = max(r.height for _, rs in cellules for r in rs)
    gap = 6
    cw = 9 * pw + 8 * gap + 2 * 14 + 24
    ch = ph + 34
    entete = 58
    img = Image.new("RGBA", (cw, entete + ch * len(cellules)), (38, 30, 36, 255))
    d = ImageDraw.Draw(img)
    d.text((12, 8), "Correctifs coiffures — face / 3/4 / dos — noir, blond, rose",
           fill=(240, 220, 170, 255), font=police)
    x = 12
    for nom, _ in COULEURS:
        d.text((x + pw, 34), nom, fill=(200, 185, 150, 255), font=petite)
        x += 3 * (pw + gap) + 14
    for k, (titre, rs) in enumerate(cellules):
        y0 = entete + k * ch
        d.rectangle([6, y0, cw - 6, y0 + ch - 6], fill=(52, 42, 50, 255))
        d.text((12, y0 + 4), titre, fill=(240, 220, 170, 255), font=petite)
        x = 12
        for n, r in enumerate(rs):
            img.alpha_composite(r, (x + (pw - r.width) // 2, y0 + 24 + (ph - r.height)))
            x += pw + gap + (14 if n % 3 == 2 else 0)
    os.makedirs(os.path.dirname(os.path.abspath(chemin)), exist_ok=True)
    img.convert("RGB").save(chemin)


def stats(ident, dossier=HAIR):
    a = np.array(Image.open(os.path.join(dossier, ident + ".png")).convert("RGBA")).astype(np.float32)
    op = a[..., 3] > 0
    l = luma(a)[op]
    rgb = a[..., :3][op]
    sat = int((rgb.max(1) - rgb.min(1) > 0).sum())
    q = np.quantile(l, [0.05, 0.5, 0.95]).round(0)
    return f"{ident}: {op.sum()} px, luma max {l.max():.0f}, p5/50/95 {q}, n255 {(l >= 254.5).sum()}, px colorés {sat}"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--planche", help="planche de contrôle avant/après (PNG)")
    ap.add_argument("--sans-catalogue", action="store_true")
    a = ap.parse_args()
    sasuke()
    g, r = sakura()
    bandeau()
    print(stats(SASUKE))
    print(stats(SAKURA), f"(plaque {g} px, lien {r} px convertis)")
    print(f"{BANDEAU}.png écrit dans {os.path.normpath(ACC)}")
    if not a.sans_catalogue:
        print("catalog.json :", "accessoire ajouté" if maj_catalogue() else "accessoire déjà présent")
    if a.planche:
        planche(a.planche)
        print("planche ->", a.planche)


if __name__ == "__main__":
    main()
