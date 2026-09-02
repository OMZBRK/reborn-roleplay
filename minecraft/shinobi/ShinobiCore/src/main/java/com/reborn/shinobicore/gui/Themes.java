package com.reborn.shinobicore.gui;

import com.reborn.shinobicore.api.Stable;

/**
 * Holder for the active {@link GuiTheme}. Defaults to the interface's
 * built-in Naruto look; a world pack swaps it once at boot (before any
 * GUI opens) via {@link #set}.
 */
@Stable
public final class Themes {

    /** The built-in defaults — the Naruto ink/scroll look. */
    private static final GuiTheme DEFAULT = new GuiTheme() {};

    private static volatile GuiTheme current = DEFAULT;

    private Themes() {}

    /** The active theme. Never null. */
    public static GuiTheme current() { return current; }

    /** Swap the active theme (null restores the default). Call once at
     *  boot before any GUI opens — themes are not per-player. */
    public static void set(GuiTheme theme) {
        current = theme == null ? DEFAULT : theme;
    }
}
