package fr.reborn.integrity.ui.esc;

import fr.reborn.integrity.ui.Colors;
import net.minecraft.client.gui.DrawContext;

/**
 * Orchestrateur du rendu du menu pause Reborn (ESC menu).
 *
 * <p>Référence design : {@code esc-menu.jsx} — pause menu avec tabs en
 * haut + 4 panels en grille 2x2 (Profile / Stream / Blog / Rewards) +
 * community bar en bas.
 *
 * <p>Style : backdrop sombre 90% qui masque le jeu, panels avec card
 * BG + accent doré sur certains éléments.
 */
public final class EscMenuRenderer {

    public static final int HEADER_H = 64;
    public static final int COMMUNITY_BAR_H = 60;
    public static final int PANEL_GAP = 14;

    private EscMenuRenderer() {}

    public static void renderBackground(DrawContext ctx, int screenW, int screenH) {
        // Backdrop sombre qui masque le jeu derrière.
        ctx.fill(0, 0, screenW, screenH, Colors.BACKDROP_85);

        // Overlay supplémentaire avec teinte accent très subtle.
        ctx.fill(0, 0, screenW, screenH, Colors.withAlpha(Colors.ACCENT_SOFT, 0.15f));
    }

    /** Calcule les dimensions des 4 panels de la grille 2×2. */
    public static int[] panelGridBounds(int screenW, int screenH) {
        int gridTop = HEADER_H + 30;
        int gridBottom = screenH - COMMUNITY_BAR_H - 20;
        int gridLeft = 80;
        int gridRight = screenW - 80;
        int gridW = gridRight - gridLeft;
        int gridH = gridBottom - gridTop;
        int panelW = (gridW - PANEL_GAP) / 2;
        int panelH = (gridH - PANEL_GAP) / 2;
        return new int[] {gridLeft, gridTop, panelW, panelH};
    }
}
