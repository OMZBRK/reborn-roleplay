package com.reborn.shinobicore.technique;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

/**
 * Jutsu rank — E (basic) through HIDEN (secret / kinjutsu). Distinct
 * from ShinobiCore's village {@code Rank} (Genin, Chunin, …), which is
 * a character grade with no mechanical link to jutsu difficulty.
 *
 * <p>The rank drives the incantation fallback step count (used when an
 * ability declares no explicit mudra list) and the PUSHUP minigame rep
 * count.
 */
public enum JutsuRank {
    E(2, NamedTextColor.GRAY),
    D(2, NamedTextColor.WHITE),
    C(4, NamedTextColor.GREEN),
    B(4, NamedTextColor.AQUA),
    A(6, NamedTextColor.GOLD),
    HIDEN(6, NamedTextColor.LIGHT_PURPLE);

    private final int defaultMudraCount;
    private final NamedTextColor color;

    JutsuRank(int defaultMudraCount, NamedTextColor color) {
        this.defaultMudraCount = defaultMudraCount;
        this.color = color;
    }

    /** Incantation steps when the ability has no explicit mudra list:
     *  E/D = 2, C/B = 4, A/HIDEN = 6. */
    public int defaultMudraCount() { return defaultMudraCount; }

    public NamedTextColor color() { return color; }

    public String displayName() {
        return this == HIDEN ? "Hiden" : name();
    }

    /** Tolerant parse; accepts "Kinjutsu" as an alias for HIDEN.
     *  Falls back to E on unknown input. */
    public static JutsuRank from(String s) {
        if (s == null) return E;
        String n = s.trim().toUpperCase(Locale.ROOT);
        if (n.equals("KINJUTSU")) return HIDEN;
        try { return JutsuRank.valueOf(n); }
        catch (IllegalArgumentException ignore) { return E; }
    }
}
