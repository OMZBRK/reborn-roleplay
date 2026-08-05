package fr.reborn.hud.ui.style;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dessin de rectangles à coins arrondis sans textures.
 *
 * <p>Algo : 3 fills rectangulaires pour le corps + un quart-de-disque
 * "pixel par pixel" pour chacun des 4 coins. Chaque pixel de coin est
 * inclus si {@code dx² + dy² <= r²}, exclu sinon. O(r²) par appel,
 * négligeable pour r ≤ 14 (typique : 6 à 10).
 *
 * <p>Pour un border 1px, on utilise un anneau : pixels inclus si
 * {@code (r-1)² < dx²+dy² <= r²}.
 *
 * <p>Pourquoi pas de texture : la consigne du brief exige zéro nouvelle
 * texture pour cette PR. Le rendu pixel-par-pixel n'est pas anti-aliased
 * mais reste propre à petits radius (≤ 12px) sur un écran HD.
 */
public final class RoundedRect {

    private RoundedRect() {}

    /** Remplit un rectangle à coins arrondis avec {@code color}. */
    public static void fill(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        // Clamp radius pour ne pas dépasser les demi-dimensions
        r = Math.min(r, Math.min(w / 2, h / 2));

        // Bandes principales (sans les coins)
        ctx.fill(x,     y + r,     x + w,     y + h - r, color);  // centre vertical pleine largeur
        ctx.fill(x + r, y,         x + w - r, y + r,     color);  // top
        ctx.fill(x + r, y + h - r, x + w - r, y + h,     color);  // bottom

        // 4 coins : quart-de-disque par fills 1x1
        int rSq = r * r;
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                int cx = r - dx, cy = r - dy;
                if (cx * cx + cy * cy <= rSq) {
                    ctx.fill(x + dx,         y + dy,         x + dx + 1,         y + dy + 1,         color); // TL
                    ctx.fill(x + w - 1 - dx, y + dy,         x + w - dx,         y + dy + 1,         color); // TR
                    ctx.fill(x + dx,         y + h - 1 - dy, x + dx + 1,         y + h - dy,         color); // BL
                    ctx.fill(x + w - 1 - dx, y + h - 1 - dy, x + w - dx,         y + h - dy,         color); // BR
                }
            }
        }
    }

    /** Dessine un border 1px à coins arrondis. Pas de remplissage. */
    public static void border(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0) {
            ctx.fill(x,         y,         x + w,     y + 1,     color);
            ctx.fill(x,         y + h - 1, x + w,     y + h,     color);
            ctx.fill(x,         y,         x + 1,     y + h,     color);
            ctx.fill(x + w - 1, y,         x + w,     y + h,     color);
            return;
        }
        r = Math.min(r, Math.min(w / 2, h / 2));

        // 4 segments droits (sans les coins)
        ctx.fill(x + r,     y,         x + w - r, y + 1,     color);  // top
        ctx.fill(x + r,     y + h - 1, x + w - r, y + h,     color);  // bottom
        ctx.fill(x,         y + r,     x + 1,     y + h - r, color);  // left
        ctx.fill(x + w - 1, y + r,     x + w,     y + h - r, color);  // right

        // Coins : pixels sur l'anneau (r-1)² < d² <= r²
        int rSq = r * r;
        int inSq = (r - 1) * (r - 1);
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                int cx = r - dx, cy = r - dy;
                int distSq = cx * cx + cy * cy;
                if (distSq <= rSq && distSq > inSq) {
                    ctx.fill(x + dx,         y + dy,         x + dx + 1,         y + dy + 1,         color); // TL
                    ctx.fill(x + w - 1 - dx, y + dy,         x + w - dx,         y + dy + 1,         color); // TR
                    ctx.fill(x + dx,         y + h - 1 - dy, x + dx + 1,         y + h - dy,         color); // BL
                    ctx.fill(x + w - 1 - dx, y + h - 1 - dy, x + w - dx,         y + h - dy,         color); // BR
                }
            }
        }
    }

    /**
     * Border épaisseur 2px (visuellement plus présente). Implémentée comme
     * une succession de 2 anneaux pour rester propre sur les coins.
     */
    public static void borderThick(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        border(ctx, x, y, w, h, r, color);
        if (w > 2 && h > 2) {
            border(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), color);
        }
    }
}
