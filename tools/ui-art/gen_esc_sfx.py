"""Sons du menu Échap « tokonoma », synthétisés.

    python gen_esc_sfx.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn/sounds/esc/*.ogg
Palette propre à l'Échap : bois (fusuma qui coulisse, hyōshigi), clochette suzu des omamori,
papier (kawaraban / kamishibai), et pour l'ambiance une pièce calme : grillons au loin,
filet d'eau et coup de bambou du shishi-odoshi.
"""

import numpy as np

import gen_stats_sfx as g
from gen_stats_sfx import SR, t, env, lowpass, highpass, koto, mix, write

g.OUT = g.OUT.parent / "esc"
RNG = np.random.default_rng(51)


def slide(dur, f0, f1):
    """Panneau de bois qui glisse dans sa rainure : bruit grave filtré + léger « grain » de frottement."""
    n = int(SR * dur)
    body = lowpass(RNG.normal(0, 1, n), np.geomspace(f0, f1, n))
    grain = highpass(lowpass(RNG.normal(0, 1, n), 2400), 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * t(dur)) ** 2)
    e = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** 0.7
    return (body * 1.4 + grain * 0.18) * e


def knock(freq=180, dur=0.18):
    """Choc de bois mat (cadre qui arrive en butée)."""
    x = t(dur)
    s = np.sin(2 * np.pi * freq * x) * np.exp(-x / 0.035) + 0.5 * np.sin(2 * np.pi * freq * 2.3 * x) * np.exp(-x / 0.02)
    click = lowpass(RNG.normal(0, 1, len(x)), 3000) * np.exp(-x / 0.004)
    return s + click * 0.6


def clap(freq):
    """Hyōshigi : deux claves de bois dur frappées l'une contre l'autre (sec, brillant)."""
    x = t(0.22)
    s = (np.sin(2 * np.pi * freq * x) + 0.6 * np.sin(2 * np.pi * freq * 2.71 * x)) * np.exp(-x / 0.03)
    return s + lowpass(RNG.normal(0, 1, len(x)), 6000) * np.exp(-x / 0.003) * 0.8


def suzu(f, dur=1.0):
    """Clochette suzu : grelot métallique, battements rapides et partiels inharmoniques."""
    x = t(dur)
    trem = 1 + 0.35 * np.sin(2 * np.pi * 14 * x)
    s = (np.sin(2 * np.pi * f * x) + 0.6 * np.sin(2 * np.pi * f * 2.42 * x) + 0.35 * np.sin(2 * np.pi * f * 4.1 * x)) * trem
    return s * env(len(x), 0.001, decay=0.18)


def rustle(dur=0.3):
    n = int(SR * dur)
    r = highpass(lowpass(RNG.normal(0, 1, n), 5000), 1200)
    crack = np.zeros(n)
    for _ in range(14):
        i = RNG.integers(0, n - 200)
        crack[i:i + 60] += RNG.normal(0, 1, 60) * np.exp(-np.arange(60) / 12)
    return (r * 0.5 + crack) * env(n, 0.02, decay=dur / 3)


def s_open():
    return mix(0.9, (0.0, slide(0.55, 220, 900), 0.8), (0.0, slide(0.55, 240, 950), 0.7), (0.5, knock(170), 0.6), (0.53, knock(150), 0.5))


def s_close():
    return mix(0.6, (0.0, slide(0.32, 900, 260), 0.9), (0.3, knock(140), 0.9))


def s_hover():
    x = t(0.06)
    return np.sin(2 * np.pi * 900 * x) * np.exp(-x / 0.012) + lowpass(RNG.normal(0, 1, len(x)), 2500) * np.exp(-x / 0.003) * 0.3


def s_select():
    return mix(0.35, (0.0, clap(1450), 0.9), (0.09, clap(1520), 0.6))


def s_charm():
    parts = [(k * RNG.uniform(0.04, 0.08), suzu(RNG.uniform(3200, 4200)), RNG.uniform(0.4, 0.8)) for k in range(4)]
    return mix(1.3, *parts)


def s_page():
    return rustle(0.28)


def s_ambience():
    """Boucle 14 s : pièce calme — grillons lointains, filet d'eau, un coup de shishi-odoshi."""
    dur = 14.0
    n = int(SR * dur)
    x = t(dur)
    water = highpass(lowpass(RNG.normal(0, 1, n), 1800 + 600 * np.sin(2 * np.pi * x / 3.1)), 400) * 0.18
    for _ in range(90):  # gouttes
        i = RNG.integers(0, n - 4000); f = RNG.uniform(900, 1800)
        tt = np.arange(4000) / SR
        water[i:i + 4000] += np.sin(2 * np.pi * (f + 2400 * tt) * tt) * np.exp(-tt / 0.012) * RNG.uniform(0.05, 0.15)
    crickets = np.zeros(n)
    for _ in range(18):
        start = RNG.uniform(0, dur - 0.6); f = RNG.uniform(4400, 5000)
        for c in range(int(RNG.integers(2, 4))):
            s0 = int(SR * (start + c * 0.12)); ln = int(SR * 0.05)
            if s0 + ln >= n: break
            tt = np.arange(ln) / SR
            pulse = (np.sin(2 * np.pi * 32 * tt) > 0) * np.sin(np.pi * tt / (ln / SR))
            crickets[s0:s0 + ln] += np.sin(2 * np.pi * f * tt) * pulse * RNG.uniform(0.05, 0.14)
    bamboo = np.zeros(n)
    o = int(SR * 7.2)
    k = knock(310, 0.5) * 0.9
    bamboo[o:o + len(k)] += k
    out = water + crickets + bamboo
    fade = int(SR * 0.6)
    loop = out[: n - fade].copy()
    loop[:fade] = loop[:fade] * np.linspace(0, 1, fade) + out[n - fade:] * np.linspace(1, 0, fade)
    return loop


if __name__ == "__main__":
    g.OUT.mkdir(parents=True, exist_ok=True)
    for name, fn, peak in [("open", s_open, 0.75), ("close", s_close, 0.7), ("hover", s_hover, 0.35),
                           ("select", s_select, 0.7), ("charm", s_charm, 0.6), ("page", s_page, 0.5),
                           ("ambience", s_ambience, 0.45)]:
        write(name, fn(), peak)
    print("->", g.OUT)
