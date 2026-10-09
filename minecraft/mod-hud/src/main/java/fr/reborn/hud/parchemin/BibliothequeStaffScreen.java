package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.Da;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Réglage d'une bibliothèque par le staff (preview). Tout se règle à la souris : modèle de départ, nombre de rouleaux,
 * délai entre deux tirages, pourcentage par rang, puis exceptions technique par technique (filtrées par branche ou
 * nature). En bas, un tirage d'aperçu et la simulation de 10 000 tirages montrent tout de suite l'effet des réglages.
 */
public final class BibliothequeStaffScreen extends Screen {

    private static final double[] STEPS = {0, 0.01, 0.02, 0.05, 0.1, 0.2, 0.3, 0.5, 1, 2, 3, 5, 8, 12, 18, 25, 35, 50, 75, 100};
    private static final String[] FILTERS = {"Tous", "Taïjutsu", "Kenjutsu", "Ninjutsu", "Katon", "Suiton", "Doton", "Fūton", "Raiton"};
    private static final String[] MODELS = {"Publique", "Réservée", "Privée"};

    private String libId;
    private String name;
    private List<Technique> techs = Technique.DEMO;
    private final Map<String, String> slotInfo = new HashMap<>();
    private double[] byRank = Tirage.PUBLIQUE.clone();
    private final Map<String, Double> overrides = new HashMap<>();
    private int count = 6, delayH = 3, model = 0, filter = 0, scroll = 0;
    private List<Technique> preview = List.of();
    private Tirage.Stats stats;
    private long seed = 7;

    private record Hit(int x, int y, int w, int h, Runnable r) {}
    private final List<Hit> hits = new ArrayList<>();

    public BibliothequeStaffScreen(String name) {
        super(Component.literal(name));
        this.name = name;
        recompute();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float d) {
        g.fillGradient(0, 0, width, height, 0xB0080406, 0xD8080406);
    }

    /** Réglages reçus du serveur ({@code {"t":"lib_cfg"}}). */
    public static BibliothequeStaffScreen fromJson(com.google.gson.JsonObject o) {
        BibliothequeStaffScreen s = new BibliothequeStaffScreen(o.get("name").getAsString());
        s.update(o);
        return s;
    }

    public String libId() { return libId; }

    public void update(com.google.gson.JsonObject o) {
        libId = o.get("id").getAsString();
        name = o.get("name").getAsString();
        count = o.get("count").getAsInt();
        delayH = o.get("delayH").getAsInt();
        model = o.get("model").getAsInt();
        var r = o.getAsJsonArray("byRank");
        for (int i = 0; i < 5 && i < r.size(); i++) byRank[i] = r.get(i).getAsDouble();
        overrides.clear();
        for (var e : o.getAsJsonObject("overrides").entrySet()) overrides.put(e.getKey(), e.getValue().getAsDouble());
        List<Technique> list = new ArrayList<>();
        slotInfo.clear();
        for (var e : o.getAsJsonArray("techs")) {
            var t = e.getAsJsonObject();
            Technique tech = Technique.fromJson(t);
            list.add(tech);
            if (t.has("slots")) slotInfo.put(tech.id(), t.get("slots").getAsString());
        }
        techs = list;
        recompute();
    }

    /** Envoie les réglages au serveur. */
    void saveToServer() {
        if (libId == null) return;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("name", name);
        o.addProperty("count", count);
        o.addProperty("delayH", delayH);
        o.addProperty("model", model);
        com.google.gson.JsonArray r = new com.google.gson.JsonArray();
        for (double d : byRank) r.add(d);
        o.add("byRank", r);
        com.google.gson.JsonObject ov = new com.google.gson.JsonObject();
        overrides.forEach(ov::addProperty);
        o.add("overrides", ov);
        ParcheminClient.send("cfg_save", libId, o);
    }

    void recompute() {
        stats = Tirage.simulate(techs, byRank, overrides, count, 10_000, 42);
        preview = Tirage.draw(techs, byRank, overrides, count, t -> true, new Random(seed));
    }

    void setModel(int m) {
        model = m;
        byRank = (m == 0 ? Tirage.PUBLIQUE : m == 1 ? Tirage.RESERVEE : Tirage.PRIVEE).clone();
        recompute();
    }

    public void setFilter(int f) { filter = f; scroll = 0; }

