package fr.reborn.hud.element;

/**
 * État complet d'un élément HUD repositionné :
 * <ul>
 *   <li>{@code x, y} : offset par rapport au point d'ancrage {@code anchor}
 *       de la position vanilla (pixels post-scaling).</li>
 *   <li>{@code scale} : multiplicateur de taille, {@code 1.0} = taille vanilla.
 *       Appliqué via {@code matrix.scale()} dans les Mixins. Clampé [0.25, 4.0].</li>
 *   <li>{@code visible} : si false, le mixin skip complètement le render
 *       de l'élément.</li>
 *   <li>{@code anchor} : point d'ancrage utilisé pour l'offset.
 *       {@code null} signifie "utilise le default de l'élément" (cf
 *       {@link HudElement#defaultAnchor()}).</li>
 * </ul>
 *
 * <p>Anchor est nullable pour distinguer "pas encore configuré"
 * (= utilise default) de "configuré sur la même valeur que le default".
 * Permet de migrer un défaut sans casser les configs existantes.
 */
public record HudElementState(int x, int y, float scale, boolean visible, HudAnchor anchor) {

    public static final HudElementState DEFAULT = new HudElementState(0, 0, 1.0f, true, null);

    public HudElementState withPos(int x, int y) {
        return new HudElementState(x, y, scale, visible, anchor);
    }

    public HudElementState withDelta(int dx, int dy) {
        return new HudElementState(x + dx, y + dy, scale, visible, anchor);
    }

    public HudElementState withScale(float scale) {
        float clamped = Math.max(0.25f, Math.min(scale, 4.0f));
        return new HudElementState(x, y, clamped, visible, anchor);
    }

    public HudElementState withVisible(boolean visible) {
        return new HudElementState(x, y, scale, visible, anchor);
    }

    public HudElementState withAnchor(HudAnchor anchor) {
        return new HudElementState(x, y, scale, visible, anchor);
    }

    /** Anchor effectif : explicite si défini, sinon défault de l'élément. */
    public HudAnchor effectiveAnchor(HudElement element) {
        return anchor != null ? anchor : element.defaultAnchor();
    }
}
