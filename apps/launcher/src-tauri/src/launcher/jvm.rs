//! Construction de la commande JVM.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.2 ; profil JVM « client »
//! détaillé et justifié dans docs/technique/LAUNCHER_JVM.md §1.
//!
//! Le but est de produire un argv complet (sans le binaire java) qu'on
//! pourra spawn via `tokio::process::Command`. La construction (`build_args`)
//! est PURE pour pouvoir l'unit-tester ; `build_command` y ajoute seulement
//! la RAM totale du système (lue une fois) pour plafonner `-Xmx`.

use crate::hardware;

/// Classe principale vanilla, remplacée par celle de Fabric au lancement.
pub const VANILLA_MAIN_CLASS: &str = "net.minecraft.client.main.Main";

/// Plancher de `-Xms` (Mo) : en dessous, le tas grossit par à-coups dès le
/// chargement des mods.
const MIN_INITIAL_HEAP_MB: u32 = 1024;

#[derive(Debug, Clone)]
pub struct LaunchConfig {
    pub minecraft_version: String,
    pub launcher_version: String,
    pub minecraft_username: String,
    pub minecraft_uuid: String,
    pub mc_access_token: String,
    /// Chemin absolu vers le natives directory (libraries natives extraites).
    pub natives_dir: String,
    /// Liste de chemins absolus vers les jars (séparés par `;` Windows ou `:` Unix dans la cmd).
    pub library_jars: Vec<String>,
    /// Chemin absolu vers le client jar Minecraft.
    pub client_jar: String,
    /// Game directory isolé (cf §4.2).
    pub game_dir: String,
    /// Assets directory.
    pub assets_dir: String,
    /// Asset index version (ex: "17", "16", …).
    pub asset_index: String,
    /// RAM demandée pour la JVM en Mo (onglet Settings → Jeu, défaut 4096).
    /// Bornée au lancement à [2048, RAM système − 2 Go] (cf `hardware::clamp_ram_mb`).
    pub ram_mb: u32,
    /// Résolution X / Y de la fenêtre de jeu.
    pub width: u32,
    pub height: u32,
    /// Adresse du serveur Reborn passée au mod HUD via sysprops
    /// (`-Dreborn.server.host` / `.port`). Le mod l'utilise pour le bouton
    /// « Espace » du main menu — on ne connecte PLUS automatiquement, le
    /// client boote sur le menu principal Reborn. None = menu sans cible
    /// (le mod retombe sur son host par défaut).
    pub auto_connect: Option<ServerAddress>,
    /// Serveur de DEV (build/dev en période de développement) passé au mod via
    /// `-Dreborn.server.dev.host` / `.port`. Le mod n'expose le sélecteur
    /// Build/Dev qu'aux comptes staff (cf `is_staff`). None = pas de serveur
    /// dev configuré → le mod n'affiche que le serveur principal.
    pub dev_server: Option<ServerAddress>,
    /// Vrai si le compte connecté est staff (role HELPER ou supérieur). Passé
    /// au mod via `-Dreborn.staff=true` pour déverrouiller les features dev
    /// (sélecteur de serveur, 2e instance). Les joueurs normaux ne voient rien.
    pub is_staff: bool,
    /// Chemin absolu vers le fichier contenant le play-token (lu par le mod
    /// Reborn Integrity au boot pour s'attester auprès du plugin Guardian).
    /// None = pas d'attestation (dev local sans plugin).
    pub play_token_path: Option<String>,
    /// URL de base de l'API Reborn (ex: `https://api.reborn-rp.com/v1`) passée
    /// au mod HUD via `-Dreborn.apiUrl`. Le mod l'utilise pour alimenter le
    /// menu ÉCHAP (compteur Discord, patch notes, streams) via l'endpoint
    /// public `/menu/panel`. None = le mod retombe sur son URL prod par défaut.
    pub api_url: Option<String>,
}

#[derive(Debug, Clone)]
pub struct ServerAddress {
    pub host: String,
    pub port: u16,
}

/// Génère les arguments JVM à passer à `java.exe`. Pas de validation de
/// chemins ici — l'appelant est supposé avoir vérifié l'existence des fichiers.
pub fn build_command(cfg: &LaunchConfig) -> Vec<String> {
    build_args(cfg, hardware::total_ram_mb())
}