    private static String pct(double p) {
        if (p >= 1 || p == 0) return String.format(Locale.FRANCE, "%.0f %%", p);
        if (p >= 0.1) return String.format(Locale.FRANCE, "%.1f %%", p);
        return String.format(Locale.FRANCE, "%.2f %%", p);
    }

    private static int stepOf(double p) {
        int best = 0;
        for (int i = 0; i < STEPS.length; i++) if (Math.abs(STEPS[i] - p) < Math.abs(STEPS[best] - p)) best = i;
        return best;
    }

    private static float slider(double p) { return stepOf(p) / (float) (STEPS.length - 1); }

    private List<Technique> filtered() {
        List<Technique> out = new ArrayList<>();
        String f = FILTERS[filter];
        for (Technique t : techs) {
            if (filter == 0 || t.branch().equals(f) || t.nature().equals(f)) out.add(t);
        }
        out.sort((a, b) -> a.rankIndex() != b.rankIndex() ? a.rankIndex() - b.rankIndex() : a.name().compareTo(b.name()));
        return out;
    }

    private static final String[] PAGES = {"Reglages", "Techniques", "Simulation"};
    private int page = 0;

    public void setPage(int p) { page = p; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        hits.clear();
        float sc = Da.small();
        Da.header(g, font, width / 2, 6, "Réglages de la bibliothèque", name);
        int w = Math.min(width - 24, 560), h = Math.min(height - 60, 330);
        int x = (width - w) / 2, y = 40 + Math.max(0, (height - 60 - h) / 2);
        Da.panel(g, x, y, w, h);

        // onglets
        int saveW = w < 480 ? 66 : 80, rollW = w < 480 ? 82 : 100;
        int tw = Math.max(54, Math.min(72, (w - 24 - saveW - rollW - 16) / 3 - 4));
        int tx = x + 12, ty = y + 10;
        for (int i = 0; i < PAGES.length; i++) {
            final int p = i;
            Da.plate(g, font, tx, ty, tw, 14, PAGES[i], page == i, Da.in(mx, my, tx, ty, tw, 14), true);
            hits.add(new Hit(tx, ty, tw, 14, () -> setPage(p)));
            tx += tw + 4;
        }
        int sx = x + w - 12 - saveW, rx = sx - 4 - rollW;
        Da.plate(g, font, sx, ty, saveW, 14, "Enregistrer", true, Da.in(mx, my, sx, ty, saveW, 14), true);
        hits.add(new Hit(sx, ty, saveW, 14, this::saveToServer));
        Da.plate(g, font, rx, ty, rollW, 14, w < 480 ? "Tirer" : "Tirer maintenant", false, Da.in(mx, my, rx, ty, rollW, 14), libId != null);
        if (libId != null) hits.add(new Hit(rx, ty, rollW, 14, () -> ParcheminClient.send("reroll", libId, null)));
        g.fill(x + 10, ty + 20, x + w - 10, ty + 21, 0x40F6CC78);
        int cx = x + 14, cy = ty + 30, cw = w - 28, ch = y + h - 12 - cy;
        g.fill(cx - 6, cy - 6, cx + cw + 6, y + h - 8, 0x0CF6CC78);                         // fond de la page
        Da.outline(g, cx - 6, cy - 6, cw + 12, y + h - 2 - cy, 0x24F6CC78);
        switch (page) {
            case 0 -> settings(g, cx, cy, cw, ch, sc, mx, my);
            case 1 -> techniques(g, cx, cy, cw, ch, sc, mx, my);
            default -> simulation(g, cx, cy, cw, ch, sc, mx, my);
        }
        Da.hint(g, font, width, height, page == 1 ? "- / + : regler une technique  -  nom en or : revenir au taux du rang"
                : "Reglettes : clic a gauche pour baisser, a droite pour monter");
        super.extractRenderState(g, mx, my, delta);
    }

