# -*- coding: utf-8 -*-
"""
teinture_bandeau_nexo.py — rend TEINTABLE le bandeau de l'utilisateur (objet Nexo ``bandeau_ninja``, modèle Blockbench
``reborn:item/bandeau``) SANS toucher à sa forme : seul le tissu prend la couleur, la plaque de métal garde la sienne.

Ce que fait le script (pack Nexo du dépôt : minecraft/server-config/nexo/pack/assets/reborn/) :
  1. models/item/bandeau.json : ajoute ``"tintindex": 0`` à chaque face des éléments du TISSU (tous les éléments hors
     du groupe Blockbench ``metal``). Édition ligne à ligne : la mise en forme de l'export Blockbench est conservée,
     rien d'autre ne change (vérifié en relisant le JSON).
  2. textures/item/texturebandeau.png : les pixels du TISSU (ceux que les faces du tissu échantillonnent) passent en
     niveaux de gris clairs en gardant leur ombrage relatif ; la plaque et le reste de l'atlas ne bougent pas. Le jeu
     MULTIPLIE ce gris par la couleur de l'objet (composant ``minecraft:dyed_color``, source de teinte ``minecraft:dye``
     que Nexo génère dès que l'objet porte ``color:``) : un tissu presque noir resterait noir, d'où le gris clair.
  3. L'original est gardé : textures/item/texturebandeau_origine.png (créé au premier passage, jamais réécrit). Le
     script repart TOUJOURS de lui : on peut le relancer autant de fois qu'on veut.
  4. Affiche la couleur ``color:`` à poser sur ``bandeau_ninja`` (items/reborn_cosmetiques_ninja.yml) pour que, sans
     teinte choisie, le bandeau ait exactement l'air d'avant (écart ≤ 1/255 par canal), et l'écart mesuré.

Après un nouvel export Blockbench du bandeau : déposer bandeau.json + texturebandeau.png (le nouvel original) dans le
pack, supprimer texturebandeau_origine.png, relancer ce script, reporter la couleur affichée dans le YAML (et dans
TeintureScreen.CANON[0] si elle change).

Revenir en arrière (bandeau non teintable) : ``--retirer`` (enlève les tintindex, remet la texture d'origine) puis
retirer ``color:`` du YAML.

Usage (depuis minecraft/mod-hud) :  python tools/teinture_bandeau_nexo.py [--retirer]
"""
from __future__ import annotations

import json
import math
import os
import re
import shutil
import sys

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(ICI, "..", "..", "server-config", "nexo", "pack", "assets", "reborn"))
MODELE = os.path.join(PACK, "models", "item", "bandeau.json")
TEXTURE = os.path.join(PACK, "textures", "item", "texturebandeau.png")
ORIGINE = os.path.join(PACK, "textures", "item", "texturebandeau_origine.png")

GROUPE_FIXE = "metal"   # groupe Blockbench qui NE se teint PAS (la plaque)
GRIS_BASE = 224         # gris donné à la couleur dominante du tissu (le plus clair du tissu ≈ 240)

FACE = re.compile(r'^(\s*)"(north|south|east|west|up|down)": \{(.*)\}(,?)\s*$')


def indices_fixes(modele: dict) -> set[int]:
    fixes: set[int] = set()

    def parcourir(g, dedans):
        if isinstance(g, int):
            if dedans:
                fixes.add(g)
            return
        d = dedans or g.get("name") == GROUPE_FIXE
        for c in g.get("children", []):
            parcourir(c, d)

    for g in modele.get("groups", []):
        parcourir(g, False)
    return fixes


def editer_modele(texte: str, tissu: set[int], ajouter: bool) -> str:
    """Ajoute (ou retire) "tintindex": 0 sur les faces des éléments du tissu, ligne à ligne."""
    lignes = texte.split("\n")
    dans_elements, idx, prof = False, -1, 0
    for i, l in enumerate(lignes):
        s = l.strip()
        if not dans_elements:
            if s.startswith('"elements"'):
                dans_elements, prof = True, 0
            continue
        # profondeur d'accolades à l'intérieur du tableau elements
        if prof == 0 and s.startswith("{"):
            idx += 1
        if prof == 0 and s.startswith("]"):
            break
        m = FACE.match(l)
        if m and idx in tissu:
            corps = m.group(3)
            corps = re.sub(r',\s*"tintindex":\s*-?\d+', "", corps)
            if ajouter:
                corps += ', "tintindex": 0'
            lignes[i] = f'{m.group(1)}"{m.group(2)}": {{{corps}}}{m.group(4)}'
        prof += l.count("{") - l.count("}")
    return "\n".join(lignes)


