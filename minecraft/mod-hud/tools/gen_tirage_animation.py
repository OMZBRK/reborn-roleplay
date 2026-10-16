# gen_tirage_animation.py — test de la Feuille (16/10) : génère assets/reborn-hud/animations/feuille_canal.json et feuille_fin.json (PAL, geckolib 1.8).
# Usage (depuis minecraft/mod-hud) : python tools/gen_tirage_animation.py src/main/resources/assets/reborn-hud/animations/feuille_canal.json
import json, sys
OUT = sys.argv[1]
E = "easeInOutSine"
def kf(pairs, key="vector"):
    return {f"{t:.3f}": {key: v, "easing": e} for t, v, e in pairs}

# 0 → 0,70 s : la main droite monte la feuille devant le visage, la gauche forme un demi-sceau ; puis respiration
# (boucle 0,70 → 3,70 s, rejouée par le code depuis le tick 14).
B = 0.70
def souffle(base, amp):
    # base au début/fin de la boucle, base+amp au milieu des deux respirations
    return [(B, base, E), (B + 0.75, [b + a for b, a in zip(base, amp)], E), (B + 1.5, base, E),
            (B + 2.25, [b + a for b, a in zip(base, amp)], E), (B + 3.0, base, E)]
def souffle1(base, amp):
    return [(B, base, E), (B + 0.75, base + amp, E), (B + 1.5, base, E), (B + 2.25, base + amp, E), (B + 3.0, base, E)]

RA = [-78.0, -32.0, 0.0]
LA = [-40.0, 25.0, -10.0]
HD = [10.0, 14.0, 0.0]
anim = {
  "model": {"body_control": {"pivot": [0.0, 12.0, 0.0]}, "waist": {"pivot": [0.0, 12.0, 0.0]}},
  "parents": {"waist": "body_control", "left_leg": "body_control", "right_leg": "body_control", "torso": "waist",
              "head": "waist", "right_arm": "waist", "left_arm": "waist"},
  "animation_length": B + 3.0,
  "loop": True,
  "player_animation_library": {"name": "Test de la Feuille — canaliser", "author": "Reborn",
                               "description": "La feuille tenue entre deux doigts devant le visage, l'autre main en demi-sceau, respiration."},
  "bones": {
    "right_arm": {
      "rotation": kf([(0.0, [0.0, 0.0, 0.0], E), (0.32, [-52.0, -16.0, 0.0], E)] + souffle(RA, [-2.0, 0.0, 0.0])),
      "bend": kf([(0.0, 0.0, E), (0.32, -38.0, E)] + souffle1(-72.0, -3.0), key="value"),
    },
    "left_arm": {
      "rotation": kf([(0.0, [0.0, 0.0, 0.0], E), (0.40, [-26.0, 14.0, -6.0], E)] + souffle(LA, [-1.5, 0.0, 0.0])),
      "bend": kf([(0.0, 0.0, E), (0.40, -60.0, E)] + souffle1(-95.0, -2.0), key="value"),
    },
    "head": {
      "rotation": kf([(0.0, [0.0, 0.0, 0.0], E), (0.45, [6.0, 8.0, 0.0], E)] + souffle(HD, [1.5, 0.0, 0.0])),
    },
    "torso": {
      "bend": kf([(0.0, 0.0, E)] + souffle1(2.0, 1.0), key="value"),
    },
  },
}
json.dump({"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {"feuille_canal": anim}},
          open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("ok", OUT)

# fin : de la pose (début de boucle) au repos en 0,45 s, jouée une fois
F = 0.45
fin = dict(anim)
fin["animation_length"] = F
fin["loop"] = False
fin["player_animation_library"] = {"name": "Test de la Feuille — fin", "author": "Reborn",
                                   "description": "Les bras redescendent."}
fin["bones"] = {
    "right_arm": {"rotation": kf([(0.0, RA, E), (F, [0.0, 0.0, 0.0], E)]), "bend": kf([(0.0, -72.0, E), (F, 0.0, E)], key="value")},
    "left_arm": {"rotation": kf([(0.0, LA, E), (F, [0.0, 0.0, 0.0], E)]), "bend": kf([(0.0, -95.0, E), (F, 0.0, E)], key="value")},
    "head": {"rotation": kf([(0.0, HD, E), (F, [0.0, 0.0, 0.0], E)])},
    "torso": {"bend": kf([(0.0, 2.0, E), (F, 0.0, E)], key="value")},
}
OUT2 = OUT.replace("feuille_canal", "feuille_fin")
json.dump({"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {"feuille_fin": fin}},
          open(OUT2, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("ok", OUT2)