    private void settings(GuiGraphicsExtractor g, int x, int y, int w, int h, float sc, int mx, int my) {
        int colW = (w - 24) / 2, lx = x, rx = x + colW + 24;
        Da.text(g, font, "Modele de depart", lx, y, sc, Da.GOLD, 0);
        int bx = lx, by = y + 9, bw = (colW - 6) / 3;
        for (int i = 0; i < MODELS.length; i++) {
            final int m = i;
            Da.plate(g, font, bx, by, bw, 13, MODELS[i], model == i, Da.in(mx, my, bx, by, bw, 13), true);
            hits.add(new Hit(bx, by, bw, 13, () -> setModel(m)));
            bx += bw + 3;
        }
        int ly = by + 24;
        Da.ruler(g, font, lx, ly, colW, (count - 1) / 8f, "Rouleaux par tirage", String.valueOf(count), false, true);
        hits.add(new Hit(lx, ly + 4, colW / 2, 14, () -> { count = Math.max(1, count - 1); recompute(); }));
        hits.add(new Hit(lx + colW / 2, ly + 4, colW / 2, 14, () -> { count = Math.min(9, count + 1); recompute(); }));
        ly += 26;
        Da.ruler(g, font, lx, ly, colW, (delayH - 1) / 23f, "Delai entre deux tirages", delayH + " h", false, true);
        hits.add(new Hit(lx, ly + 4, colW / 2, 14, () -> delayH = Math.max(1, delayH - 1)));
        hits.add(new Hit(lx + colW / 2, ly + 4, colW / 2, 14, () -> delayH = Math.min(24, delayH + 1)));

        Da.text(g, font, "Apparition par rang", rx, y, sc, Da.GOLD, 0);
        int ry = y + 10, step = Math.max(18, Math.min(24, (h - 12) / 5));
        for (int r = 0; r < 5; r++) {
            char rank = "DCBAS".charAt(r);
            ScrollArt.fuda(g, font, rx, ry + 3, rank);
            int rw = colW - 16;
            Da.ruler(g, font, rx + 16, ry, rw, slider(byRank[r]), "Rang " + rank, pct(byRank[r]), false, true);
            final int rr = r;
            hits.add(new Hit(rx + 16, ry + 4, rw / 2, 14, () -> { byRank[rr] = STEPS[Math.max(0, stepOf(byRank[rr]) - 1)]; recompute(); }));
            hits.add(new Hit(rx + 16 + rw / 2, ry + 4, rw / 2, 14, () -> { byRank[rr] = STEPS[Math.min(STEPS.length - 1, stepOf(byRank[rr]) + 1)]; recompute(); }));
            ry += step;
        }
    }

