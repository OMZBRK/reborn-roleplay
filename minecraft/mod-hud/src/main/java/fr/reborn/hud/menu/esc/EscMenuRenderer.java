package fr.reborn.hud.menu.esc;

import fr.reborn.hud.menu.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

    public static void renderBackground(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        // Backdrop sombre qui masque le jeu derrière.
        ctx.fill(0, 0, screenW, screenH, Colors.BACKDROP_85);

        // Overlay supplémentaire avec teinte accent très subtle.
        ctx.fill(0, 0, screenW, screenH, Colors.withAlpha(Colors.ACCENT_SOFT, 0.15f));
    }

    /**
     * Calcule les dimensions des 4 panels de la grille 2×2.
     *
     * <p>Adaptatif : marges latérales et offset header proportionnels à la
     * taille de l'écran (sans descendre sous un plancher), bornes clampées
     * pour ne jamais produire de panels négatifs/minuscules sur petit
     * viewport (GUI Scale élevé). Évite que la grille déborde l'écran ou
     * chevauche la community bar.
     */
    public static int[] panelGridBounds(int screenW, int screenH) {
        // Marge latérale : 80px sur grand écran, réduite jusqu'à 16px quand
        // la largeur se resserre (≈ screenW/12).
        int sideMargin = Math.max(16, Math.min(80, screenW / 12));
        // Offset sous le header : réduit sur écran court pour rendre de la
        // hauteur aux panels.
        int topOffset = screenH < 480 ? 12 : 30;

        int gridTop = HEADER_H + topOffset;
        int gridBottom = screenH - COMMUNITY_BAR_H - 16;
        int gridLeft = sideMargin;
        int gridRight = screenW - sideMargin;

        int gridW = Math.max(2 * 120 + PANEL_GAP, gridRight - gridLeft);
        int gridH = Math.max(2 * 70 + PANEL_GAP, gridBottom - gridTop);

        int panelW = (gridW - PANEL_GAP) / 2;
        int panelH = (gridH - PANEL_GAP) / 2;
        return new int[] {gridLeft, gridTop, panelW, panelH};
    }
}
