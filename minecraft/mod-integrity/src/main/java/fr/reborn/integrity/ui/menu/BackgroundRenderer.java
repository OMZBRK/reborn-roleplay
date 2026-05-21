package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import net.minecraft.client.gui.DrawContext;

/**
 * Background procédural Reborn — remplace le panorama 360° par 5 couches
 * superposées qui forment une ambiance bleu nuit pulsante :
 *
 * <ol>
 *   <li>Dégradé radial bleu nuit (statique) — base.</li>
 *   <li>Pulse accent #3b5bdb derrière le logo (animation respiration).</li>
 *   <li>Grain / noise overlay (3% opacity uni en attendant noise.png).</li>
 *   <li>Vignettes aux 4 coins (focus visuel central).</li>
 *   <li>Chakra particles ascendantes — délégué à
 *       {@link ChakraParticles}.</li>
 * </ol>
 *
 * <p>Cohérent avec l'accent Zenkai blue, zéro asset à charger (sauf le
 * noise.png futur), light side performance car les couches sont fixes
 * ou animées par calcul simple.
 */
public final class BackgroundRenderer {

    // ─── Couleurs du dégradé radial (couche 1) ───
    private static final int CENTER_COLOR = 0xFF1E2A52;   // #1e2a52 bleu nuit profond
    private static final int MID_COLOR    = 0xFF0E1428;   // #0e1428 bleu très sombre
    private static final int OUTER_COLOR  = 0xFF040611;   // #040611 presque noir teinté bleu

    private static final int PULSE_COLOR_BASE = Colors.ACCENT; // #3b5bdb

    /** Timestamp de référence pour les animations (figé au boot). */
    private static final long BORN_AT_MS = System.currentTimeMillis();

