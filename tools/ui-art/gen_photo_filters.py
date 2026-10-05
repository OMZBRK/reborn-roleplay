"""Filtres plein écran du mode photo (post effects), une combinaison filtre × niveau de flou par fichier.

    python gen_photo_filters.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn-hud/post_effect/photo_<filtre>_<flou>.json
Shader : reborn-hud:post/photo (étalonnage + flou séparable en 2 passes). « naturel_0 » n'existe pas :
c'est l'absence de filtre.
"""
import json
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn-hud/post_effect"

ID = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
FILTERS = {
    #           matrice                                                       saturation  teinte           vignettage
    "naturel": (ID,                                                            1.0, (1.0, 1.0, 1.0), 0.0),
    "encre":   (((.299, .587, .114),) * 3,                                     0.0, (1.02, 1.0, .96), 0.35),
    "sepia":   (((.393, .769, .189), (.349, .686, .168), (.272, .534, .131)),  1.0, (1.0, .97, .9), 0.3),
    "nuit":    (ID,                                                            0.55, (.62, .72, 1.0), 0.45),
}
BLUR = [0.0, 1.5, 3.0, 5.0]          # rayon en texels par niveau (0 = net)


def uniforms(m, sat, tint, vig, direction, radius):
    v4 = lambda name, val: {"name": name, "type": "vec4", "value": list(val)}
    return {"PhotoConfig": [
        v4("RedRow", (*m[0], 0.0)), v4("GreenRow", (*m[1], 0.0)), v4("BlueRow", (*m[2], 0.0)),
        v4("Params", (sat, direction[0], direction[1], radius)),
        v4("Tint", (*tint, vig)),
    ]}


def chain(name, level):
    m, sat, tint, vig = FILTERS[name]
    r = BLUR[level]
    return {
        "targets": {"swap": {}},
        "passes": [
            {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "reborn-hud:post/photo",
             "inputs": [{"sampler_name": "In", "target": "minecraft:main", "bilinear": True}],
             "output": "swap", "uniforms": uniforms(m, sat, tint, vig, (1.0, 0.0), r)},
            {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "reborn-hud:post/photo",
             "inputs": [{"sampler_name": "In", "target": "swap", "bilinear": True}],
             "output": "minecraft:main", "uniforms": uniforms(ID, 1.0, (1.0, 1.0, 1.0), 0.0, (0.0, 1.0), r)},
        ],
    }


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    n = 0
    for name in FILTERS:
        for lvl in range(len(BLUR)):
            if name == "naturel" and lvl == 0:
                continue
            (OUT / f"photo_{name}_{lvl}.json").write_text(json.dumps(chain(name, lvl), indent=2) + "\n", encoding="utf-8")
            n += 1
    print(n, "->", OUT)
