package fr.reborn.hud.menu.character;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Petits utilitaires UI partagés par les écrans du créateur/sélecteur de perso.
 * Notamment le <b>logo serveur</b> ({@code character/ui/logo.png}, emblème 3:2 à fond
 * transparent) blitté à la place du texte « REBORN ».
 */
public final class CreatorUi {

    private CreatorUi() {}

    private static final Identifier LOGO =
        Identifier.fromNamespaceAndPath("reborn", "textures/character/ui/logo.png");
    private static final int TW = 384, TH = 256; // dimensions natives du PNG

    private static Boolean logoOk;

    /** Le logo est-il livré dans le resource pack du mod ? (mémoïsé) */
    public static boolean logoExists() {
        if (logoOk != null) return logoOk;
        Minecraft mc = Minecraft.getInstance();
        logoOk = mc != null && mc.getResourceManager().getResource(LOGO).isPresent();
        return logoOk;
    }

    /** Blitte le logo (ratio 3:2 conservé) dans un rectangle {@code w×h}. */
    public static void blitLogo(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0f, 0f, w, h, TW, TH, TW, TH);
    }
}
