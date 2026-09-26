# Pack build 26.3 — modpack du serveur Build (launcher multi-version)

> Le launcher multi-version (`feature/launcher-multiversion`) expose deux serveurs :
> **RP** (MC 26.2, pack existant) et **Build** (MC 26.3, staff). Ce document décrit
> le pack du serveur Build et comment le publier. Complément de
> [`../memory` launcher-multiversion] et de `MIGRATION_26.3.md`.

## Composition (12 mods, ~109 Mo)

Pack **minimal** : outils de build + optimisation, **aucun mod RP** (pas d'emotes,
PlasmoVoice, Bendable Cuboids, mods de skin…). Bien plus léger que le pack RP.

**Requis** (`required: true`) :
- `fabric-api-0.161.0+26.3`
- `Axiom-6.1.3-for-MC26.3` — **l'outil de build**, la raison d'être du serveur
- `sodium-fabric-0.9.3-alpha.1+mc26.3` ⚠️ **alpha** (seul build Sodium 26.3 dispo)
- `lithium-fabric-0.26.1+mc26.3`
- `ferritecore-9.0.0-fabric`
- `yet_another_config_lib_v3-3.9.7+26.3` (dép de config)
- `reborn-hud-0.4.136` (build-mode : coupe l'UI RP hors serveur RP ; fournit le menu
  branded + le flux ConnectScreen)

**Optionnels** (`required: false`, opt-in via l'onglet Mods) :
- `iris-fabric-1.11.6+mc26.3` (shaders) + `sodium-extra-fabric-0.9.4+mc26.3`
- `DistantHorizons-3.3.2-26.3` (LOD longue distance, agréable pour construire)
- `entityculling-fabric-1.11.2-mc26.3`
- `zoomify-2.16.3+26.3`

Les mods tiers pointent vers le **CDN Modrinth** (pas de ré-hébergement). Seul
`reborn-hud` est hébergé par nous (release GitHub `build-pack-v1`).

## Assemblage (fait — draft prêt)

`python scripts/assemble-build-pack.py` télécharge/hash chaque jar et écrit
`secrets/manifest-build-unsigned-v1.json` (gitignoré). **Déjà généré** avec les
sha256/tailles réels. reborn-hud vient de `minecraft/mod-hud/build/libs/
reborn-hud-0.4.136.jar` (buildé sur `feature/migrate-26.3`).

⚠️ Stager sur **D:** (`/d/dev-cache/tmp/buildpack`) — C: est plein sur ce poste.

## Publication (BLOQUÉ tant que le serveur build 26.3 n'existe pas)

Prérequis **côté user** : un serveur **Purpur/Paper 26.3** sur Minestrator (Axiom
+ FAWE ; pas besoin des plugins RP). Fournir son adresse + accès SFTP.

Puis (actions sortantes = feu vert user) :

1. **Signer** le manifest avec des timestamps frais + `minLauncherVersion` = version
   du launcher multi-version qu'on publie. Remplacer `__SET_AT_PUBLISH__` puis :
   ```pwsh
   cd packages/manifest-signer
   pnpm exec tsx src/cli.ts sign ../../secrets/manifest-build-unsigned-v1.json `
     --key ../../secrets/manifest_ed25519_private.pem --out ../../secrets/manifest-build-signed-v1.json
   pnpm exec tsx src/cli.ts verify ../../secrets/manifest-build-signed-v1.json --pub ../../secrets/manifest_ed25519_public.pem
   ```
2. **Release GitHub `build-pack-v1`** portant DEUX assets : `reborn-hud-0.4.136.jar`
   (référencé par le manifest) ET `manifest-build-signed-v1.json` (le manifest lui-même,
   servi en URL statique publique). `gh release create build-pack-v1 <jar> <signed.json>`.
   Vérifier sha256 + HTTP 200 sur l'URL du jar reborn-hud avant de continuer.
3. **Rebuild + publish du launcher** avec, en plus des 9 vars `_BUILD` habituelles
   (cf `launcher-publish-on-this-pc`), les nouvelles :
   - `REBORN_BUILD_MANIFEST_URL_BUILD=https://github.com/OMZBRK/reborn-roleplay/releases/download/build-pack-v1/manifest-build-signed-v1.json`
   - `REBORN_BUILD_MC_VERSION_BUILD=26.3`
   - `REBORN_SERVER_DEV_HOST_BUILD=<host serveur build>` / `REBORN_SERVER_DEV_PORT_BUILD=<port>`
     (le profil "build" réutilise le slot serveur « dev »).
   Sans `REBORN_BUILD_MANIFEST_URL`, `servers::build_profile()` renvoie None → seul RP
   s'affiche (inerte, sûr).
4. **Serveur build** : déposer Axiom (+ FAWE) sur le serveur 26.3, restart. Le play-token
   Guardian n'est PAS requis sur le serveur build (pas de plugin RP) ; si Guardian y est
   quand même, le mod-integrity attestera comme sur RP.

## Notes / risques

- **Sodium alpha** : seul build 26.3. Acceptable pour un petit groupe staff qui construit.
- La signature couvre l'URL de reborn-hud → si on change le tag de release, re-signer.
- Le pack build est **indépendant** de la migration RP 26.3 (différée) : il ne dépend ni
  de Bendable Cuboids ni des mods RP. Voir `MIGRATION_26.3.md §5`.
