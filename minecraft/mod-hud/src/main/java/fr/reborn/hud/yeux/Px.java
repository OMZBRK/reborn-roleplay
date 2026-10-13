package fr.reborn.hud.yeux;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Dessin au pixel réel de l'écran (pas à la grille GUI) : disques, anneaux, arcs, amandes, traits. La DA refuse les
 * cercles pixelisés en escalier GUI ; ici chaque ligne est remplie au pixel près. Coordonnées en pixels écran, à
 * utiliser entre {@link #begin} et {@link #end}.
 */
final class Px {

    private Px() {}

    static double gs() { return Minecraft.getInstance().getWindow().getGuiScale(); }

    static void begin(GuiGraphicsExtractor g) {
        float k = (float) (1.0 / gs());
        g.pose().pushMatrix();
        g.pose().scale(k, k);
    }

    static void end(GuiGraphicsExtractor g) { g.pose().popMatrix(); }

    static void disc(GuiGraphicsExtractor g, double cx, double cy, double r, int c) {
        if (r <= 0) return;
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        for (int y = y0; y < y1; y++) {
            double dy = y + 0.5 - cy;
            double hw = r * r - dy * dy;
            if (hw <= 0) continue;
            hw = Math.sqrt(hw);
            g.fill((int) Math.round(cx - hw), y, (int) Math.round(cx + hw), y + 1, c);
        }
    }

    /** Anneau d'épaisseur {@code t} (bord extérieur {@code r}). */
    static void ring(GuiGraphicsExtractor g, double cx, double cy, double r, double t, int c) {
        double ri = Math.max(0, r - t);
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        for (int y = y0; y < y1; y++) {
            double dy = y + 0.5 - cy;
            double o = r * r - dy * dy;
            if (o <= 0) continue;
            o = Math.sqrt(o);
            double i = ri * ri - dy * dy;
            if (i <= 0) {
                g.fill((int) Math.round(cx - o), y, (int) Math.round(cx + o), y + 1, c);
            } else {
                i = Math.sqrt(i);
                g.fill((int) Math.round(cx - o), y, (int) Math.round(cx - i), y + 1, c);
                g.fill((int) Math.round(cx + i), y, (int) Math.round(cx + o), y + 1, c);
            }
        }
    }

    /** Arc d'anneau de {@code a0} à {@code a1} (radians, sens horaire écran). */
    static void arc(GuiGraphicsExtractor g, double cx, double cy, double r, double t, double a0, double a1, int c) {
        double len = Math.abs(a1 - a0) * r;
        int steps = Math.max(2, (int) Math.ceil(len / Math.max(1, t * 0.45)));
        for (int k = 0; k <= steps; k++) {
            double a = a0 + (a1 - a0) * k / steps;
            disc(g, cx + Math.cos(a) * (r - t / 2), cy + Math.sin(a) * (r - t / 2), t / 2, c);
        }
    }

    /** Trait épais de (x0, y0) à (x1, y1). */
    static void line(GuiGraphicsExtractor g, double x0, double y0, double x1, double y1, double t, int c) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, (int) Math.ceil(len / Math.max(1, t * 0.45)));
        for (int k = 0; k <= steps; k++) {
            double f = k / (double) steps;
            disc(g, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, t / 2, c);
        }
    }

    /**
     * Demi-largeur de l'amande d'un œil (deux arcs de cercle) à la hauteur {@code dy} du centre ; 0 hors de l'œil.
     * {@code w} demi-largeur, {@code h} demi-hauteur.
     */
    static double almond(double w, double h, double dy) {
        double rl = (w * w + h * h) / (2 * h), c = rl - h;
        double d = Math.abs(dy) + c;
        double v = rl * rl - d * d;
        return v > 0 ? Math.sqrt(v) : 0;
    }

    static void almondFill(GuiGraphicsExtractor g, double cx, double cy, double w, double h, int top, int bottom) {
        int y0 = (int) Math.floor(cy - h), y1 = (int) Math.ceil(cy + h);
        for (int y = y0; y < y1; y++) {
            double dy = y + 0.5 - cy;
            double hw = almond(w, h, dy);
            if (hw <= 0) continue;
            float f = (float) ((dy + h) / (2 * h));
            g.fill((int) Math.round(cx - hw), y, (int) Math.round(cx + hw), y + 1, mix(top, bottom, f));
        }
    }

    /** Disque découpé par l'amande (iris dans l'œil). */
    static void discInAlmond(GuiGraphicsExtractor g, double cx, double cy, double r, double ex, double ey, double w,
                             double h, int c) {
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        for (int y = y0; y < y1; y++) {
            double dy = y + 0.5 - cy;
            double hw = r * r - dy * dy;
            if (hw <= 0) continue;
            hw = Math.sqrt(hw);
            double aw = almond(w, h, y + 0.5 - ey);
            if (aw <= 0) continue;
            int xa = (int) Math.round(Math.max(cx - hw, ex - aw)), xb = (int) Math.round(Math.min(cx + hw, ex + aw));
            if (xb > xa) g.fill(xa, y, xb, y + 1, c);
        }
    }

    static int mix(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * f) << 24 | Math.round(ar + (br - ar) * f) << 16
                | Math.round(ag + (bg - ag) * f) << 8 | Math.round(ab + (bb - ab) * f);
    }

    static int alpha(int argb, float f) {
        int al = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }
}
