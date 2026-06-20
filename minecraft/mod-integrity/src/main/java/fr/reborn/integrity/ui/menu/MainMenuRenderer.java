package fr.reborn.integrity.ui.menu;

import net.minecraft.client.gui.DrawContext;

/**
 * Orchestrateur du rendu visuel du main menu Reborn — appelle dans
 * l'ordre tous les composants passifs (BG MCEF, ServerInfoMini,
 * CreditsCorner, OST card background). Les composants cliquables
 * (PressSpacePrompt, IconButton du quit / settings / globe / discord /
 * OST controls) sont des {@code ClickableWidget} ajoutés au screen via
 * {@code addDrawableChild} dans le mixin — ils se rendent automatiquement.
 *
 * <p>Layout (référence {@code reborn-design-prep/reference-screen/
 * mainmenu.png}) :
 * <ul>
 *   <li>BG plein écran : Dynamic Animated Player 3D via MCEF browser</li>
 *   <li>Centre-haut : PressSpacePrompt (CTA primaire)</li>
 *   <li>Centre-bas stack : ServerInfoMini → 3 icons → CreditsCorner</li>
 *   <li>Bottom-right : OSTPlayerV2 card</li>
 *   <li>Top-right : X close (Quitter Reborn)</li>
 * </ul>
 *
 * @see fr.reborn.integrity.mixin.TitleScreenMixin
 */
public final class MainMenuRenderer {

    /** Position X gauche de la card OST — bottom-RIGHT du screen. */
    public static int ostCardX(int screenW) {
        return screenW - OSTPlayerV2.CARD_W - Math.round(20 * responsiveScale(screenW));
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

    /** Position Y du ServerInfoMini — bumpé pour faire de la place au stack
     *  centre-bas (server info → icons → credits). */
    public static int serverInfoY(int screenH) {
        return screenH - 72;
    }

    /** Position Y top du groupe d'icons centré sous ServerInfo. */
    public static int centerIconsY(int screenH) {
        return screenH - 48;
    }

    /** Taille de chaque icon button (settings/globe/discord). */
    public static final int CENTER_ICON_SIZE = 20;
    /** Espacement horizontal entre les icons. */
    public static final int CENTER_ICON_SPACING = 8;

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
        // 1. Dynamic Animated Player en background — MCEF browser plein
        //    écran qui rend le bbmodel_viewer.html. Fallback solid color
        //    sombre si MCEF KO.
        DynamicPlayerBackground.render(ctx, screenW, screenH);

        // Pas de logo central — la marque vit dans le BG MCEF + ServerInfo.

        // 2. ServerInfo centré (bas du screen).
        ServerInfoMini.render(ctx, serverInfoCenterX(screenW), serverInfoY(screenH));

        // 3. CreditsCorner centré sous le groupe d'icons.
        CreditsCorner.render(ctx, screenW, screenH);

        // 4. OST card background (bottom-RIGHT du screen).
        OSTPlayerV2.renderBackground(ctx, ostCardX(screenW), ostCardY(screenH));
    }
}
