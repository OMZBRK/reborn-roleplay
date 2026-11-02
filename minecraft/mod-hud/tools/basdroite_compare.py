# -*- coding: utf-8 -*-
"""
basdroite_compare.py : colonne bas-droite du lot 2 (étape 2b), captures du jeu contre la maquette, au pixel près.

    python basdroite_compare.py [dossier des captures] [--journal <latest.log>] [--maquettes <dossier 17_lot2_ketto>]
                                [--sortie <dossier>]

Le banc REBORN_FEATURE_PREVIEW=basdroite (audit/FeaturePreview) prend chaque état fixe deux fois de suite, monde gelé :
basdroite_<cas>_sans.png (colonne masquée, rien qui parle, aucune notification) puis basdroite_<cas>.png. Ce script
repose la maquette — qui_parle(), notifs() et rappels() de _maquettes_lot2.py, importées par leur chemin, avec le moteur
de référence _texte_raster.py — sur la capture nue, avec le contenu du cas (orateurs, vumètres et notifications de la
table CAS ; rappels et noms de touches relus dans le journal du jeu, lignes « [basdroite-banc] <cas> … rappels « … » »,
car la touche affichée dépend de la disposition du clavier du poste), puis compare la zone bas-droite (500 × 340 px) :

- dans l'empreinte (pixels que la maquette modifie, élargis d'1 px) : écart RVB moyen et maximal en 1/255 ;
  cible : moyen < 1/255 au-delà de la dérive du fond ET max < 16/255 ;
- hors de l'empreinte : le jeu ne doit rien dessiner de plus (aucun pixel à plus de 16/255 de la capture nue) ;
- planche <sortie>/basdroite_compare_<cas>.png : jeu | maquette sur le même fond | écart ×8, zoom ×2 au plus proche.

Écrit aussi <sortie>/basdroite_compare.txt. Sortie 1 si un cas dépasse la cible. Python 3.14, Pillow, numpy, fontTools.
"""
from __future__ import annotations

import argparse
import importlib.util
import os
import re
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
CAPTURES = os.path.normpath(os.path.join(ICI, "..", "run", "screenshots"))
JOURNAL = os.path.normpath(os.path.join(ICI, "..", "run", "logs", "latest.log"))
MAQUETTES = os.environ.get("REBORN_MAQUETTES_LOT2") or \
    r"C:\Users\omarb\Desktop\Reborn - Gestion\Maquettes\gameplan\17_lot2_ketto"
CIBLE_MOY, CIBLE_MAX = 1.0, 16.0
ZONE_L, ZONE_H = 500, 340


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


def table_cas(mq):
    """Contenu de chaque cas du banc (qui parle du bas vers le haut, notifications de la plus récente à la plus
    ancienne, rappels imposés quand le journal ne suffit pas)."""
    aki = lambda puce, vu=(4, 6, 5, 3, 2): dict(nom="Aki Uchiha", puce=puce, vu=vu)
    ren = dict(nom="Ren Hyūga", vu=(3, 5, 6, 4, 3))
    complet = dict(orateurs=[aki(mq.VERT), ren, dict(nom="Kozue Nara", vu=(2, 4, 3, 2, 2))], notifs=mq.NOTIFS_EXEMPLE)
    return {
        "01_personne": dict(),
        "02_un_orateur": dict(orateurs=[ren]),
        "03_inconnu": dict(orateurs=[ren, dict(inconnu=True, vu=(2, 4, 5, 3, 2)), dict(nom="Kozue Nara", vu=(4, 6, 3, 5, 2))]),
        "04_soi_chuchote": dict(orateurs=[aki(mq.VERT)]),
        "05_soi_normale": dict(orateurs=[aki(mq.JAUNE)]),
        "06_soi_criee": dict(orateurs=[aki(mq.ROUGE_MIC, (5, 6, 6, 4, 3))]),
        "07_loin": dict(orateurs=[aki(mq.VERT), dict(ren, a=0.60)]),
        "08_complet": complet,
        "10_animation": dict(),
        "11_course": dict(),
        "12_porte_descendre": dict(),
        "13_meditation": dict(),
        "14_staff": dict(),
        "15_azerty": dict(),
        "16_clic_m": dict(),
        "17_cumul_4": dict(),
        "18_cumul_notifs": dict(notifs=mq.NOTIFS_EXEMPLE),
        "19_voix_chuchotee": dict(rappels=[("Voix : chuchotée", "H", mq.VERT)]),
        "19_voix_normale": dict(rappels=[("Voix : normale", "H", mq.JAUNE)]),
        "19_voix_criee": dict(rappels=[("Voix : criée", "H", mq.ROUGE_MIC)]),
        "23_1366": complet,
        "24_echelle4": complet,
        "28_ciel_complet": complet,
        "29_ciel_cumul_4": dict(orateurs=[aki(mq.ROUGE_MIC, (5, 6, 6, 4, 3)), dict(ren, a=0.60)]),
    }


