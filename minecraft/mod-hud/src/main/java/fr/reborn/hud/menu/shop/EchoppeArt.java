package fr.reborn.hud.menu.shop;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Textures de l'Échoppe ({@code textures/gui/echoppe/*}, générées par {@code tools/gen_echoppe_textures.py} ; tailles
 * et ancres = contrat en tête du générateur). 1 texel = 1 GUI ; le texte est posé par le code ({@link DaKit}).
 */
public final class EchoppeArt {

    private EchoppeArt() {}

    static Identifier tex(String n) { return DaKit.tex("echoppe", n); }

    public static final Identifier MUR = tex("mur"), NOREN = tex("noren"), BLASON = tex("blason"),
        CHOCHIN = tex("chochin"), HALO = tex("halo"), ENSEIGNE = tex("enseigne"), PLANCHE = tex("planche"),
        ONGLETS = tex("onglets"), ETIQUETTES = tex("etiquettes"), BOUTONS = tex("boutons"), ICONES = tex("icones"),
        ESTRADE = tex("estrade"), PIECES = tex("pieces"), VIGNETTE = tex("vignette"), ETAGERE = tex("etagere");

    public static final int MUR_W = 1280, MUR_H = 720, SOL = 64;

    /** Mur de planches et sol, centré, calé en bas de l'écran. */
    public static void mur(GuiGraphicsExtractor g, int w, int h) {
        int x = w / 2 - MUR_W / 2, y = h - MUR_H;
        if (y > 0) g.fill(0, 0, w, y, 0xFF0A0505);
        DaKit.blit(g, MUR, x, y, 0, 0, MUR_W, MUR_H, MUR_W, MUR_H);
        if (x > 0) {
            g.fill(0, 0, x, h, 0xFF0A0505);
            g.fill(x + MUR_W, 0, w, h, 0xFF0A0505);
        }
    }

    /** Noren sur toute la largeur : hampe, lés balancés d'1 GUI, blason (pièce de ryo) au centre. {@code nh} = bas du noren. */
    public static void noren(GuiGraphicsExtractor g, int w, int nh, float time) {
        int y0 = nh - 36;
        // une frise de ~70 blits : un seul élément GUI (cf. LotBlit)
        fr.reborn.hud.ui.LotBlit lot = fr.reborn.hud.ui.LotBlit.ouvrir(g, NOREN, 96, 40);
        for (int x = 0; x < w; x += 16) lot.blit(x, Math.max(0, y0 - 3), 80, 0, Math.min(16, w - x), 5);
        int n = (w + 79) / 80;
        int x0 = w / 2 - n * 40;
        for (int i = 0; i < n; i++) {
            int sway = Math.round((float) Math.sin(time * 1.4f + i * 0.8f) * 1.2f);
            lot.blit(x0 + i * 80, y0 + Math.max(0, sway), 0, 0, 80, 36);
        }
        lot.fermer();
        DaKit.blit(g, BLASON, w / 2 - 14, nh - 32, 0, 0, 28, 28, 28, 28);
    }

    /** Halo en crans teint (64 × m, m ≥ 1), centré. */
    public static void halo(GuiGraphicsExtractor g, float cx, float cy, int m, int argb) {
        int s = 64 * Math.max(1, m);
        DaKit.etire(g, HALO, Math.round(cx - s / 2f), Math.round(cy - s / 2f), s, s, 64, 64, argb);
    }

    /** Chōchin (24×38) pendu en (cx, y), balancé de {@code ang} radians. */
    public static void chochin(GuiGraphicsExtractor g, float cx, int y, float ang, boolean vive) {
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().rotate(ang);
        DaKit.blit(g, CHOCHIN, -12, 0, vive ? 24 : 0, 0, 24, 38, 48, 38);
        g.pose().popMatrix();
    }

