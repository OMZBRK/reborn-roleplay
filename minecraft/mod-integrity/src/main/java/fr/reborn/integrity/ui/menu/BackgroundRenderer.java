package fr.reborn.integrity.ui.menu;

import net.minecraft.client.gui.DrawContext;

/**
 * Background minimaliste Reborn — juste un dégradé vertical bleu nuit
 * en 3 bandes. Léger, propre, focus sur le contenu.
 *
 * <p>Aucun pulse, aucune particule, aucune vignette ici — la simplicité
 * voulue est l'identité Reborn. Les sakura particles sont gérées
 * séparément par MainMenuRenderer.
 */
public final class BackgroundRenderer {

    private static final int OUTER = 0xFF040611;   // #040611 presque noir
    private static final int MID   = 0xFF0E1428;   // #0e1428 bleu sombre
    private static final int LIGHT = 0xFF1E2A52;   // #1e2a52 bleu nuit profond

    private BackgroundRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        int third = screenH / 3;

        // Bande supérieure : outer -> mid.
        ctx.fillGradient(0, 0, screenW, third, OUTER, MID);

        // Bande centrale : mid -> light (plus lumineux au centre vertical).
        ctx.fillGradient(0, third, screenW, third * 2, MID, LIGHT);

        // Bande inférieure : light -> outer (assombrissement vers le bas).
        ctx.fillGradient(0, third * 2, screenW, screenH, LIGHT, OUTER);
    }
}
