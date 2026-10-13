package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.Da;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dessin des rouleaux : rouleau fermé rendu avec son modèle 3D (rouleaux SNR de l'équipe), étiquette de rang
 * (fuda) et sceau de rang (hanko).
 */
public final class ScrollArt {

    public static final int WASHI = 0xFFE9D8B0, WASHI_HI = 0xFFF6EBCF, WASHI_LO = 0xFFB59A6A;
    public static final int WOOD = 0xFF5A3A22, WOOD_D = 0xFF2E1C11, WOOD_HI = 0xFF7A5232;
    public static final int INK = 0xFF2A1A10, INK_SOFT = 0xFF6B5434;
    public static final int GOLD = 0xFFD9A95E, GOLD_HI = 0xFFF2D49A;
    public static final int LACQUER = 0xFFA0182B, LACQUER_LO = 0xFF7A1322;

    private ScrollArt() {}

    /** Couleur du cordon et de l'étiquette par rang, alignée sur les rouleaux SNR (vert, jaune, rouge, violet, interdit). */
    public static int rankColor(char r) {
        return switch (r) {
            case 'D', 'E' -> 0xFF3E8F62;
            case 'C' -> 0xFFC9A332;
            case 'B' -> 0xFFB0303A;
            case 'A' -> 0xFF7E52B4;
            default -> 0xFF4E4A42;
        };
    }

    public static String rankName(char r) {
        return "Rang " + r;
    }

    private static final ItemStack[] STACKS = new ItemStack[5];

    /** L'objet parchemin du rang (modèle 3D SNR du mod, le même qu'en main). */
    public static ItemStack stack(char rank) {
        int i = switch (rank) { case 'C' -> 1; case 'B' -> 2; case 'A' -> 3; case 'S' -> 4; default -> 0; };
        if (STACKS[i] == null) {
            ItemStack s = new ItemStack(Items.PAPER);
            s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("reborn", "parchemin_" + "dcbas".charAt(i)));
            STACKS[i] = s;
        }
        return STACKS[i];
    }

    /** Rouleau rendu avec son modèle 3D, dans un carré de côté {@code size} (coin haut-gauche x, y). */
    public static void item(GuiGraphicsExtractor g, int x, int y, int size, char rank) {
        float sc = size / 16f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.item(stack(rank), 0, 0);
        g.pose().popMatrix();
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
