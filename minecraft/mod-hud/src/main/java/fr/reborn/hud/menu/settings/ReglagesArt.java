package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Textures des Paramètres Reborn ({@code textures/gui/reglages/*}, générées par {@code tools/gen_reglages_textures.py} ;
 * tailles et ancres = contrat en tête du générateur). 1 texel = 1 GUI ; le texte est posé par le code ({@link DaKit}).
 * Partagé par l'écran ({@code menu/screens/ConfigShellScreen}), les onglets et leurs contrôles.
 */
public final class ReglagesArt {

    private ReglagesArt() {}

    static Identifier tex(String n) { return DaKit.tex("reglages", n); }

    public static final Identifier FOND = tex("fond"), ENTETE = tex("entete"), ONGLETS = tex("onglets"),
        FERMER = tex("fermer"), PLANCHE = tex("planche"), REGLETTE = tex("reglette"), INTERRUPTEUR = tex("interrupteur"),
        SEGMENTS = tex("segments"), BOUTONS = tex("boutons"), PIECES = tex("pieces"), VIGNETTE = tex("vignette");

    public static final int FOND_W = 1280, FOND_H = 720;

    /** Pièce de laque et byōbu, centrée, calée en haut. */
    public static void fond(GuiGraphicsExtractor g, int w, int h) {
        g.fill(0, 0, w, h, 0xFF0A0608);
        DaKit.blit(g, FOND, w / 2 - FOND_W / 2, 0, 0, 0, FOND_W, FOND_H, FOND_W, FOND_H);
    }

    /** En-tête suspendu (plaque 520 de large), centré en cx. */
    public static void entete(GuiGraphicsExtractor g, int cx) {
        DaKit.blit(g, ENTETE, cx - 270, 0, 0, 0, 540, 48, 540, 48);
    }

    /** Onglet pendu (cellule 26×36, attache en (tx, y)) : 0 repos, 1 survol, 2 actif. */
    public static void onglet(GuiGraphicsExtractor g, int tx, int y, int etat) {
        DaKit.blit(g, ONGLETS, tx - 13, y, etat * 26, 0, 26, 36, 78, 36);
    }

    /** Plaque 閉 (cellule 26×38, attache en (tx, 0)). */
    public static void fermer(GuiGraphicsExtractor g, int tx, boolean survol) {
        DaKit.blit(g, FERMER, tx - 13, 0, survol ? 26 : 0, 0, 26, 38, 52, 38);
    }

    /** Planche laquée (coupe) : boîte (x, y, w, h) ≤ 616×648. */
    public static void planche(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, PLANCHE, x - 2, y - 2, w + 8, h + 8, 0, 0, 624, 656, 18, 18, 624, 656);
    }

    /** Réglette d'or : rainure (x, y, w×10), remplissage v ∈ [0, 1], curseur (survolé ou non). */
    public static void reglette(GuiGraphicsExtractor g, int x, int y, int w, float v, boolean hot) {
        DaKit.coupeH(g, REGLETTE, x, y, w, 0, 0, 304, 10, 6, 304, 30);
        v = Math.max(0, Math.min(1, v));
        int fw = Math.round((w - 4) * v);
        DaKit.blit(g, REGLETTE, x + 2, y + 3, 2, 12, Math.min(300, fw), 4, 304, 30);
        DaKit.blit(g, REGLETTE, x + 2 + fw - 3, y - 1, hot ? 8 : 0, 18, 7, 12, 304, 30);
    }

    /** Interrupteur laqué 40×14 en (x, y). */
    public static void interrupteur(GuiGraphicsExtractor g, int x, int y, boolean marche, boolean survol) {
        int r = (marche ? 1 : 0) + (survol ? 2 : 0);
        DaKit.blit(g, INTERRUPTEUR, x - 1, y - 1, 0, r * 16, 44, 16, 44, 64);
    }

    /** Plaque segmentée (16 de haut, largeur w) : 0 repos, 1 survol, 2 actif. */
    public static void segment(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, SEGMENTS, x - 1, y - 1, w + 3, 0, etat * 20, 160, 20, 6, 160, 60);
    }

    /** Bouton-plaque (20 de haut, largeur w ≤ 596) : 0 normal, 1 survol, 2 inactif, 3 rouge. */
    public static void bouton(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, BOUTONS, x - 1, y - 1, w + 3, 0, etat * 24, 600, 24, 6, 600, 96);
    }

    /** Flèche d'or 5×7 (bouton d'action). */
    public static void fleche(GuiGraphicsExtractor g, int x, int y, float a) {
        DaKit.blit(g, PIECES, x, y, 0, 20, 5, 7, 120, 40, DaKit.blanc(a));
    }

    /** Filet d'or à losanges de x0 à x1 (y = ligne du filet). */
    public static void filet(GuiGraphicsExtractor g, int x0, int x1, int y) {
        if (x1 - x0 < 8) return;
        g.fill(x0 + 3, y, x1 - 3, y + 1, 0xFF6A4E24);
        DaKit.blit(g, PIECES, x0, y - 1, 1, 14, 3, 3, 120, 40);
        DaKit.blit(g, PIECES, x1 - 3, y - 1, 1, 14, 3, 3, 120, 40);
    }

    public static void rail(GuiGraphicsExtractor g, int x, int y, int h) {
        for (int yy = y; yy < y + h; yy += 40) DaKit.blit(g, PIECES, x, yy, 100, 0, 4, Math.min(40, y + h - yy), 120, 40);
    }

    public static void curseur(GuiGraphicsExtractor g, int x, int y, int h) {
        DaKit.blit(g, PIECES, x, y, 108, 0, 6, 2, 120, 40);
        for (int yy = y + 2; yy < y + h - 2; yy += 12) DaKit.blit(g, PIECES, x, yy, 108, 2, 6, Math.min(12, y + h - 2 - yy), 120, 40);
        DaKit.blit(g, PIECES, x, y + h - 2, 108, 14, 6, 2, 120, 40);
    }
}
