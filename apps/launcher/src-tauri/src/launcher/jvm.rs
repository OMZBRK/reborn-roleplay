//! Construction de la commande JVM.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §8.2.
//!
//! Le but est de produire un argv complet (sans le binaire java) qu'on
//! pourra spawn via `tokio::process::Command`. La fonction est PURE pour
//! pouvoir l'unit-tester sans toucher au filesystem.

use std::path::Path;

#[derive(Debug, Clone)]
pub struct LaunchConfig {
    pub minecraft_version: String,
    pub launcher_version: String,
    pub minecraft_username: String,
    pub minecraft_uuid: String,
    pub mc_access_token: String,
    /// Chemin absolu vers le natives directory (libraries natives extraites).
    pub natives_dir: String,
    /// Liste de chemins absolus vers les jars (separes par `;` Windows ou `:` Unix dans la cmd).
    pub library_jars: Vec<String>,
    /// Chemin absolu vers le client jar Minecraft.
    pub client_jar: String,
    /// Game directory isolé (cf §4.2).
    pub game_dir: String,
    /// Assets directory.
    pub assets_dir: String,
    /// Asset index version (ex: "17", "16", …).
    pub asset_index: String,
    /// RAM allouee a la JVM en Mo (cf onglet Settings → Jeu, defaut 4096).
    pub ram_mb: u32,
    /// Resolution X / Y de la fenetre de jeu.
    pub width: u32,
    pub height: u32,
    /// Adresse du serveur Reborn passée au mod HUD via sysprops
    /// (`-Dreborn.server.host` / `.port`). Le mod l'utilise pour le bouton
    /// « Espace » du main menu — on ne connecte PLUS automatiquement, le
    /// client boote sur le menu principal Reborn. None = menu sans cible
    /// (le mod retombe sur son host par défaut).
    pub auto_connect: Option<ServerAddress>,
    /// Chemin absolu vers le fichier contenant le play-token (lu par le mod
    /// Reborn Integrity au boot pour s'attester aupres du plugin Guardian).
    /// None = pas d'attestation (dev local sans plugin).
    pub play_token_path: Option<String>,
}

#[derive(Debug, Clone)]
pub struct ServerAddress {
    pub host: String,
    pub port: u16,
}

/// Genere les arguments JVM a passer a `java.exe`. Pas de path validation
/// ici — l'appelant est suppose avoir verifie l'existence des fichiers.
pub fn build_command(cfg: &LaunchConfig) -> Vec<String> {
    let mut args = Vec::with_capacity(64);

    // Memoire + GC G1 (profile Aikar deja eprouve sur les serveurs Paper, transposable client).
    args.push(format!("-Xmx{}M", cfg.ram_mb));
    args.push("-Xms512M".into());
    args.push("-XX:+UseG1GC".into());
    args.push("-XX:+ParallelRefProcEnabled".into());
    args.push("-XX:MaxGCPauseMillis=200".into());
    args.push("-XX:+UnlockExperimentalVMOptions".into());
    args.push("-XX:+DisableExplicitGC".into());
    args.push("-XX:+AlwaysPreTouch".into());
    args.push("-XX:G1NewSizePercent=30".into());
    args.push("-XX:G1MaxNewSizePercent=40".into());
    args.push("-XX:G1HeapRegionSize=8M".into());
    args.push("-XX:G1ReservePercent=20".into());
    args.push("-XX:G1HeapWastePercent=5".into());
    args.push("-XX:G1MixedGCCountTarget=4".into());
    args.push("-XX:InitiatingHeapOccupancyPercent=15".into());
    args.push("-XX:G1MixedGCLiveThresholdPercent=90".into());
    args.push("-XX:G1RSetUpdatingPauseTimePercent=5".into());
    args.push("-XX:SurvivorRatio=32".into());
    args.push("-XX:+PerfDisableSharedMem".into());
    args.push("-XX:MaxTenuringThreshold=1".into());

    args.push(format!("-Djava.library.path={}", cfg.natives_dir));
    args.push("-Dminecraft.launcher.brand=reborn-launcher".into());
    args.push(format!("-Dminecraft.launcher.version={}", cfg.launcher_version));
    if let Some(path) = cfg.play_token_path.as_deref() {
        // Le mod Reborn Integrity lit ce path et envoie son contenu via
        // custom payload `reborn:auth` au serveur (cf plugin Guardian).
        // Sysprop pointant vers un fichier plutot que le token brut : evite
        // que la valeur fuite dans `ps` / Task Manager (le path est court
        // et inoffensif).
        args.push(format!("-Dreborn.playTokenPath={path}"));
    }
    if let Some(server) = cfg.auto_connect.as_ref() {
        // Adresse du serveur transmise au mod HUD. Le main menu Reborn
        // lit ces sysprops et connecte le joueur quand il appuie sur
        // Espace — on ne passe volontairement PAS `--quickPlayMultiplayer`
        // pour laisser le menu principal s'afficher au lieu de se
        // connecter directement.
        args.push(format!("-Dreborn.server.host={}", server.host));
        args.push(format!("-Dreborn.server.port={}", server.port));
    }

    // Classpath = libraries + client jar
    let cp_separator = if cfg!(target_os = "windows") { ";" } else { ":" };
    let mut cp = cfg.library_jars.clone();
    cp.push(cfg.client_jar.clone());
    args.push("-cp".into());
    args.push(cp.join(cp_separator));

    // Main class
    args.push("net.minecraft.client.main.Main".into());

    // Game args
    args.push("--username".into());
    args.push(cfg.minecraft_username.clone());

    args.push("--version".into());
    args.push(format!("Reborn {}", cfg.minecraft_version));

    args.push("--gameDir".into());
    args.push(cfg.game_dir.clone());

    args.push("--assetsDir".into());
    args.push(cfg.assets_dir.clone());

    args.push("--assetIndex".into());
    args.push(cfg.asset_index.clone());

    args.push("--uuid".into());
    args.push(cfg.minecraft_uuid.clone());

    args.push("--accessToken".into());
    args.push(cfg.mc_access_token.clone());

    args.push("--userType".into());
    args.push("msa".into());

    args.push("--versionType".into());
    args.push("release".into());

    args.push("--width".into());
    args.push(cfg.width.to_string());

    args.push("--height".into());
    args.push(cfg.height.to_string());

    // NB : plus de `--quickPlayMultiplayer` ici. On veut afficher le main
    // menu Reborn au lancement plutôt que de connecter directement le
    // joueur. L'adresse serveur est transmise au mod via sysprops (cf.
    // `-Dreborn.server.host` / `.port` plus haut) ; c'est le bouton
    // « Espace » du menu qui déclenche la connexion.

    args
}

