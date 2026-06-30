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
 * Menu OST — layout « Zenkai » : barre du haut (logo + onglets catégories +
 * fermer), carte now-playing proéminente (pochette vinyl tournante + titre +
 * barre de progression + contrôles), recherche, liste des pistes, et pied de
 * page avec sliders Volume / Distance + Solo.
 *
 * <p>Prototype dessiné en code (palette Akatsuki). Le cadre image
 * {@code reborn-ost:textures/gui/ost_menu.png} (design 620x360), s'il est
 * présent, est affiché en fond ; sinon panneaux dessinés.
 */
public class OstScreen extends Screen {

    // Design de référence (donne les dimensions de la maquette).
    private static final int DW = 620, DH = 360;

    private static final int DIM        = 0xC8000000;
    private static final int BG         = 0xF2120A0E;
    private static final int CARD       = 0xFF1E1014;
    private static final int SECTION    = 0xFF170C10;
    private static final int BORDER     = 0xFF3A2A2E;
    private static final int ACCENT     = 0xFFA0182B;
    private static final int ACCENT_HOV = 0xFFC2364A;
    private static final int GOLD       = 0xFFD9A95E;
    private static final int TEXT       = 0xFFE8DCC8;
    private static final int TEXT_MUTED = 0xFF9A8B78;
    private static final int ROW_HOVER  = 0x22FFFFFF;
    private static final int ROW_PLAY   = 0x66A0182B;

    private static final Identifier FRAME = Identifier.of("reborn-ost", "textures/gui/ost_menu.png");
    private static final Identifier VINYL = Identifier.of("reborn-ost", "textures/gui/vinyl.png");
    private static final Identifier IC_PREV  = Identifier.of("reborn-ost", "textures/gui/beforebutton.png");
    private static final Identifier IC_NEXT  = Identifier.of("reborn-ost", "textures/gui/afterbutton.png");
    private static final Identifier IC_PLAY  = Identifier.of("reborn-ost", "textures/gui/playbutton.png");
    private static final Identifier IC_PAUSE = Identifier.of("reborn-ost", "textures/gui/pausebutton.png");
    private static final Identifier IC_STOP  = Identifier.of("reborn-ost", "textures/gui/stopbutton.png");
    private static final Identifier IC_PREV_P  = Identifier.of("reborn-ost", "textures/gui/pressedbeforebutton.png");
    private static final Identifier IC_NEXT_P  = Identifier.of("reborn-ost", "textures/gui/pressedafterbutton.png");
    private static final Identifier IC_PLAY_P  = Identifier.of("reborn-ost", "textures/gui/pressedplaybutton.png");
    private static final Identifier IC_PAUSE_P = Identifier.of("reborn-ost", "textures/gui/pressedpausebutton.png");
    private static final Identifier IC_STOP_P  = Identifier.of("reborn-ost", "textures/gui/pressedstopbutton.png");
    private static final int COVER_PX = 64;
    private static final int ROW_H = 20, THUMB = 16;

    private final Screen parent;
    private final OstLibrary library;
    private final OstAudioEngine engine;
    private final OstConfig config;

    private OstCategory selectedCategory = OstCategory.APAISANT;
    private TextFieldWidget searchField;
    private int scrollOffset = 0;
    /** Contrôle en cours d'appui (0=prev 1=play 2=next 3=stop), -1 = aucun. */
    private int pressedCtrl = -1;

    private int px, py, pw, ph;

    public OstScreen(Screen parent) {
        super(Text.literal("Menu des OST"));
        this.parent = parent;
        this.library = RebornOstClient.library();
        this.engine = RebornOstClient.audioEngine();
        this.config = RebornOstClient.config();
    }

    private void layout() {
        pw = Math.min(DW, this.width - 40);
        ph = Math.min(DH, this.height - 40);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
    }

    private int fx(int v) { return Math.round(v / (float) DW * pw); }
    private int fy(int v) { return Math.round(v / (float) DH * ph); }
    // Contenu décalé à droite (+180) pour laisser apparaître le watermark REBORN.
    private int cx0() { return px + fx(190); }
    private int cw()  { return fx(420); }

