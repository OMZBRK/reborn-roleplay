"""Sons de la fiche shinobi « lanternes célestes », synthétisés (aucun sample tiers).

    python gen_stats_sfx.py

Sortie : minecraft/mod-hud/src/main/resources/assets/reborn/sounds/stats/*.ogg (Vorbis mono 44,1 kHz,
le seul format accepté par Minecraft). Encodage via le ffmpeg embarqué d'imageio-ffmpeg.

Palette sonore : gamme pentatonique japonaise « in » (miyako-bushi) sur ré — koto (Karplus-Strong),
carillons à partiels inharmoniques, souffle (bruit filtré), flamme, grillons pour l'ambiance.
"""

import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 44100
OUT = Path(__file__).resolve().parents[2] / "minecraft/mod-hud/src/main/resources/assets/reborn/sounds/stats"
RNG = np.random.default_rng(7)

# ré mi♭ sol la si♭ (in-sen)
D4, EB4, G4, A4, BB4 = 293.66, 311.13, 392.0, 440.0, 466.16
D5, EB5, G5, A5 = D4 * 2, EB4 * 2, G4 * 2, A4 * 2


def t(dur): return np.arange(int(SR * dur)) / SR


def env(n, attack=0.005, release=None, decay=None):
    """Enveloppe : attaque linéaire puis décroissance exponentielle (decay = constante de temps)."""
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    if decay:
        a *= np.exp(-np.maximum(x - attack, 0) / decay)
    if release:
        r = int(SR * release)
        a[-r:] *= np.linspace(1, 0, r)
    return a


def lowpass(x, cutoff):
    """Passe-bas 1 pôle ; cutoff scalaire ou tableau (Hz) pour un balayage."""
    cut = np.broadcast_to(np.asarray(cutoff, np.float64), x.shape)
    alpha = 1 - np.exp(-2 * np.pi * cut / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha[i] * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, cutoff): return x - lowpass(x, cutoff)


def koto(freq, dur=1.4, damp=0.996, bright=0.5):
    """Corde pincée Karplus-Strong (koto/shamisen doux)."""
    n = int(SR * dur)
    p = max(2, int(SR / freq))
    buf = RNG.uniform(-1, 1, p) * bright + np.sin(np.linspace(0, 2 * np.pi, p)) * (1 - bright)
    out = np.empty(n)
    for i in range(n):
        out[i] = buf[i % p]
        buf[i % p] = damp * 0.5 * (buf[i % p] + buf[(i + 1) % p])
    return out * env(n, 0.002, release=0.05)


def chime(freq, dur=1.2, decay=0.45):
    x = t(dur)
    s = (np.sin(2 * np.pi * freq * x)
         + 0.45 * np.sin(2 * np.pi * freq * 2.76 * x) * np.exp(-x / (decay * 0.5))
         + 0.25 * np.sin(2 * np.pi * freq * 5.40 * x) * np.exp(-x / (decay * 0.25)))
    return s * env(len(x), 0.003, decay=decay, release=0.04)


def whoosh(dur, f0, f1, attack=0.3, level=1.0):
    n = int(SR * dur)
    cut = np.geomspace(f0, f1, n)
    w = lowpass(RNG.normal(0, 1, n), cut)
    w = highpass(w, 120)
    e = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** (1.0 / max(attack, 0.05))
    return w * e * level


def mix(length, *parts):
    out = np.zeros(int(SR * length))
    for offset, sig, gain in parts:
        o = int(SR * offset)
        seg = sig[: max(0, len(out) - o)]
        out[o:o + len(seg)] += seg * gain
    return out


def normalize(x, peak=0.85):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def write(name, x, peak=0.85):
    x = normalize(x, peak)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "s.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
        ff = __import__("imageio_ffmpeg").get_ffmpeg_exe()
        subprocess.run([ff, "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5",
                        str(OUT / f"{name}.ogg")], check=True)
    print("  ", name, f"{len(x) / SR:.2f}s")


# --------------------------------------------------------------------------- sons

def s_open():
    # souffle des nuages qui s'écartent + nappe grave + note de koto
    pad = (np.sin(2 * np.pi * (D4 / 2) * t(1.8)) + 0.6 * np.sin(2 * np.pi * (A4 / 2) * t(1.8)))
    pad *= env(len(pad), 0.5, decay=0.9, release=0.3)
    return mix(1.9, (0.0, whoosh(1.3, 300, 2400, 0.4), 0.55), (0.1, pad, 0.25),
               (0.75, koto(D5, 1.1), 0.5), (0.95, chime(A5, 0.9, 0.35), 0.18))


