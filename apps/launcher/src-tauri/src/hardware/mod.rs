//! Detection des specs systeme exposees a la page Settings -> Jeu.
//!
//! Reference : PLAN_CONCEPTION_LAUNCHER.md §6.5 (Configurations systeme).
//!
//! On lit RAM totale + CPU brand + cores + OS pour pouvoir recommander une
//! allocation memoire raisonnable au lieu de laisser l'utilisateur deviner.
//! Pas de GPU pour l'instant : `sysinfo` ne l'expose pas, et WMI/D3D Windows
//! demanderaient une dep supplementaire pour un gain marginal — on s'en
//! occupera quand le besoin se fera sentir.

use serde::Serialize;
use sysinfo::System;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemSpecs {
    pub total_ram_mb: u64,
    pub free_ram_mb: u64,
    pub cpu_brand: String,
    pub cpu_cores: usize,
    pub os_name: String,
    /// RAM recommandee pour la JVM : la moitie de la RAM totale, plafonnee
    /// a 12 Go (au-dessus, le G1GC tunning par defaut souffre, et MC vanilla
    /// n'en tire rien). Minimum 2 Go pour eviter les configs absurdes.
    pub recommended_ram_mb: u32,
}

/// Detecte les specs systeme. Synchrone et rapide (< 50ms typique), donc
/// pas besoin de tokio::spawn_blocking. On l'appelle uniquement quand la
/// page Settings -> Jeu se charge, pas au boot.
#[tauri::command]
pub fn system_specs_get() -> SystemSpecs {
    let mut sys = System::new();
    sys.refresh_memory();
    sys.refresh_cpu_all();

    let total_ram_bytes = sys.total_memory();
    let total_ram_mb = total_ram_bytes / 1024 / 1024;
    let free_ram_mb = sys.available_memory() / 1024 / 1024;

    let cpu_brand = sys
        .cpus()
        .first()
        .map(|c| c.brand().trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "CPU inconnu".to_string());
    let cpu_cores = sys.cpus().len();

    let os_name = sysinfo::System::name().unwrap_or_else(|| "OS inconnu".to_string());
    let os_version = sysinfo::System::os_version().unwrap_or_default();
    let os_label = if os_version.is_empty() {
        os_name
    } else {
        format!("{os_name} {os_version}")
    };

    SystemSpecs {
        total_ram_mb,
        free_ram_mb,
        cpu_brand,
        cpu_cores,
        os_name: os_label,
        recommended_ram_mb: recommended_ram_mb(total_ram_mb),
    }
}

fn recommended_ram_mb(total_mb: u64) -> u32 {
    // Moitie de la RAM totale, dans [2048, 12288].
    let half = total_mb / 2;
    half.clamp(2048, 12288) as u32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recommends_half_within_bounds() {
        assert_eq!(recommended_ram_mb(16_384), 8_192);
        assert_eq!(recommended_ram_mb(8_192), 4_096);
    }

    #[test]
    fn recommends_at_least_2gb_on_tiny_systems() {
        assert_eq!(recommended_ram_mb(2_048), 2_048);
        assert_eq!(recommended_ram_mb(1_024), 2_048);
    }

    #[test]
    fn caps_at_12gb_on_huge_systems() {
        assert_eq!(recommended_ram_mb(64_000), 12_288);
        assert_eq!(recommended_ram_mb(128_000), 12_288);
    }
}