    // Zones (Y) — design 620x360.
    private int headerY() { return py + fy(13); }
    private int npY()     { return py + fy(46); }
    private int npH()     { return fy(78); }
    private int searchY() { return py + fy(132); }
    private int listTop() { return py + fy(160); }
    private int footerY() { return py + fy(320); }
    private int listBottom() { return footerY() - fy(8); }

    // Carte now-playing : pochette + zone texte.
    private int artX() { return cx0() + 8; }
    private int artY() { return npY() + 7; }
    private int artSize() { return npH() - 14; }
    private int npTextX() { return artX() + artSize() + 12; }
    private int ctrlY() { return npY() + npH() - 20; }
    private int progBarX() { return npTextX(); }
    private int progBarY() { return npY() + 46; }
    private int progBarW() { return (cx0() + cw() - 12) - npTextX(); }

    @Override
    protected void init() {
        layout();
        int sw = cw() - 40;
        searchField = new TextFieldWidget(this.textRenderer,
            cx0() + 24, searchY() + 4, sw, 12, Text.literal("Rechercher"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.literal("Rechercher une piste…"));
        this.addDrawableChild(searchField);
    }

    // ─── Rendu ───

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        layout();
        ctx.fill(0, 0, this.width, this.height, DIM);
        TextRenderer tr = this.textRenderer;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hasFrame = mc.getResourceManager().getResource(FRAME).isPresent();
        if (hasFrame) {
            ctx.drawTexture(FRAME, px, py, 0f, 0f, pw, ph, DW, DH);
        } else {
            panel(ctx, px, py, pw, ph, BG);
        }
        // Panneaux de sections (bordures) — toujours dessinés, par-dessus le cadre.
        panel(ctx, cx0(), npY(), cw(), npH(), CARD);
        panel(ctx, cx0(), searchY(), cw(), fy(20), SECTION);
        panel(ctx, cx0(), listTop() - 4, cw(), listBottom() - listTop() + 8, SECTION);
        panel(ctx, cx0(), footerY(), cw(), fy(30), SECTION);

        renderHeader(ctx, tr, mouseX, mouseY, hasFrame);
        renderNowPlaying(ctx, tr, mouseX, mouseY);
        // Loupe recherche.
        ctx.drawText(tr, Text.literal("⌕"), cx0() + 8, searchY() + 6, TEXT_MUTED, false);
        renderList(ctx, tr, mouseX, mouseY);
        renderFooter(ctx, tr, mouseX, mouseY);

        if (searchField != null) searchField.render(ctx, mouseX, mouseY, delta);
    }

    private void renderHeader(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, boolean hasFrame) {
        int hy = headerY();
        if (!hasFrame) {
            // Logo dessiné seulement si le cadre ne le fournit pas déjà.
            ctx.drawText(tr, Text.literal("♫").styled(s -> s.withBold(true)), cx0() + 6, hy + 2, GOLD, false);
            ctx.drawText(tr, Text.literal("OST").styled(s -> s.withBold(true)), cx0() + 18, hy - 1, GOLD, false);
            ctx.drawText(tr, Text.literal("MENU").styled(s -> s.withBold(true)), cx0() + 18, hy + 9, ACCENT_HOV, false);
        }

        int tabX = cx0();
        for (OstCategory cat : OstCategory.values()) {
            int w = tr.getWidth(cat.displayName()) + 10;
            boolean sel = cat == selectedCategory && searchBlank();
            boolean hov = in(mouseX, mouseY, tabX, hy - 2, w, 18);
            roundRect(ctx, tabX, hy - 2, w, 18, sel ? ACCENT : (hov ? ROW_HOVER : SECTION));
            ctx.drawText(tr, Text.literal(cat.displayName()), tabX + 5, hy + 3,
                sel ? 0xFFFFFFFF : TEXT_MUTED, false);
            tabX += w + 8;
        }

        boolean closeHov = in(mouseX, mouseY, px + pw - 22, hy - 2, 16, 16);
        ctx.drawText(tr, Text.literal("✕"), px + pw - 19, hy + 1, closeHov ? ACCENT_HOV : TEXT_MUTED, false);
    }

