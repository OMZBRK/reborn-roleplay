"""Sons de la boutique « échoppe de nuit », synthétisés.

    python gen_shop_sfx.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn/sounds/shop/*.ogg
"""

import numpy as np

import gen_stats_sfx as g
from gen_stats_sfx import SR, t, env, lowpass, highpass, whoosh, koto, chime, mix, write

g.OUT = g.OUT.parent / "shop"
RNG = np.random.default_rng(33)


def furin(freqs, dur=2.2):
    """Carillon de vent en verre : frappes rapprochées, partiels aigus inharmoniques."""
    parts = []
    t0 = 0.0
    for f in freqs:
        x = t(dur)
        s = (np.sin(2 * np.pi * f * x) + 0.5 * np.sin(2 * np.pi * f * 2.92 * x) * np.exp(-x / 0.25)
             + 0.25 * np.sin(2 * np.pi * f * 5.1 * x) * np.exp(-x / 0.1)) * np.exp(-x / 0.8)
        parts.append((t0, s * env(len(x), 0.001, release=0.05), 0.5))
        t0 += RNG.uniform(0.07, 0.16)
    return mix(dur + t0, *parts)


def coin(f):
    x = t(0.35)
    s = (np.sin(2 * np.pi * f * x) + 0.7 * np.sin(2 * np.pi * f * 2.4 * x) + 0.5 * np.sin(2 * np.pi * f * 3.9 * x))
    return s * env(len(x), 0.0005, decay=0.07)


def s_open():
    rustle = highpass(lowpass(RNG.normal(0, 1, int(SR * 0.5)), 3000), 500) * env(int(SR * 0.5), 0.05, decay=0.15)
    return mix(2.6, (0.0, rustle, 0.4), (0.15, furin([2093, 2349, 2637, 3136]), 0.55))


def s_buy():
    parts = []
    for k in range(7):
        parts.append((k * RNG.uniform(0.035, 0.07), coin(RNG.uniform(2400, 3600)), RNG.uniform(0.35, 0.7)))
    parts.append((0.32, koto(g.A4, 0.9, 0.996), 0.35))
    return mix(1.2, *parts)


def s_equip():
    n = int(SR * 0.5)
    swish = highpass(lowpass(RNG.normal(0, 1, n), np.geomspace(1500, 5000, n)), 600)
    return swish * env(n, 0.08, decay=0.12)


def s_select():
    # perle de boulier (soroban) : clic de bois sec, deux rebonds
    click = koto(1200, 0.06, 0.9, 1.0)
    return mix(0.15, (0.0, click, 0.8), (0.035, click, 0.35), (0.06, click, 0.15))


def s_ambience():
    """Boucle 12 s : ambiance de pièce feutrée + fūrin lointain de temps en temps."""
    dur = 12.0
    n = int(SR * dur)
    room = lowpass(RNG.normal(0, 1, n), 180) * 0.4
    out = room.copy()
    for start in (2.2, 7.6):
        f = furin([2349, 2793, 3136], 2.4) * 0.18
        s0 = int(SR * start)
        out[s0:s0 + len(f)] += f[: n - s0]
    fade = int(SR * 0.6)
    loop = out[: n - fade].copy()
    loop[:fade] = loop[:fade] * np.linspace(0, 1, fade) + out[n - fade:] * np.linspace(1, 0, fade)
    return loop


if __name__ == "__main__":
    g.OUT.mkdir(parents=True, exist_ok=True)
    for name, fn, peak in [("open", s_open, 0.6), ("buy", s_buy, 0.75), ("equip", s_equip, 0.5),
                           ("select", s_select, 0.5), ("ambience", s_ambience, 0.4)]:
        write(name, fn(), peak)
    print("->", g.OUT)
