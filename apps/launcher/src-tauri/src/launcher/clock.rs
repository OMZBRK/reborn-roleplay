//! Petites fonctions de date sans dépendance (ni chrono ni time dans le
//! binaire) : horodatage courant, conversion calendrier ↔ jours, lecture
//! RFC 3339 (dates du manifeste signé) et formatage UTC (noms de fichiers de
//! log, messages d'erreur).
//!
//! Conversion calendrier : algorithmes de Howard Hinnant (« chrono-compatible
//! low-level date algorithms »), exacts sur tout le calendrier grégorien.

use std::time::{SystemTime, UNIX_EPOCH};

/// Millisecondes Unix courantes (0 si l'horloge système est avant 1970).
pub fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Jours écoulés depuis le 1970-01-01 pour une date grégorienne.
pub fn days_from_civil(year: i64, month: u32, day: u32) -> i64 {
    let y = if month <= 2 { year - 1 } else { year };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let m = i64::from(month);
    let doy = (153 * (if m > 2 { m - 3 } else { m + 9 }) + 2) / 5 + i64::from(day) - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe - 719_468
}

/// Inverse de [`days_from_civil`] : (année, mois 1..=12, jour 1..=31).
pub fn civil_from_days(days: i64) -> (i64, u32, u32) {
    let z = days + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let day = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let month = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    let year = yoe + era * 400 + i64::from(month <= 2);
    (year, month, day)
}

fn days_in_month(year: i64, month: u32) -> u32 {
    match month {
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        4 | 6 | 9 | 11 => 30,
        _ if (year % 4 == 0 && year % 100 != 0) || year % 400 == 0 => 29,
        _ => 28,
    }
}

/// Lit une date RFC 3339 `AAAA-MM-JJTHH:MM:SS(.fff…)(Z|±HH:MM)` et renvoie
/// les millisecondes Unix. `None` si la chaîne ne respecte pas ce format
/// (on refuse plutôt que de deviner : ces dates protègent le manifeste).
///
/// Le signer émet `2026-05-10T09:00:00.000Z` ; on accepte aussi la forme
/// sans fraction, un décalage horaire explicite et une fraction de 1 à 9
/// chiffres (tronquée à la milliseconde).
pub fn parse_rfc3339_ms(input: &str) -> Option<i64> {
    let s = input.trim();
    let b = s.as_bytes();
    let digits = |from: usize, to: usize| -> Option<i64> {
        let part = s.get(from..to)?;
        if part.is_empty() || !part.bytes().all(|c| c.is_ascii_digit()) {
            return None;
        }
        part.parse().ok()
    };
    let sep = |at: usize, allowed: &[u8]| b.get(at).is_some_and(|c| allowed.contains(c));

    if !(sep(4, b"-") && sep(7, b"-") && sep(10, b"Tt ") && sep(13, b":") && sep(16, b":")) {
        return None;
    }
    let year = digits(0, 4)?;
    let month = u32::try_from(digits(5, 7)?).ok()?;
    let day = u32::try_from(digits(8, 10)?).ok()?;
    let hour = digits(11, 13)?;
    let minute = digits(14, 16)?;
    // Seconde intercalaire (60) ramenée à 59 : sans importance ici.
    let second = digits(17, 19)?.min(59);
    if !(1..=12).contains(&month)
        || day == 0
        || day > days_in_month(year, month)
        || hour > 23
        || minute > 59
    {
        return None;
    }

    let mut i = 19;
    let mut millis = 0i64;
    if b.get(i) == Some(&b'.') {
        let start = i + 1;
        i = start;
        while b.get(i).is_some_and(u8::is_ascii_digit) {
            i += 1;
        }
        if i == start || i - start > 9 {
            return None;
        }
        let frac = &s[start..i];
        let ms_part = &frac[..frac.len().min(3)];
        millis = ms_part.parse::<i64>().ok()? * 10i64.pow(3 - ms_part.len() as u32);
    }

    let offset_minutes = match b.get(i) {
        Some(b'Z' | b'z') => {
            i += 1;
            0
        }
        Some(sign @ (b'+' | b'-')) => {
            let sign = if *sign == b'-' { -1 } else { 1 };
            if !sep(i + 3, b":") {
                return None;
            }
            let oh = digits(i + 1, i + 3)?;
            let om = digits(i + 4, i + 6)?;
            if oh > 23 || om > 59 {
                return None;
            }
            i += 6;
            sign * (oh * 60 + om)
        }
        _ => return None,
    };
    if i != b.len() {
        return None;
    }

    let days = days_from_civil(year, month, day);
    let secs = days * 86_400 + hour * 3_600 + minute * 60 + second - offset_minutes * 60;
    Some(secs * 1_000 + millis)
}

