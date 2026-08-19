package com.reborn.shinobicore.gui;

import com.reborn.shinobicore.api.Stable;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * The GUI design tokens as a swappable theme: tier palette, chrome
 * panels, title framing and sound cues. Every method has a default
 * that IS the current Naruto ink/scroll look, so this world's theme
 * is the interface itself and a future world (JJK…) overrides only
 * what it wants — a token swap, not a re-skin of every screen.
 *
 * <p>The static token classes ({@code GuiIcons}, {@code GuiSounds},
 * {@code GuiTitles}) read from {@link Themes#current()}; screens never
 * touch the theme directly.
 */
@Stable
public interface GuiTheme {

    /* ---------------------------------------------------- colour tiers */

    /** "Do the thing" button colour. */
    default NamedTextColor primary()     { return NamedTextColor.GREEN; }

    /** Alternate / soft action colour. */
    default NamedTextColor secondary()   { return NamedTextColor.YELLOW; }

    /** Close / delete / decline colour. */
    default NamedTextColor destructive() { return NamedTextColor.RED; }

    /** Back / neutral navigation colour. */
    default NamedTextColor nav()         { return NamedTextColor.GRAY; }

    /** Display-only panel colour. */
    default NamedTextColor info()        { return NamedTextColor.AQUA; }

    /** Headers / character-identity colour. */
    default NamedTextColor accent()      { return NamedTextColor.GOLD; }

    /* --------------------------------------------------------- chrome */

    /** Panel painting the decorative chest perimeter. */
    default Material borderPanel() { return Material.BLACK_STAINED_GLASS_PANE; }

    /** Panel filling otherwise-empty interior slots. */
    default Material fillerPanel() { return Material.GRAY_STAINED_GLASS_PANE; }

    /* ---------------------------------------------------------- titles */

    /** Decorative sigil bracketing chest titles ({@code ◆ Title ◆}). */
    default String titleSigil() { return "◆"; }

    /** Default framed-title colour. */
    default NamedTextColor titleColour() { return NamedTextColor.GOLD; }

    /* ---------------------------------------------------------- sounds */

    /** One sound cue: sound + volume + pitch. */
    record SoundCue(Sound sound, float volume, float pitch) {}

    /** New menu opens. */
    default SoundCue openCue()        { return new SoundCue(Sound.BLOCK_CHEST_OPEN, 0.35f, 1.3f); }

    /** Neutral screen transition. */
    default SoundCue navigateCue()    { return new SoundCue(Sound.UI_BUTTON_CLICK, 0.5f, 1.2f); }

    /** Light hover / selection tick. */
    default SoundCue selectCue()      { return new SoundCue(Sound.UI_BUTTON_CLICK, 0.4f, 1.6f); }

    /** Confirmation / accept. */
    default SoundCue acceptCue()      { return new SoundCue(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f); }

    /** Decline / no. */
    default SoundCue declineCue()     { return new SoundCue(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f); }

    /** Delete / cancel / destructive action. */
    default SoundCue destructiveCue() { return new SoundCue(Sound.ENTITY_VILLAGER_NO, 0.5f, 0.8f); }

    /** Invalid input / operation refused. */
    default SoundCue errorCue()       { return new SoundCue(Sound.ENTITY_VILLAGER_NO, 0.5f, 1.4f); }
}
