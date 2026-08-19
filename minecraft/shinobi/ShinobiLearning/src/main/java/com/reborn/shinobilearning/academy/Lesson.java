package com.reborn.shinobilearning.academy;

/**
 * The Academy curriculum — the lessons a student completes before they may
 * graduate to Genin. (See ShinobiLearning_DESIGN.md §3.) MVP set; the
 * chakra-control ladder and the Academy Three are the iconic core.
 */
public enum Lesson {
    GRIMPE_ARBRE ("Montée à l'arbre (contrôle du chakra)"),
    MARCHE_EAU   ("Marche sur l'eau (contrôle du chakra)"),
    HENGE        ("Henge no Jutsu (métamorphose)"),
    KAWARIMI     ("Kawarimi no Jutsu (substitution)"),
    BUNSHIN      ("Bunshin no Jutsu (clone)"),
    ARMES        ("Maniement kunai / shuriken"),
    TAIJUTSU     ("Bases du taijutsu");

    private final String displayName;

    Lesson(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }

    /** Tolerant parse by enum name; {@code null} when unknown. */
    public static Lesson from(String s) {
        if (s == null) return null;
        String t = s.trim();
        for (Lesson l : values()) {
            if (l.name().equalsIgnoreCase(t)) return l;
        }
        return null;
    }
}
