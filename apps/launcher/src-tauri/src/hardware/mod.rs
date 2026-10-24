//! Détection des specs système exposées à la page Settings -> Jeu, et
//! politique de RAM de la JVM.
//!
//! Référence : PLAN_CONCEPTION_LAUNCHER.md §6.5 (Configurations système) ;
//! politique RAM détaillée dans docs/technique/LAUNCHER_JVM.md §2.
//!
//! On lit RAM totale + CPU brand + cores + OS pour recommander une allocation
//! mémoire raisonnable au lieu de laisser l'utilisateur deviner. Pas de GPU
//! pour l'instant : `sysinfo` ne l'expose pas, et WMI/D3D Windows
//! demanderaient une dépendance supplémentaire pour un gain marginal.

use serde::Serialize;
use std::sync::OnceLock;
use sysinfo::System;

/// RAM minimale allouée à la JVM (Mo) : en dessous, MC 26.x + les mods
/// Reborn ne démarrent pas proprement.
pub const MIN_RAM_MB: u32 = 2048;
/// RAM allouée par défaut (Mo) — même valeur que `Preferences::default`.
pub const DEFAULT_RAM_MB: u32 = 4096;
/// Mémoire laissée au système, au launcher et au navigateur (Mo).
const SYSTEM_RESERVE_MB: u64 = 2048;
/// Plafond affiché quand la RAM système est inconnue (borne du curseur).
const UNKNOWN_MAX_RAM_MB: u32 = 16_384;

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemSpecs {
    pub total_ram_mb: u64,
    pub free_ram_mb: u64,
    pub cpu_brand: String,
    pub cpu_cores: usize,
    pub os_name: String,
    /// RAM JVM recommandée : 4 Go, ramenée à (RAM système − 2 Go) sur les
    /// petites machines, jamais sous 2 Go.
    pub recommended_ram_mb: u32,
    /// RAM JVM maximale autorisée : RAM système − 2 Go (plancher 2 Go). Au
    /// lancement, un choix supérieur est ramené à cette valeur.
    pub max_ram_mb: u32,
}

/// Détecte les specs système. Synchrone et rapide (< 50 ms typique), donc
/// pas besoin de spawn_blocking. Appelée quand la page Settings -> Jeu se
/// charge, pas au boot.
#[tauri::command]
pub fn system_specs_get() -> SystemSpecs {
    let mut sys = System::new();
    sys.refresh_memory();
    sys.refresh_cpu_all();

    let total = total_ram_mb();
    let free_ram_mb = sys.available_memory() / 1024 / 1024;

    let cpu_brand = sys
        .cpus()
        .first()
        .map(|c| c.brand().trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "Processeur inconnu".to_string());
    let cpu_cores = sys.cpus().len();

    let os_name = System::name().unwrap_or_else(|| "Système inconnu".to_string());
    let os_version = System::os_version().unwrap_or_default();
    let os_label = if os_version.is_empty() {
        os_name
    } else {
        format!("{os_name} {os_version}")
    };

    SystemSpecs {
        total_ram_mb: total.unwrap_or(0),
        free_ram_mb,
        cpu_brand,
        cpu_cores,
        os_name: os_label,
        recommended_ram_mb: recommended_ram_mb(),
        max_ram_mb: max_ram_mb(total).unwrap_or(UNKNOWN_MAX_RAM_MB),
    }
}

/// RAM totale du système en Mo, lue une seule fois (elle ne change pas en
/// cours de session). `None` si le système ne la communique pas.
pub fn total_ram_mb() -> Option<u64> {
    static TOTAL: OnceLock<Option<u64>> = OnceLock::new();
    *TOTAL.get_or_init(|| {
        let mut sys = System::new();
        sys.refresh_memory();
        let mb = sys.total_memory() / 1024 / 1024;
        (mb > 0).then_some(mb)
    })
}

/// Plafond de RAM JVM : RAM système − 2 Go, jamais sous [`MIN_RAM_MB`].
/// `None` si la RAM système est inconnue (pas de plafond).
pub fn max_ram_mb(total_mb: Option<u64>) -> Option<u32> {
    total_mb.map(|total| {
        total
            .saturating_sub(SYSTEM_RESERVE_MB)
            .max(u64::from(MIN_RAM_MB))
            .min(u64::from(u32::MAX)) as u32
    })
}

/// RAM recommandée pour ce poste : [`DEFAULT_RAM_MB`], ramenée au plafond sur
/// les petites machines. Exposée au front via `system_specs_get`.
pub fn recommended_ram_mb() -> u32 {
    recommended_for(total_ram_mb())
}

fn recommended_for(total_mb: Option<u64>) -> u32 {
    max_ram_mb(total_mb).map_or(DEFAULT_RAM_MB, |max| DEFAULT_RAM_MB.min(max))
}

/// RAM effectivement passée à `-Xmx` : le choix utilisateur borné à
/// [[`MIN_RAM_MB`], plafond]. Pure (la RAM totale est passée en paramètre).
pub fn clamp_ram_mb(requested_mb: u32, total_mb: Option<u64>) -> u32 {
    let floored = requested_mb.max(MIN_RAM_MB);
    max_ram_mb(total_mb).map_or(floored, |max| floored.min(max))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recommends_default_when_enough_ram() {
        assert_eq!(recommended_for(Some(16_384)), 4_096);
        assert_eq!(recommended_for(Some(6_144)), 4_096);
        assert_eq!(recommended_for(None), 4_096);
    }

    #[test]
    fn recommendation_leaves_2gb_to_the_system() {
        assert_eq!(recommended_for(Some(5_120)), 3_072);
        assert_eq!(recommended_for(Some(4_096)), 2_048);
    }

    #[test]
    fn recommendation_never_below_floor() {
        assert_eq!(recommended_for(Some(3_000)), 2_048);
        assert_eq!(recommended_for(Some(1_024)), 2_048);
    }

    #[test]
    fn max_is_total_minus_reserve_with_floor() {
        assert_eq!(max_ram_mb(Some(16_384)), Some(14_336));
        assert_eq!(max_ram_mb(Some(3_000)), Some(2_048));
        assert_eq!(max_ram_mb(None), None);
    }

    #[test]
    fn clamp_caps_and_floors_user_choice() {
        assert_eq!(clamp_ram_mb(8_192, Some(8_192)), 6_144);
        assert_eq!(clamp_ram_mb(16_384, Some(32_768)), 16_384);
        assert_eq!(clamp_ram_mb(1_024, Some(16_384)), 2_048);
        assert_eq!(clamp_ram_mb(6_144, None), 6_144);
    }
}
