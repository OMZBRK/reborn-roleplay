package fr.reborn.hud.element;

/**
 * État complet d'un élément HUD repositionné :
 * <ul>
 *   <li>{@code x, y} : offset par rapport à la position vanilla (pixels post-scaling).</li>
 *   <li>{@code scale} : multiplicateur de taille, {@code 1.0} = taille vanilla.
 *       Appliqué via {@code matrix.scale()} dans les Mixins.</li>
 *   <li>{@code visible} : si false, le mixin skip complètement le render
 *       de l'élément. Pratique pour cacher la bossbar ou le scoreboard
 *       sans toucher au serveur.</li>
 * </ul>
 */
public record HudElementState(int x, int y, float scale, boolean visible) {

    public static final HudElementState DEFAULT = new HudElementState(0, 0, 1.0f, true);

    public HudElementState withPos(int x, int y) {
        return new HudElementState(x, y, scale, visible);
    }

    public HudElementState withDelta(int dx, int dy) {
        return new HudElementState(x + dx, y + dy, scale, visible);
    }

    public HudElementState withScale(float scale) {
        // Clamp pour eviter des valeurs degenerees qui rendraient l'element
        // invisible / immense / au-dela des limites GL.
        float clamped = Math.max(0.25f, Math.min(scale, 4.0f));
        return new HudElementState(x, y, clamped, visible);
    }

    public HudElementState withVisible(boolean visible) {
        return new HudElementState(x, y, scale, visible);
    }
}
