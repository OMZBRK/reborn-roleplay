package fr.reborn.ost.ui;

import fr.reborn.ost.RebornOstClient;
import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstLibrary;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.config.OstConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Menu OST plein écran — accessible via la keybind {@code M}.
 *
 * <p>Layout simplifié (premier jet, pas la reproduction pixel-perfect
 * du screen Zenkai uploadé — placeholder fonctionnel) :
 * <pre>
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  Reborn OST                              [search...] X  │
 *   ├─────────────────────────────────────────────────────────┤
 *   │ [Apaisant] [Combat] [Mission] [Motiv] [Mystère] [Triste] [Fav] │
 *   ├─────────────────────────────────────────────────────────┤
 *   │  > Track 1                                  [▶] [♡]    │
 *   │    Track 2                                  [▶] [♡]    │
 *   │    Track 3                                  [▶] [♡]    │
 *   │  ...                                                    │
 *   ├─────────────────────────────────────────────────────────┤
 *   │  Volume    [────●─────]   Mode Solo [ ON ]              │
 *   │  Distance  [──●───────]                                  │
 *   └─────────────────────────────────────────────────────────┘
 * </pre>
 */
public class OstScreen extends Screen {

    private static final int TAB_HEIGHT = 22;
    private static final int ROW_HEIGHT = 18;
    private static final int FOOTER_HEIGHT = 60;
    private static final int MASCOT_WIDTH = 240;
    private static final int LIST_LEFT_MARGIN = 16;
    private static final int MASCOT_GUTTER = 14;

    private final Screen parent;
    private final OstLibrary library;
    private final OstAudioEngine engine;
    private final OstConfig config;

    private OstCategory selectedCategory = OstCategory.APAISANT;
    private TextFieldWidget searchField;
    private int scrollOffset = 0;

    public OstScreen(Screen parent) {
        super(Text.translatable("reborn-ost.screen.title"));
        this.parent = parent;
        this.library = RebornOstClient.library();
        this.engine = RebornOstClient.audioEngine();
        this.config = RebornOstClient.config();
    }

