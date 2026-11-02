# -*- coding: utf-8 -*-
"""
gen_kern.py : tables de crénage binaires de ui/TexteRaster (lot 2 « Reprises Ketto », brief §7.1).

Pour chaque TTF lu par TexteRaster, écrit à côté un fichier <nom>.kern : les paires (glyphe 1, glyphe 2, valeur)
NON NULLES, triées, que Java lit une fois puis interroge par recherche dichotomique, sans allocation.

Les valeurs viennent du moteur de référence des maquettes, Maquettes/gameplan/17_lot2_ketto/_texte_raster.py :
on importe son `Police`, donc sa fonction `_lire_crenage` (fonction 'kern' du GPOS, script latn ou DFLT, langue FRA
ou langue par défaut, PairPos formats 1 et 2, lookups Extension ; première sous-table applicable de chaque lookup,
somme des lookups ; table 'kern' à défaut), et on interroge `Police.crenage(g1, g2)` pour chaque paire du
répertoire. Java et Python ont donc exactement les mêmes paires, sans recopie de la logique.

Répertoire (REPERTOIRE ci-dessous) : latin de base, latin-1, latin étendu A (voyelles à macron comprises),
ponctuation générale U+2010 à U+203A (tirets, guillemets, puce, points de suspension, espace fine insécable), €
et ™, plus .notdef et l'espace (l'espace insécable U+00A0 prend le glyphe de l'espace, comme dans _texte_raster).
Un glyphe hors de ce répertoire s'affiche sans crénage en Java : toute la police serait 7,8 Mo de paires (288 000
paires pour Alegreya Sans, 508 000 pour chaque Inter), contre 0,37 Mo ici.

Format (gros-boutiste) :
    b"RKRN" | u16 version = 1 | u16 unitsPerEm | u32 n | n × (u16 g1, u16 g2, i16 valeur)
triés par (g1, g2). Les identifiants de glyphes sont ceux de la police (ordre de 'glyf', donc l'index FreeType).

    python gen_kern.py                       régénère les quatre tables du mod
    python gen_kern.py --verifier            relit les tables et les compare au moteur, sans rien écrire
    python gen_kern.py --ref <_texte_raster.py>

Il faut Python 3.14, Pillow, numpy et fontTools (ou leur dossier dans REBORN_PYLIB), comme _texte_raster.py.
"""
from __future__ import annotations

import argparse
import importlib.util
import os
import struct
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
FONT = os.path.normpath(os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "font"))
REF_DEFAUT = os.environ.get("REBORN_TEXTE_RASTER") or \
    r"C:\Users\omarb\Desktop\Reborn - Gestion\Maquettes\gameplan\17_lot2_ketto\_texte_raster.py"

# Polices de TexteRaster (même liste que TexteRaster.Police) : chemin relatif à assets/reborn/font.
POLICES = [
    os.path.join("recit", "alegreyasans-bolditalic.ttf"),   # récit (narration, plaques, qui parle)
    "inter-bold.ttf",                                        # titre de l'OST, libellés, touches, notifications
    "inter-medium.ttf",                                      # détail des notifications
    os.path.join("narration", "philosopher-bold.ttf"),      # narration du staff (Philosopher Bold, choix du 02/11)
]

REPERTOIRE = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + list(range(0x2010, 0x203B))
              + [0x20AC, 0x2122])

MAGIQUE, VERSION = b"RKRN", 1


def charger_ref(chemin):
    """Importe _texte_raster.py par son chemin (son dossier est ajouté à sys.path pour ses propres imports)."""
    if not os.path.isfile(chemin):
        sys.exit("moteur de référence introuvable : %s (option --ref ou variable REBORN_TEXTE_RASTER)" % chemin)
    dossier = os.path.dirname(os.path.abspath(chemin))
    sys.dont_write_bytecode = True   # pas de __pycache__ dans le dossier des maquettes
    if dossier not in sys.path:
        sys.path.insert(0, dossier)
    spec = importlib.util.spec_from_file_location("_texte_raster", chemin)
    mod = importlib.util.module_from_spec(spec)
    sys.modules["_texte_raster"] = mod
    spec.loader.exec_module(mod)
    return mod


def paires(tr, ttf):
    """[(g1, g2, valeur)] non nulles, triées, pour les glyphes du répertoire (identifiants de la police)."""
    police = tr.Police(ttf)
    tt = tr.TTFont(ttf)
    noms = {police.cmap[c] for c in REPERTOIRE if c in police.cmap}
    noms.add(".notdef")
    if 0x20 in police.cmap:
        noms.add(police.cmap[0x20])
    ident = {n: tt.getGlyphID(n) for n in noms}
    out = []
    for n1 in noms:
        for n2 in noms:
            v = police.crenage(n1, n2)
            if v:
                if not -32768 <= v <= 32767:
                    sys.exit("valeur hors 16 bits : %s %s %d" % (n1, n2, v))
                out.append((ident[n1], ident[n2], int(v)))
    out.sort()
    return police, out


def ecrire(chemin, upm, liste):
    with open(chemin, "wb") as f:
        f.write(MAGIQUE)
        f.write(struct.pack(">HHI", VERSION, upm, len(liste)))
        for g1, g2, v in liste:
            f.write(struct.pack(">HHh", g1, g2, v))


def lire(chemin):
    with open(chemin, "rb") as f:
        d = f.read()
    if d[:4] != MAGIQUE:
        raise ValueError("%s : en-tête inattendu" % chemin)
    version, upm, n = struct.unpack(">HHI", d[4:12])
    return version, upm, [struct.unpack(">HHh", d[12 + 6 * i:18 + 6 * i]) for i in range(n)]


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--ref", default=REF_DEFAUT, help="chemin de _texte_raster.py (moteur de référence)")
    ap.add_argument("--verifier", action="store_true", help="compare les .kern existants au moteur")
    a = ap.parse_args()
    tr = charger_ref(a.ref)
    echec = False
    for rel in POLICES:
        ttf = os.path.join(FONT, rel)
        kern = os.path.splitext(ttf)[0] + ".kern"
        police, liste = paires(tr, ttf)
        if a.verifier:
            version, upm, lu = lire(kern)
            ok = version == VERSION and upm == police.upm and [tuple(x) for x in lu] == liste
            echec |= not ok
            print("%-36s %s (%d paires)" % (rel, "identique" if ok else "DIFFÉRENT", len(liste)))
        else:
            ecrire(kern, police.upm, liste)
            print("%-36s %6d paires · %7d octets · source %s · UPM %d" % (
                rel, len(liste), 12 + 6 * len(liste), police.source_crenage, police.upm))
    sys.exit(1 if echec else 0)


if __name__ == "__main__":
    main()
