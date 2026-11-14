"""Sons du Byakugan (mod reborn-hud, 8e tour du 14/11) : synthèse à nous, aucun échantillon repris.

  byakugan/ouverture.ogg : un tintement de cristal frappé net, tenu une demi-seconde, puis une longue résonance (~2,2 s).
  byakugan/fermeture.ogg : une retombée sourde et brève (~0,5 s), avec un écho grave du tintement.

Caractère mesuré sur la référence (Maquettes/gameplan/17_lot5_mobilite/_etude_byakugan_v3_hd.md §5) : partiels stables
autour de 1,4 kHz et de ses multiples, corps grave vers 150 à 210 Hz, bande 2 à 5 kHz la plus forte, très peu au-dessus de
10 kHz, décroissance régulière d'environ −35 dB par seconde, les aigus d'abord.

Usage : python -X utf8 tools/ui-art/gen_byakugan_sfx.py   (numpy et ffmpeg requis ; écrit les deux .ogg du mod)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
ICI = os.path.dirname(os.path.abspath(__file__))
SORTIE = os.path.normpath(os.path.join(ICI, "..", "..", "minecraft", "mod-hud", "src", "main", "resources", "assets",
                                       "reborn", "sounds", "byakugan"))


def temps(duree):
    return np.arange(int(SR * duree)) / SR


def passe_bande(bruit, bas, haut):
    """Bruit filtré dans le domaine des fréquences (fenêtre douce)."""
    spec = np.fft.rfft(bruit)
    f = np.fft.rfftfreq(len(bruit), 1 / SR)
    gain = np.clip((f - bas) / (0.25 * bas), 0, 1) * np.clip((haut - f) / (0.25 * haut), 0, 1)
    return np.fft.irfft(spec * gain, len(bruit))


def ouverture():
    rng = np.random.default_rng(1411)
    t = temps(2.25)
    son = np.zeros_like(t)
    # partiels du cristal : (fréquence, niveau, temps de chute en s) ; deux voix à peine désaccordées battent lentement
    partiels = [(1397.0, 0.55, 0.62), (2794.0 * 1.003, 1.0, 0.50), (4191.0 * 1.007, 0.62, 0.36), (5588.0 * 1.012, 0.30, 0.24),
                (6985.0 * 1.02, 0.16, 0.17), (656.0, 0.30, 0.70), (1716.0, 0.28, 0.48), (2957.0, 0.36, 0.40),
                (3436.0, 0.22, 0.32)]
    for f, a, chute in partiels:
        for desaccord, part in ((1.0, 0.6), (1.0018, 0.4)):
            ph = rng.uniform(0, 2 * np.pi)
            # tenue d'une demi-seconde, puis chute exponentielle (les aigus d'abord)
            env = np.where(t < 0.5, 1.0 - 0.25 * t / 0.5, 0.75 * np.exp(-(t - 0.5) / chute))
            son += a * part * env * np.sin(2 * np.pi * f * desaccord * t + ph)
    # corps grave
    for f, a in ((152.0, 0.22), (211.0, 0.16)):
        son += a * np.exp(-t / 0.55) * np.sin(2 * np.pi * f * t)
    # attaque : 35 ms, et un souffle bref (2 à 8 kHz) pour le « shing »
    son *= np.clip(t / 0.035, 0, 1) ** 1.5
    souffle = passe_bande(rng.standard_normal(len(t)), 2000, 8000)
    souffle /= np.max(np.abs(souffle))
    son += 0.5 * souffle * np.clip(t / 0.012, 0, 1) * np.exp(-t / 0.045)
    # passe-bas doux au-dessus de 9 kHz, fin à zéro
    spec = np.fft.rfft(son)
    f = np.fft.rfftfreq(len(son), 1 / SR)
    son = np.fft.irfft(spec / (1 + (f / 9000.0) ** 6), len(son))
    son *= np.clip((t[-1] - t) / 0.25, 0, 1)
    return son


def fermeture():
    rng = np.random.default_rng(1412)
    t = temps(0.55)
    # souffle qui retombe : bruit dont la bande glisse de ~800 à ~200 Hz
    bruit = rng.standard_normal(len(t))
    haut = passe_bande(bruit, 500, 1100)
    bas = passe_bande(bruit, 140, 420)
    glisse = np.clip(t / 0.30, 0, 1)
    souffle = (1 - glisse) * haut / np.max(np.abs(haut)) + glisse * bas / np.max(np.abs(bas))
    son = 0.55 * souffle * np.clip(t / 0.02, 0, 1) * np.exp(-t / 0.16)
    # choc sourd qui descend
    f = 170.0 - 50.0 * np.clip(t / 0.25, 0, 1)
    son += 0.8 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip(t / 0.01, 0, 1) * np.exp(-t / 0.11)
    # écho grave du tintement, une octave plus bas
    for fr, a in ((698.5, 0.16), (1397.0, 0.07)):
        son += a * np.exp(-t / 0.14) * np.sin(2 * np.pi * fr * t)
    son *= np.clip((t[-1] - t) / 0.08, 0, 1)
    return son


def mesurer(nom, son):
    crete = 20 * np.log10(np.max(np.abs(son)))
    eff = 20 * np.log10(np.sqrt(np.mean(son ** 2)))
    spec = np.abs(np.fft.rfft(son)) ** 2
    f = np.fft.rfftfreq(len(son), 1 / SR)
    bandes = [(80, 250), (250, 700), (700, 2000), (2000, 5000), (5000, 10000), (10000, 16000)]
    e = [10 * np.log10(spec[(f >= a) & (f < b)].sum() + 1e-12) for a, b in bandes]
    e = [round(x - max(e), 1) for x in e]
    print(f"{nom}: {len(son) / SR:.2f} s, crête {crete:.1f} dBFS, efficace {eff:.1f} dBFS, continu {son.mean():.5f}, "
          f"bandes relatives {e} dB, fin {abs(son[-1]):.5f}")


def ecrire(nom, son, crete):
    son = son - son.mean()
    son = son / np.max(np.abs(son)) * crete
    mesurer(nom, son)
    os.makedirs(SORTIE, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, nom + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((son * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(SORTIE, nom + ".ogg")], check=True)


if __name__ == "__main__":
    ecrire("ouverture", ouverture(), 0.70)   # −3 dBFS
    ecrire("fermeture", fermeture(), 0.45)   # −7 dBFS : nettement plus discret
