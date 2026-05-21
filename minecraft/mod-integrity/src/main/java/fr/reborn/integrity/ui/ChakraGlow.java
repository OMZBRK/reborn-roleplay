package fr.reborn.integrity.ui;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Couche d'overlay "chakra" — petits points lumineux bleus qui flottent
 * lentement vers le haut, en contre-point des sakura particles qui
 * tombent vers le bas. Donne une ambiance énergétique sans bruit visuel.
 *
 * <p>Contrairement à l'ancienne {@code ChakraParticles} (supprimée),
 * cette version est volontairement minimaliste : peu de particules, pas
 * de trail trop voyante, alpha doux. Optimisé pour ne pas faire lagger
 * le main menu (~50 fill calls par frame, négligeable).
 */
public final class ChakraGlow {

    public static final ChakraGlow INSTANCE = new ChakraGlow(12);

    private final GlowDot[] dots;
    private final long bornAtMs;

    private ChakraGlow(int count) {
        Random rng = new Random(0xCAFE0042L);
        this.dots = new GlowDot[count];
        for (int i = 0; i < count; i++) {
            dots[i] = new GlowDot(rng);
        }
        this.bornAtMs = System.currentTimeMillis();
    }

    public void render(DrawContext ctx, int screenW, int screenH) {
        long now = System.currentTimeMillis();
        float globalTime = (now - bornAtMs) / 1000f;
        for (GlowDot d : dots) {
            d.render(ctx, screenW, screenH, globalTime);
        }
    }

    private static final class GlowDot {
        final float startLeftPct;
        /** Rayon du cœur du dot (1-2 px). */
        final int coreRadius;
        /** Opacité de base 0..1. */
        final float baseAlpha;
        /** Durée d'un cycle bottom→top en secondes (12-22s). */
        final float duration;
        /** Décalage temporel pour désynchroniser les dots. */
        final float delay;
        /** Amplitude du drift sinusoïdal horizontal. */
        final float drift;
        final float driftFrequency;
        /** Hue : 0 = accent bleu, 1 = blanc pur. Mix variable. */
        final float hueMix;

        GlowDot(Random rng) {
            this.startLeftPct = rng.nextFloat();
            this.coreRadius = rng.nextBoolean() ? 1 : 2;
            this.baseAlpha = 0.25f + rng.nextFloat() * 0.45f;
            this.duration = 14f + rng.nextFloat() * 8f;
            this.delay = -rng.nextFloat() * 20f;
            this.drift = (rng.nextFloat() - 0.5f) * 90f;
            this.driftFrequency = 0.15f + rng.nextFloat() * 0.25f;
            this.hueMix = rng.nextFloat() * 0.4f; // surtout bleu, parfois un peu blanc
        }

        void render(DrawContext ctx, int screenW, int screenH, float globalTime) {
            float t = globalTime + delay;
            float cycle = ((t % duration) + duration) % duration / duration;

            // Position : 0 = bas de l'écran, 1 = haut. Monte donc en réverse.
            float yProgress = 1f - cycle;
            float y = -coreRadius * 2f + yProgress * (screenH + coreRadius * 4f);
            float baseX = startLeftPct * screenW;
            float oscX = (float) Math.sin(t * driftFrequency * Math.PI * 2.0) * drift;
            float x = baseX + oscX;

            // Fade in/out aux extrémités pour éviter les "pop" abrupts.
            float fadeAlpha;
            if (cycle < 0.10f)      fadeAlpha = cycle / 0.10f;       // fade in
            else if (cycle > 0.90f) fadeAlpha = (1f - cycle) / 0.10f; // fade out
            else                    fadeAlpha = 1f;

            // Pulse propre du dot — sin slow.
            float pulse = 0.7f + 0.3f * (float) Math.sin(t * 1.5f + startLeftPct * 6f);

            float alpha = baseAlpha * fadeAlpha * pulse;
            if (alpha < 0.02f) return;

            // Couleur lerp ACCENT (#3B5BDB) → WHITE.
            int baseColor = lerpColor(Colors.ACCENT_HOVER, Colors.WHITE_PURE, hueMix);

            int cx = Math.round(x);
            int cy = Math.round(y);

            // Halo extérieur (3 layers fading).
            int outerR = coreRadius + 4;
            for (int r = outerR; r >= coreRadius + 1; r--) {
                float layerT = (outerR - r) / (float) (outerR - coreRadius);
                int layerAlpha = Math.round(alpha * layerT * layerT * 90);
                int color = (layerAlpha << 24) | (baseColor & 0x00FFFFFF);
                drawDisc(ctx, cx, cy, r, color);
            }

            // Cœur lumineux.
            int coreAlpha = Math.round(alpha * 220);
            int core = (coreAlpha << 24) | (baseColor & 0x00FFFFFF);
            drawDisc(ctx, cx, cy, coreRadius, core);

            // Cœur blanc surlignant le centre pour un effet "pixel hot".
            if (coreRadius >= 2) {
                int hotAlpha = Math.round(alpha * 255);
                int hot = (hotAlpha << 24) | 0x00FFFFFF;
                ctx.fill(cx, cy, cx + 1, cy + 1, hot);
            }
        }
    }

    private static void drawDisc(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b2 = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b2;
    }
}
