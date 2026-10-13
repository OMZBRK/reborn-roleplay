package fr.reborn.hud.parchemin;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.ui.DaKit;
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
 * Habillage : planche laquée, plaques-boutons, réglettes d'or ({@code tools/gen_bibliotheque_textures.py}).
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
        BiblioArt.fond(g, width, height, 0.72f);
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
        BiblioArt.entete(g, font, width, DaKit.arcade("Reglages de la bibliotheque"), DaKit.inter(name));
        int w = Math.min(width - 24, 568), h = Math.min(height - 88, 290);
        int x = (width - w) / 2, y = 60 + Math.max(0, (height - 88 - h) / 4);
        DaKit.coupe(g, BiblioArt.PANNEAU, x - 2, y - 2, w + 8, h + 8, 0, 0, 576, 348, 16, 16, 576, 348);

        // onglets
        int saveW = w < 480 ? 70 : 84, rollW = w < 480 ? 84 : 104;
        int tw = Math.max(58, Math.min(80, (w - 28 - saveW - rollW - 16) / 3 - 4));
        int tx = x + 14, ty = y + 12;
        for (int i = 0; i < PAGES.length; i++) {
            final int p = i;
            BiblioArt.plaque(g, font, tx, ty, tw, 14, PAGES[i], page == i, DaKit.dans(mx, my, tx, ty, tw, 14), true);
            hits.add(new Hit(tx, ty, tw, 14, () -> setPage(p)));
            tx += tw + 5;
        }
        int sx = x + w - 14 - saveW, rx = sx - 6 - rollW;
        BiblioArt.plaque(g, font, sx, ty, saveW, 14, "Enregistrer", true, DaKit.dans(mx, my, sx, ty, saveW, 14), true);
        hits.add(new Hit(sx, ty, saveW, 14, this::saveToServer));
        BiblioArt.plaque(g, font, rx, ty, rollW, 14, w < 480 ? "Tirer" : "Tirer maintenant", false,
            DaKit.dans(mx, my, rx, ty, rollW, 14), libId != null);
        if (libId != null) hits.add(new Hit(rx, ty, rollW, 14, () -> ParcheminClient.send("reroll", libId, null)));
        g.fill(x + 12, ty + 21, x + w - 12, ty + 22, 0xFF6A4E24);
        g.fill(x + 12, ty + 22, x + w - 12, ty + 23, 0xFF120608);
        int cx = x + 16, cy = ty + 34, cw = w - 32, ch = y + h - 14 - cy;
        switch (page) {
            case 0 -> settings(g, cx, cy, cw, ch, mx, my);
            case 1 -> techniques(g, cx, cy, cw, ch, mx, my);
            default -> simulation(g, cx, cy, cw, ch, mx, my);
        }
        BiblioArt.aides(g, font, width, height, page == 1
            ? new String[][]{{"- +", "régler une technique"}, {"Clic nom en or", "taux du rang"}, {"Molette", "défiler"}, {"Echap", "fermer"}}
            : new String[][]{{"Clic", "réglette : à gauche baisse, à droite monte"}, {"Echap", "fermer"}});
        super.extractRenderState(g, mx, my, delta);
    }

    /** Titre de section : ArcadePix ×1 or, ombré (comme les intertitres des planches de la fiche). */
    private void titre(GuiGraphicsExtractor g, String s, int x, int y) {
        DaKit.arcX1(g, font, DaKit.arcade(s), x, y, DaKit.OR, 0, true);
    }

    private void settings(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        int colW = (w - 28) / 2, lx = x, rx = x + colW + 28;
        titre(g, "Modele de depart", lx, y);
        int bx = lx, by = y + 14, bw = (colW - 8) / 3;
        for (int i = 0; i < MODELS.length; i++) {
            final int m = i;
            BiblioArt.plaque(g, font, bx, by, bw, 14, MODELS[i], model == i, DaKit.dans(mx, my, bx, by, bw, 14), true);
            hits.add(new Hit(bx, by, bw, 14, () -> setModel(m)));
            bx += bw + 4;
        }
        int ly = by + 30;
        boolean hc = DaKit.dans(mx, my, lx, ly + 4, colW, 14);
        BiblioArt.reglette(g, font, lx, ly, colW, (count - 1) / 8f, "Rouleaux par tirage", String.valueOf(count), hc);
        hits.add(new Hit(lx, ly + 4, colW / 2, 14, () -> { count = Math.max(1, count - 1); recompute(); }));
        hits.add(new Hit(lx + colW / 2, ly + 4, colW / 2, 14, () -> { count = Math.min(9, count + 1); recompute(); }));
        ly += 30;
        boolean hd = DaKit.dans(mx, my, lx, ly + 4, colW, 14);
        BiblioArt.reglette(g, font, lx, ly, colW, (delayH - 1) / 23f, "Delai entre deux tirages", delayH + " h", hd);
        hits.add(new Hit(lx, ly + 4, colW / 2, 14, () -> delayH = Math.max(1, delayH - 1)));
        hits.add(new Hit(lx + colW / 2, ly + 4, colW / 2, 14, () -> delayH = Math.min(24, delayH + 1)));

        g.fill(x + colW + 13, y, x + colW + 14, y + h - 4, 0xFF2D181C);
        titre(g, "Apparition par rang", rx, y);
        int ry = y + 14, step = Math.max(20, Math.min(26, (h - 16) / 5));
        for (int r = 0; r < 5; r++) {
            char rank = "DCBAS".charAt(r);
            BiblioArt.fuda(g, rx - 1, ry - 1, rank);
            int rw = colW - 18;
            boolean hr = DaKit.dans(mx, my, rx + 18, ry + 4, rw, 14);
            BiblioArt.reglette(g, font, rx + 18, ry, rw, slider(byRank[r]), "Rang " + rank, pct(byRank[r]), hr);
            final int rr = r;
            hits.add(new Hit(rx + 18, ry + 4, rw / 2, 14, () -> { byRank[rr] = STEPS[Math.max(0, stepOf(byRank[rr]) - 1)]; recompute(); }));
            hits.add(new Hit(rx + 18 + rw / 2, ry + 4, rw / 2, 14, () -> { byRank[rr] = STEPS[Math.min(STEPS.length - 1, stepOf(byRank[rr]) + 1)]; recompute(); }));
            ry += step;
        }
    }

    private void techniques(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        DaKit.coupeH(g, BiblioArt.CHAMP, x, y - 3, 150, 0, 0, 152, 14, 4, 152, 14);
        DaKit.inter7(g, font, DaKit.inter("Rechercher…"), x + 6, y + 4, 0xFF7A6E5C, 0);
        int fx = x + 158, fy = y - 2;
        for (int i = 0; i < FILTERS.length; i++) {
            int fw = Math.max(30, Math.round(DaKit.largeurFin(font, DaKit.arcade(FILTERS[i]))) + 14);
            if (fx + fw > x + w) { fx = x; fy += 17; }
            final int f = i;
            BiblioArt.plaque(g, font, fx, fy, fw, 12, FILTERS[i], filter == i, DaKit.dans(mx, my, fx, fy, fw, 12), true);
            hits.add(new Hit(fx, fy, fw, 12, () -> setFilter(f)));
            fx += fw + 3;
        }
        int ry = fy + 21, rowH = 14;
        int rows = Math.max(1, (y + h - 12 - ry) / rowH);
        List<Technique> list = filtered();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));
        for (int i = 0; i < rows && i + scroll < list.size(); i++) {
            Technique t = list.get(i + scroll);
            int yy = ry + i * rowH;
            float ym = yy + 4;
            if (i % 2 == 0) g.fill(x, yy - 3, x + w, yy + rowH - 3, 0x0EFFFFFF);
            g.fill(x + 2, yy, x + 5, yy + 8, ScrollArt.rankColor(t.rank()));
            g.fill(x + 2, yy, x + 5, yy + 1, 0x60FFFFFF);
            boolean custom = overrides.containsKey(t.id());
            DaKit.inter9(g, font, DaKit.inter(t.name()), x + 11, ym, custom ? DaKit.OR_CLAIR : DaKit.CREME_VAL, 0);
            String type = t.typeLine() + (slotInfo.containsKey(t.id()) ? " · slots " + slotInfo.get(t.id()) : "");
            DaKit.inter8(g, font, DaKit.inter(type), x + w / 2f - 20, ym, 0xFF9A8C78, 0);
            double p = Tirage.chance(t, byRank, overrides);
            String ps = pct(p);
            int px = x + w - 62;
            DaKit.inter8(g, font, DaKit.inter(ps), px + 25, ym, custom ? DaKit.OR_CLAIR : 0xFFC8B4A0, 1);
            final Technique tt = t;
            BiblioArt.cheville(g, px - 13, yy - 2, false, DaKit.dans(mx, my, px - 12, yy - 2, 12, 11));
            BiblioArt.cheville(g, px + 50, yy - 2, true, DaKit.dans(mx, my, px + 50, yy - 2, 12, 11));
            hits.add(new Hit(px - 12, yy - 2, 12, 11, () -> { overrides.put(tt.id(), STEPS[Math.max(0, stepOf(Tirage.chance(tt, byRank, overrides)) - 1)]); recompute(); }));
            hits.add(new Hit(px + 50, yy - 2, 12, 11, () -> { overrides.put(tt.id(), STEPS[Math.min(STEPS.length - 1, stepOf(Tirage.chance(tt, byRank, overrides)) + 1)]); recompute(); }));
            if (custom) hits.add(new Hit(x + 11, yy - 1, font.width(DaKit.inter(t.name())), 10, () -> { overrides.remove(tt.id()); recompute(); }));
        }
        if (list.size() > rows) {
            String more = (scroll + 1) + "-" + Math.min(list.size(), scroll + rows) + " sur " + list.size();
            DaKit.inter7(g, font, DaKit.inter(more), x + w, y + h - 6, 0xFF7A6E5C, 2);
        }
    }

    private void simulation(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        titre(g, "Apercu d'un tirage", x, y);
        int sx = x, cw = 48;
        int step = Math.min(cw + 4, (w - 100) / Math.max(1, count));
        for (int i = 0; i < count; i++) {
            DaKit.blit(g, BiblioArt.MINI, sx, y + 13, 0, 0, Math.min(48, step - 2), 26, 48, 26);
            if (i < preview.size()) ScrollArt.item(g, sx + (Math.min(48, step - 2) - 22) / 2, y + 14, 22, preview.get(i).rank());
            sx += step;
        }
        BiblioArt.plaque(g, font, sx + 6, y + 18, 84, 14, "Nouvel apercu", false, DaKit.dans(mx, my, sx + 6, y + 18, 84, 14), true);
        hits.add(new Hit(sx + 6, y + 18, 84, 14, () -> { seed++; recompute(); }));

        int by = y + 52;
        titre(g, "Sur 10 000 tirages", x, by);
        DaKit.inter8(g, font, DaKit.inter("rouleaux par tirage en moyenne"), x + 4 + font.width(DaKit.arcade("Sur 10 000 tirages")), by + 3.5f,
            0xFF9A8C78, 0);
        by += 14;
        double[] pr = stats.perDraw();
        double max = Math.max(0.01, java.util.Arrays.stream(pr).max().orElse(1));
        int barW = w - 120;
        int pas = h < 230 ? 14 : 18;
        for (int r = 0; r < 5; r++) {
            char rank = "DCBAS".charAt(r);
            BiblioArt.fuda(g, x - 1, by - 2, rank);
            int len = (int) Math.max(1, Math.round(barW * (pr[r] / max)));
            g.fill(x + 18, by + 4, x + 18 + barW, by + 11, 0x50000000);
            g.fill(x + 18, by + 11, x + 18 + barW, by + 12, 0xFF2D181C);
            BiblioArt.brocartH(g, x + 18, by + 4, len, 7, rank);
            String v = pr[r] >= 0.1 ? String.format(Locale.FRANCE, "%.1f", pr[r])
                    : pr[r] >= 0.001 ? String.format(Locale.FRANCE, "%.3f", pr[r]) : String.format(Locale.FRANCE, "%.4f", pr[r]);
            DaKit.inter8(g, font, DaKit.inter(v), x + 24 + barW, by + 7.5f, DaKit.CREME_VAL, 0);
            by += pas;
        }
        by += 6;
        ligneStat(g, x, by, "Au moins un rang A", String.format(Locale.FRANCE, "%.1f %% des tirages", stats.atLeastA()));
        ligneStat(g, x, by + 13, "Au moins un rang S", String.format(Locale.FRANCE, "%.2f %% des tirages", stats.atLeastS()));
        ligneStat(g, x, by + 26, "Cases vides", String.format(Locale.FRANCE, "%.1f sur %d en moyenne", stats.emptySlots(), count));
    }

    private void ligneStat(GuiGraphicsExtractor g, int x, int y, String etiq, String val) {
        DaKit.etiq(g, font, DaKit.etiq(font, etiq), x, y, DaKit.OR_ETIQ);
        DaKit.inter8(g, font, DaKit.inter(val), x + 120, y, DaKit.CREME_VAL, 0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (DaKit.dans(e.x(), e.y(), h.x, h.y, h.w, h.h)) {
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
