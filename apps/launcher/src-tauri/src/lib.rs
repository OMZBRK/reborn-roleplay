// Tauri 2 — entry point du backend Rust du launcher Reborn.

mod api;
mod auth;
mod content;
mod discord_rpc;
mod hardware;
mod integrity;
mod launcher;
mod manifest;
mod network;
mod single_instance;
mod storage;
mod tray;

#[tauri::command]
fn ping() -> &'static str {
    "pong"
}

/// Taille de référence de l'UI, en pixels CSS (fenêtre fixe, non redimensionnable).
const BASE_WIDTH: f64 = 1280.0;
const BASE_HEIGHT: f64 = 800.0;
/// En dessous, l'UI devient illisible : on ne rétrécit pas davantage.
const MIN_ZOOM: f64 = 0.5;
/// Attente maximale de la fermeture du statut Discord à la sortie.
const EXIT_RPC_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(1);

/// Zoom à appliquer pour que la fenêtre 1280×800 tienne dans une zone de
/// travail de `avail_w`×`avail_h` pixels logiques. `None` = elle tient déjà.
fn fit_zoom(avail_w: f64, avail_h: f64) -> Option<f64> {
    if avail_w >= BASE_WIDTH && avail_h >= BASE_HEIGHT {
        return None;
    }
    let zoom = (avail_w / BASE_WIDTH)
        .min(avail_h / BASE_HEIGHT)
        .clamp(MIN_ZOOM, 1.0);
    // Arrondi au centième inférieur : jamais un pixel de trop.
    Some((zoom * 100.0).floor() / 100.0)
}

/// Petits écrans / mise à l'échelle Windows élevée (ex. 1920×1080 à 150 % =
/// 1280×693 logiques) : on garde la mise en page 1280×800 mais on zoome la
/// webview pour que la fenêtre tienne entière dans la zone de travail (hors
/// barre des tâches), puis on la recentre. Sur un écran assez grand, rien
/// ne change.
fn fit_main_window<R: tauri::Runtime>(win: &tauri::WebviewWindow<R>) {
    let Some(monitor) = win
        .current_monitor()
        .ok()
        .flatten()
        .or_else(|| win.primary_monitor().ok().flatten())
    else {
        return;
    };
    let scale = monitor.scale_factor();
    let area = monitor.work_area();
    let avail_w = f64::from(area.size.width) / scale;
    let avail_h = f64::from(area.size.height) / scale;
    let Some(zoom) = fit_zoom(avail_w, avail_h) else {
        return;
    };
    tracing::info!(
        "écran {avail_w:.0}×{avail_h:.0} (logique, échelle {scale}) plus petit que {BASE_WIDTH}×{BASE_HEIGHT} : zoom {zoom}"
    );
    if let Err(e) = win.set_zoom(zoom) {
        tracing::warn!("zoom de la webview impossible : {e}");
        return;
    }
    let width = (BASE_WIDTH * zoom).floor();
    let height = (BASE_HEIGHT * zoom).floor();
    let _ = win.set_size(tauri::LogicalSize::new(width, height));
    // Centrage dans la zone de travail (pas sur l'écran entier : la barre des
    // tâches mangerait le bas de la fenêtre).
    let x =
        f64::from(area.position.x) + (f64::from(area.size.width) - width * scale).max(0.0) / 2.0;
    let y =
        f64::from(area.position.y) + (f64::from(area.size.height) - height * scale).max(0.0) / 2.0;
    let _ = win.set_position(tauri::PhysicalPosition::new(
        x.round() as i32,
        y.round() as i32,
    ));
}