    private void renderNowPlaying(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        var cur = engine.currentTrack();
        int ax = artX(), ay = artY(), as = artSize();
        if (cur.isPresent()) {
            OstTrack t = cur.get();
            drawCover(ctx, t, ax, ay, as, engine.elapsedMs() / 1000f * 70f);
            ctx.drawText(tr, Text.literal(OstTrackMeta.title(t.trackId(), t.displayName())).styled(s -> s.withBold(true)),
                npTextX(), npY() + 10, GOLD, false);
            ctx.drawText(tr, Text.literal(t.category().displayName()), npTextX(), npY() + 22, TEXT_MUTED, false);

            long el = engine.elapsedMs(), du = engine.durationMs();
            int bx = progBarX(), bw = progBarW(), byp = progBarY();
            ctx.drawText(tr, Text.literal(mmss(el)), bx, byp - 9, TEXT_MUTED, false);
            String tot = mmss(du);
            ctx.drawText(tr, Text.literal(tot), bx + bw - tr.getWidth(tot), byp - 9, TEXT_MUTED, false);
            ctx.fill(bx, byp, bx + bw, byp + 3, 0x40FFFFFF);
            if (du > 0) {
                int fw = (int) (bw * Math.min(1.0, el / (double) du));
                ctx.fill(bx, byp, bx + fw, byp + 3, GOLD);
                ctx.fill(bx + fw - 1, byp - 2, bx + fw + 1, byp + 5, ACCENT_HOV);
            }
        } else {
            drawVinylPlaceholder(ctx, ax, ay, as);
            ctx.drawText(tr, Text.literal("Aucune piste en lecture").styled(s -> s.withBold(true)),
                npTextX(), npY() + 14, TEXT_MUTED, false);
            ctx.drawText(tr, Text.literal("Choisis une piste dans la liste"), npTextX(), npY() + 28, TEXT_MUTED, false);
        }

        // Contrôles (icônes 16x16 du pack) avec animation "pressed" à l'appui.
        int cx = npTextX(), cy = ctrlY();
        boolean playing = engine.isPlaying();
        drawIconBtn(ctx, pressedCtrl == 0 ? IC_PREV_P : IC_PREV, cx, cy, mouseX, mouseY);
        Identifier pp = playing
            ? (pressedCtrl == 1 ? IC_PAUSE_P : IC_PAUSE)
            : (pressedCtrl == 1 ? IC_PLAY_P : IC_PLAY);
        drawIconBtn(ctx, pp, cx + 22, cy, mouseX, mouseY);
        drawIconBtn(ctx, pressedCtrl == 2 ? IC_NEXT_P : IC_NEXT, cx + 44, cy, mouseX, mouseY);
        drawIconBtn(ctx, pressedCtrl == 3 ? IC_STOP_P : IC_STOP, cx + 66, cy, mouseX, mouseY);
    }

    private void drawIconBtn(DrawContext ctx, Identifier icon, int x, int y, int mouseX, int mouseY) {
        if (in(mouseX, mouseY, x, y, 16, 16)) roundRect(ctx, x - 1, y - 1, 18, 18, ROW_HOVER);
        ctx.drawTexture(icon, x, y, 0f, 0f, 16, 16, 16, 16);
    }

    private void renderList(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        List<OstTrack> tracks = resolveVisibleTracks();
        int top = listTop(), bottom = listBottom(), left = cx0() + 4, right = cx0() + cw() - 4;
        ctx.enableScissor(left, top, right, bottom);
        for (int i = 0; i < tracks.size(); i++) {
            int rowY = top + (i - scrollOffset) * ROW_H;
            if (rowY + ROW_H < top || rowY > bottom) continue;
            OstTrack track = tracks.get(i);
            boolean hovered = in(mouseX, mouseY, left, rowY, right - left, ROW_H) && mouseY < bottom;
            boolean playing = engine.currentTrack().map(t -> t.trackId().equals(track.trackId())).orElse(false);
            if (playing) ctx.fill(left, rowY, right, rowY + ROW_H, ROW_PLAY);
            else if (hovered) ctx.fill(left, rowY, right, rowY + ROW_H, ROW_HOVER);

            int thumbY = rowY + (ROW_H - THUMB) / 2;
            drawCover(ctx, track, left + 4, thumbY, THUMB);
            ctx.drawText(tr, Text.literal((playing ? "▶ " : "") + OstTrackMeta.title(track.trackId(), track.displayName())),
                left + 4 + THUMB + 8, rowY + 6, playing ? 0xFFFFFFFF : TEXT, false);

            boolean fav = config.isFavorite(track.trackId());
            ctx.drawText(tr, Text.literal(fav ? "★" : "☆"), right - 14, rowY + 6, fav ? GOLD : TEXT_MUTED, false);
            String dur = OstTrackMeta.formatDuration(OstTrackMeta.duration(track.trackId()));
            if (!dur.isEmpty()) ctx.drawText(tr, Text.literal(dur), right - 28 - tr.getWidth(dur), rowY + 6, TEXT_MUTED, false);
        }
        ctx.disableScissor();
    }

