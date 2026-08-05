package fr.reborn.hud.ui.style;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pattern de grille subtil dessiné par dots — purement esthétique, donne
 * une "texture" à la zone d'édition sans détourner l'œil du HUD vanilla
 * qui reste derrière.
 *
 * <p>Inspiration : Figma canvas, Excalidraw, les éditeurs de niveaux
 * type Unity / Unreal. Le grain léger aide l'œil à percevoir l'espace
 * comme un canvas plutôt qu'un overlay opaque.
 */
public final class BackgroundGrid {

    private static final int CELL = 32;
    private static final int DOT_SIZE = 1;
    private static final int DOT_COLOR = 0x14FFFFFF;       // 8%
    private static final int DOT_COLOR_MAJOR = 0x24FFFFFF; // 14% sur les majors (toutes les 4 cellules)
    private static final int MAJOR_EVERY = 4;

    private BackgroundGrid() {}

    /**
     * Render dots sur une zone donnée. Origine grille top-left du rect.
     * Les "major dots" toutes les 4 cellules sont legerement plus visibles
     * pour aider à mesurer.
     */
    public static void render(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1) {
        int startCol = 0;
        int startRow = 0;
        int cols = (x1 - x0) / CELL + 1;
        int rows = (y1 - y0) / CELL + 1;
        for (int r = startRow; r < startRow + rows; r++) {
            for (int c = startCol; c < startCol + cols; c++) {
                int x = x0 + c * CELL;
                int y = y0 + r * CELL;
                if (x >= x1 || y >= y1) continue;
                boolean major = (c % MAJOR_EVERY == 0) && (r % MAJOR_EVERY == 0);
                ctx.fill(x, y, x + DOT_SIZE, y + DOT_SIZE, major ? DOT_COLOR_MAJOR : DOT_COLOR);
            }
        }
    }
}
