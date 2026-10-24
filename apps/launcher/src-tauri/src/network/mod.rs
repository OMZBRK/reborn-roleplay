//! Tauri command minimaliste pour le polling de connectivité côté frontend.
//!
//! Le front déclenche un GET /v1/health toutes les 12 s pour piloter le
//! OfflineBanner. On utilise un client reqwest dédié avec timeout 3 s pour
//! que le polling reste réactif (vs les 15 s du client API principal qui
//! sont calibrés pour les batchs). Le client est créé une seule fois : le
//! recréer à chaque ping refaisait la config TLS et jetait le pool de
//! connexions toutes les 12 s.

use serde::Serialize;
use std::sync::OnceLock;
use std::time::Duration;
use tauri::State;

use crate::auth::AuthState;

fn health_client() -> &'static reqwest::Client {
    static CLIENT: OnceLock<reqwest::Client> = OnceLock::new();
    CLIENT.get_or_init(|| {
        reqwest::Client::builder()
            .user_agent(format!("RebornLauncher/{}", env!("CARGO_PKG_VERSION")))
            .timeout(Duration::from_secs(3))
            .build()
            .unwrap_or_default()
    })
}

/// Résultat d'un ping `/health` : `cause` (phrase courte en français, sans
/// point final) n'est renseignée que si `ok` est faux. L'OfflineBanner
/// l'affiche sous son titre.
#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct HealthPing {
    pub ok: bool,
    pub cause: Option<String>,
}

/// Cause lisible d'un statut HTTP non-2xx renvoyé par `/health`.
fn status_cause(code: u16) -> String {
    match code {
        502..=504 => format!("le serveur répond HTTP {code} : maintenance ou redémarrage en cours"),
        500..=599 => format!("le serveur répond HTTP {code} : panne côté serveur"),
        404 => "le serveur répond HTTP 404 : adresse de l'API incorrecte".to_string(),
        _ => format!("le serveur répond HTTP {code}"),
    }
}

/// `{ ok: true }` si l'API répond 2xx au GET `/health` en moins de 3 s,
/// sinon `{ ok: false, cause }` (délai dépassé, DNS, connexion refusée,
/// HTTP 502…).
///
/// Volontairement infaillible côté IPC (jamais `Err`) : le frontend bascule
/// simplement en "offline" et affiche la cause.
#[tauri::command]
pub async fn network_ping_health(state: State<'_, AuthState>) -> Result<HealthPing, String> {
    let url = format!("{}/health", state.inner().api.base_url);
    let cause = match health_client().get(&url).send().await {
        Ok(resp) if resp.status().is_success() => {
            return Ok(HealthPing {
                ok: true,
                cause: None,
            })
        }
        Ok(resp) => status_cause(resp.status().as_u16()),
        Err(e) => crate::api::network_cause(&e),
    };
    tracing::debug!("ping /health : {cause}");
    Ok(HealthPing {
        ok: false,
        cause: Some(cause),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn health_ping_serializes_ok_and_cause() {
        let v = serde_json::to_value(HealthPing {
            ok: false,
            cause: Some(status_cause(502)),
        })
        .unwrap();
        assert_eq!(v["ok"], false);
        assert!(v["cause"].as_str().unwrap().contains("HTTP 502"));
        let v = serde_json::to_value(HealthPing {
            ok: true,
            cause: None,
        })
        .unwrap();
        assert!(v["cause"].is_null());
    }
}
