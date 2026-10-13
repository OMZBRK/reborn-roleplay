package fr.reborn.hud.staff;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Décor fixe du Poste de garde et de l'Infirmerie (DA §6) : textures générées par {@code tools/gen_garde_textures.py}
 * (contrat des coordonnées dans l'en-tête du script), posées à 1 texel = 1 GUI, par coupe quand la pièce est plus
 * petite que sa texture. Le code des écrans ne dessine que ce qui bouge ou dépend des données.
 */
final class GardeArt {

    private GardeArt() {}

    static final Identifier CADRE = DaKit.tex("garde", "cadre"), WASHI = DaKit.tex("garde", "washi"),
        ENTETE = DaKit.tex("garde", "entete"), BOUTONS = DaKit.tex("garde", "boutons"), FUDA = DaKit.tex("garde", "fuda"),
        CARTE = DaKit.tex("garde", "carte"), TANZAKU = DaKit.tex("garde", "tanzaku"), PANNEAU = DaKit.tex("garde", "panneau"),
        CARTE_CADRE = DaKit.tex("garde", "carte_cadre"), SCEAU = DaKit.tex("garde", "sceau"), PIECES = DaKit.tex("garde", "pieces");

    /** Papier maximal (le cadre et le papier ne s'étirent jamais au-delà). */
    static final int PAPIER_W = 640, PAPIER_H = 500;

    private static final int PW = 680, PH = 232;   // pieces.png

    /* ------------------------------------------------------------------ cadre, papier, en-tête */

    /** Cadre de bois autour du papier (x0, y0, w, h). */
    static void cadre(GuiGraphicsExtractor ctx, int x0, int y0, int w, int h) {
        DaKit.coupe(ctx, CADRE, x0 - 12, y0 - 12, w + 28, h + 28, 0, 0, 668, 528, 48, 48, 668, 528);
    }

    /** Papier washi (poutre laquée, bande de navigation) en (x0, y0, w, h). */
    static void papier(GuiGraphicsExtractor ctx, int x0, int y0, int w, int h) {
        DaKit.coupe(ctx, WASHI, x0, y0, w, h, 0, 0, PAPIER_W, PAPIER_H, 8, 8, PAPIER_W, PAPIER_H);
    }

    /** Papier sans la bande de navigation (fenêtres posées par-dessus). */
    static void papierNu(GuiGraphicsExtractor ctx, int x0, int y0, int w, int h) {
        DaKit.coupe(ctx, WASHI, x0, y0, w, h, 90, 0, PAPIER_W - 90, PAPIER_H, 8, 8, PAPIER_W, PAPIER_H);
    }

    /** Rouleau (jiku) au bas du papier pendant le déroulé : embouts d'or hors du papier. */
    static void jiku(GuiGraphicsExtractor ctx, int x0, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x0 - 9, y - 2, w + 18, 0, 150, 680, 9, 14, PW, PH);
    }

    /** En-tête suspendu : 0 Poste de garde, 1 Infirmerie (plaque 300 de large centrée en cx). */
    static void entete(GuiGraphicsExtractor ctx, int cx, int row) {
        DaKit.blit(ctx, ENTETE, cx - 160, 0, 0, row * 48, 320, 48, 320, 96);
    }

    /* ------------------------------------------------------------------ boutons, plaquettes */

    static final int B_NOIR = 0, B_ROUGE = 2, B_VERT = 4, B_INACTIF = 6;

    /** Bouton laqué 12 de haut (+ ombre) ; ligne de base B_*, survol ajoute 1 (sauf inactif). */
    static void bouton(GuiGraphicsExtractor ctx, int x, int y, int w, int ligne, boolean survol) {
        int r = ligne == B_INACTIF ? B_INACTIF : ligne + (survol ? 1 : 0);
        DaKit.coupeH(ctx, BOUTONS, x, y, w, 0, r * 13, 200, 13, 6, 200, 91);
    }

    /** Plaquette de hinoki (fuda) : boîte de contour (x, y, w, h), ombre comprise hors boîte. */
    static void fuda(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean survol) {
        DaKit.coupe(ctx, FUDA, x, y, w + 3, h + 3, 0, survol ? 88 : 0, 560, 88, 12, 12, 560, 176);
    }

    /** Trou percé et ficelle rouge d'une fuda, trou centré en (cx, cy). */
    static void ficelle(GuiGraphicsExtractor ctx, int cx, int cy) {
        DaKit.blit(ctx, PIECES, cx - 11, cy - 3, 0, 32, 14, 7, PW, PH);
    }

    /** Carte d'infraction (washi, bord de bois) : boîte (x, y, w, h). */
    static void carte(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean survol) {
        DaKit.coupe(ctx, CARTE, x, y, w + 2, h + 2, 0, survol ? 62 : 0, 560, 62, 8, 8, 560, 124);
    }

    /** Panneau de washi à bord de bois : boîte (x, y, w, h). */
    static void panneau(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        DaKit.coupe(ctx, PANNEAU, x, y, w + 3, h + 3, 0, 0, 303, 223, 10, 10, 304, 224);
    }

    /** Bande de papier (tanzaku) d'un blessé : boîte (x, y, w, h). */
    static void tanzaku(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        DaKit.coupe(ctx, TANZAKU, x, y, w + 3, h + 3, 0, 0, 133, 303, 10, 10, 134, 304);
    }

    /** Cadre de la carte autour de la zone (mx0, my0)–(mx1, my1). */
    static void cadreCarte(GuiGraphicsExtractor ctx, int mx0, int my0, int mx1, int my1) {
        DaKit.coupe(ctx, CARTE_CADRE, mx0 - 5, my0 - 5, mx1 - mx0 + 14, my1 - my0 + 14, 0, 0, 470, 510, 12, 12, 470, 510);
    }

    /* ------------------------------------------------------------------ petites pièces */

    /** Entrée de navigation : active (laque rouge) ou survolée (washi éclairci). */
    static void navEntree(GuiGraphicsExtractor ctx, int x, int y, int w, boolean active) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, active ? 0 : 13, 90, 13, 6, PW, PH);
    }

    static void pastille(GuiGraphicsExtractor ctx, int x, int y, int w, boolean vive) {
        DaKit.coupeH(ctx, PIECES, x, y, w, vive ? 124 : 100, 0, 24, 11, 4, PW, PH);
    }

    static void sceauVierge(GuiGraphicsExtractor ctx, int x, int y) {
        DaKit.blit(ctx, PIECES, x, y, 100, 12, 11, 11, PW, PH);
    }

    static void trait(GuiGraphicsExtractor ctx, int x, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, 26, 240, 5, 40, PW, PH);
    }

    static void losange(GuiGraphicsExtractor ctx, int x, int y) {
        DaKit.blit(ctx, PIECES, x, y, 20, 32, 5, 5, PW, PH);
    }

    /** Planche « Fermer » 124×17 (embouts compris). */
    static void fermer(GuiGraphicsExtractor ctx, int x, int y, boolean survol) {
        DaKit.blit(ctx, PIECES, x, y, survol ? 130 : 0, 40, 124, 17, PW, PH);
    }

    static void badgeGrade(GuiGraphicsExtractor ctx, int x, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, 58, 160, 14, 6, PW, PH);
    }

    static void sceauScore(GuiGraphicsExtractor ctx, int x, int y, int w, boolean fort) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, fort ? 72 : 85, 140, 13, 5, PW, PH);
    }

    static void toast(GuiGraphicsExtractor ctx, int x, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, 98, 440, 16, 6, PW, PH);
    }

    static void champ(GuiGraphicsExtractor ctx, int x, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, 114, 600, 15, 4, PW, PH);
    }

    /** Flèche 16×16 : sens −1 (gauche) ou 1 ; état 0 actif, 1 survol, 2 éteint. */
    static void fleche(GuiGraphicsExtractor ctx, int x, int y, int sens, int etat) {
        DaKit.blit(ctx, PIECES, x, y, (sens < 0 ? 0 : 48) + 16 * etat, 130, 16, 16, PW, PH);
    }

    static void perle(GuiGraphicsExtractor ctx, int x, int y, boolean allumee) {
        DaKit.blit(ctx, PIECES, x, y, allumee ? 107 : 100, 130, 6, 6, PW, PH);
    }

    static void drapeau(GuiGraphicsExtractor ctx, int x, int y, boolean on) {
        DaKit.blit(ctx, PIECES, x, y, on ? 128 : 114, 130, 13, 13, PW, PH);
    }

    /** Coupelle d'encens 12×4, centrée en cx, haut en y. */
    static void coupelle(GuiGraphicsExtractor ctx, int cx, int y) {
        DaKit.blit(ctx, PIECES, cx - 6, y, 144, 131, 12, 4, PW, PH);
    }

    /** Corde de paille torsadée (6 de haut). */
    static void corde(GuiGraphicsExtractor ctx, int x, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, 160, 680, 6, 8, PW, PH);
    }

    static void rainure(GuiGraphicsExtractor ctx, int x, int y, int w) {
        DaKit.coupeH(ctx, PIECES, x, y, w, 0, 168, 300, 4, 3, PW, PH);
    }

    static void poignee(GuiGraphicsExtractor ctx, int cx, int y) {
        DaKit.blit(ctx, PIECES, cx - 3, y, 0, 174, 7, 9, PW, PH);
    }

    /** Filigrane 64×64 (0 番, 1 医). */
    static void filigrane(GuiGraphicsExtractor ctx, int x, int y, int kj) {
        DaKit.blit(ctx, PIECES, x, y, 320 + 64 * kj, 168, 64, 64, PW, PH);
    }

    static void sceau(GuiGraphicsExtractor ctx, int x, int y, int argb) {
        DaKit.blit(ctx, SCEAU, x, y, 0, 0, 64, 64, 64, 64, argb);
    }
}
