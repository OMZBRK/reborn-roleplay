package com.reborn.shinobicore.character;

import java.util.Locale;

/**
 * Character body affinity. Each affinity buffs exactly one stat, with the
 * multiplier scaling up every few levels (see {@link AffinityMultipliers}).
 *
 * <ul>
 *   <li>{@link #STRENGTH} → HP / max health</li>
 *   <li>{@link #INTELLIGENCE} → Chakra pool</li>
 *   <li>{@link #AGILITY} → Movement speed (applied <em>after</em> potion effects)</li>
 * </ul>
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
