package fr.reborn.hud.element;

/**
 * Point d'ancrage pour le positionnement d'un élément HUD.
 *
 * <p>Définit le coin / bord de la box utilisé comme origine pour l'offset
 * {@code (x, y)} de {@link HudElementState}. Permet par exemple à un
 * scoreboard d'avoir un offset relatif à son coin droit, donc resté
 * "collé" au bord droit même si la résolution change.
 *
 * <p>Les coefficients {@code fx, fy} dans [0,1] désignent la position
 * relative dans la box : {@code (0,0)} = top-left, {@code (1,1)} = bottom-right,
 * {@code (0.5, 0.5)} = centre.
 *
 * <p><strong>Phase actuelle :</strong> l'enum est livré pour les besoins
 * de l'UI du side panel. L'application effective de l'anchor sur le
 * positionnement vanilla des éléments est câblée dans CHANTIER C.
 */
public enum HudAnchor {
    TOP_LEFT     ("top_left",      0.0f, 0.0f),
    TOP_CENTER   ("top_center",    0.5f, 0.0f),
    TOP_RIGHT    ("top_right",     1.0f, 0.0f),
    CENTER_LEFT  ("center_left",   0.0f, 0.5f),
    CENTER       ("center",        0.5f, 0.5f),
    CENTER_RIGHT ("center_right",  1.0f, 0.5f),
    BOTTOM_LEFT  ("bottom_left",   0.0f, 1.0f),
    BOTTOM_CENTER("bottom_center", 0.5f, 1.0f),
    BOTTOM_RIGHT ("bottom_right",  1.0f, 1.0f);

    private final String id;
    public final float fx, fy;

    HudAnchor(String id, float fx, float fy) {
        this.id  = id;
        this.fx  = fx;
        this.fy  = fy;
    }

    public String id() { return id; }

    /** Index 0..8 dans une grille 3×3 ligne-par-ligne. */
    public int gridIndex() {
        return ordinal();
    }

    public static HudAnchor fromGridIndex(int i) {
        HudAnchor[] vals = values();
        if (i < 0 || i >= vals.length) return CENTER;
        return vals[i];
    }
}
