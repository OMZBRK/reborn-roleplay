package com.reborn.shinobicore.character;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Elemental chakra affinity. Every character starts with {@link #NONE} and
 * picks a real one via the Leaf Test.
 */
public enum ChakraAffinity {
    NONE,
    KATON,   // Fire
    SUITON,  // Water
    FUTON,   // Wind
    DOTON,   // Earth
    RAITON;  // Lightning

    /** The five rollable elements (excludes NONE). */
    public static final ChakraAffinity[] ROLLABLE = {
            KATON, SUITON, FUTON, DOTON, RAITON
    };

    public String displayName() {
        String n = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static ChakraAffinity from(String s) {
        if (s == null) return NONE;
        try { return ChakraAffinity.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return NONE; }
    }

    /** Uniform roll over the 5 real elements. */
    public static ChakraAffinity roll() {
        return ROLLABLE[ThreadLocalRandom.current().nextInt(ROLLABLE.length)];
    }
}
