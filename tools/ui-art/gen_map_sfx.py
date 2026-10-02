"""Sons de la carte du monde, synthétisés (même boîte à outils que gen_stats_sfx).

    python gen_map_sfx.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn/sounds/map/*.ogg
"""

import numpy as np

import gen_stats_sfx as g
from gen_stats_sfx import SR, t, env, lowpass, highpass, whoosh, koto, chime, mix, write

g.OUT = g.OUT.parent / "map"
RNG = np.random.default_rng(21)


def crinkle(dur, density=0.02, bright=4000):
    """Froissement de papier : salves de bruit filtré, éparses."""
    n = int(SR * dur)
    out = np.zeros(n)
    k = 0
    while k < n:
        ln = int(SR * RNG.uniform(0.004, 0.02))
        burst = highpass(lowpass(RNG.normal(0, 1, ln), bright), 900) * env(ln, 0.001, decay=0.006)
        out[k:k + ln] += burst[: max(0, n - k)] * RNG.uniform(0.3, 1.0)
        k += int(SR * RNG.exponential(density))
    return out


def bell(freq, dur=2.6):
    """Cloche de temple : partiels inharmoniques, longue décroissance, léger battement."""
    x = t(dur)
    parts = [(1.0, 1.0, 1.6), (2.0, 0.5, 1.0), (2.76, 0.4, 0.7), (5.4, 0.2, 0.35), (8.9, 0.1, 0.2)]
    s = sum(a * np.sin(2 * np.pi * freq * r * x + 0.3 * np.sin(2 * np.pi * 1.3 * x)) * np.exp(-x / d)
            for r, a, d in parts)
    return s * env(len(x), 0.002, release=0.2)


def s_open():
    # le parchemin se déroule (froissements + souffle) puis une note de koto
    n = int(SR * 1.2)
    unroll = crinkle(1.0, 0.012) * np.linspace(1, 0.2, int(SR * 1.0))
    return mix(1.6, (0.0, whoosh(1.0, 300, 2600, 0.5), 0.45), (0.05, unroll, 0.5),
               (0.8, koto(g.G4, 1.0, 0.996), 0.35))


def s_close():
    roll = crinkle(0.45, 0.01) * np.linspace(0.3, 1, int(SR * 0.45))
    return mix(0.6, (0.0, roll, 0.6), (0.0, whoosh(0.4, 2400, 400, 0.8), 0.3))


def s_zoom():
    n = int(SR * 0.09)
    tick = np.sin(2 * np.pi * 900 * t(0.09)) * env(n, 0.001, decay=0.015)
    knock = lowpass(RNG.normal(0, 1, n), 1500) * env(n, 0.0005, decay=0.008)
    return tick * 0.6 + knock * 0.4


def s_drag():
    n = int(SR * 0.35)
    slide = highpass(lowpass(RNG.normal(0, 1, n), 3500), 700) * env(n, 0.06, decay=0.12)
    return slide * 0.7 + crinkle(0.35, 0.05) * 0.3


def s_select():
    # tampon d'encre : bruit sourd + petit clic de bois
    n = int(SR * 0.3)
    thud = lowpass(RNG.normal(0, 1, n), 400) * env(n, 0.002, decay=0.04)
    tone = np.sin(2 * np.pi * 140 * t(0.3)) * env(n, 0.002, decay=0.05)
    click = koto(600, 0.15, 0.9, 0.9)
    return mix(0.3, (0.0, thud, 0.8), (0.0, tone, 0.5), (0.0, click, 0.25))


def s_travel():
    # souffle qui monte + cloche de temple
    return mix(2.8, (0.0, whoosh(1.0, 200, 5000, 0.35), 0.5), (0.45, bell(g.D4 / 2 * 1.5), 0.55),
               (0.5, chime(g.D5, 1.5, 0.6), 0.2))


def s_ambience():
    """Boucle 14 s : vent léger + oiseaux lointains (trilles)."""
    dur = 14.0
    n = int(SR * dur)
    x = t(dur)
    wind = lowpass(RNG.normal(0, 1, n), 300 + 120 * np.sin(2 * np.pi * x / dur * 3)) * 0.5
    birds = np.zeros(n)
    for _ in range(9):
        start = RNG.uniform(0, dur - 1.2)
        f0 = RNG.uniform(2600, 3800)
        for c in range(RNG.integers(3, 7)):
            s0 = int(SR * (start + c * RNG.uniform(0.07, 0.12)))
            ln = int(SR * RNG.uniform(0.04, 0.08))
            if s0 + ln >= n: break
            tt = np.arange(ln) / SR
            sweep = f0 * (1 + 0.25 * np.sin(np.pi * tt / (ln / SR)))
            birds[s0:s0 + ln] += np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.sin(np.pi * tt / (ln / SR)) * 0.25
    out = wind + birds
    fade = int(SR * 0.6)
    loop = out[: n - fade].copy()
    loop[:fade] = loop[:fade] * np.linspace(0, 1, fade) + out[n - fade:] * np.linspace(1, 0, fade)
    return loop


if __name__ == "__main__":
    g.OUT.mkdir(parents=True, exist_ok=True)
    for name, fn, peak in [("open", s_open, 0.75), ("close", s_close, 0.6), ("zoom", s_zoom, 0.45),
                           ("drag", s_drag, 0.4), ("select", s_select, 0.7), ("travel", s_travel, 0.85),
                           ("ambience", s_ambience, 0.45)]:
        write(name, fn(), peak)
    print("->", g.OUT)
