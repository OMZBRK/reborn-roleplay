//! Utilitaire ligne de commande Reborn — POST sur les endpoints admin
//! protégés par JWT (manifest signe + release launcher).
//!
//! Lit le JWT Reborn depuis Windows Credential Manager (meme entree que
//! le launcher pose apres login Microsoft), refresh automatiquement si
//! expire, puis POST sur l'API.
//!
//! Usage:
//!   # Manifest des mods (commande historique, compatibilite ascendante)
//!   manifest-uploader --file manifest-signed.json
//!   manifest-uploader manifest --file manifest-signed.json
//!
//!   # Release du launcher (nouvelle commande)
//!   manifest-uploader release \
//!     --version 0.1.1 \
//!     --target windows-x86_64 \
//!     --url https://github.com/OMZBRK/reborn-roleplay/releases/download/.../setup.exe \
//!     --signature "<contenu du .sig>" \
//!     --notes "Description courte" \
//!     [--channel stable]

use clap::{Args, Parser, Subcommand};
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

    /// (Compatibilite ascendante) Chemin vers le manifest signe a publier
    /// sur /admin/manifest. Si fourni, equivalent a la sous-commande
    /// `manifest --file <path>`.
    #[arg(long)]
    file: Option<String>,

    #[command(subcommand)]
    command: Option<Command>,
}

#[derive(Subcommand)]
enum Command {
    /// POST /v1/admin/manifest avec un manifest signe Ed25519 (JSON).
    Manifest(ManifestArgs),
    /// POST /v1/admin/releases avec les metadata d'une release launcher.
    Release(ReleaseArgs),
}

#[derive(Args)]
struct ManifestArgs {
    /// Chemin vers le manifest signe (JSON).
    #[arg(long)]
    file: String,
}

#[derive(Args)]
struct ReleaseArgs {
    /// Version semver de la release (ex: 0.1.1).
    #[arg(long)]
    version: String,
    /// Cible Tauri (ex: windows-x86_64, darwin-aarch64, linux-x86_64).
    #[arg(long, default_value = "windows-x86_64")]
    target: String,
    /// Channel de release.
    #[arg(long, default_value = "stable")]
    channel: String,
    /// URL absolue du .exe / .dmg / .AppImage uploade.
    #[arg(long)]
    url: String,
    /// Contenu base64 du fichier .sig genere par `tauri signer sign`.
    /// Note : si le contenu commence par "untrusted comment:", c'est le
    /// fichier .sig lu directement. Si c'est juste la public signature
    /// affichee par la CLI, c'est OK aussi.
    #[arg(long)]
    signature: String,
    /// Release notes affichees dans la toast UpdateChecker.
    #[arg(long, default_value = "")]
    notes: String,
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

/// Lit le JWT depuis le Credential Manager + refresh automatique si possible.
/// Retourne le token a utiliser pour la requete, ou un code d'exit en cas d'echec.
fn acquire_token(client: &reqwest::blocking::Client, api: &str) -> Result<String, ExitCode> {
    let mut token = match read_secret(KEY_ACCESS) {
        Ok(t) => t,
        Err(e) => {
            eprintln!(
                "Impossible de lire le JWT depuis le Credential Manager : {e}.\n\
                 Lance le launcher et login Microsoft d'abord."
            );
            return Err(ExitCode::from(2));
        }
    };

    if let Ok(refresh) = read_secret(KEY_REFRESH) {
        match refresh_token(client, api, &refresh) {
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
    Ok(token)
}

fn post_json(
    client: &reqwest::blocking::Client,
    url: &str,
    token: &str,
    body: &serde_json::Value,
) -> ExitCode {
    println!("POST {url}");
    println!("Token preview : {}…", &token[..token.len().min(20)]);
    let resp = match client.post(url).bearer_auth(token).json(body).send() {
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
        println!("OK.");
        ExitCode::SUCCESS
    } else {
        eprintln!("Echec.");
        ExitCode::from(1)
    }
}

fn run_manifest(args: ManifestArgs, api: &str, client: &reqwest::blocking::Client, token: &str) -> ExitCode {
    let body = match fs::read_to_string(&args.file) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("Lecture {} echouee : {e}", args.file);
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
    let url = format!("{}/admin/manifest", api.trim_end_matches('/'));
    post_json(client, &url, token, &json)
}

fn run_release(args: ReleaseArgs, api: &str, client: &reqwest::blocking::Client, token: &str) -> ExitCode {
    let body = serde_json::json!({
        "version": args.version,
        "target": args.target,
        "channel": args.channel,
        "url": args.url,
        "signature": args.signature.trim(),
        "notes": args.notes,
    });
    let url = format!("{}/admin/releases", api.trim_end_matches('/'));
    post_json(client, &url, token, &body)
}

fn main() -> ExitCode {
    let cli = Cli::parse();

    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .expect("client http");

    let token = match acquire_token(&client, &cli.api) {
        Ok(t) => t,
        Err(code) => return code,
    };

    // Compatibilite ascendante : `--file foo.json` sans sous-commande =
    // identique a `manifest --file foo.json`.
    if let Some(file) = cli.file.clone() {
        return run_manifest(ManifestArgs { file }, &cli.api, &client, &token);
    }

    match cli.command {
        Some(Command::Manifest(args)) => run_manifest(args, &cli.api, &client, &token),
        Some(Command::Release(args)) => run_release(args, &cli.api, &client, &token),
        None => {
            eprintln!("Aucune action specifiee. Usage :");
            eprintln!("  manifest-uploader manifest --file manifest-signed.json");
            eprintln!("  manifest-uploader release --version 0.1.1 --url ... --signature ...");
            ExitCode::from(2)
        }
    }
}
