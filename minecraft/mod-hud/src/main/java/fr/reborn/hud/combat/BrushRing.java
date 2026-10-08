package fr.reborn.hud.combat;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracé au pinceau en anneau, rendu en sous-pixels (×3) pour un trait fin quelle que soit l'échelle de l'interface.
 *
 * {@link #enso} : le cercle zen de l'endurance. Le trait commence en haut, s'épaissit puis s'effile, et laisse une
 * ouverture ; il se remplit d'or selon l'endurance, la part qui vient d'être dépensée reste en laque un instant.
 */
public final class BrushRing {

    private static final int SS = 3;
    private static final float START = 18f, SWEEP = 316f;

    /** Par rayon : progression le long du trait (0..1) de chaque pixel, NaN hors du trait. */
    private static final Map<Integer, float[]> ENSO = new ConcurrentHashMap<>();

    private BrushRing() {}

    /**
     * @param frac  part d'endurance restante (0..1)
     * @param trail part juste dépensée (affichée en laque), {@code >= frac}
     * @param fill  couleur de l'endurance ; {@code trailColor} celle de la dépense ; {@code base} celle du trait vide
     */
    public static void enso(GuiGraphicsExtractor ctx, int cx, int cy, int radius, float frac, float trail,
                            int fill, int trailColor, int base) {
        int R = radius * SS;
        int half = R + 6;
        int span = 2 * half + 1;
        float[] lut = ENSO.computeIfAbsent(R, BrushRing::buildEnso);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(1f / SS, 1f / SS);
        for (int dy = -half; dy <= half; dy++) {
            int row = (dy + half) * span;
            int runStart = 0, runColor = 0;
            boolean inRun = false;
            for (int dx = -half; dx <= half + 1; dx++) {
                int color = 0;
                if (dx <= half) {
                    float u = lut[row + dx + half];
                    if (!Float.isNaN(u)) color = u <= frac ? fill : u <= trail ? trailColor : base;
                }
                if (inRun && color != runColor) {
                    if (runColor != 0) ctx.fill(runStart, dy, dx, dy + 1, runColor);
                    inRun = false;
                }
                if (!inRun && color != 0) {
                    inRun = true;
                    runStart = dx;
                    runColor = color;
                }
            }
        }
        ctx.pose().popMatrix();
    }

    private static float[] buildEnso(int R) {
        int half = R + 6;
        int span = 2 * half + 1;
        float[] out = new float[span * span];
        float base = Math.max(2f, R * 0.11f);
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                int idx = (dy + half) * span + dx + half;
                out[idx] = Float.NaN;
                float ang = (float) Math.toDegrees(Math.atan2(dx, -dy));
                if (ang < 0) ang += 360f;
                float rel = ang - START;
                if (rel < 0) rel += 360f;
                if (rel > SWEEP) continue;
                float u = rel / SWEEP;
                // pression du pinceau : attaque franche, ventre épais, queue effilée
                float press = (float) Math.pow(Math.sin(Math.PI * Math.min(1.0, u * 1.06 + 0.04)), 0.6) * (1f - 0.45f * u);
                float thick = base * (0.35f + 0.9f * press) * (1f + 0.08f * (float) Math.sin(u * 23));
                // le trait dérive légèrement vers l'intérieur en fin de course
                float rr = R - u * R * 0.06f;
                float d = (float) Math.sqrt(dx * dx + dy * dy) - rr;
                if (Math.abs(d) > thick / 2f) continue;
                // pinceau sec sur la queue : stries
                if (u > 0.72f) {
                    int streak = (int) Math.floor((d + thick) * 1.7f);
                    int h = (streak * 73856093) ^ (int) (u * 40) * 19349663;
                    if (((h >>> 3) & 7) < (int) ((u - 0.72f) * 22)) continue;
                }
                out[idx] = u;
            }
        }
        return out;
    }
}