/// Date et heure UTC décomposées.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct UtcParts {
    pub year: i64,
    pub month: u32,
    pub day: u32,
    pub hour: u32,
    pub minute: u32,
    pub second: u32,
}

pub fn utc_parts(ms: i64) -> UtcParts {
    let secs = ms.div_euclid(1_000);
    let days = secs.div_euclid(86_400);
    let rem = secs.rem_euclid(86_400) as u32;
    let (year, month, day) = civil_from_days(days);
    UtcParts {
        year,
        month,
        day,
        hour: rem / 3_600,
        minute: rem % 3_600 / 60,
        second: rem % 60,
    }
}

/// `AAAA-MM-JJ_HH-MM-SS` (UTC), pour les noms de fichiers.
pub fn format_file_stamp(ms: i64) -> String {
    let p = utc_parts(ms);
    format!(
        "{:04}-{:02}-{:02}_{:02}-{:02}-{:02}",
        p.year, p.month, p.day, p.hour, p.minute, p.second
    )
}

/// `JJ/MM/AAAA à HH:MM UTC`, pour les messages affichés à l'utilisateur.
pub fn format_human(ms: i64) -> String {
    let p = utc_parts(ms);
    format!(
        "{:02}/{:02}/{:04} à {:02}:{:02} UTC",
        p.day, p.month, p.year, p.hour, p.minute
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn epoch_and_known_dates() {
        assert_eq!(parse_rfc3339_ms("1970-01-01T00:00:00Z"), Some(0));
        // 2000-03-01 = 11017 jours après l'epoch.
        assert_eq!(
            parse_rfc3339_ms("2000-03-01T00:00:00.000Z"),
            Some(951_868_800_000)
        );
        assert_eq!(days_from_civil(2000, 3, 1), 11_017);
        assert_eq!(civil_from_days(11_017), (2000, 3, 1));
    }

    #[test]
    fn signer_forms_are_equivalent() {
        let a = parse_rfc3339_ms("2026-05-10T09:00:00Z").unwrap();
        let b = parse_rfc3339_ms("2026-05-10T09:00:00.000Z").unwrap();
        let c = parse_rfc3339_ms("2026-05-10T11:00:00+02:00").unwrap();
        assert_eq!(a, b);
        assert_eq!(a, c);
        assert_eq!(parse_rfc3339_ms("2026-05-10T09:00:00.5Z").unwrap(), a + 500);
        assert_eq!(
            parse_rfc3339_ms("2026-05-10T09:00:00.123456789Z").unwrap(),
            a + 123
        );
    }

    #[test]
    fn rejects_malformed_dates() {
        for bad in [
            "",
            "2026-05-10",
            "2026-05-10T09:00:00",
            "2026-13-10T09:00:00Z",
            "2026-02-30T09:00:00Z",
            "2026-05-10T24:00:00Z",
            "2026-05-10T09:00:00.Z",
            "2026-05-10T09:00:00Zjunk",
            "2026-05-10T09:00:00+0200",
            "２026-05-10T09:00:00Z",
        ] {
            assert_eq!(parse_rfc3339_ms(bad), None, "{bad:?} devrait être refusé");
        }
    }

    #[test]
    fn leap_years() {
        assert!(parse_rfc3339_ms("2024-02-29T00:00:00Z").is_some());
        assert!(parse_rfc3339_ms("2100-02-29T00:00:00Z").is_none());
        assert!(parse_rfc3339_ms("2000-02-29T00:00:00Z").is_some());
    }

    #[test]
    fn formatting_round_trip() {
        let ms = parse_rfc3339_ms("2026-10-24T07:05:09.999Z").unwrap();
        assert_eq!(format_file_stamp(ms), "2026-10-24_07-05-09");
        assert_eq!(format_human(ms), "24/10/2026 à 07:05 UTC");
        for days in [-1_000_000i64, -1, 0, 59, 60, 365, 20_000, 2_000_000] {
            let (y, m, d) = civil_from_days(days);
            assert_eq!(days_from_civil(y, m, d), days);
        }
    }
}
