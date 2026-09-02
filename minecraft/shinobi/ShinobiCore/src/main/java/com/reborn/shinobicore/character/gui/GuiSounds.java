package com.reborn.shinobicore.character.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * One sound cue per UI intent.
 *
 * <h2>Palette</h2>
 * <ul>
 *   <li>{@link #open}        — soft chest-style thunk as a new menu appears.</li>
 *   <li>{@link #navigate}    — neutral UI click for screen transitions.</li>
 *   <li>{@link #select}      — lighter tick for hover/select actions.</li>
 *   <li>{@link #accept}      — rising pling for confirmation / "yes".</li>
 *   <li>{@link #decline}     — low bass thud for "no".</li>
 *   <li>{@link #destructive} — villager "no" grunt for delete / cancel.</li>
 *   <li>{@link #error}       — higher-pitch villager grunt for invalid input.</li>
 * </ul>
 *
 * <p>All methods are null-safe: a null player is a no-op so the call
 * sites stay tidy.
 */
public final class GuiSounds {

    private GuiSounds() {}

    /** New menu opens. */
    public static void open(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().openCue());
    }

    /** Neutral screen transition (back, next page, navigation click). */
    public static void navigate(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().navigateCue());
    }

    /** Light hover / selection tick. */
    public static void select(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().selectCue());
    }

    /** Confirmation / accept. */
    public static void accept(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().acceptCue());
    }

    /** Decline / no. */
    public static void decline(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().declineCue());
    }

    /** Delete / cancel / destructive action. */
    public static void destructive(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().destructiveCue());
    }

    /** Invalid input / operation refused. */
    public static void error(Player p) {
        play(p, com.reborn.shinobicore.gui.Themes.current().errorCue());
    }

    private static void play(Player p, com.reborn.shinobicore.gui.GuiTheme.SoundCue cue) {
        if (p == null || cue == null) return;
        try { p.playSound(p.getLocation(), cue.sound(), cue.volume(), cue.pitch()); }
        catch (Throwable ignore) { /* never worth blowing up UI for an audio miss */ }
    }
}
