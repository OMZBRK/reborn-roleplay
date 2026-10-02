package fr.reborn.hud.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Apparition « nuages qui s'écartent » partagée par les écrans Reborn (carte du monde,
 * fiche shinobi) : un voile qui se dissipe puis une grille de nuages pixel-art qui
 * fuient vers les bords. Textures : {@code textures/gui/map/cloud_*.png}.
 *
 * <p>Usage : {@code intro.draw(ctx, width, height)} en tout dernier dans le rendu ;
 * {@link #progress()} pour synchroniser d'autres animations.
 */
public final class CloudIntro {

    private static final int[][] SIZES = {{68, 34}, {52, 30}, {84, 38}};
    private static final Identifier[] TEX = {
        Identifier.fromNamespaceAndPath("reborn", "textures/gui/map/cloud_0.png"),
        Identifier.fromNamespaceAndPath("reborn", "textures/gui/map/cloud_1.png"),
        Identifier.fromNamespaceAndPath("reborn", "textures/gui/map/cloud_2.png"),
    };

    private record Cloud(float x, float y, float dx, float dy, float scale, int variant, float delay) {}

    private final long startedAt = System.currentTimeMillis();
    private final int durationMs;
    private final int veilRgb;
    private final int cloudTint;
    private List<Cloud> clouds;
    private int builtW = -1, builtH = -1;

    /**
     * @param durationMs durée totale
     * @param veilRgb    couleur du voile initial (RGB, sans alpha)
     * @param cloudTint  teinte ARGB multipliée sur les nuages (0xFFFFFFFF = blancs)
     */
    public CloudIntro(int durationMs, int veilRgb, int cloudTint) {
        this.durationMs = durationMs;
        this.veilRgb = veilRgb & 0xFFFFFF;
        this.cloudTint = cloudTint;
    }

    /** 0 → 1 sur la durée de l'apparition. */
    public float progress() {
        return Math.min(1f, (System.currentTimeMillis() - startedAt) / (float) durationMs);
    }

    public boolean done() { return progress() >= 1f; }

    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        Random r = new Random(0x4E55L);
        List<Cloud> out = new ArrayList<>();
        int cols = 5, rows = 5;
        float cx = w / 2f, cy = h / 2f;
        float maxLen = (float) Math.hypot(cx, cy);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float x = (i + 0.5f + (r.nextFloat() - 0.5f) * 0.6f) * w / cols;
                float y = (j + 0.5f + (r.nextFloat() - 0.5f) * 0.6f) * h / rows;
                float vx = x - cx, vy = y - cy;
                float len = (float) Math.max(1, Math.hypot(vx, vy));
                // Les nuages du centre partent un peu en retard, ceux du bord d'abord.
                float delay = Math.max(0f, 0.18f * (1f - len / maxLen));
                float scale = Math.max(2.2f, h / 80f) * (0.85f + r.nextFloat() * 0.5f);
                out.add(new Cloud(x, y, vx / len, vy / len, scale, r.nextInt(3), delay));
            }
        }
        clouds = out;
    }

    public void draw(GuiGraphicsExtractor ctx, int w, int h) {
        float t = progress();
        if (t >= 1f) return;
        if (clouds == null || builtW != w || builtH != h) build(w, h);
        int veil = (int) (Math.max(0f, 1f - t * 2.2f) * 235);
        if (veil > 0) ctx.fill(0, 0, w, h, (veil << 24) | veilRgb);
        float reach = (float) Math.hypot(w, h) * 0.75f;
        for (Cloud c : clouds) {
            float k = Math.max(0f, Math.min(1f, (t - c.delay()) / (1f - c.delay())));
            float d = k * k * reach;
            int cw = SIZES[c.variant()][0], ch = SIZES[c.variant()][1];
            float x = c.x() + c.dx() * d - cw * c.scale() / 2f;
            float y = c.y() + c.dy() * d - ch * c.scale() / 2f;
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().scale(c.scale(), c.scale());
            ctx.blit(RenderPipelines.GUI_TEXTURED, TEX[c.variant()], 0, 0, 0f, 0f, cw, ch, cw, ch, cloudTint);
            ctx.pose().popMatrix();
        }
    }

    /** Un nuage isolé (décor), teinté. */
    public static void drawCloud(GuiGraphicsExtractor ctx, int variant, float x, float y, float scale, int argb) {
        int cw = SIZES[variant][0], ch = SIZES[variant][1];
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.blit(RenderPipelines.GUI_TEXTURED, TEX[variant], 0, 0, 0f, 0f, cw, ch, cw, ch, argb);
        ctx.pose().popMatrix();
    }

    public static int cloudWidth(int variant) { return SIZES[variant][0]; }
}
