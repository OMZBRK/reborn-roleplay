//! Jetons de jeu pendant la partie : écriture atomique des fichiers lus par
//! le mod Reborn Integrity, et rafraîchissement tant que le jeu tourne.
//!
//! Le jeton émis par `POST /play/session` ne vit que 5 min, alors que le mod
//! le lit au moment où le joueur rejoint un serveur. Sans rafraîchissement,
//! un joueur resté plus de 5 min au menu (ou un candidat qui lance le jeu
//! 15 min avant sa séance de douane) présenterait un jeton expiré, et
//! Guardian le refuserait. Une tâche redemande donc chaque jeton (serveur
//! principal, serveur de dev) toutes les [`REFRESH_INTERVAL`] et réécrit son
//! fichier ; elle s'arrête avec la partie (drop de [`TokenRefresher`]).
//!
//! Les échecs sont journalisés sans bruit : un avertissement au premier échec
//! d'une série, une ligne quand le jeton redevient à jour, rien entre les deux
//! (niveau debug).

use std::fmt::Display;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tauri::{AppHandle, Manager, Runtime};
use tokio::task::JoinHandle;

use crate::api::ApiError;
use crate::auth::AuthState;
use crate::storage::secrets::SecretKey;

/// Intervalle entre deux rafraîchissements. Jeton de 5 min : le fichier lu
/// par le mod garde toujours au moins 2 min de validité.
pub(crate) const REFRESH_INTERVAL: Duration = Duration::from_secs(180);
/// Nouvel essai après un échec passager (réseau, erreur 5xx, disque) : assez
/// tôt pour remplacer le jeton avant son expiration.
pub(crate) const RETRY_DELAY: Duration = Duration::from_secs(30);
/// Tentatives de remplacement du fichier : un antivirus ou une lecture du mod
/// peut le tenir ouvert un court instant.
const RENAME_ATTEMPTS: u32 = 3;
const RENAME_BACKOFF: Duration = Duration::from_millis(150);

/// Écrit `token` dans `path` de façon atomique (fichier temporaire voisin,
/// puis renommage) : le mod ne lit jamais un fichier à moitié écrit.
pub(crate) async fn write_token_atomic(path: &Path, token: &str) -> std::io::Result<()> {
    let tmp = tmp_path(path);
    tokio::fs::write(&tmp, token.as_bytes()).await?;
    let mut attempt = 1;
    loop {
        match tokio::fs::rename(&tmp, path).await {
            Ok(()) => return Ok(()),
            Err(_) if attempt < RENAME_ATTEMPTS => {
                attempt += 1;
                tokio::time::sleep(RENAME_BACKOFF).await;
            }
            Err(e) => {
                let _ = tokio::fs::remove_file(&tmp).await;
                return Err(e);
            }
        }
    }
}

/// `<dossier>/<nom>.tmp`, dans le même dossier que `path` (le renommage
/// reste sur le même volume, donc atomique).
fn tmp_path(path: &Path) -> PathBuf {
    let mut name = path
        .file_name()
        .map(|n| n.to_os_string())
        .unwrap_or_default();
    name.push(".tmp");
    path.with_file_name(name)
}

/// Un fichier de jeton à tenir à jour pendant la partie.
pub(crate) struct TokenTarget {
    path: PathBuf,
    /// Serveur demandé à l'API (`None` : serveur par défaut de l'API).
    serveur: Option<String>,
    /// Échecs consécutifs : un seul avertissement par série.
    echecs: u32,
}

impl TokenTarget {
    /// `written` : le jeton a pu être écrit au lancement. Sinon le fichier
    /// manque, et le premier essai a lieu après [`RETRY_DELAY`].
    pub(crate) fn new(path: PathBuf, serveur: Option<String>, written: bool) -> Self {
        Self {
            path,
            serveur,
            echecs: u32::from(!written),
        }
    }

