# publish-mod-manifest.ps1
#
# Workflow tout-en-un pour publier une nouvelle version du manifest des mods
# Reborn (ce que le launcher des joueurs telecharge automatiquement).
#
# Le script fait localement tout ce qui peut etre automatise sans
# credentials externes :
#
#   1. Build des mods Fabric (gradlew build)
#   2. Copie des jars dans une dir staging
#   3. Compute SHA256 + size + URL pour chaque jar
#   4. Genere le manifest unsigned JSON
#   5. Signe avec la cle privee Ed25519 locale (secrets/manifest_ed25519_private.pem)
#   6. Imprime les commandes a executer toi-meme pour :
#         - upload les jars sur GitHub Release
#         - POST le manifest signe sur l'API
#
# Usage :
#   ./scripts/publish-mod-manifest.ps1 -Version 1.1.0 [-Mods @("mod-ost","mod-integrity","mod-hud")]
#
# Pre-requis :
#   - JAVA_HOME pointe sur JDK 21 (Corretto 21)
#   - secrets/manifest_ed25519_private.pem present (cle privee Ed25519)
#   - pnpm install deja execute
#   - gh CLI installe et authentifie (pour l'upload)
#   - manifest-uploader compile (cargo build dans packages/manifest-uploader)
#
# Modifier la variable $BaseUrl plus bas pour pointer sur ton GitHub Releases.

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,

    [string[]]$Mods = @("mod-ost", "mod-integrity", "mod-hud"),

    [string]$McVersion = "1.21.1",

    [string]$MinLauncherVersion = "0.1.0",

    # Skip the Gradle build step (utile pour re-publier le manifest sans
    # re-builder les jars — par exemple pour ajouter une dep externe).
    [switch]$SkipBuild
)

# Dependances Fabric externes a inclure dans le manifest. Elles ne sont
# pas buildees ici — on les telecharge depuis leur repo Maven officiel
# puis on les pousse dans la GitHub Release a cote des nos jars pour que
# le launcher des joueurs puisse les recuperer.
#
# mcef-fabric : declare comme "mcef" dans son fabric.mod.json + bundle
# toutes les classes org/cef/* et com/cinemamod/mcef/*, donc un seul jar
# a shipper pour satisfaire la dep "mcef: *" du mod-hud (background
# dynamique 3D du main menu).
$ExternalDeps = @(
    @{
        Name = "mcef-fabric-2.1.6-1.21.1.jar"
        Url  = "https://mcef-download.cinemamod.com/repositories/releases/com/cinemamod/mcef-fabric/2.1.6-1.21.1/mcef-fabric-2.1.6-1.21.1.jar"
    }
)

$ErrorActionPreference = "Stop"

# Force JAVA_HOME sur Corretto 21 (mod Fabric 1.21.1 exige Java 21).
# Si Corretto 21 n'est pas a cet emplacement, ajuste ici.
$Jdk21Path = "C:\Program Files\Amazon Corretto\jdk21.0.9_10"
if (Test-Path $Jdk21Path) {
    $env:JAVA_HOME = $Jdk21Path
    $env:PATH = "$Jdk21Path\bin;$env:PATH"
    Write-Host "JAVA_HOME force a $Jdk21Path" -ForegroundColor Gray
} else {
    Write-Warning "Corretto 21 introuvable a $Jdk21Path - ajuste `$Jdk21Path dans le script."
}

