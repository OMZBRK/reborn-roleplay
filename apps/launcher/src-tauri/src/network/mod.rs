//! Tauri command minimaliste pour le polling de connectivite cote frontend.
//!
//! Le front declenche un GET /v1/health toutes les 12s pour piloter le
//! OfflineBanner. On utilise un client reqwest local avec timeout 3s pour
//! que le polling reste reactif (vs les 15s du client API principal qui
//! sont calibres pour les batchs).

use std::time::Duration;
use tauri::State;

use crate::auth::AuthState;

/// Retourne `true` si l'API repond 2xx au GET `/health` en moins de 3s,
/// `false` sinon (timeout, erreur reseau, status non-2xx).
///
/// Volontairement infaillible cote IPC : on encapsule toutes les erreurs en
/// `Ok(false)` parce que le frontend n'a rien a faire d'un message d'erreur
/// granulaire pour ce check — il bascule simplement en "offline".
#[tauri::command]
pub async fn network_ping_health(state: State<'_, AuthState>) -> Result<bool, String> {
    let url = format!("{}/health", state.inner().api.base_url);

    let client = match reqwest::Client::builder()
        .timeout(Duration::from_secs(3))
        .build()
    {
        Ok(c) => c,
        Err(_) => return Ok(false),
    };

    match client.get(&url).send().await {
        Ok(resp) => Ok(resp.status().is_success()),
        Err(_) => Ok(false),
    }
}