    fn label(&self) -> String {
        match self.serveur.as_deref() {
            Some(id) => format!("du serveur « {id} »"),
            None => "du serveur principal".to_string(),
        }
    }

    fn succes(&mut self) {
        if self.echecs > 0 {
            tracing::info!(
                "jeton de jeu {} de nouveau à jour (après {} échec(s))",
                self.label(),
                self.echecs
            );
        } else {
            tracing::debug!("jeton de jeu {} rafraîchi", self.label());
        }
        self.echecs = 0;
    }

    fn echec(&mut self, cause: &str, next: Duration) {
        self.echecs += 1;
        if self.echecs == 1 {
            tracing::warn!(
                "rafraîchissement du jeton de jeu {} impossible : {cause} (nouvel essai dans {} s)",
                self.label(),
                next.as_secs()
            );
        } else {
            tracing::debug!(
                "rafraîchissement du jeton de jeu {} impossible ({} échecs de suite) : {cause}",
                self.label(),
                self.echecs
            );
        }
    }
}

/// Délai avant le premier tour : tôt si un fichier manque déjà.
fn first_delay(targets: &[TokenTarget]) -> Duration {
    if targets.iter().any(|t| t.echecs > 0) {
        RETRY_DELAY
    } else {
        REFRESH_INTERVAL
    }
}

/// Après une erreur de l'API : réessayer tôt seulement si elle est passagère
/// (réseau, 5xx). Un refus (403 hors fenêtre de douane, 400 serveur inconnu…)
/// ne change pas en 30 s.
fn retry_soon(e: &ApiError) -> bool {
    !matches!(e.status(), Some(status) if status < 500)
}

/// D'où vient le JWT Reborn qui autorise `POST /play/session`.
pub(crate) enum JwtSource {
    /// Session du launcher : JWT relu dans le coffre à chaque tour (le front
    /// le renouvelle toutes les 10 min, même en partie). `mc_uuid` : compte
    /// de la partie. Si le launcher passe sur un autre compte, plus rien
    /// n'est écrit : Guardian refuserait le jeton d'un autre compte.
    Session { mc_uuid: String },
    /// Compte secondaire (2e instance) : JWT obtenu au lancement, renouvelé
    /// par une nouvelle connexion de ce compte quand l'API le refuse (cf
    /// [`renew_jwt`]).
    Alt { mc_uuid: String, jwt: String },
}

/// Tâche de rafraîchissement, arrêtée quand ce handle est lâché (fin de la
/// partie, sortie du launcher).
pub(crate) struct TokenRefresher(JoinHandle<()>);

impl Drop for TokenRefresher {
    fn drop(&mut self) {
        self.0.abort();
    }
}

/// Lance le rafraîchissement des fichiers `targets` pour la partie en cours.
pub(crate) fn spawn_refresher<R: Runtime>(
    app: AppHandle<R>,
    source: JwtSource,
    targets: Vec<TokenTarget>,
) -> TokenRefresher {
    TokenRefresher(tokio::spawn(refresh_loop(app, source, targets)))
}

async fn refresh_loop<R: Runtime>(
    app: AppHandle<R>,
    mut source: JwtSource,
    mut targets: Vec<TokenTarget>,
) {
    if targets.is_empty() {
        return;
    }
    let mut delay = first_delay(&targets);
    let mut hors_compte = false;
    loop {
        tokio::time::sleep(delay).await;
        delay = refresh_round(&app, &mut source, &mut targets, &mut hors_compte).await;
    }
}

