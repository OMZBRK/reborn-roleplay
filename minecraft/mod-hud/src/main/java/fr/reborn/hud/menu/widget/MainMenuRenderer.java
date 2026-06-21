package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import net.minecraft.client.gui.DrawContext;

/**
 * Orchestrateur du rendu visuel du main menu Reborn — appelle dans
 * l'ordre tous les composants passifs (BG MCEF, vignette overlay,
 * RebornLogoMark central, ServerInfoMini, CreditsCorner, OST card BG).
 * Les composants cliquables (PressSpacePrompt, IconButton...) restent
 * des ClickableWidget gerees par le screen.
 *
 * <p>Layout v3 (Stray/Sineru-style) :
 * <ul>
 *   <li>BG plein ecran : Dynamic Animated Player 3D via MCEF browser</li>
 *   <li>Vignette + overlay accent : assombrit les bords + halo central crimson</li>
 *   <li>Centre : RebornLogoMark (light beam vertical + REBORN + ROLEPLAY)</li>
 *   <li>Sous le logo : PressSpacePrompt (CTA primaire)</li>
 *   <li>Top-LEFT : ServerInfoMini (deplace pour liberer le centre cinematique)</li>
 *   <li>Top-RIGHT : X close + Solo dev</li>
 *   <li>Bottom-RIGHT : OSTPlayerV2 card</li>
 *   <li>Bottom-CENTER : 3 IconButton (Settings/Globe/Discord) + CreditsCorner</li>
 * </ul>
 *
 * @see fr.reborn.hud.mixin.menu.TitleScreenMixin
 */
public final class MainMenuRenderer {

    // ─────────────── Positionnement OST card (bottom-right) ────────
    public static int ostCardX(int screenW) {
        return screenW - OSTPlayerV2.CARD_W - Math.round(20 * responsiveScale(screenW));
    }
    public static int ostCardY(int screenH) {
        return screenH - OSTPlayerV2.CARD_H - 24;
    }

    // ─────────────── Positionnement PressSpacePrompt ───────────────
    /** PressSpacePrompt centre, place legerement sous le logo central. */
    public static int promptY(int screenH) {
        return RebornLogoMark.centerY(screenH) + Math.round(RebornLogoMark.totalHeight() * 0.85f);
    }

    // ─────────────── Positionnement ServerInfoMini (top-left) ──────
    /** ServerInfo deplace en haut-gauche pour liberer le centre.
     *  Le widget se centre autour de ce X — on tape donc a 130px du
     *  bord gauche pour que le card (largeur ~220px) commence aux
     *  alentours de 20px. */
    public static int serverInfoCenterX(int screenW) {
        return 130;
    }
    public static int serverInfoY(int screenH) {
        // Sous le drag-region (32px) + un peu de respiration.
        return 44;
    }

    // ─────────────── Positionnement 3 boutons bottom-center ────────
    public static int centerIconsY(int screenH) {
        return screenH - 36;
    }
    public static final int CENTER_ICON_SIZE = 22;
    public static final int CENTER_ICON_SPACING = 10;

    /**
     * Multiplicateur responsive — reduit l'UI sur petites fenetres.
     */
    public static float responsiveScale(int screenW) {
        if (screenW <= 700) return 0.55f;
        if (screenW <= 900) return 0.70f;
        if (screenW <= 1100) return 0.85f;
        return 1.0f;
    }

    private MainMenuRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        // 1. Dynamic Animated Player en BG (MCEF browser plein ecran).
        DynamicPlayerBackground.render(ctx, screenW, screenH);

        // 2. Overlay cinema : vignette aux 4 coins + halo crimson central.
        //    Donne de la profondeur sur les BG MCEF clairs et appuie le
        //    centre comme point focal Stray-style.
        renderCinemaOverlay(ctx, screenW, screenH);

        // 3. Logo central (light beam + REBORN + ROLEPLAY + underline).
        RebornLogoMark.render(ctx, screenW, screenH);

        // 4. ServerInfo deplace en haut-gauche (libere le centre).
        ServerInfoMini.render(ctx, serverInfoCenterX(screenW), serverInfoY(screenH));

        // 5. CreditsCorner centre bas.
        CreditsCorner.render(ctx, screenW, screenH);

        // 6. OST card background (bottom-RIGHT).
        OSTPlayerV2.renderBackground(ctx, ostCardX(screenW), ostCardY(screenH));
    }

    /**
     * Overlay cinema : vignette aux 4 coins (sombre) + halo accent central
     * (rouge crimson subtil). Cree de la profondeur sans cacher le BG.
     *
     * <p>Implem : 8 bandes degradees vers chaque coin. Pas un vrai radial
     * gradient (couteux en fillRects) mais visuellement equivalent pour
     * notre usage. Le halo central est un rectangle plus simple.
     */
    private static void renderCinemaOverlay(DrawContext ctx, int screenW, int screenH) {
        // Vignette : assombrit les bords sans toucher au centre.
        // Top edge — dark fade vers le haut
        verticalFade(ctx, 0, 0, screenW, screenH / 4,
            0xA0000000, 0x00000000);
        // Bottom edge — dark fade vers le bas
        verticalFade(ctx, 0, screenH - screenH / 4, screenW, screenH / 4,
            0x00000000, 0xC8000000);
        // Left edge fade
        horizontalFade(ctx, 0, 0, screenW / 5, screenH,
            0x70000000, 0x00000000);
        // Right edge fade
        horizontalFade(ctx, screenW - screenW / 5, 0, screenW / 5, screenH,
            0x00000000, 0x70000000);

        // Halo accent central : rectangle large + alpha bas + lerp vers
        // transparent sur les bords. Donne la signature "Akatsuki" au centre.
        int haloW = Math.min(screenW, 900);
        int haloH = Math.min(screenH / 2, 380);
        int haloX = (screenW - haloW) / 2;
        int haloY = (screenH - haloH) / 2 - 40;
        verticalFade(ctx, haloX, haloY, haloW, haloH / 2,
            0x00000000, Colors.withAlpha(Colors.ACCENT, 0.05f));
        verticalFade(ctx, haloX, haloY + haloH / 2, haloW, haloH / 2,
            Colors.withAlpha(Colors.ACCENT, 0.05f), 0x00000000);
    }

    private static void verticalFade(DrawContext ctx, int x, int y, int w, int h, int colorTop, int colorBot) {
        if (h <= 0 || w <= 0) return;
        for (int i = 0; i < h; i++) {
            float t = (float) i / h;
            int color = Colors.lerp(colorTop, colorBot, t);
            ctx.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    private static void horizontalFade(DrawContext ctx, int x, int y, int w, int h, int colorLeft, int colorRight) {
        if (h <= 0 || w <= 0) return;
        for (int i = 0; i < w; i++) {
            float t = (float) i / w;
            int color = Colors.lerp(colorLeft, colorRight, t);
            ctx.fill(x + i, y, x + i + 1, y + h, color);
        }
    }
}
