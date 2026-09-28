package com.reborn.shinobicore.character;

import java.util.Locale;

/**
 * Character body archetype — an <b>RP label</b> since the six stats landed
 * (SPEC_STATS_SERVICE §2): shown on the wizard and the sheet, no mechanical
 * effect. Still read once, to split a legacy character's converted points.
 */
public enum Affinity {
    STRENGTH,
    INTELLIGENCE,
    AGILITY;

    public String displayName() {
        String n = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static Affinity from(String s) {
        if (s == null) return STRENGTH;
        try { return Affinity.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return STRENGTH; }
    }
}
