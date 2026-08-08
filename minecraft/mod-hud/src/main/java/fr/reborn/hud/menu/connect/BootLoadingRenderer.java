package fr.reborn.hud.menu.connect;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Skin Reborn de l'écran de <b>boot</b> (reload de ressources au démarrage),
 * en mode <b>OVERLAY</b> : dessiné PAR-DESSUS le rendu vanilla de
 * {@code LoadingOverlay} (fond Mojang) sans annuler sa logique (fade,
 * complétion du reload, transition vers le title). On peint un fond noir plein
 * qui masque le logo Mojang, le logo Reborn centré et une barre de progression
 * sobre — même vocabulaire visuel que {@link ConnectingRenderer} (Zenkai).
 */
public final class BootLoadingRenderer {

    /** Même logo que le splash / la connexion (render 3D blocky, transparent). */
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    private static final int TRACK_COLOR = 0xFF1C1F26;   // rail sombre
    private static final int FILL_COLOR  = 0xFFB4232B;   // crimson Reborn

    private BootLoadingRenderer() {}

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH, float progress) {
        // 1. Fond noir plein — masque le logo Mojang vanilla dessous.
        ctx.fill(0, 0, screenW, screenH, 0xFF000000);

        // 2. Logo Reborn centré (~40% hauteur, ratio conservé).
        int destW = Math.min(Math.round(screenW * 0.32f), 440);
        int destH = Math.round(destW * (float) LOGO_TEX_H / LOGO_TEX_W);
        int logoX = (screenW - destW) / 2;
        int logoY = Math.round(screenH * 0.40f) - destH / 2;
        ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0f, 0f,
            destW, destH, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        // 3. Barre de progression sobre sous le logo.
        float p = Math.max(0f, Math.min(1f, progress));
        int barW = Math.min(Math.round(screenW * 0.30f), 360);
        int barH = 4;
        int barX = (screenW - barW) / 2;
        int barY = Math.round(screenH * 0.62f);
        ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, TRACK_COLOR);
        ctx.fill(barX, barY, barX + Math.round(barW * p), barY + barH, FILL_COLOR);
    }
}
