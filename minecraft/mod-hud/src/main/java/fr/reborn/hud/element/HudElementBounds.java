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
            // Chat élargi pour accueillir le panel custom (header 18 + msgs ~70 + input 18 + padding ≈ 110)
            // au-dessus de la zone hotbar/status (qui termine ~à screenHeight - 22).
            case CHAT -> new HudElementBounds(4, screenHeight - 124, 320, 90);
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

    /**
     * Bounds courants en tenant compte de l'{@link HudAnchor} :
     *
     * <p>L'anchor identifie le point de la box qui reste fixe quand on
     * scale et auquel s'ajoute l'offset {@code (x, y)}. Exemples :
     * <ul>
     *   <li>anchor {@code TOP_LEFT}, offset (5, 10), scale 2 : box top-left
     *       passe à vanillaTL + (5,10), taille doublée vers bas-droite.</li>
     *   <li>anchor {@code BOTTOM_RIGHT}, offset (-5, -3), scale 1.5 :
     *       box bottom-right passe à vanillaBR + (-5,-3), elle grossit
     *       vers le haut-gauche (BR reste l'ancre).</li>
     *   <li>anchor {@code CENTER}, offset (0, 0), scale 2 : box reste
     *       centrée sur vanilla center, agrandie symétriquement.</li>
     * </ul>
     */
    public static HudElementBounds currentFor(HudElement element, HudElementState state,
                                              int screenWidth, int screenHeight) {
        HudElementBounds v = vanillaFor(element, screenWidth, screenHeight);
        int newW = Math.max(8, Math.round(v.getWidth()  * state.scale()));
        int newH = Math.max(8, Math.round(v.getHeight() * state.scale()));

        HudAnchor anchor = state.effectiveAnchor(element);
        int anchorX = v.x() + Math.round(v.getWidth()  * anchor.fx);
        int anchorY = v.y() + Math.round(v.getHeight() * anchor.fy);
        int targetX = anchorX + state.x();
        int targetY = anchorY + state.y();
        int newX = targetX - Math.round(newW * anchor.fx);
        int newY = targetY - Math.round(newH * anchor.fy);
        return new HudElementBounds(newX, newY, newW, newH);
    }
}
