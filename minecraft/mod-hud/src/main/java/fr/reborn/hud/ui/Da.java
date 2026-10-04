package fr.reborn.hud.ui;

import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Kit de dessin commun de la DA Reborn (laque noire, or, vermillon, parchemin) pour les écrans en
 * jeu : panneau laqué à ferrures dorées, plaques-boutons, plaque d'en-tête suspendue, bande d'aide
 * en parchemin et texte ArcadePix calé sur la grille de pixels.
 */
public final class Da {

    public static final int GOLD = 0xFFF6CC78, GOLD_D = 0xFFAA8034, CREAM = 0xFFFAEED6, MUTED = 0xFFC8B4A0,
        LACQ = 0xFF5C1418, RED = 0xFFAA1E22, RED_HOV = 0xFFC82A2E, BLACK = 0xE60E0A0C, PLATE = 0xFF3C1E24,
        PLATE_HOV = 0xFF4A2830, PLATE_EDGE = 0xFF6E4646, INNER = 0xFF963C32, CORD = 0xFFC8A05A,
        WOOD = 0xFFC4965E, WOOD_D = 0xFF603E24, INK = 0xFF3C2814, DISABLED = 0x99100A0C;

    private Da() {}

    /** Majuscules sans accents (la fonte ArcadePix n'a pas d'accents). */
    public static String up(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    public static Component arc(String s) { return RebornFont.arcade(up(s)); }

    /** Échelle du texte courant (~0.5) arrondie pour que 1 pixel de fonte tombe sur des pixels écran entiers. */
    public static float small() {
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (Math.max(1, Math.round(gs * 0.5)) / gs);
    }

    /** Échelle des titres (~0.75), même arrondi au pixel. */
    public static float title() {
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (Math.max(1, Math.round(gs * 0.75)) / gs);
    }

    public static int width(Font f, String s, float sc) { return Math.round(f.width(arc(s)) * sc); }

    public static void text(GuiGraphicsExtractor g, Font f, String s, float x, float y, float sc, int col, int align) {
        Component c = arc(s);
        float w = f.width(c) * sc;
        float x0 = align == 0 ? x : align == 1 ? x - w / 2f : x - w;
        g.pose().pushMatrix();
        g.pose().translate(Math.round(x0), Math.round(y));
        g.pose().scale(sc, sc);
        g.text(f, c, 0, 0, col, false);
        g.pose().popMatrix();
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    public static void kanagu(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int c = GOLD, s = 6;
        g.fill(x, y, x + s, y + 2, c); g.fill(x, y, x + 2, y + s, c);
        g.fill(x + w - s, y, x + w, y + 2, c); g.fill(x + w - 2, y, x + w, y + s, c);
        g.fill(x, y + h - 2, x + s, y + h, c); g.fill(x, y + h - s, x + 2, y + h, c);
        g.fill(x + w - s, y + h - 2, x + w, y + h, c); g.fill(x + w - 2, y + h - s, x + w, y + h, c);
    }

    /** Panneau de laque noire : ombre portée, liseré or, filet intérieur vermillon, ferrures. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x70000000);
        g.fill(x, y, x + w, y + h, BLACK);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
        outline(g, x, y, w, h, GOLD);
        outline(g, x + 2, y + 2, w - 4, h - 4, INNER);
        kanagu(g, x, y, w, h);
    }

    /** Plaque-bouton : laque sombre (ou vermillon si active), liseré or au survol/actif. */
    public static void plate(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, String label,
                             boolean active, boolean hover, boolean enabled) {
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x60000000);
        int fill = active ? (hover ? RED_HOV : RED) : (hover ? PLATE_HOV : PLATE);
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, active ? 0x40FFD0B0 : 0x14FFFFFF);
        outline(g, x, y, w, h, active || hover ? GOLD : PLATE_EDGE);
        if (label != null) {
            float sc = small();
            text(g, f, label, x + w / 2f, y + (h - 8 * sc) / 2f + .5f, sc, active ? CREAM : (hover ? CREAM : MUTED), 1);
        }
        if (!enabled) g.fill(x, y, x + w, y + h, DISABLED);
    }

    /** Plaque laquée suspendue par un cordon, centrée en (cx, top). */
    public static void header(GuiGraphicsExtractor g, Font f, int cx, int top, String title, String sub) {
        int pw = Math.max(130, Math.round(f.width(arc(title)) * title()) + 40), ph = sub == null ? 18 : 26;
        for (int k = 0; k < top; k++) {
            g.fill(cx - 52 + k * 2, k, cx - 50 + k * 2, k + 1, CORD);
            g.fill(cx + 50 - k * 2, k, cx + 52 - k * 2, k + 1, CORD);
        }
        g.fill(cx - pw / 2 + 2, top + 3, cx + pw / 2 + 2, top + ph + 3, 0x80000000);
        g.fill(cx - pw / 2, top, cx + pw / 2, top + ph, LACQ);
        outline(g, cx - pw / 2, top, pw, ph, GOLD);
        outline(g, cx - pw / 2 + 2, top + 2, pw - 4, ph - 4, INNER);
        text(g, f, title, cx, top + 5, title(), CREAM, 1);
        if (sub != null) text(g, f, sub, cx, top + 17, small(), 0xFFE6B4A0, 1);
    }

    /** Bande de parchemin d'aide, centrée en bas de l'écran. */
    public static void hint(GuiGraphicsExtractor g, Font f, int screenW, int screenH, String s) {
        float sc = small();
        int w = width(f, s, sc) + 24, x = (screenW - w) / 2, y = screenH - 16;
        g.fill(x, y, x + w, y + 12, 0xF0EADCB6);
        outline(g, x, y, w, 12, 0xFF8C6E48);
        text(g, f, s, x + 12, y + 3, sc, 0xFF503C28, 0);
    }

    /**
     * Réglette en bois graduée (rail + curseur vermillon). {@code v} ∈ [0,1].
     * Libellé à gauche et valeur à droite au-dessus du rail.
     */
    public static void ruler(GuiGraphicsExtractor g, Font f, int x, int y, int w, float v, String label, String value,
                             boolean hot, boolean enabled) {
        float sc = small();
        text(g, f, label, x, y, sc, MUTED, 0);
        text(g, f, value, x + w, y, sc, GOLD, 2);
        int ry = y + 9;
        g.fill(x, ry, x + w, ry + 5, WOOD);
        outline(g, x, ry, w, 5, WOOD_D);
        g.fill(x + 1, ry + 1, x + w - 1, ry + 2, 0x50FFFFFF);
        for (int k = 0; k <= 20; k++) {
            int tx = x + Math.round(k * (w - 1) / 20f);
            g.fill(tx, ry + 1, tx + 1, ry + (k % 5 == 0 ? 4 : 3), INK);
        }
        int kx = x + Math.round((w - 1) * Math.max(0, Math.min(1, v)));
        int c = hot ? RED_HOV : RED;
        for (int i = 0; i < 5; i++) g.fill(kx - 4 + i, ry - 4 + i, kx + 5 - i, ry - 3 + i, i == 0 ? GOLD : c);
        g.fill(kx, ry, kx + 1, ry + 5, c);
        if (!enabled) g.fill(x - 4, y - 1, x + w + 5, ry + 6, DISABLED);
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
