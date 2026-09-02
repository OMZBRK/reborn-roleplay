package com.reborn.shinobicore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Component-styling helpers. Wraps the project's two most-repeated
 * Adventure-API patterns:
 * <ul>
 *   <li><b>Display name</b> — bold, non-italic, custom colour. Used
 *       on every item display name (~30 sites).</li>
 *   <li><b>Lore line</b> — non-italic, gray (or custom colour). Used
 *       on every item lore + GUI tile lore (~140 sites).</li>
 * </ul>
 *
 * <p>Without this helper:
 * <pre>{@code
 *   Component.text("Hello", NamedTextColor.GOLD)
 *           .decoration(TextDecoration.BOLD, true)
 *           .decoration(TextDecoration.ITALIC, false)
 * }</pre>
 * <p>With:
 * <pre>{@code
 *   Texts.title("Hello", NamedTextColor.GOLD)
 * }</pre>
 *
 * <p>Adventure's {@code .decoration(false)} is required because Bukkit
 * defaults item lore to italic — forgetting the override is the most
 * common visual bug in custom items.
 */
public final class Texts {

    private Texts() {}

    /* ------------------------------------------------------- titles */

    /** Bold + non-italic title in {@code color}. Use for item display
     *  names, GUI tile titles, headers. */
    public static Component title(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Bold + non-italic title in gold. */
    public static Component title(String text) {
        return title(text, NamedTextColor.GOLD);
    }

    /* --------------------------------------------------------- lore */

    /** Non-italic lore line in {@code color}. */
    public static Component lore(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Non-italic gray lore line — the most common case. */
    public static Component lore(String text) {
        return lore(text, NamedTextColor.GRAY);
    }

    /** Italic lore line (e.g. "à venir" placeholders, flavour text). */
    public static Component flavour(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, true);
    }

    public static Component flavour(String text) {
        return flavour(text, NamedTextColor.DARK_GRAY);
    }

    /* ------------------------------------------------------- chat */

    /** Single-colour plain chat line — no decorations forced. */
    public static Component chat(String text, TextColor color) {
        return Component.text(text, color);
    }

    /** Empty visual spacer line — useful inside lore lists. */
    public static Component spacer() {
        return Component.empty();
    }
}
