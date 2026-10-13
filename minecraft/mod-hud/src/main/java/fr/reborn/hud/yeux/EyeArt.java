package fr.reborn.hud.yeux;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * L'œil en gros plan, en pixel art ({@code textures/gui/yeux/}, générées par {@code tools/gen_yeux_textures.py}) :
 * peau, œil dans ses paupières selon le type, orbite vide, globe retiré. Dessiné à une échelle entière de pixels
 * écran pour garder des texels nets. Sert au prélèvement et à la greffe.
 */
final class EyeArt {

    enum Kind { NORMAL, SHARINGAN, BYAKUGAN, RINNEGAN }

    private EyeArt() {}

    static Kind kind(String id) {
        if (id == null) return Kind.NORMAL;
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "sharingan" -> Kind.SHARINGAN;
            case "byakugan" -> Kind.BYAKUGAN;
            case "rinnegan" -> Kind.RINNEGAN;
            default -> Kind.NORMAL;
        };
    }

    static String label(Kind k) {
        return switch (k) {
            case SHARINGAN -> "Sharingan";
            case BYAKUGAN -> "Byakugan";
            case RINNEGAN -> "Rinnegan";
            default -> "Œil";
        };
    }

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/yeux/" + name + ".png");
    }

    private static String file(Kind k) { return k.name().toLowerCase(java.util.Locale.ROOT); }

    /** Échelle entière des textures (1 texel = n pixels écran) pour un œil de demi-largeur {@code r} px. */
    static int scale(double r) { return Math.max(1, (int) Math.round(r / 29.0)); }

    /** Demi-largeur réelle de l'œil dessiné (alignée sur la grille des texels). */
    static double snap(double r) { return scale(r) * 29.0; }

    private static void blit(GuiGraphicsExtractor g, Identifier id, double x, double y, int tw, int th, int n) {
        g.blit(RenderPipelines.GUI_TEXTURED, id, (int) Math.round(x), (int) Math.round(y), 0f, 0f, tw * n, th * n, tw, th, tw, th);
    }

    /** Plateau laqué rond sous la tête du patient (texture {@code plateau.png}, même grille de texels que l'œil). */
    static void plateau(GuiGraphicsExtractor g, double cx, double cy, double r) {
        int n = scale(r);
        blit(g, tex("plateau"), cx - 54.5 * n, cy - 54.5 * n, 112, 112, n);
    }

    /** Peau du visage autour de l'œil (patch rond, sourcil, arcade). Texture pixel art {@code peau.png}. */
    static void skin(GuiGraphicsExtractor g, double cx, double cy, double r) {
        int n = scale(r);
        blit(g, tex("peau"), cx - 48 * n, cy - 50 * n, 96, 96, n);
    }

    /** Orbite vide (après prélèvement, avant greffe). */
    static void socket(GuiGraphicsExtractor g, double cx, double cy, double r, float wound) {
        int n = scale(r);
        blit(g, tex("orbite"), cx - 36 * n, cy - 22 * n, 72, 44, n);
    }

    /** L'œil complet dans ses paupières. Texture {@code oeil_<type>.png}. */
    static void eye(GuiGraphicsExtractor g, double cx, double cy, double r, Kind kind, long now) {
        int n = scale(r);
        blit(g, tex("oeil_" + file(kind)), cx - 36 * n, cy - 22 * n, 72, 44, n);
    }

    /** Le globe seul (rond), une fois retiré. Texture {@code globe_<type>.png}, rayon ~{@code r}. */
    static void globe(GuiGraphicsExtractor g, double cx, double cy, double r, Kind kind, long now) {
        int n = Math.max(1, (int) Math.round(r / 16.5));
        blit(g, tex("globe_" + file(kind)), cx - 18 * n, cy - 18 * n, 36, 36, n);
    }
}
