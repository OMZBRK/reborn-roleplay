package fr.reborn.hud.byakugan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Voile discret du Byakugan : quelques veines pâles qui partent des tempes et un léger voile froid sur les bords,
 * qui « respirent » lentement. Le centre de l'écran n'est pas touché. Une seule image plein écran, étirée.
 */
public final class ByakuganVeil {

    private static final Identifier VOILE = Identifier.fromNamespaceAndPath("reborn-hud", "textures/gui/byakugan_voile.png");
    private static final int TEX_W = 512, TEX_H = 288;

    private ByakuganVeil() {}

    public static void render(GuiGraphicsExtractor ctx) {
        float ouverture = ByakuganClient.ouverture();
        if (ouverture <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double t = System.currentTimeMillis() / 1000.0;
        float respire = 0.82f + 0.18f * (float) Math.sin(t * Math.PI * 2 / 5.0);
        int alpha = Math.round(255 * ouverture * respire);
        int color = (alpha << 24) | 0xFFFFFF;
        ctx.blit(RenderPipelines.GUI_TEXTURED, VOILE, 0, 0, 0f, 0f, w, h, TEX_W, TEX_H, TEX_W, TEX_H, color);
    }
}
