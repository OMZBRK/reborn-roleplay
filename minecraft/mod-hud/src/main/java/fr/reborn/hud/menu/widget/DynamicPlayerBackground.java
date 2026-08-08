package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fond du main menu Reborn — <b>fond branded statique</b> accordé au logo
 * Reborn (crimson sur sombre).
 *
 * <p>Historique : ce fond devait afficher un « Dynamic Animated Player » 3D via
 * un browser MCEF (fork {@code net.dimaskama:mcef-modern}). Abandonné en 26.1 :
 * sous JDK 25 (imposé par MC 26.1) le binding jcef lève une exception sur le
 * thread {@code AWT-EventQueue-0} juste après la création du browser, si bien
 * que le callback natif {@code onPaint} n'alimente jamais la texture GPU — le
 * browser reste transparent (le panorama vanilla traversait). Diagnostic prouvé
 * par un probe HTML minimal (fond rouge) qui ne s'affichait pas non plus. On
 * remplace donc le browser par un simple dégradé sombre → crimson, robuste et
 * cohérent avec la marque, sur lequel le logo + le menu ressortent bien.
 */
public final class DynamicPlayerBackground {

    /** Haut du dégradé : presque noir, légère chaleur. */
    private static final int TOP = 0xFF17090C;
    /** Bas du dégradé : crimson profond (accord logo Reborn). */
    private static final int BOTTOM = 0xFF3A0E15;
    /** Vignette latérale (assombrit les bords pour cadrer le contenu). */
    private static final int VIGNETTE = 0x55000000;

    private DynamicPlayerBackground() {}

    /** Conservé pour compat d'appel : plus d'init MCEF, no-op. */
    public static void init() {
        // Plus de browser MCEF (voir javadoc). Rien à initialiser.
    }

    /** Fond plein écran : dégradé vertical sombre → crimson + vignette douce. */
    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        int denom = Math.max(1, screenH - 1);
        for (int i = 0; i < screenH; i++) {
            float t = (float) i / denom;
            // Courbe douce (ease-in) pour concentrer le crimson vers le bas.
            float e = t * t;
            int c = Colors.lerp(TOP, BOTTOM, e);
            ctx.fill(0, i, screenW, i + 1, c);
        }
        // Vignette : deux bandes latérales en dégradé vers le centre.
        int band = Math.max(48, screenW / 6);
        horizontalFade(ctx, 0, 0, band, screenH, VIGNETTE, 0x00000000);
        horizontalFade(ctx, screenW - band, 0, band, screenH, 0x00000000, VIGNETTE);
    }

    /** Dégradé horizontal (gauche→droite) colonne par colonne. */
    private static void horizontalFade(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                       int colorLeft, int colorRight) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < w; i++) {
            float t = (float) i / w;
            int c = Colors.lerp(colorLeft, colorRight, t);
            ctx.fill(x + i, y, x + i + 1, y + h, c);
        }
    }

    /** Conservé pour compat d'appel (ancien cleanup MCEF) : no-op. */
    public static void dispose() {
        // Rien à libérer.
    }
}