/// Cœur pur de [`build_command`] : la RAM totale du système est un paramètre.
fn build_args(cfg: &LaunchConfig, total_ram_mb: Option<u64>) -> Vec<String> {
    let xmx_mb = hardware::clamp_ram_mb(cfg.ram_mb, total_ram_mb);
    if xmx_mb != cfg.ram_mb {
        tracing::warn!(
            "RAM demandée {} Mo ramenée à {xmx_mb} Mo (RAM système {} Mo, 2 Go réservés au système, plancher 2 Go)",
            cfg.ram_mb,
            total_ram_mb.map_or_else(|| "inconnue".to_string(), |t| t.to_string())
        );
    }

    let mut args = Vec::with_capacity(64);
    args.extend(heap_and_gc_flags(xmx_mb));

    // Sorties standard en UTF-8 : elles sont redirigées vers le launcher (pas
    // de console), et la JVM Windows écrirait sinon en Cp1252 — les accents
    // (chemins, pseudos, traductions) casseraient la lecture côté Rust.
    args.push("-Dstdout.encoding=UTF-8".into());
    args.push("-Dstderr.encoding=UTF-8".into());

    // macOS : GLFW ne peut tourner que sur le thread principal du process.
    // Sans ce flag, glfwInit() lève "GLFW may only be used on the main thread
    // and that thread must be the first thread in the process" et le client
    // crashe à l'init du rendu (RenderSystem::initBackendSystem), après avoir
    // pourtant chargé auth, manifest et mods. Aucun effet sur Windows/Linux,
    // où le flag n'existe pas et ferait échouer le démarrage de la JVM.
    if cfg!(target_os = "macos") {
        args.push("-XstartOnFirstThread".into());
    }

    args.push(format!("-Djava.library.path={}", cfg.natives_dir));
    args.push("-Dminecraft.launcher.brand=reborn-launcher".into());
    args.push(format!(
        "-Dminecraft.launcher.version={}",
        cfg.launcher_version
    ));
    if let Some(path) = cfg.play_token_path.as_deref() {
        // Le mod Reborn Integrity lit ce path et envoie son contenu via
        // custom payload `reborn:auth` au serveur (cf plugin Guardian).
        // Sysprop pointant vers un fichier plutôt que le token brut : évite
        // que la valeur fuite dans `ps` / le Gestionnaire des tâches.
        args.push(format!("-Dreborn.playTokenPath={path}"));
    }
    if let Some(api) = cfg.api_url.as_deref() {
        // Le menu ÉCHAP du mod HUD fetch /menu/panel sur cette base.
        args.push(format!("-Dreborn.apiUrl={api}"));
    }
    if let Some(server) = cfg.auto_connect.as_ref() {
        // Adresse du serveur transmise au mod HUD. Le main menu Reborn lit
        // ces sysprops et connecte le joueur quand il appuie sur Espace — on
        // ne passe volontairement PAS `--quickPlayMultiplayer` pour laisser
        // le menu principal s'afficher au lieu de se connecter directement.
        args.push(format!("-Dreborn.server.host={}", server.host));
        args.push(format!("-Dreborn.server.port={}", server.port));
    }
    if let Some(dev) = cfg.dev_server.as_ref() {
        // Serveur dev — le mod n'affiche le toggle Build/Dev qu'aux staffs.
        args.push(format!("-Dreborn.server.dev.host={}", dev.host));
        args.push(format!("-Dreborn.server.dev.port={}", dev.port));
    }
    if cfg.is_staff {
        // Déverrouille les features dev côté mod (sélecteur de serveur, etc.).
        args.push("-Dreborn.staff=true".into());
    }

    // Classpath = libraries + client jar
    let cp_separator = if cfg!(target_os = "windows") {
        ";"
    } else {
        ":"
    };
    let mut cp = cfg.library_jars.clone();
    cp.push(cfg.client_jar.clone());
    args.push("-cp".into());
    args.push(cp.join(cp_separator));

    // Main class (remplacée par celle de Fabric dans game.rs).
    args.push(VANILLA_MAIN_CLASS.into());

    // Game args
    let game_args = [
        ("--username", cfg.minecraft_username.clone()),
        ("--version", format!("Reborn {}", cfg.minecraft_version)),
        ("--gameDir", cfg.game_dir.clone()),
        ("--assetsDir", cfg.assets_dir.clone()),
        ("--assetIndex", cfg.asset_index.clone()),
        ("--uuid", cfg.minecraft_uuid.clone()),
        ("--accessToken", cfg.mc_access_token.clone()),
        ("--userType", "msa".into()),
        ("--versionType", "release".into()),
        ("--width", cfg.width.to_string()),
        ("--height", cfg.height.to_string()),
    ];
    for (flag, value) in game_args {
        args.push(flag.into());
        args.push(value);
    }

    // NB : plus de `--quickPlayMultiplayer` ici. On veut afficher le main
    // menu Reborn au lancement plutôt que de connecter directement le
    // joueur. L'adresse serveur est transmise au mod via sysprops (cf.
    // `-Dreborn.server.host` / `.port` plus haut) ; c'est le bouton
    // « Espace » du menu qui déclenche la connexion.

    args
}

