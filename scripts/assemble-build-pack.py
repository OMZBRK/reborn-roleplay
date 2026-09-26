#!/usr/bin/env python3
"""Assemble le manifest (non signé) du PACK BUILD 26.3 du launcher multi-version.

Le serveur "build" (staff, MC 26.3) tourne sur un modpack minimal : Axiom + opti
/ shaders + reborn-hud (build-mode). AUCUN mod RP (emotes, PlasmoVoice, Bendable
Cuboids…). Sortie = un manifest au même format que le pack RP, à signer ensuite
avec packages/manifest-signer.

Usage :
    python scripts/assemble-build-pack.py [stage_dir]

- stage_dir : où télécharger/mettre en cache les jars (défaut D:/dev-cache/tmp/
  buildpack ; ⚠️ NE PAS utiliser C: qui est souvent plein sur ce poste).
- Écrit secrets/manifest-build-unsigned-v1.json (gitignoré).
- Les mods tiers pointent vers le CDN Modrinth (pas de ré-hébergement) ; SEUL
  reborn-hud est hébergé par nous (release GitHub `build-pack-v1`).

Publication ensuite : voir docs/BUILD_PACK_26.3.md.
"""
import sys, os, json, hashlib, urllib.request

# (filename, url Modrinth, required). required=True → toujours installé.
MODS = [
    ("fabric-api-0.161.0+26.3.jar", "https://cdn.modrinth.com/data/P7dR8mSH/versions/bNnaTiuM/fabric-api-0.161.0%2B26.3.jar", True),
    ("sodium-fabric-0.9.3-alpha.1+mc26.3.jar", "https://cdn.modrinth.com/data/AANobbMI/versions/v4PSXean/sodium-fabric-0.9.3-alpha.1%2Bmc26.3.jar", True),
    ("lithium-fabric-0.26.1+mc26.3.jar", "https://cdn.modrinth.com/data/gvQqBUqZ/versions/WXHRsMRl/lithium-fabric-0.26.1%2Bmc26.3.jar", True),
    ("ferritecore-9.0.0-fabric.jar", "https://cdn.modrinth.com/data/uXXizFIs/versions/d5ddUdiB/ferritecore-9.0.0-fabric.jar", True),
    ("yet_another_config_lib_v3-3.9.7+26.3-fabric.jar", "https://cdn.modrinth.com/data/1eAoo2KR/versions/s9SjoFu1/yet_another_config_lib_v3-3.9.7%2B26.3-fabric.jar", True),
    ("Axiom-6.1.3-for-MC26.3.jar", "https://cdn.modrinth.com/data/N6n5dqoA/versions/CrfjL7cY/Axiom-6.1.3-for-MC26.3.jar", True),
    ("iris-fabric-1.11.6+mc26.3.jar", "https://cdn.modrinth.com/data/YL57xq9U/versions/bAdKrpw8/iris-fabric-1.11.6%2Bmc26.3.jar", False),
    ("sodium-extra-fabric-0.9.4+mc26.3.jar", "https://cdn.modrinth.com/data/PtjYWJkn/versions/te2y9qZn/sodium-extra-fabric-0.9.4%2Bmc26.3.jar", False),
    ("DistantHorizons-3.3.2-26.3-fabric-neoforge.jar", "https://cdn.modrinth.com/data/uCdwusMi/versions/gfi11b05/DistantHorizons-3.3.2-26.3-fabric-neoforge.jar", False),
    ("entityculling-fabric-1.11.2-mc26.3.jar", "https://cdn.modrinth.com/data/NNAgCjsB/versions/F4loCvYt/entityculling-fabric-1.11.2-mc26.3.jar", False),
    ("zoomify-2.16.3+26.3.jar", "https://cdn.modrinth.com/data/w7ThoJFB/versions/bvz5KJLQ/zoomify-2.16.3%2B26.3.jar", False),
]

# reborn-hud 26.3 (build-mode) : jar local (buildé depuis feature/migrate-26.3),
# hébergé à la publication sur la release GitHub build-pack-v1.
REBORN_HUD_JAR = "minecraft/mod-hud/build/libs/reborn-hud-0.4.136.jar"
REBORN_HUD_URL = "https://github.com/OMZBRK/reborn-roleplay/releases/download/build-pack-v1/reborn-hud-0.4.136.jar"

OUT = "secrets/manifest-build-unsigned-v1.json"


def sha256_size(path):
    h = hashlib.sha256()
    n = 0
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
            n += len(chunk)
    return h.hexdigest(), n


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    stage = sys.argv[1] if len(sys.argv) > 1 else "/d/dev-cache/tmp/buildpack"
    os.makedirs(stage, exist_ok=True)
    files, errors = [], []
    for fn, url, req in MODS:
        dest = os.path.join(stage, fn)
        try:
            if not os.path.exists(dest) or os.path.getsize(dest) == 0:
                rq = urllib.request.Request(url, headers={"User-Agent": "reborn-buildpack/1.0"})
                with urllib.request.urlopen(rq, timeout=180) as r, open(dest, "wb") as out:
                    out.write(r.read())
            sha, size = sha256_size(dest)
            files.append({"path": f"mods/{fn}", "sha256": sha, "size": size,
                          "url": "https://cdn.modrinth.com/" + "/".join(url.split("/")[3:]),
                          "required": req})
            print(f"OK  {fn:52} {size:>10}")
        except Exception as e:
            errors.append((fn, str(e)))
            print(f"ERR {fn}: {e}")

    sha, size = sha256_size(REBORN_HUD_JAR)
    files.append({"path": f"mods/{os.path.basename(REBORN_HUD_JAR)}",
                  "sha256": sha, "size": size, "url": REBORN_HUD_URL, "required": True})
    print(f"OK  {os.path.basename(REBORN_HUD_JAR)} (local) {size:>10}")

    files.sort(key=lambda f: f["path"])
    manifest = {
        "version": "1.0.0",
        "minecraftVersion": "26.3",
        "issuedAt": "__SET_AT_PUBLISH__",     # regénéré au sign (timestamps frais)
        "expiresAt": "__SET_AT_PUBLISH__",
        "minLauncherVersion": "0.3.43",        # = launcher multi-version publié
        "files": files,
    }
    json.dump(manifest, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"\n{len(files)} fichiers, {sum(f['size'] for f in files) / 1e6:.1f} Mo → {OUT}")
    if errors:
        print("ERREURS:", errors)
        sys.exit(1)


if __name__ == "__main__":
    main()
