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
        .invoke_handler(tauri::generate_handler![
            ping,
            auth::auth_login_microsoft,
            auth::auth_resume_session,
            auth::auth_logout,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
