package fr.reborn.ost.ui;

import fr.reborn.ost.RebornOstClient;
import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.audio.OstTrackMeta;
import fr.reborn.ost.config.OstConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Menu OST compact — 3 panneaux (façon maquette Reborn) : sidebar catégories à
 * gauche, liste des pistes en haut-droite, lecteur (now-playing + volume +
 * contrôles) en bas-droite. Palette Akatsuki dessinée à la main.
 */
public class OstScreen extends Screen {

    private static final int DIM        = 0xC0000000;
    private static final int ZONE_BG    = 0xF21C0A0E;
    private static final int ZONE_BORD  = 0xFF6E1B27;
    private static final int ACCENT     = 0xFFA0182B;
    private static final int ACCENT_HOV = 0xFFC2364A;
    private static final int GOLD       = 0xFFD9A95E;
    private static final int TEXT       = 0xFFE8DCC8;
    private static final int TEXT_MUTED = 0xFF9A8B78;
    private static final int ROW_HOVER  = 0x22FFFFFF;
    private static final int ROW_PLAY   = 0x66A0182B;

    private static final Identifier FRAME = Identifier.of("reborn-ost", "textures/gui/ost_frame.png");
    private static final int FRAME_W = 600, FRAME_H = 384;
    private static final int ROW_H = 22, THUMB = 18, COVER_PX = 64;
    private static final int CAT_ROW_H = 24;

    private final Screen parent;
    private final OstLibrary library;
    private final OstAudioEngine engine;
    private final OstConfig config;

    private OstCategory selectedCategory = OstCategory.APAISANT;
    private TextFieldWidget searchField;
    private int scrollOffset = 0;

    // Géométrie des 3 zones.
    private int px, py, pw, ph;
    private int sbX, sbY, sbW, sbH;
    private int mainX, mainY, mainW, mainH;
    private int footY, footH;

    public OstScreen(Screen parent) {
        super(Text.literal("Reborn OST"));
        this.parent = parent;
        this.library = RebornOstClient.library();
        this.engine = RebornOstClient.audioEngine();
        this.config = RebornOstClient.config();
    }

    private void layout() {
        pw = Math.min(FRAME_W, this.width - 50);
        ph = Math.min(FRAME_H, this.height - 50);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        // Zones calées sur la maquette (fractions du cadre 600x384).
        sbX = px + fx(8);    sbW = fx(128);  sbY = py + fy(8);   sbH = fy(368);
        mainX = px + fx(148); mainW = fx(444); mainY = py + fy(8); mainH = fy(300);
        footY = py + fy(320); footH = fy(56);
    }

    private int fx(int v) { return Math.round(v / (float) FRAME_W * pw); }
    private int fy(int v) { return Math.round(v / (float) FRAME_H * ph); }

    @Override
    protected void init() {
        layout();
        int sw = 132;
        searchField = new TextFieldWidget(this.textRenderer,
            mainX + mainW - sw - 24, mainY + 5, sw, 14, Text.literal("Rechercher"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Rechercher…"));
        this.addDrawableChild(searchField);
    }

    // ─── Rendu ───

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        ctx.fill(0, 0, this.width, this.height, DIM);
        TextRenderer tr = this.textRenderer;

        // Cadre = ta maquette Aseprite (fallback : panneaux dessinés).
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getResourceManager().getResource(FRAME).isPresent()) {
            ctx.drawTexture(FRAME, px, py, 0f, 0f, pw, ph, FRAME_W, FRAME_H);
        } else {
            panel(ctx, sbX, sbY, sbW, sbH);
            panel(ctx, mainX, mainY, mainW, mainH);
            panel(ctx, mainX, footY, mainW, footH);
        }

        renderSidebar(ctx, tr, mouseX, mouseY);
        renderMain(ctx, tr, mouseX, mouseY);
        renderFooter(ctx, tr, mouseX, mouseY);
    }

    private void renderSidebar(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        ctx.drawText(tr, Text.literal("REBORN").styled(s -> s.withBold(true)), sbX + 12, sbY + 10, GOLD, false);
        ctx.drawText(tr, Text.literal("OST").styled(s -> s.withBold(true)), sbX + 12, sbY + 20, ACCENT_HOV, false);
        ctx.fill(sbX + 8, sbY + 34, sbX + sbW - 8, sbY + 35, ZONE_BORD);

        int cy = sbY + 42;
        for (OstCategory cat : OstCategory.values()) {
            boolean sel = cat == selectedCategory && searchBlank();
            boolean hov = in(mouseX, mouseY, sbX + 6, cy, sbW - 12, CAT_ROW_H);
            if (sel) ctx.fill(sbX + 6, cy, sbX + sbW - 6, cy + CAT_ROW_H, ACCENT);
            else if (hov) ctx.fill(sbX + 6, cy, sbX + sbW - 6, cy + CAT_ROW_H, ROW_HOVER);
            if (sel) ctx.fill(sbX + 6, cy, sbX + 8, cy + CAT_ROW_H, GOLD);
            ctx.drawText(tr, Text.literal(cat.displayName()), sbX + 14, cy + 8,
                sel ? 0xFFFFFFFF : TEXT_MUTED, false);
            cy += CAT_ROW_H + 2;
        }
    }

