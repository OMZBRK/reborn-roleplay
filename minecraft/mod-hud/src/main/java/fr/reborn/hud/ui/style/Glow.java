package fr.reborn.hud.ui.style;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Effet de glow par fills concentriques à alpha décroissant. Fallback sans
 * shader / blur GPU — l'overlay reste visuellement convaincant à coût
 * négligeable.
 *
 * <p>Le nombre de couches ({@link #STEPS}) est un trade-off : plus on en a,
 * plus le halo est doux, plus le coût CPU monte (linéaire). 5 couches
 * donnent un fade ~5px avec une transition douce.
 */
public final class Glow {

    /** Nombre de couches concentriques. */
    private static final int STEPS = 5;

    private Glow() {}

    /** Glow autour d'un rect droit. */
    public static void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int baseColor) {
        int baseAlpha = (baseColor >>> 24) & 0xFF;
        int rgb = baseColor & 0x00FFFFFF;
        for (int i = STEPS; i >= 1; i--) {
            int alpha = baseAlpha * (STEPS + 1 - i) / (STEPS * 2 + 2);
            int color = (alpha << 24) | rgb;
            ctx.fill(x - i, y - i, x + w + i, y + h + i, color);
        }
    }

    /** Glow autour d'un rect arrondi. */
    public static void roundedRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int baseColor) {
        int baseAlpha = (baseColor >>> 24) & 0xFF;
        int rgb = baseColor & 0x00FFFFFF;
        for (int i = STEPS; i >= 1; i--) {
            int alpha = baseAlpha * (STEPS + 1 - i) / (STEPS * 2 + 2);
            int color = (alpha << 24) | rgb;
            RoundedRect.fill(ctx, x - i, y - i, w + i * 2, h + i * 2, r + i, color);
        }
    }
}
