package com.reborn.shinobicore.technique;

import java.util.Locale;

/** Which learning minigame gates the ability on the learning shelf. */
public enum MinigameType {
    /** Title-prompted hand-sign sequence, confirmed by left-clicks. */
    MUDRA,
    /** Sneak-rep workout with a per-rep timer. */
    PUSHUP,
    /** Staff validation — no player input, approved via /sa valider. */
    SUIVI,
    /** Auto-succeeds on shelf interaction. */
    NONE;

    public static MinigameType from(String s) {
        if (s == null) return NONE;
        try { return MinigameType.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return NONE; }
    }
}
