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
 * Menu OST compact — panneau centré (façon lecteur de musique), pas plein écran.
 * Style Reborn (palette Akatsuki, dessiné à la main). Liste des pistes avec
 * pochette + vrai titre + durée (via {@link OstTrackMeta}), onglets catégories,
 * recherche, et un pied de page lecture (now-playing + volume + distance).
 */
public class OstScreen extends Screen {

    // Palette Reborn (hardcodée — mod-ost est indépendant de mod-hud).
    private static final int DIM        = 0xB0000000;
    private static final int PANEL      = 0xF20C0709;
    private static final int HEADER     = 0xFF16090D;
    private static final int FOOTER     = 0xFF120709;
    private static final int BORDER     = 0xFF3A2A2E;
    private static final int ACCENT     = 0xFFA0182B;
    private static final int ACCENT_HOV = 0xFFC2364A;
    private static final int GOLD       = 0xFFD9A95E;
    private static final int TEXT       = 0xFFE8DCC8;
    private static final int TEXT_MUTED = 0xFF9A8B78;
    private static final int ROW_HOVER  = 0x22FFFFFF;
    private static final int ROW_PLAY   = 0x66A0182B;

    private static final int HEADER_H = 28;
    private static final int TABS_H   = 20;
    private static final int FOOTER_H = 50;
    private static final int ROW_H    = 22;
    private static final int THUMB    = 18;
    private static final int COVER_PX = 64; // taille source des covers

    private final Screen parent;
    private final OstLibrary library;
    private final OstAudioEngine engine;
    private final OstConfig config;

    private OstCategory selectedCategory = OstCategory.APAISANT;
    private TextFieldWidget searchField;
    private int scrollOffset = 0;

    private int px, py, pw, ph; // géométrie panneau

    public OstScreen(Screen parent) {
        super(Text.literal("Reborn OST"));
        this.parent = parent;
        this.library = RebornOstClient.library();
        this.engine = RebornOstClient.audioEngine();
        this.config = RebornOstClient.config();
    }

    private void layout() {
        pw = Math.min(600, this.width - 60);
        ph = Math.min(384, this.height - 60);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
    }

    @Override
    protected void init() {
        layout();
        int searchW = 150;
        searchField = new TextFieldWidget(this.textRenderer,
            px + pw - searchW - 30, py + 7, searchW, 14, Text.literal("Rechercher"));
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

        // Panneau + bordure.
        ctx.fill(px, py, px + pw, py + ph, PANEL);
        drawBorder(ctx, px, py, pw, ph, BORDER);

        // Header.
        ctx.fill(px, py, px + pw, py + HEADER_H, HEADER);
        ctx.fill(px, py + HEADER_H, px + pw, py + HEADER_H + 1, ACCENT);
        ctx.drawText(tr, Text.literal("REBORN OST").styled(s -> s.withBold(true)),
            px + 12, py + 9, GOLD, false);
        // Champ recherche : petit cadre.
        ctx.fill(px + pw - 184, py + 5, px + pw - 30, py + 21, 0x40000000);
        // Close X.
        boolean closeHov = in(mouseX, mouseY, px + pw - 24, py + 6, 16, 16);
        ctx.drawText(tr, Text.literal("✕"), px + pw - 21, py + 9, closeHov ? ACCENT_HOV : TEXT_MUTED, false);

        // Onglets catégories.
        int tabsY = py + HEADER_H + 4;
        int tabX = px + 10;
        for (OstCategory cat : OstCategory.values()) {
            String label = cat.displayName();
            int w = tr.getWidth(label) + 14;
            boolean sel = cat == selectedCategory && (searchField == null || searchField.getText().isBlank());
            boolean hov = in(mouseX, mouseY, tabX, tabsY, w, TABS_H);
            if (sel) ctx.fill(tabX, tabsY, tabX + w, tabsY + TABS_H, ACCENT);
            else if (hov) ctx.fill(tabX, tabsY, tabX + w, tabsY + TABS_H, ROW_HOVER);
            ctx.drawText(tr, Text.literal(label), tabX + 7, tabsY + 6,
                sel ? 0xFFFFFFFF : TEXT_MUTED, false);
            tabX += w + 4;
        }

        // Liste.
        List<OstTrack> tracks = resolveVisibleTracks();
        int listTop = tabsY + TABS_H + 6;
        int listBottom = py + ph - FOOTER_H - 4;
        ctx.enableScissor(px + 6, listTop, px + pw - 6, listBottom);
        for (int i = 0; i < tracks.size(); i++) {
            int rowY = listTop + (i - scrollOffset) * ROW_H;
            if (rowY + ROW_H < listTop || rowY > listBottom) continue;
            renderRow(ctx, tr, tracks.get(i), rowY, listBottom, mouseX, mouseY);
        }
        ctx.disableScissor();

        renderFooter(ctx, tr, mouseX, mouseY);
    }

