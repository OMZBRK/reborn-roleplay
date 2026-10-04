package fr.reborn.hud.ui;

import fr.reborn.hud.menu.RebornSounds;
import fr.reborn.hud.screenshot.ScreenshotLibrary;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Galerie de screenshots — DA Reborn. Les captures sont des <b>tirages</b> pincés sur des fils
 * (une rangée par fil), sous la plaque suspendue « Galerie » et ses onglets Toutes / Favoris.
 * Clic = vue détail, cœur = favori, clic droit = actions (ouvrir, favori, supprimer, dossier),
 * molette = rangée suivante. Même fonctionnement qu'avant.
 *
 * <p>Corrigé : vignettes réduites chargées en arrière-plan ({@link ScreenshotTextures#thumb}),
 * plus de « … » ni de gel ; les textures ne sont plus libérées en allant dans la vue détail ;
 * défilement borné et conservé au retour ; date au lieu du nom de fichier ; suppression confirmée.
 */
public class GalleryScreen extends Screen {

    private static final Pattern NAME_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})_(\\d{2})\\.(\\d{2})");
    private static final int GAP_X = 14, ROW_GAP = 16, CAP_H = 12, FRAME = 4;

    private final Screen parent;
    private boolean onlyFav = false;
    private static int scrollRow = 0;          // conservé entre deux ouvertures (retour de la vue détail)
    private List<Entry> entries;
    private long openedAt;
    private boolean toChild = false;

    // Menu contextuel / confirmation.
    private Entry ctxEntry, confirmDelete;
    private int ctxX, ctxY;
    private int lastHover = -1;

    // Géométrie
    private int cols, cellW, imgH, cellH, gridX, gridTop, gridBottom;

    public GalleryScreen(Screen parent) {
        super(Component.literal("Galerie"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        toChild = false;
        refresh(false);
        if (openedAt == 0) { openedAt = System.currentTimeMillis(); RebornSounds.playReborn("esc.open", 1.1f, 0.4f); }
    }

    private void refresh(boolean resetScroll) {
        entries = ScreenshotLibrary.list(onlyFav);
        if (resetScroll) scrollRow = 0;
        layout();
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
    }

    private void layout() {
        int areaW = Math.min(this.width - 40, 760);
        cols = areaW >= 560 ? 4 : areaW >= 400 ? 3 : 2;
        cellW = (areaW - (cols - 1) * GAP_X) / cols;
        imgH = (cellW - 2 * FRAME) * 9 / 16;
        cellH = imgH + 2 * FRAME + CAP_H;
        gridX = (this.width - (cols * cellW + (cols - 1) * GAP_X)) / 2;
        gridTop = 74;
        gridBottom = this.height - 24;
    }

    private int rows() { return (entries.size() + cols - 1) / cols; }
    private int visibleRows() { return Math.max(1, (gridBottom - gridTop + ROW_GAP) / (cellH + ROW_GAP + 8)); }
    private int maxScroll() { return Math.max(0, rows() - visibleRows()); }

    private int cellX(int i) { return gridX + (i % cols) * (cellW + GAP_X); }
    private int cellY(int i) { return gridTop + 8 + (i / cols - scrollRow) * (cellH + ROW_GAP + 8); }

    private float ease() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 260f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // ── rendu ────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xB0080406);
        g.fillGradient(0, 0, this.width, 90, 0x60401016, 0x00000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();
        Font f = this.font;
        float s = Da.small();
        Da.header(g, f, this.width / 2, 12, "Galerie", entries.size() + (entries.size() > 1 ? " captures" : " capture"));
        tab(g, f, "Toutes", this.width / 2 - 64, !onlyFav, mouseX, mouseY);
        tab(g, f, "Favoris", this.width / 2 + 4, onlyFav, mouseX, mouseY);
        // fermer
        int cx = this.width - 24;
        boolean ch = Da.in(mouseX, mouseY, cx, 10, 14, 14);
        g.fill(cx, 10, cx + 14, 24, ch ? Da.RED : 0xF00E0A0C);
        Da.outline(g, cx, 10, 14, 14, ch ? Da.GOLD : Da.GOLD_D);
        for (int k = -3; k <= 3; k++) {
            g.fill(cx + 7 + k, 17 + k, cx + 8 + k, 18 + k, Da.CREAM);
            g.fill(cx + 7 + k, 17 - k, cx + 8 + k, 18 - k, Da.CREAM);
        }

        if (entries.isEmpty()) {
            Da.text(g, f, onlyFav ? "Aucune capture favorite" : "Aucune capture  -  F2 pour photographier",
                this.width / 2f, this.height / 2f, s, Da.MUTED, 1);
        }

        float drop = (1f - ease()) * 24f;
        g.enableScissor(0, gridTop, this.width, gridBottom);
        int hover = -1;
        int firstRow = scrollRow, lastRow = Math.min(rows() - 1, scrollRow + visibleRows());
        for (int r = firstRow; r <= lastRow; r++) {
            int y = gridTop + 8 + (r - scrollRow) * (cellH + ROW_GAP + 8);
            // fil tendu (légère courbe) + clous
            int x0 = gridX - 16, x1 = gridX + cols * cellW + (cols - 1) * GAP_X + 16;
            for (int x = x0; x < x1; x += 2) {
                double t = (x - x0) / (double) (x1 - x0);
                int yy = y - 4 + (int) Math.round(Math.sin(t * Math.PI) * 3);
                g.fill(x, yy, x + 2, yy + 1, 0xFFC8B496);
            }
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= entries.size()) break;
                int x = cellX(i), cy = y + (int) (drop * (1 + c * 0.25f));
                boolean hov = Da.in(mouseX, mouseY, x, cy, cellW, cellH) && ctxEntry == null && confirmDelete == null;
                if (hov) hover = i;
                drawPrint(g, f, entries.get(i), x, cy, hov, mouseX, mouseY);
            }
        }
        g.disableScissor();
        if (hover != lastHover) { if (hover >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hover; }

        // ascenseur discret
        if (maxScroll() > 0) {
            int th = gridBottom - gridTop, bh = Math.max(16, th * visibleRows() / Math.max(1, rows()));
            int by = gridTop + (th - bh) * scrollRow / maxScroll();
            g.fill(this.width - 8, gridTop, this.width - 7, gridBottom, 0x40C8B496);
            g.fill(this.width - 9, by, this.width - 6, by + bh, Da.GOLD_D);
        }

        if (ctxEntry != null) drawContext(g, f, mouseX, mouseY);
        if (confirmDelete != null) drawConfirm(g, f, mouseX, mouseY);
        Da.hint(g, f, this.width, this.height, "Clic : agrandir     Clic droit : actions     Molette : defiler");
    }

    private void tab(GuiGraphicsExtractor g, Font f, String label, int x, boolean sel, int mx, int my) {
        g.fill(x + 30, 40, x + 31, 46, Da.CORD);
        Da.plate(g, f, x, 46 + (sel ? 2 : 0), 60, 13, label, sel, Da.in(mx, my, x, 46, 60, 15), true);
    }

    /** Tirage : passe-partout crème, image, bandeau date + cœur, pince en bois. */
    private void drawPrint(GuiGraphicsExtractor g, Font f, Entry e, int x, int y, boolean hov, int mx, int my) {
        g.fill(x + 2, y + 3, x + cellW + 2, y + cellH + 3, 0x80000000);
        g.fill(x, y, x + cellW, y + cellH, 0xFFF4EEDC);
        if (hov) Da.outline(g, x - 1, y - 1, cellW + 2, cellH + 2, Da.RED);
        int ix = x + FRAME, iy = y + FRAME, iw = cellW - 2 * FRAME;
        ScreenshotTextures.Tex t = ScreenshotTextures.thumb(e.path());
        if (t != null) {
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, t.id(), ix, iy, 0f, 0f, iw, imgH, t.w(), t.h(), t.w(), t.h());
        } else {
            g.fill(ix, iy, ix + iw, iy + imgH, 0xFF2A2022);
            int dots = (int) (System.currentTimeMillis() / 300 % 4);
            Da.text(g, f, ".".repeat(dots), ix + iw / 2f, iy + imgH / 2f - 3, 1f, Da.MUTED, 1);
        }
        Da.outline(g, ix - 1, iy - 1, iw + 2, imgH + 2, 0xFF96826A);
        float s = Da.small();
        Da.text(g, f, dateOf(e), x + FRAME + 1, y + FRAME + imgH + 3, s, 0xFF64503C, 0);
        heart(g, x + cellW - FRAME - 9, y + FRAME + imgH + 3, ScreenshotLibrary.isFavorite(e.name()),
            Da.in(mx, my, x + cellW - FRAME - 12, y + FRAME + imgH + 1, 14, 11));
        // pince
        int px = x + cellW / 2 - 3;
        g.fill(px, y - 7, px + 7, y + 6, 0xFFB0804F);
        Da.outline(g, px, y - 7, 7, 13, 0xFF6E4828);
        g.fill(px + 3, y - 7, px + 4, y + 6, 0xFF6E4828);
    }

    private static void heart(GuiGraphicsExtractor g, int x, int y, boolean on, boolean hov) {
        String[] rows = on ? new String[]{"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"}
                           : new String[]{"0110110", "1001001", "1000001", "0100010", "0010100", "0001000"};
        int c = on ? Da.RED : (hov ? Da.RED : 0xFF96826A);
        for (int j = 0; j < rows.length; j++)
            for (int i = 0; i < 7; i++)
                if (rows[j].charAt(i) == '1') g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
    }

    /** « 04/10 17:43 » depuis le nom de fichier Minecraft, sinon la date de modification. */
    static String dateOf(Entry e) {
        Matcher m = NAME_DATE.matcher(e.name());
        if (m.find()) return m.group(3) + "/" + m.group(2) + "  " + m.group(4) + ":" + m.group(5);
        return new SimpleDateFormat("dd/MM  HH:mm").format(new Date(e.modified()));
    }

    private static final String[] CTX = { "Ouvrir", "Favori", "Supprimer", "Dossier" };

    private int ctxW() { return 104; }

    private void drawContext(GuiGraphicsExtractor g, Font f, int mx, int my) {
        int w = ctxW(), h = CTX.length * 14 + 8;
        int x = Math.min(ctxX, this.width - w - 4), y = Math.min(ctxY, this.height - h - 4);
        Da.panel(g, x, y, w, h);
        float s = Da.small();
        for (int i = 0; i < CTX.length; i++) {
            int iy = y + 4 + i * 14;
            boolean hov = Da.in(mx, my, x, iy, w, 14);
            if (hov) { g.fill(x + 4, iy + 1, x + w - 4, iy + 13, Da.RED); g.fill(x + 4, iy + 1, x + 6, iy + 13, Da.GOLD); }
            String label = i == 1 && ScreenshotLibrary.isFavorite(ctxEntry.name()) ? "Retirer des favoris" : CTX[i];
            Da.text(g, f, label, x + 11, iy + 4, s, hov ? Da.CREAM : (i == 2 ? 0xFFE6826E : Da.MUTED), 0);
        }
    }

    private void drawConfirm(GuiGraphicsExtractor g, Font f, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        int w = 200, h = 64, x = (this.width - w) / 2, y = (this.height - h) / 2;
        Da.panel(g, x, y, w, h);
        Da.text(g, f, "Supprimer cette capture ?", x + w / 2f, y + 12, Da.small(), Da.CREAM, 1);
        Da.text(g, f, dateOf(confirmDelete), x + w / 2f, y + 22, Da.small(), Da.MUTED, 1);
        Da.plate(g, f, x + 20, y + 38, 74, 15, "Annuler", false, Da.in(mx, my, x + 20, y + 38, 74, 15), true);
        Da.plate(g, f, x + w - 94, y + 38, 74, 15, "Supprimer", true, Da.in(mx, my, x + w - 94, y + 38, 74, 15), true);
    }

    // ── interactions ─────────────────────────────────────────────
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), button = event.button();

        if (confirmDelete != null) {
            int w = 200, h = 64, x = (this.width - w) / 2, y = (this.height - h) / 2;
            if (Da.in(mx, my, x + w - 94, y + 38, 74, 15)) {
                ScreenshotLibrary.delete(confirmDelete);
                RebornSounds.playReborn("sacoche.deny", 0.9f, 0.4f);
                refresh(false);
            }
            confirmDelete = null;
            return true;
        }
        if (ctxEntry != null) {
            handleContextClick(mx, my);
            ctxEntry = null;
            return true;
        }
        if (Da.in(mx, my, this.width - 24, 10, 14, 14)) { onClose(); return true; }
        if (Da.in(mx, my, this.width / 2 - 64, 46, 60, 15)) { if (onlyFav) { onlyFav = false; refresh(true); click(); } return true; }
        if (Da.in(mx, my, this.width / 2 + 4, 46, 60, 15)) { if (!onlyFav) { onlyFav = true; refresh(true); click(); } return true; }

        int idx = cellAt(mx, my);
        if (idx >= 0) {
            Entry hit = entries.get(idx);
            if (button == 1) { ctxEntry = hit; ctxX = mx; ctxY = my; click(); return true; }
            int x = cellX(idx), y = cellY(idx);
            if (Da.in(mx, my, x + cellW - FRAME - 12, y + FRAME + imgH + 1, 14, 11)) {
                ScreenshotLibrary.toggleFavorite(hit.name());
                RebornSounds.playReborn("esc.charm", 1.2f, 0.4f);
                if (onlyFav) refresh(false);
            } else {
                click();
                toChild = true;
                Minecraft.getInstance().setScreenAndShow(new ScreenshotDetailScreen(this, entries, idx));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handleContextClick(int mx, int my) {
        int w = ctxW(), h = CTX.length * 14 + 8;
        int x = Math.min(ctxX, this.width - w - 4), y = Math.min(ctxY, this.height - h - 4);
        for (int i = 0; i < CTX.length; i++) {
            int iy = y + 4 + i * 14;
            if (Da.in(mx, my, x, iy, w, 14)) {
                click();
                switch (i) {
                    case 0 -> Util.getPlatform().openUri(ctxEntry.path().toUri());
                    case 1 -> { ScreenshotLibrary.toggleFavorite(ctxEntry.name()); if (onlyFav) refresh(false); }
                    case 2 -> confirmDelete = ctxEntry;
                    case 3 -> Util.getPlatform().openUri(ScreenshotLibrary.dir().toUri());
                }
                return;
            }
        }
    }

    private int cellAt(int mx, int my) {
        if (my < gridTop || my > gridBottom) return -1;
        for (int i = scrollRow * cols; i < entries.size(); i++) {
            int x = cellX(i), y = cellY(i);
            if (y > gridBottom) break;
            if (Da.in(mx, my, x, y, cellW, cellH)) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int before = scrollRow;
        scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(v)));
        if (scrollRow != before) RebornSounds.playReborn("esc.page", 1.1f, 0.3f);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if ((ctxEntry != null || confirmDelete != null) && event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            ctxEntry = null; confirmDelete = null; return true;
        }
        return super.keyPressed(event);
    }

    private static void click() { RebornSounds.playReborn("esc.select", 1.1f, 0.4f); }

    @Override
    public void removed() {
        if (!toChild) ScreenshotTextures.clear();
    }

    @Override
    public void onClose() {
        toChild = false;
        RebornSounds.playReborn("esc.close", 1.1f, 0.35f);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
