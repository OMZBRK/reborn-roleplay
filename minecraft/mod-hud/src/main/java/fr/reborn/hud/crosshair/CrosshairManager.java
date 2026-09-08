package fr.reborn.hud.crosshair;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.settings.RebornPrefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

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
    /** Durée d'affichage du hit-marker après un coup (ms). */
    private static final long HIT_MS = 250L;

    /** Timestamp du dernier coup porté à une entité (pour le hit-marker). */
    private static volatile long lastHitMs = 0L;

    private CrosshairManager() {}

    /** Appelé par CrosshairHitMixin quand le joueur frappe une entité. */
    public static void onHit() {
        lastHitMs = System.currentTimeMillis();
    }

    public static Identifier preset(int index) {
        int i = Math.max(0, Math.min(PRESET_COUNT - 1, index)) + 1;
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/crosshairs/" + i + ".png");
    }

    /**
     * Dessine le viseur Reborn s'il est activé et qu'on est en vue première
     * personne. Retourne {@code true} si on a pris la main (le mixin annule
     * alors le crosshair vanilla).
     */
    public static boolean tryRender(GuiGraphicsExtractor ctx) {
        RebornPrefs p = RebornPrefs.INSTANCE;
        if (!p.crosshairEnabled) return false;
        // Serveur build / non-Reborn : viseur vanilla (on ne prend pas la main).
        if (!fr.reborn.hud.runtime.RebornSession.rpFeaturesEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.player == null) return false;
        // Viseur affiché en 1re personne ET en vue ÉPAULE Reborn (3e pers. arrière) —
        // pas en 3e pers. AVANT (vue miroir → réticule trompeur).
        net.minecraft.client.CameraType camType = mc.options.getCameraType();
        boolean shoulder = fr.reborn.hud.camera.RebornCamera.INSTANCE.mode()
                == fr.reborn.hud.camera.RebornCamera.Mode.SHOULDER
            && camType == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
        if (!camType.isFirstPerson() && !shoulder) return false;

        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        float scale = Math.max(0.5f, Math.min(2.0f, p.crosshairScale / 100f));

        // Dynamique : module l'échelle avec le cooldown d'attaque — petit juste
        // après un coup, revient à 100% à mesure que l'arme se recharge.
        if (p.crosshairDynamic) {
            float cd = mc.player.getAttackStrengthScale(0f);
            scale *= 0.6f + 0.4f * cd;
        }

        int color = p.crosshairRainbow ? rainbow() : p.crosshairColor;
        drawBody(ctx, cx, cy, scale, color);

        if (p.crosshairHitMarker) {
            drawHitMarker(ctx, cx, cy);
        }
        return true;
    }

    /**
     * Dessine le corps du viseur (preset PNG ou forme procédurale) centré en
     * (cx,cy), mis à l'échelle, teinté en {@code color}.
     */
    private static void drawBody(GuiGraphicsExtractor ctx, int cx, int cy, float scale, int color) {
        RebornPrefs p = RebornPrefs.INSTANCE;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(scale, scale);
        ;
        if ("preset".equals(p.crosshairStyle)) {
            float a = ((color >>> 24) & 0xFF) / 255f;
            if (a <= 0f) a = 1f;
            float r = ((color >>> 16) & 0xFF) / 255f;
            float g = ((color >>> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            ;
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, preset(p.crosshairPreset), -TEX / 2, -TEX / 2,
                0f, 0f, TEX, TEX, TEX, TEX);
            ;
        } else {
            drawProcedural(ctx, p.crosshairStyle,
                p.crosshairGap, p.crosshairLength, p.crosshairThickness, color);
        }
        ctx.pose().popMatrix();
    }

    /** Formes procédurales (croix / point / cercle) dessinées autour de (0,0). */
    private static void drawProcedural(GuiGraphicsExtractor ctx, String style,
                                       int gap, int len, int thick, int color) {
        if (((color >>> 24) & 0xFF) == 0) color |= 0xFF000000; // force opaque si alpha non fixé
        int t = Math.max(1, thick);
        int half = Math.max(1, t / 2);
        switch (style) {
            case "dot" -> {
                int s = Math.max(1, t);
                ctx.fill(-s, -s, s, s, color);
            }
            case "circle" -> DrawHelpers.ring(ctx, 0, 0, Math.max(2, len + gap), t, color);
            default -> { // "cross"
                ctx.fill(-half, -(gap + len), t - half, -gap, color); // haut
                ctx.fill(-half, gap, t - half, gap + len, color);      // bas
                ctx.fill(-(gap + len), -half, -gap, t - half, color);  // gauche
                ctx.fill(gap, -half, gap + len, t - half, color);      // droite
            }
        }
    }

    /**
     * Dessine le viseur courant (preset + couleur/rainbow, échelle ×scaleMul)
     * à un point donné, indépendamment de l'état activé / vue. Pour l'aperçu
     * live dans l'éditeur {@code CrosshairScreen}.
     */
    public static void drawPreview(GuiGraphicsExtractor ctx, int cx, int cy, float scaleMul) {
        RebornPrefs p = RebornPrefs.INSTANCE;
        float scale = Math.max(0.5f, Math.min(2.0f, p.crosshairScale / 100f)) * scaleMul;
        int color = p.crosshairRainbow ? rainbow() : p.crosshairColor;
        drawBody(ctx, cx, cy, scale, color);
    }

    /** Hit-marker (X blanc) qui s'estompe sur {@link #HIT_MS} après un coup. */
    private static void drawHitMarker(GuiGraphicsExtractor ctx, int cx, int cy) {
        long age = System.currentTimeMillis() - lastHitMs;
        if (lastHitMs <= 0L || age > HIT_MS) return;
        float t = 1f - age / (float) HIT_MS; // 1 → 0
        int alpha = Math.max(0, Math.min(255, Math.round(255 * t)));
        int color = (alpha << 24) | 0x00FFFFFF;
        ;
        // 4 branches diagonales avec un gap au centre.
        DrawHelpers.thickLine(ctx, cx - 9, cy - 9, cx - 3, cy - 3, 2, color);
        DrawHelpers.thickLine(ctx, cx + 9, cy - 9, cx + 3, cy - 3, 2, color);
        DrawHelpers.thickLine(ctx, cx - 9, cy + 9, cx - 3, cy + 3, 2, color);
        DrawHelpers.thickLine(ctx, cx + 9, cy + 9, cx + 3, cy + 3, 2, color);
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