/// Un tour : redemande chaque jeton et réécrit son fichier. Renvoie le délai
/// avant le tour suivant.
async fn refresh_round<R: Runtime>(
    app: &AppHandle<R>,
    source: &mut JwtSource,
    targets: &mut [TokenTarget],
    hors_compte: &mut bool,
) -> Duration {
    let auth = app.state::<AuthState>();

    if let JwtSource::Session { mc_uuid } = source {
        let same_account = auth
            .current_user()
            .await
            .is_some_and(|u| super::game::uuid_eq(&u.minecraft_uuid, mc_uuid));
        if !same_account {
            if !*hors_compte {
                tracing::warn!(
                    "le launcher n'est plus connecté au compte de la partie : jetons de jeu plus rafraîchis tant qu'il n'y revient pas"
                );
                *hors_compte = true;
            }
            return REFRESH_INTERVAL;
        }
        if *hors_compte {
            tracing::info!(
                "compte de la partie de retour : rafraîchissement des jetons de jeu repris"
            );
            *hors_compte = false;
        }
    }

    let mut jwt = match current_jwt(&auth, source) {
        Ok(jwt) => jwt,
        Err(cause) => {
            for target in targets.iter_mut() {
                target.echec(&cause, RETRY_DELAY);
            }
            return RETRY_DELAY;
        }
    };

    let mut renewed = false;
    let mut next = REFRESH_INTERVAL;
    for target in targets.iter_mut() {
        let serveur = target.serveur.as_deref();
        let mut result = auth.api.fetch_play_token(&jwt, serveur).await;
        // JWT expiré (session non renouvelée par le front, ou compte alt) :
        // une seule tentative de renouvellement par tour.
        if !renewed && matches!(&result, Err(e) if e.status() == Some(401)) {
            renewed = true;
            match renew_jwt(app, &auth, source).await {
                Ok(fresh) => {
                    jwt = fresh;
                    result = auth.api.fetch_play_token(&jwt, serveur).await;
                }
                Err(cause) => tracing::debug!("renouvellement de la session impossible : {cause}"),
            }
        }
        let outcome = match result {
            Ok(session) => write_token_atomic(&target.path, &session.play_token)
                .await
                .map_err(|e| {
                    (
                        format!("écriture de {} ({e})", target.path.display()),
                        RETRY_DELAY,
                    )
                }),
            Err(e) => {
                let delay = if retry_soon(&e) {
                    RETRY_DELAY
                } else {
                    REFRESH_INTERVAL
                };
                Err((texte(&e), delay))
            }
        };
        match outcome {
            Ok(()) => target.succes(),
            Err((cause, delay)) => {
                target.echec(&cause, delay);
                next = next.min(delay);
            }
        }
    }
    next
}

/// JWT à présenter à l'API pour ce tour.
fn current_jwt(auth: &AuthState, source: &JwtSource) -> Result<String, String> {
    match source {
        JwtSource::Session { .. } => match auth.store.get(SecretKey::RebornAccessToken) {
            Ok(Some(jwt)) => Ok(jwt),
            Ok(None) => Err("aucune session Reborn dans le coffre".to_string()),
            Err(e) => Err(texte(e)),
        },
        JwtSource::Alt { jwt, .. } => Ok(jwt.clone()),
    }
}

