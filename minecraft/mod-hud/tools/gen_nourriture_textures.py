# -*- coding: utf-8 -*-
"""Textures de la nourriture (GAMEPLAN §12, NOURRITURE.md v3 — 14/10). Aucun kanji (DA §3), pixel art léger (DA §6).

1. Icônes d'effets et d'état du HUD (mod) : un fichier par icône, nom stable,
   ``src/main/resources/assets/reborn/textures/gui/effets/<nom>.png`` — pictos 10×10 des maquettes v3
   (``Maquettes/gameplan/12_nourriture/v3/_maquettes_source_v3.py``). Le code (``nourriture/EffetsIcones``) lit la
   taille réelle de chaque fichier : on peut les remplacer par des icônes peintes de 16 ou 32 px (bande verticale
   d'images = animation) sans toucher au code.
2. Objets du pack Nexo (serveur, JAMAIS dans les assets du mod) : sprites 16×16 + modèles ``item/generated`` +
   définitions ``items/<id>.json`` dans ``minecraft/server-config/nexo/pack/assets/reborn/``.

    python tools/gen_nourriture_textures.py
"""
import json
import os

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.join(ICI, "..", "src", "main", "resources", "assets", "reborn", "textures", "gui", "effets")
PACK = os.path.join(ICI, "..", "..", "server-config", "nexo", "pack", "assets", "reborn")


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def grille(rows, pal, w=None):
    w = w or max(len(r) for r in rows)
    im = Image.new("RGBA", (w, len(rows)), (0, 0, 0, 0))
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch in pal:
                im.putpixel((i, j), hexc(pal[ch]))
    return im


COEUR = [".aa..aa...", "abbaabba..", "abcbbbbba.", "abbbbbbba.", "abbbbbbba.", ".abbbbba..",
         "..abbba...", "...aba....", "....a.....", ".........."]
BOL = ["..........", "..........", "aaaaaaaaaa", "abbbbbbbba", "abccccccba", ".abccccba.",
       "..abbbba..", "...aaaa...", "...abba...", "...aaaa..."]
BOL_CREUX = ["..........", "..........", "aaaaaaaaaa", "a........a", "a........a", ".a......a.",
             "..a....a..", "...aaaa...", "...a..a...", "...aaaa..."]
BOL_AMBRE = ["..........", "..........", "aaaaaaaaaa", "a........a", "abbbbbbbba", ".abbbbbba.",
             "..abbbba..", "...aaaa...", "...a..a...", "...aaaa..."]

