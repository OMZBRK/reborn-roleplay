package fr.reborn.hud.crosshair;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.hud.menu.settings.RebornPrefs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Rendu du viseur Reborn (pilier 2 — cf {@code docs/MOD_HUD_REDESIGN.md}).
 *
 * <p>Les presets sont des PNG 33×33 blancs sur transparent
 * ({@code assets/reborn/textures/gui/crosshairs/<n>.png}) → teintés en code
 * (couleur fixe ou rainbow). {@link InGameHudMixin} appelle {@link #tryRender}
 * au début du rendu du crosshair vanilla : si on prend la main, le mixin
 * annule le crosshair vanilla.
 */
public final class CrosshairManager {

    public static final int PRESET_COUNT = 15;
    /** Taille native des PNG (centrée : 33 impair → pixel central à l'index 16). */
    private static final int TEX = 33;

    private CrosshairManager() {}

    public static Identifier preset(int index) {
        int i = Math.max(0, Math.min(PRESET_COUNT - 1, index)) + 1;
        return Identifier.of("reborn", "textures/gui/crosshairs/" + i + ".png");
    }

    /**
     * Dessine le viseur Reborn s'il est activé et qu'on est en vue première
     * personne. Retourne {@code true} si on a pris la main (le mixin annule
     * alors le crosshair vanilla).
     */
    public static boolean tryRender(DrawContext ctx) {
        RebornPrefs p = RebornPrefs.INSTANCE;
        if (!p.crosshairEnabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || mc.player == null) return false;
        if (!mc.options.getPerspective().isFirstPerson()) return false;

        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        float scale = Math.max(0.5f, Math.min(2.0f, p.crosshairScale / 100f));

        int color = p.crosshairRainbow ? rainbow() : p.crosshairColor;
        float a = ((color >>> 24) & 0xFF) / 255f;
        if (a <= 0f) a = 1f; // alpha non fixé → opaque
        float r = ((color >>> 16) & 0xFF) / 255f;
        float g = ((color >>> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        ctx.drawTexture(preset(p.crosshairPreset), -TEX / 2, -TEX / 2,
            0f, 0f, TEX, TEX, TEX, TEX);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ctx.getMatrices().pop();
        return true;
    }

    /** Couleur arc-en-ciel cyclant (~3s/tour). HSV→RGB sans dépendance AWT. */
    private static int rainbow() {
        float h = (System.currentTimeMillis() % 3000L) / 3000f * 6f;
        int i = (int) h;
        float f = h - i;
        int v = 255;
        int q = (int) (255 * (1 - f));
        int t = (int) (255 * f);
        int r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = 0; }
            case 1 -> { r = q; g = v; b = 0; }
            case 2 -> { r = 0; g = v; b = t; }
            case 3 -> { r = 0; g = q; b = v; }
            case 4 -> { r = t; g = 0; b = v; }
            default -> { r = v; g = 0; b = q; }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
