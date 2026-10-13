# -*- coding: utf-8 -*-
"""Voix off (provisoire, générée) des questions de la création de personnage.

    python tools/gen_voix_creation.py            -> toutes les étapes
    python tools/gen_voix_creation.py nom age    -> seulement ces étapes

Une phrase par étape de SceneJourneyScreen (TITLES, mêmes textes, casse de lecture), lue par la voix
Windows française « Microsoft Hortense Desktop » (System.Speech, débit un peu lent), puis convertie par
ffmpeg en OGG Vorbis mono 44,1 kHz : silences de début/fin coupés, 60 ms d'attaque, 250 ms de queue.

Sortie : src/main/resources/assets/reborn/sounds/voix/creation_<étape>.ogg
Événements sounds.json : reborn:voix.creation_<étape> (à ajouter à la main si on crée une étape).

Remplaçable tel quel par un vrai doublage : garder les noms de fichiers.
Prérequis : Windows (voix Hortense installée), ffmpeg dans le PATH.
"""
from __future__ import annotations

import base64
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ICI = Path(__file__).resolve().parent
SORTIE = ICI.parent / "src/main/resources/assets/reborn/sounds/voix"

VOIX = "Microsoft Hortense Desktop"
DEBIT = -2          # SpeechSynthesizer.Rate : -10 … 10 (0 = normal) ; -2 = un peu lent, posé
QUALITE = 2         # libvorbis -q:a (0 … 10) : 2 ≈ 64 kb/s, largement suffisant pour une voix mono

# Ordre = étapes de SceneJourneyScreen (S_VILLAGE … S_RECAP). « intro » = les trois murmures d'ouverture.
PHRASES = {
    "intro": "Avant le nom… avant le sang… il n'y avait que le chakra.",
    "village": "De quel village viens-tu ?",
    "clan": "Quel sang coule en toi ?",
    "sexe": "Qui es-tu ?",
    "nom": "Quel est ton nom ?",
    "age": "Quel âge as-tu ?",
    "taille": "Quelle est ta taille ?",
    "apparence": "À quoi ressembles-tu ?",
    "recap": "Est-ce bien toi ?",
}

PS = r"""
$ProgressPreference = 'SilentlyContinue'
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice('{voix}')
$s.Rate = {debit}
$s.Volume = 100
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(44100, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$s.SetOutputToWaveFile('{wav}', $fmt)
$s.Speak('{texte}')
$s.SetOutputToNull()
$s.Dispose()
"""


def ps_quote(s: str) -> str:
    return s.replace("'", "''")


def synthese(texte: str, wav: Path) -> None:
    script = PS.format(voix=ps_quote(VOIX), debit=DEBIT, wav=ps_quote(str(wav)), texte=ps_quote(texte))
    enc = base64.b64encode(script.encode("utf-16-le")).decode("ascii")
    subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", enc], check=True)


def vers_ogg(wav: Path, ogg: Path) -> None:
    filtre = ("silenceremove=start_periods=1:start_threshold=-48dB:start_silence=0.02,"
              "areverse,silenceremove=start_periods=1:start_threshold=-48dB:start_silence=0.05,areverse,"
              "afade=t=in:d=0.02,adelay=60,apad=pad_dur=0.25")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-af", filtre, "-ac", "1", "-ar", "44100",
                    "-c:a", "libvorbis", "-q:a", str(QUALITE), str(ogg)], check=True)


def main(etapes: list[str]) -> None:
    SORTIE.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for cle in etapes or list(PHRASES):
            texte = PHRASES[cle]
            wav = Path(tmp) / f"{cle}.wav"
            ogg = SORTIE / f"creation_{cle}.ogg"
            synthese(texte, wav)
            vers_ogg(wav, ogg)
            print(f"{ogg.name:28s} {os.path.getsize(ogg):6d} o  « {texte} »")


if __name__ == "__main__":
    main(sys.argv[1:])
