package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import fr.reborn.integrity.ui.RebornVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Coin "credits" en bas-DROITE du main menu — 3 lignes de version /
 * disclaimer / copyright, alignées à droite. Toutes en Inter Medium muté.
 *
 * <p>Référence design : {@code ref1mainmenu.png}.
 */
public final class CreditsCorner {

    /** Marge depuis le bord droit en pixels. */
    private static final int RIGHT_PADDING = 18;
    /** Distance depuis le bas — juste au-dessus des 3 icons sociaux. */
    private static final int BOTTOM_OFFSET = 38;
    private static final int LINE_HEIGHT = 10;
    private static final float TEXT_SCALE = 0.85f;

    private CreditsCorner() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int rightEdge = screenW - RIGHT_PADDING;
        int yBottom = screenH - BOTTOM_OFFSET;
        int yTop = yBottom - LINE_HEIGHT * 3;

        drawRightAligned(ctx, tr, RebornVersion.shortVersion(), rightEdge, yTop, Colors.FOREGROUND_SUBTLE);
        drawRightAligned(ctx, tr, RebornVersion.DISCLAIMER, rightEdge, yTop + LINE_HEIGHT, Colors.FOREGROUND_MUTED);
        drawRightAligned(ctx, tr, RebornVersion.COPYRIGHT, rightEdge, yTop + LINE_HEIGHT * 2, Colors.FOREGROUND_MUTED);
    }

    private static void drawRightAligned(DrawContext ctx, TextRenderer tr, String content,
                                         int rightEdge, int y, int color) {
        Text t = RebornFont.body(content);
        int scaledW = Math.round(tr.getWidth(t) * TEXT_SCALE);
        int x = rightEdge - scaledW;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        ctx.drawText(tr, t, 0, 0, color, false);
        ctx.getMatrices().pop();
    }
}
