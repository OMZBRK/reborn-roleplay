# -*- coding: utf-8 -*-
"""
gen_monnaie_nexo.py — objets PROVISOIRES de la monnaie (docs/gameplay/MONNAIE_ET_BANQUE.md, 15/10) pour le PACK
NEXO, jamais dans les assets du mod (règle « items 3D = pack Nexo »). L'utilisateur modélise les vrais objets dans
Blockbench : ce script ne pose que des sprites 16 × 16 (item/generated) aux identifiants STABLES, à remplacer en gardant
les mêmes noms de fichier.

Écrit dans minecraft/server-config/nexo/pack/assets/reborn/ :
  textures/item/<id>.png, models/item/<id>.json, items/<id>.json   pour
  - bourse_plate   (bourse < 100 ryo : kinchaku presque vide)
  - bourse_garnie  (< 5 000)
  - bourse_lourde  (5 000 et plus)
  - testament      (document scellé, catalogue / remise RP ; le testament lui-même est un enregistrement du guichet)
La bourse est créée par ShinobiCore (PAPER + item_model reborn:bourse_<état> + montant en PDC) ; l'entrée Nexo
(minecraft/server-config/nexo/items/reborn_monnaie.yml) ne sert qu'au catalogue.

Usage (depuis minecraft/mod-hud) :  python tools/gen_monnaie_nexo.py
"""
from __future__ import annotations

import json
import os

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(ICI, "..", "..", "server-config", "nexo", "pack", "assets", "reborn")

# Palette des maquettes (Maquettes/gameplan/19_monnaie/_maquettes_monnaie.py) : tissu laqué rouge, cordon d'or.
PAL = {"O": "3A0A10", "d": "6E0F1E", "m": "A0182B", "l": "C2364A", "h": "E07A80",
       "g": "8E6A30", "G": "D9A95E", "Y": "F2D08A",
       "p": "D8C8A4", "P": "F0E4C8", "q": "A89070", "k": "5A4630", "r": "A0182B", "R": "C2364A"}

SPRITES = {
    "bourse_plate": [
        "................",
        "................",
        "................",
        "................",
        ".....O.OO.O.....",
        ".....OmlmmO.....",
        "......gGYg......",
        ".....OdGmdOG....",
        "....OdmllmdOY...",
        "....OdlhlmmdO...",
        "....OdllYGmdO...",
        "....OdmlGgddO...",
        "....OddmmmddO...",
        ".....OOddddO....",
        "......OOOO......",
        "................",
    ],
    "bourse_garnie": [
        "................",
        "................",
        ".....O.OO.O.....",
        "....OlOmlOmO....",
        "....OdmlmmdO....",
        ".....gGYGGg.....",
        "....OdmGmmdOG...",
        "...OdmllmmmdOY..",
        "..OdmlhlllmmdO..",
        "..OdlhllllmmdO..",
        "..OdlllYGmmmdO..",
        "..OdmllGgmmddO..",
        "..OddmmmmmmddO..",
        "...OddmmmmddO...",
        "....OOddddOO....",
        "......OOOO......",
    ],
    "bourse_lourde": [
        "................",
        ".......YG.......",
        "....O.OGgO.O....",
        "...OlOmlmOmlO...",
        "...OdmlmmmmdO...",
        "....gGYGGYGg....",
        "...OdmmGmmmdOG..",
        "..OdmllmmmmmdOY.",
        ".OdmlhllllmmmdO.",
        ".OdlhlllllmmmdO.",
        ".OdllllYGmmmmdO.",
        ".OdmlllGgmmmddO.",
        ".OddmmmmmmmmddO.",
        "..OddmmmmmmmdO..",
        "...OOdddddddO...",
        ".....OOOOOOO....",
    ],
    # rouleau de papier scellé d'un cachet de cire rouge
    "testament": [
        "................",
        "................",
        "..kqqqqqqqqqqk..",
        ".kpPPPPPPPPPPpk.",
        ".kpPqqqqqqqqPpk.",
        ".kpPPPPPPPPPPpk.",
        ".kpPqqqqqqPPPpk.",
        ".kpPPPPPPPPPPpk.",
        ".kpPqqqqqPPPPpk.",
        ".kpPPPPPPPrrPpk.",
        ".kpPPPPPPrRRrpk.",
        ".kpPPPPPPrRRrpk.",
        "..kqqqqqqqrrqk..",
        "................",
        "................",
        "................",
    ],
}


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def sprite(rows):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in PAL:
                im.putpixel((x, y), hx(PAL[ch]))
    return im


def main():
    for d in ("textures/item", "models/item", "items"):
        os.makedirs(os.path.join(PACK, d), exist_ok=True)
    for nom, rows in SPRITES.items():
        sprite(rows).save(os.path.join(PACK, "textures", "item", nom + ".png"))
        modele = {
            "credit": "Reborn — provisoire (tools/gen_monnaie_nexo.py, 2026-10-15), à remplacer par le modèle Blockbench",
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "reborn:item/" + nom},
        }
        with open(os.path.join(PACK, "models", "item", nom + ".json"), "w", encoding="utf-8") as f:
            json.dump(modele, f, indent=2, ensure_ascii=False)
            f.write("\n")
        item = {"model": {"type": "minecraft:model", "model": "reborn:item/" + nom}}
        with open(os.path.join(PACK, "items", nom + ".json"), "w", encoding="utf-8") as f:
            json.dump(item, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print("écrit", nom)


if __name__ == "__main__":
    main()