/// Profil mémoire + GC « client » (justification flag par flag dans
/// docs/technique/LAUNCHER_JVM.md §1). Minecraft client = beaucoup d'objets
/// très courts (rendu, maillage des chunks) et une exigence de pauses brèves
/// (une pause de 50 ms ≈ 3 images à 60 FPS) ; rien à voir avec un serveur.
fn heap_and_gc_flags(xmx_mb: u32) -> Vec<String> {
    // Xms = Xmx/2 : évite les redimensionnements du tas au chargement sans
    // réserver toute la RAM d'entrée (cible : machines 4-8 Go).
    let xms_mb = (xmx_mb / 2).max(MIN_INITIAL_HEAP_MB).min(xmx_mb);
    vec![
        format!("-Xmx{xmx_mb}M"),
        format!("-Xms{xms_mb}M"),
        "-XX:+UseG1GC".into(),
        "-XX:MaxGCPauseMillis=50".into(),
        // G1NewSizePercent est « expérimental » : le déverrouillage doit précéder.
        "-XX:+UnlockExperimentalVMOptions".into(),
        "-XX:G1NewSizePercent=20".into(),
        "-XX:G1ReservePercent=20".into(),
        "-XX:G1HeapRegionSize=8M".into(),
        "-XX:+ParallelRefProcEnabled".into(),
        "-XX:+DisableExplicitGC".into(),
        "-XX:+UseStringDeduplication".into(),
        // Produit (plus expérimental) depuis Java 25 (JEP 519).
        "-XX:+UseCompactObjectHeaders".into(),
        "-XX:+PerfDisableSharedMem".into(),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Machine de test confortable (32 Go) : pas de plafonnement parasite.
    const BIG_MACHINE: Option<u64> = Some(32_768);

    fn sample_cfg() -> LaunchConfig {
        LaunchConfig {
            minecraft_version: "26.2".into(),
            launcher_version: "0.1.0".into(),
            minecraft_username: "OMZ".into(),
            minecraft_uuid: "069a79f4-44e9-4726-a5be-fca90e38aaf5".into(),
            mc_access_token: "TOKEN".into(),
            natives_dir: "C:/game/natives".into(),
            library_jars: vec!["C:/lib/a.jar".into(), "C:/lib/b.jar".into()],
            client_jar: "C:/game/client.jar".into(),
            game_dir: "C:/game".into(),
            assets_dir: "C:/game/assets".into(),
            asset_index: "17".into(),
            ram_mb: 4096,
            width: 1280,
            height: 720,
            auto_connect: Some(ServerAddress {
                host: "play.reborn-rp.fr".into(),
                port: 25565,
            }),
            dev_server: None,
            is_staff: false,
            play_token_path: None,
            api_url: None,
        }
    }

    fn argv(cfg: &LaunchConfig) -> Vec<String> {
        build_args(cfg, BIG_MACHINE)
    }

    fn has(argv: &[String], flag: &str) -> bool {
        argv.iter().any(|a| a == flag)
    }

    #[test]
    fn play_token_path_emitted_as_sysprop() {
        let mut cfg = sample_cfg();
        cfg.play_token_path = Some("C:/game/.reborn-play-token".into());
        let argv = argv(&cfg);
        assert!(
            has(&argv, "-Dreborn.playTokenPath=C:/game/.reborn-play-token"),
            "argv must carry the play-token sysprop"
        );
    }

    #[test]
    fn no_play_token_path_omits_sysprop() {
        let argv = argv(&sample_cfg());
        assert!(!argv
            .iter()
            .any(|a| a.starts_with("-Dreborn.playTokenPath=")));
    }

    #[test]
    fn includes_main_class_after_classpath() {
        let argv = argv(&sample_cfg());
        let cp_idx = argv.iter().position(|a| a == "-cp").unwrap();
        let main_idx = argv.iter().position(|a| a == VANILLA_MAIN_CLASS).unwrap();
        assert_eq!(main_idx, cp_idx + 2);
    }

    #[test]
    fn server_hint_emitted_as_sysprops() {
        let argv = argv(&sample_cfg());
        assert!(
            has(&argv, "-Dreborn.server.host=play.reborn-rp.fr"),
            "argv doit porter le sysprop host"
        );
        assert!(
            has(&argv, "-Dreborn.server.port=25565"),
            "argv doit porter le sysprop port"
        );
    }

    #[test]
    fn no_quick_play_multiplayer_ever() {
        // On ne connecte jamais automatiquement — le menu Reborn s'affiche.
        assert!(!has(&argv(&sample_cfg()), "--quickPlayMultiplayer"));
    }

    #[test]
    fn dev_server_and_staff_flag_emitted_when_set() {
        let mut cfg = sample_cfg();
        cfg.dev_server = Some(ServerAddress {
            host: "203.0.113.10".into(),
            port: 26547,
        });
        cfg.is_staff = true;
        let argv = argv(&cfg);
        assert!(has(&argv, "-Dreborn.server.dev.host=203.0.113.10"));
        assert!(has(&argv, "-Dreborn.server.dev.port=26547"));
        assert!(has(&argv, "-Dreborn.staff=true"));
    }

    #[test]
    fn no_dev_server_no_staff_flag_by_default() {
        let argv = argv(&sample_cfg());
        assert!(!argv.iter().any(|a| a.starts_with("-Dreborn.server.dev.")));
        assert!(!has(&argv, "-Dreborn.staff=true"));
    }

    #[test]
    fn no_server_hint_omits_sysprops() {
        let mut cfg = sample_cfg();
        cfg.auto_connect = None;
        let argv = argv(&cfg);
        assert!(!argv.iter().any(|a| a.starts_with("-Dreborn.server.host=")));
        assert!(!argv.iter().any(|a| a.starts_with("-Dreborn.server.port=")));
    }

    #[test]
    fn ram_is_propagated_in_xmx_and_xms_is_half() {
        let mut cfg = sample_cfg();
        cfg.ram_mb = 8192;
        let argv = argv(&cfg);
        assert!(has(&argv, "-Xmx8192M"));
        assert!(has(&argv, "-Xms4096M"));
    }

    #[test]
    fn xms_never_below_floor() {
        let mut cfg = sample_cfg();
        cfg.ram_mb = 2048;
        let argv = argv(&cfg);
        assert!(has(&argv, "-Xmx2048M"));
        assert!(has(&argv, "-Xms1024M"));
    }

    #[test]
    fn ram_capped_to_system_minus_reserve() {
        let mut cfg = sample_cfg();
        cfg.ram_mb = 8192;
        let argv = build_args(&cfg, Some(8_192));
        assert!(has(&argv, "-Xmx6144M"), "{argv:?}");
        assert!(has(&argv, "-Xms3072M"), "{argv:?}");
    }

    #[test]
    fn ram_floor_applies_to_tiny_choices() {
        let mut cfg = sample_cfg();
        cfg.ram_mb = 1024;
        assert!(has(&argv(&cfg), "-Xmx2048M"));
    }

    #[test]
    fn client_gc_profile() {
        let argv = argv(&sample_cfg());
        for flag in [
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=50",
            "-XX:+ParallelRefProcEnabled",
            "-XX:+DisableExplicitGC",
            "-XX:+UseStringDeduplication",
            "-XX:+UseCompactObjectHeaders",
        ] {
            assert!(has(&argv, flag), "flag manquant : {flag}");
        }
        // Flags pensés serveur (profil Aikar) : retirés du profil client.
        for banned in [
            "-XX:+AlwaysPreTouch",
            "-XX:InitiatingHeapOccupancyPercent=15",
            "-XX:MaxTenuringThreshold=1",
            "-XX:SurvivorRatio=32",
        ] {
            assert!(!has(&argv, banned), "flag serveur présent : {banned}");
        }
    }

    #[test]
    fn unlock_precedes_experimental_flags() {
        let argv = argv(&sample_cfg());
        let unlock = argv
            .iter()
            .position(|a| a == "-XX:+UnlockExperimentalVMOptions")
            .unwrap();
        let experimental = argv
            .iter()
            .position(|a| a.starts_with("-XX:G1NewSizePercent="))
            .unwrap();
        assert!(unlock < experimental);
    }

    #[test]
    fn stdio_forced_to_utf8() {
        let argv = argv(&sample_cfg());
        assert!(has(&argv, "-Dstdout.encoding=UTF-8"));
        assert!(has(&argv, "-Dstderr.encoding=UTF-8"));
    }

    #[test]
    fn jvm_options_precede_main_class() {
        // Tout ce qui commence par -X / -D doit être avant la main class,
        // sinon java le passe au jeu au lieu de la JVM.
        let argv = argv(&sample_cfg());
        let main_idx = argv.iter().position(|a| a == VANILLA_MAIN_CLASS).unwrap();
        assert!(argv[main_idx..]
            .iter()
            .all(|a| !a.starts_with("-X") && !a.starts_with("-D")));
    }
}
