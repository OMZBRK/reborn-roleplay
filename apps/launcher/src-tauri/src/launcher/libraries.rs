//! Téléchargement du client jar + libraries vanilla + extraction des natives.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §8.1 étape [4].
//!
//! La structure cible reproduit le layout du launcher officiel :
//!
//! ```text
//! versions/<id>/<id>.jar          ← client jar
//! versions/<id>/natives/          ← .dll / .so / .dylib extraits
//! libraries/<group>/<artifact>/<version>/<artifact>-<version>.jar
//! ```
//!
//! Client jar + libraries forment un seul ensemble vérifié
//! (`.reborn-verify/libraries-<id>.json`) : hachage sauté tant que le témoin
//! correspond et que chaque jar est présent à la bonne taille.

use std::path::{Path, PathBuf};
use tokio::fs;

use super::mojang::{
    ensure_file_set, io_at, native_classifier_matches_current_arch, LibraryArtifact, MojangError,
    RemoteFile, VersionJson,
};

const MAX_CONCURRENT_DOWNLOADS: usize = 8;

#[derive(Debug, thiserror::Error)]
pub enum LibrariesError {
    #[error(transparent)]
    Mojang(#[from] MojangError),
    #[error(
        "Extraction des bibliothèques natives impossible depuis « {jar} » : {detail} — le \
         fichier est peut-être corrompu, lance « Réparer »."
    )]
    Zip { jar: String, detail: String },
    #[error("Extraction des bibliothèques natives interrompue ({0}) — relance le jeu.")]
    Internal(String),
}

impl LibrariesError {
    /// Vrai si la cause est réseau (cf [`MojangError::is_network`]).
    pub(crate) fn is_network(&self) -> bool {
        matches!(self, Self::Mojang(e) if e.is_network())
    }
}

/// Résultat : chemins absolus du client jar, des libs, et du dossier natives
/// extrait. Prêt à être passé à `LaunchConfig`.
#[derive(Debug, Clone)]
pub struct LibrarySetup {
    pub client_jar: PathBuf,
    pub library_jars: Vec<PathBuf>,
    pub natives_dir: PathBuf,
}

