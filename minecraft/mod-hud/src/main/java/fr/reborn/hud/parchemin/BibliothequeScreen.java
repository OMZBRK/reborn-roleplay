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

    private static final int COLS = 3, ROWS = 3, GAP = 5, FRAME = 8, ROOF = 18, LEGS = 6;
    private static final int TOP = 40, FOOT = 50;

    /** Identifiant serveur ({@code null} = démo du banc d'essai, sans réseau). */
    private String libId;
    private String name;
    private final Technique[] cells = new Technique[COLS * ROWS];
    private int count;
    private String refresh;
    private double weight = 6.4;
    private double maxWeight = 12.0;
    private boolean staff;
    private String toast;
    private long toastAt;
    /** Banc d'essai : case survolée imposée (-1 = souris). */
    public int debugHover = -1;

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

    /** Bibliothèque reçue du serveur ({@code {"t":"lib"}}). */
    public static BibliothequeScreen fromJson(com.google.gson.JsonObject o) {
        BibliothequeScreen s = new BibliothequeScreen(o.get("name").getAsString(), List.of(), 0, "");
        s.update(o);
        return s;
    }

    public String libId() { return libId; }

    /** Nouvel état envoyé par le serveur (après une prise, un tirage…). */
    public void update(com.google.gson.JsonObject o) {
        libId = o.get("id").getAsString();
        name = o.get("name").getAsString();
        count = o.get("count").getAsInt();
        refresh = o.get("refreshIn").getAsString();
        weight = o.get("weight").getAsDouble();
        maxWeight = o.get("maxWeight").getAsDouble();
        staff = o.has("staff") && o.get("staff").getAsBoolean();
        var arr = o.getAsJsonArray("cells");
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < arr.size() && arr.get(i).isJsonObject() ? Technique.fromJson(arr.get(i).getAsJsonObject()) : null;
        }
    }

    public void toast(String msg) {
        toast = msg;
        toastAt = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        g.fillGradient(0, 0, width, height, 0xA0080406, 0xD0080406);
    }

    /** Hauteur d'un casier : s'adapte à la place entre l'en-tête et la jauge de la sacoche. */
    private int ch() {
        int avail = height - TOP - FOOT - ROOF - LEGS - 2 * FRAME - (ROWS + 1) * GAP;
        return Math.max(24, Math.min(40, avail / ROWS));
    }

    private int cw() { return Math.max(56, Math.min(80, Math.round(ch() * 2.0f))); }

    private int cabW() { return COLS * cw() + (COLS + 1) * GAP + FRAME * 2; }

    private int cabH() { return ROWS * ch() + (ROWS + 1) * GAP + FRAME * 2 + ROOF; }

    /**
     * Meuble centré ; si la fiche n'a pas la place à sa droite, c'est l'ensemble meuble + fiche qui est centré
     * (mise en page stable : elle ne bouge pas au survol).
     */
    private int cabX() {
        int centered = (width - cabW()) / 2, card = 150 + 12;
        if (centered + cabW() + card <= width - 6) return centered;
        int group = cabW() + card;
        return group <= width - 12 ? (width - group) / 2 : centered;
    }

    private int cabY() { return TOP + Math.max(0, (height - TOP - FOOT - LEGS - cabH()) / 2); }

    private int cellX(int i) { return cabX() + FRAME + GAP + (i % COLS) * (cw() + GAP); }

    private int cellY(int i) { return cabY() + ROOF + FRAME + GAP + (i / COLS) * (ch() + GAP); }

    private int[] staffButton() { return new int[]{width - 78, 8, 70, 14}; }

    private int hovered(int mx, int my) {
        if (debugHover >= 0) return debugHover;
        for (int i = 0; i < cells.length; i++) if (Da.in(mx, my, cellX(i), cellY(i), cw(), ch())) return i;
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        Da.header(g, font, width / 2, 6, name, "Nouveau tirage dans " + refresh);

        int x = cabX(), y = cabY(), w = cabW(), h = cabH();
        int CW = cw(), CH = ch();
        // meuble (tansu) : ombre, corps en bois veiné, piliers, pieds cerclés de métal
        g.fill(x + 5, y + 8, x + w + 5, y + h + LEGS + 4, 0x70000000);
        ScrollArt.wood(g, x, y + ROOF - 2, x + w, y + h, 0x2545F4914F6CDD1DL);
        Da.outline(g, x, y + ROOF - 2, w, h - ROOF + 2, ScrollArt.WOOD_D);
        g.fill(x + 1, y + ROOF, x + 4, y + h - 1, 0x22FFE0B0);                                  // pilier gauche
        g.fill(x + w - 4, y + ROOF, x + w - 1, y + h - 1, 0x30000000);                          // pilier droit
        for (int side = 0; side < 2; side++) {
            int lx = side == 0 ? x + 6 : x + w - 18;
            g.fill(lx, y + h, lx + 12, y + h + LEGS, ScrollArt.WOOD_D);
            g.fill(lx, y + h + LEGS - 2, lx + 12, y + h + LEGS, ScrollArt.GOLD);
        }
        // linteau laqué, filet d'or, plaque « 書庫 »
        g.fill(x - 6, y, x + w + 6, y + ROOF, ScrollArt.WOOD_D);
        g.fillGradient(x - 5, y + 1, x + w + 5, y + ROOF - 1, 0xFF3A1016, 0xFF1C080C);
        g.fill(x - 5, y + 1, x + w + 5, y + 2, 0x40FFE0B0);
        g.fill(x - 5, y + ROOF - 3, x + w + 5, y + ROOF - 2, ScrollArt.GOLD);
        int pw = 40, px = x + w / 2 - pw / 2, py = y + 2;
        g.fill(px, py, px + pw, py + ROOF - 6, ScrollArt.LACQUER);
        Da.outline(g, px, py, pw, ROOF - 6, ScrollArt.GOLD);
        g.text(font, Component.literal("書庫"), px + pw / 2 - font.width("書庫") / 2, py + (ROOF - 6 - 8) / 2 + 1, 0xFFF6EBCF, false);
        // filet d'or autour des casiers
        Da.outline(g, x + FRAME - 2, y + ROOF + FRAME - 2, w - 2 * FRAME + 4, h - ROOF - 2 * FRAME + 4, 0x50D9A95E);
        Da.kanagu(g, x, y + ROOF - 2, w, h - ROOF + 2);

        int hov = hovered(mx, my);
        for (int i = 0; i < cells.length; i++) {
            int cx = cellX(i), cy = cellY(i);
            boolean open = i < count;
            Technique t = cells[i];
            if (open) {
                // fond laqué sombre, lumière chaude venue du haut, plateau de bois
                g.fill(cx, cy, cx + CW, cy + CH, 0xFF160D09);
                g.fillGradient(cx, cy, cx + CW, cy + CH - 3, 0x26F2C890, 0x00F2C890);
                for (int k = 8; k < CW - 4; k += 10) g.fill(cx + k, cy + 3, cx + k + 1, cy + CH - 5, 0x0EFFFFFF);
                g.fillGradient(cx, cy, cx + CW, cy + 6, 0x80000000, 0x00000000);
                g.fill(cx, cy + CH - 3, cx + CW, cy + CH, 0xFF5A3A22);
                g.fill(cx, cy + CH - 3, cx + CW, cy + CH - 2, 0x40FFE0B0);
                if (t == null) {                                                                   // poussière
                    for (int k = 0; k < 5; k++) {
                        int dx = cx + 8 + k * (CW - 16) / 5;
                        g.fill(dx, cy + CH - 5 - (k % 2), dx + 2, cy + CH - 4 - (k % 2), 0x30C8B4A0);
                    }
                }
            } else {
                // porte de bois coulissante, anneau doré
                g.fillGradient(cx, cy, cx + CW, cy + CH, 0xFF6A4628, 0xFF4E321D);
                g.fill(cx + CW / 2, cy + 2, cx + CW / 2 + 1, cy + CH - 2, 0x60000000);
                Da.outline(g, cx + 2, cy + 2, CW - 4, CH - 4, 0x40000000);
                int rx = cx + CW / 2 - 6, ry = cy + CH / 2 - 2;
                Da.outline(g, rx, ry, 4, 4, ScrollArt.GOLD);
                Da.outline(g, rx + 8, ry, 4, 4, ScrollArt.GOLD);
            }
            boolean hot = i == hov && t != null;
            Da.outline(g, cx - 1, cy - 1, CW + 2, CH + 2, hot ? ScrollArt.GOLD : ScrollArt.WOOD_D);
            if (t != null) {
                int sh = Math.max(8, Math.min(11, CH / 3));
                int lift = hot ? 2 : 0;
                if (hot) g.fillGradient(cx, cy + CH - sh - 8, cx + CW, cy + CH - 3, 0x00F2D49A, 0x38F2D49A);
                ScrollArt.rolled(g, cx + 6, cy + CH - sh - 6 - lift, CW - 12, sh, t.rank(), now);
                ScrollArt.fuda(g, font, cx + CW - 15, cy + CH + 1, t.rank());
            }
        }

        if (hov >= 0 && cells[hov] != null) card(g, cells[hov], hov);

        // sacoche et aide
        int rw = Math.min(220, cabW()), rx = (width - rw) / 2, ry = height - FOOT + 12;
        String wt = String.format(java.util.Locale.FRANCE, "%.1f / %.0f kg", weight, maxWeight);
        Da.ruler(g, font, rx, ry, rw, (float) (weight / maxWeight), "Sacoche", wt, false, true);
        Da.hint(g, font, width, height, "Clic : prendre le rouleau  -  Echap : fermer");

        if (staff) {
            int[] b = staffButton();
            Da.plate(g, font, b[0], b[1], b[2], b[3], "Reglages", false, Da.in(mx, my, b[0], b[1], b[2], b[3]), true);
        }
        if (toast != null && now - toastAt < 2200) {
            float a = Math.min(1f, (2200 - (now - toastAt)) / 400f);
            int tw = font.width(toast) + 20, tx = width / 2 - tw / 2, ty = cabY() + ROOF + 4;
            g.fill(tx, ty, tx + tw, ty + 14, ((int) (a * 0xE0) << 24) | 0x1C0E12);
            Da.outline(g, tx, ty, tw, 14, ((int) (a * 255) << 24) | (ScrollArt.GOLD & 0xFFFFFF));
            g.text(font, Component.literal(toast), tx + 10, ty + 3, ((int) (a * 255) << 24) | 0xF5E9D0, false);
        }
        super.extractRenderState(g, mx, my, delta);
    }

    /** Fiche du rouleau survolé, sur papier washi : à droite du meuble, sinon à gauche, sinon sur la case. */
    private void card(GuiGraphicsExtractor g, Technique t, int cell) {
        int w = 150;
        List<FormattedCharSequence> desc = font.split(Component.literal(t.desc()), w - 16);
        int h = 74 + desc.size() * 9;
        int x;
        if (cabX() + cabW() + 12 + w <= width - 6) x = cabX() + cabW() + 12;
        else if (cabX() - 12 - w >= 6) x = cabX() - 12 - w;
        else x = Math.max(6, Math.min(width - w - 6, cellX(cell) + cw() + 4));
        int y = Math.max(TOP, Math.min(cellY(cell) - 6, height - FOOT - h));
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
        int[] sb = staffButton();
        if (staff && Da.in(e.x(), e.y(), sb[0], sb[1], sb[2], sb[3])) {
            ParcheminClient.send("cfg_open", libId, null);
            return true;
        }
        int i = hovered((int) e.x(), (int) e.y());
        if (i >= 0 && cells[i] != null) take(i);
        return true;
    }

    /** Banc d'essai : première case garnie, de préférence un rang C ou plus. */
    public int firstFilled() {
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
        if (libId != null) {                                   // le serveur décide et renvoie l'état
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("slot", i);
            ParcheminClient.send("take", libId, o);
            RebornSounds.uiClick();
            return;
        }
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
