package fr.reborn.hud.element;

/**
 * Boîte rectangulaire en coordonnées écran (pixels post-scaling).
 * Sert à représenter les bounds vanilla d'un élément HUD et les bounds
 * actuels (vanilla + état utilisateur).
 */
public record HudElementBounds(int x, int y, int width, int height) {

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public int right()  { return x + width; }
    public int bottom() { return y + height; }
    public int centerX() { return x + width / 2; }
    public int centerY() { return y + height / 2; }

    /**
     * Bounds vanilla approximatifs par élément HUD, calculés en fonction
     * de la taille d'écran. Pas exact à la pixel près — les éléments
     * vanilla ont une logique de positionnement complexe (chat scale,
     * sidebar largeur dynamique, etc.) que reproduire exactement
     * coûterait trop. L'utilisateur ajuste visuellement de toute façon.
     */
    public static HudElementBounds vanillaFor(HudElement element, int screenWidth, int screenHeight) {
        return switch (element) {
            case CHAT -> new HudElementBounds(4, screenHeight - 60, 280, 50);
            case SCOREBOARD -> new HudElementBounds(screenWidth - 100, screenHeight / 2 - 60, 90, 120);
            case BOSS_BAR -> new HudElementBounds(screenWidth / 2 - 90, 12, 182, 19);
            case ACTION_BAR -> new HudElementBounds(screenWidth / 2 - 90, screenHeight - 70, 180, 10);
            case HOTBAR -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 22, 182, 22);
            case EXPERIENCE_BAR -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 32, 182, 5);
            case HEALTH -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 39, 82, 9);
            case HUNGER -> new HudElementBounds(screenWidth / 2 + 9, screenHeight - 39, 82, 9);
            case ARMOR -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 49, 82, 9);
            case AIR -> new HudElementBounds(screenWidth / 2 + 9, screenHeight - 49, 82, 9);
        };
    }

    /** Bounds courants = vanilla translatés par l'offset et scalés. */
    public static HudElementBounds currentFor(HudElement element, HudElementState state,
                                              int screenWidth, int screenHeight) {
        HudElementBounds v = vanillaFor(element, screenWidth, screenHeight);
        int w = Math.max(8, Math.round(v.width()  * state.scale()));
        int h = Math.max(8, Math.round(v.height() * state.scale()));
        return new HudElementBounds(v.x() + state.x(), v.y() + state.y(), w, h);
    }
}
