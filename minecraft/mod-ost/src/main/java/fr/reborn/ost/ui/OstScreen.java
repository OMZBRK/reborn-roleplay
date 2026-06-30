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
 * Menu OST compact — 3 panneaux (maquette Reborn) : sidebar catégories à gauche,
 * liste des pistes en haut-droite, lecteur (now-playing + temps + volume + zone)
 * en bas-droite. Cadre = texture Aseprite, contenu calé en fractions.
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
    private static final Identifier VINYL = Identifier.of("reborn-ost", "textures/gui/vinyl.png");
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

    private int px, py, pw, ph;
    private int sbX, sbY, sbW, sbH;
    private int mainX, mainY, mainW, mainH;
    private int footY, footH;

    public OstScreen(Screen parent) {
        super(Text.literal("Menu des OST"));
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

        if (searchField != null) searchField.render(ctx, mouseX, mouseY, delta);
    }

    private int catTop() { return sbY + 40; }

    private void renderSidebar(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        Text l1 = Text.literal("MENU DES").styled(s -> s.withBold(true));
        Text l2 = Text.literal("OST").styled(s -> s.withBold(true));
        ctx.drawText(tr, l1, sbX + (sbW - tr.getWidth(l1)) / 2, sbY + 9, GOLD, false);
        ctx.drawText(tr, l2, sbX + (sbW - tr.getWidth(l2)) / 2, sbY + 19, ACCENT_HOV, false);
        ctx.fill(sbX + 12, sbY + 32, sbX + sbW - 12, sbY + 33, ZONE_BORD);

        int cy = catTop();
        for (OstCategory cat : OstCategory.values()) {
            boolean sel = cat == selectedCategory && searchBlank();
            boolean hov = in(mouseX, mouseY, sbX + 6, cy, sbW - 12, CAT_ROW_H);
            if (sel) ctx.fill(sbX + 6, cy, sbX + sbW - 6, cy + CAT_ROW_H, ACCENT);
            else if (hov) ctx.fill(sbX + 6, cy, sbX + sbW - 6, cy + CAT_ROW_H, ROW_HOVER);
            int tw = tr.getWidth(cat.displayName());
            ctx.drawText(tr, Text.literal(cat.displayName()), sbX + (sbW - tw) / 2, cy + 8,
                sel ? 0xFFFFFFFF : TEXT_MUTED, false);
            cy += CAT_ROW_H + 2;
        }
    }

    private void renderMain(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        String head = searchBlank() ? selectedCategory.displayName() : "Recherche";
        ctx.drawText(tr, Text.literal(head).styled(s -> s.withBold(true)), mainX + 10, mainY + 7, GOLD, false);
        ctx.fill(mainX + mainW - 158, mainY + 3, mainX + mainW - 22, mainY + 18, 0x40000000);
        boolean closeHov = in(mouseX, mouseY, mainX + mainW - 18, mainY + 4, 14, 14);
        ctx.drawText(tr, Text.literal("✕"), mainX + mainW - 15, mainY + 6, closeHov ? ACCENT_HOV : TEXT_MUTED, false);
        ctx.fill(mainX + 6, mainY + 21, mainX + mainW - 6, mainY + 22, ZONE_BORD);

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

    // Positions Y du footer (partagées render/clic pour rester synchro).
    private int btnRowY()  { return footY + 12; }
    private int volBarY()  { return footY + 33; }
    private int zoneBarY() { return footY + 44; }
    private int timeBarY() { return footY + 39; }

    private void renderFooter(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        var cur = engine.currentTrack();
        boolean has = cur.isPresent();
        int npX = mainX + 8;

        if (has) {
            OstTrack t = cur.get();
            drawCover(ctx, t, npX, footY + 13, 30);
            ctx.drawText(tr, Text.literal("♪ " + OstTrackMeta.title(t.trackId(), t.displayName())),
                npX + 36, footY + 11, GOLD, false);
        } else {
            ctx.drawText(tr, Text.literal("Aucune piste en lecture"), npX, footY + 13, TEXT_MUTED, false);
        }

        long el = engine.elapsedMs(), du = engine.durationMs();
        int tbX = npX + 36, tbY = timeBarY(), tbW = 168;
        ctx.drawText(tr, Text.literal(mmss(el) + " / " + mmss(du)), tbX, footY + 25, TEXT_MUTED, false);
        ctx.fill(tbX, tbY, tbX + tbW, tbY + 3, 0x40FFFFFF);
        if (du > 0) {
            int fw = (int) (tbW * Math.min(1.0, el / (double) du));
            ctx.fill(tbX, tbY, tbX + fw, tbY + 3, GOLD);
            ctx.fill(tbX + fw - 1, tbY - 2, tbX + fw + 1, tbY + 5, ACCENT_HOV);
        }

        int rx = mainX + mainW, by = btnRowY();
        boolean ppHov = in(mouseX, mouseY, rx - 152, by, 22, 14);
        ctx.fill(rx - 152, by, rx - 130, by + 14, has ? (ppHov ? ACCENT_HOV : ACCENT) : ROW_HOVER);
        ctx.drawText(tr, Text.literal(engine.isPlaying() ? "II" : "▶"), rx - 145, by + 3, 0xFFFFFFFF, false);
        boolean stopHov = in(mouseX, mouseY, rx - 126, by, 42, 14);
        ctx.fill(rx - 126, by, rx - 84, by + 14, stopHov ? ACCENT_HOV : ACCENT);
        ctx.drawText(tr, Text.literal("Stop"), rx - 116, by + 3, 0xFFFFFFFF, false);
        boolean solo = config.isSoloMode();
        ctx.fill(rx - 80, by, rx - 8, by + 14, solo ? ACCENT : ROW_HOVER);
        ctx.drawText(tr, Text.literal(solo ? "Solo ON" : "Solo OFF"), rx - 72, by + 3,
            solo ? 0xFFFFFFFF : TEXT_MUTED, false);

        int barX = rx - 116, barW = 108, vY = volBarY(), zY = zoneBarY();
        ctx.drawText(tr, Text.literal("Vol"), barX - 22, vY - 2, TEXT_MUTED, false);
        ctx.fill(barX, vY, barX + barW, vY + 3, 0x40FFFFFF);
        int vfw = (int) (barW * config.getVolume());
        ctx.fill(barX, vY, barX + vfw, vY + 3, ACCENT);
        ctx.fill(barX + vfw - 1, vY - 2, barX + vfw + 1, vY + 5, GOLD);
        ctx.drawText(tr, Text.literal("Zone"), barX - 26, zY - 2, TEXT_MUTED, false);
        ctx.fill(barX, zY, barX + barW, zY + 3, 0x40FFFFFF);
        int zfw = (int) (barW * Math.min(1f, config.getBroadcastDistance() / 128f));
        ctx.fill(barX, zY, barX + zfw, zY + 3, 0xFF3A6BB2);
        ctx.fill(barX + zfw - 1, zY - 2, barX + zfw + 1, zY + 5, GOLD);
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private void drawCover(DrawContext ctx, OstTrack track, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Identifier cover = OstTrackMeta.coverTexture(track);
        if (cover != null && mc.getResourceManager().getResource(cover).isPresent()) {
            ctx.drawTexture(cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
            return;
        }
        if (mc.getResourceManager().getResource(VINYL).isPresent()) {
            ctx.drawTexture(VINYL, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
            int cx = x + size / 2, cy = y + size / 2;
            fillDisc(ctx, cx, cy, Math.max(2, Math.round(size * 0.22f)), categoryColor(track.category()));
            fillDisc(ctx, cx, cy, Math.max(1, Math.round(size * 0.07f)), 0xFF101010);
        } else {
            ctx.fill(x, y, x + size, y + size, categoryColor(track.category()));
        }
    }

    private static void fillDisc(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    // ─── Interaction ───

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int mxi = (int) mx, myi = (int) my;

        if (in(mxi, myi, mainX + mainW - 18, mainY + 4, 16, 14)) { close(); return true; }

        int cy = catTop();
        for (OstCategory cat : OstCategory.values()) {
            if (in(mxi, myi, sbX + 6, cy, sbW - 12, CAT_ROW_H)) {
                selectedCategory = cat; scrollOffset = 0;
                if (searchField != null) searchField.setText("");
                return true;
            }
            cy += CAT_ROW_H + 2;
        }

        int rx = mainX + mainW, by = btnRowY();
        if (in(mxi, myi, rx - 152, by, 22, 14)) { engine.togglePause(); return true; }
        if (in(mxi, myi, rx - 126, by, 42, 14)) { engine.stop(); return true; }
        if (in(mxi, myi, rx - 80, by, 72, 14)) {
            config.setSoloMode(!config.isSoloMode()); config.save(); return true;
        }
        if (handleBars(mx, my)) return true;

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
        if (handleBars(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private boolean handleBars(double mx, double my) {
        int rx = mainX + mainW, barX = rx - 116, barW = 108;
        int vY = volBarY(), zY = zoneBarY();
        if (mx >= barX && mx <= barX + barW) {
            if (my >= vY - 4 && my <= vY + 7) {
                float v = clamp01((mx - barX) / barW);
                config.setVolume(v); engine.setGlobalVolume(v); config.save();
                return true;
            }
            if (my >= zY - 4 && my <= zY + 7) {
                config.setBroadcastDistance(clamp01((mx - barX) / barW) * 128f);
                config.save();
                return true;
            }
        }
        int tbX = mainX + 8 + 36, tbY = timeBarY(), tbW = 168;
        if (mx >= tbX && mx <= tbX + tbW && my >= tbY - 4 && my <= tbY + 7) {
            long du = engine.durationMs();
            if (du > 0) engine.seekMs((long) (du * clamp01((mx - tbX) / tbW)));
            return true;
        }
        return false;
    }

    private static float clamp01(double v) {
        return (float) Math.max(0, Math.min(1, v));
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

    private static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 2, y, x + w - 2, y + h, ZONE_BG);
        ctx.fill(x, y + 2, x + 2, y + h - 2, ZONE_BG);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, ZONE_BG);
        ctx.fill(x + 2, y, x + w - 2, y + 1, ZONE_BORD);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, ZONE_BORD);
        ctx.fill(x, y + 2, x + 1, y + h - 2, ZONE_BORD);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, ZONE_BORD);
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
