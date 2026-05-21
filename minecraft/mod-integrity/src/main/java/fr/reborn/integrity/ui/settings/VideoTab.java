package fr.reborn.integrity.ui.settings;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab Vidéo des paramètres Reborn. Référence : {@code settings.jsx::VideoTab}.
 *
 * <p>5 lignes de paramètres + 1 lien "Options avancées Minecraft" qui
 * ouvre le {@code VideoOptionsScreen} vanilla pour les vrais réglages
 * gameplay (FPS cap effectif, vsync OpenGL, render distance jeu).
 *
 * <p>NB : les valeurs ici sont persistées dans {@link RebornPrefs} mais
 * NE sont PAS câblées sur options.txt (consigne MVP du user).
 */
public class VideoTab implements SettingsTab {

    private final Screen parent;
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    private static final int ROW_HEIGHT = 38;
    private static final int LABEL_W = 200;
    private static final int CONTROL_W = 240;

    public VideoTab(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        RebornPrefs prefs = RebornPrefs.INSTANCE;
        int cursorY = y;
        int controlX = x + width - CONTROL_W;

        // Résolution.
        SegmentedControl resCtrl = new SegmentedControl(
            controlX, cursorY + 4, CONTROL_W, 24,
            new SegmentedControl.Option[] {
                new SegmentedControl.Option("hd", "HD"),
                new SegmentedControl.Option("fhd", "FHD"),
                new SegmentedControl.Option("qhd", "QHD"),
                new SegmentedControl.Option("4k", "4K"),
            },
            prefs.resolution,
            v -> { prefs.resolution = v; prefs.save(); }
        );
        widgets.add(resCtrl);
        cursorY += ROW_HEIGHT;

        // Mode fenêtre.
        SegmentedControl modeCtrl = new SegmentedControl(
            controlX, cursorY + 4, CONTROL_W, 24,
            new SegmentedControl.Option[] {
                new SegmentedControl.Option("fullscreen", "Plein écran"),
                new SegmentedControl.Option("borderless", "Sans bordure"),
                new SegmentedControl.Option("windowed", "Fenêtré"),
            },
            prefs.windowMode,
            v -> { prefs.windowMode = v; prefs.save(); }
        );
        widgets.add(modeCtrl);
        cursorY += ROW_HEIGHT;

        // FPS Max.
        SliderWidget fpsSlider = new SliderWidget(
            controlX, cursorY + 4, CONTROL_W, 24,
            prefs.fpsMax, 30, 240, " fps",
            v -> { prefs.fpsMax = v; prefs.save(); }
        );
        widgets.add(fpsSlider);
        cursorY += ROW_HEIGHT;

        // Distance de rendu.
        SliderWidget chunkSlider = new SliderWidget(
            controlX, cursorY + 4, CONTROL_W, 24,
            prefs.renderDistance, 4, 32, " chunks",
            v -> { prefs.renderDistance = v; prefs.save(); }
        );
        widgets.add(chunkSlider);
        cursorY += ROW_HEIGHT;

        // V-Sync toggle.
        ToggleBig vsyncToggle = new ToggleBig(
            controlX + CONTROL_W - ToggleBig.DEFAULT_WIDTH, cursorY + 4,
            prefs.vsync,
            v -> { prefs.vsync = v; prefs.save(); }
        );
        widgets.add(vsyncToggle);
        cursorY += ROW_HEIGHT;

        // Bouton "Options avancées Minecraft" (ouvre OptionsScreen vanilla).
        ButtonWidget vanillaBtn = ButtonWidget.builder(
            RebornFont.body("→ Options avancées Minecraft"),
            b -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.setScreen(new VideoOptionsScreen(parent, mc, mc.options));
                }
            }
        ).dimensions(x + width - 220, cursorY + 16, 220, 22).build();
        widgets.add(vanillaBtn);
        cursorY += 60;

        contentHeight = cursorY - y;
    }

    @Override
    public List<ClickableWidget> widgets() {
        return widgets;
    }

    @Override
    public int height() {
        return contentHeight;
    }

    @Override
    public void renderPassive(DrawContext ctx, int x, int y, int width) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        // Titre de section.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y - 28, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(tr, RebornFont.bold("AFFICHAGE"), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        // Lignes label + hint.
        String[][] rows = {
            {"Résolution", "Affecte uniquement l'affichage en jeu"},
            {"Mode fenêtre", null},
            {"FPS Max", null},
            {"Distance de rendu", "Plus haut = serveur plus lourd à charger"},
            {"V-Sync", null},
        };

        int cursorY = y;
        for (String[] row : rows) {
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

        // Info banner sur resource pack forcé.
        cursorY += 16;
        int bannerH = 36;
        DrawHelpers.roundedOutlinedRect(ctx, x, cursorY + 14, width, bannerH, 6,
            Colors.ACCENT_SOFT, Colors.withAlpha(Colors.ACCENT, 0.35f));
        ctx.drawText(tr, RebornFont.bold("INFO"),
            x + 12, cursorY + 22, Colors.ACCENT_HOVER, false);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 56, cursorY + 24, 0);
        ctx.getMatrices().scale(0.9f, 0.9f, 1f);
        ctx.drawText(tr, RebornFont.body(
            "Le pack de textures Reborn est appliqué automatiquement."),
            0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
    }
}
