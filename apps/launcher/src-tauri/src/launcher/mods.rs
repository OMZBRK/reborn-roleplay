//! Inspection et nettoyage du dossier `mods/` du game directory.
//!
//! Pour chaque .jar present, on lit `fabric.mod.json` (au format Fabric)
//! pour extraire (id, version, depends.minecraft). Si la contrainte de
//! version Minecraft ne correspond pas a la version cible du launcher,
//! le jar est marque comme incompatible et peut etre supprime.
//!
//! Cette logique est volontairement conservative : si on ne peut pas
//! parser la contrainte, on **ne supprime pas** (mode "don't be too smart").
//! L'utilisateur peut toujours nettoyer manuellement.

use serde::{Deserialize, Serialize};
use std::io::Read;
use std::path::{Path, PathBuf};

#[derive(Debug, thiserror::Error)]
pub enum ModsError {
    #[error("io : {0}")]
    Io(#[from] std::io::Error),
    #[error("zip : {0}")]
    Zip(#[from] zip::result::ZipError),
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModEntry {
    pub file_name: String,
    pub absolute_path: String,
    pub mod_id: Option<String>,
    pub mod_version: Option<String>,
    /// Contrainte sur Minecraft, telle qu'ecrite dans fabric.mod.json
    /// (ex: ">=1.21.1-", "1.21.x", "[1.21,1.22)").
    pub minecraft_constraint: Option<String>,
    pub size_bytes: u64,
    /// Vrai si on a pu determiner avec certitude que ce mod ne supporte
    /// pas la version Minecraft cible. Faux par defaut (incertitude →
    /// on ne marque pas).
    pub incompatible_with_target: bool,
}

/// Liste tous les .jar du dossier mods avec leur metadata Fabric.
pub fn inspect_mods_folder(
    mods_dir: &Path,
    target_mc_version: &str,
) -> Result<Vec<ModEntry>, ModsError> {
    let mut entries = Vec::new();
    if !mods_dir.exists() {
        return Ok(entries);
    }
    for dirent in std::fs::read_dir(mods_dir)? {
        let dirent = dirent?;
        let path = dirent.path();
        if path.extension().and_then(|s| s.to_str()) != Some("jar") {
            continue;
        }
        let file_name = path
            .file_name()
            .and_then(|s| s.to_str())
            .unwrap_or("")
            .to_string();
        let size = dirent.metadata()?.len();
        let info = read_fabric_metadata(&path).ok();
        let constraint = info.as_ref().and_then(|m| m.minecraft_constraint.clone());
        let incompatible = constraint
            .as_deref()
            .map(|c| !constraint_accepts(c, target_mc_version))
            .unwrap_or(false);
        entries.push(ModEntry {
            file_name,
            absolute_path: path.display().to_string(),
            mod_id: info.as_ref().and_then(|m| m.id.clone()),
            mod_version: info.as_ref().and_then(|m| m.version.clone()),
            minecraft_constraint: constraint,
            size_bytes: size,
            incompatible_with_target: incompatible,
        });
    }
    Ok(entries)
}

/// Supprime tous les .jar marques `incompatible_with_target = true`.
/// Retourne la liste des fichiers supprimes (chemins absolus).
pub fn purge_incompatible_mods(
    mods_dir: &Path,
    target_mc_version: &str,
) -> Result<Vec<String>, ModsError> {
    let entries = inspect_mods_folder(mods_dir, target_mc_version)?;
    let mut removed = Vec::new();
    for entry in entries {
        if !entry.incompatible_with_target {
            continue;
        }
        let path = PathBuf::from(&entry.absolute_path);
        match std::fs::remove_file(&path) {
            Ok(()) => removed.push(entry.absolute_path),
            Err(e) => tracing::warn!(
                "impossible de supprimer mod incompatible {} : {e}",
                entry.absolute_path
            ),
        }
    }
    Ok(removed)
}

#[derive(Debug, Clone)]
struct FabricModMetadata {
    id: Option<String>,
    version: Option<String>,
    /// Contrainte sur "minecraft" extraite de "depends".
    minecraft_constraint: Option<String>,
}

#[derive(Debug, Deserialize)]
struct FabricModJson {
    id: Option<String>,
    version: Option<String>,
    depends: Option<serde_json::Value>,
}

fn read_fabric_metadata(jar_path: &Path) -> Result<FabricModMetadata, ModsError> {
    let f = std::fs::File::open(jar_path)?;
    let mut zip = zip::ZipArchive::new(f)?;
    let mut entry = match zip.by_name("fabric.mod.json") {
        Ok(e) => e,
        Err(_) => {
            return Ok(FabricModMetadata {
                id: None,
                version: None,
                minecraft_constraint: None,
            })
        }
    };
    let mut bytes = Vec::with_capacity(entry.size() as usize);
    entry.read_to_end(&mut bytes)?;
    let parsed: FabricModJson = match serde_json::from_slice(&bytes) {
        Ok(v) => v,
        Err(_) => {
            return Ok(FabricModMetadata {
                id: None,
                version: None,
                minecraft_constraint: None,
            })
        }
    };
    let mc_constraint = parsed.depends.and_then(|d| {
        d.get("minecraft").and_then(|v| match v {
            serde_json::Value::String(s) => Some(s.clone()),
            // Fabric autorise un tableau de contraintes (semantique OR).
            // On encode toutes les branches en "a||b||c" ; constraint_accepts
            // les evalue en OR. Prendre seulement la premiere (ancien code)
            // purgeait a tort un mod dont la version active n'etait pas en
            // tete du tableau (ex: DistantHorizons ["26.1.0","26.1.1","26.1.2"]
            // avec la cible 26.1.2).
            serde_json::Value::Array(arr) => {
                let joined = arr
                    .iter()
                    .filter_map(|x| x.as_str())
                    .collect::<Vec<_>>()
                    .join("||");
                if joined.is_empty() {
                    None
                } else {
                    Some(joined)
                }
            }
            _ => None,
        })
    });
    Ok(FabricModMetadata {
        id: parsed.id,
        version: parsed.version,
        minecraft_constraint: mc_constraint,
    })
}

/// Determine si la contrainte Fabric (ex: "1.21.4", "~1.21.1", ">=1.21.1-")
/// accepte la version `target` (ex: "1.21.1").
///
/// On gere les cas frequents :
///   - exact ("1.21.4")
///   - prefix wildcard ("1.21.x", "1.21.*")
///   - operateurs ">=", "<=", ">", "<"
///   - tilde / caret ("~1.21.1", "^1.21.1") -> meme major.minor
///   - ranges entre crochets / parentheses ("[1.21,1.22)") -> approx
///
/// Si on ne sait pas, on retourne `true` (= ne pas suspecter).
fn constraint_accepts(constraint: &str, target: &str) -> bool {
    let constraint = constraint.trim();
    let target_parts = parse_version(target);
    if constraint.is_empty() || constraint == "*" || constraint.eq_ignore_ascii_case("any") {
        return true;
    }
    // Tableau Fabric (OR) encode "a||b||c" : accepte si AU MOINS une branche
    // accepte. A evaluer avant le split par espace (AND) car les deux syntaxes
    // peuvent coexister ("26.1.0||26.1.1" n'a pas d'espace, mais on garde l'OR
    // prioritaire par clarte).
    if constraint.contains("||") {
        return constraint
            .split("||")
            .any(|part| constraint_accepts(part.trim(), target));
    }
    // Contraintes composees (Fabric autorise ">=1.21- <1.22-" ou ">=1.21 <1.22").
    // Semantique AND : chaque sous-contrainte separee par un espace doit
    // accepter la target. On evalue recursivement plutot que d'accepter
    // aveuglement — sinon un mod verrouille a ">=1.21 <1.21.2" est considere
    // compatible avec 26.1.2 et n'est jamais purge (bug migration 1.21->26.1).
    if constraint.contains(char::is_whitespace) {
        return constraint
            .split_whitespace()
            .all(|part| constraint_accepts(part, target));
    }
    // Wildcard suffix : 1.21.x / 1.21.*
    if let Some(prefix) = constraint
        .strip_suffix(".x")
        .or_else(|| constraint.strip_suffix(".*"))
    {
        return target.starts_with(&format!("{prefix}."));
    }
    if let Some(rest) = constraint.strip_prefix(">=") {
        return cmp_versions(&parse_version(rest.trim()), &target_parts).is_le();
    }
    if let Some(rest) = constraint.strip_prefix(">") {
        return cmp_versions(&parse_version(rest.trim()), &target_parts).is_lt();
    }
    if let Some(rest) = constraint.strip_prefix("<=") {
        return cmp_versions(&parse_version(rest.trim()), &target_parts).is_ge();
    }
    if let Some(rest) = constraint.strip_prefix("<") {
        return cmp_versions(&parse_version(rest.trim()), &target_parts).is_gt();
    }
    if let Some(rest) = constraint.strip_prefix('~').or_else(|| constraint.strip_prefix('^')) {
        let cv = parse_version(rest.trim());
        return cv.0 == target_parts.0 && cv.1 == target_parts.1;
    }
    // Range "[a,b)" / "[a,b]" / "(a,b)" : borne basse inclusive/exclusive
    // selon le crochet, borne haute idem. On respecte la borne haute (sinon
    // un range [1.21,1.22) est accepte a tort pour 26.1.2).
    if constraint.starts_with('[') || constraint.starts_with('(') {
        let incl_min = constraint.starts_with('[');
        let incl_max = constraint.ends_with(']');
        let inner = &constraint[1..constraint.len().saturating_sub(1)];
        if let Some((min_str, max_str)) = inner.split_once(',') {
            let (min_str, max_str) = (min_str.trim(), max_str.trim());
            let ok_min = if min_str.is_empty() {
                true
            } else {
                let min = parse_version(min_str);
                let ord = cmp_versions(&target_parts, &min);
                if incl_min { ord.is_ge() } else { ord.is_gt() }
            };
            let ok_max = if max_str.is_empty() {
                true
            } else {
                let max = parse_version(max_str);
                let ord = cmp_versions(&target_parts, &max);
                if incl_max { ord.is_le() } else { ord.is_lt() }
            };
            return ok_min && ok_max;
        }
    }
    // Exact match (sans operateur). Si la contrainte a moins de segments
    // que la target (ex: "1.21" vs target "1.21.1"), on considere que la
    // contrainte est un prefixe et on accepte toute version qui partage
    // ces segments. C'est la convention Fabric : sodium declare souvent
    // "minecraft": "1.21" pour accepter tous les 1.21.x.
    let c_segs: Vec<u32> = constraint
        .split('.')
        .map(|p| {
            p.chars()
                .take_while(|c| c.is_ascii_digit())
                .collect::<String>()
                .parse()
                .unwrap_or(0)
        })
        .collect();
    let t_segs = [target_parts.0, target_parts.1, target_parts.2];
    c_segs
        .iter()
        .zip(t_segs.iter())
        .all(|(c, t)| c == t)
}

fn parse_version(s: &str) -> (u32, u32, u32) {
    let cleaned: String = s
        .chars()
        .take_while(|c| c.is_ascii_digit() || *c == '.')
        .collect();
    let mut parts = cleaned.split('.').map(|p| p.parse::<u32>().unwrap_or(0));
    (
        parts.next().unwrap_or(0),
        parts.next().unwrap_or(0),
        parts.next().unwrap_or(0),
    )
}

fn cmp_versions(a: &(u32, u32, u32), b: &(u32, u32, u32)) -> std::cmp::Ordering {
    a.cmp(b)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exact_constraint() {
        assert!(constraint_accepts("1.21.1", "1.21.1"));
        assert!(!constraint_accepts("1.21.4", "1.21.1"));
    }

    #[test]
    fn wildcard_constraint() {
        assert!(constraint_accepts("1.21.x", "1.21.1"));
        assert!(constraint_accepts("1.21.*", "1.21.4"));
        assert!(!constraint_accepts("1.20.x", "1.21.0"));
    }

    #[test]
    fn ge_constraint() {
        assert!(constraint_accepts(">=1.21.1", "1.21.1"));
        assert!(constraint_accepts(">=1.21.1", "1.21.4"));
        assert!(!constraint_accepts(">=1.21.4", "1.21.1"));
    }

    #[test]
    fn range_constraint() {
        assert!(constraint_accepts("[1.21,1.22)", "1.21.4"));
        assert!(constraint_accepts("[1.21,1.22)", "1.21.0"));
    }

    #[test]
    fn composite_constraint_rejects_across_major_jump() {
        // Bug migration 1.21 -> 26.1 : les mods 1.21.1 declarent des ranges
        // composes avec espace. Avant le fix, l'espace => accepte aveuglement.
        assert!(!constraint_accepts(">=1.21 <1.21.2", "26.1.2"));
        assert!(!constraint_accepts(">=1.21 <=1.21.1", "26.1.2"));
        assert!(!constraint_accepts(">=1.21.0- <1.21.2-", "26.1.2"));
        // Un mod forward-compatible (>=1.21 sans borne haute) reste accepte.
        assert!(constraint_accepts(">=1.21", "26.1.2"));
        // Un vrai mod 26.1 reste accepte.
        assert!(constraint_accepts(">=26.1 <26.2", "26.1.2"));
        assert!(!constraint_accepts(">=26.1 <26.2", "1.21.1"));
    }

    #[test]
    fn range_respects_upper_bound() {
        assert!(!constraint_accepts("[1.21,1.22)", "26.1.2"));
        assert!(constraint_accepts("[1.21,1.22)", "1.21.4"));
        assert!(!constraint_accepts("[1.21,1.21.1]", "1.21.2"));
        assert!(constraint_accepts("[26.1,26.2)", "26.1.2"));
    }

    #[test]
    fn tilde_constraint() {
        assert!(constraint_accepts("~1.21.1", "1.21.4"));
        assert!(!constraint_accepts("~1.21.1", "1.22.0"));
    }

    #[test]
    fn or_array_constraint() {
        // depends.minecraft peut etre un tableau Fabric (OR), encode "a||b||c".
        // Regression : DistantHorizons declare ["26.1.0","26.1.1","26.1.2"] et
        // l'ancien parser ne lisait que "26.1.0" -> purge a tort sous 26.1.2.
        assert!(constraint_accepts("26.1.0||26.1.1||26.1.2", "26.1.2"));
        assert!(constraint_accepts("26.1.0||26.1.1||26.1.2", "26.1.0"));
        assert!(!constraint_accepts("26.1.0||26.1.1||26.1.2", "26.1.3"));
        assert!(!constraint_accepts("26.1.0||26.1.1", "26.2.0"));
        // Branches avec operateurs.
        assert!(constraint_accepts(">=26.1||1.21.1", "26.1.2"));
        assert!(constraint_accepts(">=26.1||1.21.1", "1.21.1"));
    }
}
