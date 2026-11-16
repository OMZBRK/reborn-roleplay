"""Sons des mudras et de la porte scellée (mod reborn-hud, 14/11) : synthèse à nous, aucun échantillon repris.

  mudra/signe.ogg    : deux paumes qui se joignent, feutré et court (0,12 s). Adouci le 16/11 (« un son plus
                       subtil ») : plus de claquement sec, médiums seulement, niveau baissé de 9 dB.
  mudra/barre.ogg    : un souffle léger quand la barre s'ouvre (0,3 s).
  mudra/reussite.ogg : la suite est bonne : un bol grave et doux sur un fond sourd (1,2 s).
  mudra/echec.ogg    : les signes se dissipent : un souffle qui retombe (0,35 s).
  mudra/dalle.ogg    : la dalle de pierre coulisse : frottement continu, grenu (3,0 s = durée de la levée par défaut).
  mudra/choc.ogg     : la dalle s'arrête : un choc sourd (0,45 s).

Tous en mono (les sons de la dalle sont joués dans le monde par le serveur). Caractère pris sur l'étude
Maquettes/gameplan/ref_ketto_prologues/_etude_mudras_porte.md §2 : claquement large bande de 100 à 150 ms ; frottement
de pierre entre 500 et 1 650 Hz et entre 4 et 9 kHz ; choc sourd à l'arrêt.

Usage : python -X utf8 tools/ui-art/gen_mudra_sfx.py   (numpy et ffmpeg requis ; écrit les six .ogg du mod)
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
ICI = os.path.dirname(os.path.abspath(__file__))
SORTIE = os.path.normpath(os.path.join(ICI, "..", "..", "minecraft", "mod-hud", "src", "main", "resources", "assets",
                                       "reborn", "sounds", "mudra"))


def temps(duree):
    return np.arange(int(SR * duree)) / SR


def bande(bruit, bas, haut):
    """Bruit filtré dans le domaine des fréquences (flancs doux)."""
    spec = np.fft.rfft(bruit)
    f = np.fft.rfftfreq(len(bruit), 1 / SR)
    gain = np.clip((f - bas) / (0.3 * bas), 0, 1) * np.clip((haut - f) / (0.3 * haut), 0, 1)
    out = np.fft.irfft(spec * gain, len(bruit))
    return out / (np.max(np.abs(out)) + 1e-12)


def lent(rng, n, hz):
    """Enveloppe aléatoire lente (0 à 1), bornée à ~hz : le grain d'un frottement."""
    pts = max(4, int(n / SR * hz) + 3)
    v = rng.uniform(0, 1, pts)
    x = np.linspace(0, pts - 1, n)
    i = np.clip(x.astype(int), 0, pts - 2)
    t = x - i
    t = t * t * (3 - 2 * t)
    return v[i] * (1 - t) + v[i + 1] * t


def signe():
    rng = np.random.default_rng(1401)
    t = temps(0.12)
    # paumes feutrées : médiums seulement (plus rien au-dessus de 2,4 kHz), chute très courte
    claque = bande(rng.standard_normal(len(t)), 350, 2400) * np.exp(-t / 0.014)
    # second contact, 9 ms après : deux paumes ne se touchent jamais d'un seul coup
    d = int(0.009 * SR)
    echo = np.zeros_like(t)
    echo[d:] = bande(rng.standard_normal(len(t)), 300, 1500)[:-d] * np.exp(-t[:-d] / 0.02)
    creux = np.sin(2 * np.pi * 165 * t) * np.exp(-t / 0.035)
    son = claque + 0.4 * echo + 0.55 * creux
    return son * np.clip(t / 0.004, 0, 1) * np.clip((t[-1] - t) / 0.02, 0, 1)


def barre():
    rng = np.random.default_rng(1402)
    t = temps(0.30)
    b = rng.standard_normal(len(t))
    g = np.clip(t / 0.22, 0, 1)
    son = (1 - g) * bande(b, 300, 900) + g * bande(b, 900, 2600)
    return son * np.sin(np.pi * np.clip(t / t[-1], 0, 1)) ** 1.5


def reussite():
    rng = np.random.default_rng(1403)
    t = temps(1.2)
    son = np.zeros_like(t)
    # bol grave : fondamentale et deux partiels peu harmoniques, attaque douce
    for f, a, chute in ((294.0, 1.0, 0.42), (588.0 * 1.004, 0.42, 0.30), (811.0, 0.22, 0.20), (147.0, 0.35, 0.50)):
        son += a * np.exp(-t / chute) * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    son *= np.clip(t / 0.018, 0, 1)
    son += 0.25 * bande(rng.standard_normal(len(t)), 60, 140) * np.exp(-t / 0.35)
    return son * np.clip((t[-1] - t) / 0.2, 0, 1)


def echec():
    rng = np.random.default_rng(1404)
    t = temps(0.35)
    b = rng.standard_normal(len(t))
    g = np.clip(t / 0.25, 0, 1)
    son = (1 - g) * bande(b, 500, 1400) + g * bande(b, 150, 450)
    return son * np.clip(t / 0.015, 0, 1) * np.exp(-t / 0.11) * np.clip((t[-1] - t) / 0.06, 0, 1)


def dalle():
    rng = np.random.default_rng(1405)
    t = temps(3.0)
    n = len(t)
    b = rng.standard_normal(n)
    # trois couches de frottement, chacune avec son grain ; à-coups rapides par-dessus (la pierre accroche)
    grave = bande(b, 70, 220) * (0.55 + 0.45 * lent(rng, n, 5))
    corps = bande(rng.standard_normal(n), 500, 1650) * (0.35 + 0.65 * lent(rng, n, 14))
    aigu = bande(rng.standard_normal(n), 4000, 9000) * (0.2 + 0.8 * lent(rng, n, 30)) ** 2
    accroche = 0.6 + 0.4 * lent(rng, n, 45)
    son = (0.8 * grave + 1.0 * corps + 0.28 * aigu) * accroche
    return son * np.clip(t / 0.18, 0, 1) * np.clip((t[-1] - t) / 0.12, 0, 1)


def choc():
    rng = np.random.default_rng(1406)
    t = temps(0.45)
    f = 62.0 - 20.0 * np.clip(t / 0.2, 0, 1)
    son = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.13)
    son += 0.5 * bande(rng.standard_normal(len(t)), 120, 700) * np.exp(-t / 0.03)
    son += 0.12 * bande(rng.standard_normal(len(t)), 1500, 5000) * np.exp(-t / 0.012)
    return son * np.clip(t / 0.003, 0, 1) * np.clip((t[-1] - t) / 0.1, 0, 1)


def mesurer(nom, son):
    crete = 20 * np.log10(np.max(np.abs(son)))
    eff = 20 * np.log10(np.sqrt(np.mean(son ** 2)))
    print(f"{nom}: {len(son) / SR:.2f} s, crête {crete:.1f} dBFS, efficace {eff:.1f} dBFS, fin {abs(son[-1]):.5f}")


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
    ecrire("signe", signe(), 0.21)       # −13,5 dBFS (−4,4 avant le 16/11)
    ecrire("barre", barre(), 0.14)
    ecrire("reussite", reussite(), 0.36)
    ecrire("echec", echec(), 0.22)
    ecrire("dalle", dalle(), 0.62)
    ecrire("choc", choc(), 0.75)