    /** Planche laquée (coupe) : boîte (x, y, w, h) ≤ 312×452. */
    public static void planche(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, PLANCHE, x - 2, y - 2, w + 8, h + 8, 0, 0, 320, 460, 18, 18, 320, 460);
    }

    /** Onglet pendu (plaque 16 de haut, largeur w) : 0 repos, 1 survol, 2 actif. */
    public static void onglet(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, ONGLETS, x - 1, y - 1, w + 3, 0, etat * 20, 140, 20, 6, 140, 60);
    }

    /** Étiquette de papier (18 de haut) : 0 normal, 1 survol, 2 choisie, 3 choisie survolée. */
    public static void etiquette(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, ETIQUETTES, x - 1, y - 1, w + 3, 0, etat * 22, 300, 22, 8, 300, 88);
    }

    /** Bouton-plaque (20 de haut) : 0 normal, 1 survol, 2 rouge, 3 rouge survol, 4 inactif. */
    public static void bouton(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, BOUTONS, x - 1, y - 1, w + 3, 0, etat * 24, 340, 24, 6, 340, 120);
    }

    public static final int IC_ROBE = 0, IC_CARTE = 16, IC_SCEAU_POSSEDE = 44, IC_SCEAU_PORTE = 60;

    public static void icone14(GuiGraphicsExtractor g, int u, int x, int y) {
        DaKit.blit(g, ICONES, x, y, u, 0, 14, 14, 96, 16);
    }

    public static void ryo(GuiGraphicsExtractor g, int x, int y) {
        DaKit.blit(g, ICONES, x, y, 32, 0, 10, 10, 96, 16);
    }

    /** Étagère de tissus (128×100) posée sur le mur, pied en y (planche basse). */
    public static void etagere(GuiGraphicsExtractor g, int x, int pied) {
        DaKit.blit(g, ETAGERE, x, pied - 98, 0, 0, 128, 100, 128, 100);
    }

    public static void estrade(GuiGraphicsExtractor g, int cx, int pieds) {
        DaKit.blit(g, ESTRADE, cx - 92, pieds - 12, 0, 0, 184, 34, 184, 34);
    }

    public static void rail(GuiGraphicsExtractor g, int x, int y, int h) {
        for (int yy = y; yy < y + h; yy += 40) DaKit.blit(g, PIECES, x, yy, 404, 0, 4, Math.min(40, y + h - yy), 420, 64);
    }

    public static void curseur(GuiGraphicsExtractor g, int x, int y, int h) {
        DaKit.blit(g, PIECES, x, y, 410, 0, 6, 2, 420, 64);
        for (int yy = y + 2; yy < y + h - 2; yy += 12) DaKit.blit(g, PIECES, x, yy, 410, 2, 6, Math.min(12, y + h - 2 - yy), 420, 64);
        DaKit.blit(g, PIECES, x, y + h - 2, 410, 14, 6, 2, 420, 64);
    }

    public static void toast(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.coupeH(g, PIECES, x, y, w + 4, 0, 20, 400, 18, 6, 420, 64);
    }

    public static void bourse(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.coupeH(g, PIECES, x, y, w + 2, 0, 42, 160, 18, 6, 420, 64);
    }

    /** Aides (cabochons) calées à droite en xDroite. */
    public static void aides(GuiGraphicsExtractor g, Font f, String[][] items, float xDroite, float ymil) {
        DaKit.aides(g, f, PIECES, 0, 0, 420, 64, items, xDroite, ymil, 1f);
    }

    /** Fil d'attache vertical (cordelette d'or, 1 GUI). */
    public static void fil(GuiGraphicsExtractor g, int x, int y0, int y1) {
        fr.reborn.hud.ui.LotGui lot = fr.reborn.hud.ui.LotGui.ouvrir(g);
        for (int y = y0; y < y1; y++) lot.fill(x, y, x + 1, y + 1, (y & 1) == 0 ? 0xFFE8C27A : 0xFFC8A05A);
        lot.fermer();
    }
}
