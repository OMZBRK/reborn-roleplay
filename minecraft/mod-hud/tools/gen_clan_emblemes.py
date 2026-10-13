# -*- coding: utf-8 -*-
"""Emblèmes de clan — les VRAIS blasons, au langage des emblèmes de village (gui/esc/village_<id>.png).

    python tools/gen_clan_emblemes.py                      -> les 22 emblèmes + la planche de contrôle
    python tools/gen_clan_emblemes.py uchiha               -> un seul (planche quand même)
    python tools/gen_clan_emblemes.py --planche <png> --refs <dossier des SVG de référence des villages>

Sortie : src/main/resources/assets/reborn/textures/gui/clans/clan_<id>.png — 48x48, blanc sur transparent,
teinté par le code comme les villages (blit avec tint ARGB). <id> = CharacterRules.slug(nom du clan).

Méthode (comme les villages, tools/ui-art/gen_esc_art.py::raster_svg) : chaque blason part du SVG de
Narutopedia (tools/clans_canon/<id>.svg, sources ci-dessous), rendu en x8 avec PyMuPDF. L'« encre » du
blason (le noir, et le rouge quand il est l'aplat du blason, ex. haut de l'éventail Uchiha) devient le
blanc ; le reste (fond, aplats décoratifs) devient transparent. Le dessin est cadré dans 46 px, son trait
ramené à l'épaisseur des villages (≈ 2,8 px) par dilatation/érosion d'un disque, puis réduit en LANCZOS.
Les aplats pleins du canon restent pleins.

Clans sans blason canon (vérifié sur Narutopedia, oct. 2026) : symbole le plus proche du canon, signalé
dans STATUT — kanji du chapeau de Kage de la famille, symbole des Unités médicales pour les Hōki (clan de
médecins), kanji 鮫 pour les Hoshigaki (Kisame 鬼鮫, Samehada 鮫肌).
"""
from __future__ import annotations

import math
import os
import sys
import tempfile
from pathlib import Path

import numpy as np
import pymupdf
from PIL import Image, ImageDraw, ImageFont

ICI = Path(__file__).resolve().parent
RES = ICI.parent / "src/main/resources/assets/reborn/textures/gui"
SORTIE = RES / "clans"
CANON = ICI / "clans_canon"
LOGOS_VILLAGES = ICI.parents[2] / "tools/ui-art/logos"     # SVG Commons des villages (gen_esc_art.py)

T = 48                 # taille finale
K = 8                  # suréchantillonnage
EP = 2.8               # épaisseur du trait (px finaux) — mesurée sur village_ame / suna / iwa
CADRE = 46             # plus grande dimension du dessin (px finaux) — les villages vont de 44 à 48
NARUTOPEDIA = "https://naruto.fandom.com/wiki/File:"
POLICE_KANJI = r"C:\Windows\Fonts\YuGothB.ttc"

