package fr.reborn.hud.concentration;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * <b>Halo lumineux de la plaque lue</b> (lot 4, étape B ; décision du 07/11, planche c4b) : une lueur douce posée sur la
 * plaque, juste derrière le texte, avec quelques étincelles qui montent ; elle <b>naît et meurt avec le texte</b> (même
 * opacité, mêmes fondus : {@code RecitHud} la dessine juste avant le bloc de texte).
 *
 * <p>Allure (mesures de la planche c4b, px 1080p à l'échelle 1 = capitales de 12 px, plaque à 4 blocs) : cœur serré
 * presque blanc (σ 5 px), lueur d'environ 45 px de diamètre visible (σ 17,5 px), voile large très faible (σ 38 px), le tout
 * dans <b>un seul quad tourné vers la caméra</b> de 128 px (texture 64 × 64 calculée une fois, filtrée en linéaire) posé
 * 6 px sous le centre du texte ; <b>6 étincelles</b> (8 au plus) : un point de 2 × 2 px et sa petite lueur, qui montent de
 * 52 à 78 px en 1,9 à 2,8 s, ondulent un peu, naissent et s'éteignent sur leur période. Couleur par défaut : <b>ivoire-or
 * chaud</b> ({@link #IVOIRE_OR}, accordée au contour ivoire des joueurs et aux mots {@code <or>}) ; le violet de la
 * référence reste en constante de rechange ({@link #VIOLET}).
 *
 * <p>Mélange additif : texture prémultipliée (couleur × intensité, alpha presque nul) dans
 * {@code GUI_TEXTURED_PREMULTIPLIED_ALPHA} — la lumière s'ajoute au décor, sans plaque sombre. Coût : 1 + 6 blits par
 * image pendant la lecture, <b>aucune allocation</b> (positions des étincelles calculées depuis l'horloge) ; deux petites
 * textures (64² et 16²) créées au premier usage.
 */
public final class HaloPlaque {

    /** Palette : cœur, lueur, voile, étincelle (RVB). */
    public record Palette(int coeur, int lueur, int voile, int etincelle) { }

    /** H1, retenue le 07/11 : ivoire-or chaud. */
    public static final Palette IVOIRE_OR = new Palette(0xFFF9E8, 0xFFCC7A, 0xFFB25C, 0xFFF2D2);
    /** H2 : le violet de la référence, gardé comme rechange (jamais par défaut). */
    public static final Palette VIOLET = new Palette(0xEEE0FF, 0xA06CD2, 0x764CB6, 0xE4D2FF);
    /** Palette en service. */
    public static Palette palette = IVOIRE_OR;

    /* profil radial (px à l'échelle 1) et intensités additives, planche c4b */
    static final double COEUR_SIGMA = 5.0, COEUR_I = 0.85, LUEUR_SIGMA = 17.5, LUEUR_I = 1.45, VOILE_SIGMA = 38.0, VOILE_I = 0.50;
    /** Demi-côté du quad à l'échelle 1 (texture 64 × 64 étirée sur 128 px). */
    static final double QUAD_DEMI = 64.0;
    /** Le halo se pose un peu sous le centre du texte (px à l'échelle 1). */
    public static final double HALO_DY = 6.0;
    /** Étincelles : dx, dy de départ, période (s), phase, montée (px), dérive (px). 6 sur 8 au plus. */
    private static final double[][] ETINCELLES = {
        {-24.0, 8.0, 2.10, 0.05, 62.0, 4.0},
        {19.0, 12.0, 2.60, 0.38, 74.0, 5.0},
        {-7.0, 0.0, 1.90, 0.71, 52.0, 3.0},
        {36.0, -4.0, 2.30, 0.52, 58.0, 4.0},
        {-41.0, -6.0, 2.80, 0.20, 78.0, 5.0},
        {8.0, 16.0, 2.00, 0.88, 66.0, 4.0},
    };
    private static final int T_HALO = 64, T_ETINCELLE = 16;
    /** Rayon de l'étincelle dans sa texture (px à l'échelle 1) : la lueur σ 2,2 tient dans 6 px. */
    private static final double ETINCELLE_R = 6.0;

    private static final Identifier TEX_HALO = Identifier.fromNamespaceAndPath("reborn-hud", "concentration/halo_plaque"),
        TEX_ETINCELLE = Identifier.fromNamespaceAndPath("reborn-hud", "concentration/etincelle_plaque");
    private static Palette faite;
    private static boolean enPanne;

    /** Banc : nombre d'étincelles dessinées à la dernière image. */
    static int etincellesVues;

    private HaloPlaque() {}

    /**
     * Dessine le halo centré en ({@code cx}, {@code cy}) pixels écran (le point d'ancre du texte, en px physiques ;
     * {@link #HALO_DY} est ajouté ici), à l'échelle {@code k} (celle du texte : capitales / 12 × facteur d'écran), à
     * l'opacité {@code a} (celle du texte), au temps {@code tMs}. À appeler en pixels écran ({@code DaKit.debutPx}).
     */
    public static void dessiner(GuiGraphicsExtractor ctx, double cx, double cy, double k, float a, long tMs) {
        if (a <= 0.004f || enPanne) return;
        if (!preparer()) return;
        cy += HALO_DY * k;
        int col = gris(a);
        int d = (int) Math.round(2 * QUAD_DEMI * k);
        int x0 = (int) Math.round(cx - d / 2.0), y0 = (int) Math.round(cy - d / 2.0);
        ctx.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, TEX_HALO, x0, y0, 0f, 0f, d, d, T_HALO, T_HALO, T_HALO, T_HALO, col);
        // étincelles : chacune monte depuis la lueur, ondule, naît et s'éteint sur sa période
        int n = 0;
        double t = tMs / 1000.0;
        int de = Math.max(3, (int) Math.round(2 * ETINCELLE_R * k));
        for (double[] e : ETINCELLES) {
            double u = ((t / e[2] + e[3]) % 1.0 + 1.0) % 1.0;
            double vie = Math.pow(Math.sin(Math.PI * u), 1.5);
            float av = (float) (vie * a);
            if (av <= 0.01f) continue;
            double ex = cx + (e[0] + e[5] * Math.sin(2 * Math.PI * (1.5 * u + e[3]))) * k;
            double ey = cy + (e[1] - e[4] * u) * k;
            int ix = (int) Math.round(ex - de / 2.0), iy = (int) Math.round(ey - de / 2.0);
            ctx.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, TEX_ETINCELLE, ix, iy, 0f, 0f, de, de,
                T_ETINCELLE, T_ETINCELLE, T_ETINCELLE, T_ETINCELLE, gris(av));
            n++;
        }
        etincellesVues = n;
    }

    /** Couleur de sommet prémultipliée : tout × a (la texture l'est déjà). */
    private static int gris(float a) {
        int v = Math.max(0, Math.min(255, Math.round(a * 255f)));
        return v << 24 | v << 16 | v << 8 | v;
    }

    /** Textures (une fois, et à chaque changement de palette). */
    private static boolean preparer() {
        Palette p = palette;
        if (p == faite) return true;
        try {
            var tm = Minecraft.getInstance().getTextureManager();
            tm.register(TEX_HALO, new Lineaire("reborn-hud halo de plaque", texHalo(p)));
            tm.register(TEX_ETINCELLE, new Lineaire("reborn-hud étincelle de plaque", texEtincelle(p)));
            faite = p;
            return true;
        } catch (RuntimeException | LinkageError e) {
            enPanne = true;
            org.slf4j.LoggerFactory.getLogger("reborn-hud").warn("Halo de plaque coupé ({})", e.toString());
            return false;
        }
    }

    private static double g(double r2, double s) { return Math.exp(-r2 / (s * s)); }

    /** Quad du halo : 64 × 64 texels pour 128 px à l'échelle 1 (2 px par texel), profil de la planche c4b. */
    static NativeImage texHalo(Palette p) {
        NativeImage img = new NativeImage(T_HALO, T_HALO, false);
        double pas = 2 * QUAD_DEMI / T_HALO;
        for (int j = 0; j < T_HALO; j++) {
            for (int i = 0; i < T_HALO; i++) {
                double x = (i + 0.5) * pas - QUAD_DEMI, y = (j + 0.5) * pas - QUAD_DEMI;
                double r2 = x * x + y * y;
                // la texture s'annule avant le bord du quad : aucune arête visible
                double bord = Math.sqrt(Math.max(0, 1 - Math.sqrt(r2) / QUAD_DEMI));
                double ic = COEUR_I * g(r2, COEUR_SIGMA) * bord, il = LUEUR_I * g(r2, LUEUR_SIGMA) * bord,
                    iv = VOILE_I * g(r2, VOILE_SIGMA) * bord;
                img.setPixel(i, j, premult(p.coeur(), ic, p.lueur(), il, p.voile(), iv));
            }
        }
        return img;
    }

    /** Étincelle : point de 2 × 2 px plein et sa lueur (σ 2,2 px), 16 × 16 texels sur 12 px à l'échelle 1. */
    static NativeImage texEtincelle(Palette p) {
        NativeImage img = new NativeImage(T_ETINCELLE, T_ETINCELLE, false);
        double pas = 2 * ETINCELLE_R / T_ETINCELLE;
        for (int j = 0; j < T_ETINCELLE; j++) {
            for (int i = 0; i < T_ETINCELLE; i++) {
                double x = (i + 0.5) * pas - ETINCELLE_R, y = (j + 0.5) * pas - ETINCELLE_R;
                double point = Math.abs(x) <= 1.0 && Math.abs(y) <= 1.0 ? 1.0 : 0.0;
                img.setPixel(i, j, premult(p.etincelle(), point, p.lueur(), g(x * x + y * y, 2.2), 0, 0));
            }
        }
        return img;
    }

    /**
     * Pixel prémultiplié (ARGB de {@code NativeImage#setPixel}) : somme des couleurs × intensités, bornée à 255 ; alpha faible (un
     * dixième de la luminance) : la lumière s'ajoute presque sans assombrir ce qui est derrière.
     */
    private static int premult(int c1, double i1, int c2, double i2, int c3, double i3) {
        double r = ((c1 >> 16) & 255) * i1 + ((c2 >> 16) & 255) * i2 + ((c3 >> 16) & 255) * i3;
        double gg = ((c1 >> 8) & 255) * i1 + ((c2 >> 8) & 255) * i2 + ((c3 >> 8) & 255) * i3;
        double b = (c1 & 255) * i1 + (c2 & 255) * i2 + (c3 & 255) * i3;
        int R = (int) Math.min(255, Math.round(r)), G = (int) Math.min(255, Math.round(gg)), B = (int) Math.min(255, Math.round(b));
        int A = (int) Math.min(255, Math.round(Math.max(R, Math.max(G, B)) * 0.10));
        if (R + G + B > 0) A = Math.max(1, A);
        return A << 24 | R << 16 | G << 8 | B;
    }

    /** Texture dynamique filtrée en linéaire (la lueur étirée ne fait pas d'escaliers). */
    private static final class Lineaire extends DynamicTexture {
        Lineaire(String nom, NativeImage img) {
            super(() -> nom, img);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