# Configuration : ou les jars seront hostes une fois uploades.
# Format : un tag GitHub Release par version manifest.
$BaseUrl = "https://github.com/OMZBRK/reborn-roleplay/releases/download/mods-v$Version"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $RepoRoot
try {
    # ---------------------------------------------------------------
    # 1. Build des mods Fabric (skippable avec -SkipBuild)
    # ---------------------------------------------------------------
    if ($SkipBuild) {
        Write-Host "==> -SkipBuild active, reuse les jars deja presents dans build/libs/" -ForegroundColor Yellow
    } else {
        Write-Host "==> Build des mods Fabric..." -ForegroundColor Cyan
        foreach ($modId in $Mods) {
            $modDir = Join-Path $RepoRoot "minecraft\$modId"
            if (-not (Test-Path $modDir)) {
                throw "Mod $modId introuvable a $modDir"
            }
            Write-Host "    -> build $modId"
            Push-Location $modDir
            try {
                & ./gradlew clean build --quiet
                if ($LASTEXITCODE -ne 0) {
                    throw "Gradle build failed for $modId"
                }
            } finally {
                Pop-Location
            }
        }
    }

    # ---------------------------------------------------------------
    # 2. Staging : copie des jars dans secrets/manifest-staging/
    # ---------------------------------------------------------------
    $Staging = Join-Path $RepoRoot "secrets\manifest-staging"
    if (Test-Path $Staging) {
        # Sur Windows, Remove-Item -Recurse echoue parfois quand un shell
        # bash a le dossier comme cwd (lock OS). On nettoie alors les
        # fichiers un par un et on reutilise le dossier existant -- meme
        # resultat fonctionnel.
        try {
            Remove-Item $Staging -Recurse -Force -ErrorAction Stop
            New-Item -ItemType Directory -Path $Staging | Out-Null
        } catch {
            Write-Warning "Staging dir verrouille -- nettoyage par fichier au lieu de recreer."
            Get-ChildItem -Path $Staging -File -Force | Remove-Item -Force
        }
    } else {
        New-Item -ItemType Directory -Path $Staging | Out-Null
    }

    Write-Host "==> Staging des jars dans $Staging" -ForegroundColor Cyan
    foreach ($modId in $Mods) {
        $libsDir = Join-Path $RepoRoot "minecraft\$modId\build\libs"
        # On copie uniquement le jar de release (pas le -sources.jar).
        $jar = Get-ChildItem -Path $libsDir -Filter "*.jar" |
            Where-Object { $_.Name -notmatch "-sources\.jar$" } |
            Select-Object -First 1
        if (-not $jar) {
            throw "Aucun .jar release trouve dans $libsDir"
        }
        Copy-Item $jar.FullName $Staging
        $sizeMb = [Math]::Round($jar.Length / 1048576, 1)
        Write-Host ("    -> copie " + $jar.Name + " (" + $sizeMb + " MB)")
    }

    # ---------------------------------------------------------------
    # 2b. Telecharge les dependances externes (mcef-fabric, etc.)
    # ---------------------------------------------------------------
    if ($ExternalDeps.Count -gt 0) {
        Write-Host "==> Telechargement des dependances externes" -ForegroundColor Cyan
        foreach ($dep in $ExternalDeps) {
            $depPath = Join-Path $Staging $dep.Name
            Write-Host ("    -> DL " + $dep.Name + " depuis " + $dep.Url)
            try {
                Invoke-WebRequest -Uri $dep.Url -OutFile $depPath -UseBasicParsing
            } catch {
                throw "Echec DL de $($dep.Name) : $_"
            }
            $depKb = [Math]::Round((Get-Item $depPath).Length / 1024, 1)
            Write-Host ("       OK (" + $depKb + " KB)")
        }
    }

    # ---------------------------------------------------------------
    # 3 + 4. Build unsigned manifest via le script TypeScript existant
    # ---------------------------------------------------------------
    $Unsigned = Join-Path $RepoRoot "secrets\manifest-unsigned.json"
    Write-Host "==> Generation du manifest unsigned" -ForegroundColor Cyan
    Push-Location (Join-Path $RepoRoot "packages\manifest-signer")
    try {
        pnpm exec tsx src/build-from-folder.ts $Staging `
            --base-url $BaseUrl `
            --version $Version `
            --mc $McVersion `
            --out $Unsigned
        if ($LASTEXITCODE -ne 0) { throw "build-from-folder.ts failed" }
    } finally {
        Pop-Location
    }

    # ---------------------------------------------------------------
    # 5. Sign avec la cle privee Ed25519
    # ---------------------------------------------------------------
    $PrivateKey = Join-Path $RepoRoot "secrets\manifest_ed25519_private.pem"
    $Signed = Join-Path $RepoRoot "secrets\manifest-signed.json"
    if (-not (Test-Path $PrivateKey)) {
        throw "Cle privee absente : $PrivateKey. Generer avec : pnpm exec tsx packages/manifest-signer/src/cli.ts gen-keys --out-dir secrets"
    }

    Write-Host "==> Signature Ed25519" -ForegroundColor Cyan
    Push-Location (Join-Path $RepoRoot "packages\manifest-signer")
    try {
        pnpm exec tsx src/cli.ts sign $Unsigned --key $PrivateKey --out $Signed
        if ($LASTEXITCODE -ne 0) { throw "sign failed" }
    } finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host "  Manifest signe pret : $Signed" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Green
    Write-Host ""

    # ---------------------------------------------------------------
    # 6. Imprime les commandes a executer manuellement
    # ---------------------------------------------------------------
    Write-Host "ETAPES MANUELLES RESTANTES :" -ForegroundColor Yellow
    Write-Host ""

    Write-Host "  [A] Upload les jars sur GitHub Release" -ForegroundColor Yellow
    Write-Host "      Necessite gh CLI authentifie." -ForegroundColor Gray
    Write-Host ""
    Write-Host "      gh release create mods-v$Version ``" -ForegroundColor White
    foreach ($modId in $Mods) {
        $jar = Get-ChildItem -Path (Join-Path $RepoRoot "minecraft\$modId\build\libs") `
                -Filter "*.jar" |
            Where-Object { $_.Name -notmatch "-sources\.jar$" } |
            Select-Object -First 1
        Write-Host "        '$($jar.FullName)' ``" -ForegroundColor White
    }
    foreach ($dep in $ExternalDeps) {
        Write-Host "        '$(Join-Path $Staging $dep.Name)' ``" -ForegroundColor White
    }
    Write-Host "        --title 'Mods $Version' ``" -ForegroundColor White
    Write-Host "        --notes 'Auto-generated by publish-mod-manifest.ps1'" -ForegroundColor White
    Write-Host ""

    Write-Host "  [B] Publier le manifest signe sur l'API" -ForegroundColor Yellow
    Write-Host "      Necessite manifest-uploader compile + JWT staff dans Windows Credential Manager." -ForegroundColor Gray
    Write-Host ""
    Write-Host "      ./packages/manifest-uploader/target/release/manifest-uploader.exe ``" -ForegroundColor White
    Write-Host "        manifest --file $Signed" -ForegroundColor White
    Write-Host ""

    Write-Host "  [C] Verification cote launcher" -ForegroundColor Yellow
    Write-Host "      Apres [A] + [B] : un launcher en dev (pnpm launcher:dev) devrait DL automatiquement les nouveaux jars au prochain lancement." -ForegroundColor Gray

} finally {
    Pop-Location
}