    private int volBarX()  { return cx0() + 48; }
    private int volBarW()  { return 100; }
    private int distBarX() { return cx0() + 218; }
    private int distBarW() { return 100; }
    private int sliderY()  { return footerY() + 16; }

    private void renderFooter(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int sy = sliderY();
        ctx.drawText(tr, Text.literal("VOLUME").styled(s -> s.withBold(true)), cx0() + 8, sy - 8, TEXT_MUTED, false);
        slider(ctx, volBarX(), sy, volBarW(), config.getVolume(), ACCENT);
        ctx.drawText(tr, Text.literal("DISTANCE").styled(s -> s.withBold(true)), distBarX() - 60, sy - 8, TEXT_MUTED, false);
        slider(ctx, distBarX(), sy, distBarW(), Math.min(1f, config.getBroadcastDistance() / 128f), 0xFF3A6BB2);

        boolean solo = config.isSoloMode();
        int soloX = cx0() + cw() - 84;
        boolean soloHov = in(mouseX, mouseY, soloX, sy - 6, 78, 16);
        roundRect(ctx, soloX, sy - 6, 78, 16, solo ? ACCENT : (soloHov ? ROW_HOVER : SECTION));
        ctx.drawText(tr, Text.literal(solo ? "Mode Solo ON" : "Mode Solo OFF"), soloX + 6, sy - 2,
            solo ? 0xFFFFFFFF : TEXT_MUTED, false);
    }

    private void slider(DrawContext ctx, int x, int y, int w, float frac, int color) {
        ctx.fill(x, y, x + w, y + 3, 0x40FFFFFF);
        int fw = (int) (w * Math.max(0f, Math.min(1f, frac)));
        ctx.fill(x, y, x + fw, y + 3, color);
        ctx.fill(x + fw - 1, y - 3, x + fw + 1, y + 6, GOLD);
    }

    private void drawCover(DrawContext ctx, OstTrack track, int x, int y, int size) {
        drawCover(ctx, track, x, y, size, 0f);
    }