/// Étape principale : pour la version donnée, s'assure que le client jar +
/// toutes les libraries applicables à l'OS courant + natives sont locales,
/// retourne les chemins nécessaires au build_command.
pub async fn ensure_libraries(
    http: &reqwest::Client,
    game_dir: &Path,
    version: &VersionJson,
) -> Result<LibrarySetup, LibrariesError> {
    let version_dir = game_dir.join("versions").join(&version.id);
    let client_jar = version_dir.join(format!("{}.jar", version.id));
    let natives_dir = version_dir.join("natives");
    let libraries_root = game_dir.join("libraries");

    let client = &version.downloads.client;
    let mut files = vec![RemoteFile {
        url: client.url.clone(),
        sha1: Some(client.sha1.clone()),
        size: Some(client.size),
        dest: client_jar.clone(),
    }];
    let mut classpath_jars: Vec<PathBuf> = Vec::new();
    let mut natives_jars: Vec<(PathBuf, Vec<String>)> = Vec::new();

    for library in &version.libraries {
        if !library.applies_to_current_os() {
            continue;
        }
        let Some(downloads) = &library.downloads else {
            continue;
        };
        let exclude = || {
            library
                .extract
                .as_ref()
                .map(|e| e.exclude.clone())
                .unwrap_or_default()
        };

        // Mojang utilise deux formats pour les natives :
        // - Ancien : library + natives map + classifiers map (1 entrée par lib)
        // - Nouveau (1.19+) : une library séparée par classifier, avec le
        //   classifier dans le `name` (4e champ) et l'artifact direct.
        // Dans le nouveau format on doit ROUTER l'artifact vers natives_jars
        // (extraction) plutôt que vers le classpath, et ne garder que la
        // variante qui correspond à l'arch courante.
        let classifier_in_name = library.classifier_from_name();
        if let Some(classifier) = classifier_in_name
            .as_deref()
            .filter(|c| c.starts_with("natives-"))
        {
            if !native_classifier_matches_current_arch(classifier) {
                continue;
            }
            if let Some(artifact) = &downloads.artifact {
                let dest = libraries_root.join(&artifact.path);
                natives_jars.push((dest.clone(), exclude()));
                files.push(remote(artifact, dest));
            }
            continue;
        }

        // Library normale : artifact principal sur le classpath.
        if let Some(artifact) = &downloads.artifact {
            let dest = libraries_root.join(&artifact.path);
            classpath_jars.push(dest.clone());
            files.push(remote(artifact, dest));
        }

        // Ancien format : `natives` map + `classifiers` map sur la même lib.
        if let Some(classifier) = library.current_native_classifier() {
            if let Some(native_artifact) = downloads.classifiers.get(&classifier) {
                let dest = libraries_root.join(&native_artifact.path);
                natives_jars.push((dest.clone(), exclude()));
                files.push(remote(native_artifact, dest));
            }
        }
    }

    ensure_file_set(
        http,
        game_dir,
        &format!("libraries-{}", version.id),
        files,
        MAX_CONCURRENT_DOWNLOADS,
        |_, _| {},
    )
    .await?;

    fs::create_dir_all(&natives_dir)
        .await
        .map_err(io_at(&natives_dir))?;
    for (jar, exclude) in natives_jars {
        let dest = natives_dir.clone();
        // L'extraction zip est synchrone — on l'isole sur le pool bloquant.
        tokio::task::spawn_blocking(move || extract_natives(&jar, &dest, &exclude))
            .await
            .map_err(|e| LibrariesError::Internal(e.to_string()))??;
    }

    Ok(LibrarySetup {
        client_jar,
        library_jars: classpath_jars,
        natives_dir,
    })
}

fn remote(artifact: &LibraryArtifact, dest: PathBuf) -> RemoteFile {
    RemoteFile {
        url: artifact.url.clone(),
        sha1: Some(artifact.sha1.clone()),
        size: Some(artifact.size),
        dest,
    }
}

/// Extrait les fichiers natives d'un jar dans `natives_dir`. Ignore les
/// entrées qui commencent par un préfixe de `exclude` (en général
/// « META-INF/ ») et tout `META-INF/`. Un fichier déjà extrait à la même
/// taille n'est pas réécrit : c'est le cas normal à chaque lancement, et une
/// 2e instance garde les .dll de la première verrouillées sous Windows.
fn extract_natives(
    jar: &Path,
    natives_dir: &Path,
    exclude: &[String],
) -> Result<(), LibrariesError> {
    let zip_error = |detail: String| LibrariesError::Zip {
        jar: jar.display().to_string(),
        detail,
    };
    let file = std::fs::File::open(jar).map_err(io_at(jar))?;
    let mut archive = zip::ZipArchive::new(file).map_err(|e| zip_error(e.to_string()))?;

    for i in 0..archive.len() {
        let mut entry = archive.by_index(i).map_err(|e| zip_error(e.to_string()))?;
        if entry.is_dir() {
            continue;
        }
        let name = entry.name().to_string();
        if name.starts_with("META-INF/") || exclude.iter().any(|p| name.starts_with(p)) {
            continue;
        }
        // Mojang met les natives à plat : on ne garde que le nom de base (pas
        // de sous-dossier, donc pas de traversée de chemin possible).
        let Some(basename) = Path::new(&name).file_name() else {
            continue;
        };
        let out_path = natives_dir.join(basename);
        let already_there = std::fs::metadata(&out_path)
            .map(|m| m.len() == entry.size())
            .unwrap_or(false);
        if already_there {
            continue;
        }
        let mut out = std::fs::File::create(&out_path).map_err(io_at(&out_path))?;
        std::io::copy(&mut entry, &mut out).map_err(io_at(&out_path))?;
    }
    Ok(())
}
