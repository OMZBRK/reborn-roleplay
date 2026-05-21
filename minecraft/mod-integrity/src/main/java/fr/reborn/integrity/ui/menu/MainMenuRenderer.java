package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.SakuraParticles;
import net.minecraft.client.gui.DrawContext;

/**
 * Orchestrateur du rendu visuel du main menu Reborn — appelle dans
 * l'ordre tous les composants passifs (logo, server info, credits,
 * fond OST). Les composants cliquables (PressSpacePrompt, IconButton
 * du quit / settings / globe / discord / OST controls) sont des
 * {@code ClickableWidget} ajoutés au screen via {@code addDrawableChild}
 * dans le mixin — ils se rendent automatiquement.
 *
 * <p>Référence design : {@code reborn-design-prep/minecraft-main-menu/
 * main-menu.jsx} + screen {@code ref1mainmenu.png}.
 *
 * @see fr.reborn.integrity.mixin.TitleScreenMixin
 */
public final class MainMenuRenderer {

    /** Position X gauche de la card OST. */
    public static int ostCardX(int screenW) {
        return Math.round(20 * responsiveScale(screenW));
    }

    /** Position Y top de la card OST. */
    public static int ostCardY(int screenH) {
        return screenH - OSTPlayerV2.CARD_H - 24;
    }

    /** Position Y du PressSpacePrompt (centré horizontalement). */
    public static int promptY(int screenH) {
        return Math.round(screenH * 0.50f);
    }

    /** Position X centrée du ServerInfoMini (centre-bas du screen). */
    public static int serverInfoCenterX(int screenW) {
        return screenW / 2;
    }

    /** Position Y du ServerInfoMini (centré bas, sous la card OST en Y). */
    public static int serverInfoY(int screenH) {
        return screenH - 50;
    }

    /** Position X right des 3 icons (alignés à droite sous les credits). */
    public static int rightIconsRightEdge(int screenW) {
        return screenW - 18;
    }

    /** Position Y top du groupe d'icons à droite — juste sous les credits. */
    public static int rightIconsY(int screenH) {
        return screenH - 32;
    }

    /** Taille de chaque icon button du groupe droit. */
    public static final int RIGHT_ICON_SIZE = 20;
    /** Espacement horizontal entre les icons. */
    public static final int RIGHT_ICON_SPACING = 6;

    /**
     * Multiplicateur responsive — réduit l'UI sur petites fenêtres.
     * <ul>
     *   <li>≤ 700  → 0.55</li>
     *   <li>≤ 900  → 0.70</li>
     *   <li>≤ 1100 → 0.85</li>
     *   <li>≥ 1100 → 1.00</li>
     * </ul>
     */
    public static float responsiveScale(int screenW) {
        if (screenW <= 700) return 0.55f;
        if (screenW <= 900) return 0.70f;
        if (screenW <= 1100) return 0.85f;
        return 1.0f;
    }

    private MainMenuRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        // 1. BackgroundRenderer en premier — 5 couches procédurales qui
        //    couvrent tout l'écran et masquent intégralement le panorama
        //    vanilla + logo MC + splash + copyright. Plus besoin de
        //    bandeaux gradients pour masquer.
        BackgroundRenderer.render(ctx, screenW, screenH);

        // 2. Sakura particles overlay — par-dessus le background, donne
        //    une couche de mouvement diagonal descendant en contrepoint
        //    des chakra particles qui montent.
        SakuraParticles.INSTANCE.render(ctx, screenW, screenH);

        // 3. Logo central Reborn.
        CentralLogo.render(ctx, screenW, screenH);

        // 5. ServerInfo CENTRÉ en bas (sous le footer).
        ServerInfoMini.render(ctx, serverInfoCenterX(screenW), serverInfoY(screenH));

        // 6. Credits coin bas-droite (3 lignes au-dessus des icons sociaux).
        CreditsCorner.render(ctx, screenW, screenH);

        // 7. OST card background.
        OSTPlayerV2.renderBackground(ctx, ostCardX(screenW), ostCardY(screenH));
    }
}
