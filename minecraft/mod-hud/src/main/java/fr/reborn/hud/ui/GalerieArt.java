package fr.reborn.hud.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Textures de la galerie de captures ({@code textures/gui/galerie/*}, générées par
 * {@code tools/gen_galerie_textures.py} ; tailles et ancres = contrat en tête du générateur). Signature : les tirages
 * pincés sur des fils. Partagé par la galerie, la vue détail, la retouche et le partage.
 */
public final class GalerieArt {

    private GalerieArt() {}

    private static Identifier tex(String n) { return DaKit.tex("galerie", n); }

    public static final Identifier CARTOUCHE = tex("cartouche"), TIRAGE = tex("tirage"), GRAND = tex("grand"),
        BOITE = tex("boite"), OUTILS = tex("outils"), PELLICULE = tex("pellicule"), PIECES = tex("pieces");

    /** Passe-partout de washi (x, y, w, h) ≤ 188×128, ajouté au lot (coupe, 4 morceaux). */
    public static void tirage(LotBlit l, int x, int y, int w, int h) {
        w = Math.min(w, 188);
        h = Math.min(h, 128);
        int c = 8, lw = w + 3 - c, lh = h + 4 - c;
        l.blit(x - 1, y - 1, 0, 0, lw, lh);
        l.blit(x - 1 + lw, y - 1, 192 - c, 0, c, lh);
        l.blit(x - 1, y - 1 + lh, 0, 132 - c, lw, c);
        l.blit(x - 1 + lw, y - 1 + lh, 192 - c, 132 - c, c, c);
    }

    /** Planche laquée (x, y, w, h) ≤ 380×240 (menus, confirmations, partage). */
    public static void boite(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, BOITE, x - 2, y - 2, w + 8, h + 8, 0, 0, 388, 248, 18, 18, 388, 248);
    }

    /** Grande planche à passe-partout (x, y, w, h) ≤ 860×476 (vue détail). */
    public static void grand(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, GRAND, x - 2, y - 2, w + 8, h + 8, 0, 0, 868, 484, 24, 24, 868, 484);
    }

    /** Cartouche du titre (176×30) centré en cx : titre ArcadePix, sous-titre Inter 7. */
    public static void cartouche(GuiGraphicsExtractor g, Font f, int cx, int y, String titre, String sous) {
        DaKit.blit(g, CARTOUCHE, cx - 90, y - 2, 0, 0, 184, 38, 184, 38);
        DaKit.arcX1(g, f, DaKit.arcade(titre), cx, y + 6, DaKit.OR_CLAIR, 1, true);
        if (sous != null) DaKit.inter7(g, f, DaKit.inter(sous), cx, y + 21, DaKit.CREME_VAL, 1);
    }

    /** Ligne de menu contextuel survolée (largeur w, 14 de haut). */
    public static void ligne(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.coupeH(g, PIECES, x, y, w, 0, 26, 100, 14, 6, 136, 48);
    }

    /** Plaquette fléchée 24×24 (gauche / droite, survolée ou non). */
    public static void fleche(GuiGraphicsExtractor g, int x, int y, boolean droite, boolean survol) {
        DaKit.blit(g, PIECES, x, y, 40 + (droite ? 48 : 0) + (survol ? 24 : 0), 0, 24, 24, 136, 48);
    }
}
