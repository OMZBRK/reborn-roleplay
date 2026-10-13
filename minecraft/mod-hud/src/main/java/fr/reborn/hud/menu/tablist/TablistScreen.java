package fr.reborn.hud.menu.tablist;

import fr.reborn.hud.ui.DaKit;
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
 * Tablist Reborn — <b>panneau centré</b> (pas plein écran)
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
 *
 * <p>Habillage au niveau de la fiche (DA §4.1, §6) : le registre (名簿) — planche laquée à 90 % posée
 * sur le jeu, sceau vermillon, onglets laqués, lignes à filets, fiche du joueur sur washi. Textures
 * {@code textures/gui/tablist/*} ({@code tools/gen_tablist_textures.py}), texte {@link DaKit}.
 */
public class TablistScreen extends Screen {

    private static final String[] TABS = {"Connaissances", "Amis", "Staff", "Général"};
    private static final int GOLD = 0xFFF2C14E;

    private static net.minecraft.resources.Identifier tex(String n) { return DaKit.tex("tablist", n); }

    private static final net.minecraft.resources.Identifier PLANCHE = tex("planche"), ONGLETS = tex("onglets"),
        LIGNES = tex("lignes"), CHAMP = tex("champ"), FICHE = tex("fiche"), PIECES = tex("pieces");

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
        selfName = (this.minecraft != null && this.minecraft.player != null)
            ? this.minecraft.player.getProfile().name().orElse("Vous") : "Vous";

        Geom g = geom();
        search = new EditBox(this.font, g.searchX() + 2, g.searchY() + 2,
            g.searchW() - 4, 12, Component.literal("Rechercher"));
        search.setBordered(false);
        search.setMaxLength(24);
        search.setHint(Component.literal("Rechercher un joueur…"));
        search.setResponder(s -> { scrollY = 0; });
        this.addWidget(search);
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
        return filtered(TablistData.entries(selfName), activeTab, search != null ? search.getValue() : "");
    }

    private int maxScroll(Geom g) {
        return Math.max(0, filtered().size() * ROW_H - g.viewH());
    }

    /**
     * Fond vanilla sans son flou plein écran (plusieurs ms par image) : le voile sombre du fond de menu reste, le jeu
     * reste visible dessous.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.extractMenuBackground(ctx);
        if (this.minecraft != null) this.minecraft.gui.hud.extractDeferredSubtitles();
    }

    // ─── Rendu écran interactif ──────────────────────────
    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Geom g = geom();
        scrollY = Math.max(0, Math.min(scrollY, maxScroll(g)));

        drawPanel(ctx, this.font, g, TablistData.entries(selfName), activeTab,
            search != null ? search.getValue() : "", TablistData.rpDate(), true, selected, scrollY, mouseX, mouseY);

        // Champ de recherche par-dessus la boîte dessinée par drawPanel.
        if (search != null) search.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /**
     * Dessine le panneau complet. Statique + sans état pour être réutilisé par
     * l'overlay lecture seule (hold). En mode non-interactif : pas de hover, pas
     * de carte détail, placeholder de recherche dessiné en dur.
     */
    static void drawPanel(GuiGraphicsExtractor ctx, Font tr, Geom g, List<TabEntry> all,
                          int activeTab, String searchText, String rpDate, boolean interactive,
                          TabEntry selected, int scrollY, int mouseX, int mouseY) {
        // Léger voile sur le jeu (qui reste visible), puis la planche laquée du registre.
        ctx.fill(0, 0, g.w(), g.h(), 0x30000000);
        DaKit.coupe(ctx, PLANCHE, g.px() - 2, g.py() - 2, g.pw() + 8, g.ph() + 8, 0, 0, 664, 424, 18, 18, 664, 424);

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
        Component title = DaKit.arcade("Reborn Roleplay");
        int tw = DaKit.arcX1(ctx, tr, title, cx, g.py() + 11, DaKit.CREME, 1, true);
        DaKit.blit(ctx, PIECES, cx - tw / 2 - 28, g.py() + 7, 0, 0, 22, 22, 96, 32);
        DaKit.inter7(ctx, tr, DaKit.inter("play.reborn-rp.fr"), cx, g.py() + 26, DaKit.argb(0xC2B59A, 0.8f), 1);

        // Compteur + date RP (calendrier lore) à droite.
        int rightEdge = g.px() + g.pw() - PAD;
        DaKit.inter9(ctx, tr, DaKit.inter(online + " en ligne"), rightEdge, g.py() + 15, DaKit.OR_CLAIR, 2);
        DaKit.inter7(ctx, tr, DaKit.inter(rpDate == null ? "" : rpDate), rightEdge, g.py() + 27, DaKit.argb(0xC2B59A, 0.8f), 2);

        // Champ de recherche laqué (le champ réel est rendu par-dessus par l'écran).
        DaKit.coupeH(ctx, CHAMP, g.searchX() - 3, g.searchY() - 1, g.searchW() + 6, 0, 0, 260, 18, 6, 260, 18);
        if (!interactive && (searchText == null || searchText.isEmpty())) {
            DaKit.inter8(ctx, tr, DaKit.inter("Rechercher un joueur…"), g.searchX() + 2, g.searchY() + 7, DaKit.NEUTRE_TXT, 0);
        }
    }

    private static int[] tabWs(Font tr) {
        int[] w = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) w[i] = Math.round(DaKit.largeur8(tr, DaKit.inter(TABS[i]))) + 22;
        return w;
    }

    private static int[] tabXs(Font tr, Geom g) {
        int gap = 8, total = 0;
        int[] w = tabWs(tr);
        for (int i = 0; i < TABS.length; i++) {
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
        int[] xs = tabXs(tr, g), ws = tabWs(tr);
        for (int i = 0; i < TABS.length; i++) {
            int w = ws[i], x = xs[i], y = g.tabsY() + 3;
            boolean active = i == activeTab;
            boolean hover = interactive && mouseX >= x && mouseX < x + w
                && mouseY >= g.tabsY() && mouseY < g.tabsY() + TAB_H;
            int etat = active ? 2 : hover ? 1 : 0;
            DaKit.coupeH(ctx, ONGLETS, x - 1, y - 1, w + 3, 0, etat * 20, 160, 20, 6, 160, 60);
            DaKit.inter8(ctx, tr, DaKit.inter(TABS[i]), x + w / 2f, y + 8, active || hover ? DaKit.CREME : 0xFFB8A47E, 1);
        }
    }

    /** Étiquettes des colonnes, calculées une fois (police prête). */
    private static DaKit.Etiq E_NOM, E_GRADE, E_PING;

    private static void drawColumnHeaders(GuiGraphicsExtractor ctx, Font tr, Geom g) {
        if (E_NOM == null) {
            E_NOM = DaKit.etiq(tr, "Nom");
            E_GRADE = DaKit.etiq(tr, "Grade");
            E_PING = DaKit.etiq(tr, "Ping");
        }
        float y = g.colHeadY() + 7;
        DaKit.etiq(ctx, tr, E_NOM, g.nameX(), y, DaKit.OR_ETIQ);
        DaKit.etiq(ctx, tr, E_GRADE, g.gradeX(), y, DaKit.OR_ETIQ);
        DaKit.etiq(ctx, tr, E_PING, g.pingX() + 8 - E_PING.w() / 2f, y, DaKit.OR_ETIQ);
        ctx.fill(g.listX(), g.listTop() - 1, g.listX() + g.listW(), g.listTop(), 0xFF6A4E24);
    }

    private static void drawRow(GuiGraphicsExtractor ctx, Font tr, Geom g, TabEntry e, int y,
                                boolean isSel, boolean interactive, int mouseX, int mouseY) {
        boolean hover = interactive && mouseX >= g.listX() && mouseX < g.listX() + g.listW()
            && mouseY >= y && mouseY < y + ROW_H && mouseY >= g.listTop() && mouseY < g.listBottom();
        if (isSel || hover) {
            DaKit.coupeH(ctx, LIGNES, g.listX() - 1, y, g.listW() + 3, 0, isSel ? 20 : 0, 640, 20, 6, 640, 40);
        } else {
            ctx.fill(g.listX(), y + ROW_H - 1, g.listX() + g.listW(), y + ROW_H, DaKit.SEP);
        }
        float mid = y + ROW_H / 2f;
        int mx = g.markerX() - 4, my = Math.round(mid) - 4;

        int nameColor;
        if (e.relation() == TabEntry.Relation.SOI) {
            nameColor = DaKit.CREME;
            DaKit.blit(ctx, PIECES, mx, my, 24, 0, 9, 9, 96, 32);
        } else if (e.staff()) {
            nameColor = 0xFFF2D08A;
            DaKit.blit(ctx, PIECES, mx, my, 34, 0, 9, 9, 96, 32);
        } else if (e.relation() == TabEntry.Relation.AMI) {
            nameColor = e.clanColor() != 0 ? e.clanColor() : 0xFF9CC4B0;
            DaKit.blit(ctx, PIECES, mx, my, 44, 0, 9, 9, 96, 32);
        } else if (e.relation() == TabEntry.Relation.CONNAISSANCE) {
            nameColor = e.clanColor() != 0 ? e.clanColor() : 0xFFE26474;
            DaKit.blit(ctx, PIECES, mx, my, 54, 0, 9, 9, 96, 32);
        } else {
            nameColor = 0xFF8A7A6A;
        }

        int nameMax = g.gradeX() - g.nameX() - 10;
        DaKit.inter9(ctx, tr, DaKit.inter(DaKit.ellipse(tr, e.name(), nameMax)), g.nameX(), mid, nameColor, 0);
        int gradeColor = e.known() ? DaKit.DOUX : 0xFF6A5C52;
        DaKit.inter8(ctx, tr, DaKit.inter(e.grade()), g.gradeX(), mid, gradeColor, 0);
        drawPing(ctx, tr, e.ping(), g.pingX(), y);
    }

    private static void drawPing(GuiGraphicsExtractor ctx, Font tr, int ping, int x, int rowY) {
        int filled = ping < 40 ? 4 : ping < 90 ? 3 : ping < 160 ? 2 : 1;
        int v = filled >= 3 ? 0 : filled == 2 ? 10 : 20;
        int by = rowY + (ROW_H - 9) / 2;
        if (filled == 3) {
            DaKit.blit(ctx, PIECES, x, by, 64, 0, 11, 9, 96, 32);
            DaKit.blit(ctx, PIECES, x + 12, by, 92, 0, 3, 9, 96, 32);
        } else {
            DaKit.blit(ctx, PIECES, x, by, 64, v, 15, 9, 96, 32);
        }
        DaKit.inter7(ctx, tr, DaKit.inter(String.valueOf(ping)), x - 5, rowY + ROW_H / 2f, 0xFF8A7A6A, 2);
    }

    private static void drawScrollbar(GuiGraphicsExtractor ctx, Geom g, int total, int scrollY) {
        int vh = g.viewH();
        int max = Math.max(0, total - vh);
        if (max <= 0) return;
        int x = g.listX() + g.listW() + 5, top = g.listTop();
        for (int yy = top; yy < top + vh; yy += 20) DaKit.blit(ctx, PIECES, x, yy, 24, 12, 4, Math.min(20, top + vh - yy), 96, 32);
        int thumbH = Math.max(24, (int) ((long) vh * vh / total));
        int travel = vh - thumbH;
        int thumbY = top + (int) ((long) travel * scrollY / max);
        DaKit.blit(ctx, PIECES, x - 1, thumbY, 30, 12, 6, 2, 96, 32);
        for (int yy = thumbY + 2; yy < thumbY + thumbH - 2; yy += 8) DaKit.blit(ctx, PIECES, x - 1, yy, 30, 14, 6, Math.min(8, thumbY + thumbH - 2 - yy), 96, 32);
        DaKit.blit(ctx, PIECES, x - 1, thumbY + thumbH - 2, 30, 22, 6, 2, 96, 32);
    }

    private static void drawDetail(GuiGraphicsExtractor ctx, Font tr, Geom g, TabEntry e) {
        int cw = Math.min(300, g.pw() - 40), ch = 132;
        int cx = g.px() + (g.pw() - cw) / 2, cy = g.py() + (g.ph() - ch) / 2;
        ctx.fill(g.px(), g.py(), g.px() + g.pw(), g.py() + g.ph(), 0x88000000);
        DaKit.coupe(ctx, FICHE, cx - 2, cy - 2, cw + 8, ch + 8, 0, 0, 336, 180, 14, 14, 336, 180);

        int nameColor = e.staff() ? 0xFF8E6A30 : DaKit.ENCRE;
        DaKit.arcX1(ctx, tr, DaKit.arcade(DaKit.ellipse(tr, e.name(), cw - 30)), cx + 14, cy + 13, nameColor, 0, false);
        if (e.clanColor() != 0) ctx.fill(cx + 14, cy + 23, cx + 44, cy + 24, e.clanColor());

        if (!e.known()) {
            DaKit.Paragraphe p = DaKit.paragraphe(tr, "Aucune information : tu ne connais pas ce joueur (pas RP).",
                cw - 28, DaKit.S8, 10, 3);
            p.dessiner(ctx, tr, cx + 14, cy + 34, DaKit.ENCRE_DOUCE);
        } else {
            int ly = cy + 38;
            kv(ctx, tr, "Grade", e.grade(), cx + 14, ly, cw); ly += 16;
            kv(ctx, tr, "Clan", e.clan() == null ? "—" : e.clan(), cx + 14, ly, cw); ly += 16;
            kv(ctx, tr, "Age", e.age() + " ans", cx + 14, ly, cw); ly += 16;
            kv(ctx, tr, "Affinite", e.affinity(), cx + 14, ly, cw);
        }
        DaKit.inter7(ctx, tr, DaKit.inter("Échap ou clic pour fermer"), cx + cw - 12, cy + ch - 10, DaKit.ENCRE_DOUCE, 2);
    }

    private static void kv(GuiGraphicsExtractor ctx, Font tr, String k, String v, int x, int y, int cw) {
        DaKit.etiq(ctx, tr, DaKit.etiq(tr, k), x, y, DaKit.ENCRE_DOUCE);
        DaKit.inter9(ctx, tr, DaKit.inter(v == null ? "—" : v), x + 70, y, DaKit.ENCRE, 0);
        ctx.fill(x, y + 7, x + cw - 28, y + 8, 0x30462810);
    }

    /** Overlay lecture seule (mode hold) — appelé par le mixin PlayerListHud. */
    public static void renderHoldOverlay(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Geom g = geom(w, h);
        List<TabEntry> all = TablistData.entries(mc.player.getProfile().name().orElse("Vous"));
        drawPanel(ctx, mc.font, g, all, 3, "", TablistData.rpDate(), false, null, 0, -1, -1);
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y(); int button = event.button();
        if (button == 0) {
            if (selected != null) { selected = null; return true; }
            Geom g = geom();
            int[] xs = tabXs(this.font, g), ws = tabWs(this.font);
            if (my >= g.tabsY() && my < g.tabsY() + TAB_H) {
                for (int i = 0; i < TABS.length; i++) {
                    if (mx >= xs[i] && mx < xs[i] + ws[i]) {
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        boolean typing = search != null && search.isFocused();
        if (!typing && (keyCode == 258 /* TAB */
            || (this.minecraft != null && this.minecraft.options.keyPlayerList.matches(event)))) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