    private BackgroundRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        renderRadialGradient(ctx, screenW, screenH);
        renderPulse(ctx, screenW, screenH);
        renderNoise(ctx, screenW, screenH);
        renderVignettes(ctx, screenW, screenH);
        ChakraParticles.INSTANCE.render(ctx, screenW, screenH);
    }

    // ─────────────────────────────────────────────────────────
    // Couche 1 — Dégradé radial bleu nuit
    // ─────────────────────────────────────────────────────────

    /**
     * Approximation d'un dégradé radial centré sur (screenW/2, screenH * 0.35).
     * Implémentation simplifiée : on couvre l'écran de la couleur outer,
     * puis on superpose 2 dégradés (vertical + horizontal) qui s'estompent
     * pour simuler l'éclat radial. Bien moins coûteux que des cercles
     * concentriques pixel par pixel.
     */
    private static void renderRadialGradient(DrawContext ctx, int screenW, int screenH) {
        // Base outer color full screen.
        ctx.fill(0, 0, screenW, screenH, OUTER_COLOR);

        // Dégradé vertical : centre vers haut + bas.
        int centerY = Math.round(screenH * 0.35f);
        int topHalf = Math.max(120, centerY);
        int botHalf = Math.max(120, screenH - centerY);

        // Bande haut : centre -> outer.
        ctx.fillGradient(0, centerY - topHalf, screenW, centerY,
            Colors.withAlpha(OUTER_COLOR, 0.0f),
            Colors.withAlpha(MID_COLOR, 1.0f));
        ctx.fillGradient(0, centerY - topHalf / 2, screenW, centerY,
            Colors.withAlpha(MID_COLOR, 0.0f),
            Colors.withAlpha(CENTER_COLOR, 1.0f));

        // Bande bas : centre -> outer (symétrique).
        ctx.fillGradient(0, centerY, screenW, centerY + botHalf,
            Colors.withAlpha(CENTER_COLOR, 1.0f),
            Colors.withAlpha(OUTER_COLOR, 0.0f));

        // Atténuation horizontale aux bords pour suggérer le radial.
        int sideW = screenW / 4;
        ctx.fillGradient(0, 0, sideW, screenH,
            Colors.withAlpha(OUTER_COLOR, 0.6f),
            Colors.withAlpha(OUTER_COLOR, 0.0f));
        // Variante miroir à droite — pas de horizontal gradient natif MC,
        // donc on dessine une bande discrète multi-couches.
        for (int i = 0; i < 8; i++) {
            int x0 = screenW - sideW + i * (sideW / 8);
            int x1 = screenW - sideW + (i + 1) * (sideW / 8);
            float alpha = 0.6f * i / 8f;
            ctx.fill(x0, 0, x1, screenH, Colors.withAlpha(OUTER_COLOR, alpha));
        }
    }

    // ─────────────────────────────────────────────────────────
    // Couche 2 — Pulse accent derrière le logo
    // ─────────────────────────────────────────────────────────

    private static void renderPulse(DrawContext ctx, int screenW, int screenH) {
        float t = (System.currentTimeMillis() - BORN_AT_MS) / 1000f;
        // Cycle 6s ease-in-out sinusoïdal.
        float phase = (float) Math.sin(t * Math.PI * 2.0 / 6.0);
        float scale = 1.0f + phase * 0.05f;            // 0.95 .. 1.05
        float opacity = 0.20f + phase * 0.05f;          // 0.15 .. 0.25

        int cx = screenW / 2;
        int cy = Math.round(screenH * 0.45f);
        int baseR = Math.round(Math.min(screenW, screenH) * 0.35f);
        int finalR = Math.round(baseR * scale);

        // Multi-cercles concentriques d'opacity décroissante = simu blur.
        int rings = 8;
        for (int i = rings; i >= 0; i--) {
            float t2 = (float) i / rings;
            int rr = Math.round(finalR * (1.0f + t2 * 0.4f));
            float aalpha = opacity * (1.0f - t2) * 0.5f;
            DrawHelpers.disc(ctx, cx, cy, rr,
                Colors.withAlpha(PULSE_COLOR_BASE, aalpha));
        }
    }

    // ─────────────────────────────────────────────────────────
    // Couche 3 — Grain / noise overlay
    // ─────────────────────────────────────────────────────────

    private static void renderNoise(DrawContext ctx, int screenW, int screenH) {
        // En attendant assets/reborn/textures/gui/title/noise.png, on pose
        // une couche unie sombre 3% pour briser le côté flat du dégradé.
        ctx.fill(0, 0, screenW, screenH, Colors.withAlpha(0xFF000000, 0.03f));
        // TODO PR #5.1 : remplacer par drawTexture tile de noise.png blend
        //   multiply ~6% pour vrai effet grain cinéma.
    }

    // ─────────────────────────────────────────────────────────
    // Couche 4 — Vignettes aux 4 coins
    // ─────────────────────────────────────────────────────────

    private static void renderVignettes(DrawContext ctx, int screenW, int screenH) {
        // Diagonale de l'écran.
        int diag = (int) Math.sqrt(screenW * screenW + screenH * screenH);
        int vignetteR = (int) (diag * 0.45f);

        renderCornerVignette(ctx, 0, 0, vignetteR, screenW, screenH);                // top-left
        renderCornerVignette(ctx, screenW, 0, vignetteR, screenW, screenH);          // top-right
        renderCornerVignette(ctx, 0, screenH, vignetteR, screenW, screenH);          // bot-left
        renderCornerVignette(ctx, screenW, screenH, vignetteR, screenW, screenH);    // bot-right
    }

    /**
     * Dessine un dégradé radial fade sombre depuis (cornerX, cornerY) vers
     * l'intérieur. Approximation par 10 disques concentriques d'opacity
     * croissante au plus près du coin.
     */
    private static void renderCornerVignette(DrawContext ctx, int cornerX, int cornerY,
                                              int radius, int screenW, int screenH) {
        int rings = 10;
        // Du grand vers le petit pour empilement correct.
        for (int i = rings; i >= 0; i--) {
            float t = (float) i / rings;
            int r = Math.round(radius * (1.0f - t * 0.8f));
            // Plus on est proche du coin (t -> 0), plus opacity est élevée.
            float alpha = (1.0f - t) * 0.6f;
            DrawHelpers.disc(ctx, cornerX, cornerY, r,
                Colors.withAlpha(0xFF000000, alpha));
        }
    }
}
