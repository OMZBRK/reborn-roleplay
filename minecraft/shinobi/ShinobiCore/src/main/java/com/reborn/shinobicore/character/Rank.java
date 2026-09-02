package com.reborn.shinobicore.character;

import java.util.Locale;

/**
 * Shinobi village rank (grade). Ordered from lowest to highest so that
 * {@link #cycle()} walks the progression naturally when cycled via the
 * admin GUI.
 *
 * <p>Pure display metadata; has no mechanical effect. Used by the
 * custom tab-list to tag each character.
 */
public enum Rank {
    ACADEMY       ("Academy Student"),
    GENIN         ("Genin"),
    CHUNIN        ("Chunin"),
    SPECIAL_JONIN ("Special Jonin"),
    JONIN         ("Jonin"),
    ANBU          ("ANBU"),
    SANNIN        ("Sannin"),
    KAGE          ("Kage");

    private final String displayName;

    Rank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /** Advance to the next rank, wrapping back to the start after the highest. */
    public Rank cycle() {
        Rank[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Tolerant parse; falls back to {@link #GENIN} on unknown input. */
    public static Rank from(String s) {
        if (s == null) return GENIN;
        try {
            return Rank.valueOf(s.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ignore) {
            return GENIN;
        }
    }
}
