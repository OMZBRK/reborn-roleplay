package fr.reborn.hud.menu.character.perso;

import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.ui.LotBlit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Atlas des petits effets animés des écrans perso (lucioles, reflets, traînées, braises de chakra), construit UNE
 * fois en mémoire : chaque luciole ou braise est un seul blit de cette texture (au lieu d'une dizaine de
 * rectangles et d'ovales), et tous ces blits partagent la même texture, donc le même lot de dessin.
 *
 * <p>Densité : 4 texels par GUI (halos lisses jusqu'à l'échelle 4). Disposition (texels) : luciole moyenne 80×80 en
 * (0, 0) ; luciole proche 160×160 en (80, 0) ; point 4×4 en (0, 80) ; reflet 4×20 en (8, 80) ; braises : une rangée
 * de 20 texels par couleur à partir de y = 160, 4 variantes de 12×20 (petite, grosse, petite qui monte, grosse qui
 * monte).
 */
final class PersoSprites {

    private PersoSprites() {}

    private static final int W = 256, H = 160 + 12 * 20, D = 4;
    private static final Identifier ID = Identifier.fromNamespaceAndPath("reborn", "perso/sprites");
    private static NativeImage img;
    private static DynamicTexture tex;
    private static final Map<Integer, Integer> RANGEES = new HashMap<>();

    private static void pret() {
        if (tex != null) return;
        img = new NativeImage(W, H, true);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.setPixel(x, y, 0);
        // luciole moyenne : halo(9 GUI, 3 crans, 0,13) centré sur le milieu du pixel cœur (10, 10)
        halo(0, 0, 10.5f, 10.5f, 9, PersoArt.LUCIOLE, 0.13f, 3);
        croix(0, 0, 10, 10, 1, PersoArt.LUCIOLE, 0.4f);
        carre(0, 0, 10, 10, 1, PersoArt.LUCIOLE_COEUR, 1f);
        // luciole proche : halo(18, 3 crans, 0,15) + halo(6, 2 crans, 0,16) centrés en (20, 20), cœur 2×2 en (19, 19)
        halo(80, 0, 20f, 20f, 18, PersoArt.LUCIOLE, 0.15f, 3);
        halo(80, 0, 20f, 20f, 6, 0xFFF8C0, 0.16f, 2);
        croix(80, 0, 19, 19, 2, PersoArt.LUCIOLE, 0.55f);
        carre(80, 0, 19, 19, 2, PersoArt.LUCIOLE_COEUR, 1f);
        // point (lucioles lointaines, traînées) et reflet (3 tirets)
        carre(0, 80, 0, 0, 1, PersoArt.LUCIOLE, 1f);
        for (int j = 0; j < 3; j++) carre(8, 80, 0, j * 2, 1, PersoArt.LUCIOLE, 0.22f - j * 0.06f);
        tex = new DynamicTexture(() -> "reborn-perso-sprites", img);
        Minecraft.getInstance().getTextureManager().register(ID, tex);
    }

    /** Rangée des braises d'une couleur (créée à la première demande). */
    private static int rangee(int rgb) {
        pret();
        Integer r = RANGEES.get(rgb & 0xFFFFFF);
        if (r != null) return r;
        int n = RANGEES.size();
        if (n >= 12) return RANGEES.values().iterator().next();
        int y0 = 160 + n * 20;
        int pale = PersoArt.mix(rgb, 0xFFEEC8, 0.6f), coeur = PersoArt.mix(rgb, 0xFFEEC8, 0.3f);
        for (int v = 0; v < 4; v++) {
            int x0 = v * 12;
            boolean gros = (v & 1) == 1, monte = v >= 2;
            carre(x0, y0, 1, 0, 1, gros ? pale : coeur, 1f);
            if (gros) croix(x0, y0, 1, 0, 1, rgb, 0.42f);
            if (monte) {
                carre(x0, y0, 1, 2, 1, rgb, 0.45f);
                carre(x0, y0, 1, 4, 1, rgb, 0.2f);
            }
        }
        RANGEES.put(rgb & 0xFFFFFF, y0);
        tex.upload();
        return y0;
    }

    /* ------------------------------------------------------------------ dessin */

    /**
     * Lot de sprites de l'atlas : toutes les lucioles (ou braises) d'un passage ne font qu'un élément GUI (DA §6,
     * règle 7) ; sommets identiques à ceux des blits séparés.
     */
    static LotBlit lot(GuiGraphicsExtractor g) {
        pret();
        return LotBlit.ouvrir(g, ID, W, H);
    }

    static void lucioleMoyenne(LotBlit l, int x, int y, float a) {
        poser(l, x - 10, y - 10, 0, 0, 20, 20, a);
    }

    static void lucioleProche(LotBlit l, int x, int y, float a) {
        poser(l, x - 19, y - 19, 80, 0, 40, 40, a);
    }

    static void point(LotBlit l, int x, int y, float a) {
        poser(l, x, y, 0, 80, 1, 1, a);
    }

    static void reflet(LotBlit l, int x, int y, float a) {
        poser(l, x, y, 8, 80, 1, 5, a);
    }

    static void braise(LotBlit l, int rgb, int variante, int x, int y, float a) {
        int y0 = rangee(rgb);
        poser(l, x - 1, y, variante * 12, y0, 3, 5, a);
    }

    private static void poser(LotBlit l, int x, int y, int u, int v, int wGui, int hGui, float a) {
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        if (al == 0) return;
        l.etire(x, y, wGui, hGui, u, v, wGui * D, hGui * D, (al << 24) | 0xFFFFFF);
    }

    static void lucioleMoyenne(GuiGraphicsExtractor g, int x, int y, float a) {
        poser(g, x - 10, y - 10, 0, 0, 20, 20, a);
    }

    static void lucioleProche(GuiGraphicsExtractor g, int x, int y, float a) {
        poser(g, x - 19, y - 19, 80, 0, 40, 40, a);
    }

    static void point(GuiGraphicsExtractor g, int x, int y, float a) {
        poser(g, x, y, 0, 80, 1, 1, a);
    }

    static void reflet(GuiGraphicsExtractor g, int x, int y, float a) {
        poser(g, x, y, 8, 80, 1, 5, a);
    }

    /** Braise : variante 0 petite, 1 grosse, 2 petite qui monte, 3 grosse qui monte ; (x, y) = pixel cœur. */
    static void braise(GuiGraphicsExtractor g, int rgb, int variante, int x, int y, float a) {
        int y0 = rangee(rgb);
        poser(g, x - 1, y, variante * 12, y0, 3, 5, a);
    }

    private static void poser(GuiGraphicsExtractor g, int x, int y, int u, int v, int wGui, int hGui, float a) {
        pret();
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        if (al == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, u, v, wGui, hGui, wGui * D, hGui * D, W, H, (al << 24) | 0xFFFFFF);
    }

    /* ------------------------------------------------------------------ construction (une fois) */

    /** Composition « par-dessus » d'une couleur à l'alpha a sur le texel (x, y). */
    private static void sur(int x, int y, int rgb, float a) {
        if (a <= 0 || x < 0 || y < 0 || x >= W || y >= H) return;
        int o = img.getPixel(x, y);
        float ao = (o >>> 24) / 255f;
        float an = a + ao * (1 - a);
        if (an <= 0) return;
        int r = Math.round((((rgb >> 16) & 0xFF) * a + ((o >> 16) & 0xFF) * ao * (1 - a)) / an);
        int gg = Math.round((((rgb >> 8) & 0xFF) * a + ((o >> 8) & 0xFF) * ao * (1 - a)) / an);
        int b = Math.round(((rgb & 0xFF) * a + (o & 0xFF) * ao * (1 - a)) / an);
        img.setPixel(x, y, (Math.round(an * 255) << 24) | (r << 16) | (gg << 8) | b);
    }

    /** Carré de t GUI au GUI (gx, gy) de la cellule (x0, y0 en texels). */
    private static void carre(int x0, int y0, int gx, int gy, int t, int rgb, float a) {
        for (int j = 0; j < t * D; j++) for (int i = 0; i < t * D; i++) sur(x0 + gx * D + i, y0 + gy * D + j, rgb, a);
    }

    /** Les 4 bras d'1 GUI autour d'un carré t×t en (gx, gy). */
    private static void croix(int x0, int y0, int gx, int gy, int t, int rgb, float a) {
        for (int k = 0; k < t; k++) {
            carre(x0, y0, gx - 1, gy + k, 1, rgb, a);
            carre(x0, y0, gx + t, gy + k, 1, rgb, a);
            carre(x0, y0, gx + k, gy - 1, 1, rgb, a);
            carre(x0, y0, gx + k, gy + t, 1, rgb, a);
        }
    }

    /** halo_px de la maquette (cf. {@link PersoArt#halo}) : n disques nets concentriques, centre en GUI (cx, cy). */
    private static void halo(int x0, int y0, float cx, float cy, float r, int rgb, float force, int niveaux) {
        float[] parts = niveaux >= 3 ? new float[]{0.27f, 0.53f, 1f} : niveaux == 2 ? new float[]{0.4f, 1f} : new float[]{1f};
        int n = parts.length;
        float prev = 0f;
        for (int i = 0; i < n; i++) {
            float k = (n - i) / (float) n;
            float a = (parts[i] - prev) * force;
            prev = parts[i];
            float rr = r * k * D;
            int ccx = Math.round(cx * D), ccy = Math.round(cy * D);
            for (int j = (int) -Math.ceil(rr) - 1; j <= Math.ceil(rr) + 1; j++) {
                for (int q = (int) -Math.ceil(rr) - 1; q <= Math.ceil(rr) + 1; q++) {
                    float d = (float) Math.sqrt((q + 0.5f) * (q + 0.5f) + (j + 0.5f) * (j + 0.5f));
                    float cov = Math.max(0f, Math.min(1f, rr - d + 0.5f));
                    if (cov > 0) sur(x0 + ccx + q, y0 + ccy + j, rgb, a * cov);
                }
            }
        }
    }
}
