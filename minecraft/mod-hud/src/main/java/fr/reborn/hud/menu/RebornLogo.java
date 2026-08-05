package fr.reborn.hud.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Masque le logo Minecraft vanilla et dessine le logo "REBORN" a la place.
 *
 * <p>Strategie PR #1 :
 * <ul>
 *   <li>Couvre uniquement la zone du logo MC (centre haut, ~274×44 px)
 *       avec un rectangle opaque. On laisse vivre le splash text en
 *       jaune qui depasse a droite (le user a explicitement demande
 *       a le garder).</li>
 *   <li>Dessine "REBORN" en gros (font MC par defaut, scale 6x) centre
 *       dans cette meme zone.</li>
 * </ul>
 *
 * <p>Le panorama vanilla qui tourne, la version "Minecraft 1.21.1/Fabric
 * (Modded)" en bas-gauche, le copyright Mojang en bas-droite et le
 * splash text restent intacts.
 *
 * <p>PR ulterieure : remplacer le texte par un PNG transparent du logo
 * Reborn, pour ne plus avoir besoin du masque (le PNG aura ses propres
 * pixels opaques).
 */
public final class RebornLogo {

    private static final String LOGO_TEXT = "REBORN";

    /** Couleur du logo (ARGB) — blanc legerement chaud. */
    private static final int LOGO_COLOR = 0xFFFFFAF0;

    /** Echelle du texte (font MC = 8px, donc 6× = 48px de haut). */
    private static final float LOGO_SCALE = 6.0f;

    /** Couleur du masque qui couvre le logo MC (noir opaque). */
    private static final int MASK_COLOR = 0xFF0A0A0A;

    // Dimensions et position approximatives du logo Minecraft vanilla en
    // MC 1.21.1 — texture 274×44 px centree, y=30. On etend un peu pour
    // securite (anti-aliasing edges).
    private static final int LOGO_MC_WIDTH = 280;
    private static final int LOGO_MC_HEIGHT = 55;
    private static final int LOGO_MC_Y = 25;

    private RebornLogo() {}

    public static void render(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;

        // 1. Masque la zone du logo MC. On ne couvre pas le splash
        //    (qui depasse a droite a 20° de rotation).
        int maskX = (screenWidth - LOGO_MC_WIDTH) / 2;
        context.fill(
            maskX,
            LOGO_MC_Y,
            maskX + LOGO_MC_WIDTH,
            LOGO_MC_Y + LOGO_MC_HEIGHT,
            MASK_COLOR
        );

        // 2. Dessine "REBORN" centre dans le masque.
        Component logo = Component.literal(LOGO_TEXT);
        int textWidth = tr.width(logo);
        int textHeight = tr.lineHeight;
        float cx = screenWidth / 2.0f;
        float cy = LOGO_MC_Y + LOGO_MC_HEIGHT / 2.0f;

        context.pose().pushMatrix();
        context.pose().translate(cx, cy);
        context.pose().scale(LOGO_SCALE, LOGO_SCALE);
        context.text(
            tr,
            logo,
            -textWidth / 2,
            -textHeight / 2,
            LOGO_COLOR,
            true
        );
        context.pose().popMatrix();
    }
}
