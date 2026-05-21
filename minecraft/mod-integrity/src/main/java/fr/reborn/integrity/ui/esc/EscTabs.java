package fr.reborn.integrity.ui.esc;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Rendu de la barre de tabs du ESC menu — texte stylisé avec
 * le LogoSigil au centre. Référence : {@code esc-menu.jsx::EscTabs}.
 *
 * <p>Layout : [Reprendre · Paramètres] · [Sigil] · [Report · Déconnexion]
 *
 * <p>Les hits clicks sont gérés par des widgets séparés dans le mixin
 * — cette classe n'est que pour le rendu passif.
 */
public final class EscTabs {

    private static final Identifier SIGIL =
        Identifier.of("reborn", "textures/gui/logo_sigil.png");
    private static final int SIGIL_NATIVE = 256;
    private static final int SIGIL_DISPLAY = 36;

    public static final int TAB_W = 110;
    public static final int TAB_H = 32;
    private static final int TAB_GAP = 14;
    private static final int SIDE_GAP = 30;

    private EscTabs() {}

    public static void renderPassive(DrawContext ctx, int screenW, int barY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        // Sigil au centre.
        int sigilX = (screenW - SIGIL_DISPLAY) / 2;
        int sigilY = barY + (TAB_H - SIGIL_DISPLAY) / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sigilX, sigilY, 0);
        float scale = (float) SIGIL_DISPLAY / SIGIL_NATIVE;
        ctx.getMatrices().scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(SIGIL, 0, 0, 0f, 0f, SIGIL_NATIVE, SIGIL_NATIVE,
            SIGIL_NATIVE, SIGIL_NATIVE);
        ctx.getMatrices().pop();

        // Séparateurs · à gauche et à droite du sigil.
        int sepLeftX = sigilX - SIDE_GAP / 2;
        int sepRightX = sigilX + SIDE_GAP / 2 + SIGIL_DISPLAY;
        int sepY = barY + (TAB_H - tr.fontHeight) / 2;
        ctx.drawText(tr, RebornFont.body("·"), sepLeftX, sepY, Colors.FOREGROUND_MUTED, false);
        ctx.drawText(tr, RebornFont.body("·"), sepRightX, sepY, Colors.FOREGROUND_MUTED, false);
    }

    /** Calcule la position X du tab d'index (0 ou 1 = gauche du sigil, 2 ou 3 = droite). */
    public static int tabX(int screenW, int index) {
        int sigilX = (screenW - SIGIL_DISPLAY) / 2;
        switch (index) {
            case 0: return sigilX - SIDE_GAP - 2 * TAB_W - TAB_GAP;
            case 1: return sigilX - SIDE_GAP - TAB_W;
            case 2: return sigilX + SIGIL_DISPLAY + SIDE_GAP;
            case 3: return sigilX + SIGIL_DISPLAY + SIDE_GAP + TAB_W + TAB_GAP;
            default: return 0;
        }
    }

    public static Text tabLabel(int index) {
        switch (index) {
            case 0: return RebornFont.bold("REPRENDRE");
            case 1: return RebornFont.bold("PARAMÈTRES");
            case 2: return RebornFont.bold("REPORT");
            case 3: return RebornFont.bold("DÉCONNEXION");
            default: return RebornFont.body("");
        }
    }
}
