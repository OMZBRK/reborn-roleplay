# -*- coding: utf-8 -*-
"""
gen_oreillette_nexo.py — l'oreillette de la radio (docs/gameplay/RADIO.md §3) pour le PACK NEXO, jamais dans les
assets du mod (règle « items 3D = pack Nexo ») : écouteur laqué noir cerclé de laiton, crochet d'oreille, fil vers un
micro à pince laqué vermillon. Portée dans l'emplacement cosmétique Boucle d'oreille (ShinobiRadio).

Écrit dans minecraft/server-config/nexo/pack/assets/reborn/ :
  textures/item/oreillette.png (16×16), models/item/oreillette.json (cubes), items/oreillette.json
  (définition d'objet pour l'item_model reborn:oreillette, repli de /radio donner sans Nexo).
L'entrée Nexo « oreillette » est dans minecraft/server-config/nexo/items/reborn_cosmetiques.yml.

Usage (depuis minecraft/mod-hud) :  python tools/gen_oreillette_nexo.py
"""
from __future__ import annotations

import json
import os

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(ICI, "..", "..", "server-config", "nexo", "pack", "assets", "reborn")


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# Quatre zones 4×4 (aplats propres, un léger modelé : clair en haut à gauche, sombre en bas à droite).
ZONES = {
    "laque": (0, 0, ["0E0A0C", "1A1214", "2A1C20", "4A3438"]),
    "laiton": (4, 0, ["5E4626", "8E6A30", "D9A95E", "F2D08A"]),
    "fil": (8, 0, ["141416", "222226", "2E2E34", "3E3E46"]),
    "vermillon": (12, 0, ["5A0E18", "8E1628", "B8323E", "D85A5E"]),
}


def texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for _, (u, v, pal) in ZONES.items():
        for y in range(4):
            for x in range(4):
                k = 2 - (x + y) // 3          # 2 en haut à gauche → 0 en bas à droite
                if x == 0 and y == 0:
                    k = 3                     # reflet
                px[u + x, v + y] = hx(pal[max(0, min(3, k))])
    return im


def faces(zone):
    u, v, _ = ZONES[zone]
    uv = [u, v, u + 4, v + 4]
    return {d: {"uv": uv, "texture": "#0"} for d in ("north", "east", "south", "west", "up", "down")}


def modele():
    el = [
        {"name": "cerclage", "from": [6, 6, 7.5], "to": [10, 10, 8.5], "faces": faces("laiton")},
        {"name": "ecouteur", "from": [6.5, 6.5, 7], "to": [9.5, 9.5, 9], "faces": faces("laque")},
        {"name": "crochet", "from": [7.5, 9.5, 7.6], "to": [8.5, 11.5, 8.4], "faces": faces("laque")},
        {"name": "fil", "from": [7.75, 1.5, 7.75], "to": [8.25, 6.5, 8.25], "faces": faces("fil")},
        {"name": "micro", "from": [7.25, 0, 7.25], "to": [8.75, 1.5, 8.75], "faces": faces("vermillon")},
    ]
    return {
        "credit": "Reborn — oreillette radio (tools/gen_oreillette_nexo.py, 2026-10-14)",
        "texture_size": [16, 16],
        "textures": {"0": "reborn:item/oreillette", "particle": "reborn:item/oreillette"},
        "elements": el,
        "display": {
            "gui": {"rotation": [25, -40, 0], "translation": [0, 1.5, 0], "scale": [1.7, 1.7, 1.7]},
            "ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "fixed": {"scale": [1.3, 1.3, 1.3]},
            "thirdperson_righthand": {"translation": [0, 2.5, 1], "scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, -30, 0], "translation": [1, 3, 0], "scale": [0.8, 0.8, 0.8]},
            "head": {"translation": [0, 0, 0], "scale": [1, 1, 1]},
        },
    }


def main():
    tex = os.path.join(PACK, "textures", "item")
    mod = os.path.join(PACK, "models", "item")
    items = os.path.join(PACK, "items")
    for d in (tex, mod, items):
        os.makedirs(d, exist_ok=True)
    texture().save(os.path.join(tex, "oreillette.png"), optimize=True)
    with open(os.path.join(mod, "oreillette.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(modele(), f, indent=1, ensure_ascii=False)
    with open(os.path.join(items, "oreillette.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "reborn:item/oreillette"}}, f, indent=2)
    print("Oreillette écrite dans", os.path.normpath(PACK))


if __name__ == "__main__":
    main()