    private void renderRow(DrawContext ctx, TextRenderer tr, OstTrack track,
                           int rowY, int listBottom, int mouseX, int mouseY) {
        int left = px + 8, right = px + pw - 8;
        boolean hovered = in(mouseX, mouseY, left, rowY, right - left, ROW_H) && mouseY < listBottom;
        boolean playing = engine.currentTrack().map(t -> t.trackId().equals(track.trackId())).orElse(false);
        if (playing) ctx.fill(left, rowY, right, rowY + ROW_H, ROW_PLAY);
        else if (hovered) ctx.fill(left, rowY, right, rowY + ROW_H, ROW_HOVER);

        // Pochette.
        int thumbX = left + 4, thumbY = rowY + (ROW_H - THUMB) / 2;
        drawCover(ctx, track, thumbX, thumbY, THUMB);

        // Titre.
        String title = OstTrackMeta.title(track.trackId(), track.displayName());
        int textX = thumbX + THUMB + 8;
        ctx.drawText(tr, Text.literal((playing ? "▶ " : "") + title), textX, rowY + 7,
            playing ? 0xFFFFFFFF : TEXT, false);

        // Favori + durée à droite.
        boolean fav = config.isFavorite(track.trackId());
        String heart = fav ? "♥" : "♡";
        ctx.drawText(tr, Text.literal(heart), right - 16, rowY + 7, fav ? ACCENT_HOV : TEXT_MUTED, false);
        String dur = OstTrackMeta.formatDuration(OstTrackMeta.duration(track.trackId()));
        if (!dur.isEmpty()) {
            int dw = tr.getWidth(dur);
            ctx.drawText(tr, Text.literal(dur), right - 24 - dw, rowY + 7, TEXT_MUTED, false);
        }
    }

    private void renderFooter(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int fy = py + ph - FOOTER_H;
        ctx.fill(px, fy, px + pw, py + ph, FOOTER);
        ctx.fill(px, fy, px + pw, fy + 1, BORDER);

        // Now-playing à gauche.
        var cur = engine.currentTrack();
        int npX = px + 8;
        if (cur.isPresent()) {
            OstTrack t = cur.get();
            drawCover(ctx, t, npX, fy + 9, 32);
            String title = OstTrackMeta.title(t.trackId(), t.displayName());
            ctx.drawText(tr, Text.literal("♪ " + title), npX + 38, fy + 12, GOLD, false);
            ctx.drawText(tr, Text.literal(t.category().displayName()), npX + 38, fy + 26, TEXT_MUTED, false);
        } else {
            ctx.drawText(tr, Text.literal("— Aucune piste —"), npX, fy + 18, TEXT_MUTED, false);
        }

        // Boutons stop + solo à droite.
        boolean stopHov = in(mouseX, mouseY, px + pw - 60, fy + 8, 52, 14);
        ctx.fill(px + pw - 60, fy + 8, px + pw - 8, fy + 22, stopHov ? ACCENT_HOV : ACCENT);
        ctx.drawText(tr, Text.literal("Stop"), px + pw - 50, fy + 11, 0xFFFFFFFF, false);

        boolean solo = config.isSoloMode();
        boolean soloHov = in(mouseX, mouseY, px + pw - 122, fy + 8, 56, 14);
        ctx.fill(px + pw - 122, fy + 8, px + pw - 66, fy + 22, solo ? ACCENT : ROW_HOVER);
        ctx.drawText(tr, Text.literal(solo ? "Solo ON" : "Solo OFF"), px + pw - 117, fy + 11,
            solo ? 0xFFFFFFFF : TEXT_MUTED, false);

        // Barre de volume.
        int volX = px + pw - 122, volY = fy + 30, volW = 114;
        ctx.drawText(tr, Text.literal("Vol"), volX - 20, volY - 2, TEXT_MUTED, false);
        ctx.fill(volX, volY, volX + volW, volY + 3, 0x40FFFFFF);
        int fillW = (int) (volW * config.getVolume());
        ctx.fill(volX, volY, volX + fillW, volY + 3, ACCENT);
        ctx.fill(volX + fillW - 1, volY - 2, volX + fillW + 1, volY + 5, GOLD);
    }

