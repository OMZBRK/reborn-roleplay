package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

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
    private static final Component VERSION_TEXT = RebornFont.body(RebornVersion.shortVersion());

    private CreditsCorner() {}

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;

        int scaledW = Math.round(tr.width(VERSION_TEXT) * TEXT_SCALE);
        int x = (screenW - scaledW) / 2;
        int y = screenH - BOTTOM_OFFSET;

        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(TEXT_SCALE, TEXT_SCALE);
        ctx.text(tr, VERSION_TEXT, 0, 0, 0xFFD1D5DB, true);
        ctx.pose().popMatrix();
    }
}
