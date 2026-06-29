package fr.reborn.hud.menu.config;

import fr.reborn.hud.crosshair.CrosshairManager;
import fr.reborn.hud.crosshair.CrosshairTile;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.menu.settings.SegmentedControl;
import fr.reborn.hud.menu.settings.SettingsTab;
import fr.reborn.hud.menu.settings.SliderWidget;
import fr.reborn.hud.menu.settings.ToggleBig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Onglet Viseur (pilier 2) : active le viseur Reborn, choix du preset (grille),
 * échelle, couleur et arc-en-ciel. Le rendu in-game est dans
 * {@link CrosshairManager}.
 */
public class CrosshairTab implements SettingsTab {

    private final List<ClickableWidget> widgets = new ArrayList<>();
    private int contentHeight = 0;

    private static final int ROW_HEIGHT = 38;
    private static final int CONTROL_W = 240;
    private static final int TILE = 40;
    private static final int TILE_GAP = 8;
    /** Offset (depuis y) du label "MODÈLES", puis de la grille. */
    private static final int GRID_LABEL_OFF = 4 * ROW_HEIGHT + 10;
    private static final int GRID_TOP_OFF = 4 * ROW_HEIGHT + 28;

    // Swatches de couleur (ARGB).
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_RED   = 0xFFEF4444;
    private static final int C_GOLD  = 0xFFD9A95E;
    private static final int C_GREEN = 0xFF4ADE80;
    private static final int C_CYAN  = 0xFF38BDF8;

    @Override
    public void layout(int x, int y, int width) {
        widgets.clear();
        RebornPrefs prefs = RebornPrefs.INSTANCE;

        int controlW = Math.max(150, Math.min(CONTROL_W, width - 130));
        int controlX = x + width - controlW;
        int cursorY = y;

        // Activer.
        widgets.add(new ToggleBig(
            controlX + controlW - ToggleBig.DEFAULT_WIDTH, cursorY + 4,
            prefs.crosshairEnabled,
            v -> { prefs.crosshairEnabled = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        // Échelle.
        widgets.add(new SliderWidget(
            controlX, cursorY + 4, controlW, 24,
            prefs.crosshairScale, 50, 200, "%",
            v -> { prefs.crosshairScale = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        // Couleur.
        widgets.add(new SegmentedControl(
            controlX, cursorY + 4, controlW, 24,
            new SegmentedControl.Option[] {
                new SegmentedControl.Option("white", "Blanc"),
                new SegmentedControl.Option("red", "Rouge"),
                new SegmentedControl.Option("gold", "Or"),
                new SegmentedControl.Option("green", "Vert"),
                new SegmentedControl.Option("cyan", "Cyan"),
            },
            colorKey(prefs.crosshairColor),
            v -> {
                prefs.crosshairColor = colorFor(v);
                prefs.crosshairRainbow = false;
                prefs.save();
            }));
        cursorY += ROW_HEIGHT;

        // Arc-en-ciel.
        widgets.add(new ToggleBig(
            controlX + controlW - ToggleBig.DEFAULT_WIDTH, cursorY + 4,
            prefs.crosshairRainbow,
            v -> { prefs.crosshairRainbow = v; prefs.save(); }));
        cursorY += ROW_HEIGHT;

        // Grille de presets.
        int cols = Math.max(3, (width + TILE_GAP) / (TILE + TILE_GAP));
        int gridTop = y + GRID_TOP_OFF;
        for (int i = 0; i < CrosshairManager.PRESET_COUNT; i++) {
            final int idx = i;
            int col = i % cols;
            int row = i / cols;
            int tx = x + col * (TILE + TILE_GAP);
            int ty = gridTop + row * (TILE + TILE_GAP);
            widgets.add(new CrosshairTile(tx, ty, TILE, i,
                () -> RebornPrefs.INSTANCE.crosshairPreset,
                sel -> { prefs.crosshairPreset = sel; prefs.save(); }));
        }
        int rows = (CrosshairManager.PRESET_COUNT + cols - 1) / cols;
        contentHeight = (gridTop - y) + rows * (TILE + TILE_GAP) + 8;
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

        // Section.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y - 28, 0);
        ctx.getMatrices().scale(1.2f, 1.2f, 1f);
        ctx.drawText(tr, RebornFont.bold("VISEUR"), 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();

        // Labels des 4 lignes.
        String[][] rows = {
            {"Activer le viseur Reborn", "Remplace le viseur vanilla en vue 1ère personne"},
            {"Échelle", null},
            {"Couleur", null},
            {"Arc-en-ciel", "Cycle de teinte — ignore la couleur"},
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

        // En-tête de la grille.
        ctx.drawText(tr, RebornFont.bold("MODÈLES"), x, y + GRID_LABEL_OFF,
            Colors.FOREGROUND_SUBTLE, false);
    }

    private static String colorKey(int argb) {
        return switch (argb) {
            case C_RED -> "red";
            case C_GOLD -> "gold";
            case C_GREEN -> "green";
            case C_CYAN -> "cyan";
            default -> "white";
        };
    }

    private static int colorFor(String key) {
        return switch (key) {
            case "red" -> C_RED;
            case "gold" -> C_GOLD;
            case "green" -> C_GREEN;
            case "cyan" -> C_CYAN;
            default -> C_WHITE;
        };
    }
}
