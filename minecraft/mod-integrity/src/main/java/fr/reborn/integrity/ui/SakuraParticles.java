package fr.reborn.integrity.ui;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Particules sakura flottantes — animation continue overlay du main menu.
 *
 * <p>Inspiré du composant {@code SakuraParticles} du design system v2.
 * Chaque pétale a une trajectoire indépendante : translation verticale
 * constante + drift horizontal sinusoïdal + rotation continue. Le tout
 * dérivé d'un seed déterministe pour cohérence visuelle entre frames.
 *
 * <p>Rendu PR #1 : losange rose dessiné en primitives (4 triangles).
 * Rendu PR #2+ : texture {@code petal.png} (32×32 transparente) que tu
 * fourniras — il suffira de swap le draw call ici. Marquage TODO ci-dessous.
 *
 * <p>Performance : pour 32 pétales × ~10 fills par pétale = ~320 fills par
 * frame. Acceptable pour title screen (60 fps cible facile).
 */
public final class SakuraParticles {

    public static final SakuraParticles INSTANCE = new SakuraParticles(34);

    private final Petal[] petals;
    private final long bornAtMs;

    private SakuraParticles(int count) {
        Random rng = new Random(0xC1A055E5L); // seed fixe pour reproductibilité
        this.petals = new Petal[count];
        for (int i = 0; i < count; i++) {
            petals[i] = new Petal(rng);
        }
        this.bornAtMs = System.currentTimeMillis();
    }

    /**
     * Appel principal — à invoquer une fois par frame depuis le mixin du
     * title screen. Trace toutes les pétales en cours de chute.
     */
    public void render(DrawContext ctx, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        float globalTime = (now - bornAtMs) / 1000f;
        for (Petal p : petals) {
            p.render(ctx, screenWidth, screenHeight, globalTime);
        }
    }

    /** Une pétale individuelle — paramètres figés au constructeur. */
    private static final class Petal {
        final float startLeftPct;       // 0..1
        final float size;               // 8..16
        final float opacity;            // 0.25..0.75
        final float duration;           // 14..26 s (full top-to-bottom)
        final float delay;              // -26..0 s (négatif = déjà en cours)
        final float drift;              // amplitude px de drift horizontal
        final float driftFrequency;     // Hz approx du drift
        final float spinRatePerSec;     // °/s rotation
        final int color;                // ARGB de la pétale (légère variation de teinte)

        Petal(Random rng) {
            this.startLeftPct = rng.nextFloat();
            this.size = 8f + rng.nextFloat() * 8f;
            this.opacity = 0.25f + rng.nextFloat() * 0.5f;
            this.duration = 14f + rng.nextFloat() * 12f;
            this.delay = -rng.nextFloat() * 26f;
            this.drift = (rng.nextFloat() - 0.5f) * 220f;
            this.driftFrequency = 0.3f + rng.nextFloat() * 0.5f;
            this.spinRatePerSec = 18f + rng.nextFloat() * 27f; // 360-900° / cycle de 20s
            // Légère variation de teinte autour de Colors.PETAL_BASE (hsl(340,78%,78%)).
            float hueShift = rng.nextFloat() * 16f - 4f;
            int base = Colors.PETAL_BASE;
            int r = (base >>> 16) & 0xFF;
            int g = (base >>> 8) & 0xFF;
            int b = base & 0xFF;
            // Petite variation par décalage canal — pas un vrai HSL→RGB mais
            // suffisant pour le bruit visuel.
            r = clamp(r + Math.round(hueShift * 2f), 0, 255);
            g = clamp(g - Math.round(hueShift), 0, 255);
            this.color = (r << 16) | (g << 8) | b;
        }

        void render(DrawContext ctx, int screenW, int screenH, float globalTime) {
            float t = globalTime + delay;
            // Cycle modulo duration → t01 ∈ [0, 1].
            float cycle = ((t % duration) + duration) % duration / duration;

            // Position verticale : du haut (-size) au bas (height + size).
            float y = -size + cycle * (screenH + 2 * size);

            // Position horizontale : startLeft% + sin oscillation × drift.
            float baseX = startLeftPct * screenW;
            float oscX = (float) Math.sin(t * driftFrequency * Math.PI * 2.0) * drift;
            float x = baseX + oscX;

            // Rotation (non utilisée pour le rendu primitif diamond — sera
            // brancher en PR #2 quand on aura le PNG).
            // float rot = t * spinRatePerSec;

            int alphaInt = Math.round(opacity * 255f);
            int color = (alphaInt << 24) | (this.color & 0x00FFFFFF);

            // TODO PR #2 : remplacer par drawTexture("reborn:textures/gui/petal.png")
            // avec rotation matrix push/scale/translate.
            drawDiamond(ctx, x, y, size, color);
        }

        /** Forme losange (4 demi-triangles), substitut au sprite. */
        private static void drawDiamond(DrawContext ctx, float cx, float cy, float size, int color) {
            int halfMax = Math.round(size / 2f);
            for (int dy = -halfMax; dy <= halfMax; dy++) {
                float tDy = 1f - Math.abs(dy) / (float) halfMax;
                int w = Math.max(0, Math.round(tDy * halfMax));
                if (w <= 0) continue;
                int xi = Math.round(cx) - w;
                int yi = Math.round(cy) + dy;
                ctx.fill(xi, yi, xi + 2 * w + 1, yi + 1, color);
            }
        }

        private static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
