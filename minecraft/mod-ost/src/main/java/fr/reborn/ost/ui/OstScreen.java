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
 * Menu OST — DA Reborn (laque noire, or, papier). Le monde reste visible derrière.
 * Bandeau vertical « 音 » à gauche ; onglets catégories en plaques laquées ; carte now-playing
 * avec un <b>disque de laque à maki-e</b> qui tourne pendant la lecture ; barres de lecture,
 * volume et distance en <b>cordes de shamisen</b> (chevalets + plectre) ; liste des pistes sur
 * un <b>rouleau de papier</b> ; Solo en plaque.
 *
 * <p>Optimisé : l'existence des textures (pochettes, disque) est mise en cache au lieu d'être
 * redemandée au gestionnaire de ressources à chaque image et pour chaque ligne.
 */
public class OstScreen extends Screen {

    // Design de référence (donne les dimensions de la maquette).
    private static final int DW = 620, DH = 360;

    // Palette DA Reborn.
    private static final int DIM        = 0x50080408;   // voile léger : le monde reste visible
    private static final int BG         = 0xE60E0A0C;   // laque noire translucide
    private static final int CARD       = 0xF0140E10;
    private static final int SECTION    = 0xF0181012;
    private static final int BORDER     = 0xFFAA8034;
    private static final int ACCENT     = 0xFFAA1E22;   // laque vermillon
    private static final int ACCENT_HOV = 0xFFC82A2E;
    private static final int GOLD       = 0xFFF6CC78;
    private static final int TEXT       = 0xFFFAEED6;
    private static final int TEXT_MUTED = 0xFFC8B4A0;
    private static final int ROW_HOVER  = 0x22FFFFFF;
    private static final int ROW_PLAY   = 0xFFAA1E22;
    private static final int PAPER = 0xF2EADCB4, PAPER_HOV = 0xF2F4EAD0, PAPER_EDGE = 0xFF8C6E48, INK = 0xFF3C2814, INK_MUTED = 0xFF8C6E50;
    private static final int STRING = 0xFFE6DCC8, BRIDGE = 0xFF6E4628;
    private static final Identifier DISC = Identifier.fromNamespaceAndPath("reborn-ost", "textures/gui/disc.png");
    /** Cache d'existence des textures (évite un getResource par image et par ligne). */
    private static final java.util.Map<Identifier, Boolean> EXISTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean exists(Identifier id) {
        if (id == null) return false;
        return EXISTS.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }

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

        boolean hasFrame = false;   // DA Reborn : panneaux dessinés (l'ancien cadre Akatsuki n'est plus utilisé)
        g.fill(px + 2, py + 3, px + pw + 2, py + ph + 3, 0x70000000);
        panel(g, px, py, pw, ph, BG);
        kanagu(g, px, py, pw, ph);
        renderBanner(g, tr);
        panel(g, cx0(), npY(), cw(), npH(), CARD);
        panel(g, cx0(), searchY(), cw(), fy(20), SECTION);
        // Liste : rouleau de papier (baguettes de bois en haut et en bas).
        int lt = listTop() - 4, lb = listBottom() + 4;
        g.fill(cx0(), lt, cx0() + cw(), lb, PAPER);
        g.fill(cx0() - 3, lt - 3, cx0() + cw() + 3, lt, BRIDGE);
        g.fill(cx0() - 3, lb, cx0() + cw() + 3, lb + 3, BRIDGE);
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

    /** Bandeau vertical à gauche (zone libre de la maquette) : kakemono laqué marqué « 音 ». */
    private void renderBanner(GuiGraphicsExtractor g, Font tr) {
        int bw = Math.min(fx(150), 120), bx = px + (fx(190) - bw) / 2, by = py + fy(28), bh = ph - fy(56);
        g.fill(bx - 4, by - 4, bx + bw + 4, by, BRIDGE);
        g.fill(bx - 4, by + bh, bx + bw + 4, by + bh + 4, BRIDGE);
        g.fill(bx, by, bx + bw, by + bh, 0xF05C1418);
        g.fill(bx + 6, by + 6, bx + bw - 6, by + bh - 6, PAPER);
        Component k = Component.literal("音");
        g.pose().pushMatrix();
        g.pose().translate(bx + bw / 2f - tr.width(k) * 3f / 2f, by + bh * 0.28f);
        g.pose().scale(3f, 3f);
        g.text(tr, k, 0, 0, 0xFF8C1C20, false);
        g.pose().popMatrix();
        Component t1 = Component.literal("MUSIQUES"), t2 = Component.literal("REBORN");
        g.text(tr, t1, bx + (bw - tr.width(t1)) / 2, by + (int) (bh * 0.62f), INK, false);
        g.text(tr, t2, bx + (bw - tr.width(t2)) / 2, by + (int) (bh * 0.62f) + 12, INK_MUTED, false);
    }