# id -> recette. svg : fichier de clans_canon ; encre : "encre" (tout ce qui n'est pas blanc) ou "noir"
# (le noir seul : l'aplat rouge de l'Uzumaki est un fond) ; ep : épaisseur visée (None = celle du canon,
# mise à l'échelle) ; fins : (seuil, ajout) épaissit les traits plus fins que 2 x seuil ; gras : dilatation finale (px) ; kanji : symbole de remplacement dans un cercle.
CLANS = {
    "senju":     dict(svg="senju.svg", src=NARUTOPEDIA + "Senju_Symbol.svg", ep=2.2, statut="canon"),
    "uchiha":    dict(svg="uchiha.svg", src=NARUTOPEDIA + "Uchiha_Symbol.svg", statut="canon"),
    "hyuga":     dict(svg="hyuga.svg", src=NARUTOPEDIA + "Hy%C5%ABga_Symbol.svg",
                      statut="canon (guide Michi)"),
    "nara":      dict(svg="nara.svg", src=NARUTOPEDIA + "Nara_Symbol.svg", statut="canon (chapitre 533)"),
    "sarutobi":  dict(svg="sarutobi.svg", src=NARUTOPEDIA + "Sarutobi_Symbol.svg", statut="canon (guide Michi)"),
    "uzumaki":   dict(svg="uzumaki.svg", src=NARUTOPEDIA + "Uzumaki_Symbol.svg", encre="noir", statut="canon"),
    "aburame":   dict(svg="aburame.svg", src=NARUTOPEDIA + "Aburame_Symbol.svg", statut="canon (guide Michi)"),
    "akimichi":  dict(svg="akimichi.svg", src=NARUTOPEDIA + "Akimichi_Symbol.svg",
                      statut="canon (chapitre 533) ; variante : 食 cerclé de Chōji (partie I)"),
    "hatake":    dict(svg="hatake.svg", src=NARUTOPEDIA + "Hatake_Symbol.svg", ep=3.4, statut="canon (guide Michi)"),
    "yamanaka":  dict(svg="yamanaka.svg", src=NARUTOPEDIA + "Yamanaka_Symbol.svg", statut="canon (chapitre 533)"),
    "kurama":    dict(svg="kurama.svg", src=NARUTOPEDIA + "Kurama_Symbol.svg", ep=2.0, statut="canon (anime)"),
    "sabaku":    dict(kanji="風", src="aucun blason (Clan du Kazekage)",
                      statut="SANS BLASON CANON : 風 du chapeau de Kazekage"),
    "hoki":      dict(svg="medical.svg", src=NARUTOPEDIA + "Medical_Teams_Symbol.svg",
                      statut="SANS BLASON CANON : 医 cerclé des Unités médicales (Hōki = médecins)"),
    "hozuki":    dict(svg="hozuki.svg", src=NARUTOPEDIA + "H%C5%8Dzuki_Symbol.svg", ep=2.3,
                      statut="symbole Narutopedia (lanterne hōzuki), pas de blason canon"),
    "hoshigaki": dict(kanji="鮫", src="aucun blason (clan anime)",
                      statut="SANS BLASON CANON : 鮫 (Kisame 鬼鮫, Samehada 鮫肌)"),
    "kaguya":    dict(svg="kaguya.svg", src=NARUTOPEDIA + "Kaguya_Symbol.svg", ep=None, gras=2.0,
                      statut="canon : marques frontales du clan (Kimimaro)"),
    "yuki":      dict(svg="yuki.svg", src=NARUTOPEDIA + "Yuki_Symbol.svg",
                      statut="symbole Narutopedia (flocon), pas de blason canon"),
    "terumi":    dict(kanji="水", src="aucun blason",
                      statut="SANS BLASON CANON : 水 du chapeau de Mizukage (Mei Terumi)"),
    "yotsuki":   dict(svg="yotsuki.svg", src=NARUTOPEDIA + "Yotsuki_Symbol.svg", ep=None, fins=(0.8, 0.7),
                      statut="symbole Narutopedia (lune et nuages = 夜月), pas de blason canon"),
    "kamizuru":  dict(svg="kamizuru.svg", src=NARUTOPEDIA + "Kamizuru_Symbol.svg", ep=None,
                      statut="symbole Narutopedia (sceau 上水流), pas de blason canon"),
    "onoki":     dict(kanji="土", src="aucun blason (anime : famille Kamizuru)",
                      statut="SANS BLASON CANON : 土 du chapeau de Tsuchikage"),
    "autre":     dict(fn="autre", src="—", statut="générique (aucun clan)"),
}
VILLAGES = ["konoha", "suna", "kiri", "kumo", "iwa", "ame"]


# ───────────────────────────── morphologie (numpy seul) ─────────────────────────────
def _disque(r):
    rr = int(math.ceil(r))
    yy, xx = np.mgrid[-rr:rr + 1, -rr:rr + 1]
    return (xx ** 2 + yy ** 2) <= r * r