    private void renderMain(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        // Header du panneau liste : titre catégorie + recherche + close.
        String head = searchBlank() ? selectedCategory.displayName() : "Recherche";
        ctx.drawText(tr, Text.literal(head).styled(s -> s.withBold(true)), mainX + 10, mainY + 8, GOLD, false);
        ctx.fill(mainX + mainW - 158, mainY + 4, mainX + mainW - 22, mainY + 19, 0x40000000);
        boolean closeHov = in(mouseX, mouseY, mainX + mainW - 18, mainY + 5, 14, 14);
        ctx.drawText(tr, Text.literal("✕"), mainX + mainW - 15, mainY + 7, closeHov ? ACCENT_HOV : TEXT_MUTED, false);
        ctx.fill(mainX + 6, mainY + 22, mainX + mainW - 6, mainY + 23, ZONE_BORD);

        List<OstTrack> tracks = resolveVisibleTracks();
        int listTop = mainY + 26;
        int listBottom = mainY + mainH - 5;
        ctx.enableScissor(mainX + 5, listTop, mainX + mainW - 5, listBottom);
        for (int i = 0; i < tracks.size(); i++) {
            int rowY = listTop + (i - scrollOffset) * ROW_H;
            if (rowY + ROW_H < listTop || rowY > listBottom) continue;
            renderRow(ctx, tr, tracks.get(i), rowY, listBottom, mouseX, mouseY);
        }
        ctx.disableScissor();
    }

    private void renderRow(DrawContext ctx, TextRenderer tr, OstTrack track,
                           int rowY, int listBottom, int mouseX, int mouseY) {
        int left = mainX + 6, right = mainX + mainW - 6;
        boolean hovered = in(mouseX, mouseY, left, rowY, right - left, ROW_H) && mouseY < listBottom;
        boolean playing = engine.currentTrack().map(t -> t.trackId().equals(track.trackId())).orElse(false);
        if (playing) ctx.fill(left, rowY, right, rowY + ROW_H, ROW_PLAY);
        else if (hovered) ctx.fill(left, rowY, right, rowY + ROW_H, ROW_HOVER);

        int thumbX = left + 3, thumbY = rowY + (ROW_H - THUMB) / 2;
        drawCover(ctx, track, thumbX, thumbY, THUMB);

        String title = OstTrackMeta.title(track.trackId(), track.displayName());
        ctx.drawText(tr, Text.literal((playing ? "▶ " : "") + title), thumbX + THUMB + 8, rowY + 7,
            playing ? 0xFFFFFFFF : TEXT, false);

        boolean fav = config.isFavorite(track.trackId());
        ctx.drawText(tr, Text.literal(fav ? "♥" : "♡"), right - 14, rowY + 7, fav ? ACCENT_HOV : TEXT_MUTED, false);
        String dur = OstTrackMeta.formatDuration(OstTrackMeta.duration(track.trackId()));
        if (!dur.isEmpty()) {
            ctx.drawText(tr, Text.literal(dur), right - 24 - tr.getWidth(dur), rowY + 7, TEXT_MUTED, false);
        }
    }

    private void renderFooter(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        var cur = engine.currentTrack();
        int npX = mainX + 8;
        if (cur.isPresent()) {
            OstTrack t = cur.get();
            drawCover(ctx, t, npX, footY + 10, 32);
            ctx.drawText(tr, Text.literal("♪ " + OstTrackMeta.title(t.trackId(), t.displayName())),
                npX + 38, footY + 12, GOLD, false);
            ctx.drawText(tr, Text.literal(t.category().displayName()), npX + 38, footY + 26, TEXT_MUTED, false);
        } else {
            ctx.drawText(tr, Text.literal("— Aucune piste —"), npX, footY + 20, TEXT_MUTED, false);
        }

        int rx = mainX + mainW;
        boolean stopHov = in(mouseX, mouseY, rx - 56, footY + 8, 48, 14);
        ctx.fill(rx - 56, footY + 8, rx - 8, footY + 22, stopHov ? ACCENT_HOV : ACCENT);
        ctx.drawText(tr, Text.literal("Stop"), rx - 44, footY + 11, 0xFFFFFFFF, false);

        boolean solo = config.isSoloMode();
        boolean soloHov = in(mouseX, mouseY, rx - 116, footY + 8, 56, 14);
        ctx.fill(rx - 116, footY + 8, rx - 60, footY + 22, solo ? ACCENT : ROW_HOVER);
        ctx.drawText(tr, Text.literal(solo ? "Solo ON" : "Solo OFF"), rx - 111, footY + 11,
            solo ? 0xFFFFFFFF : TEXT_MUTED, false);

        int volX = rx - 116, volY = footY + 32, volW = 108;
        ctx.drawText(tr, Text.literal("Vol"), volX - 22, volY - 2, TEXT_MUTED, false);
        ctx.fill(volX, volY, volX + volW, volY + 3, 0x40FFFFFF);
        int fillW = (int) (volW * config.getVolume());
        ctx.fill(volX, volY, volX + fillW, volY + 3, ACCENT);
        ctx.fill(volX + fillW - 1, volY - 2, volX + fillW + 1, volY + 5, GOLD);
    }

