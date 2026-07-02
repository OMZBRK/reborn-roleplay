package fr.reborn.hud.camera;

/**
 * Presets de cadrage de la caméra épaule Reborn (style Zenkai / anime-RP).
 *
 * <p>Chaque preset définit un triplet (distance, décalage latéral, décalage
 * vertical) en blocs. Le <b>côté</b> de l'épaule (gauche/droite) est géré
 * séparément par {@link RebornCamera} (swap), donc le décalage latéral est
 * toujours une magnitude positive.
 */
public enum CameraPreset {

    DEFAUT("Défaut", 3.4, 0.50, 0.15),
    PROCHE("Proche", 2.6, 0.45, 0.05),
    LARGE("Large", 4.6, 0.42, 0.28),
    CINEMA("Cinéma", 5.6, 0.00, 0.35);

    public final String label;
    public final double distance;
    public final double right;
    public final double up;

    CameraPreset(String label, double distance, double right, double up) {
        this.label = label;
        this.distance = distance;
        this.right = right;
        this.up = up;
    }

    public CameraPreset next() {
        CameraPreset[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
