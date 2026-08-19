package com.reborn.shinobicore.technique;

import java.util.Locale;

/** Minigame difficulty — controls per-step timeouts. */
public enum Difficulty {
    EASY(3000L, 4000L),
    MEDIUM(2000L, 3000L),
    HARD(1500L, 2000L);

    private final long mudraTimeoutMillis;
    private final long pushupRepMillis;

    Difficulty(long mudraTimeoutMillis, long pushupRepMillis) {
        this.mudraTimeoutMillis = mudraTimeoutMillis;
        this.pushupRepMillis = pushupRepMillis;
    }

    /** Time allowed to confirm each mudra in the MUDRA minigame. */
    public long mudraTimeoutMillis() { return mudraTimeoutMillis; }

    /** Time allowed per rep in the PUSHUP minigame. */
    public long pushupRepMillis() { return pushupRepMillis; }

    public static Difficulty from(String s) {
        if (s == null) return MEDIUM;
        try { return Difficulty.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return MEDIUM; }
    }
}
