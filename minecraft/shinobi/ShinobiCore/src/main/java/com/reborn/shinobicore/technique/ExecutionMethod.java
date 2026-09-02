package com.reborn.shinobicore.technique;

import java.util.Locale;

/**
 * How a jutsu fires once its picker slot is selected.
 *
 * <p>{@link #F_PRESS} is <b>deprecated and never honoured</b> — F is
 * reserved for the picker open/close. The registry rewrites it to
 * {@link #LEFT_CLICK} at load time with a console warning.
 */
public enum ExecutionMethod {
    /** Single left-click (arm swing) fires. Default. */
    LEFT_CLICK,
    /** Single right-click fires. */
    RIGHT_CLICK,
    /** Legacy alias of LEFT_CLICK — the mudra-ness lives in the
     *  incantation step count, not the trigger. */
    MUDRA,
    /** Sneak held ≥ 1 s, fires on release. */
    HOLD_SNEAK,
    /** Three clicks (LMB or RMB) within 2 s. */
    CLICK_SEQUENCE,
    /** DEPRECATED — F is reserved for the picker. Never use. */
    @Deprecated
    F_PRESS;

    /** True for the triggers fired by an arm swing. */
    public boolean firesOnLeftClick() {
        return this == LEFT_CLICK || this == MUDRA;
    }

    public static ExecutionMethod from(String s) {
        if (s == null) return LEFT_CLICK;
        try { return ExecutionMethod.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return LEFT_CLICK; }
    }
}
