"""Sons du mode photo, synthétisés.

    python gen_photo_sfx.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn/sounds/photo/*.ogg
- shutter : obturateur mécanique (rideau qui claque + armement du levier)
- tick    : cran de réglette (bague d'objectif)
- filter  : changement de filtre (verre posé sur l'objectif)
- open    : ouverture du viseur (clapet qui se relève)
"""

import numpy as np

import gen_stats_sfx as g
from gen_stats_sfx import SR, t, env, lowpass, highpass, mix, write

g.OUT = g.OUT.parent / "photo"
g.OUT.mkdir(parents=True, exist_ok=True)
RNG = np.random.default_rng(88)


def click(f=3200, dur=0.03, decay=0.004):
    x = t(dur)
    n = highpass(lowpass(RNG.normal(0, 1, len(x)), f), 400) * np.exp(-x / decay)
    return n + 0.5 * np.sin(2 * np.pi * f * 0.35 * x) * np.exp(-x / (decay * 1.5))


def at(x, sec, total):
    out = np.zeros(int(SR * total))
    i = int(SR * sec)
    out[i:i + len(x)] += x[: len(out) - i]
    return out


def s_shutter():
    total = 0.42
    first = click(4200, 0.05, 0.005) * 1.0          # 1er rideau
    second = click(3000, 0.06, 0.007) * 0.8         # 2e rideau
    x = t(0.18)
    wind = lowpass(RNG.normal(0, 1, len(x)), np.geomspace(900, 300, len(x))) * np.sin(np.pi * x / 0.18) ** 2 * 0.35
    lever = click(1800, 0.04, 0.006) * 0.5
    return at(first, 0.0, total) + at(second, 0.045, total) + at(wind, 0.12, total) + at(lever, 0.3, total)


def s_tick():
    return click(5200, 0.03, 0.0025) * 0.7


def s_filter():
    x = t(0.25)
    glass = (np.sin(2 * np.pi * 2600 * x) + 0.5 * np.sin(2 * np.pi * 4100 * x)) * np.exp(-x / 0.05) * 0.4
    return glass + at(click(2400, 0.03, 0.004), 0.0, 0.25)


def s_open():
    total = 0.3
    return at(click(1500, 0.05, 0.008), 0.0, total) + at(click(2800, 0.04, 0.005) * 0.6, 0.09, total)


if __name__ == "__main__":
    print("->", g.OUT)
    write("shutter", s_shutter())
    write("tick", s_tick(), 0.6)
    write("filter", s_filter(), 0.7)
    write("open", s_open(), 0.7)
