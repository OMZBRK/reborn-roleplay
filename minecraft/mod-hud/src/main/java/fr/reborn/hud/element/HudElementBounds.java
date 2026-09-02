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
            // Chat vanilla : largeur ~320, ~7 lignes visibles, ancré en bas-gauche
            // au-dessus de la hotbar (le bas reste ~à screenHeight - 34).
            case CHAT -> new HudElementBounds(4, screenHeight - 100, 320, 66);
            case SCOREBOARD -> new HudElementBounds(screenWidth - 100, screenHeight / 2 - 60, 90, 120);
            case BOSS_BAR -> new HudElementBounds(screenWidth / 2 - 90, 12, 182, 19);
            case ACTION_BAR -> new HudElementBounds(screenWidth / 2 - 90, screenHeight - 70, 180, 10);
            case HOTBAR -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 22, 182, 22);
            case EXPERIENCE_BAR -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 32, 182, 5);
            case HEALTH -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 39, 82, 9);
            case HUNGER -> new HudElementBounds(screenWidth / 2 + 9, screenHeight - 39, 82, 9);
            case ARMOR -> new HudElementBounds(screenWidth / 2 - 91, screenHeight - 49, 82, 9);
            case AIR -> new HudElementBounds(screenWidth / 2 + 9, screenHeight - 49, 82, 9);
            // Panneau RP vitals (tête + vie + chakra + stamina) en haut-gauche.
            case VITALS -> new HudElementBounds(4, 4, 204, 52);
            // Barre d'endurance de combat : par défaut sous le viseur (déplaçable).
            case COMBAT_ENDURANCE -> new HudElementBounds(screenWidth / 2 - 45, screenHeight / 2 + 18, 90, 5);
            // Rangée d'icônes de cooldown : par défaut au-dessus de la hotbar (déplaçable).
            case COOLDOWNS -> {
                int cw = fr.reborn.hud.combat.CooldownHud.width();
                yield new HudElementBounds(screenWidth / 2 - cw / 2, screenHeight - 62, cw,
                    fr.reborn.hud.combat.CooldownHud.height());
            }
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
        int newW = Math.max(8, Math.round(v.width()  * state.scale()));
        int newH = Math.max(8, Math.round(v.height() * state.scale()));

        HudAnchor anchor = state.effectiveAnchor(element);
        int anchorX = v.x() + Math.round(v.width()  * anchor.fx);
        int anchorY = v.y() + Math.round(v.height() * anchor.fy);
        int targetX = anchorX + state.x();
        int targetY = anchorY + state.y();
        int newX = targetX - Math.round(newW * anchor.fx);
        int newY = targetY - Math.round(newH * anchor.fy);
        return new HudElementBounds(newX, newY, newW, newH);
    }
}
