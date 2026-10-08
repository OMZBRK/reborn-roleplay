package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.Da;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Dessin des rouleaux : rouleau fermé (papier washi en volume, embouts de bois, cordon à la couleur du rang),
 * étiquette de rang (fuda) et sceau de rang (hanko). Les rangs A et S ont des embouts dorés, le S scintille.
 */
public final class ScrollArt {

    public static final int WASHI = 0xFFE9D8B0, WASHI_HI = 0xFFF6EBCF, WASHI_LO = 0xFFB59A6A;
    public static final int WOOD = 0xFF5A3A22, WOOD_D = 0xFF2E1C11, WOOD_HI = 0xFF7A5232;
    public static final int INK = 0xFF2A1A10, INK_SOFT = 0xFF6B5434;
    public static final int GOLD = 0xFFD9A95E, GOLD_HI = 0xFFF2D49A;
    public static final int LACQUER = 0xFFA0182B, LACQUER_LO = 0xFF7A1322;

    private ScrollArt() {}

    /** Couleur du cordon et de l'étiquette par rang. */
    public static int rankColor(char r) {
        return switch (r) {
            case 'D' -> 0xFF8C98A2;
            case 'C' -> 0xFF4E9A62;
            case 'B' -> 0xFF3E6FB8;
            case 'A' -> 0xFF8A4FC0;
            default -> 0xFFC01E35;
        };
    }

    public static String rankName(char r) {
        return "Rang " + r;
    }

    /** Rouleau fermé couché, coin haut-gauche (x, y), hauteur {@code h} (≈ 11). */
    public static void rolled(GuiGraphicsExtractor g, int x, int y, int w, int h, char rank, long now) {
        boolean noble = rank == 'A' || rank == 'S';
        g.fill(x + 3, y + h, x + w - 1, y + h + 2, 0x50000000);                                   // ombre posée
        int bx0 = x + 3, bx1 = x + w - 3, mid = y + h / 2;
        g.fillGradient(bx0, y, bx1, mid, WASHI_HI, WASHI);
        g.fillGradient(bx0, mid, bx1, y + h, WASHI, WASHI_LO);
        g.fill(bx0, y + 2, bx1, y + 3, 0x40FFFFFF);                                               // reflet
        g.fill(bx1 - 3, y + 1, bx1 - 2, y + h - 1, 0x40000000);                                    // tour du papier
        // embouts (jiku)
        int cap = noble ? GOLD : WOOD;
        int capD = noble ? 0xFF8A6A2E : WOOD_D;
        for (int side = 0; side < 2; side++) {
            int cx = side == 0 ? x : x + w - 3;
            g.fill(cx, y - 1, cx + 3, y + h + 1, capD);
            g.fill(cx, y, cx + 3, y + h, cap);
            g.fill(cx, y, cx + 1, y + h, noble ? GOLD_HI : WOOD_HI);
        }
        // cordon et pompon
        int col = rankColor(rank);
        int kx = x + w / 2 - 2;
        g.fill(kx, y - 1, kx + 4, y + h + 1, col);
        g.fill(kx, y - 1, kx + 4, y, 0x40FFFFFF);
        g.fill(kx + 1, y + h + 1, kx + 3, y + h + 5, col);
        g.fill(kx, y + h + 5, kx + 4, y + h + 7, col);
        // scintillement du rang S
        if (rank == 'S') {
            for (int k = 0; k < 4; k++) {
                long ph = (now / 120 + k * 7) % 24;
                if (ph < 6) {
                    int sx = x + 6 + (int) ((k * 37 + now / 400) % Math.max(1, w - 12));
                    int sy = y - 3 + (int) (ph / 2);
                    g.fill(sx, sy, sx + 1, sy + 1, GOLD_HI);
                }
            }
        }
    }

    /** Étiquette de papier (fuda) suspendue, avec la lettre du rang. Coin haut-gauche (x, y), 10 × 13. */
    public static void fuda(GuiGraphicsExtractor g, Font f, int x, int y, char rank) {
        int col = rankColor(rank);
        g.fill(x + 4, y - 3, x + 6, y, 0xFF8A6A48);                                               // fil
        g.fill(x + 1, y + 1, x + 11, y + 14, 0x40000000);
        g.fill(x, y, x + 10, y + 13, 0xFFF0E2C2);
        g.fill(x, y, x + 10, y + 2, col);
        Da.outline(g, x, y, 10, 13, 0xFF8C6E48);
        Component c = RebornFont.bold(String.valueOf(rank));
        g.text(f, c, x + 5 - f.width(c) / 2, y + 4, INK, false);
    }

    /** Sceau rouge (hanko) carré avec la lettre du rang. */
    public static void hanko(GuiGraphicsExtractor g, Font f, int x, int y, int s, char rank) {
        g.fill(x, y, x + s, y + s, LACQUER);
        Da.outline(g, x + 1, y + 1, s - 2, s - 2, 0xFFF0C8B0);
        Component c = RebornFont.bold(String.valueOf(rank));
        float sc = s / 12f;
        g.pose().pushMatrix();
        g.pose().translate(x + s / 2f - f.width(c) * sc / 2f, y + s / 2f - 4 * sc);
        g.pose().scale(sc, sc);
        g.text(f, c, 0, 0, 0xFFF6EBCF, false);
        g.pose().popMatrix();
    }

    /** Bois veiné (cadre, étagères). */
    public static void wood(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, long seed) {
        g.fillGradient(x0, y0, x1, y1, 0xFF6E4828, 0xFF4A301B);
        for (int yy = y0; yy < y1; yy += 2) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int a = 0x12 + (int) ((seed >>> 59) & 0x0F);
            int span = Math.max(1, (x1 - x0) / 4);
            int sx = x0 + (int) ((seed >>> 33) % span);
            g.fill(sx, yy, x1 - (int) ((seed >>> 45) % span), yy + 1, (a << 24) | 0x1A0D06);
        }
    }
}