def s_light():
    n = int(SR * 0.45)
    burst = lowpass(RNG.normal(0, 1, n), np.geomspace(3000, 600, n)) * env(n, 0.004, decay=0.09)
    thump = np.sin(2 * np.pi * 90 * t(0.45) * (1 - 0.3 * t(0.45))) * env(n, 0.002, decay=0.06)
    crackle = (RNG.random(n) < 0.004) * RNG.uniform(-1, 1, n) * env(n, 0.0, decay=0.15)
    return burst * 0.8 + thump * 0.6 + crackle * 0.5


def s_rise():
    # carillon + souffle ascendant ; la hauteur est transposée en jeu (degré de la gamme)
    return mix(0.8, (0.0, chime(A4, 0.8, 0.32), 0.7), (0.0, whoosh(0.45, 500, 3500, 0.6), 0.25),
               (0.04, koto(A4, 0.7, 0.995, 0.3), 0.35))


def s_lower():
    x = t(0.5)
    f = 660 * (2 / 3) ** (x / 0.5)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(len(x), 0.005, decay=0.18, release=0.05)
    return mix(0.5, (0.0, tone, 0.6), (0.0, whoosh(0.35, 2500, 400, 1.0), 0.18))


def s_validate():
    notes = [D4, G4, A4, D5, G5]
    parts = [(0.0, whoosh(0.9, 400, 4500, 0.5), 0.35)]
    for k, f in enumerate(notes):
        parts.append((0.06 + k * 0.085, koto(f, 1.3, 0.997), 0.55))
    parts.append((0.5, chime(D5 * 2, 1.0, 0.45), 0.22))
    return mix(1.7, *parts)


def s_deny():
    knock = koto(150, 0.25, 0.94, 0.9)
    click = lowpass(RNG.normal(0, 1, int(SR * 0.02)), 2500) * env(int(SR * 0.02), 0.0005, decay=0.004)
    return mix(0.25, (0.0, knock, 0.8), (0.0, click, 0.5))


def s_respec():
    rev = whoosh(0.8, 3500, 300, 0.9)[::-1]
    parts = [(0.0, rev, 0.4)]
    for k, f in enumerate([D5, A4, G4, D4]):
        parts.append((0.45 + k * 0.1, koto(f, 0.9, 0.995), 0.45))
    return mix(1.3, *parts)


def s_hover():
    n = int(SR * 0.06)
    return np.sin(2 * np.pi * 2400 * t(0.06)) * env(n, 0.001, decay=0.012)


def s_tab():
    n = int(SR * 0.22)
    paper = highpass(lowpass(RNG.normal(0, 1, n), 6000), 1800)
    e = env(n, 0.002, decay=0.03) + 0.7 * np.roll(env(n, 0.002, decay=0.025), int(SR * 0.07))
    return paper * e


def s_ambience():
    """Boucle de 12 s : grillons épars + vent très doux. Bords en fondu pour boucler sans clic."""
    dur = 12.0
    n = int(SR * dur)
    x = t(dur)
    wind = lowpass(RNG.normal(0, 1, n), 380 + 160 * np.sin(2 * np.pi * x / dur * 2)) * 0.6
    crickets = np.zeros(n)
    for _ in range(26):
        start = RNG.uniform(0, dur - 0.6)
        f = RNG.uniform(4200, 5200)
        chirps = RNG.integers(2, 5)
        for c in range(chirps):
            s0 = int(SR * (start + c * 0.11))
            ln = int(SR * 0.05)
            if s0 + ln >= n: break
            tt = np.arange(ln) / SR
            pulse = (np.sin(2 * np.pi * 34 * tt) > 0) * np.sin(np.pi * tt / (ln / SR))
            crickets[s0:s0 + ln] += np.sin(2 * np.pi * f * tt) * pulse * RNG.uniform(0.15, 0.4)
    out = wind + crickets
    fade = int(SR * 0.5)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade:] *= np.linspace(1, 0, fade)
    # fondu croisé : la fin recouvre le début pour une boucle sans couture
    loop = out[: n - fade].copy()
    loop[:fade] += out[n - fade:]
    return loop


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn, peak in [("open", s_open, 0.8), ("light", s_light, 0.7), ("rise", s_rise, 0.8),
                           ("lower", s_lower, 0.7), ("validate", s_validate, 0.85), ("deny", s_deny, 0.7),
                           ("respec", s_respec, 0.8), ("hover", s_hover, 0.4), ("tab", s_tab, 0.6),
                           ("ambience", s_ambience, 0.5)]:
        write(name, fn(), peak)
    print("->", OUT)
