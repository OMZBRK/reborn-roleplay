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
 * Galerie de screenshots, au niveau de la fiche shinobi (DA §4.1, §6). Signature : les <b>tirages</b> pincés sur des fils
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
        g.fill(0, 0, this.width, this.height, 0xA8080406);
        DaKit.etire(g, fr.reborn.hud.menu.settings.ReglagesArt.VIGNETTE, 0, 0, this.width, this.height, 160, 90, DaKit.blanc(0.9f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();
        Font f = this.font;
        GalerieArt.cartouche(g, f, this.width / 2, 6, "Galerie",
            entries.size() + (entries.size() > 1 ? " captures" : " capture"));
        tab(g, f, "Toutes", this.width / 2 - 62, !onlyFav, mouseX, mouseY);
        tab(g, f, "Favoris", this.width / 2 + 2, onlyFav, mouseX, mouseY);
        fr.reborn.hud.menu.settings.ReglagesArt.fermer(g, this.width - 24, Da.in(mouseX, mouseY, this.width - 37, 0, 26, 38));

        if (entries.isEmpty()) {
            DaKit.inter8(g, f, DaKit.inter(onlyFav ? "Aucune capture favorite" : "Aucune capture — F2 pour photographier"),
                this.width / 2f, this.height / 2f, DaKit.CREME_VAL, 1);
        }

        float drop = (1f - ease()) * 24f;
        g.enableScissor(0, gridTop, this.width, gridBottom);
        int hover = -1;
        int firstRow = scrollRow, lastRow = Math.min(rows() - 1, scrollRow + visibleRows());
        // 1) fils tendus (légère courbe) : un seul élément GUI
        LotGui fils = LotGui.ouvrir(g);
        for (int r = firstRow; r <= lastRow; r++) {
            int y = gridTop + 8 + (r - scrollRow) * (cellH + ROW_GAP + 8);
            int x0 = gridX - 16, x1 = gridX + cols * cellW + (cols - 1) * GAP_X + 16;
            for (int x = x0; x < x1; x += 2) {
                double t = (x - x0) / (double) (x1 - x0);
                int yy = y - 4 + (int) Math.round(Math.sin(t * Math.PI) * 3);
                fils.fill(x, yy, x + 2, yy + 1, 0xFFC8B496);
                fils.fill(x, yy + 1, x + 2, yy + 2, 0x60000000);
            }
        }
        fils.fermer();
        // 2) passe-partout (un lot), 3) images, 4) liserés et survol (un lot), 5) dates, 6) pinces et cœurs (un lot)
        java.util.List<int[]> vis = new java.util.ArrayList<>();
        for (int r = firstRow; r <= lastRow; r++) {
            int y = gridTop + 8 + (r - scrollRow) * (cellH + ROW_GAP + 8);
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= entries.size()) break;
                vis.add(new int[]{i, cellX(i), y + (int) (drop * (1 + c * 0.25f))});
            }
        }
        LotBlit mats = LotBlit.ouvrir(g, GalerieArt.TIRAGE, 192, 132);
        for (int[] v : vis) GalerieArt.tirage(mats, v[1], v[2], cellW, cellH);
        mats.fermer();
        int iw = cellW - 2 * FRAME;
        for (int[] v : vis) {
            int ix = v[1] + FRAME, iy = v[2] + FRAME;
            ScreenshotTextures.Tex t = ScreenshotTextures.thumb(entries.get(v[0]).path());
            if (t != null) {
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, t.id(), ix, iy, 0f, 0f, iw, imgH, t.w(), t.h(), t.w(), t.h());
            } else {
                g.fill(ix, iy, ix + iw, iy + imgH, 0xFF2A2022);
            }
        }
        LotGui bords = LotGui.ouvrir(g);
        for (int[] v : vis) {
            int x = v[1], y = v[2], ix = x + FRAME, iy = y + FRAME;
            boolean hov = Da.in(mouseX, mouseY, x, y, cellW, cellH) && ctxEntry == null && confirmDelete == null;
            if (hov) hover = v[0];
            cadre(bords, ix - 1, iy - 1, iw + 2, imgH + 2, 0xFF96826A);
            if (hov) cadre(bords, x - 2, y - 2, cellW + 4, cellH + 4, DaKit.ROUGE);
        }
        bords.fermer();
        for (int[] v : vis) {
            DaKit.inter7(g, f, DaKit.inter(dateOf(entries.get(v[0]))), v[1] + FRAME + 1, v[2] + FRAME + imgH + 6,
                0xFF64503C, 0);
        }
        LotBlit pp = LotBlit.ouvrir(g, GalerieArt.PIECES, 136, 48);
        for (int[] v : vis) {
            int x = v[1], y = v[2];
            boolean on = ScreenshotLibrary.isFavorite(entries.get(v[0]).name());
            boolean hh = Da.in(mouseX, mouseY, x + cellW - FRAME - 12, y + FRAME + imgH + 1, 14, 11);
            pp.blit(x + cellW - FRAME - 9, y + FRAME + imgH + 3, on ? 28 : hh ? 20 : 12, 0, 7, 6);
            pp.blit(x + cellW / 2 - 4, y - 8, 0, 0, 9, 16);
        }
        pp.fermer();
        g.disableScissor();
        if (hover != lastHover) { if (hover >= 0) RebornSounds.playReborn("esc.hover", 1.2f, 0.12f); lastHover = hover; }

        // ascenseur discret
        if (maxScroll() > 0) {
            int th = gridBottom - gridTop, bh = Math.max(16, th * visibleRows() / Math.max(1, rows()));
            int by = gridTop + (th - bh) * scrollRow / maxScroll();
            LotGui a = LotGui.ouvrir(g);
            a.fill(this.width - 8, gridTop, this.width - 7, gridBottom, 0x40C8B496);
            a.fill(this.width - 9, by, this.width - 6, by + bh, DaKit.OR_ETEINT);
            a.fermer();
        }

        if (ctxEntry != null) drawContext(g, f, mouseX, mouseY);
        if (confirmDelete != null) drawConfirm(g, f, mouseX, mouseY);
        DaKit.aides(g, f, fr.reborn.hud.menu.settings.ReglagesArt.PIECES, 0, 0, 120, 40,
            new String[][]{{"Clic", "agrandir"}, {"Clic droit", "actions"}, {"Molette", "défiler"}}, this.width - 14,
            this.height - 10, 1f);
    }

    private static void cadre(LotGui l, int x, int y, int w, int h, int c) {
        l.fill(x, y, x + w, y + 1, c); l.fill(x, y + h - 1, x + w, y + h, c);
        l.fill(x, y, x + 1, y + h, c); l.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void tab(GuiGraphicsExtractor g, Font f, String label, int x, boolean sel, int mx, int my) {
        boolean hov = Da.in(mx, my, x, 46, 60, 16);
        fr.reborn.hud.menu.settings.ReglagesArt.segment(g, x, 46, 58, sel ? 2 : hov ? 1 : 0);
        DaKit.inter8(g, f, DaKit.inter(label), x + 29, 54, sel ? DaKit.CREME : hov ? DaKit.CREME_VAL : DaKit.NEUTRE_TXT, 1);
    }

    /** « 04/10 17:43 » depuis le nom de fichier Minecraft, sinon la date de modification. */
    static String dateOf(Entry e) {
        Matcher m = NAME_DATE.matcher(e.name());
        if (m.find()) return m.group(3) + "/" + m.group(2) + "  " + m.group(4) + ":" + m.group(5);
        return new SimpleDateFormat("dd/MM  HH:mm").format(new Date(e.modified()));
    }

    private static final String[] CTX = { "Ouvrir", "Favori", "Supprimer", "Dossier" };

    private int ctxW() { return 112; }

    private void drawContext(GuiGraphicsExtractor g, Font f, int mx, int my) {
        int w = ctxW(), h = CTX.length * 14 + 8;
        int x = Math.min(ctxX, this.width - w - 4), y = Math.min(ctxY, this.height - h - 4);
        GalerieArt.boite(g, x, y, w, h);
        for (int i = 0; i < CTX.length; i++) {
            int iy = y + 4 + i * 14;
            boolean hov = Da.in(mx, my, x, iy, w, 14);
            if (hov) GalerieArt.ligne(g, x + 4, iy, w - 8);
            String label = i == 1 && ScreenshotLibrary.isFavorite(ctxEntry.name()) ? "Retirer des favoris" : CTX[i];
            DaKit.inter8(g, f, DaKit.inter(label), x + 11, iy + 7, hov ? DaKit.CREME : (i == 2 ? DaKit.ROUGE_CLAIR : DaKit.CREME_VAL), 0);
        }
    }

    private void drawConfirm(GuiGraphicsExtractor g, Font f, int mx, int my) {
        g.fill(0, 0, this.width, this.height, 0x90000000);
        int w = 200, h = 64, x = (this.width - w) / 2, y = (this.height - h) / 2;
        GalerieArt.boite(g, x, y, w, h);
        DaKit.inter9(g, f, DaKit.inter("Supprimer cette capture ?"), x + w / 2f, y + 14, DaKit.CREME, 1);
        DaKit.inter7(g, f, DaKit.inter(dateOf(confirmDelete)), x + w / 2f, y + 26, DaKit.DOUX, 1);
        confirmerBoutons(g, f, x, y, w, mx, my);
    }

    /** Boutons Annuler / Supprimer d'une confirmation (planche 200×64 en x, y). */
    static void confirmerBoutons(GuiGraphicsExtractor g, Font f, int x, int y, int w, int mx, int my) {
        boolean h1 = Da.in(mx, my, x + 20, y + 38, 74, 15), h2 = Da.in(mx, my, x + w - 94, y + 38, 74, 15);
        fr.reborn.hud.menu.settings.ReglagesArt.segment(g, x + 20, y + 38, 74, h1 ? 1 : 0);
        fr.reborn.hud.menu.settings.ReglagesArt.segment(g, x + w - 94, y + 38, 74, 2);
        DaKit.inter8(g, f, DaKit.inter("Annuler"), x + 57, y + 46, h1 ? DaKit.CREME : DaKit.CREME_VAL, 1);
        DaKit.inter8(g, f, DaKit.inter("Supprimer"), x + w - 57, y + 46, h2 ? DaKit.CREME : DaKit.CREME_VAL, 1);
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
        if (Da.in(mx, my, this.width - 37, 0, 26, 38)) { onClose(); return true; }
        if (Da.in(mx, my, this.width / 2 - 62, 46, 58, 16)) { if (onlyFav) { onlyFav = false; refresh(true); click(); } return true; }
        if (Da.in(mx, my, this.width / 2 + 2, 46, 58, 16)) { if (!onlyFav) { onlyFav = true; refresh(true); click(); } return true; }

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