/// Helper : retourne `true` si le path donne ressemble a `java`/`javaw`.
pub fn looks_like_java_executable(path: &Path) -> bool {
    path.file_stem()
        .and_then(|s| s.to_str())
        .map(|s| s == "java" || s == "javaw")
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_cfg() -> LaunchConfig {
        LaunchConfig {
            minecraft_version: "1.21.4".into(),
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
            play_token_path: None,
        }
    }

    #[test]
    fn play_token_path_emitted_as_sysprop() {
        let mut cfg = sample_cfg();
        cfg.play_token_path = Some("C:/game/.reborn-play-token".into());
        let argv = build_command(&cfg);
        assert!(
            argv.iter()
                .any(|a| a == "-Dreborn.playTokenPath=C:/game/.reborn-play-token"),
            "argv must carry the play-token sysprop"
        );
    }

    #[test]
    fn no_play_token_path_omits_sysprop() {
        let argv = build_command(&sample_cfg());
        assert!(!argv.iter().any(|a| a.starts_with("-Dreborn.playTokenPath=")));
    }

    #[test]
    fn includes_main_class_after_classpath() {
        let argv = build_command(&sample_cfg());
        let cp_idx = argv.iter().position(|a| a == "-cp").unwrap();
        let main_idx = argv
            .iter()
            .position(|a| a == "net.minecraft.client.main.Main")
            .unwrap();
        assert!(main_idx == cp_idx + 2);
    }

    #[test]
    fn server_hint_emitted_as_sysprops() {
        let argv = build_command(&sample_cfg());
        assert!(
            argv.iter()
                .any(|a| a == "-Dreborn.server.host=play.reborn-rp.fr"),
            "argv doit porter le sysprop host"
        );
        assert!(
            argv.iter().any(|a| a == "-Dreborn.server.port=25565"),
            "argv doit porter le sysprop port"
        );
    }

    #[test]
    fn no_quick_play_multiplayer_ever() {
        // On ne connecte jamais automatiquement — le menu Reborn s'affiche.
        let argv = build_command(&sample_cfg());
        assert!(!argv.iter().any(|a| a == "--quickPlayMultiplayer"));
    }

    #[test]
    fn no_server_hint_omits_sysprops() {
        let mut cfg = sample_cfg();
        cfg.auto_connect = None;
        let argv = build_command(&cfg);
        assert!(!argv.iter().any(|a| a.starts_with("-Dreborn.server.host=")));
        assert!(!argv.iter().any(|a| a.starts_with("-Dreborn.server.port=")));
    }

    #[test]
    fn ram_is_propagated_in_xmx() {
        let mut cfg = sample_cfg();
        cfg.ram_mb = 8192;
        let argv = build_command(&cfg);
        assert!(argv.contains(&"-Xmx8192M".to_string()));
    }
}
