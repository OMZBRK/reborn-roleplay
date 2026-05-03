// Tauri 2 — entry point du backend Rust du launcher Reborn.

mod api;
mod auth;
mod hardware;
mod integrity;
mod launcher;
mod manifest;
mod social;
mod storage;

#[tauri::command]
fn ping() -> &'static str {
    "pong"
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
                        .map(|s| format!("{}…{}", &s.chars().take(8).collect::<String>(), s.chars().count()))
                        .unwrap_or_else(|_| "<not set>".to_string());
                    let ms_preview = std::env::var("MS_CLIENT_ID")
                        .map(|s| s.chars().take(8).collect::<String>())
                        .unwrap_or_else(|_| "<not set>".to_string());
                    eprintln!("[dev] MS_CLIENT_ID={ms_preview} MANIFEST_PUBLIC_KEY_HEX={pubkey_preview}");
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

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .manage(auth::AuthState::new())
        .manage(launcher::GameState::new())
        .invoke_handler(tauri::generate_handler![
            ping,
            auth::auth_login_microsoft,
            auth::auth_resume_session,
            auth::auth_logout,
            auth::auth_dev_login,
            launcher::launcher_check_update,
            launcher::launcher_apply_update,
            launcher::game::launcher_launch_game,
            launcher::game::launcher_stop_game,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