/// Sortie du launcher (« Quitter », « Redémarrer le launcher », fermeture
/// quand la zone de notification est désactivée ou que son icône manque,
/// redémarrage après mise à jour) : ménage de ce qui doit
/// l'être. Une partie en cours n'est pas tuée (cf tray.rs).
fn on_exit(app: &tauri::AppHandle) {
    use tauri::Manager;
    // D'abord le port d'instance unique : un redémarrage relance le
    // launcher juste après ce callback, il doit pouvoir le reprendre.
    if let Some(instance) = app.try_state::<single_instance::Instance>() {
        instance.shutdown();
    }
    if let Some(game) = app.try_state::<launcher::GameState>() {
        if let Some(pid) = game.detach_for_exit() {
            tracing::info!("launcher fermé pendant une partie : le jeu (pid {pid}) reste ouvert");
        }
    }
    if let Some(rpc) = app.try_state::<discord_rpc::DiscordRpcState>() {
        rpc.stop_blocking(EXIT_RPC_TIMEOUT);
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // Dev only : charge .env a la racine du monorepo pour que MS_CLIENT_ID
    // & co. soient visibles depuis le process Tauri lance par `tauri dev`.
    // CARGO_MANIFEST_DIR pointe sur src-tauri/, le .env vit 3 niveaux plus haut.
    #[cfg(debug_assertions)]
    {
        let manifest_dir = env!("CARGO_MANIFEST_DIR");
        let workspace_env = std::path::Path::new(manifest_dir)
            .ancestors()
            .nth(3)
            .map(|p| p.join(".env"));
        if let Some(path) = workspace_env {
            match dotenvy::from_path_override(&path) {
                Ok(_) => {
                    eprintln!("[dev] .env loaded from {}", path.display());
                    let pubkey_preview = std::env::var("MANIFEST_PUBLIC_KEY_HEX")
                        .map(|s| {
                            format!(
                                "{}…{}",
                                &s.chars().take(8).collect::<String>(),
                                s.chars().count()
                            )
                        })
                        .unwrap_or_else(|_| "<not set>".to_string());
                    let ms_preview = std::env::var("MS_CLIENT_ID")
                        .map(|s| s.chars().take(8).collect::<String>())
                        .unwrap_or_else(|_| "<not set>".to_string());
                    let server_preview = std::env::var("REBORN_SERVER_HOST")
                        .ok()
                        .filter(|s| !s.is_empty())
                        .map(|host| {
                            let port = std::env::var("REBORN_SERVER_PORT")
                                .unwrap_or_else(|_| "25565".into());
                            format!("{host}:{port}")
                        })
                        .unwrap_or_else(|| "<not set>".to_string());
                    eprintln!("[dev] MS_CLIENT_ID={ms_preview} MANIFEST_PUBLIC_KEY_HEX={pubkey_preview} REBORN_SERVER={server_preview}");
                }
                Err(e) => eprintln!("[dev] .env not loaded ({}) : {e}", path.display()),
            }
        }
    }

    // Logs structures — RUST_LOG=info,launcher_lib=debug pour le dev.
    let _ = tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .try_init();

    // Instance unique : si un Reborn Launcher tourne déjà (souvent caché dans
    // la zone de notification), on ramène sa fenêtre et on s'arrête là, sans
    // créer la nôtre.
    let instance = match single_instance::acquire() {
        single_instance::Startup::AlreadyRunning => {
            tracing::info!(
                "Reborn Launcher déjà ouvert : fenêtre existante ramenée, ce lancement s'arrête"
            );
            return;
        }
        single_instance::Startup::Primary(instance) => Some(instance),
        single_instance::Startup::Unprotected => None,
    };

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_store::Builder::default().build())
        .plugin(tauri_plugin_notification::init())
        .manage(auth::AuthState::new())
        .manage(launcher::GameState::new())
        .manage(discord_rpc::DiscordRpcState::new())
        .manage(tray::TrayState::new())
        .setup(move |app| {
            use tauri::Manager;
            // « Fermer = cacher » : réglage lu une fois ici, tenu à jour par
            // prefs_set (le gestionnaire de fermeture ne lit pas le disque).
            let user_prefs = tauri::async_runtime::block_on(storage::prefs::load())
                .unwrap_or_else(|e| {
                    tracing::warn!("préférences illisibles au démarrage ({e}) : valeurs par défaut");
                    storage::prefs::Preferences::default()
                });
            app.state::<tray::TrayState>().apply_prefs(&user_prefs);
            if let Err(e) = tray::create(app.handle()) {
                tracing::error!(
                    "icône de la zone de notification impossible ({e}) : fermer la fenêtre quittera le launcher"
                );
            }
            if let Some(instance) = instance {
                instance.attach(app.handle().clone());
                app.manage(instance);
            }
            // La fenêtre est créée cachée (tauri.conf.json) : on l'ajuste à
            // l'écran AVANT de la montrer, pour éviter un saut de taille.
            if let Some(win) = app.get_webview_window("main") {
                fit_main_window(&win);
                if let Err(e) = win.show() {
                    tracing::error!("affichage de la fenêtre principale impossible : {e}");
                }
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                tray::on_close_requested(window, api);
            }
        })
        .invoke_handler(tauri::generate_handler![
            ping,
            auth::auth_login_microsoft,
            auth::auth_resume_session,
            auth::auth_refresh_access,
            auth::auth_logout,
            auth::auth_dev_login,
            auth::auth_login_with_saved_account,
            auth::auth_forget_account,
            launcher::launcher_check_update,
            launcher::launcher_apply_update,
            launcher::launcher_get_mod_prefs,
            launcher::launcher_set_mod_pref,
            launcher::launcher_list_optional_mods,
            launcher::launcher_game_info,
            launcher::launcher_open_install_dir,
            launcher::launcher_reinstall_all,
            launcher::launcher_clean_obsolete,
            launcher::launcher_repair,
            launcher::game::launcher_launch_game,
            launcher::game::launcher_prepare_game,
            launcher::game::launcher_launch_second_instance,
            launcher::game::launcher_stop_game,
            launcher::game::launcher_mods_list,
            launcher::game::launcher_mods_purge,
            launcher::game::launcher_game_status,
            launcher::logs::launcher_logs_snapshot,
            launcher::logs::launcher_logs_export,
            content::update_display_name,
            content::patchnotes_list,
            content::patchnotes_detail,
            content::rules_current,
            content::lore_current,
            content::whitelist_me,
            content::whitelist_withdraw,
            content::whitelist_reclaim,
            content::whitelist_messages_list,
            content::whitelist_messages_post,
            content::candidature::candidature_options,
            content::candidature::candidature_me,
            content::candidature::candidature_envoyer,
            content::candidature::candidature_renvoyer,
            content::candidature::candidature_brouillon_lire,
            content::candidature::candidature_brouillon_ecrire,
            content::candidature::candidature_brouillon_effacer,
            content::candidature::candidature_voix_deposer,
            content::candidature::candidature_voix_ecouter,
            content::candidature::douane_etat,
            content::candidature::douane_reserver,
            content::candidature::douane_annuler,
            content::candidature::douane_acces,
            content::tickets_list,
            content::tickets_detail,
            content::tickets_create,
            content::tickets_post_message,
            content::tickets_delete,
            content::tickets_reclaim,
            content::server_status,
            content::communaute::community_discord,
            content::discord_link_start,
            content::discord_unlink,
            content::auth_me,
            content::me_badges,
            content::me_mark_read,
            content::personnages::me_characters,
            content::upload_attachment,
            content::prefs_get,
            content::prefs_set,
            content::screenshots_list,
            content::screenshots_open_folder,
            content::screenshots_toggle_favorite,
            content::screenshots_delete,
            content::screenshots_share,
            content::screenshots_process_pending_shares,
            content::shots_feed,
            content::shots_toggle_like,
            hardware::system_specs_get,
            network::network_ping_health,
        ])
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app, event| {
            if let tauri::RunEvent::Exit = event {
                on_exit(app);
            }
        });
}

#[cfg(test)]
mod tests {
    use super::fit_zoom;

    #[test]
    fn big_screens_keep_native_size() {
        assert_eq!(fit_zoom(1920.0, 1040.0), None);
        assert_eq!(fit_zoom(1280.0, 800.0), None);
    }

    #[test]
    fn small_screens_get_zoomed_to_fit() {
        // 1920×1080 à 150 % : 1280×693 logiques (barre des tâches déduite).
        assert_eq!(fit_zoom(1280.0, 693.0), Some(0.86));
        // 1366×768 à 100 % (portable d'entrée de gamme), barre des tâches de 40 px.
        assert_eq!(fit_zoom(1366.0, 728.0), Some(0.91));
        // Jamais en dessous du plancher de lisibilité.
        assert_eq!(fit_zoom(400.0, 300.0), Some(0.5));
    }
}
