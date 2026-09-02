package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Chest-title builders for ShinobiCore GUIs.
 *
 * <h2>Why not true centering?</h2>
 * Minecraft's chest title renders left-aligned at a fixed position and
 * there's no style-level way to "center" a title across the full bar.
 * The closest approximations are:
 * <ol>
 *   <li>Padding the string with a fixed number of spaces (unreliable —
 *       the default font is variable-width).</li>
 *   <li>Framing the title with decorative glyphs so it <em>reads</em>
 *       centered even when left-aligned.</li>
 * </ol>
 * We use option 2: {@code "◆ Title ◆"} — symmetric glyphs that look
 * intentional, scale with whatever the title length ends up being, and
 * don't rely on monospace assumptions.
 */
public final class GuiTitles {

    /** The decorative sigil we bracket titles with — theme-supplied. */
    private static String sigil() {
        return com.reborn.shinobicore.gui.Themes.current().titleSigil();
    }

    private GuiTitles() {}

    /* -------------------------------------------------- plain framed title */

    /** Framed bold title in {@code colour}, e.g. {@code ◆ Leaf Test ◆}. */
    public static Component framed(String text, NamedTextColor colour) {
        return Component.text(sigil() + " " + text + " " + sigil(), colour)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Framed bold gold title — the default for most of our GUIs. */
    public static Component framed(String text) {
        return framed(text, com.reborn.shinobicore.gui.Themes.current().titleColour());
    }

    /* ----------------------------------------------- character-contextual */

    /**
     * Framed title that includes the character's identity, e.g.
     * {@code ◆ Edit — Hashirama Senju ◆}. The "— Name Clan" suffix is
     * tinted with the character's clan colour so the menu's header
     * already carries the person's identity.
     *
     * <p>If {@code c} is {@code null} this degrades to {@link #framed}.
     */
    public static Component framedWithCharacter(String base, ShinobiCharacter c) {
        if (c == null) return framed(base);
        NamedTextColor clanColour = Clan.colourFor(c.clan());
        String realName = CharacterDisplay.realNameString(c);
        NamedTextColor titleColour = com.reborn.shinobicore.gui.Themes.current().titleColour();
        return Component.text(sigil() + " ", titleColour)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(base + " — ", titleColour)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(realName, clanColour)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" " + sigil(), titleColour)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
    }
}
