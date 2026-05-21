package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Particules chakra ascendantes — 10 points lumineux bleus qui montent
 * du bas vers le haut de l'écran avec un léger drift horizontal
 * sinusoïdal. Couche 5 du BackgroundRenderer.
 *
 * <p>Effet : impression d'esprits qui s'élèvent de la terre vers le ciel,
 * subtil mais évocateur du gameplay shinobi sans être littéral.
 *
 * <p>Symétrique de {@link fr.reborn.integrity.ui.SakuraParticles}
 * (qui descend en pétales sakura) — l'un compense l'autre visuellement.
 */
public final class ChakraParticles {

    public static final ChakraParticles INSTANCE = new ChakraParticles(10);

    private final Chakra[] particles;
    private final long bornAtMs;

    private ChakraParticles(int count) {
        Random rng = new Random(0xC4A4734AL);
        this.particles = new Chakra[count];
        for (int i = 0; i < count; i++) {
            particles[i] = new Chakra(rng);
        }
        this.bornAtMs = System.currentTimeMillis();
    }

    public void render(DrawContext ctx, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        float globalTime = (now - bornAtMs) / 1000f;
        for (Chakra p : particles) {
            p.render(ctx, screenWidth, screenHeight, globalTime);
        }
    }

    /** Une particule chakra individuelle. */
    private static final class Chakra {
        final float startLeftPct;     // 0..1
        final float coreSize;         // 3..5 px
        final float glowRadius;       // 10..15 px
        final float duration;         // 15..20 s
        final float delay;             // -20..0 s
        final float drift;             // amplitude px (±20)
        final float driftFreq;         // Hz

        Chakra(Random rng) {
            this.startLeftPct = rng.nextFloat();
            this.coreSize = 3f + rng.nextFloat() * 2f;
            this.glowRadius = 10f + rng.nextFloat() * 5f;
            this.duration = 15f + rng.nextFloat() * 5f;
            this.delay = -rng.nextFloat() * 20f;
            this.drift = (rng.nextFloat() - 0.5f) * 40f;
            this.driftFreq = 0.2f + rng.nextFloat() * 0.4f;
        }

        void render(DrawContext ctx, int screenW, int screenH, float globalTime) {
            float t = globalTime + delay;
            float cycle = ((t % duration) + duration) % duration / duration;

            // Trajectoire : screen-bot+30 -> screen-top-30 (ascendant).
            float y = (screenH + 30) - cycle * (screenH + 60);

            // Drift X sinusoïdal autour de startLeftPct.
            float baseX = startLeftPct * screenW;
            float oscX = (float) Math.sin(t * driftFreq * Math.PI * 2.0) * drift;
            float x = baseX + oscX;

            // Fade in 0..0.2, hold 0.2..0.8, fade out 0.8..1.0.
            float opacity;
            if (cycle < 0.2f) {
                opacity = (cycle / 0.2f) * 0.6f;
            } else if (cycle > 0.8f) {
                opacity = ((1.0f - cycle) / 0.2f) * 0.6f;
            } else {
                opacity = 0.6f;
            }

            // Glow flou autour (3 cercles concentriques).
            int glowR = Math.round(glowRadius);
            DrawHelpers.disc(ctx, Math.round(x), Math.round(y), glowR,
                Colors.withAlpha(Colors.ACCENT, opacity * 0.10f));
            DrawHelpers.disc(ctx, Math.round(x), Math.round(y), glowR / 2,
                Colors.withAlpha(Colors.ACCENT, opacity * 0.20f));

            // Core lumineux.
            int coreR = Math.round(coreSize / 2);
            DrawHelpers.disc(ctx, Math.round(x), Math.round(y), coreR,
                Colors.withAlpha(0xFFFFFFFF, opacity));
            DrawHelpers.disc(ctx, Math.round(x), Math.round(y), coreR - 1,
                Colors.withAlpha(0xFFFFFFFF, opacity * 0.8f));
        }
    }
}
