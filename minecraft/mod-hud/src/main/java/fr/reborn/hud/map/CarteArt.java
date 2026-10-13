package fr.reborn.hud.map;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Habillage de la carte du monde ({@code textures/gui/carte/*}, généré par {@code tools/gen_carte_textures.py} ;
 * tailles et ancres = contrat en tête du générateur) : cadre du rouleau, en-tête, rose des vents, cartes de washi,
 * boutons laqués et aides. 1 texel = 1 GUI ; le texte est posé par le code ({@link DaKit}).
 */
final class CarteArt {

    private CarteArt() {}

    static Identifier tex(String n) { return DaKit.tex("carte", n); }

    static final Identifier CADRE = tex("cadre"), ENTETE = tex("entete"), BOUSSOLE = tex("boussole"),
        WASHI = tex("washi"), ETIQUETTE = tex("etiquette"), BOUTONS = tex("boutons"), PIECES = tex("pieces"),
        HALO = tex("halo"), VIGNETTE = tex("vignette");

    /** Hauteur des baguettes du rouleau (haut et bas) et largeur des lisières. */
    static final int BAGUETTE = 9, LISIERE = 8;

    /** Cadre du rouleau : lisières de brocart sur les côtés, baguettes laquées haut et bas, embouts d'or. */
    static void cadre(GuiGraphicsExtractor g, int w, int h) {
        for (int y = BAGUETTE; y < h - BAGUETTE; y += 24) {
            int hh = Math.min(24, h - BAGUETTE - y);
            DaKit.blit(g, CADRE, 0, y, 24, 24, LISIERE, hh, 64, 64);
            DaKit.blit(g, CADRE, w - LISIERE, y, 34, 24, LISIERE, hh, 64, 64);
        }
        for (int x = 0; x < w; x += 64) {
            int ww = Math.min(64, w - x);
            DaKit.blit(g, CADRE, x, 0, 0, 0, ww, 12, 64, 64);
            DaKit.blit(g, CADRE, x, h - 12, 0, 12, ww, 12, 64, 64);
        }
        DaKit.blit(g, CADRE, 0, -2, 0, 24, 10, 14, 64, 64);
        DaKit.blit(g, CADRE, w - 10, -2, 12, 24, 10, 14, 64, 64);
        DaKit.blit(g, CADRE, 0, h - 12, 0, 24, 10, 14, 64, 64);
        DaKit.blit(g, CADRE, w - 10, h - 12, 12, 24, 10, 14, 64, 64);
    }

    static void entete(GuiGraphicsExtractor g, int cx, int y) {
        DaKit.blit(g, ENTETE, cx - 170, y, 0, 0, 340, 48, 340, 48);
    }

    static void boussole(GuiGraphicsExtractor g, int x, int y) {
        DaKit.blit(g, BOUSSOLE, x, y, 0, 0, 48, 48, 48, 48);
    }

    /** Carte de washi (coupe) : boîte (x, y, w, h) ≤ 192×112, bande vermillon en haut. */
    static void washi(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, WASHI, x - 2, y - 2, w + 8, h + 8, 0, 0, 200, 120, 12, 12, 200, 120);
    }

    /** Petite étiquette de washi (16 de haut). */
    static void etiquette(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.coupeH(g, ETIQUETTE, x - 1, y - 1, w + 3, 0, 0, 320, 20, 6, 560, 40);
    }

    /** Bandeau laqué (16 de haut) sous les aides. */
    static void bandeau(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.coupeH(g, ETIQUETTE, x - 1, y - 1, w + 3, 0, 20, 560, 20, 6, 560, 40);
    }

    static void bouton(GuiGraphicsExtractor g, int x, int y, int w, boolean survol) {
        DaKit.coupeH(g, BOUTONS, x - 1, y - 1, w + 3, 0, survol ? 24 : 0, 160, 24, 6, 160, 48);
    }

    static void sceau(GuiGraphicsExtractor g, int x, int y) {
        DaKit.blit(g, PIECES, x, y, 44, 0, 9, 9, 64, 16);
    }

    static void halo(GuiGraphicsExtractor g, float cx, float cy, int m, int argb) {
        int s = 64 * Math.max(1, m);
        DaKit.etire(g, HALO, Math.round(cx - s / 2f), Math.round(cy - s / 2f), s, s, 64, 64, argb);
    }

    static void vignette(GuiGraphicsExtractor g, int w, int h) {
        DaKit.etire(g, VIGNETTE, 0, 0, w, h, 160, 90, DaKit.blanc(1f));
    }

    static void aides(GuiGraphicsExtractor g, Font f, String[][] items, float xDroite, float ymil) {
        DaKit.aides(g, f, PIECES, 0, 0, 64, 16, items, xDroite, ymil, 1f);
    }

    /** Largeur totale des aides (pour les centrer). */
    static float largeurAides(Font f, String[][] items) {
        float total = 10f * (items.length - 1);
        for (String[] it : items) {
            total += (int) Math.ceil(f.width(DaKit.arcade(it[0])) * 0.5f) + 6;
            if (it[1] != null) total += 4 + DaKit.largeur8(f, DaKit.inter(it[1]));
        }
        return total;
    }
}
