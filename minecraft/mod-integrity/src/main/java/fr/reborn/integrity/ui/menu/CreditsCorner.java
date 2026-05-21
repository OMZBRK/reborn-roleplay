package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import fr.reborn.integrity.ui.RebornVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Coin "credits" en bas-gauche du main menu — 3 lignes de version /
 * disclaimer / copyright. Toutes en Inter Medium muté.
 *
 * <p>Référence design : {@code main-menu.jsx::CreditsCorner}.
 */
public final class CreditsCorner {

    private static final int LEFT_PADDING = 18;
    private static final int BOTTOM_PADDING = 18;
    private static final int LINE_HEIGHT = 11;
    private static final float TEXT_SCALE = 0.9f;

    private CreditsCorner() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int x = LEFT_PADDING;
        int yBottom = screenH - BOTTOM_PADDING;
        int yTop = yBottom - LINE_HEIGHT * 3;

        drawScaled(ctx, tr, RebornVersion.shortVersion(), x, yTop, Colors.FOREGROUND_SUBTLE);
        drawScaled(ctx, tr, RebornVersion.DISCLAIMER, x, yTop + LINE_HEIGHT, Colors.FOREGROUND_MUTED);
        drawScaled(ctx, tr, RebornVersion.COPYRIGHT, x, yTop + LINE_HEIGHT * 2, Colors.FOREGROUND_MUTED);
    }

    private static void drawScaled(DrawContext ctx, TextRenderer tr, String content,
                                   int x, int y, int color) {
        Text t = RebornFont.body(content);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        ctx.drawText(tr, t, 0, 0, color, false);
        ctx.getMatrices().pop();
    }
}
