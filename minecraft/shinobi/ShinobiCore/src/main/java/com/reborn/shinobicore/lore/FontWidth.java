package com.reborn.shinobicore.lore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Default-font glyph-width table for Minecraft's chat / tab list font.
 *
 * <p>The default Minecraft font is variable-width — character-count
 * padding doesn't yield consistent column alignment. This class
 * computes the rendered pixel width of a {@link Component} (or a raw
 * {@code String}) so callers can pad with spaces to a target pixel
 * width and get the visually consistent column we actually want.
 *
 * <p>The widths are the well-known vanilla values published by the
 * Minecraft community (each glyph + 1 trailing pixel of spacing). Bold
 * text adds 1 pixel per glyph; non-Latin chars fall back to the most
 * common width. Pixel values are precise enough for tab-list two-column
 * alignment within a few pixels.
 */
public final class FontWidth {

    private FontWidth() {}

    /** A regular space character is 4 pixels wide in the default font. */
    public static final int SPACE_WIDTH = 4;

    /** Per-character pixel widths for ASCII glyphs (without bold offset).
     *  Lookup by codepoint; codepoints &gt;= 128 fall back to {@link #DEFAULT_WIDTH}. */
    private static final int[] WIDTHS = new int[128];
    /** Width assumed for any glyph we don't have an entry for. */
    private static final int DEFAULT_WIDTH = 6;

    static {
        // Default for the bulk of letters and digits.
        for (int i = 0; i < WIDTHS.length; i++) WIDTHS[i] = DEFAULT_WIDTH;

        // Width 2 — very narrow glyphs.
        for (char c : "!,.:;|i'`".toCharArray()) WIDTHS[c] = 2;
        // Width 3.
        WIDTHS['l'] = 3;
        // Width 4 — narrow glyphs / punctuation.
        WIDTHS[' '] = 4;
        WIDTHS['('] = 4;
        WIDTHS[')'] = 4;
        WIDTHS['['] = 4;
        WIDTHS[']'] = 4;
        WIDTHS['{'] = 4;
        WIDTHS['}'] = 4;
        WIDTHS['*'] = 4;
        WIDTHS['I'] = 4;
        WIDTHS['t'] = 4;
        WIDTHS['<'] = 5;
        WIDTHS['>'] = 5;
        WIDTHS['f'] = 5;
        WIDTHS['k'] = 5;
        // Most letters & digits keep DEFAULT_WIDTH = 6.
        // Width 7 — wider glyphs.
        WIDTHS['@'] = 7;
        WIDTHS['~'] = 7;
    }

    /** Pixel width of a single ASCII codepoint (non-bold). */
    public static int charWidth(char c) {
        return c < WIDTHS.length ? WIDTHS[c] : DEFAULT_WIDTH;
    }

    /** Pixel width of a string at default styling (non-bold). */
    public static int width(String s) {
        return width(s, false);
    }

    /** Pixel width of a string honouring the bold flag (+1 px per glyph). */
    public static int width(String s, boolean bold) {
        if (s == null || s.isEmpty()) return 0;
        int total = 0;
        int boldExtra = bold ? 1 : 0;
        for (int i = 0; i < s.length(); i++) {
            total += charWidth(s.charAt(i)) + boldExtra;
        }
        return total;
    }

    /** Pixel width of a {@link Component}. Walks the tree, honouring
     *  the bold decoration as it inherits down. Non-{@link TextComponent}
     *  pieces (translatable, keybind, ...) are ignored — they don't
     *  appear in our tab content. */
    public static int width(Component component) {
        return widthInternal(component, false);
    }

    private static int widthInternal(Component c, boolean parentBold) {
        Style style = c.style();
        TextDecoration.State boldState = style.decoration(TextDecoration.BOLD);
        boolean bold = boldState == TextDecoration.State.TRUE
                || (boldState == TextDecoration.State.NOT_SET && parentBold);

        int total = 0;
        if (c instanceof TextComponent tc) {
            total += width(tc.content(), bold);
        }
        for (Component child : c.children()) {
            total += widthInternal(child, bold);
        }
        return total;
    }

    /** Build a padding component that adds {@code missingPixels}
     *  worth of horizontal space, accurate to within 1 pixel. Uses a
     *  mix of regular spaces (4 px each) and bold spaces (5 px each)
     *  so the total pixel sum exactly matches — or differs by at most
     *  1 px from — the target. Returns an empty component when the
     *  missing width is 0 or negative.
     *
     *  <p>Why bold spaces — Minecraft renders bold by drawing each
     *  glyph twice with a 1-pixel offset, which adds 1 px per glyph
     *  including invisible spaces. That gives us a 4 / 5 px granularity
     *  and any non-trivial pixel count can be expressed as
     *  {@code 4n + 5m} for some non-negative {@code n}, {@code m}. */
    public static Component spacePadding(int missingPixels) {
        if (missingPixels <= 0) return Component.empty();

        // Find the (n, m) that gets us closest to missingPixels using
        // n regular spaces (4 px) + m bold spaces (5 px). The search
        // accepts both undershoot and overshoot — that gives us 1 px
        // accuracy in cases where a strict-undershoot algorithm would
        // be off by 3 px (e.g. a 3 px gap rounds to 0 spaces under
        // strict undershoot but to 1 regular space (4 px, +1 overshoot)
        // here, which lines up the bar much better visually).
        int bestN = 0;
        int bestM = 0;
        int bestErr = missingPixels;  // err of the empty-padding case
        for (int m = 0; m <= 4; m++) {
            // For each m, try the n that floors the remainder AND
            // the n that ceilings it (in case the round-up lands
            // closer to target).
            int remaining = missingPixels - m * (SPACE_WIDTH + 1);
            int nFloor = Math.max(0, remaining / SPACE_WIDTH);
            int nCeil  = nFloor + 1;
            for (int n : new int[] { nFloor, nCeil }) {
                if (n < 0) continue;
                int total = n * SPACE_WIDTH + m * (SPACE_WIDTH + 1);
                int err = Math.abs(missingPixels - total);
                if (err < bestErr) {
                    bestErr = err;
                    bestN = n;
                    bestM = m;
                }
            }
        }

        Component out = Component.empty();
        if (bestN > 0) out = out.append(Component.text(" ".repeat(bestN)));
        if (bestM > 0) out = out.append(Component.text(" ".repeat(bestM))
                .decorate(TextDecoration.BOLD));
        return out;
    }
}
