package fr.reborn.ost.ui;

import fr.reborn.ost.RebornOstClient;
import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstPlayback;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.audio.OstTrackMeta;
import fr.reborn.ost.config.OstConfig;
import fr.reborn.ost.network.OstNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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

    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/ost_menu.png");
    private static final Identifier VINYL = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/vinyl.png");
    private static final Identifier IC_PREV  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/beforebutton.png");
    private static final Identifier IC_NEXT  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/afterbutton.png");
    private static final Identifier IC_PLAY  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/playbutton.png");
    private static final Identifier IC_PAUSE = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/pausebutton.png");
    private static final Identifier IC_STOP  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/stopbutton.png");
    private static final Identifier IC_PREV_P  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/pressedbeforebutton.png");
    private static final Identifier IC_NEXT_P  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/pressedafterbutton.png");
    private static final Identifier IC_PLAY_P  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/pressedplaybutton.png");
    private static final Identifier IC_PAUSE_P = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/pressedpausebutton.png");
    private static final Identifier IC_STOP_P  = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/pressedstopbutton.png");
    private static final int COVER_PX = 64;
    private static final int ROW_H = 20, THUMB = 16;

    private final Screen parent;
    private final OstLibrary library;
    private final OstAudioEngine engine;
    private final OstConfig config;

    private OstCategory selectedCategory = OstCategory.APAISANT;
    private EditBox searchField;
    private int scrollOffset = 0;
    /** Contrôle en cours d'appui (0=prev 1=play 2=next 3=stop), -1 = aucun. */
    private int pressedCtrl = -1;

    private int px, py, pw, ph;
    private long openedAt;

    public OstScreen(Screen parent) {
        super(Component.literal("Menu des OST"));
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
        searchField = new EditBox(this.font,
            cx0() + 24, searchY() + 4, sw, 12, Component.literal("Rechercher"));
        searchField.setBordered(false);
        searchField.setHint(Component.literal("Rechercher une piste…"));
        this.addRenderableWidget(searchField);
        openedAt = System.currentTimeMillis();
    }

    /** Slide/fade d'ouverture (ease-out cubic, 200 ms). */
    private float animEase() {
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 200f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // ─── Rendu ───

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        layout();
        g.fill(0, 0, this.width, this.height, DIM);
        Font tr = this.font;

        // Animation d'ouverture : léger slide vers le haut.
        g.pose().pushMatrix();
        g.pose().translate(0, (1f - animEase()) * 22f);

        Minecraft mc = Minecraft.getInstance();
        boolean hasFrame = mc.getResourceManager().getResource(FRAME).isPresent();
        if (hasFrame) {
            g.blit(RenderPipelines.GUI_TEXTURED, FRAME, px, py, 0f, 0f, pw, ph, DW, DH);
        } else {
            panel(g, px, py, pw, ph, BG);
        }
        // Panneaux de sections (bordures) — toujours dessinés, par-dessus le cadre.
        panel(g, cx0(), npY(), cw(), npH(), CARD);
        panel(g, cx0(), searchY(), cw(), fy(20), SECTION);
        panel(g, cx0(), listTop() - 4, cw(), listBottom() - listTop() + 8, SECTION);
        panel(g, cx0(), footerY(), cw(), fy(30), SECTION);

        renderHeader(g, tr, mouseX, mouseY, hasFrame);
        renderNowPlaying(g, tr, mouseX, mouseY);
        // Loupe recherche.
        g.text(tr, Component.literal("⌕"), cx0() + 8, searchY() + 6, TEXT_MUTED, false);
        renderList(g, tr, mouseX, mouseY);
        renderFooter(g, tr, mouseX, mouseY);

        if (searchField != null) searchField.extractRenderState(g, mouseX, mouseY, delta);
        g.pose().popMatrix();
    }

    private void renderHeader(GuiGraphicsExtractor g, Font tr, int mouseX, int mouseY, boolean hasFrame) {
        int hy = headerY();
        if (!hasFrame) {
            // Logo dessiné seulement si le cadre ne le fournit pas déjà.
            g.text(tr, Component.literal("♫").withStyle(s -> s.withBold(true)), cx0() + 6, hy + 2, GOLD, false);
            g.text(tr, Component.literal("OST").withStyle(s -> s.withBold(true)), cx0() + 18, hy - 1, GOLD, false);
            g.text(tr, Component.literal("MENU").withStyle(s -> s.withBold(true)), cx0() + 18, hy + 9, ACCENT_HOV, false);
        }

        int tabX = cx0();
        for (OstCategory cat : OstCategory.values()) {
            int w = tr.width(cat.displayName()) + 10;
            boolean sel = cat == selectedCategory && searchBlank();
            boolean hov = in(mouseX, mouseY, tabX, hy - 2, w, 18);
            roundRect(g, tabX, hy - 2, w, 18, sel ? ACCENT : (hov ? ROW_HOVER : SECTION));
            g.text(tr, Component.literal(cat.displayName()), tabX + 5, hy + 3,
                sel ? 0xFFFFFFFF : TEXT_MUTED, false);
            tabX += w + 8;
        }

        boolean closeHov = in(mouseX, mouseY, px + pw - 22, hy - 2, 16, 16);
        g.text(tr, Component.literal("✕"), px + pw - 19, hy + 1, closeHov ? ACCENT_HOV : TEXT_MUTED, false);
    }

    private void renderNowPlaying(GuiGraphicsExtractor g, Font tr, int mouseX, int mouseY) {
        var cur = engine.currentTrack();
        int ax = artX(), ay = artY(), as = artSize();
        if (cur.isPresent()) {
            OstTrack t = cur.get();
            drawCover(g, t, ax, ay, as, engine.elapsedMs() / 1000f * 70f);
            g.text(tr, Component.literal(OstTrackMeta.title(t.trackId(), t.displayName())).withStyle(s -> s.withBold(true)),
                npTextX(), npY() + 10, GOLD, false);
            g.text(tr, Component.literal(t.category().displayName()), npTextX(), npY() + 22, TEXT_MUTED, false);

            long el = engine.elapsedMs(), du = engine.durationMs();
            int bx = progBarX(), bw = progBarW(), byp = progBarY();
            g.text(tr, Component.literal(mmss(el)), bx, byp - 9, TEXT_MUTED, false);
            String tot = mmss(du);
            g.text(tr, Component.literal(tot), bx + bw - tr.width(tot), byp - 9, TEXT_MUTED, false);
            g.fill(bx, byp, bx + bw, byp + 3, 0x40FFFFFF);
            if (du > 0) {
                int fw = (int) (bw * Math.min(1.0, el / (double) du));
                g.fill(bx, byp, bx + fw, byp + 3, GOLD);
                g.fill(bx + fw - 1, byp - 2, bx + fw + 1, byp + 5, ACCENT_HOV);
            }
        } else {
            drawVinylPlaceholder(g, ax, ay, as);
            g.text(tr, Component.literal("Aucune piste en lecture").withStyle(s -> s.withBold(true)),
                npTextX(), npY() + 14, TEXT_MUTED, false);
            g.text(tr, Component.literal("Choisis une piste dans la liste"), npTextX(), npY() + 28, TEXT_MUTED, false);
        }

        // Contrôles (icônes 16x16 du pack) avec animation "pressed" à l'appui.
        int cx = npTextX(), cy = ctrlY();
        boolean playing = engine.isPlaying();
        drawIconBtn(g, pressedCtrl == 0 ? IC_PREV_P : IC_PREV, cx, cy, mouseX, mouseY);
        Identifier pp = playing
            ? (pressedCtrl == 1 ? IC_PAUSE_P : IC_PAUSE)
            : (pressedCtrl == 1 ? IC_PLAY_P : IC_PLAY);
        drawIconBtn(g, pp, cx + 22, cy, mouseX, mouseY);
        drawIconBtn(g, pressedCtrl == 2 ? IC_NEXT_P : IC_NEXT, cx + 44, cy, mouseX, mouseY);
        drawIconBtn(g, pressedCtrl == 3 ? IC_STOP_P : IC_STOP, cx + 66, cy, mouseX, mouseY);

        // Shuffle (aléatoire) + Repeat (off / une / liste) — toggles.
        boolean sh = config.isShuffle();
        boolean shHov = in(mouseX, mouseY, cx + 90, cy, 16, 16);
        roundRect(g, cx + 90, cy, 16, 16, sh ? ACCENT : (shHov ? ROW_HOVER : SECTION));
        g.text(tr, Component.literal("S"), cx + 96, cy + 4, sh ? 0xFFFFFFFF : TEXT_MUTED, false);
        int rm = config.getRepeatMode();
        boolean rpHov = in(mouseX, mouseY, cx + 112, cy, 16, 16);
        roundRect(g, cx + 112, cy, 16, 16, rm != 0 ? ACCENT : (rpHov ? ROW_HOVER : SECTION));
        g.text(tr, Component.literal("R"), cx + 118, cy + 4, rm != 0 ? 0xFFFFFFFF : TEXT_MUTED, false);
        if (rm == 1) g.text(tr, Component.literal("1"), cx + 123, cy - 2, GOLD, false);
    }

    private void drawIconBtn(GuiGraphicsExtractor g, Identifier icon, int x, int y, int mouseX, int mouseY) {
        if (in(mouseX, mouseY, x, y, 16, 16)) roundRect(g, x - 1, y - 1, 18, 18, ROW_HOVER);
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, 16, 16, 16, 16);
    }

    private void renderList(GuiGraphicsExtractor g, Font tr, int mouseX, int mouseY) {
        List<OstTrack> tracks = resolveVisibleTracks();
        int top = listTop(), bottom = listBottom(), left = cx0() + 4, right = cx0() + cw() - 4;
        g.enableScissor(left, top, right, bottom);
        for (int i = 0; i < tracks.size(); i++) {
            int rowY = top + (i - scrollOffset) * ROW_H;
            if (rowY + ROW_H < top || rowY > bottom) continue;
            OstTrack track = tracks.get(i);
            boolean hovered = in(mouseX, mouseY, left, rowY, right - left, ROW_H) && mouseY < bottom;
            boolean playing = engine.currentTrack().map(t -> t.trackId().equals(track.trackId())).orElse(false);
            if (playing) g.fill(left, rowY, right, rowY + ROW_H, ROW_PLAY);
            else if (hovered) g.fill(left, rowY, right, rowY + ROW_H, ROW_HOVER);

            int thumbY = rowY + (ROW_H - THUMB) / 2;
            drawCover(g, track, left + 4, thumbY, THUMB);
            g.text(tr, Component.literal((playing ? "▶ " : "") + OstTrackMeta.title(track.trackId(), track.displayName())),
                left + 4 + THUMB + 8, rowY + 6, playing ? 0xFFFFFFFF : TEXT, false);

            boolean fav = config.isFavorite(track.trackId());
            g.text(tr, Component.literal(fav ? "★" : "☆"), right - 14, rowY + 6, fav ? GOLD : TEXT_MUTED, false);
            String dur = OstTrackMeta.formatDuration(OstTrackMeta.duration(track.trackId()));
            if (!dur.isEmpty()) g.text(tr, Component.literal(dur), right - 28 - tr.width(dur), rowY + 6, TEXT_MUTED, false);
        }
        g.disableScissor();
    }

    private int volBarX()  { return cx0() + 48; }
    private int volBarW()  { return 100; }
    private int distBarX() { return cx0() + 218; }
    private int distBarW() { return 100; }
    private int sliderY()  { return footerY() + 16; }

    private void renderFooter(GuiGraphicsExtractor g, Font tr, int mouseX, int mouseY) {
        int sy = sliderY();
        // Volume : label à gauche, valeur % alignée à droite du slider.
        g.text(tr, Component.literal("VOLUME").withStyle(s -> s.withBold(true)), cx0() + 8, sy - 8, TEXT_MUTED, false);
        String volVal = Math.round(config.getVolume() * 100f) + " %";
        g.text(tr, Component.literal(volVal),
            volBarX() + volBarW() - tr.width(volVal), sy - 8, GOLD, false);
        slider(g, volBarX(), sy, volBarW(), config.getVolume(), ACCENT);
        // Distance : label + valeur en blocs alignée à droite du slider.
        g.text(tr, Component.literal("DISTANCE").withStyle(s -> s.withBold(true)), distBarX() - 60, sy - 8, TEXT_MUTED, false);
        String distVal = Math.round(config.getBroadcastDistance()) + " blocs";
        g.text(tr, Component.literal(distVal),
            distBarX() + distBarW() - tr.width(distVal), sy - 8, 0xFF6FA8DA, false);
        slider(g, distBarX(), sy, distBarW(), Math.min(1f, config.getBroadcastDistance() / 128f), 0xFF3A6BB2);

        boolean solo = config.isSoloMode();
        int soloX = cx0() + cw() - 84;
        boolean soloHov = in(mouseX, mouseY, soloX, sy - 6, 78, 16);
        roundRect(g, soloX, sy - 6, 78, 16, solo ? ACCENT : (soloHov ? ROW_HOVER : SECTION));
        g.text(tr, Component.literal(solo ? "Mode Solo ON" : "Mode Solo OFF"), soloX + 6, sy - 2,
            solo ? 0xFFFFFFFF : TEXT_MUTED, false);
    }

    private void slider(GuiGraphicsExtractor g, int x, int y, int w, float frac, int color) {
        g.fill(x, y, x + w, y + 3, 0x40FFFFFF);
        int fw = (int) (w * Math.max(0f, Math.min(1f, frac)));
        g.fill(x, y, x + fw, y + 3, color);
        g.fill(x + fw - 1, y - 3, x + fw + 1, y + 6, GOLD);
    }

    private void drawCover(GuiGraphicsExtractor g, OstTrack track, int x, int y, int size) {
        drawCover(g, track, x, y, size, 0f);
    }

    private void drawCover(GuiGraphicsExtractor g, OstTrack track, int x, int y, int size, float angle) {
        Minecraft mc = Minecraft.getInstance();
        Identifier cover = OstTrackMeta.coverTexture(track);
        boolean rot = angle != 0f;
        if (rot) {
            g.pose().pushMatrix();
            g.pose().translate(x + size / 2f, y + size / 2f);
            g.pose().rotate((float) Math.toRadians(angle));
            g.pose().translate(-(x + size / 2f), -(y + size / 2f));
        }
        if (cover != null && mc.getResourceManager().getResource(cover).isPresent()) {
            g.blit(RenderPipelines.GUI_TEXTURED, cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else if (mc.getResourceManager().getResource(VINYL).isPresent()) {
            g.blit(RenderPipelines.GUI_TEXTURED, VINYL, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
            int cx = x + size / 2, cy = y + size / 2;
            // Tint SUBTIL du centre selon le thème (semi-transparent → garde le
            // détail/relief du vinyl d'origine au lieu d'un disque plein).
            int tint = (categoryColor(track.category()) & 0x00FFFFFF) | 0x88000000;
            fillDisc(g, cx, cy, Math.max(2, Math.round(size * 0.20f)), tint);
        } else {
            g.fill(x, y, x + size, y + size, categoryColor(track.category()));
        }
        if (rot) g.pose().popMatrix();
    }

    private void drawVinylPlaceholder(GuiGraphicsExtractor g, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getResourceManager().getResource(VINYL).isPresent()) {
            g.blit(RenderPipelines.GUI_TEXTURED, VINYL, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else {
            g.fill(x, y, x + size, y + size, 0xFF2A1A1E);
        }
    }

    private static void fillDisc(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    // ─── Interaction ───

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        int button = event.button();
        int mxi = (int) mx, myi = (int) my;
        int hy = headerY();

        if (in(mxi, myi, px + pw - 22, hy - 2, 16, 16)) { onClose(); return true; }

        // Onglets.
        int tabX = cx0();
        for (OstCategory cat : OstCategory.values()) {
            int w = this.font.width(cat.displayName()) + 10;
            if (in(mxi, myi, tabX, hy - 2, w, 18)) {
                selectedCategory = cat; scrollOffset = 0;
                if (searchField != null) searchField.setValue("");
                return true;
            }
            tabX += w + 8;
        }

        // Contrôles now-playing (icônes 16x16) — pressedCtrl pour l'animation.
        int ccx = npTextX(), cy = ctrlY();
        if (in(mxi, myi, ccx, cy, 16, 16)) { pressedCtrl = 0; playRelative(-1); return true; }
        if (in(mxi, myi, ccx + 22, cy, 16, 16)) {
            pressedCtrl = 1;
            engine.togglePause();
            // Owner d'un broadcast → la pause se propage à toute la zone.
            if (OstNetworking.isBroadcastOwner()) OstNetworking.requestPause(engine.isPaused());
            return true;
        }
        if (in(mxi, myi, ccx + 44, cy, 16, 16)) { pressedCtrl = 2; playRelative(1); return true; }
        if (in(mxi, myi, ccx + 66, cy, 16, 16)) {
            pressedCtrl = 3;
            OstPlayback.INSTANCE.stop(engine);
            // Owner → stop pour toute la zone (no-op sinon, reset owner).
            OstNetworking.requestStop();
            return true;
        }
        // Shuffle / Repeat.
        if (in(mxi, myi, ccx + 90, cy, 16, 16)) { config.setShuffle(!config.isShuffle()); config.save(); return true; }
        if (in(mxi, myi, ccx + 112, cy, 16, 16)) { config.cycleRepeat(); config.save(); return true; }

        // Solo.
        int sy = sliderY(), soloX = cx0() + cw() - 84;
        if (in(mxi, myi, soloX, sy - 6, 78, 16)) {
            config.setSoloMode(!config.isSoloMode()); config.save();
            // Opt-out : passer en Solo coupe chez soi le broadcast serveur en
            // cours (on écoutera sa propre playlist). N'affecte que ce client.
            if (config.isSoloMode()) engine.stop();
            return true;
        }
        if (handleSliders(mx, my)) return true;

        // Lignes liste.
        List<OstTrack> tracks = resolveVisibleTracks();
        int top = listTop(), bottom = listBottom(), left = cx0() + 4, right = cx0() + cw() - 4;
        if (myi >= top && myi < bottom && mxi >= left && mxi < right) {
            int idx = (myi - top) / ROW_H + scrollOffset;
            if (idx >= 0 && idx < tracks.size()) {
                OstTrack t = tracks.get(idx);
                if (mxi > right - 18) { config.toggleFavorite(t.trackId()); config.save(); }
                else playAndMaybeBroadcast(tracks, idx);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        pressedCtrl = -1; // relâche l'animation pressed
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (handleSliders(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
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
        playAndMaybeBroadcast(list, idx);
    }

    /** Joue la piste localement ; en solo OFF, la diffuse aussi à la zone
     *  (broadcast serveur) et devient propriétaire du son. */
    private void playAndMaybeBroadcast(List<OstTrack> list, int idx) {
        OstPlayback.INSTANCE.play(engine, config, list, idx);
        if (!config.isSoloMode()) {
            OstTrack t = list.get(idx);
            OstNetworking.requestPlay(t.trackId(), config.getBroadcastDistance(), config.getVolume());
        }
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
        return searchField == null || searchField.getValue().isBlank();
    }

    private List<OstTrack> resolveVisibleTracks() {
        String q = searchField != null ? searchField.getValue() : "";
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

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg) {
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x, y + 2, x + 2, y + h - 2, bg);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, bg);
        g.fill(x + 2, y, x + w - 2, y + 1, BORDER);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, BORDER);
        g.fill(x, y + 2, x + 1, y + h - 2, BORDER);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, BORDER);
    }

    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
