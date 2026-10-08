package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.Da;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Bibliothèque à tirage, côté joueur (preview). Motif de l'écran : un meuble à casiers de bois sombre, chaque rouleau
 * couché dans son casier, l'étiquette de rang suspendue dessous. Survol : fiche du rouleau sur papier ; clic : on
 * prend le rouleau (il disparaît pour les autres), dans la limite de poids de la sacoche.
 */
public final class BibliothequeScreen extends Screen {

    private static final int COLS = 3, ROWS = 3, CW = 74, CH = 36, GAP = 6, FRAME = 9;

    private final String name;
    private final Technique[] cells = new Technique[COLS * ROWS];
    private final int count;
    private final String refresh;
    private double weight = 6.4;
    private final double maxWeight = 12.0;
    private String toast;
    private long toastAt;
    /** Banc d'essai : case survolée imposée (-1 = souris). */
    int debugHover = -1;

    public BibliothequeScreen(String name, List<Technique> drawn, int count, String refresh) {
        super(Component.literal(name));
        this.name = name;
        this.count = count;
        this.refresh = refresh;
        // les rouleaux occupent des cases au hasard parmi les « count » cases ouvertes
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) slots.add(i);
        java.util.Collections.shuffle(slots, new java.util.Random(drawn.size() * 31L + count));
        for (int i = 0; i < drawn.size() && i < slots.size(); i++) cells[slots.get(i)] = drawn.get(i);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        g.fillGradient(0, 0, width, height, 0xA0080406, 0xD0080406);
    }

    private int cabW() { return COLS * CW + (COLS + 1) * GAP + FRAME * 2; }

    private int cabH() { return ROWS * CH + (ROWS + 1) * GAP + FRAME * 2 + 16; }

    private int cabX() { return width / 2 - cabW() / 2 - 70; }

    private int cabY() { return Math.max(44, height / 2 - cabH() / 2); }

    private int cellX(int i) { return cabX() + FRAME + GAP + (i % COLS) * (CW + GAP); }

    private int cellY(int i) { return cabY() + FRAME + 16 + GAP + (i / COLS) * (CH + GAP); }

    private int hovered(int mx, int my) {
        if (debugHover >= 0) return debugHover;
        for (int i = 0; i < cells.length; i++) if (Da.in(mx, my, cellX(i), cellY(i), CW, CH)) return i;
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        Da.header(g, font, width / 2, 6, name, "Nouveau tirage dans " + refresh);

        int x = cabX(), y = cabY(), w = cabW(), h = cabH();
        // meuble : ombre, bois veiné, corniche, pieds
        g.fill(x + 4, y + 6, x + w + 4, y + h + 6, 0x70000000);
        ScrollArt.wood(g, x, y, x + w, y + h, 0x2545F4914F6CDD1DL);
        Da.outline(g, x, y, w, h, ScrollArt.WOOD_D);
        g.fill(x - 4, y - 3, x + w + 4, y + 4, ScrollArt.WOOD_D);
        g.fill(x - 3, y - 2, x + w + 3, y + 3, 0xFF5A3A22);
        g.fill(x - 3, y - 2, x + w + 3, y - 1, 0x40FFE0B0);
        g.fill(x + 4, y + h, x + 14, y + h + 6, ScrollArt.WOOD_D);
        g.fill(x + w - 14, y + h, x + w - 4, y + h + 6, ScrollArt.WOOD_D);
        // plaque laquée au fronton, kanji « livre »
        int px = x + w / 2 - 16, py = y + 4;
        g.fill(px, py, px + 32, py + 14, ScrollArt.LACQUER_LO);
        Da.outline(g, px, py, 32, 14, ScrollArt.GOLD);
        g.text(font, Component.literal("書庫"), px + 16 - font.width("書庫") / 2, py + 3, 0xFFF6EBCF, false);
        Da.kanagu(g, x, y, w, h);

        int hov = hovered(mx, my);
        for (int i = 0; i < cells.length; i++) {
            int cx = cellX(i), cy = cellY(i);
            boolean open = i < count;
            // casier : fond sombre, lambris, ombre portée par le plateau du dessus
            g.fill(cx, cy, cx + CW, cy + CH, open ? 0xFF1C110B : 0xFF3A2414);
            if (open) {
                for (int k = 6; k < CW; k += 9) g.fill(cx + k, cy + 2, cx + k + 1, cy + CH - 4, 0x18FFFFFF);
                g.fillGradient(cx, cy, cx + CW, cy + 8, 0x70000000, 0x00000000);
                g.fill(cx, cy + CH - 3, cx + CW, cy + CH, 0xFF4A301B);                            // plateau
                g.fill(cx, cy + CH - 3, cx + CW, cy + CH - 2, 0x30FFE0B0);
            } else {
                Da.outline(g, cx + 3, cy + 3, CW - 6, CH - 6, 0x40000000);                        // case fermée
            }
            Da.outline(g, cx - 1, cy - 1, CW + 2, CH + 2, ScrollArt.WOOD_D);
            Technique t = cells[i];
            if (open && t == null) {                                                               // poussière
                for (int k = 0; k < 5; k++) g.fill(cx + 10 + k * 12, cy + CH - 5 - (k % 2), cx + 12 + k * 12, cy + CH - 4 - (k % 2), 0x30C8B4A0);
            }
            if (t != null) {
                boolean hot = i == hov;
                int lift = hot ? 2 : 0;
                if (hot) g.fillGradient(cx, cy + CH - 18, cx + CW, cy + CH - 3, 0x00F2D49A, 0x30F2D49A);
                ScrollArt.rolled(g, cx + 7, cy + CH - 16 - lift, CW - 14, 10, t.rank(), now);
                ScrollArt.fuda(g, font, cx + CW - 15, cy + CH + 1, t.rank());
            }
        }

        if (hov >= 0 && cells[hov] != null) card(g, cells[hov], x + w + 14, Math.max(y, cellY(hov) - 20));

        // sacoche et aide
        int rw = 180, rx = width / 2 - rw / 2, ry = height - 44;
        String wt = String.format(java.util.Locale.FRANCE, "%.1f / %.0f kg", weight, maxWeight);
        Da.ruler(g, font, rx, ry, rw, (float) (weight / maxWeight), "Sacoche", wt, false, true);
        Da.hint(g, font, width, height, "Clic : prendre le rouleau  -  Echap : fermer");

        if (toast != null && now - toastAt < 2200) {
            float a = Math.min(1f, (2200 - (now - toastAt)) / 400f);
            int tw = font.width(toast) + 20, tx = width / 2 - tw / 2, ty = cabY() - 18;
            g.fill(tx, ty, tx + tw, ty + 14, ((int) (a * 0xE0) << 24) | 0x1C0E12);
            Da.outline(g, tx, ty, tw, 14, ((int) (a * 255) << 24) | (ScrollArt.GOLD & 0xFFFFFF));
            g.text(font, Component.literal(toast), tx + 10, ty + 3, ((int) (a * 255) << 24) | 0xF5E9D0, false);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    /** Fiche du rouleau survolé, sur papier washi. */
    private void card(GuiGraphicsExtractor g, Technique t, int x, int y) {
        int w = 150;
        List<FormattedCharSequence> desc = font.split(Component.literal(t.desc()), w - 16);
        int h = 74 + desc.size() * 9;
        if (x + w > width - 6) x = cabX() - w - 14;
        if (x < 6) x = Math.max(6, width - w - 6);
        y = Math.max(4, Math.min(y, height - h - 24));
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x60000000);
        g.fillGradient(x, y, x + w, y + h, ScrollArt.WASHI_HI, ScrollArt.WASHI);
        Da.outline(g, x, y, w, h, 0xFF8C6E48);
        g.fill(x, y, x + w, y + 3, ScrollArt.rankColor(t.rank()));
        ScrollArt.hanko(g, font, x + w - 22, y + 8, 14, t.rank());
        g.text(font, Component.literal(t.name()).withStyle(net.minecraft.ChatFormatting.BOLD), x + 8, y + 9, ScrollArt.INK, false);
        g.text(font, Component.literal(t.typeLine()), x + 8, y + 20, ScrollArt.INK_SOFT, false);
        g.fill(x + 8, y + 32, x + w - 8, y + 33, 0x40462810);
        int ly = y + 37;
        for (FormattedCharSequence s : desc) { g.text(font, s, x + 8, ly, ScrollArt.INK, false); ly += 9; }
        ly += 4;
        g.text(font, Component.literal(ScrollArt.rankName(t.rank()) + " · " + t.difficulty()), x + 8, ly, ScrollArt.INK_SOFT, false);
        g.text(font, Component.literal(t.seances() + " séance" + (t.seances() > 1 ? "s" : "") + " · "
                + String.format(java.util.Locale.FRANCE, "%.1f kg", t.weight())), x + 8, ly + 10, ScrollArt.INK_SOFT, false);
        g.text(font, RebornFont.body("Clic : prendre"), x + 8, y + h - 12, ScrollArt.LACQUER, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() != 0) return super.mouseClicked(e, dbl);
        int i = hovered((int) e.x(), (int) e.y());
        if (i >= 0 && cells[i] != null) take(i);
        return true;
    }

    /** Banc d'essai : première case garnie, de préférence un rang C ou plus. */
    int firstFilled() {
        int any = -1;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) continue;
            if (cells[i].rankIndex() >= 1) return i;
            if (any < 0) any = i;
        }
        return Math.max(0, any);
    }

    void take(int i) {
        Technique t = cells[i];
        if (t == null) return;
        if (weight + t.weight() > maxWeight) {
            toast = "Ta sacoche est trop lourde.";
        } else {
            weight += t.weight();
            cells[i] = null;
            toast = "Rouleau pris : " + t.name();
            RebornSounds.uiClick();
        }
        toastAt = System.currentTimeMillis();
    }
}
