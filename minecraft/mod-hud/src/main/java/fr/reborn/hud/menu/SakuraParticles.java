package fr.reborn.hud.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * Particules sakura flottantes — animation continue overlay du main menu.
 *
 * <p>Inspiré du composant {@code SakuraParticles} du design system v2.
 * Chaque pétale a une trajectoire indépendante : translation verticale
 * constante + drift horizontal sinusoïdal + rotation continue. Le tout
 * dérivé d'un seed déterministe pour cohérence visuelle entre frames.
 *
 * <p>Texture {@code petal.png} (32×32 transparente) dessinée via
 * {@code GuiGraphicsExtractor.blit} avec push matrix rotate + scale (aucune
 * teinte : le PNG est rendu tel quel, pas d'atténuation d'opacité).
 */
public final class SakuraParticles {

    public static final SakuraParticles INSTANCE = new SakuraParticles(8);

    private static final Identifier PETAL_TEXTURE =
        Identifier.fromNamespaceAndPath("reborn", "textures/gui/petal.png");

    private final Petal[] petals;
    private final long bornAtMs;

    private SakuraParticles(int count) {
        Random rng = new Random(0xC1A055E5L);
        this.petals = new Petal[count];
        for (int i = 0; i < count; i++) {
            petals[i] = new Petal(rng);
        }
        this.bornAtMs = System.currentTimeMillis();
    }

    public void extractRenderState(GuiGraphicsExtractor ctx, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        float globalTime = (now - bornAtMs) / 1000f;
        for (Petal p : petals) {
            p.render(ctx, screenWidth, screenHeight, globalTime);
        }
    }

    private static final class Petal {
        final float startLeftPct;
        final float size;
        final float duration;
        final float delay;
        final float drift;
        final float driftFrequency;
        final float spinRatePerSec;

        Petal(Random rng) {
            this.startLeftPct = rng.nextFloat();
            this.size = 12f + rng.nextFloat() * 12f;
            rng.nextFloat();   // ancien tirage d'opacity (non utilisé) : conservé pour ne pas
                               // décaler la séquence RNG déterministe → layout des pétales inchangé.
            this.duration = 14f + rng.nextFloat() * 12f;
            this.delay = -rng.nextFloat() * 26f;
            this.drift = (rng.nextFloat() - 0.5f) * 220f;
            this.driftFrequency = 0.3f + rng.nextFloat() * 0.5f;
            this.spinRatePerSec = 18f + rng.nextFloat() * 27f;
        }

        void render(GuiGraphicsExtractor ctx, int screenW, int screenH, float globalTime) {
            float t = globalTime + delay;
            float cycle = ((t % duration) + duration) % duration / duration;

            float y = -size + cycle * (screenH + 2 * size);
            float baseX = startLeftPct * screenW;
            float oscX = (float) Math.sin(t * driftFrequency * Math.PI * 2.0) * drift;
            float x = baseX + oscX;
            float rot = (t * spinRatePerSec) % 360f;

            // Push matrix : translate au centre de la pétale, rotate, scale,
            // puis on dessine la texture 32×32 centrée à l'origine.
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().rotate((float) Math.toRadians(rot)); // Matrix3x2fStack 2D : rotation en radians
            float scale = size / 32f;
            ctx.pose().scale(scale, scale);

            // PNG blanc-rose dessiné tel quel (pas de teinte/opacité shader).
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PETAL_TEXTURE, -16, -16, 0f, 0f, 32, 32, 32, 32);

            ctx.pose().popMatrix();
        }
    }
}
