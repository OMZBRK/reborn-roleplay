package fr.reborn.hud.ui.style;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dessin de rectangles à coins arrondis sans textures.
 *
 * <p>Algo : 3 fills rectangulaires pour le corps + un quart-de-disque pour
 * chacun des 4 coins. Un pixel de coin est inclus si {@code dx² + dy² <= r²},
 * exclu sinon. Le rendu est batché : une span horizontale par rangée de coin
 * ({@code ~4} fills/rangée, soit {@code O(r)} au lieu de {@code O(r²)}) — le
 * jeu de pixels reste identique au test pixel-par-pixel.
 *
 * <p>Pour un border 1px, on utilise un anneau : pixels inclus si
 * {@code (r-1)² < dx²+dy² <= r²} (même batch par spans).
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

        // 4 coins : quart-de-disque (mêmes pixels que le test dx²+dy² <= r²) mais
        // batché en spans horizontales — ~4 fills par rangée au lieu d'un fill 1×1/pixel.
        int rSq = r * r;
        for (int dy = 0; dy < r; dy++) {
            int cy = r - dy;
            int rem = rSq - cy * cy;
            if (rem < 0) continue;                     // aucun dx admissible sur cette rangée
            int startDx = r - (int) Math.sqrt(rem);    // 1er dx où (r-dx)²+cy² <= r²
            if (startDx >= r) continue;
            int left = x + startDx, right = x + r;             // [startDx, r) — bord gauche
            int mLeft = x + w - r, mRight = x + w - startDx;   // miroir droit
            ctx.fill(left,  y + dy,         right,  y + dy + 1, color); // TL
            ctx.fill(mLeft, y + dy,         mRight, y + dy + 1, color); // TR
            ctx.fill(left,  y + h - 1 - dy, right,  y + h - dy, color); // BL
            ctx.fill(mLeft, y + h - 1 - dy, mRight, y + h - dy, color); // BR
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

        // Coins : anneau (r-1)² < d² <= r² (mêmes pixels), batché en spans horizontales.
        int rSq = r * r;
        int inSq = (r - 1) * (r - 1);
        for (int dy = 0; dy < r; dy++) {
            int cy = r - dy;
            int cy2 = cy * cy;
            int remOut = rSq - cy2;
            if (remOut < 0) continue;                  // rien sur l'anneau à cette rangée
            int loDx = r - (int) Math.sqrt(remOut);    // d² <= r²        → dx >= loDx
            int remIn = inSq - cy2;                    // d² > (r-1)²     → dx <  hiDx
            int hiDx = (remIn < 0) ? r : r - (int) Math.sqrt(remIn);
            if (loDx >= hiDx) continue;
            int left = x + loDx, right = x + hiDx;               // [loDx, hiDx) — bord gauche
            int mLeft = x + w - hiDx, mRight = x + w - loDx;     // miroir droit
            ctx.fill(left,  y + dy,         right,  y + dy + 1, color); // TL
            ctx.fill(mLeft, y + dy,         mRight, y + dy + 1, color); // TR
            ctx.fill(left,  y + h - 1 - dy, right,  y + h - dy, color); // BL
            ctx.fill(mLeft, y + h - 1 - dy, mRight, y + h - dy, color); // BR
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