def pixels_faces(modele: dict, elements: set[int], taille: int) -> set[tuple[int, int]]:
    k = taille / 16.0
    px: set[tuple[int, int]] = set()
    for i, e in enumerate(modele["elements"]):
        if i not in elements:
            continue
        for f in e["faces"].values():
            x0, y0, x1, y1 = f["uv"]
            a, b = sorted((x0, x1))
            c, d = sorted((y0, y1))
            xa, ya = math.floor(a * k + 1e-6), math.floor(c * k + 1e-6)
            xb, yb = max(math.ceil(b * k - 1e-6), xa + 1), max(math.ceil(d * k - 1e-6), ya + 1)
            px.update((x, y) for x in range(xa, xb) for y in range(ya, yb))
    return px


def main() -> None:
    retirer = "--retirer" in sys.argv
    with open(MODELE, encoding="utf-8") as fh:
        texte = fh.read()
    modele = json.loads(texte)
    fixes = indices_fixes(modele)
    tissu = set(range(len(modele["elements"]))) - fixes
    if not fixes:
        sys.exit(f"groupe « {GROUPE_FIXE} » introuvable dans {MODELE} : rien n'est fait")

    nouveau = editer_modele(texte, tissu, not retirer)
    verif = json.loads(nouveau)
    for i, (e0, e1) in enumerate(zip(modele["elements"], verif["elements"])):
        for nom, f in e1["faces"].items():
            f0 = dict(e0["faces"][nom])
            f0.pop("tintindex", None)
            attendu = dict(f0, tintindex=0) if (i in tissu and not retirer) else f0
            assert f == attendu, (i, nom, f, attendu)
        assert {k: v for k, v in e1.items() if k != "faces"} == {k: v for k, v in e0.items() if k != "faces"}
    assert {k: v for k, v in verif.items() if k != "elements"} == {k: v for k, v in modele.items() if k != "elements"}
    with open(MODELE, "w", encoding="utf-8", newline="") as fh:
        fh.write(nouveau)

    if not os.path.exists(ORIGINE):
        shutil.copyfile(TEXTURE, ORIGINE)
    if retirer:
        shutil.copyfile(ORIGINE, TEXTURE)
        print("bandeau remis tel quel (sans tintindex, texture d'origine) ; retirer color: du YAML")
        return

    im = Image.open(ORIGINE).convert("RGBA")
    w, h = im.size
    src = im.load()
    zone_t = pixels_faces(modele, tissu, w)
    zone_f = pixels_faces(modele, fixes, w)
    if zone_t & zone_f:
        sys.exit(f"{len(zone_t & zone_f)} pixels partagés entre tissu et plaque : à séparer dans Blockbench")

    from collections import Counter
    couleurs = Counter(src[p] for p in zone_t if src[p][3] > 0)
    base = couleurs.most_common(1)[0][0]
    moy_base = sum(base[:3]) / 3.0
    teinte = tuple(min(255, round(c * 255 / GRIS_BASE)) for c in base[:3])

    out = im.copy()
    dst = out.load()
    ecart = 0
    for p in zone_t:
        r, g, b, a = src[p]
        if a == 0:
            continue
        gris = max(0, min(255, round((r + g + b) / 3.0 / moy_base * GRIS_BASE)))
        dst[p] = (gris, gris, gris, a)
        rendu = tuple(round(gris * t / 255) for t in teinte)
        ecart = max(ecart, *(abs(x - y) for x, y in zip(rendu, (r, g, b))))
    out.save(TEXTURE)

    print(f"tissu : {len(tissu)} éléments, {len(zone_t)} px ; plaque ({GROUPE_FIXE}) : {len(fixes)} éléments, inchangée")
    print(f"tissu dominant #{base[0]:02X}{base[1]:02X}{base[2]:02X} -> gris {GRIS_BASE}")
    print(f'color: "#{teinte[0]:02X}{teinte[1]:02X}{teinte[2]:02X}"   (écart max au rendu d\'origine : {ecart}/255)')


if __name__ == "__main__":
    main()