    /** Dessine la pochette (texture si présente, sinon carré coloré + initiale). */
    private void drawCover(DrawContext ctx, OstTrack track, int x, int y, int size) {
        Identifier cover = OstTrackMeta.coverTexture(track);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (cover != null && mc.getResourceManager().getResource(cover).isPresent()) {
            ctx.drawTexture(cover, x, y, 0f, 0f, size, size, COVER_PX, COVER_PX);
        } else {
            ctx.fill(x, y, x + size, y + size, categoryColor(track.category()));
            String letter = track.category().displayName().substring(0, 1);
            ctx.drawText(this.textRenderer, Text.literal(letter),
                x + size / 2 - 2, y + size / 2 - 4, 0xFFFFFFFF, false);
        }
        drawBorder(ctx, x, y, size, size, 0x60000000);
    }

    // ─── Interaction ───

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int mxi = (int) mx, myi = (int) my;

        // Close.
        if (in(mxi, myi, px + pw - 24, py + 6, 18, 18)) { close(); return true; }

        // Onglets.
        int tabsY = py + HEADER_H + 4, tabX = px + 10;
        for (OstCategory cat : OstCategory.values()) {
            int w = this.textRenderer.getWidth(cat.displayName()) + 14;
            if (in(mxi, myi, tabX, tabsY, w, TABS_H)) {
                selectedCategory = cat; scrollOffset = 0;
                if (searchField != null) searchField.setText("");
                return true;
            }
            tabX += w + 4;
        }

        int fy = py + ph - FOOTER_H;
        // Stop.
        if (in(mxi, myi, px + pw - 60, fy + 8, 52, 14)) { engine.stop(); return true; }
        // Solo.
        if (in(mxi, myi, px + pw - 122, fy + 8, 56, 14)) {
            config.setSoloMode(!config.isSoloMode()); config.save(); return true;
        }
        // Volume bar.
        if (setVolumeFromMouse(mx, my)) return true;

        // Lignes de la liste.
        List<OstTrack> tracks = resolveVisibleTracks();
        int listTop = tabsY + TABS_H + 6;
        int listBottom = py + ph - FOOTER_H - 4;
        if (myi >= listTop && myi < listBottom && mxi >= px + 8 && mxi < px + pw - 8) {
            int idx = (myi - listTop) / ROW_H + scrollOffset;
            if (idx >= 0 && idx < tracks.size()) {
                OstTrack t = tracks.get(idx);
                if (mxi > px + pw - 24) { // zone cœur
                    config.toggleFavorite(t.trackId());
                } else {
                    engine.play(t, 1.0f, null, 0f, 0f);
                    config.setLastTrackId(t.trackId());
                }
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
        int fy = py + ph - FOOTER_H;
        int volX = px + pw - 122, volY = fy + 30, volW = 114;
        if (mx >= volX && mx <= volX + volW && my >= volY - 4 && my <= volY + 7) {
            float v = (float) Math.max(0, Math.min(1, (mx - volX) / volW));
            config.setVolume(v);
            engine.setGlobalVolume(v);
            config.save();
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

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
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