    private void drawCover(DrawContext ctx, OstTrack track, int x, int y, int size) {
        Identifier cover = OstTrackMeta.coverTexture(track);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cover != null && mc.getResourceManager().getResource(cover).isPresent()) {
            ctx.drawTexture(cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else {
            ctx.fill(x, y, x + size, y + size, categoryColor(track.category()));
            ctx.drawText(this.textRenderer, Text.literal(track.category().displayName().substring(0, 1)),
                x + size / 2 - 2, y + size / 2 - 4, 0xFFFFFFFF, false);
        }
        ctx.fill(x, y, x + size, y + 1, 0x60000000);
        ctx.fill(x, y + size - 1, x + size, y + size, 0x60000000);
    }

    // ─── Interaction ───

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int mxi = (int) mx, myi = (int) my;

        if (in(mxi, myi, mainX + mainW - 18, mainY + 5, 16, 14)) { close(); return true; }

        // Sidebar catégories.
        int cy = sbY + 42;
        for (OstCategory cat : OstCategory.values()) {
            if (in(mxi, myi, sbX + 6, cy, sbW - 12, CAT_ROW_H)) {
                selectedCategory = cat; scrollOffset = 0;
                if (searchField != null) searchField.setText("");
                return true;
            }
            cy += CAT_ROW_H + 2;
        }

        int rx = mainX + mainW;
        if (in(mxi, myi, rx - 56, footY + 8, 48, 14)) { engine.stop(); return true; }
        if (in(mxi, myi, rx - 116, footY + 8, 56, 14)) {
            config.setSoloMode(!config.isSoloMode()); config.save(); return true;
        }
        if (setVolumeFromMouse(mx, my)) return true;

        // Lignes liste.
        List<OstTrack> tracks = resolveVisibleTracks();
        int listTop = mainY + 26, listBottom = mainY + mainH - 5;
        if (myi >= listTop && myi < listBottom && mxi >= mainX + 6 && mxi < mainX + mainW - 6) {
            int idx = (myi - listTop) / ROW_H + scrollOffset;
            if (idx >= 0 && idx < tracks.size()) {
                OstTrack t = tracks.get(idx);
                if (mxi > mainX + mainW - 20) config.toggleFavorite(t.trackId());
                else { engine.play(t, 1.0f, null, 0f, 0f); config.setLastTrackId(t.trackId()); }
                config.save();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (setVolumeFromMouse(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private boolean setVolumeFromMouse(double mx, double my) {
        int rx = mainX + mainW, volX = rx - 116, volY = footY + 32, volW = 108;
        if (mx >= volX && mx <= volX + volW && my >= volY - 4 && my <= volY + 7) {
            float v = (float) Math.max(0, Math.min(1, (mx - volX) / volW));
            config.setVolume(v); engine.setGlobalVolume(v); config.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        List<OstTrack> tracks = resolveVisibleTracks();
        int maxOffset = Math.max(0, tracks.size() - 1);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(vAmount)));
        return true;
    }

    private boolean searchBlank() {
        return searchField == null || searchField.getText().isBlank();
    }

    private List<OstTrack> resolveVisibleTracks() {
        String q = searchField != null ? searchField.getText() : "";
        if (q != null && !q.isBlank()) return library.search(q);
        if (selectedCategory == OstCategory.FAVORIS) return library.favorites(config.getFavorites());
        return library.tracks(selectedCategory);
    }

    private static int categoryColor(OstCategory cat) {
        return switch (cat) {
            case APAISANT -> 0xFF3FA89B;
            case COMBAT -> 0xFFB23A3A;
            case MISSION -> 0xFF3A6BB2;
            case MOTIVATION -> 0xFFD98E3A;
            case MYSTERE -> 0xFF7E3AB2;
            case TRISTE -> 0xFF5A6B7E;
            case FAVORIS -> 0xFFB23A6B;
        };
    }

    /** Panneau sombre à coins légèrement arrondis (façon maquette). */
    private static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 2, y, x + w - 2, y + h, ZONE_BG);
        ctx.fill(x, y + 2, x + 2, y + h - 2, ZONE_BG);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, ZONE_BG);
        ctx.fill(x + 2, y, x + w - 2, y + 1, ZONE_BORD);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, ZONE_BORD);
        ctx.fill(x, y + 2, x + 1, y + h - 2, ZONE_BORD);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, ZONE_BORD);
        ctx.fill(x + 1, y + 1, x + 2, y + 2, ZONE_BORD);
        ctx.fill(x + w - 2, y + 1, x + w - 1, y + 2, ZONE_BORD);
        ctx.fill(x + 1, y + h - 2, x + 2, y + h - 1, ZONE_BORD);
        ctx.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, ZONE_BORD);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