PICTOS = {
    "regeneration": (COEUR, {"a": "5A1014", "b": "D84848", "c": "F4A0A0"}),
    "pause": (COEUR, {"a": "3A3434", "b": "8A8480", "c": "B8B2AC"}),
    "satiete_prolongee": (BOL, {"a": "3A2414", "b": "C8843E", "c": "E8C46A"}),
    "repas": (BOL, {"a": "3A2414", "b": "C8843E", "c": "E8C46A"}),
    "ramen": (["..a..a....", "...a..a...", "aaaaaaaaaa", "abdcdcdcba", "abccccccba", ".abccccba.",
               "..abbbba..", "...aaaa...", "...abba...", "...aaaa..."],
              {"a": "3A2414", "b": "C8843E", "c": "E8C46A", "d": "F6E9C8"}),
    "faim": (BOL_CREUX, {"a": "D2523E"}),
    "affame": (BOL_AMBRE, {"a": "E0A050", "b": "8E5A2E"}),
    "froid": (BOL_AMBRE, {"a": "A9B4BC", "b": "5E6870"}),
    "bol_vide": (BOL_CREUX, {"a": "C8843E"}),
    "endurance": (["..........", "aaaaaa....", "......a...", "..aaaaa...", "..........", "aaaaaaaa..",
                   "........a.", "....aaaa..", "..........", ".........."], {"a": "BFE0F0"}),
    "pilule": (["..........", "...aaa....", "..abbba...", ".abbcbba..", ".abbbbba..", ".abbbbba..",
                "..abbba...", "...aaa....", "..........", ".........."],
               {"a": "3A2414", "b": "8E5A2E", "c": "D29A5A"}),
    "fatigue": (["..........", "..a....a..", "...a..a...", "....aa....", "..........", "..a....a..",
                 "...a..a...", "....aa....", "..........", ".........."], {"a": "D2523E"}),
    "chaud": ([".a...a....", "a...a.....", ".a...a....", "..a...a...", ".a...a....", "..........",
               "aaaaaaaaa.", ".abbbbba..", "..aaaaa...", ".........."], {"a": "E8E2D8", "b": "B5352B"}),
    "ryo": (["..........", ".aaaaaaa..", "abbbbbbba.", "ab.aaa.ba.", "ab.a.a.ba.", "ab.aaa.ba.",
             "abbbbbbba.", ".aaaaaaa..", "..........", ".........."], {"a": "8E6A30", "b": "E8C27A"}),
    "panier": (["...aaaa...", "..a....a..", "aaaaaaaaaa", "abcbcbcbca", "acbcbcbcba", "abcbcbcbca",
                ".acbcbcba.", ".aaaaaaaa.", "..........", ".........."],
               {"a": "4A3018", "b": "B8864A", "c": "8E6430"}),
    "chakra": (["....a.....", "...aba....", "...aba....", "..abcba...", "..abcba...", ".abccba...",
                ".abccba...", "..abba....", "...aa.....", ".........."], {"a": "1E4A80", "b": "3E8FE0", "c": "BFE0F0"}),
    "poison": (["....a.....", "...aba....", "..abbba...", "..abbba...", ".abbcbba..", ".abbbbba..",
                ".abbbbba..", "..abbba...", "...aaa....", ".........."], {"a": "2E4A1A", "b": "6AA83A", "c": "C8E89A"}),
    "meditation": (["..........", "....a.....", "...aba....", ".a.aba.a..", "abaabaaba.", ".abbbbba..",
                    "..aaaaa...", "..........", "..........", ".........."], {"a": "8E6A30", "b": "E8C27A"}),
    "the": (["...a.a....", "....a.a...", "..........", "aaaaaaaa..", "abbbbbbaaa", "abbbbbba.a",
             "abbbbbbaaa", ".abbbba...", "..aaaa....", ".........."], {"a": "3E4A2E", "b": "8EA86A"}),
    "dango": (["........a.", ".......a..", "....aaa...", "...abba...", "..aaaa....", "..acca....",
               ".aaaa.....", ".adda.....", "a.aa......", ".........."],
              {"a": "4A3018", "b": "E8A0B0", "c": "F6E9C8", "d": "8EB86A"}),
    "bento": (["..........", "aaaaaaaaaa", "abbbbabbba", "abccbaddba", "abccbaddba", "aaaaaaaaaa",
               "abeeeeeeba", "abeeeeeeba", "aaaaaaaaaa", ".........."],
              {"a": "3A1E14", "b": "A0182B", "c": "F6E9C8", "d": "8EB86A", "e": "E8C46A"}),
    "ration": (["..........", "..........", ".aaaaaaaa.", ".abbbbbba.", ".abcccccba", ".abbbbbba.",
                ".aaaaaaaa.", "..........", "..........", ".........."], {"a": "4A3018", "b": "B8864A", "c": "8E6430"}),
}


# ═══════════════════════════════ objets du pack (16×16) ═══════════════════════════════
def bol16(dessus, pal):
    rows = ["................", "................", "................", "................"] + dessus + [
        "aaaaaaaaaaaaaaaa", "abbbbbbbbbbbbbba", "abbbbbbbbbbbbbba", ".abbbbbbbbbbbba.", "..abbbbbbbbbba..",
        "...aabbbbbbaa...", ".....aaaaaa.....", ".....abbbba.....", ".....aaaaaa....."]
    rows = rows[-16:]
    return grille(rows, pal, 16)


