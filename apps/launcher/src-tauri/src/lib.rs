// Tauri 2 — entry point du backend Rust du launcher Reborn.
// Les modules ci-dessous sont des squelettes : leur contenu est livre
// au fil des semaines 2..6 du plan (cf §11 et §12.1).

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
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![ping])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