    @Override
    protected void init() {
        // Search box top-right.
        int searchW = 180;
        searchField = new TextFieldWidget(this.textRenderer, this.width - searchW - 36, 12,
            searchW, 18, Text.translatable("reborn-ost.screen.search.placeholder"));
        searchField.setPlaceholder(Text.translatable("reborn-ost.screen.search.placeholder"));
        this.addDrawableChild(searchField);

        // Close (X).
        this.addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> close())
            .dimensions(this.width - 28, 12, 18, 18).build());

        // Onglets catégories.
        int tabsY = 38;
        int tabsX = 16;
        int tabPaddingX = 8;
        for (OstCategory cat : OstCategory.values()) {
            String label = cat.displayName();
            int w = this.textRenderer.getWidth(label) + tabPaddingX * 2;
            OstCategory captured = cat;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
                this.selectedCategory = captured;
                this.scrollOffset = 0;
            }).dimensions(tabsX, tabsY, w, TAB_HEIGHT).build();
            this.addDrawableChild(btn);
            tabsX += w + 6;
        }

        // Volume + Distance + Mode Solo (footer).
        int footerY = this.height - FOOTER_HEIGHT + 8;
        SliderWidget volumeSlider = new SliderWidget(20, footerY, 200, 16,
                Text.translatable("reborn-ost.screen.volume"), config.getVolume()) {
            @Override protected void updateMessage() {
                setMessage(Text.translatable("reborn-ost.screen.volume")
                    .append(Text.literal(" — " + (int) Math.round(value * 100) + "%")));
            }
            @Override protected void applyValue() {
                float v = (float) value;
                config.setVolume(v);
                engine.setGlobalVolume(v);
                config.save();
            }
        };
        this.addDrawableChild(volumeSlider);

        SliderWidget distanceSlider = new SliderWidget(230, footerY, 200, 16,
                Text.translatable("reborn-ost.screen.distance"),
                Math.min(1.0, config.getBroadcastDistance() / 128.0)) {
            @Override protected void updateMessage() {
                setMessage(Text.translatable("reborn-ost.screen.distance")
                    .append(Text.literal(" — " + (int) Math.round(value * 128) + " blocks")));
            }
            @Override protected void applyValue() {
                config.setBroadcastDistance((float) (value * 128));
                config.save();
            }
        };
        this.addDrawableChild(distanceSlider);

        // Mode Solo toggle.
        int soloX = 440;
        ButtonWidget soloToggle = ButtonWidget.builder(soloLabel(), b -> {
            config.setSoloMode(!config.isSoloMode());
            config.save();
            b.setMessage(soloLabel());
        }).dimensions(soloX, footerY, 180, 16).build();
        this.addDrawableChild(soloToggle);

        // Bouton Stop — stoppe la piste en cours côté client. Pratique pour
        // couper un broadcast serveur ou une track déclenchée manuellement
        // sans devoir attendre la fin ni passer par /ost stop.
        int stopX = soloX + 180 + 6;
        ButtonWidget stopButton = ButtonWidget.builder(
            Text.translatable("reborn-ost.screen.stop"), b -> engine.stop()
        ).dimensions(stopX, footerY, 60, 16).build();
        this.addDrawableChild(stopButton);
    }

    private Text soloLabel() {
        return Text.translatable("reborn-ost.screen.mode_solo")
            .append(Text.literal(config.isSoloMode() ? " : ON" : " : OFF"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        TextRenderer tr = this.textRenderer;
        // Titre.
        ctx.drawText(tr, this.title, 16, 14, 0xFFFFFFFF, false);

        List<OstTrack> tracks = resolveVisibleTracks();
        int listTop = 72;
        int listBottom = this.height - FOOTER_HEIGHT - 8;

        // Mascotte sur la gauche.
        int mascotX = LIST_LEFT_MARGIN;
        int mascotY = listTop;
        int mascotH = listBottom - listTop;
        MascotPlaceholder.render(ctx, mascotX, mascotY, MASCOT_WIDTH, mascotH,
            selectedCategory, tracks.size());

        // Liste des tracks à droite — clip pour le scroll.
        int listLeft = mascotX + MASCOT_WIDTH + MASCOT_GUTTER;
        int listRight = this.width - LIST_LEFT_MARGIN;
        ctx.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = 0; i < tracks.size(); i++) {
            int rowY = listTop + (i - scrollOffset) * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < listTop || rowY > listBottom) continue;
            renderRow(ctx, tr, tracks.get(i), rowY, listLeft, listRight, mouseX, mouseY);
        }
        ctx.disableScissor();
    }

    private int listLeftEdge()  { return LIST_LEFT_MARGIN + MASCOT_WIDTH + MASCOT_GUTTER; }
    private int listRightEdge() { return this.width - LIST_LEFT_MARGIN; }

    private List<OstTrack> resolveVisibleTracks() {
        String q = searchField != null ? searchField.getText() : "";
        if (q != null && !q.isBlank()) {
            return library.search(q);
        }
        if (selectedCategory == OstCategory.FAVORIS) {
            return library.favorites(config.getFavorites());
        }
        return library.tracks(selectedCategory);
    }

    private void renderRow(DrawContext ctx, TextRenderer tr, OstTrack track,
                           int rowY, int listLeft, int listRight, int mouseX, int mouseY) {
        boolean hovered = mouseX >= listLeft && mouseX < listRight
            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
        boolean playing = engine.currentTrack()
            .map(t -> t.trackId().equals(track.trackId()))
            .orElse(false);
        int bg = playing ? 0x803B5BDB : (hovered ? 0x40FFFFFF : 0x00000000);
        if (bg != 0) ctx.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT, bg);

        boolean fav = config.isFavorite(track.trackId());
        String prefix = playing ? "▶ " : "  ";
        String suffix = fav ? "  ♥" : "  ♡";
        ctx.drawText(tr, prefix + track.displayName(), listLeft + 8, rowY + 4, 0xFFFFFFFF, false);
        int suffixW = tr.getWidth(suffix);
        ctx.drawText(tr, suffix, listRight - 8 - suffixW, rowY + 4,
            fav ? 0xFFFF6B9D : 0xFF888888, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        // Click dans la zone liste à droite uniquement.
        int listTop = 72;
        int listBottom = this.height - FOOTER_HEIGHT - 8;
        int listLeft = listLeftEdge();
        int listRight = listRightEdge();
        if (mouseX < listLeft || mouseX >= listRight
            || mouseY < listTop || mouseY >= listBottom) return false;
        List<OstTrack> tracks = resolveVisibleTracks();
        int relY = (int) (mouseY - listTop);
        int idx = relY / ROW_HEIGHT + scrollOffset;
        if (idx < 0 || idx >= tracks.size()) return false;
        OstTrack t = tracks.get(idx);
        // Zone droite ~30 px de la liste = toggle favorite.
        if (mouseX > listRight - 30) {
            config.toggleFavorite(t.trackId());
            config.save();
        } else {
            engine.play(t, 1.0f, null, 0f, 0f);
            config.setLastTrackId(t.trackId());
            config.save();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        List<OstTrack> tracks = resolveVisibleTracks();
        int maxOffset = Math.max(0, tracks.size() - 1);
        scrollOffset = Math.max(0, Math.min(maxOffset,
            scrollOffset - (int) Math.signum(verticalAmount)));
        return true;
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
