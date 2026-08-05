package fr.reborn.hud.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Utilitaires de rendu 2D — primitives que GuiGraphicsExtractor n'expose pas
 * nativement (rounded rects, gradients, glows, cercles).
 *
 * <p>Implémentations approximatives par pixel : suffisant pour des UI
 * statiques de title screen / ESC menu, pas pour du rendering temps réel
 * intensif. Toutes les fonctions sont statiques et idempotentes.
 *
 * <p>Convention : les coordonnées (x, y) désignent le coin haut-gauche,
 * (w, h) la largeur/hauteur. Les angles sont en degrés (sens horaire,
 * 0° = droite).
 */
public final class DrawHelpers {

    private DrawHelpers() {}

    // ────────────────────────────────────────────────────────
    // Rectangles
    // ────────────────────────────────────────────────────────

    /** Rectangle plein simple — wrapper de GuiGraphicsExtractor.fill avec dimensions w/h. */
    public static void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    /**
     * Rectangle avec bordure 1px. La bordure est INSIDE (le rect total
     * reste w×h). Si {@code fillColor == 0}, seule la bordure est dessinée.
     */
    public static void outlinedRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                    int fillColor, int borderColor) {
        if (fillColor != 0) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor);
        }
        // 4 bords 1px chacun.
        ctx.fill(x, y, x + w, y + 1, borderColor);                // top
        ctx.fill(x, y + h - 1, x + w, y + h, borderColor);        // bottom
        ctx.fill(x, y + 1, x + 1, y + h - 1, borderColor);        // left
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);// right
    }

    /**
     * Rectangle aux coins arrondis. Approximation par escalier de pixels
     * (test de distance² par rapport au centre du corner, math int 2x).
     * Acceptable pour radius ≤ 12. Coût : ~4*r² fill calls.
     */
    public static void roundedRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                   int radius, int color) {
        if (radius <= 0) {
            rect(ctx, x, y, w, h, color);
            return;
        }
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        // Bande centrale verticale (occupe toute la hauteur).
        ctx.fill(x + r, y, x + w - r, y + h, color);
        // Bandes latérales (sans les coins).
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        // Coins arrondis : test |2 * (r - i - 0.5)|² + |2 * (r - j - 0.5)|²
        // <= (2r)². Math int seulement — pas de Math.sqrt ni double.
        int r2x4 = 4 * r * r;
        for (int i = 0; i < r; i++) {
            int dx = 2 * (r - i) - 1;
            int dx2 = dx * dx;
            for (int j = 0; j < r; j++) {
                int dy = 2 * (r - j) - 1;
                if (dx2 + dy * dy <= r2x4) {
                    ctx.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    ctx.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    ctx.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    ctx.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }

    /** Rectangle arrondi avec bordure 1px. */
    public static void roundedOutlinedRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                           int radius, int fillColor, int borderColor) {
        roundedRect(ctx, x, y, w, h, radius, borderColor);
        if (fillColor != 0) {
            roundedRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fillColor);
        }
    }

    // ────────────────────────────────────────────────────────
    // Gradients
    // ────────────────────────────────────────────────────────

    /** Gradient vertical natif Minecraft (top → bottom). */
    public static void verticalGradient(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                        int colorTop, int colorBottom) {
        ctx.fillGradient(x, y, x + w, y + h, colorTop, colorBottom);
    }

    /** Gradient horizontal — approximation par bandes verticales 1px. */
    public static void horizontalGradient(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                          int colorLeft, int colorRight) {
        for (int i = 0; i < w; i++) {
            float t = (float) i / Math.max(1, w - 1);
            int c = Colors.lerp(colorLeft, colorRight, t);
            ctx.fill(x + i, y, x + i + 1, y + h, c);
        }
    }

    // ────────────────────────────────────────────────────────
    // Glow / shadow
    // ────────────────────────────────────────────────────────

    /**
     * Lueur autour d'un rectangle — N bandes concentriques de plus en plus
     * transparentes. Utilisé pour le hover des boutons primaires + le halo
     * du LogoSigil.
     */
    public static void glowRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                int glowColor, int radius) {
        for (int i = radius; i >= 1; i--) {
            float t = 1f - (float) i / radius;
            int c = Colors.withAlpha(glowColor, t * (((glowColor >>> 24) & 0xFF) / 255f));
            ctx.fill(x - i, y - i, x + w + i, y + h + i, c);
        }
    }

    /**
     * Drop shadow simple sous un rectangle (3-4 px d'offset vers le bas/droite,
     * dégradé). Utilisé pour les cards / modals.
     */
    public static void dropShadow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius) {
        for (int i = radius; i >= 1; i--) {
            int alpha = Math.round(80f * (1f - (float) i / radius));
            int c = (alpha << 24);
            ctx.fill(x + i, y + h, x + w + i, y + h + i, c);  // bottom
            ctx.fill(x + w, y + i, x + w + i, y + h, c);      // right
        }
    }

    // ────────────────────────────────────────────────────────
    // Cercles et arcs
    // ────────────────────────────────────────────────────────

    /** Disque plein centré sur (cx, cy). */
    public static void disc(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Anneau (cercle non-rempli) d'épaisseur {@code thickness}. */
    public static void ring(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int thickness, int color) {
        int rOut = radius;
        int rIn = Math.max(0, radius - thickness);
        int rOutSq = rOut * rOut;
        int rInSq = rIn * rIn;
        for (int dy = -rOut; dy <= rOut; dy++) {
            for (int dx = -rOut; dx <= rOut; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 <= rOutSq && d2 >= rInSq) {
                    ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }

    /**
     * Anneau pointillé (style LogoSigil — brush stroke effect).
     * {@code dashLengthDeg} et {@code gapLengthDeg} en degrés.
     * {@code rotationDeg} décale la position de départ.
     */
    public static void dashedRing(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int thickness,
                                  int color, float dashLengthDeg, float gapLengthDeg,
                                  float rotationDeg) {
        int rOut = radius;
        int rIn = Math.max(0, radius - thickness);
        int rOutSq = rOut * rOut;
        int rInSq = rIn * rIn;
        float cycle = dashLengthDeg + gapLengthDeg;
        for (int dy = -rOut; dy <= rOut; dy++) {
            for (int dx = -rOut; dx <= rOut; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 <= rOutSq && d2 >= rInSq) {
                    float ang = (float) Math.toDegrees(Math.atan2(dy, dx)) - rotationDeg;
                    while (ang < 0) ang += 360f;
                    float modAng = ang % cycle;
                    if (modAng < dashLengthDeg) {
                        ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // Lignes (utilitaires)
    // ────────────────────────────────────────────────────────

    /**
     * Ligne fine entre 2 points — algorithme de Bresenham. Épaisseur 1px.
     * Utilisé pour les traits du LogoSigil (kanji-like strokes).
     */
    public static void line(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    /** Ligne épaisse — multipasse 1px. Pas anti-aliasée. */
    public static void thickLine(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1,
                                 int thickness, int color) {
        int half = thickness / 2;
        for (int t = -half; t <= half; t++) {
            // Pour épaisseur impaire on dessine sur les axes perpendiculaires.
            line(ctx, x0, y0 + t, x1, y1 + t, color);
            line(ctx, x0 + t, y0, x1 + t, y1, color);
        }
    }
}
