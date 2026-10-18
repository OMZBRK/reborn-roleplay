package fr.reborn.hud.meditation;

/**
 * Forme et couleurs du <b>halo de méditation</b>, relevées image par image sur la vidéo de référence (17/10) et
 * partagées par le rendu en jeu ({@link MeditationRendu}) et l'aperçu du choix de couleur
 * ({@code menu/character/perso/PersoAura}).
 *
 * <h2>Ce que montre la vidéo (mesures sur l'herbe, 18/10)</h2>
 * <ul>
 *   <li>un <b>disque sombre</b> net et rond centré sous le joueur assis, rayon ≈ 0,48 bloc (compté en texels d'herbe sur
 *       la vue de dessus : la caméra de la vidéo est proche, la perspective grossit le corps) : l'herbe y est voilée de
 *       vert-gris sombre ({@code (61,125,39)} → {@code (66,114,64)}, soit un voile {@code (71,103,89)} à 50 %) ;</li>
 *   <li>autour, une <b>couronne de lumière</b> vert menthe collée au bord du disque, la plus vive au bord
 *       ({@code (61,125,39)} → {@code (97,187,87)} : + {@code (36,62,48)}, une lumière additive {@code #8CFFC2} × 0,25),
 *       qui s'éteint presque linéairement en ≈ 0,6 bloc, jusqu'à ≈ 1,1 bloc du centre ;</li>
 *   <li>de petites <b>étincelles bleu clair</b> (≈ {@code #6F9FF8}, cœur plus blanc) qui scintillent autour du corps,
 *       peu nombreuses, du sol à la tête.</li>
 * </ul>
 *
 * <h2>Couleur choisie par le joueur</h2>
 * Une seule couleur d'aura {@code C} (défaut {@link #DEFAUT}, le menthe de la vidéo) donne les trois teintes : la
 * lumière de la couronne ({@code C} ramenée à pleine intensité, atténuée si la couleur est sombre), le voile du
 * disque ({@code 0,36·C + 20}, la formule qui redonne le vert-gris mesuré) et les étincelles (teinte de {@code C}
 * tournée de 70°, saturation et valeur de {@code #6F9FF8} : le menthe redonne exactement le bleu de la vidéo, un
 * rouge donne des braises dorées, un bleu des étincelles violettes).
 */
public final class Aura {

    /** Couleur d'aura par défaut : le menthe de la référence. */
    public static final int DEFAUT = 0x8CFFC2;
    /** Rayon du disque sombre et rayon où la couronne s'éteint (blocs). */
    public static final float R_DISQUE = 0.48f, R_HALO = 1.10f;
    /**
     * Voile du disque (opacité) ; couronne au bord du disque : <b>teinte</b> (le sol prend la couleur, part 0..1) puis
     * <b>lumière</b> additive. La vidéo est une lumière additive pure (+ {@code (36,62,48)} = {@code #8CFFC2} × 0,25),
     * mais une lumière rouge ajoutée à l'herbe verte donne du kaki : la part de teinte fait lire la couleur choisie sur
     * tous les sols, et le menthe par défaut retombe sur la mesure ({@code (61,125,39)} → {@code (101,194,110)}).
     */
    public static final float VOILE = 0.5f, TEINTE = 0.35f, LUMIERE = 0.09f;

    private Aura() {}

    /** Couronne : 1 au bord du disque, s'éteint vers {@link #R_HALO} ; 0 dans le disque. {@code r} en blocs. */
    public static float couronne(float r) {
        if (r < R_DISQUE) return 0f;
        float t = (r - R_DISQUE) / (R_HALO - R_DISQUE);
        if (t >= 1f) return 0f;
        float p = (float) Math.pow(1f - t, 1.15f);
        float queue = t > 0.8f ? 1f - (t - 0.8f) / 0.2f * 0.35f : 1f;          // fin adoucie, sans arête
        return p * queue;
    }

    /** Couverture du disque 0..1 au rayon {@code r} (blocs), bord net anti-crénelé sur {@code largeur} (blocs). */
    public static float disque(float r, float largeur) {
        float d = (R_DISQUE - r) / largeur + 0.5f;
        return d <= 0f ? 0f : d >= 1f ? 1f : d;
    }

    /** Couleur de la couronne (RGB 0..255) à pleine force : la couleur ramenée à pleine intensité. */
    public static int lumiere(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int m = Math.max(r, Math.max(g, b));
        if (m <= 0) return 0;
        float v = m / 255f, k = (0.55f + 0.45f * v) * 255f / m;                // une couleur sombre éclaire moins
        return rgbClamp(r * k, g * k, b * k);
    }

    /** Voile du disque sombre (RGB 0..255, éclairé ensuite par la lumière du lieu). */
    public static int voile(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return rgbClamp(0.36f * r + 20, 0.36f * g + 20, 0.36f * b + 20);
    }

    /** Étincelles : teinte de l'aura tournée de 70°, saturation ≤ 0,55, valeur 0,97 (le #6F9FF8 de la référence). */
    public static int etincelle(int rgb) {
        float[] hsv = hsv(rgb);
        float h = (hsv[0] + 70f / 360f) % 1f;
        float s = Math.min(0.55f, hsv[1] * 1.25f);
        return depuisHsv(h, s, 0.97f);
    }

    /* ------------------------------------------------------------------ outils */

    /** RGB 0..255 bornés, assemblés. */
    public static int rgbClamp(float r, float g, float b) {
        int ri = Math.max(0, Math.min(255, Math.round(r))), gi = Math.max(0, Math.min(255, Math.round(g))),
                bi = Math.max(0, Math.min(255, Math.round(b)));
        return ri << 16 | gi << 8 | bi;
    }

    static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 1e-6f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[]{h, max <= 0 ? 0 : d / max, max};
    }

    static int depuisHsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h * 6f) % 2f - 1)), m = v - c;
        float r, g, b;
        int k = (int) Math.floor(h * 6f) % 6;
        switch (k) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return rgbClamp((r + m) * 255f, (g + m) * 255f, (b + m) * 255f);
    }
}
