package com.reborn.shinobicore.character;

import java.text.Normalizer;
import java.util.Locale;

/**
 * D'où vient une technique connue — affiché sur la fiche shinobi (onglet
 * Techniques, ligne ORIGINE). Enregistré au moment où la technique est
 * apprise ({@link ShinobiCharacter#learnAbility(String, LearnOrigin)}) ;
 * une technique antérieure à ce suivi reste {@link #INCONNU}.
 */
public enum LearnOrigin {
    /** Apprise sur un rouleau (séances, étagère de bibliothèque). */
    PARCHEMIN,
    /** Enseignée à l'Académie (henge, kawarimi, bunshin…). */
    ACADEMIE,
    /** Héritée du clan (technique secrète, dōjutsu). */
    CLAN,
    /** Transmise par un maître, d'un joueur à l'autre. */
    ENSEIGNEMENT,
    /** Donnée par le staff (commande, GUI d'administration). */
    STAFF,
    /** Aucune trace : technique apprise avant le suivi de l'origine. */
    INCONNU;

    /** Lecture tolérante (casse, accents, espaces) ; {@link #INCONNU} si illisible. */
    public static LearnOrigin from(String raw) {
        if (raw == null || raw.isBlank()) return INCONNU;
        String s = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return switch (s) {
                case "ROULEAU", "SCROLL" -> PARCHEMIN;
                case "ACADEMY" -> ACADEMIE;
                case "MAITRE", "TEACHER", "TEACH" -> ENSEIGNEMENT;
                case "ADMIN" -> STAFF;
                default -> INCONNU;
            };
        }
    }
}
