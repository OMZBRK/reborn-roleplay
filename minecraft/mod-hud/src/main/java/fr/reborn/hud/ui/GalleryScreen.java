package fr.reborn.hud.ui;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.screenshot.ScreenshotLibrary;
import fr.reborn.hud.screenshot.ScreenshotLibrary.Entry;
import fr.reborn.hud.screenshot.ScreenshotTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Galerie de screenshots (façon « Picture Browser ») : grille de vignettes,
 * onglets Tous / Favoris, clic = ouvrir, clic droit = menu contextuel
 * (Ouvrir / Favori / Supprimer / Dossier). Ouverte via une keybind.
 */
public class GalleryScreen extends Screen {

    private static final int COLS = 4, GAP = 10;
    private static final int CTX_W = 120, CTX_ROW = 14;

    private final Screen parent;
    private boolean onlyFav = false;
    private int scrollRow = 0;
    private List<Entry> entries;
    private long openedAt;

    // Menu contextuel.
    private Entry ctxEntry;
    private int ctxX, ctxY;

    private int px, py, pw, ph;

    public GalleryScreen(Screen parent) {
        super(Component.literal("Galerie"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(560, this.width - 40);
        ph = Math.min(360, this.height - 40);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        refresh();
        openedAt = System.currentTimeMillis();
    }

    private void refresh() {
        entries = ScreenshotLibrary.list(onlyFav);
        scrollRow = 0;
    }

    private float ease() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 200f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private int gridX() { return px + 12; }
    private int gridTop() { return py + 40; }
    private int gridRight() { return px + pw - 12; }
    private int gridBottom() { return py + ph - 10; }
    private int cellW() { return (gridRight() - gridX() - (COLS - 1) * GAP) / COLS; }
    private int cellImgH() { return cellW() * 9 / 16; }
    private int cellH() { return cellImgH() + 14; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xC8000000);
        Font tr = this.font;

        ctx.pose().pushMatrix();
        ctx.pose().translate(0, (1f - ease()) * 20f);

        // Panneau.
        ctx.fill(px, py, px + pw, py + ph, 0xF2120A0E);
        outline(ctx, px, py, pw, ph, Colors.BORDER_STRONG);

        // Header.
        ctx.fill(px, py, px + pw, py + 30, 0xFF170C10);
        ctx.fill(px, py + 30, px + pw, py + 31, Colors.ACCENT);
        ctx.text(tr, RebornFont.bold("GALERIE"), px + 12, py + 11, Colors.GOLD, false);
        tab(ctx, tr, "Tous", px + 90, py + 8, !onlyFav, mouseX, mouseY);
        tab(ctx, tr, "Favoris", px + 140, py + 8, onlyFav, mouseX, mouseY);
        boolean closeHov = in(mouseX, mouseY, px + pw - 22, py + 8, 16, 16);
        ctx.text(tr, Component.literal("✕"), px + pw - 18, py + 11, closeHov ? Colors.ACCENT_HOVER : Colors.FOREGROUND_MUTED, false);

        // Grille.
        ctx.enableScissor(px + 6, gridTop(), px + pw - 6, gridBottom());
        int cw = cellW(), cih = cellImgH(), ch = cellH();
        if (entries.isEmpty()) {
            ctx.text(tr, Component.literal("Aucun screenshot" + (onlyFav ? " favori" : "") + "."),
                gridX(), gridTop() + 10, Colors.FOREGROUND_MUTED, false);
        }
        for (int i = 0; i < entries.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int x = gridX() + col * (cw + GAP);
            int y = gridTop() + row * (ch + GAP) - scrollRow * (ch + GAP);
            if (y + ch < gridTop() || y > gridBottom()) continue;
            renderCell(ctx, tr, entries.get(i), x, y, cw, cih, mouseX, mouseY);
        }
        ctx.disableScissor();

        // Menu contextuel.
        if (ctxEntry != null) renderContext(ctx, tr, mouseX, mouseY);

        ctx.pose().popMatrix();
    }

    private void renderCell(GuiGraphicsExtractor ctx, Font tr, Entry e, int x, int y, int cw, int cih,
                            int mouseX, int mouseY) {
        boolean hov = in(mouseX, mouseY, x, y, cw, cih);
        ctx.fill(x - 1, y - 1, x + cw + 1, y + cih + 1, hov ? Colors.ACCENT : Colors.BORDER);
        ScreenshotTextures.Tex t = ScreenshotTextures.get(e.path());
        if (t != null) {
            // Image entière mise à l'échelle dans la cellule (region = image complète).
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, t.id(), x, y, cw, cih, 0f, 0f, t.w(), t.h(), t.w(), t.h());
        } else {
            ctx.fill(x, y, x + cw, y + cih, 0xFF1A0E12);
            ctx.text(tr, Component.literal("…"), x + cw / 2 - 2, y + cih / 2 - 4, Colors.FOREGROUND_MUTED, false);
        }
        // Cœur favori.
        boolean fav = ScreenshotLibrary.isFavorite(e.name());
        ctx.text(tr, Component.literal(fav ? "♥" : "♡"), x + cw - 12, y + 3, fav ? Colors.ACCENT_HOVER : 0xFFDDDDDD, false);
        // Nom (tronqué).
        String n = e.name().length() > 22 ? e.name().substring(0, 21) + "…" : e.name();
        ctx.text(tr, Component.literal(n), x, y + cih + 3, Colors.FOREGROUND_SUBTLE, false);
    }

