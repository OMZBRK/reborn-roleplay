"""Sons de la sacoche « inrō », synthétisés.

    python gen_sacoche_sfx.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn/sounds/sacoche/*.ogg
Palette : bois laqué (petits chocs secs et brillants), glissement d'un étage, grelot de l'ojime,
soie de la ceinture.
"""

import numpy as np

import gen_stats_sfx as g
from gen_stats_sfx import SR, t, env, lowpass, highpass, mix, write

g.OUT = g.OUT.parent / "sacoche"
RNG = np.random.default_rng(64)


def tok(freq, dur=0.12, bright=0.6):
    """Choc de bois laqué : fondamentale courte + partiel aigu + clic."""
    x = t(dur)
    s = np.sin(2 * np.pi * freq * x) * np.exp(-x / 0.022) + bright * np.sin(2 * np.pi * freq * 2.9 * x) * np.exp(-x / 0.01)
    return s + lowpass(RNG.normal(0, 1, len(x)), 5000) * np.exp(-x / 0.002) * 0.5


def slide(dur=0.16):
    n = int(SR * dur)
    s = highpass(lowpass(RNG.normal(0, 1, n), np.geomspace(1800, 900, n)), 300)
    return s * np.sin(np.pi * np.arange(n) / n) ** 0.8


def bell(f, dur=0.7):
    x = t(dur)
    s = np.sin(2 * np.pi * f * x) + 0.5 * np.sin(2 * np.pi * f * 2.42 * x) + 0.3 * np.sin(2 * np.pi * f * 3.9 * x)
    return s * (1 + 0.3 * np.sin(2 * np.pi * 12 * x)) * env(len(x), 0.001, decay=0.14)


def silk(dur=0.22):
    n = int(SR * dur)
    return highpass(lowpass(RNG.normal(0, 1, n), 6000), 2000) * env(n, 0.03, decay=dur / 3)


def s_open(): return mix(0.8, (0.0, slide(0.14), 0.6), (0.12, tok(520), 0.9), (0.16, bell(3400), 0.25))
def s_close(): return mix(0.4, (0.0, slide(0.10), 0.5), (0.08, tok(430), 0.9))
def s_tier(): return mix(0.5, (0.0, slide(0.16), 0.7), (0.15, tok(640, bright=0.4), 0.6), (0.17, bell(3900, 0.4), 0.12))
def s_pick(): return tok(880, 0.08, 0.5)
def s_place(): return mix(0.25, (0.0, tok(560), 0.9), (0.03, tok(560, 0.06), 0.3))
def s_hover(): return tok(1400, 0.04, 0.2) * 0.6
def s_belt(): return mix(0.3, (0.0, silk(), 0.8), (0.05, tok(700, 0.06), 0.4))
def s_deny(): return tok(220, 0.18, 0.1)


if __name__ == "__main__":
    g.OUT.mkdir(parents=True, exist_ok=True)
    for name, fn, peak in [("open", s_open, 0.7), ("close", s_close, 0.65), ("tier", s_tier, 0.6), ("pick", s_pick, 0.55),
                           ("place", s_place, 0.6), ("hover", s_hover, 0.3), ("belt", s_belt, 0.5), ("deny", s_deny, 0.55)]:
        write(name, fn(), peak)
    print("->", g.OUT)
