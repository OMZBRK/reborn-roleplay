package com.reborn.shinobicore.character;

import java.util.Locale;

/**
 * Primary combat art a character trains in.
 */
public enum NinjaArt {
    TAIJUTSU,
    KENJUTSU;

    public String displayName() {
        String n = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    public static NinjaArt from(String s) {
        if (s == null) return TAIJUTSU;
        try { return NinjaArt.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return TAIJUTSU; }
    }
}