    private void techniques(GuiGraphicsExtractor g, int x, int y, int w, int h, float sc, int mx, int my) {
        g.fill(x, y - 2, x + 150, y + 10, 0xFF1C0E12);
        Da.outline(g, x, y - 2, 150, 12, Da.PLATE_EDGE);
        g.text(font, Component.literal("Rechercher…"), x + 4, y, 0xFF7A6E5C, false);
        int fx = x + 156, fy = y - 2;
        for (int i = 0; i < FILTERS.length; i++) {
            int fw = Math.max(26, Da.width(font, FILTERS[i], sc) + 10);
            if (fx + fw > x + w) { fx = x; fy += 15; }
            final int f = i;
            Da.plate(g, font, fx, fy, fw, 12, FILTERS[i], filter == i, Da.in(mx, my, fx, fy, fw, 12), true);
            hits.add(new Hit(fx, fy, fw, 12, () -> setFilter(f)));
            fx += fw + 2;
        }
        int ry = fy + 18, rowH = 13;
        int rows = Math.max(1, (y + h - 10 - ry) / rowH);
        List<Technique> list = filtered();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = 0; i < rows && i + scroll < list.size(); i++) {
            Technique t = list.get(i + scroll);
            int yy = ry + i * rowH;
            if (i % 2 == 0) g.fill(x, yy - 2, x + w, yy + rowH - 2, 0x10FFFFFF);
            g.fill(x + 2, yy, x + 6, yy + 8, ScrollArt.rankColor(t.rank()));
            g.text(font, Component.literal(t.name()), x + 10, yy, 0xFFF5E9D0, false);
            String type = t.typeLine() + (slotInfo.containsKey(t.id()) ? " · slots " + slotInfo.get(t.id()) : "");
            g.text(font, Component.literal(type), x + w / 2 - 20, yy, 0xFF9A8C78, false);
            double p = Tirage.chance(t, byRank, overrides);
            boolean custom = overrides.containsKey(t.id());
            String ps = pct(p);
            int px = x + w - 62;
            g.text(font, Component.literal(ps), px + 24 - font.width(ps) / 2, yy, custom ? Da.GOLD : 0xFFC8B4A0, false);
            final Technique tt = t;
            Da.plate(g, font, px - 12, yy - 2, 12, 11, "-", false, Da.in(mx, my, px - 12, yy - 2, 12, 11), true);
            Da.plate(g, font, px + 50, yy - 2, 12, 11, "+", false, Da.in(mx, my, px + 50, yy - 2, 12, 11), true);
            hits.add(new Hit(px - 12, yy - 2, 12, 11, () -> { overrides.put(tt.id(), STEPS[Math.max(0, stepOf(Tirage.chance(tt, byRank, overrides)) - 1)]); recompute(); }));
            hits.add(new Hit(px + 50, yy - 2, 12, 11, () -> { overrides.put(tt.id(), STEPS[Math.min(STEPS.length - 1, stepOf(Tirage.chance(tt, byRank, overrides)) + 1)]); recompute(); }));
            if (custom) hits.add(new Hit(x + 10, yy - 1, font.width(t.name()), 10, () -> { overrides.remove(tt.id()); recompute(); }));
        }
        if (list.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + rows) + " sur " + list.size() + " · molette";
            g.text(font, Component.literal(more), x + w - font.width(more), y + h - 8, 0xFF7A6E5C, false);
        }
    }

    private void simulation(GuiGraphicsExtractor g, int x, int y, int w, int h, float sc, int mx, int my) {
        Da.text(g, font, "Apercu d'un tirage", x, y, sc, Da.GOLD, 0);
        int sx = x, cw = Math.min(46, (w - 90) / Math.max(1, count) - 4);
        for (int i = 0; i < count; i++) {
            g.fill(sx, y + 10, sx + cw, y + 32, 0xFF1C110B);
            Da.outline(g, sx, y + 10, cw, 22, ScrollArt.WOOD_D);
            if (i < preview.size()) {
                ScrollArt.rolled(g, sx + 3, y + 17, cw - 6, 8, preview.get(i).rank(), System.currentTimeMillis());
            }
            sx += cw + 4;
        }
        Da.plate(g, font, sx + 4, y + 14, 80, 14, "Nouvel apercu", false, Da.in(mx, my, sx + 4, y + 14, 80, 14), true);
        hits.add(new Hit(sx + 4, y + 14, 80, 14, () -> { seed++; recompute(); }));

        int by = y + 44;
        Da.text(g, font, "Sur 10 000 tirages, rouleaux par tirage en moyenne", x, by, sc, Da.GOLD, 0);
        by += 11;
        double[] pr = stats.perDraw();
        double max = Math.max(0.01, java.util.Arrays.stream(pr).max().orElse(1));
        int barW = w - 120;
        for (int r = 0; r < 5; r++) {
            char rank = "DCBAS".charAt(r);
            ScrollArt.fuda(g, font, x, by + 1, rank);
            int len = (int) Math.max(1, Math.round(barW * (pr[r] / max)));
            g.fill(x + 16, by + 3, x + 16 + barW, by + 10, 0x30000000);
            g.fill(x + 16, by + 3, x + 16 + len, by + 10, ScrollArt.rankColor(rank));
            String v = pr[r] >= 0.1 ? String.format(Locale.FRANCE, "%.1f", pr[r])
                    : pr[r] >= 0.001 ? String.format(Locale.FRANCE, "%.3f", pr[r]) : String.format(Locale.FRANCE, "%.4f", pr[r]);
            g.text(font, Component.literal(v), x + 22 + barW, by + 2, 0xFFE6D7BC, false);
            by += h < 230 ? 12 : 15;
        }
        by += h < 230 ? 2 : 4;
        g.text(font, Component.literal(String.format(Locale.FRANCE, "Au moins un rang A : %.1f %% des tirages", stats.atLeastA())), x, by, 0xFFE6D7BC, false);
        g.text(font, Component.literal(String.format(Locale.FRANCE, "Au moins un rang S : %.2f %% des tirages", stats.atLeastS())), x, by + 10, 0xFFE6D7BC, false);
        g.text(font, Component.literal(String.format(Locale.FRANCE, "Cases vides en moyenne : %.1f sur %d", stats.emptySlots(), count)), x, by + 20, 0xFFC8B4A0, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Da.in(e.x(), e.y(), h.x, h.y, h.w, h.h)) {
                RebornSounds.uiClick();
                h.r.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= (int) Math.signum(sy);
        return true;
    }

    /** Banc d'essai. */
    void debugCustom(String id, double p) { overrides.put(id, p); recompute(); }
}