RE_JOURNAL = re.compile(r"\[basdroite-banc\] basdroite_(\S+) \(.*?rappels « (.*?) »")
RE_RAPPEL = re.compile(r"(.+?) \[(.+?)\]$")


def rappels_du_journal(chemin):
    """Dernière liste de rappels journalisée pour chaque cas : [(libellé, touche)] du bas vers le haut."""
    out = {}
    if not os.path.isfile(chemin):
        return out
    with open(chemin, encoding="utf-8", errors="replace") as f:
        for ligne in f:
            m = RE_JOURNAL.search(ligne)
            if not m or m.group(1).endswith("_sans"):
                continue
            items = []
            if m.group(2) != "aucun":
                for morceau in m.group(2).split(" · "):
                    r = RE_RAPPEL.match(morceau.strip())
                    if r:
                        items.append((r.group(1), r.group(2)))
            out[m.group(1)] = items
    return out


def elargir(m):
    e = m.copy()
    e[1:, :] |= m[:-1, :]
    e[:-1, :] |= m[1:, :]
    e[:, 1:] |= m[:, :-1]
    e[:, :-1] |= m[:, 1:]
    return e


def planche(jeu, maq, chemin, z=2):
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
    ap.add_argument("--journal", default=JOURNAL)
    ap.add_argument("--maquettes", default=MAQUETTES)
    ap.add_argument("--sortie", default=None)
    args = ap.parse_args()
    sortie = args.sortie or args.captures
    os.makedirs(sortie, exist_ok=True)
    mq = charger_maquettes(args.maquettes)
    p = mq.tr.charger(mq.POLICE_RECIT)
    table = table_cas(mq)
    journal = rappels_du_journal(args.journal)
    cas = sorted(f[len("basdroite_"):-len("_sans.png")] for f in os.listdir(args.captures)
                 if f.startswith("basdroite_") and f.endswith("_sans.png"))
    if not cas:
        sys.exit("aucune paire basdroite_*_sans.png / basdroite_*.png dans %s" % args.captures)
    lignes, echec = [], False
    for c in cas:
        if c not in table:
            continue
        r = table[c]
        nu = Image.open(os.path.join(args.captures, "basdroite_%s_sans.png" % c)).convert("RGBA")
        jeu = Image.open(os.path.join(args.captures, "basdroite_%s.png" % c)).convert("RGBA")
        W, H = nu.size
        g = mq.G if (W, H) == (1920, 1080) else mq.Geo(W, H, 1.0)
        rap = r.get("rappels")
        if rap is None:
            rap = journal.get(c, [])
            if r.get("notifs"):
                rap = rap[:3]
        sc = nu.copy()
        mq.qui_parle(sc, p, r.get("orateurs", []), g)
        if r.get("notifs"):
            mq.notifs(sc, r["notifs"], g)
        if rap:
            mq.rappels(sc, rap, g)
        boite = (W - ZONE_L, H - ZONE_H, W, H)
        A = np.asarray(jeu.crop(boite).convert("RGB"), np.int32)
        B = np.asarray(sc.crop(boite).convert("RGB"), np.int32)
        N = np.asarray(nu.crop(boite).convert("RGB"), np.int32)
        emp = elargir(np.abs(B - N).max(axis=2) > 0)
        d = np.abs(A - B).max(axis=2)
        moy = float(d[emp].mean()) if emp.any() else 0.0
        mx = int(d[emp].max()) if emp.any() else 0
        hors = np.abs(A - N).max(axis=2)[~emp]
        derive, hors_max = float(hors.mean()), int(hors.max())
        ok = moy < CIBLE_MOY + derive and mx < CIBLE_MAX and hors_max < CIBLE_MAX
        echec |= not ok
        lignes.append("%-20s colonne : moyen %5.2f/255 max %3d/255 (%5d px) · hors colonne : max %3d/255, dérive %4.2f · "
                      "%s · rappels %s" % (c, moy, mx, int(emp.sum()), hors_max, derive, "OK" if ok else "HORS CIBLE",
                                            [x[0] + " [" + x[1] + "]" for x in rap]))
        planche(A, B, os.path.join(sortie, "basdroite_compare_%s.png" % c))
    rapport = "\n".join(lignes)
    print(rapport)
    with open(os.path.join(sortie, "basdroite_compare.txt"), "w", encoding="utf-8") as f:
        f.write(rapport + "\n")
    sys.exit(1 if echec else 0)


if __name__ == "__main__":
    main()