    private void renderContext(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
        String[] items = { "Ouvrir", ScreenshotLibrary.isFavorite(ctxEntry.name()) ? "Retirer favori" : "Favori",
            "Supprimer", "Dossier" };
        int h = items.length * CTX_ROW + 4;
        int x = Math.min(ctxX, this.width - CTX_W - 2), y = Math.min(ctxY, this.height - h - 2);
        ctx.fill(x, y, x + CTX_W, y + h, Colors.BACKDROP_85);
        outline(ctx, x, y, CTX_W, h, Colors.BORDER_STRONG);
        for (int i = 0; i < items.length; i++) {
            int iy = y + 2 + i * CTX_ROW;
            boolean hov = in(mouseX, mouseY, x, iy, CTX_W, CTX_ROW);
            if (hov) ctx.fill(x + 1, iy, x + CTX_W - 1, iy + CTX_ROW, Colors.ACCENT_SOFT);
            int col = i == 2 ? Colors.ACCENT_HOVER : Colors.FOREGROUND_SUBTLE;
            ctx.text(tr, Component.literal(items[i]), x + 8, iy + 3, hov ? Colors.WHITE_PURE : col, false);
        }
    }

    private void tab(GuiGraphicsExtractor ctx, Font tr, String label, int x, int y, boolean sel, int mouseX, int mouseY) {
        int w = tr.width(label) + 8;
        boolean hov = in(mouseX, mouseY, x, y, w, 16);
        if (sel) ctx.fill(x, y, x + w, y + 16, Colors.ACCENT);
        else if (hov) ctx.fill(x, y, x + w, y + 16, 0x22FFFFFF);
        ctx.text(tr, Component.literal(label), x + 4, y + 4, sel ? Colors.WHITE_PURE : Colors.FOREGROUND_MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mxi = (int) mx, myi = (int) my;

        // Menu contextuel ouvert.
        if (ctxEntry != null) {
            handleContextClick(mxi, myi);
            ctxEntry = null;
            return true;
        }

        if (in(mxi, myi, px + pw - 22, py + 8, 16, 16)) { close(); return true; }
        if (in(mxi, myi, px + 90, py + 8, this.font.width("Tous") + 8, 16)) { onlyFav = false; refresh(); return true; }
        if (in(mxi, myi, px + 140, py + 8, this.font.width("Favoris") + 8, 16)) { onlyFav = true; refresh(); return true; }

        Entry hit = cellAt(mxi, myi);
        if (hit != null) {
            int idx = entries.indexOf(hit);
            int col = idx % COLS, row = idx / COLS;
            int cx = gridX() + col * (cellW() + GAP), cy = gridTop() + row * (cellH() + GAP) - scrollRow * (cellH() + GAP);
            if (button == 1) { // clic droit → menu contextuel
                ctxEntry = hit; ctxX = mxi; ctxY = myi; return true;
            }
            // Cœur (coin haut-droit) → favori, sinon → vue détail.
            if (in(mxi, myi, cx + cellW() - 14, cy, 14, 12)) {
                ScreenshotLibrary.toggleFavorite(hit.name());
                if (onlyFav) refresh();
            } else {
                Minecraft.getInstance().setScreen(new ScreenshotDetailScreen(this, entries, entries.indexOf(hit)));
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void handleContextClick(int mxi, int myi) {
        String[] items = { "Ouvrir", "Favori", "Supprimer", "Dossier" };
        int h = items.length * CTX_ROW + 4;
        int x = Math.min(ctxX, this.width - CTX_W - 2), y = Math.min(ctxY, this.height - h - 2);
        for (int i = 0; i < items.length; i++) {
            int iy = y + 2 + i * CTX_ROW;
            if (in(mxi, myi, x, iy, CTX_W, CTX_ROW)) {
                switch (i) {
                    case 0 -> Util.getPlatform().open(ctxEntry.path().toFile());
                    case 1 -> { ScreenshotLibrary.toggleFavorite(ctxEntry.name()); if (onlyFav) refresh(); }
                    case 2 -> { ScreenshotLibrary.delete(ctxEntry); refresh(); }
                    case 3 -> Util.getPlatform().open(ScreenshotLibrary.dir().toFile());
                }
                return;
            }
        }
    }

    private Entry cellAt(int mxi, int myi) {
        if (myi < gridTop() || myi > gridBottom()) return null;
        int cw = cellW(), ch = cellH();
        for (int i = 0; i < entries.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int x = gridX() + col * (cw + GAP);
            int y = gridTop() + row * (ch + GAP) - scrollRow * (ch + GAP);
            if (in(mxi, myi, x, y, cw, cellImgH())) return entries.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int rows = (entries.size() + COLS - 1) / COLS;
        int maxScroll = Math.max(0, rows - 1);
        scrollRow = Math.max(0, Math.min(maxScroll, scrollRow - (int) Math.signum(v)));
        return true;
    }

    @Override
    public void removed() {
        ScreenshotTextures.clear();
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() { return false; }

    private static void outline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c);
        ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c);
        ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