    private void drawCover(DrawContext ctx, OstTrack track, int x, int y, int size, float angle) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Identifier cover = OstTrackMeta.coverTexture(track);
        boolean rot = angle != 0f;
        if (rot) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + size / 2f, y + size / 2f, 0);
            ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            ctx.getMatrices().translate(-(x + size / 2f), -(y + size / 2f), 0);
        }
        if (cover != null && mc.getResourceManager().getResource(cover).isPresent()) {
            ctx.drawTexture(cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else if (mc.getResourceManager().getResource(VINYL).isPresent()) {
            ctx.drawTexture(VINYL, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
            int cx = x + size / 2, cy = y + size / 2;
            // Tint SUBTIL du centre selon le thème (semi-transparent → garde le
            // détail/relief du vinyl d'origine au lieu d'un disque plein).
            int tint = (categoryColor(track.category()) & 0x00FFFFFF) | 0x88000000;
            fillDisc(ctx, cx, cy, Math.max(2, Math.round(size * 0.20f)), tint);
        } else {
            ctx.fill(x, y, x + size, y + size, categoryColor(track.category()));
        }
        if (rot) ctx.getMatrices().pop();
    }

    private void drawVinylPlaceholder(DrawContext ctx, int x, int y, int size) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getResourceManager().getResource(VINYL).isPresent()) {
            ctx.drawTexture(VINYL, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else {
            ctx.fill(x, y, x + size, y + size, 0xFF2A1A1E);
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
        int hy = headerY();

        if (in(mxi, myi, px + pw - 22, hy - 2, 16, 16)) { close(); return true; }

        // Onglets.
        int tabX = cx0();
        for (OstCategory cat : OstCategory.values()) {
            int w = this.textRenderer.getWidth(cat.displayName()) + 10;
            if (in(mxi, myi, tabX, hy - 2, w, 18)) {
                selectedCategory = cat; scrollOffset = 0;
                if (searchField != null) searchField.setText("");
                return true;
            }
            tabX += w + 8;
        }

        // Contrôles now-playing (icônes 16x16) — pressedCtrl pour l'animation.
        int ccx = npTextX(), cy = ctrlY();
        if (in(mxi, myi, ccx, cy, 16, 16)) { pressedCtrl = 0; playRelative(-1); return true; }
        if (in(mxi, myi, ccx + 22, cy, 16, 16)) { pressedCtrl = 1; engine.togglePause(); return true; }
        if (in(mxi, myi, ccx + 44, cy, 16, 16)) { pressedCtrl = 2; playRelative(1); return true; }
        if (in(mxi, myi, ccx + 66, cy, 16, 16)) { pressedCtrl = 3; engine.stop(); return true; }

        // Solo.
        int sy = sliderY(), soloX = cx0() + cw() - 84;
        if (in(mxi, myi, soloX, sy - 6, 78, 16)) {
            config.setSoloMode(!config.isSoloMode()); config.save(); return true;
        }
        if (handleSliders(mx, my)) return true;

        // Lignes liste.
        List<OstTrack> tracks = resolveVisibleTracks();
        int top = listTop(), bottom = listBottom(), left = cx0() + 4, right = cx0() + cw() - 4;
        if (myi >= top && myi < bottom && mxi >= left && mxi < right) {
            int idx = (myi - top) / ROW_H + scrollOffset;
            if (idx >= 0 && idx < tracks.size()) {
                OstTrack t = tracks.get(idx);
                if (mxi > right - 18) config.toggleFavorite(t.trackId());
                else { engine.play(t, 1.0f, null, 0f, 0f); config.setLastTrackId(t.trackId()); }
                config.save();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        pressedCtrl = -1; // relâche l'animation pressed
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (handleSliders(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private boolean handleSliders(double mx, double my) {
        int sy = sliderY();
        if (my >= sy - 4 && my <= sy + 7) {
            if (mx >= volBarX() && mx <= volBarX() + volBarW()) {
                float v = clamp01((mx - volBarX()) / volBarW());
                config.setVolume(v); engine.setGlobalVolume(v); config.save();
                return true;
            }
            if (mx >= distBarX() && mx <= distBarX() + distBarW()) {
                config.setBroadcastDistance(clamp01((mx - distBarX()) / distBarW()) * 128f);
                config.save();
                return true;
            }
        }
        // Barre de progression (seek).
        int bx = progBarX(), bw = progBarW(), byp = progBarY();
        if (mx >= bx && mx <= bx + bw && my >= byp - 4 && my <= byp + 7) {
            long du = engine.durationMs();
            if (du > 0) engine.seekMs((long) (du * clamp01((mx - bx) / bw)));
            return true;
        }
        return false;
    }

    private void playRelative(int dir) {
        var cur = engine.currentTrack();
        OstCategory cat = cur.map(OstTrack::category).orElse(selectedCategory);
        List<OstTrack> list = library.tracks(cat);
        if (list.isEmpty()) return;
        int idx = 0;
        if (cur.isPresent()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).trackId().equals(cur.get().trackId())) { idx = i; break; }
            }
            idx = (idx + dir + list.size()) % list.size();
        }
        OstTrack t = list.get(idx);
        engine.play(t, 1.0f, null, 0f, 0f);
        config.setLastTrackId(t.trackId());
        config.save();
    }

    private static float clamp01(double v) { return (float) Math.max(0, Math.min(1, v)); }

    private static String mmss(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
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

    private static void panel(DrawContext ctx, int x, int y, int w, int h, int bg) {
        ctx.fill(x + 2, y, x + w - 2, y + h, bg);
        ctx.fill(x, y + 2, x + 2, y + h - 2, bg);
        ctx.fill(x + w - 2, y + 2, x + w, y + h - 2, bg);
        ctx.fill(x + 2, y, x + w - 2, y + 1, BORDER);
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, BORDER);
        ctx.fill(x, y + 2, x + 1, y + h - 2, BORDER);
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, BORDER);
    }

    private static void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
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
