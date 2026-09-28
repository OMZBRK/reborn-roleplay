package com.reborn.shinobicore.character;

import java.util.Locale;

/**
 * Shinobi village rank (grade). Ordered from lowest to highest so that
 * {@link #cycle()} walks the progression naturally when cycled via the
 * admin GUI.
 *
 * <p>Order follows the plan (SPEC_STATS_SERVICE §5): Special Jonin sits
 * <em>after</em> Jonin. Persisted by name, so reordering never breaks a save.
 *
 * <p>Mechanical effect: each rank passage grants stat points
 * ({@link #statTier()}); used by the custom tab-list to tag each character.
 */
public enum Rank {
    ACADEMY       ("Academy Student"),
    GENIN         ("Genin"),
    CHUNIN        ("Chunin"),
    JONIN         ("Jonin"),
    SPECIAL_JONIN ("Special Jonin"),
    ANBU          ("ANBU"),
    SANNIN        ("Sannin"),
    KAGE          ("Kage");

    private final String displayName;

    Rank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /**
     * Rank passages completed, for stat points: Académie 0 → Genin 1 → Chunin 2
     * → Jonin 3 → Special Jonin 4 → ANBU / Sannin / Kage 5 (the ladder's top
     * shares the last passage).
     */
    public int statTier() { return Math.min(5, ordinal()); }

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
