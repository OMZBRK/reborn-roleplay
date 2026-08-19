package com.reborn.shinobicore.technique;

import java.util.Locale;

/** How an ability manifests in play. */
public enum ExecutionType {
    /** Cast through a JutsuItem (picker / quick-cast). */
    JUTSU,
    /** Body technique — no item, applied through gameplay. */
    PHYSICAL,
    /** Always-on once learned. */
    PASSIVE,
    /** Bound to a tool / utility item. */
    TOOL;

    public static ExecutionType from(String s) {
        if (s == null) return JUTSU;
        try { return ExecutionType.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return JUTSU; }
    }
}
