# Lance build_emote.py dans Blender (mode --background, autoexec désactivé) sur le rig officiel d'Emotecraft.
# Usage : .\tools\emotes\emote.ps1 --spec tools\emotes\specs\<emote>.json [--out ...] [--renders ...] ...
# Chemins surchargeables : $env:REBORN_BLENDER (blender.exe), $env:REBORN_EMOTE_RIG (emote_creator.blend).
$ici = Split-Path -Parent $MyInvocation.MyCommand.Path
$blender = if ($env:REBORN_BLENDER) { $env:REBORN_BLENDER } else { "C:\Users\omarb\dev-cache\blender-5.2.2\blender-5.2.2-windows-x64\blender.exe" }
$rig = if ($env:REBORN_EMOTE_RIG) { $env:REBORN_EMOTE_RIG } else { "C:\Users\omarb\dev-cache\emotecraft-rig-4f7d946e\emote_creator.blend" }
if (-not (Test-Path $blender)) { Write-Error "Blender introuvable : $blender (voir README, Installation)"; exit 1 }
if (-not (Test-Path $rig)) { Write-Error "Rig introuvable : $rig (voir README, Installation)"; exit 1 }
$script = if ($args.Count -gt 0 -and $args[0] -eq "--fit") { "fit_video.py" } else { "build_emote.py" }
& $blender --background --disable-autoexec --factory-startup $rig --python (Join-Path $ici $script) -- @args
