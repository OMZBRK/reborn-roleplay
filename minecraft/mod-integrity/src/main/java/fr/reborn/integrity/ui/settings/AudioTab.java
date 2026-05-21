package fr.reborn.integrity.ui.settings;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab Audio — 4 sliders volumes + 1 toggle mute-on-unfocus.
 * Référence : {@code settings.jsx::AudioTab}.
 */
public class AudioTab implements SettingsTab {

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    private static final int ROW_HEIGHT = 38;
    private static final int CONTROL_W = 240;

    private static final String[][] LABELS = {
        {"Volume principal", null},
        {"Musique", "Contrôle aussi le lecteur du menu principal"},
        {"Effets sonores", null},
        {"Voix", "Chat vocal RP de proximité"},
        {"Mute si fenêtre inactive", null},
    };

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        int cursorY = y;
        int controlX = x + width - CONTROL_W;

        widgets.add(new SliderWidget(controlX, cursorY + 4, CONTROL_W, 24,
            prefs.volumeMaster, 0, 100, "%",
            v -> { prefs.volumeMaster = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        widgets.add(new SliderWidget(controlX, cursorY + 4, CONTROL_W, 24,
            prefs.volumeMusic, 0, 100, "%",
            v -> { prefs.volumeMusic = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        widgets.add(new SliderWidget(controlX, cursorY + 4, CONTROL_W, 24,
            prefs.volumeSfx, 0, 100, "%",
            v -> { prefs.volumeSfx = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        widgets.add(new SliderWidget(controlX, cursorY + 4, CONTROL_W, 24,
            prefs.volumeVoice, 0, 100, "%",
            v -> { prefs.volumeVoice = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        widgets.add(new ToggleBig(controlX + CONTROL_W - ToggleBig.DEFAULT_WIDTH,
            cursorY + 4, prefs.muteOnUnfocus,
            v -> { prefs.muteOnUnfocus = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        contentHeight = cursorY - y;
    }

    @Override public List<ClickableWidget> widgets() { return widgets; }
    @Override public int height() { return contentHeight; }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y - 28, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(tr, RebornFont.bold("VOLUMES"), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        int cursorY = y;
        for (String[] row : LABELS) {
            ctx.drawText(tr, RebornFont.bold(row[0]), x, cursorY + 8, Colors.WHITE_PURE, false);
            if (row[1] != null) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x, cursorY + 20, 0);
                ctx.getMatrices().scale(0.85f, 0.85f, 1f);
                ctx.drawText(tr, RebornFont.body(row[1]), 0, 0, Colors.FOREGROUND_MUTED, false);
                ctx.getMatrices().pop();
            }
            cursorY += ROW_HEIGHT;
        }
    }
}
