package fr.reborn.hud.element;

/**
 * Offset (en pixels écran, post-scaling) appliqué à un élément HUD.
 * {@code (0,0)} = position vanilla. Valeurs positives = décale vers la
 * droite / le bas.
 */
public record HudElementOffset(int x, int y) {
    public static final HudElementOffset ZERO = new HudElementOffset(0, 0);

    public HudElementOffset withDelta(int dx, int dy) {
        return new HudElementOffset(x + dx, y + dy);
    }
}