OBJETS = {
    "plat_ramen_shoyu": bol16(["....c..c........", ".....c..c.......", "...ddddddddd....", "..deeeffeeeed..."],
                              {"a": "3A1E14", "b": "A0182B", "c": "5A3A20", "d": "C8843E", "e": "E8C46A", "f": "F6E9C8"}),
    "plat_ramen_miso": bol16(["....c..c........", ".....c..c.......", "...ddddddddd....", "..deeegfeeeed..."],
                             {"a": "3A1E14", "b": "2E3E5A", "c": "5A3A20", "d": "B0703A", "e": "D89A50", "f": "F6E9C8", "g": "8EB86A"}),
    "bol_vide": bol16(["................", "................", "................", "................"],
                      {"a": "3A1E14", "b": "A0182B"}),
    "plat_bento": grille(["................", "................", "................", "aaaaaaaaaaaaaaaa",
                          "abbbbbbbabbbbbba", "abcccccbabddddba", "abcccccbabddddba", "abcccccbabddddba",
                          "abbbbbbbabbbbbba", "aaaaaaaaaaaaaaaa", "abeeeeeeeeeeeeba", "abeeffeeeeffeeba",
                          "abeeeeeeeeeeeeba", "abbbbbbbbbbbbbba", "aaaaaaaaaaaaaaaa", "................"],
                         {"a": "1E120E", "b": "A0182B", "c": "F6E9C8", "d": "8EB86A", "e": "E8C46A", "f": "C8843E"}, 16),
    "plat_dango": grille(["..............a.", ".............a..", "..........aaa...", ".........abbba..",
                          "........abbbbba.", "........abbbba..", "......aaaaaa....", ".....acccccca...",
                          ".....acccccca...", "...aaaaaaaa.....", "..addddddda.....", "..addddddda.....",
                          "..aaaaaaaa......", ".a..............", "a...............", "................"],
                         {"a": "4A3018", "b": "E8A0B0", "c": "F6E9C8", "d": "8EB86A"}, 16),
    "plat_the_vert": grille(["................", ".....a..a.......", "......a..a......", "................",
                             "..aaaaaaaaaa....", "..abbbbbbbbaaa..", "..acccccccca..a.", "..abbbbbbbba..a.",
                             "..abbbbbbbbaaa..", "...abbbbbba.....", "....aaaaaa......", "..aaaaaaaaaa....",
                             "................", "................", "................", "................"],
                            {"a": "3E4A2E", "b": "8EA86A", "c": "C8E89A"}, 16),
    "ration_seche": grille(["................", "................", "................", "................",
                            ".aaaaaaaaaaaaaa.", ".abbbbbbbbbbbba.", ".abcbcbcbcbcbca.", ".abbbbbbbbbbbba.",
                            ".adddddddddddda.", ".abbbbbbbbbbbba.", ".abcbcbcbcbcbca.", ".abbbbbbbbbbbba.",
                            ".aaaaaaaaaaaaaa.", "................", "................", "................"],
                           {"a": "3A2414", "b": "B8864A", "c": "8E6430", "d": "6A4E24"}, 16),
    "panier_ingredients": grille(["................", ".....aaaaaa.....", "....a......a....", "...a........a...",
                                  "..eeffggeeff....", ".aaaaaaaaaaaaaa.", ".abcbcbcbcbcbca.", ".acbcbcbcbcbcba.",
                                  ".abcbcbcbcbcbca.", ".acbcbcbcbcbcba.", "..abcbcbcbcbca..", "..acbcbcbcbcba..",
                                  "...aaaaaaaaaa...", "................", "................", "................"],
                                 {"a": "4A3018", "b": "B8864A", "c": "8E6430", "e": "8EB86A", "f": "D84848", "g": "F6E9C8"}, 16),
}


def main():
    os.makedirs(MOD, exist_ok=True)
    for nom, (rows, pal) in PICTOS.items():
        grille(rows, pal, 10).save(os.path.join(MOD, nom + ".png"))
    print("HUD :", len(PICTOS), "icônes dans", os.path.normpath(MOD))

    tex = os.path.join(PACK, "textures", "item")
    mod = os.path.join(PACK, "models", "item")
    items = os.path.join(PACK, "items")
    for d in (tex, mod, items):
        os.makedirs(d, exist_ok=True)
    for nom, im in OBJETS.items():
        im.save(os.path.join(tex, nom + ".png"))
        with open(os.path.join(mod, nom + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"credit": "Reborn — nourriture (tools/gen_nourriture_textures.py, 2026-10-14)",
                       "parent": "minecraft:item/generated", "textures": {"layer0": "reborn:item/" + nom}}, f, indent=1)
        with open(os.path.join(items, nom + ".json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "reborn:item/" + nom}}, f, indent=2)
    print("Pack Nexo :", len(OBJETS), "objets dans", os.path.normpath(PACK))


if __name__ == "__main__":
    main()