/// Renouvelle le JWT refusé (401) et renvoie le nouveau.
///
/// Le renouvellement tourne dans une tâche détachée, dont on attend le
/// résultat : l'arrêt du rafraîchissement (`abort()` au drop de
/// [`TokenRefresher`], en fin de partie ou à la fermeture de la 2e instance)
/// ne l'interrompt pas. Coupé en pleine requête, il perdrait le refresh token
/// que l'API vient d'émettre en révoquant l'ancien, resté seul dans le
/// coffre : le rafraîchissement suivant passerait pour un rejeu, et l'API
/// révoquerait toute la famille (launcher déconnecté).
async fn renew_jwt<R: Runtime>(
    app: &AppHandle<R>,
    auth: &AuthState,
    source: &mut JwtSource,
) -> Result<String, String> {
    match source {
        // Même chemin que le front (verrou de rotation partagé) : les deux ne
        // consomment jamais le même refresh token.
        JwtSource::Session { .. } => {
            let app = app.clone();
            tokio::spawn(async move {
                crate::auth::auth_refresh_access(app.state::<AuthState>())
                    .await
                    .map(|_| ())
                    .map_err(texte)
            })
            .await
            .map_err(texte)??;
            current_jwt(auth, source)
        }
        // Nouvelle connexion du compte (Microsoft puis `/auth/login`, comme
        // au lancement), qui ouvre une nouvelle famille de sessions sans
        // consommer de refresh Reborn. Celui du compte peut aussi être celui
        // de la session du launcher (passé sur ce compte via le carrousel) :
        // le faire tourner ici laisserait un jeton révoqué dans l'emplacement
        // principal du coffre, et le rafraîchissement suivant du launcher
        // passerait pour un rejeu (famille révoquée, launcher déconnecté).
        JwtSource::Alt { mc_uuid, jwt } => {
            let app = app.clone();
            let uuid = mc_uuid.clone();
            let alt = tokio::spawn(async move {
                app.state::<AuthState>()
                    .prepare_alt_account(&uuid)
                    .await
                    .map_err(texte)
            })
            .await
            .map_err(texte)??;
            // Jeton d'un autre compte = refus de Guardian : rien n'est écrit.
            if !super::game::uuid_eq(&alt.user.minecraft_uuid, mc_uuid) {
                return Err(
                    "le compte enregistré ne correspond plus à celui de la 2e instance".to_string(),
                );
            }
            *jwt = alt.reborn_jwt;
            Ok(jwt.clone())
        }
    }
}

/// Texte d'une cause, sans point final (inséré au milieu d'une phrase).
fn texte(e: impl Display) -> String {
    e.to_string().trim_end().trim_end_matches('.').to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch_dir(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "reborn-play-tokens-test-{tag}-{}",
            std::process::id()
        ));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn tmp_file_sits_next_to_the_token() {
        let tmp = tmp_path(Path::new("/jeu/.reborn-play-token-dev"));
        assert_eq!(tmp, Path::new("/jeu/.reborn-play-token-dev.tmp"));
    }

    #[tokio::test]
    async fn atomic_write_replaces_the_file_and_leaves_no_temp() {
        let dir = scratch_dir("atomic");
        let path = dir.join(".reborn-play-token");
        write_token_atomic(&path, "premier").await.unwrap();
        write_token_atomic(&path, "second").await.unwrap();
        assert_eq!(std::fs::read_to_string(&path).unwrap(), "second");
        assert!(!tmp_path(&path).exists());
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn refresh_keeps_a_margin_before_expiry() {
        // Jeton de 5 min : rafraîchi avant ses 4 min, réessai bien plus tôt.
        assert!(REFRESH_INTERVAL <= Duration::from_secs(240));
        assert!(RETRY_DELAY < REFRESH_INTERVAL);
    }

    #[test]
    fn missing_file_is_retried_early() {
        let ok = TokenTarget::new(PathBuf::from("a"), None, true);
        assert_eq!(first_delay(std::slice::from_ref(&ok)), REFRESH_INTERVAL);
        let manquant = TokenTarget::new(PathBuf::from("b"), Some("dev".into()), false);
        assert_eq!(first_delay(&[ok, manquant]), RETRY_DELAY);
    }

    #[test]
    fn only_transient_errors_are_retried_early() {
        let status = |status: u16| ApiError::Status {
            status,
            body: String::new(),
        };
        assert!(retry_soon(&status(502)));
        assert!(retry_soon(&status(503)));
        assert!(!retry_soon(&status(400)));
        assert!(!retry_soon(&status(401)));
        assert!(!retry_soon(&status(403)));
    }

    #[test]
    fn labels_name_the_server() {
        assert_eq!(
            TokenTarget::new(PathBuf::from("a"), None, true).label(),
            "du serveur principal"
        );
        assert_eq!(
            TokenTarget::new(PathBuf::from("b"), Some("dev".into()), true).label(),
            "du serveur « dev »"
        );
    }
}
