#!/usr/bin/env bash
# Lance la chaîne d'émotes dans Blender (mode --background, autoexec désactivé) sur le rig officiel d'Emotecraft.
# Usage : tools/emotes/emote.sh --spec tools/emotes/specs/<emote>.json [--out ...] [--renders ...] ...
#         tools/emotes/emote.sh --fit --spec ... (calage sur vidéo, voir fit_video.py)
# Chemins surchargeables : REBORN_BLENDER (blender.exe), REBORN_EMOTE_RIG (emote_creator.blend).
set -euo pipefail
ICI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BLENDER="${REBORN_BLENDER:-/c/Users/omarb/dev-cache/blender-5.2.2/blender-5.2.2-windows-x64/blender.exe}"
RIG="${REBORN_EMOTE_RIG:-/c/Users/omarb/dev-cache/emotecraft-rig-4f7d946e/emote_creator.blend}"
[ -x "$BLENDER" ] || { echo "Blender introuvable : $BLENDER (voir README, § Installation)" >&2; exit 1; }
[ -f "$RIG" ] || { echo "Rig introuvable : $RIG (voir README, § Installation)" >&2; exit 1; }
SCRIPT="$ICI/build_emote.py"
[ "${1:-}" = "--fit" ] && SCRIPT="$ICI/fit_video.py"
"$BLENDER" --background --disable-autoexec --factory-startup "$RIG" --python "$SCRIPT" -- "$@" 2>&1 \
  | grep -vE 'Read blend|Saved:|^Blender|^ *$' || true
