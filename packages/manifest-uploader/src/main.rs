//! Utilitaire ligne de commande : lit le JWT Reborn dans Windows Credential
//! Manager (meme service que le launcher) et POST le manifest signe sur l'API.
//!
//! Usage:
//!   manifest-uploader --api https://api.reborn-rp.com/v1--file path/to/manifest-signed.json
//!
//! Le JWT est lu dans l'entree `fr.reborn-rp.launcher` / `reborn_access_token`
//! de Windows Credential Manager — c'est la cle posee par le launcher apres
//! login Microsoft.

use clap::Parser;
use keyring::Entry;
use std::fs;
use std::process::ExitCode;

const SERVICE: &str = "fr.reborn-rp.launcher";
const KEY_ACCESS: &str = "reborn_access_token";
const KEY_REFRESH: &str = "reborn_refresh_token";

#[derive(Parser)]
struct Cli {
    /// Base URL de l'API Reborn (ex: https://api.reborn-rp.com/v1).
    #[arg(long, default_value = "https://api.reborn-rp.com/v1")]
    api: String,
    /// Chemin vers le manifest signe (JSON).
    #[arg(long)]
    file: String,
}

fn read_secret(name: &str) -> Result<String, String> {
    Entry::new(SERVICE, name)
        .and_then(|e| e.get_password())
        .map_err(|e| format!("{e}"))
}

fn refresh_token(
    client: &reqwest::blocking::Client,
    api: &str,
    refresh: &str,
) -> Result<(String, String), String> {
    let url = format!("{}/auth/refresh", api.trim_end_matches('/'));
    let resp = client
        .post(&url)
        .json(&serde_json::json!({ "refreshToken": refresh }))
        .send()
        .map_err(|e| format!("refresh request : {e}"))?;
    let status = resp.status();
    let text = resp.text().unwrap_or_default();
    if !status.is_success() {
        return Err(format!("refresh HTTP {status} : {text}"));
    }
    let v: serde_json::Value =
        serde_json::from_str(&text).map_err(|e| format!("parse refresh response : {e}"))?;
    let access = v
        .get("accessToken")
        .and_then(|x| x.as_str())
        .ok_or("accessToken absent de la reponse refresh")?
        .to_string();
    let new_refresh = v
        .get("refreshToken")
        .and_then(|x| x.as_str())
        .ok_or("refreshToken absent de la reponse refresh")?
        .to_string();
    Ok((access, new_refresh))
}

fn main() -> ExitCode {
    let cli = Cli::parse();

    let mut token = match read_secret(KEY_ACCESS) {
        Ok(t) => t,
        Err(e) => {
            eprintln!(
                "Impossible de lire le JWT depuis le Credential Manager : {e}.\n\
                 Lance le launcher et login Microsoft d'abord."
            );
            return ExitCode::from(2);
        }
    };

    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .expect("client http");

    // Le JWT a un TTL court (15 min). On tente un refresh systematiquement
    // si le refresh token est present : ca evite les 401 quand l'utilisateur
    // a ouvert le launcher il y a un moment.
    if let Ok(refresh) = read_secret(KEY_REFRESH) {
        match refresh_token(&client, &cli.api, &refresh) {
            Ok((new_access, new_refresh)) => {
                println!("Refresh OK — nouveau JWT acquis.");
                token = new_access;
                if let Ok(entry) = Entry::new(SERVICE, KEY_ACCESS) {
                    let _ = entry.set_password(&token);
                }
                if let Ok(entry) = Entry::new(SERVICE, KEY_REFRESH) {
                    let _ = entry.set_password(&new_refresh);
                }
            }
            Err(e) => {
                eprintln!("Refresh echec ({e}) — on tente avec le JWT existant.");
            }
        }
    }

    let body = match fs::read_to_string(&cli.file) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("Lecture {} echouee : {e}", cli.file);
            return ExitCode::from(2);
        }
    };

    let json: serde_json::Value = match serde_json::from_str(&body) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("JSON invalide : {e}");
            return ExitCode::from(2);
        }
    };

    let url = format!("{}/admin/manifest", cli.api.trim_end_matches('/'));
    println!("POST {url}");
    println!("Token preview : {}…", &token[..token.len().min(20)]);

    let resp = match client
        .post(&url)
        .bearer_auth(&token)
        .json(&json)
        .send()
    {
        Ok(r) => r,
        Err(e) => {
            eprintln!("Requete echouee : {e}");
            return ExitCode::from(3);
        }
    };

    let status = resp.status();
    let text = resp.text().unwrap_or_default();
    println!("HTTP {status}");
    println!("{text}");

    if status.is_success() {
        println!("OK : manifest publie.");
        ExitCode::SUCCESS
    } else {
        eprintln!("Echec.");
        ExitCode::from(1)
    }
}
