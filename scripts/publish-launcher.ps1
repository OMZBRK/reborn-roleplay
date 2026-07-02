# publish-launcher.ps1
#
# Workflow tout-en-un pour publier une nouvelle version du launcher Reborn.
# Pendant : build NSIS avec env vars _BUILD bakees, signature Ed25519,
# upload GitHub Release, POST /v1/admin/releases via manifest-uploader.
#
# Pre-requis :
#   - JAVA_HOME pas necessaire (build Tauri pas Java)
#   - secrets/tauri-updater.key present + password connu (cf .env)
#   - .env racine avec MS_CLIENT_ID, DISCORD_CLIENT_ID, REBORN_SERVER_HOST,
#     REBORN_SERVER_PORT
#   - secrets/manifest_ed25519_public.hex present
#   - gh CLI authentifie (gh auth status)
#   - manifest-uploader compile (cargo build --release dans
#     packages/manifest-uploader)
#   - Version dans 3 fichiers (package.json, tauri.conf.json, Cargo.toml)
#     deja bumpee a la version cible AVANT lancement du script
#
# Usage :
#   ./scripts/publish-launcher.ps1 -Version 0.3.2 -Notes "Hotfix XYZ"
#
# Sans -ApiBase, pointe sur prod (api.reborn-rp.com/v1).

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,

    [string]$Target = "windows-x86_64",

    [string]$Channel = "stable",

    [string]$ApiUrl = "https://api.reborn-rp.com/v1",

    [string]$Notes = "",

    # Skip le build si on a deja build manuellement (debug).
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $RepoRoot
try {
    # ---------------------------------------------------------------
    # 0. Charge .env pour recuperer les valeurs a baker
    # ---------------------------------------------------------------
    $envFile = Join-Path $RepoRoot ".env"
    if (-not (Test-Path $envFile)) {
        throw ".env introuvable a $envFile -- impossible de baker les env vars."
    }

    $envMap = @{}
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $k = $line.Substring(0, $idx).Trim()
            $v = $line.Substring($idx + 1).Trim()
            # Strip quotes optionnels
            if ($v.StartsWith('"') -and $v.EndsWith('"')) {
                $v = $v.Substring(1, $v.Length - 2)
            }
            $envMap[$k] = $v
        }
    }

    $required = @("MS_CLIENT_ID", "DISCORD_CLIENT_ID", "REBORN_SERVER_HOST", "REBORN_SERVER_PORT")
    foreach ($k in $required) {
        if (-not $envMap.ContainsKey($k) -or -not $envMap[$k]) {
            throw ".env missing $k -- requis pour bake."
        }
    }

    $pubkeyFile = Join-Path $RepoRoot "secrets\manifest_ed25519_public.hex"
    if (-not (Test-Path $pubkeyFile)) {
        throw "secrets/manifest_ed25519_public.hex introuvable."
    }
    $manifestPubkeyHex = (Get-Content $pubkeyFile -Raw).Trim()

    # ---------------------------------------------------------------
    # 1. Build NSIS avec env vars _BUILD bakees
    # ---------------------------------------------------------------
    if ($SkipBuild) {
        Write-Host "==> -SkipBuild active, reuse le .exe deja present" -ForegroundColor Yellow
    } else {
        Write-Host "==> Build launcher v$Version (Tauri NSIS)" -ForegroundColor Cyan
        Write-Host "    MS_CLIENT_ID_BUILD     = $($envMap['MS_CLIENT_ID'])" -ForegroundColor Gray
        Write-Host "    DISCORD_CLIENT_ID_BUILD= $($envMap['DISCORD_CLIENT_ID'])" -ForegroundColor Gray
        Write-Host "    REBORN_SERVER_HOST_BUILD= $($envMap['REBORN_SERVER_HOST'])" -ForegroundColor Gray
        Write-Host "    REBORN_SERVER_PORT_BUILD= $($envMap['REBORN_SERVER_PORT'])" -ForegroundColor Gray
        Write-Host "    REBORN_API_URL_BUILD   = $ApiUrl" -ForegroundColor Gray
        Write-Host "    MANIFEST_PUBLIC_KEY_HEX_BUILD = $($manifestPubkeyHex.Substring(0, 32))..." -ForegroundColor Gray

        $env:MS_CLIENT_ID_BUILD = $envMap['MS_CLIENT_ID']
        $env:DISCORD_CLIENT_ID_BUILD = $envMap['DISCORD_CLIENT_ID']
        $env:REBORN_SERVER_HOST_BUILD = $envMap['REBORN_SERVER_HOST']
        $env:REBORN_SERVER_PORT_BUILD = $envMap['REBORN_SERVER_PORT']
        $env:REBORN_API_URL_BUILD = $ApiUrl
        $env:MANIFEST_PUBLIC_KEY_HEX_BUILD = $manifestPubkeyHex
        # Serveur DEV (toggle Build/Dev staff-only). Optionnel : si absent du
        # .env, le build sort sans serveur dev (aucun toggle cote mod).
        if ($envMap.ContainsKey('REBORN_SERVER_DEV_HOST') -and $envMap['REBORN_SERVER_DEV_HOST']) {
            $env:REBORN_SERVER_DEV_HOST_BUILD = $envMap['REBORN_SERVER_DEV_HOST']
            $env:REBORN_SERVER_DEV_PORT_BUILD = $envMap['REBORN_SERVER_DEV_PORT']
            Write-Host "    REBORN_SERVER_DEV_HOST_BUILD= $($envMap['REBORN_SERVER_DEV_HOST']):$($envMap['REBORN_SERVER_DEV_PORT'])" -ForegroundColor Gray
        }

        # NB : on NE positionne PAS TAURI_SIGNING_PRIVATE_KEY ici, parce
        # que la valeur dans .env est un chemin (et tauri attend la cle
        # base64). Le signage se fait au step 2 via le flag -f explicite.

        # PS 5.1 traite la stderr des native commands comme NativeCommandError
        # avec $ErrorActionPreference=Stop -> n'importe quel "Info" Tauri sur
        # stderr fait planter le script. On baisse temporairement le niveau
        # le temps du build, et on s'appuie sur $LASTEXITCODE pour la verite.
        $oldEAP = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & pnpm launcher:build
        } finally {
            $ErrorActionPreference = $oldEAP
        }
        if ($LASTEXITCODE -ne 0) {
            throw "Build launcher failed (exit $LASTEXITCODE)."
        }
    }

    # ---------------------------------------------------------------
    # 2. Signe le .exe
    # ---------------------------------------------------------------
    $exePath = Join-Path $RepoRoot "apps\launcher\src-tauri\target\release\bundle\nsis\RebornLauncher_${Version}_x64-setup.exe"
    if (-not (Test-Path $exePath)) {
        throw "EXE introuvable a $exePath -- le build a-t-il bien produit la bonne version ?"
    }

    $keyPath = Join-Path $RepoRoot "secrets\tauri-updater.key"
    if (-not (Test-Path $keyPath)) {
        throw "Cle privee Tauri introuvable : $keyPath"
    }

    $signPwd = if ($envMap.ContainsKey("TAURI_SIGNING_PRIVATE_KEY_PASSWORD")) {
        $envMap["TAURI_SIGNING_PRIVATE_KEY_PASSWORD"]
    } else {
        ""
    }

    Write-Host "==> Signature Ed25519 du binaire" -ForegroundColor Cyan
    # On unset l'env TAURI_SIGNING_PRIVATE_KEY (s'il pointe sur un chemin
    # dans .env) pour ne pas override le flag -f.
    $oldKeyEnv = $env:TAURI_SIGNING_PRIVATE_KEY
    Remove-Item Env:\TAURI_SIGNING_PRIVATE_KEY -ErrorAction SilentlyContinue
    try {
        Push-Location (Join-Path $RepoRoot "apps\launcher")
        try {
            $signArgs = @("signer", "sign", "-f", "../../secrets/tauri-updater.key")
            if ($signPwd) { $signArgs += @("-p", $signPwd) }
            $signArgs += $exePath
            & pnpm exec tauri @signArgs
            if ($LASTEXITCODE -ne 0) {
                throw "tauri signer sign failed (code $LASTEXITCODE)."
            }
        } finally {
            Pop-Location
        }
    } finally {
        if ($oldKeyEnv) { $env:TAURI_SIGNING_PRIVATE_KEY = $oldKeyEnv }
    }

    $sigPath = "$exePath.sig"
    if (-not (Test-Path $sigPath)) {
        throw "Signature pas generee : $sigPath introuvable."
    }
    $sigContent = (Get-Content $sigPath -Raw).Trim()

    # ---------------------------------------------------------------
    # 3. GitHub Release (upload .exe + .sig)
    # ---------------------------------------------------------------
    $tag = "v$Version"
    $title = "Launcher v$Version"
    if ($Notes -and $Notes.Length -gt 0) {
        $title = "Launcher v$Version - $($Notes.Substring(0, [Math]::Min(40, $Notes.Length)))..."
    }

    Write-Host "==> GitHub Release $tag" -ForegroundColor Cyan
    & gh release create $tag $exePath $sigPath --title $title --notes ($Notes | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "gh release create failed (peut-etre le tag existe deja ?)."
    }

    # ---------------------------------------------------------------
    # 4. POST /v1/admin/releases via manifest-uploader
    # ---------------------------------------------------------------
    $url = "https://github.com/OMZBRK/reborn-roleplay/releases/download/$tag/RebornLauncher_${Version}_x64-setup.exe"
    $uploader = Join-Path $RepoRoot "packages\manifest-uploader\target\release\manifest-uploader.exe"
    if (-not (Test-Path $uploader)) {
        throw "manifest-uploader pas compile -- run cargo build --release dans packages/manifest-uploader."
    }

    Write-Host "==> POST $ApiUrl/admin/releases" -ForegroundColor Cyan
    & $uploader release `
        --version $Version `
        --target $Target `
        --channel $Channel `
        --url $url `
        --signature $sigContent `
        --notes $Notes
    if ($LASTEXITCODE -ne 0) {
        throw "manifest-uploader release failed."
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  Launcher v$Version publie -- isCurrent pour $Target" -ForegroundColor Green
    Write-Host "  URL : $url" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
} finally {
    Pop-Location
}
