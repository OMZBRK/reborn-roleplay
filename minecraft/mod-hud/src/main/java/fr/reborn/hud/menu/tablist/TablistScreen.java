package fr.reborn.hud.menu.tablist;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.esc.EscData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tablist Reborn façon <b>Zenkai</b> — <b>panneau centré</b> (pas plein écran)
 * flottant au-dessus du jeu assombri. Deux modes (pref {@code tablistHold}) :
 * <ul>
 *   <li><b>Toggle</b> (défaut) : presser Tab ouvre cet écran interactif
 *       (4 onglets, recherche, clic → carte d'infos).</li>
 *   <li><b>Hold</b> : maintenir Tab affiche un overlay lecture seule
 *       (via {@link #renderHoldOverlay}, appelé par le mixin PlayerListHud).</li>
 * </ul>
 *
 * <p>v1 : données <b>mockées</b> ({@link MockTablist}). v2 : flux serveur
 * {@code reborn:tablist} (ShinobiCore).
 */
public class TablistScreen extends Screen {

    private static final String[] TABS = {"Connaissances", "Amis", "Staff", "Général"};
    private static final int GOLD = 0xFFF2C14E;

    private static final int MAX_W = 640, MAX_H = 400;
    private static final int PAD = 14;
    private static final int HEADER = 42;
    private static final int TAB_H = 22;
    private static final int COLHEAD = 14;
    private static final int ROW_H = 20;

    private int activeTab = 3; // Général
    private int scrollY = 0;
    private EditBox search;
    private TabEntry selected;
    private String selfName = "Vous";

    public TablistScreen() {
        super(Component.literal("Tablist Reborn"));
    }

    /** Géométrie du panneau, dérivée de la taille écran (responsive). */
    private record Geom(int w, int h, int px, int py, int pw, int ph,
                        int listX, int listW, int searchX, int searchY, int searchW,
                        int tabsY, int colHeadY, int listTop, int listBottom) {
        int markerX() { return listX + 10; }
        int nameX() { return listX + 24; }
        int gradeX() { return listX + (int) (listW * 0.56f); }
        int pingX() { return listX + listW - 46; }
        int viewH() { return Math.max(0, listBottom - listTop); }
    }

    private static Geom geom(int w, int h) {
        int pw = Math.min(MAX_W, w - 60);
        int ph = Math.min(MAX_H, h - 60);
        int px = (w - pw) / 2, py = (h - ph) / 2;
        int listX = px + PAD, listW = pw - 2 * PAD;
        int searchW = Math.min(190, listW / 3);
        int tabsY = py + HEADER;
        int colHeadY = tabsY + TAB_H;
        int listTop = colHeadY + COLHEAD;
        int listBottom = py + ph - 8;
        return new Geom(w, h, px, py, pw, ph, listX, listW,
            listX, py + 13, searchW, tabsY, colHeadY, listTop, listBottom);
    }

    private Geom geom() { return geom(this.width, this.height); }

    @Override
    protected void init() {
        selfName = (this.client != null && this.client.player != null)
            ? this.client.player.getGameProfile().getName() : "Vous";

        Geom g = geom();
        search = new EditBox(this.textRenderer, g.searchX() + 2, g.searchY() + 2,
            g.searchW() - 4, 12, Component.literal("Rechercher"));
        search.setBordered(false);
        search.setMaxLength(24);
        search.setHint(Component.literal("Rechercher un joueur…"));
        search.setChangedListener(s -> { scrollY = 0; });
        this.addSelectableChild(search);
    }

    // ─── Filtrage ────────────────────────────────────────
    private static List<TabEntry> filtered(List<TabEntry> all, int tab, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<TabEntry> out = new ArrayList<>();
        for (TabEntry e : all) {
            boolean tabOk = switch (tab) {
                case 0 -> e.relation() == TabEntry.Relation.CONNAISSANCE;
                case 1 -> e.relation() == TabEntry.Relation.AMI;
                case 2 -> e.staff();
                default -> true;
            };
            if (!tabOk) continue;
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(e);
        }
        return out;
    }

    private List<TabEntry> filtered() {
        return filtered(TablistData.entries(selfName), activeTab, search != null ? search.getText() : "");
    }

    private int maxScroll(Geom g) {
        return Math.max(0, filtered().size() * ROW_H - g.viewH());
    }

    // ─── Rendu écran interactif ──────────────────────────
    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Geom g = geom();
        scrollY = Math.max(0, Math.min(scrollY, maxScroll(g)));

        drawPanel(ctx, this.textRenderer, g, TablistData.entries(selfName), activeTab,
            search != null ? search.getText() : "", TablistData.rpDate(), true, selected, scrollY, mouseX, mouseY);

        // Champ de recherche par-dessus la boîte dessinée par drawPanel.
        if (search != null) search.render(ctx, mouseX, mouseY, delta);
    }

    /**
     * Dessine le panneau complet. Statique + sans état pour être réutilisé par
     * l'overlay lecture seule (hold). En mode non-interactif : pas de hover, pas
     * de carte détail, placeholder de recherche dessiné en dur.
     */
    static void drawPanel(GuiGraphicsExtractor ctx, Font tr, Geom g, List<TabEntry> all,
                          int activeTab, String searchText, String rpDate, boolean interactive,
                          TabEntry selected, int scrollY, int mouseX, int mouseY) {
        // Épuré, façon tablist vanilla : léger voile global + un bloc translucide
        // derrière le contenu — pas de cadre, pas de bordure, pas d'ombre.
        ctx.fill(0, 0, g.w(), g.h(), 0x30000000);
        ctx.fill(g.px(), g.py(), g.px() + g.pw(), g.py() + g.ph(), 0x59000000);

        List<TabEntry> rows = filtered(all, activeTab, searchText);

        drawHeader(ctx, tr, g, all.size(), searchText, rpDate, interactive);
        drawTabs(ctx, tr, g, activeTab, interactive, mouseX, mouseY);
        drawColumnHeaders(ctx, tr, g);

        // Liste scrollée + clippée.
        ctx.enableScissor(g.px(), g.listTop(), g.px() + g.pw(), g.listBottom());
        int y = g.listTop() - scrollY;
        for (TabEntry e : rows) {
            if (y + ROW_H > g.listTop() && y < g.listBottom()) {
                drawRow(ctx, tr, g, e, y, e == selected, interactive, mouseX, mouseY);
            }
            y += ROW_H;
        }
        ctx.disableScissor();

        drawScrollbar(ctx, g, rows.size() * ROW_H, scrollY);

        if (interactive && selected != null) drawDetail(ctx, tr, g, selected);
    }

    private static void drawHeader(GuiGraphicsExtractor ctx, Font tr, Geom g,
                                   int online, String searchText, String rpDate, boolean interactive) {
        int cx = g.px() + g.pw() / 2;
        Component title = RebornFont.arcade("REBORN ROLEPLAY");
        ctx.text(tr, title, cx - tr.width(title) / 2, g.py() + 9, Colors.WHITE_PURE, false);
        Component sub = RebornFont.arcade("PLAY.REBORN-RP.FR");
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, g.py() + 21);
        ctx.pose().scale(0.8f, 0.8f);
        ctx.text(tr, sub, -tr.width(sub) / 2, 0, Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();

        // Compteur + date RP (calendrier lore) à droite.
        Component count = RebornFont.arcade(online + " EN LIGNE");
        int rightEdge = g.px() + g.pw() - PAD;
        ctx.text(tr, count, rightEdge - tr.width(count), g.py() + 12, Colors.ACCENT_HOVER, false);
        Component date = RebornFont.arcade(EscData.arcadeSafe(rpDate, 28));
        ctx.pose().pushMatrix();
        ctx.pose().translate(rightEdge, g.py() + 25);
        ctx.pose().scale(0.8f, 0.8f);
        ctx.text(tr, date, -tr.width(date), 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.pose().popMatrix();

        // Recherche épurée : simple soulignement (le champ réel est rendu
        // par-dessus par l'écran). Pas de boîte encadrée.
        ctx.fill(g.searchX() - 2, g.searchY() + 15, g.searchX() + g.searchW() + 2, g.searchY() + 16,
            Colors.withAlpha(Colors.BORDER_STRONG, 0.8f));
        if (!interactive && (searchText == null || searchText.isEmpty())) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(g.searchX() + 2, g.searchY() + 3);
            ctx.pose().scale(0.9f, 0.9f);
            ctx.text(tr, RebornFont.body("Rechercher un joueur…"), 0, 0, Colors.FOREGROUND_MUTED, false);
            ctx.pose().popMatrix();
        }
    }

    private static int[] tabXs(Font tr, Geom g) {
        int gap = 20, total = 0;
        int[] w = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            w[i] = tr.width(RebornFont.arcade(EscData.arcadeSafe(TABS[i], 20)));
            total += w[i];
            if (i < TABS.length - 1) total += gap;
        }
        int[] xs = new int[TABS.length];
        int x = g.px() + (g.pw() - total) / 2;
        for (int i = 0; i < TABS.length; i++) { xs[i] = x; x += w[i] + gap; }
        return xs;
    }

    private static void drawTabs(GuiGraphicsExtractor ctx, Font tr, Geom g,
                                 int activeTab, boolean interactive, int mouseX, int mouseY) {
        int[] xs = tabXs(tr, g);
        for (int i = 0; i < TABS.length; i++) {
            Component label = RebornFont.arcade(EscData.arcadeSafe(TABS[i], 20));
            int w = tr.width(label), x = xs[i];
            boolean active = i == activeTab;
            boolean hover = interactive && mouseX >= x - 6 && mouseX < x + w + 6
                && mouseY >= g.tabsY() && mouseY < g.tabsY() + TAB_H;
            int color = active ? Colors.WHITE_PURE : (hover ? Colors.FOREGROUND_SUBTLE : Colors.FOREGROUND_MUTED);
            ctx.text(tr, label, x, g.tabsY() + 6, color, false);
            if (active) DrawHelpers.roundedRect(ctx, x, g.tabsY() + TAB_H - 4, w, 2, 1, Colors.ACCENT);
        }
    }

    private static void drawColumnHeaders(GuiGraphicsExtractor ctx, Font tr, Geom g) {
        int y = g.colHeadY() + 3;
        mini(ctx, tr, "NOM", g.nameX(), y);
        mini(ctx, tr, "GRADE", g.gradeX(), y);
        Component ping = RebornFont.arcade("PING");
        mini(ctx, tr, "PING", g.pingX() + 20 - tr.width(ping) / 2, y);
        ctx.fill(g.listX(), g.listTop() - 1, g.listX() + g.listW(), g.listTop(),
            Colors.withAlpha(Colors.BORDER, 0.6f));
    }

    private static void mini(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(0.85f, 0.85f);
        ctx.text(tr, RebornFont.arcade(s), 0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.pose().popMatrix();
    }

    private static void drawRow(GuiGraphicsExtractor ctx, Font tr, Geom g, TabEntry e, int y,
                                boolean isSel, boolean interactive, int mouseX, int mouseY) {
        boolean hover = interactive && mouseX >= g.listX() && mouseX < g.listX() + g.listW()
            && mouseY >= y && mouseY < y + ROW_H && mouseY >= g.listTop() && mouseY < g.listBottom();
        if (isSel) {
            DrawHelpers.roundedRect(ctx, g.listX(), y + 1, g.listW(), ROW_H - 2, 4, Colors.ACCENT_SOFT);
        } else if (hover) {
            DrawHelpers.roundedRect(ctx, g.listX(), y + 1, g.listW(), ROW_H - 2, 4,
                Colors.withAlpha(Colors.SURFACE_ELEVATED, 0.6f));
        }
        int midY = y + (ROW_H - tr.lineHeight) / 2;
        int mx = g.markerX(), my = y + ROW_H / 2;

        int nameColor;
        if (e.relation() == TabEntry.Relation.SOI) {
            nameColor = Colors.WHITE_PURE;
            DrawHelpers.disc(ctx, mx, my, 3, Colors.WHITE_PURE);
        } else if (e.staff()) {
            nameColor = GOLD;
            drawStar(ctx, mx, my, 4, GOLD);
        } else if (e.relation() == TabEntry.Relation.AMI) {
            nameColor = e.clanColor() != 0 ? e.clanColor() : Colors.SUCCESS;
            DrawHelpers.disc(ctx, mx, my, 3, Colors.SUCCESS);
        } else if (e.relation() == TabEntry.Relation.CONNAISSANCE) {
            nameColor = e.clanColor() != 0 ? e.clanColor() : Colors.ACCENT_HOVER;
            DrawHelpers.ring(ctx, mx, my, 3, 1, Colors.ACCENT_HOVER);
        } else {
            nameColor = Colors.FOREGROUND_MUTED;
        }

        ctx.text(tr, RebornFont.bold(e.name()), g.nameX(), midY, nameColor, false);
        int gradeColor = e.known() ? Colors.FOREGROUND_SUBTLE : Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.7f);
        ctx.text(tr, RebornFont.body(e.grade()), g.gradeX(), midY, gradeColor, false);
        drawPing(ctx, tr, e.ping(), g.pingX(), y);
    }

    private static void drawPing(GuiGraphicsExtractor ctx, Font tr, int ping, int x, int rowY) {
        int filled = ping < 40 ? 4 : ping < 90 ? 3 : ping < 160 ? 2 : 1;
        int color = filled >= 3 ? Colors.SUCCESS : filled == 2 ? GOLD : Colors.DANGER;
        int by = rowY + ROW_H / 2 + 5;
        for (int i = 0; i < 4; i++) {
            int hgt = 2 + i * 2;
            int c = i < filled ? color : Colors.withAlpha(Colors.FOREGROUND_MUTED, 0.35f);
            ctx.fill(x + i * 4, by - hgt, x + i * 4 + 2, by, c);
        }
        Component ms = RebornFont.body(String.valueOf(ping));
        ctx.text(tr, ms, x - tr.width(ms) - 6, rowY + (ROW_H - tr.lineHeight) / 2,
            Colors.FOREGROUND_MUTED, false);
    }

    private static void drawStar(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        ctx.fill(cx - r, cy, cx + r + 1, cy + 1, color);
        ctx.fill(cx, cy - r, cx + 1, cy + r + 1, color);
    }

    private static void drawScrollbar(GuiGraphicsExtractor ctx, Geom g, int total, int scrollY) {
        int vh = g.viewH();
        int max = Math.max(0, total - vh);
        if (max <= 0) return;
        int x = g.listX() + g.listW() + 4, top = g.listTop();
        DrawHelpers.roundedRect(ctx, x, top, 3, vh, 1, Colors.SURFACE);
        int thumbH = Math.max(24, (int) ((long) vh * vh / total));
        int travel = vh - thumbH;
        int thumbY = top + (int) ((long) travel * scrollY / max);
        DrawHelpers.roundedRect(ctx, x, thumbY, 3, thumbH, 1, Colors.BORDER_STRONG);
    }

    private static void drawDetail(GuiGraphicsExtractor ctx, Font tr, Geom g, TabEntry e) {
        int cw = Math.min(300, g.pw() - 40), ch = 150;
        int cx = g.px() + (g.pw() - cw) / 2, cy = g.py() + (g.ph() - ch) / 2;
        ctx.fill(g.px(), g.py(), g.px() + g.pw(), g.py() + g.ph(), 0x88000000);
        DrawHelpers.dropShadow(ctx, cx, cy, cw, ch, 6);
        DrawHelpers.roundedOutlinedRect(ctx, cx, cy, cw, ch, 10, Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
        ctx.fill(cx + 12, cy, cx + cw - 12, cy + 2, e.staff() ? GOLD : Colors.ACCENT);

        int nameColor = e.staff() ? GOLD : (e.clanColor() != 0 ? e.clanColor() : Colors.WHITE_PURE);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx + 16, cy + 14);
        ctx.pose().scale(1.3f, 1.3f);
        ctx.text(tr, RebornFont.bold(e.name()), 0, 0, nameColor, false);
        ctx.pose().popMatrix();

        if (!e.known()) {
            ctx.text(tr, RebornFont.body("Aucune information — tu ne connais pas ce joueur (Pas RP)."),
                cx + 16, cy + 44, Colors.FOREGROUND_MUTED, false);
        } else {
            int ly = cy + 44;
            kv(ctx, tr, "Grade", e.grade(), cx + 16, ly); ly += 18;
            kv(ctx, tr, "Clan", e.clan() == null ? "—" : e.clan(), cx + 16, ly); ly += 18;
            kv(ctx, tr, "Niveau", String.valueOf(e.level()), cx + 16, ly); ly += 18;
            kv(ctx, tr, "Âge", e.age() + " ans", cx + 16, ly); ly += 18;
            kv(ctx, tr, "Affinité", e.affinity(), cx + 16, ly);
        }
        Component hint = RebornFont.body("Échap / clic pour fermer");
        ctx.text(tr, hint, cx + cw - tr.width(hint) - 12, cy + ch - 14, Colors.FOREGROUND_MUTED, false);
    }

    private static void kv(GuiGraphicsExtractor ctx, Font tr, String k, String v, int x, int y) {
        ctx.text(tr, RebornFont.body(k), x, y, Colors.FOREGROUND_MUTED, false);
        ctx.text(tr, RebornFont.bold(v), x + 80, y, Colors.WHITE_PURE, false);
    }

    /** Overlay lecture seule (mode hold) — appelé par le mixin PlayerListHud. */
    public static void renderHoldOverlay(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        Geom g = geom(w, h);
        List<TabEntry> all = TablistData.entries(mc.player.getGameProfile().getName());
        drawPanel(ctx, mc.textRenderer, g, all, 3, "", TablistData.rpDate(), false, null, 0, -1, -1);
    }

    // ─── Interaction ─────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        Geom g = geom();
        if (selected == null && my >= g.listTop() && my <= g.listBottom()) {
            scrollY = Math.max(0, Math.min(maxScroll(g), scrollY - (int) (vy * 20)));
            return true;
        }
        return super.mouseScrolled(mx, my, hx, vy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (selected != null) { selected = null; return true; }
            Geom g = geom();
            int[] xs = tabXs(this.textRenderer, g);
            if (my >= g.tabsY() && my < g.tabsY() + TAB_H) {
                for (int i = 0; i < TABS.length; i++) {
                    int w = this.textRenderer.width(RebornFont.arcade(EscData.arcadeSafe(TABS[i], 20)));
                    if (mx >= xs[i] - 6 && mx < xs[i] + w + 6) {
                        activeTab = i; scrollY = 0; selected = null; return true;
                    }
                }
            }
            if (mx >= g.listX() && mx < g.listX() + g.listW() && my >= g.listTop() && my < g.listBottom()) {
                int idx = (int) ((my - g.listTop() + scrollY) / ROW_H);
                List<TabEntry> rows = filtered();
                if (idx >= 0 && idx < rows.size()) { selected = rows.get(idx); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = search != null && search.isFocused();
        if (!typing && (keyCode == 258 /* TAB */
            || (this.client != null && this.client.options.playerListKey.matchesKey(keyCode, scanCode)))) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