    private static void kanagu(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int c = GOLD, s = 6;
        g.fill(x, y, x + s, y + 2, c); g.fill(x, y, x + 2, y + s, c);
        g.fill(x + w - s, y, x + w, y + 2, c); g.fill(x + w - 2, y, x + w, y + s, c);
        g.fill(x, y + h - 2, x + s, y + h, c); g.fill(x, y + h - s, x + 2, y + h, c);
        g.fill(x + w - s, y + h - 2, x + w, y + h, c); g.fill(x + w - 2, y + h - s, x + w, y + h, c);
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
            g.fill(tabX, hy - 2, tabX + w, hy + 16, sel ? ACCENT : (hov ? 0xFF4A2830 : 0xFF3C1E24));
            outline(g, tabX, hy - 2, w, 18, sel ? GOLD : 0xFF6E4646);
            g.text(tr, Component.literal(cat.displayName()), tabX + 5, hy + 3,
                sel ? TEXT : TEXT_MUTED, false);
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
            shamisen(g, bx, byp + 1, bw, du > 0 ? (float) Math.min(1.0, el / (double) du) : 0f);
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
        g.fill(cx + 90, cy, cx + 106, cy + 16, sh ? ACCENT : (shHov ? 0xFF4A2830 : 0xFF3C1E24)); outline(g, cx + 90, cy, 16, 16, sh ? GOLD : 0xFF6E4646);
        g.text(tr, Component.literal("S"), cx + 96, cy + 4, sh ? 0xFFFFFFFF : TEXT_MUTED, false);
        int rm = config.getRepeatMode();
        boolean rpHov = in(mouseX, mouseY, cx + 112, cy, 16, 16);
        g.fill(cx + 112, cy, cx + 128, cy + 16, rm != 0 ? ACCENT : (rpHov ? 0xFF4A2830 : 0xFF3C1E24)); outline(g, cx + 112, cy, 16, 16, rm != 0 ? GOLD : 0xFF6E4646);
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
            else if (hovered) g.fill(left, rowY, right, rowY + ROW_H, PAPER_HOV);

            int thumbY = rowY + (ROW_H - THUMB) / 2;
            drawCover(g, track, left + 4, thumbY, THUMB);
            g.text(tr, Component.literal((playing ? "▶ " : "") + OstTrackMeta.title(track.trackId(), track.displayName())),
                left + 4 + THUMB + 8, rowY + 6, playing ? TEXT : INK, false);

            boolean fav = config.isFavorite(track.trackId());
            g.text(tr, Component.literal(fav ? "★" : "☆"), right - 14, rowY + 6, fav ? (playing ? GOLD : 0xFFB4781E) : (playing ? TEXT_MUTED : INK_MUTED), false);
            String dur = OstTrackMeta.formatDuration(OstTrackMeta.duration(track.trackId()));
            if (!dur.isEmpty()) g.text(tr, Component.literal(dur), right - 28 - tr.width(dur), rowY + 6, playing ? TEXT_MUTED : INK_MUTED, false);
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
        shamisen(g, volBarX(), sy + 1, volBarW(), config.getVolume());
        // Distance : label + valeur en blocs alignée à droite du slider.
        g.text(tr, Component.literal("DISTANCE").withStyle(s -> s.withBold(true)), distBarX() - 60, sy - 8, TEXT_MUTED, false);
        String distVal = Math.round(config.getBroadcastDistance()) + " blocs";
        g.text(tr, Component.literal(distVal),
            distBarX() + distBarW() - tr.width(distVal), sy - 8, GOLD, false);
        shamisen(g, distBarX(), sy + 1, distBarW(), Math.min(1f, config.getBroadcastDistance() / 128f));

        boolean solo = config.isSoloMode();
        int soloX = cx0() + cw() - 84;
        boolean soloHov = in(mouseX, mouseY, soloX, sy - 6, 78, 16);
        g.fill(soloX, sy - 6, soloX + 78, sy + 10, solo ? ACCENT : (soloHov ? 0xFF4A2830 : 0xFF3C1E24));
        outline(g, soloX, sy - 6, 78, 16, solo ? GOLD : 0xFF6E4646);
        g.text(tr, Component.literal(solo ? "Mode Solo ON" : "Mode Solo OFF"), soloX + 6, sy - 2,
            solo ? TEXT : TEXT_MUTED, false);
    }

    /** Corde de shamisen : corde fine, partie jouée en or, chevalets aux extrémités, plectre au curseur. */
    private void shamisen(GuiGraphicsExtractor g, int x, int y, int w, float frac) {
        g.fill(x, y, x + w, y + 1, STRING);
        int fw = (int) (w * Math.max(0f, Math.min(1f, frac)));
        g.fill(x, y - 1, x + fw, y + 1, GOLD);
        g.fill(x - 3, y - 2, x, y + 3, BRIDGE);
        g.fill(x + w, y - 2, x + w + 3, y + 3, BRIDGE);
        int k = x + fw;
        for (int i = 0; i < 4; i++) g.fill(k - i, y - 4 + i, k + i + 1, y - 3 + i, TEXT);
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void drawCover(GuiGraphicsExtractor g, OstTrack track, int x, int y, int size) {
        drawCover(g, track, x, y, size, 0f);
    }

    private void drawCover(GuiGraphicsExtractor g, OstTrack track, int x, int y, int size, float angle) {
        Identifier cover = OstTrackMeta.coverTexture(track);
        boolean rot = angle != 0f;
        if (rot) {
            g.pose().pushMatrix();
            g.pose().translate(x + size / 2f, y + size / 2f);
            g.pose().rotate((float) Math.toRadians(angle));
            g.pose().translate(-(x + size / 2f), -(y + size / 2f));
        }
        if (exists(cover)) {
            g.blit(RenderPipelines.GUI_TEXTURED, cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else if (exists(DISC)) {
            g.blit(RenderPipelines.GUI_TEXTURED, DISC, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
            if (size >= 24) {   // pastille de catégorie sur le bord du disque
                g.fill(x + size - 7, y + size - 7, x + size - 2, y + size - 2, categoryColor(track.category()));
            }
        } else {
            g.fill(x, y, x + size, y + size, categoryColor(track.category()));
        }
        if (rot) g.pose().popMatrix();
    }

    private void drawVinylPlaceholder(GuiGraphicsExtractor g, int x, int y, int size) {
        if (exists(DISC)) {
            g.blit(RenderPipelines.GUI_TEXTURED, DISC, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
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
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