def _dilate(m, noyau):
    """Dilatation binaire par FFT."""
    h, w = m.shape
    kh, kw = noyau.shape
    H, W = h + kh, w + kw
    f = np.fft.rfft2(m.astype(np.float32), (H, W)) * np.fft.rfft2(noyau.astype(np.float32), (H, W))
    c = np.fft.irfft2(f, (H, W))[kh // 2:kh // 2 + h, kw // 2:kw // 2 + w]
    return c > 0.5


def _erode(m, noyau):
    return ~_dilate(~m, noyau)


def _bord(m):
    e = m.copy()
    e[1:, :] &= m[:-1, :]; e[:-1, :] &= m[1:, :]
    e[:, 1:] &= m[:, :-1]; e[:, :-1] &= m[:, 1:]
    return m ^ e


def epaisseur(m):
    """Épaisseur moyenne des traits (px de la grille) : 2 x aire / périmètre, mesurée hors des aplats
    (les zones de plus de 6,4 px de large, comme le haut de l'éventail Uchiha, sont ignorées)."""
    d = _disque(3.2 * K)
    fins = m & ~_dilate(_erode(m, d), d)
    if fins.sum() < 0.05 * m.sum():
        fins = m
    return 2 * fins.sum() / max(1, _bord(fins).sum())


def ajuste(m, ep_actuelle, ep_visee):
    """Ramène le trait à ep_visee (px de la grille) par dilatation ou érosion d'un disque."""
    d = (ep_visee - ep_actuelle) / 2
    if abs(d) < 0.5:
        return m
    return _dilate(m, _disque(d)) if d > 0 else _erode(m, _disque(-d))


# ───────────────────────────── sources ─────────────────────────────
def rendu_svg(path, px):
    """SVG -> RGBA dont la plus grande dimension vaut px."""
    doc = pymupdf.open(path)
    pg = doc[0]
    z = px / max(pg.rect.width, pg.rect.height)
    pix = pg.get_pixmap(matrix=pymupdf.Matrix(z, z), alpha=True)
    return Image.frombytes("RGBA", (pix.width, pix.height), pix.samples)


def encre(im, mode):
    """Masque de l'encre du blason (bool)."""
    a = np.asarray(im).astype(np.float32) / 255
    lum = a[..., :3].mean(axis=2)
    if mode == "noir":
        return (a[..., 3] > 0.5) & (lum < 0.22)
    return (a[..., 3] > 0.5) & (lum < 0.7)


def recadre(m, cadre=CADRE):
    """Recadre le masque sur son contenu, à l'échelle cadre x cadre (px finaux), centré dans 48x48 (x K)."""
    ys, xs = np.nonzero(m)
    m = m[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = m.shape
    s = cadre * K / max(h, w)
    nw, nh = max(1, round(w * s)), max(1, round(h * s))
    im = Image.fromarray((m * 255).astype(np.uint8), "L").resize((nw, nh), Image.LANCZOS)
    out = Image.new("L", (T * K, T * K), 0)
    out.paste(im, ((T * K - nw) // 2, (T * K - nh) // 2))
    return np.asarray(out) > 127, s


def masque_kanji(car):
    """Kanji (Yu Gothic Bold) dans un cercle, à la manière du 医 cerclé des Unités médicales."""
    N = T * K
    f = ImageFont.truetype(POLICE_KANJI, int(N * 0.62))
    g = Image.new("L", (N, N), 0)
    ImageDraw.Draw(g).text((N / 2, N / 2), car, font=f, fill=255, anchor="mm")
    gm = np.asarray(g) > 127
    ys, xs = np.nonzero(gm)
    gm = gm[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    cote = 26 * K                               # le kanji tient dans un carré de 26 px (cercle de 46)
    h, w = gm.shape
    s = cote / max(h, w)
    im = Image.fromarray((gm * 255).astype(np.uint8), "L").resize((round(w * s), round(h * s)), Image.LANCZOS)
    kj = np.zeros((N, N), bool)
    km = np.asarray(im) > 127
    y0, x0 = (N - km.shape[0]) // 2, (N - km.shape[1]) // 2
    kj[y0:y0 + km.shape[0], x0:x0 + km.shape[1]] = km
    return kj


def anneau(r_ext, ep):
    N = T * K
    yy, xx = np.mgrid[0:N, 0:N]
    d = np.hypot(xx - N / 2 + 0.5, yy - N / 2 + 0.5)
    return (d <= r_ext * K) & (d >= (r_ext - ep) * K)


# ───────────────────────────── les emblèmes ─────────────────────────────
def masque_clan(cle):
    r = CLANS[cle]
    if "svg" in r:
        im = rendu_svg(CANON / r["svg"], 1600)
        m = encre(im, r.get("encre", "encre"))
        m, s = recadre(m, r.get("cadre", CADRE))
        ep_vis = r.get("ep", EP)
        if ep_vis is not None:
            m = ajuste(m, epaisseur(m), ep_vis * K)
        if r.get("fins"):                       # épaissit les seuls traits fins (le cercle de la lune Yotsuki)
            seuil, plus = r["fins"]
            d = _disque(seuil * K)
            fins = m & ~_dilate(_erode(m, d), d)
            m = m | _dilate(fins, _disque(plus * K))
        if r.get("gras"):                       # grossit tout le dessin (les deux points des Kaguya)
            m = _dilate(m, _disque(r["gras"] * K))
        return m
    if "kanji" in r:
        kj = masque_kanji(r["kanji"])
        kj = ajuste(kj, epaisseur(kj), r.get("ep_kanji", 2.4) * K)
        return kj | anneau(23, EP)
    return MAISON()


def MAISON():
    """« Autre » : maison au toit à pignon (une famille, un nom à écrire) — pas de clan, pas de canon."""
    N = T * K
    im = Image.new("L", (N, N), 0)
    d = ImageDraw.Draw(im)
    w = int(round(EP * K))

    def ligne(pts):
        q = [(x * K, y * K) for x, y in pts]
        d.line(q, fill=255, width=w, joint="curve")
        for x, y in q:
            d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=255)
    ligne([(3.5, 21), (24, 6.5), (44.5, 21)])
    ligne([(10, 21), (10, 44), (38, 44), (38, 21)])
    ligne([(20, 44), (20, 31), (28, 31), (28, 44)])
    return np.asarray(im) > 127


def rendu(m) -> Image.Image:
    petit = Image.fromarray((m * 255).astype(np.uint8), "L").resize((T, T), Image.LANCZOS)
    a = np.asarray(petit).astype(np.float32)
    a = np.clip((a - 30) / (225 - 30), 0, 1) * 255
    out = np.zeros((T, T, 4), np.uint8)
    out[..., :3] = 255
    out[..., 3] = a.astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def emblem(cle) -> Image.Image:
    return rendu(masque_clan(cle))


def stats(im):
    a = np.asarray(im)[..., 3]
    return (a > 128).sum(), im.getbbox()


# ───────────────────────────── planche de contrôle ─────────────────────────────
def planche(chemin_png, ims, refs=None):
    """Pour chaque village puis chaque clan : référence canon (SVG d'origine, en couleurs, sur papier) |
    notre emblème x3 crème sur laque, puis à taille réelle (48, 24, 16 px). Statut sous le nom."""
    try:
        f = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 13)
        fk = ImageFont.truetype(r"C:\Windows\Fonts\YuGothB.ttc", 15)
        fg = ImageFont.truetype(POLICE_KANJI, 70)
    except OSError:
        f = fk = fg = ImageFont.load_default()
    cw, ch, cols = 400, 236, 4
    items = [("v", v) for v in VILLAGES] + [("c", k) for k in CLANS]
    rows = math.ceil(len(items) / cols)
    W = Image.new("RGBA", (cols * cw, rows * ch), (28, 18, 22, 255))
    d = ImageDraw.Draw(W)
    creme, gris, papier = (245, 233, 208), (180, 160, 130), (236, 228, 212, 255)

    def teinte(im, rgb):
        a = np.asarray(im).astype(np.float32)
        a[..., 0], a[..., 1], a[..., 2] = rgb
        return Image.fromarray(a.astype(np.uint8), "RGBA")

    def ref_svg(path):
        im = rendu_svg(path, 136)
        c = Image.new("RGBA", (144, 144), papier)
        c.alpha_composite(im, ((144 - im.width) // 2, (144 - im.height) // 2))
        return c

    for i, (genre, k) in enumerate(items):
        x, y = (i % cols) * cw + 10, (i // cols) * ch + 8
        # gauche : canon
        ref = None
        if genre == "v":
            noms = {"konoha": "Konohagakure", "suna": "Sunagakure", "kiri": "Kirigakure", "kumo": "Kumogakure",
                    "iwa": "Iwagakure"}
            p = LOGOS_VILLAGES / f"{noms.get(k, '-')}.svg"
            if not p.exists() and refs:
                p = Path(refs) / f"{k}.svg"
            if p.exists():
                ref = ref_svg(p)
            im = Image.open(RES / f"esc/village_{k}.png").convert("RGBA")
        else:
            r = CLANS[k]
            if "svg" in r:
                ref = ref_svg(CANON / r["svg"])
            im = ims[k]
        if ref is None:
            ref = Image.new("RGBA", (144, 144), papier)
            rd = ImageDraw.Draw(ref)
            if genre == "c" and "kanji" in CLANS[k]:
                rd.text((72, 66), CLANS[k]["kanji"], font=fg, fill=(20, 20, 20), anchor="mm")
                rd.text((72, 128), "(pas de blason)", font=f, fill=(120, 40, 40), anchor="mm")
            else:
                rd.text((72, 72), "pas de blason", font=f, fill=(120, 40, 40), anchor="mm")
        W.alpha_composite(ref, (x, y))
        # droite : nous
        W.alpha_composite(teinte(im, creme).resize((144, 144), Image.NEAREST), (x + 152, y))
        d.rectangle((x + 151, y - 1, x + 296, y + 144), outline=(70, 50, 50, 255))
        W.alpha_composite(teinte(im, creme), (x + 304, y))
        W.alpha_composite(teinte(im, creme).resize((24, 24), Image.BOX), (x + 304, y + 56))
        W.alpha_composite(teinte(im, (217, 169, 94)).resize((16, 16), Image.BOX), (x + 334, y + 60))
        nom = ("VILLAGE " + k) if genre == "v" else k
        d.text((x, y + 150), nom, font=f, fill=creme)
        if genre == "c":
            n, bb = stats(im)
            d.text((x + 150, y + 150), f"{n} px  {bb[2] - bb[0]}x{bb[3] - bb[1]}", font=f, fill=gris)
            st = CLANS[k]["statut"]
            lignes, cur = [], ""
            for mot in st.split(" "):
                if len(cur) + len(mot) > 40:
                    lignes.append(cur); cur = mot
                else:
                    cur = (cur + " " + mot).strip()
            lignes.append(cur)
            col = (230, 120, 110) if st.startswith("SANS") or "pas de blason" in st else gris
            for j, l in enumerate(lignes[:3]):
                d.text((x, y + 168 + j * 18), l, font=fk, fill=col)
    W.save(chemin_png)
    return chemin_png


def main(argv):
    pl = refs = None
    for opt in ("--planche", "--refs"):
        if opt in argv:
            i = argv.index(opt)
            if opt == "--planche":
                pl = argv[i + 1]
            else:
                refs = argv[i + 1]
            argv = argv[:i] + argv[i + 2:]
    cles = argv or list(CLANS)
    SORTIE.mkdir(parents=True, exist_ok=True)
    ims = {}
    for k in CLANS:
        ims[k] = emblem(k)
        if k in cles:
            ims[k].save(SORTIE / f"clan_{k}.png")
            n, bb = stats(ims[k])
            print(f"clan_{k}.png  {n:4d} px  boîte {bb}")
    pl = pl or os.path.join(tempfile.gettempdir(), "planche_clans.png")
    print("planche :", planche(pl, ims, refs))


if __name__ == "__main__":
    main(sys.argv[1:])
