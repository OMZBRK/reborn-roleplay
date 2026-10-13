package fr.reborn.hud.parchemin;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Textures de la bibliothèque, de la lecture et des réglages staff ({@code textures/gui/bibliotheque/*}, générées par
 * {@code tools/gen_bibliotheque_textures.py} ; tailles = contrat en tête du générateur) et petites pièces partagées
 * par les trois écrans : en-tête suspendu, sceaux et étiquettes de rang, brocart, plaques-boutons, réglettes, aides.
 */
final class BiblioArt {

    private BiblioArt() {}

    static Identifier tex(String n) { return DaKit.tex("bibliotheque", n); }

    static final Identifier ENTETE = tex("entete"), MEUBLE = tex("meuble"), CASIERS = tex("casiers"),
        FUDAS = tex("fudas"), SCEAUX = tex("sceaux"), CARTE = tex("carte"), BROCART = tex("brocart"),
        PIECES = tex("pieces"), REGLAGES = tex("reglages"), TOAST = tex("toast"), VIGNETTE = tex("vignette"),
        ROULEAU = tex("rouleau"), BAGUETTES = tex("baguettes"), KANJI = tex("kanji"), PERLES = tex("perles"),
        SIGNE = tex("signe"), BOUTON = tex("bouton"), PANNEAU = tex("panneau"), PLAQUES = tex("plaques"),
        REGLETTE = tex("reglette"), CHEVILLES = tex("chevilles"), CHAMP = tex("champ"), MINI = tex("mini");

    /** Dimensions du meuble (texture 376×274) et de ses casiers. */
    static final int MEUBLE_W = 376, MEUBLE_H = 274, CW = 104, CH = 62;

    static int rang(char r) {
        int i = "DCBAS".indexOf(r);
        return i < 0 ? 0 : i;
    }

    /** Voile sur le jeu + vignette (le jeu reste visible derrière). */
    static void fond(GuiGraphicsExtractor g, int w, int h, float voile) {
        g.fill(0, 0, w, h, DaKit.argb(0x080406, voile));
        DaKit.etire(g, VIGNETTE, 0, 0, w, h, 160, 90, DaKit.blanc(0.9f));
    }

    /** En-tête suspendu : plaque laquée, nom en ArcadePix ×1 crème, sous-titre en Inter 7 sous la plaque. */
    static void entete(GuiGraphicsExtractor g, Font f, int w, Component nom, Component sous) {
        int x = w / 2 - 180;
        DaKit.blit(g, ENTETE, x, 0, 0, 0, 360, 48, 360, 48);
        DaKit.arcX1(g, f, nom, w / 2f, 19, DaKit.CREME, 1, true);
        if (sous != null) DaKit.inter7o(g, f, sous, w / 2f, 46, DaKit.argb(0xE8DCC0, 0.75f), 1);
    }

    /** Étiquette de rang (fuda) : fil depuis (x + 6, y), étiquette 10×13 en (x + 1, y + 6). */
    static void fuda(GuiGraphicsExtractor g, int x, int y, char r) {
        DaKit.blit(g, FUDAS, x, y, rang(r) * 14, 0, 14, 20, 70, 20);
    }

    /** Sceau de rang de 18 GUI centré en (cx, cy). */
    static void sceau18(GuiGraphicsExtractor g, int cx, int cy, char r) {
        DaKit.blit(g, SCEAUX, cx - 9, cy - 9, rang(r) * 20, 0, 20, 20, 240, 28);
    }

    /** Sceau de rang de 26 GUI centré en (cx, cy). */
    static void sceau26(GuiGraphicsExtractor g, int cx, int cy, char r) {
        DaKit.blit(g, SCEAUX, cx - 13, cy - 13, 100 + rang(r) * 28, 0, 28, 28, 240, 28);
    }

    /** Bande de brocart du rang, 8 GUI de haut, largeur w (≤ 440). */
    static void brocart(GuiGraphicsExtractor g, int x, int y, int w, char r) {
        DaKit.coupeH(g, BROCART, x, y, w, 0, rang(r) * 8, 440, 8, 4, 440, 40);
    }

    /** Bande de brocart de hauteur réduite (barres de la simulation). */
    static void brocartH(GuiGraphicsExtractor g, int x, int y, int w, int h, char r) {
        if (w <= 0) return;
        DaKit.blit(g, BROCART, x, y, 0, rang(r) * 8 + 1, Math.min(w, 440), h, 440, 40);
    }

    /** Trait de pinceau vermillon (largeur ≤ 160). */
    static void trait(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.blit(g, PIECES, x, y, 0, 0, Math.min(160, w), 4, 210, 50);
    }

    /** Aides en bas à droite (cabochons de touches). */
    static void aides(GuiGraphicsExtractor g, Font f, int w, int h, String[][] items) {
        DaKit.aides(g, f, PIECES, 0, 6, 210, 50, items, w - 18, h - 14, 1f);
    }

    static int cabochon(GuiGraphicsExtractor g, Font f, String t, int x, float ymil, float a) {
        return DaKit.cabochon(g, f, PIECES, 0, 6, 210, 50, DaKit.arcade(t), x, ymil, a);
    }

    /** Jauge laquée (sacoche) : étiquette et valeur au-dessus, rainure 200 GUI, remplissage or (laque si presque plein). */
    static void jauge(GuiGraphicsExtractor g, Font f, int x, int y, float v, DaKit.Etiq etiq, Component valeur) {
        DaKit.etiq(g, f, etiq, x, y + 3, DaKit.OR_ETIQ);
        DaKit.inter8(g, f, valeur, x + 200, y + 3, DaKit.CREME_VAL, 2);
        DaKit.blit(g, PIECES, x - 1, y + 8, 0, 20, 203, 11, 210, 50);
        int fw = Math.round(196 * Math.max(0, Math.min(1, v)));
        DaKit.blit(g, PIECES, x + 2, y + 10, 0, v > 0.9f ? 42 : 34, fw, 6, 210, 50);
    }

    /** Plaque-bouton à la largeur voulue : état 0 normal, 1 survol, 2 actif (laque rouge), 3 inactif. */
    static void plaque(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, PLAQUES, x - 1, y - 1, w + 4, 0, etat * 20, 164, 20, 6, 164, 80);
    }

    /** Plaque-bouton + libellé ArcadePix ×0,5 centré. */
    static void plaque(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, String lib, boolean actif, boolean survol,
                       boolean ok) {
        int etat = !ok ? 3 : actif ? 2 : survol ? 1 : 0;
        if (h >= 16) plaque(g, x, y + (h - 16) / 2, w, etat);
        else plaque(g, x, y - (16 - h) / 2, w, etat);
        int col = !ok ? DaKit.INACTIF : actif || survol ? DaKit.CREME : 0xFFD8CCB0;
        DaKit.arcFin(g, f, DaKit.arcade(lib), x + w / 2f, y + h / 2f - 1.75f, col, 1, ok);
    }

    /** Réglette or : étiquette, valeur, rainure graduée, remplissage et curseur. {@code v} ∈ [0, 1]. */
    static void reglette(GuiGraphicsExtractor g, Font f, int x, int y, int w, float v, String lib, String valeur, boolean hot) {
        DaKit.etiq(g, f, DaKit.etiq(f, lib), x, y + 3, DaKit.OR_ETIQ);
        DaKit.inter8(g, f, DaKit.inter(valeur), x + w, y + 3, DaKit.OR_CLAIR, 2);
        int ry = y + 9;
        DaKit.coupeH(g, REGLETTE, x, ry, w, 0, 0, 404, 10, 6, 404, 30);
        v = Math.max(0, Math.min(1, v));
        int fw = Math.round((w - 4) * v);
        DaKit.blit(g, REGLETTE, x + 2, ry + 3, 2, 12, fw, 4, 404, 30);
        int kx = x + 2 + fw - 3;
        DaKit.blit(g, REGLETTE, kx, ry - 1, hot ? 8 : 0, 18, 7, 12, 404, 30);
    }

    /** Cheville − / + (12×11). */
    static void cheville(GuiGraphicsExtractor g, int x, int y, boolean plus, boolean hot) {
        DaKit.blit(g, CHEVILLES, x, y, (plus ? 26 : 0) + (hot ? 13 : 0), 0, 13, 12, 52, 12);
    }
}
