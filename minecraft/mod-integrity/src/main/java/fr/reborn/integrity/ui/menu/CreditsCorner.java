package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.RebornFont;
import fr.reborn.integrity.ui.RebornVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Ligne credits CENTRÉE sous le groupe d'icons (settings/globe/discord)
 * lui-même sous le ServerInfoMini. Pile centre-bas du main menu.
 *
 * <p>Référence : {@code reborn-design-prep/reference-screen/mainmenu.png}
 * — une seule ligne courte "Minecraft 1.21.1 · Fabric Loader 0.16.5" en
 * gris clair avec drop shadow pour lisibilité sur les BG MCEF.
 */
public final class CreditsCorner {

    /** Distance depuis le bottom-edge — sous les icons (qui vivent à
     *  screenH-48 → bottom screenH-28). 12px de gap avant la baseline. */
    private static final int BOTTOM_OFFSET = 16;
    private static final float TEXT_SCALE = 0.85f;

    /** Cache : RebornVersion.shortVersion() ne change pas en runtime. */
    private static final Text VERSION_TEXT = RebornFont.body(RebornVersion.shortVersion());

    private CreditsCorner() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int scaledW = Math.round(tr.getWidth(VERSION_TEXT) * TEXT_SCALE);
        int x = (screenW - scaledW) / 2;
        int y = screenH - BOTTOM_OFFSET;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        ctx.drawText(tr, VERSION_TEXT, 0, 0, 0xFFD1D5DB, true);
        ctx.getMatrices().pop();
    }
}
