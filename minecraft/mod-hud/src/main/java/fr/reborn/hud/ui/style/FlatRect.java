package fr.reborn.hud.ui.style;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rectangles <b>plats</b> (coins nets, aucun arrondi) — style Reborn sobre à la
 * PhotoMode. Signatures identiques à {@link RoundedRect} (avec un paramètre
 * {@code radius} ignoré) pour permettre un remplacement direct.
 */
public final class FlatRect {

    private FlatRect() {}

    public static void fill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    public static void border(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void borderThick(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int color) {
        ctx.fill(x, y, x + w, y + 2, color);
        ctx.fill(x, y + h - 2, x + w, y + h, color);
        ctx.fill(x, y, x + 2, y + h, color);
        ctx.fill(x + w - 2, y, x + w, y + h, color);
    }
}
